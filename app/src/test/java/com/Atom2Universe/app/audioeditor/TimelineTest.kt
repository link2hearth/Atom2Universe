package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.EditSession
import com.Atom2Universe.app.audioeditor.core.Fade
import com.Atom2Universe.app.audioeditor.core.FadeShape
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addMarker
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.core.copyRange
import com.Atom2Universe.app.audioeditor.core.deleteClip
import com.Atom2Universe.app.audioeditor.core.deleteRange
import com.Atom2Universe.app.audioeditor.core.duplicateClip
import com.Atom2Universe.app.audioeditor.core.insertSilence
import com.Atom2Universe.app.audioeditor.core.moveClip
import com.Atom2Universe.app.audioeditor.core.pasteAt
import com.Atom2Universe.app.audioeditor.core.repeatRange
import com.Atom2Universe.app.audioeditor.core.replaceRange
import com.Atom2Universe.app.audioeditor.core.reverseRange
import com.Atom2Universe.app.audioeditor.core.setClipFades
import com.Atom2Universe.app.audioeditor.core.silenceRange
import com.Atom2Universe.app.audioeditor.core.splitAt
import com.Atom2Universe.app.audioeditor.core.trimClipLeft
import com.Atom2Universe.app.audioeditor.core.trimClipRight
import com.Atom2Universe.app.audioeditor.core.trimToRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimelineTest {

    /** Un projet à une piste portant un clip de 1000 trames de la source « a » (qui en compte 1000). */
    private fun base(): Triple<Project, Int, Int> {
        var p = Project().addSource(Source("a", "sources/a.wav", 1, 1000, 44100))
        val (p1, t) = p.addTrack("Piste 1")
        val (p2, c) = p1.addClip(t, "a", 0)
        p = p2
        return Triple(p, t, c!!)
    }

    private fun Project.clipsOf(trackId: Int) = track(trackId)!!.clips

    @Test
    fun `un clip posé couvre toute la source`() {
        val (p, t, _) = base()
        val c = p.clipsOf(t).single()
        assertEquals(0L, c.start); assertEquals(1000L, c.length); assertEquals(1000L, p.length)
    }

    @Test
    fun `couper en deux garde les deux moities contigues dans la source`() {
        val (p, t, _) = base()
        val q = p.splitAt(listOf(t), 400)
        val (a, b) = q.clipsOf(t)
        assertEquals(0L, a.start); assertEquals(400L, a.length); assertEquals(0L, a.srcStart)
        assertEquals(400L, b.start); assertEquals(600L, b.length); assertEquals(400L, b.srcStart)
        assertNotEquals(a.id, b.id)
    }

    @Test
    fun `couper aux bords ne fait rien`() {
        val (p, t, _) = base()
        assertEquals(p, p.splitAt(listOf(t), 0))
        assertEquals(p, p.splitAt(listOf(t), 1000))
    }

    @Test
    fun `supprimer le milieu referme le trou`() {
        val (p, t, _) = base()
        val q = p.deleteRange(listOf(t), 300, 500)
        val (a, b) = q.clipsOf(t)
        assertEquals(0L to 300L, a.start to a.length)
        assertEquals(300L, b.start); assertEquals(500L, b.length); assertEquals(500L, b.srcStart)
        assertEquals(800L, q.length)
    }

    @Test
    fun `silencer le milieu garde la duree`() {
        val (p, t, _) = base()
        val q = p.silenceRange(listOf(t), 300, 500)
        val (a, b) = q.clipsOf(t)
        assertEquals(300L, a.end); assertEquals(500L, b.start)
        assertEquals(1000L, q.length)
    }

    @Test
    fun `supprimer tout un clip le retire`() {
        val (p, t, _) = base()
        assertTrue(p.deleteRange(listOf(t), 0, 1000).clipsOf(t).isEmpty())
        assertTrue(p.deleteRange(listOf(t), -50, 2000).clipsOf(t).isEmpty())
    }

    @Test
    fun `une piste verrouillee n'est pas modifiee`() {
        val (p, t, _) = base()
        val locked = p.mapTrack(t) { it.copy(locked = true) }
        assertEquals(locked, locked.deleteRange(listOf(t), 0, 500))
        assertEquals(locked, locked.splitAt(listOf(t), 500))
    }

    @Test
    fun `copier puis coller insere et pousse la suite`() {
        val (p, t, _) = base()
        val cb = p.copyRange(listOf(t), 100, 300)
        assertEquals(200L, cb.length)
        val q = p.pasteAt(cb, listOf(t), 600)
        assertEquals(1200L, q.length)
        val clips = q.clipsOf(t)
        assertEquals(3, clips.size)
        // La partie collée est la portion [100, 300) de la source.
        val pasted = clips[1]
        assertEquals(600L, pasted.start); assertEquals(200L, pasted.length); assertEquals(100L, pasted.srcStart)
        // Le reste est repoussé de 200 trames.
        assertEquals(800L, clips[2].start); assertEquals(600L, clips[2].srcStart)
    }

    @Test
    fun `couper-coller deplace une portion`() {
        val (p, t, _) = base()
        val cb = p.copyRange(listOf(t), 0, 100)
        val q = p.deleteRange(listOf(t), 0, 100).pasteAt(cb, listOf(t), 900)
        assertEquals(1000L, q.length)
        val last = q.clipsOf(t).maxByOrNull { it.start }!!
        assertEquals(900L, last.start); assertEquals(0L, last.srcStart); assertEquals(100L, last.length)
    }

    @Test
    fun `inserer du silence ouvre un trou`() {
        val (p, t, _) = base()
        val q = p.insertSilence(listOf(t), 250, 100)
        val (a, b) = q.clipsOf(t)
        assertEquals(250L, a.end); assertEquals(350L, b.start); assertEquals(250L, b.srcStart)
        assertEquals(1100L, q.length)
    }

    @Test
    fun `rogner garde la selection et recale a zero`() {
        val (p, t, _) = base()
        val q = p.trimToRange(200, 700)
        val c = q.clipsOf(t).single()
        assertEquals(0L, c.start); assertEquals(500L, c.length); assertEquals(200L, c.srcStart)
    }

    @Test
    fun `repeter ajoute des copies a la suite`() {
        val (p, t, _) = base()
        val q = p.repeatRange(listOf(t), 0, 1000, 2)
        assertEquals(3000L, q.length)
        assertEquals(3, q.clipsOf(t).size)
    }

    @Test
    fun `rogner le bord gauche d'un clip avance la source`() {
        val (p, t, c) = base()
        val q = p.trimClipLeft(c, 250)
        val x = q.clipsOf(t).single()
        assertEquals(250L, x.start); assertEquals(750L, x.length); assertEquals(250L, x.srcStart)
    }

    @Test
    fun `rallonger le bord gauche est borne par la source`() {
        val (p, t, c) = base()
        val cut = p.trimClipLeft(c, 250)
        // On ne peut pas redescendre sous le début de la source.
        val back = cut.trimClipLeft(c, -400)
        val x = back.clipsOf(t).single()
        assertEquals(0L, x.start); assertEquals(0L, x.srcStart); assertEquals(1000L, x.length)
    }

    @Test
    fun `rogner le bord droit puis rallonger`() {
        val (p, t, c) = base()
        val cut = p.trimClipRight(c, 600)
        assertEquals(600L, cut.clipsOf(t).single().length)
        val back = cut.trimClipRight(c, 5000)
        assertEquals(1000L, back.clipsOf(t).single().length)
    }

    @Test
    fun `deplacer un clip d'une piste a l'autre`() {
        val (p, t1, c) = base()
        val (p2, t2) = p.addTrack("Piste 2")
        val q = p2.moveClip(c, 500, t2)
        assertTrue(q.clipsOf(t1).isEmpty())
        assertEquals(500L, q.clipsOf(t2).single().start)
        assertEquals(500L, p2.moveClip(c, 500).clipsOf(t1).single().start)
        assertEquals(0L, p2.moveClip(c, -30).clipsOf(t1).single().start)
    }

    @Test
    fun `dupliquer un clip le pose juste apres`() {
        val (p, t, c) = base()
        val (q, id) = p.duplicateClip(c)
        assertEquals(2, q.clipsOf(t).size)
        assertEquals(1000L, q.findClip(id!!)!!.second.start)
    }

    @Test
    fun `supprimer un clip avec ou sans refermer`() {
        val (p, t, _) = base()
        val q = p.splitAt(listOf(t), 500)
        val first = q.clipsOf(t)[0].id
        assertEquals(500L, q.deleteClip(first).clipsOf(t).single().start)
        assertEquals(0L, q.deleteClip(first, ripple = true).clipsOf(t).single().start)
    }

    @Test
    fun `retourner une plage inverse la lecture et miroite la place`() {
        val (p, t, _) = base()
        val q = p.reverseRange(listOf(t), 200, 600)
        val clips = q.clipsOf(t)
        assertEquals(3, clips.size)
        val mid = clips[1]
        assertTrue(mid.reversed)
        assertEquals(200L, mid.start); assertEquals(400L, mid.length)
        // Le premier échantillon joué est le dernier de la portion [200, 600).
        assertEquals(599L, mid.sourceFrame(0)); assertEquals(200L, mid.sourceFrame(399))
        // Retourner deux fois redonne le même son.
        val back = q.reverseRange(listOf(t), 200, 600).clipsOf(t)[1]
        assertFalse(back.reversed)
        assertEquals(200L, back.sourceFrame(0))
    }

    @Test
    fun `couper un clip retourne garde les bonnes trames`() {
        val (p, t, _) = base()
        val rev = p.reverseRange(listOf(t), 0, 1000)
        val q = rev.splitAt(listOf(t), 300)
        val (a, b) = q.clipsOf(t)
        // Retourné : à t=0 on joue la trame 999 ; la coupe à 300 laisse 999..700 puis 699..0.
        assertEquals(999L, a.sourceFrame(0)); assertEquals(700L, a.sourceFrame(299))
        assertEquals(699L, b.sourceFrame(0)); assertEquals(0L, b.sourceFrame(699))
    }

    @Test
    fun `les fondus restent continus quand on coupe en plein milieu`() {
        val (p, t, c) = base()
        val faded = p.setClipFades(c, 400, 0)
        val q = faded.splitAt(listOf(t), 100)
        val (a, b) = q.clipsOf(t)
        // Le gain à la trame 100 du clip d'origine = celui de la première trame du second morceau.
        val original = faded.clipsOf(t).single()
        assertEquals(original.gainAt(100), b.gainAt(0), 1e-5f)
        assertEquals(original.gainAt(99), a.gainAt(99), 1e-5f)
        assertEquals(original.gainAt(399), b.gainAt(299), 1e-5f)
        assertEquals(1f, b.gainAt(600), 1e-6f)
    }

    @Test
    fun `fondu de sortie decoupe au milieu`() {
        val (p, t, c) = base()
        val faded = p.setClipFades(c, 0, 500)
        val original = faded.clipsOf(t).single()
        val q = faded.splitAt(listOf(t), 750)
        val (a, b) = q.clipsOf(t)
        assertEquals(original.gainAt(749), a.gainAt(749), 1e-5f)
        assertEquals(original.gainAt(750), b.gainAt(0), 1e-5f)
        assertEquals(original.gainAt(999), b.gainAt(249), 1e-5f)
    }

    @Test
    fun `les fondus ne depassent pas la longueur du clip`() {
        val (p, t, c) = base()
        val q = p.setClipFades(c, 800, 800, FadeShape.S_CURVE).clipsOf(t).single()
        assertEquals(800L, q.fadeIn.len); assertEquals(200L, q.fadeOut.len)
    }

    @Test
    fun `courbes de fondu aux extremes`() {
        for (s in FadeShape.entries) {
            assertEquals(0f, s.curve(0f), 1e-6f)
            assertEquals(1f, s.curve(1f), 1e-6f)
        }
        assertEquals(0f, Fade.fadeIn(100).gainAt(0), 1e-6f)
        assertEquals(1f, Fade.fadeOut(100).gainAt(0), 1e-6f)
    }

    @Test
    fun `remplacer une plage par un rendu de meme duree ou plus long`() {
        val (p0, t, _) = base()
        val p = p0.addSource(Source("fx", "sources/fx.wav", 1, 400, 44100))
        // Même durée : la suite ne bouge pas.
        val same = p.replaceRange(t, 200, 600, "fx", 400, ripple = true)
        assertEquals(1000L, same.length)
        assertTrue(same.clipsOf(t).any { it.sourceId == "fx" && it.start == 200L })
        // Plus long avec ripple : la suite est repoussée.
        val longer = p.addSource(Source("fx2", "sources/fx2.wav", 1, 600, 44100)).replaceRange(t, 200, 600, "fx2", 600, ripple = true)
        assertEquals(1200L, longer.length)
        // Plus long sans ripple (queue d'écho) : la durée du projet ne bouge que si la queue dépasse la fin.
        val tail = p.addSource(Source("fx3", "sources/fx3.wav", 1, 600, 44100)).replaceRange(t, 200, 600, "fx3", 600, ripple = false)
        assertEquals(1000L, tail.length)
    }

    @Test
    fun `les reperes suivent un ripple sur toutes les pistes`() {
        val (p, t, _) = base()
        val (m, id) = p.addMarker(800, name = "x")
        val q = m.deleteRange(listOf(t), 100, 300)
        assertEquals(600L, q.markers.single { it.id == id }.pos)
        // Un repère dans la zone supprimée se colle à son bord.
        val (m2, id2) = p.addMarker(200)
        assertEquals(100L, m2.deleteRange(listOf(t), 100, 300).markers.single { it.id == id2 }.pos)
    }

    @Test
    fun `les identifiants ne se repetent jamais`() {
        val (p, t, _) = base()
        val q = p.splitAt(listOf(t), 100).splitAt(listOf(t), 500).deleteRange(listOf(t), 200, 300)
        val ids = q.tracks.flatMap { it.clips }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `l'enveloppe de piste interpole`() {
        val (p, t, _) = base()
        val track = p.track(t)!!.copy(envelope = listOf(com.Atom2Universe.app.audioeditor.core.EnvPoint(100, 1f), com.Atom2Universe.app.audioeditor.core.EnvPoint(300, 0f)))
        assertEquals(1f, track.envelopeGain(0), 1e-6f)
        assertEquals(0.5f, track.envelopeGain(200), 1e-6f)
        assertEquals(0f, track.envelopeGain(900), 1e-6f)
    }

    @Test
    fun `solo l'emporte sur sourdine`() {
        val (p, t1, _) = base()
        val (p2, t2) = p.addTrack("B")
        assertEquals(2, p2.audibleTracks().size)
        val muted = p2.mapTrack(t1) { it.copy(mute = true) }
        assertEquals(listOf(t2), muted.audibleTracks().map { it.id })
        val solo = muted.mapTrack(t1) { it.copy(solo = true) }
        assertEquals(listOf(t1), solo.audibleTracks().map { it.id })
    }

    // ---- Historique ----------------------------------------------------------------------

    @Test
    fun `annuler et rétablir`() {
        val (p, t, _) = base()
        val s = EditSession(p)
        assertFalse(s.canUndo)
        assertTrue(s.commit("split", p.splitAt(listOf(t), 500)))
        assertTrue(s.commit("delete", s.project.deleteRange(listOf(t), 0, 100)))
        assertEquals("delete", s.undoLabel)
        assertTrue(s.undo()); assertEquals("delete", s.redoLabel)
        assertEquals(1000L, s.project.length)
        assertTrue(s.undo()); assertEquals(1, s.project.tracks[0].clips.size)
        assertFalse(s.undo())
        assertTrue(s.redo()); assertTrue(s.redo()); assertFalse(s.redo())
        assertEquals(900L, s.project.length)
    }

    @Test
    fun `une nouvelle edition efface le rétablir`() {
        val (p, t, _) = base()
        val s = EditSession(p)
        s.commit("a", p.splitAt(listOf(t), 500))
        s.undo()
        assertTrue(s.canRedo)
        s.commit("b", p.splitAt(listOf(t), 300))
        assertFalse(s.canRedo)
    }

    @Test
    fun `un changement nul n'ajoute pas de pas`() {
        val (p, _, _) = base()
        val s = EditSession(p)
        assertFalse(s.commit("rien", p))
        assertFalse(s.canUndo)
    }

    @Test
    fun `un geste en apercu ne fait qu'un seul pas`() {
        val (p, t, c) = base()
        val s = EditSession(p)
        val before = s.project
        for (g in listOf(0.9f, 0.7f, 0.5f)) s.preview(s.project.mapTrack(t) { it.copy(volume = g) })
        s.commitGesture("volume", before)
        assertEquals(0.5f, s.project.track(t)!!.volume, 1e-6f)
        s.undo()
        assertEquals(1f, s.project.track(t)!!.volume, 1e-6f)
        assertFalse(s.canUndo)
        assertEquals(c, s.project.clipsOf(t).single().id)
    }

    @Test
    fun `la limite d'historique oublie les plus anciens pas`() {
        val (p, t, _) = base()
        val s = EditSession(p, limit = 3)
        for (i in 1..6) s.commit("e$i", s.project.splitAt(listOf(t), (i * 100).toLong()))
        var n = 0
        while (s.undo()) n++
        assertEquals(3, n)
    }

    @Test
    fun `les sources de l'historique restent protegees`() {
        val (p0, t, _) = base()
        val p = p0.addSource(Source("fx", "sources/fx.wav", 1, 1000, 44100))
        val s = EditSession(p)
        s.commit("effect", p.replaceRange(t, 0, 1000, "fx", 1000, ripple = true))
        // « a » n'est plus utilisée par le projet courant mais l'annulation peut la ramener.
        assertFalse("a" in s.project.usedSourceIds())
        assertTrue("a" in s.sourcesInHistory())
    }
}
