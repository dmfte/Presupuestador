package com.tuapp.fintrack.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.tuapp.fintrack.data.dao.BudgetDao
import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.model.Budget
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.Transaction

@Database(
    entities = [
        Transaction::class,
        Category::class,
        Budget::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class FinTrackDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun budgetDao(): BudgetDao
}
