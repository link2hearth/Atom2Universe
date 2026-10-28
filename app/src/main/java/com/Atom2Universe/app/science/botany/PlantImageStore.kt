package com.Atom2Universe.app.science.botany

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class PlantImageKey(val id:String,val mode:PlantView,val edge:Int,val compact:Boolean,val image:Boolean=false)

/** CPU rasterization never runs in View.onDraw. At most two jobs allocate a raster at once. */
internal class PlantImageStore(
    private val dispatcher:CoroutineDispatcher=Dispatchers.Default.limitedParallelism(2),
    maxBytes:Int=12*1024*1024,
    private val render:(PlantSpecies,PlantImageKey)->Bitmap={species,key->
        if (PlantHerbarium.hasPlate(key.id)) PlantHerbarium.decode(key) else drawing(species,key)
    }
) {
    private val cache=object:LruCache<PlantImageKey,Bitmap>(maxBytes) {
        override fun sizeOf(key:PlantImageKey,value:Bitmap)=value.allocationByteCount
        // Published bitmaps may still be held by a View or RenderThread. Let their last owner release them.
    }
    fun peek(key:PlantImageKey):Bitmap?=cache.get(canonical(key))
    internal val bytes get()=cache.size()

    suspend fun load(species:PlantSpecies,requested:PlantImageKey):Bitmap=withContext(dispatcher) {
        val key=canonical(requested)
        currentCoroutineContext().ensureActive()
        cache.get(key)?.let{return@withContext it}
        val bitmap=render(species,key)
        currentCoroutineContext().ensureActive()
        bitmap.prepareToDraw()
        cache.put(key,bitmap)
        bitmap
    }

    companion object {
        val shared=PlantImageStore()
        /** A herbarium plate is the whole specimen: every view and card shares one decoded raster per size. */
        private fun canonical(key:PlantImageKey)=if(key.image) key.copy(mode=PlantView.HABIT,compact=false) else key
        /** Species without a verified plate keep their trait-driven drawing, in the plate's 300-unit frame. */
        private fun drawing(species:PlantSpecies,key:PlantImageKey)=
            Bitmap.createBitmap(key.edge,key.edge,Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.rgb(244,240,227))
                val canvas=Canvas(this)
                canvas.scale(key.edge/300f,key.edge/300f)
                PlantPainter(canvas,species,key.compact).draw(key.mode)
            }
        fun edge(pixels:Float,compact:Boolean):Int = if(compact) 256 else
            (((pixels.toInt()+127)/128)*128).coerceIn(384,768)
    }
}
