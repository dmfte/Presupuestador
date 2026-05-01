package com.tuapp.fintrack.data.db

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FinTrackDatabaseCallback(private val context: Context) : RoomDatabase.Callback() {

    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        seedHolidays(db)
        seedDefaultPayCycles(db)
    }

    private fun seedHolidays(db: SupportSQLiteDatabase) {
        val json = context.assets.open("holidays_sv_default.json").bufferedReader().use { it.readText() }
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val array = JSONArray(json)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val label = obj.getString("label")
            val dateMs = sdf.parse(obj.getString("dateOfYear"))?.time ?: continue
            val recurring = if (obj.getBoolean("recurringYearly")) 1 else 0
            val enabled = if (obj.getBoolean("enabled")) 1 else 0
            db.execSQL(
                "INSERT INTO holidays (label, dateOfYear, recurringYearly, enabled, createdAt) VALUES (?, ?, ?, ?, ?)",
                arrayOf(label, dateMs, recurring, enabled, now)
            )
        }
    }

    private fun seedDefaultPayCycles(db: SupportSQLiteDatabase) {
        val now = System.currentTimeMillis()
        db.execSQL(
            "INSERT INTO pay_cycles (rule, rollBackOnWeekend, rollBackOnHoliday, active, createdAt) VALUES (?, 1, 1, 1, ?)",
            arrayOf("""{"type":"DayOfMonthRule","day":15}""", now)
        )
        db.execSQL(
            "INSERT INTO pay_cycles (rule, rollBackOnWeekend, rollBackOnHoliday, active, createdAt) VALUES (?, 1, 1, 1, ?)",
            arrayOf("""{"type":"LastDayOfMonthRule"}""", now)
        )
    }
}
