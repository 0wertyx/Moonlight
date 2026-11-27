package com.moonlight.school

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

internal data class AttendanceMark(val period: Int?, val code: String, val lesson: ImportedLesson?)
internal data class AttendanceDay(val date: LocalDate?, val label: String, val marks: List<AttendanceMark>, val legacy: Boolean, val hasWeek: Boolean)

internal object AttendanceModel {
    private val datePattern = Regex("""^\s*(\d{4}[-.]\d{2}[-.]\d{2}|\d{2}[.-]\d{2}[.-]\d{4})""")
    fun parseDate(text: String): LocalDate? {
        val raw=datePattern.find(text)?.groupValues?.get(1) ?: return null
        val normalized=raw.replace('.', '-')
        return runCatching {
            if(normalized.take(4).all { it.isDigit() }) LocalDate.parse(normalized)
            else LocalDate.parse(normalized, DateTimeFormatter.ofPattern("dd-MM-uuuu"))
        }.getOrNull()
    }

    fun days(snapshot: SchoolSnapshot?): List<AttendanceDay> {
        val entries=snapshot?.sections?.firstOrNull { it.key=="attendance" }?.entries.orEmpty()
        val lessons=snapshot?.lessons.orEmpty().groupBy { it.date to it.period }
        return entries.map { entry ->
            val date=parseDate(entry.title)
            val structured=entry.details.startsWith("MLA1\n")
            val marks=if(structured) entry.details.lineSequence().drop(1).mapNotNull { line ->
                val parts=line.split('\t',limit=2)
                val period=parts.firstOrNull()?.toIntOrNull()?.takeIf { it in 0..20 } ?: return@mapNotNull null
                val code=parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val matches=lessons[date?.toString() to period].orEmpty().filter { it.status!="cancelled" }
                val lesson=matches.distinctBy { it.subject }.singleOrNull()
                AttendanceMark(period,code,lesson)
            }.distinctBy { it.period to it.code }.sortedBy { it.period }.toList()
            else listOf(AttendanceMark(null,entry.details.trim(),null)).filter { it.code.isNotEmpty() }
            AttendanceDay(date,entry.title,marks,!structured,date?.let { PortalPeriods.hasWeek(snapshot,PortalPeriods.monday(it)) }==true)
        }.groupBy { it.date?.toString() ?: it.label }.values.map { group ->
            val first=group.first()
            first.copy(marks=group.flatMap { it.marks }.distinctBy { it.period to it.code }.sortedBy { it.period },legacy=group.any { it.legacy })
        }.sortedByDescending { it.date ?: LocalDate.MIN }
    }

    fun kind(code: String): String = when(code.trim().lowercase(Locale.ROOT)) {
        "nb","nu" -> "absence"
        "u" -> "excused"
        "sp" -> "late"
        "zw" -> "released"
        "ob","o" -> "present"
        else -> "other"
    }
    fun label(code: String): String = when(kind(code)) {
        "absence" -> "Nieobecność"
        "excused" -> "Nieobecność usprawiedliwiona"
        "late" -> "Spóźnienie"
        "released" -> "Zwolnienie"
        "present" -> "Obecność"
        else -> "Wpis"
    }
}
