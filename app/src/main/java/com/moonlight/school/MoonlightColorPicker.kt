package com.moonlight.school

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun ColorField(label:String,current:String,fallback:Color,onApply:(String)->Unit) {
    val selected=parseHex(current) ?: fallback
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth(),shape=RoundedCornerShape(16.dp)) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(8.dp)).background(selected)
            .border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(8.dp)))
        Spacer(Modifier.width(12.dp))
        Text(label,Modifier.weight(1f),color=MaterialTheme.colorScheme.onSurface)
        Text(hex(selected),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurface)
    }
    if(open) ColorPickerDialog(label,selected,{open=false}) {onApply(hex(it));open=false}
}

@Composable
private fun Modifier.pickPoint(onPoint:(Float,Float)->Unit):Modifier {
    val latest by rememberUpdatedState(onPoint)
    return pointerInput(Unit) {
        awaitEachGesture {
            val down=awaitFirstDown(requireUnconsumed=false)
            fun pick(p:Offset) {
                latest((p.x/size.width.coerceAtLeast(1)).coerceIn(0f,1f),(p.y/size.height.coerceAtLeast(1)).coerceIn(0f,1f))
            }
            pick(down.position)
            down.consume()
            do {
                val event=awaitPointerEvent()
                val change=event.changes.firstOrNull {it.id==down.id} ?: break
                if(change.pressed) {pick(change.position);change.consume()}
            } while(change.pressed)
        }
    }
}

@Composable
internal fun ColorPickerDialog(label:String,initial:Color,onDismiss:()->Unit,onApply:(Color)->Unit) {
    val hsv=remember(initial) {FloatArray(3).also {android.graphics.Color.colorToHSV(initial.toArgb(),it)}}
    var h by remember {mutableFloatStateOf(hsv[0])}
    var s by remember {mutableFloatStateOf(hsv[1])}
    var v by remember {mutableFloatStateOf(hsv[2])}
    var input by remember {mutableStateOf(hex(initial))}
    fun fromHsv()=Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f,359.999f),s,v)))
    fun update(hue:Float,sat:Float,value:Float) {
        h=hue;s=sat;v=value;input=hex(fromHsv())
    }
    val selected=fromHsv()
    val svDescription=tr("Nasycenie i jasność")
    val hueDescription=tr("Odcień")
    val pure=Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f,359.999f),1f,1f)))
    AlertDialog(onDismissRequest=onDismiss,title={Text(label)},text={
        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Canvas(Modifier.fillMaxWidth().widthIn(max=230.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                .semantics {contentDescription=svDescription}.pickPoint {x,y->update(h,x,1f-y)}) {
                drawRect(Brush.horizontalGradient(listOf(Color.White,pure)))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent,Color.Black)))
                val point=Offset(s*size.width,(1f-v)*size.height)
                drawCircle(Color.Black,8.dp.toPx(),point,style=Stroke(3.dp.toPx()))
                drawCircle(Color.White,8.dp.toPx(),point,style=Stroke(1.5.dp.toPx()))
            }
            Canvas(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp))
                .semantics {contentDescription=hueDescription}.pickPoint {x,_->update(x*359.999f,s,v)}) {
                drawRect(Brush.horizontalGradient(listOf(Color.Red,Color.Yellow,Color.Green,Color.Cyan,Color.Blue,Color.Magenta,Color.Red)))
                val x=(h/360f)*size.width
                drawLine(Color.Black,Offset(x,0f),Offset(x,size.height),5.dp.toPx())
                drawLine(Color.White,Offset(x,0f),Offset(x,size.height),2.dp.toPx())
            }
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(selected).border(1.dp,MaterialTheme.colorScheme.outline,RoundedCornerShape(10.dp)))
                OutlinedTextField(value=input,onValueChange={raw->
                    input=raw.take(7)
                    parseHex(input)?.let {c->
                        val a=FloatArray(3);android.graphics.Color.colorToHSV(c.toArgb(),a)
                        h=a[0];s=a[1];v=a[2]
                    }
                },modifier=Modifier.weight(1f),label={Text("HEX")},singleLine=true,isError=parseHex(input)==null)
            }
        }
    },confirmButton={TextButton(onClick={onApply(selected)},enabled=parseHex(input)!=null) {Text(tr("Zastosuj"))}},
        dismissButton={TextButton(onClick=onDismiss){Text(tr("Anuluj"))}})
}
