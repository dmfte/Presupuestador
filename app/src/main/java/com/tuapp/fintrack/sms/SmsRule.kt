package com.tuapp.fintrack.sms

import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.serialization.Serializable

@Serializable
data class SmsRule(
    val id: Long,
    val name: String = "",
    val identifierText: String,
    val amountPrefix: String,
    val type: TransactionType = TransactionType.EXPENSE,
    val categoryId: Long? = null,
    val descriptionTemplate: String = "",
    val enabled: Boolean = true
) {
    val displayName: String get() = name.ifBlank { identifierText }
}

object SmsRuleMatcher {

    /** Rules are checked in list order; the first enabled rule that parses an amount wins. */
    fun firstMatch(text: String, rules: List<SmsRule>): Pair<SmsRule, ParsedSmsTransaction>? =
        rules.asSequence()
            .filter { it.enabled }
            .firstNotNullOfOrNull { rule ->
                SmsTransactionParser.parse(text, rule.identifierText, rule.amountPrefix)?.let { rule to it }
            }

    /**
     * Maps a rule id to the earlier enabled rule with the same identifier + amount prefix.
     * Such a rule can never fire, because the earlier one always matches first.
     */
    fun shadowedBy(rules: List<SmsRule>): Map<Long, SmsRule> {
        val firstByKey = mutableMapOf<String, SmsRule>()
        val result = mutableMapOf<Long, SmsRule>()
        for (rule in rules) {
            val key = rule.matchKey()
            val earlier = firstByKey[key]
            if (earlier != null) {
                result[rule.id] = earlier
            } else if (rule.enabled) {
                firstByKey[key] = rule
            }
        }
        return result
    }

    private fun SmsRule.matchKey(): String =
        identifierText.trim().lowercase() + "\u0000" + amountPrefix.trim().lowercase()
}
