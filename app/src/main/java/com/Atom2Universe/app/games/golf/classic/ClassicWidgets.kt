package com.Atom2Universe.app.games.golf.classic

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.util.LruCache
import android.view.View
import java.util.concurrent.Executors
import com.Atom2Universe.app.games.golf.classic.core.ClassicHole
import com.Atom2Universe.app.games.golf.classic.core.GolfClub
import com.Atom2Universe.app.games.golf.classic.core.GolfLie
import com.Atom2Universe.app.games.golf.classic.core.GolfPoint
import kotlin.math.*

/** Tiny vector controls: no drawable or bitmap assets, with real accessible click targets. */
internal class GolfIcon(context: Context, kind: Kind, description: String, action: () -> Unit) : View(context) {
    enum class Kind { BACK, FLAG, HELP, LEFT, RIGHT, UP, DOWN, PERSON, SOUND, CARD, GRID, CAMERA, PLAY, PAUSE, SAVE, REPLAY }
    var kind=kind; set(value) { if(field!=value){field=value;invalidate()} }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    var highlighted = false; set(value) { field = value; invalidate() }
    init { contentDescription = description; isClickable = true; isFocusable = true; setOnClickListener { action() } }
    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        val size = min(width, height).toFloat()
        c.save(); c.translate((width-size)/2, (height-size)/2); c.scale(size/48f,size/48f)
        paint.style = Paint.Style.FILL
        paint.color = if (highlighted) 0xFF399D7A.toInt() else 0xDB163F3E.toInt()
        paint.alpha = if (isEnabled) 255 else 100
        c.drawCircle(24f,24f,23f,paint)
        paint.color = Color.WHITE
        paint.alpha = if (isEnabled) 255 else 100
        paint.strokeWidth = 2.2f; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE
        path.reset()
        when(kind) {
            Kind.REPLAY -> {
                c.drawArc(13f,13f,35f,35f,-90f,-300f,false,paint)
                path.moveTo(25f,19f);path.lineTo(34f,18f);path.lineTo(35f,27f)
            }
            Kind.PLAY -> { path.moveTo(19f,14f);path.lineTo(34f,24f);path.lineTo(19f,34f);path.close() }
            Kind.PAUSE -> { c.drawLine(19f,15f,19f,33f,paint);c.drawLine(29f,15f,29f,33f,paint) }
            Kind.SAVE -> {
                path.moveTo(14f,12f);path.lineTo(31f,12f);path.lineTo(36f,17f);path.lineTo(36f,36f)
                path.lineTo(12f,36f);path.lineTo(12f,12f);path.close()
                c.drawRect(18f,12f,29f,21f,paint);c.drawRect(18f,27f,30f,36f,paint)
            }
            Kind.CAMERA -> {
                path.moveTo(12f,19f);path.lineTo(18f,19f);path.lineTo(21f,14f);path.lineTo(28f,14f)
                path.lineTo(31f,19f);path.lineTo(36f,19f);path.lineTo(36f,34f);path.lineTo(12f,34f);path.close()
                c.drawCircle(24f,26f,5f,paint)
            }
            Kind.BACK, Kind.LEFT -> { path.moveTo(27f,16f); path.lineTo(19f,24f); path.lineTo(27f,32f) }
            Kind.RIGHT -> { path.moveTo(21f,16f); path.lineTo(29f,24f); path.lineTo(21f,32f) }
            Kind.UP -> { path.moveTo(16f,29f); path.lineTo(24f,21f); path.lineTo(32f,29f) }
            Kind.DOWN -> { path.moveTo(16f,21f); path.lineTo(24f,29f); path.lineTo(32f,21f) }
            Kind.PERSON -> { c.drawCircle(24f,18f,6f,paint); c.drawArc(13f,26f,35f,43f,180f,180f,false,paint) }
            Kind.FLAG -> { path.moveTo(19f,35f); path.lineTo(19f,12f); path.lineTo(33f,17f); path.lineTo(19f,22f); c.drawOval(12f,32f,32f,37f,paint) }
            Kind.HELP -> { c.drawCircle(24f,24f,12f,paint); c.drawPoint(24f,18f,paint); path.moveTo(24f,23f); path.lineTo(24f,31f) }
            Kind.SOUND -> { path.moveTo(13f,20f); path.lineTo(19f,20f); path.lineTo(25f,14f); path.lineTo(25f,34f); path.lineTo(19f,28f); path.lineTo(13f,28f); path.close(); c.drawArc(22f,15f,36f,33f,-65f,130f,false,paint); if(highlighted)c.drawLine(13f,35f,35f,13f,paint) }
            Kind.GRID -> { for(v in intArrayOf(19,29)){c.drawLine(v.toFloat(),13f,v.toFloat(),35f,paint);c.drawLine(13f,v.toFloat(),35f,v.toFloat(),paint)}; if(highlighted)c.drawLine(12f,36f,36f,12f,paint) }
            Kind.CARD -> { c.drawRoundRect(14f,12f,34f,36f,3f,3f,paint); for(y in 18..30 step 6)c.drawLine(19f,y.toFloat(),29f,y.toFloat(),paint) }
        }
        c.drawPath(path,paint); c.restore()
    }
}

/** The club in hand: a small drawing of its head (wood, hybrid, iron, wedge or putter) above its name. Tap to pick from the bag. */
internal class ClassicClubBadge(context: Context, action: () -> Unit) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val box = RectF()
    private var club = GolfClub.DRIVER
    private var name = ""
    init { isClickable = true; isFocusable = true; setOnClickListener { action() } }

    fun set(club: GolfClub, name: String, description: String) {
        if (club != this.club || name != this.name) { this.club = club; this.name = name; invalidate() }
        if (contentDescription?.toString() != description) contentDescription = description
    }

    override fun onDraw(c: Canvas) {
        val a = if (isEnabled) 255 else 100
        val d = resources.displayMetrics.density
        paint.style = Paint.Style.FILL; paint.color = 0xDB163F3E.toInt(); paint.alpha = a
        box.set(0f, 0f, width.toFloat(), height.toFloat()); c.drawRoundRect(box, 16f * d, 16f * d, paint)
        val side = min(width.toFloat(), height * .66f)
        c.save(); c.translate((width - side) / 2, 2f * d); c.scale(side / 40f, side / 40f)
        paint.color = when (club) {
            GolfClub.DRIVER, GolfClub.WOOD3, GolfClub.WOOD5 -> 0xFFFFFFFF
            GolfClub.HYBRID4, GolfClub.IRON5, GolfClub.IRON6, GolfClub.IRON7, GolfClub.IRON8, GolfClub.IRON9 -> 0xFFCFE8FF
            GolfClub.PUTTER -> 0xFFA6F0B8
            else -> 0xFFFFD778
        }.toInt(); paint.alpha = a
        paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        paint.style = Paint.Style.STROKE; paint.strokeWidth = 2f
        c.drawLine(31f, 3f, 16f, 27f, paint)
        paint.style = Paint.Style.FILL
        when (club) {
            GolfClub.DRIVER -> c.drawOval(2f, 25f, 22f, 39f, paint)
            GolfClub.WOOD3 -> c.drawOval(3f, 26f, 21f, 38f, paint)
            GolfClub.WOOD5 -> c.drawOval(4f, 27f, 20f, 37f, paint)
            GolfClub.HYBRID4 -> c.drawOval(5f, 26.5f, 21f, 37f, paint)
            GolfClub.PUTTER -> { box.set(2f, 31f, 22f, 38f); c.drawRoundRect(box, 2f, 2f, paint) }
            else -> {
                // Blade: the more loft, the steeper the top edge of the face.
                path.reset(); path.moveTo(17f, 25f); path.lineTo(2.5f, 30f + club.loft * .07f); path.lineTo(2.5f, 37f); path.lineTo(17f, 37f); path.close()
                c.drawPath(path, paint)
            }
        }
        c.restore()
        paint.style = Paint.Style.FILL; paint.color = Color.WHITE; paint.alpha = a
        paint.typeface = Typeface.DEFAULT_BOLD; paint.textAlign = Paint.Align.CENTER
        paint.textSize = 12f * d
        val fit = min(1f, (width - 8f * d) / max(1f, paint.measureText(name)))
        paint.textSize *= fit
        c.drawText(name, width / 2f, height - 7f * d, paint)
    }
}

/** Cached, sampled plan: every contour is the same signed-distance geometry as play. */
internal class ClassicMap(context: Context, val hole: ClassicHole) : View(context) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val path = Path()
    private var terrain: Bitmap? = null
    private var rasterRequest=0
    private val rect=RectF()
    // Redrawn only when the ball or the guide actually move.
    var ball: GolfPoint? = null
        set(value) { if (field != value) { field = value; invalidate() } }
    var preview: List<GolfPoint> = emptyList()
        set(value) { if (field != value) { field = value; invalidate() } }
    private var scale = 1f
    // +x is on the left of a player looking down +z, so the map runs the other way to match the 3D view.
    private fun sx(x:Float) = width/2f-(x-hole.mapCentreX)*scale
    private fun sy(z:Float) = height*.5f+(hole.mapCentreZ-z)*scale
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        scale=min((w-16f)/hole.mapWidth, (h-20f)/hole.mapDepth)
        if(w<1 || h<1) return
        val request=++rasterRequest
        val density=resources.displayMetrics.density
        val bw=min(w,if(w/density<=180f) 96 else 160)
        val bh=(h.toFloat()*bw/w).toInt().coerceIn(1,224)
        val key="${hole.hashCode()}:$w:$h:$bw:$bh"
        val cached=rasters.get(key)
        if(cached!=null) { terrain=cached; invalidate(); return }
        val worldScale=scale
        // The menu can show eighteen thumbnails; all terrain sampling stays off the UI thread.
        rasterWorker.execute {
            val ready=rasters.get(key) ?: run {
                val pixels=IntArray(bw*bh)
                for(j in 0 until bh) for(i in 0 until bw) {
                    val x=hole.mapCentreX-(i.toFloat()/bw*w-w*.5f)/worldScale
                    val z=hole.mapCentreZ-(j.toFloat()/bh*h-h*.5f)/worldScale
                    val colour=when(hole.lieAt(x,z)) {
                        GolfLie.GREEN -> 0xFFBDE58B.toInt()
                        GolfLie.FRINGE -> 0xFF689947.toInt()
                        GolfLie.SEMI_ROUGH -> 0xFF67823D.toInt()
                        GolfLie.FAIRWAY, GolfLie.TEE -> 0xFF81BD57.toInt()
                        GolfLie.BUNKER -> 0xFFF1D9A0.toInt()
                        GolfLie.WATER -> 0xFF4AB6CB.toInt()
                        else -> 0xFF366C46.toInt()
                    }
                    val slope=(hole.heightAt(x+1f,z)-hole.heightAt(x-1f,z))*.5f
                    val shade=(.97f+slope*.14f).coerceIn(.75f,1.12f)
                    pixels[j*bw+i]=Color.rgb((Color.red(colour)*shade).toInt().coerceAtMost(255),
                        (Color.green(colour)*shade).toInt().coerceAtMost(255),(Color.blue(colour)*shade).toInt().coerceAtMost(255))
                }
                Bitmap.createBitmap(pixels,bw,bh,Bitmap.Config.ARGB_8888).also { rasters.put(key,it) }
            }
            post { if(request==rasterRequest && width==w && height==h) { terrain=ready; invalidate() } }
        }
    }
    companion object {
        private val rasterWorker=Executors.newSingleThreadExecutor { task ->
            Thread(task,"classic-map").apply { isDaemon=true }
        }
        private val rasters=object:LruCache<String,Bitmap>(4*1024*1024) {
            override fun sizeOf(key:String,value:Bitmap)=value.byteCount
        }
    }
    override fun onDraw(c:Canvas) {
        rect.set(0f,0f,width.toFloat(),height.toFloat())
        path.reset(); path.addRoundRect(rect,14f,14f,Path.Direction.CW)
        c.save(); c.clipPath(path)
        p.style=Paint.Style.FILL; p.color=0xFF366C46.toInt()
        c.drawRect(rect,p)
        terrain?.let { c.drawBitmap(it,null,rect,p) }
        p.color=Color.WHITE; p.strokeWidth=2f; p.style=Paint.Style.STROKE
        path.reset(); preview.forEachIndexed { i,v -> if(i==0)path.moveTo(sx(v.x),sy(v.z)) else path.lineTo(sx(v.x),sy(v.z)) }
        c.drawPath(path,p)
        p.style=Paint.Style.FILL
        c.drawCircle(sx(hole.tee.x),sy(hole.tee.z),3f,p)
        p.color=0xFFFF7E62.toInt(); c.drawCircle(sx(hole.cup.x),sy(hole.cup.z),4f,p)
        hole.mini?.let { mini ->
            // Pipes and wells, where the plan would otherwise show an empty lane.
            p.color=0xFFFFA726.toInt(); for(portal in mini.portals) c.drawCircle(sx(portal.x),sy(portal.z),3f,p)
            p.color=0xFF7E57C2.toInt(); for(well in mini.wells) c.drawCircle(sx(well.x),sy(well.z),4.5f,p)
        }
        ball?.let { p.color=0xFF16453F.toInt(); c.drawCircle(sx(it.x),sy(it.z),5.5f,p)
            p.color=Color.WHITE; c.drawCircle(sx(it.x),sy(it.z),4f,p) }
        c.restore()
    }
}
