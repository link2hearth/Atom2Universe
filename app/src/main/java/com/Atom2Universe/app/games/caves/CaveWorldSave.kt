package com.Atom2Universe.app.games.caves

import android.content.Context
import com.Atom2Universe.app.games.caves.node.ItemInstance
import com.Atom2Universe.app.games.caves.node.ItemRarity
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal data class CaveWorldSave(
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
    var inventory: Map<Short, Int>,
    var hotbar: List<Short?>,
    // Progression joueur
    var playerHp: Int = 20,
    var playerLevel: Int = 1,
    var playerXp: Int = 0,
    var playerDamage: Int = 2,
    var playerFireRate: Float = 1.5f,
    var playerMaxHp: Int = 20,
    var playerShield: Int = 0,
    var playerShieldCurrent: Int = 0,
    var playerWeapons: List<String> = listOf("WHITE_SQUARE"),  // "COLOR_VARIANT"
    var wardStonePositions: List<Pair<Double, Double>> = emptyList(),
    var isCreative: Boolean = false,
    // IDs ≥ 10000 → instances d'armes dynamiques
    var weaponInstances: Map<Short, ItemInstance> = emptyMap()
) {
    fun formattedLastPlayed(): String {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        return sdf.format(Date(lastPlayedAt))
    }
}

internal object CaveWorldSaveManager {

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
            hotbar = List(19) { null }
        )
        persist(context, save)
        return save
    }

    fun updateWorld(context: Context, save: CaveWorldSave) = persist(context, save)

    fun updateFields(context: Context, snap: CaveWorldSave) {
        val existing = loadWorld(context, snap.id) ?: return
        existing.lastPlayedAt        = snap.lastPlayedAt
        existing.playerX             = snap.playerX
        existing.playerY             = snap.playerY
        existing.playerZ             = snap.playerZ
        existing.playerYaw           = snap.playerYaw
        existing.playerPitch         = snap.playerPitch
        existing.inventory           = snap.inventory
        existing.hotbar              = snap.hotbar
        existing.playerHp            = snap.playerHp
        existing.playerLevel         = snap.playerLevel
        existing.playerXp            = snap.playerXp
        existing.playerDamage        = snap.playerDamage
        existing.playerFireRate      = snap.playerFireRate
        existing.playerMaxHp         = snap.playerMaxHp
        existing.playerShield        = snap.playerShield
        existing.playerShieldCurrent = snap.playerShieldCurrent
        existing.playerWeapons       = snap.playerWeapons
        existing.wardStonePositions  = snap.wardStonePositions
        existing.isCreative          = snap.isCreative
        existing.weaponInstances     = snap.weaponInstances
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
            put("playerHp", save.playerHp)
            put("playerLevel", save.playerLevel)
            put("playerXp", save.playerXp)
            put("playerDamage", save.playerDamage)
            put("playerFireRate", save.playerFireRate.toDouble())
            put("playerMaxHp", save.playerMaxHp)
            put("playerShield", save.playerShield)
            put("playerShieldCurrent", save.playerShieldCurrent)
            val weaponsArr = JSONArray()
            save.playerWeapons.forEach { weaponsArr.put(it) }
            put("playerWeapons", weaponsArr)
            val wardArr = JSONArray()
            save.wardStonePositions.forEach { (x, z) ->
                wardArr.put(JSONObject().apply { put("x", x); put("z", z) })
            }
            put("wardStonePositions", wardArr)
            put("isCreative", save.isCreative)
            val wiJson = JSONObject()
            save.weaponInstances.forEach { (id, inst) ->
                val o = JSONObject().apply {
                    put("def_id", inst.defId)
                    put("rarity", inst.rarity.name)
                    inst.rolledDamage?.let { put("damage", it) }
                    val statsJson = JSONObject()
                    inst.rolledStats.forEach { (k, v) -> statsJson.put(k, v) }
                    put("stats", statsJson)
                    put("tier", inst.tier)
                }
                wiJson.put(id.toString(), o)
            }
            put("weaponInstances", wiJson)
        }
        saveFile(context, save.id).writeText(json.toString())
    }

    private fun fromJson(j: JSONObject): CaveWorldSave {
        val invJson = j.optJSONObject("inventory") ?: JSONObject()
        val inventory = mutableMapOf<Short, Int>()
        invJson.keys().forEach { k -> inventory[k.toShort()] = invJson.getInt(k) }
        val hotbarArr = j.optJSONArray("hotbar")
        val hotbar: List<Short?> = if (hotbarArr != null) {
            (0 until hotbarArr.length()).map { i ->
                val v = hotbarArr.getInt(i); if (v < 0) null else v.toShort()
            }
        } else List(CaveActivity.ACTIVE_SIZE) { null }
        val weaponsArr = j.optJSONArray("playerWeapons")
        val weapons: List<String> = if (weaponsArr != null) {
            (0 until weaponsArr.length()).map { weaponsArr.getString(it) }
        } else listOf("WHITE_SQUARE")
        val wardArr2 = j.optJSONArray("wardStonePositions")
        val wardStones: List<Pair<Double, Double>> = if (wardArr2 != null) {
            (0 until wardArr2.length()).mapNotNull { i ->
                val o = wardArr2.optJSONObject(i) ?: return@mapNotNull null
                Pair(o.getDouble("x"), o.getDouble("z"))
            }
        } else emptyList()
        val wiJson = j.optJSONObject("weaponInstances") ?: JSONObject()
        val weaponInstances = mutableMapOf<Short, ItemInstance>()
        wiJson.keys().forEach { k ->
            val o = wiJson.optJSONObject(k) ?: return@forEach
            val statsJson = o.optJSONObject("stats") ?: JSONObject()
            val stats = mutableMapOf<String, Int>()
            statsJson.keys().forEach { sk -> stats[sk] = statsJson.getInt(sk) }
            weaponInstances[k.toShort()] = ItemInstance(
                defId        = o.getString("def_id"),
                rarity       = runCatching { ItemRarity.valueOf(o.getString("rarity")) }.getOrDefault(ItemRarity.COMMON),
                rolledDamage = if (o.has("damage")) o.getInt("damage") else null,
                rolledStats  = stats,
                tier         = o.optInt("tier", 0)
            )
        }
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
            hotbar = hotbar,
            playerHp = j.optInt("playerHp", 20),
            playerLevel = j.optInt("playerLevel", 1),
            playerXp = j.optInt("playerXp", 0),
            playerDamage = j.optInt("playerDamage", 2),
            playerFireRate = j.optDouble("playerFireRate", 1.5).toFloat(),
            playerMaxHp = j.optInt("playerMaxHp", 20),
            playerShield = j.optInt("playerShield", 0),
            playerShieldCurrent = j.optInt("playerShieldCurrent", 0),
            playerWeapons = weapons,
            wardStonePositions = wardStones,
            isCreative = j.optBoolean("isCreative", false),
            weaponInstances = weaponInstances
        )
    }
}
