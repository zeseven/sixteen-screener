package com.sixteenscreener.app.di

import javax.inject.Qualifier

/** Backend base URL without a trailing slash; also the ID token audience. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class BackendUrl

/** Dispatcher for blocking I/O. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

/** Dispatcher for CPU-bound work. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

/** OkHttp client without authentication, used only for the token endpoint. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class TokenHttpClient

/** OkHttp client that adds the ID token to every request; shared by the API client and logo loading. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AuthenticatedHttpClient

/** Scope of work that outlives screens, such as repositories observing the active profile; lives as long as the process. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
