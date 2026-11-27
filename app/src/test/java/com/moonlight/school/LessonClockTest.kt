package com.moonlight.school

import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class LessonClockTest {
    private val lesson = ImportedLesson("2026-11-27", "Chemia", 1, "scheduled", "08:00", "08:45")
    private fun at(time: String) = LocalDateTime.parse("2026-11-27T" + time)
    @Test fun exactBoundariesAndProgress() {
        assertNull(LessonClock.progress(lesson, at("07:59:59")))
        assertEquals(0f, LessonClock.progress(lesson, at("08:00:00"))!!.fraction, 0.0001f)
        assertEquals(0.5f, LessonClock.progress(lesson, at("08:22:30"))!!.fraction, 0.0001f)
        assertEquals(23, LessonClock.progress(lesson, at("08:22:30"))!!.remainingMinutes)
        assertNull(LessonClock.progress(lesson, at("08:45:00")))
        assertNull(LessonClock.progress(lesson, at("08:20:00").plusDays(1)))
        assertNull(LessonClock.progress(lesson.copy(status="cancelled"), at("08:20:00")))
        assertNull(LessonClock.progress(lesson.copy(startTime=null), at("08:20:00")))
    }
    @Test fun gapMarkerAndCancelledPeriod() {
        val lessons = listOf(lesson, lesson.copy(period=2,status="cancelled",startTime="08:55",endTime="09:40"),
            lesson.copy(period=3,startTime="09:50",endTime="10:35"))
        assertNull(LessonClock.markerBeforeIndex(lessons, at("08:20:00")))
        assertEquals(1, LessonClock.markerBeforeIndex(lessons, at("08:50:00")))
        assertEquals(1, LessonClock.markerBeforeIndex(lessons, at("09:10:00")))
        assertEquals(2, LessonClock.markerBeforeIndex(lessons, at("09:45:00")))
        assertNull(LessonClock.markerBeforeIndex(lessons, at("10:35:00")))
    }
    @Test fun onlyBoilerplateIsRemoved() {
        assertEquals("", LessonClock.visibleNote("Uwaga: Moduł wycieczek."))
        assertEquals("Sala 12", LessonClock.visibleNote("Uwaga: Moduł wycieczek.\nSala 12"))
        assertEquals("Zastępstwo", LessonClock.visibleNote("Zastępstwo"))
    }
}
