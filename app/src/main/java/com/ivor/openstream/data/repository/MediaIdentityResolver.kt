package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.MediaIdentity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Bridges provider-specific catalog IDs into the identity used by the existing
 * TMDB-backed details/stream pipeline.
 */
@Singleton
class MediaIdentityResolver @Inject constructor(
    private val api: TmdbApi
) {
    suspend fun resolve(item: CatalogItem): Result<MediaIdentity?> = runCatching {
        val explicitTmdb = item.externalIds["tmdb"]?.toIntOrNull()
        if (explicitTmdb != null) {
            val type = if (item.type == CatalogContentType.MOVIE) "movie" else "tv"
            val ids = api.getExternalIds(type, explicitTmdb)
            return@runCatching MediaIdentity(
                tmdbId = explicitTmdb,
                tmdbType = type,
                imdbId = ids.imdbId,
                title = item.title,
                originalTitle = item.originalTitle,
                year = item.year
            )
        }

        val response = api.searchMulti(item.title, 1)
        val candidates = response.results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .map { candidate ->
                val candidateType = if (candidate.mediaType == "movie") CatalogContentType.MOVIE else CatalogContentType.SERIES
                val typeScore = if (item.type == CatalogContentType.MOVIE && candidateType == CatalogContentType.MOVIE ||
                    item.type != CatalogContentType.MOVIE && candidateType == CatalogContentType.SERIES) 100 else 0
                val title = candidate.name.trim().lowercase()
                val query = item.title.trim().lowercase()
                val titleScore = when {
                    title == query -> 100
                    title.startsWith(query) || query.startsWith(title) -> 60
                    title.contains(query) || query.contains(title) -> 30
                    else -> 0
                }
                val year = candidate.date.take(4).toIntOrNull()
                val yearScore = if (item.year != null && year != null) {
                    when {
                        year == item.year -> 50
                        abs(year - item.year) <= 1 -> 15
                        else -> 0
                    }
                } else 0
                Triple(candidate, typeScore + titleScore + yearScore, year)
            }
            .sortedByDescending { it.second }

        val best = candidates.firstOrNull() ?: return@runCatching null
        if (best.second < MIN_MATCH_SCORE) return@runCatching null

        val candidate = best.first
        val type = if (candidate.mediaType == "movie") "movie" else "tv"
        val ids = api.getExternalIds(type, candidate.id)

        MediaIdentity(
            tmdbId = candidate.id,
            tmdbType = type,
            imdbId = ids.imdbId,
            title = candidate.name,
            originalTitle = item.originalTitle,
            year = best.third
        )
    }

    private companion object {
        const val MIN_MATCH_SCORE = 100
    }
}
