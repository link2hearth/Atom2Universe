package com.Atom2Universe.app

import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Global sleep timer manager singleton.
 * Stops all audio playback after a user-defined delay.
 */
object SleepTimerManager {

    interface Listener {
        fun onTimerTick(remainingMillis: Long)
        fun onTimerFinished()
        fun onTimerCancelled()
        /** Called when time is added to the running timer. Default implementation calls onTimerTick. */
        fun onTimerExtended(newRemainingMillis: Long) {
            onTimerTick(newRemainingMillis)
        }
    }

    private val listeners = CopyOnWriteArraySet<Listener>()
    private var countDownTimer: CountDownTimer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Current remaining time in milliseconds. 0 if no timer is running.
     */
    var remainingMillis: Long = 0L
        private set

    /**
     * Whether a timer is currently running.
     */
    val isTimerRunning: Boolean
        get() = countDownTimer != null && remainingMillis > 0

    /**
     * Add a listener to receive timer events.
     */
    fun addListener(listener: Listener) {
        listeners.add(listener)
    }

    /**
     * Remove a listener.
     */
    fun removeListener(listener: Listener) {
        listeners.remove(listener)
    }

    /**
     * Start the sleep timer with the given duration.
     *
     * @param durationMillis Duration in milliseconds
     */
    fun startTimer(durationMillis: Long) {
        if (durationMillis <= 0) return

        // Cancel any existing timer
        cancelTimerInternal(notify = false)

        remainingMillis = durationMillis

        countDownTimer = object : CountDownTimer(durationMillis, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                remainingMillis = millisUntilFinished
                notifyTick(millisUntilFinished)
            }

            override fun onFinish() {
                remainingMillis = 0
                countDownTimer = null

                // Stop all audio playback
                AudioPlaybackManager.stopAll()

                notifyFinished()
            }
        }.start()

        // Notify listeners of initial state
        notifyTick(durationMillis)
    }

    /**
     * Cancel the running timer.
     */
    fun cancelTimer() {
        cancelTimerInternal(notify = true)
    }

    private fun cancelTimerInternal(notify: Boolean) {
        countDownTimer?.cancel()
        countDownTimer = null
        remainingMillis = 0

        if (notify) {
            notifyCancelled()
        }
    }

    private fun notifyTick(millisUntilFinished: Long) {
        mainHandler.post {
            listeners.forEach { it.onTimerTick(millisUntilFinished) }
        }
    }

    private fun notifyFinished() {
        mainHandler.post {
            listeners.forEach { it.onTimerFinished() }
        }
    }

    private fun notifyCancelled() {
        mainHandler.post {
            listeners.forEach { it.onTimerCancelled() }
        }
    }

    /**
     * Format remaining time as HH:MM or MM:SS depending on duration.
     */
    fun formatRemainingTime(): Pair<Int, Int> {
        val totalSeconds = remainingMillis / 1000
        val hours = (totalSeconds / 3600).toInt()
        val minutes = ((totalSeconds % 3600) / 60).toInt()
        return Pair(hours, minutes)
    }
}
