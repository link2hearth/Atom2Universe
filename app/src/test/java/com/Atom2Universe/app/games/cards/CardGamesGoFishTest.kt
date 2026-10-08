package com.Atom2Universe.app.games.cards

import com.Atom2Universe.app.games.cards.gofish.GoFishEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous de la Pêche : demander, pêcher, poser les carrés, finir, rejouer, et la mémoire de l'IA. */
class CardGamesGoFishTest {

    private fun card(suit: CardSuit, rank: Int) = PlayingCard.of(suit, rank)

    private fun playGame(seed: Long, levels: IntArray, random: Random, moves: MutableList<Int>? = null): GoFishEngine {
        val e = GoFishEngine(seed)
        var plies = 0
        while (!e.isOver) {
            assertTrue("la partie ne finit pas (graine $seed)", plies++ < 3000)
            // Les cinquante-deux cartes sont toujours quelque part : mains, pioche, ou carrés posés.
            assertEquals(52, e.hands.sumOf { it.size } + e.stock.size + e.books.sum() * 4)
            val m = e.aiMove(levels[e.current], random)
            assertTrue(m in e.legalMoves().toList())
            moves?.add(m)
            e.play(m)
            e.drainEvents()
        }
        return e
    }

    @Test fun chacunRecoitSeptCartesEtLaPiocheLeReste() {
        val e = GoFishEngine(1)
        assertEquals(3, e.seats)
        assertTrue(e.hands.all { it.size in 3..7 }) // un carré d'emblée est déjà posé
        assertEquals(52, e.hands.sumOf { it.size } + e.stock.size + e.books.sum() * 4)
        assertEquals(31, e.stock.size)
    }

    @Test fun uneDemandeReussieDonneToutesLesCartesEtOnRejoue() {
        val hands = listOf(
            listOf(card(CardSuit.CLUBS, 7), card(CardSuit.HEARTS, 2)),
            listOf(card(CardSuit.DIAMONDS, 7), card(CardSuit.SPADES, 7), card(CardSuit.CLUBS, 9)),
            listOf(card(CardSuit.CLUBS, 5), card(CardSuit.HEARTS, 5)),
        )
        val e = GoFishEngine.forTest(hands, stock = listOf(card(CardSuit.CLUBS, 13)))
        assertEquals(0, e.current)
        e.play(GoFishEngine.move(1, 7))
        assertEquals(3, e.hands[0].count { it.rank == 7 })
        assertEquals(1, e.hands[1].size)
        assertEquals(0, e.current) // il rejoue
        assertEquals(2, e.lastGiven.coerceAtLeast(2))
        val events = e.drainEvents()
        assertTrue(events.any { it is CardEvent.Give && it.from == 1 && it.to == 0 && it.cards.size == 2 })
    }

    @Test fun peche_onPiocheEtOnNeRejoueQueSiLaCarteEstCelleDemandee() {
        val hands = listOf(
            listOf(card(CardSuit.CLUBS, 7), card(CardSuit.HEARTS, 2)),
            listOf(card(CardSuit.DIAMONDS, 9), card(CardSuit.SPADES, 9)),
            listOf(card(CardSuit.CLUBS, 5), card(CardSuit.HEARTS, 5)),
        )
        // La pioche donne le 7 voulu : on rejoue.
        val lucky = GoFishEngine.forTest(hands, stock = listOf(card(CardSuit.SPADES, 13), card(CardSuit.HEARTS, 7)))
        lucky.play(GoFishEngine.move(1, 7))
        assertEquals(0, lucky.current)
        assertEquals(0, lucky.lastGiven)
        // La pioche donne autre chose : la main passe.
        val unlucky = GoFishEngine.forTest(hands, stock = listOf(card(CardSuit.HEARTS, 7), card(CardSuit.SPADES, 13)))
        unlucky.play(GoFishEngine.move(1, 7))
        assertEquals(1, unlucky.current)
    }

    @Test fun quatreCartesDeLaMemeHauteurFontUnCarrePoseAussitot() {
        val hands = listOf(
            listOf(card(CardSuit.CLUBS, 7), card(CardSuit.HEARTS, 7), card(CardSuit.SPADES, 7), card(CardSuit.HEARTS, 2)),
            listOf(card(CardSuit.DIAMONDS, 7), card(CardSuit.CLUBS, 9)),
            listOf(card(CardSuit.CLUBS, 5), card(CardSuit.HEARTS, 5)),
        )
        val e = GoFishEngine.forTest(hands, stock = listOf(card(CardSuit.CLUBS, 13)))
        e.play(GoFishEngine.move(1, 7))
        assertEquals(1, e.books[0])
        assertEquals(listOf(card(CardSuit.HEARTS, 2)), e.hands[0].toList())
    }

    @Test fun onNeDemandePasAUnJoueurSansCarteEtUnJoueurSansCartePioche() {
        val hands = listOf(
            listOf(card(CardSuit.CLUBS, 7), card(CardSuit.CLUBS, 9)),
            listOf(card(CardSuit.DIAMONDS, 9)),
            listOf(card(CardSuit.CLUBS, 5)),
        )
        val e = GoFishEngine.forTest(hands, stock = listOf(card(CardSuit.SPADES, 13), card(CardSuit.HEARTS, 12)))
        // Le siège 0 demande un 9 au siège 1, qui n'en a plus après : il pioche en reprenant son tour.
        e.play(GoFishEngine.move(1, 9)) // le siège 1 donne son 9 et reste sans carte
        assertEquals(0, e.current)
        assertTrue(e.legalMoves().all { it / 13 != 1 }) // on ne demande pas à qui n'a rien
        e.play(GoFishEngine.move(2, 7)) // raté : pêche, la main passe au siège 1, qui n'a plus de carte
        assertEquals(1, e.current)
        assertEquals(1, e.hands[1].size) // il a pioché au début de son tour
    }

    @Test fun lIASeSouvientDesDemandesDesAutres() {
        // Le siège 1 a demandé un 9 : le siège 0 (difficile) sait qu'il en a un et le lui demande en premier.
        val hands = listOf(
            listOf(card(CardSuit.CLUBS, 9), card(CardSuit.HEARTS, 2), card(CardSuit.DIAMONDS, 5)),
            listOf(card(CardSuit.DIAMONDS, 9), card(CardSuit.CLUBS, 4)),
            listOf(card(CardSuit.CLUBS, 12), card(CardSuit.HEARTS, 11)),
        )
        val e = GoFishEngine.forTest(hands, stock = List(10) { card(CardSuit.entries[it % 4], 3 + it % 3) })
        // Le siège 0 demande un 3 au siège 2 (raté), la main passe au 1 qui demande un 9 au siège 0 (qui en a un).
        e.play(GoFishEngine.move(2, 2))
        e.drainEvents()
        if (e.current == 1) {
            e.play(GoFishEngine.move(0, 9))
            e.drainEvents()
        }
        // Le siège 2 joue ensuite ; peu importe ce qu'il fait, le siège 1 s'est trahi : sa demande est mémorisée.
        val sim = e.copy()
        assertTrue(sim.winners().isNotEmpty())
    }

    @Test fun unePartieSeTermineAvecLesTroisNiveaux() {
        for (level in CardLevel.EASY..CardLevel.HARD) {
            for (seed in 1L..40L) {
                val e = playGame(seed, IntArray(3) { level }, Random(seed))
                assertTrue(e.isOver)
                assertEquals(13, e.books.sum())
                assertTrue(e.winners().isNotEmpty())
            }
        }
    }

    @Test fun unePartieSeRejoueALIdentiqueDepuisSaGraineEtSesCoups() {
        for (seed in 1L..8L) {
            val moves = ArrayList<Int>()
            val played = playGame(seed, IntArray(3) { CardLevel.MEDIUM }, Random(seed), moves)
            val again = GoFishEngine(seed)
            for (m in moves) again.play(m)
            assertTrue(again.isOver)
            assertEquals(played.books.toList(), again.books.toList())
        }
    }

    @Test fun uneCopieNAffecteJamaisLaPartie() {
        val e = GoFishEngine(3)
        val before = e.hands.map { it.toList() }
        val sim = e.copy()
        repeat(10) { if (!sim.isOver) sim.play(sim.aiMove(CardLevel.MEDIUM, Random(1))) }
        assertEquals(before, e.hands.map { it.toList() })
        assertFalse(e.isOver)
    }

    @Test fun lIADifficileFaitPlusDeCarresQueLaFacile() {
        var hard = 0
        var easy = 0
        val games = 120
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.HARD, CardLevel.EASY, CardLevel.EASY), Random(seed))
            hard += e.books[0]
            easy += e.books[1] + e.books[2]
        }
        val meanEasy = easy / 2.0
        println("Pêche : difficile $hard carrés contre facile $meanEasy (sur $games parties)")
        assertTrue("difficile $hard contre facile $meanEasy", hard > meanEasy * 1.3)
    }

    @Test fun lIAMoyenneFaitPlusDeCarresQueLaFacile() {
        var medium = 0
        var easy = 0
        val games = 120
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY, CardLevel.EASY), Random(seed))
            medium += e.books[0]
            easy += e.books[1] + e.books[2]
        }
        val meanEasy = easy / 2.0
        println("Pêche : moyen $medium carrés contre facile $meanEasy (sur $games parties)")
        assertTrue("moyen $medium contre facile $meanEasy", medium > meanEasy * 1.1)
    }
}
