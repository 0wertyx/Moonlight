package com.moonlight.school

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MailCard(context:Context, entry:SchoolEntry, appearance:MoonlightAppearance, enabled:Boolean) {
    var opened by remember(entry.mailPath) { mutableStateOf(false) }
    var confirm by remember(entry.mailPath) { mutableStateOf(false) }
    var retry by remember(entry.mailPath) { mutableIntStateOf(0) }
    var body by remember(entry.mailPath) { mutableStateOf<MailBodyResult?>(null) }
    val colors=MaterialTheme.colorScheme
    val bg=lerp(colors.surface,toneBackground(CardTone.Event),.20f)
    val ink=readableInk(bg,colors.onBackground)
    val accent=readableInk(bg,colors.primary)
    LaunchedEffect(opened,retry,entry.mailPath,enabled) {
        if (opened && enabled && entry.mailPath.isNotBlank()) {
            body=null
            body=PortalAutoSync.openMessage(context,entry.mailPath)
        } else body=null
    }
    Card(onClick={confirm=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(18.dp),
        colors=CardDefaults.cardColors(containerColor=bg,contentColor=ink)) {
        Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                DashboardIcon("Messages",22.dp,accent)
                Text(entry.sender.ifBlank { tr("Wiadomości") },modifier=Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,
                    fontWeight=if(entry.unread) FontWeight.Bold else FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
                if(entry.unread) Box(Modifier.size(7.dp).background(accent,RoundedCornerShape(50)))
            }
            Text(entry.title,style=MaterialTheme.typography.titleMedium,fontWeight=if(entry.unread) FontWeight.Bold else FontWeight.SemiBold)
            if(entry.details.isNotBlank()) Text(entry.details,style=MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                DashboardIcon("ChevronRight",22.dp,accent)
            }
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(tr("Otworzyć wiadomość?"))},
        text={Text(tr("Wiadomość może zostać oznaczona jako przeczytana w Librus."))},
        confirmButton={TextButton(onClick={confirm=false;opened=true}){Text(tr("Otwórz"))}},
        dismissButton={TextButton(onClick={confirm=false}){Text(tr("Anuluj"))}})
    if(opened) AlertDialog(onDismissRequest={opened=false},title={Text(entry.title,style=MaterialTheme.typography.titleLarge)},text={
        Column(Modifier.heightIn(max=430.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            if(entry.sender.isNotBlank()) Text(entry.sender,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            if(entry.details.isNotBlank()) Text(entry.details,style=MaterialTheme.typography.labelMedium)
            HorizontalDivider()
            when {
                !enabled -> Text(tr("Brak połączenia z dziennikiem"))
                entry.mailPath.isBlank() -> Text(tr("Starsza kopia. Odśwież pocztę."))
                body==null -> Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp)
                    Text(tr("Odczyt wiadomości…"))
                }
                body?.body==null -> Text(tr(body?.error.orEmpty().ifBlank { "Nie udało się odczytać wiadomości." }))
                else -> Text(body?.body.orEmpty().ifBlank { tr("Brak treści wiadomości") },style=MaterialTheme.typography.bodyLarge)
            }
        }
    },confirmButton={TextButton(onClick={opened=false}){Text("OK")}},dismissButton={
        if(enabled && body!=null && body?.body==null && entry.mailPath.isNotBlank()) TextButton(onClick={retry++}){Text(tr("Ponów"))}
    })
}
