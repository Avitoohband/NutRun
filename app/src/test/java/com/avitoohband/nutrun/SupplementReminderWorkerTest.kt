package com.avitoohband.nutrun

import com.avitoohband.nutrun.reminders.isSupplementDeliveryDateValid
import com.avitoohband.nutrun.reminders.supplementDeliveryId
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SupplementReminderWorkerTest {
    @Test
    fun deliveryIdentityIncludesAccountDateAndMinute() {
        assertEquals(
            "user:SUPPLEMENT:480:2026-08-10",
            supplementDeliveryId("user", LocalDate.of(2026, 8, 10), 480)
        )
    }

    @Test
    fun deliveryDateCannotCrossMidnight() {
        assertFalse(
            isSupplementDeliveryDateValid(
                intended = LocalDate.of(2026, 8, 10),
                current = LocalDate.of(2026, 8, 11)
            )
        )
    }
}
