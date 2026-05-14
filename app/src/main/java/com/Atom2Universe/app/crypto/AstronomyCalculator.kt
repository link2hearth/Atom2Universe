package com.Atom2Universe.app.crypto

import kotlin.math.*

/**
 * Calculs astronomiques (Jean Meeus, "Astronomical Algorithms").
 * Précision ~0.3° — suffisante pour un rendu visuel.
 */
object AstronomyCalculator {

    data class Vec3(val x: Double, val y: Double, val z: Double) {
        fun dot(other: Vec3) = x * other.x + y * other.y + z * other.z
        fun length() = sqrt(x * x + y * y + z * z)
        fun normalized(): Vec3 {
            val l = length().coerceAtLeast(1e-10)
            return Vec3(x / l, y / l, z / l)
        }
        operator fun times(s: Double) = Vec3(x * s, y * s, z * s)
        operator fun minus(other: Vec3) = Vec3(x - other.x, y - other.y, z - other.z)
        operator fun plus(other: Vec3) = Vec3(x + other.x, y + other.y, z + other.z)
        fun cross(other: Vec3) = Vec3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }

    data class AstroSnapshot(
        /** Direction Soleil→Terre normalisée. */
        val sunDir: Vec3,
        /** Position 3D de la Lune par rapport à la Terre (en km), coordonnées écliptiques. */
        val moonPos: Vec3,
        /** Temps sidéral moyen de Greenwich en radians (pour la rotation terrestre). */
        val gmstRad: Double,
        /** Obliquité de l'écliptique (inclinaison axe Terre) en radians ≈ 23.439°. */
        val obliquityRad: Double,
        /** Longitude du nœud ascendant de la Lune en radians (pour le plan orbital). */
        val moonAscendingNodeRad: Double,
        /** Inclinaison de l'orbite lunaire sur l'écliptique en radians ≈ 5.145°. */
        val moonInclinationRad: Double,
        /** Phase lunaire : angle D (élongation Soleil-Terre-Lune). 0=nouvelle, π=pleine. */
        val moonPhaseRad: Double
    )

    fun compute(utcMillis: Long): AstroSnapshot {
        val jd = utcMillis / 86400000.0 + 2440587.5
        val T = (jd - 2451545.0) / 36525.0

        // === SOLEIL ===
        val L0 = rad(280.46646 + 36000.76983 * T)
        val M  = rad(357.52911 + 35999.05029 * T - 0.0001537 * T * T)
        val C  = rad(
            (1.914602 - 0.004817 * T - 0.000014 * T * T) * sin(M) +
            (0.019993 - 0.000101 * T) * sin(2 * M) +
            0.000289 * sin(3 * M)
        )
        val sunLon = L0 + C  // longitude écliptique du Soleil
        // Direction Soleil→Terre (dans plan écliptique)
        val sunDir = Vec3(-cos(sunLon), -sin(sunLon), 0.0).normalized()

        // === LUNE ===
        val Lm = rad(218.3165 + 481267.8813 * T)   // longitude moyenne
        val Mm = rad(134.9634 + 477198.8676 * T)   // anomalie moyenne
        val F  = rad(93.2721  + 483202.0175 * T)   // argument de latitude
        val D  = rad(297.8502 + 445267.1115 * T)   // élongation
        val Om = rad(125.0445 - 1934.1363  * T)    // nœud ascendant (Ω)

        val moonLon = Lm +
            rad(6.2886 * sin(Mm) +
            1.2740 * sin(2 * D - Mm) +
            0.6583 * sin(2 * D) +
            0.2136 * sin(2 * Mm) -
            0.1851 * sin(D) -
            0.1143 * sin(2 * F) +
            0.0588 * sin(2 * D - 2 * Mm) +
            0.0572 * sin(2 * D - Mm + F) +
            0.0533 * sin(2 * D + Mm))

        val moonLat = rad(5.1282 * sin(F) +
            0.2806 * sin(Mm + F) +
            0.2777 * sin(Mm - F) +
            0.1732 * sin(2 * D - F) +
            0.0554 * sin(2 * D - Mm - F))

        val moonDist = 385001.0 -
            20905.0 * cos(Mm) -
            3699.0  * cos(2 * D - Mm) -
            2956.0  * cos(2 * D) -
            570.0   * cos(2 * Mm) +
            246.0   * cos(2 * Mm - 2 * D)

        val cosMoonLat = cos(moonLat)
        val moonPos = Vec3(
            moonDist * cosMoonLat * cos(moonLon),
            moonDist * cosMoonLat * sin(moonLon),
            moonDist * sin(moonLat)
        )

        // === GMST ===
        // Formule propre : GMST° = 280.46061837 + 360.98564736629 × (JD - J2000)
        val gmstDeg = 280.46061837 + 360.98564736629 * (jd - 2451545.0)
        val gmstRad = rad(gmstDeg)

        // === OBLIQUITÉ ===
        val obliquityDeg = 23.439291 - 0.013004 * T
        val obliquityRad = Math.toRadians(obliquityDeg)

        return AstroSnapshot(
            sunDir             = sunDir,
            moonPos            = moonPos,
            gmstRad            = gmstRad,
            obliquityRad       = obliquityRad,
            moonAscendingNodeRad = Om,
            moonInclinationRad = Math.toRadians(5.1454),
            moonPhaseRad       = D
        )
    }

    /** Convertit degrés → radians en normalisant à [0, 2π). */
    private fun rad(deg: Double) = Math.toRadians(((deg % 360.0) + 360.0) % 360.0)
}
