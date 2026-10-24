package com.Atom2Universe.app.games.mahjong

import kotlin.random.Random

/** Each mode changes the legal decisions, not just the decoration. */
enum class MahjongMode { CLASSIC, TRIPLE, CONNECT, CASCADE, SLIDE, TEN }
data class MahjongTile(val id: Int, var x: Int, var y: Int, var z: Int, var face: Int, var removed: Boolean = false)
data class MahjongCell(val x: Int, val y: Int)
data class MahjongSlide(val id: Int, val x: Int, val y: Int)
internal data class MahjongSolutionStep(val ids: List<Int> = emptyList(), val slide: MahjongSlide? = null)
data class MahjongSnapshot(
    val tiles: List<MahjongTile>, val tray: List<Int>, val revealed: List<Int>,
    val score: Int, val combo: Int, val matches: Int, val moves: Int, val assists: Int, val peakCombo: Int = 0,
    val focusId: Int? = null, val focusOrigin: MahjongCell? = null
)

class MahjongGame(val mode: MahjongMode, val seed: Int, val stage: Int = 1, initializeBoard: Boolean = true) {
    enum class Result { SELECTED, MATCHED, MOVED, MISMATCH, BLOCKED, TRAY_FULL, IGNORED }
    val tiles = mutableListOf<MahjongTile>()
    val tray = mutableListOf<Int>()
    // Empty compatibility field for snapshots written before the puzzle redesign.
    val revealed = mutableListOf<Int>()
    val history = mutableListOf<MahjongSnapshot>()
    var selected: Int? = null
        private set
    var focusOrigin: MahjongCell? = null
        private set
    var score = 0
        private set
    var combo = 0
        private set
    var matches = 0
        private set
    var moves = 0
        private set
    var assists = 0
        private set
    var peakCombo = 0
        private set
    var lastMatched: List<Int> = emptyList()
        private set
    var lastPath: List<MahjongCell> = emptyList()
        private set
    /** Complete construction witnesses, including the necessary slides. */
    internal val solution = mutableListOf<List<Int>>()
    internal val solutionSteps = mutableListOf<MahjongSolutionStep>()
    internal var generation: MahjongProceduralGenerator.Report? = null
    private var iceHintState: MahjongSnapshot? = null
    private var iceHint: Pair<List<Int>, MahjongSlide?> = emptyList<Int>() to null
    private var puzzleHintState: MahjongSnapshot? = null
    private var puzzleHint: List<Int> = emptyList()
    var hintSearchLimited = false
        private set
    val columns = 6
    val rows get() = if (stage < 4) 6 else 8
    val board get() = tiles.filter { !it.removed && it.id !in tray }
    val remaining get() = tiles.count { !it.removed }
    val complete get() = remaining == 0
    /** Direction of the next fall: down, left, up, right. */
    val gravityDirection get() = matches % 4

    init { if (initializeBoard) generate() }

    private fun generate() {
        if (mode == MahjongMode.SLIDE) {
            repeat(MahjongIceGenerator.tileCount(stage)) { index ->
                tiles += MahjongTile(index, index % columns, index / columns, 0, 0)
            }
            deal(Random(seed))
            return
        }
        repeat(MahjongPuzzleGenerator.tileCount(mode, stage)) { index ->
            tiles += MahjongTile(index, index % columns, index / columns, 0, 0)
        }
        deal(Random(seed))
    }

    fun isFree(tile: MahjongTile): Boolean {
        if (tile.removed || tile.id in tray) return false
        if (mode != MahjongMode.CLASSIC && mode != MahjongMode.TRIPLE) return true
        val live = board
        if (live.any { it.x == tile.x && it.y == tile.y && it.z > tile.z }) return false
        return mode == MahjongMode.TRIPLE ||
            live.none { it.z == tile.z && it.y == tile.y && it.x == tile.x - 1 } ||
            live.none { it.z == tile.z && it.y == tile.y && it.x == tile.x + 1 }
    }

    private fun deal(random: Random) {
        solution.clear(); solutionSteps.clear()
        if (mode != MahjongMode.SLIDE && MahjongPuzzleGenerator.deal(this, random)) return
        when (mode) {
            MahjongMode.CLASSIC -> dealClassic(random)
            MahjongMode.TRIPLE -> dealTriple(random)
            MahjongMode.SLIDE -> dealSlides(random)
            else -> dealPaths(random)
        }
        if (solutionSteps.isEmpty()) solutionSteps += solution.map { MahjongSolutionStep(it) }
    }

    private fun dealClassic(random: Random) {
        val live = board.toList()
        while (board.isNotEmpty()) {
            // Both members must be free before either is removed. Each layer is even.
            val highest = board.maxOf { it.z }
            val group = board.filter { it.z == highest && isFree(it) }.shuffled(random).take(2)
            check(group.size == 2)
            val face = random.nextInt(if (stage < 3) 18 else 27)
            group.forEach { it.face = face; it.removed = true }
            solution += group.map { it.id }
        }
        live.forEach { it.removed = false }
    }

    private fun dealTriple(random: Random) {
        val live = board.toList()
        val order = mutableListOf<MahjongTile>()
        while (board.isNotEmpty()) {
            val highest = board.maxOf { it.z }
            val options = board.filter { it.z == highest && isFree(it) }
            val coveredAtStart = options.filter { candidate ->
                live.any { it.x == candidate.x && it.y == candidate.y && it.z > candidate.z }
            }
            val tile = coveredAtStart.ifEmpty { options }.random(random)
            order += tile; tile.removed = true
        }
        // A B C A B C A B C occupies six slots before the first triple clears.
        // Six summit tiles hide their thirds in the middle layer.
        val palette = (0 until 27).shuffled(random)
        var faceOffset = 0
        for (block in order.chunked(9)) {
            val faces = List(block.size / 3) { palette[(faceOffset + it) % palette.size] }
            faceOffset += faces.size
            block.forEachIndexed { i, tile -> tile.face = faces[i % faces.size] }
            solution += block.map { it.id }.chunked(3)
        }
        live.forEach { it.removed = false }
    }

    private fun dealPaths(random: Random) {
        val live = board.toList()
        val positions = live.associate { it.id to MahjongCell(it.x, it.y) }
        if (mode == MahjongMode.TEN) {
            // Even rows allow a complete witness; removals expose longer corridors.
            val groups = live.groupBy { it.y }.values.map { it.sortedBy { tile -> tile.x }.toMutableList() }.toMutableList()
            while (groups.isNotEmpty()) {
                val row = groups.random(random)
                val first = random.nextInt(row.size - 1)
                val pair = listOf(row.removeAt(first), row.removeAt(first))
                val face = random.nextInt(27)
                pair[0].face = face; pair[1].face = random.nextInt(3) * 9 + 8 - face % 9
                solution += pair.map { it.id }
                if (row.isEmpty()) groups.remove(row)
            }
            return
        }
        var fall = gravityDirection
        while (board.isNotEmpty()) {
            val candidates = board.shuffled(random)
            var pair: List<MahjongTile>? = null
            outer@ for ((i, a) in candidates.withIndex()) for (b in candidates.drop(i + 1)) {
                if (connection(a, b) != null) { pair = listOf(a, b); break@outer }
            }
            val group = checkNotNull(pair)
            val face = random.nextInt(if (stage < 3) 18 else 27)
            group.forEach { it.face = face; it.removed = true }
            solution += group.map { it.id }
            if (mode == MahjongMode.CASCADE) { collapse(fall); fall = (fall + 1) % 4 }
        }
        live.forEach { tile ->
            tile.removed = false
            val position = positions.getValue(tile.id); tile.x = position.x; tile.y = position.y
        }
    }

    private fun dealSlides(random: Random) = MahjongIceGenerator.deal(this, random)

    fun compatible(a: MahjongTile, b: MahjongTile) = a.id != b.id &&
        if (mode == MahjongMode.TEN) a.face % 9 + b.face % 9 == 8 else a.face == b.face

    private fun straightPath(a: MahjongTile, x: Int, y: Int, destinationId: Int? = null): List<MahjongCell>? {
        if (a.x != x && a.y != y || a.x == x && a.y == y) return null
        val dx = (x - a.x).compareTo(0); val dy = (y - a.y).compareTo(0)
        val path = mutableListOf(MahjongCell(a.x, a.y))
        var cx = a.x; var cy = a.y
        do {
            cx += dx; cy += dy
            if (board.any { it.id != a.id && it.id != destinationId && it.x == cx && it.y == cy }) return null
            path += MahjongCell(cx, cy)
        } while (cx != x || cy != y)
        return path
    }

    /** Search includes the one-cell perimeter, with at most two right-angle turns. */
    fun connection(a: MahjongTile, b: MahjongTile): List<MahjongCell>? {
        if (a.id == b.id || a.removed || b.removed) return null
        straightPath(a, b.x, b.y, b.id)?.let { return it }
        data class Step(val x: Int, val y: Int, val direction: Int, val turns: Int, val path: List<MahjongCell>)
        val occupied = board.map { MahjongCell(it.x, it.y) }.toHashSet()
        val directions = arrayOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)
        val queue = ArrayDeque<Step>()
        val best = mutableMapOf<Triple<Int, Int, Int>, Int>()
        queue.add(Step(a.x, a.y, -1, 0, listOf(MahjongCell(a.x, a.y))))
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            for ((d, delta) in directions.withIndex()) {
                val x = step.x + delta.first; val y = step.y + delta.second
                val turns = step.turns + if (step.direction == -1 || step.direction == d) 0 else 1
                if (turns > 2 || x !in -1..columns || y !in -1..rows) continue
                val cell = MahjongCell(x, y)
                if (x == b.x && y == b.y) return step.path + cell
                if (cell in occupied) continue
                val key = Triple(x, y, d)
                if ((best[key] ?: 3) <= turns) continue
                best[key] = turns
                queue.add(Step(x, y, d, turns, step.path + cell))
            }
        }
        return null
    }

    private fun matchPath(a: MahjongTile, b: MahjongTile): List<MahjongCell>? = when (mode) {
        MahjongMode.CONNECT, MahjongMode.CASCADE -> connection(a, b)
        MahjongMode.TEN, MahjongMode.SLIDE -> straightPath(a, b.x, b.y, b.id)
        else -> emptyList()
    }

    /** The only legal destination in a direction is the last empty cell. */
    fun iceSlideTarget(id: Int, dx: Int, dy: Int): MahjongSlide? {
        if (mode != MahjongMode.SLIDE || !((dx == 0 && dy in listOf(-1, 1)) ||
                (dy == 0 && dx in listOf(-1, 1)))) return null
        val tile = tiles.getOrNull(id)?.takeIf(::isFree) ?: return null
        // A gesture on another tile first returns the previous focus to its
        // origin. Preview that same board without changing any live coordinates.
        val occupied = board.map { other ->
            if (other.id == selected && id != selected && focusOrigin != null) focusOrigin!!
            else MahjongCell(other.x, other.y)
        }.toHashSet()
        var x = tile.x; var y = tile.y
        while (x + dx in 0 until columns && y + dy in 0 until rows &&
            MahjongCell(x + dx, y + dy) !in occupied) {
            x += dx; y += dy
        }
        return if (x == tile.x && y == tile.y) null else MahjongSlide(id, x, y)
    }

    fun canSlide(id: Int, x: Int, y: Int): Boolean {
        if (x !in 0 until columns || y !in 0 until rows) return false
        val tile = tiles.getOrNull(id) ?: return false
        if ((tile.x != x && tile.y != y) || (tile.x == x && tile.y == y)) return false
        val target = iceSlideTarget(id, (x - tile.x).compareTo(0), (y - tile.y).compareTo(0))
        return target?.x == x && target.y == y
    }

    fun slideDirection(id: Int, dx: Int, dy: Int): Result {
        val target = iceSlideTarget(id, dx, dy)
        if (target != null) return slide(id, target.x, target.y)
        lastMatched = emptyList(); lastPath = emptyList()
        val cardinal = (dx == 0 && dy in listOf(-1,1)) || (dy == 0 && dx in listOf(-1,1))
        if (mode == MahjongMode.SLIDE && cardinal && selected != id && tiles.getOrNull(id)?.let(::isFree) == true) {
            val previous = selected?.let { tiles[it] }
            if (previous != null && focusOrigin != MahjongCell(previous.x,previous.y)) remember()
            selectIceTile(id)
            return Result.SELECTED
        }
        return Result.BLOCKED
    }

    fun slide(id: Int, x: Int, y: Int): Result {
        lastMatched = emptyList(); lastPath = emptyList()
        if (!canSlide(id, x, y)) return Result.BLOCKED
        remember()
        if (selected != id) selectIceTile(id)
        val tile = tiles[id]; val path = straightPath(tile, x, y)!!
        moves++; tile.x = x; tile.y = y; lastPath = path
        return Result.MOVED
    }

    private fun selectIceTile(id: Int) {
        selected?.let { previous -> focusOrigin?.let { origin ->
            tiles[previous].x = origin.x; tiles[previous].y = origin.y
        } }
        selected = id
        focusOrigin = MahjongCell(tiles[id].x, tiles[id].y)
    }

    fun slideHint(): MahjongSlide? {
        return if (mode == MahjongMode.SLIDE && !complete) iceSuggestion().second else null
    }

    /** Only an actual winning continuation can produce an ice hint. */
    private fun iceSuggestion(): Pair<List<Int>, MahjongSlide?> {
        val state = snapshot()
        if (state == iceHintState) return iceHint
        iceHintState = state
        iceHint = emptyList<Int>() to null
        val steps = MahjongIceSolver.solve(this) ?: return iceHint
        var index = 0
        while (index < steps.size && steps[index].slide == null &&
            steps[index].ids.isNotEmpty() && steps[index].ids.all { it == selected }) index++
        val first = steps.getOrNull(index) ?: return iceHint
        first.slide?.let { move ->
            iceHint = listOf(move.id) to move
            return iceHint
        }
        val id = first.ids.firstOrNull() ?: return iceHint
        val focus = selected?.let { tiles[it] }
        if (focus != null && compatible(focus, tiles[id]) && matchPath(focus, tiles[id]) != null) {
            iceHint = listOf(focus.id, id) to null
            return iceHint
        }
        val next = steps.getOrNull(index + 1)
        val move = next?.slide?.takeIf { it.id == id }
        if (move != null) iceHint = listOf(id) to move
        else {
            val partner = next?.ids?.singleOrNull()?.takeIf { compatible(tiles[id], tiles[it]) }
            // A preparatory refocus is deliberately a one-tile hint: unlike a
            // pair, that choice returns the current tentative tile to its origin.
            iceHint = (if (partner != null) listOf(id, partner) else listOf(id)) to null
        }
        return iceHint
    }

    fun hint(): List<Int> {
        hintSearchLimited = false
        if (complete) return emptyList()
        if (mode == MahjongMode.SLIDE) {
            return iceSuggestion().first
        }
        if (mode == MahjongMode.TRIPLE && tray.size >= 7) return emptyList()
        val state = snapshot()
        if (puzzleHintState == state) return puzzleHint
        val witness = MahjongPuzzleSolver.verifiedContinuation(this, solutionSteps)
        val result = if (witness != null) MahjongPuzzleSolver.Result(MahjongPuzzleSolver.Outcome.SOLVABLE, witness)
            else MahjongPuzzleSolver.solve(this)
        hintSearchLimited = result.outcome == MahjongPuzzleSolver.Outcome.UNKNOWN
        puzzleHint = result.steps?.firstOrNull()?.ids.orEmpty().let { if (mode == MahjongMode.TRIPLE) it.take(1) else it }
        if (!hintSearchLimited) {
            puzzleHintState = state
            result.steps?.let { solutionSteps.clear(); solutionSteps.addAll(it) }
        }
        return puzzleHint
    }

    fun tap(id: Int): Result {
        lastMatched = emptyList(); lastPath = emptyList()
        val tile = tiles.getOrNull(id) ?: return Result.IGNORED
        if (!isFree(tile)) return Result.BLOCKED
        if (mode == MahjongMode.TRIPLE) {
            if (tray.size >= 7) return Result.TRAY_FULL
            remember(); moves++; tray += id
            val group = tray.filter { tiles[it].face == tile.face }
            if (group.size == 3) { tray.removeAll(group.toSet()); match(group); return Result.MATCHED }
            return if (tray.size == 7) Result.TRAY_FULL else Result.SELECTED
        }
        if (mode == MahjongMode.SLIDE) {
            val focus = selected?.let { tiles[it] }
            if (focus == null) { selectIceTile(id); return Result.SELECTED }
            if (focus.id == id) return Result.SELECTED
            val path = matchPath(focus, tile)
            if (compatible(focus, tile) && path != null) {
                remember(); moves++; selected = null; focusOrigin = null; lastPath = path
                match(listOf(focus.id, id))
                return Result.MATCHED
            }
            if (focusOrigin != MahjongCell(focus.x, focus.y)) remember()
            selectIceTile(id)
            return Result.SELECTED
        }
        val a = selected?.let { tiles[it] }
        if (a == null || a.id == id) { selected = if (a == null) id else null; return Result.SELECTED }
        val path = matchPath(a, tile)
        if (!compatible(a, tile) || path == null) { selected = id; combo = 0; return Result.MISMATCH }
        remember(); moves++; selected = null; lastPath = path
        val fall = gravityDirection
        match(listOf(a.id, id))
        if (mode == MahjongMode.CASCADE) collapse(fall)
        return Result.MATCHED
    }

    private fun match(ids: List<Int>) {
        ids.forEach { tiles[it].removed = true }
        lastMatched = ids; matches++; combo = (combo + 1).coerceAtMost(8)
        peakCombo = maxOf(peakCombo, combo); score += 100 * combo
    }

    private fun collapse(direction: Int) {
        if (direction % 2 == 0) board.groupBy { it.x }.values.forEach { column ->
            column.sortedBy { it.y }.forEachIndexed { i, tile -> tile.y = if (direction == 0) rows - column.size + i else i }
        } else board.groupBy { it.y }.values.forEach { row ->
            row.sortedBy { it.x }.forEachIndexed { i, tile -> tile.x = if (direction == 1) i else columns - row.size + i }
        }
    }

    fun snapshot() = MahjongSnapshot(tiles.map { it.copy() }, tray.toList(), emptyList(), score, combo, matches, moves, assists, peakCombo,
        if (mode == MahjongMode.SLIDE) selected else null, if (mode == MahjongMode.SLIDE) focusOrigin else null)
    fun restore(state: MahjongSnapshot) {
        tiles.clear(); tiles.addAll(state.tiles.map { it.copy() })
        tray.clear(); tray.addAll(state.tray); revealed.clear()
        score = state.score; combo = state.combo; matches = state.matches; moves = state.moves; assists = state.assists
        peakCombo = state.peakCombo
        selected = if (mode == MahjongMode.SLIDE) state.focusId else null
        focusOrigin = if (mode == MahjongMode.SLIDE) state.focusOrigin else null
        lastMatched = emptyList(); lastPath = emptyList()
    }
    private fun remember() {
        if (history.size >= 256) history.removeAt(0)
        history += snapshot()
    }
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val used = assists + 1; val peak = peakCombo
        restore(history.removeAt(history.lastIndex)); assists = used; peakCombo = maxOf(peak, peakCombo)
        return true
    }

    /** Reconstruct a legal finish while retaining IDs, points, and undo history. */
    fun shuffle() {
        if (complete) return
        remember(); assists++; selected = null; focusOrigin = null; revealed.clear(); tray.clear()
        if (mode == MahjongMode.CLASSIC) {
            board.forEachIndexed { index, tile ->
                val base = columns * rows
                when {
                    index < base -> { tile.x = index % columns; tile.y = index / columns; tile.z = 0 }
                    index < base + 16 -> { tile.x = 1 + (index - base) % 4; tile.y = 1 + (index - base) / 4; tile.z = 1 }
                    else -> { tile.x = 2 + (index - base - 16) % 2; tile.y = 2 + (index - base - 16) / 2; tile.z = 2 }
                }
            }
        } else if (mode != MahjongMode.TRIPLE && mode != MahjongMode.SLIDE) {
            board.forEachIndexed { index, tile -> tile.x = index % columns; tile.y = index / columns; tile.z = 0 }
        }
        deal(Random(seed xor (assists * 7919) xor moves))
    }
}
