package com.Atom2Universe.app.games.mahjong

import org.junit.Assert.*
import org.junit.Test

class MahjongGameTest {
    private fun play(game: MahjongGame, steps: List<MahjongSolutionStep>) {
        for (step in steps) {
            step.slide?.let { move -> assertEquals(MahjongGame.Result.MOVED, game.slide(move.id, move.x, move.y)) }
            step.ids.forEach { id ->
                assertTrue("${game.mode}: blocked tile $id", game.isFree(game.tiles[id]))
                val result = game.tap(id)
                assertTrue("${game.mode}: unexpected $result", result == MahjongGame.Result.SELECTED || result == MahjongGame.Result.MATCHED)
            }
        }
    }

    @Test fun allGeneratedPuzzlesHaveACompleteLegalWitness() {
        for (mode in MahjongMode.entries) for (stage in listOf(1, 2, 5, 20)) repeat(4) { seed ->
            val game = MahjongGame(mode, seed, stage)
            play(game, game.solutionSteps.toList())
            assertTrue("$mode stage=$stage seed=$seed", game.complete)
            assertTrue(game.tray.isEmpty())
        }
    }

    @Test fun everyRedealPreservesRemainingIdsAndProvidesALegalFinish() {
        for (mode in MahjongMode.entries) for (stage in listOf(1, 5)) repeat(3) { seed ->
            val game = MahjongGame(mode, seed, stage)
            play(game, game.solutionSteps.take(4))
            val remaining = game.tiles.filter { !it.removed }.map { it.id }.toSet()
            val before = game.snapshot()
            game.shuffle()
            assertEquals(remaining, game.board.map { it.id }.toSet())
            val recovered = game.snapshot()
            val witness = game.solutionSteps.toList()
            assertTrue(game.undo())
            assertEquals(before.tiles, game.snapshot().tiles)
            assertEquals(before.tray, game.tray)
            game.restore(recovered)
            play(game, witness)
            assertTrue("$mode stage=$stage seed=$seed", game.complete)
        }
    }

    @Test fun classicStartsLayeredAndCannotPickCoveredTiles() {
        val game = MahjongGame(MahjongMode.CLASSIC, 7)
        assertTrue(game.tiles.any { it.z > 0 })
        val covered = game.tiles.first { tile -> game.tiles.any { it.x == tile.x && it.y == tile.y && it.z > tile.z } }
        assertFalse(game.isFree(covered))
        assertEquals(MahjongGame.Result.BLOCKED, game.tap(covered.id))
    }

    @Test fun tripleRequiresIncompleteSetsBeforeReachingBuriedCopies() {
        repeat(6) { seed ->
            val game = MahjongGame(MahjongMode.TRIPLE, seed)
            assertEquals(3, game.tiles.map { it.z }.distinct().size)
            assertTrue(game.board.filter(game::isFree).groupingBy { it.face }.eachCount().values.all { it < 3 })
            var peak = 0
            for (id in game.solution.flatten()) {
                game.tap(id)
                peak = maxOf(peak, game.tray.size)
            }
            assertTrue(peak >= 5)
            assertTrue(game.complete)
        }
    }

    @Test fun fullTrayCanBeUndoneOrRedealtWithoutLosingTiles() {
        val game = MahjongGame(MahjongMode.TRIPLE, 9, initializeBoard = false).apply {
            repeat(9) { tiles += MahjongTile(it, it % 6, it / 6, 0, it / 3) }
        }
        val count = game.remaining
        val free = game.board.filter(game::isFree).take(8)
        free.forEachIndexed { i, tile -> tile.face = i }
        free.take(7).forEach { game.tap(it.id) }
        assertEquals(7, game.tray.size)
        assertEquals(MahjongGame.Result.TRAY_FULL, game.tap(free[7].id))
        assertTrue(game.hint().isEmpty())
        assertEquals(count, game.remaining)
        assertTrue(game.undo()); assertEquals(6, game.tray.size)
        game.shuffle()
        assertEquals(count, game.board.size)
        play(game, game.solutionSteps.toList())
        assertTrue(game.complete)
    }

    @Test fun classicShuffleRescuesTwoStackedTilesAndUndoRestoresThem() {
        val game = MahjongGame(MahjongMode.CLASSIC, 81, 5)
        game.tiles.forEach { it.removed = true }
        val a = game.tiles[0]; val b = game.tiles[1]
        a.removed = false; b.removed = false
        a.x = 2; a.y = 2; a.z = 0; a.face = 3
        b.x = 2; b.y = 2; b.z = 1; b.face = 3
        assertTrue(game.hint().isEmpty())
        game.shuffle()
        assertEquals(setOf(a.id, b.id), game.board.map { it.id }.toSet())
        assertEquals(2, game.hint().size)
        assertTrue(game.undo())
        assertEquals(1, game.tiles[1].z)
        assertTrue(game.hint().isEmpty())
    }

    @Test fun connectAllowsPerimeterButRejectsBlockedInternalPairs() {
        val game = fullGrid(MahjongMode.CONNECT)
        val path = game.connection(game.tiles[0], game.tiles[5])
        assertNotNull(path)
        assertTrue(path!!.any { it.y == -1 })
        assertNull(game.connection(game.tiles[7], game.tiles[28]))
        assertNotNull(game.connection(game.tiles[7], game.tiles[8]))
        listOf(8, 9, 15, 21).forEach { game.tiles[it].removed = true }
        assertNotNull(game.connection(game.tiles[7], game.tiles[22]))
    }

    @Test fun cascadeRequiresAnOpenPathAndRotatesGravityWithUndo() {
        val blocked = fullGrid(MahjongMode.CASCADE)
        blocked.tiles[7].face = 0; blocked.tiles[28].face = 0
        blocked.tap(7)
        assertEquals(MahjongGame.Result.MISMATCH, blocked.tap(28))
        assertEquals(36, blocked.remaining)
        val game = MahjongGame(MahjongMode.CASCADE, 2)
        for (step in game.solutionSteps.take(4)) {
            val before = game.snapshot()
            val direction = game.gravityDirection
            play(game, listOf(step))
            assertEquals((direction + 1) % 4, game.gravityDirection)
            if (direction % 2 == 0) for (column in game.board.groupBy { it.x }.values) {
                val start = if (direction == 0) game.rows - column.size else 0
                assertEquals((start until start + column.size).toList(), column.map { it.y }.sorted())
            } else for (row in game.board.groupBy { it.y }.values) {
                val start = if (direction == 1) 0 else game.columns - row.size
                assertEquals((start until start + row.size).toList(), row.map { it.x }.sorted())
            }
            val after = game.snapshot()
            assertTrue(game.undo())
            assertEquals(before.tiles, game.snapshot().tiles)
            assertEquals(direction, game.gravityDirection)
            play(game, listOf(step))
            assertEquals(after.tiles, game.snapshot().tiles)
        }
    }

    @Test fun slidePuzzlesRequireSeveralRealMovesAndAreDeterministic() {
        for (stage in listOf(1, 5)) repeat(6) { seed ->
            val game = MahjongGame(MahjongMode.SLIDE, seed, stage)
            assertEquals(game.snapshot(), MahjongGame(MahjongMode.SLIDE, seed, stage).snapshot())
            val diagonalPairs = game.board.groupBy { it.face }.values.count { (a, b) -> a.x != b.x && a.y != b.y }
            assertTrue("Only $diagonalPairs necessary slides", diagonalPairs >= 3)
            assertTrue(game.solutionSteps.count { it.slide != null } >= 3)
            assertTrue(game.hint().isNotEmpty())
            game.slideHint()?.let { assertTrue(game.canSlide(it.id,it.x,it.y)) }
            // A long initial corridor is cosmetic. The stopper may only become
            // reachable after several slides; certify that it is indispensable.
            val quality = MahjongIceSolver.analyze(game)!!
            assertTrue(quality.stopperDependentPairs >= game.remaining / 2 - 1)
            assertTrue(quality.losingChoices >= if (stage < 4) 1 else 2)
        }
    }

    @Test fun iceHintsFollowACompleteSolutionWithoutChangingTheBoard() {
        for (stage in listOf(1,5)) repeat(2) { seed ->
            val game = MahjongGame(MahjongMode.SLIDE,seed,stage)
            var steps = 0
            while (!game.complete && steps++ < 1000) {
                val before = game.snapshot()
                val ids = game.hint()
                val slide = game.slideHint()
                assertEquals(before,game.snapshot())
                assertTrue("No winning hint: stage=$stage seed=$seed",ids.isNotEmpty())
                if (slide != null) {
                    assertEquals(listOf(slide.id),ids)
                    if (game.selected != slide.id) game.tap(slide.id)
                    assertEquals(MahjongGame.Result.MOVED,game.slide(slide.id,slide.x,slide.y))
                } else ids.forEach { game.tap(it) }
            }
            assertTrue("Hints looped: stage=$stage seed=$seed",game.complete)
        }
    }

    @Test fun iceStopsAtWallsOrBeforeTilesAndNeverAllowsAnIntermediateStop() {
        val game = MahjongGame(MahjongMode.SLIDE, 0, initializeBoard = false)
        game.tiles += listOf(MahjongTile(0,2,2,0,0), MahjongTile(1,5,2,0,1),
            MahjongTile(2,2,0,0,2), MahjongTile(3,2,5,0,3))
        val before = game.snapshot()
        assertEquals(MahjongSlide(0,4,2), game.iceSlideTarget(0,1,0))
        assertEquals(MahjongSlide(0,0,2), game.iceSlideTarget(0,-1,0))
        assertEquals(MahjongSlide(0,2,1), game.iceSlideTarget(0,0,-1))
        assertEquals(MahjongSlide(0,2,4), game.iceSlideTarget(0,0,1))
        assertNull(game.iceSlideTarget(0,0,0)); assertNull(game.iceSlideTarget(0,1,1))
        assertFalse(game.canSlide(0,3,2))
        assertEquals(MahjongGame.Result.BLOCKED, game.slide(0,3,2))
        assertEquals(before, game.snapshot())
        game.tap(0)
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(0,1,0))
        assertEquals(MahjongCell(4,2), MahjongCell(game.tiles[0].x,game.tiles[0].y))
        assertEquals(before.tiles[1], game.tiles[1]) // The stopping tile is never pushed or merged.
        assertEquals(MahjongGame.Result.BLOCKED, game.slideDirection(0,1,0))
        assertEquals(1, game.moves)
        assertTrue(game.undo())
        assertEquals(before.tiles, game.tiles)
        assertEquals(0, game.selected)
        assertEquals(MahjongCell(2,2), game.focusOrigin)
    }

    @Test fun focusSurvivesMultipleSlidesButReturnsToOriginWhenAnotherTileIsChosen() {
        val game = MahjongGame(MahjongMode.SLIDE, 0, initializeBoard = false)
        game.tiles += listOf(MahjongTile(0,1,1,0,0), MahjongTile(1,4,3,0,0),
            MahjongTile(2,1,4,0,1), MahjongTile(3,4,4,0,1))
        game.tap(0)
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(0,1,0))
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(0,0,1))
        assertEquals(MahjongCell(5,5), MahjongCell(game.tiles[0].x,game.tiles[0].y))
        assertEquals(MahjongCell(1,1), game.focusOrigin)
        game.tap(0)
        assertEquals(0, game.selected); assertEquals(MahjongCell(1,1), game.focusOrigin)
        val pending = game.snapshot()
        // Preview another tile as if the previous focus has already returned.
        assertEquals(MahjongSlide(2,1,2), game.iceSlideTarget(2,0,-1))
        assertEquals(pending, game.snapshot())
        assertEquals(MahjongGame.Result.SELECTED, game.tap(2))
        assertEquals(MahjongCell(1,1), MahjongCell(game.tiles[0].x,game.tiles[0].y))
        assertEquals(2, game.selected); assertEquals(MahjongCell(1,4), game.focusOrigin)
        assertTrue(game.undo())
        assertEquals(pending.tiles, game.tiles)
        assertEquals(pending.focusId, game.selected); assertEquals(pending.focusOrigin, game.focusOrigin)
        assertEquals(MahjongGame.Result.MOVED, game.slideDirection(2,0,-1))
        assertEquals(MahjongCell(1,1), MahjongCell(game.tiles[0].x,game.tiles[0].y))
        assertEquals(MahjongCell(1,2), MahjongCell(game.tiles[2].x,game.tiles[2].y))
        assertEquals(2, game.selected); assertEquals(MahjongCell(1,4), game.focusOrigin)
        // Touching a different tile still transfers focus if the swipe is blocked.
        assertEquals(MahjongGame.Result.SELECTED,game.slideDirection(3,0,-1))
        assertEquals(MahjongCell(1,4),MahjongCell(game.tiles[2].x,game.tiles[2].y))
        assertEquals(3,game.selected); assertEquals(MahjongCell(4,4),game.focusOrigin)
        val blocked = game.snapshot()
        assertEquals(MahjongGame.Result.BLOCKED,game.slideDirection(3,0,-1))
        assertEquals(blocked,game.snapshot())
    }

    @Test fun slideRejectsObstaclesDiagonalsAndOutsideBoardAndUndoIsExact() {
        val game = MahjongGame(MahjongMode.SLIDE, 3)
        game.tiles.forEach { it.removed = true }
        val a = game.tiles[0]; val b = game.tiles[1]; val blocker = game.tiles[2]
        a.removed = false; b.removed = false; blocker.removed = false
        a.x = 0; a.y = 0; a.face = 1
        b.x = 2; b.y = 5; b.face = 1
        blocker.x = 1; blocker.y = 0; blocker.face = 2
        val before = game.snapshot()
        assertEquals(MahjongGame.Result.BLOCKED, game.slide(a.id, 2, 0))
        assertEquals(MahjongGame.Result.BLOCKED, game.slide(a.id, 1, 1))
        assertEquals(MahjongGame.Result.BLOCKED, game.slide(a.id, -1, 0))
        assertEquals(MahjongGame.Result.BLOCKED, game.slide(a.id, 0, 2))
        assertEquals(before, game.snapshot())
        game.tap(a.id); assertEquals(MahjongGame.Result.SELECTED, game.tap(b.id))
        assertEquals(MahjongGame.Result.MOVED, game.slide(a.id, 0, 5))
        assertEquals(1, game.moves)
        assertEquals((0..5).map { MahjongCell(0,it) }, game.lastPath)
        assertTrue(game.undo()); assertEquals(before.tiles, game.snapshot().tiles)
        assertEquals(0, game.moves)
        game.slide(a.id, 0, 5)
        assertEquals(MahjongGame.Result.MATCHED, game.tap(b.id))
        assertEquals(1, game.remaining)
        assertNull(game.selected); assertNull(game.focusOrigin)
        assertTrue(game.undo())
        assertEquals(a.id, game.selected); assertEquals(MahjongCell(0,0), game.focusOrigin)
        assertEquals(3, game.remaining)
    }

    @Test fun tenUsesPipsAcrossSuitsButRequiresAnUnobstructedStraightLine() {
        val game = fullGrid(MahjongMode.TEN)
        game.tiles[0].face = 0
        game.tiles[1].face = 26
        game.tiles[2].face = 26
        game.tiles[8].face = 26
        assertTrue(game.compatible(game.tiles[0], game.tiles[1]))
        game.tap(0); assertEquals(MahjongGame.Result.MISMATCH, game.tap(2))
        game.tap(0); assertEquals(MahjongGame.Result.MISMATCH, game.tap(8))
        game.tap(0); assertEquals(MahjongGame.Result.MATCHED, game.tap(1))
        assertEquals(34, game.remaining)
        game.tiles[3].face = 13; game.tiles[4].face = 22
        assertTrue(game.compatible(game.tiles[3], game.tiles[4]))
        game.tap(3); assertEquals(MahjongGame.Result.MATCHED, game.tap(4))
    }

    @Test fun mistakesResetFlowButAssistsPreserveEarnedPoints() {
        val game = MahjongGame(MahjongMode.CASCADE, 812)
        play(game, game.solutionSteps.take(1))
        assertEquals(100, game.score)
        val a = game.board.first(); val b = game.board.first { it.face != a.face }
        game.tap(a.id); assertEquals(MahjongGame.Result.MISMATCH, game.tap(b.id))
        assertEquals(0, game.combo)
        game.shuffle()
        val sequence = game.solutionSteps.toList()
        play(game, sequence.take(1)); assertEquals(200, game.score)
        play(game, sequence.drop(1).take(1)); assertEquals(400, game.score)
        val bonus = MahjongCollection.comboBonus(game)
        game.undo(); assertEquals(200, game.score); assertEquals(2, game.peakCombo)
        play(game, sequence.drop(1).take(1))
        assertEquals(bonus, MahjongCollection.comboBonus(game))
    }

    private fun fullGrid(mode: MahjongMode) = MahjongGame(mode, 1, initializeBoard = false).apply {
        repeat(36) { tiles += MahjongTile(it, it % 6, it / 6, 0, it % 27) }
    }
}
