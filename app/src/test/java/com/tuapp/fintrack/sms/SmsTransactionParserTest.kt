package com.tuapp.fintrack.sms

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SmsTransactionParserTest {

    @Test
    fun `happy path - extracts amount after prefix`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Compra aprobada por $45.00 en Tienda X",
            identifierText = "Compra aprobada",
            amountPrefix = "$"
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(4500L)
    }

    @Test
    fun `amount with comma separator`() {
        val result = SmsTransactionParser.parse(
            notificationText = "BAC Credomatic: Compra USD 1,234.56",
            identifierText = "BAC Credomatic",
            amountPrefix = "USD "
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(123456L)
    }

    @Test
    fun `amount without decimals`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Transfer received: $500",
            identifierText = "Transfer received",
            amountPrefix = "$"
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(50000L)
    }

    @Test
    fun `identifier not found returns null`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Your order has shipped",
            identifierText = "Compra aprobada",
            amountPrefix = "$"
        )
        assertThat(result).isNull()
    }

    @Test
    fun `amount prefix not found returns null`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Compra aprobada por cuarenta y cinco dolares",
            identifierText = "Compra aprobada",
            amountPrefix = "$"
        )
        assertThat(result).isNull()
    }

    @Test
    fun `no numeric content after prefix returns null`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Compra aprobada por $ en Tienda",
            identifierText = "Compra aprobada",
            amountPrefix = "$ "
        )
        assertThat(result).isNull()
    }

    @Test
    fun `case insensitive identifier matching`() {
        val result = SmsTransactionParser.parse(
            notificationText = "COMPRA APROBADA por $25.00",
            identifierText = "compra aprobada",
            amountPrefix = "$"
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(2500L)
    }

    @Test
    fun `case insensitive prefix matching`() {
        val result = SmsTransactionParser.parse(
            notificationText = "BAC: Compra USD 50.00",
            identifierText = "BAC",
            amountPrefix = "usd "
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(5000L)
    }

    @Test
    fun `zero amount returns null`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Compra aprobada por $0.00",
            identifierText = "Compra aprobada",
            amountPrefix = "$"
        )
        assertThat(result).isNull()
    }

    @Test
    fun `blank identifier returns null`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Compra aprobada por $10.00",
            identifierText = "",
            amountPrefix = "$"
        )
        assertThat(result).isNull()
    }

    @Test
    fun `blank prefix returns null`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Compra aprobada por $10.00",
            identifierText = "Compra",
            amountPrefix = "  "
        )
        assertThat(result).isNull()
    }

    @Test
    fun `amount with single decimal digit`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Charge: $12.5 at store",
            identifierText = "Charge",
            amountPrefix = "$"
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(1250L)
    }

    @Test
    fun `prefix with colon and space`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Banco Nacional: Compra realizada. Monto: $99.99",
            identifierText = "Banco Nacional",
            amountPrefix = "Monto: $"
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(9999L)
    }

    @Test
    fun `multiple amounts takes first after prefix`() {
        val result = SmsTransactionParser.parse(
            notificationText = "Compra $30.00, balance $1,500.00",
            identifierText = "Compra",
            amountPrefix = "$"
        )
        assertThat(result).isNotNull()
        assertThat(result!!.amountCents).isEqualTo(3000L)
    }
}
