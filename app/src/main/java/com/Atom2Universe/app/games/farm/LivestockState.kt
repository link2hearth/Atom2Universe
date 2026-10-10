package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Order is the unlock order and part of the save format.
 *
 * [sale] stays below [price] on purpose: buying to resell must always lose money, the profit comes
 * from offspring and produce. Ordinary food and water are supplied automatically; affection
 * is permanent and optional, never a maintenance requirement or a production multiplier.
 */
enum class LivestockKind(val label: Int, val female: String, val male: String, val young: String,
                         val shelter: String, val landPrice: Int, val price: Int, val sale: Int,
                         val cycleHours: Int, val harvestsNeeded: Int, val manurePerDay: Int) {
    CHICKENS(R.string.farm_chickens, "hen", "rooster", "chick", "chicken_coop", 300, 60, 48, 48, 24, 4),
    SHEEP(R.string.farm_sheep, "ewe", "ram", "lamb", "sheep_shelter", 40_000, 3_000, 2_400, 84, 500, 6),
    PIGS(R.string.farm_pigs, "sow", "boar", "piglet", "pig_shelter", 150_000, 10_000, 8_000, 120, 1_200, 8),
    CATTLE(R.string.farm_cattle, "cow", "bull", "calf", "cattle_shelter", 500_000, 30_000, 24_000, 168, 2_500, 10);
    val cycleMillis get() = cycleHours * 3_600_000L
    val visualVariantCount get() = 4
}

enum class FarmAnimalProduct(val label: Int, val cycleHours: Int, val sale: Int) {
    EGG(R.string.farm_product_egg, 24, 12),
    WOOL(R.string.farm_product_wool, 48, 240),
    MILK(R.string.farm_product_milk, 24, 1_200),
    TRUFFLE(R.string.farm_product_truffle, 48, 400);
    val cycleMillis get() = cycleHours * 3_600_000L
}

data class FarmAnimal(val id: Long, val kind: LivestockKind, val male: Boolean, val variant: Int,
                      var adultAt: Long = 0, var birthAt: Long = 0, var boostUntil: Long = 0,
                      var name: String = "", var affection: Int = 0, var lastPetAt: Long = -1,
                      var productAt: Long = -1) {
    val adult get() = adultAt == 0L
    val sprite get() = (if (!adult) kind.young else if (male) kind.male else kind.female) + "_" +
        variant
    val product: FarmAnimalProduct? get() = if (!adult) null else futureProduct
    val futureProduct: FarmAnimalProduct? get() = when (kind) {
        LivestockKind.CHICKENS -> if (male) null else FarmAnimalProduct.EGG
        LivestockKind.SHEEP -> FarmAnimalProduct.WOOL
        LivestockKind.PIGS -> FarmAnimalProduct.TRUFFLE
        LivestockKind.CATTLE -> if (male) null else FarmAnimalProduct.MILK
    }
}

class LivestockState {
    companion object {
        const val WEEK = 7 * 24 * 60 * 60 * 1000L
        const val CAPACITY = 48
        const val DAY = 24 * 60 * 60 * 1000L
        /** The pit holds three days of the herd's output, with a floor for a herd of two. */
        const val MANURE_DAYS = 3
        const val MANURE_FLOOR = 200L
        const val PET_INTERVAL = 18 * 3_600_000L
        const val FIRST_EGG_MILLIS = 60_000L
        const val PRODUCT_CAPACITY = 999_999
        const val NAME_LENGTH = 24
        fun cleanName(value: String): String = value.filterNot { it.isISOControl() }
            .trim().replace(Regex("\\s+"), " ").let { clean ->
                clean.substring(0, clean.offsetByCodePoints(0, minOf(NAME_LENGTH, clean.codePointCount(0, clean.length))))
            }
    }
    var unlocked = 0
        private set
    /** Manure points, the loop that pays the vegetable garden back for feeding the pens. */
    var manure = 0L
        private set
    /** Instant up to which manure has been credited. -1 means never: zero is a valid instant. */
    private var manureTime = -1L
    private var nextId = 1L
    private var lastTime = 0L
    private val herd = mutableListOf<FarmAnimal>()
    private val products = IntArray(FarmAnimalProduct.entries.size)
    private var firstEggStarted = false
    val firstEggAvailable get() = !firstEggStarted
    val animals: List<FarmAnimal> get() = herd
    fun count(kind: LivestockKind) = herd.count { it.kind == kind }
    fun available(kind: LivestockKind) = kind.ordinal < unlocked
    fun find(id: Long) = herd.firstOrNull { it.id == id }
    fun stock(product: FarmAnimalProduct) = products[product.ordinal]
    fun productCount() = products.sum()
    fun productValue() = FarmAnimalProduct.entries.sumOf { stock(it).toLong() * it.sale }
    fun readyProducts(kind: LivestockKind? = null, clock: Long = System.currentTimeMillis()): Int =
        herd.count { (kind == null || it.kind == kind) && it.product != null && it.productAt >= 0 && it.productAt <= maxOf(clock, lastTime) }
    fun nextProductAt(kind: LivestockKind? = null): Long? = herd.filter {
        (kind == null || it.kind == kind) && it.product != null && it.productAt >= 0
    }.minOfOrNull { it.productAt }
    fun rename(id: Long, name: String): Boolean {
        val animal = find(id) ?: return false
        val cleaned = cleanName(name)
        if (cleaned == animal.name) return false
        animal.name = cleaned; return true
    }
    fun canPet(animal: FarmAnimal, clock: Long) = animal.lastPetAt < 0 || maxOf(clock, lastTime) - animal.lastPetAt >= PET_INTERVAL
    fun pet(id: Long, clock: Long): Boolean {
        val animal = find(id) ?: return false
        val now = maxOf(clock, lastTime)
        if (!canPet(animal, now)) return false
        animal.affection = (animal.affection + 1).coerceAtMost(1_000_000)
        animal.lastPetAt = now; lastTime = now; return true
    }
    fun petAll(kind: LivestockKind, clock: Long): List<Long> = herd.filter { it.kind == kind }
        .mapNotNull { if (pet(it.id, clock)) it.id else null }
    private fun startProduction(animal: FarmAnimal, now: Long, introduction: Boolean = false): Boolean {
        val product = animal.product ?: return false
        if (animal.productAt >= 0) return false
        val firstEgg = introduction && product == FarmAnimalProduct.EGG && !firstEggStarted
        animal.productAt = now + if (firstEgg) FIRST_EGG_MILLIS else product.cycleMillis
        if (product == FarmAnimalProduct.EGG) firstEggStarted = true
        return true
    }
    /** One batch waits per animal. A full inventory leaves that batch on the animal, safely ready. */
    private fun collectOne(animal: FarmAnimal, now: Long): Boolean {
        val product = animal.product ?: return false
        if (animal.productAt < 0 || animal.productAt > now || stock(product) >= PRODUCT_CAPACITY) return false
        products[product.ordinal]++
        animal.productAt = now + product.cycleMillis
        return true
    }
    fun collectProducts(kind: LivestockKind? = null, clock: Long): Int {
        advance(clock)
        val now = maxOf(clock, lastTime)
        var collected = 0
        herd.filter { kind == null || it.kind == kind }.forEach { animal ->
            if (collectOne(animal, now)) collected++
        }
        if (collected > 0) lastTime = now
        return collected
    }
    fun takeProduct(product: FarmAnimalProduct, quantity: Int): Int {
        val count = quantity.coerceIn(0, stock(product))
        products[product.ordinal] -= count; return count
    }
    fun unlock(kind: LivestockKind): Boolean {
        if (kind.ordinal != unlocked) return false
        unlocked++; return true
    }
    fun buy(kind: LivestockKind, male: Boolean, now: Long): Boolean {
        if (!available(kind) || count(kind) >= CAPACITY) return false
        lastTime = maxOf(now, lastTime)
        herd.add(FarmAnimal(nextId++, kind, male, kotlin.random.Random.nextInt(1, kind.visualVariantCount + 1)).also {
            startProduction(it, lastTime, introduction = true)
        })
        schedule(lastTime); return true
    }
    fun sell(id: Long, now: Long): Int {
        val animal = herd.firstOrNull { it.id == id && it.adult } ?: return 0
        lastTime = maxOf(now, lastTime)
        if (animal.product != null && animal.productAt in 0..lastTime && !collectOne(animal, lastTime)) return 0
        herd.remove(animal); schedule(lastTime); return animal.kind.sale
    }
    private fun schedule(now: Long) {
        for (kind in LivestockKind.entries) {
            val hasMale = herd.any { it.kind == kind && it.adult && it.male }
            herd.filter { it.kind == kind && it.adult && !it.male }.forEach {
                if (!hasMale) it.birthAt = 0
                else if (it.birthAt == 0L) it.birthAt = now + kind.cycleMillis
            }
        }
    }
    /** Only grown animals produce; a pen of chicks pays nothing. */
    fun manurePerDay(): Long = herd.sumOf { if (it.adult) it.kind.manurePerDay.toLong() else 0L }
    fun manureCapacity(): Long = maxOf(MANURE_FLOOR, manurePerDay() * MANURE_DAYS)
    /**
     * Credits whole points only, and gives back just the time it actually converted - the leftover
     * milliseconds stay banked for the next call. Without that, [advance] running once a second would
     * round every tick down to zero and the pit would never fill at all.
     */
    private fun collectManure(until: Long): Boolean {
        if (manureTime < 0L) { manureTime = until; return false }
        if (until <= manureTime) return false
        val rate = manurePerDay()
        if (rate <= 0L) { manureTime = until; return false }
        val points = rate * (until - manureTime) / DAY
        if (points <= 0L) return false
        manureTime += points * DAY / rate
        val before = manure
        manure = (manure + points).coerceAtMost(manureCapacity())
        return manure != before
    }
    /** Spends the points a planting asks for, or nothing at all when the pit is too low. */
    fun spendManure(cost: Int): Boolean {
        if (cost <= 0 || manure < cost) return false
        manure -= cost; return true
    }

    /** Replay dated events, including offspring growing up while the app is closed. */
    fun advance(clock: Long): Boolean {
        val now = maxOf(clock, lastTime)
        var changed = false
        while (true) {
            val next = herd.minOfOrNull { if (it.adult) it.birthAt.takeIf { due -> due > 0 } ?: Long.MAX_VALUE else it.adultAt } ?: break
            if (next > now) break
            // Credited before the event is applied, so each stretch uses the herd it really had.
            if (collectManure(next)) changed = true
            herd.filter { !it.adult && it.adultAt <= next }.forEach {
                it.adultAt = 0; startProduction(it, next); changed = true
            }
            val mothers = herd.filter { it.adult && !it.male && it.birthAt in 1..next }
            mothers.forEach { mother ->
                if (herd.any { it.kind == mother.kind && it.adult && it.male } && count(mother.kind) < CAPACITY) {
                    herd.add(FarmAnimal(nextId++, mother.kind, kotlin.random.Random.nextBoolean(),
                        kotlin.random.Random.nextInt(1, mother.kind.visualVariantCount + 1), next + mother.kind.cycleMillis))
                }
                mother.birthAt = next + mother.kind.cycleMillis; changed = true
            }
            schedule(next)
            // A full adult herd cannot change again until a sale; avoid replaying years of blocked births.
            for (kind in LivestockKind.entries) if (count(kind) >= CAPACITY && herd.none { it.kind == kind && !it.adult }) {
                herd.filter { it.kind == kind && it.birthAt > 0 && it.birthAt <= now }.forEach {
                    it.birthAt += ((now - it.birthAt) / kind.cycleMillis + 1) * kind.cycleMillis
                }
            }
        }
        if (collectManure(now)) changed = true
        herd.forEach { if (startProduction(it, now)) changed = true }
        if (changed) lastTime = now
        return changed
    }
    /** Back to no pen, no animal - part of the dev reset, which must leave nothing behind. */
    fun reset() {
        unlocked = 0; nextId = 1L; lastTime = 0L; manure = 0L; manureTime = -1L; herd.clear()
        products.fill(0); firstEggStarted = false
    }
    /**
     * Dev-only: pulls every deadline closer. A young animal keeps a deadline of at least 1 ms so it
     * stays young until [advance] promotes it - zero is what marks an adult.
     */
    fun cheatSkip(millis: Long) {
        herd.forEach {
            if (!it.adult) it.adultAt = (it.adultAt - millis).coerceAtLeast(1L)
            if (it.birthAt > 0) it.birthAt = (it.birthAt - millis).coerceAtLeast(1L)
            if (it.boostUntil > 0) it.boostUntil = (it.boostUntil - millis).coerceAtLeast(0L)
            if (it.productAt >= 0) it.productAt = (it.productAt - millis).coerceAtLeast(0L)
            if (it.lastPetAt >= 0) it.lastPetAt = (it.lastPetAt - millis).coerceAtLeast(0L)
        }
        // The pit fills on the same clock; -1 stays -1, it just has not started counting yet.
        if (manureTime >= 0L) manureTime = (manureTime - millis).coerceAtLeast(0L)
    }
    /** Dev-only: every young animal grown and every birth due, ready for [advance] to apply. */
    fun cheatRush(now: Long) {
        herd.forEach {
            if (!it.adult) it.adultAt = now
            if (it.birthAt > 0) it.birthAt = now
            if (it.productAt >= 0) it.productAt = now
        }
    }
    fun toJson(): JSONObject {
        val animals = JSONArray()
        herd.forEach { animals.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("male", it.male)
            .put("variant", it.variant).put("adultAt", it.adultAt).put("birthAt", it.birthAt).put("boostUntil", it.boostUntil)
            .put("name", it.name).put("affection", it.affection).put("lastPetAt", it.lastPetAt).put("productAt", it.productAt)) }
        return JSONObject().put("timingVersion", 2).put("unlocked", unlocked).put("nextId", nextId)
            .put("lastTime", lastTime).put("manure", manure).put("manureTime", manureTime).put("animals", animals)
            .put("productionVersion", 1).put("firstEggStarted", firstEggStarted)
            .put("products", JSONObject().apply { FarmAnimalProduct.entries.forEach { put(it.name, stock(it)) } })
    }
    fun restore(json: JSONObject?, clock: Long = System.currentTimeMillis()) {
        if (json == null) return
        val opened = json.getInt("unlocked"); require(opened in 0..4)
        val array = json.getJSONArray("animals"); require(array.length() <= CAPACITY * 4)
        val restored = List(array.length()) { i -> array.getJSONObject(i).let {
            FarmAnimal(it.getLong("id"), LivestockKind.valueOf(it.getString("kind")), it.getBoolean("male"),
                it.getInt("variant"), it.getLong("adultAt"), it.getLong("birthAt"), it.optLong("boostUntil").coerceAtLeast(0),
                cleanName(it.optString("name")), it.optInt("affection").coerceIn(0, 1_000_000),
                it.optLong("lastPetAt", -1).coerceAtLeast(-1), it.optLong("productAt", -1).coerceAtLeast(-1))
        } }
        require(restored.map { it.id }.distinct().size == restored.size)
        require(restored.none { it.male && it.birthAt != 0L })
        require(restored.all { it.id > 0 && it.kind.ordinal < opened && it.variant in 1..it.kind.visualVariantCount && it.adultAt >= 0 && it.birthAt >= 0 && (it.adult || it.birthAt == 0L) })
        require(LivestockKind.entries.all { kind -> restored.count { it.kind == kind } <= CAPACITY })
        val id = json.getLong("nextId"); require(id > (restored.maxOfOrNull { it.id } ?: 0))
        val time = json.getLong("lastTime"); require(time >= 0)
        if (json.optInt("timingVersion", 1) < 2) {
            val reference = maxOf(System.currentTimeMillis(), time)
            fun migrate(deadline: Long, kind: LivestockKind): Long {
                if (deadline <= reference) return deadline
                val remaining = (deadline - reference).coerceAtMost(WEEK)
                return reference + remaining * kind.cycleMillis / WEEK
            }
            restored.forEach {
                it.adultAt = migrate(it.adultAt, it.kind)
                it.birthAt = migrate(it.birthAt, it.kind)
            }
        }
        val pit = json.optLong("manure").coerceAtLeast(0)
        val pitTime = json.optLong("manureTime", -1L).coerceAtLeast(-1L)
        unlocked = opened; nextId = id; lastTime = time; herd.clear(); herd.addAll(restored)
        manureTime = pitTime; manure = pit.coerceAtMost(manureCapacity())
        val stocks = json.optJSONObject("products")
        FarmAnimalProduct.entries.forEach { products[it.ordinal] = (stocks?.optInt(it.name) ?: 0).coerceIn(0, PRODUCT_CAPACITY) }
        firstEggStarted = json.optBoolean("firstEggStarted", restored.any { it.kind == LivestockKind.CHICKENS && it.adult && !it.male })
        // Existing adults start a normal cycle at migration; their previous birthdays stay intact.
        herd.forEach { animal ->
            if (animal.product == null) animal.productAt = -1
            else if (json.optInt("productionVersion") < 1) startProduction(animal, maxOf(clock, lastTime))
        }
    }
}
