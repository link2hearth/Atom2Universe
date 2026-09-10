package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import com.Atom2Universe.app.R

/** Finger steering advances one lane at a time; lifting the finger stops the machine. */
class FieldArcadeView(context: Context, private val state: FarmState, private val changed: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val land = RectF()
    private val sprites = FarmSprites(context)
    private val atlas = context.assets.open("generated/garden/field_vehicles.png").use { BitmapFactory.decodeStream(it) }
    // Measured alpha bounds, including the combine's header which extends across an atlas third.
    private val sources = listOf(Rect(125, 96, 461, 791), Rect(644, 96, 1120, 802), Rect(1161, 80, 1754, 809))
    private val field get() = state.largeFields.fields[state.largeFields.selected]
    private var finger: PointF? = null
    private var angle = 0f
    private var blocked = false
    private var previous = -1
    private var movedAt = 0L
    var dismissBubble: (() -> Boolean)? = null
    private val drive = object : Runnable {
        override fun run() {
            val target = finger ?: return
            val f = field
            if (f.phase == 2 || f.route.isEmpty()) { stop(); return }
            val cw = land.width() / f.columns; val ch = land.height() / f.rows
            val x = land.left + (f.current % f.columns + .5f) * cw
            val y = land.top + (f.current / f.columns + .5f) * ch
            val dx = (target.x - x) / cw; val dy = (target.y - y) / ch
            if (maxOf(abs(dx), abs(dy)) > .35f) {
                val horizontal = abs(dx) > abs(dy)
                val next = f.current + if (horizontal) { if (dx > 0) 1 else -1 } else { if (dy > 0) f.columns else -f.columns }
                val old = f.current
                if (f.move(next)) {
                    angle = if (horizontal) { if (dx > 0) 90f else 270f } else { if (dy > 0) 180f else 0f }
                    previous = old; movedAt = android.os.SystemClock.uptimeMillis(); blocked = false
                    state.save(); changed()
                } else if (!blocked) { blocked = true; performHapticFeedback(HapticFeedbackConstants.REJECT) }
            }
            invalidate(); postDelayed(this, 140)
        }
    }
    init { contentDescription = context.getString(R.string.farm_field_steer); isFocusable = true }
    fun stop() { finger = null; removeCallbacks(drive) }
    fun resetMotion() { stop(); previous = -1; blocked = false; invalidate() }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (dismissBubble?.invoke() == true) return true
                if (!land.contains(event.x, event.y) || field.phase == 2) return true
                if (!state.startField()) return true
                finger = PointF(event.x, event.y); blocked = false; removeCallbacks(drive); post(drive); changed()
            }
            MotionEvent.ACTION_MOVE -> finger?.set(event.x, event.y)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL, MotionEvent.ACTION_POINTER_DOWN -> { stop(); performClick() }
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val f = field
        c.drawColor(Color.rgb(155, 183, 120))
        val cell = minOf((width - 48f) / f.columns, (height - 40f) / f.rows).coerceAtLeast(1f)
        land.set((width - cell * f.columns) / 2, (height - cell * f.rows) / 2, (width + cell * f.columns) / 2, (height + cell * f.rows) / 2)
        // Frame the playable surface with an orchard fringe, never covering the driving lanes.
        for (i in 0..10) {
            val y = i * height / 10f
            val w = minOf(land.left - 13f, cell * .8f).coerceAtLeast(8f)
            sprites.crop(c, FarmCrop.APPLE, i % 2, 3, RectF(-w * .2f, y - w, w, y + w * .35f))
            sprites.crop(c, FarmCrop.APPLE, (i + 1) % 2, 3, RectF(width - w, y - w * .4f, width + w * .2f, y + w))
        }
        paint.color = Color.rgb(221, 201, 157)
        c.drawRoundRect(RectF(land).apply { inset(-12f, -12f) }, 18f, 18f, paint)
        paint.color = Color.rgb(137, 105, 75); c.drawRoundRect(land, 8f, 8f, paint)
        val visited = f.route.toHashSet(); val eligible = f.eligible.toHashSet()
        for (i in 0 until f.size) {
            val x = land.left + i % f.columns * cell; val y = land.top + i / f.columns * cell
            val worked = i in visited
            paint.color = when {
                i !in eligible -> Color.rgb(139, 167, 107)
                f.phase == 0 && !worked -> Color.rgb(166, 142, 99)
                f.phase == 3 && worked -> Color.rgb(172, 140, 82)
                else -> Color.rgb(126, 91, 64)
            }
            c.drawRect(x, y, x + cell + .5f, y + cell + .5f, paint)
            if (i !in eligible) continue
            for (lane in 0..3) {
                paint.color = Color.argb(50, 66, 43, 28); paint.strokeWidth = 2f
                c.drawLine(x + cell * (lane + .5f) / 4, y + 2, x + cell * (lane + .5f) / 4, y + cell - 2, paint)
            }
            val wheat = f.phase == 3 && !worked || f.phase == 2
            if (wheat) {
                val mature = f.phase == 3
                for (a in 0..3) for (b in 0..3) {
                    val sx = x + (a + .5f) * cell / 4; val sy = y + (b + .7f) * cell / 4
                    paint.color = if (mature) Color.rgb(239, 194, 93) else Color.rgb(158, 189, 107)
                    paint.strokeWidth = cell * .027f
                    c.drawLine(sx, sy, sx, sy - cell * .16f, paint)
                    c.drawOval(sx - cell * .035f, sy - cell * .19f, sx + cell * .035f, sy - cell * .065f, paint)
                    paint.color = if (mature) Color.rgb(255, 220, 131) else Color.rgb(186, 209, 128)
                    c.drawLine(sx, sy - cell * .1f, sx - cell * .055f, sy - cell * .15f, paint)
                }
            } else if (f.phase == 1 && worked) {
                paint.color = Color.rgb(232, 201, 133)
                for (a in 1..3) for (b in 1..3) c.drawCircle(x + a * cell / 4, y + b * cell / 4, cell * .022f, paint)
            }
        }
        if (f.phase != 2) {
            for (i in f.eligible) if (i !in visited && (f.route.isEmpty() && i == f.start || f.route.isNotEmpty() && f.adjacent(f.current, i))) {
                paint.color = Color.argb(165, 247, 242, 199)
                c.drawCircle(land.left + (i % f.columns + .5f) * cell, land.top + (i / f.columns + .5f) * cell, cell * .10f, paint)
            }
            val progress = ((android.os.SystemClock.uptimeMillis() - movedAt) / 120f).coerceIn(0f, 1f)
            val old = previous.takeIf { it >= 0 && progress < 1 } ?: f.current
            val x = land.left + (old % f.columns + (f.current % f.columns - old % f.columns) * progress + .5f) * cell
            val y = land.top + (old / f.columns + (f.current / f.columns - old / f.columns) * progress + .5f) * cell
            val src = sources[if (f.phase == 3) 2 else f.phase]
            val h = cell * 1.15f; val w = h * src.width() / src.height()
            c.save(); c.rotate(angle, x, y)
            c.drawBitmap(atlas, src, RectF(x - w / 2, y - h / 2, x + w / 2, y + h / 2), paint.apply { color = Color.WHITE; isFilterBitmap = true })
            c.restore()
            if (progress < 1) postInvalidateOnAnimation()
        }
    }
}
