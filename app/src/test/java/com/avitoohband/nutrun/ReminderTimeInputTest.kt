package com.avitoohband.nutrun

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderTimeInputTest {
    @Test
    fun parsesTime() {
        assertEquals(20 * 60 + 5, parseReminderMinute("20:05"))
    }

    @Test
    fun rejectsInvalidTime() {
        assertNull(parseReminderMinute("25:00"))
    }

    @Test
    fun formatsLeadingZeros() {
        assertEquals("08:00", formatReminderMinute(480))
    }
}
