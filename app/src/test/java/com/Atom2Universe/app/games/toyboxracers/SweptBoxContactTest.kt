package com.Atom2Universe.app.games.toyboxracers

import com.Atom2Universe.app.games.toyboxracers.driving.sweepBoxEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SweptBoxContactTest {
    @Test
    fun unMurFinGardeSaFaceDEntreeMemeSiLaVoitureLeTraverseEnUnPas() {
        val hit = sweepBoxEntry(-2f, 0f, 2f, 0f, -.1f, .1f, -1f, 1f)
        assertNotNull(hit)
        assertEquals(-1f, hit!!.normalX, 0f)
        assertEquals(0f, hit.normalZ, 0f)
    }

    @Test
    fun entrerAuCoinUtiliseLeDernierPlanFranchi() {
        val hit = sweepBoxEntry(-2f, -4f, 0f, 0f, -1f, 1f, -1f, 1f)
        assertNotNull(hit)
        assertEquals(0f, hit!!.normalX, 0f)
        assertEquals(-1f, hit.normalZ, 0f)
    }

    @Test
    fun longerLeMurOuSenEloignerNeCreePasDeCollision() {
        assertNull(sweepBoxEntry(-1f, -2f, -1f, 2f, -1f, 1f, -1f, 1f))
        assertNull(sweepBoxEntry(-1f, 0f, -2f, 0f, -1f, 1f, -1f, 1f))
        assertNull(sweepBoxEntry(-2f, 2f, 2f, 2f, -1f, 1f, -1f, 1f))
    }

    @Test
    fun unObjetDejaDedansResteTraiteParLaSeparationStatique() {
        assertNull(sweepBoxEntry(0f, 0f, .1f, .1f, -1f, 1f, -1f, 1f))
    }
}
