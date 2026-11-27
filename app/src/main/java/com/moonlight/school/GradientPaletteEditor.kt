package com.moonlight.school

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
    FreeGradientPreview(a, onEdit={editing=it}, onMove={index, point ->
        val saved=List(8) { a.gradientPositions.getOrNull(it) }.toMutableList()
        saved[index]=point
        onChange(a.copy(gradientPositions=saved))
    })
    Text("Przeciągnij punkt • dotknij, aby zmienić kolor",style=MaterialTheme.typography.bodySmall)
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

@Composable
private fun FreeGradientPreview(a:MoonlightAppearance,onEdit:(Int)->Unit,onMove:(Int,GradientPoint)->Unit) {
    val palette=a.palette()
    val active=GradientMesh.activeIndices(a.gradientCount)
    var moving by remember {mutableStateOf<Pair<Int,GradientPoint>?>(null)}
    val points=a.positions().toMutableList().apply { moving?.let {this[it.first]=it.second} }.toList()
    val image=remember(palette,active,points) {
        val w=96; val h=160
        val pixels=GradientMesh.renderFree(palette,a.gradientCount,points,w,h)
        android.graphics.Bitmap.createBitmap(pixels,w,h,android.graphics.Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    val latest by rememberUpdatedState(a)
    val edit by rememberUpdatedState(onEdit)
    val move by rememberUpdatedState(onMove)
    val shape=RoundedCornerShape(18.dp)
    Box(Modifier.fillMaxWidth().height(270.dp).clip(shape).border(1.dp,MaterialTheme.colorScheme.outline,shape)) {
        Image(image,contentDescription=null,modifier=Modifier.matchParentSize(),contentScale=ContentScale.FillBounds)
        Canvas(Modifier.matchParentSize().pointerInput(a.gradientCount) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val current=latest.positions()
                val ids=GradientMesh.activeIndices(latest.gradientCount)
                val pad=13.dp.toPx()
                val w=(size.width-2*pad).coerceAtLeast(1f)
                val h=(size.height-2*pad).coerceAtLeast(1f)
                fun screen(p:GradientPoint)=Offset(pad+p.x*w,pad+p.y*h)
                val index=ids.minByOrNull {(screen(current[it])-down.position).getDistance()} ?: return@awaitEachGesture
                val center=screen(current[index])
                if((center-down.position).getDistance()>26.dp.toPx()) return@awaitEachGesture
                down.consume()
                var dragged=false
                var last=current[index]
                var released=false
                try {
                    while(true) {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull {it.id==down.id} ?: break
                        if(change.isConsumed) break
                        if(!change.pressed) {released=true;change.consume();break}
                        val delta=change.position-down.position
                        if(!dragged && delta.getDistance()>viewConfiguration.touchSlop) dragged=true
                        if(dragged) {
                            val p=center+delta
                            last=GradientPoint(((p.x-pad)/w).coerceIn(0f,1f),((p.y-pad)/h).coerceIn(0f,1f))
                            moving=index to last
                        }
                        change.consume()
                    }
                    if(released) {
                        if(dragged) move(index,last) else edit(index)
                    }
                } finally { moving=null }
            }
        }) {
            val pad=13.dp.toPx()
            val w=(size.width-2*pad).coerceAtLeast(1f)
            val h=(size.height-2*pad).coerceAtLeast(1f)
            active.forEach {index ->
                val p=points[index]
                val at=Offset(pad+p.x*w,pad+p.y*h)
                val radius=if(moving?.first==index) 12.dp.toPx() else 9.dp.toPx()
                drawCircle(Color.Black.copy(alpha=.65f),radius+3.dp.toPx(),at)
                drawCircle(parseHex(palette[index]) ?: Color.Black,radius,at)
                drawCircle(Color.White,radius,at,style=Stroke(2.dp.toPx()))
            }
        }
    }
}
