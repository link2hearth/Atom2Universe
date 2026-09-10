package com.Atom2Universe.app.games.farm

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

/** A saved, non-crossing route. The previous pass is the next pass's cultivable footprint. */
class LargeField(val index: Int) {
    val columns = 6 + index
    val rows = 9 + index
    val size get() = columns * rows
    val price get() = listOf(0, 400, 1200)[index]
    val seedCost get() = listOf(5, 10, 20)[index]
    var phase = 0 // plough, seed, growing, harvest
    var readyAt = 0L
    var paid = false
    var eligible = (0 until size).toList()
    val route = mutableListOf<Int>()
    val start get() = eligible.first()
    val current get() = route.lastOrNull() ?: start
    val coverage get() = route.size * 100 / eligible.size
    fun adjacent(a: Int, b: Int) = abs(a % columns - b % columns) + abs(a / columns - b / columns) == 1
    fun move(cell: Int): Boolean {
        if (!paid || phase == 2 || cell !in eligible || cell in route) return false
        if (route.isEmpty() && cell != start || route.isNotEmpty() && !adjacent(current, cell)) return false
        route.add(cell); return true
    }
    fun retry() { route.clear() }
    fun json() = JSONObject().put("phase", phase).put("readyAt", readyAt).put("paid", paid)
        .put("eligible", JSONArray(eligible)).put("route", JSONArray(route))
    fun restore(j: JSONObject) {
        val p = j.getInt("phase"); require(p in 0..3)
        fun cells(key: String): List<Int> = j.getJSONArray(key).let { a -> List(a.length()) { a.getInt(it) } }
        val e = cells("eligible"); val r = cells("route")
        require(e.isNotEmpty() && e.size <= size && e.distinct().size == e.size && e.all { it in 0 until size })
        require(r.distinct().size == r.size && r.all { it in e } && (r.isEmpty() || r.first() == e.first()))
        require(r.zipWithNext().all { (a, b) -> adjacent(a, b) })
        phase = p; readyAt = j.getLong("readyAt").coerceAtLeast(0); paid = j.getBoolean("paid")
        eligible = e; route.addAll(r)
    }
}

class LargeFieldState {
    val fields = List(3) { LargeField(it) }
    var unlocked = 1
    var selected = 0
    var grain = 0L
    fun json() = JSONObject().put("unlocked", unlocked).put("selected", selected).put("grain", grain)
        .put("fields", JSONArray(fields.map { it.json() }))
    fun restore(j: JSONObject?) {
        if (j == null) return
        val u = j.getInt("unlocked"); require(u in 1..3)
        val restored = List(3) { i -> LargeField(i).apply { restore(j.getJSONArray("fields").getJSONObject(i)) } }
        unlocked = u; selected = j.optInt("selected").coerceIn(0, u - 1); grain = j.optLong("grain").coerceAtLeast(0)
        restored.forEachIndexed { i, f -> fields[i].restore(f.json()) }
    }
}
