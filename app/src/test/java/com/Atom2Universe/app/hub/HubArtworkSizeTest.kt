package com.Atom2Universe.app.hub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HubArtworkSizeTest {
    @Test
    fun middleColumnSharesTheSameImagesForEveryGridRemainder() {
        for (spans in 2..4) for (innerWidth in 300..2500) {
            // Même répartition des pixels restants que GridLayoutManager.calculateItemBorders.
            val base = innerWidth / spans
            val remainder = innerWidth % spans
            var carried = 0
            val height = base - 32
            val expected = HubArtworkSize.forBounds(height, height)
            repeat(spans) { column ->
                var columnWidth = base
                carried += remainder
                if (carried > 0 && spans - carried < remainder) {
                    columnWidth++
                    carried -= spans
                }
                assertEquals("width=$innerWidth spans=$spans column=$column", expected,
                    HubArtworkSize.forBounds(columnWidth - 32, height))
            }
        }
    }

    @Test
    fun oneHundredLargeTilesFitWithinSixtyFourMiB() {
        val (width, height) = HubArtworkSize.forBounds(900, 900)
        assertTrue(100L * width * height * 4 <= 64L * 1024 * 1024)
    }

    @Test
    fun listAndShortcutArtworksKeepTheirAspectRatio() {
        assertEquals(120 to 240, HubArtworkSize.forBounds(120, 240))
        assertEquals(384 to 192, HubArtworkSize.forBounds(1000, 500))
        assertEquals(192 to 384, HubArtworkSize.forBounds(500, 1000))
        assertEquals(240 to 120, HubArtworkSize.forBounds(240, 120))
    }
}
