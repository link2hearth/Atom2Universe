package com.Atom2Universe.app.games.glutton

import kotlin.math.*

/**
 * A route-finding player used to measure the game. Four times a second it rasterises what is around it
 * (things too big to eat, house walls) into a small grid scaled to its own size, finds by breadth-first
 * search the reachable cell where food is worth the most for the distance, and follows that path; with
 * nothing in sight it heads outwards, where bigger food lies. It vacuums when charged.
 */
object GluttonRobot {
    private const val N = 45
    /** Diagnostics: called once per second with the robot's state. */
    var trace: ((String) -> Unit)? = null

    /** Plays up to [seconds]; returns the time played. [rings] receives the time each ring was first reached. */
    fun play(g: GluttonGame, seconds: Float, report: StringBuilder? = null, rings: FloatArray? = null): Float {
        var t = 0f; var replanAt = 0f; var knocks = 0
        var waypoint: DoubleArray? = null
        var lastX = g.x; var lastY = g.y; var stuckFor = 0f; var escape = 0f; var escapeAngle = 0.0
        val reached = rings ?: FloatArray(g.world.rings.size) { -1f }
        reached.fill(-1f); reached[0] = 0f
        val banned = HashMap<Int, Float>()
        var lastEaten = 0; var hungryFor = 0f
        while (t < seconds && !g.finished) {
            // Four seconds without a bite: what is around is out of reach (wedged behind furniture), give up on it for a while.
            if (g.eaten != lastEaten) { lastEaten = g.eaten; hungryFor = 0f } else hungryFor += 1f / 60
            if (hungryFor > 4f) {
                g.near(g.x - g.radius * 8, g.y - g.radius * 8, g.x + g.radius * 8, g.y + g.radius * 8) { if (g.canEat(it)) banned[it.id] = t + 30f }
                hungryFor = 0f; replanAt = 0f
            }
            if (t >= replanAt) { banned.values.removeAll { it < t }; waypoint = plan(g, banned.keys); replanAt = t + .25f }
            if (escape > 0f) { escape -= 1f / 60; g.steer(cos(escapeAngle), sin(escapeAngle)) }
            else waypoint?.let { g.steer((it[0] - g.x) / g.radius * 10, (it[1] - g.y) / g.radius * 10) } ?: g.steer(0.0, 0.0)
            if (g.charge >= 1f && g.combo > 3) g.activateBurst()
            g.update(1f / 60); t += 1f / 60
            for (e in g.events) {
                if (e.effect == GluttonGame.Effect.KNOCK) knocks++
                if (e.effect == GluttonGame.Effect.RING && reached[e.value] < 0f) reached[e.value] = t
            }
            if ((t * 60).roundToInt() % 30 == 0) {
                val moved = hypot(g.x - lastX, g.y - lastY)
                stuckFor = if (moved < g.radius * .4) stuckFor + .5f else 0f
                if (stuckFor >= 1.5f) { escape = .7f; escapeAngle = Math.random() * 2 * PI; stuckFor = 0f }
                lastX = g.x; lastY = g.y
            }
            trace?.let { if ((t * 60).roundToInt() % 60 == 0) it("t=%.0f ring=%d r=%.3g pos=%.3g,%.3g eaten=%d".format(t, g.ring.index, g.radius, g.x, g.y, g.eaten)) }
        }
        report?.append("${g.mode} seed=${g.seed} t=${"%.0f".format(t)} size=${"%.3g".format(g.diameterMetres)}m ring=${g.ringReached} " +
            "eaten=${g.eaten} knocks=$knocks cleared=${g.cleared} rings=${reached.withIndex().filter { it.value >= 0f }.joinToString(" ") { "${it.index}@${it.value.roundToInt()}" }}\n")
        return t
    }

    /** The next point to head for, or null to stand still. */
    private fun plan(g: GluttonGame, banned: Set<Int>): DoubleArray? {
        val r = g.radius
        val cell = r * 1.1
        val ox = g.x - cell * N / 2; val oy = g.y - cell * N / 2
        val blocked = BooleanArray(N * N)
        val value = DoubleArray(N * N)
        fun index(x: Double, y: Double): Int {
            val c = floor((x - ox) / cell).toInt(); val w = floor((y - oy) / cell).toInt()
            return if (c in 0 until N && w in 0 until N) c + w * N else -1
        }
        g.near(ox, oy, ox + cell * N, oy + cell * N) { item ->
            if (item.prop.bonus) {
                if (g.mode == GluttonGame.Mode.FRINGALE) { val i = index(item.x, item.y); if (i >= 0) value[i] += g.area * .08 }
                return@near
            }
            if (g.canEat(item)) {
                if (item.id in banned) return@near
                val i = index(item.x, item.y)
                if (i >= 0) value[i] += g.growth(item) * if (item.moves) .5 else 1.0
            } else if (!item.cover) {
                val reach = r * .8 + item.radius * .78 + cell * .5
                val c0 = floor((item.x - reach - ox) / cell).toInt().coerceAtLeast(0); val c1 = floor((item.x + reach - ox) / cell).toInt().coerceAtMost(N - 1)
                val w0 = floor((item.y - reach - oy) / cell).toInt().coerceAtLeast(0); val w1 = floor((item.y + reach - oy) / cell).toInt().coerceAtMost(N - 1)
                for (w in w0..w1) for (c in c0..c1) {
                    val cx = ox + (c + .5) * cell; val cy = oy + (w + .5) * cell
                    if (hypot(cx - item.x, cy - item.y) < reach) blocked[c + w * N] = true
                }
            }
        }
        for (cover in g.covers) if (cover.active && g.canEat(cover)) { val i = index(cover.x, cover.y); if (i >= 0) value[i] += g.growth(cover) * 3 }
        val house = g.world.rings[0]
        if (!g.covered[0] && r < GluttonLayout.WALL_HEIGHT) for (wall in house.walls) {
            val pad = r * .85 + wall.thickness
            for (w in 0 until N) for (c in 0 until N) {
                val cx = ox + (c + .5) * cell; val cy = oy + (w + .5) * cell
                val along = (if (wall.horizontal) cx else cy).coerceIn(wall.from, wall.until)
                val px = if (wall.horizontal) along else wall.line; val py = if (wall.horizontal) wall.line else along
                if (hypot(cx - px, cy - py) < pad) blocked[c + w * N] = true
            }
        }
        val start = index(g.x, g.y)
        blocked[start] = false
        val parent = IntArray(N * N) { -1 }; val dist = IntArray(N * N) { -1 }
        val queue = IntArray(N * N); var head = 0; var tail = 0
        queue[tail++] = start; dist[start] = 0
        var best = -1; var bestScore = 0.0; var far = start; var farScore = -1.0
        while (head < tail) {
            val i = queue[head++]
            val score = value[i] / (1.0 + dist[i] * .4).pow(1.3)
            if (score > bestScore) { bestScore = score; best = i }
            val cx = ox + (i % N + .5) * cell; val cy = oy + (i / N + .5) * cell
            val outward = max(abs(cx), abs(cy))
            if (outward > farScore) { farScore = outward; far = i }
            val c = i % N; val w = i / N
            for ((dc, dw) in STEPS) {
                val nc = c + dc; val nw = w + dw
                if (nc !in 0 until N || nw !in 0 until N) continue
                val j = nc + nw * N
                if (blocked[j] || dist[j] >= 0) continue
                dist[j] = dist[i] + 1; parent[j] = i; queue[tail++] = j
            }
        }
        var target = if (best >= 0) best else far
        val wanted = goal(g, banned)
        // A rich corner seen on the map beats nibbling here when it is worth more for the way there.
        val wantedScore = wanted?.let { it.third / (1.0 + hypot(it.first - g.x, it.second - g.y) / cell * .4).pow(1.3) } ?: 0.0
        if (best < 0 || wantedScore > bestScore * 1.5) wanted?.let { (gx, gy) ->
            // Nothing worth eating in sight: the reachable cell closest to the nearest food (or the way out).
            var closest = Double.MAX_VALUE
            for (i in 0 until N * N) if (dist[i] >= 0) {
                val d = hypot(ox + (i % N + .5) * cell - gx, oy + (i / N + .5) * cell - gy)
                if (d < closest) { closest = d; target = i }
            }
        }
        if (target == start) return if (best == start) doubleArrayOf(ox + (start % N + .5) * cell, oy + (start / N + .5) * cell) else null
        // Walk back to a step three cells ahead on the path.
        var step = target
        val path = ArrayList<Int>()
        while (step != start && step >= 0) { path += step; step = parent[step] }
        val aim = path[max(0, path.size - 3)]
        return doubleArrayOf(ox + (aim % N + .5) * cell, oy + (aim / N + .5) * cell)
    }

    /**
     * What a player reading the map would head for: the best bite for the distance among everything of the rings
     * around (the map shows the food of your size), or the nearest way out of the house when it is empty.
     */
    private fun goal(g: GluttonGame, banned: Set<Int>): Triple<Double, Double, Double>? {
        var best: GluttonGame.Item? = null; var score = 0.0
        // Cleaning up the universe: the closest crumb left on the map, however small.
        if (g.finale) g.layers.last()?.let { last ->
            val left = (last.statics.asSequence() + last.movers.asSequence()).filter { it.active }
            val crumb = left.filter { it.id !in banned && g.canEat(it) }.minByOrNull { hypot(it.x - g.x, it.y - g.y) }
                ?: left.minByOrNull { hypot(it.x - g.x, it.y - g.y) }
            crumb?.let { return Triple(it.x, it.y, g.growth(it)) }
        }
        for (layer in g.layers) {
            if (layer == null || abs(layer.ring.index - g.ring.index) > 1) continue
            // Inside the house only the house counts: the garden is behind its walls.
            if (g.ring.index == 0 && layer.ring.index != 0) continue
            for (item in layer.statics) if (item.id !in banned && g.canEat(item) && (item.prop.bonus || item.radius > g.radius * .25)) {
                val gain = if (item.prop.bonus) (if (g.mode == GluttonGame.Mode.FRINGALE) g.area * .08 else 0.0) else g.growth(item)
                val s = gain / (hypot(item.x - g.x, item.y - g.y) + g.radius * 30)
                if (s > score) { score = s; best = item }
            }
        }
        best?.let { return Triple(it.x, it.y, if (it.prop.bonus) g.area * .08 else g.growth(it)) }
        if (g.ring.index == 0) return g.world.rings[0].doors.minByOrNull { hypot(it[0] - g.x, it[1] - g.y) }?.let { Triple(it[0], it[1], 0.0) }
        return Triple(g.x * 1.5, g.y * 1.5, 0.0)
    }

    private val STEPS = listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1, 1 to 1, 1 to -1, -1 to 1, -1 to -1)
}
