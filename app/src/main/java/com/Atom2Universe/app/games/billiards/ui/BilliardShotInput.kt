package com.Atom2Universe.app.games.billiards.ui

import kotlin.math.abs
import kotlin.math.pow

internal const val ARCADE_TIMING_TARGET=.72f
internal const val ARCADE_TIMING_PERFECT=.045f
internal const val ARCADE_TIMING_REACH=.24f

/** Timing controls correction allowance; the prepared power remains independent. */
internal fun billiardArcadeQuality(cursor: Float): Double {
    if(!cursor.isFinite()) return 0.0
    val error=abs(cursor-ARCADE_TIMING_TARGET)
    if(error<=ARCADE_TIMING_PERFECT) return 1.0
    return ((ARCADE_TIMING_REACH-error)/(ARCADE_TIMING_REACH-ARCADE_TIMING_PERFECT))
        .coerceIn(0f,1f).toDouble().pow(1.2)
}

/** A full cycle takes 1.8 s: bottom -> top -> bottom, independent of frame rate. */
internal fun billiardTimingPower(elapsedMillis: Long): Float {
    val phase=(elapsedMillis.coerceAtLeast(0)%1800)/900f
    return if(phase<=1f) phase else 2f-phase
}

/** Finger coordinates are in dp so the same physical gesture works on all densities. */
internal class BilliardStrokeGesture {
    private data class Sample(val y: Float,val time: Long)
    private val samples=ArrayDeque<Sample>()
    private var forwardTravel=0f
    var active=false
        private set

    fun begin(y: Float,time: Long) {
        cancel(); active=true; samples.addLast(Sample(y,time))
    }
    fun move(y: Float,time: Long): Float {
        if(!active || time<samples.last().time) return power()
        val previous=samples.last()
        val forward=previous.y-y
        forwardTravel=if(forward< -3f) 0f else forwardTravel+forward.coerceAtLeast(0f)
        if(time==previous.time) samples.removeLast()
        samples.addLast(Sample(y,time))
        while(samples.size>2 && samples.elementAt(1).time<=time-100) samples.removeFirst()
        // An old DOWN sample must not dilute a fast stroke after the player
        // held the cue still. Keep at least two recent samples to measure speed.
        if(samples.size>2 && samples.first().time<time-100) samples.removeFirst()
        return power()
    }
    private fun speed(): Float {
        if(samples.size<2) return 0f
        val first=samples.first(); val last=samples.last()
        val dt=last.time-first.time
        return if(dt>0) ((first.y-last.y)*1000/dt).coerceAtLeast(0f) else 0f
    }
    private fun power()=(speed()/1400f).coerceIn(0f,1f)
    fun release(y: Float,time: Long): Float? {
        if(!active) return null
        move(y,time)
        val result=power().takeIf { forwardTravel>=12f && speed()>=60f }
        cancel(); return result
    }
    fun cancel() { active=false; samples.clear(); forwardTravel=0f }
}
