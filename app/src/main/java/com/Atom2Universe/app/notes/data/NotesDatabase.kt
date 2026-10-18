package com.Atom2Universe.app.notes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Note::class,
        NoteGroup::class,
        Tag::class,
        TagCategory::class,
        NoteTag::class
    ],
    version = 3,
    exportSchema = false
)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun noteDao(): NoteDao
    abstract fun noteGroupDao(): NoteGroupDao
    abstract fun tagDao(): TagDao
    abstract fun tagCategoryDao(): TagCategoryDao

    companion object {
        @Volatile
        private var INSTANCE: NotesDatabase? = null

        /**
         * Version 3 : chaque note reçoit son identité (uuid) pour la synchronisation. Les notes qui existent
         * déjà en reçoivent une chacune, tirée au hasard par SQLite. Sans cette migration, le
         * `fallbackToDestructiveMigration` ci-dessous effacerait toutes les notes.
         */
        internal val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE notes SET uuid = lower(hex(randomblob(16)))")
            }
        }

        fun getInstance(context: Context): NotesDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NotesDatabase::class.java,
                    "a2u_notes.db"
                )
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
