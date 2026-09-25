package com.Atom2Universe.app.games.caves.render

import kotlin.math.*

/**
 * Matrices 4×4 rangées colonne par colonne, exactement comme OpenGL les attend.
 * Écrites à la main (sans android.opengl.Matrix) pour que les poses se testent sur ordinateur.
 */
internal object Mat4 {
    fun identity() = FloatArray(16).also { it[0] = 1f; it[5] = 1f; it[10] = 1f; it[15] = 1f }

    /** Renvoie a × b : b s'applique d'abord au point, puis a. */
    fun mul(a: FloatArray, b: FloatArray): FloatArray {
        val r = FloatArray(16)
        for (c in 0..3) for (row in 0..3) {
            var s = 0f
            for (k in 0..3) s += a[k * 4 + row] * b[c * 4 + k]
            r[c * 4 + row] = s
        }
        return r
    }

    /** m ← m × translation. */
    fun translate(m: FloatArray, x: Float, y: Float, z: Float) {
        for (i in 0..3) m[12 + i] += m[i] * x + m[4 + i] * y + m[8 + i] * z
    }

    /** m ← m × rotation (degrés) autour d'un axe du repère local. */
    fun rotate(m: FloatArray, deg: Float, axis: Int) {
        if (deg == 0f) return
        val a = Math.toRadians(deg.toDouble()); val c = cos(a).toFloat(); val s = sin(a).toFloat()
        val r = identity()
        when (axis) {
            0 -> { r[5] = c; r[6] = s; r[9] = -s; r[10] = c }
            1 -> { r[0] = c; r[2] = -s; r[8] = s; r[10] = c }
            else -> { r[0] = c; r[1] = s; r[4] = -s; r[5] = c }
        }
        System.arraycopy(mul(m, r), 0, m, 0, 16)
    }

    /** m ← m × agrandissement uniforme. */
    fun scale(m: FloatArray, k: Float) { for (i in 0..11) m[i] *= k }

    fun perspective(fovyDeg: Float, aspect: Float, near: Float, far: Float): FloatArray {
        val f = 1f / tan(Math.toRadians(fovyDeg / 2.0)).toFloat()
        return FloatArray(16).also {
            it[0] = f / aspect; it[5] = f
            it[10] = (far + near) / (near - far); it[11] = -1f
            it[14] = 2f * far * near / (near - far)
        }
    }
}

/** Tout ce qui se tient à une main et se dessine avec ce module (le reste garde ses propres poses). */
internal enum class HeldKind(val melee: Boolean) {
    PICKAXE(false), AXE(false), SHOVEL(false), HOE(false), SHEARS(false), FISHING_ROD(false),
    SWORD(true), SPEAR(true), HAMMER(true);

    companion object {
        fun forWeaponType(type: String?) = when (type) {
            "sword" -> SWORD; "spear" -> SPEAR; "hammer" -> HAMMER; "fishing_rod" -> FISHING_ROD
            else -> null
        }
    }
}

/** Couleurs du modèle : [head] = fer de l'outil ou lame, [accent] = garde et ligatures. */
internal data class HeldLook(val head: Int, val accent: Int = 0xBFA779, val handle: Int = 0x8A5A33)

/**
 * Pose d'un bras droit tenant l'objet, vue à la première personne.
 * Chaîne : épaule (hors écran) → rotation du bras → main → rotation du poignet → objet.
 * Les angles sont en degrés ; tangage + = lever vers soi, lacet + = vers la gauche,
 * roulis + = pointe de l'objet penchée vers la gauche.
 */
internal data class HeldPose(
    val x: Float = 0f, val y: Float = 0f, val z: Float = 0f,
    val armPitch: Float = 0f, val armYaw: Float = 0f, val armRoll: Float = 0f,
    val wristPitch: Float = 0f, val wristYaw: Float = 0f, val wristRoll: Float = 0f,
) {
    fun lerp(o: HeldPose, t: Float) = HeldPose(
        x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t,
        armPitch + (o.armPitch - armPitch) * t, armYaw + (o.armYaw - armYaw) * t, armRoll + (o.armRoll - armRoll) * t,
        wristPitch + (o.wristPitch - wristPitch) * t, wristYaw + (o.wristYaw - wristYaw) * t,
        wristRoll + (o.wristRoll - wristRoll) * t)
    operator fun plus(o: HeldPose) = HeldPose(x + o.x, y + o.y, z + o.z,
        armPitch + o.armPitch, armYaw + o.armYaw, armRoll + o.armRoll,
        wristPitch + o.wristPitch, wristYaw + o.wristYaw, wristRoll + o.wristRoll)
}

/** Ce que l'animation a besoin de savoir à cette image. Toutes les phases vont de 0 à 1. */
internal data class HeldState(
    val equip: Float = 1f,          // sortie de l'objet après un changement de main
    val use: Float = -1f,           // coup d'outil en cours (minage, labour), -1 au repos
    val attack: Float = -1f,        // coup de mêlée lancé, -1 au repos
    val charge: Float = 0f,         // armé du coup de mêlée (bouton tenu)
    val guard: Float = 0f,          // parade levée
    val bobX: Float = 0f, val bobY: Float = 0f,
)

internal object HeldItemPoses {
    // Épaule droite juste sous le bord de l'écran ; la main se trouve à SHOULDER + HAND.
    const val SHOULDER_X = .46f; const val SHOULDER_Y = -.70f; const val SHOULDER_Z = -.10f
    const val HAND_X = -.10f; const val HAND_Y = .36f; const val HAND_Z = -.58f

    /** Durées réelles des gestes (secondes), partagées avec le moteur de jeu. */
    fun useDuration(kind: HeldKind) = when (kind) { HeldKind.SHEARS -> .30f; else -> .42f }
    /** Instant du coup d'outil où le fer arrive en bas : c'est là que l'ennemi est touché. */
    const val USE_IMPACT = .50f
    fun attackDuration(kind: HeldKind) = when (kind) { HeldKind.HAMMER -> .60f; HeldKind.SPEAR -> .42f; else -> .40f }

    fun rest(kind: HeldKind) = when (kind) {
        HeldKind.SWORD -> HeldPose(wristPitch = -24f, wristYaw = 24f, wristRoll = 12f)
        HeldKind.SPEAR -> HeldPose(x = -.04f, y = .07f, wristPitch = -64f, wristYaw = 0f, wristRoll = 8f)
        HeldKind.HAMMER -> HeldPose(wristPitch = -22f, wristYaw = 62f, wristRoll = 10f)
        HeldKind.SHEARS -> HeldPose(y = .05f, z = .06f, wristPitch = -34f, wristYaw = 10f, wristRoll = 16f)
        HeldKind.FISHING_ROD -> HeldPose(wristPitch = -38f, wristYaw = 0f, wristRoll = 8f)
        else -> HeldPose(wristPitch = -28f, wristYaw = 62f, wristRoll = 10f)
    }

    private fun windup(kind: HeldKind) = when (kind) {
        // Lame ramenée au-dessus de l'épaule droite, pointe vers l'arrière-droite.
        HeldKind.SWORD -> HeldPose(x = .02f, y = .08f, z = .03f, armYaw = -4f,
            wristPitch = -4f, wristYaw = 24f, wristRoll = -22f)
        HeldKind.SPEAR -> HeldPose(x = .01f, y = .06f, z = .13f, wristPitch = -70f, wristRoll = 8f)
        HeldKind.HAMMER -> HeldPose(x = -.01f, y = .08f, wristPitch = 14f, wristYaw = 62f, wristRoll = 2f)
        else -> rest(kind)
    }

    private fun strikeEnd(kind: HeldKind) = when (kind) {
        // Fin de taille : poing passé au centre-bas, lame presque à plat vers la gauche.
        HeldKind.SWORD -> HeldPose(x = -.16f, y = -.08f, z = -.06f, armYaw = 8f,
            wristPitch = -50f, wristYaw = 24f, wristRoll = 84f)
        HeldKind.SPEAR -> HeldPose(x = -.12f, y = .10f, z = -.30f, wristPitch = -80f, wristRoll = 8f)
        HeldKind.HAMMER -> HeldPose(x = -.06f, y = -.12f, z = -.08f, wristPitch = -76f, wristYaw = 62f, wristRoll = 12f)
        else -> rest(kind)
    }

    private fun guardPose(kind: HeldKind) = when (kind) {
        HeldKind.SWORD -> HeldPose(x = -.12f, y = .10f, wristPitch = -12f, wristYaw = 30f, wristRoll = 82f)
        HeldKind.SPEAR -> HeldPose(x = -.14f, y = .12f, wristPitch = -8f, wristRoll = 86f)
        else -> HeldPose(x = -.12f, y = .10f, wristPitch = -10f, wristYaw = 70f, wristRoll = 78f)
    }

    /** Coup d'outil : on lève, on abat vite, on revient. */
    private fun usePose(kind: HeldKind, p: Float): HeldPose {
        val base = rest(kind)
        if (kind == HeldKind.SHEARS) {
            val k = sin(p * PI.toFloat())
            return base + HeldPose(x = -.03f * k, z = -.08f * k, wristRoll = -14f * k)
        }
        if (kind == HeldKind.FISHING_ROD) {
            val k = sin(p * PI.toFloat())
            return base + HeldPose(y = .04f * k, wristPitch = 30f * k)
        }
        val up = HeldPose(y = .04f, z = .02f, wristPitch = 26f, wristRoll = -4f)
        val down = HeldPose(x = -.04f, y = -.07f, z = -.07f, armYaw = 4f, wristPitch = -42f, wristRoll = 6f)
        val offset = when {
            p < .32f -> HeldPose().lerp(up, easeOut(p / .32f))
            p < .52f -> up.lerp(down, easeIn((p - .32f) / .20f))
            else -> down.lerp(HeldPose(), smooth((p - .52f) / .48f))
        }
        return base + offset
    }

    /** Coup de mêlée : court élan (si le bouton n'a pas été tenu), frappe rapide, retour. */
    private fun attackPose(kind: HeldKind, p: Float, fromCharge: Float): HeldPose {
        val start = rest(kind).lerp(windup(kind), fromCharge)
        val wind = windup(kind); val end = strikeEnd(kind)
        val lead = if (kind == HeldKind.HAMMER) .22f else .12f
        val hit = if (kind == HeldKind.HAMMER) .44f else .34f
        return when {
            p < lead -> start.lerp(wind, easeOut(p / lead))
            p < hit -> wind.lerp(end, easeIn((p - lead) / (hit - lead)))
            p < hit + .12f -> end
            else -> end.lerp(rest(kind), smooth((p - hit - .12f) / (1f - hit - .12f)))
        }
    }

    fun pose(kind: HeldKind, s: HeldState, chargeAtRelease: Float = 0f): HeldPose {
        var pose = when {
            s.attack >= 0f && kind.melee -> attackPose(kind, s.attack.coerceIn(0f, 1f), chargeAtRelease)
            s.use >= 0f -> usePose(kind, s.use.coerceIn(0f, 1f))
            kind.melee && s.charge > 0f -> {
                // Tremblement léger quand l'armé est au maximum : on sent la tension.
                val c = smooth(s.charge)
                rest(kind).lerp(windup(kind), c) + HeldPose(armRoll = if (s.charge >= .99f) sin(s.bobX * 900f) * .6f else 0f)
            }
            else -> rest(kind)
        }
        if (s.guard > 0f) pose = pose.lerp(guardPose(kind), smooth(s.guard))
        val out = 1f - smooth(s.equip)
        return pose + HeldPose(x = s.bobX + .06f * out, y = s.bobY - .40f * out, wristPitch = -25f * out)
    }

    /** Matrice du bras (repère vue) : l'origine est l'épaule, la main est à (HAND_X, HAND_Y, HAND_Z). */
    fun armMatrix(p: HeldPose): FloatArray {
        val m = Mat4.identity()
        Mat4.translate(m, SHOULDER_X + p.x, SHOULDER_Y + p.y, SHOULDER_Z + p.z)
        Mat4.rotate(m, p.armYaw, 1); Mat4.rotate(m, p.armPitch, 0); Mat4.rotate(m, p.armRoll, 2)
        return m
    }

    /** Matrice de l'objet, exprimée dans le repère du bras. */
    fun gripMatrix(p: HeldPose): FloatArray {
        val m = Mat4.identity()
        Mat4.translate(m, HAND_X, HAND_Y, HAND_Z)
        Mat4.rotate(m, p.wristRoll, 2); Mat4.rotate(m, p.wristPitch, 0); Mat4.rotate(m, p.wristYaw, 1)
        return m
    }

    fun smooth(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * (3f - 2f * x) }
    private fun easeIn(t: Float): Float { val x = t.coerceIn(0f, 1f); return x * x * x }
    private fun easeOut(t: Float): Float { val x = 1f - t.coerceIn(0f, 1f); return 1f - x * x }
}

/** Géométrie des outils et armes de mêlée. Poignée à l'origine, manche vers +Y, travail vers -Z. */
internal object HeldItemModels {
    private const val SKIN = 0xD5A17C
    private const val SKIN_DARK = 0xC08B68
    private const val SLEEVE = 0x435B78
    private const val LEATHER = 0x4A3326

    /** Image complète en repère vue (bras + poing + objet), prête pour la projection du viewmodel. */
    fun fpsFrame(m: HeldEquipmentMesh, kind: HeldKind, look: HeldLook, pose: HeldPose) {
        m.clear()
        val arm = HeldItemPoses.armMatrix(pose)
        fpsArm(m)
        m.transform(0, arm)
        val start = m.count
        build(m, kind, look)
        fist(m)
        m.transform(start, Mat4.mul(arm, HeldItemPoses.gripMatrix(pose)))
    }

    /** Bras vu à la première personne : manche, avant-bras et poing fermé sur le manche. */
    fun fpsArm(m: HeldEquipmentMesh) {
        val hx = HeldItemPoses.HAND_X; val hy = HeldItemPoses.HAND_Y; val hz = HeldItemPoses.HAND_Z
        // Le coude se place au deux tiers du trajet, légèrement vers l'extérieur.
        val ex = hx * .45f + .03f; val ey = hy * .45f - .02f; val ez = hz * .45f
        m.rod(0f, 0f, 0f, ex, ey, ez, .085f, SLEEVE, .075f)
        m.rod(ex, ey, ez, hx + .012f, hy - .045f, hz + .035f, .058f, SKIN, .046f)
        m.box(ex, ey, ez, .07f, .07f, .07f, SLEEVE)
    }

    /** Poing autour du manche, dans le repère de l'objet (dessiné avec lui, il suit le poignet). */
    fun fist(m: HeldEquipmentMesh) {
        m.box(.006f, 0f, .006f, .036f, .044f, .036f, SKIN)
        for (i in 0..3) m.box(-.030f, -.030f + i * .020f, -.008f, .012f, .0085f, .03f, SKIN_DARK)
        m.box(.010f, .030f, -.030f, .020f, .012f, .014f, SKIN)
    }

    fun build(m: HeldEquipmentMesh, kind: HeldKind, look: HeldLook) {
        val head = look.head; val wood = look.handle; val accent = look.accent
        when (kind) {
            HeldKind.PICKAXE -> {
                handle(m, wood, .44f)
                m.box(0f, .44f, 0f, .024f, .028f, .03f, darker(head))
                for (side in listOf(-1f, 1f)) {
                    // Deux pointes qui retombent en s'effilant.
                    m.rod(0f, .45f, side * .02f, 0f, .455f, side * .11f, .026f, head, .021f)
                    m.rod(0f, .455f, side * .11f, 0f, .425f, side * .19f, .021f, head, .010f)
                    m.rod(0f, .425f, side * .19f, 0f, .40f, side * .225f, .010f, lighter(head), .002f)
                }
            }
            HeldKind.AXE -> {
                handle(m, wood, .45f)
                m.box(0f, .41f, .005f, .022f, .045f, .03f, darker(head))
                m.box(0f, .416f, -.055f, .017f, .05f, .03f, head)
                // Tranchant évasé vers l'avant.
                m.box(0f, .416f, -.095f, .014f, .066f, .022f, head)
                m.box(0f, .416f, -.121f, .008f, .076f, .006f, lighter(head))
                m.box(0f, .42f, .042f, .02f, .024f, .012f, darker(head))
            }
            HeldKind.SHOVEL -> {
                handle(m, wood, .34f)
                m.box(0f, .34f, 0f, .026f, .02f, .026f, darker(head))
                m.taper(0f, .35f, .38f, 0f, .02f, .012f, .062f, .010f, head)
                m.box(0f, .44f, 0f, .062f, .06f, .010f, head)
                m.taper(0f, .50f, .54f, 0f, .062f, .010f, .03f, .006f, lighter(head))
                m.box(0f, -.105f, 0f, .045f, .014f, .016f, wood)
            }
            HeldKind.HOE -> {
                handle(m, wood, .46f)
                m.rod(0f, .45f, .02f, 0f, .46f, -.10f, .018f, head, .016f)
                m.box(0f, .425f, -.115f, .05f, .045f, .010f, head)
                m.box(0f, .378f, -.115f, .05f, .006f, .011f, lighter(head))
            }
            HeldKind.SHEARS -> {
                for (side in listOf(-1f, 1f)) {
                    m.rod(0f, .05f, 0f, side * .026f, .21f, -.004f, .016f, head, .004f)
                    m.rod(0f, .03f, 0f, side * .03f, -.045f, 0f, .012f, accent)
                    m.box(side * .036f, -.06f, 0f, .018f, .022f, .008f, accent)
                }
                m.box(0f, .045f, 0f, .012f, .012f, .012f, darker(head))
            }
            HeldKind.FISHING_ROD -> {
                m.rod(0f, -.12f, 0f, 0f, .08f, 0f, .024f, LEATHER, .022f)
                m.rod(0f, .08f, 0f, 0f, .78f, -.08f, .016f, wood, .006f)
                m.box(.03f, .02f, -.02f, .034f, .034f, .02f, 0x9AA9B1)
                m.rod(0f, .78f, -.08f, 0f, .30f, -.12f, .0025f, 0xDDDFC9)
            }
            HeldKind.SWORD -> {
                m.rod(0f, -.075f, 0f, 0f, .07f, 0f, .022f, LEATHER, .021f)
                for (i in 0..3) m.rod(0f, -.058f + i * .034f, 0f, 0f, -.050f + i * .034f, 0f, .025f, darker(LEATHER))
                m.box(0f, -.095f, 0f, .03f, .022f, .03f, accent)
                m.box(0f, .085f, 0f, .088f, .018f, .03f, accent)
                m.box(0f, .085f, 0f, .02f, .026f, .034f, darker(accent))
                m.taper(0f, .10f, .47f, 0f, .036f, .010f, .031f, .008f, head)
                m.box(0f, .27f, 0f, .006f, .15f, .0115f, darker(head))
                m.taper(0f, .47f, .57f, 0f, .031f, .008f, .002f, .002f, lighter(head))
            }
            HeldKind.SPEAR -> {
                m.rod(0f, -.22f, 0f, 0f, .74f, 0f, .019f, wood, .016f)
                for (i in 0..2) m.rod(0f, .70f + i * .018f, 0f, 0f, .712f + i * .018f, 0f, .022f, accent)
                m.taper(0f, .74f, .80f, 0f, .012f, .012f, .036f, .010f, head)
                m.taper(0f, .80f, .95f, 0f, .036f, .010f, .002f, .002f, head)
                m.box(0f, .87f, 0f, .004f, .05f, .012f, lighter(head))
                m.box(0f, -.03f, 0f, .026f, .07f, .026f, LEATHER)
            }
            HeldKind.HAMMER -> {
                handle(m, wood, .37f)
                m.box(0f, .40f, 0f, .048f, .055f, .09f, head)
                m.box(0f, .40f, -.095f, .054f, .061f, .01f, lighter(head))
                m.box(0f, .40f, .095f, .054f, .061f, .01f, lighter(head))
                m.box(0f, .40f, 0f, .052f, .016f, .055f, accent)
            }
        }
    }

    private fun handle(m: HeldEquipmentMesh, wood: Int, top: Float) {
        m.rod(0f, -.10f, 0f, 0f, top, 0f, .020f, wood, .018f)
        m.rod(0f, -.10f, 0f, 0f, -.085f, 0f, .023f, darker(wood))
    }

    fun darker(c: Int) = shade(c, .72f)
    fun lighter(c: Int) = shade(c, 1.22f)
    private fun shade(c: Int, k: Float): Int {
        fun ch(v: Int) = (v * k).roundToInt().coerceIn(0, 255)
        return (ch((c shr 16) and 255) shl 16) or (ch((c shr 8) and 255) shl 8) or ch(c and 255)
    }
}
