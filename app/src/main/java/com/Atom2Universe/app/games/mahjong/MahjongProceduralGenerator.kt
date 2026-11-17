package com.Atom2Universe.app.games.mahjong

import java.util.concurrent.CancellationException
import kotlin.random.Random

/** Fresh dependency graphs and positions. This constructor deliberately has no layout-bank dependency. */
internal object MahjongProceduralGenerator {
    // Glissières et Pile dix seulement : les autres modes passent par MahjongDealer.
    const val MAX_ATTEMPTS = 384
    const val MAX_STATES = 120_000
    data class Report(val procedural: Boolean, val attempts: Int, val states: Int)
    private class Work(val limit: Int) {
        var states = 0
        val remaining get() = (limit - states).coerceAtLeast(0)
        fun analyze(game: MahjongGame): MahjongPuzzleSolver.Quality =
            MahjongPuzzleSolver.analyze(game, minOf(3_000, remaining)).also { states += it.states }
        fun solve(game: MahjongGame, capacity: Int = 6): MahjongPuzzleSolver.Result =
            MahjongPuzzleSolver.solve(game, minOf(6_000, remaining), capacity).also { states += it.states }
    }

    fun deal(game: MahjongGame, random: Random, maxAttempts: Int = MAX_ATTEMPTS, maxStates: Int = MAX_STATES): Boolean {
        cancelled()
        val live = game.board
        val standard = if (game.mode == MahjongMode.SLIDE) MahjongIceGenerator.tileCount(game.stage)
            else MahjongPuzzleGenerator.tileCount(game.mode, game.stage)
        val work = Work(maxStates)
        var attempts = 0
        if (live.size <= standard && live.isNotEmpty()) while (attempts < maxAttempts && work.remaining > 0) {
            cancelled(); attempts++
            val candidate = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false)
            candidate.restore(candidate.snapshot().copy(matches = game.matches))
            val full = live.size == standard
            val built = when (game.mode) {
                MahjongMode.SLIDE -> ice(candidate, live.size, random)
                MahjongMode.TEN -> dependencies(candidate, live.size, random)
                else -> return false
            }
            if (!built) continue
            val plan = if (game.mode == MahjongMode.SLIDE) {
                val quality = MahjongIceSolver.analyze(candidate, minOf(3_000, work.remaining)) ?: continue
                work.states += quality.states
                quality.solution.takeIf { !full || MahjongIceGenerator.isPuzzle(quality, live.size / 2, game.stage >= 4) }
            } else if (full) certify(candidate, work.analyze(candidate))
            else work.solve(candidate).steps
            if (plan == null) continue
            val actual = live.shuffled(random)
            val palette = (0 until 27).shuffled(random)
            val ids = candidate.tiles.indices.associateWith { actual[it].id }
            candidate.tiles.forEachIndexed { i, source ->
                actual[i].apply {
                    x = source.x; y = source.y; z = source.z
                    face = if (game.mode == MahjongMode.TEN) random.nextInt(3) * 9 + source.face % 9 else palette[source.face]
                }
            }
            val mapped = plan.map { step -> MahjongSolutionStep(step.ids.map(ids::getValue),
                step.slide?.let { MahjongSlide(ids.getValue(it.id), it.x, it.y) }) }
            // Solver certificates were replayed against the real engine before ID/face relabelling.
            game.solutionSteps += mapped
            game.solution += mapped.flatMap { it.ids }.chunked(2)
            game.generation = Report(true, attempts, work.states)
            return true
        }
        game.generation = Report(false, attempts, work.states)
        return false
    }

    private fun add(game: MahjongGame, cell: Int, z: Int, face: Int) {
        game.tiles += MahjongTile(game.tiles.size, cell % 6, cell / 6, z, face)
    }


    /** Pile dix : des couloirs qui se croisent, chaque paire ouvre la suivante. */
    private fun dependencies(game: MahjongGame, count: Int, random: Random): Boolean {
        val hard = game.stage >= 4
        val cores = if (count >= 16 && hard) 2 else if (count >= 8) 1 else 0
        repeat(cores) { core ->
            val available = (0 until game.columns * game.rows).filter { cell -> game.tiles.none { it.x == cell % 6 && it.y == cell / 6 } }
            val xs = listOf(random.nextInt(0, 2), random.nextInt(2, 4), random.nextInt(4, 6))
            val row = random.nextInt(game.rows - 1)
            val ys = listOf(row, row + 1)
            val cells = listOf(ys[0] * 6 + xs[0], ys[0] * 6 + xs[1], ys[1] * 6 + xs[1], ys[1] * 6 + xs[2])
            if (cells.any { it !in available }) return false
            cells.forEachIndexed { i, cell -> add(game, cell, 0, if (i % 2 == 0) core else 8 - core) }
        }
        // Insert new removable pairs backwards. Their removal restores the previous geometry.
        while (game.tiles.size < count) {
            cancelled()
            var inserted = false
            for (trial in 0 until 48) {
                val slots = (0 until game.columns * game.rows).filter { cell ->
                    game.tiles.none { it.x == cell % 6 && it.y == cell / 6 }
                }
                if (slots.size < 2) return false
                var a = slots.random(random); var b = slots.random(random)
                if (cores > 0) {
                    // Cross an existing corridor, then insert its removable key
                    // on the perpendicular axis. The next insertion can cross
                    // this new corridor in turn, building a chain of releases.
                    val gates = MahjongPuzzleSolver.legalChoices(game).flatMap { ids ->
                        val first = game.tiles[ids[0]]; val second = game.tiles[ids[1]]
                        val horizontal = first.y == second.y
                        val low = if (horizontal) minOf(first.x, second.x) else minOf(first.y, second.y)
                        val high = if (horizontal) maxOf(first.x, second.x) else maxOf(first.y, second.y)
                        (low + 1 until high).map { axis ->
                            (if (horizontal) first.y * 6 + axis else axis * 6 + first.x) to horizontal
                        }
                    }
                    if (gates.isEmpty()) continue
                    val gate = gates.random(random)
                    a = gate.first
                    val endpoints = slots.filter { cell ->
                        if (gate.second) cell % 6 == a % 6 && cell / 6 != a / 6
                        else cell / 6 == a / 6 && cell % 6 != a % 6
                    }
                    if (endpoints.isEmpty()) continue
                    b = endpoints.random(random)
                }
                if (a == b) continue
                if (a % 6 != b % 6 && a / 6 != b / 6) continue
                val f = random.nextInt(cores, 5)
                val first = game.tiles.size
                add(game, a, 0, f)
                add(game, b, 0, 8 - f)
                val legal = MahjongPuzzleSolver.legalChoices(game).any { it == listOf(first, first + 1) }
                if (legal) { inserted = true; break }
                game.tiles.removeAt(game.tiles.lastIndex); game.tiles.removeAt(game.tiles.lastIndex)
            }
            if (!inserted) return false
        }
        return true
    }

    private fun ice(game: MahjongGame, count: Int, random: Random): Boolean {
        if (game.stage >= 4 && count == MahjongIceGenerator.tileCount(game.stage)) return hardIce(game, random)
        val inside = (0 until 6 * game.rows).filter { it % 6 in 1..4 && it / 6 in 1 until game.rows - 1 }
        val border = (0 until 6 * game.rows).filter { it !in inside }.shuffled(random)
        val anchor = border.firstOrNull { it % 6 != border[0] % 6 && it / 6 != border[0] / 6 } ?: return false
        add(game, border[0], 0, 0); add(game, anchor, 0, 0)
        if (inside.size < count - 2) return false
        inside.shuffled(random).take(count - 2).forEach { add(game, it, 0, game.tiles.size) }
        val distances = MahjongIceSolver.geometryDistances(game)
        val options = Array(count) { a -> (2 until count).filter { b ->
            a != b && game.tiles[a].x != game.tiles[b].x && game.tiles[a].y != game.tiles[b].y && distances[a][b] !in 0..1
        }.shuffled(random) }
        return pairIce(game, options, ((1 shl count) - 1) and 3.inv(), 1)
    }

    private fun hardIce(game: MahjongGame, random: Random): Boolean {
        // Opposite edge stops leave room for a second indispensable stopping family.
        val mirrorX = random.nextBoolean(); val mirrorY = random.nextBoolean()
        fun cell(x: Int, y: Int) = (if (mirrorY) game.rows - 1 - y else y) * 6 + if (mirrorX) 5 - x else x
        add(game, cell(0, 1), 0, 0); add(game, cell(5, game.rows - 2), 0, 0)
        for (y in 1 until game.rows - 1) for (x in 1..4) add(game, cell(x, y), 0, game.tiles.size)
        val anchored = MahjongIceSolver.geometryDistances(game, anchors = 3L)
        val vertices = (2 until game.tiles.size).filter { a -> (2 until game.tiles.size).any { b ->
            a != b && game.tiles[a].x != game.tiles[b].x && game.tiles[a].y != game.tiles[b].y && anchored[a][b] < 0
        } }
        if (vertices.size != 16) return false
        val outside = (2 until game.tiles.size).filter { it !in vertices }
        val bridges = outside.flatMap { a -> outside.filter { b -> a < b && game.tiles[a].x != game.tiles[b].x &&
            game.tiles[a].y != game.tiles[b].y && anchored[a][b] >= 0 }.map { a to it } }
        if (bridges.isEmpty()) return false
        val bridge = bridges.random(random)
        val indices = listOf(0, 1, bridge.first, bridge.second) + vertices.shuffled(random)
        val source = game.tiles.toList()
        game.tiles.clear()
        indices.forEachIndexed { i, old -> add(game, source[old].y * 6 + source[old].x, 0, if (i < 4) i / 2 else i) }
        val distances = MahjongIceSolver.geometryDistances(game)
        if (distances[0][1] < 1 || distances[2][3] < 1) return false
        val options = Array(20) { a -> (4 until 20).filter { b ->
            a != b && game.tiles[a].x != game.tiles[b].x && game.tiles[a].y != game.tiles[b].y &&
                distances[a][b] !in 0..1 && anchored[indices[a]][indices[b]] < 0
        }.shuffled(random) }
        // Only the bridge can finish with the border anchor alone. Removing either
        // of these two families early leaves no possible last interior fusion.
        return pairIce(game, options, ((1 shl 20) - 1) and 15.inv(), 2)
    }

    private fun pairIce(game: MahjongGame, options: Array<List<Int>>, initial: Int, firstFace: Int): Boolean {
        var nodes = 0
        fun pair(mask: Int, face: Int): Boolean {
            cancelled()
            if (mask == 0) return true
            if (++nodes > 128) return false
            val a = options.indices.filter { mask and (1 shl it) != 0 }
                .minBy { i -> options[i].count { mask and (1 shl it) != 0 } }
            for (b in options[a]) if (mask and (1 shl b) != 0) {
                game.tiles[a].face = face; game.tiles[b].face = face
                if (pair(mask and (1 shl a).inv() and (1 shl b).inv(), face + 1)) return true
            }
            return false
        }
        return pair(initial, firstFace)
    }

    private fun copy(game: MahjongGame) = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false).apply { restore(game.snapshot()) }

    private fun certify(game: MahjongGame, quality: MahjongPuzzleSolver.Quality): List<MahjongSolutionStep>? {
        val opening = MahjongPuzzleSolver.legalChoices(game)
        if (opening.size < if (game.stage >= 4) 3 else 2) return null
        val plan = quality.result.steps ?: return null
        if (quality.choices.any { it.outcome == MahjongPuzzleSolver.Outcome.UNKNOWN }) return null
        val winning = quality.choices.filter { it.outcome == MahjongPuzzleSolver.Outcome.SOLVABLE }
        if (winning.size !in 1..5) return null
        if (quality.choices.count { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE } < if (game.stage >= 4) 2 else 1) return null
        val before = opening.map { it.toSet() }.toSet()
        for (choice in winning) {
            val trial = copy(game); choice.ids.forEach(trial::tap)
            val horizontal = game.tiles[choice.ids[0]].y == game.tiles[choice.ids[1]].y
            if (MahjongPuzzleSolver.legalChoices(trial).any { it.toSet() !in before &&
                (game.tiles[it[0]].y == game.tiles[it[1]].y) != horizontal }) return plan
        }
        return null
    }

    private fun cancelled() { if (Thread.currentThread().isInterrupted) throw CancellationException() }
}
