package com.Atom2Universe.app.crypto

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.io.InputStream

internal fun saveClickerImageToTree(context: Context, tree: Uri, input: InputStream, name: String): Uri? {
    val resolver = context.contentResolver
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
    val uri = DocumentsContract.createDocument(resolver, parent, "image/webp", name) ?: return null
    val copied = try {
        (resolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: 0L) > 0
    } catch (_: Exception) { false }
    if (!copied) runCatching { DocumentsContract.deleteDocument(resolver, uri) }
    return uri.takeIf { copied }
}

/** Écrit l'image dans Pictures/Atom2Universe ; false (et rien de laissé derrière) si vide ou en échec. */
internal fun saveToAtom2Universe(context: Context, input: InputStream, filename: String, mimeType: String): Boolean =
    copyToAtom2Universe(context, input, filename, mimeType) != null

internal fun copyToAtom2Universe(context: Context, input: InputStream, filename: String, mimeType: String): Uri? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/Atom2Universe")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = resolver.insert(
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            values
        ) ?: return null
        val written = try {
            resolver.openOutputStream(uri)?.use { input.copyTo(it) } ?: 0L
        } catch (_: Exception) { 0L }
        if (written <= 0L) {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        values.clear()
        values.put(MediaStore.MediaColumns.IS_PENDING, 0)
        try {
            if (resolver.update(uri, values, null, null) != 1) throw IOException("Cannot publish image")
            uri
        } catch (_: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            null
        }
    } else {
        val picturesDir = Environment.getExternalStoragePublicDirectory(
            Environment.DIRECTORY_PICTURES
        )
        val dir = File(picturesDir, "Atom2Universe").also { it.mkdirs() }
        val file = File(dir, filename)
        val written = try {
            file.outputStream().use { input.copyTo(it) }
        } catch (_: Exception) { 0L }
        if (written <= 0L) {
            file.delete()
            return null
        }
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        Uri.fromFile(file)
    }
}
