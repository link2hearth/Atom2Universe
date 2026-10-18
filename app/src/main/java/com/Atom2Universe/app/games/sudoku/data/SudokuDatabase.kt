package com.Atom2Universe.app.games.sudoku.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room database for Sudoku game saves.
 */
@Database(
    entities = [SudokuSaveEntity::class],
    version = 2,
    exportSchema = false
)
abstract class SudokuDatabase : RoomDatabase() {

    abstract fun sudokuDao(): SudokuDao

    companion object {
        @Volatile
        private var INSTANCE: SudokuDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sudoku_saves ADD COLUMN notesJson TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getInstance(context: Context): SudokuDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SudokuDatabase::class.java,
                    "sudoku_database"
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
