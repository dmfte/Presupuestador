package com.tuapp.fintrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import kotlinx.coroutines.flow.Flow

data class TypeTotal(val type: TransactionType, val totalCents: Long)

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

    @Query(
        "SELECT type, SUM(amountCents) AS totalCents FROM transactions " +
            "WHERE deletedAt IS NULL AND occurredAt >= :sinceMs GROUP BY type"
    )
    fun totalsByTypeSince(sinceMs: Long): Flow<List<TypeTotal>>

    @Query("SELECT * FROM transactions WHERE deletedAt IS NULL ORDER BY occurredAt DESC, id DESC LIMIT :limit")
    fun getRecent(limit: Int): Flow<List<Transaction>>

    @Query("SELECT COUNT(*) FROM transactions WHERE deletedAt IS NULL")
    fun countActive(): Flow<Int>

    @Query("SELECT * FROM transactions WHERE type = :type AND deletedAt IS NULL AND occurredAt >= :sinceMs ORDER BY occurredAt ASC, id ASC")
    suspend fun getActiveByTypeSince(type: TransactionType, sinceMs: Long): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}
