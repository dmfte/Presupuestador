package com.tuapp.fintrack.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PayCycleRuleTest {

    @Test
    fun `DayOfMonthRule serializes and deserializes`() {
        val rule = PayCycleRule.DayOfMonthRule(day = 15)
        val json = rule.toJson()
        val restored = json.toPayCycleRule()
        assertThat(restored).isEqualTo(rule)
        assertThat(json).contains("DayOfMonthRule")
        assertThat(json).contains("15")
    }

    @Test
    fun `LastDayOfMonthRule serializes and deserializes`() {
        val rule = PayCycleRule.LastDayOfMonthRule
        val json = rule.toJson()
        val restored = json.toPayCycleRule()
        assertThat(restored).isEqualTo(rule)
        assertThat(json).contains("LastDayOfMonthRule")
    }

    @Test
    fun `SpecificDateRule serializes and deserializes`() {
        val rule = PayCycleRule.SpecificDateRule(dateMs = 1_700_000_000_000L)
        val json = rule.toJson()
        val restored = json.toPayCycleRule()
        assertThat(restored).isEqualTo(rule)
    }

    @Test
    fun `WeeklyRule serializes and deserializes`() {
        val rule = PayCycleRule.WeeklyRule(dayOfWeekAnchor = 2)
        val json = rule.toJson()
        val restored = json.toPayCycleRule()
        assertThat(restored).isEqualTo(rule)
    }

    @Test
    fun `BiweeklyRule serializes and deserializes`() {
        val rule = PayCycleRule.BiweeklyRule(anchorDateMs = 1_700_000_000_000L, dayOfWeek = 2)
        val json = rule.toJson()
        val restored = json.toPayCycleRule()
        assertThat(restored).isEqualTo(rule)
    }

    @Test
    fun `seed format DayOfMonthRule parses correctly`() {
        val json = """{"type":"DayOfMonthRule","day":15}"""
        val rule = json.toPayCycleRule()
        assertThat(rule).isInstanceOf(PayCycleRule.DayOfMonthRule::class.java)
        assertThat((rule as PayCycleRule.DayOfMonthRule).day).isEqualTo(15)
    }

    @Test
    fun `seed format LastDayOfMonthRule parses correctly`() {
        val json = """{"type":"LastDayOfMonthRule"}"""
        val rule = json.toPayCycleRule()
        assertThat(rule).isEqualTo(PayCycleRule.LastDayOfMonthRule)
    }
}
