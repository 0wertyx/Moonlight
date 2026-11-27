package com.moonlight.school
import org.junit.Assert.*
import org.junit.Test

class PortalPeriodsTest {
    @Test fun automaticRangeIncludesPreviousWeek() {
        val today = java.time.LocalDate.parse("2026-11-27")
        assertEquals(listOf("2026-11-23", "2026-11-16", "2026-11-30", "2026-12-07"),
            PortalPeriods.automaticWeeks(today).map { it.toString() })
        assertTrue(PortalPeriods.requestableWeek(java.time.LocalDate.parse("2026-11-16"), today))
    }

    @Test fun olderRequestedWeekIsNotImmediatelyEvicted() {
        val base = java.time.LocalDate.parse("2026-11-23")
        val cached = (0L..23L).map { base.plusWeeks(it).toString() }
        val requested = "2026-11-16"
        val row = ImportedLesson(requested, "Historia", 1, "scheduled")
        val merged = PortalPeriods.mergeWeek(SchoolSnapshot(emptyList(), emptyList(), lessonWeeks=cached),
            SchoolSnapshot(listOf(row), emptyList(), lessonWeeks=listOf(requested)))
        assertEquals(24, merged.lessonWeeks.size)
        assertTrue(requested in merged.lessonWeeks)
        assertEquals(listOf(row), merged.lessons)
    }

    @Test fun nextWeekKeepsCurrentWeek() {
        val a = ImportedLesson("2026-11-23", "Matematyka", 1, "scheduled")
        val b = ImportedLesson("2026-11-30", "Historia", 2, "scheduled")
        val old = SchoolSnapshot(listOf(a), emptyList())
        val merged = PortalPeriods.mergeWeek(old, SchoolSnapshot(listOf(b), emptyList(), lessonWeeks = listOf("2026-11-30")))
        assertEquals(listOf(a,b), merged.lessons)
        assertEquals(listOf("2026-11-23","2026-11-30"), merged.lessonWeeks)
    }
    @Test fun emptyWeekReplacesOnlyItsOwnLessons() {
        val a = ImportedLesson("2026-11-23", "Matematyka", 1, "scheduled")
        val b = ImportedLesson("2026-11-30", "Historia", 2, "scheduled")
        val merged = PortalPeriods.mergeWeek(SchoolSnapshot(listOf(a,b), emptyList()),
            SchoolSnapshot(emptyList(), emptyList(), lessonWeeks = listOf("2026-11-30")))
        assertEquals(listOf(a), merged.lessons)
    }
    @Test fun calendarKeepsPreviousMonth() {
        val a = SchoolEvent("2026-11-27","Event A","")
        val b = SchoolEvent("2026-12-02","Event B","")
        val merged = PortalPeriods.mergeMonth(SchoolSnapshot(emptyList(),emptyList(),"2026-11",listOf(a)),
            SchoolSnapshot(emptyList(),emptyList(),"2026-12",listOf(b)))
        assertEquals(listOf(a,b), merged.events)
        assertEquals(listOf("2026-11","2026-12"), merged.calendarMonths)
    }
}
