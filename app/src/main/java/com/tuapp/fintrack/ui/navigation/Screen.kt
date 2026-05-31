package com.tuapp.fintrack.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object TransactionList : Screen("transaction_list")
    data object Entry : Screen("entry?transactionId={transactionId}&type={type}") {
        fun route(transactionId: Long? = null, type: String? = null): String {
            val params = mutableListOf<String>()
            if (transactionId != null) params.add("transactionId=$transactionId")
            if (type != null) params.add("type=$type")
            return if (params.isEmpty()) "entry" else "entry?${params.joinToString("&")}"
        }
    }
    data object Categories : Screen("categories")
    data object Budgets : Screen("budgets")
    data object Report : Screen("report")
    data object Settings : Screen("settings")
    data object ExportData : Screen("export_data")
}
