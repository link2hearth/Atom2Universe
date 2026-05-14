package com.Atom2Universe.app.games.link

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class LinkGame {

    enum class Difficulty { EASY, MEDIUM, HARD }
    enum class CellType { NORMAL, PLUS }
    enum class GenerationMode { BASE, PLUS, RANDOM }
    enum class GameMode { CLASSIC, PERFECT }
    enum class PairsLevel { FEW, NORMAL, MANY }

    data class DifficultyConfig(
        val sizeMin: Int, val sizeMax: Int,
        val plusMin: Int, val plusMax: Int,
        val pairMin: Int, val pairMax: Int,
        val scrambleMin: Int, val scrambleMax: Int
    )

    data class Cell(
        val type: CellType,
        var value: Int,
        val pairId: Int?,
        val pairColor: Int?
    )

    data class Coord(val row: Int, val col: Int)
    data class CellChange(val row: Int, val col: Int, val previous: Int, val next: Int)

    companion object {
        val CONFIGS = mapOf(
            Difficulty.EASY   to DifficultyConfig(3, 4,  0, 1,  0, 2,  12, 20),
            Difficulty.MEDIUM to DifficultyConfig(5, 6,  1, 3,  2, 5,  30, 50),
            Difficulty.HARD   to DifficultyConfig(7, 8,  3, 6,  4, 10, 60, 100)
        )
        // Perfect mode: grilles fixes, très peu de coups (chacun doit être rejoué exactement)
        val PERFECT_CONFIGS = mapOf(
            Difficulty.EASY   to DifficultyConfig(3, 3,  0, 0,  0, 1,  1, 2),
            Difficulty.MEDIUM to DifficultyConfig(4, 4,  0, 1,  0, 2,  2, 3),
            Difficulty.HARD   to DifficultyConfig(5, 5,  1, 2,  1, 3,  3, 5)
        )
        val LINK_LENGTHS = intArrayOf(2, 3, 4)
        private const val MAX_HISTORY = 200
        private const val MAX_VISITS = 7

        // Couleurs ordonnées pour maximiser le contraste entre paires consécutives :
        // rouge / bleu / vert / orange / violet / cyan / jaune / rose ...
        val PAIR_COLORS = intArrayOf(
            0xFFE53935.toInt(), // 0  rouge vif
            0xFF1565C0.toInt(), // 1  bleu foncé
            0xFF2E7D32.toInt(), // 2  vert foncé
            0xFFE64A19.toInt(), // 3  orange brûlé
            0xFF7B1FA2.toInt(), // 4  violet
            0xFF00838F.toInt(), // 5  cyan-sarcelle
            0xFFF9A825.toInt(), // 6  ambre/jaune
            0xFFC2185B.toInt(), // 7  rose magenta
            0xFF283593.toInt(), // 8  indigo foncé
            0xFF00695C.toInt(), // 9  sarcelle foncé
            0xFFFF8F00.toInt(), // 10 ambre vif
            0xFF6A1B9A.toInt(), // 11 violet profond
            0xFF039BE5.toInt(), // 12 bleu ciel
            0xFF558B2F.toInt(), // 13 vert olive
            0xFF5D4037.toInt(), // 14 brun chocolat
            0xFF37474F.toInt()  // 15 anthracite
        )

        val PAIRS_RANGE = mapOf(
            PairsLevel.FEW    to (1 to 2),
            PairsLevel.NORMAL to (3 to 4),
            PairsLevel.MANY   to (5 to 6)
        )

        private val ZIGZAG_BASE = listOf(Coord(0,0), Coord(0,1), Coord(1,1), Coord(1,2))
        private val L_BASE      = listOf(Coord(0,0), Coord(1,0), Coord(2,0), Coord(2,1))
    }

    var board: Array<Array<Cell>> = emptyArray()
        private set
    private var initialBoard: Array<Array<Cell>> = emptyArray()
    var pairs: MutableMap<Int, MutableList<Coord>> = mutableMapOf()
        private set
    var moves = 0
        private set
    private val history = ArrayDeque<List<CellChange>>()
    var isVictory = false
        private set

    var difficulty = Difficulty.MEDIUM
    var linkLength = 3
    var generationMode = GenerationMode.PLUS
    var gameMode = GameMode.CLASSIC
    var pairsLevel = PairsLevel.NORMAL
    var solutionMoveCount = 0
        private set

    val size get() = board.size
    val canUndo get() = history.isNotEmpty()

    // --- Shape variant sets for validation ---
    private val zigzagKeys: Set<String> by lazy { allVariantKeys(ZIGZAG_BASE) }
    private val lKeys: Set<String>      by lazy { allVariantKeys(L_BASE) }

    fun generateLevel() {
        if (gameMode == GameMode.PERFECT) generatePerfectLevel()
        else generateClassicLevel()
    }

    private fun generateClassicLevel() {
        val cfg = CONFIGS[difficulty]!!
        val sz = Random.nextInt(cfg.sizeMin, cfg.sizeMax + 1)
        val newBoard = buildInitialBoard(sz, cfg)
        val scrambleCount = Random.nextInt(cfg.scrambleMin, cfg.scrambleMax + 1)
        scramble(newBoard, scrambleCount)
        board = newBoard
        initialBoard = cloneBoard(newBoard)
        moves = 0
        history.clear()
        isVictory = false
        solutionMoveCount = 0
    }

    private fun generatePerfectLevel() {
        val cfg = PERFECT_CONFIGS[difficulty]!!
        val sz = cfg.sizeMin  // taille fixe par difficulté
        val newBoard = buildInitialBoard(sz, cfg)
        val targetCount = Random.nextInt(cfg.scrambleMin, cfg.scrambleMax + 1)
        solutionMoveCount = applyPerfectScramble(newBoard, targetCount)
        board = newBoard
        initialBoard = cloneBoard(newBoard)
        moves = 0
        history.clear()
        isVictory = false
    }

    // Mélange perfect : applique N coups exacts avec normalEffect.
    // Rejouer exactement ces coups (dans n'importe quel ordre) résout la grille
    // car normalEffect est sa propre inverse (f∘f = identité).
    private fun applyPerfectScramble(board: Array<Array<Cell>>, targetCount: Int): Int {
        val sz = board.size
        val usedKeys = mutableSetOf<String>()
        var applied = 0
        var attempts = 0
        val maxAttempts = targetCount * 20 + 50
        while (applied < targetCount && attempts < maxAttempts) {
            attempts++
            val pattern = randomPattern(sz) ?: continue
            val key = patternKey(pattern)
            if (key in usedKeys) continue
            applyToBoard(board, pattern, ::normalEffect)
            if (isBoardSolved(board)) {
                applyToBoard(board, pattern, ::normalEffect)  // annule
                continue
            }
            usedKeys.add(key)
            applied++
        }
        return applied
    }

    private fun patternKey(pattern: List<Coord>) =
        pattern.sortedWith(compareBy({ it.row }, { it.col }))
            .joinToString("|") { "${it.row},${it.col}" }

    private fun isBoardSolved(board: Array<Array<Cell>>) =
        board.all { row -> row.all { cell ->
            if (cell.type == CellType.PLUS) cell.value == 10 else cell.value == 0
        } }

    // --- Public move API ---

    fun applyMove(path: List<Coord>): List<Coord>? {
        if (!isPatternValid(path)) return null
        val changes = mutableListOf<CellChange>()
        val selectedKeys = path.map { coordKey(it) }.toSet()
        val usedPairs = mutableSetOf<Int>()

        path.forEach { c ->
            val cell = board[c.row][c.col]
            val prev = cell.value
            normalEffect(cell)
            changes.add(CellChange(c.row, c.col, prev, cell.value))
        }
        path.forEach { c ->
            val cell = board[c.row][c.col]
            val pid = cell.pairId ?: return@forEach
            if (pid in usedPairs) return@forEach
            val partner = pairPartner(c.row, c.col, pid) ?: return@forEach
            if (coordKey(partner) in selectedKeys) return@forEach
            usedPairs.add(pid)
            val pc = board[partner.row][partner.col]
            val prev = pc.value
            normalEffect(pc)
            changes.add(CellChange(partner.row, partner.col, prev, pc.value))
        }

        history.addLast(changes)
        if (history.size > MAX_HISTORY) history.removeFirst()
        moves++
        isVictory = checkVictory()
        return changes.map { Coord(it.row, it.col) }
    }

    fun undoLastMove(): List<Coord>? {
        if (history.isEmpty()) return null
        val entry = history.removeLast()
        entry.forEach { ch -> board[ch.row][ch.col].value = ch.previous }
        moves = max(0, moves - 1)
        isVictory = false
        return entry.map { Coord(it.row, it.col) }
    }

    fun restartLevel() {
        board = cloneBoard(initialBoard)
        history.clear()
        moves = 0
        isVictory = false
    }

    fun countRemaining(): Pair<Int, Int> {
        var normal = 0; var plus = 0
        for (row in board) for (cell in row) {
            if (cell.type == CellType.PLUS && cell.value != 10) plus++
            else if (cell.type == CellType.NORMAL && cell.value != 0) normal++
        }
        return Pair(normal, plus)
    }

    fun pairPartner(row: Int, col: Int, pairId: Int): Coord? =
        pairs[pairId]?.firstOrNull { it.row != row || it.col != col }

    // --- Pattern validation ---

    fun isPatternValid(path: List<Coord>): Boolean {
        if (path.size != linkLength) return false
        val keys = path.map { coordKey(it) }.toSet()
        if (keys.size != linkLength) return false
        for (c in path) if (c.row !in board.indices || c.col !in board[c.row].indices) return false
        if (!isConnected(path)) return false
        return when (linkLength) {
            2 -> isLine(path)
            3 -> isLine(path) || isCorner(path)
            4 -> isLine(path) || isSquare(path) || isZigzag(path) || isLShape(path)
            else -> false
        }
    }

    private fun isConnected(coords: List<Coord>): Boolean {
        val visited = mutableSetOf(coordKey(coords[0]))
        val queue = ArrayDeque<Coord>().also { it.add(coords[0]) }
        while (queue.isNotEmpty()) {
            val cur = queue.removeFirst()
            for (coord in coords) {
                if (coordKey(coord) in visited) continue
                if (abs(coord.row - cur.row) + abs(coord.col - cur.col) == 1) {
                    visited.add(coordKey(coord))
                    queue.add(coord)
                }
            }
        }
        return visited.size == coords.size
    }

    private fun isLine(coords: List<Coord>): Boolean {
        val rows = coords.map { it.row }; val cols = coords.map { it.col }
        if (rows.toSet().size == 1) return cols.sorted().zipWithNext().all { (a, b) -> b == a + 1 }
        if (cols.toSet().size == 1) return rows.sorted().zipWithNext().all { (a, b) -> b == a + 1 }
        return false
    }

    private fun isCorner(coords: List<Coord>): Boolean {
        if (coords.size != 3) return false
        val rows = coords.map { it.row }.toSet().toList()
        val cols = coords.map { it.col }.toSet().toList()
        if (rows.size != 2 || cols.size != 2) return false
        val present = coords.map { "${it.row}:${it.col}" }.toSet()
        return rows.sumOf { r -> cols.count { c -> "$r:$c" !in present } } == 1
    }

    private fun isSquare(coords: List<Coord>): Boolean {
        if (coords.size != 4) return false
        val rows = coords.map { it.row }.toSet().toList()
        val cols = coords.map { it.col }.toSet().toList()
        if (rows.size != 2 || cols.size != 2) return false
        val present = coords.map { "${it.row}:${it.col}" }.toSet()
        return rows.all { r -> cols.all { c -> "$r:$c" in present } }
    }

    private fun isZigzag(coords: List<Coord>) = shapeKey(normalizeShape(coords)) in zigzagKeys
    private fun isLShape(coords: List<Coord>)  = shapeKey(normalizeShape(coords)) in lKeys

    // --- Shape variant helpers ---

    private fun normalizeShape(coords: List<Coord>): List<Coord> {
        val minRow = coords.minOf { it.row }; val minCol = coords.minOf { it.col }
        return coords.map { Coord(it.row - minRow, it.col - minCol) }
            .sortedWith(compareBy({ it.row }, { it.col }))
    }

    private fun shapeKey(coords: List<Coord>) = coords.joinToString("|") { "${it.row},${it.col}" }

    private fun allVariantKeys(base: List<Coord>): Set<String> {
        val result = mutableSetOf<String>()
        val transforms = listOf<(Coord) -> Coord>(
            { p -> Coord(p.row, p.col) },
            { p -> Coord(p.col, -p.row) },
            { p -> Coord(-p.row, -p.col) },
            { p -> Coord(-p.col, p.row) }
        )
        for (t in transforms) {
            val r = base.map(t)
            result.add(shapeKey(normalizeShape(r)))
            result.add(shapeKey(normalizeShape(r.map { Coord(it.row, -it.col) })))
        }
        return result
    }

    private fun buildVariantList(base: List<Coord>): List<List<Coord>> {
        val result = mutableListOf<List<Coord>>(); val seen = mutableSetOf<String>()
        val transforms = listOf<(Coord) -> Coord>(
            { p -> Coord(p.row, p.col) },
            { p -> Coord(p.col, -p.row) },
            { p -> Coord(-p.row, -p.col) },
            { p -> Coord(-p.col, p.row) }
        )
        for (t in transforms) {
            val r = base.map(t)
            val nr = normalizeShape(r); if (seen.add(shapeKey(nr))) result.add(nr)
            val nm = normalizeShape(r.map { Coord(it.row, -it.col) }); if (seen.add(shapeKey(nm))) result.add(nm)
        }
        return result
    }

    // --- Board construction ---

    private fun buildInitialBoard(sz: Int, cfg: DifficultyConfig): Array<Array<Cell>> {
        val total = sz * sz
        val shuffled = (0 until total).toMutableList().also { it.shuffle() }
        val plusTarget = Random.nextInt(min(cfg.plusMin, total), min(cfg.plusMax, total) + 1)
        val plusSet = shuffled.take(plusTarget).toSet()

        val maxPairs = total / 2
        val (pMin, pMax) = PAIRS_RANGE[pairsLevel]!!
        val pairCount = Random.nextInt(min(pMin, maxPairs), min(pMax, maxPairs) + 1)
        val pairAssignments = buildPairAssignments(sz, total, pairCount)

        val newPairs = mutableMapOf<Int, MutableList<Coord>>()
        val result = Array(sz) { row ->
            Array(sz) { col ->
                val idx = row * sz + col
                val pa = pairAssignments[idx]
                val type = if (idx in plusSet) CellType.PLUS else CellType.NORMAL
                val value = if (type == CellType.PLUS) 10 else 0
                if (pa != null) newPairs.getOrPut(pa.first) { mutableListOf() }.add(Coord(row, col))
                Cell(type, value, pa?.first, pa?.second)
            }
        }
        pairs = newPairs
        return result
    }

    private fun buildPairAssignments(sz: Int, total: Int, count: Int): Map<Int, Pair<Int, Int>> {
        val shuffled = (0 until total).toMutableList().also { it.shuffle() }
        val result = mutableMapOf<Int, Pair<Int, Int>>()
        var ptr = 0
        for (id in 0 until count) {
            while (ptr < shuffled.size && shuffled[ptr] in result) ptr++
            if (ptr >= shuffled.size - 1) break
            val first = shuffled[ptr++]
            while (ptr < shuffled.size && shuffled[ptr] in result) ptr++
            if (ptr >= shuffled.size) break
            val second = shuffled[ptr++]
            val color = PAIR_COLORS[id % PAIR_COLORS.size]
            result[first] = Pair(id, color)
            result[second] = Pair(id, color)
        }
        return result
    }

    // --- Board scrambling ---

    private fun scramble(board: Array<Array<Cell>>, count: Int) {
        when (generationMode) {
            GenerationMode.BASE   -> scrambleBase(board, count)
            GenerationMode.PLUS   -> scramblePlus(board, count)
            GenerationMode.RANDOM -> scrambleRandom(board, count)
        }
    }

    private fun scrambleBase(board: Array<Array<Cell>>, count: Int) {
        val sz = board.size; var applied = 0; var attempts = 0
        val max = count * 6 + 30
        while (applied < count && attempts < max) {
            attempts++
            val pattern = randomPattern(sz) ?: continue
            if (applyToBoard(board, pattern, ::normalEffect)) applied++
        }
    }

    private fun scramblePlus(board: Array<Array<Cell>>, count: Int) {
        val sz = board.size
        val visits = Array(sz) { IntArray(sz) }
        var applied = 0; var attempts = 0; val max = count * 8 + 50
        while (applied < count && attempts < max) {
            attempts++
            val pattern = randomPattern(sz) ?: continue
            val affected = collectAffected(board, pattern)
            if (affected.isEmpty() || affected.any { visits[it.row][it.col] >= MAX_VISITS }) continue
            if (applyToBoard(board, pattern, ::creationEffect)) {
                affected.forEach { visits[it.row][it.col]++ }
                applied++
            }
        }
    }

    private fun scrambleRandom(board: Array<Array<Cell>>, count: Int) {
        val sz = board.size
        val visits = Array(sz) { IntArray(sz) }
        var applied = 0; var attempts = 0; val max = count * 8 + 50
        while (applied < count && attempts < max) {
            attempts++
            val pattern = randomPattern(sz) ?: continue
            if (Random.nextBoolean()) {
                val affected = collectAffected(board, pattern)
                if (affected.isEmpty() || affected.any { visits[it.row][it.col] >= MAX_VISITS }) continue
                if (applyToBoard(board, pattern, ::creationEffect)) {
                    affected.forEach { visits[it.row][it.col]++ }
                    applied++
                }
            } else {
                if (applyToBoard(board, pattern, ::normalEffect)) applied++
            }
        }
        if (applied < count) scrambleBase(board, count - applied)
    }

    private fun applyToBoard(board: Array<Array<Cell>>, path: List<Coord>, effect: (Cell) -> Unit): Boolean {
        if (path.size != linkLength) return false
        val selectedKeys = path.map { coordKey(it) }.toSet()
        val usedPairs = mutableSetOf<Int>()
        for (c in path) {
            if (c.row !in board.indices || c.col !in board[c.row].indices) return false
            effect(board[c.row][c.col])
        }
        for (c in path) {
            val cell = board[c.row][c.col]
            val pid = cell.pairId ?: continue
            if (pid in usedPairs) continue
            val partner = pairPartner(c.row, c.col, pid) ?: continue
            if (coordKey(partner) in selectedKeys) continue
            usedPairs.add(pid)
            if (partner.row in board.indices && partner.col in board[partner.row].indices)
                effect(board[partner.row][partner.col])
        }
        return true
    }

    private fun collectAffected(board: Array<Array<Cell>>, path: List<Coord>): List<Coord> {
        val result = mutableListOf<Coord>()
        val selectedKeys = path.map { coordKey(it) }.toSet()
        val usedPairs = mutableSetOf<Int>()
        for (c in path) {
            result.add(c)
            val cell = board[c.row][c.col]
            val pid = cell.pairId ?: continue
            if (pid in usedPairs) continue
            val partner = pairPartner(c.row, c.col, pid) ?: continue
            if (coordKey(partner) in selectedKeys) continue
            usedPairs.add(pid)
            result.add(partner)
        }
        return result
    }

    // --- Cell effects ---

    private fun normalEffect(cell: Cell) {
        cell.value = if (cell.type == CellType.PLUS) {
            if (cell.value < 10) cell.value + 1 else 9
        } else {
            if (cell.value > 0) cell.value - 1 else 1
        }
    }

    private fun creationEffect(cell: Cell) {
        cell.value = if (cell.type == CellType.PLUS) {
            if (cell.value > 0) cell.value - 1 else 1
        } else {
            if (cell.value < 10) cell.value + 1 else 9
        }
    }

    // --- Random pattern generation ---

    private fun randomPattern(sz: Int): List<Coord>? = when (linkLength) {
        2 -> randomLine(sz, 2)
        3 -> if (Random.nextBoolean()) randomLine(sz, 3) else randomCorner(sz)
        4 -> listOf({ randomLine(sz, 4) }, { randomSquare(sz) },
                    { randomVariant(sz, buildVariantList(ZIGZAG_BASE)) },
                    { randomVariant(sz, buildVariantList(L_BASE)) })
            .shuffled().firstNotNullOfOrNull { it() }
        else -> null
    }

    private fun randomLine(sz: Int, len: Int): List<Coord>? {
        if (sz < len) return null
        return if (Random.nextBoolean()) {
            val row = Random.nextInt(sz); val start = Random.nextInt(sz - len + 1)
            List(len) { Coord(row, start + it) }
        } else {
            val col = Random.nextInt(sz); val start = Random.nextInt(sz - len + 1)
            List(len) { Coord(start + it, col) }
        }
    }

    private fun randomCorner(sz: Int): List<Coord>? {
        if (sz < 2) return null
        repeat(40) {
            val pr = Random.nextInt(sz); val pc = Random.nextInt(sz)
            val dirs = listOf(Pair(-1,0) to Pair(0,1), Pair(-1,0) to Pair(0,-1),
                              Pair(1,0) to Pair(0,1),  Pair(1,0) to Pair(0,-1)).shuffled()
            for ((d1, d2) in dirs) {
                val r1 = pr + d1.first; val c1 = pc + d1.second
                val r2 = pr + d2.first; val c2 = pc + d2.second
                if (r1 in 0 until sz && c1 in 0 until sz && r2 in 0 until sz && c2 in 0 until sz)
                    return listOf(Coord(pr, pc), Coord(r1, c1), Coord(r2, c2))
            }
        }
        return randomLine(sz, 3)
    }

    private fun randomSquare(sz: Int): List<Coord>? {
        if (sz < 2) return null
        val r = Random.nextInt(sz - 1); val c = Random.nextInt(sz - 1)
        return listOf(Coord(r, c), Coord(r, c+1), Coord(r+1, c+1), Coord(r+1, c))
    }

    private fun randomVariant(sz: Int, variants: List<List<Coord>>): List<Coord>? {
        for (v in variants.shuffled()) {
            val maxR = v.maxOf { it.row }; val maxC = v.maxOf { it.col }
            if (sz <= maxR || sz <= maxC) continue
            val br = Random.nextInt(sz - maxR); val bc = Random.nextInt(sz - maxC)
            return v.map { Coord(it.row + br, it.col + bc) }
        }
        return null
    }

    // --- Misc helpers ---

    private fun checkVictory(): Boolean {
        val (n, p) = countRemaining(); return n == 0 && p == 0
    }

    private fun cloneBoard(src: Array<Array<Cell>>): Array<Array<Cell>> =
        Array(src.size) { r -> Array(src[r].size) { c -> src[r][c].let { Cell(it.type, it.value, it.pairId, it.pairColor) } } }

    private fun coordKey(c: Coord) = "${c.row}:${c.col}"
}
