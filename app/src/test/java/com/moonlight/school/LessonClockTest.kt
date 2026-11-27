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
    @Test fun importedBreaksWinOverFallbackAndDoNotExtendPastLastLesson() {
        val second = lesson.copy(period=2,startTime="08:55",endTime="09:40")
        val third = lesson.copy(period=3,status="cancelled",startTime="09:50",endTime="10:35")
        val breaks = LessonClock.breaksAfter(listOf(lesson, second, third))
        assertEquals(setOf(0), breaks.keys)
        assertEquals(10, breaks.getValue(0).minutes)
        assertEquals(0.5f, breaks.getValue(0).progress(at("08:50:00"))!!.fraction, 0.0001f)
        assertNull(breaks.getValue(0).progress(at("08:55:00")))
        assertEquals("09:40", LessonClock.dayEnd(listOf(lesson,second,third))!!.time.toString())
    }
    @Test fun bellFallbackAndFinalSlot() {
        val second = lesson.copy(period=2,startTime=null,endTime="09:40")
        assertEquals(5, LessonClock.breaksAfter(listOf(lesson, second)).getValue(0).minutes)
        val last = lesson.copy(period=8,startTime="14:45",endTime="15:30")
        assertTrue(LessonClock.breaksAfter(listOf(last)).isEmpty())
        assertTrue(LessonClock.breaksAfter(listOf(last, last.copy(period=9,startTime="15:50",endTime="16:35"))).isEmpty())
        assertEquals("15:30", LessonClock.dayEnd(listOf(last))!!.time.toString())
        assertNull(LessonClock.dayEnd(listOf(lesson.copy(status="cancelled"))))
    }
    @Test fun onlyBoilerplateIsRemoved() {
        assertEquals("", LessonClock.visibleNote("Uwaga: Moduł wycieczek."))
        assertEquals("Sala 12", LessonClock.visibleNote("Uwaga: Moduł wycieczek.\nSala 12"))
        assertEquals("Zastępstwo", LessonClock.visibleNote("Zastępstwo"))
    }
}
