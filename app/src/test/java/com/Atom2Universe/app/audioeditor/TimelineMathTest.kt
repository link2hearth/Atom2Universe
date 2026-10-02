package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.ui.TimelineMath
import com.Atom2Universe.app.audioeditor.ui.TrackColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineMathTest {

    private val rate = 44100

    @Test
    fun `les graduations majeures sont des pas ronds d'au moins la largeur demandee`() {
        // 441 trames par pixel = 0,01 s par pixel ; 70 px minimum = 0,7 s -> le pas rond suivant est 1 s.
        assertEquals(44100L, TimelineMath.majorTickFrames(441.0, rate, 70.0))
        // 4 410 trames par pixel : 7 s minimum -> 10 s.
        assertEquals(441_000L, TimelineMath.majorTickFrames(4410.0, rate, 70.0))
        // Très zoomé : 1 trame par pixel, 70 px = 70 trames = 1,6 ms -> 2 ms.
        assertEquals(88L, TimelineMath.majorTickFrames(1.0, rate, 70.0))
    }

    @Test
    fun `le pas ne descend jamais sous la largeur minimale en pixels`() {
        for (fpp in listOf(0.25, 1.0, 7.0, 100.0, 441.0, 5000.0, 90_000.0)) {
            val step = TimelineMath.majorTickFrames(fpp, rate, 76.0)
            assertTrue("fpp=$fpp pas=$step", step / fpp >= 76.0 - 1e-9 || step == Math.round(7200.0 * rate))
        }
    }

    @Test
    fun `un zoom enorme plafonne au plus grand pas`() {
        assertEquals(7200L * rate, TimelineMath.majorTickFrames(1e9, rate, 70.0))
    }

    @Test
    fun `les petits traits se comptent par pas`() {
        assertEquals(5, TimelineMath.minorDivisions(441.0, rate, 70.0))       // 1 s
        assertEquals(4, TimelineMath.minorDivisions(441.0 * 2.5, rate, 70.0)) // 2 s
    }

    @Test
    fun `l'aimant prend le point le plus proche dans le seuil, sinon la trame telle quelle`() {
        val c = longArrayOf(1000, 2000, 3000)
        assertEquals(2000L, TimelineMath.snap(2040, c, 50.0))
        assertEquals(1000L, TimelineMath.snap(1500, c, 600.0))   // 1000 et 2000 : égalité de distance -> le premier
        assertEquals(2060L, TimelineMath.snap(2060, c, 50.0))
        assertEquals(5L, TimelineMath.snap(5, longArrayOf(), 50.0))
    }

    @Test
    fun `un clip deplace se colle par son debut ou par sa fin, le plus proche des deux`() {
        val c = longArrayOf(1000, 5000)
        // Début à 1 030 : se colle à 1 000.
        assertEquals(1000L, TimelineMath.snapSpan(1030, 2000, c, 50.0))
        // Fin à 5 020 (début 3 020, longueur 2 000) : la fin se colle à 5 000 donc le début devient 3 000.
        assertEquals(3000L, TimelineMath.snapSpan(3020, 2000, c, 50.0))
        // Les deux à portée : le plus proche l'emporte (début à 1 010, fin à 5 040 -> début collé).
        assertEquals(1000L, TimelineMath.snapSpan(1010, 4040, c, 50.0))
        // Rien à portée : inchangé.
        assertEquals(2500L, TimelineMath.snapSpan(2500, 1000, c, 50.0))
    }

    @Test
    fun `le zoom d'ajustement fait tenir le projet, avec un minimum de dix secondes`() {
        assertEquals(44100.0 * 20 / 1000, TimelineMath.fitFramesPerPixel(44100L * 20, 1000, rate), 1e-9)
        assertEquals(44100.0 * 10 / 500, TimelineMath.fitFramesPerPixel(0, 500, rate), 1e-9)
    }

    @Test
    fun `le defilement apres un zoom garde la trame sous le doigt`() {
        // Trame 100 000 sous le pixel 300 à 100 trames/pixel ; on passe à 50 trames/pixel.
        val scroll = TimelineMath.scrollForZoom(100_000.0, 300.0, 50.0)
        assertEquals(100_000L - 300 * 50, scroll)
    }

    @Test
    fun `l'horloge decoupe heures, minutes, secondes et centiemes`() {
        val c = TimelineMath.clock((3600L + 125) * rate + rate / 4, rate)
        assertEquals(1L, c.hours); assertEquals(2L, c.minutes); assertEquals(5L, c.seconds); assertEquals(25L, c.centis)
        val z = TimelineMath.clock(-5, rate)
        assertEquals(0L, z.seconds + z.centis + z.minutes + z.hours)
    }

    @Test
    fun `premiere graduation visible`() {
        assertEquals(0L, TimelineMath.firstTickAtOrAfter(-10, 100))
        assertEquals(300L, TimelineMath.firstTickAtOrAfter(201, 100))
        assertEquals(200L, TimelineMath.firstTickAtOrAfter(200, 100))
        assertEquals(200L, TimelineMath.floorTick(299, 100))
    }

    @Test
    fun `les couleurs de piste suivent la palette et respectent un choix explicite`() {
        assertEquals(TrackColors.PALETTE[0], TrackColors.colorFor(0, 0))
        assertEquals(TrackColors.PALETTE[1], TrackColors.colorFor(0, TrackColors.PALETTE.size + 1))
        assertEquals(0xFF123456.toInt(), TrackColors.colorFor(0xFF123456.toInt(), 3))
    }
}
