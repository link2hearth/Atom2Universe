package com.Atom2Universe.app.games.cards.euchre

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
 * Euchre : toi et le siège d'en face contre les deux autres. Pour l'atout, des boutons : passer,
 * prendre (ou nommer une couleur au second tour), et « Seul » pour jouer sans partenaire. Quand tu
 * ramasses la carte retournée, un toucher soulève la carte à écarter, un second l'écarte.
 */
class EuchreActivity : CardGameActivity() {

    override val gameKey = "euchre"
    override val titleRes = R.string.euchre_title
    override val rulesRes = R.string.euchre_rules

    private val game: EuchreEngine get() = engine as EuchreEngine

    /** « Seul » est enclenché : la prochaine prise de l'atout se jouera sans partenaire. */
    private var aloneChoice = false

    override fun createEngine(seed: Long): CardEngine = EuchreEngine(seed)

    override fun rulesText(): CharSequence = getString(rulesRes, EuchreEngine.DEFAULT_LIMIT)

    /** Les cartes de la main : l'atout d'abord (bowers compris), puis les autres couleurs en alternant. */
    private fun sortedHand(cards: Collection<PlayingCard>): List<PlayingCard> {
        val g = game
        val trump = g.trump ?: return Cards.sortedForHand(cards)
        return cards.sortedBy { if (g.effectiveSuit(it) == trump) -1000 + g.strengthOf(it) else Cards.handKey(it) }
    }

    private fun caption(seat: Int): String? {
        val g = game
        if (g.phase <= EuchreEngine.ROUND2) return if (seat == g.dealer) getString(R.string.euchre_dealer) else null
        if (seat == g.sitting) return null
        val tricks = g.tricksWon[seat]
        if (seat != g.maker) return tricks.toString()
        return "${g.trump!!.symbol}${if (g.alone) "★" else ""} $tricks"
    }

    override fun buildScene(): TableScene {
        val g = game
        val seats = (0 until 4).map { s ->
            SeatInfo(
                name = seatName(s, 4),
                handSize = g.hands[s].size,
                hand = if (s == 0) sortedHand(g.hands[0]) else null,
                lifted = if (s == 0) lifted.toSet() else emptySet(),
                playable = if (s == 0 && g.phase == EuchreEngine.PLAY) playableCards() else null,
                caption = caption(s),
                active = g.current == s,
            )
        }
        return TableScene(seats, trick = g.trick.toList(), pile = PileInfo(
            stock = 3,
            top = if (g.phase <= EuchreEngine.ROUND2) g.upcard else null,
            suit = g.trump,
        ))
    }

    override fun onHumanCard(card: PlayingCard) {
        when (game.phase) {
            EuchreEngine.DISCARD -> if (card in lifted) submit(card.id) else { lifted.clear(); lifted.add(card); refresh() }
            EuchreEngine.PLAY -> {
                if (card !in (playableCards() ?: return)) return
                if (card in lifted) submit(card.id) else { lifted.clear(); lifted.add(card); refresh() }
            }
        }
    }

    override fun onHumanDrop(card: PlayingCard) {
        when (game.phase) {
            EuchreEngine.DISCARD -> submit(card.id)
            EuchreEngine.PLAY -> if (card in (playableCards() ?: return)) submit(card.id)
        }
    }

    override fun controls(): List<Control> {
        val g = game
        val legal = g.legalMoves()
        fun pass() = Control(getString(R.string.euchre_pass), enabled = EuchreEngine.PASS in legal) { submit(EuchreEngine.PASS) }
        fun soloToggle() = Control(getString(R.string.euchre_alone), primary = aloneChoice) { aloneChoice = !aloneChoice; refresh() }
        return when (g.phase) {
            EuchreEngine.ROUND1 -> listOf(
                pass(),
                Control(getString(R.string.euchre_order), primary = true) {
                    submit(if (aloneChoice) EuchreEngine.ORDER_ALONE else EuchreEngine.ORDER)
                },
                soloToggle(),
            )
            EuchreEngine.ROUND2 -> listOf(pass()) +
                CardSuit.entries.filter { it != g.upcard.suit }.map { suit ->
                    Control(suit.symbol, primary = true) {
                        submit((if (aloneChoice) EuchreEngine.NAME_ALONE_BASE else EuchreEngine.NAME_BASE) + suit.ordinal)
                    }
                } + soloToggle()
            else -> emptyList()
        }
    }

    override fun onMovePlayed() { aloneChoice = false }

    override fun statusText(): String = getString(R.string.cards_team_score, game.teamScore[0], game.teamScore[1])

    override fun recordValue(): Int? = if (game.isOver) game.teamScore[0] else null
}

/** La tuile du hub : les deux bowers (les valets de pique et de trèfle), puis l'as, le roi et le dix de pique. */
class EuchreArtwork(context: Context) : CardArtwork(context) {
    override val accent = 0xFF26A69A.toInt()
    override fun paint(canvas: Canvas, w: Float, h: Float, palette: KitPalette) {
        fan(canvas, w, h, listOf(
            PlayingCard.of(CardSuit.SPADES, 10),
            PlayingCard.of(CardSuit.SPADES, PlayingCard.ACE),
            PlayingCard.of(CardSuit.CLUBS, PlayingCard.JACK),
            PlayingCard.of(CardSuit.SPADES, PlayingCard.JACK),
            PlayingCard.of(CardSuit.SPADES, PlayingCard.KING),
        ), spread = 12f)
    }
}
