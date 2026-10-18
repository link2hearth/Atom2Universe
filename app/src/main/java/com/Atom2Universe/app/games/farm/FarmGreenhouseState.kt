package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONArray
import org.json.JSONObject

enum class FarmFlower(val label: Int, val family: Int, val color: Int, val hours: Int) {
    DAISY_WHITE(R.string.farm_flower_daisy_white, 0, 0xFFF2D8, 12),
    DAISY_YELLOW(R.string.farm_flower_daisy_yellow, 0, 0xEAC956, 12),
    DAISY_GOLD(R.string.farm_flower_daisy_gold, 0, 0xD9983F, 12),
    TULIP_RED(R.string.farm_flower_tulip_red, 1, 0xCF7069, 18),
    TULIP_YELLOW(R.string.farm_flower_tulip_yellow, 1, 0xF0D066, 18),
    TULIP_ORANGE(R.string.farm_flower_tulip_orange, 1, 0xE7A05B, 18),
    IRIS_BLUE(R.string.farm_flower_iris_blue, 2, 0x789ACB, 24),
    IRIS_WHITE(R.string.farm_flower_iris_white, 2, 0xEAE5EE, 24),
    IRIS_LILAC(R.string.farm_flower_iris_lilac, 2, 0xAA83BF, 24);

    val parents: List<FarmFlower> get() = when (this) {
        DAISY_GOLD -> listOf(DAISY_WHITE, DAISY_YELLOW)
        TULIP_ORANGE -> listOf(TULIP_RED, TULIP_YELLOW)
        IRIS_LILAC -> listOf(IRIS_BLUE, IRIS_WHITE)
        else -> emptyList()
    }
    val base: Boolean get() = parents.isEmpty()
    val sale: Int get() = if (base) 8 else 12
}

data class FarmFlowerCulture(val id: Long, val slot: Int, val flower: FarmFlower,
                             val startedAt: Long, val readyAt: Long, val crossed: Boolean) {
    fun progress(now: Long): Float = if (now >= readyAt) 1f else
        ((now - startedAt).coerceAtLeast(0).toDouble() / (readyAt - startedAt).coerceAtLeast(1)).toFloat().coerceIn(0f, .99999994f)
}

/** Six planters, no decay. Crossing is deterministic and collection owns the discovery. */
class FarmGreenhouseState {
    private val discovered = FarmFlower.entries.filter { it.base }.toMutableSet()
    private val stock = IntArray(FarmFlower.entries.size)
    private val cultures = arrayOfNulls<FarmFlowerCulture>(SLOTS)
    private var nextId = 1L
    private var lastTime = 0L
    var revision = 1L
        private set
    var introUsed = false
        private set
    var displayFlower = FarmFlower.DAISY_WHITE
        private set
    val jobs: List<FarmFlowerCulture> get() = cultures.filterNotNull()
    val collection: Set<FarmFlower> get() = discovered.toSet()
    val recipeFlowers: Set<FarmFlower> get() = if (introUsed) collection else emptySet()
    fun knows(flower: FarmFlower) = flower in discovered
    fun count(flower: FarmFlower) = stock[flower.ordinal]
    fun totalStock() = stock.sum()
    fun stockValue() = FarmFlower.entries.sumOf { count(it).toLong() * it.sale }
    fun readyCount(now: Long) = jobs.count { it.readyAt <= maxOf(now, lastTime) }
    fun at(slot: Int): FarmFlowerCulture? = cultures.getOrNull(slot)
    fun canStart(flower: FarmFlower, crossed: Boolean, slot: Int? = null): Boolean =
        (if (slot == null) jobs.size < SLOTS else slot in cultures.indices && cultures[slot] == null) &&
        if (crossed) !flower.base && flower.parents.all { knows(it) && count(it) >= 1 } else knows(flower)

    internal fun start(flower: FarmFlower, crossed: Boolean, expectedRevision: Long, clock: Long, slot: Int? = null): Boolean {
        if (clock < 0 || revision != expectedRevision || !canStart(flower, crossed, slot)) return false
        val target = slot ?: cultures.indexOfFirst { it == null }
        val now = maxOf(clock, lastTime)
        val duration = if (crossed) CROSS_MILLIS else if (!introUsed) INTRO_MILLIS else flower.hours * 3_600_000L
        if (crossed) flower.parents.forEach { stock[it.ordinal]-- }
        cultures[target] = FarmFlowerCulture(nextId++, target, flower, now, now + duration, crossed)
        introUsed = true; lastTime = now; revision++
        return true
    }
    internal fun collect(clock: Long): Int {
        val now = maxOf(clock, lastTime)
        var picked = 0
        for (slot in cultures.indices) {
            val job = cultures[slot] ?: continue
            if (job.readyAt > now || count(job.flower) >= STOCK_CAPACITY) continue
            stock[job.flower.ordinal]++; discovered += job.flower; cultures[slot] = null; picked++
        }
        if (picked > 0) { lastTime = now; revision++ }
        return picked
    }
    internal fun take(flower: FarmFlower, quantity: Int): Int {
        val used = quantity.coerceIn(0, count(flower)); stock[flower.ordinal] -= used
        if (used > 0) revision++
        return used
    }
    internal fun display(flower: FarmFlower): Boolean {
        if (!knows(flower) || displayFlower == flower) return false
        displayFlower = flower; return true
    }
    internal fun cheatSkip(millis: Long) {
        for (slot in cultures.indices) cultures[slot]?.let {
            cultures[slot] = it.copy(startedAt = (it.startedAt - millis).coerceAtLeast(0), readyAt = (it.readyAt - millis).coerceAtLeast(0))
        }
    }
    fun toJson() = JSONObject().put("nextId", nextId).put("revision", revision).put("lastTime", lastTime)
        .put("introUsed", introUsed).put("display", displayFlower.name)
        .put("known", JSONObject().apply { discovered.forEach { put(it.name, true) } })
        .put("stock", JSONObject().apply { FarmFlower.entries.forEach { put(it.name, count(it)) } })
        .put("jobs", JSONArray().apply { jobs.forEach { put(JSONObject().put("id", it.id).put("slot", it.slot)
            .put("flower", it.flower.name).put("startedAt", it.startedAt).put("readyAt", it.readyAt).put("crossed", it.crossed)) } })

    companion object {
        const val SLOTS = 6
        const val STOCK_CAPACITY = 999_999
        const val INTRO_MILLIS = 60_000L
        const val CROSS_MILLIS = 12 * 3_600_000L
        fun fromJson(json: JSONObject?): FarmGreenhouseState {
            val state = FarmGreenhouseState()
            if (json == null) return state
            val known = json.optJSONObject("known"); val stock = json.optJSONObject("stock")
            FarmFlower.entries.forEach { flower ->
                state.stock[flower.ordinal] = (stock?.optInt(flower.name) ?: 0).coerceIn(0, STOCK_CAPACITY)
                if (known?.optBoolean(flower.name) == true || state.count(flower) > 0) state.discovered += flower
            }
            val rawJobs = json.optJSONArray("jobs"); val ids = mutableSetOf<Long>()
            for (index in 0 until (rawJobs?.length() ?: 0).coerceAtMost(SLOTS)) {
                val raw = rawJobs?.optJSONObject(index) ?: continue
                val flower = FarmFlower.entries.firstOrNull { it.name == raw.optString("flower") } ?: continue
                val slot = raw.optInt("slot", -1); val id = raw.optLong("id")
                val start = raw.optLong("startedAt", -1); val ready = raw.optLong("readyAt", -1)
                val crossed = raw.optBoolean("crossed")
                if (slot !in 0 until SLOTS || state.at(slot) != null || id !in 1..1_000_000_000_000L ||
                    start !in 0..100_000_000_000_000L || ready !in start..100_000_000_000_000L ||
                    (crossed && flower.base) || !ids.add(id)) continue
                state.cultures[slot] = FarmFlowerCulture(id, slot, flower, start, ready, crossed)
                if (!crossed) state.discovered += flower
            }
            state.nextId = maxOf(json.optLong("nextId", 1).coerceIn(1, 1_000_000_000_000L), (ids.maxOrNull() ?: 0) + 1)
            state.revision = maxOf(json.optLong("revision", 1).coerceIn(1, 1_000_000_000_000L), state.nextId)
            state.lastTime = json.optLong("lastTime").coerceIn(0, 100_000_000_000_000L)
            state.introUsed = json.optBoolean("introUsed") || state.jobs.isNotEmpty() || state.totalStock() > 0 || state.collection.any { !it.base }
            state.displayFlower = FarmFlower.entries.firstOrNull { it.name == json.optString("display") && state.knows(it) } ?: FarmFlower.DAISY_WHITE
            return state
        }
    }
}
