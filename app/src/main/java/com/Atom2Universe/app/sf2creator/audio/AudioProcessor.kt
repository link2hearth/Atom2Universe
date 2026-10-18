package com.Atom2Universe.app.sf2creator.audio

import android.util.Log

/**
 * Unified audio processing pipeline for SF2 sample creation.
 *
 * This class orchestrates all audio processing steps:
 * 1. DC offset removal - Remove constant offset
 * 2. De-clicking - Remove pops and clicks
 * 3. Noise gating - Remove background noise
 * 4. Silence trimming - Remove silence at start/end
 * 5. Normalization - Consistent volume level
 * 6. Soft clipping - Prevent harsh digital clipping
 * 7. Fade in/out - Eliminate edge clicks
 * 8. Loop detection - Find loop points for sustained sounds
 * 9. Loop crossfade - Smooth loop transitions
 * 10. Envelope (optional) - Apply ADSR shaping
 */
class AudioProcessor(private val sampleRate: Int = 44100) {

    companion object {
        private const val TAG = "AudioProcessor"
    }

    // Processing modules
    private val deClicker = DeClicker(sampleRate)
    private val noiseGate = NoiseGate(sampleRate)
    private val trimmer = SampleTrimmer(sampleRate)
    private val normalizer = Normalizer()
    private val loopFinder = LoopFinder(sampleRate)
    private val crossfader = Crossfader(sampleRate)
    private val envelopeGenerator = EnvelopeGenerator(sampleRate)

    /**
     * Processing options for the audio pipeline.
     */
    data class ProcessingOptions(
        // DC Offset & Artifacts
        val removeDCOffset: Boolean = true,
        val deClick: Boolean = true,
        val deClickSensitivity: Float = 0.3f,  // Lower = more aggressive
        val applySoftClipping: Boolean = true,

        // Noise Gate
        val applyNoiseGate: Boolean = true,
        val noiseGateSettings: NoiseGate.Settings = NoiseGate.Settings(thresholdDb = -45f),
        val autoDetectNoiseGate: Boolean = true,  // Auto-detect optimal settings

        // Silence Trimming
        val trimSilence: Boolean = true,
        val silenceThresholdDb: Float = -55f,  // Less aggressive to preserve quiet tails
        val keepPreAttackMs: Int = 10,

        // Normalization
        val normalize: Boolean = true,
        val normalizeMode: Normalizer.Mode = Normalizer.Mode.PEAK,
        val targetLevelDb: Float = -1f,

        // Fades
        val fadeInMs: Int = 5,
        val fadeOutMs: Int = 15,
        val fadeCurve: SampleTrimmer.FadeCurve = SampleTrimmer.FadeCurve.S_CURVE,

        // Loop Detection
        val detectLoop: Boolean = true,
        val minLoopMs: Int = 50,
        val maxLoopMs: Int = 2000,
        val loopQualityThreshold: Float = 0.75f,

        // Loop Crossfade
        val crossfadeLoopMs: Int = 20,
        val crossfadeCurve: Crossfader.CurveType = Crossfader.CurveType.EQUAL_POWER,

        // Envelope (optional - usually applied during playback, not baked in)
        val applyEnvelope: Boolean = false,
        val envelopeSettings: EnvelopeGenerator.ADSRSettings = EnvelopeGenerator.Presets.PIANO
    )

    /**
     * Result of audio processing.
     */
    data class ProcessedResult(
        val samples: ShortArray,
        val loopStart: Int,
        val loopEnd: Int,
        val hasLoop: Boolean,
        val loopQuality: Float,
        val originalLengthMs: Int,
        val processedLengthMs: Int,
        val peakDb: Float,
        val rmsDb: Float,
        val processingInfo: String
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ProcessedResult
            return samples.contentEquals(other.samples) &&
                    loopStart == other.loopStart &&
                    loopEnd == other.loopEnd
        }

        override fun hashCode(): Int {
            var result = samples.contentHashCode()
            result = 31 * result + loopStart
            result = 31 * result + loopEnd
            return result
        }
    }

    /**
     * Process audio samples through the full pipeline.
     *
     * @param samples Raw audio samples
     * @param options Processing options
     * @return ProcessedResult with processed samples and metadata
     */
    fun process(samples: ShortArray, options: ProcessingOptions = ProcessingOptions()): ProcessedResult {
        if (samples.isEmpty()) {
            return ProcessedResult(
                samples = samples,
                loopStart = 0,
                loopEnd = 0,
                hasLoop = false,
                loopQuality = 0f,
                originalLengthMs = 0,
                processedLengthMs = 0,
                peakDb = Float.NEGATIVE_INFINITY,
                rmsDb = Float.NEGATIVE_INFINITY,
                processingInfo = "Empty sample"
            )
        }

        val originalLengthMs = samples.size * 1000 / sampleRate
        val infoBuilder = StringBuilder()
        var processed = samples.copyOf()

        // Analyze quality before processing
        val qualityReport = deClicker.analyzeQuality(processed)
        Log.d(TAG, qualityReport.toString())

        // Step 1: Remove DC offset
        if (options.removeDCOffset) {
            processed = deClicker.removeDCOffset(processed)
            if (kotlin.math.abs(qualityReport.dcOffsetPercent) > 0.01f) {
                infoBuilder.appendLine("DC offset removed: ${(qualityReport.dcOffsetPercent * 100).format(2)}%")
                Log.d(TAG, "Removed DC offset: ${qualityReport.dcOffsetPercent * 100}%")
            }
        }

        // Step 2: De-click (remove pops and clicks)
        if (options.deClick && qualityReport.potentialClicks > 0) {
            processed = deClicker.removeClicks(processed, options.deClickSensitivity)
            infoBuilder.appendLine("De-clicked: ${qualityReport.potentialClicks} artifacts removed")
            Log.d(TAG, "Removed ${qualityReport.potentialClicks} potential clicks")
        }

        // Step 3: Noise Gate
        if (options.applyNoiseGate) {
            val gateSettings = if (options.autoDetectNoiseGate) {
                noiseGate.analyzeAndSuggestSettings(processed)
            } else {
                options.noiseGateSettings
            }
            processed = noiseGate.apply(processed, gateSettings)
            infoBuilder.appendLine("Noise gate: ${gateSettings.thresholdDb}dB threshold")
            Log.d(TAG, "Applied noise gate with threshold ${gateSettings.thresholdDb}dB")
        }

        // Step 4: Trim Silence
        if (options.trimSilence) {
            val trimResult = trimmer.trimSilence(
                processed,
                options.silenceThresholdDb,
                keepPreAttackMs = options.keepPreAttackMs
            )
            val trimmedMs = trimResult.trimmedStartMs + trimResult.trimmedEndMs
            processed = trimResult.samples
            infoBuilder.appendLine("Trimmed: ${trimmedMs}ms silence removed")
            Log.d(TAG, "Trimmed ${trimResult.trimmedStartMs}ms from start, ${trimResult.trimmedEndMs}ms from end")
        }

        // Step 5: Normalize
        if (options.normalize) {
            val beforePeak = normalizer.measurePeakDb(processed)
            processed = normalizer.normalize(processed, options.normalizeMode, options.targetLevelDb)
            val afterPeak = normalizer.measurePeakDb(processed)
            infoBuilder.appendLine("Normalized: ${beforePeak.format(1)}dB → ${afterPeak.format(1)}dB")
            Log.d(TAG, "Normalized from ${beforePeak}dB to ${afterPeak}dB")
        }

        // Step 6: Soft Clipping (prevent harsh digital clipping)
        if (options.applySoftClipping) {
            processed = deClicker.applySoftClipping(processed, threshold = 0.95f)
            Log.d(TAG, "Applied soft clipping")
        }

        // Step 7: Apply Fades
        if (options.fadeInMs > 0 || options.fadeOutMs > 0) {
            processed = trimmer.applyFades(processed, options.fadeInMs, options.fadeOutMs, options.fadeCurve)
            infoBuilder.appendLine("Fades: ${options.fadeInMs}ms in, ${options.fadeOutMs}ms out")
            Log.d(TAG, "Applied fades: ${options.fadeInMs}ms in, ${options.fadeOutMs}ms out")
        }

        // Step 8: Loop Detection
        var loopStart = 0
        var loopEnd = 0
        var loopQuality = 0f
        var hasLoop = false

        if (options.detectLoop && loopFinder.isSuitableForLooping(processed)) {
            val loopParams = LoopFinder.LoopSearchParams(
                minLoopMs = options.minLoopMs,
                maxLoopMs = options.maxLoopMs,
                qualityThreshold = options.loopQualityThreshold
            )
            val loop = loopFinder.findBestLoop(processed, loopParams)

            if (loop != null) {
                loopStart = loop.start
                loopEnd = loop.end
                loopQuality = loop.quality
                hasLoop = true
                infoBuilder.appendLine("Loop: ${loop.lengthMs}ms, quality ${(loop.quality * 100).toInt()}%")
                Log.d(TAG, loopFinder.describeLoop(loop))

                // Step 9: Crossfade Loop
                if (options.crossfadeLoopMs > 0) {
                    processed = crossfader.applyCrossfadeLoop(
                        processed,
                        loopStart,
                        loopEnd,
                        options.crossfadeLoopMs,
                        options.crossfadeCurve
                    )
                    infoBuilder.appendLine("Loop crossfade: ${options.crossfadeLoopMs}ms")
                    Log.d(TAG, "Applied loop crossfade: ${options.crossfadeLoopMs}ms")
                }
            } else {
                infoBuilder.appendLine("No suitable loop found")
                Log.d(TAG, "No suitable loop points found")
            }
        } else if (options.detectLoop) {
            infoBuilder.appendLine("Sample not suitable for looping")
            Log.d(TAG, "Sample not suitable for looping")
        }

        // Step 10: Apply Envelope (optional - usually done during playback)
        if (options.applyEnvelope) {
            processed = envelopeGenerator.applyEnvelope(processed, options.envelopeSettings)
            infoBuilder.appendLine("Envelope applied")
            Log.d(TAG, "Applied ADSR envelope")
        }

        // Measure final levels
        val peakDb = normalizer.measurePeakDb(processed)
        val rmsDb = normalizer.measureRMSDb(processed)
        val processedLengthMs = processed.size * 1000 / sampleRate

        infoBuilder.appendLine("Final: ${processedLengthMs}ms, Peak: ${peakDb.format(1)}dB, RMS: ${rmsDb.format(1)}dB")

        return ProcessedResult(
            samples = processed,
            loopStart = loopStart,
            loopEnd = loopEnd,
            hasLoop = hasLoop,
            loopQuality = loopQuality,
            originalLengthMs = originalLengthMs,
            processedLengthMs = processedLengthMs,
            peakDb = peakDb,
            rmsDb = rmsDb,
            processingInfo = infoBuilder.toString().trim()
        )
    }

    /**
     * Quick processing with sensible defaults.
     * Use this for simple use cases.
     */
    fun quickProcess(samples: ShortArray): ProcessedResult {
        return process(samples, ProcessingOptions())
    }

    /**
     * Process for percussive sounds (no loop, fast response).
     */
    fun processPercussive(samples: ShortArray): ProcessedResult {
        return process(samples, ProcessingOptions(
            applyNoiseGate = true,
            noiseGateSettings = NoiseGate.Settings(
                thresholdDb = -50f,
                attackMs = 0.5f,
                holdMs = 30f,
                releaseMs = 50f
            ),
            trimSilence = true,
            silenceThresholdDb = -45f,
            normalize = true,
            fadeInMs = 2,
            fadeOutMs = 10,
            detectLoop = false  // Percussive sounds don't loop
        ))
    }

    /**
     * Process for sustained sounds (with loop detection).
     */
    fun processSustained(samples: ShortArray): ProcessedResult {
        return process(samples, ProcessingOptions(
            applyNoiseGate = true,
            noiseGateSettings = NoiseGate.Settings(
                thresholdDb = -40f,
                attackMs = 2f,
                holdMs = 100f,
                releaseMs = 200f
            ),
            trimSilence = true,
            silenceThresholdDb = -40f,
            normalize = true,
            fadeInMs = 5,
            fadeOutMs = 20,
            detectLoop = true,
            minLoopMs = 100,
            maxLoopMs = 2000,
            loopQualityThreshold = 0.8f,
            crossfadeLoopMs = 25
        ))
    }

    /**
     * Analyze sample and suggest processing options.
     */
    fun analyzeAndSuggest(samples: ShortArray): ProcessingOptions {
        if (samples.isEmpty()) return ProcessingOptions()

        // Detect noise floor
        val noiseGateSettings = noiseGate.analyzeAndSuggestSettings(samples)

        // Check if suitable for looping
        val suitableForLoop = loopFinder.isSuitableForLooping(samples)

        // Suggest envelope based on characteristics
        val suggestedEnvelope = envelopeGenerator.suggestPreset(samples)

        // Use a conservative silence threshold that won't cut off quiet tails
        // At most -50dB, even if noise gate suggests higher
        val silenceThreshold = minOf(noiseGateSettings.thresholdDb + 5f, -50f)

        return ProcessingOptions(
            applyNoiseGate = true,
            noiseGateSettings = noiseGateSettings,
            autoDetectNoiseGate = false,
            removeDCOffset = true,
            deClick = true,
            deClickSensitivity = 0.25f,  // More aggressive de-clicking
            trimSilence = true,
            silenceThresholdDb = silenceThreshold,
            keepPreAttackMs = 15,  // Keep more before the attack
            normalize = true,
            fadeInMs = 10,  // Longer fade-in to mask any startup transients
            fadeOutMs = 20,  // Longer fade-out to preserve tail
            detectLoop = suitableForLoop,
            loopQualityThreshold = if (suitableForLoop) 0.75f else 0.9f,
            applyEnvelope = false,  // Usually applied during playback
            envelopeSettings = suggestedEnvelope
        )
    }

    /**
     * Get info about the processing modules.
     */
    fun getProcessingInfo(): String {
        return """
            Audio Processor v1.0
            Sample Rate: $sampleRate Hz

            Modules:
            - Noise Gate (with auto-detection)
            - Silence Trimmer (configurable threshold)
            - Peak/RMS Normalizer
            - Fade In/Out (multiple curves)
            - Loop Finder (cross-correlation based)
            - Loop Crossfader (equal-power)
            - ADSR Envelope Generator
        """.trimIndent()
    }

    private fun Float.format(decimals: Int): String {
        return if (this.isFinite()) {
            "%.${decimals}f".format(this)
        } else {
            "-∞"
        }
    }
}
