package com.tuapp.fintrack.sms

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.TransactionType
import org.junit.Test

class SmsRuleMatcherTest {

    private val text = "BANCO AGRICOLA: Compra por USD 12.50 en SUPER SELECTOS"

    @Test
    fun `first enabled matching rule wins`() {
        val rules = listOf(
            SmsRule(id = 1, identifierText = "OTHER BANK", amountPrefix = "USD"),
            SmsRule(id = 2, identifierText = "BANCO AGRICOLA", amountPrefix = "USD", enabled = false),
            SmsRule(id = 3, identifierText = "banco agricola", amountPrefix = "USD", type = TransactionType.RESERVE),
            SmsRule(id = 4, identifierText = "SELECTOS", amountPrefix = "USD")
        )
        val (rule, parsed) = SmsRuleMatcher.firstMatch(text, rules)!!
        assertThat(rule.id).isEqualTo(3L)
        assertThat(parsed.amountCents).isEqualTo(1250L)
    }

    @Test
    fun `rule whose prefix is missing falls through to the next rule`() {
        val rules = listOf(
            SmsRule(id = 1, identifierText = "BANCO AGRICOLA", amountPrefix = "$"),
            SmsRule(id = 2, identifierText = "BANCO AGRICOLA", amountPrefix = "USD")
        )
        assertThat(SmsRuleMatcher.firstMatch(text, rules)!!.first.id).isEqualTo(2L)
    }

    @Test
    fun `no match returns null`() {
        assertThat(SmsRuleMatcher.firstMatch(text, listOf(SmsRule(id = 1, identifierText = "X", amountPrefix = "USD")))).isNull()
    }

    @Test
    fun `duplicate of an earlier enabled rule is flagged`() {
        val rules = listOf(
            SmsRule(id = 1, identifierText = "Bank ", amountPrefix = "USD"),
            SmsRule(id = 2, identifierText = "bank", amountPrefix = "usd"),
            SmsRule(id = 3, identifierText = "bank", amountPrefix = "$")
        )
        val shadowed = SmsRuleMatcher.shadowedBy(rules)
        assertThat(shadowed.keys).containsExactly(2L)
        assertThat(shadowed[2L]!!.id).isEqualTo(1L)
    }

    @Test
    fun `disabled earlier rule does not shadow`() {
        val rules = listOf(
            SmsRule(id = 1, identifierText = "bank", amountPrefix = "USD", enabled = false),
            SmsRule(id = 2, identifierText = "bank", amountPrefix = "USD")
        )
        assertThat(SmsRuleMatcher.shadowedBy(rules)).isEmpty()
    }
}
