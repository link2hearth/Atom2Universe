package com.Atom2Universe.app.games.chess

import kotlin.math.abs

/**
 * Logique du jeu d'échecs (sans dépendances Android)
 */
class ChessGame {

    // Plateau 8×8 (row 0 = rang 8, row 7 = rang 1)
    private val board = Array<Array<Piece?>>(8) { arrayOfNulls(8) }

    // État du jeu
    var currentTurn: PieceColor = PieceColor.WHITE
        private set

    val moveHistory = mutableListOf<Move>()
    val capturedPieces = mutableListOf<Piece>()

    // Tracking spécial pour coups spéciaux
    var enPassantTarget: Square? = null
        private set

    private var whiteKingMoved = false
    private var blackKingMoved = false
    private var whiteRookKingsideMoved = false
    private var whiteRookQueensideMoved = false
    private var blackRookKingsideMoved = false
    private var blackRookQueensideMoved = false

    // Statut de la partie
    var isInCheck = false
        private set
    var isCheckmate = false
        private set
    var isStalemate = false
        private set

    init {
        newGame()
    }

    /**
     * Initialise une nouvelle partie
     */
    fun newGame() {
        setupStartingPosition()
        currentTurn = PieceColor.WHITE
        moveHistory.clear()
        capturedPieces.clear()
        enPassantTarget = null
        resetCastlingRights()
        isInCheck = false
        isCheckmate = false
        isStalemate = false
    }

    /**
     * Configure la position de départ standard
     */
    private fun setupStartingPosition() {
        // Vider le plateau
        for (row in 0..7) {
            for (col in 0..7) {
                board[row][col] = null
            }
        }

        // Rang 8 (row 0) - Pièces noires
        board[0][0] = Piece(PieceType.ROOK, PieceColor.BLACK)
        board[0][1] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
        board[0][2] = Piece(PieceType.BISHOP, PieceColor.BLACK)
        board[0][3] = Piece(PieceType.QUEEN, PieceColor.BLACK)
        board[0][4] = Piece(PieceType.KING, PieceColor.BLACK)
        board[0][5] = Piece(PieceType.BISHOP, PieceColor.BLACK)
        board[0][6] = Piece(PieceType.KNIGHT, PieceColor.BLACK)
        board[0][7] = Piece(PieceType.ROOK, PieceColor.BLACK)

        // Rang 7 (row 1) - Pions noirs
        for (col in 0..7) {
            board[1][col] = Piece(PieceType.PAWN, PieceColor.BLACK)
        }

        // Rang 2 (row 6) - Pions blancs
        for (col in 0..7) {
            board[6][col] = Piece(PieceType.PAWN, PieceColor.WHITE)
        }

        // Rang 1 (row 7) - Pièces blanches
        board[7][0] = Piece(PieceType.ROOK, PieceColor.WHITE)
        board[7][1] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
        board[7][2] = Piece(PieceType.BISHOP, PieceColor.WHITE)
        board[7][3] = Piece(PieceType.QUEEN, PieceColor.WHITE)
        board[7][4] = Piece(PieceType.KING, PieceColor.WHITE)
        board[7][5] = Piece(PieceType.BISHOP, PieceColor.WHITE)
        board[7][6] = Piece(PieceType.KNIGHT, PieceColor.WHITE)
        board[7][7] = Piece(PieceType.ROOK, PieceColor.WHITE)
    }

    /**
     * Réinitialise les droits de roque
     */
    private fun resetCastlingRights() {
        whiteKingMoved = false
        blackKingMoved = false
        whiteRookKingsideMoved = false
        whiteRookQueensideMoved = false
        blackRookKingsideMoved = false
        blackRookQueensideMoved = false
    }

    /**
     * Récupère la pièce à une case donnée
     */
    fun getPieceAt(square: Square): Piece? {
        return if (square.isValid()) board[square.row][square.col] else null
    }

    /**
     * Récupère la pièce à une position donnée
     */
    fun getPieceAt(row: Int, col: Int): Piece? {
        return if (row in 0..7 && col in 0..7) board[row][col] else null
    }

    /**
     * Vérifie si la partie est terminée
     */
    fun isGameOver(): Boolean = isCheckmate || isStalemate

    /**
     * Trouve la position du roi d'une couleur
     */
    private fun findKing(color: PieceColor): Square? {
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = board[row][col]
                if (piece != null && piece.type == PieceType.KING && piece.color == color) {
                    return Square(row, col)
                }
            }
        }
        return null
    }

    /**
     * Génère tous les coups pseudo-légaux pour une case
     * (ne vérifie pas si le roi reste en échec)
     */
    private fun generatePseudoLegalMoves(square: Square, piece: Piece): List<Move> {
        return when (piece.type) {
            PieceType.PAWN -> generatePawnMoves(square, piece)
            PieceType.KNIGHT -> generateKnightMoves(square, piece)
            PieceType.BISHOP -> generateBishopMoves(square, piece)
            PieceType.ROOK -> generateRookMoves(square, piece)
            PieceType.QUEEN -> generateQueenMoves(square, piece)
            PieceType.KING -> generateKingMoves(square, piece)
        }
    }

    /**
     * Génère les coups possibles pour un pion
     */
    private fun generatePawnMoves(square: Square, piece: Piece): List<Move> {
        val moves = mutableListOf<Move>()
        val direction = if (piece.color == PieceColor.WHITE) -1 else 1
        val startRank = if (piece.color == PieceColor.WHITE) 6 else 1
        val promotionRank = if (piece.color == PieceColor.WHITE) 0 else 7

        // Avance d'une case
        val oneStep = Square(square.row + direction, square.col)
        if (oneStep.isValid() && getPieceAt(oneStep) == null) {
            if (oneStep.row == promotionRank) {
                // Promotion
                for (promotionType in listOf(
                    PieceType.QUEEN, PieceType.ROOK,
                    PieceType.BISHOP, PieceType.KNIGHT
                )) {
                    moves.add(Move(square, oneStep, piece, null, promotionType))
                }
            } else {
                moves.add(Move(square, oneStep, piece))

                // Avance de deux cases depuis la position de départ
                if (square.row == startRank) {
                    val twoStep = Square(square.row + direction * 2, square.col)
                    if (getPieceAt(twoStep) == null) {
                        moves.add(Move(square, twoStep, piece))
                    }
                }
            }
        }

        // Captures diagonales
        for (colOffset in listOf(-1, 1)) {
            val captureSquare = Square(square.row + direction, square.col + colOffset)
            if (captureSquare.isValid()) {
                val target = getPieceAt(captureSquare)
                if (target != null && target.color != piece.color) {
                    if (captureSquare.row == promotionRank) {
                        for (promotionType in listOf(
                            PieceType.QUEEN, PieceType.ROOK,
                            PieceType.BISHOP, PieceType.KNIGHT
                        )) {
                            moves.add(Move(square, captureSquare, piece, target, promotionType))
                        }
                    } else {
                        moves.add(Move(square, captureSquare, piece, target))
                    }
                }

                // En passant
                if (captureSquare == enPassantTarget) {
                    val capturedPawnSquare = Square(square.row, captureSquare.col)
                    val capturedPawn = getPieceAt(capturedPawnSquare)
                    moves.add(Move(square, captureSquare, piece, capturedPawn, null, false, true))
                }
            }
        }

        return moves
    }

    /**
     * Génère les coups possibles pour un cavalier
     */
    private fun generateKnightMoves(square: Square, piece: Piece): List<Move> {
        val moves = mutableListOf<Move>()
        val knightOffsets = listOf(
            Pair(-2, -1), Pair(-2, 1), Pair(-1, -2), Pair(-1, 2),
            Pair(1, -2), Pair(1, 2), Pair(2, -1), Pair(2, 1)
        )

        for ((rowOffset, colOffset) in knightOffsets) {
            val target = Square(square.row + rowOffset, square.col + colOffset)
            if (target.isValid()) {
                val targetPiece = getPieceAt(target)
                if (targetPiece == null || targetPiece.color != piece.color) {
                    moves.add(Move(square, target, piece, targetPiece))
                }
            }
        }

        return moves
    }

    /**
     * Génère les coups pour les pièces glissantes (fou, tour, dame)
     */
    private fun generateSlidingMoves(
        square: Square,
        piece: Piece,
        directions: List<Pair<Int, Int>>
    ): List<Move> {
        val moves = mutableListOf<Move>()

        for ((rowDir, colDir) in directions) {
            var currentRow = square.row + rowDir
            var currentCol = square.col + colDir

            while (currentRow in 0..7 && currentCol in 0..7) {
                val target = Square(currentRow, currentCol)
                val targetPiece = getPieceAt(target)

                if (targetPiece == null) {
                    moves.add(Move(square, target, piece))
                } else {
                    if (targetPiece.color != piece.color) {
                        moves.add(Move(square, target, piece, targetPiece))
                    }
                    break // Bloqué par une pièce
                }

                currentRow += rowDir
                currentCol += colDir
            }
        }

        return moves
    }

    /**
     * Génère les coups possibles pour un fou
     */
    private fun generateBishopMoves(square: Square, piece: Piece): List<Move> {
        val diagonals = listOf(Pair(-1, -1), Pair(-1, 1), Pair(1, -1), Pair(1, 1))
        return generateSlidingMoves(square, piece, diagonals)
    }

    /**
     * Génère les coups possibles pour une tour
     */
    private fun generateRookMoves(square: Square, piece: Piece): List<Move> {
        val orthogonals = listOf(Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1))
        return generateSlidingMoves(square, piece, orthogonals)
    }

    /**
     * Génère les coups possibles pour une dame
     */
    private fun generateQueenMoves(square: Square, piece: Piece): List<Move> {
        val allDirections = listOf(
            Pair(-1, -1), Pair(-1, 0), Pair(-1, 1),
            Pair(0, -1), Pair(0, 1),
            Pair(1, -1), Pair(1, 0), Pair(1, 1)
        )
        return generateSlidingMoves(square, piece, allDirections)
    }

    /**
     * Génère les coups possibles pour un roi
     */
    private fun generateKingMoves(square: Square, piece: Piece): List<Move> {
        val moves = mutableListOf<Move>()

        // Coups normaux (une case dans toutes les directions)
        for (rowOffset in -1..1) {
            for (colOffset in -1..1) {
                if (rowOffset == 0 && colOffset == 0) continue

                val target = Square(square.row + rowOffset, square.col + colOffset)
                if (target.isValid()) {
                    val targetPiece = getPieceAt(target)
                    if (targetPiece == null || targetPiece.color != piece.color) {
                        moves.add(Move(square, target, piece, targetPiece))
                    }
                }
            }
        }

        // Roque
        moves.addAll(generateCastlingMoves(square, piece))

        return moves
    }

    /**
     * Génère les coups de roque possibles
     */
    private fun generateCastlingMoves(square: Square, piece: Piece): List<Move> {
        val moves = mutableListOf<Move>()

        if (isInCheck) return moves // Impossible de roquer en échec

        val canCastleKingside = if (piece.color == PieceColor.WHITE) {
            !whiteKingMoved && !whiteRookKingsideMoved
        } else {
            !blackKingMoved && !blackRookKingsideMoved
        }

        val canCastleQueenside = if (piece.color == PieceColor.WHITE) {
            !whiteKingMoved && !whiteRookQueensideMoved
        } else {
            !blackKingMoved && !blackRookQueensideMoved
        }

        // Petit roque (kingside)
        if (canCastleKingside) {
            val path = listOf(Square(square.row, 5), Square(square.row, 6))
            if (path.all { getPieceAt(it) == null && !isSquareAttacked(it, piece.color.opposite()) }) {
                moves.add(Move(square, Square(square.row, 6), piece, null, null, true))
            }
        }

        // Grand roque (queenside)
        if (canCastleQueenside) {
            val emptyPath = listOf(Square(square.row, 1), Square(square.row, 2), Square(square.row, 3))
            val safePath = listOf(Square(square.row, 2), Square(square.row, 3))
            if (emptyPath.all { getPieceAt(it) == null } &&
                safePath.all { !isSquareAttacked(it, piece.color.opposite()) }) {
                moves.add(Move(square, Square(square.row, 2), piece, null, null, true))
            }
        }

        return moves
    }

    /**
     * Vérifie si une case est attaquée par une couleur
     * Méthode directe sans récursion pour éviter les problèmes avec generateKingMoves
     */
    private fun isSquareAttacked(square: Square, byColor: PieceColor): Boolean {
        // Vérifier les attaques de pions
        val pawnDirection = if (byColor == PieceColor.WHITE) -1 else 1
        for (colOffset in listOf(-1, 1)) {
            val pawnSquare = Square(square.row - pawnDirection, square.col + colOffset)
            if (pawnSquare.isValid()) {
                val piece = getPieceAt(pawnSquare)
                if (piece?.type == PieceType.PAWN && piece.color == byColor) {
                    return true
                }
            }
        }

        // Vérifier les attaques de cavaliers
        val knightMoves = listOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((rowOffset, colOffset) in knightMoves) {
            val knightSquare = Square(square.row + rowOffset, square.col + colOffset)
            if (knightSquare.isValid()) {
                val piece = getPieceAt(knightSquare)
                if (piece?.type == PieceType.KNIGHT && piece.color == byColor) {
                    return true
                }
            }
        }

        // Vérifier les attaques en diagonale (fou, dame)
        val diagonalDirections = listOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for ((rowDir, colDir) in diagonalDirections) {
            var distance = 1
            while (true) {
                val targetSquare = Square(square.row + rowDir * distance, square.col + colDir * distance)
                if (!targetSquare.isValid()) break

                val piece = getPieceAt(targetSquare)
                if (piece != null) {
                    if (piece.color == byColor &&
                        (piece.type == PieceType.BISHOP || piece.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                distance++
            }
        }

        // Vérifier les attaques orthogonales (tour, dame)
        val orthogonalDirections = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((rowDir, colDir) in orthogonalDirections) {
            var distance = 1
            while (true) {
                val targetSquare = Square(square.row + rowDir * distance, square.col + colDir * distance)
                if (!targetSquare.isValid()) break

                val piece = getPieceAt(targetSquare)
                if (piece != null) {
                    if (piece.color == byColor &&
                        (piece.type == PieceType.ROOK || piece.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                distance++
            }
        }

        // Vérifier les attaques du roi (une case autour)
        for (rowOffset in -1..1) {
            for (colOffset in -1..1) {
                if (rowOffset == 0 && colOffset == 0) continue

                val kingSquare = Square(square.row + rowOffset, square.col + colOffset)
                if (kingSquare.isValid()) {
                    val piece = getPieceAt(kingSquare)
                    if (piece?.type == PieceType.KING && piece.color == byColor) {
                        return true
                    }
                }
            }
        }

        return false
    }

    /**
     * Vérifie si le roi d'une couleur est en échec
     */
    private fun isKingInCheck(color: PieceColor): Boolean {
        val kingSquare = findKing(color) ?: return false
        return isSquareAttacked(kingSquare, color.opposite())
    }

    /**
     * Vérifie si un coup laisse le roi en échec (coup illégal)
     */
    private fun leavesKingInCheck(move: Move): Boolean {
        // Faire le coup temporairement
        val originalPiece = board[move.to.row][move.to.col]
        val movingPiece = board[move.from.row][move.from.col]

        // Gérer en passant
        var capturedEnPassant: Piece? = null
        if (move.isEnPassant) {
            val capturedPawnRow = move.from.row
            val capturedPawnCol = move.to.col
            capturedEnPassant = board[capturedPawnRow][capturedPawnCol]
            board[capturedPawnRow][capturedPawnCol] = null
        }

        board[move.to.row][move.to.col] = movingPiece
        board[move.from.row][move.from.col] = null

        val inCheck = isKingInCheck(move.piece.color)

        // Annuler le coup
        board[move.from.row][move.from.col] = movingPiece
        board[move.to.row][move.to.col] = originalPiece

        if (move.isEnPassant && capturedEnPassant != null) {
            val capturedPawnRow = move.from.row
            val capturedPawnCol = move.to.col
            board[capturedPawnRow][capturedPawnCol] = capturedEnPassant
        }

        return inCheck
    }

    /**
     * Génère tous les coups légaux pour une case
     */
    fun getLegalMoves(square: Square): List<Move> {
        val piece = getPieceAt(square) ?: return emptyList()
        if (piece.color != currentTurn) return emptyList()

        val pseudoLegalMoves = generatePseudoLegalMoves(square, piece)

        // Filtrer les coups qui laissent le roi en échec
        return pseudoLegalMoves.filterNot { leavesKingInCheck(it) }
    }

    /**
     * Génère tous les coups légaux pour une couleur
     */
    fun getAllLegalMoves(color: PieceColor): List<Move> {
        val moves = mutableListOf<Move>()
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = getPieceAt(row, col)
                if (piece != null && piece.color == color) {
                    moves.addAll(getLegalMoves(Square(row, col)))
                }
            }
        }
        return moves
    }

    /**
     * Met à jour le statut du jeu (échec, mat, pat)
     */
    private fun updateGameStatus() {
        isInCheck = isKingInCheck(currentTurn)
        val legalMoves = getAllLegalMoves(currentTurn)

        if (legalMoves.isEmpty()) {
            if (isInCheck) {
                isCheckmate = true
            } else {
                isStalemate = true
            }
        } else {
            isCheckmate = false
            isStalemate = false
        }
    }

    /**
     * Exécute un coup
     * @return true si le coup est légal et a été exécuté
     */
    fun makeMove(move: Move): Boolean {
        // Vérifier que le coup est légal
        val legalMoves = getLegalMoves(move.from)
        if (!legalMoves.contains(move)) return false

        // Récupérer la pièce
        val piece = board[move.from.row][move.from.col] ?: return false

        // Exécuter le coup
        board[move.to.row][move.to.col] = if (move.promotionType != null) {
            Piece(move.promotionType, piece.color)
        } else {
            piece
        }
        board[move.from.row][move.from.col] = null

        // Gérer le roque
        if (move.isCastling) {
            executeCastling(move)
        }

        // Gérer en passant
        if (move.isEnPassant) {
            // Retirer le pion capturé
            val capturedPawnRow = move.from.row
            val capturedPawnCol = move.to.col
            board[capturedPawnRow][capturedPawnCol] = null
        }

        // Suivre les pièces capturées
        if (move.capturedPiece != null) {
            capturedPieces.add(move.capturedPiece)
        }

        // Mettre à jour la cible en passant
        if (piece.type == PieceType.PAWN && abs(move.to.row - move.from.row) == 2) {
            enPassantTarget = Square((move.from.row + move.to.row) / 2, move.from.col)
        } else {
            enPassantTarget = null
        }

        // Mettre à jour les droits de roque
        updateCastlingRights(move)

        // Ajouter à l'historique
        moveHistory.add(move)

        // Changer le tour
        currentTurn = currentTurn.opposite()

        // Mettre à jour le statut
        updateGameStatus()

        return true
    }

    /**
     * Exécute le roque (déplace la tour)
     */
    private fun executeCastling(move: Move) {
        val row = move.from.row
        if (move.to.col == 6) {
            // Petit roque : déplacer la tour de h vers f
            val rook = board[row][7]
            board[row][5] = rook
            board[row][7] = null
        } else {
            // Grand roque : déplacer la tour de a vers d
            val rook = board[row][0]
            board[row][3] = rook
            board[row][0] = null
        }
    }

    /**
     * Met à jour les droits de roque après un coup
     */
    private fun updateCastlingRights(move: Move) {
        if (move.piece.type == PieceType.KING) {
            if (move.piece.color == PieceColor.WHITE) {
                whiteKingMoved = true
            } else {
                blackKingMoved = true
            }
        }

        if (move.piece.type == PieceType.ROOK) {
            when (move.from) {
                Square(7, 0) -> whiteRookQueensideMoved = true
                Square(7, 7) -> whiteRookKingsideMoved = true
                Square(0, 0) -> blackRookQueensideMoved = true
                Square(0, 7) -> blackRookKingsideMoved = true
            }
        }

        // Si une tour est capturée, mettre à jour les droits
        if (move.capturedPiece?.type == PieceType.ROOK) {
            when (move.to) {
                Square(7, 0) -> whiteRookQueensideMoved = true
                Square(7, 7) -> whiteRookKingsideMoved = true
                Square(0, 0) -> blackRookQueensideMoved = true
                Square(0, 7) -> blackRookKingsideMoved = true
            }
        }
    }

    /**
     * Convertit la position actuelle en notation FEN
     */
    fun toFEN(): String {
        val position = StringBuilder()

        // Position des pièces
        for (row in 0..7) {
            var emptyCount = 0
            for (col in 0..7) {
                val piece = getPieceAt(row, col)
                if (piece == null) {
                    emptyCount++
                } else {
                    if (emptyCount > 0) {
                        position.append(emptyCount)
                        emptyCount = 0
                    }
                    position.append(piece.notation)
                }
            }
            if (emptyCount > 0) position.append(emptyCount)
            if (row < 7) position.append('/')
        }

        // Couleur active
        val activeColor = if (currentTurn == PieceColor.WHITE) "w" else "b"

        // Droits de roque
        val castling = getCastlingRights()

        // Cible en passant
        val enPassant = enPassantTarget?.toAlgebraic() ?: "-"

        // Compteur de demi-coups (simplifié à 0 pour cette implémentation)
        val halfmove = "0"

        // Numéro du coup complet
        val fullmove = (moveHistory.size / 2 + 1).toString()

        return "$position $activeColor $castling $enPassant $halfmove $fullmove"
    }

    /**
     * Charge une position depuis une notation FEN
     */
    fun fromFEN(fen: String) {
        val parts = fen.split(" ")
        if (parts.size < 4) return

        // Vider le plateau
        for (row in 0..7) {
            for (col in 0..7) {
                board[row][col] = null
            }
        }

        // Parser la position
        val rows = parts[0].split("/")
        for ((rowIndex, rowStr) in rows.withIndex()) {
            var colIndex = 0
            for (char in rowStr) {
                if (char.isDigit()) {
                    colIndex += char.digitToInt()
                } else {
                    val color = if (char.isUpperCase()) PieceColor.WHITE else PieceColor.BLACK
                    val type = when (char.lowercaseChar()) {
                        'p' -> PieceType.PAWN
                        'n' -> PieceType.KNIGHT
                        'b' -> PieceType.BISHOP
                        'r' -> PieceType.ROOK
                        'q' -> PieceType.QUEEN
                        'k' -> PieceType.KING
                        else -> null
                    }
                    if (type != null && colIndex in 0..7) {
                        board[rowIndex][colIndex] = Piece(type, color)
                    }
                    colIndex++
                }
            }
        }

        // Parser la couleur active
        currentTurn = if (parts[1] == "w") PieceColor.WHITE else PieceColor.BLACK

        // Parser les droits de roque
        val castling = parts[2]
        whiteKingMoved = !castling.contains('K') && !castling.contains('Q')
        blackKingMoved = !castling.contains('k') && !castling.contains('q')
        whiteRookKingsideMoved = !castling.contains('K')
        whiteRookQueensideMoved = !castling.contains('Q')
        blackRookKingsideMoved = !castling.contains('k')
        blackRookQueensideMoved = !castling.contains('q')

        // Parser en passant
        enPassantTarget = if (parts[3] != "-") Square.fromAlgebraic(parts[3]) else null

        // Mettre à jour le statut
        updateGameStatus()
    }

    /**
     * Retourne les droits de roque au format FEN
     */
    fun getCastlingRights(): String {
        val rights = StringBuilder()
        if (!whiteKingMoved && !whiteRookKingsideMoved) rights.append('K')
        if (!whiteKingMoved && !whiteRookQueensideMoved) rights.append('Q')
        if (!blackKingMoved && !blackRookKingsideMoved) rights.append('k')
        if (!blackKingMoved && !blackRookQueensideMoved) rights.append('q')
        return if (rights.isEmpty()) "-" else rights.toString()
    }

    /**
     * Crée une copie profonde du jeu
     */
    fun clone(): ChessGame {
        val copy = ChessGame()
        copy.fromFEN(this.toFEN())
        // Copier l'historique et les pièces capturées
        copy.moveHistory.clear()
        copy.moveHistory.addAll(this.moveHistory)
        copy.capturedPieces.clear()
        copy.capturedPieces.addAll(this.capturedPieces)
        return copy
    }
}
