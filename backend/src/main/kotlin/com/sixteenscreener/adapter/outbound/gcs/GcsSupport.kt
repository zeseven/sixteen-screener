package com.sixteenscreener.adapter.outbound.gcs

import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions

object GcsClientFactory {
    fun create(projectId: String): Storage =
        StorageOptions.newBuilder().setProjectId(projectId).build().service
}

object GcsPaths {
    const val LOGOS = "logos"
    const val DIRECTORY = "directory"
}
