package com.tuapp.fintrack.ui.export

import com.google.common.truth.Truth.assertThat
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.domain.usecase.ExportTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExportFormattersTest {

    private val baseMs: Long = run {
        val cal = Calendar.getInstance(TimeZone.getDefault())
        cal.set(2025, Calendar.JUNE, 15, 12, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    private fun makeTx(
        type: TransactionType = TransactionType.EXPENSE,
        amountCents: Long = 1234L,
        categoryName: String? = "Groceries",
        description: String = "Weekly shopping",
        occurredAt: Long = baseMs
    ) = ExportTransaction(type, amountCents, categoryName, description, occurredAt)

    // ── CSV tests ─────────────────────────────────────────────────────────────

    @Test
    fun `csv starts with header row`() {
        val csv = buildCsvContent(emptyList())
        assertThat(csv.lines()[0]).isEqualTo("date,year,type,amount_usd,category,description")
    }

    @Test
    fun `csv includes one data row per transaction`() {
        val csv = buildCsvContent(listOf(makeTx(), makeTx()))
        val dataLines = csv.trim().lines().drop(1)
        assertThat(dataLines).hasSize(2)
    }

    @Test
    fun `csv amount formatted with two decimal places`() {
        val csv = buildCsvContent(listOf(makeTx(amountCents = 1234L)))
        assertThat(csv).contains("12.34")
    }

    @Test
    fun `csv year column derived from timestamp`() {
        val csv = buildCsvContent(listOf(makeTx(occurredAt = baseMs)))
        assertThat(csv).contains(",2025,")
    }

    @Test
    fun `escape csv field with comma wraps in quotes`() {
        val result = escapeCsvField("apples, oranges")
        assertThat(result).isEqualTo("\"apples, oranges\"")
    }

    @Test
    fun `escape csv field with double-quote doubles and wraps`() {
        val result = escapeCsvField("say \"hello\"")
        assertThat(result).isEqualTo("\"say \"\"hello\"\"\"")
    }

    @Test
    fun `escape csv field with newline wraps in quotes`() {
        val result = escapeCsvField("line1\nline2")
        assertThat(result).isEqualTo("\"line1\nline2\"")
    }

    @Test
    fun `escape csv field with no special chars returns as-is`() {
        val result = escapeCsvField("simple")
        assertThat(result).isEqualTo("simple")
    }

    @Test
    fun `csv round-trips description with comma`() {
        val tx = makeTx(description = "food, drinks")
        val csv = buildCsvContent(listOf(tx))
        assertThat(csv).contains("\"food, drinks\"")
    }

    @Test
    fun `csv empty transaction list produces only header`() {
        val csv = buildCsvContent(emptyList())
        assertThat(csv.trim().lines()).hasSize(1)
    }

    // ── JSON tests ────────────────────────────────────────────────────────────

    @Test
    fun `json is valid and parseable`() {
        val json = buildJsonContent(listOf(makeTx()))
        val parsed = Json.parseToJsonElement(json)
        assertThat(parsed).isInstanceOf(JsonArray::class.java)
    }

    @Test
    fun `json empty list produces empty array`() {
        val json = buildJsonContent(emptyList())
        val parsed = Json.parseToJsonElement(json).jsonArray
        assertThat(parsed).isEmpty()
    }

    @Test
    fun `json year key groups transactions correctly`() {
        val json = buildJsonContent(listOf(makeTx(occurredAt = baseMs)))
        val array = Json.parseToJsonElement(json).jsonArray
        assertThat(array).hasSize(1)
        val yearObj = array[0].jsonObject
        assertThat(yearObj.containsKey("2025")).isTrue()
    }

    @Test
    fun `json transactionCount matches transactions array length`() {
        val txs = listOf(makeTx(), makeTx(amountCents = 999L))
        val json = buildJsonContent(txs)
        val yearData = Json.parseToJsonElement(json).jsonArray[0].jsonObject["2025"]!!.jsonObject
        val count = yearData["transactionCount"]!!.jsonPrimitive.content.toInt()
        val txArray = yearData["transactions"]!!.jsonArray
        assertThat(count).isEqualTo(txArray.size)
    }

    @Test
    fun `json expenseTotalCents sums only expenses`() {
        val txs = listOf(
            makeTx(type = TransactionType.EXPENSE, amountCents = 1000L),
            makeTx(type = TransactionType.INCOME, amountCents = 5000L)
        )
        val json = buildJsonContent(txs)
        val yearData = Json.parseToJsonElement(json).jsonArray[0].jsonObject["2025"]!!.jsonObject
        val expenseTotal = yearData["expenseTotalCents"]!!.jsonPrimitive.content.toLong()
        assertThat(expenseTotal).isEqualTo(1000L)
    }

    @Test
    fun `json incomeTotalCents sums only income`() {
        val txs = listOf(
            makeTx(type = TransactionType.EXPENSE, amountCents = 1000L),
            makeTx(type = TransactionType.INCOME, amountCents = 5000L)
        )
        val json = buildJsonContent(txs)
        val yearData = Json.parseToJsonElement(json).jsonArray[0].jsonObject["2025"]!!.jsonObject
        val incomeTotal = yearData["incomeTotalCents"]!!.jsonPrimitive.content.toLong()
        assertThat(incomeTotal).isEqualTo(5000L)
    }

    @Test
    fun `json groups by year and sorts descending`() {
        val year2024Ms: Long = run {
            val cal = Calendar.getInstance(TimeZone.getDefault())
            cal.set(2024, Calendar.MARCH, 1, 12, 0, 0)
            cal.timeInMillis
        }
        val txs = listOf(
            makeTx(occurredAt = baseMs),       // 2025
            makeTx(occurredAt = year2024Ms)    // 2024
        )
        val json = buildJsonContent(txs)
        val array = Json.parseToJsonElement(json).jsonArray
        assertThat(array).hasSize(2)
        // First entry should be the more recent year (2025)
        assertThat(array[0].jsonObject.containsKey("2025")).isTrue()
        assertThat(array[1].jsonObject.containsKey("2024")).isTrue()
    }
}
