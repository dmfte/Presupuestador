package com.tuapp.fintrack.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

class DatesTest {

    private val sv = ZoneId.of("America/El_Salvador") // UTC-6
    private val tokyo = ZoneId.of("Asia/Tokyo")       // UTC+9
    private val nov14Picker = LocalDate.of(2026, 11, 14).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `picker day maps to the same local calendar day`() {
        for (zone in listOf(sv, tokyo)) {
            val local = Dates.pickerMillisToLocal(nov14Picker, LocalTime.of(10, 30), zone)
            assertThat(LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(local), zone).toLocalDate())
                .isEqualTo(LocalDate.of(2026, 11, 14))
        }
    }

    @Test
    fun `local evening maps back to the same picker day`() {
        val eveningSv = LocalDateTime.of(2026, 11, 14, 21, 0).atZone(sv).toInstant().toEpochMilli()
        assertThat(Dates.localMillisToPickerMillis(eveningSv, sv)).isEqualTo(nov14Picker)
        val morningTokyo = LocalDateTime.of(2026, 11, 14, 2, 0).atZone(tokyo).toInstant().toEpochMilli()
        assertThat(Dates.localMillisToPickerMillis(morningTokyo, tokyo)).isEqualTo(nov14Picker)
    }

    @Test
    fun `start of day is local midnight`() {
        val ms = Dates.pickerMillisToLocalStartOfDay(nov14Picker, sv)
        assertThat(ms).isEqualTo(LocalDate.of(2026, 11, 14).atStartOfDay(sv).toInstant().toEpochMilli())
    }

    @Test
    fun `month bounds cover whole month`() {
        val (start, end) = Dates.monthBounds(2026, 2, sv)
        assertThat(start).isEqualTo(LocalDate.of(2026, 2, 1).atStartOfDay(sv).toInstant().toEpochMilli())
        assertThat(end + 1).isEqualTo(LocalDate.of(2026, 3, 1).atStartOfDay(sv).toInstant().toEpochMilli())
    }
}
