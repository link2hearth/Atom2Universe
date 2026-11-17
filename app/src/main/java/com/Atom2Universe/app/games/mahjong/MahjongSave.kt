package com.Atom2Universe.app.games.mahjong

import org.json.JSONArray
import org.json.JSONObject
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

/** Persist the undo stack too: leaving the app never consumes a free recovery. */
internal object MahjongSave {
    const val VERSION = 5

    private fun write(state: MahjongSnapshot) = JSONObject().apply {
        put("tiles", JSONArray().apply { state.tiles.forEach { tile ->
            put(JSONArray(listOf(tile.id, tile.x, tile.y, tile.z, tile.face, if (tile.removed) 1 else 0)))
        } })
        put("tray", JSONArray(state.tray))
        put("score", state.score); put("combo", state.combo); put("matches", state.matches)
        put("moves", state.moves); put("assists", state.assists)
        put("peakCombo", state.peakCombo)
        state.focusId?.let { put("focusId", it) }
        state.focusOrigin?.let { put("focusOrigin", JSONArray(listOf(it.x, it.y))) }
    }

    private fun read(json: JSONObject, game: MahjongGame): MahjongSnapshot {
        val data = json.getJSONArray("tiles")
        require(data.length() in 2..256)
        require(data.length() % game.groupSize == 0)
        val tiles = List(data.length()) { i ->
            val tile = data.getJSONArray(i)
            require(tile.length() == 6 && tile.getInt(0) == i && tile.getInt(5) in 0..1)
            MahjongTile(i, tile.getInt(1), tile.getInt(2), tile.getInt(3), tile.getInt(4), tile.getInt(5) == 1).also {
                require(it.face in 0 until MahjongFaces.COUNT)
                if (game.layered) require(it.x in 0..64 && it.y in 0..64 && it.z in 0..7)
                else require(it.x in 0 until game.columns && it.y in 0 until game.rows && it.z == 0)
            }
        }
        fun ids(key: String) = json.getJSONArray(key).let { a -> List(a.length()) { a.getInt(it) } }.also {
            require(it.distinct().size == it.size && it.all { id -> id in tiles.indices && !tiles[id].removed })
        }
        val tray = ids("tray")
        require(tray.size <= MahjongGame.TRAY)
        require(game.mode == MahjongMode.TRIPLE || tray.isEmpty())
        val board = tiles.filter { !it.removed && it.id !in tray }
        require(board.map { Triple(it.x, it.y, it.z) }.distinct().size == board.size)
        val focusId = if (json.has("focusId") && !json.isNull("focusId")) json.getInt("focusId") else null
        val focusOrigin = if (json.has("focusOrigin") && !json.isNull("focusOrigin")) {
            val origin = json.getJSONArray("focusOrigin")
            require(origin.length() == 2)
            MahjongCell(origin.getInt(0), origin.getInt(1))
        } else null
        require((focusId == null) == (focusOrigin == null))
        if (focusId != null && focusOrigin != null) {
            require(game.mode == MahjongMode.SLIDE)
            require(focusId in tiles.indices && !tiles[focusId].removed && focusId !in tray && tiles[focusId].z == 0)
            require(focusOrigin.x in 0 until game.columns && focusOrigin.y in 0 until game.rows)
            require(board.none { it.id != focusId && it.x == focusOrigin.x && it.y == focusOrigin.y })
        }
        return MahjongSnapshot(tiles, tray, json.getInt("score").coerceAtLeast(0),
            json.getInt("combo").coerceIn(0, MahjongGame.MAX_CHAIN), json.getInt("matches").coerceAtLeast(0),
            json.getInt("moves").coerceAtLeast(0), json.getInt("assists").coerceAtLeast(0),
            json.optInt("peakCombo", json.optInt("combo")).coerceIn(0, MahjongGame.MAX_CHAIN),
            focusId = focusId, focusOrigin = focusOrigin)
    }

    private fun document(run: Saved) = JSONObject().apply {
        val game = run.game
        put("version", VERSION); put("mode", game.mode.name); put("seed", game.seed); put("stage", game.stage)
        put("journey", run.journey); put("daily", run.daily); put("day", run.day)
        put("id", run.id)
        put("bonusMultiplier", run.bonusMultiplier)
        put("hintsUsed", game.hintsUsed); put("shufflesUsed", game.shufflesUsed)
        run.reward?.let { put("reward", JSONObject().put("pearls", it.pearls).put("styles", JSONArray(it.styles))
            .put("gift", it.gift).put("stars", it.stars)) }
        put("state", write(game.snapshot()))
    }

    /** Reuse serialized undo entries: the engine never mutates a stored snapshot.
     * Identity keys avoid hashing every tile, and dropped entries are released on each save.
     */
    class Encoder {
        private var history = IdentityHashMap<MahjongSnapshot, String>()

        @Synchronized fun encode(run: Saved): String {
            val next = IdentityHashMap<MahjongSnapshot, String>(run.game.history.size)
            val head = document(run).toString()
            val encoded = buildString {
                append(head, 0, head.length - 1)
                append(",\"history\":[")
                run.game.history.forEachIndexed { index, state ->
                    if (index > 0) append(',')
                    val entry = history[state] ?: write(state).toString()
                    next[state] = entry
                    append(entry)
                }
                append("]}")
            }
            history = next
            return encoded
        }
    }

    data class Saved(val game: MahjongGame, val journey: Boolean, val daily: Boolean, val day: Long,
                     val id: String = java.util.UUID.randomUUID().toString(), var reward: MahjongReward? = null,
                     val bonusMultiplier: Int = 1) {
        val slot get() = when { journey -> "journey"; daily -> "daily"; else -> game.mode.name }
    }

    fun encode(run: Saved): String = document(run).apply {
        put("history", JSONArray().apply { run.game.history.forEach { put(write(it)) } })
    }.toString()

    /** Les anciennes sauvegardes (avant les vraies tuiles) sont simplement ignorées. */
    fun decode(raw: String): Saved? = runCatching {
        val json = JSONObject(raw)
        require(json.getInt("version") == VERSION)
        val stage = json.getInt("stage"); require(stage in 1..1_000_000)
        val journey = json.getBoolean("journey"); val daily = json.getBoolean("daily")
        require(!journey || !daily)
        val mode = MahjongMode.valueOf(json.getString("mode"))
        val game = MahjongGame(mode, json.getInt("seed"), stage, initializeBoard = false)
        val state = read(json.getJSONObject("state"), game)
        val history = json.getJSONArray("history"); require(history.length() <= 2048)
        val states = buildList {
            for (index in 0 until history.length()) {
                val entry = read(history.getJSONObject(index), game)
                require(entry.tiles.size == state.tiles.size)
                if (index >= history.length() - 256) add(entry)
            }
        }
        game.restore(state); game.history.addAll(states)
        game.hintsUsed = json.optInt("hintsUsed", 0).coerceIn(0, MahjongGame.HINTS)
        game.shufflesUsed = json.optInt("shufflesUsed", 0).coerceIn(0, MahjongGame.SHUFFLES)
        val reward = json.optJSONObject("reward")?.let { r ->
            val styles = r.getJSONArray("styles")
            MahjongReward(r.getInt("pearls"), List(styles.length()) { styles.getInt(it) }, r.getBoolean("gift"), r.optInt("stars", 1).coerceIn(1, 3))
        }
        require(reward == null || game.complete)
        Saved(game, journey, daily, json.getLong("day"), json.optString("id", "legacy:${mode.name}:${game.seed}:${game.stage}"),
            reward, json.optInt("bonusMultiplier", 1).coerceIn(1, 2))
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        null
    }
}
