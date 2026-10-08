package com.Atom2Universe.app.games.cards.gofish

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
 * La Pêche : toi contre deux adversaires. Un toucher sur une carte soulève toutes les cartes de sa
 * hauteur ; il reste à toucher le joueur à qui la demander. Glisser une carte vers la table la
 * choisit aussi (et la demande tout de suite quand il n'y a qu'un joueur à interroger).
 */
class GoFishActivity : CardGameActivity() {

    override val gameKey = "gofish"
    override val titleRes = R.string.gofish_title
    override val rulesRes = R.string.gofish_rules

    private val game: GoFishEngine get() = engine as GoFishEngine

    override fun createEngine(seed: Long): CardEngine = GoFishEngine(seed)

    override fun buildScene(): TableScene {
        val g = game
        val seats = (0 until 3).map { s ->
            SeatInfo(
                name = seatName(s, 3),
                handSize = g.hands[s].size,
                hand = if (s == 0) g.hands[0].sortedWith(compareBy({ it.highRank }, { it.suit })) else null,
                lifted = if (s == 0) lifted.toSet() else emptySet(),
                caption = resources.getQuantityString(R.plurals.gofish_books, g.books[s], g.books[s]),
                active = g.current == s,
            )
        }
        return TableScene(seats, pile = PileInfo(stock = g.stock.size, top = null))
    }

    /** Les joueurs à qui on peut demander la hauteur de [card]. */
    private fun targetsFor(card: PlayingCard): List<Int> =
        game.legalMoves().filter { it % 13 + 1 == card.rank }.map { it / 13 }

    private fun choose(card: PlayingCard, ask: Boolean) {
        val targets = targetsFor(card)
        if (targets.isEmpty()) return
        val alreadyChosen = lifted.firstOrNull()?.rank == card.rank
        if ((ask || alreadyChosen) && targets.size == 1) { submit(GoFishEngine.move(targets[0], card.rank)); return }
        if (alreadyChosen) return
        lifted.clear()
        lifted.addAll(game.hands[0].filter { it.rank == card.rank })
        refresh()
    }

    override fun onHumanCard(card: PlayingCard) = choose(card, ask = false)

    override fun onHumanDrop(card: PlayingCard) = choose(card, ask = true)

    override fun controls(): List<Control> {
        val rank = lifted.firstOrNull()?.rank ?: return emptyList()
        return targetsFor(lifted.first()).map { t ->
            Control(seatName(t, 3), primary = true) { submit(GoFishEngine.move(t, rank)) }
        }
    }

    private fun rankLabel(rank: Int): String = when (rank) {
        PlayingCard.ACE -> getString(R.string.card_rank_ace)
        PlayingCard.JACK -> getString(R.string.card_rank_jack)
        PlayingCard.QUEEN -> getString(R.string.card_rank_queen)
        PlayingCard.KING -> getString(R.string.card_rank_king)
        else -> rank.toString()
    }

    /** La dernière demande : qui a demandé quoi à qui, et si ça a donné quelque chose (✓ et le nombre de cartes) ou non (✗). */
    override fun statusText(): String? {
        val g = game
        if (g.lastAsker < 0) return null
        val outcome = if (g.lastGiven > 0) "✓${g.lastGiven}" else "✗"
        return getString(R.string.gofish_last, seatName(g.lastAsker, 3), seatName(g.lastTarget, 3), rankLabel(g.lastRank), outcome)
    }

    override fun recordValue(): Int? = if (game.isOver) game.books[0] else null
}

/** La tuile du hub : un carré, les quatre dames. */
class GoFishArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFF29B6F6.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, CardSuit.entries.map { PlayingCard.of(it, PlayingCard.QUEEN) }, spread = 15f)
    }
}
