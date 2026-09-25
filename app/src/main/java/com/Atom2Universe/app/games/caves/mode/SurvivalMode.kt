package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.world.WARD_STONE

/**
 * Le mode historique de Cave World : monde infini, monstres, XP, compétences et butin.
 *
 * Ce code vivait directement dans [CaveRenderer] ; il en a été sorti tel quel pour laisser la
 * place à d'autres modes, sans changer le comportement.
 */
internal class SurvivalMode(private val r: CaveRenderer) : GameMode {

    private var wired = false

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        if (wired) return
        wired = true
        wireSkills()
        wireEnemies()
        r.enemyManager.spawnManager.exploration = true
        r.enemyManager.explorationCombat=true
        r.enemyManager.clearSight=r::clearCombatLine
        r.enemyManager.meleeImpact={ enemy,dx,dz -> r.expeditionCombat.receive(enemy.scaledDamage,dx,dz,enemy) }
        r.enemyManager.rangedImpact=r::fireExplorationArrow
        r.enemyManager.spawnManager.lightAt = r::spawnLight
        if (savedState != null) restoreProgress(savedState)
    }

    override fun onPlayerPlaced(x: Double, y: Double, z: Double) {
        // Point autour duquel les monstres apparaissent.
        r.enemyManager.worldSpawnX = x
        r.enemyManager.worldSpawnY = y
        r.enemyManager.worldSpawnZ = z
    }

    override val headshotMultiplier: Float get() = 1.4f
    override fun onPlayerShot(damage: Int,dirX: Double,dirZ: Double) {
        r.expeditionCombat.receive(damage,dirX.toFloat(),dirZ.toFloat())
    }
    override fun update(dt: Float) {
        r.enemyManager.update(dt, r.camera.playerX, r.camera.playerY, r.camera.playerZ)
    }

    // ── Compétences : endurance, saut, chute ──────────────────────────────────

    private fun wireSkills() {
        val skillBook = r.skillBook
        val playerNode = r.playerNode

        playerNode.onEnduranceXp = { xp ->
            skillBook.enduranceXp += xp
            val formula = skillBook.computedMaxHp
            val newMax  = formula.coerceAtMost(120)
            if (newMax > playerNode.maxHp) {
                playerNode.setMaxHp(newMax)
                r.playerStats.maxHp = newMax
            }
        }

        val physics = r.physics
        physics.skillBook = skillBook
        physics.onJumped = { skillBook.athleticsXp += 1 }
        physics.onFallLanded = { fallBlocks ->
            val threshold = skillBook.fallSafeBlocks
            if (fallBlocks > threshold) {
                val damage = ((fallBlocks - threshold) * 2.5).toInt().coerceAtLeast(1)
                playerNode.applyDamage(damage)
                val xpGain = ((fallBlocks - threshold) * 10).toInt().coerceAtLeast(1)
                skillBook.acrobaticsXp += xpGain
            } else {
                // Chute sans dégâts : petit XP acrobatics quand même
                skillBook.acrobaticsXp += (fallBlocks * 2).toInt().coerceAtLeast(1)
            }
        }
    }

    // ── Monstres : attaques, récompenses, butin ───────────────────────────────

    private fun wireEnemies() {
        val enemyManager = r.enemyManager
        enemyManager.player         = r.playerNode
        enemyManager.eventBus       = r.eventBus
        enemyManager.thornsProvider = { r.equippedWeaponStat("thorns") }

        r.eventBus.subscribe { event ->
            if (event !is GameEvent.MobDied) return@subscribe
            val xpGain = if (event.isBoss) 5 * event.level else event.level
            r.playerStats.addXp(xpGain)
            if (event.isBoss) {
                r.inventory[WARD_STONE] = (r.inventory[WARD_STONE] ?: 0) + 1
                r.inventoryCallback?.invoke(r.inventory.toMap())
            }
        }

        // Exploration rewards feed the workshop instead of flooding the bag with random guns.
        // Existing weapons remain usable; Assault keeps its own equipment rules.
        r.lootNode.onItemsDropped = { _ -> }
        r.eventBus.subscribe { event ->
            if (event !is GameEvent.MobDied) return@subscribe
            val resource: Short = when(event.mobDefId) {
                "spider", "slime" -> 3111
                "golem", "dwarf" -> 3114
                else -> 3110
            }
            r.inventory[resource] = ((r.inventory[resource] ?: 0).toLong() + 1 + event.level.coerceAtMost(3))
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            r.inventoryCallback?.invoke(r.inventory.toMap())
        }
    }

    // ── Sauvegarde ────────────────────────────────────────────────────────────

    private fun restoreProgress(saved: CaveRenderer.SavedState) {
        val playerStats = r.playerStats
        playerStats.level    = saved.playerLevel
        playerStats.xp       = saved.playerXp
        playerStats.xpToNext = playerStats.xpRequired(saved.playerLevel)
        playerStats.maxHp    = saved.playerMaxHp
        playerStats.shield   = saved.playerShield

        val playerNode = r.playerNode
        playerNode.maxHp     = saved.playerMaxHp
        playerNode.hp        = saved.playerHp.coerceAtMost(saved.playerMaxHp)
        playerNode.maxShield = saved.playerShield
        playerNode.shield    = saved.playerShieldCurrent.coerceAtMost(saved.playerShield)

        saved.wardStonePositions.forEach { (x, z) -> r.enemyManager.wardStoneZones.add(Pair(x, z)) }

        val skillBook = r.skillBook
        skillBook.athleticsXp  = saved.skillAthleticsXp
        skillBook.speedXp      = saved.skillSpeedXp
        skillBook.enduranceXp  = saved.skillEnduranceXp
        skillBook.acrobaticsXp = saved.skillAcrobaticsXp
    }
}
