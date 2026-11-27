package com.moonlight.school

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import java.util.Locale
import org.json.JSONArray

data class Grade(val value: Int, val weight: Int)
private val polish = Locale.forLanguageTag("pl-PL")
private val dateFormat = DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", polish)

private enum class Module(val title: String, val symbol: String) {
    Desk("Biurko", "▦"), Feed("Aktualności", "⌂"), Grades("Oceny", "▤"),
    Schedule("Plan lekcji", "▣"), Messages("Wiadomości", "✉"),
    Attendance("Frekwencja", "✓"), Homework("Zadania domowe", "✎"),
    Notes("Uwagi", "≡"), Announcements("Ogłoszenia", "◉"),
    Lessons("Lekcje", "▧"), Reminders("Przypomnienia", "◷"),
    School("Informacje o szkole", "ⓘ"), Lucky("Szczęśliwy numerek", "#"),
    Goals("Moje cele", "◎"), Calculator("Kalkulator ocen", "Σ"),
    Connection("Źródło danych", "↗"), Settings("Ustawienia", "⚙")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { MoonlightConfig.personal(this) }
            var dark by remember { mutableStateOf(prefs.getBoolean("dark", false)) }
            var appearance by remember { mutableStateOf(MoonlightAppearance.load(prefs)) }
            var language by remember { mutableStateOf(prefs.getString("ui_language", "pl").orEmpty().takeIf { it in listOf("pl","ru","en") } ?: "pl") }
            val colors = appearance.scheme(dark)
            MaterialTheme(colorScheme = colors) {
                androidx.compose.runtime.CompositionLocalProvider(LocalAppearance provides appearance, LocalUiLanguage provides language, LocalContentColor provides colors.onBackground) {
                    Box(Modifier.fillMaxSize()) {
                        MoonlightBackdrop(appearance, dark)
                        SchoolApp(this@MainActivity, dark, appearance, language, { language = it; prefs.edit().putString("ui_language", it).apply() }, { next ->
                            appearance = next
                            next.save(prefs)
                        }) { dark = it; prefs.edit().putBoolean("dark", it).apply() }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SchoolApp(context: Context, dark: Boolean, appearance: MoonlightAppearance, language: String, onLanguage: (String) -> Unit, onAppearance: (MoonlightAppearance) -> Unit, onDark: (Boolean) -> Unit) {
    val locale = uiLocale(language)
    val dateFormat = remember(language) { DateTimeFormatter.ofPattern("EEE, d MMMM yyyy", locale) }
    var module by remember { mutableStateOf(Module.Desk) }
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val store = remember { SnapshotSync.preferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val state = remember(revision) { SnapshotSync.state(context) }
    var date by remember { mutableStateOf(LessonClock.now().toLocalDate()) }
    val selectedWeek = PortalPeriods.monday(date)
    val missingWeek = !PortalPeriods.hasWeek(state.snapshot, selectedWeek)
    LaunchedEffect(module, selectedWeek, missingWeek, state.portalEnabled) {
        if ((module == Module.Schedule || module == Module.Desk) && missingWeek && state.portalEnabled) {
            delay(400L)
            PortalAutoSync.loadMissingWeek(context, selectedWeek)
        }
    }
    val clock = produceState(initialValue = LessonClock.now(), module) {
        value = LessonClock.now()
        if (module == Module.Desk || module == Module.Schedule) {
            while (true) {
                delay(1000L)
                value = LessonClock.now()
            }
        }
    }
    val primary = listOf(Module.Desk, Module.Feed, Module.Grades, Module.Schedule, Module.Messages)
    fun select(m: Module) { module = m; scope.launch { drawer.close() } }
    fun chooseDate() {
        DatePickerDialog(context, { _, y, m, d -> date = LocalDate.of(y, m + 1, d) },
            date.year, date.monthValue - 1, date.dayOfMonth).show()
    }
    BackHandler(drawer.isOpen || module != Module.Desk) {
        if (drawer.isOpen) scope.launch { drawer.close() } else module = Module.Desk
    }
    ModalNavigationDrawer(drawerState = drawer, drawerContent = {
        ModalDrawerSheet {
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                item {
                    Text("☾ Moonlight", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(12.dp))
                    Text(tr("Niezależny planer szkolny"), modifier = Modifier.padding(horizontal = 12.dp))
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Text(tr("Dziennik ucznia"), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(12.dp))
                }
                items(Module.entries.filter { it != Module.Settings && it != Module.Connection }) { m ->
                    NavigationDrawerItem(label = { Text(tr(m.title)) }, icon = { MoonlightIcon(m.name, m.symbol, 32.dp) }, selected = module == m, onClick = { select(m) })
                }
                item { HorizontalDivider(); Text(tr("Aplikacja"), modifier = Modifier.padding(12.dp)) }
                items(listOf(Module.Connection, Module.Settings)) { m ->
                    NavigationDrawerItem(label = { Text(tr(m.title)) }, icon = { MoonlightIcon(m.name, m.symbol, 32.dp) }, selected = module == m, onClick = { select(m) })
                }
            }
        }
    }) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Column {
                    TopAppBar(
                        title = { Column { Text(tr(module.title)); Text(tr("Moonlight • planer niezależny"), style = MaterialTheme.typography.labelSmall) } },
                        navigationIcon = { IconButton(onClick = { scope.launch { drawer.open() } }) { Text("☰", style = MaterialTheme.typography.headlineSmall) } },
                        actions = { IconButton(onClick = { if (state.connected || state.portalEnabled) SnapshotSync.refresh(context, if (module == Module.Schedule) date.toString() else null) else select(Module.Connection) }) { MoonlightIcon("Refresh", "↻", 32.dp) } }
                    )
                    if (state.snapshot == null && state.portalEnabled) {
                        Text(tr("Pobieranie danych…"), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            bottomBar = {
                if (appearance.pill) {
                    FloatingNavigation(primary.map { m ->
                        MoonlightTab(m.name, m.symbol, when(m) {
                            Module.Schedule -> tr("Plan")
                            Module.Feed -> tr("Nowości")
                            Module.Messages -> tr("Poczta")
                            else -> tr(m.title)
                        })
                    }, module.name, appearance) { key ->
                        primary.firstOrNull { it.name == key }?.let { select(it) }
                    }
                } else {
                    NavigationBar {
                        primary.forEach { m ->
                            NavigationBarItem(selected = module == m, onClick = { select(m) },
                                icon = { MoonlightIcon(m.name, m.symbol, 30.dp) },
                                label = { Text(when(m) { Module.Schedule -> tr("Plan"); Module.Feed -> tr("Nowości"); Module.Messages -> tr("Poczta"); else -> tr(m.title) }, maxLines = 1) })
                        }
                    }
                }
            }
        ) { padding ->
            val selectedAgenda = DayAgenda.forDate(date, state.snapshot)
            if (module == Module.Schedule && selectedAgenda.isFreeWeekend(date)) {
                val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CacheTimestamp(state.lessonsUpdated)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        WeekArrow(false) { date = date.minusWeeks(1) }
                        TextButton(onClick = { chooseDate() }, colors = TextButtonDefaultsForBackdrop()) {
                            Text(monday.format(DateTimeFormatter.ofPattern("dd.MM")) + " – " +
                                monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                        }
                        WeekArrow(true) { date = date.plusWeeks(1) }
                    }
                    WeekDayPicker((0L..6L).map { monday.plusDays(it) }, date, state.snapshot) { date = it }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { date = LessonClock.now().toLocalDate() }, colors = TextButtonDefaultsForBackdrop()) { Text(tr("Dzisiaj")) }
                        if (state.portalEnabled && monday.toString() !in state.snapshot?.lessonWeeks.orEmpty()) {
                            TextButton(onClick = { PortalAutoSync.loadMissingWeek(context, date) }, colors = TextButtonDefaultsForBackdrop()) { Text(tr("Pobierz tydzień")) }
                        }
                    }
                    WeekendCard(date.format(dateFormat), appearance, Modifier.fillMaxWidth().weight(1f))
                }
            } else LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (module) {
                    Module.Desk -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = date == LessonClock.now().toLocalDate(), onClick = { date = LessonClock.now().toLocalDate() }, label = { Text(tr("Dzisiaj")) })
                                FilterChip(selected = date == LessonClock.now().toLocalDate().plusDays(1), onClick = { date = LessonClock.now().toLocalDate().plusDays(1) }, label = { Text(tr("Jutro")) })
                                FilterChip(selected = date != LessonClock.now().toLocalDate() && date != LessonClock.now().toLocalDate().plusDays(1), onClick = { chooseDate() }, label = { Text(tr("Data")) })
                            }
                            Text(date.format(dateFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        val agenda = DayAgenda.forDate(date, state.snapshot)
                        item { SectionCard(tr("Plan dnia"), agenda.localizedSummary(language), { select(Module.Schedule) }, if (agenda.activeCount == 0 && agenda.cancelledCount > 0) CardTone.CancelledDay else CardTone.Summary) }
                        items(agenda.events) { CalendarEventRow(it) }
                        timetableRows(agenda.lessons, clock)
                        item { SectionCard(tr("Oceny"), if (state.snapshot == null) tr("Brak źródła danych") else tr("Liczba ocen: ") + state.snapshot.grades.size, { select(Module.Grades) }, CardTone.Grade) }
                        items(listOf(Module.Notes, Module.Attendance, Module.Homework, Module.Lessons, Module.Messages, Module.Announcements)) { m ->
                            SectionCard(tr(m.title), tr("Brak połączenia z dziennikiem"), { select(m) })
                        }
                    }
                    Module.Schedule -> {
                        val monday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        val days = (0L..6L).map { monday.plusDays(it) }
                        val agenda = DayAgenda.forDate(date, state.snapshot)
                        val lessons = agenda.lessons
                        item {
                            CacheTimestamp(state.lessonsUpdated)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                WeekArrow(false) { date = date.minusWeeks(1) }
                                TextButton(onClick = { chooseDate() }, colors = TextButtonDefaultsForBackdrop()) {
                                    Text(monday.format(DateTimeFormatter.ofPattern("dd.MM")) + " – " +
                                        monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                                }
                                WeekArrow(true) { date = date.plusWeeks(1) }
                            }
                            WeekDayPicker(days, date, state.snapshot) { date = it }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { date = LessonClock.now().toLocalDate() }, colors = TextButtonDefaultsForBackdrop()) { Text(tr("Dzisiaj")) }
                                if (state.portalEnabled && monday.toString() !in state.snapshot?.lessonWeeks.orEmpty()) {
                                    TextButton(onClick = { PortalAutoSync.loadMissingWeek(context, date) }, colors = TextButtonDefaultsForBackdrop()) { Text(tr("Pobierz tydzień")) }
                                }
                            }
                        }
                        if (missingWeek && state.portalEnabled) item {
                            Text(tr(if (PortalPeriods.requestableWeek(selectedWeek, LessonClock.now().toLocalDate()))
                                "Tydzień nie jest jeszcze zapisany. Pobieranie automatyczne lub ponów przyciskiem."
                                else "Tydzień poza zakresem automatycznego pobierania."),
                                style = MaterialTheme.typography.bodySmall)
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), colors = toneColors(if (agenda.activeCount == 0 && agenda.cancelledCount > 0) CardTone.CancelledDay else CardTone.Summary)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(date.format(dateFormat).replaceFirstChar { it.titlecase(locale) },
                                        style = MaterialTheme.typography.titleLarge)
                                    Text(if (missingWeek && state.portalEnabled && agenda.lessons.isEmpty()) tr("Brak pobranego planu tygodnia") else agenda.localizedSummary(language))
                                    if (agenda.activeCount > 0) {
                                        val times = lessons.filter { it.status != "cancelled" }
                                        val first = times.firstOrNull()?.startTime
                                        val last = times.lastOrNull()?.endTime
                                        if (first != null && last != null) Text("$first – $last", style = MaterialTheme.typography.bodyMedium)
                                    }
                                }
                            }
                        }
                        if (state.snapshot == null) item {
                            OutlinedButton(onClick = { select(Module.Connection) }) { Text(tr("Źródło danych")) }
                        }
                        items(agenda.events) { CalendarEventRow(it) }
                        timetableRows(lessons, clock)
                    }
                    Module.Grades -> {
                        item { CacheTimestamp(state.gradesUpdated) }
                        item { OutlinedButton(onClick = { select(Module.Calculator) }) { Text(tr("Kalkulator średniej")) } }
                        val grouped = state.snapshot?.grades.orEmpty().groupBy { it.subject }
                        if (grouped.isEmpty()) item { EmptySection(state.snapshot == null) { select(Module.Connection) } }
                        else items(grouped.entries.toList()) { (subject, rows) ->
                            Card(Modifier.fillMaxWidth(), colors = toneColors(CardTone.Grade)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(subject, style = MaterialTheme.typography.titleMedium)
                                    rows.forEach { Text("${it.value}" + (it.weight?.let { w -> "   •   " + tr("waga") + " $w" } ?: "")) }
                                }
                            }
                        }
                        item { Text(tr("Wszystkie dostępne oceny"), style = MaterialTheme.typography.bodySmall) }
                    }
                    Module.Connection -> item { LibrusPanel(context) }
                    Module.Calculator -> item { PersonalCalculator(context) }
                    Module.Settings -> item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(tr("Język"), style = MaterialTheme.typography.titleMedium)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("pl" to "Polski", "ru" to "Русский", "en" to "English").forEach { (code, label) ->
                                FilterChip(selected = language == code, onClick = { onLanguage(code) }, label = { Text(label) })
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(tr("Ciemny motyw")); Switch(checked = dark, onCheckedChange = onDark)
                        }
                        AppearanceSettings(appearance, dark, onAppearance)
                        HorizontalDivider()
                        Text("Moonlight " + BuildConfig.VERSION_NAME)
                        Text(tr("Format ustawień: ") + MoonlightConfig.version(context))
                        Text(tr("Aktualizacje APK zachowują ustawienia i lokalne dane przy tej samej podpisanej aplikacji."), style = MaterialTheme.typography.bodySmall)
                        Text(tr("Osobny planer. Nie jest aplikacją Librus i nie zmienia szkolnych danych."))
                        OutlinedButton(onClick = { select(Module.Connection) }) { Text(tr("Zarządzaj źródłem danych")) }
                        }
                    }
                    Module.Announcements, Module.Attendance, Module.Homework, Module.Lucky, Module.Messages -> {
                        val sectionKey = when(module) {
                            Module.Announcements -> "announcements"
                            Module.Attendance -> "attendance"
                            Module.Homework -> "homework"
                            Module.Lucky -> "lucky"
                            else -> "messages"
                        }
                        val section = state.snapshot?.sections?.firstOrNull { it.key == sectionKey }
                        item {
                            if (section != null) CacheTimestamp(section.updated)
                            Text(if (section == null) tr("Nie pobrano danych") else if (section.entries.isEmpty()) tr("Brak wpisów") else tr(PortalSections.title(sectionKey)))
                        }
                        items(section?.entries.orEmpty()) { entry ->
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(entry.title, style = MaterialTheme.typography.titleMedium)
                                    if (entry.details.isNotBlank()) Text(entry.details, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                        if (module == Module.Homework) item { PersonalList(context, module) }
                    }
                    Module.Reminders, Module.Goals -> item { PersonalList(context, module) }
                    Module.Feed -> {
                        item { SectionCard(tr("Źródło danych"), if (state.lastSuccess > 0L) tr("Ostatni odczyt: ") + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.DEFAULT, java.text.DateFormat.DEFAULT, locale).format(java.util.Date(state.lastSuccess)) else tr("Brak odczytu"), { select(Module.Connection) }) }
                        val announcements = state.snapshot?.sections?.firstOrNull { it.key == "announcements" }
                        item { SectionCard(tr("Ogłoszenia"), if (announcements == null) tr("Nie pobrano danych") else tr("Liczba wpisów: ") + announcements.entries.size, { select(Module.Announcements) }) }
                    }
                    else -> item {
                        Text(tr("Ta sekcja nie jest jeszcze synchronizowana."), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun TextButtonDefaultsForBackdrop(): ButtonColors = ButtonDefaults.textButtonColors(contentColor=MaterialTheme.colorScheme.onBackground)

@Composable
private fun CacheTimestamp(updated: Long) {
    Text(if (updated > 0L) tr("Aktualizacja: ") + java.text.SimpleDateFormat("dd.MM, HH:mm", currentUiLocale()).format(java.util.Date(updated))
        else tr("Nie zapisano jeszcze danych"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionCard(title: String, subtitle: String, open: () -> Unit, tone: CardTone? = null) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth(), colors = if (tone != null) toneColors(tone) else CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun EmptySection(disconnected: Boolean, connect: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (disconnected) tr("Brak połączenia z dziennikiem") else tr("Brak danych"), style = MaterialTheme.typography.titleMedium)
            if (disconnected) OutlinedButton(onClick = connect) { Text(tr("Źródło danych")) }
        }
    }
}

@Composable
private fun WeekDayPicker(days: List<LocalDate>, selected: LocalDate, snapshot: SchoolSnapshot?, onSelect: (LocalDate) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        days.forEach { day ->
            val agenda = DayAgenda.forDate(day, snapshot)
            val active = day == selected
            val tone = when {
                agenda.activeCount == 0 && agenda.cancelledCount > 0 -> CardTone.CancelledDay
                active -> CardTone.SelectedDay
                agenda.events.isNotEmpty() -> CardTone.Event
                agenda.isFreeWeekend(day) -> CardTone.Weekend
                else -> CardTone.Day
            }
            val background = toneBackground(tone)
            val ink = toneInk(background)
            Card(
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f),
                border = if (active) androidx.compose.foundation.BorderStroke(2.dp, ink) else null,
                colors = CardDefaults.cardColors(containerColor = background, contentColor = ink)
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(day.format(DateTimeFormatter.ofPattern("EE", currentUiLocale())).trimEnd('.'), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    Text(day.dayOfMonth.toString(), style = MaterialTheme.typography.titleMedium)
                    Text(agenda.dayMarker, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun CalendarEventRow(event: SchoolEvent) {
    Card(Modifier.fillMaxWidth(), colors = toneColors(CardTone.Event)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(tr("Wydarzenie • Terminarz"), style = MaterialTheme.typography.labelMedium)
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            val detail = remember(event.details) { EventText.split(event.details) }
            if (detail.teacher.isNotBlank()) {
                Text(tr("Nauczyciel"), style = MaterialTheme.typography.labelSmall)
                Text(detail.teacher, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            if (detail.body.isNotBlank() && detail.body != event.title)
                Text(detail.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LessonRow(lesson: ImportedLesson, now: LocalDateTime) {
    val tone = when (lesson.status) {
        "cancelled" -> CardTone.Cancelled
        "substitution" -> CardTone.Substitution
        else -> CardTone.Lesson
    }
    val background = toneBackground(tone)
    val detailInk = readableInk(background, MaterialTheme.colorScheme.primary)
    val appearance = LocalAppearance.current
    val progress = LessonClock.progress(lesson, now)
    var expanded by rememberSaveable(lesson.date, lesson.period, lesson.subject) { mutableStateOf(false) }
    val duration = remember(lesson.startTime, lesson.endTime) {
        val times = LessonHours.validate(lesson.startTime.orEmpty(), lesson.endTime.orEmpty())
        times?.let { java.time.Duration.between(java.time.LocalTime.parse(it.first), java.time.LocalTime.parse(it.second)).toMinutes() }
    }
    val animationDuration = (260 / appearance.speed).toInt()
    val arrowRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (appearance.animations) tween(animationDuration) else snap(),
        label = "lesson details arrow"
    )
    val expansionDescription = tr(if (expanded) "Szczegóły rozwinięte" else "Szczegóły zwinięte")
    val pulse = if (progress != null && appearance.animations) {
        val transition = rememberInfiniteTransition(label = "current lesson")
        transition.animateFloat(
            initialValue = 1f, targetValue = 1.018f,
            animationSpec = infiniteRepeatable(
                tween((1400 / appearance.speed).toInt(), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "lesson scale"
        )
    } else null
    Card(onClick = { expanded = !expanded },
        modifier = Modifier.fillMaxWidth().graphicsLayer {
            val factor = pulse?.value ?: 1f
            scaleX = factor
            scaleY = if (expanded) 1f + (factor - 1f) * .35f else factor
        }.semantics { stateDescription = expansionDescription },
        colors = CardDefaults.cardColors(containerColor=background, contentColor=toneInk(background))) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(74.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.period.toString(), style = MaterialTheme.typography.titleLarge, color = detailInk)
                if (lesson.startTime != null && lesson.endTime != null) {
                    Text(lesson.startTime, style = MaterialTheme.typography.labelMedium)
                    Text(lesson.endTime, style = MaterialTheme.typography.labelMedium)
                } else Text(tr("Brak godzin"), style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (lesson.status != "scheduled") Text(
                    if (lesson.status == "cancelled") tr("Odwołane") else tr("Zastępstwo"),
                    style = MaterialTheme.typography.bodySmall, color = detailInk)
            }
            androidx.compose.foundation.Canvas(Modifier.size(22.dp).graphicsLayer { rotationZ = arrowRotation }) {
                val left = androidx.compose.ui.geometry.Offset(size.width * .20f, size.height * .36f)
                val center = androidx.compose.ui.geometry.Offset(size.width * .50f, size.height * .66f)
                val right = androidx.compose.ui.geometry.Offset(size.width * .80f, size.height * .36f)
                drawLine(detailInk, left, center, 2.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
                drawLine(detailInk, center, right, 2.5.dp.toPx(), androidx.compose.ui.graphics.StrokeCap.Round)
            }
        }
        AnimatedVisibility(
            visible = expanded,
            enter = if (appearance.animations) expandVertically(animationSpec = tween(animationDuration), expandFrom = Alignment.Top) +
                fadeIn(animationSpec = tween(animationDuration)) else EnterTransition.None,
            exit = if (appearance.animations) shrinkVertically(animationSpec = tween(animationDuration), shrinkTowards = Alignment.Top) +
                fadeOut(animationSpec = tween(animationDuration)) else ExitTransition.None
        ) {
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                HorizontalDivider(color = toneInk(background).copy(alpha = .18f))
                Text(tr("Szczegóły lekcji"), style = MaterialTheme.typography.labelLarge, color = detailInk)
                LessonDetail(tr("Nauczyciel"), lesson.teacher.ifBlank { tr("Brak informacji") }, true)
                LessonDetail(tr("Sala lekcyjna"), lesson.room.ifBlank { tr("Brak informacji") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        LessonDetail(tr("Status lekcji"), tr(when (lesson.status) {
                            "cancelled" -> "Odwołane"
                            "substitution" -> "Zastępstwo"
                            else -> "Zgodnie z planem"
                        }))
                    }
                    if (duration != null) Column(Modifier.weight(1f)) {
                        LessonDetail(tr("Czas trwania"), duration.toString() + " " + tr("min"))
                    }
                }
                val note = LessonClock.visibleNote(lesson.note)
                if (note.isNotBlank()) LessonDetail(tr("Informacje dodatkowe"), note)
            }
        }
        if (progress != null) {
            val color = timeLineColor()
            Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelSmall)
                    Text(tr("Pozostało: ") + progress.remainingMinutes + " " + tr("min"),
                        style = MaterialTheme.typography.labelSmall)
                }
                Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp))
                    .background(toneInk(background).copy(alpha = .18f))) {
                    if (progress.fraction > 0f) Box(Modifier.fillMaxWidth(progress.fraction).fillMaxHeight().background(color))
                }
            }
        }
    }
}

@Composable
private fun LessonDetail(label: String, value: String, emphasized: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = LocalContentColor.current.copy(alpha = .8f))
        Text(value, style = if (emphasized) MaterialTheme.typography.titleLarge else MaterialTheme.typography.bodyLarge,
            fontWeight = if (emphasized) FontWeight.SemiBold else FontWeight.Normal)
    }
}

private fun LazyListScope.timetableRows(lessons: List<ImportedLesson>, clock: State<LocalDateTime>) {
    val breaks = LessonClock.breaksAfter(lessons)
    val end = LessonClock.dayEnd(lessons)
    itemsIndexed(lessons, key = { index, lesson -> "lesson:${lesson.date}:${lesson.period}:$index" }) { index, lesson ->
        val now = clock.value
        val activeBreak = breaks.values.any { it.progress(now) != null }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!activeBreak && LessonClock.markerBeforeIndex(lessons, now) == index) CurrentTimeLine(now)
            LessonRow(lesson, now)
            breaks[index]?.let { BreakRow(it, now) }
        }
    }
    if (end != null) item { DayEndRow(end, clock.value) }
}

@Composable
private fun BreakRow(interval: LessonClock.Break, now: LocalDateTime) {
    val appearance = LocalAppearance.current
    val progress = interval.progress(now)
    val background = toneBackground(CardTone.Break)
    val ink = toneInk(background)
    val motion = if (progress != null && appearance.animations) {
        val transition = rememberInfiniteTransition(label = "break")
        transition.animateFloat(
            initialValue = -3f, targetValue = 3f,
            animationSpec = infiniteRepeatable(
                tween((1500 / appearance.speed).toInt(), easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ), label = "break float"
        )
    } else null
    // The reserved padding is larger than the translation, so adjacent lessons never overlap.
    Box(Modifier.fillMaxWidth().padding(vertical = 5.dp, horizontal = 10.dp)) {
        Card(Modifier.fillMaxWidth().graphicsLayer {
            translationY = (motion?.value ?: 0f).dp.toPx()
        }, colors = CardDefaults.cardColors(containerColor = background, contentColor = ink),
            border = if (progress != null) androidx.compose.foundation.BorderStroke(1.dp, readableInk(background, timeLineColor())) else null) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("Przerwa"), style = MaterialTheme.typography.titleMedium)
                    Text(interval.minutes.toString() + " " + tr("min"), style = MaterialTheme.typography.labelLarge)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(interval.start.toString() + " – " + interval.end.toString(), style = MaterialTheme.typography.bodySmall)
                    if (progress != null) Text(tr("Pozostało: ") + progress.remainingMinutes + " " + tr("min"),
                        style = MaterialTheme.typography.labelMedium)
                }
                if (progress != null) {
                    val barColor = timeLineColor()
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(3.dp))
                        .background(ink.copy(alpha = .18f))) {
                        if (progress.fraction > 0f) Box(Modifier.fillMaxWidth(progress.fraction).fillMaxHeight().background(barColor))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayEndRow(end: LessonClock.DayEnd, now: LocalDateTime) {
    val finished = end.date < now.toLocalDate().toString() ||
        (end.date == now.toLocalDate().toString() && end.time?.let { now.toLocalTime() >= it } == true)
    Card(Modifier.fillMaxWidth(), colors = toneColors(CardTone.EndOfDay)) {
        Column(Modifier.fillMaxWidth().padding(vertical = 26.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr(if (finished) "Lekcje zakończone" else "Koniec lekcji"),
                style = MaterialTheme.typography.headlineSmall)
            end.time?.let { Text(it.toString(), style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun WeekendCard(dateLabel: String, appearance: MoonlightAppearance, modifier: Modifier = Modifier) {
    Card(modifier, colors = toneColors(CardTone.Weekend), shape = RoundedCornerShape(28.dp)) {
        Box(Modifier.fillMaxSize().padding(18.dp)) {
            Column(Modifier.align(Alignment.Center).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("☾", style = MaterialTheme.typography.displaySmall)
                Text(dateLabel, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
                Text(appearance.weekendTitle.ifBlank { tr("Dzień wolny") },
                    style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(appearance.weekendMessage.ifBlank { tr("Odpocznij, aby wrócić do szkoły w dobrej formie.") },
                    style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
                    maxLines = 7, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun CurrentTimeLine(now: LocalDateTime) {
    val color = timeLineColor()
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp)) {
            Text(now.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
        }
        Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(color))
    }
}

@Composable
private fun PersonalCalculator(context: Context) {
    val prefs = remember { MoonlightConfig.personal(context) }
    val grades = remember { mutableStateListOf<Grade>().apply {
        runCatching {
            val arr = JSONArray(prefs.getString("personal_grades", "[]"))
            for (i in 0 until arr.length()) {
                val row = arr.getJSONArray(i)
                add(Grade(row.getInt(0), row.getInt(1)))
            }
        }
    } }
    fun save() { val arr = JSONArray(); grades.forEach { arr.put(JSONArray().put(it.value).put(it.weight)) }; prefs.edit().putString("personal_grades", arr.toString()).apply() }
    var value by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("Prywatny kalkulator • nie są to oceny szkolne"))
        val total = grades.sumOf { it.weight }
        Text(if (total == 0) tr("Średnia: —") else String.format(currentUiLocale(), tr("Średnia: %.2f"), grades.sumOf { it.value * it.weight }.toDouble() / total), style = MaterialTheme.typography.headlineMedium)
        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text(tr("Ocena 1–6")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text(tr("Waga 1–20")) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        if (error) Text(tr("Sprawdź ocenę i wagę."), color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            val v = value.toIntOrNull(); val w = weight.toIntOrNull()
            if (v != null && w != null && v in 1..6 && w in 1..20) { grades.add(Grade(v,w)); save(); value = ""; error = false } else error = true
        }) { Text(tr("Dodaj")) }
        grades.forEachIndexed { index, g ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("${g.value}  •  " + tr("waga") + " ${g.weight}")
                TextButton(onClick = { grades.removeAt(index); save() }) { Text(tr("Usuń")) }
            }
        }
    }
}

@Composable
private fun PersonalList(context: Context, module: Module) {
    val prefs = remember { MoonlightConfig.personal(context) }
    val key = "personal_" + module.name
    val entries = remember(module) { mutableStateListOf<String>().apply {
        runCatching { val arr = JSONArray(prefs.getString(key, "[]")); for (i in 0 until arr.length()) add(arr.getString(i)) }
    } }
    fun save() { val arr = JSONArray(); entries.forEach { arr.put(it) }; prefs.edit().putString(key, arr.toString()).apply() }
    var input by remember(module) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(tr("Prywatna lista • nie jest synchronizowana ze szkołą"))
        OutlinedTextField(value = input, onValueChange = { input = it.take(500) }, label = { Text(tr("Nowy wpis")) }, modifier = Modifier.fillMaxWidth())
        Button(enabled = input.isNotBlank() && entries.size < 200, onClick = { entries.add(input.trim()); input = ""; save() }) { Text(tr("Dodaj")) }
        entries.forEachIndexed { i, value ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(value, Modifier.weight(1f))
                TextButton(onClick = { entries.removeAt(i); save() }) { Text(tr("Usuń")) }
            }
        }
    }
}
