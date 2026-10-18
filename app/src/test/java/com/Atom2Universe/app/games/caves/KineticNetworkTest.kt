package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.FrontierWorkshops.Pos
import com.Atom2Universe.app.games.caves.world.KineticNetwork
import org.junit.Assert.*
import org.junit.Test

class KineticNetworkTest {
    /** A few hand-placed parts; axis codes as for logs: X = 1, Y = 0, Z = 2. */
    private class Grid {
        val blocks = HashMap<Pos, Short>(); val metas = HashMap<Pos, Byte>()
        val active = HashSet<Pos>()
        /** Sources with a direction of their own; the others in [active] follow the network. */
        val direction = HashMap<Pos, Int>()
        fun put(x: Int, y: Int, z: Int, id: Short, axis: Char = 'y') {
            val p = Pos(x, y, z); blocks[p] = id
            metas[p] = (when (axis) { 'x' -> 1; 'z' -> 2; else -> 0 }).toByte()
        }
        fun solve(vararg sources: Pos) = KineticNetwork({ x, y, z -> blocks[Pos(x, y, z)] ?: AIR },
            { x, y, z -> metas[Pos(x, y, z)] ?: 0 }, driving = { p, _ ->
            direction[p] ?: if (p in active) KineticNetwork.DRIVE_EITHER else KineticNetwork.DRIVE_NONE
        }).solve(sources.toList())
    }
    private val base = KineticNetwork.BASE

    @Test fun aMachineOnlyTakesTheRotationFromAGearbox() {
        val g = Grid()
        g.put(0, 0, 0, F.WATERWHEEL, 'x'); for (x in 1..3) g.put(x, 0, 0, F.SHAFT, 'x'); g.put(4, 0, 0, F.MILL)
        g.active += Pos(0, 0, 0)
        assertEquals(0f, g.solve(Pos(0, 0, 0)).speedAt(Pos(4, 0, 0)), 1e-4f)
        g.put(4, 0, 0, F.GEARBOX, 'y'); g.put(5, 0, 0, F.MILL)
        assertEquals(base, g.solve(Pos(0, 0, 0)).speedAt(Pos(5, 0, 0)), 1e-4f)
    }

    @Test fun onlyTheMillTurnsOnAShaftComingDownOntoIt() {
        val g = Grid()
        g.put(0, 0, 0, F.WATERWHEEL, 'x'); g.put(1, 0, 0, F.GEARBOX, 'z'); g.put(1, 1, 0, F.SHAFT, 'y')
        g.put(1, 2, 0, F.MILL); g.put(1, -1, 0, F.SHAFT, 'y'); g.put(1, -2, 0, F.PRESS)
        g.active += Pos(0, 0, 0)
        var r = g.solve(Pos(0, 0, 0))
        assertEquals(0f, r.speedAt(Pos(1, 2, 0)), 1e-4f)     // above the shaft: not its spindle
        assertEquals(0f, r.speedAt(Pos(1, -2, 0)), 1e-4f)    // a press never takes a shaft
        g.put(1, -2, 0, F.MILL)
        r = g.solve(Pos(0, 0, 0))
        assertEquals(base, r.speedAt(Pos(1, -2, 0)), 1e-4f)
    }

    @Test fun aRowOfGearboxesSharesOneNetwork() {
        val g = Grid()
        // Gearboxes along X, each passing the rotation on as a shaft between them would.
        g.put(0, 0, 0, F.WATERWHEEL, 'x'); g.put(1, 0, 0, F.GEARBOX, 'y'); g.put(2, 0, 0, F.GEARBOX, 'y'); g.put(3, 0, 0, F.GEARBOX, 'y')
        g.put(4, 0, 0, F.SHAFT, 'x')
        for (x in 1..3) g.put(x, 0, 1, F.MILL)
        g.active += Pos(0, 0, 0)
        val r = g.solve(Pos(0, 0, 0))
        // Straight through, each gearbox reverses the rotation: three of them, the shaft after turns backwards.
        assertEquals(-base, r.speedAt(Pos(4, 0, 0)), 1e-4f)
        for (x in 1..3) assertEquals(base, r.speedAt(Pos(x, 0, 1)), 1e-4f)
        assertEquals(6f, r.network.getValue(Pos(0, 0, 0)).stress, 1e-4f)
        // A gearbox touching by its axis face does not take part.
        g.put(2, 1, 0, F.GEARBOX, 'y'); g.put(2, 2, 0, F.MILL)
        assertEquals(0f, g.solve(Pos(0, 0, 0)).speedAt(Pos(2, 2, 0)), 1e-4f)
    }

    @Test fun wheelTurnsALineOfShaftsAndTheMillAtItsEnd() {
        val g = Grid()
        g.put(0, 0, 0, F.WATERWHEEL, 'x'); for (x in 1..3) g.put(x, 0, 0, F.SHAFT, 'x')
        g.put(4, 0, 0, F.GEARBOX, 'y'); g.put(5, 0, 0, F.MILL)
        g.active += Pos(0, 0, 0)
        val r = g.solve(Pos(0, 0, 0))
        for (x in 1..3) assertEquals(base, r.speedAt(Pos(x, 0, 0)), 1e-4f)
        assertEquals(base, r.speedAt(Pos(5, 0, 0)), 1e-4f)
        // A shaft across the line does not connect.
        g.put(2, 1, 0, F.SHAFT, 'z')
        assertEquals(0f, g.solve(Pos(0, 0, 0)).speedAt(Pos(2, 1, 0)), 0f)
    }

    @Test fun dryWheelTurnsNothing() {
        val g = Grid()
        g.put(0, 0, 0, F.WATERWHEEL, 'x'); g.put(1, 0, 0, F.SHAFT, 'x')
        assertEquals(0f, g.solve(Pos(0, 0, 0)).speedAt(Pos(1, 0, 0)), 0f)
    }

    @Test fun cogwheelsReverseAndTradeSpeed() {
        val g = Grid()
        // A crank under a vertical cogwheel; the other wheels lie in the same horizontal plane.
        g.put(0, 0, 0, F.CRANK, 'y'); g.put(0, 1, 0, F.COGWHEEL, 'y')
        g.put(1, 1, 0, F.COGWHEEL, 'y')        // side by side: same speed, opposite way
        g.put(-1, 1, 1, F.LARGE_COGWHEEL, 'y') // diagonal: half the speed, opposite way
        g.active += Pos(0, 0, 0)
        val r = g.solve(Pos(0, 0, 0))
        assertEquals(base, r.speedAt(Pos(0, 1, 0)), 1e-4f)
        assertEquals(-base, r.speedAt(Pos(1, 1, 0)), 1e-4f)
        assertEquals(-base / 2, r.speedAt(Pos(-1, 1, 1)), 1e-4f)
    }

    @Test fun gearboxTurnsAQuarterTurnAndReversesStraightThrough() {
        val g = Grid()
        g.put(0, 0, 0, F.CRANK, 'x'); g.put(1, 0, 0, F.GEARBOX, 'y')
        g.put(2, 0, 0, F.SHAFT, 'x'); g.put(1, 0, 1, F.SHAFT, 'z'); g.put(1, 0, -1, F.SHAFT, 'z')
        g.put(1, 1, 0, F.SHAFT, 'y') // on the gearbox axis: not connected
        g.active += Pos(0, 0, 0)
        val r = g.solve(Pos(0, 0, 0))
        assertEquals(-base, r.speedAt(Pos(2, 0, 0)), 1e-4f)
        assertEquals(base, kotlin.math.abs(r.speedAt(Pos(1, 0, 1))), 1e-4f)
        // Both side outputs turn the same way seen from the gearbox: opposite world speeds.
        assertEquals(-r.speedAt(Pos(1, 0, 1)), r.speedAt(Pos(1, 0, -1)), 1e-4f)
        assertEquals(0f, r.speedAt(Pos(1, 1, 0)), 0f)
    }

    @Test fun tooManyMachinesStopTheWholeNetworkUntilASecondSourceHelps() {
        val g = Grid()
        g.put(0, 0, 0, F.WATERWHEEL, 'x'); for (x in 1..3) g.put(x, 0, 0, F.SHAFT, 'x')
        g.put(4, 0, 0, F.GEARBOX, 'y'); g.put(5, 0, 0, F.CRUSHER)
        g.active += Pos(0, 0, 0)
        var r = g.solve(Pos(0, 0, 0))
        assertEquals(base, r.speedAt(Pos(5, 0, 0)), 1e-4f)           // 4 of 8
        g.put(4, 0, 1, F.PRESS); g.put(4, 0, -1, F.MILL)               // 4 + 4 + 2 = 10 of 8
        r = g.solve(Pos(0, 0, 0))
        assertTrue(r.network.getValue(Pos(0, 0, 0)).overloaded)
        assertEquals(0f, r.speedAt(Pos(5, 0, 0)), 0f)
        assertEquals(0f, r.speedAt(Pos(2, 0, 0)), 0f)
        // A crank on a cogwheel beside the line, turning it the other way round, brings 4 more.
        g.put(2, 0, 0, F.COGWHEEL, 'x'); g.put(2, 1, 0, F.COGWHEEL, 'x'); g.put(1, 1, 0, F.CRANK, 'x')
        g.active += Pos(1, 1, 0)
        r = g.solve(Pos(0, 0, 0), Pos(1, 1, 0))
        assertFalse(r.network.getValue(Pos(0, 0, 0)).stopped)
        assertEquals(12f, r.network.getValue(Pos(0, 0, 0)).capacity, 1e-4f)
        assertEquals(base, r.speedAt(Pos(4, 0, 1)), 1e-4f)
    }

    @Test fun aLoopOfGearsThatCannotTurnStops() {
        val g = Grid()
        g.put(0, -1, 0, F.CRANK, 'y')
        g.put(0, 0, 0, F.COGWHEEL, 'y'); g.put(1, 0, 0, F.COGWHEEL, 'y')        // direct: -w
        g.put(0, 1, 0, F.SHAFT, 'y'); g.put(0, 2, 0, F.LARGE_COGWHEEL, 'y')
        g.put(1, 2, 1, F.COGWHEEL, 'y'); g.put(1, 1, 1, F.SHAFT, 'y')
        g.put(1, 0, 1, F.COGWHEEL, 'y')                                         // round the loop: +2w
        g.active += Pos(0, -1, 0)
        val r = g.solve(Pos(0, -1, 0))
        assertTrue(r.network.getValue(Pos(0, 0, 0)).conflict)
        assertEquals(0f, r.speedAt(Pos(1, 0, 0)), 0f)
    }

    @Test fun theCurrentSetsTheWheelDirectionAndOpposedWheelsStop() {
        val g = Grid()
        g.put(0, 0, 0, F.WATERWHEEL, 'x'); for (x in 1..3) g.put(x, 0, 0, F.SHAFT, 'x'); g.put(4, 0, 0, F.WATERWHEEL, 'x')
        g.direction[Pos(0, 0, 0)] = KineticNetwork.DRIVE_NEGATIVE
        var r = g.solve(Pos(0, 0, 0), Pos(4, 0, 0))
        assertEquals(-base, r.speedAt(Pos(2, 0, 0)), 1e-4f)
        g.direction[Pos(4, 0, 0)] = KineticNetwork.DRIVE_NEGATIVE
        r = g.solve(Pos(0, 0, 0), Pos(4, 0, 0))
        assertEquals(16f, r.network.getValue(Pos(0, 0, 0)).capacity, 1e-4f)
        g.direction[Pos(4, 0, 0)] = KineticNetwork.DRIVE_POSITIVE
        r = g.solve(Pos(0, 0, 0), Pos(4, 0, 0))
        assertTrue(r.network.getValue(Pos(0, 0, 0)).conflict)
    }

    @Test fun largeWheelIsSlowerAndStronger() {
        val g = Grid()
        g.put(0, 0, 0, F.LARGE_WATERWHEEL, 'x'); g.put(1, 0, 0, F.SHAFT, 'x'); g.put(2, 0, 0, F.GEARBOX, 'y')
        g.put(3, 0, 0, F.PRESS); g.put(2, 0, 1, F.CRUSHER); g.put(2, 0, -1, F.MILL)
        g.direction[Pos(0, 0, 0)] = KineticNetwork.DRIVE_POSITIVE
        val r = g.solve(Pos(0, 0, 0))
        assertEquals(KineticNetwork.LARGE_WHEEL_SPEED, r.speedAt(Pos(1, 0, 0)), 1e-4f)
        assertEquals(5f, r.network.getValue(Pos(0, 0, 0)).stress, 1e-4f) // (4 + 4 + 2) at half speed
        assertFalse(r.network.getValue(Pos(0, 0, 0)).stopped)
        // A small wheel on the same axle wants twice the speed: they cannot agree.
        g.put(-1, 0, 0, F.WATERWHEEL, 'x'); g.active += Pos(-1, 0, 0)
        assertTrue(g.solve(Pos(0, 0, 0), Pos(-1, 0, 0)).network.getValue(Pos(0, 0, 0)).conflict)
    }

    @Test fun windmillForceFollowsItsSails() {
        val g = Grid()
        g.put(0, 0, 0, F.WINDMILL, 'z'); g.put(0, 0, 1, F.SHAFT, 'z'); g.put(0, 0, 2, F.GEARBOX, 'x')
        g.put(0, -1, 2, F.SHAFT, 'y'); g.put(0, -2, 2, F.GEARBOX, 'x'); g.put(0, -3, 2, F.PRESS)
        g.active += Pos(0, 0, 0)
        fun solve(sails: Float) = KineticNetwork({ x, y, z -> g.blocks[Pos(x, y, z)] ?: AIR }, { x, y, z -> g.metas[Pos(x, y, z)] ?: 0 },
            { p, _ -> if (p in g.active) KineticNetwork.DRIVE_EITHER else KineticNetwork.DRIVE_NONE }) { sails }.solve(listOf(Pos(0, 0, 0)))
        var r = solve(12f)
        assertEquals(KineticNetwork.WINDMILL_SPEED, kotlin.math.abs(r.speedAt(Pos(0, -3, 2))), 1e-4f)
        assertEquals(2f, r.network.getValue(Pos(0, 0, 0)).stress, 1e-4f) // the press at half speed
        r = solve(1f)
        assertTrue(r.network.getValue(Pos(0, 0, 0)).overloaded)
    }

    @Test fun recipeTimeFollowsSpeed() {
        assertEquals(1f, KineticNetwork.durationFactor(base), 0f)
        assertEquals(.5f, KineticNetwork.durationFactor(-2 * base), 0f)
        assertEquals(4f, KineticNetwork.durationFactor(1f), 0f)
    }
}
