package com.Atom2Universe.app.games.glutton

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Scene
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Sheet
import java.io.IOException

/**
 * Optional WebP sheets (assets/glutton/<sheet>.webp, 6 columns of square cells, see tools/glutton).
 * Only the current scene's sheets stay decoded; a missing sheet simply leaves the code-drawn stand-ins.
 */
internal class GluttonAtlas(private val context: Context) {
    private val sheets = arrayOfNulls<Bitmap>(Sheet.entries.size)
    private val source = Rect()
    private var loaded: Scene? = null

    fun prepare(scene: Scene) {
        if (loaded == scene) return
        loaded = scene
        for (sheet in Sheet.entries) {
            val wanted = sheet.scene == scene
            val current = sheets[sheet.ordinal]
            if (!wanted && current != null) { current.recycle(); sheets[sheet.ordinal] = null }
            if (wanted && current == null) sheets[sheet.ordinal] = decode(sheet)
        }
    }

    private fun decode(sheet: Sheet): Bitmap? = try {
        context.assets.open("glutton/${sheet.name.lowercase()}.webp").use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inScaled = false })
        }
    } catch (_: IOException) { null }

    fun has(sheet: Sheet) = sheets[sheet.ordinal] != null

    fun draw(canvas: Canvas, sheet: Sheet, cell: Int, target: RectF, paint: Paint): Boolean {
        val image = sheets[sheet.ordinal] ?: return false
        val size = image.width / GluttonCatalog.COLUMNS
        val column = cell % GluttonCatalog.COLUMNS; val row = cell / GluttonCatalog.COLUMNS
        if ((row + 1) * size > image.height) return false
        source.set(column * size, row * size, column * size + size, row * size + size)
        canvas.drawBitmap(image, source, target, paint)
        return true
    }

    fun release() {
        for (i in sheets.indices) { sheets[i]?.recycle(); sheets[i] = null }
        loaded = null
    }
}
