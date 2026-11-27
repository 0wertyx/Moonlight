package com.moonlight.school

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.time.LocalDate

data class ImportedLesson(
    val date: String, val subject: String, val period: Int, val status: String,
    val startTime: String? = null, val endTime: String? = null,
    val note: String = "", val teacher: String = "", val room: String = ""
)
data class ImportedGrade(val subject: String, val value: String, val weight: Int?)
data class SchoolEvent(val date: String, val title: String, val details: String)
data class SchoolEntry(val title: String, val details: String, val mailPath: String = "", val sender: String = "", val unread: Boolean = false)
data class SchoolSection(val key: String, val entries: List<SchoolEntry>, val updated: Long)
data class SchoolSnapshot(
    val lessons: List<ImportedLesson>, val grades: List<ImportedGrade>,
    val calendarMonth: String? = null, val events: List<SchoolEvent> = emptyList(),
    val lessonWeeks: List<String> = emptyList(),
    val calendarMonths: List<String> = listOfNotNull(calendarMonth),
    val sections: List<SchoolSection> = emptyList()
)

/** Independent, read-only bridge. No access to Librus app internals or credentials. */
object LibrusBridge {
    private const val PORTAL = "https://synergia.librus.pl/"
    private const val MAX_BYTES = 512 * 1024

    fun openPortal(context: Context) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PORTAL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    /** Moonlight's own JSON schema; not an official Librus export format. */
    fun readLocalSnapshot(input: InputStream): SchoolSnapshot {
        val buffer = ByteArray(8192)
        val out = ByteArrayOutputStream()
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            require(out.size() + n <= MAX_BYTES) { "Файл слишком большой" }
            out.write(buffer, 0, n)
        }
        val root = JSONObject(out.toString("UTF-8"))
        require(root.getInt("schemaVersion") == 1) { "Неподдерживаемая версия формата" }
        val lessons = root.getJSONArray("lessons")
        val grades = root.getJSONArray("grades")
        val month = root.optString("calendarMonth", "").takeIf { it.isNotBlank() && it != "null" }
            ?.let { java.time.YearMonth.parse(it).toString() }
        val events = root.optJSONArray("events") ?: org.json.JSONArray()
        val sections = root.optJSONArray("sections") ?: org.json.JSONArray()
        require(sections.length() <= PortalSections.keys.size)
        fun dates(key: String, limit: Int, normalize: (String) -> String): List<String> {
            val a = root.optJSONArray(key) ?: return emptyList()
            require(a.length() <= limit)
            return List(a.length()) { normalize(a.getString(it)) }.distinct()
        }
        val weeks = dates("lessonWeeks", 32) {
            val d = LocalDate.parse(it)
            require(d.dayOfWeek == java.time.DayOfWeek.MONDAY)
            d.toString()
        }
        val months = (dates("calendarMonths", 12) { java.time.YearMonth.parse(it).toString() } + listOfNotNull(month)).distinct()
        require(events.length() <= 500 && (months.isNotEmpty() || events.length() == 0))
        require(lessons.length() <= 2000 && grades.length() <= 2000) { "Слишком много записей" }
        fun validText(value: String, max: Int): String = value.trim().also {
            require(it.isNotEmpty() && it.length <= max) { "Некорректное текстовое поле" }
        }
        return SchoolSnapshot(
            lessons = List(lessons.length()) { i ->
                val row = lessons.getJSONObject(i)
                val date = LocalDate.parse(row.getString("date")).toString()
                val period = row.getInt("period")
                val status = row.getString("status")
                require(period in 0..20 && status in setOf("scheduled", "cancelled", "substitution"))
                val times = LessonHours.validate(row.optString("startTime", ""), row.optString("endTime", ""))
                ImportedLesson(date, validText(row.getString("subject"), 100), period, status, times?.first, times?.second, row.optString("note", "").take(400), row.optString("teacher", "").take(160), row.optString("room", "").take(80))
            }.sortedWith(compareBy({ it.date }, { it.period })),
            grades = List(grades.length()) { i ->
                val row = grades.getJSONObject(i)
                val weight = if (row.isNull("weight")) null else row.getInt("weight")
                require(weight == null || weight in 1..20)
                ImportedGrade(validText(row.getString("subject"), 100), validText(row.getString("value"), 20), weight)
            },
            calendarMonth = month,
            events = List(events.length()) { i ->
                val row = events.getJSONObject(i)
                val date = LocalDate.parse(row.getString("date")).toString()
                require(date.take(7) in months)
                SchoolEvent(date, validText(row.getString("title"), 300), row.optString("details", "").take(600))
            }.distinct(), lessonWeeks = weeks, calendarMonths = months,
            sections = List(sections.length()) { i ->
                val section = sections.getJSONObject(i)
                val key = section.getString("key")
                require(key in PortalSections.keys)
                val rows = section.getJSONArray("entries")
                require(rows.length() <= 200)
                SchoolSection(key, List(rows.length()) { j ->
                    val row = rows.getJSONObject(j)
                    SchoolEntry(validText(row.getString("title"), 300), row.optString("details", "").take(if(key == "messages") 30000 else 1500),
                         if(key == "messages") PortalSections.messagePath(row.optString("mailPath")).orEmpty() else "",
                         if(key == "messages") row.optString("sender").take(200) else "", key == "messages" && row.optBoolean("unread"))
                }.distinct(), section.optLong("updated", System.currentTimeMillis()))
            }.distinctBy { it.key }
        )
    }
}

@Composable
fun LibrusPanel(context: Context) {
    val store = remember { SnapshotSync.preferences(context) }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(store) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> revision++ }
        store.registerOnSharedPreferenceChangeListener(listener)
        onDispose { store.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val state = remember(revision) { SnapshotSync.state(context) }
    val snapshot = state.snapshot
    var showPreview by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                SnapshotSync.connect(context, uri)
                error = ""
            } catch (_: Exception) {
                error = "Не удалось сохранить доступ к файлу."
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Librus", style = MaterialTheme.typography.headlineSmall)
        Text(tr("Zaloguj się w Moonlight. Hasło pozostaje w oficjalnym formularzu Librus."))
        if (!state.connected) Button(onClick = { context.startActivity(Intent(context, PortalActivity::class.java).putExtra("login_mode", true)) }) { Text(tr("Zaloguj się do Synergii")) }
        OutlinedButton(onClick = { context.startActivity(Intent(context, PortalActivity::class.java)) }) { Text(tr("Otwórz Librus w Moonlight")) }
        Button(onClick = {
            try { LibrusBridge.openPortal(context) }
            catch (_: android.content.ActivityNotFoundException) { error = "Браузер не найден." }
        }) { Text(tr("Otwórz oficjalny portal")) }
        Text(tr("Po zalogowaniu wróć tutaj i włącz odświeżanie. Odczyt ocen, czterech tygodni planu i dwóch miesięcy terminarza, około co 30 minut. Sesja pozostaje w WebView na urządzeniu. Używaj wyłącznie własnego konta, jeśli regulamin na to pozwala."))
        if (!state.connected) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Automatyczne odświeżanie portalu"), modifier = Modifier.weight(1f))
                Switch(checked = state.portalEnabled, onCheckedChange = { SnapshotSync.setPortalEnabled(context, it) })
            }
            if (state.portalStatus.isNotEmpty()) Text(tr(state.portalStatus), color = MaterialTheme.colorScheme.onBackground)
            if (state.portalEnabled) OutlinedButton(onClick = { SnapshotSync.refresh(context) }) { Text(tr("Synchronizuj teraz")) }
        } else Text(tr("Odłącz plik przed uruchomieniem synchronizacji portalu."))
        if (state.snapshot != null || state.portalEnabled) TextButton(onClick = { SnapshotSync.disconnect(context) }) { Text(tr("Usuń lokalną kopię")) }
        HorizontalDivider()
        Text(tr("Import kopii lokalnej"), style = MaterialTheme.typography.titleLarge)
        Text(tr("Wybierz plik JSON Moonlight v1. Narzędzie eksportu w repozytorium działa osobno na komputerze; nie jest częścią APK. Dane nie są weryfikowane przez szkołę."))
        OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
            Text(if (state.connected) tr("Zmień plik") else tr("Wybierz plik JSON"))
        }
        if (state.connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr("Автообновление файла"), modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = { SnapshotSync.setEnabled(context, it) })
            }
            Text(tr("Интервал: около 30 минут, зависит от Android."))
            OutlinedButton(onClick = { SnapshotSync.refresh(context) }) { Text(tr("Обновить сейчас")) }
            TextButton(onClick = { SnapshotSync.disconnect(context) }) { Text(tr("Отключить и удалить копию")) }
        }
        val noData = tr("Brak danych")
        fun stamp(time: Long): String = if (time <= 0L) noData else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(tr("Pamięć urządzenia"), style = MaterialTheme.typography.titleMedium)
                Text(tr("Oceny: ") + stamp(state.gradesUpdated))
                Text(tr("Plan lekcji: ") + stamp(state.lessonsUpdated))
                Text(tr("Terminarz: ") + stamp(state.calendarUpdated))
                if (state.calendarStatus.isNotBlank()) Text(state.calendarStatus, style = MaterialTheme.typography.bodySmall)
                Text(tr("Tygodnie planu: ") + (snapshot?.lessonWeeks?.size ?: 0))
                snapshot?.sections.orEmpty().forEach { Text(tr(PortalSections.title(it.key)) + ": " + stamp(it.updated)) }
                Text(tr("Zapisano: ") + (snapshot?.grades?.size ?: 0) + tr(" ocen") + ", " + (snapshot?.lessons?.size ?: 0) + tr(" lekcji"))
                Text(tr("Dane i ustawienia są przechowywane prywatnie na tym telefonie. Bez kopii w chmurze."), style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.error.isNotEmpty()) Text(state.error, color = MaterialTheme.colorScheme.error)
        if (error.isNotEmpty()) Text(tr(error), color = MaterialTheme.colorScheme.error)
        if (snapshot != null) {
            OutlinedButton(onClick = { showPreview = !showPreview }, modifier = Modifier.fillMaxWidth()) {
                Text(tr(if (showPreview) "Ukryj podgląd danych" else "Pokaż podgląd danych"))
            }
        }
        if (showPreview) snapshot?.let { data ->
            Text(tr("Zapisane dane • nieoficjalny odczyt"), style = MaterialTheme.typography.labelSmall)
            Text(tr("Расписание"), style = MaterialTheme.typography.titleMedium)
            if (data.lessons.isEmpty()) Text(tr("Нет записей"))
            data.lessons.take(100).forEach { lesson ->
                val state = when (lesson.status) {
                    "cancelled" -> tr("отмена (локальная запись)")
                    "substitution" -> tr("замена (локальная запись)")
                    else -> tr("урок")
                }
                Text("${lesson.date} · ${lesson.period} · ${lesson.subject} · $state")
            }
            if (data.lessons.size > 100) Text(tr("Pokazano 100 z ") + data.lessons.size + tr(" wpisów."))
            Text(tr("Оценки"), style = MaterialTheme.typography.titleMedium)
            if (data.grades.isEmpty()) Text(tr("Нет записей"))
            data.grades.take(100).forEach { grade ->
                Text("${grade.subject}: ${grade.value}" + (grade.weight?.let { " · " + tr("waga") + " $it" } ?: ""))
            }
            if (data.grades.size > 100) Text(tr("Pokazano 100 z ") + data.grades.size + tr(" wpisów."))
        }
    }
}
