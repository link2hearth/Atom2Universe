package com.Atom2Universe.app.games.roulette

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La roulette ne doit pas créer de neutrinos : un tirage doit rendre en moyenne moins que
 * la mise. L'espérance d'une ligne se calcule exactement en parcourant les 7^3 lignes
 * possibles avec les poids des symboles ; les 8 lignes de la grille s'additionnent.
 */
class RouletteGameTest {

    @Test
    fun `un tirage rend en moyenne moins que la mise`() {
        val game = RouletteGame()
        val symbols = RouletteSymbol.entries
        val total = symbols.sumOf { it.weight.toDouble() }
        val coords = listOf(Pair(0, 0), Pair(0, 1), Pair(0, 2))
        var perLine = 0.0
        for (a in symbols) for (b in symbols) for (c in symbols) {
            val p = a.weight * b.weight * c.weight / (total * total * total)
            val mult = game.evaluateLine(listOf(a, b, c), coords)?.multiplier ?: 0
            perLine += p * mult
        }
        val perSpin = perLine * 8
        println("Roulette : espérance par ligne = $perLine, par tirage = $perSpin")
        assertTrue("un tirage rend $perSpin × la mise", perSpin < 1.0)
    }
}
