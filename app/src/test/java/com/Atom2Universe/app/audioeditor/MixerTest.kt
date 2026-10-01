package com.Atom2Universe.app.audioeditor

import com.Atom2Universe.app.audioeditor.core.EnvPoint
import com.Atom2Universe.app.audioeditor.core.Levels
import com.Atom2Universe.app.audioeditor.core.Mixer
import com.Atom2Universe.app.audioeditor.core.Project
import com.Atom2Universe.app.audioeditor.core.SampleProvider
import com.Atom2Universe.app.audioeditor.core.Source
import com.Atom2Universe.app.audioeditor.core.addClip
import com.Atom2Universe.app.audioeditor.core.addSource
import com.Atom2Universe.app.audioeditor.core.addTrack
import com.Atom2Universe.app.audioeditor.core.setClipFades
import com.Atom2Universe.app.audioeditor.core.setClipGain
import com.Atom2Universe.app.audioeditor.core.splitAt
import com.Atom2Universe.app.audioeditor.core.reverseRange
import org.junit.Assert.assertEquals
import org.junit.Test

class MixerTest {

    /** Des sources en mémoire : « id » → un tableau par canal. */
    private class MemoryProvider(val data: Map<String, Array<FloatArray>>) : SampleProvider {
        override fun read(source: Source, frame: Long, count: Int, dst: Array<FloatArray>, off: Int) {
            val d = data.getValue(source.id)
            for (c in dst.indices) for (i in 0 until count) {
                val f = frame + i
                dst[c][off + i] = if (f < 0 || f >= d[0].size) 0f else d[minOf(c, d.size - 1)][f.toInt()]
            }
        }
    }

    /** Une source mono « m » de n trames valant 0, 1, 2… (divisé par 1000) pour repérer chaque trame. */
    private fun ramp(n: Int) = FloatArray(n) { it / 1000f }

    private fun project(n: Int = 100, stereo: Boolean = false): Triple<Project, Int, MemoryProvider> {
        val chans = if (stereo) 2 else 1
        var p = Project().addSource(Source("m", "m.wav", chans, n.toLong(), 44100))
        val (p1, t) = p.addTrack("A")
        p = p1.addClip(t, "m", 0).first
        val data = if (stereo) arrayOf(ramp(n), FloatArray(n) { -it / 1000f }) else arrayOf(ramp(n))
        return Triple(p, t, MemoryProvider(mapOf("m" to data)))
    }

    private fun render(p: Project, prov: MemoryProvider, start: Long, frames: Int): Pair<FloatArray, FloatArray> {
        val l = FloatArray(frames); val r = FloatArray(frames)
        Mixer(prov).render(p, start, frames, l, r)
        return l to r
    }

    @Test
    fun `un clip mono sort au centre sur les deux voies`() {
        val (p, _, prov) = project()
        val (l, r) = render(p, prov, 0, 100)
        for (i in 0 until 100) { assertEquals(i / 1000f, l[i], 1e-6f); assertEquals(i / 1000f, r[i], 1e-6f) }
    }

    @Test
    fun `un clip stereo garde ses deux voies`() {
        val (p, _, prov) = project(stereo = true)
        val (l, r) = render(p, prov, 0, 50)
        assertEquals(0.02f, l[20], 1e-6f); assertEquals(-0.02f, r[20], 1e-6f)
    }

    @Test
    fun `avant et apres le clip c'est le silence`() {
        val (p, t, prov) = project()
        val moved = p.copy(tracks = p.tracks.map { it.withClips(it.clips.map { c -> c.copy(start = 50) }) })
        val (l, _) = render(moved, prov, 0, 200)
        assertEquals(0f, l[49], 0f); assertEquals(0f, l[50], 0f); assertEquals(0.001f, l[51], 1e-6f)
        assertEquals(0.099f, l[149], 1e-6f); assertEquals(0f, l[150], 0f)
        check(t > 0)
    }

    @Test
    fun `gain de clip et volume de piste se multiplient`() {
        val (p, t, prov) = project()
        val c = p.track(t)!!.clips.single().id
        val q = p.setClipGain(c, 0.5f).mapTrack(t) { it.copy(volume = 0.5f) }.copy(master = 2f)
        val (l, _) = render(q, prov, 0, 100)
        assertEquals(50 / 1000f * 0.5f, l[50], 1e-6f)
    }

    @Test
    fun `panoramique a la maniere d'audacity`() {
        val (p, t, prov) = project()
        val left = render(p.mapTrack(t) { it.copy(pan = -1f) }, prov, 0, 100)
        assertEquals(0.05f, left.first[50], 1e-6f); assertEquals(0f, left.second[50], 1e-6f)
        val right = render(p.mapTrack(t) { it.copy(pan = 1f) }, prov, 0, 100)
        assertEquals(0f, right.first[50], 1e-6f); assertEquals(0.05f, right.second[50], 1e-6f)
        val half = render(p.mapTrack(t) { it.copy(pan = 0.5f) }, prov, 0, 100)
        assertEquals(0.05f, half.second[50], 1e-6f); assertEquals(0.025f, half.first[50], 1e-6f)
    }

    @Test
    fun `un clip retourne se lit a l'envers`() {
        val (p, t, prov) = project()
        val q = p.reverseRange(listOf(t), 0, 100)
        val (l, _) = render(q, prov, 0, 100)
        assertEquals(0.099f, l[0], 1e-6f); assertEquals(0f, l[99], 1e-6f); assertEquals(0.049f, l[50], 1e-6f)
    }

    @Test
    fun `un bloc au milieu d'un clip retourne tombe sur les bonnes trames`() {
        val (p, t, prov) = project()
        val q = p.reverseRange(listOf(t), 0, 100)
        val (l, _) = render(q, prov, 10, 20)
        // Trame de timeline 10 → trame de source 89.
        assertEquals(0.089f, l[0], 1e-6f); assertEquals(0.070f, l[19], 1e-6f)
    }

    @Test
    fun `fondu d'entree lineaire`() {
        val (p, t, prov) = project()
        val c = p.track(t)!!.clips.single().id
        val q = p.setClipFades(c, 10, 0)
        val (l, _) = render(q, prov, 0, 100)
        assertEquals(0f, l[0], 1e-6f)
        assertEquals(5 / 1000f * 0.5f, l[5], 1e-6f)
        assertEquals(20 / 1000f, l[20], 1e-6f)
    }

    @Test
    fun `deux clips qui se chevauchent s'additionnent`() {
        val (p, t, prov) = project()
        val q = p.mapTrack(t) { tr -> tr.withClips(tr.clips + tr.clips.single().copy(id = 99)) }
        val (l, _) = render(q, prov, 0, 100)
        assertEquals(2 * 0.03f, l[30], 1e-6f)
    }

    @Test
    fun `sourdine et solo`() {
        val (p, t, prov) = project()
        val (p2, t2) = p.addTrack("B")
        val p3 = p2.addSource(Source("n", "n.wav", 1, 100, 44100)).addClip(t2, "n", 0).first
        val both = MemoryProvider(prov.data + ("n" to arrayOf(FloatArray(100) { 1f })))
        assertEquals(1.03f, render(p3, both, 0, 100).first[30], 1e-6f)
        assertEquals(1f, render(p3.mapTrack(t) { it.copy(mute = true) }, both, 0, 100).first[30], 1e-6f)
        assertEquals(0.03f, render(p3.mapTrack(t) { it.copy(solo = true) }, both, 0, 100).first[30], 1e-6f)
    }

    @Test
    fun `l'enveloppe de piste module le volume`() {
        val (p, t, _) = project()
        val flat = MemoryProvider(mapOf("m" to arrayOf(FloatArray(100) { 1f })))
        val q = p.mapTrack(t) { it.copy(envelope = listOf(EnvPoint(0, 1f), EnvPoint(100, 0f))) }
        val (l, _) = render(q, flat, 0, 100)
        assertEquals(1f, l[0], 1e-6f); assertEquals(0.5f, l[50], 1e-6f)
    }

    @Test
    fun `mixer par petits blocs donne le meme son qu'en un seul bloc`() {
        val (p0, t, prov) = project(n = 500, stereo = true)
        val c = p0.track(t)!!.clips.single().id
        val p = p0.setClipFades(c, 40, 60).splitAt(listOf(t), 200).reverseRange(listOf(t), 250, 400)
        val (whole, _) = render(p, prov, 0, 500)
        val mixer = Mixer(prov)
        val out = FloatArray(500)
        var pos = 0
        while (pos < 500) {
            val n = minOf(37, 500 - pos)
            val l = FloatArray(n); val r = FloatArray(n)
            mixer.render(p, pos.toLong(), n, l, r)
            System.arraycopy(l, 0, out, pos, n)
            pos += n
        }
        for (i in 0 until 500) assertEquals("trame $i", whole[i], out[i], 1e-6f)
    }

    @Test
    fun `les cretes sont mesurees apres le volume general`() {
        val (p, _, prov) = project(stereo = true)
        val levels = Levels()
        val l = FloatArray(100); val r = FloatArray(100)
        Mixer(prov).render(p.copy(master = 2f), 0, 100, l, r, levels)
        assertEquals(0.099f * 2, levels.peakL, 1e-6f)
        assertEquals(0.099f * 2, levels.peakR, 1e-6f)
    }
}
