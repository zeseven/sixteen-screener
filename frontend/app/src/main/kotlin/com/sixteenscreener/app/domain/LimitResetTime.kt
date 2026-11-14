package com.sixteenscreener.app.domain

import java.time.Clock
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reset time of a source request limit in the clock's time zone: the time only when the reset is today, otherwise date and time.
 */
object LimitResetTime {
    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.US)
    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.US)

    fun format(resetsAt: Instant, clock: Clock): String {
        val reset = resetsAt.atZone(clock.zone)
        val today = clock.instant().atZone(clock.zone).toLocalDate()
        return (if (reset.toLocalDate() == today) TIME else DATE_TIME).format(reset)
    }
}
