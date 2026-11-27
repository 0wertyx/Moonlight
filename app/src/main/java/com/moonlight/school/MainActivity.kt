package com.moonlight.school

import android.app.DatePickerDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
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
    var date by remember { mutableStateOf(LocalDate.now()) }
    var showCancelled by remember(date) { mutableStateOf(false) }
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
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp),
                contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (module) {
                    Module.Desk -> {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = date == LocalDate.now(), onClick = { date = LocalDate.now() }, label = { Text(tr("Dzisiaj")) })
                                FilterChip(selected = date == LocalDate.now().plusDays(1), onClick = { date = LocalDate.now().plusDays(1) }, label = { Text(tr("Jutro")) })
                                FilterChip(selected = date != LocalDate.now() && date != LocalDate.now().plusDays(1), onClick = { chooseDate() }, label = { Text(tr("Data")) })
                            }
                            Text(date.format(dateFormat), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 8.dp))
                        }
                        val agenda = DayAgenda.forDate(date, state.snapshot)
                        item { SectionCard(tr("Plan dnia"), agenda.localizedSummary(language), { select(Module.Schedule) }) }
                        items(agenda.events) { CalendarEventRow(it) }
                        items(agenda.lessons.filter { it.status != "cancelled" }) { LessonRow(it) }
                        if (agenda.cancelledCount > 0) item { TextButton(onClick = { select(Module.Schedule) }) { Text(tr("Odwołane: ") + agenda.cancelledCount) } }
                        item { SectionCard(tr("Oceny"), if (state.snapshot == null) tr("Brak źródła danych") else tr("Liczba ocen: ") + state.snapshot.grades.size, { select(Module.Grades) }) }
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
                                TextButton(onClick = { chooseDate() }) {
                                    Text(monday.format(DateTimeFormatter.ofPattern("dd.MM")) + " – " +
                                        monday.plusDays(6).format(DateTimeFormatter.ofPattern("dd.MM.yyyy")))
                                }
                                WeekArrow(true) { date = date.plusWeeks(1) }
                            }
                            WeekDayPicker(days, date, state.snapshot) { date = it }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                TextButton(onClick = { date = LocalDate.now() }) { Text(tr("Dzisiaj")) }
                                if (state.portalEnabled && monday.toString() !in state.snapshot?.lessonWeeks.orEmpty()) {
                                    TextButton(onClick = { SnapshotSync.refresh(context, date.toString()) }) { Text(tr("Pobierz tydzień")) }
                                }
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(date.format(dateFormat).replaceFirstChar { it.titlecase(locale) },
                                        style = MaterialTheme.typography.titleLarge)
                                    Text(agenda.localizedSummary(language))
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
                        items(lessons.filter { it.status != "cancelled" }) { LessonRow(it) }
                        if (agenda.cancelledCount > 0) {
                            item {
                                TextButton(onClick = { showCancelled = !showCancelled }) {
                                    Text((if (showCancelled) tr("Ukryj") else tr("Pokaż")) + tr(" odwołane lekcje (") + agenda.cancelledCount + ")")
                                }
                                val notes = lessons.filter { it.status == "cancelled" }.map { it.note }.filter { it.isNotBlank() }.distinct()
                                notes.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
                                if (agenda.activeCount == 0 && agenda.events.isEmpty()) {
                                    TextButton(onClick = { select(Module.Announcements) }) { Text(tr("Sprawdź ogłoszenia szkolne")) }
                                }
                            }
                            if (showCancelled) items(lessons.filter { it.status == "cancelled" }) { LessonRow(it) }
                        }
                    }
                    Module.Grades -> {
                        item { CacheTimestamp(state.gradesUpdated) }
                        item { OutlinedButton(onClick = { select(Module.Calculator) }) { Text(tr("Kalkulator średniej")) } }
                        val grouped = state.snapshot?.grades.orEmpty().groupBy { it.subject }
                        if (grouped.isEmpty()) item { EmptySection(state.snapshot == null) { select(Module.Connection) } }
                        else items(grouped.entries.toList()) { (subject, rows) ->
                            Card(Modifier.fillMaxWidth()) {
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
                            OutlinedButton(onClick = {
                                context.startActivity(android.content.Intent(context, PortalActivity::class.java).putExtra("section", sectionKey))
                            }) { Text(tr("Otwórz w Librus")) }
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
                        Text(tr("Otwórz odpowiednią zakładkę w portalu Librus."))
                        OutlinedButton(onClick = { context.startActivity(android.content.Intent(context, PortalActivity::class.java)) }) {
                            Text(tr("Otwórz Librus"))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CacheTimestamp(updated: Long) {
    Text(if (updated > 0L) tr("Aktualizacja: ") + java.text.SimpleDateFormat("dd.MM, HH:mm", currentUiLocale()).format(java.util.Date(updated))
        else tr("Nie zapisano jeszcze danych"), style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun SectionCard(title: String, subtitle: String, open: () -> Unit) {
    Card(onClick = open, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Card(
                onClick = { onSelect(day) },
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(tr("Wydarzenie • Terminarz"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(event.title, style = MaterialTheme.typography.titleMedium)
            val detail = remember(event.details) { EventText.split(event.details) }
            if (detail.teacher.isNotBlank()) {
                Text(tr("Nauczyciel"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail.teacher, style = MaterialTheme.typography.titleLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            }
            if (detail.body.isNotBlank() && detail.body != event.title)
                Text(detail.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LessonRow(lesson: ImportedLesson) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.width(74.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(lesson.period.toString(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                if (lesson.startTime != null && lesson.endTime != null) {
                    Text(lesson.startTime, style = MaterialTheme.typography.labelMedium)
                    Text(lesson.endTime, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Text(tr("Brak godzin"), style = MaterialTheme.typography.labelSmall)
            }
            Column(Modifier.weight(1f)) {
                Text(lesson.subject, style = MaterialTheme.typography.titleMedium)
                if (lesson.teacher.isNotBlank())
                    Text(lesson.teacher, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium)
                if (lesson.room.isNotBlank())
                    Text(tr("Sala: ") + lesson.room, style = MaterialTheme.typography.bodySmall)
                if (lesson.status != "scheduled") Text(
                    if (lesson.status == "cancelled") tr("Odwołane") else tr("Zastępstwo"),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                if (lesson.note.isNotBlank()) Text(lesson.note, style = MaterialTheme.typography.bodySmall)
            }
        }
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
