package com.Atom2Universe.app.games.cards

import com.Atom2Universe.app.games.cards.ginrummy.GinMelds
import com.Atom2Universe.app.games.cards.ginrummy.GinRummyEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Garde-fous du Gin rami : combinaisons et bois mort, tour de jeu, frapper, gin, contre, partie complète, IA. */
class CardGamesGinRummyTest {

    private val C = CardSuit.CLUBS
    private val D = CardSuit.DIAMONDS
    private val H = CardSuit.HEARTS
    private val S = CardSuit.SPADES

    private fun card(suit: CardSuit, rank: Int) = PlayingCard.of(suit, rank)
    private fun hand(cards: List<PlayingCard>) = cards
    private fun mask(cards: List<PlayingCard>) = GinMelds.maskOf(cards)

    // ── Combinaisons ────────────────────────────────────────────────────────────────────────

    @Test fun uneSuiteEtUnBrelanNeLaissentQueLeBoisMort() {
        val layout = GinMelds.best(mask(listOf(card(H, 1), card(H, 2), card(H, 3), card(S, 7), card(D, 7), card(C, 7), card(S, 13))))
        assertEquals(10, layout.deadwood) // le roi
        assertEquals(2, layout.melds.size)
    }

    @Test fun lAsEstBasEtNeFaitPasLeTourDuRoi() {
        assertEquals(0, GinMelds.deadwood(mask(listOf(card(S, 1), card(S, 2), card(S, 3)))))
        assertEquals(10 + 10 + 1, GinMelds.deadwood(mask(listOf(card(S, 12), card(S, 13), card(S, 1)))))
    }

    @Test fun lesValeursDuBoisMort() {
        assertEquals(1, GinMelds.deadwoodValue(card(S, 1)))
        assertEquals(9, GinMelds.deadwoodValue(card(S, 9)))
        assertEquals(10, GinMelds.deadwoodValue(card(S, 10)))
        assertEquals(10, GinMelds.deadwoodValue(card(S, 13)))
    }

    @Test fun leMeilleurArrangementEstChoisiQuandUneCarteSert_aDeuxCombinaisons() {
        // 5♠ 6♠ 7♠ et 7♥ 7♦ 7♣ : le 7♠ ne peut servir qu'une fois. Le mieux : suite 5-6-7 de pique (le 7♥ 7♦ 7♣ reste un brelan).
        val m = mask(listOf(card(S, 5), card(S, 6), card(S, 7), card(H, 7), card(D, 7), card(C, 7)))
        assertEquals(0, GinMelds.deadwood(m))
        // Sans le 7♣, on choisit entre la suite (et deux 7 perdus = 14) et le brelan (et 5 + 6 = 11 perdus).
        val m2 = mask(listOf(card(S, 5), card(S, 6), card(S, 7), card(H, 7), card(D, 7)))
        assertEquals(14.coerceAtMost(11), GinMelds.deadwood(m2))
    }

    @Test fun onChargeSurLesCombinaisonsDuFrappeurEnChaine() {
        val melds = listOf(mask(listOf(card(S, 5), card(S, 6), card(S, 7))))
        val rest = GinMelds.layOffAll(melds, mask(listOf(card(S, 8), card(S, 9), card(H, 2))))
        assertEquals(mask(listOf(card(H, 2))), rest) // le 8 puis le 9 se chargent
        val set = listOf(mask(listOf(card(S, 7), card(D, 7), card(C, 7))))
        assertEquals(0L, GinMelds.layOffAll(set, mask(listOf(card(H, 7)))))
    }

    // ── Règles ──────────────────────────────────────────────────────────────────────────────

    @Test fun auDebutOnPeutPrendreLaCarteRetourneeOuPasser() {
        val e = GinRummyEngine(1)
        assertEquals(GinRummyEngine.FIRST, e.phase)
        assertTrue(e.hands.all { it.size == 10 })
        assertEquals(1, e.discard.size)
        assertEquals(31, e.stock.size)
        assertEquals(listOf(GinRummyEngine.DRAW_DISCARD, GinRummyEngine.PASS), e.legalMoves().toList())
    }

    @Test fun siLesDeuxPassentLeNonDonneurPiocheAlaPioche() {
        val e = GinRummyEngine(1)
        val first = e.current
        e.play(GinRummyEngine.PASS)
        assertEquals(e.dealer, e.current)
        e.play(GinRummyEngine.PASS)
        assertEquals(first, e.current)
        assertEquals(GinRummyEngine.DRAW, e.phase)
        assertEquals(listOf(GinRummyEngine.DRAW_STOCK), e.legalMoves().toList())
    }

    @Test fun onNeRejetteJamaisLaCarteQuOnVientDePrendreSurLaDefausse() {
        val e = GinRummyEngine(1)
        val up = e.discard.last()
        e.play(GinRummyEngine.DRAW_DISCARD)
        assertEquals(GinRummyEngine.DISCARD, e.phase)
        assertEquals(11, e.hands[e.current].size)
        assertFalse(up.id in e.legalMoves().toList())
        assertTrue(e.drainEvents().any { it is CardEvent.Draw && it.fromDiscard })
    }

    @Test fun onNePeutFrapperQueAvecDixPointsDeBoisMortOuMoins() {
        val knocker = hand(listOf(card(H, 1), card(H, 2), card(H, 3), card(S, 7), card(D, 7), card(C, 7), card(C, 9), card(C, 10), card(C, 11), card(S, 4)))
        val other = hand(listOf(card(S, 13), card(H, 12), card(D, 10), card(C, 8), card(S, 6), card(H, 4), card(D, 3), card(C, 2), card(H, 9), card(D, 5)))
        val e = GinRummyEngine.forTest(listOf(knocker, other), discard = listOf(card(D, 12)), stock = List(8) { card(S, 13 - it % 3) }.distinct() + card(H, 13))
        e.play(GinRummyEngine.DRAW_STOCK) // le roi de cœur
        val knocks = e.legalMoves().filter { it >= GinRummyEngine.KNOCK_BASE }
        // Écarter le roi laisse 4 de bois mort : frapper est permis ; écarter le 4 de pique laisse 10 : permis aussi.
        assertTrue(GinRummyEngine.KNOCK_BASE + card(H, 13).id in knocks)
        assertTrue(GinRummyEngine.KNOCK_BASE + card(S, 4).id in knocks)
        // Écarter une carte d'un brelan laisse 4 + 10 + les autres : trop.
        assertFalse(GinRummyEngine.KNOCK_BASE + card(S, 7).id in knocks)
    }

    @Test fun ginRapporteLEcartPlusVingt() {
        val knocker = hand(listOf(card(H, 1), card(H, 2), card(H, 3), card(S, 7), card(D, 7), card(C, 7), card(C, 9), card(C, 10), card(C, 11), card(C, 12)))
        val other = hand(listOf(card(S, 13), card(H, 12), card(D, 10), card(C, 8), card(S, 6), card(H, 4), card(D, 3), card(C, 2), card(H, 9), card(D, 5)))
        val e = GinRummyEngine.forTest(listOf(knocker, other), discard = listOf(card(D, 12)), stock = listOf(card(S, 2), card(S, 3), card(S, 13)))
        e.play(GinRummyEngine.DRAW_STOCK)
        e.play(GinRummyEngine.KNOCK_BASE + card(S, 13).id)
        // Le bois mort de l'autre : 10+10+10+8+6+4+3+2+9+5 = 67 (aucune charge possible au gin).
        assertEquals(GinRummyEngine.KIND_GIN, e.lastKind)
        assertEquals(87, e.totals[0])
        assertEquals(GinRummyEngine.SHOWDOWN, e.phase)
        assertEquals(0, e.current)
        assertEquals(listOf(GinRummyEngine.CONTINUE), e.legalMoves().toList())
        assertTrue(e.drainEvents().any { it is CardEvent.Lay })
    }

    @Test fun frapperRapporteLEcartApresLaChargeDeLAutre() {
        val knocker = hand(listOf(card(H, 1), card(H, 2), card(H, 3), card(S, 7), card(D, 7), card(C, 7), card(C, 9), card(C, 10), card(C, 11), card(S, 4)))
        val other = hand(listOf(card(S, 13), card(H, 12), card(D, 10), card(C, 8), card(S, 6), card(H, 4), card(D, 3), card(C, 2), card(H, 9), card(D, 5)))
        val e = GinRummyEngine.forTest(listOf(knocker, other), discard = listOf(card(D, 12)), stock = listOf(card(S, 2), card(S, 3), card(H, 13)))
        e.play(GinRummyEngine.DRAW_STOCK)
        e.play(GinRummyEngine.KNOCK_BASE + card(H, 13).id)
        // L'autre : 67 de bois mort, moins le 4♥ (suite A-2-3 de cœur) et le 8♣ (suite 9-10-V de trèfle) chargés = 55 ;
        // le frappeur garde le 4♠ : 4. Il marque 55 − 4 = 51.
        assertEquals(GinRummyEngine.KIND_KNOCK, e.lastKind)
        assertEquals(51, e.totals[0])
        assertEquals(0, e.totals[1])
        assertEquals(55, e.shownDeadwood)
    }

    @Test fun uneContreDonneLEcartPlusDixAuDefenseur() {
        val knocker = hand(listOf(card(H, 1), card(H, 2), card(H, 3), card(S, 7), card(D, 7), card(C, 7), card(C, 9), card(C, 10), card(C, 11), card(S, 8)))
        // Le défenseur n'a que 2 de bois mort (le 2 de trèfle).
        val other = hand(listOf(card(D, 1), card(C, 1), card(S, 1), card(H, 5), card(H, 6), card(H, 7), card(D, 9), card(D, 10), card(D, 11), card(C, 2)))
        val e = GinRummyEngine.forTest(listOf(knocker, other), discard = listOf(card(D, 12)), stock = listOf(card(S, 2), card(S, 3), card(H, 13)))
        e.play(GinRummyEngine.DRAW_STOCK)
        e.play(GinRummyEngine.KNOCK_BASE + card(H, 13).id)
        assertEquals(GinRummyEngine.KIND_UNDERCUT, e.lastKind)
        assertEquals(0, e.totals[0])
        assertEquals(8 - 2 + 10, e.totals[1])
    }

    @Test fun quandLaPiocheEstAPresqueVideLaMancheEstNulle() {
        val a = hand(listOf(card(H, 1), card(S, 3), card(D, 5), card(C, 7), card(H, 9), card(S, 11), card(D, 13), card(C, 2), card(H, 4), card(S, 6)))
        val b = hand(listOf(card(H, 2), card(S, 4), card(D, 6), card(C, 8), card(H, 10), card(S, 12), card(D, 1), card(C, 3), card(H, 5), card(S, 7)))
        val e = GinRummyEngine.forTest(listOf(a, b), discard = listOf(card(D, 12)), stock = listOf(card(C, 13), card(C, 12), card(C, 11)))
        e.play(GinRummyEngine.DRAW_STOCK) // il reste 2 cartes
        e.play(e.legalMoves().first { it < 52 }) // on écarte sans frapper
        assertEquals(GinRummyEngine.KIND_DRAW, e.lastKind)
        assertEquals(GinRummyEngine.SHOWDOWN, e.phase)
        assertEquals(listOf(0, 0), e.totals.toList())
    }

    // ── Parties ─────────────────────────────────────────────────────────────────────────────

    private fun playGame(seed: Long, levels: IntArray, random: Random, moves: MutableList<Int>? = null): GinRummyEngine {
        val e = GinRummyEngine(seed)
        var plies = 0
        while (!e.isOver) {
            assertTrue("la partie ne finit pas (graine $seed)", plies++ < 6000)
            if (e.phase != GinRummyEngine.SHOWDOWN) assertEquals(52, e.hands.sumOf { it.size } + e.stock.size + e.discard.size)
            val m = e.aiMove(levels[e.current], random)
            moves?.add(m)
            e.play(m)
            e.drainEvents()
        }
        return e
    }

    @Test fun unePartieSeTermineAvecLesNiveauxFacileEtMoyen() {
        for (level in CardLevel.EASY..CardLevel.MEDIUM) {
            for (seed in 1L..4L) {
                val e = playGame(seed, IntArray(2) { level }, Random(seed))
                assertTrue(e.isOver)
                assertTrue(e.winners().isNotEmpty())
            }
        }
    }

    @Test fun unePartieSeRejoueALIdentiqueDepuisSaGraineEtSesCoups() {
        for (seed in 1L..4L) {
            val moves = ArrayList<Int>()
            val played = playGame(seed, IntArray(2) { CardLevel.MEDIUM }, Random(seed), moves)
            val again = GinRummyEngine(seed)
            for (m in moves) again.play(m)
            assertTrue(again.isOver)
            assertEquals(played.totals.toList(), again.totals.toList())
        }
    }

    @Test fun uneCopieNAffecteJamaisLaPartie() {
        val e = GinRummyEngine(3)
        val before = e.hands.map { it.toList() }
        val sim = e.copy()
        repeat(12) { if (!sim.isOver) sim.play(sim.aiMove(CardLevel.MEDIUM, Random(1))) }
        assertEquals(before, e.hands.map { it.toList() })
        assertEquals(GinRummyEngine.FIRST, e.phase)
    }

    @Test fun lIAMoyenneBatLIAFacile() {
        var wins = 0
        val games = 12
        for (seed in 1L..games) {
            val e = playGame(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY), Random(seed))
            if (0 in e.winners()) wins++
        }
        println("Gin rami : moyen contre facile $wins/$games")
        assertTrue("l'IA moyenne devrait dominer ($wins/$games)", wins >= games * 2 / 3)
    }

    @Test fun lIADifficileJoueToujoursUnCoupPermis() {
        // Un seul coup par phase (le niveau difficile est le plus coûteux : pas de partie entière ici).
        val e = GinRummyEngine(2)
        repeat(8) {
            if (e.isOver) return
            val m = e.aiMove(CardLevel.HARD, Random(it.toLong()))
            assertTrue(m in e.legalMoves().toList())
            e.play(m)
            e.drainEvents()
        }
    }
}
