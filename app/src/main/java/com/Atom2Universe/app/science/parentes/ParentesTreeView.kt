package com.Atom2Universe.app.science.parentes

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.Atom2Universe.app.R
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.*

/** Geometry and text are prepared once per scene, independently of the source topology. */
class ParentesTreeView(context:Context):View(context) {
    private val palette=SciencePalette(context)
    private val density=resources.displayMetrics.density
    private val fontScale=resources.configuration.fontScale
    private val labelSize=13f*resources.displayMetrics.scaledDensity
    private val text=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=labelSize }
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
    private val dot=Paint(Paint.ANTI_ALIAS_FLAG)
    // Points with a round (or square) cap draw the same discs (or squares) as drawCircle, in one call per group.
    private val point=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE }
    private val paper=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=palette.background }
    private var dashed=DashPathEffect(floatArrayOf(9f*density,7f*density),0f)
    private var dashZoom=-1f
    private val firstColor=palette.mark(0xff4cbeac.toInt())
    private val secondColor=palette.mark(0xffe1a65c.toInt())
    internal class Mark(val id:String,val x:Float,val y:Float,val label:String,val color:Int,val dotColor:Int,val ink:Int,
        val species:Boolean,val priority:Int,val width:Float,val square:Boolean) {
        val hit=RectF()
        val bounds=RectF()
        private var ellipsized=""
        private var shortRoom=-1f
        /** Ellipsized once per available width, not once per frame. */
        fun short(paint:TextPaint,room:Float):String {
            if(room!=shortRoom) { ellipsized=TextUtils.ellipsize(label,paint,room,TextUtils.TruncateAt.END).toString(); shortRoom=room }
            return ellipsized
        }
    }
    /** Branches sharing a color, a highlight state and a sibling layer, merged into one path. */
    internal class Stroke(val path:Path,val color:Int,val highlighted:Boolean,val dashed:Boolean)
    private data class StrokeKey(val color:Int,val highlighted:Boolean,val dashed:Boolean,val layer:Int)
    /** Dot centres (x, y pairs, scene units) sharing a color and a size rule. */
    internal class Dots(val points:FloatArray,val color:Int,val kind:Int,val square:Boolean)
    /** Everything derived from a scene; built off the UI thread by [prepare]. */
    class Prepared internal constructor(internal val scene:TreeScene,internal val radial:Boolean,internal val leafCount:Int,
        internal val marks:List<Mark>,internal val byId:Map<String,Mark>,internal val strokes:List<Stroke>,
        internal val dots:List<Dots>,internal val labelSpacing:Float,internal val maxZoom:Float,internal val extent:RectF)
    private var marks:List<Mark> = emptyList()
    private var byId:Map<String,Mark> = emptyMap()
    private var strokes:List<Stroke> = emptyList()
    private var dots:List<Dots> = emptyList()
    private var scene:TreeScene?=null
    private var radial=true
    private var leafCount=1
    private val drawnLabels=ArrayList<RectF>()
    private var zoom=1f
    private var maxZoom=4f
    private var labelSpacing=0f
    private var tx=0f
    private var ty=0f
    private var animator:ValueAnimator?=null
    private val extent=RectF()
    private var selectedMark:Mark?=null
    var onSelect:(String)->Unit={}
    var selected:String?=null
        set(value) { field=value; selectedMark=value?.let { byId[it] }; invalidate() }

    init {
        setBackgroundColor(palette.background)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable=true
    }

    fun show(scene:TreeScene,tree:LifeTree,groups:Set<String>,positions:Map<String,TreeLayout.RadialPoint>?=null,label:(String)->String)=
        show(prepare(scene,tree,groups,positions,label))

    /** Touches no view state: safe on a background thread. */
    fun prepare(scene:TreeScene,tree:LifeTree,groups:Set<String>,positions:Map<String,TreeLayout.RadialPoint>?=null,label:(String)->String):Prepared {
        val radial=scene.common==null
        val leafCount=scene.parents.keys.count { tree.nodes.getValue(it).species }.coerceAtLeast(1)
        val circular=if(radial) positions ?: TreeLayout.radial(scene) else emptyMap()
        val horizontal=if(radial) emptyMap() else TreeLayout.place(scene,94f*fontScale)
        // These colors follow actual ancestor IDs; they never create editorial branches.
        val hues=mapOf("Mammalia" to 0xff73c9d0.toInt(),"Aves" to 0xff71a8e8.toInt(),
            "Insecta" to 0xffe4ac64.toInt(),"Metazoa" to 0xff87bfc8.toInt(),
            "Chloroplastida" to 0xff83c67a.toInt(),"Fungi" to 0xffb298dc.toInt(),
            "Bacteria" to 0xffe08b84.toInt(),"Archaea" to 0xffd8b273.toInt(),"Eukaryota" to 0xff8ca9be.toInt())
            .mapValues { palette.mark(it.value) }
        fun color(id:String)=tree.ancestors(id).firstNotNullOfOrNull { hues[tree.nodes.getValue(it).scientific] } ?: palette.secondary
        val major=setOf("Mammalia","Aves","Insecta","Chloroplastida","Fungi","Bacteria","Archaea")
        val measure=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=labelSize }
        val marks=scene.parents.keys.map { id ->
            val node=tree.nodes.getValue(id)
            val name=label(id)
            val display=when(id) {
                scene.a -> context.getString(R.string.pt_a,name)
                scene.b -> context.getString(R.string.pt_b,name)
                else -> name
            }
            val priority=when { id==scene.common || id==scene.a || id==scene.b -> 0
                node.scientific in major -> 1
                id in groups -> 2
                node.species -> 4
                else -> 3 }
            measure.typeface=if(!node.species) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val hue=color(id)
            Mark(id,(circular[id]?.x ?: horizontal.getValue(id).x)*density,
                (circular[id]?.y ?: horizontal.getValue(id).y)*density,display,hue,
                when { id==scene.common -> palette.accent; id in scene.second -> secondColor; id in scene.first -> firstColor; else -> hue },
                if(node.species) palette.text else palette.ink(hue,palette.background),
                node.species,priority,measure.measureText(display),id in scene.second && id!=scene.common)
        }.sortedBy { it.priority }
        // Leave enough room for complete horizontal labels even in the densest sector.
        val labelSpacing=2f*(marks.maxOfOrNull { it.width } ?: 0f)+24f*density
        val maxZoom=TreeZoom.limit(marks.filter { it.label.isNotEmpty() }.map { TreeZoom.Point(it.x,it.y) },
            labelSpacing,measure.descent()-measure.ascent()+12f*density)
        val indexed=marks.associateBy { it.id }
        val strokes=LinkedHashMap<StrokeKey,Stroke>()
        // Siblings leaving one side of a parent overlap there; each goes to its own layer so the
        // translucent strokes still build up as with one drawPath per branch. Other branches meet
        // at nodes, under their dots (and, far zoomed out, along a few sector edges).
        val siblings=HashMap<String,IntArray>()
        val oval=RectF()
        for((id,parent) in scene.parents) {
            parent ?: continue
            val a=indexed.getValue(parent); val b=indexed.getValue(id)
            val first=id in scene.first; val second=id in scene.second
            val color=when { second -> secondColor; first -> firstColor; else -> b.color }
            // Around the centre (radius 0) every child starts at the same point: one shared count.
            val side=if(radial && circular.getValue(parent).radius>0f &&
                circular.getValue(id).angle<circular.getValue(parent).angle) 0 else 1
            val layer=siblings.getOrPut(parent) { IntArray(2) }[side]++
            val key=StrokeKey(color,first || second,second,layer)
            val path=strokes.getOrPut(key) { Stroke(Path(),color,first || second,second) }.path
            path.moveTo(a.x,a.y)
            if(radial) {
                val from=circular.getValue(parent); val to=circular.getValue(id)
                val r=from.radius*density
                // Circular arcs stay inside the parent's angular sector. Unlike
                // free cubic curves, they cannot cut across neighbouring clades.
                if(r>0f) path.arcTo(oval.apply { set(-r,-r,r,r) },Math.toDegrees(from.angle.toDouble()).toFloat(),
                    Math.toDegrees((to.angle-from.angle).toDouble()).toFloat(),false)
                path.lineTo(b.x,b.y)
            } else {
                val middle=(a.x+b.x)*.5f
                path.cubicTo(middle,a.y,middle,b.y,b.x,b.y)
            }
        }
        val dotGroups=LinkedHashMap<Triple<Int,Int,Boolean>,ArrayList<Mark>>()
        for(m in marks) {
            val kind=when { m.species -> LEAF; m.label.isEmpty() -> PLAIN; m.priority<=2 -> NAMED; else -> NAMED_DETAIL }
            dotGroups.getOrPut(Triple(m.dotColor,kind,m.square)) { ArrayList() }.add(m)
        }
        val dots=dotGroups.map { (key,members) ->
            val points=FloatArray(members.size*2)
            members.forEachIndexed { i,m -> points[2*i]=m.x; points[2*i+1]=m.y }
            Dots(points,key.first,key.second,key.third)
        }
        val extent=RectF(marks.minOf { it.x },marks.minOf { it.y },marks.maxOf { it.x },marks.maxOf { it.y })
        return Prepared(scene,radial,leafCount,marks,indexed,strokes.values.toList(),dots,labelSpacing,maxZoom,extent)
    }

    fun show(prepared:Prepared) {
        animator?.cancel()
        scene=prepared.scene
        radial=prepared.radial
        leafCount=prepared.leafCount
        marks=prepared.marks
        byId=prepared.byId
        strokes=prepared.strokes
        dots=prepared.dots
        labelSpacing=prepared.labelSpacing
        maxZoom=prepared.maxZoom
        extent.set(prepared.extent)
        selectedMark=selected?.let { byId[it] }
        post { fit(false) }
    }

    fun fit(animate:Boolean=true) {
        if(marks.isEmpty() || width==0 || height==0) return
        val margin=if(radial) 32f*density else 24f*density
        val labelRoom=if(radial) 0f else min(width*.48f,marks.maxOf { it.width }+20*density)
        val target=min((width-margin*2-labelRoom)/max(extent.width(),density),
            (height-margin*2)/max(extent.height(),density)).coerceIn(.04f,1.2f)
        moveCamera(target,(width-labelRoom)*.5f-extent.centerX()*target,height*.5f-extent.centerY()*target,animate)
    }
    fun zoomBy(factor:Float) {
        zoomAt(factor,width*.5f,height*.5f)
    }
    private fun zoomAt(factor:Float,x:Float,y:Float,recenter:Boolean=false) {
        val next=(zoom*factor).coerceIn(TreeZoom.MIN,maxZoom)
        val camera=TreeZoom.Camera(zoom,tx,ty).focus(next,x,y,
            if(recenter)width*.5f else x,if(recenter)height*.5f else y)
        moveCamera(camera.zoom,camera.x,camera.y,true)
    }
    fun reveal(id:String,minimumZoom:Float=zoom) {
        val m=byId[id] ?: return
        val distance=marks.asSequence().filter { it.id!=id && it.label.isNotEmpty() }
            .map { hypot(it.x-m.x,it.y-m.y) }.filter { it>0f }.minOrNull() ?: Float.POSITIVE_INFINITY
        val next=max(minimumZoom,TreeZoom.readable(distance,labelSpacing,text.descent()-text.ascent()+12f*density)).coerceAtMost(maxZoom)
        selected=id
        // Centre the node together with its name, not just the dot.
        val offset=min(m.width+18f*density,width*.65f)*.5f
        val center=width*.5f+if(radial && m.x<0)offset else -offset
        moveCamera(next,center-m.x*next,height*.5f-m.y*next,true)
    }
    private fun moveCamera(target:Float,x:Float,y:Float,animate:Boolean) {
        animator?.cancel()
        if(!animate) { zoom=target; tx=x; ty=y; invalidate(); return }
        val oldZ=zoom; val oldX=tx; val oldY=ty
        animator=ValueAnimator.ofFloat(0f,1f).apply {
            duration=220
            addUpdateListener {
                val f=it.animatedValue as Float
                zoom=oldZ+(target-oldZ)*f; tx=oldX+(x-oldX)*f; ty=oldY+(y-oldY)*f; invalidate()
            }
            start()
        }
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if(oldw==0 || oldh==0) fit(false)
        else {
            // Opening a species preview must not discard the user's zoom and position.
            animator?.cancel(); tx+=(w-oldw)*.5f; ty+=(h-oldh)*.5f
            invalidate()
        }
    }

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(scene==null) return
        if(dashZoom!=zoom) { dashed=DashPathEffect(floatArrayOf(8f*density/zoom,6f*density/zoom),0f); dashZoom=zoom }
        canvas.save()
        canvas.translate(tx,ty); canvas.scale(zoom,zoom)
        for(b in strokes) {
            edge.color=b.color
            edge.alpha=if(radial) 165 else 230
            edge.strokeWidth=(if(b.highlighted) 2.5f else 1.2f)*density/zoom
            edge.pathEffect=if(b.dashed) dashed else null
            canvas.drawPath(b.path,edge)
        }
        // All selected species remain visible as leaves at overview scale.
        val leafRadius=if(radial) (2f*PI.toFloat()*640f*zoom/leafCount*.33f).coerceIn(.7f,2.8f) else 2.8f
        for(d in dots) {
            val radius=when(d.kind) {
                LEAF -> leafRadius
                NAMED -> 3.5f
                NAMED_DETAIL -> if(zoom>=.65f) 3.5f else 1.4f
                else -> 1.4f
            }*density
            point.color=d.color
            point.strokeCap=if(d.square) Paint.Cap.SQUARE else Paint.Cap.ROUND
            point.strokeWidth=2f*radius/zoom
            canvas.drawPoints(d.points,point)
        }
        canvas.restore()
        for(m in marks) {
            m.bounds.setEmpty()
            val x=m.x*zoom+tx; val y=m.y*zoom+ty
            if(m.label.isEmpty() || x<0 || x>width || y<0 || y>height) m.hit.setEmpty()
            else m.hit.set(x-12f*density,y-12f*density,x+12f*density,y+12f*density)
        }
        val chosen=selectedMark
        if(chosen!=null) {
            val x=chosen.x*zoom+tx; val y=chosen.y*zoom+ty
            if(x>=0 && x<=width && y>=0 && y<=height) {
                val radius=5f*density
                dot.color=chosen.dotColor
                dot.style=Paint.Style.STROKE; dot.strokeWidth=1.5f*density
                canvas.drawCircle(x,y,radius+4f*density,dot); dot.style=Paint.Style.FILL
                if(chosen.square) canvas.drawRect(x-radius,y-radius,x+radius,y+radius,dot)
                else canvas.drawCircle(x,y,radius,dot)
            }
        }
        drawnLabels.clear()
        if(chosen!=null) drawLabel(canvas,chosen)
        for(m in marks) if(m!==chosen) drawLabel(canvas,m)
    }

    private fun drawLabel(canvas:Canvas,m:Mark) {
        if(m.label.isEmpty()) return
        val essential=m===selectedMark || m.priority==0
        if(radial && m.species && zoom < .38f && marks.size>40 && !essential) return
        if(radial && m.priority==3 && zoom < .65f && !essential) return
        val x=m.x*zoom+tx; val y=m.y*zoom+ty
        if(y < -text.textSize || y > height+text.textSize) return
        text.typeface=if(m.species) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
        val room=(width-12f*density).coerceAtLeast(density)
        // At detail scale names remain complete and attached to their node. Panning can
        // reveal a long name even when its dot is outside the viewport.
        val detail=zoom>=.65f
        val label=if(detail)m.label else m.short(text,room)
        val length=if(detail)m.width else min(m.width,room)
        val anchor=if(radial && m.x<0)x-length-9f*density else x+9f*density
        val left=if(detail)anchor else anchor.coerceIn(6f*density,max(6f*density,width-length-6f*density))
        if(left+length<0 || left>width || (!detail && (x<0 || x>width)))return
        val top=y-(text.descent()-text.ascent())*.5f-3*density
        m.bounds.set(left-3*density,top,left+length+3*density,top+text.descent()-text.ascent()+6*density)
        if(m.bounds.top<0 || m.bounds.bottom>height || drawnLabels.any { RectF.intersects(it,m.bounds) }) {
            m.bounds.setEmpty(); return
        }
        drawnLabels.add(m.bounds)
        // Quiet backing keeps letters readable over branches without turning labels into buttons.
        paper.alpha=235
        canvas.drawRoundRect(m.bounds,4f*density,4f*density,paper)
        text.color=m.ink
        canvas.drawText(label,left,y-(text.ascent()+text.descent())*.5f,text)
        m.hit.union(m.bounds)
        m.hit.inset(0f,-6f*density)
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        private var lastFocusX=0f
        private var lastFocusY=0f
        override fun onScaleBegin(detector:ScaleGestureDetector):Boolean {
            animator?.cancel()
            lastFocusX=detector.focusX; lastFocusY=detector.focusY
            return true
        }
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (zoom * detector.scaleFactor).coerceIn(TreeZoom.MIN,maxZoom)
            val camera=TreeZoom.Camera(zoom,tx,ty).focus(next,lastFocusX,lastFocusY,detector.focusX,detector.focusY)
            lastFocusX=detector.focusX; lastFocusY=detector.focusY
            tx=camera.x; ty=camera.y; zoom=camera.zoom
            invalidate()
            return true
        }
    }).apply { isQuickScaleEnabled=false }
    private var doubleDragging=false
    private var doubleStartX=0f
    private var doubleStartY=0f
    private var doubleLastX=0f
    private var doubleLastY=0f
    private var doubleMoved=false
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress && !doubleDragging) { tx -= distanceX; ty -= distanceY; invalidate() }
            return true
        }
        override fun onDoubleTap(e:MotionEvent):Boolean {
            animator?.cancel()
            doubleDragging=true; doubleMoved=false
            doubleStartX=e.x; doubleStartY=e.y; doubleLastX=e.x; doubleLastY=e.y
            return true
        }
        override fun onDoubleTapEvent(e:MotionEvent):Boolean {
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    doubleStartX=e.x; doubleStartY=e.y; doubleLastX=e.x; doubleLastY=e.y
                }
                MotionEvent.ACTION_MOVE -> if(!scaleDetector.isInProgress) {
                    if(hypot(e.x-doubleStartX,e.y-doubleStartY)>android.view.ViewConfiguration.get(context).scaledTouchSlop)doubleMoved=true
                    if(doubleMoved) { tx+=e.x-doubleLastX; ty+=e.y-doubleLastY; invalidate() }
                    doubleLastX=e.x; doubleLastY=e.y
                }
                MotionEvent.ACTION_UP -> {
                    if(!doubleMoved && !scaleDetector.isInProgress) {
                        val nearest=marks.filter { it.label.isNotEmpty() }.minByOrNull { hypot(it.x*zoom+tx-e.x,it.y*zoom+ty-e.y) }
                        if(nearest!=null && hypot(nearest.x*zoom+tx-e.x,nearest.y*zoom+ty-e.y)<=32f*density)reveal(nearest.id,zoom*2f)
                        else zoomAt(2f,e.x,e.y,true)
                    }
                    doubleDragging=false
                }
                MotionEvent.ACTION_CANCEL -> doubleDragging=false
            }
            return true
        }
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            val hit = marks.filter { it.hit.contains(e.x,e.y) }.minByOrNull {
                kotlin.math.hypot(it.x*zoom+tx-e.x,it.y*zoom+ty-e.y)
            }
            hit?.let { selected=it.id; onSelect(it.id) }
            performClick()
            return true
        }
    }).apply { setIsLongpressEnabled(false) }
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { animator?.cancel(); parent?.requestDisallowInterceptTouchEvent(true) }
        if(event.actionMasked==MotionEvent.ACTION_POINTER_DOWN) { doubleMoved=true; doubleDragging=false }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
            doubleDragging=false
            parent?.requestDisallowInterceptTouchEvent(false)
        }
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }

    private companion object {
        // Dot size rules, see onDraw.
        const val LEAF=0
        const val NAMED=1
        const val NAMED_DETAIL=2
        const val PLAIN=3
    }
}
