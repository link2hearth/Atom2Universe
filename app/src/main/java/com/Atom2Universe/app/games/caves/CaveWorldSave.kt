package com.Atom2Universe.app.games.caves

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CaveWorldSave(
    val id: String,
    val name: String,
    val seed: Long,
    val createdAt: Long,
    var lastPlayedAt: Long,
    var playerX: Double,
    var playerY: Double,
    var playerZ: Double,
    var playerYaw: Float,
    var playerPitch: Float,
    var inventory: Map<Byte, Int>,
    var hotbar: List<Byte?>        // 9 slots, null = vide
) {
    fun formattedLastPlayed(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(lastPlayedAt))
    }
}

object CaveWorldSaveManager {

    private fun savesDir(context: Context): File =
        File(context.filesDir, "cave_worlds").also { it.mkdirs() }

    private fun saveFile(context: Context, id: String) =
        File(savesDir(context), "$id.json")

    fun listWorlds(context: Context): List<CaveWorldSave> =
        savesDir(context).listFiles { f -> f.extension == "json" }
            ?.mapNotNull { file -> runCatching { fromJson(JSONObject(file.readText())) }.getOrNull() }
            ?.sortedByDescending { it.lastPlayedAt }
            ?: emptyList()

    fun createWorld(context: Context, name: String, seed: Long): CaveWorldSave {
        val id = "${System.currentTimeMillis()}_${(1000..9999).random()}"
        val now = System.currentTimeMillis()
        val save = CaveWorldSave(
            id = id, name = name, seed = seed,
            createdAt = now, lastPlayedAt = now,
            playerX = 0.0, playerY = 0.0, playerZ = 0.0,
            playerYaw = 0f, playerPitch = 0f,
            inventory = emptyMap(),
            hotbar = List(9) { null }
        )
        persist(context, save)
        return save
    }

    fun updateWorld(context: Context, save: CaveWorldSave) = persist(context, save)

    fun updateFields(context: Context, snap: CaveWorldSave) {
        val existing = loadWorld(context, snap.id) ?: return
        existing.lastPlayedAt = snap.lastPlayedAt
        existing.playerX      = snap.playerX
        existing.playerY      = snap.playerY
        existing.playerZ      = snap.playerZ
        existing.playerYaw    = snap.playerYaw
        existing.playerPitch  = snap.playerPitch
        existing.inventory    = snap.inventory
        existing.hotbar       = snap.hotbar
        persist(context, existing)
    }

    fun deleteWorld(context: Context, id: String) {
        saveFile(context, id).delete()
        File(context.filesDir, "cave_worlds/$id").deleteRecursively()
    }

    fun loadWorld(context: Context, id: String): CaveWorldSave? =
        runCatching { fromJson(JSONObject(saveFile(context, id).readText())) }.getOrNull()

    private fun persist(context: Context, save: CaveWorldSave) {
        val json = JSONObject().apply {
            put("id", save.id)
            put("name", save.name)
            put("seed", save.seed)
            put("createdAt", save.createdAt)
            put("lastPlayedAt", save.lastPlayedAt)
            put("playerX", save.playerX)
            put("playerY", save.playerY)
            put("playerZ", save.playerZ)
            put("playerYaw", save.playerYaw.toDouble())
            put("playerPitch", save.playerPitch.toDouble())
            val invJson = JSONObject()
            save.inventory.forEach { (k, v) -> invJson.put(k.toString(), v) }
            put("inventory", invJson)
            val hotbarArr = JSONArray()
            save.hotbar.forEach { v -> hotbarArr.put(v?.toInt() ?: -1) }
            put("hotbar", hotbarArr)
        }
        saveFile(context, save.id).writeText(json.toString())
    }

    private fun fromJson(j: JSONObject): CaveWorldSave {
        val invJson = j.optJSONObject("inventory") ?: JSONObject()
        val inventory = mutableMapOf<Byte, Int>()
        invJson.keys().forEach { k -> inventory[k.toByte()] = invJson.getInt(k) }
        val hotbarArr = j.optJSONArray("hotbar")
        val hotbar: List<Byte?> = if (hotbarArr != null) {
            (0 until hotbarArr.length()).map { i ->
                val v = hotbarArr.getInt(i); if (v < 0) null else v.toByte()
            }
        } else List(9) { null }
        return CaveWorldSave(
            id = j.getString("id"),
            name = j.getString("name"),
            seed = j.getLong("seed"),
            createdAt = j.getLong("createdAt"),
            lastPlayedAt = j.getLong("lastPlayedAt"),
            playerX = j.getDouble("playerX"),
            playerY = j.getDouble("playerY"),
            playerZ = j.getDouble("playerZ"),
            playerYaw = j.getDouble("playerYaw").toFloat(),
            playerPitch = j.getDouble("playerPitch").toFloat(),
            inventory = inventory,
            hotbar = hotbar
        )
    }
}
