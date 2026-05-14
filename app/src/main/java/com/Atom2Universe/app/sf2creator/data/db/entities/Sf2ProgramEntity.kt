package com.Atom2Universe.app.sf2creator.data.db.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a Program (Preset in SF2 terminology) within a project.
 * A Program maps to a MIDI program number (0-127) and bank (0-127).
 * Each Program can contain multiple Instruments for layering or splits.
 *
 * SF2 Hierarchy:
 * Project → Program (this) → Instrument → Sample
 *
 * GLOBAL PARAMETERS:
 * The synthesis parameters defined here are GLOBAL for this program.
 * They are ADDITIVE with instrument-global and sample-specific parameters.
 * Final value = program_global + instrument_global + sample_specific
 *
 * In Polyphone terms:
 * - This is called "Preset" (the top level that MIDI sees)
 * - Contains references to one or more Instruments
 *
 * All global parameters default to 0 (neutral/no effect).
 */
@Entity(
    tableName = "sf2_programs",
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
data class Sf2ProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val name: String,
    val programNumber: Int = 0,  // MIDI program 0-127
    val bankNumber: Int = 0,     // MIDI bank 0-127

    // ==================== GLOBAL PARAMETERS ====================
    // All values are ADDITIVE with instrument and sample values
    // Default = 0 means no modification

    // ==================== Volume/Attenuation ====================
    val globalAttenuation: Int = 0,         // GEN 48

    // ==================== Tuning ====================
    val globalCoarseTune: Int = 0,          // GEN 51
    val globalFineTune: Int = 0,            // GEN 52

    // ==================== Volume Envelope ====================
    val globalVolEnvDelay: Int = 0,         // GEN 33
    val globalVolEnvAttack: Int = 0,        // GEN 34
    val globalVolEnvHold: Int = 0,          // GEN 35
    val globalVolEnvDecay: Int = 0,         // GEN 36
    val globalVolEnvSustain: Int = 0,       // GEN 37
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
    val globalFilterFc: Int = 0,            // GEN 8
    val globalFilterQ: Int = 0,             // GEN 9

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
    val globalExclusiveClass: Int = 0       // GEN 57 (0 = none)
)
