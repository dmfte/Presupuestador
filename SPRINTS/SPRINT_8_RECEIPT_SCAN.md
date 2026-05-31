# SPRINT_8_RECEIPT_SCAN.md — AI Receipt Scanning

**Parent:** `CLAUDE.md`

---

## Overview

Add a feature to photograph receipts (camera or gallery) and have a cloud-based AI (Gemini) extract line items, prices, and category suggestions. A backend proxy (Firebase Cloud Function or Cloudflare Worker) holds the API key. The user reviews extracted data in a dedicated screen, then saves items as individual transactions or a single summed transaction.

**Key constraint:** No new Android permissions required. Intent-based camera and PhotoPicker are permission-free.

---

## Sprint Breakdown

| Sprint | Scope | Testable outcome |
|--------|-------|------------------|
| **S0** | Preparation — dependencies, domain models, data layer, service interface, DI, navigation route stub | Project compiles with new deps; unit tests pass for models and batch insert |
| **S1** | Backend proxy — Cloud Function with Gemini integration | `curl` a base64 receipt image to the deployed endpoint, get structured JSON back |
| **S2** | Image capture + scan integration — camera icon in EntryScreen, image acquisition, call proxy, show results | Tap camera in Entry → take photo → see parsed items logged / displayed in a loading-to-review transition |
| **S3** | Receipt review screen + save — full review UI, editing, save mode toggle, error handling, strings, widget refresh | End-to-end: scan receipt → review → edit items → save → transactions in DB, widget refreshed |

---

## S0 — Preparation

### S0.1 — Add OkHttp dependency

**Modify:** `gradle/libs.versions.toml`

Add under `[versions]`:
```toml
okhttp = "4.12.0"
```

Add under `[libraries]`:
```toml
okhttp = { group = "com.squareup.okhttp3", name = "okhttp", version.ref = "okhttp" }
```

**Modify:** `app/build.gradle.kts`

Add to `dependencies`:
```kotlin
implementation(libs.okhttp)
```

**Modify:** `proguard-rules.pro`

Add OkHttp keep rules:
```
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn okio.**
```

---

### S0.2 — Domain models

**New file:** `app/src/main/java/com/tuapp/fintrack/domain/model/ScannedReceipt.kt`

```kotlin
package com.tuapp.fintrack.domain.model

data class ScannedReceipt(
    val storeName: String?,
    val receiptDate: Long?,
    val lineItems: List<ScannedLineItem>,
    val subtotal: Long?,
    val tax: Long?,
    val total: Long?
)

data class ScannedLineItem(
    val description: String,
    val amountCents: Long,
    val quantity: Int = 1,
    val suggestedCategoryName: String?
)
```

---

### S0.3 — Receipt scan service interface + implementation

**New file:** `app/src/main/java/com/tuapp/fintrack/data/remote/ReceiptScanService.kt`

```kotlin
package com.tuapp.fintrack.data.remote

import com.tuapp.fintrack.domain.model.ScannedReceipt

interface ReceiptScanService {
    suspend fun scan(imageBytes: ByteArray, categoryNames: List<String>): ScannedReceipt
}
```

**New file:** `app/src/main/java/com/tuapp/fintrack/data/remote/CloudReceiptScanService.kt`

Implementation using OkHttp:
- Compresses image: scale to max 1024px longest side, JPEG quality 80.
- Base64-encodes the compressed image.
- POSTs JSON `{ "image": "<base64>", "categories": [...] }` to the proxy URL.
- Parses the JSON response with `kotlinx.serialization` into `ScannedReceipt`.
- Converts string dollar amounts (e.g., `"3.99"`) to `Long` cents (`399L`).
- Throws `ReceiptScanException` on HTTP errors, timeouts, or malformed responses.
- Timeout: 30 seconds for the HTTP call.

**Proxy URL:** Store as a `const val` in a companion object. For local testing, use `http://10.0.2.2:5001/<project>/<region>/scanReceipt` (Firebase emulator) or the deployed Cloud Function URL.

**Image compression helper** (private function in the service or a utility):
```kotlin
fun compressImage(imageBytes: ByteArray, maxDimension: Int = 1024, quality: Int = 80): ByteArray {
    val original = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    val scale = maxDimension.toFloat() / maxOf(original.width, original.height)
    val scaled = if (scale < 1f) {
        Bitmap.createScaledBitmap(original, (original.width * scale).toInt(), (original.height * scale).toInt(), true)
    } else original
    val stream = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
    return stream.toByteArray()
}
```

---

### S0.4 — DI module for network layer

**New file:** `app/src/main/java/com/tuapp/fintrack/di/NetworkModule.kt`

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Singleton
    @Provides
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()

    @Singleton
    @Provides
    fun provideReceiptScanService(client: OkHttpClient): ReceiptScanService =
        CloudReceiptScanService(client)
}
```

---

### S0.5 — Batch insert in DAO and repository

**Modify:** `data/dao/TransactionDao.kt`

Add:
```kotlin
@Insert
suspend fun insertAll(transactions: List<Transaction>): List<Long>
```

**Modify:** `data/repository/FinTrackRepository.kt`

Add:
```kotlin
suspend fun addTransactions(txs: List<Transaction>): List<Long> = transactionDao.insertAll(txs)
```

---

### S0.6 — Category matching use case

**New file:** `app/src/main/java/com/tuapp/fintrack/domain/usecase/MatchCategoriesUseCase.kt`

Takes `List<ScannedLineItem>` and `List<Category>`.
For each item, resolves `suggestedCategoryName` to a `categoryId`:
1. Exact case-insensitive name match.
2. Contains match (either direction).
3. `null` if no match.

Returns `List<Pair<ScannedLineItem, Long?>>` (item + resolved categoryId).

Only consider categories with `applicability` of `EXPENSE` or `BOTH` (receipts are expenses).

---

### S0.7 — Navigation route stub

**Modify:** `ui/navigation/Screen.kt`

Add:
```kotlin
data object ReceiptReview : Screen("receipt_review?imageUri={imageUri}") {
    fun route(imageUri: String): String =
        "receipt_review?imageUri=${Uri.encode(imageUri)}"
}
```

**Modify:** `ui/navigation/FinTrackNavHost.kt`

Add a placeholder composable for `ReceiptReview` (just `Text("Receipt Review — coming soon")`) to verify the route compiles. Wire it up with `navArgument("imageUri")`.

---

### S0 Verification

- [ ] Project compiles with `./gradlew assembleDebug`.
- [ ] `ScannedReceipt` and `ScannedLineItem` are importable from any file.
- [ ] `ReceiptScanService` interface exists; `CloudReceiptScanService` instantiates via Hilt.
- [ ] `TransactionDao.insertAll()` exists; returns `List<Long>`.
- [ ] `FinTrackRepository.addTransactions()` exists and delegates.
- [ ] `MatchCategoriesUseCase` resolves "groceries" → a Category named "Groceries".
- [ ] `Screen.ReceiptReview` route exists; navigating to it shows the placeholder.
- [ ] Unit tests for `MatchCategoriesUseCase` pass (exact match, contains match, no match, case-insensitive).

---

## S1 — Backend Proxy

### S1.1 — Project setup

Create a Firebase project (or Cloudflare Worker). For Firebase:

```bash
firebase init functions   # TypeScript, ESLint
cd functions
npm install @google/generative-ai
```

For Cloudflare Worker:
```bash
npx wrangler init receipt-scan-worker
```

---

### S1.2 — Cloud Function implementation

**New file:** `functions/src/index.ts`

HTTP function `scanReceipt`:
1. Validate request body has `image` (base64 string) and `categories` (string array).
2. Initialize Gemini client with API key from environment secret.
3. Send multimodal request: image (inline data, MIME `image/jpeg`) + text prompt.
4. Parse Gemini's JSON response.
5. Return the parsed JSON to the caller.

**Gemini prompt** (embedded in the function):

```
You are a receipt parser. Analyze this receipt image and extract all purchased items.

The user tracks expenses in these categories: {categories}

Return a JSON object with this exact structure:
{
  "store_name": "string or null",
  "receipt_date": "YYYY-MM-DD or null",
  "line_items": [
    {
      "description": "item name as shown on receipt",
      "amount": "decimal string like 4.99",
      "quantity": 1,
      "suggested_category": "one of the user's categories or null"
    }
  ],
  "subtotal": "decimal string or null",
  "tax": "decimal string or null",
  "total": "decimal string or null"
}

Rules:
- Amounts are in USD.
- Only include purchased items, not payment method info, change, or card numbers.
- If a line item shows quantity > 1, put the per-unit price in "amount" and the count in "quantity".
- For "suggested_category", use EXACTLY one of the user's category names or null.
- If you cannot read the receipt, return: {"error": "unreadable"}
- Return ONLY valid JSON, no markdown fences, no explanation.
```

Set `generationConfig.responseMimeType = "application/json"` for guaranteed JSON output.

**Model:** `gemini-2.0-flash` (free tier) or `gemini-2.5-flash` (cheap, better accuracy).

---

### S1.3 — Security

For personal/sideloaded use: validate a shared secret via a custom `X-App-Token` header. Store the secret as a Firebase environment config (`firebase functions:config:set app.token="..."`) and in the Android app as a BuildConfig field.

For Play Store: replace with Firebase App Check verification. The function checks the App Check token in the request header. This is a future enhancement — the shared-secret approach is sufficient for v1.

---

### S1.4 — Deploy and test

```bash
firebase deploy --only functions
```

Test with curl:
```bash
# Encode a receipt image
BASE64=$(base64 -i receipt.jpg)

curl -X POST https://<region>-<project>.cloudfunctions.net/scanReceipt \
  -H "Content-Type: application/json" \
  -H "X-App-Token: <secret>" \
  -d "{\"image\": \"$BASE64\", \"categories\": [\"Groceries\", \"Gas\", \"Dining\"]}"
```

Expected: JSON response with `store_name`, `line_items`, `total`, etc.

---

### S1 Verification

- [ ] Cloud Function deploys without errors.
- [ ] `curl` with a base64 receipt image returns valid JSON with line items.
- [ ] `curl` without `X-App-Token` returns 401.
- [ ] `curl` with a non-receipt image returns `{"error": "unreadable"}` or a best-effort parse.
- [ ] `curl` with a blurry/dark image returns partial results or the error response.
- [ ] Response time is under 10 seconds for a typical receipt.

---

## S2 — Image Capture + Scan Integration

### S2.1 — Camera icon in EntryScreen TopAppBar

**Modify:** `ui/entry/EntryScreen.kt`

Add to the `TopAppBar` `actions` slot:
```kotlin
if (!isEditing) {
    IconButton(onClick = { showImageSourceSheet = true }) {
        Icon(Icons.Default.CameraAlt, contentDescription = stringResource(R.string.scan_receipt))
    }
}
```

Show a `ModalBottomSheet` with two options:
- Row with camera icon + "Take a photo"
- Row with gallery icon + "Choose from gallery"

---

### S2.2 — Image acquisition via Activity Result contracts

In `EntryScreen.kt`, register two launchers:

```kotlin
// Camera
val photoUri = remember { mutableStateOf<Uri?>(null) }
val cameraLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.TakePicture()
) { success ->
    if (success) photoUri.value?.let { onNavigateToReceiptReview(it.toString()) }
}

// Gallery
val pickerLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.PickVisualMedia()
) { uri ->
    uri?.let { onNavigateToReceiptReview(it.toString()) }
}
```

For camera: create a temp file URI via `FileProvider.getUriForFile()` using the app cache directory. The existing `file_provider_paths.xml` has `<cache-path name="pdf_exports" path="." />` which covers the full cache dir.

Add `onNavigateToReceiptReview: (String) -> Unit` callback to `EntryScreen` params.

---

### S2.3 — Wire navigation

**Modify:** `ui/navigation/FinTrackNavHost.kt`

In the `Entry` composable, pass the new callback:
```kotlin
onNavigateToReceiptReview = { imageUri ->
    navController.navigate(Screen.ReceiptReview.route(imageUri))
}
```

Replace the placeholder composable for `ReceiptReview` with the real `ReceiptReviewScreen` (built in S3), or for now, a screen that:
1. Receives the `imageUri` argument.
2. Loads the image bytes from the URI.
3. Calls `ReceiptScanService.scan()` (injected via ViewModel).
4. Logs or displays the raw `ScannedReceipt` result.

This allows testing the full pipeline: camera → image → API call → parsed result.

---

### S2.4 — ReceiptReviewViewModel (scan logic only)

**New file:** `ui/receiptscan/ReceiptReviewViewModel.kt`

For this sprint, implement only the scanning phase:
```kotlin
@HiltViewModel
class ReceiptReviewViewModel @Inject constructor(
    private val scanService: ReceiptScanService,
    private val repository: FinTrackRepository,
    private val matchCategories: MatchCategoriesUseCase,
    private val getCurrentPeriod: GetCurrentPeriodUseCase,
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle
) : ViewModel() {
    // Phase: SCANNING → REVIEW or ERROR
    // Load image from URI, read bytes via ContentResolver
    // Call scanService.scan(imageBytes, categoryNames)
    // Run matchCategories on results
    // Populate UI state with editable items
}
```

**New file:** `ui/receiptscan/ReceiptReviewUiState.kt`

```kotlin
data class ReceiptReviewUiState(
    val phase: ScanPhase = ScanPhase.SCANNING,
    val storeName: String = "",
    val receiptDateMs: Long = System.currentTimeMillis(),
    val editableItems: List<EditableReceiptItem> = emptyList(),
    val receiptTotal: Long? = null,
    val saveMode: SaveMode = SaveMode.INDIVIDUAL,
    val errorMessage: String? = null,
    val savedEvent: Boolean = false,
    val isSaving: Boolean = false
)

enum class ScanPhase { SCANNING, REVIEW, ERROR }

enum class SaveMode { INDIVIDUAL, SINGLE }

data class EditableReceiptItem(
    val index: Int,
    val description: String,
    val amountText: String,
    val amountCents: Long,
    val categoryId: Long?,
    val isSelected: Boolean = true
)
```

---

### S2 Verification

- [ ] Camera icon appears in the EntryScreen TopAppBar (only for new transactions, not edits).
- [ ] Tapping the icon shows a bottom sheet with "Take a photo" and "Choose from gallery".
- [ ] Taking a photo with the camera returns to the app and navigates to the review route.
- [ ] Picking an image from the gallery navigates to the review route.
- [ ] The ViewModel successfully calls the backend proxy and receives a `ScannedReceipt`.
- [ ] Parsed items (or an error) are visible in the UI or logs.
- [ ] No new permissions in `AndroidManifest.xml`.

---

## S3 — Receipt Review Screen + Save

### S3.1 — Full review UI

**New file:** `ui/receiptscan/ReceiptReviewScreen.kt`

Composable layout:

```
Scaffold:
  TopAppBar: "Review Receipt", back button

  Content (based on phase):

  SCANNING:
    - Column centered: CircularProgressIndicator + "Analyzing receipt..."

  ERROR:
    - Column centered: error icon, error message text
    - "Try Again" button (retrigger scan)
    - "Enter Manually" outlined button (navigate back to EntryScreen)

  REVIEW:
    - Column with verticalScroll:
      1. Store name OutlinedTextField (prefilled, editable)
      2. Date OutlinedTextField (prefilled from receipt date, editable via DatePicker)
      3. SaveMode toggle: SingleChoiceSegmentedButtonRow
         - "Individual items" / "Single transaction"
      4. For each editableItem:
         - Card with:
           - Row: Checkbox + description OutlinedTextField (singleLine)
           - Row: Amount OutlinedTextField (decimal keyboard) + CategoryDropdown
         - Deselected items visually dimmed (alpha = 0.5)
      5. Summary row:
         - "X items selected — Total: $Y.YY"
         - If |sum - (receiptTotal - tax)| > 100 cents: warning text
           "Items don't add up to receipt total. Some may be missing."
      6. Spacer
      7. Button "Save X transactions" / "Save transaction" (based on saveMode)
         - Disabled while isSaving or no items selected
```

Reuse the `CategoryDropdown` pattern from `EntryScreen` but adapted for inline use (smaller, no "add category" option — just select from existing). Pass `availableCategories` from ViewModel state.

---

### S3.2 — ViewModel save logic

In `ReceiptReviewViewModel`, add:

**Edit handlers:**
- `onStoreNameChanged(name: String)`
- `onDateChanged(dateMs: Long)`
- `onSaveModeChanged(mode: SaveMode)`
- `onItemSelectionChanged(index: Int, selected: Boolean)`
- `onItemDescriptionChanged(index: Int, description: String)`
- `onItemAmountChanged(index: Int, amountText: String)` — parse to cents using `EntryViewModel.parseToCents()`
- `onItemCategoryChanged(index: Int, categoryId: Long?)`

**Save:**
```kotlin
fun onSave() {
    val state = _uiState.value
    val selectedItems = state.editableItems.filter { it.isSelected }
    if (selectedItems.isEmpty()) return

    _uiState.update { it.copy(isSaving = true) }
    viewModelScope.launch {
        try {
            val now = System.currentTimeMillis()
            val occurredAt = state.receiptDateMs

            val transactions = when (state.saveMode) {
                SaveMode.INDIVIDUAL -> selectedItems.map { item ->
                    Transaction(
                        type = TransactionType.EXPENSE,
                        amountCents = item.amountCents,
                        categoryId = item.categoryId,
                        description = item.description,
                        occurredAt = occurredAt,
                        createdAt = now
                    )
                }
                SaveMode.SINGLE -> listOf(
                    Transaction(
                        type = TransactionType.EXPENSE,
                        amountCents = selectedItems.sumOf { it.amountCents },
                        categoryId = selectedItems.firstOrNull()?.categoryId,
                        description = state.storeName.ifBlank { "Receipt" },
                        occurredAt = occurredAt,
                        createdAt = now
                    )
                )
            }

            repository.addTransactions(transactions)
            refreshWidgetPeriodSummary(appContext, repository, getCurrentPeriod)
            _uiState.update { it.copy(isSaving = false, savedEvent = true) }
        } catch (e: Exception) {
            CrashReporter.log(e, "Failed to save receipt transactions")
            _uiState.update { it.copy(isSaving = false, errorMessage = "Failed to save. Please try again.") }
        }
    }
}
```

---

### S3.3 — Error handling

- **No internet:** Check connectivity before calling the scan service. If unavailable, go directly to `ERROR` phase with message: "No internet connection. Receipt scanning requires internet."
- **API timeout (>30s):** `ERROR` phase with "The scan took too long. Try again."
- **HTTP 401:** "Authentication error. Please update the app."
- **HTTP 429:** "Too many requests. Please wait a moment."
- **Unreadable receipt:** If the API returns `{"error": "unreadable"}`, show "Could not read this receipt. Try a clearer photo with good lighting."
- **Empty items:** If `lineItems` is empty, show "No items found on this receipt."
- **All errors** offer "Try Again" (re-trigger scan with same image) and "Enter Manually" (pop back to Entry).

---

### S3.4 — Strings

**Modify:** `res/values/strings.xml` — add:

```xml
<!-- Receipt scan strings -->
<string name="scan_receipt">Scan receipt</string>
<string name="take_photo">Take a photo</string>
<string name="choose_from_gallery">Choose from gallery</string>
<string name="analyzing_receipt">Analyzing receipt…</string>
<string name="review_receipt">Review Receipt</string>
<string name="store_name">Store name</string>
<string name="save_mode_individual">Individual items</string>
<string name="save_mode_single">Single transaction</string>
<string name="items_selected">%1$d items selected</string>
<string name="save_n_transactions">Save %1$d transactions</string>
<string name="save_transaction">Save transaction</string>
<string name="receipt_saved">%1$d transactions saved from receipt</string>
<string name="receipt_saved_single">Transaction saved from receipt</string>
<string name="receipt_total_mismatch">Items don\'t add up to receipt total. Some may be missing.</string>
<string name="no_items_found">No items found on this receipt</string>
<string name="receipt_unreadable">Could not read this receipt. Try a clearer photo with good lighting.</string>
<string name="receipt_no_internet">No internet connection. Receipt scanning requires internet.</string>
<string name="receipt_timeout">The scan took too long. Please try again.</string>
<string name="receipt_auth_error">Authentication error. Please update the app.</string>
<string name="receipt_rate_limit">Too many requests. Please wait a moment.</string>
<string name="try_again">Try again</string>
<string name="enter_manually">Enter manually</string>
```

**Modify:** `res/values-es/strings.xml` — add Spanish translations:

```xml
<!-- Receipt scan strings -->
<string name="scan_receipt">Escanear recibo</string>
<string name="take_photo">Tomar una foto</string>
<string name="choose_from_gallery">Elegir de la galería</string>
<string name="analyzing_receipt">Analizando recibo…</string>
<string name="review_receipt">Revisar Recibo</string>
<string name="store_name">Nombre de tienda</string>
<string name="save_mode_individual">Artículos individuales</string>
<string name="save_mode_single">Transacción única</string>
<string name="items_selected">%1$d artículos seleccionados</string>
<string name="save_n_transactions">Guardar %1$d transacciones</string>
<string name="save_transaction">Guardar transacción</string>
<string name="receipt_saved">%1$d transacciones guardadas del recibo</string>
<string name="receipt_saved_single">Transacción guardada del recibo</string>
<string name="receipt_total_mismatch">Los artículos no suman el total del recibo. Pueden faltar algunos.</string>
<string name="no_items_found">No se encontraron artículos en este recibo</string>
<string name="receipt_unreadable">No se pudo leer este recibo. Intenta con una foto más clara y buena iluminación.</string>
<string name="receipt_no_internet">Sin conexión a internet. El escaneo de recibos requiere internet.</string>
<string name="receipt_timeout">El escaneo tardó demasiado. Inténtalo de nuevo.</string>
<string name="receipt_auth_error">Error de autenticación. Por favor actualiza la app.</string>
<string name="receipt_rate_limit">Demasiadas solicitudes. Espera un momento.</string>
<string name="try_again">Intentar de nuevo</string>
<string name="enter_manually">Ingresar manualmente</string>
```

---

### S3.5 — Navigation finalization

**Modify:** `ui/navigation/FinTrackNavHost.kt`

Replace the placeholder composable with the real `ReceiptReviewScreen`:
```kotlin
composable(
    route = Screen.ReceiptReview.route,
    arguments = listOf(navArgument("imageUri") { type = NavType.StringType })
) {
    ReceiptReviewScreen(
        onNavigateBack = { navController.popBackStack() },
        onNavigateToEntry = {
            navController.popBackStack()
            // User is already on the Entry screen in the back stack
        }
    )
}
```

---

### S3 Verification

- [ ] Full end-to-end: take photo → analyze → review items → save → transactions in DB.
- [ ] Full end-to-end: pick from gallery → same flow.
- [ ] Each line item is editable: description, amount, category.
- [ ] Checkbox deselects items; deselected items are not saved.
- [ ] "Individual items" mode saves N transactions (one per selected item).
- [ ] "Single transaction" mode saves 1 transaction with summed amount and store name.
- [ ] All saved transactions have `type = EXPENSE` and correct `occurredAt`.
- [ ] Widget refreshes after save (period summary updates).
- [ ] Total mismatch warning appears when items don't sum to receipt total.
- [ ] "No internet" error shown when offline.
- [ ] "Try again" retries the scan with the same image.
- [ ] "Enter manually" navigates back to the Entry screen.
- [ ] Unreadable receipt shows appropriate error message.
- [ ] Strings display correctly in English and Spanish.
- [ ] No new `<uses-permission>` in `AndroidManifest.xml`.
- [ ] `./gradlew assembleDebug` succeeds.

---

*End of Sprint 8. This is a new feature addition — does not modify any existing sprint deliverables.*
