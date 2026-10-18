package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le geste « Dessiner l'éclair » : la longueur dessinée remplit la jauge, on lève le doigt sur
 * la cible quand la jauge est dans la zone. Mêmes fenêtres que la barre de frappe (0,72 ± 0,045
 * pour Parfait, ± 0,13 pour Bien).
 */
class DrawGestureTest {
    // Le héros en (0, 0), la cible en (300, 0) ; 1 000 px de tracé remplissent la jauge.
    private fun gesture() = DrawGesture(
        startX = 0f, startY = 0f,
        targetX = 300f, targetY = 0f, targetRadius = 60f,
        fullLength = 1000f, center = .72f, good = .13f, perfect = .045f,
        limitMs = 4000f, step = 5f,
    )

    /**
     * Un zigzag du héros vers la cible, par petits pas de 4 px, jusqu'à [length] de tracé, en
     * finissant sur la cible : il monte et descend de 100 px, puis file droit dessus.
     */
    private fun DrawGesture.drawTo(length: Float, endX: Float = 300f, endY: Float = 0f) {
        down(0, 0f, 0f, 0f)
        var x = 0f; var y = 0f; var dir = 1f
        // D'abord le zigzag sur place, en gardant de quoi rejoindre l'arrivée en ligne droite
        while (drawn + 4f + kotlin.math.hypot(endX - x, endY - y) < length && result == null) {
            y += 4f * dir
            if (kotlin.math.abs(y) >= 100f) dir = -dir
            move(0, x, y, 0f)
        }
        // Puis tout droit jusqu'à l'arrivée
        val steps = (kotlin.math.hypot(endX - x, endY - y) / 4f).toInt().coerceAtLeast(1)
        val sx = x; val sy = y
        for (i in 1..steps) if (result == null) move(0, sx + (endX - sx) * i / steps, sy + (endY - sy) * i / steps, 0f)
    }

    @Test
    fun leverSurLaCibleDansLaZoneDonneLaNote() {
        val perfect = gesture().apply { drawTo(720f); up(0, fingerX, fingerY, 0f) }
        assertEquals(.72f, perfect.gauge, .01f)
        assertEquals(Timing.PERFECT, perfect.result)
        val good = gesture().apply { drawTo(630f); up(0, fingerX, fingerY, 0f) }
        assertEquals(Timing.GOOD, good.result)
        val short = gesture().apply { drawTo(450f); up(0, fingerX, fingerY, 0f) }
        assertEquals(Timing.MISS, short.result)
    }

    @Test
    fun leverAilleursQueSurLaCibleEstRate() {
        val g = gesture().apply { drawTo(720f, endX = 150f, endY = 200f); up(0, fingerX, fingerY, 0f) }
        assertFalse(g.onTarget)
        assertEquals(Timing.MISS, g.result)
    }

    @Test
    fun laJaugePleineFaitSurcharge() {
        val g = gesture()
        g.down(0, 0f, 0f, 0f)
        var y = 0f
        while (g.result == null && y < 2000f) { y += 4f; g.move(0, 0f, y, 0f) }
        assertEquals(Timing.MISS, g.result)
        assertTrue(g.gauge >= 1f)
    }

    @Test
    fun leDoigtSePoseNImporteOuEtLEclairPartDuHeros() {
        val g = gesture()
        g.down(0, 200f, 150f, 0f)
        assertTrue(g.started)
        // L'éclair part du héros ; le saut jusqu'au doigt ne remplit pas la jauge
        assertEquals(0f, g.traced[0], 0f); assertEquals(0f, g.traced[1], 0f)
        assertEquals(0f, g.drawn, 0f)
        g.move(0, 250f, 150f, 0f)
        assertEquals(50f, g.drawn, .01f)
        // Un second doigt ne compte pas
        g.down(1, 0f, 0f, 0f)
        g.move(1, 100f, 0f, 0f)
        assertEquals(50f, g.drawn, .01f)
    }

    @Test
    fun leTempsEstCompte() {
        val idle = gesture()
        idle.update(3990f)
        assertNull(idle.result)
        idle.update(4000f)
        assertEquals(Timing.MISS, idle.result)
        val slow = gesture().apply { down(0, 0f, 0f, 0f); move(0, 100f, 0f, 0f); update(4000f) }
        assertEquals(Timing.MISS, slow.result)
    }

    @Test
    fun laVitesseNeCompteQueLaLongueur() {
        // Le même dessin, que le doigt aille vite (gros pas) ou lentement (petits pas)
        val fast = gesture().apply { down(0, 0f, 0f, 0f); move(0, 150f, 200f, 0f); move(0, 300f, 0f, 0f) }
        val slow = gesture().apply {
            down(0, 0f, 0f, 0f)
            for (i in 1..50) move(0, 150f * i / 50, 200f * i / 50, 0f)
            for (i in 1..50) move(0, 150f + 150f * i / 50, 200f - 200f * i / 50, 0f)
        }
        assertEquals(fast.drawn, slow.drawn, .5f)
    }

    @Test
    fun leDessinVaDuHerosALaCible() {
        val g = gesture().apply { down(0, 8f, -6f, 0f); drawTo(720f); up(0, fingerX, fingerY, 0f) }
        assertTrue("trop peu de points : ${g.tracedCount}", g.tracedCount > 50)
        assertEquals(0f, g.traced[0], 0f); assertEquals(0f, g.traced[1], 0f)
        assertEquals(300f, g.traced[g.tracedCount * 2 - 2], 0f)
        assertEquals(0f, g.traced[g.tracedCount * 2 - 1], 0f)
        // Raté, l'éclair va quand même jusqu'à la cible : les dégâts tombent
        val missed = gesture().apply { drawTo(720f, endX = 150f, endY = 200f); up(0, fingerX, fingerY, 0f) }
        assertEquals(300f, missed.traced[missed.tracedCount * 2 - 2], 0f)
    }
}
