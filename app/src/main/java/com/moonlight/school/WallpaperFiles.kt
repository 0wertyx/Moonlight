package com.moonlight.school

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.AtomicFile
import java.io.File

/** Only decoded images, copied into app-private storage. No external grants or network. */
internal object WallpaperFiles {
    private const val NAME = "moonlight_wallpaper.jpg"
    fun load(context:Context,name:String):Bitmap? = if(name.startsWith("wallpaper-")) {
        runCatching {BitmapFactory.decodeFile(File(context.filesDir,NAME).absolutePath)}.getOrNull()
    } else null
    fun import(context:Context,uri:Uri):String? = runCatching {
        val resolver=context.contentResolver
        val bounds=BitmapFactory.Options().apply {inJustDecodeBounds=true}
        resolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,bounds)}
        require(bounds.outWidth in 1..50000 && bounds.outHeight in 1..50000)
        var sample=1
        while(maxOf(bounds.outWidth,bounds.outHeight)/sample>2048) sample*=2
        val bitmap=resolver.openInputStream(uri)?.use {BitmapFactory.decodeStream(it,null,BitmapFactory.Options().apply {inSampleSize=sample})} ?: error("decode")
        try {
            val file=AtomicFile(File(context.filesDir,NAME))
            val stream=file.startWrite()
            try {
                require(bitmap.compress(Bitmap.CompressFormat.JPEG,90,stream))
                file.finishWrite(stream)
            } catch(e:Exception) {file.failWrite(stream);throw e}
        } finally {bitmap.recycle()}
        "wallpaper-"+java.util.UUID.randomUUID().toString()
    }.getOrNull()
}
