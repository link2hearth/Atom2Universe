package com.Atom2Universe.app.games.caves.node

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import java.io.File

/**
 * Le pack de textures du joueur : un dossier de PNG carrés, nommés comme l'export (voir
 * [TexturePackNames]). Le dossier où ils sont rangés n'a aucune importance : seul le nom du
 * fichier compte, ce qui pardonne un zip refait à la main. Un fichier absent ou invalide retombe
 * sur la texture dessinée par le code, donc un pack peut ne changer que quelques blocs.
 */
internal object TexturePack {
    private const val TAG = "CaveTexturePack"
    private const val MAX_SIZE = 2048
    private const val MANIFEST_ICON = "pack.png"

    @Volatile private var files: Map<String, File> = emptyMap()

    /** Où le pack actif est décompressé ; `null` tant qu'aucun n'est installé. */
    fun activeDirectory(context: Context): File? = directory(context).takeIf { it.isDirectory }

    fun directory(context: Context) = File(context.filesDir, "caves/texturepack")

    /** Choisit le pack (`null` : textures du code) ; à appeler avant de construire l'atlas. */
    fun use(directory: File?) {
        files = directory?.let(::scan) ?: emptyMap()
    }

    /** Pour l'export : le modèle doit montrer les textures du code, pas celles d'un pack déjà actif. */
    fun <T> withoutPack(block: () -> T): T {
        val previous = files
        files = emptyMap()
        try { return block() } finally { files = previous }
    }

    private fun scan(root: File): Map<String, File> {
        val found = HashMap<String, File>()
        for (file in root.walkTopDown().sortedBy { it.path }) {
            if (!file.isFile || !file.extension.equals("png", true)) continue
            if (file.parentFile == root && file.name.equals(MANIFEST_ICON, true)) continue
            val name = file.nameWithoutExtension.lowercase()
            if (found.putIfAbsent(name, file) != null) Log.w(TAG, "Duplicate texture name '$name': ${file.path} ignored")
        }
        return found
    }

    /** La texture du pack pour [name], mise à la taille [size] de l'atlas, ou le résultat de [fallback]. */
    fun load(key: String, name: String, size: Int, fallback: () -> Bitmap): Bitmap {
        require(size > 0)
        val file = files[name] ?: return fallback()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true; inScaled = false }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..MAX_SIZE || bounds.outHeight != bounds.outWidth) {
            Log.w(TAG, "Not a square PNG up to ${MAX_SIZE}px: ${file.path} (texture $key); using the default")
            return fallback()
        }
        val bitmap = BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inScaled = false })
        if (bitmap == null) {
            Log.w(TAG, "Cannot decode ${file.path} (texture $key); using the default")
            return fallback()
        }
        if (bitmap.width == size) return bitmap
        // Sans lissage : une silhouette en 32 px reste nette dans l'atlas en 96 px.
        return Bitmap.createScaledBitmap(bitmap, size, size, false).also { bitmap.recycle() }
    }
}
