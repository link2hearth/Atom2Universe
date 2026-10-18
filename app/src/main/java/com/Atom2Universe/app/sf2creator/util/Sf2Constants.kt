package com.Atom2Universe.app.sf2creator.util

/**
 * Centralized constants for SF2 SoundFont operations.
 * All SF2-related constants and default values should be defined here.
 */
object Sf2Constants {

    // ==================== Audio Format ====================

    /** Default sample rate in Hz */
    const val DEFAULT_SAMPLE_RATE = 44100

    /** Default number of audio channels (mono) */
    const val DEFAULT_CHANNELS = 1

    /** Default bits per sample */
    const val DEFAULT_BITS_PER_SAMPLE = 16

    // ==================== SF2 File Structure ====================

    /** SF2 requires 46 zero samples at the end of each sample */
    const val SAMPLE_PADDING = 46

    /** Maximum length for SF2 names (preset, instrument, sample) */
    const val MAX_NAME_LENGTH = 20

    /** Sample type for mono samples */
    const val SAMPLE_TYPE_MONO = 1

    /** Sample type for stereo right samples */
    const val SAMPLE_TYPE_RIGHT = 2

    /** Sample type for stereo left samples */
    const val SAMPLE_TYPE_LEFT = 4

    // ==================== MIDI Ranges ====================

    /** Minimum MIDI note number */
    const val MIDI_NOTE_MIN = 0

    /** Maximum MIDI note number */
    const val MIDI_NOTE_MAX = 127

    /** Default root note (C4) */
    const val DEFAULT_ROOT_NOTE = 60

    /** Minimum MIDI velocity */
    const val VELOCITY_MIN = 0

    /** Maximum MIDI velocity */
    const val VELOCITY_MAX = 127

    /** MIDI program/bank number minimum */
    const val PROGRAM_MIN = 0

    /** MIDI program/bank number maximum */
    const val PROGRAM_MAX = 127

    // ==================== Filter Constants ====================

    /** Minimum filter cutoff in absolute cents (approximately 20 Hz) */
    const val FILTER_CUTOFF_CENTS_MIN = 1500

    /** Maximum filter cutoff in absolute cents (approximately 20 kHz) */
    const val FILTER_CUTOFF_CENTS_MAX = 13500

    /** Minimum filter cutoff in Hz */
    const val FILTER_CUTOFF_HZ_MIN = 200f

    /** Maximum filter cutoff in Hz (fully open) */
    const val FILTER_CUTOFF_HZ_MAX = 20000f

    /** Default filter cutoff in Hz (fully open) */
    const val DEFAULT_FILTER_CUTOFF_HZ = 20000f

    /** Default filter cutoff in absolute cents (fully open) */
    const val DEFAULT_FILTER_CUTOFF_CENTS = 13500

    /** Minimum filter resonance in centibels */
    const val FILTER_RESONANCE_CB_MIN = 0

    /** Maximum filter resonance in centibels */
    const val FILTER_RESONANCE_CB_MAX = 960

    /** UI maximum for filter resonance (40 dB = 400 cB) */
    const val FILTER_RESONANCE_UI_MAX = 400

    /** Default filter resonance in centibels (no resonance) */
    const val DEFAULT_FILTER_RESONANCE_CB = 0

    /** Reference frequency for filter cents calculation (A-1) */
    const val FILTER_REFERENCE_FREQ = 8.176

    // ==================== Volume Envelope (DAHDSR) ====================

    /** Default delay time in milliseconds (instant) */
    const val DEFAULT_VOL_ENV_DELAY_MS = 0

    /** Default attack time in milliseconds */
    const val DEFAULT_ATTACK_MS = 12

    /** Default hold time in milliseconds */
    const val DEFAULT_VOL_ENV_HOLD_MS = 0

    /** Default decay time in milliseconds */
    const val DEFAULT_DECAY_MS = 125

    /** Default sustain level in percent (0-100) */
    const val DEFAULT_SUSTAIN_PERCENT = 100

    /** Default release time in milliseconds */
    const val DEFAULT_RELEASE_MS = 500

    /** Minimum envelope time in milliseconds */
    const val ENVELOPE_TIME_MIN_MS = 1

    /** Maximum envelope delay time in milliseconds for UI */
    const val ENVELOPE_DELAY_MAX_MS = 5000

    /** Maximum attack time in milliseconds for UI */
    const val ATTACK_MAX_MS = 5000

    /** Maximum hold time in milliseconds for UI */
    const val HOLD_MAX_MS = 5000

    /** Maximum decay time in milliseconds for UI */
    const val DECAY_MAX_MS = 5000

    /** Maximum release time in milliseconds for UI */
    const val RELEASE_MAX_MS = 5000

    // ==================== Modulation Envelope ====================

    /** Default mod envelope delay (instant) */
    const val DEFAULT_MOD_ENV_DELAY_MS = 0

    /** Default mod envelope attack */
    const val DEFAULT_MOD_ENV_ATTACK_MS = 1

    /** Default mod envelope hold */
    const val DEFAULT_MOD_ENV_HOLD_MS = 0

    /** Default mod envelope decay */
    const val DEFAULT_MOD_ENV_DECAY_MS = 1

    /** Default mod envelope sustain */
    const val DEFAULT_MOD_ENV_SUSTAIN_PERCENT = 100

    /** Default mod envelope release */
    const val DEFAULT_MOD_ENV_RELEASE_MS = 1

    /** Default mod envelope to pitch (no modulation) */
    const val DEFAULT_MOD_ENV_TO_PITCH = 0

    /** Default mod envelope to filter (no modulation) */
    const val DEFAULT_MOD_ENV_TO_FILTER_FC = 0

    /** Minimum modulation amount in cents */
    const val MODULATION_CENTS_MIN = -12000

    /** Maximum modulation amount in cents */
    const val MODULATION_CENTS_MAX = 12000

    // ==================== LFO Constants ====================

    /** Default LFO delay in milliseconds */
    const val DEFAULT_LFO_DELAY_MS = 0

    /** Default LFO frequency in cents (0 = ~8.176 Hz) */
    const val DEFAULT_LFO_FREQ_CENTS = 0

    /** Minimum LFO frequency in cents (~0.001 Hz) */
    const val LFO_FREQ_CENTS_MIN = -16000

    /** Maximum LFO frequency in cents (~100 Hz) */
    const val LFO_FREQ_CENTS_MAX = 4500

    /** Default LFO to pitch (no modulation) */
    const val DEFAULT_LFO_TO_PITCH = 0

    /** Default LFO to filter (no modulation) */
    const val DEFAULT_LFO_TO_FILTER_FC = 0

    /** Default LFO to volume (no tremolo) */
    const val DEFAULT_LFO_TO_VOLUME = 0

    /** Minimum LFO to volume in centibels */
    const val LFO_TO_VOLUME_CB_MIN = -960

    /** Maximum LFO to volume in centibels */
    const val LFO_TO_VOLUME_CB_MAX = 960

    /** LFO reference frequency (8.176 Hz at 0 cents) */
    const val LFO_REFERENCE_FREQ = 8.176

    // ==================== Volume/Attenuation ====================

    /** Minimum attenuation in centibels (full volume) */
    const val ATTENUATION_CB_MIN = 0

    /** Maximum attenuation in centibels (-48 dB, near silence) */
    const val ATTENUATION_CB_MAX = 480

    /** Default attenuation in centibels (full volume) */
    const val DEFAULT_ATTENUATION_CB = 0

    /** Maximum attenuation in dB */
    const val ATTENUATION_DB_MAX = 48f

    // ==================== Tuning ====================

    /** Minimum fine tune in cents */
    const val FINE_TUNE_CENTS_MIN = -99

    /** Maximum fine tune in cents */
    const val FINE_TUNE_CENTS_MAX = 99

    /** Default fine tune in cents */
    const val DEFAULT_FINE_TUNE_CENTS = 0

    /** Minimum coarse tune in semitones */
    const val COARSE_TUNE_MIN = -120

    /** Maximum coarse tune in semitones */
    const val COARSE_TUNE_MAX = 120

    /** Default coarse tune in semitones */
    const val DEFAULT_COARSE_TUNE = 0

    /** Minimum scale tuning (0 = chromatic compression) */
    const val SCALE_TUNING_MIN = 0

    /** Maximum scale tuning (1200 = stretched octaves) */
    const val SCALE_TUNING_MAX = 1200

    /** Default scale tuning (100 = normal 12-TET) */
    const val DEFAULT_SCALE_TUNING = 100

    /** Cents per semitone */
    const val CENTS_PER_SEMITONE = 100

    // ==================== Effects Send ====================

    /** Minimum effects send value (0%) */
    const val EFFECTS_SEND_MIN = 0

    /** Maximum effects send value (100% = 1000 units of 0.1%) */
    const val EFFECTS_SEND_MAX = 1000

    /** Default chorus send */
    const val DEFAULT_CHORUS_SEND = 0

    /** Default reverb send */
    const val DEFAULT_REVERB_SEND = 0

    // ==================== Pan ====================

    /** Pan value for full left */
    const val PAN_LEFT = -500

    /** Pan value for center */
    const val PAN_CENTER = 0

    /** Pan value for full right */
    const val PAN_RIGHT = 500

    /** Default pan (center) */
    const val DEFAULT_PAN = 0

    // ==================== Exclusive Class ====================

    /** No exclusive class (default) */
    const val EXCLUSIVE_CLASS_NONE = 0

    /** Minimum exclusive class */
    const val EXCLUSIVE_CLASS_MIN = 0

    /** Maximum exclusive class */
    const val EXCLUSIVE_CLASS_MAX = 127

    // ==================== Loop Processing ====================

    /** Search window for zero-crossing snap (~11ms at 44100Hz) */
    const val ZERO_CROSSING_SEARCH_WINDOW = 500

    /** Crossfade duration at loop boundary in milliseconds */
    const val LOOP_CROSSFADE_MS = 20

    // ==================== SF2 Generator Numbers ====================
    // Reference: SoundFont 2.01 Technical Specification, Section 8.1.2

    // --- Sample Offsets (0-4, 12, 45-46, 50) ---
    const val GEN_START_ADDRS_OFFSET = 0
    const val GEN_END_ADDRS_OFFSET = 1
    const val GEN_STARTLOOP_ADDRS_OFFSET = 2
    const val GEN_ENDLOOP_ADDRS_OFFSET = 3
    const val GEN_START_ADDRS_COARSE_OFFSET = 4
    const val GEN_END_ADDRS_COARSE_OFFSET = 12
    const val GEN_STARTLOOP_ADDRS_COARSE_OFFSET = 45
    const val GEN_ENDLOOP_ADDRS_COARSE_OFFSET = 50

    // --- Modulation LFO (5, 10, 13, 21-22) ---
    const val GEN_MOD_LFO_TO_PITCH = 5
    const val GEN_MOD_LFO_TO_FILTER_FC = 10
    const val GEN_MOD_LFO_TO_VOLUME = 13
    const val GEN_DELAY_MOD_LFO = 21
    const val GEN_FREQ_MOD_LFO = 22

    // --- Vibrato LFO (6, 23-24) ---
    const val GEN_VIB_LFO_TO_PITCH = 6
    const val GEN_DELAY_VIB_LFO = 23
    const val GEN_FREQ_VIB_LFO = 24

    // --- Modulation Envelope (7, 11, 25-32) ---
    const val GEN_MOD_ENV_TO_PITCH = 7
    const val GEN_MOD_ENV_TO_FILTER_FC = 11
    const val GEN_DELAY_MOD_ENV = 25
    const val GEN_ATTACK_MOD_ENV = 26
    const val GEN_HOLD_MOD_ENV = 27
    const val GEN_DECAY_MOD_ENV = 28
    const val GEN_SUSTAIN_MOD_ENV = 29
    const val GEN_RELEASE_MOD_ENV = 30
    const val GEN_KEYNUM_TO_MOD_ENV_HOLD = 31
    const val GEN_KEYNUM_TO_MOD_ENV_DECAY = 32

    // --- Filter (8-9) ---
    const val GEN_INITIAL_FILTER_FC = 8
    const val GEN_INITIAL_FILTER_Q = 9

    // --- Effects (15-16) ---
    const val GEN_CHORUS_EFFECTS_SEND = 15
    const val GEN_REVERB_EFFECTS_SEND = 16

    // --- Pan (17) ---
    const val GEN_PAN = 17

    // --- Volume Envelope (33-40) ---
    const val GEN_DELAY_VOL_ENV = 33
    const val GEN_ATTACK_VOL_ENV = 34
    const val GEN_HOLD_VOL_ENV = 35
    const val GEN_DECAY_VOL_ENV = 36
    const val GEN_SUSTAIN_VOL_ENV = 37
    const val GEN_RELEASE_VOL_ENV = 38
    const val GEN_KEYNUM_TO_VOL_ENV_HOLD = 39
    const val GEN_KEYNUM_TO_VOL_ENV_DECAY = 40

    // --- Instrument/Preset Reference (41) ---
    const val GEN_INSTRUMENT = 41

    // --- Key/Velocity Range (43-44) ---
    const val GEN_KEY_RANGE = 43
    const val GEN_VEL_RANGE = 44

    // --- Fixed Key/Velocity (46-47) ---
    const val GEN_FIXED_KEY = 46
    const val GEN_FIXED_VELOCITY = 47

    // --- Attenuation (48) ---
    const val GEN_INITIAL_ATTENUATION = 48

    // --- Tuning (51-52, 56) ---
    const val GEN_COARSE_TUNE = 51
    const val GEN_FINE_TUNE = 52
    const val GEN_SCALE_TUNING = 56

    // --- Sample Reference (53-54, 58) ---
    const val GEN_SAMPLE_ID = 53
    const val GEN_SAMPLE_MODES = 54
    const val GEN_OVERRIDING_ROOT_KEY = 58

    // --- Exclusive Class (57) ---
    const val GEN_EXCLUSIVE_CLASS = 57

    // ==================== Sample Loop Modes ====================

    /** No loop */
    const val LOOP_MODE_NONE = 0

    /** Loop continuously */
    const val LOOP_MODE_CONTINUOUS = 1

    /** Loop during key hold, play remainder on release */
    const val LOOP_MODE_SUSTAIN = 3

    // ==================== Timecents ====================

    /** Minimum timecents value (technical minimum) */
    const val TIMECENTS_MIN = -32768

    /** Maximum timecents value */
    const val TIMECENTS_MAX = 32767

    /**
     * Default SF2 timecents value for "instant" (no delay/attack/release).
     * This is the standard value used in SF2 files to indicate zero time.
     * -12000 timecents = 2^(-10) seconds ≈ 1ms, treated as instantaneous.
     */
    const val TIMECENTS_DEFAULT_INSTANT = -12000

    // ==================== SF2 Native Unit Defaults ====================
    // These are the actual default values used in SF2 files

    /** Default envelope times in timecents (-12000 = instant) */
    const val DEFAULT_VOL_ENV_DELAY_TC = -12000
    const val DEFAULT_VOL_ENV_ATTACK_TC = -12000
    const val DEFAULT_VOL_ENV_HOLD_TC = -12000
    const val DEFAULT_VOL_ENV_DECAY_TC = -12000
    const val DEFAULT_VOL_ENV_RELEASE_TC = -12000

    /** Default sustain in centibels (0 = full volume, no attenuation) */
    const val DEFAULT_VOL_ENV_SUSTAIN_CB = 0

    /** Default mod envelope times in timecents */
    const val DEFAULT_MOD_ENV_DELAY_TC = -12000
    const val DEFAULT_MOD_ENV_ATTACK_TC = -12000
    const val DEFAULT_MOD_ENV_HOLD_TC = -12000
    const val DEFAULT_MOD_ENV_DECAY_TC = -12000
    const val DEFAULT_MOD_ENV_RELEASE_TC = -12000

    /** Default mod envelope sustain in centibels */
    const val DEFAULT_MOD_ENV_SUSTAIN_CB = 0

    /** Default LFO delays in timecents */
    const val DEFAULT_VIB_LFO_DELAY_TC = -12000
    const val DEFAULT_MOD_LFO_DELAY_TC = -12000

    /** Default LFO frequency in cents (0 = ~8.176 Hz) */
    const val DEFAULT_VIB_LFO_FREQ_CENTS = 0
    const val DEFAULT_MOD_LFO_FREQ_CENTS = 0

    // ==================== Note Names ====================

    /** Array of note names for MIDI note conversion */
    val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
}
