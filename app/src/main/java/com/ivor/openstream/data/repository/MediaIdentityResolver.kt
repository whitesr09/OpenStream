package com.ivor.openstream.data.repository

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
    private val gateway: TmdbIdentityGateway
) {
    suspend fun resolve(item: CatalogItem): Result<MediaIdentity?> = runCatching {
        val explicitTmdb = item.externalIds["tmdb"]?.toIntOrNull()
        if (explicitTmdb != null) {
            return@runCatching buildIdentity(item, explicitTmdb, item.type.toTmdbType())
        }

        val externalIdentity = resolveExternalId(item)
        if (externalIdentity != null) return@runCatching externalIdentity

        val response = gateway.searchMulti(item.title, 1)
        val candidates = response.results
            .filter { it.mediaType == "movie" || it.mediaType == "tv" }
            .map { candidate ->
                val candidateType = if (candidate.mediaType == "movie") CatalogContentType.MOVIE else CatalogContentType.SERIES
                val typeScore = if (
                    item.type == CatalogContentType.MOVIE && candidateType == CatalogContentType.MOVIE ||
                    item.type != CatalogContentType.MOVIE && candidateType == CatalogContentType.SERIES
                ) 40 else 0
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

        buildIdentity(item, best.first.id, if (best.first.mediaType == "movie") "movie" else "tv", best.third)
    }

    private suspend fun resolveExternalId(item: CatalogItem): MediaIdentity? {
        val candidates = listOf(
            item.externalIds["imdb"]?.let { it to "imdb_id" },
            item.externalIds["tvdb"]?.let { it to "tvdb_id" }
        )
        for ((externalId, externalSource) in candidates.filterNotNull()) {
            val lookup = runCatching { gateway.findByExternalId(externalId, externalSource) }.getOrNull() ?: continue
            val expectedType = item.type.toTmdbType()
            val candidate = when (expectedType) {
                "movie" -> lookup.movieResults.firstOrNull()
                else -> lookup.tvResults.firstOrNull()
            } ?: continue
            return buildIdentity(item, candidate.id, expectedType, candidate.date.take(4).toIntOrNull())
        }
        return null
    }

    private suspend fun buildIdentity(
        item: CatalogItem,
        tmdbId: Int,
        tmdbType: String,
        resolvedYear: Int? = item.year
    ): MediaIdentity {
        val ids = gateway.getExternalIds(tmdbType, tmdbId)
        return MediaIdentity(
            tmdbId = tmdbId,
            tmdbType = tmdbType,
            imdbId = ids.imdbId ?: item.externalIds["imdb"],
            title = item.title,
            originalTitle = item.originalTitle,
            year = resolvedYear
        )
    }

    private fun CatalogContentType.toTmdbType(): String =
        if (this == CatalogContentType.MOVIE) "movie" else "tv"

    private companion object {
        const val MIN_MATCH_SCORE = 100
    }
}
