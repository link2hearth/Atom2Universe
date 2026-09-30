package com.Atom2Universe.app.pixelart.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import com.Atom2Universe.app.pixelart.core.Document
import java.io.ByteArrayOutputStream
import java.io.File

/** Ce qui touche aux fichiers de l'appareil : lecture d'images, écriture par-dessus, galerie, partage. */
object DeviceImages {

    class Decoded(val pixels: IntArray, val width: Int, val height: Int, val format: ImageFormat?)

    /** Ouvre l'image et rend les pixels en ARGB non prémultiplié, réduits à [Document.MAX_SIDE] si besoin. */
    fun decode(context: Context, uri: Uri): Decoded? {
        return try {
            val mime = context.contentResolver.getType(uri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            // Réduction par le décodeur (puissances de 2) : rapide et sobre en mémoire. Un Bitmap non
            // prémultiplié ne se laisse pas redimensionner par createScaledBitmap (échec silencieux
            // sur les grandes images), donc aucune mise à l'échelle n'est demandée au Bitmap.
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > Document.MAX_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inScaled = false
                inSampleSize = sample
                inPremultiplied = false
            }
            val bmp = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                ?: return null
            var w = bmp.width
            var h = bmp.height
            var px = IntArray(w * h)
            bmp.getPixels(px, 0, w, 0, 0, w, h)
            bmp.recycle()
            val longest = maxOf(w, h)
            if (longest > Document.MAX_SIDE) {
                // Le décodeur a arrondi : dernier ajustement au plus proche voisin, sur les pixels.
                val k = Document.MAX_SIDE.toDouble() / longest
                val nw = maxOf(1, (w * k).toInt())
                val nh = maxOf(1, (h * k).toInt())
                val out = IntArray(nw * nh)
                for (y in 0 until nh) {
                    val sy = minOf(h - 1, (y / k).toInt())
                    for (x in 0 until nw) out[y * nw + x] = px[sy * w + minOf(w - 1, (x / k).toInt())]
                }
                px = out; w = nw; h = nh
            }
            Decoded(px, w, h, ImageFormat.fromMime(mime) ?: displayName(context, uri)?.let { ImageFormat.fromName(it) })
        } catch (e: Throwable) {
            null
        }
    }

    fun displayName(context: Context, uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    } ?: uri.lastPathSegment?.substringAfterLast('/')

    /**
     * Garde le droit de relire et d'écraser ce fichier après la fermeture de l'application.
     * Retourne vrai si l'écriture est permise.
     */
    fun keepAccess(context: Context, uri: Uri): Boolean {
        val rw = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        return try {
            context.contentResolver.takePersistableUriPermission(uri, rw)
            true
        } catch (e: Exception) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
            }
            // Le fournisseur a peut-être accordé l'écriture pour la seule session en cours.
            context.checkCallingOrSelfUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    /** Écrase le fichier par [bytes], d'un seul bloc (les octets sont déjà prêts : pas de fichier à moitié écrit). */
    fun overwrite(context: Context, uri: Uri, bytes: ByteArray): Boolean {
        for (mode in arrayOf("wt", "rwt", "w")) {
            try {
                context.contentResolver.openOutputStream(uri, mode)?.use {
                    it.write(bytes)
                    it.flush()
                } ?: continue
                return true
            } catch (e: Exception) {
                // Essai suivant : certains fournisseurs ne connaissent pas tous les modes.
            }
        }
        return false
    }

    /** Range l'image dans la galerie (Pictures/Atom2Universe). Retourne son adresse. */
    fun saveToGallery(context: Context, bytes: ByteArray, displayName: String, format: ImageFormat): Uri? {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, format.mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Atom2Universe")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            null
        }
    }

    /** Intent de partage d'une image : les octets passent par un fichier temporaire du cache. */
    fun shareIntent(context: Context, bytes: ByteArray, fileName: String, format: ImageFormat): Intent {
        val dir = File(context.cacheDir, "pixelart_share").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        return Intent(Intent.ACTION_SEND).apply {
            type = format.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // ---- Formats sans alpha ni GIF : passent par Bitmap --------------------------------------

    /** Encode en JPEG ou WebP (fonds transparents aplatis en blanc pour le JPEG). */
    fun compressWithBitmap(pixels: IntArray, w: Int, h: Int, format: ImageFormat): ByteArray {
        val src = if (format == ImageFormat.JPEG) {
            IntArray(pixels.size) { com.Atom2Universe.app.pixelart.core.PixelColor.over(0xFFFFFFFF.toInt(), pixels[it]) }
        } else pixels
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        bmp.setPixels(src, 0, w, 0, 0, w, h)
        val bos = ByteArrayOutputStream()
        when (format) {
            ImageFormat.JPEG -> bmp.compress(Bitmap.CompressFormat.JPEG, 95, bos)
            ImageFormat.WEBP ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) bmp.compress(Bitmap.CompressFormat.WEBP_LOSSLESS, 100, bos)
                else @Suppress("DEPRECATION") bmp.compress(Bitmap.CompressFormat.WEBP, 100, bos)
            else -> bmp.compress(Bitmap.CompressFormat.PNG, 100, bos)
        }
        bmp.recycle()
        return bos.toByteArray()
    }

    /** Octets d'un export du dessin dans le [format] et à l'échelle demandés (image courante pour PNG/JPEG/WebP). */
    fun encode(doc: Document, format: ImageFormat, scale: Int, frameIndex: Int, gif: ImageExport.GifOptions? = null): ByteArray =
        when (format) {
            ImageFormat.PNG -> ImageExport.encodePng(doc, frameIndex, scale)
            ImageFormat.GIF -> ImageExport.encodeGifBytes(doc, gif ?: ImageExport.GifOptions(scale = scale))
            ImageFormat.JPEG, ImageFormat.WEBP -> {
                val s = scale.coerceIn(1, ImageExport.maxScaleFor(doc.width, doc.height))
                val flat = ImageExport.flatten(doc, frameIndex)
                val px = if (s > 1) ImageExport.scaleNearest(flat, doc.width, doc.height, s) else flat
                compressWithBitmap(px, doc.width * s, doc.height * s, format)
            }
        }
}

/** Sélecteur d'image qui demande aussi le droit de la réécrire plus tard. */
class OpenImageForEdit : ActivityResultContracts.OpenDocument() {
    override fun createIntent(context: Context, input: Array<String>): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}

/** « Enregistrer sous » : le fichier créé reste réécrivable, sans redemander. */
class CreateImageDocument(private val mime: String) : ActivityResultContracts.CreateDocument(mime) {
    override fun createIntent(context: Context, input: String): Intent =
        super.createIntent(context, input).addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
        )
}
