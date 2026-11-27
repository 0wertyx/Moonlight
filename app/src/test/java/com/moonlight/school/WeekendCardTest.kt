package com.moonlight.school

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class WeekendCardTest {
    @Test fun emptyWeekendOnly() {
        val agenda = DayAgenda(emptyList(), emptyList(), true)
        assertTrue(agenda.isFreeWeekend(LocalDate.parse("2026-11-28")))
        assertTrue(agenda.isFreeWeekend(LocalDate.parse("2026-11-29")))
        assertFalse(agenda.isFreeWeekend(LocalDate.parse("2026-11-30")))
    }
    @Test fun weekendEventsAndLessonsStayVisible() {
        val date = LocalDate.parse("2026-11-28")
        val lesson = ImportedLesson(date.toString(), "Chemia", 1, "scheduled", "08:00", "08:45")
        val event = SchoolEvent(date.toString(), "Event", "")
        assertFalse(DayAgenda(listOf(lesson), emptyList(), true).isFreeWeekend(date))
        assertFalse(DayAgenda(emptyList(), listOf(event), true).isFreeWeekend(date))
    }
}
