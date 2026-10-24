package com.Atom2Universe.app.games.mahjong

import org.json.JSONArray
import org.json.JSONObject
import java.util.IdentityHashMap
import java.util.concurrent.CancellationException

/** Persist the undo stack too: leaving the app never consumes a free recovery. */
internal object MahjongSave {
    private fun write(state: MahjongSnapshot) = JSONObject().apply {
        put("tiles", JSONArray().apply { state.tiles.forEach { tile ->
            put(JSONArray(listOf(tile.id, tile.x, tile.y, tile.z, tile.face, if (tile.removed) 1 else 0)))
        } })
        put("tray", JSONArray(state.tray)); put("revealed", JSONArray(state.revealed))
        put("score", state.score); put("combo", state.combo); put("matches", state.matches)
        put("moves", state.moves); put("assists", state.assists)
        put("peakCombo", state.peakCombo)
        state.focusId?.let { put("focusId", it) }
        state.focusOrigin?.let { put("focusOrigin", JSONArray(listOf(it.x, it.y))) }
    }

    private fun read(json: JSONObject, game: MahjongGame, legacyMemory: Boolean = false): MahjongSnapshot {
        val data = json.getJSONArray("tiles")
        // Layouts evolve between versions. Persisted tile IDs and coordinates, rather than
        // the current generator's tile count, define a saved board and its undo history.
        require(data.length() in 2..256)
        require(data.length() % (if (game.mode == MahjongMode.TRIPLE) 3 else 2) == 0)
        val tiles = List(data.length()) { i ->
            val tile = data.getJSONArray(i)
            require(tile.length() == 6 && tile.getInt(0) == i && tile.getInt(5) in 0..1)
            MahjongTile(i, tile.getInt(1), tile.getInt(2), tile.getInt(3), tile.getInt(4), tile.getInt(5) == 1).also {
                val maxLayer = if (game.mode == MahjongMode.TRIPLE) 3 else 2
                require(it.x in 0 until game.columns && it.y in 0 until game.rows && it.z in 0..maxLayer && it.face in 0..26)
                require(game.mode == MahjongMode.CLASSIC || game.mode == MahjongMode.TRIPLE || it.z == 0)
            }
        }
        require(game.mode != MahjongMode.SLIDE || legacyMemory || tiles.all { it.removed } || data.length() <= game.rows * 4)
        fun ids(key: String) = json.getJSONArray(key).let { a -> List(a.length()) { a.getInt(it) } }.also {
            require(it.distinct().size == it.size && it.all { id -> id in tiles.indices && !tiles[id].removed })
        }
        val tray = ids("tray"); val revealed = ids("revealed")
        require(tray.size <= 7 && revealed.size <= 2)
        require(game.mode == MahjongMode.TRIPLE || tray.isEmpty())
        require(legacyMemory || revealed.isEmpty())
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
        return MahjongSnapshot(tiles, tray, revealed, json.getInt("score").coerceAtLeast(0),
            json.getInt("combo").coerceIn(0, 8), json.getInt("matches").coerceAtLeast(0),
            json.getInt("moves").coerceAtLeast(0), json.getInt("assists").coerceAtLeast(0),
            json.optInt("peakCombo", json.optInt("combo")).coerceIn(0, 8),
            focusId = focusId, focusOrigin = focusOrigin)
    }

    private fun document(game: MahjongGame, journey: Boolean, daily: Boolean, day: Long,
                         id: String, reward: MahjongReward?, bonusMultiplier: Int) = JSONObject().apply {
        put("version", 4); put("mode", game.mode.name); put("seed", game.seed); put("stage", game.stage)
        put("journey", journey); put("daily", daily); put("day", day)
        put("id", id)
        put("bonusMultiplier", bonusMultiplier)
        reward?.let { put("reward", JSONObject().put("pearls", it.pearls).put("styles", JSONArray(it.styles))
            .put("gift", it.gift).put("comboBonus", it.comboBonus)) }
        put("state", write(game.snapshot()))
    }

    fun encode(game: MahjongGame, journey: Boolean, daily: Boolean, day: Long,
               id: String = "legacy:${game.mode}:${game.seed}:${game.stage}:$journey:$daily:$day",
               reward: MahjongReward? = null, bonusMultiplier: Int = 1): String =
        document(game, journey, daily, day, id, reward, bonusMultiplier).apply {
            put("history", JSONArray().apply { game.history.forEach { put(write(it)) } })
        }.toString()

    /** Reuse serialized undo entries: the engine never mutates a stored snapshot.
     * Identity keys avoid hashing every tile, and dropped entries are released on each save.
     * The live state and receipt are always encoded again; the on-disk schema is unchanged.
     */
    class Encoder {
        private var history = IdentityHashMap<MahjongSnapshot, String>()

        @Synchronized fun encode(run: Saved): String {
            val next = IdentityHashMap<MahjongSnapshot, String>(run.game.history.size)
            val head = document(run.game, run.journey, run.daily, run.day,
                run.id, run.reward, run.bonusMultiplier).toString()
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
                     val bonusMultiplier: Int = 1, var iceMigration: Boolean = false) {
        val slot get() = when { journey -> "journey"; daily -> "daily"; else -> game.mode.name }
    }
    fun encode(run: Saved) = encode(run.game, run.journey, run.daily, run.day, run.id, run.reward, run.bonusMultiplier)
    fun decode(raw: String): Saved? = runCatching {
        val json = JSONObject(raw)
        val version = json.getInt("version"); require(version in 1..4)
        val stage = json.getInt("stage"); require(stage in 1..1_000_000)
        val journey = json.getBoolean("journey"); val daily = json.getBoolean("daily")
        require(!journey || !daily)
        val savedMode = json.getString("mode")
        val oldMemory = savedMode == "MEMORY"
        // A removed free-play mode has no successor slot. Journey/daily keep their identity,
        // seed and progress, but start a fresh slide puzzle instead of reinterpreting cards.
        require(!oldMemory || (version <= 2 && (journey || daily)))
        val mode = if (oldMemory) MahjongMode.SLIDE else MahjongMode.valueOf(savedMode)
        // Saved coordinates are authoritative; rebuilding a fresh puzzle here would be
        // wasted work on every menu card, resume and reward receipt check.
        val game = MahjongGame(mode, json.getInt("seed"), stage, initializeBoard = oldMemory)
        val state = read(json.getJSONObject("state"), game, oldMemory)
        val migrateIce = !oldMemory && mode == MahjongMode.SLIDE && version < 4 && state.tiles.any { !it.removed }
        val history = json.getJSONArray("history"); require(history.length() <= 2048)
        val states = if (oldMemory || migrateIce) emptyList() else buildList {
            for (index in 0 until history.length()) {
                val entry = read(history.getJSONObject(index), game)
                require(entry.tiles.size == state.tiles.size)
                // Validate legacy entries too, but retain only the engine's undo limit.
                if (index >= history.length() - 256) add(entry)
            }
        }
        if (!oldMemory || state.tiles.all { it.removed }) {
            game.restore(state.copy(revealed = emptyList())); game.history.addAll(states)
        }
        if (migrateIce) {
            // Old free-stop layouts have no guaranteed finish under the ice rules.
            // Rebuild only the remaining tiles, without charging an assist or losing
            // score/progress. Undo must not restore the incompatible old layout.
            game.shuffle()
            game.restore(game.snapshot().copy(assists = state.assists))
            game.history.clear()
        }
        val reward = json.optJSONObject("reward")?.let { r ->
            val styles = r.getJSONArray("styles")
            MahjongReward(r.getInt("pearls"), List(styles.length()) { styles.getInt(it) }, r.getBoolean("gift"), r.getInt("comboBonus"))
        } ?: if (version == 1 && game.complete) MahjongReward(0, emptyList(), false, 0) else null
        require(reward == null || game.complete)
        Saved(game, journey, daily, json.getLong("day"),
            json.optString("id", "legacy:$savedMode:${game.seed}:${game.stage}"), reward,
            json.optInt("bonusMultiplier",1).coerceIn(1,2), iceMigration = migrateIce)
    }.getOrElse { error ->
        if (error is CancellationException) throw error
        null
    }
}
