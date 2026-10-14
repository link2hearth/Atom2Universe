package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import com.Atom2Universe.app.games.hamlet.HamletCharacterArt.Pose
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Pixel primitives on the 48 × 56 character grid; only used while building cached frames. */
internal open class PixelPen(val c: Canvas) {
    protected val ink = 0xFF263440.toInt()
    protected val cream = 0xFFFFEFC4.toInt()
    private val p = Paint().apply { isAntiAlias = false; strokeCap = Paint.Cap.SQUARE }
    private val edge = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 0f; color = 0xFF263440.toInt() }
    private val outline = Paint().apply { isAntiAlias = false; style = Paint.Style.STROKE; strokeWidth = 1f }
    private val path = Path()
    protected fun color(hex: Int) = hex or 0xFF000000.toInt()
    protected fun blend(a: Int, b: Int) = Color.rgb((Color.red(a) * 3 + Color.red(b)) / 4,
        (Color.green(a) * 3 + Color.green(b)) / 4, (Color.blue(a) * 3 + Color.blue(b)) / 4)
    protected fun rect(x: Int, y: Int, w: Int, h: Int, col: Int) {
        p.color = col; c.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), p)
    }
    protected fun line(x: Int, y: Int, xx: Int, yy: Int, col: Int, width: Int = 1) {
        p.color = col; p.strokeWidth = width.toFloat(); c.drawLine(x.toFloat(), y.toFloat(), xx.toFloat(), yy.toFloat(), p)
    }
    private fun trace(points: IntArray) {
        path.reset(); path.moveTo(points[0].toFloat(), points[1].toFloat())
        for (i in 2 until points.size step 2) path.lineTo(points[i].toFloat(), points[i + 1].toFloat())
        path.close()
    }
    protected fun poly(col: Int, vararg points: Int) { trace(points); p.color = col; c.drawPath(path, p) }
    /** Filled polygon with a one-pixel ink edge. */
    protected fun shape(col: Int, vararg points: Int) { trace(points); p.color = col; c.drawPath(path, p); c.drawPath(path, edge) }
    protected fun box(x: Int, y: Int, w: Int, h: Int, col: Int, border: Int = ink) {
        rect(x + 1, y, w - 2, h, border); rect(x, y + 1, w, h - 2, border)
        rect(x + 1, y + 1, w - 2, h - 2, col)
    }
    protected fun disc(x: Int, y: Int, r: Int, col: Int) { p.color = col; c.drawCircle(x + .5f, y + .5f, r + .3f, p) }
    protected fun ring(x: Int, y: Int, r: Int, col: Int) { outline.color = col; c.drawCircle(x + .5f, y + .5f, r.toFloat(), outline) }
    protected fun glint(x: Int, y: Int, col: Int, size: Int = 2) { rect(x, y - size, 1, size * 2 + 1, col); rect(x - size, y, size * 2 + 1, 1, col) }
    protected fun gem(x: Int, y: Int, col: Int, light: Int, size: Int = 4) {
        poly(ink, x, y - size - 1, x + size + 1, y, x, y + size + 1, x - size - 1, y)
        poly(col, x, y - size, x + size, y, x, y + size, x - size, y)
        line(x - 1, y - 2, x - 2, y, light, 2)
    }
    protected fun star(x: Int, y: Int, r: Int, col: Int) {
        val pts = IntArray(20)
        for (i in 0 until 10) {
            val radius = if (i % 2 == 0) r.toFloat() else r * .45f
            val a = -PI / 2 + i * PI / 5
            pts[i * 2] = x + (cos(a) * radius).roundToInt(); pts[i * 2 + 1] = y + (sin(a) * radius).roundToInt()
        }
        shape(col, *pts)
    }
}

/**
 * Hero costumes. The look gives the silhouette; rarity layers metal, gems, capes and trims on top:
 * iron and bare cloth for common heroes, silver and blue gems for rare, cape and purple gems for epic,
 * gold everywhere for legendary and mythic.
 */
internal class HeroPainter(c: Canvas) : PixelPen(c) {
    private lateinit var l: HeroLook
    private lateinit var faction: HeroFaction
    private var tier = 0
    private var f = 0
    private var attack = false
    private var cast = false
    private var flutter = 0
    private var metal = 0
    private var metalLight = 0
    private var metalDark = 0
    private var jewel = 0
    private var glow = 0
    private val wood = color(0x8A603F)
    private val woodLight = color(0xB98A5A)

    fun paint(look: HeroLook, def: HeroDef, pose: Pose, frame: Int) {
        l = look; faction = def.faction; tier = def.rarity.ordinal; f = frame
        val m = METALS[if (tier == 0) 0 else if (tier <= 2) 1 else 2]
        metal = color(m[0]); metalLight = color(m[1]); metalDark = color(m[2])
        jewel = if (tier == 0) l.accent else HamletHeroFx.main(def.rarity)
        glow = if (tier == 0) l.light else HamletHeroFx.light(def.rarity)
        val walk = if (pose == Pose.WALK) STRIDE[f] else 0
        val bob = when (pose) { Pose.WALK -> if (f % 3 == 1) -1 else 0; Pose.IDLE -> if (f == 2) -1 else 0; else -> 0 }
        attack = pose == Pose.ATTACK; cast = pose == Pose.CAST
        val hurt = pose == Pose.HURT
        val lean = if (attack) intArrayOf(-2, 0, 3, 1)[f] else if (hurt) -2 else 0
        flutter = if (pose == Pose.WALK || cast) (if (f % 2 == 0) -2 else 1) else f % 2
        val handY = if (cast) intArrayOf(29, 23, 21, 25)[f] else if (attack) intArrayOf(33, 30, 33, 35)[f] else 37 - walk / 2
        val handX = if (attack) intArrayOf(31, 34, 39, 34)[f] else if (cast) 34 else 32
        c.save(); c.translate(lean.toFloat(), bob.toFloat())
        behind()
        legs(walk)
        torso()
        hairOverShoulder()
        if (l.back == Back.SATCHEL) satchel()
        farArm(walk)
        offHand(walk)
        nearArm(handX, handY)
        head(pose)
        weapon(handX, handY)
        if (l.weapon != Weapon.LUTE) hand(handX, handY)
        if (cast) {
            gem(38, 13 + f % 2, jewel, cream, 3)
            glint(13 + f * 3, 12 + (f % 2) * 4, glow, 1)
        }
        if (hurt) { rect(20, 22, 4, 1, color(0xDB7767)); line(36, 20, 39, 16, cream, 2) }
        c.restore()
        if (pose != Pose.DOWN) pet()
    }

    // ---- Back layer --------------------------------------------------------------------------

    private fun behind() {
        if (l.head == Headgear.SUN_CROWN) sunHalo()
        if (l.back == Back.ICE_WINGS) wings()
        if (l.cape || (tier >= 2 && l.back == Back.NONE)) cape()
        when (l.back) {
            Back.QUIVER -> quiver()
            Back.PINWHEEL_PACK, Back.CHIMNEY_PACK, Back.LENS_PACK -> pack()
            else -> Unit
        }
        if (l.hair == Hair.PONYTAIL) {
            val sway = f % 2
            shape(l.hairTone, 18, 16, 11 - sway, 17, 6 - sway, 24, 7 - sway, 32, 11, 28, 13, 22, 18, 21)
            line(10 - sway, 20, 8 - sway, 28, blend(l.hairTone, cream))
        }
        if (l.hair == Hair.PIGTAILS) {
            box(10, 19, 7, 10, l.hairTone); rect(11, 21, 2, 6, blend(l.hairTone, cream)); rect(15, 19, 2, 3, l.accent)
        }
    }

    private fun sunHalo() {
        val turn = f * PI / 16
        for (k in 0 until 8) {
            val a = turn + k * PI / 4
            line(25 + (cos(a) * 13).roundToInt(), 19 + (sin(a) * 13).roundToInt(),
                25 + (cos(a) * 16).roundToInt(), 19 + (sin(a) * 16).roundToInt(), metalLight)
        }
        ring(25, 19, 11, metalLight)
    }

    private fun wings() {
        val beat = intArrayOf(0, -1, -2, -1)[f % 4]
        val ice = color(0xCDEFF7); val deep = color(0x8CC8E0); val white = color(0xFFFFFF)
        shape(deep, 17, 31, 4, 24 + beat, -6, 27 + beat, 2, 32, -4, 37, 8, 37, 15, 36)
        shape(ice, 18, 27, 8, 8 + beat, 2, 2 + beat, 4, 13 + beat, -5, 10 + beat, 0, 19 + beat, -8, 20 + beat, 4, 27, 16, 32)
        line(16, 29, 5, 6 + beat, white); line(15, 30, -3, 13 + beat, white); line(14, 32, -5, 21 + beat, white)
        rect(3, 4 + beat, 1, 1, white); rect(-4, 12 + beat, 1, 1, white)
    }

    private fun cape() {
        val fl = flutter
        val lining = if (tier <= 1) l.accent else HamletHeroFx.deep(HeroRarity.entries[tier])
        shape(l.dark, 18, 26, 28, 27, 31 + fl, 48, 9 + fl, 48, 8, 35)
        line(16, 30, 11 + fl, 46, lining, 2)
        if (tier >= 2) rect(10 + fl, 47, 20, 1, lining)
        if (tier >= 3) { line(9 + fl, 47, 30 + fl, 47, metal); line(8, 36, 9 + fl, 46, metal) }
    }

    private fun quiver() {
        val leather = if (tier >= 3) l.dark else color(0x8A5A3A)
        line(11, 39, 17, 23, ink, 6); line(11, 39, 17, 23, leather, 4)
        line(12, 37, 16, 27, blend(leather, cream))
        rect(15, 26, 4, 2, if (tier >= 1) metal else l.accent)
        line(16, 22, 13, 14, cream); line(17, 22, 18, 13, cream)
        shape(l.accent, 11, 14, 12, 10, 15, 12, 14, 16); shape(cream, 16, 13, 18, 9, 20, 12, 18, 15)
    }

    private fun pack() {
        val canvas = if (l.back == Back.CHIMNEY_PACK) color(0x8C6A3E) else blend(l.dark, color(0x8A6A44))
        box(8, 27, 10, 13, canvas); rect(9, 28, 3, 10, blend(canvas, cream)); rect(8, 33, 10, 1, l.dark)
        rect(12, 31, 3, 3, if (tier >= 1) metal else l.accent)
        when (l.back) {
            Back.PINWHEEL_PACK -> {
                line(12, 27, 12, 17, wood, 2)
                val petals = if (f % 2 == 0) PINWHEEL_STRAIGHT else PINWHEEL_TILTED
                val colors = intArrayOf(l.accent, cream, color(0xE0604A), cream)
                for (k in 0..3) shape(colors[k], 12, 15, 12 + petals[k * 4], 15 + petals[k * 4 + 1], 12 + petals[k * 4 + 2], 15 + petals[k * 4 + 3])
                rect(12, 15, 1, 1, ink)
            }
            Back.CHIMNEY_PACK -> {
                box(9, 18, 5, 10, metalDark); rect(10, 19, 1, 8, metalLight)
                val smoke = color(0xE4E2DA)
                rect(9 + f % 2, 14 - f % 3, 3, 3, smoke); rect(11 - f % 2, 9 - f % 3, 2, 2, smoke)
                box(14, 30, 4, 4, l.accent)
            }
            else -> {
                box(6, 20, 8, 8, color(0xC9A15A)); rect(8, 22, 4, 4, color(0x9FE3F2)); rect(8, 22, 1, 1, cream)
            }
        }
    }

    // ---- Body ----------------------------------------------------------------------------------

    private fun legs(walk: Int) {
        val armored = l.build == Build.ARMORED
        val stride = abs(walk) / 2
        box(18 - walk / 2, 39, 6, 11 - stride, if (armored) metalDark else l.dark)
        box(25 + walk / 2, 39, 6, 11 + stride, if (armored) metal else l.cloth)
        if (armored) rect(26 + walk / 2, 41, 2, 5, metalLight)
        val boot = color(if (tier >= 3) 0x3E3236 else 0x594B44)
        box(17 - walk / 2, 48 - stride, 8, 4, boot)
        box(24 + walk / 2, 49, 9, 4, boot)
        rect(26 + walk / 2, 49, 4, 1, blend(boot, cream))
        if (tier >= 1) rect(30 + walk / 2, 50, 2, 2, metalLight)
        if (tier >= 3) { rect(25 + walk / 2, 49, 7, 1, metal); rect(18 - walk / 2, 48 - stride, 6, 1, metal) }
    }

    private fun torso() {
        when (l.build) {
            Build.STOUT -> box(16, 27, 16, 16, l.cloth)
            Build.ARMORED -> box(15, 27, 18, 16, l.cloth)
            else -> box(17, 27, 14, 16, l.cloth)
        }
        rect(19, 30, 3, 9, l.light); rect(28, 30, 2, 10, l.dark)
        if (l.build == Build.ARMORED) {
            box(18, 28, 12, 12, metal); rect(19, 30, 3, 8, metalLight); rect(27, 30, 2, 9, metalDark)
        }
        if (l.build == Build.ROBED) {
            poly(ink, 18, 34, 30, 34, 33, 48, 16, 48)
            poly(l.cloth, 19, 35, 29, 35, 31, 46, 18, 46)
            rect(21, 36, 2, 10, l.light); rect(27, 37, 2, 9, l.dark)
            rect(18, 45, 13, 2, when (tier) { 0 -> l.dark; 1 -> l.accent; 2 -> glow; else -> metal })
            if (tier >= 3) { rect(24, 35, 1, 10, metal); rect(23, 41, 3, 3, metal); rect(24, 42, 1, 1, jewel) }
        }
        val left = if (l.build == Build.ARMORED) 15 else 17
        rect(left, 39, if (l.build == Build.ARMORED) 18 else 14, 3, if (tier >= 3) metalDark else l.dark)
        if (tier >= 3) { box(22, 38, 6, 5, metal); rect(24, 39, 2, 3, jewel); rect(24, 39, 1, 1, cream) }
        else {
            box(23, 39, 4, 3, if (tier == 0) l.accent else metal)
            if (tier == 2) rect(24, 40, 2, 1, jewel)
        }
        if (tier >= 2 && l.back != Back.SATCHEL) emblem()
    }

    /** Faction badge on the chest, framed in gold from legendary up. */
    private fun emblem() {
        if (tier >= 3) box(20, 30, 9, 8, metal)
        box(21, 31, 7, 6, if (l.build == Build.ARMORED) metalDark else l.dark)
        when (faction) {
            HeroFaction.GROVE -> poly(glow, 23, 32, 26, 32, 25, 36, 22, 35)
            HeroFaction.DAWN -> glint(24, 33, glow, 2)
            HeroFaction.FORGE -> { rect(22, 33, 5, 2, glow); rect(24, 33, 1, 4, glow) }
        }
    }

    private fun hairOverShoulder() {
        when (l.hair) {
            Hair.LONG -> { box(15, 18, 6, 21, l.hairTone); rect(16, 20, 1, 16, blend(l.hairTone, cream)) }
            Hair.WAVY -> for (j in 0..5) {
                box(14 + (j + f) % 2, 18 + j * 4, 6, 6, l.hairTone)
                rect(15 + (j + f) % 2, 19 + j * 4, 1, 3, blend(l.hairTone, cream))
            }
            else -> Unit
        }
    }

    private fun satchel() {
        val leather = color(0x8A5A3A)
        line(29, 29, 19, 39, ink, 4); line(29, 29, 19, 39, leather, 2)
        poly(color(0x5FA86A), 11, 37, 9, 31, 13, 34, 15, 30, 16, 37)
        box(9, 36, 11, 9, leather); rect(10, 37, 9, 3, blend(leather, cream)); rect(14, 39, 2, 2, l.accent)
    }

    private fun farArm(walk: Int) {
        val armored = l.build == Build.ARMORED
        line(18, 29, 15 - walk / 2, 36 + walk / 2, ink, 6)
        line(18, 29, 15 - walk / 2, 36 + walk / 2, if (armored) metalDark else l.cloth, 4)
        box(12 - walk / 2, 34 + walk / 2, 5, 5, if (armored) metalDark else l.skin)
        if (armored || (tier >= 2 && l.build != Build.ROBED)) {
            box(12, 27, 9, 7, metal); rect(13, 28, 6, 2, metalLight)
            if (tier >= 3) rect(13, 32, 7, 1, metalDark)
        }
    }

    private fun nearArm(x: Int, y: Int) {
        val armored = l.build == Build.ARMORED
        line(29, 30, x, y, ink, 6); line(29, 30, x, y, if (armored) metal else l.light, 4)
        if (armored || (tier >= 1 && l.build != Build.ROBED)) {
            box(26, 27, 9, 7, metal); rect(27, 28, 6, 2, metalLight)
            if (tier >= 2) rect(30, 30, 2, 2, jewel)
        } else if (l.build == Build.ROBED && tier >= 1) {
            box(26, 28, 8, 5, if (tier >= 3) metal else l.accent)
        }
    }

    private fun hand(x: Int, y: Int) {
        box(x - 2, y - 2, 5, 5, if (l.build == Build.ARMORED) metalDark else l.skin)
        if (tier >= 3) rect(x - 2, y + 2, 5, 1, metal)
    }

    private fun offHand(walk: Int) {
        val sy = if (attack) -2 else 0
        when (l.off) {
            OffHand.ROUND_SHIELD, OffHand.LEAF_SHIELD -> {
                val board = color(0xA8784A)
                disc(14, 38 + sy, 7, ink); disc(14, 38 + sy, 6, board)
                rect(11, 32 + sy, 1, 13, blend(board, ink)); rect(16, 32 + sy, 1, 13, blend(board, ink))
                rect(9, 35 + sy, 2, 6, blend(board, cream))
                if (l.off == OffHand.LEAF_SHIELD) {
                    for (k in 0..4) {
                        val a = -PI / 2 + k * 1.25
                        val lx = 14 + (cos(a) * 7).roundToInt(); val ly = 38 + sy + (sin(a) * 7).roundToInt()
                        shape(l.light, lx - 2, ly, lx, ly - 2, lx + 2, ly, lx, ly + 2)
                    }
                    box(12, 36 + sy, 5, 5, l.accent); rect(13, 37 + sy, 1, 1, cream); rect(14, 35 + sy, 1, 2, color(0x5A3A26))
                } else { box(12, 36 + sy, 5, 5, metal); rect(13, 37 + sy, 1, 1, metalLight) }
            }
            OffHand.KITE_SHIELD -> {
                poly(ink, 8, 31 + sy, 20, 29 + sy, 22, 42 + sy, 15, 49 + sy, 8, 43 + sy)
                poly(if (tier >= 1) metal else l.dark, 9, 32 + sy, 19, 30 + sy, 21, 42 + sy, 15, 48 + sy, 9, 43 + sy)
                poly(l.dark, 11, 33 + sy, 18, 32 + sy, 19, 41 + sy, 15, 45 + sy, 11, 41 + sy)
                when (faction) {
                    HeroFaction.DAWN -> { glint(15, 38 + sy, l.accent, 3); rect(15, 38 + sy, 1, 1, cream) }
                    else -> { line(15, 34 + sy, 15, 43 + sy, l.accent, 2); line(12, 37 + sy, 18, 37 + sy, l.accent, 2) }
                }
                line(10, 33 + sy, 18, 31 + sy, metalLight)
            }
            OffHand.TOWER_SHIELD -> {
                val stone = color(0x9C9488)
                box(3, 25 + sy, 16, 25, stone); rect(4, 26 + sy, 3, 23, color(0xC2B8A8)); rect(15, 26 + sy, 3, 23, color(0x6E675F))
                box(7, 29 + sy, 8, 17, l.dark)
                rect(10, 31 + sy, 2, 13, glow); rect(8, 36 + sy, 6, 2, glow)
                for (y in intArrayOf(27, 47)) { rect(5, y + sy, 2, 2, metal); rect(15, y + sy, 2, 2, metal) }
            }
            OffHand.DRUM -> {
                line(18, 30, 14, 37, l.dark, 2)
                box(8, 36, 12, 9, l.accent); rect(9, 36, 10, 2, cream)
                for (k in 0..2) line(10 + k * 4, 38, 12 + k * 4, 44, metalLight)
                rect(9, 43, 10, 1, metal)
            }
            OffHand.DAGGER -> {
                val hx = 14 - walk / 2; val hy = 36 + walk / 2
                line(hx + 1, hy + 2, hx - 4, hy + 9, ink, 3); line(hx + 1, hy + 2, hx - 4, hy + 9, metalLight)
                rect(hx - 1, hy + 1, 4, 2, l.accent)
            }
            OffHand.NONE -> Unit
        }
    }

    // ---- Head ----------------------------------------------------------------------------------

    private fun head(pose: Pose) {
        val helmet = l.head in HELMETS
        val mass = when {
            l.head == Headgear.HOOD || l.head == Headgear.LEAF_HOOD -> l.cloth
            l.head == Headgear.HEADSCARF -> l.accent
            l.head == Headgear.HORNED_CROWN -> l.cloth
            helmet -> metal
            l.hair == Hair.BALD -> l.skin
            else -> l.hairTone
        }
        box(17, 14, 16, 16, mass)
        if (l.head == Headgear.GREAT_HELM) { greatHelm(); return }
        box(20, 17, 13, 12, l.skin)
        val shown = l.hair != Hair.HIDDEN && l.hair != Hair.BALD && !helmet
        if (shown) {
            rect(19, 17, 4, 4, l.hairTone); rect(20, 15, 11, 3, l.hairTone); rect(21, 19, 3, 2, blend(l.hairTone, cream))
        }
        if (l.hair == Hair.BALD) { rect(17, 19, 3, 6, l.hairTone); rect(22, 15, 6, 1, blend(l.skin, cream)); rect(21, 21, 2, 3, blend(l.skin, ink)) }
        rect(32, 23, 2, 3, l.skin)
        if (l.head == Headgear.MASK) {
            rect(21, 21, 13, 3, l.dark); line(19, 22, 14, 25, l.dark, 2)
            rect(26, 22, 2, 1, cream); rect(31, 22, 1, 1, cream)
        } else {
            val blink = (pose == Pose.IDLE && f == 3) || pose == Pose.HURT || pose == Pose.DOWN
            rect(26, 22, 2, if (blink) 1 else 3, ink); rect(31, 22, 1, if (blink) 1 else 2, ink)
            if (!blink) rect(26, 22, 1, 1, if (tier >= 3) glow else cream)
        }
        when (l.beard) {
            0 -> if (!l.scarf) rect(27, 27, 3, 1, color(0xAA6D60))
            1 -> { box(22, 25, 12, 5, l.hairTone); rect(27, 26, 4, 1, blend(l.hairTone, ink)) }
            2 -> { poly(ink, 22, 25, 34, 24, 30, 36, 24, 33); poly(l.hairTone, 23, 26, 33, 25, 29, 35, 25, 32); rect(26, 26, 6, 1, blend(l.hairTone, ink)) }
            else -> {
                shape(l.hairTone, 21, 25, 34, 24, 31, 40, 27, 43, 23, 34)
                line(26, 29, 27, 40, blend(l.hairTone, ink)); rect(26, 26, 7, 1, blend(l.hairTone, ink))
            }
        }
        if (l.scarf) { rect(22, 26, 12, 3, l.dark); line(18, 28, 9 - f % 2, 32, if (tier >= 1) l.accent else l.light, 3) }
        hairFront()
        headgear()
    }

    private fun greatHelm() {
        box(16, 11, 18, 19, metal); rect(18, 13, 4, 15, metalLight); rect(30, 13, 3, 15, metalDark)
        rect(24, 10, 4, 6, metalLight)
        rect(23, 20, 11, 3, ink); rect(27, 21, 2, 1, glow); rect(32, 21, 1, 1, glow)
        for (k in 0..2) rect(27 + k * 2, 25, 1, 2, ink)
        rect(17, 28, 16, 2, metalDark)
    }

    private fun hairFront() {
        val h = l.hairTone; val shine = blend(h, cream)
        when (l.hair) {
            Hair.BRAID -> { for (j in 0..4) box(16 + (j + f) % 2, 25 + j * 3, 4, 4, h); rect(16, 38, 4, 2, l.accent) }
            Hair.LONG, Hair.WAVY -> rect(32, 17, 3, 7, h)
            Hair.MESSY -> { rect(21, 12, 3, 3, h); rect(26, 11, 3, 4, h); rect(30, 13, 3, 3, h); rect(27, 12, 1, 1, shine) }
            Hair.CURLS -> {
                for (j in 0..3) box(15 + j * 4, 12 + j % 2, 6, 6, h)
                box(14, 18, 5, 15, h); box(16, 15, 5, 4, l.light); rect(18, 16, 1, 1, cream)
            }
            Hair.SPIKY -> {
                poly(h, 17, 22, 15, 14, 20, 15, 19, 9, 25, 13, 28, 10, 34, 18, 24, 18, 20, 21)
                rect(23, 13, 2, 2, shine)
            }
            Hair.BOB -> { box(16, 16, 6, 14, h); rect(20, 15, 13, 3, h); rect(17, 18, 1, 9, shine) }
            Hair.PONYTAIL -> rect(16, 16, 3, 3, l.accent)
            Hair.PIGTAILS -> { box(32, 21, 5, 9, h); rect(32, 21, 5, 1, l.accent); rect(33, 23, 1, 4, shine) }
            Hair.TOPKNOT -> { shape(h, 20, 15, 19, 11, 21, 8, 26, 8, 28, 11, 27, 15); rect(22, 9, 2, 2, shine); rect(20, 14, 8, 2, l.accent) }
            Hair.FLAME -> {
                val flick = f % 2
                shape(h, 16, 22, 13, 9, 18, 12, 19, 2 + flick, 24, 9, 28, 0 - flick, 31, 9, 37, 5 + flick, 34, 17, 21, 18)
                poly(l.accent, 19, 12, 20, 6 + flick, 23, 11); poly(l.accent, 27, 9, 28, 4 - flick, 30, 10)
                rect(34, 8 + flick, 1, 2, l.accent)
            }
            else -> Unit
        }
    }

    private fun headgear() {
        when (l.head) {
            Headgear.LEAF_CLIP -> poly(l.light, 17, 15, 14, 10, 19, 12, 23, 9, 21, 15)
            Headgear.CAP -> {
                box(17, 10, 16, 7, l.accent); rect(18, 11, 14, 2, blend(l.accent, cream))
                box(29, 14, 8, 3, blend(l.accent, ink))
            }
            Headgear.STRAW_HAT -> {
                val straw = color(0xE2C46E)
                box(19, 6, 13, 9, straw); rect(20, 7, 4, 6, blend(straw, cream)); rect(19, 11, 13, 2, l.accent)
                shape(straw, 11, 14, 39, 14, 36, 17, 14, 17)
                rect(14, 15, 22, 1, blend(straw, ink))
            }
            Headgear.REED_HAT -> {
                val straw = color(0xD8C48A)
                shape(straw, 9, 17, 25, 3, 41, 17)
                line(15, 15, 24, 6, blend(straw, ink)); line(35, 15, 26, 6, blend(straw, ink)); line(25, 6, 25, 15, blend(straw, ink))
                rect(14, 16, 22, 1, l.dark)
            }
            Headgear.FEATHER_CAP -> {
                shape(cream, 21, 11, 12, 3, 8, 5, 18, 12)
                line(19, 10, 11, 5, color(0xB9C6D2))
                box(18, 9, 15, 7, l.dark); rect(19, 10, 13, 2, l.cloth); box(30, 13, 7, 3, l.dark)
            }
            Headgear.BERET -> {
                line(19, 10, 13, 2, color(0xE0604A), 2); line(19, 10, 11, 5, cream)
                shape(l.accent, 15, 15, 17, 9, 26, 6, 35, 9, 36, 14)
                rect(20, 9, 6, 2, blend(l.accent, cream)); rect(29, 7, 2, 2, l.accent)
            }
            Headgear.HOOD -> {
                poly(ink, 16, 25, 15, 15, 24, 10, 32, 14, 35, 20, 21, 19, 21, 29)
                poly(l.cloth, 17, 23, 17, 16, 24, 12, 30, 15, 32, 18, 20, 18, 20, 26)
                rect(20, 13, 6, 2, l.light)
                if (tier >= 1) rect(20, 18, 13, 1, if (tier >= 3) metal else l.accent)
            }
            Headgear.LEAF_HOOD -> {
                val bark = color(0x5A3A26)
                line(20, 12, 16, 4, ink, 4); line(20, 12, 16, 4, bark, 2); line(16, 4, 13, 2, bark)
                line(29, 11, 33, 2, ink, 4); line(29, 11, 33, 2, bark, 2); line(33, 2, 36, 0, bark)
                rect(15, 6, 2, 2, l.accent); rect(34, 4, 2, 2, l.accent); rect(12, 1, 2, 2, l.accent)
                poly(ink, 15, 27, 13, 15, 23, 9, 33, 12, 36, 20, 21, 19, 21, 30)
                poly(l.cloth, 16, 25, 15, 16, 23, 11, 31, 14, 33, 18, 20, 18, 20, 27)
                for (k in 0..3) shape(l.light, 14 + k * 5, 15 - (k % 2) * 3, 16 + k * 5, 11 - (k % 2) * 3, 19 + k * 5, 14 - (k % 2) * 3)
                rect(20, 18, 13, 1, metal)
            }
            Headgear.HELM -> {
                shape(l.accent, 23, 8, 19, 1, 26, 3, 31, 0, 30, 7)
                shape(metal, 15, 21, 16, 11, 21, 7, 29, 7, 34, 11, 35, 21, 32, 21, 32, 15, 19, 15, 19, 21)
                rect(19, 9, 6, 2, metalLight); rect(17, 14, 17, 2, metalDark); rect(24, 15, 2, 5, metal)
            }
            Headgear.CREST_HELM -> {
                shape(l.accent, 14, 14, 16, 6, 21, 2, 29, 2, 34, 5, 37, 11, 33, 10, 29, 6, 24, 5, 20, 7, 18, 14)
                line(18, 7, 23, 4, blend(l.accent, cream))
                shape(metal, 15, 21, 16, 12, 21, 9, 29, 9, 34, 12, 35, 21, 32, 22, 32, 16, 19, 16, 19, 22)
                rect(19, 11, 5, 2, metalLight); rect(17, 15, 17, 2, metalDark); rect(25, 16, 2, 5, metal)
            }
            Headgear.HORNED_HELM -> {
                val bone = color(0xEDE3C8)
                shape(bone, 18, 13, 12, 10, 10, 1, 15, 6, 20, 10)
                shape(bone, 31, 10, 36, 6, 40, 0, 39, 9, 34, 14)
                shape(metal, 15, 21, 16, 11, 21, 8, 29, 8, 34, 11, 35, 21, 31, 21, 31, 17, 19, 17, 19, 21)
                rect(19, 10, 5, 2, metalLight); rect(16, 16, 19, 2, l.accent); rect(24, 16, 2, 2, jewel)
            }
            Headgear.HORNED_CROWN -> {
                val flick = f % 2
                shape(l.accent, 18, 13, 13, 4 + flick, 18, 7, 19, 0 + flick, 23, 9)
                rect(17, 6 + flick, 2, 2, color(0xFFE08A))
                shape(metal, 18, 14, 11, 9, 10, 0, 15, 5, 21, 10)
                shape(metal, 30, 10, 36, 4, 41, 0, 39, 9, 33, 15)
                shape(l.cloth, 15, 21, 16, 11, 21, 8, 29, 8, 34, 11, 35, 21, 31, 21, 31, 17, 19, 17, 19, 21)
                rect(19, 10, 5, 2, l.light); rect(16, 15, 19, 2, metal); gem(25, 15, jewel, cream, 2)
            }
            Headgear.WIZARD_HAT -> {
                poly(ink, 14, 17, 17, 12, 20, 3, 27, 8, 31, 15, 36, 18, 34, 21, 15, 20)
                poly(l.cloth, 17, 16, 20, 5, 25, 9, 30, 16)
                rect(17, 17, 16, 2, if (tier >= 3) metal else l.accent)
                glint(23, 12, cream); rect(27, 14, 1, 1, l.accent); rect(20, 9, 1, 1, l.accent)
            }
            Headgear.ANTLERS -> {
                val bone = color(0xEAD7A0)
                line(19, 17, 13, 9, ink, 4); line(28, 15, 32, 8, ink, 4)
                line(19, 17, 13, 9, bone, 3); line(13, 9, 13, 4, bone, 2); line(15, 12, 9, 11, bone, 2)
                line(28, 15, 32, 8, bone, 3); line(32, 8, 31, 3, bone, 2); line(31, 10, 37, 8, bone, 2)
                if (tier >= 2) { rect(12, 4, 2, 2, l.light); rect(36, 7, 2, 2, l.light); rect(31, 3, 1, 1, glow) }
            }
            Headgear.GOGGLES -> {
                box(16, 14, 18, 7, l.dark)
                box(18, 15, 6, 5, metal); box(26, 15, 6, 5, metal)
                rect(19, 16, 4, 3, l.accent); rect(27, 16, 4, 3, l.accent); rect(19, 16, 2, 1, cream); rect(27, 16, 2, 1, cream)
            }
            Headgear.LENS_GOGGLES -> {
                rect(17, 14, 17, 2, color(0x6B4A35))
                box(20, 11, 7, 6, l.accent); rect(21, 12, 5, 4, color(0x9FE3F2)); rect(21, 12, 2, 1, cream)
                box(27, 12, 5, 5, l.accent); rect(28, 13, 3, 3, color(0x9FE3F2))
            }
            Headgear.BANDANA -> {
                box(17, 13, 17, 5, l.accent); rect(19, 14, 2, 1, cream); rect(26, 15, 2, 1, cream)
                line(17, 16, 12, 19, l.accent, 2); line(17, 17, 13, 22, blend(l.accent, ink), 2)
            }
            Headgear.HEADSCARF -> {
                poly(ink, 16, 26, 15, 13, 24, 9, 33, 12, 35, 18, 21, 18, 21, 28)
                poly(l.accent, 17, 24, 16, 14, 24, 11, 32, 13, 33, 17, 20, 17, 20, 26)
                rect(19, 13, 1, 1, cream); rect(24, 12, 1, 1, cream); rect(29, 14, 1, 1, cream); rect(18, 20, 1, 1, cream)
                box(13, 22, 5, 5, l.accent); line(14, 26, 11, 31, l.accent, 2)
            }
            Headgear.FLOWER_CROWN -> {
                val petals = intArrayOf(l.accent, cream, color(0xF6D86A), l.accent, cream)
                for (k in 0..4) {
                    val x = 17 + k * 4; val y = 13 - (k % 2)
                    rect(x - 1, y + 1, 1, 1, color(0x5FA86A)); box(x, y - 1, 4, 4, petals[k]); rect(x + 1, y, 2, 2, color(0xF6D86A))
                }
            }
            Headgear.STAR_CLIPS -> {
                if (l.hair == Hair.TOPKNOT) star(28, 9, 3, l.accent) else { star(18, 19, 3, l.accent); rect(22, 15, 2, 1, l.accent) }
                if (tier >= 2) glint(24, 11, glow, 1)
            }
            Headgear.TIARA -> {
                rect(19, 15, 13, 2, metal)
                poly(metal, 22, 15, 25, 9, 28, 15); poly(metal, 18, 16, 19, 12, 21, 16); poly(metal, 29, 16, 31, 12, 32, 16)
                gem(25, 13, jewel, cream, 2)
            }
            Headgear.CIRCLET -> {
                rect(19, 15, 14, 1, metal)
                poly(l.light, 28, 15, 31, 11, 34, 13, 31, 16); rect(23, 14, 2, 2, jewel)
            }
            Headgear.GLASSES -> {
                val frame = color(0x5A3A26)
                for (x in intArrayOf(24, 30)) { rect(x, 20, 5, 1, frame); rect(x, 25, 5, 1, frame); rect(x, 20, 1, 6, frame); rect(x + 4, 20, 1, 6, frame) }
                rect(25, 21, 1, 1, cream); rect(31, 21, 1, 1, cream)
                rect(20, 15, 13, 3, l.hairTone); poly(l.accent, 17, 14, 15, 10, 19, 12, 21, 9, 21, 14); rect(18, 12, 1, 1, cream)
            }
            Headgear.SUN_CROWN -> { rect(19, 15, 13, 2, metal); gem(25, 15, jewel, cream, 2) }
            Headgear.ICE_CROWN -> {
                val ice = color(0xDDF6FF); val lift = f % 2
                shape(ice, 18, 12 - lift, 20, 4 - lift, 22, 12 - lift)
                shape(ice, 23, 11 - lift, 25, -1 - lift, 28, 11 - lift)
                shape(ice, 29, 12 - lift, 32, 3 - lift, 34, 12 - lift)
                line(25, 1 - lift, 25, 9 - lift, cream); line(20, 6 - lift, 20, 10 - lift, cream)
                rect(19, 14, 14, 2, color(0x9FD6EA)); rect(25, 14, 2, 2, jewel)
            }
            else -> Unit
        }
    }

    // ---- Weapons -------------------------------------------------------------------------------

    private fun weapon(x: Int, y: Int) {
        val ax = if (attack) 5 else 0
        when (l.weapon) {
            Weapon.BOW -> {
                val flex = if (attack && f == 1) 3 else 0
                line(x + 1, y - 13, x + 6 + flex, y - 7, ink, 3); line(x + 6 + flex, y - 7, x + 6 + flex, y + 6, ink, 3); line(x + 6 + flex, y + 6, x + 1, y + 12, ink, 3)
                line(x + 1, y - 12, x + 5 + flex, y - 6, l.accent, 2); line(x + 5 + flex, y - 6, x + 5 + flex, y + 5, l.accent, 2); line(x + 5 + flex, y + 5, x + 1, y + 11, l.accent, 2)
                line(x, y - 12, x - flex, y, cream); line(x - flex, y, x, y + 11, cream)
                if (!attack || f < 2) { line(x - 6, y, x + 9, y, cream); poly(l.light, x + 8, y - 2, x + 12, y, x + 8, y + 2) }
            }
            Weapon.LONGBOW -> {
                val flex = if (attack && f == 1) 3 else 0
                val limb = color(0x3E6B3A)
                line(x + 1, y - 18, x + 7 + flex, y - 9, ink, 4); line(x + 7 + flex, y - 9, x + 8 + flex, y + 8, ink, 4); line(x + 8 + flex, y + 8, x + 1, y + 17, ink, 4)
                line(x + 1, y - 17, x + 6 + flex, y - 9, limb, 2); line(x + 6 + flex, y - 9, x + 7 + flex, y + 8, limb, 2); line(x + 7 + flex, y + 8, x + 1, y + 16, limb, 2)
                line(x + 3, y - 15, x + 6 + flex, y - 10, metal); line(x + 3, y + 14, x + 7 + flex, y + 9, metal)
                box(x - 1, y - 20, 4, 4, metal); box(x - 1, y + 16, 4, 4, metal)
                rect(x + 5 + flex, y - 13, 2, 2, l.light); rect(x + 6 + flex, y + 11, 2, 2, l.light)
                line(x + 1, y - 16, x - flex, y, cream); line(x - flex, y, x + 1, y + 16, cream)
                gem(x + 7 + flex, y, jewel, cream, 2)
                if (!attack || f < 2) {
                    line(x - 7, y, x + 12, y, glow); rect(x - 8, y - 1, 3, 3, l.accent)
                    poly(glow, x + 11, y - 3, x + 16, y, x + 11, y + 3); rect(x + 12, y, 2, 1, cream)
                }
            }
            Weapon.CROSSBOW -> {
                line(x - 6, y + 2, x + 9, y, ink, 5); line(x - 6, y + 2, x + 9, y, wood, 3)
                line(x + 8, y - 7, x + 10, y, ink, 4); line(x + 10, y, x + 8, y + 7, ink, 4)
                line(x + 8, y - 6, x + 9, y, metal, 2); line(x + 9, y, x + 8, y + 6, metal, 2)
                line(x + 8, y - 6, x + 3, y, cream); line(x + 3, y, x + 8, y + 6, cream)
                if (!attack || f < 2) { line(x + 3, y - 1, x + 14, y - 1, metalLight); poly(l.accent, x + 13, y - 3, x + 17, y - 1, x + 13, y + 1) }
            }
            Weapon.SWORD -> {
                val tipX = if (attack && f >= 2) x + 8 else x + 3
                val tipY = if (attack && f >= 2) y - 8 else y - 21
                line(x, y + 4, x, y - 5, l.dark, 3); line(x - 4, y - 4, x + 4, y - 4, l.accent, 2)
                poly(ink, x - 2, y - 5, tipX, tipY, x + 3, y - 5)
                poly(metalLight, x - 1, y - 6, tipX, tipY + 2, x + 2, y - 5)
                line(x, y - 6, tipX, tipY + 2, cream)
            }
            Weapon.RAPIER -> {
                val tipX = if (attack && f >= 2) x + 15 else x + 4
                val tipY = if (attack && f >= 2) y - 7 else y - 25
                line(x, y - 5, tipX, tipY, ink, 3); line(x, y - 5, tipX, tipY, metalLight)
                box(x - 3, y - 6, 7, 3, metal); line(x - 3, y - 5, x - 3, y + 2, metal)
                line(x, y + 4, x, y - 3, l.dark, 2); rect(x - 1, y + 3, 3, 2, metal)
            }
            Weapon.CURVED_BLADE -> {
                if (attack && f >= 2) {
                    line(x, y - 4, x + 7, y - 9, ink, 4); line(x + 7, y - 9, x + 15, y - 10, ink, 4)
                    line(x, y - 4, x + 7, y - 9, metalLight, 2); line(x + 7, y - 9, x + 15, y - 10, metalLight, 2)
                } else {
                    line(x, y - 5, x + 1, y - 14, ink, 4); line(x + 1, y - 14, x + 5, y - 22, ink, 4)
                    line(x, y - 5, x + 1, y - 14, metalLight, 2); line(x + 1, y - 14, x + 5, y - 22, metalLight, 2)
                    line(x + 2, y - 6, x + 3, y - 14, cream)
                }
                box(x - 2, y - 6, 5, 3, l.accent); line(x, y + 4, x, y - 3, l.dark, 3)
            }
            Weapon.FLAME_SWORD -> flameSword(x, y)
            Weapon.DAGGER -> {
                val tipX = if (attack && f >= 2) x + 9 else x + 3
                val tipY = if (attack && f >= 2) y - 5 else y - 12
                line(x, y + 3, x, y - 3, l.dark, 3); line(x - 3, y - 3, x + 3, y - 3, if (tier == 0) l.accent else metal, 2)
                poly(ink, x - 2, y - 4, tipX, tipY, x + 3, y - 4); poly(metalLight, x - 1, y - 5, tipX, tipY + 2, x + 2, y - 4)
                if (tier >= 1) rect(x, y + 3, 1, 1, jewel)
            }
            Weapon.KEY -> {
                val tx = x + if (attack && f >= 2) 9 else 3
                val ty = y + if (attack && f >= 2) -12 else -17
                line(x, y - 1, tx, ty, ink, 4); line(x, y - 1, tx, ty, metal, 2)
                box(tx, ty + 1, 5, 3, metal); box(tx, ty + 5, 4, 3, metal)
                disc(x, y + 5, 4, ink); disc(x, y + 5, 3, metal); disc(x, y + 5, 1, ink); rect(x - 2, y + 3, 1, 1, metalLight)
                rect(x + 3, y + 8, 2, 4, l.accent)
            }
            Weapon.SPEAR -> {
                line(x, y + 9, x + ax, y - 14, ink, 4); line(x, y + 8, x + ax, y - 13, wood, 2)
                shape(metalLight, x - 3 + ax, y - 13, x + ax, y - 25, x + 3 + ax, y - 13)
                rect(x - 2 + ax, y - 13, 5, 2, l.accent)
                if (tier >= 1) shape(l.accent, x + ax + 1, y - 12, x + ax + 8, y - 10, x + ax + 1, y - 8)
            }
            Weapon.MALLET -> {
                line(x - 1, y + 4, x + 6, y - 6 - ax, ink, 4); line(x - 1, y + 4, x + 6, y - 6 - ax, wood, 2)
                box(x + 2, y - 14 - ax, 10, 12, woodLight); rect(x + 3, y - 13 - ax, 2, 10, blend(woodLight, cream))
                rect(x + 2, y - 10 - ax, 10, 1, wood); rect(x + 2, y - 6 - ax, 10, 1, wood)
            }
            Weapon.HAMMER -> {
                line(x - 1, y + 4, x + 6, y - 5 - ax, ink, 4); line(x - 1, y + 4, x + 6, y - 5 - ax, wood, 2)
                box(x + 3, y - 12 - ax, 7, 11, metal); rect(x + 4, y - 11 - ax, 2, 9, metalLight); rect(x + 8, y - 11 - ax, 1, 9, metalDark)
            }
            Weapon.MAUL -> {
                line(x - 2, y + 9, x + 5, y - 13 - ax, ink, 5); line(x - 2, y + 9, x + 5, y - 13 - ax, color(0x6E675F), 3)
                val stone = color(0x9C9488)
                box(x + 1, y - 26 - ax, 13, 14, stone); rect(x + 2, y - 25 - ax, 3, 12, color(0xC2B8A8)); rect(x + 11, y - 25 - ax, 2, 12, color(0x6E675F))
                rect(x + 6, y - 23 - ax, 2, 9, glow); rect(x + 4, y - 20 - ax, 6, 2, glow)
            }
            Weapon.PITCHFORK -> {
                line(x, y + 10, x + ax, y - 15, ink, 4); line(x, y + 10, x + ax, y - 15, wood, 2)
                rect(x - 4 + ax, y - 17, 9, 2, metal)
                for (k in -1..1) { line(x + k * 4 + ax, y - 17, x + k * 4 + ax, y - 24, ink, 3); line(x + k * 4 + ax, y - 17, x + k * 4 + ax, y - 23, metalLight) }
            }
            Weapon.LEAF_STAFF -> {
                staff(x, y, l.accent)
                poly(l.light, x + 1, y - 17, x - 5, y - 20, x - 6, y - 26, x, y - 23, x + 1, y - 17, x + 4, y - 25, x + 9, y - 27, x + 8, y - 20)
                gem(x + 1, y - 19, jewel, cream, 3)
            }
            Weapon.ROOT_STAFF -> {
                staff(x, y, wood)
                line(x + 1, y - 17, x - 3, y - 23, ink, 4); line(x - 3, y - 23, x + 2, y - 28, ink, 4); line(x + 2, y - 28, x + 6, y - 24, ink, 4)
                line(x + 1, y - 17, x - 3, y - 23, wood, 2); line(x - 3, y - 23, x + 2, y - 28, wood, 2); line(x + 2, y - 28, x + 6, y - 24, wood, 2)
                shape(l.light, x - 4, y - 18, x - 9, y - 21, x - 5, y - 24); shape(l.light, x + 5, y - 28, x + 10, y - 30, x + 8, y - 25)
                shape(l.light, x + 5, y - 22, x + 10, y - 21, x + 7, y - 18)
                gem(x + 1, y - 23, jewel, cream, 2)
            }
            Weapon.SUN_STAFF -> {
                staff(x, y, metal)
                val turn = if (f % 2 == 0) 0.0 else PI / 8
                for (k in 0 until 8) {
                    val a = turn + k * PI / 4
                    line(x + 1 + (cos(a) * 5).roundToInt(), y - 23 + (sin(a) * 5).roundToInt(),
                        x + 1 + (cos(a) * 9).roundToInt(), y - 23 + (sin(a) * 9).roundToInt(), metal, 2)
                }
                disc(x + 1, y - 23, 5, ink); disc(x + 1, y - 23, 4, metalLight); gem(x + 1, y - 23, jewel, cream, 2)
            }
            Weapon.STAR_STAFF -> {
                staff(x, y, l.accent)
                star(x + 1, y - 23, 7, l.accent); rect(x + 1, y - 23, 1, 1, cream)
                if (tier >= 2) { rect(x - 5, y - 28, 1, 1, glow); rect(x + 7, y - 17, 1, 1, glow) }
            }
            Weapon.ICE_STAFF -> {
                val ice = color(0xCDEFF7)
                line(x + 1, y + 13, x + 1, y - 17, ink, 4); line(x + 1, y + 12, x + 1, y - 16, ice, 2)
                shape(color(0xDDF6FF), x + 1, y - 35, x + 6, y - 24, x + 1, y - 15, x - 4, y - 24)
                shape(color(0x9FD6EA), x - 4, y - 23, x - 9, y - 30, x - 3, y - 27)
                shape(color(0x9FD6EA), x + 6, y - 21, x + 11, y - 26, x + 6, y - 26)
                line(x + 1, y - 32, x + 1, y - 19, cream); rect(x - 1, y - 26, 1, 3, cream)
                gem(x + 1, y - 17, jewel, cream, 2)
            }
            Weapon.SNOW_WAND -> {
                line(x, y + 3, x + 3, y - 9, ink, 3); line(x, y + 3, x + 3, y - 9, l.light)
                glint(x + 4, y - 12, cream, 3); line(x + 2, y - 14, x + 6, y - 10, color(0x9FD6EA)); line(x + 2, y - 10, x + 6, y - 14, color(0x9FD6EA))
                rect(x + 4, y - 12, 1, 1, jewel)
            }
            Weapon.STAR_WAND -> {
                line(x, y + 3, x + 3, y - 9, ink, 3); line(x, y + 3, x + 3, y - 9, metal)
                star(x + 4, y - 13, 5, l.accent); rect(x + 4, y - 13, 1, 1, cream)
            }
            Weapon.ORB -> {
                val ox = x + 7; val cy = y - 9 - f % 2
                disc(ox, cy, 6, ink); disc(ox, cy, 5, color(0x7C8FD8)); disc(ox + 1, cy + 1, 3, color(0x5163B0))
                glint(ox, cy, cream, if (f % 2 == 0) 2 else 1); rect(ox - 3, cy - 3, 2, 1, cream)
                rect(x + 3, y - 3, 1, 1, glow); rect(x + 5, y - 2, 1, 1, glow)
            }
            Weapon.FIRE_ORB -> {
                val cy = y - 10; val flick = f % 2
                shape(l.hairTone, x - 4, cy + 4, x - 5, cy - 1, x - 1, cy - 6 - flick, x + 1, cy - 2, x + 3, cy - 9 + flick, x + 6, cy - 1, x + 5, cy + 4)
                poly(l.accent, x - 2, cy + 3, x - 2, cy - 1, x + 1, cy - 4, x + 3, cy - 5 + flick, x + 4, cy + 3)
                rect(x, cy, 2, 2, cream)
            }
            Weapon.BOOK -> {
                shape(l.dark, x - 7, y - 9, x + 8, y - 9, x + 8, y + 1, x - 7, y + 1)
                rect(x - 6, y - 8, 6, 8, cream); rect(x + 1, y - 8, 6, 8, cream); rect(x, y - 9, 1, 10, l.dark)
                for (k in 0..2) rect(x - 5, y - 6 + k * 2, 4, 1, color(0xB9B0A0))
                line(x + 4, y - 2, x + 4, y - 5, color(0x5FA86A)); box(x + 2, y - 7, 4, 4, l.accent)
                if (tier >= 1) rect(x - 7, y - 9, 2, 2, metal)
            }
            Weapon.POTION -> {
                val glass = color(0xD9F0EC)
                box(x, y - 14, 3, 6, glass); rect(x, y - 16, 3, 3, color(0x9A6A44))
                box(x - 3, y - 10, 9, 9, glass); rect(x - 2, y - 6, 7, 4, l.accent); rect(x - 1, y - 9, 2, 2, cream)
                if (cast) { rect(x + 1, y - 19 - f, 1, 1, l.accent); rect(x - 1, y - 22 - f % 2, 1, 1, blend(l.accent, cream)) }
            }
            Weapon.LUTE -> {
                line(23, 40, 36, 23, ink, 5); line(23, 40, 36, 23, l.accent, 3)
                box(18, 32, 12, 14, color(0xA86D44)); box(20, 34, 8, 10, color(0xE6AF72)); rect(24, 36, 3, 4, l.dark)
                line(23, 44, 35, 25, cream)
                if (cast || attack) { glint(39, 20 + f, l.accent, 2); rect(12, 26 - f, 3, 2, l.light); rect(14, 21 - f, 1, 6, l.light) }
            }
            Weapon.PANPIPES -> {
                val cane = color(0xD9C27A)
                for (k in 0..4) box(x - 3 + k * 2, y - 11 + k, 3, 11 - k, cane)
                rect(x - 3, y - 6, 11, 1, l.accent)
                if (cast || attack) { rect(x - 6, y - 16 + f % 2, 2, 2, l.light); rect(x - 5, y - 21 + f % 2, 1, 5, l.light) }
            }
            Weapon.HARP -> {
                line(x - 3, y - 11, x - 1, y + 5, ink, 5); line(x - 3, y - 11, x - 1, y + 5, wood, 3)
                line(x - 3, y - 11, x + 2, y - 16, ink, 4); line(x + 2, y - 16, x + 8, y - 14, ink, 4)
                line(x + 8, y - 14, x + 8, y + 5, ink, 4); line(x - 1, y + 5, x + 8, y + 5, ink, 3)
                line(x - 3, y - 11, x + 2, y - 16, metal, 2); line(x + 2, y - 16, x + 8, y - 14, metal, 2); line(x + 8, y - 14, x + 8, y + 5, metal, 2)
                for (k in 0..3) line(x + k * 2, y - 13 + k / 2, x + k * 2, y + 4, cream)
                gem(x + 2, y - 16, jewel, cream, 2)
                if (cast || attack) { rect(x + 12, y - 18 + f % 2, 2, 2, glow); rect(x + 13, y - 23 + f % 2, 1, 5, glow) }
            }
            Weapon.LANTERN -> {
                line(x, y, x, y - 5, metalDark)
                poly(metalDark, x - 3, y - 13, x + 1, y - 17, x + 5, y - 13); rect(x, y - 19, 2, 2, metal)
                box(x - 3, y - 14, 8, 10, metal); rect(x - 2, y - 12, 6, 6, color(0xFFE38A)); rect(x, y - 10, 2, 2, cream)
                glint(x + 9, y - 10, color(0xFFF3B0), 1 + f % 2)
            }
            Weapon.LANTERN_STAFF -> {
                staff(x, y, wood)
                line(x + 1, y - 17, x + 3, y - 22, ink, 4); line(x + 3, y - 22, x + 9, y - 22, ink, 4); line(x + 9, y - 22, x + 10, y - 18, ink, 4)
                line(x + 1, y - 17, x + 3, y - 22, wood, 2); line(x + 3, y - 22, x + 9, y - 22, wood, 2); line(x + 9, y - 22, x + 10, y - 18, wood, 2)
                line(x + 10, y - 18, x + 10, y - 15, metal)
                box(x + 7, y - 15, 7, 8, l.dark); rect(x + 8, y - 13, 5, 4, l.accent); rect(x + 10, y - 12, 1, 2, cream)
            }
            Weapon.GEAR_STAFF -> {
                staff(x, y, metalDark)
                for (k in 0 until 8) {
                    val a = k * PI / 4
                    rect(x + (cos(a) * 6).roundToInt(), y - 24 + (sin(a) * 6).roundToInt(), 2, 2, metal)
                }
                disc(x + 1, y - 23, 5, ink); disc(x + 1, y - 23, 4, metal); disc(x + 1, y - 23, 2, l.accent); rect(x + 1, y - 23, 1, 1, cream)
            }
            Weapon.WRENCH -> {
                line(x - 1, y + 3, x + 6, y - 6 - ax, ink, 4); line(x - 1, y + 3, x + 6, y - 6 - ax, metal, 2)
                box(x + 3, y - 11 - ax, 10, 4, metal); box(x + 3, y - 16 - ax, 4, 6, metal); box(x + 9, y - 16 - ax, 4, 6, metal)
                rect(x + 4, y - 10 - ax, 8, 1, metalLight)
            }
            Weapon.BLASTER -> {
                val brass = color(0xC9A15A)
                box(x - 3, y - 7, 12, 7, brass); rect(x - 2, y - 6, 9, 2, blend(brass, cream)); rect(x + 2, y - 7, 1, 7, color(0x8C6A3E))
                box(x + 8, y - 8, 5, 9, metalDark); box(x - 1, y - 11, 5, 5, l.accent)
                if (attack && f == 2) { poly(color(0xFFD27A), x + 13, y - 9, x + 19, y - 4, x + 13, y + 1); rect(x + 14, y - 5, 3, 2, cream) }
            }
            Weapon.TELESCOPE -> {
                val brass = color(0xD8AE5C); val band = color(0x7A5530)
                line(x - 2, y + 2, x + 3, y - 4, ink, 5); line(x + 3, y - 4, x + 8, y - 10, ink, 7); line(x + 8, y - 10, x + 12, y - 15, ink, 8)
                line(x - 2, y + 2, x + 3, y - 4, brass, 3); line(x + 3, y - 4, x + 8, y - 10, blend(brass, cream), 5); line(x + 8, y - 10, x + 12, y - 15, brass, 6)
                line(x + 2, y - 5, x + 4, y - 3, band, 2); line(x + 7, y - 11, x + 9, y - 9, band, 2)
                disc(x + 13, y - 16, 2, color(0x9FE3F2)); rect(x + 12, y - 17, 1, 1, cream)
            }
        }
    }

    private fun staff(x: Int, y: Int, shaft: Int) {
        line(x + 1, y + 13, x + 1, y - 18, ink, 4); line(x + 1, y + 12, x + 1, y - 17, shaft, 2)
    }

    private fun flameSword(x: Int, y: Int) {
        val forward = attack && f >= 2
        val tx = x + if (forward) 18 else 12
        val ty = y + if (forward) -12 else -28
        line(x, y - 5, tx, ty, ink, 7); line(x, y - 6, tx, ty + 1, color(0x4A3A3A), 5)
        line(x, y - 7, tx, ty + 3, l.accent, 2); line(x + 1, y - 9, tx, ty + 5, color(0xFFE08A))
        for (k in 1..3) {
            val px = x + (tx - x) * k / 4; val py = (y - 5) + (ty - y + 5) * k / 4
            val flick = (f + k) % 2
            rect(px - 5 - flick, py - 1, 2, 3, l.accent); rect(px - 5 - flick, py - 2, 1, 1, color(0xFFE08A))
            rect(px + 4 + flick, py + 1, 2, 3, l.accent); rect(px + 5 + flick, py, 1, 1, color(0xFFE08A))
        }
        rect(tx - 1, ty - 3 - f % 2, 2, 3, color(0xFFE08A))
        line(x - 6, y - 5, x + 6, y - 5, ink, 4); line(x - 5, y - 5, x + 5, y - 5, metal, 2); rect(x, y - 6, 1, 2, jewel)
        line(x, y + 5, x, y - 3, ink, 3); rect(x - 1, y + 4, 3, 2, metal)
    }

    // ---- Companions ----------------------------------------------------------------------------

    private fun pet() {
        val h = intArrayOf(0, -1, -2, -1)[f % 4]
        when (l.pet) {
            Pet.LEAF_WISP -> {
                val body = color(0x8FD18A)
                shape(color(0x5FA86A), 4, 11 + h, 2, 4 + h, 8, 8 + h)
                shape(body, 0, 15 + h, 2, 11 + h, 8, 10 + h, 11, 13 + h, 11, 18 + h, 8, 21 + h, 2, 21 + h)
                rect(2, 12 + h, 3, 2, color(0xD8F2B0))
                rect(5, 15 + h, 1, 2, ink); rect(8, 15 + h, 1, 2, ink); rect(3, 18 + h, 1, 1, color(0xF2A0A0))
                if (tier >= 2) rect(12, 9 + h, 1, 1, glow)
            }
            Pet.SPIRIT -> {
                val pale = color(0xDDF5E6)
                shape(pale, 1, 21 + h, 0, 14 + h, 3, 10 + h, 8, 10 + h, 11, 14 + h, 10, 21 + h, 8, 19 + h, 6, 22 + h, 4, 19 + h)
                rect(2, 12 + h, 2, 3, cream)
                rect(4, 15 + h, 1, 2, ink); rect(7, 15 + h, 1, 2, ink); rect(6, 13 + h, 1, 1, l.accent)
            }
            Pet.STAR -> {
                star(6, 14 + h, 6, l.accent); rect(4, 14 + h, 1, 1, ink); rect(7, 14 + h, 1, 1, ink)
                if (f % 2 == 0) glint(12, 8 + h, cream, 1)
            }
            Pet.GOLEM -> {
                box(1, 48, 4, 5, metalDark); box(7, 48, 4, 5, metalDark)
                box(0, 40, 12, 10, metal); rect(1, 41, 3, 7, metalLight); rect(5, 43, 2, 2, jewel)
                box(2, 33, 8, 8, metal); rect(5, 36, 3, if (f == 3) 1 else 2, l.accent); rect(3, 34, 2, 1, metalLight)
                rect(4, 30, 2, 3, metalDark); if (f % 2 == 0) rect(4, 27, 2, 2, color(0xE4E2DA))
            }
            Pet.NONE -> Unit
        }
    }

    private companion object {
        val STRIDE = intArrayOf(0, 2, 3, 0, -2, -3)
        /** Iron for common heroes, silver for rare and epic, gold from legendary up: mid, light, dark. */
        val METALS = arrayOf(intArrayOf(0x8E989C, 0xC4CCCE, 0x5B6569), intArrayOf(0xB9C6D2, 0xF0F5FA, 0x6F7F92),
            intArrayOf(0xE9B23C, 0xFFE7A0, 0x9C6A1E))
        val PINWHEEL_STRAIGHT = intArrayOf(0, -5, 3, -3, 5, 0, 3, 3, 0, 5, -3, 3, -5, 0, -3, -3)
        val PINWHEEL_TILTED = intArrayOf(4, -4, 4, -1, 4, 4, 1, 4, -4, 4, -4, 1, -4, -4, -1, -4)
        val HELMETS = setOf(Headgear.HELM, Headgear.CREST_HELM, Headgear.GREAT_HELM, Headgear.HORNED_HELM)
    }
}
