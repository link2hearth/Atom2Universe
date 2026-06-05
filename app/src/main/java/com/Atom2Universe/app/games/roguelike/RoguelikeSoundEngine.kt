package com.Atom2Universe.app.games.roguelike

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.billthefarmer.mididriver.MidiDriver

/**
 * Sons de combat pour le Donjon via Sonivox EAS (channel 9 = percussions GM).
 * Possède le cycle de vie du MidiDriver (start/stop).
 */
internal class RoguelikeSoundEngine(private val scope: CoroutineScope) {

    private var driver: MidiDriver? = null
    private var ready = false

    companion object {
        private const val TAG  = "RoguelikeSFX"
        private const val CH9: Byte = 0x99.toByte()
    }

    fun start() {
        val d = MidiDriver.getInstance {
            ready = true
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

    // ── Sons de combat ───────────────────────────────────────────────────────

    /** Clac sec : joueur frappe un monstre. */
    fun onPlayerAttack(isCrit: Boolean) {
        Log.d(TAG, "onPlayerAttack crit=$isCrit ready=$ready")
        note(37, if (isCrit) 115 else 85)   // Side Stick — clac
        if (isCrit) scope.launch { delay(60); note(39, 90) }   // Hand Clap en écho
    }

    /** Monstre mort. */
    fun onMonsterDied() {
        Log.d(TAG, "onMonsterDied ready=$ready")
        note(36, 100)   // Bass Drum 1
    }

    /** Boum grave : joueur reçoit des dégâts. */
    fun onPlayerHit() {
        Log.d(TAG, "onPlayerHit ready=$ready")
        note(35, 127)   // Acoustic Bass Drum — très grave
        scope.launch { delay(55); note(35, 90) }
    }

    /** Stab descendant : joueur descend un étage. */
    fun onDescend() {
        Log.d(TAG, "onDescend")
        note(49, 100)
        scope.launch { delay(180); note(57, 80) }
    }

    // ── Bas niveau ───────────────────────────────────────────────────────────

    private fun note(pitch: Int, velocity: Int) {
        if (!ready) { Log.w(TAG, "note() ignoré — pas prêt"); return }
        driver?.queueEvent(byteArrayOf(CH9, pitch.toByte(), velocity.toByte()))
    }
}
