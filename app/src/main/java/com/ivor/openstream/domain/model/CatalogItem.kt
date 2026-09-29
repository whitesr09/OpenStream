package com.ivor.openstream.domain.model

data class CatalogItem(
    val sourceId: String,
    val title: String,
    val originalTitle: String? = null,
    val type: CatalogContentType,
    val year: Int? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val description: String? = null,
    val language: String? = null,
    val externalIds: Map<String, String> = emptyMap(),
    val isAgeRestricted: Boolean = false
)
