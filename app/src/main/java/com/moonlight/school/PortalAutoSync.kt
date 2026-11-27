package com.moonlight.school

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import java.time.LocalDate
import java.time.ZoneId
import java.time.DayOfWeek
import java.time.temporal.TemporalAdjusters
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/** Uses only the app's normal WebView session, with explicit user opt-in.
 * No password forms, cookie extraction, background login or session transfer. */
object PortalAutoSync {
    private const val PERIODIC = "moonlight.portal.periodic"
    private const val MANUAL = "moonlight.portal.manual"
    internal val mutex = Mutex()
    private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<PortalSyncWorker>(30, TimeUnit.MINUTES)
            .setInitialDelay(30, TimeUnit.MINUTES).setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun refresh(context: Context) {
        val request = OneTimeWorkRequestBuilder<PortalSyncWorker>().setConstraints(constraints).build()
        WorkManager.getInstance(context).enqueueUniqueWork(MANUAL, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC)
            cancelUniqueWork(MANUAL)
        }
    }
}

private data class PageResult(val snapshot: SchoolSnapshot? = null, val message: String = "", val stop: Boolean = false)

class PortalSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = PortalAutoSync.mutex.withLock {
        val ctx = applicationContext
        val generation = SnapshotSync.beginPortalRefresh(ctx) ?: return@withLock Result.success()
        try {
            val grades = readPage(ctx, "/przegladaj_oceny/uczen", PortalActivity.GRADE_SCRIPT)
            if (grades.snapshot == null) {
                SnapshotSync.finishPortalRefresh(ctx, generation, null, grades.message, grades.stop)
                return@withLock Result.success()
            }
            val lessons = readPage(ctx, "/przegladaj_plan_lekcji", PortalActivity.LESSON_SCRIPT)
            if (lessons.snapshot == null) {
                SnapshotSync.finishPortalRefresh(ctx, generation, null, lessons.message, lessons.stop)
                return@withLock Result.success()
            }
            val monday = LocalDate.now(ZoneId.of("Europe/Warsaw")).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val sunday = monday.plusDays(6)
            if (lessons.snapshot.lessons.any { LocalDate.parse(it.date).let { d -> d.isBefore(monday) || d.isAfter(sunday) } }) {
                SnapshotSync.finishPortalRefresh(ctx, generation, null, "Portal pokazuje inny tydzień. Zachowano poprzednią kopię.", true)
                return@withLock Result.success()
            }
            SnapshotSync.finishPortalRefresh(ctx, generation, SchoolSnapshot(lessons.snapshot.lessons, grades.snapshot.grades),
                "Zaktualizowano kopię ocen i planu", false)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            SnapshotSync.finishPortalRefresh(ctx, generation, null, "Błąd odczytu. Zachowano poprzednią kopię.", false)
        }
        Result.success()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun readPage(context: Context, path: String, script: String): PageResult = withContext(Dispatchers.Main.immediate) {
        val target = "https://synergia.librus.pl" + path
        fun samePage(uri: Uri): Boolean = uri.scheme == "https" && uri.host == "synergia.librus.pl" &&
            uri.port in listOf(-1, 443) && uri.userInfo == null && uri.path?.trimEnd('/') == path
        fun blocked(uri: Uri): PageResult {
            val auth = uri.host in setOf("konto.librus.pl","api.librus.pl","portal.librus.pl") ||
                uri.path.orEmpty().contains("loguj", ignoreCase = true)
            return PageResult(message = if (auth) "Zaloguj się ponownie w oficjalnym portalu, następnie włącz synchronizację." else
                "Portal przekierował na nieobsługiwaną stronę. Sprawdź połączenie ręcznie.", stop = true)
        }
        val web = WebView(context)
        try {
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                setSupportMultipleWindows(false)
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                safeBrowsingEnabled = true
            }
            android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
            withTimeout(25_000L) {
                suspendCancellableCoroutine<PageResult> { continuation ->
                    fun done(value: PageResult) {
                        if (continuation.isActive) continuation.resume(value)
                    }
                    var evaluated = false
                    web.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            if (!request.isForMainFrame) return false
                            if (samePage(request.url)) return false
                            done(blocked(request.url))
                            return true
                        }
                        override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                            val uri = Uri.parse(url.orEmpty())
                            if (!samePage(uri)) {
                                done(blocked(uri))
                                view.stopLoading()
                            }
                        }
                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) done(PageResult(message = "Brak połączenia. Zachowano poprzednią kopię."))
                        }
                        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                            if (!request.isForMainFrame) return
                            val code = response.statusCode
                            done(PageResult(message = if (code == 401 || code == 403)
                                "Brak dostępu. Sprawdź logowanie w oficjalnym portalu." else
                                "Portal zwrócił błąd HTTP $code. Zachowano poprzednią kopię.",
                                stop = code == 401 || code == 403 || code == 429))
                        }
                        override fun onPageFinished(view: WebView, url: String?) {
                            if (!continuation.isActive || evaluated || url != view.url) return
                            val uri = Uri.parse(url.orEmpty())
                            if (!samePage(uri)) { done(blocked(uri)); return }
                            evaluated = true
                            view.evaluateJavascript(script) { result ->
                                if (!continuation.isActive) return@evaluateJavascript
                                if (!samePage(Uri.parse(view.url.orEmpty()))) {
                                    done(blocked(Uri.parse(view.url.orEmpty())))
                                    return@evaluateJavascript
                                }
                                try {
                                    require(result.length <= 2_000_000)
                                    val json = JSONTokener(result).nextValue() as? String ?: error("Unsupported page")
                                    val snapshot = json.byteInputStream().use(LibrusBridge::readLocalSnapshot)
                                    done(PageResult(snapshot = snapshot))
                                } catch (_: Exception) {
                                    done(PageResult(message = "Nie rozpoznano tabeli portalu. Otwórz portal i sprawdź dane.", stop = true))
                                }
                            }
                        }
                    }
                    web.loadUrl(target)
                }
            }
        } catch (_: TimeoutCancellationException) {
            PageResult(message = "Przekroczono czas odczytu. Zachowano poprzednią kopię.")
        } finally {
            web.stopLoading()
            web.destroy()
        }
    }
}
