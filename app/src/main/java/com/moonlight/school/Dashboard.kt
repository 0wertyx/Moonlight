package com.moonlight.school

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Composable
internal fun DashboardIcon(name: String, iconSize: Dp, color: Color) {
    Canvas(Modifier.size(iconSize)) {
        val w = size.width
        val h = size.height
        val sw = 1.8.dp.toPx()
        fun pt(x: Float, y: Float) = Offset(w*x, h*y)
        fun line(x: Float,y: Float,x2: Float,y2: Float) = drawLine(color, pt(x,y), pt(x2,y2), sw, StrokeCap.Round)
        fun outline(x: Float,y: Float,x2: Float,y2: Float) = drawRect(color, pt(x,y), Size(w*(x2-x),h*(y2-y)), style=Stroke(sw))
        fun poly(vararg coords: Pair<Float,Float>, close: Boolean = false) {
            val path=Path().apply {
                moveTo(w*coords.first().first,h*coords.first().second)
                coords.drop(1).forEach { (x,y) -> lineTo(w*x,h*y) }
                if (close) close()
            }
            drawPath(path,color,style=Stroke(sw,cap=StrokeCap.Round))
        }
        when(name) {
            "Desk" -> {
                poly(.1f to .46f,.5f to .12f,.9f to .46f)
                poly(.23f to .41f,.23f to .86f,.42f to .86f,.42f to .61f,.58f to .61f,.58f to .86f,.77f to .86f,.77f to .41f)
            }
            "Grades" -> {
                poly(.06f to .37f,.5f to .15f,.94f to .37f,.5f to .59f,close=true)
                poly(.25f to .48f,.25f to .72f,.5f to .83f,.75f to .72f,.75f to .48f)
                line(.94f,.37f,.94f,.72f)
            }
            "Schedule","Lessons" -> {
                outline(.15f,.22f,.85f,.86f)
                line(.32f,.12f,.32f,.32f); line(.68f,.12f,.68f,.32f); line(.15f,.41f,.85f,.41f)
                for(x in listOf(.32f,.5f,.68f)) for(y in listOf(.55f,.7f)) drawCircle(color,sw*.65f,pt(x,y))
            }
            "Messages" -> {
                outline(.12f,.23f,.88f,.77f)
                poly(.12f to .28f,.5f to .55f,.88f to .28f)
            }
            "Feed","Announcements" -> {
                poly(.18f to .7f,.28f to .55f,.28f to .38f,.35f to .22f,.5f to .15f,.65f to .22f,.72f to .38f,.72f to .55f,.82f to .7f,close=true)
                line(.42f,.85f,.58f,.85f)
            }
            "Refresh" -> {
                drawArc(color,45f,255f,false,pt(.17f,.17f),Size(w*.66f,h*.66f),style=Stroke(sw,cap=StrokeCap.Round))
                poly(.72f to .08f,.82f to .28f,.6f to .28f)
            }
            "Attendance" -> { drawCircle(color,w*.36f,pt(.5f,.5f),style=Stroke(sw)); poly(.28f to .5f,.44f to .66f,.72f to .36f) }
            "Homework","Notes" -> {
                outline(.24f,.18f,.76f,.86f); line(.36f,.38f,.64f,.38f); line(.36f,.53f,.64f,.53f); line(.36f,.68f,.58f,.68f)
            }
            "cancel" -> { drawCircle(color,w*.36f,pt(.5f,.5f),style=Stroke(sw)); line(.27f,.27f,.73f,.73f) }
            else -> { drawCircle(color,w*.32f,pt(.5f,.5f),style=Stroke(sw)) }
        }
    }
}

@Composable
private fun dashboardColor(tone: CardTone): Color = lerp(MaterialTheme.colorScheme.surface, toneBackground(tone), .38f)

@Composable
internal fun DashboardHero(date: LocalDate, agenda: DayAgenda, now: LocalDateTime, available: Boolean, open: () -> Unit) {
    val isToday = date == now.toLocalDate()
    val active = if (isToday) agenda.lessons.firstOrNull { LessonClock.phase(it, now) == LessonClock.Phase.Active } else null
    val next = if (isToday) LessonClock.nextLesson(agenda.lessons, now) else null
    val lesson = active ?: next?.lesson ?: if (!isToday) agenda.lessons.firstOrNull { it.status != "cancelled" } else null
    val colors = MaterialTheme.colorScheme
    val bg = dashboardColor(CardTone.Summary)
    Card(onClick=open, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(22.dp),
        colors=CardDefaults.cardColors(containerColor=bg,contentColor=readableInk(bg,colors.onBackground))) {
        Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment=Alignment.CenterVertically) {
                Text(if (active != null) tr("Lekcja trwa") else tr("Następna lekcja: ").trimEnd(':',' '), Modifier.weight(1f), style=MaterialTheme.typography.labelMedium)
                if(isToday) Text(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss")),style=MaterialTheme.typography.labelMedium)
            }
            Text(lesson?.subject ?: if (!available) tr("Nie pobrano danych") else if (agenda.activeCount == 0) tr("Brak lekcji") else tr("Lekcje zakończone"),
                style=MaterialTheme.typography.headlineSmall, fontWeight=FontWeight.Bold)
            if(lesson != null) Text(listOfNotNull(lesson.startTime,lesson.endTime).joinToString(" – ") + if(lesson.room.isNotBlank()) "  ·  "+lesson.room else "", style=MaterialTheme.typography.bodyMedium)
            val progress = active?.let { LessonClock.progress(it,now) }
            if(progress != null) {
                LinearProgressIndicator(progress={progress.fraction},modifier=Modifier.fillMaxWidth().height(4.dp),color=colors.primary)
                Text(tr("Pozostało: ")+progress.remainingMinutes+" "+tr("min"),style=MaterialTheme.typography.labelLarge)
            } else if(next != null) {
                val s = next.seconds
                Text(tr("Do rozpoczęcia: ")+String.format(java.util.Locale.ROOT,"%02d:%02d:%02d",s/3600,s/60%60,s%60),
                    style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            }
        }
    }
}

@Composable
internal fun DashboardStats(active: Int, cancelled: Int, grades: Int?, schedule: () -> Unit, openGrades: () -> Unit) {
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        DashboardStat("Schedule",active.toString(),tr("Lekcje"),Modifier.weight(1f),schedule)
        DashboardStat("cancel",cancelled.toString(),tr("Odwołane"),Modifier.weight(1f),schedule)
        DashboardStat("Grades",grades?.toString() ?: "—",tr("Oceny"),Modifier.weight(1f),openGrades)
    }
}

@Composable
private fun DashboardStat(icon:String,value:String,label:String,modifier:Modifier,open:()->Unit) {
    Card(onClick=open,modifier=modifier,shape=RoundedCornerShape(16.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=.94f))) {
        Column(Modifier.fillMaxWidth().padding(10.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
            DashboardIcon(icon,20.dp,MaterialTheme.colorScheme.primary)
            Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
            Text(label,style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DashboardShortcut(icon:String,label:String,modifier:Modifier,open:()->Unit) {
    Card(onClick=open,modifier=modifier,shape=RoundedCornerShape(15.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface.copy(alpha=.94f))) {
        Column(Modifier.fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            DashboardIcon(icon,22.dp,MaterialTheme.colorScheme.primary)
            Text(label,style=MaterialTheme.typography.labelLarge,maxLines=2,overflow=TextOverflow.Ellipsis)
        }
    }
}

@Composable
internal fun DashboardLesson(lesson:ImportedLesson,now:LocalDateTime,a:MoonlightAppearance) {
    var expanded by rememberSaveable(lesson.date,lesson.period,lesson.subject) { mutableStateOf(false) }
    val tone=when(lesson.status) { "cancelled" -> CardTone.Cancelled; "substitution" -> CardTone.Substitution; else -> CardTone.Lesson }
    val bg=dashboardColor(tone)
    val colors=MaterialTheme.colorScheme
    Card(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp),
        colors=CardDefaults.cardColors(containerColor=bg,contentColor=readableInk(bg,colors.onBackground))) {
        Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Column(Modifier.width(49.dp)) {
                    Text(lesson.startTime ?: "—",style=MaterialTheme.typography.labelLarge)
                    Text(lesson.endTime ?: "—",style=MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.width(3.dp).height(42.dp).background(colors.primary,RoundedCornerShape(2.dp)))
                Column(Modifier.weight(1f)) {
                    Text(lesson.subject,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                    Text(tr(LessonClock.phase(lesson,now).label),style=MaterialTheme.typography.labelSmall)
                }
                Text(if(expanded) "⌃" else "⌄",style=MaterialTheme.typography.titleLarge)
            }
            AnimatedVisibility(expanded,enter=if(a.animations) expandVertically()+fadeIn() else androidx.compose.animation.EnterTransition.None,
                exit=if(a.animations) shrinkVertically()+fadeOut() else androidx.compose.animation.ExitTransition.None) {
                Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
                    HorizontalDivider()
                    if(lesson.teacher.isNotBlank()) Text(lesson.teacher,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    if(lesson.room.isNotBlank()) Text(tr("Sala lekcyjna")+": "+lesson.room,style=MaterialTheme.typography.bodyMedium)
                    if(lesson.note.isNotBlank()) Text(lesson.note,style=MaterialTheme.typography.bodySmall)
                    if(lesson.teacher.isBlank() && lesson.room.isBlank() && lesson.note.isBlank()) Text(tr("Brak informacji"),style=MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun eventTime(event:DashboardEvent):String {
    val locale=uiLocale(LocalUiLanguage.current)
    return java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT,locale)
        .apply { timeZone=java.util.TimeZone.getTimeZone("Europe/Warsaw") }.format(java.util.Date(event.detected))
}

@Composable
private fun eventTone(event:DashboardEvent):CardTone = when(event.kind) {
    "grade" -> CardTone.Grade
    "cancel" -> CardTone.Cancelled
    else -> CardTone.Event
}

@Composable
private fun eventIcon(event:DashboardEvent):String = when(event.kind) {
    "grade" -> "Grades"; "cancel" -> "cancel"; "homework" -> "Homework"; "messages" -> "Messages"; else -> "Announcements"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun DashboardRecent(events:List<DashboardEvent>,a:MoonlightAppearance,all:()->Unit,read:(String)->Unit,open:(DashboardEvent)->Unit) {
    var selected by remember { mutableStateOf<DashboardEvent?>(null) }
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically) {
            Text(tr("Ostatnie zdarzenia"),Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            TextButton(onClick=all) { Text(tr("Wszystkie")+" ›") }
        }
        LazyRow(horizontalArrangement=Arrangement.spacedBy(9.dp)) {
            items(events.take(12),key={it.id}) { event ->
                val bg=dashboardColor(eventTone(event))
                val ink=readableInk(bg,MaterialTheme.colorScheme.onBackground)
                val accent=readableInk(bg,MaterialTheme.colorScheme.primary)
                Card(onClick={read(event.id);selected=event},modifier=Modifier.width(190.dp).height(144.dp).then(if(a.animations) Modifier.animateItem() else Modifier),
                    shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=bg,contentColor=ink)) {
                    Column(Modifier.fillMaxSize().padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                            DashboardIcon(eventIcon(event),18.dp,accent)
                            Text(tr(event.label),Modifier.weight(1f),style=MaterialTheme.typography.labelSmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                            if(!event.read) Box(Modifier.size(6.dp).background(accent,RoundedCornerShape(50)))
                        }
                        if(event.kind=="grade") Text(event.detail,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold,maxLines=1)
                        Text(event.title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,maxLines=2,overflow=TextOverflow.Ellipsis)
                        if(event.kind!="grade") Text(listOf(event.date,event.detail).filter{it.isNotBlank()}.joinToString(" · "),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
                        Spacer(Modifier.weight(1f))
                        Text(eventTime(event),style=MaterialTheme.typography.labelSmall,maxLines=1)
                    }
                }
            }
        }
    }
    selected?.let { event ->
        AlertDialog(onDismissRequest={selected=null},title={Text(tr(event.label))},text={
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text(event.title,style=MaterialTheme.typography.titleLarge)
                if(event.date.isNotBlank()) Text(event.date)
                if(event.detail.isNotBlank()) Text(event.detail)
                Text(tr("Wykryto podczas synchronizacji")+": "+eventTime(event),style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(onClick={selected=null;open(event)}){Text(tr("Otwórz sekcję"))}},
            dismissButton={TextButton(onClick={selected=null}){Text("OK")}})
    }
}

@Composable
internal fun DashboardHistoryRow(event:DashboardEvent,read:()->Unit) {
    var expanded by rememberSaveable(event.id){mutableStateOf(false)}
    Card(onClick={read();expanded=!expanded},modifier=Modifier.fillMaxWidth(),colors=toneColors(eventTone(event))) {
        Column(Modifier.padding(13.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DashboardIcon(eventIcon(event),22.dp,LocalContentColor.current)
                Text(tr(event.label),Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
                if(!event.read) Box(Modifier.size(6.dp).background(LocalContentColor.current,RoundedCornerShape(50)))
            }
            Text(event.title,style=MaterialTheme.typography.titleMedium)
            if(event.date.isNotBlank()) Text(event.date)
            if(event.detail.isNotBlank()) Text(event.detail,maxLines=if(expanded) 20 else 2,overflow=TextOverflow.Ellipsis)
            Text(tr("Wykryto podczas synchronizacji")+": "+eventTime(event),style=MaterialTheme.typography.labelSmall)
        }
    }
}
