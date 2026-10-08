package com.Atom2Universe.app.crypto.clicker

import com.Atom2Universe.app.crypto.sound.SharedSonivoxDriver

/**
 * Son de clic discret via Sonivox EAS.
 * Ch 9 (percussions GM) — note 76 (High Wood Block) à faible vélocité.
 *
 * Passe par [SharedSonivoxDriver] (comptage de références) pour ne pas couper
 * le moteur quand la fusion, lancée depuis le clicker, le libère au retour.
 */
class ClickerSoundEngine {

    var muted = false

    private var acquired = false
    private var ready = false

    fun start() {
        if (acquired) return
        acquired = true
        SharedSonivoxDriver.acquire {
            ready = true
            // Volume canal 9 modéré
            SharedSonivoxDriver.queueEvent(byteArrayOf(0xB9.toByte(), 7, 72))
            // Reverb send = 0 — supprime l'étouffement/écho Sonivox par défaut
            SharedSonivoxDriver.queueEvent(byteArrayOf(0xB9.toByte(), 91, 0))
            // Chorus send = 0
            SharedSonivoxDriver.queueEvent(byteArrayOf(0xB9.toByte(), 93, 0))
        }
    }

    fun stop() {
        if (!acquired) return
        acquired = false
        ready = false
        SharedSonivoxDriver.release()
    }

    fun playClick() {
        if (!ready || muted) return
        // High Wood Block (76) vélocité 35 — "toc" court et neutre
        SharedSonivoxDriver.queueEvent(byteArrayOf(0x99.toByte(), 76, 35))
    }

    fun playCritClick() {
        if (!ready || muted) return
        // Low Wood Block (77) vélocité 55 — "cloc" grave, même famille que le clic normal
        SharedSonivoxDriver.queueEvent(byteArrayOf(0x99.toByte(), 77, 55))
    }
}
