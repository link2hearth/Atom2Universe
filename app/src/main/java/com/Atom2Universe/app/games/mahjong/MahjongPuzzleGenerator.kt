package com.Atom2Universe.app.games.mahjong

import java.util.concurrent.CancellationException
import kotlin.random.Random

/** Procedural construction first; certified banks are a bounded-search safety net. */
internal object MahjongPuzzleGenerator {
    fun tileCount(mode: MahjongMode, stage: Int) = when (mode) {
        MahjongMode.CLASSIC -> if (stage >= 4) 32 else 24
        MahjongMode.TRIPLE -> if (stage >= 4) 24 else 18
        MahjongMode.TEN -> if (stage >= 4) 24 else 20
        else -> 20
    }

    fun deal(game: MahjongGame, random: Random, proceduralAttempts: Int = MahjongProceduralGenerator.MAX_ATTEMPTS): Boolean {
        if (MahjongProceduralGenerator.deal(game, random, maxAttempts = proceduralAttempts)) return true
        val live = game.board.shuffled(random)
        if (live.isEmpty()) return true
        val layout = MahjongPuzzleLayouts.get(game.mode, game.stage >= 4).random(random)
        if (live.size > layout.tiles.size) return false // Preserve large legacy boards via their constructive dealer.
        val removedPairs = (layout.tiles.size - live.size) / 2
        if (game.mode == MahjongMode.CASCADE && game.matches % 4 != removedPairs % 4) return false
        val model = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false)
        layout.tiles.forEachIndexed { id, packed ->
            val cell = packed and 63
            model.tiles += MahjongTile(id, cell % game.columns, cell / game.columns, (packed shr 6) and 3, packed shr 8)
        }
        var picks = layout.picks.toList()
        if (game.mode == MahjongMode.TRIPLE) {
            val keep = model.tiles.map { it.face }.distinct().shuffled(random).take(live.size / 3).toSet()
            model.tiles.forEach { it.removed = it.face !in keep }
            picks = picks.filter { !model.tiles[it].removed }
        } else {
            repeat(removedPairs) {
                check(model.tap(picks[2 * it]) == MahjongGame.Result.SELECTED)
                check(model.tap(picks[2 * it + 1]) == MahjongGame.Result.MATCHED)
            }
            picks = picks.drop(2 * removedPairs)
        }
        val survivors = model.board
        check(survivors.size == live.size)
        val palette = (0 until 27).shuffled(random)
        val complements = (0..3).shuffled(random)
        val flipped = BooleanArray(4) { random.nextBoolean() }
        val mirrorX = game.mode != MahjongMode.CASCADE && random.nextBoolean()
        val mirrorY = game.mode != MahjongMode.CASCADE && random.nextBoolean()
        val transpose = game.rows == game.columns && game.mode !in listOf(MahjongMode.CLASSIC, MahjongMode.CASCADE) && random.nextBoolean()
        val dx = if (game.mode == MahjongMode.CASCADE) random.nextInt(-survivors.minOf { it.x }, game.columns - survivors.maxOf { it.x }) else 0
        val dy = if (game.mode == MahjongMode.CASCADE) random.nextInt(-survivors.minOf { it.y }, game.rows - survivors.maxOf { it.y }) else 0
        val ids = HashMap<Int, Int>()
        survivors.forEachIndexed { index, source ->
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            val tile = live[index]; ids[source.id] = tile.id
            var x = source.x + dx; var y = source.y + dy
            if (transpose) { val oldX = x; x = y; y = oldX }
            if (mirrorX) x = game.columns - 1 - x
            if (mirrorY) y = game.rows - 1 - y
            tile.x = x; tile.y = y; tile.z = source.z
            tile.face = if (game.mode == MahjongMode.TEN) {
                val pip = source.face % 9
                val group = minOf(pip, 8 - pip)
                val number = if (pip == 4) 4 else if ((pip < 4) != flipped[group]) complements[group] else 8 - complements[group]
                random.nextInt(3) * 9 + number
            } else palette[source.face]
        }
        val plan = picks.map { ids.getValue(it) }.chunked(if (game.mode == MahjongMode.TRIPLE) 1 else 2)
            .map { MahjongSolutionStep(it) }
        val verified = checkNotNull(MahjongPuzzleSolver.verifiedContinuation(game, plan))
        game.solutionSteps += verified
        game.solution += verified.flatMap { it.ids }.chunked(if (game.mode == MahjongMode.TRIPLE) 3 else 2)
        return true
    }
}
