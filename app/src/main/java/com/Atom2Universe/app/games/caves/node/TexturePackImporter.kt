package com.Atom2Universe.app.games.caves.node

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Installe le zip d'un joueur comme pack actif. Le zip n'est jamais fait confiance : seuls les PNG
 * et `pack.json` sont lus, les tailles sont plafonnées, et chaque fichier est écrit sous un nom
 * nettoyé directement dans le dossier du pack (le chemin dans le zip n'est jamais réutilisé,
 * donc aucun `../` ne peut en sortir). Le pack précédent n'est remplacé qu'une fois le nouveau complet.
 */
internal object TexturePackImporter {
    private const val TAG = "CaveTexturePack"
    private const val MAX_ENTRIES = 6000
    private const val MAX_FILE = 8L shl 20
    private const val MAX_TOTAL = 256L shl 20
    private const val MAX_TEXTURE_SIZE = 2048
    private const val MAX_NAME = 60

    class Result(val name: String, val textures: Int)

    /** Le nom du pack installé, ou `null` s'il n'y en a pas. */
    fun installedName(context: Context, fallback: String): String? {
        val dir = TexturePack.directory(context).takeIf { it.isDirectory } ?: return null
        return runCatching { JSONObject(File(dir, "pack.json").readText()).optString("name") }
            .getOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: fallback
    }

    fun remove(context: Context) {
        TexturePack.directory(context).deleteRecursively()
    }

    /** `null` si le zip ne contient aucune texture utilisable ; le pack déjà installé reste alors en place. */
    fun install(context: Context, input: InputStream, fallbackName: String): Result? {
        val target = TexturePack.directory(context)
        val staging = File(target.parentFile, target.name + ".new")
        staging.deleteRecursively()
        check(staging.mkdirs()) { "Cannot create $staging" }
        try {
            var name: String? = null
            var textures = 0
            var entries = 0
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    if (++entries > MAX_ENTRIES) throw IOException("Too many files in the zip")
                    val path = entry.name.replace('\\', '/')
                    val base = path.substringAfterLast('/')
                    val topLevel = path.count { it == '/' } <= 1 // pack.json may sit in a wrapper folder
                    when {
                        topLevel && base.equals("pack.json", true) ->
                            name = readName(readLimited(zip, 64 * 1024))
                        topLevel && base.equals("pack.png", true) -> Unit
                        base.endsWith(".png", true) -> {
                            val file = File(staging, TexturePackNames.sanitize(base.dropLast(4)) + ".png")
                            if (file.exists()) continue
                            total += copyLimited(zip, file)
                            if (total > MAX_TOTAL) throw IOException("Zip too large")
                            if (isUsable(file)) textures++ else file.delete()
                        }
                    }
                }
            }
            if (textures == 0) return null
            val packName = name ?: fallbackName
            File(staging, "pack.json").writeText(JSONObject().put("name", packName).toString())
            target.deleteRecursively()
            check(staging.renameTo(target)) { "Cannot install the pack" }
            return Result(packName, textures)
        } finally {
            staging.deleteRecursively()
        }
    }

    private fun readName(bytes: ByteArray): String? = runCatching {
        JSONObject(String(bytes, Charsets.UTF_8)).optString("name").trim().take(MAX_NAME).takeIf { it.isNotEmpty() }
    }.getOrNull()

    private fun readLimited(input: InputStream, max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (out.size() <= max) {
            val n = input.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
        }
        return out.toByteArray().copyOf(minOf(out.size(), max))
    }

    private fun copyLimited(input: InputStream, file: File): Long {
        var written = 0L
        file.outputStream().use { out ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                written += n
                if (written > MAX_FILE) throw IOException("File too large: ${file.name}")
                out.write(buffer, 0, n)
            }
        }
        return written
    }

    /** Un PNG lisible, carré, et de taille raisonnable : les mêmes règles que le chargeur. */
    private fun isUsable(file: File): Boolean {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        val ok = bounds.outWidth in 1..MAX_TEXTURE_SIZE && bounds.outHeight == bounds.outWidth
        if (!ok) Log.w(TAG, "Skipped ${file.name}: not a square PNG up to ${MAX_TEXTURE_SIZE}px")
        return ok
    }
}
