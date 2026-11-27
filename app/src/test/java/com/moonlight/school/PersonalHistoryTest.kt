package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class PersonalHistoryTest {
    @Test fun personalEventsRemainDistinguishable() {
        val event = DashboardEvent("personal:1", "personal_status", "Chemia", "Odwołane", "2026-11-27", 1L)
        assertTrue(event.isPersonal)
        assertEquals("Osobista zmiana statusu", event.label)
        assertTrue(event.copy(kind="personal_reset").isPersonal)
        assertFalse(event.copy(kind="cancel").isPersonal)
        assertNotEquals(event.label, event.copy(kind="cancel").label)
    }
}
