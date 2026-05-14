package com.Atom2Universe.app.midi.sf2

import java.util.concurrent.locks.LockSupport

// kotlin.math.sqrt removed - using Math.cbrt for gentler gain reduction curve

/**
 * Manages a pool of voices for polyphonic synthesis.
 * Handles voice allocation, stealing, and exclusive class management.
 */
class Sf2VoicePool(
    private val maxVoices: Int = 128,
    private val sampleRate: Int = 44100
) {
    companion object {
        private const val NUM_CHANNELS = 16
        private const val PERCUSSION_CHANNEL = 9

        // Per-channel polyphony limits to prevent single-channel overload
        private const val DEFAULT_CHANNEL_VOICE_LIMIT = 16
        private const val PERCUSSION_CHANNEL_VOICE_LIMIT = 24

        // Voice count above which we start reducing per-voice gain
        // Raised from 48 to 64: with global gain (0.25) providing 12dB headroom,
        // per-voice gain reduction only needs to kick in for extreme polyphony
        private const val GAIN_REDUCTION_THRESHOLD = 64

        // Minimum gain multiplier (never reduce below this)
        // Raised from 0.55 to 0.7: global gain already prevents saturation,
        // so we preserve more dynamics at extreme voice counts
        private const val MIN_VOICE_GAIN = 0.7f

        // Voice count below which we boost per-voice gain to compensate
        // for the conservative master gain (0.25) during solo passages
        private const val GAIN_BOOST_THRESHOLD = 16

        // Maximum boost multiplier for solo passages (+6dB)
        // Worst case: 2 voices × 2.0 boost × 0.25 master = 1.0 (soft clip threshold)
        private const val MAX_VOICE_BOOST = 2.0f

        // Smoothing coefficient for voice gain changes
        // ATTACK: Fast response when voice count increases (prevents clipping burst)
        // RELEASE: Slow response when voice count decreases (prevents volume pumping)
        private const val GAIN_ATTACK_COEFF = 0.4f   // Was 0.15 - now responds in ~2 buffers
        private const val GAIN_RELEASE_COEFF = 0.03f // Was 0.05 - even slower release for smoother recovery

        // Voice count threshold above which we force faster release times
        // This prevents sustain/release buildup from overwhelming the engine
        private const val RELEASE_CAP_THRESHOLD = 72  // Balanced: not too early, not too late

        // Maximum release time in samples when above threshold (80ms at 48000Hz)
        // Recalculated from 3528 (was 44100Hz) to 3840 (48000Hz) for correct timing
        private const val CAPPED_RELEASE_SAMPLES = 3840  // 80ms at 48000Hz

        // Emergency threshold - force quickFade on voices already in release
        // This is critical for resonant sounds like bells that accumulate
        private const val EMERGENCY_THRESHOLD = 104  // Raised to avoid triggering on normal music

        // Quick fade time for emergency mode (50ms at 48000Hz)
        // Recalculated from 2205 (was 44100Hz) to 2400 (48000Hz) for correct timing
        private const val EMERGENCY_FADE_SAMPLES = 2400

        // Voice count below which we use sequential rendering (parallel overhead not worth it)
        // Relevé à 48 : réduit l'activation du parallélisme pour les polyphonies modérées,
        // ce qui évite les problèmes de scheduling des workers en arrière-plan (écran éteint).
        private const val PARALLEL_THRESHOLD = 48

        // Maximum temp buffer size for worker threads (matches max expected numSamples)
        private const val MAX_BUFFER_SIZE = 1024
    }
    // Pre-allocated voice pool
    private val voices: Array<Sf2Voice> = Array(maxVoices) { Sf2Voice(sampleRate) }

    // Allocation tracking
    private var voiceAllocationOrder = 0L

    // Smoothed voice gain multiplier (prevents volume jumps when voice count changes)
    private var smoothedVoiceGain = 1f

    // Hysteresis flag for parallel/sequential rendering mode switching
    // Prevents oscillation when voice count hovers around PARALLEL_THRESHOLD
    private var useParallelRendering = false

    // Double-buffered channel active counts for thread-safe read from allocateVoice()
    // Without double-buffering, channelCountsBuffer (reused each render) would be
    // aliased with channelActiveSnapshot, getting cleared at the start of the next render
    private val channelActiveSnapA = IntArray(NUM_CHANNELS)
    private val channelActiveSnapB = IntArray(NUM_CHANNELS)
    @Volatile
    private var channelActiveSnapshot = channelActiveSnapA

    // Pre-allocated buffer for per-render channel counting (avoids IntArray allocation per render call)
    private val channelCountsBuffer = IntArray(NUM_CHANNELS)

    // Parallel rendering workers (created lazily on first parallel render)
    // 0 on single-core devices (always sequential), 1-3 on multi-core
    private val workerCount = minOf(Runtime.getRuntime().availableProcessors() / 2, 3)
    private var workers: Array<RenderWorker>? = null
    @Volatile private var mainRenderThread: Thread? = null

    // Pré-alloué pour éviter une allocation par cycle de rendu dans renderVoicesParallel.
    // Taille max = workerCount (1-3) — réutilisé à chaque appel via fill(true).
    private val workerHealthyBuffer = BooleanArray(maxOf(workerCount, 1))

    // Quand true, force le rendu séquentiel même si le nombre de voix dépasse le seuil.
    // Activé quand l'écran est éteint : le gouverneur CPU throttle les threads workers
    // (THREAD_PRIORITY_AUDIO), rendant le parallélisme peu fiable et source de craquements.
    // Le thread audio seul (THREAD_PRIORITY_URGENT_AUDIO) est bien plus stable en background.
    @Volatile var forceSequentialRendering: Boolean = false

    private fun ensureWorkers(): Array<RenderWorker> {
        workers?.let { return it }
        val created = Array(workerCount) { RenderWorker(it) }
        workers = created
        return created
    }

    // Stats
    var activeVoiceCount: Int = 0
        private set

    var peakVoiceCount: Int = 0
        private set

    // ==================== Render Worker ====================

    /**
     * Dedicated render worker thread for parallel voice rendering.
     * Pre-allocates temp buffers and sleeps via LockSupport.park() between render cycles.
     * Each worker renders an assigned range of voices into its own temp buffers,
     * which are then accumulated into the main output by the calling thread.
     */
    private inner class RenderWorker(val index: Int) {
        val thread: Thread

        // Pre-allocated temp buffers (6 × MAX_BUFFER_SIZE = 24KB per worker)
        val tempLeft = FloatArray(MAX_BUFFER_SIZE)
        val tempRight = FloatArray(MAX_BUFFER_SIZE)
        val tempReverbLeft = FloatArray(MAX_BUFFER_SIZE)
        val tempReverbRight = FloatArray(MAX_BUFFER_SIZE)
        val tempChorusLeft = FloatArray(MAX_BUFFER_SIZE)
        val tempChorusRight = FloatArray(MAX_BUFFER_SIZE)

        // Work parameters (set by main thread before unpark)
        var startVoice = 0
        var endVoice = 0
        var numSamples = 0
        var sf2FileRef: Sf2File? = null
        var channelVolumes: FloatArray? = null
        var channelPitchBends: FloatArray? = null
        var channelPans: FloatArray? = null
        var channelExpressions: FloatArray? = null
        var channelModulations: FloatArray? = null
        var channelReverbSends: FloatArray? = null
        var channelChorusSends: FloatArray? = null
        var combinedGain: Float = 1f

        // Result: number of still-active voices after rendering
        var activeCountResult = 0

        // Signaling
        @Volatile var renderComplete = true
        @Volatile var running = true

        init {
            thread = Thread({
                try {
                    android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO)
                } catch (_: Exception) {}
                while (running) {
                    LockSupport.park()
                    if (!running) break
                    // Guard against spurious wakeups: only render if main thread
                    // has set renderComplete=false (signaling work is ready)
                    if (renderComplete) continue
                    try {
                        doRender()
                    } catch (_: Exception) {
                        // Audio thread must never crash
                    } finally {
                        renderComplete = true
                        mainRenderThread?.let { LockSupport.unpark(it) }
                    }
                }
            }, "Sf2RenderWorker-$index").apply {
                isDaemon = true
                start()
            }
        }

        private fun doRender() {
            val file = sf2FileRef ?: return
            val samples = numSamples

            // Clear temp buffers for this render cycle
            for (i in 0 until samples) {
                tempLeft[i] = 0f
                tempRight[i] = 0f
                tempReverbLeft[i] = 0f
                tempReverbRight[i] = 0f
                tempChorusLeft[i] = 0f
                tempChorusRight[i] = 0f
            }

            activeCountResult = renderVoiceRange(
                startVoice, endVoice, file, tempLeft, tempRight, samples,
                channelVolumes, channelPitchBends, channelPans, channelExpressions,
                channelModulations, channelReverbSends, tempReverbLeft, tempReverbRight,
                channelChorusSends, tempChorusLeft, tempChorusRight, combinedGain
            )
        }

        fun shutdown() {
            running = false
            LockSupport.unpark(thread)
        }
    }

    /**
     * Allocates a voice for a new note.
     * Returns null if no voice is available (all active and can't be stolen).
     */
    fun allocateVoice(channel: Int): Sf2Voice? {
        // First, try to find an inactive voice
        for (voice in voices) {
            if (!voice.isActive) {
                voice.allocationOrder = voiceAllocationOrder++
                return voice
            }
        }

        // Enforce per-channel polyphony limits to avoid overload on busy channels
        val channelLimit = getChannelVoiceLimit(channel)
        if (channelLimit > 0) {
            val channelActiveCount = channelActiveSnapshot.getOrElse(channel) { 0 }
            if (channelActiveCount >= channelLimit) {
                val voice = stealVoice { it.channel == channel }
                voice?.allocationOrder = voiceAllocationOrder++
                return voice
            }
        }

        // All voices are active - try voice stealing
        val voice = stealVoice()
        voice?.allocationOrder = voiceAllocationOrder++
        return voice
    }

    /**
     * Steals the oldest/quietest voice for reuse.
     * Uses a combination of envelope stage, level, and velocity for stealing priority.
     * Heavily prefers voices that are already fading (in release stage).
     */
    private fun stealVoice(filter: (Sf2Voice) -> Boolean = { true }): Sf2Voice? {
        var bestCandidate: Sf2Voice? = null
        var bestScore = Float.NEGATIVE_INFINITY

        for (voice in voices) {
            if (!filter(voice)) continue
            // Calculate steal priority score (higher = better candidate for stealing)
            val score = calculateStealScore(voice)
            if (score > bestScore) {
                bestScore = score
                bestCandidate = voice
            }
        }

        // Stop the stolen voice using soft stop to prevent clicks
        // The voice's trigger() will apply a declick ramp to mask any remaining sound
        // Always use softStop - even voices in release can be audible
        bestCandidate?.softStop()
        return bestCandidate
    }

    /**
     * Calculates a score for voice stealing.
     * Higher score = better candidate for stealing.
     *
     * Priority factors (inspired by FluidSynth):
     * 1. Envelope stage: voices in release >> decay >> sustain >> attack
     * 2. Envelope level: quieter voices are less audible when cut
     * 3. Voice age: older voices have been playing longer, less likely to be noticed
     * 4. Velocity: lower velocity = quieter = less noticeable
     * 5. Channel type: melodic voices preferred over percussion (percussion is more noticeable)
     */
    private fun calculateStealScore(voice: Sf2Voice): Float {
        if (!voice.isActive) return Float.POSITIVE_INFINITY

        var score = 0f

        // Prefer voices already decaying/releasing (less audible to steal)
        score += when (voice.envelope.stage) {
            EnvelopeGenerator.Stage.RELEASE -> 4.5f
            EnvelopeGenerator.Stage.DECAY -> 2.5f
            EnvelopeGenerator.Stage.SUSTAIN -> 1.0f
            EnvelopeGenerator.Stage.HOLD -> 0.4f
            EnvelopeGenerator.Stage.ATTACK -> -1.5f
            else -> 0f
        }

        // Penalize loud voices (more audible if cut)
        val estimatedAmplitude = voice.getEstimatedAmplitude().coerceIn(0f, 1.5f)
        score += (1.5f - estimatedAmplitude) * 2.2f

        // Age bias: older voices are less noticeable to steal
        val age = (voiceAllocationOrder - voice.allocationOrder).coerceAtLeast(0)
        score += (age.coerceAtMost(2000).toFloat() / 2000f) * 1.8f

        // Lower velocity = safer to steal
        score += ((127 - voice.velocity).toFloat() / 127f) * 1.2f

        // Avoid stealing percussion (transients are noticeable)
        if (voice.channel == PERCUSSION_CHANNEL) {
            score -= 1.6f
        }

        return score
    }

    private fun getChannelVoiceLimit(channel: Int): Int {
        return if (channel == PERCUSSION_CHANNEL) {
            PERCUSSION_CHANNEL_VOICE_LIMIT
        } else {
            DEFAULT_CHANNEL_VOICE_LIMIT
        }
    }

    /**
     * Finds all voices matching the given channel and note.
     */
    @Suppress("unused")
    fun findVoices(channel: Int, note: Int): List<Sf2Voice> {
        return voices.filter { it.matches(channel, note) }
    }

    /**
     * Releases all voices matching the given channel and note.
     */
    fun releaseVoices(channel: Int, note: Int) {
        for (voice in voices) {
            if (voice.matches(channel, note)) {
                voice.release()
            }
        }
    }

    /**
     * Kills voices in the same exclusive class on the same channel.
     * Uses soft stop for a clean fadeout (e.g., hi-hat chokes).
     */
    fun killExclusiveClass(exclusiveClass: Int, channel: Int) {
        if (exclusiveClass == 0) return

        for (voice in voices) {
            if (voice.shouldBeKilledByExclusiveClass(exclusiveClass, channel)) {
                // Use soft stop for smooth choke (natural for drums like hi-hat)
                voice.softStop()
            }
        }
    }

    /**
     * Releases all voices on the given channel (All Notes Off).
     */
    fun releaseChannel(channel: Int) {
        for (voice in voices) {
            if (voice.isActive && voice.channel == channel) {
                voice.release()
            }
        }
    }

    /**
     * Immediately stops all voices on the given channel (All Sound Off).
     */
    fun stopChannel(channel: Int) {
        for (voice in voices) {
            if (voice.isActive && voice.channel == channel) {
                voice.stop()
            }
        }
    }

    /**
     * Releases all active voices.
     */
    fun releaseAll() {
        for (voice in voices) {
            if (voice.isActive) {
                voice.release()
            }
        }
    }

    /**
     * Immediately stops all voices.
     */
    fun stopAll() {
        for (voice in voices) {
            voice.stop()
        }
        activeVoiceCount = 0
    }

    /**
     * Renders audio from all active voices.
     * @param sf2File The SF2 file containing sample data
     * @param outputLeft Left channel output buffer
     * @param outputRight Right channel output buffer
     * @param numSamples Number of samples to render
     * @param channelVolumes Array of 16 channel volumes (0.0-1.0), or null for default (1.0)
     * @param channelPitchBends Array of 16 pitch bend values in semitones (-2 to +2), or null for no bend
     * @param channelPans Array of 16 channel pan values (-1.0 to +1.0), or null for center
     * @param channelExpressions Array of 16 channel expression values (0.0-1.0), or null for default (1.0)
     * @param channelModulations Array of 16 channel modulation values (0.0-1.0), or null for no modulation
     * @param channelReverbSends Array of 16 channel reverb send levels (0.0-1.0), or null for default
     * @param reverbSendLeft Left channel reverb send buffer to fill, or null to skip
     * @param reverbSendRight Right channel reverb send buffer to fill, or null to skip
     * @param channelChorusSends Array of 16 channel chorus send levels (0.0-1.0), or null for default
     * @param chorusSendLeft Left channel chorus send buffer to fill, or null to skip
     * @param chorusSendRight Right channel chorus send buffer to fill, or null to skip
     */
    fun render(
        sf2File: Sf2File,
        outputLeft: FloatArray,
        outputRight: FloatArray,
        numSamples: Int,
        channelVolumes: FloatArray? = null,
        channelPitchBends: FloatArray? = null,
        channelPans: FloatArray? = null,
        channelExpressions: FloatArray? = null,
        channelModulations: FloatArray? = null,
        channelReverbSends: FloatArray? = null,
        reverbSendLeft: FloatArray? = null,
        reverbSendRight: FloatArray? = null,
        channelChorusSends: FloatArray? = null,
        chorusSendLeft: FloatArray? = null,
        chorusSendRight: FloatArray? = null,
        masterGain: Float = 1f
    ) {
        var count = 0
        // Reuse pre-allocated buffer (zero it first)
        val channelCounts = channelCountsBuffer
        channelCounts.fill(0)

        // Count active voices first to determine quality mode
        for (voice in voices) {
            if (voice.isActive) {
                count++
                val channelIndex = voice.channel
                if (channelIndex in 0 until NUM_CHANNELS) {
                    channelCounts[channelIndex]++
                }
            }
        }

        // Enable low quality mode when voice count is high (automatic CPU management)
        // This switches to linear interpolation which is ~2x faster
        // Hysteresis: enter at threshold, exit 10 voices below to prevent rapid toggling
        // (rapid toggling between cubic/linear interpolation causes subtle artifacts)
        Sf2Voice.lowQualityMode = if (Sf2Voice.lowQualityMode) {
            count > (Sf2Voice.LOW_QUALITY_THRESHOLD - 10)
        } else {
            count > Sf2Voice.LOW_QUALITY_THRESHOLD
        }

        // Enable ultra low quality mode when voice count is very high
        // Hysteresis: enter at threshold, exit 15 voices below to prevent rapid toggling
        // (toggling quality modes at block rate was a major cause of crackling on
        // filtered instruments like pads, where voice count oscillates around threshold)
        Sf2Voice.ultraLowQualityMode = if (Sf2Voice.ultraLowQualityMode) {
            count > (Sf2Voice.ULTRA_LOW_QUALITY_THRESHOLD - 15)
        } else {
            count > Sf2Voice.ULTRA_LOW_QUALITY_THRESHOLD
        }

        // Enable release cap when voice count is very high
        // This forces faster release times to prevent voice buildup (Rush E scenario)
        // Hysteresis: enter at threshold, exit 12 below to prevent oscillating
        // between full release (2s) and capped release (80ms) — which itself causes artifacts
        Sf2Voice.releaseCapped = if (Sf2Voice.releaseCapped) {
            count > (RELEASE_CAP_THRESHOLD - 12)
        } else {
            count > RELEASE_CAP_THRESHOLD
        }
        Sf2Voice.cappedReleaseSamples = CAPPED_RELEASE_SAMPLES

        // EMERGENCY MODE: When approaching max voices, force quickFade on voices ALREADY in release
        // This is critical for resonant sounds (bells, pads) that accumulate despite release cap
        // The release cap only affects NEW noteOffs, this handles voices already releasing
        if (count > EMERGENCY_THRESHOLD) {
            var releasingCount = 0
            for (voice in voices) {
                if (voice.isActive && voice.envelope.stage == EnvelopeGenerator.Stage.RELEASE) {
                    releasingCount++
                    // Force emergency fade on voices that have been releasing for a while
                    // This quickly clears out old resonating sounds
                    voice.forceEmergencyFade(EMERGENCY_FADE_SAMPLES)
                }
            }
        }

        // Calculate voice count-based gain reduction to prevent clipping
        // When many voices play together, reduce each voice's contribution
        // Using cube root (pow 1/3) for a gentler reduction curve than sqrt
        // This preserves more dynamic range while still preventing clipping
        // BUG FIX 1.7: Vérifier count > 0 avant division pour éviter division par zéro
        val targetVoiceGain = when {
            count <= 0 -> 1f  // Pas de voix actives, gain par défaut
            count > GAIN_REDUCTION_THRESHOLD -> {
                // cbrt(threshold / count) gives a gentler reduction than sqrt:
                // e.g., 64 voices: cbrt(48/64) = 0.909 (-0.8dB)  vs sqrt: 0.866 (-1.3dB)
                // e.g., 96 voices: cbrt(48/96) = 0.794 (-2.0dB)  vs sqrt: 0.707 (-3.0dB)
                // e.g., 128 voices: cbrt(48/128) = 0.721 (-2.8dB) vs sqrt: 0.612 (-4.3dB)
                val ratio = GAIN_REDUCTION_THRESHOLD.toFloat() / count
                maxOf(MIN_VOICE_GAIN, Math.cbrt(ratio.toDouble()).toFloat())
            }
            count in 1 until GAIN_BOOST_THRESHOLD -> {
                // Boost solo/sparse passages to compensate for conservative master gain.
                // Symmetric to the reduction curve: cbrt(threshold / count) but capped.
                // e.g., 1 voice: capped at 2.0 (+6dB), 4 voices: 1.59 (+4dB),
                //        8 voices: 1.26 (+2dB), 12 voices: 1.10 (+0.8dB)
                val ratio = GAIN_BOOST_THRESHOLD.toFloat() / count
                minOf(MAX_VOICE_BOOST, Math.cbrt(ratio.toDouble()).toFloat())
            }
            else -> 1f
        }

        // Smooth the voice gain to prevent volume jumps when voice count changes
        // Fast attack (gain going DOWN) to prevent clipping
        // Slow release (gain going UP) to prevent volume jumps when notes release
        val coeff = if (targetVoiceGain < smoothedVoiceGain) GAIN_ATTACK_COEFF else GAIN_RELEASE_COEFF
        smoothedVoiceGain += (targetVoiceGain - smoothedVoiceGain) * coeff

        // Render all active voices with the smoothed gain multiplier
        // Pre-compute combined gain for thread-safe access by worker threads
        val combinedGain = smoothedVoiceGain * masterGain

        // Hysteresis for parallel/sequential mode switching to prevent oscillation
        // when voice count hovers around the threshold (causes audible mode-switch artifacts)
        // forceSequentialRendering désactive totalement le parallélisme (ex: écran éteint).
        useParallelRendering = if (forceSequentialRendering) {
            false
        } else if (useParallelRendering) {
            count > (PARALLEL_THRESHOLD - 8)
        } else {
            count >= PARALLEL_THRESHOLD
        }

        val finalCount = if (useParallelRendering && workerCount > 0 && numSamples <= MAX_BUFFER_SIZE) {
            renderVoicesParallel(
                sf2File, outputLeft, outputRight, numSamples,
                channelVolumes, channelPitchBends, channelPans, channelExpressions,
                channelModulations, channelReverbSends, reverbSendLeft, reverbSendRight,
                channelChorusSends, chorusSendLeft, chorusSendRight, combinedGain
            )
        } else {
            renderVoiceRange(
                0, maxVoices, sf2File, outputLeft, outputRight, numSamples,
                channelVolumes, channelPitchBends, channelPans, channelExpressions,
                channelModulations, channelReverbSends, reverbSendLeft, reverbSendRight,
                channelChorusSends, chorusSendLeft, chorusSendRight, combinedGain
            )
        }

        activeVoiceCount = finalCount
        if (finalCount > peakVoiceCount) {
            peakVoiceCount = finalCount
        }
        // Double-buffered snapshot update: copy counts into inactive buffer, then swap.
        // This prevents allocateVoice() from reading a buffer that's being cleared/filled.
        val snapTarget = if (channelActiveSnapshot === channelActiveSnapA) channelActiveSnapB else channelActiveSnapA
        channelCounts.copyInto(snapTarget)
        channelActiveSnapshot = snapTarget
    }

    // ==================== Parallel Rendering ====================

    /**
     * Renders a range of voices into the given output buffers.
     * Thread-safe: each voice has independent state, sf2File.getSample() is read-only.
     * @return Number of voices still active after rendering
     */
    private fun renderVoiceRange(
        startIndex: Int,
        endIndex: Int,
        sf2File: Sf2File,
        outputLeft: FloatArray,
        outputRight: FloatArray,
        numSamples: Int,
        channelVolumes: FloatArray?,
        channelPitchBends: FloatArray?,
        channelPans: FloatArray?,
        channelExpressions: FloatArray?,
        channelModulations: FloatArray?,
        channelReverbSends: FloatArray?,
        reverbSendLeft: FloatArray?,
        reverbSendRight: FloatArray?,
        channelChorusSends: FloatArray?,
        chorusSendLeft: FloatArray?,
        chorusSendRight: FloatArray?,
        combinedGain: Float
    ): Int {
        var activeCount = 0
        for (v in startIndex until endIndex) {
            val voice = voices[v]
            if (voice.isActive) {
                val expression = channelExpressions?.getOrElse(voice.channel) { 1f } ?: 1f
                val channelVolume = (channelVolumes?.getOrElse(voice.channel) { 1f } ?: 1f) * expression * combinedGain
                val pitchBend = channelPitchBends?.getOrElse(voice.channel) { 0f } ?: 0f
                val channelPan = channelPans?.getOrElse(voice.channel) { 0f } ?: 0f
                val modulation = channelModulations?.getOrElse(voice.channel) { 0f } ?: 0f
                val reverbSend = channelReverbSends?.getOrElse(voice.channel) { 0.4f } ?: 0f
                val chorusSend = channelChorusSends?.getOrElse(voice.channel) { 0f } ?: 0f

                voice.render(sf2File, outputLeft, outputRight, numSamples, channelVolume, pitchBend, channelPan, modulation, reverbSendLeft, reverbSendRight, reverbSend, chorusSendLeft, chorusSendRight, chorusSend)

                if (voice.isActive) {
                    activeCount++
                }
            }
        }
        return activeCount
    }

    /**
     * Renders voices in parallel across worker threads + main thread.
     * Voice indices are distributed evenly: workers render into temp buffers,
     * main thread renders directly into output, then accumulates worker results.
     * @return Number of voices still active after rendering
     */
    private fun renderVoicesParallel(
        sf2File: Sf2File,
        outputLeft: FloatArray,
        outputRight: FloatArray,
        numSamples: Int,
        channelVolumes: FloatArray?,
        channelPitchBends: FloatArray?,
        channelPans: FloatArray?,
        channelExpressions: FloatArray?,
        channelModulations: FloatArray?,
        channelReverbSends: FloatArray?,
        reverbSendLeft: FloatArray?,
        reverbSendRight: FloatArray?,
        channelChorusSends: FloatArray?,
        chorusSendLeft: FloatArray?,
        chorusSendRight: FloatArray?,
        combinedGain: Float
    ): Int {
        val activeWorkers = ensureWorkers()
        val actualWorkerCount = activeWorkers.size
        val totalThreads = actualWorkerCount + 1 // workers + main thread
        val chunkSize = maxVoices / totalThreads

        mainRenderThread = Thread.currentThread()

        // Configure and start workers
        // Track which workers are healthy (completed previous cycle in time)
        // Utilise le tableau pré-alloué pour éviter une allocation par cycle de rendu.
        val workerHealthy = workerHealthyBuffer
        workerHealthy.fill(true, 0, actualWorkerCount)

        for (w in 0 until actualWorkerCount) {
            val worker = activeWorkers[w]
            // CRITICAL: Wait for previous cycle to complete before overwriting parameters.
            // Without this, we corrupt worker state if the previous cycle was slow
            // (e.g., worker still rendering from a timed-out cycle N while we assign cycle N+1).
            // Timeout: 5ms max wait. If worker is still busy, mark it unhealthy and skip.
            val waitStart = System.nanoTime()
            val maxWaitNs = 5_000_000L // 5ms - half a typical audio buffer period
            while (!worker.renderComplete) {
                if (System.nanoTime() - waitStart > maxWaitNs) {
                    // Worker is stuck from previous cycle - mark unhealthy, don't assign new work
                    workerHealthy[w] = false
                    android.util.Log.w("Sf2VoicePool", "Worker $w stuck from previous cycle, skipping")
                    break
                }
                LockSupport.parkNanos(50_000) // 50us
            }

            // Only assign work to healthy workers
            if (!workerHealthy[w]) continue

            worker.startVoice = w * chunkSize
            worker.endVoice = (w + 1) * chunkSize
            worker.numSamples = numSamples
            worker.sf2FileRef = sf2File
            worker.channelVolumes = channelVolumes
            worker.channelPitchBends = channelPitchBends
            worker.channelPans = channelPans
            worker.channelExpressions = channelExpressions
            worker.channelModulations = channelModulations
            worker.channelReverbSends = channelReverbSends
            worker.channelChorusSends = channelChorusSends
            worker.combinedGain = combinedGain
            worker.renderComplete = false
            LockSupport.unpark(worker.thread)
        }

        // Main thread renders its chunk directly into output buffers
        // PLUS: render voice ranges from unhealthy workers (fallback to sequential)
        val mainStart = actualWorkerCount * chunkSize
        var mainActiveCount = renderVoiceRange(
            mainStart, maxVoices, sf2File, outputLeft, outputRight, numSamples,
            channelVolumes, channelPitchBends, channelPans, channelExpressions,
            channelModulations, channelReverbSends, reverbSendLeft, reverbSendRight,
            channelChorusSends, chorusSendLeft, chorusSendRight, combinedGain
        )

        // Render voice ranges from unhealthy workers on main thread (fallback)
        // This ensures no voices are dropped even if workers are stuck
        for (w in 0 until actualWorkerCount) {
            if (!workerHealthy[w]) {
                val fallbackStart = w * chunkSize
                val fallbackEnd = (w + 1) * chunkSize
                mainActiveCount += renderVoiceRange(
                    fallbackStart, fallbackEnd, sf2File, outputLeft, outputRight, numSamples,
                    channelVolumes, channelPitchBends, channelPans, channelExpressions,
                    channelModulations, channelReverbSends, reverbSendLeft, reverbSendRight,
                    channelChorusSends, chorusSendLeft, chorusSendRight, combinedGain
                )
            }
        }

        // Wait for healthy workers to complete — never drop worker audio.
        // Dropping results from timed-out workers was the primary cause of crackling:
        // entire voice ranges would silently disappear for one buffer (~10ms gap).
        // Workers call LockSupport.unpark(mainRenderThread) on completion,
        // so we are woken promptly. Safety timeout (50ms) only prevents infinite hang
        // if a worker thread dies unexpectedly.
        val deadline = System.nanoTime() + 50_000_000L // 50ms safety net
        while (System.nanoTime() < deadline) {
            var allDone = true
            for (w in 0 until actualWorkerCount) {
                // Only wait for healthy workers that were assigned work
                if (workerHealthy[w] && !activeWorkers[w].renderComplete) {
                    allDone = false
                    break
                }
            }
            if (allDone) break
            // Workers unpark us on completion; parkNanos as fallback for missed unparks
            LockSupport.parkNanos(100_000) // 100us
        }

        // Accumulate worker temp buffers into output
        // Only accumulate from healthy workers that actually completed
        val reverbLeft = reverbSendLeft
        val reverbRight = reverbSendRight
        val chorusLeft = chorusSendLeft
        val chorusRight = chorusSendRight
        val hasReverb = reverbLeft != null && reverbRight != null
        val hasChorus = chorusLeft != null && chorusRight != null
        var totalActiveCount = mainActiveCount

        for (w in 0 until actualWorkerCount) {
            // Skip unhealthy workers (their range was rendered by main thread)
            if (!workerHealthy[w]) continue

            val worker = activeWorkers[w]
            if (!worker.renderComplete) {
                // Worker timed out during this cycle - log but don't crash
                // Their voice range will be silent for this buffer (unavoidable)
                android.util.Log.w("Sf2VoicePool", "Worker $w timed out during render, voices ${w * chunkSize}-${(w+1) * chunkSize} silent")
                continue
            }
            totalActiveCount += worker.activeCountResult

            for (i in 0 until numSamples) {
                outputLeft[i] += worker.tempLeft[i]
                outputRight[i] += worker.tempRight[i]
            }

            if (hasReverb) {
                for (i in 0 until numSamples) {
                    reverbLeft!![i] += worker.tempReverbLeft[i]
                    reverbRight!![i] += worker.tempReverbRight[i]
                }
            }

            if (hasChorus) {
                for (i in 0 until numSamples) {
                    chorusLeft!![i] += worker.tempChorusLeft[i]
                    chorusRight!![i] += worker.tempChorusRight[i]
                }
            }
        }

        return totalActiveCount
    }

    /**
     * Shuts down all render worker threads.
     * Call this when the synthesizer is being released.
     */
    fun shutdown() {
        val activeWorkers = workers ?: return
        // Signaler l'arrêt à tous les workers
        for (worker in activeWorkers) {
            worker.shutdown()
        }
        // BUG FIX 3.13: Timeout allonge a 1000ms et tentative de force-stop pour workers recalcitrants
        for (worker in activeWorkers) {
            try {
                worker.thread.join(1000)
                if (worker.thread.isAlive) {
                    android.util.Log.w("Sf2VoicePool", "Worker thread ${worker.index} did not stop in time, forcing interrupt")
                    // Tentative supplementaire d'interruption
                    worker.thread.interrupt()
                    try {
                        worker.thread.join(500)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    if (worker.thread.isAlive) {
                        android.util.Log.e("Sf2VoicePool", "Worker thread ${worker.index} is ZOMBIE - cannot stop")
                    }
                }
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }
        workers = null
        mainRenderThread = null
    }

    /**
     * Gets all active voices (for debugging/visualization).
     */
    @Suppress("unused")
    fun getActiveVoices(): List<Sf2Voice> {
        return voices.filter { it.isActive }
    }

    /**
     * Resets the voice pool.
     */
    fun reset() {
        for (voice in voices) {
            voice.reset()
        }
        activeVoiceCount = 0
        voiceAllocationOrder = 0
        smoothedVoiceGain = 1f
        useParallelRendering = false
    }

    /**
     * Resets peak voice count statistic.
     */
    @Suppress("unused")
    fun resetPeakCount() {
        peakVoiceCount = activeVoiceCount
    }

    /**
     * Returns statistics about the voice pool.
     */
    fun getStats(): VoicePoolStats {
        var releasing = 0
        var sustaining = 0
        var attacking = 0

        for (voice in voices) {
            if (!voice.isActive) continue
            when (voice.envelope.stage) {
                EnvelopeGenerator.Stage.RELEASE -> releasing++
                EnvelopeGenerator.Stage.SUSTAIN -> sustaining++
                EnvelopeGenerator.Stage.ATTACK,
                EnvelopeGenerator.Stage.DECAY -> attacking++
                else -> {}
            }
        }

        return VoicePoolStats(
            totalVoices = maxVoices,
            activeVoices = activeVoiceCount,
            peakVoices = peakVoiceCount,
            attackingVoices = attacking,
            sustainingVoices = sustaining,
            releasingVoices = releasing
        )
    }
}

/**
 * Statistics about the voice pool state.
 */
data class VoicePoolStats(
    val totalVoices: Int,
    val activeVoices: Int,
    val peakVoices: Int,
    val attackingVoices: Int,
    val sustainingVoices: Int,
    val releasingVoices: Int
) {
    override fun toString(): String {
        return "Voices: $activeVoices/$totalVoices (peak: $peakVoices) " +
                "[A:$attackingVoices S:$sustainingVoices R:$releasingVoices]"
    }
}
