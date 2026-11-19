package com.sixteenscreener.app.domain.model

/**
 * User profile identified by a backend-generated ID.
 */
data class Profile(
    /** Backend-generated profile ID used in all profile-scoped requests. */
    val id: ProfileId,
    /** Profile name as first entered, trimmed. */
    val name: String,
    /** Default source of each role, used when a request names no source. */
    val defaultSources: DefaultSources,
)

/**
 * Default source of each source role of a profile.
 */
data class DefaultSources(
    /** Source of the company search. */
    val search: SourceId,
    /** Source of the data of companies listed on US exchanges. */
    val us: SourceId,
    /** Source of the data of companies listed on any other exchange. */
    val international: SourceId,
)

/**
 * Profile the app works with, stored on the device after it was selected by name.
 */
data class ActiveProfile(
    /** Backend-generated profile ID used in all profile-scoped requests. */
    val id: ProfileId,
    /** Profile name as returned by the backend. */
    val name: String,
)
