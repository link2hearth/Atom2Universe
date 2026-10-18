package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

/** Compare the optimized query to the former full scan, including its distance fallbacks. */
class GolfFairwayIndexTest {
    private fun scan(nodes: List<GolfRouteNode>, x: Float, z: Float, padding: Float): Float {
        var best = Float.POSITIVE_INFINITY
        for (i in 0 until nodes.lastIndex) {
            val a = nodes[i]; val b = nodes[i + 1]
            val radius = max(a.width, b.width) * .5f
            if (radius < 2f || z < a.z - radius - padding || z > b.z + radius + padding) continue
            val dx = b.x - a.x; val dz = b.z - a.z
            val t = (((x - a.x) * dx + (z - a.z) * dz) / (dx * dx + dz * dz)).coerceIn(0f, 1f)
            best = min(best, hypot(x - a.x - dx * t, z - a.z - dz * t) -
                (a.width + (b.width - a.width) * t) * .5f)
        }
        return best
    }

    @Test fun indexedDistancesExactlyMatchAllFourCoursesIncludingIsolatedGaps() {
        val random = Random(742)
        for (h in ClassicCourse.holes + HeatherCourse.holes + WildDetoursCourse.holes + VertigoCourse.holes) {
            if (h.route.isEmpty()) continue
            val primary = buildList {
                var z = h.fairwayStart
                while (z < h.length) {
                    add(GolfRouteNode(h.fairwayCenter(z), z, h.fairwayWidth(z))); z += 4f
                }
                add(GolfRouteNode(h.finishX, h.length, h.fairwayWidth(h.length)))
            }
            val alternate = h.alternateRoutes.map { nodes -> buildList {
                var z = max(h.fairwayStart, nodes.first().z)
                while (z < nodes.last().z) {
                    val i = nodes.indexOfFirst { it.z > z }.coerceAtLeast(1)
                    val a = nodes[i - 1]; val b = nodes[i]
                    val u = ((z - a.z) / (b.z - a.z)).coerceIn(0f, 1f)
                    val t = u * u * (3f - 2f * u)
                    add(GolfRouteNode(a.x + (b.x - a.x) * t, z, a.width + (b.width - a.width) * t)); z += 4f
                }
                add(nodes.last())
            } }
            val positions = h.route + h.alternateRoutes.flatten() + List(160) {
                GolfRouteNode((random.nextFloat() - .5f) * (h.width + 200f),
                    random.nextFloat() * (h.length + 160f) - 80f, 0f)
            }
            for (p in positions) for (offset in floatArrayOf(-.001f, 0f, .001f)) {
                val z = p.z + offset
                var expected = scan(primary, p.x, z, 15f)
                if (!expected.isFinite()) expected = max(15f, abs(p.x - h.fairwayCenter(z)))
                for (nodes in alternate) expected = min(expected, scan(nodes, p.x, z, ClassicHole.SEMI_ROUGH_WIDTH))
                assertEquals("Hole ${h.number}, ${p.x}/$z", expected, h.fairwaySignedDistance(p.x, z), 0f)
            }
        }
    }

    @Test fun bucketEdgesAndEmptyCorridorsPreserveTheirExactResults() {
        val samples = listOf(GolfRouteNode(-60f, -30f, 0f), GolfRouteNode(50f, 10f, 48f),
            GolfRouteNode(-30f, 25f, 18f), GolfRouteNode(-40f, 60f, 0f), GolfRouteNode(80f, 100f, 22f))
        for (padding in floatArrayOf(3f, 15f)) {
            val index = GolfFairwayIndex(samples, padding)
            for (band in -10..15) for (delta in floatArrayOf(-.001f, 0f, .001f)) for (x in -150..150 step 15) {
                val z = band * 16f + delta
                assertEquals(scan(samples, x.toFloat(), z, padding), index.signedDistance(x.toFloat(), z), 0f)
            }
        }
        assertEquals(Float.POSITIVE_INFINITY, GolfFairwayIndex(emptyList(), 15f).signedDistance(0f, 0f), 0f)
    }

    @Test fun fixedWaterLevelsAndTeeHeightMatchTheirUncachedTerrainValues() {
        val terrain = ClassicHole::class.java.getDeclaredMethod("terrainHeight", Float::class.javaPrimitiveType,
            Float::class.javaPrimitiveType).apply { isAccessible = true }
        for (h in ClassicCourse.holes + HeatherCourse.holes + WildDetoursCourse.holes + VertigoCourse.holes) {
            val tee = terrain.invoke(h, 0f, 0f) as Float
            assertEquals(tee + ClassicHole.BALL_RADIUS, h.tee.y, 0f)
            for (lake in h.hazards.filter { it.lie == GolfLie.WATER }) {
                fun level(w:GolfHazard) = (w.watercourse?.nodes?.minOf {
                    terrain.invoke(h,w.x+it.x,w.z+it.z) as Float
                } ?: (terrain.invoke(h,w.x,w.z) as Float)) - 1.2f
                assertEquals(level(lake), h.waterHeight(lake), 0f)
                val other = lake.copy(x = lake.x + 1f)
                assertEquals(level(other), h.waterHeight(other), 0f)
            }
        }
    }
}
