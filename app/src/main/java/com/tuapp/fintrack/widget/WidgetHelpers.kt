package com.tuapp.fintrack.widget

import com.tuapp.fintrack.data.repository.FinTrackRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetRepositoryEntryPoint {
    fun repository(): FinTrackRepository
}

fun formatDateShort(epochMs: Long): String {
    val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
    return sdf.format(Date(epochMs))
}

fun formatCurrency(cents: Long): String = "$%.2f".format(cents / 100.0)
