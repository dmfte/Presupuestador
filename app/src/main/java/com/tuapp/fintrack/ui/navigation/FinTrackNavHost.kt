package com.tuapp.fintrack.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tuapp.fintrack.ui.budgets.BudgetsScreen
import com.tuapp.fintrack.ui.categories.CategoriesScreen
import com.tuapp.fintrack.ui.entry.EntryScreen
import com.tuapp.fintrack.ui.export.ExportDataScreen
import com.tuapp.fintrack.ui.home.HomeScreen
import com.tuapp.fintrack.ui.list.TransactionListScreen
import com.tuapp.fintrack.ui.report.ReportScreen
import com.tuapp.fintrack.ui.settings.SettingsScreen

@Composable
fun FinTrackNavHost(initialTransactionType: String? = null) {
    val navController = rememberNavController()

    var pendingType by remember { mutableStateOf(initialTransactionType) }
    LaunchedEffect(pendingType) {
        pendingType?.let { type ->
            navController.navigate(Screen.Entry.route(type = type))
            pendingType = null
        }
    }

    NavHost(
        navController = navController,
        startDestination = Screen.Home.route
    ) {
        composable(Screen.Home.route) {
            HomeScreen(
                onAddTransaction = { navController.navigate(Screen.Entry.route()) },
                onViewTransactions = { navController.navigate(Screen.TransactionList.route) },
                onViewCategories = { navController.navigate(Screen.Categories.route) },
                onViewBudgets = { navController.navigate(Screen.Budgets.route) },
                onViewReport = { navController.navigate(Screen.Report.route) },
                onViewSettings = { navController.navigate(Screen.Settings.route) }
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

        composable(Screen.Report.route) {
            ReportScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() },
                onExportData = { navController.navigate(Screen.ExportData.route) }
            )
        }

        composable(Screen.ExportData.route) {
            ExportDataScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(
            route = Screen.Entry.route,
            arguments = listOf(
                navArgument("transactionId") {
                    type = NavType.LongType
                    defaultValue = -1L
                },
                navArgument("type") {
                    type = NavType.StringType
                    defaultValue = ""
                }
            )
        ) {
            EntryScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
