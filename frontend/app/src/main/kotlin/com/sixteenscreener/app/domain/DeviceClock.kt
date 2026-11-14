package com.sixteenscreener.app.domain

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

/**
 * System clock in the device time zone at the time of each call, so a time zone change on the device is picked up immediately.
 */
class DeviceClock(private val zoneProvider: () -> ZoneId = ZoneId::systemDefault) : Clock() {
    override fun instant(): Instant = Instant.now()

    override fun getZone(): ZoneId = zoneProvider()

    override fun withZone(zone: ZoneId): Clock = system(zone)
}
