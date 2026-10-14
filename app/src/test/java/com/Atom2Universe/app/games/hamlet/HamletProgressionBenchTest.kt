package com.Atom2Universe.app.games.hamlet

import org.junit.Test
import java.io.File

/**
 * Compare les quatre joueurs simulés (passionné, régulier, occasionnel, minimal) sur plusieurs mois, avec les
 * vraies règles du jeu. Chaque ligne donne le jour où une étape de la partie est atteinte ; une étape jamais
 * atteinte reste vide. Les courbes complètes vont dans les fichiers csv de `build/reports/hamlet-long`.
 * ./gradlew testDebugUnitTest -PbancsMesure --tests "*HamletProgressionBenchTest*"
 * Réglage : -Dhamlet.days=120 (durée simulée), -Dhamlet.seeds=2.
 */
class HamletProgressionBenchTest {
    private val out = File("build/reports/hamlet-long").also { it.mkdirs() }
    private val keys = listOf("heroes_3", "heroes_8", "heroes_16", "heroes_24", "all_heroes", "slot_4", "slot_5", "hall_3", "hall_5", "hall_8", "chapter_6", "chapter_12",
        "legend_8", "legend_30", "legend_35", "legend_10", "frontier_40", "frontier_100", "frontier_200", "level_20", "level_40")

    @Test fun rhythmsCompared() {
        val days = (System.getProperty("hamlet.days") ?: "120").toInt()
        val seeds = (System.getProperty("hamlet.seeds") ?: "2").toInt()
        val rhythms = listOf(Rhythm.PASSIONATE, Rhythm.REGULAR, Rhythm.CASUAL, Rhythm.MINIMAL)
        println("jour d'arrivée de chaque étape (médiane sur $seeds parties de $days jours)")
        println("%-12s".format("rythme") + keys.joinToString(" ") { "%9s".format(it) })
        for (r in rhythms) {
            val bots = (1..seeds).map { seed -> HamletBot(r, seed).also { it.run(days, 2.0); File(out, "${r.name}_${days}j_$seed.csv").writeText(it.timeline.toString()) } }
            val row = keys.joinToString(" ") { key ->
                val values = bots.mapNotNull { it.milestones[key] }.sorted()
                if (values.size * 2 < bots.size) "%9s".format("-") else "%9.1f".format(values[values.size / 2])
            }
            println("%-12s".format(r.name) + row)
            println("  " + bots.first().summary())
        }
    }
}
