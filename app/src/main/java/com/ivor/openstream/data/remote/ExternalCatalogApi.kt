package com.ivor.openstream.data.remote

import com.ivor.openstream.data.remote.model.JikanSearchResponse
import com.ivor.openstream.data.remote.model.TvMazeSearchResult
import retrofit2.http.GET
import retrofit2.http.Query

interface JikanApi {
    @GET("anime")
    suspend fun searchAnime(@Query("q") query: String, @Query("page") page: Int = 1): JikanSearchResponse
}

interface TvMazeApi {
    @GET("search/shows")
    suspend fun searchShows(@Query("q") query: String): List<TvMazeSearchResult>
}
