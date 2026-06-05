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
    private const val KEY   = "save_v2"

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
            "Étage ${j.getInt("floor")}  —  ${j.getInt("gold")} or"
        } catch (_: Exception) { null }
    }

    // ── Sérialisation Equipment ──────────────────────────────────────────────────

    fun equipToJson(e: Equipment): JSONObject = JSONObject().apply {
        put("slot",      e.slot.name)
        put("material",  e.material.name)
        put("rarity",    e.rarity.name)
        put("label",     e.label)
        put("spriteRow", e.spriteRow)
        put("spriteCol", e.spriteCol)
        put("stats", JSONArray().also { arr ->
            for (s in e.stats) arr.put(JSONObject().apply {
                put("type",  s.type.name)
                put("value", s.value.toDouble())
            })
        })
    }

    fun equipFromJson(j: JSONObject): Equipment {
        val statsArr = j.getJSONArray("stats")
        val stats = (0 until statsArr.length()).map { i ->
            val s = statsArr.getJSONObject(i)
            StatRoll(StatType.valueOf(s.getString("type")), s.getDouble("value").toFloat())
        }
        return Equipment(
            slot      = EquipSlot.valueOf(j.getString("slot")),
            material  = EquipMaterial.valueOf(j.getString("material")),
            rarity    = Rarity.valueOf(j.getString("rarity")),
            label     = j.getString("label"),
            stats     = stats,
            spriteRow = j.getInt("spriteRow"),
            spriteCol = j.getInt("spriteCol"),
        )
    }
}
