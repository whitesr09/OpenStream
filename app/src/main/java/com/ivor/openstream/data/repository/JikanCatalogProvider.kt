package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.JikanApi
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.CatalogQuery
import com.ivor.openstream.domain.repository.CatalogProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JikanCatalogProvider @Inject constructor(private val api: JikanApi) : CatalogProvider {
    override val id = "jikan"
    override val displayName = "Jikan / MyAnimeList"
    override val priority = 20

    override suspend fun search(query: CatalogQuery): Result<List<CatalogItem>> = runCatching {
        if (query.types.isNotEmpty() && CatalogContentType.ANIME !in query.types) return@runCatching emptyList()
        api.searchAnime(query.text, query.page).data.map { item ->
            CatalogItem(
                sourceId = "jikan:anime:${item.malId}",
                title = item.titleEnglish?.takeIf { it.isNotBlank() } ?: item.title,
                originalTitle = item.titleJapanese,
                type = CatalogContentType.ANIME,
                year = item.aired?.from?.take(4)?.toIntOrNull(),
                posterUrl = item.images?.jpg?.largeImageUrl ?: item.images?.jpg?.imageUrl,
                description = item.synopsis,
                language = "ja",
                rating = item.score,
                popularity = item.popularity?.let { 1.0 / (it + 1) },
                genres = item.genres.mapNotNull { it.malId }.toSet(),
                externalIds = mapOf("mal" to item.malId.toString()),
                isAgeRestricted = false
            )
        }
    }
}
