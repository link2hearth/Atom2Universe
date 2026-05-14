package com.Atom2Universe.app.games.solitaire.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SolitaireSaveEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SolitaireDatabase : RoomDatabase() {

    abstract fun solitaireDao(): SolitaireDao

    companion object {
        @Volatile
        private var INSTANCE: SolitaireDatabase? = null

        fun getInstance(context: Context): SolitaireDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SolitaireDatabase::class.java,
                    "solitaire_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
