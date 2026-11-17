package com.sixteenscreener.domain.model

/**
 * Where a returned logo comes from.
 */
enum class LogoOrigin {
    /** Logo stored in the bucket. */
    STORED,

    /** Logo just downloaded from a logo source and stored. */
    DOWNLOADED,

    /** Default logo: every logo source that could be asked has no logo for the company (recorded misses). */
    DEFAULT_MISSING,

    /**
     * Default logo: a step was skipped because of a failure (storage error, source error, timeout or reached limit),
     * or no logo source is configured.
     */
    DEFAULT_UNAVAILABLE,
}

/**
 * Image format of a logo.
 *
 * @property contentType media type of the image
 * @property extension file name extension of a stored image
 */
enum class ImageFormat(val contentType: String, val extension: String) {
    /** SVG document. */
    SVG("image/svg+xml", "svg"),

    /** PNG image. */
    PNG("image/png", "png"),
}

/**
 * Logo of a company.
 *
 * @property bytes image content
 * @property format image format of [bytes]
 * @property origin where the logo comes from
 */
class Logo(
    val bytes: ByteArray,
    val format: ImageFormat,
    val origin: LogoOrigin,
) {
    /** Whether the default logo is returned only because of a transient failure, so clients should ask again soon. */
    val isTemporary: Boolean get() = origin == LogoOrigin.DEFAULT_UNAVAILABLE

    /** Whether the default logo is returned because the logo sources have no logo for the company. */
    val isMissing: Boolean get() = origin == LogoOrigin.DEFAULT_MISSING
}
