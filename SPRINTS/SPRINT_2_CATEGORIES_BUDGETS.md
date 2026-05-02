# SPRINT_2_CATEGORIES_BUDGETS.md — Categories & Budgets

**Parent:** `CLAUDE.md`

---

## Overview

Build the category management screen and budget management screen. Categories support categorization of both income and expenses. Budgets are allocated per category per pay cycle and tracked in real-time.

---

## Tasks

### Categories Management Screen

- [ ] Create `CategoriesScreen` composable with:
  - LazyColumn listing all active categories.
  - Each item shows: color swatch, category name, applicability badge (Expense / Income / Both).
  - FAB to add a new category.

- [ ] Add Category:
  - Dialog/sheet that captures: category name, applicability toggle (Expense / Income / Both).
  - Auto-assign a color from a 12-color palette (cycle through in order: red, orange, yellow, green, cyan, blue, purple, pink, brown, grey, teal, indigo).
  - Save to database.
  - Show confirmation ("Category added: {name}").

- [ ] Edit Category:
  - Tap a category → opens edit dialog pre-filled with name, applicability, and current color.
  - Allow editing name and applicability.
  - Allow re-picking the color from the same 12-color palette.
  - Save changes (UPDATE).
  - Show confirmation ("Category updated").

- [ ] Archive Category:
  - Long-press or swipe to archive a category (soft-delete: set `archivedAt`).
  - Confirm before archiving ("Archived categories are hidden from entry forms but remain in historical data").
  - Show undo snackbar.

- [ ] Validation:
  - Category name must be non-empty and unique (case-insensitive).
  - Block archiving if the category has active budgets (show a warning; offer to delete the budgets first).

- [ ] Create `CategoriesViewModel` that exposes:
  - `categories: Flow<List<Category>>` — reactive list of active categories.
  - `onAddCategory(name: String, applicability: CategoryApplicability)` — create and auto-assign color.
  - `onEditCategory(id: Long, name: String, applicability: CategoryApplicability, colorHex: String)` — update.
  - `onArchiveCategory(id: Long)` — soft-delete.
  - `onUndoArchive(id: Long)` — restore.

---

### Budgets Management Screen

- [ ] Create `BudgetsScreen` composable with:
  - LazyColumn listing all active budgets.
  - Each item shows:
    - Category name (with color swatch).
    - Pay cycle label (e.g., "15th of month").
    - Budgeted amount (formatted as currency).
    - Progress bar showing current-period spending vs budget (green if under, yellow if ≥75%, red if over).
    - Spent amount (in progress bar label).
  - FAB to add a new budget.

- [ ] Add Budget:
  - Dialog/sheet with:
    - Category dropdown (only active categories with `applicability` matching expected type).
    - Pay cycle dropdown (all active cycles).
    - Budget amount input (numeric, formatted as currency).
  - Validation:
    - Cannot set a budget on an archived category (disable category option).
    - Cannot set duplicate budgets (one per category per cycle).
  - Save to database.
  - Show confirmation ("Budget created: {amount} for {category}").

- [ ] Edit Budget:
  - Tap a budget → open edit dialog with category, cycle, and amount pre-filled.
  - Allow editing amount only (category and cycle are fixed to prevent re-bucketing confusion).
  - Save changes.
  - Show "Budget updated" snackbar.

- [ ] Delete Budget:
  - Swipe or long-press to delete.
  - Confirm ("This will remove the budget; category will have no limit").
  - Set `active = false` (soft-delete).
  - Show undo snackbar.

- [ ] Real-Time Progress:
  - Progress bar computes: `currentPeriodSpending / budgetAmount`.
  - Current period is determined by `GetCurrentPeriodUseCase` (from Sprint 0).
  - Update reactively as new transactions are added.

- [ ] Create `BudgetsViewModel` that exposes:
  - `budgets: Flow<List<BudgetWithProgress>>` — reactive list with computed progress.
  - `currentPeriod: Flow<PayPeriod>` — from `GetCurrentPeriodUseCase`.
  - `onAddBudget(categoryId: Long, cycleId: Long, amountCents: Long)` — create.
  - `onEditBudget(id: Long, amountCents: Long)` — update amount.
  - `onDeleteBudget(id: Long)` — soft-delete.
  - `onUndoDelete(id: Long)` — restore.

---

### Data Classes

```kotlin
data class BudgetWithProgress(
    val budget: Budget,
    val category: Category,
    val cycle: PayCycle,
    val currentPeriodSpendingCents: Long,
    val progressPercent: Int  // 0-100+
)
```

---

## Color Palette

Define a 12-color palette as a constant list:

```kotlin
val CATEGORY_COLORS = listOf(
    "#FF5722", // red
    "#FF9800", // orange
    "#FFC107", // yellow
    "#8BC34A", // green
    "#00BCD4", // cyan
    "#2196F3", // blue
    "#9C27B0", // purple
    "#E91E63", // pink
    "#795548", // brown
    "#9E9E9E", // grey
    "#009688", // teal
    "#3F51B5"  // indigo
)
```

When creating a category, cycle through this list by `(categoryCount % CATEGORY_COLORS.size)`.

---

## Testing

- [ ] Unit tests for `CategoriesViewModel`: can add, edit, archive categories; validation prevents duplicate names.
- [ ] Unit tests for `BudgetsViewModel`: can add/edit/delete budgets; cannot set budget on archived category; progress updates correctly.
- [ ] Compose UI tests: add category, verify it appears and is selectable in budget creation; create budget, verify progress bar appears and updates on transaction creation.

---

## Verification Checklist

- [ ] Can add a category with name and applicability; color auto-assigned; appears in list.
- [ ] Can edit category name, applicability, and color; changes persist.
- [ ] Can archive a category; archived categories hidden from entry form but undo works.
- [ ] Cannot archive a category with active budgets (warning shown).
- [ ] Can create a budget: select category, cycle, amount; progress bar appears.
- [ ] Cannot create duplicate budget for same category+cycle.
- [ ] Cannot set budget on archived category (option disabled).
- [ ] Can edit budget amount; progress bar updates immediately.
- [ ] Can delete budget; undo snackbar works.
- [ ] Progress bar color: green (<75%), yellow (≥75%, <100%), red (≥100%).
- [ ] Budget amount displays in current currency (USD).
- [ ] Color palette cycles through 12 colors as categories are created.

---

*End of Sprint 2. Proceed to `SPRINT_3_PAY_CYCLES.md` when verified.*
