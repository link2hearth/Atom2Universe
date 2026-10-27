package com.Atom2Universe.app.games.glutton

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Theme
import java.io.IOException
import java.util.concurrent.Executors

/**
 * Optional WebP pages (assets/glutton/<theme>_<page>.webp, 6 × 4 square cells, see tools/glutton), decoded off
 * the frame thread. The current ring's theme and the one before it stay decoded; a missing page simply
 * leaves the code-drawn stand-ins.
 */
internal class GluttonAtlas(private val context: Context) {
    @Volatile private var pages: Array<Array<Bitmap?>> = Array(Theme.entries.size) { arrayOfNulls(GluttonCatalog.pageCount(Theme.entries[it])) }
    private val source = Rect()
    private val executor = Executors.newSingleThreadExecutor { Thread(it, "glutton-atlas").apply { isDaemon = true } }
    private var wanted: Theme? = null
    @Volatile private var released = false

    fun prepare(theme: Theme) {
        if (wanted == theme || released) return
        wanted = theme
        val keep = setOf(theme, Theme.entries.getOrNull(theme.ordinal - 1))
        executor.execute {
            val next = Array(pages.size) { pages[it].copyOf() }
            for (t in Theme.entries) for (page in next[t.ordinal].indices) {
                if (t !in keep) next[t.ordinal][page] = null
                else if (next[t.ordinal][page] == null) next[t.ordinal][page] = decode(t, page)
            }
            if (!released) pages = next
        }
    }

    private fun decode(theme: Theme, page: Int): Bitmap? = try {
        context.assets.open("glutton/${theme.name.lowercase()}_$page.webp").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        }
    } catch (_: IOException) { null }

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
        wanted = null
    }
}
