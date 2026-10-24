package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.CancellationException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MahjongSaveTest {
    @Test fun savesRestoreBoardTrayAndUndoForEveryMode() {
        for (mode in MahjongMode.entries) {
            val game = MahjongGame(mode, 914, 5)
            if (mode == MahjongMode.SLIDE) slideOneTile(game) else {
                val ids = if (mode == MahjongMode.TRIPLE) game.solution.first() else game.hint()
                ids.forEach { game.tap(it) }
            }
            if (mode == MahjongMode.TRIPLE) game.tap(game.board.first { game.isFree(it) }.id)
            val encoded = MahjongSave.encode(game, true, false, 213L)
            val restored = MahjongSave.decode(encoded)
            assertNotNull(mode.name, restored)
            assertEquals(game.snapshot(), restored!!.game.snapshot())
            assertEquals(game.history, restored.game.history)
            assertTrue(restored.journey); assertFalse(restored.daily); assertEquals(213L, restored.day)
            assertTrue(restored.game.undo())
            assertEquals(game.history.last().tiles, restored.game.tiles)
        }
    }

    @Test fun malformedSavesFailWithoutCrashing() {
        for (raw in listOf("", "{}", "{bad json}", "{\"version\":99}")) assertNull(MahjongSave.decode(raw))
        val game = MahjongGame(MahjongMode.CLASSIC, 44)
        val json = org.json.JSONObject(MahjongSave.encode(game, false, false, 0L))
        json.getJSONObject("state").getJSONArray("tiles").getJSONArray(0).put(4, 700)
        assertNull(MahjongSave.decode(json.toString()))
        for (mode in listOf(MahjongMode.CLASSIC, MahjongMode.TRIPLE)) {
            val layered = MahjongGame(mode, 914, 5)
            val raw = JSONObject(MahjongSave.encode(layered, false, false, 0L))
            assertNotNull(MahjongSave.decode(raw.toString()))
            raw.getJSONObject("state").getJSONArray("tiles").getJSONArray(0)
                .put(3, if (mode == MahjongMode.TRIPLE) 4 else 3)
            assertNull("Impossible layer must still be rejected: $mode", MahjongSave.decode(raw.toString()))
        }
    }

    @Test fun legacyFlatBoardsKeepTheirTileIdsAndUndoDespiteNewLayeredLayouts() {
        for (mode in listOf(MahjongMode.CLASSIC, MahjongMode.TRIPLE)) {
            val game = MahjongGame(mode, 44, 1)
            val oldTiles = List(36) { id -> MahjongTile(id, id % 6, id / 6, 0, id / 6) }
            val before = game.snapshot().copy(tiles = oldTiles, tray = emptyList(), revealed = emptyList())
            val groupSize = if (mode == MahjongMode.TRIPLE) 3 else 2
            game.restore(before.copy(tiles = oldTiles.map { it.copy(removed = it.id < groupSize) },
                score = 100, moves = 1, matches = 1))
            game.history += before
            val raw = JSONObject(MahjongSave.encode(game, false, false, 0L)).put("version", 2)
            val restored = MahjongSave.decode(raw.toString())!!.game
            assertEquals(mode.name, game.snapshot(), restored.snapshot())
            assertTrue(restored.undo())
            assertEquals(oldTiles, restored.tiles)
        }
    }

    @Test fun unfinishedLegacyMemoryRestartsOnlyItsJourneyPuzzleAsSlide() {
        val game = MahjongGame(MahjongMode.CLASSIC, 314, 5)
        val json = JSONObject(MahjongSave.encode(game, true, false, 41L, id = "old-memory"))
            .put("version", 2).put("mode", "MEMORY")
        json.getJSONObject("state").put("tiles", JSONArray().apply {
            repeat(36) { id -> put(JSONArray(listOf(id, id % 6, id / 6, 0, id / 2, 0))) }
        }).put("revealed", JSONArray(listOf(0, 1)))
        val restored = MahjongSave.decode(json.toString())!!
        assertEquals(MahjongMode.SLIDE, restored.game.mode)
        assertEquals("old-memory", restored.id)
        assertEquals(5, restored.game.stage); assertEquals(314, restored.game.seed)
        assertTrue(restored.journey); assertEquals(41L, restored.day)
        assertEquals(MahjongGame(MahjongMode.SLIDE, 314, 5).snapshot(), restored.game.snapshot())
        assertTrue(restored.game.history.isEmpty())
        assertEquals(4, JSONObject(MahjongSave.encode(restored)).getInt("version"))
        assertNotNull(MahjongSave.decode(MahjongSave.encode(restored)))

        // A free Memory slot must never silently appear as a saved Glissade puzzle.
        json.put("journey", false)
        assertNull(MahjongSave.decode(json.toString()))
        json.put("daily", true)
        assertEquals(MahjongMode.SLIDE, MahjongSave.decode(json.toString())!!.game.mode)
    }

    @Test fun completedMemoryKeepsItsExistingRewardReceipt() {
        val game = MahjongGame(MahjongMode.CLASSIC, 41, 5)
        game.restore(game.snapshot().copy(tiles = List(36) { id ->
            MahjongTile(id, id % 6, id / 6, 0, id / 2, removed = true)
        }))
        val reward = MahjongReward(145, emptyList(), false, 12)
        val json = JSONObject(MahjongSave.encode(game, true, false, 0L, id = "paid", reward = reward))
            .put("version", 2).put("mode", "MEMORY")
        val restored = MahjongSave.decode(json.toString())!!
        assertTrue(restored.game.complete)
        assertEquals(reward, restored.reward); assertEquals("paid", restored.id)
    }

    @Test fun freeStopSlideMigrationPreservesProgressAndRebuildsALegalIceFinish() {
        for (stage in listOf(1, 5)) {
            val game = MahjongGame(MahjongMode.SLIDE, 716, stage, initializeBoard = false)
            val oldTiles = List(game.rows * 4) { id ->
                val row = id / 4
                val columns = if (row % 2 == 0) listOf(1, 2, 3, 4) else listOf(0, 1, 4, 5)
                MahjongTile(id, columns[id % 4], row, 0, id / 2, removed = id < 4)
            }
            val before = MahjongSnapshot(oldTiles, emptyList(), emptyList(),
                score = 300, combo = 2, matches = 2, moves = 8, assists = 3, peakCombo = 2)
            game.restore(before)
            game.history += before.copy(tiles = oldTiles.map { it.copy(removed = false) },
                score = 0, combo = 0, matches = 0, moves = 0)
            val legacy = JSONObject(MahjongSave.encode(game, true, false, 42L,
                id = "free-stop-$stage", bonusMultiplier = 2)).put("version", 3)

            val restored = MahjongSave.decode(legacy.toString())!!
            val adapted = restored.game.snapshot()
            assertTrue(restored.iceMigration)
            assertEquals("free-stop-$stage", restored.id)
            assertTrue(restored.journey); assertFalse(restored.daily)
            assertEquals(42L, restored.day); assertEquals(2, restored.bonusMultiplier)
            assertEquals(before.copy(tiles = adapted.tiles), adapted)
            assertEquals(oldTiles.filter { !it.removed }.map { it.id },
                adapted.tiles.filter { !it.removed }.map { it.id })
            assertEquals(oldTiles.filter { it.removed }, adapted.tiles.filter { it.removed })
            assertTrue(restored.game.history.isEmpty())

            val encoded = MahjongSave.encode(restored)
            assertEquals(4, JSONObject(encoded).getInt("version"))
            val reopened = MahjongSave.decode(encoded)!!
            assertFalse(reopened.iceMigration)
            assertEquals(adapted, reopened.game.snapshot())
            assertTrue(reopened.game.history.isEmpty())

            // The migration's redeal is backed by the same full ice witness as a new board.
            playWitness(restored.game)
            assertTrue(restored.game.complete)
            assertTrue(restored.game.score > before.score)
        }
    }

    @Test fun finishedFreeStopSlideKeepsItsReceiptAndDoesNotMigrate() {
        val game = MahjongGame(MahjongMode.SLIDE, 28, 1, initializeBoard = false)
        val finished = List(24) { id -> MahjongTile(id, id % 6, id / 6, 0, id / 2, removed = true) }
        game.restore(MahjongSnapshot(finished, emptyList(), emptyList(), 5800, 8, 12, 30, 1, 8))
        val reward = MahjongReward(132, emptyList(), false, 23)
        val old = JSONObject(MahjongSave.encode(game, false, false, 0L, id = "paid-slide", reward = reward))
            .put("version", 3)
        val restored = MahjongSave.decode(old.toString())!!
        assertFalse(restored.iceMigration)
        assertEquals(game.snapshot(), restored.game.snapshot())
        assertEquals(reward, restored.reward); assertEquals("paid-slide", restored.id)
    }

    @Test fun focusedIceAttemptKeepsItsOriginAcrossSaveUndoAndChangingTiles() {
        val game = iceFocusFixture()
        game.tap(0)
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(0, 1, 0))
        val afterFirstSlide = game.snapshot()
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(0, 0, 1))
        assertEquals(MahjongCell(5, 5), MahjongCell(game.tiles[0].x, game.tiles[0].y))
        assertEquals(0, game.snapshot().focusId)
        assertEquals(MahjongCell(0, 0), game.snapshot().focusOrigin)

        val reopened = MahjongSave.decode(MahjongSave.encode(game, false, false, 0L))!!.game
        assertEquals(game.snapshot(), reopened.snapshot())
        assertEquals(game.history, reopened.history)
        assertEquals(0, reopened.selected)
        assertTrue(reopened.undo())
        assertEquals(afterFirstSlide.tiles, reopened.tiles)
        assertEquals(afterFirstSlide.focusId, reopened.snapshot().focusId)
        assertEquals(MahjongCell(0, 0), reopened.snapshot().focusOrigin)

        reopened.tap(2) // A different face abandons the attempt and restores its starting cell.
        assertEquals(MahjongCell(0, 0), MahjongCell(reopened.tiles[0].x, reopened.tiles[0].y))
        assertEquals(2, reopened.selected)
        assertEquals(2, reopened.snapshot().focusId)
        assertEquals(MahjongCell(2, 3), reopened.snapshot().focusOrigin)
        assertEquals(reopened.snapshot(), MahjongSave.decode(MahjongSave.encode(reopened, false, false, 0L))!!.game.snapshot())
    }

    @Test fun iceFocusMayStayAtItsOriginButMalformedFocusFailsClosed() {
        val game = iceFocusFixture()
        game.tap(0)
        val atOrigin = MahjongSave.encode(game, false, false, 0L)
        assertEquals(game.snapshot(), MahjongSave.decode(atOrigin)!!.game.snapshot())
        game.slideDirection(0, 1, 0)
        val raw = MahjongSave.encode(game, false, false, 0L)
        fun rejected(change: (JSONObject) -> Unit) {
            val json = JSONObject(raw)
            change(json)
            assertNull(MahjongSave.decode(json.toString()))
        }
        rejected { it.getJSONObject("state").remove("focusOrigin") }
        rejected { it.getJSONObject("state").remove("focusId") }
        rejected { it.getJSONObject("state").put("focusId", 999) }
        rejected { it.getJSONObject("state").getJSONArray("tiles").getJSONArray(0).put(5, 1) }
        rejected { it.getJSONObject("state").put("focusOrigin", JSONArray(listOf(2, 3))) }
        rejected { it.getJSONObject("state").put("focusOrigin", JSONArray(listOf(-1, 0))) }
        rejected { it.put("mode", "TEN") }
    }

    @Test fun incrementalEncodingPreservesFocusUndoBranchesShuffleAndReceipts() {
        val encoder = MahjongSave.Encoder()
        val game = iceFocusFixture()
        val run = MahjongSave.Saved(game, false, true, 42L, id = "cached-ice", bonusMultiplier = 2)
        fun verify(current: MahjongSave.Saved = run) {
            val fresh = MahjongSave.decode(MahjongSave.encode(current))!!
            val cached = MahjongSave.decode(encoder.encode(current))!!
            assertEquals(fresh.game.snapshot(), cached.game.snapshot())
            assertEquals(fresh.game.history, cached.game.history)
            assertEquals(fresh.id, cached.id)
            assertEquals(fresh.journey, cached.journey)
            assertEquals(fresh.daily, cached.daily)
            assertEquals(fresh.day, cached.day)
            assertEquals(fresh.reward, cached.reward)
            assertEquals(fresh.bonusMultiplier, cached.bonusMultiplier)
        }
        verify()
        game.tap(0)
        game.slideDirection(0, 1, 0); verify()
        game.slideDirection(0, 0, 1); verify()
        assertTrue(game.undo()); verify()
        game.slideDirection(0, -1, 0); verify()
        game.shuffle(); verify()

        val other = MahjongSave.Saved(MahjongGame(MahjongMode.TEN, 17), true, false, 0L, id = "other")
        other.game.hint().forEach { other.game.tap(it) }
        verify(other)
        verify()

        game.restore(game.snapshot().copy(tiles = game.tiles.map { it.copy(removed = true) },
            focusId = null, focusOrigin = null))
        run.reward = MahjongReward(134, emptyList(), false, 21)
        verify()
    }

    @Test fun legacyUndoHistoryRetainsOnlyTheLatest256ValidatedEntries() {
        val game = iceFocusFixture()
        repeat(300) { game.history += game.snapshot().copy(moves = it) }
        val encoded = MahjongSave.encode(game, false, false, 0L)
        val restored = MahjongSave.decode(encoded)!!.game
        assertEquals(game.snapshot(), restored.snapshot())
        assertEquals(game.history.takeLast(256), restored.history)
        for (moves in 299 downTo 44) {
            assertTrue(restored.undo())
            assertEquals(moves, restored.moves)
        }
        assertFalse(restored.undo())

        // Discarded history still participates in corruption validation.
        val corrupted = JSONObject(encoded)
        corrupted.getJSONArray("history").getJSONObject(0).getJSONArray("tiles").getJSONArray(0).put(4, 700)
        assertNull(MahjongSave.decode(corrupted.toString()))
    }

    @Test fun cancelledIceMigrationDoesNotBecomeAMissingSave() {
        val raw = JSONObject(MahjongSave.encode(iceFocusFixture(), false, false, 0L))
            .put("version", 3).toString()
        Thread.currentThread().interrupt()
        try {
            MahjongSave.decode(raw)
            fail("Cancelled migration must propagate cancellation")
        } catch (_: CancellationException) {
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    private fun iceFocusFixture() = MahjongGame(MahjongMode.SLIDE, 912, 1, initializeBoard = false).apply {
        restore(snapshot().copy(tiles = listOf(
            MahjongTile(0, 0, 0, 0, 0), MahjongTile(1, 3, 3, 0, 0),
            MahjongTile(2, 2, 3, 0, 1), MahjongTile(3, 1, 4, 0, 1)
        )))
    }

    private fun playWitness(game: MahjongGame) {
        for (step in game.solutionSteps.toList()) {
            step.slide?.let { move ->
                assertEquals(MahjongGame.Result.MOVED, game.slide(move.id, move.x, move.y))
            }
            for (id in step.ids) {
                val result = game.tap(id)
                assertTrue(result == MahjongGame.Result.SELECTED || result == MahjongGame.Result.MATCHED)
            }
        }
    }

    private fun slideOneTile(game: MahjongGame) {
        for (tile in game.board) for (y in 0 until game.rows) for (x in 0 until game.columns) {
            if (game.canSlide(tile.id, x, y)) { game.slide(tile.id, x, y); return }
        }
        fail("A slide board must offer a displacement")
    }
}
