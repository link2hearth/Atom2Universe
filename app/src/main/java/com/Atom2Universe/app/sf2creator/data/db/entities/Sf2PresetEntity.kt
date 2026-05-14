package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a Preset Zone within an SF2 project.
 *
 * SF2 Hierarchy (correct structure):
 * Project → Program (SF2 Preset) → PresetZone (this, PGEN) → Instrument → Sample (IGEN)
 *
 * This entity represents a PRESET ZONE, which contains:
 * 1. Preset zone generators (PGEN) - additive modifiers applied at the preset level
 * 2. Reference to an Instrument (instrumentId) - the actual sound definition
 *
 * KEY CONCEPT: Multiple preset zones can reference the SAME instrument.
 * Example: A piano preset might have two zones (keyRange 0-60 and 61-127)
 * that both point to the same "Piano" instrument.
 *
 * PARAMETER LAYERS (all additive):
 * 1. pgen* = Preset Zone generators (PGEN) - applied at the preset level
 * 2. global* in Sf2InstrumentEntity = Instrument global zone generators (IGEN global)
 * 3. sample-specific = Sample zone generators (IGEN) - per-sample values
 *
 * Final value = pgen + instrument_global + sample_specific
 *
 * All parameters default to 0 (neutral/no effect).
 */
@Entity(
    tableName = "sf2_presets",
    foreignKeys = [
        ForeignKey(
            entity = Sf2ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Sf2ProgramEntity::class,
            parentColumns = ["id"],
            childColumns = ["programId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = Sf2InstrumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["instrumentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("projectId"), Index("programId"), Index("instrumentId")]
)
data class Sf2PresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val name: String,
    // Reference to parent Program (null for standalone preset zones)
    val programId: Long? = null,
    // Reference to the Instrument this zone uses
    val instrumentId: Long,
    // Legacy fields - used when programId is null
    val programNumber: Int = 0,
    val bankNumber: Int = 0,

    // ==================== PRESET ZONE PARAMETERS (PGEN) ====================
    // These are the generators from the preset zone that references the instrument
    // Stored in SF2 native units (timecents for times, centibels for levels, etc.)
    // Default = 0 means no modification at preset level

    // Key/velocity range at preset level (restricts when this instrument plays)
    val pgenKeyRangeLow: Int = 0,
    val pgenKeyRangeHigh: Int = 127,
    val pgenVelRangeLow: Int = 0,
    val pgenVelRangeHigh: Int = 127,

    // Volume/Attenuation (additive to instrument level)
    val pgenAttenuation: Int = 0,           // GEN 48

    // Tuning (additive)
    val pgenCoarseTune: Int = 0,            // GEN 51
    val pgenFineTune: Int = 0,              // GEN 52

    // Filter (additive)
    val pgenFilterFc: Int = 0,              // GEN 8
    val pgenFilterQ: Int = 0,               // GEN 9

    // Effects (additive)
    val pgenChorusSend: Int = 0,            // GEN 15
    val pgenReverbSend: Int = 0,            // GEN 16
    val pgenPan: Int = 0,                   // GEN 17

    // Volume Envelope (additive in timecents)
    val pgenVolEnvDelay: Int = 0,           // GEN 33
    val pgenVolEnvAttack: Int = 0,          // GEN 34
    val pgenVolEnvHold: Int = 0,            // GEN 35
    val pgenVolEnvDecay: Int = 0,           // GEN 36
    val pgenVolEnvSustain: Int = 0,         // GEN 37
    val pgenVolEnvRelease: Int = 0,         // GEN 38

    // Modulation Envelope (additive)
    val pgenModEnvDelay: Int = 0,           // GEN 25
    val pgenModEnvAttack: Int = 0,          // GEN 26
    val pgenModEnvHold: Int = 0,            // GEN 27
    val pgenModEnvDecay: Int = 0,           // GEN 28
    val pgenModEnvSustain: Int = 0,         // GEN 29
    val pgenModEnvRelease: Int = 0,         // GEN 30
    val pgenModEnvToPitch: Int = 0,         // GEN 7
    val pgenModEnvToFilterFc: Int = 0,      // GEN 11

    // Vibrato LFO (additive)
    val pgenVibLfoDelay: Int = 0,           // GEN 23
    val pgenVibLfoFreq: Int = 0,            // GEN 24
    val pgenVibLfoToPitch: Int = 0,         // GEN 6

    // Modulation LFO (additive)
    val pgenModLfoDelay: Int = 0,           // GEN 21
    val pgenModLfoFreq: Int = 0,            // GEN 22
    val pgenModLfoToPitch: Int = 0,         // GEN 5
    val pgenModLfoToFilterFc: Int = 0,      // GEN 10
    val pgenModLfoToVolume: Int = 0,        // GEN 13

    // Key-based Envelope Scaling (additive)
    val pgenKeyToModEnvHold: Int = 0,       // GEN 31
    val pgenKeyToModEnvDecay: Int = 0,      // GEN 32
    val pgenKeyToVolEnvHold: Int = 0,       // GEN 39
    val pgenKeyToVolEnvDecay: Int = 0,      // GEN 40

    // Additional generators
    val pgenScaleTuning: Int = 0,           // GEN 56
    val pgenExclusiveClass: Int = 0,        // GEN 57

    // ==================== Hybrid Passthrough Tracking ====================
    /**
     * SHA-256 hash of the preset zone's state at import time.
     * Null for preset zones created from scratch.
     */
    val originalStateHash: String? = null,

    /**
     * Whether this preset zone has been modified by the user since import.
     */
    val isModifiedByUser: Boolean = false,

    /**
     * Bitmap of modification types (see ModificationFlags).
     */
    val modificationFlags: Int = 0
) {
    /**
     * Check if this preset zone has any non-default PGEN parameters (excluding ranges).
     */
    fun hasNonDefaultPgenParams(): Boolean {
        return pgenAttenuation != 0 || pgenCoarseTune != 0 || pgenFineTune != 0 ||
               pgenFilterFc != 0 || pgenFilterQ != 0 ||
               pgenChorusSend != 0 || pgenReverbSend != 0 || pgenPan != 0 ||
               pgenVolEnvDelay != 0 || pgenVolEnvAttack != 0 || pgenVolEnvHold != 0 ||
               pgenVolEnvDecay != 0 || pgenVolEnvSustain != 0 || pgenVolEnvRelease != 0 ||
               pgenModEnvDelay != 0 || pgenModEnvAttack != 0 || pgenModEnvHold != 0 ||
               pgenModEnvDecay != 0 || pgenModEnvSustain != 0 || pgenModEnvRelease != 0 ||
               pgenModEnvToPitch != 0 || pgenModEnvToFilterFc != 0 ||
               pgenVibLfoDelay != 0 || pgenVibLfoFreq != 0 || pgenVibLfoToPitch != 0 ||
               pgenModLfoDelay != 0 || pgenModLfoFreq != 0 || pgenModLfoToPitch != 0 ||
               pgenModLfoToFilterFc != 0 || pgenModLfoToVolume != 0 ||
               pgenKeyToModEnvHold != 0 || pgenKeyToModEnvDecay != 0 ||
               pgenKeyToVolEnvHold != 0 || pgenKeyToVolEnvDecay != 0 ||
               pgenScaleTuning != 0 || pgenExclusiveClass != 0
    }

    /**
     * Check if this preset zone has a non-default key range.
     */
    fun hasNonDefaultKeyRange(): Boolean {
        return pgenKeyRangeLow != 0 || pgenKeyRangeHigh != 127
    }

    /**
     * Check if this preset zone has a non-default velocity range.
     */
    fun hasNonDefaultVelRange(): Boolean {
        return pgenVelRangeLow != 0 || pgenVelRangeHigh != 127
    }
}
