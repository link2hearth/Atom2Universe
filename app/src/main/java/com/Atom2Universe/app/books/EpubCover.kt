package com.Atom2Universe.app.books

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/**
 * Extraction robuste de la couverture d'un fichier EPUB.
 *
 * Gère les deux origines possibles d'un livre :
 *  - chemin fichier (`/storage/...`) : lecture en accès direct via [ZipFile] ;
 *  - URI `content://` (dossier ajouté via le sélecteur SAF) : lecture en flux
 *    via [ZipInputStream]. Sans ce second cas, les EPUB ajoutés par dossier SAF
 *    restaient sans couverture (le test `startsWith("/")` échouait).
 *
 * Et les deux conventions de déclaration de couverture :
 *  - EPUB2 : `<meta name="cover" content="ID">` pointant vers un item du manifest ;
 *  - EPUB3 : item du manifest portant `properties="cover-image"`.
 * Avec repli sur les item dont l'id ou le href contient « cover ».
 */
internal fun loadEpubCoverBitmap(context: Context, sourcePath: String, sampleSize: Int = 2): Bitmap? =
    if (sourcePath.startsWith("/")) {
        loadEpubCoverBitmap(File(sourcePath), sampleSize)
    } else {
        loadEpubCoverFromUri(context, Uri.parse(sourcePath), sampleSize)
    }

/** Variante accès direct (chemin fichier réel). */
internal fun loadEpubCoverBitmap(epubFile: File, sampleSize: Int = 2): Bitmap? = try {
    ZipFile(epubFile).use { zip ->
        val container = zip.getEntry("META-INF/container.xml")
        val containerText = container?.let { zip.getInputStream(it).readBytes().decodeToString() }
        val opfPath = containerText
            ?.let { Regex("""full-path="([^"]+\.opf)"""").find(it)?.groupValues?.get(1) }
            ?: zip.entries().asSequence().map { it.name }.firstOrNull { it.endsWith(".opf") }
        val opfEntry = opfPath?.let { zip.getEntry(it) }
        if (opfEntry == null) {
            null
        } else {
            val opfText = zip.getInputStream(opfEntry).readBytes().decodeToString()
            val coverHref = resolveEpubCoverHref(opfText)?.let(::decodeHref)
            if (coverHref == null) {
                null
            } else {
                val coverKey = coverKey(opfPath, coverHref)
                val imgEntry = zip.getEntry(normalizeZipPath(coverKey))
                    ?: zip.getEntry(coverKey)
                    ?: zip.entries().asSequence()
                        .find { it.name.endsWith(coverHref.substringAfterLast("/")) }
                if (imgEntry == null) {
                    null
                } else {
                    val bytes = zip.getInputStream(imgEntry).readBytes()
                    decode(bytes, sampleSize)
                }
            }
        }
    }
} catch (_: Exception) {
    null
}

/** Variante flux (URI `content://`). */
private fun loadEpubCoverFromUri(context: Context, uri: Uri, sampleSize: Int): Bitmap? = try {
    // Passe 1 : récupérer les petits fichiers XML (container + opf).
    val xmlFiles = HashMap<String, ByteArray>()
    openZipStream(context, uri)?.use { zin ->
        var entry = zin.nextEntry
        while (entry != null) {
            val name = entry.name
            if (!entry.isDirectory &&
                (name.equals("META-INF/container.xml", ignoreCase = true) || name.endsWith(".opf", ignoreCase = true))
            ) {
                xmlFiles[name] = zin.readBytes()
            }
            zin.closeEntry()
            entry = zin.nextEntry
        }
    }

    val containerText = xmlFiles.entries
        .firstOrNull { it.key.equals("META-INF/container.xml", ignoreCase = true) }
        ?.value?.decodeToString()
    val opfPath = containerText
        ?.let { Regex("""full-path="([^"]+\.opf)"""").find(it)?.groupValues?.get(1) }
        ?: xmlFiles.keys.firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    val opfText = (opfPath?.let { xmlFiles[it] } ?: xmlFiles.entries.firstOrNull { it.key.endsWith(".opf", true) }?.value)
        ?.decodeToString()
    val coverHref = opfText?.let { resolveEpubCoverHref(it) }?.let(::decodeHref)

    if (coverHref == null) {
        null
    } else {
        val coverKey = normalizeZipPath(coverKey(opfPath ?: "", coverHref))
        val fileName = coverHref.substringAfterLast("/")

        // Passe 2 : retrouver l'image et la décoder.
        var exact: ByteArray? = null
        var fuzzy: ByteArray? = null
        openZipStream(context, uri)?.use { zin ->
            var entry = zin.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val n = normalizeZipPath(entry.name)
                    if (n == coverKey) {
                        exact = zin.readBytes()
                    } else if (fuzzy == null && n.endsWith(fileName)) {
                        fuzzy = zin.readBytes()
                    }
                }
                zin.closeEntry()
                if (exact != null) break
                entry = zin.nextEntry
            }
        }
        (exact ?: fuzzy)?.let { decode(it, sampleSize) }
    }
} catch (_: Exception) {
    null
}

private fun openZipStream(context: Context, uri: Uri): ZipInputStream? =
    context.contentResolver.openInputStream(uri)?.let { ZipInputStream(it.buffered()) }

private fun decode(bytes: ByteArray, sampleSize: Int): Bitmap? =
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sampleSize })

private fun coverKey(opfPath: String, coverHref: String): String {
    val opfDir = opfPath.substringBeforeLast("/", "")
    return if (opfDir.isEmpty()) coverHref else "$opfDir/$coverHref"
}

/** Résout le href de l'image de couverture à partir du texte du fichier OPF. */
private fun resolveEpubCoverHref(opfText: String): String? {
    // 1. EPUB2 : <meta name="cover" content="ID"> → href de l'item correspondant.
    val coverId = Regex("""<meta[^>]+name=["']cover["'][^>]+content=["']([^"']+)["']""")
        .find(opfText)?.groupValues?.get(1)
        ?: Regex("""<meta[^>]+content=["']([^"']+)["'][^>]+name=["']cover["']""")
            .find(opfText)?.groupValues?.get(1)
    if (!coverId.isNullOrEmpty()) {
        epubItemTags(opfText).firstOrNull { tagAttr(it, "id") == coverId }
            ?.let { tagAttr(it, "href") }
            ?.let { return it }
    }

    // 2. EPUB3 : item du manifest avec properties="cover-image".
    epubItemTags(opfText).forEach { tag ->
        val props = tagAttr(tag, "properties") ?: ""
        if (props.split(Regex("\\s+")).any { it == "cover-image" }) {
            tagAttr(tag, "href")?.let { return it }
        }
    }

    // 3. item dont l'id vaut cover-image / cover et de type image.
    epubItemTags(opfText).forEach { tag ->
        val id = tagAttr(tag, "id")?.lowercase() ?: ""
        val mime = tagAttr(tag, "media-type") ?: ""
        if ((id == "cover-image" || id == "cover") && mime.startsWith("image/")) {
            tagAttr(tag, "href")?.let { return it }
        }
    }

    // 4. premier item image dont le href contient « cover ».
    epubItemTags(opfText).forEach { tag ->
        val mime = tagAttr(tag, "media-type") ?: ""
        val href = tagAttr(tag, "href") ?: ""
        if (mime.startsWith("image/") && href.lowercase().contains("cover")) return href
    }

    return null
}

private fun epubItemTags(opfText: String): Sequence<String> =
    Regex("""<item\b[^>]*>""", RegexOption.IGNORE_CASE).findAll(opfText).map { it.value }

private fun tagAttr(tag: String, name: String): String? =
    Regex("""\b$name\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        .find(tag)?.groupValues?.get(1)

/** Décode les séquences %xx d'un href sans transformer les « + » en espaces. */
private fun decodeHref(href: String): String =
    if (href.contains('%')) {
        runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
    } else {
        href
    }

private fun normalizeZipPath(path: String): String {
    val out = ArrayList<String>()
    for (part in path.split("/")) when (part) {
        ".." -> if (out.isNotEmpty()) out.removeAt(out.lastIndex)
        ".", "" -> {}
        else -> out.add(part)
    }
    return out.joinToString("/")
}
