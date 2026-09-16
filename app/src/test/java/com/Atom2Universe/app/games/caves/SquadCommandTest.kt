package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.ai.Squad
import com.Atom2Universe.app.games.caves.ai.SquadCommand
import com.Atom2Universe.app.games.caves.ai.SquadMember
import com.Atom2Universe.app.games.caves.ai.SquadTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/**
 * L'état-major : une seule escouade sur le joueur à la fois, qui arrive groupée et l'encercle.
 *
 * Les soldats sont ici des pantins ([Puppet]) : on vérifie les *ordres* donnés, pas la marche.
 * [march] les téléporte à leur poste, ce qui simule un trajet réussi.
 */
class SquadCommandTest {

    /** Terrain dégagé : un sol plein en y = 0, donc des cases praticables partout en y = 1. */
    private val world = SolidGrid { _, y, _ -> y == 0 }
    private val grid = NavGrid.build(SIZE, 6, SIZE, world)

    private class Puppet(var px: Double, var pz: Double) : SquadMember {
        override var alive = true
        override val x: Double get() = px
        override val y: Double get() = 1.0
        override val z: Double get() = pz
        override var seesTarget = false
        override var shaken = false

        var orderedNode = -1
        var strict = false
        var radioX = Double.NaN
        var radioZ = Double.NaN
        var leashX = 0.0
        var leashZ = 0.0
        var leashRadius = Double.POSITIVE_INFINITY

        override fun order(node: Int, strict: Boolean) {
            orderedNode = node; this.strict = strict
        }

        override fun radioContact(x: Double, z: Double) { radioX = x; radioZ = z }

        override fun leashTo(x: Double, z: Double, radius: Double) {
            leashX = x; leashZ = z; leashRadius = radius
        }
    }

    private fun command(tuning: SquadTuning = SquadTuning()) =
        SquadCommand(grid, world, Random(11), tuning)

    /** Une escouade de quatre hommes serrés autour de (x, z). */
    private fun enlist(cmd: SquadCommand, x: Double, z: Double): Squad {
        val members = List(4) { Puppet(x + it % 2, z + it / 2) }
        return cmd.enlist(members)
    }

    private fun puppets(squad: Squad) = squad.members.map { it as Puppet }

    /** Le trajet est supposé réussi : chacun se retrouve sur la case qu'on lui a donnée. */
    private fun march(squad: Squad) {
        for (p in puppets(squad)) {
            val n = p.orderedNode
            if (n < 0) continue
            p.px = grid.nodeX[n] + .5
            p.pz = grid.nodeZ[n] + .5
        }
    }

    private fun run(cmd: SquadCommand, seconds: Float, px: Double = MID, pz: Double = MID) {
        repeat((seconds / DT).toInt()) { cmd.update(DT, px, 1.0, pz) }
    }

    /** L'escouade qui a quitté la réserve, s'il y en a une. */
    private fun committed(cmd: SquadCommand) = cmd.all.filter { it.stance != Squad.Stance.HOLD }

    @Test
    fun `une seule escouade a la fois quitte sa reserve`() {
        val cmd = command()
        enlist(cmd, 60.0, 10.0); enlist(cmd, 10.0, 60.0); enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 60f)   // bien après le délai d'alerte et l'expiration du regroupement
        assertEquals(1, committed(cmd).size)
        assertEquals(cmd.active, committed(cmd).first())
    }

    @Test
    fun `les reserves tiennent leur secteur et regardent vers le joueur annonce`() {
        val cmd = command()
        val far = enlist(cmd, 10.0, 60.0)
        enlist(cmd, 60.0, 10.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 3f)
        val reserve = if (cmd.active === far) cmd.all.first { it !== far } else far
        for (p in puppets(reserve)) {
            assertEquals("laisse du secteur", cmd.tuning.holdRadius, p.leashRadius, 1e-6)
            assertEquals(-1, p.orderedNode)
            assertTrue("la position annoncée doit circuler", !p.radioX.isNaN())
            assertTrue("annonce grossière mais juste",
                hypot(p.radioX - MID, p.radioZ - MID) <= cmd.tuning.radioBlur * 1.5)
        }
    }

    @Test
    fun `l'escouade se regroupe a couvert avant de se montrer`() {
        val cmd = command()
        val squad = enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 6f)
        assertEquals(Squad.Stance.RALLY, squad.stance)
        // Le point de ralliement est loin du joueur : on ne « se regroupe » pas sur sa tête.
        // (L'éclaireur, lui, est déjà parti devant : voir le test qui lui est consacré.)
        val scout = squad.roles.indexOf(Squad.Role.POINT)
        for ((i, p) in puppets(squad).withIndex()) {
            if (i == scout) continue
            val n = p.orderedNode
            assertTrue("poste de regroupement manquant", n >= 0)
            val d = distanceToPlayer(n)
            assertTrue("regroupement à $d blocs du joueur", d > cmd.tuning.engageRadius * 1.4)
            assertTrue("un ordre de regroupement se tient", p.strict)
        }
        // Une fois tout le monde en place, et alors seulement, l'assaut part.
        march(squad)
        run(cmd, .2f)
        assertEquals(Squad.Stance.ASSAULT, squad.stance)
    }

    @Test
    fun `sous le feu pendant le regroupement, elle attaque sans attendre les trainards`() {
        val cmd = command()
        val squad = enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 6f)
        assertEquals(Squad.Stance.RALLY, squad.stance)
        // Attendre bien rangé pendant qu'on vous tire dessus n'a aucun sens.
        puppets(squad).first().shaken = true
        run(cmd, .2f)
        assertEquals(Squad.Stance.ASSAULT, squad.stance)
    }

    @Test
    fun `l'eclaireur qui trouve le joueur laisse peu de temps au reste du groupe`() {
        val cmd = command()
        val squad = enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 6f)   // le temps que l'alerte parte et que l'escouade soit activée
        assertEquals(Squad.Stance.RALLY, squad.stance)
        val scout = squad.roles.indexOf(Squad.Role.POINT)
        assertTrue("une escouade doit avoir une pointe", scout >= 0)
        puppets(squad)[scout].seesTarget = true

        // Vu n'est pas visé : le groupe garde un court délai pour se placer, il ne fonce pas.
        run(cmd, 2f)
        assertEquals(Squad.Stance.RALLY, squad.stance)
        run(cmd, cmd.tuning.contactRallySeconds)
        assertEquals(Squad.Stance.ASSAULT, squad.stance)
    }

    @Test
    fun `a l'assaut, les postes encerclent le joueur au lieu de se suivre`() {
        val cmd = command()
        val squad = assaulting(cmd)
        // Les contourneurs passent d'abord par leur point de passage : on les y amène.
        march(squad)
        run(cmd, .2f)
        march(squad)

        val bearings = puppets(squad).map { p ->
            val n = p.orderedNode
            assertTrue("poste d'assaut manquant", n >= 0)
            val d = distanceToPlayer(n)
            assertTrue("poste de tir à $d blocs", d in 6.0..20.0)
            assertFalse("un ordre d'assaut laisse réagir à ce qu'on entend", p.strict)
            bearingOfNode(n)
        }
        // Une file indienne, ce sont quatre hommes dans le même azimut. On veut l'inverse.
        for (i in bearings.indices) for (j in i + 1 until bearings.size) {
            val gap = abs(normalize(bearings[i] - bearings[j]))
            assertTrue("postes à $gap° l'un de l'autre", gap > 30.0)
        }
    }

    @Test
    fun `l'eclaireur part devant pendant que les autres se regroupent`() {
        val cmd = command()
        val squad = enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 6f)
        assertEquals(Squad.Stance.RALLY, squad.stance)

        val scout = squad.roles.indexOf(Squad.Role.POINT)
        assertTrue("une escouade doit avoir une pointe", scout >= 0)
        val scoutDistance = distanceToPlayer(puppets(squad)[scout].orderedNode)
        assertFalse("l'éclaireur n'est pas tenu au regroupement", puppets(squad)[scout].strict)
        for ((i, p) in puppets(squad).withIndex()) {
            if (i == scout) continue
            assertTrue("les autres se regroupent en arrière",
                distanceToPlayer(p.orderedNode) > scoutDistance + 5.0)
            assertTrue(p.strict)
        }
    }

    @Test
    fun `le contournement passe large et derriere avant de se rabattre`() {
        val cmd = command()
        val squad = assaulting(cmd)
        val flankers = squad.roles.indices.filter { squad.roles[it] == Squad.Role.FLANK }
        assertTrue("une escouade de quatre doit contourner", flankers.isNotEmpty())

        val detours = flankers.map { puppets(squad)[it].orderedNode }
        for (n in detours) {
            assertTrue("point de passage manquant", n >= 0)
            assertTrue("le détour doit passer large (${distanceToPlayer(n)})",
                distanceToPlayer(n) > cmd.tuning.engageRadius * 1.3)
        }

        // Arrivés au point de passage, ils se rabattent sur un poste plus proche et d'un autre angle.
        march(squad)
        run(cmd, .2f)
        for ((rank, i) in flankers.withIndex()) {
            val post = puppets(squad)[i].orderedNode
            assertTrue("poste de rabattement manquant", post >= 0)
            assertTrue("le poste doit être plus près que le détour",
                distanceToPlayer(post) < distanceToPlayer(detours[rank]))
            val turn = abs(normalize(bearingOfNode(post) - bearingOfNode(detours[rank])))
            assertTrue("le rabattement doit changer d'angle ($turn°)", turn > 25.0)
        }
    }

    @Test
    fun `une fois reperee, l'escouade cesse de contourner`() {
        val cmd = command()
        val squad = assaulting(cmd)
        val flanker = squad.roles.indices.first { squad.roles[it] == Squad.Role.FLANK }
        val detour = puppets(squad)[flanker].orderedNode

        // Un des leurs a le joueur en vue : la discrétion n'a plus d'objet, tout le monde se
        // recale sur sa position réelle et personne ne perd de temps à faire le tour.
        puppets(squad).first().seesTarget = true
        run(cmd, 2.5f)
        val direct = puppets(squad)[flanker].orderedNode
        assertNotEquals("il doit abandonner son détour", detour, direct)
        assertTrue("et se rapprocher",
            distanceToPlayer(direct) < distanceToPlayer(detour))
    }

    @Test
    fun `la perte de l'eclaireur ne laisse pas l'escouade sans pointe`() {
        val cmd = command()
        val squad = assaulting(cmd)
        val scout = squad.roles.indexOf(Squad.Role.POINT)
        puppets(squad)[scout].alive = false

        // Le joueur se déplace franchement : l'escouade refait son plan, et redistribue ses rôles.
        repeat(80) { cmd.update(DT, 20.0, 1.0, 20.0) }
        val newScout = squad.roles.indexOfFirst { it == Squad.Role.POINT }
        assertTrue("quelqu'un doit reprendre la pointe", newScout >= 0)
        assertTrue("et il doit être debout", squad.members[newScout].alive)
    }

    @Test
    fun `la releve ne part qu'apres un souffle, et par un autre cote`() {
        val cmd = command()
        enlist(cmd, 62.0, 10.0); enlist(cmd, 10.0, 62.0); enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 6f)
        val first = cmd.active!!
        val firstBearing = bearingOf(first)
        puppets(first).forEach { it.alive = false }

        run(cmd, cmd.tuning.wipedPauseSeconds - 2f)
        assertNull("le joueur doit avoir son moment de répit", cmd.active)
        run(cmd, 3f)
        val second = cmd.active
        assertNotEquals(null, second)
        assertNotEquals(first.id, second!!.id)
        assertTrue("la relève arrive debout", second.living > 0)
        // À distance comparable, on préfère l'escouade qui n'arrive pas par le même couloir.
        assertTrue("relève par le même azimut",
            abs(normalize(bearingOf(second) - firstBearing)) > 30.0)
    }

    @Test
    fun `le dernier survivant ne suffit plus a faire une escouade`() {
        val cmd = command()
        enlist(cmd, 62.0, 10.0); enlist(cmd, 10.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 6f)
        val first = cmd.active!!
        march(first)
        run(cmd, .2f)
        assertEquals(Squad.Stance.ASSAULT, first.stance)

        puppets(first).drop(1).forEach { it.alive = false }
        run(cmd, cmd.tuning.reliefSeconds + 1f)
        assertNotEquals("la relève doit prendre la main", first.id, cmd.active?.id)
        // Le rescapé n'est pas rappelé pour autant : il finit son assaut.
        assertEquals(Squad.Stance.ASSAULT, first.stance)
    }

    @Test
    fun `la position annoncee est floue et n'est pas rafraichie a chaque image`() {
        val cmd = command(SquadTuning(radioIntervalSeconds = 2f, radioBlur = 3.0))
        val squad = enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        val listener = puppets(squad).first()

        // Le joueur file à l'autre bout : tant que l'annonce n'est pas repassée, rien ne bouge.
        run(cmd, 1f, px = 10.0, pz = 10.0)
        assertEquals(MID, listener.radioX, 1e-6)
        run(cmd, 1.5f, px = 10.0, pz = 10.0)
        assertNotEquals(MID, listener.radioX, 1e-6)
        val error = hypot(listener.radioX - 10.0, listener.radioZ - 10.0)
        assertTrue("annonce au bloc près : trop précis ($error)", error > 0.0)
        assertTrue("annonce délirante ($error)", error <= 3.0 * 1.5)
    }

    /** Une escouade activée, regroupée, et passée à l'assaut. */
    private fun assaulting(cmd: SquadCommand): Squad {
        val squad = enlist(cmd, 62.0, 62.0)
        cmd.start(MID, 1.0, MID)
        run(cmd, 6f)
        march(squad)
        run(cmd, .2f)
        assertEquals(Squad.Stance.ASSAULT, squad.stance)
        return squad
    }

    private fun distanceToPlayer(node: Int) =
        hypot(grid.nodeX[node] + .5 - MID, grid.nodeZ[node] + .5 - MID)

    private fun bearingOfNode(node: Int) =
        Math.toDegrees(atan2(grid.nodeX[node] + .5 - MID, grid.nodeZ[node] + .5 - MID))

    private fun bearingOf(squad: Squad) =
        Math.toDegrees(atan2(squad.anchorX - MID, squad.anchorZ - MID))

    private companion object {
        const val SIZE = 80
        const val MID = 40.5
        const val DT = .05f

        fun normalize(a: Double): Double {
            var v = a % 360.0
            if (v > 180.0) v -= 360.0
            if (v < -180.0) v += 360.0
            return v
        }
    }
}
