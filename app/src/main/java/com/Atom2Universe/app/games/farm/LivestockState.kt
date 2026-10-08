package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

/**
 * Order is the unlock order and part of the save format.
 *
 * [sale] stays below [price] on purpose: buying to resell must always lose money, the profit comes
 * from the free offspring. A full herd (24 mothers) is worth roughly 4 800, 16 500, 38 400 and
 * 82 300 coins a day - together about a third of what a fully planted farm earns by hand, which is
 * the point: the pens are comfortable, never the best way to play.
 */
enum class LivestockKind(val label: Int, val female: String, val male: String, val young: String,
                         val shelter: String, val landPrice: Int, val price: Int, val sale: Int,
                         val cycleHours: Int, val harvestsNeeded: Int, val manurePerDay: Int) {
    CHICKENS(R.string.farm_chickens, "hen", "rooster", "chick", "chicken_coop", 5_000, 500, 400, 48, 200, 4),
    SHEEP(R.string.farm_sheep, "ewe", "ram", "lamb", "sheep_shelter", 40_000, 3_000, 2_400, 84, 500, 6),
    PIGS(R.string.farm_pigs, "sow", "boar", "piglet", "pig_shelter", 150_000, 10_000, 8_000, 120, 1_200, 8),
    CATTLE(R.string.farm_cattle, "cow", "bull", "calf", "cattle_shelter", 500_000, 30_000, 24_000, 168, 2_500, 10);
    val cycleMillis get() = cycleHours * 3_600_000L
    val visualVariantCount get() = 4
}

data class FarmAnimal(val id: Long, val kind: LivestockKind, val male: Boolean, val variant: Int,
                      var adultAt: Long = 0, var birthAt: Long = 0, var boostUntil: Long = 0) {
    val adult get() = adultAt == 0L
    val sprite get() = (if (!adult) kind.young else if (male) kind.male else kind.female) + "_" +
        variant
}

class LivestockState {
    companion object {
        const val WEEK = 7 * 24 * 60 * 60 * 1000L
        const val CAPACITY = 48
        const val DAY = 24 * 60 * 60 * 1000L
        /** The pit holds three days of the herd's output, with a floor for a herd of two. */
        const val MANURE_DAYS = 3
        const val MANURE_FLOOR = 200L
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
    val animals: List<FarmAnimal> get() = herd
    fun count(kind: LivestockKind) = herd.count { it.kind == kind }
    fun available(kind: LivestockKind) = kind.ordinal < unlocked
    fun unlock(kind: LivestockKind): Boolean {
        if (kind.ordinal != unlocked) return false
        unlocked++; return true
    }
    fun buy(kind: LivestockKind, male: Boolean, now: Long): Boolean {
        if (!available(kind) || count(kind) >= CAPACITY) return false
        herd.add(FarmAnimal(nextId++, kind, male, kotlin.random.Random.nextInt(1, kind.visualVariantCount + 1)))
        lastTime = maxOf(now, lastTime)
        schedule(lastTime); return true
    }
    fun sell(id: Long, now: Long): Int {
        val animal = herd.firstOrNull { it.id == id && it.adult } ?: return 0
        lastTime = maxOf(now, lastTime)
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
            herd.filter { !it.adult && it.adultAt <= next }.forEach { it.adultAt = 0; changed = true }
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
        if (changed) lastTime = now
        return changed
    }
    /** Back to no pen, no animal - part of the dev reset, which must leave nothing behind. */
    fun reset() { unlocked = 0; nextId = 1L; lastTime = 0L; manure = 0L; manureTime = -1L; herd.clear() }
    /**
     * Dev-only: pulls every deadline closer. A young animal keeps a deadline of at least 1 ms so it
     * stays young until [advance] promotes it - zero is what marks an adult.
     */
    fun cheatSkip(millis: Long) {
        herd.forEach {
            if (!it.adult) it.adultAt = (it.adultAt - millis).coerceAtLeast(1L)
            if (it.birthAt > 0) it.birthAt = (it.birthAt - millis).coerceAtLeast(1L)
            if (it.boostUntil > 0) it.boostUntil = (it.boostUntil - millis).coerceAtLeast(0L)
        }
        // The pit fills on the same clock; -1 stays -1, it just has not started counting yet.
        if (manureTime >= 0L) manureTime = (manureTime - millis).coerceAtLeast(0L)
    }
    /** Dev-only: every young animal grown and every birth due, ready for [advance] to apply. */
    fun cheatRush(now: Long) {
        herd.forEach {
            if (!it.adult) it.adultAt = now
            if (it.birthAt > 0) it.birthAt = now
        }
    }
    fun toJson(): JSONObject {
        val animals = JSONArray()
        herd.forEach { animals.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("male", it.male)
            .put("variant", it.variant).put("adultAt", it.adultAt).put("birthAt", it.birthAt).put("boostUntil", it.boostUntil)) }
        return JSONObject().put("timingVersion", 2).put("unlocked", unlocked).put("nextId", nextId)
            .put("lastTime", lastTime).put("manure", manure).put("manureTime", manureTime).put("animals", animals)
    }
    fun restore(json: JSONObject?) {
        if (json == null) return
        val opened = json.getInt("unlocked"); require(opened in 0..4)
        val array = json.getJSONArray("animals"); require(array.length() <= CAPACITY * 4)
        val restored = List(array.length()) { i -> array.getJSONObject(i).let {
            FarmAnimal(it.getLong("id"), LivestockKind.valueOf(it.getString("kind")), it.getBoolean("male"),
                it.getInt("variant"), it.getLong("adultAt"), it.getLong("birthAt"), it.optLong("boostUntil").coerceAtLeast(0))
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
    }
}
