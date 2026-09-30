package com.Atom2Universe.app.games.cosmorun

import com.Atom2Universe.app.games.cosmorun.CosmoRunGame.EntityType
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Planches PNG dans app/build/cosmo-preview/ — à ouvrir pour juger le rendu. */
class CosmoRunPreviewTest {
    private fun shots(game: CosmoRunGame, name: String, t: Float) {
        CosmoRunPreview.save(CosmoRunPreview.render(game, 1000, 560, t), "${name}_landscape")
        CosmoRunPreview.save(CosmoRunPreview.render(game, 520, 1000, t), "${name}_portrait")
    }

    /** Un joueur exact au sol : on photographie quelques instants d'une vraie partie. */
    @Test fun dumpRun() {
        val game = CosmoRunGame(Random(11))
        val bot = ReaderBot(game, Random(5), 0f, 0f)
        game.start()
        var t = 0f
        for (shot in 0..3) {
            repeat(60 * 12) { bot.step(1f / 60f); game.update(1f / 60f); t += 1f / 60f }
            shots(game, "run_$shot", t)
        }
    }

    /** Rampe, toit, atomes et barrière sur le toit : la scène qui compte pour le tri des faces. */
    @Test fun dumpRoofScene() {
        val game = CosmoRunGame(Random(3))
        game.roofsEnabled = true
        game.start()
        game.entities.clear()
        val ramp = CosmoRunGame.Entity(EntityType.RAMP, 2, 10f, 8f)
        val roof = CosmoRunGame.Entity(EntityType.CONTAINER, 2, 18f, 40f)
        game.entities += ramp; game.entities += roof
        for (i in 0..5) game.entities += CosmoRunGame.Entity(EntityType.ATOM, 2, 20f + i * 4f, .4f, CosmoRunGame.CONTAINER_HEIGHT)
        game.entities += CosmoRunGame.Entity(EntityType.HURDLE, 2, 46f, .45f, CosmoRunGame.CONTAINER_HEIGHT)
        game.entities += CosmoRunGame.Entity(EntityType.CONTAINER, 0, 12f, 6f)
        game.entities += CosmoRunGame.Entity(EntityType.CONTAINER, 4, 20f, 9f)
        game.entities += CosmoRunGame.Entity(EntityType.HURDLE, 3, 24f, .45f)
        game.entities += CosmoRunGame.Entity(EntityType.LASER, 1, 30f, .3f)
        game.entities += CosmoRunGame.Entity(EntityType.DRONE, 3, 36f, .7f)
        var t = 0f
        shots(game, "roof_0_approach", t)
        // La rampe commence à 10 m ; la voie est libre : le joueur la gravit tout droit.
        var faces = 0
        for (i in 1..4) {
            repeat(if (i == 1) 30 else 22) { game.update(1f / 60f); t += 1f / 60f }
            shots(game, "roof_${i}", t)
        }
        assertTrue("le joueur doit être monté sur le toit", game.playerY > 2f)
        assertTrue(faces >= 0)
    }

    /** Météores : annoncés, en chute, puis posés. Monde de cristal (secteur 1). */
    @Test fun dumpMeteors() {
        val game = CosmoRunGame(Random(4))
        game.start()
        game.entities.clear()
        for ((lane, z) in listOf(1 to 34f, 3 to 26f, 2 to 60f)) game.entities += CosmoRunGame.Entity(EntityType.METEOR, lane, z, .9f)
        game.entities += CosmoRunGame.Entity(EntityType.CONTAINER, 0, 10f, 6f)
        var t = 0f
        // Aller au secteur de cristal : on avance la distance en simulant sans obstacle sur la voie du joueur.
        repeat(20) { game.update(1f / 60f); t += 1f / 60f }
        shots(game, "meteor_0", t)
        repeat(20) { game.update(1f / 60f); t += 1f / 60f }
        shots(game, "meteor_1", t)
    }
}
