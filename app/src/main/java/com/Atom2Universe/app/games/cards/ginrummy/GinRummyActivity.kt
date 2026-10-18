package com.Atom2Universe.app.games.cards.ginrummy

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
 * Le Gin rami : toi contre l'ordinateur. Ta main est rangée toute seule (les combinaisons d'abord) et
 * ton bois mort est entouré. On pioche en touchant la pioche ou la défausse, on écarte en glissant une
 * carte vers la table ou en la touchant deux fois ; une carte soulevée propose « Frapper » quand c'est permis.
 */
class GinRummyActivity : CardGameActivity() {

    override val gameKey = "ginrummy"
    override val titleRes = R.string.gin_title
    override val rulesRes = R.string.gin_rules

    private val game: GinRummyEngine get() = engine as GinRummyEngine

    override fun createEngine(seed: Long): CardEngine = GinRummyEngine(seed)

    override fun rulesText(): CharSequence = getString(rulesRes, GinRummyEngine.DEFAULT_LIMIT)

    private fun caption(seat: Int): String {
        val g = game
        val gain = if (g.phase == GinRummyEngine.SHOWDOWN) g.lastHand[seat] else 0
        return if (gain > 0) getString(R.string.cards_score_gain, g.totals[seat], gain) else g.totals[seat].toString()
    }

    override fun buildScene(): TableScene {
        val g = game
        val myTurn = !g.isOver && g.current == HUMAN
        val legal = if (myTurn) g.legalMoves() else IntArray(0)
        val showdown = g.phase == GinRummyEngine.SHOWDOWN
        val seats = listOf(
            SeatInfo(
                name = seatName(0, 2),
                handSize = g.hands[0].size,
                hand = g.arranged(g.hands[0]),
                lifted = lifted.toSet(),
                playable = if (g.phase == GinRummyEngine.DISCARD) playableCards() else null,
                marked = g.deadwoodCards(g.hands[0]).toSet(),
                caption = caption(0),
                active = g.current == 0 && !showdown,
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
            pile = PileInfo(
                stock = g.stock.size,
                top = g.discard.lastOrNull(),
                stockActive = GinRummyEngine.DRAW_STOCK in legal,
                discardActive = GinRummyEngine.DRAW_DISCARD in legal,
            ),
            row = if (showdown) g.shown.map { 1 to it } else emptyList(),
            rowBadge = if (showdown && g.lastKind != GinRummyEngine.KIND_DRAW) g.shownDeadwood.toString() else null,
            rowCapacity = 10,
            hasRow = showdown,
        )
    }

    override fun cardOfMove(move: Int): PlayingCard? = if (move in 0..51) PlayingCard(move) else null

    override fun onHumanCard(card: PlayingCard) {
        if (game.phase != GinRummyEngine.DISCARD || card.id !in game.legalMoves()) return
        if (card in lifted) submit(card.id) else { lifted.clear(); lifted.add(card); refresh() }
    }

    override fun onHumanDrop(card: PlayingCard) {
        if (game.phase == GinRummyEngine.DISCARD && card.id in game.legalMoves()) submit(card.id)
    }

    override fun onHumanStock() {
        if (GinRummyEngine.DRAW_STOCK in game.legalMoves()) submit(GinRummyEngine.DRAW_STOCK)
    }

    override fun onHumanDiscard() {
        if (GinRummyEngine.DRAW_DISCARD in game.legalMoves()) submit(GinRummyEngine.DRAW_DISCARD)
    }

    override fun controls(): List<Control> {
        val g = game
        return when (g.phase) {
            GinRummyEngine.FIRST -> listOf(
                Control(getString(R.string.gin_pass)) { submit(GinRummyEngine.PASS) },
                Control(getString(R.string.gin_take), primary = true) { submit(GinRummyEngine.DRAW_DISCARD) },
            )
            GinRummyEngine.DRAW -> buildList {
                add(Control(getString(R.string.gin_draw), primary = true) { submit(GinRummyEngine.DRAW_STOCK) })
                if (GinRummyEngine.DRAW_DISCARD in g.legalMoves()) {
                    add(Control(getString(R.string.gin_take)) { submit(GinRummyEngine.DRAW_DISCARD) })
                }
            }
            GinRummyEngine.DISCARD -> {
                val card = lifted.firstOrNull() ?: return emptyList()
                val knock = GinRummyEngine.KNOCK_BASE + card.id
                if (knock !in g.legalMoves()) return emptyList()
                val gin = g.deadwoodOf(g.hands[0].filter { it != card }) == 0
                listOf(Control(getString(if (gin) R.string.gin_gin else R.string.gin_knock), primary = true) { submit(knock) })
            }
            else -> listOf(Control(getString(R.string.gin_continue), primary = true) { submit(GinRummyEngine.CONTINUE) })
        }
    }

    /** Ton bois mort (après ta meilleure carte à écarter s'il y en a une de trop), ou le résultat du décompte. */
    override fun statusText(): String? {
        val g = game
        if (g.phase == GinRummyEngine.SHOWDOWN) return getString(when (g.lastKind) {
            GinRummyEngine.KIND_GIN -> R.string.gin_result_gin
            GinRummyEngine.KIND_UNDERCUT -> R.string.gin_result_undercut
            GinRummyEngine.KIND_DRAW -> R.string.gin_result_draw
            else -> R.string.gin_result_knock
        })
        return getString(R.string.gin_deadwood, g.deadwoodNow(0))
    }

    override fun recordValue(): Int? = if (game.isOver) game.totals[0] else null
}

/** La tuile du hub : une suite de cœurs et un brelan de 7, autour du 7 de cœur qu'ils partagent. */
class GinRummyArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFFEC407A.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, listOf(
            PlayingCard.of(CardSuit.HEARTS, 5),
            PlayingCard.of(CardSuit.HEARTS, 6),
            PlayingCard.of(CardSuit.HEARTS, 7),
            PlayingCard.of(CardSuit.SPADES, 7),
            PlayingCard.of(CardSuit.DIAMONDS, 7),
        ), spread = 12f)
    }
}
