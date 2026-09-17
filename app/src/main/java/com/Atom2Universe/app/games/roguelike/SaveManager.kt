package com.Atom2Universe.app.games.roguelike

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde l'état du joueur dans les SharedPreferences (pas le niveau — il est
 * régénéré à la reprise). Format JSON via org.json (pas de dépendance externe).
 */
object SaveManager {

    private const val PREFS = "roguelike_save"
    private const val KEY   = "save_v5"  // v5 : objets à base, matière et tier, sac

    // ── API publique ─────────────────────────────────────────────────────────────

    fun hasSave(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).contains(KEY)

    fun save(ctx: Context, game: RoguelikeGame) {
        val json = game.toJson().toString()
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json).apply()
    }

    fun load(ctx: Context): RoguelikeGame? {
        val str = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return try { RoguelikeGame.fromJson(JSONObject(str)) } catch (_: Exception) { null }
    }

    fun clear(ctx: Context) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }

    /** Résumé lisible pour l'écran de choix : "Étage 12 — 340 or". */
    fun saveSummary(ctx: Context): String? {
        val str = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return null
        return try {
            val j = JSONObject(str)
            ctx.getString(com.Atom2Universe.app.R.string.roguelike_floor_gold_summary, j.getInt("floor"), j.getInt("gold"))
        } catch (_: Exception) { null }
    }

    // ── Sérialisation Equipment ──────────────────────────────────────────────────

    private fun statsToJson(stats: List<StatRoll>) = JSONArray().also { arr ->
        for (st in stats) arr.put(JSONObject().apply {
            put("type",  st.type.name)
            put("value", st.value.toDouble())
        })
    }

    private fun statsFromJson(arr: JSONArray) = (0 until arr.length()).map { i ->
        val st = arr.getJSONObject(i)
        StatRoll(StatType.valueOf(st.getString("type")), st.getDouble("value").toFloat())
    }

    fun equipToJson(e: Equipment): JSONObject = JSONObject().apply {
        put("base",      e.base.name)
        put("material",  e.material.name)
        put("tier",      e.tier)
        put("rarity",    e.rarity.name)
        put("dmgMin",    e.damageMin)
        put("dmgMax",    e.damageMax)
        put("armor",     e.armor)
        put("implicits", statsToJson(e.implicits))
        put("affixes",   statsToJson(e.affixes))
        put("spriteRow", e.spriteRow)
        put("spriteCol", e.spriteCol)
        put("lootId",    e.lootId)
    }

    fun equipFromJson(j: JSONObject) = Equipment(
        base      = ItemBase.valueOf(j.getString("base")),
        material  = Material.valueOf(j.getString("material")),
        tier      = j.getInt("tier"),
        rarity    = Rarity.valueOf(j.getString("rarity")),
        damageMin = j.getInt("dmgMin"),
        damageMax = j.getInt("dmgMax"),
        armor     = j.getInt("armor"),
        implicits = statsFromJson(j.getJSONArray("implicits")),
        affixes   = statsFromJson(j.getJSONArray("affixes")),
        spriteRow = j.getInt("spriteRow"),
        spriteCol = j.getInt("spriteCol"),
        lootId    = j.getLong("lootId"),
    )
}
