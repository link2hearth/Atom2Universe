package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
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
 * Une des formes qui composent un corps, avec sa position dans le repère de
 * celui-ci.
 *
 * C'est ce qui permet de fabriquer une pièce d'un seul tenant à partir de
 * plusieurs morceaux : la planche d'une machine de jet et ses deux butées, par
 * exemple, ne font qu'un seul corps rigide.
 */
class BodyPart internal constructor(
    val shape: Shape,
    val halfW: Float,
    val halfH: Float,
    val radius: Float,
    val localX: Float,
    val localY: Float,
    val localAngle: Float
) {
    /** Sert à répartir la masse du corps entre ses formes. */
    internal val area: Float
        get() = if (shape == Shape.CIRCLE) {
            (Math.PI * radius * radius).toFloat()
        } else {
            4f * halfW * halfH
        }

    /** Inertie autour de son propre centre, pour une masse de 1 kg. */
    internal val unitInertia: Float
        get() = if (shape == Shape.CIRCLE) {
            radius * radius / 2f
        } else {
            (4f * halfW * halfW + 4f * halfH * halfH) / 12f
        }

    internal val boundingRadius: Float
        get() = if (shape == Shape.CIRCLE) radius else sqrt(halfW * halfW + halfH * halfH)

    internal val smallestHalfExtent: Float
        get() = if (shape == Shape.CIRCLE) radius else minOf(halfW, halfH)
}

/** Assemble les formes d'un corps composé. */
class PartsBuilder internal constructor() {
    internal val parts = ArrayList<BodyPart>()

    /** Ajoute une boîte, centrée en ([x], [y]) dans le repère du corps. */
    fun box(halfW: Float, halfH: Float, x: Float = 0f, y: Float = 0f, angle: Float = 0f) {
        parts += BodyPart(Shape.BOX, halfW, halfH, 0f, x, y, angle)
    }

    /** Ajoute un disque, centré en ([x], [y]) dans le repère du corps. */
    fun circle(radius: Float, x: Float = 0f, y: Float = 0f) {
        parts += BodyPart(Shape.CIRCLE, radius, radius, radius, x, y, 0f)
    }
}

/**
 * Un corps rigide : une ou plusieurs formes solidaires.
 *
 * Repère : mètres, axe X vers la droite, axe **Y vers le haut**. La vue se charge
 * de convertir en pixels.
 *
 * La position du corps est celle de son **centre de masse** : pour un corps
 * composé, les positions locales des formes sont recentrées à la construction,
 * sinon la pesanteur et la rotation ne s'appliqueraient pas au bon endroit.
 */
class PhysBody private constructor(
    val parts: List<BodyPart>,
    var mass: Float
) {

    /** Construit une boîte simple. */
    constructor(halfW: Float, halfH: Float, mass: Float) :
        this(listOf(BodyPart(Shape.BOX, halfW, halfH, 0f, 0f, 0f, 0f)), mass)

    companion object {
        private var nextId = 1

        /** Construit un disque simple. */
        fun circle(radius: Float, mass: Float): PhysBody =
            PhysBody(listOf(BodyPart(Shape.CIRCLE, radius, radius, radius, 0f, 0f, 0f)), mass)

        /**
         * Construit un corps composé de plusieurs formes, d'un seul tenant.
         *
         * Les formes sont décrites dans un repère libre ; elles sont ensuite
         * recentrées sur le centre de masse de l'ensemble. [localOffsetX] et
         * [localOffsetY] permettent de retrouver où une forme a atterri, pour
         * placer le corps de façon qu'une forme précise tombe au bon endroit.
         */
        fun compound(mass: Float, build: PartsBuilder.() -> Unit): PhysBody {
            val b = PartsBuilder().apply(build)
            require(b.parts.isNotEmpty()) { "un corps composé doit avoir au moins une forme" }

            // Centre de masse : moyenne des centres pondérée par les aires.
            var totalArea = 0f
            var cx = 0f
            var cy = 0f
            for (p in b.parts) {
                totalArea += p.area
                cx += p.area * p.localX
                cy += p.area * p.localY
            }
            if (totalArea > 0f) {
                cx /= totalArea
                cy /= totalArea
            }
            val centered = b.parts.map {
                BodyPart(it.shape, it.halfW, it.halfH, it.radius, it.localX - cx, it.localY - cy, it.localAngle)
            }
            return PhysBody(centered, mass)
        }
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

    /**
     * Vitesses « fantômes », qui servent uniquement à replacer les corps.
     *
     * Un solveur doit faire deux choses : empêcher les corps de s'enfoncer les uns
     * dans les autres (une affaire de vitesses) et rattraper l'enfoncement déjà
     * accumulé (une affaire de positions). Tout mélanger revient à pousser sur les
     * vraies vitesses pour corriger une position — et cette poussée est de
     * l'énergie créée de toutes pièces. Sur une chaîne de liaisons un peu raide,
     * elle s'emballe : un boulet sortait à 250 m/s d'une machine qui ne stocke que
     * dix mille joules.
     *
     * Ces vitesses-là ne servent donc qu'au déplacement, et sont remises à zéro à
     * la fin de chaque pas : la correction bouge les corps sans jamais leur donner
     * d'élan.
     */
    var pvx = 0f
    var pvy = 0f
    var pomega = 0f

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
     * Catégorie du corps : un bit, à choisir par le jeu.
     *
     * Avec [collidesWith], elle permet de dire qui touche qui. Deux pièces d'une
     * machine se chevauchent parfois par construction — l'arrêtoir d'un trébuchet
     * et son contrepoids occupent le même espace — et un projectile qui retombe sur
     * sa propre machine finit broyé entre deux pièces bien plus lourdes que lui,
     * ce qu'aucun solveur de ce genre ne sait résoudre proprement.
     */
    var category = 1

    /** Catégories que ce corps accepte de toucher. Par défaut, toutes. */
    var collidesWith = -1

    /** Vrai si les deux corps acceptent mutuellement de se toucher. */
    fun collidesWith(other: PhysBody): Boolean =
        (category and other.collidesWith) != 0 && (other.category and collidesWith) != 0

    /**
     * Énergie reçue lors de vrais chocs depuis la dernière remise à zéro, en joules.
     * Sert à faire casser les cibles : c'est le moteur qui la calcule, donc les dégâts
     * sont physiquement honnêtes.
     *
     * Les contacts au repos n'y contribuent pas (voir [Arbiter.impacting]), et le poids
     * porté par un contact n'y contribue pas non plus (voir [Arbiter.impactEnergy]) :
     * sans ces deux précautions, une caisse posée par terre se « détruirait » toute
     * seule sous son propre poids, et un mur sous celui de ses propres assises.
     */
    var impactAccum = 0f

    /**
     * Traînée de l'air, en kg/m : la moitié de ρ·Cx·S, tout regroupé.
     *
     * La force de traînée vaut ce coefficient fois le carré de la vitesse, et elle
     * s'oppose au mouvement. Zéro par défaut — le monde est alors vide d'air,
     * comme avant.
     *
     * Elle ne se néglige pas dès qu'un projectile va vite : un boulet de 12 kg et
     * 16 cm de rayon lancé à 70 m/s encaisse 110 N, soit à peu près sa propre
     * pesanteur. Sans elle, une machine de jet porte de moitié trop loin et sa
     * trajectoire est une parabole symétrique de manuel, au lieu de retomber plus
     * raide qu'elle n'est montée.
     */
    var dragFactor = 0f

    // ── Raccourcis pour les corps à une seule forme ───────────────────────────

    val shape: Shape get() = parts[0].shape
    val halfW: Float get() = parts[0].halfW
    val halfH: Float get() = parts[0].halfH
    val radius: Float get() = parts[0].radius

    /**
     * Moment d'inertie autour du centre de masse.
     *
     * Pour un corps composé, chaque forme apporte sa propre inertie **plus** sa
     * masse fois le carré de sa distance au centre — c'est le théorème de Huygens,
     * et c'est ce qui fait qu'une pièce excentrée est bien plus dure à faire tourner.
     */
    val inertia: Float
        get() {
            if (mass <= 0f) return 0f
            var totalArea = 0f
            for (p in parts) totalArea += p.area
            if (totalArea <= 0f) return 0f
            var i = 0f
            for (p in parts) {
                val m = mass * p.area / totalArea
                i += m * (p.unitInertia + p.localX * p.localX + p.localY * p.localY)
            }
            return i
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

    /** Rayon du cercle englobant tout le corps, pour éliminer vite les paires éloignées. */
    val boundingRadius: Float
        get() {
            var best = 0f
            for (p in parts) {
                val r = hypot(p.localX, p.localY) + p.boundingRadius
                if (r > best) best = r
            }
            return best
        }

    /** La plus petite demi-épaisseur du corps : sert à régler les sous-pas. */
    val smallestHalfExtent: Float
        get() {
            var best = Float.MAX_VALUE
            for (p in parts) {
                val e = p.smallestHalfExtent
                if (e < best) best = e
            }
            return best
        }

    /** Position locale d'une forme, après recentrage sur le centre de masse. */
    fun localOffsetX(part: Int): Float = parts[part].localX
    fun localOffsetY(part: Int): Float = parts[part].localY

    /** Pose monde d'une forme dans [out] : x, y, angle. */
    fun partWorld(part: Int, out: FloatArray) {
        val p = parts[part]
        val c = cos(angle)
        val s = sin(angle)
        out[0] = x + p.localX * c - p.localY * s
        out[1] = y + p.localX * s + p.localY * c
        out[2] = angle + p.localAngle
    }

    /** Remplit [out] (8 flottants) avec les 4 sommets d'une forme, en coordonnées monde. */
    fun partCorners(part: Int, out: FloatArray) {
        val p = parts[part]
        val c = cos(angle)
        val s = sin(angle)
        val px = x + p.localX * c - p.localY * s
        val py = y + p.localX * s + p.localY * c
        val pa = angle + p.localAngle
        val ca = cos(pa)
        val sa = sin(pa)
        val hw = p.halfW
        val hh = p.halfH
        out[0] = px - hw * ca + hh * sa; out[1] = py - hw * sa - hh * ca
        out[2] = px + hw * ca + hh * sa; out[3] = py + hw * sa - hh * ca
        out[4] = px + hw * ca - hh * sa; out[5] = py + hw * sa + hh * ca
        out[6] = px - hw * ca - hh * sa; out[7] = py - hw * sa + hh * ca
    }

    /** Sommets de la première forme : raccourci pour les corps simples. */
    fun corners(out: FloatArray) = partCorners(0, out)

    /** Hauteur du sommet le plus haut du corps (utile pour empiler). */
    fun topY(): Float {
        var best = -Float.MAX_VALUE
        val c = cos(angle)
        val s = sin(angle)
        for (p in parts) {
            val py = y + p.localX * s + p.localY * c
            val top = if (p.shape == Shape.CIRCLE) {
                py + p.radius
            } else {
                val pa = angle + p.localAngle
                py + p.halfW * abs(sin(pa)) + p.halfH * abs(cos(pa))
            }
            if (top > best) best = top
        }
        return best
    }

    /** Demi-largeur de la boîte englobante alignée sur les axes. */
    fun aabbHalfWidth(): Float {
        var best = 0f
        val c = cos(angle)
        val s = sin(angle)
        for (p in parts) {
            val dx = p.localX * c - p.localY * s
            val half = if (p.shape == Shape.CIRCLE) {
                p.radius
            } else {
                val pa = angle + p.localAngle
                p.halfW * abs(cos(pa)) + p.halfH * abs(sin(pa))
            }
            val reach = abs(dx) + half
            if (reach > best) best = reach
        }
        return best
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
