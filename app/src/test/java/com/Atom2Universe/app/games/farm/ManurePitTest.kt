package com.Atom2Universe.app.games.farm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde la boucle élevage → potager. Le piège de ce système, c'est que `advance` est appelé une fois
 * par seconde par l'écran : si chaque appel arrondissait sa seconde à zéro point, la fosse ne se
 * remplirait jamais. C'est exactement ce que vérifie le test central ici.
 */
class ManurePitTest {

    private val jour = LivestockState.DAY
    /** Une date plausible : un troupeau ne tourne jamais à l'instant zéro. */
    private val debut = 1_700_000_000_000L

    private fun poulailler(): LivestockState = LivestockState().apply {
        unlock(LivestockKind.CHICKENS)
        buy(LivestockKind.CHICKENS, male = false, now = debut)
        buy(LivestockKind.CHICKENS, male = true, now = debut)
        advance(debut)
    }

    @Test
    fun `un enclos vide ne produit rien`() {
        val herd = LivestockState().apply { unlock(LivestockKind.CHICKENS); advance(debut) }
        herd.advance(debut + jour * 10)
        assertEquals(0L, herd.manure)
    }

    @Test
    fun `deux poules adultes remplissent la fosse`() {
        val herd = poulailler()
        assertEquals(2 * LivestockKind.CHICKENS.manurePerDay.toLong(), herd.manurePerDay())
        herd.advance(debut + jour)
        assertEquals(8L, herd.manure)
    }

    /**
     * Le cœur du système : en sondant seconde par seconde, les millisecondes non converties doivent
     * rester en réserve. Un appel unique et 21 600 petits appels doivent donner le même total.
     */
    @Test
    fun `sonder chaque seconde donne autant qu'un seul appel`() {
        val sixHeures = 6 * 3_600_000L
        val unSeulAppel = poulailler().apply { advance(debut + sixHeures) }.manure

        val sonde = poulailler()
        var t = 0L
        while (t < sixHeures) { t += 1000L; sonde.advance(debut + t) }

        assertEquals(2L, unSeulAppel)
        assertEquals("les millisecondes perdues videraient la fosse", unSeulAppel, sonde.manure)
    }

    @Test
    fun `la fosse ne depasse jamais trois jours de production`() {
        val herd = poulailler()
        herd.advance(debut + jour * 60)
        assertTrue("le troupeau doit avoir grossi", herd.count(LivestockKind.CHICKENS) > 2)
        assertEquals(herd.manureCapacity(), herd.manure)
        assertTrue(herd.manureCapacity() >= LivestockState.MANURE_FLOOR)
    }

    @Test
    fun `une plantation prend le rang de la graine, ou rien`() {
        // Douze heures de deux poules : quatre points, de quoi semer des radis, pas des citrouilles.
        val herd = poulailler()
        herd.advance(debut + jour / 2)
        val avant = herd.manure
        assertEquals(4L, avant)

        assertTrue(herd.spendManure(FarmCrop.RADISH.manureCost))
        assertEquals(avant - FarmCrop.RADISH.manureCost, herd.manure)

        // Une citrouille coûte douze points : refusée tant que la fosse ne suit pas, et sans rien prélever.
        val reste = herd.manure
        assertTrue(!herd.spendManure(FarmCrop.PUMPKIN.manureCost))
        assertEquals("un refus ne doit rien coûter", reste, herd.manure)
    }

    @Test
    fun `la remise a zero vide la fosse`() {
        val herd = poulailler()
        herd.advance(debut + jour * 5)
        assertTrue(herd.manure > 0)
        herd.reset()
        assertEquals(0L, herd.manure)
        // Et la fosse repart de maintenant, pas de 1970 : sans ça le premier appel créditerait 55 ans.
        herd.advance(debut + jour * 5)
        assertEquals(0L, herd.manure)
    }

    @Test
    fun `avancer le temps de force remplit aussi la fosse`() {
        val herd = poulailler()
        herd.advance(debut + 1)
        herd.cheatSkip(jour)
        herd.advance(debut + 1)
        assertEquals(8L, herd.manure)
    }
}
