package com.example.musicsm.ui.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SupportPromptScheduleTest {

    private val shown = 1_000_000_000_000L

    @Test
    fun `the popup is due two days after it last showed, not before`() {
        assertFalse(SupportPromptSchedule.isDue(shown, shown + SupportPromptSchedule.INTERVAL_MS - 1))
        assertTrue(SupportPromptSchedule.isDue(shown, shown + SupportPromptSchedule.INTERVAL_MS))
    }

    @Test
    fun `the popup waits a random moment within the window`() {
        val random = Random(42)
        val delays = List(500) { SupportPromptSchedule.delayMs(random) }
        assertTrue(delays.all { it in SupportPromptSchedule.MIN_DELAY_MS..SupportPromptSchedule.MAX_DELAY_MS })
        assertTrue("delays should vary", delays.toSet().size > 1)
    }
}
