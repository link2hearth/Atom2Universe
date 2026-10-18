package com.Atom2Universe.app.readingprogress.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ReadingProgressEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ReadingProgressDatabase : RoomDatabase() {

    abstract fun readingProgressDao(): ReadingProgressDao

    companion object {
        @Volatile
        private var INSTANCE: ReadingProgressDatabase? = null
        private const val DATABASE_NAME = "reading_progress_database"

        fun getInstance(context: Context): ReadingProgressDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ReadingProgressDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
