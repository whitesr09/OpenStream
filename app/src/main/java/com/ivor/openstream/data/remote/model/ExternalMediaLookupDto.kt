package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExternalMediaLookupDto(
    @SerialName("movie_results") val movieResults: List<AnimeDto> = emptyList(),
    @SerialName("tv_results") val tvResults: List<AnimeDto> = emptyList()
)
