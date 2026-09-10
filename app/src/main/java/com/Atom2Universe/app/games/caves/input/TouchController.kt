package com.Atom2Universe.app.games.caves.input

import android.view.MotionEvent
import kotlin.math.hypot

class TouchController {
    // Left joystick: -1..1
    var moveForward = 0f
    fun reset() {
        moveForward = 0f; moveRight = 0f; deltaYaw = 0f; deltaPitch = 0f
        flyUp = false; flyDown = false; laserActive = false; rtChargeRaw = 0f
        placeRequested = false; sprintActive = false; leftId = -1; rightId = -1
        gamepadRightX = 0f; gamepadRightY = 0f
    }
    var moveRight = 0f

    // Camera delta applied each frame then reset
    @Volatile var deltaYaw = 0f
    @Volatile var deltaPitch = 0f

    // Fly buttons (set by Activity touch listeners)
    @Volatile var flyUp = false
    @Volatile var flyDown = false

    // Laser / minage (maintenu enfoncé pour miner)
    @Volatile var laserActive = false

    // Valeur analogique de la gâchette droite (0..1) — utilisée pour la charge du lancer de cailloux
    @Volatile var rtChargeRaw = 0f

    // Pose de bloc (1 bloc par appui, consommé par le renderer)
    @Volatile var placeRequested = false

    // Sprint (double-tap joystick gauche, ou toggle L3 manette)
    @Volatile var sprintActive = false

    private var leftId = -1
    private var leftCx = 0f; private var leftCy = 0f

    private var rightId = -1
    private var rightPx = 0f; private var rightPy = 0f
    private var rightDownX = 0f; private var rightDownY = 0f
    private var rightMoved = false

    private val JOYSTICK_RADIUS = 120f
    private val TAP_SLOP = 18f

    private var leftTapCount = 0
    private var lastLeftTapMs = 0L
    private val DOUBLE_TAP_MS = 380L

    fun onTouch(event: MotionEvent, screenWidth: Int, excludedPointers: Set<Int> = emptySet()) {
        val half = screenWidth / 2f
        val action = event.actionMasked
        val idx = event.actionIndex
        val pid = event.getPointerId(idx)

        when (action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                if (pid in excludedPointers) return
                val ex = event.getX(idx); val ey = event.getY(idx)
                if (ex < half) {
                    if (leftId == -1) {
                        leftId = pid; leftCx = ex; leftCy = ey
                        val now = System.currentTimeMillis()
                        if (now - lastLeftTapMs < DOUBLE_TAP_MS) {
                            leftTapCount++
                            if (leftTapCount >= 2) {
                                sprintActive = !sprintActive
                                leftTapCount = 0
                            }
                        } else {
                            leftTapCount = 1
                        }
                        lastLeftTapMs = now
                    }
                } else {
                    if (rightId == -1) {
                        rightId = pid
                        rightPx = ex; rightPy = ey
                        rightDownX = ex; rightDownY = ey
                        rightMoved = false
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until event.pointerCount) {
                    val p = event.getPointerId(i)
                    if (p in excludedPointers) continue
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
                            if (!rightMoved && hypot(ex - rightDownX, ey - rightDownY) > TAP_SLOP) rightMoved = true
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

    fun isCameraPointer(pid: Int): Boolean = pid == rightId
    fun didCameraPointerMove(): Boolean = rightMoved

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
