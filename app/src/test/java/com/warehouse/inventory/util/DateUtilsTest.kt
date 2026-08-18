package com.warehouse.inventory.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Business-date validation and the range helpers the reports depend on.
 *
 * Plain JUnit, no Robolectric: [DateUtils] is pure JVM code, and keeping it that way makes
 * this the fast part of the suite.
 */
class DateUtilsTest {

    /** A fixed "now" so the assertions do not depend on when the suite runs. */
    private val now = Calendar.getInstance().apply {
        set(2026, Calendar.MARCH, 15, 14, 30, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val oneDay = 24 * 60 * 60 * 1000L

    @Test
    fun `today is a valid business date`() {
        assertTrue(DateUtils.isValidBusinessDate(now, now))
    }

    @Test
    fun `the end of today is still valid`() {
        // The picker hands back midnight UTC for the chosen day, and a receipt entered late
        // in the day must not be rejected for being "in the future".
        assertTrue(DateUtils.isValidBusinessDate(DateUtils.endOfDay(now), now))
    }

    @Test
    fun `a future date is rejected`() {
        // Stock cannot be received or issued before it happens.
        assertFalse(DateUtils.isValidBusinessDate(now + oneDay, now))
        assertFalse(DateUtils.isValidBusinessDate(now + 365 * oneDay, now))
    }

    @Test
    fun `a past date is accepted`() {
        assertTrue(DateUtils.isValidBusinessDate(now - oneDay, now))
        assertTrue(DateUtils.isValidBusinessDate(now - 365 * oneDay, now))
    }

    @Test
    fun `an implausibly old date is rejected`() {
        // Catches a mistyped or zero timestamp rather than storing a movement dated 1970.
        assertFalse(DateUtils.isValidBusinessDate(0L, now))
        assertFalse(DateUtils.isValidBusinessDate(-1L, now))
    }

    @Test
    fun `today range covers midnight to midnight`() {
        val (start, end) = DateUtils.todayRange(now)

        assertTrue(start <= now)
        assertTrue(now < end)
        // Half-open by one day, which is what the dashboard's `date >= start AND date < end`
        // aggregates rely on.
        assertEquals(oneDay, end - start)
        assertEquals(start, DateUtils.startOfDay(now))
    }

    @Test
    fun `last days range is inclusive of today and of the first day`() {
        val (start, end) = DateUtils.lastDaysRange(7, now)

        assertEquals(DateUtils.endOfDay(now), end)
        // 7 days inclusive means 6 whole days back from the start of today.
        assertEquals(DateUtils.startOfDay(now) - 6 * oneDay, start)
    }

    @Test
    fun `last days range of one day is just today`() {
        val (start, end) = DateUtils.lastDaysRange(1, now)

        assertEquals(DateUtils.startOfDay(now), start)
        assertEquals(DateUtils.endOfDay(now), end)
    }

    @Test
    fun `month range covers the whole calendar month`() {
        val (start, end) = DateUtils.monthRange(now)

        val startCal = Calendar.getInstance().apply { timeInMillis = start }
        assertEquals(1, startCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, startCal.get(Calendar.MONTH))

        val endCal = Calendar.getInstance().apply { timeInMillis = end }
        assertEquals(31, endCal.get(Calendar.DAY_OF_MONTH))
        assertEquals(Calendar.MARCH, endCal.get(Calendar.MONTH))
    }

    @Test
    fun `dates format with latin digits`() {
        // Deliberate: Eastern Arabic numerals slow warehouse staff down and no spreadsheet
        // parses them after export, so only the digits stay Latin while the UI is Arabic.
        val formatted = DateUtils.formatDate(now)

        assertEquals("2026/03/15", formatted)
        assertTrue(formatted.all { it.isDigit() || it == '/' })
    }

    @Test
    fun `iso and month formats match the report grouping keys`() {
        // These must agree with the SQLite strftime patterns in the activity reports.
        assertEquals("2026-03-15", DateUtils.formatIsoDate(now))
        assertEquals("2026-03", DateUtils.formatMonth(now))
    }

    @Test
    fun `file stamp is filename safe`() {
        val stamp = DateUtils.fileStamp(now)

        assertEquals("20260315_143000", stamp)
        assertTrue(stamp.all { it.isDigit() || it == '_' })
    }
}
