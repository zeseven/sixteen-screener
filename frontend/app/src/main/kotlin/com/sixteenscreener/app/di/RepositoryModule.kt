package com.sixteenscreener.app.di

import com.sixteenscreener.app.data.repository.CompanyRepository
import com.sixteenscreener.app.data.repository.DefaultCompanyRepository
import com.sixteenscreener.app.data.repository.DefaultProfileRepository
import com.sixteenscreener.app.data.repository.DefaultSearchRepository
import com.sixteenscreener.app.data.repository.DefaultSourceRepository
import com.sixteenscreener.app.data.repository.DefaultWatchlistRepository
import com.sixteenscreener.app.data.repository.ProfileRepository
import com.sixteenscreener.app.data.repository.SearchRepository
import com.sixteenscreener.app.data.repository.SourceRepository
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

    @Binds
    abstract fun bindSearchRepository(repository: DefaultSearchRepository): SearchRepository

    @Binds
    abstract fun bindSourceRepository(repository: DefaultSourceRepository): SourceRepository

    @Binds
    abstract fun bindCompanyRepository(repository: DefaultCompanyRepository): CompanyRepository
}
