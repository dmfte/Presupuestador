# SPRINT_6_EXPORT.md — CSV & JSON Export (Settings)

**Parent:** `CLAUDE.md`

---

## Overview

Implement Settings → "Export data" screen with CSV and JSON export buttons. These exports are **analysis-only** (no restore path). Pay events are included as synthetic income transactions. The exports live in Settings, NOT on the Report screen (which has PDF export only).

---

## Tasks

### Export Data Screen

- [ ] Create `ExportDataScreen` composable (accessible via Settings):
  - Title: "Export your data"
  - Subtitle: "Exports include all transactions across all years. These are for analysis only; there is no restore path."
  - Two buttons: **"Export as CSV"** and **"Export as JSON"**.
  - Below each button, show "Last exported: {timestamp}" (from DataStore, default "Never").
  - On tap, generate file, launch share sheet, update timestamp.

- [ ] Create `ExportDataViewModel` that exposes:
  - `lastCsvExportTime: Flow<Long?>` — from DataStore.
  - `lastJsonExportTime: Flow<Long?>` — from DataStore.
  - `onExportCsv()` — call `ExportTransactionsUseCase`, write CSV, share, update timestamp.
  - `onExportJson()` — call `ExportTransactionsUseCase`, write JSON, share, update timestamp.

- [ ] Wire to Settings navigation:
  - Add a menu item to the main Settings screen: "Export data" → navigates to `ExportDataScreen`.

---

### ExportTransactionsUseCase

This is the core of exports: unify real transactions + synthetic pay-event transactions into a single sorted list.

```kotlin
@Singleton
class ExportTransactionsUseCase @Inject constructor(
    private val transactionDao: TransactionDao,
    private val payEventDao: PayEventDao,
    private val categoryDao: CategoryDao
) {
    suspend operator fun invoke(): List<ExportTransaction> {
        // Fetch all non-deleted transactions
        val realTransactions = transactionDao.getAllActive().first()
        
        // Fetch all pay events
        val payEvents = payEventDao.getAllActive().first()
        
        // Fetch all categories for denormalization
        val categories = categoryDao.getAllActive().first()
        val catMap = categories.associateBy { it.id }
        
        // Create synthetic transactions from pay events
        val syntheticTransactions = payEvents.map { event ->
            ExportTransaction(
                type = TransactionType.INCOME,
                amountCents = 0,  // unknown amount
                categoryName = null,
                description = "Pay event",
                occurredAt = event.occurredAt,
                isPayEvent = true
            )
        }
        
        // Denormalize categories
        val denormReal = realTransactions.map { tx ->
            ExportTransaction(
                type = tx.type,
                amountCents = tx.amountCents,
                categoryName = tx.categoryId?.let { catMap[it]?.name },
                description = tx.description,
                occurredAt = tx.occurredAt,
                isPayEvent = false
            )
        }
        
        // Merge and sort by occurredAt ascending
        return (denormReal + syntheticTransactions)
            .sortedBy { it.occurredAt }
    }
}

data class ExportTransaction(
    val type: TransactionType,
    val amountCents: Long,
    val categoryName: String?,
    val description: String,
    val occurredAt: Long,
    val isPayEvent: Boolean
)
```

---

### CSV Export

**Format:** RFC 4180, UTF-8, single file, all years.

**Columns:**
```
date,year,type,amount_usd,category,description,is_pay_event
2025-01-15,2025,EXPENSE,12.34,Groceries,Weekly shopping,false
2025-01-20,2025,INCOME,0.00,null,Pay event,true
```

**Implementation:**

```kotlin
suspend fun exportTransactionsToCsv(
    context: Context,
    useCase: ExportTransactionsUseCase
): File {
    val transactions = useCase()
    
    val csv = StringBuilder()
    csv.append("date,year,type,amount_usd,category,description,is_pay_event\n")
    
    for (tx in transactions) {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }.format(Date(tx.occurredAt))
        
        val year = Calendar.getInstance().apply {
            timeInMillis = tx.occurredAt
        }.get(Calendar.YEAR)
        
        val amountUsd = tx.amountCents / 100.0
        
        // RFC 4180: quote fields containing comma, quote, or newline
        val category = escapeCsvField(tx.categoryName ?: "")
        val description = escapeCsvField(tx.description)
        
        csv.append(
            "$date,$year,${tx.type},$amountUsd,$category,$description,${tx.isPayEvent}\n"
        )
    }
    
    val file = File(context.cacheDir, "fintrack_export_${System.currentTimeMillis()}.csv")
    file.writeText(csv.toString(), Charsets.UTF_8)
    return file
}

private fun escapeCsvField(field: String): String {
    return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
        "\"${field.replace("\"", "\"\"")}\""
    } else {
        field
    }
}
```

---

### JSON Export

**Format:** Top-level array of one-key objects keyed by year. Each year contains totals and transaction array.

**Example:**
```json
[
  {
    "2025": {
      "transactionCount": 42,
      "incomeTotalCents": 500000,
      "expenseTotalCents": 350000,
      "transactions": [
        {
          "date": "2025-01-15",
          "type": "EXPENSE",
          "amount_cents": 1234,
          "category": "Groceries",
          "description": "Weekly shopping",
          "is_pay_event": false
        },
        ...
      ]
    }
  },
  {
    "2024": {
      "transactionCount": 128,
      "incomeTotalCents": 1200000,
      "expenseTotalCents": 950000,
      "transactions": [...]
    }
  }
]
```

**Implementation using kotlinx.serialization:**

```kotlin
suspend fun exportTransactionsToJson(
    context: Context,
    useCase: ExportTransactionsUseCase
): File {
    val transactions = useCase()
    
    // Group by year
    val byYear = mutableMapOf<Int, MutableList<ExportTransaction>>()
    for (tx in transactions) {
        val year = Calendar.getInstance().apply {
            timeInMillis = tx.occurredAt
            timeZone = TimeZone.getDefault()
        }.get(Calendar.YEAR)
        
        byYear.computeIfAbsent(year) { mutableListOf() }.add(tx)
    }
    
    // Build JSON structure
    val result = mutableListOf<JsonObject>()
    for ((year, txs) in byYear.toSortedMap(reverseOrder())) {
        val income = txs.filter { it.type == TransactionType.INCOME }.sumOf { it.amountCents }
        val expense = txs.filter { it.type == TransactionType.EXPENSE }.sumOf { it.amountCents }
        
        val txJson = txs.map { tx ->
            JsonObject(
                mapOf(
                    "date" to JsonPrimitive(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(tx.occurredAt))),
                    "type" to JsonPrimitive(tx.type.name),
                    "amount_cents" to JsonPrimitive(tx.amountCents),
                    "category" to JsonPrimitive(tx.categoryName),
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
    
    val json = Json.encodeToString(JsonArray(result))
    val file = File(context.cacheDir, "fintrack_export_${System.currentTimeMillis()}.json")
    file.writeText(json, Charsets.UTF_8)
    return file
}
```

---

### Sharing & DataStore Update

```kotlin
private fun shareFile(context: Context, file: File) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    context.startActivity(
        Intent(Intent.ACTION_SEND)
            .apply {
                type = "text/*"  // or "application/json" for JSON
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
    )
}

private suspend fun updateLastExportTime(
    dataStore: DataStore<Preferences>,
    type: String,  // "csv" or "json"
    time: Long
) {
    dataStore.edit { prefs ->
        prefs[PreferencesKeys.LAST_CSV_EXPORT_TIME] = time  // or JSON equiv
    }
}
```

---

### FileProvider Setup

Add to `AndroidManifest.xml`:

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false">
    <paths>
        <cache-path name="cache" path="." />
    </paths>
</provider>
```

---

### Testing

- [ ] Unit tests for `ExportTransactionsUseCase`: verify synthetic pay events are included, categories are denormalized, sorted by date.
- [ ] Unit tests for CSV builder: verify RFC 4180 quoting (comma, quote, newline in fields), UTF-8 encoding, year column correct, pay events marked.
- [ ] Unit tests for JSON builder: verify structure, year keys, transactionCount matches array length, totals correct.
- [ ] Instrumented tests: create ≥10 transactions across ≥2 calendar years, export CSV/JSON, read files, verify content.

---

## Verification Checklist

- [ ] Settings screen has "Export data" option; tapping navigates to `ExportDataScreen`.
- [ ] CSV export button generates a file with RFC 4180 quoting; special characters (comma, quote, newline) round-trip cleanly.
- [ ] CSV includes year column derived from transaction date in device timezone.
- [ ] CSV includes pay events as synthetic income rows with `is_pay_event=true`.
- [ ] CSV opens in Google Sheets or Excel; header row visible, all columns populated.
- [ ] JSON export generates valid JSON; can be parsed without errors.
- [ ] JSON is year-keyed; each year object has correct `transactionCount`, `incomeTotalCents`, `expenseTotalCents`, and `transactions` array.
- [ ] Pay events are included in JSON as synthetic income rows with `is_pay_event: true`.
- [ ] Both exports show "Last exported: {timestamp}" after successful export.
- [ ] Tapping "Export as CSV" or "Export as JSON" launches Android share sheet.
- [ ] Exported files are ≤5MB for 500+ transactions across multiple years.

---

*End of Sprint 6. Proceed to `SPRINT_7_POLISH.md` when verified.*
