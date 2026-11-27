package com.moonlight.school

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal data class AnnouncementParts(val author: String, val date: String, val body: String) {
    companion object {
        private val authorPattern=Regex("""^(?:Autor|Nadawca|Dodane przez|Nauczyciel|Автор|Author|Sender)\s*:\s*(.+)$""",RegexOption.IGNORE_CASE)
        private val datePattern=Regex("""^(?:(?:Data(?: publikacji| dodania)?|Opublikowano|Дата|Date|Published)\s*:\s*)?((?:\d{4}[-.]\d{2}[-.]\d{2}|\d{2}[.-]\d{2}[.-]\d{4})(?:[ T]\d{2}:\d{2}(?::\d{2})?)?)$""",RegexOption.IGNORE_CASE)
        fun parse(raw:String):AnnouncementParts {
            var author=""
            var date=""
            val body=mutableListOf<String>()
            raw.lines().forEach { line ->
                val trimmed=line.trim()
                val a=authorPattern.matchEntire(trimmed)
                val d=datePattern.matchEntire(trimmed)
                when {
                    author.isEmpty() && a!=null -> author=a.groupValues[1].trim()
                    date.isEmpty() && d!=null -> date=d.groupValues[1]
                    else -> body.add(line)
                }
            }
            return AnnouncementParts(author,date,body.joinToString("\n").trim())
        }
    }
}

@Composable
internal fun SectionOverview(icon:String,title:String,count:Int?,updated:Long,refresh:()->Unit,canRefresh:Boolean) {
    val colors=MaterialTheme.colorScheme
    val bg=lerp(colors.surface,toneBackground(CardTone.Event),.18f)
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),color=bg,contentColor=readableInk(bg,colors.onBackground)) {
        Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            DashboardIcon(icon,29.dp,readableInk(bg,colors.primary))
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
                if(count!=null) Text(tr("Liczba wpisów: ")+count,style=MaterialTheme.typography.bodyMedium)
                if(updated>0L) Text(tr("Aktualizacja: ")+java.text.SimpleDateFormat("dd.MM, HH:mm",currentUiLocale()).format(java.util.Date(updated)),style=MaterialTheme.typography.labelSmall)
            }
            if(canRefresh) IconButton(onClick=refresh) { DashboardIcon("Refresh",24.dp,LocalContentColor.current) }
        }
    }
}

@Composable
internal fun SectionNotice(icon:String,title:String,description:String,action:String?,open:()->Unit) {
    val colors=MaterialTheme.colorScheme
    Surface(Modifier.fillMaxWidth(),shape=RoundedCornerShape(20.dp),color=colors.surface.copy(alpha=.94f)) {
        Column(Modifier.padding(20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(9.dp)) {
            DashboardIcon(icon,36.dp,colors.primary)
            Text(title,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            Text(description,style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            if(action!=null) Button(onClick=open) { Text(action) }
        }
    }
}

@Composable
internal fun AnnouncementCard(entry:SchoolEntry,a:MoonlightAppearance,icon:String="Announcements",isAnnouncement:Boolean=true) {
    val parts=remember(entry.details) { AnnouncementParts.parse(entry.details) }
    var expanded by rememberSaveable(entry.title,entry.details) { mutableStateOf(false) }
    val colors=MaterialTheme.colorScheme
    val bg=lerp(colors.surface,toneBackground(CardTone.Event),.26f)
    val ink=readableInk(bg,colors.onBackground)
    val accent=readableInk(bg,colors.primary)
    Card(onClick={expanded=!expanded},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(19.dp),
        colors=CardDefaults.cardColors(containerColor=bg,contentColor=ink)) {
        Column(Modifier.then(if(a.animations) Modifier.animateContentSize(tween((230/a.speed).toInt())) else Modifier).padding(14.dp),
            verticalArrangement=Arrangement.spacedBy(9.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                DashboardIcon(icon,20.dp,accent)
                Text(tr(if(isAnnouncement) "Ogłoszenie" else "Wiadomości"),style=MaterialTheme.typography.labelMedium,color=accent,modifier=Modifier.weight(1f))
                if(parts.date.isNotBlank()) Text(parts.date,style=MaterialTheme.typography.labelSmall)
            }
            Text(entry.title,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold,
                maxLines=if(expanded) Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis)
            if(parts.author.isNotBlank()) {
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)) {
                    DashboardIcon("School",18.dp,accent)
                    Text(parts.author,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                }
            }
            HorizontalDivider(color=ink.copy(alpha=.13f))
            if(parts.body.isNotBlank()) Text(parts.body,style=MaterialTheme.typography.bodyMedium,maxLines=if(expanded) Int.MAX_VALUE else 4,overflow=TextOverflow.Ellipsis)
            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.End) {
                Text(tr(if(expanded) "Zwiń" else "Rozwiń"),style=MaterialTheme.typography.labelLarge,color=accent)
                Spacer(Modifier.width(7.dp))
                Text(if(expanded) "⌃" else "⌄",color=accent,style=MaterialTheme.typography.titleLarge)
            }
        }
    }
}
