package com.sixteenscreener.app.domain

/**
 * Validated profile name: trimmed, 1 to [MAX_LENGTH] characters (Unicode code points), matching the backend rule.
 */
@JvmInline
value class ProfileName private constructor(val value: String) {

    companion object {
        const val MAX_LENGTH = 40

        fun parse(raw: String): ProfileNameValidation {
            val trimmed = raw.trim()
            return when {
                trimmed.isEmpty() -> ProfileNameValidation.Blank
                trimmed.codePointCount(0, trimmed.length) > MAX_LENGTH -> ProfileNameValidation.TooLong
                else -> ProfileNameValidation.Valid(ProfileName(trimmed))
            }
        }
    }
}

/**
 * Outcome of [ProfileName.parse].
 */
sealed interface ProfileNameValidation {
    data class Valid(val name: ProfileName) : ProfileNameValidation

    data object Blank : ProfileNameValidation

    data object TooLong : ProfileNameValidation
}
