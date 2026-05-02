# SPRINT_1_TRANSACTION_ENTRY.md — Transaction Entry & List

**Parent:** `CLAUDE.md`

---

## Overview

Build the entry screen where users log transactions, and the transaction list with filtering, editing, and soft-delete capabilities. This sprint establishes the primary user interaction flow.

---

## Tasks

### Entry Screen (Compose)

- [ ] Create `EntryScreen` composable with the following fields:
  - Type toggle (Income / Expense)
  - Amount input (numeric, formatted as currency on display)
  - Category dropdown (select from existing or "Add new" inline)
  - Description text field (optional)
  - Date picker (defaults to today, user can select past/future)
  - Save button

- [ ] Implement category combo box:
  - Dropdown lists all active categories matching the selected type.
  - "Add new category" option at the bottom opens an inline dialog.
  - Dialog captures category name, auto-assigns a color from a 12-color palette (round-robin).
  - Save the new category and auto-select it in the dropdown.

- [ ] Validation:
  - Amount must be > 0.
  - If `require_category` toggle is ON in Settings, category must be set.
  - Show inline error messages (red text) for each field.

- [ ] On Save:
  - Call `FinTrackRepository.addTransaction()`.
  - Show a brief success snackbar ("Transaction saved").
  - Clear the form and reset to defaults.

- [ ] Create `EntryViewModel` (MVVM) that exposes:
  - `uiState: StateFlow<EntryUiState>` with form fields and validation state.
  - `onTypeChanged(type: TransactionType)` — update type and refresh category dropdown.
  - `onAmountChanged(amountCents: Long)` — validate > 0.
  - `onCategorySelected(categoryId: Long?)` — set or unset.
  - `onDescriptionChanged(text: String)`
  - `onDateChanged(dateMs: Long)`
  - `onSave()` — persist and reset form.

---

### Transaction List Screen

- [ ] Create `TransactionListScreen` composable with:
  - LazyColumn of transaction items (most recent first).
  - Each item shows: type icon, amount, category (if set), date, description (truncated).
  - Swipe or long-press to access Edit/Delete options.

- [ ] Implement filtering:
  - Type filter: All / Income / Expense (tabs or dropdown).
  - Category filter: All / specific category (dropdown, only shows categories with transactions).
  - Date range filter: "This month", "Last month", "Custom range" (date picker pair).
  - Search by description: text input, case-insensitive substring match.

- [ ] Implement Edit:
  - Tap an item → opens `EntryScreen` pre-filled with the transaction's data.
  - On Save, call `FinTrackRepository.update(transaction)`.
  - Show "Updated" snackbar.

- [ ] Implement Soft-Delete:
  - Tap Delete → show confirmation dialog or immediately soft-delete.
  - Call `FinTrackRepository.softDelete(id)` which sets `deletedAt = now()`.
  - Show an "Undo" snackbar that calls `FinTrackRepository.restore(id)`.

- [ ] Create `TransactionListViewModel` that exposes:
  - `filteredTransactions: Flow<List<Transaction>>` — reactive list applying all active filters.
  - `onTypeFilterChanged(type: TransactionType?)`
  - `onCategoryFilterChanged(categoryId: Long?)`
  - `onDateRangeChanged(startMs: Long?, endMs: Long?)`
  - `onSearchChanged(query: String)`
  - `onEditTransaction(transaction: Transaction)` — navigate to Entry screen.
  - `onDeleteTransaction(id: Long)` — soft-delete.
  - `onRestoreTransaction(id: Long)` — restore from undo.

---

### Home Screen Integration

- [ ] Add a FAB (floating action button) on the Home screen that navigates to `EntryScreen`.
- [ ] Add a way to access the transaction list (e.g., "View all transactions" button or tab navigation).

---

## UI Layout Notes

**Entry Screen:**
- Column layout: type toggle at top, amount field (large, prominent), category dropdown, description field, date picker, save button at bottom.
- Use Material 3 components: `OutlinedButton`, `SegmentedButton`, `ExposedDropdownMenuBox`, `TextField`, `DatePicker`.
- Form should be scrollable if keyboard is open.

**Transaction List:**
- `LazyColumn` with filter controls above (tabs for type, dropdowns for category/date, search bar).
- Each transaction item: left side shows type icon + amount (color-coded by category or income/expense), right side shows date + description.
- Gesture: swipe for actions or long-press menu.

---

## Testing

- [ ] Unit tests for `EntryViewModel`: validate amount, test validation on required category, test form reset after save.
- [ ] Unit tests for `TransactionListViewModel`: test filtering by type, category, date range, and search independently and in combination.
- [ ] Compose UI tests: can enter a transaction and see it appear in the list; can edit and see changes; can delete and undo.

---

## Verification Checklist

- [ ] Can create a new transaction from the Entry screen; it appears in the transaction list immediately.
- [ ] Can edit a transaction; changes persist and are visible in the list.
- [ ] Can soft-delete a transaction; "Undo" snackbar restores it.
- [ ] Type filter (All/Income/Expense) works; list updates reactively.
- [ ] Category filter shows only categories with transactions and filters correctly.
- [ ] Date range filter works; "This month" and "Last month" presets work.
- [ ] Search by description is case-insensitive and finds partial matches.
- [ ] `require_category` toggle in Settings prevents saving without a category when enabled.
- [ ] Category combo box can create a new category inline and auto-select it.
- [ ] FAB on Home navigates to Entry screen.
- [ ] Navigating between Entry and List screens preserves form state (if not saved) or shows fresh form (if saved).

---

*End of Sprint 1. Proceed to `SPRINT_2_CATEGORIES_BUDGETS.md` when verified.*
