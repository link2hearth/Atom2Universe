package com.Atom2Universe.app.games.atomblocks

import com.Atom2Universe.app.games.atomblocks.AtomBlocksEngine.Mode
import com.Atom2Universe.app.games.atomblocks.AtomBlocksEngine.Piece
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AtomBlocksEngineTest {
    private val single = Piece("single", 2)

    @Test
    fun crossingRowsAndColumnsClearSimultaneouslyAndCountIntersectionOnce() {
        val engine = AtomBlocksEngine()
        val board = IntArray(64)
        for (col in 0..7) if (col != 4) board[3 * 8 + col] = 1
        for (row in 0..7) if (row != 3) board[row * 8 + 4] = 3
        val gems = IntArray(64).also { it[3 * 8] = 1; it[1 * 8 + 4] = 2 }
        setBoard(engine, board, listOf(single, single, single), gems)

        assertEquals(15, engine.previewClears(single, 3, 4).size)
        val result = engine.place(0, 3, 4)!!

        assertEquals(2, result.lines)
        assertEquals(15, result.cleared.size)
        assertEquals(15, result.cleared.map { it.row to it.col }.toSet().size)
        assertEquals(2, result.collectedGems)
        assertEquals(2, engine.collectedGems)
        assertTrue(result.allClear)
        assertEquals(905, result.scoreGained)
        assertTrue(engine.board.all { it == 0 })
        assertTrue(engine.gems.all { it == 0 })
    }

    @Test
    fun invalidPlacementDoesNotConsumePieceOrChangeHistory() {
        val engine = AtomBlocksEngine()
        val board = IntArray(64).also { it[0] = 4 }
        setBoard(engine, board, listOf(single, Piece("square3", 1), null))
        val before = engine.snapshot()

        assertNull(engine.place(-1, 0, 0))
        assertNull(engine.place(0, 0, 0))
        assertNull(engine.place(1, 7, 7))
        assertNull(engine.place(2, 4, 4))
        assertNull(engine.place(0, Int.MAX_VALUE, 0))
        assertFrameEquals(before.state, engine.snapshot().state)
        assertFalse(engine.canUndo)
    }

    @Test
    fun comboSurvivesTwoQuietMovesAndResetsOnThird() {
        val engine = AtomBlocksEngine()
        val board = IntArray(64).also { cells -> for (col in 0..6) cells[col] = 1 }
        setBoard(engine, board, listOf(single, single, single))
        assertEquals(1, engine.place(0, 0, 7)!!.combo)
        assertEquals(1, engine.place(1, 2, 0)!!.combo)
        assertEquals(1, engine.place(2, 2, 1)!!.combo)
        assertEquals(2, engine.nonClearingMoves)
        // Fix only the tray after its automatic refill, keeping the real two-move grace period.
        val current = engine.snapshot()
        assertTrue(engine.restore(current.copy(state = current.state.copy(tray = listOf(single, single, single)))))
        assertEquals(0, engine.place(0, 2, 2)!!.combo)
        assertEquals(0, engine.nonClearingMoves)
    }

    @Test
    fun anotherClearWithinGracePeriodIncrementsCombo() {
        val engine = AtomBlocksEngine()
        val board = IntArray(64).also { cells ->
            for (col in 0..6) { cells[col] = 1; cells[8 + col] = 3 }
        }
        setBoard(engine, board, listOf(single, single, single))
        assertEquals(1, engine.place(0, 0, 7)!!.combo)
        engine.place(1, 3, 3)
        val next = engine.place(2, 1, 7)!!
        assertEquals(2, next.combo)
        assertEquals(205, next.scoreGained)
    }

    @Test
    fun blockedChecksEveryRemainingPieceAndIgnoresUsedSlots() {
        val engine = AtomBlocksEngine()
        val checkerboard = IntArray(64) { if ((it / 8 + it % 8) % 2 == 0) 1 else 0 }
        setBoard(engine, checkerboard, listOf(Piece("square3", 1), single, null))
        assertFalse(engine.blocked)
        assertNotNull(engine.hint())
        engine.place(1, 0, 1)
        assertTrue(engine.blocked)
        assertNull(engine.hint())
        val unchangedBoard = engine.board.copyOf()
        assertTrue(engine.rescue())
        assertFalse(engine.blocked)
        assertArrayEquals(unchangedBoard, engine.board)
    }

    @Test
    fun undoRestoresRandomStateIncludingAutomaticDeal() {
        val engine = AtomBlocksEngine(42L)
        setBoard(engine, IntArray(64), listOf(null, null, single))
        val before = engine.snapshot()
        engine.place(2, 4, 4)!!
        val firstDeal = engine.snapshot()
        assertTrue(engine.undo())
        assertFrameEquals(before.state, engine.snapshot().state)
        engine.place(2, 4, 4)!!
        assertFrameEquals(firstDeal.state, engine.snapshot().state)
    }

    @Test
    fun zenAutomaticRescueAndPlacementUndoAsOneAction() {
        val engine = AtomBlocksEngine()
        engine.newGame(Mode.ZEN)
        val checkerboard = IntArray(64) { if ((it / 8 + it % 8) % 2 == 0) 1 else 0 }
        setBoard(engine, checkerboard, listOf(Piece("square3", 1), single, null))
        val before = engine.snapshot()
        assertTrue(engine.place(1, 0, 1)!!.blocked)
        assertTrue(engine.rescue(rememberUndo = false))
        assertFalse(engine.blocked)
        assertTrue(engine.undo())
        assertFrameEquals(before.state, engine.snapshot().state)
        assertFalse(engine.canUndo)
    }

    @Test
    fun rescueStartsAFreshComboAndCanBeUndone() {
        val engine = AtomBlocksEngine()
        val board = IntArray(64).also { cells -> for (col in 0..6) cells[col] = 1 }
        setBoard(engine, board, listOf(single, single, single))
        engine.place(0, 0, 7)
        engine.place(1, 3, 3)
        val before = engine.snapshot()
        assertEquals(1, engine.combo)
        assertEquals(1, engine.nonClearingMoves)
        assertTrue(engine.rescue())
        assertEquals(0, engine.combo)
        assertEquals(0, engine.nonClearingMoves)
        assertTrue(engine.undo())
        assertFrameEquals(before.state, engine.snapshot().state)
    }

    @Test
    fun saveRestorePreservesContinuationAndUndoHistory() {
        val original = AtomBlocksEngine(90L)
        repeat(5) {
            val move = original.hint()!!
            original.place(move.slot, move.row, move.col)
        }
        val restored = AtomBlocksEngine(1L)
        assertTrue(restored.restore(original.snapshot()))
        assertTrue(restored.canUndo)
        repeat(8) {
            val move = original.hint()!!
            val other = restored.hint()!!
            assertEquals(move, other)
            assertEquals(original.place(move.slot, move.row, move.col), restored.place(other.slot, other.row, other.col))
            assertFrameEquals(original.snapshot().state, restored.snapshot().state)
        }
        assertTrue(original.undo())
        assertTrue(restored.undo())
        assertFrameEquals(original.snapshot().state, restored.snapshot().state)
    }

    @Test
    fun corruptSaveIsRejectedAtomicallyAndSnapshotsAreDetached() {
        val engine = AtomBlocksEngine(7L)
        val before = engine.snapshot()
        val badGem = IntArray(64).also { it[0] = 2 }
        assertFalse(engine.restore(before.copy(state = before.state.copy(gems = badGem))))
        assertFalse(engine.restore(before.copy(version = 9)))
        assertFalse(engine.restore(before.copy(state = before.state.copy(tray = listOf(Piece("missing", 1), null, null)))))
        assertFalse(engine.restore(before.copy(state = before.state.copy(nonClearingMoves = 3))))
        assertFrameEquals(before.state, engine.snapshot().state)
        before.state.board[0] = 5
        assertEquals(0, engine.board[0])
    }

    @Test
    fun everyInitialDealAndFreeRescueHasACompleteLegalSequence() {
        // Rule regression over deterministic seeds; intentionally small, not a tuning simulation.
        for (seed in 0L..31L) {
            val engine = AtomBlocksEngine(seed)
            engine.newGame(Mode.ADVENTURE, level = 1 + (seed % 36).toInt(), seed = seed)
            assertTrue("initial seed=$seed", trayCanFinish(engine.board, engine.tray))
            assertTrue(engine.rescue())
            assertTrue("rescue seed=$seed", trayCanFinish(engine.board, engine.tray))
        }
    }

    @Test
    fun hintKeepsRemainingTrayPlayableAcrossRefills() {
        val engine = AtomBlocksEngine(122L)
        repeat(24) {
            assertFalse(engine.blocked)
            val move = engine.hint()!!
            assertNotNull(engine.place(move.slot, move.row, move.col))
            assertFalse(engine.blocked)
        }
    }

    @Test
    fun adventureObjectivesHaveEnoughPersistentGemsAndLevelsAreDeterministic() {
        for (level in 1..AtomBlocksEngine.ADVENTURE_LEVELS) {
            val first = AtomBlocksEngine()
            val second = AtomBlocksEngine()
            first.newGame(Mode.ADVENTURE, level, 400L)
            second.newGame(Mode.ADVENTURE, level, 400L)
            assertFrameEquals(first.snapshot().state, second.snapshot().state)
            val goal = first.goal!!
            assertEquals(goal.gems, first.gems.count { it != 0 })
            val savedGems = first.gems.copyOf()
            first.rescue()
            assertArrayEquals(savedGems, first.gems)
            assertTrue(first.undo())
            assertFrameEquals(second.snapshot().state, first.snapshot().state)
        }
    }

    @Test
    fun adventureOpeningsStayConnectedAndDensityRisesGraduallyAcrossThe36PlayedSeeds() {
        var previousOccupied = 0
        for (level in 1..AtomBlocksEngine.ADVENTURE_LEVELS) {
            val engine = AtomBlocksEngine().apply { newGame(Mode.ADVENTURE, level, 999L + level) }
            val board = engine.board
            val occupied = board.count { it != 0 }
            assertTrue("level=$level density=$occupied", occupied in 10..22)
            if (level > 1) assertTrue("level=$level density jump", occupied - previousOccupied in 0..1)
            if (level == 1) assertEquals(10, occupied)
            if (level == AtomBlocksEngine.ADVENTURE_LEVELS) assertEquals(22, occupied)
            previousOccupied = occupied
            assertTrue("level=$level upper space", (0 until 32).all { board[it] == 0 })
            for (line in 0..7) {
                assertTrue("level=$level full row=$line", (0..7).any { board[line * 8 + it] == 0 })
                assertTrue("level=$level full column=$line", (0..7).any { board[it * 8 + line] == 0 })
            }

            // Flood fill the real cells rather than reproducing the generator's gap calculations.
            val reachable = mutableSetOf(0)
            val pending = ArrayDeque<Int>().apply { addLast(0) }
            while (pending.isNotEmpty()) {
                val cell = pending.removeFirst()
                val row = cell / 8
                val col = cell % 8
                val neighbors = listOf(row - 1 to col, row + 1 to col, row to col - 1, row to col + 1)
                for ((nextRow, nextCol) in neighbors) {
                    if (nextRow !in 0..7 || nextCol !in 0..7) continue
                    val next = nextRow * 8 + nextCol
                    if (board[next] == 0 && reachable.add(next)) pending.addLast(next)
                }
            }
            assertEquals("level=$level enclosed empty cells", 64 - occupied, reachable.size)
        }
    }

    @Test
    fun finalGemLevelStillRequiresItsLineObjectiveAfterCollectingEveryGem() {
        val engine = AtomBlocksEngine().apply { newGame(Mode.ADVENTURE, 36, 1035L) }
        val goal = engine.goal!!
        assertTrue(goal.lines >= 3)
        assertEquals(6, goal.gems)
        val board = IntArray(64).also { cells ->
            for (col in 0..6) { cells[col] = 1; cells[8 + col] = 2 }
        }
        val gems = IntArray(64).also { cells -> for (col in 0 until goal.gems) cells[col] = 1 }
        val snapshot = engine.snapshot()
        assertTrue(engine.restore(snapshot.copy(state = snapshot.state.copy(
            board = board, gems = gems, tray = listOf(single, single, null),
            score = goal.score, lines = goal.lines - 2,
        ))))

        val collected = engine.place(0, 0, 7)!!
        assertEquals(goal.gems, engine.collectedGems)
        assertTrue(engine.score >= goal.score)
        assertEquals(goal.lines - 1, engine.lines)
        assertFalse(collected.won)
        assertFalse(engine.won)
        assertTrue(engine.place(1, 1, 7)!!.won)
        assertEquals(goal.lines, engine.lines)
    }

    @Test
    fun adventureHintPrefersTheGemRowWhenTwoRowsCanBeCleared() {
        val engine = AtomBlocksEngine().apply { newGame(Mode.ADVENTURE, 3, 1002L) }
        val board = IntArray(64).also { cells ->
            for (col in 0..6) { cells[col] = 1; cells[7 * 8 + col] = 2 }
        }
        val gems = IntArray(64).also { it[0] = 1; it[1] = 2 }
        setBoard(engine, board, listOf(single, single, null), gems)

        // Old saves retain their prior bottom-row preference and their original rules.
        val legacy = AtomBlocksEngine()
        assertTrue(legacy.restore(engine.snapshot().copy(version = 1)))
        assertEquals(7, legacy.hint()!!.row)
        val move = engine.hint()!!
        assertEquals(0, move.row)
        assertEquals(7, move.col)
        assertEquals(2, engine.place(move.slot, move.row, move.col)!!.collectedGems)
        assertEquals(1, engine.lines)
        assertFalse(engine.won)
    }

    @Test
    fun adventureHintTakesImmediateVictoryEvenWhenTheRemainingPieceCannotFit() {
        val engine = AtomBlocksEngine().apply { newGame(Mode.ADVENTURE, 3, 1002L) }
        val large = Piece("square3", 3)
        val board = IntArray(64) { if ((it / 8 + it % 8) % 2 == 0) 1 else 0 }.also { cells ->
            for (col in 0..7) {
                cells[2 * 8 + col] = if (col < 7) 1 else 0
                cells[7 * 8 + col] = if (col < 7) 2 else 0
            }
            for (row in listOf(1, 3)) for (col in 0..2) cells[row * 8 + col] = 0
        }
        val gems = IntArray(64).also { it[7 * 8] = 1; it[7 * 8 + 1] = 2 }
        val goal = engine.goal!!
        val snapshot = engine.snapshot()
        assertTrue(engine.restore(snapshot.copy(state = snapshot.state.copy(
            board = board, gems = gems, tray = listOf(single, large, null),
            score = goal.score, lines = goal.lines,
        ))))

        // The other clear makes a 3 × 3 space and finishes the tray, but leaves the gems behind.
        val alternative = AtomBlocksEngine()
        assertTrue(alternative.restore(engine.snapshot()))
        assertFalse(alternative.place(0, 2, 7)!!.won)
        assertTrue(alternative.canPlace(large, 1, 0))

        val move = engine.hint()!!
        assertEquals(AtomBlocksEngine.Move(0, 7, 7), move)
        assertTrue(engine.place(move.slot, move.row, move.col)!!.won)
        assertEquals(large, engine.tray[1])
        assertFalse((0..5).any { row -> (0..5).any { col -> engine.canPlace(large, row, col) } })
        assertFalse(engine.blocked)
    }

    @Test
    fun winningRequiresAllObjectivesAndCanBeUndone() {
        val engine = AtomBlocksEngine()
        engine.newGame(Mode.ADVENTURE, 1, 3L)
        val board = IntArray(64).also { cells -> for (col in 0..6) cells[col] = 1 }
        val snapshot = engine.snapshot()
        assertTrue(engine.restore(snapshot.copy(state = snapshot.state.copy(board = board, gems = IntArray(64),
            tray = listOf(single, null, null), score = engine.goal!!.score, lines = engine.goal!!.lines - 1))))
        assertFalse(engine.won)
        assertTrue(engine.place(0, 0, 7)!!.won)
        assertTrue(engine.won)
        assertFalse(engine.blocked)
        assertFalse(engine.rescue())
        assertNull(engine.place(0, 1, 1))
        assertTrue(engine.undo())
        assertFalse(engine.won)
    }

    @Test
    fun dailySeedIsRepeatableAndClassicHasNoArtificialFinish() {
        val a = AtomBlocksEngine()
        val b = AtomBlocksEngine()
        a.newGame(Mode.DAILY, seed = 20749L)
        b.newGame(Mode.DAILY, seed = 20749L)
        assertFrameEquals(a.snapshot().state, b.snapshot().state)
        assertNotNull(a.goal)
        a.newGame(Mode.CLASSIC, seed = 20749L)
        assertNull(a.goal)
        assertFalse(a.won)
    }

    private fun setBoard(engine: AtomBlocksEngine, board: IntArray, tray: List<Piece?>, gems: IntArray = IntArray(64)) {
        val previous = engine.snapshot()
        assertTrue(engine.restore(previous.copy(state = previous.state.copy(board = board, gems = gems, tray = tray), history = emptyList())))
    }

    private fun assertFrameEquals(a: AtomBlocksEngine.Frame, b: AtomBlocksEngine.Frame) {
        assertArrayEquals(a.board, b.board)
        assertArrayEquals(a.gems, b.gems)
        assertEquals(a.copy(board = b.board, gems = b.gems), b)
    }

    /** Independent exact search over the three-piece tray, including simultaneous line clears. */
    private fun trayCanFinish(board: IntArray, tray: List<Piece?>): Boolean {
        if (tray.all { it == null }) return true
        for (slot in tray.indices) {
            val piece = tray[slot] ?: continue
            val shape = AtomBlocksEngine.shape(piece.shapeId)
            for (row in 0..8 - shape.height) for (col in 0..8 - shape.width) {
                val cells = shape.cells.map { (row + it.row) * 8 + col + it.col }
                if (cells.any { board[it] != 0 }) continue
                val next = board.copyOf().also { target -> cells.forEach { target[it] = piece.color } }
                val clear = mutableSetOf<Int>()
                for (line in 0..7) {
                    if ((0..7).all { next[line * 8 + it] != 0 }) (0..7).forEach { clear += line * 8 + it }
                    if ((0..7).all { next[it * 8 + line] != 0 }) (0..7).forEach { clear += it * 8 + line }
                }
                clear.forEach { next[it] = 0 }
                val remaining = tray.toMutableList().also { it[slot] = null }
                if (trayCanFinish(next, remaining)) return true
            }
        }
        return false
    }
}
