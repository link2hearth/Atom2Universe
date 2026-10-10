package com.Atom2Universe.app.science.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineExplorationTest {
    @Test fun `une vue moderne garde un contexte moderne`() {
        val window = HumanTimeWindow(1960.0, 20.0)
        val current = TimelineChapters.humanWindow(window)
        assertEquals("human_postwar", current.id)
        assertEquals("human_modern", current.parentId)
        assertFalse(TimelineChapters.children(requireNotNull(TimelineChapters.get(current.parentId)))
            .any { it.id in listOf("universe", "origins", "galaxies") })
    }

    @Test fun `une fenetre traversant deux periodes remonte au contexte commun`() {
        val window = HumanTimeWindow(1930.0, 40.0)
        assertEquals("human_modern", TimelineChapters.humanWindow(window).id)
        window.set(1400.0, 200.0)
        assertEquals("human_recent", TimelineChapters.humanWindow(window).id)
    }

    @Test fun `dezoomer retrouve la periode complete avant le niveau superieur`() {
        val window = HumanTimeWindow(1960.0, 20.0)
        val path = TimelineChapters.canonical("human_postwar")
        assertEquals("human_postwar", TimelineChapters.wider(path, window)?.id)
        window.focus(1945.0, 1991.0)
        assertEquals("human_modern", TimelineChapters.wider(path, window)?.id)
        assertNull(TimelineChapters.wider(listOf("universe")))
    }

    @Test fun `les fleches restent locales quand la fenetre chevauche une limite`() {
        val window = HumanTimeWindow(1930.0, 40.0)
        val chapter = TimelineChapters.humanWindow(window)
        val (previous, next) = TimelineChapters.neighbours(chapter, window)
        assertEquals("human_wars", previous?.id)
        assertEquals("human_connected", next?.id)
    }

    @Test fun `chaque periode humaine peut etre cadree puis restauree`() {
        HumanHistory.periods.forEach { period ->
            val range = requireNotNull(period.human)
            val window = HumanTimeWindow(range.first.year.toDouble(), (range.last.year - range.first.year).toDouble())
            assertEquals(period.id, TimelineChapters.humanWindow(window).id)
            val path = TimelineChapters.navigate(listOf("universe", "earth"), period.id)
            assertEquals(period.id, path.last())
            assertEquals(path, TimelineChapters.restore(path))
        }
    }

    @Test fun `le passage entre periodes conserve un chemin coherent`() {
        var path = TimelineChapters.canonical("human_regional")
        val next = requireNotNull(TimelineChapters.neighbours(requireNotNull(TimelineChapters.get(path.last()))).second)
        assertEquals("human_oceans", next.id)
        path = TimelineChapters.navigate(path, next.id)
        assertEquals("human_modern", path[path.lastIndex - 1])
        assertFalse(path.contains("human_exchanges"))
        assertNotNull(TimelineChapters.restore(path))
    }

    @Test fun `le zoom par geste et le deplacement actualisent le chapitre`() {
        val window = HumanTimeWindow(1800.0, 226.0)
        assertEquals("human_modern", TimelineChapters.humanWindow(window).id)
        window.zoom(8.0, 1.0)
        assertEquals("human_connected", TimelineChapters.humanWindow(window).id)
        window.pan(-50.0)
        assertEquals("human_postwar", TimelineChapters.humanWindow(window).id)
        window.set(HumanTimeWindow.MIN, HumanTimeWindow.MAX - HumanTimeWindow.MIN)
        assertEquals("human", TimelineChapters.humanWindow(window).id)
    }

    @Test fun `la route geologique reste disponible depuis les humains`() {
        val path = TimelineChapters.navigate(TimelineChapters.canonical("quaternary"), "human_modern")
        assertTrue(path.contains("quaternary"))
        assertEquals("human_modern", path.last())
        assertNotNull(TimelineChapters.restore(path))
        val geological = TimelineChapters.navigate(path, "quaternary")
        assertEquals(TimelineChapters.canonical("quaternary"), geological)
    }

    @Test fun `les echelles plus fines relient les histoires sans dupliquer les periodes`() {
        assertEquals(listOf("origins", "galaxies", "solar", "earth"),
            TimelineChapters.zoomTargets(TimelineChapters.universe).map { it.id })
        assertTrue(TimelineChapters.zoomTargets(requireNotNull(TimelineChapters.get("quaternary"))).any { it.id == "human" })
        assertTrue(TimelineChapters.zoomTargets(requireNotNull(TimelineChapters.get("human_wars"))).isEmpty())
    }
}
