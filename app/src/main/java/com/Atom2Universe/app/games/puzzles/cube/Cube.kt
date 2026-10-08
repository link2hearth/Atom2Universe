package com.Atom2Universe.app.games.puzzles.cube

import kotlin.random.Random

/**
 * « Cube » de Simon Tatham : un cube roule sur un damier où six cases sont colorées. Quand il
 * se pose sur une case, la couleur passe de la case à la face du dessous, ou de la face à la
 * case (elles s'échangent). Gagné quand les six faces sont colorées.
 *
 * [faces] : 0 dessus, 1 dessous, 2 nord, 3 sud, 4 est, 5 ouest (true = colorée).
 */
class CubeState(val w: Int, val h: Int, val x: Int, val y: Int, val faces: BooleanArray, val blue: BooleanArray, val moves: Int) {

    /** Faire rouler le cube d'une case ; null si on sortirait de la grille. */
    fun roll(dx: Int, dy: Int): CubeState? {
        val nx = x + dx; val ny = y + dy
        if (nx !in 0 until w || ny !in 0 until h) return null
        val f = faces.copyOf()
        val o = faces
        when {
            dx == 1 -> { f[TOP] = o[WEST]; f[EAST] = o[TOP]; f[BOTTOM] = o[EAST]; f[WEST] = o[BOTTOM] }
            dx == -1 -> { f[TOP] = o[EAST]; f[WEST] = o[TOP]; f[BOTTOM] = o[WEST]; f[EAST] = o[BOTTOM] }
            dy == -1 -> { f[TOP] = o[SOUTH]; f[NORTH] = o[TOP]; f[BOTTOM] = o[NORTH]; f[SOUTH] = o[BOTTOM] }
            else -> { f[TOP] = o[NORTH]; f[SOUTH] = o[TOP]; f[BOTTOM] = o[SOUTH]; f[NORTH] = o[BOTTOM] }
        }
        val b = blue.copyOf()
        val i = ny * w + nx
        if (f[BOTTOM] != b[i]) { val t = f[BOTTOM]; f[BOTTOM] = b[i]; b[i] = t }
        return CubeState(w, h, nx, ny, f, b, moves + 1)
    }

    val isSolved get() = faces.all { it }

    fun encode() = "$w,$h,$x,$y,$moves:" + faces.joinToString("") { if (it) "1" else "0" } + ":" +
        blue.joinToString("") { if (it) "1" else "0" }

    companion object {
        const val TOP = 0; const val BOTTOM = 1; const val NORTH = 2; const val SOUTH = 3; const val EAST = 4; const val WEST = 5

        fun decode(text: String): CubeState {
            val p = text.split(':')
            val (w, h, x, y, moves) = p[0].split(',').map { it.toInt() }
            return CubeState(w, h, x, y, BooleanArray(6) { p[1][it] == '1' }, BooleanArray(w * h) { p[2][it] == '1' }, moves)
        }

        fun generate(w: Int, h: Int, random: Random): CubeState {
            val start = random.nextInt(w * h)
            val blue = BooleanArray(w * h)
            (0 until w * h).filter { it != start }.shuffled(random).take(6).forEach { blue[it] = true }
            return CubeState(w, h, start % w, start / w, BooleanArray(6), blue, 0)
        }
    }
}
