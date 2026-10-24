package com.Atom2Universe.app.games.mahjong

import java.util.concurrent.CancellationException
import kotlin.random.Random

/** Fresh dependency graphs and positions. This constructor deliberately has no layout-bank dependency. */
internal object MahjongProceduralGenerator {
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
        var parent: MahjongGame? = null
        var parentScore = Int.MIN_VALUE
        var age = 0
        if (live.size <= standard && live.isNotEmpty()) while (attempts < maxAttempts && work.remaining > 0) {
            cancelled(); attempts++
            val candidate = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false)
            candidate.restore(candidate.snapshot().copy(matches = game.matches))
            val full = live.size == standard
            if (age++ >= 64) { parent = null; parentScore = Int.MIN_VALUE; age = 0 }
            val built = if (parent != null) {
                candidate.restore(parent.snapshot())
                mutate(candidate, random)
            } else when (game.mode) {
                MahjongMode.SLIDE -> ice(candidate, live.size, random)
                MahjongMode.TRIPLE -> triple(candidate, live.size, random, work)
                MahjongMode.CLASSIC, MahjongMode.CONNECT, MahjongMode.TEN ->
                    dependencies(candidate, live.size, random)
                MahjongMode.CASCADE -> cascade(candidate, live.size, random)
            }
            if (!built) continue
            val plan = if (game.mode == MahjongMode.SLIDE) {
                val quality = MahjongIceSolver.analyze(candidate, minOf(3_000, work.remaining)) ?: continue
                work.states += quality.states
                quality.solution.takeIf { !full || MahjongIceGenerator.isPuzzle(quality, live.size / 2, game.stage >= 4) }
            } else if (full) {
                val quality = work.analyze(candidate)
                if (game.mode == MahjongMode.CASCADE && quality.result.steps != null &&
                    quality.choices.none { it.outcome == MahjongPuzzleSolver.Outcome.UNKNOWN }) {
                    val bad = quality.choices.count { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE }
                    val good = quality.choices.size - bad
                    val score = minOf(bad, if (game.stage >= 4) 2 else 1) * 20 - good * 3
                    if (score >= parentScore) { parent = copy(candidate); parentScore = score }
                }
                certify(candidate, work, quality)
            } else work.solve(candidate).steps
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
            game.solution += mapped.flatMap { it.ids }.chunked(if (game.mode == MahjongMode.TRIPLE) 3 else 2)
            game.generation = Report(true, attempts, work.states)
            return true
        }
        game.generation = Report(false, attempts, work.states)
        return false
    }

    private fun add(game: MahjongGame, cell: Int, z: Int, face: Int) {
        game.tiles += MahjongTile(game.tiles.size, cell % 6, cell / 6, z, face)
    }

    private fun mutate(game: MahjongGame, random: Random): Boolean {
        val a = game.tiles.random(random)
        val b = game.tiles.random(random)
        if (a.face == b.face) return false
        val old = a.face; a.face = b.face; b.face = old
        return true
    }

    /** Four copies share two keys: spending the two outside keys leaves a dependency cycle. */
    private fun dependencies(game: MahjongGame, count: Int, random: Random): Boolean {
        val hard = game.stage >= 4
        val cores = if (count >= 16 && hard) 2 else if (count >= 8) 1 else 0
        var face = 0
        repeat(cores) { core ->
            val available = (0 until game.columns * game.rows).filter { cell -> game.tiles.none { it.x == cell % 6 && it.y == cell / 6 } }
            when (game.mode) {
                MahjongMode.CLASSIC -> {
                    // Separated starting towers avoid accidental lateral locks in the dependency cycle.
                    val cells = available.filter { (it % 6 + it / 6) % 2 == 0 }.shuffled(random).take(4)
                    if (cells.size < 4) return false
                    add(game, cells[0], 0, face); add(game, cells[0], 1, face + 1)
                    add(game, cells[1], 0, face + 1); add(game, cells[1], 1, face)
                    add(game, cells[2], 0, face); add(game, cells[3], 0, face)
                }
                MahjongMode.CONNECT -> {
                    val origins = available.filter { it % 6 < 5 && it / 6 < game.rows - 1 &&
                        listOf(it + 1, it + 6, it + 7).all(available::contains) }
                    if (origins.isEmpty()) return false
                    val origin = origins.random(random)
                    add(game, origin, 0, face); add(game, origin + 7, 0, face)
                    add(game, origin + 1, 0, face + 1); add(game, origin + 6, 0, face + 1)
                    val outside = available.filter { cell ->
                        cell % 6 !in origin % 6..origin % 6 + 1 && cell / 6 !in origin / 6..origin / 6 + 1
                    }.shuffled(random)
                    val a = outside.firstOrNull() ?: return false
                    val b = outside.firstOrNull { it % 6 != a % 6 && it / 6 != a / 6 } ?: return false
                    add(game, a, 0, face); add(game, b, 0, face)
                }
                MahjongMode.TEN -> {
                    val xs = listOf(random.nextInt(0, 2), random.nextInt(2, 4), random.nextInt(4, 6))
                    val row = random.nextInt(game.rows - 1)
                    val ys = listOf(row, row + 1)
                    val cells = listOf(ys[0] * 6 + xs[0], ys[0] * 6 + xs[1], ys[1] * 6 + xs[1], ys[1] * 6 + xs[2])
                    if (cells.any { it !in available }) return false
                    cells.forEachIndexed { i, cell -> add(game, cell, 0, if (i % 2 == 0) core else 8 - core) }
                }
                else -> return false
            }
            face += 2
        }
        val protected = if (game.mode == MahjongMode.CLASSIC) (0 until cores).flatMap { core ->
            listOf(game.tiles[core * 6 + 4], game.tiles[core * 6 + 5]).map { it.y * 6 + it.x }
        }.toSet() else emptySet()
        // Insert new removable pairs backwards. Their removal restores the previous geometry.
        while (game.tiles.size < count) {
            cancelled()
            var inserted = false
            for (trial in 0 until 48) {
                val slots = if (game.mode == MahjongMode.CLASSIC) {
                    (0 until game.columns * game.rows).mapNotNull { cell ->
                        val top = game.tiles.filter { it.x == cell % 6 && it.y == cell / 6 }.maxOfOrNull { it.z } ?: -1
                        if (top < 2 && cell !in protected) cell to (top + 1) else null
                    }
                } else (0 until game.columns * game.rows).filter { cell ->
                    game.tiles.none { it.x == cell % 6 && it.y == cell / 6 }
                }.map { it to 0 }
                if (slots.size < 2) return false
                var a = slots.random(random); var b = slots.random(random)
                if (game.mode == MahjongMode.CLASSIC) {
                    val upper = slots.filter { it.second > 0 }
                    if (upper.isNotEmpty()) {
                        if (random.nextInt(4) != 0) a = upper.random(random)
                        if (random.nextInt(4) != 0) b = upper.random(random)
                    }
                }
                if (game.mode == MahjongMode.TEN && cores > 0) {
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
                    a = gate.first to 0
                    val endpoints = slots.filter { (cell, _) ->
                        if (gate.second) cell % 6 == a.first % 6 && cell / 6 != a.first / 6
                        else cell / 6 == a.first / 6 && cell % 6 != a.first % 6
                    }
                    if (endpoints.isEmpty()) continue
                    b = endpoints.random(random)
                }
                if (a.first == b.first) continue
                if (game.mode == MahjongMode.CONNECT && (a.first % 6 == b.first % 6 || a.first / 6 == b.first / 6)) continue
                if (game.mode == MahjongMode.TEN && a.first % 6 != b.first % 6 && a.first / 6 != b.first / 6) continue
                val f = if (game.mode == MahjongMode.TEN) random.nextInt(cores, 5) else face
                val first = game.tiles.size
                add(game, a.first, a.second, f)
                add(game, b.first, b.second, if (game.mode == MahjongMode.TEN) 8 - f else f)
                val legal = MahjongPuzzleSolver.legalChoices(game).any { it == listOf(first, first + 1) }
                if (legal) { inserted = true; face++; break }
                game.tiles.removeAt(game.tiles.lastIndex); game.tiles.removeAt(game.tiles.lastIndex)
            }
            if (!inserted) return false
        }
        return true
    }

    /** A generated cycle of covered triples creates unavoidable reserve pressure. */
    private fun triple(game: MahjongGame, count: Int, random: Random, work: Work): Boolean {
        val hard = game.stage >= 4
        val coreSize = 12
        val coreTowers = if (hard) 3 else 4
        val cells = (0 until game.columns * game.rows).shuffled(random)
        if (count < coreSize) {
            val faces = List(count) { it / 3 }.shuffled(random)
            faces.forEachIndexed { i, f -> add(game, cells[i % minOf(3, count)], i / minOf(3, count), f) }
            return true
        }
        run {
            // Search a small dependency graph, before adding unrelated families. No precomputed patterns.
            var found = false
            for (trial in 0 until 256) {
                cancelled()
                if (work.remaining == 0) return false
                game.tiles.clear()
                val faces = List(12) { it / 3 }.shuffled(random)
                faces.forEachIndexed { i, f -> add(game, cells[i % coreTowers], i / coreTowers, f) }
                if (game.board.filter(game::isFree).groupingBy { it.face }.eachCount().values.any { it >= 3 }) continue
                if (work.solve(game, if (hard) 5 else 4).outcome != MahjongPuzzleSolver.Outcome.UNSOLVABLE) continue
                if (work.solve(game).outcome == MahjongPuzzleSolver.Outcome.SOLVABLE) { found = true; break }
            }
            if (!found) return false
        }
        val extra = count - coreSize
        val towers = extra / 3
        val order = (0 until towers).shuffled(random)
        if (towers > 0) repeat(3) { z -> repeat(towers) { t ->
            add(game, cells[coreTowers + t], z, coreSize / 3 + order[(t + z) % towers])
        } }
        return true
    }

    private fun cascade(game: MahjongGame, count: Int, random: Random): Boolean {
        // Dense rows make the first fall consequential. Width, offset, removal
        // order and partner assignments are generated afresh.
        val width = random.nextInt(4, 6)
        val height = (count + width - 1) / width
        val dx = random.nextInt(7 - width); val dy = random.nextInt(game.rows - height + 1)
        val cells = (0 until count).map { (it / width + dy) * 6 + it % width + dx }
        cells.shuffled(random).forEach { add(game, it, 0, 0) }
        val pairs = MahjongPuzzleSolver.constructionPairs(game, random) ?: return false
        val duplicates = 1
        val labels = List(pairs.size) { if (it < duplicates * 2) it / 2 else it - duplicates }.shuffled(random)
        pairs.forEachIndexed { i, ids -> ids.forEach { game.tiles[it].face = labels[i] } }
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

    private fun certify(game: MahjongGame, work: Work, quality: MahjongPuzzleSolver.Quality): List<MahjongSolutionStep>? {
        val triple = game.mode == MahjongMode.TRIPLE
        val opening = MahjongPuzzleSolver.legalChoices(game)
        if (!triple && opening.size < if (game.stage >= 4) 3 else 2) return null
        if (game.mode == MahjongMode.CLASSIC && game.tiles.maxOf { it.z } < 2) return null
        val plan = quality.result.steps ?: return null
        if (quality.choices.any { it.outcome == MahjongPuzzleSolver.Outcome.UNKNOWN }) return null
        val winning = quality.choices.filter { it.outcome == MahjongPuzzleSolver.Outcome.SOLVABLE }
        if (triple) {
            if (game.board.filter(game::isFree).groupingBy { it.face }.eachCount().values.any { it >= 3 }) return null
            if (work.solve(game, if (game.stage >= 4) 5 else 4).outcome != MahjongPuzzleSolver.Outcome.UNSOLVABLE) return null
            val trial = copy(game)
            for (step in plan.take(8)) {
                if (work.analyze(trial).choices.any { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE }) return plan
                step.ids.forEach(trial::tap)
            }
            return null
        }
        if (winning.size !in 1..if (game.mode in listOf(MahjongMode.CLASSIC, MahjongMode.TEN)) 5 else 3) return null
        if (quality.choices.count { it.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE } < if (game.stage >= 4) 2 else 1) return null
        if (game.mode == MahjongMode.CONNECT) {
            if (winning.any { val a = game.tiles[it.ids[0]]; val b = game.tiles[it.ids[1]]; a.x == b.x || a.y == b.y }) return null
            return plan
        }
        val before = opening.map { it.toSet() }.toSet()
        val free = game.board.filter(game::isFree).map { it.id }.toSet()
        for (choice in winning) {
            val trial = copy(game); choice.ids.forEach(trial::tap)
            when (game.mode) {
                MahjongMode.CLASSIC -> if (trial.board.any { trial.isFree(it) && it.id !in free }) return plan
                MahjongMode.CASCADE -> {
                    val fixed = copy(game); choice.ids.forEach { fixed.tiles[it].removed = true }
                    val fixedPairs = MahjongPuzzleSolver.legalChoices(fixed).map { it.toSet() }.toSet()
                    if (MahjongPuzzleSolver.legalChoices(trial).any { it.toSet() !in fixedPairs }) return plan
                }
                MahjongMode.TEN -> {
                    val horizontal = game.tiles[choice.ids[0]].y == game.tiles[choice.ids[1]].y
                    if (MahjongPuzzleSolver.legalChoices(trial).any { it.toSet() !in before &&
                        (game.tiles[it[0]].y == game.tiles[it[1]].y) != horizontal }) return plan
                }
                else -> Unit
            }
        }
        return null
    }

    private fun cancelled() { if (Thread.currentThread().isInterrupted) throw CancellationException() }
}
