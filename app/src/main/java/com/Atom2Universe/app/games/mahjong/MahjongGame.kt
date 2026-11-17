package com.Atom2Universe.app.games.mahjong

import kotlin.math.abs
import kotlin.random.Random

/** Each mode changes the legal decisions, not just the decoration. */
enum class MahjongMode { CLASSIC, TRIPLE, CONNECT, CASCADE, SLIDE, TEN }

/**
 * Solitaire et Triples comptent en demi-tuiles (une tuile couvre 2 × 2 demi-cases, z = étage) ;
 * les autres modes sur une grille simple.
 */
data class MahjongTile(val id: Int, var x: Int, var y: Int, var z: Int, var face: Int, var removed: Boolean = false)
data class MahjongCell(val x: Int, val y: Int)
data class MahjongSlide(val id: Int, val x: Int, val y: Int)
internal data class MahjongSolutionStep(val ids: List<Int> = emptyList(), val slide: MahjongSlide? = null)
data class MahjongSnapshot(
    val tiles: List<MahjongTile>, val tray: List<Int>,
    val score: Int, val combo: Int, val matches: Int, val moves: Int, val assists: Int, val peakCombo: Int = 0,
    val focusId: Int? = null, val focusOrigin: MahjongCell? = null
)

class MahjongGame(val mode: MahjongMode, val seed: Int, val stage: Int = 1, initializeBoard: Boolean = true) {
    enum class Result { SELECTED, MATCHED, MOVED, MISMATCH, BLOCKED, TRAY_FULL, IGNORED }
    companion object {
        const val HINTS = 3
        const val SHUFFLES = 2
        const val TRAY = 7
        const val MAX_CHAIN = 8
        private val starShares = floatArrayOf(.15f, .40f, .70f)
    }
    val tiles = mutableListOf<MahjongTile>()
    val tray = mutableListOf<Int>()
    val history = mutableListOf<MahjongSnapshot>()
    var selected: Int? = null
        private set
    var focusOrigin: MahjongCell? = null
        private set
    var score = 0
        private set
    /** Multiplicateur de la chaîne en cours (0 quand elle est éteinte). */
    var combo = 0
        private set
    var matches = 0
        private set
    var moves = 0
        private set
    var assists = 0
        private set
    var peakCombo = 0
        private set
    /** Indices et mélanges sont comptés par plateau : annuler ne les rend jamais. */
    var hintsUsed = 0
        internal set
    var shufflesUsed = 0
        internal set
    /** Part restante de la fenêtre de chaîne (1 = pleine). Jamais sauvegardée. */
    var chainLeft = 0f
        private set
    var lastMatched: List<Int> = emptyList()
        private set
    var lastPath: List<MahjongCell> = emptyList()
        private set
    /** Complete construction witnesses, including the necessary slides. */
    internal val solution = mutableListOf<List<Int>>()
    internal val solutionSteps = mutableListOf<MahjongSolutionStep>()
    internal var generation: MahjongProceduralGenerator.Report? = null
    private var iceHintState: MahjongSnapshot? = null
    private var iceHint: Pair<List<Int>, MahjongSlide?> = emptyList<Int>() to null
    private var puzzleHintState: MahjongSnapshot? = null
    private var puzzleHint: List<Int> = emptyList()
    var hintSearchLimited = false
        private set
    /** La recherche a prouvé qu'aucune fin n'existe depuis ce plateau. */
    var hintProvedLost = false
        private set

    val layered get() = mode == MahjongMode.CLASSIC || mode == MahjongMode.TRIPLE
    val grid get() = mode == MahjongMode.CONNECT || mode == MahjongMode.CASCADE
    /** En demi-tuiles pour Solitaire et Triples. */
    val columns: Int get() = when {
        layered -> (tiles.maxOfOrNull { it.x } ?: 0) + 2
        grid -> if (stage < 4) 6 else 8
        else -> 6
    }
    val rows: Int get() = when {
        layered -> (tiles.maxOfOrNull { it.y } ?: 0) + 2
        grid -> when { stage < 4 -> 8; stage < 7 -> 10; else -> 12 }
        else -> if (stage < 4) 6 else 8
    }
    val board get() = tiles.filter { !it.removed && it.id !in tray }
    val remaining get() = tiles.count { !it.removed }
    val complete get() = remaining == 0
    /** Direction of the next fall: down, left, up, right. */
    val gravityDirection get() = matches % 4
    val groupSize get() = if (mode == MahjongMode.TRIPLE) 3 else 2
    val hintsLeft get() = (HINTS - hintsUsed).coerceAtLeast(0)
    val shufflesLeft get() = (SHUFFLES - shufflesUsed).coerceAtLeast(0)

    // ---- Chaîne et étoiles -------------------------------------------------------------

    /** Glissières n'a pas de minuteur : la chaîne n'y casse qu'en annulant ou en mélangeant. */
    val timed get() = mode != MahjongMode.SLIDE
    val chainSeconds get() = when (mode) {
        MahjongMode.TRIPLE -> 5f
        MahjongMode.CASCADE -> 8f
        else -> 7f
    }
    val basePoints get() = when (mode) {
        MahjongMode.TRIPLE -> 15
        MahjongMode.CASCADE -> 12
        MahjongMode.SLIDE -> 20
        else -> 10
    }
    val chaining get() = combo > 0 && (chainLeft > 0f || !timed)
    /** Le meilleur score possible : une seule chaîne du premier au dernier coup. */
    val maxScore get() = (1..tiles.size / groupSize).sumOf { basePoints * it.coerceAtMost(MAX_CHAIN) }
    fun starThreshold(star: Int) = (maxScore * starShares[(star - 1).coerceIn(0, 2)]).toInt()
    /** Finir donne toujours au moins une étoile. */
    val stars get() = (1..3).count { score >= starThreshold(it) }.coerceAtLeast(1)

    /** Fait avancer le temps de jeu ; renvoie vrai si la chaîne a changé. */
    fun tick(seconds: Float): Boolean {
        if (chainLeft <= 0f || !timed) return false
        chainLeft = (chainLeft - seconds / chainSeconds).coerceAtLeast(0f)
        if (chainLeft == 0f) combo = 0
        return true
    }
    fun breakChain() { chainLeft = 0f; combo = 0 }

    init { if (initializeBoard) generate() }

    private fun generate() {
        val random = Random(seed)
        when (mode) {
            MahjongMode.CLASSIC, MahjongMode.TRIPLE -> MahjongShapes.pick(stage, random).forEachIndexed { i, spot ->
                tiles += MahjongTile(i, spot.x, spot.y, spot.z, 0)
            }
            MahjongMode.CONNECT, MahjongMode.CASCADE -> {
                val width = columns
                repeat(width * rows) { tiles += MahjongTile(it, it % width, it / width, 0, 0) }
            }
            MahjongMode.SLIDE -> repeat(MahjongIceGenerator.tileCount(stage)) { index ->
                tiles += MahjongTile(index, index % columns, index / columns, 0, 0)
            }
            MahjongMode.TEN -> repeat(MahjongPuzzleGenerator.tileCount(mode, stage)) { index ->
                tiles += MahjongTile(index, index % columns, index / columns, 0, 0)
            }
        }
        deal(random)
    }

    private fun deal(random: Random) {
        solution.clear(); solutionSteps.clear()
        when (mode) {
            MahjongMode.SLIDE -> MahjongIceGenerator.deal(this, random)
            MahjongMode.TEN -> if (!MahjongPuzzleGenerator.deal(this, random)) dealTen(random)
            else -> MahjongDealer.deal(this, random)
        }
        if (solutionSteps.isEmpty()) solutionSteps += solution.map { MahjongSolutionStep(it) }
    }

    /** Even rows allow a complete witness; removals expose longer corridors. */
    private fun dealTen(random: Random) {
        val groups = board.groupBy { it.y }.values.map { it.sortedBy { tile -> tile.x }.toMutableList() }.toMutableList()
        while (groups.isNotEmpty()) {
            val row = groups.random(random)
            val first = random.nextInt(row.size - 1)
            val pair = listOf(row.removeAt(first), row.removeAt(first))
            val face = random.nextInt(27)
            pair[0].face = face; pair[1].face = random.nextInt(3) * 9 + 8 - face % 9
            solution += pair.map { it.id }
            if (row.isEmpty()) groups.remove(row)
        }
    }

    // ---- Règles --------------------------------------------------------------------------

    fun isFree(tile: MahjongTile): Boolean = isFree(tile, board)

    /** Solitaire : rien dessus et un côté libre. Triples : rien dessus. Autres : toujours libre. */
    internal fun isFree(tile: MahjongTile, live: List<MahjongTile>): Boolean {
        if (tile.removed || tile.id in tray) return false
        if (!layered) return true
        for (other in live) if (other.z > tile.z && abs(other.x - tile.x) < 2 && abs(other.y - tile.y) < 2) return false
        if (mode == MahjongMode.TRIPLE) return true
        var left = false; var right = false
        for (other in live) if (other.z == tile.z && abs(other.y - tile.y) < 2) {
            if (other.x == tile.x - 2) left = true else if (other.x == tile.x + 2) right = true
        }
        return !left || !right
    }

    fun freeIds(): Set<Int> { val live = board; return live.filter { isFree(it, live) }.mapTo(HashSet()) { it.id } }

    fun compatible(a: MahjongTile, b: MahjongTile) = a.id != b.id &&
        if (mode == MahjongMode.TEN) a.face % 9 + b.face % 9 == 8 else MahjongFaces.kind(a.face) == MahjongFaces.kind(b.face)

    private fun straightPath(a: MahjongTile, x: Int, y: Int, destinationId: Int? = null): List<MahjongCell>? {
        if (a.x != x && a.y != y || a.x == x && a.y == y) return null
        val dx = (x - a.x).compareTo(0); val dy = (y - a.y).compareTo(0)
        val path = mutableListOf(MahjongCell(a.x, a.y))
        var cx = a.x; var cy = a.y
        do {
            cx += dx; cy += dy
            if (board.any { it.id != a.id && it.id != destinationId && it.x == cx && it.y == cy }) return null
            path += MahjongCell(cx, cy)
        } while (cx != x || cy != y)
        return path
    }

    /** Search includes the one-cell perimeter, with at most two right-angle turns. */
    fun connection(a: MahjongTile, b: MahjongTile): List<MahjongCell>? {
        if (a.id == b.id || a.removed || b.removed) return null
        straightPath(a, b.x, b.y, b.id)?.let { return it }
        data class Step(val x: Int, val y: Int, val direction: Int, val turns: Int, val path: List<MahjongCell>)
        val occupied = board.map { MahjongCell(it.x, it.y) }.toHashSet()
        val directions = arrayOf(1 to 0, 0 to 1, -1 to 0, 0 to -1)
        val queue = ArrayDeque<Step>()
        val best = mutableMapOf<Triple<Int, Int, Int>, Int>()
        val width = columns; val height = rows
        queue.add(Step(a.x, a.y, -1, 0, listOf(MahjongCell(a.x, a.y))))
        while (queue.isNotEmpty()) {
            val step = queue.removeFirst()
            for ((d, delta) in directions.withIndex()) {
                val x = step.x + delta.first; val y = step.y + delta.second
                val turns = step.turns + if (step.direction == -1 || step.direction == d) 0 else 1
                if (turns > 2 || x !in -1..width || y !in -1..height) continue
                val cell = MahjongCell(x, y)
                if (x == b.x && y == b.y) return step.path + cell
                if (cell in occupied) continue
                val key = Triple(x, y, d)
                if ((best[key] ?: 3) <= turns) continue
                best[key] = turns
                queue.add(Step(x, y, d, turns, step.path + cell))
            }
        }
        return null
    }

    /** Toutes les tuiles qu'un chemin à deux virages au plus peut atteindre depuis [a]. */
    internal fun reachable(a: MahjongTile, live: List<MahjongTile> = board): List<Int> {
        val width = columns + 2; val height = rows + 2
        val occupant = IntArray(width * height) { -1 }
        for (t in live) occupant[(t.y + 1) * width + t.x + 1] = t.id
        val seen = BooleanArray(width * height * 12)
        val found = LinkedHashSet<Int>()
        val dx = intArrayOf(1, 0, -1, 0); val dy = intArrayOf(0, 1, 0, -1)
        fun sweep(x0: Int, y0: Int, d: Int, turns: Int) {
            var x = x0; var y = y0
            while (true) {
                x += dx[d]; y += dy[d]
                if (x !in 0 until width || y !in 0 until height) return
                val cell = y * width + x
                val occ = occupant[cell]
                if (occ != -1) { if (occ != a.id) found += occ; return }
                val key = (cell * 4 + d) * 3 + turns
                if (seen[key]) return
                seen[key] = true
                if (turns < 2) for (p in intArrayOf((d + 1) % 4, (d + 3) % 4)) sweep(x, y, p, turns + 1)
            }
        }
        for (d in 0..3) sweep(a.x + 1, a.y + 1, d, 0)
        return found.toList()
    }

    private fun matchPath(a: MahjongTile, b: MahjongTile): List<MahjongCell>? = when (mode) {
        MahjongMode.CONNECT, MahjongMode.CASCADE -> connection(a, b)
        MahjongMode.TEN, MahjongMode.SLIDE -> straightPath(a, b.x, b.y, b.id)
        else -> emptyList()
    }

    /** Un coup existe-t-il encore ? Glissières : la recherche d'indice en décide. */
    fun hasMove(): Boolean {
        if (complete) return false
        val live = board
        return when (mode) {
            MahjongMode.TRIPLE -> tray.size < TRAY && live.any { isFree(it, live) }
            MahjongMode.CLASSIC -> live.filter { isFree(it, live) }
                .groupingBy { MahjongFaces.kind(it.face) }.eachCount().values.any { it >= 2 }
            MahjongMode.CONNECT, MahjongMode.CASCADE -> live.any { a -> reachable(a, live).any { compatible(a, tiles[it]) } }
            MahjongMode.TEN -> live.any { a -> live.any { b -> b.id > a.id && compatible(a, b) && straightPath(a, b.x, b.y, b.id) != null } }
            MahjongMode.SLIDE -> true
        }
    }
    val stuck get() = !complete && !hasMove()

    /** Nombre de paires jouables tout de suite (Solitaire, Connexion, Cascade). */
    fun availablePairs(): Int {
        val live = board
        return when (mode) {
            MahjongMode.CLASSIC -> live.filter { isFree(it, live) }.groupingBy { MahjongFaces.kind(it.face) }
                .eachCount().values.sumOf { it / 2 }
            MahjongMode.CONNECT, MahjongMode.CASCADE -> live.sumOf { a -> reachable(a, live).count { it > a.id && compatible(a, tiles[it]) } }
            else -> 0
        }
    }

    // ---- Glissières ------------------------------------------------------------------------

    /** The only legal destination in a direction is the last empty cell. */
    fun iceSlideTarget(id: Int, dx: Int, dy: Int): MahjongSlide? {
        if (mode != MahjongMode.SLIDE || !((dx == 0 && dy in listOf(-1, 1)) ||
                (dy == 0 && dx in listOf(-1, 1)))) return null
        val tile = tiles.getOrNull(id)?.takeIf(::isFree) ?: return null
        // A gesture on another tile first returns the previous focus to its
        // origin. Preview that same board without changing any live coordinates.
        val occupied = board.map { other ->
            if (other.id == selected && id != selected && focusOrigin != null) focusOrigin!!
            else MahjongCell(other.x, other.y)
        }.toHashSet()
        var x = tile.x; var y = tile.y
        while (x + dx in 0 until columns && y + dy in 0 until rows &&
            MahjongCell(x + dx, y + dy) !in occupied) {
            x += dx; y += dy
        }
        return if (x == tile.x && y == tile.y) null else MahjongSlide(id, x, y)
    }

    fun canSlide(id: Int, x: Int, y: Int): Boolean {
        if (x !in 0 until columns || y !in 0 until rows) return false
        val tile = tiles.getOrNull(id) ?: return false
        if ((tile.x != x && tile.y != y) || (tile.x == x && tile.y == y)) return false
        val target = iceSlideTarget(id, (x - tile.x).compareTo(0), (y - tile.y).compareTo(0))
        return target?.x == x && target.y == y
    }

    fun slideDirection(id: Int, dx: Int, dy: Int): Result {
        val target = iceSlideTarget(id, dx, dy)
        if (target != null) return slide(id, target.x, target.y)
        lastMatched = emptyList(); lastPath = emptyList()
        val cardinal = (dx == 0 && dy in listOf(-1,1)) || (dy == 0 && dx in listOf(-1,1))
        if (mode == MahjongMode.SLIDE && cardinal && selected != id && tiles.getOrNull(id)?.let(::isFree) == true) {
            val previous = selected?.let { tiles[it] }
            if (previous != null && focusOrigin != MahjongCell(previous.x,previous.y)) remember()
            selectIceTile(id)
            return Result.SELECTED
        }
        return Result.BLOCKED
    }

    fun slide(id: Int, x: Int, y: Int): Result {
        lastMatched = emptyList(); lastPath = emptyList()
        if (!canSlide(id, x, y)) return Result.BLOCKED
        remember()
        if (selected != id) selectIceTile(id)
        val tile = tiles[id]; val path = straightPath(tile, x, y)!!
        moves++; tile.x = x; tile.y = y; lastPath = path
        return Result.MOVED
    }

    private fun selectIceTile(id: Int) {
        selected?.let { previous -> focusOrigin?.let { origin ->
            tiles[previous].x = origin.x; tiles[previous].y = origin.y
        } }
        selected = id
        focusOrigin = MahjongCell(tiles[id].x, tiles[id].y)
    }

    fun slideHint(): MahjongSlide? {
        return if (mode == MahjongMode.SLIDE && !complete) iceSuggestion().second else null
    }

    /** Only an actual winning continuation can produce an ice hint. */
    private fun iceSuggestion(): Pair<List<Int>, MahjongSlide?> {
        val state = snapshot()
        if (state == iceHintState) return iceHint
        iceHintState = state
        iceHint = emptyList<Int>() to null
        val steps = MahjongIceSolver.solve(this) ?: return iceHint
        var index = 0
        while (index < steps.size && steps[index].slide == null &&
            steps[index].ids.isNotEmpty() && steps[index].ids.all { it == selected }) index++
        val first = steps.getOrNull(index) ?: return iceHint
        first.slide?.let { move ->
            iceHint = listOf(move.id) to move
            return iceHint
        }
        val id = first.ids.firstOrNull() ?: return iceHint
        val focus = selected?.let { tiles[it] }
        if (focus != null && compatible(focus, tiles[id]) && matchPath(focus, tiles[id]) != null) {
            iceHint = listOf(focus.id, id) to null
            return iceHint
        }
        val next = steps.getOrNull(index + 1)
        val move = next?.slide?.takeIf { it.id == id }
        if (move != null) iceHint = listOf(id) to move
        else {
            val partner = next?.ids?.singleOrNull()?.takeIf { compatible(tiles[id], tiles[it]) }
            // A preparatory refocus is deliberately a one-tile hint: unlike a
            // pair, that choice returns the current tentative tile to its origin.
            iceHint = (if (partner != null) listOf(id, partner) else listOf(id)) to null
        }
        return iceHint
    }

    // ---- Indices -------------------------------------------------------------------------

    /**
     * Le premier coup d'une fin complète. Si la recherche s'épuise, un coup simplement
     * jouable ([hintSearchLimited]) ; si elle prouve la défaite, rien ([hintProvedLost]).
     */
    fun hint(): List<Int> {
        hintSearchLimited = false; hintProvedLost = false
        if (complete) return emptyList()
        if (mode == MahjongMode.SLIDE) {
            val ids = iceSuggestion().first
            hintProvedLost = ids.isEmpty()
            return ids
        }
        if (mode == MahjongMode.TRIPLE && tray.size >= TRAY) { hintProvedLost = true; return emptyList() }
        val state = snapshot()
        if (puzzleHintState == state) return puzzleHint.also { hintProvedLost = it.isEmpty() }
        val result = if (mode == MahjongMode.TEN) {
            val witness = MahjongPuzzleSolver.verifiedContinuation(this, solutionSteps)
            if (witness != null) MahjongPuzzleSolver.Result(MahjongPuzzleSolver.Outcome.SOLVABLE, witness)
            else MahjongPuzzleSolver.solve(this)
        } else MahjongSolver.solve(this, solutionSteps)
        hintSearchLimited = result.outcome == MahjongPuzzleSolver.Outcome.UNKNOWN
        hintProvedLost = result.outcome == MahjongPuzzleSolver.Outcome.UNSOLVABLE
        puzzleHint = result.steps?.firstOrNull()?.ids.orEmpty().let { if (mode == MahjongMode.TRIPLE) it.take(1) else it }
        if (hintSearchLimited) puzzleHint = MahjongSolver.anyMove(this)
        else {
            puzzleHintState = state
            result.steps?.let { solutionSteps.clear(); solutionSteps.addAll(it) }
        }
        return puzzleHint
    }

    // ---- Coups -----------------------------------------------------------------------------

    fun tap(id: Int): Result {
        lastMatched = emptyList(); lastPath = emptyList()
        val tile = tiles.getOrNull(id) ?: return Result.IGNORED
        if (!isFree(tile)) return Result.BLOCKED
        if (mode == MahjongMode.TRIPLE) {
            if (tray.size >= TRAY) return Result.TRAY_FULL
            remember(); moves++
            val kind = MahjongFaces.kind(tile.face)
            // Les tuiles de même famille se rangent côte à côte dans la réserve.
            val at = tray.indexOfLast { MahjongFaces.kind(tiles[it].face) == kind }
            if (at >= 0) tray.add(at + 1, id) else tray += id
            val group = tray.filter { MahjongFaces.kind(tiles[it].face) == kind }
            if (group.size == 3) { tray.removeAll(group.toSet()); match(group); return Result.MATCHED }
            return if (tray.size >= TRAY) Result.TRAY_FULL else Result.SELECTED
        }
        if (mode == MahjongMode.SLIDE) {
            val focus = selected?.let { tiles[it] }
            if (focus == null) { selectIceTile(id); return Result.SELECTED }
            if (focus.id == id) return Result.SELECTED
            val path = matchPath(focus, tile)
            if (compatible(focus, tile) && path != null) {
                remember(); moves++; selected = null; focusOrigin = null; lastPath = path
                match(listOf(focus.id, id))
                return Result.MATCHED
            }
            if (focusOrigin != MahjongCell(focus.x, focus.y)) remember()
            selectIceTile(id)
            return Result.SELECTED
        }
        val a = selected?.let { tiles[it] }
        if (a == null || a.id == id) { selected = if (a == null) id else null; return Result.SELECTED }
        val path = matchPath(a, tile)
        if (!compatible(a, tile) || path == null) { selected = id; return Result.MISMATCH }
        remember(); moves++; selected = null; lastPath = path
        val fall = gravityDirection
        match(listOf(a.id, id))
        if (mode == MahjongMode.CASCADE) collapse(fall)
        return Result.MATCHED
    }

    private fun match(ids: List<Int>) {
        ids.forEach { tiles[it].removed = true }
        lastMatched = ids; matches++
        combo = if (chaining) (combo + 1).coerceAtMost(MAX_CHAIN) else 1
        chainLeft = 1f
        peakCombo = maxOf(peakCombo, combo); score += basePoints * combo
    }

    internal fun collapse(direction: Int) {
        if (direction % 2 == 0) board.groupBy { it.x }.values.forEach { column ->
            column.sortedBy { it.y }.forEachIndexed { i, tile -> tile.y = if (direction == 0) rows - column.size + i else i }
        } else board.groupBy { it.y }.values.forEach { row ->
            row.sortedBy { it.x }.forEachIndexed { i, tile -> tile.x = if (direction == 1) i else columns - row.size + i }
        }
    }

    fun snapshot() = MahjongSnapshot(tiles.map { it.copy() }, tray.toList(), score, combo, matches, moves, assists, peakCombo,
        if (mode == MahjongMode.SLIDE) selected else null, if (mode == MahjongMode.SLIDE) focusOrigin else null)
    fun restore(state: MahjongSnapshot) {
        tiles.clear(); tiles.addAll(state.tiles.map { it.copy() })
        tray.clear(); tray.addAll(state.tray)
        score = state.score; combo = state.combo; matches = state.matches; moves = state.moves; assists = state.assists
        peakCombo = state.peakCombo; chainLeft = 0f
        selected = if (mode == MahjongMode.SLIDE) state.focusId else null
        focusOrigin = if (mode == MahjongMode.SLIDE) state.focusOrigin else null
        lastMatched = emptyList(); lastPath = emptyList()
    }
    private fun remember() {
        if (history.size >= 256) history.removeAt(0)
        history += snapshot()
    }
    /** Annuler casse la chaîne. */
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val used = assists + 1; val peak = peakCombo
        restore(history.removeAt(history.lastIndex)); assists = used; peakCombo = maxOf(peak, peakCombo)
        breakChain()
        return true
    }

    /**
     * Redistribue les tuiles restantes en gardant une fin possible. Faux si aucune
     * redistribution n'existe (par exemple deux tuiles empilées) : le plateau reste intact.
     */
    fun shuffle(): Boolean {
        if (complete) return false
        val before = snapshot()
        remember(); assists++; shufflesUsed++; selected = null; focusOrigin = null
        breakChain(); puzzleHintState = null; iceHintState = null
        val random = Random(seed xor (shufflesUsed * 7919) xor (moves * 31) xor remaining)
        val done = when (mode) {
            MahjongMode.SLIDE -> { deal(random); true }
            MahjongMode.TEN -> {
                board.forEachIndexed { index, tile -> tile.x = index % columns; tile.y = index / columns; tile.z = 0 }
                deal(random); true
            }
            else -> { tray.clear(); MahjongDealer.redeal(this, random) }
        }
        if (!done) {
            restore(before); history.removeAt(history.lastIndex); shufflesUsed--
        }
        return done
    }
}
