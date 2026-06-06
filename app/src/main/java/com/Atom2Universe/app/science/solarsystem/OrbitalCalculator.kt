package com.Atom2Universe.app.science.solarsystem

import kotlin.math.abs
import kotlin.math.pow

object OrbitalCalculator {

    /** Angle orbital en degrés (0..360) pour la planète donnée à elapsedDays jours simulés. */
    fun orbitAngleDeg(planet: PlanetDef, elapsedDays: Double): Float {
        val fraction = (elapsedDays / planet.orbitalPeriodDays).mod(1.0)
        return ((planet.initialAngleDeg + fraction * 360.0) % 360.0).toFloat()
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
        val realistic  = 0.8f
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

    fun getMoonOrbitRadius(moon: MoonDef, earthOrbitRadius: Float, earthRadius: Float, blend: Float): Float {
        val closeR      = earthRadius * 3.2f
        val realisticR  = earthOrbitRadius * (moon.orbitRadiusKm / 149_600_000f)
        return when {
            blend <= 1f -> lerp(closeR, lerp(closeR, realisticR, 0.5f), blend)
            else        -> lerp(lerp(closeR, realisticR, 0.5f), realisticR, blend - 1f)
        }
    }

    fun getMoonRadius(moon: MoonDef, blend: Float): Float {
        val ratio = moon.radiusKm / SolarSystemData.SUN_RADIUS_KM
        val sunClose = getSunRadius(0f)
        val close      = ratio.pow(0.38f) * sunClose * 2.2f
        val realistic  = ratio * getSunRadius(2f)
        return lerp(close, realistic, (blend / 2f).coerceIn(0f, 1f))
    }

    fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
