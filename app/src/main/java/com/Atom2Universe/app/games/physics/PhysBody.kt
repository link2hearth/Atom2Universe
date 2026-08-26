package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Les deux formes que le moteur sait gérer.
 *
 * La boîte suffisait au jeu d'équilibre ; le disque a été ajouté pour les
 * projectiles, parce qu'un boulet carré tombe comme une pierre au lieu de rouler.
 */
enum class Shape { BOX, CIRCLE }

/**
 * Un corps rigide : une boîte de demi-largeur [halfW] et demi-hauteur [halfH],
 * ou un disque de rayon [radius].
 *
 * Repère : mètres, axe X vers la droite, axe **Y vers le haut**. La vue se charge
 * de convertir en pixels.
 */
class PhysBody private constructor(
    val shape: Shape,
    val halfW: Float,
    val halfH: Float,
    val radius: Float,
    var mass: Float
) {

    /** Construit une boîte. */
    constructor(halfW: Float, halfH: Float, mass: Float) :
        this(Shape.BOX, halfW, halfH, 0f, mass)

    companion object {
        private var nextId = 1

        /** Construit un disque. [halfW] et [halfH] valent le rayon, pour que le
         *  code qui ne raisonne qu'en boîtes englobantes continue de marcher. */
        fun circle(radius: Float, mass: Float): PhysBody =
            PhysBody(Shape.CIRCLE, radius, radius, radius, mass)
    }

    /** Identifiant unique, sert de clé pour retrouver les contacts d'une image à l'autre. */
    val id: Int = nextId++

    // État
    var x = 0f
    var y = 0f
    var angle = 0f
    var vx = 0f
    var vy = 0f
    var omega = 0f

    /** Couple externe appliqué au prochain pas puis remis à zéro (ressort de la planche). */
    var torque = 0f

    // Masses inverses (0 = infiniment lourd, donc immobile sur cet axe)
    var invMass = 0f
        private set
    var invI = 0f
        private set

    var friction = 0.55f

    /**
     * Élasticité des chocs : 0 = le corps ne rebondit pas du tout (comportement
     * d'origine), 1 = rebond parfait. Au-dessus de 0,6 la simulation devient nerveuse.
     */
    var restitution = 0f

    /** Corps ignoré par le moteur (poids encore dans le plateau, ou tenu par le doigt). */
    var inWorld = true

    /** Translation figée : le sol, et la planche qui ne fait que tourner sur son pivot. */
    var lockPosition = false

    /** Rotation figée : la planche pendant la phase de pose. */
    var lockRotation = false

    /** Référence libre vers l'objet de jeu correspondant. */
    var tag: Any? = null

    /**
     * Somme des impulsions reçues lors de vrais chocs depuis la dernière remise à
     * zéro, en kg·m/s. Sert à faire casser les cibles : c'est le moteur qui la
     * calcule, donc les dégâts sont physiquement honnêtes.
     *
     * Les contacts au repos n'y contribuent pas (voir [Arbiter.impacting]), sinon
     * une caisse posée par terre se « détruirait » toute seule sous son propre poids.
     */
    var impactAccum = 0f

    /** Moment d'inertie autour du centre : boîte pleine, ou disque plein. */
    val inertia: Float
        get() = when (shape) {
            Shape.BOX -> mass * (4f * halfW * halfW + 4f * halfH * halfH) / 12f
            Shape.CIRCLE -> mass * radius * radius / 2f
        }

    init {
        refreshMass()
    }

    /** À rappeler après avoir changé [mass], [lockPosition] ou [lockRotation]. */
    fun refreshMass() {
        invMass = if (lockPosition || mass <= 0f) 0f else 1f / mass
        val i = inertia
        invI = if (lockRotation || i <= 0f) 0f else 1f / i
    }

    val immovable: Boolean get() = invMass == 0f && invI == 0f

    /** Rayon du cercle englobant, utilisé pour éliminer vite les paires trop éloignées. */
    val boundingRadius: Float
        get() = if (shape == Shape.CIRCLE) radius else sqrt(halfW * halfW + halfH * halfH)

    /** La plus petite demi-épaisseur du corps : sert à régler les sous-pas. */
    val smallestHalfExtent: Float
        get() = if (shape == Shape.CIRCLE) radius else minOf(halfW, halfH)

    /** Remplit [out] (8 flottants) avec les 4 sommets du corps, en coordonnées monde. */
    fun corners(out: FloatArray) {
        val c = cos(angle)
        val s = sin(angle)
        val hw = halfW
        val hh = halfH
        out[0] = x - hw * c + hh * s; out[1] = y - hw * s - hh * c
        out[2] = x + hw * c + hh * s; out[3] = y + hw * s - hh * c
        out[4] = x + hw * c - hh * s; out[5] = y + hw * s + hh * c
        out[6] = x - hw * c - hh * s; out[7] = y - hw * s + hh * c
    }

    /** Hauteur du sommet le plus haut du corps (utile pour empiler). */
    fun topY(): Float {
        if (shape == Shape.CIRCLE) return y + radius
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        return y + halfW * s + halfH * c
    }

    /** Demi-largeur de la boîte englobante alignée sur les axes. */
    fun aabbHalfWidth(): Float {
        if (shape == Shape.CIRCLE) return radius
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        return halfW * c + halfH * s
    }

    /** Passe un point du repère local du corps au repère monde, dans [out] (2 flottants). */
    fun localToWorld(lx: Float, ly: Float, out: FloatArray) {
        val c = cos(angle)
        val s = sin(angle)
        out[0] = x + lx * c - ly * s
        out[1] = y + lx * s + ly * c
    }

    /** Vitesse du corps, au carré : évite une racine quand on teste le repos. */
    val speedSq: Float get() = vx * vx + vy * vy

    /** Vrai si le corps ne bouge quasiment plus. */
    fun atRest(linearTol: Float = 0.02f, angularTol: Float = 0.05f): Boolean =
        immovable || (speedSq < linearTol * linearTol && abs(omega) < angularTol)
}
