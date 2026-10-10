package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

enum class FarmVillager(val label: Int, val role: Int, val requests: List<Int>, val thanks: Int) {
    LUCIE(R.string.farm_villager_lucie, R.string.farm_villager_lucie_role,
        listOf(R.string.farm_lucie_request_1, R.string.farm_lucie_request_2, R.string.farm_lucie_request_3),
        R.string.farm_lucie_thanks),
    MALO(R.string.farm_villager_malo, R.string.farm_villager_malo_role,
        listOf(R.string.farm_malo_request_1, R.string.farm_malo_request_2, R.string.farm_malo_request_3),
        R.string.farm_malo_thanks),
    IRIS(R.string.farm_villager_iris, R.string.farm_villager_iris_role,
        listOf(R.string.farm_iris_request_1, R.string.farm_iris_request_2, R.string.farm_iris_request_3),
        R.string.farm_iris_thanks)
}

data class FarmOrderItem(val crop: FarmCrop, val count: Int)

/** No deadline or daily reset: only delivering or replacing changes an order. */
data class FarmVillageOrder(val id: Long, val villager: FarmVillager, val story: Int,
                            val items: List<FarmOrderItem>, val preparation: FarmRecipe? = null) {
    val minimumReward: Long get() = rewardFor(items.sumOf { it.crop.sale.toLong() * it.count } + (preparation?.sale ?: 0))

    companion object {
        /** Quality is paid in full, then the village adds a modest 25% delivery premium. */
        const val BONUS_PERCENT = 25
        fun rewardFor(value: Long): Long = value + value * BONUS_PERCENT / 100
    }
}

data class FarmOrderDelivery(val villager: FarmVillager, val coins: Long, val friendship: Int)

/**
 * The board is independent of the clock. Small quantities keep a request within one planting;
 * rotating through the unlocked ladder gives old crops a purpose alongside the newest seed.
 */
class FarmVillageState {
    private val board = arrayOfNulls<FarmVillageOrder>(FarmVillager.entries.size)
    private val delivered = IntArray(FarmVillager.entries.size)
    private val offered = IntArray(FarmVillager.entries.size)
    private var nextId = 1L

    val orders: List<FarmVillageOrder> get() = board.filterNotNull()
    val deliveries: Int get() = delivered.sum()
    fun friendship(villager: FarmVillager): Int = delivered[villager.ordinal]
    fun find(id: Long): FarmVillageOrder? = board.firstOrNull { it?.id == id }

    /** Fill a new/legacy board; a valid existing request is never refreshed on opening the game. */
    fun ensureOrders(unlockedParcels: Int, recipes: List<FarmRecipe> = emptyList(), trees: Set<FarmCrop> = emptySet()) {
        val available = availableCrops(unlockedParcels) + FarmCrop.trees.filter { it in trees }
        FarmVillager.entries.forEach { villager ->
            val old = board[villager.ordinal]
            if (old == null || old.items.any { it.crop !in available })
                board[villager.ordinal] = generate(villager, available, old, recipes)
        }
    }

    /** Stale button callbacks cannot replace or complete a newer request. */
    fun replace(id: Long, unlockedParcels: Int, recipes: List<FarmRecipe> = emptyList(), trees: Set<FarmCrop> = emptySet()): Boolean {
        val old = find(id) ?: return false
        board[old.villager.ordinal] = generate(old.villager, availableCrops(unlockedParcels) + FarmCrop.trees.filter { it in trees }, old, recipes)
        return true
    }

    internal fun complete(id: Long, unlockedParcels: Int, recipes: List<FarmRecipe> = emptyList(), trees: Set<FarmCrop> = emptySet()): Int? {
        val old = find(id) ?: return null
        val index = old.villager.ordinal
        delivered[index] = (delivered[index] + 1).coerceAtMost(MAX_DELIVERIES)
        board[index] = generate(old.villager, availableCrops(unlockedParcels) + FarmCrop.trees.filter { it in trees }, old, recipes)
        return delivered[index]
    }

    private fun availableCrops(unlockedParcels: Int) =
        FarmCrop.ladder.take(unlockedParcels.coerceIn(1, FarmCrop.ladder.size))

    private fun generate(villager: FarmVillager, available: List<FarmCrop>, old: FarmVillageOrder?, recipes: List<FarmRecipe>): FarmVillageOrder {
        val id = nextId++
        val story = ((old?.story?.plus(1) ?: villager.ordinal) % villager.requests.size)
        val turn = offered[villager.ordinal]++
        if (recipes.isNotEmpty() && turn % 4 == villager.ordinal + 1 &&
            board.none { it?.villager != villager && it?.preparation != null }) {
            val recipe = recipes[(turn / 4 + villager.ordinal) % recipes.size]
            return FarmVillageOrder(id, villager, story, emptyList(), recipe)
        }
        val fruits = available.filter { it.tree }
        if (fruits.isNotEmpty() && turn % 4 == 0 && turn > 0 &&
            board.none { it?.villager != villager && it?.items?.any { item -> item.crop.tree } == true })
            return FarmVillageOrder(id, villager, story, listOf(FarmOrderItem(fruits[(turn / 4 + villager.ordinal) % fruits.size], 3)))
        val vegetables = available.filterNot { it.tree }
        var index = ((id - 1) % vegetables.size).toInt()
        if (vegetables.size > 1 && vegetables[index] == old?.items?.firstOrNull()?.crop)
            index = (index + 1) % vegetables.size
        val mixed = vegetables.size >= 3 && id % 3 == 0L
        val items = mutableListOf(FarmOrderItem(vegetables[index], 3 + (id % 4).toInt()))
        if (mixed) items += FarmOrderItem(vegetables[(index + maxOf(1, vegetables.size / 2)) % vegetables.size], 2)
        return FarmVillageOrder(id, villager, story, items)
    }

    fun reset(unlockedParcels: Int) {
        board.fill(null); delivered.fill(0); offered.fill(0); nextId = 1L
        ensureOrders(unlockedParcels)
    }

    fun toJson(): JSONObject {
        val relations = JSONObject()
        FarmVillager.entries.forEach { relations.put(it.name, friendship(it)) }
        val requests = JSONArray()
        orders.forEach { order ->
            val items = JSONArray()
            order.items.forEach { items.put(JSONObject().put("crop", it.crop.name).put("count", it.count)) }
            requests.put(JSONObject().put("id", order.id).put("villager", order.villager.name)
                .put("story", order.story).put("items", items).apply {
                    order.preparation?.let { put("preparation", it.name) }
                })
        }
        return JSONObject().put("nextId", nextId).put("friendship", relations).put("orders", requests)
            .put("offered", JSONObject().apply { FarmVillager.entries.forEach { put(it.name, offered[it.ordinal]) } })
    }

    companion object {
        private const val MAX_DELIVERIES = 1_000_000

        /** A damaged optional board must never invalidate the rest of a farm save. */
        fun fromJson(json: JSONObject?): FarmVillageState {
            if (json == null) return FarmVillageState()
            return runCatching {
                val result = FarmVillageState()
                val relations = json.getJSONObject("friendship")
                FarmVillager.entries.forEach {
                    result.delivered[it.ordinal] = relations.optInt(it.name).coerceIn(0, MAX_DELIVERIES)
                    result.offered[it.ordinal] = (json.optJSONObject("offered")?.optInt(it.name) ?: 0).coerceIn(0, 1_000_000_000)
                }
                val requests = json.getJSONArray("orders")
                require(requests.length() == FarmVillager.entries.size)
                val ids = mutableSetOf<Long>()
                for (i in 0 until requests.length()) {
                    val raw = requests.getJSONObject(i)
                    val id = raw.getLong("id")
                    require(id in 1..1_000_000_000_000L && ids.add(id))
                    val villager = FarmVillager.valueOf(raw.getString("villager"))
                    require(result.board[villager.ordinal] == null)
                    val story = raw.getInt("story")
                    require(story in villager.requests.indices)
                    val rawItems = raw.getJSONArray("items")
                    val preparation = if (raw.has("preparation")) FarmRecipe.valueOf(raw.getString("preparation")) else null
                    require(if (preparation == null) rawItems.length() in 1..2 else rawItems.length() == 0)
                    val items = List(rawItems.length()) { j ->
                        val item = rawItems.getJSONObject(j)
                        val crop = FarmCrop.valueOf(item.getString("crop"))
                        val count = item.getInt("count")
                        require((crop.rank > 0 || crop.tree) && count in 1..6)
                        FarmOrderItem(crop, count)
                    }
                    require(items.map { it.crop }.distinct().size == items.size)
                    result.board[villager.ordinal] = FarmVillageOrder(id, villager, story, items, preparation)
                }
                result.nextId = json.getLong("nextId")
                require(result.nextId > ids.max() && result.nextId <= 1_000_000_000_001L)
                require(result.orders.count { it.preparation != null } <= 1)
                require(result.orders.count { it.items.any { item -> item.crop.tree } } <= 1)
                result
            }.getOrElse { FarmVillageState() }
        }
    }
}

/** The entire withdrawal is planned before changing either the inventory or the purse. */
internal data class FarmOrderWithdrawal(val stacks: List<FarmHarvestStack>, val reward: Long, val preparation: FarmRecipe? = null) {
    companion object {
        fun plan(order: FarmVillageOrder, produce: Array<IntArray>, workshop: FarmWorkshopState? = null): FarmOrderWithdrawal? {
            if (order.preparation != null) {
                if (order.items.isNotEmpty() || workshop == null || workshop.count(order.preparation) < 1) return null
                return FarmOrderWithdrawal(emptyList(), order.minimumReward, order.preparation)
            }
            val stacks = mutableListOf<FarmHarvestStack>()
            for (item in order.items) {
                var needed = item.count
                for (quality in FarmCropQuality.entries) {
                    val count = minOf(needed, produce[item.crop.ordinal][quality.ordinal])
                    if (count > 0) stacks += FarmHarvestStack(item.crop, quality, count)
                    needed -= count
                    if (needed == 0) break
                }
                if (needed > 0) return null
            }
            val value = stacks.sumOf { it.crop.sale.toLong() * it.quality.multiplier * it.count }
            return FarmOrderWithdrawal(stacks, FarmVillageOrder.rewardFor(value))
        }
    }
}
