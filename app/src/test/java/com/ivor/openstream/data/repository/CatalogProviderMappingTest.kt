package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.JikanApi
import com.ivor.openstream.data.remote.TvMazeApi
import com.ivor.openstream.data.remote.model.JikanAired
import com.ivor.openstream.data.remote.model.JikanAnimeDto
import com.ivor.openstream.data.remote.model.JikanImageSet
import com.ivor.openstream.data.remote.model.JikanImages
import com.ivor.openstream.data.remote.model.JikanSearchResponse
import com.ivor.openstream.data.remote.model.TvMazeExternals
import com.ivor.openstream.data.remote.model.TvMazeImage
import com.ivor.openstream.data.remote.model.TvMazeRating
import com.ivor.openstream.data.remote.model.TvMazeSearchResult
import com.ivor.openstream.data.remote.model.TvMazeShow
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogProviderMappingTest {
    @Test
    fun `Jikan maps MAL identity and anime metadata`() = kotlinx.coroutines.runBlocking {
        val api = object : JikanApi {
            override suspend fun searchAnime(query: String, page: Int): JikanSearchResponse = JikanSearchResponse(
                data = listOf(
                    JikanAnimeDto(
                        malId = 21,
                        title = "Cowboy Bebop",
                        titleEnglish = "Cowboy Bebop",
                        titleJapanese = "カウボーイビバップ",
                        synopsis = "A space bounty hunter story",
                        images = JikanImages(JikanImageSet(largeImageUrl = "https://img.example/bebop.jpg")),
                        aired = JikanAired(from = "1998-04-03T00:00:00+00:00"),
                        score = 8.7,
                        popularity = 1,
                        type = "TV"
                    )
                )
            )
        }

        val result = JikanCatalogProvider(api).search(CatalogQuery("cowboy", types = setOf(CatalogContentType.ANIME))).getOrThrow().single()

        assertEquals("jikan:anime:21", result.sourceId)
        assertEquals(CatalogContentType.ANIME, result.type)
        assertEquals("21", result.externalIds["mal"])
        assertEquals("https://img.example/bebop.jpg", result.posterUrl)
        assertEquals(1998, result.year)
    }

    @Test
    fun `TVmaze preserves public external IDs and absolute artwork`() = kotlinx.coroutines.runBlocking {
        val api = object : TvMazeApi {
            override suspend fun searchShows(query: String): List<TvMazeSearchResult> = listOf(
                TvMazeSearchResult(
                    score = 12.0,
                    show = TvMazeShow(
                        id = 100,
                        name = "The Example Show",
                        language = "English",
                        premiered = "2024-01-01",
                        rating = TvMazeRating(8.2),
                        image = TvMazeImage(original = "https://static.tvmaze.com/example.jpg"),
                        summary = "<p>Example</p>",
                        externals = TvMazeExternals(imdb = "tt1234567", thetvdb = 7654321)
                    )
                )
            )
        }

        val result = TvMazeCatalogProvider(api).search(CatalogQuery("example", types = setOf(CatalogContentType.SERIES))).getOrThrow().single()

        assertEquals("tvmaze:show:100", result.sourceId)
        assertEquals("tt1234567", result.externalIds["imdb"])
        assertEquals("7654321", result.externalIds["tvdb"])
        assertEquals("https://static.tvmaze.com/example.jpg", result.posterUrl)
        assertEquals("Example", result.description)
        assertTrue(result.year == 2024)
    }
}
