package com.moonlight.school

import java.time.LocalDateTime
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.Duration
import kotlin.math.ceil

/** Timetable is expressed in Polish school local time, independently of UI language. */
internal object LessonClock {
    private val zone = ZoneId.of("Europe/Warsaw")
    fun now(): LocalDateTime = LocalDateTime.now(zone)

    data class Progress(val fraction: Float, val remainingMinutes: Int)
    private fun window(lesson: ImportedLesson): Pair<LocalTime, LocalTime>? {
        val times = LessonHours.validate(lesson.startTime ?: return null, lesson.endTime ?: return null) ?: return null
        return LocalTime.parse(times.first) to LocalTime.parse(times.second)
    }

    fun progress(lesson: ImportedLesson, now: LocalDateTime): Progress? {
        if (lesson.status == "cancelled" || lesson.date != now.toLocalDate().toString()) return null
        val (start, end) = window(lesson) ?: return null
        val time = now.toLocalTime()
        if (time < start || time >= end) return null
        val total = Duration.between(start, end).toMillis()
        val elapsed = Duration.between(start, time).toMillis()
        val remaining = Duration.between(time, end).toMillis()
        return Progress((elapsed.toDouble() / total).toFloat().coerceIn(0f, 1f),
            ceil(remaining / 60000.0).toInt().coerceAtLeast(1))
    }

    data class Break(val date: String, val start: LocalTime, val end: LocalTime) {
        val minutes: Int get() = Duration.between(start, end).toMinutes().toInt()
        fun progress(now: LocalDateTime): Progress? {
            if (now.toLocalDate().toString() != date) return null
            val time = now.toLocalTime()
            if (time < start || time >= end) return null
            val total = Duration.between(start, end).toMillis()
            val elapsed = Duration.between(start, time).toMillis()
            return Progress((elapsed.toDouble() / total).toFloat().coerceIn(0f, 1f),
                ceil(Duration.between(time, end).toMillis() / 60000.0).toInt().coerceAtLeast(1))
        }
    }

    private val schoolEnd = LocalTime.of(15, 30)
    private val fallbackMinutes = mapOf(
        "08:45" to 5, "09:40" to 10, "10:35" to 15, "11:35" to 10,
        "12:30" to 20, "13:35" to 15, "14:35" to 10
    )

    private fun parseTime(value: String?): LocalTime? =
        value?.let { runCatching { LocalTime.parse(it.trim().padStart(5, '0')) }.getOrNull() }

    /** No break after the last non-cancelled lesson. Imported times take priority. */
    fun breaksAfter(lessons: List<ImportedLesson>): Map<Int, Break> {
        val last = lessons.indexOfLast { it.status != "cancelled" }
        if (last <= 0) return emptyMap()
        return (0 until last).mapNotNull { index ->
            val current = lessons[index]
            val next = lessons[index + 1]
            if (current.date != next.date) return@mapNotNull null
            val end = parseTime(current.endTime) ?: return@mapNotNull null
            if (end >= schoolEnd) return@mapNotNull null
            val nextStart = parseTime(next.startTime)
                ?: if (next.period == current.period + 1) {
                    fallbackMinutes[end.toString()]?.let { end.plusMinutes(it.toLong()) }
                } else null
            val until = nextStart ?: return@mapNotNull null
            if (until <= end || until > schoolEnd) return@mapNotNull null
            index to Break(current.date, end, until)
        }.toMap()
    }

    data class DayEnd(val date: String, val time: LocalTime?)
    fun dayEnd(lessons: List<ImportedLesson>): DayEnd? =
        lessons.lastOrNull { it.status != "cancelled" }?.let { DayEnd(it.date, parseTime(it.endTime)) }

    /** During breaks or cancelled periods, put the clock before the next relevant row. */
    fun markerBeforeIndex(lessons: List<ImportedLesson>, now: LocalDateTime): Int? {
        if (lessons.any { progress(it, now) != null }) return null
        val dated = lessons.mapIndexedNotNull { i, lesson ->
            if (lesson.date != now.toLocalDate().toString()) null
            else window(lesson)?.let { Triple(i, it.first, it.second) }
        }
        if (dated.isEmpty()) return null
        val time = now.toLocalTime()
        if (time < dated.minOf { it.second } || time >= dated.maxOf { it.third }) return null
        return dated.filter { time < it.third }.minByOrNull { it.second }?.first
    }

    /** Drop only the redundant portal boilerplate; retain real notes and source data. */
    private val tripBoilerplate = Regex("^(?:Uwaga:\\s*)?Moduł wycieczek[.!]?$", RegexOption.IGNORE_CASE)
    fun visibleNote(note: String): String = note.lineSequence()
        .map { it.trim() }.filter { it.isNotEmpty() && !tripBoilerplate.matches(it) }.joinToString("\n")
}
