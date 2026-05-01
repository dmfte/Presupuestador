package com.tuapp.fintrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tuapp.fintrack.data.dao.BudgetDao
import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.HolidayDao
import com.tuapp.fintrack.data.dao.PayCycleDao
import com.tuapp.fintrack.data.dao.PayEventDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.Holiday
import com.tuapp.fintrack.data.model.PayCycle
import com.tuapp.fintrack.data.model.PayEvent
import com.tuapp.fintrack.data.model.Transaction

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
@TypeConverters(Converters::class)
abstract class FinTrackDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
    abstract fun payCycleDao(): PayCycleDao
    abstract fun payEventDao(): PayEventDao
    abstract fun holidayDao(): HolidayDao
}
