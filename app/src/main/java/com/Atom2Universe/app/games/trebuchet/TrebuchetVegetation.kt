package com.Atom2Universe.app.games.trebuchet

import kotlin.math.abs
import kotlin.random.Random

/**
 * Le décor végétal : ce qui pousse sur le relief entre la machine et le site, et
 * au-delà de lui.
 *
 * **Une plante ne connaît pas le mode arcade.** [TargetRules.site] double les
 * bâtiments en arcade pour que la silhouette reste lisible, mais un chêne ne
 * grandit pas parce que la maison d'à côté a doublé — c'est justement la règle du
 * mode arcade, énoncée ailleurs : seul le site s'agrandit, tout le reste garde ses
 * mètres nus. La végétation reste donc à sa vraie taille dans les deux modes, ce
 * qui la fait paraître plus petite à côté d'un château arcade — et c'est juste.
 *
 * **Rien ne pousse sur une forte pente**, pour la même raison qu'aucun bâtiment ne
 * s'y bâtit : ce n'est pas qu'un arbre ne pourrait pas y tenir, c'est qu'un tronc
 * vertical planté sur un flanc oblique aurait l'air posé de travers. Seule l'herbe,
 * qui n'a pas de tronc à tenir droit, s'en moque.
 */
class VegetationField(seed: Long, terrain: Terrain, siteLeft: Float, siteRight: Float) {

    enum class Kind { TUFT, BUSH, TREE }

    /**
     * Une plante : où elle pousse, ce qu'elle est, et sa forme — tirée une fois pour
     * toutes à la construction du champ. La vue n'a plus qu'à la peindre.
     */
    class Plant internal constructor(
        val kind: Kind,
        /** Position dans le monde, en mètres — comme les nuages, pas une fraction d'écran. */
        val x: Float,
        val groundY: Float,
        /** Hauteur du tronc ou de la tige, en mètres. Nulle pour un buisson, posé au sol. */
        val stem: Float,
        /** Rayon du feuillage, ou longueur des brins pour une touffe. */
        val size: Float,
        /** Boules du feuillage — dx, dy, rayon, en parts de [size] — vide pour une touffe. */
        val puffs: FloatArray,
        /** Décalage propre de balancement, pour qu'elles n'ondulent pas toutes ensemble. */
        val swayPhase: Float
    )

    val plants: List<Plant> = build(seed, terrain, siteLeft, siteRight)

    companion object {

        /** Marge laissée autour du site, comme celle que ses propres bâtiments respectent. */
        private const val SITE_MARGIN = 6f

        /** Rien ne pousse sous la machine ni dans le débattement de son bras. */
        private const val MACHINE_LEFT = -24f
        private const val MACHINE_RIGHT = 14f

        /** Pente locale au-delà de laquelle seule l'herbe accepte de pousser. */
        private const val TREE_SLOPE_LIMIT = 0.12f

        private fun build(
            seed: Long,
            terrain: Terrain,
            siteLeft: Float,
            siteRight: Float
        ): List<Plant> {
            val rng = Random(seed)
            val out = ArrayList<Plant>()
            var x = TrebuchetRules.GROUND_LEFT + 4f
            val siteLo = siteLeft - SITE_MARGIN
            val siteHi = siteRight + SITE_MARGIN
            while (x < TrebuchetRules.GROUND_RIGHT - 4f) {
                // Un pas irrégulier : une haie plantée au mètre serait une clôture,
                // pas un paysage.
                x += 2.5f + rng.nextFloat() * 8.5f
                if (x in MACHINE_LEFT..MACHINE_RIGHT) continue
                if (x in siteLo..siteHi) continue

                val groundY = terrain.heightAt(x)
                val pente = abs(terrain.heightAt(x + 1f) - terrain.heightAt(x - 1f)) / 2f
                val roll = rng.nextFloat()
                val kind = when {
                    pente > TREE_SLOPE_LIMIT || roll < 0.5f -> Kind.TUFT
                    roll < 0.82f -> Kind.BUSH
                    else -> Kind.TREE
                }
                out += makePlant(rng, kind, x, groundY)
            }
            return out
        }

        private fun makePlant(rng: Random, kind: Kind, x: Float, groundY: Float): Plant {
            val phase = rng.nextFloat() * 6.2832f
            return when (kind) {
                Kind.TUFT -> Plant(
                    kind, x, groundY, stem = 0f,
                    size = 0.28f + rng.nextFloat() * 0.22f,
                    // Trois brins, chacun avec son inclinaison de repos.
                    puffs = FloatArray(3) { (rng.nextFloat() - 0.5f) * 1.6f },
                    swayPhase = phase
                )

                Kind.BUSH -> Plant(
                    kind, x, groundY, stem = 0f,
                    size = 0.8f + rng.nextFloat() * 0.9f,
                    puffs = canopyPuffs(rng, n = 4, widthSpread = 1.05f, heightSpread = 0.55f),
                    swayPhase = phase
                )

                Kind.TREE -> {
                    val size = 1.5f + rng.nextFloat() * 1.9f
                    Plant(
                        kind, x, groundY, stem = size * (1.1f + rng.nextFloat() * 0.5f),
                        size = size,
                        puffs = canopyPuffs(rng, n = 5, widthSpread = 0.95f, heightSpread = 0.7f),
                        swayPhase = phase
                    )
                }
            }
        }

        /** Des boules de feuillage, comme celles d'un nuage — voir [CloudField]. */
        private fun canopyPuffs(
            rng: Random,
            n: Int,
            widthSpread: Float,
            heightSpread: Float
        ): FloatArray {
            val puffs = FloatArray(n * 3)
            for (i in 0 until n) {
                puffs[i * 3] = (rng.nextFloat() - 0.5f) * widthSpread
                puffs[i * 3 + 1] = (rng.nextFloat() - 0.5f) * heightSpread
                puffs[i * 3 + 2] = 0.42f + rng.nextFloat() * 0.34f
            }
            return puffs
        }
    }
}
