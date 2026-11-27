package com.moonlight.school

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SnapshotState(
    val connected: Boolean,
    val enabled: Boolean,
    val lastSuccess: Long,
    val error: String,
    val snapshot: SchoolSnapshot?,
    val portalEnabled: Boolean,
    val portalStatus: String,
    val gradesUpdated: Long,
    val lessonsUpdated: Long,
    val calendarUpdated: Long,
    val calendarStatus: String
)

/** Syncs only an explicitly selected local document, not Librus servers. */
object SnapshotSync {
    private const val PERIODIC = "moonlight.local.snapshot.periodic"
    private const val MANUAL = "moonlight.local.snapshot.manual"
    private const val URI = "source_uri"
    private const val ENABLED = "enabled"
    private const val GENERATION = "generation"
    private const val CACHE = "snapshot"
    private const val SUCCESS = "last_success"
    private const val ERROR = "error"
    private const val PORTAL_ENABLED = "portal_enabled"
    private const val PORTAL_STATUS = "portal_status"
    private const val PORTAL_ATTEMPT = "portal_attempt"
    private const val GRADES_UPDATED = "grades_updated"
    private const val LESSONS_UPDATED = "lessons_updated"
    private const val CALENDAR_UPDATED = "calendar_updated"
    private const val CALENDAR_STATUS = "calendar_status"
    private val lock = Any()

    fun preferences(context: Context): SharedPreferences =
        MoonlightConfig.snapshot(context)

    fun state(context: Context): SnapshotState {
        val p = preferences(context)
        val snapshot = p.getString(CACHE, null)?.let {
            runCatching { it.byteInputStream().use(LibrusBridge::readLocalSnapshot) }.getOrNull()
        }
        return SnapshotState(
            p.contains(URI), p.getBoolean(ENABLED, false),
            p.getLong(SUCCESS, 0L), p.getString(ERROR, "") ?: "", snapshot,
            p.getBoolean(PORTAL_ENABLED, false), p.getString(PORTAL_STATUS, "") ?: "",
            p.getLong(GRADES_UPDATED, p.getLong(SUCCESS, 0L)),
            p.getLong(LESSONS_UPDATED, p.getLong(SUCCESS, 0L)), p.getLong(CALENDAR_UPDATED, 0L), p.getString(CALENDAR_STATUS, "") ?: ""
        )
    }

    fun connect(context: Context, uri: Uri) {
        require(uri.scheme == "content")
        val resolver = context.contentResolver
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        synchronized(lock) {
            val p = preferences(context)
            val old = p.getString(URI, null)
            p.edit().putString(URI, uri.toString()).putBoolean(ENABLED, true)
                .putLong(GENERATION, p.getLong(GENERATION, 0L) + 1)
                .remove(PORTAL_ENABLED).remove(PORTAL_STATUS).remove(PORTAL_ATTEMPT)
                .remove(CACHE).remove(SUCCESS).remove(GRADES_UPDATED).remove(LESSONS_UPDATED).remove(CALENDAR_UPDATED).remove(CALENDAR_STATUS).remove(ERROR).apply()
            if (old != null && old != uri.toString()) runCatching {
                resolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        PortalAutoSync.cancel(context)
        schedule(context)
        refresh(context)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        synchronized(lock) {
            val p = preferences(context)
            if (!p.contains(URI)) return
            p.edit().putBoolean(ENABLED, enabled).apply()
        }
        if (enabled) schedule(context)
        else WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    fun disconnect(context: Context) {
        synchronized(lock) {
            val p = preferences(context)
            val old = p.getString(URI, null)
            val generation = p.getLong(GENERATION, 0L) + 1
            p.edit().remove(URI).remove(ENABLED).remove(CACHE).remove(SUCCESS)
                .remove(ERROR).remove(PORTAL_ENABLED).remove(PORTAL_STATUS).remove(PORTAL_ATTEMPT)
                .remove(GRADES_UPDATED).remove(LESSONS_UPDATED).remove(CALENDAR_UPDATED).remove(CALENDAR_STATUS)
                .putLong(GENERATION, generation).apply()
            if (old != null) runCatching {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(old), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        PortalAutoSync.cancel(context)
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(MANUAL)
        }
    }

    private fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<SnapshotWorker>(30, TimeUnit.MINUTES)
            .setInitialDelay(30, TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request
        )
    }

    fun refresh(context: Context, selectedDate: String? = null) {
        if (preferences(context).getBoolean(PORTAL_ENABLED, false)) {
            PortalAutoSync.refresh(context, selectedDate)
            return
        }
        if (!preferences(context).contains(URI)) return
        val request = OneTimeWorkRequestBuilder<SnapshotWorker>()
            .setInputData(workDataOf("manual" to true)).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.REPLACE, request)
    }

    internal fun run(context: Context, manual: Boolean): Boolean {
        val p = preferences(context)
        val source = synchronized(lock) {
            if (!manual && !p.getBoolean(ENABLED, false)) return true
            (p.getString(URI, null) ?: return true) to p.getLong(GENERATION, 0L)
        }
        val result = runCatching {
            context.contentResolver.openInputStream(Uri.parse(source.first))
                ?.use(LibrusBridge::readLocalSnapshot) ?: error("Source unavailable")
        }
        synchronized(lock) {
            if (p.getLong(GENERATION, 0L) != source.second ||
                p.getString(URI, null) != source.first ||
                (!manual && !p.getBoolean(ENABLED, false))) return true
            val data = result.getOrNull()
            if (data == null) {
                p.edit().putString(ERROR, "Не удалось обновить файл. Сохранена предыдущая копия.").apply()
                return false
            }
            val json = encode(data)
            if (json.toByteArray(Charsets.UTF_8).size > 512 * 1024) {
                p.edit().putString(ERROR, "Копия превышает лимит размера.").apply()
                return false
            }
            val now = System.currentTimeMillis()
            p.edit().putString(CACHE, json).putLong(SUCCESS, now)
                .putLong(GRADES_UPDATED, now).putLong(LESSONS_UPDATED, now)
                .putLong(CALENDAR_UPDATED, if (data.calendarMonth != null) now else 0L)
                .remove(ERROR).apply()
            return true
        }
    }

    /** Save a user-requested capture of an authenticated visible portal page. */
    fun savePortalPage(context: Context, category: String, incoming: SchoolSnapshot) {
        require(category in setOf("grades", "lessons", "calendar"))
        synchronized(lock) {
            val p = preferences(context)
            require(!p.contains(URI)) { "Disconnect the file source before importing the portal." }
            val old = state(context).snapshot ?: SchoolSnapshot(emptyList(), emptyList())
            val merged = when (category) {
                "grades" -> old.copy(grades = incoming.grades)
                "lessons" -> PortalPeriods.mergeWeek(old, incoming)
                else -> PortalPeriods.mergeMonth(old, incoming)
            }
            val json = encode(merged)
            require(merged.lessons.size <= 2000 && merged.grades.size <= 2000 && merged.events.size <= 500)
            require(json.toByteArray(Charsets.UTF_8).size <= 512 * 1024)
            val now = System.currentTimeMillis()
            p.edit().putString(CACHE, json).putLong(SUCCESS, now)
                .putLong(when(category) { "grades" -> GRADES_UPDATED; "lessons" -> LESSONS_UPDATED; else -> CALENDAR_UPDATED }, now)
                .remove(ERROR).apply()
        }
    }

    fun setPortalEnabled(context: Context, enabled: Boolean) {
        synchronized(lock) {
            val p = preferences(context)
            if (enabled && p.contains(URI)) return
            if (p.getBoolean(PORTAL_ENABLED, false) == enabled) return
            val edit = p.edit().putBoolean(PORTAL_ENABLED, enabled)
                .putLong(GENERATION, p.getLong(GENERATION, 0L) + 1)
                .remove(PORTAL_ATTEMPT).remove(ERROR)
                .putString(PORTAL_STATUS, if (enabled) "Oczekiwanie na synchronizację" else "Synchronizacja wyłączona")
            if (enabled) edit.remove(CACHE).remove(SUCCESS).remove(GRADES_UPDATED).remove(LESSONS_UPDATED).remove(CALENDAR_UPDATED).remove(CALENDAR_STATUS)
            edit.apply()
        }
        if (enabled) {
            PortalAutoSync.schedule(context)
            PortalAutoSync.refresh(context)
        } else PortalAutoSync.cancel(context)
    }

    internal fun beginPortalRefresh(context: Context): Long? = synchronized(lock) {
        val p = preferences(context)
        if (!p.getBoolean(PORTAL_ENABLED, false) || p.contains(URI)) return@synchronized null
        val now = System.currentTimeMillis()
        val previous = p.getLong(PORTAL_ATTEMPT, 0)
        if (now >= previous && now - previous < 60_000L) return@synchronized null
        p.edit().putLong(PORTAL_ATTEMPT, now).putString(PORTAL_STATUS, "Synchronizacja…").apply()
        p.getLong(GENERATION, 0)
    }

    internal fun finishPortalRefresh(context: Context, generation: Long, grades: List<ImportedGrade>?, lessons: List<ImportedLesson>?, status: String, stop: Boolean) {
        var cancel = false
        synchronized(lock) {
            val p = preferences(context)
            if (!p.getBoolean(PORTAL_ENABLED, false) || p.contains(URI) || p.getLong(GENERATION, 0) != generation) return
            val edit = p.edit().putString(PORTAL_STATUS, status)
            if (grades != null || lessons != null) {
                val previous = state(context).snapshot
                val merged = (previous ?: SchoolSnapshot(emptyList(), emptyList())).let { it.copy(lessons = lessons ?: it.lessons, grades = grades ?: it.grades) }
                val json = encode(merged)
                if (merged.grades.size <= 2000 && merged.lessons.size <= 2000 && json.toByteArray(Charsets.UTF_8).size <= 512 * 1024) {
                    val now = System.currentTimeMillis()
                    edit.putString(CACHE, json).putLong(SUCCESS, now).remove(ERROR)
                    if (grades != null) edit.putLong(GRADES_UPDATED, now)
                    if (lessons != null) edit.putLong(LESSONS_UPDATED, now)
                } else edit.putString(PORTAL_STATUS, "Dane przekraczają limit. Zachowano poprzednią kopię.")
            }
            if (stop) {
                edit.putBoolean(PORTAL_ENABLED, false).putLong(GENERATION, generation + 1)
                cancel = true
            }
            edit.apply()
        }
        if (cancel) PortalAutoSync.cancel(context)
    }

    internal fun savePeriod(context: Context, generation: Long, incoming: SchoolSnapshot, calendar: Boolean) {
        synchronized(lock) {
            val p = preferences(context)
            if (!p.getBoolean(PORTAL_ENABLED, false) || p.contains(URI) || p.getLong(GENERATION, 0) != generation) return
            val old = state(context).snapshot ?: SchoolSnapshot(emptyList(), emptyList())
            val merged = if (calendar) PortalPeriods.mergeMonth(old, incoming) else PortalPeriods.mergeWeek(old, incoming)
            val json = encode(merged)
            require(merged.lessons.size <= 2000 && merged.events.size <= 500 && json.toByteArray(Charsets.UTF_8).size <= 512 * 1024)
            val now = System.currentTimeMillis()
            p.edit().putString(CACHE, json).putLong(SUCCESS, now)
                .putLong(if (calendar) CALENDAR_UPDATED else LESSONS_UPDATED, now).apply()
        }
    }

    internal fun calendarStatus(context: Context, generation: Long, message: String) {
        synchronized(lock) {
            val p = preferences(context)
            if (p.getLong(GENERATION, 0) == generation && p.getBoolean(PORTAL_ENABLED, false))
                p.edit().putString(CALENDAR_STATUS, message).apply()
        }
    }

    private fun encode(snapshot: SchoolSnapshot): String {
        val lessons = JSONArray()
        snapshot.lessons.forEach {
            lessons.put(JSONObject().put("date", it.date).put("subject", it.subject)
                .put("period", it.period).put("status", it.status)
                .put("startTime", it.startTime ?: JSONObject.NULL).put("endTime", it.endTime ?: JSONObject.NULL))
        }
        val grades = JSONArray()
        snapshot.grades.forEach {
            grades.put(JSONObject().put("subject", it.subject).put("value", it.value)
                .put("weight", it.weight ?: JSONObject.NULL))
        }
        val events = JSONArray()
        snapshot.events.forEach {
            events.put(JSONObject().put("date", it.date).put("title", it.title).put("details", it.details))
        }
        return JSONObject().put("schemaVersion", 1).put("lessons", lessons)
            .put("grades", grades).put("calendarMonth", snapshot.calendarMonth ?: JSONObject.NULL)
            .put("events", events).put("lessonWeeks", JSONArray(snapshot.lessonWeeks))
            .put("calendarMonths", JSONArray(snapshot.calendarMonths)).toString()
    }
}

class SnapshotWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val ok = SnapshotSync.run(applicationContext, inputData.getBoolean("manual", false))
        return if (ok) Result.success() else Result.failure()
    }
}
