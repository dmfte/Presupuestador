package com.tuapp.fintrack.di

import android.content.Context
import androidx.room.Room
import com.tuapp.fintrack.data.dao.BudgetDao
import com.tuapp.fintrack.data.dao.CategoryDao
import com.tuapp.fintrack.data.dao.HolidayDao
import com.tuapp.fintrack.data.dao.PayCycleDao
import com.tuapp.fintrack.data.dao.PayEventDao
import com.tuapp.fintrack.data.dao.TransactionDao
import com.tuapp.fintrack.data.db.FinTrackDatabase
import com.tuapp.fintrack.data.db.FinTrackDatabaseCallback
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Singleton
    @Provides
    fun provideDatabase(@ApplicationContext context: Context): FinTrackDatabase =
        Room.databaseBuilder(context, FinTrackDatabase::class.java, "fintrack.db")
            .addCallback(FinTrackDatabaseCallback(context))
            .build()

    @Singleton
    @Provides
    fun provideTransactionDao(db: FinTrackDatabase): TransactionDao = db.transactionDao()

    @Singleton
    @Provides
    fun provideCategoryDao(db: FinTrackDatabase): CategoryDao = db.categoryDao()

    @Singleton
    @Provides
    fun provideBudgetDao(db: FinTrackDatabase): BudgetDao = db.budgetDao()

    @Singleton
    @Provides
    fun providePayCycleDao(db: FinTrackDatabase): PayCycleDao = db.payCycleDao()

    @Singleton
    @Provides
    fun providePayEventDao(db: FinTrackDatabase): PayEventDao = db.payEventDao()

    @Singleton
    @Provides
    fun provideHolidayDao(db: FinTrackDatabase): HolidayDao = db.holidayDao()
}
