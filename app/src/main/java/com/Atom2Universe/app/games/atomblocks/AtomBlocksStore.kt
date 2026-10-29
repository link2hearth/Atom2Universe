package com.Atom2Universe.app.games.atomblocks

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/** Independent checkpoints per mode/level; only the most recently played daily puzzles are kept. */
internal class AtomBlocksStore(context: Context) {
    val prefs = context.getSharedPreferences("atom_blocks", Context.MODE_PRIVATE)

    companion object {
        private const val DAILY_KEPT = 30
        // One writer for every instance: a recreated activity must read what the old one queued.
        private val writer = Executors.newSingleThreadExecutor { Thread(it, "AtomBlocksSave").apply { isDaemon = true } }
    }

    fun key(mode: AtomBlocksEngine.Mode, level: Int, seed: Long) = when (mode) {
        AtomBlocksEngine.Mode.ADVENTURE -> "adventure_$level"
        AtomBlocksEngine.Mode.DAILY -> "daily_$seed"
        else -> mode.name.lowercase(java.util.Locale.ROOT)
    }

    fun save(game: AtomBlocksEngine) = write(game.snapshot())

    /** The copy is taken now; encoding the 200 undo frames happens off the UI thread. */
    fun saveLater(game: AtomBlocksEngine) {
        val snapshot = game.snapshot()
        writer.execute { write(snapshot) }
    }

    /** Waits for queued saves, so a read never sees an older checkpoint. */
    fun flush() { runCatching { writer.submit(Runnable {}).get() } }

    private fun write(snapshot: AtomBlocksEngine.Snapshot) {
        val key = key(snapshot.mode, snapshot.level, snapshot.seed)
        val editor = prefs.edit().putString("save_$key", encode(snapshot).toString())
            .putString("last", key)
            .putInt("best_${snapshot.mode.name}", maxOf(best(snapshot.mode), snapshot.state.score))
        if (snapshot.state.won) editor.putBoolean("complete_$key", true)
        if (snapshot.mode == AtomBlocksEngine.Mode.DAILY) forgetOldDailies(editor, key)
        editor.apply()
    }

    private fun forgetOldDailies(editor: android.content.SharedPreferences.Editor, current: String) {
        val recent = prefs.getString("daily_recent", null)?.split(',')?.filter { it.isNotEmpty() && it != current }.orEmpty()
        val kept = (listOf(current) + recent).take(DAILY_KEPT)
        editor.putString("daily_recent", kept.joinToString(","))
        for (name in prefs.all.keys) {
            if (!name.startsWith("save_daily_") && !name.startsWith("complete_daily_")) continue
            if (name.substringAfter('_') !in kept) editor.remove(name)
        }
    }

    fun best(mode: AtomBlocksEngine.Mode) = prefs.getInt("best_${mode.name}", 0)
    fun completed(level: Int) = prefs.getBoolean("complete_adventure_$level", false)
    fun lastKey(): String? { flush(); return prefs.getString("last", null) }

    fun load(key: String, game: AtomBlocksEngine): Boolean {
        flush()
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
