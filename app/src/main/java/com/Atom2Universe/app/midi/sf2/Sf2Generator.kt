package com.Atom2Universe.app.midi.sf2

/**
 * SF2 Generator operators as defined in the SoundFont 2.01 specification.
 * Generators control how samples are played (pitch, volume, envelope, etc.)
 */
enum class Sf2Generator(val id: Int) {
    // Sample offsets
    START_ADDRS_OFFSET(0),          // Sample start address offset (low 15 bits)
    END_ADDRS_OFFSET(1),            // Sample end address offset (low 15 bits)
    STARTLOOP_ADDRS_OFFSET(2),      // Loop start address offset (low 15 bits)
    ENDLOOP_ADDRS_OFFSET(3),        // Loop end address offset (low 15 bits)
    START_ADDRS_COARSE_OFFSET(4),   // Sample start address coarse offset (32768x)

    MOD_LFO_TO_PITCH(5),            // Modulation LFO to pitch
    VIB_LFO_TO_PITCH(6),            // Vibrato LFO to pitch
    MOD_ENV_TO_PITCH(7),            // Modulation envelope to pitch
    INITIAL_FILTER_FC(8),           // Initial filter cutoff frequency
    INITIAL_FILTER_Q(9),            // Initial filter Q
    MOD_LFO_TO_FILTER_FC(10),       // Modulation LFO to filter cutoff
    MOD_ENV_TO_FILTER_FC(11),       // Modulation envelope to filter cutoff
    END_ADDRS_COARSE_OFFSET(12),    // Sample end address coarse offset (32768x)
    MOD_LFO_TO_VOLUME(13),          // Modulation LFO to volume
    UNUSED1(14),                    // Unused

    CHORUS_EFFECTS_SEND(15),        // Chorus effects send amount
    REVERB_EFFECTS_SEND(16),        // Reverb effects send amount
    PAN(17),                        // Pan position (-500 = left, +500 = right)
    UNUSED2(18),                    // Unused
    UNUSED3(19),                    // Unused
    UNUSED4(20),                    // Unused

    DELAY_MOD_LFO(21),              // Modulation LFO delay
    FREQ_MOD_LFO(22),               // Modulation LFO frequency
    DELAY_VIB_LFO(23),              // Vibrato LFO delay
    FREQ_VIB_LFO(24),               // Vibrato LFO frequency
    DELAY_MOD_ENV(25),              // Modulation envelope delay
    ATTACK_MOD_ENV(26),             // Modulation envelope attack
    HOLD_MOD_ENV(27),               // Modulation envelope hold
    DECAY_MOD_ENV(28),              // Modulation envelope decay
    SUSTAIN_MOD_ENV(29),            // Modulation envelope sustain
    RELEASE_MOD_ENV(30),            // Modulation envelope release
    KEYNUM_TO_MOD_ENV_HOLD(31),     // Key number to mod env hold
    KEYNUM_TO_MOD_ENV_DECAY(32),    // Key number to mod env decay

    DELAY_VOL_ENV(33),              // Volume envelope delay (timecents)
    ATTACK_VOL_ENV(34),             // Volume envelope attack (timecents)
    HOLD_VOL_ENV(35),               // Volume envelope hold (timecents)
    DECAY_VOL_ENV(36),              // Volume envelope decay (timecents)
    SUSTAIN_VOL_ENV(37),            // Volume envelope sustain (centibels attenuation)
    RELEASE_VOL_ENV(38),            // Volume envelope release (timecents)
    KEYNUM_TO_VOL_ENV_HOLD(39),     // Key number to vol env hold
    KEYNUM_TO_VOL_ENV_DECAY(40),    // Key number to vol env decay

    INSTRUMENT(41),                 // Instrument index (preset zones only)
    RESERVED1(42),                  // Reserved
    KEY_RANGE(43),                  // Key range (lo in bits 0-7, hi in bits 8-15)
    VEL_RANGE(44),                  // Velocity range (lo in bits 0-7, hi in bits 8-15)
    STARTLOOP_ADDRS_COARSE_OFFSET(45), // Loop start address coarse offset (32768x)
    KEYNUM(46),                     // Fixed key number
    VELOCITY(47),                   // Fixed velocity
    INITIAL_ATTENUATION(48),        // Initial attenuation (centibels)
    RESERVED2(49),                  // Reserved
    ENDLOOP_ADDRS_COARSE_OFFSET(50), // Loop end address coarse offset (32768x)

    COARSE_TUNE(51),                // Coarse tuning (semitones)
    FINE_TUNE(52),                  // Fine tuning (cents)
    SAMPLE_ID(53),                  // Sample ID (instrument zones only)
    SAMPLE_MODES(54),               // Sample loop modes (0=no loop, 1=loop, 3=loop+release)
    RESERVED3(55),                  // Reserved
    SCALE_TUNING(56),               // Scale tuning (cents per semitone, default 100)
    EXCLUSIVE_CLASS(57),            // Exclusive class (for drum instruments)
    OVERRIDING_ROOT_KEY(58),        // Overriding root key (MIDI note)
    UNUSED5(59),                    // Unused
    END_OPER(60);                   // End of generators marker

    companion object {
        private val idMap = entries.associateBy { it.id }

        fun fromId(id: Int): Sf2Generator? = idMap[id]
    }
}

/**
 * Sample loop modes for SAMPLE_MODES generator
 */
object SampleModes {
    const val NO_LOOP = 0
    const val LOOP_CONTINUOUS = 1
    const val UNUSED = 2
    const val LOOP_UNTIL_RELEASE = 3

    fun hasLoop(mode: Int): Boolean = (mode and 1) != 0
}
