package com.Atom2Universe.app.games.atomblocks

import java.io.File
import java.util.Locale
import java.util.Random
import kotlin.math.ceil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Opt-in balance experiment, not a human-success prediction or a pass/fail difficulty target.
 * Run with -PbancsMesure --tests "*AtomBlocksBalanceSimulationTest.adventureCampaign".
 * ATOM_BLOCKS_SIM_SAMPLES controls repetitions per policy/seed family (default 16).
 * ATOM_BLOCKS_SIM_TAG separates before/after CSV reports in build/reports/atomblocks-balance.
 * Every committed move, deal, rescue and victory uses the production engine unchanged.
 */
class AtomBlocksBalanceSimulationTest {
    private enum class Policy { RANDOM, CASUAL, GREEDY, PLANNER, HINT }
    private data class Placement(val row: Int, val col: Int, val mask: Long, val size: Int)
    private data class Candidate(
        val move: AtomBlocksEngine.Move,
        val occupied: Long,
        val gems: Long,
        val remaining: Int,
        val reward: Double,
        val quality: Double,
    )
    private data class Path(
        val first: AtomBlocksEngine.Move,
        val occupied: Long,
        val gems: Long,
        val remaining: Int,
        val reward: Double,
        val quality: Double,
    )
    private data class Run(
        val level: Int,
        val seedKind: String,
        val seed: Long,
        val policy: Policy,
        val repetition: Int,
        val playerSeed: Long,
        val won: Boolean,
        val wonWithoutHelp: Boolean,
        val moves: Int,
        val strictMoves: Int,
        val rescues: Int,
        val firstBlockMove: Int,
        val maxNoProgress: Int,
        val remainingGems: Int,
        val scoreGoalMove: Int,
        val gemGoalMove: Int,
        val lastGemMove: Int,
        val maxGemDrought: Int,
        val score: Int,
        val lines: Int,
        val goal: AtomBlocksEngine.Goal,
        val timedOut: Boolean,
    )

    @Test
    fun adventureCampaign() {
        val samples = (System.getenv("ATOM_BLOCKS_SIM_SAMPLES")?.toIntOrNull() ?: 16).coerceIn(1, 256)
        val seedOffset = System.getenv("ATOM_BLOCKS_SIM_SEED_OFFSET")?.toLongOrNull() ?: 0L
        val tag = (System.getenv("ATOM_BLOCKS_SIM_TAG") ?: "local").replace(Regex("[^A-Za-z0-9_.-]"), "_")
        val output = File("build/reports/atomblocks-balance/$tag").apply { mkdirs() }
        val start = System.nanoTime()
        val runs = ArrayList<Run>()
        for (level in 1..AtomBlocksEngine.ADVENTURE_LEVELS) {
            for (seedKind in listOf("ui", "variation")) {
                for (policy in listOf(Policy.RANDOM, Policy.CASUAL, Policy.GREEDY, Policy.PLANNER)) {
                    repeat(samples) { repetition ->
                        val seed = if (seedKind == "ui") 999L + level else variationSeed(level, repetition, seedOffset)
                        runs += simulate(level, seedKind, seed, policy, repetition, seedOffset)
                    }
                }
                // A small separate audit of the actual free hint, without treating it as a player.
                runs += simulate(level, seedKind,
                    if (seedKind == "ui") 999L + level else variationSeed(level, 0, seedOffset), Policy.HINT, 0, seedOffset)
            }
            println("Atom Blocks $tag: level=$level runs=${runs.size} elapsed_s=${(System.nanoTime() - start) / 1_000_000_000}")
        }
        writeRuns(File(output, "runs.csv"), runs)
        writeSummary(File(output, "summary.csv"), runs, includeSeedKind = true)
        writeSummary(File(output, "levels.csv"), runs, includeSeedKind = false)
        File(output, "protocol.txt").writeText("""
            samples_per_policy_and_seed_family=$samples
            seed_offset=$seedOffset
            ui_seed=999+level, fixed across repetitions; independent player RNG
            variation_seed=1000003+level*104729+repetition*10000019+seed_offset*1000000007
            policy_random=uniform among all legal placements
            policy_casual=12% uniform errors; otherwise stochastic selection among the top six immediate moves
            policy_greedy=best immediate line/gem reward plus light board geometry, random tie breaking
            policy_planner=beam search through the current tray, width six, future empty-space geometry
            policy_hint=actual engine.hint, one trajectory per level and seed family
            cap=120 placements or 180 actions; at most three actual free rescues at blockage
            strict=won before any rescue; aided=won with zero to three rescues on the same trajectory
            strict_moves=placements before first blockage, or final placements if never blocked
            no_progress=consecutive placements clearing no line and collecting no gem
            gem_drought=consecutive placements without collecting a gem while gems are still required
            score_goal_move/gem_goal_move/last_gem_move=-1 when not reached or not applicable
            early_block=first blockage at or before move ten
            median/p90=nearest-rank quantiles over won trajectories only, empty when there is no win
            note=Simulated policies are diagnostic probes, not estimates of human success rates.
            elapsed_seconds=${(System.nanoTime() - start) / 1_000_000_000}
        """.trimIndent() + "\n")
        assertEquals(36 * (8 * samples + 2), runs.size)
        assertTrue(runs.all { it.rescues in 0..3 && it.moves <= 120 })
        assertTrue(runs.all { !it.wonWithoutHelp || (it.won && it.rescues == 0) })
        println("Atom Blocks reports: ${output.absolutePath}")
    }

    private fun variationSeed(level: Int, repetition: Int, offset: Long) =
        1_000_003L + level * 104_729L + repetition * 10_000_019L + offset * 1_000_000_007L

    private fun simulate(level: Int, seedKind: String, seed: Long, policy: Policy, repetition: Int, seedOffset: Long): Run {
        val engine = AtomBlocksEngine(seed)
        engine.newGame(AtomBlocksEngine.Mode.ADVENTURE, level, seed)
        val playerSeed = 83_129L + level * 1_000_003L + repetition * 2_000_033L +
            policy.ordinal * 10_000_019L + seedOffset * 1_000_000_007L + if (seedKind == "ui") 0L else 91_019L
        val random = Random(playerSeed)
        var rescues = 0
        var firstBlock = -1
        var actions = 0
        var noProgress = 0
        var maxNoProgress = 0
        var scoreGoalMove = -1
        var gemGoalMove = -1
        var lastGemMove = -1
        var gemDrought = 0
        var maxGemDrought = 0
        while (!engine.won && engine.moves < 120 && actions < 180) {
            if (engine.blocked) {
                if (firstBlock < 0) firstBlock = engine.moves
                if (rescues == 3) break
                assertTrue(engine.rescue())
                rescues++
                actions++
                continue
            }
            val move = if (policy == Policy.HINT) engine.hint() else choose(engine, policy, random)
            assertNotNull("legal move level=$level seed=$seed policy=$policy move=${engine.moves}", move)
            val placement = placements.getValue(engine.tray[move!!.slot]!!.shapeId)
                .first { it.row == move.row && it.col == move.col }
            val filled = occupancy(engine.board) or placement.mask
            var expectedClear = 0L
            var expectedLines = 0
            for (line in 0..7) {
                if (filled and rows[line] == rows[line]) { expectedClear = expectedClear or rows[line]; expectedLines++ }
                if (filled and columns[line] == columns[line]) { expectedClear = expectedClear or columns[line]; expectedLines++ }
            }
            val expectedGems = occupancy(engine.gems) and expectedClear.inv()
            val result = engine.place(move.slot, move.row, move.col)
            assertNotNull("policy selected an illegal placement: $move", result)
            assertEquals("policy board geometry differs from engine", filled and expectedClear.inv(), occupancy(engine.board))
            assertEquals("policy gem geometry differs from engine", expectedGems, occupancy(engine.gems))
            assertEquals(expectedLines, result!!.lines)
            noProgress = if (result.lines == 0 && result.collectedGems == 0) noProgress + 1 else 0
            maxNoProgress = maxOf(maxNoProgress, noProgress)
            if (scoreGoalMove < 0 && engine.score >= engine.goal!!.score) scoreGoalMove = engine.moves
            if (engine.goal!!.gems > 0) {
                if (result.collectedGems > 0) lastGemMove = engine.moves
                if (gemGoalMove < 0 && engine.collectedGems >= engine.goal!!.gems) gemGoalMove = engine.moves
                gemDrought = if (result.collectedGems > 0 || gemGoalMove >= 0) 0 else gemDrought + 1
                maxGemDrought = maxOf(maxGemDrought, gemDrought)
            }
            actions++
        }
        if (engine.blocked && firstBlock < 0) firstBlock = engine.moves
        return Run(level, seedKind, seed, policy, repetition, playerSeed, engine.won,
            engine.won && rescues == 0, engine.moves, if (firstBlock < 0) engine.moves else firstBlock,
            rescues, firstBlock, maxNoProgress, engine.gems.count { it != 0 }, scoreGoalMove,
            gemGoalMove, lastGemMove, maxGemDrought, engine.score,
            engine.lines, engine.goal!!, !engine.won && (engine.moves >= 120 || actions >= 180))
    }

    private fun choose(engine: AtomBlocksEngine, policy: Policy, random: Random): AtomBlocksEngine.Move? {
        val occupied = occupancy(engine.board)
        val gems = occupancy(engine.gems)
        val remaining = engine.tray.indices.fold(0) { mask, slot ->
            if (engine.tray[slot] != null) mask or (1 shl slot) else mask
        }
        val options = candidates(occupied, gems, remaining, engine.tray)
        if (options.isEmpty()) return null
        if (policy == Policy.RANDOM || (policy == Policy.CASUAL && random.nextDouble() < 0.12)) {
            return options[random.nextInt(options.size)].move
        }
        if (policy == Policy.PLANNER) return plannedMove(options, engine.tray, random)
        val ranked = options.map { it to (it.reward + it.quality * 0.18 + random.nextDouble() * 0.01) }
            .sortedByDescending { it.second }.map { it.first }
        if (policy == Policy.GREEDY) return ranked.first().move
        // Deliberately fallible: 45%, 25%, 15%, 8%, 4%, 3% among six plausible moves.
        val ticket = random.nextInt(100)
        val rank = when { ticket < 45 -> 0; ticket < 70 -> 1; ticket < 85 -> 2; ticket < 93 -> 3; ticket < 97 -> 4; else -> 5 }
        return ranked[minOf(rank, ranked.lastIndex)].move
    }

    private fun plannedMove(options: List<Candidate>, tray: List<AtomBlocksEngine.Piece?>, random: Random): AtomBlocksEngine.Move {
        var beam = options.map { it to (it.reward + it.quality + random.nextDouble() * 0.01) }
            .sortedByDescending { it.second }.take(6)
            .map { (it, _) -> Path(it.move, it.occupied, it.gems, it.remaining, it.reward, it.quality) }
        val complete = ArrayList<Path>()
        repeat(3) {
            val next = ArrayList<Path>()
            for (path in beam) {
                if (path.remaining == 0) {
                    complete += path
                    continue
                }
                for (candidate in candidates(path.occupied, path.gems, path.remaining, tray)) {
                    next += Path(path.first, candidate.occupied, candidate.gems, candidate.remaining,
                        path.reward + candidate.reward, candidate.quality)
                }
            }
            if (next.isEmpty()) {
                return (complete.maxByOrNull { it.reward + it.quality } ?: beam.maxBy { it.reward + it.quality }).first
            }
            beam = next.map { it to (it.reward + it.quality + random.nextDouble() * 0.01) }
                .sortedByDescending { it.second }.take(6).map { it.first }
        }
        return (complete + beam).maxBy { it.reward + it.quality }.first
    }

    /** Bitboards are only a policy evaluator. Production engine remains the transition oracle. */
    private fun candidates(occupied: Long, gems: Long, remaining: Int, tray: List<AtomBlocksEngine.Piece?>): List<Candidate> {
        val result = ArrayList<Candidate>(100)
        for (slot in 0..2) {
            if (remaining and (1 shl slot) == 0) continue
            val piece = tray[slot] ?: continue
            for (placement in placements.getValue(piece.shapeId)) {
                if (occupied and placement.mask != 0L) continue
                val filled = occupied or placement.mask
                var clear = 0L
                var lines = 0
                for (line in 0..7) {
                    if (filled and rows[line] == rows[line]) { clear = clear or rows[line]; lines++ }
                    if (filled and columns[line] == columns[line]) { clear = clear or columns[line]; lines++ }
                }
                val after = filled and clear.inv()
                val remainingGems = gems and clear.inv()
                val collected = java.lang.Long.bitCount(gems and clear)
                val reward = lines * lines * 140.0 + collected * 420.0 + placement.size * 1.5 +
                    if (after == 0L && lines > 0) 70.0 else 0.0
                result += Candidate(AtomBlocksEngine.Move(slot, placement.row, placement.col), after,
                    remainingGems, remaining and (1 shl slot).inv(), reward, quality(after, remainingGems))
            }
        }
        return result
    }

    private fun quality(occupied: Long, gems: Long): Double {
        val empty = occupied.inv()
        val neighbors = ((empty and notRight) shl 1) or ((empty and notLeft) ushr 1) or
            (empty shl 8) or (empty ushr 8)
        val isolated = java.lang.Long.bitCount(empty and neighbors.inv())
        val squares2 = empty and (empty ushr 1) and (empty ushr 8) and (empty ushr 9) and square2Starts
        val squares3 = squares2 and (squares2 ushr 1) and (squares2 ushr 8) and (squares2 ushr 9) and square3Starts
        var linePotential = 0.0
        var gemPotential = 0.0
        for (line in 0..7) {
            val row = java.lang.Long.bitCount(occupied and rows[line])
            val col = java.lang.Long.bitCount(occupied and columns[line])
            linePotential += row * row + col * col
            if (gems and rows[line] != 0L) gemPotential += row * row
            if (gems and columns[line] != 0L) gemPotential += col * col
        }
        return -java.lang.Long.bitCount(occupied) * 3.5 - isolated * 22.0 +
            java.lang.Long.bitCount(squares2) * 2.0 + java.lang.Long.bitCount(squares3) * 3.0 +
            linePotential * 0.20 + gemPotential * 0.38
    }

    private fun occupancy(cells: IntArray): Long {
        var result = 0L
        for (index in cells.indices) if (cells[index] != 0) result = result or (1L shl index)
        return result
    }

    private fun writeRuns(file: File, runs: List<Run>) = file.bufferedWriter().use { writer ->
        writer.appendLine("level,seed_kind,seed,player,rep,player_seed,won,won_without_help,moves,strict_moves,rescues,first_block_move,max_no_progress,remaining_gems,score_goal_move,gem_goal_move,last_gem_move,max_gem_drought,score,lines,goal_score,goal_lines,goal_gems,timeout")
        for (run in runs) writer.appendLine(listOf(run.level, run.seedKind, run.seed, run.policy.name.lowercase(),
            run.repetition, run.playerSeed, run.won, run.wonWithoutHelp, run.moves, run.strictMoves, run.rescues,
            run.firstBlockMove, run.maxNoProgress, run.remainingGems, run.scoreGoalMove, run.gemGoalMove,
            run.lastGemMove, run.maxGemDrought, run.score, run.lines,
            run.goal.score, run.goal.lines, run.goal.gems, run.timedOut).joinToString(","))
    }

    private fun writeSummary(file: File, runs: List<Run>, includeSeedKind: Boolean) = file.bufferedWriter().use { writer ->
        writer.appendLine("level,seed_kind,player,runs,strict_win_pct,aided_win_pct,median_win_moves,p90_win_moves,mean_rescues,early_block_pct,timeout_pct,mean_remaining_gems,p90_no_progress,p90_gem_drought,goal_score,goal_lines,goal_gems")
        val grouped = runs.groupBy { Triple(it.level, if (includeSeedKind) it.seedKind else "all", it.policy) }
        for ((key, group) in grouped) {
            val wins = group.filter { it.won }.map { it.moves }.sorted()
            val goal = group.first().goal
            writer.appendLine(listOf(key.first, key.second, key.third.name.lowercase(), group.size,
                percent(group.count { it.wonWithoutHelp }, group.size), percent(wins.size, group.size),
                quantile(wins, 0.5), quantile(wins, 0.9), decimal(group.map { it.rescues }.average()),
                percent(group.count { it.firstBlockMove in 0..10 }, group.size),
                percent(group.count { it.timedOut }, group.size), decimal(group.map { it.remainingGems }.average()),
                quantile(group.map { it.maxNoProgress }.sorted(), 0.9),
                quantile(group.map { it.maxGemDrought }.sorted(), 0.9), goal.score, goal.lines, goal.gems).joinToString(","))
        }
    }

    private fun percent(count: Int, total: Int) = decimal(100.0 * count / total)
    private fun decimal(value: Double) = String.format(Locale.ROOT, "%.2f", value)
    private fun quantile(sorted: List<Int>, fraction: Double): String =
        if (sorted.isEmpty()) "" else sorted[(ceil(sorted.size * fraction).toInt() - 1).coerceIn(sorted.indices)].toString()

    companion object {
        private val rows = LongArray(8) { 255L shl (it * 8) }
        private val columns = LongArray(8) { col -> (0..7).fold(0L) { mask, row -> mask or (1L shl (row * 8 + col)) } }
        private val notLeft = columns[0].inv()
        private val notRight = columns[7].inv()
        private val square2Starts = (0..6).fold(0L) { mask, row -> mask or (127L shl (row * 8)) }
        private val square3Starts = (0..5).fold(0L) { mask, row -> mask or (63L shl (row * 8)) }
        private val placements = AtomBlocksEngine.SHAPES.associate { shape -> shape.id to buildList {
            for (row in 0..8 - shape.height) for (col in 0..8 - shape.width) {
                val mask = shape.cells.fold(0L) { value, cell -> value or (1L shl ((row + cell.row) * 8 + col + cell.col)) }
                add(Placement(row, col, mask, shape.cells.size))
            }
        } }
    }
}
