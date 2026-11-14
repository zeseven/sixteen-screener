package com.sixteenscreener.app.di

import com.sixteenscreener.app.data.repository.DefaultProfileRepository
import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.data.repository.WatchlistRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindProfileRepository(repository: DefaultProfileRepository): ProfileRepository

    @Binds
    abstract fun bindWatchlistRepository(repository: DefaultWatchlistRepository): WatchlistRepository
}
