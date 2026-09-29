package com.ivor.openstream.data.repository

import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.CatalogQuery
import com.ivor.openstream.domain.repository.CatalogProvider
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogSearchRepository @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards CatalogProvider>
) {
    suspend fun search(query: CatalogQuery): Result<List<CatalogItem>> = runCatching {
        if (query.text.isBlank()) return@runCatching emptyList()
        coroutineScope {
            providers.sortedBy { it.priority }.map { provider ->
                async { provider.search(query).getOrDefault(emptyList()) }
            }.awaitAll().flatten()
                .filter { query.includeAgeRestricted || !it.isAgeRestricted }
                .distinctBy { "${it.sourceId}:${it.title.lowercase()}:${it.year ?: 0}" }
        }
    }
}
