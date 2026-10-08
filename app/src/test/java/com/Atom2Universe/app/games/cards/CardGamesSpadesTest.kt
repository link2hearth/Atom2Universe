package com.Atom2Universe.app.games.cards

import com.Atom2Universe.app.games.cards.spades.SpadesEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous de Spades : annonces, règles des plis, décompte, partie complète, rejeu, force de l'IA. */
class CardGamesSpadesTest {

    private fun card(suit: CardSuit, rank: Int) = PlayingCard.of(suit, rank)
    private fun suitHand(suit: CardSuit) = (1..13).map { card(suit, it) }

    /** Joue une partie entière ; [levels] donne le niveau de chaque siège. */
    private fun playGame(seed: Long, levels: IntArray, random: Random, moves: MutableList<Int>? = null): SpadesEngine {
        val e = SpadesEngine(seed)
        var plies = 0
        while (!e.isOver) {
            assertTrue("la partie ne finit pas (graine $seed)", plies++ < 5000)
            if (!e.bidding) {
                // Treize cartes au départ, jamais plus ; le pli ne dépasse pas quatre cartes.
                assertTrue(e.hands.all { it.size <= 13 })
                assertTrue(e.trick.size < 4)
            }
            val m = e.aiMove(levels[e.current], random)
            moves?.add(m)
            e.play(m)
            e.drainEvents()
        }
        return e
    }

    @Test fun uneManchePasseParLesAnnoncesPuisLesPlis() {
        val e = SpadesEngine(1)
        assertTrue(e.bidding)
        assertEquals(14, e.legalMoves().size)
        for (i in 0 until 4) e.play(SpadesEngine.BID_BASE + 3)
        assertFalse(e.bidding)
        assertTrue(e.legalMoves().all { it in 0..51 })
        assertEquals(13, e.hands[e.current].size)
    }

    @Test fun onNOuvrePasAPiqueAvantQuIlSoitTombe() {
        val hands = listOf(
            listOf(card(CardSuit.SPADES, 13), card(CardSuit.HEARTS, 5)),
            listOf(card(CardSuit.CLUBS, 3)), listOf(card(CardSuit.CLUBS, 4)), listOf(card(CardSuit.CLUBS, 6)),
        )
        val e = SpadesEngine.forTest(hands, intArrayOf(1, 1, 1, 1))
        assertEquals(listOf(card(CardSuit.HEARTS, 5).id), e.legalMoves().toList())
        // Une main de piques seulement peut ouvrir à pique.
        val onlySpades = SpadesEngine.forTest(
            listOf(listOf(card(CardSuit.SPADES, 2), card(CardSuit.SPADES, 9)), hands[1], hands[2], hands[3]),
            intArrayOf(1, 1, 1, 1))
        assertEquals(2, onlySpades.legalMoves().size)
    }

    @Test fun onFournitLaCouleurDemandeeEtUnPiqueCoupe() {
        val hands = listOf(
            listOf(card(CardSuit.HEARTS, 10), card(CardSuit.CLUBS, 2)),
            listOf(card(CardSuit.HEARTS, 3), card(CardSuit.HEARTS, 12)),
            listOf(card(CardSuit.CLUBS, 5), card(CardSuit.SPADES, 2)),
            listOf(card(CardSuit.HEARTS, 4), card(CardSuit.DIAMONDS, 9)),
        )
        val e = SpadesEngine.forTest(hands, intArrayOf(1, 1, 1, 1))
        e.play(card(CardSuit.HEARTS, 10).id)
        assertEquals(2, e.legalMoves().size) // le siège 1 doit fournir cœur
        e.play(card(CardSuit.HEARTS, 12).id)
        assertEquals(2, e.legalMoves().size) // le siège 2 n'a pas de cœur : il joue ce qu'il veut
        e.play(card(CardSuit.SPADES, 2).id)  // il coupe
        e.play(card(CardSuit.HEARTS, 4).id)
        assertEquals(1, e.trickNo) // un pli ramassé
        assertEquals(1, e.tricksWon[2]) // le 2 de pique prend le pli
    }

    @Test fun leDecompteDUneMancheEstJuste() {
        // Le siège 0 a tous les piques et ramasse tout ; les autres n'ont pas de pique.
        val hands = listOf(suitHand(CardSuit.SPADES), suitHand(CardSuit.CLUBS), suitHand(CardSuit.HEARTS), suitHand(CardSuit.DIAMONDS))
        // Équipe 0 (sièges 0 et 2) annonce 5 + 2 ; équipe 1 annonce 3 et un nil (siège 3).
        val e = SpadesEngine.forTest(hands, intArrayOf(5, 3, 2, 0))
        while (e.handNo == 0) {
            val m = e.legalMoves().let { moves ->
                if (e.current == 0) moves.maxBy { PlayingCard(it).highRank } else moves[0]
            }
            e.play(m)
            e.drainEvents()
        }
        // Équipe 0 : 13 plis pour 7 annoncés = 70 + 6 sacs. Équipe 1 : 3 annoncés ratés = −30, nil réussi = +100.
        assertEquals(76, e.teamScore[0])
        assertEquals(6, e.teamBags[0])
        assertEquals(70, e.teamScore[1])
    }

    @Test fun dixSacsCoutentCentPoints() {
        val hands = listOf(suitHand(CardSuit.SPADES), suitHand(CardSuit.CLUBS), suitHand(CardSuit.HEARTS), suitHand(CardSuit.DIAMONDS))
        // L'équipe 0 annonce 2 et ramasse 13 : 11 sacs de trop, donc 10 sacs = −100.
        val e = SpadesEngine.forTest(hands, intArrayOf(1, 1, 1, 1))
        while (e.handNo == 0) {
            val m = e.legalMoves().let { if (e.current == 0) it.maxBy { x -> PlayingCard(x).highRank } else it[0] }
            e.play(m)
            e.drainEvents()
        }
        assertEquals(20 + 11 - 100, e.teamScore[0])
        assertEquals(1, e.teamBags[0])
    }

    @Test fun uneAnnonceNilRateeCouteCentPoints() {
        val hands = listOf(suitHand(CardSuit.SPADES), suitHand(CardSuit.CLUBS), suitHand(CardSuit.HEARTS), suitHand(CardSuit.DIAMONDS))
        // Le siège 0 annonce nil mais ramasse tout.
        val e = SpadesEngine.forTest(hands, intArrayOf(0, 1, 1, 1))
        while (e.handNo == 0) {
            val m = e.legalMoves().let { if (e.current == 0) it.maxBy { x -> PlayingCard(x).highRank } else it[0] }
            e.play(m)
            e.drainEvents()
        }
        // Équipe 0 : nil raté (−100) ; le siège 2 annonce 1 et l'équipe prend 13 plis : +10 et 12 sacs (+12),
        // dont dix coûtent 100.
        assertEquals(-100 + 10 + 12 - 100, e.teamScore[0])
    }

    @Test fun uneEquipeQuiAtteintLaLimiteGagneEtLaPartieEstCoherente() {
        for (level in CardLevel.EASY..CardLevel.HARD) {
            val games = if (level == CardLevel.HARD) 2 else 12
            for (seed in 1L..games) {
                val e = playGame(seed, IntArray(4) { level }, Random(seed))
                assertTrue(e.isOver)
                val winners = e.winners()
                assertTrue(winners.isNotEmpty())
                // Les partenaires gagnent ensemble.
                for (w in winners) assertTrue((w + 2) % 4 in winners)
            }
        }
    }

    @Test fun unePartieSeRejoueALIdentiqueDepuisSaGraineEtSesCoups() {
        for (seed in 1L..4L) {
            val moves = ArrayList<Int>()
            val played = playGame(seed, IntArray(4) { CardLevel.MEDIUM }, Random(seed), moves)
            val again = SpadesEngine(seed)
            for (m in moves) again.play(m)
            assertTrue(again.isOver)
            assertEquals(played.teamScore.toList(), again.teamScore.toList())
            assertEquals(played.handNo, again.handNo)
        }
    }

    @Test fun uneCopieNAffecteJamaisLaPartie() {
        val e = SpadesEngine(3)
        for (i in 0 until 4) e.play(SpadesEngine.BID_BASE + 3)
        val before = e.hands.map { it.toList() }
        val sim = e.copy()
        while (!sim.isOver && sim.handNo == e.handNo) sim.play(sim.aiMove(CardLevel.MEDIUM, Random(1)))
        assertEquals(before, e.hands.map { it.toList() })
        assertEquals(0, e.trickNo)
    }

    @Test fun lIAMoyenneBatLIAFacileParEquipe() {
        var wins = 0
        val games = 14
        for (seed in 1L..games) {
            // Les sièges 0 et 2 (moyen) contre 1 et 3 (facile).
            val e = playGame(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY, CardLevel.MEDIUM, CardLevel.EASY), Random(seed))
            if (0 in e.winners()) wins++
        }
        println("Spades : moyen contre facile $wins/$games")
        assertTrue("l'IA moyenne devrait dominer la facile ($wins/$games)", wins >= games * 2 / 3)
    }

    @Test fun lIADifficileNEstJamaisPireQueLaMoyenne() {
        var wins = 0
        val games = 8
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.HARD, CardLevel.MEDIUM, CardLevel.HARD, CardLevel.MEDIUM), Random(seed))
            if (0 in e.winners()) wins++
        }
        println("Spades : difficile contre moyen $wins/$games")
        assertTrue("l'IA difficile perd trop ($wins/$games)", wins >= games / 2)
    }

    @Test fun lesAnnoncesDeLIASontRaisonnables() {
        // Sur beaucoup de mains, le total des annonces moyennes reste proche de 13 plis (±).
        var total = 0
        var hands = 0
        for (seed in 1L..60L) {
            val e = SpadesEngine(seed)
            repeat(4) { e.play(e.aiMove(CardLevel.MEDIUM, Random(seed))) }
            total += e.bids.sumOf { it }
            hands++
        }
        val mean = total.toDouble() / hands
        println("Spades : total moyen des annonces $mean")
        assertTrue("annonces moyennes $mean", mean in 9.0..16.0)
    }
}
