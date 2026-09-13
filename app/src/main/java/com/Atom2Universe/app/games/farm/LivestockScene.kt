package com.Atom2Universe.app.games.farm

import android.content.Context
import android.graphics.*
import android.view.View
import com.Atom2Universe.app.R
import android.os.SystemClock
import kotlin.math.*

class LivestockIcon(context: Context, private val sprites: FarmSprites, private val id: String) : View(context) {
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        sprites.livestock(canvas, id, RectF(0f, 0f, width.toFloat(), height.toFloat()))
    }
}

class LivestockScene(private val context: Context, private val sprites: FarmSprites, private val state: LivestockState) {
    companion object {
        val pens = listOf(RectF(80f, 150f, 760f, 700f), RectF(840f, 170f, 1520f, 720f),
            RectF(80f, 800f, 760f, 1350f), RectF(840f, 820f, 1520f, 1370f))
        fun hit(x: Float, y: Float) = pens.indexOfFirst { it.contains(x, y) }.takeIf { it >= 0 }?.let { LivestockKind.entries[it] }
    }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class Motion(val random: java.util.Random, var time: Double, var x: Double = 0.0,
                              var y: Double = 0.0, var targetX: Double = 0.0, var targetY: Double = 0.0,
                              var wait: Double = 0.0, var walk: Double = 0.0, var eat: Double = 0.0,
                              var homeX: Double = 0.0, var homeY: Double = 0.0,
                              var roamAfter: Double = 0.0, var longTrip: Boolean = false,
                              var phase: Double = 0.0, var direction: Int = 1, var placed: Boolean = false)
    private val motions = mutableMapOf<Long, Motion>()
    private var lastFrame = 0L
    private fun ease(x: Double) = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
    private val backgrounds = mutableMapOf<LivestockKind, Bitmap>()
    private var backgroundHeight = 0
    fun drawPage(canvas: Canvas, kind: LivestockKind, height: Int, dt: Double) {
        if (backgroundHeight != height) { backgrounds.clear(); backgroundHeight = height }
        paint.isFilterBitmap = false
        paint.alpha = 255
        canvas.drawBitmap(backgrounds.getOrPut(kind) { LivestockHabitatArt.bake(kind, height) }, 0f, 0f, paint)
        val ids = state.animals.mapTo(mutableSetOf()) { it.id }; motions.keys.retainAll(ids)
        val herd = state.animals.filter { it.kind == kind }
        // Fixed world proportions: a pair is no larger than a full herd.
        val size = when (kind) { LivestockKind.CHICKENS -> 46f; LivestockKind.SHEEP -> 55f
            LivestockKind.PIGS -> 53f; LivestockKind.CATTLE -> 66f }
        val obstacles = listOf(RectF(24f, 22f, 145f, 125f), RectF(200f, 70f, 273f, 120f), RectF(212f, 131f, 284f, 159f))
        fun clear(x: Double, y: Double): Boolean {
            val left = x - size * .43; val right = x + size * .43
            val top = y - size * .86
            return left >= 14 && right <= 306 && top >= 35 && y <= height - 34 &&
                obstacles.none { left < it.right && right > it.left && top < it.bottom && y + 3 > it.top }
        }
        fun route(ax: Double, ay: Double, bx: Double, by: Double): Boolean {
            val steps = max(1, ceil(hypot(bx - ax, by - ay) / 3).toInt())
            return (0..steps).all { val u = it.toDouble() / steps; clear(ax + (bx - ax) * u, ay + (by - ay) * u) }
        }
        herd.forEach { animal ->
            val bird = kind == LivestockKind.CHICKENS
            val m = motions.getOrPut(animal.id) { Motion(java.util.Random(animal.id * 719 + kind.ordinal), (animal.id * 7 % 24).toDouble()) }
            if (!m.placed || !clear(m.x, m.y)) {
                // Reposition only on first appearance or a screen-size change, never when buying a neighbour.
                for (attempt in 0..100) {
                    val x = 35 + m.random.nextDouble() * 250
                    val y = 180 + m.random.nextDouble() * (height - 218).coerceAtLeast(1)
                    if (clear(x, y)) { m.x = x; m.y = y; break }
                }
                m.targetX = m.x; m.targetY = m.y; m.placed = true
                m.homeX = m.x; m.homeY = m.y; m.longTrip = false
                m.wait = 7 + m.random.nextDouble() * 8
                m.roamAfter = 60 + m.random.nextDouble() * 40
            }
            m.time += dt
            if (!clear(m.targetX, m.targetY)) {
                m.targetX = m.x; m.targetY = m.y; m.wait = 0.0
            }
            m.wait = (m.wait - dt).coerceAtLeast(0.0)
            m.roamAfter = (m.roamAfter - dt).coerceAtLeast(0.0)
            var distance = hypot(m.targetX - m.x, m.targetY - m.y)
            if (distance < .1 && m.wait <= 0) {
                val wander = m.roamAfter <= 0
                for (attempt in 0..48) {
                    val angle = m.random.nextDouble() * PI * 2
                    val radius = sqrt(m.random.nextDouble()) * 18
                    val x = if (wander) 35 + m.random.nextDouble() * 250 else m.homeX + cos(angle) * radius
                    val y = if (wander) 90 + m.random.nextDouble() * (height - 125).coerceAtLeast(1) else m.homeY + sin(angle) * radius
                    val length = hypot(x - m.x, y - m.y)
                    val lengths = if (wander) 40.0..100.0 else 3.0..14.0
                    if (length in lengths && route(m.x, m.y, x, y)) {
                        m.longTrip = wander
                        m.targetX = x; m.targetY = y; distance = length; break
                    }
                }
                if (distance < .1) m.wait = 1.0
            }
            val walk = if (distance > .1) 1.0 else 0.0
            val eat = if (m.wait > .4 && animal.id % 3 != 0L && sin(m.time * .8) > .15) 1.0 else 0.0
            val blend = 1 - exp(-dt * 5)
            m.walk += (walk - m.walk) * blend; m.eat += (eat - m.eat) * blend
            val scale = min(size / (if (bird) 92.0 else 120.0), size / (if (bird) 78.0 else 91.0)) *
                (if (!animal.adult) { if (bird) .66 else .74 } else 1.0)
            if (distance > .1) {
                val step = min(distance, dt * (if (bird) 5.2 else 4.6) * m.walk)
                val dx = m.targetX - m.x; val dy = m.targetY - m.y
                if (abs(dx) > .5) m.direction = if (dx > 0) 1 else -1
                m.x += dx / distance * step; m.y += dy / distance * step
                // Foot cadence follows actual distance, including diagonal travel and deceleration.
                m.phase += step / ((if (bird) 4.77 / 5 else 6.16 / 3) * scale)
                if (distance - step < .1) {
                    m.x = m.targetX; m.y = m.targetY
                    m.wait = 7 + m.random.nextDouble() * 8
                    if (m.longTrip) {
                        // Settle in the new patch before considering another long walk.
                        m.homeX = m.x; m.homeY = m.y
                        m.wait = 24 + m.random.nextDouble() * 8
                        m.roamAfter = 60 + m.random.nextDouble() * 40
                        m.longTrip = false
                    }
                }
            }
        }
        // Back-to-front drawing makes crossings read naturally instead of following purchase order.
        herd.sortedBy { motions.getValue(it.id).y }.forEach { animal ->
            val m = motions.getValue(animal.id)
            val x = m.x.toFloat(); val bottom = m.y.toFloat()
            sprites.livestock(canvas, animal.sprite, RectF(x - size / 2, bottom - size, x + size / 2, bottom),
                FarmAnimalArt.Pose(m.time, m.walk, m.eat, m.phase, m.direction))
        }
        if (!state.available(kind)) {
            paint.color = Color.argb(45, 76, 76, 57); canvas.drawRect(0f, 22f, 320f, height.toFloat(), paint)
        }
    }
    fun draw(canvas: Canvas, visible: RectF) = drawPage(canvas, LivestockKind.CHICKENS, 360, 0.0)
}
