package com.Atom2Universe.app.games.billiards.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.games.billiards.core.BilliardTable
import com.Atom2Universe.app.games.billiards.core.*
import com.Atom2Universe.app.games.billiards.render.BilliardRenderer
import kotlin.math.hypot
import kotlin.math.sqrt

enum class BilliardCallStep { NONE, BALL, POCKET, RAILS }

/** Projected call targets and placement guides follow the current camera. */
class BilliardPocketLabels(context: Context,private val scene: BilliardRenderer,private val table: BilliardTable) : View(context) {
    var session: BilliardSession?=null
    var step=BilliardCallStep.NONE
        set(value) { field=value; invalidate() }
    var candidates=emptySet<Int>()
    var draftBall=-1
    var draftPocket=-1
    var draftRails=emptyList<Int>()
    var ballSelected: ((Int)->Unit)?=null
    var pocketSelected: ((Int)->Unit)?=null
    var railSelected: ((Int)->Unit)?=null
    private val density=resources.displayMetrics.density
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign=Paint.Align.CENTER; textSize=12*resources.displayMetrics.density }
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO }
    // The overlay follows the camera, but only a new table image can move it: a cheap check
    // replaces the full-screen redraw that used to run ten times a second forever.
    private var drawnFrame=-1L
    private val follow=object: Runnable {
        override fun run() {
            if(!isAttachedToWindow) return
            if(scene.renderedFrames!=drawnFrame) invalidate()
            if(scene.camera.settling) postOnAnimation(this) else postDelayed(this,100)
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(follow); post(follow) }
    override fun onDetachedFromWindow() { removeCallbacks(follow); super.onDetachedFromWindow() }
    private fun ballRadius(ball: Ball,center: Pair<Float,Float>): Float {
        val edges=listOfNotNull(scene.screenPoint(ball.p+V3(ball.radius),ball.radius),
            scene.screenPoint(ball.p+V3(0.0,ball.radius),ball.radius),scene.screenPoint(ball.p,ball.radius*2))
        // Screen-space sphere radius from the projected three axes. Unlike a fixed
        // hit box, this still includes the visible ball when the camera zooms in.
        var xx=0f; var yy=0f; var xy=0f
        for(edge in edges) { val x=edge.first-center.first; val y=edge.second-center.second; xx+=x*x; yy+=y*y; xy+=x*y }
        return sqrt(((xx+yy+sqrt((xx-yy)*(xx-yy)+4*xy*xy))*.5f).coerceAtLeast(0f))
    }
    override fun onDraw(c: Canvas) {
        drawnFrame=scene.renderedFrames
        val s=session
        if(s?.match?.ballInHand==true && !s.world.moving && !s.replaying) drawPlacement(c,s)
        if(s==null || !s.ready) return
        val selectedBall=if(step!=BilliardCallStep.NONE) draftBall else s.nominated
        val eligible=if(step==BilliardCallStep.BALL) candidates else s.legalTargets().map { it.id }.toSet()
        paint.style=Paint.Style.STROKE
        for(ball in s.world.balls) if(ball.motion!=Motion.POCKETED && (ball.id in eligible || ball.id==selectedBall)) {
            val point=scene.screenPoint(ball.p,ball.radius) ?: continue
            val radius=ballRadius(ball,point).coerceAtLeast(5*density)
            paint.strokeWidth=if(ball.id==selectedBall || step==BilliardCallStep.BALL) 2*density else density
            paint.color=if(ball.id==selectedBall) 0xFFFFDCA0.toInt() else if(step==BilliardCallStep.BALL) 0xFFF1EBDD.toInt() else 0x8888D8B6.toInt()
            c.drawCircle(point.first,point.second,radius+4*density,paint)
            if(ball.id==selectedBall) c.drawCircle(point.first,point.second,radius+8*density,paint)
        }
        paint.style=Paint.Style.FILL
        val selectingPocket=step==BilliardCallStep.POCKET || step==BilliardCallStep.RAILS
        val selectedPocket=if(step!=BilliardCallStep.NONE) draftPocket else s.calledPocket
        // Snooker calls a colour, never a pocket. Old saves can contain pocket=0.
        val showPockets=selectingPocket || s.discipline==Discipline.ONE_POCKET ||
            (step==BilliardCallStep.NONE && s.discipline.family!=TableFamily.SNOOKER && selectedBall>=0 && selectedPocket>=0)
        if(!showPockets) return
        val r=if(step==BilliardCallStep.POCKET) 17*density else 12*density
        for(p in table.pockets) {
            val point=scene.screenPoint(p.center,0.0) ?: continue
            val owned=s.discipline==Discipline.ONE_POCKET && p.id==s.pocketFor(s.match.player)
            val chosen=p.id==selectedPocket || owned
            if(!selectingPocket && !chosen && s.discipline!=Discipline.ONE_POCKET) continue
            paint.color=if(chosen) 0xAA9A7536.toInt() else 0x6617241F
            c.drawCircle(point.first,point.second,r,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=2*density
            paint.color=if(chosen) 0xFFFFDCA0.toInt() else 0xFFF1EBDD.toInt()
            c.drawCircle(point.first,point.second,r,paint)
            if(chosen) {
                c.drawLine(point.first-6*density,point.second,point.first-1*density,point.second+5*density,paint)
                c.drawLine(point.first-1*density,point.second+5*density,point.first+7*density,point.second-5*density,paint)
            }
            paint.style=Paint.Style.FILL
        }
        if(step==BilliardCallStep.RAILS) table.rails.take(6).forEachIndexed { index, rail ->
            val a=scene.screenPoint(rail.a,.060) ?: return@forEachIndexed
            val b=scene.screenPoint(rail.b,.060) ?: return@forEachIndexed
            val point=scene.screenPoint((rail.a+rail.b)*.5,.060) ?: return@forEachIndexed
            paint.strokeWidth=if(index in draftRails) 5*density else 3*density
            paint.color=if(index in draftRails) 0xFFFFDCA0.toInt() else 0xAAF1EBDD.toInt()
            c.drawLine(a.first,a.second,b.first,b.second,paint)
            paint.color=0xFFFFDCA0.toInt()
            val orders=draftRails.indices.filter { draftRails[it]==index }.map { context.getString(R.string.billiard_number,it+1) }
            if(orders.isNotEmpty()) c.drawText(orders.joinToString(context.getString(R.string.billiard_list_separator)),point.first,point.second-8*density,paint)
        }
    }
    private fun drawPlacement(c: Canvas,s: BilliardSession) {
        val points=when(s.match.handRegion) {
            HandRegion.D -> (0..48).map { val a=Math.PI/2+it*Math.PI/48; V3(table.headLine+kotlin.math.cos(a)*table.dRadius,table.width/2+kotlin.math.sin(a)*table.dRadius) }
            HandRegion.HEAD -> listOf(V3(),V3(table.headLine),V3(table.headLine,table.width),V3(0.0,table.width))
            HandRegion.LOWER_HALF -> listOf(V3(),V3(table.length/2),V3(table.length/2,table.width),V3(0.0,table.width))
            HandRegion.OPPOSITE_HALF -> {
                val opponent=s.world.balls.firstOrNull { it.id==1-s.match.player }
                val left=if(opponent!=null && opponent.p.x<table.length/2) table.length/2 else 0.0
                listOf(V3(left),V3(left+table.length/2),V3(left+table.length/2,table.width),V3(left,table.width))
            }
            HandRegion.START_SPOTS -> {
                for(side in listOf(-1,1)) {
                    val point=scene.screenPoint(V3(table.length/4,table.width/2+side*table.startSpotOffset)) ?: continue
                    paint.color=0xAA7FE3CE.toInt(); c.drawCircle(point.first,point.second,8*resources.displayMetrics.density,paint)
                }
                emptyList()
            }
            else -> emptyList()
        }.mapNotNull { scene.screenPoint(it) }
        if(points.size>2) {
            val path=android.graphics.Path(); path.moveTo(points.first().first,points.first().second)
            points.drop(1).forEach { path.lineTo(it.first,it.second) }; path.close()
            paint.color=0x334FD8C1; c.drawPath(path,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=resources.displayMetrics.density*1.5f; paint.color=0xAA7FE3CE.toInt(); c.drawPath(path,paint); paint.style=Paint.Style.FILL
        }
    }
    /** Only taps are forwarded here; the GLSurfaceView keeps drag, pan and pinch. */
    fun selectAt(x: Float,y: Float) {
        val s=session ?: return
        if(!s.ready) return
        fun closest(points: List<Pair<Int,Pair<Float,Float>>>,radius: Float): Int? = points
            .map { (id,p) -> id to hypot(p.first-x,p.second-y) }.minByOrNull { it.second }
            ?.takeIf { it.second<=radius }?.first
        when(step) {
            BilliardCallStep.BALL -> s.world.balls.filter { it.id in candidates && it.motion!=Motion.POCKETED }
                .mapNotNull { b -> scene.screenPoint(b.p,b.radius)?.let { point ->
                    val distance=hypot(point.first-x,point.second-y)
                    if(distance<=(ballRadius(b,point)+5*density).coerceAtLeast(28*density)) b.id to distance else null
                } }.minByOrNull { it.second }?.let { ballSelected?.invoke(it.first) }
            BilliardCallStep.POCKET -> closest(table.pockets.mapNotNull { p -> scene.screenPoint(p.center,0.0)?.let { p.id to it } },30*density)
                ?.let { pocketSelected?.invoke(it) }
            BilliardCallStep.RAILS -> {
                table.rails.take(6).mapIndexedNotNull { index,rail ->
                    val a=scene.screenPoint(rail.a,.060) ?: return@mapIndexedNotNull null
                    val b=scene.screenPoint(rail.b,.060) ?: return@mapIndexedNotNull null
                    val dx=b.first-a.first; val dy=b.second-a.second
                    val t=(((x-a.first)*dx+(y-a.second)*dy)/(dx*dx+dy*dy).coerceAtLeast(1f)).coerceIn(0f,1f)
                    index to hypot(x-a.first-t*dx,y-a.second-t*dy)
                }.minByOrNull { it.second }?.takeIf { it.second<24*density }?.let { railSelected?.invoke(it.first) }
            }
            BilliardCallStep.NONE -> Unit
        }
    }
}
