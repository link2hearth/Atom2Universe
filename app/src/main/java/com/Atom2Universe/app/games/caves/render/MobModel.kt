package com.Atom2Universe.app.games.caves.render

/**
 * Modèles de mobs voxel (assemblages de boîtes type Minecraft).
 *
 * Espace local : origine au centre des pieds, +Y vers le haut, +Z vers l'avant
 * (direction du regard / de marche), +X vers la droite. Unités « voxel » ;
 * le rendu mappe [MobModel.heightVox] sur la hauteur monde du mob.
 *
 * Aucune dépendance Android : géométrie pure, testable, sans GL.
 */
internal enum class Limb { NONE, LEG, ARM }

/** Une boîte du modèle. Tailles pleines (w,h,d), centre (cx,cy,cz). */
internal class MobPart(
    val cx: Float, val cy: Float, val cz: Float,
    val w: Float, val h: Float, val d: Float,
    val color: Int,
    val limb: Limb = Limb.NONE,
    /** -1 = gauche, +1 = droite : déphase le balancement des membres. */
    val side: Int = 0,
    /** Hauteur du pivot de balancement (épaule / hanche). */
    val pivotY: Float = cy + h * 0.5f,
    /** Inclinaison statique autour de X (degrés) : pose figée (bras zombie tendus…). */
    val baseTiltDeg: Float = 0f,
    /** Pièce auto-éclairée : ignore l'ombrage par face et la teinte de niveau (yeux, lueurs). */
    val emissive: Boolean = false
)

internal class MobModel(
    val parts: List<MobPart>,
    val heightVox: Float,
    /** Corps gélatineux : écrasement/étirement vertical animé (slime). */
    val squash: Boolean = false,
    /** Flotte : oscillation verticale, pas de contact sol (spectre). */
    val floats: Boolean = false
)

internal object MobModels {

    private fun p(
        cx: Float, cy: Float, cz: Float, w: Float, h: Float, d: Float, color: Int,
        limb: Limb = Limb.NONE, side: Int = 0,
        pivotY: Float = cy + h * 0.5f, baseTiltDeg: Float = 0f, emissive: Boolean = false
    ) = MobPart(cx, cy, cz, w, h, d, color, limb, side, pivotY, baseTiltDeg, emissive)

    private val models: Map<String, MobModel> by lazy {
        mapOf(
            "zombie"   to zombie(),
            "skeleton" to skeleton(),
            "ogre"     to ogre(),
            "dwarf"    to dwarf(),
            "goblin"   to goblin(),
            "troll"    to troll(),
            "golem"    to golem(),
            "wraith"   to wraith(),
            "spider"   to spider(),
            "imp"      to imp(),
            "mummy"    to mummy(),
            "slime"    to slime()
        )
    }

    fun get(model: String): MobModel = models[model] ?: models.getValue("slime")

    /** Hauteur voxel de référence : [MobModel.heightVox] est mappé sur `baseScale × 2`. */
    const val REF_VOX = 30f

    /** Hauteur monde réelle du modèle rendu (sert à la hitbox et au placement de la barre de vie). */
    fun bodyHeightWorld(model: String, baseScale: Float): Float =
        get(model).heightVox * (baseScale * 2f / REF_VOX)

    // ── Humanoïdes ──────────────────────────────────────────────────────────

    private fun zombie(): MobModel {
        val skin = 0xFF6E8B57.toInt(); val shirt = 0xFF3B4A2A.toInt(); val pants = 0xFF2B3340.toInt()
        return MobModel(listOf(
            p(-3f, 6f, 0f, 4f, 12f, 4f, pants, Limb.LEG, -1),
            p( 3f, 6f, 0f, 4f, 12f, 4f, pants, Limb.LEG,  1),
            p( 0f, 17f, 0f, 10f, 11f, 6f, shirt),
            // Bras tendus vers l'avant — pose zombie iconique
            p(-7f, 16f, 0f, 3f, 12f, 3f, skin, Limb.ARM, -1, pivotY = 22f, baseTiltDeg = -78f),
            p( 7f, 16f, 0f, 3f, 12f, 3f, skin, Limb.ARM,  1, pivotY = 22f, baseTiltDeg = -78f),
            p( 0f, 26.5f, 0f, 8f, 8f, 8f, skin),
            p(-2f, 27f, 4f, 1.6f, 1.6f, 1f, 0xFF1A1A12.toInt(), emissive = true),
            p( 2f, 27f, 4f, 1.6f, 1.6f, 1f, 0xFF1A1A12.toInt(), emissive = true)
        ), heightVox = 30.5f)
    }

    private fun skeleton(): MobModel {
        val bone = 0xFFE6E2D3.toInt(); val socket = 0xFF14141C.toInt()
        return MobModel(listOf(
            p(-2.5f, 6f, 0f, 2.5f, 12f, 2.5f, bone, Limb.LEG, -1),
            p( 2.5f, 6f, 0f, 2.5f, 12f, 2.5f, bone, Limb.LEG,  1),
            p( 0f, 16.5f, 0f, 7f, 10f, 4f, bone),
            // Cage thoracique suggérée par une bande sombre
            p( 0f, 15f, 2.2f, 7.2f, 6f, 0.6f, 0xFFCFC9B6.toInt()),
            p(-6f, 16f, 0f, 2f, 12f, 2f, bone, Limb.ARM, -1, pivotY = 22f),
            p( 6f, 16f, 0f, 2f, 12f, 2f, bone, Limb.ARM,  1, pivotY = 22f),
            p( 0f, 26.5f, 0f, 7f, 7f, 7f, bone),
            p(-1.8f, 27f, 3.6f, 1.8f, 2f, 1f, socket, emissive = true),
            p( 1.8f, 27f, 3.6f, 1.8f, 2f, 1f, socket, emissive = true)
        ), heightVox = 30f)
    }

    private fun ogre(): MobModel {
        val skin = 0xFF8AA15A.toInt(); val belly = 0xFF7C9150.toInt()
        val loin = 0xFF5A4632.toInt(); val tusk = 0xFFF0EAD0.toInt()
        return MobModel(listOf(
            p(-5f, 7f, 0f, 6f, 14f, 6f, skin, Limb.LEG, -1),
            p( 5f, 7f, 0f, 6f, 14f, 6f, skin, Limb.LEG,  1),
            p( 0f, 13f, 1f, 16f, 5f, 12f, loin),               // pagne
            p( 0f, 22f, 0f, 18f, 14f, 13f, belly),             // gros bidon
            p(-11f, 22f, 0f, 5f, 16f, 5f, skin, Limb.ARM, -1, pivotY = 30f, baseTiltDeg = 12f),
            p( 11f, 22f, 0f, 5f, 16f, 5f, skin, Limb.ARM,  1, pivotY = 30f, baseTiltDeg = 12f),
            p( 0f, 32f, 0f, 9f, 8f, 9f, skin),                 // petite tête enfoncée
            p(-2.5f, 29.5f, 4.5f, 1.6f, 2.6f, 1.6f, tusk, emissive = true),
            p( 2.5f, 29.5f, 4.5f, 1.6f, 2.6f, 1.6f, tusk, emissive = true),
            p(-2.2f, 33f, 4.2f, 1.4f, 1.4f, 1f, 0xFF20240F.toInt(), emissive = true),
            p( 2.2f, 33f, 4.2f, 1.4f, 1.4f, 1f, 0xFF20240F.toInt(), emissive = true)
        ), heightVox = 36f)
    }

    private fun dwarf(): MobModel {
        val skin = 0xFFC8A07A.toInt(); val beard = 0xFF8A4B2A.toInt()
        val tunic = 0xFF5A3A6A.toInt(); val helmet = 0xFF9AA0A8.toInt(); val boot = 0xFF3A2A1E.toInt()
        return MobModel(listOf(
            p(-3.5f, 4f, 0f, 5f, 8f, 5f, boot, Limb.LEG, -1),
            p( 3.5f, 4f, 0f, 5f, 8f, 5f, boot, Limb.LEG,  1),
            p( 0f, 13f, 0f, 12f, 10f, 8f, tunic),              // torse trapu
            p(-8f, 12f, 0f, 4f, 10f, 4f, skin, Limb.ARM, -1, pivotY = 18f),
            p( 8f, 12f, 0f, 4f, 10f, 4f, skin, Limb.ARM,  1, pivotY = 18f),
            p( 0f, 21.5f, 0f, 8f, 7f, 8f, skin),               // tête
            p( 0f, 18f, 3.5f, 8f, 8f, 3f, beard),              // grosse barbe
            p( 0f, 26f, 0f, 9f, 4f, 9f, helmet),               // casque
            p( 0f, 29f, 0f, 1.6f, 3f, 1.6f, helmet),           // pointe de casque
            p(-2f, 22.5f, 4f, 1.4f, 1.4f, 1f, 0xFF14100A.toInt(), emissive = true),
            p( 2f, 22.5f, 4f, 1.4f, 1.4f, 1f, 0xFF14100A.toInt(), emissive = true)
        ), heightVox = 31f)
    }

    private fun goblin(): MobModel {
        val skin = 0xFF6FA84B.toInt(); val loin = 0xFF7A5A38.toInt()
        return MobModel(listOf(
            p(-2.5f, 5f, 0f, 3f, 10f, 3f, skin, Limb.LEG, -1),
            p( 2.5f, 5f, 0f, 3f, 10f, 3f, skin, Limb.LEG,  1),
            p( 0f, 13f, 1f, 8f, 7f, 5f, loin),                 // torse voûté en avant
            p(-5.5f, 12f, 1f, 2.5f, 9f, 2.5f, skin, Limb.ARM, -1, pivotY = 17f, baseTiltDeg = -20f),
            p( 5.5f, 12f, 1f, 2.5f, 9f, 2.5f, skin, Limb.ARM,  1, pivotY = 17f, baseTiltDeg = -20f),
            p( 0f, 20f, 1.5f, 7f, 7f, 7f, skin),               // grosse tête penchée
            p(-6f, 21f, 0f, 4f, 2.5f, 2f, skin, baseTiltDeg = 35f),   // oreille pointue G
            p( 6f, 21f, 0f, 4f, 2.5f, 2f, skin, baseTiltDeg = 35f),   // oreille pointue D
            p(-1.7f, 20.5f, 5f, 1.5f, 1.5f, 1f, 0xFFFFE24A.toInt(), emissive = true),
            p( 1.7f, 20.5f, 5f, 1.5f, 1.5f, 1f, 0xFFFFE24A.toInt(), emissive = true)
        ), heightVox = 24f)
    }

    private fun troll(): MobModel {
        val skin = 0xFF4F6B3A.toInt(); val patch = 0xFF3A5230.toInt()
        return MobModel(listOf(
            p(-4f, 8f, 0f, 5f, 16f, 5f, skin, Limb.LEG, -1),
            p( 4f, 8f, 0f, 5f, 16f, 5f, skin, Limb.LEG,  1),
            p( 0f, 26f, 0f, 13f, 16f, 9f, skin),               // long torse
            p( 0f, 24f, 4f, 9f, 8f, 1.2f, patch),              // tache de mousse
            p(-8.5f, 24f, 0f, 4f, 20f, 4f, skin, Limb.ARM, -1, pivotY = 33f),  // bras très longs
            p( 8.5f, 24f, 0f, 4f, 20f, 4f, skin, Limb.ARM,  1, pivotY = 33f),
            p( 0f, 37f, 0f, 8f, 7f, 8f, skin),                 // petite tête
            p(-2f, 37.5f, 4f, 1.6f, 1.6f, 1f, 0xFFD8E060.toInt(), emissive = true),
            p( 2f, 37.5f, 4f, 1.6f, 1.6f, 1f, 0xFFD8E060.toInt(), emissive = true)
        ), heightVox = 40.5f)
    }

    private fun golem(): MobModel {
        val stone = 0xFF8C8C8C.toInt(); val dark = 0xFF6E6E6E.toInt(); val crack = 0xFF565656.toInt()
        return MobModel(listOf(
            p(-5f, 6f, 0f, 7f, 12f, 7f, dark, Limb.LEG, -1),
            p( 5f, 6f, 0f, 7f, 12f, 7f, dark, Limb.LEG,  1),
            p( 0f, 22f, 0f, 18f, 18f, 12f, stone),             // torse massif cubique
            p( 0f, 18f, 6.2f, 2f, 12f, 1f, crack),             // fissure
            p(-12f, 22f, 0f, 6f, 18f, 6f, stone, Limb.ARM, -1, pivotY = 30f),  // bras lourds
            p( 12f, 22f, 0f, 6f, 18f, 6f, stone, Limb.ARM,  1, pivotY = 30f),
            p( 0f, 36f, 0f, 11f, 9f, 11f, dark),               // tête bloc
            p(-2.5f, 36.5f, 5.5f, 2f, 1.5f, 1f, 0xFFB0E0FF.toInt(), emissive = true),
            p( 2.5f, 36.5f, 5.5f, 2f, 1.5f, 1f, 0xFFB0E0FF.toInt(), emissive = true)
        ), heightVox = 40.5f)
    }

    private fun wraith(): MobModel {
        val robe = 0xFF2E2440.toInt(); val robe2 = 0xFF3A2E52.toInt(); val hood = 0xFF221A33.toInt()
        return MobModel(listOf(
            // Robe en pyramide inversée flottante (du large vers l'étroit en haut)
            p( 0f, 4f, 0f, 14f, 8f, 12f, robe),
            p( 0f, 12f, 0f, 12f, 8f, 10f, robe2),
            p( 0f, 20f, 0f, 10f, 8f, 8f, robe),
            p(-7f, 18f, 0f, 3f, 12f, 3f, robe2, Limb.ARM, -1, pivotY = 24f, baseTiltDeg = -25f),
            p( 7f, 18f, 0f, 3f, 12f, 3f, robe2, Limb.ARM,  1, pivotY = 24f, baseTiltDeg = -25f),
            p( 0f, 28f, 0f, 9f, 8f, 9f, hood),                 // capuche
            p(-2f, 28f, 4.6f, 1.8f, 1.8f, 1f, 0xFF66F0FF.toInt(), emissive = true),
            p( 2f, 28f, 4.6f, 1.8f, 1.8f, 1f, 0xFF66F0FF.toInt(), emissive = true)
        ), heightVox = 32f, floats = true)
    }

    private fun spider(): MobModel {
        val body = 0xFF332626.toInt(); val abd = 0xFF26201E.toInt()
        val leg = 0xFF1F1818.toInt(); val eye = 0xFFE04040.toInt()
        val parts = mutableListOf(
            p( 0f, 4f, 3f, 8f, 6f, 8f, body),                  // céphalothorax
            p( 0f, 4.5f, -6f, 11f, 8f, 11f, abd),              // abdomen
            p(-3f, 5.5f, 7f, 1.6f, 1.6f, 1f, eye, emissive = true),
            p( 3f, 5.5f, 7f, 1.6f, 1.6f, 1f, eye, emissive = true)
        )
        // 8 pattes : 4 de chaque côté, inclinées vers l'extérieur, balancement alterné
        for (i in 0..3) {
            val z = 5f - i * 3.2f
            parts += p(-6.5f, 4f, z, 7f, 1.6f, 1.6f, leg, Limb.LEG, if (i % 2 == 0) -1 else 1, pivotY = 5.5f)
            parts += p( 6.5f, 4f, z, 7f, 1.6f, 1.6f, leg, Limb.LEG, if (i % 2 == 0) 1 else -1, pivotY = 5.5f)
        }
        return MobModel(parts, heightVox = 9f)
    }

    private fun imp(): MobModel {
        val skin = 0xFFB23A2E.toInt(); val belly = 0xFF8A2A22.toInt()
        val horn = 0xFFE8DCC0.toInt(); val eye = 0xFFFFD23A.toInt()
        return MobModel(listOf(
            p(-2f, 3.5f, 0f, 2.5f, 7f, 2.5f, skin, Limb.LEG, -1),
            p( 2f, 3.5f, 0f, 2.5f, 7f, 2.5f, skin, Limb.LEG,  1),
            p( 0f, 9.5f, 0f, 6f, 6f, 4f, belly),
            p(-4.5f, 9f, 0f, 2f, 7f, 2f, skin, Limb.ARM, -1, pivotY = 13f, baseTiltDeg = -30f),
            p( 4.5f, 9f, 0f, 2f, 7f, 2f, skin, Limb.ARM,  1, pivotY = 13f, baseTiltDeg = -30f),
            p( 0f, 15f, 0f, 6f, 6f, 6f, skin),                 // tête
            p(-2f, 18.5f, 0f, 1.4f, 3.5f, 1.4f, horn, baseTiltDeg = -18f),  // corne G
            p( 2f, 18.5f, 0f, 1.4f, 3.5f, 1.4f, horn, baseTiltDeg = -18f),  // corne D
            p( 0f, 6f, -3.5f, 1.4f, 1.4f, 5f, skin, baseTiltDeg = 40f),     // queue
            p(-1.5f, 15.5f, 3.2f, 1.4f, 1.4f, 1f, eye, emissive = true),
            p( 1.5f, 15.5f, 3.2f, 1.4f, 1.4f, 1f, eye, emissive = true)
        ), heightVox = 18.5f)
    }

    private fun mummy(): MobModel {
        val wrap = 0xFFD9CFB0.toInt(); val wrap2 = 0xFFBEB48E.toInt(); val socket = 0xFF111111.toInt()
        return MobModel(listOf(
            p(-3f, 6f, 0f, 4f, 12f, 4f, wrap, Limb.LEG, -1),
            p( 3f, 6f, 0f, 4f, 12f, 4f, wrap2, Limb.LEG,  1),
            p( 0f, 17f, 0f, 10f, 11f, 6f, wrap),
            p( 0f, 14f, 3.2f, 10.2f, 3f, 0.6f, wrap2),         // bandage en travers
            p( 0f, 19f, 3.2f, 10.2f, 3f, 0.6f, wrap2),
            // Bras tendus, raides
            p(-7f, 16f, 0f, 3f, 12f, 3f, wrap2, Limb.ARM, -1, pivotY = 22f, baseTiltDeg = -82f),
            p( 7f, 16f, 0f, 3f, 12f, 3f, wrap, Limb.ARM,  1, pivotY = 22f, baseTiltDeg = -82f),
            p( 0f, 26.5f, 0f, 8f, 8f, 8f, wrap),
            p(-2f, 27f, 4f, 1.8f, 1.6f, 1f, socket, emissive = true),
            p( 2f, 27f, 4f, 1.8f, 1.6f, 1f, socket, emissive = true)
        ), heightVox = 30.5f)
    }

    private fun slime(): MobModel {
        val body = 0xFF63C76B.toInt(); val eye = 0xFFFFFFFF.toInt(); val pupil = 0xFF14281E.toInt()
        return MobModel(listOf(
            p( 0f, 6f, 0f, 14f, 12f, 14f, body),               // gros cube gélatineux
            p( 0f, 9f, 0f, 10f, 5f, 10f, 0xFF7BE083.toInt()),  // reflet plus clair en haut
            p(-3f, 7.5f, 7f, 2.6f, 3f, 1f, eye, emissive = true),
            p( 3f, 7.5f, 7f, 2.6f, 3f, 1f, eye, emissive = true),
            p(-3f, 7f, 8f, 1.4f, 1.6f, 1f, pupil, emissive = true),
            p( 3f, 7f, 8f, 1.4f, 1.6f, 1f, pupil, emissive = true)
        ), heightVox = 13f, squash = true)
    }
}
