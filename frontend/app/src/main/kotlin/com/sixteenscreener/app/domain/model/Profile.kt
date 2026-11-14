package com.sixteenscreener.app.domain.model

/**
 * User profile identified by a backend-generated ID.
 */
data class Profile(
    /** Backend-generated profile ID used in all profile-scoped requests. */
    val id: ProfileId,
    /** Profile name as first entered, trimmed. */
    val name: String,
    /** Source used when a request names no source. */
    val defaultSource: SourceId,
)
