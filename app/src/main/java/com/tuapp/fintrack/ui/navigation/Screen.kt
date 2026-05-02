package com.tuapp.fintrack.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object TransactionList : Screen("transaction_list")
    data object Entry : Screen("entry?transactionId={transactionId}") {
        fun route(transactionId: Long? = null) =
            if (transactionId != null) "entry?transactionId=$transactionId" else "entry"
    }
    data object Categories : Screen("categories")
    data object Budgets : Screen("budgets")
    data object PayCycles : Screen("pay_cycles")
    data object Holidays : Screen("holidays")
}
