package com.Atom2Universe.app.games.reflex

import android.os.Handler
import android.os.HandlerThread
import org.billthefarmer.mididriver.MidiDriver

/**
 * Sons du Collisionneur, joués par le synthé MIDI Sonivox (même approche que Nucléa).
 * ch0 = vibraphone (touches réussies), ch1 = pad grave (erreurs), ch9 = percussions.
 *
 * Les fins de notes passent par un seul fil dédié plutôt que par un fil par note :
 * un combo rapide en demande plusieurs par seconde.
 */
class ReflexSoundEngine {

    private var driver: MidiDriver? = null
    @Volatile private var ready = false
    private var timerThread: HandlerThread? = null
    private var timer: Handler? = null

    companion object {
        private const val CH0: Byte = 0x90.toByte()
        private const val CH1: Byte = 0x91.toByte()
        private const val CH9: Byte = 0x99.toByte()

        /** Gamme pentatonique : la note d'un PARFAIT monte avec le combo. */
        private val SCALE = intArrayOf(60, 62, 64, 67, 69, 72, 74, 76, 79, 81, 84, 86, 88)
    }

    fun start() {
        timerThread = HandlerThread("ReflexSfx").also { it.start() }
        timer = Handler(timerThread!!.looper)
        val d = MidiDriver.getInstance {
            ready = true
            programChange(0, 11)   // vibraphone
            programChange(1, 89)   // pad « warm »
            controlChange(0, 7, 110)
            controlChange(1, 7, 90)
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

    fun onHit(grade: HitGrade, combo: Int) {
        when (grade) {
            HitGrade.PERFECT -> {
                val pitch = SCALE[(combo / 2).coerceIn(0, SCALE.size - 1)]
                note(CH0, pitch, 105, 140)
                note(CH0, pitch + 12, 55, 90, delayMs = 45)
            }
            HitGrade.GOOD -> note(CH0, SCALE[(combo / 3).coerceIn(0, 6)], 80, 110)
            HitGrade.EARLY -> perc(42, 70)
        }
    }

    fun onCrack() = perc(75, 110)

    fun onMiss() {
        perc(38, 70)
        note(CH1, 43, 80, 180)
    }

    fun onAntiTouched() {
        perc(35, 127)
        perc(49, 90)
        note(CH1, 36, 115, 420)
    }

    fun onEmptyTap() = perc(37, 45)

    fun onWrongOrder() = perc(76, 90)

    fun onLifeRegained() {
        note(CH0, 79, 100, 120)
        note(CH0, 86, 100, 160, delayMs = 110)
    }

    fun onOverheatStart() {
        perc(49, 120)
        note(CH0, 72, 110, 120)
        note(CH0, 76, 110, 120, delayMs = 80)
        note(CH0, 79, 115, 120, delayMs = 160)
        note(CH0, 84, 120, 220, delayMs = 240)
    }

    fun onUnlock() {
        note(CH0, 84, 85, 120)
        note(CH0, 79, 70, 140, delayMs = 120)
    }

    fun onGameOver() {
        note(CH1, 55, 95, 280)
        note(CH1, 51, 90, 280, delayMs = 300)
        note(CH1, 48, 85, 600, delayMs = 600)
    }

    fun onButton() = note(CH0, 88, 70, 70)

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
