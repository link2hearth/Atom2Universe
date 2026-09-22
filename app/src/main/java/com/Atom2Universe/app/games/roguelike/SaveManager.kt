package com.Atom2Universe.app.games.roguelike

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Sauvegarde séparée : état rapide du niveau et inventaire long du héros. */
object SaveManager {

    private const val PREFS = "roguelike_save"
    private const val KEY_STATE = "state_v7"
    private const val KEY_INVENTORY = "inventory_v7"
    /** v6 : ancienne sauvegarde monolithique, migrée à la première reprise. */
    private const val KEY   = "save_v6"
    private val OLD_KEYS = listOf("save_v5")

    // ── API publique ─────────────────────────────────────────────────────────────

    fun hasSave(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).let {
            it.contains(KEY_STATE) && it.contains(KEY_INVENTORY) || it.contains(KEY)
        }

    fun save(ctx: Context, game: RoguelikeGame) {
        saveInventory(ctx, game)
        saveState(ctx, game)
    }

    fun saveState(ctx: Context, game: RoguelikeGame) {
        val json = game.mapStateToJson().toString()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            OLD_KEYS.forEach { remove(it) }
            remove(KEY)
            putString(KEY_STATE, json)
        }.apply()
    }

    fun saveInventory(ctx: Context, game: RoguelikeGame) {
        val json = game.inventoryToJson().toString()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            OLD_KEYS.forEach { remove(it) }
            remove(KEY)
            putString(KEY_INVENTORY, json)
        }.apply()
    }

    fun saveImmediate(ctx: Context, game: RoguelikeGame) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            OLD_KEYS.forEach { remove(it) }
            remove(KEY)
            putString(KEY_INVENTORY, game.inventoryToJson().toString())
            putString(KEY_STATE, game.mapStateToJson().toString())
        }.commit()
    }

    fun load(ctx: Context): RoguelikeGame? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val inventory = prefs.getString(KEY_INVENTORY, null)
        val state = prefs.getString(KEY_STATE, null)
        if (inventory != null && state != null) {
            return try {
                val game = RoguelikeGame.inventoryFromJson(JSONObject(inventory))
                game.restoreFromSavedState(JSONObject(state))
                game
            } catch (_: Exception) { null }
        }
        val legacy = prefs.getString(KEY, null) ?: return null
        return try {
            RoguelikeGame.fromJson(JSONObject(legacy)).also { saveImmediate(ctx, it) }
        } catch (_: Exception) { null }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY)
            .remove(KEY_STATE)
            .remove(KEY_INVENTORY)
            .apply()
    }

    /** Résumé lisible pour l'écran de choix : "Étage 12 — 340 or". */
    fun saveSummary(ctx: Context): String? {
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val str = prefs.getString(KEY_INVENTORY, null) ?: prefs.getString(KEY, null) ?: return null
        return try {
            val j = JSONObject(str)
            ctx.getString(com.Atom2Universe.app.R.string.roguelike_floor_gold_summary, j.getInt("floor"),
                DungeonNumbers.format(ctx, j.getInt("gold")))
        } catch (_: Exception) { null }
    }

    // ── Sérialisation Equipment ──────────────────────────────────────────────────

    private fun statsToJson(stats: List<StatRoll>) = JSONArray().also { arr ->
        for (st in stats) arr.put(JSONObject().apply {
            put("type",  st.type.name)
            put("value", st.value.toDouble())
            put("tier",  st.tier)
        })
    }

    private fun statsFromJson(arr: JSONArray) = (0 until arr.length()).map { i ->
        val st = arr.getJSONObject(i)
        StatRoll(StatType.valueOf(st.getString("type")), st.getDouble("value").toFloat(), st.optInt("tier"))
    }

    fun equipToJson(e: Equipment): JSONObject = JSONObject().apply {
        put("base",      e.base.name)
        put("power",     e.power)
        put("rarity",    e.rarity.name)
        put("dmgMin",    e.damageMin)
        put("dmgMax",    e.damageMax)
        put("armor",     e.armor)
        put("implicits", statsToJson(e.implicits))
        put("affixes",   statsToJson(e.affixes))
        put("spriteRow", e.spriteRow)
        put("spriteCol", e.spriteCol)
        put("lootId",    e.lootId)
        e.weight?.let { put("weight", it.name) }
        e.isotopeZ?.let { put("isotope", it) }
    }

    fun equipFromJson(j: JSONObject) = Equipment(
        base      = ItemBase.valueOf(j.getString("base")),
        power     = j.getInt("power"),
        rarity    = Rarity.valueOf(j.getString("rarity")),
        damageMin = j.getInt("dmgMin"),
        damageMax = j.getInt("dmgMax"),
        armor     = j.getInt("armor"),
        implicits = statsFromJson(j.getJSONArray("implicits")),
        affixes   = statsFromJson(j.getJSONArray("affixes")),
        spriteRow = j.getInt("spriteRow"),
        spriteCol = j.getInt("spriteCol"),
        lootId    = j.getLong("lootId"),
        weight    = j.optString("weight", "").takeIf { it.isNotEmpty() }
            ?.let { runCatching { ArmorWeight.valueOf(it) }.getOrNull() },
        isotopeZ  = if (j.has("isotope")) j.getInt("isotope") else null,
    ).let { item ->
        // Une ancienne lanterne conserve sa puissance, mais son implicite suit maintenant END.
        if (item.base == ItemBase.LANTERN) item.copy(implicits = item.implicits.map {
            if (it.type == StatType.STR) it.copy(type = StatType.END) else it
        }) else item
    }
}
