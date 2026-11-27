package com.moonlight.school

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.roundToInt

/** Eight persistent slots: left top to bottom, then right top to bottom. */
internal object GradientMesh {
    private val hex = Regex("#[0-9a-fA-F]{6}")
    fun palette(first:String,second:String,saved:List<String>):List<String> {
        val a=first.takeIf {hex.matches(it)} ?: "#151322"
        val b=second.takeIf {hex.matches(it)} ?: "#352254"
        return List(8) { i -> saved.getOrNull(i)?.takeIf {hex.matches(it)} ?: if(i<4) a else b }
    }
    fun leftCount(count:Int)=(count.coerceIn(2,8)+1)/2
    fun rightCount(count:Int)=count.coerceIn(2,8)/2

    private fun mix(a:Int,b:Int,t:Float):Int {
        fun channel(shift:Int):Int {
            val x=(a ushr shift) and 255
            val y=(b ushr shift) and 255
            return (x+(y-x)*t).roundToInt().coerceIn(0,255)
        }
        return (0xff shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
    private fun column(colors:IntArray,start:Int,count:Int,y:Float):Int {
        if(count==1) return colors[start]
        val pos=y.coerceIn(0f,1f)*(count-1)
        val i=pos.toInt().coerceAtMost(count-2)
        return mix(colors[start+i],colors[start+i+1],pos-i)
    }
    fun sample(colors:IntArray,count:Int,x:Float,y:Float):Int =
        mix(column(colors,0,leftCount(count),y),column(colors,4,rightCount(count),y),x.coerceIn(0f,1f))

    fun render(palette:List<String>,count:Int,angle:Float,w:Int,h:Int):IntArray {
        require(w>1 && h>1 && palette.size==8)
        val colors=palette.map { (0xff shl 24) or it.substring(1).toInt(16) }.toIntArray()
        val rad=angle.toDouble()*PI/180.0
        val c=cos(rad).toFloat()
        val s=sin(rad).toFloat()
        // Rotate the two-column mesh around its centre, scaling to fit its bounds.
        val extent=(abs(c)+abs(s)).coerceAtLeast(1f)
        return IntArray(w*h) { i ->
            val x=(i%w).toFloat()/(w-1)-.5f
            val y=(i/w).toFloat()/(h-1)-.5f
            sample(colors,count,.5f+(x*c+y*s)/extent,.5f+(-x*s+y*c)/extent)
        }
    }
}
