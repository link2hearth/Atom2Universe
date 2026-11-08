package com.Atom2Universe.app.studio

import com.Atom2Universe.app.studio.data.StudioLibrary
import com.Atom2Universe.app.studio.data.StudioProject
import com.Atom2Universe.app.studio.engine.Inst
import com.Atom2Universe.app.studio.music.Styles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** La bibliothèque des morceaux et la qualité des styles de départ. */
class StudioLibraryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun library() = StudioLibrary(File(tmp.root, "songs"))

    @Test
    fun startsEmpty() {
        assertTrue(library().list().isEmpty())
    }

    @Test
    fun createdSongsComeBackWithTheirName() {
        val lib = library()
        val e = lib.create("Mon beat", StudioProject.fromStyle(Styles.TECHNO), now = 1000L)
        val back = lib.load(e.id)
        assertNotNull(back)
        assertEquals("Mon beat", back!!.name)
        assertEquals("techno", back.project.styleId)
        assertEquals(1000L, back.created)
        assertEquals(1, lib.list().size)
    }

    @Test
    fun theMostRecentSongComesFirst() {
        val lib = library()
        val a = lib.create("A", StudioProject.blank(), now = 1000L)
        val b = lib.create("B", StudioProject.blank(), now = 2000L)
        assertEquals(listOf(b.id, a.id), lib.list().map { it.id })
        lib.save(a.id, StudioProject.blank(), now = 3000L)
        assertEquals(listOf(a.id, b.id), lib.list().map { it.id })
    }

    @Test
    fun savingKeepsTheNameAndTheCreationDate() {
        val lib = library()
        val e = lib.create("Nom", StudioProject.blank(), now = 10L)
        val changed = StudioProject.fromStyle(Styles.HIPHOP)
        assertTrue(lib.save(e.id, changed, now = 50L))
        val back = lib.load(e.id)!!
        assertEquals("Nom", back.name)
        assertEquals(10L, back.created)
        assertEquals(50L, back.modified)
        assertEquals("hiphop", back.project.styleId)
    }

    @Test
    fun savingAMissingSongDoesNothing() {
        assertFalse(library().save("pinconnu", StudioProject.blank()))
    }

    @Test
    fun renameDuplicateAndDelete() {
        val lib = library()
        val e = lib.create("Premier", StudioProject.fromStyle(Styles.LOFI), now = 1L)
        lib.rename(e.id, "Deuxième")
        assertEquals("Deuxième", lib.load(e.id)!!.name)
        val copy = lib.duplicate(e.id, "Deuxième (copie)", now = 5L)!!
        assertNotEquals(e.id, copy.id)
        assertEquals("lofi", lib.load(copy.id)!!.project.styleId)
        assertEquals(2, lib.list().size)
        lib.delete(e.id)
        assertNull(lib.load(e.id))
        assertEquals(listOf(copy.id), lib.list().map { it.id })
    }

    @Test
    fun aBrokenFileIsIgnored() {
        val lib = library()
        val ok = lib.create("Bon", StudioProject.blank())
        File(tmp.root, "songs/cassé.json").writeText("{ pas du json")
        File(tmp.root, "songs/vide.json").writeText("")
        assertEquals(listOf(ok.id), lib.list().map { it.id })
    }

    @Test
    fun blankProjectHasNoSoundAtAll() {
        val p = StudioProject.blank()
        assertEquals(Inst.COUNT, p.tracks.size)
        for (pattern in p.patterns) {
            assertTrue(pattern.notes.isEmpty())
            assertTrue(pattern.lanes.all { l -> l.velocity.all { it == 0f } })
        }
    }

    // ---- Les styles de départ ------------------------------------------------------------------------------

    @Test
    fun everyGridIsSixteenStepsLong() {
        for (s in Styles.ALL) {
            assertEquals("${s.id} synthé", 16, s.synthSteps.length)
            for ((inst, grid) in s.drums) assertEquals("${s.id} piste $inst", 16, grid.length)
        }
    }

    @Test
    fun houseKeepsAllFourBeatsOnTheKick() {
        val p = Styles.generate(Styles.HOUSE, 16, 1234L)
        val kick = p.lane(Inst.KICK)!!.velocity
        for (s in listOf(0, 4, 8, 12)) assertTrue("temps $s", kick[s] > 0f)
        // jamais de kick entre les temps
        for (s in 0 until 16) if (s % 4 != 0) assertEquals(0f, kick[s], 0f)
    }

    @Test
    fun randomPatternsStayInTheStyle() {
        for (style in Styles.ALL) for (seed in 1L..40L) {
            val p = Styles.generate(style, 16, seed)
            val hat = p.lane(Inst.HAT)!!.velocity
            val open = p.lane(Inst.OPEN_HAT)!!.velocity
            val snare = p.lane(Inst.SNARE)!!.velocity
            val clap = p.lane(Inst.CLAP)!!.velocity
            for (s in 0 until 16) {
                assertFalse("${style.id} $seed : les deux charlestons au pas $s", hat[s] > 0f && open[s] > 0f)
                if (!style.layerClap) assertFalse("${style.id} $seed : caisse claire et clap au pas $s", snare[s] > 0f && clap[s] > 0f)
            }
            // Le kick des styles à quatre temps ne manque jamais ; chaque style a un kick audible
            assertTrue("${style.id} $seed : pas de kick", p.lane(Inst.KICK)!!.velocity.any { it > 0f })
        }
    }

    @Test
    fun sameSeedGivesTheSamePatternAndAnotherSeedAnotherOne() {
        for (style in Styles.ALL) {
            val a = Styles.generate(style, 16, 7L)
            val b = Styles.generate(style, 16, 7L)
            for (i in a.lanes.indices) assertTrue(a.lanes[i].velocity.contentEquals(b.lanes[i].velocity))
            assertEquals(a.notes.map { it.pitch to it.step }, b.notes.map { it.pitch to it.step })
        }
        val x = Styles.generate(Styles.TECHNO, 16, 1L)
        val y = Styles.generate(Styles.TECHNO, 16, 2L)
        val same = x.lanes.indices.all { x.lanes[it].velocity.contentEquals(y.lanes[it].velocity) }
        assertFalse(same)
    }

    @Test
    fun bassLinesFollowTheirMotifWhateverTheSeed() {
        // Les notes jouées se suivent dans l'ordre du motif : seule la place des notes change avec la graine
        val style = Styles.HOUSE
        val degrees = intArrayOf(0, 0, 2, 0, 3)
        val scale = com.Atom2Universe.app.studio.music.Scales.byId(style.scaleId)
        for (seed in 1L..20L) {
            val notes = Styles.generate(style, 16, seed).notes.sortedBy { it.step }
            for ((k, n) in notes.withIndex()) {
                val d = degrees[k % degrees.size]
                assertEquals("graine $seed note $k", style.synthRoot + scale.intervals[d], n.pitch)
            }
        }
    }

    @Test
    fun progressionsMoveTheChordEveryEightSteps() {
        val p = Styles.generate(Styles.AMBIENT, 16, 3L)
        val first = p.notes.filter { it.step < 8 }.map { it.pitch }
        val second = p.notes.filter { it.step >= 8 }.map { it.pitch }
        if (first.isNotEmpty() && second.isNotEmpty()) {
            assertEquals(Styles.AMBIENT.synthRoot, first.min())
            assertEquals(Styles.AMBIENT.synthRoot - 4, second.min())
        }
    }

    @Test
    fun trapIsAvailable() {
        assertTrue(Styles.ALL.any { it.id == "trap" })
        assertEquals("trap", Styles.byId("trap").id)
    }
}
