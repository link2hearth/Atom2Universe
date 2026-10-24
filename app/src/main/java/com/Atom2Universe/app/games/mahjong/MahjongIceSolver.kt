package com.Atom2Universe.app.games.mahjong

import java.util.BitSet
import java.util.concurrent.CancellationException

/**
 * Exact search for committed pairs. Only the focused tile moves during a trial;
 * after its match both tiles disappear and every survivor is at its original cell.
 * A state is therefore just the set of surviving tiles, not arbitrary rearrangements.
 */
internal object MahjongIceSolver {
    enum class Outcome { SOLVABLE, UNSOLVABLE, UNKNOWN }

    /** A legal first fusion, including the shortest ice route in either orientation. */
    data class Choice(val ids: List<Int>, val minSlides: Int, val outcome: Outcome)

    data class Quality(
        val outcome: Outcome,
        val opening: List<Choice>,
        val stopperDependentPairs: Int,
        val solution: List<MahjongSolutionStep>?,
        val states: Int = 0
    ) {
        val immediatePairs get() = opening.count { it.minSlides == 0 }
        val winningChoices get() = opening.count { it.outcome == Outcome.SOLVABLE }
        val losingChoices get() = opening.count { it.outcome == Outcome.UNSOLVABLE }
    }

    /**
     * Certify puzzle properties, not just one convenient solution. An isolated pair
     * that cannot meet on otherwise empty ice needs another family's stopping tile
     * in EVERY solution. A losing opening is reported only after exhaustive search;
     * reaching the work budget is UNKNOWN and can never certify a trap.
     *
     * Generation uses canonical boards with one pair per face and no active trial.
     * The state budget is shared by the solution and all opening continuations.
     */
    fun analyze(game: MahjongGame, maxStates: Int = 20_000): Quality? {
        checkCancellation()
        if (game.mode != MahjongMode.SLIDE || game.selected != null || maxStates < 0) return null
        val live = game.board
        if (live.isEmpty()) return Quality(Outcome.SOLVABLE, emptyList(), 0, emptyList())
        if (live.size > 32 || live.size % 2 != 0) return null
        val pairs = live.indices.groupBy { live[it].face }.values
        if (pairs.any { it.size != 2 }) return null
        val cells = IntArray(live.size) { live[it].y * game.columns + live[it].x }
        val search = Search(game.columns, game.rows, cells, IntArray(live.size) { live[it].face }, maxStates)
        val all = (1L shl live.size) - 1L
        var dependent = 0
        data class Opening(val a: Int, val b: Int, val slides: Int)
        val legal = mutableListOf<Opening>()
        for (pair in pairs) {
            val a = pair[0]; val b = pair[1]
            val first = search.distance(all, a, b, cells[a])
            val second = search.distance(all, b, a, cells[b])
            if (first >= 0 || second >= 0) legal += Opening(a, b,
                if (first < 0) second else if (second < 0) first else minOf(first, second))
            val isolated = (1L shl a) or (1L shl b)
            if (search.distance(isolated, a, b, cells[a]) < 0 &&
                search.distance(isolated, b, a, cells[b]) < 0) dependent++
        }
        val found = search.find(all)
        val plan = found?.let { checked(game, steps(it, live, game.columns)) }
        val outcome = when {
            plan != null -> Outcome.SOLVABLE
            found != null || search.budgetExceeded -> Outcome.UNKNOWN
            else -> Outcome.UNSOLVABLE
        }
        val provenFirst = if (plan != null) found?.firstOrNull() else null
        val opening = legal.map { move ->
            // The checked witness already proves this continuation; do not solve
            // it a second time or lose the certificate when the budget is spent.
            val witnessed = provenFirst != null &&
                ((provenFirst.mover == move.a && provenFirst.partner == move.b) ||
                    (provenFirst.mover == move.b && provenFirst.partner == move.a))
            val rest = if (witnessed) null else search.find(all and (1L shl move.a).inv() and (1L shl move.b).inv())
            Choice(listOf(live[move.a].id, live[move.b].id), move.slides, when {
                witnessed || rest != null -> Outcome.SOLVABLE
                search.budgetExceeded -> Outcome.UNKNOWN
                else -> Outcome.UNSOLVABLE
            })
        }
        return Quality(outcome, opening, dependent, plan, search.visited)
    }

    /** Pair distances on the full geometry, or with only the supplied anchors and tested pair. */
    fun geometryDistances(game: MahjongGame, anchors: Long? = null): Array<IntArray> {
        checkCancellation()
        val live = game.board
        require(live.size <= 32)
        val cells = IntArray(live.size) { live[it].y * game.columns + live[it].x }
        val search = Search(game.columns, game.rows, cells, IntArray(live.size) { live[it].face }, 0)
        val all = (1L shl live.size) - 1
        val distances = Array(live.size) { IntArray(live.size) { -1 } }
        for (a in live.indices) for (b in a + 1 until live.size) {
            checkCancellation()
            val mask = anchors?.or((1L shl a) or (1L shl b)) ?: all
            val first = search.distance(mask, a, b, cells[a])
            val second = search.distance(mask, b, a, cells[b])
            val distance = if (first < 0) second else if (second < 0) first else minOf(first, second)
            distances[a][b] = distance; distances[b][a] = distance
        }
        return distances
    }

    /** Shortest legal route for this pair, without solving or mutating the rest. */
    fun pairPlan(game: MahjongGame, firstId: Int, secondId: Int): List<MahjongSolutionStep>? {
        checkCancellation()
        if (game.mode != MahjongMode.SLIDE || game.selected != null || firstId == secondId) return null
        val live = game.board
        if (live.size > 32) return null
        val a = live.indexOfFirst { it.id == firstId }; val b = live.indexOfFirst { it.id == secondId }
        if (a < 0 || b < 0 || live[a].face != live[b].face) return null
        val cells = IntArray(live.size) { live[it].y * game.columns + live[it].x }
        val search = Search(game.columns, game.rows, cells, IntArray(live.size) { live[it].face })
        val all = (1L shl live.size) - 1L
        val first = search.route(all, a, b, cells[a])
        val second = search.route(all, b, a, cells[b])
        val match = when {
            first != null && (second == null || first.size <= second.size) -> Match(a, b, first)
            second != null -> Match(b, a, second)
            else -> return null
        }
        return steps(listOf(match), live, game.columns)
    }

    fun solve(game: MahjongGame): List<MahjongSolutionStep>? {
        checkCancellation()
        if (game.mode != MahjongMode.SLIDE) return null
        val live = game.board
        if (live.isEmpty()) return emptyList()
        if (live.size > 32 || live.size % 2 != 0) return null
        val focus = live.indexOfFirst { it.id == game.selected }
        val origin = game.focusOrigin
        val columns = game.columns
        val cells = IntArray(live.size) { i ->
            if (i == focus && origin != null) origin.y * columns + origin.x
            else live[i].y * columns + live[i].x
        }
        val search = Search(columns, game.rows, cells, IntArray(live.size) { live[it].face })
        val all = (1L shl live.size) - 1L

        // Prefer completing the active attempt, using its actual current position.
        // Its origin remains authoritative for all subsequent committed states.
        if (focus >= 0 && origin != null) {
            val currentCell = live[focus].y * columns + live[focus].x
            for (partner in live.indices) {
                if (partner == focus || live[partner].face != live[focus].face) continue
                val route = search.route(all, focus, partner, currentCell) ?: continue
                val rest = search.find(all and (1L shl focus).inv() and (1L shl partner).inv()) ?: continue
                return checked(game, steps(listOf(Match(focus, partner, route)) + rest, live, columns))
            }
        }

        val matches = search.find(all) ?: return null
        val result = steps(matches, live, columns).toMutableList()
        if (focus >= 0 && origin != null && matches.isNotEmpty()) {
            val first = matches.first().mover
            // A second tap on the focused tile intentionally does not cancel it.
            // Switch to a different face first when the solution needs a fresh start
            // of this same family. That switch can never accidentally form a pair.
            if (first == focus) {
                val reset = live.indices.firstOrNull { live[it].face != live[focus].face }
                if (reset != null) result.add(0, MahjongSolutionStep(ids = listOf(live[reset].id)))
                else if (live.size == 2) {
                    val partner = matches.first().partner
                    if (matches.first().route.isEmpty()) return checked(game, listOf(
                        MahjongSolutionStep(ids = listOf(live[partner].id)),
                        MahjongSolutionStep(ids = listOf(live[focus].id))))
                    result.add(0, MahjongSolutionStep(ids = listOf(live[partner].id)))
                } else return null
            }
        }
        return checked(game, result)
    }

    /** Validate against the actual transactional rules before accepting any level. */
    private fun checked(game: MahjongGame, steps: List<MahjongSolutionStep>): List<MahjongSolutionStep>? {
        val copy = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false)
        copy.restore(game.snapshot())
        for (step in steps) {
            checkCancellation()
            step.slide?.let { if (copy.slide(it.id, it.x, it.y) != MahjongGame.Result.MOVED) return null }
            for (id in step.ids) {
                val result = copy.tap(id)
                if (result != MahjongGame.Result.SELECTED && result != MahjongGame.Result.MATCHED) return null
            }
        }
        return steps.takeIf { copy.complete }
    }

    private fun checkCancellation() {
        if (Thread.currentThread().isInterrupted) throw CancellationException()
    }

    private data class Match(val mover: Int, val partner: Int, val route: IntArray)

    private fun steps(matches: List<Match>, tiles: List<MahjongTile>, columns: Int): List<MahjongSolutionStep> = buildList {
        for (match in matches) {
            val id = tiles[match.mover].id
            add(MahjongSolutionStep(ids = listOf(id)))
            for (cell in match.route) add(MahjongSolutionStep(slide = MahjongSlide(id, cell % columns, cell / columns)))
            add(MahjongSolutionStep(ids = listOf(tiles[match.partner].id)))
        }
    }

    private class Search(val columns: Int, val rows: Int, val cells: IntArray, val faces: IntArray,
                         private val maxStates: Int = 100_000) {
        private val partners = LongArray(cells.size) { a ->
            var mask = 0L
            for (b in cells.indices) if (a != b && faces[a] == faces[b]) mask = mask or (1L shl b)
            mask
        }
        private val pairBits = IntArray(cells.size)
        // A generated board has one pair per face. Its subset of surviving pairs
        // fits in sixteen bits, so failed states need at most 8 KiB, with no boxing.
        private val compactFailed: BitSet? by lazy(LazyThreadSafetyMode.NONE) {
            if (partners.all { java.lang.Long.bitCount(it) == 1 }) {
                var pair = 0
                for (a in cells.indices) {
                    val b = java.lang.Long.numberOfTrailingZeros(partners[a])
                    if (a < b) {
                        pairBits[a] = 1 shl pair
                        pairBits[b] = pairBits[a]
                        pair++
                    }
                }
                BitSet(1 shl pair)
            } else null
        }
        // Keep support for custom boards containing more than two of one face.
        private val failed by lazy(LazyThreadSafetyMode.NONE) { if (compactFailed == null) HashSet<Long>() else null }
        var visited = 0
            private set
        var budgetExceeded = false
            private set
        // A route completes before recursion starts, so every route can share
        // these buffers instead of allocating three arrays for every candidate.
        private val occupied = IntArray(columns * rows)
        private val parent = IntArray(occupied.size)
        private val queue = IntArray(occupied.size)
        private val neighbors = IntArray(occupied.size * 4) { index ->
            val cell = index / 4
            val x = cell % columns
            val y = cell / columns
            when (index % 4) {
                0 -> if (x + 1 < columns) cell + 1 else -1
                1 -> if (y + 1 < rows) cell + columns else -1
                2 -> if (x > 0) cell - 1 else -1
                else -> if (y > 0) cell - columns else -1
            }
        }

        fun find(mask: Long): List<Match>? {
            var pairState = 0
            if (compactFailed != null) {
                for (a in cells.indices) if (mask and (1L shl a) != 0L) pairState = pairState or pairBits[a]
            }
            return find(mask, pairState)
        }

        private fun find(mask: Long, pairState: Int): List<Match>? {
            checkCancellation()
            if (mask == 0L) return emptyList()
            if (compactFailed?.get(pairState) ?: failed!!.contains(mask)) return null
            if (visited >= maxStates) { budgetExceeded = true; return null }
            visited++
            for (a in cells.indices) {
                if (mask and (1L shl a) == 0L) continue
                var candidates = partners[a] and mask and (-1L shl (a + 1))
                while (candidates != 0L) {
                    val b = java.lang.Long.numberOfTrailingZeros(candidates)
                    candidates = candidates and (candidates - 1)
                    val mover: Int
                    val partner: Int
                    if (endpoint(mask, a, b, cells[a]) >= 0) {
                        mover = a; partner = b
                    } else if (endpoint(mask, b, a, cells[b]) >= 0) {
                        mover = b; partner = a
                    } else continue
                    val rest = find(mask and (1L shl a).inv() and (1L shl b).inv(), pairState and pairBits[a].inv())
                    if (rest != null) {
                        // Only retain paths on the winning branch. Failed branches
                        // do not allocate Match objects or lists of slide positions.
                        val route = route(mask, mover, partner, cells[mover]) ?: return null
                        return listOf(Match(mover, partner, route)) + rest
                    }
                }
            }
            // An unfinished branch is not a proof that this position is lost.
            if (!budgetExceeded) {
                val compact = compactFailed
                if (compact != null) compact.set(pairState) else failed!!.add(mask)
            }
            return null
        }

        fun distance(mask: Long, mover: Int, partner: Int, start: Int): Int {
            var end = endpoint(mask, mover, partner, start)
            if (end < 0) return -1
            var length = 0
            while (parent[end] != -1) { length++; end = parent[end] }
            return length
        }

        fun route(mask: Long, mover: Int, partner: Int, start: Int): IntArray? {
            var end = endpoint(mask, mover, partner, start)
            if (end < 0) return null
            var length = 0
            var cell = end
            while (parent[cell] != -1) { length++; cell = parent[cell] }
            val path = IntArray(length)
            while (length > 0) { path[--length] = end; end = parent[end] }
            return path
        }

        /** All endpoint positions reachable by moving this one tile, with others fixed. */
        private fun endpoint(mask: Long, mover: Int, partner: Int, start: Int): Int {
            checkCancellation()
            occupied.fill(-1)
            for (i in cells.indices) if (i != mover && mask and (1L shl i) != 0L) occupied[cells[i]] = i
            if (start !in occupied.indices || occupied[start] != -1) return -1
            parent.fill(-2)
            var read = 0; var write = 0
            parent[start] = -1; queue[write++] = start
            val destination = cells[partner]
            while (read < write) {
                val cell = queue[read++]
                if (connected(cell, destination, occupied, partner)) return cell
                for (direction in 0 until 4) {
                    var end = cell
                    var next = neighbors[end * 4 + direction]
                    while (next >= 0 && occupied[next] == -1) {
                        end = next
                        next = neighbors[end * 4 + direction]
                    }
                    if (parent[end] == -2) { parent[end] = cell; queue[write++] = end }
                }
            }
            return -1
        }

        private fun connected(start: Int, end: Int, occupied: IntArray, partner: Int): Boolean {
            val x = start % columns; val y = start / columns
            val tx = end % columns; val ty = end / columns
            if (x != tx && y != ty) return false
            val dx = (tx - x).compareTo(0); val dy = (ty - y).compareTo(0)
            var cx = x + dx; var cy = y + dy
            while (cx != tx || cy != ty) {
                val occupant = occupied[cy * columns + cx]
                if (occupant != -1 && occupant != partner) return false
                cx += dx; cy += dy
            }
            return true
        }
    }
}
