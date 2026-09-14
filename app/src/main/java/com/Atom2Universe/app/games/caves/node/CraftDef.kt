package com.Atom2Universe.app.games.caves.node

import org.json.JSONObject

internal data class CraftGroup(val tag: String, val ids: List<Short>, val count: Int) {
    fun available(inv: Map<Short, Int>): Long = ids.sumOf { (inv[it] ?: 0).coerceAtLeast(0).toLong() }
}

internal data class CraftDef(
    val ingredients: List<Pair<Short, Int>>,
    val result: Short = 0,
    val resultCount: Int = 1,
    val resultItemId: String? = null,
    val groups: List<CraftGroup> = emptyList(),
    val tools: List<Short> = emptyList(),
) {
    val inputIds: Set<Short> = (ingredients.map { it.first } + groups.flatMap { it.ids } + tools).toSet()
    init {
        require(resultCount > 0 && ((result > 0) xor (resultItemId != null)))
        val inputs = ingredients.map { it.first } + groups.flatMap { it.ids }
        require(inputs.isNotEmpty() && inputs.distinct().size == inputs.size) { "Overlapping craft ingredients" }
        require(ingredients.all { it.second > 0 } && groups.all { it.count > 0 && it.ids.isNotEmpty() })
        require(tools.none { it in inputs }) { "A required tool cannot be consumed" }
    }
    fun maxCraftable(inv: Map<Short, Int>): Int {
        if (tools.any { (inv[it] ?: 0) < 1 }) return 0
        val limits = ingredients.map { (id, n) -> (inv[id] ?: 0).coerceAtLeast(0).toLong() / n } + groups.map { it.available(inv) / it.count }
        return (limits.minOrNull() ?: 0).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    fun canCraft(inv: Map<Short, Int>) = maxCraftable(inv) > 0

    /** Exact debit for mixed wood/fuel batches; equipment is never consumed. */
    fun consumption(inv: Map<Short, Int>, batches: Int): Map<Short, Int>? {
        if (batches <= 0 || maxCraftable(inv) < batches) return null
        val debit = ingredients.associate { it.first to Math.multiplyExact(it.second, batches) }.toMutableMap()
        for (group in groups) {
            var remaining = group.count.toLong() * batches
            for (id in group.ids.sorted()) {
                val take = minOf(remaining, (inv[id] ?: 0).coerceAtLeast(0).toLong()).toInt()
                if (take > 0) debit[id] = take
                remaining -= take
            }
            check(remaining == 0L)
        }
        return debit
    }
    companion object {
        fun fromJson(j: JSONObject): CraftDef {
            val arr = j.getJSONArray("ingredients")
            val exact = mutableListOf<Pair<Short, Int>>()
            val groups = mutableListOf<CraftGroup>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                require(o.has("id") xor o.has("tag"))
                if (o.has("tag")) {
                    val tag = o.getString("tag")
                    groups += CraftGroup(tag, BlockRegistry.all().filter { tag in it.tags }.map { it.id }, o.getInt("count"))
                } else {
                    val id = o.getInt("id").toShort()
                    require(BlockRegistry.get(id) != null) { "Unknown ingredient $id" }
                    exact += id to o.getInt("count")
                }
            }
            val tools = j.optJSONArray("tools")?.let { a -> (0 until a.length()).map { a.getInt(it).toShort() } } ?: emptyList()
            require(tools.all { BlockRegistry.get(it) != null })
            val result = j.optInt("result", 0).toShort()
            require(result == 0.toShort() || BlockRegistry.get(result) != null)
            return CraftDef(exact, result, j.optInt("result_count", 1),
                j.optString("result_item").takeIf { it.isNotBlank() }, groups, tools)
        }
    }
}
