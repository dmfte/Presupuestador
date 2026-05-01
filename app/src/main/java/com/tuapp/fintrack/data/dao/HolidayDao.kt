package com.tuapp.fintrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tuapp.fintrack.data.model.Holiday
import kotlinx.coroutines.flow.Flow

@Dao
interface HolidayDao {
    @Insert
    suspend fun insert(holiday: Holiday): Long

    @Update
    suspend fun update(holiday: Holiday)

    @Query("SELECT * FROM holidays WHERE enabled = 1 ORDER BY dateOfYear ASC")
    fun getAllActive(): Flow<List<Holiday>>

    @Query("SELECT * FROM holidays WHERE enabled = 1 ORDER BY dateOfYear ASC")
    suspend fun getAllActiveOnce(): List<Holiday>

    @Query("SELECT * FROM holidays ORDER BY dateOfYear ASC")
    fun getAll(): Flow<List<Holiday>>

    @Query("UPDATE holidays SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: Long, enabled: Boolean)

    @Query("DELETE FROM holidays WHERE id = :id")
    suspend fun delete(id: Long)
}
