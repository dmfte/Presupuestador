package com.tuapp.fintrack.sms

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
        val amountStr = match.value.replace(",", "")
        val amount = amountStr.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val amountCents = (amount * 100).toLong()
        return ParsedSmsTransaction(amountCents = amountCents)
    }
}
