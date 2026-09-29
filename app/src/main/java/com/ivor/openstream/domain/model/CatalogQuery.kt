package com.ivor.openstream.domain.model

data class CatalogQuery(
    val text: String,
    val page: Int = 1,
    val pageSize: Int = 20,
    val types: Set<CatalogContentType> = emptySet(),
    val language: String? = null,
    val includeAgeRestricted: Boolean = false
)
