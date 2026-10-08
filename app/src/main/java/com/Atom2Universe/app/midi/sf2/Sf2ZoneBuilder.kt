package com.Atom2Universe.app.midi.sf2

/**
 * Construit les zones finales d'un SF2 à partir des générateurs bruts (pgen/igen),
 * selon les règles de superposition de la norme SoundFont 2.01 (§9.4) :
 *
 * - Dans un instrument (et dans un preset), un générateur de la zone locale REMPLACE
 *   celui de la zone globale. Il ne s'y ajoute pas.
 * - Seule la première zone peut être globale (zone sans générateur terminal
 *   sampleID / instrument). Une zone suivante sans générateur terminal est ignorée.
 * - Les générateurs d'un preset s'AJOUTENT aux valeurs de l'instrument (ce sont des décalages),
 *   la valeur par défaut de la norme étant utilisée quand l'instrument ne fixe rien.
 * - Les générateurs liés à l'échantillon (offsets, sampleModes, exclusiveClass,
 *   overridingRootKey, keynum, velocity, sampleID) sont interdits au niveau preset : ignorés.
 * - Les plages de touches / vélocités du preset et de l'instrument s'intersectent.
 *
 * Partagé par [Sf2Parser] et [Sf2StreamingParser] pour qu'ils produisent les mêmes régions.
 */
internal object Sf2ZoneBuilder {

    private const val GEN_COUNT = 61

    // Valeurs par défaut de la norme pour les générateurs qui en ont une non nulle
    private const val DEFAULT_TIMECENTS = -12000
    private const val DEFAULT_FILTER_FC = 13500
    private const val DEFAULT_SCALE_TUNING = 100

    // Plage de l'atténuation selon la norme : 0 à 1440 cB (144 dB)
    private const val MAX_ATTENUATION_CB = 1440

    private val GEN_START_OFFSET = Sf2Generator.START_ADDRS_OFFSET.id
    private val GEN_END_OFFSET = Sf2Generator.END_ADDRS_OFFSET.id
    private val GEN_STARTLOOP_OFFSET = Sf2Generator.STARTLOOP_ADDRS_OFFSET.id
    private val GEN_ENDLOOP_OFFSET = Sf2Generator.ENDLOOP_ADDRS_OFFSET.id
    private val GEN_START_COARSE = Sf2Generator.START_ADDRS_COARSE_OFFSET.id
    private val GEN_END_COARSE = Sf2Generator.END_ADDRS_COARSE_OFFSET.id
    private val GEN_STARTLOOP_COARSE = Sf2Generator.STARTLOOP_ADDRS_COARSE_OFFSET.id
    private val GEN_ENDLOOP_COARSE = Sf2Generator.ENDLOOP_ADDRS_COARSE_OFFSET.id

    /** Générateurs sans effet au niveau preset (norme §8.5). */
    private val INSTRUMENT_ONLY = intArrayOf(
        GEN_START_OFFSET, GEN_END_OFFSET, GEN_STARTLOOP_OFFSET, GEN_ENDLOOP_OFFSET,
        GEN_START_COARSE, GEN_END_COARSE, GEN_STARTLOOP_COARSE, GEN_ENDLOOP_COARSE,
        Sf2Generator.KEYNUM.id, Sf2Generator.VELOCITY.id, Sf2Generator.SAMPLE_ID.id,
        Sf2Generator.SAMPLE_MODES.id, Sf2Generator.EXCLUSIVE_CLASS.id,
        Sf2Generator.OVERRIDING_ROOT_KEY.id
    )

    /**
     * Générateurs d'une zone : valeur brute + indicateur « fixé dans le fichier ».
     * Les plages de touches et de vélocités sont gardées à part (null = non fixée).
     */
    class Generators {
        val amount = IntArray(GEN_COUNT)
        val isSet = BooleanArray(GEN_COUNT)
        var keyRange: IntRange? = null
        var velRange: IntRange? = null

        fun has(op: Int): Boolean = isSet[op]

        fun valueOr(op: Int, default: Int): Int = if (isSet[op]) amount[op] else default

        operator fun set(op: Int, value: Int) {
            amount[op] = value
            isSet[op] = true
        }

        /** Index de l'échantillon (générateur terminal d'une zone d'instrument), null si absent. */
        val sampleId: Int?
            get() = if (has(Sf2Generator.SAMPLE_ID.id)) amount[Sf2Generator.SAMPLE_ID.id] and 0xFFFF else null

        /** Index de l'instrument (générateur terminal d'une zone de preset), null si absent. */
        val instrumentIndex: Int?
            get() = if (has(Sf2Generator.INSTRUMENT.id)) amount[Sf2Generator.INSTRUMENT.id] and 0xFFFF else null
    }

    /**
     * Lit les générateurs [start, end) d'une zone. Si un générateur apparaît deux fois,
     * la dernière valeur l'emporte.
     */
    fun collect(generators: List<Sf2GeneratorEntry>, start: Int, end: Int): Generators {
        val result = Generators()
        val limit = minOf(generators.size, end)
        for (index in maxOf(0, start) until limit) {
            val entry = generators[index]
            when (entry.operator) {
                Sf2Generator.KEY_RANGE.id -> result.keyRange = entry.decodeRange()
                Sf2Generator.VEL_RANGE.id -> result.velRange = entry.decodeRange()
                in 0 until GEN_COUNT -> result[entry.operator] = entry.amount
            }
        }
        return result
    }

    /** Zone locale posée sur la zone globale : chaque générateur local remplace le global. */
    fun overlay(global: Generators?, local: Generators): Generators {
        if (global == null) return local
        val result = Generators()
        for (op in 0 until GEN_COUNT) {
            when {
                local.isSet[op] -> result[op] = local.amount[op]
                global.isSet[op] -> result[op] = global.amount[op]
            }
        }
        result.keyRange = local.keyRange ?: global.keyRange
        result.velRange = local.velRange ?: global.velRange
        return result
    }

    /**
     * Zones locales d'un instrument ou d'un preset, chacune déjà superposée à la zone globale.
     *
     * @param bagStart premier bag de l'élément
     * @param bagEnd bag suivant le dernier bag de l'élément
     * @param bagGeneratorIndex index du premier générateur d'un bag (null si le bag n'existe pas)
     * @param terminalOperator SAMPLE_ID pour un instrument, INSTRUMENT pour un preset
     */
    fun buildZones(
        bagStart: Int,
        bagEnd: Int,
        bagGeneratorIndex: (Int) -> Int?,
        generators: List<Sf2GeneratorEntry>,
        terminalOperator: Int
    ): List<Generators> {
        var global: Generators? = null
        val zones = mutableListOf<Generators>()
        for (bagIndex in bagStart until bagEnd) {
            val genStart = bagGeneratorIndex(bagIndex) ?: continue
            val genEnd = bagGeneratorIndex(bagIndex + 1) ?: generators.size
            val zone = collect(generators, genStart, genEnd)
            if (zone.has(terminalOperator)) {
                zones.add(overlay(global, zone))
            } else if (bagIndex == bagStart) {
                // Seule la première zone peut être globale ; les autres zones incomplètes sont ignorées
                global = zone
            }
        }
        return zones
    }

    /**
     * Combine une zone d'instrument et la zone de preset qui la référence.
     * Retourne null si les plages de touches ou de vélocités ne se recouvrent pas.
     */
    fun combine(
        instrument: Generators,
        preset: Generators,
        sampleHeaders: List<Sf2SampleHeader>
    ): Sf2ZoneData? {
        val keyRange = intersect(instrument.keyRange, preset.keyRange) ?: return null
        val velRange = intersect(instrument.velRange, preset.velRange) ?: return null

        // Générateurs propres à l'instrument : valeur de l'instrument seule
        fun inst(op: Int, default: Int = 0) = instrument.valueOr(op, default)

        // Générateur additif : null si ni l'instrument ni le preset ne le fixent
        fun summed(op: Int, default: Int): Int? {
            if (!instrument.has(op) && !preset.has(op)) return null
            return instrument.valueOr(op, default) + preset.valueOr(op, 0)
        }

        fun sum(op: Int, default: Int = 0): Int = summed(op, default) ?: default

        val sampleId = instrument.sampleId
        val rootKey = if (instrument.has(Sf2Generator.OVERRIDING_ROOT_KEY.id)) {
            instrument.amount[Sf2Generator.OVERRIDING_ROOT_KEY.id].takeIf { it in 0..127 }
        } else null
        val forcedKey = if (instrument.has(Sf2Generator.KEYNUM.id)) {
            instrument.amount[Sf2Generator.KEYNUM.id].takeIf { it in 0..127 }
        } else null
        val forcedVelocity = if (instrument.has(Sf2Generator.VELOCITY.id)) {
            instrument.amount[Sf2Generator.VELOCITY.id].takeIf { it in 0..127 }
        } else null

        return Sf2ZoneData(
            keyRange = keyRange,
            velRange = velRange,
            startOffset = inst(GEN_START_OFFSET) + inst(GEN_START_COARSE) * 32768,
            endOffset = inst(GEN_END_OFFSET) + inst(GEN_END_COARSE) * 32768,
            startLoopOffset = inst(GEN_STARTLOOP_OFFSET) + inst(GEN_STARTLOOP_COARSE) * 32768,
            endLoopOffset = inst(GEN_ENDLOOP_OFFSET) + inst(GEN_ENDLOOP_COARSE) * 32768,
            coarseTune = sum(Sf2Generator.COARSE_TUNE.id),
            fineTune = sum(Sf2Generator.FINE_TUNE.id),
            scaleTuning = sum(Sf2Generator.SCALE_TUNING.id, DEFAULT_SCALE_TUNING),
            attenuation = sum(Sf2Generator.INITIAL_ATTENUATION.id).coerceIn(0, MAX_ATTENUATION_CB),
            pan = summed(Sf2Generator.PAN.id, 0)?.let { it / 500f },
            sampleModes = inst(Sf2Generator.SAMPLE_MODES.id),
            rootKey = rootKey,
            exclusiveClass = inst(Sf2Generator.EXCLUSIVE_CLASS.id),
            reverbSend = summed(Sf2Generator.REVERB_EFFECTS_SEND.id, 0)?.let { it / 1000f },
            chorusSend = summed(Sf2Generator.CHORUS_EFFECTS_SEND.id, 0)?.let { it / 1000f },
            forcedKeyNum = forcedKey,
            forcedVelocity = forcedVelocity,
            volumeEnvelope = Sf2EnvelopeData(
                delay = summed(Sf2Generator.DELAY_VOL_ENV.id, DEFAULT_TIMECENTS),
                attack = summed(Sf2Generator.ATTACK_VOL_ENV.id, DEFAULT_TIMECENTS),
                hold = summed(Sf2Generator.HOLD_VOL_ENV.id, DEFAULT_TIMECENTS),
                decay = summed(Sf2Generator.DECAY_VOL_ENV.id, DEFAULT_TIMECENTS),
                sustain = summed(Sf2Generator.SUSTAIN_VOL_ENV.id, 0),
                release = summed(Sf2Generator.RELEASE_VOL_ENV.id, DEFAULT_TIMECENTS),
                keynumToHold = summed(Sf2Generator.KEYNUM_TO_VOL_ENV_HOLD.id, 0),
                keynumToDecay = summed(Sf2Generator.KEYNUM_TO_VOL_ENV_DECAY.id, 0)
            ),
            sampleId = sampleId,
            sampleHeader = sampleId?.let { sampleHeaders.getOrNull(it) },
            filterFc = summed(Sf2Generator.INITIAL_FILTER_FC.id, DEFAULT_FILTER_FC),
            filterQ = summed(Sf2Generator.INITIAL_FILTER_Q.id, 0),
            vibLfoDelay = summed(Sf2Generator.DELAY_VIB_LFO.id, DEFAULT_TIMECENTS),
            vibLfoFreq = summed(Sf2Generator.FREQ_VIB_LFO.id, 0),
            vibLfoToPitch = summed(Sf2Generator.VIB_LFO_TO_PITCH.id, 0),
            modLfoDelay = summed(Sf2Generator.DELAY_MOD_LFO.id, DEFAULT_TIMECENTS),
            modLfoFreq = summed(Sf2Generator.FREQ_MOD_LFO.id, 0),
            modLfoToPitch = summed(Sf2Generator.MOD_LFO_TO_PITCH.id, 0),
            modLfoToFilterFc = summed(Sf2Generator.MOD_LFO_TO_FILTER_FC.id, 0),
            modLfoToVolume = summed(Sf2Generator.MOD_LFO_TO_VOLUME.id, 0),
            modEnvelope = Sf2EnvelopeData(
                delay = summed(Sf2Generator.DELAY_MOD_ENV.id, DEFAULT_TIMECENTS),
                attack = summed(Sf2Generator.ATTACK_MOD_ENV.id, DEFAULT_TIMECENTS),
                hold = summed(Sf2Generator.HOLD_MOD_ENV.id, DEFAULT_TIMECENTS),
                decay = summed(Sf2Generator.DECAY_MOD_ENV.id, DEFAULT_TIMECENTS),
                sustain = summed(Sf2Generator.SUSTAIN_MOD_ENV.id, 0),
                release = summed(Sf2Generator.RELEASE_MOD_ENV.id, DEFAULT_TIMECENTS),
                keynumToHold = summed(Sf2Generator.KEYNUM_TO_MOD_ENV_HOLD.id, 0),
                keynumToDecay = summed(Sf2Generator.KEYNUM_TO_MOD_ENV_DECAY.id, 0)
            ),
            modEnvToPitch = summed(Sf2Generator.MOD_ENV_TO_PITCH.id, 0),
            modEnvToFilterFc = summed(Sf2Generator.MOD_ENV_TO_FILTER_FC.id, 0)
        )
    }

    /**
     * Retire d'une zone de preset les générateurs réservés aux instruments.
     * Appelé une fois par zone de preset, avant [combine].
     */
    fun stripInstrumentOnly(preset: Generators): Generators {
        for (op in INSTRUMENT_ONLY) preset.isSet[op] = false
        return preset
    }

    private fun intersect(a: IntRange?, b: IntRange?): IntRange? {
        val lo = maxOf(a?.first ?: 0, b?.first ?: 0)
        val hi = minOf(a?.last ?: 127, b?.last ?: 127)
        return if (lo <= hi) lo..hi else null
    }
}
