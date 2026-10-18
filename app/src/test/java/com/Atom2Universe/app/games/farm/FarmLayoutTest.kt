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
            assertTrue("la parcelle $i sort par le haut", r[1] >= 0f)
            assertTrue("la parcelle $i sort par la gauche", r[0] >= 0f)
            assertTrue("la parcelle $i sort par la droite", r[2] <= FarmLayout.worldWidth)
            assertTrue("la parcelle $i sort par le bas", r[3] <= FarmLayout.worldHeight)
        }
    }

    /**
     * Le plan a été entièrement redessiné, mais les cellules sont sauvegardées par index : si une des
     * quatorze parcelles d'avant changeait de taille, toutes les plantations suivantes glisseraient
     * d'une case, et la sauvegarde refuserait de se charger.
     */
    @Test
    fun `les quatorze parcelles d'origine gardent leur taille`() {
        val tailles = listOf(4 to 3, 3 to 4, 6 to 3, 4 to 4, 5 to 4, 4 to 5, 3 to 2, 6 to 5, 5 to 3,
            7 to 4, 4 to 6, 6 to 6, 6 to 3, 4 to 4)
        assertEquals(tailles, FarmLayout.lands.take(tailles.size).map { it.columns to it.rows })
    }

    /** La cour (hangar et puits) et le buisson au trésor sont posés par le plan, pas à la main. */
    @Test
    fun `la cour et le buisson ne mordent sur aucune parcelle`() {
        listOf("la cour" to FarmLayout.yard, "le buisson" to FarmLayout.treasure).forEach { (nom, zone) ->
            assertTrue("$nom sort du monde", zone.left >= 0f && zone.top >= 0f &&
                zone.right <= FarmLayout.worldWidth && zone.bottom <= FarmLayout.worldHeight)
            FarmLayout.lands.forEachIndexed { i, land ->
                val r = bords(land)
                val touche = zone.left < r[2] + marge && zone.right > r[0] - marge &&
                    zone.top < r[3] + marge && zone.bottom > r[1] - marge
                assertTrue("$nom touche la parcelle $i", !touche)
            }
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
