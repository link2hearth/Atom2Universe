package com.Atom2Universe.app.games.roguelike

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Sauvegarde séparée : état rapide du niveau et inventaire long du héros. Les écritures passent par
 * `apply()` : le disque est écrit hors du fil UI, dans l'ordre des appels (la dernière gagne), et
 * Android termine les écritures en attente avant d'arrêter l'activité.
 */
object SaveManager {

    private const val PREFS = "roguelike_save"
    private const val KEY_STATE = "state_v7"
    private const val KEY_INVENTORY = "inventory_v7"
    /** Une sauvegarde illisible est mise de côté ici avant de repartir, jamais écrasée. */
    private const val KEY_STATE_RESCUE = "state_v7_rescue"
    private const val KEY_INVENTORY_RESCUE = "inventory_v7_rescue"

    // ── API publique ─────────────────────────────────────────────────────────────

    /** Tout : l'inventaire et l'étage. */
    fun save(ctx: Context, game: RoguelikeGame) {
        val inventory = game.inventoryToJson().toString()
        val state = game.mapStateToJson().toString()
        prefs(ctx).edit()
            .putString(KEY_INVENTORY, inventory)
            .putString(KEY_STATE, state)
            .apply()
    }

    /** L'étage seul : la marche. */
    fun saveState(ctx: Context, game: RoguelikeGame) {
        val state = game.mapStateToJson().toString()
        prefs(ctx).edit().putString(KEY_STATE, state).apply()
    }

    fun load(ctx: Context): RoguelikeGame? {
        val prefs = prefs(ctx)
        val inventory = prefs.getString(KEY_INVENTORY, null) ?: return null
        val state = prefs.getString(KEY_STATE, null)
        if (state != null) {
            try {
                return RoguelikeGame.fromSave(JSONObject(inventory), JSONObject(state))
            } catch (_: Exception) {
                rescue(prefs, inventory, state)
            }
        }
        // L'étage est perdu : on garde au moins le héros, sur un étage neuf
        return try {
            RoguelikeGame.fromSave(JSONObject(inventory), null)
        } catch (_: Exception) {
            rescue(prefs, inventory, state)
            null
        }
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** La première sauvegarde perdue est la plus précieuse : une suivante ne la remplace pas. */
    private fun rescue(prefs: SharedPreferences, inventory: String, state: String?) {
        if (prefs.contains(KEY_INVENTORY_RESCUE)) return
        prefs.edit()
            .putString(KEY_INVENTORY_RESCUE, inventory)
            .putString(KEY_STATE_RESCUE, state)
            .commit()
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
        put("grade",     e.rarity.name)
        put("dmgMin",    e.damageMin)
        put("dmgMax",    e.damageMax)
        put("armor",     e.armor)
        put("implicits", statsToJson(e.implicits))
        put("affixes",   statsToJson(e.affixes))
        put("lootId",    e.lootId)
        e.weight?.let { put("weight", it.name) }
        e.isotopeZ?.let { put("isotope", it) }
    }

    fun equipFromJson(j: JSONObject) = Equipment(
        base      = ItemBase.valueOf(j.getString("base")),
        power     = j.getInt("power"),
        rarity    = Rarity.valueOf(j.getString("grade")),
        damageMin = j.getInt("dmgMin"),
        damageMax = j.getInt("dmgMax"),
        armor     = j.getInt("armor"),
        implicits = statsFromJson(j.getJSONArray("implicits")),
        affixes   = statsFromJson(j.getJSONArray("affixes")),
        lootId    = j.getLong("lootId"),
        weight    = j.optString("weight", "").takeIf { it.isNotEmpty() }
            ?.let { runCatching { ArmorWeight.valueOf(it) }.getOrNull() },
        isotopeZ  = if (j.has("isotope")) j.getInt("isotope") else null,
    )

    /** Un objet illisible (une valeur renommée…) est laissé de côté, seul : le reste du sac se charge. */
    fun equipOrNull(j: JSONObject?): Equipment? = j?.let { runCatching { equipFromJson(it) }.getOrNull() }
}
