package com.Atom2Universe.app.games.farm

import android.content.SharedPreferences
import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

enum class FarmCrop(val label: Int, val sheet: String, val row: Int, val cost: Int, val sale: Int, val seconds: Int, val tree: Boolean = false) {
    WHEAT(R.string.farm_wheat, "garden_wheat_radish_lettuce_zucchini_v1.png", 0, 2, 5, 6 * 3600),
    RADISH(R.string.farm_radish, "garden_wheat_radish_lettuce_zucchini_v1.png", 2, 1, 3, 2 * 3600),
    LETTUCE(R.string.farm_lettuce, "garden_wheat_radish_lettuce_zucchini_v1.png", 4, 3, 7, 4 * 3600),
    ZUCCHINI(R.string.farm_zucchini, "garden_wheat_radish_lettuce_zucchini_v1.png", 6, 4, 9, 8 * 3600),
    STRAWBERRY(R.string.farm_strawberry, "garden_fruit_vegetables_variants_v1.png", 0, 5, 12, 12 * 3600),
    PUMPKIN(R.string.farm_pumpkin, "garden_fruit_vegetables_variants_v1.png", 2, 8, 20, 24 * 3600),
    EGGPLANT(R.string.farm_eggplant, "garden_fruit_vegetables_variants_v1.png", 4, 5, 13, 12 * 3600),
    BLUEBERRY(R.string.farm_blueberry, "garden_fruit_vegetables_variants_v1.png", 6, 7, 18, 18 * 3600),
    CORN(R.string.farm_corn, "garden_corn_pepper_peas_cauliflower_clean.png", 0, 4, 10, 10 * 3600),
    PEPPER(R.string.farm_pepper, "garden_corn_pepper_peas_cauliflower_clean.png", 2, 6, 15, 16 * 3600),
    PEAS(R.string.farm_peas, "garden_corn_pepper_peas_cauliflower_clean.png", 4, 3, 8, 6 * 3600),
    CAULIFLOWER(R.string.farm_cauliflower, "garden_corn_pepper_peas_cauliflower_clean.png", 6, 6, 16, 16 * 3600),
    APPLE(R.string.farm_apple, "garden_fruit_trees_v1.png", 0, 20, 12, 48 * 3600, true),
    PEAR(R.string.farm_pear, "garden_fruit_trees_v1.png", 2, 25, 15, 72 * 3600, true),
    CHERRY(R.string.farm_cherry, "garden_fruit_trees_v1.png", 4, 30, 18, 96 * 3600, true)
}

enum class FarmLandUse(val label: Int) {
    CROPS(R.string.farm_use_crops), ORCHARD(R.string.farm_use_orchard)
}

data class FarmPlot(var crop: FarmCrop? = null, var planted: Long = 0, var watered: Boolean = false,
                    var variant: Int = 0, var established: Boolean = false, var debris: Int = 0,
                    var critical: Boolean = false) {
    fun duration(): Int = if (established) 24 * 3600 else crop?.seconds ?: 0
    fun progress(now: Long): Float = if (crop == null || !watered) 0f else
        ((now - planted).coerceAtLeast(0).toDouble() / (duration() * 1000L)).toFloat().coerceIn(0f, 1f)
    fun stage(now: Long): Int = if (established) { if (progress(now) >= 1f) 4 else 3 }
        else (progress(now) * 4).toInt().coerceAtMost(4)
    fun remaining(now: Long): Int = kotlin.math.ceil((1.0 - progress(now)) * duration()).toInt()
}

data class FarmParcel(var unlocked: Boolean = false, var use: FarmLandUse = FarmLandUse.CROPS)

class FarmState(private val prefs: SharedPreferences) {
    var coins = 40L
        private set
    var harvests = 0
        private set
    var selected = FarmCrop.RADISH
    var wateringLevel = 0
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
            require(version in 1..4)
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
                    p.optBoolean("critical") && crop != null)
            }
            val selection = FarmCrop.valueOf(json.getString("selected"))
            val restoredCoins = json.getLong("coins").coerceAtLeast(0)
            val restoredHarvests = json.optInt("harvests").coerceAtLeast(0)
            val restoredWatering = if (version >= 4) json.getInt("wateringLevel").coerceIn(0, 2) else 0
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
            livestock.restore(restoredHerd.toJson())
            runCatching { largeFields.restore(json.optJSONObject("largeFields")) }
        }
    }

    fun unlockLivestock(kind: LivestockKind): Boolean {
        if (coins < kind.landPrice || !livestock.unlock(kind)) return false
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
    fun sellGrain(): Boolean {
        if (largeFields.grain < 10) return false
        largeFields.grain -= 10; coins += 10; save(); return true
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
        to.critical = from.critical
    }
    fun unlockCost(index: Int): Int = 60 * index * index * FarmLayout.lands[index].capacity / 12
    fun unlock(index: Int): Boolean {
        if (index !in parcels.indices || parcels[index].unlocked || coins < unlockCost(index)) return false
        coins -= unlockCost(index); parcels[index].unlocked = true; save(); return true
    }
    fun changeUse(index: Int, use: FarmLandUse): Boolean {
        if (!parcels[index].unlocked || FarmLayout.cells(index).any { plots[it].crop != null }) return false
        parcels[index].use = use; save(); return true
    }
    fun buy(crop: FarmCrop, quantity: Int): Boolean {
        if (quantity !in 1..12 || seeds[crop.ordinal] + quantity > 9999 || coins < crop.cost.toLong() * quantity) return false
        coins -= crop.cost.toLong() * quantity; seeds[crop.ordinal] += quantity; selected = crop; save(); return true
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
        save(); return true
    }
    private fun waterOne(index: Int, now: Long): Boolean {
        val p = plots[index]
        if (!parcels[FarmLayout.parcelOf(index)].unlocked || p.crop == null || p.watered || p.progress(now) >= 1f) return false
        p.watered = true; p.planted = now
        p.critical = kotlin.random.Random.nextInt(100) < 15
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
    fun wateringTargets(cell: Int): List<Int> {
        val parcel = FarmLayout.parcelOf(cell)
        return when (wateringLevel) {
            2 -> FarmLayout.cells(parcel).toList()
            1 -> {
                val spec = FarmLayout.lands[parcel]
                val row = FarmLayout.localCell(cell) / spec.columns
                FarmLayout.cells(parcel).filter { FarmLayout.localCell(it) / spec.columns == row }
            }
            else -> listOf(cell)
        }
    }
    fun wateringHitsNeeded(): Int = when (wateringLevel) { 0 -> 3; 1 -> 5; else -> 7 }
    fun wateringUpgradeCost(level: Int): Long = if (level == 1) 150L else 500L
    fun upgradeWatering(): Boolean {
        if (wateringLevel >= 2) return false
        val cost = wateringUpgradeCost(wateringLevel + 1)
        if (coins < cost) return false
        coins -= cost; wateringLevel++; save(); return true
    }
    fun harvest(index: Int, now: Long): Int {
        val p = plots[index]
        val crop = p.crop ?: return 0
        if (!parcels[FarmLayout.parcelOf(index)].unlocked || p.progress(now) < 1f) return 0
        val amount = if (p.critical) crop.sale * 2 else crop.sale
        coins += amount; harvests++
        if (crop.tree) { p.established = true; p.planted = now }
        else { p.crop = null; p.established = false }
        p.watered = false; p.critical = false
        save(); return amount
    }
    fun clear(index: Int) {
        if (!parcels[FarmLayout.parcelOf(index)].unlocked) return
        coins += plots[index].crop?.cost ?: 0
        plots[index].apply { crop = null; planted = 0; watered = false; established = false; critical = false }
        save()
    }
    /** Dev-only helpers for quick manual testing; never reachable from normal play. */
    fun cheatReset() {
        coins = 40; harvests = 0; wateringLevel = 0; selected = FarmCrop.RADISH
        parcels.forEachIndexed { i, p -> p.unlocked = i == 0; p.use = FarmLandUse.CROPS }
        plots.forEachIndexed { i, p ->
            p.crop = null; p.planted = 0; p.watered = false; p.variant = 0; p.established = false; p.critical = false
            p.debris = 1 + (i * 7 % 3)
        }
        seeds.fill(0)
        save()
    }
    fun cheatAddCoins(amount: Long) { coins += amount.coerceAtLeast(0); save() }
    fun cheatSkipTime(millis: Long) {
        plots.forEach { if (it.watered) it.planted -= millis }
        largeFields.fields.forEach { if (it.phase == 2) it.readyAt = (it.readyAt - millis).coerceAtLeast(0) }
        advanceFields(); save()
    }
    fun cheatCompleteGrowth() {
        val now = System.currentTimeMillis()
        plots.forEach { if (it.watered) it.planted = now - it.duration() * 1000L - 1000L }
        largeFields.fields.forEach { if (it.phase == 2) it.readyAt = now }
        advanceFields()
        save()
    }
    /** Clears parcel 1's debris and plants a mix of unwatered vegetables, ready to test watering. */
    /** Waters every thirsty plant across every unlocked parcel at once. */
    fun cheatWaterAll() { waterMany(plots.indices.toList(), System.currentTimeMillis()) }
    fun save() {
        val array = JSONArray()
        plots.forEach { p -> array.put(JSONObject().put("crop", p.crop?.name ?: "").put("planted", p.planted)
            .put("watered", p.watered).put("variant", p.variant).put("established", p.established).put("debris", p.debris)
            .put("critical", p.critical)) }
        val lands = JSONArray()
        parcels.forEach { lands.put(JSONObject().put("unlocked", it.unlocked).put("use", it.use.name)) }
        val inventory = JSONObject()
        FarmCrop.entries.forEach { inventory.put(it.name, seeds[it.ordinal]) }
        prefs.edit().putString("state", JSONObject().put("version", 4).put("coins", coins)
            .put("harvests", harvests).put("selected", selected.name).put("plots", array)
            .put("parcels", lands).put("seeds", inventory).put("wateringLevel", wateringLevel)
            .put("largeFields", largeFields.json()).put("livestock", livestock.toJson()).toString()).apply()
    }
}
