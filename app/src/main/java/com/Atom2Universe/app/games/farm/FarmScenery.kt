package com.Atom2Universe.app.games.farm

import android.graphics.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Permanent scenery: never part of the crop inventory or the interactive hit regions. */
class FarmScenery(private val sprites: FarmSprites) {
    private val paint = Paint().apply { isAntiAlias = false; isFilterBitmap = false }
    // Rasterize small authored silhouettes BEFORE the camera transform. Turning AA off on
    // full-resolution paths alone still produces vector-sized edges, not enlarged art pixels.
    private val pixelSprites = mutableMapOf<String, Bitmap>()
    private fun pixelSprite(canvas: Canvas, key: String, width: Int, height: Int,
                            target: RectF, draw: (Canvas) -> Unit) {
        val bitmap = pixelSprites.getOrPut(key) {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
        }
        paint.style = Paint.Style.FILL
        canvas.drawBitmap(bitmap, null, target, paint)
    }
    private val pathSamples = mutableListOf<PointF>()
    private data class Decoration(val kind: Int, val rect: RectF)
    private val decorations = mutableListOf<Decoration>()
    private val shed = RectF(710f, 2150f, 920f, 2330f)
    private val well = RectF(1050f, 2070f, 1160f, 2210f)
    private val farmstead = RectF(180f, -810f, 2480f, -810f + 2300f / 3f)
    private val plots = FarmLayout.lands.map { RectF(it.x - 45, it.y - 50, it.x + it.width + 45, it.y + it.height + 50) }

    private fun trail(x: Float, y: Float, vararg curves: Float) {
        val path = Path().apply {
            moveTo(x, y)
            for (i in curves.indices step 6) cubicTo(curves[i], curves[i + 1], curves[i + 2], curves[i + 3], curves[i + 4], curves[i + 5])
        }
        val measure = PathMeasure(path, false)
        val point = FloatArray(2)
        var distance = 0f
        while (distance <= measure.length) {
            measure.getPosTan(distance, point, null)
            pathSamples.add(PointF(point[0], point[1])); distance += 24f
        }
    }
    init {
        trail(1320f, 40f, 1320f, 0f, 1310f, -35f, 1320f, -85f)
        // One winding spine and short lanes into clearings, rather than roads spanning a grid.
        trail(1300f, FarmLayout.worldHeight,
            1410f, 2390f, 1210f, 2170f, 1310f, 1960f,
            1410f, 1740f, 1270f, 1480f, 1300f, 1300f,
            1370f, 1100f, 1240f, 900f, 1300f, 660f,
            1390f, 440f, 1250f, 240f, 1320f, 40f)
        trail(1300f, 660f, 1000f, 560f, 660f, 700f, 110f, 680f)
        trail(1310f, 610f, 1610f, 570f, 2000f, 720f, 2540f, 740f)
        trail(1300f, 1300f, 980f, 1350f, 530f, 1390f, 90f, 1370f)
        trail(1315f, 1070f, 1490f, 1080f, 1740f, 1010f, 1870f, 1040f,
            1830f, 1270f, 1900f, 1500f, 1860f, 1730f,
            1810f, 2010f, 1840f, 2260f, 1850f, 2450f,
            1960f, 2490f, 2180f, 2460f, 2500f, 2490f)
        trail(1860f, 1730f, 2060f, 1640f, 2310f, 1730f, 2540f, 1680f)
        trail(1310f, 1960f, 1050f, 2010f, 560f, 1970f, 100f, 1990f)
        trail(1310f, 1960f, 1500f, 2020f, 1700f, 1990f, 1840f, 2010f)
        // The two southern lanes. They leave the spine at points that lie on it, so the junction
        // reads as a fork rather than a road starting in the middle of a field.
        trail(1309f, 2278f, 1000f, 2470f, 620f, 2430f, 180f, 2450f)
        trail(1324f, 2576f, 1450f, 2620f, 1620f, 2550f, 1790f, 2580f)
        // Each spur ends precisely at its parcel gate.
        val junctions = listOf(
            PointF(965f, 629f), PointF(1580f, 625f), PointF(2040f, 700f), PointF(435f, 680f),
            PointF(310f, 1380f), PointF(925f, 1350f), PointF(1650f, 1040f), PointF(2100f, 1690f),
            PointF(215f, 1980f), PointF(780f, 1980f), PointF(1555f, 2000f), PointF(2050f, 2470f),
            PointF(245f, 2446f), PointF(1545f, 2583f)
        )
        // Written by hand, one per parcel: adding land without adding its gate would otherwise fail
        // with an out-of-range index several frames into the first draw.
        require(junctions.size == FarmLayout.lands.size) { "une jonction manque pour une parcelle" }
        FarmLayout.lands.forEachIndexed { i, land ->
            val gateX = land.x + land.width / land.columns * 1.5f
            val gateY = land.y + land.height + 14
            val destination = junctions[i]
            trail(gateX, gateY, gateX - 12, gateY + 40, destination.x + 24, destination.y - 35, destination.x, destination.y)
        }
        trail(825f, 1985f, 770f, 2040f, 840f, 2080f, 815f, 2330f)
        trail(1100f, 1990f, 1120f, 2020f, 1140f, 2040f, 1105f, 2170f)
        // Reproducible clusters; keep complete canopies clear of beds, signs and paths.
        val random = Random(7041)
        repeat(900) {
            val kind = random.nextInt(5)
            val w = if (kind == 0) random.nextInt(95, 150).toFloat() else random.nextInt(38, 80).toFloat()
            val h = if (kind == 0) w * 1.3f else w * .8f
            val x = random.nextFloat() * (FarmLayout.worldWidth - w - 40) + 20
            val y = random.nextFloat() * (FarmLayout.worldHeight - h - 40) + 20
            val rect = RectF(x, y, x + w, y + h)
            if (decorations.size < 180 && plots.none { RectF.intersects(it, rect) } &&
                !RectF.intersects(shed, rect) && !RectF.intersects(well, rect) &&
                decorations.none { RectF.intersects(it.rect, rect) } &&
                pathSamples.none { rect.left - 38 < it.x && rect.right + 38 > it.x && rect.top - 38 < it.y && rect.bottom + 38 > it.y }) {
                decorations.add(Decoration(kind, rect))
            }
        }
        // Populate the northern extension too, following the farm image's transparent silhouette.
        // Separate seed preserves all the existing field decorations.
        val northernRandom = Random(9052)
        val entrance = RectF(1170f, -210f, 1470f, 80f)
        var northernCount = 0
        repeat(1400) {
            if (northernCount >= 85) return@repeat
            val kind = if (northernRandom.nextInt(3) == 0) 0 else 1
            val w = if (kind == 0) northernRandom.nextInt(145, 235).toFloat()
                else northernRandom.nextInt(65, 115).toFloat()
            val h = if (kind == 0) w * 1.3f else w * .8f
            val x = 20 + northernRandom.nextFloat() * (FarmLayout.worldWidth - w - 40)
            val y = FarmLayout.worldTop + 20 + northernRandom.nextFloat() * (-FarmLayout.worldTop - h - 20)
            val rect = RectF(x, y, x + w, y + h)
            val clearance = RectF(rect).apply { inset(-12f, -12f) }
            if (!RectF.intersects(entrance, clearance) &&
                plots.none { RectF.intersects(it, clearance) } &&
                decorations.none { RectF.intersects(it.rect, clearance) } &&
                pathSamples.none { clearance.contains(it.x, it.y) } &&
                sprites.farmsteadTransparent(clearance, farmstead)) {
                decorations.add(Decoration(kind, rect))
                northernCount++
            }
        }
        // Kind 4 used to be a posy of five wildflowers drawn from one seedless, cached sprite, so
        // every one of them was the same five flowers in the same diagonal - two of them in view at
        // once was enough to read as a copy-paste. The meadow now carries its own wildflowers, one
        // per grass tuft, seeded per tuft and leaning with the wind, so the posies are dropped.
        // They are cleared here rather than never generated: the placement draws from a seeded
        // random and checks what is already down, so skipping them earlier would shuffle every
        // other bush, rock and tree on the map.
        decorations.removeAll { it.kind == 4 }
        decorations.sortBy { it.rect.bottom }
    }
    /**
     * A grid of the trail's own sample points (already spaced 24 units apart along every lane),
     * bucketed so "distance to the nearest path" is a handful of comparisons instead of a scan of
     * every sample - the ground texture needs that distance for every baked pixel.
     */
    private val sampleGrid: Map<Long, List<PointF>> = HashMap<Long, MutableList<PointF>>().also { grid ->
        pathSamples.forEach { p ->
            val cell = packCell(kotlin.math.floor(p.x / GRID_CELL).toInt(), kotlin.math.floor(p.y / GRID_CELL).toInt())
            grid.getOrPut(cell) { mutableListOf() }.add(p)
        }
    }
    private fun packCell(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    private fun nearestPathDistance(x: Float, y: Float): Float {
        val cx = kotlin.math.floor(x / GRID_CELL).toInt(); val cy = kotlin.math.floor(y / GRID_CELL).toInt()
        var bestSq = Float.MAX_VALUE
        for (dx in -1..1) for (dy in -1..1) {
            val bucket = sampleGrid[packCell(cx + dx, cy + dy)] ?: continue
            for (p in bucket) {
                val ddx = x - p.x; val ddy = y - p.y
                val d2 = ddx * ddx + ddy * ddy
                if (d2 < bestSq) bestSq = d2
            }
        }
        return if (bestSq == Float.MAX_VALUE) 9999f else kotlin.math.sqrt(bestSq)
    }
    /**
     * The whole trail network, painted once into a half-resolution cached bitmap and stamped back
     * every frame with a single drawBitmap - matching the design's per-pixel sandy/pebbled dirt
     * (a wavy, dithered edge blending into the grass) instead of a handful of concentric strokes.
     */
    private val groundTexture: Bitmap by lazy { buildGroundTexture() }
    private fun buildGroundTexture(): Bitmap {
        val bw = kotlin.math.ceil(FarmLayout.worldWidth * GROUND_SCALE).toInt() + 1
        val bh = kotlin.math.ceil((FarmLayout.worldHeight - FarmLayout.worldTop) * GROUND_SCALE).toInt() + 1
        val random = Random(20260912)
        val green = Color.rgb(112, 166, 83)
        val sandMid = Color.rgb(176, 172, 101)
        val sandDark = Color.rgb(228, 195, 139)
        val sandPale = Color.rgb(239, 210, 153)
        val sandLight = Color.rgb(213, 181, 124)
        // A world this size has millions of output texels but only a few hundred path samples, so
        // finding "distance to nearest path" is far cheaper the other way round: splat each sample's
        // own small neighbourhood into a distance buffer instead of asking every texel to search the
        // whole sample grid. MAX_MARGIN comfortably covers the half-width, its wobble and the blend.
        val distSq = FloatArray(bw * bh) { Float.MAX_VALUE }
        val marginCells = kotlin.math.ceil(MAX_MARGIN * GROUND_SCALE).toInt()
        pathSamples.forEach { p ->
            val cbx = (p.x * GROUND_SCALE).toInt(); val cby = ((p.y - FarmLayout.worldTop) * GROUND_SCALE).toInt()
            for (oy in -marginCells..marginCells) {
                val by = cby + oy; if (by !in 0 until bh) continue
                for (ox in -marginCells..marginCells) {
                    val bx = cbx + ox; if (bx !in 0 until bw) continue
                    val wx = bx / GROUND_SCALE; val wy = by / GROUND_SCALE + FarmLayout.worldTop
                    val ddx = wx - p.x; val ddy = wy - p.y
                    val d2 = ddx * ddx + ddy * ddy
                    val idx = by * bw + bx
                    if (d2 < distSq[idx]) distSq[idx] = d2
                }
            }
        }
        val pixels = IntArray(bw * bh)
        for (by in 0 until bh) for (bx in 0 until bw) {
            val idx = by * bw + bx
            if (distSq[idx] == Float.MAX_VALUE) continue
            val d = kotlin.math.sqrt(distSq[idx])
            val wx = bx / GROUND_SCALE; val wy = by / GROUND_SCALE + FarmLayout.worldTop
            val edge = PATH_HALF_WIDTH + 1.4f * kotlin.math.sin(wx * .031f) + 1.3f * kotlin.math.sin(wy * .043f + wx * .017f)
            var color = 0
            if (d < edge + 6f && random.nextFloat() > .12f) color = green
            if (d < edge) color = if (d > edge - 4f) sandMid else {
                val r = random.nextFloat()
                if (r < .12f) sandLight else if (r < .30f) sandPale else sandDark
            }
            pixels[idx] = color
        }
        // Pebble dabs as direct pixel writes too - this bakes once for the whole map rather than
        // per tile, but it shares the same fix: a Canvas draw call costs far more than a write.
        fun setPixel(bx: Int, by: Int, color: Int) { if (bx in 0 until bw && by in 0 until bh) pixels[by * bw + bx] = color }
        val pebbles = intArrayOf(sandLight, sandPale, sandDark)
        repeat(pathSamples.size * 6) {
            val base = pathSamples[random.nextInt(pathSamples.size)]
            val angle = random.nextFloat() * (Math.PI * 2).toFloat()
            val r = random.nextFloat() * 26f
            val px = base.x + cos(angle) * r; val py = base.y + sin(angle) * r
            if (nearestPathDistance(px, py) < 15f) {
                val bx = (px * GROUND_SCALE).toInt(); val by = ((py - FarmLayout.worldTop) * GROUND_SCALE).toInt()
                val color = pebbles[random.nextInt(pebbles.size)]
                val len = 1 + random.nextInt(3)
                for (i in 0 until len) setPixel(bx + i, by, color)
                if (random.nextInt(9) == 0) {
                    setPixel(bx, by, Color.rgb(0xbb, 0xa1, 0x7a)); setPixel(bx + 1, by, Color.rgb(0xbb, 0xa1, 0x7a))
                    setPixel(bx, by - 1, Color.rgb(0xf6, 0xde, 0xb0))
                }
            }
        }
        val bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, bw, 0, 0, bw, bh)
        return bitmap
    }
    fun ground(canvas: Canvas) {
        canvas.save()
        canvas.translate(0f, FarmLayout.worldTop); canvas.scale(1f / GROUND_SCALE, 1f / GROUND_SCALE)
        canvas.drawBitmap(groundTexture, 0f, 0f, paint)
        canvas.restore()
    }
    fun objects(canvas: Canvas, visible: RectF, windTime: Float) {
        if (RectF.intersects(farmstead, visible)) sprites.farmstead(canvas, farmstead)
        decorations.forEach { decoration ->
            val rect = decoration.rect
            if (!RectF.intersects(rect, visible)) return@forEach
            when (decoration.kind) {
                0 -> sprites.crop(canvas, FarmCrop.APPLE, 0, 3, rect)
                1, 2 -> bush(canvas, rect, windTime)
                3 -> rock(canvas, rect)
            }
        }
        if (RectF.intersects(shed, visible)) building(canvas, shed, false)
        if (RectF.intersects(well, visible)) building(canvas, well, true)
    }
    /** A stable per-instance pick, not a random one: the same rock/bush must always draw the same. */
    private fun decorationHash(rect: RectF): Int {
        var h = kotlin.math.floor(rect.left).toInt() * 0x2c1b3c6d xor kotlin.math.floor(rect.top).toInt() * 0x165667b1
        h = h xor (h ushr 13)
        return (h ushr 1) and 0x7fffffff
    }
    private val bushSprites = mutableMapOf<Int, Bitmap>()
    private val bentBushSprites = mutableMapOf<Int, Bitmap>()
    /** Painted once per variant into an 80×80 silhouette, echoing the design's paintBush(). */
    private fun bushSprite(id: Int, clumps: Int = BUSH_CLUMPS): Bitmap {
        return bushSprites.getOrPut(id * 8 + clumps) {
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val fill = Paint().apply { isAntiAlias = false }
            val s = 24f; val bx0 = 40f; val by0 = 72f
            fill.color = Color.rgb(0x57, 0x97, 0x56); canvas.drawOval(bx0 - s, by0 - 3f, bx0 + s, by0 + 7f, fill)
            for (i in 0 until clumps) {
                val bx = bx0 + sin(i * 2.7f + id) * s * .5f; val by = by0 - 5f - i * 3f
                fill.color = Color.rgb(0x32, 0x7e, 0x58); canvas.drawOval(bx - s * .62f, by - s * .49f, bx + s * .62f, by + s * .49f, fill)
                fill.color = Color.rgb(0x43, 0x9b, 0x60); canvas.drawOval(bx - 1f - s * .55f, by - 3f - s * .43f, bx - 1f + s * .55f, by - 3f + s * .43f, fill)
                fill.color = Color.rgb(0x6d, 0xbb, 0x69); canvas.drawOval(bx - 3f - s * .43f, by - 5f - s * .31f, bx - 3f + s * .43f, by - 5f + s * .31f, fill)
                for (j in 0 until 9) {
                    val a = sin(j * 12.3f + i * 4f + id); val b = cos(j * 7.7f + i * 2f)
                    val px = bx + a * s * .37f - 2f; val py = by - 5f + b * s * .23f
                    fill.color = if (j % 3 == 0) Color.rgb(0xb2, 0xdc, 0x7e) else Color.rgb(0x86, 0xcd, 0x72)
                    canvas.drawRect(px, py, px + 3f + j % 3, py + 2f, fill)
                }
                if (id % 2 == 0 && i % 2 == 0) {
                    fill.color = Color.rgb(0xf0, 0xba, 0xd3); canvas.drawRect(bx + 3f, by - 3f, bx + 6f, by, fill)
                    fill.color = Color.rgb(0xff, 0xe3, 0xa0); canvas.drawRect(bx + 4f, by - 2f, bx + 5f, by - 1f, fill)
                }
            }
            bitmap
        }
    }
    /**
     * The canopy bends with height, its base and shadow held perfectly still: the cached silhouette
     * is stamped back in coarse horizontal bands, each shifted a little more than the one below, so
     * the whole bush leans without redrawing its shape from scratch every frame.
     */
    fun bush(canvas: Canvas, rect: RectF, windTime: Float, seed: Int = decorationHash(rect)) {
        val id = (seed and Int.MAX_VALUE) % 6
        val sprite = bushSprite(id)
        val scale = rect.width() / 60f
        val baseX = rect.centerX(); val baseY = rect.bottom - 4f
        val frame = (FarmSprites.gustAt(baseX, windTime) * 17f).toInt().coerceIn(0, 17)
        val bent = bentBushSprites.getOrPut(id * 18 + frame) {
            Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).also { bitmap ->
                val buffer = Canvas(bitmap)
                for (row in 0 until 80) {
                    val heightFrac = ((72 - row) / 32f).coerceAtLeast(0f)
                    val dx = kotlin.math.round(frame * .14f * heightFrac * heightFrac).toInt()
                    buffer.drawBitmap(sprite, Rect(0, row, 80, row + 1),
                        Rect(dx, row, 80 + dx, row + 1), paint)
                }
            }
        }
        canvas.drawBitmap(bent, null, RectF(baseX - 40f * scale, baseY - 72f * scale,
            baseX + 40f * scale, baseY + 8f * scale), paint)
    }
    /**
     * The bush with only its lowest [clumps] volumes left, standing still. The clearing mini-game
     * tears one volume off per pull, so the bush comes apart in the player's hand instead of a few
     * loose sprigs being plucked off ground that looked untouched.
     *
     * Unbent on purpose: [bush] caches one bitmap per wind frame, and keying that cache by clump
     * count as well would multiply it fivefold for a bush that is about to be pulled out anyway.
     */
    fun bushClumps(canvas: Canvas, rect: RectF, clumps: Int, alpha: Int = 255,
                   seed: Int = decorationHash(rect)) {
        if (clumps <= 0) return
        val sprite = bushSprite((seed and Int.MAX_VALUE) % 6, clumps.coerceIn(1, BUSH_CLUMPS))
        val scale = rect.width() / 60f
        val baseX = rect.centerX(); val baseY = rect.bottom - 4f
        paint.alpha = alpha
        canvas.drawBitmap(sprite, null, RectF(baseX - 40f * scale, baseY - 72f * scale,
            baseX + 40f * scale, baseY + 8f * scale), paint)
        paint.alpha = 255
    }
    /** Where volume [index] of a bush sits on the map - the mini-game bursts leaves at the one it just tore off. */
    fun bushClumpCenter(rect: RectF, index: Int, seed: Int = decorationHash(rect)): PointF {
        val id = (seed and Int.MAX_VALUE) % 6
        val scale = rect.width() / 60f
        // Mirrors the placement inside bushSprite: s = 24, base at sprite (40, 72).
        return PointF(rect.centerX() + sin(index * 2.7f + id) * 24f * .5f * scale,
            rect.bottom - 4f + (-5f - index * 3f) * scale)
    }
    /** The fenced beds, plus their sign and the width of their fence: no wild grass grows inside. */
    private val beds = FarmLayout.lands.map {
        RectF(it.x - 10, it.y - 20, it.x + it.width + 10, it.y + it.height + 12)
    }
    /**
     * Whether a loose tuft of grass may root at this world point. Ground that belongs to something
     * else does not grow wild grass: the planting beds, the trodden trails, and anything already
     * standing on it. Only the root is tested, not the whole blade - a tuft growing right up
     * against a path leans a little over it, which is what a verge looks like.
     *
     * Reads nothing but state fixed in the constructor, so the meadow bake can call it off the UI thread.
     */
    fun tuftAllowed(x: Float, y: Float): Boolean {
        if (beds.any { it.contains(x, y) }) return false
        if (nearestPathDistance(x, y) < PATH_HALF_WIDTH + 5f) return false
        if (shed.contains(x, y) || well.contains(x, y)) return false
        return decorations.none { it.rect.contains(x, y) }
    }
    /** One piece of clutter to pull out of a neglected cell: a weed clump or a small stone. */
    class Rubble(val fx: Float, val fy: Float, val stone: Boolean, val scale: Float, val seed: Int)

    /**
     * What a cluttered cell is made of: four pieces on average, weeds and stones mixed, laid out
     * from the cell's own hash. The art and the hit testing both read this one list, so what the
     * finger grabs is always what the eye sees - which is what the sprite-sheet version got wrong.
     * It drew one flat PNG square of weeds at rest, then a completely different set of little
     * bushes as soon as the mini-game started.
     */
    fun rubbleLayout(rect: RectF, seed: Int = decorationHash(rect)): List<Rubble> {
        val random = Random(seed)
        val count = 3 + random.nextInt(3)
        // Slots on a loose 3x2 grid, then jittered: scattering purely at random piles pieces on top
        // of one another, and two overlapping pieces cannot be told apart or grabbed separately.
        return (0 until 6).shuffled(random).take(count).map { slot ->
            Rubble(.18f + slot % 3 * .32f + (random.nextFloat() - .5f) * .13f,
                // Each piece stands ON its fy line and is drawn upwards from it, so the rows sit
                // low in the cell: placed around the middle, the clutter all bunches near the top.
                .50f + slot / 3 * .31f + (random.nextFloat() - .5f) * .10f,
                random.nextFloat() < .35f, .85f + random.nextFloat() * .45f, random.nextInt())
        }
    }
    /** The whole cluttered cell, at rest. */
    fun rubble(canvas: Canvas, rect: RectF, windTime: Float) {
        rubbleLayout(rect).forEach { rubblePiece(canvas, rect, it, windTime) }
    }
    /** One piece of it - the mini-game draws them one by one so it can leave out what was pulled. */
    fun rubblePiece(canvas: Canvas, rect: RectF, item: Rubble, windTime: Float, alpha: Int = 255) {
        val x = rect.left + item.fx * rect.width()
        val y = rect.top + item.fy * rect.height()
        if (item.stone) {
            val w = 18f * item.scale; val h = 13f * item.scale
            paint.alpha = alpha
            pixelSprite(canvas, "pebble:${(item.seed and Int.MAX_VALUE) % 3}", 32, 22,
                RectF(x - w / 2, y - h, x + w / 2, y)) {
                drawRock(it, RectF(4f, 2f, 28f, 22f), (item.seed and Int.MAX_VALUE) % 3)
            }
            paint.alpha = 255
        } else {
            val w = 22f * item.scale; val h = 18f * item.scale
            canvas.save()
            // The sprite is cached upright and the whole clump is tilted about its own root, so the
            // weeds catch the wind without a cached bitmap per lean angle.
            canvas.rotate(FarmSprites.gustAt(x, windTime) * 5f, x, y)
            paint.alpha = alpha
            pixelSprite(canvas, "weed:${(item.seed and Int.MAX_VALUE) % 4}", 32, 26,
                RectF(x - w / 2, y - h, x + w / 2, y)) { drawWeed(it, (item.seed and Int.MAX_VALUE) % 4) }
            paint.alpha = 255
            canvas.restore()
        }
    }
    /** A rough clump of tall weeds: blades fanning out of one root, in the three greens the beds use. */
    private fun drawWeed(canvas: Canvas, variant: Int) {
        // 32x26 with the root at (16, 23): checked against the widest fan and the longest blade, the
        // tips clear every edge by about three pixels. A blade cut off by the sprite's border reads
        // as a straight razor line and gives the whole clump away as a rectangle.
        val rootX = 16f; val rootY = 23f
        val brush = Paint().apply { isAntiAlias = false; strokeCap = Paint.Cap.BUTT }
        brush.style = Paint.Style.FILL
        brush.color = Color.rgb(0x1d, 0x6b, 0x3d)
        canvas.drawOval(rootX - 5f, rootY - 2f, rootX + 5f, rootY + 3f, brush)
        brush.style = Paint.Style.STROKE
        for (blade in 0 until 5) {
            val spread = (blade - 2) * .36f + sin(variant * 2.1f + blade * 1.7f) * .12f
            val length = 13f + sin(variant * 3.3f + blade * 2.9f) * 4f
            val tipX = rootX + sin(spread) * length
            val tipY = rootY - cos(spread) * length
            // A wide dark stroke with a narrower bright one laid over it reads as a curved blade
            // without any of the maths a real curve would need.
            brush.strokeWidth = 4f; brush.color = Color.rgb(0x23, 0x7e, 0x45)
            canvas.drawLine(rootX, rootY, tipX, tipY, brush)
            brush.strokeWidth = 2f
            brush.color = if (blade % 2 == 0) Color.rgb(0x5f, 0xb8, 0x4a) else Color.rgb(0x9a, 0xd9, 0x53)
            canvas.drawLine(rootX, rootY - 1f, tipX - sin(spread) * 3f, tipY + cos(spread) * 3f, brush)
        }
    }
    /** A stable, wind-free rock: shaded facets plus an optional moss tuft, echoing the design's rock(). */
    private fun rockStable(seed: Int): Int {
        var hash = seed xor (seed ushr 16)
        hash *= 0x45d9f3b
        return (hash xor (hash ushr 16)) and Int.MAX_VALUE
    }
    /** The art box of one rock variant inside the 48x40 sprite - the single source for its geometry. */
    private fun rockBody(variant: Int): RectF {
        val width = when (variant / 2) { 0 -> 48f; 1 -> 41f; else -> 44f }
        val height = when (variant / 2) { 0 -> 40f; 1 -> 40f; else -> 30f }
        return RectF((48f - width) / 2f, 40f - height, (48f + width) / 2f, 40f)
    }
    /**
     * Where the rock really sits inside [rect]. It STANDS on the bottom of its cell rather than
     * filling it, so its middle is nowhere near the middle of the cell - between 9 and 14 world
     * units lower, depending on the variant. Anything that has to turn a rock, aim at one or throw
     * its dust must use this; spinning it about the cell centre visibly swings it round a point
     * hanging in the air above it.
     */
    fun rockCenter(rect: RectF, seed: Int = decorationHash(rect)): PointF {
        val variant = rockStable(seed) % 6
        val body = rockBody(variant)
        val s = body.width() / 2.4f
        val vScale = if (variant % 3 == 1) .8f else 1f
        // drawRock puts the base 4 art pixels above the sprite's foot and spans -1.1s to +0.07s
        // around it, so the silhouette's middle is a little over half of 1.1s up from that base.
        val centerY = (body.bottom - 4f) - .515f * s * vScale
        return PointF(rect.left + body.centerX() / 48f * rect.width(),
            rect.top + centerY / 40f * rect.height())
    }
    /**
     * [alpha] has to be passed in: this class paints with its own Paint, so a caller dimming its own
     * one and then calling here changed nothing at all - which is why the cleared rock never faded.
     */
    fun rock(canvas: Canvas, rect: RectF, alpha: Int = 255, seed: Int = decorationHash(rect)) {
        val stable = rockStable(seed)
        val variant = stable % 6
        canvas.save()
        if ((stable ushr 4) and 1 != 0) canvas.scale(-1f, 1f, rect.centerX(), rect.centerY())
        paint.alpha = alpha
        pixelSprite(canvas, "rock:$variant", 48, 40, rect) { drawRock(it, rockBody(variant), variant % 3) }
        paint.alpha = 255
        canvas.restore()
    }
    private fun drawRock(canvas: Canvas, rect: RectF, variant: Int) {
        val s = rect.width() / 2.4f; val x = rect.centerX(); val y = rect.bottom - 4f
        val vScale = if (variant == 1) .8f else 1f
        paint.style = Paint.Style.FILL; paint.color = Color.rgb(0x65, 0x9e, 0x57)
        canvas.drawOval(x + 2f - s - 4f, y + 1f - 4f, x + 2f + s + 4f, y + 1f + 4f, paint)
        val shape = listOf(-1f to 0f, -.95f to -.52f, -.55f to -.93f, .18f to -1.1f, .83f to -.75f, 1f to -.24f, .62f to .07f)
        poly(canvas, shape.map { (a, b) -> floatArrayOf(x + a * s, y + b * s * vScale) }, Color.rgb(0x77, 0x7e, 0x8c))
        poly(canvas, listOf(floatArrayOf(x - s + 2f, y - s * .5f), floatArrayOf(x - s * .5f, y - s * .9f),
            floatArrayOf(x + s * .16f, y - s * 1.02f), floatArrayOf(x + s * .66f, y - s * .65f),
            floatArrayOf(x + s * .25f, y - s * .27f), floatArrayOf(x - s * .65f, y - s * .17f)), Color.rgb(0xb8, 0xbd, 0xc8))
        poly(canvas, listOf(floatArrayOf(x - s * .5f, y - s * .88f), floatArrayOf(x + s * .16f, y - s),
            floatArrayOf(x + s * .45f, y - s * .72f), floatArrayOf(x - s * .12f, y - s * .62f)), Color.rgb(0xe0, 0xdb, 0xd6))
        poly(canvas, listOf(floatArrayOf(x + s * .3f, y - s * .3f), floatArrayOf(x + s * .75f, y - s * .59f),
            floatArrayOf(x + s * .82f, y - s * .2f), floatArrayOf(x + s * .43f, y - s * .04f)), Color.rgb(0x96, 0x9b, 0xa9))
        paint.color = Color.rgb(0xee, 0xf0, 0xdf); canvas.drawRect(x - s * .5f, y - s * .8f, x - s * .5f + 4f, y - s * .8f + 2f, paint)
        if (variant != 1) {
            paint.color = Color.rgb(0x5c, 0xa7, 0x6a); canvas.drawRect(x - s + 2f, y - 3f, x - s + 2f + s * .6f, y, paint)
            paint.color = Color.rgb(0xa1, 0xd8, 0x79); canvas.drawRect(x - s + 4f, y - 4f, x - s + 4f + s * .5f, y - 2f, paint)
        }
    }
    private fun poly(canvas: Canvas, points: List<FloatArray>, color: Int) {
        paint.style = Paint.Style.FILL; paint.color = color
        val path = Path().apply {
            moveTo(points[0][0], points[0][1])
            for (i in 1 until points.size) lineTo(points[i][0], points[i][1])
            close()
        }
        canvas.drawPath(path, paint)
    }
    private fun segment(canvas: Canvas, a: FloatArray, b: FloatArray, width: Float, color: Int) {
        paint.style = Paint.Style.STROKE; paint.strokeWidth = width; paint.strokeCap = Paint.Cap.BUTT
        paint.color = color; canvas.drawLine(a[0], a[1], b[0], b[1], paint)
        paint.style = Paint.Style.FILL
    }
    /** Posts have a fixed silhouette; spacing must never stretch a post into a side rail. */
    private fun parcelPost(canvas: Canvas, x: Float, base: Float) {
        fencePost(canvas, RectF(x - 9f, base - 56f, x + 9f, base))
    }
    fun fenceBack(canvas: Canvas, land: RectF, columns: Int, rows: Int) {
        val step = land.width() / columns
        val topBase = land.top + 64f
        val bottomBase = land.bottom + 20f
        for (col in 0 until columns) {
            fenceRail(canvas, RectF(land.left + col * step, land.top + 14f,
                land.left + (col + 1) * step, topBase))
        }
        // In this front-facing projection, receding rails are seen edge-on. Connect them
        // continuously behind short upright posts instead of stacking elongated posts.
        for (x in floatArrayOf(land.left, land.right)) {
            pixelSprite(canvas, "side-rail", 4, 16,
                RectF(x - 3f, topBase - 34f, x + 3f, bottomBase - 12f)) { c ->
                paint.color = Color.rgb(142, 94, 54); c.drawRect(0f, 0f, 4f, 16f, paint)
                paint.color = Color.rgb(231, 170, 88); c.drawRect(0f, 0f, 3f, 16f, paint)
                paint.color = Color.rgb(255, 207, 124); c.drawRect(0f, 0f, 1f, 16f, paint)
            }
            val gaps = rows + 1
            for (row in 1 until gaps) parcelPost(canvas, x,
                topBase + (bottomBase - topBase) * row / gaps)
        }
        for (col in 0..columns) parcelPost(canvas, land.left + col * step, topBase)
    }
    fun fenceFront(canvas: Canvas, land: RectF, columns: Int, opening: Float) {
        val step = land.width() / columns
        for (col in 0 until columns) {
            val segment = RectF(land.left + col * step, land.bottom - 27f,
                land.left + (col + 1) * step, land.bottom + 20f)
            if (col == 1) gate(canvas, segment, opening) else fenceRail(canvas, segment)
        }
        // Both gate jambs and every rail junction share the same ground line.
        for (col in 0..columns) parcelPost(canvas, land.left + col * step, land.bottom + 20f)
    }
    /** Two golden-brown planks spanning the rect, echoing the design's rail() sprite without an atlas. */
    fun fenceRail(canvas: Canvas, rect: RectF) {
        pixelSprite(canvas, "rail", 54, 28, rect) { drawFenceRail(it, RectF(0f, 0f, 54f, 28f)) }
    }
    private fun drawFenceRail(canvas: Canvas, rect: RectF) {
        val h = rect.height()
        for ((top, bottom) in listOf(.08f to .30f, .52f to .74f)) {
            val ry0 = rect.top + h * top; val ry1 = rect.top + h * bottom
            paint.style = Paint.Style.FILL
            paint.color = Color.rgb(142, 94, 54); canvas.drawRect(rect.left, ry0, rect.right, ry1, paint)
            paint.color = Color.rgb(231, 170, 88); canvas.drawRect(rect.left, ry0, rect.right, ry0 + (ry1 - ry0) * .55f, paint)
            paint.color = Color.rgb(255, 207, 124); canvas.drawRect(rect.left, ry0, rect.right, ry0 + (ry1 - ry0) * .22f, paint)
        }
    }
    /** A post with a rounded pixel top and a grained shaft, echoing the design's post() sprite. */
    fun fencePost(canvas: Canvas, rect: RectF) {
        pixelSprite(canvas, "post", 10, 32, rect) { drawFencePost(it, RectF(0f, 0f, 10f, 32f)) }
    }
    private fun drawFencePost(canvas: Canvas, rect: RectF) {
        canvas.save(); canvas.translate(rect.left, rect.top); canvas.scale(rect.width() / 20f, rect.height() / 64f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(60, 60, 90, 45); canvas.drawOval(1f, 58f, 19f, 66f, paint)
        paint.color = Color.rgb(130, 84, 54); canvas.drawRect(2f, 6f, 18f, 14f, paint)
        canvas.drawRect(4f, 2f, 16f, 8f, paint); canvas.drawRect(6f, 0f, 14f, 4f, paint)
        paint.color = Color.rgb(255, 219, 139); canvas.drawRect(4f, 6f, 16f, 12f, paint)
        canvas.drawRect(6f, 2f, 14f, 8f, paint)
        paint.color = Color.rgb(205, 144, 72); canvas.drawRect(3f, 8f, 17f, 60f, paint)
        paint.color = Color.rgb(240, 189, 105); canvas.drawRect(3f, 8f, 9f, 58f, paint)
        paint.color = Color.rgb(172, 108, 54); canvas.drawRect(13f, 9f, 17f, 58f, paint)
        paint.color = Color.rgb(232, 173, 89); canvas.drawRect(2f, 44f, 18f, 50f, paint)
        paint.color = Color.rgb(255, 206, 123); canvas.drawRect(3f, 44f, 17f, 46f, paint)
        canvas.restore()
    }
    /**
     * A timber gate hinged at the rect's bottom-left corner. [opening] runs 0 (flat, closed) to 1
     * (swung open); the diagonal brace and the far edge are projected as a board with real
     * thickness, not drawn as a fixed-width line, so the perspective holds at every angle.
     */
    fun gate(canvas: Canvas, rect: RectF, opening: Float) {
        val frame = (opening.coerceIn(0f, 1f) * 48f).toInt()
        val sx = rect.width() / 54f
        val sy = rect.height() / 28f
        // Include the thickness, outward swing and shadow, not only the closed rectangle.
        val target = RectF(rect.left - 6f * sx, rect.top - 2f * sy,
            rect.left + 66f * sx, rect.bottom + 38f * sy)
        pixelSprite(canvas, "gate:$frame", 72, 68, target) {
            drawGate(it, RectF(6f, 2f, 60f, 30f), frame / 48f)
        }
    }
    private fun drawGate(canvas: Canvas, rect: RectF, opening: Float) {
        canvas.save(); canvas.translate(rect.left, rect.bottom); canvas.scale(rect.width() / 54f, rect.height() / 28f)
        val angle = opening.coerceIn(0f, 1f) * 1.18f
        val w = cos(angle) * 54f; val depth = sin(angle) * 27f
        fun p(u: Float, v: Float) = floatArrayOf(u * w, u * depth + v)
        fun back(u: Float, v: Float) = floatArrayOf(u * w - sin(angle) * 5f, u * depth + v + cos(angle) * 2.5f)
        poly(canvas, listOf(floatArrayOf(0f, 2f), floatArrayOf(w + 8f, 2f + depth),
            floatArrayOf(w + 12f, 6f + depth), floatArrayOf(2f, 6f)), Color.rgb(0x69, 0x9d, 0x54))
        val outline = listOf(0f to -24f, .1f to -28f, .9f to -28f, 1f to -24f, 1f to 0f, 0f to 0f)
        poly(canvas, outline.map { (u, v) -> back(u, v) }, Color.rgb(0x85, 0x55, 0x32))
        poly(canvas, listOf(p(0f, -24f), p(.1f, -28f), p(.9f, -28f), p(1f, -24f), p(1f, 0f), p(0f, 0f)), Color.rgb(0x86, 0x56, 0x37))
        for (i in 0 until 6) {
            val a = i / 6f + .02f; val b = (i + 1) / 6f - .015f
            poly(canvas, listOf(p(a, -25f), p(b, -25f), p(b, -2f), p(a, -2f)),
                if (i % 2 == 1) Color.rgb(0xdb, 0xa0, 0x52) else Color.rgb(0xe8, 0xb0, 0x63))
            segment(canvas, p(a, -24f), p(a, -3f), 1f, Color.rgb(0xf9, 0xcf, 0x82))
        }
        for (v in listOf(-22f, -5f)) poly(canvas, listOf(p(0f, v), p(1f, v), p(1f, v + 3f), p(0f, v + 3f)), Color.rgb(0xf7, 0xc7, 0x7e))
        poly(canvas, listOf(p(.06f, -7f), p(.94f, -25f), p(.94f, -20f), p(.06f, -2f)), Color.rgb(0x9a, 0x63, 0x37))
        poly(canvas, listOf(p(.06f, -7f), p(.94f, -25f), p(.94f, -22f), p(.06f, -4f)), Color.rgb(0xff, 0xcf, 0x80))
        poly(canvas, listOf(p(1f, -24f), back(1f, -24f), back(1f, 0f), p(1f, 0f)), Color.rgb(0xac, 0x71, 0x3d))
        segment(canvas, back(1f, -23f), back(1f, -1f), 1f, Color.rgb(0x78, 0x4d, 0x31))
        for (i in 0 until 3) {
            val (u, v) = outline[i]; val (u2, v2) = outline[i + 1]
            poly(canvas, listOf(p(u, v), p(u2, v2), back(u2, v2), back(u, v)), Color.rgb(0xf3, 0xc7, 0x7e))
        }
        paint.style = Paint.Style.FILL
        for (v in listOf(-21f, -7f)) {
            paint.color = Color.rgb(0x52, 0x5b, 0x64); canvas.drawRect(0f, v, 6f, v + 3f, paint)
            paint.color = Color.rgb(0xa3, 0xab, 0xb1); canvas.drawRect(1f, v, 3f, v + 1f, paint)
        }
        val latch = p(.92f, -13f)
        paint.color = Color.rgb(0x52, 0x5b, 0x64); canvas.drawRect(latch[0] - 1f, latch[1], latch[0] + 2f, latch[1] + 4f, paint)
        canvas.restore()
    }
    private fun block(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, color: Int) {
        paint.color = color; canvas.drawRect(x, y, x + w, y + h, paint)
    }
    /** Pixel-shaped timber and stone echo the supplied crop sprites without an extra atlas. */
    private fun building(canvas: Canvas, rect: RectF, isWell: Boolean) {
        canvas.save(); canvas.translate(rect.left, rect.top); canvas.scale(rect.width() / 100, rect.height() / 100)
        paint.color = Color.argb(55, 37, 59, 30); canvas.drawOval(-4f, 80f, 105f, 102f, paint)
        val timber = Color.rgb(135, 89, 50)
        if (isWell) {
            block(canvas, 14f, 58f, 72f, 34f, Color.rgb(132, 139, 125))
            block(canvas, 20f, 53f, 60f, 12f, Color.rgb(69, 87, 81))
            block(canvas, 27f, 56f, 46f, 5f, Color.rgb(103, 161, 173))
            for (r in 0..2) for (col in 0..3) block(canvas, 17f + col * 17, 66f + r * 8, 14f, 6f, Color.rgb(187 + r * 8, 183 + r * 8, 155 + r * 8))
            block(canvas, 15f, 22f, 7f, 42f, timber); block(canvas, 78f, 22f, 7f, 42f, timber)
            block(canvas, 49f, 24f, 2f, 32f, Color.rgb(202, 177, 122))
            block(canvas, 42f, 49f, 16f, 13f, Color.rgb(154, 113, 65))
        } else {
            block(canvas, 12f, 30f, 76f, 62f, timber)
            for (i in 0..6) block(canvas, 15f + i * 10, 33f, 8f, 56f, Color.rgb(185, 135, 77))
            block(canvas, 52f, 51f, 24f, 41f, Color.rgb(84, 70, 40))
            block(canvas, 56f, 53f, 17f, 37f, Color.rgb(132, 99, 60))
            block(canvas, 22f, 46f, 22f, 20f, Color.rgb(104, 79, 49))
            block(canvas, 25f, 49f, 16f, 13f, Color.rgb(158, 204, 194))
            block(canvas, 32f, 49f, 2f, 13f, Color.rgb(239, 217, 164))
            block(canvas, 68f, 70f, 3f, 3f, Color.rgb(243, 204, 104))
            block(canvas, 7f, 82f, 31f, 14f, Color.rgb(117, 82, 40))
            block(canvas, 10f, 76f, 9f, 9f, Color.rgb(100, 142, 70))
            block(canvas, 22f, 72f, 12f, 13f, Color.rgb(127, 161, 76))
        }
        for (i in 0..5) {
            val inset = i * 7f
            block(canvas, inset, 30f - i * 5, 100 - inset * 2, 7f, if (i % 2 == 0) Color.rgb(154, 77, 61) else Color.rgb(183, 97, 70))
        }
        block(canvas, 0f, 34f, 100f, 4f, Color.rgb(105, 70, 50))
        canvas.restore()
    }
    companion object {
        /** Volumes a full bush is built from. The clearing mini-game tears them off one by one. */
        const val BUSH_CLUMPS = 5
        private const val GRID_CELL = 48f
        private const val PATH_HALF_WIDTH = 31f
        private const val GROUND_SCALE = .25f
        // Half-width + its wobble (2.7) + the green blend margin (6), rounded up: nothing outside
        // this radius of a path sample can ever be coloured, so the splat need not reach further.
        private const val MAX_MARGIN = 42f
    }
}
