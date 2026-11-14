package com.sixteenscreener.app.di

import com.sixteenscreener.app.BuildConfig
import com.sixteenscreener.app.data.remote.auth.AssetServiceAccountKeySource
import com.sixteenscreener.app.data.remote.auth.AuthInterceptor
import com.sixteenscreener.app.data.remote.auth.IdTokenProvider
import com.sixteenscreener.app.data.remote.auth.ServiceAccountIdTokenProvider
import com.sixteenscreener.app.data.remote.auth.ServiceAccountKeySource
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.time.Duration
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {

    @Binds
    abstract fun bindIdTokenProvider(provider: ServiceAccountIdTokenProvider): IdTokenProvider

    @Binds
    abstract fun bindServiceAccountKeySource(source: AssetServiceAccountKeySource): ServiceAccountKeySource

    companion object {
        val CONNECT_TIMEOUT: Duration = Duration.ofSeconds(15)
        val READ_TIMEOUT: Duration = Duration.ofSeconds(30)

        @Provides
        @BackendUrl
        fun provideBackendUrl(): String = BuildConfig.BACKEND_URL

        @Provides
        @Singleton
        fun provideJson(): Json = Json {
            ignoreUnknownKeys = true
        }

        @Provides
        @Singleton
        @TokenHttpClient
        fun provideTokenHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(CONNECT_TIMEOUT)
            .readTimeout(READ_TIMEOUT)
            .build()

        @Provides
        @Singleton
        @AuthenticatedHttpClient
        fun provideAuthenticatedHttpClient(
            @TokenHttpClient baseClient: OkHttpClient,
            authInterceptor: AuthInterceptor,
        ): OkHttpClient = baseClient.newBuilder()
            .addInterceptor(authInterceptor)
            .build()
    }
}
