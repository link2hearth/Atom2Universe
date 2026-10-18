package com.Atom2Universe.app.games.golf.classic.core

import kotlin.random.Random

/** The seed picks a noun and an adjective by index; the words themselves live in the string resources. */
object RandomGolfNames {
    fun pick(seed: Long, nouns: Int, adjectives: Int): Pair<Int, Int> {
        val r = Random(seed * 2_654_435_761L + 97L)
        return r.nextInt(nouns) to r.nextInt(adjectives)
    }
}
