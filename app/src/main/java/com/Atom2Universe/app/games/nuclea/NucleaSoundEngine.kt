package com.Atom2Universe.app.games.nuclea

import android.util.Log
import org.billthefarmer.mididriver.MidiDriver

/**
 * SFX procéduraux via le driver MIDI Sonivox (même approche que StarsWar).
 * ch0 = glockenspiel (fusions, pickups), ch1 = pad synthé (événements graves),
 * ch9 = percussions.
 */
class NucleaSoundEngine {

    private var driver: MidiDriver? = null
    private var ready = false

    private var bounceCooldown = 0L
    private var pickupCooldown = 0L
    private var fusionCooldown = 0L

    companion object {
        private const val TAG = "NucleaSFX"
        private const val CH0: Byte = 0x90.toByte()   // note-on canal 0
        private const val CH1: Byte = 0x91.toByte()   // note-on canal 1
        private const val CH9: Byte = 0x99.toByte()   // percussions
        private const val BOUNCE_COOLDOWN_MS = 140L
        private const val PICKUP_COOLDOWN_MS = 90L
        private const val FUSION_COOLDOWN_MS = 70L
    }

    fun start() {
        val d = MidiDriver.getInstance {
            ready = true
            programChange(0, 9)    // ch0 : glockenspiel — blips cristallins
            programChange(1, 89)   // ch1 : pad « warm » — graves cosmiques
            controlChange(0, 7, 105)
            controlChange(1, 7, 95)
            Log.d(TAG, "EAS prêt")
        }
        driver = d
        d.start()
    }

    fun stop() {
        ready = false
        driver?.stop()
        driver = null
    }

    // ── Événements de jeu ────────────────────────────────────────────────────

    /** Blip de fusion : la note monte avec l'élément créé (gamme pentatonique). */
    fun onFusion(tier: Int) {
        val now = System.currentTimeMillis()
        if (now - fusionCooldown < FUSION_COOLDOWN_MS) return
        fusionCooldown = now
        val scale = intArrayOf(60, 62, 65, 67, 70, 72, 77)   // Do Ré Fa Sol Sib Do Fa
        val pitch = scale[tier.coerceIn(0, scale.size - 1)]
        noteOnOff(CH0, pitch, 95, 130)
        noteDelayed(CH0, pitch + 12, 60, delayMs = 70)
    }

    fun onBounce() {
        val now = System.currentTimeMillis()
        if (now - bounceCooldown < BOUNCE_COOLDOWN_MS) return
        bounceCooldown = now
        perc(37, 45)   // side stick très discret
    }

    fun onPickup() {
        val now = System.currentTimeMillis()
        if (now - pickupCooldown < PICKUP_COOLDOWN_MS) return
        pickupCooldown = now
        noteOnOff(CH0, 96, 45, 50)
    }

    fun onPowerUp() {
        noteOnOff(CH0, 79, 90, 100)
        noteDelayed(CH0, 84, 90, delayMs = 90)
        noteDelayed(CH0, 91, 85, delayMs = 180)
    }

    fun onPlayerHit() {
        perc(35, 127)   // grosse caisse
    }

    fun onShieldBreak() {
        perc(42, 100)
        noteOnOff(CH1, 55, 90, 200)
    }

    fun onAnnihilation() {
        perc(49, 110)                              // crash
        noteOnOff(CH1, 43, 100, 300)               // grave inquiétant
    }

    fun onSupernova() {
        perc(49, 127)
        noteDelayed(CH9, 57, 120, delayMs = 120)
        // Arpège ascendant triomphal
        noteOnOff(CH0, 60, 110, 150)
        noteDelayed(CH0, 67, 110, delayMs = 120)
        noteDelayed(CH0, 72, 115, delayMs = 240)
        noteDelayed(CH0, 79, 120, delayMs = 360)
    }

    fun onDecay() {
        noteOnOff(CH0, 54, 55, 80)
    }

    fun onBlackHole() {
        // Drone grave menaçant
        noteOnOff(CH1, 36, 110, 900)
        noteDelayed(CH1, 35, 100, delayMs = 500)
    }

    fun onFeed() {
        noteOnOff(CH1, 48, 65, 90)
    }

    fun onBossDefeated() {
        perc(49, 127)
        noteDelayed(CH9, 57, 120, delayMs = 150)
        noteOnOff(CH0, 65, 110, 160)
        noteDelayed(CH0, 72, 115, delayMs = 150)
        noteDelayed(CH0, 77, 120, delayMs = 300)
    }

    fun onWaveCleared() {
        noteOnOff(CH0, 67, 90, 140)
        noteDelayed(CH0, 74, 95, delayMs = 140)
    }

    fun onRevive() {
        noteOnOff(CH0, 72, 110, 200)
        noteDelayed(CH0, 79, 110, delayMs = 180)
        noteDelayed(CH0, 84, 115, delayMs = 360)
    }

    fun onGameOver() {
        noteOnOff(CH1, 55, 95, 280)
        noteDelayed(CH1, 51, 90, delayMs = 320)
        noteDelayed(CH1, 48, 85, delayMs = 640)
    }

    fun onBuy() {
        noteOnOff(CH0, 88, 80, 70)
        noteDelayed(CH0, 93, 75, delayMs = 70)
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
            if (ready) {
                driver?.queueEvent(byteArrayOf(ch, pitch.toByte(), velocity.toByte()))
                try { Thread.sleep(160) } catch (_: InterruptedException) {}
                val chOff = (ch.toInt() and 0x0F or 0x80).toByte()
                if (ready) driver?.queueEvent(byteArrayOf(chOff, pitch.toByte(), 0))
            }
        }.start()
    }

    private fun programChange(ch: Int, prog: Int) {
        driver?.queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))
    }

    private fun controlChange(ch: Int, cc: Int, value: Int) {
        driver?.queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
    }
}
