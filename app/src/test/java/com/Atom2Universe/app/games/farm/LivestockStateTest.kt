package com.Atom2Universe.app.games.farm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde le troupeau, et surtout sa remise à zéro : la triche « réinitialiser » laissait les bêtes,
 * le grain et les champs en place, si bien qu'une partie « neuve » démarrait avec un élevage déjà
 * monté. Ce test couvre le troupeau ; les grands champs s'appuient sur `PointF` et ne peuvent pas
 * tourner hors appareil.
 */
class LivestockStateTest {

    private val heure = 3_600_000L

    private fun troupeauMonte(): LivestockState = LivestockState().apply {
        unlock(LivestockKind.CHICKENS)
        buy(LivestockKind.CHICKENS, male = false, now = 0)
        buy(LivestockKind.CHICKENS, male = true, now = 0)
    }

    @Test
    fun `un couple adulte programme une naissance`() {
        val herd = troupeauMonte()
        assertTrue(herd.advance(LivestockKind.CHICKENS.cycleMillis))
        assertEquals(3, herd.count(LivestockKind.CHICKENS))
        assertEquals(1, herd.animals.count { !it.adult })
    }

    @Test
    fun `la remise a zero ne laisse ni enclos ni bete`() {
        val herd = troupeauMonte()
        herd.advance(LivestockKind.CHICKENS.cycleMillis * 3)
        assertTrue("le troupeau doit avoir grossi avant le test", herd.count(LivestockKind.CHICKENS) > 2)

        herd.reset()

        assertEquals(0, herd.unlocked)
        assertTrue(herd.animals.isEmpty())
        LivestockKind.entries.forEach { assertTrue("${it.name} reste disponible", !herd.available(it)) }
        // Un enclos verrouillé ne doit plus rien accepter : l'achat échoue tant qu'on ne rouvre pas.
        assertTrue(!herd.buy(LivestockKind.CHICKENS, male = false, now = 0))
    }

    @Test
    fun `avancer le temps fait grandir les petits`() {
        val herd = troupeauMonte()
        herd.advance(LivestockKind.CHICKENS.cycleMillis)
        val petit = herd.animals.first { !it.adult }.id

        // La triche rapproche les échéances ; c'est advance qui applique le changement.
        herd.cheatSkip(LivestockKind.CHICKENS.cycleMillis)
        herd.advance(LivestockKind.CHICKENS.cycleMillis + heure)

        // La mère remet bas au même instant : il y a toujours un petit, mais plus celui-là.
        assertTrue("le petit suivi doit être devenu adulte",
            herd.animals.first { it.id == petit }.adult)
    }

    @Test
    fun `tout terminer d'un coup ne casse pas le troupeau`() {
        val herd = troupeauMonte()
        val maintenant = LivestockKind.CHICKENS.cycleMillis
        herd.advance(maintenant)
        val avant = herd.animals.map { it.id }.toSet()

        herd.cheatRush(maintenant)
        herd.advance(maintenant)

        assertTrue("toutes les bêtes présentes doivent être adultes",
            herd.animals.filter { it.id in avant }.all { it.adult })
        assertTrue("les naissances doivent repartir", herd.animals.any { it.birthAt > maintenant })
    }
}
