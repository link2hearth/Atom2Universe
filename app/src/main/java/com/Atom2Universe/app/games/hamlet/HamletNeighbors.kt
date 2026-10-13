package com.Atom2Universe.app.games.hamlet

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class HamletNeighbor(val id: Int, val level: Int, val seed: Int, val shops: List<String>)
data class NeighborOrder(val neighbor: Int, val shop: Int, val type: String, val placed: Long,
                         var due: Long, var aid: Int = 0)
data class NeighborReward(val coins: Int, val xp: Int, val work: Int)

/** Local neighbours are deterministic scenery; only the player's orders belong to the save.
 * Ordering is free and repeatable. Work accelerates an individual order, never all 36 at once. */
class HamletNeighbors(private val g: HamletGame) {
    private val pending = LinkedHashMap<Int, NeighborOrder>()
    private val seen = HashSet<Int>()
    private val claims = IntArray(3)
    val orders: List<NeighborOrder> get() = pending.values.map { it.copy() }
    val visited: Set<Int> get() = seen.toSet()
    private fun key(neighbor: Int, shop: Int) = neighbor * 1000 + shop
    fun reset() { pending.clear(); seen.clear(); claims.fill(0) }
    fun copyFrom(other: HamletNeighbors) { reset(); other.pending.forEach { (key, value) -> pending[key] = value.copy() }; seen.addAll(other.seen); other.claims.copyInto(claims) }
    fun profile(id: Int) = villages.getOrNull(id)
    fun shopType(neighbor: Int, shop: Int) = profile(neighbor)?.shops?.getOrNull(shop - FIRST_SHOP)
    fun order(neighbor: Int, shop: Int) = pending[key(neighbor, shop)]?.copy()
    fun left(neighbor: Int, shop: Int): Long = pending[key(neighbor, shop)]?.let { (it.due - g.now()).coerceAtLeast(0) } ?: 0
    fun readyCount() = pending.values.count { it.due <= g.now() }
    fun activeCount(neighbor: Int) = pending.values.count { it.neighbor == neighbor }
    fun duration(neighbor: Int, shop: Int): Int = if (shopType(neighbor, shop) == null) 0 else 120 + ((neighbor * 3 + shop - FIRST_SHOP) % 5) * 60
    fun reward(neighbor: Int, shop: Int) = NeighborReward(12 + duration(neighbor, shop) / 15, 3 + duration(neighbor, shop) / 60, 6)

    fun milestoneTarget(index: Int): Int = when (index) {
        0 -> intArrayOf(5, 12).getOrElse(claims[0]) { 0 }
        1, 2 -> 3 + claims[index] * 12
        else -> 0
    }
    fun milestoneProgress(index: Int): Long = when (index) {
        0 -> seen.size.toLong()
        1 -> g.stats["ordered"] ?: 0
        2 -> g.stats["pickedUp"] ?: 0
        else -> 0
    }
    fun claimMilestone(index: Int): Boolean {
        val target = milestoneTarget(index)
        if (target <= 0 || milestoneProgress(index) < target) return false
        val rank = claims[index]++
        g.workSeconds = (g.workSeconds + 30).coerceAtMost(7200)
        g.reward(null, 50 + rank.coerceAtMost(18) * 25, 10 + rank.coerceAtMost(18) * 5)
        changed(); return true
    }

    fun visit(neighbor: Int): Boolean {
        if (profile(neighbor) == null) return false
        if (seen.add(neighbor)) { g.stat("visited"); g.chronicle.record(HamletChronicle.Kind.VISIT, subject = "neighbor:$neighbor"); changed() }
        return true
    }

    fun placeOrder(neighbor: Int, shop: Int): Boolean {
        val type = shopType(neighbor, shop) ?: return false
        val key = key(neighbor, shop)
        if (pending.containsKey(key)) return false
        val now = g.now()
        if (now < 0 || now > Long.MAX_VALUE - MAX_DURATION_MS) return false
        pending[key] = NeighborOrder(neighbor, shop, type, now, now + duration(neighbor, shop) * 1000L)
        g.stat("ordered"); g.chronicle.record(HamletChronicle.Kind.ORDER, subject = "neighbor:$neighbor"); changed(); return true
    }

    fun collect(neighbor: Int, shop: Int): NeighborReward? {
        val key = key(neighbor, shop)
        val order = pending[key] ?: return null
        if (order.due > g.now()) return null
        // Remove before callbacks, so a listener cannot collect the same parcel twice.
        pending.remove(key)
        val reward = reward(neighbor, shop)
        g.workSeconds = (g.workSeconds + reward.work).coerceAtMost(7200)
        g.stat("pickedUp")
        g.chronicle.record(HamletChronicle.Kind.PICKUP, subject = "neighbor:$neighbor", amount = reward.coins.toLong())
        g.reward(null, reward.coins, reward.xp)
        changed(); return reward
    }

    /** The bank is earned through existing village activities; it is never a premium currency. */
    fun useWork(neighbor: Int, shop: Int): Int {
        val order = pending[key(neighbor, shop)] ?: return 0
        val left = left(neighbor, shop)
        if (left <= 0 || g.workSeconds <= 0) return 0
        val seconds = minOf(300, g.workSeconds, ((left + 999) / 1000).toInt())
        g.workSeconds -= seconds
        order.due = maxOf(order.placed, g.now(), order.due - seconds * 1000L)
        g.stat("work_used", seconds.toLong()); changed(); return seconds
    }

    fun helpTool(neighbor: Int, shop: Int): Int {
        val order = pending[key(neighbor, shop)] ?: return -1
        if (left(neighbor, shop) <= 0) return -1
        return (neighbor * 7 + shop + order.aid * 5) % 3
    }

    /** Six sorting/packing gestures save 30 seconds. No charge, waiting period or daily quota. */
    fun help(neighbor: Int, shop: Int, tool: Int): Boolean {
        val order = pending[key(neighbor, shop)] ?: return false
        if (tool !in 0..2 || tool != helpTool(neighbor, shop)) return false
        order.aid++
        if (order.aid == 6) {
            order.aid = 0
            order.due = maxOf(order.placed, g.now(), order.due - 30_000)
            g.stat("neighbor_helped")
        }
        changed(); return true
    }

    fun encode(): JSONObject = JSONObject().put("claims", JSONArray(claims.toList())).put("visited", JSONArray(seen.sorted())).put("orders", JSONArray().also { rows ->
        pending.values.forEach { rows.put(JSONObject().put("neighbor", it.neighbor).put("shop", it.shop)
            .put("type", it.type).put("placed", it.placed).put("due", it.due).put("aid", it.aid)) }
    })

    fun decode(json: JSONObject?) {
        reset()
        json ?: return
        json.optJSONArray("claims")?.let { rows -> for (i in claims.indices) claims[i] = rows.optInt(i).coerceIn(0, if (i == 0) 2 else 100_000) }
        json.optJSONArray("visited")?.let { rows -> for (i in 0 until minOf(rows.length(), villages.size))
            rows.optInt(i, -1).takeIf { profile(it) != null }?.let(seen::add) }
        json.optJSONArray("orders")?.let { rows ->
            for (i in 0 until minOf(rows.length(), villages.size * 3)) {
                val row = rows.optJSONObject(i) ?: continue
                val neighbor = row.optInt("neighbor", -1); val shop = row.optInt("shop", -1)
                val type = shopType(neighbor, shop) ?: continue
                if (type != row.optString("type")) continue
                val placed = row.optLong("placed", -1); val due = row.optLong("due", -1)
                if (placed < 0 || placed > Long.MAX_VALUE - MAX_DURATION_MS || due < placed || due - placed > duration(neighbor, shop) * 1000L) continue
                val key = key(neighbor, shop)
                if (pending.containsKey(key)) continue
                pending[key] = NeighborOrder(neighbor, shop, type, placed, due, row.optInt("aid").coerceIn(0, 5))
            }
        }
    }

    /** A fresh independent scene each visit: editing or advancing it can never touch home. */
    fun generate(neighbor: Int): HamletGame? {
        val profile = profile(neighbor) ?: return null
        val scene = HamletGame(g::now, Random(profile.seed))
        scene.hall = 2 + neighbor / 4
        scene.xp = Hamlet.xpTable[profile.level - 1].toLong()
        scene.giftT = g.now(); scene.created = g.now()
        val r = Random(profile.seed)
        fun put(id: Int, type: String, x: Int, y: Int): Obj? {
            if (!scene.canPlace(type, x, y, false)) return null
            val item = Obj(id, type, x, y).apply { t0 = g.now(); next = Long.MAX_VALUE }
            scene.objs[id] = item
            for (yy in y until y + item.d) for (xx in x until x + item.w) scene.occ[scene.idx(xx, yy)] = id
            return item
        }
        put(1, "hall", 30, 21)
        val origin = scene.realmOrigin(); val end = origin + scene.realmSize() - 1
        for (y in 26..end - 2) for (x in 31..32) if (scene.objAt(x, y) == null) scene.paths[scene.idx(x, y)] = if (neighbor % 3 == 0) 2 else 1
        for (y in intArrayOf(29, 35, 41)) for (x in origin + 2..end - 2) if (scene.objAt(x, y) == null) scene.paths[scene.idx(x, y)] = 1
        val shopPlaces = arrayOf(24 to 30, 35 to 30, (if (neighbor % 2 == 0) 24 else 35) to 36)
        profile.shops.forEachIndexed { i, type -> put(FIRST_SHOP + i, type, shopPlaces[i].first, shopPlaces[i].second) }
        val homes = Hamlet.defs.filter { it.cat == Cat.HOME && it.lvl <= profile.level && it.gems == 0 && it.w <= 3 && it.d <= 3 }
        val homePlaces = arrayOf(24 to 25, 35 to 25, 20 to 30, 40 to 30, 20 to 36, 40 to 36, 24 to 42, 35 to 42)
        homePlaces.forEachIndexed { i, (x, y) -> put(10 + i, homes.random(r).id, x, y) }
        var id = 200
        for (y in 36..39 step 2) for (x in (if (neighbor % 2 == 0) 35 else 24)..(if (neighbor % 2 == 0) 38 else 27) step 2) {
            put(id++, "field", x, y)?.let { field ->
                val crop = Hamlet.crops.filter { !it.magic && it.lvl <= profile.level }.random(r)
                field.crop = crop.id; field.t0 = g.now() - (crop.time * (0.3 + r.nextDouble() * 0.9) * 1000).toLong()
            }
        }
        val decorations = arrayOf("treeRound", "treeOval", "treePine", "bush", "flowersRed", "flowersYellow", "bench", "rock")
        repeat(65 + neighbor * 3) { put(id++, decorations.random(r), r.nextInt(origin + 1, end), r.nextInt(origin + 1, end)) }
        scene.nextId = id
        syncScene(neighbor, scene)
        scene.bump()
        return scene
    }

    fun syncScene(neighbor: Int, scene: HamletGame) {
        profile(neighbor)?.shops?.forEachIndexed { i, _ ->
            val shop = scene.objs[FIRST_SHOP + i] ?: return@forEachIndexed
            val pending = pending[key(neighbor, shop.id)]
            // Reuse native shop badges and animation without mutating the player's own shops.
            shop.order = if (pending == null) Order(neighbor.toString()) else Order(neighbor.toString(), pending.due - shop.def.job * 1000L)
        }
    }
    private fun changed() { g.listener?.onChanged() }

    companion object {
        const val FIRST_SHOP = 100
        private const val MAX_DURATION_MS = 360_000L
        val villages = listOf(
            HamletNeighbor(0, 5, 1703, listOf("bakery", "florist", "herbalist")),
            HamletNeighbor(1, 8, 1867, listOf("cheesemaker", "farmmarket", "tailor")),
            HamletNeighbor(2, 12, 2017, listOf("toymaker", "chandler", "weaver")),
            HamletNeighbor(3, 17, 2203, listOf("tavern", "bookshop", "potter")),
            HamletNeighbor(4, 23, 2377, listOf("cider", "windmill", "barn")),
            HamletNeighbor(5, 28, 2539, listOf("greenhouse", "apothecary", "petshop")),
            HamletNeighbor(6, 14, 2707, listOf("stables", "blacksmith", "armory")),
            HamletNeighbor(7, 19, 2879, listOf("library", "school", "cartographer")),
            HamletNeighbor(8, 25, 3049, listOf("musichall", "painter", "clockmaker")),
            HamletNeighbor(9, 30, 3221, listOf("teahouse", "spicemarket", "brewery")),
            HamletNeighbor(10, 35, 3389, listOf("academy", "observatory", "glassworks")),
            HamletNeighbor(11, 10, 3559, listOf("adventureInn", "adventureForge", "adventureStore")),
        )
    }
}
