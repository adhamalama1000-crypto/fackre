package com.warehouse.inventory.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    /**
     * Display formats. Locale.US is used for the *pattern digits* on purpose: with an
     * Arabic locale, SimpleDateFormat renders Eastern Arabic numerals (١٤٤٥/٠٣/١٢), which
     * warehouse staff read slower than Western digits and which no spreadsheet will parse
     * after export. The surrounding UI stays Arabic; only the digits are Latin.
     */
    private val displayFormat = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    private val displayFormatWithTime = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.US)
    private val fileStampFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val monthFormat = SimpleDateFormat("yyyy-MM", Locale.US)

    /** Earliest business date the app accepts, to catch typos and bad pickers. */
    private const val MIN_VALID_DATE = 946_684_800_000L // 2000-01-01T00:00:00Z

    /** Returns [startOfToday, startOfTomorrow) in epoch millis. */
    fun todayRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = startOfDayCalendar(now)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return start to cal.timeInMillis
    }

    fun startOfDay(millis: Long): Long = startOfDayCalendar(millis).timeInMillis

    /** Last millisecond of the given day — for inclusive `date <= :to` bounds. */
    fun endOfDay(millis: Long): Long {
        val cal = startOfDayCalendar(millis)
        cal.add(Calendar.DAY_OF_MONTH, 1)
        return cal.timeInMillis - 1
    }

    /** Inclusive range covering the last [days] days, ending at the end of today. */
    fun lastDaysRange(days: Int, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = startOfDayCalendar(now)
        cal.add(Calendar.DAY_OF_MONTH, -(days - 1).coerceAtLeast(0))
        return cal.timeInMillis to endOfDay(now)
    }

    /** Inclusive range covering the calendar month containing [millis]. */
    fun monthRange(millis: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = startOfDayCalendar(millis)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return start to cal.timeInMillis - 1
    }

    /**
     * Rejects dates that cannot be a real business date: before 2000, or after the end of
     * today. Stock cannot be received or issued in the future.
     */
    fun isValidBusinessDate(millis: Long, now: Long = System.currentTimeMillis()): Boolean =
        millis in MIN_VALID_DATE..endOfDay(now)

    fun formatDate(millis: Long): String = displayFormat.format(Date(millis))

    fun formatDateTime(millis: Long): String = displayFormatWithTime.format(Date(millis))

    /** `yyyy-MM-dd`, for CSV cells and report grouping keys. */
    fun formatIsoDate(millis: Long): String = isoDateFormat.format(Date(millis))

    /** `yyyy-MM`, matching the monthly report's SQLite strftime grouping. */
    fun formatMonth(millis: Long): String = monthFormat.format(Date(millis))

    /** Filename-safe stamp, e.g. `20260817_142530`. */
    fun fileStamp(millis: Long = System.currentTimeMillis()): String =
        fileStampFormat.format(Date(millis))

    private fun startOfDayCalendar(millis: Long): Calendar = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
