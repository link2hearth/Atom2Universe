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
import com.Atom2Universe.app.games.caves.world.CaveCheckpoint

internal data class StuckAmmo(val x: Double,val y: Double,val z: Double,
    val vx: Double,val vy: Double,val vz: Double,val ammoId: Short)

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
    var farming: String = "{}",
    var workshops: String = "{}",
    var worldTimeMs: Long = 400_000L,
    var frontierLife: String = "{}",
    var chunkChanges: Map<String,CaveCheckpoint.Edit> = emptyMap(),
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
    // Compétences RPG (XP cumulatif, niveau calculé à la volée)
    // IDs ≥ 10000 → instances d'armes dynamiques
    var weaponInstances: Map<Short, ItemInstance> = emptyMap(),
    var recoverableAmmo: List<StuckAmmo> = emptyList(),
    var passiveAnimals: String = "[]",
    val terrainVersion: Int = 5
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
        savesDir(context).listFiles().orEmpty().mapNotNull { file ->
            when { file.extension=="json" -> file.nameWithoutExtension
                file.isDirectory && File(file,"checkpoint.db").exists() -> file.name
                else -> null }
        }.distinct().mapNotNull { loadWorld(context,it) }.sortedByDescending { it.lastPlayedAt }

    fun createWorld(context: Context, name: String, seed: Long, isCreative: Boolean = false): CaveWorldSave {
        val id = "${System.currentTimeMillis()}_${(1000..9999).random()}"
        val now = System.currentTimeMillis()
        val save = CaveWorldSave(
            id = id, name = name, seed = seed, isCreative = isCreative,
            createdAt = now, lastPlayedAt = now,
            playerX = 0.0, playerY = 0.0, playerZ = 0.0,
            playerYaw = 0f, playerPitch = 0f,
            inventory = emptyMap(),
            hotbar = List(CaveActivity.ACTIVE_SIZE) { null }
        )
        persist(context, save)
        return save
    }

    fun updateWorld(context: Context, save: CaveWorldSave) = persist(context, save)

    @Synchronized fun updateFields(context: Context, snap: CaveWorldSave): Boolean {
        val existing = checkNotNull(loadWorld(context, snap.id)) { "World checkpoint unavailable" }
        if (existing.lastPlayedAt > snap.lastPlayedAt) return false
        existing.lastPlayedAt        = snap.lastPlayedAt
        existing.playerX             = snap.playerX
        existing.playerY             = snap.playerY
        existing.playerZ             = snap.playerZ
        existing.playerYaw           = snap.playerYaw
        existing.playerPitch         = snap.playerPitch
        existing.inventory           = snap.inventory
        existing.hotbar              = snap.hotbar
        existing.farming = snap.farming
        existing.workshops = snap.workshops
        existing.worldTimeMs = snap.worldTimeMs
        existing.frontierLife = snap.frontierLife
        existing.chunkChanges = snap.chunkChanges
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
        existing.recoverableAmmo = snap.recoverableAmmo
        existing.passiveAnimals = snap.passiveAnimals
        persist(context, existing)
        return true
    }

    fun deleteWorld(context: Context, id: String) {
        saveFile(context, id).delete()
        File(context.filesDir, "cave_worlds/$id").deleteRecursively()
    }

    @Synchronized fun loadWorld(context: Context, id: String): CaveWorldSave? =
        runCatching { fromJson(JSONObject(CaveCheckpoint.metadata(File(context.filesDir,"cave_worlds/$id"))
            ?: android.util.AtomicFile(saveFile(context,id)).readFully().toString(Charsets.UTF_8))) }.getOrNull()

    @Synchronized private fun persist(context: Context, save: CaveWorldSave) {
        val json = JSONObject().apply {
            put("itemSchema",1)
            put("id", save.id)
            put("name", save.name)
            put("seed", save.seed)
            put("terrainVersion", save.terrainVersion)
            put("passiveAnimals", save.passiveAnimals)
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

            put("farming", save.farming)
            put("workshops", save.workshops)
            put("worldTimeMs", save.worldTimeMs)
            put("frontierLife", save.frontierLife)
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
            put("recoverableAmmo",JSONArray().also { arr ->
                save.recoverableAmmo.takeLast(256).forEach { a -> arr.put(JSONObject().apply {
                    put("x",a.x);put("y",a.y);put("z",a.z)
                    put("vx",a.vx);put("vy",a.vy);put("vz",a.vz);put("ammo",a.ammoId.toInt())
                }) }
            })
        }
        CaveCheckpoint.commit(File(context.filesDir,"cave_worlds/${save.id}"),json.toString(),save.chunkChanges)
        // Compatibility mirror only: the database is authoritative and world listing also
        // discovers database directories if a crash interrupts this optional mirror write.
        runCatching {
            val file=android.util.AtomicFile(saveFile(context,save.id))
            val output=file.startWrite()
            try { output.write(json.toString().toByteArray(Charsets.UTF_8));file.finishWrite(output) }
            catch(error: Exception) { file.failWrite(output);throw error }
        }.onFailure { android.util.Log.w("CaveSave","Checkpoint committed; metadata mirror unavailable",it) }
    }

    private fun fromJson(j: JSONObject): CaveWorldSave {
        // Old static buckets overlapped weapon instance IDs. Known weapons keep their IDs;
        // only unambiguous bucket stacks and references move below the weapon range.
        if(j.optInt("itemSchema",0)<1) {
            val weaponIds=j.optJSONObject("weaponInstances") ?: JSONObject()
            fun migrateItems(items: JSONObject?) {
                items ?: return
                for(old in 10000..10001) {
                    val key=old.toString();val count=items.optInt(key,0)
                    if(count<=0 || weaponIds.has(key)) continue
                    val newKey=(old-10).toString()
                    items.put(newKey,(items.optLong(newKey,0)+count).coerceAtMost(Int.MAX_VALUE.toLong()))
                    items.remove(key)
                }
            }
            migrateItems(j.optJSONObject("inventory"))
            for(key in listOf("hotbar","buildHotbar","gardenHotbar")) j.optJSONArray(key)?.let { arr ->
                for(i in 0 until arr.length()) { val old=arr.optInt(i,-1)
                    if(old in 10000..10001 && !weaponIds.has(old.toString())) arr.put(i,old-10)
                }
            }
            val workshops=runCatching { JSONObject(j.optString("workshops","{}")) }.getOrElse { JSONObject() }
            workshops.optJSONArray("stores")?.let { rows -> for(i in 0 until rows.length()) migrateItems(rows.optJSONObject(i)?.optJSONObject("items")) }
            j.put("workshops",workshops.toString())
        }
        val invJson = j.optJSONObject("inventory") ?: JSONObject()
        val inventory = mutableMapOf<Short, Int>()
        invJson.keys().forEach { k -> inventory[k.toShort()] = invJson.getInt(k) }
        val hotbarArr = j.optJSONArray("hotbar")
        val hotbar: List<Short?> = if (hotbarArr != null) {
            (0 until hotbarArr.length()).map { i ->
                val v = hotbarArr.getInt(i); if (v < 0) null else v.toShort()
            }
        } else List(CaveActivity.ACTIVE_SIZE) { null }
        val buildHotbarArr = j.optJSONArray("buildHotbar")
        val buildHotbar: List<Short?> = if (buildHotbarArr != null) {
            (0 until buildHotbarArr.length()).map { i ->
                val v = buildHotbarArr.getInt(i); if (v < 0) null else v.toShort()
            }
        } else emptyList()
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
            terrainVersion = j.optInt("terrainVersion", 1),
            passiveAnimals = j.optString("passiveAnimals", "[]"),
            createdAt = j.getLong("createdAt"),
            lastPlayedAt = j.getLong("lastPlayedAt"),
            playerX = j.getDouble("playerX"),
            playerY = j.getDouble("playerY"),
            playerZ = j.getDouble("playerZ"),
            playerYaw = j.getDouble("playerYaw").toFloat(),
            playerPitch = j.getDouble("playerPitch").toFloat(),
            inventory = inventory,
            hotbar = CaveHotbar.restore(hotbar,buildHotbar,
                j.optJSONArray("gardenHotbar")?.let { a -> (0 until a.length()).map { a.optInt(it,-1).takeIf { id -> id>=0 }?.toShort() } }.orEmpty(),if(j.optBoolean("isCreative",false)) null else inventory),
            farming = j.optString("farming", "{}"),
            workshops = j.optString("workshops", "{}"),
            worldTimeMs = j.optLong("worldTimeMs", 400_000L).coerceAtLeast(0L),
            frontierLife = j.optString("frontierLife", "{}"),
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
            weaponInstances = weaponInstances,
            recoverableAmmo = j.optJSONArray("recoverableAmmo")?.let { arr ->
                (0 until minOf(arr.length(),256)).mapNotNull { i ->
                    val a=arr.optJSONObject(i) ?: return@mapNotNull null
                    val id=a.optInt("ammo")
                    if(id!=8010 && id!=8011) return@mapNotNull null
                    val values=listOf("x","y","z","vx","vy","vz").map { a.optDouble(it) }
                    if(values.any { !it.isFinite() }) null else StuckAmmo(values[0],values[1],values[2],values[3],values[4],values[5],id.toShort())
                }
            } ?: emptyList()
        )
    }
}
