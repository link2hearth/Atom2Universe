package com.Atom2Universe.app.games.cosmorun

import org.junit.Test
import java.io.File
import kotlin.random.Random

/** Sortie de diagnostic (pas d'assertion) : ce qui entoure le joueur à chaque choc du lecteur exact. */
class CosmoRunDebugTest {
    @Test fun dumpExactBotHits() {
        val out = StringBuilder()
        for (seed in 1..2) {
            val game = CosmoRunGame(Random(seed))
            val bot = ReaderBot(game, Random(seed * 31 + 7), 0f, 0f)
            var t = 0f
            val history = ArrayDeque<String>()
            game.onEvent = { event, v ->
                if (event == CosmoRunGame.Event.STUMBLE || event == CosmoRunGame.Event.CRASH) {
                    val near = game.entities.filter { !it.dead && it.z > -3f && it.z < 30f && it.type.blocking }
                        .sortedBy { it.z }.joinToString(" ") { "${it.type.name[0]}${it.lane}@${"%.1f".format(it.z)}+${"%.1f".format(it.length)}" }
                    out.appendLine("seed=$seed t=%.2f d=%.0f $event(hitlane=$v) lane=%d/%.2f y=%.2f v=%.1f | $near | last: ${history.takeLast(8).joinToString(",")}".format(t, game.distance, game.laneTarget, game.laneF, game.playerY, game.speed))
                }
                if (event == CosmoRunGame.Event.LANE || event == CosmoRunGame.Event.JUMP || event == CosmoRunGame.Event.SLIDE || event == CosmoRunGame.Event.SCRAPE)
                    history.addLast("$event$v@%.2f".format(t))
            }
            game.start()
            while (game.isRunning && game.distance < 4000f && t < 1500f) { bot.step(1f / 60f); game.update(1f / 60f); t += 1f / 60f }
            out.appendLine("── seed $seed fini à ${game.distance.toInt()} m")
        }
        File("build/cosmo-sim").apply { mkdirs() }.let { File(it, "debug.txt").writeText(out.toString()) }
    }
}
