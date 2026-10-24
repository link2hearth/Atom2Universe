package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CancellationException
import kotlin.random.Random

class MahjongProceduralGeneratorTest {
    private fun play(game: MahjongGame) {
        for (step in game.solutionSteps.toList()) {
            step.slide?.let { assertEquals(MahjongGame.Result.MOVED, game.slide(it.id, it.x, it.y)) }
            for (id in step.ids) assertTrue(game.tap(id) in listOf(MahjongGame.Result.SELECTED, MahjongGame.Result.MATCHED))
        }
        assertTrue("${game.mode} stage=${game.stage} seed=${game.seed}", game.complete)
    }

    /** Remove artwork, IDs and board symmetries before comparing partner geometry. */
    private fun shape(game: MahjongGame): String = buildList {
        for (transpose in if (game.rows == 6) listOf(false, true) else listOf(false))
            for (mx in listOf(false, true)) for (my in listOf(false, true)) {
                add(game.tiles.groupBy { if (game.mode == MahjongMode.TEN) minOf(it.face % 9, 8 - it.face % 9) else it.face }
                    .values.map { family -> family.map {
                        var x = if (transpose) it.y else it.x
                        var y = if (transpose) it.x else it.y
                        if (mx) x = 5 - x
                        if (my) y = game.rows - 1 - y
                        it.z * 64 + y * 6 + x
                    }.sorted().joinToString(",") }.sorted().joinToString(";"))
            }
    }.min()

    private fun catalogs(mode: MahjongMode, stage: Int): Set<String> = if (mode == MahjongMode.SLIDE) {
        (if (stage < 4) MahjongIceLayouts.normal else MahjongIceLayouts.hard).map { cells ->
            shape(MahjongGame(mode, 0, stage, initializeBoard = false).apply {
                cells.forEachIndexed { i, cell -> tiles += MahjongTile(i, cell % 6, cell / 6, 0, i / 2) }
            })
        }.toSet()
    } else MahjongPuzzleLayouts.get(mode, stage >= 4).map { layout ->
        shape(MahjongGame(mode, 0, stage, initializeBoard = false).apply {
            layout.tiles.forEachIndexed { i, packed ->
                val cell = packed and 63
                tiles += MahjongTile(i, cell % 6, cell / 6, (packed shr 6) and 3, packed shr 8)
            }
        })
    }.toSet()

    @Test fun freshGenerationCoverageDiversityAndLegalSolutions() {
        for (mode in MahjongMode.entries) for (stage in listOf(1, 4)) {
            var fresh = 0; var states = 0; var attempts = 0
            val shapes = mutableSetOf<String>()
            val reserveGraphs = mutableSetOf<String>()
            val bank = catalogs(mode, stage)
            val started = System.nanoTime()
            for (sample in 0 until 32) {
                val game = MahjongGame(mode, sample * 7919 xor 0x3571, stage)
                val report = game.generation!!
                if (report.procedural) {
                    fresh++
                    val geometry = shape(game)
                    assertTrue("Procedural construction must not simply transform a bank", geometry !in bank)
                    shapes += geometry
                }
                states += report.states; attempts += report.attempts
                assertTrue(report.states <= MahjongProceduralGenerator.MAX_STATES)
                assertTrue(report.attempts <= MahjongProceduralGenerator.MAX_ATTEMPTS)
                assertEquals(game.tiles.size, game.tiles.map { Triple(it.x, it.y, it.z) }.toSet().size)
                if (mode == MahjongMode.SLIDE) {
                    assertTrue(MahjongIceGenerator.isPuzzle(MahjongIceSolver.analyze(game)!!, game.remaining / 2, stage >= 4))
                } else if (mode == MahjongMode.TRIPLE) {
                    assertEquals(MahjongPuzzleSolver.Outcome.UNSOLVABLE, MahjongPuzzleSolver.solve(game, trayLimit = if (stage < 4) 4 else 5).outcome)
                    reserveGraphs += game.tiles.groupBy { it.face }.values.map { family -> family.map { tile ->
                        "${tile.z}:${game.tiles.count { it.x == tile.x && it.y == tile.y && it.z > tile.z && it.face == tile.face }}"
                    }.sorted().joinToString(",") }.sorted().joinToString(";")
                } else {
                    val quality = MahjongPuzzleSolver.analyze(game)
                    assertTrue(quality.choices.none { it.outcome == MahjongPuzzleSolver.Outcome.UNKNOWN })
                    assertTrue(quality.choices.count { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE } >= if (stage < 4) 1 else 2)
                }
                play(game)
            }
            println("PROCEDURAL $mode stage=$stage fresh=$fresh/32 unique=${shapes.size} attempts=${attempts / 32} states=${states / 32} checkedMs=${(System.nanoTime() - started) / 32_000_000}")
            assertTrue("$mode stage=$stage fallback must remain exceptional ($fresh/32 fresh)", fresh >= 30)
            assertTrue("Fresh geometry, not only new artwork", shapes.size >= 30)
            if (mode == MahjongMode.TRIPLE) assertTrue("Reserve dependency graphs must also vary", reserveGraphs.size >= 4)
        }
    }

    @Test fun exhaustedBudgetsLeaveInputUntouchedAndCertifiedFallbackStillFinishes() {
        for (mode in MahjongMode.entries) for (stage in listOf(1, 4)) {
            val game = MahjongGame(mode, 81, stage, initializeBoard = false).apply {
                val size = if (mode == MahjongMode.SLIDE) MahjongIceGenerator.tileCount(stage) else MahjongPuzzleGenerator.tileCount(mode, stage)
                repeat(size) { tiles += MahjongTile(it, it % 6, it / 6, 0, 0) }
            }
            val before = game.snapshot()
            assertFalse(MahjongProceduralGenerator.deal(game, Random(1), maxStates = 0))
            assertEquals(before, game.snapshot())
            assertTrue(game.solutionSteps.isEmpty())
            if (mode == MahjongMode.SLIDE) MahjongIceGenerator.deal(game, Random(2), proceduralAttempts = 0)
            else assertTrue(MahjongPuzzleGenerator.deal(game, Random(2), proceduralAttempts = 0))
            assertEquals(MahjongProceduralGenerator.Report(false, 0, 0), game.generation)
            play(game)
        }
    }

    @Test fun generatedMechanicsActuallyUncoverTurnFallAndOpenPerpendicularCorridors() {
        fun copy(game: MahjongGame) = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false).apply { restore(game.snapshot()) }
        fun legal(game: MahjongGame): Set<Set<Int>> = buildSet {
            for (a in game.board) for (b in game.board) if (a.id < b.id && game.compatible(a, b)) {
                val trial = copy(game)
                if (trial.tap(a.id) == MahjongGame.Result.SELECTED && trial.tap(b.id) == MahjongGame.Result.MATCHED) add(setOf(a.id, b.id))
            }
        }
        for (mode in listOf(MahjongMode.CLASSIC, MahjongMode.CONNECT, MahjongMode.CASCADE, MahjongMode.TEN))
            for (stage in listOf(1, 4)) {
                val game = MahjongGame(mode, 434, stage)
                val winning = MahjongPuzzleSolver.analyze(game).choices.filter { it.outcome == MahjongPuzzleSolver.Outcome.SOLVABLE }
                val before = legal(game)
                val free = game.board.filter(game::isFree).map { it.id }.toSet()
                if (mode == MahjongMode.CONNECT) {
                    for (choice in winning) {
                        val path = game.connection(game.tiles[choice.ids[0]], game.tiles[choice.ids[1]])!!
                        assertTrue(path.zipWithNext { a, b -> (b.x - a.x) to (b.y - a.y) }.distinct().size > 1)
                    }
                } else assertTrue("$mode stage=$stage must use its own mechanic", winning.any { choice ->
                    val trial = copy(game); choice.ids.forEach(trial::tap)
                    when (mode) {
                        MahjongMode.CLASSIC -> trial.board.any { trial.isFree(it) && it.id !in free }
                        MahjongMode.CASCADE -> {
                            val fixed = copy(game); choice.ids.forEach { fixed.tiles[it].removed = true }
                            (legal(trial) - legal(fixed)).isNotEmpty()
                        }
                        else -> {
                            val horizontal = game.tiles[choice.ids[0]].y == game.tiles[choice.ids[1]].y
                            (legal(trial) - before).any { pair ->
                                val ids = pair.toList()
                                (game.tiles[ids[0]].y == game.tiles[ids[1]].y) != horizontal
                            }
                        }
                    }
                })
            }
    }

    @Test fun cancellationStopsGenerationWithoutPublishingAPartialBoard() {
        val game = MahjongGame(MahjongMode.CLASSIC, 1, initializeBoard = false)
        val before = game.snapshot()
        Thread.currentThread().interrupt()
        try {
            MahjongProceduralGenerator.deal(game, Random(1))
            fail("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(before, game.snapshot())
            assertTrue(game.solutionSteps.isEmpty())
        } finally { Thread.interrupted() }
    }

    @Test fun optimisticPruningAgreesWithExhaustiveActualGameMovesIncludingDuplicatePartners() {
        for (mode in listOf(MahjongMode.CLASSIC, MahjongMode.CONNECT, MahjongMode.TEN)) repeat(40) { seed ->
            val random = Random(seed)
            val game = MahjongGame(mode, seed, initializeBoard = false)
            val cells = (0 until 36).shuffled(random).take(8)
            val faces = (if (mode == MahjongMode.TEN) listOf(0, 8, 0, 8, 2, 6, 2, 6) else List(8) { it / 4 }).shuffled(random)
            repeat(8) { i ->
                val cell = if (mode == MahjongMode.CLASSIC && i >= 6) cells[i - 6] else cells[i]
                game.tiles += MahjongTile(i, cell % 6, cell / 6, if (mode == MahjongMode.CLASSIC && i >= 6) 1 else 0, faces[i])
            }
            val memo = mutableMapOf<Int, Boolean>()
            fun exhaust(state: MahjongGame): Boolean {
                if (state.complete) return true
                val mask = state.board.sumOf { 1 shl it.id }
                memo[mask]?.let { return it }
                for (a in state.board) for (b in state.board) if (a.id < b.id && state.compatible(a, b)) {
                    val next = MahjongGame(mode, seed, initializeBoard = false).apply { restore(state.snapshot()) }
                    if (next.tap(a.id) == MahjongGame.Result.SELECTED && next.tap(b.id) == MahjongGame.Result.MATCHED && exhaust(next)) {
                        memo[mask] = true; return true
                    }
                }
                memo[mask] = false; return false
            }
            assertEquals("$mode seed=$seed", if (exhaust(game)) MahjongPuzzleSolver.Outcome.SOLVABLE else MahjongPuzzleSolver.Outcome.UNSOLVABLE,
                MahjongPuzzleSolver.solve(game).outcome)
        }
    }
}
