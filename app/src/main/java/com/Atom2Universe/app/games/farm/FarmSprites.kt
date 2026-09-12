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
             growth: Float? = null, windTime: Float? = null, artVariant: Int = variant,
             established: Boolean = false) {
        FarmPlantArt.draw(canvas, crop, artVariant, growth ?: (stage / 4f), target, windTime, established)
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
        val size = GRASS_TILE / 4
        val pixels = IntArray(size * size)
        grassTilePixels(column, row, pixels)
        // Bitmap.createBitmap(pixels, ...) returns an immutable bitmap, which Canvas refuses to
        // wrap - build a blank mutable one instead and fill it with the computed pixels.
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return bitmap
    }

    /**
     * Fills [pixels] with one tile's texels. Split out of [buildGrassTile] so the whole-map bake
     * ([bakeMeadow]) can write tile after tile into one big buffer instead of allocating a bitmap
     * per tile - and so it touches nothing this class caches, which is what lets it run off the
     * UI thread.
     */
    private fun grassTilePixels(column: Int, row: Int, pixels: IntArray) {
        // One art pixel spans four world units, like the coarse dirt texture. The camera
        // enlarges these texels without filtering instead of shrinking 80 noisy texels.
        val size = GRASS_TILE / 4
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
        // A short sprig, 2 to 4 texels - 8 to 16 world units. It used to be the TALLER of the two
        // kinds of blade the meadow grows, at 16 to 32, which put the big grass in the layer that
        // cannot move and the small grass in the one that sways: backwards, and it showed. The
        // sizes are now swapped with the loose tufts (see [tuftAt]), so what waves is what stands out.
        repeat(1) {
            val x = 4 + random.nextInt(size - 8); val y = 10 + random.nextInt(size - 14)
            val h = 2 + random.nextInt(3)
            for (t in 0..h) {
                val f = t / h.toFloat()
                setPixel(x - (2 * f).toInt(), y - t, Color.rgb(0x4c, 0x99, 0x58))
                setPixel(x + (2 * f).toInt(), y - (t * .8f).toInt(), Color.rgb(0x5c, 0xa7, 0x54))
            }
        }
    }


    /** A single leaning tuft of grass, its base fixed to the ground and only its blades swaying. */
    private val tuftSprites = mutableMapOf<Int, Bitmap>()
    /**
     * The tuft's own footprint in world units, anchored on the point [tuftAt] reports - which is
     * where its base sits, art pixel (7, 20) of a 16x24 sprite, at two world units per art pixel.
     */
    private fun tuftTarget(x: Float, y: Float, scale: Float) =
        RectF(x - 14f * scale, y - 40f * scale, x + 18f * scale, y + 8f * scale)
    private fun tuftSprite(cache: MutableMap<Int, Bitmap>, brush: Paint,
                           height: Float, sway: Float, flower: Boolean, seed: Int): Bitmap {
        // Two world units per art pixel. The sprite is 24 tall rather than 16 because these are now
        // the meadow's TALL blades; at h = 16 the tip reaches art row 4 and a flower still fits above it.
        val h = (height / 2f).toInt().coerceIn(8, 16)
        val bend = kotlin.math.round(sway).toInt().coerceIn(0, 2)
        val color = seed and 3
        val key = h * 32 + bend * 8 + (if (flower) 4 else 0) + color
        return cache.getOrPut(key) {
            Bitmap.createBitmap(16, 24, Bitmap.Config.ARGB_8888).also {
                drawTuftPixels(Canvas(it), brush, 7f, 20f, h.toFloat(), bend.toFloat(), flower, color)
            }
        }
    }
    private fun drawTuft(canvas: Canvas, x: Float, y: Float, height: Float, sway: Float,
                         flower: Boolean, seed: Int, scale: Float = 1f) {
        canvas.drawBitmap(tuftSprite(tuftSprites, paint, height, sway, flower, seed), null,
            tuftTarget(x, y, scale), paint)
    }
    private fun drawTuftPixels(canvas: Canvas, paint: Paint, x: Float, y: Float, height: Float, sway: Float, flower: Boolean, seed: Int) {
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

    fun grass(canvas: Canvas, target: RectF, column: Int, row: Int, windTime: Float = 0f, detailed: Boolean = true) {
        val bitmap = grassTiles.getOrPut(tileKey(column, row)) { buildGrassTile(column, row) }
        canvas.drawBitmap(bitmap, null, target, paint)
        // Three independent slots per tile, each very likely to carry a loose tuft that leans with
        // the shared wind field. Most of the meadow's density is baked into the tile itself (free);
        // these are only the blades that actually need to move every frame, so the count stays
        // modest even though the visible result reads as a dense, swaying meadow. Skipped past a
        // couple hundred visible tiles (zoomed far out): individual blades are sub-pixel there anyway.
        if (!detailed) return
        // Tuft placement and size are authored in world units, against a GRASS_TILE-wide tile. The
        // hub icon and the arcade field tile the meadow at their own smaller pitch, so everything
        // is scaled to whatever tile they asked for rather than stamped at its absolute size - at
        // 34 pixels a tile, an unscaled tuft would stand taller than the tile it grows in.
        val scale = target.width() / GRASS_TILE
        for (slot in 0 until 3) {
            val tuft = tuftAt(column, row, slot) ?: continue
            val fx = target.left + tuft.dx * scale; val fy = target.top + tuft.dy * scale
            drawTuft(canvas, fx, fy, tuft.height, windAt(fx, windTime), tuft.flower, tuft.seed, scale)
        }
    }

    /**
     * Where a tile's three tuft slots sit, if they carry one at all. The placement is pure hash, so
     * the live draw above and the whole-map bake below plant exactly the same blades in exactly the
     * same spots - the meadow must not shift when one takes over from the other.
     */
    private class Tuft(val dx: Float, val dy: Float, val height: Float, val flower: Boolean, val seed: Int)
    private fun tuftAt(column: Int, row: Int, slot: Int): Tuft? {
        var tuft = column * 0x2c1b3c6d xor row * 0x165667b1 xor (slot * 0x9e3779b1.toInt())
        tuft = tuft xor (tuft ushr 13)
        if ((tuft ushr 3) % 6 == 0) return null
        return Tuft(8f + (tuft ushr 8) % 64, 20f + (tuft ushr 15) % 52,
            16f + (tuft ushr 21) % 17, (tuft ushr 25) % 4 == 0, tuft)
    }

    /**
     * The meadow, painted ONCE into bitmaps laid out in WORLD coordinates at [MEADOW_SCALE] texels
     * per world unit, which the camera then stamps back with one drawBitmap per frame.
     *
     * It comes in two layers, and the split is what buys back the wind. The bed - grass, speckle,
     * baked blades - never moves, so it is baked flat and forgotten. The loose tufts and their
     * wildflowers DO move, so they get their own transparent layer: zoomed out the whole layer is
     * slid a world unit or two with the gust, and zoomed in it is left aside entirely while the
     * visible tufts are drawn live, blade by blade, bending exactly as they always did (see
     * [tufts]). Baking them into the bed instead would freeze the meadow solid.
     *
     * This replaces a screen-sized cache that was rebuilt several times a second: on a 1752x2800
     * tablet that came to 48 MB of pixels re-uploaded to the GPU every 150 ms, and the map stuttered
     * in step with it. That cost was the same whatever the meadow contained, which is also why
     * stripping detail out of it never helped.
     *
     * Touches only local caches and no field of this class, so it is safe to run off the UI thread.
     */
    fun bakeMeadowBed(columnStart: Int, rowStart: Int, columns: Int, rows: Int): Bitmap {
        val source = GRASS_TILE / 4
        val tile = (GRASS_TILE * MEADOW_SCALE).toInt()
        val step = tile / source
        val w = columns * tile; val h = rows * tile
        val pixels = IntArray(w * h)
        val cell = IntArray(source * source)
        for (row in 0 until rows) for (column in 0 until columns) {
            grassTilePixels(columnStart + column, rowStart + row, cell)
            // Each source texel becomes a step x step block. The camera already magnified those very
            // texels without filtering, so blowing them up here changes not one pixel on screen.
            val ox = column * tile; val oy = row * tile
            for (sy in 0 until source) for (sx in 0 until source) {
                val color = cell[sy * source + sx]
                for (dy in 0 until step) {
                    var index = (oy + sy * step + dy) * w + ox + sx * step
                    for (dx in 0 until step) pixels[index++] = color
                }
            }
        }
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, w, 0, 0, w, h)
        return bitmap
    }
    /** The loose tufts alone, upright, on transparent ground - the layer that gets slid. See [bakeMeadowBed]. */
    fun bakeMeadowTufts(columnStart: Int, rowStart: Int, columns: Int, rows: Int, mask: TuftMask): Bitmap {
        val tile = (GRASS_TILE * MEADOW_SCALE).toInt()
        val bitmap = Bitmap.createBitmap(columns * tile, rows * tile, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.scale(MEADOW_SCALE, MEADOW_SCALE)
        canvas.translate(-columnStart * GRASS_TILE.toFloat(), -rowStart * GRASS_TILE.toFloat())
        val brush = Paint().apply { isFilterBitmap = false; isAntiAlias = false }
        val cache = mutableMapOf<Int, Bitmap>()
        // One tile of bleed on every side: a tuft stands well above its own base, so the ones rooted
        // just outside still lean into this bitmap. They are clipped by its edges, which is right.
        forEachTuft(columnStart - 1, rowStart - 1, columns + 2, rows + 2) { column, row, slot, x, y, tuft ->
            if (mask.allows(column, row, slot))
                canvas.drawBitmap(tuftSprite(cache, brush, tuft.height, 0f, tuft.flower, tuft.seed),
                    null, tuftTarget(x, y, 1f), brush)
        }
        return bitmap
    }
    /**
     * The live, bending tufts over a range of tiles - the zoomed-in half of the deal [bakeMeadowBed]
     * describes. Each blade leans by the gust sampled at its own x, so the wind travels across the
     * meadow instead of the whole field nodding together. Only ever called for what is on screen:
     * at full zoom-out this would be thousands of draws per frame, which is the wall the baked
     * layer exists to avoid.
     */
    fun tufts(canvas: Canvas, columnStart: Int, rowStart: Int, columns: Int, rows: Int,
              windTime: Float, mask: TuftMask) {
        forEachTuft(columnStart, rowStart, columns, rows) { column, row, slot, x, y, tuft ->
            if (mask.allows(column, row, slot))
                drawTuft(canvas, x, y, tuft.height, windAt(x, windTime), tuft.flower, tuft.seed)
        }
    }
    private inline fun forEachTuft(columnStart: Int, rowStart: Int, columns: Int, rows: Int,
                                   action: (column: Int, row: Int, slot: Int, x: Float, y: Float, tuft: Tuft) -> Unit) {
        for (row in 0 until rows) for (column in 0 until columns) {
            val c = columnStart + column; val r = rowStart + row
            val left = c * GRASS_TILE.toFloat(); val top = r * GRASS_TILE.toFloat()
            for (slot in 0 until 3) {
                val tuft = tuftAt(c, r, slot) ?: continue
                action(c, r, slot, left + tuft.dx, top + tuft.dy, tuft)
            }
        }
    }

    /**
     * Which tuft slots are allowed to grow at all. Grass has no business sprouting through a
     * planting bed, a trodden path or the middle of a bush, but asking that question costs a walk
     * over every trail sample and every decoration on the map - far too much to repeat for a
     * thousand blades on every frame. The answer never changes, so it is settled once, off the UI
     * thread, and read back here as a single array lookup.
     */
    class TuftMask(val columnStart: Int, val rowStart: Int, val columns: Int, val rows: Int) {
        val bits = BooleanArray(columns * rows * 3)
        fun index(column: Int, row: Int, slot: Int) =
            ((row - rowStart) * columns + (column - columnStart)) * 3 + slot
        fun allows(column: Int, row: Int, slot: Int): Boolean =
            column >= columnStart && row >= rowStart &&
                column < columnStart + columns && row < rowStart + rows &&
                bits[index(column, row, slot)]
    }
    /** Settles [TuftMask] over a tile range; [clear] answers whether one world point may carry grass. */
    fun tuftMask(columnStart: Int, rowStart: Int, columns: Int, rows: Int,
                 clear: (x: Float, y: Float) -> Boolean): TuftMask {
        val mask = TuftMask(columnStart, rowStart, columns, rows)
        forEachTuft(columnStart, rowStart, columns, rows) { column, row, slot, x, y, _ ->
            mask.bits[mask.index(column, row, slot)] = clear(x, y)
        }
        return mask
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

    companion object {
        /**
         * The gust itself, 0 to 1: ONE wave, travelling left to right, shared by the entire farm -
         * grass, tufts, bushes and the crops in their beds (FarmPlantArt reads it too). It used to
         * be two formulas written apart, and the vegetables rippled on a twenty-second cycle while
         * the meadow around them ran on sixteen.
         *
         * It is kept separate from [windAt] so that changing how far the grass leans cannot quietly
         * change how far everything else does: each consumer scales this by its own amplitude.
         */
        fun gustAt(x: Float, timeSeconds: Float): Float {
            val phase = 2.0 * Math.PI * (x - WIND_SPEED * timeSeconds) / WIND_WAVELENGTH
            val gust = 0.5 + 0.5 * cos(phase)
            return (gust * gust).toFloat()
        }
        /** How far a blade of grass leans, in sprite pixels, under the gust at [x]. */
        fun windAt(x: Float, timeSeconds: Float) = WIND_STRENGTH * gustAt(x, timeSeconds)
        /**
         * Texels per world unit in the baked meadow. Exactly twice the grass tile's own resolution
         * (one texel per four world units), so every tile texel lands on a clean 2x2 block and the
         * 16-pixel tuft sprites still bake at their native size.
         */
        const val MEADOW_SCALE = .5f
        /** The side of one grass tile, in world units. */
        const val GRASS_TILE = 80
        private val GRASS_PALETTE = intArrayOf(
            Color.parseColor("#74bb60"), Color.parseColor("#7cc45f"), Color.parseColor("#82c862"),
            Color.parseColor("#8acd66"), Color.parseColor("#91d16b"), Color.parseColor("#86c565")
        )
        private val GRASS_SPECKLE = intArrayOf(
            Color.parseColor("#91d16b"), Color.parseColor("#9bd574"), Color.parseColor("#6bb65c"),
            Color.parseColor("#80c262"), Color.parseColor("#5a9a4d")
        )
        private val GRASS_FLOWERS = intArrayOf(
            Color.parseColor("#ffe8b0"), Color.parseColor("#ffd0df"), Color.parseColor("#e0d0ff"), Color.parseColor("#fff2d7")
        )
        /**
         * World units per second the gust travels. At a 320-unit wavelength this sets the cycle a
         * given blade lives through: 30 puts it near eleven seconds. It was twice that, and a
         * meadow where each blade stirred once every sixteen seconds read as a still photograph.
         */
        private const val WIND_SPEED = 30f
        private const val WIND_WAVELENGTH = 320f
        /**
         * Peak lean in sprite pixels. The lean is rounded to a whole pixel and capped at 2, so this
         * governs how OFTEN a blade sits at full lean rather than how far it goes: measured over a
         * cycle, 1.7 held the full lean 16% of the time and 2.3 holds it 29%. The blade still never
         * leans further than two pixels - the livelier meadow comes mostly from [WIND_SPEED].
         */
        private const val WIND_STRENGTH = 2.3f
    }

    fun environment(canvas: Canvas, column: Int, row: Int, target: RectF) {
        val bitmap = sheet("garden_environment_v1.png")
        // The supplied sheet has a 4 × 4 grid with transparent padding around objects.
        canvas.drawBitmap(bitmap, Rect(column * bitmap.width / 4, row * bitmap.height / 4,
            (column + 1) * bitmap.width / 4, (row + 1) * bitmap.height / 4), target, paint)
    }
}
