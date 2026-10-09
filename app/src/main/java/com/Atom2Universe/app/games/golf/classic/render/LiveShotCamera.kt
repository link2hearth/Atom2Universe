package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.*
import kotlin.math.*

/** The orbit stays centred on the shot's ball while its angle and distance remain adjustable. */
internal class LiveShotCamera {
    var mode=ShotCameraMode.ORBIT
        private set
    val manual get()=mode==ShotCameraMode.ORBIT
    private var yaw=0f
    private var pitch=.4f
    private var distance=30f

    fun begin(aim:Float,club:GolfClub) {
        yaw=aim+.8f;pitch=.4f;distance=if(club==GolfClub.PUTTER)8f else 30f
    }

    fun select(next:ShotCameraMode) {
        mode=next
    }

    fun rotate(dx:Float,dy:Float,width:Int,height:Int) {
        yaw=(yaw-dx/width.coerceAtLeast(1)*5f)%(2f*PI.toFloat())
        pitch=(pitch+dy/height.coerceAtLeast(1)*3f).coerceIn(-1.35f,1.45f)
    }

    fun zoom(factor:Float) {
        distance=(distance*factor).coerceIn(.8f,600f)
    }

    private fun orbitEye(ball:GolfPoint)=GolfPoint(ball.x-sin(yaw)*distance*cos(pitch),
        ball.y+distance*sin(pitch),ball.z-cos(yaw)*distance*cos(pitch))

    fun pose(hole:ClassicHole,ball:GolfPoint,origin:GolfPoint,aim:Float,club:GolfClub,seconds:Float,stroke:Int):GolfCameraPose {
        val pose=when(mode) {
            ShotCameraMode.ORBIT -> {
                val eye=orbitEye(ball)
                GolfCameraPose(eye.copy(y=max(eye.y,hole.heightAt(eye.x,eye.z)+.3f)),ball,mode.ordinal+stroke*10)
            }
            else -> ShotCamera.pose(hole,ball,origin,aim,club,seconds,stroke,mode)
        }
        return pose
    }
}
