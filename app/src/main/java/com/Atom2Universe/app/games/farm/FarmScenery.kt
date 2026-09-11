package com.Atom2Universe.app.games.farm

import android.graphics.*
import kotlin.random.Random

/** Permanent scenery: never part of the crop inventory or the interactive hit regions. */
class FarmScenery(private val sprites: FarmSprites) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = mutableListOf<Path>()
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
        paths.add(path)
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
    fun ground(canvas: Canvas) {
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        for ((width, color) in listOf(62f to Color.rgb(133, 143, 78), 49f to Color.rgb(187, 162, 108), 35f to Color.rgb(204, 180, 124))) {
            paint.strokeWidth = width; paint.color = color
            paths.forEach { canvas.drawPath(it, paint) }
        }
        paint.style = Paint.Style.FILL
        paint.color = Color.argb(65, 124, 107, 72)
        pathSamples.forEachIndexed { i, point ->
            if (i % 3 == 0) canvas.drawOval(point.x - 3 + i % 13, point.y - 2, point.x + 1 + i % 13, point.y + 1, paint)
        }
    }
    fun objects(canvas: Canvas, visible: RectF) {
        if (RectF.intersects(farmstead, visible)) sprites.farmstead(canvas, farmstead)
        decorations.forEach { decoration ->
            val rect = decoration.rect
            if (!RectF.intersects(rect, visible)) return@forEach
            when (decoration.kind) {
                0 -> sprites.crop(canvas, FarmCrop.APPLE, 0, 3, rect)
                1, 2 -> sprites.environment(canvas, 2, 3, rect)
                3 -> sprites.environment(canvas, 3, 3, rect)
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
}
