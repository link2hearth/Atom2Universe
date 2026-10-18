package com.Atom2Universe.app.games.caves.world

import com.Atom2Universe.app.games.caves.node.FarmItems
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.farm.FarmCrop
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * The whole "coins -> seeds -> dishes" economy, tuned in this one place.
 *
 * Mobs drop coins, villagers sell seeds for coins, deeper seeds grow stronger vegetables, and a dish
 * heals by a fixed amount that follows the depth its best vegetable is sold from. Everything scales with
 * [MineralProgression.scale] (1.18 per stage), like mob health and player health, so the ratio between a
 * seed's price and one mob's loot stays put for the whole game.
 *
 * Species are handled by their *position* in the unlock order (0 = first sold), which only needs the
 * stage number. [cropAt] / [positionOf] bridge to the farm's crop indices.
 */
internal object CoinEconomy {
    // ── Mob loot ──────────────────────────────────────────────────────────────
    /** Coins per mob at stage 0 (a roll in this range, scaled by stage): mean 2.5. */
    const val LOOT_MIN = 2.0
    const val LOOT_MAX = 3.0
    const val BOSS_MULTIPLIER = 5
    /** A monster-site chest holds this many mobs' worth of coins (inclusive range). */
    val CHEST_MOBS = 2..4

    // ── Seed prices and unlocks ───────────────────────────────────────────────
    /** The first species sold, all at the base price and available from the start. */
    const val STARTER_SPECIES = 3
    const val BASE_PRICE = 10.0
    /** Each species after the starters costs this much more than the previous one. */
    const val PRICE_GROWTH = 1.5
    /**
     * Stages between two unlocks. ln(1.5) / ln(1.18) = 2.45: one step of price growth is exactly the
     * growth of a mob's loot over that many stages, so a seed stays worth about the same number of mobs.
     */
    const val STAGES_PER_SPECIES = 2.45

    // ── Seeds that are still found for free ───────────────────────────────────
    /** Wild plants only give seeds of the first species of the unlock order. */
    const val WILD_SEED_SPECIES = 5
    const val WILD_SEED_CHANCE = .12f
    /** Chance for a monster-site chest to hold one seed, from a species unlocked by its stage. */
    const val SITE_SEED_CHANCE = .18

    /** Stock per period for each seed offer at a villager: lots, and how many seeds come in a lot. */
    const val SEED_LOT = 1

    // ── Dishes ────────────────────────────────────────────────────────────────
    /** Recipes that only exist as JSON crafts in the assets (the crafts folder), listed here by hand. */
    private val jsonCrafts: Map<Short, List<Short>> = mapOf(
        F.SALAD to listOf(FarmItems.produce(3), FarmItems.produce(8)),
        F.TRAVEL_RATION to listOf(F.BREAD, FarmItems.produce(4), F.CHEESE),
        F.FLOUR to listOf(FarmItems.produce(0))
    )

    // ── Loot ──────────────────────────────────────────────────────────────────
    private fun growth(stage: Int): Double = MineralProgression.scale(stage.coerceIn(0, MineralProgression.LAST_STAGE)).toDouble()
    fun lootMin(stage: Int): Long = (LOOT_MIN * growth(stage)).roundToLong().coerceAtLeast(1L)
    fun lootMax(stage: Int): Long = (LOOT_MAX * growth(stage)).roundToLong().coerceAtLeast(lootMin(stage))
    fun lootMean(stage: Int): Double = (lootMin(stage) + lootMax(stage)) / 2.0
    /** Coins dropped by one mob killed at [stage]. Always fits an Int: the top stage is a few thousand. */
    fun loot(stage: Int, boss: Boolean, rng: Random): Int {
        val lo = lootMin(stage);val hi = lootMax(stage)
        val roll = lo + rng.nextLong(hi - lo + 1)
        return (roll * if (boss) BOSS_MULTIPLIER else 1).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
    fun chestCoins(stage: Int, rng: Random): Int =
        (lootMean(stage) * (CHEST_MOBS.first + rng.nextInt(CHEST_MOBS.last - CHEST_MOBS.first + 1)))
            .roundToLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()

    // ── Seeds ─────────────────────────────────────────────────────────────────
    /** Price in coins of the seed at unlock [position]. Half-even rounding gives 10, 15, 22, 34, 51... */
    fun price(position: Int): Int {
        val step = (position - (STARTER_SPECIES - 1)).coerceAtLeast(0)
        return kotlin.math.round(BASE_PRICE * PRICE_GROWTH.pow(step)).coerceAtMost(Int.MAX_VALUE.toDouble()).toInt()
    }
    /** Stage from which the seed at [position] is sold (the deepest stage the player has reached). */
    fun unlockStage(position: Int): Int {
        val step = position - (STARTER_SPECIES - 1)
        return if (step <= 0) 0 else (step * STAGES_PER_SPECIES).roundToLong().toInt().coerceAtMost(MineralProgression.LAST_STAGE)
    }
    /**
     * The order seeds are unlocked in, by what the kitchen needs first: the wheat field, then the burger
     * garden (tomato, potato, carrot, onion), the pot herbs, and last the fruit.
     */
    private val unlockOrder = listOf(FarmCrop.WHEAT, FarmCrop.RADISH, FarmCrop.LETTUCE, FarmCrop.TOMATO, FarmCrop.POTATO,
        FarmCrop.CARROT, FarmCrop.ONION, FarmCrop.PEAS, FarmCrop.CORN, FarmCrop.PEPPER, FarmCrop.LEEK, FarmCrop.CHILI,
        FarmCrop.EGGPLANT, FarmCrop.BROCCOLI, FarmCrop.CAULIFLOWER, FarmCrop.STRAWBERRY, FarmCrop.RASPBERRY,
        FarmCrop.BLUEBERRY, FarmCrop.PINEAPPLE)
    /** Farm crop indices in unlock order. */
    val order: List<Int> by lazy { unlockOrder.map { crop -> FarmItems.crops.indexOf(crop).also { require(it >= 0) } } }
    fun cropAt(position: Int): Int = order[position]
    fun positionOf(crop: Int): Int = order.indexOf(crop)
    fun isUnlocked(position: Int, deepestStage: Int) = unlockStage(position) <= deepestStage
    fun unlockedCrops(stage: Int): List<Int> = order.filterIndexed { position, _ -> isUnlocked(position, stage) }
    fun wildSeedCrops(): List<Int> = order.take(WILD_SEED_SPECIES)
    fun cropStage(crop: Int): Int = unlockStage(positionOf(crop))

    /** The seed dropped by a wild plant, or null: only the first species, [WILD_SEED_CHANCE] of the time. */
    fun wildSeed(rng: Random): Short? {
        if (rng.nextFloat() >= WILD_SEED_CHANCE) return null
        val crops = wildSeedCrops()
        return FarmItems.seed(crops[rng.nextInt(crops.size)])
    }
    /** A seed for a monster-site chest of [stage], or null. Same random source as the rest of the chest. */
    fun siteSeed(stage: Int, rng: Random): Short? {
        if (rng.nextDouble() >= SITE_SEED_CHANCE) return null
        val crops = unlockedCrops(stage)
        return FarmItems.seed(crops[rng.nextInt(crops.size)])
    }

    // ── Dishes ────────────────────────────────────────────────────────────────
    private val recipesOf: Map<Short, List<Collection<Short>>> by lazy {
        val map = HashMap<Short, MutableList<Collection<Short>>>()
        fun add(output: Short, inputs: Collection<Short>) { map.getOrPut(output) { mutableListOf() }.add(inputs) }
        for (r in FrontierWorkshops.legacyRecipes) for (out in r.output.keys) add(out, r.input.keys)
        for (c in KitchenRecipes.crafts) add(c.result, c.inputIds - c.tools.toSet())
        for ((out, inputs) in jsonCrafts) add(out, inputs)
        map
    }
    private val dishStages = HashMap<Short, Int>()
    /** Stage from which the best vegetable of [id] is sold; 0 for anything without a vegetable in it. */
    @Synchronized fun dishStage(id: Short): Int = dishStages.getOrPut(id) { stageOf(id, HashSet()) }
    private fun stageOf(id: Short, seen: MutableSet<Short>): Int {
        FarmItems.produceCrop(id)?.let { return cropStage(it) }
        if (!seen.add(id)) return 0
        // Each recipe is as deep as its deepest vegetable; a dish with several recipes is as deep as the easiest one.
        val best = recipesOf[id]?.minOfOrNull { inputs -> inputs.maxOfOrNull { stageOf(it, seen) } ?: 0 } ?: 0
        seen.remove(id)
        return best
    }
    /** Average hit points one piece of armor gives at [stage]: what a standard dish heals. */
    fun gearHp(stage: Int): Double = ((50.0 * growth(stage) - 25.0) * AVERAGE_SLOT_WEIGHT).coerceAtLeast(1.0)
    /** The four armor slots share 100 % of the equipment health: 20 + 40 + 25 + 15 %. */
    const val AVERAGE_SLOT_WEIGHT = .25
    /** Fixed healing of a dish: [weight] times the health of a piece of armor of its best vegetable's stage. */
    fun healing(id: Short, weight: Float): Int {
        if (weight <= 0f) return 0
        return (gearHp(dishStage(id)) * weight).roundToLong().coerceIn(1L, Int.MAX_VALUE.toLong()).toInt()
    }
}
