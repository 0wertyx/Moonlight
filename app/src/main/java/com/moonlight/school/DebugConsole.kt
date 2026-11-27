package com.moonlight.school

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalDateTime

/** This state has no Context, file, account, network or snapshot preference access.
 * All samples are synthetic, transient and displayed only inside the labelled lab. */
internal class DebugLabState {
    val grades = mutableStateListOf(ImportedGrade("DEMO · A", "4", 1))
    val lessons = mutableStateListOf<ImportedLesson>()
    val events = mutableStateListOf<DashboardEvent>()
    val log = mutableStateListOf<String>()
    var marks by mutableStateOf(mapOf(1 to "NB"))
        private set
    var sampleTime by mutableStateOf("07:45")
    val day: LocalDate = LocalDate.of(2030, 1, 7)
    private var serial = 0

    init { reset() }

    fun reset() {
        grades.clear(); grades.add(ImportedGrade("DEMO · A","4",1))
        lessons.clear()
        lessons.add(ImportedLesson(day.toString(),"DEMO · A",1,"scheduled","08:00","08:45",room="T-01"))
        lessons.add(ImportedLesson(day.toString(),"DEMO · B",2,"scheduled","08:55","09:40",room="T-02"))
        marks=mapOf(1 to "NB")
        sampleTime="07:45"
        events.clear(); log.clear(); note("Dane testowe zostały przywrócone")
    }
    fun note(value:String) { log.add(0,value); if(log.size>30) log.removeAt(log.lastIndex) }
    fun addGrade(value:String) {
        grades.add(ImportedGrade("DEMO · A",value,1)); note("Dodano ocenę testową")
    }
    fun changeGrade(value:String) {
        if(grades.isNotEmpty()) { grades[grades.lastIndex]=grades.last().copy(value=value);note("Zmieniono ocenę testową") }
    }
    fun removeGrade() {
        if(grades.isNotEmpty()) {grades.removeAt(grades.lastIndex);note("Usunięto ocenę testową")}
    }
    fun status(period:Int,status:String) {
        val i=lessons.indexOfFirst {it.period==period}
        if(i>=0){lessons[i]=lessons[i].copy(status=status);note("Zmieniono status lekcji testowej")}
    }
    fun addLesson() {
        if(lessons.size>=8)return
        val n=lessons.size+1
        val start=java.time.LocalTime.of(8,0).plusMinutes((n-1)*55L)
        lessons.add(ImportedLesson(day.toString(),"DEMO · "+('A'+n-1),n,"scheduled",start.toString(),start.plusMinutes(45).toString(),room="T-0"+n))
        note("Dodano lekcję testową")
    }
    fun attendance(period:Int,code:String) {
        marks=if(code=="—") marks-period else marks+(period to code)
        note("Zmieniono frekwencję testową")
    }
    fun event(kind:String) {
        val detail=when(kind){"grade"->"5";"cancel"->"08:00 – 08:45";else->"DEMO"}
        events.add(0,DashboardEvent("demo:"+ ++serial,kind,"DEMO · A",detail,if(kind=="cancel") day.toString() else "",System.currentTimeMillis()))
        if(events.size>12)events.removeAt(events.lastIndex)
        note("Dodano zdarzenie testowe")
    }
    fun clearEvents(){events.clear();note("Wyczyszczono zdarzenia testowe")}
    fun snapshot():SchoolSnapshot {
        val rows=marks.toSortedMap().map { (period,code) -> "$period\t$code" }
        val entries=if(rows.isEmpty()) emptyList() else listOf(SchoolEntry(day.toString(),"MLA1\n"+rows.joinToString("\n")))
        return SchoolSnapshot(lessons.toList(),grades.toList(),lessonWeeks=listOf(PortalPeriods.monday(day).toString()),
            sections=listOf(SchoolSection("attendance",entries,0L)))
    }
    fun now():LocalDateTime=day.atTime(java.time.LocalTime.parse(sampleTime))
}

internal data class DebugDiagnostics(val sync:Boolean,val cached:Boolean,val lastSuccess:Long,val version:String)

@Composable
internal fun DebugConsoleSettings(enabled:Boolean,left:Boolean,onEnabled:(Boolean)->Unit,onSide:(Boolean)->Unit,open:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(shape=RoundedCornerShape(20.dp),color=lerp(colors.surface,colors.primary,.10f)) {
        Column(Modifier.fillMaxWidth().padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DashboardIcon("Settings",24.dp,colors.primary)
                Column(Modifier.weight(1f)) {
                    Text("Debug Console",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                    Text(tr("Tryb testowy"),style=MaterialTheme.typography.labelSmall)
                }
                Switch(checked=enabled,onCheckedChange=onEnabled)
            }
            Text(tr("Tylko testy lokalne. Dane Librus nie są zmieniane."),style=MaterialTheme.typography.bodySmall)
            if(enabled) {
                Text(tr("Strona panelu"),style=MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected=left,onClick={onSide(true)},label={Text(tr("Lewa"))})
                    FilterChip(selected=!left,onClick={onSide(false)},label={Text(tr("Prawa"))})
                }
                Button(onClick=open,modifier=Modifier.fillMaxWidth()){Text(tr("Otwórz konsolę"))}
            }
        }
    }
}

@Composable
internal fun DebugConsoleOverlay(open:Boolean,left:Boolean,a:MoonlightAppearance,lab:DebugLabState,diagnostics:DebugDiagnostics,onOpen:()->Unit,onClose:()->Unit,onPreview:()->Unit) {
    val colors=MaterialTheme.colorScheme
    val duration=(260/a.speed).toInt()
    BackHandler(open,onBack=onClose)
    Box(Modifier.fillMaxSize()) {
        if(!open) {
            val edge=if(left) Alignment.CenterStart else Alignment.CenterEnd
            Surface(onClick=onOpen,modifier=Modifier.align(edge).width(38.dp).height(64.dp),
                shape=if(left) RoundedCornerShape(topEnd=17.dp,bottomEnd=17.dp) else RoundedCornerShape(topStart=17.dp,bottomStart=17.dp),
                color=colors.surface.copy(alpha=.97f),contentColor=colors.onSurface,shadowElevation=6.dp) {
                Box(contentAlignment=Alignment.Center) { Text("⌘",style=MaterialTheme.typography.titleLarge,color=colors.primary) }
            }
        }
        AnimatedVisibility(visible=open,enter=if(a.animations) fadeIn(tween(duration)) else EnterTransition.None,
            exit=if(a.animations) fadeOut(tween(duration)) else ExitTransition.None) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.55f)).clickable(onClick=onClose))
        }
        AnimatedVisibility(visible=open,modifier=Modifier.align(if(left) Alignment.CenterStart else Alignment.CenterEnd),
            enter=if(a.animations) slideInHorizontally(tween(duration)){ if(left) -it else it }+fadeIn(tween(duration)) else EnterTransition.None,
            exit=if(a.animations) slideOutHorizontally(tween(duration)){if(left) -it else it}+fadeOut(tween(duration)) else ExitTransition.None) {
            val shape=if(left) RoundedCornerShape(topEnd=26.dp,bottomEnd=26.dp) else RoundedCornerShape(topStart=26.dp,bottomStart=26.dp)
            Surface(modifier=Modifier.fillMaxHeight().fillMaxWidth(.92f).widthIn(max=440.dp).safeDrawingPadding()
                .border(1.dp,colors.primary.copy(alpha=.35f),shape),shape=shape,color=colors.surface.copy(alpha=.98f),
                contentColor=colors.onSurface,shadowElevation=12.dp) {
                Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.primary.copy(alpha=.13f),Color.Transparent))).padding(14.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("MOONLIGHT / LAB",style=MaterialTheme.typography.labelSmall,color=colors.primary)
                            Text("Debug Console",style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)
                        }
                        TextButton(onClick=onClose){Text("×",style=MaterialTheme.typography.headlineSmall)}
                    }
                    Surface(shape=RoundedCornerShape(12.dp),color=colors.errorContainer,contentColor=colors.onErrorContainer) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("DEMO • "+tr("Tryb testowy"),fontWeight=FontWeight.Bold)
                            Text(tr("Tylko testy lokalne. Dane Librus nie są zmieniane."),style=MaterialTheme.typography.labelSmall)
                        }
                    }
                    OutlinedButton(onClick=onPreview,modifier=Modifier.fillMaxWidth().padding(top=5.dp)) { Text(tr("Pełnoekranowy podgląd DEMO")) }
                    Box(Modifier.weight(1f)) { DebugLabContent(lab,a,diagnostics) }
                }
            }
        }
    }
}

@Composable
private fun DebugLabContent(lab:DebugLabState,a:MoonlightAppearance,d:DebugDiagnostics) {
    var tab by remember { mutableStateOf("Podgląd") }
    var grade by remember { mutableStateOf("5") }
    var period by remember { mutableIntStateOf(1) }
    var reset by remember { mutableStateOf(false) }
    val tabs=listOf("Podgląd","Oceny","Plan lekcji","Frekwencja","Ostatnie zdarzenia","Diagnostyka")
    Column(Modifier.fillMaxSize()) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical=5.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        tabs.forEach { name -> FilterChip(selected=tab==name,onClick={tab=name},label={Text(tr(name))}) }
    }
    Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        when(tab) {
            "Podgląd" -> {
                Text(tr("Czas testowy"),style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf("07:45","08:20","08:50","16:00").forEach { t -> FilterChip(selected=lab.sampleTime==t,onClick={lab.sampleTime=t},label={Text(t)}) }
                }
                DashboardHero(lab.snapshot(),lab.now(),true){}
                val snap=lab.snapshot()
                val agenda=DayAgenda.live(lab.now(),snap)
                DashboardStats(agenda.activeCount,agenda.cancelledCount,snap.grades.size,{tab="Plan lekcji"},{tab="Oceny"})
            }
            "Oceny" -> {
                Text(tr("Ocena testowa"),style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                    (1..6).forEach { value -> FilterChip(selected=grade==value.toString(),onClick={grade=value.toString()},label={Text(value.toString())}) }
                }
                Button(onClick={lab.addGrade(grade)},modifier=Modifier.fillMaxWidth(),enabled=lab.grades.size<30){Text(tr("Dodaj ocenę testową"))}
                OutlinedButton(onClick={lab.changeGrade(grade)},modifier=Modifier.fillMaxWidth(),enabled=lab.grades.isNotEmpty()){Text(tr("Zmień ostatnią ocenę"))}
                OutlinedButton(onClick={lab.removeGrade()},modifier=Modifier.fillMaxWidth(),enabled=lab.grades.isNotEmpty()){Text(tr("Usuń ostatnią ocenę"))}
                if(lab.grades.isEmpty())Text(tr("Brak testowych ocen"))
                lab.grades.forEach { item ->
                    Surface(shape=RoundedCornerShape(14.dp),color=MaterialTheme.colorScheme.surfaceVariant) {
                        Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically) {
                            Text(item.subject,Modifier.weight(1f));Text(item.value,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
            "Plan lekcji" -> {
                Text(tr("Lekcja testowa"),style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    lab.lessons.forEach { item -> FilterChip(selected=period==item.period,onClick={period=item.period},label={Text("#"+item.period)}) }
                }
                Button(onClick={lab.status(period,"cancelled")},modifier=Modifier.fillMaxWidth()){Text(tr("Odwołaj lekcję"))}
                OutlinedButton(onClick={lab.status(period,"scheduled")},modifier=Modifier.fillMaxWidth()){Text(tr("Przywróć lekcję"))}
                OutlinedButton(onClick={lab.status(period,"substitution")},modifier=Modifier.fillMaxWidth()){Text(tr("Zastępstwo"))}
                OutlinedButton(onClick={lab.addLesson()},modifier=Modifier.fillMaxWidth(),enabled=lab.lessons.size<8){Text(tr("Dodaj lekcję testową"))}
                lab.lessons.forEach { item -> key(item.period){DashboardLesson(item,lab.now(),a)} }
            }
            "Frekwencja" -> {
                Text(tr("Lekcja testowa"),style=MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    lab.lessons.forEach { item -> FilterChip(selected=period==item.period,onClick={period=item.period},label={Text("#"+item.period)}) }
                }
                Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(5.dp)) {
                    listOf("NB","U","SP","ZW","—").forEach { code -> FilterChip(selected=(lab.marks[period]?:"—")==code,onClick={lab.attendance(period,code)},label={Text(code)}) }
                }
                val days=AttendanceModel.days(lab.snapshot())
                AttendanceOverview(days)
                days.forEach { day -> AttendanceDayCard(day,a,false,{}, {}) }
            }
            "Ostatnie zdarzenia" -> {
                Text(tr("Symuluj zdarzenie"),style=MaterialTheme.typography.titleMedium)
                listOf("grade" to "Nowa ocena","cancel" to "Odwołana lekcja","homework" to "Nowe zadanie","messages" to "Nowa wiadomość","announcements" to "Nowe ogłoszenie").forEach { (kind,label) ->
                    OutlinedButton(onClick={lab.event(kind)},modifier=Modifier.fillMaxWidth()){Text(tr(label))}
                }
                if(lab.events.isEmpty())Text(tr("Brak testowych zdarzeń"))
                else {
                    DashboardRecent(lab.events.toList(),a,{},{id ->
                        val index=lab.events.indexOfFirst{it.id==id}
                        if(index>=0)lab.events[index]=lab.events[index].copy(read=true)
                    }){}
                    OutlinedButton(onClick={lab.clearEvents()},modifier=Modifier.fillMaxWidth()){Text(tr("Wyczyść zdarzenia"))}
                }
            }
            else -> {
                Text("Moonlight "+d.version,style=MaterialTheme.typography.titleMedium)
                Text(tr(if(d.sync) "Synchronizacja włączona" else "Synchronizacja wyłączona"))
                Text(tr(if(d.cached) "Kopia dostępna" else "Brak kopii"))
                if(d.lastSuccess>0)Text(tr("Aktualizacja: ")+java.text.SimpleDateFormat("dd.MM HH:mm",currentUiLocale()).format(java.util.Date(d.lastSuccess)))
                HorizontalDivider()
                Text(tr("Dziennik testów"),style=MaterialTheme.typography.titleMedium)
                lab.log.forEach{Text("› "+tr(it),style=MaterialTheme.typography.bodySmall)}
                TextButton(onClick={lab.log.clear()}){Text(tr("Wyczyść dziennik"))}
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick={reset=true},modifier=Modifier.fillMaxWidth()){Text(tr("Przywróć dane testowe"))}
        Spacer(Modifier.height(10.dp))
    }
    }
    if(reset)AlertDialog(onDismissRequest={reset=false},title={Text(tr("Przywróć dane testowe"))},
        text={Text(tr("Tylko testy lokalne. Dane Librus nie są zmieniane."))},
        confirmButton={TextButton(onClick={lab.reset();period=1;reset=false}){Text("OK")}},
        dismissButton={TextButton(onClick={reset=false}){Text(tr("Anuluj"))}})
}
