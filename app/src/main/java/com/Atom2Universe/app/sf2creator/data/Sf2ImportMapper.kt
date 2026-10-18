package com.Atom2Universe.app.sf2creator.data

import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.BASE_ORIGINAL_PITCH
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.FULL_RANGE
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_INSTRUMENT
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_KEYNUM
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_KEY_RANGE
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_OVERRIDING_ROOT_KEY
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_SAMPLE_ID
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_SAMPLE_MODES
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_VELOCITY
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.GEN_VEL_RANGE
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.rangeHigh
import com.Atom2Universe.app.sf2creator.data.Sf2ZoneGenerators.rangeLow
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedInstrument
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedPreset
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedPresetZone
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedSample
import com.Atom2Universe.app.sf2creator.reader.Sf2ParsedZone

/**
 * Builds the project entities of an imported SF2, keeping each zone's generators
 * (see [Sf2ZoneGenerators]).
 *
 * The editable parameters show what each zone actually plays. For instrument zones, the
 * parameters that have no instrument-level field in the app (loop mode, root key, key and
 * velocity ranges, fixed key and velocity) take the value of the global zone when the zone
 * does not set them; the others keep the zone's own value and the global zone is edited on
 * the instrument.
 */
object Sf2ImportMapper {

    private const val INSTANT = -12000

    fun instrument(projectId: Long, name: String, source: Sf2ParsedInstrument): Sf2InstrumentEntity {
        val g = source.globalGenerators - GEN_SAMPLE_ID
        val entity = Sf2InstrumentEntity(
            projectId = projectId,
            name = name,
            globalAttenuation = g[48] ?: 0,
            globalCoarseTune = g[51] ?: 0,
            globalFineTune = g[52] ?: 0,
            globalVolEnvDelay = g[33] ?: 0,
            globalVolEnvAttack = g[34] ?: 0,
            globalVolEnvHold = g[35] ?: 0,
            globalVolEnvDecay = g[36] ?: 0,
            globalVolEnvSustain = g[37] ?: 0,
            globalVolEnvRelease = g[38] ?: 0,
            globalModEnvDelay = g[25] ?: 0,
            globalModEnvAttack = g[26] ?: 0,
            globalModEnvHold = g[27] ?: 0,
            globalModEnvDecay = g[28] ?: 0,
            globalModEnvSustain = g[29] ?: 0,
            globalModEnvRelease = g[30] ?: 0,
            globalModEnvToPitch = g[7] ?: 0,
            globalModEnvToFilterFc = g[11] ?: 0,
            globalVibLfoDelay = g[23] ?: 0,
            globalVibLfoFreq = g[24] ?: 0,
            globalVibLfoToPitch = g[6] ?: 0,
            globalModLfoDelay = g[21] ?: 0,
            globalModLfoFreq = g[22] ?: 0,
            globalModLfoToPitch = g[5] ?: 0,
            globalModLfoToFilterFc = g[10] ?: 0,
            globalModLfoToVolume = g[13] ?: 0,
            globalFilterFc = g[8] ?: 0,
            globalFilterQ = g[9] ?: 0,
            globalChorusSend = g[15] ?: 0,
            globalReverbSend = g[16] ?: 0,
            globalPan = g[17] ?: 0,
            globalKeyToModEnvHold = g[31] ?: 0,
            globalKeyToModEnvDecay = g[32] ?: 0,
            globalKeyToVolEnvHold = g[39] ?: 0,
            globalKeyToVolEnvDecay = g[40] ?: 0,
            globalScaleTuning = g[56] ?: 0,
            globalExclusiveClass = g[57] ?: 0,
            importedGenerators = Sf2ZoneGenerators.encode(g)
        )
        return entity.copy(importBaseline = Sf2ZoneGenerators.encode(Sf2ZoneGenerators.instrumentGlobalView(entity)))
    }

    /**
     * Instrument zone of [instrument] playing [sample]. The audio reference (source file or
     * WAV) is left to the caller.
     */
    fun sampleZone(
        instrumentId: Long,
        name: String,
        zone: Sf2ParsedZone,
        instrument: Sf2ParsedInstrument,
        sample: Sf2ParsedSample
    ): Sf2SampleEntity {
        val local = zone.generators - GEN_SAMPLE_ID
        val global = instrument.globalGenerators
        fun own(gen: Int, default: Int) = local[gen] ?: default
        fun resolved(gen: Int) = local[gen] ?: global[gen]

        val keyRange = resolved(GEN_KEY_RANGE) ?: FULL_RANGE
        val velRange = resolved(GEN_VEL_RANGE) ?: FULL_RANGE
        val sampleModes = resolved(GEN_SAMPLE_MODES) ?: 0
        val frames = (sample.end - sample.start).toInt().coerceAtLeast(1)

        val entity = Sf2SampleEntity(
            instrumentId = instrumentId,
            name = name,
            audioFilePath = "",
            sampleRate = sample.sampleRate,
            rootNote = resolved(GEN_OVERRIDING_ROOT_KEY) ?: sample.originalPitch,
            keyRangeStart = rangeLow(keyRange),
            keyRangeEnd = rangeHigh(keyRange),
            velRangeStart = rangeLow(velRange),
            velRangeEnd = rangeHigh(velRange),
            // Loop points relative to the sample start; the end is inclusive in the app
            loopStart = (sample.loopStart - sample.start).toInt().coerceAtLeast(0),
            loopEnd = (sample.loopEnd - sample.start - 1).toInt().coerceIn(0, frames - 1),
            hasLoop = (sampleModes and 1) != 0 && sample.hasLoop(),
            sampleModes = sampleModes,
            attenuation = own(48, 0),
            coarseTune = own(51, 0),
            fineTuneCents = own(52, 0),
            scaleTuning = own(56, 100),
            volEnvDelay = own(33, INSTANT),
            volEnvAttack = own(34, INSTANT),
            volEnvHold = own(35, INSTANT),
            volEnvDecay = own(36, INSTANT),
            volEnvSustain = own(37, 0),
            volEnvRelease = own(38, INSTANT),
            modEnvDelay = own(25, INSTANT),
            modEnvAttack = own(26, INSTANT),
            modEnvHold = own(27, INSTANT),
            modEnvDecay = own(28, INSTANT),
            modEnvSustain = own(29, 0),
            modEnvRelease = own(30, INSTANT),
            modEnvToPitch = own(7, 0),
            modEnvToFilterFc = own(11, 0),
            vibLfoDelay = own(23, INSTANT),
            vibLfoFreq = own(24, 0),
            vibLfoToPitch = own(6, 0),
            modLfoDelay = own(21, INSTANT),
            modLfoFreq = own(22, 0),
            modLfoToPitch = own(5, 0),
            modLfoToFilterFc = own(10, 0),
            modLfoToVolume = own(13, 0),
            filterFc = own(8, 13500),
            filterQ = own(9, 0),
            chorusSend = own(15, 0),
            reverbSend = own(16, 0),
            pan = own(17, 0),
            exclusiveClass = own(57, 0),
            keyToVolEnvHold = own(39, 0),
            keyToVolEnvDecay = own(40, 0),
            keyToModEnvHold = own(31, 0),
            keyToModEnvDecay = own(32, 0),
            fixedKey = resolved(GEN_KEYNUM) ?: -1,
            fixedVelocity = resolved(GEN_VELOCITY) ?: -1,
            pitchCorrection = sample.pitchCorrection,
            importedGenerators = Sf2ZoneGenerators.encode(local)
        )
        val baseline = Sf2ZoneGenerators.sampleView(entity) + (BASE_ORIGINAL_PITCH to sample.originalPitch)
        return entity.copy(importBaseline = Sf2ZoneGenerators.encode(baseline))
    }

    /** Preset zone of [preset] playing [instrumentId]. */
    fun presetZone(
        projectId: Long,
        programId: Long,
        instrumentId: Long,
        name: String,
        preset: Sf2ParsedPreset,
        zone: Sf2ParsedPresetZone
    ): Sf2PresetEntity {
        val local = zone.generators - GEN_INSTRUMENT
        fun own(gen: Int) = local[gen] ?: 0
        val keyRange = local[GEN_KEY_RANGE] ?: preset.globalGenerators[GEN_KEY_RANGE] ?: FULL_RANGE
        val velRange = local[GEN_VEL_RANGE] ?: preset.globalGenerators[GEN_VEL_RANGE] ?: FULL_RANGE

        val entity = Sf2PresetEntity(
            projectId = projectId,
            programId = programId,
            instrumentId = instrumentId,
            name = name,
            programNumber = preset.programNumber,
            bankNumber = preset.bankNumber,
            pgenKeyRangeLow = rangeLow(keyRange),
            pgenKeyRangeHigh = rangeHigh(keyRange),
            pgenVelRangeLow = rangeLow(velRange),
            pgenVelRangeHigh = rangeHigh(velRange),
            pgenAttenuation = own(48),
            pgenCoarseTune = own(51),
            pgenFineTune = own(52),
            pgenFilterFc = own(8),
            pgenFilterQ = own(9),
            pgenChorusSend = own(15),
            pgenReverbSend = own(16),
            pgenPan = own(17),
            pgenVolEnvDelay = own(33),
            pgenVolEnvAttack = own(34),
            pgenVolEnvHold = own(35),
            pgenVolEnvDecay = own(36),
            pgenVolEnvSustain = own(37),
            pgenVolEnvRelease = own(38),
            pgenModEnvDelay = own(25),
            pgenModEnvAttack = own(26),
            pgenModEnvHold = own(27),
            pgenModEnvDecay = own(28),
            pgenModEnvSustain = own(29),
            pgenModEnvRelease = own(30),
            pgenModEnvToPitch = own(7),
            pgenModEnvToFilterFc = own(11),
            pgenVibLfoDelay = own(23),
            pgenVibLfoFreq = own(24),
            pgenVibLfoToPitch = own(6),
            pgenModLfoDelay = own(21),
            pgenModLfoFreq = own(22),
            pgenModLfoToPitch = own(5),
            pgenModLfoToFilterFc = own(10),
            pgenModLfoToVolume = own(13),
            pgenKeyToModEnvHold = own(31),
            pgenKeyToModEnvDecay = own(32),
            pgenKeyToVolEnvHold = own(39),
            pgenKeyToVolEnvDecay = own(40),
            pgenScaleTuning = own(56),
            pgenExclusiveClass = own(57),
            importedGenerators = Sf2ZoneGenerators.encode(local)
        )
        return entity.copy(importBaseline = Sf2ZoneGenerators.encode(Sf2ZoneGenerators.presetZoneView(entity)))
    }

    /** [program] with the global zone of [preset]. */
    fun programGlobals(program: Sf2ProgramEntity, preset: Sf2ParsedPreset): Sf2ProgramEntity {
        val g = preset.globalGenerators - GEN_INSTRUMENT
        val entity = program.copy(
            globalAttenuation = g[48] ?: 0,
            globalCoarseTune = g[51] ?: 0,
            globalFineTune = g[52] ?: 0,
            globalVolEnvDelay = g[33] ?: 0,
            globalVolEnvAttack = g[34] ?: 0,
            globalVolEnvHold = g[35] ?: 0,
            globalVolEnvDecay = g[36] ?: 0,
            globalVolEnvSustain = g[37] ?: 0,
            globalVolEnvRelease = g[38] ?: 0,
            globalModEnvDelay = g[25] ?: 0,
            globalModEnvAttack = g[26] ?: 0,
            globalModEnvHold = g[27] ?: 0,
            globalModEnvDecay = g[28] ?: 0,
            globalModEnvSustain = g[29] ?: 0,
            globalModEnvRelease = g[30] ?: 0,
            globalModEnvToPitch = g[7] ?: 0,
            globalModEnvToFilterFc = g[11] ?: 0,
            globalVibLfoDelay = g[23] ?: 0,
            globalVibLfoFreq = g[24] ?: 0,
            globalVibLfoToPitch = g[6] ?: 0,
            globalModLfoDelay = g[21] ?: 0,
            globalModLfoFreq = g[22] ?: 0,
            globalModLfoToPitch = g[5] ?: 0,
            globalModLfoToFilterFc = g[10] ?: 0,
            globalModLfoToVolume = g[13] ?: 0,
            globalFilterFc = g[8] ?: 0,
            globalFilterQ = g[9] ?: 0,
            globalChorusSend = g[15] ?: 0,
            globalReverbSend = g[16] ?: 0,
            globalPan = g[17] ?: 0,
            globalKeyToModEnvHold = g[31] ?: 0,
            globalKeyToModEnvDecay = g[32] ?: 0,
            globalKeyToVolEnvHold = g[39] ?: 0,
            globalKeyToVolEnvDecay = g[40] ?: 0,
            globalScaleTuning = g[56] ?: 0,
            globalExclusiveClass = g[57] ?: 0,
            importedGenerators = Sf2ZoneGenerators.encode(g)
        )
        return entity.copy(importBaseline = Sf2ZoneGenerators.encode(Sf2ZoneGenerators.programGlobalView(entity)))
    }
}
