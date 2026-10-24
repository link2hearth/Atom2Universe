package com.Atom2Universe.app.games.mahjong

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.util.SparseArray
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import com.Atom2Universe.app.R
import kotlin.math.*
import kotlin.random.Random

class MahjongBoardView(context: Context, val game: MahjongGame, private val accent: Int,
                       private val porcelain: Int = 0, private val effects: Boolean = true,
                       private val haptics: Boolean = true, val texture: Int = porcelain) : View(context) {
    var onMove: (MahjongGame.Result) -> Unit = {}
    private val art = MahjongArt(context)
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val bounds = mutableMapOf<Int, RectF>()
    private val from = mutableMapOf<Int, RectF>()
    private val movingRects = SparseArray<RectF>()
    private val faces = SparseArray<Bitmap>()
    private val bitmapRect = RectF()
    private val selectionRect = RectF()
    private val ghostRect = RectF()
    private val footprintRect = RectF()
    private val arrowPath = Path()
    private val matchPath = Path()
    private val numberLabels = Array(9) { context.getString(R.string.mj_number,it+1) }
    private val iceBounds = RectF()
    private var iceGradient: LinearGradient? = null
    private var footprintDashes: DashPathEffect? = null
    private var gridWidth = -1
    private var gridHeight = -1
    private var gridColumns = -1
    private var gridRows = -1
    private var gridRects = emptyArray<RectF>()
    private var occupiedCells = BooleanArray(0)
    private var reachableCells = BooleanArray(0)
    private val slideTargets = arrayOfNulls<MahjongSlide>(4)
    private var slideStopSource: Int? = null
    private var slideStopsValid = false
    private var free = emptySet<Int>()
    private var drawOrder = emptyList<MahjongTile>()
    private var tileW = 0f
    private var tileH = 0f
    private var left = 0f
    private var top = 0f
    private var trayTop = 0f
    private var motionAt = 0L
    private var entranceAt = SystemClock.uptimeMillis()
    private var active = true
    private var hints = emptyList<Int>()
    private var slideHint: MahjongSlide? = null
    private var hintUntil = 0L
    private var pathUntil = 0L
    private var overlayExpiryAt = 0L
    private val expireOverlays = Runnable {
        overlayExpiryAt = 0L
        if (active) { invalidate(); scheduleOverlayExpiry() }
    }
    private val animated get() = effects && ValueAnimator.areAnimatorsEnabled()
    private data class Spark(val x: Float, val y: Float, val vx: Float, val vy: Float, val color: Int)
    private data class Ghost(val rect: RectF, val face: Int)
    private data class Burst(val start: Long, val ghosts: List<Ghost>, val sparks: List<Spark>,
                             val path: Path?, val x: Float, val y: Float, val scoreLabel: String, val comboLabel: String?)
    private val bursts = mutableListOf<Burst>()
    private var pressed: Int? = null
    private var downX = 0f
    private var downY = 0f
    private var touchCell: MahjongCell? = null
    private var dragging = false
    private var dragTarget: MahjongSlide? = null
    private val swipeThreshold get() = max(resources.displayMetrics.density*6f,
        min(tileW*.24f,resources.displayMetrics.density*12f))

    init {
        isClickable = true; isFocusable = true
        contentDescription = context.getString(R.string.mj_board_description)
    }

    override fun onSizeChanged(w: Int,h: Int,oldw: Int,oldh: Int) {
        recycleFaces(); from.clear(); movingRects.clear(); layoutTiles()
    }

    private fun layoutTiles() {
        if (width <= 0 || height <= 0) return
        val pad = resources.displayMetrics.density * 10f
        val reserve = if (game.mode == MahjongMode.TRIPLE) min(height * .18f, width * .19f) else pad
        // Connections may travel around the outside edge, so leave that route visible.
        val perimeter = game.mode == MahjongMode.CONNECT || game.mode == MahjongMode.CASCADE
        tileW = min((width-pad*2)/(game.columns+(if(perimeter) 1.8f else .65f)),
            (height-reserve-pad*2)/((game.rows+(if(perimeter) 1.8f else .4f))*1.24f)).coerceAtLeast(1f)
        tileH = tileW*1.24f
        left = (width-game.columns*tileW)/2
        top = (height-reserve-game.rows*tileH)/2 + pad*.3f
        trayTop = height-reserve+pad*.1f
        bounds.clear()
        drawOrder = game.board.sortedWith(compareBy<MahjongTile> { it.z }.thenBy { it.y }.thenBy { it.x })
        free = drawOrder.filter(game::isFree).map { it.id }.toSet()
        for (tile in drawOrder) {
            val x = left+tile.x*tileW-tile.z*tileW*.045f
            val y = top+tile.y*tileH-tile.z*tileH*.07f
            bounds[tile.id] = RectF(x,y,x+tileW*.94f,y+tileH*.91f)
        }
        val slot = min(tileW*.90f, (height-trayTop)/1.36f).coerceAtLeast(1f)
        val start = (width-slot*7.42f)/2
        for ((i,id) in game.tray.withIndex()) bounds[id] = RectF(start+i*slot*1.06f,trayTop,
            start+i*slot*1.06f+slot*.94f,trayTop+slot*1.24f)
        if (game.mode == MahjongMode.SLIDE) updateSlideGrid()
        matchPath.rewind()
        game.lastPath.forEachIndexed { index,cell ->
            val x=left+(cell.x+.47f)*tileW; val y=top+(cell.y+.45f)*tileH
            if(index==0) matchPath.moveTo(x,y) else matchPath.lineTo(x,y)
        }
    }

    private fun recycleFaces() {
        for (i in 0 until faces.size()) faces.valueAt(i).recycle()
        faces.clear()
    }

    private fun updateSlideGrid() {
        if (gridWidth!=width || gridHeight!=height || gridColumns!=game.columns || gridRows!=game.rows) {
            gridWidth=width; gridHeight=height; gridColumns=game.columns; gridRows=game.rows
            gridRects=Array(game.columns*game.rows) { index ->
                val x=index%game.columns; val y=index/game.columns
                RectF(left+x*tileW,top+y*tileH,left+(x+.94f)*tileW,top+(y+.91f)*tileH)
            }
            occupiedCells=BooleanArray(gridRects.size); reachableCells=BooleanArray(gridRects.size)
            iceBounds.set(left-tileW*.10f,top-tileH*.08f,left+game.columns*tileW,top+game.rows*tileH)
            iceGradient=LinearGradient(iceBounds.left,iceBounds.top,iceBounds.right,iceBounds.bottom,
                0xb8e2f7ff.toInt(),0xb0b9dfef.toInt(),Shader.TileMode.CLAMP)
            footprintDashes=DashPathEffect(floatArrayOf(tileW*.11f,tileW*.08f),0f)
        }
        occupiedCells.fill(false)
        for (tile in drawOrder) occupiedCells[tile.y*game.columns+tile.x]=true
        slideStopsValid=false
    }

    private fun ensureSlideStops(sourceId: Int?) {
        if (slideStopsValid && slideStopSource==sourceId) return
        slideStopsValid=true; slideStopSource=sourceId
        reachableCells.fill(false)
        slideTargets.fill(null)
        if (sourceId==null) return
        slideTargets[0]=game.iceSlideTarget(sourceId,1,0)
        slideTargets[1]=game.iceSlideTarget(sourceId,-1,0)
        slideTargets[2]=game.iceSlideTarget(sourceId,0,1)
        slideTargets[3]=game.iceSlideTarget(sourceId,0,-1)
        for (target in slideTargets) if(target!=null) reachableCells[target.y*game.columns+target.x]=true
    }

    private fun startMotion(before: Map<Int,RectF>,now: Long) {
        from.clear()
        for ((id,old) in before) {
            val target=bounds[id] ?: continue
            if (old!=target) from[id]=old
        }
        motionAt=now
    }

    private fun location(id: Int, now: Long): RectF? {
        val target = bounds[id] ?: return null
        val old = from[id] ?: return target
        if (!animated || now-motionAt>=340) return target
        val t = ((now-motionAt)/340f).coerceIn(0f,1f)
        val ease = if(game.mode==MahjongMode.SLIDE) t else 1f-(1f-t).pow(3)
        val rect=movingRects[id] ?: RectF().also { movingRects.put(id,it) }
        rect.set(old.left+(target.left-old.left)*ease,old.top+(target.top-old.top)*ease,
            old.right+(target.right-old.right)*ease,old.bottom+(target.bottom-old.bottom)*ease)
        return rect
    }

    private fun drawTile(c: Canvas, rect: RectF, face: Int, back: Boolean, dim: Boolean, selected: Boolean, alpha: Int = 255) {
        // Ghosts shrink every frame; cache at board resolution, never at animated sizes.
        val bw = ceil(tileW*.94f).toInt().coerceIn(1,320)
        val bh = ceil(bw*1.24f).toInt()
        val key = ((if(back) -1 else face)+1)*512+bw
        val bitmap = faces[key] ?: run {
            Bitmap.createBitmap(bw+2,ceil(bh*1.08f).toInt()+2,Bitmap.Config.ARGB_8888).also {
                art.tile(Canvas(it),0f,0f,bw.toFloat(),bh.toFloat(),face,accent,back=back,style=porcelain,texture=texture)
                faces.put(key,it)
            }
        }
        p.style = Paint.Style.FILL; p.color = Color.WHITE; p.alpha = alpha
        bitmapRect.set(rect.left,rect.top,rect.right+rect.width()*2f/bw,rect.top+rect.height()*1.08f)
        c.drawBitmap(bitmap,null,bitmapRect,p); p.alpha = 255
        if (game.mode == MahjongMode.TEN && !back) {
            // The numerical rule stays legible with every collectible tile texture.
            val radius = rect.width()*.23f
            val cx = rect.right-radius*.95f; val cy = rect.bottom-radius*1.05f
            p.color = 0xff344763.toInt(); p.alpha = alpha
            c.drawCircle(cx,cy,radius,p)
            p.color = Color.WHITE; p.alpha = alpha
            p.textSize = rect.width()*.36f; p.textAlign = Paint.Align.CENTER; p.typeface = Typeface.DEFAULT_BOLD
            c.drawText(numberLabels[face%9],cx,cy-(p.ascent()+p.descent())/2,p)
            p.alpha = 255
        }
        if (dim) {
            p.color = 0x2633467e
            c.drawRoundRect(rect,rect.width()*.15f,rect.width()*.15f,p)
            // A small edge marker distinguishes blocked tiles without hiding their art.
            p.color=0xff637084.toInt()
            c.drawRoundRect(rect.centerX()-rect.width()*.08f,rect.bottom-rect.height()*.04f,
                rect.centerX()+rect.width()*.08f,rect.bottom-rect.height()*.025f,2f,2f,p)
        }
        if (selected) {
            p.style = Paint.Style.STROKE; p.strokeWidth = max(2f,rect.width()*.05f); p.color = 0xffffbd36.toInt()
            selectionRect.set(rect); selectionRect.inset(-rect.width()*.028f,-rect.height()*.02f)
            c.drawRoundRect(selectionRect,rect.width()*.17f,rect.width()*.17f,p); p.style = Paint.Style.FILL
        }
    }

    override fun onDraw(c: Canvas) {
        super.onDraw(c)
        if (bounds.isEmpty() && !game.complete) layoutTiles()
        art.backdrop(c,width.toFloat(),height.toFloat(),accent)
        val now = SystemClock.uptimeMillis()
        if (game.mode == MahjongMode.TRIPLE) drawTray(c)
        if (game.mode == MahjongMode.SLIDE) drawSlideGrid(c,now)
        if (now-motionAt>=340) from.clear()
        var moving = animated && from.isNotEmpty()
        for (tile in drawOrder) {
            val rect = location(tile.id,now) ?: continue
            val selected = tile.id==game.selected || (now<hintUntil && tile.id in hints) || (dragging && tile.id==pressed)
            val save = c.save()
            val enter = if (animated) ((now-entranceAt-tile.id%12*16)/440f).coerceIn(0f,1f) else 1f
            if (enter<1) { c.translate(0f,(1-enter).pow(2)*tileH*.4f); moving=true }
            drawTile(c,rect,tile.face,false,tile.id !in free,selected,(90+165*enter).toInt())
            c.restoreToCount(save)
        }
        for (id in game.tray) location(id,now)?.let { drawTile(c,it,game.tiles[id].face,false,false,false) }
        if (game.mode == MahjongMode.SLIDE) {
            val arrow=if(dragging) dragTarget else if(now<hintUntil) slideHint else null
            arrow?.let { drawSlideArrow(c,it,now) }
        }
        if (!animated && now<pathUntil && game.lastPath.size>1) {
            p.style=Paint.Style.STROKE; p.strokeWidth=max(3f,tileW*.06f); p.color=0xffdb8a1b.toInt()
            c.drawPath(matchPath,p); p.style=Paint.Style.FILL
        }
        bursts.removeAll { now-it.start>1200 }
        if (animated) for (burst in bursts) { drawBurst(c,burst,now); moving=true }
        if (active && moving) postInvalidateOnAnimation()
        scheduleOverlayExpiry(now)
    }

    private fun scheduleOverlayExpiry(now: Long = SystemClock.uptimeMillis()) {
        var expiry=0L
        if (active) {
            if (hintUntil>now) expiry=hintUntil
            if (!animated && pathUntil>now && (expiry==0L || pathUntil<expiry)) expiry=pathUntil
        }
        if (expiry==overlayExpiryAt) return
        removeCallbacks(expireOverlays)
        overlayExpiryAt=expiry
        // Hints and paths are static: redraw once at expiry, not on every display frame.
        if (expiry!=0L) postDelayed(expireOverlays,(expiry-now).coerceAtLeast(1L))
    }

    private fun cellAt(x: Float,y: Float): MahjongCell? {
        if(tileW<=0f || tileH<=0f) return null
        val column=floor((x-left)/tileW).toInt(); val row=floor((y-top)/tileH).toInt()
        return if(column in 0 until game.columns && row in 0 until game.rows) MahjongCell(column,row) else null
    }

    private fun drawSlideGrid(c: Canvas,now: Long) {
        val sourceId=if(dragging) pressed else game.selected
        val source=sourceId?.let { game.tiles.getOrNull(it) }
        ensureSlideStops(sourceId)
        p.style=Paint.Style.FILL
        p.shader=iceGradient
        c.drawRoundRect(iceBounds,tileW*.18f,tileW*.18f,p); p.shader=null
        p.style=Paint.Style.STROKE; p.strokeWidth=max(2f,tileW*.045f); p.color=0xff94bdce.toInt()
        c.drawRoundRect(iceBounds,tileW*.18f,tileW*.18f,p)
        for(y in 0 until game.rows) for(x in 0 until game.columns) {
            val index=y*game.columns+x
            if(occupiedCells[index]) continue
            val reachable=reachableCells[index]
            val target=(dragging && dragTarget?.let { it.x==x && it.y==y }==true) ||
                (now<hintUntil && slideHint?.let { it.x==x && it.y==y }==true)
            val rect=gridRects[index]
            p.style=Paint.Style.FILL
            p.color=when { target -> 0x60ffbd36; reachable -> 0x404f90c7; else -> 0x104f90c7 }
            c.drawRoundRect(rect,tileW*.13f,tileW*.13f,p)
            p.style=Paint.Style.STROKE; p.strokeWidth=max(1f,tileW*.025f)
            p.color=if(reachable || target) 0xff5490b9.toInt() else 0x254f90c7
            c.drawRoundRect(rect,tileW*.13f,tileW*.13f,p)
            if(!reachable && !target) {
                p.color=0x88ffffff.toInt(); p.strokeWidth=max(1f,tileW*.025f)
                c.drawLine(rect.left+rect.width()*.19f,rect.bottom-rect.height()*.17f,
                    rect.right-rect.width()*.20f,rect.top+rect.height()*.23f,p)
                c.drawLine(rect.centerX(),rect.centerY(),rect.right-rect.width()*.16f,rect.centerY(),p)
            }
            if(reachable) {
                p.color=0xff397b9b.toInt(); p.strokeWidth=max(2f,tileW*.06f)
                if(source?.x==x) c.drawLine(rect.centerX()-tileW*.14f,rect.centerY(),
                    rect.centerX()+tileW*.14f,rect.centerY(),p)
                else c.drawLine(rect.centerX(),rect.centerY()-tileH*.12f,
                    rect.centerX(),rect.centerY()+tileH*.12f,p)
            }
            if(game.focusOrigin?.let { it.x==x && it.y==y }==true) {
                // This footprint remains while the selected tile tries several slides.
                // Changing focus brings it back here unless a pair has been completed.
                p.style=Paint.Style.STROKE; p.strokeWidth=max(2f,tileW*.04f)
                p.color=0xffb47b21.toInt()
                p.pathEffect=footprintDashes
                footprintRect.set(rect); footprintRect.inset(tileW*.06f,tileH*.06f)
                c.drawRoundRect(footprintRect,tileW*.11f,tileW*.11f,p)
                p.pathEffect=null
            }
        }
        p.style=Paint.Style.FILL
    }

    private fun drawSlideArrow(c: Canvas,move: MahjongSlide,now: Long) {
        val start=location(move.id,now) ?: return
        val endX=left+(move.x+.47f)*tileW; val endY=top+(move.y+.455f)*tileH
        val angle=atan2(endY-start.centerY(),endX-start.centerX())
        val size=tileW*.23f
        p.style=Paint.Style.STROKE; p.strokeWidth=max(3f,tileW*.075f)
        p.strokeCap=Paint.Cap.ROUND; p.strokeJoin=Paint.Join.ROUND; p.color=0xffdb8a1b.toInt()
        arrowPath.rewind()
        arrowPath.apply {
            moveTo(start.centerX(),start.centerY()); lineTo(endX,endY)
            moveTo(endX-cos(angle-.65f)*size,endY-sin(angle-.65f)*size); lineTo(endX,endY)
            lineTo(endX-cos(angle+.65f)*size,endY-sin(angle+.65f)*size)
        }
        c.drawPath(arrowPath,p); p.style=Paint.Style.FILL; p.strokeCap=Paint.Cap.BUTT
    }

    private fun drawTray(c: Canvas) {
        val slot = min(tileW*.90f,(height-trayTop)/1.36f).coerceAtLeast(1f)
        val start = (width-slot*7.42f)/2
        p.style = Paint.Style.FILL; p.color = 0x95ffffff.toInt()
        c.drawRoundRect(start-10f,trayTop-10f,width-start+10f,trayTop+slot*1.35f,slot*.2f,slot*.2f,p)
        for (i in 0..6) {
            val x = start+i*slot*1.06f
            p.color = if (game.tray.size>=6) 0x30e5a334 else 0x204f90c7
            c.drawRoundRect(x,trayTop,x+slot*.94f,trayTop+slot*1.24f,slot*.12f,slot*.12f,p)
        }
    }

    private fun drawBurst(c: Canvas,b: Burst,now: Long) {
        val t = ((now-b.start)/1000f).coerceIn(0f,1.2f)
        val fade = (1f-t).coerceIn(0f,1f)
        for (ghost in b.ghosts) {
            val gather = (t/.26f).coerceIn(0f,1f)
            val scale = if(t<.26f) 1f+.12f*sin(gather*PI).toFloat() else (1-(t-.26f)/.34f).coerceIn(0f,1f)
            if (scale<=0) continue
            val cx = ghost.rect.centerX()+(b.x-ghost.rect.centerX())*gather*.55f
            val cy = ghost.rect.centerY()+(b.y-ghost.rect.centerY())*gather*.55f-t*tileH*.28f
            ghostRect.set(cx-ghost.rect.width()*scale/2,cy-ghost.rect.height()*scale/2,
                cx+ghost.rect.width()*scale/2,cy+ghost.rect.height()*scale/2)
            drawTile(c,ghostRect,ghost.face,false,false,true,(fade*255).toInt())
        }
        p.style = Paint.Style.STROKE; p.color = 0xffffbf46.toInt(); p.alpha = (fade*180).toInt()
        p.strokeWidth = tileW*.035f
        c.drawCircle(b.x,b.y,t*tileW*2.4f,p)
        if(b.path!=null) {
            p.strokeWidth = tileW*.07f; c.drawPath(b.path,p)
        }
        p.style = Paint.Style.FILL; p.alpha=255
        for ((index,s) in b.sparks.withIndex()) {
            val x = s.x+s.vx*t; val y=s.y+s.vy*t+tileW*t*t
            val a = (fade*255).toInt()
            val color = (s.color and 0x00ffffff) or (a shl 24)
            if(index%3==0) art.star(c,x,y,tileW*.07f*fade,color)
            else { p.color=color; c.drawCircle(x,y,tileW*.035f*fade,p) }
        }
        // Pearls arc into the score area instead of floating over the next pair.
        if(t<.85f) repeat(3) { i ->
            val u=((t-i*.05f)/.7f).coerceIn(0f,1f)
            val x=b.x+(width*.12f-b.x)*u
            val y=b.y+(tileH*.12f-b.y)*u-sin(u*PI).toFloat()*tileH
            art.pearl(c,x,y,tileW*.08f*(1-u*.55f))
        }
        if(t<1f) {
            p.color=0xff655091.toInt(); p.alpha=(fade*255).toInt(); p.typeface=Typeface.DEFAULT_BOLD
            p.textAlign=Paint.Align.CENTER; p.textSize=tileW*.42f
            c.drawText(b.scoreLabel,b.x.coerceIn(tileW,width-tileW),
                (b.y-tileH*.6f-t*tileH*.6f).coerceAtLeast(tileH*.45f),p)
            if(b.comboLabel!=null) {
                p.textSize=tileW*.30f; p.color=0xffc07917.toInt(); p.alpha=(fade*255).toInt()
                c.drawText(b.comboLabel,b.x.coerceIn(tileW,width-tileW),
                    (b.y-tileH*.3f-t*tileH*.6f).coerceAtLeast(tileH*.8f),p)
            }
        }
        p.alpha=255
    }

    fun showHint() {
        val ids=game.hint()
        showHint(ids,if(game.mode==MahjongMode.SLIDE && ids.size<2) game.slideHint() else null)
    }

    fun showHint(ids: List<Int>,slide: MahjongSlide?) {
        hints=ids; slideHint=slide
        hintUntil=SystemClock.uptimeMillis()+2600L
        scheduleOverlayExpiry(); invalidate()
    }

    fun refresh() {
        val now=SystemClock.uptimeMillis()
        val old=bounds.keys.mapNotNull { id -> location(id,now)?.let { id to RectF(it) } }.toMap()
        layoutTiles(); startMotion(old,now)
        hints=emptyList(); slideHint=null; hintUntil=0; pathUntil=0
        scheduleOverlayExpiry(now); invalidate()
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); active=true }
    override fun onDetachedFromWindow() { pause(); recycleFaces(); super.onDetachedFromWindow() }
    fun pause() {
        active=false; pressed=null; dragging=false; dragTarget=null
        removeCallbacks(expireOverlays); overlayExpiryAt=0L
    }
    fun resume() { active=true; invalidate() }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled || !active) return false
        when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX=event.x; downY=event.y; dragging=false; dragTarget=null; touchCell=cellAt(event.x,event.y)
                val now=SystemClock.uptimeMillis()
                pressed=drawOrder.asReversed().firstOrNull { location(it.id,now)?.contains(event.x,event.y)==true }?.id
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if(game.mode==MahjongMode.SLIDE && pressed!=null) {
                    val wasDragging=dragging; val previousTarget=dragTarget
                    dragging=hypot(event.x-downX,event.y-downY)>=swipeThreshold
                    val direction=swipeDirection(event.x-downX,event.y-downY)
                    if(dragging) {
                        ensureSlideStops(pressed)
                        val index=when { direction.first>0 -> 0; direction.first<0 -> 1; direction.second>0 -> 2; else -> 3 }
                        dragTarget=slideTargets[index]
                    } else dragTarget=null
                    if (wasDragging!=dragging || previousTarget!=dragTarget) invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                touchCell=cellAt(event.x,event.y)
                val distance=hypot(event.x-downX,event.y-downY)
                val threshold=if(game.mode==MahjongMode.SLIDE) swipeThreshold else tileW*.55f
                if(game.mode==MahjongMode.SLIDE && pressed!=null && distance>=threshold) {
                    val direction=swipeDirection(event.x-downX,event.y-downY)
                    slideInDirection(pressed!!,direction.first,direction.second)
                } else if(distance<threshold) performClick()
                pressed=null; dragging=false; dragTarget=null; invalidate(); return true
            }
            MotionEvent.ACTION_CANCEL -> { pressed=null; dragging=false; dragTarget=null; invalidate(); return true }
        }
        return true
    }
    override fun performClick(): Boolean {
        if (!isEnabled || !active) return false
        super.performClick()
        val id=pressed
        if(id!=null) chooseTile(id)
        else if(game.mode==MahjongMode.SLIDE) {
            val selected=game.selected; val cell=touchCell
            if(selected!=null && cell!=null) {
                val tile=game.tiles[selected]
                if(tile.x==cell.x || tile.y==cell.y)
                    slideInDirection(selected,(cell.x-tile.x).compareTo(0),(cell.y-tile.y).compareTo(0))
            }
        }
        return true
    }

    fun chooseTile(id: Int) = applyMove { game.tap(id) }

    fun slideTile(id: Int,x: Int,y: Int) = applyMove { game.slide(id,x,y) }

    private fun swipeDirection(dx:Float,dy:Float):Pair<Int,Int> =
        if(abs(dx)>=abs(dy)) dx.compareTo(0f) to 0 else 0 to dy.compareTo(0f)

    private fun slideInDirection(id:Int,dx:Int,dy:Int) = applyMove { game.slideDirection(id,dx,dy) }

    private fun applyMove(action: () -> MahjongGame.Result) {
        if(!isEnabled || !active || game.complete) return
        val now=SystemClock.uptimeMillis()
        val before=bounds.keys.mapNotNull { key -> location(key,now)?.let { key to RectF(it) } }.toMap()
        val score=game.score
        val result=action(); hintUntil=0; slideHint=null
        pathUntil=if(result in listOf(MahjongGame.Result.MATCHED,MahjongGame.Result.MOVED) && game.lastPath.isNotEmpty()) now+750L else 0L
        layoutTiles(); startMotion(before,now)
        if(result==MahjongGame.Result.MATCHED) {
            val ghosts=game.lastMatched.mapNotNull { key -> before[key]?.let { Ghost(it,game.tiles[key].face) } }
            if(ghosts.isNotEmpty() && animated) {
                val cx=ghosts.map { it.rect.centerX() }.average().toFloat()
                val cy=ghosts.map { it.rect.centerY() }.average().toFloat()
                val colors=intArrayOf(0xffffbd4f.toInt(),0xffef86ac.toInt(),0xff49bfc3.toInt(),0xff9d8ce4.toInt())
                val sparks=List((24+game.combo*3).coerceAtMost(60)) { index ->
                    val a=Random.nextFloat()*PI*2; val speed=tileW*(1f+Random.nextFloat()*2.8f)
                    Spark(cx,cy,cos(a).toFloat()*speed,sin(a).toFloat()*speed-tileW*.8f,colors[index%4])
                }
                val path=if(game.lastPath.size>1) Path(matchPath) else null
                bursts+=Burst(now,ghosts,sparks,path,cx,cy,
                    context.getString(R.string.mj_gain_short,game.score-score),
                    if(game.combo>=2) context.getString(R.string.mj_multiplier,game.combo) else null)
                while(bursts.size>5) bursts.removeAt(0)
            }
            if(haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
        if(result==MahjongGame.Result.MOVED && haptics) performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        scheduleOverlayExpiry(now); invalidate(); onMove(result)
    }
}
