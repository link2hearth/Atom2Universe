package com.Atom2Universe.app.sf2creator.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2InstrumentEntity
import com.Atom2Universe.app.sf2creator.data.db.entities.Sf2ModulatorEntity
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
 * Une base plus ancienne que la v16 est simplement recréée (un seul testeur, pas de conversion).
 */
@Database(
    entities = [
        Sf2ProjectEntity::class,
        Sf2ProgramEntity::class,
        Sf2PresetEntity::class,
        Sf2InstrumentEntity::class,
        Sf2SampleEntity::class,
        Sf2ModulatorEntity::class,
        Sf2SourceMetadataEntity::class
    ],
    version = 17,
    exportSchema = false
)
abstract class Sf2ProjectDatabase : RoomDatabase() {

    abstract fun projectDao(): Sf2ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: Sf2ProjectDatabase? = null

        fun getInstance(context: Context): Sf2ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        /** v17 : les tables de l'ancienne architecture « patchs » ne servent plus. Les projets sont gardés. */
        private val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS sf2_patches")
                db.execSQL("DROP TABLE IF EXISTS sf2_index")
            }
        }

        private fun buildDatabase(context: Context): Sf2ProjectDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                Sf2ProjectDatabase::class.java,
                "sf2_projects.db"
            )
                .addMigrations(MIGRATION_16_17)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
    }
}
