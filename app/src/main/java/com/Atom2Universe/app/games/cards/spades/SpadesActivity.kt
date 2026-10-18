package com.Atom2Universe.app.games.cards.spades

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.CardArtwork
import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardGameActivity
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.cards.SeatInfo
import com.Atom2Universe.app.games.cards.TableScene
import com.Atom2Universe.app.games.kit.KitPalette

/**
 * Spades : toi et le siège d'en face contre les deux autres. On annonce d'abord ses plis avec les
 * boutons − et +, puis un premier toucher soulève une carte jouable, un second la pose.
 */
class SpadesActivity : CardGameActivity() {

    override val gameKey = "spades"
    override val titleRes = R.string.spades_title
    override val rulesRes = R.string.spades_rules

    private val game: SpadesEngine get() = engine as SpadesEngine

    /** L'annonce en cours de choix, entre 0 (nil) et 13. */
    private var bidChoice = 3

    override fun createEngine(seed: Long): CardEngine = SpadesEngine(seed)

    override fun rulesText(): CharSequence = getString(rulesRes, SpadesEngine.DEFAULT_LIMIT)

    override fun buildScene(): TableScene {
        val g = game
        val seats = (0 until 4).map { s ->
            SeatInfo(
                name = seatName(s, 4),
                handSize = g.hands[s].size,
                hand = if (s == 0) Cards.sortedForHand(g.hands[0]) else null,
                lifted = if (s == 0) lifted.toSet() else emptySet(),
                playable = if (s == 0 && !g.bidding) playableCards() else null,
                caption = if (g.bids[s] == SpadesEngine.NO_BID) null else "${g.tricksWon[s]}/${bidText(g.bids[s])}",
                active = g.current == s,
            )
        }
        return TableScene(seats, trick = g.trick.toList())
    }

    private fun bidText(bid: Int) = if (bid == 0) getString(R.string.spades_nil_mark) else bid.toString()

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
        if (!game.bidding) return emptyList()
        val label = if (bidChoice == 0) getString(R.string.spades_nil) else getString(R.string.spades_bid, bidChoice)
        return listOf(
            Control("−", enabled = bidChoice > 0) { bidChoice--; refresh() },
            Control(label, primary = true) { submit(SpadesEngine.BID_BASE + bidChoice) },
            Control("+", enabled = bidChoice < 13) { bidChoice++; refresh() },
        )
    }

    override fun statusText(): String = getString(R.string.cards_team_score, game.teamScore[0], game.teamScore[1])

    override fun recordValue(): Int? = if (game.isOver) game.teamScore[0].coerceAtLeast(0) else null
}

/** La tuile du hub : l'as, le roi, la dame, le valet et le dix de pique. */
class SpadesArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFF7E57C2.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, listOf(10, PlayingCard.JACK, PlayingCard.QUEEN, PlayingCard.KING, PlayingCard.ACE)
            .map { PlayingCard.of(CardSuit.SPADES, it) }, spread = 12f)
    }
}
