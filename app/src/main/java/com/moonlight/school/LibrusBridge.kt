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
    val startTime: String? = null, val endTime: String? = null
)
data class ImportedGrade(val subject: String, val value: String, val weight: Int?)
data class SchoolEvent(val date: String, val title: String, val details: String)
data class SchoolSnapshot(
    val lessons: List<ImportedLesson>, val grades: List<ImportedGrade>,
    val calendarMonth: String? = null, val events: List<SchoolEvent> = emptyList(),
    val lessonWeeks: List<String> = emptyList(),
    val calendarMonths: List<String> = listOfNotNull(calendarMonth)
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
                ImportedLesson(date, validText(row.getString("subject"), 100), period, status, times?.first, times?.second)
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
            }.distinct(), lessonWeeks = weeks, calendarMonths = months
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
        Text("Eksperymentalny widok oficjalnego portalu. Logowanie odbywa się na stronie Librus, a nie w formularzu Moonlight.")
        Button(onClick = { context.startActivity(Intent(context, PortalActivity::class.java)) }) { Text("Otwórz Librus w Moonlight") }
        Button(onClick = {
            try { LibrusBridge.openPortal(context) }
            catch (_: android.content.ActivityNotFoundException) { error = "Браузер не найден." }
        }) { Text("Otwórz oficjalny portal") }
        Text("Po zalogowaniu wróć tutaj i włącz odświeżanie. Odczyt ocen, trzech tygodni planu i dwóch miesięcy terminarza, około co 30 minut. Sesja pozostaje w WebView na urządzeniu. Używaj wyłącznie własnego konta, jeśli regulamin na to pozwala.")
        if (!state.connected) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Automatyczne odświeżanie portalu", modifier = Modifier.weight(1f))
                Switch(checked = state.portalEnabled, onCheckedChange = { SnapshotSync.setPortalEnabled(context, it) })
            }
            if (state.portalStatus.isNotEmpty()) Text(state.portalStatus, color = MaterialTheme.colorScheme.primary)
            if (state.portalEnabled) OutlinedButton(onClick = { SnapshotSync.refresh(context) }) { Text("Synchronizuj teraz") }
        } else Text("Odłącz plik przed uruchomieniem synchronizacji portalu.")
        if (state.snapshot != null || state.portalEnabled) TextButton(onClick = { SnapshotSync.disconnect(context) }) { Text("Usuń lokalną kopię") }
        HorizontalDivider()
        Text("Import kopii lokalnej", style = MaterialTheme.typography.titleLarge)
        Text("Wybierz plik JSON Moonlight v1. Narzędzie eksportu w repozytorium działa osobno na komputerze; nie jest częścią APK. Dane nie są weryfikowane przez szkołę.")
        OutlinedButton(onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
            Text(if (state.connected) "Zmień plik" else "Wybierz plik JSON")
        }
        if (state.connected) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Автообновление файла", modifier = Modifier.weight(1f))
                Switch(checked = state.enabled, onCheckedChange = { SnapshotSync.setEnabled(context, it) })
            }
            Text("Интервал: около 30 минут, зависит от Android.")
            OutlinedButton(onClick = { SnapshotSync.refresh(context) }) { Text("Обновить сейчас") }
            TextButton(onClick = { SnapshotSync.disconnect(context) }) { Text("Отключить и удалить копию") }
        }
        fun stamp(time: Long): String = if (time <= 0L) "Brak danych" else java.text.DateFormat.getDateTimeInstance().format(java.util.Date(time))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Pamięć urządzenia", style = MaterialTheme.typography.titleMedium)
                Text("Oceny: " + stamp(state.gradesUpdated))
                Text("Plan lekcji: " + stamp(state.lessonsUpdated))
                Text("Terminarz: " + stamp(state.calendarUpdated))
                if (state.calendarStatus.isNotBlank()) Text(state.calendarStatus, style = MaterialTheme.typography.bodySmall)
                Text("Tygodnie planu: " + (snapshot?.lessonWeeks?.size ?: 0))
                Text("Zapisano: ${snapshot?.grades?.size ?: 0} ocen, ${snapshot?.lessons?.size ?: 0} lekcji")
                Text("Dane i ustawienia są przechowywane prywatnie na tym telefonie. Bez kopii w chmurze.", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (state.error.isNotEmpty()) Text(state.error, color = MaterialTheme.colorScheme.error)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        snapshot?.let { data ->
            Text("Zapisane dane • nieoficjalny odczyt", style = MaterialTheme.typography.labelSmall)
            Text("Расписание", style = MaterialTheme.typography.titleMedium)
            if (data.lessons.isEmpty()) Text("Нет записей")
            data.lessons.take(100).forEach { lesson ->
                val state = when (lesson.status) {
                    "cancelled" -> "отмена (локальная запись)"
                    "substitution" -> "замена (локальная запись)"
                    else -> "урок"
                }
                Text("${lesson.date} · ${lesson.period} · ${lesson.subject} · $state")
            }
            if (data.lessons.size > 100) Text("Показано 100 из ${data.lessons.size} записей.")
            Text("Оценки", style = MaterialTheme.typography.titleMedium)
            if (data.grades.isEmpty()) Text("Нет записей")
            data.grades.take(100).forEach { grade ->
                Text("${grade.subject}: ${grade.value}" + (grade.weight?.let { " · вес $it" } ?: ""))
            }
            if (data.grades.size > 100) Text("Показано 100 из ${data.grades.size} записей.")
        }
    }
}
