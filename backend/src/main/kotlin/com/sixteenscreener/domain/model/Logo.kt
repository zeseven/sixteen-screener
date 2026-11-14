package com.sixteenscreener.domain.model

/**
 * Where a returned logo comes from.
 */
enum class LogoOrigin {
    /** Logo stored in the bucket. */
    STORED,

    /** Logo just downloaded from the logo source and stored. */
    DOWNLOADED,

    /** Default logo: the logo source has no logo for the company (recorded miss). */
    DEFAULT_MISSING,

    /** Default logo: the logo source could not be asked (limit reached, call failed or no source configured). */
    DEFAULT_UNAVAILABLE,
}

/**
 * SVG logo of a company.
 *
 * @property svg SVG document
 * @property origin where the logo comes from
 */
class Logo(
    val svg: ByteArray,
    val origin: LogoOrigin,
) {
    /** Whether the default logo is returned only because of a transient failure, so clients should ask again soon. */
    val isTemporary: Boolean get() = origin == LogoOrigin.DEFAULT_UNAVAILABLE
}
