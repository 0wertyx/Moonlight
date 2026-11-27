package com.moonlight.school

/** Read-only school sections. Message bodies are requested individually by the user. */
internal object PortalSections {
    val automatic = linkedMapOf(
        "messages" to "/wiadomosci",
        "announcements" to "/ogloszenia",
        "attendance" to "/przegladaj_nb/uczen",
        "homework" to "/moje_zadania",
        "lucky" to "/uczen_index"
    )
    val keys = automatic.keys
    fun title(key: String): String = when(key) {
        "announcements" -> "Ogłoszenia"
        "attendance" -> "Frekwencja"
        "homework" -> "Zadania domowe"
        "lucky" -> "Szczęśliwy numerek"
        "messages" -> "Wiadomości"
        else -> "Librus"
    }
    fun kind(path: String): String? {
        val p = path.trimEnd('/')
        return automatic.entries.firstOrNull { it.value == p }?.key
            ?: if (p == "/wiadomosci" || Regex("/wiadomosci/[0-9]+").matches(p)) "messages" else null
    }
    fun startPath(key: String?): String = automatic[key] ?: if (key == "messages") "/wiadomosci" else "/"
    fun merge(old: SchoolSnapshot, incoming: SchoolSnapshot, key: String): SchoolSnapshot {
        require(key in keys)
        val section = incoming.sections.single { it.key == key }
        return old.copy(sections = old.sections.filterNot { it.key == key } + section)
    }
    internal fun messagePath(value: String): String? =
        value.takeIf { Regex("/wiadomosci/1/[0-9]{1,10}/[0-9]{1,15}").matches(it) }

    internal val MESSAGE_BODY_SCRIPT = """
(() => {
 if(location.origin !== 'https://synergia.librus.pl' || !/^\/wiadomosci\/1\/\d+\/\d+$/.test(location.pathname)) return null;
 const body=document.querySelector('.container-message-content');
 if(!body) return null;
 const value=(body.innerText || '').trim();
 return JSON.stringify({schemaVersion:1,lessons:[],grades:[],sections:[{key:'messages',entries:[{title:'Message',details:value.slice(0,30000)}]}]});
})()
    """.trimIndent()

    fun script(key: String): String {
        require(key in keys)
        return SCRIPT.replace("__SECTION__", key)
    }
    private val SCRIPT = """
(() => {
 if (location.origin !== 'https://synergia.librus.pl') return null;
 const key='__SECTION__', path=location.pathname.replace(/\/$/, '');
 const entries=[];
 const clean=x=>(x?.innerText || '').trim();
 const add=(title,details)=>{if(title && entries.length<200) entries.push({title:title.slice(0,300),details:details.slice(0,1500)});};
 if(key==='announcements' && path==='/ogloszenia'){
   const tables=document.querySelectorAll('div#body div.container-background table.decorated');
   if(!tables.length) return null;
   for(const table of tables){
     const cells=Array.from(table.querySelectorAll('td'));
     const title=clean(table.querySelector('thead'));
     if(cells.length>=4 && title) add(title,[clean(cells[2]),clean(cells[3])].filter(Boolean).join('\n'));
   }
 } else if(key==='attendance' && path==='/przegladaj_nb/uczen'){
   const table=document.querySelector('table.center.big.decorated');
   if(!table) return null;
   for(const row of table.querySelectorAll('tr[class*="line"]')){
     const cells=Array.from(row.children).filter(c=>c.tagName==='TD');
     const date=clean(cells[0]);
     if(!/^(?:\d{4}[-.]\d{2}[-.]\d{2}|\d{2}[.-]\d{2}[.-]\d{4})/.test(date)) continue;
     const slots=Array.from(cells[1]?.children || []);
     const lines=[];
     // The overview's numbered lesson strip starts at 1. An explicit zero header is also supported.
     // Preserve empty slots: filtering them before indexing would shift every later absence.
     const headerCells=Array.from(table.querySelectorAll('thead th, thead td'));
     const stripHeader=headerCells.find(c=>/Nr\s*lekcji/i.test(clean(c)));
     const headerLabels=stripHeader ? Array.from(stripHeader.querySelectorAll('div,span')).map(clean).filter(s=>/^\d{1,2}$/.test(s)) : [];
     const base=headerLabels[0]==='0' ? 0 : 1;
     for(const [i,slot] of slots.entries()){
       const mark=clean(slot);
       if(!mark) continue;
       const explicit=slot.getAttribute('data-lesson') || slot.getAttribute('data-period');
       const period=explicit && /^\d{1,2}$/.test(explicit) ? Number(explicit) :
         headerLabels.length===slots.length ? Number(headerLabels[i]) : i+base;
       if(!Number.isInteger(period) || period<0 || period>20) continue;
       // The known layout contains one linked attendance record per nonempty slot.
       const link=slot.querySelector('a');
       if(!link && !/^[\p{L}+\-]{1,12}$/u.test(mark)) continue;
       lines.push(period+'\t'+mark.replace(/\s+/g,' ').slice(0,30));
     }
     if(lines.length) add(date,'MLA1\n'+lines.join('\n'));
     else if(clean(cells[1])) add(date,clean(cells[1]));
   }
 } else if(key==='homework' && path==='/moje_zadania'){
   const table=document.querySelector('table.decorated.myHomeworkTable');
   if(!table) return null;
   for(const row of table.querySelectorAll('tbody tr')){
     const c=Array.from(row.children).filter(x=>x.tagName==='TD');
     if(c.length<9) continue;
     const title=[clean(c[0]),clean(c[2])].filter(Boolean).join(' • ');
     add(title,[clean(c[3]),clean(c[6]),clean(c[8])].filter(Boolean).join('\n'));
   }
 } else if(key==='lucky' && path==='/uczen_index'){
   const node=document.querySelector('.luckyNumber');
   if(!node) return null;
   const number=clean(node).match(/\d+/)?.[0];
   if(number) add(number,'Szczęśliwy numerek');
 } else if(key==='messages' && /^\/wiadomosci(?:\/\d+)?$/.test(path)){
   const table=document.querySelector('table.container-message table.decorated.stretch');
   if(!table) return null;
   for(const row of table.querySelectorAll('tbody tr')){
     const c=Array.from(row.children).filter(x=>x.tagName==='TD');
     if(c.length<5) continue;
     const a=c[3].querySelector('a[href]');
     if(!a) continue;
     let url;
     try { url=new URL(a.getAttribute('href'),location.origin); } catch(e) { continue; }
     if(url.origin!==location.origin || !/^\/wiadomosci\/1\/\d{1,10}\/\d{1,15}$/.test(url.pathname)) continue;
     const title=clean(c[3]);
     if(title && entries.length<200) entries.push({title:title.slice(0,300),details:clean(c[4]).slice(0,1500),
       mailPath:url.pathname, sender:clean(c[2]).slice(0,200),
       unread:c[2].style.fontWeight==='bold' || c[2].style.fontWeight==='700' || getComputedStyle(c[2]).fontWeight==='700'});
   }
   if(!entries.length && table.querySelector('a[href*="/wiadomosci/1/"]')) return null;
 } else return null;
 return JSON.stringify({schemaVersion:1,lessons:[],grades:[],sections:[{key,entries}]});
})()
    """.trimIndent()
}
