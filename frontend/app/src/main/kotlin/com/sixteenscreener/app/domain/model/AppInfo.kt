package com.sixteenscreener.app.domain.model

/**
 * Build information shown on the settings screen.
 */
data class AppInfo(
    /** App version name. */
    val version: String,
    /** Backend URL the app was built for. */
    val backendUrl: String,
)
