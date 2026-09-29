package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.CatalogQuery
import com.ivor.openstream.domain.repository.CatalogProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TmdbCatalogProvider @Inject constructor(private val api: TmdbApi) : CatalogProvider {
    override val id = "tmdb"
    override val displayName = "TMDB"
    override val priority = 0

    override suspend fun search(query: CatalogQuery): Result<List<CatalogItem>> = runCatching {
        val response = when {
            query.types.size == 1 && CatalogContentType.MOVIE in query.types -> api.searchMovie(query.text, query.page)
            query.types.size == 1 && CatalogContentType.SERIES in query.types -> api.searchTv(query.text, query.page)
            else -> api.searchMulti(query.text, query.page)
        }
        response.results.mapNotNull { item ->
            val baseType = if (item.isMovie) CatalogContentType.MOVIE else CatalogContentType.SERIES
            val type = if (
                baseType == CatalogContentType.SERIES &&
                item.originalLanguage.equals("ja", true) &&
                16 in item.genreIds.orEmpty()
            ) CatalogContentType.ANIME else baseType

            if (query.types.isNotEmpty() && type !in query.types) return@mapNotNull null
            if (query.language != null && !item.originalLanguage.equals(query.language, true)) return@mapNotNull null

            CatalogItem(
                sourceId = "tmdb:" + item.mediaType + ":" + item.id,
                title = item.name,
                type = type,
                year = item.date.take(4).toIntOrNull(),
                posterUrl = item.posterPath,
                backdropUrl = item.backdropPath,
                description = item.overview,
                language = item.originalLanguage,
                rating = item.voteAverage,
                popularity = item.popularity,
                genres = item.genreIds.orEmpty().toSet(),
                externalIds = mapOf("tmdb" to item.id.toString()),
                isAgeRestricted = item.adult == true
            )
        }
    }
}
