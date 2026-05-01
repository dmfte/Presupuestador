package com.tuapp.fintrack.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object TransactionList : Screen("transaction_list")
    data object Entry : Screen("entry?transactionId={transactionId}") {
        fun route(transactionId: Long? = null) =
            if (transactionId != null) "entry?transactionId=$transactionId" else "entry"
    }
}
