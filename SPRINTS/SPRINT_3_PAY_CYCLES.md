# SPRINT_3_PAY_CYCLES.md — Pay Cycles, Holidays & "I've Been Paid"

**Parent:** `CLAUDE.md`

---

## Overview

Build the pay cycle and holiday settings screens, implement the "I've been paid today" button on Home, and wire the daily auto-advance worker. This sprint makes the period system fully user-configurable and responsive.

---

## Tasks

### Pay Cycles Settings Screen

- [ ] Create `PayCyclesScreen` composable with:
  - LazyColumn listing all active pay cycles.
  - Each item shows: rule summary (e.g., "15th of month", "Last day of month", "Every Friday"), two toggle switches ("Roll back if weekend", "Roll back if holiday"), and delete button.
  - FAB to add a new pay cycle.

- [ ] Add Pay Cycle:
  - Multi-step dialog/sheet:
    - **Step 1 (Rule picker):** Select rule type:
      - "Day of month" → number input (1–31, with hint "31 or leave blank = last day of month").
      - "Last day of month" → no input.
      - "Specific date" → date picker (a fixed date like May 15 every year).
      - "Weekly" → day-of-week picker (Monday, Tuesday, ..., Sunday).
      - "Biweekly" → anchor date picker + day-of-week picker.
    - **Step 2 (Rollback settings):** Two toggles, both default ON:
      - "Roll back if weekend" (Sat/Sun → previous Friday).
      - "Roll back if holiday" (holiday → previous business day).
    - Save to database as a `PayCycle` with rule JSON-encoded.

- [ ] Edit Pay Cycle:
  - Tap a cycle → open edit dialog with rule and toggles pre-filled.
  - Allow editing rule and toggles.
  - On save: call `PayCycleDao.update()`, then call `holidayResolver.invalidate()` to clear cache and trigger home screen update.

- [ ] Delete Pay Cycle:
  - Swipe or long-press → soft-delete (set `active = false`).
  - Confirm first.
  - Show undo snackbar.

- [ ] Display Effective Payday:
  - For each cycle, show computed effective next payday (applying rollbacks).
  - Example: "Next payday: Fri Apr 30 (nominally May 1)" if rollback changes the date.
  - Update whenever holidays/cycles change.

- [ ] Create `PayCyclesViewModel` that exposes:
  - `payCycles: Flow<List<PayCycleWithEffectiveDate>>` — reactive list with computed effective paydays.
  - `onAddPayCycle(rule: PayCycleRule, rollBackOnWeekend: Boolean, rollBackOnHoliday: Boolean)` — create.
  - `onEditPayCycle(id: Long, rule: PayCycleRule, rollBackOnWeekend: Boolean, rollBackOnHoliday: Boolean)` — update and invalidate cache.
  - `onDeletePayCycle(id: Long)` — soft-delete and invalidate cache.
  - `onUndoDelete(id: Long)` — restore.

---

### Holidays Settings Screen

- [ ] Create `HolidaysScreen` composable with:
  - LazyColumn listing all holidays (both default and custom).
  - Each item shows: holiday label, date, toggle (enabled/disabled), edit button, delete button.
  - FAB to add a custom holiday.

- [ ] Add Holiday:
  - Dialog/sheet that captures:
    - Label (text input, e.g., "My Birthday").
    - Date of year (date picker, selecting just month/day or a specific year+month+day).
    - Toggle: "Recurring yearly" (default ON).
  - Save to database as a `Holiday`.

- [ ] Edit Holiday:
  - Tap a holiday → open edit dialog with label, date, and recurring toggle pre-filled.
  - Allow editing label, date, and recurring flag.
  - Save changes.
  - On save: call `holidayResolver.invalidate()` to clear cache and trigger home screen update.

- [ ] Toggle Enable/Disable:
  - Toggle switch on each holiday item → update `enabled` boolean without deleting.
  - Instant update, no confirmation needed.

- [ ] Delete Holiday:
  - Delete button → soft-delete (set `enabled = false` or hard-delete, your choice; soft-delete preserves history).
  - Confirm first.
  - Show undo snackbar.

- [ ] Default Holidays:
  - Pre-seeded holidays (from Sprint 0) are marked as "default"; allow editing label or disabling, but do not allow deleting (UI shows "Read-only" or "Delete" is grayed out).
  - Actually, allow deletion of defaults too; just show that undo is available.

- [ ] Create `HolidaysViewModel` that exposes:
  - `holidays: Flow<List<Holiday>>` — reactive list of all holidays.
  - `onAddHoliday(label: String, dateMs: Long, recurringYearly: Boolean)` — create.
  - `onEditHoliday(id: Long, label: String, dateMs: Long, recurringYearly: Boolean)` — update and invalidate cache.
  - `onToggleHoliday(id: Long, enabled: Boolean)` — update enabled flag and invalidate cache.
  - `onDeleteHoliday(id: Long)` — soft-delete and invalidate cache.
  - `onUndoDelete(id: Long)` — restore.

---

### "I've Been Paid Today" Button

- [ ] Add a prominent button on the Home screen: **"I've Been Paid Today"**.
  - Position: top-right or below the period summary.
  - Styling: solid color (e.g., primary color), tap animation.

- [ ] On Tap:
  - Show a confirmation dialog: "Mark today as a payday? The current period will reset immediately."
  - On confirm:
    - Call `PayEventDao.insert()` with `occurredAt = today (at 00:00 in device timezone)` and `createdAt = now()`.
    - Trigger `GetCurrentPeriodUseCase` to recompute period boundaries.
    - Update Home screen to show the new period.
    - Show snackbar: "Period reset. Next payday: {date}".

- [ ] Idempotency:
  - If a `PayEvent` already exists for today, tapping the button again should NOT create a duplicate.
  - Check `payEventDao.existsForDate(today)` before inserting.

- [ ] Create a use case `RecordManualPayEventUseCase` that wraps this logic.

---

### Home Screen Integration

- [ ] Wire `GetCurrentPeriodUseCase` into Home screen:
  - Display period bounds (e.g., "Apr 15 – May 14").
  - Display days remaining (e.g., "5 days left").
  - Display next effective payday (applying rollbacks).
  - If effective payday differs from nominal, show both (e.g., "Next paycheck: Fri Apr 30 (nominally May 1)").

- [ ] Period Summary Widget:
  - On the Home screen, show a summary card with:
    - Current period date range.
    - Days remaining (progress bar?).
    - Total income this period.
    - Total expenses this period.
    - Net (income - expenses).
  - Update reactively as transactions are added.

- [ ] Home Screen ViewModel:
  - `currentPeriod: Flow<PayPeriod>` — from `GetCurrentPeriodUseCase`.
  - `periodSummary: Flow<PeriodSummary>` — computed from `GetCurrentPeriodUseCase` + transaction queries.
  - `onPaymentRecorded()` — call `RecordManualPayEventUseCase`.

---

### Daily Auto-Advance Worker

- [ ] Implement a `PeriodicWorkRequest` using `WorkManager`:
  - Schedule to run daily at 00:05 local time.
  - Each day, check all active pay cycles for paydays falling on today.
  - For each payday, check if a `PayEvent` already exists for today (idempotent dedup).
  - If not, insert a new `PayEvent` with `occurredAt = today (00:00 device timezone)`.

- [ ] Worker code:
  ```kotlin
  class PayPeriodAdvanceWorker(
      context: Context,
      params: WorkerParameters
  ) : CoroutineWorker(context, params) {
      override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
          try {
              val payCycleDao = FinTrackDatabase.getInstance(applicationContext).payCycleDao()
              val payEventDao = FinTrackDatabase.getInstance(applicationContext).payEventDao()
              val holidayResolver = HolidayResolver(...)
              val getCurrent = GetCurrentPeriodUseCase(...)
              
              val today = System.currentTimeMillis().toLocalDate()
              val cycles = payCycleDao.getAllActive().first()
              
              for (cycle in cycles) {
                  val nextPayday = cycle.rule.computeNextOccurrence(today)
                  if (nextPayday == today) {
                      val exists = payEventDao.existsForDate(today)
                      if (!exists) {
                          payEventDao.insert(
                              PayEvent(
                                  occurredAt = today.atStartOfDay().toEpochMillis(),
                                  createdAt = System.currentTimeMillis()
                              )
                          )
                      }
                  }
              }
              Result.success()
          } catch (e: Exception) {
              Log.e("PayPeriodAdvanceWorker", "Error", e)
              Result.retry()
          }
      }
  }
  ```

- [ ] Schedule from `Application.onCreate`:
  ```kotlin
  val workRequest = PeriodicWorkRequestBuilder<PayPeriodAdvanceWorker>(
      1, TimeUnit.DAYS
  ).setInitialDelay(5, TimeUnit.MINUTES).build()
  
  WorkManager.getInstance(context).enqueueUniquePeriodicWork(
      "pay_period_advance",
      ExistingPeriodicWorkPolicy.KEEP,
      workRequest
  )
  ```

- [ ] Test with `WorkManager.testDriver` (instrumented test):
  - Force the worker to run.
  - Verify exactly one `PayEvent` is created per effective payday.
  - Verify it skips if a `PayEvent` already exists for that date.

---

## Testing

- [ ] Unit tests for `PayCyclesViewModel`: can add, edit, delete cycles; rule evaluation correct.
- [ ] Unit tests for `HolidaysViewModel`: can add, edit, toggle, delete holidays.
- [ ] Unit tests for `GetCurrentPeriodUseCase` with holidays and rollbacks (from Sprint 0; expand here if needed).
- [ ] Instrumented tests for `PayPeriodAdvanceWorker`: force run, verify idempotent event creation.
- [ ] Compose UI tests: add cycle with rollback, verify effective payday displays correctly; toggle holiday, verify effective payday updates.

---

## Verification Checklist

- [ ] Can add a "Last day of month" cycle with both rollback flags ON; on Sat May 31, effective payday shows Fri May 30.
- [ ] Can add a holiday on Fri May 30; effective payday shifts to Thu May 29.
- [ ] Can edit cycle or holiday; effective payday updates immediately.
- [ ] Can delete/undo delete cycles and holidays.
- [ ] "I've been paid today" button shows confirmation dialog; on confirm, resets period and shows new next payday.
- [ ] Tapping button twice does not create duplicate `PayEvent` for the same day.
- [ ] Home screen displays current period dates, days remaining, and period summary (income/expense/net).
- [ ] Daily worker (forced via test driver) creates exactly one `PayEvent` per effective payday; skips if already exists.
- [ ] Widget updates when holidays or cycles change (via `GlanceAppWidget.update()` call after `holidayResolver.invalidate()`).

---

*End of Sprint 3. Proceed to `SPRINT_4_WIDGETS.md` when verified.*
