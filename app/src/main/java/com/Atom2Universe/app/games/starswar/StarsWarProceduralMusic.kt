package com.Atom2Universe.app.games.starswar

import android.util.Log
import kotlinx.coroutines.*
import org.billthefarmer.mididriver.MidiDriver
import kotlin.random.Random

/**
 * Musique procédurale sci-fi pour Stars War via Sonivox EAS.
 *
 * Thème adapté au palier de vague. Appeler [onWaveChanged] à chaque nouvelle vague.
 *
 * Ch 0 — mélodie (Vibraphone GM 11 ou Electric Piano GM 4)
 * Ch 1 — basse (Synth Bass 2 GM 39)
 * Ch 9 — percussions GM
 */
class StarsWarProceduralMusic(private val scope: CoroutineScope) {

    private data class Theme(
        val label:    String,
        val progMel:  Int,
        val progBass: Int,
        val root:     Int,
        val scale:    IntArray,
        val bpm:      Int,
        val density:  Float
    )

    companion object {
        private const val TAG = "StarsWarMusic"

        private val PENTATONIC_MAJOR = intArrayOf(0, 2, 4, 7, 9)
        private val DORIAN           = intArrayOf(0, 2, 3, 5, 7, 9, 10)
        private val PHRYGIAN         = intArrayOf(0, 1, 3, 5, 7, 8, 10)
        private val NAT_MINOR        = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        private val LOCRIAN          = intArrayOf(0, 1, 3, 5, 6, 8, 10)

        private val THEMES = listOf(
            Theme("Dérive",     progMel = 11, progBass = 39, root = 60, scale = PENTATONIC_MAJOR, bpm = 85,  density = 0.35f),
            Theme("Secteur",    progMel = 11, progBass = 39, root = 62, scale = DORIAN,           bpm = 100, density = 0.42f),
            Theme("Alerte",     progMel =  4, progBass = 39, root = 64, scale = PHRYGIAN,         bpm = 115, density = 0.48f),
            Theme("Invasion",   progMel =  4, progBass = 39, root = 57, scale = NAT_MINOR,        bpm = 125, density = 0.52f),
            Theme("Apocalypse", progMel =  4, progBass = 39, root = 59, scale = LOCRIAN,          bpm = 135, density = 0.58f),
        )

        private fun themeFor(wave: Int): Theme = when {
            wave <= 4  -> THEMES[0]
            wave <= 9  -> THEMES[1]
            wave <= 14 -> THEMES[2]
            wave <= 19 -> THEMES[3]
            else       -> THEMES[4]
        }
    }

    private var musicJob: Job? = null
    private var currentWave = 1

    fun start(wave: Int = currentWave) {
        currentWave = wave
        musicJob?.cancel()
        Log.d(TAG, "start() wave=$wave thème=${themeFor(wave).label}")
        musicJob = scope.launch {
            delay(500)
            val theme = themeFor(currentWave)
            initChannels(theme)
            musicLoop(theme)
        }
    }

    fun stop() {
        Log.d(TAG, "stop()")
        musicJob?.cancel()
        musicJob = null
        runCatching { allNotesOff() }
    }

    fun onWaveChanged(wave: Int) {
        val newTheme = themeFor(wave)
        val oldTheme = themeFor(currentWave)
        currentWave = wave
        Log.d(TAG, "waveChanged wave=$wave thème=${newTheme.label}")
        if (newTheme.label == oldTheme.label) return
        musicJob?.cancel()
        musicJob = scope.launch {
            allNotesOff()
            delay(200)
            initChannels(newTheme)
            musicLoop(newTheme)
        }
    }

    private fun initChannels(t: Theme) {
        programChange(0, t.progMel)
        programChange(1, t.progBass)
        controlChange(0, 7, 100)
        controlChange(1, 7, 88)
    }

    private suspend fun musicLoop(theme: Theme) {
        val tickMs  = 60_000L / theme.bpm / 4
        var cycle   = 0
        var rootDeg = 0
        var density = theme.density

        while (currentCoroutineContext().isActive) {
            if (cycle > 0 && cycle % Random.nextInt(4, 9) == 0) {
                rootDeg = (rootDeg + Random.nextInt(-1, 2) + theme.scale.size) % theme.scale.size
                density = (density + Random.nextFloat() * 0.14f - 0.07f)
                    .coerceIn(theme.density * 0.6f, theme.density * 1.5f)
            }
            playBar(theme, tickMs, rootDeg, density)
            cycle++
        }
    }

    private suspend fun playBar(theme: Theme, tickMs: Long, rootDeg: Int, density: Float) {
        val bassHits = euclidean(3, 8)
        var melNote  = -1
        var bassNote = -1

        for (step in 0 until 16) {
            currentCoroutineContext().ensureActive()
            val t0 = System.currentTimeMillis()

            if (melNote  >= 0) { noteOff(0, melNote);  melNote  = -1 }
            if (bassNote >= 0) { noteOff(1, bassNote); bassNote = -1 }

            // Percussions — style électro spatial
            when (step) {
                0        -> { perc(36, 85); perc(42, 40) }
                4, 12    ->   perc(42, 32)
                8        -> { perc(36, 72); perc(42, 40) }
                2, 6, 10, 14 -> if (Random.nextFloat() < 0.28f) perc(42, 22)
            }
            // Open hi-hat sur le "et" du 3e temps
            if (step == 10 && Random.nextFloat() < 0.40f) perc(46, 55)

            // Basse euclidienne
            if (step % 2 == 0 && bassHits[step / 2] == 1) {
                val n = theme.root - 12 + theme.scale[rootDeg]
                noteOn(1, n, 80)
                bassNote = n
            }

            // Mélodie probabiliste
            if (Random.nextFloat() < density) {
                val deg = (rootDeg + Random.nextInt(theme.scale.size)) % theme.scale.size
                val oct = if (Random.nextFloat() < 0.12f) 12 else 0
                val n   = theme.root + theme.scale[deg] + oct
                noteOn(0, n, Random.nextInt(55, 95))
                melNote = n
            }

            val elapsed = System.currentTimeMillis() - t0
            delay((tickMs - elapsed).coerceAtLeast(1L))
        }

        if (melNote  >= 0) noteOff(0, melNote)
        if (bassNote >= 0) noteOff(1, bassNote)
    }

    private fun euclidean(k: Int, n: Int): IntArray {
        val p = IntArray(n); var acc = 0
        for (i in 0 until n) { acc += k; if (acc >= n) { acc -= n; p[i] = 1 } }
        return p
    }

    private fun d() = MidiDriver.getInstance()

    private fun noteOn(ch: Int, note: Int, vel: Int) =
        d().queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))

    private fun noteOff(ch: Int, note: Int) =
        d().queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))

    private fun programChange(ch: Int, prog: Int) =
        d().queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))

    private fun controlChange(ch: Int, cc: Int, value: Int) =
        d().queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))

    private fun perc(note: Int, vel: Int) =
        d().queueEvent(byteArrayOf(0x99.toByte(), note.toByte(), vel.toByte()))

    private fun allNotesOff() {
        for (ch in 0..2) controlChange(ch, 123, 0)
    }
}
