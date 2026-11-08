package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.dsp.SynthPresets
import com.Atom2Universe.app.studio.engine.DemoPatterns
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.engine.Lane
import com.Atom2Universe.app.studio.engine.StepPattern
import com.Atom2Universe.app.studio.engine.StudioEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

/** Garde-fous du moteur du Studio : rythme exact, sortie saine, pas de clic, résultat indépendant des blocs. */
class StudioEngineTest {

    private val rate = 48_000

    /** Rend [frames] trames par blocs de [block] ; rend (gauche, droite). */
    private fun renderAll(engine: StudioEngine, frames: Int, block: Int): Pair<FloatArray, FloatArray> {
        val outL = FloatArray(frames)
        val outR = FloatArray(frames)
        val l = FloatArray(block)
        val r = FloatArray(block)
        var done = 0
        while (done < frames) {
            val n = minOf(block, frames - done)
            engine.render(l, r, n)
            System.arraycopy(l, 0, outL, done, n)
            System.arraycopy(r, 0, outR, done, n)
            done += n
        }
        return outL to outR
    }

    private fun demoEngine(bpm: Float = 120f, swing: Float = 0f) = StudioEngine(rate).also {
        it.pattern = DemoPatterns.house()
        it.bpm = bpm
        it.swing = swing
    }

    private fun stepFrames(engine: StudioEngine, blockSize: Int, seconds: Int): List<Long> {
        val frames = mutableListOf<Long>()
        engine.onStep = { _, f -> frames += f }
        engine.play()
        renderAll(engine, rate * seconds, blockSize)
        return frames
    }

    @Test
    fun stepsFallOnTheExactSampleWhateverTheBlockSize() {
        // 120 bpm à 48 kHz : une double-croche dure exactement 6000 échantillons.
        for (block in listOf(256, 97, 512)) {
            val frames = stepFrames(demoEngine(), block, 4)
            assertEquals("pas joués en 4 s (bloc $block)", 32, frames.size)
            for ((k, f) in frames.withIndex()) assertEquals("pas $k, bloc $block", k * 6000L, f)
        }
    }

    @Test
    fun swingDelaysOddStepsOnly() {
        val frames = stepFrames(demoEngine(swing = 0.2f), 256, 2)
        for ((k, f) in frames.withIndex()) {
            val expected = k * 6000L + if (k % 2 == 1) 1200L else 0L
            assertEquals("pas $k", expected, f)
        }
    }

    @Test
    fun oddTempoStaysWithinOneSampleOfTheIdealGrid() {
        // 137,3 bpm : la durée du pas n'est pas entière, l'erreur ne doit jamais s'accumuler.
        val bpm = 137.3f
        val step = rate * 60.0 / bpm / 4.0
        val frames = stepFrames(demoEngine(bpm = bpm), 211, 20)
        assertTrue(frames.size > 100)
        for ((k, f) in frames.withIndex()) {
            val ideal = (k * step)
            assertTrue("pas $k : $f contre ${ideal.roundToLong()}", abs(f - ideal) <= 1.0)
        }
    }

    @Test
    fun outputIsFiniteAndNeverAboveOne() {
        // Tout au maximum : toutes les voix, vélocités à fond, tempo rapide, gain maître poussé.
        val engine = demoEngine(bpm = 170f).also { it.masterGain = 2.5f }
        engine.play()
        val (l, r) = renderAll(engine, rate * 10, 256)
        var peak = 0f
        for (i in l.indices) {
            assertTrue("NaN/Inf à $i", l[i].isFinite() && r[i].isFinite())
            peak = maxOf(peak, abs(l[i]), abs(r[i]))
        }
        assertTrue("pic $peak", peak <= 1f)
        assertTrue("le limiteur aurait dû travailler", engine.limitedSamples > 0)
    }

    @Test
    fun defaultMixHasHeadroom() {
        // Au réglage normal, le limiteur ne doit presque jamais intervenir : c'est lui qui rend le son « saturé ».
        val engine = demoEngine()
        engine.play()
        val (l, r) = renderAll(engine, rate * 16, 256)
        var peak = 0f
        for (i in l.indices) peak = maxOf(peak, abs(l[i]), abs(r[i]))
        println("pic du mix par défaut : $peak ; échantillons limités : ${engine.limitedSamples}")
        assertTrue("trop fort : $peak", peak < 0.98f)
        assertEquals("le limiteur ne devrait pas intervenir", 0L, engine.limitedSamples)
        assertTrue("trop faible : $peak", peak > 0.3f)
    }

    @Test
    fun outputDoesNotDependOnBlockSize() {
        val a = demoEngine().also { it.play() }
        val b = demoEngine().also { it.play() }
        val (la, ra) = renderAll(a, rate * 3, 256)
        val (lb, rb) = renderAll(b, rate * 3, 101)
        assertArrayEquals(la, lb, 0f)
        assertArrayEquals(ra, rb, 0f)
    }

    @Test
    fun drumsDieOutAndFreeTheirVoices() {
        val engine = StudioEngine(rate)
        engine.spaceAmount = 0f // la queue de la réverbération dure plus longtemps que les instruments
        engine.echoAmount = 0f
        for (inst in listOf(Inst.KICK, Inst.SNARE, Inst.CLAP, Inst.HAT, Inst.OPEN_HAT, Inst.TOM, Inst.PERC)) engine.trigger(inst)
        val (l, r) = renderAll(engine, rate * 3, 256)
        var tail = 0f
        for (i in rate * 25 / 10 until l.size) tail = maxOf(tail, abs(l[i]), abs(r[i]))
        assertTrue("queue encore audible : $tail", tail < 1e-3f)
        assertEquals(0, engine.activeVoices)
    }

    @Test
    fun noClickOnNoteStartOrRelease() {
        // Une note de basse tenue puis relâchée : aucune marche brutale d'un échantillon à l'autre.
        for (patch in SynthPresets.ALL) {
            val engine = StudioEngine(rate)
            engine.synth.patch = patch
            engine.trigger(Inst.SYNTH, 33, 1f)
            val (l1, _) = renderAll(engine, rate / 2, 256)
            engine.release(Inst.SYNTH, 33)
            val (l2, _) = renderAll(engine, rate * 6, 256) // le drone met 2,5 s à se relâcher
            val all = l1 + l2
            var maxStep = 0f
            for (i in 1 until all.size) maxStep = maxOf(maxStep, abs(all[i] - all[i - 1]))
            println("${patch.id} : plus grand saut ${"%.4f".format(maxStep)}")
            assertTrue("${patch.id} : saut de $maxStep", maxStep < 0.12f)
            assertEquals(0, engine.synth.activeVoices)
        }
    }

    @Test
    fun stealingAVoiceDoesNotClick() {
        // Neuf notes sur huit voix : la neuvième reprend une voix sans saut.
        val engine = StudioEngine(rate)
        engine.synth.patch = SynthPresets.PAD
        var maxStep = 0f
        var prev = 0f
        val l = FloatArray(256)
        val r = FloatArray(256)
        for (k in 0 until 9) {
            engine.trigger(Inst.SYNTH, 40 + k * 3, 0.8f)
            for (b in 0 until 40) {
                engine.render(l, r, 256)
                for (i in 0 until 256) {
                    maxStep = maxOf(maxStep, abs(l[i] - prev))
                    prev = l[i]
                }
            }
        }
        assertTrue("saut de $maxStep", maxStep < 0.1f)
    }

    @Test
    fun stoppingReleasesHeldNotes() {
        val engine = demoEngine().also { it.play() }
        renderAll(engine, rate, 256)
        engine.stop()
        renderAll(engine, rate * 3, 256)
        assertEquals(0, engine.activeVoices)
        assertEquals(-1, engine.currentStep)
    }

    @Test
    fun kickHitsDoNotClick() {
        // Le « claquement » : un clic de bruit franc ou une reprise qui fait sauter l'onde. Le saut d'un échantillon à
        // l'autre autour de chaque attaque reste faible devant le pic (une vraie TR-909 enregistrée : ~25 %).
        val engine = StudioEngine(rate)
        engine.pattern = StepPattern(16, listOf(Lane(Inst.KICK, FloatArray(16) { if (it % 2 == 0) 1f else 0f })))
        engine.play()
        val (l, _) = renderAll(engine, rate * 4, 256)
        var peak = 0f
        for (x in l) peak = maxOf(peak, abs(x))
        var worst = 0f
        for (k in 0 until 16) {
            val f = k * 12_000
            if (f + 20 >= l.size) break
            for (i in maxOf(f - 3, 1)..f + 10) worst = maxOf(worst, abs(l[i] - l[i - 1]))
        }
        println("grosse caisse : saut max à l'attaque ${"%.1f".format(100 * worst / peak)} % du pic")
        assertTrue("claquement : ${100 * worst / peak} % du pic", worst / peak < 0.15f)
    }

    @Test
    fun hatsLiveWhereASmallSpeakerCanPlayThem() {
        // Un charleston dont l'énergie est presque toute au-dessus de 12 kHz est inaudible sur une tablette.
        val engine = StudioEngine(rate)
        for (inst in listOf(Inst.HAT, Inst.OPEN_HAT)) {
            engine.trigger(inst)
            val (l, _) = renderAll(engine, rate / 10, 256)
            val n = 4096
            val re = DoubleArray(n)
            val im = DoubleArray(n)
            for (i in 0 until n) re[i] = l[i] * (0.5 - 0.5 * kotlin.math.cos(2 * Math.PI * i / n))
            var high = 0.0
            var total = 0.0
            var centroidNum = 0.0
            var centroidDen = 0.0
            for (k in 1 until n / 2) {
                var sr = 0.0
                var si = 0.0
                // DFT directe, peu de bins utiles : 4096 × 2048 reste instantané en test
                val w = -2 * Math.PI * k / n
                for (i in 0 until n) {
                    sr += re[i] * kotlin.math.cos(w * i)
                    si += re[i] * kotlin.math.sin(w * i)
                }
                val mag = kotlin.math.sqrt(sr * sr + si * si)
                val hz = k * rate.toDouble() / n
                total += mag * mag
                if (hz >= 12_000) high += mag * mag
                centroidNum += mag * hz
                centroidDen += mag
            }
            val centroid = centroidNum / centroidDen
            println("charleston $inst : centroïde ${centroid.toInt()} Hz, ${"%.1f".format(100 * high / total)} % au-dessus de 12 kHz")
            assertTrue("centroïde $centroid", centroid in 6_000.0..10_500.0)
            assertTrue("trop d'aigus : ${high / total}", high / total < 0.2)
            engine.stop()
        }
    }

    @Test
    fun hatsSitAboutTwentyDbUnderTheKick() {
        // Rapport mesuré sur de vraies TR-808/909 : charleston fermé 17 à 21 dB sous la grosse caisse.
        fun rms100ms(inst: Int): Double {
            val e = StudioEngine(rate)
            e.masterGain = 1f
            e.trigger(inst)
            val (l, r) = renderAll(e, rate / 10, 256)
            var sum = 0.0
            for (i in l.indices) sum += (l[i] * l[i] + r[i] * r[i]) / 2.0
            return kotlin.math.sqrt(sum / l.size)
        }
        val ratioDb = 20 * kotlin.math.log10(rms100ms(Inst.HAT) / rms100ms(Inst.KICK))
        println("charleston fermé / grosse caisse : ${"%.1f".format(ratioDb)} dB")
        assertTrue("rapport $ratioDb dB", ratioDb in -23.0..-16.0)
    }

    @Test
    fun reverbTailIsBoundedAndDies() {
        val engine = StudioEngine(rate)
        engine.spaceAmount = 1f
        engine.echoAmount = 0f
        engine.trigger(Inst.CLAP)
        val (l, r) = renderAll(engine, rate * 12, 256)
        var tail = 0f
        for (i in rate * 11 until l.size) tail = maxOf(tail, abs(l[i]), abs(r[i]))
        var mid = 0f
        for (i in rate until rate * 2) mid = maxOf(mid, abs(l[i]))
        for (x in l) assertTrue(x.isFinite())
        println("réverbération : niveau à 1-2 s $mid, à 11-12 s $tail")
        assertTrue("la queue doit s'entendre à 1 s : $mid", mid > 0.001f)
        assertTrue("la queue doit mourir : $tail", tail < 1e-3f)
    }

    @Test
    fun delayRepeatsAtTheTempoAndAlternatesSides() {
        // Une croche pointée à 120 bpm = 0,375 s = 18 000 échantillons : le premier écho est à gauche, le deuxième à droite.
        val engine = StudioEngine(rate)
        engine.spaceAmount = 0f
        engine.echoAmount = 1f
        engine.bpm = 120f
        engine.instrument(Inst.PERC)!!.delaySend = 1f
        engine.instrument(Inst.PERC)!!.level = 1f
        engine.trigger(Inst.PERC)
        val (l, r) = renderAll(engine, rate * 2, 256)
        fun energy(a: FloatArray, from: Int, to: Int): Double {
            var e = 0.0
            for (i in from until to) e += a[i].toDouble() * a[i]
            return e
        }
        val firstL = energy(l, 18_000, 18_000 + 4_000)
        val firstR = energy(r, 18_000, 18_000 + 4_000)
        val secondL = energy(l, 36_000, 36_000 + 4_000)
        val secondR = energy(r, 36_000, 36_000 + 4_000)
        println("écho : 1er L=$firstL R=$firstR ; 2e L=$secondL R=$secondR")
        assertTrue("1er écho à gauche", firstL > firstR * 20)
        assertTrue("2e écho à droite", secondR > secondL * 20)
    }

    @Test
    fun mutedInstrumentIsSilentButOthersPlay() {
        val engine = StudioEngine(rate)
        engine.spaceAmount = 0f
        engine.echoAmount = 0f
        engine.instrument(Inst.KICK)!!.muted = true
        engine.trigger(Inst.KICK)
        val (l, _) = renderAll(engine, rate / 4, 256)
        var peak = 0f
        for (x in l) peak = maxOf(peak, abs(x))
        assertEquals(0f, peak, 0f)
        engine.trigger(Inst.SNARE)
        val (l2, _) = renderAll(engine, rate / 4, 256)
        for (x in l2) peak = maxOf(peak, abs(x))
        assertTrue(peak > 0.1f)
    }

    @Test
    fun tomAndPercussionSitNearTheirRealCounterparts() {
        // Mesuré sur une TR-808 : tom moyen ~0,7 dB sous la grosse caisse, cloche ~5,7 dB sous (RMS sur 100 ms).
        fun rms(inst: Int): Double {
            val e = StudioEngine(rate)
            e.masterGain = 1f
            e.spaceAmount = 0f
            e.echoAmount = 0f
            e.trigger(inst)
            val (l, r) = renderAll(e, rate / 10, 256)
            var sum = 0.0
            for (i in l.indices) sum += (l[i] * l[i] + r[i] * r[i]) / 2.0
            return kotlin.math.sqrt(sum / l.size)
        }
        val kick = rms(Inst.KICK)
        val tom = 20 * kotlin.math.log10(rms(Inst.TOM) / kick)
        val perc = 20 * kotlin.math.log10(rms(Inst.PERC) / kick)
        println("tom / grosse caisse : ${"%.1f".format(tom)} dB ; cloche / grosse caisse : ${"%.1f".format(perc)} dB")
        assertTrue("tom $tom dB", tom in -5.0..1.0)
        assertTrue("cloche $perc dB", perc in -11.0..-3.0)
    }
}
