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

data class ImportedLesson(val date: String, val subject: String, val period: Int, val status: String)
data class ImportedGrade(val subject: String, val value: String, val weight: Int?)
data class SchoolSnapshot(val lessons: List<ImportedLesson>, val grades: List<ImportedGrade>)

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
                ImportedLesson(date, validText(row.getString("subject"), 100), period, status)
            }.sortedWith(compareBy({ it.date }, { it.period })),
            grades = List(grades.length()) { i ->
                val row = grades.getJSONObject(i)
                val weight = if (row.isNull("weight")) null else row.getInt("weight")
                require(weight == null || weight in 1..20)
                ImportedGrade(validText(row.getString("subject"), 100), validText(row.getString("value"), 20), weight)
            }
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
        Text("Oficjalny portal otwiera się w przeglądarce. Zalogowanie tam nie łączy konta z Moonlight.")
        Button(onClick = {
            try { LibrusBridge.openPortal(context) }
            catch (_: android.content.ActivityNotFoundException) { error = "Браузер не найден." }
        }) { Text("Otwórz oficjalny portal") }
        Text("Synchronizacja z serwerem Librus: niedostępna. Aplikacja odświeża tylko wybrany plik lokalny.")
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
        if (state.lastSuccess > 0L) Text("Последнее чтение: " + java.text.DateFormat.getDateTimeInstance().format(java.util.Date(state.lastSuccess)))
        if (state.error.isNotEmpty()) Text(state.error, color = MaterialTheme.colorScheme.error)
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        snapshot?.let { data ->
            Text("ЛОКАЛЬНАЯ КОПИЯ • не подтверждено Librus", color = MaterialTheme.colorScheme.primary)
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
