package com.Atom2Universe.app.notes

import androidx.room.Room
import com.Atom2Universe.app.notes.data.Note
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.NotesDatabase
import com.Atom2Universe.app.notes.data.Tag
import com.Atom2Universe.app.notes.export.NoteExportManager
import com.Atom2Universe.app.notes.repository.NotesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** La base des notes : corbeille, liens entre notes, et import d'une sauvegarde par les noms. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesRepositoryTest {

    private lateinit var db: NotesDatabase
    private lateinit var repo: NotesRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NotesDatabase::class.java).allowMainThreadQueries().build()
        repo = NotesRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `la corbeille cache la note sans l effacer`() = runBlocking {
        val id = repo.insertNote(Note(title = "Courses", content = "- [ ] lait"))
        repo.moveToTrash(id)
        assertTrue(repo.getAllNotesWithTags().first().isEmpty())
        assertEquals(listOf(id), repo.getTrashedNotesWithTags().first().map { it.note.id })
        repo.restoreFromTrash(id)
        assertEquals(listOf(id), repo.getAllNotesWithTags().first().map { it.note.id })
        repo.moveToTrash(id)
        repo.emptyTrash()
        assertTrue(repo.getTrashedNotesWithTags().first().isEmpty())
    }

    @Test
    fun `une note trop vieille dans la corbeille s efface`() = runBlocking {
        val id = repo.insertNote(Note(title = "Vieille"))
        db.noteDao().setDeletedAt(id, System.currentTimeMillis() - 40L * 24 * 3600_000)
        val fresh = repo.insertNote(Note(title = "Récente"))
        repo.moveToTrash(fresh)
        repo.purgeTrash(30L * 24 * 3600_000)
        assertEquals(listOf(fresh), repo.getTrashedNotesWithTags().first().map { it.note.id })
    }

    @Test
    fun `les retroliens trouvent les notes qui citent le titre`() = runBlocking {
        val target = repo.insertNote(Note(title = "Projet"))
        val citing = repo.insertNote(Note(title = "Journal", content = "voir [[projet]] demain"))
        repo.insertNote(Note(title = "Autre", content = "rien à voir"))
        val trashed = repo.insertNote(Note(title = "Jetée", content = "[[Projet]]"))
        repo.moveToTrash(trashed)
        assertEquals(listOf(citing), repo.getBacklinks("Projet", target).map { it.note.id })
    }

    @Test
    fun `l import rattache groupes et tags par leur nom`() = runBlocking {
        // Une note déjà là, avec le tag « maison » : l'import doit le réutiliser.
        val maison = repo.insertTag(Tag(name = "maison"))
        val exported = NoteExportManager.generateExportContent(
            Note(id = 99, title = "Peinture", content = "- [x] acheter", isFavorite = true, dateCreated = 1000, dateModified = 2000),
            listOf(Tag(id = 7, name = "Maison"), Tag(id = 8, name = "travaux")),
            NoteGroup(id = 5, name = "Projets"),
        )
        val parsed = NoteExportManager.parseImportContent(exported)
        repo.importBackup(emptyList(), emptyList(), listOf(NoteGroup(id = 5, name = "Projets")), listOf(parsed), replace = false)

        val note = repo.getAllNotesWithTags().first().single()
        assertEquals("Peinture", note.note.title)
        assertEquals("- [x] acheter", note.note.content)
        assertEquals("☑ acheter", note.note.contentPlainText)
        assertEquals("Projets", note.group?.name)
        assertTrue(note.note.isFavorite)
        assertEquals(1000L, note.note.dateCreated)
        assertEquals(setOf("maison", "travaux"), note.tags.map { it.name }.toSet())
        assertTrue(note.tags.any { it.id == maison })
        assertEquals(2, repo.getAllTagsList().size)
    }

    @Test
    fun `remplacer efface tout avant d importer`() = runBlocking {
        repo.insertNote(Note(title = "Ancienne"))
        val parsed = NoteExportManager.parseImportContent("# Nouvelle\n\ntexte")
        repo.importBackup(emptyList(), emptyList(), emptyList(), listOf(parsed), replace = true)
        assertEquals(listOf("Nouvelle"), repo.getAllNotesWithTags().first().map { it.note.title })
    }

    @Test
    fun `dupliquer copie le texte et les tags`() = runBlocking {
        val id = repo.insertNote(Note(title = "A", content = "x", isPinned = true))
        val tag = repo.insertTag(Tag(name = "t"))
        repo.setTagsForNote(id, listOf(tag))
        val copy = repo.duplicateNote(id, "A (copie)")!!
        val c = repo.getNoteWithTagsById(copy)!!
        assertEquals("x", c.note.content)
        assertEquals(false, c.note.isPinned)
        assertEquals(listOf(tag), c.tags.map { it.id })
    }
}
