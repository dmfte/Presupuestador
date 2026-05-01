package com.tuapp.fintrack.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pay_cycles")
data class PayCycle(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val rule: String,
    val rollBackOnWeekend: Boolean = true,
    val rollBackOnHoliday: Boolean = true,
    val active: Boolean = true,
    val createdAt: Long
)
