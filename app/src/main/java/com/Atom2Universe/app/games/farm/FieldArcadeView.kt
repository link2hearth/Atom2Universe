package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.atan2
import com.Atom2Universe.app.R

/**
 * Finger steering is free-roaming: the tractor chases the finger and obstacles get in the way.
 * A pass ends itself - no button - once the footprint is fully covered or the tank runs dry.
 */
class FieldArcadeView(context: Context, private val state: FarmState, private val changed: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val land = RectF()
    private val sprites = FarmSprites(context)
    private val scenery by lazy { FarmScenery(sprites) }
    private val atlas = context.assets.open("generated/garden/field_vehicles.png").use { BitmapFactory.decodeStream(it) }
    // Measured alpha bounds, including the combine's header which extends across an atlas third.
    private val sources = listOf(Rect(125, 96, 461, 791), Rect(644, 96, 1120, 802), Rect(1161, 80, 1754, 809))
    private val field get() = state.largeFields.fields[state.largeFields.selected]
    private var finger: PointF? = null
    private var angle = 0f
    private var blocked = false
    private var lastTick = 0L
    private var lastUi = 0L
    private var lastSave = 0L
    // The fine mask is painted into a low-res bitmap once per frame (bulk setPixels) rather than
    // one drawRect per fine cell, so the tractor's footprint can be far finer than the driving grid.
    private var maskBitmap: Bitmap? = null
    private var maskPixels = IntArray(0)
    // Grass tiles, the orchard fringe and the soil-bed border never change frame to frame, so they're
    // rendered once into a cached bitmap instead of redrawing dozens of sprites at 60fps.
    private var bgBitmap: Bitmap? = null
    private var bgW = -1; private var bgH = -1; private var bgCols = -1; private var bgRows = -1
    // The wheat/seed sprites are real crop art, not cheap primitives, so this layer is rebuilt on a
    // throttle rather than every animation frame while the tractor is actively painting the mask.
    private var cropBitmap: Bitmap? = null
    private var cropW = -1; private var cropH = -1; private var cropPhase = -1; private var lastCropBuild = 0L
    var dismissBubble: (() -> Boolean)? = null
    private fun conclude() { state.finishField(); changed(); stop() }
    private val drive = object : Runnable {
        override fun run() {
            val target = finger ?: return
            val f = field
            if (f.phase == 2) { stop(); return }
            val now = android.os.SystemClock.uptimeMillis()
            val dt = (if (lastTick == 0L) 16L else now - lastTick).coerceIn(1L, 64L) / 1000f
            lastTick = now
            val cw = land.width() / f.columns
            val tx = (target.x - land.left) / cw; val ty = (target.y - land.top) / cw
            val px = f.pos.x; val py = f.pos.y
            val moved = f.move(PointF(tx, ty), dt)
            if (moved > 0f) {
                val ddx = f.pos.x - px; val ddy = f.pos.y - py
                if (abs(ddx) > 1e-4f || abs(ddy) > 1e-4f) {
                    angle = ((Math.toDegrees(atan2(ddx.toDouble(), -ddy.toDouble())) + 360) % 360).toFloat()
                }
                blocked = false
                if (f.coverage == 100) { invalidate(); conclude(); return }
                if (now - lastSave > 500) { state.save(); lastSave = now }
            } else if (f.distanceUsed >= f.budget) { invalidate(); conclude(); return }
            else if (!blocked) { blocked = true; performHapticFeedback(HapticFeedbackConstants.REJECT) }
            if (now - lastUi > 90) { lastUi = now; changed() }
            invalidate(); postDelayed(this, 16)
        }
    }
    init { contentDescription = context.getString(R.string.farm_field_steer); isFocusable = true }
    fun stop() { finger = null; removeCallbacks(drive) }
    fun resetMotion() { stop(); blocked = false; invalidate() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (dismissBubble?.invoke() == true) return true
                if (!land.contains(event.x, event.y) || field.phase == 2) return true
                if (!state.startField()) return true
                finger = PointF(event.x, event.y); blocked = false; lastTick = 0L; removeCallbacks(drive); post(drive); changed()
            }
            MotionEvent.ACTION_MOVE -> finger?.set(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> {
                stop(); state.save(); changed(); performClick()
            }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    private fun drawObstacle(c: Canvas, cell: Float, o: Obstacle) {
        val ox = land.left + o.cx * cell; val oy = land.top + o.cy * cell
        when (o) {
            // Match the main farm's pixel rocks; seed from field coordinates, not screen size.
            is Obstacle.Trunk -> {
                val d = o.r * cell * 2.6f
                scenery.rock(c, RectF(ox - d / 2, oy - d / 2, ox + d / 2, oy + d / 2),
                    o.cx.toBits() xor (o.cy.toBits() * 31))
            }
            // The bush tile is round, so a long hedge is a short row of bushes rather than one stretched sprite.
            is Obstacle.Hedge -> {
                val horizontal = o.w >= o.h
                val length = maxOf(o.w, o.h) * cell; val thickness = minOf(o.w, o.h) * cell
                val count = (length / thickness).toInt().coerceIn(2, 6)
                val bush = thickness * 1.7f
                for (k in 0 until count) {
                    val t = (k + .5f) / count - .5f
                    val bx = if (horizontal) ox + t * length else ox
                    val by = if (horizontal) oy else oy + t * length
                    scenery.bush(c, RectF(bx - bush / 2, by - bush / 2, bx + bush / 2, by + bush / 2), 0f,
                        (o.cx.toBits() xor o.cy.toBits()) + k * 31)
                }
            }
        }
    }
    /**
     * Rebuilds the soil texture as a maskCols x maskRows bitmap, blitted scaled up over [land].
     * Ground outside the current footprint is left transparent so the grass underneath shows through;
     * unploughed ground (still untouched wild field) gets only a dimming wash for the same reason,
     * rather than a flat dirt fill it hasn't earned yet.
     */
    private fun maskBitmap(f: LargeField): Bitmap {
        val w = f.maskCols; val h = f.maskRows
        var bmp = maskBitmap
        if (bmp == null || bmp.width != w || bmp.height != h) {
            bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); maskBitmap = bmp; maskPixels = IntArray(w * h)
        }
        val unploughed = Color.argb(80, 45, 60, 30); val worked = Color.rgb(126, 91, 64); val harvested = Color.rgb(172, 140, 82)
        for (i in 0 until w * h) {
            maskPixels[i] = if (!f.eligible[i]) Color.TRANSPARENT else {
                val p = f.painted[i]
                when { f.phase == 0 && !p -> unploughed; f.phase == 3 && p -> harvested; else -> worked }
            }
        }
        bmp.setPixels(maskPixels, 0, w, 0, 0, w, h)
        return bmp
    }
    /** Grass tiling, the orchard fringe and the soil-bed border, cached until size or field changes. */
    private fun background(f: LargeField, cell: Float): Bitmap {
        var bmp = bgBitmap
        if (bmp == null || bgW != width || bgH != height || bgCols != f.columns || bgRows != f.rows) {
            bmp = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
            val bc = Canvas(bmp)
            val tile = 68f
            var gy = 0; var ty = 0f
            while (ty < height) {
                var gx = 0; var tx = 0f
                while (tx < width) {
                    bc.save()
                    if ((gx + gy) % 2 == 0) bc.scale(-1f, 1f, tx + tile / 2, ty)
                    sprites.grass(bc, RectF(tx, ty, tx + tile, ty + tile), gx, gy)
                    bc.restore()
                    tx += tile; gx++
                }
                ty += tile; gy++
            }
            for (i in 0..10) {
                val y = i * height / 10f
                val w = minOf(land.left - 13f, cell * .8f).coerceAtLeast(8f)
                sprites.crop(bc, FarmCrop.APPLE, i % 2, 3, RectF(-w * .2f, y - w, w, y + w * .35f))
                sprites.crop(bc, FarmCrop.APPLE, (i + 1) % 2, 3, RectF(width - w, y - w * .4f, width + w * .2f, y + w))
            }
            // A stroke, not a fill: the bed only frames the field, it must never cover the grass
            // showing through the mask's transparent (outside-footprint) or dimmed (unploughed) cells.
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 12f; paint.color = Color.rgb(221, 201, 157)
            bc.drawRoundRect(RectF(land).apply { inset(-6f, -6f) }, 18f, 18f, paint)
            paint.style = Paint.Style.FILL
            bgBitmap = bmp; bgW = width; bgH = height; bgCols = f.columns; bgRows = f.rows
        }
        return bmp
    }
    /**
     * Wheat sprigs (growing/harvest) or sprout markers (sowing), sampling the fine mask at each
     * sprite's own spot so they fill in one by one, matching what the brush actually covered.
     */
    private fun cropLayer(f: LargeField, cell: Float): Bitmap {
        val w = land.width().toInt().coerceAtLeast(1); val h = land.height().toInt().coerceAtLeast(1)
        val now = android.os.SystemClock.uptimeMillis()
        var bmp = cropBitmap
        if (bmp == null || cropW != w || cropH != h || cropPhase != f.phase || now - lastCropBuild > 150) {
            if (bmp == null || cropW != w || cropH != h) {
                bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); cropBitmap = bmp; cropW = w; cropH = h
            } else bmp.eraseColor(Color.TRANSPARENT)
            val cc = Canvas(bmp)
            if (f.phase == 2 || f.phase == 3) {
                val stage = if (f.phase == 3) 4 else 3
                for (i in 0 until f.size) {
                    val col = i % f.columns; val row = i / f.columns
                    val x = col * cell; val y = row * cell
                    for (a in 0..3) for (b in 0..3) {
                        val mx = (col * LargeField.SUB + (a + .5f) * LargeField.SUB / 4).toInt().coerceIn(0, f.maskCols - 1)
                        val my = (row * LargeField.SUB + (b + .5f) * LargeField.SUB / 4).toInt().coerceIn(0, f.maskRows - 1)
                        val idx = my * f.maskCols + mx
                        if (!f.eligible[idx] || (f.phase == 3 && f.painted[idx])) continue
                        val sx = x + (a + .5f) * cell / 4; val sy = y + (b + .85f) * cell / 4
                        val sw = cell / 4 * .95f; val sh = cell / 4 * 1.35f
                        sprites.crop(cc, FarmCrop.WHEAT, (a + b) % 2, stage, RectF(sx - sw / 2, sy - sh, sx + sw / 2, sy))
                    }
                }
            } else if (f.phase == 1) {
                for (i in 0 until f.size) {
                    val col = i % f.columns; val row = i / f.columns
                    val x = col * cell; val y = row * cell
                    for (a in 1..3) for (b in 1..3) {
                        val mx = (col * LargeField.SUB + a * LargeField.SUB / 4f).toInt().coerceIn(0, f.maskCols - 1)
                        val my = (row * LargeField.SUB + b * LargeField.SUB / 4f).toInt().coerceIn(0, f.maskRows - 1)
                        val idx = my * f.maskCols + mx
                        if (!f.eligible[idx] || !f.painted[idx]) continue
                        val sx = x + a * cell / 4; val sy = y + b * cell / 4
                        val sw = cell / 6f; val sh = cell / 5f
                        sprites.crop(cc, FarmCrop.WHEAT, (a + b) % 2, 1, RectF(sx - sw / 2, sy - sh, sx + sw / 2, sy))
                    }
                }
            }
            cropPhase = f.phase; lastCropBuild = now
        }
        return bmp
    }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val f = field
        val cell = minOf((width - 48f) / f.columns, (height - 40f) / f.rows).coerceAtLeast(1f)
        land.set((width - cell * f.columns) / 2, (height - cell * f.rows) / 2, (width + cell * f.columns) / 2, (height + cell * f.rows) / 2)
        c.drawBitmap(background(f, cell), 0f, 0f, null)
        paint.isFilterBitmap = false
        c.drawBitmap(maskBitmap(f), null, land, paint)
        c.drawBitmap(cropLayer(f, cell), land.left, land.top, null)
        for (o in f.obstacles) drawObstacle(c, cell, o)
        if (f.phase != 2) {
            val x = land.left + f.pos.x * cell; val y = land.top + f.pos.y * cell
            val src = sources[if (f.phase == 3) 2 else f.phase]
            val h = cell * 1.15f; val w = h * src.width() / src.height()
            c.save(); c.rotate(angle, x, y)
            c.drawBitmap(atlas, src, RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), paint.apply { color = Color.WHITE; isFilterBitmap = true })
            c.restore()
            if (finger != null) postInvalidateOnAnimation()
        }
    }
}
