package com.ferik.mt5risk

import com.ferik.mt5risk.pass.MessageSeverity
import java.math.BigDecimal
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AppGraphTest {

    @Test
    fun aTapWithoutTheAccessibilityServiceDoesNothingAndTellsTheUser() = runTest {
        assertFalse(AppGraph.isAccessibilityConnected)

        val message = async { AppGraph.messages.first() }
        runCurrent()
        val result = AppGraph.runPass(BigDecimal.ONE)
        runCurrent()

        assertNull("no pass can run without the service", result)
        val shown = message.await()
        assertEquals(MessageSeverity.ERROR, shown.severity)
        assertTrue(shown.text, shown.text.contains("accessibility service"))
    }

    @Test
    fun withoutTheServiceMt5IsNeverReportedAsForeground() {
        assertFalse(AppGraph.isMt5Foreground())
    }
}
