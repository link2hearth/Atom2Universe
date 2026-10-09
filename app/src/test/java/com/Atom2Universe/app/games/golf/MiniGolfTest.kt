package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import java.util.PriorityQueue
import kotlin.math.*

/**
 * Mini-golf: every hole is set out coherently and can be finished. A bot that plays like a careful
 * player (it only sees where the ball ends up, and waits for the moving parts) must hole out.
 */
class MiniGolfTest {
    private val courses = mapOf("classique" to MiniGolfCourse.holes, "déjanté" to MiniGolfCrazyCourse.holes)

    private fun every(check: (String, ClassicHole) -> Unit) {
        for ((name, holes) in courses) for (hole in holes) check("$name ${hole.number}", hole)
    }

    @Test fun eachCourseHasEighteenNumberedHoles() {
        for ((name, holes) in courses) {
            assertEquals(name, (1..18).toList(), holes.map { it.number })
            assertTrue(name, holes.all { it.mini != null && it.par in 2..5 })
        }
    }

    @Test fun teeAndCupAreInsideTheRailsWithRoomAroundThem() {
        every { name, hole ->
            val layout = hole.mini!!
            assertTrue("$name tee", layout.sdf(hole.tee.x, hole.tee.z) < -.35f)
            assertEquals("$name tee lie", GolfLie.GREEN, layout.lieAt(hole.tee.x, hole.tee.z))
            // The cup and its lip must clear the rails, or the ball could not reach it from every side.
            assertTrue("$name cup ${layout.sdf(hole.cup.x, hole.cup.z)}", layout.sdf(hole.cup.x, hole.cup.z) < -(hole.cupRadius + .1f))
            assertEquals("$name cup lie", GolfLie.GREEN, layout.lieAt(hole.cup.x, hole.cup.z))
            assertTrue("$name cup above ground", hole.cup.z > 2f)
            // No rail, post or arm may stand in the way of the tee.
            for (p in layout.posts) assertTrue("$name post on the tee", hypot(p.x, p.z) > p.r + .6f)
            for (r in layout.rotors) assertTrue("$name rotor on the tee", hypot(r.x, r.z) > r.length + .8f)
        }
    }

    @Test fun obstaclesStayInsideTheirLane() {
        every { name, hole ->
            val layout = hole.mini!!
            for (p in layout.posts) assertTrue("$name post", layout.sdf(p.x, p.z) < -p.r + .05f)
            for (r in layout.rotors) assertTrue("$name rotor hub", layout.sdf(r.x, r.z) < -.3f)
            for (w in layout.wells) assertTrue("$name well", layout.sdf(w.x, w.z) < 0f)
            for (p in layout.portals) {
                assertTrue("$name portal", layout.sdf(p.x, p.z) < -p.r)
                assertTrue("$name portal exit", layout.sdf(p.toX, p.toZ) < -p.r)
            }
        }
    }

    @Test fun theCupIsReachableFromTheTee() {
        every { name, hole ->
            val geodesic = Geodesic(hole)
            val d = geodesic.at(hole.tee.x, hole.tee.z)
            assertTrue("$name unreachable ($d)", d < 1e5f)
            println("MINI $name: shortest way ${"%.1f".format(d)} m, route ${"%.1f".format(hole.mini!!.pathLength)} m")
        }
    }

    @Test fun theBallNeverLeavesTheRails() {
        every { name, hole ->
            val layout = hole.mini!!
            for (k in 0 until 36) for (power in floatArrayOf(.35f, 1f)) {
                val game = ClassicGame(hole)
                game.aimAngle = k * 2f * PI.toFloat() / 36f
                assertTrue(game.hit(power))
                var frames = 0
                while ((game.state == GolfState.ROLLING || game.state == GolfState.FLYING) && frames++ < 5000) game.update(1f / 60f)
                val b = game.ball
                assertTrue("$name finite", b.x.isFinite() && b.y.isFinite() && b.z.isFinite())
                assertTrue("$name outside the rails at ${b.x}, ${b.z}", layout.sdf(b.x, b.z) <= .02f)
            }
        }
    }

    @Test fun aBotFinishesEveryHole() {
        val results = ArrayList<String>()
        every { name, hole ->
            val started = System.nanoTime()
            val strokes = solve(hole)
            results += "$name par ${hole.par} bot ${strokes ?: "-"} (${(System.nanoTime() - started) / 1_000_000} ms)"
            assertNotNull("$name cannot be finished", strokes)
        }
        println("MINI bot\n" + results.joinToString("\n"))
    }


    @Test fun aPuttRollsItsRangeOnFlatCarpetAndRailsBounceIt() {
        val hole = miniHole(99, 2, 0f, 29f, MiniTheme.MEADOW) { lane(2f, 0f to -.6f, 0f to 30f) }
        for (power in floatArrayOf(.25f, .5f, 1f)) {
            val game = ClassicGame(hole)
            game.aimAngle = 0f
            assertTrue(game.hit(power))
            finish(game)
            assertEquals(power * hole.puttRange, game.ball.z, power * hole.puttRange * .04f)
        }
        // Into the side rail at 45 degrees: the ball bounces back inside, slower, and keeps going forward.
        val game = ClassicGame(hole)
        game.aimAngle = PI.toFloat() / 4f
        assertTrue(game.hit(.5f))
        var fastest = 0f
        var frames = 0
        while (game.state == GolfState.ROLLING && frames++ < 6000) {
            game.update(1f / 60f)
            fastest = max(fastest, game.velocity.let { hypot(it.x, it.z) })
            assertTrue(abs(game.ball.x) <= 1.0f)
        }
        assertTrue(game.ball.z > 2f)
        assertTrue(fastest <= hole.puttRange.let { sqrt(2f * .55f * hole.rollScale * .5f * it) } + .05f)
    }

    @Test fun waterCostsAStrokeAndPutsTheBallBack() {
        val hole = miniHole(98, 2, 0f, 9f, MiniTheme.MEADOW) {
            lane(2f, 0f to -.6f, 0f to 10f)
            water(miniBox(0f, 4f, 1.2f, .6f))
        }
        val game = ClassicGame(hole)
        game.aimAngle = 0f
        game.hit(.5f)
        finish(game)
        assertEquals(GolfState.READY, game.state)
        assertEquals(2, game.strokes)
        assertEquals(0f, game.ball.z, .01f)
    }

    @Test fun aPipeCarriesTheBallToItsExitAndAMovingArmCarriesARestingBall() {
        val hole = miniHole(97, 2, 0f, 11f, MiniTheme.MEADOW) {
            lane(1.4f, 0f to -.6f, 0f to 4.4f)
            lane(1.4f, 0f to 8f, 0f to 12f)
            pipe(0f, 3.6f, 0f, 8.4f, heading = 0f)
        }
        val game = ClassicGame(hole)
        game.aimAngle = 0f
        game.hit(.5f)
        finish(game)
        assertTrue("came out of the pipe: ${game.ball.z}", game.ball.z > 8f)
        // A rotor sweeping over the ball that waits next to it pushes it aside, without ever throwing it.
        val swept = miniHole(96, 2, 0f, 9f, MiniTheme.MEADOW) {
            lane(3f, 0f to -.6f, 0f to 10f)
            rotor(.5f, 0f, 2, 1f, 1f)
        }
        val still = ClassicGame(swept)
        repeat(900) { still.update(1f / 60f) }
        assertEquals(GolfState.READY, still.state)
        assertTrue(swept.mini!!.sdf(still.ball.x, still.ball.z) < 0f)
    }

    // --- the bot -------------------------------------------------------------------------------

    private fun finish(game: ClassicGame) {
        var frames = 0
        while ((game.state == GolfState.ROLLING || game.state == GolfState.FLYING) && frames++ < 6000) game.update(1f / 60f)
    }

    private fun idle(game: ClassicGame, seconds: Float) {
        var left = seconds
        while (left > 1e-4f) { val dt = min(left, 1f / 30f); game.update(dt); left -= dt }
    }

    private class Trial(val angle: Float, val power: Float, val wait: Float, val score: Float)

    /** Strokes the bot needs, or null if it gives up. */
    private fun solve(hole: ClassicHole, limit: Int = 14): Int? {
        val layout = hole.mini!!
        val geodesic = Geodesic(hole)
        val main = ClassicGame(hole)
        val waits = if (layout.hasMovers) listOf(0f, .7f, 1.4f, 2.1f) else listOf(0f)
        val range = hole.puttRange
        repeat(limit) {
            val ball = main.ball
            // Where a player would look: the cup, the pipes, and the waypoints he can see from here.
            val targets = ArrayList<Pair<Float, Float>>()
            targets += hole.cup.x to hole.cup.z
            layout.portals.forEach { targets += it.x to it.z }
            layout.route.filter { layout.clear(ball.x, ball.z, it.x, it.z) }
                .sortedBy { hypot(it.x - ball.x, it.z - ball.z) }.take(8).forEach { targets += it.x to it.z }
            val angles = ArrayList<Float>()
            for (k in 0 until 36) angles += k * 2f * PI.toFloat() / 36f
            for ((tx, tz) in targets) {
                val a = atan2(tx - ball.x, tz - ball.z)
                for (d in floatArrayOf(-.02f, 0f, .02f)) angles += a + d
            }
            var best: Trial? = null
            fun consider(angle: Float, power: Float, wait: Float): Boolean {
                val trial = ClassicGame(hole)
                trial.restore(ball, main.strokes); trial.seekClock(main.clock)
                trial.aimAngle = angle
                idle(trial, wait)
                if (!trial.hit(power)) return false
                finish(trial)
                if (trial.state == GolfState.HOLED) { best = Trial(angle, power, wait, -1f); return true }
                val score = geodesic.at(trial.ball.x, trial.ball.z) + trial.lastPenalty * 3f + trial.strokes * 0f
                if (best == null || score < best!!.score - 1e-3f) best = Trial(angle, power, wait, score)
                return false
            }
            val powers = ArrayList<Float>()
            for (p in floatArrayOf(.06f, .12f, .2f, .33f, .5f, .75f, 1f)) powers += p
            for ((tx, tz) in targets) for (f in floatArrayOf(.95f, 1f, 1.08f)) {
                val p = hypot(tx - ball.x, tz - ball.z) * f / range
                if (p in .03f..1f) powers += p
            }
            val distinctPowers = powers.distinct()
            for (wait in waits) for (angle in angles) for (p in distinctPowers) if (consider(angle, p, wait)) return replay(main, best!!)
            val chosen = best ?: return null
            if (replay(main, chosen) != null) return main.strokes
        }
        return null
    }

    /** Plays the chosen trial on the real game. Returns the strokes if the ball was holed, else null. */
    private fun replay(main: ClassicGame, trial: Trial): Int? {
        main.aimAngle = trial.angle
        idle(main, trial.wait)
        main.hit(trial.power)
        finish(main)
        return if (main.state == GolfState.HOLED) main.strokes else null
    }

    /** Shortest walk to the cup in metres on a 10 cm grid, pipes included (one way). */
    private class Geodesic(hole: ClassicHole) {
        private val layout = hole.mini!!
        private val step = .1f
        private val b = layout.bounds
        private val x0 = b[0] - .5f
        private val z0 = b[1] - .5f
        private val nx = ceil((b[2] + .5f - x0) / step).toInt() + 1
        private val nz = ceil((b[3] + .5f - z0) / step).toInt() + 1
        private val dist = FloatArray(nx * nz) { Float.MAX_VALUE }
        private fun cx(i: Int) = x0 + i * step
        private fun cz(j: Int) = z0 + j * step
        private fun free(i: Int, j: Int) = layout.sdf(cx(i), cz(j)) < -.03f

        init {
            val queue = PriorityQueue<Pair<Float, Int>>(compareBy { it.first })
            val start = index(hole.cup.x, hole.cup.z)
            dist[start] = 0f
            queue += 0f to start
            val done = BooleanArray(layout.portals.size)
            while (queue.isNotEmpty()) {
                val (d, id) = queue.poll()
                if (d > dist[id]) continue
                val i = id % nx; val j = id / nx
                for (di in -1..1) for (dj in -1..1) {
                    if (di == 0 && dj == 0) continue
                    val ni = i + di; val nj = j + dj
                    if (ni !in 0 until nx || nj !in 0 until nz || !free(ni, nj)) continue
                    val nd = d + step * (if (di != 0 && dj != 0) 1.4142f else 1f)
                    if (nd < dist[nj * nx + ni]) { dist[nj * nx + ni] = nd; queue += nd to (nj * nx + ni) }
                }
                // Coming from a pipe's exit: every cell over its mouth is that far from the cup too.
                layout.portals.forEachIndexed { k, p ->
                    if (!done[k] && hypot(cx(i) - p.toX, cz(j) - p.toZ) < .25f) {
                        done[k] = true
                        val r = ceil(p.r / step).toInt()
                        val pi = ((p.x - x0) / step).roundToInt(); val pj = ((p.z - z0) / step).roundToInt()
                        for (a in -r..r) for (c in -r..r) {
                            val ni = pi + a; val nj = pj + c
                            if (ni !in 0 until nx || nj !in 0 until nz || !free(ni, nj)) continue
                            val nd = d + .3f
                            if (nd < dist[nj * nx + ni]) { dist[nj * nx + ni] = nd; queue += nd to (nj * nx + ni) }
                        }
                    }
                }
            }
        }

        private fun index(x: Float, z: Float) =
            ((z - z0) / step).roundToInt().coerceIn(0, nz - 1) * nx + ((x - x0) / step).roundToInt().coerceIn(0, nx - 1)

        fun at(x: Float, z: Float): Float {
            var best = Float.MAX_VALUE
            val i0 = ((x - x0) / step).roundToInt(); val j0 = ((z - z0) / step).roundToInt()
            for (a in -1..1) for (c in -1..1) {
                val i = i0 + a; val j = j0 + c
                if (i in 0 until nx && j in 0 until nz) best = min(best, dist[j * nx + i])
            }
            return if (best == Float.MAX_VALUE) 1e6f else best
        }
    }
}
