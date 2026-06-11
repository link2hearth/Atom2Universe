package com.Atom2Universe.app.crypto.clicker

import org.billthefarmer.mididriver.MidiDriver

/**
 * Son de clic discret via Sonivox EAS.
 * Ch 9 (percussions GM) — note 76 (High Wood Block) à faible vélocité.
 */
class ClickerSoundEngine {

    var muted = false

    private var driver: MidiDriver? = null
    private var ready = false

    fun start() {
        val d = MidiDriver.getInstance {
            ready = true
            val drv = driver ?: return@getInstance
            // Volume canal 9 modéré
            drv.queueEvent(byteArrayOf(0xB9.toByte(), 7, 72))
            // Reverb send = 0 — supprime l'étouffement/écho Sonivox par défaut
            drv.queueEvent(byteArrayOf(0xB9.toByte(), 91, 0))
            // Chorus send = 0
            drv.queueEvent(byteArrayOf(0xB9.toByte(), 93, 0))
        }
        driver = d
        d.start()
    }

    fun stop() {
        ready = false
        driver?.stop()
        driver = null
    }

    fun playClick() {
        if (!ready || muted) return
        // High Wood Block (76) vélocité 35 — "toc" court et neutre
        driver?.queueEvent(byteArrayOf(0x99.toByte(), 76, 35))
    }

    fun playCritClick() {
        if (!ready || muted) return
        // Low Wood Block (77) vélocité 55 — "cloc" grave, même famille que le clic normal
        driver?.queueEvent(byteArrayOf(0x99.toByte(), 77, 55))
    }
}
