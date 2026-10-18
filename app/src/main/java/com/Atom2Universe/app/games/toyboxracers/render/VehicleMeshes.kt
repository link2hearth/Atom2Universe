package com.Atom2Universe.app.games.toyboxracers.render

import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Petites carrosseries moulées : mêmes points de contact, silhouettes différentes. */
internal object VehicleMeshes {
    enum class Style { GT, RALLY, COUPE, ROADSTER, MUSCLE, COMPACT }

    private val rubber = tint(.065f, .075f, .105f)
    private val trim = tint(.12f, .15f, .21f)
    private val silver = tint(.73f, .82f, .91f)
    private val ivory = tint(1f, .94f, .79f)
    private val glass = tint(.10f, .24f, .34f)
    private val glassHighlight = tint(.38f, .70f, .78f)
    private val headlight = tint(1f, .94f, .72f)
    private val taillight = tint(1f, .075f, .14f)

    private data class Section(val z: Float, val width: Float, val bottom: Float, val top: Float)

    fun body(b: MeshBuilder, paint: FloatArray, style: Style) {
        val rally = style == Style.RALLY || style == Style.COMPACT
        val muscle = style == Style.MUSCLE
        val roofHeight = when (style) {
            Style.RALLY -> .76f
            Style.COMPACT -> .72f
            Style.ROADSTER -> .58f
            Style.COUPE -> .61f
            else -> .66f
        }
        val roofRear = if (rally) -.30f else if (style == Style.COUPE) -.25f else -.21f
        val roofFront = if (rally) .12f else if (style == Style.COUPE) .08f else .035f
        val rear = if (style == Style.COMPACT) -.64f else -.72f
        val nose = if (muscle) .77f else .73f
        val accent = if (style == Style.MUSCLE || style == Style.RALLY) ivory else trim
        val highlight = tint(paint[0] * .65f + .35f, paint[1] * .65f + .35f, paint[2] * .65f + .35f)

        loft(b, listOf(
            Section(rear, .31f, .13f, .29f),
            Section(-.49f, .43f, .10f, .39f),
            Section(-.22f, .405f, .10f, .40f),
            Section(.27f, .405f, .10f, .37f),
            Section(.49f, .43f, .12f, if (muscle) .38f else .34f),
            Section(nose, .32f, .15f, .27f)
        ), .055f, paint)
        // Bas de caisse, pare-chocs et diffuseur : une base sombre sous la peinture.
        b.box(0f, .125f, -.04f, .70f, .07f, 1.13f, trim)
        b.box(0f, .19f, nose + .006f, .53f, .075f, .035f, trim)
        b.box(0f, .19f, rear - .015f, .56f, .085f, .045f, trim)
        b.box(0f, .255f, nose + .012f, .25f, .065f, .035f, rubber)
        for (x in floatArrayOf(-.085f, 0f, .085f)) {
            b.box(x, .135f, rear - .025f, .024f, .07f, .12f, trim)
        }
        loft(b, listOf(
            Section(-.43f, .29f, .36f, .405f),
            Section(roofRear, .265f, .36f, roofHeight),
            Section(roofFront, .26f, .35f, roofHeight),
            Section(.30f, .31f, .345f, .395f)
        ), .035f, if (rally) ivory else highlight)

        // Vitrages inclinés, bordés par les montants de la cabine.
        val frontTopZ = roofFront + .025f
        val frontTopY = roofHeight - (roofHeight - .395f) * .025f / (.30f - roofFront) + .009f
        val frontBottomY = roofHeight - (roofHeight - .395f) * (.282f - roofFront) / (.30f - roofFront) + .009f
        b.quad(Vec3(-.233f, frontTopY, frontTopZ), Vec3(-.272f, frontBottomY, .282f),
            Vec3(.272f, frontBottomY, .282f), Vec3(.233f, frontTopY, frontTopZ), glass)
        val rearTopZ = roofRear - .025f
        val rearTopY = roofHeight - (roofHeight - .405f) * .025f / (roofRear + .43f) + .009f
        val rearBottomY = .405f + (roofHeight - .405f) * .022f / (roofRear + .43f) + .009f
        b.quad(Vec3(.25f, rearBottomY, -.408f), Vec3(-.25f, rearBottomY, -.408f),
            Vec3(-.235f, rearTopY, rearTopZ), Vec3(.235f, rearTopY, rearTopZ), glass)
        for (side in floatArrayOf(-1f, 1f)) {
            val points = listOf(Vec3(side * .303f, .425f, -.37f),
                Vec3(side * .273f, roofHeight - .055f, roofRear + .018f),
                Vec3(side * .268f, roofHeight - .055f, roofFront - .014f),
                Vec3(side * .309f, .415f, .252f))
            if (side > 0) b.quad(points[0], points[1], points[2], points[3], glass)
            else b.quad(points[3], points[2], points[1], points[0], glass)
            b.box(side * .299f, .49f, -.12f, .026f, .14f, .026f, highlight)
            b.box(side * .435f, .165f, -.02f, .035f, .065f, .58f, accent)
            b.box(side * .414f, .325f, -.12f, .024f, .025f, .095f, silver)
            b.box(side * .36f, .445f, .18f, .13f, .065f, .10f, paint)
            b.box(side * .365f, .445f, .126f, .09f, .041f, .012f, glassHighlight)
            for (z in PrototypeMeshFactory.CAR_WHEEL_Z) arch(b, side, z, paint)
            // Cadres des optiques et deux sorties d'échappement.
            b.box(side * .245f, .285f, nose - .014f, .15f, .10f, .07f, trim)
            b.box(side * .245f, .28f, rear - .005f, .17f, .09f, .05f, trim)
            b.box(side * .245f, .21f, rear - .047f, .10f, .075f, .09f, silver)
            b.box(side * .245f, .21f, rear - .097f, .067f, .047f, .012f, rubber)
        }
        // Deux bandes de course suivent réellement le capot incliné.
        for (x in floatArrayOf(-.075f, .075f)) {
            b.quad(Vec3(x - .027f, .373f, .30f), Vec3(x + .027f, .373f, .30f),
                Vec3(x + .027f, .289f, .68f), Vec3(x - .027f, .289f, .68f), accent)
            if (style != Style.ROADSTER) b.box(x, roofHeight + .004f, (roofRear + roofFront) * .5f,
                .054f, .012f, roofFront - roofRear, accent)
        }
        if (style == Style.GT || style == Style.RALLY) {
            for (x in floatArrayOf(-.26f, .26f)) b.box(x, .47f, -.57f, .035f, .19f, .045f, trim)
            loft(b, listOf(Section(-.65f, .47f, .55f, .59f), Section(-.48f, .43f, .53f, .56f)), .015f, accent)
        }
        if (style == Style.COUPE) b.box(0f, .415f, -.58f, .76f, .045f, .09f, paint)
        if (muscle) b.box(0f, .405f, .41f, .19f, .085f, .20f, trim)
        if (style == Style.ROADSTER) {
            for (x in floatArrayOf(-.14f, .14f)) {
                b.box(x, .62f, -.13f, .085f, .12f, .07f, trim)
                b.box(x, .65f, -.23f, .055f, .14f, .035f, silver)
            }
            b.box(0f, .589f, -.08f, .42f, .018f, .22f, rubber)
        }
        if (rally) {
            b.box(0f, roofHeight + .04f, -.09f, .39f, .045f, .075f, trim)
            for (x in floatArrayOf(-.14f, -.047f, .047f, .14f))
                b.box(x, roofHeight + .052f, -.047f, .068f, .03f, .016f, headlight)
        }
    }

    fun lights(style: Style, rear: Boolean, glow: Boolean = false): ColoredMesh {
        val b = MeshBuilder()
        val z = if (rear) (if (style == Style.COMPACT) -.673f else -.753f)
            else (if (style == Style.MUSCLE) .799f else .759f)
        for (side in floatArrayOf(-1f, 1f)) {
            if (glow) {
                val color = (if (rear) taillight else headlight).copyOf().apply { this[3] = if (rear) .16f else .25f }
                val edge = color.copyOf().apply { this[3] = 0f }
                val center = Vec3(side * .245f, if (rear) .28f else .285f, z + if (rear) -.012f else .012f)
                // Un dégradé radial à bord transparent, attaché à chaque optique.
                repeat(12) { i ->
                    fun p(angle: Float) = Vec3(center.x + cos(angle) * .105f,
                        center.y + sin(angle) * .073f, center.z)
                    val a = i * PI.toFloat() / 6f
                    b.coloredTriangle(center, p(a), p(a + PI.toFloat() / 6f), color, edge, edge)
                }
                continue
            }
            b.box(side * .245f, if (rear) .28f else .285f, z, .115f, .049f, .016f,
                if (rear) taillight else headlight)
            if (!rear) b.box(side * .245f, .253f, z + .002f, .126f, .011f, .018f, ivory)
        }
        return b.build()
    }

    fun wheel(b: MeshBuilder, x: Float, y: Float, z: Float) {
        b.cylinderX(x, y, z, .16f, .21f, 20, rubber)
        b.cylinderX(x, y, z, .172f, .155f, 20, trim)
        b.cylinderX(x, y, z, .178f, .13f, 20, silver)
        b.cylinderX(x, y, z, .183f, .108f, 20, rubber)
        for (side in floatArrayOf(-1f, 1f)) {
            val faceX = x + side * .094f
            repeat(5) { spoke ->
                val a = spoke * 2f * PI.toFloat() / 5f
                fun p(radius: Float, angle: Float) = Vec3(faceX, y + cos(angle) * radius, z + sin(angle) * radius)
                val a0 = p(.035f, a - .25f); val a1 = p(.119f, a + .03f)
                val a2 = p(.119f, a + .26f); val a3 = p(.035f, a + .25f)
                if (side > 0) b.quad(a0, a1, a2, a3, ivory) else b.quad(a3, a2, a1, a0, ivory)
            }
        }
        b.cylinderX(x, y, z, .196f, .041f, 10, silver)
    }

    /** Jets attachés aux pots, en volume ; origine Z sur la sortie d'échappement. */
    fun boostJets(): ColoredMesh {
        val b = MeshBuilder()
        for (x in floatArrayOf(-.245f, .245f)) {
            for (layer in 0..2) {
                val radius = .09f - layer * .023f
                val length = .73f - layer * .19f
                val color = when (layer) {
                    0 -> floatArrayOf(1f, .24f, .055f, .28f)
                    1 -> floatArrayOf(1f, .66f, .12f, .62f)
                    else -> floatArrayOf(1f, .96f, .70f, .92f)
                }
                repeat(8) { i ->
                    val a = i * PI.toFloat() / 4f
                    val c = (i + 1) * PI.toFloat() / 4f
                    b.triangle(Vec3(x + cos(a) * radius, .21f + sin(a) * radius, 0f),
                        Vec3(x + cos(c) * radius, .21f + sin(c) * radius, 0f),
                        Vec3(x, .21f, -length), color)
                }
            }
        }
        return b.build()
    }

    private fun arch(b: MeshBuilder, side: Float, z: Float, paint: FloatArray) {
        repeat(12) { segment ->
            val a = -PI.toFloat() * .5f + segment * PI.toFloat() / 12f
            val c = a + PI.toFloat() / 12f
            fun p(x: Float, radius: Float, angle: Float) = Vec3(side * x,
                PrototypeMeshFactory.CAR_WHEEL_Y + cos(angle) * radius, z + sin(angle) * radius)
            val points = listOf(p(.447f, .233f, a), p(.447f, .277f, a),
                p(.447f, .277f, c), p(.447f, .233f, c))
            if (side > 0) b.quad(points[0], points[1], points[2], points[3], paint)
            else b.quad(points[3], points[2], points[1], points[0], paint)
            if (side > 0) b.quad(p(.34f, .277f, c), p(.447f, .277f, c),
                p(.447f, .277f, a), p(.34f, .277f, a), paint)
            else b.quad(p(.34f, .277f, a), p(.447f, .277f, a),
                p(.447f, .277f, c), p(.34f, .277f, c), paint)
        }
    }

    private fun loft(b: MeshBuilder, sections: List<Section>, bevel: Float, color: FloatArray) {
        fun ring(s: Section): List<Vec3> {
            val edge = bevel.coerceAtMost((s.top - s.bottom) * .4f)
            return listOf(
            Vec3(-s.width, s.bottom + edge, s.z), Vec3(-s.width + bevel, s.bottom, s.z),
            Vec3(s.width - bevel, s.bottom, s.z), Vec3(s.width, s.bottom + edge, s.z),
            Vec3(s.width, s.top - edge, s.z), Vec3(s.width - bevel, s.top, s.z),
            Vec3(-s.width + bevel, s.top, s.z), Vec3(-s.width, s.top - edge, s.z))
        }
        val rings = sections.map(::ring)
        for (i in 0 until rings.lastIndex) for (j in 0..7) {
            val k = (j + 1) % 8
            b.quad(rings[i][j], rings[i][k], rings[i + 1][k], rings[i + 1][j], color)
        }
        for (j in 1..6) {
            b.triangle(rings.first()[0], rings.first()[j + 1], rings.first()[j], color)
            b.triangle(rings.last()[0], rings.last()[j], rings.last()[j + 1], color)
        }
    }

    private fun tint(r: Float, g: Float, b: Float) = floatArrayOf(r, g, b, 1f)
}
