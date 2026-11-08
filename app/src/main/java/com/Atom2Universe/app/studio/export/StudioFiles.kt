package com.Atom2Universe.app.studio.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.Atom2Universe.app.studio.data.StudioProject
import java.io.File
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écrit les exports du Studio dans le dossier Musique de la tablette (MediaStore), ou dans celui de l'application avant Android 10. */
object StudioFiles {

    const val FOLDER = "Atom2Universe Studio"

    class Saved(val uri: Uri, val name: String)

    private fun baseName(project: StudioProject): String =
        "Studio_${project.styleId}_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())

    fun saveWav(context: Context, project: StudioProject, sampleRate: Int = 48_000, onProgress: (Float) -> Boolean): Saved? =
        save(context, baseName(project) + ".wav", "audio/wav") { out ->
            StudioExporter.renderWav(project, sampleRate, out, onProgress)
        }

    fun saveMidi(context: Context, project: StudioProject): Saved? =
        save(context, baseName(project) + ".mid", "audio/midi") { out ->
            out.write(StudioExporter.midiBytes(project))
            true
        }

    /** [write] rend `false` si l'utilisateur annule : le fichier à moitié écrit est alors supprimé. */
    private fun save(context: Context, name: String, mime: String, write: (OutputStream) -> Boolean): Saved? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveMediaStore(context, name, mime, write)
            else saveFile(context, name, write)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveMediaStore(context: Context, name: String, mime: String, write: (OutputStream) -> Boolean): Saved? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        val ok = try {
            resolver.openOutputStream(uri)?.use { out -> java.io.BufferedOutputStream(out, 1 shl 16).let { b -> write(b).also { b.flush() } } } ?: false
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            throw e
        }
        if (!ok) {
            resolver.delete(uri, null, null)
            return null
        }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        return Saved(uri, name)
    }

    private fun saveFile(context: Context, name: String, write: (OutputStream) -> Boolean): Saved? {
        val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir, FOLDER).apply { mkdirs() }
        val file = File(dir, name)
        val ok = file.outputStream().buffered(1 shl 16).use { write(it) }
        if (!ok) {
            file.delete()
            return null
        }
        return Saved(Uri.fromFile(file), name)
    }
}
