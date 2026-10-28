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
    // Where each animal was drawn this frame, for the taps. The rectangles are kept from one frame
    // to the next and only refilled: a pen redraws every animal many times a second.
    private var hitIds = LongArray(LivestockState.CAPACITY)
    private val hitRects = ArrayList<RectF>()
    private var hitCount = 0
    private val greetings = android.util.LongSparseArray<Long>()
    fun hitAnimal(x: Float, y: Float): Long? {
        for (i in hitCount - 1 downTo 0) if (hitRects[i].contains(x, y)) return hitIds[i]
        return null
    }
    fun greet(ids: Collection<Long>) {
        val now = SystemClock.uptimeMillis()
        ids.forEach { greetings.put(it, now) }
    }
    private data class Motion(val random: java.util.Random, var time: Double, var x: Double = 0.0,
                              var y: Double = 0.0, var targetX: Double = 0.0, var targetY: Double = 0.0,
                              var wait: Double = 0.0, var walk: Double = 0.0, var eat: Double = 0.0,
                              var homeX: Double = 0.0, var homeY: Double = 0.0,
                              var roamAfter: Double = 0.0, var longTrip: Boolean = false,
                              var phase: Double = 0.0, var direction: Int = 1, var placed: Boolean = false)
    private val motions = android.util.LongSparseArray<Motion>()
    /** The animals of the page being drawn, and the same in drawing order - refilled, never rebuilt. */
    private val herd = ArrayList<FarmAnimal>()
    private val drawOrder = ArrayList<FarmAnimal>()
    private val byDepth = Comparator<FarmAnimal> { a, b -> motions.get(a.id).y.compareTo(motions.get(b.id).y) }
    private val pose = FarmAnimalArt.Pose()
    private val heart = Path()
    private var lastFrame = 0L
    private fun ease(x: Double) = x.coerceIn(0.0, 1.0).let { it * it * (3 - 2 * it) }
    private val backgrounds = mutableMapOf<LivestockKind, Bitmap>()
    private var backgroundHeight = 0
    /** The pen's fences and troughs, in page units. Fixed, so not rebuilt on every frame. */
    private val obstacles = listOf(RectF(24f, 22f, 145f, 125f), RectF(200f, 70f, 273f, 120f), RectF(212f, 131f, 284f, 159f))
    private fun clear(x: Double, y: Double, size: Float, height: Int): Boolean {
        val left = x - size * .43; val right = x + size * .43
        val top = y - size * .86
        if (left < 14 || right > 306 || top < 35 || y > height - 34) return false
        for (i in obstacles.indices) {
            val box = obstacles[i]
            if (left < box.right && right > box.left && top < box.bottom && y + 3 > box.top) return false
        }
        return true
    }
    private fun route(ax: Double, ay: Double, bx: Double, by: Double, size: Float, height: Int): Boolean {
        val steps = max(1, ceil(hypot(bx - ax, by - ay) / 3).toInt())
        return (0..steps).all { val u = it.toDouble() / steps; clear(ax + (bx - ax) * u, ay + (by - ay) * u, size, height) }
    }
    /** Draws one herd's page. True while something on it moves - an empty pen has nothing to animate. */
    fun drawPage(canvas: Canvas, kind: LivestockKind, height: Int, dt: Double): Boolean {
        hitCount = 0
        val animationNow = SystemClock.uptimeMillis()
        for (i in greetings.size() - 1 downTo 0)
            if (animationNow - greetings.valueAt(i) >= 1800 || state.find(greetings.keyAt(i)) == null) greetings.removeAt(i)
        if (backgroundHeight != height) { backgrounds.clear(); backgroundHeight = height }
        paint.isFilterBitmap = false
        paint.alpha = 255
        canvas.drawBitmap(backgrounds.getOrPut(kind) { LivestockHabitatArt.bake(kind, height) }, 0f, 0f, paint)
        // Animals sold or butchered leave their motion behind; it is dropped once there are more
        // motions than animals, instead of rebuilding a set of every id on every frame.
        if (motions.size() > state.animals.size)
            for (i in motions.size() - 1 downTo 0) if (state.find(motions.keyAt(i)) == null) motions.removeAt(i)
        herd.clear()
        val animals = state.animals
        for (i in animals.indices) if (animals[i].kind == kind) herd.add(animals[i])
        // Fixed world proportions: a pair is no larger than a full herd.
        val size = when (kind) { LivestockKind.CHICKENS -> 46f; LivestockKind.SHEEP -> 55f
            LivestockKind.PIGS -> 53f; LivestockKind.CATTLE -> 66f }
        for (animal in herd) {
            val bird = kind == LivestockKind.CHICKENS
            val m = motions.get(animal.id) ?: Motion(java.util.Random(animal.id * 719 + kind.ordinal),
                (animal.id * 7 % 24).toDouble()).also { motions.put(animal.id, it) }
            if (!m.placed || !clear(m.x, m.y, size, height)) {
                // Reposition only on first appearance or a screen-size change, never when buying a neighbour.
                for (attempt in 0..100) {
                    val x = 35 + m.random.nextDouble() * 250
                    val y = 180 + m.random.nextDouble() * (height - 218).coerceAtLeast(1)
                    if (clear(x, y, size, height)) { m.x = x; m.y = y; break }
                }
                m.targetX = m.x; m.targetY = m.y; m.placed = true
                m.homeX = m.x; m.homeY = m.y; m.longTrip = false
                m.wait = 7 + m.random.nextDouble() * 8
                m.roamAfter = 60 + m.random.nextDouble() * 40
            }
            m.time += dt
            if (!clear(m.targetX, m.targetY, size, height)) {
                m.targetX = m.x; m.targetY = m.y; m.wait = 0.0
            }
            if (greetings.indexOfKey(animal.id) >= 0) { m.wait = 2.0; m.targetX = m.x; m.targetY = m.y }
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
                    if (length in lengths && route(m.x, m.y, x, y, size, height)) {
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
        drawOrder.clear()
        for (i in herd.indices) drawOrder.add(herd[i])
        drawOrder.sortWith(byDepth)
        for (animal in drawOrder) {
            val m = motions.get(animal.id)
            val x = m.x.toFloat(); val bottom = m.y.toFloat()
            if (hitCount == hitRects.size) hitRects.add(RectF())
            if (hitCount == hitIds.size) hitIds = hitIds.copyOf(hitIds.size * 2)
            val target = hitRects[hitCount]
            target.set(x - size / 2, bottom - size, x + size / 2, bottom)
            hitIds[hitCount++] = animal.id
            pose.time = m.time; pose.walk = m.walk; pose.eat = m.eat; pose.phase = m.phase; pose.direction = m.direction
            sprites.livestock(canvas, animal.sprite, target, pose)
            greetings.get(animal.id)?.let { started ->
                val t = (animationNow - started) / 1800f
                val cy = bottom - size - 3 - t * 16
                paint.color = Color.argb(((1 - t) * 255).toInt().coerceIn(0, 255), 216, 112, 108)
                heart.reset()
                heart.moveTo(x, cy + 5); heart.cubicTo(x - 13, cy - 2, x - 8, cy - 12, x, cy - 5)
                heart.cubicTo(x + 8, cy - 12, x + 13, cy - 2, x, cy + 5); heart.close()
                canvas.drawPath(heart, paint)
                paint.alpha = 255
            }
        }
        if (!state.available(kind)) {
            paint.color = Color.argb(45, 76, 76, 57); canvas.drawRect(0f, 22f, 320f, height.toFloat(), paint)
        }
        return herd.isNotEmpty()
    }
    fun draw(canvas: Canvas, visible: RectF) = drawPage(canvas, LivestockKind.CHICKENS, 360, 0.0)
}
