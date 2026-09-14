package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.ai.*
import org.junit.Assert.*
import org.junit.Test

class SoldierCollisionTest {
    @Test fun `le volume bloque un mur et un plafond mais autorise le contact au sol`() {
        val world = SolidGrid { x, y, _ -> y == 0 || x == 3 || y == 4 }
        assertTrue(SoldierCollision.clearsWorld(world, 2.5, 1.0, 2.5))
        assertFalse(SoldierCollision.clearsWorld(world, 2.8, 1.0, 2.5))
        assertFalse(SoldierCollision.clearsWorld(world, 2.5, 2.3, 2.5))
    }

    @Test fun `deux corps se bloquent seulement si leurs hauteurs se chevauchent`() {
        assertTrue(SoldierCollision.overlaps(1.5, 1.0, 1.5, 1.9, 1.0, 1.5))
        assertFalse(SoldierCollision.overlaps(1.5, 1.0, 1.5, 2.2, 1.0, 1.5))
        assertFalse(SoldierCollision.overlaps(1.5, 1.0, 1.5, 1.5, 3.0, 1.5))
    }

    @Test fun `un long pas ne traverse pas un corps et reprend quand il se degage`() {
        val world = SolidGrid { _, y, _ -> y == 0 }
        val grid = NavGrid.build(8, 5, 3, world)
        var occupied = true
        val clearance = BodyClearance { x, y, z -> SoldierCollision.clearsWorld(world, x, y, z) &&
            !(occupied && SoldierCollision.overlaps(x, y, z, 3.5, 1.0, 1.5)) }
        val path = IntList()
        PathFinder(grid).findPath(grid.nodeAt(0, 1, 1), grid.nodeAt(7, 1, 1), path)
        val follower = PathFollower(grid, clearance)
        follower.place(.5, 1.0, 1.5); follower.follow(path)
        follower.advance(2f, 4f)
        assertTrue(follower.x <= 2.901)
        assertFalse(follower.arrived)
        occupied = false
        follower.advance(3f, 4f)
        assertTrue(follower.arrived)
    }

    @Test fun `le chemin contourne une case occupee`() {
        val world = SolidGrid { _, y, _ -> y == 0 }
        val grid = NavGrid.build(8, 5, 5, world)
        val path = IntList()
        val occupied = grid.nodeAt(3, 1, 2)
        val clearance = BodyClearance { x, y, z ->
            !SoldierCollision.overlaps(x, y, z, 3.5, 1.0, 2.5) }
        assertTrue(PathFinder(grid).findPath(grid.nodeAt(0, 1, 2), grid.nodeAt(7, 1, 2), path, clearance))
        for (i in 0 until path.size) assertNotEquals(occupied, path[i])
    }

    @Test fun `monter et descendre une marche conserve un volume libre`() {
        val world = SolidGrid { x, y, _ -> y == 0 || (x == 3 && y == 1) }
        val grid = NavGrid.build(8, 5, 1, world)
        val path = IntList()
        assertTrue(PathFinder(grid).findPath(grid.nodeAt(0, 1, 0), grid.nodeAt(7, 1, 0), path))
        val follower = PathFollower(grid, BodyClearance { x, y, z ->
            SoldierCollision.clearsWorld(world, x, y, z) })
        follower.place(.5, 1.0, .5); follower.follow(path)
        repeat(300) {
            follower.advance(.016f, 4f)
            assertTrue(SoldierCollision.clearsWorld(world, follower.x, follower.y, follower.z))
        }
        assertTrue(follower.arrived)
        assertEquals(1.0, follower.y, .0001)
    }
}
