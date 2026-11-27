package com.moonlight.school

import android.content.SharedPreferences
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

internal data class MoonlightAppearance(
    val accent: String = "",
    val text: String = "",
    val first: String = "#151322",
    val second: String = "#352254",
    val background: String = "solid",
    val animations: Boolean = true,
    val pill: Boolean = true,
    val speed: Float = 1f
) {
    companion object {
        fun load(p: SharedPreferences) = MoonlightAppearance(
            accent = p.getString("ui_accent", "").orEmpty(),
            text = p.getString("ui_text", "").orEmpty(),
            first = p.getString("ui_gradient_a", "#151322") ?: "#151322",
            second = p.getString("ui_gradient_b", "#352254") ?: "#352254",
            background = p.getString("ui_background", "solid")?.takeIf { it in listOf("solid","gradient","stars","constellations") } ?: "solid",
            animations = p.getBoolean("ui_animations", true),
            pill = p.getBoolean("ui_pill", true),
            speed = p.getFloat("ui_speed", 1f).coerceIn(0.5f, 2f)
        )
    }
    fun save(p: SharedPreferences) {
        p.edit().putString("ui_accent", accent).putString("ui_text", text)
            .putString("ui_gradient_a", first).putString("ui_gradient_b", second)
            .putString("ui_background", background).putBoolean("ui_animations", animations)
            .putBoolean("ui_pill", pill).putFloat("ui_speed", speed).apply()
    }
    fun scheme(dark: Boolean): ColorScheme {
        val primary = parseHex(accent) ?: if (dark) Color(0xFFCAB8FF) else Color(0xFF59469A)
        val base = if (dark) Color(0xFF14131C) else Color(0xFFF3F2F7)
        val surface = if (dark) Color(0xFF201E2A) else Color.White
        val ink = parseHex(text) ?: if (dark) Color(0xFFF0EDF8) else Color(0xFF211D2A)
        val container = lerp(surface, primary, if (dark) .28f else .17f)
        val variant = lerp(surface, primary, .10f)
        val onPrimary = if (primary.luminance() > .42f) Color(0xFF171321) else Color.White
        val original = if (dark) darkColorScheme() else lightColorScheme()
        return original.copy(
            primary = primary, onPrimary = onPrimary, primaryContainer = container,
            onPrimaryContainer = ink, background = base, onBackground = ink,
            surface = surface, onSurface = ink, surfaceVariant = variant, onSurfaceVariant = ink,
            secondaryContainer = container, onSecondaryContainer = ink,
            surfaceContainer = surface, surfaceContainerHigh = lerp(surface, primary, .07f),
            surfaceContainerHighest = variant, surfaceContainerLow = surface
        )
    }
}
internal val LocalAppearance = staticCompositionLocalOf { MoonlightAppearance() }

private fun parseHex(value: String): Color? {
    if (!Regex("#[0-9A-Fa-f]{6}").matches(value)) return null
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
}
private fun hex(color: Color): String = String.format(java.util.Locale.ROOT, "#%06X", color.toArgb() and 0xffffff)

@Composable
internal fun MoonlightBackdrop(a: MoonlightAppearance, dark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val first = parseHex(a.first) ?: Color(0xFF151322)
    val second = parseHex(a.second) ?: Color(0xFF352254)
    val brush = Brush.linearGradient(listOf(first,second))
    val cosmic = a.background == "stars" || a.background == "constellations"
    Box(Modifier.fillMaxSize().then(if(a.background == "solid") Modifier.background(scheme.background) else Modifier.background(brush))) {
        if (cosmic) {
            val points = remember { Random(17).let { r -> List(42) { Offset(r.nextFloat(), r.nextFloat()) } } }
            val phase = if (a.animations) {
                val transition = rememberInfiniteTransition(label = "space")
                val frame by transition.animateFloat(
                    initialValue = 0f, targetValue = 6.283185f,
                    animationSpec = infiniteRepeatable(tween((20000 / a.speed).toInt(), easing = LinearEasing), RepeatMode.Restart),
                    label = "orbit"
                )
                frame
            } else 0f
            Canvas(Modifier.fillMaxSize()) {
                val actual = points.mapIndexed { i,p ->
                    Offset((p.x + .009f * sin(phase+i)).coerceIn(0f,1f)*size.width,
                        (p.y + .007f * sin(phase+i*2)).coerceIn(0f,1f)*size.height)
                }
                if (a.background == "constellations") {
                    for (i in actual.indices) for (j in i+1 until actual.size) {
                        if ((actual[i]-actual[j]).getDistance() < 90.dp.toPx())
                            drawLine(Color(0xFFC8B8FF).copy(alpha=.18f), actual[i], actual[j], 1.dp.toPx())
                    }
                }
                actual.forEachIndexed { i,p ->
                    drawCircle(Color.White.copy(alpha=.28f+.20f*(1f+sin(phase+i))*.5f), radius=(1f+i%3*.65f).dp.toPx(), center=p)
                }
            }
        }
        if (!dark && a.background != "solid") Box(Modifier.fillMaxSize().background(Color.White.copy(alpha=.68f)))
    }
}

internal data class MoonlightTab(val key: String, val icon: String, val label: String)
@Composable
internal fun FloatingNavigation(tabs: List<MoonlightTab>, selected: String, a: MoonlightAppearance, onSelect: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(32.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=10.dp,vertical=8.dp)) {
        Surface(modifier = Modifier.fillMaxWidth().border(1.dp, colors.primary.copy(alpha=.25f), shape),
            shape=shape, color=colors.surface.copy(alpha=.96f), shadowElevation=8.dp) {
            Row(Modifier.fillMaxWidth().padding(5.dp).selectableGroup(), horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                tabs.forEach { tab ->
                    val active = selected == tab.key
                    val animatedScale by animateFloatAsState(if(active && a.animations) 1.10f else 1f,
                        animationSpec=if(a.animations) tween((180/a.speed).toInt()) else snap(), label="tab")
                    val fill by animateFloatAsState(if(active) 1f else 0f,
                        animationSpec=if(a.animations) tween((220/a.speed).toInt()) else snap(), label="selection")
                    Column(Modifier.weight(1f).heightIn(min=60.dp).clip(RoundedCornerShape(27.dp))
                        .background(lerp(Color.Transparent, colors.primaryContainer, fill))
                        .selectable(selected=active, role=Role.Tab, onClick={onSelect(tab.key)})
                        .padding(vertical=7.dp), horizontalAlignment=Alignment.CenterHorizontally, verticalArrangement=Arrangement.Center) {
                        Box(Modifier.scale(animatedScale)) { MoonlightIcon(tab.key,tab.icon,28.dp) }
                        Spacer(Modifier.height(4.dp))
                        Text(tab.label, maxLines=1, overflow=TextOverflow.Ellipsis, style=MaterialTheme.typography.labelSmall,
                            color=colors.onSurface)
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorField(label: String, current: String, fallback: Color, onApply: (String) -> Unit) {
    var draft by remember(current) { mutableStateOf(current.ifBlank { hex(fallback) }) }
    val valid = parseHex(draft) != null
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically, horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(30.dp).clip(RoundedCornerShape(9.dp)).background(parseHex(draft) ?: fallback)
                .border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(9.dp)))
            OutlinedTextField(draft,{draft=it.take(7)},Modifier.weight(1f), label={Text(label)}, singleLine=true,isError=!valid)
            TextButton(onClick={onApply(draft.uppercase())},enabled=valid) { Text("OK") }
        }
    }
}

@Composable
internal fun AppearanceSettings(a: MoonlightAppearance, dark: Boolean, onChange: (MoonlightAppearance) -> Unit) {
    Text("Personalizacja", style=MaterialTheme.typography.headlineSmall)
    Text("Motyw na żywo", style=MaterialTheme.typography.labelLarge)
    Box(Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(18.dp)).background(
        if(a.background=="solid") Brush.linearGradient(listOf(MaterialTheme.colorScheme.surface,MaterialTheme.colorScheme.surface))
        else Brush.linearGradient(listOf(parseHex(a.first) ?: Color.DarkGray, parseHex(a.second) ?: Color.Black))
    ).padding(12.dp), contentAlignment=Alignment.Center) {
        Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surface) {
            Text("Moonlight",Modifier.padding(horizontal=20.dp,vertical=12.dp),color=MaterialTheme.colorScheme.onSurface,
                style=MaterialTheme.typography.titleLarge)
        }
    }
    Text("Kolor akcentu",style=MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        listOf("#CAB8FF","#80CBC4","#F5A5C8","#7CB8FF","#FFC47D","#C5E1A5").forEach { value ->
            val c=parseHex(value)!!
            Surface(onClick={onChange(a.copy(accent=value))},shape=RoundedCornerShape(18.dp),color=c,
                modifier=Modifier.size(36.dp).border(if(a.accent==value) 3.dp else 1.dp,MaterialTheme.colorScheme.onSurface,RoundedCornerShape(18.dp))) {}
        }
    }
    ColorField("Akcent HEX",a.accent,MaterialTheme.colorScheme.primary) {onChange(a.copy(accent=it))}
    ColorField("Tekst HEX",a.text,MaterialTheme.colorScheme.onSurface) {onChange(a.copy(text=it))}
    if(a.text.isNotBlank()) TextButton(onClick={onChange(a.copy(text=""))}) {Text("Domyślny kolor tekstu")}
    Text("Tło",style=MaterialTheme.typography.titleMedium)
    listOf("solid" to "Jednolite","gradient" to "Gradient","stars" to "Gwiazdy","constellations" to "Konstelacje").chunked(2).forEach { group ->
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            group.forEach { (key,label) -> FilterChip(selected=a.background==key,onClick={onChange(a.copy(background=key))},label={Text(label)}) }
        }
    }
    if(a.background!="solid") {
        ColorField("Gradient A",a.first,Color(0xFF151322)) {onChange(a.copy(first=it))}
        ColorField("Gradient B",a.second,Color(0xFF352254)) {onChange(a.copy(second=it))}
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Pływający pasek nawigacji",Modifier.weight(1f))
        Switch(a.pill,{onChange(a.copy(pill=it))})
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text("Animacje",Modifier.weight(1f))
        Switch(a.animations,{onChange(a.copy(animations=it))})
    }
    if(a.animations) {
        Text("Szybkość: "+String.format(java.util.Locale.ROOT,"%.1f",a.speed)+"×")
        Slider(value=a.speed,onValueChange={onChange(a.copy(speed=it))},valueRange=.5f..2f,steps=5)
    }
    OutlinedButton(onClick={onChange(MoonlightAppearance())}) {Text("Przywróć wygląd domyślny")}
}
