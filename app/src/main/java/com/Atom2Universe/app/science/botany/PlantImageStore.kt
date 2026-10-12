package com.Atom2Universe.app.science.botany

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.LruCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class PlantImageKey(val id:String,val mode:PlantView,val edge:Int,val compact:Boolean)

/** CPU rasterization never runs in View.onDraw. At most two jobs allocate a raster at once. */
internal class PlantImageStore(
    private val dispatcher:CoroutineDispatcher=Dispatchers.Default.limitedParallelism(2),
    maxBytes:Int=12*1024*1024,
    private val render:(PlantSpecies,PlantImageKey)->Bitmap={species,key->
        if (PlantHerbarium.hasPlate(key.id, key.mode)) PlantHerbarium.decode(key) else
        Bitmap.createBitmap(key.edge,key.edge,Bitmap.Config.ARGB_8888).also { bitmap->
            val canvas=Canvas(bitmap);canvas.scale(key.edge/300f,key.edge/300f)
            PlantPainter(canvas,species,key.compact).draw(key.mode)
        }
    }
) {
    private val cache=object:LruCache<PlantImageKey,Bitmap>(maxBytes) {
        override fun sizeOf(key:PlantImageKey,value:Bitmap)=value.allocationByteCount
        // Published bitmaps may still be held by a View or RenderThread. Let their last owner release them.
    }
    fun peek(key:PlantImageKey):Bitmap?=cache.get(key)
    internal val bytes get()=cache.size()

    suspend fun load(species:PlantSpecies,key:PlantImageKey):Bitmap=withContext(dispatcher) {
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
        fun edge(pixels:Float,compact:Boolean):Int = if(compact) 256 else
            (((pixels.toInt()+127)/128)*128).coerceIn(384,768)
    }
}
