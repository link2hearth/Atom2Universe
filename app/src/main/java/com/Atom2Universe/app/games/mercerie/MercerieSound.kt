package com.Atom2Universe.app.games.mercerie

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.billthefarmer.mididriver.MidiDriver

/**
 * Les sons du jeu, joués par Sonivox : boîte à musique, marimba et harpe, toujours sur une gamme
 * pentatonique pour que tout sonne juste, même en pleine cascade.
 */
class MercerieSound {
    private var driver: MidiDriver? = null
    @Volatile private var ready = false
    private val handler = Handler(Looper.getMainLooper())
    private var lastZap = 0L

    /**
     * Le synthé ne démarre qu'à la première note et s'arrête après [IDLE_MS] de silence : allumé,
     * il calcule du son (même muet) sans arrêt, ce qui garde le processeur éveillé.
     */
    private val sleep = Runnable { shutDown() }
    private var active = false

    /** L'activité est au premier plan : les sons sont permis. */
    fun start() { active = true }

    /** Le haut-parleur de la barre du haut : coupé, aucune note ne part et le synthé s'éteint. */
    var muted = false
        set(value) {
            field = value
            if (value) { handler.removeCallbacksAndMessages(null); shutDown() }
        }

    /** Démarre le synthé tout de suite (un doigt se pose) : la note qui suit ne l'attendra pas. */
    fun wake() {
        if (!active || muted) return
        if (driver == null) boot()
        handler.removeCallbacks(sleep)
        handler.postDelayed(sleep, IDLE_MS)
    }

    private fun boot() {
        val d = MidiDriver.getInstance {
            ready = true
            send(0xC0 or BOX, 10)
            send(0xC0 or WOOD, 12)
            send(0xC0 or HARP, 46)
            send(0xB0 or BOX, 7, 100)
            send(0xB0 or WOOD, 7, 96)
            send(0xB0 or HARP, 7, 88)
        }
        driver = d
        d.start()
    }

    fun stop() {
        active = false
        handler.removeCallbacksAndMessages(null)
        shutDown()
    }

    private fun shutDown() {
        ready = false
        driver?.stop()
        driver = null
    }

    private fun send(status: Int, a: Int, b: Int = -1) {
        val d = driver ?: return
        d.queueEvent(if (b < 0) byteArrayOf(status.toByte(), a.toByte()) else byteArrayOf(status.toByte(), a.toByte(), b.toByte()))
    }

    private fun note(ch: Int, pitch: Int, velocity: Int, ms: Long, delay: Long = 0) {
        if (muted) return
        wake()
        if (!ready) return
        val play = Runnable {
            if (!ready) return@Runnable
            send(0x90 or ch, pitch.coerceIn(24, 108), velocity.coerceIn(1, 127))
            handler.postDelayed({ if (ready) send(0x80 or ch, pitch.coerceIn(24, 108), 0) }, ms)
        }
        if (delay <= 0) play.run() else handler.postDelayed(play, delay)
    }

    private fun scale(step: Int) = 60 + 12 * (step / 5) + PENTA[step % 5]

    /** Une pièce part ; la note monte avec la cascade. */
    fun pop(cascade: Int) = note(WOOD, scale(4 + cascade.coerceIn(1, 9)), 78, 160)
    fun swap() = note(WOOD, 67, 52, 90)
    fun bad() { note(WOOD, 52, 60, 120); note(WOOD, 51, 54, 140, 70) }
    fun tick() = note(WOOD, 79, 40, 60)
    fun land() {}
    fun special() { note(BOX, 88, 84, 220); note(BOX, 91, 78, 260, 70) }
    fun line() { for (k in 0 until 6) note(HARP, scale(5 + k), 74 - k * 4, 200, k * 28L) }
    fun blast() { note(WOOD, 43, 110, 300); note(WOOD, 50, 92, 260, 30); note(BOX, 84, 70, 160, 40) }
    fun zap() {
        val now = SystemClock.uptimeMillis()
        if (now - lastZap < 50) return
        lastZap = now
        note(BOX, scale(10 + (now % 5).toInt()), 70, 140)
    }
    fun basket() { for (k in 0 until 3) note(BOX, scale(10 + k * 2), 86, 260, k * 80L) }
    /** Un mot de félicitation : une montée d'autant plus longue et brillante que le palier est haut. */
    fun cheer(tier: Int) {
        val n = 2 + tier
        for (k in 0 until n) note(HARP, scale(6 + k * 2), 82 + tier * 4, 320, k * 55L)
        if (tier >= 3) for (k in 0 until 3) note(BOX, scale(12 + k * 2 + tier), 74, 360, n * 55L + k * 70L)
    }
    /** « Plus que cinq coups » : deux notes qui hésitent. */
    fun warn() { note(BOX, scale(9), 70, 200); note(BOX, scale(8), 66, 260, 160) }
    fun combo() { for (p in intArrayOf(0, 2, 4, 7)) note(HARP, scale(5 + p), 100, 600); note(WOOD, 41, 110, 300) }
    fun poof() = note(WOOD, scale(7), 60, 120)
    fun flutter() {
        val now = SystemClock.uptimeMillis()
        if (now - lastZap < 60) return
        lastZap = now
        for (k in 0 until 3) note(HARP, scale(11 + k), 64, 110, k * 35L)
    }
    fun spread() { note(WOOD, 45, 70, 220); note(WOOD, 47, 56, 200, 90) }
    /** Le minuteur baisse : un tic sec, plus aigu quand il presse. */
    fun tickTock(left: Int) = note(WOOD, if (left <= 3) 84 else 76, if (left <= 3) 90 else 60, 70)
    fun ring() { for (k in 0 until 8) note(BOX, if (k % 2 == 0) 88 else 84, 100, 90, k * 75L) }
    fun collect() = note(BOX, scale(13 + (SystemClock.uptimeMillis() % 3).toInt()), 58, 120)
    fun win(stars: Int) { for (k in 0 until 4 + stars) note(HARP, scale(5 + k * 2), 96, 500, k * 110L) }
    fun lose() { for (k in 0 until 3) note(WOOD, scale(6 - k * 2), 70, 380, k * 220L) }

    private companion object {
        const val BOX = 0
        const val WOOD = 1
        const val HARP = 2
        /** Silence au bout duquel le synthé s'éteint. */
        const val IDLE_MS = 8000L
        val PENTA = intArrayOf(0, 2, 4, 7, 9)
    }
}
