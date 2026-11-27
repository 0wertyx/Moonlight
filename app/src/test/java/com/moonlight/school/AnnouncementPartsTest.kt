package com.moonlight.school

import org.junit.Assert.*
import org.junit.Test

class AnnouncementPartsTest {
    @Test fun explicitMetadata() {
        val p=AnnouncementParts.parse("Autor: Jan Kowalski\nData publikacji: 2026-11-27 08:00\nTreść ogłoszenia\nDrugi akapit")
        assertEquals("Jan Kowalski",p.author)
        assertEquals("2026-11-27 08:00",p.date)
        assertEquals("Treść ogłoszenia\nDrugi akapit",p.body)
    }
    @Test fun noInventedAuthorOrDate() {
        val p=AnnouncementParts.parse("Spotkanie 27.11.2026 o 12:00")
        assertTrue(p.author.isEmpty())
        assertTrue(p.date.isEmpty())
        assertEquals("Spotkanie 27.11.2026 o 12:00",p.body)
    }
}
