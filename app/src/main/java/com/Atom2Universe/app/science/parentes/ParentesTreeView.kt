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
    private val text=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textSize=13f*resources.displayMetrics.scaledDensity }
    private val edge=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
    private val dot=Paint(Paint.ANTI_ALIAS_FLAG)
    private val paper=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=palette.background }
    private var dashed=DashPathEffect(floatArrayOf(9f*density,7f*density),0f)
    private var dashZoom=-1f
    private val firstColor=palette.mark(0xff4cbeac.toInt())
    private val secondColor=palette.mark(0xffe1a65c.toInt())
    private data class Mark(val id:String,val x:Float,val y:Float,val label:String,val color:Int,
        val species:Boolean,val priority:Int,val width:Float,val hit:RectF=RectF(),val bounds:RectF=RectF())
    private data class Branch(val id:String,val path:Path,val color:Int,val bounds:RectF=RectF())
    private var marks:List<Mark> = emptyList()
    private var branches:List<Branch> = emptyList()
    private var scene:TreeScene?=null
    private var radial=true
    private var leafCount=1
    private val drawnLabels=ArrayList<RectF>()
    private var zoom=1f
    private var tx=0f
    private var ty=0f
    private var animator:ValueAnimator?=null
    private val extent=RectF()
    var onSelect:(String)->Unit={}
    var selected:String?=null
        set(value) { field=value; invalidate() }

    init {
        setBackgroundColor(palette.background)
        importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        isClickable=true
    }

    fun show(scene:TreeScene,tree:LifeTree,groups:Set<String>,prepared:Map<String,TreeLayout.RadialPoint>?=null,label:(String)->String) {
        animator?.cancel()
        this.scene=scene
        radial=scene.common==null
        leafCount=scene.parents.keys.count { tree.nodes.getValue(it).species }.coerceAtLeast(1)
        val circular=if(radial) prepared ?: TreeLayout.radial(scene) else emptyMap()
        val horizontal=if(radial) emptyMap() else TreeLayout.place(scene,94f*fontScale)
        // These colors follow actual ancestor IDs; they never create editorial branches.
        val hues=mapOf("Mammalia" to 0xff73c9d0.toInt(),"Aves" to 0xff71a8e8.toInt(),
            "Insecta" to 0xffe4ac64.toInt(),"Metazoa" to 0xff87bfc8.toInt(),
            "Chloroplastida" to 0xff83c67a.toInt(),"Fungi" to 0xffb298dc.toInt(),
            "Bacteria" to 0xffe08b84.toInt(),"Archaea" to 0xffd8b273.toInt(),"Eukaryota" to 0xff8ca9be.toInt())
            .mapValues { palette.mark(it.value) }
        fun color(id:String)=tree.ancestors(id).firstNotNullOfOrNull { hues[tree.nodes.getValue(it).scientific] } ?: palette.secondary
        val major=setOf("Mammalia","Aves","Insecta","Chloroplastida","Fungi","Bacteria","Archaea")
        marks=scene.parents.keys.map { id ->
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
            text.typeface=if(!node.species) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            Mark(id,(circular[id]?.x ?: horizontal.getValue(id).x)*density,
                (circular[id]?.y ?: horizontal.getValue(id).y)*density,display,color(id),node.species,priority,text.measureText(display))
        }.sortedBy { it.priority }
        val indexed=marks.associateBy { it.id }
        branches=scene.parents.mapNotNull { (id,parent) ->
            parent ?: return@mapNotNull null
            val a=indexed.getValue(parent); val b=indexed.getValue(id)
            Branch(id,Path().apply {
                moveTo(a.x,a.y)
                if(radial) {
                    val from=circular.getValue(parent); val to=circular.getValue(id)
                    val r=from.radius*density
                    // Circular arcs stay inside the parent's angular sector. Unlike
                    // free cubic curves, they cannot cut across neighbouring clades.
                    if(r>0f) arcTo(RectF(-r,-r,r,r),Math.toDegrees(from.angle.toDouble()).toFloat(),
                        Math.toDegrees((to.angle-from.angle).toDouble()).toFloat(),false)
                    lineTo(b.x,b.y)
                } else {
                    val middle=(a.x+b.x)*.5f
                    cubicTo(middle,a.y,middle,b.y,b.x,b.y)
                }
            },b.color).also { it.path.computeBounds(it.bounds,true) }
        }
        extent.set(marks.minOf { it.x },marks.minOf { it.y },marks.maxOf { it.x },marks.maxOf { it.y })
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
        val next=(zoom*factor).coerceIn(.04f,4f)
        moveCamera(next,width*.5f-(width*.5f-tx)*next/zoom,height*.5f-(height*.5f-ty)*next/zoom,true)
    }
    fun reveal(id:String) {
        val m=marks.firstOrNull { it.id==id } ?: return
        val next=max(zoom,.65f)
        selected=id
        moveCamera(next,width*.5f-m.x*next,height*.5f-m.y*next,true)
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
        val s=scene ?: return
        if(dashZoom!=zoom) { dashed=DashPathEffect(floatArrayOf(8f*density/zoom,6f*density/zoom),0f); dashZoom=zoom }
        canvas.save()
        canvas.translate(tx,ty); canvas.scale(zoom,zoom)
        for(b in branches) {
            if(b.bounds.right*zoom+tx < -4f*density || b.bounds.left*zoom+tx > width+4f*density ||
                b.bounds.bottom*zoom+ty < -4f*density || b.bounds.top*zoom+ty > height+4f*density) continue
            val a=b.id in s.first; val c=b.id in s.second
            edge.color=when { c -> secondColor; a -> firstColor; else -> b.color }
            edge.alpha=if(radial) 165 else 230
            edge.strokeWidth=(if(a || c) 2.5f else 1.2f)*density/zoom
            edge.pathEffect=if(c) dashed else null
            canvas.drawPath(b.path,edge)
        }
        canvas.restore()
        for(m in marks) { m.hit.setEmpty(); m.bounds.setEmpty() }
        // All selected species remain visible as leaves at overview scale.
        for(m in marks) {
            val x=m.x*zoom+tx; val y=m.y*zoom+ty
            if(x<0 || x>width || y<0 || y>height) continue
            dot.color=when { m.id==s.common -> palette.accent; m.id in s.second -> secondColor; m.id in s.first -> firstColor; else -> m.color }
            val leafRadius=if(radial) (2f*PI.toFloat()*640f*zoom/leafCount*.33f).coerceIn(.7f,2.8f) else 2.8f
            val namedVisible=m.label.isNotEmpty() && (m.priority<=2 || zoom>=.65f)
            val radius=(if(m.id==selected) 5f else if(m.species) leafRadius else if(namedVisible) 3.5f else 1.4f)*density
            if(m.id==selected) {
                dot.style=Paint.Style.STROKE; dot.strokeWidth=1.5f*density
                canvas.drawCircle(x,y,radius+4f*density,dot); dot.style=Paint.Style.FILL
            }
            if(m.id in s.second && m.id!=s.common) canvas.drawRect(x-radius,y-radius,x+radius,y+radius,dot)
            else canvas.drawCircle(x,y,radius,dot)
            if(m.label.isNotEmpty()) m.hit.set(x-12f*density,y-12f*density,x+12f*density,y+12f*density)
        }
        val ordered=marks.sortedBy { if(it.id==selected && it.species) -1 else it.priority }
        drawnLabels.clear()
        for(m in ordered) {
            if(m.label.isEmpty()) continue
            val essential=m.id==selected || m.priority==0
            if(radial && m.species && zoom < .38f && marks.size>40 && !essential) continue
            if(radial && m.priority==3 && zoom < .65f && !essential) continue
            val x=m.x*zoom+tx; val y=m.y*zoom+ty
            if(x<0 || x>width || y<0 || y>height) continue
            text.typeface=if(m.species) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
            val room=(width-12f*density).coerceAtLeast(density)
            val label=android.text.TextUtils.ellipsize(m.label,text,room,android.text.TextUtils.TruncateAt.END).toString()
            val length=min(m.width,room)
            val left=(if(radial && m.x<0) x-length-9f*density else x+9f*density).coerceIn(6f*density,max(6f*density,width-length-6f*density))
            val top=y-(text.descent()-text.ascent())*.5f-3*density
            m.bounds.set(left-3*density,top,left+length+3*density,top+text.descent()-text.ascent()+6*density)
            if(m.bounds.top<0 || m.bounds.bottom>height || drawnLabels.any { RectF.intersects(it,m.bounds) }) {
                m.bounds.setEmpty(); continue
            }
            drawnLabels.add(m.bounds)
            // Quiet backing keeps letters readable over branches without turning labels into buttons.
            paper.alpha=235
            canvas.drawRoundRect(m.bounds,4f*density,4f*density,paper)
            text.color=if(m.species) palette.text else palette.ink(m.color,palette.background)
            canvas.drawText(label,left,y-(text.ascent()+text.descent())*.5f,text)
            m.hit.union(m.bounds)
            m.hit.inset(0f,-6f*density)
        }
    }

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val next = (zoom * detector.scaleFactor).coerceIn(.04f, 4f)
            tx = detector.focusX - (detector.focusX-tx) * next/zoom
            ty = detector.focusY - (detector.focusY-ty) * next/zoom
            zoom = next
            invalidate()
            return true
        }
    })
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (!scaleDetector.isInProgress) { tx -= distanceX; ty -= distanceY; invalidate() }
            return true
        }
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            val hit = marks.filter { it.hit.contains(e.x,e.y) }.minByOrNull {
                kotlin.math.hypot(it.x*zoom+tx-e.x,it.y*zoom+ty-e.y)
            }
            hit?.let { selected=it.id; onSelect(it.id) }
            performClick()
            return true
        }
    })
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { animator?.cancel(); parent?.requestDisallowInterceptTouchEvent(true) }
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) parent?.requestDisallowInterceptTouchEvent(false)
        return true
    }
    override fun performClick(): Boolean { super.performClick(); return true }
    override fun onDetachedFromWindow() { animator?.cancel(); super.onDetachedFromWindow() }
}
