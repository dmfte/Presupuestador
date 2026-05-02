# CLAUDE.md — Presupuestador (Personal Finance Tracker)

> **Purpose of this document:** This is the master spec for Claude Code to autonomously build the **Presupuestador** Android app. Read this entire file before starting. Execute sprints in order. Do not skip the Setup sprint. After each sprint, run the verification checklist before moving on.
>
> **Sprint-specific instructions are in separate files.** When starting a sprint, read the corresponding file in the `/SPRINTS/` directory.

---

## 1. Project Overview

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
| 11 | **Backup/export** | CSV and JSON export are for **data analysis only** (transactions only, no app config). Exports are NOT backups; there is no restore path. PDF export is per-month only. See Sprint 6. |
| 12 | **Recurring transactions** | NOT in v1. Defer to v2. |
| 13 | **Pay date roll-back** | Each cycle has two toggles, both default ON: `rollBackOnWeekend` (Sat/Sun → previous Friday) and `rollBackOnHoliday` (holiday → previous business day). The algorithm walks back as many days as needed (no max) until a non-weekend, non-holiday day is found. See Sprint 1 (§7.1). |
| 14 | **Effective vs nominal date** | Period boundaries always use the *effective* date (after roll-back). A nominal payday on Sat May 31 with weekend roll-back active means the new period starts Fri May 30. |
| 15 | **Holidays** | Stored in Room. Pre-seeded from `assets/holidays_sv_default.json` (14 entries — see Sprint 1). User can edit, disable, delete, or add. App updates refresh movable dates without overwriting user changes. |
| 16 | **Period freshness** | Periods are NEVER frozen. Always computed from current cycle + holiday state. Editing a cycle or holiday retroactively re-buckets all past transactions. |
| 17 | **Period auto-advance** | A daily WorkManager job at 00:05 local time writes a `PayEvent` for any cycle whose effective date is today. Idempotent (dedupes against existing events for the same date). |
| 18 | **CSV export shape** | Single file, all years, with a `year` column derived from `occurred_at` in device timezone. Pay events included as synthetic income rows with `is_pay_event=true`. See Sprint 6. |
| 19 | **JSON export shape** | Top-level array of one-key objects keyed by year (`[{"2025": {...}}, {"2026": {...}}]`). Each year contains transaction count, income/expense totals, and a transaction array. Categories denormalized to names per-transaction (no separate category list). Pay events included as synthetic income rows. See Sprint 6. |
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

**See `SPRINTS/SPRINT_0_SETUP.md` § Data Model for full entity definitions including:**
- `Transaction` (id, type, amountCents, categoryId, description, occurredAt, createdAt, deletedAt)
- `Category` (id, name, applicability, colorHex, createdAt, archivedAt)
- `Budget` (id, categoryId, amountCents, cycleId, active)
- `PayCycle` (id, rule, rollBackOnWeekend, rollBackOnHoliday, active, createdAt)
- `PayEvent` (id, occurredAt, createdAt)
- `Holiday` (id, label, dateOfYear, recurringYearly, enabled)

---

## 5. Database & Repositories

See `SPRINTS/SPRINT_0_SETUP.md` § Database & Repositories for:
- Room database setup and version strategy
- DAO definitions (TransactionDao, CategoryDao, BudgetDao, PayCycleDao, PayEventDao, HolidayDao)
- Repository layer with reactive Flow-based queries
- Hilt module setup

---

## 6. Core Use Cases & Business Logic

See `SPRINTS/SPRINT_0_SETUP.md` § Use Cases & Business Logic for:
- `GetCurrentPeriodUseCase` — compute effective period boundaries
- `HolidayResolver` — resolve holidays with caching
- Pay cycle rule evaluation and rollback logic
- Period re-bucketing on cycle/holiday changes

---

## 7. Sprint Structure

Each sprint has its own file in the `/SPRINTS/` directory. Read the file corresponding to your current sprint:

- **`SPRINT_0_SETUP.md`** — Project scaffolding, DB schema, core use cases
- **`SPRINT_1_TRANSACTION_ENTRY.md`** — Entry screen, transaction list, edit/delete
- **`SPRINT_2_CATEGORIES_BUDGETS.md`** — Category management, budgets, auto-color assignment
- **`SPRINT_3_PAY_CYCLES.md`** — Pay cycles, holidays, "I've been paid" button
- **`SPRINT_4_WIDGETS.md`** — Glance small & medium entry widgets
- **`SPRINT_5_REPORTS.md`** — Report screen, charts, suggestions, PDF export
- **`SPRINT_6_EXPORT.md`** — CSV/JSON export (Settings → Export data)
- **`SPRINT_7_POLISH.md`** — Error states, empty states, dark theme, accessibility, signed APK

---

## 8. Working Agreement for Claude Code

- **Run autonomously.** Do not ask for permission between sub-tasks within a sprint. Ask only when blocked by a genuinely ambiguous decision not covered in this document.
- **Commit per sprint.** One commit at sprint completion with a message like `feat(sprint-3): categories and budgets`.
- **Tests are part of "done."** A sprint is not complete without its verification block passing.
- **No silent dependency upgrades.** If a library version needs bumping mid-sprint, log it in the commit message.
- **When in doubt, prefer simple over clever.** This is a personal-use app — readability wins over micro-optimization.
- **Use the latest stable versions** in the version catalog at the time of Sprint 0. Do not pin to outdated versions.
- **Load sprint files on demand.** When starting a new sprint, you may discard the previous sprint's detailed instructions to manage context.

---

## 9. Open items for v2 (do not implement now)

- Recurring transactions (auto-log monthly rent etc.).
- Multi-currency.
- Cloud sync (e.g., user-controlled WebDAV target).
- Receipt photo attachment.
- Budget rollovers (carry unused budget to next period).
- iOS port.

---

*End of master spec. Begin with `SPRINTS/SPRINT_0_SETUP.md`.*
