package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class AttendanceModelTest {
    private fun snapshot(entries:List<SchoolEntry>, lessons:List<ImportedLesson>) =
        SchoolSnapshot(lessons,emptyList(),sections=listOf(SchoolSection("attendance",entries,1L)))
    @Test fun matchExactDayAndPeriod() {
        val entries=listOf(SchoolEntry("27.11.2026 (pt.)","MLA1\n2\tnb\n5\tsp"))
        val lessons=listOf(ImportedLesson("2026-11-26","Wrong day",2,"scheduled"),ImportedLesson("2026-11-27","Chemia",2,"scheduled"),ImportedLesson("2026-11-27","Wrong period",4,"scheduled"))
        val marks=AttendanceModel.days(snapshot(entries,lessons)).single().marks
        assertEquals("Chemia",marks[0].lesson?.subject)
        assertEquals(5,marks[1].period)
        assertNull(marks[1].lesson)
    }
    @Test fun legacyNeverGuessesPeriod() {
        val marks=AttendanceModel.days(snapshot(listOf(SchoolEntry("2026-11-27","nb nb")),emptyList())).single().marks
        assertNull(marks.single().period)
        assertNull(marks.single().lesson)
    }
    @Test fun conflictingOrCancelledSubjectsNotGuessed() {
        val entries=listOf(SchoolEntry("2026-11-27","MLA1\n1\tnb\n2\tu"))
        val lessons=listOf(ImportedLesson("2026-11-27","A",1,"scheduled"),ImportedLesson("2026-11-27","B",1,"scheduled"),ImportedLesson("2026-11-27","C",2,"cancelled"))
        assertTrue(AttendanceModel.days(snapshot(entries,lessons)).single().marks.all { it.lesson==null })
    }
}
