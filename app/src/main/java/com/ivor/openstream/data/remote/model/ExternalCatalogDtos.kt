package com.ivor.openstream.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable data class JikanSearchResponse(val data: List<JikanAnimeDto> = emptyList())

@Serializable
data class JikanAnimeDto(
    @SerialName("mal_id") val malId: Int,
    val title: String = "",
    @SerialName("title_english") val titleEnglish: String? = null,
    @SerialName("title_japanese") val titleJapanese: String? = null,
    val synopsis: String? = null,
    val images: JikanImages? = null,
    val aired: JikanAired? = null,
    val score: Double? = null,
    val popularity: Int? = null,
    val type: String? = null,
    val genres: List<JikanGenre> = emptyList()
)
@Serializable data class JikanImages(val jpg: JikanImageSet? = null)
@Serializable data class JikanImageSet(@SerialName("image_url") val imageUrl: String? = null, @SerialName("large_image_url") val largeImageUrl: String? = null)
@Serializable data class JikanAired(@SerialName("from") val from: String? = null)
@Serializable data class JikanGenre(val malId: Int? = null, val name: String = "")

@Serializable data class TvMazeSearchResult(val score: Double = 0.0, val show: TvMazeShow)
@Serializable
data class TvMazeShow(
    val id: Int,
    val name: String,
    val language: String? = null,
    val premiered: String? = null,
    val rating: TvMazeRating? = null,
    val image: TvMazeImage? = null,
    val summary: String? = null,
    val externals: TvMazeExternals? = null
)
@Serializable data class TvMazeRating(val average: Double? = null)
@Serializable data class TvMazeImage(val medium: String? = null, val original: String? = null)
@Serializable
data class TvMazeExternals(
    val tvrage: Int? = null,
    val thetvdb: Int? = null,
    val imdb: String? = null
)
