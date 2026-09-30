package com.ivor.openstream.data.repository

import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.remote.model.AnimeDto
import com.ivor.openstream.data.remote.model.ExternalIdsDto
import com.ivor.openstream.data.remote.model.ExternalMediaLookupDto
import com.ivor.openstream.data.remote.model.TmdbResponse
import javax.inject.Inject
import javax.inject.Singleton

interface TmdbIdentityGateway {
    suspend fun findByExternalId(externalId: String, externalSource: String): ExternalMediaLookupDto
    suspend fun searchMulti(query: String, page: Int = 1): TmdbResponse<AnimeDto>
    suspend fun getExternalIds(mediaType: String, id: Int): ExternalIdsDto
}

@Singleton
class TmdbIdentityGatewayImpl @Inject constructor(
    private val api: TmdbApi
) : TmdbIdentityGateway {
    override suspend fun findByExternalId(externalId: String, externalSource: String): ExternalMediaLookupDto =
        api.findByExternalId(externalId, externalSource)

    override suspend fun searchMulti(query: String, page: Int): TmdbResponse<AnimeDto> =
        api.searchMulti(query, page)

    override suspend fun getExternalIds(mediaType: String, id: Int): ExternalIdsDto =
        api.getExternalIds(mediaType, id)
}
