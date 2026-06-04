package com.Atom2Universe.app.games.caves.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Gestion manette Xbox (et compatibles HID) pour Cave World.
 *
 * Mapping :
 *   Stick gauche    → déplacement (avant/arrière/strafe)
 *   Stick droit     → caméra (yaw/pitch)
 *   A               → sauter / monter (spectateur)
 *   B               → descendre (spectateur)
 *   RT (gâchette D) → laser / miner
 *   LT (gâchette G) → poser un bloc
 *   R1 / L1         → naviguer hotbar droite / gauche
 */
class GamepadController(private val touch: TouchController) {

    private var ltWasAboveThreshold = false

    fun onGenericMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false

        touch.moveForward   = -axis(event, MotionEvent.AXIS_Y)
        touch.moveRight     =  axis(event, MotionEvent.AXIS_X)
        touch.gamepadRightX =  axis(event, MotionEvent.AXIS_Z)
        touch.gamepadRightY =  axis(event, MotionEvent.AXIS_RZ)

        // RT : laser/miner ou charge lancer de cailloux (selon slot sélectionné)
        val rt = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        )
        touch.rtChargeRaw = rt
        touch.laserActive = rt > TRIGGER_THRESHOLD

        // LT : poser un bloc (front montant uniquement)
        val lt = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        )
        val ltAbove = lt > TRIGGER_THRESHOLD
        if (ltAbove && !ltWasAboveThreshold) touch.placeRequested = true
        ltWasAboveThreshold = ltAbove

        return true
    }

    fun onKeyDown(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A      -> { touch.flyUp   = true; true }
        KeyEvent.KEYCODE_BUTTON_B      -> { touch.flyDown = true; true }
        KeyEvent.KEYCODE_BUTTON_THUMBL -> { touch.sprintActive = !touch.sprintActive; true }
        else -> false
    }

    fun onKeyUp(keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> { touch.flyUp   = false; true }
        KeyEvent.KEYCODE_BUTTON_B -> { touch.flyDown = false; true }
        else -> false
    }

    // Lit un axe avec zone morte basée sur le flat du périphérique
    private fun axis(event: MotionEvent, axis: Int): Float {
        val device = event.device ?: return 0f
        val range  = device.getMotionRange(axis, event.source) ?: return 0f
        val value  = event.getAxisValue(axis)
        return if (abs(value) > range.flat * DEADZONE_FACTOR) value else 0f
    }

    companion object {
        private const val TRIGGER_THRESHOLD = 0.3f
        private const val DEADZONE_FACTOR   = 1.5f
    }
}
