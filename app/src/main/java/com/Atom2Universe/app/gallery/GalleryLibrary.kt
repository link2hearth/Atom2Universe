package com.Atom2Universe.app.gallery

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document as Doc
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.security.DigestInputStream

internal sealed interface GalleryTile
internal data object AddFolder : GalleryTile
internal data class Folder(val uri: Uri, val name: String) : GalleryTile
internal data class Photo(
    val id: String, val uri: Uri, val name: String, val modified: Long,
    val parent: Uri? = null, val movable: Boolean = false,
    val favorite: Boolean = false, val bin: Uri? = null
) : GalleryTile

internal enum class Shelf { HOME, PICK, FOLDER, RECENT, FAVORITES, TRASH }

internal enum class PhotoFileFormat(val extension: String, val mimeType: String) {
    WEBP("webp", "image/webp"),
    PNG("png", "image/png");

    @Suppress("DEPRECATION")
    fun encoder(): Bitmap.CompressFormat = when (this) {
        PNG -> Bitmap.CompressFormat.PNG
        WEBP -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY
                else Bitmap.CompressFormat.WEBP
    }
}

internal data class PhotoExportOptions(val format: PhotoFileFormat = PhotoFileFormat.WEBP, val quality: Int = 80)

/** Only user-selected folders and marked photos are indexed. Folder contents stay with the provider. */
internal class GalleryLibrary(private val app: Context) :
    SQLiteOpenHelper(app, "gallery.db", null, 1) {
    companion object {
        @Volatile private var instance: GalleryLibrary? = null
        fun get(context: Context): GalleryLibrary = instance ?: synchronized(this) {
            instance ?: GalleryLibrary(context.applicationContext).also { instance = it }
        }
    }
    private val resolver get() = app.contentResolver
    private val copies get() = File(app.filesDir, "gallery-recent")
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE folders (uri TEXT PRIMARY KEY, name TEXT NOT NULL)")
        db.execSQL("CREATE TABLE bins (parent TEXT PRIMARY KEY, uri TEXT NOT NULL)")
        db.execSQL("""CREATE TABLE photos (id TEXT PRIMARY KEY, uri TEXT NOT NULL, name TEXT NOT NULL,
            modified INTEGER NOT NULL, parent TEXT, movable INTEGER DEFAULT 0, favorite INTEGER DEFAULT 0,
            recent INTEGER DEFAULT 0, trashed INTEGER DEFAULT 0, bin TEXT)""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun Cursor.str(column: String): String = getString(getColumnIndexOrThrow(column))
    private fun Cursor.optionalUri(column: String): Uri? =
        getString(getColumnIndexOrThrow(column))?.let(Uri::parse)
    private fun Cursor.number(column: String): Long = getLong(getColumnIndexOrThrow(column))
    private fun Cursor.photo() = Photo(str("id"), Uri.parse(str("uri")), str("name"), number("modified"),
        optionalUri("parent"), number("movable") != 0L, number("favorite") != 0L, optionalUri("bin"))

    private fun <T> rows(sql: String, vararg args: String, read: Cursor.() -> T): List<T> =
        readableDatabase.rawQuery(sql, args).use { cursor ->
            buildList { while (cursor.moveToNext()) add(cursor.read()) }
        }

    fun folders(): List<GalleryTile> =
        rows("SELECT * FROM folders ORDER BY rowid") { Folder(Uri.parse(str("uri")), str("name")) } + AddFolder

    fun rootFolder(tree: Uri): Folder {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        return Folder(root, displayName(root))
    }

    fun pickerRoots(): List<Folder> = resolver.persistedUriPermissions
        .filter { it.isReadPermission && DocumentsContract.isTreeUri(it.uri) }
        .mapNotNull { runCatching { rootFolder(it.uri) }.getOrNull() }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Folder::name))

    fun addFolder(folder: Folder) {
        val values = ContentValues().apply { put("uri", folder.uri.toString()); put("name", folder.name) }
        writableDatabase.insertWithOnConflict("folders", null, values, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun unlink(folder: Folder) {
        writableDatabase.delete("folders", "uri=?", arrayOf(folder.uri.toString()))
        // Favorites and trashed documents may still rely on the tree grant.
    }

    private fun displayName(uri: Uri): String {
        val fallback = uri.lastPathSegment?.substringAfterLast('/') ?: app.getString(com.Atom2Universe.app.R.string.gallery_image)
        if (uri.scheme != "content") return fallback
        return resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: fallback
    }

    private fun identity(uri: Uri): String =
        if ((DocumentsContract.isTreeUri(uri) && "document" in uri.pathSegments) || DocumentsContract.isDocumentUri(app, uri))
            DocumentsContract.buildDocumentUri(uri.authority, DocumentsContract.getDocumentId(uri)).toString()
        else uri.toString()

    fun contents(folder: Folder, directoriesOnly: Boolean = false, coverOnly: Boolean = false): List<GalleryTile> {
        val favorites = rows("SELECT id FROM photos WHERE favorite=1") { str("id") }.toHashSet()
        val hidden = rows("SELECT id FROM photos WHERE trashed!=0") { str("id") }.toHashSet()
        val bins = rows("SELECT uri FROM bins") { identity(Uri.parse(str("uri"))) }.toHashSet()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, DocumentsContract.getDocumentId(folder.uri))
        val projection = arrayOf(Doc.COLUMN_DOCUMENT_ID, Doc.COLUMN_DISPLAY_NAME, Doc.COLUMN_MIME_TYPE,
            Doc.COLUMN_LAST_MODIFIED, Doc.COLUMN_FLAGS)
        val writable = app.checkUriPermission(folder.uri, android.os.Process.myPid(), android.os.Process.myUid(),
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val directories = mutableListOf<Folder>()
        val photos = mutableListOf<Photo>()
        resolver.query(children, projection, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val child = DocumentsContract.buildDocumentUriUsingTree(folder.uri, cursor.getString(0))
                val id = DocumentsContract.buildDocumentUri(child.authority, cursor.getString(0)).toString()
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2).orEmpty()
                if (mime == Doc.MIME_TYPE_DIR) {
                    if (!coverOnly && id !in bins) directories.add(Folder(child, name))
                } else if (!directoriesOnly && id !in hidden && (mime.startsWith("image/") ||
                        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.').lowercase())?.startsWith("image/") == true)) {
                    val movable = writable && cursor.getInt(4) and Doc.FLAG_SUPPORTS_MOVE != 0
                    photos.add(Photo(id, child, name, cursor.getLong(3), folder.uri, movable, id in favorites))
                    if (coverOnly) break
                }
            }
        } ?: throw IOException()
        return directories.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, Folder::name)) +
            photos.sortedWith(compareByDescending<Photo> { it.modified }.thenBy(String.CASE_INSENSITIVE_ORDER, Photo::name))
    }

    fun collection(shelf: Shelf): List<Photo> {
        val where = when (shelf) {
            Shelf.FAVORITES -> "favorite=1 AND trashed=0 ORDER BY name COLLATE NOCASE"
            Shelf.RECENT -> "recent>0 AND trashed=0 ORDER BY recent DESC, rowid DESC"
            Shelf.TRASH -> "trashed>0 ORDER BY trashed DESC, rowid DESC"
            else -> error("Expected a photo collection")
        }
        return rows("SELECT * FROM photos WHERE $where") { photo() }
    }

    private fun record(photo: Photo, edit: ContentValues.() -> Unit) {
        val oldUri = rows("SELECT uri FROM photos WHERE id=?", photo.id) { Uri.parse(str("uri")) }.firstOrNull()
        val values = ContentValues().apply {
            put("uri", photo.uri.toString()); put("name", photo.name); put("modified", photo.modified)
            put("parent", photo.parent?.toString()); put("movable", photo.movable)
            edit()
        }
        if (writableDatabase.update("photos", values, "id=?", arrayOf(photo.id)) == 0) {
            if (!values.containsKey("id")) values.put("id", photo.id)
            writableDatabase.insertOrThrow("photos", null, values)
        }
        if (oldUri != null && oldUri != photo.uri) deleteOwnedCopy(oldUri)
    }

    @Synchronized fun setFavorite(photo: Photo, favorite: Boolean) = record(photo) { put("favorite", favorite) }
    @Synchronized fun opened(photo: Photo) = record(photo) { put("recent", System.currentTimeMillis()) }

    fun canCreateIn(parent: Uri): Boolean = app.checkUriPermission(parent, android.os.Process.myPid(),
        android.os.Process.myUid(), Intent.FLAG_GRANT_WRITE_URI_PERMISSION) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
        documentFlags(parent) and Doc.FLAG_DIR_SUPPORTS_CREATE != 0

    @Synchronized fun saveEdit(parent: Uri, title: String, image: Bitmap, options: PhotoExportOptions): Photo {
        val format = options.format
        val existing = destinationNames(Folder(parent, ""))
        var name = "$title.${format.extension}"
        var n = 2
        while (name in existing) name = "$title (${n++}).${format.extension}"
        val uri = requireNotNull(DocumentsContract.createDocument(resolver, parent, format.mimeType, name))
        try {
            requireNotNull(resolver.openOutputStream(uri, "w")).use {
                check(image.compress(format.encoder(), options.quality, it))
            }
            val photo = Photo(identity(uri), uri, displayName(uri), System.currentTimeMillis(), parent,
                documentFlags(uri) and Doc.FLAG_SUPPORTS_MOVE != 0)
            record(photo) { put("recent",System.currentTimeMillis()); put("favorite",false); put("trashed",0); putNull("bin") }
            return photo
        } catch (e: Exception) {
            DocumentsContract.deleteDocument(resolver, uri)
            throw e
        }
    }

    /** VIEW often supplies a one-activity read grant. Keep a private original only in that case. */
    @Synchronized fun openExternal(source: Uri, flags: Int): String {
        require(source.scheme == "content" || source.scheme == "file")
        if (source.scheme == "file") {
            val file = File(requireNotNull(source.path)).canonicalFile
            require(file.path.startsWith("/storage/") || file.path.startsWith("/sdcard/"))
            require("/Android/data/" !in file.path && "/Android/obb/" !in file.path)
        } else {
            require(app.packageManager.resolveContentProvider(requireNotNull(source.authority), 0)?.applicationInfo?.uid != app.applicationInfo.uid)
        }
        val sourceId = identity(source)
        val entry = rows("SELECT * FROM photos WHERE id=?", sourceId) { photo() }.firstOrNull()
        val id = if (entry?.bin != null) "external:$sourceId" else sourceId
        val previous = if (id == sourceId) entry else rows("SELECT * FROM photos WHERE id=?", id) { photo() }.firstOrNull()
        val name = runCatching { displayName(source) }.getOrNull() ?: source.lastPathSegment?.substringAfterLast('/')
            ?: app.getString(com.Atom2Universe.app.R.string.gallery_image)
        val persisted = source.scheme == "content" && flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0 &&
            runCatching { resolver.takePersistableUriPermission(source, Intent.FLAG_GRANT_READ_URI_PERMISSION) }.isSuccess
        val retained = previous?.takeIf { it.parent != null && it.bin == null }?.uri
        val needsCopy = !persisted && retained == null
        val copy = if (needsCopy) {
            copies.mkdirs()
            File.createTempFile("image-", ".original", copies)
        } else null
        try {
            if (copy != null) {
                requireNotNull(resolver.openInputStream(source)).use { input ->
                    copy.outputStream().use { output -> input.copyTo(output) }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(copy.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
            }
            val photo = Photo(id, retained ?: copy?.let(Uri::fromFile) ?: source, name, System.currentTimeMillis(),
                previous?.parent, previous?.movable == true)
            record(photo) { put("recent", System.currentTimeMillis()); put("trashed", 0); putNull("bin") }
        } catch (e: Exception) {
            copy?.delete()
            throw e
        }
        return id
    }

    private fun deleteOwnedCopy(uri: Uri) {
        if (uri.scheme == "file") uri.path?.let(::File)?.takeIf { it.parentFile == copies }?.delete()
    }

    private fun documentFlags(uri: Uri): Int =
        resolver.query(uri, arrayOf(Doc.COLUMN_FLAGS), null, null)?.use {
            if (it.moveToFirst()) it.getInt(0) else 0
        } ?: 0

    fun canRelocate(photo: Photo): Boolean = photo.parent != null || isOwnedCopy(photo.uri)

    private fun isOwnedCopy(uri: Uri) = uri.scheme == "file" && uri.path?.let(::File)?.parentFile == copies

    /** Reserve names across one batch, including files that are invisible in the photo grid. */
    fun destinationNames(folder: Folder): MutableSet<String> {
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(folder.uri, DocumentsContract.getDocumentId(folder.uri))
        return resolver.query(children, arrayOf(Doc.COLUMN_DISPLAY_NAME), null, null)?.use { cursor ->
            sortedSetOf(String.CASE_INSENSITIVE_ORDER).apply {
                while (cursor.moveToNext()) cursor.getString(0)?.let(::add)
            }
        } ?: throw IOException()
    }

    @Synchronized fun transfer(photo: Photo, folder: Folder, move: Boolean, names: MutableSet<String>) {
        val parent = photo.parent
        val local = isOwnedCopy(photo.uri)
        if (move) {
            require(parent != null || local)
            if (parent != null && identity(parent) == identity(folder.uri)) return
        }
        val nativeMove = move && photo.movable && photo.uri.authority == folder.uri.authority && photo.name !in names
        if (nativeMove) {
            val moved = requireNotNull(DocumentsContract.moveDocument(resolver, photo.uri, requireNotNull(parent), folder.uri))
            // Some providers return the new ID wrapped in the source tree's grant.
            val uri = DocumentsContract.buildDocumentUriUsingTree(folder.uri, DocumentsContract.getDocumentId(moved))
            try {
                updateLocation(photo, uri, folder)
            } catch (e: Exception) {
                DocumentsContract.moveDocument(resolver, uri, folder.uri, parent)
                throw e
            }
            names.add(photo.name)
            return
        }
        if (move && !local) require(documentFlags(photo.uri) and Doc.FLAG_SUPPORTS_DELETE != 0)
        var name = photo.name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        var suffix = 2
        while (name in names) name = "${photo.name.take(dot)} (${suffix++})${photo.name.drop(dot)}"
        val mime = resolver.getType(photo.uri)?.takeIf { it.startsWith("image/") }
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(photo.name.substringAfterLast('.').lowercase())
            ?: "application/octet-stream"
        val uri = requireNotNull(DocumentsContract.createDocument(resolver, folder.uri, mime, name))
        var deletingSource = false
        try {
            val digest = if (move) MessageDigest.getInstance("SHA-256") else null
            requireNotNull(resolver.openInputStream(photo.uri)).use { input ->
                requireNotNull(resolver.openOutputStream(uri, "w")).use { output ->
                    (if (digest == null) input else DigestInputStream(input, digest)).copyTo(output)
                }
            }
            if (move) {
                // Across providers, verify the closed destination before removing the source.
                val expected = requireNotNull(digest).digest()
                requireNotNull(resolver.openInputStream(uri)).use { input ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    val stream = DigestInputStream(input, digest)
                    while (stream.read(buffer) != -1) { /* digest the complete destination */ }
                }
                check(expected.contentEquals(digest.digest()))
                writableDatabase.beginTransaction()
                try {
                    updateLocation(photo, uri, folder)
                    deletingSource = true
                    if (local) check(File(requireNotNull(photo.uri.path)).delete())
                    else check(DocumentsContract.deleteDocument(resolver, photo.uri))
                    writableDatabase.setTransactionSuccessful()
                } finally { writableDatabase.endTransaction() }
            }
            names.add(name)
        } catch (e: Exception) {
            // A remote provider may finish deletion and then lose the reply. Keep the verified copy then.
            if (!deletingSource) DocumentsContract.deleteDocument(resolver, uri)
            else names.add(name)
            throw e
        }
    }

    private fun updateLocation(photo: Photo, uri: Uri, folder: Folder) {
        val values = ContentValues().apply {
            put("id", identity(uri)); put("uri", uri.toString()); put("parent", folder.uri.toString())
            put("name", displayName(uri)); put("movable", documentFlags(uri) and Doc.FLAG_SUPPORTS_MOVE != 0)
        }
        writableDatabase.update("photos", values, "id=? AND trashed=0", arrayOf(photo.id))
    }

    /** A dedicated provider directory makes moves reversible without rewriting the image bytes. */
    @Synchronized fun trash(photo: Photo) {
        var target: Uri? = null
        var moved = photo.uri
        val parent = photo.parent
        if (parent != null && photo.movable) {
            val existing = rows("SELECT uri FROM bins WHERE parent=?", parent.toString()) { Uri.parse(str("uri")) }.firstOrNull()
            target = existing?.takeIf { uri ->
                resolver.query(uri, arrayOf(Doc.COLUMN_DOCUMENT_ID), null, null)?.use { it.moveToFirst() } == true
            } ?: requireNotNull(DocumentsContract.createDocument(resolver, parent, Doc.MIME_TYPE_DIR, ".Atom2Universe-Trash")).also {
                writableDatabase.replaceOrThrow("bins", null, ContentValues().apply {
                    put("parent", parent.toString()); put("uri", it.toString())
                })
            }
            moved = requireNotNull(DocumentsContract.moveDocument(resolver, photo.uri, parent, target))
        }
        try {
            record(photo.copy(uri = moved)) {
                put("trashed", System.currentTimeMillis())
                put("bin", target?.toString())
                if (target != null) put("id", identity(moved))
            }
        } catch (e: Exception) {
            if (target != null && parent != null) DocumentsContract.moveDocument(resolver, moved, target, parent)
            throw e
        }
    }

    private fun requireTrashed(photo: Photo) {
        check(rows("SELECT id FROM photos WHERE id=? AND uri=? AND trashed>0",
            photo.id, photo.uri.toString()) { str("id") }.isNotEmpty())
    }

    @Synchronized fun restore(photo: Photo) {
        requireTrashed(photo)
        val restored = if (photo.bin != null) requireNotNull(DocumentsContract.moveDocument(
            resolver, photo.uri, photo.bin, requireNotNull(photo.parent))) else photo.uri
        try {
            val values = ContentValues().apply {
                put("trashed", 0); putNull("bin"); put("uri", restored.toString())
                if (photo.bin != null) { put("id", identity(restored)); put("name", displayName(restored)) }
            }
            check(writableDatabase.update("photos", values, "id=?", arrayOf(photo.id)) == 1)
        } catch (e: Exception) {
            if (photo.bin != null && photo.parent != null) DocumentsContract.moveDocument(resolver, restored, photo.parent, photo.bin)
            throw e
        }
    }

    @Synchronized fun discard(photo: Photo) {
        requireTrashed(photo)
        if (photo.bin != null) check(DocumentsContract.deleteDocument(resolver, photo.uri))
        deleteOwnedCopy(photo.uri)
        if (photo.bin == null && photo.parent != null) {
            record(photo) { put("trashed", -1); put("favorite", false); put("recent", 0) }
        } else writableDatabase.delete("photos", "id=?", arrayOf(photo.id))
    }
}
