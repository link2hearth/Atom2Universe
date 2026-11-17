package com.Atom2Universe.app.games.mahjong

import com.Atom2Universe.app.games.mahjong.MahjongPuzzleSolver.Outcome
import com.Atom2Universe.app.games.mahjong.MahjongPuzzleSolver.Result
import java.util.concurrent.CancellationException

/**
 * Indices des grands plateaux (Solitaire, Triples, Connexion, Cascade) : on rejoue d'abord
 * le témoin de construction, puis une recherche bornée cherche une fin complète. Épuiser le
 * budget n'est jamais une preuve de défaite.
 */
internal object MahjongSolver {
    fun solve(game: MahjongGame, witness: List<MahjongSolutionStep>, budget: Int = 25_000): Result {
        if (game.complete) return Result(Outcome.SOLVABLE, emptyList())
        MahjongPuzzleSolver.verifiedContinuation(game, witness)?.let { return Result(Outcome.SOLVABLE, it) }
        return when (game.mode) {
            MahjongMode.CLASSIC -> Classic(game, budget).run()
            MahjongMode.TRIPLE -> Triple(game, budget).run()
            MahjongMode.CONNECT, MahjongMode.CASCADE -> Paths(game, budget / 60).run()
            else -> Result(Outcome.UNKNOWN)
        }
    }

    /** Un coup simplement jouable, pour un indice quand la recherche n'a pas conclu. */
    fun anyMove(game: MahjongGame): List<Int> {
        val live = game.board
        return when (game.mode) {
            MahjongMode.CLASSIC -> live.filter { game.isFree(it, live) }.groupBy { MahjongFaces.kind(it.face) }
                .values.firstOrNull { it.size >= 2 }?.take(2)?.map { it.id }.orEmpty()
            MahjongMode.TRIPLE -> {
                if (game.tray.size >= MahjongGame.TRAY) return emptyList()
                val inTray = game.tray.groupingBy { MahjongFaces.kind(game.tiles[it].face) }.eachCount()
                live.filter { game.isFree(it, live) }
                    .maxByOrNull { inTray[MahjongFaces.kind(it.face)] ?: 0 }?.let { listOf(it.id) }.orEmpty()
            }
            MahjongMode.CONNECT, MahjongMode.CASCADE -> live.firstNotNullOfOrNull { a ->
                game.reachable(a, live).firstOrNull { game.compatible(a, game.tiles[it]) }?.let { listOf(a.id, it) }
            }.orEmpty()
            else -> MahjongPuzzleSolver.legalChoices(game).firstOrNull().orEmpty()
        }
    }

    private class Key(val bits: LongArray) {
        private val hash = bits.contentHashCode()
        override fun hashCode() = hash
        override fun equals(other: Any?) = other is Key && other.bits.contentEquals(bits)
    }

    private fun cancelled() { if (Thread.currentThread().isInterrupted) throw CancellationException() }

    /** Ensemble de bits sur des LongArray : 144 tuiles tiennent en trois mots. */
    private abstract class Bitboard(game: MahjongGame) {
        val live = game.board
        val n = live.size
        val words = (n + 63) / 64
        val kinds = IntArray(n) { MahjongFaces.kind(live[it].face) }
        val above = Array(n) { LongArray(words) }
        val left = Array(n) { LongArray(words) }
        val right = Array(n) { LongArray(words) }
        /** Les tuiles que [i] couvre ou bloque sur le côté. */
        val dependents = Array(n) { LongArray(words) }
        val failed = HashSet<Key>()
        var visited = 0
        var exceeded = false
        init {
            for (i in 0 until n) for (j in 0 until n) {
                val a = live[i]; val b = live[j]
                if (b.z > a.z && kotlin.math.abs(a.x - b.x) < 2 && kotlin.math.abs(a.y - b.y) < 2) { set(above[i], j); set(dependents[j], i) }
                if (game.mode == MahjongMode.CLASSIC && a.z == b.z && kotlin.math.abs(a.y - b.y) < 2) {
                    if (b.x == a.x - 2) { set(left[i], j); set(dependents[j], i) }
                    if (b.x == a.x + 2) { set(right[i], j); set(dependents[j], i) }
                }
            }
        }
        fun set(bits: LongArray, i: Int) { bits[i shr 6] = bits[i shr 6] or (1L shl (i and 63)) }
        fun clear(bits: LongArray, i: Int) { bits[i shr 6] = bits[i shr 6] and (1L shl (i and 63)).inv() }
        fun has(bits: LongArray, i: Int) = bits[i shr 6] and (1L shl (i and 63)) != 0L
        fun none(mask: LongArray, alive: LongArray): Boolean { for (w in 0 until words) if (mask[w] and alive[w] != 0L) return false; return true }
        fun count(mask: LongArray, alive: LongArray): Int { var c = 0; for (w in 0 until words) c += java.lang.Long.bitCount(mask[w] and alive[w]); return c }
        fun empty(alive: LongArray): Boolean { for (w in alive) if (w != 0L) return false; return true }
        fun uncovered(alive: LongArray, i: Int) = has(alive, i) && none(above[i], alive)
        fun full() = LongArray(words).also { for (i in 0 until n) set(it, i) }
        fun enter(alive: LongArray): Key? {
            cancelled()
            val key = Key(alive.copyOf())
            if (key in failed) return null
            if (visited >= budget()) { exceeded = true; return null }
            visited++
            return key
        }
        abstract fun budget(): Int
    }

    private class Classic(game: MahjongGame, private val limit: Int) : Bitboard(game) {
        override fun budget() = limit
        fun free(alive: LongArray, i: Int) = uncovered(alive, i) && (none(left[i], alive) || none(right[i], alive))

        fun run(): Result {
            val plan = search(full())
            return if (plan != null) Result(Outcome.SOLVABLE, plan.map { MahjongSolutionStep(listOf(live[it[0]].id, live[it[1]].id)) }, visited)
            else Result(if (exceeded) Outcome.UNKNOWN else Outcome.UNSOLVABLE, states = visited)
        }

        private fun search(alive: LongArray): ArrayDeque<IntArray>? {
            if (empty(alive)) return ArrayDeque()
            val key = enter(alive) ?: return null
            val freeByKind = HashMap<Int, MutableList<Int>>()
            val aliveByKind = IntArray(MahjongFaces.KINDS)
            for (i in 0 until n) if (has(alive, i)) {
                aliveByKind[kinds[i]]++
                if (free(alive, i)) freeByKind.getOrPut(kinds[i]) { ArrayList() } += i
            }
            // Toutes les tuiles restantes d'une famille sont libres : les retirer ne coûte rien.
            for ((kind, list) in freeByKind) if (list.size >= 2 && list.size == aliveByKind[kind]) {
                val next = alive.copyOf(); list.forEach { clear(next, it) }
                val rest = search(next)
                if (rest == null) { if (!exceeded) failed += key; return null }
                list.chunked(2).asReversed().forEach { rest.addFirst(intArrayOf(it[0], it[1])) }
                return rest
            }
            val moves = ArrayList<IntArray>()
            for (list in freeByKind.values) for (a in list.indices) for (b in a + 1 until list.size) moves += intArrayOf(list[a], list[b])
            moves.sortByDescending { count(dependents[it[0]], alive) + count(dependents[it[1]], alive) }
            for (move in moves) {
                val next = alive.copyOf(); clear(next, move[0]); clear(next, move[1])
                val rest = search(next)
                if (rest != null) { rest.addFirst(move); return rest }
                if (exceeded) return null
            }
            failed += key
            return null
        }
    }

    private class Triple(private val game: MahjongGame, private val limit: Int) : Bitboard(game) {
        override fun budget() = limit
        private val total = IntArray(MahjongFaces.KINDS)
        private val trayStart = IntArray(MahjongFaces.KINDS)
        init {
            for (k in kinds) total[k]++
            for (id in game.tray) trayStart[MahjongFaces.kind(game.tiles[id].face)]++
        }

        fun run(): Result {
            val plan = search(full())
            return if (plan != null) Result(Outcome.SOLVABLE, plan.map { MahjongSolutionStep(listOf(live[it].id)) }, visited)
            else Result(if (exceeded) Outcome.UNKNOWN else Outcome.UNSOLVABLE, states = visited)
        }

        private fun search(alive: LongArray): ArrayDeque<Int>? {
            if (empty(alive)) return ArrayDeque()
            val key = enter(alive) ?: return null
            val tray = IntArray(MahjongFaces.KINDS)
            val left = IntArray(MahjongFaces.KINDS)
            for (i in 0 until n) if (has(alive, i)) left[kinds[i]]++
            var size = 0
            for (k in 0 until MahjongFaces.KINDS) { tray[k] = (trayStart[k] + total[k] - left[k]) % 3; size += tray[k] }
            val options = (0 until n).filter { uncovered(alive, it) && (tray[kinds[it]] == 2 || size + 1 < MahjongGame.TRAY) }
            // Compléter un triple ne coûte jamais rien.
            val sorted = options.sortedWith(compareByDescending<Int> { tray[kinds[it]] }.thenByDescending { count(dependents[it], alive) })
            sorted.firstOrNull()?.takeIf { tray[kinds[it]] == 2 }?.let { forced ->
                val next = alive.copyOf(); clear(next, forced)
                val rest = search(next)
                if (rest == null) { if (!exceeded) failed += key; return null }
                rest.addFirst(forced); return rest
            }
            for (i in sorted) {
                val next = alive.copyOf(); clear(next, i)
                val rest = search(next)
                if (rest != null) { rest.addFirst(i); return rest }
                if (exceeded) return null
            }
            failed += key
            return null
        }
    }

    /** Connexion et Cascade bougent ou ouvrent des chemins : recherche sur des copies du jeu. */
    private class Paths(private val game: MahjongGame, private val limit: Int) {
        private val failed = HashSet<String>()
        private var visited = 0
        private var exceeded = false

        fun run(): Result {
            val start = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false).apply { restore(game.snapshot()) }
            val plan = search(start)
            return if (plan != null) Result(Outcome.SOLVABLE, plan.map { MahjongSolutionStep(it) }, visited)
            else Result(if (exceeded) Outcome.UNKNOWN else Outcome.UNSOLVABLE, states = visited)
        }

        private fun search(state: MahjongGame): ArrayDeque<List<Int>>? {
            cancelled()
            if (state.complete) return ArrayDeque()
            val key = state.tiles.joinToString(",") { if (it.removed) "-" else "${it.x}.${it.y}" }
            if (key in failed) return null
            if (visited >= limit) { exceeded = true; return null }
            visited++
            val live = state.board
            val left = live.groupingBy { MahjongFaces.kind(it.face) }.eachCount()
            val moves = live.flatMap { a ->
                state.reachable(a, live).filter { it > a.id && state.compatible(a, state.tiles[it]) }.map { listOf(a.id, it) }
            }.sortedBy { left[MahjongFaces.kind(state.tiles[it[0]].face)] ?: 0 }
            for (move in moves) {
                val next = MahjongGame(state.mode, state.seed, state.stage, initializeBoard = false).apply { restore(state.snapshot()) }
                next.tap(move[0])
                if (next.tap(move[1]) != MahjongGame.Result.MATCHED) continue
                val rest = search(next)
                if (rest != null) { rest.addFirst(move); return rest }
                if (exceeded) return null
            }
            failed += key
            return null
        }
    }
}
