package com.Atom2Universe.app.notes

import com.Atom2Universe.app.notes.sync.NameKind
import com.Atom2Universe.app.notes.sync.NotesMerge
import com.Atom2Universe.app.notes.sync.NotesSyncFile
import com.Atom2Universe.app.notes.sync.NotesSyncState
import com.Atom2Universe.app.notes.sync.SyncCategory
import com.Atom2Universe.app.notes.sync.SyncGroup
import com.Atom2Universe.app.notes.sync.SyncNote
import com.Atom2Universe.app.notes.sync.SyncTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La fusion des notes à trois : ici, le cloud, et ce qu'on a échangé la dernière fois. Sans Android. */
class NotesMergeTest {

    private val now = 1_000_000_000L
    private val day = 24L * 3600_000L

    private fun note(uuid: String, text: String = "texte", modified: Long = 100, group: String? = null, tags: List<String> = emptyList(), deletedAt: Long? = null) =
        SyncNote(uuid, "Titre $uuid", text, group, tags, false, false, null, "auto", 1, modified, deletedAt)

    private var counter = 0
    private fun plan(
        local: List<SyncNote> = emptyList(),
        cloud: List<SyncNote> = emptyList(),
        purged: Map<String, Long> = emptyMap(),
        state: Map<String, String> = emptyMap(),
    ) = NotesMerge.planNotes(local, cloud, purged, state, now, "(conflit)") { "copie-${++counter}" }

    // ---- L'empreinte ----------------------------------------------------------------------------

    @Test
    fun `l empreinte ignore les dates, la casse des noms et l ordre des tags`() {
        val a = note("1", group = "Travail", tags = listOf("b", "A"))
        val b = a.copy(groupName = " travail ", tags = listOf("a", "B"), dateModified = 999, dateCreated = 7)
        assertEquals(a.hash(), b.hash())
        assertNotEquals(a.hash(), a.copy(content = "autre").hash())
        assertNotEquals(a.hash(), a.copy(isPinned = true).hash())
        assertNotEquals(a.hash(), a.copy(deletedAt = 5).hash())
        assertNotEquals(a.hash(), a.copy(groupName = null).hash())
    }

    // ---- Notes ----------------------------------------------------------------------------------

    @Test
    fun `une note neuve ici est publiee, une note neuve du cloud est creee ici`() {
        val p = plan(local = listOf(note("a")), cloud = listOf(note("b")))
        assertEquals(listOf("b"), p.applyLocal.map { it.uuid })
        assertEquals(setOf("a", "b"), p.cloudNotes.map { it.uuid }.toSet())
        assertEquals(setOf("a", "b"), p.hashes.keys)
        assertNull(p.expectedLocal["b"])
    }

    @Test
    fun `seul le cote qui a bouge depuis la base gagne`() {
        val base = note("a", "v1")
        val h0 = mapOf("a" to base.hash())

        val localChanged = plan(local = listOf(note("a", "v2")), cloud = listOf(base), state = h0)
        assertTrue(localChanged.applyLocal.isEmpty())
        assertEquals("v2", localChanged.cloudNotes.single().content)

        val cloudChanged = plan(local = listOf(base), cloud = listOf(note("a", "v3")), state = h0)
        assertEquals("v3", cloudChanged.applyLocal.single().content)
        assertEquals(base.hash(), cloudChanged.expectedLocal["a"])

        val nothing = plan(local = listOf(base), cloud = listOf(base), state = h0)
        assertTrue(nothing.applyLocal.isEmpty() && nothing.newLocal.isEmpty() && nothing.deleteLocal.isEmpty())
    }

    @Test
    fun `quand les deux ont bouge la plus recente gagne et l autre devient une copie`() {
        val base = note("a", "v1", modified = 100)
        val h0 = mapOf("a" to base.hash())
        val mine = note("a", "ici", modified = 300)
        val theirs = note("a", "ailleurs", modified = 200)

        val p = plan(local = listOf(mine), cloud = listOf(theirs), state = h0)
        assertTrue(p.applyLocal.isEmpty())
        val copy = p.newLocal.single()
        assertEquals("ailleurs", copy.content)
        assertNotEquals("a", copy.uuid)
        assertTrue(copy.title.endsWith("(conflit)"))
        assertEquals(setOf("a", copy.uuid), p.cloudNotes.map { it.uuid }.toSet())
        assertEquals("ici", p.cloudNotes.first { it.uuid == "a" }.content)

        // Et dans l'autre sens : le cloud est plus récent, il s'écrit ici, et c'est notre version qui est gardée en copie.
        val q = plan(local = listOf(theirs), cloud = listOf(mine), state = h0)
        assertEquals("ici", q.applyLocal.single().content)
        assertEquals("ailleurs", q.newLocal.single().content)
    }

    @Test
    fun `une note jamais echangee et differente des deux cotes est un conflit, pas une ecrasement`() {
        val p = plan(local = listOf(note("a", "ici", modified = 5)), cloud = listOf(note("a", "la", modified = 9)))
        assertEquals("la", p.applyLocal.single().content)
        assertEquals("ici", p.newLocal.single().content)
    }

    @Test
    fun `une note absente du cloud sans pierre tombale est republiee, jamais supprimee`() {
        val a = note("a")
        val p = plan(local = listOf(a), cloud = emptyList(), state = mapOf("a" to a.hash()))
        assertTrue(p.deleteLocal.isEmpty())
        assertEquals(listOf("a"), p.cloudNotes.map { it.uuid })
    }

    @Test
    fun `supprimee ici, la suppression part dans une pierre tombale`() {
        val a = note("a")
        val p = plan(local = emptyList(), cloud = listOf(a), state = mapOf("a" to a.hash()))
        assertTrue(p.cloudNotes.isEmpty())
        assertEquals(now, p.purged["a"])
        assertTrue(p.hashes.isEmpty())
    }

    @Test
    fun `supprimee ici mais modifiee ailleurs depuis, elle revient`() {
        val base = note("a", "v1")
        val p = plan(local = emptyList(), cloud = listOf(note("a", "v2")), state = mapOf("a" to base.hash()))
        assertEquals("v2", p.applyLocal.single().content)
        assertNull(p.purged["a"])
    }

    @Test
    fun `supprimee ailleurs, elle part d ici si on n y a pas touche, sinon elle reste`() {
        val a = note("a")
        val untouched = plan(local = listOf(a), cloud = emptyList(), purged = mapOf("a" to now - 1000), state = mapOf("a" to a.hash()))
        assertEquals(listOf("a"), untouched.deleteLocal)
        assertTrue(untouched.cloudNotes.isEmpty())

        val edited = note("a", "modifiee ici")
        val kept = plan(local = listOf(edited), cloud = emptyList(), purged = mapOf("a" to now - 1000), state = mapOf("a" to a.hash()))
        assertTrue(kept.deleteLocal.isEmpty())
        assertEquals(listOf("a"), kept.cloudNotes.map { it.uuid })
        assertNull(kept.purged["a"])
    }

    @Test
    fun `les pierres tombales expirent`() {
        val p = plan(purged = mapOf("vieille" to now - 91 * day, "recente" to now - 10 * day))
        assertEquals(setOf("recente"), p.purged.keys)
    }

    // ---- Groupes, tags, catégories --------------------------------------------------------------

    private fun <T> names(
        local: Map<String, T>, cloud: Map<String, T>, removed: Map<String, Long> = emptyMap(),
        synced: Set<String> = emptySet(), newer: (T, T) -> Boolean = { _, _ -> false },
    ) = NotesMerge.planNames(local, cloud, removed, synced, now, newer)

    @Test
    fun `un nom neuf d un cote est cree de l autre`() {
        val p = names(local = mapOf("a" to "A"), cloud = mapOf("b" to "B"))
        assertEquals(listOf("B"), p.createLocal)
        assertEquals(setOf("A", "B"), p.cloud.toSet())
        assertEquals(setOf("a", "b"), p.synced)
    }

    @Test
    fun `un nom supprime ici est supprime du cloud et ne revient pas`() {
        val p = names(local = emptyMap(), cloud = mapOf("a" to "A"), synced = setOf("a"))
        assertTrue(p.cloud.isEmpty())
        assertEquals(now, p.removed["a"])
        assertTrue(p.createLocal.isEmpty())
        assertTrue(p.synced.isEmpty())
    }

    @Test
    fun `un nom supprime ailleurs est supprime ici, sauf s il est ne ici depuis`() {
        val gone = names(local = mapOf("a" to "A"), cloud = emptyMap(), removed = mapOf("a" to now - 5), synced = setOf("a"))
        assertEquals(setOf("a"), gone.deleteLocal)
        assertTrue(gone.cloud.isEmpty())

        val reborn = names(local = mapOf("a" to "A"), cloud = emptyMap(), removed = mapOf("a" to now - 5), synced = emptySet())
        assertTrue(reborn.deleteLocal.isEmpty())
        assertEquals(listOf("A"), reborn.cloud)
        assertNull(reborn.removed["a"])
    }

    @Test
    fun `un groupe plus recent du cloud remplace le notre, jamais l inverse sur un tag`() {
        val older = SyncGroup("g", "", "#111", "auto", null, 0, 10)
        val newer = SyncGroup("g", "", "#222", "auto", null, 0, 20)
        val g = names(local = mapOf("g" to older), cloud = mapOf("g" to newer), synced = setOf("g")) { mine, theirs -> theirs.dateModified > mine.dateModified }
        assertEquals(listOf(newer), g.updateLocal)
        assertEquals(listOf(newer), g.cloud)

        val t1 = SyncTag("t", "#111", 0, "auto", null)
        val t2 = SyncTag("t", "#222", 0, "auto", null)
        val t = names(local = mapOf("t" to t1), cloud = mapOf("t" to t2), synced = setOf("t"))
        assertTrue(t.updateLocal.isEmpty())
        assertEquals(listOf(t1), t.cloud)
    }

    // ---- Le fichier ------------------------------------------------------------------------------

    @Test
    fun `le fichier et l etat font l aller-retour`() {
        val file = NotesSyncFile(
            NotesSyncFile.FORMAT, 5, "Tablette",
            listOf(note("a", "été \"cité\"\nligne 2", group = "G", tags = listOf("x", "y"))),
            listOf(SyncGroup("G", "d", "#fff", "dark", "star", 2, 9)),
            listOf(SyncTag("x", null, 1, "auto", "C")), listOf(SyncCategory("C", 3)),
            mapOf("u" to 7L), mapOf("g:vieux" to 8L),
        )
        val back = NotesSyncFile.fromJson(file.toJson())!!
        assertEquals(file.contentOnly(), back.contentOnly())
        assertEquals("Tablette", back.deviceName)
        assertEquals(file.notes, back.notes)
        assertEquals(file.groups, back.groups)
        assertEquals(file.removed, back.removed)
        assertNull(NotesSyncFile.fromJson("pas du json"))
        assertNull(NotesSyncFile.fromJson("{\"notes\":[]}"))

        val state = NotesSyncState(mapOf("a" to "ff"), setOf("g"), setOf("t"), setOf("c"))
        val sb = NotesSyncState.fromJson(state.toJson())
        assertEquals(state.notes, sb.notes)
        assertEquals(setOf("t"), sb.names(NameKind.TAG))
        assertTrue(NotesSyncState.fromJson("???").notes.isEmpty())
        assertTrue(NotesSyncState.fromJson(null).notes.isEmpty())
    }

    @Test
    fun `deux fichiers qui disent la meme chose ont le meme contenu, qui les a ecrits ne compte pas`() {
        val a = NotesSyncFile(1, 5, "A", listOf(note("1"), note("2")), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())
        val b = NotesSyncFile(1, 99, "B", listOf(note("2"), note("1")), emptyList(), emptyList(), emptyList(), emptyMap(), emptyMap())
        assertEquals(a.contentOnly(), b.contentOnly())
        assertNotEquals(a.toJson(), b.toJson())
        assertNotNull(a)
        assertFalse(a.contentOnly().contains("\"device\""))
    }
}
