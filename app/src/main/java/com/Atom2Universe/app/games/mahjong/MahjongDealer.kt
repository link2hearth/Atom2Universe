package com.Atom2Universe.app.games.mahjong

import java.util.concurrent.CancellationException
import kotlin.math.abs
import kotlin.random.Random

/**
 * Distribution par construction à rebours (Solitaire, Triples, Connexion, Cascade) : on retire
 * d'abord des emplacements jouables ensemble, puis on leur donne une même famille. L'ordre de
 * retrait est une fin complète : chaque plateau est finissable, et chaque mélange aussi.
 */
internal object MahjongDealer {
    fun deal(game: MahjongGame, random: Random) {
        val live = game.board
        if (live.isEmpty()) return
        val groups = live.size / game.groupSize
        // Deux groupes par famille : quatre tuiles d'une même famille en Solitaire, six en Triples.
        val kinds = (0 until MahjongFaces.KINDS).shuffled(random).take((groups + 1) / 2)
        val groupKinds = kinds.flatMap { listOf(it, it) }.take(groups).shuffled(random)
        val used = IntArray(MahjongFaces.KINDS)
        val faceFor = { kind: Int -> MahjongFaces.faces(kind).let { it[used[kind]++ % it.size] } }
        repeat(32) { if (build(game, live, groupKinds, faceFor, random)) return }
        error("No construction for ${game.mode}")
    }

    /** Garde les familles restantes, change leur place. */
    fun redeal(game: MahjongGame, random: Random): Boolean {
        val live = game.board
        if (live.isEmpty()) return true
        val byKind = live.groupBy { MahjongFaces.kind(it.face) }
        val groupKinds = byKind.flatMap { (kind, list) -> List(list.size / game.groupSize) { kind } }.shuffled(random)
        val faces = byKind.mapValues { (_, list) -> ArrayDeque(list.map { it.face }.shuffled(random)) }
        game.solution.clear(); game.solutionSteps.clear()
        repeat(8) { if (build(game, live, groupKinds, { faces.getValue(it).removeFirst() }, random)) return true }
        return false
    }

    private fun build(game: MahjongGame, live: List<MahjongTile>, groupKinds: List<Int>,
                      faceFor: (Int) -> Int, random: Random): Boolean {
        if (game.mode == MahjongMode.TRIPLE) {
            val order = tripleOrder(live, random)
            // Trois triples mêlés par fenêtre de neuf retraits : la réserve ne dépasse jamais six.
            val window = if (game.stage <= 2) 2 else 3
            var group = 0
            for (chunk in order.chunked(window * 3)) {
                val count = chunk.size / 3
                val kinds = (0 until count).flatMap { i -> List(3) { groupKinds[group + i] } }.shuffled(random)
                chunk.forEachIndexed { i, tile -> tile.face = faceFor(kinds[i]) }
                group += count
            }
            game.solutionSteps += order.map { MahjongSolutionStep(listOf(it.id)) }
            game.solution += order.map { it.id }.chunked(3)
            return true
        }
        val pairs = (if (game.mode == MahjongMode.CLASSIC) classicPairs(live, random) else pathPairs(game, random)) ?: return false
        pairs.forEachIndexed { i, pair -> pair.forEach { id -> game.tiles[id].face = faceFor(groupKinds[i]) } }
        game.solutionSteps += pairs.map { MahjongSolutionStep(it) }
        game.solution += pairs
        return true
    }

    private class Layered(val live: List<MahjongTile>) {
        val n = live.size
        val above = Array(n) { IntArray(0) }
        val left = Array(n) { IntArray(0) }
        val right = Array(n) { IntArray(0) }
        val below = Array(n) { IntArray(0) }
        init {
            for (i in 0 until n) {
                val a = live[i]
                val up = ArrayList<Int>(); val l = ArrayList<Int>(); val r = ArrayList<Int>(); val down = ArrayList<Int>()
                for (j in 0 until n) {
                    val b = live[j]
                    if (abs(a.x - b.x) < 2 && abs(a.y - b.y) < 2) {
                        if (b.z > a.z) up += j else if (b.z < a.z) down += j
                    }
                    if (a.z == b.z && abs(a.y - b.y) < 2) {
                        if (b.x == a.x - 2) l += j else if (b.x == a.x + 2) r += j
                    }
                }
                above[i] = up.toIntArray(); left[i] = l.toIntArray(); right[i] = r.toIntArray(); below[i] = down.toIntArray()
            }
        }
        fun covered(alive: BooleanArray, i: Int) = above[i].any { alive[it] }
        fun free(alive: BooleanArray, i: Int) = alive[i] && !covered(alive, i) &&
            (left[i].none { alive[it] } || right[i].none { alive[it] })
        /** Les tuiles hautes et celles qui bloquent beaucoup partent d'abord : moins d'impasses. */
        fun weight(alive: BooleanArray, i: Int) = 1 + live[i].z * 3 + below[i].count { alive[it] } +
            (left[i].count { alive[it] } + right[i].count { alive[it] })
    }

    private fun pick(candidates: List<Int>, weight: (Int) -> Int, random: Random): Int {
        val total = candidates.sumOf(weight)
        var roll = random.nextInt(total)
        for (c in candidates) { roll -= weight(c); if (roll < 0) return c }
        return candidates.last()
    }

    private fun classicPairs(live: List<MahjongTile>, random: Random): List<List<Int>>? {
        val geometry = Layered(live)
        repeat(160) {
            cancelled()
            val alive = BooleanArray(live.size) { true }
            var left = live.size
            val pairs = ArrayList<List<Int>>(live.size / 2)
            while (left > 0) {
                val free = (0 until live.size).filter { geometry.free(alive, it) }
                if (free.size < 2) break
                val a = pick(free, { geometry.weight(alive, it) }, random)
                val b = pick(free - a, { geometry.weight(alive, it) }, random)
                alive[a] = false; alive[b] = false; left -= 2
                pairs += listOf(live[a].id, live[b].id)
            }
            if (left == 0) return pairs
        }
        return null
    }

    private fun tripleOrder(live: List<MahjongTile>, random: Random): List<MahjongTile> {
        val geometry = Layered(live)
        val alive = BooleanArray(live.size) { true }
        val order = ArrayList<MahjongTile>(live.size)
        repeat(live.size) {
            val free = (0 until live.size).filter { alive[it] && !geometry.covered(alive, it) }
            val i = pick(free, { 1 + live[it].z * 2 }, random)
            alive[i] = false; order += live[i]
        }
        return order
    }

    /** Connexion et Cascade : la construction rejoue les chutes avec les vraies directions. */
    private fun pathPairs(game: MahjongGame, random: Random): List<List<Int>>? {
        val copy = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false)
        copy.restore(game.snapshot())
        val pairs = ArrayList<List<Int>>()
        var fall = game.gravityDirection
        while (true) {
            cancelled()
            val live = copy.board
            if (live.isEmpty()) return pairs
            var chosen: List<Int>? = null
            for (a in live.shuffled(random)) {
                val ends = copy.reachable(a, live)
                if (ends.isNotEmpty()) { chosen = listOf(a.id, ends.random(random)); break }
            }
            val pair = chosen ?: return null
            pair.forEach { copy.tiles[it].removed = true }
            pairs += pair
            if (game.mode == MahjongMode.CASCADE) { copy.collapse(fall); fall = (fall + 1) % 4 }
        }
    }

    private fun cancelled() { if (Thread.currentThread().isInterrupted) throw CancellationException() }
}
