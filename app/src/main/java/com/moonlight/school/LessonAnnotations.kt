package com.moonlight.school

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Personal annotations never participate in imported status, timers, agenda or sync.
 * Session-local by design, with separate DM-only and shared notes. */
internal class LessonAnnotations {
    private val common = mutableStateMapOf<Triple<String, Int, String>, String>()
    private val dm = mutableStateMapOf<Triple<String, Int, String>, String>()
    private fun key(lesson: ImportedLesson) = Triple(lesson.date, lesson.period, lesson.subject)
    fun get(lesson: ImportedLesson, visual: Boolean): String? =
        if (visual) dm[key(lesson)] ?: common[key(lesson)] else common[key(lesson)]
    fun set(lesson: ImportedLesson, text: String, dmOnly: Boolean) {
        val k = key(lesson)
        if (dmOnly) dm[k] = text
        else { common[k] = text; dm.remove(k) }
    }
    fun undo(lesson: ImportedLesson, visual: Boolean) {
        val k = key(lesson)
        if (visual && dm.containsKey(k)) dm.remove(k) else common.remove(k)
    }
    fun clearDm() { dm.clear() }
    fun clearAll() { common.clear(); dm.clear() }
}

internal data class LessonAnnotationAccess(
    val enabled: Boolean, val visual: Boolean, val annotations: LessonAnnotations
)
internal val LocalLessonAnnotations = staticCompositionLocalOf<LessonAnnotationAccess?> { null }

@Composable
internal fun LessonStatusWithAnnotation(lesson: ImportedLesson, phase: LessonClock.Phase, ink: Color, style: TextStyle) {
    val access = LocalLessonAnnotations.current
    val enabled = access?.enabled == true
    val annotation = if (enabled) access?.annotations?.get(lesson, access.visual) else null
    var editing by remember(lesson.date, lesson.period, lesson.subject) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(phase.label), modifier=Modifier.weight(1f, fill=false), style=style, color=ink)
            if (enabled && access != null) {
                val label = tr(if (annotation == null) "Dodaj lokalną notatkę" else "Cofnij lokalną notatkę")
                IconButton(onClick = {
                    if (annotation != null) access.annotations.undo(lesson, access.visual)
                    else editing = true
                }, modifier=Modifier.size(40.dp).semantics { contentDescription=label }) {
                    Canvas(Modifier.size(16.dp)) {
                        val w=size.width; val h=size.height; val stroke=1.8.dp.toPx()
                        fun line(x:Float,y:Float,x2:Float,y2:Float) = drawLine(ink,Offset(x*w,y*h),Offset(x2*w,y2*h),stroke,StrokeCap.Round)
                        if (annotation == null) {
                            line(.20f,.80f,.72f,.28f); line(.28f,.88f,.80f,.36f)
                            line(.20f,.80f,.17f,.92f); line(.17f,.92f,.28f,.88f)
                            line(.72f,.28f,.80f,.36f); line(.66f,.34f,.74f,.42f)
                        } else {
                            line(.12f,.44f,.42f,.14f); line(.12f,.44f,.42f,.44f)
                            line(.12f,.44f,.42f,.74f)
                            line(.42f,.44f,.77f,.44f); line(.77f,.44f,.88f,.57f)
                            line(.88f,.57f,.88f,.80f); line(.88f,.80f,.68f,.91f)
                        }
                    }
                }
            }
        }
        if (annotation != null) {
            Text(tr("Lokalna notatka") + ": " + annotation, style=MaterialTheme.typography.labelSmall, fontWeight=FontWeight.Medium, color=ink)
        }
    }
    if (editing && enabled && access != null) {
        var value by remember { mutableStateOf("") }
        var dmOnly by remember { mutableStateOf(access.visual) }
        AlertDialog(onDismissRequest={ editing=false }, title={Text(tr("Dodaj lokalną notatkę"))},
            text={
                Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text(tr("Status szkoły: ") + tr(phase.label), style=MaterialTheme.typography.bodyMedium)
                    Text(tr("Adnotacje nie zastępują statusu Librus."),style=MaterialTheme.typography.bodySmall)
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
