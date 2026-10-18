package com.Atom2Universe.app.science

import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

/**
 * [StellarSurfaceShader] recopié ligne à ligne pour le processeur.
 *
 * Le shader peint les étoiles de l'échelle cosmique en direct, sur la carte graphique. Un
 * jeu en canevas 2D (Accrétion) veut la même photosphère, mais cuite une fois dans une
 * image : même bruit, mêmes réglages, donc la même étoile d'un module à l'autre. Si l'on
 * retouche l'un, on retouche l'autre.
 */
internal object StellarSurfaceCpu {

    /** Réglages du motif, dans l'ordre de `uPattern` : taille des cellules, contraste, taches, graine. */
    class Pattern(val scale: Float, val contrast: Float, val spots: Float, val seed: Float)

    private fun fract(v: Float) = v - floor(v)

    private fun hash(x: Float, y: Float, z: Float): Float {
        var px = fract(x * 0.1031f); var py = fract(y * 0.1031f); var pz = fract(z * 0.1031f)
        val d = px * (py + 19.19f) + py * (pz + 19.19f) + pz * (px + 19.19f)
        px += d; py += d; pz += d
        return fract((px + py) * pz)
    }

    private fun noise(x: Float, y: Float, z: Float): Float {
        val ix = floor(x); val iy = floor(y); val iz = floor(z)
        var fx = x - ix; var fy = y - iy; var fz = z - iz
        fx = fx * fx * (3f - 2f * fx); fy = fy * fy * (3f - 2f * fy); fz = fz * fz * (3f - 2f * fz)
        fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t
        return mix(
            mix(mix(hash(ix, iy, iz), hash(ix + 1, iy, iz), fx),
                mix(hash(ix, iy + 1, iz), hash(ix + 1, iy + 1, iz), fx), fy),
            mix(mix(hash(ix, iy, iz + 1), hash(ix + 1, iy, iz + 1), fx),
                mix(hash(ix, iy + 1, iz + 1), hash(ix + 1, iy + 1, iz + 1), fx), fy), fz)
    }

    private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /**
     * Couleur d'un point de la photosphère : ([px], [py], [pz]) est le point sur la sphère
     * unité, [mu] le cosinus de l'angle avec le regard (1 au centre du disque, 0 au bord).
     * Rend une couleur opaque 0xFFRRGGBB.
     */
    fun color(px: Float, py: Float, pz: Float, mu: Float, tint: Int, pattern: Pattern, time: Float = 0f): Int {
        val dx = sin(time * 0.19f); val dy = cos(time * 0.13f); val dz = sin(time * 0.17f)
        val s = pattern.scale; val w = pattern.seed
        val qx = px * s + w; val qy = py * s + w; val qz = pz * s + w
        val warp = noise(qx * 0.45f + dx, qy * 0.45f + dy, qz * 0.45f + dz)
        val cells = noise(qx + dx * 0.35f + warp * 0.7f, qy + dy * 0.35f + warp * 0.7f, qz + dz * 0.35f + warp * 0.7f)
        val fine = noise(qx * 3.1f - dx * 0.2f, qy * 3.1f - dy * 0.2f, qz * 3.1f - dz * 0.2f)
        var light = 1.04f + (cells - 0.5f) * pattern.contrast * 1.35f + (fine - 0.5f) * pattern.contrast * 0.35f
        val spots = smoothstep(0.72f, 0.86f, noise(px * 8f + w + dx * 0.1f, py * 8f + w + dy * 0.1f, pz * 8f + w + dz * 0.1f))
        light *= 1f - spots * pattern.spots
        val limb = 0.78f + 0.22f * max(mu, 0f).pow(0.45f)
        var brightness = max(light * limb * 1.08f, 0f)
        if (brightness > 0.90f) brightness = 0.90f + 0.10f * (1f - exp(-(brightness - 0.90f) * 7f))
        fun channel(c: Int) = ((c / 255f).pow(1.18f) * brightness * 255f).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (channel((tint shr 16) and 0xFF) shl 16) or
            (channel((tint shr 8) and 0xFF) shl 8) or channel(tint and 0xFF)
    }
}
