package com.Atom2Universe.app.games.cards

import com.Atom2Universe.app.games.cards.crazyeights.CrazyEightsEngine
import com.Atom2Universe.app.games.cards.hearts.HeartsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Garde-fous des jeux de cartes du kit : les règles tiennent, une partie jouée par les IA se
 * termine, les points sont justes, et une partie se rejoue à l'identique depuis sa graine et ses coups.
 */
class CardGamesTest {

    // ── Le paquet ───────────────────────────────────────────────────────────────────────────

    @Test fun lePaquetA52CartesDistinctes() {
        val deck = Cards.standardDeck()
        assertEquals(52, deck.size)
        assertEquals(52, deck.map { it.id }.toSet().size)
        for (suit in CardSuit.entries) assertEquals(13, deck.count { it.suit == suit })
        assertEquals(CardSuit.CLUBS, PlayingCard.of(CardSuit.CLUBS, 2).suit)
        assertEquals(2, PlayingCard.of(CardSuit.CLUBS, 2).rank)
        assertEquals(14, PlayingCard.of(CardSuit.HEARTS, PlayingCard.ACE).highRank)
    }

    @Test fun uneMainSeRangeEnCouleursAlternees() {
        val hand = listOf(
            PlayingCard.of(CardSuit.DIAMONDS, 3), PlayingCard.of(CardSuit.SPADES, 9),
            PlayingCard.of(CardSuit.CLUBS, 5), PlayingCard.of(CardSuit.HEARTS, 2),
            PlayingCard.of(CardSuit.SPADES, 4),
        )
        val sorted = Cards.sortedForHand(hand)
        assertEquals(listOf(CardSuit.SPADES, CardSuit.SPADES, CardSuit.HEARTS, CardSuit.CLUBS, CardSuit.DIAMONDS), sorted.map { it.suit })
        assertEquals(4, sorted[0].rank)
        assertEquals(9, sorted[1].rank)
    }

    // ── Huit américain ──────────────────────────────────────────────────────────────────────

    /** Joue la manche jusqu'au bout ; chaque siège a son niveau. Vérifie les règles à chaque coup. */
    private fun playCrazyEights(seed: Long, levels: IntArray, random: Random): CrazyEightsEngine {
        val e = CrazyEightsEngine(seed)
        var plies = 0
        while (!e.isOver) {
            assertTrue("la manche ne finit pas (graine $seed)", plies++ < 3000)
            // Les 52 cartes sont toujours quelque part.
            assertEquals(52, e.hands.sumOf { it.size } + e.stockSize + e.discardSize)
            val seat = e.current
            val legal = e.legalMoves()
            assertTrue(legal.isNotEmpty())
            val canPlayACard = e.hands[seat].any {
                it.rank == 8 || it.suit == e.activeSuit || it.rank == e.topCard.rank
            }
            // On ne pioche ni ne passe que faute de carte jouable.
            if (legal.any { it == CrazyEightsEngine.DRAW || it == CrazyEightsEngine.PASS }) assertFalse(canPlayACard)
            else assertTrue(canPlayACard)
            e.play(e.aiMove(levels[seat], random))
            e.drainEvents()
        }
        return e
    }

    @Test fun uneMancheDeHuitAmericainSeTermineAvecLesTroisNiveaux() {
        for (level in CardLevel.EASY..CardLevel.HARD) {
            val games = if (level == CardLevel.HARD) 6 else 40
            for (seed in 1L..games) {
                val e = playCrazyEights(seed, IntArray(4) { level }, Random(seed))
                assertTrue(e.isOver)
                assertEquals(1, e.winners().size)
            }
        }
    }

    @Test fun leVainqueurMarqueLesCartesRestantesAuxAutres() {
        for (seed in 1L..40L) {
            val e = playCrazyEights(seed, IntArray(4) { CardLevel.MEDIUM }, Random(seed))
            val winner = e.winner
            if (e.hands[winner].isEmpty()) {
                val others = (0 until 4).filter { it != winner }.sumOf { s -> e.hands[s].sumOf { CrazyEightsEngine.pointValue(it) } }
                assertEquals(others, e.roundPoints)
                assertEquals(e.roundPoints, e.scores[winner])
            }
        }
    }

    @Test fun lesCartesValentLeursPoints() {
        assertEquals(50, CrazyEightsEngine.pointValue(PlayingCard.of(CardSuit.HEARTS, 8)))
        assertEquals(10, CrazyEightsEngine.pointValue(PlayingCard.of(CardSuit.CLUBS, PlayingCard.KING)))
        assertEquals(10, CrazyEightsEngine.pointValue(PlayingCard.of(CardSuit.CLUBS, 10)))
        assertEquals(1, CrazyEightsEngine.pointValue(PlayingCard.of(CardSuit.SPADES, PlayingCard.ACE)))
        assertEquals(7, CrazyEightsEngine.pointValue(PlayingCard.of(CardSuit.DIAMONDS, 7)))
    }

    @Test fun unHuitChoisitLaCouleurASuivre() {
        for (seed in 1L..200L) {
            val e = CrazyEightsEngine(seed)
            val eight = e.legalMoves().firstOrNull { it >= CrazyEightsEngine.EIGHT_BASE } ?: continue
            val card = CrazyEightsEngine.cardOf(eight)!!
            // Un huit s'offre dans les quatre couleurs.
            assertEquals(4, e.legalMoves().count { CrazyEightsEngine.cardOf(it) == card })
            val chosen = CardSuit.entries[(eight - CrazyEightsEngine.EIGHT_BASE) % 4]
            e.play(eight)
            assertEquals(chosen, e.activeSuit)
            return
        }
    }

    @Test fun uneMancheSeRejoueDepuisLaGraineEtLesCoups() {
        for (seed in 1L..15L) {
            val random = Random(seed)
            val first = CrazyEightsEngine(seed)
            val moves = ArrayList<Int>()
            while (!first.isOver) {
                val m = first.aiMove(CardLevel.EASY, random)
                // La copie ne perturbe pas la partie : le rejeu doit rester identique.
                first.copy().aiMove(CardLevel.MEDIUM, random)
                first.play(m)
                moves.add(m)
            }
            val replay = CrazyEightsEngine(seed)
            for (m in moves) replay.play(m)
            assertEquals(first.winner, replay.winner)
            assertEquals(first.roundPoints, replay.roundPoints)
            for (s in 0 until 4) assertEquals(first.hands[s], replay.hands[s])
        }
    }

    @Test fun lIaMoyenneFaitUnPeuMieuxQueLeHasardAuHuitAmericain() {
        var wins = 0
        val games = 400
        for (seed in 1L..games) {
            val e = playCrazyEights(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY, CardLevel.EASY, CardLevel.EASY), Random(seed))
            if (e.winner == 0) wins++
        }
        // Le huit américain est surtout une affaire de chance : au hasard, un siège sur quatre gagne
        // (100 sur 400). La règle simple fait un peu mieux, et pas davantage.
        assertTrue("l'IA moyenne ne gagne que $wins parties sur $games", wins >= 105)
    }

    // ── Dame de pique ───────────────────────────────────────────────────────────────────────

    /** Joue la partie jusqu'au bout et vérifie les règles et les points à chaque coup. */
    private fun playHearts(seed: Long, levels: IntArray, random: Random, limit: Int = HeartsEngine.DEFAULT_LIMIT): HeartsEngine {
        val e = HeartsEngine(seed, limit)
        var plies = 0
        var hand = 0
        while (!e.isOver) {
            assertTrue("la partie ne finit pas (graine $seed)", plies++ < 5000)
            val seat = e.current
            val legal = e.legalMoves()
            assertTrue(legal.isNotEmpty())
            if (!e.passing) checkHeartsRules(e, seat, legal)
            val handBefore = e.handNo
            e.play(e.aiMove(levels[seat], random))
            e.drainEvents()
            if (e.handNo != handBefore || e.isOver) {
                // Une manche vient de finir : ses points valent 26, ou 78 si quelqu'un a pris la lune.
                val sum = e.lastHand.sum()
                assertTrue("manche de $sum points", sum == 26 || sum == 78)
                if (sum == 78) assertEquals(0, e.lastHand[e.moonShooter])
                hand++
            }
        }
        assertTrue(hand >= 1)
        return e
    }

    private fun checkHeartsRules(e: HeartsEngine, seat: Int, legal: IntArray) {
        val hand = e.hands[seat]
        val cards = legal.map { PlayingCard(it) }
        assertTrue(hand.containsAll(cards))
        if (e.trick.isEmpty()) {
            if (e.trickNo == 0) assertEquals(listOf(PlayingCard(HeartsEngine.TWO_OF_CLUBS)), cards)
            else if (!e.heartsBroken && cards.any { it.suit == CardSuit.HEARTS }) assertTrue(hand.all { it.suit == CardSuit.HEARTS })
        } else {
            val led = e.trick[0].second.suit
            if (hand.any { it.suit == led }) assertTrue(cards.all { it.suit == led })
            else if (e.trickNo == 0 && cards.any { HeartsEngine.isPoint(it) }) assertTrue(hand.all { HeartsEngine.isPoint(it) })
        }
    }

    @Test fun uneDonneDeDameDePiqueSeTermineAvecLesTroisNiveaux() {
        for (level in CardLevel.EASY..CardLevel.HARD) {
            val games = if (level == CardLevel.HARD) 2 else 12
            for (seed in 1L..games) {
                val e = playHearts(seed, IntArray(4) { level }, Random(seed))
                assertTrue(e.totals.any { it >= e.limit })
                assertTrue(e.winners().isNotEmpty())
                // Le vainqueur est celui qui a le moins de points.
                for (w in e.winners()) assertEquals(e.totals.min(), e.totals[w])
            }
        }
    }

    @Test fun lePassageDonneTroisCartesAuVoisin() {
        val e = HeartsEngine(7)
        assertTrue(e.passing)
        assertEquals(1, e.passShift) // la première manche se passe à gauche
        val before = Array(4) { e.hands[it].toList() }
        val chosen = Array(4) { before[it].take(3) }
        for (s in 0 until 4) {
            assertEquals(s, e.current)
            e.play(HeartsEngine.passMove(chosen[s]))
        }
        assertFalse(e.passing)
        for (s in 0 until 4) {
            val from = Math.floorMod(s - 1, 4)
            assertEquals(13, e.hands[s].size)
            assertTrue(e.hands[s].containsAll(chosen[from]))
            assertTrue(chosen[s].none { it in e.hands[s] })
        }
        assertEquals(chosen[3].toSet(), e.received)
        // Celui qui tient le 2 de trèfle ouvre, et il n'a pas le choix.
        assertEquals(listOf(HeartsEngine.TWO_OF_CLUBS), e.legalMoves().toList())
    }

    @Test fun lePassageSeFaitDansLeBonSens() {
        // Les manches passent : à gauche, à droite, en face, puis rien.
        val shifts = ArrayList<Int>()
        val e = HeartsEngine(3)
        val random = Random(3)
        var seen = -1
        var guard = 0
        while (!e.isOver && shifts.size < 4 && guard++ < 6000) {
            if (e.handNo != seen) { seen = e.handNo; shifts.add(e.passShift) }
            e.play(e.aiMove(CardLevel.MEDIUM, random))
            e.drainEvents()
        }
        assertEquals(listOf(1, -1, 2, 0).take(shifts.size), shifts)
    }

    @Test fun prendreLaLuneDonne26PointsAuxAutres() {
        // Le siège 0 a tous les trèfles, donc tous les plis ; les autres ne peuvent que se défausser.
        val clubs = (0 until 13).map { PlayingCard.of(CardSuit.CLUBS, it + 1) }
        val diamonds = (0 until 13).map { PlayingCard.of(CardSuit.DIAMONDS, it + 1) }
        val spades = (0 until 13).map { PlayingCard.of(CardSuit.SPADES, it + 1) }
        val hearts = (0 until 13).map { PlayingCard.of(CardSuit.HEARTS, it + 1) }
        val e = HeartsEngine.forTest(listOf(clubs, diamonds, spades, hearts))
        assertEquals(listOf(HeartsEngine.TWO_OF_CLUBS), e.legalMoves().toList())
        while (e.handNo == 3) {
            val legal = e.legalMoves().map { PlayingCard(it) }
            val pick = if (e.current == 0) legal.maxByOrNull { it.highRank }!! else legal.first()
            e.play(pick.id)
            e.drainEvents()
        }
        assertEquals(0, e.moonShooter)
        assertEquals(listOf(0, 26, 26, 26), e.lastHand.toList())
        assertEquals(listOf(0, 26, 26, 26), e.totals.toList())
    }

    @Test fun lIaMoyenneBatLeHasardALaDameDePique() {
        var medium = 0
        var random = 0
        val games = 20
        for (seed in 1L..games) {
            val e = playHearts(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.EASY, CardLevel.EASY, CardLevel.EASY), Random(seed))
            medium += e.totals[0]
            random += (1..3).sumOf { e.totals[it] } / 3
        }
        // Avec les mêmes donnes, la règle simple prend moins de points que le hasard.
        assertTrue("IA moyenne : $medium points, hasard : $random", medium < random)
    }

    @Test fun lIaDifficileNEstJamaisPlusMauvaiseQueLaMoyenne() {
        var hard = 0
        var medium = 0
        for (seed in 1L..4L) {
            val e1 = playHearts(seed, intArrayOf(CardLevel.HARD, CardLevel.MEDIUM, CardLevel.MEDIUM, CardLevel.MEDIUM), Random(seed))
            hard += e1.totals[0]
            val e2 = playHearts(seed, intArrayOf(CardLevel.MEDIUM, CardLevel.MEDIUM, CardLevel.MEDIUM, CardLevel.MEDIUM), Random(seed))
            medium += e2.totals[0]
        }
        // Mesure grossière : la recherche ne doit pas faire pire que la règle simple qu'elle utilise.
        assertTrue("difficile : $hard points, moyen : $medium", hard <= medium + 20)
    }
}
