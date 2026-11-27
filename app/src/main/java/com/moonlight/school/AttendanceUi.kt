package com.moonlight.school

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

@Composable
internal fun AttendanceOverview(days: List<AttendanceDay>) {
    val marks=days.flatMap { it.marks }.filter { it.period!=null }
    if (days.any { it.legacy }) {
        Text(tr("Zapis starszego formatu. Odśwież dane."),style=MaterialTheme.typography.bodySmall)
    }
    if (marks.isNotEmpty() || days.none { it.legacy }) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("NB" to marks.count { AttendanceModel.kind(it.code)=="absence" },
            "U" to marks.count { AttendanceModel.kind(it.code)=="excused" },
            "SP" to marks.count { AttendanceModel.kind(it.code)=="late" }).forEach { (label,count) ->
            Surface(Modifier.weight(1f),shape=RoundedCornerShape(16.dp),color=MaterialTheme.colorScheme.surface.copy(alpha=.94f)) {
                Column(Modifier.padding(12.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                    Text(count.toString(),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                    Text(label,style=MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
    Text(tr("Dni z wpisami")+": "+days.size,style=MaterialTheme.typography.bodySmall,modifier=Modifier.padding(top=8.dp))
}

@Composable
internal fun AttendanceDayCard(day: AttendanceDay, appearance: MoonlightAppearance, canLoad: Boolean, load:()->Unit, open:()->Unit) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(7.dp)) {
        val title=day.date?.format(DateTimeFormatter.ofPattern("EEEE, dd.MM.yyyy",uiLocale(LocalUiLanguage.current))) ?: day.label
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(title,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            if(day.date!=null) TextButton(onClick=open) { DashboardIcon("Schedule",20.dp,MaterialTheme.colorScheme.primary) }
        }
        day.marks.forEach { mark ->
            AttendanceMarkCard(mark,appearance)
        }
        if(day.legacy) Text(tr("Zapis starszego formatu. Odśwież dane."),style=MaterialTheme.typography.bodySmall)
        if(canLoad && day.marks.any { it.period!=null && it.lesson==null }) {
            TextButton(onClick=load) { Text(tr("Pobierz plan tygodnia")) }
        }
    }
}

@Composable
private fun AttendanceMarkCard(mark:AttendanceMark,a:MoonlightAppearance) {
    var expanded by rememberSaveable(mark.period,mark.code) { mutableStateOf(false) }
    val colors=MaterialTheme.colorScheme
    val kind=AttendanceModel.kind(mark.code)
    val tone=when(kind) { "absence" -> CardTone.Cancelled; "late" -> CardTone.Substitution; "excused","released","present" -> CardTone.EndOfDay; else -> CardTone.Event }
    val bg=lerp(colors.surface,toneBackground(tone),.35f)
    val ink=readableInk(bg,colors.onBackground)
    val accent=readableInk(bg,when(kind) {
        "absence" -> Color(0xFFFF8495)
        "late" -> Color(0xFFFFC16C)
        "excused","released","present" -> Color(0xFF8AD9AD)
        else -> colors.primary
    })
    Card(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(17.dp),colors=CardDefaults.cardColors(containerColor=bg,contentColor=ink)) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(11.dp)) {
                Box(Modifier.width(3.dp).height(43.dp).background(accent,RoundedCornerShape(3.dp)))
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Text(mark.lesson?.subject ?: tr("Przedmiot nieznany"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(listOfNotNull(mark.period?.let { tr("Lekcja nr")+" "+it },mark.lesson?.startTime?.let { start -> listOfNotNull(start,mark.lesson.endTime).joinToString(" – ") }).joinToString("  ·  ").ifBlank { tr("Numer lekcji nieznany") },style=MaterialTheme.typography.labelMedium)
                }
                Text(mark.code.uppercase(uiLocale(LocalUiLanguage.current)).take(12),color=accent,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            }
            AnimatedVisibility(expanded,enter=if(a.animations) expandVertically()+fadeIn() else androidx.compose.animation.EnterTransition.None,
                exit=if(a.animations) shrinkVertically()+fadeOut() else androidx.compose.animation.ExitTransition.None) {
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    HorizontalDivider()
                    Text(tr(AttendanceModel.label(mark.code)),style=MaterialTheme.typography.bodyMedium)
                    mark.lesson?.let { lesson ->
                        if(lesson.teacher.isNotBlank()) Text(lesson.teacher,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                        if(lesson.room.isNotBlank()) Text(tr("Sala lekcyjna")+": "+lesson.room)
                    }
                    if(mark.lesson==null && mark.period!=null) Text(tr("Plan nie zawiera tej lekcji"),style=MaterialTheme.typography.bodySmall)
                    if(mark.period==null) Text(mark.code,style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
