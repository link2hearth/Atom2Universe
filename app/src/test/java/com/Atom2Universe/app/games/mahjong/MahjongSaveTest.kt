package com.Atom2Universe.app.games.mahjong

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MahjongSaveTest {
    private fun saved(game: MahjongGame, journey: Boolean = false) = MahjongSave.Saved(game, journey, false, 213L)

    @Test fun savesRestoreBoardTrayUndoAndCountersForEveryMode() {
        for (mode in MahjongMode.entries) {
            val game = MahjongGame(mode, 914, 5)
            val step = game.solutionSteps.first()
            step.slide?.let { game.slide(it.id, it.x, it.y) }
            step.ids.forEach { game.tap(it) }
            if (mode == MahjongMode.TRIPLE) game.tap(game.board.first { game.isFree(it) }.id)
            game.hintsUsed = 2; game.shufflesUsed = 1
            val restored = MahjongSave.decode(MahjongSave.encode(saved(game, journey = true)))
            assertNotNull(mode.name, restored)
            assertEquals(game.snapshot(), restored!!.game.snapshot())
            assertEquals(game.history, restored.game.history)
            assertEquals(2, restored.game.hintsUsed); assertEquals(1, restored.game.shufflesUsed)
            assertTrue(restored.journey); assertEquals(213L, restored.day)
            if (game.history.isNotEmpty()) {
                assertTrue(restored.game.undo())
                assertEquals(game.history.last().tiles, restored.game.tiles)
            }
            // The incremental encoder writes the same document.
            assertEquals(MahjongSave.encode(saved(game, true)).length, MahjongSave.Encoder().encode(saved(game, true)).length)
        }
    }

    @Test fun malformedOrOlderSavesAreIgnored() {
        for (raw in listOf("", "{}", "{bad json}", "{\"version\":99}")) assertNull(MahjongSave.decode(raw))
        val game = MahjongGame(MahjongMode.CLASSIC, 44)
        val json = JSONObject(MahjongSave.encode(saved(game)))
        assertNotNull(MahjongSave.decode(json.toString()))
        assertNull(MahjongSave.decode(JSONObject(json.toString()).put("version", 4).toString()))
        json.getJSONObject("state").getJSONArray("tiles").getJSONArray(0).put(4, 700)
        assertNull(MahjongSave.decode(json.toString()))
        // Two tiles on the same spot are refused.
        val twin = JSONObject(MahjongSave.encode(saved(game)))
        val tiles = twin.getJSONObject("state").getJSONArray("tiles")
        val first = tiles.getJSONArray(0)
        tiles.getJSONArray(1).put(1, first.getInt(1)).put(2, first.getInt(2)).put(3, first.getInt(3))
        assertNull(MahjongSave.decode(twin.toString()))
    }

    @Test fun rewardsKeepTheirStars() {
        val game = MahjongGame(MahjongMode.CLASSIC, 3, 1)
        for (step in game.solutionSteps.toList()) step.ids.forEach(game::tap)
        val run = saved(game).apply { reward = MahjongReward(120, emptyList(), false, game.stars) }
        val restored = MahjongSave.decode(MahjongSave.encode(run))!!
        assertEquals(run.reward, restored.reward)
        assertEquals(3, restored.reward!!.stars)
    }
}
