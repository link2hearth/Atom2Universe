package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.*
import kotlin.random.Random

data class GolfPoint(val x: Float, val y: Float, val z: Float)
data class GolfTree(val x: Float, val z: Float, val radius: Float, val kind: Int)
enum class GolfLie { TEE, FAIRWAY, ROUGH, BUNKER, GREEN, WATER, OUT, SEMI_ROUGH, FRINGE }

/**
 * Fixed equipment. Carry is the full-swing airborne range in metres on flat ground without wind
 * (for the putter: the roll on a flat green). Launch angle and backspin follow launch-monitor
 * averages for a good amateur; the loft is the printed club loft. The launch angle is the full
 * swing's: shorter swings launch higher (see [GolfCalibration]). With a centred contact the ball
 * never comes back; spinning back on a green takes a wedge struck low.
 */
enum class GolfClub(val carry: Float, val loft: Float, val launch: Float, val spinRpm: Float) {
    DRIVER(235f, 10.5f, 12f, 2700f), WOOD3(213f, 15f, 11.5f, 3500f), WOOD5(195f, 18f, 12.5f, 4300f),
    HYBRID4(180f, 22f, 13.5f, 4500f), IRON5(164f, 25f, 13.5f, 5300f), IRON6(151f, 28f, 15f, 6100f),
    IRON7(138f, 32f, 17f, 7000f), IRON8(125f, 36f, 19f, 7300f), IRON9(112f, 40f, 21.5f, 7600f),
    PW(97f, 45f, 25f, 7800f), GW(82f, 50f, 28f, 7900f), SW(66f, 56f, 32f, 8000f), LW(49f, 60f, 36f, 8100f),
    PUTTER(25f, 3f, 0f, 0f)
}

/** Width is the full mowing width. Zero-width nodes represent an intentional rough carry. */
data class GolfRouteNode(val x: Float, val z: Float, val width: Float)
data class GolfElevationNode(val z: Float, val height: Float)
enum class GolfLandscapeStyle { PARKLAND, AUTUMN_HIGHLANDS, SNOW_MOUNTAINS }
/** Scenery only: course physics and turf palettes are independent of seasonal decorations. */
enum class GolfDecorTheme { GARDEN, FAIRY, HALLOWEEN, CHRISTMAS }

/** A designed carry and reception, used to validate risk/reward routes against real equipment. */
data class GolfAttackLanding(val x: Float, val z: Float, val club: GolfClub,
    val fromX: Float = 0f, val fromZ: Float = 0f)
data class GolfMound(val x: Float, val z: Float, val height: Float, val rx: Float, val rz: Float)
enum class GolfGreenForm { PLANE, TIER, RIDGE, SWALE, FALSE_FRONT, CROWN, PUNCHBOWL }

/** Metres, with direction the uphill normal of the feature line, in radians from world +x. */
data class GolfGreenRelief(
    val form: GolfGreenForm = GolfGreenForm.PLANE,
    val direction: Float = 0f,
    val height: Float = 0f,
    val start: Float = 3.5f,
    val run: Float = 12f
) {
    private val nx = cos(direction)
    private val nz = sin(direction)

    fun heightAt(dx: Float, dz: Float): Float {
        val u = dx * nx + dz * nz
        return when (form) {
            GolfGreenForm.PLANE -> 0f
            GolfGreenForm.TIER -> height * smooth((u - start) / run)
            GolfGreenForm.FALSE_FRONT -> -height * smooth((-u - start) / run)
            GolfGreenForm.RIDGE -> -height * smooth((abs(u) - start) / run)
            GolfGreenForm.SWALE -> height * smooth((abs(u) - start) / run)
            // Shallow radial forms retain the main drainage slope: no closed sump or local peak.
            GolfGreenForm.CROWN -> -height * smooth((hypot(dx, dz) - start) / run)
            GolfGreenForm.PUNCHBOWL -> height * smooth((hypot(dx, dz) - start) / run)
        }
    }
}

/** Crossfall is dz-independent on a straight fairway; a negative crown is a broad valley. */
data class GolfFairwayRelief(val crossfall: Float = 0f, val crown: Float = 0f)

data class GolfGreenShape(
    val aspect: Float = 1f, val rotation: Float = 0f, val shape: Float = .08f,
    val phase: Float = 0f, val offsetX: Float = 0f, val offsetZ: Float = 0f,
    val slopeX: Float = .006f, val slopeZ: Float = .004f,
    val relief: GolfGreenRelief = GolfGreenRelief()
)

/** Rotated, scalloped contours shared by rendering and collision, in world metres. */
data class GolfHazard(
    val x: Float, val z: Float, val rx: Float, val rz: Float, val lie: GolfLie,
    val rotation: Float = 0f, val shape: Float = .15f, val phase: Float = 0f
) {
    private val cosine = cos(rotation)
    private val sine = sin(rotation)
    fun signedDistance(px: Float, pz: Float): Float = organicDistance(
        px - x, pz - z, rx, rz, cosine, sine, shape, phase
    )
    fun contains(px: Float, pz: Float): Boolean = signedDistance(px, pz) <= 0f
}

private fun organicDistance(dx: Float, dz: Float, rx: Float, rz: Float,
    cosine: Float, sine: Float, shape: Float, phase: Float): Float {
    val u = (dx * cosine + dz * sine) / rx
    val v = (-dx * sine + dz * cosine) / rz
    val angle = atan2(v, u)
    val edge = 1f + shape * (.66f * cos(3f * angle + phase) + .34f * sin(2f * angle - phase))
    return (hypot(u, v) - edge) * min(rx, rz)
}

private fun smooth(t: Float): Float = t.coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }

/** Original, individually routed holes. Coordinates and elevations are metres. */
data class ClassicHole(
    val number: Int,
    val par: Int,
    val length: Float,
    val bend: Float = 0f,
    val finishX: Float = 0f,
    val width: Float = 240f,
    val greenRadius: Float = 16f,
    val elevation: Float = 0f,
    val hazards: List<GolfHazard> = emptyList(),
    val fairwayBaseWidth: Float = 34f,
    val route: List<GolfRouteNode> = emptyList(),
    val elevationProfile: List<GolfElevationNode> = emptyList(),
    val mounds: List<GolfMound> = emptyList(),
    val greenShape: GolfGreenShape = GolfGreenShape(),
    val fairwayRelief: GolfFairwayRelief = GolfFairwayRelief(),
    val landingZ: Float = 195f,
    val fairwayStart: Float = if (route.isEmpty()) -5f else if (par == 3) length - 29f else 40f + (number % 4) * 7f,
    val cupRadius: Float = CUP_RADIUS,
    val retainingBankHeight: Float = 0f,
    val cupBowlDepth: Float = 0f,
    /** Additional mown corridors; the primary route remains the caddie's prudent line. */
    val alternateRoutes: List<List<GolfRouteNode>> = emptyList(),
    val plantedTrees: List<GolfTree> = emptyList(),
    val landscapeStyle: GolfLandscapeStyle = GolfLandscapeStyle.PARKLAND,
    val attackLandings: List<GolfAttackLanding> = emptyList(),
    /** Keep randomly planted canopies outside designed carries; landing hazards still apply. */
    val attackTreeClearance: Float = 0f,
    /** An archipelago replaces continuous land: everything outside these contours is water. */
    val islands: List<GolfHazard> = emptyList(),
    val islandWaterLevel: Float = 0f,
    val decorTheme: GolfDecorTheme = GolfDecorTheme.GARDEN,
    /** A mini-golf hole: lanes, rails and obstacles replace the routed fairway, and only the putter is played. */
    val mini: MiniLayout? = null,
    /** Metres a full putt rolls on flat turf (the putter's carry, unless the hole shortens it). */
    val puttRange: Float = GolfClub.PUTTER.carry,
    /** The turf's rolling resistance relative to a green: carpet holds the ball back more. */
    val rollScale: Float = 1f
) {
    private val greenCos = cos(greenShape.rotation)
    private val greenSin = sin(greenShape.rotation)
    // Existing wide routing bulbs are deliberate landing shelves (including par-5 lay-ups).
    // Resolve after route sample caches exist: branched mound grading also queries fairway distance.
    // heightAt never rebuilds lists or allocates forms.
    private val landingShelves by lazy { route.filterIndexed { i, node ->
        i > 0 && i < route.lastIndex && node.width >= 45f &&
            node.width >= route[i - 1].width && node.width >= route[i + 1].width
    }.map { GolfPoint(it.x, terrainBase(it.x, it.z), it.z) } }

    // A swept, variable-radius centreline gives round corners and entrance bulbs. In
    // particular a dogleg's outside edge cannot form the spikes of a horizontal strip.
    private val fairwaySamples: List<GolfRouteNode> by lazy {
        if (route.isEmpty()) emptyList() else buildList {
            var z = fairwayStart
            while (z < length) {
                add(GolfRouteNode(fairwayCenter(z), z, fairwayWidth(z)))
                z += 4f
            }
            add(GolfRouteNode(finishX, length, fairwayWidth(length)))
        }
    }

    private val alternateSamples: List<List<GolfRouteNode>> by lazy {
        alternateRoutes.map { nodes ->
            require(nodes.size >= 2 && nodes.zipWithNext().all { (a, b) -> b.z > a.z })
            buildList {
                var z = max(fairwayStart, nodes.first().z)
                while (z < nodes.last().z) {
                    val i = nodes.indexOfFirst { it.z > z }.coerceAtLeast(1)
                    val a = nodes[i - 1]; val b = nodes[i]
                    val t = smooth((z - a.z) / (b.z - a.z))
                    add(GolfRouteNode(a.x + (b.x - a.x) * t, z, a.width + (b.width - a.width) * t))
                    z += 4f
                }
                add(nodes.last())
            }
        }
    }

    private val primaryFairwayIndex by lazy { GolfFairwayIndex(fairwaySamples, 15f) }
    private val alternateFairwayIndexes by lazy { alternateSamples.map { GolfFairwayIndex(it, SEMI_ROUGH_WIDTH) } }
    private val teeTerrainHeight by lazy { terrainHeight(0f, 0f) }
    private val waterLevels by lazy {
        hazards.filter { it.lie == GolfLie.WATER }.associateWith { terrainHeight(it.x, it.z) - 1.2f }
    }

    val tee: GolfPoint = GolfPoint(0f, heightAt(0f, 0f) + BALL_RADIUS, 0f)
    val cup: GolfPoint = GolfPoint(finishX, heightAt(finishX, length), length)
    val openingTarget: GolfPoint get() = recommendedLanding(tee)

    /** The plan of the hole (menus, map): its middle and size in metres. A mini-golf plan frames the lanes. */
    val mapCentreX: Float get() = mini?.let { (it.bounds[0] + it.bounds[2]) * .5f } ?: 0f
    val mapCentreZ: Float get() = mini?.let { (it.bounds[1] + it.bounds[3]) * .5f } ?: (length * .5f)
    val mapWidth: Float get() = mini?.let { it.bounds[2] - it.bounds[0] + 1.2f } ?: width
    val mapDepth: Float get() = mini?.let { it.bounds[3] - it.bounds[1] + 1.2f } ?: (length + 35f)

    /** Length shown in the menus: the caddie's route for a mini-golf hole, whose lanes may turn back. */
    val displayLength: Float get() = mini?.pathLength ?: length

    val trees: List<GolfTree> by lazy {
        if (mini != null) return@lazy emptyList()
        if (islands.isNotEmpty()) return@lazy plantedTrees
        val random = Random(number * 7349)
        buildList {
            addAll(plantedTrees)
            repeat(110) { i ->
                val z = if (i % 3 == 0) random.nextFloat() * (length + 65f) - 15f else {
                    val grove = (i % 4 + .55f + (number % 3) * .13f) / 4.8f
                    (grove * length + (random.nextFloat() - .5f) * 65f).coerceIn(-10f, length + 35f)
                }
                val side = if (i % 2 == 0) -1f else 1f
                // Small groves and meadow openings, not two equally spaced avenues.
                val offset = fairwayWidth(z) * .5f + 19f + random.nextFloat() * 44f
                val x = (fairwayCenter(z) + side * offset).coerceIn(-width * .47f, width * .47f)
                val radius = 2.8f + random.nextFloat() * 2.5f
                if (hypot(x - cup.x, z - cup.z) > 25f + radius && hypot(x, z) > 15f + radius &&
                    hazards.none { it.signedDistance(x, z) < radius + 3f } &&
                    fairwaySignedDistance(x, z) > radius + 8f &&
                    (attackTreeClearance <= 0f || attackLandings.none { a ->
                        val dx = a.x - a.fromX; val dz = a.z - a.fromZ
                        val t = (((x - a.fromX) * dx + (z - a.fromZ) * dz) /
                            (dx * dx + dz * dz).coerceAtLeast(1f)).coerceIn(0f, 1f)
                        hypot(x - a.fromX - dx * t, z - a.fromZ - dz * t) <
                            radius * 1.5f + attackTreeClearance
                    }) &&
                    abs(x - pathX(z)) > 5f + radius &&
                    (number % 3 != 0 || z / length !in .25f.. .48f)) {
                    val kind=if(landscapeStyle==GolfLandscapeStyle.SNOW_MOUNTAINS ||
                        landscapeStyle==GolfLandscapeStyle.AUTUMN_HIGHLANDS && i%5<3)1 else i%3
                    add(GolfTree(x, z, radius, kind))
                }
            }
        }
    }

    fun pathX(z: Float): Float {
        var x = fairwayCenter(z) + max(10f, fairwayWidth(z) * .5f) + 15f
        // Keep the path beyond lakes and the greenside bunkers displaced outside the collar.
        for (hazard in hazards) if (hazard.lie == GolfLie.WATER || hazard.lie == GolfLie.BUNKER) {
            val c = cos(hazard.rotation); val s = sin(hazard.rotation)
            val halfX = hypot(hazard.rx * c, hazard.rz * s) * (1f + hazard.shape)
            val halfZ = hypot(hazard.rx * s, hazard.rz * c) * (1f + hazard.shape)
            val dz = abs(z - hazard.z)
            val aroundHazard = smooth((halfZ + 35f - dz) / 35f)
            val clearance = if (hazard.lie == GolfLie.WATER) 12f else 6f
            x += max(0f, hazard.x + halfX + clearance - x) * aroundHazard
        }
        return x.coerceIn(-width * .47f, width * .47f)
    }

    private fun routeSegment(z: Float): Int {
        for (i in 0 until route.lastIndex) if (z <= route[i + 1].z) return i
        return max(0, route.lastIndex - 1)
    }

    fun fairwayCenter(z: Float): Float {
        if (route.isEmpty()) {
            val t = (z / length).coerceIn(0f, 1f)
            return finishX * t + bend * sin(t * PI.toFloat())
        }
        if (z <= route.first().z) return route.first().x
        if (z >= route.last().z) return route.last().x
        val i = routeSegment(z)
        val a = route[i]; val b = route[i + 1]
        val before = route[max(0, i - 1)]; val after = route[min(route.lastIndex, i + 2)]
        val span = b.z - a.z
        val t = ((z - a.z) / span).coerceIn(0f, 1f)
        val slopeA = (b.x - before.x) / (b.z - before.z)
        val slopeB = (after.x - a.x) / (after.z - a.z)
        val t2 = t * t; val t3 = t2 * t
        return (2f*t3 - 3f*t2 + 1f)*a.x + (t3 - 2f*t2 + t)*span*slopeA +
            (-2f*t3 + 3f*t2)*b.x + (t3 - t2)*span*slopeB
    }

    fun fairwayWidth(z: Float): Float {
        if (route.isEmpty()) return fairwayBaseWidth
        if (z <= route.first().z) return route.first().width
        if (z >= route.last().z) return route.last().width
        val i = routeSegment(z)
        val a = route[i]; val b = route[i + 1]
        val width = a.width + (b.width - a.width) * smooth((z - a.z) / (b.z - a.z))
        val entrance = if (par > 3) {
            val d = (z - fairwayStart - 22f) / 31f
            (9f + number % 3 * 2f) * exp(-d * d)
        } else 0f
        return width + entrance
    }

    fun fairwaySignedDistance(x: Float, z: Float): Float {
        if (islands.isNotEmpty()) return islandSignedDistance(x, z) + ISLAND_ROUGH_WIDTH
        var distance = primaryFairwaySignedDistance(x, z)
        for (index in alternateFairwayIndexes) distance = min(distance, index.signedDistance(x, z))
        return distance
    }

    private fun primaryFairwaySignedDistance(x: Float, z: Float): Float {
        if (route.isNotEmpty()) {
            val best = primaryFairwayIndex.signedDistance(x, z)
            return if (best.isFinite()) best else max(15f, abs(x - fairwayCenter(z)))
        }
        val w = fairwayWidth(z)
        if (w < .1f) return max(SEMI_ROUGH_WIDTH + 1f, abs(x - fairwayCenter(z)))
        val center = fairwayCenter(z)
        val slope = (fairwayCenter(z + 1f) - fairwayCenter(z - 1f)) * .5f
        val edge = if (route.isEmpty()) 0f else min(1.15f, w * .025f) *
            sin(z * .095f + number * 1.7f + if (x > center) .7f else 2.3f)
        return max(abs(x - center) / sqrt(1f + slope*slope) - w * .5f - edge,
            max(-5f - z, z - length - 9f))
    }

    fun greenSignedDistance(x: Float, z: Float): Float = mini?.sdf(x, z) ?: organicDistance(
        x - finishX - greenShape.offsetX, z - length - greenShape.offsetZ,
        greenRadius * greenShape.aspect, greenRadius / greenShape.aspect,
        greenCos, greenSin, greenShape.shape, greenShape.phase
    )

    fun islandSignedDistance(x: Float, z: Float): Float {
        var distance = Float.POSITIVE_INFINITY
        for (island in islands) distance = min(distance, island.signedDistance(x, z))
        return distance
    }

    private fun terrainBase(x: Float, z: Float): Float {
        var h: Float
        if (elevationProfile.isEmpty()) {
            h = elevation * smooth(z / length)
        } else {
            val i = elevationProfile.indexOfFirst { it.z >= z }
            h = when {
                i == 0 -> elevationProfile.first().height
                i < 0 -> elevationProfile.last().height
                else -> {
                    val a = elevationProfile[i - 1]; val b = elevationProfile[i]
                    a.height + (b.height - a.height) * smooth((z - a.z) / (b.z - a.z))
                }
            }
        }
        val lateral = x - fairwayCenter(z)
        // One crossfall and one broad crown/swale, following the routing instead of oscillating.
        h += fairwayRelief.crossfall * lateral +
            fairwayRelief.crown * exp(-lateral * lateral / 900f)
        for (mound in mounds) {
            val dx = (x - mound.x) / mound.rx; val dz = (z - mound.z) / mound.rz
            h += mound.height * exp(-1.6f * (dx*dx + dz*dz)) *
                smooth((if (alternateRoutes.isEmpty()) abs(lateral) - fairwayWidth(z) * .5f
                    else fairwaySignedDistance(x, z)) / 24f)
        }
        return h
    }

    private fun terrainHeight(x: Float, z: Float): Float {
        var h = terrainBase(x, z)
        for (shelf in landingShelves) {
            val dx = x - shelf.x; val dz = z - shelf.z
            val distance = hypot(dx / 42f, dz / 52f)
            if (distance < 1f) {
                val blend = smooth((distance - .45f) / .55f)
                val plane = shelf.y + fairwayRelief.crossfall * dx + .01f * dz
                h = plane + (h - plane) * blend
            }
        }
        return h
    }

    fun waterHeight(hazard: GolfHazard): Float = waterLevels[hazard] ?: (terrainHeight(hazard.x, hazard.z) - 1.2f)

    fun heightAt(x: Float, z: Float): Float {
        mini?.let { return it.heightAt(x, z) }
        val greenDistance = greenSignedDistance(x, z)
        val dx = x - finishX; val dz = z - length
        val greenHeight = elevation + greenShape.slopeX * dx + greenShape.slopeZ * dz +
            greenShape.relief.heightAt(dx, dz) -
            cupBowlDepth * (1f - smooth((hypot(dx, dz) - .2f) / 16f))
        // Most putting/mesh samples need no route, mound, shelf or hazard evaluation.
        if (greenDistance <= 2f) return greenHeight
        if (islands.isNotEmpty()) {
            val shore = islandSignedDistance(x, z)
            if (shore >= 0f) return islandWaterLevel
            val land = islandWaterLevel + (elevation - islandWaterLevel) * smooth(-shore / 6f)
            val blend = smooth((greenDistance - 2f) / 8f)
            return greenHeight * (1f - blend) + land * blend
        }
        var height = terrainHeight(x, z)
        for (hazard in hazards) if (hazard.lie == GolfLie.WATER) {
            val d = hazard.signedDistance(x, z)
            if (d < 9f) {
                val blend = smooth(d / 9f)
                height = waterHeight(hazard) * (1f - blend) + height * blend
            }
        }
        // Tee boxes are genuinely level, with a rounded bank outside the playing platform.
        val teeBlend = smooth((max(abs(x) / 5.5f, abs(z) / 7f) - 1f) / 1.4f)
        height = teeTerrainHeight * (1f - teeBlend) + height * teeBlend
        // Continue the designed surface through the 2 m collar, then blend into its supporting
        // bank. Both endpoints have zero blend derivative: no inherited bumps at the green edge.
        val greenBlend = smooth((greenDistance - 2f) / 24f)
        height = greenHeight * (1f - greenBlend) + height * greenBlend
        for (hazard in hazards) if (hazard.lie == GolfLie.BUNKER) {
            val d = hazard.signedDistance(x, z)
            if (d < 0f) height -= .7f * smooth(-d / 3f) * smooth((greenDistance - 2f) / 3f)
        }
        // Apply shore grading last: the green terrace must not lift the outer lake bank
        // after its inner edge has been made horizontal (which creates a jagged cliff).
        if (greenDistance > 2f) {
            for (hazard in hazards) if (hazard.lie == GolfLie.WATER) {
                val d = hazard.signedDistance(x, z)
                if (d < 9f) {
                    // Shore grading still runs last, outside the protected putting terrace/collar.
                    val influence = (1f - smooth(d / 9f)) * smooth((greenDistance - 2f) / 5f)
                    height += (waterHeight(hazard) - height) * influence
                }
            }
        }
        if (retainingBankHeight > 0f) {
            // One bank around the union: the fairway entrance into the green stays open.
            // Smooth grassy slopes retain rolling misses without introducing collision walls.
            val distance = min(greenDistance - 2f, fairwaySignedDistance(x, z))
            val bank = smooth(distance / 8f) * (1f - smooth((distance - 10f) / 14f))
            var dry = 1f
            for (hazard in hazards) if (hazard.lie == GolfLie.WATER) {
                dry = min(dry, smooth(hazard.signedDistance(x, z) / 5f))
            }
            height += retainingBankHeight * bank * dry * teeBlend
        }
        return height
    }

    fun lieAt(x: Float, z: Float): GolfLie {
        mini?.let { return it.lieAt(x, z) }
        if (abs(x) > width * .5f || z < -20f || z > length + 55f) return GolfLie.OUT
        val greenDistance = greenSignedDistance(x, z)
        if (greenDistance <= 0f) return GolfLie.GREEN
        if (islands.isNotEmpty() && islandSignedDistance(x, z) >= 0f) return GolfLie.WATER
        if (abs(x) < 4.5f && abs(z) < 5.5f) return GolfLie.TEE
        hazards.firstOrNull { it.contains(x, z) }?.let { return it.lie }
        if (greenDistance <= FRINGE_WIDTH) return GolfLie.FRINGE
        val fairwayDistance = fairwaySignedDistance(x, z)
        if (fairwayDistance <= 0f) return GolfLie.FAIRWAY
        if (fairwayDistance <= SEMI_ROUGH_WIDTH) return GolfLie.SEMI_ROUGH
        return GolfLie.ROUGH
    }

    /** Caddie orientation only: does not choose power, correct a swing or play a stroke. */
    fun recommendedLanding(ball: GolfPoint): GolfPoint {
        mini?.let { return it.aim(ball, cup) }
        if (islands.isNotEmpty()) {
            val reach = if (lieAt(ball.x, ball.z) == GolfLie.TEE) 225f else 205f
            if (hypot(cup.x - ball.x, cup.z - ball.z) <= reach) return cup
            val target = islands.filter { it.z > ball.z + 30f &&
                hypot(it.x - ball.x, it.z - ball.z) <= reach }
                .minByOrNull { hypot(cup.x - it.x, cup.z - it.z) }
                ?: islands.filter { it.z > ball.z + 30f }.minByOrNull { hypot(it.x - ball.x, it.z - ball.z) }
            if (target != null) return GolfPoint(target.x, heightAt(target.x, target.z), target.z)
            return cup
        }
        if (par == 3 || hypot(cup.x - ball.x, cup.z - ball.z) < 225f) return cup
        val z = max(landingZ, ball.z + 160f).coerceAtMost(length - 35f)
        val center = fairwayCenter(z)
        val offsets = floatArrayOf(0f, -7f, 7f, -12f, 12f)
        val x = offsets.map { center + it }.firstOrNull {
            lieAt(it, z) == GolfLie.FAIRWAY && hazards.none { h -> h.signedDistance(it, z) < 5f }
        } ?: center
        return GolfPoint(x, heightAt(x, z), z)
    }

    companion object {
        const val SEMI_ROUGH_WIDTH = 3f
        const val FRINGE_WIDTH = 2f
        const val ISLAND_ROUGH_WIDTH = 6f
        const val BALL_RADIUS = GolfBallPhysics.RADIUS
        /** The regulation hole: 108 mm across, at least 4 inches deep, with a 2.4 m flagstick. */
        const val CUP_RADIUS = .054f
        const val CUP_DEPTH = .102f
        const val PIN_RADIUS = .0125f
        const val PIN_HEIGHT = 2.4f
    }
}

object ClassicCourse {
    /* Original parkland routing, not copied maps. Design/topography references:
     * https://www.pebblebeach.com/content/uploads/PebbleBeach-Scorecard.pdf
     * https://www.pebblebeach.com/golf/spyglass-hill-golf-course/
     * https://www.theopen.com/st-andrews-150th-open/course-guide
     * https://www.theopen.com/latest/navigating-the-road-hole-at-the-old-course
     * Principles: downhill pond par 3; raised approach over a ravine; angled green with
     * open entry; generous alternate landing area around a corner bunker; coastal finish.
     * Reworked as the forgiving first course: broad fairways, lateral hazards, reachable par 5s
     * and one drivable par 4. Collecting greens, grassy retaining banks and double-width cups
     * ease this course only; equipment and the physical ball/cup simulation stay shared.
     */
    private fun sand(x: Float, z: Float, rx: Float = 7f, rz: Float = 11f,
        angle: Float = 0f, shape: Float = .23f) = GolfHazard(x,z,rx,rz,GolfLie.BUNKER,angle,shape,x*.11f)
    private fun lake(x: Float, z: Float, rx: Float, rz: Float, angle: Float = 0f) =
        GolfHazard(x,z,rx,rz,GolfLie.WATER,angle,.16f,z*.07f)
    private fun route(vararg nodes: Triple<Int, Int, Int>) = nodes.map { GolfRouteNode(it.first.toFloat(),it.second.toFloat(),it.third.toFloat()) }
    private fun n(x: Int,z: Int,w: Int) = Triple(x,z,w)
    private fun relief(vararg nodes: Pair<Int, Int>) = nodes.map { GolfElevationNode(it.first.toFloat(),it.second.toFloat()) }
    private fun hill(x: Int,z: Int,h: Int,rx: Int,rz: Int) = GolfMound(x.toFloat(),z.toFloat(),h.toFloat(),rx.toFloat(),rz.toFloat())

    val holes: List<ClassicHole> = listOf(
        // 1. Welcoming downhill drive and broad run-up: a short approach sets up the first birdie.
        ClassicHole(1,4,285f,finishX=-15f,elevation=4f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(-3,41,30),n(-14,101,57),n(-5,165,76),n(2,202,67),n(-10,248,52),n(-15,285,46)),
            elevationProfile=relief(0 to 7,51 to 6,133 to 1,198 to 1,285 to 4),
            mounds=listOf(hill(-47,106,6,34,55),hill(42,221,5,32,47)),
            hazards=listOf(sand(52f,184f,7f,12f,.45f),sand(-54f,283f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.06f,0.0f,0f,0f,.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.PLANE, 0f, 0f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f),landingZ=165f,fairwayStart=65f),
        // 2. Gentle dogleg with a broad outside shelf; the corner bunker stays beyond the safe landing.
        ClassicHole(2,4,310f,finishX=49f,elevation=7f,width=320f,greenRadius=21f,
            route=route(n(0,0,13),n(-11,63,30),n(-22,135,64),n(-20,180,79),n(2,215,62),n(37,255,52),n(49,310,46)),
            elevationProfile=relief(0 to 2,99 to 5,175 to 6,234 to 3,310 to 7),
            mounds=listOf(hill(39,157,8,36,40),hill(-65,244,5,41,47)),
            hazards=listOf(sand(-77f,199f,7f,12f,-.4f),sand(88f,308f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,-.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.RIDGE, .6f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,.10f),landingZ=180f,fairwayStart=65f),
        // 3. Short iron across the meadow; the pond is lateral and the large sloping green accepts a running ace.
        ClassicHole(3,3,115f,finishX=-16f,elevation=2f,width=320f,greenRadius=22f,
            route=route(n(0,0,12),n(-2,14,0),n(-6,24,0),n(-14,81,0),n(-18,99,40),n(-16,115,46)),
            elevationProfile=relief(0 to 7,31 to 6,70 to 0,94 to 1,115 to 2),
            mounds=listOf(hill(-61,75,7,30,36)),
            hazards=listOf(lake(55f,75f,22f,21f,.65f),sand(-57f,118f,7f,10f,-.5f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.PLANE, 0f, 0f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f)),
        // 4. Short par 5 with two broad shelves: a placed drive leaves a wood or long iron for an eagle putt.
        ClassicHole(4,5,405f,finishX=22f,elevation=5f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(-6,48,30),n(-33,114,52),n(-43,173,74),n(-25,220,52),n(13,273,61),n(38,323,78),n(32,367,52),n(22,405,46)),
            elevationProfile=relief(0 to 9,62 to 8,161 to 1,238 to 4,308 to 1,405 to 5),
            mounds=listOf(hill(15,167,9,38,63),hill(-59,299,7,42,44)),
            hazards=listOf(sand(-100f,192f,7f,12f,-.4f),sand(61f,403f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,0f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.SWALE, 0f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,-.10f),landingZ=173f,fairwayStart=65f),
        // 5. Lake kept outside the generous land route, with a low rear terrace and open entrance.
        ClassicHole(5,4,295f,finishX=-10f,elevation=2f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(10,59,30),n(29,124,56),n(33,180,72),n(16,222,55),n(-4,257,52),n(-10,295,46)),
            elevationProfile=relief(0 to 3,95 to 7,163 to 4,231 to 1,295 to 2),
            mounds=listOf(hill(86,150,8,38,62)),
            hazards=listOf(lake(-62f,180f,25f,40f,-.38f),sand(90f,199f,7f,12f,.45f),sand(-49f,293f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.TIER, 1.5707964f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f),landingZ=180f,fairwayStart=65f),
        // 6. Soft dogleg over a low shoulder; wide fairway and approach reward a controlled drive.
        ClassicHole(6,4,320f,finishX=-51f,elevation=9f,width=320f,greenRadius=21f,
            route=route(n(0,0,13),n(12,62,30),n(16,140,64),n(7,184,78),n(-25,220,59),n(-50,267,52),n(-51,320,46)),
            elevationProfile=relief(0 to 1,77 to 5,156 to 8,208 to 7,253 to 3,320 to 9),
            mounds=listOf(hill(-35,153,7,33,38),hill(52,260,5,44,47)),
            hazards=listOf(sand(-50f,203f,7f,12f,-.4f),sand(-12f,318f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,-.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.RIDGE, .45f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,.10f),landingZ=184f,fairwayStart=65f),
        // 7. Short downhill wedge to a broad green: a precise bounce and roll can reach the cup.
        ClassicHole(7,3,90f,finishX=15f,elevation=1f,width=320f,greenRadius=22f,
            route=route(n(0,0,12),n(4,14,0),n(8,56,0),n(13,74,40),n(15,90,46)),
            elevationProfile=relief(0 to 10,21 to 10,45 to 3,68 to 0,90 to 1),
            mounds=listOf(hill(-50,36,6,30,28)),
            hazards=listOf(lake(70f,65f,22f,30f,.23f),sand(-26f,93f,7f,10f,-.5f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,-.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.CROWN, 0f, .025f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f)),
        // 8. River-valley par 5 with continuous fairway; the pond is lateral, keeping the second shot open.
        ClassicHole(8,5,425f,finishX=39f,elevation=4f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(13,56,30),n(34,133,60),n(36,188,79),n(17,228,52),n(0,251,50),n(-8,272,52),n(-12,299,59),n(6,345,79),n(32,385,52),n(39,425,46)),
            elevationProfile=relief(0 to 5,92 to 2,184 to 4,252 to 0,324 to 2,425 to 4),
            mounds=listOf(hill(-48,151,9,47,53),hill(69,327,9,40,40)),
            hazards=listOf(lake(-80f,265f,30f,18f,-.23f),sand(-21f,207f,7f,12f,-.4f),sand(78f,423f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.SWALE, -.4f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,-.10f),landingZ=188f,fairwayStart=65f),
        // 9. Broad links fairway; former central sand moved to the edge so a modest miss stays playable.
        ClassicHole(9,4,285f,finishX=-3f,elevation=2f,width=320f,greenRadius=21f,
            route=route(n(0,0,13),n(-5,51,30),n(-18,113,72),n(-18,169,82),n(-4,204,74),n(11,242,55),n(-3,285,46)),
            elevationProfile=relief(0 to 5,70 to 2,136 to 0,203 to 4,285 to 2),
            mounds=listOf(hill(-69,200,6,35,37),hill(54,125,7,33,48)),
            hazards=listOf(sand(39f,188f,7f,12f,.45f),sand(-42f,283f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,0f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.RIDGE, 0f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f),landingZ=169f,fairwayStart=65f),
        // 10. Raised tee and shallow bowl, followed by a gentle climb and very soft front slope.
        ClassicHole(10,4,300f,finishX=31f,elevation=8f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(-10,52,30),n(-29,107,56),n(-23,168,76),n(1,211,52),n(28,254,50),n(31,300,46)),
            elevationProfile=relief(0 to 11,43 to 10,122 to 1,187 to 1,244 to 5,300 to 8),
            mounds=listOf(hill(26,128,7,36,51),hill(-44,256,8,40,43)),
            hazards=listOf(sand(-80f,187f,7f,12f,-.4f),sand(70f,298f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.FALSE_FRONT, 1.5707964f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,.10f),landingZ=168f,fairwayStart=65f),
        // 11. Reachable par 5; a broad mown surround catches a slightly long second shot.
        ClassicHole(11,5,420f,finishX=-34f,elevation=7f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(6,59,30),n(20,132,60),n(21,180,71),n(4,225,52),n(-23,266,59),n(-44,315,80),n(-49,359,54),n(-34,420,64)),
            elevationProfile=relief(0 to 1,78 to 4,157 to 8,220 to 3,296 to 5,356 to 2,420 to 7),
            mounds=listOf(hill(-29,139,8,37,50),hill(24,306,9,45,57)),
            hazards=listOf(sand(78f,199f,7f,12f,.45f),sand(-73f,418f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,-.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.TIER, .7f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f),landingZ=180f,fairwayStart=65f),
        // 12. Mid-iron to a generous plateau with an open front and a calm cup for a possible ace.
        ClassicHole(12,3,135f,finishX=-21f,elevation=8f,width=320f,greenRadius=22f,
            route=route(n(0,0,12),n(-3,16,0),n(-8,85,0),n(-16,113,40),n(-21,135,46)),
            elevationProfile=relief(0 to 2,29 to 1,69 to 0,96 to 3,135 to 8),
            mounds=listOf(hill(44,71,8,40,37),hill(-67,120,7,28,31)),
            hazards=listOf(sand(-62f,138f,7f,10f,-.5f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.TIER, .2f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,.10f)),
        // 13. Drivable short par 4: a wide direct corridor makes an eagle putt a realistic reward,
        // and a broad mown surround catches a drive that runs through the green.
        ClassicHole(13,4,240f,finishX=-29f,elevation=4f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(0,52,30),n(-5,105,62),n(-12,165,76),n(-21,203,56),n(-29,240,62)),
            elevationProfile=relief(0 to 7,65 to 4,124 to 1,179 to 1,240 to 4),
            mounds=listOf(hill(78,175,9,38,40)),
            hazards=listOf(lake(-82f,140f,24f,28f,-.4f),sand(45f,184f,7f,12f,.45f),sand(-68f,238f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,-.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.CROWN, 0f, .025f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f),landingZ=165f,fairwayStart=65f),
        // 14. Broad, gentle hook; the outside dunes frame the shot without squeezing its landing.
        ClassicHole(14,4,335f,finishX=-55f,elevation=2f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(16,64,30),n(23,128,63),n(10,183,82),n(-16,223,66),n(-45,266,52),n(-55,335,46)),
            elevationProfile=relief(0 to 2,71 to 5,144 to 8,191 to 7,250 to 1,335 to 2),
            mounds=listOf(hill(-33,138,8,35,36),hill(40,268,10,43,55)),
            hazards=listOf(sand(-47f,202f,7f,12f,-.4f),sand(-16f,333f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.RIDGE, -.5f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,.10f),landingZ=183f,fairwayStart=65f),
        // 15. Continuous fairway through a shallow dry hollow, with a broad run-up for the second shot.
        ClassicHole(15,4,345f,finishX=42f,elevation=10f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(-7,62,30),n(-11,127,57),n(-2,183,72),n(14,218,52),n(22,240,50),n(25,254,49),n(27,272,51),n(39,302,50),n(42,345,58)),
            elevationProfile=relief(0 to 3,82 to 7,176 to 8,222 to 6,247 to 1,287 to 6,345 to 10),
            mounds=listOf(hill(-67,224,7,35,38),hill(89,148,10,44,54)),
            hazards=listOf(sand(55f,202f,7f,12f,.45f),sand(3f,343f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,1.2f,0f,0f,-.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.RIDGE, .35f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f),landingZ=183f,fairwayStart=65f),
        // 16. An iron beside a lateral lake to an open green: room to miss and a line for a precise ace.
        ClassicHole(16,3,150f,finishX=24f,elevation=2f,width=320f,greenRadius=22f,
            route=route(n(0,0,12),n(4,21,0),n(11,96,0),n(32,119,47),n(24,150,46)),
            elevationProfile=relief(0 to 7,42 to 5,90 to 0,127 to 1,150 to 2),
            mounds=listOf(hill(76,109,7,34,41)),
            hazards=listOf(lake(-53f,102f,23f,28f,-.33f),sand(-17f,153f,7f,10f,-.5f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,1.8f,0f,0f,.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.PLANE, 0f, 0f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,.10f)),
        // 17. The former skinny road-hole green becomes a broad angled target, with sand beyond the collar.
        ClassicHole(17,4,315f,finishX=48f,elevation=5f,width=320f,greenRadius=21f,
            route=route(n(0,0,12),n(-10,58,30),n(-19,127,61),n(-10,179,72),n(14,221,54),n(36,265,52),n(48,315,46)),
            elevationProfile=relief(0 to 1,82 to 5,160 to 7,222 to 2,272 to 3,315 to 5),
            mounds=listOf(hill(39,151,8,37,41),hill(-41,253,7,37,41)),
            hazards=listOf(sand(47f,198f,7f,12f,.45f),sand(9f,313f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,-.3f,.12f,0.0f,0f,0f,-.004f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.TIER, .7f, .07f, run=18f)),
            fairwayRelief=GolfFairwayRelief(.006f,.10f),landingZ=179f,fairwayStart=65f),
        // 18. Reachable lakeside finishing par 5, two broad landing shelves and an open collecting green.
        ClassicHole(18,5,435f,finishX=-8f,elevation=4f,width=320f,greenRadius=24f,
            route=route(n(0,0,13),n(10,58,30),n(27,130,60),n(37,186,78),n(41,233,52),n(37,285,60),n(22,335,82),n(1,381,54),n(-8,435,46)),
            elevationProfile=relief(0 to 9,72 to 7,158 to 2,226 to 3,300 to 1,360 to 2,435 to 4),
            mounds=listOf(hill(99,242,9,34,75),hill(-57,412,7,36,39)),
            hazards=listOf(lake(-69f,280f,29f,62f,-.15f),sand(-20f,205f,7f,12f,-.4f),sand(31f,433f,7f,11f,.6f)),
            // Gentle drainage, a calm cup and a shallow version of the original contour.
            greenShape=GolfGreenShape(1.04f,.3f,.12f,0.6f,0f,0f,.003f,.014f,
                relief=GolfGreenRelief(GolfGreenForm.PUNCHBOWL, 0f, .025f, run=18f)),
            fairwayRelief=GolfFairwayRelief(-.006f,.10f),landingZ=186f,fairwayStart=65f)
    ).map { hole ->
        hole.copy(
            elevation = hole.elevation * .5f,
            elevationProfile = hole.elevationProfile.map { it.copy(height = it.height * .5f) },
            mounds = hole.mounds.map { it.copy(height = it.height * .5f) },
            fairwayRelief = GolfFairwayRelief(hole.fairwayRelief.crossfall * .5f, hole.fairwayRelief.crown * .5f),
            greenShape = hole.greenShape.copy(slopeX = 0f, slopeZ = 0f, relief = GolfGreenRelief()),
            cupRadius = ClassicHole.CUP_RADIUS * 2f,
            retainingBankHeight = 1.2f,
            cupBowlDepth = .16f
        )
    }
}
