package com.Atom2Universe.app.games.caves

import kotlinx.coroutines.*
import org.billthefarmer.mididriver.MidiDriver
import kotlin.random.Random

/**
 * Musique procédurale d'ambiance pour Cave World via Sonivox EAS.
 *
 * Cycle autonome : silence → épisode musical (pattern aléatoire) → silence → …
 * Tempo très lent, notes tenues longtemps, pas de percussions.
 *
 * Ch 0 — mélodie (Strings / Choir, très épars)
 * Ch 1 — pad ambiant tenu sur la mesure
 * Ch 2 — basse rare, grave
 */
class CaveProceduralMusic(private val scope: CoroutineScope) {

    private data class Theme(
        val label:       String,
        val progMel:     Int,
        val progPad:     Int,
        val progBass:    Int,
        val root:        Int,
        val scale:       IntArray,
        val bpm:         Int,
        val density:     Float,
        val padVol:      Int,
        val bassVol:     Int,
        val melVol:      IntRange,
        val episodeBars: IntRange
    )

    companion object {
        // Aeolienne (mineure naturelle) : sombre, mélancolique
        private val AEOLIAN       = intArrayOf(0, 2, 3, 5, 7, 8, 10)
        // Pentatonique mineure : mystère dépouillé
        private val PENTATONIC    = intArrayOf(0, 3, 5, 7, 10)
        // Phrygien : obscurité profonde, presque oppressante
        private val PHRYGIAN      = intArrayOf(0, 1, 3, 5, 7, 8, 10)
        // Gamme par tons entiers : flottant, irréel, désorientation
        private val WHOLE_TONE    = intArrayOf(0, 2, 4, 6, 8, 10)

        private val THEMES = listOf(
            // Silence profond : quasi rien, juste un pad très doux
            Theme("Silence profond",
                progMel = 48,  progPad = 91, progBass = 42,
                root = 48, scale = AEOLIAN,
                bpm = 42, density = 0.06f,
                padVol = 30, bassVol = 52, melVol = 36..55,
                episodeBars = 24..44),
            // Caverne : atmosphère de grotte, cordes, notes rares
            Theme("Caverne",
                progMel = 48,  progPad = 88, progBass = 43,
                root = 45, scale = AEOLIAN,
                bpm = 46, density = 0.12f,
                padVol = 50, bassVol = 68, melVol = 45..70,
                episodeBars = 32..56),
            // Abîme : phrygien, contrebasse profonde, choeur lointain
            Theme("Abîme",
                progMel = 52,  progPad = 95, progBass = 43,
                root = 44, scale = PHRYGIAN,
                bpm = 44, density = 0.09f,
                padVol = 42, bassVol = 72, melVol = 36..60,
                episodeBars = 28..50),
            // Mystère : pentatonique, légèrement plus de mouvement
            Theme("Mystère",
                progMel = 48,  progPad = 89, progBass = 42,
                root = 50, scale = PENTATONIC,
                bpm = 50, density = 0.14f,
                padVol = 55, bassVol = 60, melVol = 46..72,
                episodeBars = 32..60),
            // Éther : gamme par tons, très lent, sons de pad principalement
            Theme("Éther",
                progMel = 89,  progPad = 88, progBass = 42,
                root = 47, scale = WHOLE_TONE,
                bpm = 40, density = 0.07f,
                padVol = 38, bassVol = 48, melVol = 34..52,
                episodeBars = 20..40),
        )

        private const val SILENCE_MIN_MS = 60_000L    // 1 min
        private const val SILENCE_MAX_MS = 240_000L   // 4 min
        private const val INITIAL_MIN_MS = 10_000L
        private const val INITIAL_MAX_MS = 35_000L
    }

    private var cycleJob: Job? = null
    @Volatile private var active = false

    fun start() {
        active = true
        cycleJob?.cancel()
        cycleJob = scope.launch {
            delay(Random.nextLong(INITIAL_MIN_MS, INITIAL_MAX_MS))
            while (isActive) {
                val theme = THEMES.random()
                initChannels(theme)
                playEpisode(theme)
                if (!isActive) break
                delay(Random.nextLong(SILENCE_MIN_MS, SILENCE_MAX_MS))
            }
        }
    }

    fun stop() {
        active = false
        cycleJob?.cancel()
        cycleJob = null
    }

    fun pause() = stop()
    fun resume() = start()

    // ── Initialisation canaux ─────────────────────────────────────────────────

    private fun initChannels(t: Theme) {
        programChange(0, t.progMel)
        programChange(1, t.progPad)
        programChange(2, t.progBass)
        controlChange(0, 7, 80)
        controlChange(1, 7, t.padVol)
        controlChange(2, 7, t.bassVol)
        // Reverb élevée pour l'acoustique de grotte
        controlChange(0, 91, 92)
        controlChange(1, 91, 80)
        controlChange(2, 91, 64)
    }

    // ── Épisode musical ───────────────────────────────────────────────────────

    private suspend fun playEpisode(theme: Theme) {
        val tickMs    = 60_000L / theme.bpm / 4
        val totalBars = Random.nextInt(theme.episodeBars.first, theme.episodeBars.last + 1)
        var rootDeg   = 0
        var motif     = generateMotif(theme.scale.size)

        for (bar in 0 until totalBars) {
            currentCoroutineContext().ensureActive()
            if (bar > 0 && bar % Random.nextInt(8, 17) == 0) {
                motif   = generateMotif(theme.scale.size)
                rootDeg = (rootDeg + Random.nextInt(1, theme.scale.size / 2 + 1)) % theme.scale.size
            }
            playBar(theme, tickMs, rootDeg, motif, bar)
        }
    }

    // Motif de 4 degrés qui revient vers la tonique
    private fun generateMotif(scaleSize: Int): IntArray {
        val pivot = Random.nextInt(1, scaleSize)
        return intArrayOf(0, pivot, (pivot + Random.nextInt(1, scaleSize)) % scaleSize, 0)
    }

    // ── Mesure (16 steps = 4 temps) ───────────────────────────────────────────

    private suspend fun playBar(
        theme: Theme, tickMs: Long,
        rootDeg: Int, motif: IntArray, bar: Int
    ) {
        var melNote     = -1
        var melEndStep  = -1
        var bassNote    = -1
        var bassEndStep = -1
        var padNote     = -1
        var padNote2    = -1

        // Pad tenu sur toute la mesure : fondamentale + quinte
        val padDeg = (rootDeg + motif[0]) % theme.scale.size
        val pn = theme.root + theme.scale[padDeg]
        noteOn(1, pn.coerceIn(36, 84), (theme.padVol * 0.82f).toInt())
        noteOn(1, (pn + 7).coerceIn(36, 96), (theme.padVol * 0.52f).toInt())
        padNote  = pn.coerceIn(36, 84)
        padNote2 = (pn + 7).coerceIn(36, 96)

        for (step in 0 until 16) {
            currentCoroutineContext().ensureActive()
            val t0 = System.currentTimeMillis()

            if (melNote  >= 0 && step >= melEndStep)  { noteOff(0, melNote);  melNote  = -1 }
            if (bassNote >= 0 && step >= bassEndStep)  { noteOff(2, bassNote); bassNote = -1 }

            // Basse : sur les temps forts seulement, très rare
            if (bassNote < 0 && step % 4 == 0 && Random.nextFloat() < theme.density * 0.75f) {
                val bDeg = (rootDeg + motif[step / 4 % motif.size]) % theme.scale.size
                val bn   = (theme.root - 24 + theme.scale[bDeg]).coerceIn(24, 48)
                noteOn(2, bn, theme.bassVol)
                bassNote    = bn
                bassEndStep = step + Random.nextInt(4, 9)
            }

            // Mélodie : éparse, notes longues
            if (melNote < 0) {
                val motifDeg = motif[(step / 4 + bar % motif.size) % motif.size]
                val melDeg   = (rootDeg + motifDeg) % theme.scale.size
                val thresh   = when (step % 4) {
                    0    -> theme.density * 1.4f
                    2    -> theme.density * 0.9f
                    else -> theme.density * 0.35f
                }
                if (Random.nextFloat() < thresh) {
                    val oct = when {
                        Random.nextFloat() < 0.10f ->  12
                        Random.nextFloat() < 0.08f -> -12
                        else -> 0
                    }
                    val mn = (theme.root + theme.scale[melDeg] + oct).coerceIn(36, 84)
                    noteOn(0, mn, Random.nextInt(theme.melVol.first, theme.melVol.last + 1))
                    melNote    = mn
                    melEndStep = step + Random.nextInt(3, 7)
                }
            }

            val elapsed = System.currentTimeMillis() - t0
            delay((tickMs - elapsed).coerceAtLeast(1L))
        }

        if (melNote  >= 0) noteOff(0, melNote)
        if (bassNote >= 0) noteOff(2, bassNote)
        if (padNote  >= 0) noteOff(1, padNote)
        if (padNote2 >= 0) noteOff(1, padNote2)
    }

    // ── MIDI bas niveau ───────────────────────────────────────────────────────

    private fun d() = MidiDriver.getInstance()

    private fun noteOn(ch: Int, note: Int, vel: Int) {
        if (!active) return
        d().queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
    }

    private fun noteOff(ch: Int, note: Int) {
        if (!active) return
        d().queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
    }

    private fun programChange(ch: Int, prog: Int) {
        if (!active) return
        d().queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))
    }

    private fun controlChange(ch: Int, cc: Int, value: Int) {
        if (!active) return
        d().queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
    }
}
