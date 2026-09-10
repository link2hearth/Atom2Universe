package com.Atom2Universe.app.games.farm

import android.graphics.PointF
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

/** A hedge or tree trunk blocking the tractor. Coordinates are in cell units. */
sealed class Obstacle(val cx: Float, val cy: Float) {
    abstract fun contains(x: Float, y: Float): Boolean
    abstract fun overlaps(o: Obstacle, margin: Float): Boolean
    class Trunk(cx: Float, cy: Float, val r: Float) : Obstacle(cx, cy) {
        override fun contains(x: Float, y: Float) = hypot((x - cx).toDouble(), (y - cy).toDouble()) <= r
        override fun overlaps(o: Obstacle, margin: Float) = when (o) {
            is Trunk -> hypot((o.cx - cx).toDouble(), (o.cy - cy).toDouble()) < r + o.r + margin
            is Hedge -> o.overlaps(this, margin)
        }
    }
    class Hedge(cx: Float, cy: Float, val w: Float, val h: Float) : Obstacle(cx, cy) {
        override fun contains(x: Float, y: Float) = abs(x - cx) <= w / 2 && abs(y - cy) <= h / 2
        override fun overlaps(o: Obstacle, margin: Float) = when (o) {
            is Hedge -> abs(o.cx - cx) < (w + o.w) / 2 + margin && abs(o.cy - cy) < (h + o.h) / 2 + margin
            is Trunk -> abs(o.cx - cx) < w / 2 + o.r + margin && abs(o.cy - cy) < h / 2 + o.r + margin
        }
    }
}

/**
 * A field worked by free-roaming tractor: the fine mask is the paintable footprint, the previous
 * pass's painted mask becomes the next pass's eligible footprint. Obstacles are redrawn every pass.
 */
class LargeField(val index: Int) {
    val columns = 6 + index
    val rows = 9 + index
    val size get() = columns * rows
    val price get() = listOf(0, 400, 1200)[index]
    val seedCost get() = listOf(5, 10, 20)[index]
    val maskCols = columns * SUB
    val maskRows = rows * SUB
    var phase = 0 // plough, seed, growing, harvest
    var readyAt = 0L
    var paid = false
    var started = false
    var eligible = BooleanArray(maskCols * maskRows) { true }
    var painted = BooleanArray(maskCols * maskRows)
    var obstacles: List<Obstacle> = emptyList()
    var pos = startPoint()
    var distanceUsed = 0f
    init { obstacles = randomObstacles() }

    private val eligibleArea get() = eligible.count { it } / (SUB * SUB).toFloat()
    val budget get() = eligibleArea * BUDGET_FACTOR
    val fuel get() = (1f - distanceUsed / budget).coerceIn(0f, 1f)
    val coverage: Int get() {
        var elig = 0; var done = 0
        for (i in eligible.indices) if (eligible[i]) { elig++; if (painted[i]) done++ }
        return if (elig == 0) 0 else done * 100 / elig
    }
    val complete get() = eligible.all { it } && coverage == 100

    private fun startPoint(): PointF {
        val i = eligible.indexOfFirst { it }.coerceAtLeast(0)
        return PointF((i % maskCols + .5f) / SUB, (i / maskCols + .5f) / SUB)
    }
    // Only obstacles and the field's outer edge stop the tractor: ground left over from a previous,
    // incomplete pass just doesn't take the crop - it was never meant to be an invisible wall.
    private fun blocked(x: Float, y: Float): Boolean {
        if (x < .02f || x > columns - .02f || y < .02f || y > rows - .02f) return true
        return obstacles.any { it.contains(x, y) }
    }
    private fun paintAt(x: Float, y: Float) {
        val x0 = ((x - BRUSH) * SUB).toInt().coerceIn(0, maskCols - 1); val x1 = ((x + BRUSH) * SUB).toInt().coerceIn(0, maskCols - 1)
        val y0 = ((y - BRUSH) * SUB).toInt().coerceIn(0, maskRows - 1); val y1 = ((y + BRUSH) * SUB).toInt().coerceIn(0, maskRows - 1)
        for (my in y0..y1) for (mx in x0..x1) {
            val cx = (mx + .5f) / SUB; val cy = (my + .5f) / SUB
            if (hypot((cx - x).toDouble(), (cy - y).toDouble()) <= BRUSH && eligible[my * maskCols + mx]) painted[my * maskCols + mx] = true
        }
    }
    private fun tryStep(dx: Float, dy: Float): Float {
        val nx = pos.x + dx; val ny = pos.y + dy
        if (!blocked(nx, ny)) { pos.set(nx, ny); paintAt(nx, ny); return hypot(dx.toDouble(), dy.toDouble()).toFloat() }
        var moved = 0f
        if (dx != 0f && !blocked(pos.x + dx, pos.y)) { pos.x += dx; paintAt(pos.x, pos.y); moved += abs(dx) }
        if (dy != 0f && !blocked(pos.x, pos.y + dy)) { pos.y += dy; paintAt(pos.x, pos.y); moved += abs(dy) }
        return moved
    }
    /** Places the tractor on its starting dot without spending any of the distance budget. */
    fun begin() { if (!started) { pos = startPoint(); started = true; paintAt(pos.x, pos.y) } }
    /** Steers toward [target] (cell units) for [dtSeconds]; returns the distance actually covered. */
    fun move(target: PointF, dtSeconds: Float): Float {
        if (!paid || phase == 2 || distanceUsed >= budget) return 0f
        begin()
        val dx0 = target.x - pos.x; val dy0 = target.y - pos.y
        val dist = hypot(dx0.toDouble(), dy0.toDouble()).toFloat()
        if (dist < .04f) return 0f
        val step = minOf(SPEED * dtSeconds, dist, budget - distanceUsed)
        val moved = tryStep(dx0 / dist * step, dy0 / dist * step)
        distanceUsed += moved
        return moved
    }
    private fun randomObstacles(seed: Long = Random.nextLong()): List<Obstacle> {
        val rnd = Random(seed)
        val start = startPoint()
        val list = mutableListOf<Obstacle>()
        val rocks = rnd.nextInt(1, 4)
        var guard = 0
        while (list.size < rocks && guard++ < 300) {
            val cx = rnd.nextFloat() * columns; val cy = rnd.nextFloat() * rows
            if (hypot((cx - start.x).toDouble(), (cy - start.y).toDouble()) < 1.2) continue
            val mx = (cx * SUB).toInt().coerceIn(0, maskCols - 1); val my = (cy * SUB).toInt().coerceIn(0, maskRows - 1)
            if (!eligible[my * maskCols + mx]) continue
            val o = Obstacle.Trunk(cx, cy, .32f)
            if (list.none { it.overlaps(o, .3f) }) list.add(o)
        }
        // Hedges always sit flush against one edge of the field, never adrift in the open ground.
        val hedges = rnd.nextInt(1, 3)
        var placed = 0; guard = 0
        while (placed < hedges && guard++ < 300) {
            val length = rnd.nextInt(2, 5).toFloat(); val thickness = .42f
            val vertical = rnd.nextBoolean()
            val alongSpan = if (vertical) rows else columns
            val acrossSpan = if (vertical) columns else rows
            if (length >= alongSpan) continue
            val along = rnd.nextFloat() * (alongSpan - length) + length / 2
            val nearStart = rnd.nextBoolean()
            val across = if (nearStart) thickness / 2 else acrossSpan - thickness / 2
            val cx = if (vertical) across else along; val cy = if (vertical) along else across
            val o = Obstacle.Hedge(cx, cy, if (vertical) thickness else length, if (vertical) length else thickness)
            if (hypot((cx - start.x).toDouble(), (cy - start.y).toDouble()) < 1.2) continue
            if (list.none { it is Obstacle.Hedge && it.overlaps(o, .3f) }) { list.add(o); placed++ }
        }
        return list
    }
    /** Clears progress for the next pass; obstacles are untouched so they hold for the whole cycle. */
    fun beginPass() { painted = BooleanArray(maskCols * maskRows); pos = startPoint(); distanceUsed = 0f; started = false }
    /** Starts a fresh plough-to-harvest cycle: new obstacles, then a clean first pass. */
    fun beginCycle() { obstacles = randomObstacles(); beginPass() }
    fun json(): JSONObject {
        fun mask(m: BooleanArray) = String(CharArray(m.size) { if (m[it]) '1' else '0' })
        val obs = JSONArray(obstacles.map {
            JSONObject().apply {
                when (it) {
                    is Obstacle.Trunk -> put("type", "trunk").put("cx", it.cx).put("cy", it.cy).put("r", it.r)
                    is Obstacle.Hedge -> put("type", "hedge").put("cx", it.cx).put("cy", it.cy).put("w", it.w).put("h", it.h)
                }
            }
        })
        return JSONObject().put("phase", phase).put("readyAt", readyAt).put("paid", paid).put("started", started)
            .put("eligible", mask(eligible)).put("painted", mask(painted))
            .put("posX", pos.x).put("posY", pos.y).put("distanceUsed", distanceUsed).put("obstacles", obs)
    }
    fun restore(j: JSONObject) {
        val p = j.getInt("phase"); require(p in 0..3)
        fun mask(key: String): BooleanArray {
            val s = j.getString(key); require(s.length == maskCols * maskRows)
            return BooleanArray(s.length) { s[it] == '1' }
        }
        val e = mask("eligible"); val pt = mask("painted")
        val px = j.getDouble("posX").toFloat(); val py = j.getDouble("posY").toFloat()
        require(px in 0f..columns.toFloat() && py in 0f..rows.toFloat())
        val arr = j.getJSONArray("obstacles")
        val obs = List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            when (o.getString("type")) {
                "trunk" -> Obstacle.Trunk(o.getDouble("cx").toFloat(), o.getDouble("cy").toFloat(), o.getDouble("r").toFloat())
                "hedge" -> Obstacle.Hedge(o.getDouble("cx").toFloat(), o.getDouble("cy").toFloat(), o.getDouble("w").toFloat(), o.getDouble("h").toFloat())
                else -> throw JSONException("unknown obstacle")
            }
        }
        phase = p; readyAt = j.getLong("readyAt").coerceAtLeast(0); paid = j.getBoolean("paid"); started = j.optBoolean("started")
        eligible = e; painted = pt; pos = PointF(px, py); distanceUsed = j.getDouble("distanceUsed").toFloat().coerceAtLeast(0f)
        obstacles = obs
    }
    companion object { const val SUB = 10; const val BRUSH = .55f; const val SPEED = 3.6f; const val BUDGET_FACTOR = 1f }
}

class LargeFieldState {
    val fields = List(3) { LargeField(it) }
    var unlocked = 1
    var selected = 0
    var grain = 0L
    fun json() = JSONObject().put("unlocked", unlocked).put("selected", selected).put("grain", grain)
        .put("fields", JSONArray(fields.map { it.json() }))
    fun restore(j: JSONObject?) {
        if (j == null) return
        val u = j.getInt("unlocked"); require(u in 1..3)
        val restored = List(3) { i -> LargeField(i).apply { restore(j.getJSONArray("fields").getJSONObject(i)) } }
        unlocked = u; selected = j.optInt("selected").coerceIn(0, u - 1); grain = j.optLong("grain").coerceAtLeast(0)
        restored.forEachIndexed { i, f -> fields[i].restore(f.json()) }
    }
}
