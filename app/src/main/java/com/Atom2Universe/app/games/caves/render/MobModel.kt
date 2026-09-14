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
internal enum class Limb { NONE, LEG, ARM, WEAPON, MUZZLE_FLASH, HEAD, TAIL, WING, LOOK, CLOTH, CRAWL }

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
    val emissive: Boolean = false,
    val pivotZ: Float = cz,
    val pivotX: Float = cx
)

internal class MobModel(
    val parts: List<MobPart>,
    val heightVox: Float,
    /** Corps gélatineux : écrasement/étirement vertical animé (slime). */
    val squash: Boolean = false,
    /** Flotte : oscillation verticale, pas de contact sol (spectre). */
    val floats: Boolean = false,
    val headPivotZ: Float = 8f,
    val feedingDrop: Float = 5.5f,
    /** Rythme et amplitude propres aux ennemis ; les animaux gardent leur animation. */
    val gait: Float = 9f,
    val stride: Float = .5f,
    val breath: Float = .16f
)

internal object MobModels {

    private val models: Map<String, MobModel> by lazy {
        EnemyModels.all + listOf("sheep", "cow", "chicken", "pig").associateWith {
            AnimalModels.get(it, false, 0)
        }
    }

    fun get(model: String): MobModel = models[model] ?: models.getValue("slime")
    /** Hauteur voxel de référence : [MobModel.heightVox] est mappé sur `baseScale × 2`. */
    const val REF_VOX = 30f

    /** Hauteur monde réelle du modèle rendu (sert à la hitbox et au placement de la barre de vie). */
    fun bodyHeightWorld(model: String, baseScale: Float): Float =
        get(model).heightVox * (baseScale * 2f / REF_VOX)

    /**
     * Bas de la tête, en fraction de la hauteur du corps. Chez les humanoïdes (jambes 12, torse 11,
     * tête 8 voxels), la tête commence vers 74 % : un tir au-dessus touche la tête.
     */
    const val HEAD_START = 0.74f

}
