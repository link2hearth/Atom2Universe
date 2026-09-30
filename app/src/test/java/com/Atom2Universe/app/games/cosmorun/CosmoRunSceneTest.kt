package com.Atom2Universe.app.games.cosmorun

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Le budget de faces : l'astronaute doit toujours être dessiné, sur toutes les pistes que produit le jeu. */
class CosmoRunSceneTest {
    @Test fun faceBudgetHoldsAndThePlayerIsAlwaysDrawn() {
        val counts = ArrayList<Int>()
        var playerMissing = 0
        for (seed in 1..12) {
            val game = CosmoRunGame(Random(seed))
            val bot = ReaderBot(game, Random(seed), .03f, .02f, greedy = seed % 2 == 0)
            val scene = CosmoRunScene()
            game.start()
            var tick = 0
            while (game.isRunning && game.distance < 3500f && tick < 60 * 220) {
                bot.step(1f / 60f); game.update(1f / 60f); tick++
                if (tick % 20 != 0) continue
                scene.frame(1000, 560, game, tick / 60f)
                scene.begin(); scene.buildDeck(game.distance)
                val deck = scene.faceCount
                scene.begin(); scene.buildWorld(game, false, game.distance)
                counts.add(deck + scene.faceCount)
                // Les dernières faces émises sont celles du personnage : il en faut plusieurs dizaines.
                if (scene.faceCount < 60) playerMissing++
            }
        }
        counts.sort()
        val p99 = counts[counts.size * 99 / 100]
        File("build/cosmo-sim").apply { mkdirs() }.let {
            File(it, "faces.txt").writeText("images=${counts.size} médiane=${counts[counts.size / 2]} p99=$p99 max=${counts.last()}\n")
        }
        assertTrue("p99 de faces : $p99", p99 < 2000)
        assertTrue("le personnage manquait sur $playerMissing images", playerMissing == 0)
    }
}
