package com.Atom2Universe.app.music

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.LruCache
import android.widget.ImageView
import com.Atom2Universe.app.R
import com.Atom2Universe.app.music.model.Album
import com.Atom2Universe.app.music.model.MusicTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jaudiotagger.audio.AudioFileIO
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit

/** One read-only artwork resolver for album tiles, artist fallbacks and both players. */
internal object MusicArtworkLoader {
    private val httpClient = OkHttpClient.Builder().callTimeout(15, TimeUnit.SECONDS).build()
    private val readers = Semaphore(3)
    private val locks = Array(32) { Mutex() }
    private val generation = AtomicLong()
    private val memory = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 16 / 1024).toInt()
    ) {
        override fun sizeOf(key: String, value: Bitmap) = maxOf(1, value.byteCount / 1024)
    }
    @Volatile private var diskDirectory: File? = null

    private data class Source(val uris: List<Uri>, val tracks: List<MusicTrack>, val albumName: String) {
        // Track identity keeps unrelated albums (including duplicate titles) out of each other's cache.
        val identity: String get() = digest(buildString {
            append(albumName).append('\n')
            tracks.map { it.filePath ?: it.uri.toString() }.sorted().forEach { append(it).append('\n') }
            uris.map(Uri::toString).sorted().forEach { append(it).append('\n') }
        })
    }

    private class Binding { var job: Job? = null }
    private data class FolderPicture(val uri: Uri, val name: String, val stamp: String)

    fun init(context: Context) {
        if (diskDirectory == null) synchronized(this) {
            if (diskDirectory == null) diskDirectory = File(context.cacheDir, "music_artwork_v2").apply { mkdirs() }
        }
    }

    fun loadAlbum(view: ImageView, album: Album, placeholder: Int, scope: CoroutineScope) =
        bind(view, placeholder, scope, 256) { listOf(sourceForAlbum(album)) }

    fun loadTrack(view: ImageView, track: MusicTrack, placeholder: Int, scope: CoroutineScope, size: Int) =
        bind(view, placeholder, scope, size) {
            val album = MusicLibrary.getArtworkAlbum(track)
            listOf(album?.let(::sourceForAlbum) ?: Source(listOfNotNull(track.albumArtUri), listOf(track), track.album))
        }

    /** Shared resolver for RemoteViews, which cannot use an ImageView binding. */
    suspend fun loadTrackBitmap(context: Context, track: MusicTrack, size: Int): Bitmap? = readSafely {
        withContext(Dispatchers.IO) {
            init(context.applicationContext)
            val album = MusicLibrary.getArtworkAlbum(track)
            val source = album?.let(::sourceForAlbum)
                ?: Source(listOfNotNull(track.albumArtUri), listOf(track), track.album)
            readers.withPermit { load(context.applicationContext, source, size) }
        }
    }

    fun loadArtist(view: ImageView, iconPath: String?, albums: List<Album>, placeholder: Int, scope: CoroutineScope) =
        bind(view, placeholder, scope, 256, artist = true) {
            buildList {
                iconPath?.let { add(Source(listOf(Uri.fromFile(File(it))), emptyList(), "")) }
                albums.forEach { add(sourceForAlbum(it)) }
            }
        }

    fun cancel(view: ImageView) {
        (view.getTag(R.id.music_artwork_binding) as? Binding)?.job?.cancel()
        view.setTag(R.id.music_artwork_binding, null)
    }

    private fun bind(
        view: ImageView,
        placeholder: Int,
        scope: CoroutineScope,
        size: Int,
        artist: Boolean = false,
        sources: () -> List<Source>
    ) {
        cancel(view)
        val binding = Binding()
        view.setTag(R.id.music_artwork_binding, binding)
        view.setImageResource(placeholder)
        if (artist) view.scaleType = ImageView.ScaleType.CENTER
        val context = view.context.applicationContext
        binding.job = scope.launch(Dispatchers.Main.immediate) {
            val bitmap = readSafely {
                withContext(Dispatchers.IO) {
                    init(context)
                    var result: Bitmap? = null
                    for (source in sources()) {
                        currentCoroutineContext().ensureActive()
                        result = readers.withPermit { load(context, source, size) }
                        if (result != null) break
                    }
                    result
                }
            }
            if (view.getTag(R.id.music_artwork_binding) === binding && bitmap != null) {
                view.setImageBitmap(bitmap)
                if (artist) view.scaleType = ImageView.ScaleType.CENTER_CROP
            }
        }
    }

    private fun sourceForAlbum(album: Album): Source {
        val canonical = album.tracks.firstOrNull()?.let(MusicLibrary::getArtworkAlbum) ?: album
        val tracks = canonical.tracks.toList()
        val uris = tracks.mapNotNull { it.albumArtUri }.ifEmpty { listOfNotNull(canonical.albumArtUri) }
        return Source(uris.distinct(), tracks, canonical.name)
    }

    private suspend fun load(context: Context, source: Source, size: Int): Bitmap? {
        val epoch = generation.get()
        val folders = source.tracks.mapNotNull { localFile(it)?.parentFile }
            .flatMap { listOfNotNull(it, discParent(it)) }.distinctBy { it.path }
        val pictures = folders.flatMap { folderPictures(context, it) }.distinctBy { it.uri }
        // Changes made by a tag editor or a newly copied folder cover must invalidate thumbnails.
        val signature = buildString {
            source.tracks.forEach { track -> localFile(track)?.let { append(fileStamp(it)) } }
            source.uris.filter { it.scheme == "file" }.forEach { it.path?.let { path -> append(fileStamp(File(path))) } }
            pictures.sortedBy { it.uri.toString() }.forEach { append(it.stamp).append('\n') }
        }
        val key = "${source.identity}_${size}_${digest(signature)}"
        return locks[(key.hashCode() and Int.MAX_VALUE) % locks.size].withLock {
            memory.get(key)?.let { return@withLock it }
            currentCoroutineContext().ensureActive()
            val cacheFile = File(diskDirectory, "$key.png")
            val cached = if (cacheFile.isFile) readSafely { BitmapFactory.decodeFile(cacheFile.path) } else null
            val bitmap = cached ?: resolve(context, source, pictures, size)
            currentCoroutineContext().ensureActive()
            if (bitmap != null && epoch == generation.get()) {
                memory.put(key, bitmap)
                if (cached == null) readSafely {
                    cacheFile.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                }
            }
            bitmap
        }
    }

    private suspend fun resolve(context: Context, source: Source, pictures: List<FolderPicture>, size: Int): Bitmap? {
        for (uri in source.uris) {
            currentCoroutineContext().ensureActive()
            decodeUri(context, uri, size)?.let { return it }
        }
        // Android's album-art URI is only a hint. It may be absent/stale even with an APIC/FLAC cover.
        // Try every track: the first song of an album doesn't necessarily contain the picture.
        for (track in source.tracks) {
            currentCoroutineContext().ensureActive()
            embeddedArtwork(context, track, size)?.let { return it }
        }
        val preferred = listOf("cover", "folder", "front", "album", "albumart", "albumartsmall", source.albumName.lowercase(Locale.ROOT))
        val ordered = pictures.sortedWith(compareBy<FolderPicture> {
            preferred.indexOf(it.name.substringBeforeLast('.').lowercase(Locale.ROOT)).takeIf { rank -> rank >= 0 } ?: preferred.size
        }.thenBy { it.name.lowercase(Locale.ROOT) })
        for (picture in ordered) {
            currentCoroutineContext().ensureActive()
            decodeUri(context, picture.uri, size)?.let { return it }
        }
        return null
    }

    private fun embeddedArtwork(context: Context, track: MusicTrack, size: Int): Bitmap? {
        // Do not open a Navidrome stream just to look for embedded artwork.
        if (track.uri.scheme in listOf("http", "https")) return null
        val file = localFile(track)
        // jaudiotagger understands artwork frames that the platform retriever sometimes ignores.
        readSafely {
            file?.takeIf { it.isFile && it.canRead() }?.let {
                AudioFileIO.read(it).tag?.artworkList
                    ?.sortedBy { art -> if (art.pictureType == 3) 0 else 1 }
                    ?.forEach { art ->
                        if (!art.isLinked) art.binaryData?.let { data -> decodeBytes(data, size)?.let { return it } }
                    }
            }
        }
        for (uri in listOfNotNull(file?.let(Uri::fromFile), track.uri).distinct()) {
            val bitmap = readSafely {
                val retriever = MediaMetadataRetriever()
                try {
                    retriever.setDataSource(context, uri)
                    retriever.embeddedPicture?.let { decodeBytes(it, size) }
                } finally {
                    retriever.release()
                }
            }
            if (bitmap != null) return bitmap
        }
        return null
    }

    private fun folderPictures(context: Context, folder: File): List<FolderPicture> {
        val files = readSafely { folder.listFiles()?.filter {
            it.isFile && it.canRead() && it.extension.lowercase(Locale.ROOT) in IMAGE_EXTENSIONS
        } }.orEmpty()
        if (files.isNotEmpty()) return files.map { FolderPicture(Uri.fromFile(it), it.name, fileStamp(it)) }

        // With scoped storage the audio may be readable but sibling images may only be accessible
        // through a music folder already granted by the user. Do not request wider permissions.
        val pictures = mutableListOf<FolderPicture>()
        for (grant in context.contentResolver.persistedUriPermissions) {
            if (!grant.isReadPermission || grant.uri.authority != "com.android.externalstorage.documents") continue
            readSafely {
                val treeId = DocumentsContract.getTreeDocumentId(grant.uri)
                val volume = treeId.substringBefore(':')
                val storage = if (volume.equals("primary", true)) Environment.getExternalStorageDirectory().path else "/storage/$volume"
                val grantedPath = File(storage, treeId.substringAfter(':', "")).path.trimEnd('/')
                if (folder.path != grantedPath && !folder.path.startsWith("$grantedPath/")) return@readSafely
                val relative = folder.path.removePrefix(grantedPath).trimStart('/')
                val folderId = if (relative.isEmpty()) treeId
                    else treeId.trimEnd('/') + (if (treeId.endsWith(':')) "" else "/") + relative
                val children = DocumentsContract.buildChildDocumentsUriUsingTree(grant.uri, folderId)
                val columns = arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE
                )
                context.contentResolver.query(children, columns, null, null, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(1) ?: continue
                        if (name.substringAfterLast('.').lowercase(Locale.ROOT) !in IMAGE_EXTENSIONS) continue
                        val uri = DocumentsContract.buildDocumentUriUsingTree(grant.uri, cursor.getString(0))
                        pictures.add(FolderPicture(uri, name, "$uri:${cursor.getLong(2)}:${cursor.getLong(3)}"))
                    }
                }
            }
        }
        return pictures
    }

    private fun decodeUri(context: Context, uri: Uri, size: Int): Bitmap? = readSafely {
        if (uri.scheme == "http" || uri.scheme == "https") {
            httpClient.newCall(Request.Builder().url(uri.toString()).build()).execute().use { response ->
                if (response.isSuccessful) response.body?.byteStream()?.use { stream ->
                    // Bound the download before allocating a byte array, even without Content-Length.
                    val bytes = stream.takeBytes(MAX_ART_BYTES + 1)
                    if (bytes.size <= MAX_ART_BYTES) decodeBytes(bytes, size) else null
                } else null
            }
        } else decodeStream(size) { context.contentResolver.openInputStream(uri) }
    }

    private fun decodeStream(size: Int, open: () -> InputStream?): Bitmap? = readSafely {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open()?.use { BitmapFactory.decodeStream(it, null, options) }
        if (!prepareDecode(options, size)) return@readSafely null
        open()?.use { BitmapFactory.decodeStream(it, null, options) }?.let { fitToSize(it, size) }
    }

    private fun decodeBytes(data: ByteArray, size: Int): Bitmap? = readSafely {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, options)
        if (!prepareDecode(options, size)) return@readSafely null
        BitmapFactory.decodeByteArray(data, 0, data.size, options)?.let { fitToSize(it, size) }
    }

    private fun prepareDecode(options: BitmapFactory.Options, size: Int): Boolean {
        if (options.outWidth <= 0 || options.outHeight <= 0) return false
        options.inSampleSize = 1
        // Bound the long edge too: extremely wide pictures must not exhaust the image cache.
        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > size * 2) options.inSampleSize *= 2
        options.inJustDecodeBounds = false
        return true
    }

    private fun fitToSize(bitmap: Bitmap, size: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= size) return bitmap
        val resized = Bitmap.createScaledBitmap(
            bitmap, maxOf(1, bitmap.width * size / longest), maxOf(1, bitmap.height * size / longest), true
        )
        if (resized !== bitmap) bitmap.recycle()
        return resized
    }

    private fun InputStream.takeBytes(limit: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var remaining = limit
        while (remaining > 0) {
            val count = read(buffer, 0, minOf(buffer.size, remaining))
            if (count < 0) break
            output.write(buffer, 0, count)
            remaining -= count
        }
        return output.toByteArray()
    }

    fun invalidateAlbum(album: Album) {
        generation.incrementAndGet()
        val prefix = sourceForAlbum(album).identity + "_"
        memory.snapshot().keys.filter { it.startsWith(prefix) }.forEach(memory::remove)
        diskDirectory?.listFiles()?.filter { it.name.startsWith(prefix) }?.forEach { it.delete() }
    }

    fun clearMemory() { memory.evictAll() }

    fun clearAll() {
        generation.incrementAndGet()
        memory.evictAll()
        diskDirectory?.listFiles()?.forEach { it.delete() }
    }

    private fun localFile(track: MusicTrack): File? = track.filePath?.takeIf { it.isNotBlank() }?.let(::File)
        ?: track.uri.takeIf { it.scheme == "file" }?.path?.let(::File)

    // Covers above CD1/CD2 are valid; never search an unrelated artist or Music directory.
    private fun discParent(folder: File): File? = folder.parentFile.takeIf { DISC_FOLDER.matches(folder.name) }
    private fun fileStamp(file: File) = "${file.path}:${file.length()}:${file.lastModified()}\n"
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    private inline fun <T> readSafely(read: () -> T): T? = try { read() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "avif")
    private const val MAX_ART_BYTES = 32 * 1024 * 1024
    private val DISC_FOLDER = Regex("(?i)(cd|disc|disk|disque)[ _-]*[0-9]+")
}
