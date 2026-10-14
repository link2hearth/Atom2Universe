package com.Atom2Universe.app.games.hamlet

import org.junit.Test
import kotlin.random.Random

/**
 * Banc de la campagne : jusqu'où une équipe au plafond de niveau pour ses étoiles se laisse battre.
 * Il sert à régler la courbe des ennemis (`enemyScale`), pas à vérifier une compilation. Exclu des tests ordinaires.
 * ./gradlew testDebugUnitTest -PbancsMesure --tests "*HamletAdventureBenchTest*"
 */
class HamletAdventureBenchTest {
    private val start = 1_800_000_000_000L

    private fun wins(team: IntArray, stars: Int, stage: Int, seeds: Int): Double {
        var won = 0
        for (seed in 0 until seeds) {
            val g = HamletGame({ start }, Random(seed)).apply { startNew(false) }
            val a = g.adventure
            a.frontier = 400
            team.forEachIndexed { slot, id ->
                val h = a.heroes[id]; a.welcome(h)
                h.stars = stars.coerceAtMost(HamletHeroes.STAR_CAP); h.level = a.levelCap(id)
                for (i in h.skills.indices) h.skills[i] = a.skillCap(id)
                a.assign(id, slot)
            }
            val battle = a.beginBattle(BattleMode.CAMPAIGN, stage, seed * 31 + stage) ?: continue
            var guard = 0
            while (battle.outcome == BattleOutcome.RUNNING && guard++ < 40) battle.advance(30f)
            if (battle.outcome == BattleOutcome.VICTORY) won++
        }
        return won * 100.0 / seeds
    }

    /** Pour chaque nombre d'étoiles, le pourcentage de victoires par tranche d'étapes (cinq héros, tout au plafond). */
    @Test fun winsByStars() {
        val team = intArrayOf(7, 1, 3, 0, 2)
        val stages = listOf(0, 5, 9, 19, 29, 39, 49, 59, 79, 99, 119, 149, 199)
        println("étoiles | " + stages.joinToString(" ") { "%4d".format(it + 1) })
        for (stars in listOf(0, 1, 2, 3, 4, 5, 6, 8, 10)) {
            println("%7d | ".format(stars) + stages.joinToString(" ") { "%4.0f".format(wins(team, stars, it, 6)) })
        }
    }

    /** Une équipe réduite à un, deux ou trois héros face aux premières étapes (début de partie). */
    @Test fun smallTeamsEarly() {
        val teams = listOf(intArrayOf(7), intArrayOf(7, 3), intArrayOf(7, 3, 0))
        for (team in teams) for (stars in listOf(0, 1, 2)) {
            println("${team.size} héros, $stars étoile(s) : " + (0..9).joinToString(" ") { "%3.0f".format(wins(team, stars, it, 6)) })
        }
    }
}
