package com.ivor.openstream.di

import com.ivor.openstream.data.repository.TmdbCatalogProvider
import com.ivor.openstream.domain.repository.CatalogProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    @IntoSet
    fun provideTmdbCatalogProvider(provider: TmdbCatalogProvider): CatalogProvider = provider
}
