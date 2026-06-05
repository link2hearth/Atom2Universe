package com.Atom2Universe.app.games.starswar

import android.util.Log
import org.billthefarmer.mididriver.MidiDriver

internal class StarsWarSoundEngine {

    private var driver: MidiDriver? = null
    private var ready = false

    private var shotCooldown = 0L

    companion object {
        private const val TAG = "StarsWarSFX"
        private const val CH0: Byte = 0x90.toByte()
        private const val CH9: Byte = 0x99.toByte()
        private const val SHOT_COOLDOWN_MS = 150L
    }

    fun start() {
        val d = MidiDriver.getInstance {
            ready = true
            programChange(0, 80)   // ch0 utilisé pour game over / nouvelle vague
            controlChange(0, 7, 100)
            Log.d(TAG, "EAS prêt")
        }
        driver = d
        d.start()
        Log.d(TAG, "start()")
    }

    fun stop() {
        Log.d(TAG, "stop()")
        ready = false
        driver?.stop()
        driver = null
    }

    // ── Sons de combat ────────────────────────────────────────────────────────

    fun onPlayerShot() {
        val now = System.currentTimeMillis()
        if (now - shotCooldown < SHOT_COOLDOWN_MS) return
        shotCooldown = now
        noteOnOff(CH0, 84, 60, 70)   // Do6 — piou court et aigu
    }

    fun onEnemyDestroyed() {
        perc(36, 100)                              // Kick
        noteDelayed(CH9, 49, 80, delayMs = 60)     // Crash décalé
    }

    fun onBossDestroyed() {
        perc(49, 127)                              // Crash fort
        noteDelayed(CH9, 57, 110, delayMs = 150)   // Crash cymbal décalé
    }

    fun onPlayerHit() {
        perc(35, 127)   // Bass drum grave
    }

    fun onGameOver() {
        // Séquence descendante 3 notes : Sol4→Mi4→Do4, 300ms chacune
        noteOnOff(CH0, 67, 90, 280)
        noteDelayed(CH0, 64, 85, delayMs = 320)
        noteDelayed(CH0, 60, 80, delayMs = 640)
    }

    fun onNewWave() {
        perc(49, 90)
        noteOnOff(CH0, 72, 80, 200)   // Do5
    }

    fun onMeteorPhase() {
        perc(46, 100)
        noteDelayed(CH9, 46, 95, delayMs = 120)   // Open hi-hat x2 rapide
    }

    // ── Bas niveau ───────────────────────────────────────────────────────────

    private fun perc(pitch: Int, velocity: Int) {
        if (!ready) return
        driver?.queueEvent(byteArrayOf(CH9, pitch.toByte(), velocity.toByte()))
    }

    private fun noteOnOff(ch: Byte, pitch: Int, velocity: Int, durationMs: Long) {
        if (!ready) return
        val chOff = (ch.toInt() and 0x0F or 0x80).toByte()
        driver?.queueEvent(byteArrayOf(ch, pitch.toByte(), velocity.toByte()))
        Thread {
            try { Thread.sleep(durationMs) } catch (_: InterruptedException) {}
            if (ready) driver?.queueEvent(byteArrayOf(chOff, pitch.toByte(), 0))
        }.start()
    }

    private fun noteDelayed(ch: Byte, pitch: Int, velocity: Int, delayMs: Long) {
        if (!ready) return
        Thread {
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            if (ready) driver?.queueEvent(byteArrayOf(ch, pitch.toByte(), velocity.toByte()))
        }.start()
    }

    private fun programChange(ch: Int, prog: Int) {
        driver?.queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))
    }

    private fun controlChange(ch: Int, cc: Int, value: Int) {
        driver?.queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
    }
}
