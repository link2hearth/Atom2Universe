package com.Atom2Universe.app.midi.sf2

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.tanh

/**
 * SF2 Synthesizer - Main synthesis engine.
 *
 * Handles MIDI events and renders audio using the SF2 file data.
 * Supports 16 MIDI channels with per-channel program and control state.
 *
 * Thread-safety: This class is thread-safe. The render() method is called from
 * the audio thread, while MIDI events (noteOn, noteOff, etc.) are called from
 * the playback thread. Internal synchronization ensures safe concurrent access.
 */
class Sf2Synthesizer(
    private val sf2File: Sf2File,
    val sampleRate: Int = 44100,
    maxVoices: Int = 128
) {
    companion object {
        // MIDI Control Change numbers
        const val CC_BANK_SELECT_MSB = 0
        const val CC_MODULATION = 1
        const val CC_VOLUME = 7
        const val CC_PAN = 10
        const val CC_EXPRESSION = 11
        const val CC_BANK_SELECT_LSB = 32
        const val CC_DATA_ENTRY_MSB = 6
        const val CC_DATA_ENTRY_LSB = 38
        const val CC_SUSTAIN_PEDAL = 64
        const val CC_REVERB = 91
        const val CC_CHORUS = 93
        const val CC_RPN_LSB = 100
        const val CC_RPN_MSB = 101
        const val CC_ALL_SOUND_OFF = 120
        const val CC_RESET_ALL_CONTROLLERS = 121
        const val CC_ALL_NOTES_OFF = 123

        // Default pitch bend range in semitones
        const val DEFAULT_PITCH_BEND_RANGE = 2

        // Number of MIDI channels
        const val NUM_CHANNELS = 16

        // Percussion channel (channel 10 in MIDI is index 9)
        const val PERCUSSION_CHANNEL = 9

        // Maximum sustained notes per channel before we start releasing oldest ones
        // Prevents sustain pedal from causing infinite voice buildup
        private const val MAX_SUSTAINED_NOTES_PER_CHANNEL = 24
    }

    // Voice pool (thread-safe access via voiceLock)
    private val voicePool = Sf2VoicePool(maxVoices, sampleRate)

    // Lock for voice pool and channel state access
    private val voiceLock = Any()

    // Flag to prevent operations during reset
    private val isResetting = AtomicBoolean(false)

    // Audio limiter for preventing clipping and balancing levels
    private val limiter = AudioLimiter()

    // EQ 10 bandes post-limiter
    private val equalizer = MidiEqualizerEngine(sampleRate)

    // Optional mix-bus saturation before limiter for gentle musical warmth
    @Volatile
    var busSaturationEnabled: Boolean = false
        private set

    @Volatile
    var busSaturationDrive: Float = 1.15f
        private set

    // Reverb effect (can be disabled for CPU savings)
    private val reverb = Reverb(sampleRate)

    // Pre-allocated buffers for per-channel reverb send mixing
    // These are used to separate dry and wet signal paths
    private val reverbSendLeft = FloatArray(1024)
    private val reverbSendRight = FloatArray(1024)

    // Chorus effect (can be disabled for CPU savings)
    private val chorus = Chorus(sampleRate)

    // Pre-allocated buffers for per-channel chorus send mixing
    private val chorusSendLeft = FloatArray(1024)
    private val chorusSendRight = FloatArray(1024)

    // Per-channel state (accessed under voiceLock)
    private val channelProgram = IntArray(NUM_CHANNELS) { 0 }
    private val channelBank = IntArray(NUM_CHANNELS) { 0 }
    private val channelVolume = FloatArray(NUM_CHANNELS) { 1f }
    private val channelPan = FloatArray(NUM_CHANNELS) { 0f }
    private val channelExpression = FloatArray(NUM_CHANNELS) { 1f }
    private val channelSustain = BooleanArray(NUM_CHANNELS) { false }
    private val channelPitchBend = FloatArray(NUM_CHANNELS) { 0f }
    private val channelModulation = FloatArray(NUM_CHANNELS) { 0f }
    private val channelReverbSend = FloatArray(NUM_CHANNELS) { 0.4f }  // Default 40% reverb send
    private val channelChorusSend = FloatArray(NUM_CHANNELS) { 0f }  // Default 0% chorus send

    // Smoothed controller values (anti-zipper for volume/pan/expression)
    private val smoothedChannelVolume = FloatArray(NUM_CHANNELS) { 1f }
    private val smoothedChannelPan = FloatArray(NUM_CHANNELS) { 0f }
    private val smoothedChannelExpression = FloatArray(NUM_CHANNELS) { 1f }

    // BUG FIX 1.10: Double-buffered snapshots avec AtomicReference pour swap atomique.
    // Chaque snapshot a deux buffers pre-alloues (A/B). Le thread MIDI copie dans le
    // buffer inactif, puis utilise AtomicReference.set() pour un swap atomique.
    // Le thread audio utilise AtomicReference.get() pour lire la derniere version
    // avec une barriere memoire appropriee.
    // Cela garantit que le thread audio ne lira jamais un buffer partiellement copie.
    private val channelVolumeSnapA = FloatArray(NUM_CHANNELS) { 1f }
    private val channelVolumeSnapB = FloatArray(NUM_CHANNELS) { 1f }
    private val channelVolumeSnapshotRef = AtomicReference(channelVolumeSnapA)

    private val channelPitchBendSnapA = FloatArray(NUM_CHANNELS) { 0f }
    private val channelPitchBendSnapB = FloatArray(NUM_CHANNELS) { 0f }
    private val channelPitchBendSnapshotRef = AtomicReference(channelPitchBendSnapA)

    private val channelPanSnapA = FloatArray(NUM_CHANNELS) { 0f }
    private val channelPanSnapB = FloatArray(NUM_CHANNELS) { 0f }
    private val channelPanSnapshotRef = AtomicReference(channelPanSnapA)

    private val channelExpressionSnapA = FloatArray(NUM_CHANNELS) { 1f }
    private val channelExpressionSnapB = FloatArray(NUM_CHANNELS) { 1f }
    private val channelExpressionSnapshotRef = AtomicReference(channelExpressionSnapA)

    private val channelModulationSnapA = FloatArray(NUM_CHANNELS) { 0f }
    private val channelModulationSnapB = FloatArray(NUM_CHANNELS) { 0f }
    private val channelModulationSnapshotRef = AtomicReference(channelModulationSnapA)

    private val channelReverbSendSnapA = FloatArray(NUM_CHANNELS) { 0.4f }
    private val channelReverbSendSnapB = FloatArray(NUM_CHANNELS) { 0.4f }
    private val channelReverbSendSnapshotRef = AtomicReference(channelReverbSendSnapA)

    private val channelChorusSendSnapA = FloatArray(NUM_CHANNELS) { 0f }
    private val channelChorusSendSnapB = FloatArray(NUM_CHANNELS) { 0f }
    private val channelChorusSendSnapshotRef = AtomicReference(channelChorusSendSnapA)

    // Auto-activation flags for FX based on CC91/93 (avoids overriding manual settings)
    private var reverbAutoEnabled = false
    private var chorusAutoEnabled = false
    private var reverbAutoAllowed = false
    private var chorusAutoAllowed = false

    // Per-channel pitch bend range in semitones (default ±2, configurable via RPN 0)
    private val channelPitchBendRange = IntArray(NUM_CHANNELS) { DEFAULT_PITCH_BEND_RANGE }

    // RPN (Registered Parameter Number) state machine per channel
    // RPN is set by CC101 (MSB) then CC100 (LSB), then CC6/CC38 set the value
    private val channelRpnMsb = IntArray(NUM_CHANNELS) { 127 }  // 127 = null/unset
    private val channelRpnLsb = IntArray(NUM_CHANNELS) { 127 }

    // Master volume (0.0 - 1.0) - volatile for thread-safe access
    @Volatile var masterVolume: Float = 1f

    // Global gain applied to all voices before mixing (inspired by FluidSynth's default of 0.2)
    // This provides ~12dB of headroom for polyphonic mixing, so the AudioLimiter
    // acts as a safety net rather than being constantly engaged.
    // Default 0.35 : compromis entre les SF2 silencieux et les SF2 forts
    // Can be adjusted per-SF2: quiet SF2s benefit from higher gain (0.35-0.50),
    // loud SF2s may need lower gain (0.15-0.20).
    @Volatile
    var globalGain: Float = 0.35f
        set(value) { field = value.coerceIn(0.05f, 1.0f) }

    // Sustained notes (notes held by sustain pedal)
    // Using LinkedHashSet to maintain insertion order for oldest-first release
    private val sustainedNotes = Array(NUM_CHANNELS) { linkedSetOf<Int>() }

    // Rendering protection: prevents concurrent voice modification during parallel render.
    // When isRendering is true, external MIDI events (from UI/hybrid threads) are queued
    // and processed at the start of the next render cycle. Internal events from
    // processMidiEventsForBuffer are dispatched BEFORE render starts, so they are unaffected.
    // BUG FIX 3.7: Utiliser AtomicBoolean au lieu de volatile pour les opérations atomiques
    private val isRendering = AtomicBoolean(false)

    // Queue for external MIDI events received during rendering
    // Uses ConcurrentLinkedQueue for lock-free thread-safe access
    // Limited to MAX_PENDING_EVENTS to prevent unbounded memory growth if render is blocked
    private data class QueuedMidiEvent(
        val type: Int, // 0=noteOn, 1=noteOff, 2=programChange, 3=controlChange, 4=pitchBend
        val channel: Int,
        val param1: Int,
        val param2: Int = 0
    )
    private val pendingExternalEvents = java.util.concurrent.ConcurrentLinkedQueue<QueuedMidiEvent>()
    private val MAX_PENDING_EVENTS = 1000 // Limite pour éviter OutOfMemory si render bloqué
    // BUG FIX 1.6: Utiliser AtomicInteger pour le comptage atomique au lieu de .size()
    // ConcurrentLinkedQueue.size() traverse toute la queue (O(n)) et n'est pas atomique
    private val pendingEventCount = java.util.concurrent.atomic.AtomicInteger(0)

    /**
     * BUG FIX 1.10: Swaps a double-buffered snapshot avec AtomicReference.
     * Copie source dans le buffer inactif, puis met a jour atomiquement la reference.
     * Zero allocations - les deux buffers sont pre-alloues a l'init.
     * Le swap atomique garantit que le thread audio ne lira jamais un buffer partiellement copie.
     */
    private fun swapSnapshot(
        source: FloatArray,
        ref: AtomicReference<FloatArray>,
        bufA: FloatArray,
        bufB: FloatArray
    ) {
        val current = ref.get()
        val target = if (current === bufA) bufB else bufA
        source.copyInto(target)
        ref.set(target)  // Swap atomique avec barriere memoire
    }

    private fun updateFxAutoState(
        sendLevels: FloatArray,
        isEnabled: Boolean,
        wasAutoEnabled: Boolean,
        onEnable: () -> Unit,
        onDisable: () -> Unit
    ): Boolean {
        val hasAnySend = sendLevels.any { it > 0f }
        return when {
            hasAnySend && !isEnabled -> {
                onEnable()
                true
            }
            !hasAnySend && wasAutoEnabled && isEnabled -> {
                onDisable()
                false
            }
            else -> wasAutoEnabled
        }
    }

    init {
        // Set channel 10 (index 9) to percussion bank
        channelBank[PERCUSSION_CHANNEL] = Sf2Preset.PERCUSSION_BANK

        // BUG FIX: Désactiver l'auto-gain du limiter (il compresse trop et étouffe les percussions)
        // On garde le peak limiting pour éviter le clipping, mais sans compression dynamique
        limiter.autoGainEnabled = false
    }

    // ==================== MIDI Event Handlers ====================

    /**
     * Handles a Note On event. Thread-safe.
     */
    fun noteOn(channel: Int, note: Int, velocity: Int) {
        if (channel !in 0 until NUM_CHANNELS) return
        if (isResetting.get()) return  // Skip during reset
        // BUG FIX 3.7: Utiliser AtomicBoolean.get() pour lecture thread-safe
        if (isRendering.get()) {
            // BUG FIX 1.6: Utiliser AtomicInteger pour comptage atomique
            // Limite pour éviter croissance mémoire infinie si render bloqué
            if (pendingEventCount.get() < MAX_PENDING_EVENTS) {
                pendingExternalEvents.add(QueuedMidiEvent(0, channel, note, velocity))
                pendingEventCount.incrementAndGet()
            }
            return
        }

        // Validate MIDI values to prevent garbage data from causing issues
        val safeNote = note.coerceIn(0, 127)
        val safeVelocity = velocity.coerceIn(0, 127)

        if (safeVelocity == 0) {
            // Velocity 0 = Note Off
            noteOff(channel, safeNote)
            return
        }

        val bank: Int
        val program: Int
        synchronized(voiceLock) {
            bank = channelBank[channel]
            program = channelProgram[channel]
        }

        // Find matching regions for this note (outside lock - read-only on sf2File)
        val regions = sf2File.getRegions(bank, program, safeNote, safeVelocity)

        if (regions.isNotEmpty()) {
            triggerRegions(channel, safeNote, safeVelocity, regions)
            return
        }

        // Fallback logic for when no regions found
        val fallbackRegions = findFallbackRegions(channel, bank, program, safeNote, safeVelocity)
        if (fallbackRegions.isNotEmpty()) {
            triggerRegions(channel, safeNote, safeVelocity, fallbackRegions)
        }
        // Note: We silently ignore notes with no regions to reduce log spam
    }

    /**
     * Finds fallback regions when the primary lookup fails.
     * Implements multiple fallback strategies especially for percussion.
     */
    private fun findFallbackRegions(channel: Int, bank: Int, program: Int, note: Int, velocity: Int): List<Sf2Region> {
        // For percussion channel, try multiple fallback strategies
        if (channel == PERCUSSION_CHANNEL) {
            // Strategy 1: Try bank 128, program 0 (Standard Kit) - most common location
            var regions = sf2File.getRegions(128, 0, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 2: Try bank 128, program 1 (Room Kit)
            regions = sf2File.getRegions(128, 1, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 3: Try bank 128, program 8 (Room Kit alternate)
            regions = sf2File.getRegions(128, 8, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 4: Try bank 128, program 16 (Power Kit)
            regions = sf2File.getRegions(128, 16, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 5: Try bank 128, program 24 (Electronic Kit)
            regions = sf2File.getRegions(128, 24, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 6: Try bank 128, program 25 (TR-808 Kit)
            regions = sf2File.getRegions(128, 25, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 7: Try bank 128, program 32 (Jazz Kit)
            regions = sf2File.getRegions(128, 32, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 8: Try bank 128, program 40 (Brush Kit)
            regions = sf2File.getRegions(128, 40, note, velocity)
            if (regions.isNotEmpty()) return regions

            // Strategy 9: Try bank 0, program 0 (some SF2s put drums here)
            regions = sf2File.getRegions(0, 0, note, velocity)
            if (regions.isNotEmpty()) return regions

            // No percussion found
            if (!loggedMissingPercussion.contains(note)) {
                loggedMissingPercussion.add(note)
            }
            return emptyList()
        }

        // For melodic channels, try bank 0 with same program
        if (bank != 0) {
            val regions = sf2File.getRegions(0, program, note, velocity)
            if (regions.isNotEmpty()) {
                // Log once that we're using fallback bank
                if (!loggedBankFallback.contains("$channel:$bank:$program")) {
                    loggedBankFallback.add("$channel:$bank:$program")
                }
                return regions
            }
        }

        // Try bank 0, program 0 (piano) as last resort
        val regions = sf2File.getRegions(0, 0, note, velocity)
        if (regions.isNotEmpty()) {
            if (!loggedPianoFallback.contains("$channel:$bank:$program")) {
                loggedPianoFallback.add("$channel:$bank:$program")
            }
            return regions
        }

        // Log missing melodic instrument - THIS IS LIKELY THE PROBLEM
        if (!loggedMissingInstruments.contains("$bank:$program")) {
            loggedMissingInstruments.add("$bank:$program")
        }
        missingNoteCount++
        return emptyList()
    }

    // Track logged warnings to reduce spam
    private val loggedMissingPercussion = mutableSetOf<Int>()
    private val loggedMissingInstruments = mutableSetOf<String>()
    private val loggedBankFallback = mutableSetOf<String>()
    private val loggedPianoFallback = mutableSetOf<String>()
    private var missingNoteCount = 0

    // Maximum layers per note - prevents CPU overload from heavily layered soundfonts
    // Raised from 4 to 8: high-quality SF2 files use multiple velocity/key layers for realism
    private val maxLayersPerNote = 8

    private fun triggerRegions(channel: Int, note: Int, velocity: Int, regions: List<Sf2Region>) {
        if (isResetting.get()) return

        // Limit regions to prevent CPU overload from heavily layered soundfonts
        val limitedRegions = if (regions.size > maxLayersPerNote) {
            regions.sortedBy { it.attenuation }.take(maxLayersPerNote)
        } else {
            regions
        }

        synchronized(voiceLock) {
            for (region in limitedRegions) {
                // Handle exclusive class (kill other voices in same class)
                if (region.exclusiveClass != 0) {
                    voicePool.killExclusiveClass(region.exclusiveClass, channel)
                }

                // Allocate a voice
                val voice = voicePool.allocateVoice(channel)
                if (voice == null) {
                    return
                }

                // Trigger the voice
                voice.trigger(channel, note, velocity, region)
            }
        }
    }

    /**
     * Handles a Note Off event. Thread-safe.
     */
    fun noteOff(channel: Int, note: Int) {
        if (channel !in 0 until NUM_CHANNELS) return
        if (isResetting.get()) return
        // BUG FIX 3.7: Utiliser AtomicBoolean.get() pour lecture thread-safe
        if (isRendering.get()) {
            // BUG FIX 1.6: Utiliser AtomicInteger pour comptage atomique
            if (pendingEventCount.get() < MAX_PENDING_EVENTS) {
                pendingExternalEvents.add(QueuedMidiEvent(1, channel, note, 0))
                pendingEventCount.incrementAndGet()
            }
            return
        }

        // Validate MIDI note
        val safeNote = note.coerceIn(0, 127)

        synchronized(voiceLock) {
            // If sustain pedal is held, add to sustained notes
            if (channelSustain[channel]) {
                sustainedNotes[channel].add(safeNote)

                // Sustain cap: if too many notes are sustained, release oldest ones
                // This prevents sustain pedal from causing infinite voice buildup
                while (sustainedNotes[channel].size > MAX_SUSTAINED_NOTES_PER_CHANNEL) {
                    val oldest = sustainedNotes[channel].iterator().next()
                    sustainedNotes[channel].remove(oldest)
                    voicePool.releaseVoices(channel, oldest)
                }
                return
            }

            // Release all voices for this note
            voicePool.releaseVoices(channel, safeNote)
        }
    }

    /**
     * Handles a Program Change event. Thread-safe.
     */
    fun programChange(channel: Int, program: Int) {
        if (channel !in 0 until NUM_CHANNELS) return
        if (isResetting.get()) return
        // BUG FIX 3.7: Utiliser AtomicBoolean.get() pour lecture thread-safe
        if (isRendering.get()) {
            // BUG FIX 1.6: Utiliser AtomicInteger pour comptage atomique
            if (pendingEventCount.get() < MAX_PENDING_EVENTS) {
                pendingExternalEvents.add(QueuedMidiEvent(2, channel, program, 0))
                pendingEventCount.incrementAndGet()
            }
            return
        }

        synchronized(voiceLock) {
            channelProgram[channel] = program.coerceIn(0, 127)
        }
    }

    /**
     * Handles a Control Change event. Thread-safe.
     */
    fun controlChange(channel: Int, controller: Int, value: Int) {
        if (channel !in 0 until NUM_CHANNELS) return
        if (isResetting.get()) return
        // BUG FIX 3.7: Utiliser AtomicBoolean.get() pour lecture thread-safe
        if (isRendering.get()) {
            // BUG FIX 1.6: Utiliser AtomicInteger pour comptage atomique
            if (pendingEventCount.get() < MAX_PENDING_EVENTS) {
                pendingExternalEvents.add(QueuedMidiEvent(3, channel, controller, value))
                pendingEventCount.incrementAndGet()
            }
            return
        }

        // Validate CC value to prevent garbage data
        val safeValue = value.coerceIn(0, 127)

        synchronized(voiceLock) {
            when (controller) {
                CC_MODULATION -> {
                    // Modulation wheel: 0 = no modulation, 127 = maximum
                    // Normalized to 0.0-1.0 for use as vibrato depth multiplier
                    channelModulation[channel] = safeValue / 127f
                    swapSnapshot(channelModulation, channelModulationSnapshotRef, channelModulationSnapA, channelModulationSnapB)
                }
                CC_DATA_ENTRY_MSB -> {
                    // Data Entry MSB: sets the value for the currently selected RPN/NRPN
                    handleDataEntry(channel, safeValue, isLsb = false)
                }
                CC_BANK_SELECT_MSB -> {
                    channelBank[channel] = (channelBank[channel] and 0x7F) or ((safeValue and 0x7F) shl 7)
                }
                CC_BANK_SELECT_LSB -> {
                    channelBank[channel] = (channelBank[channel] and 0x3F80) or (safeValue and 0x7F)
                }
                CC_DATA_ENTRY_LSB -> {
                    // Data Entry LSB: fine adjustment for the currently selected RPN/NRPN
                    handleDataEntry(channel, safeValue, isLsb = true)
                }
                CC_VOLUME -> {
                    channelVolume[channel] = safeValue / 127f
                    swapSnapshot(channelVolume, channelVolumeSnapshotRef, channelVolumeSnapA, channelVolumeSnapB)
                }
                CC_PAN -> {
                    // MIDI pan: 0 = left, 64 = center, 127 = right
                    channelPan[channel] = (safeValue - 64) / 64f
                    swapSnapshot(channelPan, channelPanSnapshotRef, channelPanSnapA, channelPanSnapB)
                }
                CC_EXPRESSION -> {
                    channelExpression[channel] = safeValue / 127f
                    swapSnapshot(channelExpression, channelExpressionSnapshotRef, channelExpressionSnapA, channelExpressionSnapB)
                }
                CC_SUSTAIN_PEDAL -> {
                    val sustained = safeValue >= 64
                    if (channelSustain[channel] && !sustained) {
                        // Sustain released - release all sustained notes
                        releaseSustainedNotes(channel)
                    }
                    channelSustain[channel] = sustained
                }
                CC_REVERB -> {
                    // Per-channel reverb send level (0 = dry, 127 = full reverb)
                    channelReverbSend[channel] = safeValue / 127f
                    swapSnapshot(channelReverbSend, channelReverbSendSnapshotRef, channelReverbSendSnapA, channelReverbSendSnapB)
                    if (reverbAutoAllowed) {
                        reverbAutoEnabled = updateFxAutoState(
                            channelReverbSend,
                            reverb.enabled,
                            reverbAutoEnabled,
                            onEnable = { reverb.enabled = true },
                            onDisable = { reverb.enabled = false }
                        )
                    }
                }
                CC_CHORUS -> {
                    // Per-channel chorus send level (0 = dry, 127 = full chorus)
                    channelChorusSend[channel] = safeValue / 127f
                    swapSnapshot(channelChorusSend, channelChorusSendSnapshotRef, channelChorusSendSnapA, channelChorusSendSnapB)
                    if (chorusAutoAllowed) {
                        chorusAutoEnabled = updateFxAutoState(
                            channelChorusSend,
                            chorus.enabled,
                            chorusAutoEnabled,
                            onEnable = { chorus.enabled = true },
                            onDisable = { chorus.enabled = false }
                        )
                    }
                }
                CC_RPN_LSB -> {
                    channelRpnLsb[channel] = safeValue
                }
                CC_RPN_MSB -> {
                    channelRpnMsb[channel] = safeValue
                }
                CC_ALL_SOUND_OFF -> {
                    voicePool.stopChannel(channel)
                    sustainedNotes[channel].clear()
                }
                CC_RESET_ALL_CONTROLLERS -> {
                    resetChannelControllers(channel)
                }
                CC_ALL_NOTES_OFF -> {
                    voicePool.releaseChannel(channel)
                    sustainedNotes[channel].clear()
                }
            }
        }
    }

    /**
     * Handles a Pitch Bend event. Thread-safe.
     * @param value Pitch bend value (0-16383, center = 8192)
     */
    fun pitchBend(channel: Int, value: Int) {
        if (channel !in 0 until NUM_CHANNELS) return
        if (isResetting.get()) return
        // BUG FIX 3.7: Utiliser AtomicBoolean.get() pour lecture thread-safe
        if (isRendering.get()) {
            // BUG FIX 1.6: Utiliser AtomicInteger pour comptage atomique
            if (pendingEventCount.get() < MAX_PENDING_EVENTS) {
                pendingExternalEvents.add(QueuedMidiEvent(4, channel, value, 0))
                pendingEventCount.incrementAndGet()
            }
            return
        }

        // Validate pitch bend value (14-bit range: 0-16383)
        val safeValue = value.coerceIn(0, 16383)

        synchronized(voiceLock) {
            // BUG FIX 1.12: Utiliser Double pour le calcul de pitch bend pour eviter
            // la perte de precision apres 1000+ bends (~5 cents d'erreur avec Float).
            // La division 14-bit (0-16383) par 8192 necessite plus de precision que Float32.
            val range = channelPitchBendRange[channel]
            val pitchBendDouble = ((safeValue - 8192).toDouble() / 8192.0) * range
            channelPitchBend[channel] = pitchBendDouble.toFloat()
            swapSnapshot(channelPitchBend, channelPitchBendSnapshotRef, channelPitchBendSnapA, channelPitchBendSnapB)
        }
    }

    // ==================== Audio Rendering ====================

    /**
     * Drains any external MIDI events that were queued during the previous render cycle.
     * Called at the start of each render cycle, before isRendering is set to true.
     * Since isRendering is false here, the events are processed normally via the
     * standard noteOn/noteOff/etc. methods (which won't re-queue since isRendering=false).
     */
    private fun drainPendingExternalEvents() {
        var event = pendingExternalEvents.poll()
        while (event != null) {
            // BUG FIX 1.6: Décrémenter le compteur atomique lors du retrait
            pendingEventCount.decrementAndGet()
            when (event.type) {
                0 -> noteOn(event.channel, event.param1, event.param2)
                1 -> noteOff(event.channel, event.param1)
                2 -> programChange(event.channel, event.param1)
                3 -> controlChange(event.channel, event.param1, event.param2)
                4 -> pitchBend(event.channel, event.param1)
            }
            event = pendingExternalEvents.poll()
        }
    }

    /**
     * Renders audio samples into the output buffers. Thread-safe.
     * The buffers should be pre-cleared (filled with zeros) before calling.
     * This method is called from the audio thread.
     */
    fun render(outputLeft: FloatArray, outputRight: FloatArray, numSamples: Int) {
        // Skip rendering during reset to avoid inconsistent state
        if (isResetting.get()) {
            return
        }

        // Process any external MIDI events that were queued during previous render
        drainPendingExternalEvents()

        // BUG FIX 3.7: Utiliser AtomicBoolean.set() pour écriture thread-safe
        isRendering.set(true)
        try {
            // BUG FIX 1.10: Render all voices with per-channel snapshots via AtomicReference.get()
            // AtomicReference.get() fournit une barriere memoire, garantissant que nous
            // lisons une copie complete du buffer (jamais partiellement copie).
            // Note: Lock removed for better performance. Voice pool iteration is safe because:
            // 1. Voice array is fixed size (no add/remove)
            // 2. Voice.isActive is volatile for visibility
            // 3. Individual voice rendering is independent
            val volumeSnapshot = channelVolumeSnapshotRef.get()
            val pitchBendSnapshot = channelPitchBendSnapshotRef.get()
            val panSnapshot = channelPanSnapshotRef.get()
            val expressionSnapshot = channelExpressionSnapshotRef.get()
            val modulationSnapshot = channelModulationSnapshotRef.get()
            val reverbSendSnapshot = channelReverbSendSnapshotRef.get()
            val chorusSendSnapshot = channelChorusSendSnapshotRef.get()

            smoothChannelControllers(volumeSnapshot, panSnapshot, expressionSnapshot, numSamples)

            // Clear send buffers (reverb + chorus)
            val sendSamples = numSamples.coerceAtMost(reverbSendLeft.size)
            for (i in 0 until sendSamples) {
                reverbSendLeft[i] = 0f
                reverbSendRight[i] = 0f
                chorusSendLeft[i] = 0f
                chorusSendRight[i] = 0f
            }

            // masterGain is now applied INSIDE each voice (via voicePool per-voice channelVolume)
            // This means BiquadFilter sees signals ~4x quieter, reducing resonance/instability.
            // Send buffers also come out at the correct level automatically.
            val effectiveGain = masterVolume * globalGain
            voicePool.render(
                sf2File,
                outputLeft,
                outputRight,
                numSamples,
                smoothedChannelVolume,
                pitchBendSnapshot,
                smoothedChannelPan,
                smoothedChannelExpression,
                modulationSnapshot,
                reverbSendSnapshot,
                reverbSendLeft,
                reverbSendRight,
                chorusSendSnapshot,
                chorusSendLeft,
                chorusSendRight,
                effectiveGain
            )

            // Apply reverb effect on per-channel send buffers, then mix back
            if (reverb.enabled) {
                // Process reverb on the send buffers (fully wet, we already control the send level per-channel)
                val savedWet = reverb.wetLevel
                val savedDry = reverb.dryLevel
                reverb.wetLevel = 1f
                reverb.dryLevel = 0f
                reverb.process(reverbSendLeft, reverbSendRight, sendSamples)
                reverb.wetLevel = savedWet
                reverb.dryLevel = savedDry

                // Mix reverb output back into main output, scaled by the original wet level
                for (i in 0 until sendSamples) {
                    outputLeft[i] += reverbSendLeft[i] * savedWet
                    outputRight[i] += reverbSendRight[i] * savedWet
                }
            }

            // Apply chorus effect on per-channel send buffers, then mix back
            if (chorus.enabled) {
                chorus.process(chorusSendLeft, chorusSendRight, sendSamples)

                // Mix chorus output back into main output at the chorus level
                val chorusLevel = chorus.level
                for (i in 0 until sendSamples) {
                    outputLeft[i] += chorusSendLeft[i] * chorusLevel
                    outputRight[i] += chorusSendRight[i] * chorusLevel
                }
            }

            applyBusSaturation(outputLeft, outputRight, numSamples)

            // Apply limiter to prevent clipping and balance levels
            limiter.process(outputLeft, outputRight, numSamples)

            // Apply EQ post-limiter
            equalizer.process(outputLeft, outputRight, numSamples)
        } catch (_: Exception) {
            // Log but don't crash - audio rendering should be resilient
        } finally {
            // BUG FIX 3.7: Utiliser AtomicBoolean.set() pour écriture thread-safe
            isRendering.set(false)
        }
    }

    private fun smoothChannelControllers(
        volumeSnapshot: FloatArray,
        panSnapshot: FloatArray,
        expressionSnapshot: FloatArray,
        numSamples: Int
    ) {
        if (numSamples <= 0) return

        val bufferSeconds = numSamples.toFloat() / sampleRate
        val smoothingSeconds = 0.005f
        val alpha = 1f - exp((-bufferSeconds / smoothingSeconds).toDouble()).toFloat()

        for (channel in 0 until NUM_CHANNELS) {
            smoothedChannelVolume[channel] += (volumeSnapshot[channel] - smoothedChannelVolume[channel]) * alpha
            smoothedChannelVolume[channel] = smoothedChannelVolume[channel].coerceIn(0f, 1f)

            smoothedChannelPan[channel] += (panSnapshot[channel] - smoothedChannelPan[channel]) * alpha
            smoothedChannelPan[channel] = smoothedChannelPan[channel].coerceIn(-1f, 1f)

            smoothedChannelExpression[channel] += (expressionSnapshot[channel] - smoothedChannelExpression[channel]) * alpha
            smoothedChannelExpression[channel] = smoothedChannelExpression[channel].coerceIn(0f, 1f)
        }
    }

    /**
     * Renders audio and returns new stereo float arrays. Thread-safe.
     */
    @Suppress("unused")
    fun renderToBuffers(numSamples: Int): Pair<FloatArray, FloatArray> {
        val left = FloatArray(numSamples)
        val right = FloatArray(numSamples)
        render(left, right, numSamples)
        return Pair(left, right)
    }

    // ==================== Utility Methods ====================

    /**
     * Enable or disable mix-bus saturation before the limiter.
     */
    fun setBusSaturationEnabled(enabled: Boolean) {
        busSaturationEnabled = enabled
    }

    /**
     * Adjust the drive for mix-bus saturation (1.0 = neutral).
     */
    fun setBusSaturationDrive(drive: Float) {
        busSaturationDrive = drive.coerceIn(1.0f, 3.0f)
    }

    private fun applyBusSaturation(left: FloatArray, right: FloatArray, numSamples: Int) {
        if (!busSaturationEnabled || numSamples <= 0) return

        val drive = busSaturationDrive
        val normalization = tanh(drive.toDouble()).toFloat()
        if (normalization <= 0f) return

        for (i in 0 until numSamples) {
            left[i] = (tanh((left[i] * drive).toDouble()).toFloat() / normalization)
            right[i] = (tanh((right[i] * drive).toDouble()).toFloat() / normalization)
        }
    }

    /**
     * Handles RPN Data Entry (CC6/CC38).
     * Called from within synchronized block, no extra sync needed.
     * Currently supports:
     *   RPN 0,0 = Pitch Bend Sensitivity (semitones via MSB, cents via LSB)
     */
    private fun handleDataEntry(channel: Int, value: Int, isLsb: Boolean) {
        val rpnMsb = channelRpnMsb[channel]
        val rpnLsb = channelRpnLsb[channel]

        // RPN 127,127 = null (no RPN selected) — ignore data entry
        if (rpnMsb == 127 && rpnLsb == 127) return

        // RPN 0,0 = Pitch Bend Sensitivity
        if (rpnMsb == 0 && rpnLsb == 0) {
            if (!isLsb) {
                // MSB = semitones (0-24 is typical range, clamp to 24 for safety)
                channelPitchBendRange[channel] = value.coerceIn(0, 24)
            }
            // LSB = cents (0-99), we ignore sub-semitone precision for simplicity
        }
    }

    // Note: releaseSustainedNotes is called from within synchronized blocks, no extra sync needed
    private fun releaseSustainedNotes(channel: Int) {
        for (note in sustainedNotes[channel]) {
            voicePool.releaseVoices(channel, note)
        }
        sustainedNotes[channel].clear()
    }

    // Note: resetChannelControllers is called from within synchronized blocks, no extra sync needed
    private fun resetChannelControllers(channel: Int) {
        channelVolume[channel] = 1f
        channelPan[channel] = 0f
        channelExpression[channel] = 1f
        channelSustain[channel] = false
        channelPitchBend[channel] = 0f
        channelModulation[channel] = 0f
        channelReverbSend[channel] = 0.4f  // Default 40% reverb send
        channelChorusSend[channel] = 0f    // Default 0% chorus send
        channelPitchBendRange[channel] = DEFAULT_PITCH_BEND_RANGE
        channelRpnMsb[channel] = 127      // Null RPN
        channelRpnLsb[channel] = 127
        sustainedNotes[channel].clear()
    }

    /**
     * Resets the synthesizer to initial state. Thread-safe.
     */
    fun reset() {
        // Set flag to prevent other operations during reset
        isResetting.set(true)

        try {
            // Clear any pending external events (stale after reset)
            // BUG FIX 1.6: Réinitialiser le compteur atomique lors du clear
            pendingExternalEvents.clear()
            pendingEventCount.set(0)

            synchronized(voiceLock) {
                voicePool.stopAll()

                for (ch in 0 until NUM_CHANNELS) {
                    channelProgram[ch] = 0
                    channelBank[ch] = if (ch == PERCUSSION_CHANNEL) Sf2Preset.PERCUSSION_BANK else 0
                    resetChannelControllers(ch)
                }

                // Update snapshots (zero-alloc double-buffer swap avec AtomicReference)
                swapSnapshot(channelVolume, channelVolumeSnapshotRef, channelVolumeSnapA, channelVolumeSnapB)
                swapSnapshot(channelPitchBend, channelPitchBendSnapshotRef, channelPitchBendSnapA, channelPitchBendSnapB)
                swapSnapshot(channelPan, channelPanSnapshotRef, channelPanSnapA, channelPanSnapB)
                swapSnapshot(channelExpression, channelExpressionSnapshotRef, channelExpressionSnapA, channelExpressionSnapB)
                swapSnapshot(channelModulation, channelModulationSnapshotRef, channelModulationSnapA, channelModulationSnapB)
                swapSnapshot(channelReverbSend, channelReverbSendSnapshotRef, channelReverbSendSnapA, channelReverbSendSnapB)
                swapSnapshot(channelChorusSend, channelChorusSendSnapshotRef, channelChorusSendSnapA, channelChorusSendSnapB)
            }

            for (ch in 0 until NUM_CHANNELS) {
                smoothedChannelVolume[ch] = channelVolume[ch]
                smoothedChannelPan[ch] = channelPan[ch]
                smoothedChannelExpression[ch] = channelExpression[ch]
            }

            // Reset limiter state
            limiter.reset()

            // Reset EQ (clear filter state)
            equalizer.reset()

            // Reset reverb (clear delay buffers)
            reverb.reset()

            // Reset chorus (clear delay buffers)
            chorus.reset()

            reverbAutoAllowed = reverb.enabled
            chorusAutoAllowed = chorus.enabled

            // Clear logged warnings
            loggedMissingPercussion.clear()
            loggedMissingInstruments.clear()
        } finally {
            isResetting.set(false)
        }
    }

    /**
     * Releases all active voices (panic/all notes off). Thread-safe.
     */
    fun allNotesOff() {
        synchronized(voiceLock) {
            voicePool.releaseAll()
            for (ch in 0 until NUM_CHANNELS) {
                sustainedNotes[ch].clear()
                channelSustain[ch] = false  // Reset sustain pedal state
            }
        }
    }

    /**
     * Immediately stops all sound. Thread-safe.
     */
    fun allSoundOff() {
        synchronized(voiceLock) {
            voicePool.stopAll()
            for (ch in 0 until NUM_CHANNELS) {
                sustainedNotes[ch].clear()
                channelSustain[ch] = false  // Reset sustain pedal state
            }
        }
    }

    /**
     * Gets the number of active voices.
     */
    @Suppress("unused")
    fun getActiveVoiceCount(): Int = voicePool.activeVoiceCount

    /**
     * Gets voice pool statistics.
     */
    @Suppress("unused")
    fun getVoicePoolStats(): VoicePoolStats = voicePool.getStats()

    /**
     * Gets the program number for a channel.
     */
    @Suppress("unused")
    fun getChannelProgram(channel: Int): Int {
        return if (channel in 0 until NUM_CHANNELS) channelProgram[channel] else 0
    }

    /**
     * Gets the bank number for a channel.
     */
    @Suppress("unused")
    fun getChannelBank(channel: Int): Int {
        return if (channel in 0 until NUM_CHANNELS) channelBank[channel] else 0
    }

    /**
     * Gets all available presets from the SF2 file.
     */
    @Suppress("unused")
    fun getAvailablePresets(): List<ProgramInfo> = sf2File.getPrograms()

    /**
     * Gets statistics string for debugging.
     */
    fun getStats(): String {
        val gainDb = (20 * kotlin.math.log10(limiter.getAutoGain().toDouble())).toInt()
        return "SF2: ${sf2File.name} | ${voicePool.getStats()} | Gain: ${gainDb}dB"
    }

    /**
     * Enable/disable the auto-gain feature of the limiter.
     */
    @Suppress("unused")
    fun setAutoGainEnabled(enabled: Boolean) {
        limiter.autoGainEnabled = enabled
    }

    /**
     * Enable/disable soft clipping.
     */
    @Suppress("unused")
    fun setSoftClipEnabled(enabled: Boolean) {
        limiter.softClipEnabled = enabled
    }

    /**
     * Sets the velocity curve for all voices.
     * Affects the dynamic response to MIDI velocity.
     *
     * @param curve The velocity curve to use
     */
    @Suppress("unused")
    fun setVelocityCurve(curve: VelocityCurve) {
        Sf2Voice.velocityCurve = curve
    }

    /**
     * Gets the current velocity curve.
     */
    @Suppress("unused")
    fun getVelocityCurve(): VelocityCurve = Sf2Voice.velocityCurve

    // -------------------------------------------------------------------------
    // EQ 10 bandes
    // -------------------------------------------------------------------------

    fun setEqEnabled(enabled: Boolean) {
        equalizer.enabled = enabled
    }

    fun setEqBandLevel(band: Int, millibels: Int) {
        equalizer.setBandLevel(band, millibels)
    }

    fun getEqBandLevel(band: Int): Int = equalizer.getBandLevel(band)

    // -------------------------------------------------------------------------

    /**
     * Sets the reverb preset.
     * @param preset -1 = Off, 0 = Large Hall, 1 = Hall, 2 = Chamber, 3 = Room
     */
    fun setReverbPreset(preset: Int) {
        reverb.applyPreset(preset)
        reverbAutoEnabled = false
        reverbAutoAllowed = preset >= 0
    }

    /**
     * Returns true if reverb is currently enabled.
     */
    @Suppress("unused")
    fun isReverbEnabled(): Boolean = reverb.enabled

    /**
     * Enables or disables reverb directly.
     */
    @Suppress("unused")
    fun setReverbEnabled(enabled: Boolean) {
        reverb.enabled = enabled
        reverbAutoEnabled = false
        reverbAutoAllowed = enabled
    }

    /**
     * Sets the chorus preset.
     * @param preset -1 = Off, 0 = Light, 1 = Default, 2 = Rich
     */
    fun setChorusPreset(preset: Int) {
        chorus.applyPreset(preset)
        chorusAutoEnabled = false
        chorusAutoAllowed = preset >= 0
    }

    /**
     * Returns true if chorus is currently enabled.
     */
    @Suppress("unused")
    fun isChorusEnabled(): Boolean = chorus.enabled

    /**
     * Enables or disables chorus directly.
     */
    @Suppress("unused")
    fun setChorusEnabled(enabled: Boolean) {
        chorus.enabled = enabled
        chorusAutoEnabled = false
        chorusAutoAllowed = enabled
    }

    /**
     * Releases resources held by the synthesizer.
     * Shuts down render worker threads in the voice pool.
     */
    /**
     * Active/désactive le rendu séquentiel forcé sur le VoicePool.
     * Appeler avec true quand l'écran est éteint pour éviter les craquements
     * dus au throttling CPU des threads workers (gouverneur Android en background).
     */
    fun setForceSequentialRendering(forced: Boolean) {
        voicePool.forceSequentialRendering = forced
    }

    fun release() {
        // BUG FIX 1.6: Réinitialiser le compteur atomique lors du clear
        pendingExternalEvents.clear()
        pendingEventCount.set(0)
        voicePool.shutdown()
    }
}
