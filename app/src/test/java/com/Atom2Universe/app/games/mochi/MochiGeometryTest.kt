package com.Atom2Universe.app.games.mochi

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

/** Intégration des murs avec les vraies réceptions, le score, le guide et les jardins. */
class MochiGeometryTest {
    private val bankWalls = listOf(
        MochiWall(700, 290f, 115f, 310f, 600f, MochiWall.Material.RUBBER),
        MochiWall(701, 130f, 180f, 220f, 200f)
    )

    private fun bankGarden(): MochiGame = MochiGame(31).also { g ->
        g.flowers.clear()
        g.flowers += MochiGame.Flower(0, 210f, MochiGame.START_Y, visited = true)
        // À t=.9 : après un rebond à x=273, vx passe de 300 à -282.
        g.flowers += MochiGame.Flower(1, 78.42f, 297.2f)
        g.hazards.clear()
        g.sweets.clear()
        g.walls.clear()
        g.walls += bankWalls
    }

    private fun step(g: MochiGame) {
        g.update(MochiGame.STEP)
        // Isoler la situation conçue des prochaines sections créées à l'horizon.
        g.flowers.removeAll { it.id !in 0..1 }
        g.hazards.clear()
        g.sweets.clear()
        g.walls.removeAll { it.id !in 700..701 }
    }

    private fun shoot(g: MochiGame, vx: Float, vy: Float) {
        assertTrue(g.launch(-vx / (MochiGame.DRAG_POWER * g.launchPower),
            vy / (MochiGame.DRAG_POWER * g.launchPower)))
    }

    private fun finishFlight(g: MochiGame) {
        var ticks = 0
        while (g.phase == MochiGame.Phase.FLYING && ticks++ < 300) step(g)
        assertTrue("Un vol ne doit pas rester coincé dans un mur", ticks < 300)
    }

    @Test fun anOverhangBlocksTheDirectJumpButABankShotReachesTheFlower() {
        val direct = bankGarden()
        shoot(direct, (78.42f - direct.x) / .9f, 550f)
        finishFlight(direct)
        assertFalse("Le tir direct doit réellement rencontrer l'auvent", direct.flowers.first { it.id == 1 }.visited)

        val bank = bankGarden()
        shoot(bank, 300f, 550f)
        repeat(35) { step(bank) }
        assertTrue(bank.flightBounces > 0)
        assertEquals("Un rebond seul ne crée pas de points", 0, bank.score)
        finishFlight(bank)
        assertEquals(MochiGame.Phase.ATTACHED, bank.phase)
        assertEquals(1, bank.attachedId)
        assertEquals(1, bank.bankShots)
        assertTrue("La réception après ricochet doit être récompensée", bank.score > 16)

        val score = bank.score
        val edge = bankWalls.first().left - MochiGame.RADIUS
        val restitution = MochiWall.Material.RUBBER.restitution
        val returnVx = (1 + restitution) * (edge - bank.x) / restitution
        shoot(bank, returnVx, MochiGame.GRAVITY * .5f)
        finishFlight(bank)
        assertEquals(1, bank.attachedId)
        assertEquals("La même réception ne permet pas de farmer les ricochets", score, bank.score)
        assertEquals(1, bank.bankShots)
    }

    @Test fun theGuideMatchesTheRealFlightBeforeAndAfterAnInteriorBounce() {
        val g = bankGarden()
        val preview = g.preview(-300f / MochiGame.DRAG_POWER, 550f / MochiGame.DRAG_POWER)
        assertEquals(0f, g.time, 0f)
        assertEquals(0, g.score)
        assertEquals(0, g.flightBounces)
        assertEquals(MochiGame.Outcome.LAND, preview.outcome)
        assertEquals(1, preview.flowerId)
        assertTrue(preview.points.size >= 16)
        shoot(g, 300f, 550f)
        val every = MochiGame.PREVIEW_EVERY
        repeat(80) { tick ->
            step(g)
            if ((tick + 1) % every == 0) {
                val point = preview.points[(tick + 1) / every - 1]
                assertEquals("Guide x après ${tick + 1} pas", point.x, g.x, .002f)
                assertEquals("Guide y après ${tick + 1} pas", point.y, g.y, .002f)
            }
        }
        assertTrue(g.flightBounces > 0)
        assertTrue(g.vx < 0)
        assertEquals(MochiGame.Phase.FLYING, g.phase)
    }

    @Test fun obstacleTopsAlwaysBounceMochiOffInsteadOfTrappingHim() {
        val g = MochiGame(2)
        g.flowers.removeAll { it.id != 0 }
        g.hazards.clear(); g.sweets.clear(); g.walls.clear()
        // Mochi passe au-dessus du bord gauche et retombe sur le sommet plat.
        g.walls += MochiWall(777, 290f, 160f, 410f, 180f)
        shoot(g, 150f, 420f)
        var bounces = 0
        var ticks = 0
        var highestAfterFirstBounce = 0f
        while (g.phase == MochiGame.Phase.FLYING && ticks++ < 600) {
            g.update(MochiGame.STEP)
            g.flowers.removeAll { it.id != 0 }
            g.hazards.clear(); g.sweets.clear(); g.walls.removeAll { it.id != 777 }
            if (g.events.any { it.effect == MochiGame.Effect.BOUNCE }) bounces++
            if (bounces > 0) highestAfterFirstBounce = max(highestAfterFirstBounce, g.y)
            if (g.phase == MochiGame.Phase.FLYING) assertFalse("On ne peut jamais se relancer depuis les ronces", g.launch(0f, 100f))
        }
        assertTrue("Les rebonds sont conservés", bounces > 0)
        assertTrue("Un sommet plat repousse Mochi de côté au lieu de le garder", ticks < 600)
        assertTrue("Chaque rebond garde une vraie hauteur", highestAfterFirstBounce > 180f + MochiGame.RADIUS + 20f)
        assertTrue(g.phase == MochiGame.Phase.ATTACHED || g.end == MochiGame.End.FALL)
    }

    @Test fun aShelfBelowTheFallLineCannotTrapMochiOffScreen() {
        val g = MochiGame(2)
        g.flowers.removeAll { it.id != 0 }
        g.hazards.clear()
        g.sweets.clear()
        g.walls.clear()
        g.walls += MochiWall(777, 200f, -30f, 350f, -10f)
        shoot(g, 100f, 0f)
        repeat(90) { g.update(MochiGame.STEP) }
        assertEquals(MochiGame.Phase.OVER, g.phase)
        assertEquals(MochiGame.End.FALL, g.end)
    }

    @Test fun aBankShotIsIndependentOfTheDisplayRefreshRate() {
        val a = bankGarden()
        val b = bankGarden()
        shoot(a, 300f, 550f)
        shoot(b, 300f, 550f)
        repeat(40) { a.update(1f / 60f) }
        repeat(80) { b.update(1f / 120f) }
        assertTrue(a.flightBounces > 0)
        assertEquals(a.phase, b.phase)
        assertEquals(a.x, b.x, .002f)
        assertEquals(a.y, b.y, .002f)
        assertEquals(a.vx, b.vx, .002f)
        assertEquals(a.vy, b.vy, .002f)
        assertEquals(a.flightBounces, b.flightBounces)
        assertEquals(a.score, b.score)
    }

    @Test fun generatedGeometryVariesAndPreservesClearPerchesAndBankAlternatives() {
        val signatures = mutableSetOf<List<Int>>()
        var vertical = false
        var overhang = false
        var rubber = false
        var bankAlternative = false
        var branches = 0
        for (seed in listOf(0L, 13L, 42L)) {
            val generator = MochiGarden(seed)
            var previous = MochiGame.Flower(0, 210f, MochiGame.START_Y, visited = true)
            val flowers = arrayListOf(previous)
            val walls = arrayListOf<MochiWall>()
            repeat(24) {
                val section = generator.next(previous, walls, flowers)
                walls += section.walls
                flowers += section.next
                section.branch?.let { flowers += it; branches++ }
                for (wall in section.walls) {
                    assertTrue(wall.left >= 0f && wall.right <= MochiGame.WIDTH)
                    assertTrue(wall.right > wall.left && wall.top > wall.bottom)
                    val width = wall.right - wall.left
                    val height = wall.top - wall.bottom
                    vertical = vertical || height > width * 1.5f
                    overhang = overhang || width > height * 1.5f
                    rubber = rubber || wall.material == MochiWall.Material.RUBBER
                    signatures += listOf((wall.left / 5).roundToInt(), (width / 5).roundToInt(),
                        (height / 5).roundToInt(), ((wall.bottom - previous.y) / 5).roundToInt())
                }
                for (flower in flowers) for (phase in listOf(0f, .8f, 1.6f, 2.4f, 3.2f)) {
                    assertFalse("Une fleur ne doit pas naître dans un mur : seed=$seed fleur=${flower.id}",
                        walls.any { MochiFlight.overlaps(it, flower.at(phase), flower.y, MochiGame.RADIUS + .5f) })
                }
                if (!bankAlternative) section.branch?.let { branch ->
                    val shot = MochiGarden.solveLaunch(previous, branch, walls, 0f, flowers,
                        requireInteriorBounce = true)
                    bankAlternative = shot != null && shot.bounces > 0
                }
                previous = section.next
                flowers.removeAll { it.y < previous.y - 450f }
                walls.removeAll { it.top < previous.y - 450f }
            }
        }
        assertTrue("Le parcours doit contenir des parois verticales", vertical)
        assertTrue("Le parcours doit contenir des surplombs", overhang)
        assertTrue("Les surfaces de ricochet doivent être générées", rubber)
        assertTrue("Les géométries ne doivent pas être quelques copies déplacées", signatures.size >= 10)
        assertTrue("Le parcours doit réellement bifurquer", branches >= 3)
        assertTrue("Une branche générée doit être atteignable en exploitant un mur", bankAlternative)
    }
}
