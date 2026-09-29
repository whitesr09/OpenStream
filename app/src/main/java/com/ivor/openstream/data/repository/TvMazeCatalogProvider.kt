package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.TvMazeApi
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.CatalogQuery
import com.ivor.openstream.domain.repository.CatalogProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TvMazeCatalogProvider @Inject constructor(private val api: TvMazeApi) : CatalogProvider {
    override val id = "tvmaze"
    override val displayName = "TVmaze"
    override val priority = 30

    override suspend fun search(query: CatalogQuery): Result<List<CatalogItem>> = runCatching {
        if (query.types.isNotEmpty() && query.types.none { it == CatalogContentType.SERIES || it == CatalogContentType.OTHER }) {
            return@runCatching emptyList()
        }
        api.searchShows(query.text).map { result ->
            val show = result.show
            CatalogItem(
                sourceId = "tvmaze:show:${show.id}",
                title = show.name,
                type = CatalogContentType.SERIES,
                year = show.premiered?.take(4)?.toIntOrNull(),
                posterUrl = show.image?.original ?: show.image?.medium,
                description = show.summary?.replace(Regex("<[^>]*>"), ""),
                language = show.language,
                rating = show.rating?.average,
                popularity = result.score,
                externalIds = buildMap {
                    show.externals?.imdb?.let { put("imdb", it) }
                    show.externals?.thetvdb?.let { put("tvdb", it.toString()) }
                    show.externals?.tvrage?.let { put("tvrage", it.toString()) }
                },
                isAgeRestricted = false
            )
        }
    }
}
