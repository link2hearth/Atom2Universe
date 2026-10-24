package com.Atom2Universe.app.games.atomblocks

/** Pure, deterministic rules for the 8 × 8 block puzzle. No clocks or paid resources. */
class AtomBlocksEngine(seed: Long = 1L) {
    enum class Mode { CLASSIC, ZEN, ADVENTURE, DAILY }

    data class Cell(val row: Int, val col: Int)
    data class Shape(val id: String, val cells: List<Cell>, val width: Int, val height: Int)
    data class Piece(val shapeId: String, val color: Int)
    data class Move(val slot: Int, val row: Int, val col: Int)
    data class ClearedCell(val row: Int, val col: Int, val color: Int, val gem: Int)
    data class Goal(val score: Int, val lines: Int, val gems: Int)
    data class MoveResult(
        val placed: List<Cell>,
        val cleared: List<ClearedCell>,
        val lines: Int,
        val combo: Int,
        val scoreGained: Int,
        val allClear: Boolean,
        val won: Boolean,
        val blocked: Boolean,
        val collectedGems: Int,
    )

    data class Frame(
        val board: IntArray,
        val gems: IntArray,
        val tray: List<Piece?>,
        val score: Int,
        val combo: Int,
        val lines: Int,
        val moves: Int,
        val collectedGems: Int,
        val nonClearingMoves: Int,
        val won: Boolean,
        val randomState: Long,
    )

    data class Snapshot(
        val version: Int = 2,
        val mode: Mode,
        val level: Int,
        val seed: Long,
        val state: Frame,
        val history: List<Frame> = emptyList(),
        val adventureVersion: Int = 2,
    )

    var board = IntArray(SIZE * SIZE)
        private set
    var gems = IntArray(SIZE * SIZE)
        private set
    var tray: List<Piece?> = List(3) { null }
        private set
    var score = 0
        private set
    var combo = 0
        private set
    var lines = 0
        private set
    var moves = 0
        private set
    var collectedGems = 0
        private set
    var nonClearingMoves = 0
        private set
    var won = false
        private set
    var mode = Mode.CLASSIC
        private set
    var level = 1
        private set
    var seed = seed
        private set
    val goal: Goal? get() = goalFor(mode, level, adventureVersion)
    val canUndo: Boolean get() = history.isNotEmpty()
    val blocked: Boolean get() = !won && tray.filterNotNull().none { fitsAnywhere(it, occupancy()) }

    private var randomState = seed
    private var adventureVersion = 2
    private val history = ArrayDeque<Frame>()

    init { newGame(Mode.CLASSIC, seed = seed) }

    fun newGame(mode: Mode, level: Int = 1, seed: Long = 1L) {
        this.mode = mode
        this.level = level.coerceIn(1, ADVENTURE_LEVELS)
        this.seed = seed
        adventureVersion = 2
        randomState = seed xor (this.level.toLong() * -7046029254386353131L)
        board = IntArray(SIZE * SIZE)
        gems = IntArray(SIZE * SIZE)
        score = 0
        combo = 0
        lines = 0
        moves = 0
        collectedGems = 0
        nonClearingMoves = 0
        won = false
        history.clear()
        if (mode == Mode.ADVENTURE || mode == Mode.DAILY) prepareBoard()
        tray = deal(occupancy())
    }

    fun canPlace(piece: Piece, row: Int, col: Int): Boolean {
        val shape = shapesById[piece.shapeId] ?: return false
        if (row < 0 || col < 0 || row > SIZE - shape.height || col > SIZE - shape.width) return false
        return shape.cells.all { board[(row + it.row) * SIZE + col + it.col] == 0 }
    }

    /** Preview the union of full rows and columns, so intersections are never counted twice. */
    fun previewClears(piece: Piece, row: Int, col: Int): List<Cell> {
        if (!canPlace(piece, row, col)) return emptyList()
        val shape = shape(piece.shapeId)
        val occupied = occupancy() or maskAt(shape, row, col)
        return cellsIn(clearInfo(occupied).mask)
    }

    fun place(slot: Int, row: Int, col: Int): MoveResult? {
        if (won || slot !in 0..2) return null
        val piece = tray[slot] ?: return null
        if (!canPlace(piece, row, col)) return null
        remember()
        val placed = shape(piece.shapeId).cells.map { Cell(row + it.row, col + it.col) }
        placed.forEach { board[it.row * SIZE + it.col] = piece.color }
        val clearing = clearInfo(occupancy())
        val cleared = cellsIn(clearing.mask).map {
            val index = it.row * SIZE + it.col
            ClearedCell(it.row, it.col, board[index], gems[index])
        }
        cleared.forEach { board[it.row * SIZE + it.col] = 0; gems[it.row * SIZE + it.col] = 0 }
        val gemsGained = cleared.count { it.gem != 0 }
        collectedGems = safeAdd(collectedGems, gemsGained)
        moves = safeAdd(moves, 1)
        lines = safeAdd(lines, clearing.lines)
        if (clearing.lines > 0) {
            combo = safeAdd(combo, 1)
            nonClearingMoves = 0
        } else {
            nonClearingMoves++
            if (nonClearingMoves >= 3) {
                combo = 0
                nonClearingMoves = 0
            }
        }
        val allClear = clearing.lines > 0 && board.all { it == 0 }
        // Placement: 5/cell; simultaneous lines: 100 × lines² × combo; empty board: 500.
        val rawGain = placed.size * 5L + 100L * clearing.lines * clearing.lines * combo +
            if (allClear) 500L else 0L
        val gained = rawGain.coerceAtMost(Int.MAX_VALUE.toLong() - score).toInt()
        score += gained
        tray = tray.toMutableList().also { it[slot] = null }
        won = reachedGoal(goal, score, lines, collectedGems)
        if (!won && tray.all { it == null }) tray = deal(occupancy())
        return MoveResult(placed, cleared, clearing.lines, combo, gained, allClear, won, blocked, gemsGained)
    }

    /** Free reroll, resetting only the combo. Zen can group this with the preceding move for undo. */
    fun rescue(rememberUndo: Boolean = true): Boolean {
        if (won) return false
        if (rememberUndo) remember()
        tray = deal(occupancy())
        combo = 0
        nonClearingMoves = 0
        return true
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        applyFrame(history.removeLast())
        return true
    }

    /** Prefer a first move from a complete solution of the current tray, with bounded search. */
    fun hint(): Move? {
        if (won) return null
        val occupied = occupancy()
        val remaining = tray.indices.fold(0) { mask, index -> if (tray[index] != null) mask or (1 shl index) else mask }
        val candidates = options(occupied, remaining, considerGoal = balancedAdventure)
        if (candidates.isEmpty()) return null
        val budget = SearchBudget(12_000)
        val failed = HashSet<SearchKey>()
        for (candidate in candidates) {
            if (candidate.wins) return Move(candidate.slot, candidate.placement.row, candidate.placement.col)
            val next = afterPlacement(occupied, candidate.placement.mask)
            if (canFinish(next, remaining and (1 shl candidate.slot).inv(), budget, failed)) {
                return Move(candidate.slot, candidate.placement.row, candidate.placement.col)
            }
            if (budget.left <= 0) break
        }
        return candidates.first().let { Move(it.slot, it.placement.row, it.placement.col) }
    }

    fun snapshot(): Snapshot = Snapshot(mode = mode, level = level, seed = seed,
        state = frame(), history = history.map(::copyFrame), adventureVersion = adventureVersion)

    /** Validate the whole payload first: a corrupt save must not partially replace a live game. */
    fun restore(snapshot: Snapshot): Boolean {
        if (snapshot.version !in 1..2 || snapshot.level !in 1..ADVENTURE_LEVELS || snapshot.history.size > MAX_UNDO) return false
        val rules = if (snapshot.version == 1) 1 else snapshot.adventureVersion
        if (rules !in 1..2) return false
        val expectedGoal = goalFor(snapshot.mode, snapshot.level, rules)
        if (!validFrame(snapshot.state, expectedGoal) || snapshot.history.any { !validFrame(it, expectedGoal) }) return false
        mode = snapshot.mode
        level = snapshot.level
        seed = snapshot.seed
        adventureVersion = rules
        history.clear()
        snapshot.history.forEach { history.addLast(copyFrame(it)) }
        applyFrame(snapshot.state)
        return true
    }

    private fun prepareBoard() {
        if (mode == Mode.ADVENTURE && adventureVersion >= 2) {
            prepareAdventureBoard()
            return
        }
        val difficulty = if (mode == Mode.DAILY) 12 else level
        val rows = 2 + (difficulty / 9).coerceAtMost(3)
        for (row in SIZE - rows until SIZE) {
            val holes = linkedSetOf<Int>()
            while (holes.size < 2 + (row % 2)) holes.add(nextInt(SIZE))
            for (col in 0 until SIZE) if (col !in holes) board[row * SIZE + col] = 1 + nextInt(6)
        }
        prepareGems()
    }

    /** Gradual density, with overlapping gaps connected to the free area above the puzzle. */
    private fun prepareAdventureBoard() {
        var cellsLeft = 10 + (level - 1) * 12 / (ADVENTURE_LEVELS - 1)
        var gapStart = nextInt(SIZE - 2)
        for (row in SIZE - 1 downTo 0) {
            if (cellsLeft == 0) break
            val gapWidth = 2 + row % 2
            gapStart = (gapStart + nextInt(3) - 1).coerceIn(0, SIZE - gapWidth)
            val available = (0 until SIZE).filter { it !in gapStart until gapStart + gapWidth }.toMutableList()
            while (cellsLeft > 0 && available.isNotEmpty()) {
                val col = available.removeAt(nextInt(available.size))
                board[row * SIZE + col] = 1 + nextInt(6)
                cellsLeft--
            }
        }
        prepareGems()
    }

    private fun prepareGems() {
        val neededGems = goal?.gems ?: 0
        if (neededGems > 0) {
            val available = board.indices.filter { board[it] != 0 }.toMutableList()
            repeat(neededGems) { index ->
                val cell = available.removeAt(nextInt(available.size))
                gems[cell] = 1 + index % 3
            }
        }
    }

    /**
     * Construct a legal three-move sequence, clearing simulated lines after each move.
     * This guarantees every deal has a solution, without changing the player's board.
     */
    private fun deal(initial: Long): List<Piece?> {
        var occupied = initial
        var remainingGems = occupancy(gems)
        val result = ArrayList<Piece?>(3)
        repeat(3) {
            val available = SHAPES.mapNotNull { candidate ->
                val positions = placements.getValue(candidate.id).filter { occupied and it.mask == 0L }
                if (positions.isEmpty()) null else candidate to positions
            }
            // A single cell always fits: a completely occupied board would already have cleared.
            check(available.isNotEmpty())
            val weights = available.map { (shape, positions) ->
                if (balancedAdventure) adventureWeight(shape, positions, occupied, remainingGems)
                else shapeWeight(shape.cells.size)
            }
            var ticket = nextInt(weights.sum())
            var selected = 0
            while (ticket >= weights[selected]) ticket -= weights[selected++]
            val (shape, positions) = available[selected]
            val position = positions[nextInt(positions.size)]
            result += Piece(shape.id, 1 + nextInt(6))
            val filled = occupied or position.mask
            val cleared = clearInfo(filled).mask
            occupied = filled and cleared.inv()
            remainingGems = remainingGems and cleared.inv()
        }
        return result
    }

    private val balancedAdventure: Boolean get() = mode == Mode.ADVENTURE && adventureVersion >= 2

    /** Board-based weights: helpful pieces stay available regardless of wins, losses or purchases. */
    private fun adventureWeight(shape: Shape, positions: List<Placement>, occupied: Long, gemMask: Long): Int {
        var weight = when (shape.cells.size) { 1 -> 4; 2 -> 5; else -> shapeWeight(shape.cells.size) }
        var canClear = false
        var canCollect = false
        for (position in positions) {
            val clearing = clearInfo(occupied or position.mask)
            canClear = canClear || clearing.lines > 0
            canCollect = canCollect || clearing.mask and gemMask != 0L
        }
        weight *= if (canCollect) 4 else if (canClear) 2 else 1
        // A fragmented gap cannot receive a large block. Give small repair pieces a fair chance
        // even when one placement alone would not yet finish the gem's row or column.
        if (gemMask != 0L && shape.cells.size <= 3) {
            val repairable = (rowMasks.asSequence() + colMasks.asSequence()).any { line ->
                val holes = line and occupied.inv()
                line and gemMask != 0L && java.lang.Long.bitCount(holes) in 1..3 &&
                    positions.any { it.mask and holes == it.mask }
            }
            if (repairable) weight += 30
        }
        return weight
    }

    private fun options(occupied: Long, remaining: Int, considerGoal: Boolean = false): List<Option> {
        val candidates = ArrayList<Option>()
        val gemMask = if (considerGoal) occupancy(gems) else 0L
        for (slot in tray.indices) {
            if (remaining and (1 shl slot) == 0) continue
            val piece = tray[slot] ?: continue
            for (placement in placements.getValue(piece.shapeId)) {
                if (occupied and placement.mask != 0L) continue
                val clearing = clearInfo(occupied or placement.mask)
                var preference = clearing.lines * 1000 + java.lang.Long.bitCount(placement.mask) * 10 + placement.row
                var wins = false
                if (considerGoal) {
                    val after = (occupied or placement.mask) and clearing.mask.inv()
                    val collected = java.lang.Long.bitCount(clearing.mask and gemMask)
                    val gain = java.lang.Long.bitCount(placement.mask) * 5L +
                        100L * clearing.lines * clearing.lines * (combo.toLong() + 1L) +
                        if (after == 0L && clearing.lines > 0) 500L else 0L
                    wins = reachedGoal(goal, (score + gain).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
                        safeAdd(lines, clearing.lines), safeAdd(collectedGems, collected))
                    val empty = after.inv()
                    val neighbors = ((empty and colMasks[7].inv()) shl 1) or
                        ((empty and colMasks[0].inv()) ushr 1) or (empty shl SIZE) or (empty ushr SIZE)
                    val isolated = java.lang.Long.bitCount(empty and neighbors.inv())
                    val room = placements.getValue("square2").count { after and it.mask == 0L } +
                        3 * placements.getValue("square3").count { after and it.mask == 0L }
                    preference += collected * 2500 + room * 6 - isolated * 60 - java.lang.Long.bitCount(after) * 6
                    if (wins) preference = Int.MAX_VALUE
                }
                candidates += Option(slot, placement, preference, wins)
            }
        }
        return candidates.sortedByDescending { it.preference }
    }

    private fun canFinish(occupied: Long, remaining: Int, budget: SearchBudget, failed: MutableSet<SearchKey>): Boolean {
        if (remaining == 0) return true
        if (--budget.left <= 0) return false
        val key = SearchKey(occupied, remaining)
        if (key in failed) return false
        for (candidate in options(occupied, remaining)) {
            if (canFinish(afterPlacement(occupied, candidate.placement.mask),
                    remaining and (1 shl candidate.slot).inv(), budget, failed)) return true
            if (budget.left <= 0) return false
        }
        failed += key
        return false
    }

    private fun remember() {
        if (history.size >= MAX_UNDO) history.removeFirst()
        history.addLast(frame())
    }

    private fun frame() = Frame(board.copyOf(), gems.copyOf(), tray.toList(), score, combo, lines,
        moves, collectedGems, nonClearingMoves, won, randomState)

    private fun applyFrame(frame: Frame) {
        board = frame.board.copyOf()
        gems = frame.gems.copyOf()
        tray = frame.tray.toList()
        score = frame.score
        combo = frame.combo
        lines = frame.lines
        moves = frame.moves
        collectedGems = frame.collectedGems
        nonClearingMoves = frame.nonClearingMoves
        won = frame.won
        randomState = frame.randomState
    }

    private fun validFrame(frame: Frame, goal: Goal?): Boolean {
        if (frame.board.size != SIZE * SIZE || frame.gems.size != SIZE * SIZE || frame.tray.size != 3) return false
        if (frame.board.any { it !in 0..6 } || frame.gems.any { it !in 0..3 }) return false
        if (frame.board.indices.any { frame.gems[it] != 0 && frame.board[it] == 0 }) return false
        if (frame.tray.any { it != null && (it.shapeId !in shapesById || it.color !in 1..6) }) return false
        if (listOf(frame.score, frame.combo, frame.lines, frame.moves, frame.collectedGems).any { it < 0 }) return false
        if (frame.nonClearingMoves !in 0..2 || clearInfo(occupancy(frame.board)).lines != 0) return false
        if (frame.won != reachedGoal(goal, frame.score, frame.lines, frame.collectedGems)) return false
        if (!frame.won && frame.tray.all { it == null }) return false
        if (goal != null && goal.gems > frame.collectedGems + frame.gems.count { it != 0 }) return false
        return true
    }

    private fun occupancy(cells: IntArray = board): Long {
        var mask = 0L
        cells.indices.forEach { if (cells[it] != 0) mask = mask or (1L shl it) }
        return mask
    }

    private fun fitsAnywhere(piece: Piece, occupied: Long) = placements.getValue(piece.shapeId).any { occupied and it.mask == 0L }

    /** SplitMix64: the complete random state is a single serializable Long, including zero. */
    private fun nextInt(bound: Int): Int {
        randomState += -7046029254386353131L
        var value = randomState
        value = (value xor (value ushr 30)) * -4658895280553007687L
        value = (value xor (value ushr 27)) * -7723592293110705685L
        value = value xor (value ushr 31)
        return ((value ushr 1) % bound).toInt()
    }

    private data class Placement(val row: Int, val col: Int, val mask: Long)
    private data class Clearing(val mask: Long, val lines: Int)
    private data class Option(val slot: Int, val placement: Placement, val preference: Int, val wins: Boolean = false)
    private data class SearchKey(val occupied: Long, val remaining: Int)
    private class SearchBudget(var left: Int)

    companion object {
        const val SIZE = 8
        const val ADVENTURE_LEVELS = 36
        private const val MAX_UNDO = 200

        /** Fixed orientations: rotating pieces is intentionally not part of placement rules. */
        val SHAPES: List<Shape> = buildList {
            fun addShape(id: String, vararg rows: String) {
                val cells = rows.flatMapIndexed { row, text -> text.mapIndexedNotNull { col, char ->
                    if (char == '#') Cell(row, col) else null
                } }
                add(Shape(id, cells, cells.maxOf { it.col } + 1, cells.maxOf { it.row } + 1))
            }
            addShape("single", "#")
            for (length in 2..5) {
                addShape("line${length}h", "#".repeat(length))
                addShape("line${length}v", *Array(length) { "#" })
            }
            addShape("square2", "##", "##")
            addShape("square3", "###", "###", "###")
            addShape("rect2x3", "###", "###")
            addShape("rect3x2", "##", "##", "##")
            addShape("corner3a", "##", "#.")
            addShape("corner3b", "##", ".#")
            addShape("corner3c", "#.", "##")
            addShape("corner3d", ".#", "##")
            addShape("l4a", "#.", "#.", "##")
            addShape("l4b", ".#", ".#", "##")
            addShape("l4c", "##", "#.", "#.")
            addShape("l4d", "##", ".#", ".#")
            addShape("l4e", "###", "#..")
            addShape("l4f", "###", "..#")
            addShape("l4g", "#..", "###")
            addShape("l4h", "..#", "###")
            addShape("l5a", "#..", "#..", "###")
            addShape("l5b", "..#", "..#", "###")
            addShape("l5c", "###", "#..", "#..")
            addShape("l5d", "###", "..#", "..#")
            addShape("t4a", "###", ".#.")
            addShape("t4b", ".#.", "###")
            addShape("t4c", "#.", "##", "#.")
            addShape("t4d", ".#", "##", ".#")
            addShape("s4h", ".##", "##.")
            addShape("z4h", "##.", ".##")
            addShape("s4v", "#.", "##", ".#")
            addShape("z4v", ".#", "##", "#.")
            addShape("diagonal2a", "#.", ".#")
            addShape("diagonal2b", ".#", "#.")
            addShape("diagonal3a", "#..", ".#.", "..#")
            addShape("diagonal3b", "..#", ".#.", "#..")
        }
        private val shapesById = SHAPES.associateBy { it.id }
        private val rowMasks = LongArray(SIZE) { row -> 255L shl (row * SIZE) }
        private val colMasks = LongArray(SIZE) { col -> (0 until SIZE).fold(0L) { mask, row -> mask or (1L shl (row * SIZE + col)) } }
        private val placements = SHAPES.associate { shape -> shape.id to buildList {
            for (row in 0..SIZE - shape.height) for (col in 0..SIZE - shape.width) {
                add(Placement(row, col, maskAt(shape, row, col)))
            }
        } }

        fun shape(id: String): Shape = requireNotNull(shapesById[id]) { "Unknown shape: $id" }

        private fun maskAt(shape: Shape, row: Int, col: Int): Long = shape.cells.fold(0L) { mask, cell ->
            mask or (1L shl ((row + cell.row) * SIZE + col + cell.col))
        }

        private fun clearInfo(occupied: Long): Clearing {
            var clear = 0L
            var lines = 0
            rowMasks.forEach { if (occupied and it == it) { clear = clear or it; lines++ } }
            colMasks.forEach { if (occupied and it == it) { clear = clear or it; lines++ } }
            return Clearing(clear, lines)
        }

        private fun afterPlacement(occupied: Long, piece: Long): Long {
            val result = occupied or piece
            return result and clearInfo(result).mask.inv()
        }

        private fun cellsIn(mask: Long): List<Cell> = (0 until SIZE * SIZE).mapNotNull {
            if (mask and (1L shl it) != 0L) Cell(it / SIZE, it % SIZE) else null
        }

        private fun shapeWeight(size: Int): Int = when (size) { 1 -> 1; 2 -> 3; 3 -> 5; 4 -> 6; 5 -> 4; 6 -> 3; else -> 1 }
        private fun copyFrame(frame: Frame) = frame.copy(board = frame.board.copyOf(), gems = frame.gems.copyOf(), tray = frame.tray.toList())
        private fun safeAdd(a: Int, b: Int): Int = (a.toLong() + b).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        private fun goalFor(mode: Mode, level: Int, adventureVersion: Int): Goal? = when (mode) {
            Mode.CLASSIC, Mode.ZEN -> null
            Mode.DAILY -> Goal(score = 2_500, lines = 12, gems = 0)
            Mode.ADVENTURE -> if (adventureVersion >= 2) Goal(score = 500 + level * 60,
                lines = 3 + (level - 1) / 4, gems = if (level % 3 == 0) 2 + level / 9 else 0)
                else if (level % 3 == 0) Goal(score = 450 + level * 65, lines = 0, gems = 2 + level / 9)
                else Goal(score = 350 + level * 65, lines = 2 + level / 4, gems = 0)
        }
        private fun reachedGoal(goal: Goal?, score: Int, lines: Int, gems: Int): Boolean = goal != null &&
            score >= goal.score && lines >= goal.lines && gems >= goal.gems
    }
}
