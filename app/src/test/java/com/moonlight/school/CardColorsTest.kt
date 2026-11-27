package com.moonlight.school

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class CardColorsTest {
    @Test fun fallbackInkIsReadable() {
        listOf(Color.White, Color.Black, Color(0xFF435A9E), Color(0xFFF7CF9A), Color(0xFF777777)).forEach { background ->
            assertTrue(contrastRatio(background, readableInk(background, background)) >= 4.5f)
        }
    }
    @Test fun preferredInkIsKeptWhenReadable() {
        assertEquals(Color(0xFFF0EDF8), readableInk(Color.Black, Color(0xFFF0EDF8)))
    }
    @Test fun separateCardKeys() {
        assertEquals(CardTone.entries.size, CardTone.entries.map { it.prefKey }.distinct().size)
    }
}
