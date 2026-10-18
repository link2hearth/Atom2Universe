package com.Atom2Universe.app.games.particules.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ParticulesMetaEntity::class],
    version = 3,
    exportSchema = false
)
abstract class ParticulesDatabase : RoomDatabase() {

    abstract fun metaDao(): ParticulesMetaDao

    companion object {
        @Volatile
        private var INSTANCE: ParticulesDatabase? = null

        fun getInstance(context: Context): ParticulesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ParticulesDatabase::class.java,
                    "particules_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
