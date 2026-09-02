package com.Atom2Universe.app.games.trebuchet

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.billthefarmer.mididriver.MidiDriver
import kotlin.random.Random

/**
 * L'ambiance sonore du trébuchet, via Sonivox EAS — même patron que
 * [com.Atom2Universe.app.games.match3.Match3SoundEngine] ou
 * [com.Atom2Universe.app.games.caves.CaveSoundEngine] : le moteur natif est
 * possédé en propre, démarré et arrêté avec la vue qui le porte.
 *
 * Pas de musique : seulement des bruitages ponctuels. Rien n'utilise de fichier
 * audio — tout sort de la banque General MIDI intégrée, y compris la banque
 * « Sound Effects » (programmes 121-128) qui fournit la détonation (Gunshot)
 * qu'aucun instrument ordinaire n'approche.
 */
class TrebuchetSfx {

    private companion object {
        const val CH_PERC = 9        // Percussion GM : marteau, explosion, étincelles.
        const val CH_CRACK = 1       // Program 127 — Gunshot.
        const val CH_BREATH = 2      // Program 121 — Breath Noise.
        const val CH_VOICE = 3       // Program 54 — Synth Voice.

        const val PITCH_CENTER = 8192
    }

    /** Coupe tous les bruitages sans arrêter le moteur — le réglage du menu. */
    var enabled = true

    private var driver: MidiDriver? = null
    private var ready = false
    private var scope: CoroutineScope? = null
    private val rng = Random(System.nanoTime())

    /** Les toms graves de la table de percussion : le registre d'un éboulement. */
    private val TOMS = intArrayOf(41, 43, 45, 47)

    fun start() {
        scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
        val d = MidiDriver.getInstance {
            ready = true
            programChange(CH_CRACK, 127)
            programChange(CH_BREATH, 121)
            programChange(CH_VOICE, 54)
        }
        driver = d
        d.start()
    }

    fun stop() {
        ready = false
        scope?.cancel()
        scope = null
        driver?.stop()
        driver = null
    }

    // ── Bruitages ────────────────────────────────────────────────────────────

    /** Un coup de bois sec : l'édition de la machine au marteau. */
    fun hammerTap() {
        if (!enabled) return
        val note = if (rng.nextBoolean()) 76 else 77
        note(CH_PERC, note, 70 + rng.nextInt(31))
    }

    /** Le cri d'un villageois qui déguerpit : une voix qui dégringole. */
    fun villagerCry() {
        if (!enabled) return
        val base = (74 + rng.nextInt(7) - 3).coerceIn(60, 84)
        val vel = 90 + rng.nextInt(28)
        note(CH_VOICE, base, vel)
        scope?.launch {
            var bend = PITCH_CENTER
            repeat(6) {
                delay(28L)
                bend -= 950
                pitchBend(CH_VOICE, bend.coerceAtLeast(0))
            }
            noteOff(CH_VOICE, base)
            pitchBend(CH_VOICE, PITCH_CENTER)
        }
    }

    /**
     * Une pierre qui cède : **un choc mat et grave, jamais une explosion**.
     *
     * La différence n'est pas cosmétique. Le premier essai employait [explosion] — grosse
     * caisse, cymbale crash et craquement — pour sonner une assise qui se brise, et le
     * joueur a cru à un bug de simulation : « le boulet est explosif on dirait, ça cogne
     * et ça explose contre le bois ». La physique était juste ; c'est le son qui
     * racontait autre chose.
     *
     * Ici, un tom grave tiré au sort et un craquement court. De la matière qui cède, pas
     * de la poudre.
     */
    fun rubble() {
        if (!enabled) return
        val tom = TOMS[rng.nextInt(TOMS.size)]
        note(CH_PERC, tom, 52 + rng.nextInt(34))
        noteOnOffDelayed(CH_CRACK, 46 + rng.nextInt(6), 40 + rng.nextInt(26), 130L)
    }

    /** Le boulet — ou la bombe — qui rend tout d'un coup. */
    fun explosion() {
        if (!enabled) return
        note(CH_PERC, 36, 127)   // Grosse caisse.
        note(CH_PERC, 49, 110)   // Cymbale crash.
        noteOnOffDelayed(CH_CRACK, 60, 120, 320L)
        scope?.launch { delay(60L); note(CH_PERC, 45, 90) }   // Traîne grave.
    }

    /** Une fusée qui part : un souffle qui monte. */
    fun fireworkLaunch() {
        if (!enabled) return
        val note = 58 + rng.nextInt(6)
        note(CH_BREATH, note, 55 + rng.nextInt(20))
        scope?.launch {
            var bend = PITCH_CENTER - 3000
            pitchBend(CH_BREATH, bend)
            repeat(5) {
                delay(30L)
                bend += 600
                pitchBend(CH_BREATH, bend.coerceAtMost(16383))
            }
            noteOff(CH_BREATH, note)
            pitchBend(CH_BREATH, PITCH_CENTER)
        }
    }

    /** Un bouquet qui éclate : un crac, et une étincelle métallique. */
    fun fireworkBurst() {
        if (!enabled) return
        noteOnOffDelayed(CH_CRACK, 60, 65 + rng.nextInt(30), 260L)
        val accent = if (rng.nextBoolean()) 81 else 57
        note(CH_PERC, accent, 55 + rng.nextInt(45))
    }

    // ── Bas niveau ───────────────────────────────────────────────────────────

    private fun noteOnOffDelayed(channel: Int, pitch: Int, velocity: Int, durationMs: Long) {
        note(channel, pitch, velocity)
        scope?.launch {
            delay(durationMs)
            noteOff(channel, pitch)
        }
    }

    private fun note(channel: Int, pitch: Int, velocity: Int) {
        if (!ready) return
        driver?.queueEvent(
            byteArrayOf(
                (0x90 or (channel and 0x0F)).toByte(),
                pitch.coerceIn(0, 127).toByte(),
                velocity.coerceIn(0, 127).toByte()
            )
        )
    }

    private fun noteOff(channel: Int, pitch: Int) {
        driver?.queueEvent(
            byteArrayOf(
                (0x80 or (channel and 0x0F)).toByte(),
                pitch.coerceIn(0, 127).toByte(),
                0
            )
        )
    }

    private fun programChange(channel: Int, program: Int) {
        driver?.queueEvent(
            byteArrayOf((0xC0 or (channel and 0x0F)).toByte(), program.coerceIn(0, 127).toByte())
        )
    }

    private fun pitchBend(channel: Int, value: Int) {
        val v = value.coerceIn(0, 16383)
        driver?.queueEvent(
            byteArrayOf(
                (0xE0 or (channel and 0x0F)).toByte(),
                (v and 0x7F).toByte(),
                ((v shr 7) and 0x7F).toByte()
            )
        )
    }
}
