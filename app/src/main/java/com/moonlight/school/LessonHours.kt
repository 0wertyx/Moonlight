package com.moonlight.school

internal object LessonHours {
    private val clock = Regex("(?:[01][0-9]|2[0-3]):[0-5][0-9]")

    fun validate(start: String, end: String): Pair<String, String>? {
        val a = start.trim().padStart(5, '0')
        val b = end.trim().padStart(5, '0')
        if (!clock.matches(a) || !clock.matches(b) || a >= b) return null
        return a to b
    }
}
