package com.tuapp.fintrack.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("categoryId")]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TransactionType,
    val amountCents: Long,
    val categoryId: Long? = null,
    val description: String = "",
    val occurredAt: Long,
    val createdAt: Long,
    val deletedAt: Long? = null
)

enum class TransactionType { INCOME, EXPENSE }
