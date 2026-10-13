package com.Atom2Universe.app.science.botany

import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], manifest=Config.NONE)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlantHerbariumTest {
    @Test fun toutesLesPlanchesSontLisiblesCompletesEtAssocieesAuBonCatalogue() {
        val directory = File("src/main/assets/botany/herbarium")
        assertEquals(285, PlantHerbarium.speciesIds.size)
        assertEquals(PlantHerbarium.speciesIds.map { "$it.webp" }.toSet(), directory.list()!!.toSet())
        for (id in PlantHerbarium.speciesIds) {
            assertNotNull("Missing species: $id", BotanyCatalog.get(id))
            val file=File(directory, "$id.webp")
            assertTrue("Offline image must stay under 50 KB: $id",file.length() in 1..50_000)
            val image = requireNotNull(BitmapFactory.decodeFile(file.absolutePath))
            assertTrue("Portrait plate: $id", image.height > image.width)
            assertTrue("Enough detail: $id", image.width >= 320)
            assertEquals("Bestiary image size: $id",640,image.height)
            assertTrue(PlantHerbarium.hasPlate(id))
            assertFalse("Habit must remain a code drawing",PlantImageKey(id,PlantView.HABIT,640,false).image)
        }
    }
}
