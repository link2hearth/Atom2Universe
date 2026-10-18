package com.Atom2Universe.app.games.toyboxracers.track

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.tan

/** Un coin du tracé et le rayon du virage qui l'arrondit. */
internal data class Corner(val x: Float, val z: Float, val radius: Float)

/**
 * Boucle fermée vue de dessus, paramétrée par la distance parcourue.
 *
 * Deux façons de la dessiner :
 * - [spline] : des points de passage reliés par une Catmull-Rom *centripète*. Contrairement à la
 *   version uniforme, elle ne fait jamais de boucle ni de pointe entre deux points rapprochés —
 *   c'était la cause des épingles repliées : deux points serrés dans un virage, la courbe partait
 *   en vrille et le bord intérieur de la route repassait sur lui-même.
 * - [corners] : un polygone dont chaque coin est arrondi par un arc de cercle de rayon choisi.
 *   Les lignes droites sont vraiment droites et chaque virage a exactement le rayon voulu : c'est
 *   la façon de garantir qu'aucun virage n'est plus serré que la demi-largeur de la route.
 *
 * Tout ce qui se règle « le long du tour » (bosses, tremplins, largeur) se lit ensuite en mètres
 * réels : une bosse garde la même pente qu'elle tombe dans une ligne droite ou dans un virage.
 */
internal class PlanarLoop private constructor(private val xs: FloatArray, private val zs: FloatArray) {
    private val cumulative = FloatArray(xs.size)
    val length: Float

    init {
        for (i in 1 until xs.size) cumulative[i] = cumulative[i - 1] + hypot(xs[i] - xs[i - 1], zs[i] - zs[i - 1])
        length = cumulative.last()
    }

    /** Position à `distance` mètres du départ, en tournant autant de fois que nécessaire. */
    fun at(distance: Float): Pair<Float, Float> {
        var s = distance % length
        if (s < 0f) s += length
        var low = 0
        var high = cumulative.size - 1
        while (high - low > 1) {
            val middle = (low + high) ushr 1
            if (cumulative[middle] <= s) low = middle else high = middle
        }
        val span = (cumulative[high] - cumulative[low]).coerceAtLeast(1e-6f)
        val t = (s - cumulative[low]) / span
        return xs[low] + (xs[high] - xs[low]) * t to zs[low] + (zs[high] - zs[low]) * t
    }

    /**
     * Fraction du tour la plus proche de ce point de la pièce. Là où la route repasse au même
     * endroit (pont, hélice), [near] désigne le passage voulu : seuls les points à moins de
     * 0,15 tour de cette fraction comptent.
     */
    fun fractionNear(x: Float, z: Float, near: Float = -1f): Float {
        var best = 0f
        var bestDistance = Float.MAX_VALUE
        for (i in 0 until xs.size - 1) {
            // Projection sur chaque segment : une ligne droite n'a que ses deux extrémités.
            val dx = xs[i + 1] - xs[i]; val dz = zs[i + 1] - zs[i]
            val span = dx * dx + dz * dz
            val t = if (span < 1e-9f) 0f else (((x - xs[i]) * dx + (z - zs[i]) * dz) / span).coerceIn(0f, 1f)
            val s = cumulative[i] + (cumulative[i + 1] - cumulative[i]) * t
            val f = s / length
            if (near >= 0f) {
                var d = abs(f - near) % 1f
                if (d > .5f) d = 1f - d
                if (d > .15f) continue
            }
            val distance = hypot(xs[i] + dx * t - x, zs[i] + dz * t - z)
            if (distance < bestDistance) { bestDistance = distance; best = f }
        }
        return best
    }

    companion object {
        fun spline(points: List<Pair<Float, Float>>, subdivisions: Int = 64): PlanarLoop {
            require(points.size >= 4)
            val n = points.size
            val xs = ArrayList<Float>()
            val zs = ArrayList<Float>()
            for (i in 0 until n) {
                val p0 = points[(i - 1 + n) % n]
                val p1 = points[i]
                val p2 = points[(i + 1) % n]
                val p3 = points[(i + 2) % n]
                fun knot(a: Pair<Float, Float>, b: Pair<Float, Float>) =
                    hypot(b.first - a.first, b.second - a.second).toDouble().pow(.5).toFloat().coerceAtLeast(1e-3f)
                val t1 = knot(p0, p1)
                val t2 = t1 + knot(p1, p2)
                val t3 = t2 + knot(p2, p3)
                for (k in 0 until subdivisions) {
                    val t = t1 + (t2 - t1) * k / subdivisions
                    // Formulation pyramidale de Barry et Goldman, nœuds en racine des distances.
                    fun axis(a: Float, b: Float, c: Float, d: Float): Float {
                        val a1 = (t1 - t) / t1 * a + t / t1 * b
                        val a2 = (t2 - t) / (t2 - t1) * b + (t - t1) / (t2 - t1) * c
                        val a3 = (t3 - t) / (t3 - t2) * c + (t - t2) / (t3 - t2) * d
                        val b1 = (t2 - t) / t2 * a1 + t / t2 * a2
                        val b2 = (t3 - t) / (t3 - t1) * a2 + (t - t1) / (t3 - t1) * a3
                        return (t2 - t) / (t2 - t1) * b1 + (t - t1) / (t2 - t1) * b2
                    }
                    xs += axis(p0.first, p1.first, p2.first, p3.first)
                    zs += axis(p0.second, p1.second, p2.second, p3.second)
                }
            }
            xs += xs[0]; zs += zs[0]
            return PlanarLoop(xs.toFloatArray(), zs.toFloatArray())
        }

        /**
         * Polygone arrondi : chaque coin devient un arc de son rayon, tangent aux deux côtés.
         * Deux arcs voisins ne doivent pas se chevaucher sur le côté qui les relie.
         */
        fun corners(corners: List<Corner>): PlanarLoop {
            require(corners.size >= 3)
            val n = corners.size
            val xs = ArrayList<Float>()
            val zs = ArrayList<Float>()
            val tangents = FloatArray(n)
            var firstExit = 0
            for (i in 0 until n) {
                if (i == 1) firstExit = xs.size - 1
                val p = corners[i]
                val a = corners[(i - 1 + n) % n]
                val b = corners[(i + 1) % n]
                val ux = a.x - p.x; val uz = a.z - p.z
                val vx = b.x - p.x; val vz = b.z - p.z
                val lu = hypot(ux, uz); val lv = hypot(vx, vz)
                val inner = acos(((ux * vx + uz * vz) / (lu * lv)).coerceIn(-1f, 1f))
                // Angle intérieur proche de 180° : coin presque plat, pas d'arc.
                if (inner > PI.toFloat() - 1e-3f) {
                    tangents[i] = 0f
                    xs += p.x; zs += p.z
                    continue
                }
                val t = p.radius / tan(inner / 2f)
                tangents[i] = t
                val sx = p.x + ux / lu * t; val sz = p.z + uz / lu * t
                val ex = p.x + vx / lv * t; val ez = p.z + vz / lv * t
                val bx = ux / lu + vx / lv; val bz = uz / lu + vz / lv
                val bl = hypot(bx, bz)
                val centerDistance = p.radius / sin(inner / 2f)
                val cx = p.x + bx / bl * centerDistance
                val cz = p.z + bz / bl * centerDistance
                val a0 = atan2(sz - cz, sx - cx)
                val a1 = atan2(ez - cz, ex - cx)
                var sweep = a1 - a0
                while (sweep > PI) sweep -= (2 * PI).toFloat()
                while (sweep < -PI) sweep += (2 * PI).toFloat()
                val steps = ceil(abs(sweep) * p.radius / .4f).toInt().coerceAtLeast(2)
                for (k in 0..steps) {
                    val angle = a0 + sweep * k / steps
                    xs += cx + cos(angle) * p.radius
                    zs += cz + sin(angle) * p.radius
                }
            }
            for (i in 0 until n) {
                val p = corners[i]; val q = corners[(i + 1) % n]
                require(tangents[i] + tangents[(i + 1) % n] <= hypot(q.x - p.x, q.z - p.z) + .01f) {
                    "Virages ${p} et ${q} trop larges pour le côté qui les relie"
                }
            }
            // Le tour commence à la sortie du premier virage : la ligne de départ est sur un droit.
            val rx = xs.subList(firstExit, xs.size) + xs.subList(0, firstExit + 1)
            val rz = zs.subList(firstExit, zs.size) + zs.subList(0, firstExit + 1)
            return PlanarLoop(rx.toFloatArray(), rz.toFloatArray())
        }
    }
}
