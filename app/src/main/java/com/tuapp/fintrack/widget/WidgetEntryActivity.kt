package com.tuapp.fintrack.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import androidx.activity.ComponentActivity
import com.tuapp.fintrack.R

/**
 * Minimal activity that opens when the user taps the amount button on the widget.
 * On done, sends a broadcast to WidgetAmountReceiver so the widget can update its state.
 * Uses a broadcast instead of setResult because Glance cannot receive Activity results.
 */
class WidgetEntryActivity : ComponentActivity() {

    private lateinit var amountInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget_entry)

        amountInput = findViewById(R.id.amount_input)

        val initialCents = intent.getLongExtra(EXTRA_AMOUNT_CENTS, 0L)
        if (initialCents > 0L) {
            amountInput.setText("%.2f".format(initialCents / 100.0))
            amountInput.selectAll()
        }

        amountInput.requestFocus()
        amountInput.postDelayed({
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(amountInput, InputMethodManager.SHOW_IMPLICIT)
        }, 100)

        amountInput.setOnEditorActionListener { _, _, _ ->
            commitAmount()
            true
        }

        findViewById<Button>(R.id.done_button).setOnClickListener { commitAmount() }
        findViewById<Button>(R.id.cancel_button).setOnClickListener { finish() }
    }

    private fun commitAmount() {
        val text = amountInput.text.toString().trim()
        val amount = text.toDoubleOrNull() ?: 0.0
        val cents = (amount * 100).toLong()

        sendBroadcast(
            Intent(WidgetAmountReceiver.ACTION_AMOUNT_ENTERED).apply {
                setPackage(packageName)
                putExtra(WidgetAmountReceiver.EXTRA_AMOUNT_CENTS, cents)
            }
        )
        finish()
    }

    companion object {
        const val EXTRA_AMOUNT_CENTS = "amount_cents"
    }
}
