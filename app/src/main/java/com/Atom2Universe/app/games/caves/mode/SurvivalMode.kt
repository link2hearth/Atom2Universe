package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.ItemRegistry
import com.Atom2Universe.app.games.caves.node.WeaponInstanceRegistry
import com.Atom2Universe.app.games.caves.world.WARD_STONE

/**
 * Le mode historique de Cave World : monde infini, monstres, XP, compétences et butin.
 *
 * Ce code vivait directement dans [CaveRenderer] ; il en a été sorti tel quel pour laisser la
 * place à d'autres modes, sans changer le comportement.
 */
internal class SurvivalMode(private val r: CaveRenderer) : GameMode {

    // HP max du dernier mob tué — plafond pour les gains d'endurance
    private var lastKilledMobMaxHp = 20

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        wireSkills()
        wireEnemies()
        if (savedState != null) restoreProgress(savedState)
    }

    override fun onPlayerPlaced(x: Double, y: Double, z: Double) {
        // Point autour duquel les monstres apparaissent.
        r.enemyManager.worldSpawnX = x
        r.enemyManager.worldSpawnY = y
        r.enemyManager.worldSpawnZ = z
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
            val newMax  = formula.coerceAtMost(lastKilledMobMaxHp)
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
            // Le dernier mob tué fixe le plafond HP pour les gains d'endurance
            lastKilledMobMaxHp = event.mobMaxHp.coerceAtLeast(20)
            if (event.isBoss) {
                r.inventory[WARD_STONE] = (r.inventory[WARD_STONE] ?: 0) + 1
                r.inventoryCallback?.invoke(r.inventory.toMap())
            }
        }

        r.lootNode.onItemsDropped = { items ->
            for (item in items) {
                if (ItemRegistry.get(item.defId)?.type == "weapon") {
                    val id = WeaponInstanceRegistry.allocate(item)
                    r.inventory[id] = 1
                    // Une arme dropée va toujours dans la barre combat, même si la barre
                    // construction est active au moment du kill.
                    val freeHotbarSlot = r.combatHotbar.indexOfFirst { it == null }
                    if (freeHotbarSlot >= 0) r.combatHotbar[freeHotbarSlot] = id
                }
            }
            r.inventoryCallback?.invoke(r.inventory.toMap())
            r.hotbarCallback?.invoke(r.hotbar.copyOf(), r.selectedSlot)
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
