package com.Atom2Universe.app.periodic.illuminated

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LightingColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import com.Atom2Universe.app.crypto.gacha.GachaRarity
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Une carte d'élément à la manière d'un livre d'heures : vélin, bordure de lierre doré,
 * miniature gravée au centre, numéro en clef de voûte, nom sur un phylactère, symbole en lettrine.
 *
 * Classe de dessin pure (aucun `Context`) : la vue lui donne les textes déjà traduits, le
 * test JVM la dessine avec des textes d'essai. La carte se dessine dans un repère de
 * 360 × 520 ; l'appelant applique l'échelle.
 *
 * Deux calques, comme pour tout décor animé du projet :
 * - **cuit** une fois par taille : vélin, cadres, textes, et la planche fixe de la gravure ;
 * - **vivant** à chaque image : feuilles qui ondulent, fleurs qui tournent, reflet qui court
 *   sur l'or, et ce que la scène anime.
 */
internal class IlluminatedCard(private val fonts: Fonts) {

    class Fonts(val title: Typeface, val letter: Typeface, val caps: Typeface)

    class Content(
        val rarity: GachaRarity,
        val number: String,
        val name: String,
        val symbol: String,
        val mass: String,
        val rarityLabel: String,
        val toxic: Boolean,
        val radioactive: Boolean
    )

    /** L'ancienne scène, dessinée telle quelle dans la fenêtre tant que l'élément n'a pas sa gravure. */
    fun interface LegacyArt { fun draw(c: Canvas, t: Float) }

    @Volatile private var content: Content? = null
    @Volatile private var scene: EngravedScene? = null
    @Volatile private var legacy: LegacyArt? = null
    @Volatile private var livery = Livery.of(GachaRarity.PRIMORDIAL)

    /** Tout ce qui est cuit pour une taille donnée. Immuable : on le remplace d'un bloc. */
    class Baked internal constructor(
        internal val generation: Int,
        val scale: Float,
        internal val plate: Bitmap,
        internal val goldMask: Bitmap,
        internal val frameSprites: Sprites,
        internal val sceneSprites: Sprites,
        internal val nodes: List<Node>,
        internal val perimeter: Float
    )

    @Volatile private var generation = 0
    @Volatile private var baked: Baked? = null

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shimmerPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val shimmerMatrix = Matrix()
    private val shimmer = LinearGradient(0f, 0f, 120f, 0f,
        intArrayOf(0x00FFF6D0, 0x8CFFF6D0.toInt(), 0xE6FFFFFF.toInt(), 0x8CFFF6D0.toInt(), 0x00FFF6D0),
        floatArrayOf(0f, .35f, .5f, .65f, 1f), Shader.TileMode.CLAMP)
    private val glintPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glint = RadialGradient(0f, 0f, 18f, 0x66FFF3C4, 0x00FFF3C4, Shader.TileMode.CLAMP)
    private val leafGlow = Array(6) { i -> LightingColorFilter(0xFFFFFF, (i * 0x0B) * 0x010100 + i * 0x04) }
    private val box = RectF(0f, 0f, W, H)

    fun set(content: Content, scene: EngravedScene?, legacy: LegacyArt?) {
        generation++
        this.content = content
        this.scene = scene
        this.legacy = if (scene == null) legacy else null
        livery = Livery.of(content.rarity)
        baked = null
    }

    /** Lâche les images cuites (pas de `recycle()` : le fil de rendu peut encore les tenir). */
    fun release() { baked = null }

    /** Vrai si la carte est cuite pour cette échelle et peut se dessiner tout de suite. */
    fun isReady(scale: Float) = baked?.scale == scale

    /**
     * Cuit la carte à cette échelle. Ne touche à rien de ce que [draw] lit : peut tourner sur un
     * fil de travail. Le résultat se pose ensuite avec [install].
     */
    fun prepare(scale: Float): Baked? {
        val gen = generation
        val data = content ?: return null
        val w = ceil(W * scale).toInt(); val h = ceil(H * scale).toInt()
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val mask = Bitmap.createBitmap(w, h, Bitmap.Config.ALPHA_8)
        val c = Canvas(bmp); c.scale(w / W, h / H)
        val m = Canvas(mask); m.scale(w / W, h / H)
        val b = Burin(c, m)
        val nodes = ArrayList<Node>()

        vellum(b)
        miniature(b)
        val perimeter = borderStatic(b, nodes)
        keystone(b, data)
        ribbon(b, data)
        lettrine(b, data)
        footer(b, data)

        val frame = Sprites(scale).apply { bake(frameSpriteList()) }
        val sceneSprites = Sprites(scale).apply { bake(scene?.sprites() ?: emptyList()) }
        return Baked(gen, scale, bmp, mask, frame, sceneSprites, nodes, perimeter)
    }

    /** Pose une cuisson faite par [prepare] ; refusée si la carte a changé entre-temps. */
    fun install(b: Baked): Boolean {
        if (b.generation != generation) return false
        baked = b
        return true
    }

    /**
     * Dessine la carte. Le canevas est déjà dans le repère de la carte ; [scale] donne le nombre
     * de pixels par unité, pour cuire à la bonne finesse. ([lightX], [lightY]) : la lumière,
     * que le doigt déplace. Sans cuisson prête, [bakeNow] cuit sur place ; sinon rien n'est
     * dessiné et la fonction rend faux.
     */
    fun draw(c: Canvas, scale: Float, t: Float, lightX: Float = 180f, lightY: Float = 220f, bakeNow: Boolean = true): Boolean {
        var p = baked
        if (p == null || p.scale != scale) {
            if (!bakeNow) return false
            p = prepare(scale) ?: return false
            install(p)
        }
        c.drawBitmap(p.plate, null, box, bitmapPaint)

        c.save()
        c.clipRect(WIN_L, WIN_TOP, WIN_R, RIBBON_TOP)
        c.clipPath(windowPath)
        scene?.animate(c, p.sceneSprites, t)
        legacy?.draw(c, t)
        c.restore()

        drawLiveBorder(c, p, t)
        drawShimmer(c, p, t, lightX, lightY)
        return true
    }

    // ───────────────────────────── cuisson ─────────────────────────────

    private fun vellum(b: Burin) {
        val card = Path().apply { addRoundRect(4f, 4f, W - 4f, H - 4f, 17f, 17f, Path.Direction.CW) }
        b.pen(livery.vellum).shader = RadialGradient(180f, 250f, 330f,
            intArrayOf(livery.vellum, livery.vellum, livery.vellumEdge), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(card, b.p)
        // Le grain : un bruit cuit une fois pour toutes, posé en motif.
        b.pen(0xFF000000.toInt()).shader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        b.p.alpha = if (livery.dark) 90 else 70
        b.c.drawPath(card, b.p)
        // Double filet : or, puis encre.
        b.goldStroke(Path().apply { addRoundRect(8.5f, 8.5f, W - 8.5f, H - 8.5f, 13f, 13f, Path.Direction.CW) }, 2.2f)
        b.c.drawRoundRect(11.5f, 11.5f, W - 11.5f, H - 11.5f, 10f, 10f, b.pen(livery.ink, .6f))
        b.c.drawRect(BAND_IN, BAND_IN, W - BAND_IN, H - BAND_IN, b.pen(livery.ink, .6f))
    }

    private fun miniature(b: Burin) {
        val inner = window(0f) // un chemin à soi : la cuisson peut tourner sur un autre fil
        // Le papier de la planche, plus clair que le vélin.
        b.fill(inner, if (scene == null) 0xFF14182A.toInt() else PAPER)
        val s = scene
        b.c.save(); b.c.clipPath(inner)
        if (s != null) {
            sky(b, s.sky)
            s.engrave(b)
        } else {
            // Fond d'azur semé d'or, en attendant la gravure de cet élément.
            val rnd = Random(3)
            repeat(40) {
                val x = WIN_L + rnd.nextFloat() * (WIN_R - WIN_L); val y = WIN_TOP + rnd.nextFloat() * 250f
                b.goldCircle(x, y, .8f + rnd.nextFloat())
            }
        }
        b.c.restore()
        // La baguette : or, couleur de la rareté, or.
        b.stroke(window(1.2f), 1.6f, livery.ink)
        b.stroke(window(4.6f), 5f, livery.pigment)
        b.stroke(window(4.6f), .6f, withAlpha(0xFFFFFFFF.toInt(), 120))
        b.goldStroke(window(2.2f), 1.5f)
        b.goldStroke(window(7.6f), 1.5f)
        b.stroke(window(8.8f), .7f, livery.ink)
    }

    private fun sky(b: Burin, sky: EngravedScene.Sky) {
        val r = RectF(WIN_L, WIN_TOP, WIN_R, RIBBON_TOP + 30f)
        when (sky) {
            EngravedScene.Sky.NONE -> Unit
            EngravedScene.Sky.DAY -> {
                b.pen(0xFF7FA6C9.toInt()).shader = LinearGradient(0f, WIN_TOP, 0f, 240f,
                    0x887FA6C9.toInt(), 0x007FA6C9, Shader.TileMode.CLAMP)
                b.c.drawRect(r, b.p)
                b.hatchRect(RectF(WIN_L, WIN_TOP, WIN_R, 230f), 0f, 2.6f, .5f, Ink.BROWN,
                    Fade(0f, WIN_TOP, 0f, 228f, 200, 0))
            }
            EngravedScene.Sky.DUSK -> {
                b.pen(0xFFE9A86A.toInt()).shader = LinearGradient(0f, WIN_TOP, 0f, 260f,
                    intArrayOf(0x996A7FB5.toInt(), 0x88E9A86A.toInt(), 0x22F2D49A), null, Shader.TileMode.CLAMP)
                b.c.drawRect(r, b.p)
                b.hatchRect(RectF(WIN_L, WIN_TOP, WIN_R, 240f), 0f, 2.4f, .5f, Ink.BROWN,
                    Fade(0f, WIN_TOP, 0f, 238f, 220, 0))
            }
            EngravedScene.Sky.NIGHT -> {
                b.fill(rect(r.left, r.top, r.right, r.bottom), 0xFF22304F.toInt())
                b.hatchRect(r, 0f, 1.9f, .6f, 0xFF0D1426.toInt())
                b.hatchRect(r, 70f, 2.6f, .45f, 0xFF0D1426.toInt(), Fade(0f, WIN_TOP, 0f, 300f, 200, 40))
                val rnd = Random(11)
                repeat(36) {
                    val x = WIN_L + rnd.nextFloat() * (WIN_R - WIN_L); val y = WIN_TOP + rnd.nextFloat() * 200f
                    b.c.drawCircle(x, y, .5f + rnd.nextFloat() * .9f, b.pen(0xFFFFF6DC.toInt()))
                }
            }
        }
    }

    // ───────────────────────────── bordure ─────────────────────────────

    private class Side(val x0: Float, val y0: Float, val x1: Float, val y1: Float)
    internal class Node(
        val x: Float, val y: Float, val angle: Float, val kind: Int, val arc: Float, val sign: Float
    )

    /** Les tiges de lierre, côté par côté, entre les rosaces des coins et les cartouches. */
    private val sides = listOf(
        Side(36f, MID, 156f, MID), Side(204f, MID, 324f, MID),
        Side(W - MID, 36f, W - MID, H - 36f),
        Side(324f, H - MID, 236f, H - MID), Side(124f, H - MID, 36f, H - MID),
        Side(MID, H - 36f, MID, 36f)
    )

    private fun borderStatic(b: Burin, nodes: MutableList<Node>): Float {
        var arc = 0f
        val stem = FloatArray(4096); var n = 0
        val ink = b.pen(livery.ink, .9f)
        for (side in sides) {
            val len = hypot(side.x1 - side.x0, side.y1 - side.y0)
            val dx = (side.x1 - side.x0) / len; val dy = (side.y1 - side.y0) / len
            val ox = dy; val oy = -dx // vers l'extérieur de la carte
            val halfWaves = (len / 15f).roundToInt().coerceAtLeast(2)
            val lambda = 2f * len / halfWaves
            fun at(s: Float): FloatArray {
                val off = AMP * sin(2f * PI.toFloat() * s / lambda)
                return floatArrayOf(side.x0 + dx * s + ox * off, side.y0 + dy * s + oy * off)
            }
            var s = 0f; var prev = at(0f)
            while (s < len) {
                s = (s + 1.6f).coerceAtMost(len)
                val cur = at(s)
                if (n + 4 <= stem.size) {
                    stem[n++] = prev[0]; stem[n++] = prev[1]; stem[n++] = cur[0]; stem[n++] = cur[1]
                }
                prev = cur
            }
            for (k in 0 until halfWaves) {
                val sk = (k + .5f) * lambda / 2f
                val sign = if (k % 2 == 0) 1f else -1f
                val p = at(sk)
                val kind = when (k % 4) { 0, 3 -> LEAF; else -> CURL }
                val lean = if (k % 4 == 0) 1f else -1f
                if (kind == LEAF) {
                    val vx = ox * sign + dx * .75f * lean; val vy = oy * sign + dy * .75f * lean
                    val vl = hypot(vx, vy)
                    val bx = p[0] + vx / vl * 2.4f; val by = p[1] + vy / vl * 2.4f
                    b.c.drawLine(p[0], p[1], bx, by, ink)
                    val leafIndex = nodes.size
                    val k2 = if (leafIndex % 3 == 2) FLOWER else LEAF
                    nodes.add(Node(bx, by, Math.toDegrees(atan2(vy, vx).toDouble()).toFloat(), k2, arc + sk, sign))
                } else {
                    curl(b, p[0], p[1], ox * sign, oy * sign, dx * lean, dy * lean)
                }
            }
            arc += len
        }
        b.c.drawLines(stem, 0, n, ink)
        // Rosaces des coins : leur couronne d'or est fixe, la fleur intérieure tourne.
        for ((x, y) in corners) {
            b.goldCircle(x, y, 12.5f)
            b.c.drawCircle(x, y, 12.5f, b.pen(livery.ink, .8f))
            b.c.drawCircle(x, y, 9f, b.pen(livery.pigment))
            b.c.drawCircle(x, y, 9f, b.pen(livery.ink, .6f))
        }
        return arc
    }

    /** Vrille d'encre et besant d'or : la ponctuation du rinceau. */
    private fun curl(b: Burin, x: Float, y: Float, nx: Float, ny: Float, tx: Float, ty: Float) {
        val path = Path()
        val cx = x + nx * 5f + tx * 2f; val cy = y + ny * 5f + ty * 2f
        path.moveTo(x, y)
        for (i in 1..18) {
            val f = i / 18f
            val a = atan2(y - cy, x - cx) + f * 5.2f * (if (tx * ny - ty * nx > 0) 1 else -1)
            val r = 5.4f * (1f - f * .78f)
            path.lineTo(cx + cos(a) * r, cy + sin(a) * r)
        }
        b.stroke(path, .7f, livery.ink)
        val gx = x - nx * 4.5f + tx * 3f; val gy = y - ny * 4.5f + ty * 3f
        b.goldCircle(gx, gy, 1.7f)
        b.c.drawCircle(gx, gy, 1.7f, b.pen(livery.ink, .45f))
    }

    private fun frameSpriteList() = listOf(
        Sprite(LEAF, RectF(-1.5f, -5.5f, 11.5f, 5.5f)) { b -> ivyLeaf(b, Gilding.LEAF) },
        Sprite(FLOWER, RectF(-5.5f, -5.5f, 5.5f, 5.5f)) { b -> flower(b) },
        Sprite(ROSETTE, RectF(-9f, -9f, 9f, 9f)) { b -> rosette(b) }
    )

    private fun ivyLeaf(b: Burin, color: Int) {
        val leaf = Path().apply {
            moveTo(0f, 0f)
            cubicTo(.6f, -3.6f, 3.4f, -5.2f, 5f, -3.4f)
            cubicTo(6.4f, -2.2f, 8.4f, -1.2f, 10.5f, 0f)
            cubicTo(8.4f, 1.2f, 6.4f, 2.2f, 5f, 3.4f)
            cubicTo(3.4f, 5.2f, .6f, 3.6f, 0f, 0f)
            close()
        }
        b.pen(color).shader = LinearGradient(0f, -5f, 4f, 5f,
            intArrayOf(Gilding.LIGHT, color, Gilding.DEEP), floatArrayOf(0f, .45f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(leaf, b.p)
        b.stroke(leaf, .55f, livery.ink)
        b.line(.8f, 0f, 8.6f, 0f, .4f, withAlpha(livery.ink, 170))
    }

    private fun flower(b: Burin) {
        for (i in 0 until 5) {
            val a = i * 2 * PI.toFloat() / 5
            val x = cos(a) * 2.6f; val y = sin(a) * 2.6f
            b.c.drawCircle(x, y, 2.4f, b.pen(livery.flower))
            b.c.drawCircle(x, y, 2.4f, b.pen(livery.ink, .4f))
            b.c.drawCircle(x - .6f, y - .7f, .7f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 200)))
        }
        b.c.drawCircle(0f, 0f, 1.7f, b.pen(Gilding.LEAF))
        b.c.drawCircle(0f, 0f, 1.7f, b.pen(livery.ink, .35f))
    }

    private fun rosette(b: Burin) {
        // Quadrilobe : quatre pétales d'or sur le fond de la rareté, filetés de blanc.
        for (i in 0 until 4) {
            val a = i * PI.toFloat() / 2
            val x = cos(a) * 4f; val y = sin(a) * 4f
            b.pen(Gilding.LEAF).shader = RadialGradient(x - 1f, y - 1f, 5f,
                Gilding.LIGHT, Gilding.DEEP, Shader.TileMode.CLAMP)
            b.c.drawCircle(x, y, 3.6f, b.p)
            b.c.drawCircle(x, y, 3.6f, b.pen(livery.ink, .45f))
        }
        b.c.drawCircle(0f, 0f, 2.2f, b.pen(Ink.RUBRIC))
        b.c.drawCircle(0f, 0f, 2.2f, b.pen(livery.ink, .4f))
        b.c.drawCircle(-.6f, -.6f, .6f, b.pen(0xFFFFFFFF.toInt()))
    }

    private fun drawLiveBorder(c: Canvas, p: Baked, t: Float) {
        val sprites = p.frameSprites
        val perimeter = p.perimeter
        val glintArc = (t * 62f) % perimeter
        for (node in p.nodes) {
            // Une onde de vent fait le tour du cadre : chaque feuille suit, un peu en retard.
            val sway = 8f * sin(t * 1.7f - node.arc * .045f) + 3f * sin(t * 3.1f + node.arc * .11f)
            var d = node.arc - glintArc
            if (d < -perimeter / 2) d += perimeter
            if (d > perimeter / 2) d -= perimeter
            val near = (1f - kotlin.math.abs(d) / 40f).coerceIn(0f, 1f)
            sprites.paint.colorFilter = if (near > 0f) leafGlow[(near * 5).toInt()] else null
            c.save(); c.translate(node.x, node.y)
            if (node.kind == FLOWER) {
                c.rotate(t * 18f * node.sign + node.arc)
                sprites.draw(c, FLOWER)
            } else {
                c.rotate(node.angle + sway * node.sign)
                sprites.draw(c, LEAF)
            }
            c.restore()
        }
        sprites.paint.colorFilter = null
        for ((i, xy) in corners.withIndex()) {
            c.save(); c.translate(xy.first, xy.second)
            c.rotate(t * (if (i % 2 == 0) 14f else -14f))
            sprites.draw(c, ROSETTE)
            c.restore()
        }
        // L'éclat qui court sur la bordure.
        val (gx, gy) = pointAt(glintArc)
        glintPaint.shader = glint
        c.save(); c.translate(gx, gy)
        c.drawCircle(0f, 0f, 18f, glintPaint)
        c.restore()
    }

    private fun pointAt(arc: Float): Pair<Float, Float> {
        var a = arc
        for (side in sides) {
            val len = hypot(side.x1 - side.x0, side.y1 - side.y0)
            if (a <= len) {
                val f = a / len
                return (side.x0 + (side.x1 - side.x0) * f) to (side.y0 + (side.y1 - side.y0) * f)
            }
            a -= len
        }
        return sides[0].x0 to sides[0].y0
    }

    private fun drawShimmer(c: Canvas, p: Baked, t: Float, lightX: Float, lightY: Float) {
        val mask = p.goldMask
        // Une bande de lumière balaie l'or en diagonale ; le doigt la déplace.
        val cycle = 7.5f
        val f = (t % cycle) / cycle
        val x = -260f + f * 880f + (lightX - 180f) * 1.1f
        shimmerMatrix.reset()
        shimmerMatrix.setRotate(-58f + (lightY - 220f) * .05f)
        shimmerMatrix.postTranslate(x, 0f)
        shimmer.setLocalMatrix(shimmerMatrix)
        shimmerPaint.shader = shimmer
        c.drawBitmap(mask, null, box, shimmerPaint)
    }

    // ───────────────────────────── textes ─────────────────────────────

    private fun keystone(b: Burin, data: Content) {
        val x = 180f; val y = 30f
        b.goldCircle(x, y, 22f)
        b.c.drawCircle(x, y, 22f, b.pen(livery.ink, .9f))
        for (i in 0 until 28) {
            val a = i * 2 * PI.toFloat() / 28
            b.goldCircle(x + cos(a) * 19.6f, y + sin(a) * 19.6f, 1.15f)
            b.c.drawCircle(x + cos(a) * 19.6f, y + sin(a) * 19.6f, 1.15f, b.pen(withAlpha(livery.ink, 150), .3f))
        }
        b.c.drawCircle(x, y, 17.2f, b.pen(PAPER))
        b.c.drawCircle(x, y, 17.2f, b.pen(livery.ink, .8f))
        b.c.drawCircle(x, y, 15.6f, b.pen(livery.pigment, .6f))
        val p = b.pen(Ink.SEPIA)
        p.typeface = fonts.caps; p.textAlign = Paint.Align.CENTER
        p.textSize = 19f
        val wMax = 27f
        val m = p.measureText(data.number)
        if (m > wMax) p.textSize *= wMax / m
        b.c.drawText(data.number, x, y + p.textSize * .36f, p)
    }

    private fun ribbon(b: Burin, data: Content) {
        val top = RIBBON_TOP; val bot = top + 30f; val sag = 7f
        for (s in floatArrayOf(-1f, 1f)) {
            val e = 180f + s * 122f          // bout du ruban, côté face
            val tail = 180f + s * 156f       // pointe de la queue d'aronde
            val tailShape = poly(e, top + 8f, tail, top + 8f, tail - s * 10f, top + 23f, tail, top + 38f, e, top + 38f)
            b.fill(tailShape, RIBBON_BACK)
            b.hatch(tailShape, if (s < 0) 20f else 160f, 2.2f, .5f, Ink.BROWN)
            b.stroke(tailShape, .9f)
            // Le pli : le revers du ruban, plus sombre.
            val fold = poly(e, bot, e, top + 38f, e - s * 9f, bot)
            b.fill(fold, 0xFF8F7454.toInt())
            b.hatch(fold, 45f, 1.6f, .5f)
            b.stroke(fold, .8f)
        }
        val body = Path().apply {
            moveTo(58f, top); quadTo(180f, top + sag * 2, 302f, top)
            lineTo(302f, bot); quadTo(180f, bot + sag * 2, 58f, bot); close()
        }
        b.fill(body, RIBBON)
        // Ombre aux deux bouts, là où le ruban tourne.
        b.c.save(); b.c.clipPath(body)
        b.hatchRect(RectF(58f, top - 4f, 86f, bot + 16f), 90f, 1.9f, .5f, Ink.BROWN, Fade(58f, 0f, 86f, 0f, 220, 0))
        b.hatchRect(RectF(274f, top - 4f, 302f, bot + 16f), 90f, 1.9f, .5f, Ink.BROWN, Fade(302f, 0f, 274f, 0f, 220, 0))
        b.c.restore()
        b.stroke(body, 1.1f)
        val inner = Path().apply {
            moveTo(64f, top + 3.2f); quadTo(180f, top + 3.2f + sag * 2, 296f, top + 3.2f)
            moveTo(64f, bot - 3.2f); quadTo(180f, bot - 3.2f + sag * 2, 296f, bot - 3.2f)
        }
        b.stroke(inner, .5f, Ink.RUBRIC)
        // Le nom suit la courbe du ruban.
        val mid = Path().apply { moveTo(64f, top + 15f); quadTo(180f, top + 15f + sag * 2, 296f, top + 15f) }
        val p = b.pen(Ink.SEPIA)
        p.typeface = fonts.title; p.textAlign = Paint.Align.CENTER; p.textSize = 23f
        val wMax = 214f
        val m = p.measureText(data.name)
        if (m > wMax) p.textSize *= wMax / m
        b.c.drawTextOnPath(data.name, mid, 0f, p.textSize * .34f, p)
    }

    private fun lettrine(b: Burin, data: Content) {
        val l = 147f; val t = 368f; val s = 66f
        // Antennes de filigrane, à la plume, de part et d'autre de la lettre.
        for (side in floatArrayOf(-1f, 1f)) {
            val x0 = if (side < 0) l else l + s
            val color = if (side < 0) Ink.RUBRIC else livery.filigree
            val pen = b.pen(color, .7f)
            for (k in 0..2) {
                val y = t + 14f + k * 19f
                val path = Path().apply {
                    moveTo(x0, y)
                    cubicTo(x0 + side * 22f, y - 9f + k * 4f, x0 + side * 46f, y + 7f, x0 + side * (60f + k * 6f), y - 2f + k * 2f)
                }
                b.c.drawPath(path, pen)
                val ex = x0 + side * (60f + k * 6f); val ey = y - 2f + k * 2f
                b.c.drawCircle(ex + side * 2.5f, ey - 2.5f, 2.6f, pen)
                b.c.drawCircle(x0 + side * 30f, y - 4f + k * 2f, .9f, b.pen(color))
            }
            // Le bout des antennes : un médaillon pour les insignes de danger.
            val cx = 180f + side * 104f; val cy = t + s / 2f
            b.goldCircle(cx, cy, 13.5f)
            b.c.drawCircle(cx, cy, 13.5f, b.pen(livery.ink, .8f))
            b.c.drawCircle(cx, cy, 10.5f, b.pen(PAPER))
            b.c.drawCircle(cx, cy, 10.5f, b.pen(livery.ink, .6f))
            val badge = if (side < 0) data.toxic else data.radioactive
            when {
                badge && side < 0 -> skull(b, cx, cy)
                badge -> trefoil(b, cx, cy)
                else -> {
                    // Sans insigne, une petite rose : six besants autour d'un cœur rouge.
                    for (i in 0 until 6) {
                        val a = i * PI.toFloat() / 3
                        b.goldCircle(cx + cos(a) * 5f, cy + sin(a) * 5f, 2.1f)
                        b.c.drawCircle(cx + cos(a) * 5f, cy + sin(a) * 5f, 2.1f, b.pen(livery.ink, .4f))
                    }
                    b.c.drawCircle(cx, cy, 2.4f, b.pen(Ink.RUBRIC))
                    b.c.drawCircle(cx, cy, 2.4f, b.pen(livery.ink, .4f))
                }
            }
        }
        val frame = rect(l, t, l + s, t + s)
        b.gold(frame, Fade(l, t, l + s, t + s))
        b.stroke(frame, 1f, livery.ink)
        val ground = rect(l + 4.5f, t + 4.5f, l + s - 4.5f, t + s - 4.5f)
        b.fill(ground, livery.pigment)
        b.stroke(ground, .7f, livery.ink)
        // Filigrane blanc sur le fond : petites volutes et points.
        val white = b.pen(withAlpha(0xFFFFFFFF.toInt(), 190), .45f)
        for (i in 0..3) {
            val cx = if (i % 2 == 0) l + 12f else l + s - 12f
            val cy = if (i < 2) t + 12f else t + s - 12f
            b.c.drawArc(cx - 4f, cy - 4f, cx + 4f, cy + 4f, 0f + i * 90f, 250f, false, white)
            b.c.drawCircle(cx, cy, .9f, b.pen(withAlpha(0xFFFFFFFF.toInt(), 190)))
        }
        val gold = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.letter; textAlign = Paint.Align.CENTER
            textSize = if (data.symbol.length > 1) 40f else 50f
            val m = measureText(data.symbol)
            if (m > s - 14f) textSize *= (s - 14f) / m
        }
        val baseline = t + s / 2f + gold.textSize * .36f
        gold.shader = LinearGradient(0f, baseline - gold.textSize * .75f, 0f, baseline,
            intArrayOf(Gilding.LIGHT, Gilding.LEAF, Gilding.DEEP), null, Shader.TileMode.CLAMP)
        // Contour d'encre sous l'or, pour que la lettre se lise sur tous les fonds.
        val outline = Paint(gold).apply {
            shader = null; color = Ink.SEPIA; style = Paint.Style.STROKE
            strokeWidth = 3f; strokeJoin = Paint.Join.ROUND
        }
        b.c.drawText(data.symbol, l + s / 2f + .8f, baseline + 1.2f, outline)
        b.c.drawText(data.symbol, l + s / 2f, baseline, outline)
        b.goldText(data.symbol, l + s / 2f, baseline, gold)
    }

    private fun skull(b: Burin, x: Float, y: Float) {
        val ink = Ink.SEPIA
        b.c.drawOval(x - 5.6f, y - 6.4f, x + 5.6f, y + 3.2f, b.pen(0xFFF3E7CF.toInt()))
        b.c.drawOval(x - 5.6f, y - 6.4f, x + 5.6f, y + 3.2f, b.pen(ink, .7f))
        b.c.drawRoundRect(x - 3.4f, y + 1f, x + 3.4f, y + 5.6f, 1.2f, 1.2f, b.pen(0xFFF3E7CF.toInt()))
        b.c.drawRoundRect(x - 3.4f, y + 1f, x + 3.4f, y + 5.6f, 1.2f, 1.2f, b.pen(ink, .6f))
        b.c.drawCircle(x - 2.3f, y - 1.6f, 1.6f, b.pen(ink))
        b.c.drawCircle(x + 2.3f, y - 1.6f, 1.6f, b.pen(ink))
        for (i in -1..1) b.line(x + i * 1.6f, y + 3f, x + i * 1.6f, y + 5.4f, .45f, ink)
        b.line(x - 7.5f, y + 5.5f, x + 7.5f, y - 6f, .9f, ink)
        b.line(x - 7.5f, y - 6f, x + 7.5f, y + 5.5f, .9f, ink)
    }

    private fun trefoil(b: Burin, x: Float, y: Float) {
        b.c.drawCircle(x, y, 9.4f, b.pen(0xFFF2C744.toInt()))
        for (i in 0..2) {
            b.c.drawArc(x - 8f, y - 8f, x + 8f, y + 8f, -120f + i * 120f, 60f, true, b.pen(Ink.SEPIA))
        }
        b.c.drawCircle(x, y, 2.8f, b.pen(0xFFF2C744.toInt()))
        b.c.drawCircle(x, y, 1.8f, b.pen(Ink.SEPIA))
    }

    private fun footer(b: Burin, data: Content) {
        val p = b.pen(livery.text)
        p.typeface = fonts.caps; p.textAlign = Paint.Align.CENTER; p.textSize = 9.5f
        p.letterSpacing = .12f
        val m = p.measureText(data.mass)
        if (m > 230f) p.textSize *= 230f / m
        b.c.drawText(data.mass, 180f, 458f, p)
        // Le cartouche de la rareté coupe la bordure du bas, comme la clef de voûte celle du haut.
        val l = 128f; val r = 232f; val top = H - MID - 10f; val bot = H - MID + 10f
        val plaque = poly(l, H - MID, l + 9f, top, r - 9f, top, r, H - MID, r - 9f, bot, l + 9f, bot)
        b.gold(plaque, Fade(l, top, r, bot))
        b.stroke(plaque, .9f, livery.ink)
        val inner = poly(l + 5f, H - MID, l + 11.5f, top + 3f, r - 11.5f, top + 3f, r - 5f, H - MID, r - 11.5f, bot - 3f, l + 11.5f, bot - 3f)
        b.fill(inner, livery.pigment)
        b.stroke(inner, .6f, livery.ink)
        val q = b.pen(0xFFFFF8E8.toInt())
        q.typeface = fonts.caps; q.textAlign = Paint.Align.CENTER; q.textSize = 8f; q.letterSpacing = .14f
        val label = data.rarityLabel
        val mm = q.measureText(label)
        if (mm > 78f) q.textSize *= 78f / mm
        b.c.drawText(label, 180f, H - MID + q.textSize * .36f, q)
    }

    // ───────────────────────────── géométrie ─────────────────────────────

    private val windowPath = window(0f)

    /** La fenêtre de la miniature, en arc surbaissé ; [inset] > 0 l'agrandit vers l'extérieur. */
    private fun window(inset: Float): Path {
        val l = WIN_L - inset; val r = WIN_R + inset
        val top = WIN_TOP - inset; val spring = WIN_SPRING; val bottom = RIBBON_TOP + 26f + inset
        val half = (r - l) / 2f; val h = spring - top
        val radius = (half * half + h * h) / (2 * h)
        val cy = top + radius
        val sweep = Math.toDegrees(asin((half / radius).toDouble())).toFloat()
        return Path().apply {
            moveTo(l, bottom); lineTo(l, spring)
            arcTo(RectF(180f - radius, cy - radius, 180f + radius, cy + radius), 270f - sweep, 2 * sweep)
            lineTo(r, bottom); close()
        }
    }

    private val corners = listOf(MID to MID, W - MID to MID, W - MID to H - MID, MID to H - MID)

    /** Les couleurs d'une rareté, prises aux pigments des enlumineurs. */
    private class Livery(
        val pigment: Int, val flower: Int, val filigree: Int,
        val vellum: Int, val vellumEdge: Int, val ink: Int, val text: Int, val dark: Boolean
    ) {
        companion object {
            private const val VELLUM = 0xFFF1E4C6.toInt()
            private const val EDGE = 0xFFD9C08E.toInt()
            fun of(r: GachaRarity) = when (r) {
                // Argent et azur pâle.
                GachaRarity.PRIMORDIAL -> Livery(0xFF7D93AE.toInt(), 0xFF9DB4CF.toInt(), 0xFF4D6A8C.toInt(), VELLUM, EDGE, Ink.SEPIA, Ink.SEPIA, false)
                // Vert-de-gris.
                GachaRarity.SPALLATION -> Livery(0xFF2F7D60.toInt(), 0xFF4FA483.toInt(), 0xFF2F7D60.toInt(), VELLUM, EDGE, Ink.SEPIA, Ink.SEPIA, false)
                // Lapis-lazuli.
                GachaRarity.FUSION -> Livery(0xFF1F4C9C.toInt(), 0xFF3E6FC7.toInt(), 0xFF1F4C9C.toInt(), VELLUM, EDGE, Ink.SEPIA, Ink.SEPIA, false)
                // Vermillon.
                GachaRarity.SUPERNOVA -> Livery(0xFFC2441F.toInt(), 0xFFE0602E.toInt(), 0xFF1F4C9C.toInt(), VELLUM, EDGE, Ink.SEPIA, Ink.SEPIA, false)
                // Pourpre.
                GachaRarity.NEUTRONIQUE -> Livery(0xFF6E2A73.toInt(), 0xFF9C4DA4.toInt(), 0xFF6E2A73.toInt(), VELLUM, EDGE, Ink.SEPIA, Ink.SEPIA, false)
                // Les Heures noires : vélin teint, or et argent.
                GachaRarity.SYNTHETIQUE -> Livery(0xFFB0281F.toInt(), 0xFFD8473A.toInt(), 0xFFC9CDD6.toInt(), 0xFF2A2630.toInt(), 0xFF141218.toInt(), 0xFFC9CDD6.toInt(), 0xFFE6E1D6.toInt(), true)
            }
        }
    }

    companion object {
        const val W = 360f
        const val H = 520f
        private const val MID = 23f          // axe de la bordure
        private const val AMP = 3.6f         // ondulation de la tige
        private const val BAND_IN = 35f      // bord intérieur de la bordure
        const val WIN_L = 46f
        const val WIN_R = 314f
        const val WIN_TOP = 60f
        const val WIN_SPRING = 98f
        const val RIBBON_TOP = 318f
        private const val PAPER = 0xFFF6EDD8.toInt()
        private const val RIBBON = 0xFFF8F0DC.toInt()
        private const val RIBBON_BACK = 0xFFC9B48E.toInt()
        private const val LEAF = 1
        private const val FLOWER = 2
        private const val CURL = 3
        private const val ROSETTE = 4

        /** Le grain du vélin : un bruit de valeur pavable, écrit pixel par pixel. */
        private val grain: Bitmap by lazy {
            val n = 128
            val rnd = Random(29)
            val lattice = Array(4) { o -> val g = 4 shl o; FloatArray(g * g) { rnd.nextFloat() } }
            val px = IntArray(n * n)
            for (y in 0 until n) for (x in 0 until n) {
                var v = 0f; var amp = .5f
                for (o in 0 until 4) {
                    val g = 4 shl o
                    val fx = x * g / n.toFloat(); val fy = y * g / n.toFloat()
                    val x0 = fx.toInt(); val y0 = fy.toInt()
                    val tx = fx - x0; val ty = fy - y0
                    val sx = tx * tx * (3 - 2 * tx); val sy = ty * ty * (3 - 2 * ty)
                    fun l(i: Int, j: Int) = lattice[o][(j % g) * g + (i % g)]
                    val a = l(x0, y0) + (l(x0 + 1, y0) - l(x0, y0)) * sx
                    val b = l(x0, y0 + 1) + (l(x0 + 1, y0 + 1) - l(x0, y0 + 1)) * sx
                    v += (a + (b - a) * sy) * amp; amp *= .5f
                }
                val fine = rnd.nextFloat() * .25f
                val alpha = ((v * .9f + fine - .35f).coerceIn(0f, 1f) * 110).toInt()
                px[y * n + x] = (alpha shl 24) or 0x5A4020
            }
            Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888).apply { setPixels(px, 0, n, 0, 0, n, n) }
        }

    }
}
