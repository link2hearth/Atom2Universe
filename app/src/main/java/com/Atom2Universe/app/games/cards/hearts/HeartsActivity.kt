package com.Atom2Universe.app.games.cards.hearts

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
 * Dame de pique : toi contre trois adversaires. Au passage, on touche trois cartes puis le bouton ;
 * ensuite un premier toucher soulève une carte jouable, un second la pose.
 */
class HeartsActivity : CardGameActivity() {

    override val gameKey = "hearts"
    override val titleRes = R.string.hearts_title
    override val rulesRes = R.string.hearts_rules

    private val game: HeartsEngine get() = engine as HeartsEngine

    override fun createEngine(seed: Long): CardEngine = HeartsEngine(seed)

    override fun rulesText(): CharSequence = getString(rulesRes, HeartsEngine.DEFAULT_LIMIT)

    override fun buildScene(): TableScene {
        val g = game
        val seats = (0 until 4).map { s ->
            val taken = g.handPointsOf(s)
            SeatInfo(
                name = seatName(s, 4),
                handSize = g.hands[s].size,
                hand = if (s == 0) Cards.sortedForHand(g.hands[0]) else null,
                lifted = if (s == 0) lifted.toSet() else emptySet(),
                playable = if (s == 0 && !g.passing) playableCards() else null,
                marked = if (s == 0 && !g.passing && g.trickNo == 0) g.received else emptySet(),
                caption = if (taken > 0) getString(R.string.hearts_caption, g.totals[s], taken) else g.totals[s].toString(),
                active = g.current == s,
            )
        }
        return TableScene(seats, trick = g.trick.toList())
    }

    override fun onHumanCard(card: PlayingCard) {
        val g = game
        if (g.passing) {
            // Au passage, on choisit trois cartes : un toucher les soulève ou les repose.
            if (card in lifted) lifted.remove(card) else if (lifted.size < 3) lifted.add(card)
            refresh()
            return
        }
        if (card !in (playableCards() ?: return)) return
        if (card in lifted) submit(card.id) else { lifted.clear(); lifted.add(card); refresh() }
    }

    override fun onHumanDrop(card: PlayingCard) {
        if (game.passing) {
            // Au passage, glisser une carte vers la table la met de côté.
            if (card !in lifted && lifted.size < 3) { lifted.add(card); refresh() }
            return
        }
        if (card in (playableCards() ?: return)) submit(card.id)
    }

    override fun controls(): List<Control> {
        val g = game
        if (!g.passing) return emptyList()
        val label = when (g.passShift) {
            1 -> R.string.hearts_pass_left
            -1 -> R.string.hearts_pass_right
            else -> R.string.hearts_pass_across
        }
        return listOf(Control(getString(label), enabled = lifted.size == 3, primary = true) {
            submit(HeartsEngine.passMove(lifted.toList()))
        })
    }

    override fun statusText(): String = getString(R.string.hearts_hand, game.handNo + 1)

    override fun recordValue(): Int? = if (game.isOver) game.totals[0] else null
    override val lowerRecordIsBetter = true
}

/** La tuile du hub : la dame de pique parmi des cœurs. */
class HeartsArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFFEF5350.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, listOf(
            PlayingCard.of(CardSuit.HEARTS, 10),
            PlayingCard.of(CardSuit.HEARTS, PlayingCard.ACE),
            HeartsEngine.QUEEN_OF_SPADES,
            PlayingCard.of(CardSuit.HEARTS, PlayingCard.KING),
            PlayingCard.of(CardSuit.HEARTS, 5),
        ), spread = 12f)
    }
}
