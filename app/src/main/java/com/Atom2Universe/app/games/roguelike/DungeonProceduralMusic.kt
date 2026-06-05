package com.Atom2Universe.app.games.roguelike

import android.util.Log
import kotlinx.coroutines.*
import org.billthefarmer.mididriver.MidiDriver
import kotlin.random.Random

/**
 * Musique procédurale de donjon via Sonivox EAS.
 *
 * Un thème différent par palier d'étages (instruments + gamme + BPM).
 * Appeler [onFloorChanged] à chaque descente pour changer de thème.
 *
 * Deux voix mélodiques uniquement (polyphonie EAS limitée) :
 *   Ch 0 — mélodie
 *   Ch 1 — basse
 *   Ch 9 — percussions GM
 *
 * Les noteOff sont gérés séquentiellement dans la boucle (pas de scope.launch),
 * ce qui évite les bursts MIDI responsables du grésil.
 */
class DungeonProceduralMusic(private val scope: CoroutineScope) {

    // ── Thèmes ────────────────────────────────────────────────────────────────

    private data class Theme(
        val label:    String,
        val progMel:  Int,          // programme GM ch0
        val progBass: Int,          // programme GM ch1
        val root:     Int,          // note MIDI de base
        val scale:    IntArray,     // intervalles en demi-tons
        val bpm:      Int,
        val density:  Float         // densité mélodique initiale
    )

    companion object {
        private const val TAG = "DungeonMusic"

        // Gammes
        private val PHRYGIAN  = intArrayOf(0, 1, 3, 5, 7, 8, 10)  // sombre, modal
        private val NAT_MINOR = intArrayOf(0, 2, 3, 5, 7, 8, 10)  // mineur naturel
        private val LOCRIAN   = intArrayOf(0, 1, 3, 5, 6, 8, 10)  // très sombre
        private val DIMINISH  = intArrayOf(0, 2, 3, 5, 6, 8, 9)   // dissonant

        // Thèmes — sélectionnés par floor
        private val THEMES = listOf(
            // Étage 1-2 : entrée du donjon — cristallin, doux
            Theme("Entrée",     progMel = 9,  progBass = 12, root = 62, scale = PHRYGIAN,  bpm = 90,  density = 0.38f),
            // Étage 3-4 : cryptes — boîte à musique, mystérieux
            Theme("Cryptes",    progMel = 10, progBass = 45, root = 60, scale = NAT_MINOR, bpm = 80,  density = 0.32f),
            // Étage 5-6 : catacombes — xylophone, plus nerveux
            Theme("Catacombes", progMel = 13, progBass = 33, root = 64, scale = PHRYGIAN,  bpm = 105, density = 0.45f),
            // Étage 7-8 : abîme — cloches tubulaires, lent et solennel
            Theme("Abîme",      progMel = 14, progBass = 43, root = 59, scale = LOCRIAN,   bpm = 72,  density = 0.28f),
            // Étage 9+ : profondeurs — vibraphone, dissonant et inquiétant
            Theme("Profondeurs",progMel = 11, progBass = 43, root = 57, scale = DIMINISH,  bpm = 88,  density = 0.35f),
        )

        private fun themeFor(floor: Int): Theme = when (floor) {
            1, 2 -> THEMES[0]
            3, 4 -> THEMES[1]
            5, 6 -> THEMES[2]
            7, 8 -> THEMES[3]
            else -> THEMES[4]
        }
    }

    // ── État ──────────────────────────────────────────────────────────────────

    private var musicJob: Job? = null
    private var currentFloor  = 1

    // ── Cycle de vie ─────────────────────────────────────────────────────────

    fun start(floor: Int = currentFloor) {
        currentFloor = floor
        musicJob?.cancel()
        Log.d(TAG, "start() floor=$floor thème=${themeFor(floor).label}")
        musicJob = scope.launch {
            delay(500)
            val theme = themeFor(currentFloor)
            Log.d(TAG, "EAS prêt — thème ${theme.label} bpm=${theme.bpm}")
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

    /** Appelé quand le joueur descend au nouvel étage [floor]. */
    fun onFloorChanged(floor: Int) {
        val newTheme = themeFor(floor)
        val oldTheme = themeFor(currentFloor)
        currentFloor = floor
        Log.d(TAG, "floorChanged floor=$floor thème=${newTheme.label}")
        if (newTheme.label == oldTheme.label) return   // même palier, pas de changement
        // Redémarre la boucle avec le nouveau thème (le stop/start nettoie proprement)
        musicJob?.cancel()
        musicJob = scope.launch {
            allNotesOff()
            delay(200)   // petite pause de transition
            initChannels(newTheme)
            musicLoop(newTheme)
        }
    }

    // ── Init canaux ──────────────────────────────────────────────────────────

    private fun initChannels(t: Theme) {
        Log.d(TAG, "initChannels mel=${t.progMel} bass=${t.progBass}")
        programChange(0, t.progMel)
        programChange(1, t.progBass)
        controlChange(0, 7, 100)
        controlChange(1, 7, 92)
    }

    // ── Boucle musicale ──────────────────────────────────────────────────────

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
                Log.d(TAG, "mutation cycle=$cycle rootDeg=$rootDeg density=%.2f".format(density))
            }
            playBar(theme, tickMs, rootDeg, density)
            cycle++
        }
    }

    // ── Mesure (16 doubles croches) ──────────────────────────────────────────

    private suspend fun playBar(theme: Theme, tickMs: Long, rootDeg: Int, density: Float) {
        val bassHits = euclidean(3, 8)
        var melNote  = -1
        var bassNote = -1

        for (step in 0 until 16) {
            currentCoroutineContext().ensureActive()
            val t0 = System.currentTimeMillis()

            // noteOff des notes précédentes
            if (melNote  >= 0) { noteOff(0, melNote);  melNote  = -1 }
            if (bassNote >= 0) { noteOff(1, bassNote); bassNote = -1 }

            // Percussions
            when (step) {
                0     -> { perc(36, 82);  perc(42, 38) }
                4, 12 ->   perc(42, 32)
                8     -> { perc(38, 72);  perc(42, 38) }
                2, 6, 10, 14 -> if (Random.nextFloat() < 0.22f) perc(42, 22)
            }

            // Basse euclidienne (temps pairs uniquement)
            if (step % 2 == 0 && bassHits[step / 2] == 1) {
                val n = theme.root - 12 + theme.scale[rootDeg]
                noteOn(1, n, 78)
                bassNote = n
            }

            // Mélodie probabiliste
            if (Random.nextFloat() < density) {
                val deg = (rootDeg + Random.nextInt(theme.scale.size)) % theme.scale.size
                val oct = if (Random.nextFloat() < 0.15f) 12 else 0
                val n   = theme.root + theme.scale[deg] + oct
                noteOn(0, n, Random.nextInt(55, 92))
                melNote = n
            }

            val elapsed = System.currentTimeMillis() - t0
            delay((tickMs - elapsed).coerceAtLeast(1L))
        }

        if (melNote  >= 0) noteOff(0, melNote)
        if (bassNote >= 0) noteOff(1, bassNote)
    }

    // ── Euclidien (Bresenham) ─────────────────────────────────────────────────

    private fun euclidean(k: Int, n: Int): IntArray {
        val p = IntArray(n); var acc = 0
        for (i in 0 until n) { acc += k; if (acc >= n) { acc -= n; p[i] = 1 } }
        return p
    }

    // ── MIDI bas niveau ───────────────────────────────────────────────────────

    private fun d() = MidiDriver.getInstance()

    private fun noteOn(ch: Int, note: Int, vel: Int) {
        Log.v(TAG, "noteOn ch=$ch n=$note v=$vel")
        d().queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
    }

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
