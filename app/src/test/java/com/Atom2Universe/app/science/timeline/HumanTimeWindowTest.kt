package com.Atom2Universe.app.science.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HumanTimeWindowTest {
    @Test fun `la vue initiale ignore les deux reperes prehistoriques`() {
        val window = HumanTimeWindow()
        assertEquals(HumanHistory.bce(4000).year.toDouble(), window.start, .0001)
        assertEquals(HumanTimeWindow.MAX, window.end, .0001)
        assertFalse(window.intersects(HumanHistory.ago(60_000).year, HumanHistory.ago(60_000).year))
        assertFalse(window.intersects(HumanHistory.ago(300_000).year, HumanHistory.ago(300_000).year))
        assertEquals(TimelineChapters.recentHistory.id, TimelineChapters.humanWindow(window).id)
    }

    @Test fun `un seul geste cadre cent ans et les fleches gardent cette duree`() {
        val window = HumanTimeWindow()
        window.century()
        assertEquals(HumanTimeWindow.MAX - 100, window.start, .0001)
        assertEquals(100.0, window.span, .0001)
        window.pan(-window.span)
        assertEquals(HumanTimeWindow.MAX - 200, window.start, .0001)
        assertEquals(100.0, window.span, .0001)
        window.pan(window.span)
        assertEquals(HumanTimeWindow.MAX, window.end, .0001)
    }

    @Test fun `les siecles les plus anciens gardent leur chapitre historique`() {
        val window = HumanTimeWindow(-3950.0, 100.0)
        assertTrue(window.isRecent)
        assertEquals("human_villages", TimelineChapters.humanWindow(window).id)
        window.recentOverview()
        assertEquals(TimelineChapters.recentHistory.id, TimelineChapters.humanWindow(window).id)
    }

    @Test fun `le curseur rejoint toute la plage sans modifier le zoom`() {
        val window = HumanTimeWindow()
        window.century()
        window.seekRecent(0.0)
        assertEquals(HumanTimeWindow.RECENT_MIN, window.start, .0001)
        window.seekRecent(.5)
        assertEquals(HumanTimeWindow.RECENT_MIN + (HumanTimeWindow.RECENT_SPAN - 100) / 2, window.start, .0001)
        window.seekRecent(1.0)
        assertEquals(HumanTimeWindow.MAX, window.end, .0001)
        assertEquals(100.0, window.span, .0001)
    }

    @Test fun `glisser et pincer ne ramene pas les reperes anciens dans la plage recente`() {
        val window = HumanTimeWindow()
        window.century()
        window.pan(-1e6)
        assertEquals(HumanTimeWindow.RECENT_MIN, window.start, .0001)
        window.zoom(.0001)
        assertEquals(HumanTimeWindow.RECENT_SPAN, window.span, .0001)
        assertTrue(window.isRecent)
    }

    @Test fun `une recherche prehistorique reste possible puis retour direct aux siecles`() {
        val window = HumanTimeWindow()
        window.focus(-65_000.0, -50_000.0)
        assertFalse(window.isRecent)
        assertTrue(window.intersects(-60_000, -60_000))
        window.pan(-1000.0)
        assertEquals(-66_000.0, window.start, .0001)
        window.century()
        assertTrue(window.isRecent)
        assertEquals(HumanTimeWindow.MAX, window.end, .0001)
        assertEquals(100.0, window.span, .0001)
    }

    @Test fun `le passage avant et apres notre ere garde une fenetre de cent ans`() {
        val window = HumanTimeWindow(-50.0, 100.0)
        window.pan(100.0)
        assertEquals(50.0, window.start, .0001)
        assertEquals(150.0, window.end, .0001)
        window.pan(-100.0)
        assertEquals(-50.0, window.start, .0001)
        assertEquals(100.0, window.span, .0001)
    }
}
