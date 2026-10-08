package com.Atom2Universe.app.science.biology

import org.junit.Assert.*
import org.junit.Test

class AnatomyHistoryTest {
    private val initial = AnatomyDisplayState(
        emptySet(), null, setOf("SKELETON", "MUSCLES", "ORGANS"),
        setOf("SKELETON:SKULL", "SKELETON:SPINE", "MUSCLES:masseter", "ORGANS:RESPIRATORY"),
        emptySet(), "mandible", listOf(0.0, 0.0, 3.0, 0.0, .85, 0.0),
    )

    @Test fun hidingThenUndoRestoresTheSelectedStructureAndRedoHidesItAgain() {
        val history = AnatomyHistory<AnatomyDisplayState>()
        val hidden = initial.copy(hidden = setOf("mandible"), selected = null)
        history.record(initial, hidden)
        assertEquals(initial, history.undo())
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
        assertEquals(hidden, history.redo())
        assertFalse(history.canRedo)
    }

    @Test fun noChangeKeepsRedoButANewActionReplacesTheRedoBranch() {
        val history = AnatomyHistory<AnatomyDisplayState>()
        val hidden = initial.copy(hidden = setOf("mandible"), selected = null)
        history.record(initial, hidden)
        history.undo()
        history.record(initial, initial)
        assertTrue(history.canRedo)
        val colored = initial.copy(colored = setOf("SKELETON"))
        history.record(initial, colored)
        assertFalse(history.canRedo)
        assertEquals(initial, history.undo())
        assertEquals(colored, history.redo())
    }

    @Test fun muscleFiltersPreserveSelectedBonesAndOrgans() {
        val groups = updateLayerGroups(initial.groups,
            setOf("MUSCLES:masseter", "MUSCLES:quadriceps"), setOf("MUSCLES:quadriceps", "SKELETON:FEET"))
        assertEquals(setOf("SKELETON:SKULL", "SKELETON:SPINE", "MUSCLES:quadriceps", "ORGANS:RESPIRATORY"), groups)
        val none = updateLayerGroups(groups, setOf("MUSCLES:masseter", "MUSCLES:quadriceps"), emptySet())
        assertEquals(setOf("SKELETON:SKULL", "SKELETON:SPINE", "ORGANS:RESPIRATORY"), none)
    }

    @Test fun isolationAndCameraAreUndoneAsOneAction() {
        val history = AnatomyHistory<AnatomyDisplayState>()
        val isolated = initial.copy(isolated = "mandible", camera = listOf(0.0, .2, .08, .01, 1.48, .12))
        history.record(initial, isolated)
        assertEquals(initial, history.undo())
        assertEquals(isolated, history.redo())
        assertNull(history.redo())
    }

    @Test fun boundedHistoryRestoresBothStacksInTheirOriginalOrder() {
        val history = AnatomyHistory<Int>(3)
        (0..4).forEach { history.record(it, it + 1) }
        assertEquals(4, history.undo())
        assertEquals(3, history.undo())
        val restored = AnatomyHistory<Int>(3)
        restored.restore(history.undoEntries, history.redoEntries)
        assertEquals(4, restored.redo())
        assertEquals(5, restored.redo())
        assertEquals(4, restored.undo())
        assertEquals(3, restored.undo())
        assertEquals(2, restored.undo())
        assertNull(restored.undo())
    }
}
