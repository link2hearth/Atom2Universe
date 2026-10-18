package com.Atom2Universe.app.games.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.view.View
import com.Atom2Universe.app.games.cards.cribbage.CribbageEngine
import com.Atom2Universe.app.games.cards.gofish.GoFishEngine
import com.Atom2Universe.app.games.kit.KitPalette
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Aperçus de la table pour les nouveaux jeux : la rangée centrale (décompte du cribbage, mains du gin
 * rami), la défausse cliquable, la table à trois joueurs de la Pêche. Les images vont dans
 * `app/build/apercu/cartes-kit/` ; le test échoue si le dessin lève une exception ou reste vide.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardTableRowRenderTest {

    private val dossier = File("build/apercu/cartes-kit").also { it.mkdirs() }
    private val labels = arrayOf("A", "2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K")

    private fun table(): CardTableView {
        val view = CardTableView(RuntimeEnvironment.getApplication(), labels)
        view.palette = KitPalette.artwork(0xFFD4A24C.toInt())
        File("src/main/assets/Assets/Cartes/bonus/images (7).jpg").takeIf { it.exists() }?.let {
            view.painter.setBack(BitmapFactory.decodeFile(it.path))
        }
        view.setPadding(24, 12, 24, 12)
        return view
    }

    private fun peindre(view: CardTableView, nom: String): Int {
        val w = 1080
        val h = 1560
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val fond = KitPalette.artwork(0).background
        bmp.eraseColor(fond)
        view.draw(Canvas(bmp))
        FileOutputStream(File(dossier, "$nom.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        var n = 0
        for (y in 0 until h step 4) for (x in 0 until w step 4) if (bmp.getPixel(x, y) != fond) n++
        return n
    }

    private fun card(s: CardSuit, r: Int) = PlayingCard.of(s, r)

    @Test fun cribbageEnPleinDecompte() {
        val hands = listOf(
            listOf(card(CardSuit.SPADES, 7), card(CardSuit.HEARTS, 9), card(CardSuit.CLUBS, 3)),
            listOf(card(CardSuit.DIAMONDS, 4), card(CardSuit.CLUBS, 12), card(CardSuit.HEARTS, 2)),
        )
        val e = CribbageEngine.forTestPeg(hands, starter = card(CardSuit.DIAMONDS, 11), dealer = 1)
        val row = listOf(0 to card(CardSuit.CLUBS, 8), 1 to card(CardSuit.HEARTS, 8), 0 to card(CardSuit.DIAMONDS, 5), 1 to card(CardSuit.SPADES, 10))
        val scene = TableScene(
            listOf(
                SeatInfo("You", 3, hand = e.hands[0].sortedBy { it.rank }, playable = setOf(card(CardSuit.CLUBS, 3)), caption = "52 +4", active = true),
                SeatInfo("North", 3, caption = "47 · Crib"),
            ),
            pile = PileInfo(stock = 39, top = e.starter),
            row = row, rowBadge = "31", hasRow = true,
        )
        val view = table()
        view.show(scene)
        assertTrue(peindre(view, "cribbage-decompte") > 4000)
    }

    @Test fun ginRamiAuDecompte() {
        val hand = (0 until 10).map { PlayingCard(it * 5) }.sortedBy { it.id }
        val scene = TableScene(
            listOf(
                SeatInfo("You", 10, hand = hand, marked = setOf(hand[0], hand[1]), caption = "62 +25"),
                SeatInfo("North", 10, caption = "40"),
            ),
            pile = PileInfo(stock = 19, top = card(CardSuit.HEARTS, 13), stockActive = true, discardActive = true),
            row = hand.map { 1 to it }, rowBadge = "7", rowCapacity = 10, hasRow = true,
        )
        val view = table()
        view.show(scene)
        assertTrue(peindre(view, "gin-decompte") > 4000)
    }

    @Test fun pecheATroisJoueurs() {
        val e = GoFishEngine(4)
        val scene = TableScene(
            listOf(
                SeatInfo("You", e.hands[0].size, hand = e.hands[0].sortedBy { it.highRank }, lifted = e.hands[0].filter { it.rank == e.hands[0][0].rank }.toSet(), caption = "0 books", active = true),
                SeatInfo("West", e.hands[1].size, caption = "1 book"),
                SeatInfo("East", e.hands[2].size, caption = "0 books"),
            ),
            pile = PileInfo(stock = e.stock.size, top = null),
        )
        val view = table()
        view.show(scene)
        assertTrue(peindre(view, "peche-trois") > 4000)
    }
}
