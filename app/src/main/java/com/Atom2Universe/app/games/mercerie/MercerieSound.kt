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

    fun start() {
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
        ready = false
        handler.removeCallbacksAndMessages(null)
        driver?.stop()
        driver = null
    }

    private fun send(status: Int, a: Int, b: Int = -1) {
        val d = driver ?: return
        d.queueEvent(if (b < 0) byteArrayOf(status.toByte(), a.toByte()) else byteArrayOf(status.toByte(), a.toByte(), b.toByte()))
    }

    private fun note(ch: Int, pitch: Int, velocity: Int, ms: Long, delay: Long = 0) {
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
    fun win(stars: Int) { for (k in 0 until 4 + stars) note(HARP, scale(5 + k * 2), 96, 500, k * 110L) }
    fun lose() { for (k in 0 until 3) note(WOOD, scale(6 - k * 2), 70, 380, k * 220L) }

    private companion object {
        const val BOX = 0
        const val WOOD = 1
        const val HARP = 2
        val PENTA = intArrayOf(0, 2, 4, 7, 9)
    }
}
