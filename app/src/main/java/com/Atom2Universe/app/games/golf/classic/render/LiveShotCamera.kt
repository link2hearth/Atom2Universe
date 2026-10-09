package com.Atom2Universe.app.games.golf.classic.render

import com.Atom2Universe.app.games.golf.classic.core.*
import kotlin.math.*

/** Manual views follow the shot's ball or detach from it without changing the simulation. */
internal class LiveShotCamera {
    var mode=ShotCameraMode.ORBIT
        private set
    val manual get()=mode==ShotCameraMode.ORBIT || mode==ShotCameraMode.FREE
    private var yaw=0f
    private var pitch=.4f
    private var distance=30f
    private var freeEye=GolfPoint(0f,10f,0f)
    private var lastPose:GolfCameraPose?=null

    fun begin(ball:GolfPoint,aim:Float,club:GolfClub) {
        yaw=aim+.8f;pitch=.4f;distance=if(club==GolfClub.PUTTER)8f else 30f
        freeEye=orbitEye(ball);lastPose=null
    }

    fun select(next:ShotCameraMode) {
        // Detach exactly where the previous camera stood; further ball movement has no effect.
        if(next==ShotCameraMode.FREE && mode!=next)lastPose?.let { pose ->
            freeEye=pose.eye
            val dx=pose.target.x-freeEye.x;val dy=pose.target.y-freeEye.y;val dz=pose.target.z-freeEye.z
            yaw=atan2(dx,dz);pitch=atan2(-dy,hypot(dx,dz))
        }
        mode=next
    }

    fun rotate(dx:Float,dy:Float,width:Int,height:Int) {
        yaw=(yaw-dx/width.coerceAtLeast(1)*5f)%(2f*PI.toFloat())
        pitch=(pitch+dy/height.coerceAtLeast(1)*3f).coerceIn(-1.35f,1.45f)
    }

    fun move(dx:Float,dy:Float,factor:Float,height:Int) {
        if(mode==ShotCameraMode.ORBIT)distance=(distance*factor).coerceIn(.8f,600f)
        else if(mode==ShotCameraMode.FREE) {
            val scale=distance/height.coerceAtLeast(1)
            val forward=distance*(1f-factor).coerceIn(-1f,1f)
            freeEye=GolfPoint(freeEye.x-cos(yaw)*dx*scale+sin(yaw)*cos(pitch)*forward,
                freeEye.y+dy*scale-sin(pitch)*forward,
                freeEye.z+sin(yaw)*dx*scale+cos(yaw)*cos(pitch)*forward)
        }
    }

    private fun orbitEye(ball:GolfPoint)=GolfPoint(ball.x-sin(yaw)*distance*cos(pitch),
        ball.y+distance*sin(pitch),ball.z-cos(yaw)*distance*cos(pitch))

    fun pose(hole:ClassicHole,ball:GolfPoint,origin:GolfPoint,aim:Float,club:GolfClub,seconds:Float,stroke:Int):GolfCameraPose {
        val pose=when(mode) {
            ShotCameraMode.ORBIT -> {
                val eye=orbitEye(ball)
                GolfCameraPose(eye.copy(y=max(eye.y,hole.heightAt(eye.x,eye.z)+.3f)),ball,mode.ordinal+stroke*10)
            }
            ShotCameraMode.FREE -> {
                freeEye=freeEye.copy(x=freeEye.x.coerceIn(-1200f,1200f),z=freeEye.z.coerceIn(-1200f,hole.length+1200f),y=freeEye.y.coerceIn(-200f,1200f))
                freeEye=freeEye.copy(y=max(freeEye.y,hole.heightAt(freeEye.x,freeEye.z)+.3f))
                GolfCameraPose(freeEye,GolfPoint(freeEye.x+sin(yaw)*cos(pitch),freeEye.y-sin(pitch),freeEye.z+cos(yaw)*cos(pitch)),mode.ordinal+stroke*10)
            }
            else -> ShotCamera.pose(hole,ball,origin,aim,club,seconds,stroke,mode)
        }
        lastPose=pose
        return pose
    }
}
