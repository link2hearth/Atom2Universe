package com.Atom2Universe.app.games.mahjong

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.hub.CachedHubArtworkDrawable
import kotlin.math.*

internal data class MahjongStyle(val glaze: Int, val edge: Int, val light: Int, val motifs: IntArray, val inks: IntArray)

internal object MahjongStyles {
    const val COUNT = 24
    private val hues = intArrayOf(0xff21b49a.toInt(), 0xffee80a4.toInt(), 0xff29b9dc.toInt(),
        0xff9d79e8.toInt(), 0xffffb53c.toInt(), 0xfff0785c.toInt(), 0xff619de7.toInt(),
        0xffa6c84d.toInt(), 0xffd470cf.toInt(), 0xff40cdbc.toInt(), 0xffa18bdf.toInt(), 0xffe99a5a.toInt())
    private val motifs = arrayOf(intArrayOf(0,3,2), intArrayOf(1,4,2), intArrayOf(7,6,4),
        intArrayOf(4,5,3), intArrayOf(0,2,4), intArrayOf(3,1,6), intArrayOf(6,4,7),
        intArrayOf(2,1,0), intArrayOf(3,5,1), intArrayOf(7,2,6), intArrayOf(5,4,0), intArrayOf(1,3,7))
    private val styles by lazy { List(COUNT) { id ->
        val hue = hues[id / 2]
        MahjongStyle(hue, if (id % 2 == 1) 0xffeebc55.toInt() else MahjongArt.darken(hue, .63f),
            MahjongArt.mix(0xfffffff6.toInt(), hue, if (id % 2 == 1) .24f else .055f), motifs[id / 2],
            intArrayOf(MahjongArt.darken(hue, .58f), 0xffc64068.toInt(), 0xff4354a8.toInt()))
    } }
    fun get(id: Int) = styles[id.coerceIn(0, COUNT - 1)]
}

/** Illustrated porcelain; every face retains a distinct suit and countable pips. */
class MahjongArt(private val context: Context? = null) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shape = Path()
    private val oval = RectF()
    private val source = Rect()
    init { context?.let(MahjongAtlas::load) }

    fun tile(c: Canvas, x: Float, y: Float, w: Float, h: Float, face: Int, accent: Int,
             selected: Boolean = false, dim: Boolean = false, back: Boolean = false, style: Int = 0,
             texture: Int = style) {
        if (w <= 0 || h <= 0) return
        val skin = MahjongStyles.get(texture)
        val save = c.save(); c.translate(x, y)
        val r = w * .12f
        p.shader = null; p.style = Paint.Style.FILL; p.color = 0x300e335b
        c.drawRoundRect(w*.025f, h*.09f, w*1.025f, h*1.07f, r, r, p)
        p.color = skin.edge
        c.drawRoundRect(0f, h*.075f, w, h*1.025f, r, r, p)
        p.color = mix(skin.glaze, Color.WHITE, .35f)
        c.drawRoundRect(w*.02f, h*.058f, w*.98f, h*.988f, r, r, p)
        p.shader = LinearGradient(0f, 0f, w*.75f, h,
            if (back) mix(skin.glaze, Color.WHITE, .12f) else Color.WHITE,
            if (back) darken(skin.glaze, .72f) else skin.light, Shader.TileMode.CLAMP)
        c.drawRoundRect(0f, 0f, w, h*.95f, r, r, p)
        val textureSave=c.save()
        shape.reset(); shape.addRoundRect(0f,0f,w,h*.95f,r,r,Path.Direction.CW)
        c.clipPath(shape)
        p.shader=null; p.color=Color.WHITE; p.alpha=if(back) 80 else 210
        oval.set(0f,0f,w,h*.95f)
        MahjongAtlas.texture(c,texture,oval,p,source)
        p.alpha=255
        c.restoreToCount(textureSave)
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = w*.018f
        p.color = if (back) 0x80ffffff.toInt() else mix(skin.edge, Color.WHITE, .6f)
        c.drawRoundRect(w*.06f, h*.045f, w*.94f, h*.90f, r*.65f, r*.65f, p)
        if (texture % 2 == 1) {
            p.color = 0xffd99c34.toInt(); p.strokeWidth = w*.025f
            for (corner in 0..3) {
                val cx = if (corner % 2 == 0) w*.09f else w*.91f
                val cy = if (corner < 2) h*.07f else h*.87f
                c.drawLine(cx-w*.04f, cy, cx+w*.04f, cy, p)
                c.drawLine(cx, cy-w*.04f, cx, cy+w*.04f, p)
            }
        }
        if (back) {
            p.style = Paint.Style.STROKE; p.color = 0xaaffffff.toInt(); p.strokeWidth = w*.018f
            c.drawCircle(w*.5f, h*.46f, w*.28f, p)
            c.drawCircle(w*.5f, h*.46f, w*.235f, p)
            oval.set(w*.12f,h*.15f,w*.88f,h*.77f)
            p.style=Paint.Style.FILL; p.color=Color.WHITE
            if (!MahjongAtlas.draw(c,style,oval,p,source)) motif(c,w*.5f,h*.46f,w*.16f,1,0xffffe1a1.toInt())
        } else {
            val suit = (face / 9).coerceIn(0, 2); val count = face % 9 + 1
            // The material belongs to the collection; large countable marks lead the face.
            p.style=Paint.Style.FILL; p.color=skin.inks[suit]; p.typeface=Typeface.DEFAULT_BOLD
            p.textSize=w*.19f; p.textAlign=Paint.Align.LEFT
            val rank=context?.getString(com.Atom2Universe.app.R.string.mj_number,count) ?: count.toString()
            c.drawText(rank,w*.14f,h*.22f,p)
            motif(c,w*.79f,h*.16f,w*.065f,suitMotifs[suit],skin.inks[suit])
            val points=pipPositions[count-1]
            for(i in points.indices step 2) {
                val cx=w*(.5f+points[i]*.255f)
                val cy=h*(.52f+points[i+1]*.19f)
                if(count<=2) {
                    val size=w*(if(count==1) .58f else .36f)
                    oval.set(cx-size/2,cy-size/2,cx+size/2,cy+size/2)
                    p.style=Paint.Style.FILL; p.color=Color.WHITE
                    if(!MahjongAtlas.draw(c,style,oval,p,source))
                        motif(c,cx,cy,size*.35f,suitMotifs[suit],skin.inks[suit])
                } else {
                    val radius=w*(if(count<=5) .105f else .087f)
                    motif(c,cx,cy+w*.012f,radius,suitMotifs[suit],0x403b3452)
                    motif(c,cx,cy,radius,suitMotifs[suit],skin.inks[suit])
                }
            }
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = w*.025f; p.color = 0xc0ffffff.toInt()
        oval.set(w*.06f, h*.03f, w*.34f, h*.24f); c.drawArc(oval, 190f, 90f, false, p)
        if (selected) {
            p.strokeWidth = w*.045f; p.color = 0xffffca52.toInt()
            c.drawRoundRect(-w*.03f, -h*.02f, w*1.03f, h*.98f, r, r, p)
        }
        if (dim) { p.style = Paint.Style.FILL; p.color = 0x20264172; c.drawRoundRect(0f,0f,w,h*.95f,r,r,p) }
        p.style = Paint.Style.FILL; c.restoreToCount(save)
    }

    private fun motif(c: Canvas, x: Float, y: Float, r: Float, kind: Int, color: Int) {
        p.shader = null; p.color = color; p.style = Paint.Style.FILL; p.strokeCap = Paint.Cap.ROUND
        when (kind) {
            0 -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = r*.3f; c.drawCircle(x,y,r*.8f,p)
                p.style = Paint.Style.FILL; c.drawCircle(x,y,r*.34f,p)
            }
            1 -> {
                repeat(5) { i ->
                    val a = i * PI * 2 / 5 - PI / 2
                    c.drawCircle(x+cos(a).toFloat()*r*.53f, y+sin(a).toFloat()*r*.53f, r*.43f,p)
                }
                p.color = 0xffffd777.toInt(); c.drawCircle(x,y,r*.25f,p)
            }
            2 -> {
                shape.reset(); shape.moveTo(x-r*.75f,y+r*.8f)
                shape.cubicTo(x-r*1.1f,y-r*.7f,x+r*.4f,y-r*1.1f,x+r*.75f,y-r*.8f)
                shape.cubicTo(x+r,y+r*.5f,x-r*.2f,y+r*1.1f,x-r*.75f,y+r*.8f)
                c.drawPath(shape,p); p.color = 0x90ffffff.toInt(); p.strokeWidth = r*.12f
                c.drawLine(x-r*.48f,y+r*.5f,x+r*.45f,y-r*.5f,p)
            }
            3 -> {
                shape.reset(); shape.moveTo(x,y-r); shape.lineTo(x+r*.8f,y)
                shape.lineTo(x,y+r); shape.lineTo(x-r*.8f,y); shape.close(); c.drawPath(shape,p)
                p.color = 0x60ffffff; shape.reset(); shape.moveTo(x,y-r); shape.lineTo(x,y+r)
                shape.lineTo(x-r*.8f,y); shape.close(); c.drawPath(shape,p)
            }
            4 -> star(c,x,y,r,color)
            5 -> {
                shape.reset(); shape.moveTo(x+r*.65f,y-r*.76f)
                shape.cubicTo(x-r*1.25f,y-r*1.15f,x-r*1.25f,y+r*1.15f,x+r*.65f,y+r*.76f)
                shape.cubicTo(x-r*.20f,y+r*.4f,x-r*.20f,y-r*.4f,x+r*.65f,y-r*.76f)
                c.drawPath(shape,p)
            }
            6 -> {
                p.style = Paint.Style.STROKE; p.strokeWidth = r*.30f
                repeat(2) { i ->
                    shape.reset(); shape.moveTo(x-r,y-r*.4f+i*r*.7f)
                    shape.cubicTo(x-r*.35f,y-r*1.05f+i*r*.7f,x+r*.3f,y+r*.25f+i*r*.7f,x+r,y-r*.4f+i*r*.7f)
                    c.drawPath(shape,p)
                }
            }
            else -> {
                oval.set(x-r,y-r,x+r,y+r); c.drawArc(oval,190f,160f,true,p)
                p.color = 0x90ffffff.toInt(); p.strokeWidth = r*.12f
                for (i in -1..1) c.drawLine(x,y+r*.2f,x+i*r*.65f,y-r*.7f,p)
            }
        }
        p.style = Paint.Style.FILL
    }

    fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = color; shape.reset()
        repeat(10) { i ->
            val a = -PI/2 + i*PI/5; val radius = if (i%2==0) r else r*.45f
            val px = x+cos(a).toFloat()*radius; val py = y+sin(a).toFloat()*radius
            if (i==0) shape.moveTo(px,py) else shape.lineTo(px,py)
        }
        shape.close(); c.drawPath(shape,p)
    }

    fun pearl(c: Canvas, x: Float, y: Float, r: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(x-r*.35f,y-r*.4f,r*1.6f,
            intArrayOf(Color.WHITE,0xffb7f7ed.toInt(),0xff70a9df.toInt()),null,Shader.TileMode.CLAMP)
        c.drawCircle(x,y,r,p); p.shader = null
        p.color = 0xccffffff.toInt(); c.drawCircle(x-r*.32f,y-r*.32f,r*.22f,p)
    }

    fun chest(c: Canvas, x: Float, y: Float, w: Float, open: Float = 0f) {
        oval.set(x,y-w*.12f,x+w,y+w*.88f)
        p.shader=null; p.style=Paint.Style.FILL; p.color=Color.WHITE
        if (MahjongAtlas.treasure(c,if(open>.4f) 1 else 0,oval,p,source)) return
        val h = w*.72f; p.style = Paint.Style.FILL
        p.color = 0x250f4064; c.drawOval(x-w*.03f,y+h*.90f,x+w*1.03f,y+h*1.12f,p)
        p.shader = LinearGradient(x,y,x,y+h,0xffffe49c.toInt(),0xffe79a30.toInt(),Shader.TileMode.CLAMP)
        c.drawRoundRect(x,y+h*.27f,x+w,y+h,w*.12f,w*.12f,p); p.shader = null
        p.color = 0xffd96d70.toInt(); c.drawRoundRect(x+w*.12f,y+h*.34f,x+w*.88f,y+h*.87f,w*.07f,w*.07f,p)
        c.save(); c.rotate(-open*24f,x+w*.1f,y+h*.3f)
        p.color = 0xffffd578.toInt(); c.drawRoundRect(x-w*.03f,y-open*h*.18f,x+w*1.03f,y+h*.36f-open*h*.18f,w*.1f,w*.1f,p)
        p.color = 0xfff4a75c.toInt(); c.drawRoundRect(x+w*.13f,y+h*.08f-open*h*.18f,x+w*.87f,y+h*.25f-open*h*.18f,w*.04f,w*.04f,p)
        c.restore(); star(c,x+w*.5f,y+h*.57f,w*.14f,0xfffff2b9.toInt())
    }

    fun trophy(c: Canvas,x: Float,y: Float,w: Float) {
        oval.set(x,y,x+w,y+w); p.shader=null; p.style=Paint.Style.FILL; p.color=Color.WHITE
        MahjongAtlas.treasure(c,2,oval,p,source)
    }

    fun backdrop(c: Canvas, w: Float, h: Float, accent: Int) {
        if (w <= 0 || h <= 0) return
        val surface=context?.let { com.Atom2Universe.app.AppearanceStyle.color(it,com.Atom2Universe.app.R.attr.a2uSurfaceColor) } ?: 0xffedf9ff.toInt()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f,0f,w,h,mix(accent,0xffedf9ff.toInt(),.86f),0xffe5e5ff.toInt(),Shader.TileMode.CLAMP)
        p.shader=RadialGradient(w*.5f,h*.42f,max(w,h)*.8f,mix(surface,accent,.12f),surface,Shader.TileMode.CLAMP)
        c.drawRoundRect(0f,0f,w,h,w*.045f,w*.045f,p); p.shader = null
        p.color = (accent and 0x00ffffff) or 0x16000000; p.style = Paint.Style.STROKE; p.strokeWidth = max(1f,w*.001f)
        for (i in 0..2) {
            c.save(); c.rotate(i*60f,w*.5f,h*.5f)
            c.drawOval(w*.12f,h*.17f,w*.88f,h*.83f,p); c.restore()
        }
        p.color=(accent and 0x00ffffff) or 0x30000000
        c.drawRoundRect(w*.015f,h*.01f,w*.985f,h*.99f,w*.04f,w*.04f,p)
        p.style = Paint.Style.FILL
    }

    fun modeScene(c: Canvas, w: Float, h: Float, mode: Int, style: Int) {
        val color = modeColors[mode.coerceIn(0,7)]
        p.shader = LinearGradient(0f,0f,w,h,mix(color,Color.WHITE,.22f),color,Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL; c.drawRect(0f,0f,w,h,p); p.shader = null
        p.color = 0x28ffffff; c.drawCircle(w*.86f,h*.08f,w*.57f,p); c.drawCircle(w*.04f,h*.83f,w*.30f,p)
        val tw = min(w*.25f,h*.32f); val th = tw*1.24f
        fun piece(cx: Float, cy: Float, face: Int, rotation: Float = 0f, back: Boolean = false) {
            c.save(); c.rotate(rotation,cx,cy)
            tile(c,cx-tw/2,cy-th/2,tw,th,face,color,back=back,style=style); c.restore()
        }
        when (mode) {
            0 -> { for (i in 0..3) piece(w*(.22f+i*.19f),h*.4f,i*5); piece(w*.5f,h*.28f,10,-8f) }
            1 -> { for (i in 0..2) piece(w*(.25f+i*.25f),h*(.32f+abs(i-1)*.07f),1,(i-1)*9f) }
            2 -> {
                p.color = 0xffffedb5.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = w*.025f
                shape.reset(); shape.moveTo(w*.25f,h*.4f); shape.lineTo(w*.25f,h*.16f)
                shape.lineTo(w*.76f,h*.16f); shape.lineTo(w*.76f,h*.4f); c.drawPath(shape,p)
                piece(w*.25f,h*.4f,4,-9f); piece(w*.76f,h*.4f,4,9f)
            }
            3 -> {
                p.color=0xffffedb5.toInt(); p.style=Paint.Style.STROKE; p.strokeWidth=w*.025f
                c.drawArc(RectF(w*.18f,h*.13f,w*.82f,h*.60f),-65f,255f,false,p)
                c.drawLine(w*.25f,h*.16f,w*.18f,h*.28f,p); c.drawLine(w*.18f,h*.28f,w*.34f,h*.27f,p)
                piece(w*.38f,h*.28f,11,-8f); piece(w*.61f,h*.43f,11,8f)
            }
            4 -> {
                // A frozen lane and a snow crystal identify the full-distance slide rule.
                p.style=Paint.Style.FILL; p.color=0x35ffffff
                c.drawRoundRect(w*.16f,h*.12f,w*.83f,h*.43f,w*.035f,w*.035f,p)
                p.color=0x50ffffff; p.style=Paint.Style.STROKE; p.strokeWidth=w*.012f
                c.drawRoundRect(w*.57f,h*.14f,w*.82f,h*.45f,w*.035f,w*.035f,p)
                p.color=0xffffedb5.toInt(); p.strokeWidth=w*.025f
                c.drawLine(w*.32f,h*.25f,w*.68f,h*.25f,p)
                c.drawLine(w*.68f,h*.25f,w*.60f,h*.19f,p); c.drawLine(w*.68f,h*.25f,w*.60f,h*.31f,p)
                piece(w*.27f,h*.25f,2); piece(w*.70f,h*.56f,2)
                p.color=0xc0ffffff.toInt(); p.style=Paint.Style.STROKE; p.strokeWidth=w*.013f
                for(angle in listOf(0f,60f,120f)) {
                    c.save(); c.rotate(angle,w*.30f,h*.52f)
                    c.drawLine(w*.23f,h*.52f,w*.37f,h*.52f,p); c.restore()
                }
                p.style=Paint.Style.FILL
            }
            5 -> { piece(w*.3f,h*.37f,3,-10f); piece(w*.69f,h*.37f,14,10f); star(c,w*.5f,h*.18f,w*.07f,0xffffe896.toInt()) }
            6 -> { for(i in 0..2) piece(w*(.23f+i*.25f),h*(.45f-i*.09f),i*10,(i-1)*12f); star(c,w*.83f,h*.15f,w*.09f,0xffffe48b.toInt()) }
            else -> { chest(c,w*.24f,h*.19f,w*.52f,.6f); star(c,w*.22f,h*.18f,w*.06f,Color.WHITE) }
        }
        for(i in 0..5) star(c,w*(.11f+i*.155f),h*(.09f+i%3*.19f),w*.018f,0x90ffffff.toInt())
        p.shader = LinearGradient(0f,h*.55f,0f,h,Color.TRANSPARENT,0x88344966.toInt(),Shader.TileMode.CLAMP)
        c.drawRect(0f,h*.55f,w,h,p); p.shader = null
    }

    companion object {
        private val suitMotifs=intArrayOf(0,3,4)
        private val pipPositions=arrayOf(
            floatArrayOf(0f,0f),
            floatArrayOf(-.6f,-.65f,.6f,.65f),
            floatArrayOf(-1f,-1f,0f,0f,1f,1f),
            floatArrayOf(-1f,-1f,1f,-1f,-1f,1f,1f,1f),
            floatArrayOf(-1f,-1f,1f,-1f,0f,0f,-1f,1f,1f,1f),
            floatArrayOf(-1f,-1f,1f,-1f,-1f,0f,1f,0f,-1f,1f,1f,1f),
            floatArrayOf(-1f,-1f,0f,-1f,1f,-1f,0f,0f,-1f,1f,0f,1f,1f,1f),
            floatArrayOf(-1f,-1f,0f,-1f,1f,-1f,-1f,0f,1f,0f,-1f,1f,0f,1f,1f,1f),
            floatArrayOf(-1f,-1f,0f,-1f,1f,-1f,-1f,0f,0f,0f,1f,0f,-1f,1f,0f,1f,1f,1f))
        val accents = intArrayOf(0xff21b49a.toInt(),0xffee80a4.toInt(),0xff29b9dc.toInt(),0xff9d79e8.toInt(),0xffffb53c.toInt(),0xfff0785c.toInt())
        val modeColors = intArrayOf(0xff36b9aa.toInt(),0xffe17cae.toInt(),0xff5da9eb.toInt(),0xffaa84e5.toInt(),
            0xff68b8cd.toInt(),0xfff18d79.toInt(),0xff6387da.toInt(),0xffd58eb8.toInt())
        fun darken(c: Int, a: Float) = Color.rgb((Color.red(c)*a).toInt(),(Color.green(c)*a).toInt(),(Color.blue(c)*a).toInt())
        fun mix(a: Int,b: Int,t: Float) = Color.rgb((Color.red(a)*(1-t)+Color.red(b)*t).toInt(),
            (Color.green(a)*(1-t)+Color.green(b)*t).toInt(),(Color.blue(a)*(1-t)+Color.blue(b)*t).toInt())
    }
}

class MahjongHubTileDrawable(context: Context) : CachedHubArtworkDrawable() {
    private val art=MahjongArt(context.applicationContext)
    override fun render(canvas: Canvas, w: Float, h: Float) { art.modeScene(canvas,w,h,1,3) }
}
