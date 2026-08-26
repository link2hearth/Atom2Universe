package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

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

    /** Vitesse de rebond visée, calculée avant résolution (0 si le choc est mou). */
    var bounce = 0f
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
 * Une forme d'un corps, mise à plat en coordonnées monde.
 *
 * La détection travaille sur des **formes**, pas sur des corps : un corps peut en
 * porter plusieurs, et chacune doit être testée séparément.
 */
internal class ShapeRef {
    var shape = Shape.BOX
    var x = 0f
    var y = 0f
    var angle = 0f
    var halfW = 0f
    var halfH = 0f
    var radius = 0f

    fun set(body: PhysBody, part: Int) {
        val p = body.parts[part]
        val c = cos(body.angle)
        val s = sin(body.angle)
        shape = p.shape
        x = body.x + p.localX * c - p.localY * s
        y = body.y + p.localX * s + p.localY * c
        angle = body.angle + p.localAngle
        halfW = p.halfW
        halfH = p.halfH
        radius = p.radius
    }

    fun corners(out: FloatArray) {
        val c = cos(angle)
        val s = sin(angle)
        out[0] = x - halfW * c + halfH * s; out[1] = y - halfW * s - halfH * c
        out[2] = x + halfW * c + halfH * s; out[3] = y + halfW * s - halfH * c
        out[4] = x + halfW * c - halfH * s; out[5] = y + halfW * s + halfH * c
        out[6] = x - halfW * c - halfH * s; out[7] = y - halfW * s + halfH * c
    }
}

/**
 * Détection de collision entre deux formes, quelles qu'elles soient.
 *
 * Objet unique avec des tampons réutilisés : le moteur tourne sur un seul thread,
 * donc on évite ainsi toute allocation pendant la simulation.
 */
internal object Collider {

    private val refA = ShapeRef()
    private val refB = ShapeRef()
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

    /** Identifiant de point de contact pour les formes rondes : il n'y en a qu'un. */
    private const val ROUND_FEATURE = -1

    /**
     * Calcule les points de contact entre la forme [pa] de [a] et la forme [pb] de
     * [b], et les écrit dans [out]. Retourne le nombre de points (0 sans collision).
     */
    fun collide(a: PhysBody, pa: Int, b: PhysBody, pb: Int, out: Array<Contact>): Int {
        refA.set(a, pa)
        refB.set(b, pb)
        return when {
            refA.shape == Shape.CIRCLE && refB.shape == Shape.CIRCLE -> circleCircle(refA, refB, out)
            refA.shape == Shape.CIRCLE -> circleBox(refA, refB, out, circleIsA = true)
            refB.shape == Shape.CIRCLE -> circleBox(refB, refA, out, circleIsA = false)
            else -> boxBox(refA, refB, out)
        }
    }

    // --------------------------- Disque contre disque ---------------------------

    private fun circleCircle(a: ShapeRef, b: ShapeRef, out: Array<Contact>): Int {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val d2 = dx * dx + dy * dy
        val r = a.radius + b.radius
        if (d2 > r * r) return 0

        val d = sqrt(d2)
        // Deux centres confondus : la direction est arbitraire, mais il en faut une.
        if (d < 1e-6f) {
            normalX = 0f; normalY = 1f
        } else {
            normalX = dx / d; normalY = dy / d
        }
        val c = out[0]
        c.separation = d - r
        // Point au milieu du recouvrement, sur la ligne des centres.
        c.px = a.x + normalX * (a.radius + c.separation * 0.5f)
        c.py = a.y + normalY * (a.radius + c.separation * 0.5f)
        c.normalImpulse = 0f
        c.tangentImpulse = 0f
        c.feature = ROUND_FEATURE
        return 1
    }

    // ---------------------------- Disque contre boîte ----------------------------

    /**
     * Le principe : on ramène le centre du disque dans le repère de la boîte, on y
     * cherche le point de la boîte le plus proche, et la normale suit ce segment.
     *
     * [circleIsA] dit si le disque est le corps A de la paire : la normale doit
     * toujours aller de A vers B.
     */
    private fun circleBox(
        circle: ShapeRef,
        box: ShapeRef,
        out: Array<Contact>,
        circleIsA: Boolean
    ): Int {
        val c0 = cos(box.angle)
        val s0 = sin(box.angle)
        val dx = circle.x - box.x
        val dy = circle.y - box.y
        // Passage dans le repère de la boîte (rotation inverse)
        val lx = dx * c0 + dy * s0
        val ly = -dx * s0 + dy * c0

        val clampedX = lx.coerceIn(-box.halfW, box.halfW)
        val clampedY = ly.coerceIn(-box.halfH, box.halfH)

        val nlx: Float
        val nly: Float
        val separation: Float

        if (clampedX == lx && clampedY == ly) {
            // Centre du disque à l'intérieur de la boîte : on ressort par la face
            // la plus proche, sinon la normale n'aurait aucune direction définie.
            val dxEdge = box.halfW - abs(lx)
            val dyEdge = box.halfH - abs(ly)
            if (dxEdge < dyEdge) {
                nlx = if (lx < 0f) -1f else 1f
                nly = 0f
                separation = -dxEdge - circle.radius
            } else {
                nlx = 0f
                nly = if (ly < 0f) -1f else 1f
                separation = -dyEdge - circle.radius
            }
        } else {
            val ox = lx - clampedX
            val oy = ly - clampedY
            val dist = sqrt(ox * ox + oy * oy)
            if (dist > circle.radius) return 0
            if (dist < 1e-6f) {
                nlx = 0f; nly = 1f
            } else {
                nlx = ox / dist; nly = oy / dist
            }
            separation = dist - circle.radius
        }

        // Normale de la boîte vers le disque, ramenée dans le repère monde.
        val wnx = nlx * c0 - nly * s0
        val wny = nlx * s0 + nly * c0

        val c = out[0]
        c.separation = separation
        // Point de contact : sur la surface du disque, du côté de la boîte.
        c.px = circle.x - wnx * circle.radius
        c.py = circle.y - wny * circle.radius
        c.normalImpulse = 0f
        c.tangentImpulse = 0f
        c.feature = ROUND_FEATURE

        if (circleIsA) {
            // A = disque, B = boîte : la normale doit pointer vers la boîte.
            normalX = -wnx; normalY = -wny
        } else {
            normalX = wnx; normalY = wny
        }
        return 1
    }

    // ---------------------------- Boîte contre boîte ----------------------------

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

    private fun boxBox(a: ShapeRef, b: ShapeRef, out: Array<Contact>): Int {
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

/**
 * L'ensemble des contacts entre deux formes, conservé d'une image à l'autre.
 *
 * Les impulsions, elles, s'appliquent aux **corps** : c'est leur centre de masse
 * qui bouge, quelle que soit la forme touchée.
 */
class Arbiter(
    val a: PhysBody,
    val b: PhysBody,
    val partA: Int = 0,
    val partB: Int = 0
) {

    val contacts = Array(2) { Contact() }
    var count = 0
    var normalX = 0f
    var normalY = 0f
    var friction = 0f
    var restitution = 0f
    var stamp = 0

    /**
     * Vrai quand les deux corps se sont vraiment percutés à ce pas, par opposition
     * à un contact qui ne fait que porter un poids. C'est ce qui distingue un boulet
     * qui frappe un mur d'une caisse tranquillement posée dessus.
     */
    var impacting = false
        private set

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
        restitution = maxOf(a.restitution, b.restitution)
    }

    /** Somme des impulsions normales appliquées au pas écoulé, en kg·m/s. */
    fun totalNormalImpulse(): Float {
        var s = 0f
        for (i in 0 until count) s += contacts[i].normalImpulse
        return s
    }

    /**
     * Prépare la résolution. [impactSpeed] est la vitesse d'approche à partir de
     * laquelle on considère qu'il y a choc : en dessous, pas de rebond et pas de dégât.
     */
    fun preStep(invDt: Float, impactSpeed: Float) {
        val allowedPenetration = 0.004f
        val biasFactor = 0.22f
        val maxBiasSpeed = 3f
        val nx = normalX
        val ny = normalY
        val tx = ny
        val ty = -nx

        // Première passe : la vitesse d'approche réelle, mesurée avant que le
        // solveur ne touche à quoi que ce soit. Elle sert au rebond et aux dégâts.
        impacting = false
        for (i in 0 until count) {
            val c = contacts[i]
            val rax = c.px - a.x
            val ray = c.py - a.y
            val rbx = c.px - b.x
            val rby = c.py - b.y
            val dvx = (b.vx - b.omega * rby) - (a.vx - a.omega * ray)
            val dvy = (b.vy + b.omega * rbx) - (a.vy + a.omega * rax)
            val vn = dvx * nx + dvy * ny
            if (vn < -impactSpeed) {
                impacting = true
                c.bounce = -restitution * vn
            } else {
                c.bounce = 0f
            }
        }

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

            // Correction douce de l'interpénétration (Baumgarte), plafonnée.
            //
            // Le plafond est indispensable depuis les sous-pas : cette correction
            // est proportionnelle à 1/dt, donc découper une image en seize la rend
            // seize fois plus violente. Un boulet coincé dans un angle se faisait
            // éjecter à 20 m/s — toujours la même vitesse, quelle que soit la
            // machine, signature d'une correction devenue folle plutôt que d'un
            // vrai lancer. À pas fixe la valeur reste très en dessous du plafond,
            // donc rien ne change pour le jeu d'équilibre.
            c.bias = (-biasFactor * invDt * minOf(0f, c.separation + allowedPenetration))
                .coerceAtMost(maxBiasSpeed)

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

            // -- Composante normale : empêche l'interpénétration, et fait rebondir --
            val vn = dvx * nx + dvy * ny
            // On prend la plus exigeante des deux corrections, jamais leur somme :
            // les additionner ajoutait de l'énergie, et une balle rebondissait plus
            // haut que ne l'autorise son élasticité.
            var dPn = c.massNormal * (-vn + maxOf(c.bias, c.bounce))
            val newPn = maxOf(c.normalImpulse + dPn, 0f)
            dPn = newPn - c.normalImpulse
            c.normalImpulse = newPn
            var px = dPn * nx
            var py = dPn * ny
            a.vx -= a.invMass * px; a.vy -= a.invMass * py
            a.omega -= a.invI * (c.rax * py - c.ray * px)
            b.vx += b.invMass * px; b.vy += b.invMass * py
            b.omega += b.invI * (c.rbx * py - c.rby * px)

            // -- Composante tangentielle : le frottement, borné par la loi de Coulomb --
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
