package com.Atom2Universe.app.games.farm

import com.Atom2Universe.app.R
import org.json.JSONObject

enum class FarmVisitor(val label: Int, val stories: List<Int>) {
    ROBIN(R.string.farm_visitor_robin, listOf(R.string.farm_robin_1, R.string.farm_robin_2, R.string.farm_robin_3)),
    BUTTERFLY(R.string.farm_visitor_butterfly, listOf(R.string.farm_butterfly_1, R.string.farm_butterfly_2, R.string.farm_butterfly_3)),
    SQUIRREL(R.string.farm_visitor_squirrel, listOf(R.string.farm_squirrel_1, R.string.farm_squirrel_2, R.string.farm_squirrel_3)),
    RABBIT(R.string.farm_visitor_rabbit, listOf(R.string.farm_rabbit_1, R.string.farm_rabbit_2, R.string.farm_rabbit_3))
}

data class FarmEncounter(val id: Long, val visitor: FarmVisitor, val story: Int,
                         val crop: FarmCrop, val seeds: Int, val coins: Long) {
    val coinAlternative: Long get() = coins + seeds.toLong() * crop.cost
}
data class FarmVisitorReward(val visitor: FarmVisitor, val coins: Long, val seeds: Int)

/** One patient visitor. Only a greeting starts the 24-hour interval to the next meeting. */
class FarmEncountersState {
    private val greetings = IntArray(FarmVisitor.entries.size)
    private var nextId = 1L
    private var lastTime = 0L
    var nextAt = 0L
        private set
    var pending: FarmEncounter? = null
        private set
    val totalGreetings get() = greetings.sum()
    fun count(visitor: FarmVisitor) = greetings[visitor.ordinal]
    internal fun ensure(available: Set<FarmVisitor>, crop: FarmCrop, clock: Long): Boolean {
        if (clock < 0 || pending != null || crop.rank <= 0) return false
        val now = maxOf(clock, lastTime)
        if (now < nextAt) return false
        val eligible = FarmVisitor.entries.filter { it in available }
        if (eligible.isEmpty()) return false
        // Pick the least recently introduced species by round-robin, without skipping new arrivals.
        val least = eligible.minOf { count(it) }
        val visitor = eligible.first { count(it) == least }
        pending = FarmEncounter(nextId++, visitor, count(visitor) % visitor.stories.size, crop, 4, maxOf(12L, crop.sale / 2L))
        lastTime = now
        return true
    }
    internal fun greet(id: Long, clock: Long): FarmEncounter? {
        val meeting = pending?.takeIf { it.id == id } ?: return null
        val now = maxOf(clock, lastTime)
        if (clock < 0) return null
        greetings[meeting.visitor.ordinal] = (count(meeting.visitor) + 1).coerceAtMost(1_000_000)
        pending = null; lastTime = now; nextAt = now + INTERVAL
        return meeting
    }
    fun toJson() = JSONObject().put("nextId", nextId).put("lastTime", lastTime).put("nextAt", nextAt)
        .put("greetings", JSONObject().apply { FarmVisitor.entries.forEach { put(it.name, count(it)) } })
        .apply { pending?.let { put("pending", JSONObject().put("id", it.id).put("visitor", it.visitor.name)
            .put("story", it.story).put("crop", it.crop.name).put("seeds", it.seeds).put("coins", it.coins)) } }
    companion object {
        const val INTERVAL = 24 * 3_600_000L
        fun fromJson(json: JSONObject?): FarmEncountersState {
            val state = FarmEncountersState()
            if (json == null) return state
            val visits = json.optJSONObject("greetings")
            FarmVisitor.entries.forEach { state.greetings[it.ordinal] = (visits?.optInt(it.name) ?: 0).coerceIn(0, 1_000_000) }
            state.nextAt = json.optLong("nextAt").coerceIn(0, 100_000_000_000_000L)
            state.lastTime = json.optLong("lastTime").coerceIn(0, 100_000_000_000_000L)
            state.pending = runCatching {
                val raw = json.optJSONObject("pending") ?: return@runCatching null
                val id = raw.getLong("id"); val visitor = FarmVisitor.valueOf(raw.getString("visitor"))
                val story = raw.getInt("story"); val crop = FarmCrop.valueOf(raw.getString("crop"))
                val seeds = raw.getInt("seeds"); val coins = raw.getLong("coins")
                require(id in 1..1_000_000_000_000L && story in visitor.stories.indices && crop.rank > 0 && seeds in 1..6 && coins in 1..1_000_000)
                FarmEncounter(id, visitor, story, crop, seeds, coins)
            }.getOrNull()
            state.nextId = maxOf(json.optLong("nextId", 1).coerceIn(1, 1_000_000_000_000L), (state.pending?.id ?: 0) + 1)
            return state
        }
    }
}

object FarmVisitorLayout {
    val area: FarmLayout.Area get() = FarmLayout.lands[0].let { FarmLayout.Area(it.x - 132f, it.y - 144f, it.x - 12f, it.y - 42f) }
}
