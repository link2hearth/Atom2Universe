package com.Atom2Universe.app.crypto.fusion

import com.Atom2Universe.app.crypto.sound.SharedSonivoxDriver

/**
 * Sons procéduraux pour la séquence de fusion via Sonivox EAS.
 *
 * Ch 0 — impacts (Orchestra Hit GM 55)
 * Ch 1 — fanfare (Trumpet GM 56)
 * Ch 2 — fail    (Tuba GM 58)
 * Ch 9 — percussions GM
 *
 * Séquence : playCountdownStep(0) → playCountdownStep(1) → playCountdownStep(2)
 *   puis  playWin() / playFail() / playSpecialCard() → playWin()
 *
 * Passe par [SharedSonivoxDriver] (comptage de références) : libérer le moteur
 * en quittant la fusion ne doit pas couper le son du clicker sous-jacent.
 */
class FusionSoundEngine {

    var muted = false

    private var acquired = false
    private var ready = false

    fun start() {
        if (acquired) return
        acquired = true
        SharedSonivoxDriver.acquire {
            ready = true
            programChange(0, 55)   // Orchestra Hit — coups percussifs du compte à rebours
            programChange(1, 56)   // Trumpet — fanfare victoire
            programChange(2, 58)   // Tuba — descente d'échec
            controlChange(0, 7, 118)
            controlChange(1, 7, 115)
            controlChange(2, 7, 108)
            controlChange(9, 7, 110)
        }
    }

    fun stop() {
        if (!acquired) return
        acquired = false
        ready = false
        SharedSonivoxDriver.release()
    }

    // ── Compte à rebours (3 splats) ────────────────────────────────────────────

    /** "Bim" — premier petit splash */
    /** "Chlack" — splash intermédiaire */
    /** "Boom" — splash final avant le résultat */
    fun playCountdownStep(step: Int) {
        if (!ready || muted) return
        when (step) {
            0 -> {
                // "Bim" : coup léger + hihat
                perc(36, 68)
                noteOnOff(0, 60, 72, 160)   // Do4 Orchestra Hit léger
            }
            1 -> {
                // "Chlack" : snare + impact médium
                perc(38, 92)
                perc(42, 50)
                noteOnOff(0, 55, 90, 200)   // Sol3 Orchestra Hit médium
            }
            2 -> {
                // "Boom" : kick fort + tom grave + gros impact
                perc(35, 122)
                perc(41, 88)
                noteOnOff(0, 48, 115, 260)  // Do3 Orchestra Hit grave
            }
        }
    }

    // ── Résultats ─────────────────────────────────────────────────────────────

    /** "Paaaa" — fanfare victoire montante */
    fun playWin() {
        if (!ready || muted) return
        // Arpège Do4→Sol4→Do5→Mi5 puis note tenue "paaaa"
        noteOnOff(1, 60, 95,  110)
        noteDelayed(1, 67, 100, 120, 120)
        noteDelayed(1, 72, 108, 160, 245)
        noteDelayed(1, 76, 118, 600, 380)  // Mi5 tenu = le "paaaa"
        percDelayed(49, 88, 360)           // crash cymbal sur l'arrivée
    }

    /** "Bhouu" — descente triste */
    fun playFail() {
        if (!ready || muted) return
        // Sol3→Fa3→Re3→Do3→La2 descente lente et grave
        noteOnOff(2, 55, 88,  210)
        noteDelayed(2, 53, 78, 230, 200)
        noteDelayed(2, 50, 68, 250, 420)
        noteDelayed(2, 48, 58, 270, 640)
        noteDelayed(2, 45, 48, 360, 870)  // La2 finale grave = le "bhouu"
        percDelayed(38, 55, 50)           // caisse claire étouffée
    }

    /** "Tadaaa" — fanfare carte spéciale, plus épique que le win */
    fun playSpecialCard() {
        if (!ready || muted) return
        // Arpège rapide ascendant Do4→Sol4→Do5→Sol5 puis accord plein tenu
        noteOnOff(1, 60, 100, 90)
        noteDelayed(1, 67, 108, 105, 90)
        noteDelayed(1, 72, 115, 130, 185)
        noteDelayed(1, 79, 127, 700, 290)  // Sol5 tenu = le "tadaaa"
        // Double frappe percussive pour l'emphase
        percDelayed(49, 112, 270)          // crash sur l'arrivée du Sol5
        percDelayed(38,  85, 295)
    }

    // ── Bas niveau ────────────────────────────────────────────────────────────

    private fun perc(note: Int, vel: Int) =
        SharedSonivoxDriver.queueEvent(byteArrayOf(0x99.toByte(), note.toByte(), vel.toByte()))

    private fun percDelayed(note: Int, vel: Int, delayMs: Long) = Thread {
        try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
        if (ready) perc(note, vel)
    }.start()

    private fun noteOnOff(ch: Int, note: Int, vel: Int, durationMs: Long) {
        if (!ready) return
        SharedSonivoxDriver.queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
        Thread {
            try { Thread.sleep(durationMs) } catch (_: InterruptedException) {}
            if (ready) SharedSonivoxDriver.queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
        }.start()
    }

    private fun noteDelayed(ch: Int, note: Int, vel: Int, durationMs: Long, delayMs: Long) {
        Thread {
            try { Thread.sleep(delayMs) } catch (_: InterruptedException) {}
            if (!ready) return@Thread
            SharedSonivoxDriver.queueEvent(byteArrayOf((0x90 or ch).toByte(), note.toByte(), vel.toByte()))
            try { Thread.sleep(durationMs) } catch (_: InterruptedException) {}
            if (ready) SharedSonivoxDriver.queueEvent(byteArrayOf((0x80 or ch).toByte(), note.toByte(), 0))
        }.start()
    }

    private fun programChange(ch: Int, prog: Int) =
        SharedSonivoxDriver.queueEvent(byteArrayOf((0xC0 or ch).toByte(), prog.toByte()))

    private fun controlChange(ch: Int, cc: Int, value: Int) =
        SharedSonivoxDriver.queueEvent(byteArrayOf((0xB0 or ch).toByte(), cc.toByte(), value.toByte()))
}
