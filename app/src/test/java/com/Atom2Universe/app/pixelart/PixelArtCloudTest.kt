package com.Atom2Universe.app.pixelart

import com.Atom2Universe.app.cloud.CloudInventory
import com.Atom2Universe.app.cloud.projects.CloudModule
import com.Atom2Universe.app.cloud.projects.CloudProjectSync
import com.Atom2Universe.app.cloud.projects.CloudProjectSync.Decision
import com.Atom2Universe.app.pixelart.core.EditorSession
import com.Atom2Universe.app.pixelart.core.Tool
import com.Atom2Universe.app.pixelart.io.ImageFormat
import com.Atom2Universe.app.pixelart.io.PixelArtCloudAdapter
import com.Atom2Universe.app.pixelart.io.ProjectStore
import com.Atom2Universe.app.pixelart.io.SourceLink
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Ce que le cloud demande au pixel art, sans Drive : un projet part dans un fichier et revient sous le
 * même identifiant, sans jamais abîmer l'exemplaire local quand le fichier est mauvais.
 */
class PixelArtCloudTest {

    private lateinit var dir: File
    private lateinit var store: ProjectStore
    private lateinit var adapter: PixelArtCloudAdapter
    private val RED = 0xFFFF0000.toInt()
    private val BLUE = 0xFF0000FF.toInt()

    @Before
    fun setUp() {
        dir = File.createTempFile("pixelart", "cloud").also { it.delete(); it.mkdirs() }
        store = ProjectStore(dir)
        adapter = PixelArtCloudAdapter(store)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun exported(id: String): ByteArray = ByteArrayOutputStream().also { adapter.exportTo(id, it) }.toByteArray()

    private fun pixelAt(id: String, x: Int, y: Int, w: Int): Int {
        val p = store.load(id)!!
        return p.doc.cel(p.doc.layers[0].id, p.doc.frames[0].id)!![y * w + x]
    }

    private fun draw(id: String, x: Int, y: Int, c: Int) {
        val p = store.load(id)!!
        val s = EditorSession(p.doc)
        s.primary = c
        s.tool = Tool.PENCIL
        s.pointerDown(x, y); s.pointerUp()
        store.saveNow(p)
    }

    @Test
    fun unProjetRevientSousLeMemeIdentifiantAvecSaDate() {
        val p = store.create("Sprite", 8, 8)
        draw(p.meta.id, 1, 1, RED)
        val sentModified = adapter.localModified(p.meta.id)!!
        val bytes = exported(p.meta.id)

        // Un autre appareil : un dossier de projets vide.
        val otherDir = File.createTempFile("pixelart", "other").also { it.delete(); it.mkdirs() }
        try {
            val other = PixelArtCloudAdapter(ProjectStore(otherDir))
            assertNull(other.localModified(p.meta.id))
            assertTrue(other.importAs(p.meta.id, ByteArrayInputStream(bytes)))
            // Même identifiant, et la date de modification du projet d'origine : c'est elle que la sync compare ensuite.
            assertEquals(sentModified, other.localModified(p.meta.id))
            assertEquals("Sprite", other.nameOf(p.meta.id))
        } finally {
            otherDir.deleteRecursively()
        }
    }

    @Test
    fun remplacerGardeLeLienLocalEtPrendLeDessinDuCloud() {
        val p = store.create("Sprite", 8, 8)
        draw(p.meta.id, 1, 1, RED)
        val cloud = exported(p.meta.id)

        // Ici, on a depuis relié le projet à une image de l'appareil et dessiné autre chose.
        store.setLink(p.meta.id, SourceLink("content://exemple/doc/1", "sprite.png", ImageFormat.PNG))
        draw(p.meta.id, 5, 5, BLUE)

        assertTrue(adapter.importAs(p.meta.id, ByteArrayInputStream(cloud)))
        val back = store.load(p.meta.id)!!
        assertEquals("sprite.png", back.meta.link?.name)
        assertEquals(RED, pixelAt(p.meta.id, 1, 1, 8))
        assertEquals(0, pixelAt(p.meta.id, 5, 5, 8))
        // Aucun dossier de travail ne traîne, et la liste ne montre qu'un projet.
        assertEquals(listOf(p.meta.id), store.list().map { it.id })
        assertEquals(listOf(p.meta.id), dir.list()!!.toList())
    }

    @Test
    fun unFichierIllisibleLaisseLeProjetLocalIntact() {
        val p = store.create("Sprite", 8, 8)
        draw(p.meta.id, 2, 3, RED)
        val before = adapter.localModified(p.meta.id)

        assertFalse(adapter.importAs(p.meta.id, ByteArrayInputStream(ByteArray(40) { it.toByte() })))
        // Un zip vide, ou sans manifeste, n'est pas un projet non plus.
        assertFalse(adapter.importAs(p.meta.id, ByteArrayInputStream(ByteArrayOutputStream().also {
            java.util.zip.ZipOutputStream(it).use { z -> z.putNextEntry(java.util.zip.ZipEntry("autre.txt")); z.write(1); z.closeEntry() }
        }.toByteArray())))

        assertEquals(before, adapter.localModified(p.meta.id))
        assertEquals(RED, pixelAt(p.meta.id, 2, 3, 8))
        assertEquals(listOf(p.meta.id), dir.list()!!.toList())
    }

    @Test
    fun uneCelAbimeeFaitRefuserLeFichier() {
        val p = store.create("Sprite", 8, 8)
        draw(p.meta.id, 2, 3, RED)
        val bytes = exported(p.meta.id)
        // On remplace le contenu de la cel par du bruit, comme un téléchargement tronqué.
        val broken = ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(broken).use { out ->
            java.util.zip.ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    out.putNextEntry(java.util.zip.ZipEntry(e.name))
                    if (e.name.endsWith(".cel")) out.write(ByteArray(30) { 7 }) else zin.copyTo(out)
                    out.closeEntry()
                }
            }
        }
        val otherDir = File.createTempFile("pixelart", "other").also { it.delete(); it.mkdirs() }
        try {
            val other = PixelArtCloudAdapter(ProjectStore(otherDir))
            assertFalse(other.importAs("abc", ByteArrayInputStream(broken.toByteArray())))
            assertNull(other.localModified("abc"))
            assertEquals(0, otherDir.list()!!.size)
        } finally {
            otherDir.deleteRecursively()
        }
    }

    @Test
    fun gardeLesDeuxFaitUneCopieSansLienNiIdentifiantCommun() {
        val p = store.create("Sprite", 8, 8)
        draw(p.meta.id, 1, 1, RED)
        store.setLink(p.meta.id, SourceLink("content://exemple/doc/1", "sprite.png", ImageFormat.PNG))
        val bytes = exported(p.meta.id)

        val copyId = adapter.importCopy(ByteArrayInputStream(bytes), "(cloud)")
        assertNotNull(copyId)
        assertNotEquals(p.meta.id, copyId)
        assertEquals("Sprite (cloud)", store.nameOf(copyId!!))
        assertNull(store.load(copyId)!!.meta.link)
        assertEquals(2, store.list().size)
    }

    @Test
    fun renommerEstUnChangementPourLaSync() {
        val p = store.create("A", 4, 4)
        val before = adapter.localModified(p.meta.id)!!
        Thread.sleep(5)
        store.rename(p.meta.id, "B")
        assertTrue(adapter.localModified(p.meta.id)!! > before)
    }

    @Test
    fun ouvrirEtFermerSansDessinerNeChangePasLaDate() {
        val p = store.create("A", 4, 4)
        val before = adapter.localModified(p.meta.id)!!
        Thread.sleep(5)
        // Ce que fait l'éditeur en se fermant : tout recharger et tout réécrire, sans rien changer.
        val opened = store.load(p.meta.id)!!
        store.saveNow(opened)
        assertEquals(before, adapter.localModified(p.meta.id))
        // Dessiner, lui, la change.
        draw(p.meta.id, 0, 0, RED)
        assertTrue(adapter.localModified(p.meta.id)!! > before)
    }

    @Test
    fun laRegleDeSyncDistingueLesQuatreSituations() {
        // Jamais envoyé, cloud vide de souvenir : les deux côtés « ont bougé » -> on demande.
        assertEquals(Decision.CONFLICT, CloudProjectSync.decide(localModified = 100, sent = null, remoteSeq = 7, baseSeq = 0))
        // Rien n'a bougé nulle part.
        assertEquals(Decision.NOTHING, CloudProjectSync.decide(100, sent = 100, remoteSeq = 7, baseSeq = 7))
        // Seul ce côté a dessiné : on envoie.
        assertEquals(Decision.UPLOAD, CloudProjectSync.decide(150, sent = 100, remoteSeq = 7, baseSeq = 7))
        // Seul l'autre appareil a publié : on télécharge.
        assertEquals(Decision.DOWNLOAD, CloudProjectSync.decide(100, sent = 100, remoteSeq = 9, baseSeq = 7))
        // Les deux ont bougé : conflit.
        assertEquals(Decision.CONFLICT, CloudProjectSync.decide(150, sent = 100, remoteSeq = 9, baseSeq = 7))
    }

    @Test
    fun lesNomsDeFichiersCloudSeReconnaissentEtSeClassent() {
        assertEquals("px_ab12.zip", CloudModule.PIXEL_ART.fileName("ab12"))
        assertEquals("ab12", CloudModule.PIXEL_ART.idOf("px_ab12.zip"))
        assertNull(CloudModule.PIXEL_ART.idOf("zc_ab12.zip"))
        assertNull(CloudModule.PIXEL_ART.idOf("px_.zip"))
        assertEquals(CloudModule.CANVAS, CloudModule.ofFile("cv_x-1.zip"))
        assertNull(CloudModule.ofFile("games_state.json"))

        assertEquals(CloudInventory.CloudCategory.PIXELART, CloudInventory.categorize("px_ab12.zip"))
        assertEquals(CloudInventory.CloudCategory.ZOOM, CloudInventory.categorize("zc_ab12.zip"))
        assertEquals(CloudInventory.CloudCategory.CANVAS, CloudInventory.categorize("cv_ab12.zip"))
        // Les noms déjà connus gardent leur catégorie, et l'inconnu reste inconnu.
        assertEquals(CloudInventory.CloudCategory.GAMES, CloudInventory.categorize("games_state.json"))
        assertEquals(CloudInventory.CloudCategory.UNKNOWN, CloudInventory.categorize("px_sans_extension"))
    }
}
