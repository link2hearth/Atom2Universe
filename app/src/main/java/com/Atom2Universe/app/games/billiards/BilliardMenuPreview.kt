package com.Atom2Universe.app.games.billiards

import android.content.Context
import android.graphics.Rect
import android.widget.FrameLayout
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.render.*
import com.Atom2Universe.app.games.billiards.ui.BilliardSurface

internal data class BilliardPreviewSpec(val discipline: Discipline=Discipline.EIGHT,
    val room: BilliardRoom=BilliardRoom.CLUB,val exercise: Int=-1,val spread: Boolean=false)

/** Isolated previews: never own the live session, save files, timers or AI workers. */
internal class BilliardMenuPreview(private val context: Context,var preferences: BilliardConfig) {
    val root=FrameLayout(context)
    private var surface: BilliardSurface?=null
    private var config: BilliardConfig?=null
    private var spec: BilliardPreviewSpec?=null
    private var area=Rect()
    private var resumed=false
    private var portrait=false
    init { root.addOnLayoutChangeListener { _,_,_,_,_,_,_,_,_ -> frameViewport() } }

    fun show(next: BilliardPreviewSpec) {
        val c=preferences.copy(discipline=next.discipline,room=next.room,mode=PlayMode.PRACTICE,cloth=1,
            tableSize=BilliardTableSize.defaultFor(next.discipline.family))
        display(next,c)
    }
    fun showTable(c: BilliardConfig) = display(BilliardPreviewSpec(c.discipline,c.room),c)
    private fun display(next: BilliardPreviewSpec,c: BilliardConfig) {
        if(next==spec && c==config) return
        spec=next
        val preview=BilliardSession(c.discipline,c.mode,c.cloth,c.tableSize)
        if(next.exercise>=0) BilliardChallenges.all[next.exercise].install(preview)
        else if(next.spread && next.discipline.family!=TableFamily.CAROM) BilliardChallenges.spread(preview,0)
        val old=config
        if(old==null || old.discipline!=c.discipline || old.room!=c.room || old.tableSize!=c.tableSize || old.cloth!=c.cloth) {
            surface?.let { it.preserveEGLContextOnPause=false; it.onPause() }
            root.removeAllViews()
            val renderer=BilliardRenderer(c,preview.table) { context.getString(R.string.billiard_number,it) }.apply { showCue=false }
            surface=BilliardSurface(context,renderer).apply {
                // This surface is decorative; menu chrome consumes all interactions above it.
                setOnTouchListener { _,_ -> true }; importantForAccessibility=android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            root.addView(surface,FrameLayout.LayoutParams(-1,-1))
            if(!resumed) surface?.onPause()
        }
        config=c
        val renderer=surface!!.scene
        renderer.rotationStripes=c.rotationStripes; renderer.cueTint=c.cueTint; renderer.look=c.look
        renderer.frame=BilliardFrame(preview.world.balls.map { it.copyDeep() },preview.world.pins.toList(),preview.cueId,Shot(0.0),false)
        renderer.targetZone=next.exercise.takeIf { it>=0 }?.let { BilliardChallenges.all[it].zone(preview.table) }
        renderer.camera.showcase=false
        if(next.exercise>=0) renderer.camera.top(portrait) else renderer.camera.overview()
        frameViewport(); if(resumed) surface?.wakeCamera()
    }
    fun viewport(rect: Rect) { if(area!=rect) { area=Rect(rect); frameViewport() } }
    fun appearance(value: BilliardConfig) {
        preferences=value
        config=config?.copy(rotationStripes=value.rotationStripes,finish=value.finish,look=value.look)
        surface?.scene?.let { it.rotationStripes=value.rotationStripes; it.cueTint=value.cueTint; it.look=value.look }
        if(resumed) surface?.requestRender()
    }
    private fun frameViewport() {
        if(root.width<=0 || root.height<=0 || area.isEmpty) return
        val renderer=surface?.scene ?: return
        val nextPortrait=area.height()>area.width()
        if(portrait!=nextPortrait && spec?.exercise?.let { it>=0 }==true) renderer.camera.top(nextPortrait)
        portrait=nextPortrait
        renderer.hudTop=area.top.toFloat(); renderer.hudLeft=area.left.toFloat()
        renderer.hudBottom=(root.height-area.bottom).coerceAtLeast(0).toFloat()
        renderer.hudRight=(root.width-area.right).coerceAtLeast(0).toFloat()
        renderer.camera.reframe()
        if(resumed) surface?.wakeCamera()
    }
    fun resume() { if(!resumed) { resumed=true; surface?.onResume(); surface?.wakeCamera() } }
    fun pause() { if(resumed) { resumed=false; surface?.onPause() } }
    fun close() { resumed=false; surface?.let { it.preserveEGLContextOnPause=false; it.onPause() }; root.removeAllViews(); surface=null }
}
