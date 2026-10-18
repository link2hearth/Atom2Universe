package com.Atom2Universe.app.games.caves.render

import kotlin.math.hypot
import kotlin.math.max

/**
 * Fissures d'un bloc qu'on casse : un motif en étoile, symétrique (les 8 symétries du carré),
 * qui s'étend du centre vers les bords au fil de la casse, sur chaque face visible du bloc.
 *
 * Le motif est écrit une seule fois, dans un huitième de face (0 ≤ v ≤ u), puis recopié par
 * symétrie : c'est ce qui garantit qu'il reste parfaitement régulier. Coordonnées de face :
 * centre en (0, 0), bords en ±0,5.
 *
 * Les sommets sont dessinés en mode « assombrir » (glBlendFunc ZERO, ONE_MINUS_SRC_COLOR) :
 * la couleur n'est pas ajoutée au bloc, elle le fonce. Un fin liseré clair plus large
 * en dessous (le halo) donne du relief à la fissure.
 */
internal object BlockCracks {
    /** Ordre des faces : +x, -x, +y, -y, +z, -z (comme les normales du moteur). */
    val NORMALS = arrayOf(
        intArrayOf(1, 0, 0), intArrayOf(-1, 0, 0), intArrayOf(0, 1, 0),
        intArrayOf(0, -1, 0), intArrayOf(0, 0, 1), intArrayOf(0, 0, -1)
    )

    /** Un trait : sa polyligne (u, v, u, v…), la casse où il apparaît, où il est complet, son épaisseur. */
    private class Line(val pts: FloatArray, val from: Float, val to: Float, val half: Float)

    private val LINES = listOf(
        // Le cœur : petit octogone autour du point d'impact.
        Line(floatArrayOf(.05f, 0f, .04f, .04f), .03f, .12f, .007f),
        // Les huit branches principales : quatre le long des axes, quatre le long des diagonales.
        Line(floatArrayOf(.05f, 0f, .13f, .03f, .20f, .012f, .30f, .05f, .38f, .03f, .48f, .06f), .06f, .50f, .008f),
        Line(floatArrayOf(.04f, .04f, .12f, .09f, .17f, .15f, .26f, .20f, .31f, .27f, .40f, .31f, .44f, .40f, .48f, .44f), .10f, .62f, .008f),
        // Un maillon entre les deux branches, puis un anneau : l'éclatement de la pierre.
        Line(floatArrayOf(.13f, .03f, .19f, .09f, .17f, .15f), .32f, .46f, .006f),
        Line(floatArrayOf(.30f, .05f, .33f, .14f, .40f, .19f, .48f, .20f), .40f, .66f, .006f),
        Line(floatArrayOf(.24f, .027f, .27f, .09f, .22f, .178f), .52f, .72f, .006f),
        // Les éclats du bord, pour les derniers instants.
        Line(floatArrayOf(.38f, .03f, .42f, .10f, .48f, .13f), .66f, .84f, .005f),
        Line(floatArrayOf(.31f, .27f, .37f, .22f, .42f, .24f), .70f, .88f, .005f)
    )

    private const val HALO_SHADE = .26f
    private const val CORE_SHADE = .86f

    /** Les 8 symétries du carré, appliquées à (u, v) : rotations puis miroirs. */
    private fun mirror(i: Int, u: Float, v: Float, out: FloatArray) {
        val flip = i >= 4
        var a = u; var b = v
        if (flip) b = -b
        repeat(i and 3) { val t = -b; b = a; a = t }
        out[0] = a; out[1] = b
    }

    /**
     * Un morceau de face à fissurer : [face] (ordre de [NORMALS]), [plane] sa position le long de la
     * normale dans le bloc (0..1), puis son rectangle (u0..u1, v0..v1) dans le plan de la face, en
     * coordonnées de bloc (0..1). Axes : faces ±x → u = z, v = y ; faces ±y → u = x, v = z ;
     * faces ±z → u = x, v = y. Un cube entier donne six morceaux de 0..1 ; une dalle ou un escalier,
     * un morceau par face réelle : le motif se centre sur le morceau et s'arrête à son bord.
     */
    class Patch(val face: Int, val plane: Float, val u0: Float, val u1: Float, val v0: Float, val v1: Float)

    /**
     * @param x,y,z coin du bloc, relatif à la caméra
     * @param progress casse du bloc, de 0 à 1
     * @return sommets (x, y, z, r, g, b) en triangles
     */
    fun build(x: Float, y: Float, z: Float, patches: List<Patch>, progress: Float): FloatArray {
        if (progress < .03f) return FloatArray(0)
        val out = ArrayList<Float>(2048)
        val m0 = FloatArray(2); val m1 = FloatArray(2)
        val poly = FloatArray(16); val tmp = FloatArray(16)
        for (patch in patches) {
            val n = NORMALS[patch.face]
            val cu = (patch.u0 + patch.u1) / 2; val cv = (patch.v0 + patch.v1) / 2
            val hu = (patch.u1 - patch.u0) / 2; val hv = (patch.v1 - patch.v0) / 2
            // Axes de la face (u, v) dans le monde, et position du centre du morceau.
            val ux: Float; val uy: Float; val uz: Float; val vx: Float; val vy: Float; val vz: Float
            val cx: Float; val cy: Float; val cz: Float
            val lift = (if (n[0] + n[1] + n[2] > 0) LIFT else -LIFT)
            when {
                n[0] != 0 -> { ux = 0f; uy = 0f; uz = 1f; vx = 0f; vy = 1f; vz = 0f
                    cx = x + patch.plane + lift; cy = y + cv; cz = z + cu }
                n[1] != 0 -> { ux = 1f; uy = 0f; uz = 0f; vx = 0f; vy = 0f; vz = 1f
                    cx = x + cu; cy = y + patch.plane + lift; cz = z + cv }
                else -> { ux = 1f; uy = 0f; uz = 0f; vx = 0f; vy = 1f; vz = 0f
                    cx = x + cu; cy = y + cv; cz = z + patch.plane + lift }
            }
            fun vert(u: Float, v: Float, shade: Float) {
                out.add(cx + ux * u + vx * v); out.add(cy + uy * u + vy * v); out.add(cz + uz * u + vz * v)
                out.add(shade); out.add(shade); out.add(shade)
            }
            /** Découpe le triangle (6 floats) au rectangle du morceau, puis l'émet. */
            fun clippedTriangle(t: FloatArray, shade: Float) {
                var count = 3
                System.arraycopy(t, 0, poly, 0, 6)
                for (edge in 0..3) {
                    var outCount = 0
                    for (i in 0 until count) {
                        val ax = poly[i * 2]; val ay = poly[i * 2 + 1]
                        val j = (i + 1) % count
                        val bx = poly[j * 2]; val by = poly[j * 2 + 1]
                        // Distance signée au bord : positive dedans.
                        val da = when (edge) { 0 -> hu - ax; 1 -> ax + hu; 2 -> hv - ay; else -> ay + hv }
                        val db = when (edge) { 0 -> hu - bx; 1 -> bx + hu; 2 -> hv - by; else -> by + hv }
                        if (da >= 0f) { tmp[outCount * 2] = ax; tmp[outCount * 2 + 1] = ay; outCount++ }
                        if ((da >= 0f) != (db >= 0f)) {
                            val f = da / (da - db)
                            tmp[outCount * 2] = ax + (bx - ax) * f; tmp[outCount * 2 + 1] = ay + (by - ay) * f; outCount++
                        }
                    }
                    System.arraycopy(tmp, 0, poly, 0, outCount * 2)
                    count = outCount
                    if (count < 3) return
                }
                for (i in 1 until count - 1) {
                    vert(poly[0], poly[1], shade); vert(poly[i * 2], poly[i * 2 + 1], shade)
                    vert(poly[i * 2 + 2], poly[i * 2 + 3], shade)
                }
            }
            val quad = FloatArray(6)
            fun ribbon(au: Float, av: Float, bu: Float, bv: Float, half: Float, shade: Float) {
                val du = bu - au; val dv = bv - av
                val len = max(hypot(du, dv), 1e-4f)
                val pu = -dv / len * half; val pv = du / len * half
                quad[0] = au - pu; quad[1] = av - pv; quad[2] = bu - pu; quad[3] = bv - pv; quad[4] = bu + pu; quad[5] = bv + pv
                clippedTriangle(quad, shade)
                quad[0] = au - pu; quad[1] = av - pv; quad[2] = bu + pu; quad[3] = bv + pv; quad[4] = au + pu; quad[5] = av + pv
                clippedTriangle(quad, shade)
            }
            for (line in LINES) {
                val reveal = ((progress - line.from) / (line.to - line.from)).coerceIn(0f, 1f)
                if (reveal <= 0f) continue
                var total = 0f
                for (k in 2 until line.pts.size step 2) total += hypot(line.pts[k] - line.pts[k - 2], line.pts[k + 1] - line.pts[k - 1])
                var left = total * reveal
                for (k in 2 until line.pts.size step 2) {
                    if (left <= 0f) break
                    val au = line.pts[k - 2]; val av = line.pts[k - 1]
                    var bu = line.pts[k]; var bv = line.pts[k + 1]
                    val seg = hypot(bu - au, bv - av)
                    if (seg > left) { val f = left / seg; bu = au + (bu - au) * f; bv = av + (bv - av) * f }
                    left -= seg
                    for (s in 0..7) {
                        mirror(s, au, av, m0); mirror(s, bu, bv, m1)
                        ribbon(m0[0], m0[1], m1[0], m1[1], line.half + HALO, HALO_SHADE)
                        ribbon(m0[0], m0[1], m1[0], m1[1], line.half, CORE_SHADE)
                    }
                }
            }
        }
        return out.toFloatArray()
    }

    /** Décollement de la fissure par rapport à la face, en blocs. */
    private const val LIFT = .012f
    private const val HALO = .008f
}
