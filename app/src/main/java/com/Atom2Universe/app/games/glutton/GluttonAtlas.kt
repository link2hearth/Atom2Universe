package com.Atom2Universe.app.games.glutton

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Theme
import com.Atom2Universe.app.games.glutton.GluttonWorld.Ground
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Optional WebP pages (assets/glutton/<theme>_<page>.webp, 6 × 4 square cells, see tools/glutton) and ground
 * textures (assets/glutton/ground_<name>.webp), decoded off the frame thread. The current era's pages and
 * those of the eras on either side stay decoded; a missing page simply leaves the code-drawn stand-ins.
 */
internal class GluttonAtlas(private val context: Context) {
    @Volatile private var pages: Array<Array<Bitmap?>> = Array(Theme.entries.size) { arrayOfNulls(GluttonCatalog.pageCount(Theme.entries[it])) }
    @Volatile private var grounds: Array<Bitmap?> = arrayOfNulls(Ground.entries.size)
    private val source = Rect()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "glutton-atlas").apply { isDaemon = true } }
    private var wanted: Theme? = null
    private var groundsAsked = false
    @Volatile private var released = false

    fun prepare(theme: Theme) {
        if (released) return
        if (!groundsAsked) {
            groundsAsked = true
            executor.execute {
                val loaded = arrayOfNulls<Bitmap>(Ground.entries.size)
                for (g in Ground.entries) loaded[g.ordinal] = decode("glutton/ground_${g.name.lowercase()}.webp", Bitmap.Config.RGB_565)
                if (!released) grounds = loaded
            }
        }
        if (wanted == theme) return
        wanted = theme
        val keep = Theme.entries.filter { kotlin.math.abs(it.ordinal - theme.ordinal) <= 1 }.toSet()
        executor.execute {
            val next = Array(pages.size) { pages[it].copyOf() }
            for (t in Theme.entries) for (page in next[t.ordinal].indices) {
                if (t !in keep) next[t.ordinal][page] = null
                else if (next[t.ordinal][page] == null) next[t.ordinal][page] = decode("glutton/${t.name.lowercase()}_$page.webp", Bitmap.Config.ARGB_8888)
            }
            if (!released) pages = next
        }
    }

    private fun decode(path: String, config: Bitmap.Config): Bitmap? = try {
        context.assets.open(path).use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false; inPreferredConfig = config })
                ?.also { bitmap -> bitmap.setHasMipMap(true) }
        }
    } catch (_: IOException) { null }

    fun ground(ground: Ground): Bitmap? = grounds[ground.ordinal]

    fun draw(canvas: Canvas, theme: Theme, page: Int, cell: Int, target: RectF, paint: Paint): Boolean {
        val image = pages[theme.ordinal].getOrNull(page) ?: return false
        val size = image.width / GluttonCatalog.COLUMNS
        val column = cell % GluttonCatalog.COLUMNS; val row = cell / GluttonCatalog.COLUMNS
        if ((row + 1) * size > image.height) return false
        source.set(column * size, row * size, column * size + size, row * size + size)
        canvas.drawBitmap(image, source, target, paint)
        return true
    }

    fun release() {
        released = true
        executor.shutdownNow()
        pages = Array(Theme.entries.size) { arrayOfNulls(0) }
        grounds = arrayOfNulls(Ground.entries.size)
        wanted = null
    }
}
