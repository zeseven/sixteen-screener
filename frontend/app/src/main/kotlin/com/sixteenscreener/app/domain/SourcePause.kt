package com.sixteenscreener.app.domain

import java.time.Clock
import java.time.Instant

/**
 * End of a source pause as shown on the Sources screen: in the clock's time zone, the time only when it ends today, otherwise
 * date and time; nothing when the source is not paused or the pause is already over.
 */
object SourcePause {

    fun endText(pausedUntil: Instant?, clock: Clock): String? =
        pausedUntil?.takeIf { it.isAfter(clock.instant()) }?.let { LimitResetTime.format(it, clock) }
}
