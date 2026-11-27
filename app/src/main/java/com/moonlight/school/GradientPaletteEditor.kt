package com.moonlight.school

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
internal fun GradientPaletteEditor(a:MoonlightAppearance,onChange:(MoonlightAppearance)->Unit) {
    val palette=a.palette()
    val left=GradientMesh.leftCount(a.gradientCount)
    val right=GradientMesh.rightCount(a.gradientCount)
    var editing by remember {mutableIntStateOf(-1)}
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        listOf(0 to left,4 to right).forEach { (start,count) ->
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                Text(if(start==0) "Lewa strona" else "Prawa strona",style=MaterialTheme.typography.labelLarge)
                repeat(count) { i ->
                    val index=start+i
                    val shape=RoundedCornerShape(14.dp)
                    val color=parseHex(palette[index]) ?: Color.Black
                    OutlinedButton(onClick={editing=index},modifier=Modifier.fillMaxWidth(),shape=shape,
                        contentPadding=PaddingValues(horizontal=8.dp,vertical=8.dp)) {
                        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(color)
                            .border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(8.dp)))
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text((if(start==0) "L" else "P")+(i+1),style=MaterialTheme.typography.labelMedium)
                            Text(palette[index],style=MaterialTheme.typography.labelSmall,maxLines=1)
                        }
                    }
                }
            }
        }
    }
    if(editing in 0..7) {
        val index=editing
        val latest by rememberUpdatedState(a)
        ColorPickerDialog("Kolor "+(if(index<4) "L"+(index+1) else "P"+(index-3)),
            parseHex(palette[index]) ?: Color.Black,{editing=-1}) { color ->
            onChange(latest.withGradientColor(index,hex(color)))
            editing=-1
        }
    }
}
