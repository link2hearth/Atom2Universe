package com.Atom2Universe.app.games.hamlet

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Garde-fous du dessin des héros de Hameau. Les planches pour juger le dessin à l'œil sont dans
 * le banc `HamletApercuTest.heros`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HamletHeroArtTest {
    /** Aucun héros n'est le recoloriage d'un autre : leurs silhouettes (canal alpha) diffèrent toutes. */
    @Test fun silhouettesDistinctes() {
        assertEquals(HamletHeroes.defs.size, HamletHeroLooks.size)
        val silhouettes = HamletHeroes.defs.indices.map { id ->
            val bmp = HamletCharacterArt.bitmap(true, id)
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            px.map { it ushr 24 != 0 }
        }
        for (a in silhouettes.indices) for (b in a + 1 until silhouettes.size)
            assertNotEquals("héros $a et $b ont la même silhouette", silhouettes[a], silhouettes[b])
    }

    /** Plus un héros est rare, plus il brille : la surface des effets autour du dessin croît à chaque rang. */
    @Test fun effetsCroissentAvecLaRarete() {
        val moyennes = HeroRarity.entries.map { rarity ->
            HamletHeroes.defs.filter { it.rarity == rarity }.map { lueur(it.id) }.average()
        }
        assertEquals(0.0, moyennes[0], 0.0)
        for (i in 1 until moyennes.size) assertTrue("rang $i : $moyennes", moyennes[i] > moyennes[i - 1])
    }

    private fun lueur(id: Int): Int {
        fun pixels(paint: (Canvas) -> Unit): Int {
            val bmp = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
            paint(Canvas(bmp))
            val px = IntArray(220 * 220)
            bmp.getPixels(px, 0, 220, 0, 0, 220, 220)
            return px.count { it ushr 24 != 0 }
        }
        val avecEffets = pixels { HamletCharacterArt.draw(it, true, id, 110f, 180f, 112f, .4f, fx = 1.3f) }
        val seul = pixels {
            val zone = RectF()
            HamletCharacterArt.bounds(110f, 180f, 112f, zone)
            it.drawBitmap(HamletCharacterArt.bitmap(true, id, time = .4f), null, zone, Paint().apply { isFilterBitmap = false })
        }
        return avecEffets - seul
    }
}
