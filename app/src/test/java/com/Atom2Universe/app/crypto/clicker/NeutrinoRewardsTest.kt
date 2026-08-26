package com.Atom2Universe.app.crypto.clicker

import org.junit.Assert.assertEquals
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
}
