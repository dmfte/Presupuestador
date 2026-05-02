# SPRINT_4_WIDGETS.md — Widgets (Glance)

**Parent:** `CLAUDE.md`

---

## Overview

Implement two Jetpack Glance widgets: Small (quick entry only) and Medium (quick entry + period summary). Both use the same entry form but differ in layout. Glance has limitations (no arbitrary Composables, no inline keyboard input), so widgets open a helper activity for amount entry.

---

## Architecture

Glance widgets are constrained: no arbitrary Composables, limited state management, no direct keyboard access. Our solution:

1. **SmallEntryWidget & MediumEntryWidget** — Glance composables with buttons and fixed UI.
2. **WidgetEntryActivity** — A simple Activity that opens when the user needs to enter an amount. It has a focused number input, and returns to the widget on save.
3. **WidgetActionCallback** — Handles button taps: writes transaction to DB → triggers widget update.
4. **Widget state** — Stored in DataStore for the widget to read (period summary for Medium widget).

---

## Tasks

### Small Entry Widget

- [ ] Create `SmallEntryWidget` Glance composable:
  - **Layout:** Vertical column with:
    - Type toggle: two buttons (INCOME / EXPENSE), one selected at a time.
    - Amount display: large text showing current amount (initially "0.00"), tappable to open `WidgetEntryActivity`.
    - Category dropdown (Glance `GlanceComposable` equivalent; list of categories or a button that says "Select category").
    - Save button.
  
  - **Constraints (Glance):**
    - Use `GlanceComposable` or `Text`, `Button`, `Column`, `Row` only.
    - No `TextField` or `OutlinedTextField` — amount input opens Activity instead.
    - Category selection: show a dropdown or a button; tapping opens a chooser Activity if needed, or use a static list if ≤5 categories.

- [ ] Create `WidgetEntryActivity`:
  - Minimal UI: a number input field (large, focused by default), a "Done" button.
  - `onCreate`: read the amount from `intent.getDoubleExtra("amount", 0.0)`.
  - `onDone`: write amount back to `intent` and call `setResult(RESULT_OK, intent)`.
  - Return to widget.

- [ ] Create `SmallEntryWidgetActionCallback`:
  - Handles button taps:
    - **Type toggle:** update transient state (in memory or DataStore).
    - **Amount button:** launch `WidgetEntryActivity` with current amount.
    - **Category dropdown:** update state.
    - **Save button:** create Transaction in Room, update widget.

- [ ] Wire `ActionCallback` to Glance:
  ```kotlin
  @Composable
  fun SmallEntryWidget() {
      Box(
          modifier = GlanceModifier
              .fillMaxSize()
              .background(Color.White)
              .padding(16.dp)
      ) {
          Column(verticalAlignment = Alignment.CenterVertically) {
              // Type toggle
              Row {
                  Button(
                      text = "Income",
                      onClick = actionStartActivity(
                          Intent(context, SmallEntryWidgetActionCallback::class.java)
                              .apply { putExtra("action", "type_income") }
                      )
                  )
                  Button(
                      text = "Expense",
                      onClick = actionStartActivity(...)
                  )
              }
              
              // Amount
              Button(
                  text = "Amount: $0.00",
                  onClick = actionStartActivity(
                      Intent(context, WidgetEntryActivity::class.java)
                  )
              )
              
              // Category (if many, use a button; if few, use Row of buttons)
              // ...
              
              // Save
              Button(
                  text = "Save",
                  onClick = actionRunCallback(SmallEntryWidgetActionCallback::class.java)
              )
          }
      }
  }
  ```

---

### Medium Entry Widget

- [ ] Create `MediumEntryWidget` Glance composable:
  - **Layout:** Same entry controls as SmallEntryWidget, but with a period summary strip on top:
    - Period date range (e.g., "Apr 15 – May 14").
    - Current period income (large, green).
    - Current period expenses (large, red).
    - Net (green if positive, red if negative).
  
  - **Period Summary Data:**
    - Read from DataStore (updated by the app whenever transactions change).
    - Or compute on-the-fly if Room queries are available to Glance (check Glance+Room integration).

- [ ] Update the Medium widget whenever:
  - A transaction is saved.
  - The period changes.
  - Manual "I've been paid today" is triggered.

---

### Glance App Widget Manager

- [ ] Create `FinTrackGlanceAppWidgetReceiver`:
  ```kotlin
  class FinTrackGlanceAppWidgetReceiver : GlanceAppWidgetReceiver() {
      override val glanceAppWidget: GlanceAppWidget = SmallEntryGlanceWidget()
  }
  ```
  (or separate receivers for Small and Medium).

- [ ] Register in `AndroidManifest.xml`:
  ```xml
  <receiver
      android:name=".widget.FinTrackGlanceAppWidgetReceiver"
      android:exported="true">
      <intent-filter>
          <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
      </intent-filter>
      <meta-data
          android:name="android.appwidget.provider"
          android:resource="@xml/app_widget_provider" />
  </receiver>
  ```

- [ ] Create `res/xml/app_widget_provider.xml`:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <appwidget-provider
      xmlns:android="http://schemas.android.com/apk/res/android"
      android:initialLayout="@layout/widget_small"
      android:minWidth="110dp"
      android:minHeight="110dp"
      android:widgetCategory="home_screen"
      android:resizeableCategory="vertical" />
  ```

---

### Widget Update Trigger

- [ ] When a transaction is saved (in `EntryViewModel.onSave()` or the repository):
  - Call `GlanceAppWidget.update(context, SmallEntryGlanceWidget::class.java)` (or both Small and Medium).
  - This forces the widget to recompose and display the updated data.

- [ ] When the period changes (e.g., "I've been paid today"):
  - Update the Medium widget summary.
  - Call `GlanceAppWidget.update()`.

---

### WidgetEntryActivity Design

```kotlin
class WidgetEntryActivity : AppCompatActivity() {
    private lateinit var amountInput: EditText
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_entry)
        
        amountInput = findViewById(R.id.amount_input)
        amountInput.requestFocus()
        // Show keyboard
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showSoftInput(amountInput, InputMethodManager.SHOW_IMPLICIT)
        
        findViewById<Button>(R.id.done_button).setOnClickListener {
            val amount = amountInput.text.toString().toDoubleOrNull() ?: 0.0
            setResult(RESULT_OK, Intent().apply {
                putExtra("amount_cents", (amount * 100).toLong())
            })
            finish()
        }
    }
}
```

**Layout (`activity_widget_entry.xml`):**
```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp"
    android:gravity="center">
    
    <EditText
        android:id="@+id/amount_input"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:inputType="numberDecimal"
        android:hint="Amount (USD)"
        android:textSize="24sp"
        android:gravity="center" />
    
    <Button
        android:id="@+id/done_button"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="16dp"
        android:text="Done" />
</LinearLayout>
```

---

### Testing

- [ ] Compose UI tests (Glance): add widget to home screen; tap entry button; verify UI responds.
- [ ] Instrumented tests: save a transaction from the widget; verify it appears in the app.
- [ ] Instrumented tests: Medium widget; verify period summary updates after transaction.

---

## Verification Checklist

- [ ] Both Small and Medium widget sizes are installable on home screen.
- [ ] Saving a transaction from either widget creates the transaction in Room.
- [ ] Transaction is immediately visible in the app's transaction list.
- [ ] Medium widget's period summary updates within 1–2 seconds after adding a transaction.
- [ ] Amount entry field opens `WidgetEntryActivity` and returns the entered amount.
- [ ] Type toggle works (Income / Expense).
- [ ] Category dropdown displays available categories for the selected type.
- [ ] All Glance UI constraints are respected (no Compose-exclusive features).
- [ ] Widget survives app restart and home screen restart.

---

*End of Sprint 4. Proceed to `SPRINT_5_REPORTS.md` when verified.*
