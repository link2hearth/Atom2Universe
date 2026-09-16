package com.Atom2Universe.app.games.caves

import kotlinx.coroutines.*
import org.billthefarmer.mididriver.MidiDriver
import kotlin.random.Random

/** Original piano miniatures. Main-thread lifecycle; SoundPool owns combat audio separately. */
class CaveProceduralMusic(private val scope: CoroutineScope) {
    private data class Piece(val bpm: Int, val chords: List<IntArray>, val phrases: List<IntArray>)
    companion object {
        // Four beats per bar, one cell per eighth note: 0 holds, -1 releases.
        // Composed phrases and harmonic arcs, never a random walk through a scale.
        private val PIECES = listOf(
            Piece(60, listOf(
                intArrayOf(48,55,64,71), intArrayOf(45,52,60,67),
                intArrayOf(41,48,57,64), intArrayOf(43,50,59,64)
            ), listOf(
                intArrayOf(76,0,0,74,0,71,0,-1), intArrayOf(72,0,0,0,76,0,71,0),
                intArrayOf(69,0,72,0,0,67,0,-1), intArrayOf(71,0,0,69,67,0,0,-1),
                intArrayOf(67,0,71,0,76,0,0,-1), intArrayOf(76,0,0,79,76,0,72,0),
                intArrayOf(72,0,69,0,67,0,0,-1), intArrayOf(69,0,71,0,72,0,0,0)
            )),
            Piece(54, listOf(
                intArrayOf(50,57,65,69), intArrayOf(46,53,62,69),
                intArrayOf(41,48,57,64), intArrayOf(48,55,62,67)
            ), listOf(
                intArrayOf(77,0,0,76,74,0,0,-1), intArrayOf(74,0,0,69,0,72,0,-1),
                intArrayOf(72,0,69,0,0,67,0,0), intArrayOf(67,0,0,74,72,0,0,-1),
                intArrayOf(69,0,74,0,77,0,0,-1), intArrayOf(77,0,0,74,0,72,0,0),
                intArrayOf(76,0,72,0,69,0,0,-1), intArrayOf(67,0,0,64,65,0,0,0)
            )),
            Piece(64, listOf(
                intArrayOf(43,50,59,66), intArrayOf(40,47,55,62),
                intArrayOf(48,55,64,71), intArrayOf(45,52,60,67)
            ), listOf(
                intArrayOf(74,0,0,71,0,69,0,-1), intArrayOf(67,0,0,71,74,0,0,0),
                intArrayOf(76,0,0,74,71,0,0,-1), intArrayOf(72,0,69,0,0,67,0,-1),
                intArrayOf(71,0,74,0,78,0,0,-1), intArrayOf(79,0,0,74,71,0,0,-1),
                intArrayOf(76,0,74,0,71,0,0,-1), intArrayOf(69,0,0,66,67,0,0,0)
            ))
        )
    }

    private var driver: MidiDriver? = null
    private var ready = false
    private var job: Job? = null
    private var nextPiece = Random.nextInt(PIECES.size)
    private var combatUntil = 0L

    /** Called on the main thread by the activity's event bridge. */
    fun onCombat() { combatUntil = android.os.SystemClock.uptimeMillis() + 4500 }

    private var dingJob: Job? = null
    private var lastDingMs = -1000L

    private fun ensureDriver() {
        if (driver == null) {
            driver = MidiDriver.getInstance()
            driver?.setOnMidiStartListener { ready = true }
            driver?.start()
        }
    }

    /** A dedicated MIDI channel, also usable in Assault without starting the music cycle. */
    fun playKillDing() {
        val now = android.os.SystemClock.uptimeMillis()
        if (now - lastDingMs < 100) return
        ensureDriver()
        if (!ready) return
        lastDingMs = now
        dingJob?.cancel()
        cc(3, 120, 0)
        send(0xC3, 9) // General MIDI glockenspiel.
        cc(3, 7, 100)
        cc(3, 11, 110)
        cc(3, 10, 64)
        cc(3, 64, 0)
        cc(3, 91, 25)
        note(3, 79, 100)
        dingJob = scope.launch {
            delay(220)
            send(0x83, 79, 0)
        }
    }

    fun start() {
        if (job?.isActive == true) return
        ensureDriver()
        if (!ready) return
        for (ch in 0..1) {
            send(0xC0 or ch, 0) // Acoustic grand piano, both hands.
            cc(ch, 7, if (ch == 0) 74 else 55)
            cc(ch, 10, if (ch == 0) 70 else 56)
            cc(ch, 91, 65)
            cc(ch, 64, 0)
        }
        job = scope.launch {
            delay(6000)
            while (isActive) {
                val piece = PIECES[nextPiece]
                nextPiece = (nextPiece + 1) % PIECES.size
                play(piece)
                delay(Random.nextLong(35_000, 75_001))
            }
        }
    }

    fun pause() {
        dingJob?.cancel()
        dingJob = null
        cc(3, 64, 0)
        cc(3, 123, 0)
        cc(3, 120, 0)
        job?.cancel()
        job = null
        silence()
    }
    fun resume() = start()
    fun stop() {
        pause()
        ready = false
        driver?.setOnMidiStartListener(null)
        driver?.stop()
        driver = null
    }

    private suspend fun play(piece: Piece) {
        val eighth = 30_000L / piece.bpm
        // A, A' (quieter), B, A: around two minutes, with a real ending.
        for (section in 0..3) {
            for (bar in 0..7) {
                val chord = piece.chords[bar % piece.chords.size]
                val phrase = piece.phrases[if (section == 2) (bar + 4) % 8 else bar]
                var melody = -1
                for (step in 0..7) {
                    currentCoroutineContext().ensureActive()
                    val duck = android.os.SystemClock.uptimeMillis() < combatUntil
                    for (ch in 0..1) cc(ch, 11, if (duck) 48 else 100)
                    if (step == 0) {
                        cc(1, 123, 0)
                        note(1, chord[0], 39)
                    }
                    if (step == 2 || step == 5) {
                        note(1, chord[if (step == 2) 1 else 2], 33)
                        if (section != 1) note(1, chord[3], 28)
                    }
                    val pitch = phrase[step]
                    if (pitch != 0) {
                        if (melody >= 0) send(0x80, melody, 0)
                        melody = pitch
                        if (pitch > 0) note(0, pitch, (if (section == 1) 43 else 52) + Random.nextInt(-3, 4))
                    }
                    delay(eighth + if (step == 7) 85 else 0)
                }
                if (melody >= 0) send(0x80, melody, 0)
                cc(1, 123, 0)
            }
        }
        note(1, piece.chords[0][0], 32)
        note(0, piece.phrases[0][0] - 12, 38)
        delay(3500)
        for (ch in 0..1) cc(ch, 123, 0)
    }

    private fun silence() {
        for (ch in 0..1) {
            cc(ch, 64, 0)
            cc(ch, 123, 0)
            cc(ch, 120, 0)
        }
    }
    private fun note(ch: Int, pitch: Int, velocity: Int) = send(0x90 or ch, pitch, velocity)
    private fun cc(ch: Int, controller: Int, value: Int) = send(0xB0 or ch, controller, value)
    private fun send(vararg bytes: Int) {
        if (ready) driver?.queueEvent(ByteArray(bytes.size) { bytes[it].toByte() })
    }
}
