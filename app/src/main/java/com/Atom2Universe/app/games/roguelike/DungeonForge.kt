package com.Atom2Universe.app.games.roguelike

import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * La forge : une structure rare posée sur la carte. Elle reforge un objet **au niveau de l'étage
 * actuel** : un nouveau tirage du même type d'objet, de la même rareté et du même poids (une
 * pièce de set reste une pièce du même set). Un objet porté depuis des dizaines d'étages peut ainsi
 * remonter. On peut reforger autant de fois qu'on veut, tant qu'on paie.
 *
 * Le prix vaut [FLOORS_OF_INCOME] étages de gains **à l'étage de la forge**. Les gains par étage
 * (or ramassé, or des combats et revente de tout le butin) ont été mesurés par les bots le
 * 23/09/2026 (`RoguelikeSimulationTest.goldEconomy`, règle checkpoint 10 / retour 20, voir
 * DONJON_FORGE.md) : environ `1,17 × étage^1,66` à partir de l'étage 25.
 */
object DungeonForge {
    /** Pas de forge avant cet étage. */
    const val MIN_FLOOR = 50
    /** La chance, à chaque arrivée sur un étage, qu'une forge s'y trouve. */
    const val CHANCE = 0.05f
    /** Un reset coûte ce nombre d'étages de gains. */
    const val FLOORS_OF_INCOME = 5
    private const val INCOME_COEF = 1.17
    private const val INCOME_EXPONENT = 1.66

    /** Tiré à l'arrivée sur l'étage, pas à la génération : régénérer l'étage au feu de camp ne relance pas la chance. */
    fun rollPresence(floor: Int, rng: Random) = floor >= MIN_FLOOR && rng.nextFloat() < CHANCE

    /** Ce que rapporte un étage à cette profondeur, d'après la mesure des bots. */
    fun incomePerFloor(floor: Int) = INCOME_COEF * floor.coerceAtLeast(1).toDouble().pow(INCOME_EXPONENT)

    /** Le prix d'un reset, arrondi à deux chiffres significatifs pour se lire d'un coup d'œil. */
    fun price(floor: Int): Int {
        val raw = incomePerFloor(floor) * FLOORS_OF_INCOME
        val step = 10.0.pow((log10(raw).toInt() - 1).coerceAtLeast(0))
        return ((raw / step).roundToLong() * step).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }
}
