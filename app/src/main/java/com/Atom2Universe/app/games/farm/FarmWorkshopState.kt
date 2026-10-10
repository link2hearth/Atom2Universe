package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

data class FarmRecipeCrop(val crop: FarmCrop, val count: Int)
data class FarmRecipeProduct(val product: FarmAnimalProduct, val count: Int)

/** Only ordinary crops: crafting must never silently downgrade a valuable harvest. */
enum class FarmRecipe(val label: Int, val minutes: Int, val sale: Int,
                      val crops: List<FarmRecipeCrop> = emptyList(), val products: List<FarmRecipeProduct> = emptyList()) {
    PICKLES(R.string.farm_recipe_pickles, 15, 48, listOf(FarmRecipeCrop(FarmCrop.RADISH, 3))),
    SOUP(R.string.farm_recipe_soup, 30, 100, listOf(FarmRecipeCrop(FarmCrop.PEAS, 2), FarmRecipeCrop(FarmCrop.LETTUCE, 1))),
    EGG_SALAD(R.string.farm_recipe_egg_salad, 60, 44, listOf(FarmRecipeCrop(FarmCrop.LETTUCE, 1)), listOf(FarmRecipeProduct(FarmAnimalProduct.EGG, 1))),
    TRUFFLE_PAN(R.string.farm_recipe_truffle_pan, 120, 690, listOf(FarmRecipeCrop(FarmCrop.CORN, 2)), listOf(FarmRecipeProduct(FarmAnimalProduct.TRUFFLE, 1))),
    CHEESE(R.string.farm_recipe_cheese, 240, 3_200, products = listOf(FarmRecipeProduct(FarmAnimalProduct.MILK, 2))),
    SCARF(R.string.farm_recipe_scarf, 360, 640, products = listOf(FarmRecipeProduct(FarmAnimalProduct.WOOL, 2)));
    val durationMillis get() = minutes * 60_000L
    val ingredientValue get() = crops.sumOf { it.crop.sale * it.count } + products.sumOf { it.product.sale * it.count }
    fun accessible(parcels: Int, availableProducts: Set<FarmAnimalProduct>) =
        crops.all { it.crop.rank <= parcels } && products.all { it.product in availableProducts }
}

data class FarmPreparation(val id: Long, val recipe: FarmRecipe, val readyAt: Long)

/** Three independent worktops. Ready jobs never expire, and collection never starts another job. */
class FarmWorkshopState {
    companion object {
        const val SLOTS = 3
        const val STOCK_CAPACITY = 999_999
        const val INTRO_MILLIS = 60_000L
        private const val MAX_ID = 1_000_000_000_000L
        fun fromJson(json: JSONObject?): FarmWorkshopState {
            val state = FarmWorkshopState()
            if (json == null) return state
            val stock = json.optJSONObject("stock")
            val known = json.optJSONObject("known")
            FarmRecipe.entries.forEach { recipe ->
                state.stock[recipe.ordinal] = (stock?.optInt(recipe.name) ?: 0).coerceIn(0, STOCK_CAPACITY)
                if (known?.optBoolean(recipe.name) == true || state.count(recipe) > 0) state.discovered += recipe
            }
            val jobs = json.optJSONArray("jobs")
            val ids = mutableSetOf<Long>()
            for (i in 0 until (jobs?.length() ?: 0).coerceAtMost(SLOTS)) {
                val raw = jobs?.optJSONObject(i) ?: continue
                val recipe = FarmRecipe.entries.firstOrNull { it.name == raw.optString("recipe") } ?: continue
                val id = raw.optLong("id")
                val ready = raw.optLong("readyAt", -1)
                if (id !in 1..MAX_ID || ready !in 0..100_000_000_000_000L || !ids.add(id)) continue
                state.pending += FarmPreparation(id, recipe, ready)
                state.discovered += recipe
            }
            state.nextId = maxOf((json.optLong("nextId", 1)).coerceIn(1, MAX_ID), (ids.maxOrNull() ?: 0) + 1)
            state.revision = maxOf(json.optLong("revision", 1).coerceIn(1, MAX_ID), state.nextId)
            state.introUsed = json.optBoolean("introUsed") || state.pending.isNotEmpty() || state.totalStock() > 0
            state.lastTime = json.optLong("lastTime").coerceIn(0, 100_000_000_000_000L)
            return state
        }
    }
    private val discovered = mutableSetOf<FarmRecipe>()
    private val pending = mutableListOf<FarmPreparation>()
    private val stock = IntArray(FarmRecipe.entries.size)
    private var nextId = 1L
    private var lastTime = 0L
    var revision = 1L
        private set
    var introUsed = false
        private set
    val jobs: List<FarmPreparation> get() = pending.toList()
    val recipes: List<FarmRecipe> get() = FarmRecipe.entries.filter { knows(it) }
    fun knows(recipe: FarmRecipe) = recipe in discovered
    fun count(recipe: FarmRecipe) = stock[recipe.ordinal]
    fun totalStock() = stock.sum()
    fun stockValue() = FarmRecipe.entries.sumOf { count(it).toLong() * it.sale }
    fun readyCount(clock: Long) = pending.count { it.readyAt <= maxOf(clock, lastTime) }
    fun discover(parcels: Int, products: Set<FarmAnimalProduct>): Boolean {
        val before = discovered.size
        FarmRecipe.entries.filter { it.accessible(parcels, products) }.forEach { discovered += it }
        return before != discovered.size
    }
    internal fun start(recipe: FarmRecipe, clock: Long, expectedRevision: Long): Boolean {
        if (!knows(recipe) || pending.size >= SLOTS || revision != expectedRevision || clock < 0) return false
        val now = maxOf(clock, lastTime)
        pending += FarmPreparation(nextId++, recipe, now + if (!introUsed) INTRO_MILLIS else recipe.durationMillis)
        introUsed = true; lastTime = now; revision++
        return true
    }
    internal fun collect(clock: Long): Int {
        val now = maxOf(clock, lastTime)
        var collected = 0
        val iterator = pending.iterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            if (job.readyAt > now || count(job.recipe) >= STOCK_CAPACITY) continue
            stock[job.recipe.ordinal]++; iterator.remove(); collected++
        }
        if (collected > 0) { lastTime = now; revision++ }
        return collected
    }
    internal fun take(recipe: FarmRecipe, quantity: Int): Int {
        val taken = quantity.coerceIn(0, count(recipe))
        stock[recipe.ordinal] -= taken; return taken
    }
    internal fun cheatSkip(millis: Long) {
        for (i in pending.indices) pending[i] = pending[i].copy(readyAt = (pending[i].readyAt - millis).coerceAtLeast(0))
    }
    fun toJson() = JSONObject().put("nextId", nextId).put("revision", revision).put("lastTime", lastTime).put("introUsed", introUsed)
        .put("known", JSONObject().apply { recipes.forEach { put(it.name, true) } })
        .put("stock", JSONObject().apply { FarmRecipe.entries.forEach { put(it.name, count(it)) } })
        .put("jobs", JSONArray().apply { pending.forEach { put(JSONObject().put("id", it.id).put("recipe", it.recipe.name).put("readyAt", it.readyAt)) } })
}
