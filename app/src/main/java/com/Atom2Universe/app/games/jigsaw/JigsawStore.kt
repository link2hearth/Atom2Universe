package com.Atom2Universe.app.games.jigsaw

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class JigsawStore(context: Context) {
    private val prefs = context.getSharedPreferences("jigsaw", Context.MODE_PRIVATE)

    fun save(game: JigsawGame, synchronous: Boolean = false) {
        val editor = prefs.edit().putString("save", JigsawCodec.encode(game))
        if (synchronous) editor.commit() else editor.apply()
    }

    fun load(): JigsawGame? = prefs.getString("save", null)?.let { JigsawCodec.decode(it) }

    /** A save whose image can no longer be read is useless: it is dropped. */
    fun clear() { prefs.edit().remove("save").apply() }

    fun completed(path: String) = path in prefs.getStringSet("completed", emptySet()).orEmpty()
    fun markCompleted(path: String) {
        if (completed(path)) return
        val set = prefs.getStringSet("completed", emptySet()).orEmpty().toMutableSet().apply { add(path) }
        prefs.edit().putStringSet("completed", set).apply()
    }
}

/** The save format can be checked on the JVM, separately from Android preferences. */
object JigsawCodec {
    private const val VERSION = 4

    fun encode(game: JigsawGame): String {
        val data = JSONObject().put("version", VERSION).put("image", game.imagePath).put("size", game.size.name)
            .put("columns", game.grid.columns).put("rows", game.grid.rows)
            .put("width", game.grid.width.toDouble()).put("height", game.grid.height.toDouble())
            .put("seed", game.seed).put("rotation", game.rotation).put("session", game.sessionId)
            .put("layout", game.layout.name)
            .put("elapsed", game.elapsedMs).put("rewardHandled", game.rewardHandled)
            .put("guideUnlocked", game.guideUnlocked).put("guideVisible", game.guideVisible)
        data.put("groups", JSONArray().also { array -> game.groups.forEach { g ->
            array.put(JSONObject().put("ids", JSONArray(g.ids)).put("x", g.offset.x.toDouble())
                .put("y", g.offset.y.toDouble()).put("turns", g.turns).put("tray", g.inTray).put("locked", g.locked))
        } })
        return data.toString()
    }

    fun decode(text: String): JigsawGame? = runCatching {
        val json = JSONObject(text)
        require(json.getInt("version") == VERSION)
        val grid = JigsawGrid(json.getInt("columns"), json.getInt("rows"),
            json.getDouble("width").toFloat(), json.getDouble("height").toFloat())
        val game = JigsawGame(json.getString("image"), JigsawSize.valueOf(json.getString("size")),
            json.getInt("seed"), json.getBoolean("rotation"), json.getString("session"),
            JigsawLayout.valueOf(json.getString("layout")), grid)
        require(game.sessionId.matches(Regex("[a-fA-F0-9-]{36}")))
        game.elapsedMs = json.getLong("elapsed")
        game.rewardHandled = json.getBoolean("rewardHandled")
        game.guideUnlocked = json.getBoolean("guideUnlocked")
        game.guideVisible = json.getBoolean("guideVisible")
        game.groups.clear()
        val groups = json.getJSONArray("groups")
        for (i in 0 until groups.length()) {
            val g = groups.getJSONObject(i); val ids = g.getJSONArray("ids")
            game.groups += PieceGroup((0 until ids.length()).map { ids.getInt(it) }.toMutableList(),
                PuzzlePoint(g.getDouble("x").toFloat(), g.getDouble("y").toFloat()), g.getInt("turns"),
                g.getBoolean("tray"), g.getBoolean("locked"))
        }
        require(game.valid())
        game
    }.getOrNull()
}
