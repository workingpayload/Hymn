package com.example.musicsm.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeGreetingTest {

    @Test
    fun `greeting changes at five, noon and five in the evening`() {
        assertEquals(DayPart.EVENING, dayPartAt(4))
        assertEquals(DayPart.MORNING, dayPartAt(5))
        assertEquals(DayPart.MORNING, dayPartAt(11))
        assertEquals(DayPart.AFTERNOON, dayPartAt(12))
        assertEquals(DayPart.AFTERNOON, dayPartAt(16))
        assertEquals(DayPart.EVENING, dayPartAt(17))
        assertEquals(DayPart.EVENING, dayPartAt(0))
    }
}
