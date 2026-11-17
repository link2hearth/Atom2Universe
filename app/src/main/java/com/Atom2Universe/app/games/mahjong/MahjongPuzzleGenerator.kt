package com.Atom2Universe.app.games.mahjong

import java.util.concurrent.CancellationException
import kotlin.random.Random

/** Pile dix : construction procédurale d'abord, banque certifiée en secours. */
internal object MahjongPuzzleGenerator {
    fun tileCount(mode: MahjongMode, stage: Int) = when (mode) {
        MahjongMode.TEN -> if (stage >= 4) 24 else 20
        else -> 20
    }

    fun deal(game: MahjongGame, random: Random, proceduralAttempts: Int = MahjongProceduralGenerator.MAX_ATTEMPTS): Boolean {
        if (game.mode != MahjongMode.TEN) return false
        if (MahjongProceduralGenerator.deal(game, random, maxAttempts = proceduralAttempts)) return true
        val live = game.board.shuffled(random)
        if (live.isEmpty()) return true
        val layout = MahjongPuzzleLayouts.ten(game.stage >= 4).random(random)
        if (live.size > layout.tiles.size) return false
        val removedPairs = (layout.tiles.size - live.size) / 2
        val model = MahjongGame(game.mode, game.seed, game.stage, initializeBoard = false)
        layout.tiles.forEachIndexed { id, packed ->
            val cell = packed and 63
            model.tiles += MahjongTile(id, cell % game.columns, cell / game.columns, (packed shr 6) and 3, packed shr 8)
        }
        var picks = layout.picks.toList()
        repeat(removedPairs) {
            check(model.tap(picks[2 * it]) == MahjongGame.Result.SELECTED)
            check(model.tap(picks[2 * it + 1]) == MahjongGame.Result.MATCHED)
        }
        picks = picks.drop(2 * removedPairs)
        val survivors = model.board
        check(survivors.size == live.size)
        val complements = (0..3).shuffled(random)
        val flipped = BooleanArray(4) { random.nextBoolean() }
        val mirrorX = random.nextBoolean()
        val mirrorY = random.nextBoolean()
        val ids = HashMap<Int, Int>()
        survivors.forEachIndexed { index, source ->
            if (Thread.currentThread().isInterrupted) throw CancellationException()
            val tile = live[index]; ids[source.id] = tile.id
            var x = source.x; var y = source.y
            if (mirrorX) x = game.columns - 1 - x
            if (mirrorY) y = game.rows - 1 - y
            tile.x = x; tile.y = y; tile.z = source.z
            val pip = source.face % 9
            val group = minOf(pip, 8 - pip)
            val number = if (pip == 4) 4 else if ((pip < 4) != flipped[group]) complements[group] else 8 - complements[group]
            tile.face = random.nextInt(3) * 9 + number
        }
        val plan = picks.map { ids.getValue(it) }.chunked(2).map { MahjongSolutionStep(it) }
        val verified = checkNotNull(MahjongPuzzleSolver.verifiedContinuation(game, plan))
        game.solutionSteps += verified
        game.solution += verified.flatMap { it.ids }.chunked(2)
        return true
    }
}
