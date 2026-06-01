package com.Atom2Universe.app.games.caves.node

import org.json.JSONObject

internal data class CraftDef(
    val ingredients: List<Pair<Short, Int>>,
    val result: Short,
    val resultCount: Int,
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
                ingredients = ingredients,
                result      = j.getInt("result").toShort(),
                resultCount = j.optInt("result_count", 1),
            )
        }
    }
}
