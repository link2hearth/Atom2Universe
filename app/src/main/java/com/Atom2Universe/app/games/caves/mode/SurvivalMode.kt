package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.FrontierItems as F
import com.Atom2Universe.app.games.caves.world.CoinEconomy
import com.Atom2Universe.app.games.caves.node.MineralItems as M
import com.Atom2Universe.app.games.caves.world.MineralProgression as P

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
        wireEnemies()
        r.enemyManager.spawnManager.exploration = true
        r.enemyManager.explorationCombat=true
        r.enemyManager.clearSight=r::clearCombatLine
        // L'équipement qui encaisse est celui du joueur de l'appareil, le seul pour l'instant.
        r.enemyManager.meleeImpact={ enemy,_,dx,dz -> r.expeditionCombat.receive(enemy.scaledDamage,dx,dz,enemy) }
        r.enemyManager.rangedImpact=r::fireExplorationProjectile
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
        r.enemyManager.update(dt)
    }

    // ── Monstres : attaques, récompenses, butin ───────────────────────────────

    private fun wireEnemies() {
        val enemyManager = r.enemyManager
        enemyManager.targets        = r.sim.players
        enemyManager.eventBus       = r.eventBus

        r.eventBus.subscribe { event ->
            if (event !is GameEvent.MobDied) return@subscribe
            val xpGain = if (event.isBoss) 5 * event.level else event.level
            r.playerStats.addXp(xpGain)
        }

        // Exploration rewards feed the workshop: resources, never weapons.
        r.eventBus.subscribe { event ->
            if (event !is GameEvent.MobDied) return@subscribe
            val resource: Short = when(event.mobDefId) {
                "spider", "slime" -> 3111
                "golem", "dwarf" -> 3114
                else -> 3110
            }
            r.inventory[resource] = ((r.inventory[resource] ?: 0).toLong() + 1 + event.level.coerceAtMost(3))
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            val stage = (event.level - 1).coerceIn(0, P.LAST_STAGE)
            // Coins go straight into the bag, no item on the ground.
            val coins = CoinEconomy.loot(stage, event.isBoss, kotlin.random.Random)
            r.inventory[F.COIN] = ((r.inventory[F.COIN] ?: 0).toLong() + coins)
                .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (event.isBoss) {
                val metal = M.id(stage, M.Form.RAW)
                r.inventory[metal] = ((r.inventory[metal] ?: 0).toLong() + 8)
                    .coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            }
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

        r.player.bossStages.addAll(saved.bossStages)
        saved.wardStonePositions.forEach { (x, z) -> r.enemyManager.wardStoneZones.add(Pair(x, z)) }

    }
}
