package com.ivor.openstream.domain.repository

import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.CatalogQuery

/** Search/catalog seam independent of TMDB. */
interface CatalogProvider {
    val id: String
    val displayName: String
    val priority: Int
    suspend fun search(query: CatalogQuery): Result<List<CatalogItem>>
}
