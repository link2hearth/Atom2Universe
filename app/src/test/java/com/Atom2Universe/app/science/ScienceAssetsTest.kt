package com.Atom2Universe.app.science

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class ScienceAssetsTest {
    private val modules = listOf("human_biology", "mycology", "botany", "bestiary", "periodic_table")

    @Test fun absentOrEmptyDirectoriesDisableOptionalModules() {
        for (id in modules) {
            assertFalse(id, ScienceAssets.isAvailable(id) { null })
            assertFalse(id, ScienceAssets.isAvailable(id) { emptyArray() })
            assertFalse(id, ScienceAssets.isAvailable(id) { throw IOException("Unavailable") })
        }
    }

    @Test fun eachModuleUsesItsOwnDirectoryIncludingElementsCapitalization() {
        val paths = listOf("science/biology", "science/mycology", "science/botany", "science/bestiary", "science/Elements")
        for ((index, path) in paths.withIndex()) {
            for ((otherIndex, id) in modules.withIndex()) {
                val available = ScienceAssets.isAvailable(id) {
                    if (it == path) arrayOf("data") else emptyArray()
                }
                if (index == otherIndex) assertTrue(id, available) else assertFalse(id, available)
            }
        }
    }

    @Test fun unrelatedScienceTilesStayAvailableWithoutOfflineData() {
        for (id in listOf("parentes", "solar_system", "nuclide_table", "living_earth")) {
            assertTrue(id, ScienceAssets.isAvailable(id) { error("Should not inspect optional assets") })
        }
    }
}
