# SPRINT_0_SETUP.md — Project Scaffolding & Core Infrastructure

**Parent:** `CLAUDE.md`

---

## Overview

Set up the Android project structure, Room database with all entities, DAOs, repositories, and core use cases (especially `GetCurrentPeriodUseCase` and `HolidayResolver`). This sprint establishes the single source of truth (Room) and the reactive layer (Flow) that all other sprints depend on.

---

## Data Model (Full Entity Definitions)

All amounts stored as **`Long` cents** (e.g., $12.34 = `1234L`). Never use `Float`/`Double` for money. Convert at the UI boundary only.

### Transaction Entity

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
```

### Category Entity

```kotlin
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
```

### Budget Entity

```kotlin
@Entity(
    tableName = "budgets",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = PayCycle::class,
            parentColumns = ["id"],
            childColumns = ["cycleId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Budget(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val categoryId: Long,               // FK to categories
    val amountCents: Long,              // budgeted amount per cycle
    val cycleId: Long,                  // which pay cycle this budget is bound to
    val active: Boolean = true,
    val createdAt: Long
)
```

### PayCycle Entity

```kotlin
@Entity(tableName = "pay_cycles")
data class PayCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rule: String,                   // JSON-encoded rule object (see §7.1)
    val rollBackOnWeekend: Boolean = true,
    val rollBackOnHoliday: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long
)

// Rule types: DayOfMonthRule, LastDayOfMonthRule, SpecificDateRule, WeeklyRule, BiweeklyRule
// Encode/decode as JSON in the repository layer using kotlinx.serialization
```

### PayEvent Entity

```kotlin
@Entity(tableName = "pay_events")
data class PayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAt: Long,               // epoch millis, the effective payday
    val createdAt: Long
)
```

### Holiday Entity

```kotlin
@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,                  // e.g., "Christmas", "Labor Day"
    val dateOfYear: Long,               // epoch millis for THIS YEAR's occurrence
    val recurringYearly: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long
)
```

---

## Database & Repositories

### Room Database Setup

```kotlin
@Database(
    entities = [
        Transaction::class,
        Category::class,
        Budget::class,
        PayCycle::class,
        PayEvent::class,
        Holiday::class
    ],
    version = 1,
    exportSchema = true
)
abstract class FinTrackDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun payCycleDao(): PayCycleDao
    abstract fun payEventDao(): PayEventDao
    abstract fun holidayDao(): HolidayDao

    companion object {
        @Volatile
        private var instance: FinTrackDatabase? = null

        fun getInstance(context: Context): FinTrackDatabase =
            instance ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    FinTrackDatabase::class.java,
                    "fintrack.db"
                )
                    .createFromAsset("holidays_sv_default.json")  // Pre-seed holidays
                    .build()
                    .also { instance = it }
            }
    }
}
```

### DAOs

**TransactionDao:**
```kotlin
@Dao
interface TransactionDao {
    @Insert suspend fun insert(transaction: Transaction): Long
    @Update suspend fun update(transaction: Transaction)
    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)
    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun getAllActive(): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE type = :type AND deletedAt IS NULL ORDER BY occurredAt DESC")
    fun getByType(type: TransactionType): Flow<List<Transaction>>
    @Query("SELECT * FROM transactions WHERE occurredAt BETWEEN :startMs AND :endMs AND deletedAt IS NULL")
    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<Transaction>>
}
```

**CategoryDao:**
```kotlin
@Dao
interface CategoryDao {
    @Insert suspend fun insert(category: Category): Long
    @Update suspend fun update(category: Category)
    @Query("SELECT * FROM categories WHERE archivedAt IS NULL ORDER BY name")
    fun getAllActive(): Flow<List<Category>>
    @Query("SELECT * FROM categories WHERE archivedAt IS NULL AND applicability IN (:types)")
    fun getByApplicability(types: List<CategoryApplicability>): Flow<List<Category>>
    @Query("UPDATE categories SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun archive(id: Long, archivedAt: Long)
}
```

**BudgetDao, PayCycleDao, PayEventDao, HolidayDao** — follow similar patterns.

### Repository Layer

```kotlin
@Singleton
class FinTrackRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val payCycleDao: PayCycleDao,
    private val payEventDao: PayEventDao,
    private val holidayDao: HolidayDao
) {
    // Expose Flow<T> from DAOs
    val allTransactions = transactionDao.getAllActive()
    val allCategories = categoryDao.getAllActive()
    val allPayCycles = payCycleDao.getAllActive()
    val allPayEvents = payEventDao.getAllActive()
    val allHolidays = holidayDao.getAllActive()

    suspend fun addTransaction(tx: Transaction) = transactionDao.insert(tx)
    suspend fun addCategory(cat: Category) = categoryDao.insert(cat)
    // ... etc.
}
```

### Hilt Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Singleton
    @Provides
    fun providesFinTrackDatabase(context: Context): FinTrackDatabase =
        FinTrackDatabase.getInstance(context)

    @Singleton
    @Provides
    fun providesTransactionDao(db: FinTrackDatabase): TransactionDao = db.transactionDao()
    // ... provide other DAOs
}
```

---

## Core Use Cases & Business Logic

### GetCurrentPeriodUseCase

Computes the *effective* period boundaries for the current moment. This is the most critical piece.

```kotlin
@Singleton
class GetCurrentPeriodUseCase @Inject constructor(
    private val payCycleDao: PayCycleDao,
    private val payEventDao: PayEventDao,
    private val holidayResolver: HolidayResolver
) {
    suspend operator fun invoke(): PayPeriod {
        val now = System.currentTimeMillis()
        val cycles = payCycleDao.getAllActive().first()
        
        if (cycles.isEmpty()) {
            // Fallback: 15th and last day of month
            return computePeriodForDate(now)
        }
        
        // Find the period containing 'now'
        var current = now
        var periodStart: Long? = null
        var periodEnd: Long? = null
        var nextPayday: Long? = null
        
        // Sort cycles by their effective dates
        // Walk forward/backward to find current period
        // Apply roll-back rules via holidayResolver
        
        return PayPeriod(
            startDateMs = periodStart!!,
            endDateMs = periodEnd!!,
            nextPaydayMs = nextPayday!!,
            daysRemaining = computeDaysRemaining(periodEnd!!, now)
        )
    }
    
    private fun applyRollBack(
        nominalDateMs: Long,
        cycle: PayCycle,
        holidays: List<Holiday>
    ): Long {
        var effective = nominalDateMs
        val calendar = Calendar.getInstance().apply { timeInMillis = effective }
        
        while (true) {
            val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
            val isWeekend = dayOfWeek in listOf(Calendar.SATURDAY, Calendar.SUNDAY)
            val isHoliday = holidays.any { it.dateOfYear == effective && it.enabled }
            
            if (!isWeekend && !isHoliday) break
            if (isWeekend && !cycle.rollBackOnWeekend) break
            if (isHoliday && !cycle.rollBackOnHoliday) break
            
            calendar.add(Calendar.DAY_OF_MONTH, -1)
            effective = calendar.timeInMillis
        }
        
        return effective
    }
}

data class PayPeriod(
    val startDateMs: Long,
    val endDateMs: Long,
    val nextPaydayMs: Long,
    val daysRemaining: Int
)
```

### HolidayResolver

Caches holiday resolution to avoid repeated computation.

```kotlin
@Singleton
class HolidayResolver @Inject constructor(
    private val holidayDao: HolidayDao
) {
    private var cachedHolidays: List<Holiday>? = null
    private var cacheValidMs: Long? = null
    private val CACHE_TTL_MS = 60_000  // 1 minute

    suspend fun getHolidaysForYear(year: Int): List<Holiday> {
        val now = System.currentTimeMillis()
        
        if (cachedHolidays != null && cacheValidMs != null && 
            (now - cacheValidMs!!) < CACHE_TTL_MS) {
            return cachedHolidays!!
        }
        
        val allHolidays = holidayDao.getAllActive().first()
        cachedHolidays = allHolidays.filter { isInYear(it.dateOfYear, year) }
        cacheValidMs = now
        
        return cachedHolidays!!
    }

    fun invalidate() {
        cachedHolidays = null
        cacheValidMs = null
    }

    private fun isInYear(dateMs: Long, year: Int): Boolean {
        val calendar = Calendar.getInstance().apply { timeInMillis = dateMs }
        return calendar.get(Calendar.YEAR) == year
    }
}
```

### Pay Cycle Rule Evaluation

Encode pay cycle rules as JSON for flexibility:

```kotlin
@Serializable
sealed class PayCycleRule {
    @Serializable
    data class DayOfMonthRule(val day: Int) : PayCycleRule()  // 1-31, 31 = last day
    
    @Serializable
    data object LastDayOfMonthRule : PayCycleRule()
    
    @Serializable
    data class SpecificDateRule(val dateMs: Long) : PayCycleRule()
    
    @Serializable
    data class WeeklyRule(val dayOfWeekAnchor: Int) : PayCycleRule()  // 1=Sunday, 7=Saturday
    
    @Serializable
    data class BiweeklyRule(val anchorDateMs: Long, val dayOfWeek: Int) : PayCycleRule()
}

fun PayCycleRule.computeNextOccurrence(fromDateMs: Long): Long {
    // Decode and compute based on rule type
    // Return the effective date after applying roll-backs
}
```

---

## Tasks for Sprint 0

- [ ] Create Gradle project with FinTrack package name.
- [ ] Add Room, Hilt, Kotlin Coroutines, kotlinx.serialization to `libs.versions.toml`.
- [ ] Implement all entities and DAOs as specified above.
- [ ] Create `FinTrackDatabase` with Room setup and pre-seed from `assets/holidays_sv_default.json`.
- [ ] Implement `FinTrackRepository` with reactive Flow queries.
- [ ] Implement `GetCurrentPeriodUseCase` with full rollback logic.
- [ ] Implement `HolidayResolver` with caching.
- [ ] Implement `PayCycleRule` serialization and evaluation.
- [ ] Add default PayCycles (15th and last day of month) on first app launch.
- [ ] Create `assets/holidays_sv_default.json` with 14 Salvadoran holidays (see list below).
- [ ] Write unit tests for `GetCurrentPeriodUseCase`, `HolidayResolver`, and pay cycle rule evaluation.
- [ ] Verify via DB inspector: on first launch, 2 pay cycles + 14 holidays are created. Inserting a debug transaction via a debug button persists across app restart.

---

## Default Holidays (Pre-seeded)

Create `assets/holidays_sv_default.json`:

```json
[
  { "label": "New Year", "dateOfYear": "2025-01-01", "recurringYearly": true, "enabled": true },
  { "label": "Good Friday", "dateOfYear": "2025-04-18", "recurringYearly": false, "enabled": true },
  { "label": "Holy Saturday", "dateOfYear": "2025-04-19", "recurringYearly": false, "enabled": true },
  { "label": "Labor Day", "dateOfYear": "2025-05-01", "recurringYearly": true, "enabled": true },
  { "label": "Father's Day", "dateOfYear": "2025-06-15", "recurringYearly": true, "enabled": true },
  { "label": "Salvadoran Independence Day", "dateOfYear": "2025-09-15", "recurringYearly": true, "enabled": true },
  { "label": "Columbus Day", "dateOfYear": "2025-10-12", "recurringYearly": true, "enabled": true },
  { "label": "All Souls' Day", "dateOfYear": "2025-11-02", "recurringYearly": true, "enabled": true },
  { "label": "Immaculate Conception", "dateOfYear": "2025-12-08", "recurringYearly": true, "enabled": true },
  { "label": "Christmas Eve", "dateOfYear": "2025-12-24", "recurringYearly": true, "enabled": true },
  { "label": "Christmas Day", "dateOfYear": "2025-12-25", "recurringYearly": true, "enabled": true },
  { "label": "New Year's Eve", "dateOfYear": "2025-12-31", "recurringYearly": true, "enabled": true },
  { "label": "Mother's Day", "dateOfYear": "2025-05-10", "recurringYearly": true, "enabled": true },
  { "label": "Peasants' Day", "dateOfYear": "2025-11-17", "recurringYearly": true, "enabled": true }
]
```

---

## Verification Checklist

- [ ] All §4 entities compile without errors.
- [ ] All §5 DAOs compile and expose `Flow<T>`.
- [ ] Room database initializes on first launch and pre-seeds 14 holidays.
- [ ] `GetCurrentPeriodUseCase` tests pass: given a date range, cycles, and holidays, compute correct period boundaries and apply rollbacks.
- [ ] `HolidayResolver` caches correctly; `invalidate()` clears cache.
- [ ] Pay cycle rule evaluation tests pass (day-of-month, last day, weekly, biweekly).
- [ ] Debug transaction inserted via a button persists across app restart.
- [ ] App schema is exported to `build/databases/` for version tracking.

---

*End of Sprint 0. Proceed to `SPRINT_1_TRANSACTION_ENTRY.md` when this sprint is verified.*
