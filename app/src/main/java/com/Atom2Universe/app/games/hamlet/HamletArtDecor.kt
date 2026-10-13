package com.Atom2Universe.app.games.hamlet

import kotlin.math.cos
import kotlin.math.sin

/** Décors : petits objets de village, statues, enclos, et clôtures qui se raccordent. */
object HamletArtDecor {

    private const val WOOD = 0x9A6A3A
    private const val DARK_WOOD = 0x6A4A2E
    private const val STONE = 0xB4AEA2

    fun draw(p: Iso, def: Def, w: Float, d: Float, mask: Int) {
        val l = def.look
        val cx = w / 2; val cy = d / 2
        val a = l.a; val b = l.b
        if (l.kind.startsWith("f_")) { fence(p, l, mask); return }
        p.shadow(0.1f, 0.1f, w - 0.1f, d - 0.1f, 0.22f)
        when (l.kind) {
            "banner" -> {
                p.cyl(cx, cy, 0f, 0.05f, 0.18f, 0x6A665C)
                p.line(cx, cy, 0.05f, cx, cy, 1.55f, DARK_WOOD, 3f)
                p.line(cx, cy, 1.5f, cx + 0.42f, cy, 1.5f, DARK_WOOD, 2.4f)
                p.face(a, cx, cy, 1.48f, cx + 0.4f, cy, 1.48f, cx + 0.4f, cy, 0.78f, cx + 0.2f, cy, 0.9f, cx, cy, 0.78f)
                p.face(Iso.shade(a, 0.35f), cx + 0.1f, cy, 1.3f, cx + 0.3f, cy, 1.3f, cx + 0.3f, cy, 1.18f, cx + 0.1f, cy, 1.18f)
            }
            "d_maypole" -> {
                p.cyl(cx, cy, 0f, 0.12f, 0.2f, STONE)
                p.cyl(cx, cy, 0.1f, 2.1f, 0.06f, 0xF4F0E4)
                val cols = intArrayOf(0xE8302A, 0x3A9AD8, 0xF2C230, 0x4AD05A)
                for (k in 0..3) {
                    val ang = k * 1.5708f + 0.4f
                    val ex = cx + cos(ang) * 0.5f; val ey = cy + sin(ang) * 0.5f
                    p.line(cx, cy, 2.05f, ex, ey, 0.55f, cols[k], 2.2f)
                    p.dot(ex, ey, 0.55f, 4f, cols[k])
                }
                p.ball(cx, cy, 2.18f, 0.1f, 0xF2C230, 0.1f)
            }
            "d_fire" -> fire(p, cx, cy, l.n)
            "d_catapult" -> {
                p.box(cx - 0.4f, cy - 0.28f, 0.05f, cx + 0.4f, cy + 0.28f, 0.2f, WOOD)
                p.cyl(cx - 0.3f, cy + 0.3f, 0f, 0.12f, 0.1f, DARK_WOOD); p.cyl(cx + 0.3f, cy + 0.3f, 0f, 0.12f, 0.1f, DARK_WOOD)
                p.line(cx - 0.3f, cy, 0.2f, cx - 0.3f, cy, 0.7f, DARK_WOOD, 3f); p.line(cx + 0.3f, cy, 0.2f, cx + 0.3f, cy, 0.7f, DARK_WOOD, 3f)
                p.line(cx - 0.3f, cy, 0.62f, cx + 0.15f, cy, 0.3f, WOOD, 3.4f)
                p.ball(cx + 0.18f, cy, 0.3f, 0.1f, 0x6A665C, 0.1f)
            }
            "d_beehive" -> {
                p.box(cx - 0.2f, cy - 0.2f, 0f, cx + 0.2f, cy + 0.2f, 0.28f, WOOD)
                p.cyl(cx, cy, 0.28f, 0.48f, 0.2f, 0xE8B82A); p.cyl(cx, cy, 0.48f, 0.65f, 0.17f, 0xD8A820); p.ball(cx, cy, 0.67f, 0.14f, 0xE8B82A, 0.1f)
                p.oval(cx, cy + 0.18f, 0.44f, 3.4f, 3f, 0x2A1E12)
                p.dot(cx + 0.3f, cy + 0.3f, 0.7f, 1.8f, 0x3A2E22)
            }
            "d_wheelbarrow" -> {
                p.box(cx - 0.3f, cy - 0.2f, 0.18f, cx + 0.2f, cy + 0.2f, 0.4f, 0xC8452E)
                p.cyl(cx + 0.28f, cy, 0.1f, 0.12f, 0.1f, 0x4A4A52)
                p.line(cx - 0.3f, cy - 0.15f, 0.2f, cx - 0.55f, cy - 0.2f, 0.18f, WOOD, 2.4f); p.line(cx - 0.3f, cy + 0.15f, 0.2f, cx - 0.55f, cy + 0.2f, 0.18f, WOOD, 2.4f)
                p.ball(cx - 0.05f, cy, 0.45f, 0.2f, 0x7A5A3A, 0.1f)
            }
            "d_laundry" -> {
                p.line(cx - 0.45f, cy, 0f, cx - 0.45f, cy, 0.95f, DARK_WOOD, 3f); p.line(cx + 0.45f, cy, 0f, cx + 0.45f, cy, 0.95f, DARK_WOOD, 3f)
                p.line(cx - 0.45f, cy, 0.92f, cx + 0.45f, cy, 0.88f, 0xE8E0D0, 1.2f)
                for ((i, col) in intArrayOf(0xF4F0E4, 0x4A8AD8, 0xE8402A, 0xF4F0E4).withIndex()) { val u = cx - 0.35f + i * 0.23f; p.face(col, u, cy, 0.9f, u + 0.17f, cy, 0.9f, u + 0.17f, cy, 0.56f, u, cy, 0.56f) }
            }
            "d_picnic" -> {
                p.box(cx - 0.34f, cy - 0.22f, 0.26f, cx + 0.34f, cy + 0.22f, 0.32f, WOOD)
                p.box(cx - 0.34f, cy + 0.26f, 0.15f, cx + 0.34f, cy + 0.38f, 0.2f, WOOD)
                p.box(cx - 0.34f, cy - 0.38f, 0.15f, cx + 0.34f, cy - 0.26f, 0.2f, WOOD)
                for (u in listOf(-0.24f, 0.24f)) p.box(cx + u - 0.03f, cy - 0.2f, 0f, cx + u + 0.03f, cy + 0.2f, 0.26f, DARK_WOOD)
                p.dot(cx, cy, 0.36f, 4f, 0xE8402A); p.dot(cx + 0.15f, cy, 0.36f, 3f, 0xF4F0E4)
            }
            "d_kennel" -> {
                p.box(cx - 0.3f, cy - 0.3f, 0f, cx + 0.3f, cy + 0.3f, 0.4f, 0xC87A4A)
                p.faceY(cy + 0.3f, cx - 0.1f, cx + 0.1f, 0f, 0.28f, 0x2A1E12)
                p.gable(cx - 0.3f, cy - 0.3f, cx + 0.3f, cy + 0.3f, 0.4f, 0.28f, 0x8A3A32, 0xC87A4A, true, 0.06f, 1)
            }
            "d_mailbox" -> {
                p.box(cx - 0.03f, cy - 0.03f, 0f, cx + 0.03f, cy + 0.03f, 0.6f, DARK_WOOD)
                p.box(cx - 0.17f, cy - 0.1f, 0.6f, cx + 0.17f, cy + 0.1f, 0.8f, 0x3A6AC8)
                p.faceY(cy + 0.1f, cx - 0.1f, cx + 0.1f, 0.62f, 0.76f, 0x1A3A8A)
                p.face(0xE8302A, cx + 0.17f, cy, 0.7f, cx + 0.17f, cy, 0.95f, cx + 0.23f, cy, 0.95f)
            }
            "d_rune" -> {
                p.face(0x8A867A, cx - 0.22f, cy + 0.05f, 0f, cx + 0.22f, cy + 0.05f, 0f, cx + 0.15f, cy, 0.95f, cx - 0.12f, cy, 1.05f)
                p.face(0xA8A498, cx - 0.22f, cy + 0.05f, 0f, cx + 0.22f, cy + 0.05f, 0f, cx + 0.15f, cy + 0.05f, 0.9f, cx - 0.12f, cy + 0.05f, 1.0f)
                p.line(cx, cy + 0.07f, 0.35f, cx, cy + 0.07f, 0.8f, 0x4AB8F0, 2.2f); p.line(cx - 0.08f, cy + 0.07f, 0.65f, cx + 0.08f, cy + 0.07f, 0.55f, 0x4AB8F0, 2.2f)
            }
            "d_stocks" -> {
                p.box(cx - 0.35f, cy - 0.04f, 0f, cx - 0.27f, cy + 0.04f, 0.6f, WOOD); p.box(cx + 0.27f, cy - 0.04f, 0f, cx + 0.35f, cy + 0.04f, 0.6f, WOOD)
                p.box(cx - 0.35f, cy - 0.06f, 0.3f, cx + 0.35f, cy + 0.06f, 0.4f, DARK_WOOD)
                p.box(cx - 0.35f, cy - 0.06f, 0.45f, cx + 0.35f, cy + 0.06f, 0.52f, WOOD)
            }
            "d_tent" -> tent(p, cx, cy, a, b)
            "d_torch" -> {
                p.cyl(cx, cy, 0f, 0.8f, 0.04f, DARK_WOOD)
                flame(p, cx, cy, 0.85f, 1f)
            }
            "d_archery" -> {
                p.line(cx - 0.3f, cy, 0f, cx - 0.1f, cy, 0.5f, DARK_WOOD, 3f); p.line(cx + 0.3f, cy, 0f, cx + 0.1f, cy, 0.5f, DARK_WOOD, 3f)
                p.oval(cx, cy + 0.03f, 0.7f, 16f, 20f, 0xF4F0E4); p.oval(cx, cy + 0.03f, 0.7f, 12f, 15f, 0xE8302A); p.oval(cx, cy + 0.03f, 0.7f, 8f, 10f, 0xF4F0E4); p.oval(cx, cy + 0.03f, 0.7f, 4f, 5f, 0xE8302A)
                p.line(cx + 0.05f, cy + 0.05f, 0.8f, cx + 0.3f, cy + 0.2f, 0.95f, DARK_WOOD, 1.4f)
            }
            "d_birdbath" -> {
                p.cyl(cx, cy, 0f, 0.4f, 0.1f, STONE); p.cyl(cx, cy, 0.38f, 0.48f, 0.3f, STONE); p.disc(cx, cy, 0.46f, 0.25f, 0x7ACBEA)
                p.ball(cx + 0.05f, cy, 0.58f, 0.07f, 0xE8402A, 0.07f)
            }
            "d_flowercart" -> {
                p.box(cx - 0.34f, cy - 0.22f, 0.18f, cx + 0.34f, cy + 0.22f, 0.4f, 0x3A9A8A)
                p.cyl(cx - 0.18f, cy + 0.25f, 0.08f, 0.1f, 0.12f, DARK_WOOD); p.cyl(cx + 0.18f, cy + 0.25f, 0.08f, 0.1f, 0.12f, DARK_WOOD)
                for (i in 0..5) p.ball(cx - 0.25f + i * 0.1f, cy + (i % 2) * 0.1f - 0.05f, 0.5f, 0.1f, intArrayOf(0xE8302A, 0xF4D42A, 0xF88AB8, 0xA84AD8, 0xFFFFFF, 0xF08A1C)[i], 0.1f)
            }
            "d_arch" -> {
                p.box(cx - 0.4f, cy - 0.04f, 0f, cx - 0.33f, cy + 0.04f, 0.9f, 0xF4F0E4); p.box(cx + 0.33f, cy - 0.04f, 0f, cx + 0.4f, cy + 0.04f, 0.9f, 0xF4F0E4)
                for (i in 0..8) { val ang = 3.14159f * i / 8; p.ball(cx - cos(ang) * 0.4f, cy, 0.9f + sin(ang) * 0.35f, 0.07f, if (i % 2 == 0) a else 0x3F8F3A, 0.07f) }
                for (z in listOf(0.2f, 0.5f)) { p.ball(cx - 0.36f, cy + 0.02f, z, 0.07f, a, 0.07f); p.ball(cx + 0.36f, cy + 0.02f, z + 0.1f, 0.07f, a, 0.07f) }
            }
            "d_haystack" -> { p.cone(cx, cy, 0f, 0.8f, 0.4f, 0xD8B84A); p.ball(cx, cy, 0.0f, 0.4f, 0xD8B84A, 0.06f) }
            "d_fountain" -> fountain(p, cx, cy, if (w > 1) 0.85f else 0.4f, a, b, l.n)
            "d_well" -> {
                p.cyl(cx, cy, 0f, 0.4f, 0.3f, STONE); p.disc(cx, cy, 0.38f, 0.22f, 0x2A4A6A)
                p.box(cx - 0.3f, cy - 0.03f, 0.4f, cx - 0.25f, cy + 0.03f, 1.0f, DARK_WOOD); p.box(cx + 0.25f, cy - 0.03f, 0.4f, cx + 0.3f, cy + 0.03f, 1.0f, DARK_WOOD)
                p.gable(cx - 0.35f, cy - 0.15f, cx + 0.35f, cy + 0.15f, 0.98f, 0.28f, 0x8A3A32, 0xB8B0A0, true, 0.06f, 1)
                p.line(cx, cy, 0.95f, cx, cy, 0.55f, 0xC8B088, 1.2f); p.box(cx - 0.06f, cy - 0.06f, 0.42f, cx + 0.06f, cy + 0.06f, 0.55f, WOOD)
            }
            "d_signpost" -> {
                p.box(cx - 0.03f, cy - 0.03f, 0f, cx + 0.03f, cy + 0.03f, 0.9f, DARK_WOOD)
                p.face(0xC89A5A, cx + 0.03f, cy - 0.2f, 0.78f, cx + 0.03f, cy + 0.18f, 0.78f, cx + 0.03f, cy + 0.18f, 0.62f, cx + 0.03f, cy - 0.2f, 0.62f)
                p.face(0xB8885A, cx - 0.3f, cy + 0.04f, 0.58f, cx + 0.04f, cy + 0.04f, 0.58f, cx + 0.04f, cy + 0.04f, 0.43f, cx - 0.3f, cy + 0.04f, 0.43f)
            }
            "d_haybale" -> { p.cyl(cx, cy, 0f, 0.4f, 0.28f, 0xD8B84A); p.line(cx - 0.2f, cy + 0.2f, 0.2f, cx + 0.2f, cy + 0.2f, 0.2f, 0xA8801C, 1.2f) }
            "d_scarecrow" -> {
                p.box(cx - 0.03f, cy - 0.03f, 0f, cx + 0.03f, cy + 0.03f, 0.95f, DARK_WOOD)
                p.line(cx - 0.4f, cy, 0.68f, cx + 0.4f, cy, 0.68f, DARK_WOOD, 3f)
                p.box(cx - 0.15f, cy - 0.1f, 0.42f, cx + 0.15f, cy + 0.1f, 0.7f, 0x4A6AA8)
                p.ball(cx, cy, 0.86f, 0.14f, 0xF0D8A8, 0.14f)
                p.ball(cx, cy, 0.99f, 0.17f, 0xA8803A, 0.08f); p.cyl(cx, cy, 0.97f, 1.1f, 0.1f, 0x8A6A32)
                p.line(cx - 0.4f, cy, 0.68f, cx - 0.5f, cy, 0.5f, 0xD8B84A, 2f); p.line(cx + 0.4f, cy, 0.68f, cx + 0.5f, cy, 0.5f, 0xD8B84A, 2f)
            }
            "d_lamp" -> lamp(p, cx, cy, l.n)
            "d_bench" -> {
                p.box(cx - 0.4f, cy - 0.12f, 0.2f, cx + 0.4f, cy + 0.12f, 0.27f, a)
                p.box(cx - 0.4f, cy - 0.14f, 0.27f, cx + 0.4f, cy - 0.09f, 0.55f, a)
                p.box(cx - 0.36f, cy - 0.1f, 0f, cx - 0.3f, cy + 0.1f, 0.2f, p.right(a)); p.box(cx + 0.3f, cy - 0.1f, 0f, cx + 0.36f, cy + 0.1f, 0.2f, p.right(a))
            }
            "d_cart" -> {
                p.box(cx - 0.4f, cy - 0.25f, 0.2f, cx + 0.4f, cy + 0.25f, 0.4f, WOOD)
                p.ball(cx, cy, 0.5f, 0.3f, 0xD8B84A, 0.18f)
                p.cyl(cx - 0.2f, cy + 0.28f, 0.05f, 0.09f, 0.17f, DARK_WOOD); p.cyl(cx + 0.2f, cy + 0.28f, 0.05f, 0.09f, 0.17f, DARK_WOOD)
                p.line(cx - 0.4f, cy, 0.25f, cx - 0.7f, cy + 0.1f, 0.1f, DARK_WOOD, 2.4f)
            }
            "d_barrels" -> {
                p.cyl(cx - 0.18f, cy - 0.05f, 0f, 0.4f, 0.17f, WOOD); p.cyl(cx + 0.2f, cy + 0.05f, 0f, 0.38f, 0.16f, 0x8A5A32)
                p.cyl(cx, cy + 0.2f, 0f, 0.3f, 0.14f, 0xA87A44)
                for (z in listOf(0.1f, 0.3f)) p.line(cx - 0.35f, cy - 0.05f, z, cx - 0.01f, cy - 0.05f, z, 0x4A4A52, 1f)
            }
            "d_gazebo" -> {
                p.box(0.15f, 0.15f, 0f, w - 0.15f, d - 0.15f, 0.1f, 0xD8D2C4)
                for ((px, py) in listOf(0.3f to 0.3f, w - 0.3f to 0.3f, 0.3f to d - 0.3f, w - 0.3f to d - 0.3f)) p.cyl(px, py, 0.1f, 1.1f, 0.05f, 0xF4F0E4)
                p.pyramid(cx, cy, w / 2 - 0.05f, d / 2 - 0.05f, 1.05f, 0.7f, 0x3A8A7A, 4)
                p.ball(cx, cy, 1.8f, 0.07f, 0xE8B82A, 0.07f)
                p.box(cx - 0.3f, cy - 0.3f, 0.1f, cx + 0.3f, cy + 0.3f, 0.3f, 0xB8905A)
            }
            "d_pond" -> pond(p, w, d, l.n)
            "d_waterfall" -> {
                p.ball(0.7f, 0.7f, 0.5f, 0.75f, 0x8A867A, 0.8f)
                p.ball(1.0f, 0.5f, 0.9f, 0.5f, 0x9A968A, 0.6f)
                p.face(0x8AD8F2, 0.9f, 1.2f, 1.0f, 1.2f, 1.1f, 1.0f, 1.2f, 1.1f, 0.12f, 0.9f, 1.2f, 0.12f)
                p.disc(1.1f, 1.45f, 0.01f, 0.5f, 0x5AB8E0)
                p.dot(1.0f, 1.3f, 0.2f, 3f, 0xFFFFFF); p.dot(1.2f, 1.35f, 0.16f, 2.5f, 0xFFFFFF)
            }
            "d_stall" -> stall(p, cx, cy, a, l.n)
            "d_guardtower" -> {
                p.box(cx - 0.3f, cy - 0.3f, 0f, cx + 0.3f, cy + 0.3f, 1.35f, 0xA8A298)
                p.windowY(cy + 0.3f, cx, 0.8f, 0.1f, 0.25f, 0x2A3A4A, 0x6A665C)
                p.box(cx - 0.4f, cy - 0.4f, 1.35f, cx + 0.4f, cy + 0.4f, 1.47f, 0xB8B2A6)
                p.pyramid(cx, cy, 0.46f, 0.46f, 1.45f, 0.55f, 0x6A3A32, 3)
                p.line(cx, cy, 1.98f, cx, cy, 2.3f, DARK_WOOD, 1.4f); p.face(0xC8392E, cx, cy, 2.3f, cx + 0.22f, cy, 2.22f, cx, cy, 2.14f)
            }
            "d_gnome" -> {
                p.cyl(cx, cy, 0f, 0.3f, 0.12f, 0x4A8AD8); p.ball(cx, cy + 0.05f, 0.34f, 0.1f, 0xF0D8A8, 0.1f)
                p.cone(cx, cy, 0.36f, 0.34f, 0.11f, 0xE8302A); p.ball(cx, cy + 0.08f, 0.26f, 0.12f, 0xF4F0E4, 0.08f)
            }
            "d_stonearch" -> {
                p.box(cx - 0.42f, cy - 0.12f, 0f, cx - 0.22f, cy + 0.12f, 0.95f, STONE); p.box(cx + 0.22f, cy - 0.12f, 0f, cx + 0.42f, cy + 0.12f, 0.95f, STONE)
                p.box(cx - 0.47f, cy - 0.15f, 0.95f, cx + 0.47f, cy + 0.15f, 1.15f, Iso.shade(STONE, 0.08f))
                p.box(cx - 0.16f, cy - 0.12f, 0.8f, cx + 0.16f, cy + 0.12f, 0.95f, STONE)
            }
            "d_ruins" -> {
                p.box(0.3f, 0.3f, 0f, 1.7f, 0.5f, 0.55f, 0xA8A498)
                p.box(0.3f, 0.3f, 0f, 0.5f, 1.4f, 0.9f, 0xB8B2A6)
                p.cyl(1.4f, 1.3f, 0f, 1.1f, 0.14f, 0xC8C2B6); p.cyl(1.4f, 1.3f, 1.05f, 1.15f, 0.18f, 0xC8C2B6)
                p.cyl(0.9f, 1.5f, 0f, 0.35f, 0.14f, 0xB8B2A6)
                p.ball(0.7f, 0.9f, 0.1f, 0.2f, 0x5A8A3A, 0.12f)
            }
            "d_circle" -> for (k in 0..6) {
                val ang = k * 6.2832f / 7
                val sx = cx + cos(ang) * 0.75f; val sy = cy + sin(ang) * 0.75f
                p.face(Iso.shade(STONE, -0.1f), sx - 0.12f, sy, 0f, sx + 0.12f, sy, 0f, sx + 0.08f, sy, 0.8f + (k % 3) * 0.1f, sx - 0.08f, sy, 0.75f)
            }
            "d_tomb" -> {
                p.face(0x9A968A, cx - 0.26f, cy + 0.05f, 0f, cx - 0.04f, cy + 0.05f, 0f, cx - 0.04f, cy + 0.05f, 0.45f, cx - 0.15f, cy + 0.05f, 0.58f, cx - 0.26f, cy + 0.05f, 0.45f)
                p.box(cx + 0.08f, cy - 0.12f, 0f, cx + 0.34f, cy + 0.08f, 0.3f, 0xA8A498)
                p.line(cx - 0.15f, cy + 0.06f, 0.2f, cx - 0.15f, cy + 0.06f, 0.4f, 0x5A5A62, 1.4f)
            }
            "d_totem" -> {
                p.cyl(cx, cy, 0f, 1.4f, 0.17f, 0x9A6A3A)
                p.ball(cx, cy + 0.12f, 0.35f, 0.14f, 0xE8302A, 0.14f); p.ball(cx, cy + 0.12f, 0.75f, 0.14f, 0x3A9AD8, 0.14f); p.ball(cx, cy + 0.12f, 1.15f, 0.14f, 0xF2C230, 0.14f)
                p.line(cx - 0.35f, cy, 1.05f, cx + 0.35f, cy, 1.05f, 0x4AB8A0, 4f)
            }
            "d_chest" -> {
                p.box(cx - 0.28f, cy - 0.18f, 0f, cx + 0.28f, cy + 0.18f, 0.28f, 0x8A5A32)
                p.box(cx - 0.3f, cy - 0.2f, 0.28f, cx + 0.3f, cy + 0.2f, 0.4f, 0xA06A3A)
                p.faceY(cy + 0.2f, cx - 0.05f, cx + 0.05f, 0.2f, 0.42f, 0xE8B82A); p.faceY(cy + 0.18f, cx - 0.28f, cx + 0.28f, 0.1f, 0.14f, 0xE8B82A)
                p.ball(cx + 0.2f, cy + 0.22f, 0.12f, 0.07f, 0xF2C230, 0.05f)
            }
            "d_gold" -> { for ((gx, gy, gz, r) in listOf(listOf(0f, 0f, 0.12f, 0.35f), listOf(0.2f, 0.18f, 0.1f, 0.25f), listOf(-0.2f, 0.18f, 0.1f, 0.22f), listOf(0.02f, 0.05f, 0.32f, 0.2f))) p.ball(cx + gx, cy + gy, gz, r, 0xF2C230, r * 0.7f); p.dot(cx - 0.1f, cy + 0.1f, 0.4f, 2f, 0xFFFFFF) }
            "d_throne" -> {
                p.box(cx - 0.3f, cy - 0.28f, 0f, cx + 0.3f, cy + 0.28f, 0.3f, 0xA8483A)
                p.box(cx - 0.3f, cy - 0.28f, 0.3f, cx + 0.3f, cy - 0.15f, 1.2f, 0xE8B82A)
                p.box(cx - 0.3f, cy - 0.28f, 0.3f, cx - 0.2f, cy + 0.28f, 0.6f, 0xE8B82A); p.box(cx + 0.2f, cy - 0.28f, 0.3f, cx + 0.3f, cy + 0.28f, 0.6f, 0xE8B82A)
                p.box(cx - 0.22f, cy - 0.15f, 0.3f, cx + 0.22f, cy + 0.2f, 0.42f, 0xC8302A)
                p.ball(cx, cy - 0.22f, 1.27f, 0.09f, 0xC8302A, 0.09f)
            }
            "d_armor" -> {
                p.box(cx - 0.22f, cy - 0.15f, 0f, cx + 0.22f, cy + 0.15f, 0.08f, 0x6A4A2E)
                p.cyl(cx, cy, 0.08f, 0.6f, 0.17f, 0xA8B0BA); p.ball(cx, cy, 0.78f, 0.15f, 0xB8C0CA, 0.17f)
                p.line(cx - 0.04f, cy + 0.14f, 0.78f, cx + 0.04f, cy + 0.14f, 0.78f, 0x2A2A32, 1.4f)
                p.line(cx + 0.25f, cy, 0.2f, cx + 0.25f, cy, 1.0f, 0x6A4A2E, 2f)
            }
            "d_dummy" -> {
                p.box(cx - 0.03f, cy - 0.03f, 0f, cx + 0.03f, cy + 0.03f, 0.7f, DARK_WOOD); p.line(cx - 0.3f, cy, 0.55f, cx + 0.3f, cy, 0.55f, DARK_WOOD, 3f)
                p.cyl(cx, cy, 0.3f, 0.7f, 0.15f, 0xC8A870); p.ball(cx, cy, 0.78f, 0.14f, 0xC8A870, 0.14f)
                p.line(cx - 0.12f, cy + 0.15f, 0.5f, cx + 0.12f, cy + 0.15f, 0.5f, 0xE8302A, 1.6f)
            }
            "d_cannon" -> {
                p.cyl(cx - 0.18f, cy + 0.25f, 0.06f, 0.1f, 0.17f, DARK_WOOD); p.cyl(cx - 0.18f, cy - 0.25f, 0.06f, 0.1f, 0.17f, DARK_WOOD)
                p.line(cx - 0.35f, cy, 0.3f, cx + 0.4f, cy + 0.05f, 0.45f, 0x3A3A42, 8f)
                p.ball(cx + 0.45f, cy + 0.05f, 0.46f, 0.07f, 0x2A2A32, 0.07f)
                p.ball(cx + 0.35f, cy + 0.35f, 0.06f, 0.07f, 0x3A3A42, 0.07f); p.ball(cx + 0.2f, cy + 0.4f, 0.06f, 0.07f, 0x3A3A42, 0.07f)
            }
            "d_trebuchet" -> {
                p.box(0.4f, 0.8f, 0f, 1.6f, 1.2f, 0.12f, WOOD)
                p.line(0.9f, 1.0f, 0.1f, 1.0f, 1.0f, 1.3f, DARK_WOOD, 4f); p.line(1.2f, 1.0f, 0.1f, 1.0f, 1.0f, 1.3f, DARK_WOOD, 4f)
                p.line(1.0f, 1.0f, 1.3f, 0.5f, 1.0f, 1.9f, WOOD, 4f)
                p.line(1.0f, 1.0f, 1.3f, 1.4f, 1.0f, 0.9f, WOOD, 4f)
                p.box(0.3f, 0.92f, 1.55f, 0.55f, 1.08f, 1.9f, 0x6A665C)
                p.ball(1.5f, 1.0f, 0.8f, 0.1f, 0x4A4A52, 0.1f)
            }
            "d_portal" -> {
                p.cyl(cx, cy, 0f, 0.08f, 0.95f, 0x8A867A)
                p.oval(cx, cy, 1.05f, 34f, 56f, 0x5A3A8A); p.oval(cx, cy, 1.05f, 28f, 48f, 0x9A5AF0); p.oval(cx, cy, 1.05f, 16f, 32f, 0xD8B8FF)
                p.box(cx - 0.86f, cy - 0.1f, 0f, cx - 0.66f, cy + 0.1f, 1.45f, 0xA8A498); p.box(cx + 0.66f, cy - 0.1f, 0f, cx + 0.86f, cy + 0.1f, 1.45f, 0xA8A498)
                p.box(cx - 0.9f, cy - 0.12f, 1.4f, cx + 0.9f, cy + 0.12f, 1.6f, 0xB8B2A6)
                p.dot(cx - 0.3f, cy + 0.2f, 1.1f, 2f, 0xFFFFFF); p.dot(cx + 0.2f, cy + 0.2f, 0.8f, 2f, 0xFFFFFF)
            }
            "d_cauldron" -> {
                p.ball(cx, cy, 0.28f, 0.32f, 0x2A2A32, 0.26f); p.disc(cx, cy, 0.46f, 0.26f, 0x6AE05A)
                p.dot(cx - 0.08f, cy, 0.52f, 3f, 0xB8FFA8); p.dot(cx + 0.1f, cy + 0.05f, 0.62f, 2f, 0xB8FFA8)
                p.line(cx - 0.2f, cy + 0.25f, 0f, cx - 0.2f, cy + 0.4f, 0.12f, 0x2A2A32, 3f); p.line(cx + 0.2f, cy + 0.25f, 0f, cx + 0.2f, cy + 0.4f, 0.12f, 0x2A2A32, 3f)
                flame(p, cx, cy + 0.3f, 0.06f, 0.6f)
            }
            "d_crystalball" -> {
                p.cyl(cx, cy, 0f, 0.2f, 0.2f, 0x6A3A8A); p.ball(cx, cy, 0.5f, 0.24f, 0xB8E0FF, 0.24f); p.dot(cx - 0.08f, cy, 0.58f, 3f, 0xFFFFFF)
            }
            "d_sundial" -> {
                p.cyl(cx, cy, 0f, 0.3f, 0.2f, STONE); p.disc(cx, cy, 0.3f, 0.22f, 0xD8D2C4)
                p.face(0x8A6A32, cx, cy, 0.3f, cx + 0.2f, cy, 0.3f, cx, cy, 0.52f)
            }
            "d_obelisk" -> {
                p.face(0xC8C2B6, cx - 0.16f, cy + 0.1f, 0f, cx + 0.16f, cy + 0.1f, 0f, cx + 0.08f, cy + 0.04f, 1.5f, cx - 0.08f, cy + 0.04f, 1.5f)
                p.face(0x8A867A, cx + 0.16f, cy - 0.1f, 0f, cx + 0.16f, cy + 0.1f, 0f, cx + 0.08f, cy + 0.04f, 1.5f, cx + 0.08f, cy - 0.04f, 1.5f)
                p.face(0xE8B82A, cx - 0.08f, cy + 0.04f, 1.5f, cx + 0.08f, cy + 0.04f, 1.5f, cx, cy, 1.72f)
            }
            "d_crystal" -> {
                p.shadow(0.2f, 0.2f, 0.8f, 0.8f, 0.15f)
                for ((dx, dz, hh) in listOf(Triple(-0.12f, 0f, 0.7f), Triple(0.12f, 0f, 0.55f), Triple(0f, 0.05f, 1.0f))) {
                    p.face(a, cx + dx - 0.1f, cy + 0.04f, dz, cx + dx + 0.1f, cy + 0.04f, dz, cx + dx, cy, dz + hh)
                    p.face(Iso.shade(a, -0.3f), cx + dx + 0.1f, cy + 0.04f, dz, cx + dx + 0.1f, cy - 0.04f, dz, cx + dx, cy, dz + hh)
                }
                p.oval(cx, cy, 0.6f, 20f, 26f, Iso.withAlpha(a, 0.18f))
            }
            "d_sacks" -> { p.ball(cx - 0.15f, cy, 0.18f, 0.2f, 0xD8C090, 0.22f); p.ball(cx + 0.2f, cy + 0.05f, 0.16f, 0.18f, 0xC8A870, 0.2f); p.ball(cx, cy + 0.2f, 0.14f, 0.16f, 0xD8C090, 0.17f) }
            "d_crates" -> { p.box(cx - 0.3f, cy - 0.25f, 0f, cx + 0.05f, cy + 0.1f, 0.35f, 0xB8905A); p.box(cx + 0.05f, cy - 0.05f, 0f, cx + 0.38f, cy + 0.3f, 0.3f, 0xA8804A); p.box(cx - 0.2f, cy - 0.15f, 0.35f, cx + 0.05f, cy + 0.08f, 0.6f, 0xC8A06A) }
            "d_woodpile" -> { for (r in 0..2) for (c in 0..(3 - r)) p.cyl(cx - 0.3f + c * 0.17f + r * 0.08f, cy, r * 0.15f, r * 0.15f + 0.15f, 0.08f, 0xB88A52); p.line(cx - 0.3f, cy + 0.1f, 0.02f, cx + 0.3f, cy + 0.1f, 0.02f, DARK_WOOD, 1f) }
            "d_trough" -> { p.box(cx - 0.4f, cy - 0.14f, 0f, cx + 0.4f, cy + 0.14f, 0.26f, WOOD); p.top(cx - 0.35f, cy - 0.1f, cx + 0.35f, cy + 0.1f, 0.24f, 0x5AB8E0) }
            "d_coop" -> {
                p.box(cx - 0.36f, cy - 0.3f, 0f, cx + 0.36f, cy + 0.3f, 0.45f, 0xC84A32)
                p.gable(cx - 0.36f, cy - 0.3f, cx + 0.36f, cy + 0.3f, 0.45f, 0.3f, 0x8A5A32, 0xC84A32, true, 0.06f, 1)
                p.oval(cx, cy + 0.31f, 0.22f, 5f, 6f, 0x2A1E12)
                p.ball(cx + 0.4f, cy + 0.38f, 0.1f, 0.1f, 0xF4F0E4, 0.1f); p.dot(cx + 0.46f, cy + 0.38f, 0.2f, 2f, 0xE8302A)
            }
            "d_pen" -> pen(p, w, d, a, l.n)
            "d_dovecote" -> {
                p.cyl(cx, cy, 0f, 0.9f, 0.04f, DARK_WOOD)
                p.box(cx - 0.22f, cy - 0.22f, 0.9f, cx + 0.22f, cy + 0.22f, 1.35f, 0xF0E4C8)
                p.oval(cx + 0.05f, cy + 0.22f, 1.1f, 4f, 5f, 0x3A2E22)
                p.pyramid(cx, cy, 0.3f, 0.3f, 1.33f, 0.35f, 0xB8452E, 2)
                p.ball(cx + 0.28f, cy + 0.25f, 0.93f, 0.07f, 0xF4F0E4, 0.06f)
            }
            "d_carriage" -> {
                p.box(cx - 0.4f, cy - 0.2f, 0.18f, cx + 0.3f, cy + 0.2f, 0.62f, 0x8A2A3A)
                p.windowY(cy + 0.2f, cx - 0.1f, 0.42f, 0.2f, 0.2f, 0xBFE3F2, 0xE8B82A)
                p.box(cx - 0.42f, cy - 0.22f, 0.62f, cx + 0.32f, cy + 0.22f, 0.7f, 0xE8B82A)
                p.cyl(cx - 0.2f, cy + 0.25f, 0.04f, 0.1f, 0.18f, 0x4A3A22); p.cyl(cx + 0.18f, cy + 0.25f, 0.04f, 0.1f, 0.18f, 0x4A3A22)
                p.line(cx + 0.3f, cy, 0.3f, cx + 0.62f, cy, 0.22f, DARK_WOOD, 2.4f)
            }
            "d_boat" -> {
                p.shadow(0.2f, 0.2f, 0.8f, 0.8f, 0.1f)
                p.face(0x8A5A32, cx - 0.4f, cy - 0.12f, 0.12f, cx + 0.4f, cy - 0.12f, 0.12f, cx + 0.28f, cy + 0.12f, 0f, cx - 0.28f, cy + 0.12f, 0f)
                p.face(0xA87A44, cx - 0.4f, cy - 0.12f, 0.12f, cx + 0.4f, cy - 0.12f, 0.12f, cx + 0.5f, cy, 0.12f, cx - 0.5f, cy, 0.12f)
                p.line(cx - 0.2f, cy - 0.3f, 0.15f, cx + 0.1f, cy + 0.3f, 0.05f, DARK_WOOD, 2f)
            }
            "d_hotair" -> {
                p.box(cx - 0.17f, cy - 0.17f, 0f, cx + 0.17f, cy + 0.17f, 0.22f, 0x8A6A44)
                for (o in listOf(-0.14f, 0.14f)) p.line(cx + o, cy, 0.22f, cx + o * 2, cy, 1.0f, 0x5A4A32, 1.2f)
                p.ball(cx, cy, 1.55f, 0.55f, 0xE8402A, 0.65f)
                p.oval(cx, cy, 1.55f, 8f, 28f, 0xF4D42A)
                p.oval(cx, cy, 1.55f, 22f, 6f, 0x3A9AD8)
            }
            "d_blanket" -> {
                p.face(0xE8402A, cx - 0.4f, cy - 0.35f, 0.01f, cx + 0.4f, cy - 0.35f, 0.01f, cx + 0.4f, cy + 0.35f, 0.01f, cx - 0.4f, cy + 0.35f, 0.01f)
                for (i in 0..2) p.face(0xF4F0E4, cx - 0.4f + i * 0.27f, cy - 0.35f, 0.012f, cx - 0.4f + i * 0.27f + 0.13f, cy - 0.35f, 0.012f, cx - 0.4f + i * 0.27f + 0.13f, cy + 0.35f, 0.012f, cx - 0.4f + i * 0.27f, cy + 0.35f, 0.012f)
                p.ball(cx, cy, 0.1f, 0.15f, 0xA87A44, 0.1f); p.dot(cx + 0.15f, cy + 0.1f, 0.06f, 3.5f, 0xE8302A)
            }
            "d_pavilion" -> {
                p.box(0.2f, 0.2f, 0f, w - 0.2f, d - 0.2f, 0.08f, 0xB8905A)
                for ((px, py) in listOf(0.3f to 0.3f, w - 0.3f to 0.3f, 0.3f to d - 0.3f, w - 0.3f to d - 0.3f)) p.box(px - 0.04f, py - 0.04f, 0.08f, px + 0.04f, py + 0.04f, 1.0f, WOOD)
                p.pyramid(cx, cy, w / 2, d / 2, 0.98f, 0.5f, 0xC8392E, 3)
                p.box(cx - 0.5f, cy - 0.25f, 0.08f, cx + 0.5f, cy + 0.25f, 0.4f, 0xE8DCC0)
                p.dot(cx - 0.2f, cy, 0.45f, 3f, 0xE8402A); p.dot(cx + 0.2f, cy, 0.45f, 3f, 0xF4D42A)
            }
            "d_stage" -> {
                p.box(0.2f, 0.3f, 0f, w - 0.2f, d - 0.3f, 0.3f, 0x9A6A3A)
                p.box(0.2f, 0.3f, 0.3f, w - 0.2f, 0.4f, 1.5f, 0x7A2A3A)
                p.box(0.2f, 0.3f, 1.4f, w - 0.2f, d - 0.3f, 1.5f, 0xE8B82A)
                for (k in 0..5) p.face(0xA8384A, 0.3f + k * 0.28f, d - 0.3f, 1.4f, 0.5f + k * 0.28f, d - 0.3f, 1.4f, 0.4f + k * 0.28f, d - 0.3f, 1.05f, 0.3f + k * 0.28f, d - 0.3f, 1.4f)
                p.ball(cx, cy, 0.5f, 0.1f, 0xF0D8A8, 0.1f); p.cyl(cx, cy, 0.3f, 0.45f, 0.09f, 0x3A6AC8)
            }
            "d_bunting" -> {
                p.line(cx - 0.45f, cy, 0.9f, cx - 0.45f, cy, 0f, DARK_WOOD, 3f); p.line(cx + 0.45f, cy, 0.9f, cx + 0.45f, cy, 0f, DARK_WOOD, 3f)
                val cols = intArrayOf(0xE8302A, 0xF2C230, 0x3A9AD8, 0x4AD05A, 0xE8302A, 0xF2C230)
                var prevX = cx - 0.45f; var prevZ = 0.88f
                for (k in 0..5) {
                    val u = cx - 0.45f + 0.9f * (k + 1) / 6f
                    val z = 0.88f - sin(3.14159f * (k + 1) / 6f) * 0.12f
                    p.line(prevX, cy, prevZ, u, cy, z, 0xE8E0D0, 1f)
                    p.face(cols[k], u - 0.1f, cy, z, u, cy, z, u - 0.05f, cy, z - 0.18f)
                    prevX = u; prevZ = z
                }
            }
            "d_snowman" -> { p.ball(cx, cy, 0.18f, 0.24f, 0xF8FAFF, 0.22f); p.ball(cx, cy, 0.52f, 0.18f, 0xF8FAFF, 0.17f); p.ball(cx, cy, 0.8f, 0.13f, 0xF8FAFF, 0.13f); p.dot(cx + 0.02f, cy + 0.12f, 0.8f, 1.8f, 0xF08A1C); p.dot(cx - 0.05f, cy + 0.1f, 0.84f, 1.4f, 0x2A2A32); p.dot(cx + 0.05f, cy + 0.1f, 0.84f, 1.4f, 0x2A2A32); p.cyl(cx, cy, 0.9f, 1.05f, 0.1f, 0x2A2A32) }
            "d_jack" -> { p.ball(cx, cy, 0.22f, 0.3f, 0xF08A1C, 0.24f); p.box(cx - 0.03f, cy - 0.03f, 0.44f, cx + 0.03f, cy + 0.03f, 0.56f, 0x4F7A2A); p.oval(cx - 0.07f, cy + 0.26f, 0.28f, 3f, 4f, 0xFFE860); p.oval(cx + 0.08f, cy + 0.26f, 0.28f, 3f, 4f, 0xFFE860); p.oval(cx, cy + 0.27f, 0.16f, 7f, 3f, 0xFFE860) }
            "d_xmas" -> {
                p.cyl(cx, cy, 0f, 0.2f, 0.07f, DARK_WOOD)
                for (k in 0..3) p.cone(cx, cy, 0.15f + k * 0.38f, 0.65f - k * 0.08f, 0.5f - k * 0.1f, 0x2F7A4A)
                for ((i, col) in intArrayOf(0xE8302A, 0xF2C230, 0x3A9AD8, 0xE8302A, 0xF2C230).withIndex()) p.dot(cx - 0.2f + i * 0.1f, cy + 0.1f + (i % 2) * 0.04f, 0.3f + i * 0.2f, 2.4f, col)
                p.ball(cx, cy, 1.75f, 0.09f, 0xF2C230, 0.09f)
            }
            "d_statue" -> statue(p, cx, cy, l.n, w > 1, l.a)
            else -> p.ball(cx, cy, 0.3f, 0.3f, 0xC8A870, 0.3f)
        }
    }

    // ------------------------------------------------------------------ pièces communes

    private fun flame(p: Iso, x: Float, y: Float, z: Float, s: Float) {
        p.oval(x, y, z + 0.1f * s, 7f * s, 9f * s, 0xF08A1C)
        p.oval(x, y, z + 0.1f * s, 5f * s, 7f * s, 0xF8C23A)
        p.oval(x, y, z + 0.06f * s, 2.6f * s, 4f * s, 0xFFF4B0)
    }

    private fun fire(p: Iso, cx: Float, cy: Float, kind: Int) {
        when (kind) {
            1 -> { // brasero
                p.cyl(cx, cy, 0f, 0.5f, 0.05f, 0x3A3A42)
                p.cyl(cx, cy, 0.5f, 0.62f, 0.26f, 0x3A3A42)
                p.disc(cx, cy, 0.6f, 0.22f, 0x5A1A0A)
                flame(p, cx, cy, 0.62f, 1.4f)
            }
            2 -> { // grand feu
                for (k in 0..5) { val a = k * 1.047f; p.line(cx + cos(a) * 0.3f, cy + sin(a) * 0.3f, 0f, cx, cy, 0.4f, DARK_WOOD, 4f) }
                flame(p, cx, cy, 0.15f, 2.2f)
            }
            else -> { // feu de camp
                for (k in 0..4) { val a = k * 1.256f; p.ball(cx + cos(a) * 0.25f, cy + sin(a) * 0.25f, 0.05f, 0.07f, 0x8A867A, 0.06f) }
                p.line(cx - 0.2f, cy - 0.1f, 0.05f, cx + 0.2f, cy + 0.1f, 0.12f, DARK_WOOD, 4f)
                p.line(cx + 0.2f, cy - 0.1f, 0.05f, cx - 0.2f, cy + 0.1f, 0.12f, DARK_WOOD, 4f)
                flame(p, cx, cy, 0.1f, 1.2f)
            }
        }
    }

    private fun lamp(p: Iso, cx: Float, cy: Float, kind: Int) {
        p.cyl(cx, cy, 0f, 0.1f, 0.1f, 0x4A4A52)
        if (kind == 1) {
            p.cyl(cx, cy, 0.1f, 1.45f, 0.04f, 0x2A2A32)
            p.line(cx, cy, 1.2f, cx - 0.3f, cy, 1.25f, 0x2A2A32, 2f); p.line(cx, cy, 1.2f, cx + 0.3f, cy, 1.25f, 0x2A2A32, 2f)
            for (s in listOf(-0.3f, 0.3f)) { p.box(cx + s - 0.06f, cy - 0.06f, 1.25f, cx + s + 0.06f, cy + 0.06f, 1.45f, 0xFFE9A0); p.pyramid(cx + s, cy, 0.08f, 0.08f, 1.43f, 0.12f, 0x2A2A32) }
        } else {
            p.cyl(cx, cy, 0.1f, 1.05f, 0.04f, 0x2A2A32)
            p.box(cx - 0.08f, cy - 0.08f, 1.0f, cx + 0.08f, cy + 0.08f, 1.22f, 0xFFE9A0)
            p.pyramid(cx, cy, 0.11f, 0.11f, 1.2f, 0.14f, 0x2A2A32)
        }
    }

    private fun tent(p: Iso, cx: Float, cy: Float, cloth: Int, stripe: Int) {
        p.face(Iso.shade(cloth, -0.3f), cx + 0.42f, cy - 0.38f, 0f, cx + 0.42f, cy + 0.38f, 0f, cx, cy + 0.38f, 0.85f, cx, cy - 0.38f, 0.85f)
        p.face(cloth, cx - 0.42f, cy + 0.38f, 0f, cx + 0.42f, cy + 0.38f, 0f, cx, cy + 0.38f, 0.85f)
        p.face(0x2A1E12, cx - 0.12f, cy + 0.383f, 0f, cx + 0.12f, cy + 0.383f, 0f, cx, cy + 0.383f, 0.55f)
        p.line(cx, cy - 0.38f, 0.85f, cx, cy + 0.38f, 0.85f, stripe, 2.4f)
        p.line(cx, cy + 0.38f, 0.85f, cx, cy + 0.38f, 1.2f, DARK_WOOD, 1.6f)
        p.face(stripe, cx, cy + 0.38f, 1.2f, cx + 0.22f, cy + 0.38f, 1.12f, cx, cy + 0.38f, 1.04f)
    }

    private fun fountain(p: Iso, cx: Float, cy: Float, r: Float, stone: Int, water: Int, kind: Int) {
        p.cyl(cx, cy, 0f, 0.28f, r, stone)
        p.disc(cx, cy, 0.26f, r * 0.82f, water)
        p.cyl(cx, cy, 0.26f, 0.8f, r * 0.14f, stone)
        p.cyl(cx, cy, 0.7f, 0.78f, r * 0.45f, stone)
        p.disc(cx, cy, 0.78f, r * 0.38f, water)
        if (kind == 0) p.cyl(cx, cy, 0.78f, 1.25f, r * 0.08f, stone)
        for (k in 0..5) {
            val a = k * 1.047f
            p.dot(cx + cos(a) * r * 0.2f, cy + sin(a) * r * 0.2f, 0.95f - (k % 2) * 0.08f, 2.2f, 0xD8F4FF)
        }
        p.dot(cx, cy, 1.0f, 3f, 0xFFFFFF)
        p.oval(cx, cy, 0.3f, r * 38f, r * 12f, Iso.withAlpha(0xFFFFFF, 0.25f))
    }

    private fun pond(p: Iso, w: Float, d: Float, kind: Int) {
        val cx = w / 2; val cy = d / 2
        p.disc(cx, cy, 0f, 0.95f, 0x8A867A)
        p.disc(cx, cy, 0.01f, 0.82f, 0x3A8AB8)
        p.disc(cx - 0.1f, cy, 0.02f, 0.5f, 0x5AB0D8)
        if (kind == 0) {
            p.ball(cx - 0.3f, cy + 0.1f, 0.05f, 0.2f, 0x4F9D37, 0.03f)
            p.ball(cx + 0.35f, cy - 0.05f, 0.05f, 0.16f, 0x4F9D37, 0.03f)
            p.ball(cx - 0.3f, cy + 0.1f, 0.12f, 0.1f, 0x6AD04A, 0.1f); p.dot(cx - 0.34f, cy + 0.05f, 0.2f, 1.6f, 0xFFFFFF)
        } else {
            p.ball(cx, cy, 0.1f, 0.2f, 0xFFFFFF, 0.13f); p.ball(cx + 0.14f, cy + 0.06f, 0.26f, 0.09f, 0xFFFFFF, 0.1f); p.dot(cx + 0.24f, cy + 0.08f, 0.26f, 1.6f, 0xF08A1C)
            p.ball(cx - 0.5f, cy - 0.2f, 0.05f, 0.17f, 0x4F9D37, 0.03f)
        }
        for (k in 0..5) { val a = k * 1.047f + 0.3f; p.ball(cx + cos(a) * 0.88f, cy + sin(a) * 0.88f, 0.08f, 0.1f, 0x9A968A, 0.09f) }
    }

    private fun stall(p: Iso, cx: Float, cy: Float, cloth: Int, kind: Int) {
        p.box(cx - 0.36f, cy - 0.2f, 0f, cx + 0.36f, cy + 0.2f, 0.34f, WOOD)
        for (s in listOf(-0.34f, 0.34f)) { p.box(cx + s - 0.025f, cy - 0.2f, 0.34f, cx + s + 0.025f, cy - 0.16f, 0.9f, DARK_WOOD); p.box(cx + s - 0.025f, cy + 0.16f, 0.34f, cx + s + 0.025f, cy + 0.2f, 0.9f, DARK_WOOD) }
        when (kind) {
            1 -> for (i in 0..4) p.ball(cx - 0.25f + i * 0.125f, cy + 0.02f, 0.4f, 0.07f, intArrayOf(0xE8302A, 0xF08A1C, 0x6AD04A, 0xE8302A, 0xF4D42A)[i], 0.07f)
            2 -> for (i in 0..2) { p.oval(cx - 0.2f + i * 0.2f, cy, 0.38f, 7f, 3.4f, 0x9AB0C8) }
            else -> for (i in 0..3) p.ball(cx - 0.22f + i * 0.15f, cy, 0.4f, 0.07f, intArrayOf(0xF4F0E4, 0xE8A83A, 0xC8392E, 0x4A8AD8)[i], 0.07f)
        }
        for (i in 0..4) {
            val xa = cx - 0.42f + i * 0.168f
            p.face(if (i % 2 == 0) cloth else 0xF8F4EA, xa, cy + 0.0f, 0.95f, xa + 0.168f, cy, 0.95f, xa + 0.168f, cy + 0.3f, 0.78f, xa, cy + 0.3f, 0.78f)
        }
    }

    private fun pen(p: Iso, w: Float, d: Float, animal: Int, kind: Int) {
        val e = 0.1f
        p.top(e, e, w - e, d - e, 0.01f, 0x9AB070)
        val posts = ArrayList<Pair<Float, Float>>()
        val step = 0.5f
        var u = e
        while (u <= w - e + 0.01f) { posts.add(u to e); posts.add(u to d - e); u += step }
        var v = e + step
        while (v < d - e) { posts.add(e to v); posts.add(w - e to v); v += step }
        for (z in listOf(0.2f, 0.36f)) {
            p.line(e, e, z, w - e, e, z, DARK_WOOD, 1.8f); p.line(e, e, z, e, d - e, z, DARK_WOOD, 1.8f)
        }
        for ((x, y) in posts.sortedBy { it.first + it.second }) p.box(x - 0.025f, y - 0.025f, 0f, x + 0.025f, y + 0.025f, 0.42f, WOOD)
        for (z in listOf(0.2f, 0.36f)) {
            p.line(e, d - e, z, w - e, d - e, z, DARK_WOOD, 1.8f); p.line(w - e, e, z, w - e, d - e, z, DARK_WOOD, 1.8f)
        }
        val n = if (w > 1.5f) 3 else 1
        for (i in 0 until n) {
            val ax = w * (0.3f + 0.4f * ((i * 0.37f) % 1f)); val ay = d * (0.35f + 0.3f * ((i * 0.61f) % 1f))
            when (kind) {
                1 -> { p.ball(ax, ay, 0.2f, 0.2f, animal, 0.16f); p.ball(ax + 0.17f, ay + 0.04f, 0.27f, 0.1f, animal, 0.1f); p.dot(ax + 0.2f, ay + 0.06f, 0.26f, 1.6f, 0x2A2A32); p.ball(ax - 0.06f, ay + 0.04f, 0.2f, 0.12f, 0x2A2A32, 0.09f) }
                2 -> { p.ball(ax, ay, 0.28f, 0.22f, animal, 0.17f); p.ball(ax + 0.2f, ay + 0.05f, 0.4f, 0.1f, animal, 0.12f); for (s in listOf(-0.1f, 0.1f)) p.box(ax + s - 0.015f, ay - 0.015f, 0f, ax + s + 0.015f, ay + 0.015f, 0.2f, p.right(animal)) }
                else -> { p.ball(ax, ay, 0.12f, 0.15f, animal, 0.1f); p.ball(ax + 0.12f, ay, 0.18f, 0.07f, animal, 0.07f) }
            }
        }
        if (kind == 2) p.box(w - 0.45f, e + 0.05f, 0f, w - e - 0.05f, e + 0.3f, 0.2f, 0xD8B84A)
    }

    private fun statue(p: Iso, cx: Float, cy: Float, kind: Int, big: Boolean, material: Int = 0) {
        val s = if (big) 1.5f else 1f
        p.box(cx - 0.25f * s, cy - 0.25f * s, 0f, cx + 0.25f * s, cy + 0.25f * s, 0.32f * s, 0xA8A498)
        p.box(cx - 0.2f * s, cy - 0.2f * s, 0.32f * s, cx + 0.2f * s, cy + 0.2f * s, 0.4f * s, 0xB8B2A6)
        val st = if (material == 0) 0xD0CCC0 else material
        val z = 0.4f * s
        when (kind) {
            3 -> { // dragon
                p.ball(cx, cy, z + 0.2f * s, 0.2f * s, st, 0.15f * s); p.ball(cx + 0.15f * s, cy, z + 0.42f * s, 0.1f * s, st, 0.09f * s)
                p.face(st, cx - 0.1f, cy, z + 0.3f, cx - 0.4f * s, cy, z + 0.55f * s, cx - 0.05f, cy, z + 0.4f)
                p.line(cx - 0.2f * s, cy, z + 0.12f * s, cx - 0.4f * s, cy + 0.1f, z + 0.05f, st, 4f)
            }
            4 -> { // lion
                p.ball(cx, cy, z + 0.16f * s, 0.25f * s, st, 0.15f * s); p.ball(cx + 0.2f * s, cy, z + 0.34f * s, 0.14f * s, Iso.shade(st, 0.05f), 0.14f * s)
                p.ball(cx + 0.2f * s, cy, z + 0.34f * s, 0.2f * s, Iso.shade(st, -0.1f), 0.2f * s); p.ball(cx + 0.24f * s, cy, z + 0.34f * s, 0.11f * s, st, 0.11f * s)
            }
            5 -> { // ange
                p.cyl(cx, cy, z, z + 0.45f * s, 0.11f * s, st); p.ball(cx, cy, z + 0.55f * s, 0.1f * s, st, 0.1f * s)
                p.face(st, cx, cy - 0.02f, z + 0.4f * s, cx - 0.35f * s, cy - 0.02f, z + 0.65f * s, cx - 0.25f * s, cy - 0.02f, z + 0.25f * s)
                p.face(Iso.shade(st, -0.2f), cx, cy + 0.02f, z + 0.4f * s, cx + 0.3f * s, cy + 0.02f, z + 0.7f * s, cx + 0.22f * s, cy + 0.02f, z + 0.25f * s)
            }
            6 -> { // mage
                p.cyl(cx, cy, z, z + 0.5f * s, 0.13f * s, st); p.ball(cx, cy, z + 0.6f * s, 0.1f * s, st, 0.1f * s); p.cone(cx, cy, z + 0.62f * s, 0.32f * s, 0.14f * s, Iso.shade(st, -0.1f))
                p.line(cx + 0.18f * s, cy, z, cx + 0.18f * s, cy, z + 0.75f * s, 0x8A6A32, 2f); p.ball(cx + 0.18f * s, cy, z + 0.8f * s, 0.06f * s, 0x4AB8F0, 0.06f * s)
            }
            2 -> { // reine
                p.cone(cx, cy, z, 0.55f * s, 0.18f * s, st); p.ball(cx, cy, z + 0.62f * s, 0.09f * s, st, 0.09f * s)
                for (k in -1..1) p.face(0xE8B82A, cx + k * 0.05f * s - 0.02f, cy, z + 0.68f * s, cx + k * 0.05f * s + 0.02f, cy, z + 0.68f * s, cx + k * 0.05f * s, cy, z + 0.8f * s)
            }
            1 -> { // roi
                p.cyl(cx, cy, z, z + 0.6f * s, 0.15f * s, st); p.ball(cx, cy, z + 0.72f * s, 0.11f * s, st, 0.11f * s)
                for (k in -1..1) p.face(0xE8B82A, cx + k * 0.06f * s - 0.025f, cy, z + 0.8f * s, cx + k * 0.06f * s + 0.025f, cy, z + 0.8f * s, cx + k * 0.06f * s, cy, z + 0.95f * s)
                p.line(cx + 0.2f * s, cy, z, cx + 0.2f * s, cy, z + 0.85f * s, 0x8A6A32, 2.4f)
            }
            else -> { // chevalier
                p.cyl(cx, cy, z, z + 0.5f * s, 0.13f * s, st); p.ball(cx, cy, z + 0.6f * s, 0.1f * s, st, 0.11f * s)
                p.line(cx, cy + 0.12f * s, z + 0.6f * s, cx, cy + 0.12f * s, z + 0.66f * s, 0x2A2A32, 1.4f)
                p.line(cx + 0.2f * s, cy, z + 0.1f * s, cx + 0.2f * s, cy, z + 0.85f * s, 0xB8C0CA, 2.6f)
                p.box(cx - 0.2f * s, cy + 0.1f * s, z + 0.25f * s, cx - 0.04f * s, cy + 0.14f * s, z + 0.5f * s, 0xA8B0BA)
            }
        }
    }

    // ------------------------------------------------------------------ clôtures qui se raccordent

    /** Bits du masque : 1 = voisin en +x, 2 = en -x, 4 = en +y, 8 = en -y. */
    private fun fence(p: Iso, l: Look, mask: Int) {
        val cx = 0.5f; val cy = 0.5f
        val col = l.a
        val k = l.kind
        val solid = k == "f_hedge" || k == "f_wall"
        val th = if (k == "f_hedge") 0.2f else 0.11f
        val h = if (k == "f_hedge") 0.55f else 0.42f
        val m = if (mask == 0) 3 else mask
        // segments derrière le poteau, puis le poteau, puis les segments devant
        if (m and 2 != 0) segment(p, k, col, 0f, cx, cy - th, cy + th, solid, h, true)
        if (m and 8 != 0) segmentY(p, k, col, 0f, cy, cx - th, cx + th, solid, h, true)
        post(p, k, col, cx, cy, th, h)
        if (m and 1 != 0) segment(p, k, col, cx, 1f, cy - th, cy + th, solid, h, false)
        if (m and 4 != 0) segmentY(p, k, col, cy, 1f, cx - th, cx + th, solid, h, false)
    }

    private fun post(p: Iso, k: String, col: Int, cx: Float, cy: Float, th: Float, h: Float) {
        when (k) {
            "f_hedge" -> p.box(cx - th, cy - th, 0f, cx + th, cy + th, h, col)
            "f_wall" -> p.box(cx - th - 0.02f, cy - th - 0.02f, 0f, cx + th + 0.02f, cy + th + 0.02f, h + 0.06f, Iso.shade(col, 0.05f))
            "f_iron" -> { p.box(cx - 0.03f, cy - 0.03f, 0f, cx + 0.03f, cy + 0.03f, 0.62f, col); p.ball(cx, cy, 0.66f, 0.05f, 0xE8B82A, 0.05f) }
            "f_picket" -> p.box(cx - 0.045f, cy - 0.045f, 0f, cx + 0.045f, cy + 0.045f, 0.5f, col)
            "f_rope" -> { p.box(cx - 0.035f, cy - 0.035f, 0f, cx + 0.035f, cy + 0.035f, 0.5f, 0x6A4A2E); p.ball(cx, cy, 0.52f, 0.05f, 0xC8A878, 0.05f) }
            else -> p.box(cx - 0.045f, cy - 0.045f, 0f, cx + 0.045f, cy + 0.045f, 0.52f, Iso.shade(col, -0.1f))
        }
    }

    /** Segment le long de x, de [x0] à [x1]. */
    private fun segment(p: Iso, k: String, col: Int, x0: Float, x1: Float, ya: Float, yb: Float, solid: Boolean, h: Float, behind: Boolean) {
        val cy = (ya + yb) / 2
        if (solid) {
            p.box(x0, ya, 0f, x1, yb, h, col)
            if (k == "f_hedge") { var u = x0 + 0.1f; while (u < x1) { p.dot(u, yb, h * 0.6f, 3f, Iso.shade(col, 0.2f)); u += 0.17f } }
            else { p.line(x0, yb, h * 0.5f, x1, yb, h * 0.5f, Iso.shade(col, -0.35f), 1f) }
            return
        }
        when (k) {
            "f_picket" -> {
                p.line(x0, cy, 0.14f, x1, cy, 0.14f, Iso.shade(col, -0.2f), 1.8f); p.line(x0, cy, 0.34f, x1, cy, 0.34f, Iso.shade(col, -0.2f), 1.8f)
                var u = x0 + 0.08f
                while (u < x1) { p.face(col, u - 0.035f, cy, 0f, u + 0.035f, cy, 0f, u + 0.035f, cy, 0.4f, u, cy, 0.46f, u - 0.035f, cy, 0.4f); u += 0.14f }
            }
            "f_iron" -> {
                p.line(x0, cy, 0.12f, x1, cy, 0.12f, col, 2f); p.line(x0, cy, 0.5f, x1, cy, 0.5f, col, 2f)
                var u = x0 + 0.06f
                while (u < x1) { p.line(u, cy, 0f, u, cy, 0.58f, col, 1.5f); p.dot(u, cy, 0.6f, 1.6f, 0xE8B82A); u += 0.12f }
            }
            "f_rope" -> {
                val a = if (behind) 0.2f else 0.0f
                p.line(x0, cy, 0.42f, x1, cy, 0.34f - a * 0.0f, 0xC8A878, 2.2f)
                p.line(x0, cy, 0.3f, x1, cy, 0.22f, 0xC8A878, 1.6f)
            }
            else -> {
                p.line(x0, cy, 0.2f, x1, cy, 0.2f, col, 2.6f); p.line(x0, cy, 0.38f, x1, cy, 0.38f, col, 2.6f)
            }
        }
    }

    /** Segment le long de y, de [y0] à [y1]. */
    private fun segmentY(p: Iso, k: String, col: Int, y0: Float, y1: Float, xa: Float, xb: Float, solid: Boolean, h: Float, behind: Boolean) {
        val cx = (xa + xb) / 2
        if (solid) {
            p.box(xa, y0, 0f, xb, y1, h, col)
            if (k == "f_hedge") { var u = y0 + 0.1f; while (u < y1) { p.dot(xb, u, h * 0.6f, 3f, Iso.shade(col, 0.1f)); u += 0.17f } }
            return
        }
        when (k) {
            "f_picket" -> {
                p.line(cx, y0, 0.14f, cx, y1, 0.14f, Iso.shade(col, -0.35f), 1.8f); p.line(cx, y0, 0.34f, cx, y1, 0.34f, Iso.shade(col, -0.35f), 1.8f)
                var u = y0 + 0.08f
                while (u < y1) { p.face(p.right(col), cx, u - 0.035f, 0f, cx, u + 0.035f, 0f, cx, u + 0.035f, 0.4f, cx, u, 0.46f, cx, u - 0.035f, 0.4f); u += 0.14f }
            }
            "f_iron" -> {
                p.line(cx, y0, 0.12f, cx, y1, 0.12f, col, 2f); p.line(cx, y0, 0.5f, cx, y1, 0.5f, col, 2f)
                var u = y0 + 0.06f
                while (u < y1) { p.line(cx, u, 0f, cx, u, 0.58f, col, 1.5f); p.dot(cx, u, 0.6f, 1.6f, 0xE8B82A); u += 0.12f }
            }
            "f_rope" -> {
                p.line(cx, y0, 0.42f, cx, y1, 0.34f, 0xB89868, 2.2f)
                p.line(cx, y0, 0.3f, cx, y1, 0.22f, 0xB89868, 1.6f)
            }
            else -> {
                p.line(cx, y0, 0.2f, cx, y1, 0.2f, Iso.shade(col, -0.25f), 2.6f); p.line(cx, y0, 0.38f, cx, y1, 0.38f, Iso.shade(col, -0.25f), 2.6f)
            }
        }
    }
}
