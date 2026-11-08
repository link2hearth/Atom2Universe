package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.data.StudioProjectCodec
import com.Atom2Universe.app.studio.data.StudioSession
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.StudioEngine
import com.Atom2Universe.app.studio.engine.TrackMix
import com.Atom2Universe.app.studio.music.Styles
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

/** Mixeur : effets de piste, solo, mixage sauvegardé, filtre et réverbération du pad. */
class StudioMixerTest {

    private val rate = 48_000

    private fun render(engine: StudioEngine, frames: Int, block: Int = 256): FloatArray {
        val out = FloatArray(frames)
        val l = FloatArray(block)
        val r = FloatArray(block)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            engine.render(l, r, n)
            for (i in 0 until n) out[done + i] = l[i] + r[i]
            done += n
        }
        return out
    }

    private fun dry(): StudioEngine = StudioEngine(rate).also {
        it.spaceAmount = 0f
        it.echoAmount = 0f
        for (inst in it.allInstruments) {
            inst.reverbSend = 0f
            inst.delaySend = 0f
        }
    }

    private fun rms(a: FloatArray, from: Int = 0, to: Int = a.size): Double {
        var s = 0.0
        for (i in from until to) s += a[i].toDouble() * a[i]
        return sqrt(s / (to - from))
    }

    /** Énergie des variations rapides (aigus) et lentes (graves) : différence première contre signal. */
    private fun highness(a: FloatArray): Double {
        var d = 0.0
        for (i in 1 until a.size) {
            val x = (a[i] - a[i - 1]).toDouble()
            d += x * x
        }
        return sqrt(d / a.size) / rms(a).coerceAtLeast(1e-9)
    }

    private fun hit(lane: Int, setup: (StudioEngine) -> Unit = {}): FloatArray {
        val e = dry()
        setup(e)
        e.trigger(lane, 60, 1f)
        return render(e, rate / 2)
    }

    @Test
    fun effectsOffAreBitIdentical() {
        val a = hit(Inst.SNARE)
        val b = hit(Inst.SNARE) { e ->
            e.applyMix(TrackMix.defaults().map { it.copy(drive = 0f, crush = 0f, filter = 0f) })
            for (inst in e.allInstruments) {
                inst.reverbSend = 0f
                inst.delaySend = 0f
            }
        }
        // le mixage d'origine règle niveau et panoramique : on compare au même réglage appliqué des deux côtés
        val c = hit(Inst.SNARE) { e ->
            e.applyMix(TrackMix.defaults())
            for (inst in e.allInstruments) {
                inst.reverbSend = 0f
                inst.delaySend = 0f
            }
        }
        assertTrue(a.isNotEmpty() && b.size == c.size)
        for (i in b.indices) assertEquals(b[i], c[i], 0f)
    }

    @Test
    fun lowpassFilterRemovesHighs() {
        val plain = hit(Inst.HAT)
        val muffled = hit(Inst.HAT) { it.instrument(Inst.HAT)!!.filterSweep = -1f }
        assertTrue(rms(muffled) < rms(plain) * 0.5)
        assertTrue(highness(muffled) < highness(plain))
    }

    @Test
    fun highpassFilterRemovesLows() {
        val plain = hit(Inst.KICK)
        val thin = hit(Inst.KICK) { it.instrument(Inst.KICK)!!.filterSweep = 1f }
        assertTrue(rms(thin) < rms(plain) * 0.5)
        assertTrue(highness(thin) > highness(plain))
    }

    @Test
    fun driveKeepsLoudnessWithinThreeDecibels() {
        val report = StringBuilder()
        var worst = 0.0
        for (lane in 0 until Inst.SYNTH) {
            val plain = rms(hit(lane))
            for (d in listOf(0.3f, 0.6f, 1f)) {
                val driven = rms(hit(lane) { it.instrument(lane)!!.drive = d })
                val db = 20 * log10(driven / plain)
                report.append("piste $lane drive $d : ${"%.1f".format(db)} dB\n")
                worst = maxOf(worst, abs(db))
            }
        }
        assertTrue("écart max ${"%.1f".format(worst)} dB\n$report", worst < 3.0)
    }

    @Test
    fun crushStaysFiniteAndBounded() {
        for (lane in 0 until Inst.COUNT) {
            val out = hit(lane) { e ->
                e.instrument(lane)!!.crush = 1f
                e.instrument(lane)!!.drive = 1f
                e.instrument(lane)!!.filterSweep = -0.5f
            }
            for (v in out) {
                assertFalse(v.isNaN() || v.isInfinite())
                assertTrue(abs(v) < 4f)
            }
        }
    }

    @Test
    fun effectsDoNotDependOnBlockSize() {
        fun run(block: Int): FloatArray {
            val e = dry()
            val inst = e.instrument(Inst.SNARE)!!
            inst.crush = 0.6f
            inst.drive = 0.5f
            inst.filterSweep = -0.4f
            e.trigger(Inst.SNARE, 60, 1f)
            return render(e, 20_000, block)
        }
        val a = run(64)
        val b = run(1000)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-5f)
    }

    @Test
    fun soloSilencesTheOthers() {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.toggleSolo(Inst.KICK)
        assertTrue(s.isSolo(Inst.KICK))
        assertFalse(engine.instrument(Inst.KICK)!!.muted)
        assertTrue(engine.instrument(Inst.SNARE)!!.muted)
        assertTrue(engine.instrument(Inst.SYNTH)!!.muted)
        s.toggleSolo(Inst.SNARE)
        assertFalse(engine.instrument(Inst.SNARE)!!.muted)
        s.toggleSolo(Inst.KICK)
        s.toggleSolo(Inst.SNARE)
        assertFalse(engine.allInstruments.any { it.muted })
    }

    @Test
    fun muteStaysWhenSoloIsCleared() {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.setMuted(Inst.HAT, true)
        s.toggleSolo(Inst.HAT)
        assertTrue("muet l'emporte sur solo", engine.instrument(Inst.HAT)!!.muted)
        s.toggleSolo(Inst.HAT)
        assertTrue(engine.instrument(Inst.HAT)!!.muted)
    }

    @Test
    fun mixSurvivesTheCodecAndStyleChange() {
        val engine = StudioEngine(rate)
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.updateTrack(Inst.KICK) { it.copy(level = 1.3f, pan = -0.4f, drive = 0.5f, crush = 0.25f, filter = -0.6f, delaySend = 0.3f) }
        s.toggleSolo(Inst.KICK)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(s.project.tracks.map { it.mix }, back.tracks.map { it.mix })
        assertEquals(setOf(Inst.KICK), back.solo)
        s.applyStyle(Styles.ALL.last())
        assertEquals(1.3f, s.mixOf(Inst.KICK).level, 0f)
        assertTrue(s.isSolo(Inst.KICK))
    }

    @Test
    fun settingSynthSpaceMovesBothSends() {
        val s = StudioSession(StudioEngine(rate), StudioProject.fromStyle(Styles.HOUSE))
        s.setSynthSpace(0.5f)
        assertEquals(0.5f, s.mixOf(Inst.SYNTH).reverbSend, 1e-6f)
        assertEquals(0.35f, s.mixOf(Inst.SYNTH).delaySend, 1e-6f)
    }

    @Test
    fun masterFilterMuffles() {
        fun hat(filter: Float): FloatArray {
            val e = dry()
            e.masterFilter = filter
            render(e, 4_000)
            e.trigger(Inst.HAT, 60, 1f)
            return render(e, rate / 2)
        }
        val open = hat(0f)
        val closed = hat(1f)
        assertTrue(rms(closed) < rms(open) * 0.3)
        for (v in closed) assertFalse(v.isNaN())
    }

    @Test
    fun masterFilterAndBoostAtRestChangeNothing() {
        fun run(touch: Boolean): FloatArray {
            val e = dry()
            if (touch) {
                e.masterFilter = 1f
                e.spaceBoost = 1f
                render(e, 4_000)
                e.masterFilter = 0f
                e.spaceBoost = 0f
                render(e, 48_000)
            }
            e.trigger(Inst.SNARE, 60, 1f)
            return render(e, 10_000)
        }
        val a = run(false)
        val b = run(true)
        for (i in a.indices) assertEquals(a[i], b[i], 1e-3f)
    }

    @Test
    fun spaceBoostAddsTail() {
        fun tail(boost: Float): Double {
            val e = dry()
            e.instrument(Inst.CLAP)!!.reverbSend = 0.5f
            e.spaceBoost = boost
            render(e, 4_000)
            e.trigger(Inst.CLAP, 60, 1f)
            val out = render(e, rate)
            return rms(out, rate / 2, rate)
        }
        assertTrue(tail(1f) > tail(0f) * 2)
    }

    // ---- Geste du pad enregistré dans la boucle ----

    private fun recordedSession(filter: Float, space: Float, from: Double = 0.0, to: Double = 16.0): Pair<StudioEngine, StudioSession> {
        val engine = dry()
        val s = StudioSession(engine, StudioProject.fromStyle(Styles.HOUSE))
        s.beginGesture()
        var pos = from
        while (pos < to) {
            s.recordGesture(pos, filter, space)
            pos += 0.1
        }
        s.endGesture()
        return engine to s
    }

    @Test
    fun recordedGestureIsPlayedBack() {
        val (plainEngine, _) = recordedSession(0f, 0f)
        val (gestureEngine, _) = recordedSession(1f, 0f)
        plainEngine.play()
        gestureEngine.play()
        val plain = render(plainEngine, rate * 2)
        val closed = render(gestureEngine, rate * 2)
        // filtre maître fermé sur toute la boucle : bien moins d'aigus (la grosse caisse, grave, reste)
        val half = rate
        assertTrue(highness(closed.copyOfRange(half, rate * 2)) < highness(plain.copyOfRange(half, rate * 2)) * 0.6)
    }

    @Test
    fun fingerOnPadBeatsTheRecordedGesture() {
        val (engine, _) = recordedSession(1f, 0f)
        engine.padActive = true
        engine.masterFilter = 0f
        val (closedEngine, _) = recordedSession(1f, 0f)
        engine.play()
        closedEngine.play()
        val finger = render(engine, rate * 2)
        val auto = render(closedEngine, rate * 2)
        assertTrue(highness(finger.copyOfRange(rate, rate * 2)) > highness(auto.copyOfRange(rate, rate * 2)) * 1.5)
    }

    @Test
    fun gestureOnlyOverwritesWhatWasTouched() {
        val (_, s) = recordedSession(1f, 0.5f, 0.0, 8.0)
        val f = s.project.pattern.autoFilter!!
        val sp = s.project.pattern.autoSpace!!
        assertEquals(1f, f[0], 0f)
        assertEquals(0.5f, sp[10], 0.011f)
        assertEquals(0f, f[f.size - 1], 0f)
        // Une seconde prise sur la fin ne touche pas le début
        s.beginGesture()
        s.recordGesture(12.0, 0.25f, 0f)
        s.recordGesture(13.0, 0.25f, 0f)
        s.endGesture()
        val g = s.project.pattern.autoFilter!!
        assertEquals(1f, g[0], 0f)
        assertEquals(0.25f, g[12 * 4 + 1], 0.011f)
    }

    @Test
    fun gestureSurvivesCodecAndStepCountChange() {
        val (_, s) = recordedSession(0.6f, 0.3f)
        val back = StudioProjectCodec.decode(StudioProjectCodec.encode(s.project))!!
        assertEquals(0.6f, back.pattern.autoFilter!![5], 0.011f)
        assertEquals(0.3f, back.pattern.autoSpace!![40], 0.011f)
        s.setStepCount(32)
        assertEquals(128, s.project.pattern.autoFilter!!.size)
        assertEquals(0.6f, s.project.pattern.autoFilter!![100], 0.011f)
        s.setStepCount(16)
        assertEquals(64, s.project.pattern.autoFilter!!.size)
    }

    @Test
    fun silentGestureIsNotKept() {
        val (_, s) = recordedSession(0f, 0f)
        assertFalse(s.project.pattern.hasAutomation)
        val (_, s2) = recordedSession(0.8f, 0f)
        assertTrue(s2.project.pattern.hasAutomation)
        s2.clearGesture()
        assertFalse(s2.project.pattern.hasAutomation)
    }

    @Test
    fun randomizeKeepsTheGesture() {
        val (_, s) = recordedSession(0.9f, 0f)
        s.randomize(5L)
        assertTrue(s.project.pattern.hasAutomation)
        s.clear()
        assertFalse(s.project.pattern.hasAutomation)
    }
}
