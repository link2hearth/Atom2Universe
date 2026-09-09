package com.Atom2Universe.app.games.caves.node

import org.json.JSONObject

internal data class CraftDef(
    val ingredients: List<Pair<Short, Int>>,
    val result: Short = 0,
    val resultCount: Int = 1,
    /** Recette d'arme : id [ItemRegistry] à rouler (rareté/affixes) au lieu d'un simple bloc. */
    val resultItemId: String? = null
) {
    fun canCraft(inv: Map<Short, Int>) = ingredients.all { (id, n) -> (inv[id] ?: 0) >= n }

    companion object {
        fun fromJson(j: JSONObject): CraftDef {
            val arr = j.getJSONArray("ingredients")
            val ingredients = (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                o.getInt("id").toShort() to o.getInt("count")
            }
            return CraftDef(
                ingredients  = ingredients,
                result       = if (j.has("result")) j.getInt("result").toShort() else 0,
                resultCount  = j.optInt("result_count", 1),
                resultItemId = j.optString("result_item").takeIf { it.isNotBlank() }
            )
        }
    }
}
