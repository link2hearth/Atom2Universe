package com.Atom2Universe.app.games.cards.ohhell

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.CardArtwork
import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardGameActivity
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PileInfo
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.cards.SeatInfo
import com.Atom2Universe.app.games.cards.TableScene
import com.Atom2Universe.app.games.kit.KitPalette

/**
 * Oh Hell : chacun pour soi. On annonce d'abord ses plis avec − et +, puis un premier toucher soulève
 * une carte jouable, un second la pose. La carte retournée au centre donne l'atout.
 */
class OhHellActivity : CardGameActivity() {

    override val gameKey = "ohhell"
    override val titleRes = R.string.ohhell_title
    override val rulesRes = R.string.ohhell_rules

    private val game: OhHellEngine get() = engine as OhHellEngine

    /** L'annonce en cours de choix. */
    private var bidChoice = 1

    override fun createEngine(seed: Long): CardEngine = OhHellEngine(seed)

    override fun buildScene(): TableScene {
        val g = game
        val seats = (0 until 4).map { s ->
            SeatInfo(
                name = seatName(s, 4),
                handSize = g.hands[s].size,
                hand = if (s == 0) Cards.sortedForHand(g.hands[0]) else null,
                lifted = if (s == 0) lifted.toSet() else emptySet(),
                playable = if (s == 0 && !g.bidding) playableCards() else null,
                caption = if (g.bids[s] == OhHellEngine.NO_BID) g.totals[s].toString()
                    else "${g.totals[s]} · ${g.tricksWon[s]}/${g.bids[s]}",
                active = g.current == s,
            )
        }
        return TableScene(seats, trick = g.trick.toList(),
            pile = PileInfo(stock = g.stockSize, top = g.trumpCard, suit = g.trump))
    }

    override fun onHumanCard(card: PlayingCard) {
        if (game.bidding) return
        if (card !in (playableCards() ?: return)) return
        if (card in lifted) submit(card.id) else { lifted.clear(); lifted.add(card); refresh() }
    }

    override fun onHumanDrop(card: PlayingCard) {
        if (game.bidding) return
        if (card in (playableCards() ?: return)) submit(card.id)
    }

    override fun controls(): List<Control> {
        val g = game
        if (!g.bidding) return emptyList()
        val choice = bidChoice.coerceIn(0, g.handSize)
        val allowed = g.legalMoves().contains(OhHellEngine.BID_BASE + choice)
        return listOf(
            Control("−", enabled = choice > 0) { bidChoice = choice - 1; refresh() },
            Control(getString(R.string.ohhell_bid, choice), enabled = allowed, primary = true) {
                submit(OhHellEngine.BID_BASE + choice)
            },
            Control("+", enabled = choice < g.handSize) { bidChoice = choice + 1; refresh() },
        )
    }

    override fun statusText(): String = getString(R.string.ohhell_hand, minOf(game.handNo + 1, game.handCount), game.handCount)

    override fun recordValue(): Int? = if (game.isOver) game.totals[0] else null
}

/** La tuile du hub : cinq cartes de quatre couleurs, la plus haute au centre. */
class OhHellArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFFFF7043.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, listOf(
            PlayingCard.of(CardSuit.CLUBS, 7),
            PlayingCard.of(CardSuit.DIAMONDS, PlayingCard.QUEEN),
            PlayingCard.of(CardSuit.SPADES, PlayingCard.ACE),
            PlayingCard.of(CardSuit.HEARTS, PlayingCard.KING),
            PlayingCard.of(CardSuit.CLUBS, 3),
        ), spread = 13f)
    }
}
