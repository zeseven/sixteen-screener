package com.sixteenscreener.adapter.outbound.gcs

import com.google.cloud.NoCredentials
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

object GcsClientFactory {
    fun create(projectId: String, emulatorHost: String? = null): Storage =
        StorageOptions.newBuilder()
            .setProjectId(projectId)
            .apply { emulatorHost?.let { setHost(it).setCredentials(NoCredentials.getInstance()) } }
            .build()
            .service
}

object GcsPaths {
    const val LOGOS = "logos"
    const val FINNHUB_LOGOS = "finnhub-logos"
    const val DIRECTORY = "directory"
}
