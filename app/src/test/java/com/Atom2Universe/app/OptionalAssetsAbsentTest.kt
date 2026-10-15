package com.Atom2Universe.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import android.view.View
import com.Atom2Universe.app.games.cards.CardBacks
import com.Atom2Universe.app.games.cards.CardPainter
import com.Atom2Universe.app.games.chess.ChessPieceImages
import com.Atom2Universe.app.games.chess.Piece
import com.Atom2Universe.app.games.chess.PieceColor
import com.Atom2Universe.app.games.chess.PieceType
import com.Atom2Universe.app.games.jigsaw.JigsawImages
import com.Atom2Universe.app.periodic.PeriodicTableActivity
import com.Atom2Universe.app.science.ScienceAssets
import com.Atom2Universe.app.science.bestiary.BestiaryActivity
import com.Atom2Universe.app.science.biology.HumanBiologyActivity
import com.Atom2Universe.app.science.botany.BotanyActivity
import com.Atom2Universe.app.science.mycology.MycologyActivity
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Run explicitly with Assets/ and science/ omitted; skip when the optional packs are installed. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OptionalAssetsAbsentTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Before fun withoutPacks() {
        assumeTrue(context.assets.list("Assets").isNullOrEmpty())
        assumeTrue(context.assets.list("science").isNullOrEmpty())
    }

    @Test fun puzzlesOfferNoBundledCollection() {
        assertTrue(JigsawImages.collections.all { it.paths(context).isEmpty() })
        assertTrue(JigsawImages.paths(context, -1).isEmpty())
        assertNull(JigsawImages.decode(context, "Assets/Puzzle/missing.jpg"))
    }

    @Test fun cardBacksAreDrawnWithoutAnyImagesOrRenameIndex() {
        assertTrue(CardBacks.availableIndices(context).isEmpty())
        assertNull(CardBacks.loadRandom(context))
        assertEquals("old.jpg", CardBacks.resolveLegacyPath(context, "old.jpg"))
        val bitmap = Bitmap.createBitmap(100, 140, Bitmap.Config.ARGB_8888)
        try {
            CardPainter(context).drawBack(Canvas(bitmap), RectF(0f, 0f, 100f, 140f), shadow = false)
            assertTrue(Color.alpha(bitmap.getPixel(50, 70)) > 0)
        } finally {
            bitmap.recycle()
        }
    }

    @Test fun allTwelveChessPiecesStillRender() {
        for (color in PieceColor.entries) for (type in PieceType.entries) {
            val bitmap = ChessPieceImages.load(context.assets, Piece(type, color), 64)
            try {
                assertEquals(64, bitmap.width)
                val pixels = IntArray(64 * 64)
                bitmap.getPixels(pixels, 0, 64, 0, 0, 64, 64)
                assertTrue("$color $type", pixels.count { Color.alpha(it) > 0 } > 100)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @Test fun scientificModulesCloseSafelyWithoutTheirData() {
        for ((id, activity) in listOf(
            "human_biology" to HumanBiologyActivity::class.java,
            "mycology" to MycologyActivity::class.java,
            "botany" to BotanyActivity::class.java,
            "bestiary" to BestiaryActivity::class.java,
            "periodic_table" to PeriodicTableActivity::class.java
        )) {
            assertFalse(id, ScienceAssets.isAvailable(context, id))
            val controller = Robolectric.buildActivity(activity).create()
            try {
                assertTrue(id, controller.get().isFinishing)
            } finally {
                controller.destroy()
            }
        }
    }

    @Test fun aboutPageHidesOfflineLicensesThatAreNotBundled() {
        val controller = Robolectric.buildActivity(AboutActivity::class.java).setup()
        try {
            for (id in listOf(R.id.about_filament_license, R.id.about_facial_license_offline, R.id.about_detail_license_offline)) {
                assertEquals(View.GONE, controller.get().findViewById<View>(id).visibility)
            }
        } finally {
            controller.pause().stop().destroy()
        }
    }
}
