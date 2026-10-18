package com.Atom2Universe.app.games.cards

import com.Atom2Universe.app.games.cards.ohhell.OhHellEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous d'Oh Hell : le crochet, l'atout, le décompte, la partie complète, le rejeu, la force de l'IA. */
class CardGamesOhHellTest {

    private fun card(suit: CardSuit, rank: Int) = PlayingCard.of(suit, rank)

    private fun playGame(seed: Long, levels: IntArray, random: Random, moves: MutableList<Int>? = null): OhHellEngine {
        val e = OhHellEngine(seed)
        var plies = 0
        var lastHand = 0
        while (!e.isOver) {
            assertTrue("la partie ne finit pas (graine $seed)", plies++ < 1000)
            if (e.handNo != lastHand) lastHand = e.handNo
            assertTrue(e.hands.all { it.size <= e.handSize })
            val m = e.aiMove(levels[e.current], random)
            moves?.add(m)
            e.play(m)
            e.drainEvents()
        }
        return e
    }

    @Test fun lesManchesDonnentDeMoinsEnMoinsDeCartes() {
        val e = OhHellEngine(5)
        assertEquals(7, e.hands[0].size)
        // Quatre annonces, puis tous les plis, jusqu'à la manche suivante.
        repeat(4) { e.play(e.legalMoves().first { it >= OhHellEngine.BID_BASE + 1 }) }
        while (e.handNo == 0) { e.play(e.legalMoves()[0]); e.drainEvents() }
        assertEquals(1, e.handNo)
        assertEquals(6, e.hands[0].size)
    }

    @Test fun leDernierAParlerNePeutPasEgaliserLeTotalEtLesPlis() {
        val e = OhHellEngine(2)
        e.play(OhHellEngine.BID_BASE + 3)
        e.play(OhHellEngine.BID_BASE + 2)
        e.play(OhHellEngine.BID_BASE + 1)
        // 3 + 2 + 1 = 6 : le dernier ne peut pas annoncer 1 (total 7 = nombre de plis).
        val last = e.legalMoves().map { it - OhHellEngine.BID_BASE }
        assertFalse(1 in last)
        assertTrue(0 in last && 2 in last)
        assertEquals(7, last.size) // 0..7 moins un
    }

    @Test fun lAtoutBatLaCouleurDemandee() {
        val hands = listOf(
            listOf(card(CardSuit.SPADES, 13), card(CardSuit.SPADES, 4)),
            listOf(card(CardSuit.SPADES, 2), card(CardSuit.CLUBS, 9)),
            listOf(card(CardSuit.HEARTS, 3), card(CardSuit.CLUBS, 2)),
            listOf(card(CardSuit.SPADES, 8), card(CardSuit.CLUBS, 3)),
        )
        val e = OhHellEngine.forTest(hands, card(CardSuit.HEARTS, 2), intArrayOf(0, 0, 1, 0))
        e.play(card(CardSuit.SPADES, 13).id)
        // Le siège 1 a du pique : il doit en fournir.
        assertEquals(listOf(card(CardSuit.SPADES, 2).id), e.legalMoves().toList())
        e.play(card(CardSuit.SPADES, 2).id)
        // Le siège 2 n'a pas de pique : il coupe avec son atout.
        assertEquals(2, e.legalMoves().size)
        e.play(card(CardSuit.HEARTS, 3).id)
        e.play(card(CardSuit.SPADES, 8).id)
        assertEquals(1, e.tricksWon[2])
    }

    @Test fun faireExactementSonAnnonceRapporteDixPlusLAnnonce() {
        val hands = listOf(
            listOf(card(CardSuit.SPADES, 1), card(CardSuit.SPADES, 13)),
            listOf(card(CardSuit.CLUBS, 2), card(CardSuit.CLUBS, 3)),
            listOf(card(CardSuit.DIAMONDS, 4), card(CardSuit.DIAMONDS, 5)),
            listOf(card(CardSuit.DIAMONDS, 6), card(CardSuit.DIAMONDS, 7)),
        )
        // Atout : cœur, que personne n'a ; le siège 0 ramasse les deux plis.
        val e = OhHellEngine.forTest(hands, card(CardSuit.HEARTS, 2), intArrayOf(2, 0, 1, 0))
        while (e.handNo == 5) {
            val m = e.legalMoves().let { moves -> if (e.current == 0) moves.maxBy { PlayingCard(it).highRank } else moves[0] }
            e.play(m)
            e.drainEvents()
        }
        // Siège 0 : 2 annoncés, 2 faits = 12 ; siège 1 : 0 et 0 = 10 ; siège 2 : 1 annoncé, 0 fait = 0 ; siège 3 : 10.
        assertEquals(listOf(12, 10, 0, 10), e.totals.toList())
    }

    @Test fun unePartieSeTermineAvecLesTroisNiveaux() {
        for (level in CardLevel.EASY..CardLevel.HARD) {
            val games = if (level == CardLevel.HARD) 3 else 20
            for (seed in 1L..games) {
                val e = playGame(seed, IntArray(4) { level }, Random(seed))
                assertTrue(e.isOver)
                assertTrue(e.winners().isNotEmpty())
                // Chaque manche rapporte 0 ou 10 + l'annonce à chacun : jamais de points négatifs.
                assertTrue(e.totals.all { it >= 0 })
            }
        }
    }

    @Test fun unePartieSeRejoueALIdentiqueDepuisSaGraineEtSesCoups() {
        for (seed in 1L..4L) {
            val moves = ArrayList<Int>()
            val played = playGame(seed, IntArray(4) { CardLevel.MEDIUM }, Random(seed), moves)
            val again = OhHellEngine(seed)
            for (m in moves) again.play(m)
            assertTrue(again.isOver)
            assertEquals(played.totals.toList(), again.totals.toList())
        }
    }

    @Test fun uneCopieNAffecteJamaisLaPartie() {
        val e = OhHellEngine(3)
        repeat(4) { e.play(e.legalMoves()[1]) }
        val before = e.hands.map { it.toList() }
        val sim = e.copy()
        while (!sim.isOver && sim.handNo == e.handNo) sim.play(sim.aiMove(CardLevel.MEDIUM, Random(1)))
        assertEquals(before, e.hands.map { it.toList() })
        assertEquals(0, e.trickNo)
    }

    @Test fun lIAMoyenneFaitMieuxQueLaFacile() {
        var medium = 0
        var others = 0
        val games = 24
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY, CardLevel.EASY, CardLevel.EASY), Random(seed))
            medium += e.totals[0]
            others += e.totals[1] + e.totals[2] + e.totals[3]
        }
        val meanOthers = others / 3.0
        println("Oh Hell : moyen $medium contre facile $meanOthers (sur $games parties)")
        assertTrue("l'IA moyenne doit marquer plus ($medium contre $meanOthers)", medium > meanOthers * 1.15)
    }

    @Test fun lIADifficileNEstPasPireQueLaMoyenne() {
        var hard = 0
        var others = 0
        val games = 10
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.HARD, CardLevel.MEDIUM, CardLevel.MEDIUM, CardLevel.MEDIUM), Random(seed))
            hard += e.totals[0]
            others += e.totals[1] + e.totals[2] + e.totals[3]
        }
        val meanOthers = others / 3.0
        println("Oh Hell : difficile $hard contre moyen $meanOthers (sur $games parties)")
        assertTrue("l'IA difficile ne doit pas être pire ($hard contre $meanOthers)", hard >= meanOthers * 0.95)
    }

    @Test fun lesAnnoncesDeLIASuiventLaTailleDeLaMain() {
        var total = 0
        var count = 0
        for (seed in 1L..80L) {
            val e = OhHellEngine(seed)
            repeat(4) { e.play(e.aiMove(CardLevel.MEDIUM, Random(seed))) }
            total += e.bids.sum()
            count++
        }
        val mean = total.toDouble() / count
        println("Oh Hell : total moyen des annonces sur 7 cartes $mean")
        assertTrue("annonces moyennes $mean pour 7 plis", mean in 5.0..9.5)
    }
}
