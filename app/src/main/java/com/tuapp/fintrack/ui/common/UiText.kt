package com.tuapp.fintrack.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.tuapp.fintrack.R
import com.tuapp.fintrack.data.model.CategoryApplicability
import com.tuapp.fintrack.data.model.TransactionType

/** A localisable message produced outside the UI layer (ViewModels, engines). */
data class UiMessage(@param:StringRes val id: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes id: Int, vararg args: Any) : this(id, args.toList())

    fun resolve(context: Context): String = context.getString(id, *args.toTypedArray())

    @Composable
    fun resolve(): String = stringResource(id, *args.toTypedArray())
}

@StringRes
fun TransactionType.labelRes(): Int = when (this) {
    TransactionType.EXPENSE -> R.string.transaction_type_expense
    TransactionType.INCOME -> R.string.transaction_type_income
    TransactionType.RESERVE -> R.string.transaction_type_reserve
}

@StringRes
fun CategoryApplicability.labelRes(): Int = when (this) {
    CategoryApplicability.EXPENSE -> R.string.transaction_type_expense
    CategoryApplicability.INCOME -> R.string.transaction_type_income
    CategoryApplicability.BOTH -> R.string.category_applicability_both
}
