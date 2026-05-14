package com.tuapp.fintrack.ui.export

import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.domain.usecase.ExportTransaction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal fun buildCsvContent(transactions: List<ExportTransaction>): String {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    val sb = StringBuilder()
    sb.append("date,year,type,amount_usd,category,description,is_pay_event\n")
    for (tx in transactions) {
        val date = dateFmt.format(Date(tx.occurredAt))
        val year = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = tx.occurredAt
        }.get(Calendar.YEAR)
        val amountUsd = "%.2f".format(tx.amountCents / 100.0)
        val category = escapeCsvField(tx.categoryName ?: "")
        val description = escapeCsvField(tx.description)
        sb.append("$date,$year,${tx.type},$amountUsd,$category,$description,${tx.isPayEvent}\n")
    }
    return sb.toString()
}

internal fun escapeCsvField(field: String): String {
    return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
        "\"${field.replace("\"", "\"\"")}\""
    } else {
        field
    }
}

internal fun buildJsonContent(transactions: List<ExportTransaction>): String {
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    val byYear = mutableMapOf<Int, MutableList<ExportTransaction>>()
    for (tx in transactions) {
        val year = Calendar.getInstance(TimeZone.getDefault()).apply {
            timeInMillis = tx.occurredAt
        }.get(Calendar.YEAR)
        byYear.getOrPut(year) { mutableListOf() }.add(tx)
    }

    val result = mutableListOf<JsonObject>()
    for ((year, txs) in byYear.toSortedMap(reverseOrder())) {
        val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
        val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }

        val txJson = txs.map { tx ->
            JsonObject(
                mapOf(
                    "date" to JsonPrimitive(dateFmt.format(Date(tx.occurredAt))),
                    "type" to JsonPrimitive(tx.type.name),
                    "amount_cents" to JsonPrimitive(tx.amountCents),
                    "category" to (tx.categoryName?.let { JsonPrimitive(it) } ?: JsonNull),
                    "description" to JsonPrimitive(tx.description),
                    "is_pay_event" to JsonPrimitive(tx.isPayEvent)
                )
            )
        }

        val yearData = JsonObject(
            mapOf(
                "transactionCount" to JsonPrimitive(txs.size),
                "incomeTotalCents" to JsonPrimitive(income),
                "expenseTotalCents" to JsonPrimitive(expense),
                "transactions" to JsonArray(txJson)
            )
        )

        result.add(JsonObject(mapOf(year.toString() to yearData)))
    }

    return Json { prettyPrint = true }.encodeToString(JsonArray.serializer(), JsonArray(result))
}
