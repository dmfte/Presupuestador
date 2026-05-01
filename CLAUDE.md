# CLAUDE.md — FinTrack (Personal Finance Tracker)

> **Purpose of this document:** This is the master spec for Claude Code to autonomously build the **FinTrack** Android app. Read this entire file before starting. Execute sprints in order. Do not skip the Setup sprint. After each sprint, run the verification checklist before moving on.

---

## 1. Project Overview

**App name (working):** FinTrack
**Package:** `com.tuapp.fintrack`
**Platform:** Android (min SDK 26 / Android 8.0, target SDK 34)
**Dev environment:** macOS, Android Studio, VS Code, Claude Code via Android Studio terminal (JetBrains plugin)
**Distribution:** Personal use, sideload via APK (no Play Store release planned).

### What it does

A privacy-first, offline-first personal finance tracker. The user logs income and expenses (in USD) with optional category and description, then sees a full end-of-month report with charts, trends, and suggestions. A home screen widget (two sizes) makes logging a transaction take less than five seconds. Budgets can be allocated per category and tracked against custom pay-period cycles.

### Non-goals

- No cloud sync, no accounts, no bank integration.
- No multi-currency (USD only for v1).
- No multi-user support.
- No Play Store distribution.

---

## 2. Core Requirements (locked decisions)

These are the answers from the spec interview. Treat them as binding.

| # | Decision | Implementation rule |
|---|----------|---------------------|
| 1 | **Language** | Use the device's default locale for all UI strings. Provide `strings.xml` (English) and `strings-es.xml` (Spanish) for v1. |
| 2 | **Currency** | USD only. Format with `NumberFormat.getCurrencyInstance(Locale.US)`. |
| 3 | **Categories at launch** | Empty list. User adds categories via a combo input (select existing OR type new and save). |
| 4 | **Category required?** | NOT required by default. A toggle in Settings (`require_category`) makes it mandatory. |
| 5 | **Income categorizable?** | Yes. Same category system, but each category has a `type` field: `EXPENSE`, `INCOME`, or `BOTH`. |
| 6 | **Edit/delete past transactions** | Yes, from the app (not the widget). Soft-delete with undo snackbar. |
| 7 | **Pay cycles** | Default: 15th and last day of month. User can add/remove cycles in Settings. Each cycle is defined by a rule (specific day-of-month, "last day of month", custom date, or weekly/biweekly anchor). |
| 8 | **"I've been paid today" button** | Prominent button on the Home screen that resets the current period immediately, regardless of the configured cycle. Logs an event so the next scheduled cycle still fires correctly. |
| 9 | **Widget sizes** | Two: **Small** = quick entry only. **Medium** = quick entry + current period summary. |
| 10 | **Monthly report** | Full: pie chart (spending by category), bar chart (daily spend), trend lines vs prior 3 months, top categories, budget vs actual, plain-language suggestions, export to CSV and PDF. |
| 11 | **Backup/export** | CSV and JSON export are for **data analysis only** (transactions only, no app config). Exports are NOT backups; there is no restore path. PDF export is per-month only. See §10. |
| 12 | **Recurring transactions** | NOT in v1. Defer to v2. |
| 13 | **Pay date roll-back** | Each cycle has two toggles, both default ON: `rollBackOnWeekend` (Sat/Sun → previous Friday) and `rollBackOnHoliday` (holiday → previous business day). The algorithm walks back as many days as needed (no max) until a non-weekend, non-holiday day is found. See §7.1. |
| 14 | **Effective vs nominal date** | Period boundaries always use the *effective* date (after roll-back). A nominal payday on Sat May 31 with weekend roll-back active means the new period starts Fri May 30. |
| 15 | **Holidays** | Stored in Room. Pre-seeded from `assets/holidays_sv_default.json` (14 entries — see §4). User can edit, disable, delete, or add. App updates refresh movable dates without overwriting user changes. |
| 16 | **Period freshness** | Periods are NEVER frozen. Always computed from current cycle + holiday state. Editing a cycle or holiday retroactively re-buckets all past transactions. |
| 17 | **Period auto-advance** | A daily WorkManager job at 00:05 local time writes a `PayEvent` for any cycle whose effective date is today. Idempotent (dedupes against existing events for the same date). |
| 18 | **CSV export shape** | Single file, all years, with a `year` column derived from `occurred_at` in device timezone. Pay events included as synthetic income rows with `is_pay_event=true`. See §10.1. |
| 19 | **JSON export shape** | Top-level array of one-key objects keyed by year (`[{"2025": {...}}, {"2026": {...}}]`). Each year contains transaction count, income/expense totals, and a transaction array. Categories denormalized to names per-transaction (no separate category list). Pay events included as synthetic income rows. See §10.2. |
| 20 | **Export location** | CSV and JSON live on a Settings → "Export data" screen, NOT on the report screen. The report screen only exports a per-month PDF. |

---

## 3. Tech Stack

- **Language:** Kotlin 100%.
- **UI (app):** Jetpack Compose with Material 3.
- **UI (widget):** Jetpack Glance (Compose for widgets).
- **DB:** Room (SQLite). Single source of truth.
- **Async:** Kotlin Coroutines + Flow. Repository exposes `Flow<T>`.
- **DI:** Hilt.
- **Charts:** Vico (`com.patrykandpatrick.vico:compose-m3`). Lightweight, Compose-native.
- **PDF export:** Android's built-in `PdfDocument` API. No third-party PDF library.
- **CSV export:** Manual `StringBuilder` + `MediaStore` for sharing.
- **Settings:** DataStore (Preferences).
- **Testing:** JUnit + Truth for unit tests; Compose UI tests for screens.
- **Build:** Gradle Kotlin DSL, version catalog (`libs.versions.toml`).

### Why these choices

- **Glance** is the only Google-supported way to write widgets in Compose. The Glance API is constrained (no arbitrary Composables) — design widgets within those limits from day one.
- **Vico** renders cleanly in Compose without bringing in a WebView (unlike MPAndroidChart wrappers). Smaller APK.
- **Built-in PdfDocument** avoids licensing headaches and keeps the APK small. Sufficient for monthly report layouts.

---

## 4. Data Model

All amounts stored as **`Long` cents** (e.g., $12.34 = `1234L`). Never use `Float`/`Double` for money. Convert at the UI boundary only.

### Entities

```kotlin
@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,          // INCOME or EXPENSE
    val amountCents: Long,              // always positive; sign comes from type
    val categoryId: Long?,              // nullable; null = uncategorized
    val description: String,            // may be empty
    val occurredAt: Long,               // epoch millis (the date the user assigns)
    val createdAt: Long,                // epoch millis (when the row was written)
    val deletedAt: Long? = null         // soft delete
)

enum class TransactionType { INCOME, EXPENSE }

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,                   // unique, case-insensitive
    val applicability: CategoryApplicability,  // EXPENSE, INCOME, BOTH
    val colorHex: String,               // e.g., "#FF5722" — assigned on create
    val createdAt: Long,
    val archivedAt: Long? = null
)

enum class CategoryApplicability { EXPENSE, INCOME, BOTH }

@Entity(tableName = "budgets")
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,               // FK to categories
    val amountCents: Long,              // budgeted amount per cycle
    val cycleId: Long,                  // which pay cycle this budget is bound to
    val active: Boolean = true,
    val createdAt: Long
)

@Entity(tableName = "pay_cycles")
data class PayCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,                  // e.g., "Mid-month", "End-of-month"
    val rule: PayCycleRule,             // serialized as String (kotlinx.serialization)
    val rollBackOnWeekend: Boolean = true,   // Sat/Sun rolls back to previous Friday
    val rollBackOnHoliday: Boolean = true,   // holiday rolls back; no-op if holiday list empty
    val createdAt: Long,
    val archivedAt: Long? = null
)

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long,                     // epochDay (java.time LocalDate.toEpochDay())
    val label: String,                  // e.g., "Independence Day"
    val recurringYearly: Boolean,       // true = fixed-date (Jan 1, May 1); false = movable (Holy Week)
    val enabled: Boolean = true,        // user can disable without deleting
    val createdAt: Long
)

// Sealed class serialized to a single String column via TypeConverter
sealed class PayCycleRule {
    data class DayOfMonth(val day: Int) : PayCycleRule()      // 1..31
    object LastDayOfMonth : PayCycleRule()
    data class SpecificDate(val epochDay: Long) : PayCycleRule()  // one-off
    data class Weekly(val dayOfWeek: Int) : PayCycleRule()    // 1=Mon..7=Sun
    data class Biweekly(val anchorEpochDay: Long) : PayCycleRule()
}

@Entity(tableName = "pay_events")
data class PayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val cycleId: Long?,                 // null if triggered by "I've been paid today"
    val occurredAt: Long,               // when the period started
    val source: PayEventSource          // SCHEDULED or MANUAL
)

enum class PayEventSource { SCHEDULED, MANUAL }
```

### Default seed data

On first launch:

- **No categories.** User adds them as they go.
- **Two pay cycles:** `("Mid-month", DayOfMonth(15), rollBackOnWeekend=true, rollBackOnHoliday=true)` and `("End-of-month", LastDayOfMonth, rollBackOnWeekend=true, rollBackOnHoliday=true)`.
- **No budgets.**
- **Holidays (SV default seed):** loaded from a bundled `holidays_sv_default.json` asset. Each entry has a fixed nominal date and a `recurringYearly` flag. User can edit, disable, or delete any of them post-install.

```json
{
  "version": 1,
  "locale": "sv",
  "holidays": [
    { "month": 1,  "day": 1,  "label": "Año Nuevo",                    "recurringYearly": true  },
    { "month": 4,  "day": 2,  "label": "Jueves Santo (2026)",          "recurringYearly": false },
    { "month": 4,  "day": 3,  "label": "Viernes Santo (2026)",         "recurringYearly": false },
    { "month": 4,  "day": 4,  "label": "Sábado Santo (2026)",          "recurringYearly": false },
    { "month": 5,  "day": 1,  "label": "Día del Trabajo",              "recurringYearly": true  },
    { "month": 5,  "day": 10, "label": "Día de la Madre",              "recurringYearly": true  },
    { "month": 6,  "day": 17, "label": "Día del Padre",                "recurringYearly": true  },
    { "month": 8,  "day": 3,  "label": "Fiestas Agostinas",            "recurringYearly": true  },
    { "month": 8,  "day": 4,  "label": "Fiestas Agostinas",            "recurringYearly": true  },
    { "month": 8,  "day": 5,  "label": "Fiestas Agostinas",            "recurringYearly": true  },
    { "month": 8,  "day": 6,  "label": "Fiestas Agostinas",            "recurringYearly": true  },
    { "month": 9,  "day": 15, "label": "Día de la Independencia",      "recurringYearly": true  },
    { "month": 11, "day": 2,  "label": "Día de los Difuntos",          "recurringYearly": true  },
    { "month": 12, "day": 25, "label": "Navidad",                      "recurringYearly": true  }
  ]
}
```

**Movable-date strategy:** Holy Week dates are seeded for the current year only with `recurringYearly: false`. Each app update bundles a refreshed JSON with the next year's movable dates. On install/update, the seeder inserts only entries whose `(year, month, day)` doesn't already exist in the DB — never overwrites user edits.

### Derived concepts (computed, not stored)

- **Current period:** From the most recent `PayEvent` (or app-install date if none) up to *now*. The "period end" is the next scheduled effective pay date computed from active cycles + roll-back rules + holiday list.
- **Period progress:** Sum of expense transactions in current period grouped by category.
- **Budget remaining:** `budget.amountCents - sum(expenses in current period for budget.categoryId)`.
- **Periods are always computed fresh** from current cycle + holiday state. They are never frozen. Changing a cycle rule or toggling a holiday retroactively re-buckets transactions in past periods. (See §7.4.)

---

## 5. Architecture

Single-module app. Standard MVVM + repository.

```
com.tuapp.fintrack/
├── data/
│   ├── db/                 # Room DB, DAOs, TypeConverters
│   ├── repository/         # TransactionRepository, CategoryRepository, BudgetRepository, PayCycleRepository
│   └── prefs/              # DataStore wrappers (Settings)
├── domain/
│   ├── model/              # Domain models (separate from entities if needed; v1 can reuse entities)
│   ├── usecase/            # GetCurrentPeriodUseCase, ComputeBudgetProgressUseCase, GenerateMonthlyReportUseCase, etc.
│   └── period/             # PayCycle math (next occurrence, period bounds)
├── ui/
│   ├── home/               # Home screen (recent tx, quick actions, "I've been paid")
│   ├── entry/              # Add/edit transaction screen
│   ├── transactions/       # Full list + filters + edit/delete
│   ├── categories/         # Manage categories
│   ├── budgets/            # Manage budgets, see progress
│   ├── report/             # Monthly report (charts, suggestions, export)
│   ├── settings/           # Settings, pay cycles, import/export, locale
│   └── theme/              # Material 3 theme, typography, colors
├── widget/
│   ├── small/              # Small widget (Glance)
│   ├── medium/             # Medium widget (Glance)
│   └── actions/            # Glance ActionCallback impls
├── export/
│   ├── csv/                # CSV builder
│   ├── pdf/                # PDF report builder
│   └── json/               # JSON export (analysis only)
└── di/                     # Hilt modules
```

### Threading

- All DB access via `suspend` + `Flow`.
- Widget callbacks run on `WorkManager` if work > 50ms; otherwise inline.
- PDF/CSV generation always on `Dispatchers.IO`, never on the main thread.

---

## 6. Key UX flows

### 6.1 First launch

1. Splash → Home screen, empty state ("No transactions yet — tap + to add one or use the widget").
2. A one-time onboarding card explains: pay cycles default to 15th & last; tap "Customize" to change.
3. Home shows the period summary skeleton.

### 6.2 Adding a transaction (in-app)

1. FAB → Entry screen.
2. Fields, in this order:
   - **Type toggle:** Expense / Income (default: Expense).
   - **Amount:** numeric keypad, formatted as USD.
   - **Category:** combo box. Tap to dropdown. Type to filter or to add new ("Add 'Groceries'" appears when no exact match).
   - **Description:** single-line text.
   - **Date:** defaults to today. Tap to change.
3. Save → snackbar "Saved" with Undo.

### 6.3 Adding a transaction (widget — small)

- 4 controls fit: Type toggle (E/I), Amount field, Category dropdown, Save button.
- On Save, show a brief checkmark state for 1.5s then reset.

### 6.4 Adding a transaction (widget — medium)

- Same as small, plus a top strip: `"Period: $X spent / $Y budget"` and `"Days left: N"`.

### 6.5 "I've been paid today"

- Big secondary button on Home.
- Tapping it: confirm dialog → on confirm, write a `PayEvent(cycleId=null, source=MANUAL, occurredAt=now)`.
- The current period resets immediately. Scheduled cycles still fire as configured (we do NOT reschedule them based on manual events). The auto-advance worker dedupes if a scheduled effective payday lands on the same calendar day. See §7.5–§7.6.

### 6.6 Monthly report

- Accessed from Home → "View this month's report" or from any prior month via a month picker.
- Sections, in order:
  1. **Headline:** Net (income − expenses) for the month.
  2. **Pie chart:** spending by category (top 6 + "Other").
  3. **Bar chart:** daily spending across the month.
  4. **Trend:** line chart of monthly net for last 4 months (this month + prior 3).
  5. **Top burner:** "Your biggest expense category was X at $Y (Z% of income)."
  6. **Budget vs actual:** table per active budget.
  7. **Suggestions:** 1–3 plain-language tips generated from rules (see §8).
  8. **Export PDF button** (this month only). CSV/JSON full-history exports live in Settings → Export data.

---

## 7. Pay cycle math

This is the trickiest piece. Get it right early. Calendar arithmetic is easy; **period semantics** are where bugs hide. Read §7.4 carefully.

### 7.1 Two-stage date resolution

Every pay date is computed in two stages:

1. **Nominal date** — what the cycle rule says (e.g., "the 15th of June 2026" = `2026-06-15`).
2. **Effective date** — the nominal date adjusted backward by roll-back rules. This is the date the period boundary uses.

```kotlin
fun resolveEffectiveDate(
    nominal: LocalDate,
    cycle: PayCycle,
    holidays: Set<LocalDate>   // pre-expanded (see §7.3)
): LocalDate {
    var d = nominal
    while (true) {
        val isWeekend = cycle.rollBackOnWeekend &&
            (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY)
        val isHoliday = cycle.rollBackOnHoliday && d in holidays
        if (!isWeekend && !isHoliday) return d
        d = d.minusDays(1)
        // No max iterations cap — per spec, walk back as far as needed.
        // In practice this terminates within ~10 days even in pathological cases.
    }
}
```

**Termination is guaranteed** because no calendar can have more than ~6 consecutive weekend+holiday days in real-world holiday lists. If it ever loops > 14 iterations, log a warning (likely indicates a malformed holiday seed).

### 7.2 Nominal date generators (per rule)

- **`DayOfMonth(d)`:** `LocalDate.of(year, month, min(d, YearMonth.of(year, month).lengthOfMonth()))`. So `DayOfMonth(31)` in February returns Feb 28/29.
- **`LastDayOfMonth`:** `YearMonth.of(year, month).atEndOfMonth()`.
- **`SpecificDate(epochDay)`:** `LocalDate.ofEpochDay(epochDay)`. One-shot; doesn't recur.
- **`Weekly(dow)`:** next occurrence of `dow` strictly after `from`.
- **`Biweekly(anchorEpochDay)`:** anchor + `ceil((from - anchor).days / 14) * 14` days. If `from == anchor`, return anchor + 14.

Each rule exposes `nextNominal(after: LocalDate): LocalDate?` (null only for `SpecificDate` once it's passed).

### 7.3 Holiday set expansion

Holidays are stored with a fixed `epochDay` and a `recurringYearly` flag. To get the effective holiday set for a given year:

1. Take all enabled holidays.
2. For each: if `recurringYearly`, expand to that year (same month/day). If not recurring, include only if its stored year matches.
3. Return as `Set<LocalDate>` keyed by `LocalDate`.

Cache the expanded set per year in memory (it's small — ~15 entries — and is read on every period query). Invalidate when any holiday row changes.

```kotlin
class HolidayResolver(private val dao: HolidayDao) {
    private val cache = mutableMapOf<Int, Set<LocalDate>>()

    suspend fun forYear(year: Int): Set<LocalDate> = cache.getOrPut(year) {
        dao.allEnabled().mapNotNull { h ->
            val stored = LocalDate.ofEpochDay(h.date)
            when {
                h.recurringYearly -> runCatching { stored.withYear(year) }.getOrNull()
                stored.year == year -> stored
                else -> null
            }
        }.toSet()
    }

    fun invalidate() = cache.clear()
}
```

`withYear()` can throw for Feb 29 in a non-leap year — that's why the `runCatching`. If a user marks Feb 29 as recurring, it simply doesn't appear in non-leap years (acceptable).

### 7.4 Period semantics (LOCKED DECISIONS)

These are the policy choices that prevent silent bugs. Do not deviate.

**Boundary convention:** `[start, end)` half-open. A transaction whose `occurredAt` equals the period's `start` belongs to the new period. A transaction equal to `end` belongs to the *next* period.

**Boundary basis:** Effective dates, not nominal. The period boundary is the effective payday — i.e., the day money actually arrives.

**Period freshness:** Periods are computed fresh every time. Never cached, never frozen. If the user changes a cycle rule or toggles a holiday, all past periods retroactively re-bucket. This is the explicit spec decision and the report screen must reflect this on every open.

**Multiple cycles:** "Next effective payday" is `min()` across all active cycles' effective dates computed from "today." If two cycles fall on the same effective date, treat as one boundary (one `PayEvent` written by the auto-advance worker, not two).

**Manual `PayEvent` interaction:** A manual `PayEvent` (from "I've been paid today") sets the current period start. The next scheduled effective payday is still computed from cycle rules — manual events do not reschedule cycles. Edge case: if a scheduled effective payday falls *before* the next time the auto-advance worker runs, but after a manual `PayEvent` that was logged for the same calendar day, the auto-advance worker dedupes by checking `pay_events` for any row with `occurredAt` on that date; if one exists, skip writing a new one.

**Timezone:** All period math uses the device's current default timezone (`ZoneId.systemDefault()`). Transactions store `occurredAt` as epoch millis but are converted via `Instant.ofEpochMilli(...).atZone(ZoneId.systemDefault()).toLocalDate()` before any period comparison. **Never compare epoch millis directly to period bounds** — always go through `LocalDate`. If the user travels and changes timezone, periods recompute on next query (acceptable; a transaction logged at 11pm in San Salvador and viewed from Tokyo may shift one day, which is the correct behavior).

**Period start when no `PayEvent` exists:** App-install date (stored on first launch in DataStore as `install_date_epoch_day`).

### 7.5 Auto-advance worker

A `PeriodicWorkRequest` (WorkManager) runs once daily at 00:05 local time:

1. Compute "today" as `LocalDate.now(ZoneId.systemDefault())`.
2. For each active cycle, compute the effective date for the cycle's most recent past nominal date.
3. If any effective date == today AND no `PayEvent` exists with `occurredAt` on today, write a new `PayEvent(cycleId=cycle.id, source=SCHEDULED, occurredAt=startOfTodayMillis)`.
4. Trigger widget updates.

This is the "trust the prediction, auto-advance at midnight" behavior from the spec. The worker is idempotent — running it twice on the same day produces no duplicate events.

**Why a daily worker instead of computing on-the-fly?** Two reasons: (1) the widget needs `PayEvent` rows to display "Days left" without doing complex math at render time, and (2) the report screen's "period N of M" labels need stable identifiers.

### 7.6 Manual "I've been paid today"

Writes `PayEvent(cycleId=null, source=MANUAL, occurredAt=now)`. Confirmation dialog before writing. Triggers widget update. Does NOT touch cycles. The auto-advance worker's dedup check (§7.5 step 3) ensures the next scheduled payday on the same calendar day won't double-fire.

### 7.7 Required tests

Sprint 1's test suite must cover, at minimum:

1. `LastDayOfMonth` for Feb 2024 (leap, returns 29) and Feb 2026 (non-leap, returns 28).
2. `DayOfMonth(31)` in April returns April 30.
3. `Biweekly(anchor=2026-01-01)` from `2026-01-01` returns `2026-01-15`; from `2026-01-15` returns `2026-01-29`.
4. Roll-back: nominal Sat → previous Friday with weekend rule on, no holidays.
5. Roll-back: nominal Sun, previous Friday is also a holiday → previous Thursday.
6. Roll-back chain: nominal Mon (holiday), Sun (weekend), Sat (weekend), Fri (holiday), Thu (clear) → returns Thu.
7. Period boundary equality: transaction at `occurredAt == periodStartMillis` belongs to the new period; at `periodEndMillis` belongs to next.
8. Two cycles with same effective date produce one `PayEvent`, not two.
9. Holiday Feb 29 marked `recurringYearly=true` does not appear in 2027.
10. Changing a cycle rule causes a previously-period-2 transaction to re-bucket into period-3 on next query.

---

## 8. Suggestion engine (rule-based, no ML)

Generated each time the report is viewed. Rules:

| Trigger | Suggestion |
|---|---|
| Top category > 40% of income | "Your spending on {category} ({pct}% of income) is unusually high. Consider setting a budget." |
| Any budget exceeded by > 20% | "You went over your {category} budget by {amount}. Adjust the budget or rein in spending next period." |
| Daily spend variance > 2× average on any day | "There was a spending spike on {date} ({amount}). Worth reviewing those transactions." |
| Net negative for 2+ consecutive months | "You've spent more than you earned for {N} months in a row." |
| > 30% of expense transactions are uncategorized | "Categorizing more transactions will give you better reports. Consider enabling 'Require category' in Settings." |

Cap output at 3 suggestions per report, ordered by severity.

---

## 9. Settings (DataStore)

| Key | Type | Default |
|---|---|---|
| `require_category` | Boolean | `false` |
| `theme_mode` | String (`SYSTEM` / `LIGHT` / `DARK`) | `SYSTEM` |
| `first_launch_completed` | Boolean | `false` |
| `last_export_at` | Long? | null |
| `default_transaction_type` | String (`INCOME` / `EXPENSE`) | `EXPENSE` |

Pay cycles live in Room (not DataStore) because they're a list with relations.

---

## 10. Export formats

**Purpose:** Exports are for **external data analysis only** — spreadsheets, taxes, personal review. They are NOT backups. There is no import/restore path. The user accepts that uninstalling the app discards budgets, pay cycles, holiday config, and settings; only transactions are recoverable, and only as analytical records.

**Scope of all exports:** every transaction in the database (excluding soft-deleted rows). No category list, no budgets, no pay cycles, no holidays, no settings.

**Pay events as transactions:** Each `PayEvent` row is included in the export as a synthetic income transaction:
- `type = INCOME`
- `amountCents = 0` (the actual amount lives on whatever real income transaction the user logged that day; pay events only mark the period boundary)
- `category = "Pay event"`
- `description = "Scheduled pay event"` (or `"Manual pay event"` for `source=MANUAL`)
- `occurredAt` = the pay event's `occurredAt`
- An additional column/field `is_pay_event = true` distinguishes them from real income transactions

This lets the user filter pay events out in their spreadsheet, or use them to delineate periods.

### 10.1 CSV — single file, year column

UTF-8 encoded, comma-separated, RFC 4180 quoting (double quotes around fields containing commas, quotes, or newlines; quotes within fields doubled). One header row. Sorted by `occurred_at` ascending.

```csv
id,year,type,amount_usd,category,description,occurred_at_iso,created_at_iso,is_pay_event
1,2026,EXPENSE,12.34,Groceries,"Milk, bread",2026-04-30T14:22:01-06:00,2026-04-30T14:22:05-06:00,false
2,2026,INCOME,1500.00,Salary,Mid-month paycheck,2026-04-30T08:00:00-06:00,2026-04-30T08:00:12-06:00,false
3,2026,INCOME,0.00,Pay event,Scheduled pay event,2026-04-30T00:00:00-06:00,2026-04-30T00:05:00-06:00,true
4,2026,EXPENSE,8.50,,Coffee,2026-05-01T07:15:22-06:00,2026-05-01T07:15:30-06:00,false
```

**Column rules:**
- `year`: derived from `occurred_at` in the device's current timezone (`Instant.atZone(ZoneId.systemDefault()).year`). 4-digit integer.
- `amount_usd`: dollars with 2 decimals, no currency symbol, no thousands separator. Always positive.
- `category`: the category name at export time (live lookup, not denormalized at transaction-create time). Empty string if `categoryId` is null OR the category was archived/deleted.
- `description`: empty string if blank.
- `occurred_at_iso` / `created_at_iso`: ISO 8601 with timezone offset (e.g., `-06:00`). Use `OffsetDateTime` formatter.
- `is_pay_event`: literal `true` or `false`.

**Filename:** `fintrack_export_YYYY-MM-DD.csv` (today's date in device locale).

### 10.2 JSON — year-keyed array of objects

Top-level structure is an **array** of one-key objects, one per year that has at least one transaction. Years sorted ascending. Within each year, transactions sorted by `occurredAt` ascending.

```json
[
  {
    "2025": {
      "transactionCount": 142,
      "totalIncomeUsd": 18000.00,
      "totalExpenseUsd": 16234.50,
      "transactions": [
        {
          "id": 1,
          "type": "EXPENSE",
          "amountUsd": 12.34,
          "category": "Groceries",
          "description": "Milk, bread",
          "occurredAt": "2025-12-30T14:22:01-06:00",
          "createdAt": "2025-12-30T14:22:05-06:00",
          "isPayEvent": false
        }
      ]
    }
  },
  {
    "2026": {
      "transactionCount": 87,
      "totalIncomeUsd": 9000.00,
      "totalExpenseUsd": 7421.18,
      "transactions": [
        {
          "id": 143,
          "type": "INCOME",
          "amountUsd": 0.00,
          "category": "Pay event",
          "description": "Scheduled pay event",
          "occurredAt": "2026-01-15T00:00:00-06:00",
          "createdAt": "2026-01-15T00:05:00-06:00",
          "isPayEvent": true
        }
      ]
    }
  }
]
```

**Field rules:**
- `transactionCount`: count of `transactions` array length (real + pay events). Convenience field for the analyst.
- `totalIncomeUsd` / `totalExpenseUsd`: pre-computed sums for the year, excluding pay-event rows (since their amount is 0 anyway, but be explicit). 2-decimal numbers.
- `category`: same live-lookup rules as CSV. Use `null` (not empty string) when absent.
- All timestamps as ISO 8601 with offset.
- Numbers as JSON numbers, not strings.

**Filename:** `fintrack_export_YYYY-MM-DD.json`.

### 10.3 Where exports live in the UI

- **Settings → "Export data"** screen. Two buttons: "Export as CSV" and "Export as JSON."
- Each button generates the file in the app's cache dir, then launches Android's share sheet (`Intent.ACTION_SEND` with the file URI via `FileProvider`).
- **No** export buttons on the monthly report screen. The report screen has only the PDF export (see §10.4).
- Show last-export timestamp under each button: "Last exported: Apr 30, 2026 2:14 PM" (read from DataStore key `last_export_at`).

### 10.4 PDF — monthly report only

Unchanged from before. PDF export lives on the **report screen** (not Settings) and exports only the currently-viewed month as a formatted report. Single document, portrait, 8.5" × 11". Uses `PdfDocument`:

- Page 1: Headline + pie chart (rendered to bitmap from Vico) + top categories table.
- Page 2: Daily bar chart + trend chart.
- Page 3: Full transaction list for the period.

The PDF is the *only* per-month export. CSV and JSON are always full-history.

### 10.5 No restore

There is no import path. Removed entirely from scope. If the user uninstalls and reinstalls, they start fresh. This is a deliberate decision — exports are analysis artifacts, not backups.

---

## 11. Sprint plan

Each sprint ends with a verification block. Do not start the next sprint until verification passes.

### Sprint 0 — Setup (REQUIRED FIRST)

- [ ] Create Android Studio project with package `com.tuapp.fintrack`, min SDK 26, target SDK 34.
- [ ] Set up version catalog (`gradle/libs.versions.toml`) with: Compose BOM, Material 3, Hilt, Room, Glance, Vico, DataStore, Coroutines, Navigation Compose.
- [ ] Configure Hilt (`@HiltAndroidApp` Application class, manifest entry).
- [ ] Set up base theme (Material 3, dynamic color on Android 12+).
- [ ] Wire up Navigation Compose with placeholder screens for: Home, Entry, Transactions, Categories, Budgets, Report, Settings.
- [ ] Add an English `strings.xml` and Spanish `values-es/strings.xml`.

**Verify:** App builds and launches to a Home placeholder. Navigation between placeholders works. No runtime crashes.

### Sprint 1 — Data layer

- [ ] Implement all Room entities from §4 (`Transaction`, `Category`, `Budget`, `PayCycle`, `PayEvent`, `Holiday`).
- [ ] Implement DAOs: `TransactionDao`, `CategoryDao`, `BudgetDao`, `PayCycleDao`, `PayEventDao`, `HolidayDao`. Each exposes `Flow<List<T>>` for queries.
- [ ] Implement `TypeConverter` for `PayCycleRule` (use `kotlinx.serialization`).
- [ ] Implement repositories that wrap DAOs.
- [ ] Implement `Migration1to2` placeholder pattern (so the team is ready for schema bumps).
- [ ] Bundle `assets/holidays_sv_default.json` (see §4 seed data).
- [ ] Write `DatabaseSeeder` that on first launch (gated by `first_launch_completed`): inserts the two default pay cycles, parses the holiday JSON asset, and inserts each holiday with `enabled=true`. On subsequent app updates, re-read the JSON and insert only `(year, month, day)` triples not already present (never overwrite user edits).
- [ ] Implement `HolidayResolver` (see §7.3) with per-year cache + invalidation hook.
- [ ] Implement `resolveEffectiveDate()` (see §7.1) and `nextNominal()` per rule (§7.2).
- [ ] Implement `GetCurrentPeriodUseCase` (computes fresh on every call per §7.4).
- [ ] Unit-test all 10 cases listed in §7.7.

**Verify:** All §7.7 tests pass. App on first launch creates 2 pay cycles + 14 holidays (verified via DB inspector). Inserting a transaction via a debug button persists across app restart.

### Sprint 2 — Transaction entry & list

- [ ] Build Entry screen (Compose) with all fields from §6.2.
- [ ] Build category combo box: dropdown of existing + "Add new" inline action.
- [ ] Validate amount > 0 and (if `require_category` is on) category is set.
- [ ] Build Transactions list screen with: filter by type, filter by category, date range filter, search by description.
- [ ] Implement edit (open Entry pre-filled) and soft-delete (with Undo snackbar).
- [ ] Wire FAB on Home → Entry.

**Verify:** Can create, view, edit, and delete a transaction. Undo restores it. The `require_category` toggle in Settings actually enforces. List updates reactively when a transaction is added.

### Sprint 3 — Categories & budgets

- [ ] Categories management screen: list, add, edit name/applicability/color, archive.
- [ ] Auto-assign a color from a 12-color palette on category create (cycle through).
- [ ] Budgets management screen: list, add (pick category + amount + cycle), edit, delete.
- [ ] Show per-budget progress bar in the list (computed live from current-period spending).
- [ ] Block setting a budget on an archived category.

**Verify:** Adding a budget and then logging a matching expense updates the progress bar within 1s.

### Sprint 4 — Pay cycles, holidays & "I've been paid"

- [ ] Pay cycle settings screen: list, add (with rule picker UI), edit, delete. Each cycle exposes two toggles: "Roll back if weekend" and "Roll back if holiday."
- [ ] Rule picker UI supports: specific day-of-month (1–31, with "31 = last day of month" hint), "Last day of month", specific date (calendar picker), weekly (day picker), biweekly (anchor date picker).
- [ ] Holidays settings screen: list seeded holidays, toggle enabled/disabled, edit label/date, delete, add custom. Show `recurringYearly` as a switch on the edit form.
- [ ] Implement "I've been paid today" button on Home with confirmation dialog. Per §7.6, writes a manual `PayEvent`.
- [ ] Wire `GetCurrentPeriodUseCase` (built in Sprint 1) into Home: show period bounds, days remaining, and the *effective* next payday (not nominal — show both if they differ, e.g. "Next paycheck: Fri Apr 30 (nominally May 1)").
- [ ] Implement the daily auto-advance `PeriodicWorkRequest` per §7.5. Schedule from `Application.onCreate`. Idempotent dedup on `pay_events.occurredAt` for the day.
- [ ] When holidays or cycles change, call `holidayResolver.invalidate()` and trigger widget update.

**Verify:**
- Adding a "Last day of month" cycle with both roll-back flags on, with Sat May 31 2025 in the calendar, shows next effective payday as Fri May 30 2025.
- Adding a holiday on that Friday shifts the displayed effective payday to Thu May 29 2025.
- Tapping "I've been paid today" resets the period immediately.
- The auto-advance worker (forced via `WorkManager.testDriver` in an instrumented test) writes exactly one `PayEvent` per effective payday and skips if a manual event already exists for that date.

### Sprint 5 — Widgets (Glance)

- [ ] Implement `SmallEntryWidget` (Glance): type toggle, amount field, category dropdown, save button.
- [ ] Implement `MediumEntryWidget` (Glance): same controls + period summary strip on top.
- [ ] Implement Glance `ActionCallback` for save → writes Transaction to DB → triggers widget update.
- [ ] Handle the Glance text input limitation: amount field opens an Activity with a focused input, returns to widget on save (Glance doesn't support inline keyboard input on widgets).
- [ ] Update widget on transaction changes via `GlanceAppWidget.update()`.

**Verify:** Both widget sizes can be added to the home screen. Saving from each widget creates a transaction visible in the app. Medium widget's period summary updates after adding a transaction.

### Sprint 6 — Monthly report & exports

**Report screen:**
- [ ] Build Report screen with month picker (default: current month).
- [ ] Implement Vico pie chart (spending by category).
- [ ] Implement Vico bar chart (daily spending).
- [ ] Implement Vico line chart (4-month trend).
- [ ] Implement budget-vs-actual table.
- [ ] Implement suggestion engine per §8.
- [ ] Implement PDF export (multi-page, see §10.4). PDF button lives ON the report screen, exports the currently-viewed month only.

**Settings → Export data screen:**
- [ ] Build new Settings → Export data screen with two buttons: "Export as CSV" and "Export as JSON."
- [ ] Implement `ExportTransactionsUseCase` that returns a unified list of `(real transactions ∪ synthetic pay-event transactions)`, sorted by `occurredAt` ascending. Pay-event synthetics built per §10 ("Pay events as transactions").
- [ ] Implement CSV builder (§10.1): RFC 4180 quoting, UTF-8, single file, year column derived in device timezone.
- [ ] Implement JSON builder (§10.2): year-keyed array of one-key objects with totals and transaction list. Use `kotlinx.serialization` with explicit `JsonObject` construction since the year keys are dynamic.
- [ ] On export: write file to app cache dir, launch Android share sheet via `FileProvider`. Update `last_export_at` in DataStore.
- [ ] Show "Last exported: {timestamp}" under each export button (read from DataStore).

**Explicitly out of scope:**
- ❌ No JSON restore / import path. Per §10.5, exports are analysis-only.
- ❌ No CSV/JSON export buttons on the report screen.
- ❌ No category/budget/cycle/holiday data in exports.

**Verify:**
- A month with ≥10 mixed transactions produces a sensible report; PDF export opens in a default PDF viewer.
- CSV export across ≥2 calendar years opens in Sheets/Excel: header row correct, year column populated, special characters in descriptions (commas, quotes, newlines) round-trip cleanly, pay events present with `is_pay_event=true`.
- JSON export validates as well-formed JSON; top-level is an array; each year object contains correct `transactionCount` matching its `transactions` array length; income/expense totals match a manual sum.
- `last_export_at` timestamp updates after each successful export.

### Sprint 7 — Polish & hardening

- [ ] Error states on all screens.
- [ ] Loading skeletons for charts.
- [ ] Empty states with helpful CTAs.
- [ ] Dark theme verified across all screens and both widgets.
- [ ] Accessibility: TalkBack labels on all interactive elements; minimum 48dp touch targets.
- [ ] App icon and adaptive icon.
- [ ] Crash reporting hook (no-op in v1, just a logging interceptor).
- [ ] Generate signed APK for sideload.

**Verify:** App passes a manual smoke test: install fresh, add 20 mixed transactions across two periods, set 2 budgets, generate a report, export PDF for the current month, export full CSV and JSON from Settings. All three files open correctly in their default apps. CSV opens in Sheets with the year column populated. JSON validates and is year-keyed. PDF renders all 3 pages.

---

## 12. Working agreement for Claude Code

- **Run autonomously.** Do not ask for permission between sub-tasks within a sprint. Ask only when blocked by a genuinely ambiguous decision not covered in this document.
- **Commit per sprint.** One commit at sprint completion with a message like `feat(sprint-3): categories and budgets`.
- **Tests are part of "done."** A sprint is not complete without its verification block passing.
- **No silent dependency upgrades.** If a library version needs bumping mid-sprint, log it in the commit message.
- **When in doubt, prefer simple over clever.** This is a personal-use app — readability wins over micro-optimization.
- **Use the latest stable versions** in the version catalog at the time of Sprint 0. Do not pin to outdated versions.

---

## 13. Open items for v2 (do not implement now)

- Recurring transactions (auto-log monthly rent etc.).
- Multi-currency.
- Cloud sync (e.g., user-controlled WebDAV target).
- Receipt photo attachment.
- Budget rollovers (carry unused budget to next period).
- iOS port.

---

*End of spec. Begin with Sprint 0.*
