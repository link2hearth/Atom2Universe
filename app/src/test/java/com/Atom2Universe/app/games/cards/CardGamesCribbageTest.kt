package com.Atom2Universe.app.games.cards

import com.Atom2Universe.app.games.cards.cribbage.CribbageEngine
import com.Atom2Universe.app.games.cards.cribbage.CribbageScoring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous du Cribbage : le décompte des mains et du jeu, la manche, la partie complète, l'IA. */
class CardGamesCribbageTest {

    private val C = CardSuit.CLUBS
    private val D = CardSuit.DIAMONDS
    private val H = CardSuit.HEARTS
    private val S = CardSuit.SPADES

    private fun card(suit: CardSuit, rank: Int) = PlayingCard.of(suit, rank)

    /** Les cartes d'une pile par leurs hauteurs (la couleur n'y compte pas). */
    private fun pile(vararg ranks: Int) = ranks.map { card(C, it) }

    // ── Le compte des mains ─────────────────────────────────────────────────────────────────

    @Test fun laMainDeVingtNeufEstLaMeilleure() {
        val hand = listOf(card(C, 5), card(D, 5), card(S, 5), card(H, 11))
        assertEquals(29, CribbageScoring.scoreHand(hand, card(H, 5)))
    }

    @Test fun uneDoubleSuiteCompteDeuxFois() {
        val hand = listOf(card(C, 3), card(D, 3), card(H, 4), card(S, 5))
        // Quinze : roi + 5, et 3-3-4-5 : 4 points ; paire de 3 : 2 ; deux suites de 3 : 6.
        assertEquals(12, CribbageScoring.scoreHand(hand, card(C, 13)))
    }

    @Test fun leValetDeLaCouleurDeLaRetourneeMarqueUnPoint() {
        val hand = listOf(card(D, 11), card(C, 2), card(S, 7), card(H, 9))
        // Un seul quinze (2 + 4 + 9), plus le nobs.
        assertEquals(3, CribbageScoring.scoreHand(hand, card(D, 4)))
    }

    @Test fun laCouleurDuCribExigeCinqCartes() {
        val hand = listOf(card(H, 2), card(H, 4), card(H, 6), card(H, 8))
        val withoutFlush = CribbageScoring.scoreHand(hand, card(S, 13), isCrib = true)
        assertEquals(withoutFlush + 4, CribbageScoring.scoreHand(hand, card(S, 13), isCrib = false))
        // Avec la retournée de la même couleur, cinq points, même au crib.
        assertEquals(CribbageScoring.scoreHand(hand, card(H, 13), isCrib = false), CribbageScoring.scoreHand(hand, card(H, 13), isCrib = true))
    }

    @Test fun uneMainSansRienNeMarquePas() {
        val hand = listOf(card(C, 2), card(D, 4), card(H, 6), card(S, 13))
        assertEquals(0, CribbageScoring.scoreHand(hand, card(C, 8)))
    }

    // ── Le compte en jouant ─────────────────────────────────────────────────────────────────

    @Test fun quinzeEtTrenteEtUnRapportentDeux() {
        assertEquals(2, CribbageScoring.pegPoints(pile(10, 5), 15))
        assertEquals(2, CribbageScoring.pegPoints(pile(10, 10, 10, 1), 31))
        assertEquals(0, CribbageScoring.pegPoints(pile(9, 2), 11))
    }

    @Test fun pairesBrelansEtCarresFinDePile() {
        assertEquals(2, CribbageScoring.pegPoints(pile(8, 8), 16))
        assertEquals(6, CribbageScoring.pegPoints(pile(4, 4, 4), 12))
        assertEquals(12, CribbageScoring.pegPoints(pile(4, 4, 4, 4), 16))
        // Une carte différente au milieu casse la série.
        assertEquals(0, CribbageScoring.pegPoints(pile(4, 9, 4), 17))
    }

    @Test fun lesSuitesCompteDansLeDesordreEnFinDePile() {
        assertEquals(3, CribbageScoring.pegPoints(pile(7, 8, 9), 24))
        assertEquals(3, CribbageScoring.pegPoints(pile(3, 5, 4), 12))
        assertEquals(5, CribbageScoring.pegPoints(pile(2, 3, 5, 4, 6), 20))
        assertEquals(0, CribbageScoring.pegPoints(pile(7, 8, 10), 25))
    }

    // ── La manche ───────────────────────────────────────────────────────────────────────────

    @Test fun apresLesEcartsOnRetourneLaCarteEtLeNonDonneurOuvre() {
        val e = CribbageEngine(1)
        assertEquals(CribbageEngine.DISCARD, e.phase)
        assertTrue(e.hands.all { it.size == 6 })
        assertEquals(15, e.legalMoves().size)
        e.play(e.legalMoves()[0])
        assertEquals(1, e.current)
        e.play(e.legalMoves()[0])
        assertEquals(CribbageEngine.PEG, e.phase)
        assertEquals(4, e.crib.size)
        assertTrue(e.hands.all { it.size == 4 })
        assertEquals(1 - e.dealer, e.current)
        assertTrue(e.starter != null)
    }

    @Test fun onNePoseJamaisAuDessusDeTrenteEtUne() {
        for (seed in 1L..20L) {
            val e = CribbageEngine(seed)
            var plies = 0
            while (!e.isOver && plies++ < 3000) {
                if (e.phase == CribbageEngine.PEG) assertTrue(e.count in 0..31)
                e.play(e.aiMove(CardLevel.MEDIUM, Random(seed)))
                e.drainEvents()
            }
            assertTrue(e.isOver)
        }
    }

    @Test fun unJeuComplet_goDernierePoseEtCompteDesMains() {
        val hands = listOf(listOf(card(S, 10), card(H, 10)), listOf(card(D, 10), card(C, 5)))
        val e = CribbageEngine.forTestPeg(hands, starter = card(C, 4), crib = listOf(card(C, 2), card(C, 7), card(D, 9), card(H, 13)), dealer = 1)
        assertEquals(0, e.current)
        e.play(card(S, 10).id)
        e.play(card(D, 10).id) // paire : 2 points
        assertEquals(2, e.totals[1])
        e.play(card(H, 10).id) // brelan : 6 points, total 30
        // Brelan : 6 points ; le total est 30, personne ne peut poser (le 5 de trèfle ferait 35) : le dernier
        // à poser marque 1 pour « go », et la pile repart.
        assertEquals(7, e.totals[0])
        assertEquals(0, e.count)
        assertEquals(1, e.current)
        e.play(card(C, 5).id)
        // Plus aucune carte nulle part : dernier point (1), puis le non-donneur compte sa main (paire de 10 = 2).
        assertEquals(CribbageEngine.SHOW, e.phase)
        assertEquals(3, e.totals[1])
        assertEquals(9, e.totals[0])
        assertEquals(0, e.current)
        assertEquals(listOf(CribbageEngine.CONTINUE), e.legalMoves().toList())
        assertTrue(e.drainEvents().any { it is CardEvent.Lay })
    }

    @Test fun treizeEtUnFontSweepEtMarquentDeux() {
        val hands = listOf(listOf(card(S, 10), card(H, 10), card(C, 9)), listOf(card(D, 10), card(C, 1), card(C, 3)))
        val e = CribbageEngine.forTestPeg(hands, starter = card(C, 4), dealer = 1)
        e.play(card(S, 10).id)
        e.play(card(D, 10).id)
        e.play(card(H, 10).id)
        e.play(card(C, 1).id) // 31 : 2 points
        assertEquals(0, e.count)
        assertEquals(0, e.pile.size)
        assertTrue(e.totals[1] >= 4) // paire (2) + trente et un (2)
    }

    @Test fun laPartieSArreteDesQuOnAtteintLaLimite() {
        val hands = listOf(listOf(card(S, 10), card(H, 10)), listOf(card(D, 10), card(C, 5)))
        val e = CribbageEngine.forTestPeg(hands, starter = card(C, 4), dealer = 1)
        e.totals[0] = 119
        e.play(card(S, 10).id)
        e.play(card(D, 10).id)
        e.play(card(H, 10).id) // 6 points : on dépasse 121 sur-le-champ
        assertTrue(e.isOver)
        assertEquals(setOf(0), e.winners())
    }

    // ── Parties ─────────────────────────────────────────────────────────────────────────────

    private fun playGame(seed: Long, levels: IntArray, random: Random, moves: MutableList<Int>? = null): CribbageEngine {
        val e = CribbageEngine(seed)
        var plies = 0
        while (!e.isOver) {
            assertTrue("la partie ne finit pas (graine $seed)", plies++ < 6000)
            val m = e.aiMove(levels[e.current], random)
            moves?.add(m)
            e.play(m)
            e.drainEvents()
        }
        return e
    }

    @Test fun unePartieSeTermineAvecLesTroisNiveaux() {
        for (level in CardLevel.EASY..CardLevel.HARD) {
            for (seed in 1L..4L) {
                val e = playGame(seed, IntArray(2) { level }, Random(seed))
                assertTrue(e.isOver)
                assertTrue("personne n'a atteint la limite", e.totals.max() >= CribbageEngine.DEFAULT_LIMIT || e.handNo >= 39)
            }
        }
    }

    @Test fun unePartieSeRejoueALIdentiqueDepuisSaGraineEtSesCoups() {
        for (seed in 1L..5L) {
            val moves = ArrayList<Int>()
            val played = playGame(seed, IntArray(2) { CardLevel.MEDIUM }, Random(seed), moves)
            val again = CribbageEngine(seed)
            for (m in moves) again.play(m)
            assertTrue(again.isOver)
            assertEquals(played.totals.toList(), again.totals.toList())
        }
    }

    @Test fun uneCopieNAffecteJamaisLaPartie() {
        val e = CribbageEngine(3)
        val before = e.hands.map { it.toList() }
        val sim = e.copy()
        repeat(15) { if (!sim.isOver) sim.play(sim.aiMove(CardLevel.MEDIUM, Random(1))) }
        assertEquals(before, e.hands.map { it.toList() })
        assertEquals(CribbageEngine.DISCARD, e.phase)
        assertFalse(e.isOver)
    }

    @Test fun lIAMoyenneBatLIAFacile() {
        var wins = 0
        val games = 24
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY), Random(seed))
            if (0 in e.winners()) wins++
        }
        println("Cribbage : moyen contre facile $wins/$games")
        assertTrue("l'IA moyenne devrait dominer ($wins/$games)", wins >= games * 6 / 10)
    }

    @Test fun lIADifficileNEstPasPireQueLaMoyenne() {
        var wins = 0
        val games = 16
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.HARD, CardLevel.MEDIUM), Random(seed))
            if (0 in e.winners()) wins++
        }
        println("Cribbage : difficile contre moyen $wins/$games")
        assertTrue("l'IA difficile perd trop ($wins/$games)", wins >= games * 3 / 10)
    }
}
