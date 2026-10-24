package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException

class MahjongPuzzleQualityTest {
    private val modes = MahjongMode.entries.filter { it != MahjongMode.SLIDE }
    private fun copy(game: MahjongGame) = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false).apply { restore(game.snapshot()) }
    private fun model(mode: MahjongMode, hard: Boolean, layout: MahjongPuzzleLayouts.Layout) =
        MahjongGame(mode, 1, if (hard) 4 else 1, initializeBoard = false).apply {
            layout.tiles.forEachIndexed { id, packed ->
                val cell = packed and 63
                tiles += MahjongTile(id, cell % 6, cell / 6, (packed shr 6) and 3, packed shr 8)
            }
        }
    private fun play(game: MahjongGame, steps: List<MahjongSolutionStep>) {
        for (step in steps) for (id in step.ids) {
            val result = game.tap(id)
            assertTrue("${game.mode}: $result", result == MahjongGame.Result.SELECTED || result == MahjongGame.Result.MATCHED)
        }
    }
    private fun legalPairs(game: MahjongGame): Set<Set<Int>> = buildSet {
        val live = game.board
        for (a in live) for (b in live) if (a.id < b.id && game.compatible(a, b)) {
            val trial = copy(game)
            if (trial.tap(a.id) == MahjongGame.Result.SELECTED && trial.tap(b.id) == MahjongGame.Result.MATCHED) add(setOf(a.id, b.id))
        }
    }

    @Test fun everyCatalogHasItsOwnCertifiedDecisionsAndACompleteLegalWitness() {
        for (mode in modes) for (hard in listOf(false, true)) {
            val layouts = MahjongPuzzleLayouts.get(mode, hard)
            assertEquals(8, layouts.size)
            for (layout in layouts) {
                val game = model(mode, hard, layout)
                val initial = game.snapshot()
                val quality = MahjongPuzzleSolver.analyze(game)
                assertEquals("$mode hard=$hard", MahjongPuzzleSolver.Outcome.SOLVABLE, quality.result.outcome)
                assertTrue(quality.choices.none { it.outcome == MahjongPuzzleSolver.Outcome.UNKNOWN })
                assertEquals(initial, game.snapshot())
                if (mode == MahjongMode.TRIPLE) {
                    assertEquals(3, game.tiles.map { it.z }.distinct().size)
                    assertTrue(game.board.filter(game::isFree).groupingBy { it.face }.eachCount().values.all { it < 3 })
                    val constrained = MahjongPuzzleSolver.solve(game, trayLimit = if (hard) 5 else 4)
                    assertEquals("Reserve pressure is required in EVERY solution", MahjongPuzzleSolver.Outcome.UNSOLVABLE, constrained.outcome)
                    var critical = false
                    for (id in layout.picks.take(8)) {
                        val phase = MahjongPuzzleSolver.analyze(game)
                        phase.choices.firstOrNull { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE }?.let { wrong ->
                            val trial = copy(game)
                            val action = trial.tap(wrong.ids.single())
                            assertTrue(action != MahjongGame.Result.BLOCKED && action != MahjongGame.Result.IGNORED)
                            assertEquals(MahjongPuzzleSolver.Outcome.UNSOLVABLE, MahjongPuzzleSolver.solve(trial).outcome)
                            critical = true
                        }
                        game.tap(id)
                    }
                    assertTrue("A tempting pick must really threaten the tray", critical)
                    game.restore(initial)
                } else {
                    val winning = quality.choices.filter { it.outcome == MahjongPuzzleSolver.Outcome.SOLVABLE }
                    val losing = quality.choices.filter { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE }
                    assertTrue("$mode must offer a real wrong pairing", losing.size >= if (hard) 2 else 1)
                    assertTrue(winning.size in 1..if (mode in listOf(MahjongMode.CLASSIC, MahjongMode.TEN)) 5 else 3)
                    val wrong = copy(game)
                    play(wrong, listOf(MahjongSolutionStep(losing.first().ids)))
                    assertEquals(MahjongPuzzleSolver.Outcome.UNSOLVABLE, MahjongPuzzleSolver.solve(wrong).outcome)
                    when (mode) {
                        MahjongMode.CLASSIC -> {
                            assertEquals(3, game.tiles.map { it.z }.distinct().size)
                            assertTrue(winning.any { choice ->
                                val before = game.board.filter(game::isFree).map { it.id }.toSet()
                                val trial = copy(game); play(trial, listOf(MahjongSolutionStep(choice.ids)))
                                trial.board.any { trial.isFree(it) && it.id !in before }
                            })
                        }
                        MahjongMode.CONNECT -> for (choice in winning) {
                            val a = game.tiles[choice.ids[0]]; val b = game.tiles[choice.ids[1]]
                            val path = game.connection(a, b)!!
                            val directions = path.zipWithNext { x, y -> (y.x - x.x) to (y.y - x.y) }
                            assertTrue("A winning connection must turn a corner", directions.distinct().size > 1)
                        }
                        MahjongMode.CASCADE, MahjongMode.TEN -> assertTrue(winning.any { choice ->
                            val beforePairs = legalPairs(game)
                            val trial = copy(game); play(trial, listOf(MahjongSolutionStep(choice.ids)))
                            if (mode == MahjongMode.CASCADE) {
                                val fixed = copy(game)
                                choice.ids.forEach { fixed.tiles[it].removed = true }
                                (legalPairs(trial) - legalPairs(fixed)).isNotEmpty()
                            } else {
                                val horizontal = game.tiles[choice.ids[0]].y == game.tiles[choice.ids[1]].y
                                (legalPairs(trial) - beforePairs).any { pair ->
                                    val ids = pair.toList()
                                    (game.tiles[ids[0]].y == game.tiles[ids[1]].y) != horizontal
                                }
                            }
                        })
                        else -> Unit
                    }
                }
                play(game, layout.picks.toList().chunked(if (mode == MahjongMode.TRIPLE) 1 else 2).map { MahjongSolutionStep(it) })
                assertTrue("$mode hard=$hard witness", game.complete)
            }
        }
    }

    @Test fun generatedBoardsAreDeterministicAndHintsRemainWinningAfterReload() {
        for (mode in modes) for (stage in listOf(1, 4)) for (seed in 0..2) {
            val original = MahjongGame(mode, seed, stage)
            assertEquals(original.snapshot(), MahjongGame(mode, seed, stage).snapshot())
            if (mode == MahjongMode.TRIPLE) {
                assertEquals(MahjongPuzzleSolver.Outcome.UNSOLVABLE,
                    MahjongPuzzleSolver.solve(original, trayLimit = if (stage < 4) 4 else 5).outcome)
            } else {
                val decisions = MahjongPuzzleSolver.analyze(original)
                assertTrue("Transforms must preserve the actual traps", decisions.choices.count {
                    it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE
                } >= if (stage < 4) 1 else 2)
            }
            // Drop the construction witness, as when reloading a persisted game.
            val game = copy(original)
            var taps = 0
            while (!game.complete && taps < 100) {
                val before = game.snapshot()
                val ids = game.hint()
                assertEquals(before, game.snapshot())
                assertFalse("$mode stage=$stage seed=$seed", game.hintSearchLimited)
                assertTrue("A winning hint is required", ids.isNotEmpty())
                play(game, listOf(MahjongSolutionStep(ids))); taps += ids.size
            }
            assertTrue(game.complete)
        }
    }

    @Test fun partialRedealsPreserveIdsAndScoreAndAlwaysProvideACompleteFinish() {
        for (mode in modes) for (stage in listOf(1, 4)) {
            val source = MahjongGame(mode, 191, stage)
            val steps = source.solutionSteps.toList()
            for (step in steps) {
                play(source, listOf(step))
                if (source.complete || source.selected != null || source.tray.isNotEmpty()) continue
                val game = copy(source)
                val before = game.snapshot()
                val ids = game.tiles.filter { !it.removed }.map { it.id }.toSet()
                game.shuffle()
                assertEquals(ids, game.board.map { it.id }.toSet())
                assertEquals(before.score, game.score)
                assertEquals(before.tiles.filter { it.removed }, game.tiles.filter { it.removed })
                val after = game.snapshot(); val witness = game.solutionSteps.toList()
                assertTrue(game.undo()); assertEquals(before.tiles, game.tiles)
                game.restore(after); play(game, witness)
                assertTrue(game.complete)
            }
        }
    }

    @Test fun searchLimitAndCancellationNeverPretendThePuzzleIsLost() {
        val game = MahjongGame(MahjongMode.CLASSIC, 1)
        val before = game.snapshot()
        val quality = MahjongPuzzleSolver.analyze(game, budget = 0)
        assertEquals(MahjongPuzzleSolver.Outcome.UNKNOWN, quality.result.outcome)
        assertTrue(quality.choices.none { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE })
        Thread.currentThread().interrupt()
        try {
            MahjongPuzzleSolver.solve(game)
            fail("Cancellation must stop the search")
        } catch (_: CancellationException) {
            assertTrue(Thread.currentThread().isInterrupted)
        } finally { Thread.interrupted() }
        assertEquals(before, game.snapshot())
    }
}
