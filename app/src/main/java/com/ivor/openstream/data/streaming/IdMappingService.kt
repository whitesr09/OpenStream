package com.ivor.openstream.data.streaming

import com.ivor.openstream.data.local.dao.IdMappingDao
import com.ivor.openstream.data.local.entity.IdMappingEntity
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.domain.model.MediaIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class IdMappingService @Inject constructor(
    private val tmdbApi: TmdbApi,
    private val mappingDao: IdMappingDao
) {
    private val memoryCache = ConcurrentHashMap<String, String>()

    suspend fun enrich(identity: MediaIdentity): MediaIdentity = withContext(Dispatchers.IO) {
        if (!identity.imdbId.isNullOrBlank()) return@withContext identity

        val cacheKey = "tmdb-external:${identity.tmdbType}:${identity.tmdbId}"
        memoryCache[cacheKey]?.let { return@withContext identity.copy(imdbId = it) }

        val cached = mappingDao.get(cacheKey)
        if (cached != null && cached.providerMediaId.isNotBlank()) {
            memoryCache[cacheKey] = cached.providerMediaId
            return@withContext identity.copy(imdbId = cached.providerMediaId)
        }

        runCatching {
            val externalIds = tmdbApi.getExternalIds(identity.tmdbType, identity.tmdbId)
            val imdbId = externalIds.imdbId?.takeIf { it.isNotBlank() }
                ?: return@runCatching identity
            memoryCache[cacheKey] = imdbId
            mappingDao.insert(
                IdMappingEntity(
                    cacheKey = cacheKey,
                    providerId = "imdb",
                    providerMediaId = imdbId,
                    resolvedAt = System.currentTimeMillis()
                )
            )
            identity.copy(imdbId = imdbId)
        }.getOrDefault(identity)
    }
}
