package com.Atom2Universe.app.games.game2048

/**
 * Logique pure du jeu 2048 Quantum.
 * Portée depuis scripts/arcade/quantum-2048.js
 *
 * Représentation : tableau 1D de taille size*size, index = row*size + col.
 *
 * Mode Quantique 🐱 :
 *  - quantumMode = true → l'objectif est un nombre impure (quantumTarget)
 *  - jokers = nombre de coups spéciaux restants
 *  - nextMoveIsJoker = si true, le prochain glissement fusionne TOUTE paire adjacente non-nulle
 *  - Victoire : une tuile égale exactement à quantumTarget apparaît sur le plateau
 *  - Game over en mode quantique : seulement quand jokers = 0 et aucune fusion possible
 */
class Game2048Logic(var size: Int = 4) {

    enum class Direction { UP, DOWN, LEFT, RIGHT }

    var board = IntArray(size * size)
        private set
    var score = 0
        private set
    var moves = 0
        private set
    var bestTile = 0
        private set
    var gameOver = false
        private set
    var hasWon = false
        private set
    var target = 256

    // ── Mode Quantique 🐱 ─────────────────────────────────────────────────────
    var quantumMode = false
    var quantumTarget = 0
    var jokers = 0
    var nextMoveIsJoker = false

    /** Lance une nouvelle partie. */
    fun newGame(
        newSize: Int = size,
        newTarget: Int = target,
        newQuantumMode: Boolean = false,
        newQuantumTarget: Int = 0,
        newJokers: Int = 0
    ) {
        size = newSize
        target = newTarget
        quantumMode = newQuantumMode
        quantumTarget = newQuantumTarget
        jokers = newJokers
        nextMoveIsJoker = false
        board = IntArray(size * size)
        score = 0
        moves = 0
        bestTile = 0
        gameOver = false
        hasWon = false
        spawnTile()
        spawnTile()
    }

    /** Fait glisser les tuiles dans la direction donnée. Retourne true si le plateau a changé. */
    fun move(direction: Direction): Boolean {
        val before = board.copyOf()
        var gained = 0
        val useJoker = nextMoveIsJoker && jokers > 0

        when (direction) {
            Direction.LEFT -> {
                for (row in 0 until size) {
                    val (newLine, pts) = slideLeft(getRow(row), useJoker)
                    setRow(row, newLine)
                    gained += pts
                }
            }
            Direction.RIGHT -> {
                for (row in 0 until size) {
                    val (newLine, pts) = slideLeft(getRow(row).reversed().toIntArray(), useJoker)
                    setRow(row, newLine.reversed().toIntArray())
                    gained += pts
                }
            }
            Direction.UP -> {
                for (col in 0 until size) {
                    val (newLine, pts) = slideLeft(getCol(col), useJoker)
                    setCol(col, newLine)
                    gained += pts
                }
            }
            Direction.DOWN -> {
                for (col in 0 until size) {
                    val (newLine, pts) = slideLeft(getCol(col).reversed().toIntArray(), useJoker)
                    setCol(col, newLine.reversed().toIntArray())
                    gained += pts
                }
            }
        }

        val changed = !board.contentEquals(before)
        if (changed) {
            if (useJoker) {
                jokers--
                nextMoveIsJoker = false
            }
            score += gained
            moves++
            bestTile = board.maxOrNull() ?: 0

            // Vérifier victoire
            if (!hasWon) {
                hasWon = if (quantumMode) {
                    board.any { it == quantumTarget }
                } else {
                    bestTile >= target
                }
            }

            spawnTile()
            checkGameOver()
        }
        return changed
    }

    /**
     * Fait glisser et fusionne une ligne vers la gauche.
     * En mode joker, toute paire adjacente non-nulle peut fusionner.
     */
    private fun slideLeft(line: IntArray, joker: Boolean = false): Pair<IntArray, Int> {
        var pts = 0
        val nonZero = line.filter { it != 0 }.toMutableList()

        var i = 0
        while (i < nonZero.size - 1) {
            if (nonZero[i] == nonZero[i + 1] || joker) {
                val merged = nonZero[i] + nonZero[i + 1]
                nonZero[i] = merged
                nonZero.removeAt(i + 1)
                pts += merged
            }
            i++
        }

        val result = IntArray(size)
        for (j in nonZero.indices) result[j] = nonZero[j]
        return Pair(result, pts)
    }

    private fun getRow(row: Int) = IntArray(size) { col -> board[row * size + col] }
    private fun setRow(row: Int, line: IntArray) {
        for (col in 0 until size) board[row * size + col] = line[col]
    }
    private fun getCol(col: Int) = IntArray(size) { row -> board[row * size + col] }
    private fun setCol(col: Int, line: IntArray) {
        for (row in 0 until size) board[row * size + col] = line[row]
    }

    /** Spawne une tuile aléatoire (90% = 2, 10% = 4) dans une case vide. */
    private fun spawnTile(): Boolean {
        val empty = board.indices.filter { board[it] == 0 }
        if (empty.isEmpty()) return false
        val idx = empty.random()
        board[idx] = if (Math.random() < 0.9) 2 else 4
        return true
    }

    /**
     * Mode quantique : fusionne deux cases adjacentes spécifiques (coûte un joker).
     * Retourne true si la fusion a eu lieu.
     */
    fun mergeAdjacentCells(fromRow: Int, fromCol: Int, toRow: Int, toCol: Int): Boolean {
        if (!quantumMode || gameOver) return false
        if (fromRow !in 0 until size || fromCol !in 0 until size) return false
        if (toRow !in 0 until size || toCol !in 0 until size) return false
        if (kotlin.math.abs(toRow - fromRow) + kotlin.math.abs(toCol - fromCol) != 1) return false
        val fromIdx = fromRow * size + fromCol
        val toIdx = toRow * size + toCol
        val fromVal = board[fromIdx]
        val toVal = board[toIdx]
        if (fromVal == 0 || toVal == 0) return false
        if (jokers <= 0) return false

        val merged = fromVal + toVal
        board[toIdx] = merged
        board[fromIdx] = 0
        jokers--
        nextMoveIsJoker = false
        score += merged
        moves++
        bestTile = board.maxOrNull() ?: 0

        if (!hasWon) {
            hasWon = board.any { it == quantumTarget }
        }

        spawnTile()
        checkGameOver()
        return true
    }

    /** Vérifie si aucun mouvement n'est possible. */
    private fun checkGameOver() {
        if (board.any { it == 0 }) return  // Case vide = pas game over

        // Vérifier fusions classiques (paires égales adjacentes)
        for (row in 0 until size) {
            for (col in 0 until size) {
                val v = board[row * size + col]
                if (col + 1 < size && board[row * size + col + 1] == v) return
                if (row + 1 < size && board[(row + 1) * size + col] == v) return
            }
        }

        // En mode quantique avec jokers restants, toute paire adjacente non-nulle est fusionnable.
        // Sur un plateau plein (sans zéros) avec size >= 2, il y a toujours des paires adjacentes
        // non-nulles → le game over n'arrive que quand jokers = 0.
        if (quantumMode && jokers > 0) return

        gameOver = true
    }

    /** Sérialise l'état pour la sauvegarde. */
    fun serialize(): Map<String, Any> = mapOf(
        "size" to size,
        "target" to target,
        "board" to board.toList(),
        "score" to score,
        "moves" to moves,
        "bestTile" to bestTile,
        "hasWon" to hasWon,
        "gameOver" to gameOver,
        "quantumMode" to quantumMode,
        "quantumTarget" to quantumTarget,
        "jokers" to jokers
    )

    /** Restaure l'état depuis la sauvegarde. Retourne false si les données sont invalides. */
    fun deserialize(data: Map<String, Any>): Boolean {
        return try {
            val savedSize = (data["size"] as? Number)?.toInt() ?: return false
            val savedBoard = (data["board"] as? List<*>)?.map { (it as Number).toInt() } ?: return false
            if (savedBoard.size != savedSize * savedSize) return false
            size = savedSize
            target = (data["target"] as? Number)?.toInt() ?: target
            board = savedBoard.toIntArray()
            score = (data["score"] as? Number)?.toInt() ?: 0
            moves = (data["moves"] as? Number)?.toInt() ?: 0
            bestTile = (data["bestTile"] as? Number)?.toInt() ?: 0
            hasWon = data["hasWon"] as? Boolean ?: false
            gameOver = data["gameOver"] as? Boolean ?: false
            quantumMode = data["quantumMode"] as? Boolean ?: false
            quantumTarget = (data["quantumTarget"] as? Number)?.toInt() ?: 0
            jokers = (data["jokers"] as? Number)?.toInt() ?: 0
            nextMoveIsJoker = false  // Ne jamais restaurer un joker en attente
            true
        } catch (e: Exception) {
            false
        }
    }
}
