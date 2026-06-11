package com.Atom2Universe.app.science.solarsystem

import kotlin.math.cos
import kotlin.math.sin

/**
 * Position géocentrique de la Lune par la théorie tronquée de Meeus
 * "Astronomical Algorithms" Ch. 47 (15 termes longitude, 10 latitude, 12 distance).
 *
 * Précision : ~0.3° longitude, ~0.2° latitude, ~500 km distance sur ±100 ans autour de J2000.
 * Suffisant pour une représentation visuelle fidèle.
 */
object LunarCalculator {

    const val MEAN_DISTANCE_KM = 384_400.0

    data class LunarPosition(
        val longitude: Double,        // longitude écliptique géocentrique (°)
        val latitude: Double,         // latitude écliptique géocentrique (°)
        val distanceKm: Double,       // distance geocentrique (km)
        val ascendingNodeDeg: Double  // longitude du noeud ascendant courant (°)
    )

    private fun toRad(d: Double) = Math.toRadians(d)
    private fun Double.mod360() = ((this % 360.0) + 360.0) % 360.0

    fun position(elapsedDays: Double): LunarPosition {
        val T = elapsedDays / 36525.0

        // Arguments fondamentaux (°) — Meeus Table 47.A
        val Lp = (218.3164477 + 481267.88123421 * T).mod360()  // longitude moyenne Lune
        val M  = (357.5291092 +  35999.0502909  * T).mod360()  // anomalie moyenne Soleil
        val Mp = (134.9633964 + 477198.8676313  * T).mod360()  // anomalie moyenne Lune
        val D  = (297.8501921 + 445267.1114034  * T).mod360()  // élongation moyenne Lune
        val F  = ( 93.2720950 + 483202.0175233  * T).mod360()  // argument de latitude
        val Om = (125.0445479 -   1934.1362608  * T).mod360()  // noeud ascendant

        val Dr  = toRad(D);  val Mr  = toRad(M)
        val Mpr = toRad(Mp); val Fr  = toRad(F)

        // ── Longitude (unités : 0.000001°) ───────────────────────────
        var sumL = 0.0
        sumL += 6288774 * sin(Mpr)
        sumL += 1274027 * sin(2*Dr - Mpr)
        sumL +=  658314 * sin(2*Dr)
        sumL +=  213618 * sin(2*Mpr)
        sumL -=  185116 * sin(Mr)
        sumL -=  114332 * sin(2*Fr)
        sumL +=   58793 * sin(2*Dr - 2*Mpr)
        sumL +=   57066 * sin(2*Dr - Mr - Mpr)
        sumL +=   53322 * sin(2*Dr + Mpr)
        sumL +=   45758 * sin(2*Dr - Mr)
        sumL -=   40923 * sin(Mr - Mpr)
        sumL -=   34720 * sin(Dr)
        sumL -=   30383 * sin(Mr + Mpr)
        sumL +=   15327 * sin(2*Dr - 2*Fr)
        sumL +=   10980 * sin(Mpr - 2*Fr)

        // ── Latitude (unités : 0.000001°) ────────────────────────────
        var sumB = 0.0
        sumB += 5128122 * sin(Fr)
        sumB +=  280602 * sin(Mpr + Fr)
        sumB +=  277693 * sin(Mpr - Fr)
        sumB +=  173237 * sin(2*Dr - Fr)
        sumB +=   55413 * sin(2*Dr - Mpr + Fr)
        sumB +=   46272 * sin(2*Dr - Mpr - Fr)
        sumB +=   32573 * sin(2*Dr + Fr)
        sumB +=   17198 * sin(2*Mpr + Fr)
        sumB +=    9266 * sin(2*Dr + Mpr - Fr)
        sumB +=    8822 * sin(2*Mpr - Fr)

        // ── Distance (unités : 0.001 km, base 385 000.56 km) ─────────
        var sumR = 0.0
        sumR -= 20905355 * cos(Mpr)
        sumR -=  3699111 * cos(2*Dr - Mpr)
        sumR -=  2955968 * cos(2*Dr)
        sumR -=   569925 * cos(2*Mpr)
        sumR +=    48888 * cos(Mr)
        sumR +=   246158 * cos(2*Dr - 2*Mpr)
        sumR -=   152138 * cos(2*Dr - Mr - Mpr)
        sumR -=   170733 * cos(2*Dr + Mpr)
        sumR -=   204586 * cos(2*Dr - Mr)
        sumR -=   129620 * cos(Mr - Mpr)
        sumR +=   108743 * cos(Dr)
        sumR +=   104755 * cos(Mr + Mpr)

        return LunarPosition(
            longitude        = (Lp + sumL / 1_000_000.0).mod360(),
            latitude         = sumB / 1_000_000.0,
            distanceKm       = 385_000.56 + sumR / 1_000.0,
            ascendingNodeDeg = Om
        )
    }

    /**
     * Position 3D de la Lune relative à la Terre, en unités de scène.
     * Plan écliptique = XZ, Y = nord écliptique (cohérent avec SolarSystemRenderer).
     */
    fun position3D(pos: LunarPosition, kmPerUnit: Double): FloatArray {
        val r    = pos.distanceKm / kmPerUnit
        val lRad = Math.toRadians(pos.longitude)
        val bRad = Math.toRadians(pos.latitude)
        val cosB = cos(bRad)
        return floatArrayOf(
            (r * cosB * cos(lRad)).toFloat(),
            (r * sin(bRad)).toFloat(),
            (r * cosB * sin(lRad)).toFloat()
        )
    }
}
