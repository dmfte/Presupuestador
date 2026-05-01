package com.tuapp.fintrack.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "holidays")
data class Holiday(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val dateOfYear: Long,
    val recurringYearly: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long
)
