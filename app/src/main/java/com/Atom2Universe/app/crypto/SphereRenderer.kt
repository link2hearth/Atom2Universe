package com.Atom2Universe.app.crypto

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.*

/**
 * Rendu logiciel d'une sphère texturée en projection orthographique.
 * Pour chaque pixel visible de la sphère, on calcule la direction 3D
 * correspondante, on la convertit en coordonnées géographiques, et on
 * lit la couleur dans la texture équirectangulaire.
 *
 * C'est la seule façon d'afficher les vrais continents à leur position
 * correcte sans OpenGL.
 */
object SphereRenderer {

    /**
     * Rend la Terre comme une sphère texturée depuis le point de vue donné.
     * Si une texture de nuit est fournie, elle est utilisée côté ombre (lumières des villes).
     *
     * @param texture      Bitmap équirectangulaire de jour
     * @param nightTexture Bitmap équirectangulaire de nuit (lumières des villes), ou null
     * @param snap         Snapshot astronomique
     * @param right        Vecteur "droite" de la caméra (coordonnées écliptiques)
     * @param up           Vecteur "haut" de la caméra
     * @param forward      Vecteur "avant" de la caméra (caméra → Terre)
     * @param radiusPx     Rayon de la sphère en pixels
     */
    fun renderEarth(
        texture: Bitmap,
        nightTexture: Bitmap?,
        snap: AstronomyCalculator.AstroSnapshot,
        right: AstronomyCalculator.Vec3,
        up: AstronomyCalculator.Vec3,
        forward: AstronomyCalculator.Vec3,
        radiusPx: Int,
        showTerminator: Boolean = true
    ): Bitmap {
        val size = radiusPx * 2
        val bmpW = texture.width
        val bmpH = texture.height

        val dayPixels = IntArray(bmpW * bmpH)
        texture.getPixels(dayPixels, 0, bmpW, 0, 0, bmpW, bmpH)

        // Texture de nuit (peut avoir une résolution différente)
        val nightPixels: IntArray?
        val nW: Int; val nH: Int
        if (nightTexture != null) {
            nightPixels = IntArray(nightTexture.width * nightTexture.height)
            nightTexture.getPixels(nightPixels, 0, nightTexture.width, 0, 0, nightTexture.width, nightTexture.height)
            nW = nightTexture.width; nH = nightTexture.height
        } else {
            nightPixels = null; nW = bmpW; nH = bmpH
        }

        val outPixels = IntArray(size * size) { Color.TRANSPARENT }

        val eps = snap.obliquityRad
        val cosEps = cos(eps); val sinEps = sin(eps)
        val gmst = snap.gmstRad
        val cosGmst = cos(gmst); val sinGmst = sin(gmst)
        val r = radiusPx.toDouble()

        for (py in 0 until size) {
            for (px in 0 until size) {
                val u = (px - r) / r
                val v = -(py - r) / r
                val r2 = u * u + v * v
                if (r2 > 1.0) continue

                val w = sqrt(1.0 - r2)

                // Normale de surface en coordonnées écliptiques
                val nx_ecl = right.x * u + up.x * v - forward.x * w
                val ny_ecl = right.y * u + up.y * v - forward.y * w
                val nz_ecl = right.z * u + up.z * v - forward.z * w

                // Écliptique → équatorial
                val nx_eq = nx_ecl
                val ny_eq = ny_ecl * cosEps - nz_ecl * sinEps
                val nz_eq = ny_ecl * sinEps + nz_ecl * cosEps

                // Équatorial → corps terrestre
                val nx_body = nx_eq * cosGmst + ny_eq * sinGmst
                val ny_body = -nx_eq * sinGmst + ny_eq * cosGmst
                val nz_body = nz_eq

                val lat = asin(nz_body.coerceIn(-1.0, 1.0))
                val lon = atan2(ny_body, nx_body)

                val tu = (((lon / PI + 1.0) / 2.0) * bmpW).toInt().coerceIn(0, bmpW - 1)
                val tv = (((PI / 2.0 - lat) / PI) * bmpH).toInt().coerceIn(0, bmpH - 1)

                // dotSun : >0 = côté jour, <0 = côté nuit
                val dotSun = (nx_ecl * (-snap.sunDir.x) +
                              ny_ecl * (-snap.sunDir.y) +
                              nz_ecl * (-snap.sunDir.z)).coerceIn(-1.0, 1.0)

                // Zone du terminateur : blend progressif sur ~15° de part et d'autre
                val terminator = 0.26  // sin(15°) ≈ 0.26
                val dayWeight = ((dotSun + terminator) / (2.0 * terminator)).coerceIn(0.0, 1.0)

                var color: Int

                if (dayWeight >= 1.0) {
                    // Plein jour
                    var c = dayPixels[tv * bmpW + tu]
                    val sunFactor = 0.4 + dotSun.pow(0.6) * 0.6
                    c = darkenByFactor(c, (sunFactor * w.pow(0.12)).toFloat())
                    color = c
                } else if (dayWeight <= 0.0) {
                    // Pleine nuit
                    color = if (nightPixels != null) {
                        val ntu = (((lon / PI + 1.0) / 2.0) * nW).toInt().coerceIn(0, nW - 1)
                        val ntv = (((PI / 2.0 - lat) / PI) * nH).toInt().coerceIn(0, nH - 1)
                        val nc = nightPixels[ntv * nW + ntu]
                        darkenByFactor(nc, (1.1f * w.pow(0.12).toFloat()))
                    } else {
                        darkenByFactor(dayPixels[tv * bmpW + tu], 0.06f)
                    }
                } else {
                    // Zone terminateur : blend jour ↔ nuit
                    val dayColor = dayPixels[tv * bmpW + tu]
                    val sunFactor = 0.4 + dotSun.coerceAtLeast(0.0).pow(0.6) * 0.6
                    val dayC = darkenByFactor(dayColor, sunFactor.toFloat())

                    val nightC = if (nightPixels != null) {
                        val ntu = (((lon / PI + 1.0) / 2.0) * nW).toInt().coerceIn(0, nW - 1)
                        val ntv = (((PI / 2.0 - lat) / PI) * nH).toInt().coerceIn(0, nH - 1)
                        nightPixels[ntv * nW + ntu]
                    } else {
                        darkenByFactor(dayColor, 0.06f)
                    }

                    color = blendColors(dayC, nightC, dayWeight, w.pow(0.12).toFloat())
                }

                // Lueur atmosphérique au terminateur (option)
                if (showTerminator) {
                    val glowStrength = (1.0 - (dotSun / 0.055).pow(2)).coerceIn(0.0, 1.0) * 0.26
                    if (glowStrength > 0.0) {
                        val r = (Color.red(color)   + (178 - Color.red(color))   * glowStrength).toInt().coerceIn(0, 255)
                        val g = (Color.green(color) + (205 - Color.green(color)) * glowStrength).toInt().coerceIn(0, 255)
                        val b = (Color.blue(color)  + (235 - Color.blue(color))  * glowStrength).toInt().coerceIn(0, 255)
                        color = Color.argb(255, r, g, b)
                    }
                }

                outPixels[py * size + px] = color
            }
        }

        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, size, 0, 0, size, size)
        return result
    }

    private fun blendColors(day: Int, night: Int, dayW: Double, limbFactor: Float): Int {
        val nw = (1.0 - dayW)
        val r = ((Color.red(day)   * dayW + Color.red(night)   * nw) * limbFactor).toInt().coerceIn(0, 255)
        val g = ((Color.green(day) * dayW + Color.green(night) * nw) * limbFactor).toInt().coerceIn(0, 255)
        val b = ((Color.blue(day)  * dayW + Color.blue(night)  * nw) * limbFactor).toInt().coerceIn(0, 255)
        return Color.argb(255, r, g, b)
    }

    /**
     * Rend la Lune comme une sphère texturée.
     * La Lune est en rotation synchrone : la même face est toujours dirigée vers la Terre.
     */
    fun renderMoon(
        texture: Bitmap,
        snap: AstronomyCalculator.AstroSnapshot,
        right: AstronomyCalculator.Vec3,
        up: AstronomyCalculator.Vec3,
        forward: AstronomyCalculator.Vec3,
        radiusPx: Int
    ): Bitmap {
        val size = radiusPx * 2
        val bmpW = texture.width
        val bmpH = texture.height

        val texPixels = IntArray(bmpW * bmpH)
        texture.getPixels(texPixels, 0, bmpW, 0, 0, bmpW, bmpH)

        val outPixels = IntArray(size * size) { Color.TRANSPARENT }

        // Rotation synchrone : l'axe X de la Lune pointe vers la Terre
        // moonBodyX = direction Lune→Terre = -moonPos.normalized()
        val moonBodyX = (snap.moonPos * -1.0).normalized()
        // Axe Z de la Lune ≈ pôle nord écliptique
        val eclNorth = AstronomyCalculator.Vec3(0.0, 0.0, 1.0)
        // Gram-Schmidt pour orthogonaliser
        val moonBodyZ = (eclNorth - moonBodyX * eclNorth.dot(moonBodyX)).normalized()
        val moonBodyY = moonBodyZ.cross(moonBodyX).normalized()

        // Direction du Soleil vue depuis la Lune (pour l'éclairage de phase)
        val sunFromMoon = snap.sunDir * -1.0  // sunDir = Soleil→Terre, donc -sunDir = Terre→Soleil direction

        val r = radiusPx.toDouble()

        for (py in 0 until size) {
            for (px in 0 until size) {
                val u = (px - r) / r
                val v = -(py - r) / r
                val r2 = u * u + v * v
                if (r2 > 1.0) continue

                val w = sqrt(1.0 - r2)

                // Normale de surface en coords écliptiques (face visible depuis la caméra)
                val nx_ecl = right.x * u + up.x * v - forward.x * w
                val ny_ecl = right.y * u + up.y * v - forward.y * w
                val nz_ecl = right.z * u + up.z * v - forward.z * w
                val n = AstronomyCalculator.Vec3(nx_ecl, ny_ecl, nz_ecl)

                // Convertir N dans le repère corps de la Lune
                val lx = n.dot(moonBodyX)
                val ly = n.dot(moonBodyY)
                val lz = n.dot(moonBodyZ)

                // Coordonnées sphériques dans le repère Lune
                val lat = asin(lz.coerceIn(-1.0, 1.0))
                val lon = atan2(ly, lx)

                val tu = (((lon / PI + 1.0) / 2.0) * bmpW).toInt().coerceIn(0, bmpW - 1)
                val tv = (((PI / 2.0 - lat) / PI) * bmpH).toInt().coerceIn(0, bmpH - 1)

                var color = texPixels[tv * bmpW + tu]

                // Éclairage : est-ce que ce point de la Lune reçoit la lumière du Soleil ?
                // sunFromMoon est la direction Lune→Soleil en coords écliptiques
                val dotSun = n.dot(sunFromMoon)  // > 0 = côté éclairé
                val lightFactor = when {
                    dotSun <= 0.0 -> 0.05  // nuit profonde
                    else          -> dotSun.coerceIn(0.0, 1.0).pow(0.7)
                }
                color = darkenByFactor(color, lightFactor.toFloat())

                // Assombrissement de limbe
                val limbFactor = w.pow(0.15)
                color = darkenByFactor(color, limbFactor.toFloat())

                outPixels[py * size + px] = color
            }
        }

        val result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        result.setPixels(outPixels, 0, size, 0, 0, size, size)
        return result
    }

    private fun darkenByFactor(color: Int, factor: Float): Int {
        val r = (Color.red(color)   * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color)  * factor).toInt().coerceIn(0, 255)
        return Color.argb(Color.alpha(color), r, g, b)
    }
}
