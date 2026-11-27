package com.moonlight.school

import java.time.LocalDate
import java.time.DayOfWeek

/** Read-only merged view. Calendar entries never change lesson status. */
internal data class DayAgenda(
    val lessons: List<ImportedLesson>,
    val events: List<SchoolEvent>,
    val hasSnapshot: Boolean
) {
    fun isFreeWeekend(date: LocalDate): Boolean =
        date.dayOfWeek in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) && lessons.isEmpty() && events.isEmpty()

    val activeCount: Int get() = lessons.count { it.status != "cancelled" }
    val cancelledCount: Int get() = lessons.size - activeCount
    val summary: String get() = localizedSummary("pl")
    fun localizedSummary(language: String): String {
        if (!hasSnapshot) return translate(language, "Brak danych")
        val parts = mutableListOf<String>()
        if (activeCount > 0) parts.add(translate(language, "Lekcje: ") + activeCount)
        if (events.isNotEmpty()) parts.add(translate(language, "Wydarzenia: ") + events.size)
        if (cancelledCount > 0) parts.add(if (activeCount == 0 && events.isEmpty()) translate(language, "Wszystkie lekcje odwołane (") + cancelledCount + ")" else translate(language, "Odwołane: ") + cancelledCount)
        return if (parts.isEmpty()) translate(language, "Brak wpisów na ten dzień") else parts.joinToString(" • ")
    }
    val dayMarker: String get() = when {
        activeCount > 0 && events.isNotEmpty() -> "$activeCount •"
        events.isNotEmpty() -> "◆"
        lessons.isNotEmpty() -> activeCount.toString()
        else -> "—"
    }
    companion object {
        fun forDate(date: LocalDate, snapshot: SchoolSnapshot?): DayAgenda {
            val key = date.toString()
            return DayAgenda(
                snapshot?.lessons.orEmpty().filter { it.date == key }.sortedBy { it.period },
                snapshot?.events.orEmpty().filter { it.date == key }.distinct(),
                snapshot != null
            )
        }
    }
}
