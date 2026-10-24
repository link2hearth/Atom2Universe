package com.Atom2Universe.app.games.mahjong

import java.util.concurrent.CancellationException

/** Bounded, cancellable search. Exhausting a budget is never a proof of defeat. */
internal object MahjongPuzzleSolver {
    enum class Outcome { SOLVABLE, UNSOLVABLE, UNKNOWN }
    data class Result(val outcome: Outcome, val steps: List<MahjongSolutionStep>? = null, val states: Int = 0)
    data class Choice(val ids: List<Int>, val outcome: Outcome)
    data class Quality(val result: Result, val choices: List<Choice>, val states: Int = 0)

    /** Geometry-only helpers for construction; no recursive search or game mutation. */
    fun legalChoices(game: MahjongGame): List<List<Int>> {
        cancelled()
        val search = Search(game, 0, 6)
        return search.options(search.all, search.start).map(search::ids)
    }

    fun constructionPairs(game: MahjongGame, random: kotlin.random.Random): List<List<Int>>? {
        cancelled()
        val search = Search(game, 0, 6)
        var mask = search.all
        var cells = search.start
        val result = mutableListOf<List<Int>>()
        while (mask != 0L) {
            cancelled()
            val options = search.options(mask, cells)
            if (options.isEmpty()) return null
            val action = options.random(random)
            result += search.ids(action)
            val after = search.after(mask, cells, action)
            mask = after.first; cells = after.second
        }
        return result
    }

    fun solve(game: MahjongGame, budget: Int = 20_000, trayLimit: Int = 6): Result {
        cancelled()
        if (game.complete) return Result(Outcome.SOLVABLE, emptyList())
        if (game.mode == MahjongMode.SLIDE || game.board.size > 60) return Result(Outcome.UNKNOWN)
        val search = Search(game, budget, trayLimit)
        val plan = search.find(search.all, search.start)
        return result(game, search, plan)
    }

    fun analyze(game: MahjongGame, budget: Int = 30_000): Quality {
        cancelled()
        if (game.mode == MahjongMode.SLIDE || game.board.size > 60) return Quality(Result(Outcome.UNKNOWN), emptyList())
        val search = Search(game, budget, 6)
        val plan = search.find(search.all, search.start)
        val solved = result(game, search, plan)
        val choices = search.options(search.all, search.start).map { action ->
            val after = search.after(search.all, search.start, action)
            val rest = search.find(after.first, after.second)
            Choice(search.ids(action), when {
                rest != null -> Outcome.SOLVABLE
                search.exceeded -> Outcome.UNKNOWN
                else -> Outcome.UNSOLVABLE
            })
        }
        return Quality(solved, choices, search.visited)
    }

    /** Also usable with an existing construction witness after some moves. */
    fun verifiedContinuation(game: MahjongGame, witness: List<MahjongSolutionStep>): List<MahjongSolutionStep>? {
        if (witness.isEmpty()) return if (game.complete) emptyList() else null
        val copy = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false)
        copy.restore(game.snapshot())
        val remaining = mutableListOf<MahjongSolutionStep>()
        for (step in witness) {
            cancelled()
            if (step.slide != null) return null
            val ids = step.ids.filter { id -> copy.tiles.getOrNull(id)?.removed == false && id !in copy.tray }
            if (ids.isEmpty()) continue
            if (game.mode != MahjongMode.TRIPLE && ids.size != 2) return null
            for (id in ids) {
                val action = copy.tap(id)
                if (action != MahjongGame.Result.SELECTED && action != MahjongGame.Result.MATCHED) return null
            }
            remaining += MahjongSolutionStep(ids)
        }
        return remaining.takeIf { copy.complete }
    }

    private fun result(game: MahjongGame, search: Search, plan: List<Int>?): Result {
        if (plan != null) {
            val steps = plan.map { MahjongSolutionStep(search.ids(it)) }
            val checked = verifiedContinuation(game, steps)
            return if (checked != null) Result(Outcome.SOLVABLE, checked, search.visited) else Result(Outcome.UNKNOWN, states = search.visited)
        }
        return Result(if (search.exceeded) Outcome.UNKNOWN else Outcome.UNSOLVABLE, states = search.visited)
    }

    private fun cancelled() {
        if (Thread.currentThread().isInterrupted) throw CancellationException()
    }

    private data class MovingState(val mask: Long, val cells: String)

    private class Search(val game: MahjongGame, val budget: Int, val trayLimit: Int) {
        val tiles = game.board
        val n = tiles.size
        val all = (1L shl n) - 1
        val start = IntArray(n) { tiles[it].y * game.columns + tiles[it].x }
        private val triple = game.mode == MahjongMode.TRIPLE
        private val cascade = game.mode == MahjongMode.CASCADE
        private val layered = triple || game.mode == MahjongMode.CLASSIC
        private val faces = IntArray(n) { tiles[it].face }
        private val groups = LongArray(27)
        private val tray = IntArray(27)
        private val above = LongArray(n)
        private val left = LongArray(n)
        private val right = LongArray(n)
        private val pairs = mutableListOf<Int>()
        private val paths = HashMap<Int, LongArray>()
        private val grid = LongArray(game.columns * game.rows)
        private val failed = HashSet<Any>()
        private val won = HashMap<Any, List<Int>>()
        var visited = 0
            private set
        var exceeded = false
            private set

        init {
            for (i in tiles.indices) {
                groups[faces[i]] = groups[faces[i]] or (1L shl i)
                val a = tiles[i]
                for (j in tiles.indices) {
                    val b = tiles[j]; val bit = 1L shl j
                    if (a.x == b.x && a.y == b.y && b.z > a.z) above[i] = above[i] or bit
                    if (a.y == b.y && a.z == b.z) {
                        if (b.x == a.x - 1) left[i] = left[i] or bit
                        if (b.x == a.x + 1) right[i] = right[i] or bit
                    }
                    if (j > i && game.compatible(a, b)) pairs += i * n + j
                }
            }
            for (id in game.tray) tray[game.tiles[id].face]++
            for (i in start.indices) grid[start[i]] = 1L shl i
            if (!layered && !cascade) for (action in pairs) {
                paths[action] = routeMasks(action / n, action % n, start)
            }
        }

        fun ids(action: Int) = if (triple) listOf(tiles[action].id)
            else listOf(tiles[action / n].id, tiles[action % n].id)

        private fun free(mask: Long, i: Int): Boolean = mask and (1L shl i) != 0L &&
            above[i] and mask == 0L && (triple || left[i] and mask == 0L || right[i] and mask == 0L)

        fun options(mask: Long, positions: IntArray): List<Int> {
            if (triple) {
                val used = groups.indices.sumOf { (tray[it] + java.lang.Long.bitCount(groups[it] and mask.inv())) % 3 }
                return if (used >= 7) emptyList() else tiles.indices.filter { free(mask, it) }
            }
            if (cascade) {
                grid.fill(0L)
                for (i in positions.indices) if (mask and (1L shl i) != 0L) grid[positions[i]] = 1L shl i
            }
            return pairs.filter { action ->
                val a = action / n; val b = action % n
                mask and (1L shl a) != 0L && mask and (1L shl b) != 0L &&
                    if (layered) free(mask, a) && free(mask, b)
                    else if (cascade) reachable(a, b, positions, mask)
                    else paths.getValue(action).any { it and mask == 0L }
            }
        }

        fun after(mask: Long, positions: IntArray, action: Int): Pair<Long, IntArray> {
            val next = if (triple) mask and (1L shl action).inv()
                else mask and (1L shl (action / n)).inv() and (1L shl (action % n)).inv()
            if (!cascade) return next to positions
            val direction = (game.matches + (n - java.lang.Long.bitCount(mask)) / 2) % 4
            val cells = positions.copyOf()
            for (axis in 0 until if (direction % 2 == 0) game.columns else game.rows) {
                val line = tiles.indices.filter { next and (1L shl it) != 0L &&
                    (if (direction % 2 == 0) cells[it] % game.columns else cells[it] / game.columns) == axis }
                    .sortedBy { if (direction % 2 == 0) cells[it] / game.columns else cells[it] % game.columns }
                line.forEachIndexed { index, i ->
                    cells[i] = if (direction % 2 == 0)
                        axis + game.columns * (if (direction == 0) game.rows - line.size + index else index)
                    else (if (direction == 3) game.columns - line.size + index else index) + game.columns * axis
                }
            }
            return next to cells
        }

        fun find(mask: Long, positions: IntArray): List<Int>? {
            cancelled()
            if (mask == 0L) return emptyList()
            val key: Any = if (cascade) MovingState(mask, String(CharArray(n) {
                if (mask and (1L shl it) != 0L) (positions[it] + 1).toChar() else 0.toChar()
            })) else mask
            won[key]?.let { return it }
            if (key in failed) return null
            if (visited >= budget) { exceeded = true; return null }
            visited++
            if (!triple && !cascade) {
                // Removing obstacles only helps in these modes. Optimistically
                // removed partners remain reusable: consuming three of four
                // identical tiles must NOT turn the fourth into a false orphan.
                var optimistic = mask
                while (optimistic != 0L) {
                    var next = optimistic
                    for (action in pairs) {
                        val a = action / n; val b = action % n
                        val bits = (1L shl a) or (1L shl b)
                        if (mask and bits != bits || optimistic and bits == 0L) continue
                        val possible = if (layered)
                            (optimistic and (1L shl a) == 0L || free(optimistic, a)) &&
                                (optimistic and (1L shl b) == 0L || free(optimistic, b))
                        else paths.getValue(action).any { it and optimistic == 0L }
                        if (possible) next = next and bits.inv()
                    }
                    if (next == optimistic) { failed += key; return null }
                    optimistic = next
                }
            }
            if (triple) {
                var used = 0
                for (f in groups.indices) used += (tray[f] + java.lang.Long.bitCount(groups[f] and mask.inv())) % 3
                if (used > trayLimit) { failed += key; return null }
            }
            for (action in options(mask, positions)) {
                val after = after(mask, positions, action)
                val rest = find(after.first, after.second)
                if (rest != null) return (listOf(action) + rest).also { won[key] = it }
            }
            if (!exceeded) failed += key
            return null
        }

        /** Enumerate the straight, one-corner and two-corner paths once per pair. */
        private fun line(x: Int, y: Int, tx: Int, ty: Int): Long {
            var cx = x; var cy = y; var blockers = 0L
            val dx = (tx - x).compareTo(0); val dy = (ty - y).compareTo(0)
            while (cx != tx || cy != ty) {
                cx += dx; cy += dy
                if (cx in 0 until game.columns && cy in 0 until game.rows) blockers = blockers or grid[cy * game.columns + cx]
            }
            return blockers
        }

        private fun reachable(a: Int, b: Int, positions: IntArray, mask: Long): Boolean {
            val ax = positions[a] % game.columns; val ay = positions[a] / game.columns
            val bx = positions[b] % game.columns; val by = positions[b] / game.columns
            val obstacles = mask and (1L shl a).inv() and (1L shl b).inv()
            if ((ax == bx || ay == by) && line(ax, ay, bx, by) and obstacles == 0L) return true
            if ((line(ax, ay, ax, by) or line(ax, by, bx, by)) and obstacles == 0L) return true
            if ((line(ax, ay, bx, ay) or line(bx, ay, bx, by)) and obstacles == 0L) return true
            for (x in -1..game.columns) if ((line(ax, ay, x, ay) or line(x, ay, x, by) or line(x, by, bx, by)) and obstacles == 0L) return true
            for (y in -1..game.rows) if ((line(ax, ay, ax, y) or line(ax, y, bx, y) or line(bx, y, bx, by)) and obstacles == 0L) return true
            return false
        }

        private fun routeMasks(a: Int, b: Int, positions: IntArray): LongArray {
            val columns = game.columns
            val ax = positions[a] % columns; val ay = positions[a] / columns
            val bx = positions[b] % columns; val by = positions[b] / columns
            val routes = ArrayList<Long>(columns + game.rows + 7)
            if (ax == bx || ay == by) routes += line(ax, ay, bx, by)
            if (game.mode != MahjongMode.TEN) {
                routes += line(ax, ay, ax, by) or line(ax, by, bx, by)
                routes += line(ax, ay, bx, ay) or line(bx, ay, bx, by)
                for (x in -1..columns) routes += line(ax, ay, x, ay) or line(x, ay, x, by) or line(x, by, bx, by)
                for (y in -1..game.rows) routes += line(ax, ay, ax, y) or line(ax, y, bx, y) or line(bx, y, bx, by)
            }
            val endpoints = (1L shl a) or (1L shl b)
            return LongArray(routes.size) { routes[it] and endpoints.inv() }
        }
    }
}
