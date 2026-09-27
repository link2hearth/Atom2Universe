package com.Atom2Universe.app.games.caves.entity

import com.Atom2Universe.app.games.caves.node.MobDef
import kotlin.math.pow
import kotlin.math.roundToInt
import com.Atom2Universe.app.games.caves.world.MineralProgression as P

internal enum class EnemyState { WANDER, CHASE, ATTACK }
internal enum class ExhibitPose { REFERENCE, IDLE, ACTION }

internal class Enemy(
    val id: Int,
    val def: MobDef,
    var x: Double,
    var y: Double,
    var z: Double
) {
    var level: Int = 1
    var isBoss: Boolean = false
    /** Stable authored habitat; boss victories are persisted separately from transient actors. */
    var undergroundSiteId: String? = null
    val isSiteBoss get() = isBoss && undergroundSiteId != null
    val collisionRadius get() = def.radius.toDouble() * if (isSiteBoss) BOSS_SPRITE_SCALE else 1f
    var animTime: Float = 0f
    var exhibitPose: ExhibitPose? = null
    var resting = false
    /** Aspect de la faune, indépendant du niveau et conservé dans la sauvegarde. */
    var young = false
    var coat = 0
    var domestic = false
    var growth = 600f
    var affection = 0f
    var breedRest = 0f
    var mealRest = 0f
    /** -1: needs feeding, 0: product ready, positive: production in progress. */
    var productTime = -1f
    /** Seconds remaining in the shared rifle recoil animation. */
    var shotRecoil = 0f
    var heldWeaponType: String? = null
    /**
     * Entièrement caché par des murs pleins du point de vue de la caméra : le renderer ne le
     * construit pas. Seul le mode Assaut le calcule ; ailleurs il reste faux.
     */
    var occluded = false
    var weaponReload = 0f
    /** Impulsion visuelle déclenchée par une frappe réelle ; ne décide jamais des dégâts. */
    var strikeTime = 0f
    var attackWindup = 0f
    var attack: EnemyAttack? = null
    var attackRecovery = 0f
    var hopRest = 0f
    var landingSquash = 0f
    var windupYaw = 0f
    var staggerTimer = 0f
    var exploration = false
    var walkPhase = 0f
    var motionBlend = 0f

    // HP = hpBase × level² : linéaire au carré, sans cap, calibré à ~500 HP à level 10 (hpBase=5)
    private fun scaledHp(): Int = if(exploration) (def.hpBase*10f*P.scale(level-1)).roundToInt().coerceAtLeast(1)
        else (def.hpBase.toLong()*level*level).coerceIn(1,Int.MAX_VALUE.toLong()).toInt()
    val maxHp get() = if (isBoss) scaledHp() * BOSS_HP_MULT else scaledHp()
    val scaledDamage get() = if(exploration) ((def.damageBase+2)*2f*(if(isBoss) 1.5f else 1f)*P.scale(level-1)).roundToInt().coerceAtLeast(1)
        else (if (isBoss) def.damageBase * 3 else def.damageBase) + (level - 1) / 3
    val scaledSpeed get() = def.speed * (1f + (level - 1) * def.speedScalePerLevel).coerceAtMost(if(exploration) 1.25f else if (isBoss) 2.0f else 3.0f)
    val baseScale get() = if (isBoss) def.spriteScale * BOSS_SPRITE_SCALE else def.spriteScale

    var hp: Int = def.hpBase
    var state = EnemyState.WANDER
    var velY = 0.0
    var waterDriftX = 0.0
    var waterDriftZ = 0.0
    var onGround = false
    var wanderTimer = 0f
    var wanderDirX = 0f
    var wanderDirZ = 0f
    var attackCooldown = 0f
    var yaw = 0f
    var hitFlash = 0f
    var stuckTimer = 0f

    // Recul horizontal quand le joueur inflige des dégâts — vitesse amortie.
    var knockX = 0.0
    var knockZ = 0.0

    // Effets de statut appliqués par les affixes d'arme
    // Saignement : jauge qui monte à chaque proc et explose en gros dégâts à 100
    // (façon Dark Souls), puis redescend seule si le mob n'est plus touché.
    var bleedBuildup: Float = 0f
    var bleedDecayGrace: Float = 0f

    var freezeTimer: Float = 0f      // gel : immobilisation totale, aucune IA
    var confusionTimer: Float = 0f   // électrique : attaque ses alliés au lieu du joueur

    var poisonTimer: Float = 0f
    var poisonDamage: Int = 0
    var poisonTickTimer: Float = 0f

    var fireTimer: Float = 0f
    var fireDamage: Int = 0
    var fireTickTimer: Float = 0f

    var alertPlayed: Boolean = false

    companion object {
        const val BOSS_SPRITE_SCALE = 2.2f
        const val BOSS_HP_MULT      = 6
        const val BLEED_BURST_THRESHOLD = 100f
        const val BLEED_BURST_FRACTION  = 0.30f   // % des PV max infligés d'un coup
        const val BLEED_DECAY_PER_SEC   = 12f     // vitesse à laquelle la jauge redescend
    }
}
