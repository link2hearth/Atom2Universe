package com.Atom2Universe.app.games.mahjong

import kotlin.random.Random

/** Procedural ice puzzles, with certified layouts retained only for bounded-search fallback. */
internal object MahjongIceGenerator {
    fun tileCount(stage: Int) = if (stage < 4) 16 else 20

    fun deal(game: MahjongGame, random: Random, proceduralAttempts: Int = MahjongProceduralGenerator.MAX_ATTEMPTS) {
        if (MahjongProceduralGenerator.deal(game, random, maxAttempts = proceduralAttempts)) return
        val live = game.board.shuffled(random)
        if (live.isEmpty()) return
        val standardCount = tileCount(game.stage)
        val legacy = live.size > standardCount
        val layouts = when {
            game.stage < 4 && legacy -> MahjongIceLayouts.legacyNormal
            game.stage < 4 -> MahjongIceLayouts.normal
            legacy -> MahjongIceLayouts.legacyHard
            else -> MahjongIceLayouts.hard
        }
        val cells = layouts.random(random)
        check(live.size <= cells.size && live.size % 2 == 0)
        val mirrorX = random.nextBoolean(); val mirrorY = random.nextBoolean()
        val transpose = game.rows == game.columns && random.nextBoolean()
        val candidate = MahjongGame(MahjongMode.SLIDE, game.seed, game.stage, initializeBoard = false)
        repeat(live.size) { index ->
            val cell = cells[index]
            var x = cell % game.columns; var y = cell / game.columns
            if (transpose) { val swap = x; x = y; y = swap }
            if (mirrorX) x = game.columns - 1 - x
            if (mirrorY) y = game.rows - 1 - y
            candidate.tiles += MahjongTile(index, x, y, 0, index / 2)
        }
        // Even prefixes are reverse solution suffixes, so partial redeals also
        // keep a finish. Dense banks exist only to preserve older saves' tile IDs.
        val quality = checkNotNull(MahjongIceSolver.analyze(candidate))
        check(quality.outcome == MahjongIceSolver.Outcome.SOLVABLE)
        val fullPuzzle = live.size == standardCount
        if (fullPuzzle) check(isPuzzle(quality, live.size / 2, game.stage >= 4))
        val palette = (0 until 27).shuffled(random)
        candidate.tiles.forEachIndexed { index, source ->
            val tile = live[index]
            tile.x = source.x; tile.y = source.y; tile.z = 0; tile.face = palette[source.face]
        }
        // Re-solve/replay with the real IDs, including removed tiles retained by
        // saves. No unvalidated or merely "probably solvable" fallback is used.
        val plan = checkNotNull(MahjongIceSolver.solve(game))
        game.solutionSteps += plan
        game.solution += plan.flatMap { it.ids }.chunked(2)
    }

    internal fun isPuzzle(quality: MahjongIceSolver.Quality, pairs: Int, hard: Boolean): Boolean =
        quality.outcome == MahjongIceSolver.Outcome.SOLVABLE &&
            quality.opening.none { it.outcome == MahjongIceSolver.Outcome.UNKNOWN } &&
            quality.immediatePairs == 0 && quality.stopperDependentPairs >= pairs - 1 &&
            quality.winningChoices in 1..3 && quality.losingChoices >= (if (hard) 2 else 1) &&
            quality.opening.filter { it.outcome == MahjongIceSolver.Outcome.SOLVABLE }.all { it.minSlides >= 2 }

}
