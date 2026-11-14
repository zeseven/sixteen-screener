package com.sixteenscreener.app.data.remote.auth

import android.content.Context
import com.sixteenscreener.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Provides the service account key used to mint ID tokens.
 */
fun interface ServiceAccountKeySource {
    /**
     * Loads and parses the key. Blocking; call it off the main thread.
     *
     * @throws InvalidServiceAccountKeyException when the key cannot be read or parsed
     */
    fun load(): ServiceAccountKey
}

class AssetServiceAccountKeySource @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : ServiceAccountKeySource {
    override fun load(): ServiceAccountKey {
        val json = try {
            context.assets.open(BuildConfig.SERVICE_ACCOUNT_KEY_ASSET).bufferedReader().use { it.readText() }
        } catch (exception: java.io.IOException) {
            throw InvalidServiceAccountKeyException("Service account key asset cannot be read", exception)
        }
        return ServiceAccountKey.parse(json)
    }
}
