package com.Atom2Universe.app.games.cards

import com.Atom2Universe.app.games.cards.euchre.EuchreEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous d'Euchre : le choix de l'atout, les bowers, jouer seul, le décompte, la partie complète, l'IA. */
class CardGamesEuchreTest {

    private fun card(suit: CardSuit, rank: Int) = PlayingCard.of(suit, rank)
    private val J = PlayingCard.JACK
    private val A = PlayingCard.ACE

    private fun playGame(seed: Long, levels: IntArray, random: Random, moves: MutableList<Int>? = null): EuchreEngine {
        val e = EuchreEngine(seed)
        var plies = 0
        while (!e.isOver) {
            assertTrue("la partie ne finit pas (graine $seed)", plies++ < 4000)
            assertTrue(e.hands.all { it.size <= 6 })
            val m = e.aiMove(levels[e.current], random)
            moves?.add(m)
            e.play(m)
            e.drainEvents()
            // Une manche finie rapporte l'un des résultats permis.
            if (e.phase == EuchreEngine.ROUND1 && e.trickNo == 0 && e.handNo > 0) {
                val d = e.lastHand
                val ok = (d[0] in listOf(0, 1, 2, 4) && d[1] == 0) || (d[1] in listOf(0, 1, 2, 4) && d[0] == 0)
                assertTrue("décompte impossible ${d.toList()}", ok)
            }
        }
        return e
    }

    @Test fun lePaquetA24CartesEtChacunEnRecoitCinq() {
        val e = EuchreEngine(1)
        assertEquals(EuchreEngine.ROUND1, e.phase)
        assertTrue(e.hands.all { it.size == 5 })
        val all = e.hands.flatMap { it } + e.upcard
        assertTrue(all.all { it.rank == A || it.rank >= 9 })
        assertEquals(all.size, all.toSet().size)
        assertEquals(listOf(EuchreEngine.PASS, EuchreEngine.ORDER, EuchreEngine.ORDER_ALONE), e.legalMoves().toList())
    }

    @Test fun prendreFaitRamasserLaCarteAuDonneurQuiEnEcarteUne() {
        val e = EuchreEngine(1)
        val dealer = e.dealer
        e.play(EuchreEngine.ORDER)
        assertEquals(e.upcard.suit, e.trump)
        assertEquals(EuchreEngine.DISCARD, e.phase)
        assertEquals(dealer, e.current)
        assertEquals(6, e.hands[dealer].size)
        assertTrue(e.drainEvents().any { it is CardEvent.Draw && it.fromDiscard && it.seat == dealer })
        e.play(e.legalMoves()[0])
        assertEquals(EuchreEngine.PLAY, e.phase)
        assertEquals(5, e.hands[dealer].size)
    }

    @Test fun auSecondTourOnNommeUneAutreCouleurEtLeDonneurNePeutPasPasser() {
        val e = EuchreEngine(2)
        repeat(4) { e.play(EuchreEngine.PASS) }
        assertEquals(EuchreEngine.ROUND2, e.phase)
        val firstMoves = e.legalMoves().toList()
        assertTrue(EuchreEngine.PASS in firstMoves)
        assertFalse(EuchreEngine.NAME_BASE + e.upcard.suit.ordinal in firstMoves)
        assertEquals(1 + 6, firstMoves.size)
        repeat(3) { e.play(EuchreEngine.PASS) }
        assertEquals(e.dealer, e.current)
        assertFalse(EuchreEngine.PASS in e.legalMoves().toList())
    }

    @Test fun leBowerGaucheCompteCommeAtoutPourFournir() {
        // Atout cœur : le valet de carreau est un cœur. Carreau demandé : le siège 1 n'est pas obligé de le jouer.
        val hands = listOf(
            listOf(card(CardSuit.DIAMONDS, 9), card(CardSuit.CLUBS, 9)),
            listOf(card(CardSuit.DIAMONDS, J), card(CardSuit.DIAMONDS, 10)),
            listOf(card(CardSuit.CLUBS, 10), card(CardSuit.CLUBS, 11 - 1)),
            listOf(card(CardSuit.SPADES, 9), card(CardSuit.SPADES, 10)),
        )
        val e = EuchreEngine.forTest(hands, CardSuit.HEARTS, maker = 0)
        e.play(card(CardSuit.DIAMONDS, 9).id)
        assertEquals(listOf(card(CardSuit.DIAMONDS, 10).id), e.legalMoves().toList())
        assertEquals(CardSuit.HEARTS, e.effectiveSuit(card(CardSuit.DIAMONDS, J)))
    }

    @Test fun leBowerDroitBatLeGaucheQuiBatLAsDAtout() {
        val hands = listOf(
            listOf(card(CardSuit.HEARTS, A), card(CardSuit.CLUBS, 9)),
            listOf(card(CardSuit.DIAMONDS, J), card(CardSuit.CLUBS, 10)),
            listOf(card(CardSuit.HEARTS, J), card(CardSuit.CLUBS, 12)),
            listOf(card(CardSuit.SPADES, A), card(CardSuit.SPADES, 10)),
        )
        val e = EuchreEngine.forTest(hands, CardSuit.HEARTS, maker = 0)
        e.play(card(CardSuit.HEARTS, A).id)
        e.play(card(CardSuit.DIAMONDS, J).id) // bower gauche : il compte en cœur
        e.play(card(CardSuit.HEARTS, J).id)   // bower droit
        e.play(card(CardSuit.SPADES, A).id)
        assertEquals(1, e.tricksWon[2])
    }

    @Test fun JouerSeulLaisseLePartenaireDeCoteEtRapporteQuatrePoints() {
        val hands = listOf(
            listOf(card(CardSuit.HEARTS, J), card(CardSuit.DIAMONDS, J), card(CardSuit.HEARTS, A), card(CardSuit.HEARTS, 13), card(CardSuit.HEARTS, 12)),
            listOf(card(CardSuit.CLUBS, 9), card(CardSuit.CLUBS, 10), card(CardSuit.CLUBS, 12), card(CardSuit.CLUBS, 13), card(CardSuit.CLUBS, A)),
            listOf(card(CardSuit.DIAMONDS, 9), card(CardSuit.DIAMONDS, 10), card(CardSuit.DIAMONDS, 12), card(CardSuit.DIAMONDS, 13), card(CardSuit.DIAMONDS, A)),
            listOf(card(CardSuit.SPADES, 9), card(CardSuit.SPADES, 10), card(CardSuit.SPADES, 12), card(CardSuit.SPADES, 13), card(CardSuit.SPADES, A)),
        )
        val e = EuchreEngine.forTest(hands, CardSuit.HEARTS, maker = 0, alone = true)
        assertEquals(0, e.hands[2].size)
        e.play(card(CardSuit.HEARTS, J).id)
        assertEquals(1, e.current) // le siège 2 est de côté : on passe au 1…
        e.play(e.legalMoves()[0])
        assertEquals(3, e.current) // …puis directement au 3
        e.play(e.legalMoves()[0])
        e.drainEvents()
        // Le pli à trois cartes est ramassé par le siège 0, qui rejoue.
        assertEquals(0, e.current)
        assertEquals(1, e.tricksWon[0])
        while (e.handNo == 0) {
            val m = e.legalMoves().let { moves -> if (e.current == 0) moves.maxBy { PlayingCard(it).highRank } else moves[0] }
            e.play(m)
            e.drainEvents()
        }
        assertEquals(4, e.teamScore[0])
        assertEquals(0, e.teamScore[1])
    }

    @Test fun uneEquipeQuiNeFaitPasTroisPlisEstEuchreeEtDonneDeuxPoints() {
        // L'atout est cœur, fait par le siège 0 : il n'a que des petites cartes et ne ramasse rien.
        val hands = listOf(
            listOf(card(CardSuit.CLUBS, 9), card(CardSuit.CLUBS, 10), card(CardSuit.DIAMONDS, 9), card(CardSuit.DIAMONDS, 10), card(CardSuit.SPADES, 9)),
            listOf(card(CardSuit.HEARTS, J), card(CardSuit.HEARTS, A), card(CardSuit.HEARTS, 13), card(CardSuit.HEARTS, 12), card(CardSuit.HEARTS, 10)),
            listOf(card(CardSuit.CLUBS, 12), card(CardSuit.CLUBS, 13), card(CardSuit.DIAMONDS, 12), card(CardSuit.DIAMONDS, 13), card(CardSuit.SPADES, 10)),
            listOf(card(CardSuit.CLUBS, A), card(CardSuit.DIAMONDS, A), card(CardSuit.SPADES, A), card(CardSuit.SPADES, 12), card(CardSuit.SPADES, 13)),
        )
        // Le siège 1 (équipe 1) ouvre avec ses atouts et rafle tout.
        val e = EuchreEngine.forTest(hands, CardSuit.HEARTS, maker = 0, leader = 1)
        while (e.handNo == 0) {
            val m = e.legalMoves().let { moves -> if (e.current == 1) moves.maxBy { PlayingCard(it).highRank } else moves[0] }
            e.play(m)
            e.drainEvents()
        }
        assertEquals(0, e.teamScore[0])
        assertEquals(2, e.teamScore[1])
    }

    @Test fun unePartieSeTermineAvecLesTroisNiveaux() {
        for (level in CardLevel.EASY..CardLevel.HARD) {
            val games = if (level == CardLevel.HARD) 3 else 25
            for (seed in 1L..games) {
                val e = playGame(seed, IntArray(4) { level }, Random(seed))
                assertTrue(e.isOver)
                assertTrue(e.teamScore.max() >= EuchreEngine.DEFAULT_LIMIT)
                for (w in e.winners()) assertTrue((w + 2) % 4 in e.winners())
            }
        }
    }

    @Test fun unePartieSeRejoueALIdentiqueDepuisSaGraineEtSesCoups() {
        for (seed in 1L..5L) {
            val moves = ArrayList<Int>()
            val played = playGame(seed, IntArray(4) { CardLevel.MEDIUM }, Random(seed), moves)
            val again = EuchreEngine(seed)
            for (m in moves) again.play(m)
            assertTrue(again.isOver)
            assertEquals(played.teamScore.toList(), again.teamScore.toList())
        }
    }

    @Test fun uneCopieNAffecteJamaisLaPartie() {
        val e = EuchreEngine(3)
        e.play(EuchreEngine.ORDER)
        val before = e.hands.map { it.toList() }
        val sim = e.copy()
        while (!sim.isOver && sim.handNo == e.handNo) sim.play(sim.aiMove(CardLevel.MEDIUM, Random(1)))
        assertEquals(before, e.hands.map { it.toList() })
        assertEquals(EuchreEngine.DISCARD, e.phase)
    }

    @Test fun lIAMoyenneBatLIAFacileParEquipe() {
        var wins = 0
        val games = 24
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY, CardLevel.MEDIUM, CardLevel.EASY), Random(seed))
            if (0 in e.winners()) wins++
        }
        println("Euchre : moyen contre facile $wins/$games")
        assertTrue("l'IA moyenne devrait dominer ($wins/$games)", wins >= games * 2 / 3)
    }

    @Test fun lIADifficileNEstPasPireQueLaMoyenne() {
        var wins = 0
        val games = 10
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.HARD, CardLevel.MEDIUM, CardLevel.HARD, CardLevel.MEDIUM), Random(seed))
            if (0 in e.winners()) wins++
        }
        println("Euchre : difficile contre moyen $wins/$games")
        assertTrue("l'IA difficile perd trop ($wins/$games)", wins >= games / 2 - 1)
    }
}
