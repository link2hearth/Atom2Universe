package com.Atom2Universe.app.games.toyboxracers.input

import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs

/**
 * Manette pour Toybox Racers, calquée sur les jeux de kart.
 *
 * Direction  : stick gauche (analogique) ou croix directionnelle
 * Gaz        : gâchette droite (analogique) ou A
 * Frein/recul: gâchette gauche (analogique) ou B
 * Saut/glisse: R1, L1 ou X — tenu pendant le virage, relâché pour relancer
 * Pause      : Start ou Select
 * Départ     : Y
 *
 * La classe ne fait que traduire les événements : elle n'a aucun état de jeu.
 * Chaque commande a deux entrées (un axe et un bouton) et la plus engagée
 * l'emporte, pour que les manettes sans gâchettes analogiques restent jouables.
 */
internal class RacerGamepad(private val listener: Listener) {

    interface Listener {
        fun onSteering(value: Float)
        fun onThrottle(value: Float)
        fun onBrake(value: Float)
        fun onHop(pressed: Boolean)
        fun onPause()
        fun onRestart()
    }

    private var axisSteering = 0f
    private var axisThrottle = 0f
    private var axisBrake = 0f
    private var buttonSteering = 0f
    private var buttonThrottle = 0f
    private var buttonBrake = 0f
    private val hopButtons = HashSet<Int>()

    /** Vrai dès qu'un périphérique de jeu a parlé : sert à masquer le tactile. */
    var active = false
        private set

    fun reset() {
        axisSteering = 0f
        axisThrottle = 0f
        axisBrake = 0f
        buttonSteering = 0f
        buttonThrottle = 0f
        buttonBrake = 0f
        hopButtons.clear()
        push()
        listener.onHop(false)
    }

    fun onGenericMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK) return false
        active = true
        // Le bouton « ◀ » de l'écran envoie +1 : pousser le stick à gauche
        // (AXIS_X négatif) doit donc donner la même valeur positive.
        val stick = -axis(event, MotionEvent.AXIS_X)
        val hat = -event.getAxisValue(MotionEvent.AXIS_HAT_X)
        axisSteering = if (abs(hat) > 0.5f) hat else stick
        axisThrottle = maxOf(
            event.getAxisValue(MotionEvent.AXIS_RTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_GAS)
        ).coerceIn(0f, 1f)
        axisBrake = maxOf(
            event.getAxisValue(MotionEvent.AXIS_LTRIGGER),
            event.getAxisValue(MotionEvent.AXIS_BRAKE)
        ).coerceIn(0f, 1f)
        push()
        return true
    }

    fun onKeyDown(keyCode: Int): Boolean = onKey(keyCode, true)

    fun onKeyUp(keyCode: Int): Boolean = onKey(keyCode, false)

    private fun onKey(keyCode: Int, down: Boolean): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_LEFT -> buttonSteering = if (down) 1f else 0f
            KeyEvent.KEYCODE_DPAD_RIGHT -> buttonSteering = if (down) -1f else 0f
            KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_DPAD_UP ->
                buttonThrottle = if (down) 1f else 0f
            KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_DPAD_DOWN ->
                buttonBrake = if (down) 1f else 0f
            KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_X -> {
                // Trois boutons mènent au saut : la glisse ne doit pas se couper
                // parce qu'on relâche l'un pendant qu'on tient l'autre.
                val wasHeld = hopButtons.isNotEmpty()
                if (down) hopButtons.add(keyCode) else hopButtons.remove(keyCode)
                val held = hopButtons.isNotEmpty()
                active = true
                if (held != wasHeld) listener.onHop(held)
                return true
            }
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT -> {
                active = true
                if (down) listener.onPause()
                return true
            }
            KeyEvent.KEYCODE_BUTTON_Y -> {
                active = true
                if (down) listener.onRestart()
                return true
            }
            else -> return false
        }
        active = true
        push()
        return true
    }

    private fun push() {
        val steering = if (buttonSteering != 0f) buttonSteering else axisSteering
        listener.onSteering(steering.coerceIn(-1f, 1f))
        listener.onThrottle(maxOf(axisThrottle, buttonThrottle))
        listener.onBrake(maxOf(axisBrake, buttonBrake))
    }

    /** Lit un axe avec la zone morte déclarée par le périphérique. */
    private fun axis(event: MotionEvent, axis: Int): Float {
        val device = event.device ?: return 0f
        val range = device.getMotionRange(axis, event.source) ?: return 0f
        val value = event.getAxisValue(axis)
        return if (abs(value) > range.flat * DEADZONE_FACTOR) value else 0f
    }

    companion object {
        private const val DEADZONE_FACTOR = 1.5f
    }
}
