package com.moonlight.school

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
internal fun MoonlightIcon(name: String, fallback: String, size: Dp) {
    DashboardIcon(name, size, LocalContentColor.current)
}

@Composable
internal fun WeekArrow(next: Boolean, onClick: () -> Unit) {
    val background = MaterialTheme.colorScheme.surface
    val color = readableInk(background, MaterialTheme.colorScheme.primary)
    val description = if(next) tr("Następny tydzień") else tr("Poprzedni tydzień")
    IconButton(onClick = onClick, colors = IconButtonDefaults.iconButtonColors(contentColor = color), modifier = Modifier.size(48.dp).semantics {
        contentDescription = description
    }) {
        Canvas(Modifier.size(30.dp)) {
            val outer = size.width * if (next) 0.30f else 0.70f
            val tip = size.width * if (next) 0.70f else 0.30f
            val middle = Offset(tip, size.height * 0.5f)
            val stroke = 4.dp.toPx()
            drawLine(color, Offset(outer, size.height * 0.18f), middle, stroke, StrokeCap.Round)
            drawLine(color, middle, Offset(outer, size.height * 0.82f), stroke, StrokeCap.Round)
        }
    }
}
