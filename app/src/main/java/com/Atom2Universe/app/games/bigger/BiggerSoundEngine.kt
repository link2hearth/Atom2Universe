package com.Atom2Universe.app.games.bigger

import android.os.Handler
import android.os.HandlerThread
import org.billthefarmer.mididriver.MidiDriver

/**
 * Sons d'Accrétion, joués par le synthé MIDI Sonivox (même approche que le Collisionneur).
 * ch0 = célesta (fusions), ch1 = nappe grave (effondrements, fin), ch9 = percussions.
 *
 * **Plus l'objet est gros, plus sa note est grave** : on entend la masse. Une chaîne
 * ajoute l'octave au-dessus, de plus en plus fort.
 */
class BiggerSoundEngine {

    private var driver: MidiDriver? = null
    @Volatile private var ready = false
    private var timerThread: HandlerThread? = null
    private var timer: Handler? = null

    companion object {
        private const val CH0: Byte = 0x90.toByte()
        private const val CH1: Byte = 0x91.toByte()
        private const val CH9: Byte = 0x99.toByte()

        /** Pentatonique, de l'aigu (poussière) au grave (géante bleue). */
        private val SCALE = intArrayOf(88, 86, 84, 81, 79, 76, 74, 72, 69, 67, 64, 62)
    }

    fun start() {
        timerThread = HandlerThread("BiggerSfx").also { it.start() }
        timer = Handler(timerThread!!.looper)
        val d = MidiDriver.getInstance {
            ready = true
            programChange(0, 8)    // célesta
            programChange(1, 89)   // nappe « warm »
            controlChange(0, 7, 105)
            controlChange(1, 7, 95)
        }
        driver = d
        d.start()
    }

    fun stop() {
        ready = false
        timer?.removeCallbacksAndMessages(null)
        timerThread?.quitSafely()
        timerThread = null
        timer = null
        driver?.stop()
        driver = null
    }

    // ── Événements de jeu ────────────────────────────────────────────────────

    fun onDrop() = perc(76, 45)

    fun onMerge(tier: Int, chain: Int) {
        val pitch = SCALE[tier.coerceIn(0, SCALE.size - 1)]
        note(CH0, pitch, 95, 220)
        if (chain > 1) note(CH0, pitch + 12, (50 + chain * 8).coerceAtMost(110), 160, delayMs = 50)
    }

    fun onDiscovery() {
        note(CH0, 79, 90, 140)
        note(CH0, 84, 95, 140, delayMs = 110)
        note(CH0, 91, 100, 260, delayMs = 220)
    }

    fun onCollapse() {
        perc(52, 90)
        note(CH1, 36, 120, 900)
        note(CH1, 43, 100, 900, delayMs = 60)
    }

    fun onAnnihilation() {
        perc(49, 127)
        perc(57, 110)
        note(CH1, 31, 127, 1400)
        note(CH1, 38, 110, 1400)
        note(CH1, 43, 100, 1400)
        note(CH0, 72, 100, 120, delayMs = 150)
        note(CH0, 79, 100, 120, delayMs = 250)
        note(CH0, 84, 105, 120, delayMs = 350)
        note(CH0, 91, 110, 300, delayMs = 450)
    }

    /** Battement d'alerte quand le bocal va déborder. */
    fun onWarning() = perc(35, 85)

    fun onGameOver() {
        note(CH1, 55, 95, 300)
        note(CH1, 51, 90, 300, delayMs = 320)
        note(CH1, 46, 85, 700, delayMs = 640)
    }

    // ── Bas niveau ───────────────────────────────────────────────────────────

    private fun perc(pitch: Int, velocity: Int) {
        if (!ready) return
        driver?.queueEvent(byteArrayOf(CH9, pitch.toByte(), velocity.toByte()))
    }

    private fun note(ch: Byte, pitch: Int, velocity: Int, durationMs: Long, delayMs: Long = 0) {
        if (!ready) return
        val t = timer ?: return
        val off = (ch.toInt() and 0x0F or 0x80).toByte()
        val on = Runnable {
            if (ready) driver?.queueEvent(byteArrayOf(ch, pitch.toByte(), velocity.toByte()))
        }
        if (delayMs > 0) t.postDelayed(on, delayMs) else on.run()
        t.postDelayed({
            if (ready) driver?.queueEvent(byteArrayOf(off, pitch.toByte(), 0))
        }, delayMs + durationMs)
    }

    private fun programChange(ch: Int, prog: Int) {
        driver?.queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))
    }

    private fun controlChange(ch: Int, cc: Int, value: Int) {
        driver?.queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
    }
}
