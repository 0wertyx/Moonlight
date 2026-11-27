package com.moonlight.school

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.roundToInt

/** Eight persistent slots: left top to bottom, then right top to bottom. */
internal data class GradientPoint(val x: Float, val y: Float)

internal object GradientMesh {
    private val hex = Regex("#[0-9a-fA-F]{6}")
    fun palette(first:String,second:String,saved:List<String>):List<String> {
        val a=first.takeIf {hex.matches(it)} ?: "#151322"
        val b=second.takeIf {hex.matches(it)} ?: "#352254"
        return List(8) { i -> saved.getOrNull(i)?.takeIf {hex.matches(it)} ?: if(i<4) a else b }
    }
    fun leftCount(count:Int)=(count.coerceIn(2,8)+1)/2
    fun rightCount(count:Int)=count.coerceIn(2,8)/2

    fun activeIndices(count:Int):List<Int> =
        (0 until leftCount(count)).toList() + (4 until 4+rightCount(count)).toList()

    fun positions(count:Int, angle:Float, saved:List<GradientPoint?>):List<GradientPoint> {
        val rad=angle.toDouble()*PI/180.0
        val c=cos(rad).toFloat()
        val s=sin(rad).toFloat()
        val extent=(abs(c)+abs(s)).coerceAtLeast(1f)
        return List(8) { index ->
            saved.getOrNull(index)?.takeIf { it.x.isFinite() && it.y.isFinite() && it.x in 0f..1f && it.y in 0f..1f }
                ?: run {
                    val left=index<4
                    val n=if(left) leftCount(count) else rightCount(count)
                    val row=if(left) index else index-4
                    val x=if(left) -.5f else .5f
                    val y=if(n==1) 0f else row.coerceAtMost(n-1).toFloat()/(n-1)-.5f
                    GradientPoint((.5f+(x*c-y*s)/extent).coerceIn(0f,1f),(.5f+(x*s+y*c)/extent).coerceIn(0f,1f))
                }
        }
    }
    fun decodePositions(raw:String):List<GradientPoint?> = raw.split(";").take(8).map { token ->
        val parts=token.split(":")
        val x=parts.getOrNull(0)?.toFloatOrNull()
        val y=parts.getOrNull(1)?.toFloatOrNull()
        if(x!=null && y!=null && x.isFinite() && y.isFinite() && x in 0f..1f && y in 0f..1f) GradientPoint(x,y) else null
    }
    fun encodePositions(points:List<GradientPoint?>):String =
        List(8) { points.getOrNull(it)?.let { p -> "${p.x}:${p.y}" } ?: "-" }.joinToString(";")

    /** Smooth inverse-distance blend, exactly matching a control point at its center. */
    fun renderFree(palette:List<String>,count:Int,points:List<GradientPoint>,w:Int,h:Int):IntArray {
        require(w>1 && h>1 && palette.size==8 && points.size==8)
        val indices=activeIndices(count).toIntArray()
        val colors=palette.map {it.substring(1).toInt(16)}.toIntArray()
        val px=FloatArray(8) { points[it].x }
        val py=FloatArray(8) { points[it].y }
        val reds=IntArray(8) { (colors[it] ushr 16) and 255 }
        val greens=IntArray(8) { (colors[it] ushr 8) and 255 }
        val blues=IntArray(8) { colors[it] and 255 }
        return IntArray(w*h) { i ->
            val x=(i%w).toFloat()/(w-1)
            val y=(i/w).toFloat()/(h-1)
            var total=0f
            var red=0f; var green=0f; var blue=0f
            for(j in indices) {
                val dx=x-px[j]
                val dy=y-py[j]
                val weight=1f/(dx*dx+dy*dy+0.000001f)
                total+=weight
                red+=weight*reds[j]
                green+=weight*greens[j]
                blue+=weight*blues[j]
            }
            (0xff shl 24) or ((red/total).roundToInt().coerceIn(0,255) shl 16) or
                ((green/total).roundToInt().coerceIn(0,255) shl 8) or (blue/total).roundToInt().coerceIn(0,255)
        }
    }

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
