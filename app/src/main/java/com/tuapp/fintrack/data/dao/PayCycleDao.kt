package com.tuapp.fintrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tuapp.fintrack.data.model.PayCycle
import kotlinx.coroutines.flow.Flow

@Dao
interface PayCycleDao {
    @Insert
    suspend fun insert(cycle: PayCycle): Long

    @Update
    suspend fun update(cycle: PayCycle)

    @Query("SELECT * FROM pay_cycles WHERE active = 1 ORDER BY createdAt ASC")
    fun getAllActive(): Flow<List<PayCycle>>

    @Query("SELECT * FROM pay_cycles WHERE active = 1 ORDER BY createdAt ASC")
    suspend fun getAllActiveOnce(): List<PayCycle>

    @Query("UPDATE pay_cycles SET active = 0 WHERE id = :id")
    suspend fun deactivate(id: Long)
}
