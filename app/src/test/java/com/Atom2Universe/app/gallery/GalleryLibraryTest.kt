package com.Atom2Universe.app.gallery

import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.graphics.Bitmap
import android.net.Uri
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document as Doc
import android.provider.DocumentsProvider
import org.junit.Assert.*
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.android.controller.ContentProviderController
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlinx.coroutines.runBlocking

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class GalleryLibraryTest {
    private val library by lazy { GalleryLibrary(RuntimeEnvironment.getApplication()) }
    @After fun closeDatabase() { library.close() }

    @Test @Config(sdk = [26, 34]) @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun mosaicThumbnailsKeepPortraitAndLandscapeProportions() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        for ((width, height) in listOf(240 to 720, 800 to 200)) {
            val file = File(context.cacheDir, "tile-$width.png")
            file.outputStream().use { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
            val photo = Photo(file.name, Uri.fromFile(file), file.name, 0)
            val thumb = requireNotNull(GalleryImages.thumbnail(context, photo, 160))
            assertEquals(width * thumb.height, height * thumb.width)
        }
    }

    @Test fun recentImageSurvivesTemporaryAccessAndIsDeduplicatedOnReopen() {
        val context = RuntimeEnvironment.getApplication()
        val source = File(context.cacheDir, "shared.png")
        source.outputStream().use { Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, it) }
        val authority = "test.gallery.shared"
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun getType(uri: Uri) = "image/png"
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?) =
                MatrixCursor(arrayOf(Doc.COLUMN_DISPLAY_NAME)).apply { addRow(arrayOf(source.name)) }
            override fun openFile(uri: Uri, mode: String) = ParcelFileDescriptor.open(source, ParcelFileDescriptor.MODE_READ_ONLY)
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
            override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?) = 0
        }
        ContentProviderController.of(provider).create(ProviderInfo().apply {
            this.authority = authority
            applicationInfo = android.content.pm.ApplicationInfo().apply { uid = context.applicationInfo.uid + 1 }
        })
        val shared = Uri.parse("content://$authority/photo")
        val id = library.openExternal(shared, 0)
        val first = library.collection(Shelf.RECENT).single()
        library.setFavorite(first, true)
        assertEquals(id, library.openExternal(shared, 0))
        val recent = library.collection(Shelf.RECENT).single()
        assertTrue(recent.favorite)
        assertFalse(File(requireNotNull(first.uri.path)).exists())
        assertTrue(source.renameTo(File(context.cacheDir, "no-longer-shared.png")))
        val copy = File(requireNotNull(recent.uri.path))
        assertEquals(source.name, recent.name)
        assertArrayEquals(File(context.cacheDir, "no-longer-shared.png").readBytes(), copy.readBytes())
        library.trash(recent)
        library.restore(library.collection(Shelf.TRASH).single())
        assertEquals(recent, library.collection(Shelf.RECENT).single())
    }

    /** Like external storage, this provider changes document IDs when a file moves. */
    private fun installProvider(base: File, authority: String) {
        val provider = object : DocumentsProvider() {
            override fun onCreate() = true
            override fun queryRoots(projection: Array<out String>?) = MatrixCursor(emptyArray())
            override fun isChildDocument(parentDocumentId: String, documentId: String) = documentId.startsWith("$parentDocumentId/")
            fun cursor(files: List<File>, projection: Array<out String>?) =
                MatrixCursor(projection ?: arrayOf(Doc.COLUMN_DOCUMENT_ID, Doc.COLUMN_MIME_TYPE, Doc.COLUMN_DISPLAY_NAME)).apply {
                    for (file in files) addRow(columnNames.map {
                        when (it) {
                            Doc.COLUMN_DOCUMENT_ID -> file.relativeTo(base).invariantSeparatorsPath
                            Doc.COLUMN_DISPLAY_NAME -> file.name
                            Doc.COLUMN_MIME_TYPE -> if (file.isDirectory) Doc.MIME_TYPE_DIR else "image/jpeg"
                            Doc.COLUMN_FLAGS -> Doc.FLAG_SUPPORTS_MOVE or Doc.FLAG_SUPPORTS_DELETE
                            Doc.COLUMN_LAST_MODIFIED -> file.lastModified()
                            else -> null
                        }
                    })
                }
            override fun queryDocument(documentId: String, projection: Array<out String>?) =
                cursor(listOf(File(base, documentId)).filter(File::exists), projection)
            override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?) =
                cursor(File(base, parentDocumentId).listFiles().orEmpty().toList(), projection)
            override fun createDocument(parentDocumentId: String, mimeType: String, displayName: String): String {
                val id = "$parentDocumentId/$displayName"
                val file = File(base, id)
                check(if (mimeType == Doc.MIME_TYPE_DIR) file.mkdir() else file.createNewFile())
                return id
            }
            override fun deleteDocument(documentId: String) { check(File(base, documentId).delete()) }
            override fun moveDocument(sourceDocumentId: String, sourceParentDocumentId: String, targetParentDocumentId: String): String {
                val from = File(base, sourceDocumentId)
                val id = "$targetParentDocumentId/${from.name}"
                check(from.renameTo(File(base, id)))
                return id
            }
            override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor =
                ParcelFileDescriptor.open(File(base, documentId), ParcelFileDescriptor.parseMode(mode))
        }
        ContentProviderController.of(provider).create(ProviderInfo().apply {
            this.authority = authority
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        })
    }

    @Test fun trashAndRestoreKeepImageBytesAndFavoriteAcrossDocumentIdChanges() {
        val base = RuntimeEnvironment.getApplication().cacheDir
        val album = File(base, "album").apply { mkdirs() }
        val original = File(album, "photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val authority = "test.gallery.documents"
        installProvider(base, authority)
        val folder = library.rootFolder(DocumentsContract.buildTreeDocumentUri(authority, "album"))
        library.addFolder(folder)
        assertEquals(listOf(folder, AddFolder), library.folders())
        val photo = library.contents(folder).filterIsInstance<Photo>().single().copy(movable = true)
        library.setFavorite(photo, true)
        library.opened(photo)
        library.trash(photo)
        assertFalse(original.exists())
        assertTrue(library.contents(folder).isEmpty())
        assertTrue(library.collection(Shelf.FAVORITES).isEmpty())
        val trashed = library.collection(Shelf.TRASH).single()
        assertTrue(trashed.favorite)
        library.restore(trashed)
        assertArrayEquals(byteArrayOf(1, 2, 3), original.readBytes())
        assertTrue(library.collection(Shelf.TRASH).isEmpty())
        assertEquals(photo.id, library.collection(Shelf.RECENT).single().id)
        assertEquals(photo.id, library.collection(Shelf.FAVORITES).single().id)
        assertTrue(library.contents(folder).filterIsInstance<Photo>().single().favorite)
    }

    @Test fun copyAndMoveAcrossAlbumsPreserveBytesNamesAndFavorite() {
        val base = RuntimeEnvironment.getApplication().cacheDir
        val source = File(base, "source").apply { mkdirs() }
        val target = File(base, "target").apply { mkdirs() }
        val original = File(source, "portrait.jpg").apply { writeText("portrait") }
        File(target, original.name).writeText("existing")
        val authority = "test.gallery.transfer"
        installProvider(base, authority)
        val from = library.rootFolder(DocumentsContract.buildTreeDocumentUri(authority, source.name))
        val to = library.rootFolder(DocumentsContract.buildTreeDocumentUri(authority, target.name))
        val photo = library.contents(from).filterIsInstance<Photo>().single().copy(movable = true)
        library.setFavorite(photo, true)
        val names = library.destinationNames(to)
        library.transfer(photo, to, move = false, names)
        assertEquals("portrait", original.readText())
        assertEquals("portrait", File(target, "portrait (2).jpg").readText())
        library.transfer(photo, to, move = true, names)
        assertFalse(original.exists())
        assertEquals("existing", File(target, "portrait.jpg").readText())
        assertEquals("portrait", File(target, "portrait (3).jpg").readText())
        val moved = library.collection(Shelf.FAVORITES).single()
        assertEquals("portrait (3).jpg", moved.name)
        assertEquals(to.uri, moved.parent)
        library.transfer(moved.copy(movable = true), from, move = true, library.destinationNames(from))
        val returned = library.collection(Shelf.FAVORITES).single()
        assertEquals(from.uri, returned.parent)
        assertEquals("portrait", File(source, returned.name).readText())
        assertTrue(library.contents(from).filterIsInstance<Photo>().single().favorite)
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun editedPngCopiesKeepAlphaAndNeverOverwriteAnExistingFile() {
        val base = RuntimeEnvironment.getApplication().cacheDir
        val album = File(base,"edits").apply { mkdirs() }
        val authority = "test.gallery.editor"
        installProvider(base,authority)
        val folder = library.rootFolder(DocumentsContract.buildTreeDocumentUri(authority,album.name))
        val previous = File(album,"portrait.png").apply { writeText("original") }
        val src = Bitmap.createBitmap(8,4,Bitmap.Config.ARGB_8888).apply { setPixel(2,2,android.graphics.Color.RED) }
        val options = PhotoExportOptions(format = PhotoFileFormat.PNG)
        val first = library.saveEdit(folder.uri,"portrait",src,options)
        val second = library.saveEdit(folder.uri,"portrait",src,options)
        assertEquals("original",previous.readText())
        assertNotEquals(first.uri,second.uri)
        val png = android.graphics.BitmapFactory.decodeFile(File(album,first.name).path)
        assertEquals(8,png.width)
        assertEquals(0,android.graphics.Color.alpha(png.getPixel(0,0)))
        assertEquals(android.graphics.Color.RED,png.getPixel(2,2))
        assertTrue(library.collection(Shelf.RECENT).any { it.id == second.id })
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) fun webpCopyDefaultsTo80AndUsesTheSelectedQuality() {
        val base = RuntimeEnvironment.getApplication().cacheDir
        val album = File(base, "webp-edits").apply { mkdirs() }
        val authority = "test.gallery.webp"
        installProvider(base, authority)
        val folder = library.rootFolder(DocumentsContract.buildTreeDocumentUri(authority, album.name))
        val image = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val random = kotlin.random.Random(42)
        val pixels = IntArray(96 * 96) { random.nextInt(0x1000000) or android.graphics.Color.BLACK }
        pixels[0] = android.graphics.Color.TRANSPARENT
        image.setPixels(pixels, 0, 96, 0, 0, 96, 96)
        val defaults = PhotoExportOptions()
        assertEquals(80, defaults.quality)
        val normal = library.saveEdit(folder.uri, "photo", image, defaults)
        val low = library.saveEdit(folder.uri, "photo", image, defaults.copy(quality = 5))
        val file = File(album, normal.name)
        assertEquals("photo.webp", normal.name)
        assertEquals("photo (2).webp", low.name)
        assertTrue(File(album, low.name).length() < file.length())
        val info = android.graphics.BitmapFactory.Options()
        val decoded = android.graphics.BitmapFactory.decodeFile(file.path, info)
        assertEquals("image/webp", info.outMimeType)
        assertEquals(96, decoded.width)
        assertEquals(96, decoded.height)
        assertEquals(0, android.graphics.Color.alpha(decoded.getPixel(0, 0)))
    }

    @Test fun sortShuffleAndDragKeepTheirOrderOnReopen() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences("gallery-sort-test", 0)
        val sort = GalleryOrdering(prefs, "album", Shelf.FOLDER)
        val photos = listOf("b", "c", "a").mapIndexed { i, name -> Photo(name, Uri.EMPTY, name, i.toLong()) }
        sort.choose(GalleryOrder.AZ)
        assertEquals(listOf(photos[2], photos[0], photos[1]), sort.arrange(photos))
        sort.choose(GalleryOrder.ZA)
        assertEquals(listOf(photos[1], photos[0], photos[2]), sort.arrange(photos))
        sort.choose(GalleryOrder.NEWEST)
        assertEquals(photos.reversed(), sort.arrange(photos))
        sort.choose(GalleryOrder.OLDEST)
        assertEquals(photos, sort.arrange(photos))
        sort.choose(GalleryOrder.SHUFFLE)
        assertEquals(sort.arrange(photos), GalleryOrdering(prefs, "album", Shelf.FOLDER).arrange(photos.reversed()))
        sort.saveManual(listOf(photos[1], photos[2], photos[0]))
        assertEquals(listOf(photos[1], photos[2], photos[0]), GalleryOrdering(prefs, "album", Shelf.FOLDER).arrange(photos))
    }
}
