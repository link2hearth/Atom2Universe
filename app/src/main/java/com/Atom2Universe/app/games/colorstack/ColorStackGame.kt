package com.Atom2Universe.app.games.colorstack

import org.json.JSONArray
import org.json.JSONObject
import kotlin.random.Random

data class Token(
    val id: Int,
    val colorId: Int,
    val colorHex: String,
    val origin: Int
)

class ColorStackGame {

    enum class Difficulty { EASY, MEDIUM, HARD }

    data class DifficultyConfig(
        val columns: Int,
        val capacity: Int,
        val filledColumns: List<Int>,
        val emptyColumns: Int,
        val scrambleMoves: Int,
        val minMulticoloredColumns: Int,
        val minDisplacedRatio: Double,
        val extraTopSpace: Int
    )

    companion object {
        val COLOR_PALETTE = listOf(
            Pair(0, "#ff6b6b"),
            Pair(1, "#4ab3ff"),
            Pair(2, "#2ecc71"),
            Pair(3, "#f6b93b"),
            Pair(4, "#9b59b6"),
            Pair(5, "#ff9ff3"),
            Pair(6, "#95a5a6")
        )

        val DIFFICULTY_CONFIGS = mapOf(
            Difficulty.EASY to DifficultyConfig(
                columns = 4, capacity = 4, filledColumns = listOf(0, 1, 2),
                emptyColumns = 1, scrambleMoves = 48, minMulticoloredColumns = 2,
                minDisplacedRatio = 0.5, extraTopSpace = 1
            ),
            Difficulty.MEDIUM to DifficultyConfig(
                columns = 6, capacity = 5, filledColumns = listOf(0, 1, 2, 3, 4),
                emptyColumns = 1, scrambleMoves = 72, minMulticoloredColumns = 3,
                minDisplacedRatio = 0.6, extraTopSpace = 1
            ),
            Difficulty.HARD to DifficultyConfig(
                columns = 8, capacity = 6, filledColumns = listOf(0, 1, 2, 3, 4, 5, 6),
                emptyColumns = 1, scrambleMoves = 96, minMulticoloredColumns = 4,
                minDisplacedRatio = 0.65, extraTopSpace = 2
            )
        )

        private const val MAX_SCRAMBLE_ATTEMPTS = 120
        private const val PREFER_DIFFERENT_COLOR_WEIGHT = 2.5
        private const val PREFER_SAME_COLOR_WEIGHT = 1.0
        private const val MIN_MOVE_POOL = 6
    }

    var board: MutableList<ArrayDeque<Token>> = mutableListOf()
        private set
    var initialBoard: List<List<Token>> = emptyList()
        private set
    val history: MutableList<Pair<Int, Int>> = mutableListOf()
    var solved = false
        private set
    var moves = 0
        private set
    var difficulty: Difficulty = Difficulty.EASY
        private set

    private var config: DifficultyConfig = DIFFICULTY_CONFIGS[Difficulty.EASY]!!
    private var tokenIdCounter = 0

    val capacity get() = config.capacity
    val effectiveCapacity get() = config.capacity + config.extraTopSpace
    val columnCount get() = config.columns

    fun newGame(diff: Difficulty) {
        difficulty = diff
        config = DIFFICULTY_CONFIGS[diff]!!
        tokenIdCounter = 0
        solved = false
        moves = 0
        history.clear()

        var result: MutableList<ArrayDeque<Token>>? = null
        repeat(MAX_SCRAMBLE_ATTEMPTS) {
            if (result != null) return@repeat
            val solved = generateSolvedBoard()
            result = scrambleBoard(solved)
        }

        board = result ?: fallbackScramble(generateSolvedBoard())
        initialBoard = board.map { it.toList() }
    }

    fun restart() {
        board = initialBoard.map { colList ->
            ArrayDeque<Token>().also { deque -> colList.forEach { deque.addLast(it) } }
        }.toMutableList()
        history.clear()
        solved = false
        moves = 0
    }

    fun canMove(from: Int, to: Int): Boolean {
        if (from == to || from < 0 || from >= board.size || to < 0 || to >= board.size) return false
        val src = board[from]
        val dst = board[to]
        if (src.isEmpty() || dst.size >= effectiveCapacity) return false
        if (dst.isEmpty()) return true
        return src.last().colorId == dst.last().colorId
    }

    fun move(from: Int, to: Int): Boolean {
        if (!canMove(from, to)) return false
        board[to].addLast(board[from].removeAt(board[from].lastIndex))
        history.add(from to to)
        moves++
        solved = checkSolved()
        return true
    }

    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val (from, to) = history.removeAt(history.lastIndex)
        board[from].addLast(board[to].removeAt(board[to].lastIndex))
        moves = (moves - 1).coerceAtLeast(0)
        solved = false
        return true
    }

    fun getValidTargets(fromIndex: Int): List<Int> = board.indices.filter { canMove(fromIndex, it) }

    private fun checkSolved(): Boolean = board.all { col ->
        if (col.isEmpty()) return@all true
        if (col.size != config.capacity) return@all false
        val first = col.first().colorId
        col.all { it.colorId == first }
    }

    private fun generateSolvedBoard(): MutableList<ArrayDeque<Token>> {
        val result = mutableListOf<ArrayDeque<Token>>()
        val paletteSize = COLOR_PALETTE.size
        val usedIndices = mutableSetOf<Int>()

        config.filledColumns.forEachIndexed { columnIndex, colorHint ->
            var pi = colorHint % paletteSize
            if (paletteSize >= config.filledColumns.size) {
                var tries = 0
                while (usedIndices.contains(pi) && tries < paletteSize) {
                    pi = (pi + 1) % paletteSize
                    tries++
                }
            }
            usedIndices.add(pi)
            val (colorId, colorHex) = COLOR_PALETTE[pi]
            val col = ArrayDeque<Token>()
            repeat(config.capacity) { col.addLast(Token(tokenIdCounter++, colorId, colorHex, columnIndex)) }
            result.add(col)
        }

        val required = maxOf(config.columns, result.size + config.emptyColumns)
        while (result.size < required) result.add(ArrayDeque())
        return result
    }

    // Scramble fidèle au JS : pendant le scramble, TOUT déplacement vers une colonne non-pleine
    // est autorisé (pas de restriction de couleur). Les poids donnent juste plus de chance
    // aux mélanges (différente couleur = poids fort).
    private fun scrambleBoard(solvedBoard: MutableList<ArrayDeque<Token>>): MutableList<ArrayDeque<Token>>? {
        val work = deepCopy(solvedBoard)
        val cap = effectiveCapacity
        val baseRequired = maxOf(3, (config.scrambleMoves * 0.6).toInt())
        val requiredMoves = maxOf(baseRequired, MIN_MOVE_POOL)
        val maxMoves = maxOf(config.scrambleMoves * 5, requiredMoves + MIN_MOVE_POOL)

        var lastMove: Pair<Int, Int>? = null
        var performed = 0

        for (i in 0 until maxMoves) {
            val candidates = collectScrambleCandidates(work, cap, lastMove)
            if (candidates.isEmpty()) {
                if (performed < MIN_MOVE_POOL) return null
                break
            }
            val move = chooseWeighted(candidates) ?: break
            work[move.first].also { src ->
                work[move.second].addLast(src.removeAt(src.lastIndex))
            }
            performed++
            lastMove = move

            if (performed >= config.scrambleMoves && meetsDiversity(work)) break
        }

        if (performed < requiredMoves) return null
        if (!ensureEmptyColumns(work)) return null
        if (!meetsDiversity(work)) return null
        return work
    }

    // Candidats : tout (source non-vide, dest non-pleine), pas de restriction couleur
    private fun collectScrambleCandidates(
        board: MutableList<ArrayDeque<Token>>,
        cap: Int,
        lastMove: Pair<Int, Int>?
    ): List<Pair<Pair<Int, Int>, Double>> {
        val result = mutableListOf<Pair<Pair<Int, Int>, Double>>()
        for (si in board.indices) {
            if (board[si].isEmpty()) continue
            val movingToken = board[si].last()
            for (di in board.indices) {
                if (di == si) continue
                if (board[di].size >= cap) continue
                // Exclure l'exact inverse du dernier move
                if (lastMove != null && lastMove.first == di && lastMove.second == si) continue
                val destTop = board[di].lastOrNull()
                val weight = if (destTop != null && destTop.colorId == movingToken.colorId)
                    PREFER_SAME_COLOR_WEIGHT
                else
                    PREFER_DIFFERENT_COLOR_WEIGHT
                result.add((si to di) to weight)
            }
        }
        return result
    }

    private fun chooseWeighted(candidates: List<Pair<Pair<Int, Int>, Double>>): Pair<Int, Int>? {
        if (candidates.isEmpty()) return null
        val total = candidates.sumOf { it.second }
        var r = Random.nextDouble() * total
        for ((move, w) in candidates) {
            r -= w
            if (r <= 0) return move
        }
        return candidates.last().first
    }

    private fun meetsDiversity(board: MutableList<ArrayDeque<Token>>): Boolean {
        if (isSolvedBoard(board)) return false
        val multicolored = board.count { col ->
            col.size > 1 && col.first().colorId != col.last().colorId ||
            (col.size > 1 && col.any { it.colorId != col.first().colorId })
        }
        if (multicolored < config.minMulticoloredColumns) return false
        var displaced = 0
        var total = 0
        // Utilisez forEachIndexed pour avoir l'index correct (pas board.indexOf)
        board.forEachIndexed { ci, col ->
            col.forEach { token ->
                total++
                if (token.origin != ci) displaced++
            }
        }
        if (total == 0) return false
        return displaced.toDouble() / total >= config.minDisplacedRatio
    }

    // Algorithme fidèle au JS : déplace les jetons un par un pour libérer des colonnes
    private fun ensureEmptyColumns(board: MutableList<ArrayDeque<Token>>): Boolean {
        val required = config.emptyColumns
        if (required == 0) return true
        val cap = effectiveCapacity
        val maxIter = board.size * cap.coerceAtLeast(1) * 4
        var iter = 0
        while (countEmpty(board) < required && iter < maxIter) {
            iter++
            val sorted = board.indices
                .filter { board[it].isNotEmpty() }
                .sortedBy { board[it].size }
            var moved = false
            for (si in sorted) {
                val src = board[si]
                if (src.isEmpty()) continue
                val token = src.last()
                val di = bestRelocationDest(board, si, token, cap) ?: continue
                val dst = board[di]
                if (dst.size >= cap) continue
                dst.addLast(src.removeAt(src.lastIndex))
                moved = true
                break
            }
            if (!moved) break
        }
        return countEmpty(board) >= required
    }

    private fun bestRelocationDest(
        board: MutableList<ArrayDeque<Token>>,
        sourceIndex: Int,
        token: Token,
        cap: Int
    ): Int? {
        var bestIdx = -1
        var bestScore = Double.NEGATIVE_INFINITY
        board.forEachIndexed { di, col ->
            if (di == sourceIndex || col.size >= cap) return@forEachIndexed
            var score = 0.0
            if (di == token.origin) score -= 4.0
            if (col.isEmpty()) {
                score += 1.0
            } else {
                score += 3.0 - col.size.toDouble() / cap.coerceAtLeast(1)
                val top = col.last()
                if (top.colorId != token.colorId) score += 1.0 else score -= 1.0
            }
            if (col.size < cap - 1) score += 0.5
            if (score > bestScore) { bestScore = score; bestIdx = di }
        }
        return if (bestIdx >= 0) bestIdx else null
    }

    private fun countEmpty(board: List<ArrayDeque<Token>>) = board.count { it.isEmpty() }

    private fun isSolvedBoard(board: MutableList<ArrayDeque<Token>>): Boolean = board.all { col ->
        if (col.isEmpty()) return@all true
        if (col.size != config.capacity) return@all false
        val first = col.first().colorId
        col.all { it.colorId == first }
    }

    private fun fallbackScramble(solvedBoard: MutableList<ArrayDeque<Token>>): MutableList<ArrayDeque<Token>> {
        val work = deepCopy(solvedBoard)
        val cap = effectiveCapacity
        val populated = work.indices.filter { work[it].isNotEmpty() }
        val cycleLen = minOf(populated.size, maxOf(2, config.minMulticoloredColumns))
        for (idx in 0 until cycleLen) {
            val si = populated[idx]
            val di = populated[(idx + 1) % populated.size]
            if (si != di && work[di].size < cap) {
                work[di].addLast(work[si].removeAt(work[si].lastIndex))
            }
        }
        ensureEmptyColumns(work)
        return work
    }

    private fun deepCopy(src: MutableList<ArrayDeque<Token>>): MutableList<ArrayDeque<Token>> =
        src.map { col -> ArrayDeque<Token>().also { col.forEach(it::addLast) } }.toMutableList()

    fun serialize(): String {
        val json = JSONObject()
        json.put("difficulty", difficulty.name)
        json.put("moves", moves)
        json.put("solved", solved)
        json.put("tokenIdCounter", tokenIdCounter)
        json.put("board", encodeBoard(board))
        json.put("initialBoard", encodeBoard(initialBoard.map { list ->
            ArrayDeque<Token>().also { d -> list.forEach(d::addLast) }
        }))
        return json.toString()
    }

    private fun encodeBoard(b: List<ArrayDeque<Token>>): JSONArray {
        val arr = JSONArray()
        b.forEach { col ->
            val ca = JSONArray()
            col.forEach { t ->
                ca.put(JSONObject().apply {
                    put("id", t.id); put("colorId", t.colorId)
                    put("colorHex", t.colorHex); put("origin", t.origin)
                })
            }
            arr.put(ca)
        }
        return arr
    }

    fun deserialize(jsonStr: String): Boolean {
        return try {
            val json = JSONObject(jsonStr)
            difficulty = Difficulty.valueOf(json.getString("difficulty"))
            config = DIFFICULTY_CONFIGS[difficulty]!!
            moves = json.getInt("moves")
            solved = json.getBoolean("solved")
            tokenIdCounter = json.optInt("tokenIdCounter", 0)
            board = decodeBoard(json.getJSONArray("board"))
            initialBoard = decodeBoard(json.getJSONArray("initialBoard")).map { it.toList() }
            history.clear()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun decodeBoard(arr: JSONArray): MutableList<ArrayDeque<Token>> {
        val result = mutableListOf<ArrayDeque<Token>>()
        for (i in 0 until arr.length()) {
            val ca = arr.getJSONArray(i)
            val col = ArrayDeque<Token>()
            for (j in 0 until ca.length()) {
                val t = ca.getJSONObject(j)
                col.addLast(Token(t.getInt("id"), t.getInt("colorId"), t.getString("colorHex"), t.getInt("origin")))
            }
            result.add(col)
        }
        return result
    }
}
