package com.Atom2Universe.app.sf2creator.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2IndexEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ModulatorEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PatchEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2PresetEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProgramEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ProjectEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SampleEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2SourceMetadataEntity

/**
 * Room database for SF2 Creator projects.
 *
 * Hierarchy:
 * - Sf2ProjectEntity: The SF2 file/project
 * - Sf2ProgramEntity: MIDI Program (0-127, Bank 0-127) - what sequencers see
 * - Sf2PresetEntity: Preset Zone (PGEN parameters + reference to instrument)
 * - Sf2InstrumentEntity: Instrument (IGEN global parameters, can be shared)
 * - Sf2SampleEntity: Audio samples with zones (IGEN parameters)
 *
 * Version History:
 * - v1: Initial schema
 * - v2: Added Programs hierarchy
 * - v3: Added advanced SF2 parameters (Mod Env, LFOs, global params)
 * - v4: Added Modulators table and key-to-envelope generators
 * - v5: Added preset zone parameters (PGEN) to preserve SF2 hierarchy
 * - v6: Added pitchCorrection to samples for lossless SF2 import/export
 * - v7: Converted sample parameters to SF2 native units
 * - v8: Added programId to modulators for global preset-level modulators (PMOD global zone)
 * - v9: Separated Instruments from Presets - Presets now reference Instruments via instrumentId
 * - v10: Added hybrid passthrough support:
 *        - New table sf2_source_metadata for tracking original SF2 file
 *        - Added originalStateHash, isModifiedByUser, modificationFlags to samples, presets, instruments
 * - v12: Added sampleIndexMapping to sf2_source_metadata for hybrid passthrough
 * - v13: Added sampleModes to sf2_samples for full SF2 loop mode support
 * - v11: Added missing global zone generators:
 *        - keyToModEnvHold/Decay, keyToVolEnvHold/Decay, scaleTuning, exclusiveClass
 *        - Added to sf2_instruments, sf2_programs, sf2_presets
 * - v14: Polyphone-like architecture:
 *        - Added sourceFilePath, isLegacyProject to sf2_projects
 *        - New table sf2_patches for storing modifications only
 *        - New table sf2_index for lightweight navigation
 */
@Database(
    entities = [
        Sf2ProjectEntity::class,
        Sf2ProgramEntity::class,
        Sf2PresetEntity::class,
        Sf2InstrumentEntity::class,
        Sf2SampleEntity::class,
        Sf2ModulatorEntity::class,
        Sf2SourceMetadataEntity::class,
        Sf2PatchEntity::class,
        Sf2IndexEntity::class
    ],
    version = 15,
    exportSchema = false
)
abstract class Sf2ProjectDatabase : RoomDatabase() {

    abstract fun projectDao(): Sf2ProjectDao
    abstract fun patchDao(): Sf2PatchDao
    abstract fun indexDao(): Sf2IndexDao

    companion object {
        @Volatile
        private var INSTANCE: Sf2ProjectDatabase? = null

        fun getInstance(context: Context): Sf2ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /**
         * Migration from version 2 to 3.
         * Adds advanced SF2 parameters:
         * - Modulation Envelope (8 params per sample)
         * - Vibrato LFO (3 params per sample)
         * - Modulation LFO (5 params per sample)
         * - Additional Volume Envelope params (delay, hold)
         * - Tuning params (coarse tune, scale tuning)
         * - Velocity range
         * - Exclusive class
         * - Global params for Presets (Instruments)
         * - Global params for Programs
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // ==================== sf2_samples table ====================
                // Velocity range
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN velRangeStart INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN velRangeEnd INTEGER NOT NULL DEFAULT 127")

                // Tuning
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN coarseTune INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN scaleTuning INTEGER NOT NULL DEFAULT 100")

                // Volume Envelope (delay, hold)
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN volEnvDelayMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN volEnvHoldMs INTEGER NOT NULL DEFAULT 0")

                // Modulation Envelope
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvDelayMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvAttackMs INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvHoldMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvDecayMs INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvSustainPercent INTEGER NOT NULL DEFAULT 100")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvReleaseMs INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modEnvToFilterFc INTEGER NOT NULL DEFAULT 0")

                // Vibrato LFO
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN vibLfoDelayMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN vibLfoFreqCents INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN vibLfoToPitch INTEGER NOT NULL DEFAULT 0")

                // Modulation LFO
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modLfoDelayMs INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modLfoFreqCents INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modLfoToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modLfoToFilterFc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modLfoToVolume INTEGER NOT NULL DEFAULT 0")

                // Exclusive class
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN exclusiveClass INTEGER NOT NULL DEFAULT 0")

                // ==================== sf2_presets table (Instruments) ====================
                // Global parameters
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalAttenuation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalCoarseTune INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalFineTune INTEGER NOT NULL DEFAULT 0")

                // Global Volume Envelope
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVolEnvDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVolEnvAttack INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVolEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVolEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVolEnvSustain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVolEnvRelease INTEGER NOT NULL DEFAULT 0")

                // Global Modulation Envelope
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvAttack INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvSustain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvRelease INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModEnvToFilterFc INTEGER NOT NULL DEFAULT 0")

                // Global Vibrato LFO
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVibLfoDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVibLfoFreq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalVibLfoToPitch INTEGER NOT NULL DEFAULT 0")

                // Global Modulation LFO
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModLfoDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModLfoFreq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModLfoToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModLfoToFilterFc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalModLfoToVolume INTEGER NOT NULL DEFAULT 0")

                // Global Filter
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalFilterFc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalFilterQ INTEGER NOT NULL DEFAULT 0")

                // Global Effects
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalChorusSend INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalReverbSend INTEGER NOT NULL DEFAULT 0")

                // Global Pan
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN globalPan INTEGER NOT NULL DEFAULT 0")

                // ==================== sf2_programs table ====================
                // Global parameters (same as presets)
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalAttenuation INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalCoarseTune INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalFineTune INTEGER NOT NULL DEFAULT 0")

                // Global Volume Envelope
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVolEnvDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVolEnvAttack INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVolEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVolEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVolEnvSustain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVolEnvRelease INTEGER NOT NULL DEFAULT 0")

                // Global Modulation Envelope
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvAttack INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvSustain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvRelease INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModEnvToFilterFc INTEGER NOT NULL DEFAULT 0")

                // Global Vibrato LFO
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVibLfoDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVibLfoFreq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalVibLfoToPitch INTEGER NOT NULL DEFAULT 0")

                // Global Modulation LFO
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModLfoDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModLfoFreq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModLfoToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModLfoToFilterFc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalModLfoToVolume INTEGER NOT NULL DEFAULT 0")

                // Global Filter
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalFilterFc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalFilterQ INTEGER NOT NULL DEFAULT 0")

                // Global Effects
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalChorusSend INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalReverbSend INTEGER NOT NULL DEFAULT 0")

                // Global Pan
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalPan INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 3 to 4.
         * Adds:
         * - Modulators table (sf2_modulators) for storing pmod/imod data
         * - Key-to-envelope scaling generators (GEN 31, 32, 39, 40)
         * - Fixed key/velocity generators (GEN 46, 47)
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create sf2_modulators table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sf2_modulators (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        presetId INTEGER,
                        sampleId INTEGER,
                        srcOper INTEGER NOT NULL,
                        destOper INTEGER NOT NULL,
                        amount INTEGER NOT NULL,
                        amtSrcOper INTEGER NOT NULL,
                        transOper INTEGER NOT NULL,
                        FOREIGN KEY (presetId) REFERENCES sf2_presets(id) ON DELETE CASCADE,
                        FOREIGN KEY (sampleId) REFERENCES sf2_samples(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_modulators_presetId ON sf2_modulators(presetId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_modulators_sampleId ON sf2_modulators(sampleId)")

                // Add key-to-envelope scaling generators to sf2_samples
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN keyToVolEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN keyToVolEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN keyToModEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN keyToModEnvDecay INTEGER NOT NULL DEFAULT 0")

                // Add fixed key/velocity generators to sf2_samples (-1 = not fixed)
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN fixedKey INTEGER NOT NULL DEFAULT -1")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN fixedVelocity INTEGER NOT NULL DEFAULT -1")
            }
        }

        /**
         * Migration from version 4 to 5.
         * Adds preset zone parameters (PGEN) to sf2_presets table.
         * These are the generators from the preset zone that references this instrument,
         * preserving the full SF2 hierarchy: Preset -> Preset Zone (PGEN) -> Instrument
         */
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Key/velocity range at preset level
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenKeyRangeLow INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenKeyRangeHigh INTEGER NOT NULL DEFAULT 127")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVelRangeLow INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVelRangeHigh INTEGER NOT NULL DEFAULT 127")

                // Volume/Attenuation
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenAttenuation INTEGER NOT NULL DEFAULT 0")

                // Tuning
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenCoarseTune INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenFineTune INTEGER NOT NULL DEFAULT 0")

                // Filter
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenFilterFc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenFilterQ INTEGER NOT NULL DEFAULT 0")

                // Effects
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenChorusSend INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenReverbSend INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenPan INTEGER NOT NULL DEFAULT 0")

                // Volume Envelope
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVolEnvDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVolEnvAttack INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVolEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVolEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVolEnvSustain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVolEnvRelease INTEGER NOT NULL DEFAULT 0")

                // Modulation Envelope
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvAttack INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvSustain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvRelease INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModEnvToFilterFc INTEGER NOT NULL DEFAULT 0")

                // Vibrato LFO
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVibLfoDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVibLfoFreq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenVibLfoToPitch INTEGER NOT NULL DEFAULT 0")

                // Modulation LFO
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModLfoDelay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModLfoFreq INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModLfoToPitch INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModLfoToFilterFc INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenModLfoToVolume INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 5 to 6.
         * Adds pitchCorrection to samples for lossless SF2 import/export.
         * pitchCorrection is stored in the sample header (shdr) in SF2 files.
         */
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add pitchCorrection to sf2_samples (from sample header, in cents -99 to +99)
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN pitchCorrection INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 6 to 7.
         * Converts sample parameters from human-readable units to SF2 native units:
         * - Time values (ms) → timecents
         * - Sustain (%) → centibels of attenuation
         * - Filter cutoff (Hz) → absolute cents
         *
         * Column renames:
         * - volEnvDelayMs → volEnvDelay, attackMs → volEnvAttack, etc.
         * - sustainPercent → volEnvSustain (now centibels)
         * - filterCutoffHz → filterFc (now cents)
         * - filterResonanceCb → filterQ
         * - vibLfoDelayMs → vibLfoDelay, vibLfoFreqCents → vibLfoFreq
         * - modLfoDelayMs → modLfoDelay, modLfoFreqCents → modLfoFreq
         *
         * Strategy: Create new table with SF2 native columns and default values.
         * Existing custom envelope/filter settings will be reset to SF2 defaults.
         * Audio data, key/velocity ranges, and other parameters are preserved.
         */
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create new table with SF2 native unit columns
                db.execSQL("""
                    CREATE TABLE sf2_samples_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        presetId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        audioFilePath TEXT NOT NULL,
                        sampleRate INTEGER NOT NULL DEFAULT 44100,
                        rootNote INTEGER NOT NULL,
                        keyRangeStart INTEGER NOT NULL DEFAULT 0,
                        keyRangeEnd INTEGER NOT NULL DEFAULT 127,
                        velRangeStart INTEGER NOT NULL DEFAULT 0,
                        velRangeEnd INTEGER NOT NULL DEFAULT 127,
                        loopStart INTEGER NOT NULL DEFAULT 0,
                        loopEnd INTEGER NOT NULL DEFAULT 0,
                        hasLoop INTEGER NOT NULL DEFAULT 0,
                        attenuation INTEGER NOT NULL DEFAULT 0,
                        coarseTune INTEGER NOT NULL DEFAULT 0,
                        fineTuneCents INTEGER NOT NULL DEFAULT 0,
                        scaleTuning INTEGER NOT NULL DEFAULT 100,
                        volEnvDelay INTEGER NOT NULL DEFAULT -12000,
                        volEnvAttack INTEGER NOT NULL DEFAULT -12000,
                        volEnvHold INTEGER NOT NULL DEFAULT -12000,
                        volEnvDecay INTEGER NOT NULL DEFAULT -12000,
                        volEnvSustain INTEGER NOT NULL DEFAULT 0,
                        volEnvRelease INTEGER NOT NULL DEFAULT -12000,
                        modEnvDelay INTEGER NOT NULL DEFAULT -12000,
                        modEnvAttack INTEGER NOT NULL DEFAULT -12000,
                        modEnvHold INTEGER NOT NULL DEFAULT -12000,
                        modEnvDecay INTEGER NOT NULL DEFAULT -12000,
                        modEnvSustain INTEGER NOT NULL DEFAULT 0,
                        modEnvRelease INTEGER NOT NULL DEFAULT -12000,
                        modEnvToPitch INTEGER NOT NULL DEFAULT 0,
                        modEnvToFilterFc INTEGER NOT NULL DEFAULT 0,
                        vibLfoDelay INTEGER NOT NULL DEFAULT -12000,
                        vibLfoFreq INTEGER NOT NULL DEFAULT 0,
                        vibLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        modLfoDelay INTEGER NOT NULL DEFAULT -12000,
                        modLfoFreq INTEGER NOT NULL DEFAULT 0,
                        modLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        modLfoToFilterFc INTEGER NOT NULL DEFAULT 0,
                        modLfoToVolume INTEGER NOT NULL DEFAULT 0,
                        filterFc INTEGER NOT NULL DEFAULT 13500,
                        filterQ INTEGER NOT NULL DEFAULT 0,
                        chorusSend INTEGER NOT NULL DEFAULT 0,
                        reverbSend INTEGER NOT NULL DEFAULT 0,
                        pan INTEGER NOT NULL DEFAULT 0,
                        exclusiveClass INTEGER NOT NULL DEFAULT 0,
                        keyToVolEnvHold INTEGER NOT NULL DEFAULT 0,
                        keyToVolEnvDecay INTEGER NOT NULL DEFAULT 0,
                        keyToModEnvHold INTEGER NOT NULL DEFAULT 0,
                        keyToModEnvDecay INTEGER NOT NULL DEFAULT 0,
                        fixedKey INTEGER NOT NULL DEFAULT -1,
                        fixedVelocity INTEGER NOT NULL DEFAULT -1,
                        pitchCorrection INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (presetId) REFERENCES sf2_presets(id) ON DELETE CASCADE
                    )
                """)

                // Copy data that doesn't need conversion (metadata, ranges, etc.)
                // Envelope/filter params get SF2 default values
                db.execSQL("""
                    INSERT INTO sf2_samples_new (
                        id, presetId, name, audioFilePath, sampleRate, rootNote,
                        keyRangeStart, keyRangeEnd, velRangeStart, velRangeEnd,
                        loopStart, loopEnd, hasLoop,
                        attenuation, coarseTune, fineTuneCents, scaleTuning,
                        modEnvToPitch, modEnvToFilterFc,
                        vibLfoToPitch,
                        modLfoToPitch, modLfoToFilterFc, modLfoToVolume,
                        chorusSend, reverbSend, pan, exclusiveClass,
                        keyToVolEnvHold, keyToVolEnvDecay, keyToModEnvHold, keyToModEnvDecay,
                        fixedKey, fixedVelocity, pitchCorrection
                    )
                    SELECT
                        id, presetId, name, audioFilePath, sampleRate, rootNote,
                        keyRangeStart, keyRangeEnd, velRangeStart, velRangeEnd,
                        loopStart, loopEnd, hasLoop,
                        attenuation, coarseTune, fineTuneCents, scaleTuning,
                        modEnvToPitch, modEnvToFilterFc,
                        vibLfoToPitch,
                        modLfoToPitch, modLfoToFilterFc, modLfoToVolume,
                        chorusSend, reverbSend, pan, exclusiveClass,
                        keyToVolEnvHold, keyToVolEnvDecay, keyToModEnvHold, keyToModEnvDecay,
                        fixedKey, fixedVelocity, pitchCorrection
                    FROM sf2_samples
                """)

                // Drop old table and rename new one
                db.execSQL("DROP TABLE sf2_samples")
                db.execSQL("ALTER TABLE sf2_samples_new RENAME TO sf2_samples")

                // Recreate index
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_samples_presetId ON sf2_samples(presetId)")
            }
        }

        /**
         * Migration from version 7 to 8.
         * Adds programId to sf2_modulators for global preset-level modulators (PMOD global zone).
         * This allows storing modulators that apply to all zones within a preset/program.
         */
        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add programId column to sf2_modulators
                db.execSQL("ALTER TABLE sf2_modulators ADD COLUMN programId INTEGER DEFAULT NULL REFERENCES sf2_programs(id) ON DELETE CASCADE")
                // Create index for programId
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_modulators_programId ON sf2_modulators(programId)")
            }
        }

        /**
         * Migration from version 8 to 9.
         * Major restructuring: separates Instruments from Presets.
         *
         * Before: Sf2PresetEntity contained both preset zone (PGEN) AND instrument global (IGEN) parameters
         * After: Sf2PresetEntity contains only preset zone (PGEN) parameters and references an Sf2InstrumentEntity
         *
         * Changes:
         * 1. Create sf2_instruments table with global* parameters
         * 2. Migrate global* from sf2_presets to sf2_instruments
         * 3. Update sf2_presets to reference instruments via instrumentId
         * 4. Update sf2_samples: presetId → instrumentId
         * 5. Add instrumentId to sf2_modulators
         */
        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create sf2_instruments table with all global* parameters
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sf2_instruments (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        globalAttenuation INTEGER NOT NULL DEFAULT 0,
                        globalCoarseTune INTEGER NOT NULL DEFAULT 0,
                        globalFineTune INTEGER NOT NULL DEFAULT 0,
                        globalVolEnvDelay INTEGER NOT NULL DEFAULT 0,
                        globalVolEnvAttack INTEGER NOT NULL DEFAULT 0,
                        globalVolEnvHold INTEGER NOT NULL DEFAULT 0,
                        globalVolEnvDecay INTEGER NOT NULL DEFAULT 0,
                        globalVolEnvSustain INTEGER NOT NULL DEFAULT 0,
                        globalVolEnvRelease INTEGER NOT NULL DEFAULT 0,
                        globalModEnvDelay INTEGER NOT NULL DEFAULT 0,
                        globalModEnvAttack INTEGER NOT NULL DEFAULT 0,
                        globalModEnvHold INTEGER NOT NULL DEFAULT 0,
                        globalModEnvDecay INTEGER NOT NULL DEFAULT 0,
                        globalModEnvSustain INTEGER NOT NULL DEFAULT 0,
                        globalModEnvRelease INTEGER NOT NULL DEFAULT 0,
                        globalModEnvToPitch INTEGER NOT NULL DEFAULT 0,
                        globalModEnvToFilterFc INTEGER NOT NULL DEFAULT 0,
                        globalVibLfoDelay INTEGER NOT NULL DEFAULT 0,
                        globalVibLfoFreq INTEGER NOT NULL DEFAULT 0,
                        globalVibLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        globalModLfoDelay INTEGER NOT NULL DEFAULT 0,
                        globalModLfoFreq INTEGER NOT NULL DEFAULT 0,
                        globalModLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        globalModLfoToFilterFc INTEGER NOT NULL DEFAULT 0,
                        globalModLfoToVolume INTEGER NOT NULL DEFAULT 0,
                        globalFilterFc INTEGER NOT NULL DEFAULT 0,
                        globalFilterQ INTEGER NOT NULL DEFAULT 0,
                        globalChorusSend INTEGER NOT NULL DEFAULT 0,
                        globalReverbSend INTEGER NOT NULL DEFAULT 0,
                        globalPan INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (projectId) REFERENCES sf2_projects(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_instruments_projectId ON sf2_instruments(projectId)")

                // 2. Copy data from sf2_presets to sf2_instruments (1:1 during migration)
                // Each existing preset becomes an instrument with the same ID
                db.execSQL("""
                    INSERT INTO sf2_instruments (
                        id, projectId, name,
                        globalAttenuation, globalCoarseTune, globalFineTune,
                        globalVolEnvDelay, globalVolEnvAttack, globalVolEnvHold,
                        globalVolEnvDecay, globalVolEnvSustain, globalVolEnvRelease,
                        globalModEnvDelay, globalModEnvAttack, globalModEnvHold,
                        globalModEnvDecay, globalModEnvSustain, globalModEnvRelease,
                        globalModEnvToPitch, globalModEnvToFilterFc,
                        globalVibLfoDelay, globalVibLfoFreq, globalVibLfoToPitch,
                        globalModLfoDelay, globalModLfoFreq, globalModLfoToPitch,
                        globalModLfoToFilterFc, globalModLfoToVolume,
                        globalFilterFc, globalFilterQ,
                        globalChorusSend, globalReverbSend, globalPan
                    )
                    SELECT
                        id, projectId, name,
                        globalAttenuation, globalCoarseTune, globalFineTune,
                        globalVolEnvDelay, globalVolEnvAttack, globalVolEnvHold,
                        globalVolEnvDecay, globalVolEnvSustain, globalVolEnvRelease,
                        globalModEnvDelay, globalModEnvAttack, globalModEnvHold,
                        globalModEnvDecay, globalModEnvSustain, globalModEnvRelease,
                        globalModEnvToPitch, globalModEnvToFilterFc,
                        globalVibLfoDelay, globalVibLfoFreq, globalVibLfoToPitch,
                        globalModLfoDelay, globalModLfoFreq, globalModLfoToPitch,
                        globalModLfoToFilterFc, globalModLfoToVolume,
                        globalFilterFc, globalFilterQ,
                        globalChorusSend, globalReverbSend, globalPan
                    FROM sf2_presets
                """)

                // 3. Recreate sf2_presets without global* columns, with instrumentId
                db.execSQL("""
                    CREATE TABLE sf2_presets_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        programId INTEGER,
                        instrumentId INTEGER NOT NULL,
                        programNumber INTEGER NOT NULL DEFAULT 0,
                        bankNumber INTEGER NOT NULL DEFAULT 0,
                        pgenKeyRangeLow INTEGER NOT NULL DEFAULT 0,
                        pgenKeyRangeHigh INTEGER NOT NULL DEFAULT 127,
                        pgenVelRangeLow INTEGER NOT NULL DEFAULT 0,
                        pgenVelRangeHigh INTEGER NOT NULL DEFAULT 127,
                        pgenAttenuation INTEGER NOT NULL DEFAULT 0,
                        pgenCoarseTune INTEGER NOT NULL DEFAULT 0,
                        pgenFineTune INTEGER NOT NULL DEFAULT 0,
                        pgenFilterFc INTEGER NOT NULL DEFAULT 0,
                        pgenFilterQ INTEGER NOT NULL DEFAULT 0,
                        pgenChorusSend INTEGER NOT NULL DEFAULT 0,
                        pgenReverbSend INTEGER NOT NULL DEFAULT 0,
                        pgenPan INTEGER NOT NULL DEFAULT 0,
                        pgenVolEnvDelay INTEGER NOT NULL DEFAULT 0,
                        pgenVolEnvAttack INTEGER NOT NULL DEFAULT 0,
                        pgenVolEnvHold INTEGER NOT NULL DEFAULT 0,
                        pgenVolEnvDecay INTEGER NOT NULL DEFAULT 0,
                        pgenVolEnvSustain INTEGER NOT NULL DEFAULT 0,
                        pgenVolEnvRelease INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvDelay INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvAttack INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvHold INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvDecay INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvSustain INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvRelease INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvToPitch INTEGER NOT NULL DEFAULT 0,
                        pgenModEnvToFilterFc INTEGER NOT NULL DEFAULT 0,
                        pgenVibLfoDelay INTEGER NOT NULL DEFAULT 0,
                        pgenVibLfoFreq INTEGER NOT NULL DEFAULT 0,
                        pgenVibLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        pgenModLfoDelay INTEGER NOT NULL DEFAULT 0,
                        pgenModLfoFreq INTEGER NOT NULL DEFAULT 0,
                        pgenModLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        pgenModLfoToFilterFc INTEGER NOT NULL DEFAULT 0,
                        pgenModLfoToVolume INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (projectId) REFERENCES sf2_projects(id) ON DELETE CASCADE,
                        FOREIGN KEY (programId) REFERENCES sf2_programs(id) ON DELETE SET NULL,
                        FOREIGN KEY (instrumentId) REFERENCES sf2_instruments(id) ON DELETE CASCADE
                    )
                """)

                // Copy data: use preset.id as instrumentId (1:1 mapping during migration)
                db.execSQL("""
                    INSERT INTO sf2_presets_new (
                        id, projectId, name, programId, instrumentId, programNumber, bankNumber,
                        pgenKeyRangeLow, pgenKeyRangeHigh, pgenVelRangeLow, pgenVelRangeHigh,
                        pgenAttenuation, pgenCoarseTune, pgenFineTune,
                        pgenFilterFc, pgenFilterQ, pgenChorusSend, pgenReverbSend, pgenPan,
                        pgenVolEnvDelay, pgenVolEnvAttack, pgenVolEnvHold,
                        pgenVolEnvDecay, pgenVolEnvSustain, pgenVolEnvRelease,
                        pgenModEnvDelay, pgenModEnvAttack, pgenModEnvHold,
                        pgenModEnvDecay, pgenModEnvSustain, pgenModEnvRelease,
                        pgenModEnvToPitch, pgenModEnvToFilterFc,
                        pgenVibLfoDelay, pgenVibLfoFreq, pgenVibLfoToPitch,
                        pgenModLfoDelay, pgenModLfoFreq, pgenModLfoToPitch,
                        pgenModLfoToFilterFc, pgenModLfoToVolume
                    )
                    SELECT
                        id, projectId, name, programId, id, programNumber, bankNumber,
                        pgenKeyRangeLow, pgenKeyRangeHigh, pgenVelRangeLow, pgenVelRangeHigh,
                        pgenAttenuation, pgenCoarseTune, pgenFineTune,
                        pgenFilterFc, pgenFilterQ, pgenChorusSend, pgenReverbSend, pgenPan,
                        pgenVolEnvDelay, pgenVolEnvAttack, pgenVolEnvHold,
                        pgenVolEnvDecay, pgenVolEnvSustain, pgenVolEnvRelease,
                        pgenModEnvDelay, pgenModEnvAttack, pgenModEnvHold,
                        pgenModEnvDecay, pgenModEnvSustain, pgenModEnvRelease,
                        pgenModEnvToPitch, pgenModEnvToFilterFc,
                        pgenVibLfoDelay, pgenVibLfoFreq, pgenVibLfoToPitch,
                        pgenModLfoDelay, pgenModLfoFreq, pgenModLfoToPitch,
                        pgenModLfoToFilterFc, pgenModLfoToVolume
                    FROM sf2_presets
                """)

                db.execSQL("DROP TABLE sf2_presets")
                db.execSQL("ALTER TABLE sf2_presets_new RENAME TO sf2_presets")

                // Recreate indices for sf2_presets
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_presets_projectId ON sf2_presets(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_presets_programId ON sf2_presets(programId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_presets_instrumentId ON sf2_presets(instrumentId)")

                // 4. Update sf2_samples: presetId → instrumentId
                // During migration, presetId = instrumentId (same value)
                db.execSQL("""
                    CREATE TABLE sf2_samples_new (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        instrumentId INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        audioFilePath TEXT NOT NULL,
                        sampleRate INTEGER NOT NULL DEFAULT 44100,
                        rootNote INTEGER NOT NULL,
                        keyRangeStart INTEGER NOT NULL DEFAULT 0,
                        keyRangeEnd INTEGER NOT NULL DEFAULT 127,
                        velRangeStart INTEGER NOT NULL DEFAULT 0,
                        velRangeEnd INTEGER NOT NULL DEFAULT 127,
                        loopStart INTEGER NOT NULL DEFAULT 0,
                        loopEnd INTEGER NOT NULL DEFAULT 0,
                        hasLoop INTEGER NOT NULL DEFAULT 0,
                        attenuation INTEGER NOT NULL DEFAULT 0,
                        coarseTune INTEGER NOT NULL DEFAULT 0,
                        fineTuneCents INTEGER NOT NULL DEFAULT 0,
                        scaleTuning INTEGER NOT NULL DEFAULT 100,
                        volEnvDelay INTEGER NOT NULL DEFAULT -12000,
                        volEnvAttack INTEGER NOT NULL DEFAULT -12000,
                        volEnvHold INTEGER NOT NULL DEFAULT -12000,
                        volEnvDecay INTEGER NOT NULL DEFAULT -12000,
                        volEnvSustain INTEGER NOT NULL DEFAULT 0,
                        volEnvRelease INTEGER NOT NULL DEFAULT -12000,
                        modEnvDelay INTEGER NOT NULL DEFAULT -12000,
                        modEnvAttack INTEGER NOT NULL DEFAULT -12000,
                        modEnvHold INTEGER NOT NULL DEFAULT -12000,
                        modEnvDecay INTEGER NOT NULL DEFAULT -12000,
                        modEnvSustain INTEGER NOT NULL DEFAULT 0,
                        modEnvRelease INTEGER NOT NULL DEFAULT -12000,
                        modEnvToPitch INTEGER NOT NULL DEFAULT 0,
                        modEnvToFilterFc INTEGER NOT NULL DEFAULT 0,
                        vibLfoDelay INTEGER NOT NULL DEFAULT -12000,
                        vibLfoFreq INTEGER NOT NULL DEFAULT 0,
                        vibLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        modLfoDelay INTEGER NOT NULL DEFAULT -12000,
                        modLfoFreq INTEGER NOT NULL DEFAULT 0,
                        modLfoToPitch INTEGER NOT NULL DEFAULT 0,
                        modLfoToFilterFc INTEGER NOT NULL DEFAULT 0,
                        modLfoToVolume INTEGER NOT NULL DEFAULT 0,
                        filterFc INTEGER NOT NULL DEFAULT 13500,
                        filterQ INTEGER NOT NULL DEFAULT 0,
                        chorusSend INTEGER NOT NULL DEFAULT 0,
                        reverbSend INTEGER NOT NULL DEFAULT 0,
                        pan INTEGER NOT NULL DEFAULT 0,
                        exclusiveClass INTEGER NOT NULL DEFAULT 0,
                        keyToVolEnvHold INTEGER NOT NULL DEFAULT 0,
                        keyToVolEnvDecay INTEGER NOT NULL DEFAULT 0,
                        keyToModEnvHold INTEGER NOT NULL DEFAULT 0,
                        keyToModEnvDecay INTEGER NOT NULL DEFAULT 0,
                        fixedKey INTEGER NOT NULL DEFAULT -1,
                        fixedVelocity INTEGER NOT NULL DEFAULT -1,
                        pitchCorrection INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (instrumentId) REFERENCES sf2_instruments(id) ON DELETE CASCADE
                    )
                """)

                // Copy data: presetId becomes instrumentId
                db.execSQL("""
                    INSERT INTO sf2_samples_new (
                        id, instrumentId, name, audioFilePath, sampleRate, rootNote,
                        keyRangeStart, keyRangeEnd, velRangeStart, velRangeEnd,
                        loopStart, loopEnd, hasLoop,
                        attenuation, coarseTune, fineTuneCents, scaleTuning,
                        volEnvDelay, volEnvAttack, volEnvHold, volEnvDecay, volEnvSustain, volEnvRelease,
                        modEnvDelay, modEnvAttack, modEnvHold, modEnvDecay, modEnvSustain, modEnvRelease,
                        modEnvToPitch, modEnvToFilterFc,
                        vibLfoDelay, vibLfoFreq, vibLfoToPitch,
                        modLfoDelay, modLfoFreq, modLfoToPitch, modLfoToFilterFc, modLfoToVolume,
                        filterFc, filterQ, chorusSend, reverbSend, pan, exclusiveClass,
                        keyToVolEnvHold, keyToVolEnvDecay, keyToModEnvHold, keyToModEnvDecay,
                        fixedKey, fixedVelocity, pitchCorrection
                    )
                    SELECT
                        id, presetId, name, audioFilePath, sampleRate, rootNote,
                        keyRangeStart, keyRangeEnd, velRangeStart, velRangeEnd,
                        loopStart, loopEnd, hasLoop,
                        attenuation, coarseTune, fineTuneCents, scaleTuning,
                        volEnvDelay, volEnvAttack, volEnvHold, volEnvDecay, volEnvSustain, volEnvRelease,
                        modEnvDelay, modEnvAttack, modEnvHold, modEnvDecay, modEnvSustain, modEnvRelease,
                        modEnvToPitch, modEnvToFilterFc,
                        vibLfoDelay, vibLfoFreq, vibLfoToPitch,
                        modLfoDelay, modLfoFreq, modLfoToPitch, modLfoToFilterFc, modLfoToVolume,
                        filterFc, filterQ, chorusSend, reverbSend, pan, exclusiveClass,
                        keyToVolEnvHold, keyToVolEnvDecay, keyToModEnvHold, keyToModEnvDecay,
                        fixedKey, fixedVelocity, pitchCorrection
                    FROM sf2_samples
                """)

                db.execSQL("DROP TABLE sf2_samples")
                db.execSQL("ALTER TABLE sf2_samples_new RENAME TO sf2_samples")

                // Recreate index for sf2_samples
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_samples_instrumentId ON sf2_samples(instrumentId)")

                // 5. Add instrumentId to sf2_modulators
                db.execSQL("ALTER TABLE sf2_modulators ADD COLUMN instrumentId INTEGER DEFAULT NULL REFERENCES sf2_instruments(id) ON DELETE CASCADE")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_modulators_instrumentId ON sf2_modulators(instrumentId)")
            }
        }

        /**
         * Migration from version 9 to 10.
         * Adds hybrid passthrough support for lossless SF2 round-trip:
         *
         * 1. New table sf2_source_metadata:
         *    - Stores path to copied SF2 source file
         *    - Stores chunk registry (offsets, sizes, hashes) as JSON
         *
         * 2. New columns in sf2_samples, sf2_presets, sf2_instruments:
         *    - originalStateHash: SHA-256 hash at import time
         *    - isModifiedByUser: Boolean flag for modifications
         *    - modificationFlags: Bitmap of modification types
         */
        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Create sf2_source_metadata table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sf2_source_metadata (
                        projectId INTEGER PRIMARY KEY NOT NULL,
                        sourceFilePath TEXT,
                        sourceFileHash TEXT,
                        chunkRegistry TEXT NOT NULL DEFAULT '{}',
                        importedAt INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (projectId) REFERENCES sf2_projects(id) ON DELETE CASCADE
                    )
                """)

                // 2. Add tracking columns to sf2_samples
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN originalStateHash TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN isModifiedByUser INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN modificationFlags INTEGER NOT NULL DEFAULT 0")

                // 3. Add tracking columns to sf2_presets
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN originalStateHash TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN isModifiedByUser INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN modificationFlags INTEGER NOT NULL DEFAULT 0")

                // 4. Add tracking columns to sf2_instruments
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN originalStateHash TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN isModifiedByUser INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN modificationFlags INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 10 to 11.
         * Adds missing global zone generators that were not previously supported:
         * - keyToModEnvHold/Decay (GEN 31/32)
         * - keyToVolEnvHold/Decay (GEN 39/40)
         * - scaleTuning (GEN 56)
         * - exclusiveClass (GEN 57)
         */
        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add to sf2_instruments (IGEN global zone)
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN globalKeyToModEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN globalKeyToModEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN globalKeyToVolEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN globalKeyToVolEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN globalScaleTuning INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_instruments ADD COLUMN globalExclusiveClass INTEGER NOT NULL DEFAULT 0")

                // 2. Add to sf2_programs (PGEN global zone)
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalKeyToModEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalKeyToModEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalKeyToVolEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalKeyToVolEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalScaleTuning INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_programs ADD COLUMN globalExclusiveClass INTEGER NOT NULL DEFAULT 0")

                // 3. Add to sf2_presets (PGEN per-zone)
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenKeyToModEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenKeyToModEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenKeyToVolEnvHold INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenKeyToVolEnvDecay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenScaleTuning INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_presets ADD COLUMN pgenExclusiveClass INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * Migration from version 11 to 12.
         * Adds sampleIndexMapping to sf2_source_metadata for hybrid passthrough export.
         * This stores the original sample header information needed to reconstruct pdta
         * when copying the smpl chunk directly from the source file.
         */
        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sampleIndexMapping column with default empty JSON object
                db.execSQL("ALTER TABLE sf2_source_metadata ADD COLUMN sampleIndexMapping TEXT NOT NULL DEFAULT '{}'")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sampleModes column to store full SF2 loop mode (0=no loop, 1=loop, 3=loop+release)
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN sampleModes INTEGER NOT NULL DEFAULT 0")
                // Set sampleModes based on existing hasLoop value (1 = loop continuously)
                db.execSQL("UPDATE sf2_samples SET sampleModes = 1 WHERE hasLoop = 1")
            }
        }

        /**
         * Migration from version 13 to 14.
         * Polyphone-like architecture:
         *
         * 1. Add sourceFilePath and isLegacyProject to sf2_projects
         * 2. Create sf2_patches table for storing modifications only
         * 3. Create sf2_index table for lightweight navigation
         * 4. Mark all existing projects as legacy
         */
        private val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add new columns to sf2_projects
                db.execSQL("ALTER TABLE sf2_projects ADD COLUMN sourceFilePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sf2_projects ADD COLUMN isLegacyProject INTEGER NOT NULL DEFAULT 0")

                // Mark all existing projects as legacy
                db.execSQL("UPDATE sf2_projects SET isLegacyProject = 1")

                // 2. Create sf2_patches table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sf2_patches (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        targetType TEXT NOT NULL,
                        targetIndex INTEGER NOT NULL,
                        patchType TEXT NOT NULL,
                        patchData TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY (projectId) REFERENCES sf2_projects(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_patches_projectId ON sf2_patches(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_patches_targetType ON sf2_patches(targetType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_patches_targetIndex ON sf2_patches(targetIndex)")

                // 3. Create sf2_index table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sf2_index (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        elementType TEXT NOT NULL,
                        originalIndex INTEGER NOT NULL,
                        name TEXT NOT NULL,
                        programNumber INTEGER DEFAULT NULL,
                        bankNumber INTEGER DEFAULT NULL,
                        rootNote INTEGER DEFAULT NULL,
                        sampleRate INTEGER DEFAULT NULL,
                        isDeleted INTEGER NOT NULL DEFAULT 0,
                        isAdded INTEGER NOT NULL DEFAULT 0,
                        FOREIGN KEY (projectId) REFERENCES sf2_projects(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_index_projectId ON sf2_index(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_index_elementType ON sf2_index(elementType)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sf2_index_originalIndex ON sf2_index(originalIndex)")
            }
        }

        /**
         * Migration from version 14 to 15.
         * Adds patch-based import support:
         * - sourceFilePath: Path to source SF2 file for imported samples
         * - sourceSmplOffset: Byte offset within source SF2's smpl chunk
         * - sourceSampleSize: Size of sample data in bytes
         * - isExtracted: Whether sample has been extracted to WAV for editing
         *
         * This enables efficient export by reading sample audio directly from
         * the source SF2 file instead of extracting to WAV files on import.
         */
        private val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add source reference columns to sf2_samples
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN sourceFilePath TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN sourceSmplOffset INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN sourceSampleSize INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sf2_samples ADD COLUMN isExtracted INTEGER NOT NULL DEFAULT 1")
                // Note: isExtracted defaults to 1 (true) for existing samples since they have WAV files
            }
        }

        private fun buildDatabase(context: Context): Sf2ProjectDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                Sf2ProjectDatabase::class.java,
                "sf2_projects.db"
            )
                .addMigrations(
                    MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5,
                    MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                    MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13,
                    MIGRATION_13_14, MIGRATION_14_15
                )
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
