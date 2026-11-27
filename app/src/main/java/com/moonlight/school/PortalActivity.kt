package com.moonlight.school

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONTokener

/** Experimental visible portal only; never reads login forms, credentials or cookies. */
class PortalActivity : Activity() {
    private lateinit var web: WebView
    private lateinit var address: TextView
    private lateinit var importButton: Button
    private var loaded = false

    private fun allowed(uri: Uri): Boolean =
        uri.scheme == "https" && uri.port in listOf(-1,443) &&
            uri.userInfo == null && uri.host in setOf("synergia.librus.pl","portal.librus.pl","api.librus.pl","konto.librus.pl","librus.pl","www.librus.pl")

    private fun pageKind(uri: Uri): String? {
        if (!allowed(uri) || uri.host != "synergia.librus.pl") return null
        val path = uri.path.orEmpty()
        return when {
            path == "/przegladaj_oceny/uczen" || path.startsWith("/przegladaj_oceny/uczen/") -> "grades"
            path == "/przegladaj_plan_lekcji" || path.startsWith("/przegladaj_plan_lekcji/") -> "lessons"
            path.trimEnd('/') == "/terminarz" -> "calendar"
            else -> PortalSections.kind(path)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            fitsSystemWindows = true
        }
        address = TextView(this).apply { setPadding(16,12,16,12); text = "Moonlight • oficjalny portal" }
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        fun button(label: String, click: () -> Unit) = Button(this).apply { text = label; setOnClickListener { click() } }
        bar.addView(button("Wróć") { if (web.canGoBack()) web.goBack() else finish() })
        bar.addView(button("Odśwież") { web.reload() })
        bar.addView(button("Zamknij") { finish() })
        importButton = button("Importuj widoczne dane") { capture() }.apply { isEnabled = false }
        root.addView(address); root.addView(bar); root.addView(importButton)
        web = WebView(this)
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
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return !allowed(request.url)
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                loaded = false
                importButton.isEnabled = false
                address.text = "Moonlight • " + (url?.let { Uri.parse(it).host } ?: "")
            }
            override fun onPageFinished(view: WebView, url: String?) {
                val current = Uri.parse(view.url.orEmpty())
                loaded = url == view.url && allowed(current)
                address.text = "Moonlight • " + (current.host ?: "")
                importButton.isEnabled = loaded && pageKind(current) != null
            }
        }
        root.addView(web, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
        web.loadUrl("https://synergia.librus.pl" + PortalSections.startPath(intent.getStringExtra("section")))
    }

    private fun capture() {
        if (!loaded) return
        val original = web.url ?: return
        val kind = pageKind(Uri.parse(original)) ?: return
        if (SnapshotSync.state(this).connected) {
            toast("Najpierw odłącz źródło plikowe w ustawieniach.")
            return
        }
        AlertDialog.Builder(this).setTitle("Import lokalnej kopii")
            .setMessage("Zapisz tylko widoczne dane własnego konta, jeśli regulamin na to pozwala. Dane pozostaną na urządzeniu. Nie jest to oficjalna synchronizacja.")
            .setNegativeButton("Anuluj", null)
            .setPositiveButton("Importuj") { _, _ ->
                if (!loaded || web.url != original) return@setPositiveButton
                val script = when (kind) { "grades" -> GRADE_SCRIPT; "calendar" -> CALENDAR_SCRIPT; "lessons" -> LESSON_SCRIPT; else -> PortalSections.script(kind) }
                web.evaluateJavascript(script) { result ->
                    if (isFinishing || isDestroyed || !loaded || web.url != original) return@evaluateJavascript
                    try {
                        require(result.length <= 2_000_000)
                        val json = JSONTokener(result).nextValue() as? String ?: error("unsupported page")
                        val snapshot = json.byteInputStream().use(LibrusBridge::readLocalSnapshot)
                        SnapshotSync.savePortalPage(this, kind, snapshot)
                        toast("Zapisano lokalną kopię. Wróć do Moonlight.")
                    } catch (_: Exception) { toast("Nie rozpoznano danych. Sprawdź widok portalu.") }
                }
            }.show()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    override fun onDestroy() {
        if (::web.isInitialized) {
            CookieManager.getInstance().flush()
            web.stopLoading()
            (web.parent as? ViewGroup)?.removeView(web)
            web.destroy()
        }
        super.onDestroy()
    }

    companion object {
        internal val GRADE_SCRIPT = """
(() => {
 if (location.origin !== 'https://synergia.librus.pl' || !location.pathname.startsWith('/przegladaj_oceny/uczen')) return null;
 const table = document.querySelectorAll('table.decorated.stretch')[1];
 if (!table) return null;
 const grades = [], seen = new Set();
 for (const row of table.querySelectorAll('tr[class^="line"]:not([name])')) {
   const cells = Array.from(row.children).filter(c=>c.tagName==='TD');
   const subject = cells[1]?.innerText.trim();
   if (!subject || subject.length>100) continue;
   for (const col of [2,5,6,9]) {
     for (const a of cells[col]?.querySelectorAll('span.grade-box a') || []) {
       const value = a.innerText.trim().replace(/\*+$/, '').trim();
       if (!value || value.length>20) continue;
       const href = a.getAttribute('href') || '';
       if (href && seen.has(href)) continue;
       if (href) seen.add(href);
       grades.push({subject,value,weight:null});
     }
   }
 }
 if (!grades.length || grades.length>2000) return null;
 return JSON.stringify({schemaVersion:1,lessons:[],grades});
})()
        """.trimIndent()
        internal val CALENDAR_SCRIPT = """
(() => {
 if (location.origin !== 'https://synergia.librus.pl' || location.pathname.replace(/\/$/, '') !== '/terminarz') return null;
 const table=document.querySelector('table.kalendarz.decorated.center,table.kalendarz');
 if (!table) return null;
 const names=['styczeń','luty','marzec','kwiecień','maj','czerwiec','lipiec','sierpień','wrzesień','październik','listopad','grudzień'];
 const monthNames=['stycz','lut','marc','kwiet','maj','czerw','lip','sierp','wrześ','paźdz','listop','grud'];
 let month=Number(document.querySelector('select[name="miesiac"]')?.value || document.querySelector('input[name="miesiac"]')?.value);
 let year=Number(document.querySelector('select[name="rok"]')?.value || document.querySelector('input[name="rok"]')?.value);
 if (!(month>=1 && month<=12 && year>=2000 && year<=2100)) {
   for (const node of document.querySelectorAll('caption,h1,h2,h3,table.kalendarz th')) {
     const t=(node.innerText||'').trim().toLowerCase();
     if (t.length>70) continue;
     const y=t.match(/\b(20\d{2})\b/);
     const n=monthNames.findIndex(x=>t.includes(x));
     if (y && n>=0) {month=n+1;year=+y[1];break;}
   }
 }
 if (!Number.isInteger(month) || month<1 || month>12 || !Number.isInteger(year) || year<2000 || year>2100) return null;
 const calendarMonth=String(year)+'-'+String(month).padStart(2,'0');
 const events=[],seen=new Set();
 const days=Array.from(table.querySelectorAll('.kalendarz-dzien'));
 const maxDay=new Date(Date.UTC(year,month,0)).getUTCDate();
 let started=false,last=0;
 for (const day of days) {
   const raw=(day.querySelector('.kalendarz-numer-dnia')?.innerText || '').trim();
   const m=raw.match(/^(\d{1,2})(?:\D|$)/);
   if (!m || +m[1]<1 || +m[1]>maxDay) continue;
   const n=+m[1];
   if (!started) {if(n!==1) continue; started=true;}
   if (n<last) break;
   last=n;
   const date=calendarMonth+'-'+String(n).padStart(2,'0');
   for (const cell of day.querySelectorAll('td')) {
     if (cell.querySelector('td')) continue;
     const title=(cell.innerText || '').trim();
     const info=(cell.getAttribute('title') || '').replace(/<[^>]*>/g,' ').trim();
     const action=cell.getAttribute('onclick') || '';
     if ((!info && !action.includes('terminarz/')) || !title || title.length>300) continue;
     const key=JSON.stringify([date,title,info]);
     if (seen.has(key)) continue;
     seen.add(key);
     events.push({date,title,details:info.slice(0,600)});
   }
 }
 if (!started || last<20 || events.length>500) return null;
 return JSON.stringify({schemaVersion:1,lessons:[],grades:[],calendarMonth,events});
})()
        """.trimIndent()
        internal val LESSON_SCRIPT = """
(() => {
 if (location.origin !== 'https://synergia.librus.pl' || !location.pathname.startsWith('/przegladaj_plan_lekcji')) return null;
 const table = document.querySelector('table.decorated.plan-lekcji');
 const week = document.querySelector('select[name="tydzien"]');
 const m = (week?.value || '').match(/^(\d{4}-\d{2}-\d{2})_(\d{4}-\d{2}-\d{2})$/);
 if (!table || !m) return null;
 const monday = new Date(m[1]+'T00:00:00Z');
 if (Number.isNaN(+monday) || monday.getUTCDay()!==1) return null;
 const lessons=[];
 for (const row of table.querySelectorAll('tr.line1')) {
   const cells=Array.from(row.children).filter(c=>c.tagName==='TD');
   const number=(cells[0]?.innerText || '').trim();
   if (!/^\d{1,2}$/.test(number) || +number>20) continue;
   // The timetable's TH contains the school-specific period hours.
   const header=Array.from(row.children).filter(c=>c.tagName==='TH').map(c=>c.innerText).join(' ');
   const clock=header.match(/(?:^|[^\d])(?:[01]?\d|2[0-3]):[0-5]\d(?!\d)/g) || [];
   const times=clock.map(s=>s.match(/\d{1,2}:[0-5]\d$/)?.[0]?.padStart(5,'0'));
   const valid=times.length===2 && times[0] && times[1] && times[0]<times[1];
   const startTime=valid?times[0]:null, endTime=valid?times[1]:null;
   const slots=cells.slice(1,-1);
   if (slots.length<5 || slots.length>7) return null;
   for (const [i,cell] of slots.entries()) {
     const labels=Array.from(cell.querySelectorAll('.plan-lekcji-info'));
     const flag=labels.map(x=>x.innerText).join(' ').toLowerCase();
     const note=[...new Set(labels.flatMap(x=>[x.innerText,x.getAttribute('title')||''])
       .map(x=>x.replace(/<[^>]*>/g,' ').trim()).filter(x=>x && !/^(odwołane|zastępstwo)$/i.test(x)))].join(' • ').slice(0,400);
     const entries=Array.from(cell.querySelectorAll('.text')).map(e=>{
       const lines=(e.innerText || '').split(/\r?\n/).map(x=>x.trim()).filter(Boolean);
       const subject=e.querySelector('b')?.innerText.trim();
       const info=(lines[1] || '').replace(/^[-–]\s*/, '').trim();
       const roomMatch=info.match(/^(.+?)\s+s\.\s*(.+)$/);
       const teacher=info && info!==subject && !/^(odwołan|zastępstw)/i.test(info) ? (roomMatch?roomMatch[1]:info) : '';
       return {subject,struck:!!e.closest('s'),teacher:teacher.slice(0,160),room:(roomMatch?.[2]||'').slice(0,80)};
     }).filter(e=>e.subject && e.subject.length<=100);
     const active=entries.filter(e=>!e.struck);
     let status='scheduled', selected=active;
     if (!active.length && flag.includes('odwołan')) {status='cancelled';selected=entries.filter(e=>e.struck);}
     else if (active.length && flag.includes('zastępstw')) status='substitution';
     if (selected.length!==1) continue;
     const date=new Date(+monday);date.setUTCDate(date.getUTCDate()+i);
     lessons.push({date:date.toISOString().slice(0,10),period:+number,subject:selected[0].subject,status,startTime,endTime,note,teacher:selected[0].teacher,room:selected[0].room});
   }
 }
 if (lessons.length>2000 || !table.querySelector('tr.line1')) return null;
 return JSON.stringify({schemaVersion:1,lessons,grades:[],lessonWeeks:[m[1]]});
})()
        """.trimIndent()
    }
}
