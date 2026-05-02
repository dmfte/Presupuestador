# SPRINT_7_POLISH.md — Polish & Hardening

**Parent:** `CLAUDE.md`

---

## Overview

Final sprint: error states, empty states, dark theme support, accessibility (TalkBack, minimum touch targets), app icon, and signed APK generation for sideload. This sprint hardens the app for real-world use.

---

## Tasks

### Error States & Validation Feedback

- [ ] Every screen with user input must show clear error messages inline:
  - Amount > 0 validation on Entry screen (red text below field).
  - Required category validation (if setting is ON) — red text and disabled Save button.
  - Unique category name validation (case-insensitive) — warn if duplicate.
  - Invalid date range on Transactions filter — disable invalid combinations.
  - Network/IO errors (unlikely in offline app, but log them gracefully).

- [ ] Bottom-sheet or dialog error states:
  - "Failed to save transaction. Please try again."
  - "Failed to export. Please try again."
  - "Database error. Please restart the app."

- [ ] Show snackbar errors briefly (2–3 seconds) with "Retry" or "Dismiss" action.

---

### Empty States with CTAs

- [ ] Transaction list when no transactions exist:
  - Icon + message: "No transactions yet. Start by tapping the FAB to log one."
  - Show FAB prominently.

- [ ] Categories list when empty:
  - Icon + message: "No categories. Tap + to create one, or leave transactions uncategorized."

- [ ] Budgets list when empty:
  - Icon + message: "No budgets set. Create a budget to track spending limits."

- [ ] Report when no transactions in selected month:
  - Icon + message: "No data for {month}. Try a different month or add transactions."

- [ ] Each empty state has a CTA button (e.g., "Add first transaction") if applicable.

---

### Dark Theme Support

- [ ] Material 3 `DynamicColorScheme` supports dark theme automatically if you use Material colors.
- [ ] Test all screens in dark mode (Android Settings → Display → Dark theme).
- [ ] Verify:
  - Text contrast is sufficient (WCAG AA: 4.5:1 for normal text).
  - Chart colors are visible (pie chart labels, bar heights, line colors).
  - Buttons and interactive elements are clearly defined.
  - Icon colors adapt (e.g., white icons on dark bg, black on light).

- [ ] Theme configuration:
  ```kotlin
  val DarkColorScheme = darkColorScheme(
      primary = Color(0xFFBB86FC),
      secondary = Color(0xFF03DAC6),
      tertiary = Color(0xFF03DAC6),
      background = Color(0xFF121212),
      surface = Color(0xFF1F1F1F),
      error = Color(0xFFCF6679)
  )
  
  @Composable
  fun FinTrackTheme(
      darkTheme: Boolean = isSystemInDarkTheme(),
      content: @Composable () -> Unit
  ) {
      val colorScheme = when {
          darkTheme -> DarkColorScheme
          else -> LightColorScheme
      }
      
      MaterialTheme(
          colorScheme = colorScheme,
          typography = Typography,
          content = content
      )
  }
  ```

---

### Accessibility (A11y)

#### TalkBack Labels & Descriptions

- [ ] All interactive elements have `semantics`:
  ```kotlin
  Button(
      onClick = { ... },
      modifier = Modifier.semantics {
          contentDescription = "Save transaction"
      }
  )
  ```

- [ ] For charts, provide `contentDescription` with summary (e.g., "Pie chart: groceries 35%, utilities 20%, other 45%").

- [ ] Form labels are properly associated with inputs (Compose handles this with `label` parameter).

- [ ] Icons have descriptions if not accompanied by text:
  ```kotlin
  Icon(
      Icons.Default.Add,
      contentDescription = "Add new transaction",
      tint = Color.Blue
  )
  ```

#### Minimum Touch Targets

- [ ] All buttons and interactive elements have ≥48dp × 48dp touch target (Material 3 default).
- [ ] Spacing between adjacent buttons ≥8dp.
- [ ] Verify in Compose UI: `modifier.size(48.dp)` or `modifier.minimumInteractiveComponentSize()`.

#### Color Contrast

- [ ] WCAG AA compliance (4.5:1 for normal text, 3:1 for large text).
- [ ] Use Android Accessibility Scanner or manual review on dark/light themes.

#### Text Scaling

- [ ] App respects system font size settings (Settings → Display → Font size).
- [ ] Use `sp` (scale-independent pixels) for text, not `dp`.
- [ ] Test at 100%, 125%, 150%, 200% system scales.

---

### App Icon & Branding

- [ ] Design or use a placeholder icon (≥192×192 px, ideally scalable SVG).
- [ ] Create adaptive icon (Android 8+):
  - `res/mipmap-anydpi-v33/ic_launcher.xml` (foreground + background).
  - Foreground: SVG or PNG of the main icon design.
  - Background: single color or shape.

- [ ] Set in `AndroidManifest.xml`:
  ```xml
  <application
      android:icon="@mipmap/ic_launcher"
      android:roundIcon="@mipmap/ic_launcher_round"
      ...
  />
  ```

- [ ] App label:
  ```xml
  <application
      android:label="@string/app_name"
      ...
  />
  ```
  where `strings.xml` contains `<string name="app_name">FinTrack</string>`.

---

### Crash Reporting Hook (Logging)

- [ ] Implement a no-op crash logging interceptor for v1 (groundwork for v2 crash reporting):
  ```kotlin
  object CrashReporter {
      fun log(exception: Exception, message: String = "") {
          Log.e("FinTrackCrash", message, exception)
          // v2: send to Sentry, Firebase, etc.
      }
      
      fun logUI(screenName: String, event: String) {
          Log.d("FinTrackEvent", "$screenName: $event")
      }
  }
  ```

- [ ] Call `CrashReporter.log()` in ViewModel exception handlers:
  ```kotlin
  viewModelScope.launch {
      try {
          repository.addTransaction(tx)
      } catch (e: Exception) {
          CrashReporter.log(e, "Failed to add transaction")
          uiState.value = uiState.value.copy(error = "Failed to save")
      }
  }
  ```

---

### Generate Signed APK for Sideload

- [ ] Create a keystore (one-time):
  ```bash
  keytool -genkey -v -keystore release.keystore \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -alias fintrack-release
  ```
  - Enter password (remember it!).
  - Common name (CN): Your name or "FinTrack Release".

- [ ] Configure signing in `build.gradle.kts`:
  ```kotlin
  android {
      signingConfigs {
          create("release") {
              storeFile = file("release.keystore")
              storePassword = System.getenv("KEYSTORE_PASSWORD")
              keyAlias = "fintrack-release"
              keyPassword = System.getenv("KEY_PASSWORD")
          }
      }
      
      buildTypes {
          release {
              signingConfig = signingConfigs.getByName("release")
              isMinifyEnabled = true
              proguardFiles(
                  getDefaultProguardFile("proguard-android-optimize.txt"),
                  "proguard-rules.pro"
              )
          }
      }
  }
  ```

- [ ] Build signed APK:
  ```bash
  export KEYSTORE_PASSWORD="your-password"
  export KEY_PASSWORD="your-key-password"
  ./gradlew assembleRelease
  ```
  Output: `app/build/outputs/apk/release/app-release.apk`.

- [ ] Sign verification (optional):
  ```bash
  jarsigner -verify -verbose -certs app-release.apk
  ```

- [ ] Share or sideload:
  - Transfer APK to Android device.
  - Settings → Security → Unknown Sources (enable).
  - Open file manager, tap APK to install.

---

### Testing & Verification

- [ ] Smoke test (manual):
  - Install signed APK on a device.
  - Add 20 mixed transactions across two periods.
  - Set 2 budgets for different categories.
  - Generate a report for the current month; verify all charts render.
  - Export PDF → opens in PDF viewer, 3+ pages.
  - Export CSV → opens in Sheets with year column populated.
  - Export JSON → opens in text editor, validates as JSON.
  - Toggle dark theme; verify all screens readable.
  - Open each screen; verify empty states if applicable.

- [ ] Unit tests:
  - Test error handling in ViewModels (exceptions → error messages).
  - Test crash reporter logging.

- [ ] Accessibility (manual):
  - Enable TalkBack on device.
  - Navigate every screen using TalkBack.
  - Verify all buttons/fields have descriptions.
  - Test touch targets with Accessibility Scanner app.

---

## Verification Checklist

- [ ] All screens show inline validation errors (red text).
- [ ] Empty states appear when lists are empty; each has a CTA.
- [ ] App renders correctly in dark mode (no contrast issues, all elements visible).
- [ ] All interactive elements have `contentDescription` and ≥48dp touch target.
- [ ] TalkBack can navigate every screen; all buttons/fields have labels.
- [ ] System font scaling (100%, 150%, 200%) doesn't break UI.
- [ ] App icon appears on home screen; adaptive icon renders correctly.
- [ ] No unhandled exceptions (all wrapped in try-catch with logging).
- [ ] Smoke test passes: fresh install → 20 transactions → report + all exports → all files valid.
- [ ] Signed APK is ≤15MB, can be sideloaded and runs without Play Store.

---

*End of Sprint 7. All sprints complete. Begin initial testing and refinement.*
