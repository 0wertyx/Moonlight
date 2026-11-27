package com.moonlight.school

internal data class EventText(val teacher: String, val body: String) {
    companion object {
        private val teacherField = Regex(
            """\bNauczyciel\s*:\s*(.+?)(?=\s+(?:Opis|Data dodania|Przedmiot|Sala|Rodzaj|Nr lekcji|Przedział czasu)\s*:|$)""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        fun split(raw: String): EventText {
            val match = teacherField.find(raw) ?: return EventText("", raw.trim())
            val teacher = match.groupValues[1].trim()
            if (teacher.length !in 2..160) return EventText("", raw.trim())
            val body = raw.removeRange(match.range).trim().replace(
                Regex("""\s+(?=(?:Opis|Data dodania|Przedmiot|Sala|Rodzaj|Nr lekcji|Przedział czasu)\s*:)""", RegexOption.IGNORE_CASE), "\n"
            )
            return EventText(teacher, body)
        }
    }
}
