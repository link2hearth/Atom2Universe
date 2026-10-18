package com.Atom2Universe.app.comics

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ComicEntry::class, ComicsRootLibrary::class],
    version = 1,
    exportSchema = false
)
abstract class ComicsDatabase : RoomDatabase() {

    abstract fun comicsDao(): ComicsDao

    companion object {
        @Volatile
        private var INSTANCE: ComicsDatabase? = null

        fun getInstance(context: Context): ComicsDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ComicsDatabase::class.java,
                    "comics_database"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
