package com.moonlight.school

import android.content.SharedPreferences
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

internal sealed interface StartupResult {
    data object Loading : StartupResult
    data object Failed : StartupResult
    data class Ready(val prefs: SharedPreferences, val dark: Boolean,
                     val appearance: MoonlightAppearance, val language: String) : StartupResult
}

@Composable
internal fun MoonlightStartupScreen(failed: Boolean, retry: () -> Unit) {
    val background = Color(0xFF171324)
    val accent = Color(0xFFCAB8FF)
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = background)) {
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(background, Color(0xFF30234A))))
            .systemBarsPadding(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Canvas(Modifier.size(88.dp)) {
                    val r = size.minDimension * .38f
                    drawCircle(accent, r, center)
                    drawCircle(Color(0xFF211A34), r * .91f, center + Offset(r * .52f, -r * .34f))
                    drawCircle(Color.White, 2.dp.toPx(), Offset(size.width * .79f, size.height * .23f))
                }
                Text("Moonlight", color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                if (failed) {
                    Text("Unable to load local data", color = accent, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = retry) { Text("Retry") }
                } else {
                    LinearProgressIndicator(modifier = Modifier.width(128.dp).height(3.dp), color = accent, trackColor = accent.copy(alpha = .18f))
                }
            }
        }
    }
}
