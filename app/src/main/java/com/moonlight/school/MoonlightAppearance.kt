package com.moonlight.school

import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.PI
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
    val speed: Float = 1f,
    val solid: String = "",
    val cardColors: Map<CardTone, String> = emptyMap(),
    val timeLine: String = "",
    val effect: String = "none",
    val effectColor: String = "#C8B8FF",
    val effectOpacity: Float = .65f,
    val angle: Float = 45f,
    val photo: String = "",
    val photoShade: Float = .35f,
    val gradientCount: Int = 2,
    val gradientColors: List<String> = emptyList(),
    val gradientPositions: List<GradientPoint?> = emptyList()
) {
    fun palette(): List<String> = GradientMesh.palette(first, second, gradientColors)
    fun positions(): List<GradientPoint> = GradientMesh.positions(gradientCount, angle, gradientPositions)
    fun withGradientColor(index: Int, color: String): MoonlightAppearance {
        val colors = palette().toMutableList()
        colors[index] = color
        return copy(first = colors[0], second = colors[4], gradientColors = colors)
    }
    companion object {
        fun load(p: SharedPreferences) = MoonlightAppearance(
            accent = p.getString("ui_accent", "").orEmpty(),
            text = p.getString("ui_text", "").orEmpty(),
            first = p.getString("ui_gradient_a", "#151322") ?: "#151322",
            second = p.getString("ui_gradient_b", "#352254") ?: "#352254",
            background = when(p.getString("ui_background", "solid")) {
                "gradient", "stars", "constellations" -> "gradient"
                "photo" -> "photo"
                else -> "solid"
            },
            animations = p.getBoolean("ui_animations", true),
            pill = p.getBoolean("ui_pill", true),
            speed = p.getFloat("ui_speed", 1f).coerceIn(0.5f, 2f),
            solid = p.getString("ui_solid", "").orEmpty(),
            timeLine = p.getString("ui_time_line", "").orEmpty(),
            cardColors = CardTone.entries.associateWith { tone ->
                p.getString(tone.prefKey, if (tone == CardTone.Lesson) p.getString("ui_panel", "").orEmpty() else "").orEmpty()
            },
            effect = p.getString("ui_effect", null)?.takeIf { it in listOf("none","stars","constellations") }
                ?: p.getString("ui_background", "solid")?.takeIf { it in listOf("stars","constellations") } ?: "none",
            effectColor = p.getString("ui_effect_color", "#C8B8FF") ?: "#C8B8FF",
            effectOpacity = p.getFloat("ui_effect_opacity", .65f).coerceIn(0f,1f),
            angle = p.getFloat("ui_gradient_angle", 45f).coerceIn(0f,360f),
            photo = p.getString("ui_photo", "").orEmpty(),
            photoShade = p.getFloat("ui_photo_shade", .35f).coerceIn(0f, .85f),
            gradientCount = p.getInt("ui_gradient_count", 2).coerceIn(2,8),
            gradientColors = p.getString("ui_gradient_colors", "").orEmpty().split(","),
            gradientPositions = GradientMesh.decodePositions(p.getString("ui_gradient_positions", "").orEmpty())
        )
    }
    fun save(p: SharedPreferences) {
        val editor = p.edit().putString("ui_accent", accent).putString("ui_text", text)
            .putString("ui_gradient_a", first).putString("ui_gradient_b", second)
            .putString("ui_background", background).putBoolean("ui_animations", animations)
            .putBoolean("ui_pill", pill).putFloat("ui_speed", speed)
            .putString("ui_solid", solid).putString("ui_time_line", timeLine)
            .putString("ui_effect", effect).putString("ui_effect_color", effectColor)
            .putFloat("ui_effect_opacity", effectOpacity).putFloat("ui_gradient_angle", angle)
            .putString("ui_photo", photo).putFloat("ui_photo_shade", photoShade)
            .putInt("ui_gradient_count", gradientCount).putString("ui_gradient_colors", palette().joinToString(","))
            .putString("ui_gradient_positions", GradientMesh.encodePositions(gradientPositions))
        CardTone.entries.forEach { editor.putString(it.prefKey, cardColors[it].orEmpty()) }
        editor.apply()
    }
    fun scheme(dark: Boolean): ColorScheme {
        val primary = parseHex(accent) ?: if (dark) Color(0xFFCAB8FF) else Color(0xFF59469A)
        val base = parseHex(solid) ?: if (dark) Color(0xFF14131C) else Color(0xFFF3F2F7)
        val surface = if (dark) Color(0xFF201E2A) else Color.White
        val ink = parseHex(text) ?: if (dark) Color(0xFFF0EDF8) else Color(0xFF211D2A)
        val container = primary
        val variant = surface
        val onPrimary = readableInk(primary, ink)
        val original = if (dark) darkColorScheme() else lightColorScheme()
        return original.copy(
            primary = primary, onPrimary = onPrimary, primaryContainer = container,
            onPrimaryContainer = onPrimary, background = base, onBackground = ink,
            surface = surface, onSurface = readableInk(surface, ink), surfaceVariant = variant, onSurfaceVariant = readableInk(variant, ink),
            secondary = primary, onSecondary = onPrimary, secondaryContainer = container, onSecondaryContainer = onPrimary,
            outline = ink.copy(alpha = .45f), outlineVariant = ink.copy(alpha = .18f),
            surfaceContainer = surface, surfaceContainerHigh = surface,
            surfaceContainerHighest = variant, surfaceContainerLow = surface,
            surfaceContainerLowest = surface, surfaceBright = surface, surfaceDim = surface
        )
    }
}
internal val LocalAppearance = staticCompositionLocalOf { MoonlightAppearance() }

internal val defaultTimeLineColor = Color(0xFFFF626E)
@Composable internal fun timeLineColor(): Color = parseHex(LocalAppearance.current.timeLine) ?: defaultTimeLineColor

internal enum class CardTone(val prefKey: String, val label: String) {
    Lesson("ui_card_lesson", "Kolor lekcji"),
    Day("ui_card_day", "Kolor dni"),
    SelectedDay("ui_card_selected_day", "Kolor wybranego dnia"),
    Event("ui_card_event", "Kolor wydarzeń"),
    Cancelled("ui_card_cancelled", "Kolor odwołanych lekcji"),
    CancelledDay("ui_card_cancelled_day", "Kolor odwołanych dni"),
    Substitution("ui_card_substitution", "Kolor zastępstw"),
    Summary("ui_card_summary", "Kolor podsumowania"),
    Grade("ui_card_grade", "Kolor ocen");
}

internal fun contrastRatio(a: Color, b: Color): Float {
    val l1 = a.luminance()
    val l2 = b.luminance()
    return (maxOf(l1, l2) + .05f) / (minOf(l1, l2) + .05f)
}

/** Preserve the chosen text color wherever readable; otherwise use the higher contrast ink. */
internal fun readableInk(background: Color, preferred: Color): Color =
    if (contrastRatio(background, preferred) >= 4.5f) preferred
    else if (contrastRatio(background, Color.White) >= contrastRatio(background, Color.Black)) Color.White
    else Color.Black

internal fun CardTone.defaultColor(s: ColorScheme): Color {
    val dark = s.surface.luminance() < .35f
    return when (this) {
        CardTone.Lesson, CardTone.Day, CardTone.Grade -> s.surface
        CardTone.SelectedDay -> s.primary
        CardTone.Event -> if (dark) Color(0xFF30304B) else Color(0xFFEAE4FA)
        CardTone.Cancelled -> if (dark) Color(0xFF49303D) else Color(0xFFFFE5E9)
        CardTone.CancelledDay -> if (dark) Color(0xFF563646) else Color(0xFFFADBE4)
        CardTone.Substitution -> if (dark) Color(0xFF423C2A) else Color(0xFFFFEFCB)
        CardTone.Summary -> lerp(s.surface, s.primary, .22f)
    }
}

@Composable
internal fun toneBackground(tone: CardTone): Color =
    parseHex(LocalAppearance.current.cardColors[tone].orEmpty()) ?: tone.defaultColor(MaterialTheme.colorScheme)

@Composable
internal fun toneInk(background: Color): Color =
    readableInk(background, MaterialTheme.colorScheme.onBackground)

@Composable
internal fun toneColors(tone: CardTone): CardColors {
    val background = toneBackground(tone)
    return CardDefaults.cardColors(containerColor = background, contentColor = toneInk(background))
}


internal fun parseHex(value: String): Color? {
    if (!Regex("#[0-9A-Fa-f]{6}").matches(value)) return null
    return runCatching { Color(android.graphics.Color.parseColor(value)) }.getOrNull()
}
internal fun hex(color: Color): String = String.format(java.util.Locale.ROOT, "#%06X", color.toArgb() and 0xffffff)

@Composable
internal fun MoonlightBackdrop(a: MoonlightAppearance, dark: Boolean) {
    val scheme = MaterialTheme.colorScheme
    val context = LocalContext.current
    val photo by produceState<android.graphics.Bitmap?>(initialValue = null, a.photo, a.background) {
        value = if (a.background == "photo") withContext(Dispatchers.IO) { WallpaperFiles.load(context, a.photo) } else null
    }
    val cosmic = a.background != "photo" && a.effect != "none"
    val effectColor = parseHex(a.effectColor) ?: Color(0xFFC8B8FF)
    Box(Modifier.fillMaxSize().background(scheme.background)) {
        if (a.background == "gradient") {
            val palette = a.palette()
            val positions = a.positions()
            val image = remember(palette, a.gradientCount, positions) {
                val w = 96
                val h = 160
                val pixels = GradientMesh.renderFree(palette, a.gradientCount, positions, w, h)
                android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
            }
            Image(image, contentDescription=null, modifier=Modifier.fillMaxSize(), contentScale=ContentScale.FillBounds)
        }
        if (a.background == "photo") {
            photo?.let { Image(it.asImageBitmap(), contentDescription=null, modifier=Modifier.fillMaxSize(), contentScale=ContentScale.Crop) }
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=a.photoShade)))
        }
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
                if (a.effect == "constellations") {
                    for (i in actual.indices) for (j in i+1 until actual.size) {
                        if ((actual[i]-actual[j]).getDistance() < 90.dp.toPx())
                            drawLine(effectColor.copy(alpha=.40f*a.effectOpacity), actual[i], actual[j], 1.dp.toPx())
                    }
                }
                actual.forEachIndexed { i,p ->
                    drawCircle(effectColor.copy(alpha=(.45f+.45f*(1f+sin(phase+i))*.5f)*a.effectOpacity), radius=(1f+i%3*.65f).dp.toPx(), center=p)
                }
            }
        }

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
                        .background(lerp(Color.Transparent, colors.primary.copy(alpha=.18f), fill))
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
internal fun AppearanceSettings(a: MoonlightAppearance, dark: Boolean, onChange: (MoonlightAppearance) -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(tr("Personalizacja"), style=MaterialTheme.typography.headlineSmall)
    Text(tr("Motyw na żywo"), style=MaterialTheme.typography.labelLarge)
    Box(Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(18.dp)), contentAlignment=Alignment.Center) {
        MoonlightBackdrop(a, dark)
        Surface(shape=RoundedCornerShape(15.dp),color=MaterialTheme.colorScheme.surface) {
            Text("Moonlight",Modifier.padding(horizontal=20.dp,vertical=12.dp),color=MaterialTheme.colorScheme.onSurface,
                style=MaterialTheme.typography.titleLarge)
        }
    }
    Text(tr("Presety akcentu"),style=MaterialTheme.typography.titleMedium)
    val activeAccent = hex(MaterialTheme.colorScheme.primary)
    listOf("#CAB8FF","#80CBC4","#F5A5C8","#7CB8FF","#FFC47D","#C5E1A5").chunked(3).forEach { group ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            group.forEach { value ->
                val selected = activeAccent.equals(value, ignoreCase=true)
                val shape = RoundedCornerShape(14.dp)
                Box(Modifier.weight(1f).height(52.dp).clip(shape)
                    .background(MaterialTheme.colorScheme.surface)
                    .border(if(selected) 2.dp else 1.dp,
                        if(selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,shape)
                    .selectable(selected=selected, role=Role.RadioButton, onClick={onChange(a.copy(accent=value))}),
                    contentAlignment=Alignment.Center) {
                    Box(Modifier.size(32.dp).clip(RoundedCornerShape(16.dp)).background(parseHex(value)!!))
                }
            }
        }
    }
    if(a.accent.isNotBlank()) TextButton(onClick={onChange(a.copy(accent=""))}) {Text(tr("Domyślny akcent"))}
    ColorField(tr("Kolor akcentu"),a.accent,MaterialTheme.colorScheme.primary) {onChange(a.copy(accent=it))}
    ColorField(tr("Kolor tekstu"),a.text,MaterialTheme.colorScheme.onSurface) {onChange(a.copy(text=it))}
    if(a.text.isNotBlank()) TextButton(onClick={onChange(a.copy(text=""))}) {Text(tr("Domyślny kolor tekstu"))}
    Text(tr("Kolory kart"),style=MaterialTheme.typography.titleMedium)
    CardTone.entries.forEach { tone ->
        ColorField(tr(tone.label), a.cardColors[tone].orEmpty(), tone.defaultColor(MaterialTheme.colorScheme)) { value ->
            onChange(a.copy(cardColors = a.cardColors + (tone to value)))
        }
        if (a.cardColors[tone].orEmpty().isNotBlank()) {
            TextButton(onClick={onChange(a.copy(cardColors=a.cardColors - tone))}) { Text(tr("Domyślny kolor karty")) }
        }
    }
    if (a.cardColors.values.any { it.isNotBlank() }) {
        OutlinedButton(onClick={onChange(a.copy(cardColors=emptyMap()))}) { Text(tr("Resetuj kolory kart")) }
    }
    ColorField(tr("Kolor linii czasu"), a.timeLine, defaultTimeLineColor) { onChange(a.copy(timeLine=it)) }
    if(a.timeLine.isNotBlank()) TextButton(onClick={onChange(a.copy(timeLine=""))}) { Text(tr("Domyślna linia czasu")) }
    Text(tr("Tło • warstwa bazowa"),style=MaterialTheme.typography.titleMedium)
    Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        listOf("solid" to "Kolor","gradient" to "Gradient","photo" to "Zdjęcie").forEach { (key,label) ->
            FilterChip(selected=a.background==key,onClick={onChange(a.copy(background=key))},label={Text(tr(label))})
        }
    }
    if(a.background=="solid") {
        ColorField(tr("Kolor tła"),a.solid,MaterialTheme.colorScheme.background) {onChange(a.copy(solid=it))}
        if(a.solid.isNotBlank()) TextButton(onClick={onChange(a.copy(solid=""))}) {Text(tr("Domyślne tło"))}
    } else if(a.background=="gradient") {
        Text(tr("Liczba kolorów: ")+a.gradientCount,style=MaterialTheme.typography.titleMedium)
        (2..8).toList().chunked(4).forEach { group ->
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            group.forEach { n ->
                FilterChip(selected=a.gradientCount==n,onClick={onChange(a.copy(gradientCount=n))},
                    label={Text(n.toString(),maxLines=1)},modifier=Modifier.weight(1f))
            }
        }
        }
        GradientPaletteEditor(a,onChange)
        TextButton(onClick={onChange(a.copy(gradientPositions=emptyList(),angle=0f))}) {Text(tr("Resetuj położenie punktów"))}
    } else {
        val context=LocalContext.current
        val scope=rememberCoroutineScope()
        val latest by rememberUpdatedState(a)
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf("") }
        val picker=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if(uri!=null && !busy) {
                busy=true; error=""
                scope.launch {
                    val imported=withContext(Dispatchers.IO) { WallpaperFiles.import(context,uri) }
                    if(imported!=null) onChange(latest.copy(background="photo",photo=imported))
                    else error="Nie można wczytać obrazu"
                    busy=false
                }
            }
        }
        OutlinedButton(onClick={picker.launch("image/*")},enabled=!busy) {Text(if(busy) tr("Wczytywanie…") else tr("Wybierz własne zdjęcie"))}
        if(error.isNotBlank()) Text(tr(error),color=MaterialTheme.colorScheme.error)
        Text(tr("Przyciemnienie zdjęcia: ")+(a.photoShade*100).toInt()+"%")
        Slider(a.photoShade,{onChange(a.copy(photoShade=it))},valueRange=0f..0.85f)
    }
    if(a.background!="photo") {
        Text(tr("Efekty • osobna warstwa"),style=MaterialTheme.typography.titleMedium)
        listOf("none" to "Brak","stars" to "Gwiazdy","constellations" to "Konstelacje").chunked(2).forEach { group ->
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                group.forEach { (key,label) -> FilterChip(selected=a.effect==key,onClick={onChange(a.copy(effect=key))},label={Text(tr(label))}) }
            }
        }
        if(a.effect!="none") {
            ColorField(tr("Kolor efektu"),a.effectColor,Color(0xFFC8B8FF)) {onChange(a.copy(effectColor=it))}
            Text(tr("Intensywność: ")+(a.effectOpacity*100).toInt()+"%")
            Slider(a.effectOpacity,{onChange(a.copy(effectOpacity=it))})
        }
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(tr("Pływający pasek nawigacji"),Modifier.weight(1f))
        Switch(a.pill,{onChange(a.copy(pill=it))})
    }
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
        Text(tr("Animacje"),Modifier.weight(1f))
        Switch(a.animations,{onChange(a.copy(animations=it))})
    }
    if(a.animations) {
        Text(tr("Szybkość: ")+String.format(java.util.Locale.ROOT,"%.1f",a.speed)+"×")
        Slider(value=a.speed,onValueChange={onChange(a.copy(speed=it))},valueRange=.5f..2f,steps=5)
    }
    OutlinedButton(onClick={onChange(MoonlightAppearance())}) {Text(tr("Przywróć wygląd domyślny"))}
}
}
