package com.moonlight.school

import java.time.LocalDateTime
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
