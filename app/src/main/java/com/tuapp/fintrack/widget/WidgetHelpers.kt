package com.tuapp.fintrack.widget

import android.content.Context
import com.tuapp.fintrack.data.model.Category
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.repository.FinTrackRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRepositoryEntryPoint {
    fun repository(): FinTrackRepository
}

suspend fun loadCategoriesForWidget(context: Context, selectedType: String): List<Category> {
    val entryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext,
        WidgetRepositoryEntryPoint::class.java
    )
    val allCats = entryPoint.repository().allCategories.first()
    return allCats.filter { cat ->
        when (selectedType) {
            "INCOME" -> cat.applicability == CategoryApplicability.INCOME || cat.applicability == CategoryApplicability.BOTH
            else -> cat.applicability == CategoryApplicability.EXPENSE || cat.applicability == CategoryApplicability.BOTH
        }
    }
}

fun formatDateShort(epochMs: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

fun formatCurrency(cents: Long): String = "$%.2f".format(cents / 100.0)
