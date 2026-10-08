package com.Atom2Universe.app.games.puzzles.flip

import kotlin.random.Random

/**
 * « Flip » de Simon Tatham : toucher une case retourne un motif de cases autour d'elle (une
 * croix, ou un motif tiré au hasard et dessiné dans la case). Il faut tout éteindre.
 *
 * [patterns] donne, pour chaque case, les cases qu'elle retourne. [lit] est l'état courant.
 */
class FlipState(
    val w: Int,
    val h: Int,
    val patterns: Array<IntArray>,
    val lit: BooleanArray,
    val moves: Int,
    /**
     * Les cases qu'il reste à toucher (une fois chacune) pour tout éteindre : celles du tirage,
     * corrigées par chaque appui du joueur (toucher deux fois une case revient à ne pas la toucher).
     */
    val needed: BooleanArray,
) {
    val isSolved: Boolean get() = lit.none { it }

    fun press(index: Int): FlipState {
        val next = lit.copyOf()
        for (j in patterns[index]) next[j] = !next[j]
        val need = needed.copyOf(); need[index] = !need[index]
        return FlipState(w, h, patterns, next, moves + 1, need)
    }

    /** Astuce : un appui de la solution. */
    fun hint(): FlipState? = needed.indices.filter { needed[it] }.randomOrNull()?.let { press(it) }

    fun encode(): String = buildString {
        append("$w,$h,$moves:")
        append(lit.joinToString("") { if (it) "1" else "0" })
        append(':')
        append(patterns.joinToString(";") { p -> p.joinToString(",") })
        append(':')
        append(needed.joinToString("") { if (it) "1" else "0" })
    }

    companion object {
        fun decode(text: String): FlipState? {
            val parts = text.split(':')
            if (parts.size != 4) return null
            val (w, h, moves) = parts[0].split(',').map { it.toInt() }
            val lit = BooleanArray(w * h) { parts[1][it] == '1' }
            val patterns = parts[2].split(';').map { p -> p.split(',').map { it.toInt() }.toIntArray() }
            if (patterns.size != w * h) return null
            return FlipState(w, h, patterns.toTypedArray(), lit, moves, BooleanArray(w * h) { parts[3][it] == '1' })
        }

        /** La croix : la case et ses quatre voisines. */
        fun crosses(w: Int, h: Int): Array<IntArray> = Array(w * h) { i ->
            val x = i % w
            val y = i / w
            buildList {
                add(i)
                if (x > 0) add(i - 1)
                if (x < w - 1) add(i + 1)
                if (y > 0) add(i - w)
                if (y < h - 1) add(i + w)
            }.toIntArray()
        }

        /**
         * Des motifs au hasard : la case elle-même, plus deux à quatre voisines (diagonales
         * comprises) qui forment avec elle une seule tache, pour que le motif se lise d'un coup
         * d'œil dans la case.
         */
        fun randomPatterns(w: Int, h: Int, random: Random): Array<IntArray> = Array(w * h) { i ->
            val x = i % w
            val y = i / w
            val inside = { dx: Int, dy: Int -> x + dx in 0 until w && y + dy in 0 until h }
            val shape = mutableSetOf(0 to 0)
            val target = 1 + 2 + random.nextInt(3)
            var guard = 0
            while (shape.size < target && guard++ < 100) {
                val (bx, by) = shape.random(random)
                val (dx, dy) = listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1).random(random)
                val nx = bx + dx
                val ny = by + dy
                if (nx in -1..1 && ny in -1..1 && inside(nx, ny)) shape.add(nx to ny)
            }
            shape.map { (dx, dy) -> (y + dy) * w + x + dx }.sorted().toIntArray()
        }

        /**
         * Partir de la grille éteinte et y appuyer au hasard garantit une grille faisable, et
         * donne toutes les grilles faisables avec la même chance (même raisonnement que Tatham).
         */
        fun generate(w: Int, h: Int, random: Boolean, rng: Random): FlipState {
            val patterns = if (random) randomPatterns(w, h, rng) else crosses(w, h)
            while (true) {
                val lit = BooleanArray(w * h)
                val pressed = BooleanArray(w * h) { rng.nextBoolean() }
                for (i in 0 until w * h) if (pressed[i]) for (j in patterns[i]) lit[j] = !lit[j]
                if (lit.any { it }) return FlipState(w, h, patterns, lit, 0, pressed)
            }
        }
    }
}
