package com.tuapp.fintrack.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pay_events")
data class PayEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val occurredAt: Long,
    val createdAt: Long
)
