package com.moonlight.school

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val iconIndices = mapOf(
    "Desk" to 0, "Schedule" to 1, "Lessons" to 2, "Grades" to 3,
    "Feed" to 4, "Homework" to 5, "Calculator" to 6, "Messages" to 7,
    "Attendance" to 8, "Settings" to 9, "Lucky" to 10, "School" to 11,
    "Refresh" to 12, "Goals" to 13, "Connection" to 14,
    "Notes" to 5, "Announcements" to 4, "Reminders" to 4
)

@Composable
internal fun MoonlightIcon(name: String, fallback: String, size: Dp) {
    val context = LocalContext.current
    val bitmap = remember(context, name) {
        runCatching {
            val i = iconIndices.getValue(name)
            val atlas = context.assets.open("moonlight_icons.webp").use { BitmapFactory.decodeStream(it) }
            require(atlas.width == 240 && atlas.height == 144)
            Bitmap.createBitmap(atlas, (i % 5) * 48, (i / 5) * 48, 48, 48).asImageBitmap()
        }.getOrNull()
    }
    if (bitmap != null) Image(bitmap, contentDescription = null, modifier = Modifier.size(size).clip(RoundedCornerShape(8.dp)))
    else Text(fallback, style = MaterialTheme.typography.titleLarge)
}

@Composable
internal fun WeekArrow(next: Boolean, onClick: () -> Unit) {
    val background = MaterialTheme.colorScheme.surface
    val color = readableInk(background, MaterialTheme.colorScheme.primary)
    val description = if(next) tr("Następny tydzień") else tr("Poprzedni tydzień")
    FilledTonalIconButton(onClick = onClick, colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = background, contentColor = color), modifier = Modifier.size(48.dp).semantics {
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
