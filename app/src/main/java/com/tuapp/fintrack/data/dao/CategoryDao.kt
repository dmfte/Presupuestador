package com.tuapp.fintrack.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category): Long

    @Update
    suspend fun update(category: Category)

    @Query("SELECT * FROM categories WHERE archivedAt IS NULL ORDER BY name")
    fun getAllActive(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE archivedAt IS NULL AND applicability IN (:types) ORDER BY name")
    fun getByApplicability(types: List<CategoryApplicability>): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Long): Category?

    @Query("UPDATE categories SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun archive(id: Long, archivedAt: Long)
}
