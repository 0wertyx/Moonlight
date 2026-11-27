package com.moonlight.school
import org.junit.Assert.*
import org.junit.Test
class EventTextTest {
    @Test fun separatesTeacher() {
        val e = EventText.split("Nauczyciel: Anna Kowalska Opis: Sprawdzian Data dodania: 2026-11-23")
        assertEquals("Anna Kowalska", e.teacher)
        assertEquals("Opis: Sprawdzian\nData dodania: 2026-11-23", e.body)
    }
    @Test fun noTeacherDoesNotInvent() {
        assertEquals(EventText("", "Wycieczka"), EventText.split("Wycieczka"))
    }
}
