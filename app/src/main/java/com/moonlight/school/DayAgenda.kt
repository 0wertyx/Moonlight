package com.moonlight.school

import java.time.LocalDate

/** Read-only merged view. Calendar entries never change lesson status. */
internal data class DayAgenda(
    val lessons: List<ImportedLesson>,
    val events: List<SchoolEvent>,
    val hasSnapshot: Boolean
) {
    val activeCount: Int get() = lessons.count { it.status != "cancelled" }
    val cancelledCount: Int get() = lessons.size - activeCount
    val summary: String get() {
        if (!hasSnapshot) return "Brak danych"
        val parts = mutableListOf<String>()
        if (activeCount > 0) parts.add("Lekcje: $activeCount")
        if (events.isNotEmpty()) parts.add("Wydarzenia: ${events.size}")
        if (cancelledCount > 0) parts.add("Odwołane: $cancelledCount")
        return if (parts.isEmpty()) "Brak wpisów na ten dzień" else parts.joinToString(" • ")
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
