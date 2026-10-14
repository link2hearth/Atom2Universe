package com.Atom2Universe.app.games.hamlet

import android.graphics.Canvas
import com.Atom2Universe.app.games.hamlet.HamletCharacterArt.Pose

/**
 * Regional bestiary. Battles keep their six enemy kinds (role, faction and numbers come from the kind);
 * each region only dresses those kinds in its own creatures, and gives its bosses their own face.
 */
object HamletEnemies {
    const val REGIONS = 6
    const val KINDS = 6
    private const val BOSSES = REGIONS * KINDS
    const val FERN_DRAKE = BOSSES + REGIONS
    const val COUNT = FERN_DRAKE + 1
    /** Allied summons are moss sprites, whatever the battlefield. */
    const val SUMMON = 0

    /** Art for an enemy of [kind] (0–5 ordinary, 6 and 7 bosses) met in [region]. */
    fun visual(kind: Int, region: Int): Int {
        val r = Math.floorMod(region, REGIONS)
        return if (kind >= KINDS) BOSSES + r else r * KINDS + Math.floorMod(kind, KINDS)
    }
    /** Moss sprite, bramble boar and fern drake keep their faces in every region. */
    fun pet(pet: Int) = intArrayOf(0, 5, FERN_DRAKE)[pet]
    fun isBoss(id: Int) = id in BOSSES until FERN_DRAKE
}

/** Enemy frames; only used while constructing cached frames, never in a drawing loop. */
internal class EnemyPainter(c: Canvas) : PixelPen(c) {
    private var pose = Pose.IDLE
    private var f = 0
    private var motion = 0
    private var bob = 0
    private var hover = 0
    private var flap = 0
    private var blink = false
    private var attack = false
    private var cast = false
    private val wood = color(0x8A603F)
    private val gold = color(0xF2C14E)
    private val glowYellow = color(0xF6E27A)

    private fun lit(col: Int) = blend(col, cream)
    private fun dim(col: Int) = blend(col, ink)
    /** Box with a lit top edge and a shaded right side: the shared three-tone volume. */
    private fun mass(x: Int, y: Int, w: Int, h: Int, col: Int) {
        box(x, y, w, h, col); rect(x + 2, y + 1, w - 5, 2, lit(col)); rect(x + w - 3, y + 3, 2, h - 5, dim(col))
    }
    private fun eyes(x: Int, y: Int, closed: Boolean, distance: Int) {
        rect(x, y, 3, if (closed) 1 else 4, ink); rect(x + distance, y, 2, if (closed) 1 else 3, ink)
        if (!closed) { rect(x + 1, y, 1, 1, cream); rect(x + distance, y, 1, 1, cream) }
    }

    fun paint(id: Int, pose: Pose, frame: Int) {
        this.pose = pose; f = frame
        motion = when (pose) { Pose.WALK -> intArrayOf(0, 2, 3, 0, -2, -3)[f]; Pose.ATTACK -> intArrayOf(-2, 0, 4, 1)[f]; else -> 0 }
        bob = when (pose) { Pose.IDLE, Pose.CAST -> f % 2; Pose.WALK -> kotlin.math.abs(motion) / 2; else -> 0 }
        hover = intArrayOf(0, -1, -2, -1)[f % 4]
        flap = intArrayOf(-5, 0, 5, 0)[f % 4]
        blink = pose == Pose.HURT || pose == Pose.DOWN || (pose == Pose.IDLE && f == 3)
        attack = pose == Pose.ATTACK; cast = pose == Pose.CAST
        c.save(); c.translate((if (attack) motion else 0).toFloat(), -bob.toFloat())
        when (id) {
            0 -> mossSprite(); 1 -> magpie(); 2 -> brigand(); 3 -> scarecrow(); 4 -> ogre(true); 5 -> boar()
            6 -> mushroom(); 7 -> ghostOwl(); 8 -> fox(); 9 -> stump(); 10 -> mossBear(); 11 -> mistWolf()
            12 -> hedgehog(); 13 -> leafSpirit(); 14 -> squirrel(); 15 -> quarryGolem(); 16 -> tortoise(); 17 -> stag()
            18 -> hauntedArmor(); 19 -> bat(); 20 -> shade(); 21 -> statue(); 22 -> emberGolem(); 23 -> mimic()
            24 -> toad(); 25 -> fireflyJelly(); 26 -> waterSnake(); 27 -> sludge(); 28 -> croc(); 29 -> mantis()
            30 -> ram(); 31 -> stormEagle(); 32 -> lynx(); 33 -> yeti(); 34 -> ogre(false); 35 -> griffon()
            36 -> bull(); 37 -> treant(); 38 -> amberSnail(); 39 -> dragon(false); 40 -> hydra(); 41 -> dragon(true)
            else -> fernDrake()
        }
        if (pose == Pose.HURT) { line(38, 12, 42, 8, cream, 2); line(39, 17, 45, 16, cream, 2) }
        c.restore()
    }

    /** Fliers hover above the ground line, with their own slow bob. */
    private fun lift(by: Int) { c.translate(0f, (by + hover).toFloat()) }

    // ---- Sentier des prairies ------------------------------------------------------------------

    private fun mossSprite() {
        val leaf = color(0x71AF75); val leafLight = color(0xBBDD85)
        val squish = if (attack || pose == Pose.WALK) f % 2 * 3 else bob
        box(11 - motion / 2, 46, 9, 6, color(0x3F7263)); box(28 + motion / 2, 46, 10, 6, color(0x3F7263))
        box(10 - squish, 30 + squish, 27 + squish * 2, 20 - squish, color(0x448875))
        box(13, 27 + squish, 23, 19 - squish, leaf); rect(16, 30 + squish, 8, 3, leafLight)
        eyes(23, 38 + squish, blink, 8); rect(28, 45, 3, 1, ink)
        line(23, 29 + squish, 24 + bob, 20, color(0x79593A), 2)
        poly(leaf, 24, 23, 15, 17 - bob, 16, 13 - bob, 23, 15, 25, 21, 29, 14, 35, 13, 33, 20)
        rect(12, 42, 3, 3, leafLight)
    }

    private fun magpie() {
        lift(-5)
        val black = color(0x2A2E3A); val sheen = color(0x3F6FB0); val white = color(0xF2F0E6)
        shape(dim(black), 18, 25, 10, 12 + flap, 5, 15 + flap, 13, 29)
        shape(black, 3, 33, 15, 28, 17, 32, 5, 37); line(5, 34, 14, 30, sheen)
        box(13, 21, 19, 16, black); box(17, 27, 13, 10, white); rect(15, 23, 3, 6, sheen)
        shape(black, 19, 26, 21, 11 + flap, 28, 8 + flap, 28, 25); line(22, 24, 26, 12 + flap, sheen); rect(24, 19 + flap / 2, 2, 4, white)
        box(27, 14, 12, 11, black); rect(33, 17, 3, 3, white); rect(34, 18, 1, if (blink) 1 else 2, ink)
        shape(color(0x4A4F5C), 38, 18, 45, 20, 38, 22)
        line(20, 37, 19, 43, ink); line(25, 37, 26, 43, ink)
        disc(23, 45, 2, gold); rect(23, 45, 1, 1, ink)
        if (cast) glint(42, 11 + f, glowYellow, 2)
    }

    private fun brigand() {
        box(16 - motion / 2, 39, 6, 13, color(0x62494A)); box(27 + motion / 2, 39, 6, 13, color(0x62494A))
        poly(ink, 16, 24, 30, 26, 36, 45, 24, 42, 13, 46); poly(color(0xAA604B), 17, 26, 29, 28, 33, 42, 23, 39, 16, 42)
        box(17, 16, 17, 16, color(0x955F44)); poly(color(0xD89759), 16, 19, 18, 12, 27, 10, 34, 17)
        rect(19, 21, 16, 6, ink); eyes(24, 22, blink, 7)
        line(16, 30, 12, 40 - motion, ink, 5); line(30, 31, 37, 34 + motion, ink, 5)
        line(37, 34 + motion, 42, 24 + motion, color(0xC6D2C4), 3); line(11, 39 - motion, 7, 47 - motion, color(0xC6D2C4), 3)
        rect(19, 36, 13, 3, color(0xE0B875))
    }

    private fun scarecrow() {
        val straw = color(0xE2C46E); val coat = color(0x5A7FA8); val sack = color(0xD8C08A); val red = color(0xC8463C)
        line(24, 36, 24, 53, ink, 4); line(24, 37, 24, 53, wood, 2)
        line(6, 29, 42, 29, ink, 5); line(7, 29, 41, 29, wood, 3)
        poly(straw, 7, 26, 0, 30 + f % 2, 7, 33); poly(straw, 41, 26, 48, 30 - f % 2, 41, 33)
        shape(coat, 13, 25, 35, 25, 37, 44, 31, 41, 27, 46, 21, 41, 15, 45, 11, 43)
        rect(16, 28, 3, 12, lit(coat)); rect(30, 28, 3, 10, dim(coat))
        box(25, 32, 7, 7, red); rect(26, 33, 1, 1, cream); rect(30, 37, 1, 1, cream)
        rect(13, 37, 23, 2, straw)
        poly(straw, 17, 42, 19, 49, 22, 43); poly(straw, 27, 44, 29, 51, 31, 44)
        box(16, 9, 17, 16, sack); rect(18, 11, 4, 12, lit(sack))
        box(24, 13, 5, 5, ink); box(29, 13, 4, 5, ink)
        if (cast || attack) { rect(25, 14, 3, 3, color(0xFF9A3A)); rect(30, 14, 2, 3, color(0xFF9A3A)) } else rect(25, 14, 1, 1, cream)
        line(22, 21, 32, 21, ink); for (k in 0..3) rect(23 + k * 3, 20, 1, 3, ink)
        poly(straw, 15, 13, 12, 19, 18, 15)
        shape(color(0x6B4A35), 11, 12, 37, 12, 31, 9, 27, -1 - f % 2, 21, 8)
        rect(17, 9, 15, 2, red)
        box(4, 21, 6, 6, color(0x2A2E3A)); poly(color(0x4A4F5C), 9, 23, 12, 24, 9, 25); rect(7, 23, 1, 1, cream)
    }

    private fun boar() {
        val leaf = color(0x71AF75)
        for (j in 0..3) box(11 + j * 7, 43, 5, 9 - (j % 2 * 2 - 1) * motion / 2, color(0x5F4E3E))
        box(7, 29, 31, 19, color(0x96734F)); rect(10, 31, 22, 4, color(0xC59A65))
        poly(leaf, 7, 30, 13, 18, 18, 25, 23, 18, 27, 25, 33, 20, 35, 33)
        box(29, 31, 13, 15, color(0xBB9569)); box(37, 37, 9, 8, color(0xD4AF88)); rect(41, 39, 2, 3, ink)
        eyes(33, 34, blink, 6); poly(color(0xD1A975), 30, 32, 30 - bob, 22, 36, 27); poly(cream, 38, 45, 35, 46, 34, 41, 36, 42)
        line(8, 37, 3, 33 - bob, color(0x6E583F), 2)
    }

    /** Rock sentinel of the meadow walls, or the frost ogre of the high plateaus. */
    private fun ogre(rock: Boolean) {
        val leaf = color(0x71AF75); val leafLight = color(0xBBDD85)
        val stone = if (rock) color(0xA58C6B) else color(0x6797AE)
        val light = if (rock) color(0xD4BB8A) else color(0xB1D6DE)
        val dark = if (rock) color(0x736953) else color(0x3D6B89)
        box(11 - motion / 2, 41, 11, 12, dark); box(27 + motion / 2, 41, 11, 12, stone)
        box(10, 26, 29, 21, stone); rect(15, 28, 20, 4, light); rect(28, 35, 9, 10, dark)
        box(6, 28 - motion, 10, 17, dark); box(34, 27 + motion, 10, 17, stone); rect(35, 29 + motion, 7, 5, light)
        box(15, 12, 23, 20, stone); rect(17, 14, 17, 5, light); rect(18, 22, 19, 4, dark); eyes(24, 22, blink, 9)
        if (rock) { gem(25, 36, leaf, leafLight, 4); line(20, 15, 21, 20, dark); line(20, 34, 17, 39, dark, 2); rect(10, 27, 8, 3, leaf) }
        else { poly(light, 16, 16, 10, 7, 13, 5, 21, 13); poly(light, 32, 13, 39, 6, 42, 8, 36, 18); rect(25, 29, 2, 4, cream); rect(33, 29, 2, 4, cream); box(7, 43, 8, 8, dark) }
    }

    private fun bull() {
        val hide = color(0x6E4430); val hoof = color(0x2E2524); val bone = color(0xEDE3C8); val muzzle = color(0xC99A7E)
        val step = motion / 2
        line(4, 25, -3, 36, ink, 3); line(4, 25, -3, 36, hide); shape(dim(hide), -6, 35, -1, 34, -3, 41)
        box(6 - step, 40, 7, 13, dim(hide)); box(26 + step, 40, 7, 13, dim(hide))
        mass(1, 19, 38, 25, hide); rect(6, 39, 26, 3, lit(hide))
        box(11 + step, 41, 7, 12, hide); box(31 - step, 41, 7, 12, hide)
        for (x in intArrayOf(6 - step, 26 + step, 11 + step, 31 - step)) rect(x + 1, 50, 5, 2, hoof)
        shape(hide, 24, 20, 29, 12, 38, 14, 41, 24)
        mass(33, 17, 16, 18, hide)
        box(41, 26, 11, 10, muzzle); rect(46, 28, 1, 2, ink); rect(49, 28, 1, 2, ink)
        rect(45, 35, 6, 1, gold); rect(44, 33, 1, 3, gold); rect(51, 33, 1, 3, gold)
        line(38, 20, 44, 22, ink, 2); rect(40, 22, 4, 2, ink); rect(42, 22, 1, 1, color(0xFF5A3A))
        shape(bone, 35, 18, 29, 12, 28, 4, 32, 7, 38, 16)
        shape(bone, 45, 18, 51, 11, 53, 3, 55, 10, 48, 21)
        val petals = intArrayOf(color(0xF28FA8), cream, color(0xF6D86A))
        for (k in 0..4) { box(30 + k, 22 + k * 4, 4, 4, petals[k % 3]); rect(31 + k, 23 + k * 4, 2, 2, color(0xF6D86A)) }
        if (attack || cast) { rect(53, 29 - f % 2, 3, 2, cream); rect(56, 26 - f % 2, 2, 2, cream) }
    }

    // ---- Bois murmurant ------------------------------------------------------------------------

    private fun mushroom() {
        val cap = color(0xC8463C); val stem = color(0xEAD9BC)
        val squash = if (attack || pose == Pose.WALK) (f % 2) * 2 else 0
        box(15 - motion / 2, 46, 7, 6, dim(stem)); box(26 + motion / 2, 46, 7, 6, dim(stem))
        mass(14, 30 + squash, 20, 17 - squash, stem)
        eyes(21, 36 + squash, blink, 7)
        rect(19, 41 + squash, 2, 1, color(0xF2A0A0)); rect(31, 41 + squash, 2, 1, color(0xF2A0A0)); rect(25, 42 + squash, 3, 1, ink)
        shape(cap, 6, 32 + squash, 9, 22 + squash, 16, 15 + squash, 24, 13 + squash, 32, 15 + squash, 39, 22 + squash, 42, 32 + squash)
        rect(8, 31 + squash, 33, 2, dim(cap))
        disc(15, 22 + squash, 2, cream); disc(25, 17 + squash, 2, cream); disc(34, 24 + squash, 2, cream); rect(20, 27 + squash, 2, 2, cream)
        if (cast) { rect(9 + f * 3, 10 - f, 2, 2, color(0xE8E0A0)); rect(34 - f * 2, 8 - f, 2, 2, color(0xE8E0A0)) }
    }

    private fun ghostOwl() {
        lift(-4)
        val pale = color(0xB8CBE0); val deep = color(0x7C8FB8)
        poly(lit(pale), 16, 40, 18, 48 + f % 2, 22, 42, 26, 50 - f % 2, 30, 42)
        shape(deep, 13, 24, 3, 30 + flap / 2, 5, 39 + flap / 2, 15, 38)
        shape(deep, 33, 24, 43, 30 + flap / 2, 41, 39 + flap / 2, 31, 38)
        mass(13, 18, 22, 24, pale); rect(18, 31, 12, 9, lit(pale))
        for (k in 0..2) rect(19 + k * 4, 33 + (k % 2) * 2, 2, 1, deep)
        poly(deep, 14, 20, 12, 11, 18, 18); poly(deep, 34, 20, 36, 11, 30, 18)
        disc(19, 25, 4, lit(pale)); disc(29, 25, 4, lit(pale))
        if (blink) { rect(17, 25, 5, 1, ink); rect(27, 25, 5, 1, ink) }
        else { disc(19, 25, 2, ink); disc(29, 25, 2, ink); rect(19, 25, 1, 1, glowYellow); rect(29, 25, 1, 1, glowYellow) }
        poly(color(0xD9A050), 23, 28, 26, 28, 24, 32)
        if (cast) { glint(7, 14 + f, glowYellow, 1); glint(41, 12 + f, glowYellow, 1); glint(24, 8 - f % 2, cream, 2) }
    }

    private fun fox() {
        val fur = color(0xD9762E); val white = color(0xF4EBDD); val dark = color(0x3A2A26); val scarf = color(0x4E79C4)
        shape(fur, 18, 36, 7, 33, 1, 24 + f % 2, 6, 25 + f % 2, 13, 29, 19, 31)
        poly(white, 1, 24 + f % 2, 4, 22 + f % 2, 6, 26 + f % 2)
        box(18 - motion / 2, 40, 6, 12, dark); box(25 + motion / 2, 40, 6, 12, dark)
        mass(17, 27, 14, 15, fur); rect(22, 30, 6, 10, white)
        rect(17, 26, 15, 3, scarf); line(18, 28, 11, 34 - f % 2, scarf, 3)
        line(29, 31, 35, 34 + motion, ink, 4); line(29, 31, 35, 34 + motion, fur, 2)
        line(35, 33 + motion, 41, 27 + motion, ink, 3); line(35, 33 + motion, 41, 27 + motion, color(0xD8E2EE))
        mass(19, 12, 14, 14, fur)
        shape(fur, 31, 17, 40, 21, 31, 25); rect(38, 20, 2, 2, ink)
        poly(white, 26, 21, 33, 22, 30, 26, 24, 25)
        shape(fur, 20, 13, 21, 4, 25, 12); shape(fur, 26, 12, 30, 4, 31, 13); rect(22, 8, 1, 3, dark); rect(28, 8, 1, 3, dark)
        rect(24, 16, 9, 3, dark); rect(28, 17, if (blink) 1 else 2, 1, cream)
    }

    private fun stump() {
        val bark = color(0x7A5A3E); val ring = color(0xD9B98A); val moss = color(0x6FAE5E)
        poly(dim(bark), 8 - motion / 2, 53, 12, 44, 16, 53); poly(dim(bark), 32 + motion / 2, 53, 36, 44, 41, 53)
        mass(10, 20, 28, 32, bark)
        line(15, 26, 15, 46, dim(bark)); line(32, 24, 32, 44, dim(bark)); line(22, 38, 22, 49, dim(bark))
        shape(ring, 10, 21, 13, 16, 35, 16, 38, 21); rect(16, 18, 16, 1, dim(ring)); rect(21, 19, 6, 1, dim(ring))
        poly(moss, 9, 23, 12, 16, 18, 19, 16, 24)
        box(18, 27, 6, 5, ink); box(27, 27, 6, 5, ink)
        rect(19, 28, if (blink) 4 else 3, if (blink) 1 else 2, color(0xF2B033)); rect(28, 28, if (blink) 4 else 3, if (blink) 1 else 2, color(0xF2B033))
        line(17, 25, 23, 27, dim(bark), 2); line(34, 25, 28, 27, dim(bark), 2)
        box(21, 35, 10, 5, ink); rect(22, 35, 2, 1, cream); rect(28, 35, 2, 1, cream)
        line(37, 31, 44, 25 + motion / 2, ink, 4); line(37, 31, 44, 25 + motion / 2, bark, 2)
        poly(moss, 44, 25 + motion / 2, 48, 20 + motion / 2, 46, 27 + motion / 2)
        box(7, 35, 5, 3, color(0xC8463C)); rect(9, 38, 1, 2, color(0xEAD9BC))
    }

    private fun mossBear() {
        val fur = color(0x6B4A35); val moss = color(0x5FA86A); val muzzle = color(0xC9A27E)
        box(13 - motion / 2, 42, 9, 11, dim(fur)); box(27 + motion / 2, 42, 9, 11, fur)
        mass(9, 22, 30, 24, fur); shape(lit(fur), 18, 31, 31, 31, 29, 44, 20, 44)
        shape(moss, 8, 31, 10, 21, 22, 17, 34, 20, 37, 26, 30, 25, 20, 28)
        poly(lit(moss), 12, 22, 10, 13, 16, 20); poly(lit(moss), 21, 19, 21, 10, 25, 18)
        box(15, 18, 4, 3, color(0xC8463C))
        box(34, 28 + motion / 2, 8, 13, fur); for (k in 0..2) rect(35 + k * 2, 40 + motion / 2, 1, 3, cream)
        mass(26, 9, 17, 15, fur)
        box(25, 6, 6, 6, fur); box(37, 6, 6, 6, fur); rect(27, 8, 2, 2, dim(fur)); rect(39, 8, 2, 2, dim(fur))
        box(36, 16, 9, 7, muzzle); rect(42, 17, 3, 2, ink); rect(39, 21, 3, 1, ink)
        rect(33, 13, 2, if (blink) 1 else 2, ink)
    }

    private fun mistWolf() {
        val fur = color(0x8A98A8); val dark = color(0x56627A); val mist = color(0xD8E2EE)
        shape(fur, 7, 29, -1, 23, -6, 27, -2, 33, 6, 35); rect(-5, 20 + f % 2, 3, 2, mist); rect(-8, 25 - f % 2, 3, 2, mist)
        box(9 - motion / 2, 36, 5, 16, dark); box(29 + motion / 2, 38, 5, 14, dark)
        shape(fur, 4, 31, 9, 25, 29, 22, 38, 25, 39, 41, 30, 39, 13, 37, 5, 39)
        rect(10, 27, 16, 2, lit(fur)); rect(33, 30, 3, 9, dim(fur))
        poly(lit(fur), 25, 24, 29, 18, 33, 22, 37, 19, 40, 26)
        box(13 + motion / 2, 37, 5, 15, fur); box(33 - motion / 2, 39, 5, 13, fur)
        mass(31, 15, 14, 13, fur)
        shape(fur, 42, 20, 50, 23, 49, 27, 42, 28); rect(48, 22, 2, 2, ink)
        shape(fur, 32, 16, 33, 7, 37, 14); shape(fur, 37, 15, 40, 7, 42, 15)
        rect(38, 19, 3, if (blink) 1 else 2, color(0x9FE3F2))
        if (attack) { rect(44, 27, 1, 3, cream); rect(47, 27, 1, 3, cream) }
        rect(3, 50 - f % 2, 7, 2, mist); rect(34, 51, 8, 2, mist); rect(18, 52, 6, 1, mist)
    }

    private fun treant() {
        val bark = color(0x6B4E36); val leaf = color(0x4E8F4A); val leafLight = color(0x8FC46E); val glowGreen = color(0xC8F27A)
        disc(6, 6, 7, ink); disc(21, -2, 8, ink); disc(37, 1, 8, ink); disc(46, 11, 6, ink)
        disc(6, 6, 6, leaf); disc(21, -2, 7, leaf); disc(37, 1, 7, leaf); disc(46, 11, 5, leaf)
        shape(leaf, -2, 14, 2, 4, 14, -2, 30, -4, 44, 4, 50, 14, 40, 18, 8, 18)
        disc(19, -5, 3, leafLight); disc(34, -2, 2, leafLight); disc(5, 4, 2, leafLight)
        shape(dim(bark), 8 - motion / 2, 53, 12, 40, 18, 40, 16, 53); shape(bark, 28 + motion / 2, 53, 30, 40, 36, 40, 40, 53)
        mass(10, 12, 28, 34, bark)
        line(14, 16, 14, 42, dim(bark)); line(34, 14, 34, 40, dim(bark))
        line(11, 24, 1, 34 + motion / 2, ink, 6); line(11, 24, 1, 34 + motion / 2, bark, 4); line(1, 34 + motion / 2, -4, 39 + motion / 2, bark, 2)
        line(37, 22, 48, 30 + motion, ink, 6); line(37, 22, 48, 30 + motion, bark, 4); line(48, 30 + motion, 53, 25 + motion, bark, 2)
        poly(leafLight, 53, 25 + motion, 57, 22 + motion, 55, 27 + motion)
        box(16, 21, 7, 6, ink); box(27, 21, 7, 6, ink)
        rect(17, 22, 5, if (blink) 1 else 4, glowGreen); rect(28, 22, 5, if (blink) 1 else 4, glowGreen)
        line(15, 19, 23, 22, dim(bark), 2); line(35, 19, 27, 22, dim(bark), 2)
        box(19, 30, 12, 6, ink); rect(20, 31, 10, 1, dim(bark))
        poly(leaf, 16, 36, 34, 36, 31, 47, 26, 42, 21, 48)
        rect(14, 41, 1, 4, glowGreen); rect(33, 39, 1, 3, glowGreen)
        box(35, 41, 5, 3, color(0xC8463C)); rect(37, 44, 1, 2, color(0xEAD9BC))
    }

    // ---- Vallée ambrée -------------------------------------------------------------------------

    private fun hedgehog() {
        val spine = color(0x8A5A32); val face = color(0xE8C9A0)
        box(16 - motion / 2, 46, 6, 6, dim(face)); box(28 + motion / 2, 46, 6, 6, dim(face))
        val pts = IntArray(54)
        for (i in 0..24) {
            val a = Math.PI + i * Math.PI / 24
            val r = if (i % 2 == 0) 21.0 else 14.0
            pts[i * 2] = 21 + (Math.cos(a) * r).toInt(); pts[i * 2 + 1] = 42 + (Math.sin(a) * r * .95).toInt()
        }
        pts[50] = 40; pts[51] = 48; pts[52] = 2; pts[53] = 48
        shape(spine, *pts)
        for (i in 0..24 step 2) rect(pts[i * 2], pts[i * 2 + 1], 1, 1, cream)
        disc(19, 43, 6, dim(spine))
        for (k in 0..4) line(9 + k * 5, 36 + (k % 2) * 3, 11 + k * 5, 31 + (k % 2) * 3, lit(spine))
        shape(face, 30, 32, 43, 39, 41, 48, 28, 48); rect(42, 39, 2, 2, ink)
        rect(34, 36, 2, if (blink) 1 else 3, ink); rect(35, 42, 2, 1, color(0xF2A0A0))
        disc(12, 28, 2, color(0x6B3A1E)); rect(11, 27, 1, 1, cream); disc(27, 24, 2, color(0x6B3A1E))
        poly(color(0xE0703A), 19, 23, 15, 16, 23, 18)
    }

    private fun leafSpirit() {
        lift(-6)
        val leaf = color(0xE0703A); val vein = color(0xB04A2A); val glowAmber = color(0xFFD27A)
        shape(leaf, 24, 10, 27, 18, 34, 14, 32, 22, 41, 24, 33, 29, 36, 36, 27, 33, 24, 42, 21, 33, 12, 36, 15, 29, 7, 24, 16, 22, 14, 14, 21, 18)
        line(24, 14, 24, 40, vein); line(24, 28, 33, 20, vein); line(24, 28, 15, 20, vein)
        line(24, 42, 22, 48 + f % 2, ink, 2)
        rect(20, 24, 2, if (blink) 1 else 3, ink); rect(27, 24, 2, if (blink) 1 else 3, ink); rect(23, 29, 3, 1, ink)
        glint(8 + f * 2, 14, glowAmber, 1); glint(40 - f * 2, 38, glowAmber, 1)
        if (cast) glint(24, 5 - f % 2, cream, 2)
    }

    private fun squirrel() {
        val fur = color(0xB0642E); val belly = color(0xEAD2B0); val cap = color(0x7A5530)
        shape(fur, 18, 40, 8, 38, 3, 28, 5, 16, 12, 9, 19, 13, 14, 21, 15, 30, 20, 34)
        line(7, 30, 9, 16, lit(fur), 2)
        box(19 - motion / 2, 43, 6, 9, dim(fur)); box(26 + motion / 2, 43, 6, 9, fur)
        mass(18, 28, 14, 16, fur); rect(24, 31, 6, 11, belly)
        mass(22, 15, 14, 14, fur)
        shape(fur, 24, 17, 24, 9, 28, 15); rect(31, 23, 4, 3, belly); rect(35, 21, 1, 1, ink)
        rect(30, 19, 2, if (blink) 1 else 3, ink); if (!blink) rect(30, 19, 1, 1, cream)
        shape(cap, 21, 17, 23, 12, 30, 11, 36, 15, 36, 17); rect(24, 13, 1, 1, lit(cap)); rect(29, 12, 1, 1, lit(cap)); rect(28, 9, 2, 3, cap)
        line(30, 32, 36, 30 + motion, ink, 4); line(30, 32, 36, 30 + motion, fur, 2)
        disc(38, 28 + motion, 2, color(0x9A6A44)); rect(37, 25 + motion, 4, 2, cap)
    }

    private fun quarryGolem() {
        val stone = color(0xB89A78); val seam = dim(stone); val amber = color(0xF2A033)
        box(12 - motion / 2, 42, 10, 11, seam); box(27 + motion / 2, 42, 10, 11, stone)
        mass(10, 24, 28, 19, stone)
        rect(11, 33, 26, 1, seam); rect(22, 25, 1, 8, seam); rect(16, 34, 1, 8, seam); rect(30, 34, 1, 8, seam)
        gem(24, 37, amber, cream, 4)
        shape(amber, 29, 25, 32, 14, 35, 18, 38, 25); line(32, 17, 33, 23, cream)
        shape(amber, 10, 25, 8, 16, 14, 22)
        box(3, 26 - motion, 8, 16, seam); box(38, 25 + motion, 9, 16, stone); rect(39, 26 + motion, 3, 14, lit(stone))
        mass(17, 12, 15, 13, stone)
        rect(21, 17, 9, 3, ink); rect(24, 18, 2, if (blink) 1 else 2, amber); rect(28, 18, 1, if (blink) 1 else 2, amber)
    }

    private fun tortoise() {
        val skin = color(0x8FA06A); val shell = color(0x9C9488); val mortar = color(0x6E675F)
        box(8 - motion / 2, 42, 7, 10, dim(skin)); box(30 + motion / 2, 42, 7, 10, dim(skin))
        line(24, 13, 24, 4, wood, 2); poly(color(0xC8463C), 25, 4, 31, 6 + f % 2, 25, 9)
        box(14, 14, 5, 6, shell); box(22, 12, 5, 6, shell); box(30, 14, 5, 6, shell)
        shape(shell, 3, 44, 5, 30, 12, 21, 26, 18, 38, 22, 43, 32, 44, 44)
        rect(6, 30, 37, 1, mortar); rect(4, 37, 40, 1, mortar)
        for (x in intArrayOf(14, 26, 36)) rect(x, 22, 1, 8, mortar)
        for (x in intArrayOf(9, 20, 32)) rect(x, 31, 1, 6, mortar)
        for (x in intArrayOf(15, 27, 39)) rect(x, 38, 1, 6, mortar)
        rect(8, 24, 4, 2, lit(shell)); rect(18, 32, 5, 2, lit(shell)); poly(color(0xE0703A), 12, 21, 9, 17, 15, 18)
        box(12 + motion / 2, 43, 7, 10, skin); box(34 - motion / 2, 43, 7, 10, skin)
        mass(40, 31, 11, 10, skin); rect(46, 34, 2, if (blink) 1 else 2, ink); rect(46, 38, 4, 1, dim(skin))
    }

    private fun stag() {
        val fur = color(0xA8603A); val antler = color(0xE6D2A8)
        box(9 - motion / 2, 37, 4, 15, dim(fur)); box(27 + motion / 2, 37, 4, 15, dim(fur))
        mass(6, 22, 28, 17, fur); rect(6, 27, 4, 8, cream)
        box(13 + motion / 2, 38, 4, 14, fur); box(31 - motion / 2, 38, 4, 14, fur)
        shape(fur, 26, 26, 30, 13, 36, 13, 35, 28)
        mass(31, 9, 12, 11, fur); box(40, 13, 8, 6, lit(fur)); rect(46, 14, 2, 2, ink)
        rect(37, 12, 2, if (blink) 1 else 2, ink)
        poly(fur, 31, 11, 28, 7, 33, 10)
        line(35, 9, 30, 1, antler, 2); line(32, 4, 27, 3, antler); line(31, 2, 31, -4, antler)
        line(38, 9, 42, 0, antler, 2); line(40, 4, 45, 3, antler); line(41, 2, 42, -4, antler)
        rect(26, 2, 2, 2, color(0xE0703A)); rect(45, 2, 2, 2, color(0xC8463C)); rect(30, -4, 2, 2, color(0xF2B033))
    }

    private fun amberSnail() {
        val foot = color(0x9C8A6A); val shell = color(0xC0703A); val shellLight = color(0xE8A060); val shellDark = color(0x7A3E22)
        val amber = color(0xF2B033)
        shape(foot, -6, 53, -2, 44 + bob, 40, 42 + bob, 50, 46 + bob, 50, 53); rect(0, 45 + bob, 42, 2, lit(foot))
        disc(18, 26, 18, ink); disc(18, 26, 17, shell); disc(17, 24, 13, shellLight)
        var px = 18 + 14; var py = 26
        for (i in 1..44) {
            val a = i * .32; val rad = 14.0 - i * .3
            val nx = 18 + (Math.cos(a) * rad).toInt(); val ny = 26 + (Math.sin(a) * rad).toInt()
            line(px, py, nx, ny, shellDark, 2); px = nx; py = ny
        }
        rect(9, 15, 4, 2, cream)
        shape(amber, 5, 12, 7, -3, 13, 9); shape(amber, 11, 9, 17, -9, 22, 7); shape(amber, 22, 9, 27, -4, 30, 11)
        line(16, -6, 17, 4, cream); line(7, 0, 8, 8, cream)
        line(38, 43, 40, 27 - bob, ink, 5); line(44, 43, 47, 25 + bob, ink, 5)
        line(38, 43, 40, 27 - bob, foot, 3); line(44, 43, 47, 25 + bob, foot, 3)
        box(36, 21 - bob, 8, 8, cream); box(43, 19 + bob, 8, 8, cream)
        rect(40, 24 - bob, 2, if (blink) 1 else 3, ink); rect(47, 22 + bob, 2, if (blink) 1 else 3, ink)
        line(36, 20 - bob, 43, 23 - bob, ink, 2); line(43, 19 + bob, 50, 21 + bob, ink, 2)
        rect(42, 40, 6, 2, ink)
        if (cast) { glint(5 + f * 5, -6 - f, cream, 2); glint(30, -2 + f * 2, amber, 2) }
    }

    // ---- Anciennes tours -----------------------------------------------------------------------

    private fun hauntedArmor() {
        val steel = color(0x8E98A8); val ghost = color(0x7FF0C8); val cloth = color(0x6A2A3A)
        box(18 - motion / 2, 40, 6, 12, dim(steel)); box(25 + motion / 2, 40, 6, 12, steel)
        mass(16, 25, 17, 16, steel); rect(24, 27, 1, 12, dim(steel))
        shape(cloth, 19, 30, 29, 30, 30, 46, 26, 43, 22, 47, 18, 44)
        box(12, 23, 8, 7, steel); box(29, 23, 8, 7, steel)
        rect(16, 21 - f % 2, 2, 2, ghost); rect(32, 20 - (f + 1) % 2, 2, 2, ghost)
        mass(18, 8, 15, 16, steel); rect(22, 14, 11, 3, ink)
        rect(27, 15, 2, 1, if (blink) dim(ghost) else ghost); rect(31, 15, 1, 1, if (blink) dim(ghost) else ghost)
        line(20, 8, 15, 2, cloth, 2)
        val tipX = if (attack && f >= 2) 48 else 40; val tipY = if (attack && f >= 2) 26 else 10
        line(31, 30, 36, 34 + motion, ink, 5); line(31, 30, 36, 34 + motion, steel, 3)
        line(37, 33, tipX, tipY, ink, 4); line(37, 33, tipX, tipY, lit(steel), 2)
        line(33, 34, 41, 32, ink, 3); rect(38, 34, 2, 3, color(0x9A6A44))
    }

    private fun bat() {
        lift(-3)
        val flap = intArrayOf(-6, 0, 6, 0)[f % 4]
        poly(ink, 20, 29, 11, 20 + flap, 3, 22 + flap, 4, 38 + flap / 2, 11, 34, 16, 39, 22, 35)
        poly(color(0x796697), 19, 30, 10, 23 + flap, 5, 24 + flap, 6, 34 + flap / 2, 12, 31, 18, 36)
        poly(ink, 28, 29, 37, 20 + flap, 45, 22 + flap, 44, 38 + flap / 2, 37, 34, 32, 39, 26, 35)
        poly(color(0xAA7DA8), 29, 30, 38, 23 + flap, 43, 24 + flap, 42, 34 + flap / 2, 36, 31, 30, 36)
        box(18, 24, 15, 19, color(0x59476D)); poly(color(0xB190BC), 19, 27, 17, 15, 23, 21, 29, 21, 34, 16, 32, 27)
        eyes(23, 29, blink, 6); rect(25, 35, 2, 3, cream); rect(30, 35, 1, 2, cream)
    }

    private fun shade() {
        val cloak = color(0x2E2A44); val void = color(0x14121F); val eye = color(0xC79AF2)
        shape(cloak, 14, 40, 34, 40, 32, 50 + f % 2, 29, 46, 26, 52, 22, 46, 18, 51 - f % 2, 16, 46)
        shape(cloak, 15, 22, 33, 22, 35, 42, 13, 42)
        line(18, 25, 17, 40, lit(cloak))
        line(31, 30, 37, 32 + motion, ink, 4); line(31, 30, 37, 32 + motion, cloak, 2)
        line(37, 32 + motion, 43, 26 + motion, ink, 3); line(37, 32 + motion, 43, 26 + motion, eye)
        shape(cloak, 15, 26, 16, 12, 24, 6, 33, 10, 36, 20, 34, 27)
        shape(void, 22, 24, 23, 14, 30, 13, 33, 20, 32, 25)
        rect(25, 17, 2, if (blink) 1 else 2, eye); rect(29, 17, 2, if (blink) 1 else 2, eye)
        rect(9, 45 + f % 2, 3, 2, lit(cloak)); rect(6, 41 - f % 2, 2, 2, lit(cloak))
    }

    private fun statue() {
        val stone = color(0xA8A49A); val moss = color(0x6E9A5A)
        box(10, 46, 28, 7, dim(stone)); rect(11, 47, 26, 1, lit(stone))
        line(14, 4, 14, 46, ink, 3); line(14, 5, 14, 45, dim(stone)); shape(stone, 12, 6, 14, -3, 16, 6)
        box(17, 38, 6, 9, stone); box(26, 38, 6, 9, stone)
        mass(15, 23, 19, 16, stone); line(20, 26, 23, 31, dim(stone))
        mass(18, 9, 14, 15, stone); poly(moss, 18, 10, 23, 8, 21, 13)
        val glare = cast || attack
        rect(25, 15, 2, 3, if (glare) color(0x9FE3F2) else dim(stone)); rect(29, 15, 1, 3, if (glare) color(0x9FE3F2) else dim(stone))
        box(26 + motion / 2, 24, 15, 20, stone); rect(27 + motion / 2, 25, 3, 18, lit(stone))
        rect(33 + motion / 2, 27, 2, 13, dim(stone)); rect(29 + motion / 2, 32, 10, 2, dim(stone))
        rect(27 + motion / 2, 40, 6, 3, moss); line(36, 26, 38, 31, dim(stone))
    }

    private fun emberGolem() {
        val rock = color(0x4A3A3A); val lava = color(0xFF7A2A); val hot = color(0xFFE08A)
        box(12 - motion / 2, 42, 10, 11, dim(rock)); box(27 + motion / 2, 42, 10, 11, rock)
        mass(9, 22, 30, 22, rock)
        line(14, 26, 20, 34, lava); line(20, 34, 17, 41, lava); line(31, 25, 27, 33, lava); line(27, 33, 33, 40, lava)
        disc(24, 32, 3, lava); rect(24, 32, 1, 1, hot)
        box(3, 24 - motion, 8, 18, rock); rect(5, 30 - motion, 1, 6, lava)
        box(37, 24 + motion, 9, 18, rock); rect(41, 28 + motion, 1, 8, lava)
        mass(17, 10, 15, 13, rock)
        rect(22, 15, 3, if (blink) 1 else 2, lava); rect(28, 15, 2, if (blink) 1 else 2, lava); rect(23, 19, 6, 1, lava)
        shape(lava, 18, 10, 20, 2 - f % 2, 23, 9); shape(hot, 25, 10, 28, 3 + f % 2, 30, 10)
        rect(12, 20, 2, 2, lava); rect(34, 19 - f % 2, 2, 2, lava)
        if (cast) { rect(19, -2 - f, 2, 2, color(0x8A8080)); rect(28, -4 - f % 2, 2, 2, color(0x8A8080)) }
    }

    private fun mimic() {
        val chest = color(0x8A5A32); val band = color(0xC9A15A); val tongue = color(0xD8506A)
        val open = if (attack) intArrayOf(2, 6, 10, 4)[f] else if (cast) 6 else (f % 2) * 2
        box(14 - motion / 2, 44, 5, 8, dim(chest)); box(30 + motion / 2, 44, 5, 8, dim(chest))
        mass(10, 30, 28, 15, chest); rect(10, 37, 28, 2, band); rect(10, 30, 3, 15, band); rect(35, 30, 3, 15, band)
        rect(12, 30 - open, 24, open + 1, color(0x3A1A24))
        for (k in 0..5) { poly(cream, 12 + k * 4, 30, 14 + k * 4, 27, 16 + k * 4, 30); poly(cream, 12 + k * 4, 30 - open, 14 + k * 4, 33 - open, 16 + k * 4, 30 - open) }
        if (open > 2) shape(tongue, 26, 30, 36, 28 + f % 2, 42, 32, 34, 34)
        shape(chest, 10, 31 - open, 11, 20 - open, 37, 20 - open, 38, 31 - open)
        rect(10, 26 - open, 28, 2, band); rect(13, 22 - open, 23, 2, lit(chest))
        box(19, 22 - open, 4, 4, cream); box(27, 22 - open, 4, 4, cream)
        rect(21, 23 - open, 1, if (blink) 1 else 2, ink); rect(29, 23 - open, 1, if (blink) 1 else 2, ink)
        box(22, 32, 5, 5, band); rect(24, 34, 1, 2, ink)
        disc(7, 50, 2, gold); disc(41, 51, 2, gold); rect(6, 49, 1, 1, cream)
    }

    /** Ember wyvern of the old towers, or the frost dragon of the high plateaus. */
    private fun dragon(frost: Boolean) {
        val hide = color(if (frost) 0x6FA8C8 else 0x8A2A2A)
        val belly = color(if (frost) 0xDDF2FA else 0xE8A060)
        val wing = color(if (frost) 0x3E6E96 else 0x5A1E24)
        val horn = color(if (frost) 0xDDF6FF else 0x3A2A26)
        val breath = color(if (frost) 0x9FE3F2 else 0xFF7A2A)
        val core = color(if (frost) 0xFFFFFF else 0xFFE08A)
        val beat = intArrayOf(-3, 0, 3, 0)[f % 4]
        shape(hide, 8, 38, -5, 43, -10, 37, -3, 37, 5, 33); shape(horn, -10, 37, -14, 33, -7, 36)
        shape(wing, 16, 24, 3, 3 + beat, -8, 8 + beat, -2, 14 + beat, -7, 22 + beat, 4, 25, 12, 31)
        box(14 - motion / 2, 40, 7, 13, dim(hide)); box(26 + motion / 2, 40, 8, 13, hide)
        rect(14 - motion / 2, 51, 7, 2, cream); rect(26 + motion / 2, 51, 8, 2, cream)
        mass(10, 22, 26, 22, hide); shape(belly, 22, 26, 34, 28, 32, 42, 22, 42)
        for (y in intArrayOf(30, 34, 38)) rect(23, y, 9, 1, dim(belly))
        for (x in intArrayOf(12, 17, 22)) {
            if (frost) shape(horn, x, 23, x + 2, 15, x + 4, 23) else poly(dim(hide), x, 22, x + 2, 17, x + 4, 22)
        }
        shape(hide, 28, 27, 32, 12, 38, 11, 36, 29)
        mass(32, 4, 16, 12, hide); box(44, 8, 10, 7, hide); rect(51, 9, 2, 1, ink)
        box(44, 14, 9, if (attack) 6 else 3, dim(hide)); if (attack) { rect(46, 14, 1, 2, cream); rect(50, 14, 1, 2, cream) }
        shape(horn, 34, 5, 30, -6, 37, 3); shape(horn, 38, 4, 38, -7, 42, 3)
        rect(41, 7, 3, 2, color(0xFFD27A)); rect(42, 7, 1, 2, if (blink) color(0xFFD27A) else ink)
        shape(wing, 22, 23, 18, 2 + beat, 26, -7 + beat, 31, 3 + beat, 31, 21)
        line(23, 21, 25, -3 + beat, lit(wing)); line(27, 20, 29, 2 + beat, lit(wing))
        if (attack && f >= 2) { shape(breath, 53, 13, 60, 7, 61, 22); rect(54, 14, 4, 3, core) }
        if (cast) { rect(55, 8 - f, 2, 2, breath); rect(58, 13 + f % 2, 2, 2, breath) }
    }

    // ---- Marais des lucioles -------------------------------------------------------------------

    private fun toad() {
        val skin = color(0x6A5A9E); val spot = color(0xC8F27A); val belly = color(0xD8C8E8)
        shape(dim(skin), 4, 52, 6, 42, 15, 43, 14, 52); shape(dim(skin), 34, 52, 35, 43, 44, 42, 46, 52)
        shape(skin, 6, 46, 8, 32, 18, 25, 32, 25, 40, 32, 42, 46)
        shape(belly, 14, 46, 18, 37, 32, 37, 36, 46)
        if (cast) disc(25, 39, 5 + f % 2, color(0xF2A0C8))
        disc(19, 25, 4, ink); disc(31, 25, 4, ink); disc(19, 25, 3, skin); disc(31, 25, 3, skin)
        disc(19, 25, 2, color(0xF2D04A)); disc(31, 25, 2, color(0xF2D04A))
        rect(18, 25, 3, if (blink) 2 else 1, ink); rect(30, 25, 3, if (blink) 2 else 1, ink)
        line(13, 34, 38, 34, ink)
        disc(11, 37, 2, spot); disc(37, 30, 2, spot); disc(25, 29, 1, spot); disc(14, 43, 1, spot)
        box(9 - motion / 2, 46, 8, 6, skin); box(32 + motion / 2, 46, 8, 6, skin)
        if (attack) line(38, 33, 48, 32 - f, color(0xD8506A), 2)
        rect(16, 47, 1, 3, spot)
    }

    private fun fireflyJelly() {
        lift(-6)
        val bell = color(0x5FC8B8)
        for (k in 0..4) line(14 + k * 5, 32, 13 + k * 5 + ((f + k) % 2) * 2, 44 + (k % 2) * 3, lit(bell))
        shape(bell, 10, 32, 12, 20, 18, 13, 30, 13, 36, 20, 38, 32)
        rect(11, 31, 27, 2, dim(bell)); poly(lit(bell), 14, 22, 18, 16, 21, 17, 16, 24)
        disc(24, 26, 4, glowYellow); rect(24, 26, 2, 2, cream)
        rect(19, 19, 2, if (blink) 1 else 3, ink); rect(28, 19, 2, if (blink) 1 else 3, ink)
        rect(6 + f * 2, 14, 2, 2, glowYellow); rect(41 - f, 30, 2, 2, glowYellow); rect(30 + f, 6, 1, 1, glowYellow)
        if (cast) glint(24, 26, cream, 3 + f % 2)
    }

    private fun waterSnake() {
        val scale = color(0x3E7A6E); val belly = color(0xD8D8A0); val pattern = color(0x2A4E48)
        shape(scale, 6, 52, 6, 44, 14, 40, 30, 40, 40, 44, 40, 52)
        line(8, 46, 38, 46, dim(scale)); for (x in intArrayOf(11, 19, 27, 35)) poly(pattern, x, 43, x + 2, 41, x + 4, 43, x + 2, 45)
        line(28, 42, 26, 30, ink, 9); line(28, 42, 26, 30, scale, 7); line(30, 41, 29, 31, belly, 2)
        line(26, 30, 30 + motion / 2, 20, ink, 8); line(26, 30, 30 + motion / 2, 20, scale, 6)
        shape(lit(scale), 26, 23, 21, 16, 27, 13)
        mass(28 + motion / 2, 11, 14, 10, scale); box(38 + motion / 2, 14, 7, 6, scale)
        rect(34 + motion / 2, 13, 3, 2, color(0xF2D04A)); rect(35 + motion / 2, 13, 1, 2, if (blink) color(0xF2D04A) else ink)
        if (attack) {
            rect(39 + motion / 2, 19, 5, 3, color(0x8A2A3A)); rect(39 + motion / 2, 19, 1, 2, cream); rect(43 + motion / 2, 19, 1, 2, cream)
            line(45 + motion / 2, 18, 49 + motion / 2, 17, color(0xD8506A)); rect(49 + motion / 2, 16, 1, 1, color(0xD8506A))
        }
        rect(29, 35, 2, 2, pattern); rect(28, 26, 2, 2, pattern)
    }

    private fun sludge() {
        val mud = color(0x6E6A3A); val reed = color(0x8FA84A)
        val sag = bob + if (attack) f % 2 else 0
        line(20, 25, 17, 12, reed, 2); box(16, 6, 4, 7, color(0x6B4A35)); line(30, 23, 33, 14, reed, 2)
        shape(mud, 6, 52, 7, 40 + sag, 11, 30 + sag, 18, 23 + sag, 28, 21 + sag, 36, 26 + sag, 41, 36 + sag, 43, 52)
        rect(9, 44, 2, 5, dim(mud)); rect(38, 40, 2, 6, dim(mud))
        disc(13, 36, 2, lit(mud)); disc(34, 31 + sag, 1, lit(mud)); rect(11, 29 + sag, 4, 2, lit(mud))
        box(19, 31 + sag, 5, 5, cream); box(28, 30 + sag, 5, 5, cream)
        rect(21, 33 + sag, 2, if (blink) 1 else 2, ink); rect(30, 32 + sag, 2, if (blink) 1 else 2, ink)
        box(21, 39 + sag, 10, 4, color(0x3A361E))
        rect(25, 17 + sag, 2, 2, glowYellow)
        if (cast) { rect(30, 16 - f * 2, 2, 2, lit(mud)); rect(14, 18 - f, 2, 2, lit(mud)) }
    }

    private fun croc() {
        val hide = color(0x4E6A3A); val belly = color(0xC8C08A); val eye = color(0xF2D04A)
        shape(hide, 6, 40, -8, 45, -11, 41, 4, 34); for (x in intArrayOf(-6, -1, 3)) rect(x, 39, 2, 2, dim(hide))
        box(5 - motion / 2, 44, 6, 8, dim(hide)); box(22 + motion / 2, 44, 6, 8, dim(hide))
        mass(2, 31, 34, 15, hide); rect(6, 42, 26, 3, belly)
        for (k in 0..5) rect(5 + k * 5, 29, 3, 3, dim(hide))
        box(10 + motion / 2, 45, 6, 8, hide); box(27 - motion / 2, 45, 6, 8, hide)
        val jaw = if (attack) intArrayOf(1, 4, 6, 2)[f] else 0
        box(40, 39 + jaw, 15, 4, dim(hide)); for (k in 0..3) rect(42 + k * 3, 38 + jaw, 1, 2, cream)
        mass(30, 30, 12, 12, hide); box(40, 33, 15, 7, hide); rect(52, 34, 2, 1, ink)
        for (k in 0..3) rect(42 + k * 3, 39, 1, 2, cream)
        box(33, 25, 7, 7, hide); rect(35, 27, 3, 2, eye); rect(36, 27, 1, 2, if (blink) eye else ink)
        shape(color(0x5FA86A), 29, 30, 33, 25, 41, 27, 39, 31)
    }

    private fun mantis() {
        val green = color(0x7AB04A); val eye = color(0xE8E06A)
        line(18, 40, 11 - motion / 2, 52, ink, 3); line(18, 40, 11 - motion / 2, 52, dim(green))
        line(21, 40, 22, 52, ink, 3); line(21, 40, 22, 52, dim(green))
        line(24, 40, 31 + motion / 2, 52, ink, 3); line(24, 40, 31 + motion / 2, 52, dim(green))
        shape(green, 4, 38, 9, 31, 22, 33, 25, 40, 13, 45)
        shape(lit(green), 7, 33, 20, 29, 25, 34, 10, 38)
        line(22, 37, 28, 21, ink, 7); line(22, 37, 28, 21, green, 5)
        shape(green, 25, 12, 37, 9, 34, 18, 28, 19)
        disc(26, 12, 2, eye); disc(36, 10, 2, eye); rect(26, 12, 1, if (blink) 2 else 1, ink); rect(36, 10, 1, if (blink) 2 else 1, ink)
        line(27, 11, 21, 2, dim(green)); line(34, 9, 38, 0, dim(green))
        val tipX = if (attack && f >= 2) 47 else 37; val tipY = if (attack && f >= 2) 29 else 11
        line(28, 23, 34, 26, ink, 4); line(28, 23, 34, 26, green, 2)
        line(34, 26, tipX, tipY, ink, 4); line(34, 26, tipX, tipY, lit(green), 2)
        rect((34 + tipX) / 2, (26 + tipY) / 2, 1, 1, cream)
    }

    private fun hydra() {
        val hide = color(0x3E6E5E); val belly = color(0xC8D8A0); val eye = color(0xF2D04A)
        shape(hide, 4, 42, -8, 48, -11, 43, 2, 35)
        box(5 - motion / 2, 45, 8, 8, dim(hide)); box(26 + motion / 2, 45, 8, 8, dim(hide))
        mass(1, 30, 36, 20, hide); shape(belly, 12, 49, 14, 38, 28, 38, 30, 49)
        val ends = intArrayOf(9, 9, 25, 1, 41, 11)
        val roots = intArrayOf(12, 32, 20, 30, 29, 32)
        for (k in 0..2) {
            val sway = intArrayOf(0, 1, 0, -1)[(f + k) % 4]
            val hx = ends[k * 2] + sway + (if (attack) motion else 0); val hy = ends[k * 2 + 1] + (if (k == 1) sway else 0)
            line(roots[k * 2], roots[k * 2 + 1], hx, hy + 3, ink, 8); line(roots[k * 2], roots[k * 2 + 1], hx, hy + 3, hide, 6)
            line(roots[k * 2] + 2, roots[k * 2 + 1], hx + 2, hy + 5, belly, 2)
            shape(lit(hide), hx - 6, hy - 2, hx - 10, hy - 8, hx - 3, hy - 4)
            mass(hx - 6, hy - 4, 13, 9, hide); box(hx + 5, hy - 1, 8, 6, hide)
            rect(hx + 1, hy - 2, 3, 2, eye); rect(hx + 2, hy - 2, 1, 2, if (blink) eye else ink)
            rect(hx + 7, hy + 4, 1, 2, cream); rect(hx + 10, hy + 4, 1, 2, cream)
        }
        if (cast) { rect(50, 6 - f, 2, 2, color(0xB7F27A)); rect(46, 14 + f, 2, 2, color(0xB7F27A)) }
    }

    // ---- Hauts plateaux ------------------------------------------------------------------------

    private fun ram() {
        val wool = color(0xEDE8DA); val face = color(0x5A4A40); val horn = color(0xC9B08A)
        for ((x, d) in listOf(9 to -1, 15 to 1, 25 to -1, 31 to 1)) box(x + d * motion / 2, 41, 5, 11, face)
        mass(5, 23, 30, 21, wool)
        for (k in 0..4) { disc(9 + k * 6, 26 + (k % 2) * 2, 3, lit(wool)); disc(10 + k * 6, 40 - (k % 2), 2, dim(wool)) }
        disc(30, 19, 6, ink); disc(30, 19, 5, horn); disc(30, 19, 3, dim(horn)); disc(30, 19, 1, horn); rect(27, 24, 3, 4, horn)
        mass(32, 19, 11, 14, face); box(39, 25, 6, 7, lit(face)); rect(43, 27, 1, 1, ink)
        rect(37, 23, 3, if (blink) 1 else 2, color(0xF2D04A)); if (!blink) rect(38, 23, 2, 1, ink)
        poly(dim(face), 33, 21, 30, 27, 35, 25)
        rect(32, 31, 9, 2, color(0xC8463C)); disc(36, 34, 2, gold)
    }

    private fun stormEagle() {
        lift(-4)
        val feather = color(0x4A5A7A); val white = color(0xF0F0F0); val beak = color(0xF2C14E); val bolt = color(0xFFF07A)
        shape(dim(feather), 20, 26, 6, 10 + flap, -2, 12 + flap, 6, 18 + flap, 0, 22 + flap, 10, 26, 16, 32)
        shape(feather, 14, 34, 5, 40, 9, 43, 18, 37)
        mass(16, 22, 16, 16, feather); shape(white, 24, 24, 31, 26, 30, 36, 24, 36)
        shape(feather, 22, 26, 26, 8 + flap, 34, 4 + flap, 33, 12 + flap, 29, 27)
        line(26, 22, 29, 10 + flap, lit(feather))
        mass(26, 12, 11, 10, white); shape(beak, 35, 15, 42, 17, 37, 21, 35, 19)
        rect(31, 15, 2, if (blink) 1 else 2, ink); line(29, 14, 34, 15, ink)
        line(21, 38, 20, 43, beak); line(26, 38, 27, 43, beak)
        if (cast || f % 2 == 0) { line(41, 3, 38, 8, bolt, 2); line(38, 8, 42, 10, bolt, 2); line(42, 10, 39, 15, bolt, 2) }
        if (cast) { rect(4, 8 + f, 2, 2, bolt); rect(10, 4 - f % 2, 1, 2, bolt) }
    }

    private fun lynx() {
        val fur = color(0xD8CCB8); val spot = color(0x8A7A68); val dark = color(0x4A4038)
        box(3, 29, 7, 5, fur); rect(3, 30, 2, 3, dark)
        box(9 - motion / 2, 44, 7, 8, dim(fur)); box(25 + motion / 2, 44, 7, 8, dim(fur))
        mass(6, 29, 30, 17, fur)
        for ((x, y) in listOf(12 to 33, 18 to 37, 23 to 33, 29 to 37, 15 to 40, 9 to 37)) rect(x, y, 2, 2, spot)
        rect(12, 29, 10, 1, color(0xFFFFFF))
        box(13 + motion / 2, 45, 8, 8, fur); box(30 - motion / 2, 45, 8, 8, fur)
        rect(14 + motion / 2, 50, 6, 2, lit(fur)); rect(31 - motion / 2, 50, 6, 2, lit(fur))
        mass(30, 19, 15, 14, fur)
        shape(fur, 31, 20, 32, 11, 36, 18); line(32, 11, 32, 8, dark, 2)
        shape(fur, 38, 19, 41, 11, 43, 19); line(41, 11, 41, 8, dark, 2)
        shape(lit(fur), 31, 26, 29, 32, 36, 30)
        box(39, 23, 6, 5, lit(fur)); rect(43, 24, 2, 1, color(0xC88A8A))
        rect(36, 21, 3, if (blink) 1 else 2, color(0x9FD6A0)); if (!blink) rect(37, 21, 1, 2, ink)
    }

    private fun yeti() {
        val fur = color(0xEEF2F6); val shade = color(0xB8C4D4); val skin = color(0x7A8EA8)
        box(13 - motion / 2, 42, 9, 11, shade); box(27 + motion / 2, 42, 9, 11, fur)
        rect(13 - motion / 2, 50, 9, 3, skin); rect(27 + motion / 2, 50, 9, 3, skin)
        shape(fur, 9, 44, 8, 26, 14, 16, 34, 16, 40, 26, 39, 44)
        for ((x, y) in listOf(13 to 30, 18 to 36, 33 to 32, 28 to 40)) line(x, y, x - 1, y + 4, shade)
        box(3, 24 - motion, 8, 20, shade); box(3, 42 - motion, 8, 6, skin)
        box(37, 24 + motion, 9, 20, fur); box(37, 42 + motion, 9, 6, skin)
        shape(fur, 14, 19, 18, 7, 28, 4, 36, 9, 39, 19)
        box(23, 18, 13, 11, skin)
        rect(26, 21, 2, if (blink) 1 else 2, ink); rect(32, 21, 2, if (blink) 1 else 2, ink)
        rect(22, 17, 15, 3, fur)
        box(27, 25, 7, 3, color(0x3A2A3A)); rect(28, 25, 1, 2, cream); rect(32, 25, 1, 2, cream)
        rect(10, 44, 1, 3, color(0x9FD6EA)); rect(40, 43, 1, 3, color(0x9FD6EA))
    }

    private fun griffon() {
        val feather = color(0xE8D8B0); val lion = color(0xC9A060); val beak = color(0xF2C14E); val wing = color(0x8A6A44)
        line(6, 34, -2, 27, ink, 3); line(6, 34, -2, 27, lion); box(-5, 24, 5, 5, dim(lion))
        box(8 - motion / 2, 40, 6, 12, dim(lion)); box(13 + motion / 2, 41, 6, 11, lion)
        mass(5, 26, 28, 16, lion)
        val up = flap / 2
        shape(wing, 12, 31, 5, 18 + up, 6, 5 + up, 10, 10 + up, 13, 1 + up, 17, 8 + up, 21, 2 + up, 24, 11 + up, 27, 7 + up, 27, 30)
        line(13, 28, 8, 9 + up, lit(wing)); line(17, 28, 14, 6 + up, lit(wing)); line(21, 28, 21, 7 + up, lit(wing))
        rect(8, 20 + up, 16, 1, dim(wing))
        box(24 + motion / 2, 40, 5, 12, feather); box(30 - motion / 2, 40, 5, 12, lit(feather))
        rect(24 + motion / 2, 50, 5, 2, beak); rect(30 - motion / 2, 50, 5, 2, beak)
        shape(feather, 24, 24, 36, 24, 34, 40, 24, 40)
        mass(29, 12, 13, 12, feather); shape(beak, 40, 16, 47, 18, 44, 22, 40, 21)
        rect(36, 15, 2, if (blink) 1 else 2, ink); shape(feather, 30, 13, 28, 5, 33, 11)
    }

    /** Fern drake, the third companion. */
    private fun fernDrake() {
        val green = color(0x509887); val light = color(0xA5CDA1); val dark = color(0x35656A)
        val leafLight = color(0xBBDD85)
        poly(ink, 18, 41, 6, 43, 2, 35, 3, 30, 7, 37, 17, 34)
        poly(green, 17, 40, 7, 41, 4, 35, 8, 38, 17, 36)
        box(15 - motion / 2, 42, 8, 11, dark); box(28 + motion / 2, 42, 9, 11, dark)
        box(14, 25, 24, 22, green); rect(25, 32, 9, 13, light)
        val beat = intArrayOf(-3, 1, 5, 1)[f % 4]
        poly(ink, 18, 32, 7, 18 + beat, 5, 8 + beat, 17, 13 + beat, 25, 26)
        poly(light, 18, 30, 9, 18 + beat, 8, 11 + beat, 16, 16 + beat, 22, 26)
        line(9, 13 + beat, 16, 27, dark, 2)
        box(24, 16, 17, 17, green); box(34, 25, 12, if (attack || cast) 9 else 6, green)
        rect(35, 28, 9, 2, dark); eyes(29, 21, blink, 7); rect(29, 23, 3, 1, color(0xE8CB7B))
        poly(light, 24, 20, 22, 9, 28, 14); poly(light, 35, 18, 38, 9, 39, 21)
        if (cast) { gem(42, 35, leafLight, cream, 3); glint(39, 42, cream, 2) }
    }
}
