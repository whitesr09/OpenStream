package com.ivor.openstream.di

import com.ivor.openstream.BuildConfig
import com.ivor.openstream.data.remote.GithubApi
import com.ivor.openstream.data.remote.JikanApi
import com.ivor.openstream.data.remote.TvMazeApi
import com.ivor.openstream.data.remote.TmdbApi
import com.ivor.openstream.data.settings.AppDns
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(): Interceptor = Interceptor { chain ->
        val original = chain.request()
        val originalHttpUrl = original.url
        val url = originalHttpUrl.newBuilder()
            .addQueryParameter("api_key", BuildConfig.TMDB_API_KEY)
            .apply {
                if (originalHttpUrl.host == "api.themoviedb.org" && originalHttpUrl.queryParameter("include_adult") == null) {
                    addQueryParameter("include_adult", "false")
                }
            }
            .build()
        chain.proceed(original.newBuilder().url(url).build())
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(authInterceptor: Interceptor, dns: AppDns): OkHttpClient =
        OkHttpClient.Builder()
            .dns(dns)
            .addInterceptor(authInterceptor)
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("StreamingClient")
    fun provideStreamingOkHttpClient(dns: AppDns): OkHttpClient =
        OkHttpClient.Builder()
            .dns(dns)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    @Named("Tmdb")
    fun provideTmdbRetrofit(okHttpClient: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideTmdbApi(@Named("Tmdb") retrofit: Retrofit): TmdbApi = retrofit.create(TmdbApi::class.java)

    @Provides
    @Singleton
    @Named("Jikan")
    fun provideJikanRetrofit(@Named("StreamingClient") client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.jikan.moe/v4/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideJikanApi(@Named("Jikan") retrofit: Retrofit): JikanApi = retrofit.create(JikanApi::class.java)

    @Provides
    @Singleton
    @Named("TvMaze")
    fun provideTvMazeRetrofit(@Named("StreamingClient") client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.tvmaze.com/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideTvMazeApi(@Named("TvMaze") retrofit: Retrofit): TvMazeApi = retrofit.create(TvMazeApi::class.java)

    @Provides
    @Singleton
    fun provideGithubApi(json: Json, dns: AppDns): GithubApi =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(
                OkHttpClient.Builder()
                    .dns(dns)
                    .addInterceptor { chain ->
                        chain.proceed(
                            chain.request().newBuilder()
                                .header("Accept", "application/vnd.github+json")
                                .build()
                        )
                    }
                    .build()
            )
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GithubApi::class.java)
}
