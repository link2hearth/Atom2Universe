package com.Atom2Universe.app.zoomcanvas

import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import androidx.room.Room
import com.Atom2Universe.app.pixelart.core.ShapeFill
import com.Atom2Universe.app.pixelart.core.ShapeKind
import com.Atom2Universe.app.zoomcanvas.core.PixelLayer
import com.Atom2Universe.app.zoomcanvas.core.ZoomScene
import com.Atom2Universe.app.zoomcanvas.data.ZoomDatabase
import com.Atom2Universe.app.zoomcanvas.data.ZoomStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * La couche de pixels du « Canvas » : la grille et son historique, les outils (au doigt, sur la vraie vue),
 * le rendu avec de vrais pixels, la sauvegarde dans la base et la migration de la base 2 vers la 3.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ZoomPixelTest {

    private val red = 0xFFFF0000.toInt()
    private val blue = 0xFF0000FF.toInt()

    private lateinit var dir: File
    private lateinit var db: ZoomDatabase
    private lateinit var store: ZoomStore

    @Before
    fun setUp() {
        dir = File.createTempFile("zoompixel", "test").also { it.delete(); it.mkdirs() }
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), ZoomDatabase::class.java).allowMainThreadQueries().build()
        store = ZoomStore(dir, db)
    }

    @After
    fun tearDown() {
        db.close()
        dir.deleteRecursively()
    }

    private fun scene() = ZoomScene.create(single = true)

    private fun ZoomScene.paint(vararg cells: Pair<Int, Int>, color: Int = red) {
        beginPixelEdit()
        for ((x, y) in cells) paintCell(x, y, color)
        endPixelEdit()
    }

    // ---- La grille ----

    @Test
    fun cellsLiveInTilesAndEmptyTilesDisappear() {
        val l = PixelLayer()
        assertTrue(l.set(5, 7, red))
        assertFalse("même couleur : rien n'a changé", l.set(5, 7, red))
        assertEquals(red, l.get(5, 7))
        assertEquals(0, l.get(6, 7))
        assertEquals(1, l.tileCount)
        // Négatif : tuile (−1, −1), sans fausse division.
        l.set(-1, -1, blue)
        assertEquals(blue, l.get(-1, -1))
        assertEquals(2, l.tileCount)
        assertTrue(l.set(5, 7, 0))
        assertTrue(l.set(-1, -1, 0x00123456))
        assertTrue("sans une case colorée, plus de tuile", l.isEmpty)
        assertNull(l.bounds())
    }

    @Test
    fun boundsCoverEveryCell() {
        val l = PixelLayer()
        l.set(-70, 3, red)
        l.set(130, -9, red)
        assertEquals(listOf(-70, -9, 130, 3), l.bounds()!!.toList())
    }

    @Test
    fun tileBytesRoundTripAndBadBytesAreRejected() {
        val px = IntArray(PixelLayer.SIZE * PixelLayer.SIZE) { if (it % 7 == 0) red else 0 }
        val bytes = PixelLayer.encode(px)
        assertTrue("compressé", bytes.size < px.size * 4 / 2)
        assertTrue(px.contentEquals(PixelLayer.decode(bytes)!!))
        assertNull(PixelLayer.decode(byteArrayOf(1, 2, 3, 4, 5)))
    }

    @Test
    fun drainGivesOnlyWhatChangedAndRequeueGivesItBack() {
        val l = PixelLayer()
        l.set(1, 1, red)
        l.set(500, 500, blue)
        val d = l.drain()
        assertEquals(2, d.upserts.size)
        assertFalse(l.hasUnsaved)
        l.set(1, 1, blue)
        val d2 = l.drain()
        assertEquals(listOf(PixelLayer.tileKey(0, 0)), d2.upserts.map { it.first })
        // L'écriture a échoué : la tuile repart.
        l.requeue(d2)
        assertTrue(l.hasUnsaved)
        assertEquals(1, l.drain().upserts.size)
        // Une tuile qui se vide est à effacer de la base.
        l.set(1, 1, 0)
        assertEquals(listOf(PixelLayer.tileKey(0, 0)), l.drain().deletes.toList())
    }

    // ---- Historique ----

    @Test
    fun aGestureIsOneUndoStep() {
        val s = scene()
        s.paint(1 to 1, 2 to 1, 3 to 1)
        assertEquals(red, s.pixels.get(2, 1))
        assertTrue(s.canUndo)
        assertTrue(s.undo())
        assertTrue(s.pixels.isEmpty)
        assertFalse(s.canUndo)
        assertTrue(s.redo())
        assertEquals(red, s.pixels.get(3, 1))
    }

    @Test
    fun overpaintingUndoesBackToThePreviousColor() {
        val s = scene()
        s.paint(0 to 0, color = red)
        s.paint(0 to 0, color = blue)
        s.undo()
        assertEquals(red, s.pixels.get(0, 0))
    }

    @Test
    fun cancelledOrEmptyGesturesLeaveNoTrace() {
        val s = scene()
        s.paint(2 to 2)
        s.beginPixelEdit()
        s.paintCell(2, 2, blue)
        s.paintCell(9, 9, blue)
        s.cancelPixelEdit()
        assertEquals(red, s.pixels.get(2, 2))
        assertEquals(0, s.pixels.get(9, 9))
        // Peindre ce qui y est déjà : pas de pas d'historique.
        s.beginPixelEdit()
        s.paintCell(2, 2, red)
        assertFalse(s.endPixelEdit())
        s.undo()
        assertFalse(s.canUndo)
    }

    @Test
    fun fillStopsAtWallsAndAtItsBox() {
        val s = scene()
        // Une boîte 5 × 5, bords compris.
        val wall = ArrayList<Pair<Int, Int>>()
        for (i in 0..4) { wall.add(i to 0); wall.add(i to 4); wall.add(0 to i); wall.add(4 to i) }
        s.paint(*wall.toTypedArray())
        assertTrue(s.fillPixels(2, 2, blue, -10, -10, 10, 10))
        assertEquals(9, (1..3).sumOf { x -> (1..3).count { y -> s.pixels.get(x, y) == blue } })
        assertEquals("rien ne fuit", 0, s.pixels.get(5, 2))
        // Dehors, dans la boîte de recherche : tout ce qui reste.
        assertTrue(s.fillPixels(8, 8, 0xFF00FF00.toInt(), -10, -10, 10, 10))
        assertEquals(0xFF00FF00.toInt(), s.pixels.get(-10, 10))
        // Une seule fois d'historique chacun.
        s.undo()
        assertEquals(0, s.pixels.get(8, 8))
        assertEquals(blue, s.pixels.get(2, 2))
        // Même couleur, case hors boîte, boîte énorme : rien.
        assertFalse(s.fillPixels(2, 2, blue, -10, -10, 10, 10))
        assertFalse(s.fillPixels(50, 50, blue, -10, -10, 10, 10))
        assertFalse(s.fillPixels(0, 0, blue, -2000, -2000, 2000, 2000))
    }

    @Test
    fun snappedCameraPutsCellEdgesOnWholeScreenPixels() {
        val s = scene()
        s.zoomAt(8.0, 0.0, 0.0)
        s.pan(13.3, -7.7)
        for (w in doubleArrayOf(800.0, 801.0)) {
            s.snapCameraToPixels(w, 600.0)
            for (cell in intArrayOf(-3, 0, 5, 12)) {
                val x = (cell - s.cx) * s.zoom + w / 2
                assertEquals("bord de la case $cell (largeur $w)", Math.rint(x), x, 1e-6)
            }
            val y = (4 - s.cy) * s.zoom + 300
            assertEquals(Math.rint(y), y, 1e-6)
        }
    }

    @Test
    fun fitToContentIncludesThePixels() {
        val s = scene()
        s.paint(100 to 100, 131 to 115)
        s.jumpTo(0, 800.0, 600.0, ZoomScene.PIXEL_FIT_MAX_ZOOM)
        assertEquals(116.0, s.cx, 1.0)
        assertEquals(108.0, s.cy, 1.0)
        assertTrue(s.zoom > 10.0 && s.zoom < ZoomScene.PIXEL_FIT_MAX_ZOOM)
    }

    // ---- La vue : outils au doigt, rendu ----

    private val view by lazy {
        ZoomCanvasView(RuntimeEnvironment.getApplication()).also {
            it.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(600, View.MeasureSpec.EXACTLY))
            it.layout(0, 0, 800, 600)
        }
    }

    /** Une scène à 8 pixels par case, centrée sur la case (0, 0) : la case (x, y) occupe l'écran [400 + 8x, 408 + 8x) × [300 + 8y, 308 + 8y). */
    private fun open(): ZoomScene {
        val s = scene()
        s.zoomAt(8.0, 0.0, 0.0)
        view.scene = s
        view.pixelMode = true
        view.color = red
        view.fillColor = blue
        return s
    }

    private fun cx(cell: Int) = 404f + 8 * cell
    private fun cy(cell: Int) = 304f + 8 * cell

    private var downAt = 0L

    private fun touch(action: Int, x: Float, y: Float, at: Long = SystemClock.uptimeMillis()) {
        if (action == MotionEvent.ACTION_DOWN) downAt = at
        val e = MotionEvent.obtain(downAt, at, action, x, y, 0)
        view.dispatchTouchEvent(e)
        e.recycle()
    }

    private fun drag(vararg cells: Pair<Int, Int>) {
        touch(MotionEvent.ACTION_DOWN, cx(cells[0].first), cy(cells[0].second))
        for (c in cells.drop(1)) touch(MotionEvent.ACTION_MOVE, cx(c.first), cy(c.second))
        touch(MotionEvent.ACTION_UP, cx(cells.last().first), cy(cells.last().second))
    }

    @Test
    fun brushDrawsAContinuousLineOfCells() {
        val s = open()
        view.tool = ZoomCanvasView.Tool.PIXEL_PEN
        view.strokeSize = 1f
        view.pixelPerfect = false
        drag(0 to 0, 6 to 3)
        // Un doigt qui saute de 6 cases : la ligne se remplit, sans trou.
        val cells = (0..6).flatMap { x -> (0..3).map { y -> x to y } }.filter { (x, y) -> s.pixels.get(x, y) == red }
        assertEquals(7, cells.size)
        assertEquals(red, s.pixels.get(0, 0))
        assertEquals(red, s.pixels.get(6, 3))
        assertTrue(s.undo())
        assertTrue(s.pixels.isEmpty)
    }

    @Test
    fun pixelPerfectRemovesTheCornerOfAnL() {
        val s = open()
        view.tool = ZoomCanvasView.Tool.PIXEL_PEN
        view.strokeSize = 1f
        view.pixelPerfect = true
        drag(0 to 0, 1 to 0, 1 to 1)
        assertEquals(red, s.pixels.get(0, 0))
        assertEquals(red, s.pixels.get(1, 1))
        assertEquals("le coin est retiré", 0, s.pixels.get(1, 0))
        s.undo()
        view.pixelPerfect = false
        drag(0 to 0, 1 to 0, 1 to 1)
        assertEquals(red, s.pixels.get(1, 0))
    }

    @Test
    fun bigBrushPaintsABlockAndEraserEmptiesIt() {
        val s = open()
        view.tool = ZoomCanvasView.Tool.PIXEL_PEN
        view.strokeSize = 3f
        view.pixelRound = false
        drag(10 to 10)
        assertEquals(9, (9..11).sumOf { x -> (9..11).count { y -> s.pixels.get(x, y) == red } })
        view.tool = ZoomCanvasView.Tool.PIXEL_ERASER
        view.strokeSize = 1f
        drag(10 to 10)
        assertEquals(0, s.pixels.get(10, 10))
        assertEquals(red, s.pixels.get(9, 9))
    }

    @Test
    fun aSecondFingerRightAwayCancelsTheStroke() {
        val s = open()
        view.tool = ZoomCanvasView.Tool.PIXEL_PEN
        view.strokeSize = 1f
        val t0 = SystemClock.uptimeMillis()
        touch(MotionEvent.ACTION_DOWN, cx(2), cy(2), t0)
        touch(MotionEvent.ACTION_MOVE, cx(5), cy(2), t0 + 10)
        val pinch = MotionEvent.obtain(t0, t0 + 20, MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), 2,
            arrayOf(MotionEvent.PointerProperties().apply { id = 0 }, MotionEvent.PointerProperties().apply { id = 1 }),
            arrayOf(MotionEvent.PointerCoords().apply { x = cx(5); y = cy(2) }, MotionEvent.PointerCoords().apply { x = 600f; y = 100f }),
            0, 0, 1f, 1f, 0, 0, 0, 0)
        view.dispatchTouchEvent(pinch)
        pinch.recycle()
        assertTrue("c'était un pincement : rien n'est peint", s.pixels.isEmpty)
        assertFalse(s.canUndo)
    }

    @Test
    fun shapeDrawsItsOutlineOnTheGrid() {
        val s = open()
        view.tool = ZoomCanvasView.Tool.PIXEL_SHAPE
        view.shapeKind = ShapeKind.RECT
        view.shapeFill = ShapeFill.OUTLINE
        view.shapeSquare = false
        view.strokeSize = 1f
        drag(0 to 0, 3 to 2)
        var n = 0
        for (x in 0..3) for (y in 0..2) if (s.pixels.get(x, y) == red) n++
        assertEquals("le tour d'un 4 × 3", 10, n)
        assertEquals(0, s.pixels.get(1, 1))
        // Plein à la couleur secondaire + contour : « les deux ».
        view.shapeFill = ShapeFill.BOTH
        drag(10 to 0, 13 to 2)
        assertEquals(blue, s.pixels.get(11, 1))
        assertEquals(red, s.pixels.get(10, 0))
    }

    @Test
    fun bucketFillsAClosedZoneAndPipettePicksAColor() {
        val s = open()
        val wall = ArrayList<Pair<Int, Int>>()
        for (i in 0..4) { wall.add(i to 0); wall.add(i to 4); wall.add(0 to i); wall.add(4 to i) }
        s.paint(*wall.toTypedArray())
        view.tool = ZoomCanvasView.Tool.PIXEL_FILL
        view.color = blue
        touch(MotionEvent.ACTION_DOWN, cx(2), cy(2))
        touch(MotionEvent.ACTION_UP, cx(2), cy(2))
        assertEquals(blue, s.pixels.get(1, 1))
        assertEquals(blue, s.pixels.get(3, 3))
        assertEquals(0, s.pixels.get(6, 6))

        var picked = 0
        view.listener = object : ZoomCanvasView.Listener {
            override fun onDrawingChanged() {}
            override fun onViewMoved() {}
            override fun onNothingToMove() {}
            override fun onSelectionChanged() {}
            override fun onTextRequested(id: Long?, sx: Double, sy: Double) {}
            override fun onColorPicked(color: Int) { picked = color }
        }
        view.tool = ZoomCanvasView.Tool.PIXEL_PICK
        touch(MotionEvent.ACTION_DOWN, cx(0), cy(0))
        touch(MotionEvent.ACTION_UP, cx(0), cy(0))
        assertEquals(red, picked)
    }

    private fun render(): Bitmap {
        val bmp = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        return bmp
    }

    @Test
    fun cellsAreDrawnCrispAndTheOrderChoosesWhoIsOnTop() {
        val s = open()
        view.pixelMode = false
        s.paint(0 to 0, 1 to 0)
        var bmp = render()
        assertEquals(red, bmp.getPixel(400, 300))
        assertEquals(red, bmp.getPixel(407, 307))
        assertEquals("la case suivante", red, bmp.getPixel(408, 300))
        assertEquals("pas de bavure", view.paperColor, bmp.getPixel(416, 300))
        assertEquals(view.paperColor, bmp.getPixel(399, 300))

        // Un gros trait noir par-dessus : devant ou derrière selon le choix.
        s.beginStroke(-60.0, 4.0, 0xFF000000.toInt(), 24.0)
        s.extendStroke(60.0, 4.0)
        s.endStroke()
        s.pixelsAbove = false
        bmp = render()
        assertEquals("les pixels derrière : le trait passe devant", 0xFF000000.toInt(), bmp.getPixel(404, 304))
        s.pixelsAbove = true
        bmp = render()
        assertEquals("les pixels devant", red, bmp.getPixel(404, 304))
    }

    // ---- Sauvegarde ----

    @Test
    fun pixelsSurviveSaveAndReopen() {
        val p = store.create("pixels", ZoomScene.DEFAULT_RATIO, single = true)
        p.scene.paint(3 to 4, -90 to 700, color = blue)
        p.scene.pixelsAbove = true
        store.save(p.meta, p.scene.drainChanges())
        assertFalse(p.scene.hasUnsavedItems)

        val back = store.load(p.meta.id)!!
        assertEquals(blue, back.scene.pixels.get(3, 4))
        assertEquals(blue, back.scene.pixels.get(-90, 700))
        assertEquals(2, back.scene.pixels.tileCount)
        assertTrue(back.scene.pixelsAbove)

        // Vider la dernière case d'une tuile supprime sa ligne ; l'autre reste.
        back.scene.paint(-90 to 700, color = 0)
        store.save(back.meta, back.scene.drainChanges())
        val again = store.load(p.meta.id)!!
        assertEquals(1, again.scene.pixels.tileCount)
        assertEquals(0, again.scene.pixels.get(-90, 700))
        assertEquals(blue, again.scene.pixels.get(3, 4))
    }

    @Test
    fun aFailedWriteIsRetriedAndDuplicatesKeepThePixels() {
        val p = store.create("pixels", ZoomScene.DEFAULT_RATIO, single = true)
        p.scene.paint(1 to 1)
        val lost = p.scene.drainChanges()
        p.scene.requeueChanges(lost)
        assertTrue(p.scene.hasUnsavedItems)
        store.save(p.meta, p.scene.drainChanges())

        val copy = store.duplicate(p.meta.id, "copie")!!
        val c = store.load(copy)!!
        assertTrue(c.scene.single)
        assertEquals(red, c.scene.pixels.get(1, 1))
        // La galerie du « Canvas » compte les pixels dans le poids du projet.
        assertTrue(store.list(single = true).all { it.sizeBytes > 0 })
    }

    // ---- Migration ----

    @Test
    fun theVersion2DatabaseOpensInVersion3() {
        val app = RuntimeEnvironment.getApplication()
        val file = app.getDatabasePath("migration-test.db")
        file.parentFile?.mkdirs()
        // La base de la version 2, telle que Room l'écrivait : trois tables, la colonne `kind` ajoutée à la main.
        SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
            old.execSQL("CREATE TABLE IF NOT EXISTS `zc_project` (`pid` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `created` INTEGER NOT NULL, `modified` INTEGER NOT NULL, `ratio` REAL NOT NULL, `camDepth` INTEGER NOT NULL, `cx` REAL NOT NULL, `cy` REAL NOT NULL, `zoom` REAL NOT NULL, `nextId` INTEGER NOT NULL)")
            old.execSQL("ALTER TABLE zc_project ADD COLUMN kind INTEGER NOT NULL DEFAULT 0")
            old.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_zc_project_uuid` ON `zc_project` (`uuid`)")
            old.execSQL("CREATE TABLE IF NOT EXISTS `zc_layer` (`pid` INTEGER NOT NULL, `depth` INTEGER NOT NULL, `ax` REAL NOT NULL, `ay` REAL NOT NULL, `count` INTEGER NOT NULL, `erasers` INTEGER NOT NULL, `minX` REAL NOT NULL, `minY` REAL NOT NULL, `maxX` REAL NOT NULL, `maxY` REAL NOT NULL, PRIMARY KEY(`pid`, `depth`), FOREIGN KEY(`pid`) REFERENCES `zc_project`(`pid`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            old.execSQL("CREATE TABLE IF NOT EXISTS `zc_item` (`pid` INTEGER NOT NULL, `itemId` INTEGER NOT NULL, `depth` INTEGER NOT NULL, `type` INTEGER NOT NULL, `z` INTEGER NOT NULL, `data` BLOB NOT NULL, PRIMARY KEY(`pid`, `itemId`), FOREIGN KEY(`pid`) REFERENCES `zc_project`(`pid`) ON UPDATE NO ACTION ON DELETE CASCADE )")
            old.execSQL("CREATE INDEX IF NOT EXISTS `index_zc_item_pid_depth_itemId` ON `zc_item` (`pid`, `depth`, `itemId`)")
            old.execSQL("INSERT INTO zc_project (uuid, name, created, modified, ratio, camDepth, cx, cy, zoom, nextId, kind) VALUES ('u1', 'ancien', 1, 2, 10000.0, 0, 0.0, 0.0, 1.0, 1, 1)")
            old.execSQL("INSERT INTO zc_layer VALUES (1, 0, 0.0, 0.0, 0, 0, 0.0, 0.0, 0.0, 0.0)")
            old.version = 2
        }
        val migrated = Room.databaseBuilder(app, ZoomDatabase::class.java, "migration-test.db")
            .addMigrations(*ZoomDatabase.MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            val st = ZoomStore(dir, migrated)
            assertEquals(listOf("ancien"), st.list(single = true).map { it.name })
            val p = st.load("u1")!!
            assertTrue(p.scene.single)
            assertFalse(p.scene.pixelsAbove)
            p.scene.paint(2 to 2)
            st.save(p.meta, p.scene.drainChanges())
            assertEquals(red, st.load("u1")!!.scene.pixels.get(2, 2))
        } finally {
            migrated.close()
            app.deleteDatabase("migration-test.db")
        }
    }
}
