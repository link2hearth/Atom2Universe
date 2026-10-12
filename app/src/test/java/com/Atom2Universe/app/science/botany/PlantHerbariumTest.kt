package com.Atom2Universe.app.science.botany

import android.graphics.BitmapFactory
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34], manifest=Config.NONE)
class PlantHerbariumTest {
    @Test fun toutesLesPlanchesSontLisiblesCompletesEtAssocieesAuBonCatalogue() {
        val directory = File("src/main/assets/botany/herbarium")
        assertEquals(10, PlantHerbarium.speciesIds.size)
        assertEquals(PlantHerbarium.speciesIds.map { "$it.png" }.toSet(), directory.list()!!.toSet())
        for (id in PlantHerbarium.speciesIds) {
            assertNotNull("Missing species: $id", BotanyCatalog.get(id))
            val image = requireNotNull(BitmapFactory.decodeFile(File(directory, "$id.png").absolutePath))
            assertTrue("Portrait plate: $id", image.height > image.width)
            assertTrue("Enough detail: $id", image.width >= 1024)
            assertTrue(PlantHerbarium.hasPlate(id, PlantView.HABIT))
            for (mode in PlantView.entries.filter { it != PlantView.HABIT }) {
                assertFalse("Organ studies must keep their own views", PlantHerbarium.hasPlate(id, mode))
            }
        }
    }
}
