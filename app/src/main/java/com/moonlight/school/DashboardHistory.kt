package com.moonlight.school

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

internal data class DashboardEvent(
    val id: String, val kind: String, val title: String, val detail: String,
    val date: String, val detected: Long, val read: Boolean = false
) {
    val isPersonal: Boolean get() = kind in setOf("personal_status", "personal_reset")
    val visibleDetail: String get() = if (kind == "cancel") LessonClock.visibleNote(detail) else detail
    val label: String get() = when (kind) {
        "personal_status" -> "Osobista zmiana statusu"
        "personal_reset" -> "Cofnięto osobistą zmianę"
        "grade" -> "Nowa ocena"
        "cancel" -> "Odwołana lekcja"
        "homework" -> "Nowe zadanie"
        "messages" -> "Nowa wiadomość"
        else -> "Nowe ogłoszenie"
    }
}

internal object DashboardHistory {
    const val KEY = "dashboard_history_v1"
    private const val LIMIT = 60

    fun detect(old: SchoolSnapshot?, next: SchoolSnapshot, now: Long, today: LocalDate,
               grades: Boolean, lessons: Boolean, sections: Boolean): List<DashboardEvent> {
        val result = mutableListOf<DashboardEvent>()
        fun add(kind: String, title: String, detail: String, date: String = "") {
            result.add(DashboardEvent(UUID.randomUUID().toString(), kind, title, detail, date, now))
        }
        if (grades && old != null) {
            // Preserve multiplicity: two identical grades may be legitimate separate grades.
            val counts = old.grades.groupingBy { it }.eachCount().toMutableMap()
            next.grades.forEach { grade ->
                val count = counts[grade] ?: 0
                if (count > 0) counts[grade] = count - 1
                else add("grade", grade.subject, grade.value)
            }
        }
        if (lessons) {
            val previous = old?.lessons.orEmpty().filter { it.status == "cancelled" }
                .map { Triple(it.date, it.period, it.subject) }.toSet()
            next.lessons.filter { it.status == "cancelled" && it.date >= today.toString() }
                .distinctBy { Triple(it.date, it.period, it.subject) }.forEach { lesson ->
                    if (Triple(lesson.date, lesson.period, lesson.subject) !in previous) {
                        val hours = listOfNotNull(lesson.startTime, lesson.endTime).joinToString(" – ")
                        add("cancel", lesson.subject, listOf(hours, lesson.note).filter { it.isNotBlank() }.joinToString("\n"), lesson.date)
                    }
                }
        }
        if (sections && old != null) {
            next.sections.filter { it.key in setOf("homework", "messages", "announcements") }.forEach { section ->
                val previous = old.sections.firstOrNull { it.key == section.key } ?: return@forEach
                fun stable(entry: SchoolEntry) = if(section.key == "messages" && entry.mailPath.isNotBlank()) entry.mailPath
                    else entry.title + "\u0000" + entry.details
                val counts = previous.entries.groupingBy { stable(it) }.eachCount().toMutableMap()
                section.entries.forEach { entry ->
                    val key = stable(entry)
                    val count = counts[key] ?: 0
                    if (count > 0) counts[key] = count - 1
                    else if (section.key != "messages" || previous.entries.all { it.mailPath.isNotBlank() })
                        add(section.key, entry.title, listOf(entry.sender,entry.details).filter { it.isNotBlank() }.joinToString("\n").take(500))
                }
            }
        }
        return result
    }

    fun read(p: SharedPreferences, key: String = KEY): List<DashboardEvent> = runCatching {
        val rows = JSONArray(p.getString(key, "[]") ?: "[]")
        List(rows.length().coerceAtMost(LIMIT)) { i ->
            val row = rows.getJSONObject(i)
            DashboardEvent(row.getString("id"), row.getString("kind"), row.getString("title"),
                row.optString("detail"), row.optString("date"), row.getLong("detected"), row.optBoolean("read"))
        }
    }.getOrDefault(emptyList())

    internal fun write(edit: SharedPreferences.Editor, events: List<DashboardEvent>, key: String = KEY) {
        val rows = JSONArray()
        events.take(LIMIT).forEach { event ->
            rows.put(JSONObject().put("id", event.id).put("kind", event.kind).put("title", event.title)
                .put("detail", event.detail).put("date", event.date).put("detected", event.detected).put("read", event.read))
        }
        edit.putString(key, rows.toString())
    }

    fun markRead(p: SharedPreferences, id: String) {
        val events = read(p)
        if (events.any { it.id == id && !it.read }) {
            val edit = p.edit()
            write(edit, events.map { if (it.id == id) it.copy(read = true) else it })
            edit.apply()
        }
    }

    // Called under SnapshotSync's lock, with the same atomic preference editor as the new cache.
    fun record(p: SharedPreferences, edit: SharedPreferences.Editor, old: SchoolSnapshot?, next: SchoolSnapshot,
               now: Long, grades: Boolean, lessons: Boolean, sections: Boolean) {
        val added = detect(old, next, now, LessonClock.now().toLocalDate(), grades, lessons, sections)
        if (added.isEmpty()) return
        val history = read(p)
        // A period can be evicted and reloaded; do not report the same cancellation twice.
        val unique = added.filter { item -> item.kind != "cancel" || history.none {
            it.kind == "cancel" && it.date == item.date && it.title == item.title && it.detail == item.detail
        } }
        if (unique.isNotEmpty()) write(edit, unique + history)
    }
}
