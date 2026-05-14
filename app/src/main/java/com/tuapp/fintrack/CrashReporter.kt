package com.tuapp.fintrack

import android.util.Log

object CrashReporter {
    fun log(exception: Exception, message: String = "") {
        Log.e("FinTrackCrash", message, exception)
    }

    fun logUI(screenName: String, event: String) {
        Log.d("FinTrackEvent", "$screenName: $event")
    }
}
