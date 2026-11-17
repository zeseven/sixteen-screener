package com.sixteenscreener.app.domain.model

/**
 * Build information shown on the settings screen.
 */
data class AppInfo(
    /** App version name. */
    val version: String,
    /** Backend URL the app was built for. */
    val backendUrl: String,
    /** Build number: the count of commits on the first-parent line of the built commit. */
    val buildNumber: Int,
    /** Short hash of the commit the app was built from. */
    val commitHash: String,
    /** Whether the working tree had uncommitted or untracked changes at build time. */
    val isDirty: Boolean,
)
