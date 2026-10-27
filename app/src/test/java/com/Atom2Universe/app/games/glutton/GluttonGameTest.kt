package com.Atom2Universe.app.games.glutton

import com.Atom2Universe.app.games.glutton.GluttonCatalog.Motion
import com.Atom2Universe.app.games.glutton.GluttonCatalog.Scene
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class GluttonGameTest {
    private fun advance(g: GluttonGame, seconds: Float, fps: Int = 60) {
        repeat((seconds * fps).roundToInt()) { g.update(1f / fps) }
    }
    private fun prop(id: String, scene: Scene) = GluttonCatalog.forScene(scene).first { it.id == id }
    /** Feeds right-sized bites at the mouth until the radius reaches the target. */
    private fun growTo(g: GluttonGame, target: Float) {
        repeat(400) {
            if (g.radius >= target) return
            val candidates = GluttonCatalog.forScene(g.scene).filter { !it.moves && !it.goal && !it.treasure && it.radius <= g.radius * GluttonGame.EDIBLE }
            val prop = candidates.maxByOrNull { it.radius }
            assertNotNull("nothing edible at ${g.radius}", prop)
            g.items.removeAll { !it.moves }
            repeat(if (g.radius > target * .8f) 1 else 6) { g.items += GluttonGame.Item(9000 + it, prop!!, g.x, g.y, 0f) }
            g.update(1f / 60)
        }
        fail("Growth stalled at ${g.radius} (target $target)")
    }
    private fun swallowGoal(g: GluttonGame) {
        growTo(g, g.targetRadius); g.items.clear(); g.items += g.goal
        g.goal.x = g.x; g.goal.y = g.y; g.update(1f / 60)
    }
    /** Greedy player: steers to the best nearby bite, vacuums when charged, goes for the landmark once it fits,
     *  and gives up on a target it has been pushing against for half a second. */
    private fun play(g: GluttonGame, seconds: Float, report: StringBuilder? = null): Float {
        var t = 0f; val bins = IntArray(4); var knocks = 0
        var target: GluttonGame.Item? = null; var blockedFor = 0f; var retarget = 0f
        val avoided = HashMap<GluttonGame.Item, Float>()
        val path = ArrayList<Pair<Float, Float>>()
        while (t < seconds && !g.sceneCleared && !g.finished) {
            if (path.isEmpty() || (t * 4).toInt() > path.size) path += g.x to g.y
            if (target == null || !g.canEat(target) || target.cooldown > 0f || retarget <= 0f) {
                retarget = 1f
                val candidates = g.items.filter { g.canEat(it) && !it.moves && it.radius >= g.radius * .12f && (avoided[it] ?: 0f) < t }
                val clear = candidates.filter { lineIsClear(g, it) }
                target = if (g.canEat(g.goal)) g.goal else (clear.ifEmpty { candidates })
                    .minByOrNull { hypot(it.x - g.x, it.y - g.y) / (it.radius / g.radius + .05f) }
            }
            retarget -= 1f / 60
            if (target != null) {
                val dx = target.x - g.x; val dy = target.y - g.y; val d = hypot(dx, dy).coerceAtLeast(1f)
                var sx = dx / d; var sy = dy / d
                // Things too big to eat that sit ahead push the steering gently aside.
                for (o in g.items) if (o.active && !g.canEat(o)) {
                    val ox = g.x - o.x; val oy = g.y - o.y; val od = hypot(ox, oy).coerceAtLeast(1f)
                    val gap = od - o.radius - g.radius
                    if (gap < 60f && ox * sx + oy * sy < 0f) { val w = (1f - gap / 60f).coerceIn(0f, 1f) * .8f; sx += ox / od * w; sy += oy / od * w }
                }
                blockedFor = if (hypot(g.vx, g.vy) < g.moveSpeed * .25f) blockedFor + 1f / 60 else 0f
                if (blockedFor > 1.5f && target !== g.goal) { avoided[target] = t + 6f; target = null; blockedFor = 0f }
                g.steer(sx, sy)
            } else g.steer(0f, 0f)
            if (g.charge >= 1f && g.combo > 3) g.activateBurst()
            g.update(1f / 60); t += 1f / 60
            for (e in g.events) {
                if (e.effect == GluttonGame.Effect.EAT) { val ratio = e.item!!.radius / g.radius; bins[if (ratio < .2f) 0 else if (ratio < .35f) 1 else if (ratio < .5f) 2 else 3]++ }
                if (e.effect == GluttonGame.Effect.KNOCK) knocks++
            }
        }
        if (report != null) snapshot(g, path, "bot_${g.sceneIndex}_${g.seed}")
        report?.append("scene=${g.sceneIndex} t=${"%.0f".format(t)} cleared=${g.sceneCleared} size=${"%.2f".format(g.diameterMetres)}m eaten=${g.eaten} bins(<.2,<.35,<.5,>=.5)=${bins.toList()} knocks=$knocks score=${g.score}\n")
        return t
    }
    /** True when no inedible item sits on the straight line from the gobbler to the target. */
    private fun lineIsClear(g: GluttonGame, target: GluttonGame.Item): Boolean {
        val dx = target.x - g.x; val dy = target.y - g.y; val len2 = (dx * dx + dy * dy).coerceAtLeast(1f)
        for (o in g.items) {
            if (!o.active || g.canEat(o) || o === target) continue
            val t = ((o.x - g.x) * dx + (o.y - g.y) * dy) / len2
            if (t <= 0f || t >= 1f) continue
            val px = g.x + dx * t; val py = g.y + dy * t
            if (hypot(o.x - px, o.y - py) < o.radius * .8f + g.radius * .8f) return false
        }
        return true
    }
    /** Debug picture of a run: items as circles (green = edible now), the robot's path in red. */
    private fun snapshot(g: GluttonGame, path: List<Pair<Float, Float>>, name: String) {
        val scale = 700f / g.scene.width
        val w = (g.scene.width * scale).toInt(); val h = (g.scene.height * scale).toInt()
        val px = IntArray(w * h) { 0xEEDACE }
        fun disc(cx: Float, cy: Float, radius: Float, color: Int, filled: Boolean) {
            val r = (radius * scale).coerceAtLeast(1f)
            val x0 = (cx * scale - r).toInt(); val x1 = (cx * scale + r).toInt(); val y0 = (cy * scale - r).toInt(); val y1 = (cy * scale + r).toInt()
            for (y in y0..y1) for (x in x0..x1) {
                if (x < 0 || y < 0 || x >= w || y >= h) continue
                val d = hypot(x - cx * scale, y - cy * scale)
                if (d <= r && (filled || d >= r - 1.5f)) px[y * w + x] = color
            }
        }
        for (item in g.items) if (item.active) disc(item.x, item.y, item.radius,
            if (g.canEat(item)) 0x50B478 else if (item.moves) 0xDC5A5A else 0x786E8C, true)
        for (i in 1 until path.size) {
            val steps = 24
            for (s in 0..steps) {
                val t = s / steps.toFloat()
                val x = ((path[i - 1].first + (path[i].first - path[i - 1].first) * t) * scale).toInt()
                val y = ((path[i - 1].second + (path[i].second - path[i - 1].second) * t) * scale).toInt()
                if (x in 0 until w && y in 0 until h) px[y * w + x] = 0xC82828
            }
        }
        disc(g.x, g.y, g.radius, 0x1E3C5A, false)
        val raw = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(raw).use { z ->
            for (y in 0 until h) { z.write(0); for (x in 0 until w) { val c = px[y * w + x]; z.write(c shr 16 and 255); z.write(c shr 8 and 255); z.write(c and 255) } }
        }
        val out = java.io.ByteArrayOutputStream(); val d = java.io.DataOutputStream(out)
        d.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            d.writeInt(data.size); val t = type.toByteArray(); d.write(t); d.write(data)
            val crc = java.util.zip.CRC32(); crc.update(t); crc.update(data); d.writeInt(crc.value.toInt())
        }
        val hdr = java.io.ByteArrayOutputStream()
        java.io.DataOutputStream(hdr).apply { writeInt(w); writeInt(h); write(byteArrayOf(8, 2, 0, 0, 0)) }
        chunk("IHDR", hdr.toByteArray()); chunk("IDAT", raw.toByteArray()); chunk("IEND", ByteArray(0))
        val dir = java.io.File("build/outputs/glutton").apply { mkdirs() }
        java.io.File(dir, "$name.png").writeBytes(out.toByteArray())
    }
    @Test fun catalogueIsConsistent() {
        val ids = HashSet<String>()
        for (prop in GluttonCatalog.props) {
            assertTrue(prop.id, prop.metres > 0f && prop.count > 0)
            assertTrue(prop.id, prop.where == GluttonCatalog.ANY || prop.where in 0..8)
            if (prop.moves) assertTrue(prop.id, prop.speed > 0f)
            ids += prop.id
        }
        for (sheet in GluttonCatalog.Sheet.entries) {
            val cells = GluttonCatalog.props.filter { it.sheet == sheet }.map { it.cell }
            assertEquals(sheet.name, (0 until cells.size).toList(), cells)
            assertTrue(sheet.name, cells.size <= 24)
        }
        for (scene in Scene.entries) {
            assertEquals(scene.name, 1, GluttonCatalog.forScene(scene).count { it.goal })
            assertEquals(scene.name, 3, GluttonCatalog.forScene(scene).count { it.treasure })
            val start = scene.startMetres / 2f * scene.scale
            val goal = GluttonCatalog.goal(scene)
            // Each scene is roughly a twelvefold growth with a ladder of sizes between start and landmark.
            assertTrue(scene.name, goal.radius / GluttonGame.EDIBLE / start in 9f..14f)
            var reach = start * GluttonGame.EDIBLE
            val sizes = GluttonCatalog.forScene(scene).filter { !it.moves && !it.goal }.map { it.radius }.sorted()
            for (size in sizes) if (size <= reach * 1.001f) reach = max(reach, size / .55f * GluttonGame.EDIBLE)
            assertTrue("${scene.name}: ladder stops at radius $reach before the landmark ${goal.radius}", reach >= goal.radius)
        }
    }
    @Test fun generatedScenesAreReproducibleBoundedAndPlentiful() {
        for (scene in 0..2) repeat(20) { seed ->
            val a = GluttonGame(seed.toLong(), scene); val b = GluttonGame(seed.toLong(), scene)
            assertEquals(a.items.map { listOf(it.prop.id, it.x, it.y) }, b.items.map { listOf(it.prop.id, it.x, it.y) })
            assertEquals(1, a.items.count { it.prop.goal }); assertEquals(3, a.items.count { it.prop.treasure })
            assertTrue(a.items.size <= GluttonGame.MAX_ITEMS)
            assertTrue("scene=$scene seed=$seed items=${a.items.size}", a.items.size >= 140)
            assertTrue(a.items.count { it.moves } >= 6)
            for (item in a.items) {
                assertTrue(item.x in 0f..a.scene.width); assertTrue(item.y in 0f..a.scene.height)
            }
            assertTrue(a.items.count { a.canEat(it) } >= 12)
        }
    }
    @Test fun firstBitesAreImmediateAndSizeIsContinuous() {
        val g = GluttonGame(4)
        val start = g.radius
        for (frame in 0 until 5 * 60) {
            val food = g.nearestFood() ?: break
            g.steer(food.x - g.x, food.y - g.y); g.update(1f / 60)
            if (g.eaten >= 5) break
        }
        assertTrue("eaten=${g.eaten}", g.eaten >= 5)
        assertTrue(g.radius > start && g.radius < start * 1.5f)
        assertTrue(g.stuck.size == g.eaten - g.treasures)
    }
    @Test fun onlySmallerThingsAreEdibleAndBiggerOnesBlock() {
        val g = GluttonGame(2); g.items.clear()
        val chair = GluttonGame.Item(1, prop("chair", Scene.ROOM), g.x + 120f, g.y, 0f); g.items += chair
        assertFalse(g.canEat(chair))
        g.steer(1f, 0f); advance(g, 2f)
        assertTrue(chair.active); assertEquals(0, g.eaten)
        assertTrue("overlapping the chair", hypot(g.x - chair.x, g.y - chair.y) >= g.radius * .8f + chair.radius * .78f - 1f)
        growTo(g, chair.radius / GluttonGame.EDIBLE + 1f)
        g.items.clear(); g.items += chair; chair.x = g.x; chair.y = g.y
        g.update(1f / 60); assertFalse(chair.active)
    }
    @Test fun rightSizedBitesGrowFarMoreThanCrumbs() {
        val g = GluttonGame(3)
        val crumb = GluttonGame.Item(1, prop("crumb", Scene.ROOM), g.x, g.y, 0f)
        val cookie = GluttonGame.Item(2, prop("cookie", Scene.ROOM), g.x, g.y, 0f)
        assertTrue(g.growth(crumb) > 0f)
        assertTrue(g.growth(cookie) > g.growth(crumb) * 3f)
        assertEquals(GluttonGame.CAP * g.area, g.growth(cookie), .01f)
        assertTrue(g.isBigBite(cookie)); assertFalse(g.isBigBite(crumb))
        assertTrue(g.points(cookie) > g.points(crumb))
    }
    @Test fun aBumpKnocksStuckThingsLooseAndTheyCanBeEatenAgain() {
        val g = GluttonGame(5); g.items.clear()
        val prop = prop("cookie", Scene.ROOM)
        repeat(6) { g.items += GluttonGame.Item(500 + it, prop, g.x, g.y, 0f) }
        g.update(1f / 60)
        assertEquals(6, g.stuck.size); val grown = g.radius; val eaten = g.eaten
        advance(g, 1.2f)
        val cat = GluttonGame.Item(900, prop("cat", Scene.ROOM), g.x + g.radius + 20f, g.y, 0f); g.items += cat
        var knocked = false
        for (frame in 0 until 90) {
            g.update(1f / 60)
            if (g.events.any { it.effect == GluttonGame.Effect.KNOCK }) { knocked = true; break }
        }
        assertTrue(knocked)
        assertEquals(3, g.stuck.size)
        assertTrue(g.radius < grown)
        val loose = g.items.filter { it.active && it.prop == prop }
        assertEquals(3, loose.size)
        assertTrue(loose.all { it.cooldown > 0f && !g.canEat(it) })
        g.items.remove(cat)
        advance(g, 2f)
        for (item in loose) if (item.active) { item.x = g.x; item.y = g.y }
        g.update(1f / 60)
        assertEquals(eaten + 3, g.eaten); assertEquals(6, g.stuck.size)
    }
    @Test fun chasersHuntWhileYouAreSmallAndFleeOnceYouOutgrowThem() {
        val g = GluttonGame(6); g.items.removeAll { it.moves }
        val vacuum = GluttonGame.Item(900, prop("robot_vacuum", Scene.ROOM), g.x + 190f, g.y, 0f); g.items += vacuum
        val before = hypot(vacuum.x - g.x, vacuum.y - g.y)
        advance(g, 1f)
        assertTrue(vacuum.alert); assertTrue(hypot(vacuum.x - g.x, vacuum.y - g.y) < before)
        growTo(g, vacuum.radius / GluttonGame.EDIBLE + 5f)
        g.items.removeAll { !it.moves }
        // Bumps during the growth leave it keeping its distance; what matters here is the reaction to a giant.
        vacuum.rest = 0f; vacuum.x = g.x + g.radius * 2f; vacuum.y = g.y; vacuum.vx = 0f; vacuum.vy = 0f
        g.steer(0f, 0f); g.update(1f / 60); g.update(1f / 60)
        assertTrue(vacuum.alert); assertTrue(vacuum.vx > 0f)
    }
    @Test fun patrolsDriveTheirLaneAndBounceAtWalls() {
        val g = GluttonGame(7)
        val car = g.items.first { it.prop.motion == Motion.PATROL_H }
        val lane = car.y
        g.items.removeAll { it.moves && it !== car }
        advance(g, 25f)
        assertEquals(lane, car.y, .01f)
        assertTrue(car.x in GluttonGame.WALL..g.scene.width - GluttonGame.WALL)
    }
    @Test fun burstIsASingleShockwaveThatStunsNearbyCreatures() {
        val g = GluttonGame(8); g.items.removeAll { it.moves }
        val cat = GluttonGame.Item(900, prop("cat", Scene.ROOM), g.x + g.suctionRadius, g.y, 0f); g.items += cat
        assertTrue(g.activateBurst()); assertFalse(g.activateBurst())
        g.update(1f / 60)
        assertEquals(1, g.events.count { it.effect == GluttonGame.Effect.BURST })
        assertTrue(cat.stunned > 0f)
        val trace = StringBuilder()
        repeat(8) { advance(g, .25f); trace.append(" t=${g.elapsed} stun=${cat.stunned} d=${hypot(cat.x - g.x, cat.y - g.y)} active=${cat.active} items=${g.items.size}") }
        assertEquals(trace.toString(), 0f, cat.stunned, .001f)
    }
    @Test fun treasuresAddTimeAndSpeedIsAnIndependentStar() {
        val g = GluttonGame(9); g.items.removeAll { it.moves }
        growTo(g, 60f)
        val treasure = GluttonGame.Item(700, prop("golden_spoon", Scene.ROOM), 0f, 0f, 0f); g.items += treasure
        val time = g.remaining; treasure.x = g.x; treasure.y = g.y; g.update(1f / 60)
        assertEquals(1, g.treasures); assertTrue(g.remaining > time + 2f); assertEquals(1f, g.charge, .001f)
        assertEquals(0, g.stuck.count { it.item.prop.treasure })
        swallowGoal(g)
        assertEquals(2, g.medals[0])
    }
    @Test fun scenesChainWithSizeCarriedOverAndEndInVictory() {
        val g = GluttonGame(10)
        for (scene in 0..2) {
            swallowGoal(g)
            assertTrue(g.sceneCleared); assertTrue(g.medals[scene] > 0)
            val metres = g.diameterMetres; val score = g.score
            if (scene < 2) {
                assertFalse(g.finished)
                val time = g.remaining; advance(g, 2f); assertEquals(time, g.remaining, .001f)
                assertTrue(g.advanceScene()); assertFalse(g.advanceScene())
                assertEquals(scene + 1, g.sceneIndex); assertFalse(g.sceneCleared)
                assertEquals(metres, g.diameterMetres, .01f); assertEquals(score, g.score)
                assertTrue("radius ${g.radius}", g.radius in 10f..34f)
                assertEquals(0, g.stuck.size)
            } else assertTrue(g.finished && g.won)
        }
    }
    @Test fun aStreakOfOneFamilyPaysABonus() {
        val g = GluttonGame(11); g.items.clear()
        val prop = prop("cookie", Scene.ROOM)
        repeat(GluttonGame.STREAK) { g.items += GluttonGame.Item(500 + it, prop, g.x, g.y, 0f) }
        g.update(1f / 60)
        assertEquals(1, g.events.count { it.effect == GluttonGame.Effect.STREAK })
        assertEquals(prop.family, g.streakFamily)
    }
    @Test fun movementAndCollectionsAreIndependentOfRefreshRate() {
        val results = intArrayOf(60, 120).map { fps ->
            val g = GluttonGame(12); g.items.removeAll { it.moves }
            g.steer(1f, 0f); advance(g, 3f, fps); g.x to g.eaten
        }
        assertEquals(results[0].first, results[1].first, .5f); assertEquals(results[0].second, results[1].second)
    }
    @Test fun releaseInvalidInputTimeoutAndMemoryAreSafe() {
        val g = GluttonGame(13)
        g.steer(Float.NaN, 1f); g.update(1f / 60); assertTrue(g.x.isFinite())
        g.update(Float.NaN); g.update(-1f); g.update(Float.POSITIVE_INFINITY)
        advance(g, g.scene.budget + 40f)
        assertTrue(g.finished); assertFalse(g.won)
        assertTrue(g.items.size <= GluttonGame.MAX_ITEMS)
    }
    @Test fun aGreedyPlayerFinishesEveryScenePlayingItForReal() {
        val report = StringBuilder()
        val failures = ArrayList<String>()
        for (scene in 0..2) {
            val times = (0 until 4).map { seed ->
                val g = GluttonGame(100L + seed, scene)
                val t = play(g, g.scene.budget + 5f, report)
                if (!g.sceneCleared) failures += "scene=$scene seed=$seed not cleared"
                t
            }
            val average = times.average()
            if (average > Scene.entries[scene].budget * .85f) failures += "scene=$scene average ${average}s leaves no margin"
            if (average < 35f) failures += "scene=$scene average ${average}s is too easy"
        }
        println(report)
        assertTrue(report.toString() + failures.joinToString("\n"), failures.isEmpty())
    }
}
