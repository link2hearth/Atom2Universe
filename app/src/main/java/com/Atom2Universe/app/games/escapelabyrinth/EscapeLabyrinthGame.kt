package com.Atom2Universe.app.games.escapelabyrinth

import kotlin.math.*
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
//  Tile & direction enums
// ─────────────────────────────────────────────────────────────────────────────

enum class TileType { WALL, FLOOR, START, EXIT, BONUS }

enum class Dir {
    N, S, W, E;
    val dr: Int get() = when (this) { N -> -1; S -> 1; else -> 0 }
    val dc: Int get() = when (this) { W -> -1; E -> 1; else -> 0 }
    companion object {
        fun between(fr: Int, fc: Int, tr: Int, tc: Int): Dir = when {
            tr < fr -> N; tr > fr -> S; tc < fc -> W; else -> E
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Core data types
// ─────────────────────────────────────────────────────────────────────────────

fun cellKey(r: Int, c: Int) = "$r,$c"
fun String.asCell(): Pair<Int, Int> {
    val i = indexOf(',')
    return substring(0, i).toInt() to substring(i + 1).toInt()
}

data class GuardStep(val row: Int, val col: Int, val dir: Dir)

/** A single vision ray from a guard position toward a target cell. */
data class VisionRay(
    val targetRow: Int, val targetCol: Int,
    val pathTileRows: IntArray, val pathTileCols: IntArray  // tile-space intermediate points
)

data class Guard(
    val id: Int,
    val path: List<GuardStep>,
    val visionRange: Int,
    val halfAngle: Float,                   // half opening angle in degrees
    val templates: List<List<VisionRay>>,   // one list per patrol step
    val visionUnion: Set<String>            // union of all visible cell keys
)

data class BonusOrb(val id: Int, val row: Int, val col: Int) {
    fun key() = cellKey(row, col)
}

data class GuardPhaseState(
    val row: Int, val col: Int, val dir: Dir,
    val nextRow: Int, val nextCol: Int
)

// Level is immutable after construction
class Level(
    val seed: Long,
    val difficulty: Difficulty,
    val cellW: Int,
    val cellH: Int,
    val grid: Array<Array<TileType>>,           // [tileRow][tileCol]
    val adj: Array<Array<Set<String>>>,          // [row][col] → neighbour keys
    val start: Pair<Int, Int>,
    val exit: Pair<Int, Int>,
    val bonuses: List<BonusOrb>,
    val guards: List<Guard>,
    val guardCycle: Int,
    val guardStates: List<List<GuardPhaseState>>,
    val bonusByCell: Map<String, BonusOrb>,
    val solveTurns: Int
) {
    val tileW get() = cellW * 2 - 1
    val tileH get() = cellH * 2 - 1
}

// ─────────────────────────────────────────────────────────────────────────────
//  Difficulty presets
// ─────────────────────────────────────────────────────────────────────────────

enum class Difficulty(
    val labelEn: String,
    val labelFr: String,
    val sizeMin: Int, val sizeMax: Int,
    val cycleMin: Float, val cycleMax: Float,
    val patrolMin: Int, val patrolMax: Int,
    val visionRange: Int, val halfAngle: Float,
    val bonusMin: Int, val bonusMax: Int
) {
    EASY  ("Easy",   "Facile",    8,  11, 0.06f, 0.10f, 1, 2, 3, 45f, 2, 3),
    MEDIUM("Medium", "Moyen",    11,  13, 0.09f, 0.14f, 2, 3, 4, 52f, 3, 5),
    HARD  ("Hard",   "Difficile",13,  16, 0.12f, 0.18f, 3, 4, 5, 60f, 4, 6)
}

// ─────────────────────────────────────────────────────────────────────────────
//  Play state
// ─────────────────────────────────────────────────────────────────────────────

data class PlayState(
    val row: Int,
    val col: Int,
    val guardPhase: Int,
    val collectedIds: Set<Int>,
    val turn: Int,
    val completed: Boolean = false,
    val caught: Boolean = false
)

enum class MoveOutcome { OK, WALL, BLOCKED_BY_GUARD, IN_VISION, MISSING_ORBS, WIN }

data class ActionResult(val outcome: MoveOutcome, val newState: PlayState? = null)

// ─────────────────────────────────────────────────────────────────────────────
//  Main game object
// ─────────────────────────────────────────────────────────────────────────────

object EscapeLabyrinthGame {

    private const val MAX_GEN_ATTEMPTS = 40
    private const val MAX_PATROL_LEN = 22
    private const val MAX_PATROL_TRIES = 100
    private const val MAX_SOLVE_TURNS = 150
    private const val MAX_SOLVE_STATES = 100_000

    // ── Public API ───────────────────────────────────────────────────────────

    fun create(seed: Long, diff: Difficulty): Level? {
        for (attempt in 0 until MAX_GEN_ATTEMPTS) {
            try {
                val rng = Random(seed + attempt.toLong() * 0x9e3779b7L)
                return generate(seed, diff, rng) ?: continue
            } catch (_: Exception) {}
        }
        return null
    }

    fun initialPlay(level: Level) = PlayState(
        row = level.start.first,
        col = level.start.second,
        guardPhase = 0,
        collectedIds = emptySet(),
        turn = 0
    )

    /** Try to move the player by (dr, dc). Returns result + new state. */
    fun move(level: Level, play: PlayState, dr: Int, dc: Int): ActionResult {
        if (play.completed || play.caught) return ActionResult(MoveOutcome.OK, play)
        val tr = play.row + dr
        val tc = play.col + dc

        if (tr !in 0 until level.cellH || tc !in 0 until level.cellW)
            return ActionResult(MoveOutcome.WALL, play)
        if (!level.adj[play.row][play.col].contains(cellKey(tr, tc)))
            return ActionResult(MoveOutcome.WALL, play)

        val nextPhase = (play.guardPhase + 1) % level.guardCycle
        val curStates = level.guardStates[play.guardPhase]
        val nxtStates = level.guardStates[nextPhase]

        // Body-collision check
        for (i in nxtStates.indices) {
            val nxt = nxtStates[i]
            if (nxt.row == tr && nxt.col == tc) {
                val caught = play.copy(guardPhase = nextPhase, turn = play.turn + 1, caught = true)
                return ActionResult(MoveOutcome.BLOCKED_BY_GUARD, caught)
            }
            val prv = curStates.getOrNull(i)
            if (prv != null && prv.row == tr && prv.col == tc &&
                nxt.row == play.row && nxt.col == play.col) {
                val caught = play.copy(guardPhase = nextPhase, turn = play.turn + 1, caught = true)
                return ActionResult(MoveOutcome.BLOCKED_BY_GUARD, caught)
            }
        }

        // Vision check (after guard advances)
        if (guardVisionAt(level, nextPhase).contains(cellKey(tr, tc))) {
            val caught = play.copy(guardPhase = nextPhase, turn = play.turn + 1, caught = true)
            return ActionResult(MoveOutcome.IN_VISION, caught)
        }

        // Collect orb if present
        val collected = play.collectedIds.toMutableSet()
        level.bonusByCell[cellKey(tr, tc)]?.let { collected.add(it.id) }

        // Exit check
        if (tr == level.exit.first && tc == level.exit.second) {
            if (collected.size < level.bonuses.size)
                return ActionResult(MoveOutcome.MISSING_ORBS,
                    play.copy(row = tr, col = tc, guardPhase = nextPhase,
                        collectedIds = collected, turn = play.turn + 1))
            val won = play.copy(row = tr, col = tc, guardPhase = nextPhase,
                collectedIds = collected, turn = play.turn + 1, completed = true)
            return ActionResult(MoveOutcome.WIN, won)
        }

        return ActionResult(MoveOutcome.OK,
            play.copy(row = tr, col = tc, guardPhase = nextPhase,
                collectedIds = collected, turn = play.turn + 1))
    }

    /** Skip a turn (guard advances, player stays). */
    fun wait(level: Level, play: PlayState): PlayState {
        if (play.completed || play.caught) return play
        val nextPhase = (play.guardPhase + 1) % level.guardCycle

        // Check if guard moved onto player
        val nxtStates = level.guardStates[nextPhase]
        for (g in nxtStates) {
            if (g.row == play.row && g.col == play.col) {
                return play.copy(guardPhase = nextPhase, turn = play.turn + 1, caught = true)
            }
        }
        // Check if player is now in vision
        if (guardVisionAt(level, nextPhase).contains(cellKey(play.row, play.col))) {
            return play.copy(guardPhase = nextPhase, turn = play.turn + 1, caught = true)
        }

        return play.copy(guardPhase = nextPhase, turn = play.turn + 1)
    }

    /** Return the set of cell keys visible by all guards at a given phase. */
    fun guardVisionAt(level: Level, phase: Int): Set<String> {
        val result = mutableSetOf<String>()
        for (guard in level.guards) {
            val step = phase % guard.path.size
            val tmpl = guard.templates.getOrNull(step) ?: continue
            for (ray in tmpl) {
                val blocked = (0 until ray.pathTileRows.size).any { i ->
                    val tr = ray.pathTileRows[i]; val tc = ray.pathTileCols[i]
                    tr < 0 || tr >= level.tileH || tc < 0 || tc >= level.tileW ||
                    level.grid[tr][tc] == TileType.WALL
                }
                if (!blocked) result.add(cellKey(ray.targetRow, ray.targetCol))
            }
        }
        return result
    }

    // ── Level generation ─────────────────────────────────────────────────────

    private fun generate(seed: Long, diff: Difficulty, rng: Random): Level? {
        val size = diff.sizeMin + rng.nextInt(diff.sizeMax - diff.sizeMin + 1)

        // Build tile grid and adjacency
        val grid = Array(size * 2 - 1) { Array(size * 2 - 1) { TileType.WALL } }
        val adjMut = Array(size) { Array(size) { mutableSetOf<String>() } }
        carveMaze(size, size, grid, adjMut, rng)

        // Add loops
        val ratio = diff.cycleMin + rng.nextFloat() * (diff.cycleMax - diff.cycleMin)
        addCycles(size, size, grid, adjMut, (size * size * ratio).toInt(), rng)

        // Upcast to immutable (MutableSet IS a Set)
        @Suppress("UNCHECKED_CAST")
        val adj = adjMut as Array<Array<Set<String>>>

        // Diameter endpoints → start & exit
        val (start, exit) = mazeDiameter(size, adj)
        grid[start.first * 2][start.second * 2] = TileType.START
        grid[exit.first * 2][exit.second * 2] = TileType.EXIT

        val distFromStart = bfsDistances(size, adj, start)

        // Guards
        val rawGuards = generateGuards(size, adj, diff, start, exit, rng)
        val guards = rawGuards.map { buildVisionTemplates(it, size, grid) }

        // Bonus orbs
        val bonuses = generateBonuses(size, adj, diff, start, exit, guards, distFromStart, rng)
        bonuses.forEach { grid[it.row * 2][it.col * 2] = TileType.BONUS }

        // Guard cycle + phase states
        val cycle = lcmList(guards.map { it.path.size }.ifEmpty { listOf(1) })
        val phases = List(cycle) { phase ->
            guards.map { g ->
                val step = phase % g.path.size
                val seg = g.path[step]; val nxt = g.path[(step + 1) % g.path.size]
                GuardPhaseState(seg.row, seg.col, seg.dir, nxt.row, nxt.col)
            }
        }
        val bonusByCell = bonuses.associateBy { it.key() }

        val level = Level(seed, diff, size, size, grid, adj, start, exit,
            bonuses, guards, cycle, phases, bonusByCell, 0)

        val solveTurns = validateLevel(level) ?: return null
        return Level(seed, diff, size, size, grid, adj, start, exit,
            bonuses, guards, cycle, phases, bonusByCell, solveTurns)
    }

    // ── Maze carving (randomized DFS) ────────────────────────────────────────

    private fun carveMaze(
        w: Int, h: Int,
        grid: Array<Array<TileType>>,
        adj: Array<Array<MutableSet<String>>>,
        rng: Random
    ) {
        val visited = Array(h) { BooleanArray(w) }
        val stack = ArrayDeque<Pair<Int, Int>>()
        val sr = rng.nextInt(h); val sc = rng.nextInt(w)
        stack.addLast(sr to sc); visited[sr][sc] = true
        grid[sr * 2][sc * 2] = TileType.FLOOR

        val dirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        while (stack.isNotEmpty()) {
            val (cr, cc) = stack.last()
            val nbrs = dirs.filter { (dr, dc) ->
                val nr = cr + dr; val nc = cc + dc
                nr in 0 until h && nc in 0 until w && !visited[nr][nc]
            }.toMutableList()
            if (nbrs.isEmpty()) { stack.removeLast(); continue }
            nbrs.shuffle(rng)
            val (dr, dc) = nbrs.first()
            val nr = cr + dr; val nc = cc + dc
            grid[cr * 2 + dr][cc * 2 + dc] = TileType.FLOOR
            grid[nr * 2][nc * 2] = TileType.FLOOR
            adj[cr][cc].add(cellKey(nr, nc))
            adj[nr][nc].add(cellKey(cr, cc))
            visited[nr][nc] = true
            stack.addLast(nr to nc)
        }
    }

    // ── Add extra connections (create loops) ─────────────────────────────────

    private fun addCycles(
        w: Int, h: Int,
        grid: Array<Array<TileType>>,
        adj: Array<Array<MutableSet<String>>>,
        count: Int, rng: Random
    ) {
        if (count <= 0) return
        data class C(val r: Int, val c: Int, val nr: Int, val nc: Int, val wr: Int, val wc: Int)
        val cands = mutableListOf<C>()
        for (r in 0 until h) for (c in 0 until w) {
            if (r + 1 < h && !adj[r][c].contains(cellKey(r + 1, c)))
                cands.add(C(r, c, r + 1, c, r * 2 + 1, c * 2))
            if (c + 1 < w && !adj[r][c].contains(cellKey(r, c + 1)))
                cands.add(C(r, c, r, c + 1, r * 2, c * 2 + 1))
        }
        cands.shuffle(rng)
        var added = 0
        for (cand in cands) {
            if (added >= count) break
            if (adj[cand.r][cand.c].contains(cellKey(cand.nr, cand.nc))) continue
            adj[cand.r][cand.c].add(cellKey(cand.nr, cand.nc))
            adj[cand.nr][cand.nc].add(cellKey(cand.r, cand.c))
            grid[cand.wr][cand.wc] = TileType.FLOOR
            added++
        }
    }

    // ── BFS helpers ──────────────────────────────────────────────────────────

    private fun bfsDistances(
        size: Int,
        adj: Array<Array<Set<String>>>,
        start: Pair<Int, Int>
    ): Array<IntArray> {
        val dist = Array(size) { IntArray(size) { -1 } }
        val queue = ArrayDeque<Pair<Int, Int>>()
        dist[start.first][start.second] = 0
        queue.addLast(start)
        while (queue.isNotEmpty()) {
            val (r, c) = queue.removeFirst()
            for (key in adj[r][c]) {
                val (nr, nc) = key.asCell()
                if (dist[nr][nc] == -1) {
                    dist[nr][nc] = dist[r][c] + 1
                    queue.addLast(nr to nc)
                }
            }
        }
        return dist
    }

    private fun bfsFarthest(
        size: Int,
        adj: Array<Array<Set<String>>>,
        start: Pair<Int, Int>
    ): Pair<Pair<Int, Int>, Array<IntArray>> {
        val dist = bfsDistances(size, adj, start)
        var best = start; var bestD = 0
        for (r in 0 until size) for (c in 0 until size) {
            if (dist[r][c] > bestD) { bestD = dist[r][c]; best = r to c }
        }
        return best to dist
    }

    private fun mazeDiameter(
        size: Int,
        adj: Array<Array<Set<String>>>
    ): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        val (far1, _) = bfsFarthest(size, adj, 0 to 0)
        val (far2, _) = bfsFarthest(size, adj, far1)
        return far1 to far2
    }

    // ── Guard generation ─────────────────────────────────────────────────────

    private fun generateGuards(
        size: Int,
        adj: Array<Array<Set<String>>>,
        diff: Difficulty,
        start: Pair<Int, Int>,
        exit: Pair<Int, Int>,
        rng: Random
    ): List<Guard> {
        val forbidden = mutableSetOf(start.let { cellKey(it.first, it.second) },
            exit.let { cellKey(it.first, it.second) })
        val count = diff.patrolMin + rng.nextInt(diff.patrolMax - diff.patrolMin + 1)
        val occupied = mutableSetOf<String>()
        val guards = mutableListOf<Guard>()

        val candidates = (0 until size).flatMap { r ->
            (0 until size).filter { c -> !forbidden.contains(cellKey(r, c)) && adj[r][c].size >= 2 }
                .map { c -> r to c }
        }.toMutableList()
        candidates.shuffle(rng)

        var tries = 0; var idx = 0
        while (guards.size < count && tries < MAX_PATROL_TRIES && idx < candidates.size) {
            tries++
            val startCell = candidates[idx++]
            if (occupied.contains(cellKey(startCell.first, startCell.second))) continue
            val cycle = buildPatrolCycle(startCell, adj, forbidden, occupied, rng) ?: continue
            if (cycle.size < 4) continue
            cycle.forEach { (r, c) -> occupied.add(cellKey(r, c)) }
            val path = cycle.mapIndexed { i, (r, c) ->
                val (nr, nc) = cycle[(i + 1) % cycle.size]
                GuardStep(r, c, Dir.between(r, c, nr, nc))
            }
            guards.add(Guard(guards.size, path, diff.visionRange, diff.halfAngle,
                emptyList(), emptySet()))
        }
        return guards
    }

    private fun buildPatrolCycle(
        start: Pair<Int, Int>,
        adj: Array<Array<Set<String>>>,
        forbidden: Set<String>,
        occupied: Set<String>,
        rng: Random
    ): List<Pair<Int, Int>>? {
        val stack = ArrayDeque<Pair<Pair<Int, Int>, Pair<Int, Int>?>>() // (current, prev)
        val visited = mutableSetOf(cellKey(start.first, start.second))
        stack.addLast(start to null)
        var iters = 0
        while (stack.isNotEmpty() && stack.size <= MAX_PATROL_LEN) {
            if (++iters > 3000) break
            val (cur, prev) = stack.last()
            val (cr, cc) = cur
            val nbrs = adj[cr][cc]
                .map { it.asCell().let { (r, c) -> r to c } }
                .filter { n -> !forbidden.contains(cellKey(n.first, n.second))
                    && !occupied.contains(cellKey(n.first, n.second))
                    && n != prev }
                .toMutableList()
            nbrs.shuffle(rng)
            // Close the loop?
            if (stack.size >= 4 && nbrs.any { it == start })
                return stack.map { it.first }
            val next = nbrs.firstOrNull { !visited.contains(cellKey(it.first, it.second)) }
            if (next == null) {
                visited.remove(cellKey(cur.first, cur.second))
                stack.removeLast(); continue
            }
            visited.add(cellKey(next.first, next.second))
            stack.addLast(next to cur)
        }
        return null
    }

    // ── Vision template construction ─────────────────────────────────────────

    private fun buildVisionTemplates(guard: Guard, size: Int, grid: Array<Array<TileType>>): Guard {
        val tH = size * 2 - 1; val tW = size * 2 - 1
        val templates = guard.path.map { seg ->
            val dirX = seg.dir.dc.toFloat(); val dirY = seg.dir.dr.toFloat()
            val rays = mutableListOf<VisionRay>()
            for (dy in -guard.visionRange..guard.visionRange) {
                for (dx in -guard.visionRange..guard.visionRange) {
                    if (dx == 0 && dy == 0) continue
                    if (max(abs(dx), abs(dy)) > guard.visionRange) continue
                    val angle = angleDeg(dirX, dirY, dx.toFloat(), dy.toFloat())
                    if (angle > guard.halfAngle) continue
                    val tr = seg.row + dy; val tc = seg.col + dx
                    if (tr !in 0 until size || tc !in 0 until size) continue
                    val tilePath = bresenhamTiles(seg.row, seg.col, tr, tc)
                    rays.add(VisionRay(tr, tc,
                        tilePath.map { it.first }.toIntArray(),
                        tilePath.map { it.second }.toIntArray()))
                }
            }
            rays.sortBy { max(abs(it.targetRow - seg.row), abs(it.targetCol - seg.col)) }
            rays as List<VisionRay>
        }
        val union = mutableSetOf<String>()
        templates.forEach { tmpl -> tmpl.forEach { ray -> union.add(cellKey(ray.targetRow, ray.targetCol)) } }
        return guard.copy(templates = templates, visionUnion = union)
    }

    private fun angleDeg(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dot = ax * bx + ay * by
        val magA = sqrt(ax * ax + ay * ay).coerceAtLeast(1e-6f)
        val magB = sqrt(bx * bx + by * by).coerceAtLeast(1e-6f)
        return acos((dot / (magA * magB)).coerceIn(-1f, 1f)) * (180f / PI.toFloat())
    }

    /** Bresenham line in tile-space. Returns list of (tileRow, tileCol), excluding start. */
    private fun bresenhamTiles(r0: Int, c0: Int, r1: Int, c1: Int): List<Pair<Int, Int>> {
        var x = c0 * 2; var y = r0 * 2
        val x1 = c1 * 2; val y1 = r1 * 2
        val dx = abs(x1 - x); val dy = -abs(y1 - y)
        val sx = if (x < x1) 1 else -1; val sy = if (y < y1) 1 else -1
        var err = dx + dy
        val result = mutableListOf<Pair<Int, Int>>()
        while (true) {
            if (!(x == c0 * 2 && y == r0 * 2)) result.add(y to x)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
        return result
    }

    // ── Bonus orb placement ──────────────────────────────────────────────────

    private fun generateBonuses(
        size: Int,
        adj: Array<Array<Set<String>>>,
        diff: Difficulty,
        start: Pair<Int, Int>,
        exit: Pair<Int, Int>,
        guards: List<Guard>,
        distFromStart: Array<IntArray>,
        rng: Random
    ): List<BonusOrb> {
        val count = diff.bonusMin + rng.nextInt(diff.bonusMax - diff.bonusMin + 1)
        val occupied = mutableSetOf(
            cellKey(start.first, start.second),
            cellKey(exit.first, exit.second)
        )
        guards.forEach { g -> g.path.forEach { s -> occupied.add(cellKey(s.row, s.col)) } }

        val deadEnds = mutableListOf<Pair<Int, Int>>()
        val corridors = mutableListOf<Pair<Int, Int>>()
        for (r in 0 until size) for (c in 0 until size) {
            if (occupied.contains(cellKey(r, c))) continue
            val dist = distFromStart[r][c]; if (dist < 2) continue
            if (adj[r][c].size == 1) deadEnds.add(r to c)
            else corridors.add(r to c)
        }
        deadEnds.shuffle(rng); corridors.shuffle(rng)

        val bonuses = mutableListOf<BonusOrb>()
        val pool = (deadEnds + corridors).iterator()
        while (bonuses.size < count && pool.hasNext()) {
            val (r, c) = pool.next()
            if (!occupied.contains(cellKey(r, c))) {
                occupied.add(cellKey(r, c))
                bonuses.add(BonusOrb(bonuses.size, r, c))
            }
        }
        return bonuses
    }

    // ── BFS validation (confirms level is solvable) ──────────────────────────

    private fun validateLevel(level: Level): Int? {
        val allMask = (1 shl level.bonuses.size) - 1
        val sr = level.start.first; val sc = level.start.second

        data class St(val r: Int, val c: Int, val ph: Int, val mask: Int, val turns: Int)

        val visited = mutableSetOf("$sr,$sc|0|0")
        val queue = ArrayDeque<St>()
        queue.addLast(St(sr, sc, 0, 0, 0))
        val bonusIdx = level.bonuses.associateBy({ it.key() }, { it.id })

        while (queue.isNotEmpty()) {
            val s = queue.removeFirst()
            if (s.turns > MAX_SOLVE_TURNS) continue
            if (visited.size > MAX_SOLVE_STATES) return null
            if (s.r == level.exit.first && s.c == level.exit.second && s.mask == allMask) return s.turns

            val nextPh = (s.ph + 1) % level.guardCycle
            val curGs = level.guardStates[s.ph]
            val nxtGs = level.guardStates[nextPh]
            val vision = guardVisionAt(level, nextPh)

            for ((dr, dc) in arrayOf(0 to 0, -1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val tr = s.r + dr; val tc = s.c + dc
                if (dr == 0 && dc == 0) {
                    // wait — check guard doesn't step on player
                    var coll = false
                    for (g in nxtGs) if (g.row == s.r && g.col == s.c) { coll = true; break }
                    if (coll) continue
                    if (vision.contains(cellKey(s.r, s.c))) continue
                } else {
                    if (tr !in 0 until level.cellH || tc !in 0 until level.cellW) continue
                    if (!level.adj[s.r][s.c].contains(cellKey(tr, tc))) continue
                    var coll = false
                    for (i in nxtGs.indices) {
                        val nxt = nxtGs[i]; val prv = curGs.getOrNull(i)
                        if (nxt.row == tr && nxt.col == tc) { coll = true; break }
                        if (prv != null && prv.row == tr && prv.col == tc &&
                            nxt.row == s.r && nxt.col == s.c) { coll = true; break }
                    }
                    if (coll) continue
                    if (vision.contains(cellKey(tr, tc))) continue
                }
                val fr = if (dr == 0 && dc == 0) s.r else tr
                val fc = if (dr == 0 && dc == 0) s.c else tc
                val newMask = bonusIdx[cellKey(fr, fc)]?.let { s.mask or (1 shl it) } ?: s.mask
                val stKey = "$fr,$fc|$nextPh|$newMask"
                if (!visited.contains(stKey)) {
                    visited.add(stKey)
                    queue.addLast(St(fr, fc, nextPh, newMask, s.turns + 1))
                }
            }
        }
        return null
    }

    // ── Math helpers ─────────────────────────────────────────────────────────

    private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)
    private fun lcm(a: Int, b: Int) = if (a == 0 || b == 0) 1 else a / gcd(a, b) * b
    private fun lcmList(v: List<Int>) = v.fold(1) { acc, x -> lcm(acc, x) }
}
