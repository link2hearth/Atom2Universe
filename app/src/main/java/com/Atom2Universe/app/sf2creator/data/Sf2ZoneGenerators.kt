package com.Atom2Universe.app.sf2creator.data

import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity

/**
 * Correspondence between the editable parameters of the entities and the generators of
 * an SF2 zone.
 *
 * An imported zone keeps the generators that were written in the file
 * ([Sf2SampleEntity.importedGenerators]...) and the editable parameters as they were right
 * after import ([Sf2SampleEntity.importBaseline]...). At export, the zone is written from its
 * imported generators, and only the parameters that differ from the baseline (edited in the
 * app) replace them. Values the app cannot represent (a global attack of exactly 0 timecents,
 * a zone that repeats a default to override its global zone, sample offsets...) are therefore
 * written back unchanged.
 *
 * Zones created in the app (no imported generators) are written from their parameters,
 * leaving out SF2 defaults.
 */
object Sf2ZoneGenerators {

    const val GEN_START_OFFSET = 0
    const val GEN_END_OFFSET = 1
    const val GEN_STARTLOOP_OFFSET = 2
    const val GEN_ENDLOOP_OFFSET = 3
    const val GEN_START_COARSE_OFFSET = 4
    const val GEN_END_COARSE_OFFSET = 12
    const val GEN_INSTRUMENT = 41
    const val GEN_KEY_RANGE = 43
    const val GEN_VEL_RANGE = 44
    const val GEN_STARTLOOP_COARSE_OFFSET = 45
    const val GEN_KEYNUM = 46
    const val GEN_VELOCITY = 47
    const val GEN_ENDLOOP_COARSE_OFFSET = 50
    const val GEN_SAMPLE_ID = 53
    const val GEN_SAMPLE_MODES = 54
    const val GEN_OVERRIDING_ROOT_KEY = 58

    /** Sample header values kept in the baseline of an instrument zone (not generators). */
    const val BASE_LOOP_START = -1
    const val BASE_LOOP_END = -2
    const val BASE_ORIGINAL_PITCH = -3

    /** Generators that move the sample start, end and loop points of a zone. */
    val OFFSET_GENERATORS = setOf(
        GEN_START_OFFSET, GEN_END_OFFSET, GEN_STARTLOOP_OFFSET, GEN_ENDLOOP_OFFSET,
        GEN_START_COARSE_OFFSET, GEN_END_COARSE_OFFSET, GEN_STARTLOOP_COARSE_OFFSET, GEN_ENDLOOP_COARSE_OFFSET
    )
    val LOOP_OFFSET_GENERATORS = setOf(
        GEN_STARTLOOP_OFFSET, GEN_ENDLOOP_OFFSET, GEN_STARTLOOP_COARSE_OFFSET, GEN_ENDLOOP_COARSE_OFFSET
    )

    const val FULL_RANGE = 127 shl 8

    /** Timecents of an "instant" envelope or LFO time, the SF2 default. */
    private const val INSTANT = -12000

    /** SF2 defaults of the instrument zone generators the app edits (others default to 0). */
    private val ZONE_DEFAULTS = mapOf(
        8 to 13500, 56 to 100,
        21 to INSTANT, 23 to INSTANT,
        25 to INSTANT, 26 to INSTANT, 27 to INSTANT, 28 to INSTANT, 30 to INSTANT,
        33 to INSTANT, 34 to INSTANT, 35 to INSTANT, 36 to INSTANT, 38 to INSTANT,
        GEN_KEY_RANGE to FULL_RANGE, GEN_VEL_RANGE to FULL_RANGE,
        GEN_KEYNUM to -1, GEN_VELOCITY to -1
    )

    fun range(low: Int, high: Int): Int = (high.coerceIn(0, 127) shl 8) or low.coerceIn(0, 127)
    fun rangeLow(value: Int): Int = value and 0xFF
    fun rangeHigh(value: Int): Int = (value shr 8) and 0xFF

    // ==================== Encoding ====================

    fun encode(generators: Map<Int, Int>): String =
        generators.entries.sortedBy { it.key }.joinToString(",") { "${it.key}:${it.value}" }

    fun decode(text: String?): Map<Int, Int>? {
        if (text == null) return null
        if (text.isEmpty()) return emptyMap()
        return try {
            text.split(',').associate { entry ->
                val separator = entry.lastIndexOf(':')
                entry.substring(0, separator).toInt() to entry.substring(separator + 1).toInt()
            }
        } catch (e: RuntimeException) {
            null
        }
    }

    // ==================== Editable parameters as generators ====================

    /** Loop mode actually used: the loop switch of the app wins over the stored mode. */
    fun effectiveSampleModes(sample: Sf2SampleEntity): Int {
        val loops = (sample.sampleModes and 1) != 0
        return when {
            sample.hasLoop && loops -> sample.sampleModes
            sample.hasLoop -> 1
            loops -> 0
            else -> sample.sampleModes
        }
    }

    /** Instrument zone (sample entity): every editable parameter, plus its loop points. */
    fun sampleView(s: Sf2SampleEntity): Map<Int, Int> = mapOf(
        GEN_KEY_RANGE to range(s.keyRangeStart, s.keyRangeEnd),
        GEN_VEL_RANGE to range(s.velRangeStart, s.velRangeEnd),
        5 to s.modLfoToPitch, 6 to s.vibLfoToPitch, 7 to s.modEnvToPitch,
        8 to s.filterFc, 9 to s.filterQ, 10 to s.modLfoToFilterFc, 11 to s.modEnvToFilterFc,
        13 to s.modLfoToVolume, 15 to s.chorusSend, 16 to s.reverbSend, 17 to s.pan,
        21 to s.modLfoDelay, 22 to s.modLfoFreq, 23 to s.vibLfoDelay, 24 to s.vibLfoFreq,
        25 to s.modEnvDelay, 26 to s.modEnvAttack, 27 to s.modEnvHold, 28 to s.modEnvDecay,
        29 to s.modEnvSustain, 30 to s.modEnvRelease, 31 to s.keyToModEnvHold, 32 to s.keyToModEnvDecay,
        33 to s.volEnvDelay, 34 to s.volEnvAttack, 35 to s.volEnvHold, 36 to s.volEnvDecay,
        37 to s.volEnvSustain, 38 to s.volEnvRelease, 39 to s.keyToVolEnvHold, 40 to s.keyToVolEnvDecay,
        GEN_KEYNUM to s.fixedKey, GEN_VELOCITY to s.fixedVelocity,
        48 to s.attenuation, 51 to s.coarseTune, 52 to s.fineTuneCents,
        GEN_SAMPLE_MODES to effectiveSampleModes(s), 56 to s.scaleTuning, 57 to s.exclusiveClass,
        GEN_OVERRIDING_ROOT_KEY to s.rootNote,
        BASE_LOOP_START to s.loopStart, BASE_LOOP_END to s.loopEnd
    )

    private fun additiveView(
        attenuation: Int, coarseTune: Int, fineTune: Int,
        volEnvDelay: Int, volEnvAttack: Int, volEnvHold: Int, volEnvDecay: Int, volEnvSustain: Int, volEnvRelease: Int,
        modEnvDelay: Int, modEnvAttack: Int, modEnvHold: Int, modEnvDecay: Int, modEnvSustain: Int, modEnvRelease: Int,
        modEnvToPitch: Int, modEnvToFilterFc: Int,
        vibLfoDelay: Int, vibLfoFreq: Int, vibLfoToPitch: Int,
        modLfoDelay: Int, modLfoFreq: Int, modLfoToPitch: Int, modLfoToFilterFc: Int, modLfoToVolume: Int,
        filterFc: Int, filterQ: Int, chorusSend: Int, reverbSend: Int, pan: Int,
        keyToModEnvHold: Int, keyToModEnvDecay: Int, keyToVolEnvHold: Int, keyToVolEnvDecay: Int,
        scaleTuning: Int, exclusiveClass: Int
    ): Map<Int, Int> = mapOf(
        5 to modLfoToPitch, 6 to vibLfoToPitch, 7 to modEnvToPitch,
        8 to filterFc, 9 to filterQ, 10 to modLfoToFilterFc, 11 to modEnvToFilterFc,
        13 to modLfoToVolume, 15 to chorusSend, 16 to reverbSend, 17 to pan,
        21 to modLfoDelay, 22 to modLfoFreq, 23 to vibLfoDelay, 24 to vibLfoFreq,
        25 to modEnvDelay, 26 to modEnvAttack, 27 to modEnvHold, 28 to modEnvDecay,
        29 to modEnvSustain, 30 to modEnvRelease, 31 to keyToModEnvHold, 32 to keyToModEnvDecay,
        33 to volEnvDelay, 34 to volEnvAttack, 35 to volEnvHold, 36 to volEnvDecay,
        37 to volEnvSustain, 38 to volEnvRelease, 39 to keyToVolEnvHold, 40 to keyToVolEnvDecay,
        48 to attenuation, 51 to coarseTune, 52 to fineTune, 56 to scaleTuning, 57 to exclusiveClass
    )

    /** Instrument global zone. 0 means "not set" for every parameter. */
    fun instrumentGlobalView(i: Sf2InstrumentEntity): Map<Int, Int> = additiveView(
        i.globalAttenuation, i.globalCoarseTune, i.globalFineTune,
        i.globalVolEnvDelay, i.globalVolEnvAttack, i.globalVolEnvHold, i.globalVolEnvDecay, i.globalVolEnvSustain, i.globalVolEnvRelease,
        i.globalModEnvDelay, i.globalModEnvAttack, i.globalModEnvHold, i.globalModEnvDecay, i.globalModEnvSustain, i.globalModEnvRelease,
        i.globalModEnvToPitch, i.globalModEnvToFilterFc,
        i.globalVibLfoDelay, i.globalVibLfoFreq, i.globalVibLfoToPitch,
        i.globalModLfoDelay, i.globalModLfoFreq, i.globalModLfoToPitch, i.globalModLfoToFilterFc, i.globalModLfoToVolume,
        i.globalFilterFc, i.globalFilterQ, i.globalChorusSend, i.globalReverbSend, i.globalPan,
        i.globalKeyToModEnvHold, i.globalKeyToModEnvDecay, i.globalKeyToVolEnvHold, i.globalKeyToVolEnvDecay,
        i.globalScaleTuning, i.globalExclusiveClass
    )

    /** Preset global zone (program). 0 means "not set" for every parameter. */
    fun programGlobalView(p: Sf2ProgramEntity): Map<Int, Int> = additiveView(
        p.globalAttenuation, p.globalCoarseTune, p.globalFineTune,
        p.globalVolEnvDelay, p.globalVolEnvAttack, p.globalVolEnvHold, p.globalVolEnvDecay, p.globalVolEnvSustain, p.globalVolEnvRelease,
        p.globalModEnvDelay, p.globalModEnvAttack, p.globalModEnvHold, p.globalModEnvDecay, p.globalModEnvSustain, p.globalModEnvRelease,
        p.globalModEnvToPitch, p.globalModEnvToFilterFc,
        p.globalVibLfoDelay, p.globalVibLfoFreq, p.globalVibLfoToPitch,
        p.globalModLfoDelay, p.globalModLfoFreq, p.globalModLfoToPitch, p.globalModLfoToFilterFc, p.globalModLfoToVolume,
        p.globalFilterFc, p.globalFilterQ, p.globalChorusSend, p.globalReverbSend, p.globalPan,
        p.globalKeyToModEnvHold, p.globalKeyToModEnvDecay, p.globalKeyToVolEnvHold, p.globalKeyToVolEnvDecay,
        p.globalScaleTuning, p.globalExclusiveClass
    )

    /** Preset zone: its ranges plus additive parameters (0 = not set). */
    fun presetZoneView(z: Sf2PresetEntity): Map<Int, Int> = additiveView(
        z.pgenAttenuation, z.pgenCoarseTune, z.pgenFineTune,
        z.pgenVolEnvDelay, z.pgenVolEnvAttack, z.pgenVolEnvHold, z.pgenVolEnvDecay, z.pgenVolEnvSustain, z.pgenVolEnvRelease,
        z.pgenModEnvDelay, z.pgenModEnvAttack, z.pgenModEnvHold, z.pgenModEnvDecay, z.pgenModEnvSustain, z.pgenModEnvRelease,
        z.pgenModEnvToPitch, z.pgenModEnvToFilterFc,
        z.pgenVibLfoDelay, z.pgenVibLfoFreq, z.pgenVibLfoToPitch,
        z.pgenModLfoDelay, z.pgenModLfoFreq, z.pgenModLfoToPitch, z.pgenModLfoToFilterFc, z.pgenModLfoToVolume,
        z.pgenFilterFc, z.pgenFilterQ, z.pgenChorusSend, z.pgenReverbSend, z.pgenPan,
        z.pgenKeyToModEnvHold, z.pgenKeyToModEnvDecay, z.pgenKeyToVolEnvHold, z.pgenKeyToVolEnvDecay,
        z.pgenScaleTuning, z.pgenExclusiveClass
    ) + mapOf(
        GEN_KEY_RANGE to range(z.pgenKeyRangeLow, z.pgenKeyRangeHigh),
        GEN_VEL_RANGE to range(z.pgenVelRangeLow, z.pgenVelRangeHigh)
    )

    // ==================== Generators to write ====================

    /**
     * Generators of an instrument zone, without its sample ID. Loop offsets are adjusted
     * by the exporter, which knows the sample header that will be written.
     */
    fun sampleZoneGenerators(s: Sf2SampleEntity): MutableMap<Int, Int> = merge(
        view = sampleView(s),
        imported = decode(s.importedGenerators),
        baseline = decode(s.importBaseline),
        isUnset = { gen, value -> (gen == GEN_KEYNUM || gen == GEN_VELOCITY) && value < 0 },
        writeWhenNew = { gen, value -> gen == GEN_OVERRIDING_ROOT_KEY || value != (ZONE_DEFAULTS[gen] ?: 0) }
    )

    fun instrumentGlobalGenerators(i: Sf2InstrumentEntity): MutableMap<Int, Int> = merge(
        view = instrumentGlobalView(i),
        imported = decode(i.importedGenerators),
        baseline = decode(i.importBaseline),
        isUnset = { _, value -> value == 0 },
        writeWhenNew = { _, value -> value != 0 }
    )

    fun programGlobalGenerators(p: Sf2ProgramEntity): MutableMap<Int, Int> = merge(
        view = programGlobalView(p),
        imported = decode(p.importedGenerators),
        baseline = decode(p.importBaseline),
        isUnset = { _, value -> value == 0 },
        writeWhenNew = { _, value -> value != 0 }
    )

    /** Generators of a preset zone, without its instrument reference. */
    fun presetZoneGenerators(z: Sf2PresetEntity): MutableMap<Int, Int> {
        val unset = { gen: Int, value: Int ->
            if (gen == GEN_KEY_RANGE || gen == GEN_VEL_RANGE) value == FULL_RANGE else value == 0
        }
        return merge(
            view = presetZoneView(z),
            imported = decode(z.importedGenerators),
            baseline = decode(z.importBaseline),
            isUnset = unset,
            writeWhenNew = { gen, value -> !unset(gen, value) }
        )
    }

    private fun merge(
        view: Map<Int, Int>,
        imported: Map<Int, Int>?,
        baseline: Map<Int, Int>?,
        isUnset: (Int, Int) -> Boolean,
        writeWhenNew: (Int, Int) -> Boolean
    ): MutableMap<Int, Int> {
        if (imported == null || baseline == null) {
            return view.filter { (gen, value) -> gen >= 0 && writeWhenNew(gen, value) }.toMutableMap()
        }
        val generators = imported.filterKeys { it != GEN_SAMPLE_ID && it != GEN_INSTRUMENT }.toMutableMap()
        for ((gen, value) in view) {
            if (gen < 0 || baseline[gen] == value) continue
            if (isUnset(gen, value)) generators.remove(gen) else generators[gen] = value
        }
        return generators
    }

    /**
     * Generators in the order of the standard: key range first, velocity range second,
     * the instrument or sample reference ([terminal]) last.
     */
    fun ordered(generators: Map<Int, Int>, terminal: Pair<Int, Int>? = null): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>(generators.size + 1)
        generators[GEN_KEY_RANGE]?.let { result.add(GEN_KEY_RANGE to it) }
        generators[GEN_VEL_RANGE]?.let { result.add(GEN_VEL_RANGE to it) }
        generators.entries
            .filter { it.key != GEN_KEY_RANGE && it.key != GEN_VEL_RANGE && it.key != GEN_INSTRUMENT && it.key != GEN_SAMPLE_ID }
            .sortedBy { it.key }
            .forEach { result.add(it.key to it.value) }
        terminal?.let { result.add(it) }
        return result
    }

    /**
     * Writes a loop point offset as fine + coarse (32768 sample) generators, replacing any
     * previous ones. Nothing is written for a zero offset.
     */
    fun setOffset(generators: MutableMap<Int, Int>, fineGen: Int, coarseGen: Int, offset: Long) {
        generators.remove(fineGen)
        generators.remove(coarseGen)
        if (offset == 0L) return
        val coarse = (offset / 32768).toInt()
        val fine = (offset - coarse * 32768L).toInt()
        if (fine != 0) generators[fineGen] = fine
        if (coarse != 0) generators[coarseGen] = coarse
    }
}
