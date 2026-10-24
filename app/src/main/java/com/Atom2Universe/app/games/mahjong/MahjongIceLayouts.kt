package com.Atom2Universe.app.games.mahjong

/**
 * Curated ice layouts: each consecutive pair of cells is one face, cell = y * 6 + x.
 * Pairs are ordered backwards from a legal full solution, so every even prefix
 * also has a legal finish. Generation rechecks the quality after transformations
 * and mutations; these layouts keep its work bounded on slower phones.
 *
 * Normal/hard layouts have one border pair and interior diagonal pairs that need
 * other tiles as stopping blocks. The denser legacy layouts retain old tile IDs.
 */
internal object MahjongIceLayouts {
    val normal: List<IntArray> = listOf(
        intArrayOf(4, 34, 21, 26, 10, 13, 20, 7, 28, 14, 22, 27, 16, 25, 9, 19),
        intArrayOf(24, 33, 13, 22, 15, 28, 21, 25, 27, 8, 14, 10, 9, 26, 16, 20),
        intArrayOf(0, 31, 28, 20, 19, 10, 25, 14, 16, 8, 9, 22, 21, 26, 13, 27),
        intArrayOf(2, 24, 13, 26, 22, 25, 14, 19, 27, 20, 21, 10, 7, 15, 28, 9),
        intArrayOf(1, 35, 14, 27, 16, 7, 20, 10, 15, 19, 25, 9, 8, 21, 13, 22),
        intArrayOf(12, 33, 10, 25, 13, 20, 14, 9, 15, 28, 19, 16, 8, 27, 7, 21),
        intArrayOf(11, 1, 22, 26, 21, 25, 20, 10, 13, 9, 8, 19, 28, 14, 27, 16),
        intArrayOf(6, 3, 8, 28, 27, 22, 19, 9, 26, 15, 7, 21, 20, 16, 25, 14)
    )

    val hard: List<IntArray> = listOf(
        intArrayOf(43, 41, 10, 26, 32, 27, 9, 13, 31, 20, 19, 14, 37, 15, 21, 16, 38, 7, 39, 25),
        intArrayOf(11, 43, 14, 25, 40, 9, 10, 31, 16, 7, 26, 13, 27, 37, 33, 19, 20, 15, 32, 21),
        intArrayOf(6, 23, 10, 13, 7, 39, 25, 8, 9, 14, 16, 31, 20, 15, 21, 40, 27, 34, 22, 37),
        intArrayOf(36, 46, 13, 38, 28, 32, 26, 39, 21, 7, 8, 16, 40, 15, 27, 20, 19, 10, 25, 9),
        intArrayOf(17, 12, 9, 31, 14, 34, 25, 32, 28, 8, 37, 22, 33, 38, 26, 40, 39, 13, 27, 10),
        intArrayOf(43, 35, 37, 26, 19, 34, 13, 38, 15, 40, 20, 39, 33, 10, 9, 22, 21, 32, 14, 28),
        intArrayOf(17, 1, 16, 21, 9, 13, 8, 37, 15, 25, 14, 40, 38, 31, 39, 32, 26, 34, 33, 7),
        intArrayOf(23, 1, 15, 37, 7, 40, 27, 34, 13, 39, 10, 19, 38, 33, 14, 9, 21, 8, 16, 20)
    )

    val legacyNormal: List<IntArray> = listOf(
        intArrayOf(7, 30, 4, 18, 0, 20, 16, 12, 14, 35, 34, 29, 10, 13, 19, 33, 23, 32, 1, 22, 28, 2, 31, 21),
        intArrayOf(25, 30, 24, 11, 12, 0, 18, 6, 1, 23, 26, 31, 9, 32, 33, 19, 20, 2, 8, 21, 22, 27, 7, 4)
    )

    val legacyHard: List<IntArray> = listOf(
        intArrayOf(37, 30, 16, 3, 13, 39, 22, 47, 41, 27, 35, 45, 40, 26, 21, 4, 2, 34, 24, 42, 38, 17, 15, 6, 32, 1, 7, 19, 31, 25, 11, 5),
        intArrayOf(24, 7, 45, 3, 10, 27, 16, 9, 5, 40, 11, 14, 17, 12, 15, 19, 42, 21, 39, 31, 20, 22, 36, 29, 28, 47, 2, 8, 25, 37, 26, 33),
        intArrayOf(37, 19, 45, 36, 42, 15, 43, 14, 47, 22, 1, 25, 5, 18, 4, 39, 34, 0, 46, 10, 24, 44, 11, 40, 23, 16, 35, 20, 6, 3, 12, 2)
    )
}
