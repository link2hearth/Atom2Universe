package com.Atom2Universe.app.science.solarsystem

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OrbitalGeometryTest {

    /** Vu du nord écliptique (+Y de la scène), les planètes doivent tourner dans le sens direct. */
    @Test
    fun planetsOrbitCounterclockwiseSeenFromNorth() {
        for (planet in SolarSystemData.planets) {
            val a = OrbitalCalculator.orbitPosition3D(planet, 10f, 0.0)
            val b = OrbitalCalculator.orbitPosition3D(planet, 10f, planet.orbitalPeriodDays / 50.0)
            // Composante Y de a × b (moment cinétique) : positive = antihoraire vu de +Y.
            val angularMomentumY = a[2] * b[0] - a[0] * b[2]
            assertTrue("${planet.id} tourne à l'envers", angularMomentumY > 0f)
        }
    }

    @Test
    fun eclipticNorthIsSceneUpAndLongitude90IsMinusZ() {
        val north = OrbitalCalculator.eclipticToScene(0.0, 0.0, 1.0)
        assertEquals(1f, north[1], 1e-6f)
        val lon90 = OrbitalCalculator.eclipticToScene(0.0, 1.0, 0.0)
        assertEquals(-1f, lon90[2], 1e-6f)
    }

    /** Meeus, exemple 12.a : 1987-04-10 0 h UT, TSMG = 13 h 10 min 46,3668 s = 197,693195°. */
    @Test
    fun greenwichSiderealTimeMatchesMeeus() {
        val days = 2446895.5 - 2451545.0
        assertEquals(197.693195, OrbitalCalculator.greenwichSiderealDeg(days), 1e-3)
    }

    /** Meeus, exemple 47.a : 1992-04-12 0 h TD, λ = 133,162655°, Δ = 368 409,7 km (série complète). */
    @Test
    fun moonPositionMatchesMeeusWithinTruncationError() {
        val pos = LunarCalculator.position(2448724.5 - 2451545.0)
        assertEquals(133.162655, pos.longitude, 0.3)
        assertEquals(368_409.7, pos.distanceKm, 500.0)
    }

    @Test
    fun simDateFloorsNegativeDays() {
        val j2000 = LocalDate.of(2000, 1, 1)
        assertEquals(LocalDate.of(1999, 12, 31), SolarSystemActivity.simDate(j2000, -0.5))
        assertEquals(LocalDate.of(2000, 1, 1), SolarSystemActivity.simDate(j2000, 0.5))
    }
}
