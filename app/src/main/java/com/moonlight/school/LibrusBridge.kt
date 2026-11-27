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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    var snapshot by remember { mutableStateOf<SchoolSnapshot?>(null) }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            busy = true
            error = ""
            scope.launch {
                try {
                    snapshot = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use(LibrusBridge::readLocalSnapshot)
                            ?: error("Не удалось открыть файл")
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    error = "Не удалось прочитать файл. Проверь формат Moonlight JSON v1."
                } finally {
                    busy = false
                }
            }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Librus", style = MaterialTheme.typography.headlineSmall)
        Text("Официальный портал открывается отдельно в браузере. Вход и пароль не передаются Moonlight.")
        Button(onClick = {
            try { LibrusBridge.openPortal(context) }
            catch (_: android.content.ActivityNotFoundException) { error = "Браузер не найден." }
        }) { Text("Открыть Synergia") }
        Text("Автоматическая синхронизация: не подключена.")
        HorizontalDivider()
        Text("Импорт локальной копии", style = MaterialTheme.typography.titleLarge)
        Text("Поддерживается собственный JSON-формат Moonlight v1. Данные вводятся пользователем и не подтверждены школой. Импорт хранится только в памяти.")
        OutlinedButton(enabled = !busy, onClick = { picker.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) {
            Text(if (busy) "Чтение…" else "Выбрать JSON")
        }
        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
        snapshot?.let { data ->
            Text("ЛОКАЛЬНАЯ КОПИЯ • не подтверждено Librus", color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = { snapshot = null; error = "" }) { Text("Очистить копию") }
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
