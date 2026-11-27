package com.moonlight.school

import org.junit.Assert.assertEquals
import org.junit.Test

class DashboardEventDetailTest {
    @Test fun hidesTripBoilerplateInCancellationOnly() {
        val detail = "08:00 – 08:45\nUwaga: Moduł wycieczek\nInna informacja"
        val event = DashboardEvent("1", "cancel", "Chemia", detail, "2026-11-27", 0L)
        assertEquals("08:00 – 08:45\nInna informacja", event.visibleDetail)
        assertEquals(detail, event.detail)
        assertEquals(detail, event.copy(kind = "announcements").visibleDetail)
    }
}
