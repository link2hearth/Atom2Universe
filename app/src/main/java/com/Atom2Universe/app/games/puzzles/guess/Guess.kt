package com.Atom2Universe.app.games.puzzles.guess

import com.Atom2Universe.app.games.kit.Hints
import kotlin.random.Random

/**
 * « Guess » de Simon Tatham, le Mastermind : deviner une suite de [pegs] couleurs cachée. Après
 * chaque essai, un pion plein dit « bonne couleur, bonne place », un pion creux « bonne couleur,
 * mauvaise place ».
 *
 * [row] est l'essai en cours de composition (-1 = vide), [cursor] la case qui recevra la
 * prochaine couleur.
 */
class GuessState(
    val pegs: Int,
    val colours: Int,
    val maxGuesses: Int,
    val secret: IntArray,
    val guesses: List<IntArray>,
    val row: IntArray,
    val cursor: Int,
) {
    val isSolved: Boolean get() = guesses.lastOrNull()?.contentEquals(secret) == true
    val isLost: Boolean get() = !isSolved && guesses.size >= maxGuesses

    fun put(colour: Int): GuessState {
        val next = row.copyOf()
        next[cursor] = colour
        val empty = (1..pegs).map { (cursor + it) % pegs }.firstOrNull { next[it] < 0 } ?: cursor
        return GuessState(pegs, colours, maxGuesses, secret, guesses, next, empty)
    }

    fun select(slot: Int): GuessState {
        val next = row.copyOf()
        if (slot == cursor) next[slot] = -1
        return GuessState(pegs, colours, maxGuesses, secret, guesses, next, slot)
    }

    /**
     * Astuce : une couleur du secret, posée à sa place dans la rangée en cours (d'abord sur une
     * case fausse, sinon sur une vide).
     */
    fun hint(): GuessState? {
        val k = Hints.pick(pegs, { row[it] >= 0 && row[it] != secret[it] }, { row[it] < 0 }) ?: return null
        val next = row.copyOf(); next[k] = secret[k]
        return GuessState(pegs, colours, maxGuesses, secret, guesses, next, cursor)
    }

    val canSubmit: Boolean get() = row.all { it >= 0 }

    fun submit(): GuessState? {
        if (!canSubmit) return null
        return GuessState(pegs, colours, maxGuesses, secret, guesses + listOf(row.copyOf()), IntArray(pegs) { -1 }, 0)
    }

    fun encode(): String = buildString {
        append("$pegs,$colours,$maxGuesses,$cursor:")
        append(secret.joinToString("")).append(':')
        append(row.joinToString("") { if (it < 0) "." else it.toString() }).append(':')
        append(guesses.joinToString(";") { g -> g.joinToString("") })
    }

    companion object {
        /** (bien placés, mal placés) d'un essai contre le secret. */
        fun score(secret: IntArray, guess: IntArray): Pair<Int, Int> {
            var black = 0
            val a = IntArray(16); val b = IntArray(16)
            for (i in secret.indices) {
                if (secret[i] == guess[i]) black++ else { a[secret[i]]++; b[guess[i]]++ }
            }
            return black to (0 until 16).sumOf { minOf(a[it], b[it]) }
        }

        fun decode(text: String): GuessState? {
            val parts = text.split(':')
            if (parts.size != 4) return null
            val (pegs, colours, max, cursor) = parts[0].split(',').map { it.toInt() }
            val secret = parts[1].map { it - '0' }.toIntArray()
            val row = parts[2].map { if (it == '.') -1 else it - '0' }.toIntArray()
            val guesses = if (parts[3].isEmpty()) emptyList() else parts[3].split(';').map { g -> g.map { it - '0' }.toIntArray() }
            return GuessState(pegs, colours, max, secret, guesses, row, cursor)
        }

        fun generate(pegs: Int, colours: Int, maxGuesses: Int, duplicates: Boolean, random: Random): GuessState {
            val secret = if (duplicates) IntArray(pegs) { random.nextInt(colours) }
            else (0 until colours).shuffled(random).take(pegs).toIntArray()
            return GuessState(pegs, colours, maxGuesses, secret, emptyList(), IntArray(pegs) { -1 }, 0)
        }
    }
}
