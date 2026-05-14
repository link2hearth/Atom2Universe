package com.Atom2Universe.app.sf2creator.data

import android.content.Context
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedModulator
import com.Atom2Universe.app.sf2creator.util.Sf2Constants
import com.Atom2Universe.app.sf2creator.util.WavUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Singleton clipboard for copying presets and samples between projects.
 * Holds copied data in memory until paste or clear.
 */
object Sf2Clipboard {

    /**
     * Types of clipboard content.
     */
    sealed class ClipboardContent {
        /**
         * A complete program (MIDI program) with all its instruments and samples.
         */
        data class Program(
            val programName: String,
            val programNumber: Int,
            val bankNumber: Int,
            val instruments: List<InstrumentData>
        ) : ClipboardContent()

        /**
         * A complete preset/instrument with all its samples.
         */
        data class Preset(
            val presetName: String,
            val programNumber: Int,
            val bankNumber: Int,
            val samples: List<SampleData>,
            val presetGlobalParams: PresetGlobalParams = PresetGlobalParams(),
            val instrumentGlobalParams: InstrumentGlobalParams = InstrumentGlobalParams()
        ) : ClipboardContent()

        /**
         * Preset global parameters (PGEN).
         * All values are ADDITIVE with instrument and sample values.
         */
        data class PresetGlobalParams(
            val pgenKeyRangeLow: Int = 0,
            val pgenKeyRangeHigh: Int = 127,
            val pgenVelRangeLow: Int = 0,
            val pgenVelRangeHigh: Int = 127,
            val pgenAttenuation: Int = 0,
            val pgenCoarseTune: Int = 0,
            val pgenFineTune: Int = 0,
            val pgenFilterFc: Int = 0,
            val pgenFilterQ: Int = 0,
            val pgenChorusSend: Int = 0,
            val pgenReverbSend: Int = 0,
            val pgenPan: Int = 0,
            val pgenVolEnvDelay: Int = 0,
            val pgenVolEnvAttack: Int = 0,
            val pgenVolEnvHold: Int = 0,
            val pgenVolEnvDecay: Int = 0,
            val pgenVolEnvSustain: Int = 0,
            val pgenVolEnvRelease: Int = 0,
            val pgenModEnvDelay: Int = 0,
            val pgenModEnvAttack: Int = 0,
            val pgenModEnvHold: Int = 0,
            val pgenModEnvDecay: Int = 0,
            val pgenModEnvSustain: Int = 0,
            val pgenModEnvRelease: Int = 0,
            val pgenModEnvToPitch: Int = 0,
            val pgenModEnvToFilterFc: Int = 0,
            val pgenVibLfoDelay: Int = 0,
            val pgenVibLfoFreq: Int = 0,
            val pgenVibLfoToPitch: Int = 0,
            val pgenModLfoDelay: Int = 0,
            val pgenModLfoFreq: Int = 0,
            val pgenModLfoToPitch: Int = 0,
            val pgenModLfoToFilterFc: Int = 0,
            val pgenModLfoToVolume: Int = 0
        )

        /**
         * A single sample.
         */
        data class Sample(
            val sample: SampleData
        ) : ClipboardContent()

        /**
         * Multiple samples (from multi-select).
         */
        data class Samples(
            val samples: List<SampleData>
        ) : ClipboardContent()
    }

    /**
     * Instrument/preset data with all samples.
     * Represents ONE preset zone (one instrument reference with specific PGEN parameters).
     */
    data class InstrumentData(
        val name: String,
        val programNumber: Int,
        val bankNumber: Int,
        val samples: List<SampleData>,
        val globalParams: InstrumentGlobalParams = InstrumentGlobalParams(),
        // PGEN parameters specific to THIS zone (key/velocity range, attenuation, etc.)
        val zoneParams: ClipboardContent.PresetGlobalParams = ClipboardContent.PresetGlobalParams()
    )

    /**
     * Instrument global parameters (IGEN global zone).
     * All values are ADDITIVE with sample-specific values.
     */
    data class InstrumentGlobalParams(
        val globalAttenuation: Int = 0,
        val globalCoarseTune: Int = 0,
        val globalFineTune: Int = 0,
        val globalVolEnvDelay: Int = 0,
        val globalVolEnvAttack: Int = 0,
        val globalVolEnvHold: Int = 0,
        val globalVolEnvDecay: Int = 0,
        val globalVolEnvSustain: Int = 0,
        val globalVolEnvRelease: Int = 0,
        val globalModEnvDelay: Int = 0,
        val globalModEnvAttack: Int = 0,
        val globalModEnvHold: Int = 0,
        val globalModEnvDecay: Int = 0,
        val globalModEnvSustain: Int = 0,
        val globalModEnvRelease: Int = 0,
        val globalModEnvToPitch: Int = 0,
        val globalModEnvToFilterFc: Int = 0,
        val globalVibLfoDelay: Int = 0,
        val globalVibLfoFreq: Int = 0,
        val globalVibLfoToPitch: Int = 0,
        val globalModLfoDelay: Int = 0,
        val globalModLfoFreq: Int = 0,
        val globalModLfoToPitch: Int = 0,
        val globalModLfoToFilterFc: Int = 0,
        val globalModLfoToVolume: Int = 0,
        val globalFilterFc: Int = 0,
        val globalFilterQ: Int = 0,
        val globalChorusSend: Int = 0,
        val globalReverbSend: Int = 0,
        val globalPan: Int = 0
    )

    /**
     * Sample data with audio included (for paste).
     * Contains all SF2 parameters in SF2 native units.
     *
     * SF2 Native Units:
     * - Time values: timecents (1200 * log2(seconds)), -12000 = instant
     * - Sustain levels: centibels of attenuation (0 = full volume, 1000 = silent)
     * - Filter cutoff: absolute cents (1200 * log2(freq/8.176))
     */
    data class SampleData(
        val name: String,
        val audioData: ShortArray,
        val sampleRate: Int,
        val rootNote: Int,
        val keyRangeStart: Int,
        val keyRangeEnd: Int,
        val loopStart: Int,
        val loopEnd: Int,
        val hasLoop: Boolean,
        val sampleModes: Int = 0, // SF2 sampleModes: 0=no loop, 1=loop, 3=loop+release
        val attenuation: Int,
        val fineTuneCents: Int,
        // Volume Envelope - SF2 native units (timecents/centibels)
        val volEnvDelay: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val volEnvAttack: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val volEnvHold: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val volEnvDecay: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val volEnvSustain: Int = 0,  // centibels (0 = full volume)
        val volEnvRelease: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        // Filter - SF2 native units
        val filterFc: Int = 13500,   // absolute cents (13500 = ~20kHz)
        val filterQ: Int = 0,
        val chorusSend: Int,
        val reverbSend: Int,
        val pan: Int,
        // ==================== Advanced SF2 Parameters ====================
        // Velocity range
        val velRangeStart: Int = 0,
        val velRangeEnd: Int = 127,
        // Tuning
        val coarseTune: Int = 0,
        val scaleTuning: Int = 100,
        // Modulation Envelope - SF2 native units
        val modEnvDelay: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val modEnvAttack: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val modEnvHold: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val modEnvDecay: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val modEnvSustain: Int = 0,  // centibels
        val modEnvRelease: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val modEnvToPitch: Int = 0,
        val modEnvToFilterFc: Int = 0,
        // Vibrato LFO - SF2 native units
        val vibLfoDelay: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val vibLfoFreq: Int = 0,
        val vibLfoToPitch: Int = 0,
        // Modulation LFO - SF2 native units
        val modLfoDelay: Int = Sf2Constants.TIMECENTS_DEFAULT_INSTANT,
        val modLfoFreq: Int = 0,
        val modLfoToPitch: Int = 0,
        val modLfoToFilterFc: Int = 0,
        val modLfoToVolume: Int = 0,
        // Exclusive class
        val exclusiveClass: Int = 0,
        // Key-to-envelope scaling
        val keyToVolEnvHold: Int = 0,
        val keyToVolEnvDecay: Int = 0,
        val keyToModEnvHold: Int = 0,
        val keyToModEnvDecay: Int = 0,
        // Fixed key/velocity
        val fixedKey: Int = -1,
        val fixedVelocity: Int = -1,
        // Sample header fields
        val pitchCorrection: Int = 0,
        // Modulators
        val modulators: List<Sf2ParsedModulator> = emptyList()
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is SampleData) return false
            return name == other.name && audioData.contentEquals(other.audioData)
        }

        override fun hashCode(): Int {
            var result = name.hashCode()
            result = 31 * result + audioData.contentHashCode()
            return result
        }
    }

    // Current clipboard content
    private var content: ClipboardContent? = null

    /**
     * Check if clipboard has content.
     */
    fun hasContent(): Boolean = content != null

    /**
     * Check if clipboard contains a program.
     */
    fun hasProgram(): Boolean = content is ClipboardContent.Program

    /**
     * Check if clipboard contains a preset.
     */
    fun hasPreset(): Boolean = content is ClipboardContent.Preset

    /**
     * Check if clipboard contains sample(s).
     */
    fun hasSamples(): Boolean = content is ClipboardContent.Sample || content is ClipboardContent.Samples

    /**
     * Get current content type description.
     */
    fun getContentDescription(): String {
        return when (val c = content) {
            is ClipboardContent.Program -> "Program: ${c.programName} (${c.instruments.size} instruments)"
            is ClipboardContent.Preset -> "Preset: ${c.presetName} (${c.samples.size} samples)"
            is ClipboardContent.Sample -> "Sample: ${c.sample.name}"
            is ClipboardContent.Samples -> "${c.samples.size} samples"
            null -> "Empty"
        }
    }

    /**
     * Clear clipboard content.
     */
    fun clear() {
        content = null
    }

    /**
     * Copy a program with all its instruments and samples.
     */
    suspend fun copyProgram(
        context: Context,
        program: Sf2ProgramEntity
    ) = withContext(Dispatchers.IO) {
        val repository = Sf2ProjectRepository(context)
        val presetZones = repository.getPresetZonesForProgram(program.id)

        val instrumentDataList = presetZones.mapNotNull { presetZone ->
            val samples = repository.getSamplesForInstrument(presetZone.instrumentId)
            val sampleDataList = samples.mapNotNull { entity ->
                loadSampleData(context, entity)
            }

            if (sampleDataList.isEmpty() && samples.isNotEmpty()) {
                null // Skip instruments where we couldn't load any samples
            } else {
                // Load instrument global parameters
                val instrument = repository.getInstrumentById(presetZone.instrumentId)
                val instrumentParams = if (instrument != null) {
                    InstrumentGlobalParams(
                        globalAttenuation = instrument.globalAttenuation,
                        globalCoarseTune = instrument.globalCoarseTune,
                        globalFineTune = instrument.globalFineTune,
                        globalVolEnvDelay = instrument.globalVolEnvDelay,
                        globalVolEnvAttack = instrument.globalVolEnvAttack,
                        globalVolEnvHold = instrument.globalVolEnvHold,
                        globalVolEnvDecay = instrument.globalVolEnvDecay,
                        globalVolEnvSustain = instrument.globalVolEnvSustain,
                        globalVolEnvRelease = instrument.globalVolEnvRelease,
                        globalModEnvDelay = instrument.globalModEnvDelay,
                        globalModEnvAttack = instrument.globalModEnvAttack,
                        globalModEnvHold = instrument.globalModEnvHold,
                        globalModEnvDecay = instrument.globalModEnvDecay,
                        globalModEnvSustain = instrument.globalModEnvSustain,
                        globalModEnvRelease = instrument.globalModEnvRelease,
                        globalModEnvToPitch = instrument.globalModEnvToPitch,
                        globalModEnvToFilterFc = instrument.globalModEnvToFilterFc,
                        globalVibLfoDelay = instrument.globalVibLfoDelay,
                        globalVibLfoFreq = instrument.globalVibLfoFreq,
                        globalVibLfoToPitch = instrument.globalVibLfoToPitch,
                        globalModLfoDelay = instrument.globalModLfoDelay,
                        globalModLfoFreq = instrument.globalModLfoFreq,
                        globalModLfoToPitch = instrument.globalModLfoToPitch,
                        globalModLfoToFilterFc = instrument.globalModLfoToFilterFc,
                        globalModLfoToVolume = instrument.globalModLfoToVolume,
                        globalFilterFc = instrument.globalFilterFc,
                        globalFilterQ = instrument.globalFilterQ,
                        globalChorusSend = instrument.globalChorusSend,
                        globalReverbSend = instrument.globalReverbSend,
                        globalPan = instrument.globalPan
                    )
                } else {
                    InstrumentGlobalParams()
                }

                InstrumentData(
                    name = presetZone.name,
                    programNumber = presetZone.programNumber,
                    bankNumber = presetZone.bankNumber,
                    samples = sampleDataList,
                    globalParams = instrumentParams,
                    // PGEN parameters specific to THIS zone
                    zoneParams = ClipboardContent.PresetGlobalParams(
                        pgenKeyRangeLow = presetZone.pgenKeyRangeLow,
                        pgenKeyRangeHigh = presetZone.pgenKeyRangeHigh,
                        pgenVelRangeLow = presetZone.pgenVelRangeLow,
                        pgenVelRangeHigh = presetZone.pgenVelRangeHigh,
                        pgenAttenuation = presetZone.pgenAttenuation,
                        pgenCoarseTune = presetZone.pgenCoarseTune,
                        pgenFineTune = presetZone.pgenFineTune,
                        pgenFilterFc = presetZone.pgenFilterFc,
                        pgenFilterQ = presetZone.pgenFilterQ,
                        pgenChorusSend = presetZone.pgenChorusSend,
                        pgenReverbSend = presetZone.pgenReverbSend,
                        pgenPan = presetZone.pgenPan,
                        pgenVolEnvDelay = presetZone.pgenVolEnvDelay,
                        pgenVolEnvAttack = presetZone.pgenVolEnvAttack,
                        pgenVolEnvHold = presetZone.pgenVolEnvHold,
                        pgenVolEnvDecay = presetZone.pgenVolEnvDecay,
                        pgenVolEnvSustain = presetZone.pgenVolEnvSustain,
                        pgenVolEnvRelease = presetZone.pgenVolEnvRelease,
                        pgenModEnvDelay = presetZone.pgenModEnvDelay,
                        pgenModEnvAttack = presetZone.pgenModEnvAttack,
                        pgenModEnvHold = presetZone.pgenModEnvHold,
                        pgenModEnvDecay = presetZone.pgenModEnvDecay,
                        pgenModEnvSustain = presetZone.pgenModEnvSustain,
                        pgenModEnvRelease = presetZone.pgenModEnvRelease,
                        pgenModEnvToPitch = presetZone.pgenModEnvToPitch,
                        pgenModEnvToFilterFc = presetZone.pgenModEnvToFilterFc,
                        pgenVibLfoDelay = presetZone.pgenVibLfoDelay,
                        pgenVibLfoFreq = presetZone.pgenVibLfoFreq,
                        pgenVibLfoToPitch = presetZone.pgenVibLfoToPitch,
                        pgenModLfoDelay = presetZone.pgenModLfoDelay,
                        pgenModLfoFreq = presetZone.pgenModLfoFreq,
                        pgenModLfoToPitch = presetZone.pgenModLfoToPitch,
                        pgenModLfoToFilterFc = presetZone.pgenModLfoToFilterFc,
                        pgenModLfoToVolume = presetZone.pgenModLfoToVolume
                    )
                )
            }
        }

        content = ClipboardContent.Program(
            programName = program.name,
            programNumber = program.programNumber,
            bankNumber = program.bankNumber,
            instruments = instrumentDataList
        )
    }

    /**
     * Copy a preset with all its samples.
     */
    suspend fun copyPreset(
        context: Context,
        preset: Sf2PresetEntity,
        samples: List<Sf2SampleEntity>
    ) = withContext(Dispatchers.IO) {
        val repository = Sf2ProjectRepository(context)
        val sampleDataList = samples.mapNotNull { entity ->
            loadSampleData(context, entity)
        }

        // Load instrument global parameters
        val instrument = repository.getInstrumentById(preset.instrumentId)
        val instrumentParams = if (instrument != null) {
            InstrumentGlobalParams(
                globalAttenuation = instrument.globalAttenuation,
                globalCoarseTune = instrument.globalCoarseTune,
                globalFineTune = instrument.globalFineTune,
                globalVolEnvDelay = instrument.globalVolEnvDelay,
                globalVolEnvAttack = instrument.globalVolEnvAttack,
                globalVolEnvHold = instrument.globalVolEnvHold,
                globalVolEnvDecay = instrument.globalVolEnvDecay,
                globalVolEnvSustain = instrument.globalVolEnvSustain,
                globalVolEnvRelease = instrument.globalVolEnvRelease,
                globalModEnvDelay = instrument.globalModEnvDelay,
                globalModEnvAttack = instrument.globalModEnvAttack,
                globalModEnvHold = instrument.globalModEnvHold,
                globalModEnvDecay = instrument.globalModEnvDecay,
                globalModEnvSustain = instrument.globalModEnvSustain,
                globalModEnvRelease = instrument.globalModEnvRelease,
                globalModEnvToPitch = instrument.globalModEnvToPitch,
                globalModEnvToFilterFc = instrument.globalModEnvToFilterFc,
                globalVibLfoDelay = instrument.globalVibLfoDelay,
                globalVibLfoFreq = instrument.globalVibLfoFreq,
                globalVibLfoToPitch = instrument.globalVibLfoToPitch,
                globalModLfoDelay = instrument.globalModLfoDelay,
                globalModLfoFreq = instrument.globalModLfoFreq,
                globalModLfoToPitch = instrument.globalModLfoToPitch,
                globalModLfoToFilterFc = instrument.globalModLfoToFilterFc,
                globalModLfoToVolume = instrument.globalModLfoToVolume,
                globalFilterFc = instrument.globalFilterFc,
                globalFilterQ = instrument.globalFilterQ,
                globalChorusSend = instrument.globalChorusSend,
                globalReverbSend = instrument.globalReverbSend,
                globalPan = instrument.globalPan
            )
        } else {
            InstrumentGlobalParams() // Default empty params
        }

        content = ClipboardContent.Preset(
            presetName = preset.name,
            programNumber = preset.programNumber,
            bankNumber = preset.bankNumber,
            samples = sampleDataList,
            instrumentGlobalParams = instrumentParams,
            presetGlobalParams = ClipboardContent.PresetGlobalParams(
                pgenKeyRangeLow = preset.pgenKeyRangeLow,
                pgenKeyRangeHigh = preset.pgenKeyRangeHigh,
                pgenVelRangeLow = preset.pgenVelRangeLow,
                pgenVelRangeHigh = preset.pgenVelRangeHigh,
                pgenAttenuation = preset.pgenAttenuation,
                pgenCoarseTune = preset.pgenCoarseTune,
                pgenFineTune = preset.pgenFineTune,
                pgenFilterFc = preset.pgenFilterFc,
                pgenFilterQ = preset.pgenFilterQ,
                pgenChorusSend = preset.pgenChorusSend,
                pgenReverbSend = preset.pgenReverbSend,
                pgenPan = preset.pgenPan,
                pgenVolEnvDelay = preset.pgenVolEnvDelay,
                pgenVolEnvAttack = preset.pgenVolEnvAttack,
                pgenVolEnvHold = preset.pgenVolEnvHold,
                pgenVolEnvDecay = preset.pgenVolEnvDecay,
                pgenVolEnvSustain = preset.pgenVolEnvSustain,
                pgenVolEnvRelease = preset.pgenVolEnvRelease,
                pgenModEnvDelay = preset.pgenModEnvDelay,
                pgenModEnvAttack = preset.pgenModEnvAttack,
                pgenModEnvHold = preset.pgenModEnvHold,
                pgenModEnvDecay = preset.pgenModEnvDecay,
                pgenModEnvSustain = preset.pgenModEnvSustain,
                pgenModEnvRelease = preset.pgenModEnvRelease,
                pgenModEnvToPitch = preset.pgenModEnvToPitch,
                pgenModEnvToFilterFc = preset.pgenModEnvToFilterFc,
                pgenVibLfoDelay = preset.pgenVibLfoDelay,
                pgenVibLfoFreq = preset.pgenVibLfoFreq,
                pgenVibLfoToPitch = preset.pgenVibLfoToPitch,
                pgenModLfoDelay = preset.pgenModLfoDelay,
                pgenModLfoFreq = preset.pgenModLfoFreq,
                pgenModLfoToPitch = preset.pgenModLfoToPitch,
                pgenModLfoToFilterFc = preset.pgenModLfoToFilterFc,
                pgenModLfoToVolume = preset.pgenModLfoToVolume
            )
        )
    }

    /**
     * Copy a single sample.
     */
    suspend fun copySample(
        context: Context,
        sample: Sf2SampleEntity
    ) = withContext(Dispatchers.IO) {
        val sampleData = loadSampleData(context, sample) ?: return@withContext
        content = ClipboardContent.Sample(sampleData)
    }

    /**
     * Copy multiple samples.
     */
    suspend fun copySamples(
        context: Context,
        samples: List<Sf2SampleEntity>
    ) = withContext(Dispatchers.IO) {
        val sampleDataList = samples.mapNotNull { loadSampleData(context, it) }
        if (sampleDataList.isNotEmpty()) {
            content = ClipboardContent.Samples(sampleDataList)
        }
    }

    /**
     * Paste clipboard content into an instrument.
     *
     * @param context Application context
     * @param targetInstrumentId The instrument to paste into
     * @return Number of samples pasted
     */
    suspend fun pasteToInstrument(
        context: Context,
        targetInstrumentId: Long
    ): Int = withContext(Dispatchers.IO) {
        val repository = Sf2ProjectRepository(context)
        var pastedCount = 0

        when (val c = content) {
            is ClipboardContent.Program -> {
                // For a program, paste all samples from all instruments
                for (instrument in c.instruments) {
                    for (sample in instrument.samples) {
                        addSampleToInstrument(repository, targetInstrumentId, sample)
                        pastedCount++
                    }
                }
            }
            is ClipboardContent.Preset -> {
                // Apply instrument global parameters if available
                val instrument = repository.getInstrumentById(targetInstrumentId)
                if (instrument != null) {
                    val updatedInstrument = instrument.copy(
                        globalAttenuation = c.instrumentGlobalParams.globalAttenuation,
                        globalCoarseTune = c.instrumentGlobalParams.globalCoarseTune,
                        globalFineTune = c.instrumentGlobalParams.globalFineTune,
                        globalVolEnvDelay = c.instrumentGlobalParams.globalVolEnvDelay,
                        globalVolEnvAttack = c.instrumentGlobalParams.globalVolEnvAttack,
                        globalVolEnvHold = c.instrumentGlobalParams.globalVolEnvHold,
                        globalVolEnvDecay = c.instrumentGlobalParams.globalVolEnvDecay,
                        globalVolEnvSustain = c.instrumentGlobalParams.globalVolEnvSustain,
                        globalVolEnvRelease = c.instrumentGlobalParams.globalVolEnvRelease,
                        globalModEnvDelay = c.instrumentGlobalParams.globalModEnvDelay,
                        globalModEnvAttack = c.instrumentGlobalParams.globalModEnvAttack,
                        globalModEnvHold = c.instrumentGlobalParams.globalModEnvHold,
                        globalModEnvDecay = c.instrumentGlobalParams.globalModEnvDecay,
                        globalModEnvSustain = c.instrumentGlobalParams.globalModEnvSustain,
                        globalModEnvRelease = c.instrumentGlobalParams.globalModEnvRelease,
                        globalModEnvToPitch = c.instrumentGlobalParams.globalModEnvToPitch,
                        globalModEnvToFilterFc = c.instrumentGlobalParams.globalModEnvToFilterFc,
                        globalVibLfoDelay = c.instrumentGlobalParams.globalVibLfoDelay,
                        globalVibLfoFreq = c.instrumentGlobalParams.globalVibLfoFreq,
                        globalVibLfoToPitch = c.instrumentGlobalParams.globalVibLfoToPitch,
                        globalModLfoDelay = c.instrumentGlobalParams.globalModLfoDelay,
                        globalModLfoFreq = c.instrumentGlobalParams.globalModLfoFreq,
                        globalModLfoToPitch = c.instrumentGlobalParams.globalModLfoToPitch,
                        globalModLfoToFilterFc = c.instrumentGlobalParams.globalModLfoToFilterFc,
                        globalModLfoToVolume = c.instrumentGlobalParams.globalModLfoToVolume,
                        globalFilterFc = c.instrumentGlobalParams.globalFilterFc,
                        globalFilterQ = c.instrumentGlobalParams.globalFilterQ,
                        globalChorusSend = c.instrumentGlobalParams.globalChorusSend,
                        globalReverbSend = c.instrumentGlobalParams.globalReverbSend,
                        globalPan = c.instrumentGlobalParams.globalPan
                    )
                    repository.updateInstrument(updatedInstrument)
                }

                // Paste samples
                for (sample in c.samples) {
                    addSampleToInstrument(repository, targetInstrumentId, sample)
                    pastedCount++
                }
            }
            is ClipboardContent.Sample -> {
                addSampleToInstrument(repository, targetInstrumentId, c.sample)
                pastedCount = 1
            }
            is ClipboardContent.Samples -> {
                for (sample in c.samples) {
                    addSampleToInstrument(repository, targetInstrumentId, sample)
                    pastedCount++
                }
            }
            null -> { /* Nothing to paste */ }
        }

        pastedCount
    }

    /**
     * Paste clipboard content into a preset, applying global preset parameters.
     */
    suspend fun pasteToPreset(
        context: Context,
        targetPresetId: Long
    ): Int = withContext(Dispatchers.IO) {
        val repository = Sf2ProjectRepository(context)
        val preset = repository.getPresetById(targetPresetId)
        if (preset == null) return@withContext 0

        // Apply preset global parameters if clipboard contains a Preset
        val c = content
        if (c is ClipboardContent.Preset) {
            val updatedPreset = preset.copy(
                pgenKeyRangeLow = c.presetGlobalParams.pgenKeyRangeLow,
                pgenKeyRangeHigh = c.presetGlobalParams.pgenKeyRangeHigh,
                pgenVelRangeLow = c.presetGlobalParams.pgenVelRangeLow,
                pgenVelRangeHigh = c.presetGlobalParams.pgenVelRangeHigh,
                pgenAttenuation = c.presetGlobalParams.pgenAttenuation,
                pgenCoarseTune = c.presetGlobalParams.pgenCoarseTune,
                pgenFineTune = c.presetGlobalParams.pgenFineTune,
                pgenFilterFc = c.presetGlobalParams.pgenFilterFc,
                pgenFilterQ = c.presetGlobalParams.pgenFilterQ,
                pgenChorusSend = c.presetGlobalParams.pgenChorusSend,
                pgenReverbSend = c.presetGlobalParams.pgenReverbSend,
                pgenPan = c.presetGlobalParams.pgenPan,
                pgenVolEnvDelay = c.presetGlobalParams.pgenVolEnvDelay,
                pgenVolEnvAttack = c.presetGlobalParams.pgenVolEnvAttack,
                pgenVolEnvHold = c.presetGlobalParams.pgenVolEnvHold,
                pgenVolEnvDecay = c.presetGlobalParams.pgenVolEnvDecay,
                pgenVolEnvSustain = c.presetGlobalParams.pgenVolEnvSustain,
                pgenVolEnvRelease = c.presetGlobalParams.pgenVolEnvRelease,
                pgenModEnvDelay = c.presetGlobalParams.pgenModEnvDelay,
                pgenModEnvAttack = c.presetGlobalParams.pgenModEnvAttack,
                pgenModEnvHold = c.presetGlobalParams.pgenModEnvHold,
                pgenModEnvDecay = c.presetGlobalParams.pgenModEnvDecay,
                pgenModEnvSustain = c.presetGlobalParams.pgenModEnvSustain,
                pgenModEnvRelease = c.presetGlobalParams.pgenModEnvRelease,
                pgenModEnvToPitch = c.presetGlobalParams.pgenModEnvToPitch,
                pgenModEnvToFilterFc = c.presetGlobalParams.pgenModEnvToFilterFc,
                pgenVibLfoDelay = c.presetGlobalParams.pgenVibLfoDelay,
                pgenVibLfoFreq = c.presetGlobalParams.pgenVibLfoFreq,
                pgenVibLfoToPitch = c.presetGlobalParams.pgenVibLfoToPitch,
                pgenModLfoDelay = c.presetGlobalParams.pgenModLfoDelay,
                pgenModLfoFreq = c.presetGlobalParams.pgenModLfoFreq,
                pgenModLfoToPitch = c.presetGlobalParams.pgenModLfoToPitch,
                pgenModLfoToFilterFc = c.presetGlobalParams.pgenModLfoToFilterFc,
                pgenModLfoToVolume = c.presetGlobalParams.pgenModLfoToVolume
            )
            repository.updatePreset(updatedPreset)
        }

        // Paste samples and instrument parameters
        pasteToInstrument(context, preset.instrumentId)
    }

    /**
     * Paste clipboard content as a new preset zone with instrument in a project.
     * Only works if clipboard contains a Preset.
     *
     * @param context Application context
     * @param targetProjectId The project to paste into
     * @return The new preset zone ID, or null if paste failed
     */
    suspend fun pasteAsNewPreset(
        context: Context,
        targetProjectId: Long
    ): Long? = withContext(Dispatchers.IO) {
        val c = content as? ClipboardContent.Preset ?: return@withContext null
        val repository = Sf2ProjectRepository(context)

        // Create new instrument
        val newInstrumentId = repository.createInstrument(
            projectId = targetProjectId,
            name = c.presetName
        )

        // Create new preset zone pointing to the instrument
        val newPresetZoneId = repository.createPresetZone(
            projectId = targetProjectId,
            instrumentId = newInstrumentId,
            name = c.presetName,
            programNumber = c.programNumber,
            bankNumber = c.bankNumber
        )

        // Add all samples to the instrument
        for (sample in c.samples) {
            addSampleToInstrument(repository, newInstrumentId, sample)
        }

        newPresetZoneId
    }

    /**
     * Paste clipboard program as a new program in a project.
     * Only works if clipboard contains a Program.
     *
     * @param context Application context
     * @param targetProjectId The project to paste into
     * @return The new program ID, or null if paste failed
     */
    suspend fun pasteProgramToProject(
        context: Context,
        targetProjectId: Long
    ): Long? = withContext(Dispatchers.IO) {
        val c = content as? ClipboardContent.Program ?: return@withContext null
        val repository = Sf2ProjectRepository(context)

        // Create new program
        val newProgramId = repository.createProgram(
            projectId = targetProjectId,
            name = c.programName,
            programNumber = c.programNumber,
            bankNumber = c.bankNumber
        )

        // Create instruments and preset zones for each
        for (instrumentData in c.instruments) {
            // Create instrument
            val instrumentId = repository.createInstrument(
                projectId = targetProjectId,
                name = instrumentData.name
            )

            // Apply instrument global parameters
            val instrument = repository.getInstrumentById(instrumentId)
            if (instrument != null) {
                val updatedInstrument = instrument.copy(
                    globalAttenuation = instrumentData.globalParams.globalAttenuation,
                    globalCoarseTune = instrumentData.globalParams.globalCoarseTune,
                    globalFineTune = instrumentData.globalParams.globalFineTune,
                    globalVolEnvDelay = instrumentData.globalParams.globalVolEnvDelay,
                    globalVolEnvAttack = instrumentData.globalParams.globalVolEnvAttack,
                    globalVolEnvHold = instrumentData.globalParams.globalVolEnvHold,
                    globalVolEnvDecay = instrumentData.globalParams.globalVolEnvDecay,
                    globalVolEnvSustain = instrumentData.globalParams.globalVolEnvSustain,
                    globalVolEnvRelease = instrumentData.globalParams.globalVolEnvRelease,
                    globalModEnvDelay = instrumentData.globalParams.globalModEnvDelay,
                    globalModEnvAttack = instrumentData.globalParams.globalModEnvAttack,
                    globalModEnvHold = instrumentData.globalParams.globalModEnvHold,
                    globalModEnvDecay = instrumentData.globalParams.globalModEnvDecay,
                    globalModEnvSustain = instrumentData.globalParams.globalModEnvSustain,
                    globalModEnvRelease = instrumentData.globalParams.globalModEnvRelease,
                    globalModEnvToPitch = instrumentData.globalParams.globalModEnvToPitch,
                    globalModEnvToFilterFc = instrumentData.globalParams.globalModEnvToFilterFc,
                    globalVibLfoDelay = instrumentData.globalParams.globalVibLfoDelay,
                    globalVibLfoFreq = instrumentData.globalParams.globalVibLfoFreq,
                    globalVibLfoToPitch = instrumentData.globalParams.globalVibLfoToPitch,
                    globalModLfoDelay = instrumentData.globalParams.globalModLfoDelay,
                    globalModLfoFreq = instrumentData.globalParams.globalModLfoFreq,
                    globalModLfoToPitch = instrumentData.globalParams.globalModLfoToPitch,
                    globalModLfoToFilterFc = instrumentData.globalParams.globalModLfoToFilterFc,
                    globalModLfoToVolume = instrumentData.globalParams.globalModLfoToVolume,
                    globalFilterFc = instrumentData.globalParams.globalFilterFc,
                    globalFilterQ = instrumentData.globalParams.globalFilterQ,
                    globalChorusSend = instrumentData.globalParams.globalChorusSend,
                    globalReverbSend = instrumentData.globalParams.globalReverbSend,
                    globalPan = instrumentData.globalParams.globalPan
                )
                repository.updateInstrument(updatedInstrument)
            }

            // Create preset zone with PGEN parameters
            repository.createPresetZoneForProgramWithPgen(
                projectId = targetProjectId,
                programId = newProgramId,
                instrumentId = instrumentId,
                name = instrumentData.name,
                programNumber = instrumentData.programNumber,
                bankNumber = instrumentData.bankNumber,
                pgenParams = Sf2ProjectRepository.PresetZoneParams(
                    keyRangeLow = instrumentData.zoneParams.pgenKeyRangeLow,
                    keyRangeHigh = instrumentData.zoneParams.pgenKeyRangeHigh,
                    velRangeLow = instrumentData.zoneParams.pgenVelRangeLow,
                    velRangeHigh = instrumentData.zoneParams.pgenVelRangeHigh,
                    attenuation = instrumentData.zoneParams.pgenAttenuation,
                    coarseTune = instrumentData.zoneParams.pgenCoarseTune,
                    fineTune = instrumentData.zoneParams.pgenFineTune,
                    filterFc = instrumentData.zoneParams.pgenFilterFc,
                    filterQ = instrumentData.zoneParams.pgenFilterQ,
                    chorusSend = instrumentData.zoneParams.pgenChorusSend,
                    reverbSend = instrumentData.zoneParams.pgenReverbSend,
                    pan = instrumentData.zoneParams.pgenPan,
                    volEnvDelay = instrumentData.zoneParams.pgenVolEnvDelay,
                    volEnvAttack = instrumentData.zoneParams.pgenVolEnvAttack,
                    volEnvHold = instrumentData.zoneParams.pgenVolEnvHold,
                    volEnvDecay = instrumentData.zoneParams.pgenVolEnvDecay,
                    volEnvSustain = instrumentData.zoneParams.pgenVolEnvSustain,
                    volEnvRelease = instrumentData.zoneParams.pgenVolEnvRelease,
                    modEnvDelay = instrumentData.zoneParams.pgenModEnvDelay,
                    modEnvAttack = instrumentData.zoneParams.pgenModEnvAttack,
                    modEnvHold = instrumentData.zoneParams.pgenModEnvHold,
                    modEnvDecay = instrumentData.zoneParams.pgenModEnvDecay,
                    modEnvSustain = instrumentData.zoneParams.pgenModEnvSustain,
                    modEnvRelease = instrumentData.zoneParams.pgenModEnvRelease,
                    modEnvToPitch = instrumentData.zoneParams.pgenModEnvToPitch,
                    modEnvToFilterFc = instrumentData.zoneParams.pgenModEnvToFilterFc,
                    vibLfoDelay = instrumentData.zoneParams.pgenVibLfoDelay,
                    vibLfoFreq = instrumentData.zoneParams.pgenVibLfoFreq,
                    vibLfoToPitch = instrumentData.zoneParams.pgenVibLfoToPitch,
                    modLfoDelay = instrumentData.zoneParams.pgenModLfoDelay,
                    modLfoFreq = instrumentData.zoneParams.pgenModLfoFreq,
                    modLfoToPitch = instrumentData.zoneParams.pgenModLfoToPitch,
                    modLfoToFilterFc = instrumentData.zoneParams.pgenModLfoToFilterFc,
                    modLfoToVolume = instrumentData.zoneParams.pgenModLfoToVolume
                )
            )

            // Add all samples to this instrument
            for (sample in instrumentData.samples) {
                addSampleToInstrument(repository, instrumentId, sample)
            }
        }

        newProgramId
    }

    // ==================== Private helpers ====================

    private suspend fun loadSampleData(context: Context, entity: Sf2SampleEntity): SampleData? {
        // Load audio data based on whether sample is extracted or patch-based
        val audioData: ShortArray = if (!entity.isExtracted && entity.sourceFilePath != null) {
            // Patch-based: read from SF2 source file
            loadAudioFromSf2Source(
                File(entity.sourceFilePath),
                entity.sourceSmplOffset,
                entity.sourceSampleSize
            ) ?: return null
        } else {
            // Extracted: read from WAV file
            val audioFile = File(entity.audioFilePath)
            WavUtils.loadWavFile(audioFile) ?: return null
        }

        return SampleData(
            name = entity.name,
            audioData = audioData,
            sampleRate = entity.sampleRate,
            rootNote = entity.rootNote,
            keyRangeStart = entity.keyRangeStart,
            keyRangeEnd = entity.keyRangeEnd,
            loopStart = entity.loopStart,
            loopEnd = entity.loopEnd,
            hasLoop = entity.hasLoop,
            attenuation = entity.attenuation,
            fineTuneCents = entity.fineTuneCents,
            // Volume Envelope - SF2 native units
            volEnvDelay = entity.volEnvDelay,
            volEnvAttack = entity.volEnvAttack,
            volEnvHold = entity.volEnvHold,
            volEnvDecay = entity.volEnvDecay,
            volEnvSustain = entity.volEnvSustain,
            volEnvRelease = entity.volEnvRelease,
            // Filter - SF2 native units
            filterFc = entity.filterFc,
            filterQ = entity.filterQ,
            chorusSend = entity.chorusSend,
            reverbSend = entity.reverbSend,
            pan = entity.pan,
            // Advanced SF2 parameters
            velRangeStart = entity.velRangeStart,
            velRangeEnd = entity.velRangeEnd,
            coarseTune = entity.coarseTune,
            scaleTuning = entity.scaleTuning,
            // Modulation Envelope - SF2 native units
            modEnvDelay = entity.modEnvDelay,
            modEnvAttack = entity.modEnvAttack,
            modEnvHold = entity.modEnvHold,
            modEnvDecay = entity.modEnvDecay,
            modEnvSustain = entity.modEnvSustain,
            modEnvRelease = entity.modEnvRelease,
            modEnvToPitch = entity.modEnvToPitch,
            modEnvToFilterFc = entity.modEnvToFilterFc,
            // Vibrato LFO - SF2 native units
            vibLfoDelay = entity.vibLfoDelay,
            vibLfoFreq = entity.vibLfoFreq,
            vibLfoToPitch = entity.vibLfoToPitch,
            // Modulation LFO - SF2 native units
            modLfoDelay = entity.modLfoDelay,
            modLfoFreq = entity.modLfoFreq,
            modLfoToPitch = entity.modLfoToPitch,
            modLfoToFilterFc = entity.modLfoToFilterFc,
            modLfoToVolume = entity.modLfoToVolume,
            exclusiveClass = entity.exclusiveClass,
            // Key-to-envelope scaling
            keyToVolEnvHold = entity.keyToVolEnvHold,
            keyToVolEnvDecay = entity.keyToVolEnvDecay,
            keyToModEnvHold = entity.keyToModEnvHold,
            keyToModEnvDecay = entity.keyToModEnvDecay,
            // Fixed key/velocity
            fixedKey = entity.fixedKey,
            fixedVelocity = entity.fixedVelocity,
            // Sample header fields
            pitchCorrection = entity.pitchCorrection,
            // Modulators - load from database
            modulators = loadModulators(context, entity.id)
        )
    }

    private suspend fun loadModulators(context: Context, sampleId: Long): List<Sf2ParsedModulator> {
        val repository = Sf2ProjectRepository(context)
        val modulatorEntities = repository.getModulatorsForSample(sampleId)
        return modulatorEntities.map { mod ->
            Sf2ParsedModulator(
                srcOper = mod.srcOper,
                destOper = mod.destOper,
                amount = mod.amount,
                amtSrcOper = mod.amtSrcOper,
                transOper = mod.transOper
            )
        }
    }

    private suspend fun addSampleToInstrument(
        repository: Sf2ProjectRepository,
        instrumentId: Long,
        sample: SampleData
    ) {
        repository.addSampleToInstrument(
            instrumentId = instrumentId,
            name = sample.name,
            samples = sample.audioData,
            sampleRate = sample.sampleRate,
            rootNote = sample.rootNote,
            keyRangeStart = sample.keyRangeStart,
            keyRangeEnd = sample.keyRangeEnd,
            loopStart = sample.loopStart,
            loopEnd = sample.loopEnd,
            hasLoop = sample.hasLoop,
            attenuation = sample.attenuation,
            fineTuneCents = sample.fineTuneCents,
            // Volume Envelope - SF2 native units
            volEnvDelay = sample.volEnvDelay,
            volEnvAttack = sample.volEnvAttack,
            volEnvHold = sample.volEnvHold,
            volEnvDecay = sample.volEnvDecay,
            volEnvSustain = sample.volEnvSustain,
            volEnvRelease = sample.volEnvRelease,
            // Filter - SF2 native units
            filterFc = sample.filterFc,
            filterQ = sample.filterQ,
            chorusSend = sample.chorusSend,
            reverbSend = sample.reverbSend,
            pan = sample.pan,
            // Advanced SF2 parameters
            velRangeStart = sample.velRangeStart,
            velRangeEnd = sample.velRangeEnd,
            coarseTune = sample.coarseTune,
            scaleTuning = sample.scaleTuning,
            // Modulation Envelope - SF2 native units
            modEnvDelay = sample.modEnvDelay,
            modEnvAttack = sample.modEnvAttack,
            modEnvHold = sample.modEnvHold,
            modEnvDecay = sample.modEnvDecay,
            modEnvSustain = sample.modEnvSustain,
            modEnvRelease = sample.modEnvRelease,
            modEnvToPitch = sample.modEnvToPitch,
            modEnvToFilterFc = sample.modEnvToFilterFc,
            // Vibrato LFO - SF2 native units
            vibLfoDelay = sample.vibLfoDelay,
            vibLfoFreq = sample.vibLfoFreq,
            vibLfoToPitch = sample.vibLfoToPitch,
            // Modulation LFO - SF2 native units
            modLfoDelay = sample.modLfoDelay,
            modLfoFreq = sample.modLfoFreq,
            modLfoToPitch = sample.modLfoToPitch,
            modLfoToFilterFc = sample.modLfoToFilterFc,
            modLfoToVolume = sample.modLfoToVolume,
            exclusiveClass = sample.exclusiveClass,
            // Key-to-envelope scaling
            keyToVolEnvHold = sample.keyToVolEnvHold,
            keyToVolEnvDecay = sample.keyToVolEnvDecay,
            keyToModEnvHold = sample.keyToModEnvHold,
            keyToModEnvDecay = sample.keyToModEnvDecay,
            // Fixed key/velocity
            fixedKey = sample.fixedKey,
            fixedVelocity = sample.fixedVelocity,
            // Sample header fields
            pitchCorrection = sample.pitchCorrection,
            // Modulators
            modulators = sample.modulators
        )
    }

    /**
     * Load audio data directly from SF2 source file at specified offset.
     * Used for patch-based imported samples that haven't been extracted.
     */
    private fun loadAudioFromSf2Source(sourceFile: File, offset: Long, size: Long): ShortArray? {
        if (!sourceFile.exists() || size <= 0) return null

        return try {
            java.io.RandomAccessFile(sourceFile, "r").use { raf ->
                raf.seek(offset)
                val numSamples = (size / 2).toInt()
                val samples = ShortArray(numSamples)
                val buffer = ByteArray(size.toInt())
                val bytesRead = raf.read(buffer)

                if (bytesRead < size) return null

                // Convert bytes to shorts (little-endian, SF2 format)
                for (i in 0 until numSamples) {
                    val lo = buffer[i * 2].toInt() and 0xFF
                    val hi = buffer[i * 2 + 1].toInt()
                    samples[i] = ((hi shl 8) or lo).toShort()
                }
                samples
            }
        } catch (e: Exception) {
            null
        }
    }
}
