package com.Atom2Universe.app.games.caves.world

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object StructureCapture {

    /** Dossier Documents/cave_world/structures/ sur le stockage public. */
    fun structuresDir(): File =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            .resolve("cave_world/structures")
            .also { it.mkdirs() }

    /** True si l'app a accès en écriture au stockage externe public. */
    fun hasStorageAccess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED

    /** Ouvre les paramètres système pour accorder MANAGE_EXTERNAL_STORAGE (Android 11+). */
    fun openStorageSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", context.packageName, null))
            context.startActivity(intent)
        }
    }

    /** Lit la région cornerA..cornerB dans le world et retourne un StructureDef. Les blocs AIR ne sont pas inclus. */
    fun capture(world: World, name: String,
                cornerA: Triple<Int, Int, Int>, cornerB: Triple<Int, Int, Int>): StructureDef {
        val minX = minOf(cornerA.first,  cornerB.first)
        val minY = minOf(cornerA.second, cornerB.second)
        val minZ = minOf(cornerA.third,  cornerB.third)
        val maxX = maxOf(cornerA.first,  cornerB.first)
        val maxY = maxOf(cornerA.second, cornerB.second)
        val maxZ = maxOf(cornerA.third,  cornerB.third)

        val blocks = mutableListOf<IntArray>()
        for (y in minY..maxY) for (z in minZ..maxZ) for (x in minX..maxX) {
            val b = world.blockAt(x, y, z)
            if (b == AIR) continue
            blocks.add(intArrayOf(x - minX, y - minY, z - minZ, b.toInt() and 0xFFFF))
        }

        return StructureDef(
            name  = name,
            sizeX = maxX - minX + 1,
            sizeY = maxY - minY + 1,
            sizeZ = maxZ - minZ + 1,
            blocks = blocks.toTypedArray()
        )
    }

    /** Sérialise et sauvegarde dans Documents/cave_world/structures/NOM.json. */
    fun save(def: StructureDef): File {
        val file = File(structuresDir(), "${sanitizeName(def.name)}.json")
        file.writeText(toJson(def).toString(2))
        return file
    }

    fun toJson(def: StructureDef): JSONObject = JSONObject().apply {
        put("name",  def.name)
        put("sizeX", def.sizeX)
        put("sizeY", def.sizeY)
        put("sizeZ", def.sizeZ)
        val arr = JSONArray()
        for (blk in def.blocks) arr.put(JSONArray().apply { blk.forEach { put(it) } })
        put("blocks", arr)
    }

    fun fromJson(json: JSONObject): StructureDef {
        val name  = json.getString("name")
        val sizeX = json.getInt("sizeX")
        val sizeY = json.getInt("sizeY")
        val sizeZ = json.getInt("sizeZ")
        val arr   = json.getJSONArray("blocks")
        val blocks = Array(arr.length()) { i ->
            val row = arr.getJSONArray(i)
            intArrayOf(row.getInt(0), row.getInt(1), row.getInt(2), row.getInt(3))
        }
        return StructureDef(name, sizeX, sizeY, sizeZ, blocks)
    }

    /** Charge tous les JSONs depuis Documents/cave_world/structures/. */
    fun loadUserStructures(): List<StructureDef> =
        structuresDir()
            .listFiles { f -> f.extension == "json" }
            ?.mapNotNull { f -> runCatching { fromJson(JSONObject(f.readText())) }.getOrNull() }
            ?: emptyList()

    private fun sanitizeName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64).ifEmpty { "structure" }
}
