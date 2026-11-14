package com.tuapp.fintrack.domain.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

/** Locale-independent money helpers. Never routes amounts through Float/Double. */
object Money {

    private val nonNumeric = Regex("[^0-9.,-]")

    /**
     * Parses user or SMS text to cents. Accepts "12.34", "12,34", "1,234.56", "1.234,56", "$ 12.3".
     * Returns null when the text is not a number.
     */
    fun parseToCentsOrNull(input: String): Long? {
        var s = input.trim().replace(nonNumeric, "")
        if (s.isEmpty()) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        s = when {
            // Both present: the right-most one is the decimal separator.
            lastDot >= 0 && lastComma >= 0 ->
                if (lastDot > lastComma) s.replace(",", "")
                else s.replace(".", "").replace(',', '.')
            // Only commas: decimal if exactly one comma followed by 1-2 digits, else thousands.
            lastComma >= 0 ->
                if (s.count { it == ',' } == 1 && s.length - lastComma - 1 in 1..2) s.replace(',', '.')
                else s.replace(",", "")
            // Several dots and no comma: thousands separators ("1.234.567").
            s.count { it == '.' } > 1 -> s.replace(".", "")
            else -> s
        }
        return try {
            BigDecimal(s).setScale(2, RoundingMode.HALF_UP).movePointRight(2).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }

    /** Plain "12.34" for text fields and CSV. Always '.' as decimal separator. */
    fun centsToPlain(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    /** "$12.34" in US format, independent of the device locale. */
    fun formatUsd(cents: Long): String =
        NumberFormat.getCurrencyInstance(Locale.US).format(BigDecimal.valueOf(cents, 2))
}
