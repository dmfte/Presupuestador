package com.tuapp.fintrack.sms

import android.app.Notification
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import android.widget.Toast
import com.tuapp.fintrack.R
import com.tuapp.fintrack.data.model.Transaction
import com.tuapp.fintrack.data.model.TransactionType
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import com.tuapp.fintrack.domain.usecase.GetCurrentMonthPeriodUseCase
import com.tuapp.fintrack.widget.refreshWidgetPeriodSummary
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SmsNotificationListenerService : NotificationListenerService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun repository(): FinTrackRepository
        fun settingsRepository(): SettingsRepository
        fun getCurrentPeriodUseCase(): GetCurrentMonthPeriodUseCase
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this) ?: return
        if (sbn.packageName != defaultSmsPackage) return

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ListenerEntryPoint::class.java
        )

        scope.launch {
            try {
                val settings = entryPoint.settingsRepository()
                if (!settings.smsAutoEnabled.first()) return@launch

                val identifierText = settings.smsIdentifierText.first()
                val amountPrefix = settings.smsAmountPrefix.first()
                if (identifierText.isBlank() || amountPrefix.isBlank()) return@launch

                val extras = sbn.notification.extras
                val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: return@launch

                val parsed = SmsTransactionParser.parse(text, identifierText, amountPrefix)
                    ?: return@launch

                val typeStr = settings.smsTransactionType.first()
                val type = when (typeStr) {
                    "INCOME" -> TransactionType.INCOME
                    "RESERVE" -> TransactionType.RESERVE
                    else -> TransactionType.EXPENSE
                }
                val categoryId = settings.smsDefaultCategoryId.first()
                val description = settings.smsDescriptionTemplate.first().ifBlank { "SMS auto-transaction" }

                val now = System.currentTimeMillis()
                val repository = entryPoint.repository()

                repository.addTransaction(
                    Transaction(
                        type = type,
                        amountCents = parsed.amountCents,
                        categoryId = categoryId,
                        description = description,
                        occurredAt = now,
                        createdAt = now
                    )
                )

                val getCurrentPeriod = entryPoint.getCurrentPeriodUseCase()
                refreshWidgetPeriodSummary(applicationContext, repository, getCurrentPeriod)

                val amountStr = "$%.2f".format(parsed.amountCents / 100.0)
                val typeName = when (type) {
                    TransactionType.EXPENSE -> "expense"
                    TransactionType.INCOME -> "income"
                    TransactionType.RESERVE -> "reserve"
                }
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.sms_transaction_recorded, typeName, amountStr),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                Log.d(TAG, "Auto-created transaction: ${parsed.amountCents} cents ($type)")
            } catch (e: Exception) {
                Log.e(TAG, "Error processing SMS notification", e)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "SmsNotificationListener"
    }
}
