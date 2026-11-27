package com.moonlight.school

import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import org.json.JSONArray

data class Grade(val value: Int, val weight: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFCAB8FF),
                background = Color(0xFF101019),
                surface = Color(0xFF191827),
                secondaryContainer = Color(0xFF342A54)
            )) { Moonlight(this) }
        }
    }
}

@Composable
fun Moonlight(context: Context) {
    val prefs = remember { context.getSharedPreferences("moonlight_local", Context.MODE_PRIVATE) }
    var page by remember { mutableIntStateOf(0) }
    var demo by remember { mutableStateOf(prefs.getBoolean("demo", false)) }
    val subjects = listOf("Matematyka", "Język polski", "Informatyka", "Historia", "Język angielski", "Biologia")
    var canceled by remember {
        mutableStateOf(prefs.getStringSet("demo_cancelled", emptySet())?.toSet() ?: emptySet())
    }
    val grades = remember {
        mutableStateListOf<Grade>().apply {
            runCatching {
                val stored = JSONArray(prefs.getString("personal_grades", "[]"))
                for (i in 0 until stored.length()) {
                    val item = stored.getJSONArray(i)
                    val v = item.getInt(0)
                    val w = item.getInt(1)
                    if (v in 1..6 && w in 1..20) add(Grade(v,w))
                }
            }
        }
    }
    fun persistGrades() {
        val arr = JSONArray()
        grades.forEach { arr.put(JSONArray().put(it.value).put(it.weight)) }
        prefs.edit().putString("personal_grades", arr.toString()).apply()
    }
    var value by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("1") }
    var error by remember { mutableStateOf("") }
    Scaffold(
        bottomBar = {
            NavigationBar {
                listOf("Расписание", "Оценки", "Librus", "Настройки").forEachIndexed { index, label ->
                    NavigationBarItem(selected = page == index, onClick = { page = index },
                        icon = { Text(listOf("◷", "Σ", "↗", "⚙")[index]) }, label = { Text(label) })
                }
            }
        }
    ) { inset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inset).padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("☾  Moonlight", style = MaterialTheme.typography.headlineLarge)
                Text("Независимый школьный планировщик", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (demo) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Text("ДЕМО • вымышленные данные, не школьные записи", modifier = Modifier.padding(14.dp))
                }
            }
            when (page) {
                0 -> {
                    item { Text("Расписание", style = MaterialTheme.typography.headlineSmall) }
                    if (!demo) item {
                        Text("Автоматическая синхронизация пока недоступна.")
                        OutlinedButton(onClick = { page = 2 }) { Text("Открыть Librus") }
                        Button(onClick = { demo = true; prefs.edit().putBoolean("demo",true).apply() }) { Text("Открыть демо") }
                    } else {
                        itemsIndexed(subjects) { index, subject ->
                            val key = index.toString()
                            val isCanceled = key in canceled
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(subject, style = MaterialTheme.typography.titleLarge)
                                    Text(if (isCanceled) "ДЕМО: симуляция отмены" else "ДЕМО: пример урока")
                                    OutlinedButton(onClick = {
                                        canceled = if (isCanceled) canceled - key else canceled + key
                                        prefs.edit().putStringSet("demo_cancelled", canceled).apply()
                                    }) { Text(if (isCanceled) "Восстановить пример" else "Симулировать отмену") }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    item {
                        Text("Личный калькулятор", style = MaterialTheme.typography.headlineSmall)
                        Text("Введённые вручную оценки. Не данные Librus.")
                        val total = grades.sumOf { it.weight }
                        val avg = if (total == 0) null else grades.sumOf { it.value * it.weight }.toDouble() / total
                        Text(avg?.let { String.format(Locale.ROOT, "Средний балл: %.2f", it) } ?: "Средний балл: —",
                            style = MaterialTheme.typography.headlineMedium)
                        OutlinedTextField(value = value, onValueChange = { value = it }, label = { Text("Оценка 1–6") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        OutlinedTextField(value = weight, onValueChange = { weight = it }, label = { Text("Вес 1–20") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error)
                        Button(onClick = {
                            val v = value.toIntOrNull()
                            val w = weight.toIntOrNull()
                            if (v != null && w != null && v in 1..6 && w in 1..20) {
                                grades.add(Grade(v,w)); persistGrades(); value = ""; error = ""
                            } else error = "Проверь оценку и вес."
                        }) { Text("Добавить") }
                    }
                    itemsIndexed(grades) { index, grade ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Оценка: ${grade.value} • вес: ${grade.weight}")
                                TextButton(onClick = { grades.removeAt(index); persistGrades() }) { Text("Удалить") }
                            }
                        }
                    }
                }
                2 -> item { LibrusPanel(context) }
                3 -> item {
                    Text("Настройки", style = MaterialTheme.typography.headlineSmall)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Демонстрационное расписание")
                        Switch(checked = demo, onCheckedChange = { demo = it; prefs.edit().putBoolean("demo",it).apply() })
                    }
                    OutlinedButton(onClick = { canceled = emptySet(); prefs.edit().remove("demo_cancelled").apply() }) { Text("Сбросить симуляции") }
                    Text("Moonlight 0.1.0 • без доступа к школьному аккаунту")
                }
            }
        }
    }
}
