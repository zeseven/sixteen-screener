package com.sixteenscreener.domain.model

/**
 * Input rejected by domain validation. Mapped to `400 Bad Request` by the HTTP layer.
 */
class InvalidInputException(message: String) : RuntimeException(message)

/**
 * A requested resource (profile, company, source) does not exist. Mapped to `404 Not Found` by the HTTP layer.
 */
class ResourceNotFoundException(message: String) : RuntimeException(message)
