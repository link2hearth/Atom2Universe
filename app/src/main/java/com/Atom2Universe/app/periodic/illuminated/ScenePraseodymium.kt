package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.sin

/**
 * Praséodyme — les lunettes du souffleur de verre. Le verre chaud jette une lumière jaune
 * aveuglante ; le verre des lunettes, au praséodyme et au néodyme, l'éteint. Un souffleur tourne
 * sa baguette dans la flamme du chalumeau ; le verre des lunettes passe et repasse devant nous :
 * au travers, l'éblouissement disparaît et le verre en fusion se voit net.
 */
internal class ScenePraseodymium : EngravedScene() {

    override val sky = Sky.NONE
    override val nameRes = R.string.card_scene_glassblower_glasses
    override val noteRes = R.string.card_note_glassblower_glasses
    override val explainRes = R.string.card_explain_glassblower_glasses

    override fun engrave(b: Burin) {
        // L'atelier dans la pénombre.
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 50f, 0f, 262f,
            0xFF201814.toInt(), 0xFF100C0A.toInt(), Shader.TileMode.CLAMP)
        b.c.drawRect(40f, 50f, 320f, 262f, b.p)
        b.hatchRect(RectF(40f, 50f, 320f, 262f), 90f, 3.6f, .45f, withAlpha(0xFF000000.toInt(), 120))
        // L'établi.
        val bench = rect(40f, 262f, 320f, 340f)
        b.body(bench, 0xFF5A3A22.toInt(), .35f, 4f, outline = 0f, washAlpha = 255)
        b.line(40f, 262f, 320f, 262f, 1.4f, 0xFF8A6040.toInt())
        // Le pot de baguettes de couleur.
        for (k in 0 until 7) {
            val x = 268f + k * 5f
            b.line(x, 270f - 30f - (k % 3) * 9f, x - 4f + k, 290f, 3f, COLORS[k])
        }
        b.body(Path().apply { addRoundRect(262f, 262f, 306f, 300f, 4f, 4f, Path.Direction.CW) }, 0xFF8AA8A0.toInt(), .2f, 90f, outline = .8f, washAlpha = 110)
        // Une perle finie, posée sur l'établi.
        b.body(ellipse(92f, 286f, 9f, 7f), 0xFF3A70C0.toInt(), .3f, 30f, outline = .8f, washAlpha = 230)
        b.wash(ellipse(89f, 283f, 3f, 2f), Ink.WHITE, 180)
        // Le chalumeau et ses deux tuyaux.
        b.c.drawPath(Path().apply { moveTo(56f, 204f); cubicTo(50f, 236f, 70f, 250f, 60f, 300f) }, b.pen(0xFF7A2A20.toInt(), 5f))
        b.c.drawPath(Path().apply { moveTo(70f, 204f); cubicTo(66f, 240f, 90f, 252f, 84f, 300f) }, b.pen(0xFF2A5A34.toInt(), 5f))
        val torch = Path().apply { addRoundRect(40f, 184f, 118f, 204f, 6f, 6f, Path.Direction.CW) }
        b.pen(0xFF000000.toInt()).shader = LinearGradient(0f, 184f, 0f, 204f,
            intArrayOf(0xFFD8DCE0.toInt(), 0xFF8A8E94.toInt(), 0xFF4A4E54.toInt()), floatArrayOf(0f, .4f, 1f), Shader.TileMode.CLAMP)
        b.c.drawPath(torch, b.p)
        b.stroke(torch, .9f)
        for (kx in floatArrayOf(70f, 94f)) {
            b.body(Path().apply { addRoundRect(kx - 6f, 170f, kx + 6f, 184f, 2f, 2f, Path.Direction.CW) }, 0xFF2A2A2E.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
            for (i in 0 until 4) b.line(kx - 4f + i * 2.7f, 171f, kx - 4f + i * 2.7f, 183f, .5f, 0xFF5A5A60.toInt())
        }
        b.body(rect(118f, 188f, 128f, 200f), 0xFFB08A3A.toInt(), .3f, 90f, outline = .7f, washAlpha = 255)
        // La baguette de verre, tenue hors de l'image, la pointe dans la flamme.
        val rod = limb(214f, 189f, 340f, 116f, 8f, 9f)
        b.wash(rod, 0xFFB8E0D8.toInt(), 120)
        b.line(222f, 182f, 336f, 116f, 1f, withAlpha(Ink.WHITE, 170))
        b.stroke(rod, .7f, withAlpha(Ink.SEPIA, 160))
    }

    // ───────────── animation ─────────────

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val swirl = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1.4f; strokeCap = Paint.Cap.ROUND }
    private val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f; color = 0xFF2E1C14.toInt(); strokeJoin = Paint.Join.ROUND }
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 1f; color = 0x50FFE8D0 }
    private val gather = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(GX - 3f, GY - 3f, 16f, intArrayOf(0xFFFFF6D0.toInt(), 0xFFFFA838.toInt(), 0xFFC03A10.toInt()), floatArrayOf(0f, .55f, 1f), Shader.TileMode.CLAMP)
    }
    private val hot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f; strokeCap = Paint.Cap.ROUND
        shader = LinearGradient(GX, GY, GX + 30f, GY - 17f, 0xFFFF9030.toInt(), 0x00FF9030, Shader.TileMode.CLAMP)
    }
    private val flare = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(GX + 6f, GY, 96f, intArrayOf(0xF0FFE880.toInt(), 0xB0FFC040.toInt(), 0x40FF9A20, 0x00FF9A20),
            floatArrayOf(0f, .25f, .6f, 1f), Shader.TileMode.CLAMP)
    }
    private val cone = Path()
    private val tongue = Path()
    private val ball = RectF(GX - 11f, GY - 11f, GX + 11f, GY + 11f)
    private val lenses = Path().apply {
        addRoundRect(LX - 78f, LY - 58f, LX + 78f, LY + 58f, 40f, 40f, Path.Direction.CW)
        addRoundRect(LX + 102f, LY - 58f, LX + 258f, LY + 58f, 40f, 40f, Path.Direction.CW)
    }
    private val arms = Path().apply {
        moveTo(LX + 78f, LY - 40f); quadTo(LX + 90f, LY - 54f, LX + 102f, LY - 40f)
        moveTo(LX - 78f, LY - 44f); lineTo(LX - 160f, LY - 50f)
    }
    private val lensesMoved = Path()
    private val armsMoved = Path()

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val fl = 6f * sin(t * 11f) + 4f * sin(t * 17f)
        // La flamme bleue du chalumeau, qui enveloppe la pointe de la baguette.
        cone.reset()
        cone.moveTo(128f, 188f); cone.quadTo(190f, 182f, 248f + fl, 194f); cone.quadTo(190f, 206f, 128f, 200f); cone.close()
        fill.color = withAlpha(0xFF5A8AFF.toInt(), 70); c.drawPath(cone, fill)
        cone.reset()
        cone.moveTo(128f, 191f); cone.quadTo(150f, 190f, 168f + fl * .3f, 194f); cone.quadTo(150f, 198f, 128f, 197f); cone.close()
        fill.color = withAlpha(0xFFA8C8FF.toInt(), 150); c.drawPath(cone, fill)
        // Le verre fond, rougeoie, et tourne : deux traînées glissent sur la goutte.
        c.drawLine(GX, GY, GX + 30f, GY - 17f, hot)
        gather.alpha = (225 + 30 * sin(t * 3f)).toInt()
        c.drawCircle(GX, GY, 11f, gather)
        val rot = t * 240f
        swirl.color = withAlpha(0xFFFFF0C0.toInt(), 170)
        c.drawArc(ball, rot, 70f, false, swirl)
        c.drawArc(ball, rot + 180f, 70f, false, swirl)

        // Les lunettes passent et repassent devant nous.
        val dx = 58f * sin(t * TWO_PI / SWAY)
        val dy = 5f * sin(t * TWO_PI / SWAY * 2.3f)
        lenses.offset(dx, dy, lensesMoved)
        arms.offset(dx, dy, armsMoved)

        // Hors des verres : l'éblouissement jaune du verre chaud, qui noie tout.
        c.save(); c.clipOutPath(lensesMoved)
        flare.alpha = (210 + 45 * sin(t * 9f) * sin(t * 5.3f)).toInt()
        c.drawCircle(GX + 6f, GY, 96f, flare)
        tongue.reset()
        tongue.moveTo(GX, GY - 9f); tongue.quadTo(GX + 40f, GY - 12f + fl * .4f, GX + 84f + fl, GY - 2f)
        tongue.quadTo(GX + 40f, GY + 10f, GX, GY + 9f); tongue.close()
        fill.color = withAlpha(0xFFFFD050.toInt(), 170); c.drawPath(tongue, fill)
        c.restore()
        // Dans les verres : une teinte lilas, et rien d'autre.
        c.save(); c.clipPath(lensesMoved)
        fill.color = withAlpha(0xFF8A7AB8.toInt(), 58); c.drawPaint(fill)
        c.restore()
        c.drawPath(armsMoved, frame)
        c.drawPath(lensesMoved, frame)
        c.drawPath(lensesMoved, rim)
    }

    private companion object {
        const val GX = 210f
        const val GY = 191f
        const val LX = 140f
        const val LY = 200f
        const val SWAY = 11f
        const val TWO_PI = 6.2831855f
        val COLORS = intArrayOf(0xFFC8281E.toInt(), 0xFF2A70C8.toInt(), 0xFF38A050.toInt(), 0xFFE8B030.toInt(),
            0xFF8A3AB0.toInt(), 0xFF20A8A0.toInt(), 0xFFE86A20.toInt())
    }
}
