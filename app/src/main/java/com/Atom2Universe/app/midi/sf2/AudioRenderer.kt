package com.Atom2Universe.app.midi.sf2

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock
import kotlin.math.max

/**
 * Listener for audio renderer state changes.
 * Used to notify the application when audio routing changes or errors occur.
 */
interface AudioRendererListener {
    /** Called when the AudioTrack dies and needs recovery */
    fun onAudioTrackDied(reason: String)
    /** Called when the AudioTrack has been successfully recovered */
    fun onAudioTrackRecovered()
    /** Called when audio output device changes */
    fun onOutputDeviceChanged(deviceId: Int, deviceType: Int)
}

/**
 * Audio renderer using Android AudioTrack.
 * Handles real-time audio output with a callback-based rendering model.
 *
 * Thread-safety: This class is thread-safe. All public methods can be called
 * from any thread. The render loop runs on a dedicated audio thread.
 *
 * @param sampleRate Sample rate in Hz
 * @param bufferSizeFrames Buffer size in frames (smaller = lower latency but higher CPU)
 * @param lowLatency Enable low-latency mode for interactive use (keyboards, etc.)
 */
class AudioRenderer(
    private val context: android.content.Context? = null,
    val sampleRate: Int = 48000,  // Changed to 48000Hz to match most device DACs and avoid SRC
    private val bufferSizeFrames: Int = 512,
    private val lowLatency: Boolean = false,
    private val forceSpeaker: Boolean = false  // Force audio to built-in speaker (for practice mode)
) {
    companion object {
        private const val TAG = "AudioRenderer"

        private const val CHANNELS = AudioFormat.CHANNEL_OUT_STEREO
        private const val ENCODING = AudioFormat.ENCODING_PCM_FLOAT

        private const val OPERATION_TIMEOUT_MS = 3000L
        private const val MAX_RECOVERY_ATTEMPTS = 3
        private const val RECOVERY_DELAY_MS = 100L

        private val instanceCounter = AtomicInteger(0)

        /**
         * Final-stage soft clipping using fast polynomial approximation.
         * Much smoother than hard clipping, produces less harsh harmonics.
         *
         * Below 0.9: pass through unchanged
         * 0.9 to 1.0: gentle compression
         * Above 1.0: asymptotic limit to ~0.99
         */
        private fun softClipFinal(x: Float): Float {
            val absX = if (x >= 0f) x else -x

            // Fast path: most samples are below threshold
            if (absX <= 0.9f) return x

            // Soft saturation for values above 0.9
            val sign = if (x >= 0f) 1f else -1f
            return if (absX <= 1.0f) {
                // Gentle compression in 0.9-1.0 range using cubic curve
                // Maps [0.9, 1.0] -> [0.9, 0.98] smoothly
                val t = (absX - 0.9f) * 10f  // 0 to 1
                sign * (0.9f + 0.08f * (3f * t * t - 2f * t * t * t))
            } else {
                // Hard overdrive territory - use hyperbolic saturation
                // Asymptotically approaches 0.995
                val excess = absX - 1.0f
                sign * (0.98f + 0.015f * excess / (excess + 0.1f))
            }
        }
    }

    private val operationLock = ReentrantLock()

    private var audioTrack: AudioTrack? = null

    private var renderThread: Thread? = null
    private val isRunning = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)
    private val isReleased = AtomicBoolean(false)

    private val instanceId = instanceCounter.incrementAndGet()

    private val renderCallback = AtomicReference<RenderCallback?>(null)
    private val listener = AtomicReference<AudioRendererListener?>(null)

    // Preferred output device (for recovery after AudioTrack recreation)
    private var preferredOutputDevice: AudioDeviceInfo? = null

    private val leftBuffer = FloatArray(bufferSizeFrames)
    private val rightBuffer = FloatArray(bufferSizeFrames)
    private val interleavedBuffer = FloatArray(bufferSizeFrames * 2)

    var audioSessionId: Int = AudioManager.AUDIO_SESSION_ID_GENERATE
        private set

    @Volatile var underrunCount: Int = 0
        private set

    @Volatile var recoveryCount: Int = 0
        private set

    fun interface RenderCallback {
        fun onRender(leftBuffer: FloatArray, rightBuffer: FloatArray, numSamples: Int)
    }

    /**
     * Callback for tapping the audio output (recording).
     * Called on the audio thread after soft clipping, with the final interleaved stereo samples.
     */
    fun interface AudioTapCallback {
        fun onAudioTap(interleavedBuffer: FloatArray, numFrames: Int)
    }

    private val audioTapCallback = AtomicReference<AudioTapCallback?>(null)

    fun setRenderCallback(callback: RenderCallback?) {
        renderCallback.set(callback)
    }

    /**
     * Set a tap callback to capture the audio output in real time.
     * Pass null to stop capturing.
     */
    fun setAudioTapCallback(callback: AudioTapCallback?) {
        audioTapCallback.set(callback)
    }

    fun setListener(l: AudioRendererListener?) {
        listener.set(l)
    }

    fun start(): Boolean {
        android.util.Log.d(TAG, "start: called, isReleased=${isReleased.get()} isRunning=${isRunning.get()}")

        if (isReleased.get()) {
            android.util.Log.w(TAG, "start: renderer is released, cannot start")
            return false
        }

        if (isRunning.get()) {
            android.util.Log.d(TAG, "start: already running")
            return true
        }

        val acquired = try {
            operationLock.tryLock(OPERATION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            android.util.Log.w(TAG, "start: interrupted while acquiring lock")
            return false
        }

        if (!acquired) {
            android.util.Log.w(TAG, "start: failed to acquire lock")
            return false
        }

        try {
            if (isRunning.get()) {
                return true
            }

            // Try to REUSE existing AudioTrack if available and valid
            // This is critical for background playback - creating new AudioTracks
            // in background can fail on some Android versions
            val existingTrack = audioTrack
            if (existingTrack != null) {
                try {
                    // Check if the existing track is still usable
                    if (existingTrack.state == AudioTrack.STATE_INITIALIZED) {
                        // Reuse existing track - just restart it
                        android.util.Log.d(TAG, "start: reusing existing track")
                        existingTrack.play()
                        isRunning.set(true)
                        isPaused.set(false)

                        renderThread = Thread(this::renderLoop, "SF2-AudioRenderer-$instanceId")
                        renderThread?.priority = Thread.MAX_PRIORITY
                        renderThread?.start()

                        return true
                    }
                } catch (_: Exception) {
                    android.util.Log.w(TAG, "start: existing track not reusable")
                }

                // Release the old track if it's not usable
                try {
                    existingTrack.stop()
                } catch (_: Exception) { }
                try {
                    existingTrack.release()
                } catch (_: Exception) { }
                audioTrack = null
            }

            // Create a new AudioTrack using the helper
            // Use forceSpeaker to prevent audio routing to USB devices
            val newTrack = createAudioTrack(forceSpeaker, context)
            if (newTrack == null || newTrack.state != AudioTrack.STATE_INITIALIZED) {
                android.util.Log.e(TAG, "start: failed to create AudioTrack")
                return false
            }

            audioTrack = newTrack
            audioSessionId = newTrack.audioSessionId

            newTrack.play()
            isRunning.set(true)
            isPaused.set(false)

            renderThread = Thread(this::renderLoop, "SF2-AudioRenderer-$instanceId")
            renderThread?.priority = Thread.MAX_PRIORITY
            renderThread?.start()

            android.util.Log.i(TAG, "start: SUCCESS, sessionId=$audioSessionId")

            return true

        } catch (_: Exception) {
            releaseInternal()
            return false
        } finally {
            operationLock.unlock()
        }
    }

    fun pause() {
        if (isReleased.get()) return
        isPaused.set(true)
        try {
            audioTrack?.pause()
        } catch (_: Exception) { }
    }

    fun resume() {
        if (isReleased.get()) return
        isPaused.set(false)
        try {
            audioTrack?.play()
        } catch (_: Exception) { }
    }

    /**
     * Flushes the audio buffer to immediately stop any pending audio.
     * This should be called after stopping voices to prevent ghost notes.
     * The AudioTrack must be paused before flushing.
     */
    fun flush() {
        if (isReleased.get()) return
        try {
            val track = audioTrack ?: return
            val wasPaused = isPaused.get()

            // Must pause before flushing
            if (!wasPaused) {
                isPaused.set(true)
                track.pause()
            }

            // Flush clears all pending audio data
            track.flush()

            // Resume if we weren't paused before
            if (!wasPaused) {
                isPaused.set(false)
                track.play()
            }
        } catch (_: Exception) { }
    }

    /**
     * Pauses and flushes the audio buffer, then resumes.
     * Use this during seek to immediately cut off any playing audio.
     */
    fun flushAndResume() {
        if (isReleased.get()) return
        try {
            val track = audioTrack ?: return
            track.pause()
            track.flush()
            track.play()
        } catch (_: Exception) { }
    }

    fun stop() {
        if (isReleased.get()) return

        val acquired = try {
            operationLock.tryLock(OPERATION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            forceStop()
            return
        }

        if (!acquired) {
            forceStop()
            return
        }

        try {
            stopInternal()
        } finally {
            operationLock.unlock()
        }
    }

    private fun stopInternal() {
        if (!isRunning.getAndSet(false)) {
            return
        }

        // First, stop the AudioTrack to unblock any pending write() call
        // This MUST happen BEFORE thread.join() to prevent deadlock:
        // - The render thread may be blocked in AudioTrack.write(WRITE_BLOCKING)
        // - pause()+flush() will unblock the write() call
        // - Without this, thread.join() would wait forever (or until timeout)
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (e: Exception) {
            android.util.Log.w(TAG, "stopInternal: failed to pause/flush AudioTrack", e)
        }

        // Now interrupt the thread (sets interrupted flag for sleep/wait calls)
        renderThread?.interrupt()

        val thread = renderThread
        if (thread != null && thread.isAlive) {
            try {
                // Primary join with 3s timeout (increased from 2s)
                // The AudioTrack should already be unblocked from pause() above
                thread.join(3000)

                if (thread.isAlive) {
                    android.util.Log.w(TAG, "stopInternal: render thread did not stop after 3s, forcing AudioTrack release")

                    // Emergency: release the AudioTrack completely to force-unblock the thread
                    // This is a last resort - the track will need to be recreated on next start()
                    try {
                        audioTrack?.release()
                        audioTrack = null
                    } catch (e: Exception) {
                        android.util.Log.w(TAG, "stopInternal: failed to release AudioTrack", e)
                    }

                    // Final join attempt after releasing the track
                    try {
                        thread.join(2000)
                        if (thread.isAlive) {
                            android.util.Log.e(TAG, "stopInternal: render thread ZOMBIE - could not stop after 5s total")
                            // Thread is a zombie - nothing more we can do without risking app instability
                            // The thread will eventually die when the AudioTrack native resources are GC'd
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        renderThread = null

        // Final cleanup: pause and flush if track still exists (normal case)
        // Skip if we already released it in emergency path above
        if (audioTrack != null) {
            try {
                audioTrack?.pause()
                audioTrack?.flush()
            } catch (_: Exception) { }
        }
    }

    private fun forceStop() {
        isRunning.set(false)
        renderThread?.interrupt()
        try {
            audioTrack?.pause()
            audioTrack?.flush()
        } catch (_: Exception) { }
    }

    fun release() {
        if (isReleased.getAndSet(true)) {
            return
        }

        val acquired = try {
            operationLock.tryLock(OPERATION_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            releaseInternal()
            return
        }

        if (!acquired) {
            releaseInternal()
            return
        }

        try {
            releaseInternal()
        } finally {
            operationLock.unlock()
        }
    }

    private fun releaseInternal() {
        stopInternal()

        try {
            audioTrack?.release()
        } catch (_: Exception) { }
        audioTrack = null
    }

    fun isRunning(): Boolean = isRunning.get()

    fun isPaused(): Boolean = isPaused.get()

    fun setPreferredOutputDevice(device: AudioDeviceInfo?): Boolean {
        // Save the preference for recovery
        preferredOutputDevice = device

        val track = audioTrack ?: return false
        return try {
            val result = track.setPreferredDevice(device)
            val deviceLabel = device?.let { "type=${it.type}(id=${it.id})" } ?: "default"
            android.util.Log.d(TAG, "setPreferredOutputDevice: device=$deviceLabel result=$result trackState=${track.state} playState=${track.playState}")

            if (result && device != null) {
                listener.get()?.onOutputDeviceChanged(device.id, device.type)
            }
            result
        } catch (_: Exception) {
            android.util.Log.w(TAG, "setPreferredOutputDevice failed")
            false
        }
    }

    /**
     * Creates a new AudioTrack with current settings.
     * Used for initial creation and recovery after death.
     *
     * When forceSpeaker is true, the track will prefer the built-in speaker
     * to prevent audio from being routed to USB devices (like MIDI keyboards).
     */
    private fun createAudioTrack(forceSpeaker: Boolean = false, context: android.content.Context? = null): AudioTrack? {
        val minBufferSize = AudioTrack.getMinBufferSize(sampleRate, CHANNELS, ENCODING)

        // En mode normal (musique), on agrandit le buffer hardware à 4× la taille de rendu.
        // Cela crée ~42ms de réserve préchargée dans le hardware : si un cycle de rendu
        // prend plus longtemps (throttling CPU en background/Doze mode), le speaker pioche
        // dans cette réserve sans interruption. Le buffer de RENDU reste à 512 frames pour
        // conserver la précision MIDI. En mode lowLatency (clavier), on garde 1× pour
        // minimiser la latence interactive.
        val hardwareMultiplier = if (lowLatency) 1 else 4
        val bufferSizeBytes = max(minBufferSize, bufferSizeFrames * 2 * 4 * hardwareMultiplier)

        val builder = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(if (lowLatency) AudioAttributes.USAGE_GAME else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(ENCODING)
                    .setSampleRate(sampleRate)
                    .setChannelMask(CHANNELS)
                    .build()
            )
            .setBufferSizeInBytes(bufferSizeBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)

        if (lowLatency) {
            builder.setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
        }

        return try {
            val track = builder.build()
            android.util.Log.d(TAG, "createAudioTrack: created new track state=${track.state} sessionId=${track.audioSessionId}")

            // If forceSpeaker is requested, set preferred device to speaker immediately
            if (forceSpeaker && context != null) {
                val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? AudioManager
                val speaker = audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                    ?.find { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                if (speaker != null) {
                    val result = track.setPreferredDevice(speaker)
                    android.util.Log.d(TAG, "createAudioTrack: set preferred device to SPEAKER, result=$result")
                    preferredOutputDevice = speaker
                }
            }

            track
        } catch (_: Exception) {
            android.util.Log.e(TAG, "createAudioTrack: failed to create track")
            null
        }
    }

    /**
     * Attempts to recover the AudioTrack after it dies.
     * This can happen when USB devices are connected/disconnected.
     * @return true if recovery was successful
     */
    private fun attemptRecovery(reason: String): Boolean {
        android.util.Log.w(TAG, "attemptRecovery: starting recovery, reason=$reason")
        listener.get()?.onAudioTrackDied(reason)

        // Release the dead track
        try {
            audioTrack?.release()
        } catch (_: Exception) {
            android.util.Log.w(TAG, "attemptRecovery: failed to release dead track")
        }
        audioTrack = null

        // Wait a bit for the audio system to stabilize
        try {
            Thread.sleep(RECOVERY_DELAY_MS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return false
        }

        // Try to create a new AudioTrack
        for (attempt in 1..MAX_RECOVERY_ATTEMPTS) {
            android.util.Log.d(TAG, "attemptRecovery: attempt $attempt/$MAX_RECOVERY_ATTEMPTS")

            val newTrack = createAudioTrack(forceSpeaker, context)
            if (newTrack != null && newTrack.state == AudioTrack.STATE_INITIALIZED) {
                audioTrack = newTrack
                audioSessionId = newTrack.audioSessionId

                // Start playback
                try {
                    newTrack.play()
                    recoveryCount++
                    android.util.Log.i(TAG, "attemptRecovery: SUCCESS after $attempt attempts, total recoveries=$recoveryCount")
                    listener.get()?.onAudioTrackRecovered()
                    return true
                } catch (_: Exception) {
                    android.util.Log.w(TAG, "attemptRecovery: failed to start track on attempt $attempt")
                    try { newTrack.release() } catch (_: Exception) { }
                    audioTrack = null
                }
            }

            // Wait before retry
            if (attempt < MAX_RECOVERY_ATTEMPTS) {
                try {
                    Thread.sleep(RECOVERY_DELAY_MS * attempt)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    return false
                }
            }
        }

        android.util.Log.e(TAG, "attemptRecovery: FAILED after $MAX_RECOVERY_ATTEMPTS attempts")
        return false
    }

    private fun renderLoop() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
        android.util.Log.i(TAG, "renderLoop: STARTED on thread ${Thread.currentThread().name}")

        try {
            while (isRunning.get() && !Thread.currentThread().isInterrupted) {
                if (isPaused.get()) {
                    try {
                        Thread.sleep(10)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                    continue
                }

                leftBuffer.fill(0f)
                rightBuffer.fill(0f)

                renderCallback.get()?.onRender(leftBuffer, rightBuffer, bufferSizeFrames)

                // Final output stage with soft clipping
                for (i in 0 until bufferSizeFrames) {
                    interleavedBuffer[i * 2] = softClipFinal(leftBuffer[i])
                    interleavedBuffer[i * 2 + 1] = softClipFinal(rightBuffer[i])
                }

                // Audio recording tap (after soft clip, before AudioTrack write)
                audioTapCallback.get()?.onAudioTap(interleavedBuffer, bufferSizeFrames)

                val track = audioTrack
                if (track != null && isRunning.get()) {
                    try {
                        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) {
                            // WRITE_BLOCKING is essential for proper audio timing
                            val written = track.write(
                                interleavedBuffer,
                                0,
                                interleavedBuffer.size,
                                AudioTrack.WRITE_BLOCKING
                            )

                            if (written < 0) {
                                // AudioTrack error - attempt recovery
                                android.util.Log.w(TAG, "renderLoop: write error=$written, attempting recovery")
                                if (!attemptRecovery("write_error_$written")) {
                                    android.util.Log.e(TAG, "renderLoop: recovery failed, exiting")
                                    break
                                }
                                android.util.Log.i(TAG, "renderLoop: recovery succeeded, continuing")
                            } else if (written < interleavedBuffer.size) {
                                underrunCount++
                            }
                        } else if (track.playState == AudioTrack.PLAYSTATE_STOPPED) {
                            // Track was stopped unexpectedly
                            android.util.Log.w(TAG, "renderLoop: track stopped, state=${track.state}")
                            if (track.state == AudioTrack.STATE_INITIALIZED) {
                                try {
                                    track.play()
                                } catch (_: Exception) {
                                    if (!attemptRecovery("restart_failed")) break
                                }
                            } else {
                                if (!attemptRecovery("track_uninitialized")) break
                            }
                        }
                    } catch (e: IllegalStateException) {
                        android.util.Log.w(TAG, "renderLoop: IllegalStateException", e)
                        if (!attemptRecovery("illegal_state")) break
                    }
                } else if (track == null && isRunning.get()) {
                    android.util.Log.w(TAG, "renderLoop: audioTrack is null")
                    if (!attemptRecovery("null_track")) break
                }
            }
        } catch (_: Exception) {
            android.util.Log.e(TAG, "renderLoop: exception")
        }
        android.util.Log.i(TAG, "renderLoop: EXITED")
    }

    fun getStats(): String {
        return "AudioRenderer: ${sampleRate}Hz, running=${isRunning.get()}, " +
                "paused=${isPaused.get()}, underruns=$underrunCount, recoveries=$recoveryCount"
    }

    @Suppress("unused")
    fun resetStats() {
        underrunCount = 0
    }
}
