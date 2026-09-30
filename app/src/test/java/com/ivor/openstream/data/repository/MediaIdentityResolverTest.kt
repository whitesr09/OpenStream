package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.ExternalIdsDto
import com.ivor.openstream.data.remote.model.ExternalMediaLookupDto
import com.ivor.openstream.data.remote.model.TmdbResponse
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaIdentityResolverTest {
    @Test
    fun `IMDb external ID resolves to TMDB identity`() = kotlinx.coroutines.runBlocking {
        val gateway = FakeGateway(
            lookup = ExternalMediaLookupDto(
                movieResults = emptyList(),
                tvResults = listOf(anime(id = 42, mediaType = "tv", date = "2020-01-01"))
            ),
            externalIds = ExternalIdsDto(imdbId = "tt123")
        )
        val item = catalogItem(externalIds = mapOf("imdb" to "tt123"))

        val identity = MediaIdentityResolver(gateway).resolve(item).getOrThrow()

        assertEquals(42, identity?.tmdbId)
        assertEquals("tv", identity?.tmdbType)
        assertEquals("tt123", identity?.imdbId)
    }

    @Test
    fun `title and year fallback resolves only a strong match`() = kotlinx.coroutines.runBlocking {
        val gateway = FakeGateway(
            search = listOf(anime(id = 99, mediaType = "tv", date = "2005-09-22")),
            externalIds = ExternalIdsDto(imdbId = "tt999")
        )
        val item = catalogItem(title = "The Office", year = 2005)

        val identity = MediaIdentityResolver(gateway).resolve(item).getOrThrow()

        assertEquals(99, identity?.tmdbId)
        assertEquals(2005, identity?.year)
    }

    @Test
    fun `weak title mismatch is rejected instead of guessing`() = kotlinx.coroutines.runBlocking {
        val gateway = FakeGateway(
            search = listOf(anime(id = 100, mediaType = "tv", date = "2005-09-22", title = "Office Space"))
        )

        val identity = MediaIdentityResolver(gateway).resolve(catalogItem(title = "The Office", year = 2005)).getOrThrow()

        assertNull(identity)
    }

    private fun catalogItem(
        title: String = "The Office",
        year: Int? = 2005,
        externalIds: Map<String, String> = emptyMap()
    ) = CatalogItem(
        sourceId = "test:1",
        title = title,
        type = CatalogContentType.SERIES,
        year = year,
        externalIds = externalIds
    )

    private fun anime(
        id: Int,
        mediaType: String,
        date: String,
        title: String = "The Office"
    ) = AnimeDto(
        id = id,
        tvName = title,
        mediaType = mediaType,
        firstAirDate = date
    )

    private class FakeGateway(
        private val lookup: ExternalMediaLookupDto = ExternalMediaLookupDto(),
        private val search: List<AnimeDto> = emptyList(),
        private val externalIds: ExternalIdsDto = ExternalIdsDto()
    ) : TmdbIdentityGateway {
        override suspend fun findByExternalId(externalId: String, externalSource: String): ExternalMediaLookupDto = lookup

        override suspend fun searchMulti(query: String, page: Int): TmdbResponse<AnimeDto> =
            TmdbResponse(page = 1, results = search, totalPages = 1, totalResults = search.size)

        override suspend fun getExternalIds(mediaType: String, id: Int): ExternalIdsDto = externalIds
    }
}
