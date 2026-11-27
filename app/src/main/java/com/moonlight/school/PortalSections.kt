package com.moonlight.school

/** Explicit read-only student sections. No account settings, recipient directories or message bodies. */
internal object PortalSections {
    val automatic = linkedMapOf(
        "announcements" to "/ogloszenia",
        "attendance" to "/przegladaj_nb/uczen",
        "homework" to "/moje_zadania",
        "lucky" to "/uczen_index"
    )
    val keys = automatic.keys + "messages"
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
     const data=clean(cells[1]);
     if(data) add(date,data);
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
     if(c.length<5 || !c[3].querySelector('a')) continue;
     add(clean(c[3]),clean(c[4]));
   }
 } else return null;
 return JSON.stringify({schemaVersion:1,lessons:[],grades:[],sections:[{key,entries}]});
})()
    """.trimIndent()
}
