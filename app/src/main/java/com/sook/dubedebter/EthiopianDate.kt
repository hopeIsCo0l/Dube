package com.sook.dubedebter

import java.util.TimeZone

internal data class EthiopianDate(val year: Int, val month: Int, val day: Int)

/**
 * Converts the local civil date of an instant using the Ethiopic Amete Mihret epoch.
 * Uses Julian day arithmetic so Android 6 (API 23) is supported without ICU APIs.
 * Reference: https://www.geez.org/Calendars/ (Beyene–Kudlek algorithm).
 */
internal fun ethiopianDate(
    timestamp: Long,
    timeZone: TimeZone = TimeZone.getDefault()
): EthiopianDate {
    val localMillis = timestamp + timeZone.getOffset(timestamp)
    val julianDay = localMillis.floorDiv(86_400_000L) + 2_440_588L
    val elapsed = julianDay - 1_723_856L
    val cycle = elapsed.floorDiv(1461L)
    val remainder = elapsed - cycle * 1461L
    val dayOfYear = remainder % 365L + 365L * (remainder / 1460L)
    val year = 4L * cycle + remainder / 365L - remainder / 1460L
    return EthiopianDate(year.toInt(), (dayOfYear / 30L + 1L).toInt(), (dayOfYear % 30L + 1L).toInt())
}
