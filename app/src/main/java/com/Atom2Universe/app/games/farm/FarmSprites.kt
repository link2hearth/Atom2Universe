package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF

class FarmSprites(private val context: Context) {
    private val livestockAtlas by lazy {
        val json = context.assets.open("generated/garden/livestock/atlas.json").bufferedReader().use {
            org.json.JSONObject(it.readText()).getJSONArray("sprites")
        }
        (0 until json.length()).associate { i ->
            val entry = json.getJSONObject(i); val r = entry.getJSONArray("rect")
            entry.getString("id") to (entry.getString("sheet") to Rect(r.getInt(0), r.getInt(1), r.getInt(0) + r.getInt(2), r.getInt(1) + r.getInt(3)))
        }
    }
    fun livestock(canvas: Canvas, id: String, target: RectF) {
        val (name, source) = livestockAtlas.getValue(id)
        val bitmap = sheet("livestock/$name")
        val scale = minOf(target.width() / source.width(), target.height() / source.height())
        val w = source.width() * scale; val h = source.height() * scale
        canvas.drawBitmap(bitmap, source, RectF(target.centerX() - w / 2, target.bottom - h,
            target.centerX() + w / 2, target.bottom), paint)
    }
    private val sheets = mutableMapOf<String, Bitmap>()
    private val bounds = mutableMapOf<String, Rect>()
    private val paint = Paint().apply { isFilterBitmap = false; isAntiAlias = false }

    private fun sheet(name: String): Bitmap = sheets.getOrPut(name) {
        context.assets.open("generated/garden/$name").use { BitmapFactory.decodeStream(it) }
            ?: error("Invalid farm sprite sheet: $name")
    }

    fun crop(canvas: Canvas, crop: FarmCrop, variant: Int, stage: Int, target: RectF) {
        val bitmap = sheet(crop.sheet)
        val rows = if (crop.tree) 6 else 8
        val row = crop.row + variant
        val key = "${crop.name}:$variant:$stage"
        val source = bounds.getOrPut(key) {
            val cell = Rect(stage * bitmap.width / 5, row * bitmap.height / rows,
                (stage + 1) * bitmap.width / 5, (row + 1) * bitmap.height / rows)
            // Alpha bounds remove export padding and align each sprite to the soil surface.
            var left = cell.right; var top = cell.bottom; var right = cell.left; var bottom = cell.top
            for (y in cell.top until cell.bottom) for (x in cell.left until cell.right) {
                if ((bitmap.getPixel(x, y) ushr 24) > 128) {
                    left = minOf(left, x); top = minOf(top, y)
                    right = maxOf(right, x + 1); bottom = maxOf(bottom, y + 1)
                }
            }
            if (right > left && bottom > top) Rect(left, top, right, bottom) else cell
        }
        val cellWidth = bitmap.width / 5f
        val cellHeight = bitmap.height / rows.toFloat()
        val scale = minOf(target.width() / cellWidth, target.height() / cellHeight)
        val w = source.width() * scale
        val h = source.height() * scale
        canvas.drawBitmap(bitmap, source, RectF(target.centerX() - w / 2, target.bottom - h,
            target.centerX() + w / 2, target.bottom), paint)
    }

    fun grass(canvas: Canvas, target: RectF) {
        val bitmap = sheet("garden_environment_v1.png")
        // Interior of the first tile avoids the irregular generated tile edges.
        canvas.drawBitmap(bitmap, Rect(55, 60, 280, 280), target, paint)
    }

    fun farmstead(canvas: Canvas, target: RectF) {
        val bitmap = sheet("garden_farmstead_v1.png")
        canvas.drawBitmap(bitmap, null, target, paint)
    }

    /** Check actual alpha, so scenery can fill the transparent silhouette without hiding the house. */
    fun farmsteadTransparent(area: RectF, target: RectF): Boolean {
        if (!RectF.intersects(area, target)) return true
        val bitmap = sheet("garden_farmstead_v1.png")
        val sx = bitmap.width / target.width()
        val sy = bitmap.height / target.height()
        val left = ((area.left - target.left) * sx).toInt().coerceIn(0, bitmap.width)
        val top = ((area.top - target.top) * sy).toInt().coerceIn(0, bitmap.height)
        val right = kotlin.math.ceil((area.right - target.left) * sx).toInt().coerceIn(0, bitmap.width)
        val bottom = kotlin.math.ceil((area.bottom - target.top) * sy).toInt().coerceIn(0, bitmap.height)
        for (y in top until bottom) for (x in left until right) {
            if ((bitmap.getPixel(x, y) ushr 24) > 24) return false
        }
        return true
    }

    fun environment(canvas: Canvas, column: Int, row: Int, target: RectF) {
        val bitmap = sheet("garden_environment_v1.png")
        // The supplied sheet has a 4 × 4 grid with transparent padding around objects.
        canvas.drawBitmap(bitmap, Rect(column * bitmap.width / 4, row * bitmap.height / 4,
            (column + 1) * bitmap.width / 4, (row + 1) * bitmap.height / 4), target, paint)
    }
}
