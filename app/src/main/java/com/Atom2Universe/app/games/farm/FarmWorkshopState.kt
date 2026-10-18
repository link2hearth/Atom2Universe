package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

data class FarmRecipeCrop(val crop: FarmCrop, val count: Int)
data class FarmRecipeProduct(val product: FarmAnimalProduct, val count: Int)
data class FarmRecipeFlower(val flower: FarmFlower, val count: Int)
data class FarmRecipeMeat(val meat: FarmMeat, val count: Int)
data class FarmRecipePreparation(val recipe: FarmRecipe, val count: Int)
enum class FarmRecipeCategory(val label: Int) {
    GARDEN(R.string.farm_workshop_category_garden), FARM_KITCHEN(R.string.farm_workshop_category_farm),
    CRAFTS(R.string.farm_workshop_category_crafts)
}

/** Only ordinary crops: crafting must never silently downgrade a valuable harvest. */
enum class FarmRecipe(val label: Int, val minutes: Int, val sale: Int,
                      val crops: List<FarmRecipeCrop> = emptyList(), val products: List<FarmRecipeProduct> = emptyList(),
                      val flowers: List<FarmRecipeFlower> = emptyList(), val meats: List<FarmRecipeMeat> = emptyList(),
                      val preparations: List<FarmRecipePreparation> = emptyList(), val dishesNeeded: Int = 0) {
    PICKLES(R.string.farm_recipe_pickles, 15, 48, listOf(FarmRecipeCrop(FarmCrop.RADISH, 3))),
    SOUP(R.string.farm_recipe_soup, 30, 100, listOf(FarmRecipeCrop(FarmCrop.PEAS, 2), FarmRecipeCrop(FarmCrop.LETTUCE, 1))),
    EGG_SALAD(R.string.farm_recipe_egg_salad, 60, 44, listOf(FarmRecipeCrop(FarmCrop.LETTUCE, 1)), listOf(FarmRecipeProduct(FarmAnimalProduct.EGG, 1))),
    TRUFFLE_PAN(R.string.farm_recipe_truffle_pan, 120, 690, listOf(FarmRecipeCrop(FarmCrop.CORN, 2)), listOf(FarmRecipeProduct(FarmAnimalProduct.TRUFFLE, 1))),
    CHEESE(R.string.farm_recipe_cheese, 240, 3_200, products = listOf(FarmRecipeProduct(FarmAnimalProduct.MILK, 2))),
    SCARF(R.string.farm_recipe_scarf, 360, 640, products = listOf(FarmRecipeProduct(FarmAnimalProduct.WOOL, 2))),
    APPLE_COMPOTE(R.string.farm_recipe_apple_compote, 30, 48, listOf(FarmRecipeCrop(FarmCrop.APPLE, 3))),
    PEAR_SYRUP(R.string.farm_recipe_pear_syrup, 60, 80, listOf(FarmRecipeCrop(FarmCrop.PEAR, 3))),
    CHERRY_JAM(R.string.farm_recipe_cherry_jam, 120, 128, listOf(FarmRecipeCrop(FarmCrop.CHERRY, 3))),
    GARDEN_BOUQUET(R.string.farm_recipe_garden_bouquet, 60, 32, flowers = listOf(
        FarmRecipeFlower(FarmFlower.DAISY_WHITE, 1), FarmRecipeFlower(FarmFlower.TULIP_RED, 1), FarmRecipeFlower(FarmFlower.IRIS_BLUE, 1))),
    POULTRY_PEAS(R.string.farm_recipe_poultry_peas, 45, 72, listOf(FarmRecipeCrop(FarmCrop.PEAS, 1)),
        meats = listOf(FarmRecipeMeat(FarmMeat.POULTRY, 1))),
    MUTTON_STEW(R.string.farm_recipe_mutton_stew, 120, 1_000,
        listOf(FarmRecipeCrop(FarmCrop.PEAS, 2), FarmRecipeCrop(FarmCrop.ZUCCHINI, 1)), meats = listOf(FarmRecipeMeat(FarmMeat.MUTTON, 1))),
    PORK_SKEWERS(R.string.farm_recipe_pork_skewers, 90, 2_300,
        listOf(FarmRecipeCrop(FarmCrop.CORN, 1), FarmRecipeCrop(FarmCrop.CHILI, 1)), meats = listOf(FarmRecipeMeat(FarmMeat.PORK, 1))),
    BEEF_ROAST(R.string.farm_recipe_beef_roast, 180, 4_200,
        listOf(FarmRecipeCrop(FarmCrop.ZUCCHINI, 1), FarmRecipeCrop(FarmCrop.CORN, 1)), meats = listOf(FarmRecipeMeat(FarmMeat.BEEF, 1))),
    CORN_FRITTERS(R.string.farm_recipe_corn_fritters, 45, 180, listOf(FarmRecipeCrop(FarmCrop.CORN, 2)),
        listOf(FarmRecipeProduct(FarmAnimalProduct.EGG, 1))),
    STUFFED_ZUCCHINI(R.string.farm_recipe_stuffed_zucchini, 60, 300,
        listOf(FarmRecipeCrop(FarmCrop.ZUCCHINI, 2), FarmRecipeCrop(FarmCrop.PEAS, 1))),
    STRAWBERRY_JAM(R.string.farm_recipe_strawberry_jam, 90, 480, listOf(FarmRecipeCrop(FarmCrop.STRAWBERRY, 3))),
    PUMPKIN_SOUP(R.string.farm_recipe_pumpkin_soup, 120, 2_800, listOf(FarmRecipeCrop(FarmCrop.PUMPKIN, 1)),
        listOf(FarmRecipeProduct(FarmAnimalProduct.MILK, 1))),
    CORNBREAD(R.string.farm_recipe_cornbread, 4, 180, listOf(FarmRecipeCrop(FarmCrop.CORN, 2)),
        listOf(FarmRecipeProduct(FarmAnimalProduct.EGG, 1)), dishesNeeded = 2),
    EGG_SANDWICH(R.string.farm_recipe_egg_sandwich, 2, 300, listOf(FarmRecipeCrop(FarmCrop.LETTUCE, 1)),
        listOf(FarmRecipeProduct(FarmAnimalProduct.EGG, 1)), preparations = listOf(FarmRecipePreparation(CORNBREAD, 1)), dishesNeeded = 3),
    CHEESE_SANDWICH(R.string.farm_recipe_cheese_sandwich, 3, 4_600, listOf(FarmRecipeCrop(FarmCrop.LETTUCE, 1)),
        preparations = listOf(FarmRecipePreparation(CORNBREAD, 1), FarmRecipePreparation(CHEESE, 1)), dishesNeeded = 5),
    BERRY_PANCAKES(R.string.farm_recipe_berry_pancakes, 5, 420,
        listOf(FarmRecipeCrop(FarmCrop.CORN, 1), FarmRecipeCrop(FarmCrop.STRAWBERRY, 2)),
        listOf(FarmRecipeProduct(FarmAnimalProduct.EGG, 1)), dishesNeeded = 4),
    TOMATO_SOUP(R.string.farm_recipe_tomato_soup, 30, 13_000, listOf(FarmRecipeCrop(FarmCrop.TOMATO, 3)),
        listOf(FarmRecipeProduct(FarmAnimalProduct.MILK, 1)), dishesNeeded = 6),
    CAULIFLOWER_GRATIN(R.string.farm_recipe_cauliflower_gratin, 20, 2_700, listOf(FarmRecipeCrop(FarmCrop.CAULIFLOWER, 2)),
        listOf(FarmRecipeProduct(FarmAnimalProduct.MILK, 1)), dishesNeeded = 8);
    val durationMillis get() = minutes * 60_000L
    val category get() = when {
        this == SCARF || flowers.isNotEmpty() -> FarmRecipeCategory.CRAFTS
        products.isNotEmpty() || meats.isNotEmpty() -> FarmRecipeCategory.FARM_KITCHEN
        else -> FarmRecipeCategory.GARDEN
    }
    val food get() = category != FarmRecipeCategory.CRAFTS
    val ingredientValue get() = crops.sumOf { it.crop.sale * it.count } + products.sumOf { it.product.sale * it.count } +
        flowers.sumOf { it.flower.sale * it.count } + meats.sumOf { it.meat.sale * it.count } +
        preparations.sumOf { it.recipe.sale * it.count }
    fun accessible(parcels: Int, availableProducts: Set<FarmAnimalProduct>, trees: Set<FarmCrop> = emptySet(),
                   availableFlowers: Set<FarmFlower> = emptySet(), availableMeats: Set<FarmMeat> = emptySet()) =
        crops.all { if (it.crop.tree) it.crop in trees else it.crop.rank in 1..parcels } && products.all { it.product in availableProducts } &&
            flowers.all { it.flower in availableFlowers } && meats.all { it.meat in availableMeats }
}

data class FarmPreparation(val id: Long, val recipe: FarmRecipe, val readyAt: Long, val handsOn: Boolean = false)

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
            val cooked = json.optJSONObject("cooked")
            val practiced = json.optJSONObject("practiced")
            val favorites = json.optJSONObject("favorites")
            FarmRecipe.entries.forEach { recipe ->
                state.stock[recipe.ordinal] = (stock?.optInt(recipe.name) ?: 0).coerceIn(0, STOCK_CAPACITY)
                if (known?.optBoolean(recipe.name) == true || state.count(recipe) > 0) state.discovered += recipe
                if (recipe.food) {
                    // Old pantry creations prove at least that many completed dishes; sold history is unknown.
                    state.cooked[recipe.ordinal] = maxOf(state.count(recipe), cooked?.optInt(recipe.name) ?: 0).coerceIn(0, STOCK_CAPACITY)
                    if (state.cooked(recipe) > 0) state.discovered += recipe
                    state.practiced[recipe.ordinal] = (practiced?.optInt(recipe.name) ?: 0).coerceIn(0, state.cooked(recipe))
                    if (favorites?.optBoolean(recipe.name) == true && state.knows(recipe)) state.favorites += recipe
                }
            }
            val jobs = json.optJSONArray("jobs")
            val ids = mutableSetOf<Long>()
            for (i in 0 until (jobs?.length() ?: 0).coerceAtMost(SLOTS)) {
                val raw = jobs?.optJSONObject(i) ?: continue
                val recipe = FarmRecipe.entries.firstOrNull { it.name == raw.optString("recipe") } ?: continue
                val id = raw.optLong("id")
                val ready = raw.optLong("readyAt", -1)
                if (id !in 1..MAX_ID || ready !in 0..100_000_000_000_000L || !ids.add(id)) continue
                state.pending += FarmPreparation(id, recipe, ready, raw.optBoolean("handsOn") && recipe.food)
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
    private val cooked = IntArray(FarmRecipe.entries.size)
    private val practiced = IntArray(FarmRecipe.entries.size)
    private val favorites = mutableSetOf<FarmRecipe>()
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
    fun cooked(recipe: FarmRecipe) = cooked[recipe.ordinal]
    fun practiced(recipe: FarmRecipe) = practiced[recipe.ordinal]
    fun favorite(recipe: FarmRecipe) = recipe in favorites
    val distinctDishes get() = FarmRecipe.entries.count { it.food && cooked(it) > 0 }
    val totalDishes get() = cooked.sum()
    internal fun toggleFavorite(recipe: FarmRecipe): Boolean {
        if (!recipe.food || !knows(recipe)) return false
        if (!favorites.remove(recipe)) favorites += recipe
        return true
    }
    fun totalStock() = stock.sum()
    fun stockValue() = FarmRecipe.entries.sumOf { count(it).toLong() * it.sale }
    fun readyCount(clock: Long) = pending.count { it.readyAt <= maxOf(clock, lastTime) }
    fun discover(parcels: Int, products: Set<FarmAnimalProduct>, trees: Set<FarmCrop> = emptySet(), flowers: Set<FarmFlower> = emptySet(),
                 meats: Set<FarmMeat> = emptySet()): Boolean {
        val before = discovered.size
        // Prepared ingredients refer to earlier entries: reveal their dependent page in this pass.
        FarmRecipe.entries.forEach { recipe ->
            if (recipe.dishesNeeded <= distinctDishes && recipe.preparations.all { knows(it.recipe) } &&
                recipe.accessible(parcels, products, trees, flowers, meats)) discovered += recipe
        }
        return before != discovered.size
    }
    internal fun start(recipe: FarmRecipe, clock: Long, expectedRevision: Long, handsOn: Boolean = false): Boolean {
        if (!knows(recipe) || pending.size >= SLOTS || revision != expectedRevision || clock < 0) return false
        val now = maxOf(clock, lastTime)
        val practiced = handsOn && recipe.food
        val duration = if (!introUsed) INTRO_MILLIS else recipe.durationMillis * (if (practiced) 80 else 100) / 100
        pending += FarmPreparation(nextId++, recipe, now + duration, practiced)
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
            if (job.recipe.food) {
                cooked[job.recipe.ordinal] = (cooked(job.recipe) + 1).coerceAtMost(STOCK_CAPACITY)
                if (job.handsOn) practiced[job.recipe.ordinal] = (practiced(job.recipe) + 1).coerceAtMost(cooked(job.recipe))
            }
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
        .put("cooked", JSONObject().apply { FarmRecipe.entries.filter { it.food }.forEach { put(it.name, cooked(it)) } })
        .put("practiced", JSONObject().apply { FarmRecipe.entries.filter { it.food }.forEach { put(it.name, practiced(it)) } })
        .put("favorites", JSONObject().apply { favorites.forEach { put(it.name, true) } })
        .put("jobs", JSONArray().apply { pending.forEach { put(JSONObject().put("id", it.id).put("recipe", it.recipe.name).put("readyAt", it.readyAt).put("handsOn", it.handsOn)) } })
}
