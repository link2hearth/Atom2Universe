package com.Atom2Universe.app.games.billiards.ui

import com.Atom2Universe.app.games.billiards.core.BilliardPlanner.PlannedShot
import com.Atom2Universe.app.games.billiards.core.Shot
import kotlin.math.*

/** Presentation only: intermediate aims never change the shot used by the referee. */
class BilliardComputerAim(initial: Shot, firstTarget: Shot) {
    var visualShot=initial
        private set
    private var target=firstTarget
    var selected: PlannedShot?=null
        private set
    var placementApplied=false
    private var elapsed=0.0
    private var settling=0.0

    fun consider(candidate: PlannedShot) {
        if(selected==null) target=candidate.shot
    }

    fun prepare(choice: PlannedShot) {
        selected=choice; target=choice.shot; settling=0.0
    }

    // Small practice strokes during the search, then one quick stroke before striking.
    val pullback: Double get() = if(selected==null) .025*(1-cos(elapsed*2*PI/.95))
        else .035*(1-cos(settling.coerceAtMost(.35)*2*PI/.35))

    /** Uses active frame time so a paused activity or open dialog cannot finish the stroke. */
    fun advance(seconds: Double): Boolean {
        val dt=seconds.coerceIn(0.0,.05)
        elapsed+=dt
        if(selected!=null) settling+=dt
        val blend=1-exp(-16*dt)
        fun approach(a: Double,b: Double)=a+(b-a)*blend
        val turn=atan2(sin(target.angle-visualShot.angle),cos(target.angle-visualShot.angle))
        visualShot=target.copy(angle=visualShot.angle+turn*blend,
            speed=approach(visualShot.speed,target.speed),side=approach(visualShot.side,target.side),
            top=approach(visualShot.top,target.top),elevation=approach(visualShot.elevation,target.elevation))
        // A player looks at the table before striking, even when the answer comes fast.
        return selected!=null && elapsed>=1.2 && settling>=.35 && abs(turn)<.01
    }
}
