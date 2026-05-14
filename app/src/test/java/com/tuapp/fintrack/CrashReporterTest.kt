package com.tuapp.fintrack

import org.junit.Test

class CrashReporterTest {

    @Test
    fun `log does not throw for a normal exception`() {
        CrashReporter.log(RuntimeException("test error"), "test message")
    }

    @Test
    fun `log does not throw with empty message`() {
        CrashReporter.log(IllegalStateException("state error"))
    }

    @Test
    fun `logUI does not throw`() {
        CrashReporter.logUI("HomeScreen", "pay_button_clicked")
    }
}
