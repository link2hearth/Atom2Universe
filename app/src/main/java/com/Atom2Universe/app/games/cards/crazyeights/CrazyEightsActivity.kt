package com.Atom2Universe.app.games.cards.crazyeights

import android.content.Context
import android.graphics.Canvas
import android.graphics.RectF
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.cards.CardArtwork
import com.Atom2Universe.app.games.cards.CardEngine
import com.Atom2Universe.app.games.cards.CardGameActivity
import com.Atom2Universe.app.games.cards.CardPainter
import com.Atom2Universe.app.games.cards.CardSuit
import com.Atom2Universe.app.games.cards.Cards
import com.Atom2Universe.app.games.cards.PileInfo
import com.Atom2Universe.app.games.cards.PlayingCard
import com.Atom2Universe.app.games.cards.SeatInfo
import com.Atom2Universe.app.games.cards.TableScene
import com.Atom2Universe.app.games.kit.KitPalette

/**
 * Huit américain : toi contre trois adversaires. Un premier toucher soulève une carte jouable, un
 * second la pose ; un 8 demande ensuite la couleur à suivre. Quand rien ne se joue, un bouton (ou un
 * toucher sur la pioche) pioche, puis passe.
 */
class CrazyEightsActivity : CardGameActivity() {

    override val gameKey = "crazyeights"
    override val titleRes = R.string.crazy8_title
    override val rulesRes = R.string.crazy8_rules

    private val game: CrazyEightsEngine get() = engine as CrazyEightsEngine

    /** Un 8 est soulevé et on attend la couleur choisie. */
    private var choosingSuit = false

    override fun createEngine(seed: Long): CardEngine = CrazyEightsEngine(seed)

    override fun cardOfMove(move: Int): PlayingCard? = CrazyEightsEngine.cardOf(move)

    override fun buildScene(): TableScene {
        val g = game
        val seats = (0 until g.seats).map { s ->
            SeatInfo(
                name = seatName(s, g.seats),
                handSize = g.hands[s].size,
                hand = if (s == 0) Cards.sortedForHand(g.hands[0]) else null,
                lifted = if (s == 0) lifted.toSet() else emptySet(),
                playable = if (s == 0) playableCards() else null,
                active = g.current == s,
            )
        }
        return TableScene(seats, pile = PileInfo(
            stock = g.stockSize,
            top = g.topCard,
            suit = g.activeSuit,
            stockActive = g.current == HUMAN && CrazyEightsEngine.DRAW in g.legalMoves(),
        ))
    }

    override fun onHumanCard(card: PlayingCard) {
        val options = game.legalMoves().filter { CrazyEightsEngine.cardOf(it) == card }
        if (options.isEmpty()) return
        if (card !in lifted) {
            lifted.clear()
            lifted.add(card)
            choosingSuit = false
            refresh()
            return
        }
        // Un second toucher pose la carte ; un 8 demande d'abord sa couleur.
        if (options.size == 1) submit(options[0]) else { choosingSuit = true; refresh() }
    }

    override fun onHumanDrop(card: PlayingCard) {
        val options = game.legalMoves().filter { CrazyEightsEngine.cardOf(it) == card }
        when {
            options.isEmpty() -> {}
            options.size == 1 -> submit(options[0])
            // Un 8 glissé sur la table : il reste à choisir sa couleur.
            else -> { lifted.clear(); lifted.add(card); choosingSuit = true; refresh() }
        }
    }

    override fun onHumanStock() {
        val legal = game.legalMoves()
        if (CrazyEightsEngine.DRAW in legal) submit(CrazyEightsEngine.DRAW)
    }

    override fun controls(): List<Control> {
        val legal = game.legalMoves()
        val eight = lifted.firstOrNull()
        return when {
            choosingSuit && eight != null -> CardSuit.entries.reversed().map { suit ->
                Control(suit.symbol) { submit(CrazyEightsEngine.eightMove(eight, suit)) }
            }
            CrazyEightsEngine.DRAW in legal -> listOf(Control(getString(R.string.crazy8_draw), primary = true) { submit(CrazyEightsEngine.DRAW) })
            CrazyEightsEngine.PASS in legal -> listOf(Control(getString(R.string.crazy8_pass), primary = true) { submit(CrazyEightsEngine.PASS) })
            else -> emptyList()
        }
    }

    override fun onMovePlayed() { choosingSuit = false }

    override fun statusText(): String? =
        if (game.isOver && game.winner == HUMAN) getString(R.string.cards_points, game.roundPoints) else null

    override fun recordValue(): Int? = if (game.isOver && game.winner == HUMAN) game.roundPoints else null
}

/** La tuile du hub : un éventail de quatre 8, un par couleur. */
class CrazyEightsArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFF66BB6A.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, CardSuit.entries.map { PlayingCard.of(it, 8) }, spread = 15f)
    }
}
