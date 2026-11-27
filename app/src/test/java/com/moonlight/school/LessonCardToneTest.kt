package com.moonlight.school

import org.junit.Assert.assertEquals
import org.junit.Test

class LessonCardToneTest {
    @Test fun officialCancelledUsesConfiguredCancelledTone() {
        assertEquals(CardTone.Cancelled, lessonCardTone("cancelled", null))
        assertEquals(CardTone.Cancelled, lessonCardTone("cancelled", LocalLessonStatus.Active))
    }
    @Test fun personalAnnotationUsesExistingColorSlot() {
        assertEquals(CardTone.Cancelled, lessonCardTone("scheduled", LocalLessonStatus.Cancelled))
        assertEquals(CardTone.Substitution, lessonCardTone("scheduled", LocalLessonStatus.Substitution))
        assertEquals(CardTone.Lesson, lessonCardTone("scheduled", null))
    }
    @Test fun actualSubstitutionRetainsSourceTone() {
        assertEquals(CardTone.Substitution, lessonCardTone("substitution", LocalLessonStatus.Cancelled))
    }
}
