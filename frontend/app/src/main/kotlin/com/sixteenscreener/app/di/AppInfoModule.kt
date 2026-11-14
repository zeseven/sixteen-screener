package com.sixteenscreener.app.di

import com.sixteenscreener.app.BuildConfig
import com.sixteenscreener.app.domain.model.AppInfo
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object AppInfoModule {
    @Provides
    fun provideAppInfo(@BackendUrl backendUrl: String): AppInfo = AppInfo(version = BuildConfig.VERSION_NAME, backendUrl = backendUrl)
}
