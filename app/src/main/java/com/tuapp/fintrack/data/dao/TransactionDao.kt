package com.tuapp.fintrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)

    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC")
    fun getAllActive(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): Transaction?

    @Query("SELECT * FROM transactions WHERE type = :type AND deletedAt IS NULL ORDER BY occurredAt DESC")
    fun getByType(type: TransactionType): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE occurredAt BETWEEN :startMs AND :endMs AND deletedAt IS NULL ORDER BY occurredAt DESC")
    fun getByDateRange(startMs: Long, endMs: Long): Flow<List<Transaction>>
}
