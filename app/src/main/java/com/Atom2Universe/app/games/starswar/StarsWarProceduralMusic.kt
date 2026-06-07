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
 * Ch 0 — mélodie (Strings GM 48 → Synth Lead GM 80/81 selon intensité)
 * Ch 1 — pad ambiant (Synth Strings GM 50 → Pad Sweep GM 95)
 * Ch 2 — basse (Synth Bass 2 GM 39 → Synth Bass 1 GM 38)
 * Ch 9 — percussions GM
 */
class StarsWarProceduralMusic(private val scope: CoroutineScope) {

    private data class Theme(
        val label:        String,
        val progMel:      Int,
        val progPad:      Int,
        val progBass:     Int,
        val root:         Int,
        val scale:        IntArray,
        val bpm:          Int,
        val density:      Float,
        val padVol:       Int,
        val percIntensity: Float
    )

    companion object {
        private const val TAG = "StarsWarMusic"

        // Lydienne : le #4 donne l'émerveillement de l'espace infini (Star Wars, Interstellar)
        private val LYDIAN         = intArrayOf(0, 2, 4, 6, 7, 9, 11)
        // Pentatonique mineure : mystère, menace sourde sans agressivité
        private val PENTATONIC_MIN = intArrayOf(0, 3, 5, 7, 10)
        // Dorienne : tension croissante, ambiguïté majeur/mineur
        private val DORIAN         = intArrayOf(0, 2, 3, 5, 7, 9, 10)
        // Mineure harmonique : drama oriental, montée de danger
        private val HARMONIC_MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 11)
        // Locrienne : dissonance maximale, chaos, apocalypse
        private val LOCRIAN        = intArrayOf(0, 1, 3, 5, 6, 8, 10)

        private val THEMES = listOf(
            // vagues 1-4 : espace profond, dérive silencieuse — Lydien + strings doux
            Theme("Dérive",
                progMel = 48, progPad = 50, progBass = 39,
                root = 60, scale = LYDIAN,
                bpm = 68, density = 0.22f, padVol = 55, percIntensity = 0.18f),
            // vagues 5-9 : secteur inconnu, tension naissante — Pentatonique mineure
            Theme("Secteur",
                progMel = 48, progPad = 50, progBass = 39,
                root = 57, scale = PENTATONIC_MIN,
                bpm = 88, density = 0.30f, padVol = 62, percIntensity = 0.28f),
            // vagues 10-14 : alerte rouge, ennemis détectés — Dorienne
            Theme("Alerte",
                progMel = 80, progPad = 50, progBass = 39,
                root = 55, scale = DORIAN,
                bpm = 108, density = 0.38f, padVol = 68, percIntensity = 0.42f),
            // vagues 15-19 : invasion en cours — Mineure harmonique
            Theme("Invasion",
                progMel = 80, progPad = 95, progBass = 38,
                root = 53, scale = HARMONIC_MINOR,
                bpm = 122, density = 0.47f, padVol = 72, percIntensity = 0.58f),
            // vagues 20+ : apocalypse totale — Locrienne
            Theme("Apocalypse",
                progMel = 81, progPad = 95, progBass = 38,
                root = 50, scale = LOCRIAN,
                bpm = 138, density = 0.55f, padVol = 78, percIntensity = 0.72f),
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
            delay(300)
            initChannels(newTheme)
            musicLoop(newTheme)
        }
    }

    private fun initChannels(t: Theme) {
        programChange(0, t.progMel)
        programChange(1, t.progPad)
        programChange(2, t.progBass)
        controlChange(0, 7, 95)
        controlChange(1, 7, t.padVol)
        controlChange(2, 7, 88)
        controlChange(9, 7, 100)
    }

    private suspend fun musicLoop(theme: Theme) {
        val tickMs  = 60_000L / theme.bpm / 4
        var cycle   = 0
        var rootDeg = 0
        var density = theme.density
        var motif   = generateMotif(theme.scale.size)

        while (currentCoroutineContext().isActive) {
            // Renouvellement du motif toutes les 8-16 mesures
            if (cycle > 0 && cycle % Random.nextInt(8, 17) == 0) {
                motif   = generateMotif(theme.scale.size)
                rootDeg = (rootDeg + Random.nextInt(1, theme.scale.size / 2 + 1)) % theme.scale.size
                density = (density + Random.nextFloat() * 0.10f - 0.05f)
                    .coerceIn(theme.density * 0.65f, theme.density * 1.45f)
            }
            playBar(theme, tickMs, rootDeg, density, motif, cycle)
            cycle++
        }
    }

    // Génère un motif de 4 degrés qui commence et finit proche de la tonique
    private fun generateMotif(scaleSize: Int): IntArray {
        val pivot = Random.nextInt(1, scaleSize)
        return intArrayOf(0, pivot, (pivot + Random.nextInt(1, scaleSize)) % scaleSize, 0)
    }

    private suspend fun playBar(
        theme: Theme, tickMs: Long,
        rootDeg: Int, density: Float,
        motif: IntArray, cycle: Int
    ) {
        val bassHits   = euclidean(if (cycle % 4 < 2) 3 else 2, 8)
        var melNote    = -1
        var bassNote   = -1
        var padNote    = -1
        var padNote2   = -1

        // Pad ambiant tenu sur toute la mesure : fondamentale + quinte
        val padDeg = (rootDeg + motif[0]) % theme.scale.size
        val pn = theme.root + theme.scale[padDeg]
        noteOn(1, pn.coerceIn(36, 84), (theme.padVol * 0.75f).toInt())
        noteOn(1, (pn + 7).coerceIn(36, 96), (theme.padVol * 0.50f).toInt())
        padNote  = pn.coerceIn(36, 84)
        padNote2 = (pn + 7).coerceIn(36, 96)

        for (step in 0 until 16) {
            currentCoroutineContext().ensureActive()
            val t0 = System.currentTimeMillis()

            if (melNote  >= 0) { noteOff(0, melNote);  melNote  = -1 }
            if (bassNote >= 0) { noteOff(2, bassNote); bassNote = -1 }

            playPercussion(step, theme.percIntensity, cycle)

            // Basse 2 octaves sous la tonique — ancre grave et spatiale
            if (step % 2 == 0 && bassHits[step / 2] == 1) {
                val bDeg = (rootDeg + if (step == 0) 0 else Random.nextInt(2)) % theme.scale.size
                val bn   = (theme.root - 24 + theme.scale[bDeg]).coerceAtLeast(24)
                noteOn(2, bn, 88)
                bassNote = bn
            }

            // Mélodie basée sur le motif : accent fort sur les temps, léger sur les contretemps
            val motifDeg  = motif[(step / 4 + cycle % motif.size) % motif.size]
            val melDeg    = (rootDeg + motifDeg) % theme.scale.size
            val melThresh = when (step) {
                0          -> density * 1.4f
                4, 8, 12   -> density
                else       -> density * 0.55f
            }
            if (Random.nextFloat() < melThresh) {
                val oct = when {
                    Random.nextFloat() < 0.07f -> 12
                    Random.nextFloat() < 0.12f -> -12
                    else -> 0
                }
                val mn = (theme.root + theme.scale[melDeg] + oct).coerceIn(36, 96)
                noteOn(0, mn, Random.nextInt(62, 102))
                melNote = mn
            }

            val elapsed = System.currentTimeMillis() - t0
            delay((tickMs - elapsed).coerceAtLeast(1L))
        }

        if (melNote  >= 0) noteOff(0, melNote)
        if (bassNote >= 0) noteOff(2, bassNote)
        if (padNote  >= 0) noteOff(1, padNote)
        if (padNote2 >= 0) noteOff(1, padNote2)
    }

    private fun playPercussion(step: Int, intensity: Float, cycle: Int) {
        val bigCycle = cycle % 8 == 0
        when (step) {
            0 -> {
                perc(36, if (bigCycle) 100 else 88)
                perc(42, 32)
            }
            4 -> {
                perc(38, (72 * intensity).toInt().coerceAtLeast(18))
                perc(42, 28)
            }
            8 -> {
                perc(36, 80)
                perc(42, 32)
            }
            12 -> {
                perc(38, (66 * intensity).toInt().coerceAtLeast(18))
                if (Random.nextFloat() < intensity * 0.55f) perc(46, 50)
            }
            2, 6, 10, 14 -> if (Random.nextFloat() < intensity * 0.38f) perc(42, 18)
            else          -> if (Random.nextFloat() < intensity * 0.16f) perc(42, 12)
        }
        // Crash cymbal au début des grands cycles si tension suffisante
        if (step == 0 && bigCycle && intensity > 0.35f) {
            perc(49, (intensity * 75).toInt())
        }
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
        for (ch in 0..3) controlChange(ch, 123, 0)
    }
}
