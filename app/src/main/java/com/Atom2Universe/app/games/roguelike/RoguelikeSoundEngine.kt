package com.Atom2Universe.app.games.roguelike

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.billthefarmer.mididriver.MidiDriver

/**
 * Sons de combat pour le Donjon via Sonivox EAS (channel 9 = percussions GM).
 * Possède le cycle de vie du MidiDriver (start/stop). Musique coupée, le moteur (Oboe + EAS)
 * ne tourne que le temps d'un combat, ou d'un bruitage isolé, puis s'éteint après un silence.
 */
internal class RoguelikeSoundEngine(private val scope: CoroutineScope) {

    private var driver: MidiDriver? = null
    private var ready = false
    /** L'écran est au premier plan (entre start et stop). */
    private var foreground = false
    /** La musique joue ou un combat est affiché : le moteur reste allumé. */
    private var needed = false
    private var idleStop: Job? = null

    companion object {
        private const val TAG  = "RoguelikeSFX"
        private const val CH9: Byte = 0x99.toByte()
        /** Silence après lequel un moteur dont personne n'a besoin s'éteint. */
        private const val IDLE_STOP_MS = 3_000L
    }

    fun start() {
        foreground = true
        if (needed) boot()
    }

    fun stop() {
        foreground = false
        shutdown()
    }

    /** Musique active ou combat affiché : le moteur doit être prêt sans délai. */
    fun setNeeded(value: Boolean) {
        needed = value
        if (!foreground) return
        if (value) boot() else scheduleIdleStop()
    }

    private fun boot() {
        idleStop?.cancel()
        idleStop = null
        // Jamais deux init EAS de suite : le pilote est un singleton natif
        if (driver != null) return
        val d = MidiDriver.getInstance {
            ready = true
            Log.d(TAG, "EAS prêt")
        }
        driver = d
        d.start()
        Log.d(TAG, "start()")
    }

    private fun shutdown() {
        idleStop?.cancel()
        idleStop = null
        if (driver == null) return
        Log.d(TAG, "stop()")
        ready = false
        driver?.stop()
        driver = null
    }

    private fun scheduleIdleStop() {
        if (driver == null || needed || idleStop != null) return
        idleStop = scope.launch {
            delay(IDLE_STOP_MS)
            idleStop = null
            shutdown()
        }
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

    /** Bloc de bois clair : parade réussie (deux coups si parfaite). */
    fun onParry(perfect: Boolean) {
        note(76, 110)   // Hi Wood Block
        if (perfect) scope.launch { delay(70); note(76, 90) }
    }

    /** Stab descendant : joueur descend un étage. */
    fun onDescend() {
        Log.d(TAG, "onDescend")
        note(49, 100)
        scope.launch { delay(180); note(57, 80) }
    }

    // ── Bas niveau ───────────────────────────────────────────────────────────

    private fun note(pitch: Int, velocity: Int) {
        // Un bruitage hors combat, musique coupée (la descente) : on rallume le temps du son
        if (driver == null && foreground) boot()
        if (!ready) { Log.w(TAG, "note() ignoré — pas prêt"); return }
        driver?.queueEvent(byteArrayOf(CH9, pitch.toByte(), velocity.toByte()))
        // Le silence se compte depuis le dernier son
        idleStop?.cancel()
        idleStop = null
        scheduleIdleStop()
    }
}
