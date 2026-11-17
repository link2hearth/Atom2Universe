package com.Atom2Universe.app.games.colorstack

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.content.edit
import kotlin.math.max
import kotlin.random.Random

/**
 * Tableau peint derrière le jeu (assets/colorstack/backdrops/) : l'image couvre toute la vue,
 * recadrée au centre, sous un voile sombre propre à chaque tableau pour que les pièces restent
 * lisibles. Le tableau en cours est gardé ; une nouvelle partie en tire un autre.
 */
internal class Backdrop private constructor(
    context: Context,
    private val key: String,
    private val images: List<Pair<String, Float>>
) {
    companion object {
        private const val PREFS = "colorstack_backdrops"

        /** Les éprouvettes : laboratoires de savant fou. Voile = part d'assombrissement. */
        fun lab(context: Context) = Backdrop(context, "lab", listOf(
            "lab_1_tesla" to 0.10f,
            "lab_2_victorian" to 0.22f,
            "lab_3_bunker" to 0.18f
        ))

        /** Color Stack : la tour du sorcier. */
        fun wizard(context: Context) = Backdrop(context, "wizard", listOf(
            "wizard_1_library" to 0.08f,
            "wizard_2_cellar" to 0.08f,
            "wizard_3_observatory" to 0.08f
        ))
    }

    private val assets = context.applicationContext.assets
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private var index = prefs.getInt(key, -1).takeIf { it in images.indices }
        ?: Random.nextInt(images.size).also { prefs.edit { putInt(key, it) } }
    private var bitmap: Bitmap? = null
    private var failed = false

    /** Avec ou sans tableau (réglage du joueur, gardé d'une partie à l'autre). */
    var enabled = prefs.getBoolean("${key}_on", true)
        set(value) {
            field = value
            prefs.edit { putBoolean("${key}_on", value) }
            if (!value) bitmap = null
        }
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()

    /** Une nouvelle partie : un autre tableau que celui en cours. */
    fun next() {
        if (images.size > 1) index = (index + 1 + Random.nextInt(images.size - 1)) % images.size
        prefs.edit { putInt(key, index) }
        bitmap = null
        failed = false
    }

    fun draw(canvas: Canvas, w: Int, h: Int) {
        if (!enabled || w <= 0 || h <= 0) return
        val bmp = bitmap ?: load() ?: return
        val scale = max(w / bmp.width.toFloat(), h / bmp.height.toFloat())
        val sw = (w / scale).toInt().coerceAtMost(bmp.width)
        val sh = (h / scale).toInt().coerceAtMost(bmp.height)
        val sx = (bmp.width - sw) / 2
        val sy = (bmp.height - sh) / 2
        src.set(sx, sy, sx + sw, sy + sh)
        dst.set(0, 0, w, h)
        canvas.drawBitmap(bmp, src, dst, paint)
        val veil = (images[index].second.coerceIn(0f, 1f) * 255).toInt()
        canvas.drawColor(veil shl 24)
    }

    private fun load(): Bitmap? {
        if (failed) return null
        val decoded = runCatching {
            assets.open("colorstack/backdrops/${images[index].first}.webp").use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
        if (decoded == null) failed = true
        bitmap = decoded
        return decoded
    }
}
