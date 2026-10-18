package com.Atom2Universe.app.radio.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Base de données Room pour le module Radio
 *
 * Tables:
 * - radio_filters: Cache des pays et langues disponibles
 */
@Database(
    entities = [RadioFilterEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RadioDatabase : RoomDatabase() {

    abstract fun radioFilterDao(): RadioFilterDao

    companion object {
        @Volatile
        private var INSTANCE: RadioDatabase? = null
        private const val DATABASE_NAME = "radio_database"

        fun getInstance(context: Context): RadioDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RadioDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
