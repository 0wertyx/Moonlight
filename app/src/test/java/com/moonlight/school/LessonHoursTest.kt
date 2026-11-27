package com.moonlight.school
import org.junit.Assert.*
import org.junit.Test
class LessonHoursTest {
    @Test fun actualHours() { assertEquals("08:00" to "08:45", LessonHours.validate("8:00", "08:45")) }
    @Test fun missingLegacyFields() { assertNull(LessonHours.validate("", "")) }
    @Test fun invalidRange() { assertNull(LessonHours.validate("15:00", "14:00")) }
    @Test fun invalidClock() { assertNull(LessonHours.validate("25:00", "25:45")) }
    @Test fun noGuessingEnd() { assertNull(LessonHours.validate("08:00", "")) }
}
