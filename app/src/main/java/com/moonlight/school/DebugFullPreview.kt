package com.moonlight.school

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import java.time.format.DateTimeFormatter

/** Full-screen isolated synthetic preview. Never reads or writes the actual school snapshot. */
@Composable
internal fun DebugFullPreview(lab:DebugLabState,a:MoonlightAppearance,dark:Boolean,badge:String,caption:String,onConsole:()->Unit,onExit:()->Unit) {
    var tab by remember { mutableStateOf("Desk") }
    val snap=lab.snapshot()
    val now=lab.now()
    val agenda=DayAgenda.live(now,snap)
    val colors=MaterialTheme.colorScheme
    BackHandler(onBack=onExit)
    Surface(Modifier.fillMaxSize(),color=colors.background,contentColor=colors.onBackground) {
        Box(Modifier.fillMaxSize()) {
            MoonlightBackdrop(a,dark)
            Column(Modifier.fillMaxSize().statusBarsPadding()) {
                Surface(Modifier.fillMaxWidth(),color=colors.errorContainer,contentColor=colors.onErrorContainer) {
                    Row(Modifier.padding(horizontal=12.dp,vertical=6.dp),verticalAlignment=Alignment.CenterVertically,
                        horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                        val consoleLabel = tr("Otwórz konsolę") + " · " + tr("Dane testowe")
                        Surface(onClick=onConsole,modifier=Modifier.size(44.dp).semantics { contentDescription=consoleLabel },
                            shape=CircleShape,color=colors.surface,contentColor=colors.primary,
                            border=BorderStroke(1.5.dp,colors.primary)) {
                            Box(contentAlignment=Alignment.Center) {
                                Text(badge.ifBlank { "DM" },fontWeight=FontWeight.Bold,style=MaterialTheme.typography.labelMedium,maxLines=1)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            if(caption.isNotBlank()) Text(caption,style=MaterialTheme.typography.titleSmall,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text(tr("Dane testowe"),fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleSmall)
                            Text(tr("Dane fikcyjne — nie jest to dziennik szkoły."),style=MaterialTheme.typography.labelSmall)
                        }
                        TextButton(onClick=onExit){Text(tr("Wyjdź z DEMO"),color=colors.onErrorContainer)}
                    }
                }
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal=12.dp),
                    contentPadding=PaddingValues(vertical=12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    item {
                        Text("DEMO · "+lab.day.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")),style=MaterialTheme.typography.titleMedium)
                    }
                    when(tab) {
                        "Desk" -> {
                            item { DashboardHero(snap,now,true){tab="Schedule"} }
                            if(lab.events.isNotEmpty()) item {
                                DashboardRecent(lab.events.toList(),a,{tab="Feed"},{id->
                                    val i=lab.events.indexOfFirst{it.id==id}
                                    if(i>=0)lab.events[i]=lab.events[i].copy(read=true)
                                }) { event -> tab=when(event.kind){"grade"->"Grades";"cancel"->"Schedule";else->"Feed"} }
                            }
                            item { DashboardStats(agenda.activeCount,agenda.cancelledCount,snap.grades.size,{tab="Schedule"},{tab="Grades"}) }
                            items(agenda.lessons,key={it.period}) { DashboardLesson(it,now,a) }
                        }
                        "Schedule" -> {
                            item { DashboardHero(snap,now,true){} }
                            items(agenda.lessons,key={it.period}) { DashboardLesson(it,now,a) }
                        }
                        "Grades" -> {
                            item { Text("DEMO · "+tr("Oceny"),style=MaterialTheme.typography.headlineSmall) }
                            itemsIndexed(snap.grades) { _,grade ->
                                val bg=lerp(colors.surface,toneBackground(CardTone.Grade),.30f)
                                Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),color=bg,contentColor=readableInk(bg,colors.onBackground)) {
                                    Row(Modifier.padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
                                        Text(grade.subject,Modifier.weight(1f),fontWeight=FontWeight.SemiBold)
                                        Text(grade.value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                                    }
                                }
                            }
                        }
                        "Attendance" -> {
                            val days=AttendanceModel.days(snap)
                            item { AttendanceOverview(days) }
                            items(days) { AttendanceDayCard(it,a,false,{}, {tab="Schedule"}) }
                        }
                        else -> {
                            if(lab.events.isEmpty()) item { Text(tr("Brak testowych zdarzeń")) }
                            items(lab.events.toList(),key={it.id}) { event ->
                                DashboardHistoryRow(event) {
                                    val i=lab.events.indexOfFirst{it.id==event.id}
                                    if(i>=0)lab.events[i]=lab.events[i].copy(read=true)
                                }
                            }
                        }
                    }
                }
                val tabs=listOf(
                    MoonlightTab("Desk","⌂",tr("Biurko")),
                    MoonlightTab("Feed","◉",tr("Nowości")),
                    MoonlightTab("Grades","▤",tr("Oceny")),
                    MoonlightTab("Schedule","▣",tr("Plan")),
                    MoonlightTab("Attendance","✓",tr("Frekwencja")))
                FloatingNavigation(tabs,tab,a){tab=it}
            }
        }
    }
}
