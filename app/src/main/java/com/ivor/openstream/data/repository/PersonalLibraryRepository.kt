package com.ivor.openstream.data.repository

import android.content.SharedPreferences
import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class PersonalLibraryRepository @Inject constructor(
    @Named("StreamingClient") private val client: OkHttpClient,
    private val json: Json,
    private val preferences: SharedPreferences
) {
    val isConfigured: Boolean
        get() = manifestUrl.isNotBlank()

    suspend fun catalogEntries(forceRefresh: Boolean = false): Result<List<AnimeDto>> = load(forceRefresh).map { entries ->
        entries.map { entry ->
            AnimeDto(
                id = entry.tmdbId,
                tvName = if (entry.mediaType == "tv") entry.title else null,
                movieTitle = if (entry.mediaType == "movie") entry.title else null,
                overview = entry.description,
                posterPath = entry.posterPath,
                backdropPath = entry.backdropPath,
                firstAirDate = if (entry.mediaType == "tv") entry.year?.toString() else null,
                releaseDate = if (entry.mediaType == "movie") entry.year?.toString() else null,
                mediaType = entry.mediaType
            )
        }
    }

    suspend fun hasPlayableStream(mediaType: String, tmdbId: Int): Boolean =
        load(forceRefresh = false).getOrDefault(emptyList()).any { it.mediaType == mediaType && it.tmdbId == tmdbId && it.streams.isNotEmpty() }

    suspend fun streamsFor(mediaType: String, tmdbId: Int): Result<List<VideoServer>> = load(forceRefresh = false).map { entries ->
        entries.firstOrNull { it.mediaType == mediaType && it.tmdbId == tmdbId }
            ?.streams
            .orEmpty()
            .mapIndexed { index, stream ->
                VideoServer(
                    id = "personal:$mediaType:$tmdbId:$index",
                    providerId = PROVIDER_ID,
                    providerName = PROVIDER_NAME,
                    name = stream.name ?: "${PROVIDER_NAME} ${stream.qualityLabel ?: ""}".trim(),
                    url = stream.url,
                    quality = StreamQuality.parse(stream.qualityLabel),
                    headers = stream.headers,
                    mimeType = stream.mimeType
                )
            }
    }

    private suspend fun load(forceRefresh: Boolean): Result<List<ManifestEntry>> = runCatching {
        if (!isConfigured) return@runCatching emptyList()
        mutex.withLock {
            if (!forceRefresh && memoryCache != null && System.currentTimeMillis() - cachedAt < CACHE_TTL_MS) {
                return@withLock memoryCache.orEmpty()
            }

            val fetched = runCatching { fetchRemote() }
            val entries = fetched.getOrNull()
                ?: readCached()
                ?: throw fetched.exceptionOrNull() ?: IllegalStateException("Personal library unavailable")
            memoryCache = entries
            cachedAt = System.currentTimeMillis()
            if (fetched.isSuccess) {
                writeCached(entries)
            }
            entries
        }
    }

    private fun fetchRemote(): List<ManifestEntry> {
        val request = Request.Builder().url(manifestUrl).header("Accept", "application/json").build()
        client.newCall(request).execute().use { response ->
            require(response.isSuccessful) { "Personal manifest returned HTTP ${response.code}" }
            val body = response.body?.string()?.takeIf { it.isNotBlank() }
                ?: error("Personal manifest was empty")
            val manifest = json.decodeFromString<PersonalLibraryManifestDto>(body)
            return manifest.items.mapNotNull { item -> item.toEntry() }.distinctBy { "${it.mediaType}:${it.tmdbId}" }
        }
    }

    private fun readCached(): List<ManifestEntry>? = runCatching {
        preferences.getString(KEY_CACHE, null)
            ?.let { json.decodeFromString<List<ManifestEntry>>(it) }
    }.getOrNull()

    private fun writeCached(entries: List<ManifestEntry>) {
        runCatching {
            preferences.edit().putString(KEY_CACHE, json.encodeToString(entries)).apply()
        }
    }

    private val manifestUrl: String
        get() = BuildConfig.PERSONAL_LIBRARY_MANIFEST_URL.trim()

    @Serializable
    private data class PersonalLibraryManifestDto(
        val name: String? = null,
        val items: List<PersonalLibraryItemDto> = emptyList()
    )

    @Serializable
    private data class PersonalLibraryItemDto(
        val id: String? = null,
        val title: String? = null,
        val type: String? = null,
        @SerialName("mediaType") val mediaType: String? = null,
        val year: String? = null,
        val poster: String? = null,
        @SerialName("posterPath") val posterPath: String? = null,
        val backdrop: String? = null,
        @SerialName("backdropPath") val backdropPath: String? = null,
        val description: String? = null,
        @SerialName("tmdbId") val tmdbId: Int? = null,
        val streamUrl: String? = null,
        val mimeType: String? = null,
        val headers: Map<String, String>? = null,
        val streams: List<PersonalStreamDto> = emptyList()
    ) {
        fun toEntry(): ManifestEntry? {
            val resolvedTmdbId = tmdbId ?: id?.toIntOrNull() ?: return null
            val normalizedType = when ((mediaType ?: type ?: "movie").trim().lowercase()) {
                "movie", "film" -> "movie"
                "tv", "series", "show" -> "tv"
                else -> "movie"
            }
            val normalizedTitle = title?.trim().orEmpty().ifBlank { null } ?: return null
            val normalizedStreams = buildList {
                streams.forEach { stream ->
                    val url = stream.url?.trim().orEmpty()
                    if (url.isNotBlank()) {
                        add(
                            StreamEntry(
                                name = stream.title?.trim()?.ifBlank { null } ?: stream.quality?.trim()?.ifBlank { null },
                                url = url,
                                qualityLabel = stream.quality?.trim()?.ifBlank { null },
                                mimeType = stream.mimeType?.trim()?.ifBlank { null },
                                headers = stream.headers.orEmpty()
                            )
                        )
                    }
                }
                val singularUrl = streamUrl?.trim().orEmpty()
                if (singularUrl.isNotBlank()) {
                    add(
                        StreamEntry(
                            name = "Default",
                            url = singularUrl,
                            qualityLabel = null,
                            mimeType = mimeType?.trim()?.ifBlank { null },
                            headers = headers.orEmpty()
                        )
                    )
                }
            }.distinctBy { it.url }
            return ManifestEntry(
                tmdbId = resolvedTmdbId,
                title = normalizedTitle,
                mediaType = normalizedType,
                year = year?.take(4)?.toIntOrNull(),
                description = description?.trim()?.ifBlank { null },
                posterPath = (posterPath ?: poster)?.trim()?.ifBlank { null },
                backdropPath = (backdropPath ?: backdrop)?.trim()?.ifBlank { null },
                streams = normalizedStreams
            )
        }
    }

    @Serializable
    private data class PersonalStreamDto(
        val quality: String? = null,
        val title: String? = null,
        val url: String? = null,
        val mimeType: String? = null,
        val headers: Map<String, String>? = null
    )

    @Serializable
    private data class ManifestEntry(
        val tmdbId: Int,
        val title: String,
        val mediaType: String,
        val year: Int?,
        val description: String?,
        val posterPath: String?,
        val backdropPath: String?,
        val streams: List<StreamEntry>
    )

    @Serializable
    private data class StreamEntry(
        val name: String?,
        val url: String,
        val qualityLabel: String?,
        val mimeType: String?,
        val headers: Map<String, String> = emptyMap()
    )

    private companion object {
        const val PROVIDER_ID = "personal-library"
        const val PROVIDER_NAME = "My Library"
        const val KEY_CACHE = "personal_library_manifest_cache_v1"
        const val CACHE_TTL_MS = 15 * 60 * 1_000L
    }

    private val mutex = Mutex()
    private var memoryCache: List<ManifestEntry>? = null
    private var cachedAt: Long = 0L
}
