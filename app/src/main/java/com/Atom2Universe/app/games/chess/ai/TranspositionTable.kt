package com.Atom2Universe.app.games.chess.ai

import com.Atom2Universe.app.games.chess.*
import kotlin.random.Random

/**
 * Type de score dans la table de transposition
 */
enum class ScoreType {
    EXACT,       // Score exact
    LOWER_BOUND, // Alpha cutoff (score >= valeur stockée)
    UPPER_BOUND  // Beta cutoff (score <= valeur stockée)
}

/**
 * Entrée dans la table de transposition
 */
data class TranspositionEntry(
    val zobristHash: Long,
    val depth: Int,
    val score: Int,
    val bestMove: Move?,
    val flag: ScoreType
)

/**
 * Table de transposition pour mémoriser les positions déjà évaluées.
 * Tableau fixe indexé par (hash & mask) — accès O(1) sans allocation ni éviction O(n).
 * sizePow2 doit être une puissance de 2 (défaut 65536 ≈ 2 Mo).
 */
class TranspositionTable(sizePow2: Int = 65536) {
    private val mask = (sizePow2 - 1).toLong()
    private val table = arrayOfNulls<TranspositionEntry>(sizePow2)

    fun store(hash: Long, depth: Int, score: Int, bestMove: Move?, flag: ScoreType) {
        val index = (hash and mask).toInt()
        val existing = table[index]
        // Remplace si : slot vide, même position, ou recherche plus profonde
        if (existing == null || existing.zobristHash == hash || depth >= existing.depth) {
            table[index] = TranspositionEntry(hash, depth, score, bestMove, flag)
        }
    }

    fun probe(hash: Long): TranspositionEntry? {
        val index = (hash and mask).toInt()
        val entry = table[index] ?: return null
        return if (entry.zobristHash == hash) entry else null
    }

    fun clear() = table.fill(null)

    fun size(): Int = table.count { it != null }
}

/**
 * Hachage Zobrist pour identifier de manière unique les positions d'échecs
 * Utilise des nombres aléatoires pour chaque pièce sur chaque case
 */
object ZobristHash {
    // Table de hachage : [row][col][pieceIndex]
    // pieceIndex: 0-5 = pièces blanches (PAWN à KING), 6-11 = pièces noires
    private val pieceHashTable = Array(8) { Array(8) { LongArray(12) {
        Random.nextLong()
    } } }

    private val blackToMoveHash = Random.nextLong()
    private val castlingRightsHash = LongArray(4) { Random.nextLong() } // WK, WQ, BK, BQ
    private val enPassantHash = LongArray(8) { Random.nextLong() } // Pour chaque colonne

    /**
     * Calcule le hash Zobrist pour une position
     */
    fun compute(game: ChessGame): Long {
        var hash = 0L

        // Hasher toutes les pièces
        for (row in 0..7) {
            for (col in 0..7) {
                val piece = game.getPieceAt(row, col)
                if (piece != null) {
                    val pieceIndex = getPieceIndex(piece)
                    hash = hash xor pieceHashTable[row][col][pieceIndex]
                }
            }
        }

        // Hasher le tour
        if (game.currentTurn == PieceColor.BLACK) {
            hash = hash xor blackToMoveHash
        }

        // Hasher les droits de roque
        val castlingRights = game.getCastlingRights()
        if (castlingRights.contains('K')) hash = hash xor castlingRightsHash[0]
        if (castlingRights.contains('Q')) hash = hash xor castlingRightsHash[1]
        if (castlingRights.contains('k')) hash = hash xor castlingRightsHash[2]
        if (castlingRights.contains('q')) hash = hash xor castlingRightsHash[3]

        // Hasher en passant
        val enPassantTarget = game.enPassantTarget
        if (enPassantTarget != null) {
            hash = hash xor enPassantHash[enPassantTarget.col]
        }

        return hash
    }

    /**
     * Retourne l'index d'une pièce pour le hachage
     * 0-5: pièces blanches, 6-11: pièces noires
     */
    private fun getPieceIndex(piece: Piece): Int {
        val typeIndex = when (piece.type) {
            PieceType.PAWN -> 0
            PieceType.KNIGHT -> 1
            PieceType.BISHOP -> 2
            PieceType.ROOK -> 3
            PieceType.QUEEN -> 4
            PieceType.KING -> 5
        }
        return if (piece.color == PieceColor.WHITE) typeIndex else typeIndex + 6
    }
}
