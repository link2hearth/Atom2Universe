package com.Atom2Universe.app.games.farm

import android.graphics.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** Permanent scenery: never part of the crop inventory or the interactive hit regions. */
class FarmScenery(private val sprites: FarmSprites) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
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
                3 -> rock(canvas, rect, decorationHash(rect) % 3)
                else -> {
                    // Individual wildflowers, without the square grass backing of the atlas tile.
                    for (i in 0..4) {
                        val x = rect.left + rect.width() * (.15f + (i * 3 % 5) * .16f)
                        val y = rect.top + rect.height() * (.2f + (i * 2 % 5) * .15f)
                        paint.color = if (i % 2 == 0) Color.rgb(255, 241, 213) else Color.rgb(232, 177, 169)
                        canvas.drawCircle(x - 2, y, 3f, paint); canvas.drawCircle(x + 2, y, 3f, paint)
                        canvas.drawCircle(x, y - 2, 3f, paint); canvas.drawCircle(x, y + 2, 3f, paint)
                        paint.color = Color.rgb(235, 190, 76); canvas.drawCircle(x, y, 2f, paint)
                    }
                }
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
    /** Painted once per variant into an 80×80 silhouette, echoing the design's paintBush(). */
    private fun bushSprite(id: Int): Bitmap {
        return bushSprites.getOrPut(id) {
            val bitmap = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val fill = Paint().apply { isAntiAlias = false }
            val s = 24f; val bx0 = 40f; val by0 = 72f
            fill.color = Color.rgb(0x57, 0x97, 0x56); canvas.drawOval(bx0 - s, by0 - 3f, bx0 + s, by0 + 7f, fill)
            for (i in 0 until 5) {
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
    fun bush(canvas: Canvas, rect: RectF, windTime: Float) {
        val id = decorationHash(rect) % 6
        val sprite = bushSprite(id)
        val scale = rect.width() / 60f
        val baseX = rect.centerX(); val baseY = rect.bottom - 4f
        val bend = sprites.windAt(baseX, windTime) * 1.4f
        val bands = 10
        for (band in 0 until bands) {
            val rowStart = band * 80 / bands; val rowEnd = (band + 1) * 80 / bands
            val heightFrac = ((72 - (rowStart + rowEnd) / 2f) / 32f).coerceAtLeast(0f)
            val dx = bend * heightFrac * heightFrac * scale
            val dst = RectF(baseX - 40f * scale + dx, baseY - (72 - rowStart) * scale,
                baseX + 40f * scale + dx, baseY - (72 - rowEnd) * scale)
            canvas.drawBitmap(sprite, Rect(0, rowStart, 80, rowEnd), dst, paint)
        }
    }
    /** A stable, wind-free rock: shaded facets plus an optional moss tuft, echoing the design's rock(). */
    private fun rock(canvas: Canvas, rect: RectF, variant: Int) {
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
    /** Two golden-brown planks spanning the rect, echoing the design's rail() sprite without an atlas. */
    fun fenceRail(canvas: Canvas, rect: RectF) {
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
        canvas.save(); canvas.translate(rect.left, rect.top); canvas.scale(rect.width() / 20f, rect.height() / 64f)
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(60, 60, 90, 45); canvas.drawOval(1f, 58f, 19f, 66f, paint)
        paint.color = Color.rgb(130, 84, 54); canvas.drawRect(2f, 4f, 18f, 10f, paint)
        paint.color = Color.rgb(255, 219, 139); canvas.drawRect(4f, 1f, 16f, 5f, paint)
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
    private companion object {
        const val GRID_CELL = 48f
        const val PATH_HALF_WIDTH = 31f
        const val GROUND_SCALE = .5f
        // Half-width + its wobble (2.7) + the green blend margin (6), rounded up: nothing outside
        // this radius of a path sample can ever be coloured, so the splat need not reach further.
        const val MAX_MARGIN = 42f
    }
}
