package com.tuapp.fintrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tuapp.fintrack.data.model.Budget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Insert
    suspend fun insert(budget: Budget): Long

    @Update
    suspend fun update(budget: Budget)

    @Query("SELECT * FROM budgets WHERE active = 1")
    fun getAllActive(): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE categoryId = :categoryId AND active = 1")
    fun getByCategoryId(categoryId: Long): Flow<List<Budget>>

    @Query("SELECT * FROM budgets WHERE cycleId = :cycleId AND active = 1")
    fun getByCycleId(cycleId: Long): Flow<List<Budget>>

    @Query("UPDATE budgets SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}
