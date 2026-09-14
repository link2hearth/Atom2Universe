package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.ai.LineOfSight
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.PathFinder
import com.Atom2Universe.app.games.caves.ai.PlayerSnapshot
import com.Atom2Universe.app.games.caves.ai.ShotSink
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.ai.Soldier
import com.Atom2Universe.app.games.caves.ai.SoldierTuning
import com.Atom2Universe.app.games.caves.ai.SoldierDecision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

class SoldierTest {

    /** Monde de test : un sol plein en y = 0, plus les blocs listés. */
    private class TestWorld(val sizeX: Int, val sizeY: Int, val sizeZ: Int) : SolidGrid {
        private val blocks = HashSet<Triple<Int, Int, Int>>()
        fun wall(x0: Int, x1: Int, z0: Int, z1: Int, height: Int) {
            for (x in x0..x1) for (z in z0..z1) for (y in 1..height) blocks.add(Triple(x, y, z))
        }
        override fun isSolid(x: Int, y: Int, z: Int) = y == 0 || Triple(x, y, z) in blocks
    }

    private class Shots : ShotSink {
        var count = 0
        override fun fire(x: Double, y: Double, z: Double, dx: Double, dy: Double, dz: Double) { count++ }
    }

    private fun soldier(world: TestWorld, x: Double, z: Double, tuning: SoldierTuning = SoldierTuning()): Soldier {
        val grid = NavGrid.build(world.sizeX, world.sizeY, world.sizeZ, world)
        return Soldier(grid, world, PathFinder(grid), Random(7), tuning).also { it.place(x, 1.0, z) }
    }

    /** Joueur debout : pieds sur le sol (y = 1), yeux 1,62 bloc plus haut. */
    private fun player(x: Double, z: Double, alive: Boolean = true) = PlayerSnapshot().also {
        it.x = x; it.eyeY = 2.62; it.z = z; it.alive = alive
    }

    private fun run(s: Soldier, p: PlayerSnapshot, seconds: Float, shots: ShotSink = Shots()) {
        val dt = 0.05f
        repeat((seconds / dt).toInt()) { s.update(dt, p, shots) }
    }

    @Test
    fun `il voit devant lui, mais ni dans son dos ni derriere un mur`() {
        val open = TestWorld(30, 6, 30)
        val front = soldier(open, 5.5, 5.5)          // regard initial vers +Z
        front.update(0.05f, player(5.5, 20.5), Shots())
        assertTrue(front.seesPlayer)

        val back = soldier(open, 5.5, 20.5)
        back.update(0.05f, player(5.5, 5.5), Shots())  // le joueur est derrière lui
        assertFalse(back.seesPlayer)

        val walled = TestWorld(30, 6, 30).also { it.wall(0, 29, 10, 10, 2) }
        val blind = soldier(walled, 5.5, 5.5)
        blind.update(0.05f, player(5.5, 20.5), Shots())
        assertFalse(blind.seesPlayer)
    }

    @Test
    fun `il attend son temps de reaction avant de tirer`() {
        val s = soldier(TestWorld(30, 6, 30), 5.5, 5.5, SoldierTuning(reactionMin = 0.4f, reactionMax = 0.4f))
        val shots = Shots()
        val p = player(5.5, 15.5)
        repeat(6) { s.update(0.05f, p, shots) }       // 0,30 s
        assertEquals(0, shots.count)
        repeat(6) { s.update(0.05f, p, shots) }       // 0,60 s
        assertTrue(shots.count >= 1)
        assertEquals(Soldier.State.ENGAGE, s.state)
    }

    @Test
    fun `il va voir la derniere position connue, pas la vraie`() {
        val s = soldier(TestWorld(40, 6, 40), 5.5, 5.5)
        s.update(0.05f, player(5.5, 15.5), Shots())
        assertTrue(s.knowsPlayer)

        // Le joueur disparaît (hors de vue) et file loin : le soldat ne le sait pas.
        val hidden = player(35.5, 35.5, alive = false)
        run(s, hidden, 4f)
        assertEquals(Soldier.State.SEARCH, s.state)
        assertTrue("il doit être là où il l'a vu", hypot(s.x - 5.5, s.z - 15.5) < 1.5)
    }

    @Test
    fun `il finit par oublier et repart en patrouille`() {
        val s = soldier(TestWorld(40, 6, 40), 5.5, 5.5, SoldierTuning(memorySeconds = 3f))
        s.update(0.05f, player(5.5, 15.5), Shots())
        run(s, player(35.5, 35.5, alive = false), 4f)
        assertFalse(s.knowsPlayer)
        assertEquals(Soldier.State.PATROL, s.state)
    }

    @Test
    fun `il entend un tir et va voir d ou il vient`() {
        val s = soldier(TestWorld(40, 6, 40), 5.5, 5.5)
        val nobody = player(30.5, 5.5, alive = false)
        s.hearShot(30.5, 2.62, 5.5)
        s.update(0.05f, nobody, Shots())
        assertEquals(Soldier.State.SEARCH, s.state)
        run(s, nobody, 8f)
        assertTrue(hypot(s.x - 30.5, s.z - 5.5) < 1.5)

        val deaf = soldier(TestWorld(80, 6, 40), 5.5, 5.5, SoldierTuning(hearingRange = 35.0))
        deaf.hearShot(70.5, 2.62, 5.5)                 // 65 blocs : trop loin pour l'entendre
        assertFalse(deaf.knowsPlayer)
    }

    @Test
    fun `un tir ou un impact lointain declenche la riposte mais pas a travers un mur`() {
        val world = TestWorld(12, 6, 150)
        val p = player(5.5, 5.5)
        for (hit in listOf(false, true)) {
            val s = soldier(world, 5.5, 140.5)
            val shots = Shots()
            if (hit) s.onDamaged(p.x, p.eyeY, p.z) else s.hearShot(p.x, p.eyeY, p.z)
            run(s, p, 1.5f, shots)
            assertTrue("riposte à 135 blocs", shots.count > 0)
        }
        world.wall(0, 11, 75, 75, 3)
        val hidden = soldier(world, 5.5, 140.5)
        val shots = Shots()
        hidden.hearShot(p.x, p.eyeY, p.z)
        run(hidden, p, 1f, shots)
        assertFalse(hidden.seesPlayer)
        assertEquals(0, shots.count)
        assertEquals(Soldier.State.SEARCH, hidden.state)
    }

    @Test
    fun `la visee converge sur une cible mobile et anticipe sa course`() {
        val s = soldier(TestWorld(100, 6, 100), 40.5, 5.5,
            SoldierTuning(magazineSize = 100))
        val p = player(40.5, 55.5).also { it.velX = 4.0 }
        var hits = 0
        var total = 0
        var elapsed = 0f
        val shots = ShotSink { x, y, z, dx, dy, dz ->
            if (elapsed >= 2f) {
                val travel = (p.z - z) / dz
                val seconds = travel / s.tuning.bulletSpeed
                val futureX = p.x + p.velX * seconds
                val hitY = y + dy * travel
                total++
                if (kotlin.math.abs(x + dx * travel - futureX) <= 0.30 &&
                    hitY in (p.eyeY - 1.62)..(p.eyeY + 0.18)) hits++
            }
        }
        repeat(120) {
            elapsed += 0.05f
            p.x += p.velX * 0.05
            s.update(0.05f, p, shots)
        }
        assertTrue("la course ne fait pas diverger la visée", s.aimErrorDeg < 0.5f)
        assertTrue(total >= 5)
        assertTrue("au moins un tiers des tirs stabilisés touchent le corps", hits * 3 >= total)
    }

    @Test
    fun `chargeur vide, il court se mettre a l abri pour recharger`() {
        // Muret de 2 blocs juste derrière le soldat ; le joueur est devant, en pleine vue.
        val world = TestWorld(30, 6, 40).also { it.wall(6, 14, 10, 10, 2) }
        val tuning = SoldierTuning(magazineSize = 2, reactionMin = 0f, reactionMax = 0f,
            fireInterval = 0.1f, reloadSeconds = 5f)
        val s = soldier(world, 10.5, 12.5, tuning)
        val p = player(10.5, 28.5)
        val shots = Shots()
        run(s, p, 0.5f, shots)
        assertEquals(2, shots.count)
        assertEquals(Soldier.State.RELOAD, s.state)

        run(s, p, 4f, shots)
        assertEquals("pas de tir pendant le rechargement", 2, shots.count)
        assertTrue("il doit être passé derrière le muret", s.z < 10.0)
        assertFalse(LineOfSight.isClear(p.x, p.eyeY, p.z, s.x, s.y + 1.62, s.z, world))

        run(s, p, 4f, shots)
        assertTrue("rechargement terminé après le trajet et l'attente", s.ammo > 0 || shots.count > 2)
    }

    @Test
    fun `les scores privilegient le repli apres blessure puis autorisent la riposte`() {
        assertEquals(Soldier.State.COVER, SoldierDecision.choose(true, true, .4f, true, true))
        assertEquals(Soldier.State.ENGAGE, SoldierDecision.choose(true, true, .4f, true, false))
        assertEquals(Soldier.State.ENGAGE, SoldierDecision.choose(true, true, .4f, false, true))
        assertEquals(Soldier.State.SEARCH, SoldierDecision.choose(false, true, 1f, false, true))
        assertEquals(Soldier.State.PATROL, SoldierDecision.choose(false, false, .1f, false, true))
    }

    @Test
    fun `blesse il gagne un abri sans tirer et finit par ressortir`() {
        val world = TestWorld(30, 6, 40).also { it.wall(6, 14, 10, 10, 2) }
        val s = soldier(world, 10.5, 12.5)
        val p = player(10.5, 28.5)
        s.healthFraction = .4f
        s.onDamaged(p.x, p.eyeY, p.z)
        val shots = Shots()
        s.update(.05f, p, shots)
        assertEquals(Soldier.State.COVER, s.state)
        var reachedCover = false
        repeat(120) {
            if (s.state == Soldier.State.COVER && s.follower.arrived) reachedCover = true
            val before = shots.count
            val covering = s.state == Soldier.State.COVER
            s.update(.05f, p, shots)
            if (covering && s.state == Soldier.State.COVER) assertEquals(before, shots.count)
        }
        assertTrue(reachedCover)
        assertTrue(s.state != Soldier.State.COVER)
    }

    @Test
    fun `sans abri le soldat blesse riposte au lieu de rester bloque`() {
        val s = soldier(TestWorld(30, 6, 30), 5.5, 5.5)
        val p = player(5.5, 20.5)
        s.healthFraction = .3f
        s.onDamaged(p.x, p.eyeY, p.z)
        val shots = Shots()
        run(s, p, 1.5f, shots)
        assertEquals(Soldier.State.ENGAGE, s.state)
        assertTrue(shots.count > 0)
    }

    @Test
    fun `un soldat expose change de position en gardant le contact`() {
        val s = soldier(TestWorld(30, 6, 40), 15.5, 5.5,
            SoldierTuning(magazineSize = 100, repositionSeconds = 1f))
        val p = player(15.5, 30.5)
        val shots = Shots()
        run(s, p, 2f, shots)
        assertTrue(kotlin.math.abs(s.x - 15.5) > .5)
        assertTrue(s.seesPlayer)
        assertTrue(shots.count > 0)
    }
}
