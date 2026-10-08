package com.Atom2Universe.app.science.timeline

import android.content.Context
import android.graphics.*
import android.view.View
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.*
import kotlin.random.Random

/** Original schematic illustrations: no downloaded images, textures, fonts or bitmap assets.
 * All positions are seeded and static; drawing never schedules an animation loop. */
class CosmicArt {
    enum class Kind(val color: Int) {
        ORIGIN(0xFFFFCB88.toInt()), PARTICLES(0xFFFFA489.toInt()), ATOM(0xFFFFD48D.toInt()),
        LIGHT(0xFF7DDDDD.toInt()), DARK(0xFF91A1CB.toInt()), STARS(0xFFBCD4FF.toInt()),
        GALAXY(0xFFC9B7FF.toInt()), PROTOGALAXY(0xFFC9B7FF.toInt()), SUPERNOVA(0xFFFFB99B.toInt()), WEB(0xFF8BCDCB.toInt()),
        EXPANSION(0xFFAAAFF1.toInt()), SUN(0xFFFFCF81.toInt()), EARTH(0xFF7CDCC3.toInt()),
        MOON(0xFFBFCBDC.toInt()),
        STRATA(0xFFA8D3BE.toInt()), MICROBIAL(0xFFD3B28F.toInt()), MARINE(0xFF8CCAC2.toInt()),
        FERN(0xFF9FCAB0.toInt()), CONIFERS(0xFF9BBFD1.toInt()), FLOWER(0xFFE1BB9D.toInt()),
        GRASSLAND(0xFFDAC995.toInt()), ICE(0xFFC3DBDE.toInt());

        val geological get() = ordinal >= STRATA.ordinal
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val points = List(100) {
        val random = Random(421 + it)
        floatArrayOf(random.nextFloat(), random.nextFloat(), random.nextFloat())
    }

    fun draw(canvas: Canvas, bounds: RectF, kind: Kind, background: Boolean = false) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        val save = canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)
        val w = bounds.width(); val h = bounds.height(); val u = min(w, h)
        val cx = w * .5f; val cy = h * .5f
        paint.reset(); paint.isAntiAlias = true
        if (background) {
            paint.shader = LinearGradient(0f, 0f, w, h,
                0xFF142534.toInt(), 0xFF09131E.toInt(), Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
            if (!kind.geological) points.forEach { p ->
                paint.color = Color.argb((40 + p[2] * 100).toInt(), 191, 220, 240)
                canvas.drawCircle(p[0] * w, p[1] * h, u * (.002f + p[2] * .003f), paint)
            }
        }
        when (kind) {
            Kind.STRATA, Kind.MICROBIAL, Kind.MARINE, Kind.FERN, Kind.CONIFERS,
            Kind.FLOWER, Kind.GRASSLAND, Kind.ICE -> drawGeology(canvas, w, h, kind)
            Kind.ORIGIN -> {
                halo(canvas, cx, cy, u * .48f, kind.color, 200)
                for (i in 0..22) {
                    val angle = i * 2.39996f
                    val r = u * (.13f + (i % 5) * .056f)
                    paint.color = Color.argb(110, 255, 212, 157); paint.strokeWidth = u * .005f
                    canvas.drawLine(cx + cos(angle) * u * .1f, cy + sin(angle) * u * .1f,
                        cx + cos(angle) * r, cy + sin(angle) * r, paint)
                }
                halo(canvas, cx, cy, u * .16f, Color.WHITE, 255)
            }
            Kind.PARTICLES, Kind.ATOM -> {
                halo(canvas, cx, cy, u * .46f, kind.color, 75)
                // Nucleosynthesis creates nuclei, not bound electron orbits.
                val count = if (kind == Kind.ATOM) 7 else 17
                repeat(count) { i ->
                    val p = points[i]; val spread = if (kind == Kind.ATOM) .22f else .8f
                    paint.color = if (i % 2 == 0) kind.color else 0xFF8FD9E7.toInt()
                    canvas.drawCircle(cx + (p[0] - .5f) * u * spread,
                        cy + (p[1] - .5f) * u * spread, u * (.03f + p[2] * .016f), paint)
                }
            }
            Kind.PROTOGALAXY -> {
                // Young irregular systems must not look like a fully assembled modern spiral.
                halo(canvas, cx, cy, u * .43f, kind.color, 65)
                repeat(18) { i ->
                    val p = points[i]
                    halo(canvas, cx + (p[0] - .5f) * u * .68f, cy + (p[1] - .5f) * u * .5f,
                        u * (.025f + p[2] * .06f), if (i % 3 == 0) 0xFFFFDC9F.toInt() else kind.color, 195)
                }
            }
            Kind.GALAXY -> {
                val tilt = canvas.save(); canvas.translate(cx, cy); canvas.rotate(-24f); canvas.scale(1f, .6f)
                halo(canvas, 0f, 0f, u * .48f, kind.color, 80)
                repeat(3) { arm ->
                    repeat(75) { i ->
                        val t = i / 75f; val angle = arm * 2.094f + t * 5.4f
                        val r = (.04f + t * .43f) * u
                        paint.color = Color.argb((180 * (1f - t * .6f)).toInt(), 203, 198, 255)
                        canvas.drawCircle(cos(angle) * r, sin(angle) * r, u * (.004f + (1 - t) * .01f), paint)
                    }
                }
                halo(canvas, 0f, 0f, u * .17f, 0xFFFFE5B1.toInt(), 255)
                canvas.restoreToCount(tilt)
            }
            Kind.SUN -> {
                halo(canvas, cx, cy, u * .48f, kind.color, 155)
                paint.shader = RadialGradient(cx - u * .05f, cy - u * .06f, u * .25f,
                    intArrayOf(0xFFFFF4D0.toInt(), 0xFFFFCF6A.toInt(), 0xFFEF923D.toInt()), null, Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, u * .225f, paint); paint.shader = null
                paint.style = Paint.Style.STROKE; paint.strokeWidth = u * .004f; paint.color = 0x559BCCDA
                canvas.drawOval(cx - u * .46f, cy - u * .13f, cx + u * .46f, cy + u * .13f, paint)
                paint.style = Paint.Style.FILL
            }
            Kind.EARTH, Kind.MOON -> {
                // The early Earth is molten, not a present-day globe with modern continents.
                val earth = kind == Kind.EARTH
                val warm = if (earth) 0xFFE8A164.toInt() else 0xFFDEB891.toInt()
                halo(canvas, cx, cy, u * .43f, if (earth) 0xFFDD7047.toInt() else kind.color, 60)
                paint.shader = RadialGradient(cx - u * .1f, cy - u * .1f, u * .53f,
                    intArrayOf(warm, if (earth) 0xFF843F33.toInt() else 0xFF836357.toInt(), 0xFF101A27.toInt()),
                    floatArrayOf(0f, .52f, 1f), Shader.TileMode.CLAMP)
                canvas.drawCircle(cx, cy, u * .3f, paint); paint.shader = null
                val clipped = canvas.save()
                path.reset(); path.addCircle(cx, cy, u * .3f, Path.Direction.CW); canvas.clipPath(path)
                repeat(18) { i ->
                    val p = points[i]
                    paint.color = if (earth) 0x55FFC578 else 0x33E3AD6A
                    canvas.drawOval(cx + (p[0] - .55f) * u * .65f, cy + (p[1] - .55f) * u * .65f,
                        cx + (p[0] - .4f) * u * .65f, cy + (p[1] - .46f) * u * .65f, paint)
                }
                canvas.restoreToCount(clipped)
                if (!earth) {
                    // Debris around a newly forming hot Moon; no later impact craters.
                    paint.color = 0xBBD3B79A.toInt()
                    repeat(12) { i ->
                        val angle = i * .52f
                        canvas.drawCircle(cx + cos(angle) * u * .43f,
                            cy + sin(angle) * u * .35f, u * .008f, paint)
                    }
                }
            }
            Kind.WEB -> {
                val nodes = points.take(22)
                paint.strokeWidth = u * .004f
                nodes.forEachIndexed { i, a ->
                    nodes.drop(i + 1).forEach { b ->
                        if (hypot(a[0] - b[0], a[1] - b[1]) < .28f) {
                            paint.color = 0x4B7AC9D3
                            canvas.drawLine(w * (.08f + a[0] * .84f), h * (.08f + a[1] * .84f),
                                w * (.08f + b[0] * .84f), h * (.08f + b[1] * .84f), paint)
                        }
                    }
                    halo(canvas, w * (.08f + a[0] * .84f), h * (.08f + a[1] * .84f), u * .04f, kind.color, 160)
                }
            }
            Kind.EXPANSION -> {
                paint.style = Paint.Style.STROKE; paint.strokeWidth = u * .01f
                repeat(5) { i ->
                    paint.color = Color.argb(180 - i * 24, 168, 177, 247)
                    canvas.drawOval(cx - u * (.08f + i * .085f), cy - u * (.04f + i * .065f),
                        cx + u * (.08f + i * .085f), cy + u * (.04f + i * .065f), paint)
                }
                paint.style = Paint.Style.FILL
            }
            Kind.LIGHT -> {
                repeat(75) { i ->
                    val p = points[i]
                    val color = if (i % 2 == 0) 0xFF7BB9D8.toInt() else 0xFFEAB685.toInt()
                    halo(canvas, w * (.12f + p[0] * .76f), h * (.12f + p[1] * .76f), u * .075f, color, 120)
                }
            }
            Kind.STARS, Kind.DARK, Kind.SUPERNOVA -> {
                halo(canvas, cx, cy, u * .47f, kind.color, if (kind == Kind.DARK) 25 else 70)
                repeat(if (kind == Kind.DARK) 8 else 28) { i ->
                    val p = points[i]
                    halo(canvas, w * (.08f + p[0] * .84f), h * (.08f + p[1] * .84f),
                        u * (.015f + p[2] * .025f), kind.color, if (kind == Kind.DARK) 60 else 220)
                }
                if (kind == Kind.SUPERNOVA) {
                    halo(canvas, cx, cy, u * .3f, kind.color, 210)
                    paint.color = Color.WHITE; canvas.drawCircle(cx, cy, u * .035f, paint)
                }
            }
        }
        canvas.restoreToCount(save)
    }

    /** Emblems of environments and fossils, not reconstructions of a particular locality or species. */
    private fun drawGeology(canvas: Canvas, w: Float, h: Float, kind: Kind) {
        val u = min(w, h)
        val cx = w * .5f
        val base = h * .78f
        val extent = min(w * .44f, h * 1.35f)
        halo(canvas, cx, h * .5f, u * .6f, kind.color, 30)
        // Stratified foundation, also legible in the small band emblems.
        repeat(4) { layer ->
            val y = base + u * layer * .052f
            paint.color = (kind.color and 0x00ffffff) or ((90 - layer * 16) shl 24)
            paint.strokeWidth = u * .026f; paint.style = Paint.Style.STROKE
            path.reset(); path.moveTo(cx - extent, y)
            path.cubicTo(cx - extent * .3f, y - u * .055f, cx + extent * .25f, y + u * .04f, cx + extent, y)
            canvas.drawPath(path, paint)
        }
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = kind.color; paint.strokeWidth = u * .012f
        when (kind) {
            Kind.STRATA -> {
                repeat(6) { layer ->
                    val y = base - u * (.05f + layer * .075f)
                    path.reset(); path.moveTo(cx - extent * .85f, y)
                    path.cubicTo(cx - extent * .22f, y - u * .2f, cx + extent * .2f, y + u * .12f, cx + extent * .85f, y - u * .05f)
                    paint.alpha = 100 + layer * 23; canvas.drawPath(path, paint)
                }
            }
            Kind.MICROBIAL -> {
                repeat(3) { mound ->
                    val x = cx + (mound - 1) * u * .36f
                    repeat(5) { layer ->
                        val r = u * (.16f - layer * .025f)
                        canvas.drawArc(x - r, base - r * 1.5f, x + r, base + r * .35f, 180f, 180f, false, paint)
                    }
                }
                paint.alpha = 85
                repeat(3) { wave -> canvas.drawLine(cx - extent, h * (.22f + wave * .12f), cx + extent, h * (.22f + wave * .12f), paint) }
            }
            Kind.MARINE -> {
                // Trilobite-like fossil: no later ammonites in Cambrian bands.
                val cy = h * .43f
                paint.style = Paint.Style.FILL; paint.alpha = 55
                canvas.drawOval(cx - u * .19f, cy - u * .25f, cx + u * .19f, cy + u * .27f, paint)
                paint.style = Paint.Style.STROKE; paint.alpha = 235
                canvas.drawOval(cx - u * .18f, cy - u * .24f, cx + u * .18f, cy + u * .26f, paint)
                canvas.drawLine(cx, cy - u * .2f, cx, cy + u * .2f, paint)
                repeat(7) { segment ->
                    val y = cy - u * .12f + segment * u * .05f
                    val half = u * (.15f - abs(segment - 2) * .015f)
                    path.reset(); path.moveTo(cx - half, y + u * .025f)
                    path.quadTo(cx, y - u * .035f, cx + half, y + u * .025f)
                    canvas.drawPath(path, paint)
                }
            }
            Kind.FERN -> {
                canvas.drawLine(cx, base, cx + u * .035f, h * .17f, paint)
                repeat(8) { leaf ->
                    val y = base - u * (.07f + leaf * .065f)
                    val reach = u * (.25f - leaf * .025f)
                    for (side in listOf(-1, 1)) {
                        path.reset(); path.moveTo(cx, y)
                        path.quadTo(cx + side * reach * .6f, y - u * .015f, cx + side * reach, y - u * .07f)
                        canvas.drawPath(path, paint)
                    }
                }
            }
            Kind.CONIFERS -> {
                repeat(5) { tree ->
                    val x = cx + (tree - 2) * u * .3f
                    val size = u * if (tree % 2 == 0) .54f else .38f
                    canvas.drawLine(x, base, x, base - size, paint)
                    repeat(4) { tier ->
                        val y = base - size + size * (.18f + tier * .19f)
                        val reach = size * (.12f + tier * .05f)
                        path.reset(); path.moveTo(x - reach, y + size * .12f); path.lineTo(x, y)
                        path.lineTo(x + reach, y + size * .12f); canvas.drawPath(path, paint)
                    }
                }
            }
            Kind.FLOWER -> {
                val cy = h * .33f
                canvas.drawLine(cx, base, cx, cy, paint)
                for (side in listOf(-1, 1)) {
                    path.reset(); path.moveTo(cx, base - u * .08f)
                    path.quadTo(cx + side * u * .25f, base - u * .1f, cx + side * u * .2f, base - u * .3f)
                    path.quadTo(cx, base - u * .25f, cx, base - u * .08f); canvas.drawPath(path, paint)
                }
                repeat(5) { petal ->
                    val angle = petal * 2f * PI.toFloat() / 5 - PI.toFloat() / 2
                    canvas.drawCircle(cx + cos(angle) * u * .105f, cy + sin(angle) * u * .105f, u * .074f, paint)
                }
                paint.style = Paint.Style.FILL; canvas.drawCircle(cx, cy, u * .038f, paint)
            }
            Kind.GRASSLAND -> {
                repeat(11) { tuft ->
                    val x = cx + (tuft - 5) * extent * .17f
                    val rise = u * (.16f + points[tuft][2] * .23f)
                    for (side in -1..1) {
                        path.reset(); path.moveTo(x, base)
                        path.quadTo(x + side * u * .03f, base - rise * .7f, x + side * u * .08f, base - rise)
                        canvas.drawPath(path, paint)
                    }
                }
            }
            Kind.ICE -> {
                repeat(3) { peak ->
                    val x = cx + (peak - 1) * u * .4f
                    val high = base - u * if (peak == 1) .62f else .43f
                    path.reset(); path.moveTo(x - u * .35f, base); path.lineTo(x, high)
                    path.lineTo(x + u * .35f, base); path.close()
                    paint.style = Paint.Style.FILL; paint.alpha = 60; canvas.drawPath(path, paint)
                    paint.style = Paint.Style.STROKE; paint.alpha = 235; canvas.drawPath(path, paint)
                    path.reset(); path.moveTo(x - u * .12f, high + u * .21f)
                    path.lineTo(x, high + u * .15f); path.lineTo(x + u * .1f, high + u * .18f)
                    canvas.drawPath(path, paint)
                }
            }
            else -> Unit
        }
        paint.style = Paint.Style.FILL; paint.alpha = 255; paint.strokeCap = Paint.Cap.BUTT
    }

    private fun halo(canvas: Canvas, x: Float, y: Float, radius: Float, color: Int, alpha: Int) {
        val rgb = color and 0x00ffffff
        paint.shader = RadialGradient(x, y, radius.coerceAtLeast(.1f),
            intArrayOf(rgb or (alpha shl 24), rgb or ((alpha / 3) shl 24), rgb),
            floatArrayOf(0f, .35f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(x, y, radius, paint); paint.shader = null
    }
}

class CosmicIllustrationView(context: Context, private val kind: CosmicArt.Kind) : View(context) {
    private val art = CosmicArt()
    private val box = RectF()
    init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }
    override fun onDraw(canvas: Canvas) {
        box.set(0f, 0f, width.toFloat(), height.toFloat())
        art.draw(canvas, box, kind, background = true)
    }
}

@Suppress("UNUSED_PARAMETER")
class TimelineHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    override fun render(canvas: Canvas, w: Float, h: Float) {
        val art = CosmicArt()
        canvas.drawColor(0xFF0B1521.toInt())
        art.draw(canvas, RectF(w * .14f, h * .08f, w * .96f, h * .55f), CosmicArt.Kind.GALAXY, true)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF638F9E.toInt(); strokeWidth = w * .004f
        }
        canvas.drawLine(w * .1f, h * .51f, w * .9f, h * .51f, paint)
        listOf(.12f to CosmicArt.Kind.ORIGIN, .5f to CosmicArt.Kind.STARS, .82f to CosmicArt.Kind.SUN).forEach { (x, kind) ->
            art.draw(canvas, RectF(w * x - w * .11f, h * .4f, w * x + w * .11f, h * .62f), kind)
        }
        com.Atom2Universe.app.science.ScienceTileArt.bottomShade(canvas, w, h, 0xFF0B1521.toInt())
    }
}
