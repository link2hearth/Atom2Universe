package com.Atom2Universe.app.games.chess.ai

import android.content.Context
import android.util.Log
import com.Atom2Universe.app.games.chess.*
import karballo.Board
import karballo.Config
import karballo.search.SearchEngine
import karballo.search.SearchParameters
import karballo.book.Book
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.util.Random

/**
 * Adapter pour utiliser le moteur Karballo avec ChessGame
 * Convertit les positions FEN et les coups entre les deux systèmes
 */
class KarballoAdapter(
    private val context: Context? = null
) {
    private val config = Config().apply {
        transpositionTableSize = 16 // 16 MB pour Android (réduit pour la mémoire)
        evaluator = "complete"
        useBook = false // Désactivé temporairement pour débogage
    }

    private val searchEngine = SearchEngine(config)

    @Volatile
    private var isCancelled = false

    /**
     * Cherche le meilleur coup pour la position actuelle
     * @param game Le jeu d'échecs actuel
     * @param depth Profondeur de recherche
     * @param timeLimitMs Limite de temps en ms
     * @return Le meilleur coup trouvé, ou null si aucun coup légal
     */
    fun findBestMove(
        game: ChessGame,
        depth: Int,
        timeLimitMs: Long
    ): Move? {
        isCancelled = false

        // Convertir la position en FEN et la charger dans Karballo
        val fen = game.toFEN()
        Log.d(TAG, "Recherche pour FEN: $fen, temps: ${timeLimitMs}ms")

        searchEngine.board.fen = fen
        Log.d(TAG, "Position chargée dans Karballo")

        // Configurer les paramètres de recherche
        // IMPORTANT: Ne PAS définir depth sinon le temps est ignoré par Karballo!
        // On utilise seulement moveTime pour limiter la recherche
        val searchParams = SearchParameters().apply {
            this.moveTime = timeLimitMs.toInt()
        }

        // Lancer la recherche (bloquante)
        Log.d(TAG, "Lancement de la recherche... isSearching=${searchEngine.isSearching}")

        // Si une recherche est en cours, l'arrêter d'abord
        if (searchEngine.isSearching) {
            Log.w(TAG, "Une recherche est déjà en cours, arrêt...")
            searchEngine.stop()
            Thread.sleep(100) // Attendre un peu
        }

        val startTime = System.currentTimeMillis()
        searchEngine.go(searchParams)
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Recherche terminée en ${elapsed}ms")

        // Récupérer le meilleur coup directement depuis le SearchEngine
        val bestMoveInt = searchEngine.bestMove
        Log.d(TAG, "Meilleur coup trouvé: ${karballo.Move.toString(bestMoveInt)}")

        // Convertir le coup Karballo en coup ChessGame
        return if (bestMoveInt != karballo.Move.NONE) {
            val move = convertKarballoMoveToChessMove(game, bestMoveInt)
            Log.d(TAG, "Coup converti: ${move?.from} -> ${move?.to}")
            move
        } else {
            Log.w(TAG, "Aucun coup trouvé!")
            null
        }
    }

    companion object {
        private const val TAG = "KarballoAdapter"
    }

    /**
     * Annule la recherche en cours
     */
    fun cancel() {
        isCancelled = true
        searchEngine.stop()
    }

    /**
     * Vide la table de transposition
     */
    fun clear() {
        searchEngine.clear()
    }

    /**
     * Convertit un coup Karballo en coup ChessGame
     */
    private fun convertKarballoMoveToChessMove(game: ChessGame, karballoMove: Int): Move? {
        // Utiliser la notation algébrique de Karballo directement
        val moveStr = karballo.Move.toString(karballoMove)
        Log.d(TAG, "Conversion du coup: $moveStr")

        if (moveStr.length < 4) {
            Log.e(TAG, "Coup invalide: $moveStr")
            return null
        }

        // Parser la notation algébrique (ex: "e2e4", "g1f3", "e7e8q")
        val fromCol = moveStr[0] - 'a'  // a=0, b=1, ..., h=7
        val fromRow = 8 - (moveStr[1] - '0')  // 1->7, 2->6, ..., 8->0
        val toCol = moveStr[2] - 'a'
        val toRow = 8 - (moveStr[3] - '0')

        Log.d(TAG, "Parsed: from=($fromRow,$fromCol) to=($toRow,$toCol)")

        val from = Square(fromRow, fromCol)
        val to = Square(toRow, toCol)

        // Récupérer la pièce qui bouge
        val piece = game.getPieceAt(from)
        if (piece == null) {
            Log.e(TAG, "Aucune pièce trouvée à from=($fromRow,$fromCol)")
            Log.e(TAG, "FEN: ${game.toFEN()}")
            return null
        }

        // Vérifier le type de promotion depuis la notation (5ème caractère: q, r, b, n)
        val promotionType = if (moveStr.length >= 5) {
            when (moveStr[4]) {
                'q' -> PieceType.QUEEN
                'r' -> PieceType.ROOK
                'b' -> PieceType.BISHOP
                'n' -> PieceType.KNIGHT
                else -> null
            }
        } else null

        // Chercher le coup correspondant dans les coups légaux
        val legalMoves = game.getLegalMoves(from)

        // D'abord chercher avec le type de promotion si spécifié
        val move = legalMoves.find { m ->
            m.to == to && (promotionType == null || m.promotionType == promotionType)
        } ?: legalMoves.find { m -> m.to == to }

        Log.d(TAG, "Coup trouvé: ${move?.from} -> ${move?.to}")
        return move
    }

    /**
     * Book d'ouvertures pour Android (lit depuis assets)
     */
    private class AndroidBook(private val context: Context) : Book {
        private val moves = mutableListOf<Int>()
        private val weights = mutableListOf<Int>()
        private var totalWeight = 0L
        private val random = Random()

        private fun int2MoveString(move: Int): String {
            val sb = StringBuilder()
            sb.append(('a' + (move shr 6 and 0x7)))
            sb.append((move shr 9 and 0x7) + 1)
            sb.append(('a' + (move and 0x7)))
            sb.append((move shr 3 and 0x7) + 1)
            if (move shr 12 and 0x7 != 0) {
                sb.append("nbrq"[(move shr 12 and 0x7) - 1])
            }
            return sb.toString()
        }

        private fun generateMoves(board: Board) {
            totalWeight = 0
            moves.clear()
            weights.clear()

            val key2Find = board.getKey()

            try {
                DataInputStream(BufferedInputStream(context.assets.open("chess/book.bin"))).use { dataInputStream ->
                    while (true) {
                        val key = dataInputStream.readLong()
                        if (key == key2Find) {
                            val moveInt = dataInputStream.readShort().toInt()
                            val weight = dataInputStream.readShort().toInt()
                            dataInputStream.readInt() // Unused learn field

                            val move = karballo.Move.getFromString(board, int2MoveString(moveInt), true)
                            if (board.getLegalMove(move) != karballo.Move.NONE) {
                                moves.add(move)
                                weights.add(weight)
                                totalWeight += weight.toLong()
                            }
                        } else {
                            dataInputStream.skipBytes(8)
                        }
                    }
                }
            } catch (e: Exception) {
                // EOFException normale en fin de fichier ; autres erreurs ignorées
            }
        }

        override fun getMove(board: Board): Int {
            generateMoves(board)
            var randomWeight = (random.nextFloat() * totalWeight).toLong()
            for (i in moves.indices) {
                randomWeight -= weights[i].toLong()
                if (randomWeight <= 0) {
                    return moves[i]
                }
            }
            return karballo.Move.NONE
        }
    }
}
