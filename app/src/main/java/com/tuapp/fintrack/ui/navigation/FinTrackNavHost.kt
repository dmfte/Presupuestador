package com.tuapp.fintrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tuapp.fintrack.ui.budgets.BudgetsScreen
import com.tuapp.fintrack.ui.categories.CategoriesScreen
import com.tuapp.fintrack.ui.entry.EntryScreen
import com.tuapp.fintrack.ui.home.HomeScreen
import com.tuapp.fintrack.ui.list.TransactionListScreen

@Composable
fun FinTrackNavHost() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAddTransaction = { navController.navigate(Screen.Entry.route()) },
                onViewTransactions = { navController.navigate(Screen.TransactionList.route) },
                onViewCategories = { navController.navigate(Screen.Categories.route) },
                onViewBudgets = { navController.navigate(Screen.Budgets.route) }
            )
        }

        composable(Screen.TransactionList.route) {
            TransactionListScreen(
                onNavigateBack = { navController.popBackStack() },
                onEditTransaction = { id -> navController.navigate(Screen.Entry.route(id)) }
            )
        }

        composable(Screen.Categories.route) {
            CategoriesScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Budgets.route) {
            BudgetsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Entry.route,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.LongType
                    defaultValue = -1L
                }
            )
        ) {
            EntryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
