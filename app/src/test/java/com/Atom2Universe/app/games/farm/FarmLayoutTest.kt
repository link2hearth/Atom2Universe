package com.Atom2Universe.app.games.farm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde le plan de la ferme. Les parcelles sont posées à la main, en coordonnées absolues : rien
 * dans le code n'empêche d'en placer une sur une autre, et le défaut ne se verrait qu'en jouant,
 * une fois la parcelle fautive achetée — donc très tard.
 */
class FarmLayoutTest {

    /** Le décor laisse ce halo autour de chaque parcelle ; deux parcelles collées s'y disputeraient. */
    private val marge = 50f

    private fun bords(land: FarmLayout.Land) =
        floatArrayOf(land.x, land.y, land.x + land.width, land.y + land.height)

    @Test
    fun `aucune parcelle n'en recouvre une autre`() {
        val rects = FarmLayout.lands.map { bords(it) }
        rects.indices.forEach { i ->
            (i + 1 until rects.size).forEach { j ->
                val a = rects[i]; val b = rects[j]
                val seTouchent = a[0] - marge < b[2] + marge && a[2] + marge > b[0] - marge &&
                    a[1] - marge < b[3] + marge && a[3] + marge > b[1] - marge
                assertTrue("les parcelles $i et $j se chevauchent", !seTouchent)
            }
        }
    }

    @Test
    fun `chaque parcelle tient dans le monde`() {
        FarmLayout.lands.forEachIndexed { i, land ->
            val r = bords(land)
            assertTrue("la parcelle $i sort par le haut", r[1] >= FarmLayout.worldTop)
            assertTrue("la parcelle $i sort par la gauche", r[0] >= 0f)
            assertTrue("la parcelle $i sort par la droite", r[2] <= FarmLayout.worldWidth)
            assertTrue("la parcelle $i sort par le bas", r[3] <= FarmLayout.worldHeight)
        }
    }

    /** Les index de plantation sont sauvegardés : découper les cellules autrement les décalerait. */
    @Test
    fun `les cellules se decoupent parcelle par parcelle, sans trou`() {
        assertEquals(FarmLayout.lands.sumOf { it.capacity }, FarmLayout.cellCount)
        var attendu = 0
        FarmLayout.lands.indices.forEach { i ->
            val plage = FarmLayout.cells(i)
            assertEquals("la parcelle $i ne commence pas où la précédente finit", attendu, plage.first)
            assertEquals(FarmLayout.lands[i].capacity, plage.count())
            plage.forEach { cellule -> assertEquals(i, FarmLayout.parcelOf(cellule)) }
            assertEquals(0, FarmLayout.localCell(plage.first))
            attendu = plage.last + 1
        }
    }
}
