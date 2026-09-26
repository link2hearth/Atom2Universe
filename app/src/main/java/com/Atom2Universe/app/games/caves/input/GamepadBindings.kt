package com.Atom2Universe.app.games.caves.input

import android.view.KeyEvent
import com.Atom2Universe.app.R

/** Identifiants persistants indépendants de l'ordre d'affichage. */
internal enum class XboxButton(val keyCode: Int, val label: Int, val color: Int = 0xFFDCE6EC.toInt()) {
    A(KeyEvent.KEYCODE_BUTTON_A, R.string.cave_pad_a, 0xFF78D84B.toInt()),
    B(KeyEvent.KEYCODE_BUTTON_B, R.string.cave_pad_b, 0xFFFF6464.toInt()),
    X(KeyEvent.KEYCODE_BUTTON_X, R.string.cave_pad_x, 0xFF55ACFF.toInt()),
    Y(KeyEvent.KEYCODE_BUTTON_Y, R.string.cave_pad_y, 0xFFFFD34D.toInt()),
    LB(KeyEvent.KEYCODE_BUTTON_L1, R.string.cave_pad_lb),
    RB(KeyEvent.KEYCODE_BUTTON_R1, R.string.cave_pad_rb),
    LT(KeyEvent.KEYCODE_BUTTON_L2, R.string.cave_pad_lt),
    RT(KeyEvent.KEYCODE_BUTTON_R2, R.string.cave_pad_rt),
    LS(KeyEvent.KEYCODE_BUTTON_THUMBL, R.string.cave_pad_ls),
    RS(KeyEvent.KEYCODE_BUTTON_THUMBR, R.string.cave_pad_rs),
    UP(KeyEvent.KEYCODE_DPAD_UP, R.string.cave_pad_up),
    DOWN(KeyEvent.KEYCODE_DPAD_DOWN, R.string.cave_pad_down),
    LEFT(KeyEvent.KEYCODE_DPAD_LEFT, R.string.cave_pad_left),
    RIGHT(KeyEvent.KEYCODE_DPAD_RIGHT, R.string.cave_pad_right);

    companion object {
        fun fromKey(code: Int) = entries.firstOrNull { it.keyCode == code }
    }
}

internal enum class GamepadAction(val label: Int, val defaultButton: XboxButton) {
    JUMP(R.string.cave_pad_jump, XboxButton.A),
    CROUCH(R.string.cave_pad_crouch, XboxButton.B),
    ATTACK(R.string.cave_pad_attack, XboxButton.RT),
    PLACE(R.string.cave_pad_place, XboxButton.LT),
    SECONDARY(R.string.cave_pad_secondary, XboxButton.X),
    INVENTORY(R.string.cave_pad_inventory, XboxButton.Y),
    PREVIOUS_SLOT(R.string.cave_pad_previous, XboxButton.LB),
    NEXT_SLOT(R.string.cave_pad_next, XboxButton.RB),
    RUN(R.string.cave_pad_run, XboxButton.LS),
    CAMERA(R.string.cave_pad_camera, XboxButton.RS);
}

internal object GamepadBindings {
    fun defaults(): Map<GamepadAction, XboxButton> = GamepadAction.entries.associateWith { it.defaultButton }
    fun valid(bindings: Map<GamepadAction, XboxButton>) =
        bindings.keys == GamepadAction.entries.toSet() && bindings.values.toSet().size == bindings.size

    fun assign(bindings: MutableMap<GamepadAction, XboxButton>, action: GamepadAction, button: XboxButton) {
        val previous = bindings.getValue(action)
        val occupied = bindings.entries.firstOrNull { it.value == button }?.key
        if (occupied != null) bindings[occupied] = previous
        bindings[action] = button
    }
}
