package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import kotlin.math.cos
import kotlin.random.Random

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

    fun crop(canvas: Canvas, crop: FarmCrop, variant: Int, stage: Int, target: RectF,
             growth: Float? = null, windTime: Float? = null, artVariant: Int = variant) {
        // Crops ported to procedural Kotlin drawing (see FarmPlantArt) bypass the sprite sheet
        // entirely; every caller - field, harvest minigame, shop and inventory previews, produce
        // icon - already goes through this one function, so nothing else needs to change.
        if (FarmPlantArt.supports(crop)) {
            FarmPlantArt.draw(canvas, crop, artVariant, growth ?: (stage / 4f), target, windTime)
            return
        }
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

    fun flower(canvas: Canvas, sheetName: String, row: Int, stage: Int, target: RectF) {
        val bitmap = sheet(sheetName)
        val rows = 8
        val key = "$sheetName:$row:$stage"
        val source = bounds.getOrPut(key) {
            val cell = Rect(stage * bitmap.width / 5, row * bitmap.height / rows,
                (stage + 1) * bitmap.width / 5, (row + 1) * bitmap.height / rows)
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

    /**
     * The reference design paints its whole meadow as one continuous field - `sin(x/31+y/48) +
     * sin(y/22-x/67) + noise` evaluated straight in world coordinates - so nothing ever repeats and
     * there is nothing to seam. A sprite-sheet lawn had to fall back on a handful of interchangeable
     * tiles instead; now that the source is code, not a PNG, there is no reason to keep that
     * fallback. Each on-screen tile is still baked once into its own small cached bitmap - the
     * camera must not repaint the world from scratch every frame - but the wave inside it is
     * sampled at that tile's true world position, so neighbouring tiles pick up the pattern exactly
     * where the last one left off instead of each showing an identical, recognisable blob.
     */
    private val grassTiles = mutableMapOf<Long, Bitmap>()
    private fun tileKey(column: Int, row: Int) = (column.toLong() shl 32) or (row.toLong() and 0xFFFFFFFFL)

    /**
     * Every one of these runs through a raw pixel index, never a Canvas draw call. A camera pan
     * can reveal dozens of never-seen tiles in a single frame, each baked on the spot - going
     * through drawRect()/drawCircle() thousands of times per tile (their per-call overhead, not
     * the pixel count, is what's expensive) was the actual first-paint stutter; an index write has
     * none of that overhead, so baking a tile is now pure, cheap arithmetic.
     */
    private fun buildGrassTile(column: Int, row: Int): Bitmap {
        // One art pixel spans four world units, like the coarse dirt texture. The camera
        // enlarges these texels without filtering instead of shrinking 80 noisy texels.
        val size = GRASS_TILE / 4
        val pixels = IntArray(size * size)
        val random = Random(column * -0x61c88647 xor row * 0x9e3779b1.toInt())
        fun setPixel(x: Int, y: Int, color: Int) { if (x in 0 until size && y in 0 until size) pixels[y * size + x] = color }
        // Sampled every 2×2 block, matching the reference's own grain rather than every single
        // pixel - a quarter of the trig calls for a texture that reads identically.
        var ly = 0
        while (ly < size) {
            var lx = 0
            while (lx < size) {
                val wx = (column * size + lx).toDouble(); val wy = (row * size + ly).toDouble()
                val wave = kotlin.math.sin(wx / 31.0 + wy / 48.0) + kotlin.math.sin(wy / 22.0 - wx / 67.0)
                val n = wave + random.nextFloat() * .6
                val color = GRASS_PALETTE[kotlin.math.floor(n + 2.4).toInt().coerceIn(0, GRASS_PALETTE.size - 1)]
                setPixel(lx, ly, color); setPixel(lx + 1, ly, color)
                setPixel(lx, ly + 1, color); setPixel(lx + 1, ly + 1, color)
                lx += 2
            }
            ly += 2
        }
        // A one-time bake costs nothing once the tile is cached, so most of the meadow's density -
        // fine speckle, small static blades, the occasional flower - lives here rather than being
        // redrawn live every frame; only a couple of extra blades per tile actually sway (see
        // grass() below), which is what keeps the wind cheap while the ground still reads as dense.
        repeat((size * size * .04f).toInt()) {
            val x = random.nextInt(size); val y = random.nextInt(size)
            val color = GRASS_SPECKLE[random.nextInt(GRASS_SPECKLE.size)]
            val len = random.nextInt(1, 4)
            for (i in 0 until len) setPixel(x + i, y, color)
        }
        repeat(1) {
            val x = 4 + random.nextInt(size - 8); val y = 10 + random.nextInt(size - 14)
            val h = 4 + random.nextInt(5)
            for (t in 0..h) {
                val f = t / h.toFloat()
                setPixel(x - (2 * f).toInt(), y - t, Color.rgb(0x4c, 0x99, 0x58))
                setPixel(x + (2 * f).toInt(), y - (t * .8f).toInt(), Color.rgb(0x5c, 0xa7, 0x54))
            }
        }
        // Bitmap.createBitmap(pixels, ...) returns an immutable bitmap, which Canvas refuses to
        // wrap - build a blank mutable one instead and fill it with the computed pixels.
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /** The whole meadow shares one travelling gust: left to right, a 16-second cycle. */
    fun windAt(x: Float, timeSeconds: Float): Float {
        val phase = 2.0 * Math.PI * (x - WIND_SPEED * timeSeconds) / WIND_WAVELENGTH
        val gust = 0.5 + 0.5 * cos(phase)
        return (WIND_STRENGTH * gust * gust).toFloat()
    }

    /** A single leaning tuft of grass, its base fixed to the ground and only its blades swaying. */
    private val tuftSprites = mutableMapOf<Int, Bitmap>()
    private fun drawTuft(canvas: Canvas, x: Float, y: Float, height: Float, sway: Float, flower: Boolean, seed: Int) {
        val h = (height / 2f).toInt().coerceIn(3, 7)
        val bend = kotlin.math.round(sway).toInt().coerceIn(0, 2)
        val color = seed and 3
        val key = h * 32 + bend * 8 + (if (flower) 4 else 0) + color
        val bitmap = tuftSprites.getOrPut(key) {
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).also {
                drawTuftPixels(Canvas(it), 7f, 12f, h.toFloat(), bend.toFloat(), flower, color)
            }
        }
        canvas.drawBitmap(bitmap, null, RectF(x - 14f, y - 24f, x + 18f, y + 8f), paint)
    }
    private fun drawTuftPixels(canvas: Canvas, x: Float, y: Float, height: Float, sway: Float, flower: Boolean, seed: Int) {
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(0x69, 0xa6, 0x57)
        canvas.drawRect(x - 3f, y, x + 4f, y + 2f, paint)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f
        paint.color = Color.rgb(0x4c, 0x99, 0x58); canvas.drawLine(x, y, x + sway, y - height, paint)
        paint.color = Color.rgb(0xa8, 0xdf, 0x7b); canvas.drawLine(x - 1f, y, x - 4f + sway, y - height + 2f, paint)
        paint.color = Color.rgb(0x5c, 0xa7, 0x54); canvas.drawLine(x + 1f, y, x + 4f + sway, y - height + 1f, paint)
        paint.style = Paint.Style.FILL
        if (flower) {
            val fx = x + sway; val fy = y - height - 1f
            paint.color = GRASS_FLOWERS[seed and 3]
            canvas.drawRect(fx - 2f, fy - 1f, fx + 3f, fy + 2f, paint)
            canvas.drawRect(fx - 1f, fy - 2f, fx + 2f, fy + 3f, paint)
            paint.color = Color.rgb(0xff, 0xe2, 0x8b); canvas.drawRect(fx, fy, fx + 1f, fy + 1f, paint)
        }
    }

    fun grass(canvas: Canvas, target: RectF, column: Int, row: Int, windTime: Float = 0f) {
        val bitmap = grassTiles.getOrPut(tileKey(column, row)) { buildGrassTile(column, row) }
        canvas.drawBitmap(bitmap, null, target, paint)
        // Three independent slots per tile, each very likely to carry a loose tuft that leans with
        // the shared wind field. Most of the meadow's density is baked into the tile itself (free);
        // these are only the blades that actually need to move every frame, so the count stays
        // modest even though the visible result reads as a dense, swaying meadow.
        for (slot in 0 until 3) {
            var tuft = column * 0x2c1b3c6d xor row * 0x165667b1 xor (slot * 0x9e3779b1.toInt())
            tuft = tuft xor (tuft ushr 13)
            if ((tuft ushr 3) % 6 == 0) continue
            val fx = target.left + 8 + (tuft ushr 8) % 64
            val fy = target.top + 20 + (tuft ushr 15) % 52
            val height = 7f + (tuft ushr 21) % 8
            drawTuft(canvas, fx, fy, height, windAt(fx, windTime), (tuft ushr 25) % 4 == 0, tuft)
        }
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

    private companion object {
        const val GRASS_TILE = 80
        val GRASS_PALETTE = intArrayOf(
            Color.parseColor("#74bb60"), Color.parseColor("#7cc45f"), Color.parseColor("#82c862"),
            Color.parseColor("#8acd66"), Color.parseColor("#91d16b"), Color.parseColor("#86c565")
        )
        val GRASS_SPECKLE = intArrayOf(
            Color.parseColor("#91d16b"), Color.parseColor("#9bd574"), Color.parseColor("#6bb65c"),
            Color.parseColor("#80c262"), Color.parseColor("#5a9a4d")
        )
        val GRASS_FLOWERS = intArrayOf(
            Color.parseColor("#ffe8b0"), Color.parseColor("#ffd0df"), Color.parseColor("#e0d0ff"), Color.parseColor("#fff2d7")
        )
        const val WIND_SPEED = 20f
        const val WIND_WAVELENGTH = 320f
        const val WIND_STRENGTH = 1.7f
    }

    fun environment(canvas: Canvas, column: Int, row: Int, target: RectF) {
        val bitmap = sheet("garden_environment_v1.png")
        // The supplied sheet has a 4 × 4 grid with transparent padding around objects.
        canvas.drawBitmap(bitmap, Rect(column * bitmap.width / 4, row * bitmap.height / 4,
            (column + 1) * bitmap.width / 4, (row + 1) * bitmap.height / 4), target, paint)
    }
}
