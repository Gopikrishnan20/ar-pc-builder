package com.arpcbuilder.di

import com.arpcbuilder.data.InMemoryComponentRepository
import com.arpcbuilder.domain.repository.ComponentRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {
    @Binds
    @Singleton
    abstract fun bindComponentRepository(impl: InMemoryComponentRepository): ComponentRepository
}
