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

/** Garde-fous du bestiaire régional de Hameau. Les planches sont dans le banc `HamletApercuTest.ennemis`. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = android.app.Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class HamletEnemyArtTest {
    /** Chaque région a ses six ennemis et son boss ; les familiers et les invocations gardent leur visage. */
    @Test fun chaqueRegionASesCreatures() {
        val ordinaires = (0 until HamletEnemies.REGIONS).flatMap { r -> (0 until HamletEnemies.KINDS).map { HamletEnemies.visual(it, r) } }
        assertEquals(HamletEnemies.REGIONS * HamletEnemies.KINDS, ordinaires.toSet().size)
        val boss = (0 until HamletEnemies.REGIONS).map { HamletEnemies.visual(6, it) }
        assertEquals(boss, (0 until HamletEnemies.REGIONS).map { HamletEnemies.visual(7, it) })
        assertEquals(HamletEnemies.REGIONS, boss.toSet().size)
        assertTrue(boss.all { HamletEnemies.isBoss(it) } && ordinaires.none { HamletEnemies.isBoss(it) })
        assertEquals(listOf(0, 5, HamletEnemies.FERN_DRAKE), (0..2).map { HamletEnemies.pet(it) })
        assertEquals(HamletEnemies.visual(0, 0), HamletEnemies.SUMMON)
        assertTrue((ordinaires + boss + HamletEnemies.FERN_DRAKE).all { it in 0 until HamletEnemies.COUNT })
    }

    /** Aucune créature n'est le recoloriage d'une autre. */
    @Test fun silhouettesDistinctes() {
        val silhouettes = (0 until HamletEnemies.COUNT).map { id ->
            val bmp = HamletCharacterArt.bitmap(false, id)
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            px.map { it ushr 24 != 0 }
        }
        for (a in silhouettes.indices) for (b in a + 1 until silhouettes.size)
            assertNotEquals("ennemis $a et $b ont la même silhouette", silhouettes[a], silhouettes[b])
    }

    /** Seuls les boss portent une aura ; les ennemis ordinaires restent sobres. */
    @Test fun seulsLesBossBrillent() {
        for (id in 0 until HamletEnemies.COUNT) {
            val extra = lueur(id)
            if (HamletEnemies.isBoss(id)) assertTrue("boss $id sans aura", extra > 200) else assertEquals("ennemi $id", 0, extra)
        }
    }

    private fun lueur(id: Int): Int {
        fun pixels(paint: (Canvas) -> Unit): Int {
            val bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
            paint(Canvas(bmp))
            val px = IntArray(240 * 240)
            bmp.getPixels(px, 0, 240, 0, 0, 240, 240)
            return px.count { it ushr 24 != 0 }
        }
        val avecEffets = pixels { HamletCharacterArt.draw(it, false, id, 120f, 190f, 112f, .4f, fx = 1.3f) }
        val seul = pixels {
            val zone = RectF()
            HamletCharacterArt.bounds(120f, 190f, 112f, zone)
            it.drawBitmap(HamletCharacterArt.bitmap(false, id, time = .4f), null, zone, Paint().apply { isFilterBitmap = false })
        }
        return avecEffets - seul
    }
}
