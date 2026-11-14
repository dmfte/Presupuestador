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
import com.tuapp.fintrack.data.repository.FinTrackRepository
import com.tuapp.fintrack.data.settings.SettingsRepository
import com.tuapp.fintrack.domain.util.Money
import com.tuapp.fintrack.ui.common.labelRes
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
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Recently processed notifications. Messaging apps re-post the same notification (thread
     * updates, mark-as-read, reboots); without this each re-post would log a duplicate transaction.
     */
    private val recentKeys = object : LinkedHashMap<String, Unit>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?) = size > MAX_RECENT_KEYS
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(this) ?: return
        if (sbn.packageName != defaultSmsPackage) return
        // Group summaries repeat the text of a child notification.
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val entryPoint = EntryPointAccessors.fromApplication(
            applicationContext,
            ListenerEntryPoint::class.java
        )

        scope.launch {
            try {
                val settings = entryPoint.settingsRepository()
                if (!settings.smsAutoEnabled.first()) return@launch

                val rules = settings.smsRules.first()
                if (rules.none { it.enabled }) return@launch

                val extras = sbn.notification.extras
                val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
                    ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
                    ?: return@launch

                val dedupeKey = "${sbn.key}|${sbn.notification.`when`}|${text.hashCode()}"
                val firstSeen = synchronized(recentKeys) { recentKeys.put(dedupeKey, Unit) == null }
                if (!firstSeen) return@launch

                val (rule, parsed) = SmsRuleMatcher.firstMatch(text, rules) ?: return@launch

                val type = rule.type
                val categoryId = rule.categoryId
                val description = rule.descriptionTemplate.ifBlank { getString(R.string.sms_default_description) }

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

                val amountStr = Money.formatUsd(parsed.amountCents)
                val typeName = getString(type.labelRes())
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(
                        applicationContext,
                        getString(R.string.sms_transaction_recorded, typeName, amountStr),
                        Toast.LENGTH_SHORT
                    ).show()
                }

                Log.d(TAG, "Auto-created transaction: ${parsed.amountCents} cents ($type) via rule ${rule.id}")
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
        private const val MAX_RECENT_KEYS = 50
    }
}
