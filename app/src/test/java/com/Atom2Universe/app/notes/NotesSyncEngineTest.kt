package com.Atom2Universe.app.notes

import androidx.room.Room
import com.Atom2Universe.app.notes.data.Note
import com.Atom2Universe.app.notes.data.NoteGroup
import com.Atom2Universe.app.notes.data.NotesDatabase
import com.Atom2Universe.app.notes.data.Tag
import com.Atom2Universe.app.notes.repository.NotesRepository
import com.Atom2Universe.app.notes.sync.ApplyJob
import com.Atom2Universe.app.notes.sync.NotesCloud
import com.Atom2Universe.app.notes.sync.NotesCloudRead
import com.Atom2Universe.app.notes.sync.NotesMerge
import com.Atom2Universe.app.notes.sync.NotesStateStore
import com.Atom2Universe.app.notes.sync.NotesSyncEngine
import com.Atom2Universe.app.notes.sync.NotesSyncState
import com.Atom2Universe.app.notes.sync.NotesSyncStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Deux appareils (deux bases, deux états) qui se parlent par un faux cloud : ce que l'un écrit arrive chez
 * l'autre, rien ne se perd quand les deux écrivent, et un envoi raté n'écrase jamais rien.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotesSyncEngineTest {

    private class FakeCloud : NotesCloud {
        var text: String? = null
        var failRead = false
        var failWrite = false
        var writes = 0
        override suspend fun read(): NotesCloudRead = when {
            failRead -> NotesCloudRead.Failed
            text == null -> NotesCloudRead.Missing
            else -> NotesCloudRead.Found(text!!)
        }
        override suspend fun write(text: String): Boolean {
            if (failWrite) return false
            this.text = text; writes++
            return true
        }
    }

    private class MemState : NotesStateStore {
        var state = NotesSyncState.EMPTY
        override fun load() = state
        override fun save(state: NotesSyncState) { this.state = state }
    }

    private inner class Device(val name: String) {
        val db: NotesDatabase = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), NotesDatabase::class.java).allowMainThreadQueries().build()
        val repo = NotesRepository(db)
        val state = MemState()
        val store = NotesSyncStore(db)
        private var seq = 0
        fun engine(cloud: NotesCloud) = NotesSyncEngine(store, cloud, state, name, "(conflit)", now = { clock }, newUuid = { "$name-conflit-${++seq}" })
        fun sync(cloud: FakeCloud): NotesSyncEngine.Result = runBlocking { engine(cloud).sync() }

        fun add(title: String, content: String = "", group: String? = null, tags: List<String> = emptyList(), modified: Long = clock): Long = runBlocking {
            val groupId = group?.let { g -> db.noteGroupDao().getAllGroupsForBackup().firstOrNull { it.name.equals(g, true) }?.id ?: repo.insertGroup(NoteGroup(name = g)) }
            val id = repo.insertNote(Note(title = title, content = content, contentPlainText = content, groupId = groupId, dateCreated = modified, dateModified = modified))
            val tagIds = tags.map { t -> repo.getTagByName(t)?.id ?: repo.insertTag(Tag(name = t)) }
            repo.setTagsForNote(id, tagIds)
            id
        }

        fun edit(id: Long, content: String, modified: Long = clock) = runBlocking {
            repo.updateNote(repo.getNoteById(id)!!.copy(content = content, contentPlainText = content, dateModified = modified))
        }

        fun titles(): List<String> = runBlocking { db.noteDao().getAllNotesForSync().map { it.title }.sorted() }
        fun byTitle(title: String): Note = runBlocking { db.noteDao().getAllNotesForSync().first { it.title == title } }
        fun contentOf(title: String): String = byTitle(title).content
        fun groupOf(title: String): String? = runBlocking { byTitle(title).groupId?.let { db.noteGroupDao().getGroupById(it)?.name } }
        fun tagsOf(title: String): List<String> = runBlocking { repo.getNoteWithTagsById(byTitle(title).id)!!.tags.map { it.name }.sorted() }
        fun groups(): List<String> = runBlocking { db.noteGroupDao().getAllGroupsForBackup().map { it.name }.sorted() }
        fun allTags(): List<String> = runBlocking { db.tagDao().getAllTagsForBackup().map { it.name }.sorted() }
    }

    private var clock = 1_000L
    private lateinit var a: Device
    private lateinit var b: Device
    private lateinit var cloud: FakeCloud

    @Before
    fun setUp() {
        clock = 1_000L
        a = Device("A"); b = Device("B"); cloud = FakeCloud()
    }

    @After
    fun tearDown() { a.db.close(); b.db.close() }

    private fun tick(): Long { clock += 1_000; return clock }

    @Test
    fun `les notes d un appareil arrivent sur l autre avec leur groupe et leurs tags`() {
        a.add("Courses", "- [ ] lait", group = "Maison", tags = listOf("urgent", "liste"))
        a.add("Idée", "un **texte**")
        assertTrue(a.sync(cloud) is NotesSyncEngine.Result.Done)

        val r = b.sync(cloud) as NotesSyncEngine.Result.Done
        assertTrue(r.changedLocal)
        assertEquals(listOf("Courses", "Idée"), b.titles())
        assertEquals("- [ ] lait", b.contentOf("Courses"))
        assertEquals("Maison", b.groupOf("Courses"))
        assertEquals(listOf("liste", "urgent"), b.tagsOf("Courses"))
        assertNull(b.groupOf("Idée"))
        assertEquals(a.byTitle("Courses").uuid, b.byTitle("Courses").uuid)
        assertEquals("un **texte**", b.contentOf("Idée"))
        // Le texte brut de recherche est recalculé ici, pas copié.
        assertFalse(b.byTitle("Idée").contentPlainText.contains("**"))
    }

    @Test
    fun `une modification passe d un appareil a l autre, dans les deux sens`() {
        val id = a.add("Note", "v1")
        a.sync(cloud); b.sync(cloud)

        b.edit(b.byTitle("Note").id, "v2 de B", tick())
        b.sync(cloud)
        a.sync(cloud)
        assertEquals("v2 de B", a.contentOf("Note"))

        a.edit(id, "v3 de A", tick())
        a.sync(cloud); b.sync(cloud)
        assertEquals("v3 de A", b.contentOf("Note"))
        assertEquals(listOf("Note"), a.titles())
        assertEquals(listOf("Note"), b.titles())
    }

    @Test
    fun `quand les deux ecrivent, rien n est perdu - la plus recente reste et l autre devient une copie`() {
        val id = a.add("Note", "v1")
        a.sync(cloud); b.sync(cloud)

        a.edit(id, "texte de A", tick())
        b.edit(b.byTitle("Note").id, "texte de B", tick())
        a.sync(cloud)           // le cloud porte A
        b.sync(cloud)           // B est plus récent : il gagne, A est gardé en copie
        a.sync(cloud)

        for (d in listOf(a, b)) {
            val titles = d.titles()
            assertEquals(2, titles.size)
            assertEquals("texte de B", d.contentOf("Note"))
            assertEquals("texte de A", d.contentOf(titles.first { it.contains("(conflit)") }))
        }
        // Et la situation est stable : une sync de plus ne fait plus rien.
        val writes = cloud.writes
        assertEquals(NotesSyncEngine.Result.Done(changedLocal = false, uploaded = false), a.sync(cloud))
        assertEquals(NotesSyncEngine.Result.Done(changedLocal = false, uploaded = false), b.sync(cloud))
        assertEquals(writes, cloud.writes)
    }

    @Test
    fun `une note purgee ici disparait aussi de l autre et ne revient pas`() {
        val id = a.add("A jeter", "x")
        a.add("A garder", "y")
        a.sync(cloud); b.sync(cloud)
        assertEquals(2, b.titles().size)

        runBlocking { a.repo.deleteNoteForever(id) }
        a.sync(cloud)
        b.sync(cloud)
        assertEquals(listOf("A garder"), b.titles())
        // Les syncs suivantes, dans n'importe quel ordre, ne la ressuscitent pas.
        a.sync(cloud); b.sync(cloud); a.sync(cloud)
        assertEquals(listOf("A garder"), a.titles())
        assertEquals(listOf("A garder"), b.titles())
    }

    @Test
    fun `la corbeille voyage aussi`() {
        val id = a.add("Vieille", "x")
        a.sync(cloud); b.sync(cloud)
        runBlocking { a.repo.moveToTrash(id) }
        a.sync(cloud); b.sync(cloud)
        assertNotNull(b.byTitle("Vieille").deletedAt)
        runBlocking { b.repo.restoreFromTrash(b.byTitle("Vieille").id) }
        b.sync(cloud); a.sync(cloud)
        assertNull(a.byTitle("Vieille").deletedAt)
    }

    @Test
    fun `un groupe supprime ici est supprime de l autre sans revenir`() {
        a.add("Note", "x", group = "Travail", tags = listOf("t1"))
        a.sync(cloud); b.sync(cloud)
        assertEquals(listOf("Travail"), b.groups())

        val group = runBlocking { a.db.noteGroupDao().getAllGroupsForBackup().single() }
        val tag = runBlocking { a.db.tagDao().getAllTagsForBackup().single() }
        runBlocking { a.repo.deleteGroup(group); a.repo.deleteTag(tag) }
        a.sync(cloud); b.sync(cloud); a.sync(cloud); b.sync(cloud)

        assertTrue(a.groups().isEmpty() && b.groups().isEmpty())
        assertTrue(a.allTags().isEmpty() && b.allTags().isEmpty())
        // Les notes, elles, sont intactes et sans groupe des deux côtés.
        assertNull(a.groupOf("Note")); assertNull(b.groupOf("Note"))
        assertEquals(emptyList<String>(), b.tagsOf("Note"))
    }

    @Test
    fun `un envoi rate n ecrase rien - la modification d ici repart au tour suivant`() {
        val id = a.add("Note", "v1")
        a.sync(cloud); b.sync(cloud)

        a.edit(id, "v2", tick())
        cloud.failWrite = true
        assertEquals(NotesSyncEngine.Result.Failed, a.sync(cloud))
        assertEquals("v2", a.contentOf("Note"))

        cloud.failWrite = false
        a.sync(cloud)
        assertEquals("v2", a.contentOf("Note"))
        b.sync(cloud)
        assertEquals("v2", b.contentOf("Note"))
    }

    @Test
    fun `un cloud qui ne repond pas ne change rien, ni ici ni la-haut`() {
        a.add("Note", "v1")
        a.sync(cloud)
        val before = cloud.text
        cloud.failRead = true
        assertEquals(NotesSyncEngine.Result.Failed, a.sync(cloud))
        assertEquals(NotesSyncEngine.Result.Failed, b.sync(cloud))
        assertEquals(before, cloud.text)
        assertTrue(b.titles().isEmpty())
    }

    @Test
    fun `un fichier plus recent que l application ou illisible n est jamais ecrase`() {
        a.add("Note", "v1")
        cloud.text = "{\"format\":99,\"notes\":[]}"
        assertEquals(NotesSyncEngine.Result.TooNew, a.sync(cloud))
        assertEquals("{\"format\":99,\"notes\":[]}", cloud.text)
        cloud.text = "pas du json"
        assertEquals(NotesSyncEngine.Result.Failed, a.sync(cloud))
        assertEquals("pas du json", cloud.text)
    }

    @Test
    fun `une sync sans changement n ecrit rien sur le cloud`() {
        a.add("Note", "v1")
        a.sync(cloud)
        val writes = cloud.writes
        a.sync(cloud); b.sync(cloud); a.sync(cloud)
        assertEquals(writes, cloud.writes)
    }

    @Test
    fun `une note modifiee pendant la sync n est pas ecrasee par la version du cloud`() = runBlocking {
        val id = a.add("Note", "v1")
        a.sync(cloud); b.sync(cloud)
        b.edit(b.byTitle("Note").id, "v2 de B", tick())
        b.sync(cloud)

        // A prend sa photo et fait son plan (le cloud porte v2)...
        val snap = a.store.snapshot()
        val remote = com.Atom2Universe.app.notes.sync.NotesSyncFile.fromJson(cloud.text!!)!!
        val plan = NotesMerge.planNotes(snap.notes, remote.notes, remote.purged, a.state.state.notes, clock, "(conflit)") { "x" }
        assertEquals(1, plan.applyLocal.size)
        // ...mais pendant ce temps, on tape dans la note.
        a.edit(id, "frappe en cours", tick())
        val empty = NotesMerge.planNames<com.Atom2Universe.app.notes.sync.SyncGroup>(emptyMap(), emptyMap(), emptyMap(), emptySet(), clock) { _, _ -> false }
        val emptyT = NotesMerge.planNames<com.Atom2Universe.app.notes.sync.SyncTag>(emptyMap(), emptyMap(), emptyMap(), emptySet(), clock) { _, _ -> false }
        val emptyC = NotesMerge.planNames<com.Atom2Universe.app.notes.sync.SyncCategory>(emptyMap(), emptyMap(), emptyMap(), emptySet(), clock) { _, _ -> false }
        val skipped = a.store.apply(ApplyJob(plan, empty, emptyT, emptyC, emptySet()))

        assertEquals(setOf(a.byTitle("Note").uuid), skipped)
        assertEquals("frappe en cours", a.contentOf("Note"))
        // Au tour suivant, la frappe et la version de B se rencontrent : conflit, donc copie, donc rien de perdu.
        a.sync(cloud)
        assertTrue(a.titles().size == 2)
    }

    @Test
    fun `une base d avant la sync recoit des identites au premier tour`() {
        a.add("Ancienne", "x")
        runBlocking {
            val n = a.db.noteDao().getAllNotesForSync().single()
            a.db.noteDao().updateNote(n.copy(uuid = ""))
        }
        a.sync(cloud)
        val uuid = a.byTitle("Ancienne").uuid
        assertTrue(uuid.isNotBlank())
        b.sync(cloud)
        assertEquals(uuid, b.byTitle("Ancienne").uuid)
    }
}
