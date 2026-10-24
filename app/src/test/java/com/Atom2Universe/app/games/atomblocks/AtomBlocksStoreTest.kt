package com.Atom2Universe.app.games.atomblocks

import android.app.Application
import com.Atom2Universe.app.games.atomblocks.AtomBlocksEngine.Mode
import com.Atom2Universe.app.games.atomblocks.AtomBlocksEngine.Piece
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class AtomBlocksStoreTest {
    private lateinit var store: AtomBlocksStore
    private val single = Piece("single", 2)

    @Before fun clearStore() {
        store = AtomBlocksStore(RuntimeEnvironment.getApplication())
        assertTrue(store.prefs.edit().clear().commit())
    }

    @Test fun roundTripPreservesThePositionUndoHistoryAndNextRandomDeal() {
        val original = AtomBlocksEngine(42L)
        val initial = original.snapshot()
        assertTrue(original.restore(initial.copy(state = initial.state.copy(tray = List(3) { single }))))
        assertNotNull(original.place(0, 3, 3))
        assertNotNull(original.place(1, 3, 4))
        val beforeRefill = original.snapshot()
        store.save(original)

        val restored = AtomBlocksEngine(999L)
        val reopened = AtomBlocksStore(RuntimeEnvironment.getApplication())
        assertTrue(reopened.load(reopened.lastKey()!!, restored))
        assertSnapshotEquals(beforeRefill, restored.snapshot())
        assertTrue(restored.canUndo)

        // Using the last piece crosses a random-deal boundary after loading JSON.
        assertEquals(original.place(2, 3, 5), restored.place(2, 3, 5))
        val afterRefill = original.snapshot()
        assertSnapshotEquals(afterRefill, restored.snapshot())
        assertTrue(restored.undo())
        assertSnapshotEquals(beforeRefill, restored.snapshot())
        assertNotNull(restored.place(2, 3, 5))
        assertSnapshotEquals(afterRefill, restored.snapshot())

        // Every historical frame, not just the current board, survives persistence.
        repeat(3) {
            assertTrue(original.undo())
            assertTrue(restored.undo())
            assertSnapshotEquals(original.snapshot(), restored.snapshot())
        }
        assertFalse(restored.canUndo)
    }

    @Test fun modesAdventureLevelsAndDailyDatesKeepIndependentCheckpoints() {
        val cases = listOf(
            Triple(Mode.CLASSIC, 1, 101L), Triple(Mode.ZEN, 1, 202L),
            Triple(Mode.ADVENTURE, 1, 1000L), Triple(Mode.ADVENTURE, 3, 1002L),
            Triple(Mode.DAILY, 1, -3L), Triple(Mode.DAILY, 1, 20749L),
        )
        val expected = linkedMapOf<String, AtomBlocksEngine.Snapshot>()
        cases.forEach { (mode, level, seed) ->
            val game = AtomBlocksEngine().apply { newGame(mode, level, seed) }
            val move = game.hint()!!
            assertNotNull(game.place(move.slot, move.row, move.col))
            store.save(game)
            expected[store.key(mode, level, seed)] = game.snapshot()
        }
        assertEquals(cases.size, expected.size)
        assertEquals(expected.keys.last(), store.lastKey())
        val reopened = AtomBlocksStore(RuntimeEnvironment.getApplication())
        expected.forEach { (key, snapshot) ->
            val restored = AtomBlocksEngine()
            assertTrue(key, reopened.load(key, restored))
            assertSnapshotEquals(snapshot, restored.snapshot())
        }

        // Starting a new classic run must not replace any other mode's checkpoint.
        reopened.save(AtomBlocksEngine(909L))
        expected.filterKeys { it != "classic" }.forEach { (key, snapshot) ->
            val restored = AtomBlocksEngine()
            assertTrue(reopened.load(key, restored))
            assertSnapshotEquals(snapshot, restored.snapshot())
        }
        assertEquals("classic", reopened.lastKey())
    }

    @Test fun earnedRecordAndAdventureCompletionSurviveUndoAndRestart() {
        val game = AtomBlocksEngine().apply { newGame(Mode.ADVENTURE, 1, 1000L) }
        val goal = game.goal!!
        val before = game.snapshot()
        val rowToClear = IntArray(64).also { cells -> for (col in 0..6) cells[col] = 1 }
        assertTrue(game.restore(before.copy(state = before.state.copy(
            board = rowToClear, gems = IntArray(64), tray = listOf(single, null, null),
            score = goal.score, lines = goal.lines - 1,
        ))))
        assertTrue(game.place(0, 0, 7)!!.won)
        val earnedScore = game.score
        store.save(game)

        assertTrue(game.undo())
        assertFalse(game.won)
        assertTrue(game.score < earnedScore)
        store.save(game)
        val reopened = AtomBlocksStore(RuntimeEnvironment.getApplication())
        assertEquals(earnedScore, reopened.best(Mode.ADVENTURE))
        assertTrue(reopened.completed(1))
        assertFalse(reopened.completed(2))
        assertEquals(0, reopened.best(Mode.CLASSIC))
        val restored = AtomBlocksEngine()
        assertTrue(reopened.load("adventure_1", restored))
        assertSnapshotEquals(game.snapshot(), restored.snapshot())
        assertFalse(restored.won)

        game.newGame(Mode.ADVENTURE, 1, 1000L)
        reopened.save(game)
        assertTrue(reopened.completed(1))
        assertEquals(earnedScore, reopened.best(Mode.ADVENTURE))
    }

    @Test fun legacyAdventureObjectivesAndUndoSurviveMigrationAndResaving() {
        for (finished in listOf(false, true)) {
            // A v1 payload has no adventureVersion: its level 1 goal was 415 points / 2 lines.
            assertTrue(store.prefs.edit().putString("save_adventure_1", legacyAdventureSave(finished).toString()).commit())
            val restored = AtomBlocksEngine()
            assertTrue(store.load("adventure_1", restored))
            assertEquals(AtomBlocksEngine.Goal(415, 2, 0), restored.goal)
            assertEquals(finished, restored.won)
            assertEquals(finished, restored.canUndo)
            assertEquals(2, restored.snapshot().version)
            assertEquals(1, restored.snapshot().adventureVersion)
            val migrated = restored.snapshot()

            store.save(restored)
            val encoded = JSONObject(store.prefs.getString("save_adventure_1", null)!!)
            assertEquals(2, encoded.getInt("version"))
            assertEquals(1, encoded.getInt("adventureVersion"))
            val reopened = AtomBlocksEngine()
            assertTrue(AtomBlocksStore(RuntimeEnvironment.getApplication()).load("adventure_1", reopened))
            assertSnapshotEquals(migrated, reopened.snapshot())
            assertEquals(AtomBlocksEngine.Goal(415, 2, 0), reopened.goal)
            if (finished) {
                assertTrue(reopened.undo())
                assertFalse(reopened.won)
                assertEquals(310, reopened.score)
                assertEquals(1, reopened.lines)
                assertEquals(AtomBlocksEngine.Goal(415, 2, 0), reopened.goal)
                assertTrue(reopened.place(0, 0, 7)!!.won)
                assertEquals(415, reopened.score)
            }
            reopened.newGame(Mode.ADVENTURE, 1, 1000L)
            assertEquals(2, reopened.snapshot().adventureVersion)
        }
    }

    @Test fun malformedOrInvalidSaveLeavesTheLiveGameAndHistoryUntouched() {
        val saved = AtomBlocksEngine(81L)
        val move = saved.hint()!!
        assertNotNull(saved.place(move.slot, move.row, move.col))
        store.save(saved)
        val key = store.lastKey()!!
        val validJson = store.prefs.getString("save_$key", null)!!
        val live = AtomBlocksEngine().apply { newGame(Mode.ZEN, seed = 91L) }
        assertTrue(live.rescue())
        val unchanged = live.snapshot()
        val broken = listOf(
            "{incomplete",
            JSONObject(validJson).put("version", 99).toString(),
            JSONObject(validJson).apply { remove("adventureVersion") }.toString(),
            JSONObject(validJson).put("adventureVersion", 99).toString(),
            JSONObject(validJson).apply { getJSONObject("state").put("score", -1) }.toString(),
            JSONObject(validJson).apply { getJSONObject("state").put("board", JSONArray(listOf(1))) }.toString(),
            JSONObject(validJson).apply {
                getJSONArray("history").getJSONObject(0).getJSONArray("tray").getJSONObject(0)
                    .put("shape", "unknown_shape")
            }.toString(),
        )
        broken.forEach { json ->
            assertTrue(store.prefs.edit().putString("save_$key", json).commit())
            assertFalse(store.load(key, live))
            assertSnapshotEquals(unchanged, live.snapshot())
        }
        assertFalse(store.load("missing_checkpoint", live))
        assertSnapshotEquals(unchanged, live.snapshot())

        assertTrue(store.prefs.edit().putString("save_$key", validJson).commit())
        assertTrue(store.load(key, live))
        assertSnapshotEquals(saved.snapshot(), live.snapshot())
    }

    /** Frozen legacy JSON: one single clears a row for 105 points, leaving another cell occupied. */
    private fun legacyAdventureSave(finished: Boolean): JSONObject {
        val before = IntArray(64).also { cells ->
            for (col in 0..6) cells[col] = 1
            cells[9] = 2
        }
        val after = IntArray(64).also { it[9] = 2 }
        fun frame(won: Boolean) = JSONObject().apply {
            put("board", JSONArray((if (won) after else before).toList()))
            put("gems", JSONArray(List(64) { 0 }))
            put("tray", JSONArray().apply {
                put(if (won) JSONObject.NULL else JSONObject().put("shape", "single").put("color", 2))
                put(JSONObject.NULL); put(JSONObject.NULL)
            })
            put("score", if (won) 415 else 310); put("combo", if (won) 1 else 0)
            put("lines", if (won) 2 else 1); put("moves", if (won) 9 else 8)
            put("collected", 0); put("misses", 0); put("won", won); put("random", 123456L)
        }
        return JSONObject().apply {
            put("version", 1); put("mode", "ADVENTURE"); put("level", 1); put("seed", 1000L)
            put("state", frame(finished))
            put("history", JSONArray().apply { if (finished) put(frame(false)) })
        }
    }

    private fun assertSnapshotEquals(expected: AtomBlocksEngine.Snapshot, actual: AtomBlocksEngine.Snapshot) {
        assertEquals(expected.version, actual.version)
        assertEquals(expected.adventureVersion, actual.adventureVersion)
        assertEquals(expected.mode, actual.mode)
        assertEquals(expected.level, actual.level)
        assertEquals(expected.seed, actual.seed)
        assertFrameEquals(expected.state, actual.state)
        assertEquals(expected.history.size, actual.history.size)
        expected.history.zip(actual.history).forEach { (a, b) -> assertFrameEquals(a, b) }
    }

    private fun assertFrameEquals(expected: AtomBlocksEngine.Frame, actual: AtomBlocksEngine.Frame) {
        assertArrayEquals(expected.board, actual.board)
        assertArrayEquals(expected.gems, actual.gems)
        assertEquals(expected.copy(board = actual.board, gems = actual.gems), actual)
    }
}
