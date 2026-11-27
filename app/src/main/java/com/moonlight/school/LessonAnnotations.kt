package com.moonlight.school

import android.content.SharedPreferences
import java.util.Locale
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal enum class LocalLessonStatus(val label: String) {
    Upcoming("Lekcja jeszcze się nie rozpoczęła"),
    Soon("Lekcja zaraz się rozpocznie"),
    Active("Lekcja trwa"),
    Finished("Lekcja zakończona"),
    Cancelled("Odwołane"),
    Substitution("Zastępstwo")
}

/** Subject-level personal notes and visual statuses stored independently from school snapshots.
 * Both scopes survive restart; DM-only notes are visible only in visual mode. */
internal class LessonAnnotations(private val prefs: SharedPreferences) {
    private val common = mutableStateMapOf<String, String>()
    private val dm = mutableStateMapOf<String, String>()
    private val commonPrefix = "subject_note_v1_common:"
    private val dmPrefix = "subject_note_v1_dm:"
    private val statusCommonPrefix = "subject_status_v1_common:"
    private val statusDmPrefix = "subject_status_v1_dm:"
    private val statusCommon = mutableStateMapOf<String, LocalLessonStatus>()
    private val statusDm = mutableStateMapOf<String, LocalLessonStatus>()

    init {
        prefs.all.forEach { (key, value) ->
            if (value is String && value.isNotBlank()) {
                when {
                    key.startsWith(commonPrefix) -> common[key.removePrefix(commonPrefix)] = value
                    key.startsWith(dmPrefix) -> dm[key.removePrefix(dmPrefix)] = value
                    key.startsWith(statusCommonPrefix) -> LocalLessonStatus.entries.firstOrNull { it.name == value }
                        ?.let { statusCommon[key.removePrefix(statusCommonPrefix)] = it }
                    key.startsWith(statusDmPrefix) -> LocalLessonStatus.entries.firstOrNull { it.name == value }
                        ?.let { statusDm[key.removePrefix(statusDmPrefix)] = it }
                }
            }
        }
    }

    private fun key(lesson: ImportedLesson) = lesson.subject.trim().lowercase(Locale.ROOT)
    fun get(lesson: ImportedLesson, visual: Boolean): String? =
        if (visual) dm[key(lesson)] ?: common[key(lesson)] else common[key(lesson)]

    fun set(lesson: ImportedLesson, text: String, dmOnly: Boolean) {
        val k = key(lesson)
        if (k.isBlank() || text.isBlank()) return
        val value = text.trim().take(120)
        val editor = prefs.edit()
        if (dmOnly) {
            dm[k] = value
            editor.putString(dmPrefix + k, value)
        } else {
            common[k] = value
            dm.remove(k)
            editor.putString(commonPrefix + k, value).remove(dmPrefix + k)
        }
        editor.apply()
    }

    fun status(lesson: ImportedLesson, visual: Boolean): LocalLessonStatus? =
        if (visual) statusDm[key(lesson)] ?: statusCommon[key(lesson)] else statusCommon[key(lesson)]

    fun setStatus(lesson: ImportedLesson, status: LocalLessonStatus, dmOnly: Boolean) {
        val k = key(lesson)
        if (k.isBlank()) return
        val editor = prefs.edit()
        if (dmOnly) {
            statusDm[k] = status
            editor.putString(statusDmPrefix + k, status.name)
        } else {
            statusCommon[k] = status
            statusDm.remove(k)
            editor.putString(statusCommonPrefix + k, status.name).remove(statusDmPrefix + k)
        }
        editor.apply()
    }

    fun undoStatus(lesson: ImportedLesson, visual: Boolean) {
        val k = key(lesson)
        if (visual && statusDm.containsKey(k)) {
            statusDm.remove(k)
            prefs.edit().remove(statusDmPrefix + k).apply()
        } else {
            statusCommon.remove(k)
            prefs.edit().remove(statusCommonPrefix + k).apply()
        }
    }

    fun undo(lesson: ImportedLesson, visual: Boolean) {
        val k = key(lesson)
        if (visual && dm.containsKey(k)) {
            dm.remove(k)
            prefs.edit().remove(dmPrefix + k).apply()
        } else {
            common.remove(k)
            prefs.edit().remove(commonPrefix + k).apply()
        }
    }
}

internal data class LessonAnnotationAccess(
    val enabled: Boolean, val visual: Boolean, val annotations: LessonAnnotations
)
internal val LocalLessonAnnotations = staticCompositionLocalOf<LessonAnnotationAccess?> { null }

/** Display-only personal annotation; the imported school lesson is never modified. */
@Composable
internal fun currentLessonAnnotation(lesson: ImportedLesson): LocalLessonStatus? {
    val access = LocalLessonAnnotations.current ?: return null
    return if (access.enabled) access.annotations.status(lesson, access.visual) else null
}

internal fun lessonCardTone(status: String, personal: LocalLessonStatus?): CardTone = when {
    status == "cancelled" -> CardTone.Cancelled
    status == "substitution" -> CardTone.Substitution
    personal == LocalLessonStatus.Cancelled -> CardTone.Cancelled
    personal == LocalLessonStatus.Substitution -> CardTone.Substitution
    else -> CardTone.Lesson
}

@Composable
internal fun LessonStatusWithAnnotation(lesson: ImportedLesson, phase: LessonClock.Phase, ink: Color, style: TextStyle) {
    val access = LocalLessonAnnotations.current
    val enabled = access?.enabled == true
    val annotation = if (enabled) access?.annotations?.get(lesson, access.visual) else null
    val localStatus = currentLessonAnnotation(lesson)
    var editingStatus by remember(lesson.date, lesson.period, lesson.subject) { mutableStateOf(false) }
    var editing by remember(lesson.date, lesson.period, lesson.subject) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f, fill=false)) {
                val schoolCancelled = phase == LessonClock.Phase.Cancelled
                val personal = localStatus?.takeIf { !schoolCancelled && it.label != phase.label }
                val label = if (schoolCancelled) phase.label else personal?.label ?: phase.label
                val cancelled = schoolCancelled || personal == LocalLessonStatus.Cancelled
                if (personal != null) {
                    Text(tr("Osobista adnotacja"), style=MaterialTheme.typography.labelSmall,
                        color=ink, fontWeight=FontWeight.Medium)
                }
                Text(tr(label), style=style, color=ink,
                    textDecoration=if(cancelled) TextDecoration.LineThrough else TextDecoration.None)
                if (schoolCancelled && localStatus != null && localStatus != LocalLessonStatus.Cancelled) {
                    Text(tr("Osobista adnotacja") + ": " + tr(localStatus.label),
                        style=MaterialTheme.typography.labelSmall, color=ink)
                }
            }
            if (enabled && access != null) {
                val label = tr(if (localStatus == null) "Zmień status lokalny" else "Cofnij status lokalny")
                AnnotationAction(ink, localStatus != null, label) {
                    if (localStatus != null) access.annotations.undoStatus(lesson, access.visual)
                    else editingStatus = true
                }
            }
        }
        if (enabled && access != null) {
            Row(verticalAlignment=Alignment.CenterVertically) {
                Text(tr("Lokalna notatka") + (annotation?.let { ": " + it } ?: ""),
                    modifier=Modifier.weight(1f, fill=false),
                    style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Medium, color=ink)
                val label = tr(if (annotation == null) "Dodaj lokalną notatkę" else "Cofnij lokalną notatkę")
                AnnotationAction(ink, annotation != null, label) {
                    if (annotation != null) access.annotations.undo(lesson, access.visual)
                    else editing = true
                }
            }
        }
    }
    if (editingStatus && enabled && access != null) {
        var selected by remember { mutableStateOf<LocalLessonStatus?>(null) }
        var dmOnly by remember { mutableStateOf(access.visual) }
        AlertDialog(
            onDismissRequest = { editingStatus = false },
            title = { Text(tr("Zmień status lokalny")) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(tr("Status szkoły: ") + tr(phase.label), style=MaterialTheme.typography.bodyMedium)
                    Text(tr("Status lokalny dotyczy wszystkich lekcji tego przedmiotu."),
                        style=MaterialTheme.typography.bodySmall)
                    LocalLessonStatus.entries.forEach { option ->
                        FilterChip(selected=selected == option, onClick={ selected=option },
                            label={Text(tr(option.label))})
                    }
                    if (access.visual) {
                        FilterChip(selected=dmOnly,onClick={dmOnly=true},label={Text(tr("Tylko DM"))})
                        FilterChip(selected=!dmOnly,onClick={dmOnly=false},label={Text(tr("W obu trybach"))})
                    }
                }
            },
            confirmButton = {
                TextButton(enabled=selected != null, onClick={
                    selected?.let { access.annotations.setStatus(lesson,it,access.visual && dmOnly) }
                    editingStatus=false
                }) { Text(tr("Zapisz status lokalny")) }
            },
            dismissButton = { TextButton(onClick={editingStatus=false}) { Text(tr("Anuluj")) } }
        )
    }
    if (editing && enabled && access != null) {
        var value by remember { mutableStateOf("") }
        var dmOnly by remember { mutableStateOf(access.visual) }
        AlertDialog(onDismissRequest={ editing=false }, title={Text(tr("Dodaj lokalną notatkę"))},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(tr("Status szkoły: ") + tr(phase.label), style=MaterialTheme.typography.bodyMedium)
                    Text(tr("Adnotacje nie zastępują statusu Librus."),style=MaterialTheme.typography.bodySmall)
                    Text(tr("Notatka dotyczy wszystkich lekcji tego przedmiotu."),style=MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value=value,onValueChange={value=it.filter { c -> !c.isISOControl() }.take(120)},
                        label={Text(tr("Lokalna notatka"))},modifier=Modifier.fillMaxWidth(),singleLine=true)
                    if(access.visual) {
                        FilterChip(selected=dmOnly,onClick={dmOnly=true},label={Text(tr("Tylko DM"))})
                        FilterChip(selected=!dmOnly,onClick={dmOnly=false},label={Text(tr("W obu trybach"))})
                    }
                }
            },
            confirmButton={TextButton(enabled=value.isNotBlank(),onClick={
                access.annotations.set(lesson,value.trim(),access.visual && dmOnly); editing=false
            }){Text(tr("Zapisz"))}},
            dismissButton={TextButton(onClick={editing=false}){Text(tr("Anuluj"))}})
    }
}

@Composable
private fun AnnotationAction(ink: Color, undo: Boolean, label: String, onClick: () -> Unit) {
    IconButton(onClick=onClick, modifier=Modifier.size(40.dp).semantics { contentDescription=label }) {
        Canvas(Modifier.size(16.dp)) {
            val w=size.width; val h=size.height; val stroke=1.8.dp.toPx()
            fun line(x:Float,y:Float,x2:Float,y2:Float) =
                drawLine(ink,Offset(x*w,y*h),Offset(x2*w,y2*h),stroke,StrokeCap.Round)
            if (!undo) {
                line(.20f,.80f,.72f,.28f); line(.28f,.88f,.80f,.36f)
                line(.20f,.80f,.17f,.92f); line(.17f,.92f,.28f,.88f)
                line(.72f,.28f,.80f,.36f); line(.66f,.34f,.74f,.42f)
            } else {
                line(.12f,.44f,.42f,.14f); line(.12f,.44f,.42f,.44f)
                line(.12f,.44f,.42f,.74f); line(.42f,.44f,.77f,.44f)
                line(.77f,.44f,.88f,.57f); line(.88f,.57f,.88f,.80f)
                line(.88f,.80f,.68f,.91f)
            }
        }
    }
}
