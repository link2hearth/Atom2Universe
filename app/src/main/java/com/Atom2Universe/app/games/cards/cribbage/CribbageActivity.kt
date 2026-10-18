package com.Atom2Universe.app.games.cards.cribbage

import android.content.Context
import android.graphics.Canvas
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.CardArtwork
import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardGameActivity
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.PileInfo
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.cards.SeatInfo
import com.Atom2Universe.app.games.cards.TableScene
import com.Atom2Universe.app.games.kit.KitPalette

/**
 * Le Cribbage : toi contre l'ordinateur. On touche (ou on glisse) deux cartes pour le crib, puis le
 * bouton ; on pose ensuite ses cartes une à une, et chaque compte de main s'affiche au centre avant
 * de passer au suivant avec « Continuer ».
 */
class CribbageActivity : CardGameActivity() {

    override val gameKey = "cribbage"
    override val titleRes = R.string.cribbage_title
    override val rulesRes = R.string.cribbage_rules

    private val game: CribbageEngine get() = engine as CribbageEngine

    override fun createEngine(seed: Long): CardEngine = CribbageEngine(seed)

    override fun rulesText(): CharSequence = getString(rulesRes, CribbageEngine.DEFAULT_LIMIT)

    private fun caption(seat: Int): String {
        val g = game
        val base = if (g.gained[seat] > 0) getString(R.string.cards_score_gain, g.totals[seat], g.gained[seat]) else g.totals[seat].toString()
        return if (seat == g.dealer) "$base · ${getString(R.string.cribbage_crib)}" else base
    }

    override fun buildScene(): TableScene {
        val g = game
        val peg = g.phase == CribbageEngine.PEG
        val show = g.phase == CribbageEngine.SHOW
        val seats = listOf(
            SeatInfo(
                name = seatName(0, 2),
                handSize = g.hands[0].size,
                hand = g.hands[0].sortedWith(compareBy({ it.rank }, { it.suit })),
                lifted = lifted.toSet(),
                playable = if (peg) playableCards() else null,
                caption = caption(0),
                active = g.current == 0 && !show,
            ),
            SeatInfo(
                name = seatName(1, 2),
                handSize = g.hands[1].size,
                caption = caption(1),
                active = g.current == 1,
            ),
        )
        return TableScene(
            seats,
            pile = PileInfo(stock = g.stockSize, top = g.starter),
            row = when {
                peg -> g.pile.toList()
                show -> g.showRow()
                else -> emptyList()
            },
            rowBadge = when {
                peg -> g.count.toString()
                show -> g.showPoints.toString()
                else -> null
            },
            rowCapacity = 8,
            hasRow = true,
        )
    }

    override fun onHumanCard(card: PlayingCard) {
        when (game.phase) {
            CribbageEngine.DISCARD -> {
                // Deux cartes pour le crib : un toucher les soulève ou les repose.
                if (card in lifted) lifted.remove(card) else if (lifted.size < 2) lifted.add(card)
                refresh()
            }
            CribbageEngine.PEG -> {
                if (card !in (playableCards() ?: return)) return
                if (card in lifted) submit(card.id) else { lifted.clear(); lifted.add(card); refresh() }
            }
        }
    }

    override fun onHumanDrop(card: PlayingCard) {
        when (game.phase) {
            CribbageEngine.DISCARD -> if (card !in lifted && lifted.size < 2) { lifted.add(card); refresh() }
            CribbageEngine.PEG -> if (card in (playableCards() ?: return)) submit(card.id)
        }
    }

    override fun controls(): List<Control> = when (game.phase) {
        CribbageEngine.DISCARD -> listOf(Control(getString(R.string.cribbage_to_crib), enabled = lifted.size == 2, primary = true) {
            submit(CribbageEngine.discardMove(lifted.toList()))
        })
        CribbageEngine.SHOW -> listOf(Control(getString(R.string.cribbage_continue), primary = true) { submit(CribbageEngine.CONTINUE) })
        else -> emptyList()
    }

    /** Pendant les comptes : à qui est la main qu'on compte, ou le crib. */
    override fun statusText(): String? {
        val g = game
        if (g.phase != CribbageEngine.SHOW) return null
        return if (g.showStage == 2) getString(R.string.cribbage_crib) else seatName(g.showOwner(), 2)
    }

    override fun recordValue(): Int? = if (game.isOver) game.totals[0] else null
}

/** La tuile du hub : la main de 29, trois 5, le valet et le 5 retourné. */
class CribbageArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFFD4A24C.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, listOf(
            PlayingCard.of(CardSuit.CLUBS, 5),
            PlayingCard.of(CardSuit.DIAMONDS, 5),
            PlayingCard.of(CardSuit.HEARTS, PlayingCard.JACK),
            PlayingCard.of(CardSuit.SPADES, 5),
            PlayingCard.of(CardSuit.HEARTS, 5),
        ), spread = 12f)
    }
}
