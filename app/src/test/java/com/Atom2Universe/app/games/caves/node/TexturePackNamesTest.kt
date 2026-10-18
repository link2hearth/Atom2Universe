package com.Atom2Universe.app.games.caves.node

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Le contrat de nommage du pack de textures : noms sûrs, uniques, et export sans perte. */
class TexturePackNamesTest {
    @Test fun sanitizeKeepsOnlySafeCharacters() {
        assertEquals("stone_brick", TexturePackNames.sanitize("Stone Brick"))
        assertEquals("a_b-c_1", TexturePackNames.sanitize("a:b-c/1"))
        assertEquals("texture", TexturePackNames.sanitize("///"))
    }

    @Test fun uniformBlocksUseTheBareName_othersGetASuffix() {
        assertEquals("stone", TexturePackNames.faceName("stone", "top", allFacesSame = true))
        assertEquals("grass_top", TexturePackNames.faceName("grass", "top"))
    }

    @Test fun namerNeverRepeatsANameEvenIgnoringCase() {
        val namer = TexturePackNames.Namer()
        val names = listOf("Foo_top", "foo_top", "FOO_TOP", "foo").map(namer::unique)
        assertEquals(names.size, names.map { it.lowercase() }.toSet().size)
        assertEquals("foo_top", names[0])
        assertEquals("foo_top_2", names[1])
    }

    private fun upscale(pixels: IntArray, size: Int, f: Int) =
        IntArray(size * f * size * f) { pixels[(it / (size * f) / f) * size + (it % (size * f)) / f] }

    @Test fun shrinkFindsTheNativeResolution() {
        val native = IntArray(32 * 32) { (it * 2654435761L).toInt() or 0xFF000000.toInt() }
        val (shrunk, size) = TexturePackNames.shrinkToNative(upscale(native, 32, 3), 96)
        assertEquals(32, size)
        assertArrayEquals(native, shrunk)
    }

    @Test fun shrinkLeavesRealHighResolutionAlone() {
        val noisy = IntArray(96 * 96) { it * 7919 }
        val (same, size) = TexturePackNames.shrinkToNative(noisy, 96)
        assertEquals(96, size)
        assertTrue(same === noisy)
    }

    @Test fun aSolidTextureIsNeverReducedBelowSixteenPixels() {
        val (_, size) = TexturePackNames.shrinkToNative(IntArray(96 * 96) { -1 }, 96)
        assertEquals(16, size)
    }
}
