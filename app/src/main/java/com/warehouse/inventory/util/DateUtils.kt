package com.warehouse.inventory.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {

    private val displayFormat = SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
    private val displayFormatWithTime = SimpleDateFormat("yyyy/MM/dd  HH:mm", Locale.getDefault())

    /** Returns [startOfToday, startOfTomorrow) in epoch millis. */
    fun todayRange(now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_MONTH, 1)
        val end = cal.timeInMillis
        return start to end
    }

    fun formatDate(millis: Long): String = displayFormat.format(Date(millis))

    fun formatDateTime(millis: Long): String = displayFormatWithTime.format(Date(millis))
}
