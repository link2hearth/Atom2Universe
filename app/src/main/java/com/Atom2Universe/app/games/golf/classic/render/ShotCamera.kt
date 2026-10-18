package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.*
import kotlin.math.*

enum class ShotCameraMode { AUTO, SIDE, AERIAL, ARRIVAL, CHASE, ORBIT }

data class GolfCameraPose(val eye: GolfPoint, val target: GolfPoint, val cut: Int)

/** The same time-based director is used during a stroke and when reviewing its recording. */
object ShotCamera {
    fun pose(hole: ClassicHole, ball: GolfPoint, origin: GolfPoint, aim: Float,
             club: GolfClub, seconds: Float, variant: Int, mode: ShotCameraMode): GolfCameraPose {
        val segment = (seconds / 3.5f).toInt()
        val selected = if (mode == ShotCameraMode.AUTO) {
            val sequence = if (variant % 2 == 0) listOf(ShotCameraMode.SIDE, ShotCameraMode.AERIAL, ShotCameraMode.ARRIVAL)
                else listOf(ShotCameraMode.AERIAL, ShotCameraMode.SIDE, ShotCameraMode.ARRIVAL)
            sequence[segment % sequence.size]
        } else mode
        val small = club == GolfClub.PUTTER
        val distance = if (small) 6f else 24f
        val side = if (variant % 2 == 0) 1f else -1f
        val sx = sin(aim); val sz = cos(aim)
        val eye = when (selected) {
            ShotCameraMode.AERIAL -> GolfPoint(ball.x-sx*distance*.5f,ball.y+distance*1.35f,ball.z-sz*distance*.5f)
            ShotCameraMode.ARRIVAL -> {
                val near = hypot(ball.x-hole.cup.x,ball.z-hole.cup.z) < distance*2f
                val anchor = if (near) hole.cup else ball
                GolfPoint(anchor.x+sx*distance+sz*distance*.45f,ball.y+distance*.45f,anchor.z+sz*distance-sx*distance*.45f)
            }
            ShotCameraMode.CHASE -> GolfPoint(ball.x-sx*distance,ball.y+distance*.4f,ball.z-sz*distance)
            else -> GolfPoint(ball.x+sz*distance*side-sx*distance*.3f,ball.y+distance*.35f,ball.z-sx*distance*side-sz*distance*.3f)
        }
        return GolfCameraPose(eye.copy(y=max(eye.y,hole.heightAt(eye.x,eye.z)+.8f)),ball,
            selected.ordinal + variant*10)
    }
}
