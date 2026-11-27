package com.moonlight.school
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class DayAgendaTest {
    private val day = LocalDate.of(2026, 11, 27)
    private val event = SchoolEvent(day.toString(), "Wycieczka", "Wydarzenie szkolne")
    private val lesson = ImportedLesson(day.toString(), "Matematyka", 1, "scheduled")
    @Test fun eventOnlyDay() {
        val agenda = DayAgenda.forDate(day, SchoolSnapshot(emptyList(), emptyList(), "2026-11", listOf(event)))
        assertEquals("Wydarzenia: 1", agenda.summary)
        assertEquals("◆", agenda.dayMarker)
    }
    @Test fun eventDoesNotCancelLessons() {
        val agenda = DayAgenda.forDate(day, SchoolSnapshot(listOf(lesson), emptyList(), "2026-11", listOf(event)))
        assertEquals(1, agenda.activeCount)
        assertEquals("Lekcje: 1 • Wydarzenia: 1", agenda.summary)
        assertEquals(listOf(lesson), agenda.lessons)
    }
    @Test fun cancelledLessonAndEvent() {
        val cancelled = lesson.copy(status = "cancelled")
        val agenda = DayAgenda.forDate(day, SchoolSnapshot(listOf(cancelled), emptyList(), "2026-11", listOf(event)))
        assertEquals("Wydarzenia: 1 • Odwołane: 1", agenda.summary)
        assertEquals(0, agenda.activeCount)
    }
    @Test fun liveAgendaIgnoresSelectedWeekend() {
        val timed = lesson.copy(startTime = "08:00", endTime = "08:45")
        val snapshot = SchoolSnapshot(listOf(timed), emptyList())
        val now = day.atTime(6, 20)
        assertTrue(DayAgenda.forDate(day.plusDays(1), snapshot).lessons.isEmpty())
        assertEquals(timed, LessonClock.nextLesson(DayAgenda.live(now, snapshot).lessons, now)?.lesson)
        assertTrue(DayAgenda.live(now.plusDays(1), snapshot).lessons.isEmpty())
    }
    @Test fun doesNotLeakIntoOtherDays() {
        val agenda = DayAgenda.forDate(day.plusDays(1), SchoolSnapshot(listOf(lesson), emptyList(), "2026-11", listOf(event)))
        assertTrue(agenda.events.isEmpty())
        assertTrue(agenda.lessons.isEmpty())
    }
}
