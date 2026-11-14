package com.tuapp.fintrack.domain.util

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses values that truncate through Double`() {
        assertThat(Money.parseToCentsOrNull("19.99")).isEqualTo(1999L)
        assertThat(Money.parseToCentsOrNull("0.29")).isEqualTo(29L)
        assertThat(Money.parseToCentsOrNull("1.15")).isEqualTo(115L)
        assertThat(Money.parseToCentsOrNull("8.2")).isEqualTo(820L)
    }

    @Test
    fun `parses comma decimal and thousands separators`() {
        assertThat(Money.parseToCentsOrNull("12,50")).isEqualTo(1250L)
        assertThat(Money.parseToCentsOrNull("1,234.56")).isEqualTo(123456L)
        assertThat(Money.parseToCentsOrNull("1.234,56")).isEqualTo(123456L)
        assertThat(Money.parseToCentsOrNull("1,234")).isEqualTo(123400L)
        assertThat(Money.parseToCentsOrNull("$ 12.3")).isEqualTo(1230L)
    }

    @Test
    fun `returns null for non numbers`() {
        assertThat(Money.parseToCentsOrNull("")).isNull()
        assertThat(Money.parseToCentsOrNull("abc")).isNull()
        assertThat(Money.parseToCentsOrNull(".")).isNull()
    }

    @Test
    fun `formats with dot decimal regardless of locale`() {
        assertThat(Money.centsToPlain(1234L)).isEqualTo("12.34")
        assertThat(Money.centsToPlain(5L)).isEqualTo("0.05")
        assertThat(Money.formatUsd(123456L)).isEqualTo("$1,234.56")
    }
}
