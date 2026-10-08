package com.Atom2Universe.app.games.caves.world

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SlabShapeTest {
    @Test fun metaRoundTrip() {
        for (side in 0..5) assertEquals(side, PartialBlockModel.slabSide(PartialBlockModel.slabMeta(side)))
        // Les dalles couchées gardent leur ancien meta : 0 en bas, 4 en haut.
        assertEquals(0.toByte(), PartialBlockModel.slabMeta(0))
        assertEquals(4.toByte(), PartialBlockModel.slabMeta(1))
    }

    @Test fun fiveZonesOnTheSideOfABlock() {
        // Face +X d'un bloc visée : la nouvelle case la touche par son côté −X (2).
        fun aim(y: Double, z: Double) = PartialBlockModel.slabSideFromAim(2, 1.0, y, z)
        assertEquals(2, aim(.5, .5))    // centre : debout, à plat contre le bloc
        assertEquals(1, aim(.9, .5))    // haut : couchée en haut
        assertEquals(0, aim(.1, .5))    // bas : couchée en bas
        assertEquals(5, aim(.5, .9))    // bord +Z : debout, perpendiculaire
        assertEquals(4, aim(.5, .1))    // bord −Z
    }

    @Test fun fiveZonesOnTopOfABlock() {
        fun aim(x: Double, z: Double) = PartialBlockModel.slabSideFromAim(0, x, 1.0, z)
        assertEquals(0, aim(.5, .5))    // centre : couchée en bas, comme avant
        assertEquals(3, aim(.95, .6))   // bord +X : debout contre ce bord
        assertEquals(2, aim(.05, .6))
        assertEquals(5, aim(.6, .95))
        assertEquals(4, aim(.4, .05))
    }

    @Test fun eachSideFillsItsHalf() {
        // Face pleine du côté plein, face ouverte en face : c'est ce que lit la lumière du ciel.
        val fullFace = intArrayOf(1, 0, 3, 2, 5, 4)   // côté plein → direction de la face collée au voisin
        for (side in 0..5) {
            val boxes = PartialBlockModel.boxes(PartialBlockModel.slabMeta(side), slab = true)
            assertEquals(4, boxes.size)
            assertFalse(PartialBlockModel.hasOpenBoundary(boxes, fullFace[side]))
            assertTrue(PartialBlockModel.hasOpenBoundary(boxes, fullFace[side] xor 1))
        }
    }
}
