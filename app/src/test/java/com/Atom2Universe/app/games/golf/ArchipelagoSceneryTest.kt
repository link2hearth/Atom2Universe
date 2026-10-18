package com.Atom2Universe.app.games.golf

import com.Atom2Universe.app.games.golf.classic.core.*
import com.Atom2Universe.app.games.golf.classic.render.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ArchipelagoSceneryTest {
    @Test fun everyHoleHasCoastalPalmsAndMooringsClearOfThePlayingSurfaces() {
        for (hole in ArchipelagoCourse.holes) {
            assertTrue("Palms on ${hole.number}", hole.trees.size >= 2)
            assertEquals(hole.trees, hole.copy().trees)
            for (tree in hole.trees) {
                assertEquals(3, tree.kind)
                assertTrue(hole.lieAt(tree.x, tree.z) in listOf(GolfLie.ROUGH, GolfLie.SEMI_ROUGH))
                assertTrue(hole.greenSignedDistance(tree.x, tree.z) > tree.radius + 4f)
                assertTrue(hypot(tree.x, tree.z) >= 11f)
                assertTrue(hole.attackLandings.all { hypot(tree.x - it.x, tree.z - it.z) > tree.radius + 12f })
            }
            val scene = ArchipelagoScenery(hole)
            assertEquals(hole.islands.size, scene.moorings.size)
            assertEquals(scene.moorings, ArchipelagoLayout.moorings(hole.copy()))
            hole.islands.forEachIndexed { index, island ->
                val m = scene.moorings[index]
                assertSame(m, scene.mooringFor(GolfPoint(island.x, 0f, island.z)))
                assertTrue(hole.islandSignedDistance(m.shore.x, m.shore.z) < 0f)
                for (i in 0 until 24) {
                    val angle = i * PI.toFloat() / 12f
                    assertEquals("Launch on water ${hole.number}/$index", GolfLie.WATER,
                        hole.lieAt(m.boat.x + cos(angle) * 3.2f, m.boat.z + sin(angle) * 3.2f))
                }
            }
            assertSame(scene.moorings.first(), scene.mooringFor(hole.tee))
            assertSame(scene.moorings.last(), scene.mooringFor(hole.cup))
            // Building all 18 real scene meshes catches empty candidate sets and invalid geometry.
            val meshes = scene.scenery + scene.launch
            assertTrue(meshes.sumOf { it.count } in 2000..180000)
            for (mesh in meshes) {
                val data = ClassicMesh::class.java.getDeclaredField("vertices").apply { isAccessible = true }
                    .get(mesh) as FloatArray
                assertTrue(data.all { it.isFinite() })
            }
        }
    }

    @Test fun palmTrunksAndHighFrondsStopBallsWithoutAnInvisibleLowCanopy() {
        val tree = GolfTree(0f, 0f, 3f, 3)
        val speed = GolfPoint(0f, 0f, 12f)
        val trunk = GolfTreeCollision.collide(tree, 0f, GolfPoint(0f, 5f, -2f), GolfPoint(0f, 5f, 2f), speed)
        assertNotNull(trunk)
        assertTrue(trunk!!.velocity.z < 0f)
        assertNull(GolfTreeCollision.collide(tree, 0f, GolfPoint(1f, 1f, -4f), GolfPoint(1f, 1f, 4f), speed))
        val crown = GolfTreeCollision.collide(tree, 0f, GolfPoint(1f, 7f, -4f), GolfPoint(1f, 7f, 4f), speed)
        assertNotNull(crown)
        val v = crown!!.velocity
        assertTrue(v.x*v.x + v.y*v.y + v.z*v.z < speed.z*speed.z)
    }

    @Test fun theSandBeachEndsAtTheActualWaterPenaltyBoundary() {
        for (hole in ArchipelagoCourse.holes) for (island in hole.islands) {
            for (i in 0 until 24) {
                val angle = i * PI.toFloat() / 12f
                val p = ArchipelagoLayout.coast(hole, island, angle, .9f)
                if (hole.lieAt(p.x,p.z) == GolfLie.OUT || hole.greenSignedDistance(p.x,p.z) < 0f ||
                    hole.islandSignedDistance(p.x,p.z) < -ClassicHole.ISLAND_BEACH_WIDTH) continue
                assertEquals("Beach ${hole.number}", GolfLie.BUNKER, hole.lieAt(p.x,p.z))
            }
        }
    }
}
