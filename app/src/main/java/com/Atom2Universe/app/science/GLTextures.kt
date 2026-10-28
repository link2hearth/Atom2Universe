package com.Atom2Universe.app.science

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.opengl.GLES20
import java.io.IOException
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future

/** Textures d'astres des vues 3D : décodées hors du fil GL, sûres sur un GPU sans NPOT. */
internal object GLTextures {
    private val decoder: ExecutorService by lazy {
        Executors.newFixedThreadPool(2) { task ->
            Thread(task, "ScienceTextureDecode").apply { isDaemon = true }
        }
    }
    @Volatile private var npotChecked = false
    @Volatile private var npotSupported = true

    /**
     * Les JPEG restent en ARGB_8888 : en RGB_565, les dégradés lisses (océans, géantes de
     * glace) se découpaient en bandes visibles une fois éclairés.
     */
    fun decode(context: Context, asset: String): Bitmap? = try {
        context.assets.open(asset).use { stream ->
            BitmapFactory.decodeStream(stream, null, BitmapFactory.Options().apply { inScaled = false })
        }
    } catch (_: IOException) {
        null
    }

    /** Lance le décodage en arrière-plan, pendant que la surface GL se prépare. */
    fun prefetch(context: Context, assets: Collection<String>, into: MutableMap<String, Future<Bitmap?>> = ConcurrentHashMap()):
        MutableMap<String, Future<Bitmap?>> {
        val app = context.applicationContext
        assets.forEach { asset ->
            if (!into.containsKey(asset)) into[asset] = decoder.submit(Callable { decode(app, asset) })
        }
        return into
    }

    /** L'image préchargée si elle existe, sinon décodée sur place (perte du contexte GL). */
    fun take(context: Context, prefetched: MutableMap<String, Future<Bitmap?>>, asset: String): Bitmap? {
        val pending = prefetched.remove(asset)
        if (pending != null) {
            try { return pending.get() } catch (_: Exception) { }
        }
        return decode(context, asset)
    }

    /**
     * Fil GL : sans l'extension NPOT (OpenGL ES 2 strict), une texture de 1000 × 500 en REPEAT
     * avec mipmaps est incomplète et s'affiche noire. On la ramène alors à des puissances de 2.
     */
    fun uploadable(bitmap: Bitmap): Bitmap {
        if (!npotChecked) {
            val version = GLES20.glGetString(GLES20.GL_VERSION).orEmpty()
            val extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS).orEmpty()
            npotSupported = version.startsWith("OpenGL ES 3") || extensions.contains("GL_OES_texture_npot")
            npotChecked = true
        }
        if (npotSupported) return bitmap
        val w = nearestPowerOfTwo(bitmap.width)
        val h = nearestPowerOfTwo(bitmap.height)
        if (w == bitmap.width && h == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, w, h, true).also { bitmap.recycle() }
    }

    private fun nearestPowerOfTwo(n: Int): Int {
        val low = Integer.highestOneBit(n.coerceAtLeast(1))
        return if (n - low > low / 2) low * 2 else low
    }
}
