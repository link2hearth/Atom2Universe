package com.Atom2Universe.app.games.physics

import kotlin.math.abs
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * Le monde physique : la liste des corps, celle des liaisons, et la boucle de
 * simulation.
 *
 * Le fonctionnement reprend celui de Box2D-Lite, en trois temps à chaque pas :
 *  1. **détection** des contacts (théorème de l'axe séparateur pour les boîtes,
 *     point le plus proche pour les disques) ;
 *  2. **résolution** des contacts et des liaisons par impulsions séquentielles
 *     (plusieurs passes qui corrigent tour à tour chaque point) ;
 *  3. **intégration** des positions.
 *
 * Les impulsions sont conservées d'une image à l'autre (« warm starting ») :
 * c'est ce qui rend une pile de briques stable au lieu de trembler.
 */
class PhysWorld {

    val bodies = ArrayList<PhysBody>()
    val joints = ArrayList<RevoluteJoint>()
    private val arbiters = HashMap<Long, Arbiter>()
    private val fresh = Array(2) { Contact() }
    private val doomed = ArrayList<Long>()

    var gravity = 9.81f

    /** Nombre de passes du solveur : plus il y en a, plus les piles sont stables. */
    var iterations = 14

    /**
     * Vitesse d'approche à partir de laquelle un contact compte comme un choc :
     * en dessous, pas de rebond et pas de dégât. Sans ce seuil, une caisse posée
     * par terre s'abîmerait toute seule sous son propre poids et tremblerait.
     */
    var impactSpeedThreshold = 0.5f

    /** Nombre maximal de sous-pas consentis par image (voir [stepFrame]). */
    var maxSubSteps = 32

    private var stamp = 0

    fun add(body: PhysBody) {
        bodies.add(body)
    }

    fun remove(body: PhysBody) {
        bodies.remove(body)
        forgetContacts(body)
        joints.removeAll { it.a === body || it.b === body }
    }

    fun addJoint(joint: RevoluteJoint) {
        joints.add(joint)
    }

    fun removeJoint(joint: RevoluteJoint) {
        joints.remove(joint)
    }

    fun clear() {
        bodies.clear()
        joints.clear()
        arbiters.clear()
    }

    /** Oublie les contacts mémorisés d'un corps (à faire quand on le téléporte). */
    fun forgetContacts(body: PhysBody) {
        doomed.clear()
        for ((k, arb) in arbiters) if (arb.a === body || arb.b === body) doomed.add(k)
        for (k in doomed) arbiters.remove(k)
    }

    /** Remet à zéro les chocs encaissés par tous les corps. */
    fun clearImpacts() {
        for (bd in bodies) bd.impactAccum = 0f
    }

    /**
     * Simule une image entière, en la découpant en autant de sous-pas qu'il faut
     * pour que rien ne traverse rien.
     *
     * Le moteur teste les collisions à des positions figées : un boulet à 30 m/s
     * avance de 50 cm par image à 60 Hz, et passerait **au travers** d'une planche
     * de 10 cm sans jamais la toucher. On mesure donc le corps le plus rapide et
     * le corps le plus mince, et on subdivise le pas jusqu'à ce que le premier ne
     * puisse plus franchir le second d'un seul bond.
     *
     * Au repos, [maxSubSteps] n'est jamais atteint : un monde tranquille coûte un
     * seul sous-pas, exactement comme avant.
     */
    fun stepFrame(dt: Float) {
        if (dt <= 0f) return
        // On consomme l'image par tranches, en **recalculant la taille de la
        // tranche après chacune**. C'est indispensable dès qu'un choc entre en jeu :
        // le contrepoids d'une machine de jet frappe le bras au milieu de l'image et
        // le fait passer de zéro à trois tours par seconde. Un découpage décidé une
        // fois pour toutes au début de l'image aurait taillé les pas pour un bras
        // immobile, et le reste de l'image se serait joué à pleine vitesse avec des
        // pas énormes — le boulet traversait sa butée.
        var remaining = dt
        var guard = 0
        while (remaining > 1e-6f && guard < 4 * maxSubSteps) {
            val h = minOf(remaining, safeStep(dt))
            step(h)
            remaining -= h
            guard++
        }
    }

    /**
     * Durée pendant laquelle, à l'état actuel, rien ne peut franchir la plus petite
     * épaisseur du monde.
     *
     * La vitesse retenue est celle du **point le plus rapide** de chaque corps,
     * rotation comprise : le centre d'un bras de douze mètres avance lentement
     * pendant que son extrémité file à vingt mètres par seconde.
     */
    private fun safeStep(frameDt: Float): Float {
        var fastest = 0f
        var thinnest = Float.MAX_VALUE
        for (bd in bodies) {
            if (!bd.inWorld) continue
            if (bd.smallestHalfExtent < thinnest) thinnest = bd.smallestHalfExtent
            if (bd.invMass > 0f || bd.invI > 0f) {
                val s = sqrt(bd.speedSq) + abs(bd.omega) * bd.boundingRadius
                if (s > fastest) fastest = s
            }
        }
        if (thinnest == Float.MAX_VALUE || fastest <= 0f) return frameDt
        // Marge de deux : deux corps peuvent se croiser en sens contraire, et leur
        // rapprochement vaut alors la somme de leurs vitesses.
        val safe = thinnest * 0.5f / fastest
        // Le plancher garantit que l'image finit toujours par être consommée, même
        // face à une vitesse aberrante.
        return safe.coerceIn(frameDt / maxSubSteps, frameDt)
    }

    /** Nombre de sous-pas que [stepFrame] emploierait pour une image de [dt]. */
    fun subStepsFor(dt: Float): Int =
        ceil(dt / safeStep(dt)).toInt().coerceIn(1, maxSubSteps)

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

        // 2. Préparation puis résolution itérative des contacts et des liaisons
        for (arb in arbiters.values) arb.preStep(invDt, impactSpeedThreshold)
        for (j in joints) j.preStep(invDt)
        repeat(iterations) {
            for (arb in arbiters.values) arb.applyImpulse()
            for (j in joints) j.applyImpulse()
        }

        // 2 bis. Comptabilisation des chocs, pour les cibles qui doivent casser.
        for (arb in arbiters.values) {
            if (!arb.impacting) continue
            val p = arb.totalNormalImpulse()
            arb.a.impactAccum += p
            arb.b.impactAccum += p
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

    /**
     * Vrai quand plus rien ne bouge dans le monde. C'est ce qui dit à un jeu de
     * tir que le coup est terminé : le boulet s'est arrêté, les débris aussi.
     */
    fun isAtRest(linearTol: Float = 0.05f, angularTol: Float = 0.08f): Boolean {
        for (bd in bodies) {
            if (!bd.inWorld) continue
            if (!bd.atRest(linearTol, angularTol)) return false
        }
        return true
    }

    /** Vitesse du corps le plus rapide, en m/s (0 si tout dort). */
    fun fastestSpeed(): Float {
        var best = 0f
        for (bd in bodies) {
            if (!bd.inWorld || bd.invMass == 0f) continue
            val s = bd.speedSq
            if (s > best) best = s
        }
        return sqrt(best)
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
                if (!a.collidesWith(b)) continue

                // Rejet rapide au niveau des corps entiers, avant d'entrer dans
                // le détail de leurs formes.
                val dx = b.x - a.x
                val dy = b.y - a.y
                val r = a.boundingRadius + b.boundingRadius
                val far = dx * dx + dy * dy > r * r
                if (connectedByJoint(a, b)) continue

                for (pa in a.parts.indices) {
                    for (pb in b.parts.indices) {
                        val key = pairKey(a, pa, b, pb)
                        if (far) {
                            arbiters.remove(key)
                            continue
                        }
                        val n = Collider.collide(a, pa, b, pb, fresh)
                        if (n > 0) {
                            val arb = arbiters.getOrPut(key) { Arbiter(a, b, pa, pb) }
                            arb.update(fresh, n, Collider.normalX, Collider.normalY)
                            arb.stamp = stamp
                        } else {
                            arbiters.remove(key)
                        }
                    }
                }
            }
        }
        // Nettoyage des contacts qui n'ont pas été revus (corps retirés du monde)
        doomed.clear()
        for ((k, arb) in arbiters) if (arb.stamp != stamp) doomed.add(k)
        for (k in doomed) arbiters.remove(k)
    }

    /** Vrai si une liaison relie déjà ces deux corps et interdit leur collision. */
    private fun connectedByJoint(a: PhysBody, b: PhysBody): Boolean {
        for (j in joints) {
            if (j.collideConnected) continue
            if ((j.a === a && j.b === b) || (j.a === b && j.b === a)) return true
        }
        return false
    }

    /**
     * Clé d'un contact : le couple de corps **et** le couple de formes touchées.
     * Deux formes d'un même corps qui touchent le même voisin doivent avoir chacune
     * leur propre suivi, sinon leurs impulsions mémorisées se mélangeraient.
     */
    private fun pairKey(a: PhysBody, pa: Int, b: PhysBody, pb: Int): Long {
        val ida = a.id.toLong() and 0xFFFFFF
        val idb = b.id.toLong() and 0xFFFFFF
        return (ida shl 40) or (idb shl 16) or ((pa.toLong() and 0xFF) shl 8) or (pb.toLong() and 0xFF)
    }
}