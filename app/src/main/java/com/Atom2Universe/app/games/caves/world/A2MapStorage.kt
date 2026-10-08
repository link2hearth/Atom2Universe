package com.Atom2Universe.app.games.caves.world

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import java.io.File

/**
 * Où vivent les cartes du mode Assaut : livrées avec l'appli (assets/caves/maps) ou exportées
 * par le joueur depuis un monde créatif (Documents/cave_world/maps, comme les structures).
 */
internal object A2MapStorage {

    private const val ASSET_DIR = "caves/maps"
    private const val ASSET_PREFIX = "asset:"
    private const val BUILTIN_PREFIX = "builtin:"
    const val SHOWCASE_PATH = "builtin:biome_showcase"
    const val TRAINING_PATH = "builtin:combat_training"

    /**
     * [path] : chemin de fichier, « asset:… » pour une carte livrée avec l'appli, ou
     * « builtin:… » pour une carte fabriquée par le code ([BuiltinMaps]).
     */
    data class Entry(val name: String, val path: String)

    /** True si l'app a accès en écriture au stockage externe public. */
    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    /** Ouvre les paramètres système pour accorder MANAGE_EXTERNAL_STORAGE (Android 11+). */
    fun openStorageSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null)))
        }
    }

    fun userMapsDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .resolve("cave_world/maps")

    fun list(context: Context): List<Entry> {
        val ext = ".${A2Map.EXTENSION}"
        val bundled = (context.assets.list(ASSET_DIR) ?: emptyArray())
            .filter { it.endsWith(ext) }
            .map { Entry(it.removeSuffix(ext), "$ASSET_PREFIX$ASSET_DIR/$it") }
        val user = if (hasStorageAccess()) {
            userMapsDir().listFiles { f -> f.isFile && f.name.endsWith(ext) }
                ?.map { Entry(it.name.removeSuffix(ext), it.absolutePath) }
                .orEmpty()
        } else emptyList()
        // L'arène d'essai d'abord : elle existe toujours, sans rien exporter.
        val builtin = Entry(
            context.getString(com.Atom2Universe.app.R.string.cave_assault_builtin_arena),
            "$BUILTIN_PREFIX${BuiltinMaps.ARENA_ID}")
        val showcase = Entry(context.getString(com.Atom2Universe.app.R.string.cave_assault_showcase), SHOWCASE_PATH)
        val tower = Entry(context.getString(com.Atom2Universe.app.R.string.cave_assault_office_tower),
            "$BUILTIN_PREFIX${OfficeTowerMap.ID}")
        val suburb = Entry(context.getString(com.Atom2Universe.app.R.string.cave_assault_maple_crossing),
            "$BUILTIN_PREFIX${MapleCrossingMap.ID}")
        val dust2 = Entry(context.getString(com.Atom2Universe.app.R.string.cave_assault_dust2),
            "$BUILTIN_PREFIX${Dust2Map.ID}")
        val training = Entry(context.getString(com.Atom2Universe.app.R.string.cave_training_map), TRAINING_PATH)
        return listOf(training, showcase, dust2, suburb, builtin, tower) + (bundled + user).sortedBy { it.name.lowercase() }
    }

    fun load(context: Context, path: String): A2Map =
        if (path == TRAINING_PATH) {
            com.Atom2Universe.app.games.caves.node.BlockRegistry.load(context.assets)
            com.Atom2Universe.app.games.caves.node.MobRegistry.load(context.assets)
            CombatTrainingMap.create()
        } else if (path == SHOWCASE_PATH) {
            com.Atom2Universe.app.games.caves.node.BlockRegistry.load(context.assets)
            com.Atom2Universe.app.games.caves.node.MobRegistry.load(context.assets)
            ShowcaseMap.create()
        } else if (path == "$BUILTIN_PREFIX${BuiltinMaps.ARENA_ID}") {
            BuiltinMaps.arena()
        } else if (path == "$BUILTIN_PREFIX${Dust2Map.ID}") {
            Dust2Map.create()
        } else if (path == "$BUILTIN_PREFIX${OfficeTowerMap.ID}") {
            OfficeTowerMap.create()
        } else if (path == "$BUILTIN_PREFIX${MapleCrossingMap.ID}") {
            com.Atom2Universe.app.games.caves.node.BlockRegistry.load(context.assets)
            MapleCrossingMap.create()
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
