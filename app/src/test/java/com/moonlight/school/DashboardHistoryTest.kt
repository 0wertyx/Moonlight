package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DashboardHistoryTest {
    private val today=LocalDate.parse("2026-11-27")
    private val grade=ImportedGrade("Chemia","5",1)
    private val lesson=ImportedLesson(today.toString(),"Chemia",1,"scheduled","08:00","08:45")

    @Test fun identicalGradeMultiplicity() {
        val old=SchoolSnapshot(emptyList(),listOf(grade))
        val next=old.copy(grades=listOf(grade,grade))
        val events=DashboardHistory.detect(old,next,1L,today,true,false,false)
        assertEquals(1,events.size)
        assertEquals("5",events.single().detail)
        assertTrue(DashboardHistory.detect(next,next,2L,today,true,false,false).isEmpty())
    }

    @Test fun initialGradesAreBaseline() {
        assertTrue(DashboardHistory.detect(null,SchoolSnapshot(emptyList(),listOf(grade)),1L,today,false,true,false).isEmpty())
    }

    @Test fun onlyNewCurrentOrFutureCancellation() {
        val old=SchoolSnapshot(listOf(lesson),emptyList())
        val next=old.copy(lessons=listOf(lesson.copy(status="cancelled"),lesson.copy(date=today.minusDays(1).toString(),status="cancelled")))
        val events=DashboardHistory.detect(old,next,1L,today,false,true,false)
        assertEquals("cancel",events.single().kind)
        assertEquals("Chemia",events.single().title)
        assertTrue(DashboardHistory.detect(next,next,2L,today,false,true,false).isEmpty())
    }
}
