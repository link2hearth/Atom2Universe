package com.Atom2Universe.app.games.mahjong

import android.content.Context
import android.graphics.*
import com.Atom2Universe.app.R
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

/**
 * Tuiles de mahjong traditionnelles en relief : face ivoire (légèrement teintée par la texture
 * de la collection), tranche ivoire puis dos coloré, visibles à gauche et en bas.
 * La porcelaine équipée illustre les quatre fleurs ; les saisons ont leurs propres dessins.
 */
class MahjongArt(private val context: Context? = null) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shape = Path()
    private val oval = RectF()
    private val source = Rect()
    private val numerals = context?.resources?.getStringArray(R.array.mj_glyph_numerals) ?: emptyArray()
    private val winds = context?.resources?.getStringArray(R.array.mj_glyph_winds) ?: emptyArray()
    private val dragons = context?.resources?.getStringArray(R.array.mj_glyph_dragons) ?: emptyArray()
    private val wan = context?.getString(R.string.mj_glyph_wan) ?: ""
    private val serif = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    /** Étoile de rayon 1 construite une fois : chaque étoile n'est plus qu'un déplacement et une échelle. */
    private val unitStar = Path().apply {
        repeat(10) { i ->
            val a = -PI/2 + i*PI/5; val radius = if (i%2==0) 1f else .45f
            val px = cos(a).toFloat()*radius; val py = sin(a).toFloat()*radius
            if (i==0) moveTo(px,py) else lineTo(px,py)
        }
        close()
    }
    private var shadowBlur: BlurMaskFilter? = null
    private var shadowRadius = -1f
    init { context?.let(MahjongAtlas::load) }

    /** Couleur du dos (et de la tranche) selon la texture de la collection. */
    fun backColor(texture: Int) = mix(MahjongStyles.get(texture).glaze, 0xff0c3b33.toInt(), .28f)

    /**
     * Face en (x, y, w, h) ; l'épaisseur [depth] déborde à gauche et en bas, l'ombre au-delà.
     */
    fun tile(c: Canvas, x: Float, y: Float, w: Float, h: Float, face: Int, accent: Int,
             selected: Boolean = false, dim: Boolean = false, back: Boolean = false, style: Int = 0,
             texture: Int = style, depth: Float = w * .13f, shadow: Boolean = true) {
        if (w <= 0 || h <= 0) return
        val save = c.save(); c.translate(x, y)
        val r = w * .12f
        p.shader = null; p.style = Paint.Style.FILL
        if (shadow) {
            p.color = 0x50000000
            val blur = max(1f, w * .05f)
            if (blur != shadowRadius) { shadowRadius = blur; shadowBlur = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL) }
            p.maskFilter = shadowBlur
            c.drawRoundRect(-depth * 1.55f, depth * 1.35f, w - depth * .55f, h + depth * 1.75f, r, r, p)
            p.maskFilter = null
        }
        // Tranche : dos coloré au loin, ivoire contre la face.
        val backTone = backColor(texture)
        val steps = 9
        for (k in steps downTo 1) {
            val t = k / steps.toFloat(); val off = depth * t
            p.color = if (t > .45f) mix(backTone, darken(backTone, .62f), (t - .45f) / .55f)
                else mix(0xffe9dfc6.toInt(), 0xffcdbd98.toInt(), t / .45f)
            c.drawRoundRect(-off, off, w - off, h + off, r, r, p)
        }
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, w * .012f); p.color = 0x40000000
        c.drawRoundRect(-depth, depth, w - depth, h + depth, r, r, p)
        p.style = Paint.Style.FILL
        // Face.
        if (back) {
            p.shader = LinearGradient(0f, 0f, w, h, mix(backTone, Color.WHITE, .25f), darken(backTone, .85f), Shader.TileMode.CLAMP)
            c.drawRoundRect(0f, 0f, w, h, r, r, p); p.shader = null
            oval.set(w * .14f, h * .2f, w * .86f, h * .2f + w * .72f)
            p.color = Color.WHITE; p.alpha = 230
            MahjongAtlas.draw(c, style, oval, p, source); p.alpha = 255
        } else {
            p.shader = LinearGradient(0f, 0f, w * .8f, h, 0xfffffdf6.toInt(), 0xffefe5cf.toInt(), Shader.TileMode.CLAMP)
            c.drawRoundRect(0f, 0f, w, h, r, r, p); p.shader = null
            val clip = c.save()
            shape.reset(); shape.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW); c.clipPath(shape)
            p.color = Color.WHITE; p.alpha = 64
            oval.set(0f, 0f, w, h); MahjongAtlas.texture(c, texture, oval, p, source); p.alpha = 255
            c.restoreToCount(clip)
            symbol(c, face, w * .1f, h * .08f, w * .8f, h * .84f, style)
        }
        // Biseau : lumière en haut à gauche, ombre en bas à droite.
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, w * .03f)
        p.shader = LinearGradient(0f, 0f, w, h, intArrayOf(0xd0ffffff.toInt(), 0x00ffffff, 0x2a000000),
            floatArrayOf(0f, .5f, 1f), Shader.TileMode.CLAMP)
        c.drawRoundRect(w * .02f, w * .02f, w - w * .02f, h - w * .02f, r * .85f, r * .85f, p)
        p.shader = null; p.strokeWidth = max(1f, w * .01f); p.color = 0x30000000
        c.drawRoundRect(0f, 0f, w, h, r, r, p)
        p.style = Paint.Style.FILL
        if (dim) { p.color = 0x30122030; c.drawRoundRect(0f, 0f, w, h, r, r, p) }
        if (selected) {
            p.color = 0x55ffd34d; c.drawRoundRect(0f, 0f, w, h, r, r, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * .05f; p.color = 0xffffb627.toInt()
            c.drawRoundRect(0f, 0f, w, h, r, r, p); p.style = Paint.Style.FILL
        }
        c.restoreToCount(save)
    }

    // ---- Symboles ---------------------------------------------------------------------------

    private fun symbol(c: Canvas, face: Int, x: Float, y: Float, w: Float, h: Float, porcelain: Int) {
        when {
            face < 9 -> dots(c, face + 1, x, y, w, h)
            face < 18 -> bamboo(c, face - 8, x, y, w, h)
            face < 27 -> character(c, face - 17, x, y, w, h)
            face < 31 -> glyph(c, winds.getOrNull(face - 27), x, y, w, h, INK, .66f)
            face == 31 -> glyph(c, dragons.getOrNull(0), x, y, w, h, RED, .66f)
            face == 32 -> glyph(c, dragons.getOrNull(1), x, y, w, h, GREEN, .66f)
            face == 33 -> whiteDragon(c, x, y, w, h)
            // Une famille = un seul dessin : les quatre fleurs portent la porcelaine équipée,
            // les quatre saisons la lanterne (le soleil si la lanterne est déjà équipée).
            face < 38 -> bonus(c, porcelain, x, y, w, h)
            else -> bonus(c, if (porcelain == LANTERN) SUN else LANTERN, x, y, w, h)
        }
    }

    private fun pip(c: Canvas, cx: Float, cy: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL; p.shader = null
        p.color = color; c.drawCircle(cx, cy, r, p)
        p.color = 0xfffffbf0.toInt(); c.drawCircle(cx, cy, r * .66f, p)
        p.color = color; c.drawCircle(cx, cy, r * .4f, p)
        p.color = 0x90ffffff.toInt(); c.drawCircle(cx - r * .14f, cy - r * .14f, r * .13f, p)
        p.style = Paint.Style.STROKE; p.strokeWidth = r * .1f; p.color = darken(color, .75f)
        c.drawCircle(cx, cy, r, p); p.style = Paint.Style.FILL
    }

    private fun dots(c: Canvas, rank: Int, x: Float, y: Float, w: Float, h: Float) {
        fun at(fx: Float, fy: Float, r: Float, color: Int) = pip(c, x + w * fx, y + h * fy, w * r, color)
        when (rank) {
            1 -> {
                val cx = x + w / 2; val cy = y + h / 2; val r = min(w, h) * .45f
                p.style = Paint.Style.FILL
                p.color = GREEN; c.drawCircle(cx, cy, r, p)
                p.color = 0xfffffbf0.toInt(); c.drawCircle(cx, cy, r * .84f, p)
                p.color = BLUE; c.drawCircle(cx, cy, r * .72f, p)
                p.color = 0xfffffbf0.toInt()
                repeat(8) { i -> val a = i * PI / 4; c.drawCircle(cx + cos(a).toFloat() * r * .56f, cy + sin(a).toFloat() * r * .56f, r * .08f, p) }
                p.color = RED; c.drawCircle(cx, cy, r * .38f, p)
                p.color = 0xfffffbf0.toInt(); c.drawCircle(cx, cy, r * .22f, p)
                p.color = RED; c.drawCircle(cx, cy, r * .11f, p)
            }
            2 -> { at(.5f, .26f, .25f, GREEN); at(.5f, .74f, .25f, BLUE) }
            3 -> { at(.22f, .18f, .2f, BLUE); at(.5f, .5f, .2f, RED); at(.78f, .82f, .2f, GREEN) }
            4 -> { at(.27f, .27f, .22f, BLUE); at(.73f, .27f, .22f, GREEN); at(.27f, .73f, .22f, GREEN); at(.73f, .73f, .22f, BLUE) }
            5 -> { at(.24f, .21f, .2f, BLUE); at(.76f, .21f, .2f, GREEN); at(.5f, .5f, .2f, RED); at(.24f, .79f, .2f, GREEN); at(.76f, .79f, .2f, BLUE) }
            6 -> { for (fx in floatArrayOf(.28f, .72f)) { at(fx, .16f, .18f, GREEN); at(fx, .56f, .18f, RED); at(fx, .85f, .18f, RED) } }
            7 -> {
                at(.2f, .1f, .15f, GREEN); at(.5f, .22f, .15f, GREEN); at(.8f, .34f, .15f, GREEN)
                for (fx in floatArrayOf(.3f, .7f)) { at(fx, .62f, .16f, RED); at(fx, .88f, .16f, RED) }
            }
            8 -> for (fy in floatArrayOf(.12f, .37f, .63f, .88f)) { at(.28f, fy, .16f, BLUE); at(.72f, fy, .16f, BLUE) }
            else -> {
                val colors = intArrayOf(BLUE, RED, GREEN)
                for ((row, fy) in floatArrayOf(.15f, .5f, .85f).withIndex()) for (fx in floatArrayOf(.17f, .5f, .83f)) at(fx, fy, .15f, colors[row])
            }
        }
    }

    private fun stick(c: Canvas, cx: Float, cy: Float, length: Float, color: Int, angle: Float = 0f) {
        val half = length / 2; val wide = length * .16f
        c.save(); c.rotate(angle, cx, cy)
        p.style = Paint.Style.FILL; p.shader = null
        p.color = color
        c.drawRoundRect(cx - wide / 2, cy - half, cx + wide / 2, cy + half, wide * .45f, wide * .45f, p)
        p.color = darken(color, .7f)
        for (f in floatArrayOf(-.42f, 0f, .42f)) {
            c.drawRoundRect(cx - wide * .62f, cy + f * length - wide * .17f, cx + wide * .62f, cy + f * length + wide * .17f, wide * .15f, wide * .15f, p)
        }
        p.color = 0x70ffffff
        c.drawRoundRect(cx - wide * .3f, cy - half * .86f, cx - wide * .08f, cy + half * .86f, wide * .1f, wide * .1f, p)
        c.restore()
    }

    private fun bamboo(c: Canvas, rank: Int, x: Float, y: Float, w: Float, h: Float) {
        fun at(fx: Float, fy: Float, length: Float, color: Int = GREEN, angle: Float = 0f) = stick(c, x + w * fx, y + h * fy, h * length, color, angle)
        when (rank) {
            1 -> {
                // Une grande tige et ses feuilles, en souvenir de l'oiseau du 1.
                at(.5f, .52f, .86f, GREEN)
                p.color = GREEN; p.style = Paint.Style.FILL
                for ((side, fy) in listOf(-1f to .3f, 1f to .5f, -1f to .7f)) {
                    shape.reset()
                    val bx = x + w * .5f; val by = y + h * fy
                    shape.moveTo(bx, by)
                    shape.quadTo(bx + side * w * .3f, by - h * .12f, bx + side * w * .46f, by - h * .02f)
                    shape.quadTo(bx + side * w * .26f, by + h * .03f, bx, by)
                    c.drawPath(shape, p)
                }
                p.color = RED; c.drawCircle(x + w * .5f, y + h * .07f, w * .07f, p)
            }
            2 -> { at(.5f, .26f, .42f, BLUE); at(.5f, .74f, .42f, GREEN) }
            3 -> { at(.5f, .26f, .42f, BLUE); at(.3f, .74f, .42f); at(.7f, .74f, .42f) }
            4 -> { at(.3f, .26f, .42f, BLUE); at(.7f, .26f, .42f); at(.3f, .74f, .42f); at(.7f, .74f, .42f, BLUE) }
            5 -> { at(.2f, .26f, .42f); at(.8f, .26f, .42f, BLUE); at(.5f, .5f, .42f, RED); at(.2f, .74f, .42f, BLUE); at(.8f, .74f, .42f) }
            6 -> for (fy in floatArrayOf(.26f, .74f)) for ((i, fx) in floatArrayOf(.2f, .5f, .8f).withIndex()) at(fx, fy, .42f, if (fy < .5f) GREEN else if (i == 1) BLUE else GREEN)
            7 -> { at(.5f, .15f, .28f, RED); for (fy in floatArrayOf(.5f, .84f)) for (fx in floatArrayOf(.2f, .5f, .8f)) at(fx, fy, .28f, if (fy < .7f) GREEN else BLUE) }
            8 -> {
                for ((fy, sign) in listOf(.26f to 1f, .74f to -1f)) {
                    at(.14f, fy, .42f); at(.86f, fy, .42f)
                    at(.37f, fy, .42f, BLUE, 18f * sign); at(.63f, fy, .42f, BLUE, -18f * sign)
                }
            }
            else -> for (fy in floatArrayOf(.16f, .5f, .84f)) for (fx in floatArrayOf(.2f, .5f, .8f)) at(fx, fy, .28f, if (fx == .5f) RED else GREEN)
        }
    }

    private fun glyph(c: Canvas, text: String?, x: Float, y: Float, w: Float, h: Float, color: Int, share: Float, top: Float = .5f) {
        if (text.isNullOrEmpty()) return
        p.style = Paint.Style.FILL; p.shader = null; p.color = color
        p.typeface = serif; p.textAlign = Paint.Align.CENTER
        p.textSize = min(h * share, w * 1.02f)
        val cy = y + h * top
        c.drawText(text, x + w / 2, cy - (p.ascent() + p.descent()) / 2, p)
    }

    private fun character(c: Canvas, rank: Int, x: Float, y: Float, w: Float, h: Float) {
        glyph(c, numerals.getOrNull(rank - 1), x, y, w, h, INK, .4f, .25f)
        glyph(c, wan, x, y, w, h, RED, .5f, .7f)
    }

    private fun whiteDragon(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        p.style = Paint.Style.STROKE; p.color = BLUE; p.shader = null
        p.strokeWidth = w * .07f
        c.drawRoundRect(x + w * .08f, y + h * .1f, x + w * .92f, y + h * .9f, w * .06f, w * .06f, p)
        p.strokeWidth = w * .03f
        c.drawRoundRect(x + w * .22f, y + h * .21f, x + w * .78f, y + h * .79f, w * .03f, w * .03f, p)
        p.style = Paint.Style.FILL
        for (fx in floatArrayOf(.08f, .92f)) for (fy in floatArrayOf(.1f, .9f)) c.drawCircle(x + w * fx, y + h * fy, w * .06f, p)
    }

    private fun bonus(c: Canvas, illustration: Int, x: Float, y: Float, w: Float, h: Float) {
        val side = min(w * 1.08f, h)
        oval.set(x + (w - side) / 2, y + (h - side) / 2, x + (w + side) / 2, y + (h + side) / 2)
        p.shader = null; p.style = Paint.Style.FILL; p.color = Color.WHITE
        MahjongAtlas.draw(c, illustration, oval, p, source)
    }

    // ---- Petits dessins de l'interface ------------------------------------------------------

    fun star(c: Canvas, x: Float, y: Float, r: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = color; p.shader = null
        val save = c.save(); c.translate(x, y); c.scale(r, r)
        c.drawPath(unitStar, p); c.restoreToCount(save)
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

    /**
     * Le tapis de jeu : un vert de jade profond et des vagues seigaiha très discrètes.
     * Dessiné une fois par taille dans un bitmap par la vue du plateau.
     */
    fun table(c: Canvas, w: Float, h: Float, id: Int = 0, thumbnail: Boolean = false) {
        if (w <= 0 || h <= 0) return
        p.style = Paint.Style.FILL
        val landscape = context?.let { if (thumbnail) MahjongAtlas.tableThumb(it, id) else MahjongAtlas.table(it, id) }
        if (landscape != null) {
            // Le paysage couvre tout (recadré au centre), assombri pour que les tuiles ressortent :
            // plus sombre en haut sous la bande du score, un peu en bas, et sur les bords.
            val scale = max(w / landscape.width, h / landscape.height)
            val sw = w / scale; val sh = h / scale
            source.set(((landscape.width - sw) / 2).toInt(), ((landscape.height - sh) / 2).toInt(),
                ((landscape.width + sw) / 2).toInt(), ((landscape.height + sh) / 2).toInt())
            if (thumbnail) {
                oval.set(0f, 0f, w, h)
                p.color = Color.WHITE; c.drawBitmap(landscape, source, oval, p)
                veil(c, w, h, id)
                return
            }
            // Plein écran : le recadrage et ses voiles sont cuits une fois à la taille du recadrage,
            // puis posés d'un seul trait (quatre couches plein écran à chaque trame sinon).
            val bw = source.width().coerceAtLeast(1); val bh = source.height().coerceAtLeast(1)
            val key = (id.toLong() shl 48) or (w.toLong() shl 24) or h.toLong()
            val baked = MahjongAtlas.bakedTable(key) {
                Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bake ->
                    val bc = Canvas(bake)
                    p.color = Color.WHITE; bc.drawBitmap(landscape, source, Rect(0, 0, bw, bh), p)
                    bc.scale(bw / w, bh / h); veil(bc, w, h, id)
                }
            }
            oval.set(0f, 0f, w, h)
            p.color = Color.WHITE; c.drawBitmap(baked, null, oval, p)
            return
        }
        p.shader = LinearGradient(0f, 0f, 0f, h, 0xff17534a.toInt(), 0xff0b2c28.toInt(), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
        val r = max(w, h) / 22f
        p.style = Paint.Style.STROKE; p.strokeWidth = max(1f, r * .07f); p.color = 0x12ffffff
        var row = 0; var cy = -r
        while (cy < h + r * 2) {
            val shift = if (row % 2 == 0) 0f else r
            var cx = -r * 2 + shift
            while (cx < w + r * 2) {
                for (k in 0..3) { val rr = r * (1f - k * .24f); oval.set(cx - rr, cy - rr, cx + rr, cy + rr); c.drawArc(oval, 180f, 180f, false, p) }
                cx += r * 2
            }
            cy += r * .5f; row++
        }
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(w / 2, h * .45f, max(w, h) * .75f, intArrayOf(0x00000000, 0x00000000, 0x66000000),
            floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
    }

    /** Voiles du paysage : plus sombre en haut sous la bande du score, un peu en bas, et sur les bords. */
    private fun veil(c: Canvas, w: Float, h: Float, id: Int) {
        val dim = MahjongAtlas.tableDim(id)
        if (dim > 0f) { p.color = Color.argb((dim * 255).toInt(), 0, 0, 0); c.drawRect(0f, 0f, w, h, p) }
        p.shader = LinearGradient(0f, 0f, 0f, h, intArrayOf(0x70000000, 0x10000000, 0x08000000, 0x50000000),
            floatArrayOf(0f, .18f, .72f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = RadialGradient(w / 2, h * .5f, max(w, h) * .75f, intArrayOf(0x00000000, 0x00000000, 0x45000000),
            floatArrayOf(0f, .62f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p); p.shader = null
    }

    /** Cartes du menu : une petite scène de vraies tuiles qui montre la règle du mode. */
    fun modeScene(c: Canvas, w: Float, h: Float, mode: Int, style: Int, texture: Int = style) {
        val color = modeColors[mode.coerceIn(0,7)]
        p.shader = LinearGradient(0f,0f,w,h,mix(color,Color.WHITE,.18f),darken(color,.82f),Shader.TileMode.CLAMP)
        p.style = Paint.Style.FILL; c.drawRect(0f,0f,w,h,p); p.shader = null
        p.color = 0x22ffffff; c.drawCircle(w*.86f,h*.08f,w*.57f,p); c.drawCircle(w*.04f,h*.83f,w*.30f,p)
        val tw = min(w*.2f,h*.24f); val th = tw*1.33f; val d = tw*.13f
        fun piece(cx: Float, cy: Float, face: Int, rotation: Float = 0f, layer: Int = 0) {
            c.save(); c.rotate(rotation,cx,cy)
            tile(c,cx-tw/2+layer*d,cy-th/2-layer*d,tw,th,face,color,style=style,texture=texture,depth=d); c.restore()
        }
        when (mode) {
            0 -> {
                // Une petite pyramide : trois tuiles, deux à cheval dessus, une au sommet.
                val step = tw * .98f
                for (i in 2 downTo 0) piece(w*.5f+(i-1)*step,h*.42f,intArrayOf(0,31,9)[i])
                for (i in 1 downTo 0) piece(w*.5f+(i-.5f)*step,h*.42f,intArrayOf(27,33)[i],layer=1)
                piece(w*.5f,h*.42f,34,layer=2)
            }
            1 -> {
                for (i in 0..2) piece(w*(.27f+i*.23f),h*(.30f+abs(i-1)*.04f),32,(i-1)*8f)
                p.color = 0x55ffffff; c.drawRoundRect(w*.1f,h*.56f,w*.9f,h*.56f+th*.62f,tw*.2f,tw*.2f,p)
            }
            2 -> {
                p.color = 0xfffff1c2.toInt(); p.style = Paint.Style.STROKE; p.strokeWidth = w*.022f
                shape.reset(); shape.moveTo(w*.26f,h*.38f); shape.lineTo(w*.26f,h*.12f)
                shape.lineTo(w*.74f,h*.12f); shape.lineTo(w*.74f,h*.38f); c.drawPath(shape,p); p.style = Paint.Style.FILL
                piece(w*.26f,h*.42f,4); piece(w*.74f,h*.42f,4)
                piece(w*.5f,h*.45f,20)
            }
            3 -> {
                p.color=0xfffff1c2.toInt(); p.style=Paint.Style.STROKE; p.strokeWidth=w*.022f
                c.drawArc(RectF(w*.16f,h*.1f,w*.84f,h*.62f),-65f,255f,false,p)
                c.drawLine(w*.24f,h*.14f,w*.17f,h*.27f,p); c.drawLine(w*.17f,h*.27f,w*.33f,h*.26f,p); p.style=Paint.Style.FILL
                piece(w*.38f,h*.3f,13,-8f); piece(w*.62f,h*.45f,13,8f)
            }
            4 -> {
                p.style=Paint.Style.FILL; p.color=0x35ffffff
                c.drawRoundRect(w*.14f,h*.12f,w*.86f,h*.44f,w*.035f,w*.035f,p)
                p.color=0xfffff1c2.toInt(); p.style=Paint.Style.STROKE; p.strokeWidth=w*.022f
                c.drawLine(w*.36f,h*.27f,w*.66f,h*.27f,p)
                c.drawLine(w*.66f,h*.27f,w*.59f,h*.21f,p); c.drawLine(w*.66f,h*.27f,w*.59f,h*.33f,p)
                p.style=Paint.Style.FILL
                piece(w*.25f,h*.28f,28); piece(w*.76f,h*.52f,28)
            }
            5 -> { piece(w*.3f,h*.36f,2,-9f); piece(w*.7f,h*.36f,15,9f); star(c,w*.5f,h*.14f,w*.06f,0xffffe896.toInt()) }
            6 -> { for(i in 0..2) piece(w*(.25f+i*.25f),h*(.44f-i*.08f),intArrayOf(31,23,38)[i],(i-1)*10f); star(c,w*.84f,h*.14f,w*.08f,0xffffe48b.toInt()) }
            else -> { chest(c,w*.24f,h*.19f,w*.52f,.6f); star(c,w*.22f,h*.18f,w*.06f,Color.WHITE) }
        }
        for(i in 0..5) star(c,w*(.11f+i*.155f),h*(.07f+i%3*.05f),w*.016f,0x90ffffff.toInt())
        p.shader = LinearGradient(0f,h*.55f,0f,h,Color.TRANSPARENT,0x88203040.toInt(),Shader.TileMode.CLAMP)
        c.drawRect(0f,h*.55f,w,h,p); p.shader = null
    }

    companion object {
        private const val INK = 0xff15233d.toInt()
        private const val RED = 0xffc4292a.toInt()
        private const val GREEN = 0xff177a47.toInt()
        private const val BLUE = 0xff1d4f9c.toInt()
        /** Lanterne corail et Soleil d'ambre dans l'atlas des porcelaines. */
        private const val LANTERN = 11
        private const val SUN = 9
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
    override fun render(canvas: Canvas, w: Float, h: Float) { art.modeScene(canvas,w,h,0,3) }
}
