package com.moonlight.school

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLanguageTest {
    @Test fun labelsAndFallback() {
        assertEquals("Настройки", translate("ru", "Ustawienia"))
        assertEquals("Settings", translate("en", "Ustawienia"))
        assertEquals("Ustawienia", translate("pl", "Ustawienia"))
        assertEquals("school content", translate("ru", "school content"))
    }
}
