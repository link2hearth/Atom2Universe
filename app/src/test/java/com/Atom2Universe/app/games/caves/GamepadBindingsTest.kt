package com.Atom2Universe.app.games.caves

import com.Atom2Universe.app.games.caves.input.*
import org.junit.Assert.*
import org.junit.Test

class GamepadBindingsTest {
    @Test fun assigningAnOccupiedButtonSwapsActions() {
        val map = GamepadBindings.defaults().toMutableMap()
        GamepadBindings.assign(map, GamepadAction.JUMP, XboxButton.X)
        assertEquals(XboxButton.X, map[GamepadAction.JUMP])
        assertEquals(XboxButton.A, map[GamepadAction.SECONDARY])
        assertTrue(GamepadBindings.valid(map))
    }

    @Test fun assigningAnUnusedDpadButtonKeepsOtherActions() {
        val map = GamepadBindings.defaults().toMutableMap()
        GamepadBindings.assign(map, GamepadAction.INVENTORY, XboxButton.UP)
        assertEquals(XboxButton.UP, map[GamepadAction.INVENTORY])
        assertFalse(map.containsValue(XboxButton.Y))
        assertEquals(XboxButton.RT, map[GamepadAction.ATTACK])
        assertTrue(GamepadBindings.valid(map))
    }

    @Test fun duplicateOrMissingBindingsAreRejected() {
        val map = GamepadBindings.defaults().toMutableMap()
        map[GamepadAction.JUMP] = XboxButton.B
        assertFalse(GamepadBindings.valid(map))
        map.remove(GamepadAction.JUMP)
        assertFalse(GamepadBindings.valid(map))
    }

    @Test fun remappedJumpUsesNewButtonAndReleases() {
        val touch = TouchController()
        val controller = GamepadController(touch)
        val map = GamepadBindings.defaults().toMutableMap()
        GamepadBindings.assign(map, GamepadAction.JUMP, XboxButton.UP)
        controller.configure(map)
        controller.onKeyDown(XboxButton.A.keyCode)
        assertFalse(touch.flyUp)
        controller.onKeyDown(XboxButton.UP.keyCode)
        assertTrue(touch.flyUp)
        controller.onKeyUp(XboxButton.UP.keyCode)
        assertFalse(touch.flyUp)
    }

    @Test fun repeatedKeyDownDoesNotRepeatAnAction() {
        val actions = mutableListOf<GamepadAction>()
        val controller = GamepadController(TouchController()) { actions.add(it) }
        repeat(5) { controller.onKeyDown(XboxButton.RS.keyCode) }
        assertEquals(listOf(GamepadAction.CAMERA), actions)
        controller.onKeyUp(XboxButton.RS.keyCode)
        controller.onKeyDown(XboxButton.RS.keyCode)
        assertEquals(2, actions.size)
    }

    @Test fun mappedFaceButtonCanHoldAndReleaseAttack() {
        val touch = TouchController()
        val controller = GamepadController(touch)
        val map = GamepadBindings.defaults().toMutableMap()
        GamepadBindings.assign(map, GamepadAction.ATTACK, XboxButton.X)
        controller.configure(map)
        controller.onKeyDown(XboxButton.X.keyCode)
        controller.onKeyDown(XboxButton.X.keyCode)
        assertTrue(touch.laserActive)
        assertEquals(1f, touch.rtChargeRaw, 0f)
        assertEquals(1, touch.firePresses)
        controller.onKeyUp(XboxButton.X.keyCode)
        assertFalse(touch.laserActive)
        assertEquals(0f, touch.rtChargeRaw, 0f)
    }

    @Test fun reconfigurationReleasesHeldControls() {
        val touch = TouchController()
        val controller = GamepadController(touch)
        controller.onKeyDown(XboxButton.A.keyCode)
        controller.onKeyDown(XboxButton.LS.keyCode)
        controller.onKeyDown(XboxButton.RT.keyCode)
        controller.configure(GamepadBindings.defaults())
        assertFalse(touch.flyUp)
        assertFalse(touch.sprintRequested)
        assertFalse(touch.laserActive)
        controller.onKeyDown(XboxButton.A.keyCode)
        assertTrue(touch.flyUp)
    }
}
