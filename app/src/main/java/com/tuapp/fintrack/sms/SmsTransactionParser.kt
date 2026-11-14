package com.tuapp.fintrack.sms

import com.tuapp.fintrack.domain.util.Money

data class ParsedSmsTransaction(val amountCents: Long)

object SmsTransactionParser {

    private val amountRegex = Regex("""[\d,]+\.?\d{0,2}""")

    fun parse(
        notificationText: String,
        identifierText: String,
        amountPrefix: String
    ): ParsedSmsTransaction? {
        if (identifierText.isBlank() || amountPrefix.isBlank()) return null
        if (!notificationText.contains(identifierText, ignoreCase = true)) return null

        val prefixIndex = notificationText.indexOf(amountPrefix, ignoreCase = true)
        if (prefixIndex == -1) return null

        val afterPrefix = notificationText.substring(prefixIndex + amountPrefix.length).trimStart()
        val match = amountRegex.find(afterPrefix) ?: return null
        val amountCents = Money.parseToCentsOrNull(match.value) ?: return null
        if (amountCents <= 0L) return null
        return ParsedSmsTransaction(amountCents = amountCents)
    }
}
