package com.Atom2Universe.app.science.mycology

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory

/** Optional text-free ImageGen plates. The code-rendered diagnostic views remain available. */
internal object FungusHerbarium {
    private val validId = Regex("[a-z0-9_]+")
    /** The bundled plates never change while the app runs: the folder is listed once. */
    @Volatile private var plates: Set<String>? = null

    fun hasPlate(context: Context, id: String): Boolean {
        if (!id.matches(validId)) return false
        val known = plates ?: context.assets.list("science/mycology/herbarium").orEmpty().toSet().also { plates = it }
        return "$id.webp" in known
    }

    fun decode(context: Context, id: String, targetEdge: Int = 768): Bitmap {
        require(id.matches(validId))
        val path = "science/mycology/herbarium/$id.webp"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid herbarium plate: $id" }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (inSampleSize * 2) >= targetEdge) inSampleSize *= 2
        }
        return context.assets.open(path).use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
    }
}
