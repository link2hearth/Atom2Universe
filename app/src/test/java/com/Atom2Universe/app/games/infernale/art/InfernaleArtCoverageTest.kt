package com.Atom2Universe.app.games.infernale.art

import com.Atom2Universe.app.games.physics.catalog.MachinePartCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le contrat entre le catalogue et la bibliothèque de dessins : **une pièce, un dessin**.
 *
 * Il existait déjà une vérification, dans `tools/infernale/ExportArt.kt`, et elle est
 * bonne — mais elle se lance à la main, en PowerShell, et elle relit le catalogue en le
 * grattant à l'expression régulière. Deux choses lui échappent donc, et ce sont
 * exactement les deux qui font mal :
 *
 *  1. **Personne ne la lance.** Une pièce ajoutée au catalogue un mardi ne fera pas
 *     broncher `./gradlew testDebugUnitTest`, qui est pourtant ce qu'on lance vraiment.
 *  2. **Elle ne regarde que le raster**, pas la table `InfernalePartArt.shapes`. Or
 *     `draw()` commence par `if (id !in shapes) return` : une pièce absente de cette
 *     table n'est pas une erreur, elle est **invisible**. Silencieusement. C'est la
 *     panne la plus désagréable qui soit — celle qui ne laisse aucune trace.
 *
 * Ce test-ci lit le vrai objet `MachinePartCatalog` au lieu de gratter son fichier, et
 * ferme les deux portes.
 */
class InfernaleArtCoverageTest {

    private val ids: List<String> get() = MachinePartCatalog.definitions.map { it.id }

    @Test
    fun `chaque piece du catalogue a une silhouette declaree`() {
        val sansForme = ids.filterNot { it in InfernalePartArt.shapes }
        assertTrue(
            "ces pièces seraient invisibles dans le jeu, sans aucune erreur : $sansForme",
            sansForme.isEmpty()
        )
    }

    @Test
    fun `aucune silhouette ne designe une piece qui n existe plus`() {
        val orphelines = InfernalePartArt.shapes.keys.filterNot { it in ids.toSet() }
        assertTrue("ces dessins ne correspondent à aucune pièce : $orphelines",
            orphelines.isEmpty())
    }

    @Test
    fun `chaque piece du catalogue se dessine vraiment`() {
        // Le raster lève une erreur sur un identifiant inconnu, donc il suffit de tout
        // parcourir. On vérifie en plus qu'il en sort des pixels : une branche vide
        // passerait sans bruit.
        val raster = InfernalePixels()
        for (id in ids) {
            val pixels = raster.render(id, 0.7f, 0.3f, 0.6f, true, 0.25f)
            assertTrue("dessin vide pour $id", pixels.any { it != 0 })
        }
    }

    @Test
    fun `le dessin ne depend que de l etat qu on lui donne`() {
        // Le renderer ne doit tenir aucune horloge : deux appels identiques donnent les
        // mêmes pixels, sinon la pause du jeu ne figerait pas l'image.
        val raster = InfernalePixels()
        for (id in ids) {
            val premier = raster.render(id, 1.1f, 0.4f, 0.7f, false, 0.5f).copyOf()
            val second = raster.render(id, 1.1f, 0.4f, 0.7f, false, 0.5f)
            assertTrue("le dessin de $id change tout seul d'un appel à l'autre",
                premier.contentEquals(second))
        }
    }

    @Test
    fun `un etat aberrant ne fait pas tomber le dessin`() {
        // La simulation peut très bien produire un NaN le jour où une liaison casse ;
        // le dessin ne doit pas être ce qui plante.
        val raster = InfernalePixels()
        for (id in ids) {
            raster.render(id, Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, true, Float.NEGATIVE_INFINITY)
            raster.render(id, -1e9f, -5f, 12f, false, -3.5f)
        }
    }

    @Test
    fun `le catalogue et la bibliotheque ont exactement la meme taille`() {
        // Le compte est aussi une alarme : il ne bouge que quand on ajoute une pièce,
        // et ce jour-là on veut relire les deux listes ensemble.
        assertEquals("le catalogue et les dessins ont divergé",
            ids.size, InfernalePartArt.shapes.size)
    }
}
