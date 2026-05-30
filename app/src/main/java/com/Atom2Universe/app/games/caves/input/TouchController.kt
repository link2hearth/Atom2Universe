package com.Atom2Universe.app.games.caves.input

import android.view.MotionEvent
import kotlin.math.hypot

class TouchController {
    // Left joystick: -1..1
    var moveForward = 0f
    var moveRight = 0f

    // Camera delta applied each frame then reset
    @Volatile var deltaYaw = 0f
    @Volatile var deltaPitch = 0f

    // Fly buttons (set by Activity touch listeners)
    @Volatile var flyUp = false
    @Volatile var flyDown = false

    // Laser / minage (maintenu enfoncé pour miner)
    @Volatile var laserActive = false

    // Pose de bloc (1 bloc par appui, consommé par le renderer)
    @Volatile var placeRequested = false

    private var leftId = -1
    private var leftCx = 0f; private var leftCy = 0f

    private var rightId = -1
    private var rightPx = 0f; private var rightPy = 0f

    private val JOYSTICK_RADIUS = 120f

    fun onTouch(event: MotionEvent, screenWidth: Int) {
        val half = screenWidth / 2f
        val action = event.actionMasked
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val ex = event.getX(idx); val ey = event.getY(idx)
                if (ex < half) {
                    if (leftId == -1) { leftId = pid; leftCx = ex; leftCy = ey }
                } else {
                    if (rightId == -1) { rightId = pid; rightPx = ex; rightPy = ey }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val p = event.getPointerId(i)
                    val ex = event.getX(i); val ey = event.getY(i)
                    when (p) {
                        leftId -> {
                            val dx = (ex - leftCx) / JOYSTICK_RADIUS
                            val dy = (ey - leftCy) / JOYSTICK_RADIUS
                            val len = hypot(dx, dy).coerceAtMost(1f)
                            val scale = if (hypot(dx, dy) > 1f) len / hypot(dx, dy) else 1f
                            moveForward = -dy * scale
                            moveRight   =  dx * scale
                        }
                        rightId -> {
                            deltaYaw   += (ex - rightPx) * 0.14f
                            deltaPitch += (ey - rightPy) * 0.14f
                            rightPx = ex; rightPy = ey
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (pid == leftId)  { leftId  = -1; moveForward = 0f; moveRight = 0f }
                if (pid == rightId) { rightId = -1 }
            }
            MotionEvent.ACTION_CANCEL -> {
                leftId = -1; rightId = -1; moveForward = 0f; moveRight = 0f
            }
        }
    }

    // Axes du stick droit manette (mis à jour par GamepadController, -1..1)
    @Volatile var gamepadRightX = 0f
    @Volatile var gamepadRightY = 0f

    fun consumeDeltas(): Pair<Float, Float> {
        val dy = deltaYaw + gamepadRightX * GAMEPAD_CAM_SPEED
        val dp = deltaPitch + gamepadRightY * GAMEPAD_CAM_SPEED
        deltaYaw = 0f; deltaPitch = 0f
        return Pair(dy, dp)
    }

    companion object {
        // Degrés de rotation par frame à pleine déflexion du stick (~240°/s à 30 fps)
        const val GAMEPAD_CAM_SPEED = 8f
    }
}
