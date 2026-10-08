package karballo

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Garde-fou du moteur d'echecs : le « perft » compte toutes les suites de coups legaux
 * jusqu'a une profondeur donnee. Les valeurs ci-dessous sont les references publiees par
 * la communaute des echecs (chessprogramming.org/Perft_Results) ; si un seul coup est
 * genere en trop ou en moins (roque, prise en passant, promotion, clouage...) le total
 * devient faux des la profondeur 3 ou 4.
 *
 * Utile le jour ou l'on touche au moteur embarque (karballo/) ou a l'IA des echecs.
 */
class KarballoPerftTest {

    private fun perft(board: Board, depth: Int): Long {
        if (depth == 0) return 1
        val moves = IntArray(256)
        val count = board.getLegalMoves(moves)
        if (depth == 1) return count.toLong()
        var total = 0L
        for (i in 0 until count) {
            if (board.doMove(moves[i], verifyCheck = false, fillSanInfo = false)) {
                total += perft(board, depth - 1)
                board.undoMove()
            }
        }
        return total
    }

    private fun check(fen: String, vararg expected: Long) {
        val board = Board()
        board.fen = fen
        expected.forEachIndexed { i, nodes ->
            assertEquals("$fen a la profondeur ${i + 1}", nodes, perft(board, i + 1))
        }
        assertEquals("le plateau doit revenir intact apres le perft", fen.substringBefore(' '), board.fen.substringBefore(' '))
    }

    @Test fun positionInitiale() =
        check(Board.FEN_START_POSITION, 20, 400, 8902, 197281)

    /** « Kiwipete » : roques des deux cotes, prises, clouages, promotions a portee. */
    @Test fun kiwipete() =
        check("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1", 48, 2039, 97862)

    /** Finale de pions et tours : prise en passant et echecs decouverts. */
    @Test fun finaleEnPassant() =
        check("8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1", 14, 191, 2812, 43238)

    /** Promotions avec capture et roques qui traversent une case attaquee. */
    @Test fun promotions() =
        check("r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1", 6, 264, 9467)
}
