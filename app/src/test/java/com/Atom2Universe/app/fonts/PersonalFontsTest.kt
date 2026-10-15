package com.Atom2Universe.app.fonts

import android.app.Application
import android.graphics.Typeface
import com.Atom2Universe.app.pixelart.canvas.FontManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PersonalFontsTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private fun fixture(): File {
        // Optional local pack, never copied into test resources or bundled in the APK by this test.
        val font = File("src/main/assets/fonts/Atkinson Hyperlegible.ttf")
        assumeTrue("A local TTF is needed to exercise a real import", font.isFile)
        return font
    }

    @Before fun reset() = runBlocking { PersonalFonts.clear(context) }
    @After fun cleanup() = runBlocking { PersonalFonts.clear(context) }

    @Test fun importedCopyIsReadableEvenWhenTheOriginalIsGone() = runBlocking {
        val source = File(context.cacheDir, "Original.ttf")
        fixture().copyTo(source, overwrite = true)
        assertEquals(PersonalFonts.ImportStatus.ADDED, PersonalFonts.importStream(context, source.name) { source.inputStream() })
        assertTrue(source.delete())
        val entry = PersonalFonts.list(context).single()
        assertEquals("Original", entry.label)
        assertNotNull(PersonalFonts.loadTypeface(context, entry.id))
        // Rebuild the consumer's cache from storage, as when reopening the canvas.
        FontManager.init(context)
        assertTrue(FontManager.getAvailableFonts().any { it.first == entry.id })
        assertNotNull(FontManager.getTypeface(context, entry.id))
    }

    @Test fun identicalFontIsNotDuplicatedWhenRenamed() = runBlocking {
        val source = fixture()
        assertEquals(PersonalFonts.ImportStatus.ADDED, PersonalFonts.importStream(context, "One.TTF") { source.inputStream() })
        val entry = PersonalFonts.list(context).single()
        assertEquals(PersonalFonts.ImportStatus.EXISTING, PersonalFonts.importStream(context, "Renamed.ttf") { source.inputStream() })
        assertEquals(listOf(entry), PersonalFonts.list(context))
    }

    @Test fun invalidFilesAreNotPublishedAndLeaveNoTemporaryCopy() = runBlocking {
        assertEquals(PersonalFonts.ImportStatus.SKIPPED,
            PersonalFonts.importStream(context, "Invalid.ttf") { ByteArrayInputStream(byteArrayOf(1, 2, 3)) })
        assertEquals(PersonalFonts.ImportStatus.SKIPPED,
            PersonalFonts.importStream(context, "NotAFont.txt") { error("Unsupported files must not be opened") })
        assertTrue(PersonalFonts.list(context).isEmpty())
        assertTrue(File(context.noBackupFilesDir, "personal_fonts").listFiles().orEmpty().isEmpty())
    }

    @Test fun failedImportPreservesTheExistingLibrary() = runBlocking {
        val source = fixture()
        PersonalFonts.importStream(context, source.name) { source.inputStream() }
        val previous = PersonalFonts.list(context)
        assertEquals(PersonalFonts.ImportStatus.SKIPPED,
            PersonalFonts.importStream(context, "Inaccessible.otf") { throw IOException("Provider unavailable") })
        assertEquals(previous, PersonalFonts.list(context))
        assertTrue(File(context.noBackupFilesDir, "personal_fonts").listFiles().orEmpty().none { it.extension == "part" })
    }

    @Test fun removingCopiesKeepsTheOriginalAndFallsBackToSystemFonts() = runBlocking {
        val source = fixture()
        PersonalFonts.importStream(context, source.name) { source.inputStream() }
        val entry = PersonalFonts.list(context).single()
        FontManager.init(context)
        FontManager.getTypeface(context, entry.id)
        PersonalFonts.clear(context)
        assertTrue(source.isFile)
        assertTrue(PersonalFonts.list(context).isEmpty())
        assertNull(PersonalFonts.loadTypeface(context, entry.id))
        assertEquals(Typeface.DEFAULT, FontManager.getTypeface(context, entry.id))
    }

    @Test fun savedIdentifiersCannotReadOutsideTheFontLibrary() {
        assertNull(PersonalFonts.loadTypeface(context, "personal:../outside.ttf"))
        assertNull(PersonalFonts.loadTypeface(context, "personal:/outside.ttf"))
        assertNull(PersonalFonts.loadTypeface(context, "personal:..\\outside.ttf"))
    }
}
