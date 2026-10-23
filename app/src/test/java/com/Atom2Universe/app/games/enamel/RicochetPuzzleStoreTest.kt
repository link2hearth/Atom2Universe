package com.Atom2Universe.app.games.enamel

import android.app.Application
import android.content.Context
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE, application = Application::class)
class RicochetPuzzleStoreTest {
    private lateinit var store: RicochetPuzzleStore
    @Before fun setup() {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("ricochet_puzzle", Context.MODE_PRIVATE).edit().clear().commit()
        store = RicochetPuzzleStore(context)
    }

    private fun completed(seed: Int, hard: Boolean, shots: Int): RicochetPuzzleGame {
        val g = RicochetPuzzleGame(seed, hard)
        assertTrue(g.restoreState("1|$seed|${if (hard) 1 else 0}|$shots|0|2.0|0.3|5.0|1.1|"))
        assertTrue(g.complete)
        return g
    }

    @Test fun recordsOnlyImproveAndAreSeparateBySeedAndDifficulty() {
        val g = completed(42, false, 8)
        assertTrue(store.save(g))
        assertFalse(store.save(completed(42, false, 10)))
        assertEquals(8, store.best(g))
        assertTrue(store.save(completed(42, false, 5)))
        assertEquals(5, store.best(g))
        assertEquals(0, store.best(42, true))
        assertEquals(0, store.best(43, false))
        assertTrue(store.save(completed(42, true, 9)))
        assertEquals(5, store.best(g))
        assertEquals(9, store.best(42, true))
    }

    @Test fun incompleteAttemptsDoNotSetRecordsAndRestoreExactly() {
        val g = RicochetPuzzleGame(27, true)
        assertTrue(g.launch(4f, 12.5f, 0f, -1f, .5f))
        repeat(20) { g.step(1f / 120) }
        assertFalse(store.save(g))
        assertEquals(0, store.best(g))
        val copy = RicochetPuzzleGame()
        assertTrue(store.restore(copy))
        assertEquals(g.saveState(), copy.saveState())
    }

    @Test fun recentSeedsRemainAvailableWithoutDuplicates() {
        store.save(RicochetPuzzleGame(10))
        store.save(RicochetPuzzleGame(11, true))
        store.save(RicochetPuzzleGame(10))
        assertEquals(listOf(10 to false, 11 to true), store.recent())
        assertEquals(store.recent(), RicochetPuzzleStore(RuntimeEnvironment.getApplication()).recent())
    }

    @Test fun statsIncludeSyncedRecordsOutsideHistoryAndIgnoreInvalidOrOldVersions() {
        val prefs = RuntimeEnvironment.getApplication().getSharedPreferences(RicochetPuzzleStore.PREFS, Context.MODE_PRIVATE)
        prefs.edit().putInt("best_1_0_42", 7).putInt("best_1_1_42", 9)
            .putInt("best_0_0_99", 1).putInt("best_1_0_12", 0)
            .putInt("best_1_0_1000000000", 5).putString("best_1_0_43", "bad").commit()
        assertTrue(store.recent().isEmpty())
        assertEquals(listOf(7, 9), store.records().map { it.shots })
        assertEquals(listOf(false, true), store.records().map { it.hard })
        assertEquals(listOf(42, 42), store.records().map { it.seed })
    }

    @Test fun cloudMergeKeepsFewestShotsForEachSeedAndDifficulty() {
        val easy = "ricochet_puzzle.best_1_0_42"
        val hard = "ricochet_puzzle.best_1_1_42"
        val another = "ricochet_puzzle.best_1_0_43"
        val local = com.Atom2Universe.app.crypto.sync.HighScoresSyncData(mapOf(easy to 8.0, hard to 11.0))
        val remote = com.Atom2Universe.app.crypto.sync.HighScoresSyncData(mapOf(easy to 6.0, hard to 14.0, another to 9.0))
        val merged = com.Atom2Universe.app.crypto.sync.HighScoresSyncData.merge(local, remote)
        assertEquals(mapOf(easy to 6.0, hard to 11.0, another to 9.0), merged.values)
    }
}
