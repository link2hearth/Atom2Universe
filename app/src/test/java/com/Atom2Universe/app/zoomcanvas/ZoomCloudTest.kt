package com.Atom2Universe.app.zoomcanvas

import androidx.room.Room
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.zoomcanvas.core.ImageItem
import com.Atom2Universe.app.zoomcanvas.core.LayerItem
import com.Atom2Universe.app.zoomcanvas.core.ShapeItem
import com.Atom2Universe.app.zoomcanvas.core.Stroke
import com.Atom2Universe.app.zoomcanvas.core.TextItem
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.data.LoadedZoomProject
import com.Atom2Universe.app.zoomcanvas.data.ZoomCloudAdapter
import com.Atom2Universe.app.zoomcanvas.data.ZoomDatabase
import com.Atom2Universe.app.zoomcanvas.data.ZoomStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Un projet du canvas voyage dans un fichier : ce qui arrive de l'autre côté doit être le même dessin,
 * sous le même identifiant, et un mauvais fichier ne doit rien changer.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZoomCloudTest {

    private lateinit var dirA: File
    private lateinit var dirB: File
    private lateinit var dbA: ZoomDatabase
    private lateinit var dbB: ZoomDatabase
    private lateinit var a: ZoomStore
    private lateinit var b: ZoomStore
    private lateinit var adapterA: ZoomCloudAdapter
    private lateinit var adapterB: ZoomCloudAdapter
    private val ink = 0xFF112233.toInt()

    @Before
    fun setUp() {
        dirA = File.createTempFile("zoomcloudA", "test").also { it.delete(); it.mkdirs() }
        dirB = File.createTempFile("zoomcloudB", "test").also { it.delete(); it.mkdirs() }
        dbA = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ZoomDatabase::class.java).allowMainThreadQueries().build()
        dbB = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ZoomDatabase::class.java).allowMainThreadQueries().build()
        a = ZoomStore(dirA, dbA)
        b = ZoomStore(dirB, dbB)
        adapterA = ZoomCloudAdapter(a, single = false)
        adapterB = ZoomCloudAdapter(b, single = false)
    }

    @After
    fun tearDown() {
        dbA.close(); dbB.close()
        dirA.deleteRecursively(); dirB.deleteRecursively()
    }

    private fun ZoomScene.scribble(n: Int, dx: Double = 0.0): Stroke {
        beginStroke(-50.0 + dx, 10.0, ink, 5.0)
        for (i in 1..n) extendStroke(-50.0 + dx + i * 7.0, 10.0 + (i % 3) * 9.0)
        return endStroke()!!
    }

    private fun LoadedZoomProject.save(store: ZoomStore) = store.save(meta, scene.drainChanges())

    private fun dump(scene: ZoomScene): String = buildString {
        append("cam ${scene.depth} ${scene.cx} ${scene.cy} ${scene.zoom} next=${scene.nextStrokeId}\n")
        for (d in scene.nonEmptyDepths()) {
            val l = scene.layer(d)!!
            append("layer $d ${l.ax} ${l.ay}\n")
            for (item in l.drawOrder()) append("  ").append(describe(item)).append('\n')
        }
    }

    private fun describe(i: LayerItem): String = when (i) {
        is Stroke -> "stroke ${i.id} ${i.x} ${i.y} ${i.color} ${i.width} ${i.kind} ${i.pts.joinToString(",")}"
        is ImageItem -> "image ${i.id} ${i.key} ${i.pxW}x${i.pxH} ${i.x} ${i.y} ${i.w} ${i.h}"
        is ShapeItem -> "shape ${i.id} $i"
        is TextItem -> "text ${i.id} $i"
        else -> error("unexpected $i")
    }

    private fun exported(store: ZoomStore, id: String): ByteArray =
        ByteArrayOutputStream().also { store.exportProject(id, it) }.toByteArray()

    /** Un projet à plusieurs couches, de tout un peu, avec une image importée sur le disque. */
    private fun richProject(): LoadedZoomProject {
        val p = a.create("Mise en abyme", ZoomScene.DEFAULT_RATIO)
        val scene = p.scene
        scene.scribble(12)
        scene.beginShape(ShapeKind.ELLIPSE, ShapeFill.BOTH, 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 4.0, -80.0, -60.0, false)
        scene.updateShape(90.0, 70.0)
        scene.endShape()
        scene.addText("Bonjour", 10.0, -100.0, 24.0, ink, 0, "Serif", 6.5)
        scene.addImage("pic.png", 64, 48, 100.0, 100.0)
        repeat(30) { scene.zoomAt(1.9, 21.0, -13.0) }
        scene.scribble(5, 30.0)
        p.save(a)
        a.saveImage(p.meta.id, "pic.png", ByteArray(300) { (it * 7).toByte() })
        a.saveThumb(p.meta.id, ByteArray(40) { it.toByte() })
        return p
    }

    @Test
    fun aProjectComesBackIdenticalUnderTheSameIdAndDate() {
        val p = richProject()
        val bytes = exported(a, p.meta.id)

        assertNull(adapterB.localModified(p.meta.id))
        assertTrue(adapterB.importAs(p.meta.id, ByteArrayInputStream(bytes)))

        assertEquals(adapterA.localModified(p.meta.id), adapterB.localModified(p.meta.id))
        assertEquals("Mise en abyme", adapterB.nameOf(p.meta.id))
        val back = b.load(p.meta.id, all = true)!!
        assertEquals(dump(a.load(p.meta.id, all = true)!!.scene), dump(back.scene))
        assertEquals(setOf("pic.png"), back.scene.imageKeys())
        assertArrayEquals(a.imageFile(p.meta.id, "pic.png").readBytes(), b.imageFile(p.meta.id, "pic.png").readBytes())
        assertArrayEquals(a.thumbFile(p.meta.id).readBytes(), b.thumbFile(p.meta.id).readBytes())
        // Aucun dossier de travail ne reste.
        assertEquals(listOf(p.meta.id), dirB.list()!!.toList())
        // Et on peut continuer à dessiner : les identifiants ne se répètent pas.
        back.scene.scribble(3)
        val ids = back.scene.allLayers().flatMap { l -> l.entries().map { it.id } }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun replacingTakesTheCloudDrawingAndLeavesOneProject() {
        val p = richProject()
        val bytes = exported(a, p.meta.id)
        assertTrue(adapterB.importAs(p.meta.id, ByteArrayInputStream(bytes)))

        // Ici, on a dessiné autre chose dessus ; le cloud, lui, porte encore l'ancien dessin.
        val local = b.load(p.meta.id)!!
        local.scene.scribble(30, 200.0)
        local.meta.modified = System.currentTimeMillis() + 10
        local.save(b)
        val localDump = dump(local.scene)

        assertTrue(adapterB.importAs(p.meta.id, ByteArrayInputStream(bytes)))
        val after = b.load(p.meta.id, all = true)!!
        assertNotEquals(localDump, dump(after.scene))
        assertEquals(dump(a.load(p.meta.id, all = true)!!.scene), dump(after.scene))
        assertEquals(1, b.list().size)
        assertEquals(listOf(p.meta.id), dirB.list()!!.toList())
    }

    @Test
    fun aBrokenFileChangesNothing() {
        val p = richProject()
        val bytes = exported(a, p.meta.id)
        assertTrue(adapterB.importAs(p.meta.id, ByteArrayInputStream(bytes)))
        val before = dump(b.load(p.meta.id, all = true)!!.scene)
        val modifiedBefore = adapterB.localModified(p.meta.id)

        // Du bruit, un zip coupé en plein milieu d'une entrée (couper seulement l'index final ne perdrait
        // rien : le contenu est déjà entier), un zip sans manifeste.
        assertFalse(adapterB.importAs(p.meta.id, ByteArrayInputStream(ByteArray(64) { it.toByte() })))
        assertFalse(adapterB.importAs(p.meta.id, ByteArrayInputStream(bytes.copyOf(bytes.size - 450))))
        assertFalse(adapterB.importAs(p.meta.id, ByteArrayInputStream(ByteArrayOutputStream().also {
            ZipOutputStream(it).use { z -> z.putNextEntry(ZipEntry("items.bin")); z.write(1); z.closeEntry() }
        }.toByteArray())))

        assertEquals(modifiedBefore, adapterB.localModified(p.meta.id))
        assertEquals(before, dump(b.load(p.meta.id, all = true)!!.scene))
        assertEquals(setOf("pic.png"), b.load(p.meta.id, all = true)!!.scene.imageKeys())
        assertEquals(listOf(p.meta.id), dirB.list()!!.toList())
    }

    @Test
    fun aTruncatedItemListIsRefusedEvenWhenTheZipIsWellFormed() {
        val p = richProject()
        val bytes = exported(a, p.meta.id)
        // On recoupe items.bin en son milieu : le zip reste valide, le dessin est incomplet.
        val cut = ByteArrayOutputStream()
        ZipOutputStream(cut).use { out ->
            ZipInputStream(ByteArrayInputStream(bytes)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val data = zin.readBytes()
                    out.putNextEntry(ZipEntry(e.name))
                    out.write(if (e.name == "items.bin") data.copyOf(data.size / 2) else data)
                    out.closeEntry()
                }
            }
        }
        assertFalse(adapterB.importAs(p.meta.id, ByteArrayInputStream(cut.toByteArray())))
        assertNull(adapterB.localModified(p.meta.id))
        assertEquals(0, dirB.list()!!.size)
    }

    @Test
    fun keepingBothMakesANewProjectNamedWithTheSuffix() {
        val p = richProject()
        val bytes = exported(a, p.meta.id)
        val copyId = adapterA.importCopy(ByteArrayInputStream(bytes), "(cloud)")
        assertNotNull(copyId)
        assertNotEquals(p.meta.id, copyId)
        assertEquals("Mise en abyme (cloud)", adapterA.nameOf(copyId!!))
        assertEquals(2, a.list().size)
        assertEquals(dump(a.load(p.meta.id, all = true)!!.scene), dump(a.load(copyId, all = true)!!.scene))
    }

    @Test
    fun aCanvasProjectCannotBeImportedAsAnInfiniteOneAndTheOtherWayRound() {
        val flat = a.create("flat", ZoomScene.DEFAULT_RATIO, single = true)
        flat.scene.scribble(4)
        flat.save(a)
        val bytes = exported(a, flat.meta.id)

        assertFalse(adapterB.importAs(flat.meta.id, ByteArrayInputStream(bytes)))
        val canvasB = ZoomCloudAdapter(b, single = true)
        assertTrue(canvasB.importAs(flat.meta.id, ByteArrayInputStream(bytes)))
        assertTrue(b.load(flat.meta.id)!!.scene.single)
        assertEquals(1, b.list(single = true).size)
        assertEquals(0, b.list(single = false).size)
    }

    @Test
    fun theSingleCanvasPixelLayerTravelsToo() {
        val flat = a.create("flat", ZoomScene.DEFAULT_RATIO, single = true)
        flat.scene.beginPixelEdit()
        for (x in 0 until 70) flat.scene.paintCell(x, x / 2, 0xFFFF0000.toInt())
        flat.scene.endPixelEdit()
        flat.scene.scribble(3)
        flat.save(a)
        val canvasB = ZoomCloudAdapter(b, single = true)
        assertTrue(canvasB.importAs(flat.meta.id, ByteArrayInputStream(exported(a, flat.meta.id))))

        val before = a.load(flat.meta.id)!!.scene
        val after = b.load(flat.meta.id)!!.scene
        assertEquals(dump(before), dump(after))
        for (x in 0 until 70) assertEquals("cellule $x", before.pixels.get(x, x / 2), after.pixels.get(x, x / 2))
    }
}
