package com.Atom2Universe.app.games.caves.node

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONObject
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Écrit le pack modèle : chaque texture que le code dessine, en PNG sous son vrai nom, rangée par
 * onglet du catalogue, avec un `pack.json` et un LISEZMOI. L'utilisateur n'a plus qu'à retoucher
 * les images, supprimer celles qu'il ne change pas, et rezipper.
 */
internal object TexturePackExporter {
    const val FORMAT = 1
    private const val ATLAS_TILE = 96
    private val tag = Regex("^[a-z0-9_]+$")

    /** Le nombre de textures écrites. À appeler hors du fil d'interface, et jamais pendant une partie. */
    fun write(context: Context, out: OutputStream, readme: String, packName: String): Int {
        BlockRegistry.load(context.assets)
        // Le renderer construit l'atlas à 96 px sans « vivid » ; l'export part des mêmes pixels.
        val bitmaps = TexturePack.withoutPack { BlockRegistry.buildTextureAtlas(context.assets, ATLAS_TILE, vivid = false) }
        val entries = BlockRegistry.textureEntries()
        check(bitmaps.size == entries.size) { "Atlas and texture names disagree" }
        try {
            ZipOutputStream(out).use { zip ->
                zip.text("pack.json", JSONObject().put("name", packName).put("author", "").put("version", 1)
                    .put("format", FORMAT).toString(2))
                zip.text("README.txt", readme)
                for ((i, entry) in entries.withIndex()) {
                    check(tag.matches(entry.folder)) { "Bad folder ${entry.folder}" }
                    zip.putNextEntry(ZipEntry("assets/caves/textures/${entry.folder}/${entry.name}.png"))
                    writePng(bitmaps[i], zip)
                    zip.closeEntry()
                }
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
        return entries.size
    }

    private fun ZipOutputStream.text(path: String, content: String) {
        putNextEntry(ZipEntry(path))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    private fun writePng(bitmap: Bitmap, out: OutputStream) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val (native, size) = if (bitmap.width == bitmap.height) TexturePackNames.shrinkToNative(pixels, bitmap.width)
            else pixels to bitmap.width
        val height = native.size / size
        val image = Bitmap.createBitmap(size, height, Bitmap.Config.ARGB_8888)
        image.setPixels(native, 0, size, 0, 0, size, height)
        image.compress(Bitmap.CompressFormat.PNG, 100, out)
        image.recycle()
    }
}
