package com.tuapp.fintrack.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val applicability: CategoryApplicability,
    val colorHex: String,
    val createdAt: Long,
    val archivedAt: Long? = null
)

enum class CategoryApplicability { EXPENSE, INCOME, BOTH }
