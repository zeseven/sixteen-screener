package com.sixteenscreener.app.di

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import coil3.serviceLoaderEnabled
import coil3.svg.SvgDecoder
import com.sixteenscreener.app.data.remote.logo.BackendRelativeUrlMapper
import dagger.Lazy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ImageModule {
    private const val MEMORY_CACHE_PERCENT = 0.15
    private const val DISK_CACHE_BYTES = 32L * 1024 * 1024
    private const val DISK_CACHE_DIRECTORY = "logos"

    @Provides
    @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        @AuthenticatedHttpClient httpClient: Lazy<OkHttpClient>,
        @BackendUrl backendUrl: String,
    ): ImageLoader = ImageLoader.Builder(context)
        .serviceLoaderEnabled(false)
        .components {
            add(BackendRelativeUrlMapper(backendUrl))
            add(OkHttpNetworkFetcherFactory(callFactory = { httpClient.get() }))
            add(SvgDecoder.Factory())
        }
        .memoryCache { MemoryCache.Builder().maxSizePercent(context, MEMORY_CACHE_PERCENT).build() }
        .diskCache {
            DiskCache.Builder()
                .directory(context.cacheDir.resolve(DISK_CACHE_DIRECTORY))
                .maxSizeBytes(DISK_CACHE_BYTES)
                .build()
        }
        .crossfade(true)
        .build()
}
