package com.ivor.openstream.data.repository

import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.CatalogQuery
import com.ivor.openstream.domain.repository.CatalogProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Fan-out search across enabled providers with cross-provider identity-aware deduplication. */
@Singleton
class CatalogSearchRepository @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards CatalogProvider>,
    private val settingsStore: AppSettingsStore
) {
    suspend fun search(query: CatalogQuery): Result<List<CatalogItem>> = runCatching {
        if (query.text.isBlank()) return@runCatching emptyList()
        val enabled = providers
            .filter { it.id !in settingsStore.current.disabledCatalogProviders }
            .sortedBy { it.priority }

        val results = coroutineScope {
            enabled.map { provider ->
                async {
                    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
                        runCatching { provider.search(query).getOrDefault(emptyList()) }
                            .getOrDefault(emptyList())
                            .map { ProviderResult(provider, it) }
                    }.orEmpty()
                }
            }.awaitAll().flatten()
        }

        results
            .filter { query.includeAgeRestricted || !it.item.isAgeRestricted }
            .sortedWith(compareBy<ProviderResult> { it.provider.priority }.thenByDescending { it.item.popularity ?: 0.0 })
            .let(::deduplicate)
            .map { it.item }
    }

    private fun deduplicate(results: List<ProviderResult>): List<ProviderResult> {
        val merged = mutableListOf<ProviderResult>()
        for (candidate in results) {
            val matchIndex = merged.indexOfFirst { sameCanonicalIdentity(it.item, candidate.item) }
            if (matchIndex < 0) merged += candidate else merged[matchIndex] = merge(merged[matchIndex], candidate)
        }
        return merged
    }

    private fun sameCanonicalIdentity(a: CatalogItem, b: CatalogItem): Boolean {
        if (a.type.toIdentityType() != b.type.toIdentityType()) return false
        val aIds = identityIds(a)
        val bIds = identityIds(b)
        if (aIds.isNotEmpty() && bIds.isNotEmpty() && aIds.any { it in bIds }) return true

        val aTitles = titleKeys(a)
        val bTitles = titleKeys(b)
        if (aTitles.none { it in bTitles }) return false
        return when {
            a.year != null && b.year != null -> a.year == b.year
            a.year == null && b.year == null -> true
            else -> false
        }
    }

    private fun merge(a: ProviderResult, b: ProviderResult): ProviderResult {
        val preferred = if (a.provider.priority <= b.provider.priority) a else b
        val secondary = if (preferred === a) b else a
        return preferred.copy(item = preferred.item.copy(
            originalTitle = preferred.item.originalTitle ?: secondary.item.originalTitle,
            year = preferred.item.year ?: secondary.item.year,
            posterUrl = preferred.item.posterUrl ?: secondary.item.posterUrl,
            backdropUrl = preferred.item.backdropUrl ?: secondary.item.backdropUrl,
            description = preferred.item.description ?: secondary.item.description,
            language = preferred.item.language ?: secondary.item.language,
            rating = preferred.item.rating ?: secondary.item.rating,
            popularity = preferred.item.popularity ?: secondary.item.popularity,
            genres = preferred.item.genres.ifEmpty { secondary.item.genres },
            externalIds = secondary.item.externalIds + preferred.item.externalIds,
            isAgeRestricted = preferred.item.isAgeRestricted || secondary.item.isAgeRestricted
        ))
    }

    private fun identityIds(item: CatalogItem): Set<String> = item.externalIds
        .filterKeys { it.lowercase() in ID_NAMESPACES }
        .map { (namespace, value) -> namespace.lowercase() + ":" + value.trim().lowercase() }
        .filter { it.substringAfter(':').isNotBlank() }
        .toSet()

    private fun titleKeys(item: CatalogItem): Set<String> = listOfNotNull(item.title, item.originalTitle)
        .map(::normalizeTitle).filter(String::isNotBlank).toSet()

    private fun normalizeTitle(value: String): String = value.lowercase()
        .replace("&", " and ").replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        .replace(Regex("\\s+"), " ")

    private fun CatalogContentType.toIdentityType(): String = when (this) {
        CatalogContentType.MOVIE -> "movie"
        CatalogContentType.EPISODE -> "episode"
        CatalogContentType.SHORT -> "short"
        CatalogContentType.LIVE -> "live"
        CatalogContentType.MUSIC_VIDEO -> "music_video"
        CatalogContentType.DOCUMENTARY -> "documentary"
        CatalogContentType.ANIME, CatalogContentType.SERIES, CatalogContentType.OTHER -> "series"
    }

    private data class ProviderResult(val provider: CatalogProvider, val item: CatalogItem)

    private companion object {
        const val PROVIDER_TIMEOUT_MS = 8_000L
        val ID_NAMESPACES = setOf("tmdb", "imdb", "tvdb", "tvmaze", "anilist", "mal")
    }
}
