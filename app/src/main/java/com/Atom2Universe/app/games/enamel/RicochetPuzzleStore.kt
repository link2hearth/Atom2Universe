package com.Atom2Universe.app.games.enamel

import android.content.Context

/** Sauvegarde locale du défi et minimum de coups par version, difficulté et seed. */
class RicochetPuzzleStore(context: Context) {
    companion object { const val PREFS = "ricochet_puzzle" }
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Record(val seed: Int, val hard: Boolean, val shots: Int, val key: String)

    /** Inclut les records synchronisés, même si la seed ne figure plus dans les essais récents. */
    fun records(): List<Record> = prefs.all.mapNotNull { (key, value) ->
        val parts = key.split('_')
        val seed = parts.getOrNull(3)?.toIntOrNull()
        val shots = value as? Int
        if (parts.size != 4 || parts[0] != "best" || parts[1] != RicochetPuzzleRules.VERSION.toString() ||
            parts[2] !in listOf("0", "1") || seed == null || seed !in 0..RicochetPuzzleRules.MAX_SEED ||
            shots == null || shots <= 0) null
        else Record(seed, parts[2] == "1", shots, key)
    }.sortedWith(compareBy({ it.hard }, { it.seed }))

    fun restore(game: RicochetPuzzleGame): Boolean = prefs.getString("run", null)?.let(game::restoreState) ?: false
    fun best(game: RicochetPuzzleGame): Int = prefs.getInt(game.recordKey, 0)
    fun best(seed: Int, hard: Boolean): Int = prefs.getInt(RicochetPuzzleGame.recordKey(seed, hard), 0)

    fun save(game: RicochetPuzzleGame): Boolean {
        val old = best(game)
        val record = game.complete && (old == 0 || game.shots < old)
        val key = "${if (game.hard) 1 else 0}:${game.seed}"
        val recent = (listOf(key) + (prefs.getString("recent", "") ?: "").split(',').filter { it.isNotEmpty() && it != key }).take(30)
        prefs.edit().putString("run", game.saveState()).putString("recent", recent.joinToString(",")).apply {
            if (record) putInt(game.recordKey, game.shots)
        }.apply()
        return record
    }

    fun recent(): List<Pair<Int, Boolean>> = (prefs.getString("recent", "") ?: "").split(',').mapNotNull {
        val parts = it.split(':')
        val seed = parts.getOrNull(1)?.toIntOrNull()
        if (parts.size != 2 || parts[0] !in listOf("0", "1") || seed == null || seed !in 0..RicochetPuzzleRules.MAX_SEED) null
        else seed to (parts[0] == "1")
    }
}
