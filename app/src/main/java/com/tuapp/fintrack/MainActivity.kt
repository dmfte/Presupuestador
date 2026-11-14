package com.tuapp.fintrack

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tuapp.fintrack.ui.MainViewModel
import com.tuapp.fintrack.ui.common.StartingBalanceDialog
import com.tuapp.fintrack.ui.navigation.FinTrackNavHost
import com.tuapp.fintrack.ui.theme.FinTrackTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only honour the widget deep-link on a fresh launch, not on configuration changes.
        val transactionType =
            if (savedInstanceState == null) intent.getStringExtra(EXTRA_TRANSACTION_TYPE) else null

        setContent {
            FinTrackTheme {
                FinTrackNavHost(initialTransactionType = transactionType)
                FirstTimeStartingBalanceGate()
            }
        }
    }

    companion object {
        const val EXTRA_TRANSACTION_TYPE = "transaction_type"
    }
}

@Composable
private fun FirstTimeStartingBalanceGate(
    mainViewModel: MainViewModel = hiltViewModel()
) {
    val show by mainViewModel.showStartingBalancePrompt.collectAsStateWithLifecycle()
    if (show) {
        StartingBalanceDialog(
            title = stringResource(R.string.starting_balance_welcome_title),
            message = stringResource(R.string.starting_balance_welcome_message),
            initialCents = 0L,
            confirmLabel = stringResource(R.string.starting_balance_save),
            skipLabel = stringResource(R.string.starting_balance_skip),
            dismissOnOutsideTap = false,
            onConfirm = { mainViewModel.setStartingBalance(it) },
            onSkip = { mainViewModel.skipStartingBalancePrompt() },
            onDismiss = {}
        )
    }
}
