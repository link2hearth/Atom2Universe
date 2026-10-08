package com.Atom2Universe.app.games.caves.input

import android.view.InputDevice
import android.view.MotionEvent
import kotlin.math.abs

/** Affectations de jeu uniquement ; les menus gardent leur navigation standard. */
internal class GamepadController(
    private val touch: TouchController,
    private val onAction: (GamepadAction) -> Unit = {}
) {
    private var bindings = GamepadBindings.defaults()
    private val keys = BooleanArray(XboxButton.entries.size)
    private val axes = FloatArray(XboxButton.entries.size)
    private val pressed = BooleanArray(XboxButton.entries.size)
    private var resetSerial = 0

    fun configure(value: Map<GamepadAction, XboxButton>) {
        reset()
        bindings = if (GamepadBindings.valid(value)) value.toMap() else GamepadBindings.defaults()
    }

    fun reset() {
        resetSerial++
        keys.fill(false); axes.fill(0f); pressed.fill(false)
        touch.reset()
    }

    fun onGenericMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        touch.moveForward = -axis(event, MotionEvent.AXIS_Y)
        touch.moveRight = axis(event, MotionEvent.AXIS_X)
        touch.gamepadRightX = axis(event, MotionEvent.AXIS_Z)
        touch.gamepadRightY = axis(event, MotionEvent.AXIS_RZ)

        axes[XboxButton.RT.ordinal] = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS)).coerceIn(0f, 1f)
        axes[XboxButton.LT.ordinal] = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE)).coerceIn(0f, 1f)
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        axes[XboxButton.LEFT.ordinal] = (-hx).coerceIn(0f, 1f)
        axes[XboxButton.RIGHT.ordinal] = hx.coerceIn(0f, 1f)
        axes[XboxButton.UP.ordinal] = (-hy).coerceIn(0f, 1f)
        axes[XboxButton.DOWN.ordinal] = hy.coerceIn(0f, 1f)
        val serial = resetSerial
        for (button in MOTION_BUTTONS) {
            update(button)
            // Une action peut ouvrir l'inventaire et réinitialiser les entrées pendant
            // cet événement. Ne pas réactiver une autre action derrière le menu.
            if (serial != resetSerial) break
        }
        return true
    }

    fun onKeyDown(keyCode: Int): Boolean = key(keyCode, true)
    fun onKeyUp(keyCode: Int): Boolean = key(keyCode, false)

    private fun key(code: Int, down: Boolean): Boolean {
        val button = XboxButton.fromKey(code) ?: return false
        keys[button.ordinal] = down
        update(button)
        return true
    }

    private fun update(button: XboxButton) {
        val i = button.ordinal
        // Certains pilotes envoient aussi une touche L2/R2 : elle ne doit pas
        // transformer une pression analogique partielle en une charge maximale.
        val pressure = if (axes[i] > 0f) axes[i] else if (keys[i]) 1f else 0f
        val down = keys[i] || axes[i] > TRIGGER_THRESHOLD
        val changed = pressed[i] != down
        pressed[i] = down
        val action = bindings.entries.firstOrNull { it.value == button }?.key ?: return
        // La pression analogique suit l'action Tir, même après réaffectation de RT/LT.
        if (action == GamepadAction.ATTACK) {
            touch.rtChargeRaw = pressure
            touch.laserActive = down
            if (changed && down) touch.firePresses++
        }
        if (!changed) return // répétitions Android et doublons touche/axe ignorés
        when (action) {
            GamepadAction.JUMP -> touch.flyUp = down
            GamepadAction.CROUCH -> if (down) touch.pressDown() else touch.flyDown = false
            GamepadAction.RUN -> if (down) touch.pressGamepadRun() else touch.releaseGamepadRun()
            GamepadAction.PLACE -> { if (down) touch.placeRequested = true; touch.placeHeld = down }
            GamepadAction.ATTACK -> Unit
            else -> if (down) onAction(action)
        }
    }

    private fun axis(event: MotionEvent, axis: Int): Float {
        val range = event.device?.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > range.flat * DEADZONE_FACTOR) value else 0f
    }

    companion object {
        private val MOTION_BUTTONS = arrayOf(XboxButton.RT, XboxButton.LT,
            XboxButton.LEFT, XboxButton.RIGHT, XboxButton.UP, XboxButton.DOWN)
        private const val TRIGGER_THRESHOLD = 0.3f
        private const val DEADZONE_FACTOR = 1.5f
    }
}
