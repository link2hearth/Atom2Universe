package com.Atom2Universe.app.games.draughts

class DraughtsGame {

    // board[row][col], seules les cases (row+col)%2==1 sont jouables
    // row 0 = haut (côté noir initial), row 9 = bas (côté blanc initial)
    val board = Array(10) { arrayOfNulls<DraughtsPiece>(10) }

    var currentTurn = DraughtsPieceColor.WHITE
    var isGameOver = false
    var winner: DraughtsPieceColor? = null
    var moveCount = 0

    init { newGame() }

    fun newGame() {
        for (r in 0..9) for (c in 0..9) board[r][c] = null
        for (r in 0..3) for (c in 0..9)
            if ((r + c) % 2 == 1) board[r][c] = DraughtsPiece(DraughtsPieceType.PAWN, DraughtsPieceColor.BLACK)
        for (r in 6..9) for (c in 0..9)
            if ((r + c) % 2 == 1) board[r][c] = DraughtsPiece(DraughtsPieceType.PAWN, DraughtsPieceColor.WHITE)
        currentTurn = DraughtsPieceColor.WHITE
        isGameOver = false
        winner = null
        moveCount = 0
    }

    fun getPieceAt(pos: DraughtsPos): DraughtsPiece? =
        if (pos.row in 0..9 && pos.col in 0..9) board[pos.row][pos.col] else null

    fun getPieceAt(row: Int, col: Int): DraughtsPiece? =
        if (row in 0..9 && col in 0..9) board[row][col] else null

    fun getLegalMoves(): List<DraughtsMove> = getLegalMovesForColor(currentTurn)

    fun getLegalMovesForColor(color: DraughtsPieceColor): List<DraughtsMove> {
        val captures = getAllCaptureMoves(color)
        if (captures.isNotEmpty()) {
            val maxCap = captures.maxOf { it.captureCount }
            return captures.filter { it.captureCount == maxCap }
        }
        return getAllSimpleMoves(color)
    }

    fun getLegalMovesFrom(pos: DraughtsPos): List<DraughtsMove> {
        val piece = getPieceAt(pos) ?: return emptyList()
        if (piece.color != currentTurn) return emptyList()
        return getLegalMoves().filter { it.from == pos }
    }

    // Retourne vrai si des prises sont obligatoires (pour avertir l'utilisateur)
    fun hasMandatoryCaptures(): Boolean = getAllCaptureMoves(currentTurn).isNotEmpty()

    // Positions des pièces qui doivent obligatoirement capturer
    fun mandatoryPiecePositions(): Set<DraughtsPos> =
        getAllCaptureMoves(currentTurn).map { it.from }.toSet()

    private fun getAllSimpleMoves(color: DraughtsPieceColor): List<DraughtsMove> {
        val moves = mutableListOf<DraughtsMove>()
        for (r in 0..9) for (c in 0..9) {
            val piece = board[r][c] ?: continue
            if (piece.color != color) continue
            moves.addAll(simpleMovesForPiece(piece, DraughtsPos(r, c)))
        }
        return moves
    }

    private fun simpleMovesForPiece(piece: DraughtsPiece, pos: DraughtsPos): List<DraughtsMove> {
        val moves = mutableListOf<DraughtsMove>()
        if (piece.isKing()) {
            for ((dr, dc) in DIAGONALS) {
                var r = pos.row + dr; var c = pos.col + dc
                while (r in 0..9 && c in 0..9 && board[r][c] == null) {
                    moves.add(DraughtsMove(pos, DraughtsPos(r, c)))
                    r += dr; c += dc
                }
            }
        } else {
            val dirs = if (piece.color == DraughtsPieceColor.WHITE) listOf(-1 to -1, -1 to 1)
                       else listOf(1 to -1, 1 to 1)
            for ((dr, dc) in dirs) {
                val nr = pos.row + dr; val nc = pos.col + dc
                if (nr in 0..9 && nc in 0..9 && board[nr][nc] == null) {
                    val to = DraughtsPos(nr, nc)
                    val isProm = (piece.color == DraughtsPieceColor.WHITE && nr == 0) ||
                                 (piece.color == DraughtsPieceColor.BLACK && nr == 9)
                    moves.add(DraughtsMove(pos, to, isPromotion = isProm))
                }
            }
        }
        return moves
    }

    private fun getAllCaptureMoves(color: DraughtsPieceColor): List<DraughtsMove> {
        val moves = mutableListOf<DraughtsMove>()
        for (r in 0..9) for (c in 0..9) {
            val piece = board[r][c] ?: continue
            if (piece.color != color) continue
            val pos = DraughtsPos(r, c)
            val tempBoard = copyBoardArray(board)
            tempBoard[r][c] = null
            if (piece.isKing())
                findKingCaptures(piece, pos, tempBoard, emptySet(), pos, emptyList(), moves)
            else
                findPawnCaptures(piece, pos, tempBoard, emptySet(), pos, emptyList(), moves)
        }
        return moves
    }

    private fun findPawnCaptures(
        piece: DraughtsPiece,
        currentPos: DraughtsPos,
        boardState: Array<Array<DraughtsPiece?>>,
        captured: Set<DraughtsPos>,
        moveFrom: DraughtsPos,
        animPath: List<Pair<DraughtsPos, DraughtsPos?>>,
        results: MutableList<DraughtsMove>
    ) {
        var foundAny = false
        for ((dr, dc) in DIAGONALS) {
            val midPos = currentPos.offset(dr, dc)
            val landPos = currentPos.offset(dr * 2, dc * 2)
            if (!midPos.isInBounds() || !landPos.isInBounds()) continue
            val midPiece = boardState[midPos.row][midPos.col] ?: continue
            if (midPiece.color == piece.color) continue
            if (midPos in captured) continue
            if (boardState[landPos.row][landPos.col] != null) continue

            foundAny = true
            val newBoard = copyBoardArray(boardState)
            newBoard[midPos.row][midPos.col] = null
            val newCaptured = captured + midPos
            val newAnimPath = animPath + (landPos to midPos)

            val isProm = (piece.color == DraughtsPieceColor.WHITE && landPos.row == 0) ||
                         (piece.color == DraughtsPieceColor.BLACK && landPos.row == 9)

            if (isProm) {
                results.add(DraughtsMove(moveFrom, landPos, newCaptured.toList(), isPromotion = true, animPath = newAnimPath))
            } else {
                val subResults = mutableListOf<DraughtsMove>()
                findPawnCaptures(piece, landPos, newBoard, newCaptured, moveFrom, newAnimPath, subResults)
                if (subResults.isEmpty()) {
                    results.add(DraughtsMove(moveFrom, landPos, newCaptured.toList(), animPath = newAnimPath))
                } else {
                    results.addAll(subResults)
                }
            }
        }
        if (!foundAny && captured.isNotEmpty()) {
            results.add(DraughtsMove(moveFrom, currentPos, captured.toList(), animPath = animPath))
        }
    }

    private fun findKingCaptures(
        piece: DraughtsPiece,
        currentPos: DraughtsPos,
        boardState: Array<Array<DraughtsPiece?>>,
        captured: Set<DraughtsPos>,
        moveFrom: DraughtsPos,
        animPath: List<Pair<DraughtsPos, DraughtsPos?>>,
        results: MutableList<DraughtsMove>
    ) {
        var foundAny = false
        for ((dr, dc) in DIAGONALS) {
            var r = currentPos.row + dr; var c = currentPos.col + dc
            // Avancer jusqu'à la première pièce ou le bord
            while (r in 0..9 && c in 0..9 && boardState[r][c] == null) { r += dr; c += dc }
            if (r !in 0..9 || c !in 0..9) continue
            val enemyPos = DraughtsPos(r, c)
            val enemyPiece = boardState[r][c] ?: continue
            if (enemyPiece.color == piece.color) continue
            if (enemyPos in captured) continue

            // Toutes les cases d'atterrissage possibles après la pièce ennemie
            r += dr; c += dc
            while (r in 0..9 && c in 0..9 && boardState[r][c] == null) {
                val landPos = DraughtsPos(r, c)
                foundAny = true
                val newBoard = copyBoardArray(boardState)
                newBoard[enemyPos.row][enemyPos.col] = null
                val newCaptured = captured + enemyPos
                val newAnimPath = animPath + (landPos to enemyPos)

                val subResults = mutableListOf<DraughtsMove>()
                findKingCaptures(piece, landPos, newBoard, newCaptured, moveFrom, newAnimPath, subResults)
                if (subResults.isEmpty()) {
                    results.add(DraughtsMove(moveFrom, landPos, newCaptured.toList(), animPath = newAnimPath))
                } else {
                    results.addAll(subResults)
                }
                r += dr; c += dc
            }
        }
        if (!foundAny && captured.isNotEmpty()) {
            results.add(DraughtsMove(moveFrom, currentPos, captured.toList(), animPath = animPath))
        }
    }

    fun makeMove(move: DraughtsMove) {
        val piece = board[move.from.row][move.from.col] ?: return
        board[move.from.row][move.from.col] = null
        for (cap in move.captures) board[cap.row][cap.col] = null
        val finalPiece = if (move.isPromotion) piece.promoted() else {
            val isPromNow = (piece.color == DraughtsPieceColor.WHITE && move.to.row == 0) ||
                            (piece.color == DraughtsPieceColor.BLACK && move.to.row == 9)
            if (isPromNow && !piece.isKing()) piece.promoted() else piece
        }
        board[move.to.row][move.to.col] = finalPiece
        currentTurn = currentTurn.opposite()
        moveCount++
        checkGameOver()
    }

    private fun checkGameOver() {
        val whitePieces = board.sumOf { row -> row.count { it?.color == DraughtsPieceColor.WHITE } }
        val blackPieces = board.sumOf { row -> row.count { it?.color == DraughtsPieceColor.BLACK } }
        when {
            whitePieces == 0 -> { isGameOver = true; winner = DraughtsPieceColor.BLACK }
            blackPieces == 0 -> { isGameOver = true; winner = DraughtsPieceColor.WHITE }
            getLegalMoves().isEmpty() -> { isGameOver = true; winner = currentTurn.opposite() }
        }
    }

    fun countPieces(color: DraughtsPieceColor): Int =
        board.sumOf { row -> row.count { it?.color == color } }

    fun countKings(color: DraughtsPieceColor): Int =
        board.sumOf { row -> row.count { it?.color == color && it.isKing() } }

    fun clone(): DraughtsGame {
        val copy = DraughtsGame()
        for (r in 0..9) for (c in 0..9) copy.board[r][c] = board[r][c]
        copy.currentTurn = currentTurn
        copy.isGameOver = isGameOver
        copy.winner = winner
        copy.moveCount = moveCount
        return copy
    }

    fun serialize(): String {
        val sb = StringBuilder()
        sb.append(if (currentTurn == DraughtsPieceColor.WHITE) "W" else "B")
        for (r in 0..9) for (c in 0..9) {
            if ((r + c) % 2 != 1) continue
            val piece = board[r][c]
            sb.append(when {
                piece == null -> "."
                piece.color == DraughtsPieceColor.WHITE && !piece.isKing() -> "w"
                piece.color == DraughtsPieceColor.WHITE && piece.isKing() -> "W"
                piece.color == DraughtsPieceColor.BLACK && !piece.isKing() -> "b"
                else -> "B"
            })
        }
        return sb.toString()
    }

    fun deserialize(s: String) {
        if (s.length < 51) return
        currentTurn = if (s[0] == 'W') DraughtsPieceColor.WHITE else DraughtsPieceColor.BLACK
        var idx = 1
        for (r in 0..9) for (c in 0..9) {
            if ((r + c) % 2 != 1) continue
            if (idx >= s.length) break
            board[r][c] = when (s[idx]) {
                'w' -> DraughtsPiece(DraughtsPieceType.PAWN, DraughtsPieceColor.WHITE)
                'W' -> DraughtsPiece(DraughtsPieceType.KING, DraughtsPieceColor.WHITE)
                'b' -> DraughtsPiece(DraughtsPieceType.PAWN, DraughtsPieceColor.BLACK)
                'B' -> DraughtsPiece(DraughtsPieceType.KING, DraughtsPieceColor.BLACK)
                else -> null
            }
            idx++
        }
        isGameOver = false; winner = null; moveCount = 0
        checkGameOver()
    }

    private fun copyBoardArray(src: Array<Array<DraughtsPiece?>>): Array<Array<DraughtsPiece?>> =
        Array(10) { r -> Array(10) { c -> src[r][c] } }

    companion object {
        val DIAGONALS = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
    }
}
