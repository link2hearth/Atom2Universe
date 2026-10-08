package com.Atom2Universe.app.games.cosmorun

import com.Atom2Universe.app.games.cosmorun.CosmoRunGame.Entity
import com.Atom2Universe.app.games.cosmorun.CosmoRunGame.EntityType
import com.Atom2Universe.app.games.cosmorun.CosmoRunGame.Event
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Les règles une par une, sur des pistes construites à la main (chaque test est un bug réel trouvé en relecture). */
class CosmoRunGameTest {
    private fun newGame(): Pair<CosmoRunGame, MutableList<Event>> {
        val game = CosmoRunGame(Random(1))
        val events = ArrayList<Event>()
        game.onEvent = { e, _ -> events.add(e) }
        game.start()
        game.entities.clear()
        return game to events
    }

    private fun CosmoRunGame.run(seconds: Float) {
        repeat((seconds * 60).toInt()) { update(1f / 60f) }
    }

    @Test fun sidewaysBrushBouncesBackWithoutAHit() {
        val (game, events) = newGame()
        // Un long conteneur dans la voie 3 ; le joueur (voie 2) glisse deux fois vers la droite.
        game.entities += Entity(EntityType.CONTAINER, 3, -8f, 40f)
        game.moveRight(); game.moveRight()
        game.run(1f)
        assertFalse("un frôlement n'est pas un choc: $events", Event.STUMBLE in events || Event.CRASH in events)
        assertTrue(Event.SCRAPE in events)
        assertTrue("renvoyé du côté d'où il vient, pas dans le conteneur", game.laneTarget <= 2)
    }

    @Test fun headOnContainerPushesThePlayerAsideAndStaysStanding() {
        val (game, events) = newGame()
        val wall = Entity(EntityType.CONTAINER, 2, 8f, 30f)
        game.entities += wall
        game.run(1f)
        assertTrue(Event.STUMBLE in events)
        assertFalse("le mur reste debout", wall.dead)
        assertTrue("poussé vers une voie libre", game.laneTarget != 2)
        assertTrue("le Gardien est là", game.wardenTime > 0f)
        game.run(1.2f)
        assertEquals("un seul choc, pas de mort", 1, events.count { it == Event.STUMBLE })
        assertFalse(game.isGameOver)
    }

    @Test fun aSecondHitWhileTheWardenIsHereEndsTheRun() {
        val (game, events) = newGame()
        game.entities += Entity(EntityType.HURDLE, 2, 6f, .45f)
        game.entities += Entity(EntityType.HURDLE, 2, 16f, .45f)
        game.run(1.6f)
        assertTrue(Event.STUMBLE in events)
        assertTrue(Event.CRASH in events)
        assertTrue(game.isGameOver)
    }

    @Test fun aShieldCancelsOneHit() {
        val (game, events) = newGame()
        game.entities += Entity(EntityType.SHIELD, 2, 1f, .5f)
        game.entities += Entity(EntityType.HURDLE, 2, 10f, .45f)
        game.run(1.2f)
        assertTrue(Event.SHIELD in events)
        assertTrue(Event.SHIELD_BREAK in events)
        assertFalse("pas de trébuchement", Event.STUMBLE in events)
        assertEquals(0f, game.wardenTime, 0f)
    }

    @Test fun onlyOverdriveCountsAsSmashingForMissions() {
        val (game, _) = newGame()
        game.entities += Entity(EntityType.HURDLE, 2, 6f, .45f)
        game.run(1f)
        assertEquals("se cogner n'est pas fracasser", 0, game.smashes)
    }

    @Test fun smashingAContainerTakesItsRoofWithIt() {
        val (game, _) = newGame()
        val roofAtom = Entity(EntityType.ATOM, 2, 12f, .4f, CosmoRunGame.CONTAINER_HEIGHT)
        val roofHurdle = Entity(EntityType.HURDLE, 2, 14f, .45f, CosmoRunGame.CONTAINER_HEIGHT)
        game.entities += Entity(EntityType.BOOST, 2, 1f, .5f)
        game.entities += Entity(EntityType.CONTAINER, 2, 8f, 12f)
        game.entities += roofAtom; game.entities += roofHurdle
        game.run(1f)
        assertTrue(game.boostTime > 0f)
        assertTrue("le conteneur fracassé a perdu son toit", roofAtom.dead && roofHurdle.dead)
        assertTrue(game.smashes >= 1)
    }

    @Test fun overdriveEndsWithAGraceInsteadOfAnInstantHit() {
        val (game, _) = newGame()
        game.entities += Entity(EntityType.BOOST, 2, 1f, .5f)
        game.run(1f)
        assertTrue(game.boostTime > 0f)
        var grace = false
        repeat(60 * 8) {
            game.update(1f / 60f)
            if (game.boostTime == 0f && game.invincibleTime > 0f) grace = true
        }
        assertTrue("une courte protection suit la fin de la surrégime", grace)
    }

    @Test fun walkingUnderAGateSidewaysWithoutSlidingIsAHit() {
        val (game, events) = newGame()
        game.entities += Entity(EntityType.LASER, 3, 1.1f, .3f)
        game.moveRight()
        game.run(.5f)
        assertTrue("entrer de côté sous un portique sans se baisser touche : $events", Event.STUMBLE in events)
        assertFalse(Event.CLEAR in events)
    }

    @Test fun slidingUnderAGateClearsItAndScoresFlux() {
        val (game, events) = newGame()
        game.entities += Entity(EntityType.LASER, 2, 4f, .3f)
        game.run(.15f)
        game.slide()
        game.run(.6f)
        assertTrue(Event.CLEAR in events)
        assertFalse(Event.STUMBLE in events)
        assertEquals(1, game.chain)
    }

    @Test fun aRampCarriesThePlayerToTheRoofAndTheCameraFollows() {
        val (game, _) = newGame()
        game.entities += Entity(EntityType.RAMP, 2, 6f, 8f)
        game.entities += Entity(EntityType.CONTAINER, 2, 14f, 40f)
        game.run(1.4f)
        assertTrue("sur le toit : ${game.playerY}", game.playerY > 2.3f && game.onRoof)
        game.run(.6f)
        val lift = game.cameraLift
        assertTrue("la caméra monte : $lift", lift > 1f)
        // Sauter depuis le toit ne fait pas redescendre la caméra.
        game.jump()
        var lowest = lift
        repeat(30) { game.update(1f / 60f); lowest = minOf(lowest, game.cameraLift) }
        assertTrue("caméra à $lowest après le saut, était à $lift", lowest >= lift - .05f)
    }

    @Test fun leavingARoofEdgeStillAllowsALateJump() {
        val (game, _) = newGame()
        game.entities += Entity(EntityType.RAMP, 2, 6f, 8f)
        game.entities += Entity(EntityType.CONTAINER, 2, 14f, 14f)
        game.entities += Entity(EntityType.CONTAINER, 2, 34f, 20f)
        // On court sur le toit jusqu'au bord puis on saute juste après l'avoir quitté (coyote).
        var jumped = false
        repeat(60 * 4) {
            game.update(1f / 60f)
            if (!jumped && game.onRoof.not() && game.playerY > 2.3f) { game.jump(); jumped = true }
        }
        assertTrue(jumped)
        assertFalse("le saut de coyote passe la brèche", game.isGameOver)
    }

    @Test fun hangarKeepsThePerRunMissionProgress() {
        val game = CosmoRunGame(Random(1))
        game.missions.report(CosmoRunMissions.Kind.DISTANCE, 500f)
        val before = game.missions.missions.map { it.progress }
        game.enterHangar()
        assertEquals(before, game.missions.missions.map { it.progress })
        game.start()
        assertTrue("une vraie course repart de zéro", game.missions.missions.filter { it.kind.perRun }.all { it.progress == 0f })
    }

    @Test fun sameSeedGivesTheSameTrack() {
        fun layout(seed: Int): List<String> {
            val g = CosmoRunGame(Random(seed)); g.start()
            return g.entities.map { "${it.type}${it.lane}@${"%.2f".format(it.z)}" }
        }
        assertEquals(layout(5), layout(5))
        assertTrue(layout(5) != layout(6))
    }

    @Test fun graceNeverLocksThePlayerInsideAContainer() {
        val (game, events) = newGame()
        // Une barrière, puis un conteneur 0,15 s derrière : la grâce du choc ne doit pas mener à une mort.
        game.entities += Entity(EntityType.HURDLE, 2, 6f, .45f)
        game.entities += Entity(EntityType.CONTAINER, 2, 8.6f, 20f)
        game.run(3f)
        assertFalse("mort injuste : $events", Event.CRASH in events)
        assertFalse(game.isGameOver)
        assertTrue("repoussé hors de la voie du conteneur", game.laneTarget != 2)
    }

    @Test fun aPushNeverGoesTowardAContainerAboutToArrive() {
        val (game, events) = newGame()
        game.entities += Entity(EntityType.CONTAINER, 2, 8f, 30f)          // de face
        game.entities += Entity(EntityType.CONTAINER, 1, 10.5f, 30f)       // voisine gauche : arrive 2,5 m derrière
        game.run(2f)
        assertFalse("mort injuste : $events", Event.CRASH in events)
        assertEquals("la seule voie libre est la droite", 3, game.laneTarget)
    }

    @Test fun aMeteorIsOnlySolidOnceLanded() {
        val (game, events) = newGame()
        val meteor = Entity(EntityType.METEOR, 2, 30f, .6f)
        game.entities += meteor
        game.run(.5f)
        // À plus de 20 m, il n'a pas encore touché le sol : rien ne blesse, même dans sa voie.
        assertFalse(meteor.landed)
        // On saute quand il arrive à ~5,5 m (à cette vitesse, le milieu du saut passe dessus).
        var ticks = 0
        while (meteor.z > 5.5f && ticks++ < 600) game.update(1f / 60f)
        game.jump()
        game.run(1.5f)
        assertFalse("sauté à temps : $events", Event.STUMBLE in events)
        assertTrue(Event.METEOR in events)
    }
}
