package com.Atom2Universe.app.science.parentes

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.graphics.ColorUtils
import com.Atom2Universe.app.science.SciencePalette
import kotlin.math.*

/** Original schematic art. Organisms illustrate characters, never reconstructed ancestors. */
class ParentesIllustrationView(context: Context, private val subject: String) : View(context) {
    private val palette = SciencePalette(context)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val green = palette.mark(0xff71cba5.toInt())
    private val blue = palette.mark(0xff83b9e4.toInt())
    private val gold = palette.mark(0xffe6b574.toInt())
    private val purple = palette.mark(0xffb69cdb.toInt())
    private var progress = 1f
    private var motion: ValueAnimator? = null
    var onProgress: (Float) -> Unit = {}

    init { background = palette.shape(palette.surface, 24f) }

    fun play() {
        motion?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) { progress = 1f; onProgress(1f); invalidate(); return }
        motion = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 4200
            interpolator = LinearInterpolator()
            addUpdateListener { progress = it.animatedValue as Float; onProgress(progress); invalidate() }
            start()
        }
    }
    fun pause() { motion?.pause() }
    fun resume() { motion?.resume() }
    fun stop() { motion?.cancel(); motion = null }
    override fun onDetachedFromWindow() { stop(); super.onDetachedFromWindow() }

    private fun fill(color: Int, alpha: Int = 255) {
        paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color; paint.alpha = alpha
    }
    private fun stroke(color: Int, width: Float = 3f, alpha: Int = 255) {
        fill(color, alpha); paint.style = Paint.Style.STROKE; paint.strokeWidth = width
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
    }
    private fun line(c: Canvas, color: Int, width: Float, vararg points: Float) {
        stroke(color, width)
        val p = Path().apply { moveTo(points[0], points[1]); for (i in 2 until points.size step 2) lineTo(points[i], points[i+1]) }
        c.drawPath(p, paint)
    }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, color: Int, alpha: Int = 255) {
        fill(color, alpha); c.drawOval(x-rx, y-ry, x+rx, y+ry, paint)
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val scale = min(width / 400f, height / 230f)
        c.save(); c.translate((width-400*scale)/2f, (height-230*scale)/2f); c.scale(scale,scale)
        oval(c,200f,113f,143f,96f,green,13)
        repeat(19) { i ->
            val x = 30f+(i*71%340); val y = 20f+(i*47%190)
            oval(c,x,y,1.5f,1.5f,palette.secondary,35)
        }
        val wave = sin(progress * PI.toFloat() * 4f)
        when(subject) {
            "cell", "eukaryote", "plastid" -> cell(c, subject, wave)
            "fungus" -> {
                for (i in 0..4) {
                    val x = 140f+i*30
                    line(c,purple,8f,200f,192f,x,133f,x-26,82f)
                    line(c,purple,5f,x,133f,x+27,105f,x+32,57f)
                    oval(c,x+32,57f,6f,6f,gold,180)
                }
                oval(c,200f,191f,34f,8f,purple,80)
            }
            "colony" -> {
                repeat(19) { i ->
                    val angle=i*2.39996f
                    val radius=sqrt(i.toFloat())*18f
                    val x=200f+cos(angle)*radius; val y=114f+sin(angle)*radius
                    val col=if(i%3==0) gold else if(i%3==1) green else blue
                    oval(c,x,y,19f,18f,col,180)
                    oval(c,x+3,y-2,5f,5f,purple)
                    if(i == (progress*18).toInt()) { stroke(palette.text,2f); c.drawCircle(x,y,22f,paint) }
                }
            }
            "fish" -> {
                fill(blue,210)
                c.drawPath(Path().apply {
                    moveTo(77f,114f); cubicTo(144f,35f,276f,67f,305f,111f)
                    lineTo(340f,80f); lineTo(337f,150f); lineTo(305f,123f)
                    cubicTo(220f,182f,129f,156f,77f,114f); close()
                },paint)
                line(c,gold,4f,109f,112f,300f,117f)
                repeat(12) { i -> val x=133f+i*12; line(c,palette.surface,2f,x,115f,x-7,93f); line(c,palette.surface,2f,x,115f,x-5,136f) }
                oval(c,113f,101f,5f,5f,palette.text)
                line(c,blue,3f,188f,71f,213f,46f,233f,73f)
                oval(c,155f+progress*123,115f,5f,5f,gold)
            }
            "limbs" -> {
                line(c,blue,14f,93f,72f,167f,105f)
                line(c,green,9f,173f,105f,240f,86f)
                line(c,green,9f,173f,114f,242f,104f)
                for(i in 0..4) {
                    val y=65f+i*22
                    line(c,gold,6f,252f,99f,284f,y,315f,y-9+i*4)
                }
                for((x,y) in listOf(93f to 72f,170f to 109f,248f to 98f)) oval(c,x,y,10f,10f,palette.text)
            }
            "egg" -> {
                oval(c,200f,113f,74f,94f,gold,190)
                oval(c,200f,121f,64f,75f,palette.surface)
                oval(c,203f,145f,41f,31f,gold,220)
                oval(c,192f,101f,40f,37f,blue,85)
                stroke(blue,3f+wave*.5f); c.drawOval(151f,62f,233f,141f,paint)
                stroke(purple,12f); c.drawArc(172f,77f,209f,116f,30f,270f,false,paint)
                oval(c,205f,98f,10f,10f,purple)
            }
            "mammal" -> {
                oval(c,184f,116f,77f,39f,gold)
                oval(c,269f,102f,32f,29f,gold)
                oval(c,261f,74f,12f,16f,gold)
                oval(c,291f,112f,18f,11f,gold)
                for(x in listOf(139f,169f,226f,248f)) line(c,gold,13f,x,137f,x-5,178f,x+11,178f)
                stroke(gold,8f); c.drawArc(79f,67f,140f,135f,70f,180f,false,paint)
                oval(c,280f,96f,4f,4f,palette.text)
                repeat(19) { i -> val x=121f+i*7; line(c,palette.text,1.4f,x,93f-8*sin(i*.17f),x+3,79f-8*sin(i*.17f)) }
            }
            "hand" -> {
                fill(gold)
                c.drawRoundRect(160f,104f,241f,179f,22f,22f,paint)
                for(i in 0..3) {
                    val x=167f+i*21
                    c.drawRoundRect(x,44f+abs(i-1)*9,x+16,125f,8f,8f,paint)
                    line(c,palette.surface,2f,x+3,94f,x+12,94f)
                }
                line(c,gold,19f,169f,150f,143f,130f,132f,102f)
                line(c,gold,48f,198f,170f,198f,203f)
                line(c,palette.surface,2f,179f,150f,215f,141f,231f,153f)
                oval(c,133f,102f,12f,12f,blue,100+(progress*100).toInt())
            }
            "human" -> {
                oval(c,204f,47f,17f,19f,gold)
                line(c,blue,29f,201f,81f,197f,125f)
                line(c,blue,12f,200f,85f,173f,109f,154f,96f-wave*3)
                line(c,blue,12f,206f,86f,228f,107f,244f,91f+wave*3)
                line(c,green,15f,194f,129f,171f,162f,155f,198f)
                line(c,green,15f,202f,129f,224f,161f,243f,195f)
                line(c,gold,10f,154f,198f,138f,200f)
                line(c,gold,10f,241f,197f,258f,197f)
            }
            "feather" -> {
                val reveal=.35f+progress*.65f
                for(i in 0..24) {
                    val y=39f+i*5.4f; val span=sin(i/25f*PI.toFloat())*62*reveal
                    line(c,if(i%3==0) green else blue,3f,200f,y,200f-span,y-16f)
                    line(c,blue,3f,201f,y,201f+span*.85f,y-14f)
                }
                line(c,gold,4f,198f,204f,202f,30f)
            }
            "bird", "pigeon" -> bird(c, subject=="pigeon", wave)
            "moss" -> {
                for(i in -2..2) {
                    c.save(); c.rotate(i*24f,200f,169f)
                    line(c,green,26f,200f,168f,200f,115f,179f,82f)
                    line(c,green,20f,200f,117f,219f,87f)
                    line(c,gold,2f,200f,174f,199f,123f,180f,84f)
                    c.restore()
                }
            }
            "vessels" -> {
                fill(green,55); c.drawRoundRect(129f,29f,271f,203f,32f,32f,paint)
                for(i in 0..4) {
                    val x=150f+i*25
                    line(c,if(i%2==0) blue else gold,12f,x,45f,x,188f)
                    repeat(4) { j -> val y=188f-((j*36+progress*144)%144); oval(c,x,y,3f,6f,palette.text,200) }
                }
                oval(c,200f,33f,68f,12f,green,150)
            }
            "seed" -> {
                oval(c,198f,118f,58f,75f,gold)
                oval(c,196f,118f,45f,62f,palette.surface)
                oval(c,181f,127f,24f,44f,gold,120)
                stroke(green,8f); c.drawArc(184f,88f,220f,138f,20f,260f,false,paint)
                line(c,green,5f,213f,114f,224f,145f+progress*16,231f,170f+progress*24)
            }
            "flower" -> {
                line(c,green,7f,200f,130f,200f,209f)
                for(i in 0..4) {
                    c.save(); c.rotate(i*72f,200f,103f)
                    oval(c,200f,65f,24f,42f,purple,200); c.restore()
                }
                oval(c,200f,111f,27f,29f,gold)
                oval(c,200f,115f,13f,17f,green)
                line(c,green,5f,200f,112f,200f,63f)
                oval(c,200f,60f,9f,5f,green)
                repeat(7) { i -> oval(c,171f+i*9,82f+(i%2)*6,3f,3f,gold) }
            }
            "oak" -> {
                fun branch(x:Float,y:Float,length:Float,angle:Float,depth:Int) {
                    val ex=x+cos(angle)*length; val ey=y+sin(angle)*length
                    line(c,gold,(depth*2f).coerceAtLeast(1f),x,y,ex,ey)
                    if(depth>0) {
                        branch(ex,ey,length*.7f,angle-.48f,depth-1)
                        branch(ex,ey,length*.7f,angle+.55f,depth-1)
                    } else oval(c,ex,ey,15f+progress*4,11f+progress*3,green,180)
                }
                branch(192f,209f,57f,-PI.toFloat()/2,4)
                oval(c,327f,183f,12f,18f,gold)
                oval(c,327f,173f,15f,7f,purple)
            }
        }
        c.restore()
    }

    private fun cell(c:Canvas, kind:String, wave:Float) {
        if(kind=="plastid") {
            fill(green,55); c.drawRoundRect(106f,31f,294f,201f,27f,27f,paint)
            stroke(green,4f); c.drawRoundRect(106f,31f,294f,201f,27f,27f,paint)
            oval(c,205f,116f,50f,58f,blue,55)
            for(i in 0..5) {
                val x=if(i<3) 126f else 275f; val y=63f+(i%3)*50
                oval(c,x,y,12f,19f,green)
                repeat(3) { j -> line(c,palette.surface,1.5f,x-6,y-8+j*8,x+6,y-8+j*8) }
            }
            oval(c,167f,119f,18f,20f,purple)
        } else {
            oval(c,200f,115f,102f,75f,green,55)
            stroke(green,4f); c.drawOval(98f,40f,302f,190f,paint)
            if(kind=="eukaryote") {
                oval(c,193f,111f,35f,31f,purple,200)
                oval(c,198f,107f,11f,11f,palette.surface,180)
                for(i in 0..3) {
                    val x=if(i<2) 134f else 260f; val y=79f+(i%2)*66
                    oval(c,x,y,20f,10f,gold)
                    line(c,palette.surface,2f,x-12,y+3,x-6,y-4,x,y+4,x+6,y-4,x+12,y+3)
                }
            } else {
                val p=Path(); val q=Path()
                for(i in 0..50) {
                    val x=143f+i*2.2f; val y=sin(i*.27f+wave*.2f)*20f
                    if(i==0) { p.moveTo(x,112+y); q.moveTo(x,112-y) }
                    else { p.lineTo(x,112+y); q.lineTo(x,112-y) }
                    if(i%5==0) line(c,purple,1.5f,x,112+y,x,112-y)
                }
                stroke(gold,3f); c.drawPath(p,paint); stroke(purple,3f); c.drawPath(q,paint)
            }
        }
    }

    private fun bird(c:Canvas,pigeon:Boolean,wave:Float) {
        val body=if(pigeon) ColorUtils.blendARGB(blue,palette.secondary,.45f) else blue
        fill(body)
        c.drawPath(Path().apply {
            moveTo(101f,144f); cubicTo(126f,112f,177f,91f,225f,115f)
            cubicTo(237f,96f,232f,69f,254f,64f); cubicTo(283f,53f,300f,91f,277f,108f)
            cubicTo(272f,156f,219f,181f,153f,161f); lineTo(92f,176f); close()
        },paint)
        oval(c,252f,111f,17f,17f,green,130)
        c.save(); c.rotate(if(pigeon) -5f else -24f+wave*12,217f,123f)
        fill(body)
        c.drawPath(Path().apply {
            moveTo(219f,123f); cubicTo(169f,68f,127f,58f,80f,42f)
            cubicTo(98f,116f,136f,145f,219f,140f); close()
        },paint)
        repeat(8) { i -> line(c,palette.surface,1.5f,111f+i*12,77f+i*4,126f+i*10,120f+i*2) }
        if(pigeon) { line(c,palette.text,6f,146f,82f,171f,136f); line(c,palette.text,5f,163f,89f,187f,139f) }
        c.restore()
        fill(gold); c.drawPath(Path().apply { moveTo(281f,82f); lineTo(307f,89f); lineTo(282f,95f); close() },paint)
        oval(c,270f,78f,4f,4f,palette.text)
        line(c,gold,4f,207f,166f,206f,192f,196f,197f)
        line(c,gold,4f,227f,160f,232f,190f,245f,194f)
    }
}

/** The IDs and exact fork come from LifeTree, while geometry has no time scale. */
class ParentesStoryForkView(context:Context) : View(context) {
    private val palette=SciencePalette(context)
    private val pen=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; strokeCap=Paint.Cap.ROUND }
    var progress=1f
        set(value) { field=value; invalidate() }
    override fun onDraw(c:Canvas) {
        super.onDraw(c)
        val d=resources.displayMetrics.density
        val mid=width*.5f; val top=12*d; val end=height-12*d
        val paths=listOf(width*.16f to palette.mark(0xff71cba5.toInt()),width*.84f to palette.mark(0xffe6b574.toInt()))
        for((x,color) in paths) {
            val p=Path().apply { moveTo(mid,top); cubicTo(mid,end*.65f,x,end*.4f,x,end) }
            pen.color=palette.grid; pen.strokeWidth=3*d; c.drawPath(p,pen)
            val measure=PathMeasure(p,false); val revealed=Path()
            measure.getSegment(0f,measure.length*(progress*1.6f).coerceAtMost(1f),revealed,true)
            pen.color=color; c.drawPath(revealed,pen)
            pen.style=Paint.Style.FILL; c.drawCircle(x,end,5*d,pen); pen.style=Paint.Style.STROKE
        }
        pen.style=Paint.Style.FILL; pen.color=palette.text; c.drawCircle(mid,top,5*d,pen); pen.style=Paint.Style.STROKE
    }
}
