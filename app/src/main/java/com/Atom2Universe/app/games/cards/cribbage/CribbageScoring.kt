package com.Atom2Universe.app.games.cards.cribbage

import com.Atom2Universe.app.games.cards.PlayingCard

/**
 * Les points du Cribbage. Les cartes valent leur chiffre pour compter jusqu'à 15 et 31 : l'as 1, les
 * figures 10. Pour les suites, l'as est bas (A-2-3) et les figures se suivent V, D, R.
 */
object CribbageScoring {

    fun value(card: PlayingCard): Int = minOf(card.rank, 10)

    /**
     * Les points d'une main de quatre cartes avec la carte retournée [starter] : quinze (2 points par
     * combinaison qui fait 15), paires, suites, couleur, et le valet de la couleur de la retournée
     * (« nobs », 1 point). Dans le crib ([isCrib]), la couleur ne compte qu'avec cinq cartes.
     */
    fun scoreHand(hand: List<PlayingCard>, starter: PlayingCard, isCrib: Boolean = false): Int {
        val all = hand + starter
        var points = 0

        // Quinze : toutes les façons de choisir des cartes dont la somme fait 15.
        for (subset in 1 until (1 shl all.size)) {
            var sum = 0
            for (i in all.indices) if (subset and (1 shl i) != 0) sum += value(all[i])
            if (sum == 15) points += 2
        }

        // Paires : chaque couple de même hauteur.
        for (i in all.indices) for (j in i + 1 until all.size) if (all[i].rank == all[j].rank) points += 2

        // Suites : la plus longue, comptée autant de fois qu'elle se présente (un double suite compte deux fois).
        for (length in all.size downTo 3) {
            var found = 0
            for (subset in 1 until (1 shl all.size)) {
                if (Integer.bitCount(subset) != length) continue
                val ranks = all.filterIndexed { i, _ -> subset and (1 shl i) != 0 }.map { it.rank }
                if (ranks.toSet().size == length && ranks.max() - ranks.min() == length - 1) found++
            }
            if (found > 0) { points += found * length; break }
        }

        // Couleur : quatre cartes de la main de même couleur (cinq avec la retournée).
        if (hand.all { it.suit == hand[0].suit }) {
            if (starter.suit == hand[0].suit) points += 5 else if (!isCrib) points += 4
        }

        // Nobs : le valet de la main qui a la couleur de la retournée.
        if (hand.any { it.rank == PlayingCard.JACK && it.suit == starter.suit }) points += 1
        return points
    }

    /**
     * Les points que vient de rapporter la dernière carte posée au décompte : [pile] est la suite des
     * cartes posées depuis la dernière remise à zéro, [count] leur total. 15 et 31 rapportent 2 ; une
     * paire, un brelan ou un carré de même hauteur en fin de pile 2, 6 ou 12 ; une suite de trois cartes
     * ou plus (dans le désordre) en fin de pile autant de points que de cartes.
     */
    fun pegPoints(pile: List<PlayingCard>, count: Int): Int {
        var points = 0
        if (count == 15 || count == 31) points += 2

        var same = 1
        while (same < pile.size && pile[pile.size - 1 - same].rank == pile.last().rank) same++
        points += when (same) { 2 -> 2; 3 -> 6; 4 -> 12; else -> 0 }

        for (length in minOf(pile.size, 7) downTo 3) {
            val ranks = pile.takeLast(length).map { it.rank }
            if (ranks.toSet().size == length && ranks.max() - ranks.min() == length - 1) { points += length; break }
        }
        return points
    }
}
