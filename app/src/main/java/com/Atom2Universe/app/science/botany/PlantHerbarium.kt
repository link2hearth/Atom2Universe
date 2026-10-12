package com.Atom2Universe.app.science.botany

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.Atom2Universe.app.A2UApplication

/** Text-free complete plates. Organ views retain their independent vector studies. */
internal object PlantHerbarium {
    val speciesIds = setOf("robur", "bellis", "lolium", "castanea", "urtica", "digitalis",
        "allium_ursinum", "convallaria_majalis", "daucus", "laurus_nobilis")

    fun hasPlate(id: String, mode: PlantView) = mode == PlantView.HABIT && id in speciesIds

    fun decode(key: PlantImageKey): Bitmap {
        val assets = A2UApplication.appContext.assets
        val path = "botany/herbarium/${key.id}.png"
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        assets.open(path).use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid herbarium plate: ${key.id}" }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inSampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (inSampleSize * 2) >= key.edge) inSampleSize *= 2
        }
        return assets.open(path).use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
    }
}
