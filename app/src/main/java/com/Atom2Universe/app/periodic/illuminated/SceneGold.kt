package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Or — le chercheur d'or. Agenouillé au bord d'une rivière de montagne, il fait tourner sa
 * batée : l'eau emporte le sable léger, et la pépite, dix-neuf fois plus dense que l'eau,
 * reste au fond et brille. Derrière lui, sa tente et son feu de camp.
 */
internal class SceneGold : EngravedScene() {

    override val nameRes = R.string.card_scene_gold_panning
    override val noteRes = R.string.card_note_gold_panning
    override val explainRes = R.string.card_explain_gold_panning

    override fun engrave(b: Burin) {
        mountains(b)
        hills(b)
        ground(b)
        camp(b)
        pickaxe(b)
        prospector(b)
    }

    // ───────────── le paysage ─────────────

    private fun mountains(b: Burin) {
        val peaks = floatArrayOf(
            40f, 196f, 62f, 170f, 78f, 182f, 102f, 142f, 120f, 164f, 136f, 154f, 160f, 186f,
            186f, 160f, 206f, 178f, 232f, 134f, 258f, 166f, 274f, 154f, 298f, 178f, 322f, 168f
        )
        val range = Path().apply {
            moveTo(peaks[0], peaks[1])
            for (i in 2 until peaks.size step 2) lineTo(peaks[i], peaks[i + 1])
            lineTo(322f, 236f); lineTo(40f, 236f); close()
        }
        b.body(range, 0xFF9AA8BC.toInt(), 0f, outline = 0f, washAlpha = 170)
        b.hatch(range, -12f, 3.2f, .4f, 0xFF4A5670.toInt(), Fade(0f, 150f, 0f, 220f, 40, 160))
        // Le flanc droit de chaque sommet est à l'ombre.
        b.c.save(); b.c.clipPath(range)
        for (i in 2 until peaks.size - 2 step 2) {
            val px = peaks[i]; val py = peaks[i + 1]
            if (py >= peaks[i - 1] || py >= peaks[i + 3]) continue
            val rx = peaks[i + 2]; val ry = peaks[i + 3]
            val face = poly(px, py, rx, ry, rx + 6f, 236f, px + (rx - px) * .2f, 236f)
            b.hatch(face, 62f, 1.7f, .45f, 0xFF2E3850.toInt(), Fade(0f, py, 0f, 230f, 230, 60))
            // Les neiges sur les plus hauts.
            if (py < 166f) {
                val cap = poly(px, py, px + 9f, py + 12f, px + 5f, py + 10f, px + 1.5f, py + 15f,
                    px - 3f, py + 10.5f, px - 6.5f, py + 13.5f, px - 10f, py + 11f)
                b.fill(cap, 0xFFFAF7F0.toInt())
                b.stroke(cap, .4f, 0xFF4A5670.toInt())
            }
        }
        b.c.restore()
        b.stroke(Path().apply {
            moveTo(peaks[0], peaks[1]); for (i in 2 until peaks.size step 2) lineTo(peaks[i], peaks[i + 1])
        }, .7f, 0xFF3A4660.toInt())
    }

    private fun hills(b: Burin) {
        val hills = Path().apply {
            moveTo(40f, 206f); cubicTo(80f, 194f, 130f, 200f, 170f, 212f)
            cubicTo(186f, 218f, 196f, 220f, 205f, 214f)
            cubicTo(230f, 200f, 280f, 196f, 322f, 204f)
            lineTo(322f, 242f); lineTo(40f, 242f); close()
        }
        b.body(hills, 0xFF6F8F5A.toInt(), .25f, 75f, Fade(0f, 200f, 0f, 240f, 60, 200), outline = .7f, washAlpha = 170)
        // Une forêt de sapins sur les deux versants.
        val rnd = Random(21)
        for (row in 0..2) {
            var x = 44f + rnd.nextFloat() * 6f
            while (x < 320f) {
                if (x !in 186f..222f) {
                    val ridge = ridgeAt(x)
                    val base = ridge + 9f + row * 8f + rnd.nextFloat() * 3f
                    pine(b, x, base, 11f + row * 3f + rnd.nextFloat() * 4f)
                }
                x += 7f + rnd.nextFloat() * 5f
            }
        }
    }

    /** La ligne de crête des collines, pour y planter les sapins. */
    private fun ridgeAt(x: Float): Float = when {
        x < 170f -> 206f - 10f * sin((x - 40f) / 130f * PI.toFloat()) + (x - 40f) / 130f * 6f
        x < 205f -> 212f + (x - 170f) / 35f * 2f
        else -> 214f - 14f * sin((x - 205f) / 117f * PI.toFloat() * .6f)
    }

    private fun pine(b: Burin, x: Float, base: Float, h: Float) {
        b.line(x, base, x, base - h * .2f, .8f, Ink.BROWN)
        for (k in 0..2) {
            val ty = base - h + k * h * .27f
            val by = ty + h * .42f
            val hw = h * (.17f + k * .08f)
            val tri = poly(x, ty, x + hw, by, x - hw, by)
            b.body(tri, 0xFF3E5E3E.toInt(), .5f, 70f, Fade(x + hw, 0f, x - hw, 0f, 255, 40), outline = .45f, washAlpha = 230)
        }
    }

    private fun ground(b: Burin) {
        val ground = Path().apply {
            moveTo(40f, 234f); cubicTo(120f, 226f, 200f, 230f, 322f, 228f)
            lineTo(322f, 346f); lineTo(40f, 346f); close()
        }
        b.body(ground, 0xFFC4AE84.toInt(), 0f, outline = 0f, washAlpha = 210)
        b.hatch(ground, -6f, 2.6f, .45f, Ink.SEPIA, Fade(0f, 236f, 0f, 330f, 60, 210))
        b.stipple(ground, 520, .55f, Ink.BROWN, seed = 3)
        b.stroke(Path().apply { moveTo(40f, 234f); cubicTo(120f, 226f, 200f, 230f, 322f, 228f) }, .6f)
        // Des galets sur la berge.
        val rnd = Random(9)
        repeat(26) {
            val x = 46f + rnd.nextFloat() * 120f; val y = 262f + rnd.nextFloat() * 70f
            if (x > 70f && x < 196f && y < 300f) return@repeat
            val r = 1.4f + rnd.nextFloat() * 2.4f
            b.body(ellipse(x, y, r, r * .6f), 0xFF9C9488.toInt(), .3f, 40f, outline = .45f, washAlpha = 220)
        }
        // La rivière, qui descend de la vallée jusqu'au premier plan.
        b.wash(river, 0xFF7FA7C4.toInt(), 235)
        b.hatch(river, 0f, 2.1f, .5f, Ink.BROWN, Fade(0f, 214f, 0f, 340f, 80, 220))
        b.stroke(river, .9f)
        for ((x, y, rx, ry) in listOf(floatArrayOf(288f, 300f, 14f, 8f), floatArrayOf(250f, 332f, 16f, 8f),
                floatArrayOf(308f, 326f, 10f, 6f), floatArrayOf(232f, 238f, 6f, 3.5f))) {
            boulder(b, x, y, rx, ry)
        }
    }

    private val river = Path().apply {
        moveTo(200f, 212f)
        cubicTo(197f, 236f, 194f, 256f, 186f, 278f)
        cubicTo(180f, 296f, 172f, 320f, 164f, 346f)
        lineTo(330f, 346f); lineTo(330f, 262f)
        cubicTo(290f, 252f, 240f, 234f, 212f, 212f)
        close()
    }

    private fun boulder(b: Burin, x: Float, y: Float, rx: Float, ry: Float) {
        val rock = Path().apply {
            moveTo(x - rx, y + ry * .4f)
            cubicTo(x - rx, y - ry, x - rx * .3f, y - ry * 1.2f, x + rx * .2f, y - ry)
            cubicTo(x + rx, y - ry * .8f, x + rx * 1.05f, y, x + rx, y + ry * .4f)
            close()
        }
        b.body(rock, 0xFF8C8478.toInt(), .5f, 40f, Fade(x + rx, y + ry, x - rx * .4f, y - ry, 240, 20), outline = .8f, washAlpha = 240)
        // L'écume en amont.
        val foam = b.pen(withAlpha(Ink.WHITE, 220), .8f)
        b.c.drawArc(x - rx * 1.25f, y - ry * .2f, x + rx * 1.25f, y + ry * 1.2f, 160f, -140f, false, foam)
        b.c.drawLine(x - rx * 1.5f, y + ry * .8f, x - rx * .9f, y + ry * .8f, foam)
    }

    private fun camp(b: Burin) {
        val side = poly(84f, 218f, 112f, 221f, 124f, 246f, 104f, 247f)
        b.body(side, 0xFFE6DCC2.toInt(), .45f, 80f, outline = .7f, washAlpha = 230)
        val front = poly(64f, 247f, 84f, 218f, 104f, 247f)
        b.body(front, 0xFFF2EBD8.toInt(), .2f, 70f, Fade(104f, 247f, 84f, 226f, 200, 0), outline = .8f, washAlpha = 245)
        b.fill(poly(79f, 247f, 84f, 232f, 89f, 247f), withAlpha(Ink.SEPIA, 210))
        b.line(84f, 213f, 84f, 218f, .9f)
        b.line(112f, 221f, 132f, 247f, .4f)
        // Le foyer : un cercle de pierres et deux bûches.
        for (i in 0 until 7) {
            val a = i * 2 * PI.toFloat() / 7
            b.body(ellipse(58f + cos(a) * 6f, 258f + sin(a) * 2f, 2f, 1.4f), 0xFF8C8478.toInt(), .3f, 40f, outline = .4f, washAlpha = 230)
        }
        b.line(52f, 258f, 63f, 256f, 1.6f, Ink.BROWN); b.line(53f, 256f, 64f, 259f, 1.6f, Ink.BROWN)
    }

    private fun pickaxe(b: Burin) {
        boulder(b, 62f, 314f, 18f, 11f)
        b.line(50f, 332f, 84f, 288f, 2.4f, 0xFF7A5634.toInt())
        b.line(50f, 332f, 84f, 288f, .5f)
        b.taper(71f, 279f, 80f, 281f, 90f, 289f, 97f, 297f, 3.6f, 0xFF4A4A4E.toInt())
        b.taper(71f, 279f, 78f, 279f, 86f, 284f, 93f, 292f, 1f, 0xFFB8B8B8.toInt())
    }

    // ───────────── le chercheur ─────────────

    private fun prospector(b: Burin) {
        // La jambe arrière, genou à terre.
        val boot = Path().apply {
            moveTo(100f, 287f); lineTo(100f, 298f); lineTo(77f, 299f)
            quadTo(74f, 297f, 78f, 294f); lineTo(90f, 289f); close()
        }
        b.body(boot, BOOT, .6f, 50f, outline = .7f, washAlpha = 240)
        cloth(b, limb(122f, 293f, 97f, 293f, 11f, 8.5f), DENIM)
        cloth(b, limb(130f, 264f, 122f, 293f, 15f, 12f), DENIM)
        // Le rocher sous le pied avant.
        boulder(b, 178f, 304f, 24f, 8f)
        // Le buste penché sur la rivière : chemise de flanelle à carreaux, bretelle.
        val torso = Path().apply {
            moveTo(124f, 272f)
            cubicTo(116f, 250f, 124f, 228f, 142f, 216f)
            cubicTo(152f, 210f, 164f, 213f, 169f, 222f)
            cubicTo(172f, 234f, 162f, 252f, 152f, 266f)
            lineTo(148f, 274f); lineTo(124f, 274f); close()
        }
        flannel(b, torso)
        b.fill(poly(123f, 266f, 151f, 262f, 150f, 268f, 124f, 272f), 0xFF4A3424.toInt())
        b.taper(150f, 213f, 147f, 230f, 143f, 250f, 139f, 266f, 3.4f, 0xFF3A2A1E.toInt())
        // La jambe avant, genou levé.
        cloth(b, limb(138f, 266f, 166f, 262f, 15f, 12.5f), DENIM)
        cloth(b, limb(166f, 262f, 170f, 291f, 12f, 9f), DENIM)
        val front = Path().apply {
            moveTo(163f, 286f); lineTo(176f, 286f); quadTo(188f, 290f, 190f, 296f)
            lineTo(190f, 299f); lineTo(162f, 299f); close()
        }
        b.body(front, BOOT, .6f, 50f, outline = .7f, washAlpha = 240)
        // La tête : le profil, la barbe, le chapeau de feutre.
        b.body(limb(158f, 214f, 162f, 206f, 6f, 6f), SKIN, .3f, 60f, outline = .6f, washAlpha = 240)
        val head = Path().apply {
            addCircle(164f, 201f, 8f, Path.Direction.CW)
            op(poly(170.5f, 197.5f, 175f, 203.5f, 170.5f, 205f), Path.Op.UNION)
        }
        b.body(head, SKIN, .25f, 60f, Fade(156f, 0f, 168f, 0f, 220, 0), outline = .7f, washAlpha = 245)
        b.fill(poly(156f, 196f, 160f, 195f, 158.5f, 204f, 155.5f, 202f), BEARD)
        b.body(ellipse(159.5f, 202f, 1.8f, 2.6f), SKIN, .3f, 0f, outline = .5f, washAlpha = 255)
        val beard = Path().apply {
            moveTo(158f, 203f)
            cubicTo(158f, 210f, 163f, 216f, 170f, 214.5f)
            cubicTo(174.5f, 212.5f, 174.5f, 208f, 172.5f, 206f)
            lineTo(168f, 206f); quadTo(163.5f, 206f, 161f, 202f); close()
        }
        b.body(beard, BEARD, .55f, 75f, outline = .5f, washAlpha = 240)
        b.taper(166.5f, 205f, 169f, 204f, 171.5f, 204.5f, 174f, 206f, 1.8f, BEARD)
        b.c.drawCircle(169.3f, 199.6f, .9f, b.pen(Ink.SEPIA))
        b.line(166.5f, 197.2f, 171.2f, 197.6f, .7f)
        b.c.save(); b.c.rotate(6f, 164f, 196.5f)
        b.body(ellipse(164f, 196.5f, 16.5f, 3.2f), HAT, .45f, 20f, outline = .7f, washAlpha = 245)
        b.c.restore()
        val crown = Path().apply {
            moveTo(153.5f, 196f); cubicTo(153f, 186f, 156f, 183f, 164f, 183f)
            cubicTo(172f, 183f, 175f, 186f, 174.5f, 196f); close()
        }
        b.body(crown, HAT, .45f, 70f, Fade(153f, 0f, 175f, 0f, 240, 40), outline = .7f, washAlpha = 245)
        b.fill(poly(153.6f, 191.5f, 174.4f, 191.5f, 174.5f, 195.5f, 153.5f, 195.5f), 0xFF3A2A1E.toInt())
        // Le bras proche, jusqu'au coude ; l'avant-bras et la batée bougent à part.
        flannel(b, limb(160f, 222f, 174f, 245f, 10f, 8.5f))
    }

    /** La peau : lavis, hachures serrées et fines ; [shade] pour ce qui est dans l'ombre. */
    private fun skin(b: Burin, shape: Path, fade: Fade, shade: Boolean = false) {
        b.wash(shape, SKIN, 250)
        if (shade) b.wash(shape, 0xFF8A5A3A.toInt(), 80)
        b.hatch(shape, 50f, 1.4f, .3f, Ink.SEPIA, fade)
        b.stroke(shape, .6f)
    }

    /** Un tissu : lavis, ombre du côté du dos, contour. */
    private fun cloth(b: Burin, shape: Path, color: Int) {
        b.wash(shape, color, 235)
        b.hatch(shape, 40f, 2f, .45f, Ink.SEPIA, Fade(116f, 0f, 168f, 0f, 220, 30))
        b.stroke(shape, .7f)
    }

    /** La flanelle rouge à carreaux, la marque du chercheur d'or. */
    private fun flannel(b: Burin, shape: Path) {
        b.wash(shape, SHIRT, 240)
        b.hatch(shape, 0f, 4.2f, .9f, withAlpha(0xFF5A160E.toInt(), 160), wobble = 0f)
        b.hatch(shape, 90f, 4.2f, .9f, withAlpha(0xFF5A160E.toInt(), 160), wobble = 0f)
        b.hatch(shape, 40f, 1.9f, .45f, Ink.SEPIA, Fade(116f, 0f, 166f, 0f, 220, 0))
        b.stroke(shape, .7f)
    }

    override fun sprites() = listOf(Sprite(PAN, RectF(164f, 236f, 262f, 280f)) { b -> pan(b) })

    /** Les avant-bras et la batée, d'un seul bloc : ils tournent autour du coude. */
    private fun pan(b: Burin) {
        // L'avant-bras lointain, dans l'ombre, tient le bord du fond.
        skin(b, limb(176f, 242f, 205f, 254f, 7f, 5.5f), Fade(0f, 256f, 0f, 240f, 230, 60), shade = true)
        skin(b, ellipse(206.5f, 254f, 3.6f, 2.8f), Fade(0f, 257f, 0f, 251f, 230, 60), shade = true)
        // La batée : le flanc d'acier, puis l'intérieur.
        val rim = RectF(196f, 253.5f, 256f, 270.5f)
        val bottom = RectF(207f, 264f, 247f, 274f)
        val wall = Path().apply { arcTo(rim, 0f, 180f, true); arcTo(bottom, 180f, -180f); close() }
        b.body(wall, 0xFF5C5A58.toInt(), .55f, 80f, Fade(196f, 0f, 256f, 0f, 240, 60), outline = .8f, washAlpha = 250)
        val inside = Path().apply { addOval(rim, Path.Direction.CW) }
        b.fill(inside, 0xFF6E6658.toInt())
        b.c.save(); b.c.clipPath(inside)
        // L'eau trouble sur le sable noir ; le sable lourd se rassemble du côté du chercheur.
        b.wash(ellipse(232f, 262.5f, 22f, 6.5f), 0xFF9BB8C6.toInt(), 170)
        b.stipple(ellipse(212f, 263f, 13f, 6f), 160, .5f, 0xFF2A2420.toInt(), seed = 5)
        b.hatch(inside, 0f, 1.6f, .4f, withAlpha(Ink.SEPIA, 160), Fade(0f, 254f, 0f, 262f, 220, 0))
        b.c.restore()
        // La pépite, et quelques paillettes.
        val nugget = Path().apply {
            moveTo(232f, 264.5f); cubicTo(231f, 261.5f, 234f, 260.6f, 236.5f, 261f)
            cubicTo(239.5f, 261f, 241f, 263f, 239.8f, 265f); cubicTo(238f, 267f, 233.5f, 267f, 232f, 264.5f); close()
        }
        b.gold(nugget, Fade(233f, 261f, 239f, 266f))
        b.hatch(nugget, 30f, 1.2f, .35f, Gilding.DEEP, Fade(240f, 266f, 234f, 262f, 230, 0))
        b.stroke(nugget, .5f)
        for ((x, y) in listOf(226f to 263f, 228.5f to 266f, 243f to 264.5f, 222f to 265.5f, 245.5f to 262f)) {
            b.goldCircle(x, y, .75f)
        }
        b.c.drawOval(rim, b.pen(0xFF3A3836.toInt(), 1.6f))
        b.c.drawArc(rim, 200f, 120f, false, b.pen(withAlpha(0xFFFFFFFF.toInt(), 170), .8f))
        // L'avant-bras proche, manche retroussée, et la main sur le bord.
        skin(b, limb(174f, 246f, 198f, 262f, 7.5f, 6f), Fade(178f, 258f, 186f, 248f, 200, 0))
        flannel(b, limb(173f, 245f, 178.5f, 249f, 9.5f, 9f))
        skin(b, ellipse(199.5f, 264f, 4.4f, 3.3f), Fade(0f, 267f, 0f, 261f, 180, 0))
        for (k in 0..2) b.line(200f + k * 1.6f, 261.5f, 201.5f + k * 1.6f, 265.5f, .4f)
    }

    // ───────────── animation ─────────────

    private val flow = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val spark = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND }
    private val smoke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fire = Paint(Paint.ANTI_ALIAS_FLAG)
    private val firePath = Path()
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(0f, 0f, 1f, 0x88FFB050.toInt(), 0x00FFB050, Shader.TileMode.CLAMP)
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        currents(c, t)
        campfire(c, t)
        eagle(c, t)

        // La batée tourne : un balancement autour du coude.
        val deg = 2.2f * sin(t * 2.4f) + .8f * sin(t * 5.1f)
        s.draw(c, PAN, deg = deg, px = 175f, py = 245f)
        c.save(); c.rotate(deg, 175f, 245f)
        // L'eau qui tourne dans la batée.
        val start = (t * 170f) % 360f
        spark.style = Paint.Style.STROKE
        spark.color = withAlpha(0xFFFFFFFF.toInt(), 170); spark.strokeWidth = .9f
        c.drawArc(204f, 257f, 250f, 268f, start, 70f, false, spark)
        spark.color = withAlpha(0xFFFFFFFF.toInt(), 110)
        c.drawArc(210f, 259f, 246f, 266.5f, start + 180f, 50f, false, spark)
        // L'éclat de la pépite, toutes les 3,2 s.
        val k = max(0f, sin(t * 2 * PI.toFloat() / 3.2f)).pow(6)
        if (k > .02f) star(c, 236.5f, 263.5f, 2f + 8f * k, (255 * k).toInt())
        c.restore()

        // L'eau qui déborde de la batée et retombe dans la rivière.
        val r = Math.toRadians(deg.toDouble()).toFloat()
        val ox = 175f + 81f * cos(r) - 17f * sin(r)
        val oy = 245f + 81f * sin(r) + 17f * cos(r)
        val surface = 292f
        for (j in 0..2) {
            val p = (t * 1.3f + j / 3f) % 1f
            val y = oy + (surface - oy) * p * p
            spark.style = Paint.Style.FILL; spark.color = withAlpha(0xFFE8F2F6.toInt(), (220 * (1f - p * .5f)).toInt())
            c.drawCircle(ox + j * .8f, y, 1.1f, spark)
            val q = p
            spark.style = Paint.Style.STROKE; spark.strokeWidth = .7f
            spark.color = withAlpha(0xFFFFFFFF.toInt(), (150 * (1f - q)).toInt())
            val rx = 2f + 11f * q
            c.drawOval(ox - rx, surface - rx * .28f, ox + rx, surface + rx * .28f, spark)
        }
    }

    /** Le courant : de petits traits clairs qui descendent la rivière, et l'or qui y brille. */
    private fun currents(c: Canvas, t: Float) {
        for (i in 0 until 18) {
            val u = (t * .045f + i / 18f) % 1f
            val v = 1 - u
            val cx = v * v * v * 206f + 3 * v * v * u * 214f + 3 * v * u * u * 232f + u * u * u * 252f
            val cy = v * v * v * 214f + 3 * v * v * u * 250f + 3 * v * u * u * 296f + u * u * u * 346f
            val hw = 6f + 70f * u
            val x = cx + OFFSETS[i] * hw
            if (ROCKS.any { (rx, ry) -> hypot(x - rx, (cy - ry) * 2f) < 22f }) continue
            val len = 2.5f + 9f * u
            flow.color = withAlpha(0xFFFFFFFF.toInt(), (160 * sin(u * PI.toFloat())).toInt()); flow.strokeWidth = .8f
            c.drawLine(x - len, cy, x + len * .6f, cy + len * .08f, flow)
        }
        for (i in 0 until GLINTS) {
            val k = sin(t * glints[i * 4 + 3] + glints[i * 4 + 2])
            if (k < .6f) continue
            star(c, glints[i * 4], glints[i * 4 + 1], 1.5f + 3f * (k - .6f) / .4f, (255 * (k - .6f) / .4f).toInt())
        }
    }

    /** Les paillettes du lit de la rivière : x, y, phase, vitesse. Tirées une fois. */
    private val glints = FloatArray(GLINTS * 4).also { g ->
        val rnd = Random(77)
        for (i in 0 until GLINTS) {
            val u = .25f + rnd.nextFloat() * .7f
            val v = 1 - u
            val cx = v * v * v * 206f + 3 * v * v * u * 214f + 3 * v * u * u * 232f + u * u * u * 252f
            val cy = v * v * v * 214f + 3 * v * v * u * 250f + 3 * v * u * u * 296f + u * u * u * 346f
            g[i * 4] = cx + (rnd.nextFloat() * 1.3f - .4f) * (6f + 70f * u)
            g[i * 4 + 1] = cy
            g[i * 4 + 2] = rnd.nextFloat() * 6.28f
            g[i * 4 + 3] = 1.2f + rnd.nextFloat() * 1.6f
        }
    }

    private fun star(c: Canvas, x: Float, y: Float, len: Float, alpha: Int) {
        spark.style = Paint.Style.STROKE; spark.strokeWidth = .9f
        spark.color = withAlpha(0xFFFFF4C0.toInt(), alpha)
        c.drawLine(x - len, y, x + len, y, spark)
        c.drawLine(x, y - len, x, y + len, spark)
        spark.strokeWidth = .6f
        c.drawLine(x - len * .4f, y - len * .4f, x + len * .4f, y + len * .4f, spark)
        c.drawLine(x - len * .4f, y + len * .4f, x + len * .4f, y - len * .4f, spark)
        spark.style = Paint.Style.FILL; spark.color = withAlpha(Gilding.LIGHT, alpha)
        c.drawCircle(x, y, 1.2f, spark)
    }

    /** Le feu de camp : trois langues qui dansent, et la fumée qui part avec le vent. */
    private fun campfire(c: Canvas, t: Float) {
        val fx = 58f; val fy = 257f
        c.save(); c.translate(fx, fy - 3f); c.scale(13f, 13f); c.drawCircle(0f, 0f, 1f, glowPaint); c.restore()
        for (k in 0 until 3) {
            val h = 6f + 2.2f * sin(t * 9f + k * 2.1f) + 1.4f * sin(t * 14f + k)
            val w = 2.6f - k * .5f
            val x = fx - 2.5f + k * 2.5f
            val lean = 1.5f * sin(t * 4f + k)
            firePath.reset()
            firePath.moveTo(x - w, fy)
            firePath.quadTo(x - w, fy - h * .6f, x + lean, fy - h)
            firePath.quadTo(x + w, fy - h * .6f, x + w, fy)
            firePath.close()
            fire.color = if (k == 1) 0xFFF7C04A.toInt() else 0xFFE0662A.toInt()
            c.drawPath(firePath, fire)
        }
        for (k in 0 until 6) {
            val q = (t * .16f + k / 6f) % 1f
            val y = fy - 8f - q * 64f
            val x = fx + 5f * sin(q * 5f + t * .5f) + q * 16f
            smoke.color = withAlpha(0xFF8A847C.toInt(), (110 * (1f - q) * (q * 6f).coerceAtMost(1f)).toInt())
            c.drawCircle(x, y, 2f + 7f * q, smoke)
        }
    }

    /** Un aigle qui plane au-dessus des sommets. */
    private fun eagle(c: Canvas, t: Float) {
        val a = t * .45f
        val x = 112f + cos(a) * 30f; val y = 112f + sin(a) * 8f
        val flap = 1.2f * sin(t * 2.3f)
        flow.color = Ink.SEPIA; flow.strokeWidth = 1f
        c.drawLine(x - 8f, y - 1f - flap, x - 3.5f, y - 2.5f, flow)
        c.drawLine(x - 3.5f, y - 2.5f, x, y, flow)
        c.drawLine(x, y, x + 3.5f, y - 2.5f, flow)
        c.drawLine(x + 3.5f, y - 2.5f, x + 8f, y - 1f - flap, flow)
    }

    private companion object {
        const val PAN = 1
        const val GLINTS = 10
        const val SKIN = 0xFFDDAA82.toInt()
        const val SHIRT = 0xFFA8432E.toInt()
        const val DENIM = 0xFF41597A.toInt()
        const val HAT = 0xFF6E5236.toInt()
        const val BOOT = 0xFF3A2A1E.toInt()
        const val BEARD = 0xFF6B5240.toInt()
        val OFFSETS = floatArrayOf(-.5f, .2f, .7f, -.2f, .45f, .9f, 0f, -.4f, .6f, .3f, -.1f, .8f, -.3f, .5f, .1f, .75f, -.45f, .35f)
        val ROCKS = listOf(288f to 300f, 250f to 332f, 308f to 326f, 232f to 238f)
    }
}
