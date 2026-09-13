package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.ai.IntList
import com.Atom2Universe.app.games.caves.ai.LineOfSight
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.PathFinder
import com.Atom2Universe.app.games.caves.ai.PathFollower
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.world.BuiltinMaps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavigationTest {

    /** Petit monde de test : un sol plein en y = 0, plus les blocs listés. */
    private class TestWorld(val sizeX: Int, val sizeY: Int, val sizeZ: Int) : SolidGrid {
        private val blocks = HashSet<Triple<Int, Int, Int>>()
        fun set(x: Int, y: Int, z: Int) = blocks.add(Triple(x, y, z))
        fun column(x: Int, z: Int, height: Int) = (1..height).forEach { set(x, it, z) }
        override fun isSolid(x: Int, y: Int, z: Int) = y == 0 || Triple(x, y, z) in blocks
        fun grid() = NavGrid.build(sizeX, sizeY, sizeZ, this)
    }

    private fun edges(grid: NavGrid, node: Int) =
        (grid.edgeStart[node] until grid.edgeStart[node + 1]).map { grid.edgeTarget[it] }

    private fun path(grid: NavGrid, from: Triple<Int, Int, Int>, to: Triple<Int, Int, Int>): List<Triple<Int, Int, Int>>? {
        val out = IntList()
        val start = grid.nodeAt(from.first, from.second, from.third)
        val goal = grid.nodeAt(to.first, to.second, to.third)
        if (!PathFinder(grid).findPath(start, goal, out)) return null
        return (0 until out.size).map { Triple(grid.nodeX[out[it]], grid.nodeY[out[it]], grid.nodeZ[out[it]]) }
    }

    @Test
    fun `sur un sol plat chaque case voit ses 8 voisines`() {
        val grid = TestWorld(5, 4, 5).grid()
        assertEquals(25, grid.nodeCount)
        assertEquals(8, edges(grid, grid.nodeAt(2, 1, 2)).size)
        assertEquals(3, edges(grid, grid.nodeAt(0, 1, 0)).size)
        assertEquals(-1, grid.nodeAt(2, 2, 2))   // en l'air
    }

    @Test
    fun `le chemin contourne un mur par son ouverture`() {
        val world = TestWorld(7, 4, 7)
        for (z in 0..5) world.column(3, z, 2)   // mur en x = 3, ouvert seulement en z = 6
        val route = path(world.grid(), Triple(0, 1, 0), Triple(6, 1, 0))!!
        assertTrue(route.none { world.isSolid(it.first, it.second, it.third) })
        assertTrue("le chemin doit passer par l'ouverture", route.contains(Triple(3, 1, 6)))
    }

    @Test
    fun `une marche d un bloc se monte, deux blocs non`() {
        val world = TestWorld(3, 6, 1)
        world.column(1, 0, 1)            // marche d'un bloc en x = 1 : pieds en y = 2
        world.column(2, 0, 3)            // colonne de 3 blocs en x = 2 : pieds en y = 4, deux de plus
        val grid = world.grid()
        assertTrue(edges(grid, grid.nodeAt(0, 1, 0)).contains(grid.nodeAt(1, 2, 0)))
        assertTrue(grid.nodeAt(2, 4, 0) >= 0)
        assertFalse(edges(grid, grid.nodeAt(1, 2, 0)).contains(grid.nodeAt(2, 4, 0)))
    }

    @Test
    fun `on descend jusqu a 3 blocs, pas au dela`() {
        val world = TestWorld(3, 8, 1)
        world.column(0, 0, 3)            // rebord de 3 blocs : pieds en y = 4
        world.column(2, 0, 4)            // pilier de 4 blocs de l'autre côté
        val grid = world.grid()
        val top = grid.nodeAt(0, 4, 0)
        assertTrue(edges(grid, top).contains(grid.nodeAt(1, 1, 0)))       // chute de 3
        assertFalse(edges(grid, grid.nodeAt(1, 1, 0)).contains(top))       // on ne remonte pas
        val world2 = TestWorld(2, 8, 1)
        world2.column(0, 0, 4)           // rebord de 4 blocs
        val grid2 = world2.grid()
        assertTrue(edges(grid2, grid2.nodeAt(0, 5, 0)).isEmpty())
    }

    @Test
    fun `une diagonale ne rase pas l arete d un mur`() {
        val world = TestWorld(3, 4, 3)
        world.column(1, 0, 2)
        val grid = world.grid()
        // De (0,0) à (1,1) en diagonale : (1,0) est un mur, donc interdit.
        assertFalse(edges(grid, grid.nodeAt(0, 1, 0)).contains(grid.nodeAt(1, 1, 1)))
    }

    @Test
    fun `aucun chemin vers une zone fermee`() {
        val world = TestWorld(5, 4, 5)
        for (x in 1..3) { world.column(x, 1, 2); world.column(x, 3, 2) }
        world.column(1, 2, 2); world.column(3, 2, 2)   // (2, 2) enfermé
        assertEquals(null, path(world.grid(), Triple(0, 1, 0), Triple(2, 1, 2)))
    }

    @Test
    fun `la couverture distingue mur et muret`() {
        val world = TestWorld(3, 4, 3)
        world.column(2, 1, 2)   // mur de 2 blocs à l'est de (1, 1)
        world.column(0, 1, 1)   // muret d'un bloc à l'ouest
        val grid = world.grid()
        val n = grid.nodeAt(1, 1, 1)
        assertTrue(grid.hasFullCover(n, NavGrid.POS_X))
        assertFalse(grid.hasHalfCover(n, NavGrid.POS_X))
        assertTrue(grid.hasHalfCover(n, NavGrid.NEG_X))
        assertFalse(grid.hasFullCover(n, NavGrid.NEG_X))
        assertFalse(grid.hasFullCover(n, NavGrid.POS_Z))
    }

    @Test
    fun `la ligne de vue est coupee par un mur mais passe au dessus d un muret`() {
        val world = TestWorld(10, 5, 1)
        world.column(4, 0, 2)   // mur de 2 blocs (y = 1 et 2)
        world.column(8, 0, 1)   // muret d'un bloc (y = 1)
        val eye = 2.6           // pieds sur le sol en y = 1, yeux 1,6 bloc plus haut
        assertFalse(LineOfSight.isClear(1.5, eye, 0.5, 5.5, eye, 0.5, world))
        assertTrue(LineOfSight.isClear(5.5, eye, 0.5, 9.5, eye, 0.5, world))
        assertTrue(LineOfSight.isClear(1.5, 3.5, 0.5, 9.5, 3.5, 0.5, world))
        // Regard qui plonge vers le bas d'une cible cachée derrière le muret : coupé.
        assertFalse(LineOfSight.isClear(5.5, eye, 0.5, 9.5, 1.2, 0.5, world))
    }

    @Test
    fun `le suiveur arrive au bout du chemin`() {
        val world = TestWorld(7, 4, 7)
        for (z in 0..5) world.column(3, z, 2)
        val grid = world.grid()
        val out = IntList()
        assertTrue(PathFinder(grid).findPath(grid.nodeAt(0, 1, 0), grid.nodeAt(6, 1, 0), out))
        val follower = PathFollower(grid)
        follower.place(0.5, 1.0, 0.5)
        follower.follow(out)
        var frames = 0
        while (!follower.arrived && frames < 10_000) { follower.advance(1f / 60f, 4f); frames++ }
        assertTrue(follower.arrived)
        assertEquals(6.5, follower.x, 1e-9)
        assertEquals(0.5, follower.z, 1e-9)
    }

    @Test
    fun `dans l arene on va d un point d apparition a l autre`() {
        val map = BuiltinMaps.arena()
        val grid = NavGrid.build(map.sizeX, map.sizeY, map.sizeZ) { x, y, z ->
            map.blockAt(x, y, z) != com.Atom2Universe.app.games.caves.world.AIR
        }
        val a = map.spawnsA.first(); val b = map.spawnsB.first()
        val route = path(grid, Triple(a.x, a.y, a.z), Triple(b.x, b.y, b.z))!!
        assertTrue(route.size in 96..200)
    }
}
