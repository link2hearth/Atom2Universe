package com.Atom2Universe.app.notes

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.Atom2Universe.app.notes.data.NotesDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Les notes d'avant la sync doivent survivre à l'arrivée de leur identité : `fallbackToDestructiveMigration`
 * est actif sur cette base, donc une migration oubliée ou fausse effacerait tout sans un mot.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesMigrationTest {

    @Test
    fun `les notes d une base en version 2 gardent leur texte et recoivent chacune une identite`() {
        val context = RuntimeEnvironment.getApplication()
        val name = "migration_test.db"
        context.deleteDatabase(name)

        // 1. Une base toute neuve, rendue à la forme de la version 2 (sans identité de note).
        Room.databaseBuilder(context, NotesDatabase::class.java, name).allowMainThreadQueries().build().apply {
            openHelper.writableDatabase.close()
            close()
        }
        val path = context.getDatabasePath(name).path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            // Le SQLite des tests ne sait pas retirer une colonne : on refait la table telle qu'elle était en version 2.
            db.execSQL(
                "CREATE TABLE notes_v2 (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `groupId` INTEGER, `title` TEXT NOT NULL, " +
                    "`content` TEXT NOT NULL, `contentPlainText` TEXT NOT NULL, `isPinned` INTEGER NOT NULL, `isFavorite` INTEGER NOT NULL, " +
                    "`colorHex` TEXT, `textColorMode` TEXT NOT NULL, `dateCreated` INTEGER NOT NULL, `dateModified` INTEGER NOT NULL, " +
                    "`deletedAt` INTEGER, FOREIGN KEY(`groupId`) REFERENCES `note_groups`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL("DROP TABLE notes")
            db.execSQL("ALTER TABLE notes_v2 RENAME TO notes")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_notes_groupId` ON `notes` (`groupId`)")
            for ((i, t) in listOf("Courses", "Idées", "Journal").withIndex()) {
                db.execSQL(
                    "INSERT INTO notes (title, content, contentPlainText, isPinned, isFavorite, textColorMode, dateCreated, dateModified) " +
                        "VALUES ('$t', 'contenu $i', 'contenu $i', 0, 0, 'auto', ${100 + i}, ${200 + i})",
                )
            }
            db.version = 2
        }

        // 2. On la rouvre avec la version actuelle : la migration doit tourner, pas la destruction.
        val migrated = Room.databaseBuilder(context, NotesDatabase::class.java, name)
            .addMigrations(NotesDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val notes = runBlocking { migrated.noteDao().getAllNotesForSync() }
            assertEquals(listOf("Courses", "Idées", "Journal"), notes.map { it.title }.sorted())
            assertEquals(listOf("contenu 0", "contenu 1", "contenu 2"), notes.map { it.content }.sorted())
            assertEquals(3, notes.map { it.uuid }.toSet().size)
            assertTrue(notes.all { it.uuid.length == 32 })
        } finally {
            migrated.close()
            context.deleteDatabase(name)
        }
    }
}
