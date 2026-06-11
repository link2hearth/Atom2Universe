package com.Atom2Universe.app.science.solarsystem

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

object OrbitalCalculator {

    /**
     * Longitude écliptique héliocentrique (°) à elapsedDays jours depuis J2000.
     *
     * Modèle képlérien à l'ordre 2 :
     *  1. Longitude moyenne L = L0 + L1·T  (T en siècles juliens)
     *  2. Anomalie moyenne   M = L − ω̃
     *  3. Équation du centre  C = 2e·sin M + (5/4)e²·sin 2M
     *  4. Longitude vraie    = L + C
     *
     * Erreur résiduelle : < 1° pour les planètes intérieures sur ±200 ans,
     * < 0.5° pour les géantes gazeuses.
     */
    fun orbitAngleDeg(planet: PlanetDef, elapsedDays: Double): Float {
        val T = elapsedDays / 36525.0          // siècles juliens depuis J2000
        val L = (planet.initialAngleDeg + planet.meanMotionDegCentury * T).mod(360.0)
        val M = Math.toRadians(((L - planet.perihelionLongDeg) + 720.0).mod(360.0))
        val e = planet.eccentricity.toDouble()
        val center = Math.toDegrees(2.0 * e * sin(M) + 1.25 * e * e * sin(2.0 * M))
        return ((L + center + 360.0).mod(360.0)).toFloat()
    }

    /**
     * Position 3D héliocentrique écliptique (X, Y, Z) à l'échelle de scène donnée.
     *
     * Formule standard avec nœud ascendant Ω et inclinaison i :
     *   u = λ − Ω  (argument de latitude approché)
     *   X = r·(cos Ω·cos u − sin Ω·sin u·cos i)
     *   Y = r·sin u·sin i
     *   Z = r·(sin Ω·cos u + cos Ω·sin u·cos i)
     *
     * Le plan écliptique correspond à Y = 0 (Terre à Y ≈ 0 par définition).
     */
    fun orbitPosition3D(planet: PlanetDef, orbitR: Float, elapsedDays: Double): FloatArray {
        val lambda = Math.toRadians(orbitAngleDeg(planet, elapsedDays).toDouble())
        val omega  = Math.toRadians(planet.ascendingNodeDeg.toDouble())
        val incl   = Math.toRadians(planet.orbitalInclinationDeg.toDouble())
        val u = lambda - omega
        return floatArrayOf(
            (orbitR * (cos(omega)*cos(u) - sin(omega)*sin(u)*cos(incl))).toFloat(),
            (orbitR * sin(u)*sin(incl)).toFloat(),
            (orbitR * (sin(omega)*cos(u) + cos(omega)*sin(u)*cos(incl))).toFloat()
        )
    }

    /** Angle de rotation propre en degrés à elapsedDays. Sens de rotation géré par le signe. */
    fun selfRotationDeg(planet: PlanetDef, elapsedDays: Double): Float {
        val rotations = elapsedDays / abs(planet.rotationPeriodDays.toDouble())
        val sign = if (planet.rotationPeriodDays < 0) -1f else 1f
        return (sign * rotations * 360.0 % 360.0).toFloat()
    }

    /** Angle orbital de la Lune autour de sa planète hôte. */
    fun moonAngleDeg(moon: MoonDef, elapsedDays: Double): Float {
        val fraction = (elapsedDays / moon.orbitalPeriodDays).mod(1.0)
        return (fraction * 360.0 % 360.0).toFloat()
    }

    // ────────────────────────────────────────────────────────────────
    // Calcul des rayons d'affichage selon le blend de proportion (0..2)
    //   0.0 → CLOSE (rapproché, planètes très agrandies)
    //   1.0 → COMPRESSED (logarithmique)
    //   2.0 → REALISTIC (vraies proportions)
    // ────────────────────────────────────────────────────────────────

    fun getOrbitRadius(planet: PlanetDef, blend: Float): Float {
        val au = planet.orbitRadiusAU
        val close      = au.pow(0.42f) * 13f
        val compressed = au.pow(0.58f) * 9f
        val realistic  = au * 6f
        return when {
            blend <= 1f -> lerp(close, compressed, blend)
            else        -> lerp(compressed, realistic, blend - 1f)
        }
    }

    fun getSunRadius(blend: Float): Float {
        val close      = 1.2f
        val compressed = 1.5f
        // ~5× l'échelle vraie (0.00465 × 6 × 5 ≈ 0.14) — proportions inter-corps exactes,
        // taille gérable pour le depth buffer et le zoom
        val realistic  = 0.14f
        return when {
            blend <= 1f -> lerp(close, compressed, blend)
            else        -> lerp(compressed, realistic, blend - 1f)
        }
    }

    fun getPlanetRadius(planet: PlanetDef, blend: Float): Float {
        val ratio = planet.radiusKm / SolarSystemData.SUN_RADIUS_KM
        val sunClose = getSunRadius(0f)
        val close      = ratio.pow(0.38f) * sunClose * 2.2f
        val compressed = ratio.pow(0.52f) * sunClose * 1.5f
        val realistic  = ratio * getSunRadius(2f)
        return when {
            blend <= 1f -> lerp(close, compressed, blend)
            else        -> lerp(compressed, realistic, blend - 1f)
        }
    }

    /** Rayon de la Lune : toujours proportionnel au rayon de la Terre dans le blend courant.
     *  Ratio réel Moon/Earth = 1737/6371 ≈ 0.273 — conservé dans tous les modes. */
    fun getMoonRadius(moon: MoonDef, earthRadius: Float): Float =
        earthRadius * (moon.radiusKm / 6371f)

    fun getMoonOrbitRadius(moon: MoonDef, earthOrbitRadius: Float, earthRadius: Float, blend: Float): Float {
        val moonR = getMoonRadius(moon, earthRadius)
        // Orbite "close" : 1.75× (Terre + Lune) pour une séparation visuelle claire
        val closeR     = (earthRadius + moonR) * 1.75f
        val realisticR = earthOrbitRadius * (moon.orbitRadiusKm / 149_600_000f)
        // Plancher physique : jamais moins que Terre + Lune
        val minR = earthRadius + moonR * 1.2f
        return when {
            blend <= 1f -> lerp(closeR, lerp(closeR, realisticR, 0.5f), blend).coerceAtLeast(minR)
            else        -> lerp(lerp(closeR, realisticR, 0.5f), realisticR, blend - 1f).coerceAtLeast(minR)
        }
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
