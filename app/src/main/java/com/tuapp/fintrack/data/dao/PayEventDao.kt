package com.tuapp.fintrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.tuapp.fintrack.data.model.PayEvent
import kotlinx.coroutines.flow.Flow

@Dao
interface PayEventDao {
    @Insert
    suspend fun insert(event: PayEvent): Long

    @Query("SELECT * FROM pay_events ORDER BY occurredAt DESC")
    fun getAllActive(): Flow<List<PayEvent>>

    @Query("SELECT * FROM pay_events ORDER BY occurredAt DESC")
    suspend fun getAllOnce(): List<PayEvent>

    @Query("SELECT * FROM pay_events WHERE occurredAt BETWEEN :startMs AND :endMs ORDER BY occurredAt DESC")
    suspend fun getByDateRange(startMs: Long, endMs: Long): List<PayEvent>

    @Query("SELECT COUNT(*) FROM pay_events WHERE occurredAt BETWEEN :startMs AND :endMs")
    suspend fun countInRange(startMs: Long, endMs: Long): Int
}
