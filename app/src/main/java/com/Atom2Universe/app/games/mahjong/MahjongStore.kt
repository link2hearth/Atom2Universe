package com.Atom2Universe.app.games.mahjong

import android.content.Context
import android.content.SharedPreferences
import android.util.JsonReader
import android.util.JsonToken
import java.io.StringReader
import kotlin.random.Random

internal data class MahjongReward(val pearls: Int, val styles: List<Int>, val gift: Boolean, val comboBonus: Int)

/** Each mode, the journey and the daily board have a separate durable slot. */
internal class MahjongStore(val prefs: SharedPreferences) {
    constructor(context: Context) : this(context.getSharedPreferences("mahjong_garden", Context.MODE_PRIVATE))
    private val encoder = MahjongSave.Encoder()
    data class Summary(val total: Int, val remaining: Int)
    val pearls get() = prefs.getInt("pearls", 0)
    val wins get() = prefs.getInt("wins", 0)
    val purchases get() = prefs.getInt("porcelain_purchases", 0).coerceIn(0, 21)
    val nextPrice get() = MahjongCollection.price(purchases)
    /** Atomic local purchase; reentrant taps cannot debit an owned design twice. */
    fun purchase(style: Int): Boolean = synchronized(prefs) {
        if (style !in 0 until MahjongStyles.COUNT || style in unlocked || pearls < nextPrice) return@synchronized false
        prefs.edit().putInt("pearls", pearls - nextPrice)
            .putInt("porcelain_purchases", purchases + 1)
            .putStringSet("porcelains", (unlocked + style).map(Int::toString).toSet())
            .putInt("porcelain", style).commit()
    }
    val stage get() = prefs.getInt("stage", 1).coerceIn(1, 999_999)
    /** Applies only when creating a free-play board; an unfinished board keeps its level. */
    val difficulty get() = prefs.getInt("puzzle_difficulty", 1).coerceIn(0, 1)
    fun setDifficulty(value: Int) { prefs.edit().putInt("puzzle_difficulty", value.coerceIn(0, 1)).apply() }
    val unlocked get() = prefs.getStringSet("porcelains", setOf("0", "1", "2"))!!.mapNotNull { it.toIntOrNull() }
        .filter { it in 0 until MahjongStyles.COUNT }.toSet()
    val style get() = prefs.getInt("porcelain", 0).takeIf { it in unlocked } ?: 0
    val unlockedTextures get() = prefs.getStringSet("textures", setOf("0", "1", "2"))!!
        .mapNotNull { it.toIntOrNull() }.filter { it in 0 until MahjongStyles.COUNT }.toSet()
    val texture get() = prefs.getInt("texture", 0).takeIf { it in unlockedTextures } ?: 0
    val texturePurchases get() = prefs.getInt("texture_purchases", 0).coerceIn(0, 21)
    val nextTexturePrice get() = MahjongCollection.price(texturePurchases)

    fun equipTexture(id: Int) { if (id in unlockedTextures) prefs.edit().putInt("texture", id).apply() }
    fun purchaseTexture(id: Int): Boolean = synchronized(prefs) {
        if (id !in 0 until MahjongStyles.COUNT || id in unlockedTextures || pearls < nextTexturePrice)
            return@synchronized false
        prefs.edit().putInt("pearls", pearls - nextTexturePrice)
            .putInt("texture_purchases", texturePurchases + 1)
            .putStringSet("textures", (unlockedTextures + id).map(Int::toString).toSet())
            .putInt("texture", id).commit()
    }

    init {
        if (!prefs.getBoolean("independent_saves_v2", false)) {
            val edit = prefs.edit()
            prefs.getString("run", null)?.let(MahjongSave::decode)?.let { old ->
                if (!prefs.contains("run_${old.slot}")) writeRun(edit, old)
            }
            val inherited = (0 until (3 + wins).coerceAtMost(MahjongStyles.COUNT)).toMutableSet()
            val thresholds = intArrayOf(0, 120, 320, 640, 1100, 1800)
            thresholds.forEachIndexed { i, threshold -> if (pearls >= threshold) inherited += i * 2 }
            val oldDaily = prefs.getLong("daily_day", Long.MIN_VALUE)
            if (oldDaily != Long.MIN_VALUE) edit.putStringSet("daily_receipts", setOf(oldDaily.toString()))
            edit.putStringSet("porcelains", inherited.map(Int::toString).toSet())
                .putInt("porcelain", (prefs.getInt("theme", 0) * 2).coerceIn(0, 10))
                .putBoolean("independent_saves_v2", true).remove("run").apply()
        }
        // Existing porcelain purchases included their material. Keep those textures for free.
        if (!prefs.contains("textures")) prefs.edit()
            .putStringSet("textures", unlocked.map(Int::toString).toSet())
            .putInt("texture", style).apply()
    }

    fun load(slot: String) = prefs.getString("run_$slot", null)?.let(MahjongSave::decode)?.takeIf { it.slot == slot }
    private fun writeRun(edit: SharedPreferences.Editor, run: MahjongSave.Saved): SharedPreferences.Editor {
        val raw = encoder.encode(run)
        return writeSummary(edit.putString("run_${run.slot}", raw)
            .putString("current_${run.slot}", run.id).putBoolean("rewarded_${run.slot}", run.reward != null),
            run.slot, raw, Summary(run.game.tiles.size, run.game.remaining))
    }

    private fun writeSummary(edit: SharedPreferences.Editor, slot: String, raw: String, summary: Summary) = edit
        .putInt("summary_total_$slot", summary.total).putInt("summary_remaining_$slot", summary.remaining)
        .putInt("summary_hash_$slot", raw.hashCode()).putInt("summary_length_$slot", raw.length)

    /** Menu cards need only counts, never a game, solver, or restored undo stack. */
    fun summary(slot: String): Summary? = synchronized(prefs) {
        val raw = prefs.getString("run_$slot", null) ?: return@synchronized null
        if (prefs.getInt("summary_length_$slot", -1) == raw.length &&
            prefs.getInt("summary_hash_$slot", 0) == raw.hashCode()) {
            val total = prefs.getInt("summary_total_$slot", -1)
            val remaining = prefs.getInt("summary_remaining_$slot", -1)
            if (total in 2..256 && remaining in 0..total) return@synchronized Summary(total, remaining)
        }
        readSummary(raw, slot)?.also { writeSummary(prefs.edit(), slot, raw, it).apply() }
    }

    private fun readSummary(raw: String, slot: String): Summary? = runCatching {
        JsonReader(StringReader(raw)).use { reader ->
            var version = 0
            var mode: String? = null
            var journey = false
            var daily = false
            var result: Summary? = null
            reader.beginObject()
            while (reader.hasNext()) when (reader.nextName()) {
                "version" -> version = reader.nextInt()
                "mode" -> mode = reader.nextString()
                "journey" -> journey = reader.nextBoolean()
                "daily" -> daily = reader.nextBoolean()
                "state" -> {
                    reader.beginObject()
                    while (reader.hasNext()) {
                        if (reader.nextName() != "tiles") {
                            reader.skipValue()
                            continue
                        }
                        var total = 0
                        var remaining = 0
                        reader.beginArray()
                        while (reader.hasNext()) {
                            require(total < 256)
                            reader.beginArray()
                            repeat(5) { reader.skipValue() }
                            val removed = reader.nextInt()
                            require(removed in 0..1)
                            require(!reader.hasNext())
                            reader.endArray()
                            total++
                            if (removed == 0) remaining++
                        }
                        reader.endArray()
                        require(total in 2..256)
                        result = Summary(total, remaining)
                    }
                    reader.endObject()
                }
                // In particular, skip history without constructing its snapshots.
                else -> reader.skipValue()
            }
            reader.endObject()
            require(reader.peek() == JsonToken.END_DOCUMENT)
            require(version in 1..4 && !(journey && daily))
            require(MahjongMode.entries.any { it.name == mode } || (mode == "MEMORY" && version <= 2 && (journey || daily)))
            require(slot == when { journey -> "journey"; daily -> "daily"; else -> mode })
            result
        }
    }.getOrNull()

    fun save(run: MahjongSave.Saved) = synchronized(prefs) {
        // Cheap metadata checks avoid regenerating/parsing the whole undo stack on every tap.
        val current = prefs.getString("current_${run.slot}", null)
        if (current != null && current != run.id) return@synchronized
        if (prefs.getBoolean("rewarded_${run.slot}", false)) {
            if (run.reward == null) run.reward = load(run.slot)?.reward
            return@synchronized
        }
        writeRun(prefs.edit(), run).apply()
    }

    fun resumeOrCreate(slot: String, today: Long): MahjongSave.Saved {
        load(slot)?.takeIf { !it.game.complete || it.reward == null }?.let { return it }
        return newRun(slot, today)
    }

    fun newRun(slot: String, today: Long): MahjongSave.Saved {
        return prepareRun(slot, today, fresh = true).also(::activate)
    }

    /** May generate/solve on a worker; accepting a prepared result is a separate UI action. */
    fun prepareRun(slot: String, today: Long, fresh: Boolean = false, restoring: Boolean = false): MahjongSave.Saved {
        if (!fresh || restoring) load(slot)?.takeIf { restoring || !it.game.complete || it.reward == null }?.let { return it }
        val journey = slot == "journey"; val daily = slot == "daily"
        val mode = when { journey -> MahjongMode.entries[(stage - 1) % MahjongMode.entries.size]
            daily -> MahjongMode.entries[Math.floorMod(today, MahjongMode.entries.size.toLong()).toInt()]
            else -> MahjongMode.valueOf(slot) }
        val level = when {
            journey -> stage
            daily -> 5
            else -> intArrayOf(1, 4)[difficulty] +
                (prefs.getInt("wins_${mode.name}", 0).coerceAtLeast(0) / 3).coerceAtMost(4)
        }
        val seed = if (daily) (today xor 0x4d41484aL).toInt() else Random.nextInt()
        return MahjongSave.Saved(MahjongGame(mode, seed, level), journey, daily, if (daily) today else 0L, bonusMultiplier = MahjongCollection.multiplier(mode, today))
    }

    fun activate(run: MahjongSave.Saved) = synchronized(prefs) { writeRun(prefs.edit(), run).apply() }

    fun equip(style: Int) { if (style in unlocked) prefs.edit().putInt("porcelain", style).apply() }

    /** Reward and completed run are written in the SAME transaction; animations can be skipped safely. */
    fun finish(run: MahjongSave.Saved): MahjongReward? = synchronized(prefs) {
        if (!run.game.complete) return@synchronized null
        val stored = load(run.slot) ?: return@synchronized null
        if (stored.id != run.id) return@synchronized null
        stored.reward?.let { run.reward = it; return@synchronized it }
        val gift = (wins + 1) % 5 == 0
        val receipts = prefs.getStringSet("daily_receipts", emptySet())!!.toMutableSet()
        val dailyBonus = run.daily && receipts.add(run.day.toString())
        val comboBonus = MahjongCollection.comboBonus(run.game)
        val amount = (30 + run.game.tiles.size / 3 + comboBonus) * run.bonusMultiplier +
            (if (gift) 100 else 0) + (if (dailyBonus) 40 else 0)
        val balance = (pearls.toLong() + amount).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        val styles = emptyList<Int>()
        val reward = MahjongReward(amount, styles, gift, comboBonus)
        run.reward = reward
        val mode = run.game.mode.name
        val edit = prefs.edit().putInt("wins", wins + 1)
            .putInt("wins_$mode", prefs.getInt("wins_$mode", 0) + 1)
            .putInt("pearls", balance)
            .putInt("best_$mode", maxOf(run.game.score, prefs.getInt("best_$mode", 0)))
            .putStringSet("porcelains", (unlocked + styles).map(Int::toString).toSet())
            .putStringSet("daily_receipts", receipts)
        writeRun(edit, run)
        if (run.journey) edit.putInt("stage", maxOf(stage, run.game.stage + 1))
        edit.apply()
        reward
    }
}
