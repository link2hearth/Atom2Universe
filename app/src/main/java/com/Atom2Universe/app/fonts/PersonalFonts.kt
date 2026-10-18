package com.Atom2Universe.app.fonts

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** User-selected fonts copied into private storage; no access to the original folder is needed later. */
object PersonalFonts {
    private const val PREFIX = "personal:"
    private const val MAX_BYTES = 64L * 1024 * 1024
    private val changes = AtomicLong()
    val revision: Long get() = changes.get()

    data class Entry(val id: String, val label: String)
    data class ImportResult(val added: Int, val existing: Int, val skipped: Int)
    internal enum class ImportStatus { ADDED, EXISTING, SKIPPED }

    // Keep personal files out of automatic Android cloud backups as well as the APK.
    private fun directory(context: Context) = File(context.noBackupFilesDir, "personal_fonts")
    fun isPersonal(id: String) = id.startsWith(PREFIX)
    private fun supported(name: String) = name.substringAfterLast('.', "").lowercase(Locale.ROOT) in setOf("ttf", "otf")

    fun list(context: Context): List<Entry> {
        val entries = directory(context).listFiles().orEmpty().filter { it.isFile && supported(it.name) }
            .map { file ->
                val name = file.name.substringAfter('-').substringBeforeLast('.')
                    .replace('-', ' ').replace('_', ' ').trim()
                Entry(PREFIX + file.name, name)
            }
        val counts = entries.groupingBy { it.label }.eachCount()
        return entries.map { entry ->
            if ((counts[entry.label] ?: 0) > 1) entry.copy(label = "${entry.label} (${entry.id.removePrefix(PREFIX).take(6)})")
            else entry
        }.sortedBy { it.label.lowercase(Locale.ROOT) }
    }

    fun loadTypeface(context: Context, id: String): Typeface? {
        if (!isPersonal(id)) return null
        val name = id.removePrefix(PREFIX)
        // IDs can be restored from a book or drawing; never treat them as arbitrary file paths.
        if (name != File(name).name || '/' in name || '\\' in name || !supported(name)) return null
        val file = File(directory(context), name)
        if (!file.isFile) return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val font = android.graphics.fonts.Font.Builder(file).build()
                val family = android.graphics.fonts.FontFamily.Builder(font).build()
                Typeface.CustomFallbackBuilder(family).setSystemFallback("sans-serif").build()
            } else Typeface.Builder(file).build()
        }.getOrNull()
    }

    suspend fun importFolder(context: Context, uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val root = requireNotNull(DocumentFile.fromTreeUri(context, uri))
        require(root.isDirectory && root.canRead())
        val pending = ArrayDeque<DocumentFile>().apply { add(root) }
        val visited = HashSet<Uri>()
        val results = ArrayList<ImportStatus>()
        while (pending.isNotEmpty()) {
            currentCoroutineContext().ensureActive()
            val folder = pending.removeFirst()
            if (!visited.add(folder.uri)) continue
            for (document in folder.listFiles()) {
                currentCoroutineContext().ensureActive()
                if (document.isDirectory) pending.add(document)
                else if (document.name?.let(::supported) == true) {
                    results.add(importStream(context, document.name!!) {
                        requireNotNull(context.contentResolver.openInputStream(document.uri))
                    })
                }
            }
        }
        results.summary()
    }

    suspend fun importFiles(context: Context, uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
        uris.map { uri ->
            currentCoroutineContext().ensureActive()
            val name = DocumentFile.fromSingleUri(context, uri)?.name.orEmpty()
            importStream(context, name) { requireNotNull(context.contentResolver.openInputStream(uri)) }
        }.summary()
    }

    private fun List<ImportStatus>.summary() = ImportResult(
        count { it == ImportStatus.ADDED }, count { it == ImportStatus.EXISTING }, count { it == ImportStatus.SKIPPED }
    )

    /** Validate a complete temporary copy before publishing it, so failures preserve the existing library. */
    internal suspend fun importStream(context: Context, name: String, open: () -> InputStream): ImportStatus {
        if (!supported(name)) return ImportStatus.SKIPPED
        val folder = directory(context)
        check(folder.isDirectory || folder.mkdirs())
        val temporary = File.createTempFile("font-", ".part", folder)
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            var total = 0L
            open().use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= MAX_BYTES)
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            }
            require(total > 0)
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            // The hash makes repeat imports harmless, even if the original font was renamed.
            if (folder.listFiles().orEmpty().any { it.name.startsWith("$hash-") }) return ImportStatus.EXISTING
            requireNotNull(Typeface.Builder(temporary).build()) // Reject corrupt or unsupported files.
            val extension = name.substringAfterLast('.').lowercase(Locale.ROOT)
            val safeName = name.substringAfterLast('/').substringAfterLast('\\').substringBeforeLast('.')
                .replace(Regex("[^\\p{L}\\p{N} ._()-]"), "_").trim().take(100).ifBlank { "font" }
            val destination = File(folder, "$hash-$safeName.$extension")
            check(temporary.renameTo(destination))
            changes.incrementAndGet()
            return ImportStatus.ADDED
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return ImportStatus.SKIPPED
        } finally {
            temporary.delete()
        }
    }

    suspend fun clear(context: Context): Unit = withContext(Dispatchers.IO) {
        val folder = directory(context)
        for (file in folder.listFiles().orEmpty()) {
            currentCoroutineContext().ensureActive()
            check(file.delete())
        }
        changes.incrementAndGet()
    }
}
