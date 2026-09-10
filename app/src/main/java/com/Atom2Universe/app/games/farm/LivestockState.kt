package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

/** Order is the unlock order and part of the save format. */
enum class LivestockKind(val label: Int, val female: String, val male: String, val young: String,
                         val shelter: String, val landPrice: Int, val price: Int, val sale: Int, val cycleHours: Int) {
    CHICKENS(R.string.farm_chickens, "hen", "rooster", "chick", "chicken_coop", 60, 10, 7, 48),
    SHEEP(R.string.farm_sheep, "ewe", "ram", "lamb", "sheep_shelter", 300, 60, 40, 84),
    PIGS(R.string.farm_pigs, "sow", "boar", "piglet", "pig_shelter", 900, 150, 100, 120),
    CATTLE(R.string.farm_cattle, "cow", "bull", "calf", "cattle_shelter", 2400, 400, 280, 168);
    val cycleMillis get() = cycleHours * 3_600_000L
}

data class FarmAnimal(val id: Long, val kind: LivestockKind, val male: Boolean, val variant: Int,
                      var adultAt: Long = 0, var birthAt: Long = 0, var boostUntil: Long = 0) {
    val adult get() = adultAt == 0L
    val sprite get() = (if (!adult) kind.young else if (male) kind.male else kind.female) + "_" + variant
}

class LivestockState {
    companion object { const val WEEK = 7 * 24 * 60 * 60 * 1000L; const val CAPACITY = 48 }
    var unlocked = 0
        private set
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
        herd.add(FarmAnimal(nextId++, kind, male, kotlin.random.Random.nextInt(1, 3)))
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
    /** Replay dated events, including offspring growing up while the app is closed. */
    fun advance(clock: Long): Boolean {
        val now = maxOf(clock, lastTime)
        var changed = false
        while (true) {
            val next = herd.minOfOrNull { if (it.adult) it.birthAt.takeIf { due -> due > 0 } ?: Long.MAX_VALUE else it.adultAt } ?: break
            if (next > now) break
            herd.filter { !it.adult && it.adultAt <= next }.forEach { it.adultAt = 0; changed = true }
            val mothers = herd.filter { it.adult && !it.male && it.birthAt in 1..next }
            mothers.forEach { mother ->
                if (herd.any { it.kind == mother.kind && it.adult && it.male } && count(mother.kind) < CAPACITY) {
                    herd.add(FarmAnimal(nextId++, mother.kind, kotlin.random.Random.nextBoolean(),
                        kotlin.random.Random.nextInt(1, 3), next + mother.kind.cycleMillis))
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
        if (changed) lastTime = now
        return changed
    }
    fun toJson(): JSONObject {
        val animals = JSONArray()
        herd.forEach { animals.put(JSONObject().put("id", it.id).put("kind", it.kind.name).put("male", it.male)
            .put("variant", it.variant).put("adultAt", it.adultAt).put("birthAt", it.birthAt).put("boostUntil", it.boostUntil)) }
        return JSONObject().put("timingVersion", 2).put("unlocked", unlocked).put("nextId", nextId).put("lastTime", lastTime).put("animals", animals)
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
        require(restored.all { it.id > 0 && it.kind.ordinal < opened && it.variant in 1..2 && it.adultAt >= 0 && it.birthAt >= 0 && (it.adult || it.birthAt == 0L) })
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
        unlocked = opened; nextId = id; lastTime = time; herd.clear(); herd.addAll(restored)
    }
}
