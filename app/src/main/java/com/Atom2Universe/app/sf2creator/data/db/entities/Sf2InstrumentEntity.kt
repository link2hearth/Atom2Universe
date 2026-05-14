package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an SF2 Instrument.
 *
 * SF2 Hierarchy (correct structure):
 * Project → Program (SF2 Preset) → PresetZone (PGEN) → Instrument (this) → Sample (IGEN)
 *
 * An Instrument can be SHARED between multiple preset zones. This is the key difference
 * from the previous model where instruments were embedded in preset entities.
 *
 * Example:
 * - Preset "Piano" with 2 zones (keyRange 0-60 and 61-127) can both reference
 *   the SAME instrument, which holds the global IGEN parameters and samples.
 *
 * GLOBAL PARAMETERS (IGEN global zone):
 * These parameters are stored in the instrument's global zone (first zone without sampleID).
 * They are ADDITIVE with sample-specific parameters.
 * Final value = pgen + instrument_global + sample_specific
 *
 * All global parameters default to 0 (neutral/no effect).
 */
@Entity(
    tableName = "sf2_instruments",
    foreignKeys = [
        ForeignKey(
            entity = Sf2ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId")]
)
data class Sf2InstrumentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val name: String,

    // ==================== INSTRUMENT GLOBAL PARAMETERS (IGEN global zone) ====================
    // All values are ADDITIVE with sample-specific values
    // Default = 0 means no modification

    // ==================== Volume/Attenuation ====================
    val globalAttenuation: Int = 0,         // GEN 48 - Added to sample attenuation

    // ==================== Tuning ====================
    val globalCoarseTune: Int = 0,          // GEN 51 - Added to sample tuning
    val globalFineTune: Int = 0,            // GEN 52 - Added to sample fine tune

    // ==================== Volume Envelope ====================
    val globalVolEnvDelay: Int = 0,         // GEN 33
    val globalVolEnvAttack: Int = 0,        // GEN 34
    val globalVolEnvHold: Int = 0,          // GEN 35
    val globalVolEnvDecay: Int = 0,         // GEN 36
    val globalVolEnvSustain: Int = 0,       // GEN 37 - In centibels (added)
    val globalVolEnvRelease: Int = 0,       // GEN 38

    // ==================== Modulation Envelope ====================
    val globalModEnvDelay: Int = 0,         // GEN 25
    val globalModEnvAttack: Int = 0,        // GEN 26
    val globalModEnvHold: Int = 0,          // GEN 27
    val globalModEnvDecay: Int = 0,         // GEN 28
    val globalModEnvSustain: Int = 0,       // GEN 29
    val globalModEnvRelease: Int = 0,       // GEN 30
    val globalModEnvToPitch: Int = 0,       // GEN 7
    val globalModEnvToFilterFc: Int = 0,    // GEN 11

    // ==================== Vibrato LFO ====================
    val globalVibLfoDelay: Int = 0,         // GEN 23
    val globalVibLfoFreq: Int = 0,          // GEN 24
    val globalVibLfoToPitch: Int = 0,       // GEN 6

    // ==================== Modulation LFO ====================
    val globalModLfoDelay: Int = 0,         // GEN 21
    val globalModLfoFreq: Int = 0,          // GEN 22
    val globalModLfoToPitch: Int = 0,       // GEN 5
    val globalModLfoToFilterFc: Int = 0,    // GEN 10
    val globalModLfoToVolume: Int = 0,      // GEN 13

    // ==================== Filter ====================
    val globalFilterFc: Int = 0,            // GEN 8  - Added to sample filter (in cents)
    val globalFilterQ: Int = 0,             // GEN 9  - Added to sample resonance

    // ==================== Effects ====================
    val globalChorusSend: Int = 0,          // GEN 15
    val globalReverbSend: Int = 0,          // GEN 16

    // ==================== Pan ====================
    val globalPan: Int = 0,                 // GEN 17

    // ==================== Key-based Envelope Scaling ====================
    val globalKeyToModEnvHold: Int = 0,     // GEN 31
    val globalKeyToModEnvDecay: Int = 0,    // GEN 32
    val globalKeyToVolEnvHold: Int = 0,     // GEN 39
    val globalKeyToVolEnvDecay: Int = 0,    // GEN 40

    // ==================== Additional Global Generators ====================
    val globalScaleTuning: Int = 0,         // GEN 56 (0 = use sample default)
    val globalExclusiveClass: Int = 0,      // GEN 57 (0 = none)

    // ==================== Hybrid Passthrough Tracking ====================
    /**
     * SHA-256 hash of the instrument's state at import time.
     * Null for instruments created from scratch.
     */
    val originalStateHash: String? = null,

    /**
     * Whether this instrument has been modified by the user since import.
     */
    val isModifiedByUser: Boolean = false,

    /**
     * Bitmap of modification types (see ModificationFlags).
     */
    val modificationFlags: Int = 0
) {
    /**
     * Check if this instrument has any non-default global parameters.
     */
    fun hasAnyNonDefaultGlobal(): Boolean {
        return globalAttenuation != 0 || globalCoarseTune != 0 || globalFineTune != 0 ||
               globalVolEnvDelay != 0 || globalVolEnvAttack != 0 || globalVolEnvHold != 0 ||
               globalVolEnvDecay != 0 || globalVolEnvSustain != 0 || globalVolEnvRelease != 0 ||
               globalModEnvDelay != 0 || globalModEnvAttack != 0 || globalModEnvHold != 0 ||
               globalModEnvDecay != 0 || globalModEnvSustain != 0 || globalModEnvRelease != 0 ||
               globalModEnvToPitch != 0 || globalModEnvToFilterFc != 0 ||
               globalVibLfoDelay != 0 || globalVibLfoFreq != 0 || globalVibLfoToPitch != 0 ||
               globalModLfoDelay != 0 || globalModLfoFreq != 0 || globalModLfoToPitch != 0 ||
               globalModLfoToFilterFc != 0 || globalModLfoToVolume != 0 ||
               globalFilterFc != 0 || globalFilterQ != 0 ||
               globalChorusSend != 0 || globalReverbSend != 0 || globalPan != 0 ||
               globalKeyToModEnvHold != 0 || globalKeyToModEnvDecay != 0 ||
               globalKeyToVolEnvHold != 0 || globalKeyToVolEnvDecay != 0 ||
               globalScaleTuning != 0 || globalExclusiveClass != 0
    }

    /**
     * Build a map of generator number to value for all non-default global parameters.
     * Used during SF2 export to write the global zone.
     */
    fun buildGlobalGeneratorsMap(): Map<Int, Int> {
        val gens = mutableMapOf<Int, Int>()

        if (globalModLfoToPitch != 0) gens[5] = globalModLfoToPitch    // GEN_MOD_LFO_TO_PITCH
        if (globalVibLfoToPitch != 0) gens[6] = globalVibLfoToPitch    // GEN_VIB_LFO_TO_PITCH
        if (globalModEnvToPitch != 0) gens[7] = globalModEnvToPitch    // GEN_MOD_ENV_TO_PITCH
        if (globalFilterFc != 0) gens[8] = globalFilterFc              // GEN_INITIAL_FILTER_FC
        if (globalFilterQ != 0) gens[9] = globalFilterQ                // GEN_INITIAL_FILTER_Q
        if (globalModLfoToFilterFc != 0) gens[10] = globalModLfoToFilterFc  // GEN_MOD_LFO_TO_FILTER_FC
        if (globalModEnvToFilterFc != 0) gens[11] = globalModEnvToFilterFc  // GEN_MOD_ENV_TO_FILTER_FC
        if (globalModLfoToVolume != 0) gens[13] = globalModLfoToVolume // GEN_MOD_LFO_TO_VOLUME
        if (globalChorusSend != 0) gens[15] = globalChorusSend         // GEN_CHORUS_EFFECTS_SEND
        if (globalReverbSend != 0) gens[16] = globalReverbSend         // GEN_REVERB_EFFECTS_SEND
        if (globalPan != 0) gens[17] = globalPan                       // GEN_PAN
        if (globalModLfoDelay != 0) gens[21] = globalModLfoDelay       // GEN_DELAY_MOD_LFO
        if (globalModLfoFreq != 0) gens[22] = globalModLfoFreq         // GEN_FREQ_MOD_LFO
        if (globalVibLfoDelay != 0) gens[23] = globalVibLfoDelay       // GEN_DELAY_VIB_LFO
        if (globalVibLfoFreq != 0) gens[24] = globalVibLfoFreq         // GEN_FREQ_VIB_LFO
        if (globalModEnvDelay != 0) gens[25] = globalModEnvDelay       // GEN_DELAY_MOD_ENV
        if (globalModEnvAttack != 0) gens[26] = globalModEnvAttack     // GEN_ATTACK_MOD_ENV
        if (globalModEnvHold != 0) gens[27] = globalModEnvHold         // GEN_HOLD_MOD_ENV
        if (globalModEnvDecay != 0) gens[28] = globalModEnvDecay       // GEN_DECAY_MOD_ENV
        if (globalModEnvSustain != 0) gens[29] = globalModEnvSustain   // GEN_SUSTAIN_MOD_ENV
        if (globalModEnvRelease != 0) gens[30] = globalModEnvRelease   // GEN_RELEASE_MOD_ENV
        if (globalKeyToModEnvHold != 0) gens[31] = globalKeyToModEnvHold   // GEN_KEYNUM_TO_MOD_ENV_HOLD
        if (globalKeyToModEnvDecay != 0) gens[32] = globalKeyToModEnvDecay // GEN_KEYNUM_TO_MOD_ENV_DECAY
        if (globalVolEnvDelay != 0) gens[33] = globalVolEnvDelay       // GEN_DELAY_VOL_ENV
        if (globalVolEnvAttack != 0) gens[34] = globalVolEnvAttack     // GEN_ATTACK_VOL_ENV
        if (globalVolEnvHold != 0) gens[35] = globalVolEnvHold         // GEN_HOLD_VOL_ENV
        if (globalVolEnvDecay != 0) gens[36] = globalVolEnvDecay       // GEN_DECAY_VOL_ENV
        if (globalVolEnvSustain != 0) gens[37] = globalVolEnvSustain   // GEN_SUSTAIN_VOL_ENV
        if (globalVolEnvRelease != 0) gens[38] = globalVolEnvRelease   // GEN_RELEASE_VOL_ENV
        if (globalKeyToVolEnvHold != 0) gens[39] = globalKeyToVolEnvHold   // GEN_KEYNUM_TO_VOL_ENV_HOLD
        if (globalKeyToVolEnvDecay != 0) gens[40] = globalKeyToVolEnvDecay // GEN_KEYNUM_TO_VOL_ENV_DECAY
        if (globalAttenuation != 0) gens[48] = globalAttenuation       // GEN_INITIAL_ATTENUATION
        if (globalCoarseTune != 0) gens[51] = globalCoarseTune         // GEN_COARSE_TUNE
        if (globalFineTune != 0) gens[52] = globalFineTune             // GEN_FINE_TUNE
        if (globalScaleTuning != 0) gens[56] = globalScaleTuning       // GEN_SCALE_TUNING
        if (globalExclusiveClass != 0) gens[57] = globalExclusiveClass // GEN_EXCLUSIVE_CLASS

        return gens
    }
}
