package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.node.EventBus
import com.Atom2Universe.app.games.caves.node.GameEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.billthefarmer.mididriver.MidiDriver

/**
 * Sons de combat pour Cave World via Sonivox EAS (General MIDI channel 9 = percussions).
 *
 * Toutes les notes jouées sont des percussions GM — pas besoin de Note Off car
 * l'enveloppe s'arrête naturellement après le decay.
 *
 * Notes GM utilisées :
 *   35 = Acoustic Bass Drum  → ennemi détecte le joueur (sourd, grave)
 *   36 = Bass Drum 1         → ennemi boss détecte + mob mort
 *   38 = Acoustic Snare      → ennemi touché (sec, claquant)
 *   40 = Electric Snare      → boss touché
 *   49 = Crash Cymbal 1      → joueur touché
 *   57 = Crash Cymbal 2      → boss spawn (doublé avec 49)
 */
internal class CaveSoundEngine(private val scope: CoroutineScope) {

    private var driver: MidiDriver? = null
    private var ready = false

    // Cooldowns (ms) pour éviter le spam sonore sur tick rapide
    private var lastMobHitMs = 0L
    private var lastPlayerHitMs = 0L

    companion object {
        private const val CH9: Byte = 0x99.toByte()   // Note On, channel 9
        private const val MOB_HIT_COOLDOWN_MS = 120L
        private const val PLAYER_HIT_COOLDOWN_MS = 200L
    }

    fun start() {
        val d = MidiDriver.getInstance { ready = true }
        driver = d
        d.start()
    }

    fun stop() {
        ready = false
        driver?.stop()
        driver = null
    }

    fun subscribe(bus: EventBus) {
        bus.subscribe { event ->
            when (event) {
                is GameEvent.MobNearby  -> onMobNearby(event.isBoss)
                is GameEvent.MobHit     -> onMobHit(event.isBoss)
                is GameEvent.PlayerHit  -> onPlayerHit()
                is GameEvent.MobDied    -> onMobDied(event.isBoss)
                is GameEvent.BossSpawned -> onBossSpawned()
            }
        }
    }

    // ── Sons ─────────────────────────────────────────────────────────────────

    private fun onMobNearby(isBoss: Boolean) {
        if (isBoss) {
            // Grondement grave x2 légèrement décalé
            note(36, 70)
            scope.launch { delay(120); note(36, 55) }
        } else {
            note(35, 55)
        }
    }

    private fun onMobHit(isBoss: Boolean) {
        val now = System.currentTimeMillis()
        if (now - lastMobHitMs < MOB_HIT_COOLDOWN_MS) return
        lastMobHitMs = now
        note(if (isBoss) 40 else 38, if (isBoss) 100 else 80)
    }

    private fun onPlayerHit() {
        val now = System.currentTimeMillis()
        if (now - lastPlayerHitMs < PLAYER_HIT_COOLDOWN_MS) return
        lastPlayerHitMs = now
        note(49, 110)
    }

    private fun onMobDied(isBoss: Boolean) {
        if (isBoss) {
            note(36, 100)
            scope.launch { delay(80); note(49, 90); delay(120); note(57, 80) }
        } else {
            note(36, 70)
        }
    }

    private fun onBossSpawned() {
        note(49, 127)
        scope.launch { delay(150); note(57, 120) }
    }

    // ── Bas niveau ───────────────────────────────────────────────────────────

    private fun note(pitch: Int, velocity: Int) {
        if (!ready) return
        driver?.queueEvent(byteArrayOf(CH9, pitch.toByte(), velocity.toByte()))
    }
}
