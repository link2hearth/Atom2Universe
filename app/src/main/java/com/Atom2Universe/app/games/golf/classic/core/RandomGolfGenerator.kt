package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*
import kotlin.random.Random

/** Scenery and relief of a generated hole. [amplitude] is the largest height step between profile nodes, in metres. */
enum class RandomGolfTheme(val landscape: GolfLandscapeStyle, val decor: GolfDecorTheme, val amplitude: Float) {
    GARDEN(GolfLandscapeStyle.PARKLAND, GolfDecorTheme.GARDEN, 7f),
    FAIRY(GolfLandscapeStyle.PARKLAND, GolfDecorTheme.FAIRY, 9f),
    HALLOWEEN(GolfLandscapeStyle.AUTUMN_HIGHLANDS, GolfDecorTheme.HALLOWEEN, 18f),
    CHRISTMAS(GolfLandscapeStyle.SNOW_MOUNTAINS, GolfDecorTheme.CHRISTMAS, 40f),
    ARCHIPELAGO(GolfLandscapeStyle.PARKLAND, GolfDecorTheme.GARDEN, 0f)
}

/**
 * A generated course is fully described by these three values: the seed gives every hole (its par,
 * length, relief, hazards), the count and the theme (null = a different theme on each hole) frame it.
 */
data class RandomGolfSpec(val seed: Long, val holeCount: Int, val theme: RandomGolfTheme?) {
    val id: String get() = "rnd-$seed-$holeCount-${theme?.name?.lowercase() ?: "mix"}"
    val progressName: String get() = "classic_golf_v2_" + id.replace('-', '_')

    companion object {
        val HOLE_COUNTS = listOf(3, 5, 9, 18)
        const val MAX_SEED = 999_999_999L

        fun parse(id: String?): RandomGolfSpec? {
            val parts = id?.split('-') ?: return null
            if (parts.size != 4 || parts[0] != "rnd") return null
            val seed = parts[1].toLongOrNull()?.takeIf { it in 0..MAX_SEED } ?: return null
            val count = parts[2].toIntOrNull()?.takeIf { it in HOLE_COUNTS } ?: return null
            val theme = if (parts[3] == "mix") null else
                RandomGolfTheme.entries.firstOrNull { it.name.equals(parts[3], ignoreCase = true) } ?: return null
            return RandomGolfSpec(seed, count, theme)
        }
    }
}

/**
 * Builds holes from a seed. Same spec, same holes. A draw that fails [problem] (a hazard on the tee,
 * an island out of reach...) is redrawn from the next sub-seed; the last attempts carry no hazards.
 */
object RandomGolfGenerator {
    private const val ATTEMPTS = 24
    private const val BARE_ATTEMPTS = 4

    fun holes(spec: RandomGolfSpec): List<ClassicHole> = (1..spec.holeCount).map { hole(spec, it) }

    /** Par of the whole course, from the seed alone: no hole is built. */
    fun parOf(spec: RandomGolfSpec): Int = (1..spec.holeCount).sumOf { parOf(spec, it) }

    fun parOf(spec: RandomGolfSpec, number: Int): Int {
        val roll = rng(spec, number, 0).nextInt(100)
        return if (roll < 22) 3 else if (roll < 76) 4 else 5
    }

    fun themeOf(spec: RandomGolfSpec, number: Int): RandomGolfTheme =
        spec.theme ?: RandomGolfTheme.entries[rng(spec, number, -1).nextInt(RandomGolfTheme.entries.size)]

    private fun rng(spec: RandomGolfSpec, number: Int, salt: Int) =
        Random(spec.seed * 1_000_003L + number * 7_919L + salt * 104_729L + 17L)

    /** Hole [number] (from 1) of the course: the same as in [holes], built on its own. */
    fun hole(spec: RandomGolfSpec, number: Int): ClassicHole {
        val par = parOf(spec, number)
        val theme = themeOf(spec, number)
        var last: ClassicHole? = null
        for (attempt in 1..ATTEMPTS) {
            val r = rng(spec, number, attempt)
            val built = if (theme == RandomGolfTheme.ARCHIPELAGO) archipelago(r, number, par)
                else land(r, number, par, theme, attempt <= ATTEMPTS - BARE_ATTEMPTS)
            last = built
            if (problem(built) == null) return built
        }
        return last!!
    }

    private fun f(r: Random, a: Float, b: Float) = a + r.nextFloat() * (b - a)
    private fun sign(r: Random) = if (r.nextBoolean()) 1f else -1f
    private fun n(x: Float, z: Float, w: Float) = GolfRouteNode(x, z, w)

    // ---------------------------------------------------------------- land holes

    private fun land(r: Random, number: Int, par: Int, theme: RandomGolfTheme, withHazards: Boolean): ClassicHole {
        var len = when (par) { 3 -> f(r, 105f, 195f); 4 -> f(r, 300f, 395f); else -> f(r, 470f, 570f) }
        // Par 4 and 5 bend: the landing area sits to one side, the green to the same side (dogleg) or the other (S).
        val bend = sign(r)
        val end = if (par == 3) sign(r) * f(r, 0f, 40f) else (if (r.nextBoolean()) bend else -bend) * f(r, 15f, 85f)
        val first = when (par) {
            3 -> 195f
            4 -> f(r, 165f, min(212f, len - 130f))
            else -> f(r, 170f, 205f)
        }.roundToInt().toFloat()
        val second = if (par == 5) first + f(r, 160f, 185f) else 0f
        if (par == 5) len = max(len, second + 125f)
        len = len.roundToInt().toFloat()
        val lx = if (par == 3) f(r, -30f, 30f) else bend * f(r, 20f, 58f)
        val side = sign(r)
        val route = buildList {
            add(n(0f, 0f, 12f))
            when (par) {
                3 -> {
                    add(n(end * .15f + f(r, -6f, 6f), 18f, 0f))
                    add(n(end * .5f + f(r, -10f, 10f), len * .58f, 0f))
                    add(n(end, len - 30f, f(r, 22f, 30f)))
                }
                4 -> {
                    add(n(side * f(r, 4f, 12f), 55f, f(r, 18f, 24f)))
                    add(n(lx, first, f(r, 46f, 54f)))
                    add(n((lx + end) / 2f + f(r, -10f, 10f), (first + len - 55f) / 2f, f(r, 26f, 34f)))
                    add(n(lx + (end - lx) * .7f, len - 55f, f(r, 24f, 30f)))
                }
                else -> {
                    val lx2 = -lx * f(r, .3f, 1f) + f(r, -18f, 18f)
                    add(n(side * f(r, 4f, 12f), 55f, f(r, 18f, 24f)))
                    add(n(lx, first, f(r, 46f, 54f)))
                    add(n((lx + lx2) / 2f, (first + second) / 2f, f(r, 34f, 42f)))
                    add(n(lx2, second, f(r, 48f, 56f)))
                    add(n(lx2 + (end - lx2) * .7f, len - 55f, f(r, 24f, 30f)))
                }
            }
            add(n(end, len, f(r, 21f, 25f)))
        }
        val profile = profile(r, len, theme, listOfNotNull(first.takeIf { par > 3 }, second.takeIf { par == 5 }))
        val radius = f(r, 15f, 19f)
        val form = GolfGreenForm.entries[r.nextInt(GolfGreenForm.entries.size)]
        val green = GolfGreenShape(
            aspect = f(r, .96f, 1.1f), rotation = f(r, -.6f, .6f), shape = f(r, .08f, .16f),
            phase = f(r, 0f, 6f), slopeX = f(r, -.012f, .012f), slopeZ = f(r, .006f, .016f),
            relief = if (form == GolfGreenForm.PLANE) GolfGreenRelief() else GolfGreenRelief(
                form, f(r, 0f, 6.28f), f(r, .07f, .24f), run = f(r, 12f, 16f)))
        val attacks = if (par == 3) emptyList() else buildList {
            add(GolfAttackLanding(lx, first, GolfClub.DRIVER))
            if (par == 5) add(GolfAttackLanding(route[3].x, second, GolfClub.WOOD3, lx, first))
        }
        val bare = ClassicHole(number, par, len, finishX = end, width = 300f, greenRadius = radius,
            elevation = profile.last().height, route = route, elevationProfile = profile,
            greenShape = green, fairwayRelief = GolfFairwayRelief(f(r, -.014f, .014f), f(r, -.24f, .24f)),
            landingZ = first, attackLandings = attacks, attackTreeClearance = 6f,
            landscapeStyle = theme.landscape, decorTheme = theme.decor)
        if (!withHazards) return bare
        return bare.copy(mounds = mounds(r, bare, theme), hazards = hazards(r, bare, theme, first, second))
    }

    /**
     * Heights along the hole. The engine flattens the ground around each wide landing area, so the profile
     * is level across them (and on the tee run and the approach); the slopes lie between those plateaus.
     */
    private fun profile(r: Random, len: Float, theme: RandomGolfTheme, plateaus: List<Float>): List<GolfElevationNode> {
        // z, and whether the node keeps the height of the one before it
        val anchors = ArrayList<Pair<Float, Boolean>>()
        anchors += 0f to false; anchors += len * .14f to true
        if (plateaus.isEmpty()) { anchors += len * .42f to false; anchors += len * .66f to false }
        for (c in plateaus) { anchors += (c - 50f) to false; anchors += (c + 50f) to true }
        anchors += (len - 60f) to false; anchors += len to true
        // Close nodes would force a steep step between them: keep the later one only if it is far enough.
        val spaced = ArrayList<Pair<Float, Boolean>>()
        for ((i, anchor) in anchors.sortedBy { it.first }.withIndex()) {
            if (i == anchors.lastIndex || spaced.isEmpty() || anchor.first - spaced.last().first >= 12f) spaced += anchor
        }
        anchors.clear(); anchors += spaced
        val maxSlope = if (theme == RandomGolfTheme.CHRISTMAS) .14f else .1f
        var heights = FloatArray(anchors.size)
        for (draw in 1..20) {
            heights = FloatArray(anchors.size)
            heights[0] = f(r, 0f, theme.amplitude)
            for (i in 1 until anchors.size) {
                val limit = maxSlope * (anchors[i].first - anchors[i - 1].first)
                heights[i] = heights[i - 1] +
                    if (anchors[i].second) 0f else f(r, -theme.amplitude, theme.amplitude).coerceIn(-limit, limit)
            }
            if (heights.max() - heights.min() >= 6f) break
        }
        val base = heights.min() - f(r, 0f, 3f)
        return anchors.indices.map { GolfElevationNode(anchors[it].first, heights[it] - base) }
    }

    private fun mounds(r: Random, hole: ClassicHole, theme: RandomGolfTheme): List<GolfMound> =
        List(r.nextInt(4)) {
            val z = f(r, .15f, .85f) * hole.length
            val x = hole.fairwayCenter(z) + sign(r) * (hole.fairwayWidth(z) / 2f + f(r, 38f, 75f))
            GolfMound(x.coerceIn(-135f, 135f), z, f(r, .5f, 1f) * max(5f, min(14f, theme.amplitude * .7f)),
                f(r, 30f, 40f), f(r, 40f, 65f))
        }

    private fun sand(x: Float, z: Float, rx: Float, rz: Float, angle: Float) =
        GolfHazard(x, z, rx, rz, GolfLie.BUNKER, angle, .18f, x * .07f)

    private fun hazards(r: Random, hole: ClassicHole, theme: RandomGolfTheme, first: Float, second: Float): List<GolfHazard> {
        val list = ArrayList<GolfHazard>()
        val len = hole.length
        // Fairway bunkers on the edge of the landing areas, never across the middle.
        for (z0 in listOf(first, second).filter { it > 0f }) {
            val z = z0 + f(r, -18f, 18f)
            list += sand(hole.fairwayCenter(z) + sign(r) * (hole.fairwayWidth(z) / 2f + f(r, -1f, 4f)), z,
                f(r, 6f, 9f), f(r, 11f, 16f), f(r, -.5f, .5f))
        }
        // Greenside bunkers, around the green except on its approach.
        repeat(1 + r.nextInt(2)) {
            val a = f(r, 0f, 6.28f)
            if (sin(a) > -.8f) {
                val d = hole.greenRadius + f(r, 8f, 12f)
                val x = hole.finishX + cos(a) * d; val z = len + sin(a) * d
                if (hole.greenSignedDistance(x, z) > 4f) list += sand(x, z, f(r, 6f, 8f), f(r, 9f, 12f), f(r, -.6f, .6f))
            }
        }
        // A lake beside the line, only where the ground is gentle enough for a flat water level.
        if (theme.amplitude <= 20f && r.nextFloat() < .35f) repeat(12) {
            if (list.none { it.lie == GolfLie.WATER }) {
                val z = f(r, .35f, .8f) * len
                val rx = f(r, 20f, 36f); val rz = f(r, 30f, 58f)
                val x = hole.fairwayCenter(z) + sign(r) * (hole.fairwayWidth(z) / 2f + rx + f(r, 18f, 38f))
                val lake = GolfWatercourse.meander(x.coerceIn(-130f, 130f), z, rx, rz, f(r, -.4f, .4f))
                val clear = (0..len.toInt() step 8).all { zz ->
                    lake.signedDistance(hole.fairwayCenter(zz.toFloat()), zz.toFloat()) > hole.fairwayWidth(zz.toFloat()) / 2f + 10f
                } && lake.signedDistance(hole.finishX, len) > hole.greenRadius + 22f &&
                    lake.signedDistance(0f, 0f) > 30f && list.none { lake.signedDistance(it.x, it.z) < 14f }
                if (clear) list += lake
            }
        }
        return list
    }

    // ---------------------------------------------------------------- archipelago

    private fun island(x: Int, z: Int, rx: Int, rz: Int, rotation: Float) =
        GolfHazard(x.toFloat(), z.toFloat(), rx * 1.18f, rz * 1.18f, GolfLie.FAIRWAY,
            rotation, .08f, x * .031f + z * .017f, GolfShoreline.at(x.toFloat(), z.toFloat()))

    private fun lanes(r: Random, z: Int, count: Int): List<GolfHazard> {
        val ranges = listOf(-80..-45, -15..15, 45..78).shuffled(r).take(count)
        return ranges.map { range ->
            val x = range.first + r.nextInt(range.last - range.first + 1)
            island(x, z + r.nextInt(-12, 13), r.nextInt(19, 34), r.nextInt(24, 36), 0f)
        }
    }

    private fun archipelago(r: Random, number: Int, par: Int): ClassicHole {
        val len = when (par) { 3 -> r.nextInt(120, 191); 4 -> r.nextInt(285, 376); else -> r.nextInt(490, 546) }
        val finish = r.nextInt(-22, 23)
        val radius = r.nextInt(12, 18)
        val greenIsland = island(finish, len, radius + 23, radius + 26, number * .2f)
        val teeIsland = island(0, 0, 17, 22, number * .37f)
        val first = when (par) {
            3 -> emptyList()
            4 -> lanes(r, (len * .54f).toInt().coerceIn(150, 205), 2 + r.nextInt(2))
            else -> lanes(r, r.nextInt(175, 201), 2 + r.nextInt(2))
        }
        val second = if (par == 5) lanes(r, r.nextInt(325, min(395, len - 110) + 1), 2 + r.nextInt(2)) else emptyList()
        val attacks = buildList {
            if (par == 3) {
                val distance = hypot(finish.toFloat(), len.toFloat())
                GolfClub.entries.filter { it != GolfClub.PUTTER && it.carry >= distance }.takeLast(2)
                    .forEach { add(GolfAttackLanding(finish.toFloat(), len.toFloat(), it)) }
            } else {
                for (target in first) for (club in listOf(GolfClub.WOOD3, GolfClub.DRIVER))
                    add(GolfAttackLanding(target.x, target.z, club))
                for (target in second) {
                    val from = first.minBy { hypot(target.x - it.x, target.z - it.z) }
                    for (club in listOf(GolfClub.WOOD3, GolfClub.DRIVER))
                        add(GolfAttackLanding(target.x, target.z, club, from.x, from.z))
                }
            }
        }
        return ClassicHole(number, par, len.toFloat(), finishX = finish.toFloat(), width = 360f,
            greenRadius = radius.toFloat(), elevation = 2.4f,
            route = listOf(n(0f, 0f, 0f), n(finish.toFloat(), len.toFloat(), 0f)),
            fairwayStart = 0f, landingZ = first.firstOrNull()?.z ?: len.toFloat(),
            islands = listOf(teeIsland) + first + second + greenIsland,
            greenShape = GolfGreenShape(aspect = 1f, rotation = number * .2f, shape = .06f,
                slopeX = if (number % 2 == 0) -.007f else .007f, slopeZ = .009f),
            attackLandings = attacks)
    }

    // ---------------------------------------------------------------- checks

    /** Null when the hole is playable; otherwise what is wrong with it. */
    fun problem(hole: ClassicHole): String? {
        val cup = hole.cup
        if (hole.lieAt(0f, 0f) != GolfLie.TEE) return "tee"
        if (hole.lieAt(cup.x, cup.z) != GolfLie.GREEN || hole.greenSignedDistance(cup.x, cup.z) > -9f) return "cup"
        val target = hole.openingTarget
        if (hole.lieAt(target.x, target.z) !in setOf(GolfLie.FAIRWAY, GolfLie.GREEN)) return "opening target"
        if (hole.islands.isEmpty()) {
            if (hole.route.any { abs(it.x) > hole.width * .45f }) return "route out of bounds"
            val hazards = hole.hazards
            for ((x, z) in listOf(0f to 0f, cup.x to cup.z, target.x to target.z))
                if (hazards.any { it.signedDistance(x, z) < 6f }) return "hazard on a key spot"
            if (hazards.any { it.lie == GolfLie.BUNKER && hole.greenSignedDistance(it.x, it.z) < 3f }) return "bunker on the green"
            if (hole.par > 3) {
                if (hypot(target.x, target.z) !in 150f..230f) return "landing out of range"
                if (hole.fairwayWidth(target.z) < 35f) return "landing too narrow"
            }
            val heights = (0..hole.length.toInt() step 4).map { hole.heightAt(hole.fairwayCenter(it.toFloat()), it.toFloat()) }
            if (heights.max() - heights.min() <= 4f) return "flat"
            if (heights.zipWithNext().any { (a, b) -> abs(b - a) / 4f > .24f }) return "too steep"
            val sx = hole.heightAt(cup.x + .5f, cup.z) - hole.heightAt(cup.x - .5f, cup.z)
            val sz = hole.heightAt(cup.x, cup.z + .5f) - hole.heightAt(cup.x, cup.z - .5f)
            if (hypot(sx, sz) !in .004f..0.03f) return "cup slope"
        } else {
            val stages = ArrayList<MutableList<GolfHazard>>()
            for (isle in hole.islands.drop(1).dropLast(1).sortedBy { it.z }) {
                if (stages.isEmpty() || isle.z - stages.last().first().z > 40f) stages += mutableListOf(isle)
                else stages.last() += isle
            }
            var from = listOf(hole.islands.first())
            for (stage in stages) {
                if (from.any { a -> stage.none { hypot(it.x - a.x, it.z - a.z) <= 210f } }) return "island out of reach"
                if (stage.any { b -> from.none { hypot(b.x - it.x, b.z - it.z) <= 218f } }) return "island unreachable"
                from = stage
            }
            if (from.any { hypot(cup.x - it.x, cup.z - it.z) > 200f }) return "green out of reach"
        }
        return null
    }
}
