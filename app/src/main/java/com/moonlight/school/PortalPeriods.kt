package com.moonlight.school

import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek

/** Replace only the verified period, preserving all other cached periods. */
internal object PortalPeriods {
    fun monday(date: LocalDate): LocalDate =
        date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun automaticWeeks(today: LocalDate): List<LocalDate> {
        val current = monday(today)
        return listOf(current, current.minusWeeks(1), current.plusWeeks(1), current.plusWeeks(2))
    }

    fun requestableWeek(week: LocalDate, today: LocalDate): Boolean =
        week == monday(week) && week >= monday(today.minusMonths(2)) &&
            week <= monday(today.plusMonths(3))

    fun hasWeek(snapshot: SchoolSnapshot?, week: LocalDate): Boolean =
        week.toString() in snapshot?.lessonWeeks.orEmpty()

    fun mergeWeek(old: SchoolSnapshot, incoming: SchoolSnapshot): SchoolSnapshot {
        val key = incoming.lessonWeeks.single()
        val first = LocalDate.parse(key)
        require(first.dayOfWeek == DayOfWeek.MONDAY)
        val end = first.plusDays(6).toString()
        require(incoming.lessons.all { it.date in key..end })
        val oldWeeks = old.lessons.map {
            LocalDate.parse(it.date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString()
        }
        // Always retain the requested week even if it is older than the other cached weeks.
        val weeks = ((old.lessonWeeks + oldWeeks).distinct().filterNot { it == key }.sorted().takeLast(23) + key).sorted()
        val lessons = (old.lessons.filterNot { it.date in key..end } + incoming.lessons)
            .filter { LocalDate.parse(it.date).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString() in weeks }
            .sortedWith(compareBy({ it.date }, { it.period }))
        return old.copy(lessons = lessons, lessonWeeks = weeks)
    }
    fun mergeMonth(old: SchoolSnapshot, incoming: SchoolSnapshot): SchoolSnapshot {
        val month = requireNotNull(incoming.calendarMonth)
        require(incoming.events.all { it.date.take(7) == month })
        val months = (old.calendarMonths + listOfNotNull(old.calendarMonth) + month).distinct().sorted().takeLast(4)
        val events = (old.events.filterNot { it.date.take(7) == month } + incoming.events)
            .filter { it.date.take(7) in months }.distinct().sortedBy { it.date }
        return old.copy(calendarMonth = month, calendarMonths = months, events = events)
    }
}
