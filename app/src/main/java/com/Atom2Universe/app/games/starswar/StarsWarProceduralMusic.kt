package com.Atom2Universe.app.games.starswar

import android.os.SystemClock
import kotlinx.coroutines.*
import org.billthefarmer.mididriver.MidiDriver

/** Trois compositions originales. Appels sur le thread principal ; SFX indépendants. */
class StarsWarProceduralMusic(private val scope: CoroutineScope) {
    private data class Song(val bpm: Int, val program: Int, val melody: List<IntArray>)
    companion object {
        // Une case = une croche ; 0 prolonge, -1 laisse respirer la phrase.
        // Cmaj7, Am7, Fmaj7, G6, Em7, Am7, Dm7, G6.
        private val CHORDS = listOf(
            intArrayOf(60,64,67,71), intArrayOf(57,60,64,67),
            intArrayOf(53,57,60,64), intArrayOf(55,59,62,64),
            intArrayOf(52,55,59,62), intArrayOf(57,60,64,67),
            intArrayOf(50,53,57,60), intArrayOf(55,59,62,64)
        )
        private val SONGS = listOf(
            Song(96, 8, listOf( // Célesta : Petites orbites
                intArrayOf(76,0,79,81,79,0,76,-1),
                intArrayOf(72,0,76,79,76,0,72,-1),
                intArrayOf(69,72,76,0,74,72,69,0),
                intArrayOf(71,0,74,76,74,0,-1,-1),
                intArrayOf(79,0,76,74,71,0,74,-1),
                intArrayOf(76,0,79,76,72,0,-1,-1),
                intArrayOf(74,77,76,74,72,0,69,-1),
                intArrayOf(71,74,79,0,76,74,72,0)
            )),
            Song(108, 12, listOf( // Marimba : Courrier des comètes
                intArrayOf(72,76,79,-1,76,79,81,79),
                intArrayOf(76,0,72,69,72,76,79,-1),
                intArrayOf(77,76,72,-1,69,72,76,0),
                intArrayOf(74,71,67,-1,71,74,76,74),
                intArrayOf(71,74,76,79,83,0,79,-1),
                intArrayOf(81,79,76,-1,72,76,79,0),
                intArrayOf(77,0,74,72,69,72,74,-1),
                intArrayOf(71,74,79,-1,74,71,72,0)
            )),
            Song(116, 11, listOf( // Vibraphone : Carrousel stellaire
                intArrayOf(79,76,79,0,83,81,79,-1),
                intArrayOf(81,76,72,0,76,79,81,-1),
                intArrayOf(81,79,77,76,77,0,72,-1),
                intArrayOf(79,74,71,74,76,0,74,-1),
                intArrayOf(79,0,83,81,79,76,74,-1),
                intArrayOf(76,79,81,0,84,81,79,-1),
                intArrayOf(81,77,74,0,77,76,74,-1),
                intArrayOf(79,0,74,71,74,0,72,0)
            ))
        )
        private fun songIndex(wave: Int) = ((wave.coerceAtLeast(1) - 1) / 5) % SONGS.size
    }
    private var driver: MidiDriver? = null
    private var ready = false
    private var musicJob: Job? = null
    private var currentWave = 1
    private var paused = false
    private var bar = 0

    fun start(wave: Int = currentWave) {
        if (driver != null) return
        currentWave = wave
        val d = MidiDriver.getInstance()
        driver = d
        d.setOnMidiStartListener {
            ready = true
            initChannels()
            if (!paused) launchMusic()
        }
        d.start()
    }
    fun stop() {
        musicJob?.cancel()
        musicJob = null
        silence(true)
        ready = false
        driver?.setOnMidiStartListener(null)
        driver?.stop()
        driver = null
    }
    fun setPaused(value: Boolean) {
        if (paused == value) return
        paused = value
        if (value) {
            musicJob?.cancel()
            musicJob = null
            silence(true)
        } else if (ready) launchMusic()
    }
    fun onWaveChanged(wave: Int) {
        if (wave == 1 && currentWave != 1) bar = 0
        currentWave = wave
        // Entrée du nouveau thème à la prochaine mesure.
    }
    private fun initChannels() {
        program(1, 4) // Piano électrique
        program(2, 32) // Contrebasse
        program(3, 12) // Réponse de marimba
        for (ch in 0..3) {
            cc(ch, 7, intArrayOf(66,43,52,38)[ch])
            cc(ch, 10, intArrayOf(70,52,64,82)[ch])
            cc(ch, 91, 22)
            cc(ch, 93, 0)
            cc(ch, 64, 0)
        }
    }
    private fun launchMusic() {
        musicJob?.cancel()
        musicJob = scope.launch {
            var previousSong = -1
            while (isActive) {
                val index = songIndex(currentWave)
                if (index != previousSong) {
                    program(0, SONGS[index].program)
                    previousSong = index
                }
                playBar(SONGS[index], bar)
                bar = (bar + 1) % 16
            }
        }
    }
    private suspend fun playBar(song: Song, measure: Int) {
        val chord = CHORDS[measure % 8]
        val phrase = song.melody[measure % 8]
        val tick = 30_000L / song.bpm
        val start = SystemClock.uptimeMillis()
        var melody = -1
        var bass = -1
        var answer = -1
        for (step in 0..7) {
            currentCoroutineContext().ensureActive()
            if (step == 0 || step == 4) {
                if (bass >= 0) off(2, bass)
                bass = chord[if (step == 0) 0 else 2] - 12
                on(2, bass, 54)
                chord.forEach { off(1, it); on(1, it, if (step == 0) 42 else 32) }
            }
            if (phrase[step] != 0) {
                if (melody >= 0) off(0, melody)
                melody = phrase[step]
                if (melody > 0) on(0, melody, if (step % 2 == 0) 64 else 51)
            }
            if (answer >= 0) off(3, answer)
            answer = -1
            // Deuxième passage : une réponse discrète renouvelle l'arrangement.
            if (measure >= 8 && (step == 3 || step == 7)) {
                answer = chord[if (step == 3) 2 else 1] + 12
                on(3, answer, 40)
            }
            delay((start + (step + 1) * tick - SystemClock.uptimeMillis()).coerceAtLeast(1))
        }
        silence(false)
    }
    private fun send(vararg bytes: Int) { driver?.queueEvent(bytes.map { it.toByte() }.toByteArray()) }
    private fun on(ch: Int, note: Int, velocity: Int) = send(0x90 or ch, note, velocity)
    private fun off(ch: Int, note: Int) = send(0x80 or ch, note, 0)
    private fun cc(ch: Int, controller: Int, value: Int) = send(0xB0 or ch, controller, value)
    private fun program(ch: Int, value: Int) = send(0xC0 or ch, value)
    private fun silence(immediate: Boolean) { for (ch in 0..3) cc(ch, if (immediate) 120 else 123, 0) }
}
