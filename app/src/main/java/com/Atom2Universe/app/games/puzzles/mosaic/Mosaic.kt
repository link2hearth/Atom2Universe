package com.Atom2Universe.app.games.puzzles.mosaic

import com.Atom2Universe.app.games.kit.GridSearch
import kotlin.random.Random

/**
 * « Mosaic » de Simon Tatham : chaque nombre dit combien de cases sont noires dans le carré de
 * 3 × 3 dont il occupe le centre (lui compris). Retrouver le dessin.
 *
 * [clues] : -1 = pas de nombre. [cells] : 0 inconnu, 1 noir, 2 blanc (marqué). [picture] :
 * l'image du mode « Images » (PatternPictures), ou -1 pour une grille tirée au hasard.
 */
class MosaicState(
    val w: Int, val h: Int, val clues: IntArray, val cells: IntArray, val picture: Int = -1,
    /** Le dessin à retrouver (1 noir, 0 blanc), gardé pour les astuces ; vide si inconnu. */
    val solution: IntArray = IntArray(0),
) {

    fun with(i: Int, v: Int): MosaicState {
        val next = cells.copyOf(); next[i] = v
        return MosaicState(w, h, clues, next, picture, solution)
    }

    /** Astuce : corrige une case fausse, sinon révèle une case encore indécise. */
    fun hint(): MosaicState? {
        if (solution.size != w * h) return null
        fun want(i: Int) = if (solution[i] == 1) 1 else 2
        val wrong = cells.indices.filter { cells[it] != 0 && cells[it] != want(it) }
        val open = cells.indices.filter { cells[it] == 0 }
        val i = wrong.ifEmpty { open }.randomOrNull() ?: return null
        return with(i, want(i))
    }

    fun block(i: Int): List<Int> = blockOf(w, h, i)

    /** 1 = indice satisfait et bloc complet, -1 = contredit, 0 = en cours. */
    fun clueState(i: Int): Int {
        val b = block(i)
        val black = b.count { cells[it] == 1 }
        val open = b.count { cells[it] == 0 }
        return when {
            black > clues[i] || black + open < clues[i] -> -1
            open == 0 -> 1
            else -> 0
        }
    }

    /** La solution étant unique, des noires qui satisfont tous les nombres sont la solution. */
    val isSolved: Boolean
        get() = clues.indices.all { clues[it] < 0 || block(it).count { j -> cells[j] == 1 } == clues[it] }

    fun encode() = "$w,$h:" + clues.joinToString(",") + ":" + cells.joinToString("") + ":" + picture + ":" +
        solution.joinToString("")

    companion object {
        fun decode(text: String): MosaicState {
            val p = text.split(':')
            val (w, h) = p[0].split(',').map { it.toInt() }
            return MosaicState(w, h, p[1].split(',').map { it.toInt() }.toIntArray(), p[2].map { it - '0' }.toIntArray(), p[3].toInt(),
                p[4].map { it - '0' }.toIntArray())
        }

        fun blockOf(w: Int, h: Int, i: Int): List<Int> {
            val x = i % w; val y = i / w
            val out = ArrayList<Int>(9)
            for (dy in -1..1) for (dx in -1..1) {
                val nx = x + dx; val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) out.add(ny * w + nx)
            }
            return out
        }

        fun generate(w: Int, h: Int, random: Random): MosaicState =
            fromDrawing(w, h, IntArray(w * h) { if (random.nextFloat() < 0.5f) 1 else 0 }, -1, random)

        /**
         * La grille d'une image dessinée : on part de tous les nombres et on en retire tant que la
         * grille se résout encore case par case. Le tirage est fixé par l'image : la même image
         * donne toujours la même grille.
         */
        fun fromPicture(index: Int): MosaicState {
            val p = com.Atom2Universe.app.games.puzzles.pattern.PatternPictures.all[index]
            // Quelques dessins restent ambigus même avec tous leurs nombres : une marge vide d'une
            // case tout autour ajoute des nombres de bord qui lèvent le doute.
            val all = IntArray(p.w * p.h) { i -> blockOf(p.w, p.h, i).count { p.filled(it) } }
            val margin = if (unique(p.w, p.h, all, 2_000)) 0 else 1
            val w = p.w + 2 * margin; val h = p.h + 2 * margin
            val drawing = IntArray(w * h) { i ->
                val x = i % w - margin; val y = i / w - margin
                if (x in 0 until p.w && y in 0 until p.h && p.filled(y * p.w + x)) 1 else 0
            }
            return fromDrawing(w, h, drawing, index, Random(index + 1L))
        }

        /** La case de l'image sous la case [i] de la grille (marge éventuelle), ou -1 dans la marge. */
        fun pictureCell(state: MosaicState, i: Int): Int {
            val p = com.Atom2Universe.app.games.puzzles.pattern.PatternPictures.all[state.picture]
            val margin = (state.w - p.w) / 2
            val x = i % state.w - margin; val y = i / state.w - margin
            return if (x in 0 until p.w && y in 0 until p.h) y * p.w + x else -1
        }

        private fun fromDrawing(w: Int, h: Int, picture: IntArray, index: Int, random: Random): MosaicState {
            val clues = IntArray(w * h) { i -> blockOf(w, h, i).count { picture[it] == 1 } }
            // Quelques dessins ne se résolvent pas case par case même avec tous leurs nombres :
            // pour eux, on accepte une courte exploration, tant que la solution reste unique.
            val budget = if (propagates(w, h, clues)) 1 else 2_000
            for (i in (0 until w * h).shuffled(random)) {
                val keep = clues[i]
                clues[i] = -1
                if (!unique(w, h, clues, budget)) clues[i] = keep
            }
            return MosaicState(w, h, clues, IntArray(w * h), index, picture.copyOf())
        }

        /** Vrai si ces nombres se résolvent case par case, sans essai (donc une seule solution). */
        fun propagates(w: Int, h: Int, clues: IntArray) = unique(w, h, clues, 1)

        /** Vrai si l'unicité se prouve en au plus [budget] nœuds de recherche (1 = sans essai). */
        fun unique(w: Int, h: Int, clues: IntArray, budget: Int): Boolean {
            val blocks = Array(w * h) { blockOf(w, h, it).toIntArray() }
            fun ok(g: IntArray, c: Int): Boolean {
                for (k in blocks[c]) {
                    if (clues[k] < 0) continue
                    var on = 0; var unk = 0
                    for (j in blocks[k]) if (g[j] == 1) on++ else if (g[j] < 0) unk++
                    if (on > clues[k] || on + unk < clues[k]) return false
                }
                return true
            }
            return GridSearch.count(w * h, 2, IntArray(w * h) { -1 }, budget = budget) { g, c -> ok(g, c) } == 1
        }
    }
}
