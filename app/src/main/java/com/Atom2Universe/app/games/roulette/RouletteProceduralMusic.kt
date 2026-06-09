package com.Atom2Universe.app.games.roulette

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.billthefarmer.mididriver.MidiDriver
import kotlin.random.Random

/**
 * Up-tempo casino theme for Roulette, played through Sonivox EAS.
 *
 * Ch 0 - lead riff
 * Ch 1 - organ stabs
 * Ch 2 - picked bass
 * Ch 3 - bright comping
 * Ch 4 - spin/win/lose stingers
 * Ch 9 - GM drums
 */
internal class RouletteProceduralMusic(private val scope: CoroutineScope) {

    private data class Groove(
        val root: Int,
        val scale: IntArray,
        val bpm: Int,
        val leadVelocity: IntRange,
        val bassVelocity: IntRange
    )

    companion object {
        private val MINOR_BLUES = intArrayOf(0, 3, 5, 6, 7, 10)

        private val MAIN_GROOVE = Groove(
            root = 57,
            scale = MINOR_BLUES,
            bpm = 144,
            leadVelocity = 70..104,
            bassVelocity = 78..112
        )

        private val CHORDS = arrayOf(
            intArrayOf(0, 3, 7, 10),
            intArrayOf(5, 8, 12, 15),
            intArrayOf(3, 7, 10, 15),
            intArrayOf(7, 10, 14, 17)
        )
    }

    private var driver: MidiDriver? = null
    private var musicJob: Job? = null
    private var ready = false
    private var active = false
    private var spinHeatBars = 0

    fun start() {
        if (active) return
        active = true
        val d = MidiDriver.getInstance {
            ready = true
            initChannels()
        }
        driver = d
        d.start()

        musicJob?.cancel()
        musicJob = scope.launch {
            delay(250)
            initChannels()
            musicLoop(MAIN_GROOVE)
        }
    }

    fun stop() {
        active = false
        ready = false
        musicJob?.cancel()
        musicJob = null
        runCatching { allNotesOff() }
        driver?.stop()
        driver = null
    }

    fun onSpinStarted() {
        spinHeatBars = 4
        stinger(intArrayOf(69, 72, 76, 81), 82, 70L)
    }

    fun onWin(multiplier: Int) {
        val top = if (multiplier >= 8) 88 else 84
        stinger(intArrayOf(72, 76, 79, top), 112, 95L)
        scope.launch {
            delay(380)
            perc(49, 105)
            delay(90)
            perc(57, 92)
        }
    }

    fun onLose() {
        stinger(intArrayOf(64, 61, 57), 64, 120L)
    }

    private suspend fun musicLoop(groove: Groove) {
        val tickMs = 60_000L / groove.bpm / 4
        var bar = 0
        var motif = generateMotif(groove.scale.size)
        while (currentCoroutineContext().isActive) {
            if (bar > 0 && bar % 8 == 0) motif = generateMotif(groove.scale.size)
            playBar(groove, tickMs, motif, bar)
            if (spinHeatBars > 0) spinHeatBars--
            bar++
        }
    }

    private suspend fun playBar(groove: Groove, tickMs: Long, motif: IntArray, bar: Int) {
        val chord = CHORDS[bar % CHORDS.size]
        val bassPattern = if (spinHeatBars > 0) intArrayOf(1, 0, 1, 1, 0, 1, 0, 1) else intArrayOf(1, 0, 0, 1, 0, 1, 0, 1)
        val leadPattern = if (spinHeatBars > 0) intArrayOf(1, 0, 1, 0, 1, 1, 0, 1) else intArrayOf(1, 0, 0, 1, 0, 0, 1, 0)

        var leadNote = -1
        var bassNote = -1
        var compNote = -1

        playChord(1, groove.root, chord, 58, holdMs = tickMs * 3)

        for (step in 0 until 16) {
            currentCoroutineContext().ensureActive()
            val t0 = System.currentTimeMillis()

            if (leadNote >= 0) {
                noteOff(0, leadNote)
                leadNote = -1
            }
            if (bassNote >= 0) {
                noteOff(2, bassNote)
                bassNote = -1
            }
            if (compNote >= 0) {
                noteOff(3, compNote)
                compNote = -1
            }

            playDrums(step, bar)

            if (step % 2 == 0 && bassPattern[step / 2] == 1) {
                val degree = when (step) {
                    0, 8 -> 0
                    6 -> 3
                    10 -> 4
                    else -> motif[(step / 2 + bar) % motif.size]
                }
                val note = groove.root - 24 + groove.scale[degree % groove.scale.size]
                noteOn(2, note.coerceIn(28, 52), Random.nextInt(groove.bassVelocity.first, groove.bassVelocity.last + 1))
                bassNote = note.coerceIn(28, 52)
            }

            if (step % 2 == 1 && Random.nextFloat() < 0.55f) {
                val stab = groove.root + chord.random() + if (Random.nextBoolean()) 12 else 0
                noteOn(3, stab.coerceIn(48, 86), 42)
                compNote = stab.coerceIn(48, 86)
            }

            if (step % 2 == 0 && leadPattern[step / 2] == 1) {
                val degree = motif[(step / 2 + bar) % motif.size]
                val octave = if (step == 14 && Random.nextFloat() < 0.55f) 24 else 12
                val note = groove.root + groove.scale[degree % groove.scale.size] + octave
                noteOn(0, note.coerceIn(60, 96), Random.nextInt(groove.leadVelocity.first, groove.leadVelocity.last + 1))
                leadNote = note.coerceIn(60, 96)
            }

            if (step == 12) playChord(1, groove.root, chord, 50, holdMs = tickMs * 2)

            val elapsed = System.currentTimeMillis() - t0
            delay((tickMs - elapsed).coerceAtLeast(1L))
        }

        if (leadNote >= 0) noteOff(0, leadNote)
        if (bassNote >= 0) noteOff(2, bassNote)
        if (compNote >= 0) noteOff(3, compNote)
    }

    private fun generateMotif(scaleSize: Int): IntArray {
        val pivot = Random.nextInt(1, scaleSize)
        val turn = (pivot + Random.nextInt(1, scaleSize)) % scaleSize
        return intArrayOf(0, pivot, turn, pivot, 0, 3, 4, 0)
    }

    private fun playDrums(step: Int, bar: Int) {
        if (step % 4 == 0) perc(36, if (step == 0 && bar % 4 == 0) 108 else 96)
        if (step == 4 || step == 12) perc(38, 86)
        if (step % 2 == 0) perc(42, if (spinHeatBars > 0) 54 else 42)
        if (step == 7 || step == 15) perc(46, if (spinHeatBars > 0) 72 else 50)
        if (step == 14 && Random.nextFloat() < 0.35f) perc(39, 48)
        if (step == 0 && bar % 8 == 0) perc(49, 58)
    }

    private fun initChannels() {
        programChange(0, 80)
        programChange(1, 16)
        programChange(2, 34)
        programChange(3, 4)
        programChange(4, 10)
        controlChange(0, 7, 90)
        controlChange(1, 7, 70)
        controlChange(2, 7, 96)
        controlChange(3, 7, 62)
        controlChange(4, 7, 105)
        controlChange(9, 7, 100)
        controlChange(0, 91, 34)
        controlChange(1, 91, 42)
        controlChange(3, 91, 28)
        controlChange(4, 91, 50)
    }

    private fun playChord(ch: Int, root: Int, intervals: IntArray, velocity: Int, holdMs: Long) {
        if (!canPlay()) return
        val notes = intervals.map { (root + it).coerceIn(36, 96) }
        notes.forEach { noteOn(ch, it, velocity) }
        scope.launch {
            delay(holdMs)
            notes.forEach { noteOff(ch, it) }
        }
    }

    private fun stinger(notes: IntArray, velocity: Int, stepMs: Long) {
        if (!active) return
        scope.launch {
            notes.forEach { note ->
                noteOn(4, note, velocity)
                delay(stepMs)
                noteOff(4, note)
            }
        }
    }

    private fun canPlay() = active && ready

    private fun noteOn(ch: Int, note: Int, velocity: Int) {
        if (!canPlay()) return
        driver?.queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), velocity.coerceIn(0, 127).toByte()))
    }

    private fun noteOff(ch: Int, note: Int) {
        if (!canPlay()) return
        driver?.queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
    }

    private fun programChange(ch: Int, prog: Int) {
        if (!active) return
        driver?.queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))
    }

    private fun controlChange(ch: Int, cc: Int, value: Int) {
        if (!active) return
        driver?.queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
    }

    private fun perc(note: Int, velocity: Int) {
        if (!canPlay()) return
        driver?.queueEvent(byteArrayOf(0x99.toByte(), note.toByte(), velocity.coerceIn(0, 127).toByte()))
    }

    private fun allNotesOff() {
        for (ch in 0..4) {
            driver?.queueEvent(byteArrayOf((0xB0 or ch).toByte(), 123.toByte(), 0))
        }
    }
}
