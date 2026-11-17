package com.sixteenscreener.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import com.sixteenscreener.app.data.local.DataStoreProfileStore
import com.sixteenscreener.app.data.local.DataStoreWatchlistStore
import com.sixteenscreener.app.data.local.ProfileStore
import com.sixteenscreener.app.data.local.WatchlistStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StorageModule {

    @Binds
    abstract fun bindProfileStore(store: DataStoreProfileStore): ProfileStore

    @Binds
    abstract fun bindWatchlistStore(store: DataStoreWatchlistStore): WatchlistStore

    companion object {
        private const val PREFERENCES_FILE = "settings"
        private const val WATCHLIST_CACHE_FILE = "watchlist_cache"

        @Provides
        @Singleton
        fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(produceFile = { context.preferencesDataStoreFile(PREFERENCES_FILE) })

        @Provides
        @Singleton
        @WatchlistCache
        fun provideWatchlistCacheDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
                produceFile = { context.preferencesDataStoreFile(WATCHLIST_CACHE_FILE) },
            )
    }
}
