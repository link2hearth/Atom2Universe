package com.Atom2Universe.app.periodic.illuminated

import com.Atom2Universe.app.R
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Xénon — les phares. Dans les phares au xénon, un arc électrique traverse une petite bulle de
 * xénon : il en sort une lumière blanche bleutée, bien plus puissante qu'une ampoule ordinaire.
 * La nuit, sur une route de campagne, une voiture file ; son faisceau, coupé net en haut avec un
 * liseré bleu, éclaire la route devant elle. Les balises, les arbres et un panneau de virage
 * surgissent du noir quand ils y entrent, puis replongent dans l'ombre derrière.
 */
internal class SceneXenon : EngravedScene() {

    override val sky = Sky.NIGHT
    override val nameRes = R.string.card_scene_xenon_headlights
    override val noteRes = R.string.card_note_xenon_headlights
    override val explainRes = R.string.card_explain_xenon_headlights

    override fun engrave(b: Burin) {
        // La lune.
        b.glow(266f, 96f, 40f, 0xFFE8ECF8.toInt(), 80)
        b.c.drawCircle(266f, 96f, 11f, b.pen(0xFFF4F2E8.toInt()))
        b.c.drawCircle(262f, 93f, 2.4f, b.pen(withAlpha(0xFFB8B8C0.toInt(), 160)))
        b.c.drawCircle(270f, 100f, 1.6f, b.pen(withAlpha(0xFFB8B8C0.toInt(), 160)))
        // Le talus de l'autre côté, la route, le bas-côté.
        b.wash(rect(40f, 236f, 320f, ROAD_T), 0xFF141C14.toInt(), 255)
        b.wash(rect(40f, ROAD_T, 320f, ROAD_B), 0xFF22242A.toInt(), 255)
        b.hatchRect(RectF(40f, ROAD_T, 320f, ROAD_B), 0f, 2f, .4f, 0xFF0E0F12.toInt())
        b.line(40f, ROAD_T + 2f, 320f, ROAD_T + 2f, 1f, withAlpha(0xFFB8B8B0.toInt(), 80))
        b.line(40f, ROAD_B - 3f, 320f, ROAD_B - 3f, 1.2f, withAlpha(0xFFB8B8B0.toInt(), 100))
        b.wash(rect(40f, ROAD_B, 320f, 340f), 0xFF0E140E.toInt(), 255)
        b.hatchRect(RectF(40f, ROAD_B, 320f, 340f), 80f, 2.4f, .5f, 0xFF050805.toInt())
    }

    override fun sprites(): List<Sprite> = listOf(
        // La voiture, de profil, phares tournés vers la droite ; sans ses roues.
        Sprite(CAR, RectF(56f, 226f, 204f, 290f)) { b ->
            // Une berline : coffre court à l'arrière, toit au milieu, long capot devant les phares.
            val body = Path().apply {
                moveTo(62f, 274f); lineTo(60f, 258f); quadTo(61f, 250f, 70f, 249f)
                lineTo(90f, 247f); quadTo(100f, 236f, 114f, 231f); lineTo(140f, 230f)
                quadTo(150f, 234f, 164f, 246f); quadTo(190f, 248f, 197f, 252f); quadTo(201f, 255f, 200f, 262f)
                lineTo(200f, 272f); lineTo(62f, 274f); close()
            }
            b.body(body, 0xFF2A3A5A.toInt(), .45f, 0f, Fade(0f, 276f, 0f, 236f, 220, 30), outline = 1.1f, washAlpha = 255)
            // Les vitres, avec la lueur bleue du tableau de bord.
            val glass = poly(100f, 246f, 111f, 235f, 128f, 234f, 128f, 246f)
            val front = poly(132f, 234f, 143f, 234f, 158f, 246f, 132f, 246f)
            b.fill(glass, 0xFF141E2E.toInt()); b.fill(front, 0xFF141E2E.toInt())
            b.glow(150f, 244f, 10f, 0xFF4A8AFF.toInt(), 120)
            b.stroke(glass, .8f); b.stroke(front, .8f)
            b.line(70f, 260f, 196f, 260f, .7f, withAlpha(0xFF8AA0C8.toInt(), 160))
            // Les portières et leurs poignées.
            b.line(130f, 248f, 130f, 272f, .7f, withAlpha(Ink.SEPIA, 200))
            b.line(100f, 249f, 100f, 262f, .7f, withAlpha(Ink.SEPIA, 200))
            b.line(162f, 250f, 162f, 262f, .7f, withAlpha(Ink.SEPIA, 200))
            b.line(118f, 255f, 124f, 255f, 1.2f, 0xFF8A96A8.toInt())
            b.line(148f, 255f, 154f, 255f, 1.2f, 0xFF8A96A8.toInt())
            // Les passages de roue, le feu arrière, le bloc optique.
            b.fill(Path().apply { addArc(76f, 262f, 108f, 294f, 180f, 180f); close() }, 0xFF0A0C10.toInt())
            b.fill(Path().apply { addArc(154f, 262f, 186f, 294f, 180f, 180f); close() }, 0xFF0A0C10.toInt())
            b.body(rect(60f, 254f, 66f, 262f), 0xFFC8281E.toInt(), 0f, outline = .6f, washAlpha = 255)
            b.body(poly(186f, 252f, 199f, 255f, 199f, 262f, 188f, 260f), 0xFFE8F0FF.toInt(), 0f, outline = .7f, washAlpha = 255)
        }
    )

    // ───────────── animation ─────────────

    private val hills = Path()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val beam = Path().apply {
        moveTo(LAMP_X, LAMP_Y - 3f); lineTo(250f, LAMP_Y - 6f); lineTo(330f, LAMP_Y - 14f)
        lineTo(330f, ROAD_B + 6f); lineTo(LAMP_X, LAMP_Y + 4f); close()
    }
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(LAMP_X, 0f, 320f, 0f, 0x88D8E8FF.toInt(), 0x10D8E8FF, Shader.TileMode.CLAMP)
    }

    override fun animate(c: Canvas, s: Sprites, t: Float) {
        val road = t * SPEED
        // Les collines lointaines glissent à peine.
        hills.reset(); hills.moveTo(40f, 240f)
        var x = 40f
        while (x <= 320f) {
            val u = x + road * .1f
            hills.lineTo(x, 214f - 10f * sin(u * .021f) - 6f * sin(u * .053f + 1f))
            x += 8f
        }
        hills.lineTo(320f, 240f); hills.close()
        fill.color = 0xFF0E1420.toInt(); c.drawPath(hills, fill)
        // Les arbres derrière le talus, à mi-vitesse : sombres, éclairés seulement dans le faisceau.
        drawRow(c, road * .55f, TREE_GAP, 0)
        // Les balises du bord de route et, de loin en loin, le panneau de virage.
        drawRow(c, road, POST_GAP, 1)
        drawRow(c, road, SIGN_GAP, 2)
        // Le marquage au milieu de la route.
        ink.color = withAlpha(0xFFE8E8E0.toInt(), 120); ink.strokeWidth = 2.4f; ink.strokeCap = Paint.Cap.BUTT
        var d = -(road % 40f)
        while (d < 320f) {
            val lx = 40f + d
            ink.color = withAlpha(0xFFE8E8E0.toInt(), (60 + 180 * lit(lx + 10f)).toInt())
            c.drawLine(lx, ROAD_MID, lx + 20f, ROAD_MID, ink); d += 40f
        }
        ink.strokeCap = Paint.Cap.ROUND
        // Le faisceau, et sa tache sur le bitume.
        c.drawPath(beam, beamPaint)
        fill.color = withAlpha(0xFFD8E8FF.toInt(), 50); c.drawOval(206f, ROAD_T + 4f, 330f, ROAD_B - 4f, fill)
        // Le liseré bleu de la coupure, typique des projecteurs au xénon.
        ink.color = withAlpha(0xFF6A8AFF.toInt(), 150); ink.strokeWidth = 1.2f
        c.drawLine(LAMP_X + 4f, LAMP_Y - 3.5f, 250f, LAMP_Y - 6.5f, ink); c.drawLine(250f, LAMP_Y - 6.5f, 320f, LAMP_Y - 13.5f, ink)
        // La voiture et ses roues qui tournent.
        s.draw(c, CAR, dy = .6f * sin(t * 9f))
        wheel(c, 92f, road); wheel(c, 170f, road)
        // Le phare lui-même : un point d'un blanc bleuté éblouissant.
        fill.color = withAlpha(0xFFB8D0FF.toInt(), 120); c.drawCircle(LAMP_X - 2f, LAMP_Y, 9f, fill)
        fill.color = 0xFFFFFFFF.toInt(); c.drawCircle(LAMP_X - 2f, LAMP_Y, 3f, fill)
        fill.color = withAlpha(0xFFFF2A1A.toInt(), 120); c.drawCircle(62f, 258f, 6f, fill)
    }

    /** La lumière reçue à l'abscisse [x] : nulle derrière le phare, vive devant, puis qui faiblit au loin. */
    private fun lit(x: Float): Float {
        if (x < LAMP_X + 6f) return 0f
        val on = ((x - LAMP_X - 6f) / 24f).coerceAtMost(1f)
        return on / (1f + (x - LAMP_X) / 160f)
    }

    /** Une file d'objets qui défilent : [kind] 0 arbre, 1 balise, 2 panneau. */
    private fun drawRow(c: Canvas, shift: Float, gap: Float, kind: Int) {
        val first = floor((shift - 60f) / gap).toInt()
        var i = first
        while (true) {
            val x = 40f + i * gap - shift + gap * .5f * hash(i, kind + 7)
            if (x > 340f) break
            i++
            if (x < 20f) continue
            if (kind == 2 && hash(i, 11) < .5f) continue
            val l = lit(x)
            when (kind) {
                0 -> {
                    val h = 30f + 18f * hash(i, 3)
                    fill.color = mix(0xFF0A120A.toInt(), 0xFF6A8A5A.toInt(), l * .9f)
                    c.drawCircle(x, 236f - h, 12f + 6f * hash(i, 4), fill)
                    c.drawCircle(x + 8f, 240f - h * .7f, 10f, fill)
                    fill.color = mix(0xFF0A0A08.toInt(), 0xFF7A5A3A.toInt(), l * .9f)
                    c.drawRect(x - 2f, 236f - h * .6f, x + 2f, 240f, fill)
                }
                1 -> {
                    fill.color = mix(0xFF2A2A2A.toInt(), 0xFFF4F4F0.toInt(), .15f + .85f * l)
                    c.drawRect(x - 2.4f, ROAD_T - 22f, x + 2.4f, ROAD_T + 2f, fill)
                    fill.color = 0xFF0A0A0A.toInt(); c.drawRect(x - 2.4f, ROAD_T - 17f, x + 2.4f, ROAD_T - 13f, fill)
                    fill.color = mix(0xFF3A2A10.toInt(), 0xFFFFC040.toInt(), l)
                    c.drawRect(x - 1.6f, ROAD_T - 21f, x + 1.6f, ROAD_T - 18f, fill)
                    if (l > .3f) { fill.color = withAlpha(0xFFFFD070.toInt(), (120 * l).toInt()); c.drawCircle(x, ROAD_T - 19.5f, 5f * l, fill) }
                }
                else -> {
                    fill.color = mix(0xFF2A2A2A.toInt(), 0xFFB8B8B8.toInt(), l)
                    c.drawRect(x - 1.4f, ROAD_T - 40f, x + 1.4f, ROAD_T + 2f, fill)
                    fill.color = mix(0xFF2A0A0A.toInt(), 0xFFFF3A2A.toInt(), .2f + .8f * l)
                    c.drawRect(x - 14f, ROAD_T - 54f, x + 14f, ROAD_T - 38f, fill)
                    ink.color = mix(0xFF3A3A3A.toInt(), 0xFFFFFFFF.toInt(), .2f + .8f * l); ink.strokeWidth = 2.6f
                    for (k in 0 until 3) {
                        val cx = x - 7f + k * 7f
                        c.drawLine(cx - 2.5f, ROAD_T - 50f, cx + 2.5f, ROAD_T - 46f, ink)
                        c.drawLine(cx + 2.5f, ROAD_T - 46f, cx - 2.5f, ROAD_T - 42f, ink)
                    }
                    if (l > .3f) { fill.color = withAlpha(0xFFFFFFFF.toInt(), (60 * l).toInt()); c.drawRect(x - 18f, ROAD_T - 58f, x + 18f, ROAD_T - 34f, fill) }
                }
            }
        }
    }

    private fun wheel(c: Canvas, x: Float, road: Float) {
        fill.color = 0xFF141414.toInt(); c.drawCircle(x, WHEEL_Y, 12f, fill)
        fill.color = 0xFF8A9096.toInt(); c.drawCircle(x, WHEEL_Y, 7f, fill)
        ink.color = 0xFF3A3E44.toInt(); ink.strokeWidth = 1.6f
        val a0 = road / 12f
        for (k in 0 until 5) {
            val a = a0 + k * 2f * PI.toFloat() / 5f
            c.drawLine(x, WHEEL_Y, x + 6.4f * cos(a), WHEEL_Y + 6.4f * sin(a), ink)
        }
        ink.color = Ink.SEPIA; ink.strokeWidth = 1f; c.drawCircle(x, WHEEL_Y, 12f, ink)
    }

    private fun mix(a: Int, b: Int, f: Float): Int {
        val g = f.coerceIn(0f, 1f)
        val r = ((a shr 16) and 255) + ((((b shr 16) and 255) - ((a shr 16) and 255)) * g).toInt()
        val gg = ((a shr 8) and 255) + ((((b shr 8) and 255) - ((a shr 8) and 255)) * g).toInt()
        val bl = (a and 255) + (((b and 255) - (a and 255)) * g).toInt()
        return (0xFF shl 24) or (r shl 16) or (gg shl 8) or bl
    }

    private fun hash(a: Int, b: Int): Float {
        var h = a * 374761393 + b * 668265263
        h = (h xor (h ushr 13)) * 1103515245
        return ((h xor (h ushr 16)) and 0xFFFF) / 65535f
    }

    private companion object {
        const val ROAD_T = 262f
        const val ROAD_B = 300f
        const val ROAD_MID = 281f
        const val WHEEL_Y = 278f
        const val LAMP_X = 198f
        const val LAMP_Y = 257f
        const val SPEED = 150f
        const val TREE_GAP = 46f
        const val POST_GAP = 74f
        const val SIGN_GAP = 330f
        const val CAR = 1
    }
}
