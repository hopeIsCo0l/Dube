package com.sook.dubedebter

import java.util.GregorianCalendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Test

class EthiopianDateTest {
    private val utc = TimeZone.getTimeZone("UTC")
    private fun instant(year: Int, month: Int, day: Int, hour: Int = 0): Long =
        GregorianCalendar(utc).apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    @Test fun convertsOpeningDate() {
        assertEquals(EthiopianDate(2019, 3, 13), ethiopianDate(instant(2026, 11, 22), utc))
    }

    @Test fun handlesLeapDayAndNewYear() {
        assertEquals(EthiopianDate(2015, 13, 6), ethiopianDate(instant(2023, 9, 11), utc))
        assertEquals(EthiopianDate(2016, 1, 1), ethiopianDate(instant(2023, 9, 12), utc))
    }

    @Test fun handlesOrdinaryYearEnd() {
        assertEquals(EthiopianDate(2016, 13, 5), ethiopianDate(instant(2024, 9, 10), utc))
        assertEquals(EthiopianDate(2017, 1, 1), ethiopianDate(instant(2024, 9, 11), utc))
    }

    @Test fun usesSameLocalDateAsGregorianDisplay() {
        val timestamp = instant(2023, 9, 11, 22)
        assertEquals(EthiopianDate(2015, 13, 6), ethiopianDate(timestamp, utc))
        assertEquals(EthiopianDate(2016, 1, 1),
            ethiopianDate(timestamp, TimeZone.getTimeZone("Africa/Addis_Ababa")))
    }

    @Test fun handlesGregorianLeapDay() {
        assertEquals(EthiopianDate(2016, 6, 21), ethiopianDate(instant(2024, 2, 29), utc))
    }
}
