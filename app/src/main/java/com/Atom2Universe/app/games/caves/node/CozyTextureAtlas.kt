package com.Atom2Universe.app.games.caves.node

import android.content.res.AssetManager
import android.graphics.*

/** Crops the authored atlases and derives material tints without duplicating PNG assets. */
internal class CozyTextureAtlas(private val assets: AssetManager, private val size: Int) : AutoCloseable {
    private val sheets = HashMap<String, Bitmap>()
    private fun sheet(name: String) = sheets.getOrPut(name) {
        assets.open("caves/textures/$name.png").use { BitmapFactory.decodeStream(it) }
    }

    fun texture(name: String): Bitmap {
        val parts = name.split(':')
        val family = parts[1]
        val tile = parts[2].toInt()
        val source = sheet(when (family) {
            "flora" -> "flora"
            "groundcover" -> "groundcover"
            "utility", "glass" -> "utility"
            else -> "materials"
        })
        // Integer endpoints also support generated dimensions not divisible by four.
        val src = Rect(tile % 4 * source.width / 4, tile / 4 * source.height / 4,
            (tile % 4 + 1) * source.width / 4, (tile / 4 + 1) * source.height / 4)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        if (parts.size > 3) {
            val color = Color.parseColor("#${parts[3]}")
            // Preserve luminance detail while imposing a controlled material palette.
            var luminance = 0f; var samples = 0
            for (y in src.top until src.bottom step 8) for (x in src.left until src.right step 8) {
                val pixel = source.getPixel(x, y)
                luminance += .299f * Color.red(pixel) + .587f * Color.green(pixel) + .114f * Color.blue(pixel)
                samples++
            }
            val mean = (luminance / samples.coerceAtLeast(1)).coerceAtLeast(1f)
            val r = Color.red(color) * .72f / mean
            val g = Color.green(color) * .72f / mean
            val b = Color.blue(color) * .72f / mean
            paint.colorFilter = ColorMatrixColorFilter(floatArrayOf(
                .299f*r,.587f*r,.114f*r,0f,Color.red(color)*.28f,
                .299f*g,.587f*g,.114f*g,0f,Color.green(color)*.28f,
                .299f*b,.587f*b,.114f*b,0f,Color.blue(color)*.28f,
                0f,0f,0f,1f,0f))
        }
        if (family == "flora" || family == "groundcover") {
            if (family == "groundcover") {
                // Ignore the atlas gutter so tips from adjacent cells cannot leak into a sprite.
                val cellHeight = src.height()
                src.top += cellHeight / 25
                src.bottom -= cellHeight / 20
            }
            // Trim transparent feet so crossed sprites stand directly on their support block.
            while (src.bottom > src.top + 1 && (src.left until src.right).none {
                    Color.alpha(source.getPixel(it, src.bottom - 1)) > 180 }) src.bottom--
        }
        canvas.drawBitmap(source, src, Rect(0, 0, size, size), paint)
        paint.colorFilter = null
        if (family == "ore") {
            val mineral = Color.parseColor("#${parts[4]}")
            val points = arrayOf(9 to 13, 38 to 7, 48 to 35, 21 to 43, 7 to 55, 32 to 27)
            for ((x, y) in points) {
                val u = x * size / 64f; val v = y * size / 64f; val d = size / 12f
                paint.color = Color.rgb(Color.red(mineral)*2/3, Color.green(mineral)*2/3, Color.blue(mineral)*2/3)
                canvas.drawRect(u-1, v-1, u+d+1, v+d+1, paint)
                paint.color = mineral; canvas.drawRect(u, v, u+d, v+d, paint)
                paint.color = Color.argb(125,255,255,255); canvas.drawRect(u, v, u+d, v+size/40f, paint)
            }
        }
        if (family == "cloth") {
            paint.color = Color.argb(18, 255, 255, 255)
            for (y in 0 until size step 4) canvas.drawLine(0f,y.toFloat(),size.toFloat(),y.toFloat(),paint)
            paint.color = Color.argb(15, 30, 20, 45)
            for (x in 0 until size step 4) canvas.drawLine(x.toFloat(),0f,x.toFloat(),size.toFloat(),paint)
        }
        if (family == "leaf") {
            // Small, stable openings retain the airy character of voxel foliage.
            for (y in 0 until size) for (x in 0 until size)
                if ((x / 3 * 31 + y / 3 * 17) % 37 == 0) out.setPixel(x, y, Color.TRANSPARENT)
        }
        if (family == "glass") {
            // Clear panes with a slim frame and sparse reflections; the block shader uses alpha cutout.
            val border = (size / 20).coerceAtLeast(1)
            for (y in border until size - border) for (x in border until size - border) {
                val reflection = x + y in size / 2..size / 2 + 1 || x + y in size / 2 + 5..size / 2 + 6
                if (!reflection) out.setPixel(x, y, Color.TRANSPARENT)
            }
        }
        if (family == "cap") {
            val capTile = parts[4].toInt()
            val capSource = parts.getOrNull(5)?.let { sheet(it) } ?: source
            val cap = Rect(capTile % 4 * capSource.width / 4, capTile / 4 * capSource.height / 4,
                (capTile % 4 + 1) * capSource.width / 4, (capTile / 4 + 1) * capSource.height / 4)
            canvas.drawBitmap(capSource, cap, Rect(0, 0, size, size / 5), paint)
        }
        return out
    }

    override fun close() { sheets.values.forEach { it.recycle() }; sheets.clear() }
}
