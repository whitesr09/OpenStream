package com.ivor.openstream.data.streaming.providers

import com.ivor.openstream.data.streaming.BROWSER_USER_AGENT
import com.ivor.openstream.data.streaming.StreamProvider
import com.ivor.openstream.domain.model.MediaIdentity
import com.ivor.openstream.domain.model.ResolverApiRequest
import com.ivor.openstream.domain.model.ResolverApiSpec
import com.ivor.openstream.domain.model.StreamAudio
import com.ivor.openstream.domain.model.StreamQuality
import com.ivor.openstream.domain.model.StreamSubtitle
import com.ivor.openstream.domain.model.VideoServer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Generic multi-stage HTTP resolver.
 *
 * Flow:
 *   TMDB/IMDb identity -> optional search -> provider id -> optional details -> playback -> streams.
 *
 * It deliberately exposes only declarative HTTP + JSON mapping. Provider-specific authentication,
 * encryption, JavaScript execution and anti-bot bypasses belong outside this engine.
 */
class ResolverApiProvider(
    private val client: OkHttpClient,
    private val json: Json,
    private val spec: ResolverApiSpec,
    private val baseUrl: String,
    override val id: String,
    override val displayName: String,
    override val priority: Int
) : StreamProvider {

    override val isEnabled: Boolean = true

    override suspend fun resolve(identity: MediaIdentity): Result<List<VideoServer>> = runCatching {
        val providerId = findProviderId(identity)
        val details = providerId?.let { spec.details?.let { request(it, identity, providerId) } }
        val playback = request(spec.playback, identity, providerId, details)
        parseStreams(playback)
    }

    private fun findProviderId(identity: MediaIdentity): String? {
        val search = spec.search ?: return null
        val root = request(search, identity, null)
        val candidates = root.at(spec.response.searchItemsPath).asCandidates()
        if (candidates.isEmpty()) return null

        return candidates
            .mapNotNull { item ->
                val providerId = item.at(spec.response.providerIdPath).stringValue()
                    ?: item.stringValue()
                    ?: return@mapNotNull null
                val title = spec.response.titlePath?.let { item.at(it).stringValue() }
                val year = spec.response.yearPath?.let { item.at(it).intValue() }
                Candidate(providerId, title, year)
            }
            .sortedWith(
                compareByDescending<Candidate> { titleScore(identity, it.title) }
                    .thenBy { yearDistance(identity.year, it.year) }
            )
            .firstOrNull()
            ?.providerId
    }

    private fun request(
        requestSpec: ResolverApiRequest,
        identity: MediaIdentity,
        providerId: String?,
        details: JsonElement? = null
    ): JsonElement {
        val url = resolveUrl(expand(requestSpec.url, identity, providerId, details))
        val query = requestSpec.query.entries.joinToString("&") { (key, value) ->
            encoded(key) + "=" + encoded(expand(value, identity, providerId, details))
        }
        val finalUrl = when {
            query.isBlank() -> url
            "?" in url -> "$url&$query"
            else -> "$url?$query"
        }

        val bodyText = requestSpec.body
            ?.let { expand(it, identity, providerId, details) }
            ?.takeIf { it.isNotBlank() }

        val builder = Request.Builder()
            .url(finalUrl)
            .header("User-Agent", BROWSER_USER_AGENT)
            .header("Accept", "application/json")
            .apply {
                requestSpec.headers.forEach { (key, value) ->
                    header(key, expand(value, identity, providerId, details))
                }
            }

        val body = bodyText?.toRequestBody("application/json; charset=utf-8".toMediaType())
        builder.method(requestSpec.method.uppercase(), body)

        return client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Resolver $displayName returned HTTP ${response.code}")
            }
            val raw = response.body?.string().orEmpty()
            if (raw.isBlank()) throw IllegalStateException("Resolver $displayName returned an empty body")
            json.parseToJsonElement(raw)
        }
    }

    private fun parseStreams(root: JsonElement): List<VideoServer> {
        val response = spec.response
        val streamElements = root.at(response.streamsPath).asCandidates()
        if (streamElements.isEmpty()) return emptyList()

        val sharedSubtitles = response.subtitlesPath
            ?.takeIf { it.isNotBlank() }
            ?.let { root.at(it).asCandidates() }
            ?.mapNotNull { subtitle ->
                val url = subtitle.at(response.subtitleUrlPath).stringValue()
                if (url.isNullOrBlank()) null else StreamSubtitle(
                    url = url,
                    label = response.subtitleLabelPath?.let { subtitle.at(it).stringValue() }
                        ?: "Subtitle"
                )
            }
            .orEmpty()

        return streamElements.mapNotNull { element ->
            val url = element.at(response.streamUrlPath).stringValue()
                ?: element.stringValue()
                ?: return@mapNotNull null

            val quality = response.qualityPath?.let { element.at(it).stringValue() }
            val language = response.languagePath?.let { element.at(it).stringValue() }
            val audio = response.audioPath?.let { element.at(it).stringValue() } ?: language

            VideoServer(
                id = "$id-${url.hashCode()}",
                providerId = id,
                providerName = displayName,
                name = buildName(quality, language),
                url = url,
                quality = StreamQuality.parse(quality),
                audio = StreamAudio.parse(audio),
                audioLanguage = language,
                mimeType = response.mimeTypePath?.let { element.at(it).stringValue() },
                subtitles = sharedSubtitles
            )
        }.distinctBy { it.url }
    }

    private fun buildName(quality: String?, language: String?): String =
        listOf(displayName, quality, language)
            .filter { !it.isNullOrBlank() }
            .joinToString(" · ")

    private fun resolveUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("https://")) return trimmed
        if (trimmed.startsWith("http://")) {
            throw IllegalArgumentException("Resolver $displayName only permits HTTPS URLs")
        }
        return baseUrl.trimEnd('/') + "/" + trimmed.trimStart('/')
    }

    private fun expand(
        template: String,
        identity: MediaIdentity,
        providerId: String?,
        details: JsonElement?
    ): String =
        template
            .replace("{tmdbId}", identity.tmdbId.toString())
            .replace("{imdbId}", identity.imdbId.orEmpty())
            .replace("{title}", identity.title)
            .replace("{originalTitle}", identity.originalTitle.orEmpty())
            .replace("{year}", identity.year?.toString().orEmpty())
            .replace("{season}", identity.season.toString())
            .replace("{episode}", identity.episode.toString())
            .replace("{providerId}", providerId.orEmpty())
            .replace("{details}", details?.toString().orEmpty())

    private fun JsonElement.at(path: String): JsonElement {
        val normalized = path.trim().removePrefix("$").removePrefix(".")
        if (normalized.isBlank()) return this

        var current: JsonElement = this
        normalized.split('.').filter { it.isNotBlank() }.forEach { token ->
            val match = Regex("""^([^\[]+)(?:\[(\d+)])?$""").matchEntire(token)
                ?: return JsonPrimitive("")
            val key = match.groupValues[1]
            current = when (current) {
                is JsonObject -> current[key] ?: return JsonPrimitive("")
                else -> return JsonPrimitive("")
            }
            match.groupValues[2].takeIf { it.isNotBlank() }?.toIntOrNull()?.let { index ->
                current = (current as? JsonArray)?.getOrNull(index) ?: return JsonPrimitive("")
            }
        }
        return current
    }

    private fun JsonElement.asCandidates(): List<JsonElement> = when (this) {
        is JsonArray -> this.toList()
        is JsonObject -> listOf(this)
        is JsonPrimitive -> if (this.contentOrNull?.isNotBlank() == true) listOf(this) else emptyList()
        else -> emptyList()
    }

    private fun JsonElement.stringValue(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonElement.intValue(): Int? = stringValue()?.toIntOrNull()

    private fun titleScore(identity: MediaIdentity, candidate: String?): Int {
        val wanted = normalize(identity.title)
        val actual = normalize(candidate)
        if (wanted.isBlank() || actual.isBlank()) return 0
        return when {
            wanted == actual -> 100
            actual.contains(wanted) || wanted.contains(actual) -> 70
            wanted.split(' ').intersect(actual.split(' ').toSet()).size >= 2 -> 40
            else -> 0
        }
    }

    private fun yearDistance(expected: Int?, actual: Int?): Int =
        if (expected == null || actual == null) 999 else kotlin.math.abs(expected - actual)

    private fun normalize(value: String?): String =
        value.orEmpty().lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

    private fun encoded(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8.name())

    private data class Candidate(
        val providerId: String,
        val title: String?,
        val year: Int?
    )
}
