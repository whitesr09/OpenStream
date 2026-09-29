package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalIdsDto(
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("tvdb_id") val tvdbId: Int? = null
)


@kotlinx.serialization.Serializable
data class WatchProviderResponseDto(
    val id: Int = 0,
    val results: Map<String, WatchProviderRegionDto> = emptyMap()
)

@kotlinx.serialization.Serializable
data class WatchProviderRegionDto(
    val link: String? = null,
    val flatrate: List<WatchProviderDto> = emptyList(),
    val free: List<WatchProviderDto> = emptyList(),
    val ads: List<WatchProviderDto> = emptyList(),
    val rent: List<WatchProviderDto> = emptyList(),
    val buy: List<WatchProviderDto> = emptyList()
)

@kotlinx.serialization.Serializable
data class WatchProviderDto(
    @kotlinx.serialization.SerialName("provider_id") val providerId: Int,
    @kotlinx.serialization.SerialName("provider_name") val providerName: String,
    @kotlinx.serialization.SerialName("logo_path") val logoPath: String? = null,
    @kotlinx.serialization.SerialName("display_priority") val displayPriority: Int = 999
)
