package com.Atom2Universe.app.games.mahjong

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.*

internal enum class MahjongIcon { BACK, INFO, UNDO, HINT, SHUFFLE, COLLECTION, SETTINGS, PLAY, CHECK, LOCK, HOME }

internal class MahjongIconDrawable(private val icon: MahjongIcon, private val tint: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color=tint; strokeWidth=2.1f; strokeCap=Paint.Cap.ROUND; strokeJoin=Paint.Join.ROUND }
    override fun draw(c: Canvas) {
        val save=c.save()
        val side=min(bounds.width(),bounds.height()).toFloat()
        c.translate(bounds.exactCenterX()-side/2,bounds.exactCenterY()-side/2); c.scale(side/24f,side/24f)
        p.color=tint; p.style=Paint.Style.STROKE
        val path=Path()
        when(icon) {
            MahjongIcon.BACK -> { c.drawLine(19f,12f,5f,12f,p); c.drawLine(5f,12f,11f,6f,p); c.drawLine(5f,12f,11f,18f,p) }
            MahjongIcon.INFO -> { c.drawCircle(12f,12f,9f,p); c.drawLine(12f,11f,12f,17f,p); p.style=Paint.Style.FILL; c.drawCircle(12f,7f,1.2f,p) }
            MahjongIcon.UNDO -> { c.drawArc(RectF(6f,6f,21f,21f),-170f,260f,false,p); c.drawLine(5f,4f,5f,10f,p); c.drawLine(5f,10f,11f,10f,p) }
            MahjongIcon.HINT -> { c.drawCircle(10f,10f,6.5f,p); c.drawLine(15f,15f,21f,21f,p); c.drawLine(7f,10f,13f,10f,p); c.drawLine(10f,7f,10f,13f,p) }
            MahjongIcon.SHUFFLE -> {
                path.moveTo(3f,6f); path.lineTo(7f,6f); path.lineTo(17f,18f); path.lineTo(22f,18f); c.drawPath(path,p)
                path.reset(); path.moveTo(3f,18f); path.lineTo(7f,18f); path.lineTo(17f,6f); path.lineTo(22f,6f); c.drawPath(path,p)
                for(y in listOf(6f,18f)) { c.drawLine(19f,y-3f,22f,y,p); c.drawLine(19f,y+3f,22f,y,p) }
            }
            MahjongIcon.COLLECTION -> {
                c.save(); c.rotate(-17f,8f,13f); c.drawRoundRect(2f,5f,12f,21f,2f,2f,p); c.restore()
                p.style=Paint.Style.FILL; p.color=0xfff4ecff.toInt(); c.drawRoundRect(9f,3f,21f,21f,2f,2f,p)
                p.style=Paint.Style.STROKE; p.color=tint; c.drawRoundRect(9f,3f,21f,21f,2f,2f,p); c.drawCircle(15f,11f,2.5f,p)
            }
            MahjongIcon.SETTINGS -> { c.drawCircle(12f,12f,6f,p); c.drawCircle(12f,12f,2f,p)
                repeat(8) { c.save(); c.rotate(it*45f,12f,12f); c.drawLine(12f,2f,12f,5f,p); c.restore() } }
            MahjongIcon.PLAY -> { p.style=Paint.Style.FILL; path.moveTo(8f,4f); path.lineTo(21f,12f); path.lineTo(8f,20f); path.close(); c.drawPath(path,p) }
            MahjongIcon.CHECK -> { path.moveTo(4f,12f); path.lineTo(10f,18f); path.lineTo(21f,5f); c.drawPath(path,p) }
            MahjongIcon.LOCK -> { c.drawRoundRect(5f,10f,19f,21f,2f,2f,p); c.drawArc(RectF(8f,2f,16f,16f),180f,180f,false,p); c.drawLine(12f,14f,12f,17f,p) }
            MahjongIcon.HOME -> { path.moveTo(3f,11f); path.lineTo(12f,3f); path.lineTo(21f,11f); c.drawPath(path,p)
                path.reset(); path.moveTo(6f,10f); path.lineTo(6f,21f); path.lineTo(18f,21f); path.lineTo(18f,10f); c.drawPath(path,p) }
        }
        c.restoreToCount(save)
    }
    override fun setAlpha(alpha: Int) { p.alpha=alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { p.colorFilter=colorFilter }
    override fun getIntrinsicWidth() = 24
    override fun getIntrinsicHeight() = 24
    @Suppress("DEPRECATION") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

internal class MahjongGiftMeter(context: Context, private val victories: Int) : View(context) {
    private val art=MahjongArt(context)
    init { contentDescription=context.getString(R.string.mj_gift_progress,victories%5,5) }
    override fun onDraw(c: Canvas) {
        val r=min(height*.25f,width/14f)
        val w=min(height*.85f,width*.15f)
        val left=(width-r*12f-w)/2
        repeat(5) { i -> art.star(c,left+r*1.4f+i*r*2.3f,height*.5f,r,if(i<victories%5) 0xffffb53d.toInt() else 0xffd5d7e8.toInt()) }
        art.chest(c,left+r*12f,height*.17f,w)
    }
}

/** Finite celebration. All buttons remain usable while the chest and porcelain appear. */
internal class MahjongRewardView(context: Context, private val reward: MahjongReward,
                                 private val initialStyle: Int, private val effects: Boolean,
                                 private val onEquip: (Int) -> Unit) : View(context) {
    private val art=MahjongArt(context)
    private val p=Paint(Paint.ANTI_ALIAS_FLAG)
    private val start=SystemClock.uptimeMillis()
    private val targets=mutableMapOf<Int,RectF>()
    private var selected=initialStyle
    private var pressed:Int?=null
    init { isClickable=true; contentDescription=context.getString(R.string.mj_reward_accessible,reward.pearls,reward.styles.size) }
    override fun onDraw(c: Canvas) {
        val animated=effects && ValueAnimator.areAnimatorsEnabled()
        val t=if(animated) ((SystemClock.uptimeMillis()-start)/1800f).coerceIn(0f,1f) else 1f
        val cx=width/2f; val cy=height*.41f
        p.style=Paint.Style.FILL
        p.shader=RadialGradient(cx,cy,max(width,height)*.6f,intArrayOf(0x38d3b56e,0x00d3b56e),null,Shader.TileMode.CLAMP)
        c.drawRect(0f,0f,width.toFloat(),height.toFloat(),p); p.shader=null
        repeat(12) { i ->
            c.save(); c.rotate(i*30f+t*20f,cx,cy)
            p.color=0x10ffffff
            val path=Path().apply { moveTo(cx,cy); lineTo(cx-width*.09f,cy-height*.65f); lineTo(cx+width*.09f,cy-height*.65f); close() }
            c.drawPath(path,p); c.restore()
        }
        val reveal=((t-.15f)/.5f).coerceIn(0f,1f)
        val styles=reward.styles
        val tw=min(width/(styles.size+1.6f),height*.29f).coerceAtLeast(1f)
        val th=tw*1.24f
        targets.clear()
        styles.forEachIndexed { i,style ->
            val x=cx+(i-(styles.size-1)/2f)*tw*1.05f-tw/2
            val y=cy-th*.65f+(1-reveal).pow(2)*th
            val rect=RectF(x,y,x+tw,y+th)
            targets[style]=rect
            val scale=.7f+.3f*reveal+sin(reveal*PI).toFloat()*.08f
            c.save(); c.scale(scale,scale,rect.centerX(),rect.centerY())
            art.tile(c,x,y,tw,th,0,MahjongStyles.get(style).glaze,selected=selected==style,style=style); c.restore()
        }
        if(styles.isEmpty()) {
            val size=min(width*.65f,height*.56f)
            art.trophy(c,cx-size/2,cy-size*.48f,size)
        }
        val chestWidth=min(width*.38f,height*.40f)
        art.chest(c,cx-chestWidth/2,height*.66f,chestWidth,reveal)
        val starRadius=min(width*.055f,height*.065f)
        repeat(3) { i -> art.star(c,cx+(i-1)*starRadius*3.1f,height*.12f-abs(i-1)*height*.015f,starRadius,0xffffb834.toInt()) }
        if(t<1f) repeat(44) { i ->
            val x=(i*73%101)/101f*width
            val y=((i*37%97)/97f*height*.5f+t*height*.8f)%height
            val color=MahjongArt.modeColors[i%6]
            c.save(); c.rotate(i*29f+t*240f,x,y)
            p.color=color; c.drawRoundRect(x,y,x+width*.014f,y+width*.028f,2f,2f,p); c.restore()
        }
        if(t<1f && isShown) postInvalidateOnAnimation()
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> { pressed=targets.entries.firstOrNull { it.value.contains(event.x,event.y) }?.key; return true }
            MotionEvent.ACTION_UP -> { val id=pressed; if(id!=null && targets[id]?.contains(event.x,event.y)==true) performClick(); pressed=null; return true }
            MotionEvent.ACTION_CANCEL -> { pressed=null; return true }
        }
        return true
    }
    override fun performClick(): Boolean {
        super.performClick(); pressed?.let { selected=it; onEquip(it); invalidate() }; return true
    }
}
