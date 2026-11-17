package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class MahjongGameTest {
    private val dealt = listOf(MahjongMode.CLASSIC, MahjongMode.TRIPLE, MahjongMode.CONNECT, MahjongMode.CASCADE)

    /** Rejoue un témoin de fin complète avec les vraies règles du jeu. */
    private fun finish(game: MahjongGame, steps: List<MahjongSolutionStep> = game.solutionSteps.toList()) {
        for (step in steps) for (id in step.ids) {
            val result = game.tap(id)
            assertTrue("$result on $id", result == MahjongGame.Result.SELECTED || result == MahjongGame.Result.MATCHED)
        }
        assertTrue(game.complete)
    }

    @Test fun everyShapeIsWellFormedSupportedAndSplitsIntoPairsAndTriples() {
        for ((index, shape) in MahjongShapes.all.withIndex()) {
            assertEquals("shape $index", 0, shape.size % 6)
            for ((i, a) in shape.withIndex()) for (b in shape.drop(i + 1)) {
                assertFalse("overlap in $index", a.z == b.z && abs(a.x - b.x) < 2 && abs(a.y - b.y) < 2)
            }
            for (a in shape.filter { it.z > 0 }) for (dx in 0..1) for (dy in 0..1) {
                assertTrue("unsupported in $index", shape.any { b -> b.z == a.z - 1 && a.x + dx - b.x in 0..1 && a.y + dy - b.y in 0..1 })
            }
            // Portrait : huit tuiles de large au plus.
            assertTrue(shape.maxOf { it.x } + 2 <= 16)
        }
    }

    @Test fun everyDealtBoardHasACompleteLegalWitnessAtEveryTier() {
        for (mode in dealt) for (stage in listOf(1, 4, 8)) for (seed in 1..3) {
            val game = MahjongGame(mode, seed * 101 + stage, stage)
            assertTrue(game.tiles.size >= 48)
            val kinds = game.tiles.groupingBy { MahjongFaces.kind(it.face) }.eachCount()
            assertTrue("$mode copies", kinds.values.all { it % game.groupSize == 0 && it <= game.groupSize * 2 })
            finish(game)
        }
    }

    @Test fun classicNeedsAnEmptyTopAndAFreeSide() {
        val game = MahjongGame(MahjongMode.CLASSIC, 1, initializeBoard = false).apply {
            tiles += MahjongTile(0, 0, 0, 0, 5)
            tiles += MahjongTile(1, 2, 0, 0, 7)
            tiles += MahjongTile(2, 4, 0, 0, 5)
            tiles += MahjongTile(3, 1, 0, 1, 7)
            tiles += MahjongTile(4, 8, 0, 0, 7)
        }
        // The upper tile straddles the first two: both are covered.
        assertFalse(game.isFree(game.tiles[0])); assertFalse(game.isFree(game.tiles[1]))
        assertTrue(game.isFree(game.tiles[2])); assertTrue(game.isFree(game.tiles[3]))
        assertEquals(MahjongGame.Result.BLOCKED, game.tap(0))
        assertEquals(MahjongGame.Result.SELECTED, game.tap(3))
        assertEquals(MahjongGame.Result.MATCHED, game.tap(4))
        // Uncovered, the middle tile is still held on both sides.
        assertTrue(game.isFree(game.tiles[0])); assertFalse(game.isFree(game.tiles[1]))
        assertEquals(MahjongGame.Result.SELECTED, game.tap(0))
        assertEquals(MahjongGame.Result.MATCHED, game.tap(2))
        assertTrue(game.isFree(game.tiles[1]))
    }

    @Test fun flowersMatchAnyFlowerAndSeasonsAnySeason() {
        val game = MahjongGame(MahjongMode.CLASSIC, 1, initializeBoard = false).apply {
            tiles += MahjongTile(0, 0, 0, 0, 34); tiles += MahjongTile(1, 4, 0, 0, 37)
            tiles += MahjongTile(2, 8, 0, 0, 38); tiles += MahjongTile(3, 12, 0, 0, 34)
        }
        assertTrue(game.compatible(game.tiles[0], game.tiles[1]))
        assertFalse(game.compatible(game.tiles[1], game.tiles[2]))
        assertEquals(MahjongGame.Result.SELECTED, game.tap(0))
        assertEquals(MahjongGame.Result.MISMATCH, game.tap(2))
        assertEquals(MahjongGame.Result.MISMATCH, game.tap(1))
        assertEquals(MahjongGame.Result.MATCHED, game.tap(0))
    }

    @Test fun quickMatchesChainUntilTheRingRunsOutAndStarsFollowTheScore() {
        val game = MahjongGame(MahjongMode.CLASSIC, 7, 1)
        val steps = game.solutionSteps.toList()
        for (id in steps[0].ids) game.tap(id)
        assertEquals(1, game.combo); assertEquals(10, game.score)
        for (id in steps[1].ids) game.tap(id)
        assertEquals(2, game.combo); assertEquals(30, game.score)
        game.tick(game.chainSeconds + .1f)
        assertFalse(game.chaining)
        for (id in steps[2].ids) game.tap(id)
        assertEquals(1, game.combo)
        // Undo breaks the chain.
        for (id in steps[3].ids) game.tap(id)
        assertEquals(2, game.combo)
        assertTrue(game.undo()); assertEquals(0, game.combo)
        for (id in steps[3].ids) game.tap(id)
        // A perfect chain gives three stars, no chain one.
        val perfect = MahjongGame(MahjongMode.CLASSIC, 7, 1)
        finish(perfect)
        assertEquals(perfect.maxScore, perfect.score)
        assertEquals(3, perfect.stars)
        val slow = MahjongGame(MahjongMode.CLASSIC, 7, 1)
        for (step in slow.solutionSteps.toList()) { step.ids.forEach(slow::tap); slow.tick(60f) }
        assertTrue(slow.complete); assertEquals(1, slow.stars)
    }

    @Test fun slideHasNoTimer() {
        val game = MahjongGame(MahjongMode.SLIDE, 3, 1)
        assertFalse(game.timed)
        val plan = game.solutionSteps.toList()
        loop@ for (step in plan) {
            step.slide?.let { game.slide(it.id, it.x, it.y) }
            for (id in step.ids) if (game.tap(id) == MahjongGame.Result.MATCHED) break@loop
        }
        assertEquals(1, game.combo)
        assertFalse(game.tick(120f))
        assertTrue(game.chaining)
    }

    @Test fun shuffleKeepsTheFamiliesAndAlwaysLeavesAFinish() {
        for (mode in dealt) {
            val game = MahjongGame(mode, 42, 4)
            val steps = game.solutionSteps.toList()
            steps.take(steps.size / 3).forEach { step -> step.ids.forEach(game::tap) }
            // Triples : la réserve retourne sur le plateau avant la redistribution.
            val live = game.tiles.filter { !it.removed }
            val before = live.map { MahjongFaces.kind(it.face) }.sorted()
            val positions = live.map { Triple(it.id, it.x, it.y) }.toSet()
            assertTrue(game.shuffle())
            assertEquals(1, game.shufflesUsed); assertEquals(1, game.shufflesLeft)
            assertTrue(game.tray.isEmpty())
            assertEquals(before, game.board.map { MahjongFaces.kind(it.face) }.sorted())
            assertEquals(positions, game.board.map { Triple(it.id, it.x, it.y) }.toSet())
            finish(game)
            // Undo never gives the shuffle back.
            val again = MahjongGame(mode, 42, 4)
            assertTrue(again.shuffle()); assertTrue(again.undo())
            assertEquals(1, again.shufflesLeft)
        }
    }

    @Test fun twoStackedTilesCannotBeRedealtAndTheBoardStaysIntact() {
        val game = MahjongGame(MahjongMode.CLASSIC, 1, initializeBoard = false).apply {
            tiles += MahjongTile(0, 0, 0, 0, 3); tiles += MahjongTile(1, 0, 0, 1, 3)
        }
        assertTrue(game.stuck)
        val before = game.snapshot()
        assertFalse(game.shuffle())
        assertEquals(before, game.snapshot()); assertEquals(0, game.shufflesUsed); assertTrue(game.history.isEmpty())
    }

    @Test fun fullTrayBlocksTriplesAndHintsFollowAFinish() {
        val game = MahjongGame(MahjongMode.TRIPLE, 9, 1)
        val hint = game.hint()
        assertEquals(1, hint.size)
        assertFalse(game.hintSearchLimited)
        finish(game)
        val stuck = MahjongGame(MahjongMode.TRIPLE, 1, initializeBoard = false).apply {
            repeat(21) { tiles += MahjongTile(it, it % 8 * 2, it / 8 * 2, 0, it % 7) }
        }
        repeat(7) { assertNotEquals(MahjongGame.Result.MATCHED, stuck.tap(it)) }
        assertEquals(MahjongGame.TRAY, stuck.tray.size)
        assertTrue(stuck.stuck)
        assertEquals(MahjongGame.Result.TRAY_FULL, stuck.tap(8))
    }

    @Test fun classicHintsStillLeadToTheEndAfterTheWitnessIsAbandoned() {
        val game = MahjongGame(MahjongMode.CLASSIC, 5, 4)
        // Play legal pairs that are not the witness, then ask for hints until the end.
        repeat(3) { MahjongSolver.anyMove(game).forEach(game::tap) }
        var guard = 0
        while (!game.complete && guard++ < 200) {
            val ids = game.hint()
            if (ids.isEmpty()) { assertTrue(game.hintProvedLost); return }
            ids.forEach(game::tap)
        }
        assertTrue(game.complete)
    }

    @Test fun connectFindsPathsAroundTheBorder() {
        val game = MahjongGame(MahjongMode.CONNECT, 1, 1, initializeBoard = false).apply {
            for (x in 0 until 6) for (y in 0 until 8) tiles += MahjongTile(tiles.size, x, y, 0, (x + y * 6) % 30)
        }
        val corner = game.tiles.first { it.x == 0 && it.y == 0 }
        val reach = game.reachable(corner).toSet()
        // Along the top border to any tile of the first row, and down the left border.
        assertTrue(game.tiles.filter { it.y == 0 || it.x == 0 }.all { it.id == corner.id || it.id in reach })
        assertFalse(game.tiles.first { it.x == 3 && it.y == 4 }.id in reach)
    }
}
