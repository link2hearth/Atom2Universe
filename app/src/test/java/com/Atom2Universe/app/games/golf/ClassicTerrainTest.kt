package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.util.Locale
import kotlin.math.*

/** Fast guards on the final surface (including any bank/sand blending), in world metres. */
@RunWith(Parameterized::class)
class ClassicTerrainTest(private val courseId: String, private val holes: List<ClassicHole>) {
    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun courses() = listOf(arrayOf<Any>("gardens", ClassicCourse.holes), arrayOf<Any>("heather", HeatherCourse.holes))
    }
    private fun gradient(h: ClassicHole, x: Float, z: Float) =
        (h.heightAt(x + .15f, z) - h.heightAt(x - .15f, z)) / .3f to
            (h.heightAt(x, z + .15f) - h.heightAt(x, z - .15f)) / .3f

    @Test fun everyGreenIsSmoothDrainableAndHasACalmCup() {
        val report = StringBuilder("hole,min_slope_percent,max_slope_percent,cup_max_percent,midpoint_error_mm\n")
        for (h in holes) {
            var maximum = 0f; var minimum = Float.MAX_VALUE; var cupMaximum = 0f; var curvature = 0f
            // 25 cm grid, with the same gradient stencil as ClassicGame.
            for (ix in -120..120) for (iz in -120..120) {
                val dx = ix * .25f; val dz = iz * .25f
                val x = h.cup.x + dx; val z = h.cup.z + dz
                if (h.greenSignedDistance(x, z) > 0f) continue
                val (gx, gz) = gradient(h, x, z)
                val slope = hypot(gx, gz)
                maximum = max(maximum, slope); minimum = min(minimum, slope)
                assertTrue("Rest ${h.number} at $dx,$dz ($slope)", GolfBallPhysics.canRest(gx, gz, GolfLie.GREEN))
                if (hypot(dx, dz) <= 3f) {
                    cupMaximum = max(cupMaximum, slope)
                    val plane = h.cup.y + h.greenShape.slopeX * dx + h.greenShape.slopeZ * dz
                    if (h.cupBowlDepth == 0f) assertEquals("Cup plane ${h.number}", plane, h.heightAt(x, z), .00001f)
                    else assertTrue("Collecting cup ${h.number}", h.heightAt(x, z) >= h.cup.y - .00001f)
                }
                // Sag against a 2 m chord in four directions, wholly inside green + collar.
                for (a in 0..3) {
                    val angle = a * PI.toFloat() / 4f
                    val ox = cos(angle); val oz = sin(angle)
                    if (h.greenSignedDistance(x + ox, z + oz) > 0f ||
                        h.greenSignedDistance(x - ox, z - oz) > 0f) continue
                    val sag = abs((h.heightAt(x + ox, z + oz) + h.heightAt(x - ox, z - oz)) * .5f - h.heightAt(x, z))
                    curvature = max(curvature, sag)
                }
            }
            report.append(String.format(Locale.ROOT, "%d,%.4f,%.4f,%.4f,%.4f%n",
                h.number, minimum * 100f, maximum * 100f, cupMaximum * 100f, curvature * 1000f))
            assertTrue("Green ${h.number}: max $maximum", maximum < .05f)
            if (h.cupBowlDepth == 0f) assertTrue("Green ${h.number}: drainage $minimum", minimum >= .0099f)
            assertTrue("Cup ${h.number}: $cupMaximum", cupMaximum <= .0201f)
            // <= 6 mm sag over 2 m: allows a designed tier, rejects the old short sinus humps.
            assertTrue("Green ${h.number}: 2 m sag $curvature", curvature < .006f)
        }
        File("build/reports/classic-terrain/$courseId").mkdirs()
        File("build/reports/classic-terrain/$courseId/slopes.csv").writeText(report.toString())
        println(report)
    }

    @Test fun greenCollarAndBanksJoinWithoutSlopeBreaks() {
        for (h in holes) for (i in 0 until 72) {
            val angle = i * PI.toFloat() / 36f
            val dx = cos(angle); val dz = sin(angle)
            for (edge in listOf(0f, 2f, 5f, 7f, 26f)) {
                var lo = 0f; var hi = 90f
                repeat(24) {
                    val mid = (lo + hi) * .5f
                    if (h.greenSignedDistance(h.cup.x + mid * dx, h.cup.z + mid * dz) < edge) lo = mid else hi = mid
                }
                fun y(r: Float) = h.heightAt(h.cup.x + r * dx, h.cup.z + r * dz)
                val e = .025f
                val left = (y(lo) - y(lo - e)) / e
                val right = (y(lo + e) - y(lo)) / e
                assertTrue("Slope break ${h.number}, angle $i, edge $edge: $left/$right", abs(left - right) < .025f)
            }
        }
    }

    @Test fun landingBulbsHaveGentlePlayableCores() {
        for (h in holes.filter { it.par > 3 }) {
            val shelves = h.route.filterIndexed { i, n -> i > 0 && i < h.route.lastIndex &&
                n.width >= 45f && n.width >= h.route[i - 1].width && n.width >= h.route[i + 1].width }
            assertTrue(shelves.isNotEmpty())
            for (shelf in shelves) for (ix in -8..8 step 2) for (iz in -8..8 step 2) {
                val x = shelf.x + ix; val z = shelf.z + iz
                if (h.lieAt(x, z) != GolfLie.FAIRWAY || h.hazards.any { it.signedDistance(x, z) < 10f }) continue
                val (gx, gz) = gradient(h, x, z)
                assertTrue("Landing ${h.number}: $x,$z", hypot(gx, gz) <= .0301f)
            }
        }
    }

    @Test fun bunkersKeepTheirExcavatedBowlsOutsideTheProtectedCollar() {
        for (h in holes) for (sand in h.hazards.filter { it.lie == GolfLie.BUNKER }) {
            assertEquals("Bunker centre ${h.number}: ${sand.x},${sand.z}", GolfLie.BUNKER, h.lieAt(sand.x, sand.z))
            val uncut = h.copy(hazards = h.hazards.filter { it != sand })
            val depth = uncut.heightAt(sand.x, sand.z) - h.heightAt(sand.x, sand.z)
            // Lake grading must still win at the water-side pot bunker on hole 7.
            val onBank = h.hazards.any { it.lie == GolfLie.WATER && it.signedDistance(sand.x, sand.z) < 9f }
            if (onBank) assertTrue("Water-side bunker ${h.number}: $depth", depth >= .3f)
            else assertEquals("Bunker depth ${h.number}: ${sand.x},${sand.z}", .7f, depth, .005f)
        }
    }

    @Test fun walkingPathClearsTheDisplacedBunkersAndLakes() {
        for (h in holes) for (z in -10..h.length.toInt() + 35 step 2) {
            val x = h.pathX(z.toFloat())
            for (hazard in h.hazards) assertTrue("Path ${h.number} at $x,$z through ${hazard.lie}",
                hazard.signedDistance(x, z.toFloat()) > 2f)
        }
    }

    @Test fun wellReadPuttsCanHoleFromTwoFiveAndTenMetresOnEveryGreen() {
        val report = StringBuilder("hole,distance,direction,aim_radians,power\n")
        for (h in holes) for (distance in listOf(2f, 5f, 10f)) for (direction in 0..7) {
            val angle = direction * PI.toFloat() / 4f
            val start = GolfPoint(h.cup.x - sin(angle) * distance, 0f, h.cup.z - cos(angle) * distance)
            assertEquals("Start ${h.number}/$distance/$direction", GolfLie.GREEN, h.lieAt(start.x, start.z))
            var success = false
            // Numerical line/dose search is test-only. Confirm the answer through the real game,
            // real 108 mm cup, hit(), finite-difference slopes and HOLED state, not proximity.
            for (arrival in listOf(.5f, .35f, .65f)) {
                val velocity = solvePutt(h, start, distance, angle, arrival)
                val power = (velocity.first * velocity.first + velocity.second * velocity.second) /
                    (2f * GolfBallPhysics.rolling(GolfLie.GREEN) * GolfClub.PUTTER.carry)
                val aim = atan2(velocity.first, velocity.second)
                val game = ClassicGame(h).apply {
                    restore(start, 2); club = GolfClub.PUTTER; aimAngle = aim
                    assertTrue(power > 0f && power <= 1f)
                    assertTrue(hit(power))
                }
                var steps = 0
                while (game.state == GolfState.ROLLING && steps++ < 2400) game.update(GolfBallPhysics.STEP)
                if (game.state == GolfState.HOLED && game.strokes == 3) {
                    success = true
                    report.append("${h.number},$distance,$direction,$aim,$power\n")
                    break
                }
            }
            assertTrue("No putt holes: ${h.number}, $distance m, direction $direction", success)
        }
        File("build/reports/classic-terrain/$courseId").mkdirs()
        File("build/reports/classic-terrain/$courseId/putts.csv").writeText(report.toString())
    }

    /** Shoot for the cup centre at a fixed arrival time, on continuous turf, then test the cup. */
    private fun solvePutt(h: ClassicHole, start: GolfPoint, distance: Float, angle: Float, arrival: Float): Pair<Float, Float> {
        val drag = GolfBallPhysics.rolling(GolfLie.GREEN)
        val time = (sqrt(arrival * arrival + 2f * drag * distance) - arrival) / drag
        val steps = (time / GolfBallPhysics.STEP).roundToInt()
        val speed = sqrt(arrival * arrival + 2f * drag * distance)
        var vx = sin(angle) * speed; var vz = cos(angle) * speed
        fun end(ax: Float, az: Float): BallState {
            val b = BallState().apply { px = start.x; pz = start.z; this.vx = ax; this.vz = az }
            repeat(steps) {
                val (gx, gz) = gradient(h, b.px, b.pz)
                GolfBallPhysics.rollingStep(b, gx, gz, h.lieAt(b.px, b.pz))
            }
            return b
        }
        repeat(12) {
            val b = end(vx, vz)
            val ex = h.cup.x - b.px; val ez = h.cup.z - b.pz
            if (hypot(ex, ez) < .001f) return vx to vz
            val delta = .015f
            val bx = end(vx + delta, vz); val bz = end(vx, vz + delta)
            val xx = (bx.px - b.px) / delta; val xz = (bz.px - b.px) / delta
            val zx = (bx.pz - b.pz) / delta; val zz = (bz.pz - b.pz) / delta
            val det = xx * zz - xz * zx
            if (abs(det) < .0001f) return vx to vz
            vx += ((ex * zz - ez * xz) / det).coerceIn(-1f, 1f)
            vz += ((ez * xx - ex * zx) / det).coerceIn(-1f, 1f)
        }
        return vx to vz
    }
}
