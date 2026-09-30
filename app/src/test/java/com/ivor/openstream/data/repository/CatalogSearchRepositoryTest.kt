package com.ivor.openstream.data.repository

import android.content.SharedPreferences
import com.ivor.openstream.data.settings.AppSettingsStore
import com.ivor.openstream.domain.model.CatalogContentType
import com.ivor.openstream.domain.model.CatalogItem
import com.ivor.openstream.domain.model.CatalogQuery
import com.ivor.openstream.domain.repository.CatalogProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class CatalogSearchRepositoryTest {
    @Test
    fun `provider failure is isolated from successful providers`() = kotlinx.coroutines.runBlocking {
        val good = FakeProvider("good", 0) { listOf(item("Good", 2020)) }
        val broken = FakeProvider("broken", 10) { error("boom") }

        val result = repository(setOf(good, broken)).search(CatalogQuery("query")).getOrThrow()

        assertEquals(listOf("Good"), result.map { it.title })
    }

    @Test
    fun `age restricted results are hidden by default`() = kotlinx.coroutines.runBlocking {
        val provider = FakeProvider("test", 0) {
            listOf(item("Public", 2020), item("Restricted", 2020, restricted = true))
        }

        val result = repository(setOf(provider)).search(CatalogQuery("query")).getOrThrow()

        assertEquals(listOf("Public"), result.map { it.title })
    }

    @Test
    fun `same external identity is merged across providers`() = kotlinx.coroutines.runBlocking {
        val tmdb = FakeProvider("tmdb", 0) {
            listOf(item("The Show", 2024, ids = mapOf("tmdb" to "123"), description = "TMDB"))
        }
        val other = FakeProvider("other", 20) {
            listOf(item("The Show", 2024, ids = mapOf("imdb" to "tt123"), poster = "poster", description = null))
        }

        val result = repository(setOf(other, tmdb)).search(CatalogQuery("show")).getOrThrow()

        assertEquals(1, result.size)
        assertEquals("TMDB", result.single().description)
        assertEquals(setOf("tmdb", "imdb"), result.single().externalIds.keys)
    }

    @Test
    fun `same title with different known years is not deduplicated`() = kotlinx.coroutines.runBlocking {
        val provider = FakeProvider("test", 0) {
            listOf(item("The Show", 2020), item("The Show", 2024))
        }

        val result = repository(setOf(provider)).search(CatalogQuery("show")).getOrThrow()

        assertEquals(2, result.size)
        assertTrue(result.map { it.year }.containsAll(listOf(2020, 2024)))
    }

    private fun repository(providers: Set<CatalogProvider>): CatalogSearchRepository =
        CatalogSearchRepository(providers, AppSettingsStore(emptyPreferences()))

    private fun item(
        title: String,
        year: Int?,
        ids: Map<String, String> = emptyMap(),
        description: String? = "description",
        poster: String? = null,
        restricted: Boolean = false
    ) = CatalogItem(
        sourceId = "test:$title:$year",
        title = title,
        type = CatalogContentType.SERIES,
        year = year,
        description = description,
        posterUrl = poster,
        externalIds = ids,
        isAgeRestricted = restricted
    )

    private class FakeProvider(
        override val id: String,
        override val priority: Int,
        private val response: suspend (CatalogQuery) -> List<CatalogItem>
    ) : CatalogProvider {
        override val displayName: String = id
        override suspend fun search(query: CatalogQuery): Result<List<CatalogItem>> =
            runCatching { response(query) }
    }

    private fun emptyPreferences(): SharedPreferences =
        Proxy.newProxyInstance(
            SharedPreferences::class.java.classLoader,
            arrayOf(SharedPreferences::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getString" -> args?.getOrNull(1)
                "getStringSet" -> args?.getOrNull(1)
                "getBoolean" -> args?.getOrNull(1) ?: false
                "getInt" -> args?.getOrNull(1) ?: 0
                "getLong" -> args?.getOrNull(1) ?: 0L
                "getFloat" -> args?.getOrNull(1) ?: 0f
                "contains" -> false
                else -> null
            }
        } as SharedPreferences
}
