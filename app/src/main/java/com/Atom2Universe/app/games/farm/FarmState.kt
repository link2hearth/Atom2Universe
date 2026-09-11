package com.Atom2Universe.app.games.farm

import android.content.SharedPreferences
import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * The seed ladder: one new crop per parcel bought, [rank] 1..12 in that order.
 *
 * Ranks come in pairs. The odd rank of a pair is the quick crop you harvest while you are here, the
 * even rank is the overnight crop - so every second purchase widens what you can do in a session,
 * and every other one widens what you can leave running.
 *
 * Values follow `net gain = u * sqrt(hours)`, and u is shared by both crops of a pair, then grows
 * 1.85x at the next pair. Both halves matter. The square root means waiting twice as long never
 * pays twice as much. Holding u flat inside a pair is what stops the overnight crop - which is
 * always the newer, shinier seed - from quietly out-earning the quick crop next to it: three quick
 * harvests beat one overnight harvest by about 1.8x, two of them still edge past it. Every extra
 * visit pays, which was exactly what the old table did not do.
 *
 * Trees keep rank 0: the orchard is parked until its own balancing pass.
 *
 * Declaration order is NOT the ladder. It must stay as it is: the legacy v1 save migration indexes
 * `oldSeconds` by ordinal.
 */
enum class FarmCrop(val label: Int, val sheet: String, val row: Int, val rank: Int, val cost: Int,
                    val sale: Int, val seconds: Int, val tree: Boolean = false) {
    WHEAT(R.string.farm_wheat, "garden_wheat_radish_lettuce_zucchini_v1.png", 0, 4, 8, 45, 8 * 3600),
    RADISH(R.string.farm_radish, "garden_wheat_radish_lettuce_zucchini_v1.png", 2, 1, 2, 12, 2 * 3600),
    LETTUCE(R.string.farm_lettuce, "garden_wheat_radish_lettuce_zucchini_v1.png", 4, 2, 3, 20, 6 * 3600),
    ZUCCHINI(R.string.farm_zucchini, "garden_wheat_radish_lettuce_zucchini_v1.png", 6, 6, 16, 92, 10 * 3600),
    STRAWBERRY(R.string.farm_strawberry, "garden_fruit_vegetables_variants_v1.png", 0, 7, 21, 120, 5 * 3600),
    PUMPKIN(R.string.farm_pumpkin, "garden_fruit_vegetables_variants_v1.png", 2, 12, 155, 900, 24 * 3600),
    EGGPLANT(R.string.farm_eggplant, "garden_fruit_vegetables_variants_v1.png", 4, 8, 32, 186, 12 * 3600),
    BLUEBERRY(R.string.farm_blueberry, "garden_fruit_vegetables_variants_v1.png", 6, 11, 90, 520, 8 * 3600),
    CORN(R.string.farm_corn, "garden_corn_pepper_peas_cauliflower_clean.png", 0, 5, 10, 58, 4 * 3600),
    PEPPER(R.string.farm_pepper, "garden_corn_pepper_peas_cauliflower_clean.png", 2, 9, 42, 243, 6 * 3600),
    PEAS(R.string.farm_peas, "garden_corn_pepper_peas_cauliflower_clean.png", 4, 3, 5, 27, 3 * 3600),
    CAULIFLOWER(R.string.farm_cauliflower, "garden_corn_pepper_peas_cauliflower_clean.png", 6, 10, 68, 396, 16 * 3600),
    APPLE(R.string.farm_apple, "garden_fruit_trees_v1.png", 0, 0, 20, 12, 48 * 3600, true),
    PEAR(R.string.farm_pear, "garden_fruit_trees_v1.png", 2, 0, 25, 15, 72 * 3600, true),
    CHERRY(R.string.farm_cherry, "garden_fruit_trees_v1.png", 4, 0, 30, 18, 96 * 3600, true);

    /** Net coins per hour, the number the shop shows so the trade-off is readable before buying. */
    val coinsPerHour: Float get() = (sale - cost) * 3600f / seconds
    /**
     * Manure points one planting of this crop asks for. Tying it to the rung is what keeps the loop
     * honest across the whole game: a flat price would cover a starting coop and then cover every
     * single planting once four herds are full, turning the bonus into a permanent doubling.
     */
    val manureCost: Int get() = rank
    companion object {
        /** The ladder, in buying order. Trees are absent while the orchard is parked. */
        val ladder = entries.filter { it.rank > 0 }.sortedBy { it.rank }
    }
}

enum class FarmLandUse(val label: Int) {
    CROPS(R.string.farm_use_crops), ORCHARD(R.string.farm_use_orchard)
}

data class FarmPlot(var crop: FarmCrop? = null, var planted: Long = 0, var watered: Boolean = false,
                    var variant: Int = 0, var established: Boolean = false, var debris: Int = 0,
                    var critical: Boolean = false, var rich: Boolean = false) {
    fun duration(): Int = if (established) 24 * 3600 else crop?.seconds ?: 0
    fun progress(now: Long): Float = if (crop == null || !watered) 0f else
        ((now - planted).coerceAtLeast(0).toDouble() / (duration() * 1000L)).toFloat().coerceIn(0f, 1f)
    fun stage(now: Long): Int = if (established) { if (progress(now) >= 1f) 4 else 3 }
        else (progress(now) * 4).toInt().coerceAtMost(4)
    fun remaining(now: Long): Int = kotlin.math.ceil((1.0 - progress(now)) * duration()).toInt()
}

data class FarmParcel(var unlocked: Boolean = false, var use: FarmLandUse = FarmLandUse.CROPS)

class FarmState(private val prefs: SharedPreferences) {
    // A deliberately thin purse: five radishes, and the pile under the bush matters on day one.
    var coins = STARTING_COINS
        private set
    var harvests = 0
        private set
    var bushBonusDay = -1L
        private set
    var selected = FarmCrop.RADISH
    var wateringLevel = 0
        private set
    var harvestLevel = 0
        private set
    var fertilizerLevel = 0
        private set
    val parcels = List(FarmLayout.lands.size) { FarmParcel(unlocked = it == 0) }
    val plots = List(FarmLayout.cellCount) { FarmPlot(debris = 1 + (it * 7 % 3)) }
    val seeds = IntArray(FarmCrop.entries.size)
    val livestock = LivestockState()
    val largeFields = LargeFieldState()

    init {
        // Parse into temporary objects: invalid saves must never partially overwrite the farm.
        runCatching {
            val raw = prefs.getString("state", null) ?: return@runCatching
            val json = JSONObject(raw)
            val version = json.getInt("version")
            require(version in 1..5)
            val savedLandCount = if (version >= 2) json.getJSONArray("parcels").length() else 6
            require(savedLandCount in 1..parcels.size)
            val saved = json.getJSONArray("plots")
            require(saved.length() == when (version) {
                1 -> 12
                2 -> 72
                else -> FarmLayout.cells(savedLandCount - 1).last + 1
            })
            val restored = List(saved.length()) { index ->
                val p = saved.getJSONObject(index)
                val crop = p.optString("crop").takeIf { it.isNotEmpty() }?.let { FarmCrop.valueOf(it) }
                FarmPlot(crop, p.optLong("planted").coerceAtLeast(0), p.optBoolean("watered"),
                    p.optInt("variant").coerceIn(0, 1), p.optBoolean("established") && crop?.tree == true,
                    if (crop == null) p.optInt("debris").coerceIn(0, 3) else 0,
                    p.optBoolean("critical") && crop != null, p.optBoolean("rich") && crop != null)
            }
            val selection = FarmCrop.valueOf(json.getString("selected"))
            val restoredCoins = json.getLong("coins").coerceAtLeast(0)
            val restoredHarvests = json.optInt("harvests").coerceAtLeast(0)
            val restoredWatering = if (version >= 4) json.getInt("wateringLevel").coerceIn(0, 2) else 0
            val restoredHarvestLevel = json.optInt("harvestLevel").coerceIn(0, 2)
            val restoredFertilizer = json.optInt("fertilizerLevel").coerceIn(0, 2)
            val lands = if (version >= 2) {
                val array = json.getJSONArray("parcels")
                require(version != 2 || array.length() == 6)
                List(parcels.size) { i -> if (i >= array.length()) FarmParcel() else array.getJSONObject(i).let {
                    FarmParcel(it.getBoolean("unlocked"), FarmLandUse.valueOf(it.getString("use")))
                } }.also { require(it[0].unlocked) }
            } else List(parcels.size) { FarmParcel(it == 0) }
            val inventory = if (version >= 2) {
                val inv = json.getJSONObject("seeds")
                IntArray(seeds.size) { inv.optInt(FarmCrop.entries[it].name).coerceIn(0, 9999) }
            } else IntArray(seeds.size)
            // Validate the herd before applying either part of the save.
            val restoredHerd = LivestockState().apply { restore(json.optJSONObject("livestock")) }
            if (version >= 2) {
                restored.forEachIndexed { i, p ->
                    val parcel = lands[if (version == 2) i / 12 else FarmLayout.parcelOf(i)]
                    require(p.crop == null || (parcel.unlocked && p.crop!!.tree == (parcel.use == FarmLandUse.ORCHARD)))
                }
                restored.forEachIndexed { i, p ->
                    val target = if (version == 2) FarmLayout.cells(i / 12).first + i % 12 else i
                    copyPlot(p, plots[target])
                }
            } else {
                // Keep all legacy plants, separating trees from vegetables without charging land.
                var vegetable = 0
                var tree = FarmLayout.cells(1).first
                val now = System.currentTimeMillis()
                val oldSeconds = intArrayOf(45, 30, 60, 90, 120, 240, 150, 210, 100, 180, 75, 180, 300, 360, 420)
                restored.forEach { p ->
                    p.crop?.let { crop ->
                        val oldDuration = oldSeconds[crop.ordinal] * 1000L
                        val elapsed = (now - p.planted).coerceAtLeast(0)
                        val progress = ((elapsed + if (p.watered) oldDuration / 4 else 0).toDouble() / oldDuration).coerceIn(0.0, 1.0)
                        p.watered = true
                        p.planted = now - (progress * p.duration() * 1000).toLong()
                    }
                    if (p.crop?.tree == true) {
                        lands[1].unlocked = true; lands[1].use = FarmLandUse.ORCHARD
                        copyPlot(p, plots[tree++])
                    } else copyPlot(p, plots[vegetable++])
                }
            }
            lands.forEachIndexed { i, p -> parcels[i].apply { unlocked = p.unlocked; use = p.use } }
            inventory.copyInto(seeds)
            coins = restoredCoins; harvests = restoredHarvests; selected = selection; wateringLevel = restoredWatering
            harvestLevel = restoredHarvestLevel; fertilizerLevel = restoredFertilizer
            livestock.restore(restoredHerd.toJson())
            runCatching { largeFields.restore(json.optJSONObject("largeFields")) }
            bushBonusDay = json.optLong("bushBonusDay", -1)
            if (version < 5) migrateToRebalancedEconomy()
        }
    }
    /**
     * Version 5 rescaled every price and every sale roughly eightfold, so a purse saved under the old
     * numbers would have been worth nothing. The field cycle counter did not exist either: a farm that
     * already keeps animals must not lose access to its own herd, so it is credited the two cycles the
     * livestock milestone now asks for.
     */
    private fun migrateToRebalancedEconomy() {
        coins *= 8
        if (livestock.unlocked > 0 && largeFields.cycles < LIVESTOCK_FIELD_CYCLES)
            largeFields.cycles = LIVESTOCK_FIELD_CYCLES
        save()
    }

    /** Crops are sold parcel by parcel: owning N parcels puts the first N rungs of the ladder on sale. */
    val unlockedParcels get() = parcels.count { it.unlocked }
    fun cropUnlocked(crop: FarmCrop) = crop.rank in 1..unlockedParcels
    /** Nothing gates the fields and the pens today; both now open on a visible milestone. */
    fun fieldsUnlocked() = harvests >= FIELDS_HARVESTS
    fun livestockUnlocked() = harvests >= LIVESTOCK_HARVESTS && largeFields.cycles >= LIVESTOCK_FIELD_CYCLES
    fun regionUnlocked(region: FarmRegion) = when (region) {
        FarmRegion.HOME -> true
        FarmRegion.FIELDS -> fieldsUnlocked()
        FarmRegion.LIVESTOCK -> livestockUnlocked()
    }

    /**
     * Half a harvest of the best crop you can grow: five coins next to the first radishes, hundreds
     * once the pumpkins are in. A gift that helps on day one and never becomes the main income.
     */
    fun bushBonusAmount(): Long = maxOf(5L,
        (FarmCrop.ladder.filter { cropUnlocked(it) }.maxOfOrNull { it.sale } ?: FarmCrop.RADISH.sale) / 2L)

    /** One free pile of coins a day, tucked behind the bush above parcel 1. */
    fun bushBonusReady(now: Long = System.currentTimeMillis()) = bushBonusDay != now / 86_400_000L
    /** Returns what the pile was worth, zero when today's pile is already taken. */
    fun claimBushBonus(): Long {
        if (!bushBonusReady()) return 0
        val gained = bushBonusAmount()
        coins += gained; bushBonusDay = System.currentTimeMillis() / 86_400_000L; save(); return gained
    }

    /** Each pen asks for harvests and for a working herd of the previous animal, not just coins. */
    fun livestockRequirementMet(kind: LivestockKind): Boolean {
        if (!livestockUnlocked()) return false
        if (harvests < kind.harvestsNeeded) return false
        val previous = LivestockKind.entries.getOrNull(kind.ordinal - 1) ?: return true
        return livestock.animals.count { it.kind == previous && it.adult } >= LIVESTOCK_HERD_NEEDED
    }
    fun unlockLivestock(kind: LivestockKind): Boolean {
        if (!livestockRequirementMet(kind) || coins < kind.landPrice || !livestock.unlock(kind)) return false
        coins -= kind.landPrice; save(); return true
    }
    fun buyAnimal(kind: LivestockKind, male: Boolean): Boolean {
        val now = System.currentTimeMillis()
        advanceLivestock(now)
        if (coins < kind.price || !livestock.buy(kind, male, now)) return false
        coins -= kind.price; save(); return true
    }
    fun sellAnimal(id: Long): Int {
        val now = System.currentTimeMillis()
        advanceLivestock(now)
        val value = livestock.sell(id, now)
        if (value > 0) { coins += value; save() }
        return value
    }
    fun advanceLivestock(now: Long = System.currentTimeMillis()) {
        if (livestock.advance(now)) save()
    }
    fun unlockField(index: Int): Boolean {
        if (index != largeFields.unlocked || index !in 1..2) return false
        val f = largeFields.fields[index]
        if (coins < f.price) return false
        coins -= f.price; largeFields.unlocked++; largeFields.selected = index; save(); return true
    }
    fun startField(): Boolean {
        val f = largeFields.fields[largeFields.selected]
        if (f.phase == 2) return false
        if (!f.paid) {
            if (coins < f.seedCost) return false
            coins -= f.seedCost; f.paid = true
        }
        f.begin()
        save(); return true
    }
    fun finishField(): Boolean {
        val f = largeFields.fields[largeFields.selected]
        if (!f.paid || f.phase == 2) return false
        when (f.phase) {
            0 -> { f.eligible = f.painted.copyOf(); f.phase = 1; f.beginPass() }
            1 -> { f.eligible = f.painted.copyOf(); f.phase = 2; f.readyAt = System.currentTimeMillis() + 6 * 3600_000L; f.beginPass() }
            3 -> {
                val worked = f.painted.indices.count { f.painted[it] && f.eligible[it] } / (LargeField.SUB * LargeField.SUB)
                largeFields.grain += worked * 2L + if (f.complete) f.size / 2 else 0
                largeFields.cycles++
                f.phase = 0; f.paid = false; f.eligible = BooleanArray(f.maskCols * f.maskRows) { true }; f.readyAt = 0
                f.beginCycle()
            }
        }
        save(); return true
    }
    fun advanceFields() {
        var changed = false
        largeFields.fields.forEach { if (it.phase == 2 && System.currentTimeMillis() >= it.readyAt) { it.phase = 3; changed = true } }
        if (changed) save()
    }
    /**
     * A field never grows while the vegetable garden multiplies by eight hundred, so the silo scales
     * the price of the grain instead of its quantity - the feed costs keep their meaning that way.
     */
    fun grainPrice(): Long = 2L * largeFields.siloMultiplier
    fun siloUpgradeCost(level: Int): Long = when (level) { 1 -> 30_000L; 2 -> 300_000L; else -> 2_500_000L }
    fun upgradeSilo(): Boolean {
        if (largeFields.silo >= 3) return false
        val cost = siloUpgradeCost(largeFields.silo + 1)
        if (coins < cost) return false
        coins -= cost; largeFields.silo++; save(); return true
    }
    /** Selling ten at a time was fine at ten grains and absurd at five thousand. */
    fun sellGrain(): Long {
        val sold = largeFields.grain
        if (sold <= 0) return 0
        largeFields.grain = 0; coins += sold * grainPrice(); save(); return sold * grainPrice()
    }
    fun feedYoung(kind: LivestockKind): Boolean {
        val now = System.currentTimeMillis()
        advanceLivestock(now)
        val young = livestock.animals.filter { it.kind == kind && !it.adult && it.boostUntil <= now }
        val cost = young.size * (kind.ordinal + 1) * 5L
        if (young.isEmpty() || largeFields.grain < cost) return false
        young.forEach {
            val remaining = it.adultAt - now
            it.adultAt -= minOf(12 * 3600_000L, remaining / 2)
            it.boostUntil = minOf(it.adultAt, now + 12 * 3600_000L)
        }
        largeFields.grain -= cost; save(); return true
    }
    private fun copyPlot(from: FarmPlot, to: FarmPlot) {
        to.crop = from.crop; to.planted = from.planted; to.watered = from.watered
        to.variant = from.variant; to.established = from.established; to.debris = from.debris
        to.critical = from.critical; to.rich = from.rich
    }
    fun unlockCost(index: Int): Int = FarmLayout.lands[index].price
    /** Parcels are bought in order: skipping one would skip the seed it unlocks. */
    fun unlockAvailable(index: Int) = index in 1 until parcels.size &&
        !parcels[index].unlocked && parcels[index - 1].unlocked
    fun unlock(index: Int): Boolean {
        if (!unlockAvailable(index) || coins < unlockCost(index)) return false
        coins -= unlockCost(index); parcels[index].unlocked = true; save(); return true
    }
    fun changeUse(index: Int, use: FarmLandUse): Boolean {
        // The orchard is parked until the fruit trees get their own balancing pass.
        if (use == FarmLandUse.ORCHARD && parcels[index].use != FarmLandUse.ORCHARD) return false
        if (!parcels[index].unlocked || FarmLayout.cells(index).any { plots[it].crop != null }) return false
        parcels[index].use = use; save(); return true
    }
    fun buy(crop: FarmCrop, quantity: Int): Boolean {
        if (!cropUnlocked(crop)) return false
        if (quantity !in 1..MAX_SEED_BATCH || seeds[crop.ordinal] + quantity > 9999 ||
            coins < crop.cost.toLong() * quantity) return false
        coins -= crop.cost.toLong() * quantity; seeds[crop.ordinal] += quantity; selected = crop; save(); return true
    }
    /** How many seeds a "fill the parcels" purchase needs: every free cell that is ready to plant. */
    fun emptyCells(tree: Boolean): Int = plots.indices.count { i ->
        val parcel = parcels[FarmLayout.parcelOf(i)]
        parcel.unlocked && plots[i].debris == 0 && plots[i].crop == null &&
            tree == (parcel.use == FarmLandUse.ORCHARD)
    }
    fun clean(index: Int): Boolean {
        val p = plots[index]
        if (!parcels[FarmLayout.parcelOf(index)].unlocked || p.debris == 0) return false
        p.debris = 0; save(); return true
    }
    fun plant(index: Int, now: Long): Boolean {
        val p = plots[index]
        val land = parcels[FarmLayout.parcelOf(index)]
        if (!land.unlocked || p.debris != 0 || p.crop != null || seeds[selected.ordinal] == 0 ||
            selected.tree != (land.use == FarmLandUse.ORCHARD)) return false
        seeds[selected.ordinal]--
        p.crop = selected; p.planted = now; p.watered = false; p.established = false; p.critical = false
        p.variant = kotlin.random.Random.nextInt(2)
        // The pit empties by itself into whatever goes in the ground. No choice to make: the only
        // crops worth planting are the newest rungs anyway, which are also the ones worth enriching.
        advanceLivestock(now)
        p.rich = livestock.spendManure(selected.manureCost)
        save(); return true
    }
    private fun waterOne(index: Int, now: Long): Boolean {
        val p = plots[index]
        if (!parcels[FarmLayout.parcelOf(index)].unlocked || p.crop == null || p.watered || p.progress(now) >= 1f) return false
        p.watered = true; p.planted = now
        p.critical = kotlin.random.Random.nextInt(100) < criticalChance()
        return true
    }
    fun water(index: Int, now: Long): Boolean {
        val ok = waterOne(index, now); if (ok) save(); return ok
    }
    /** All watered at once so a whole row or parcel costs a single save, not one per cell. */
    fun waterMany(indices: List<Int>, now: Long): List<Int> {
        val watered = indices.filter { waterOne(it, now) }
        if (watered.isNotEmpty()) save()
        return watered
    }
    /** One cell, its row, or the whole parcel - shared by the watering can and the harvest basket. */
    private fun areaTargets(cell: Int, level: Int): List<Int> {
        val parcel = FarmLayout.parcelOf(cell)
        return when (level) {
            2 -> FarmLayout.cells(parcel).toList()
            1 -> {
                val spec = FarmLayout.lands[parcel]
                val row = FarmLayout.localCell(cell) / spec.columns
                FarmLayout.cells(parcel).filter { FarmLayout.localCell(it) / spec.columns == row }
            }
            else -> listOf(cell)
        }
    }
    fun wateringTargets(cell: Int): List<Int> = areaTargets(cell, wateringLevel)
    fun harvestTargets(cell: Int): List<Int> = areaTargets(cell, harvestLevel)
    fun wateringHitsNeeded(): Int = when (wateringLevel) { 0 -> 3; 1 -> 5; else -> 7 }
    fun wateringUpgradeCost(level: Int): Long = if (level == 1) 3_000L else 20_000L
    fun upgradeWatering(): Boolean {
        if (wateringLevel >= 2) return false
        val cost = wateringUpgradeCost(wateringLevel + 1)
        if (coins < cost) return false
        coins -= cost; wateringLevel++; save(); return true
    }
    /**
     * Late game, a full farm is 237 cells. Pulling each one by hand three times a day is not a game,
     * so the basket reaches a row, then a parcel - the same ladder as the watering can.
     */
    fun harvestUpgradeCost(level: Int): Long = if (level == 1) 60_000L else 400_000L
    fun upgradeHarvest(): Boolean {
        if (harvestLevel >= 2) return false
        val cost = harvestUpgradeCost(harvestLevel + 1)
        if (coins < cost) return false
        coins -= cost; harvestLevel++; save(); return true
    }
    /** Percent chance that a watered plant turns into a double-value harvest. */
    fun criticalChance(): Int = when (fertilizerLevel) { 0 -> 15; 1 -> 22; else -> 30 }
    fun fertilizerUpgradeCost(level: Int): Long = if (level == 1) 30_000L else 150_000L
    fun upgradeFertilizer(): Boolean {
        if (fertilizerLevel >= 2) return false
        val cost = fertilizerUpgradeCost(fertilizerLevel + 1)
        if (coins < cost) return false
        coins -= cost; fertilizerLevel++; save(); return true
    }
    private fun harvestOne(index: Int, now: Long): Int {
        val p = plots[index]
        val crop = p.crop ?: return 0
        if (!parcels[FarmLayout.parcelOf(index)].unlocked || p.progress(now) < 1f) return 0
        // Rich soil and a critical stack: a lucky plant on manure sells for four times its price.
        var amount = crop.sale
        if (p.critical) amount *= 2
        if (p.rich) amount *= 2
        coins += amount; harvests++
        if (crop.tree) { p.established = true; p.planted = now }
        else { p.crop = null; p.established = false }
        p.watered = false; p.critical = false; p.rich = false
        return amount
    }
    fun harvest(index: Int, now: Long): Int {
        val amount = harvestOne(index, now)
        if (amount > 0) save()
        return amount
    }
    /** Reaped together so a whole row costs a single save, mirroring [waterMany]. */
    fun harvestMany(indices: List<Int>, now: Long): Pair<Int, Int> {
        var total = 0; var count = 0
        indices.forEach { val gain = harvestOne(it, now); if (gain > 0) { total += gain; count++ } }
        if (count > 0) save()
        return total to count
    }
    fun clear(index: Int) {
        if (!parcels[FarmLayout.parcelOf(index)].unlocked) return
        // Only a mistake is refunded: once watered, the seed is spent.
        if (!plots[index].watered) coins += plots[index].crop?.cost ?: 0
        plots[index].apply { crop = null; planted = 0; watered = false; established = false; critical = false; rich = false }
        save()
    }
    /** Dev-only helpers for quick manual testing; never reachable from normal play. */
    /** Every stored value the farm owns, back to a brand new game - herd and fields included. */
    fun cheatReset() {
        coins = STARTING_COINS; harvests = 0
        wateringLevel = 0; harvestLevel = 0; fertilizerLevel = 0
        selected = FarmCrop.RADISH
        bushBonusDay = -1
        parcels.forEachIndexed { i, p -> p.unlocked = i == 0; p.use = FarmLandUse.CROPS }
        plots.forEachIndexed { i, p ->
            p.crop = null; p.planted = 0; p.watered = false; p.variant = 0; p.established = false
            p.critical = false; p.rich = false
            p.debris = 1 + (i * 7 % 3)
        }
        seeds.fill(0)
        livestock.reset()
        largeFields.reset()
        save()
    }
    fun cheatAddCoins(amount: Long) { coins += amount.coerceAtLeast(0); save() }
    fun cheatSkipTime(millis: Long) {
        plots.forEach { if (it.watered) it.planted -= millis }
        largeFields.fields.forEach { if (it.phase == 2) it.readyAt = (it.readyAt - millis).coerceAtLeast(0) }
        // The herd is on the same clock: skipping time must move births and growth too.
        livestock.cheatSkip(millis)
        advanceLivestock(); advanceFields(); save()
    }
    fun cheatCompleteGrowth() {
        val now = System.currentTimeMillis()
        plots.forEach { if (it.watered) it.planted = now - it.duration() * 1000L - 1000L }
        largeFields.fields.forEach { if (it.phase == 2) it.readyAt = now }
        livestock.cheatRush(now)
        advanceLivestock(now); advanceFields()
        save()
    }
    /** Clears parcel 1's debris and plants a mix of unwatered vegetables, ready to test watering. */
    /** Waters every thirsty plant across every unlocked parcel at once. */
    fun cheatWaterAll() { waterMany(plots.indices.toList(), System.currentTimeMillis()) }
    fun save() {
        val array = JSONArray()
        plots.forEach { p -> array.put(JSONObject().put("crop", p.crop?.name ?: "").put("planted", p.planted)
            .put("watered", p.watered).put("variant", p.variant).put("established", p.established).put("debris", p.debris)
            .put("critical", p.critical).put("rich", p.rich)) }
        val lands = JSONArray()
        parcels.forEach { lands.put(JSONObject().put("unlocked", it.unlocked).put("use", it.use.name)) }
        val inventory = JSONObject()
        FarmCrop.entries.forEach { inventory.put(it.name, seeds[it.ordinal]) }
        prefs.edit().putString("state", JSONObject().put("version", 5).put("coins", coins)
            .put("harvests", harvests).put("selected", selected.name).put("plots", array)
            .put("parcels", lands).put("seeds", inventory).put("wateringLevel", wateringLevel)
            .put("harvestLevel", harvestLevel).put("fertilizerLevel", fertilizerLevel)
            .put("largeFields", largeFields.json()).put("livestock", livestock.toJson())
            .put("bushBonusDay", bushBonusDay).toString()).apply()
    }

    companion object {
        /** Milestones. The fields and the pens are earned by playing, not found in a menu. */
        const val FIELDS_HARVESTS = 100
        const val LIVESTOCK_HARVESTS = 200
        const val LIVESTOCK_FIELD_CYCLES = 2
        /** Adults of the previous animal a pen asks for before the next one opens. */
        const val LIVESTOCK_HERD_NEEDED = 10
        /** Upper bound of a single seed purchase, so "fill the parcels" cannot overflow the stock. */
        const val MAX_SEED_BATCH = 999
        /** Five radishes. The farm has to be worked from the first minute. */
        const val STARTING_COINS = 10L
    }
}
