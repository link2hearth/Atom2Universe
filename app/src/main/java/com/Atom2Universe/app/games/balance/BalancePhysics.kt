package com.Atom2Universe.app.games.balance

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Mini moteur physique 2D à corps rigides (boîtes orientées), écrit sur mesure
 * pour le jeu d'équilibre.
 *
 * Repère : mètres, axe X vers la droite, axe **Y vers le haut**, origine au pivot
 * de la planche. La vue se charge de convertir en pixels.
 *
 * Le fonctionnement reprend celui de Box2D-Lite, en trois temps à chaque pas :
 *  1. **détection** des contacts entre boîtes (théorème de l'axe séparateur, puis
 *     découpage de la face « incidente » contre la face de référence) ;
 *  2. **résolution** des contacts par impulsions séquentielles (plusieurs passes
 *     qui corrigent tour à tour chaque point de contact) ;
 *  3. **intégration** des positions.
 *
 * Les impulsions sont conservées d'une image à l'autre (« warm starting ») :
 * c'est ce qui rend une pile de briques stable au lieu de trembler.
 */

/** Un corps rigide : une boîte de demi-largeur [halfW] et demi-hauteur [halfH]. */
class PhysBody(
    val halfW: Float,
    val halfH: Float,
    var mass: Float
) {
    companion object {
        private var nextId = 1
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

    /** Corps ignoré par le moteur (poids encore dans le plateau, ou tenu par le doigt). */
    var inWorld = true

    /** Translation figée : le sol, et la planche qui ne fait que tourner sur son pivot. */
    var lockPosition = false

    /** Rotation figée : la planche pendant la phase de pose. */
    var lockRotation = false

    /** Référence libre vers l'objet de jeu correspondant. */
    var tag: Any? = null

    /** Moment d'inertie d'une boîte pleine autour de son centre. */
    val inertia: Float
        get() = mass * (4f * halfW * halfW + 4f * halfH * halfH) / 12f

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
    val boundingRadius: Float get() = sqrt(halfW * halfW + halfH * halfH)

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
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        return y + halfW * s + halfH * c
    }

    /** Demi-largeur de la boîte englobante alignée sur les axes. */
    fun aabbHalfWidth(): Float {
        val c = abs(cos(angle))
        val s = abs(sin(angle))
        return halfW * c + halfH * s
    }
}

/** Un point de contact entre deux corps. */
class Contact {
    var px = 0f
    var py = 0f
    var separation = 0f

    /** Impulsions accumulées, conservées d'une image à l'autre. */
    var normalImpulse = 0f
    var tangentImpulse = 0f

    // Pré-calculs du solveur
    var massNormal = 0f
    var massTangent = 0f
    var bias = 0f
    var rax = 0f
    var ray = 0f
    var rbx = 0f
    var rby = 0f

    /** Identifiant géométrique du point (quelle face contre quel sommet). */
    var feature = 0

    fun set(o: Contact) {
        px = o.px; py = o.py; separation = o.separation
        normalImpulse = o.normalImpulse; tangentImpulse = o.tangentImpulse
        feature = o.feature
    }
}

/**
 * Détection de collision boîte contre boîte.
 *
 * Objet unique avec des tampons réutilisés : le moteur tourne sur un seul thread,
 * donc on évite ainsi toute allocation pendant la simulation.
 */
internal object BoxCollider {

    private val vertsA = FloatArray(8)
    private val vertsB = FloatArray(8)
    private val segIn = FloatArray(4)
    private val segMid = FloatArray(4)
    private val segOut = FloatArray(4)
    private val featIn = IntArray(2)
    private val featMid = IntArray(2)
    private val featOut = IntArray(2)

    /** Normale du contact, orientée du corps A vers le corps B. */
    var normalX = 0f
        private set
    var normalY = 0f
        private set

    private var sepValue = 0f
    private var sepIndex = 0

    /**
     * Cherche, parmi les 4 faces du polygone [vr], celle qui sépare le mieux [vi].
     * Résultat dans [sepValue] (distance, positive = pas de collision) et [sepIndex].
     */
    private fun maxSeparation(vr: FloatArray, vi: FloatArray) {
        var best = -Float.MAX_VALUE
        var bestI = 0
        for (i in 0 until 4) {
            val ax = vr[i * 2]
            val ay = vr[i * 2 + 1]
            val j = (i + 1) and 3
            var nx = vr[j * 2 + 1] - ay
            var ny = -(vr[j * 2] - ax)
            val len = sqrt(nx * nx + ny * ny)
            if (len < 1e-6f) continue
            nx /= len; ny /= len
            // Point de [vi] le plus « enfoncé » dans cette face
            var minS = Float.MAX_VALUE
            for (k in 0 until 4) {
                val s = (vi[k * 2] - ax) * nx + (vi[k * 2 + 1] - ay) * ny
                if (s < minS) minS = s
            }
            if (minS > best) { best = minS; bestI = i }
        }
        sepValue = best
        sepIndex = bestI
    }

    /**
     * Découpe le segment [srcP] par le demi-plan « produit scalaire (d, p) <= offset ».
     * Retourne le nombre de points conservés (0 à 2), écrits dans [dstP].
     */
    private fun clip(
        dx: Float, dy: Float, offset: Float,
        srcP: FloatArray, srcF: IntArray,
        dstP: FloatArray, dstF: IntArray,
        edgeFeature: Int
    ): Int {
        var num = 0
        val d0 = dx * srcP[0] + dy * srcP[1] - offset
        val d1 = dx * srcP[2] + dy * srcP[3] - offset
        if (d0 <= 0f) { dstP[num * 2] = srcP[0]; dstP[num * 2 + 1] = srcP[1]; dstF[num] = srcF[0]; num++ }
        if (d1 <= 0f && num < 2) { dstP[num * 2] = srcP[2]; dstP[num * 2 + 1] = srcP[3]; dstF[num] = srcF[1]; num++ }
        if (d0 * d1 < 0f && num < 2) {
            val t = d0 / (d0 - d1)
            dstP[num * 2] = srcP[0] + t * (srcP[2] - srcP[0])
            dstP[num * 2 + 1] = srcP[1] + t * (srcP[3] - srcP[1])
            dstF[num] = edgeFeature
            num++
        }
        return num
    }

    /**
     * Calcule les points de contact entre [a] et [b] et les écrit dans [out].
     * Retourne le nombre de points (0 s'il n'y a pas de collision).
     */
    fun collide(a: PhysBody, b: PhysBody, out: Array<Contact>): Int {
        a.corners(vertsA)
        b.corners(vertsB)

        maxSeparation(vertsA, vertsB)
        val sepA = sepValue
        val faceA = sepIndex
        if (sepA > 0f) return 0

        maxSeparation(vertsB, vertsA)
        val sepB = sepValue
        val faceB = sepIndex
        if (sepB > 0f) return 0

        // Face de référence : celle qui sépare le mieux (petit biais pour éviter
        // de basculer d'une face à l'autre à chaque image).
        val flip = sepB > sepA + 0.002f
        val refVerts = if (flip) vertsB else vertsA
        val incVerts = if (flip) vertsA else vertsB
        val refIdx = if (flip) faceB else faceA

        val rj = (refIdx + 1) and 3
        val r0x = refVerts[refIdx * 2]
        val r0y = refVerts[refIdx * 2 + 1]
        val r1x = refVerts[rj * 2]
        val r1y = refVerts[rj * 2 + 1]
        var tx = r1x - r0x
        var ty = r1y - r0y
        val tl = sqrt(tx * tx + ty * ty)
        if (tl < 1e-6f) return 0
        tx /= tl; ty /= tl
        val nx = ty        // normale sortante de la face de référence
        val ny = -tx

        // Face incidente : celle dont la normale est la plus opposée à la référence.
        var incIdx = 0
        var minDot = Float.MAX_VALUE
        for (i in 0 until 4) {
            val j = (i + 1) and 3
            val ex = incVerts[j * 2] - incVerts[i * 2]
            val ey = incVerts[j * 2 + 1] - incVerts[i * 2 + 1]
            val el = sqrt(ex * ex + ey * ey)
            if (el < 1e-6f) continue
            val d = (ey / el) * nx + (-ex / el) * ny
            if (d < minDot) { minDot = d; incIdx = i }
        }
        val ij = (incIdx + 1) and 3
        segIn[0] = incVerts[incIdx * 2]; segIn[1] = incVerts[incIdx * 2 + 1]
        segIn[2] = incVerts[ij * 2]; segIn[3] = incVerts[ij * 2 + 1]
        featIn[0] = incIdx; featIn[1] = ij

        // Découpage contre les deux bords latéraux de la face de référence.
        val side0 = tx * r0x + ty * r0y
        val side1 = tx * r1x + ty * r1y
        if (clip(-tx, -ty, -side0, segIn, featIn, segMid, featMid, 8) < 2) return 0
        if (clip(tx, ty, side1, segMid, featMid, segOut, featOut, 9) < 2) return 0

        normalX = if (flip) -nx else nx
        normalY = if (flip) -ny else ny

        var count = 0
        for (i in 0 until 2) {
            val px = segOut[i * 2]
            val py = segOut[i * 2 + 1]
            val sep = (px - r0x) * nx + (py - r0y) * ny
            if (sep <= 0f) {
                val c = out[count]
                c.px = px
                c.py = py
                c.separation = sep
                c.normalImpulse = 0f
                c.tangentImpulse = 0f
                c.feature = (if (flip) 1 shl 16 else 0) or (refIdx shl 8) or featOut[i]
                count++
            }
        }
        return count
    }
}

/** L'ensemble des contacts entre deux corps, conservé d'une image à l'autre. */
class Arbiter(val a: PhysBody, val b: PhysBody) {

    val contacts = Array(2) { Contact() }
    var count = 0
    var normalX = 0f
    var normalY = 0f
    var friction = 0f
    var stamp = 0

    /** Reprend les impulsions des contacts précédents quand ils correspondent (warm starting). */
    fun update(fresh: Array<Contact>, freshCount: Int, nx: Float, ny: Float) {
        for (i in 0 until freshCount) {
            val nc = fresh[i]
            for (j in 0 until count) {
                val oc = contacts[j]
                if (oc.feature == nc.feature) {
                    nc.normalImpulse = oc.normalImpulse
                    nc.tangentImpulse = oc.tangentImpulse
                    break
                }
            }
        }
        for (i in 0 until freshCount) contacts[i].set(fresh[i])
        count = freshCount
        normalX = nx
        normalY = ny
        friction = sqrt(a.friction * b.friction)
    }

    fun preStep(invDt: Float) {
        val allowedPenetration = 0.004f
        val biasFactor = 0.22f
        val nx = normalX
        val ny = normalY
        val tx = ny
        val ty = -nx
        for (i in 0 until count) {
            val c = contacts[i]
            c.rax = c.px - a.x; c.ray = c.py - a.y
            c.rbx = c.px - b.x; c.rby = c.py - b.y

            val rnA = c.rax * ny - c.ray * nx
            val rnB = c.rbx * ny - c.rby * nx
            val kn = a.invMass + b.invMass + a.invI * rnA * rnA + b.invI * rnB * rnB
            c.massNormal = if (kn > 0f) 1f / kn else 0f

            val rtA = c.rax * ty - c.ray * tx
            val rtB = c.rbx * ty - c.rby * tx
            val kt = a.invMass + b.invMass + a.invI * rtA * rtA + b.invI * rtB * rtB
            c.massTangent = if (kt > 0f) 1f / kt else 0f

            // Correction douce de l'interpénétration (Baumgarte)
            c.bias = -biasFactor * invDt * minOf(0f, c.separation + allowedPenetration)

            // Réapplication des impulsions de l'image précédente
            val px = c.normalImpulse * nx + c.tangentImpulse * tx
            val py = c.normalImpulse * ny + c.tangentImpulse * ty
            a.vx -= a.invMass * px; a.vy -= a.invMass * py
            a.omega -= a.invI * (c.rax * py - c.ray * px)
            b.vx += b.invMass * px; b.vy += b.invMass * py
            b.omega += b.invI * (c.rbx * py - c.rby * px)
        }
    }

    /** Une passe du solveur : corrige les vitesses aux points de contact. */
    fun applyImpulse() {
        val nx = normalX
        val ny = normalY
        val tx = ny
        val ty = -nx
        for (i in 0 until count) {
            val c = contacts[i]

            // Vitesse relative au point de contact
            var dvx = (b.vx - b.omega * c.rby) - (a.vx - a.omega * c.ray)
            var dvy = (b.vy + b.omega * c.rbx) - (a.vy + a.omega * c.rax)

            // ── Composante normale : empêche l'interpénétration ──
            val vn = dvx * nx + dvy * ny
            var dPn = c.massNormal * (-vn + c.bias)
            val newPn = maxOf(c.normalImpulse + dPn, 0f)
            dPn = newPn - c.normalImpulse
            c.normalImpulse = newPn
            var px = dPn * nx
            var py = dPn * ny
            a.vx -= a.invMass * px; a.vy -= a.invMass * py
            a.omega -= a.invI * (c.rax * py - c.ray * px)
            b.vx += b.invMass * px; b.vy += b.invMass * py
            b.omega += b.invI * (c.rbx * py - c.rby * px)

            // ── Composante tangentielle : le frottement, borné par la loi de Coulomb ──
            dvx = (b.vx - b.omega * c.rby) - (a.vx - a.omega * c.ray)
            dvy = (b.vy + b.omega * c.rbx) - (a.vy + a.omega * c.rax)
            val vt = dvx * tx + dvy * ty
            var dPt = c.massTangent * (-vt)
            val maxPt = friction * c.normalImpulse
            val oldPt = c.tangentImpulse
            c.tangentImpulse = (oldPt + dPt).coerceIn(-maxPt, maxPt)
            dPt = c.tangentImpulse - oldPt
            px = dPt * tx
            py = dPt * ty
            a.vx -= a.invMass * px; a.vy -= a.invMass * py
            a.omega -= a.invI * (c.rax * py - c.ray * px)
            b.vx += b.invMass * px; b.vy += b.invMass * py
            b.omega += b.invI * (c.rbx * py - c.rby * px)
        }
    }
}

/** Le monde physique : la liste des corps et la boucle de simulation. */
class PhysWorld {

    val bodies = ArrayList<PhysBody>()
    private val arbiters = HashMap<Long, Arbiter>()
    private val fresh = Array(2) { Contact() }
    private val doomed = ArrayList<Long>()

    var gravity = 9.81f

    /** Nombre de passes du solveur : plus il y en a, plus les piles sont stables. */
    var iterations = 14

    private var stamp = 0

    fun add(body: PhysBody) {
        bodies.add(body)
    }

    fun clear() {
        bodies.clear()
        arbiters.clear()
    }

    /** Oublie les contacts mémorisés d'un corps (à faire quand on le téléporte). */
    fun forgetContacts(body: PhysBody) {
        doomed.clear()
        for ((k, arb) in arbiters) if (arb.a === body || arb.b === body) doomed.add(k)
        for (k in doomed) arbiters.remove(k)
    }

    fun step(dt: Float) {
        if (dt <= 0f) return
        val invDt = 1f / dt
        stamp++

        broadPhase()

        // 1. Intégration des forces
        for (bd in bodies) {
            if (!bd.inWorld) continue
            if (bd.invMass > 0f) bd.vy -= gravity * dt
            if (bd.invI > 0f && bd.torque != 0f) bd.omega += bd.invI * bd.torque * dt
            bd.torque = 0f
        }

        // 2. Préparation puis résolution itérative des contacts
        for (arb in arbiters.values) arb.preStep(invDt)
        repeat(iterations) {
            for (arb in arbiters.values) arb.applyImpulse()
        }

        // 3. Intégration des positions + amortissement léger (aide la mise au repos)
        for (bd in bodies) {
            if (!bd.inWorld) continue
            if (bd.invMass > 0f) {
                bd.x += bd.vx * dt
                bd.y += bd.vy * dt
                bd.vx *= 0.999f
                bd.vy *= 0.999f
            }
            if (bd.invI > 0f) {
                bd.angle += bd.omega * dt
                bd.omega *= 0.997f
            }
        }
    }

    /** Recherche des paires en contact (O(n²), largement suffisant ici). */
    private fun broadPhase() {
        for (i in bodies.indices) {
            val a = bodies[i]
            if (!a.inWorld) continue
            for (j in i + 1 until bodies.size) {
                val b = bodies[j]
                if (!b.inWorld) continue
                if (a.immovable && b.immovable) continue

                val dx = b.x - a.x
                val dy = b.y - a.y
                val r = a.boundingRadius + b.boundingRadius
                val key = pairKey(a, b)
                if (dx * dx + dy * dy > r * r) {
                    arbiters.remove(key)
                    continue
                }

                val n = BoxCollider.collide(a, b, fresh)
                if (n > 0) {
                    val arb = arbiters.getOrPut(key) { Arbiter(a, b) }
                    arb.update(fresh, n, BoxCollider.normalX, BoxCollider.normalY)
                    arb.stamp = stamp
                } else {
                    arbiters.remove(key)
                }
            }
        }
        // Nettoyage des contacts qui n'ont pas été revus (corps retirés du monde)
        doomed.clear()
        for ((k, arb) in arbiters) if (arb.stamp != stamp) doomed.add(k)
        for (k in doomed) arbiters.remove(k)
    }

    private fun pairKey(a: PhysBody, b: PhysBody): Long {
        val lo = minOf(a.id, b.id).toLong()
        val hi = maxOf(a.id, b.id).toLong()
        return (hi shl 32) or lo
    }
}
