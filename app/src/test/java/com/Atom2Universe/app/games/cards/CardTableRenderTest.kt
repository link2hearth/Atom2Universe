package com.Atom2Universe.app.games.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import com.Atom2Universe.app.games.cards.crazyeights.CrazyEightsEngine
import com.Atom2Universe.app.games.cards.hearts.HeartsEngine
import com.Atom2Universe.app.games.kit.KitPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowSystemClock
import java.io.File
import java.io.FileOutputStream
import java.time.Duration
import kotlin.random.Random

/**
 * Garde-fou de la table de jeu, qui sert aussi d'aperçu : il dessine la vraie [CardTableView] avec
 * de vraies donnes (une manche de dame de pique, une de huit américain, une main de 20 cartes sur
 * deux rangées), à quelques instants d'un trajet de carte, et échoue si le dessin lève une exception
 * ou reste vide. Les images vont dans `app/build/apercu/cartes-kit/` : c'est la seule façon de juger
 * la mise en page sans téléphone. Les textes sont des textes d'essai (le test n'a pas les ressources
 * de l'appli), le dos est une vraie image des assets.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardTableRenderTest {

    private val dossier = File("build/apercu/cartes-kit").also { it.mkdirs() }
    private val labels = arrayOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

    private fun table(): CardTableView {
        val view = CardTableView(RuntimeEnvironment.getApplication(), labels)
        view.palette = KitPalette.artwork(0xFF66BB6A.toInt())
        File("src/main/assets/Assets/Cartes/bonus/images (7).jpg").takeIf { it.exists() }?.let {
            view.painter.setBack(BitmapFactory.decodeFile(it.path))
        }
        val d = 3 // xxhdpi
        view.setPadding(8 * d, 4 * d, 8 * d, 4 * d)
        return view
    }

    private fun peindre(view: CardTableView, w: Int = 1080, h: Int = 1560): Bitmap {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.eraseColor(KitPalette.artwork(0).background)
        view.draw(Canvas(bmp))
        return bmp
    }

    private fun ecrire(nom: String, bmp: Bitmap) {
        FileOutputStream(File(dossier, "$nom.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Le nombre de pixels qui ne sont pas la couleur de fond : une table vide n'en a presque pas. */
    private fun remplissage(bmp: Bitmap): Int {
        val fond = KitPalette.artwork(0).background
        var n = 0
        for (y in 0 until bmp.height step 4) for (x in 0 until bmp.width step 4) if (bmp.getPixel(x, y) != fond) n++
        return n
    }

    private val noms = listOf("You", "West", "North", "East")

    /** Une manche de dame de pique jouée jusqu'au milieu du troisième pli, la main du joueur à l'écran. */
    private fun heartsScene(engine: HeartsEngine, lifted: Set<PlayingCard> = emptySet()): TableScene {
        val playable = if (engine.current == 0 && !engine.passing)
            engine.legalMoves().map { PlayingCard(it) }.toSet() else null
        val seats = (0 until 4).map { s ->
            val taken = engine.handPointsOf(s)
            SeatInfo(noms[s], engine.hands[s].size,
                hand = if (s == 0) Cards.sortedForHand(engine.hands[0]) else null,
                lifted = if (s == 0) lifted else emptySet(),
                playable = if (s == 0) playable else null,
                marked = if (s == 0 && engine.trickNo == 0) engine.received else emptySet(),
                caption = if (taken > 0) "${engine.totals[s]} +$taken" else engine.totals[s].toString(),
                active = engine.current == s)
        }
        return TableScene(seats, trick = engine.trick.toList())
    }

    private fun playUntilHumanHasATrickInProgress(): HeartsEngine {
        val e = HeartsEngine(21)
        val random = Random(21)
        // On passe, puis on joue jusqu'à un moment où le pli en cours a deux cartes et c'est au joueur.
        var guard = 0
        while (guard++ < 400) {
            if (e.current == 0 && !e.passing && e.trickNo >= 2 && e.trick.size >= 2) break
            e.play(e.aiMove(CardLevel.MEDIUM, random))
            e.drainEvents()
        }
        return e
    }

    @Test
    fun laTableDeDameDePiqueSeDessine() {
        val e = playUntilHumanHasATrickInProgress()
        val view = table()
        val lifted = setOf(Cards.sortedForHand(e.hands[0]).first())
        view.show(heartsScene(e, lifted))
        val bmp = peindre(view)
        ecrire("dame-de-pique", bmp)
        assertTrue("table vide : ${remplissage(bmp)}", remplissage(bmp) > 5000)
    }

    @Test
    fun laTableDeHuitAmericainSeDessine() {
        val e = CrazyEightsEngine(9)
        val random = Random(9)
        repeat(12) {
            if (!e.isOver && e.current != 0) { e.play(e.aiMove(CardLevel.MEDIUM, random)); e.drainEvents() }
        }
        val seats = (0 until 4).map { s ->
            SeatInfo(noms[s], e.hands[s].size,
                hand = if (s == 0) Cards.sortedForHand(e.hands[0]) else null,
                playable = if (s == 0) e.legalMoves().toList().mapNotNull { CrazyEightsEngine.cardOf(it) }.toSet() else null,
                active = e.current == s)
        }
        val view = table()
        view.show(TableScene(seats, pile = PileInfo(e.stockSize, e.topCard, e.activeSuit, stockActive = true)))
        val bmp = peindre(view)
        ecrire("huit-americain", bmp)
        assertTrue("table vide", remplissage(bmp) > 5000)
    }

    @Test
    fun uneMainDeVingtCartesSeRangeSurDeuxRangees() {
        val hand = Cards.sortedForHand(Cards.standardDeck().take(20))
        val seats = listOf(
            SeatInfo(noms[0], 20, hand = hand, playable = hand.take(7).toSet(), active = true),
            SeatInfo(noms[1], 14), SeatInfo(noms[2], 22), SeatInfo(noms[3], 9),
        )
        val view = table()
        view.show(TableScene(seats, pile = PileInfo(8, PlayingCard.of(CardSuit.HEARTS, 8), CardSuit.SPADES)))
        val bmp = peindre(view)
        ecrire("main-de-vingt", bmp)
        assertTrue("table vide", remplissage(bmp) > 5000)
    }

    @Test
    fun uneCarteVoyageDeSaMainAuPli() {
        val e = playUntilHumanHasATrickInProgress()
        val view = table()
        view.show(heartsScene(e))
        peindre(view)
        // Le joueur pose sa carte : on la voit à mi-chemin, puis arrivée, puis le pli qui part chez le gagnant.
        val card = PlayingCard(e.legalMoves().first())
        e.play(card.id)
        val events = e.drainEvents()
        view.present(heartsScene(e), events)
        ShadowSystemClock.advanceBy(Duration.ofMillis(1))
        peindre(view)
        ShadowSystemClock.advanceBy(Duration.ofMillis(130))
        ecrire("trajet-1-en-vol", peindre(view))
        ShadowSystemClock.advanceBy(Duration.ofMillis(200))
        val bmp = peindre(view)
        ecrire("trajet-2-posee", bmp)
        assertTrue("table vide", remplissage(bmp) > 5000)
        // Le tout se termine : plus aucune carte en route après un moment.
        repeat(20) { ShadowSystemClock.advanceBy(Duration.ofMillis(200)); peindre(view) }
        assertTrue("des cartes sont encore en route", !view.busy)
    }

    private fun toucher(view: View, action: Int, x: Float, y: Float, t: Long) {
        val e = android.view.MotionEvent.obtain(0, t, action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    @Test
    fun uneCarteGlisseeVersLaTableSeJoue() {
        val e = playUntilHumanHasATrickInProgress()
        val view = table()
        // Aucune carte grisée : on glisse la dernière, quelle qu'elle soit.
        val base = heartsScene(e)
        val me = base.seats[0]
        view.show(TableScene(listOf(SeatInfo(me.name, me.handSize, hand = me.hand)) + base.seats.drop(1), trick = base.trick))
        peindre(view)
        var dropped: PlayingCard? = null
        view.onCardDrop = { dropped = it }
        // Le doigt se pose sur la dernière carte de la main (entièrement visible), monte, puis lâche.
        val x = 960f
        toucher(view, android.view.MotionEvent.ACTION_DOWN, x, 1400f, 0)
        toucher(view, android.view.MotionEvent.ACTION_MOVE, x - 150f, 1100f, 40)
        toucher(view, android.view.MotionEvent.ACTION_MOVE, 600f, 800f, 80)
        ecrire("glisse-en-cours", peindre(view))
        toucher(view, android.view.MotionEvent.ACTION_UP, 600f, 800f, 120)
        assertTrue("la carte n'a pas été déposée", dropped != null)

        // Lâchée trop bas (toujours sur la main) : rien ne se joue.
        dropped = null
        toucher(view, android.view.MotionEvent.ACTION_DOWN, x, 1400f, 200)
        toucher(view, android.view.MotionEvent.ACTION_MOVE, x - 50f, 1380f, 240)
        toucher(view, android.view.MotionEvent.ACTION_UP, x - 50f, 1380f, 280)
        assertTrue("une carte lâchée sur la main ne doit pas se jouer", dropped == null)
    }
}
