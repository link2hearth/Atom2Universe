package com.Atom2Universe.app.games.caves.world

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Où vivent les cartes du mode Assaut : livrées avec l'appli (assets/caves/maps) ou exportées
 * par le joueur depuis un monde créatif (Documents/cave_world/maps, comme les structures).
 */
internal object A2MapStorage {

    private const val ASSET_DIR = "caves/maps"
    private const val ASSET_PREFIX = "asset:"
    private const val BUILTIN_PREFIX = "builtin:"

    /**
     * [path] : chemin de fichier, « asset:… » pour une carte livrée avec l'appli, ou
     * « builtin:… » pour une carte fabriquée par le code ([BuiltinMaps]).
     */
    data class Entry(val name: String, val path: String)

    fun userMapsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .resolve("cave_world/maps")

    fun list(context: Context): List<Entry> {
        val ext = ".${A2Map.EXTENSION}"
        val bundled = (context.assets.list(ASSET_DIR) ?: emptyArray())
            .filter { it.endsWith(ext) }
            .map { Entry(it.removeSuffix(ext), "$ASSET_PREFIX$ASSET_DIR/$it") }
        val user = if (StructureCapture.hasStorageAccess()) {
            userMapsDir().listFiles { f -> f.isFile && f.name.endsWith(ext) }
                ?.map { Entry(it.name.removeSuffix(ext), it.absolutePath) }
                .orEmpty()
        } else emptyList()
        // L'arène d'essai d'abord : elle existe toujours, sans rien exporter.
        val builtin = Entry(
            context.getString(com.Atom2Universe.app.R.string.cave_assault_builtin_arena),
            "$BUILTIN_PREFIX${BuiltinMaps.ARENA_ID}")
        return listOf(builtin) + (bundled + user).sortedBy { it.name.lowercase() }
    }

    fun load(context: Context, path: String): A2Map =
        if (path == "$BUILTIN_PREFIX${BuiltinMaps.ARENA_ID}") {
            BuiltinMaps.arena()
        } else if (path.startsWith(ASSET_PREFIX)) {
            context.assets.open(path.removePrefix(ASSET_PREFIX)).buffered().use { A2Map.read(it) }
        } else {
            File(path).inputStream().buffered().use { A2Map.read(it) }
        }

    fun save(map: A2Map, fileName: String): File {
        val dir = userMapsDir().also { it.mkdirs() }
        val safe = fileName.replace(Regex("[^\\p{L}\\p{N}_\\-]"), "_").take(64).ifEmpty { "map" }
        val file = File(dir, "$safe.${A2Map.EXTENSION}")
        file.outputStream().buffered().use { map.write(it) }
        return file
    }
}
