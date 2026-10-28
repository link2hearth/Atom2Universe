package com.Atom2Universe.app.games.balance

import com.Atom2Universe.app.games.physics.PhysWorld
import kotlin.math.abs

/**
 * Le repos jugé sur ce que l'écran montre, et non sur la vitesse du moteur.
 *
 * Une vitesse instantanée ne dit pas si l'image change : un mobile qui frémit autour d'un
 * seuil le franchit à chaque battement, et la veille ne venait jamais ; un corps qui rampe
 * sous le seuil finit, lui, par bouger d'un pixel. On relève donc à chaque image les positions
 * **dessinées**, en pixels. Tant qu'aucune ne s'écarte de [seuilPx] de celles notées au début
 * de la fenêtre, le calme s'accumule ; au bout de [duree] secondes, l'image est figée. Un
 * écart rouvre la fenêtre depuis la position du moment : une lente dérive finit toujours par
 * dépasser le seuil, un frémissement plus petit qu'un demi-pixel jamais.
 *
 * Classe sans vue ni Android : c'est elle que les tests font tourner sur un vrai montage.
 */
class ReposEcran(
    private val seuilPx: Float = 0.5f,
    private val duree: Float = 0.5f
) {
    private var notes = FloatArray(64)
    private var reference = FloatArray(64)
    private var n = 0
    private var nReference = -1
    private var calme = 0f

    /** Ouvre le relevé d'une image. */
    fun debut() {
        n = 0
    }

    /** Note une position dessinée, en pixels. */
    fun point(x: Float, y: Float) {
        if (n + 2 > notes.size) notes = notes.copyOf(notes.size * 2)
        notes[n++] = x
        notes[n++] = y
    }

    /**
     * Note chaque corps mobile du monde tel que l'écran le montre : son centre, et le chemin
     * que fait son bord le plus éloigné quand il tourne (angle × rayon englobant). Le repère
     * est celui des vues : `x = origineX + x·echelle`, `y = origineY − y·echelle`.
     */
    fun monde(world: PhysWorld, origineX: Float, origineY: Float, echelle: Float) {
        for (b in world.bodies) {
            if (!b.inWorld || b.immovable) continue
            point(origineX + b.x * echelle, origineY - b.y * echelle)
            point(b.angle * b.boundingRadius * echelle, 0f)
        }
    }

    /**
     * Ferme le relevé de l'image. [fige] dit que rien d'autre que les points notés ne peut
     * changer l'image (pas d'animation, rien en main). Rend vrai quand l'image n'a pas bougé
     * depuis [duree] secondes.
     */
    fun fin(dt: Float, fige: Boolean): Boolean {
        var bouge = !fige || n != nReference
        if (!bouge) {
            for (i in 0 until n) {
                if (abs(notes[i] - reference[i]) >= seuilPx) {
                    bouge = true
                    break
                }
            }
        }
        if (bouge) {
            if (reference.size < n) reference = FloatArray(notes.size)
            System.arraycopy(notes, 0, reference, 0, n)
            nReference = n
            calme = 0f
            return false
        }
        calme += dt
        return calme >= duree
    }

    /** Oublie la fenêtre en cours : la prochaine image en ouvre une neuve. */
    fun oublier() {
        nReference = -1
        calme = 0f
    }
}
