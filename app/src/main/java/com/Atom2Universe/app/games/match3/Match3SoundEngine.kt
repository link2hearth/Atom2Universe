package com.Atom2Universe.app.games.match3

import org.billthefarmer.mididriver.MidiDriver
import kotlin.random.Random

/**
 * Sons procéduraux pour Match3 via Sonivox EAS.
 *
 * Ch 0 — mélodie principale (Glockenspiel GM 9)
 * Ch 1 — sous-note grave (Marimba GM 12)
 *
 * Appeler [start] dans onAttachedToWindow, [stop] dans onDetachedFromWindow.
 */
class Match3SoundEngine {

    companion object {
        // Pentatonique mineure de C5 : chaque gemme = une note
        // Argent=0 Bronze=1 Cuivre=2 Diamant=3 Or=4
        private val GEM_NOTES = intArrayOf(60, 63, 65, 67, 70) // C5 Eb5 F5 G5 Bb5

        // Montée pentatonique pour les combos (intervalles cumulés)
        private val COMBO_OFFSETS = intArrayOf(0, 5, 10, 12, 17, 19, 24)

        private const val CH_MAIN = 0
        private const val CH_SUB  = 1
    }

    private var driver: MidiDriver? = null
    private var ready = false

    fun start() {
        val d = MidiDriver.getInstance {
            ready = true
            programChange(CH_MAIN, 9)   // Glockenspiel
            programChange(CH_SUB,  12)  // Marimba
            controlChange(CH_MAIN, 7, 108)
            controlChange(CH_SUB,  7, 52)
        }
        driver = d
        d.start()
    }

    fun stop() {
        ready = false
        driver?.stop()
        driver = null
    }

    /**
     * Son de match.
     * @param gemType  0-4, détermine la note de base (couleur sonore de la gemme)
     * @param combo    0 = premier match, 1+ = cascade — la note monte dans la gamme
     */
    fun playMatch(gemType: Int, combo: Int) {
        if (!ready) return
        val base   = GEM_NOTES[gemType.coerceIn(0, GEM_NOTES.lastIndex)]
        val offset = COMBO_OFFSETS[combo.coerceIn(0, COMBO_OFFSETS.lastIndex)]
        val note   = (base + offset).coerceIn(36, 96)
        val vel    = (72 + combo * 7 + Random.nextInt(-4, 5)).coerceIn(58, 127)

        noteOnOff(CH_MAIN, note, vel, 180)

        // Quinte harmonique à partir du combo 2
        if (combo >= 2 && Random.nextFloat() < 0.55f) {
            noteOnOff(CH_MAIN, (note + 7).coerceIn(36, 96), (vel - 18).coerceAtLeast(30), 140)
        }

        // Note grave fantôme (Marimba) pour donner du corps
        noteOnOff(CH_SUB, (note - 12).coerceIn(24, 84), (vel * 0.42f).toInt(), 220)
    }

    // ── Bas niveau ────────────────────────────────────────────────────────────

    private fun noteOnOff(ch: Int, note: Int, vel: Int, durationMs: Long) {
        val d = driver ?: return
        d.queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
        Thread {
            try { Thread.sleep(durationMs) } catch (_: InterruptedException) {}
            if (ready) d.queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
        }.start()
    }

    private fun programChange(ch: Int, prog: Int) =
        driver?.queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))

    private fun controlChange(ch: Int, cc: Int, value: Int) =
        driver?.queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
}
