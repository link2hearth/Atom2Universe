package com.Atom2Universe.app.music.sync

import com.Atom2Universe.app.music.sync.model.SyncFavoriteEntry
import com.Atom2Universe.app.music.sync.model.TrackIdentifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Garde-fou de la synchro des favoris : les fusionneurs decident « qui gagne » avec
 * `isActive` et `getLastModifiedTimestamp` (le plus recent l'emporte). Si ces deux petites
 * regles derivent, un favori retire sur le telephone reapparait depuis le cloud, ou
 * l'inverse, sans aucun message d'erreur.
 */
class SyncEntryTest {

    private fun entry(addedAt: Long, removedAt: Long? = null) =
        SyncFavoriteEntry("k", "a", "t", "al", addedAt, removedAt)

    @Test fun unFavoriJamaisRetireEstActif() {
        assertTrue(entry(addedAt = 100).isActive())
    }

    @Test fun retirerPuisReajouterReactive() {
        assertFalse("retire apres l'ajout", entry(addedAt = 100, removedAt = 200).isActive())
        assertTrue("reajoute apres le retrait", entry(addedAt = 300, removedAt = 200).isActive())
    }

    @Test fun laDateDeModificationEstLaPlusRecenteDesDeux() {
        assertEquals(100L, entry(addedAt = 100).getLastModifiedTimestamp())
        assertEquals(200L, entry(addedAt = 100, removedAt = 200).getLastModifiedTimestamp())
        assertEquals(300L, entry(addedAt = 300, removedAt = 200).getLastModifiedTimestamp())
    }

    @Test fun laCleDUnFavoriEstArtisteTitreAlbumSansCasseNiEspaces() {
        val created = SyncFavoriteEntry.createActive("  Daft Punk ", "Around The World", "HOMEWORK")
        assertEquals("daft punk|around the world|homework", created.key)
        assertTrue(created.isActive())
    }

    @Test fun lIdentifiantDeMorceauFaitLAllerRetourAvecSaCle() {
        val id = TrackIdentifier("Daft Punk", "Around The World", "Homework")
        assertEquals("daft punk|around the world|homework", id.toMetadataKey())
        val back = TrackIdentifier.fromMetadataKey(id.toMetadataKey())
        assertNotNull(back)
        assertEquals(id.toMetadataKey(), back!!.toMetadataKey())
        assertNull("une cle mal formee est refusee", TrackIdentifier.fromMetadataKey("pas|une cle"))
    }
}
