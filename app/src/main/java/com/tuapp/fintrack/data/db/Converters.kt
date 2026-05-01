package com.tuapp.fintrack.data.db

import androidx.room.TypeConverter
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.TransactionType

class Converters {
    @TypeConverter
    fun fromTransactionType(value: TransactionType): String = value.name

    @TypeConverter
    fun toTransactionType(value: String): TransactionType = TransactionType.valueOf(value)

    @TypeConverter
    fun fromCategoryApplicability(value: CategoryApplicability): String = value.name

    @TypeConverter
    fun toCategoryApplicability(value: String): CategoryApplicability = CategoryApplicability.valueOf(value)
}
