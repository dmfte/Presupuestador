package com.tuapp.fintrack.domain.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset

object Dates {

    /** Local instant -> the UTC-midnight millis Material DatePicker expects for that local calendar day. */
    fun localMillisToPickerMillis(ms: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
            .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun pickerDate(pickerMs: Long): LocalDate =
        Instant.ofEpochMilli(pickerMs).atZone(ZoneOffset.UTC).toLocalDate()

    /** Picker day at local start-of-day (use for period starts). */
    fun pickerMillisToLocalStartOfDay(pickerMs: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        pickerDate(pickerMs).atStartOfDay(zone).toInstant().toEpochMilli()

    /** Picker day combined with [time] (use for transactions: keeps ordering within the day). */
    fun pickerMillisToLocal(
        pickerMs: Long,
        time: LocalTime = LocalTime.now(),
        zone: ZoneId = ZoneId.systemDefault()
    ): Long = pickerDate(pickerMs).atTime(time).atZone(zone).toInstant().toEpochMilli()

    /** Inclusive [start, end] millis of a calendar month (1-based [month]) in [zone]. */
    fun monthBounds(year: Int, month: Int, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val ym = YearMonth.of(year, month)
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val nextStart = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return start to nextStart - 1
    }

    /** Local wall-clock time of [ms] in [zone]. */
    fun localTimeOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalTime =
        Instant.ofEpochMilli(ms).atZone(zone).toLocalTime()

    /** Year and 1-based month containing [ms] in [zone]. */
    fun yearMonthOf(ms: Long, zone: ZoneId = ZoneId.systemDefault()): YearMonth =
        YearMonth.from(Instant.ofEpochMilli(ms).atZone(zone))
}
