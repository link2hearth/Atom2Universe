package com.Atom2Universe.app.games.particules

import kotlinx.coroutines.*
import org.billthefarmer.mididriver.MidiDriver
import kotlin.math.min
import kotlin.random.Random

/**
 * Bande son procédurale pour Particules (casse-briques).
 *
 * Ch 0 — mélodie de fond (Vibraphone GM 11 → Lead Square GM 80 → Sawtooth GM 81)
 * Ch 1 — basse de fond   (Synth Bass 2 GM 39)
 * Ch 2 — événements      (Lead Square GM 80)
 * Ch 9 — percussions GM
 *
 * Appeler [start] dans onCreate, [stop] dans onDestroy.
 * Appeler [pause] / [resumeBackground] dans onPause / onResume.
 */
class ParticulesMusic(private val scope: CoroutineScope) {

    companion object {
        // Gammes — progressent avec le niveau
        private val PENTA_MIN = intArrayOf(0, 3, 5, 7, 10)       // niv 1-5  : mystérieux
        private val DORIAN    = intArrayOf(0, 2, 3, 5, 7, 9, 10)  // niv 6-10 : tension
        private val PHRYGIAN  = intArrayOf(0, 1, 3, 5, 7, 8, 10)  // niv 11+  : intense
        private const val ROOT = 60  // Do4
    }

    private var driver: MidiDriver? = null
    private var ready = false
    private var bgJob: Job? = null
    private var currentLevel = 1

    // ── Cycle de vie ──────────────────────────────────────────────

    fun start() {
        val d = MidiDriver.getInstance {
            ready = true
            programChange(0, 11)   // Vibraphone
            programChange(1, 39)   // Synth Bass 2
            programChange(2, 80)   // Lead Square
            controlChange(0, 7, 82)
            controlChange(1, 7, 72)
            controlChange(2, 7, 105)
            controlChange(9, 7, 88)
        }
        driver = d
        d.start()
    }

    fun stop() {
        bgJob?.cancel(); bgJob = null
        ready = false
        driver?.stop(); driver = null
    }

    fun pause() {
        bgJob?.cancel(); bgJob = null
    }

    fun resumeBackground() {
        if (ready && bgJob?.isActive != true) startBgLoop(currentLevel)
    }

    // ── Mélodie de fond ───────────────────────────────────────────

    fun onLevelChanged(level: Int) {
        currentLevel = level
        bgJob?.cancel()
        if (!ready) return
        // Instrument mélodie s'intensifie avec les niveaux
        when {
            level <= 5  -> programChange(0, 11)   // Vibraphone
            level <= 10 -> programChange(0, 80)   // Lead Square
            else        -> programChange(0, 81)   // Lead Sawtooth
        }
        startBgLoop(level)
    }

    private fun startBgLoop(level: Int) {
        val scale = when {
            level <= 5  -> PENTA_MIN
            level <= 10 -> DORIAN
            else        -> PHRYGIAN
        }
        val bpm    = (90 + min(level - 1, 18) * 2).coerceAtMost(126)
        val tickMs = 60_000L / bpm / 4

        bgJob = scope.launch {
            delay(200)
            var step    = 0
            var motifPos = 0
            var motif   = newMotif(scale.size)

            while (currentCoroutineContext().isActive) {
                if (!ready) { delay(200); continue }
                val t0 = System.currentTimeMillis()

                val beat16 = step % 16
                val beat32 = step % 32

                // Mélodie
                val playMel = when {
                    beat16 == 0            -> true
                    beat16 == 8            -> Random.nextFloat() < 0.75f
                    beat16 % 4 == 0        -> Random.nextFloat() < 0.40f
                    else                   -> Random.nextFloat() < 0.15f
                }
                if (playMel) {
                    val deg  = motif[motifPos % motif.size]
                    val oct  = if (Random.nextFloat() < 0.10f) 12 else 0
                    val note = (ROOT + scale[deg] + oct).coerceIn(36, 96)
                    val vel  = if (beat16 == 0) 80 else (50 + Random.nextInt(22))
                    noteOnOff(0, note, vel, tickMs * 3)
                    motifPos++
                }

                // Basse sur les temps forts
                if (beat16 == 0) noteOnOff(1, (ROOT - 12 + scale[0]).coerceAtLeast(24), 78, tickMs * 7)
                if (beat16 == 8) noteOnOff(1, (ROOT - 12 + scale[scale.size / 2]).coerceAtLeast(24), 62, tickMs * 3)

                // Percussions légères
                when (beat16) {
                    0       -> perc(35, 72)
                    8       -> perc(35, 62)
                    4, 12   -> if (Random.nextFloat() < 0.65f) perc(38, 50)
                    2, 6, 10, 14 -> if (Random.nextFloat() < 0.22f) perc(42, 26)
                }

                // Nouveau motif tous les 32 pas
                if (step > 0 && beat32 == 0) { motif = newMotif(scale.size); motifPos = 0 }

                step++
                val elapsed = System.currentTimeMillis() - t0
                delay((tickMs - elapsed).coerceAtLeast(1L))
            }
        }
    }

    private fun newMotif(scaleSize: Int) = IntArray(8) { i ->
        if (i == 0 || i == 7) 0 else Random.nextInt(scaleSize)
    }

    // ── Sons d'événements ─────────────────────────────────────────

    /** "Tic" quand une brique résiste (reste en vie) */
    fun onBrickHit() {
        if (!ready) return
        noteOnOff(2, 48, 32, 22)
    }

    /** Note selon le type et le niveau quand une brique est détruite */
    fun onBrickDestroyed(type: ParticulesView.BType, level: Int) {
        if (!ready) return
        val note = when (type) {
            ParticulesView.BType.SIMPLE         -> (60 + (level % 12)).coerceIn(48, 84)
            ParticulesView.BType.RESISTANT      -> 52
            ParticulesView.BType.BONUS          -> 72
            ParticulesView.BType.EXPLOSIVE      -> 43
            ParticulesView.BType.ICE            -> 79
            ParticulesView.BType.REGEN          -> 65
            ParticulesView.BType.INDESTRUCTIBLE -> return
        }
        noteOnOff(2, note, 60 + Random.nextInt(22), 85)
        if (type == ParticulesView.BType.EXPLOSIVE) perc(36, 92)
    }

    /** Arpège quand un palier de combo est atteint (5 / 10 / 15) */
    fun onCombo(n: Int) {
        if (!ready) return
        val base = when {
            n >= 15 -> 60
            n >= 10 -> 64
            else    -> 67
        }
        noteOnOff(2, base,      100, 75)
        noteDelayed(2, base + 4,  105, 85,  90)
        noteDelayed(2, base + 7,  112, 100, 180)
        if (n >= 10) noteDelayed(2, base + 12, 115, 130, 270)
        if (n >= 15) { noteDelayed(2, base + 16, 120, 180, 360); percDelayed(49, 88, 350) }
    }

    /** Fanfare de niveau terminé */
    fun onLevelClear() {
        if (!ready) return
        noteOnOff(2, 60, 98, 110)
        noteDelayed(2, 64, 104, 125, 125)
        noteDelayed(2, 67, 110, 155, 255)
        noteDelayed(2, 72, 118, 480, 395)
        percDelayed(49, 78, 380)
    }

    /** Glissando descendant sur vie perdue */
    fun onLifeLost() {
        if (!ready) return
        noteOnOff(2, 67, 82, 210)
        noteDelayed(2, 65, 72, 225, 210)
        noteDelayed(2, 62, 62, 240, 430)
        noteDelayed(2, 60, 52, 290, 650)
        perc(35, 78)
    }

    /** Mélodie de game over */
    fun onGameOver() {
        if (!ready) return
        noteOnOff(2, 60, 88, 260)
        noteDelayed(2, 58, 82, 275, 290)
        noteDelayed(2, 55, 76, 305, 600)
        noteDelayed(2, 53, 70, 350, 920)
        noteDelayed(2, 48, 64, 580, 1300)
        percDelayed(36, 98, 25)
        percDelayed(38, 65, 310)
    }

    /** Son de power-up ramassé */
    fun onPowerUp(type: ParticulesView.PType) {
        if (!ready) return
        val note = when (type) {
            ParticulesView.PType.MULTIBALL -> 79
            ParticulesView.PType.LASER     -> 76
            ParticulesView.PType.SHIELD    -> 74
            ParticulesView.PType.SPEED     -> 81
            ParticulesView.PType.EXTEND    -> 72
            else                           -> 71
        }
        noteOnOff(2, note, 90, 95)
    }

    // ── Bas niveau ────────────────────────────────────────────────

    private fun perc(note: Int, vel: Int) =
        driver?.queueEvent(byteArrayOf(0x99.toByte(), note.toByte(), vel.toByte()))

    private fun percDelayed(note: Int, vel: Int, delayMs: Long) = Thread {
        try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
        if (ready) perc(note, vel)
    }.start()

    private fun noteOnOff(ch: Int, note: Int, vel: Int, durationMs: Long) {
        val d = driver ?: return
        d.queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
        Thread {
            try { Thread.sleep(durationMs) } catch (_: InterruptedException) {}
            if (ready) d.queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
        }.start()
    }

    private fun noteDelayed(ch: Int, note: Int, vel: Int, durationMs: Long, delayMs: Long) {
        val d = driver ?: return
        Thread {
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            if (!ready) return@Thread
            d.queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
            try { Thread.sleep(durationMs) } catch (_: InterruptedException) {}
            if (ready) d.queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
        }.start()
    }

    private fun programChange(ch: Int, prog: Int) =
        driver?.queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))

    private fun controlChange(ch: Int, cc: Int, value: Int) =
        driver?.queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
}
