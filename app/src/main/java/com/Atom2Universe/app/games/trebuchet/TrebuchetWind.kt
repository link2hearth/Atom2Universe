package com.Atom2Universe.app.games.trebuchet

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Le vent qui souffle sur un site : une vitesse et une direction.
 *
 * **Il ne varie pas pendant un tir, et c'est un choix de jeu.** L'air réel a des
 * rafales ; un jeu où l'on ne vise pas, où l'on règle une machine et où l'on corrige
 * d'un tir à l'autre, n'en veut surtout pas. Une rafale rendrait deux tirs identiques
 * différents, ce qui casserait la seule boucle d'apprentissage du jeu : régler,
 * observer, corriger. Le vent est donc une donnée du niveau, affichée en clair, et le
 * joueur compose avec.
 *
 * Il sort de la graine du site, comme tout le reste : la même graine donne le même
 * château **et** le même vent. Un niveau reste un entier.
 */
class Wind(val speed: Float, val angle: Float) {

    val vx: Float = speed * cos(angle)
    val vy: Float = speed * sin(angle)

    /** Vrai quand il n'y a rien à signaler : l'air est calme. */
    val calm: Boolean get() = speed < 0.5f

    /** Vrai si le vent pousse le boulet vers la cible. */
    val tailwind: Boolean get() = vx > 0f

    /**
     * Force sur l'échelle du jeu, de 0 (calme) à 5 (tempête).
     *
     * Cinq crans, parce que l'indicateur doit se lire d'un coup d'œil et qu'un chiffre
     * au dixième près ne dit rien à personne. La vitesse exacte reste affichée à côté
     * pour qui veut calculer.
     */
    val force: Int get() = (speed / MAX_SPEED * 5f).roundToInt().coerceIn(0, 5)

    /** Vrai si les deux vents se ressemblent assez pour qu'on ne les distingue pas. */
    fun sameAs(o: Wind): Boolean = abs(speed - o.speed) < 0.05f && abs(angle - o.angle) < 0.01f

    override fun toString(): String =
        "Wind(${"%.1f".format(speed)} m/s, ${Math.toDegrees(angle.toDouble()).roundToInt()}°)"

    companion object {
        /** Vitesse maximale, en m/s. Une trentaine de kilomètres-heure : un vent de côte. */
        const val MAX_SPEED = 8.5f

        /**
         * Inclinaison maximale sur l'horizontale, en degrés.
         *
         * **Un vent fort ne monte pas et ne descend pas.** Une brise peut avoir un peu
         * de pente — un souffle qui remonte une colline — mais une tempête verticale
         * n'existe pas, et surtout n'aurait aucun sens à l'écran : la flèche de
         * l'indicateur pointerait vers le ciel et personne ne saurait quoi en faire.
         * L'inclinaison permise se resserre donc à mesure que le vent forcit, jusqu'à
         * être franchement horizontale dans le haut de l'échelle.
         */
        const val MAX_TILT_DEG = 20f

        val CALM = Wind(0f, 0f)

        /**
         * Le vent d'une graine.
         *
         * La vitesse est tirée **au carré** : la plupart des sites sont calmes ou
         * presque, et les vents forts restent l'exception qu'on remarque. Un vent moyen
         * sur tous les niveaux serait un vent qu'on cesse de regarder.
         */
        fun forSeed(seed: Long): Wind {
            val rng = Random(seed * 7919L + 13L)
            val t = rng.nextFloat()
            val speed = MAX_SPEED * t * t
            // La pente permise se resserre quand le vent forcit.
            val tilt = MAX_TILT_DEG * (1f - speed / MAX_SPEED) * (rng.nextFloat() * 2f - 1f)
            val deg = if (rng.nextFloat() < 0.5f) tilt else 180f - tilt
            return Wind(speed, Math.toRadians(deg.toDouble()).toFloat())
        }
    }
}
