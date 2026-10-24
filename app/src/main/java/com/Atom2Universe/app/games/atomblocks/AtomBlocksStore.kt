package com.Atom2Universe.app.games.atomblocks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Independent checkpoints per mode/level; a daily puzzle never expires. */
internal class AtomBlocksStore(context: Context) {
    val prefs = context.getSharedPreferences("atom_blocks", Context.MODE_PRIVATE)

    fun key(mode: AtomBlocksEngine.Mode, level: Int, seed: Long) = when (mode) {
        AtomBlocksEngine.Mode.ADVENTURE -> "adventure_$level"
        AtomBlocksEngine.Mode.DAILY -> "daily_$seed"
        else -> mode.name.lowercase(java.util.Locale.ROOT)
    }

    fun save(game: AtomBlocksEngine) {
        val snapshot = game.snapshot()
        val key = key(game.mode, game.level, game.seed)
        prefs.edit().putString("save_$key", encode(snapshot).toString())
            .putString("last", key)
            .putInt("best_${game.mode.name}", maxOf(best(game.mode), game.score))
            .apply()
        if (game.won) prefs.edit().putBoolean("complete_$key", true).apply()
    }

    fun best(mode: AtomBlocksEngine.Mode) = prefs.getInt("best_${mode.name}", 0)
    fun completed(level: Int) = prefs.getBoolean("complete_adventure_$level", false)
    fun lastKey(): String? = prefs.getString("last", null)

    fun load(key: String, game: AtomBlocksEngine): Boolean {
        val value = prefs.getString("save_$key", null) ?: return false
        return runCatching { game.restore(decode(JSONObject(value))) }.getOrDefault(false)
    }

    private fun encode(s: AtomBlocksEngine.Snapshot) = JSONObject().apply {
        put("version", s.version); put("mode", s.mode.name); put("level", s.level); put("seed", s.seed)
        put("adventureVersion", s.adventureVersion)
        put("state", encodeFrame(s.state))
        put("history", JSONArray().apply { s.history.forEach { put(encodeFrame(it)) } })
    }

    private fun encodeFrame(f: AtomBlocksEngine.Frame) = JSONObject().apply {
        put("board", JSONArray(f.board.toList())); put("gems", JSONArray(f.gems.toList()))
        put("tray", JSONArray().apply {
            f.tray.forEach { p -> put(p?.let { JSONObject().put("shape", it.shapeId).put("color", it.color) } ?: JSONObject.NULL) }
        })
        put("score", f.score); put("combo", f.combo); put("lines", f.lines); put("moves", f.moves)
        put("collected", f.collectedGems); put("misses", f.nonClearingMoves)
        put("won", f.won); put("random", f.randomState)
    }

    private fun decode(o: JSONObject): AtomBlocksEngine.Snapshot {
        val history = o.getJSONArray("history")
        require(history.length() <= 200)
        val version = o.getInt("version")
        val mode = AtomBlocksEngine.Mode.valueOf(o.getString("mode"))
        val seed = o.getLong("seed")
        if (mode == AtomBlocksEngine.Mode.DAILY) java.time.LocalDate.ofEpochDay(seed)
        return AtomBlocksEngine.Snapshot(
            version = version, mode = mode,
            adventureVersion = if (version == 1) 1 else o.getInt("adventureVersion"),
            level = o.getInt("level"), seed = seed, state = decodeFrame(o.getJSONObject("state")),
            history = List(history.length()) { decodeFrame(history.getJSONObject(it)) }
        )
    }

    private fun decodeFrame(o: JSONObject): AtomBlocksEngine.Frame {
        fun cells(name: String): IntArray {
            val values = o.getJSONArray(name)
            require(values.length() == 64)
            return IntArray(64) { values.getInt(it) }
        }
        val tray = o.getJSONArray("tray")
        require(tray.length() == 3)
        return AtomBlocksEngine.Frame(
            board = cells("board"), gems = cells("gems"),
            tray = List(3) { i -> if (tray.isNull(i)) null else tray.getJSONObject(i).let {
                AtomBlocksEngine.Piece(it.getString("shape"), it.getInt("color"))
            } },
            score = o.getInt("score"), combo = o.getInt("combo"), lines = o.getInt("lines"),
            moves = o.getInt("moves"), collectedGems = o.getInt("collected"),
            nonClearingMoves = o.getInt("misses"), won = o.getBoolean("won"), randomState = o.getLong("random")
        )
    }
}
