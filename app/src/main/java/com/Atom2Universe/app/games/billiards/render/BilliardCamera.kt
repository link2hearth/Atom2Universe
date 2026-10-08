package com.Atom2Universe.app.games.billiards.render

import com.Atom2Universe.app.games.billiards.core.V3
import kotlin.math.*

enum class BilliardCameraMode { CLOSE, WIDE, TOP, FREE }

data class BilliardCameraState(val mode: BilliardCameraMode, val yaw: Float, val pitch: Float,
                              val zoom: Float, val x: Float, val z: Float)
data class BilliardCameraPose(val eye: V3, val target: V3, val up: V3,
                             val orthographicHalfHeight: Float? = null)

/** Camera presets and gestures are independent of whether the player is aiming. */
class BilliardCamera {
    @Volatile var mode = BilliardCameraMode.FREE; private set
    @Volatile var yaw = -.85f; private set
    @Volatile var pitch = .76f; private set
    @Volatile var zoom = 1f; private set
    @Volatile var x = 0f; private set
    @Volatile var z = 0f; private set
    val behindCue get() = mode==BilliardCameraMode.CLOSE || mode==BilliardCameraMode.WIDE
    @Volatile var showcase = false
    @Volatile var settling = true; private set
    private var last = 0L
    private var eye: V3? = null
    private var target: V3? = null
    private var plannedCue: V3? = null
    private var length = 2.54f
    private var width = 1.27f
    private var aspect = 1f
    private var available = 1f
    private var smoothing = 12.0

    @Synchronized fun select(value: BilliardCameraMode, portrait: Boolean = false) {
        // Switch projections immediately to avoid a temporarily tilted 2D view.
        if(mode==BilliardCameraMode.TOP || value==BilliardCameraMode.TOP) { eye=null; target=null }
        mode=value; zoom=1f; x=0f; z=0f; plannedCue=null; smoothing=12.0
        when(value) {
            BilliardCameraMode.CLOSE, BilliardCameraMode.WIDE -> pitch=.18f
            BilliardCameraMode.TOP -> { pitch=(PI/2).toFloat(); yaw=if(portrait) (PI/2).toFloat() else 0f }
            BilliardCameraMode.FREE -> { pitch=.76f; yaw=-.85f }
        }
        settling=true
    }
    fun overview() = select(BilliardCameraMode.FREE)
    fun aim() = select(BilliardCameraMode.CLOSE)
    fun top(portrait: Boolean = false) = select(BilliardCameraMode.TOP,portrait)
    /** A changed menu viewport must keep rendering until its new framing has settled. */
    @Synchronized fun reframe() { settling=true }

    @Synchronized fun state() = BilliardCameraState(mode,yaw,pitch,zoom,x,z)
    @Synchronized fun restore(state: BilliardCameraState, portrait: Boolean) {
        select(state.mode,portrait)
        if(mode!=BilliardCameraMode.TOP) { yaw=state.yaw; pitch=state.pitch }
        zoom=state.zoom; x=state.x; z=state.z
    }

    /** Once per stroke: subsequent gestures are never overridden. */
    @Synchronized fun watchShot(angle: Double) {
        if(behindCue) {
            mode=BilliardCameraMode.FREE
            yaw=atan2(-cos(angle),-sin(angle)).toFloat(); pitch=.82f
        }
        if(mode==BilliardCameraMode.FREE) pitch=max(pitch,.72f)
        zoom=1f; x=0f; z=0f; smoothing=5.0; settling=true
    }

    private fun fitDistance(): Float {
        val horizontal=abs(cos(yaw))*length+abs(sin(yaw))*width+.50f
        val depth=abs(sin(yaw))*length+abs(cos(yaw))*width+.50f
        return max(horizontal/aspect.coerceAtLeast(.2f),depth*sin(pitch)+.3f)/(.76f*available)
    }
    private fun detachFromCue() {
        if(!behindCue) return
        val from=eye; val at=target
        mode=BilliardCameraMode.FREE
        if(from!=null && at!=null) {
            val delta=from-at
            yaw=atan2(delta.x,delta.z).toFloat()
            pitch=atan2(delta.y,hypot(delta.x,delta.z)).toFloat().coerceIn(.08f,1.48f)
            x=at.x.toFloat(); z=at.z.toFloat()
            zoom=(fitDistance()/delta.length().toFloat().coerceAtLeast(.1f)).coerceIn(.45f,12f)
        }
    }
    @Synchronized fun orbit(dx: Float,dy: Float) {
        if(mode==BilliardCameraMode.TOP) return
        detachFromCue()
        yaw-=dx*.006f; pitch=(pitch+dy*.004f).coerceIn(.08f,1.48f)
        smoothing=24.0; settling=true
    }
    @Synchronized fun dolly(factor: Float) {
        if(!factor.isFinite() || factor<=0) return
        zoom=(zoom*factor).coerceIn(.45f,if(behindCue) 4f else 12f); smoothing=24.0; settling=true
    }
    @Synchronized fun pan(dx: Float,dz: Float) {
        if(!dx.isFinite() || !dz.isFinite()) return
        detachFromCue()
        x=(x+dx).coerceIn(-length,length); z=(z+dz).coerceIn(-width,width)
        smoothing=24.0; settling=true
    }
    @Synchronized fun pose(frame: BilliardFrame?,length: Float,width: Float,aspect: Float,available: Float=1f): BilliardCameraPose {
        this.length=length; this.width=width; this.aspect=aspect; this.available=available
        val now=System.nanoTime()
        val dt=if(last==0L) .016 else ((now-last)/1e9).coerceIn(0.0,.1)
        last=now
        if(showcase) yaw+=(dt*.035).toFloat()
        val cue=frame?.balls?.firstOrNull { it.id==frame.cueId }
        val desiredTarget: V3
        val desiredEye: V3
        var halfHeight: Float?=null
        var up=V3(0.0,1.0,0.0)
        if(mode==BilliardCameraMode.TOP) {
            val horizontal=abs(cos(yaw))*length+abs(sin(yaw))*width+.32f
            val depth=abs(sin(yaw))*length+abs(cos(yaw))*width+.32f
            halfHeight=max(horizontal/aspect.coerceAtLeast(.2f),depth)/(2*available*zoom)
            desiredTarget=V3(x.toDouble(),.78,z.toDouble())
            desiredEye=desiredTarget+V3(0.0,6.0,0.0)
            up=V3(-sin(yaw.toDouble()),0.0,-cos(yaw.toDouble()))
        } else if(behindCue && cue!=null) {
            if(!frame.moving || plannedCue==null) plannedCue=cue.p
            val cuePosition=plannedCue!!
            val a=frame.shot.angle
            val distance=((if(mode==BilliardCameraMode.CLOSE) .95 else 2.6)/zoom).coerceIn(.22,5.8)
            desiredTarget=V3(cuePosition.x-length/2+cos(a)*.35,.81+cue.radius,cuePosition.y-width/2+sin(a)*.35)
            // Moving back preserves the same sight line through the cue ball.
            val horizontal=distance+.35
            desiredEye=desiredTarget+V3(-cos(a)*horizontal,sin(pitch.toDouble())*horizontal,-sin(a)*horizontal)
        } else {
            val distance=fitDistance()/zoom
            desiredTarget=V3(x.toDouble(),.78,z.toDouble())
            desiredEye=desiredTarget+V3(sin(yaw.toDouble())*cos(pitch.toDouble())*distance,
                sin(pitch.toDouble())*distance,cos(yaw.toDouble())*cos(pitch.toDouble())*distance)
        }
        val mix=1-exp(-dt*smoothing)
        eye=eye?.let { it+(desiredEye-it)*mix } ?: desiredEye
        target=target?.let { it+(desiredTarget-it)*mix } ?: desiredTarget
        settling=showcase || (eye!!-desiredEye).length()>.0003 || (target!!-desiredTarget).length()>.0003
        return BilliardCameraPose(eye!!,target!!,up,halfHeight)
    }
}
