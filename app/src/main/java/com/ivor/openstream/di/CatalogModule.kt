package com.ivor.openstream.di

import com.ivor.openstream.data.repository.JikanCatalogProvider
import com.ivor.openstream.data.repository.TmdbCatalogProvider
import com.ivor.openstream.data.repository.TmdbIdentityGateway
import com.ivor.openstream.data.repository.TmdbIdentityGatewayImpl
import com.ivor.openstream.data.repository.TvMazeCatalogProvider
import com.ivor.openstream.domain.repository.CatalogProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @IntoSet
    fun provideTmdbCatalogProvider(provider: TmdbCatalogProvider): CatalogProvider = provider

    @Provides
    @IntoSet
    fun provideJikanCatalogProvider(provider: JikanCatalogProvider): CatalogProvider = provider

    @Provides
    @IntoSet
    fun provideTvMazeCatalogProvider(provider: TvMazeCatalogProvider): CatalogProvider = provider

    @Provides
    @Singleton
    fun provideTmdbIdentityGateway(provider: TmdbIdentityGatewayImpl): TmdbIdentityGateway = provider
}
