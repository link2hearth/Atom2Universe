package com.Atom2Universe.app.crypto.clicker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Barème des neutrinos : seules les règles calculées sont testées ici. */
class NeutrinoRewardsTest {

    @Test
    fun `equilibre - chaque essai rate coute un neutrino`() {
        // EASY = 3, MEDIUM = 6, HARD = 12 quand on réussit du premier coup.
        assertEquals(3, NeutrinoRewards.balance(0))
        assertEquals(6, NeutrinoRewards.balance(1))
        assertEquals(12, NeutrinoRewards.balance(2))

        assertEquals(2, NeutrinoRewards.balance(0, 1))
        assertEquals(9, NeutrinoRewards.balance(2, 3))
    }

    @Test
    fun `equilibre - la recompense ne descend jamais sous un neutrino`() {
        assertEquals(1, NeutrinoRewards.balance(0, 2))
        assertEquals(1, NeutrinoRewards.balance(0, 40))
        assertEquals(1, NeutrinoRewards.balance(2, 99))
    }

    /**
     * Trébuchet : la récompense monte avec ce que le site oppose, jamais avec le nombre
     * de tirs — au trébuchet on règle sa machine sur plusieurs coups avant d'être sûr de
     * toucher où l'on vise, et compter les tirs punirait le réglage.
     *
     * La difficulté ne se calcule pas ici : elle vient de `TargetGenerator.difficulty`,
     * qui sert aussi à fixer l'objectif de tirs du niveau. Un seul chiffre pour les deux,
     * sinon un site paierait comme un facile en se jouant comme un difficile.
     */
    @Test
    fun `trebuchet - un site facile vaut le minimum, un site dur le maximum`() {
        assertEquals(NeutrinoRewards.TREBUCHET_MIN, NeutrinoRewards.trebuchet(0f))
        assertEquals(NeutrinoRewards.TREBUCHET_MAX, NeutrinoRewards.trebuchet(1f))
        assertEquals(30, NeutrinoRewards.trebuchet(0.5f))
    }

    @Test
    fun `trebuchet - la recompense reste toujours dans ses bornes`() {
        for (d in listOf(-5f, -1f, 0f, 0.33f, 0.5f, 0.99f, 1f, 2f, 100f)) {
            val gain = NeutrinoRewards.trebuchet(d)
            assertTrue(
                "difficulté=$d donne $gain",
                gain in NeutrinoRewards.TREBUCHET_MIN..NeutrinoRewards.TREBUCHET_MAX
            )
        }
    }

    @Test
    fun `trebuchet - la recompense ne descend jamais quand la difficulte monte`() {
        var precedent = 0
        for (i in 0..20) {
            val gain = NeutrinoRewards.trebuchet(i / 20f)
            assertTrue("la récompense a reculé à ${i / 20f}", gain >= precedent)
            precedent = gain
        }
    }
}
