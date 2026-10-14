package com.Atom2Universe.app.games.hamlet

import org.junit.Test
import java.io.File

/**
 * Banc de la partie complète : un joueur passionné contre des joueurs qui passent cinq minutes deux ou trois fois par jour.
 * Les règles sont celles du jeu, aucun gain n'est ajouté. Ce sont des robots à la politique explicite, pas des joueurs.
 * ./gradlew testDebugUnitTest -PbancsMesure --tests "*HamletBotBenchTest*"
 */
class HamletBotBenchTest {
    private val out = File("build/reports/hamlet-long").also { it.mkdirs() }

    private fun play(rhythm: Rhythm, seed: Int, days: Int): HamletBot {
        val bot = HamletBot(rhythm, seed)
        val start = System.nanoTime()
        bot.run(days, (System.getProperty("hamlet.snap") ?: "1.0").toDouble())
        File(out, "${rhythm.name}_${days}j_$seed.csv").writeText(bot.timeline.toString())
        println("${rhythm.name} seed=$seed ${days}j: ${(System.nanoTime() - start) / 1_000_000} ms, ${bot.battles} combats (${bot.defeats} défaites), ${(bot.activeSeconds / 3600).toInt()} h actives")
        println("  " + bot.summary())
        println(bot.teamReport())
        println(bot.legendReport())
        println(bot.chapterReport())
        return bot
    }

    /** Sonde courte pour régler le début de partie (trois premiers jours). */
    @Test fun probe() {
        val days = (System.getProperty("hamlet.days") ?: "3").toInt()
        val which = System.getProperty("hamlet.rhythm") ?: "passionne"
        val rhythm = listOf(Rhythm.PASSIONATE, Rhythm.REGULAR, Rhythm.CASUAL, Rhythm.MINIMAL).first { it.name == which }
        val bot = play(rhythm, 1, days)
        val rows = bot.timeline.toString().lines().filter { it.isNotBlank() }
        val step = (System.getProperty("hamlet.step") ?: "10").toInt()
        println(rows.first()); println(rows.drop(1).filterIndexed { i, _ -> i % step == 0 || i == rows.size - 2 }.joinToString("\n"))
    }
}
