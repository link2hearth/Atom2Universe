package com.Atom2Universe.app.science.botany

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import com.Atom2Universe.app.R
import kotlinx.coroutines.*
import java.text.NumberFormat
import kotlin.math.min

/** Visible plates request a bounded background raster; onDraw only blits it and draws the legend. */
class PlantPlateView(context: Context, val specimen: PlantSpecies, initialMode: PlantView = PlantView.HABIT,
                     private val compact: Boolean = false) : View(context) {
    var mode = initialMode
        set(value) { if(field != value) { field=value; clear(); describe() } }
    var marks = true
        set(value) { if(field != value) { field=value; invalidate() } }
    private var cached: Bitmap? = null
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val visibleRect=Rect()
    private var scope:CoroutineScope?=null
    private var request:Job?=null
    private var requested:PlantImageKey?=null
    private var failed=false
    private val scrollListener=ViewTreeObserver.OnScrollChangedListener { requestIfVisible() }

    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_YES; describe() }

    private fun describe() {
        contentDescription=context.getString(R.string.bot_plate_accessibility,context.getString(specimen.name),context.getString(mode.label),
            context.getString(specimen.captions.getValue(mode)))
    }
    private fun clear(){ request?.cancel();request=null;requested=null;cached=null;failed=false;invalidate() }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){ super.onSizeChanged(w,h,oldw,oldh);clear() }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate)
        viewTreeObserver.addOnScrollChangedListener(scrollListener)
        post { requestIfVisible() }
    }
    override fun onDetachedFromWindow() {
        if(viewTreeObserver.isAlive)viewTreeObserver.removeOnScrollChangedListener(scrollListener)
        scope?.cancel();scope=null;clear();super.onDetachedFromWindow()
    }
    override fun onWindowVisibilityChanged(visibility:Int) {
        super.onWindowVisibilityChanged(visibility)
        if(isAttachedToWindow)requestIfVisible()
    }
    private fun requestIfVisible() {
        if(!isAttachedToWindow || width<=0 || height<=0)return
        if(!isShown || windowVisibility!=VISIBLE || !getLocalVisibleRect(visibleRect)) {
            if(requested!=null)clear()
            return
        }
        val factor=min(width/300f,height/(if(compact)302f else 340f))
        val key=PlantImageKey(specimen.id,mode,PlantImageStore.edge(300*factor,compact),compact)
        if(requested==key)return
        request?.cancel();requested=key;cached=PlantImageStore.shared.peek(key);failed=false
        invalidate()
        if(cached!=null)return
        request=scope?.launch {
            try {
                val image=PlantImageStore.shared.load(specimen,key)
                if(requested==key && isAttachedToWindow){cached=image;invalidate()}
            } catch(cancelled:CancellationException) { throw cancelled
            } catch(error:Exception) {
                Log.w("BotanyPlate","Cannot render ${key.id}/${key.mode}",error)
                if(requested==key){failed=true;invalidate()}
            }
        }
    }

    override fun onDraw(canvas:Canvas) {
        super.onDraw(canvas)
        if(width<=0 || height<=0) return
        requestIfVisible()
        val c=canvas
            c.drawColor(0xfff4f0e3.toInt())
            val factor=min(width/300f,height/(if(compact) 302f else 340f))
            c.save();c.translate((width-300f*factor)/2,0f);c.scale(factor,factor)
            val image=cached
            if(image!=null)c.drawBitmap(image,null,RectF(0f,0f,300f,300f),paint)
            else {
                paint.style=Paint.Style.FILL;paint.color=0xff7a826c.toInt();paint.textSize=if(compact) 26f else 12f
                paint.textAlign=Paint.Align.CENTER
                c.drawText(context.getString(if(failed)R.string.bot_plate_unavailable else R.string.bot_plate_loading),150f,155f,paint)
                paint.textAlign=Paint.Align.LEFT
            }
            if(!compact) {
                paint.color=0xff53604b.toInt();paint.strokeWidth=1.2f;paint.style=Paint.Style.STROKE
                c.drawLine(25f,311f,95f,311f,paint);c.drawLine(25f,307f,25f,315f,paint);c.drawLine(95f,307f,95f,315f,paint)
                paint.style=Paint.Style.FILL;paint.textSize=11f;paint.typeface=Typeface.create("sans-serif",Typeface.NORMAL)
                val study=specimen.look.study
                val cmPerPixel=if(study!=null) study.cmPerPixel(specimen,mode) else when(mode) {
                    PlantView.HABIT -> (specimen.heightMinCm+specimen.heightMaxCm)/2f/249f
                    PlantView.LEAF -> specimen.look.leafCm/(if(specimen.look.leaf==LeafShape.NEEDLE && specimen.look.needleCount>2)145f else 198f)
                    PlantView.FLOWER -> specimen.look.flowerCm/when(specimen.look.flower){FlowerShape.STAR,FlowerShape.CUP,FlowerShape.DAISY->124f;else->180f}
                    PlantView.FRUIT -> specimen.look.fruitCm/when(specimen.look.fruit){FruitShape.CONE->130f;FruitShape.GRAIN->166f;else->120f}
                }
                val cm=70*cmPerPixel
                val format=NumberFormat.getNumberInstance(resources.configuration.locales[0]).apply{maximumFractionDigits=if(cm<1f) 2 else 1}
                val label=context.getString(if(cm>=100) R.string.bot_scale_m else R.string.bot_scale_cm,format.format(if(cm>=100) cm/100 else cm))
                c.drawText(label,25f,329f,paint)
                if(marks) {
                    val anchors=if(study!=null)listOf(study.anchor(mode)) else when(mode) {
                        PlantView.HABIT->listOf(150f to 40f,90f to 137f,155f to 263f)
                        PlantView.LEAF->listOf(166f to 99f,53f to 226f,183f to 213f)
                        PlantView.FLOWER->listOf(150f to 145f,173f to 127f)
                        PlantView.FRUIT->listOf(158f to 160f)
                    }
                    val nf=NumberFormat.getIntegerInstance(resources.configuration.locales[0])
                    anchors.forEachIndexed {i,(x,y)->
                        val lx=if(i%2==0) 275f else 22f;val ly=47f+i*65f
                        paint.color=0xff849176.toInt();paint.strokeWidth=.8f;c.drawLine(x,y,lx,ly,paint)
                        paint.color=0xfff4f0e3.toInt();c.drawCircle(lx,ly,10f,paint)
                        paint.color=0xff53604b.toInt();paint.style=Paint.Style.STROKE;c.drawCircle(lx,ly,10f,paint)
                        paint.style=Paint.Style.FILL;paint.textAlign=Paint.Align.CENTER;paint.textSize=11f;c.drawText(nf.format(i+1),lx,ly+4f,paint);paint.textAlign=Paint.Align.LEFT
                    }
                }
            }
            c.restore()
    }
}
