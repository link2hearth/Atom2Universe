package com.Atom2Universe.app.games.caves.mode

import com.Atom2Universe.app.games.caves.CaveRenderer
import com.Atom2Universe.app.games.caves.ai.NavGrid
import com.Atom2Universe.app.games.caves.ai.PathFinder
import com.Atom2Universe.app.games.caves.ai.PlayerSnapshot
import com.Atom2Universe.app.games.caves.ai.ShotSink
import com.Atom2Universe.app.games.caves.ai.SolidGrid
import com.Atom2Universe.app.games.caves.ai.Soldier
import com.Atom2Universe.app.games.caves.ai.SoldierTuning
import com.Atom2Universe.app.games.caves.ai.withPersonality
import com.Atom2Universe.app.games.caves.ai.BodyClearance
import com.Atom2Universe.app.games.caves.ai.SoldierCollision
import com.Atom2Universe.app.games.caves.ai.RouteQueue
import com.Atom2Universe.app.games.caves.ai.SoldierCrowd
import com.Atom2Universe.app.games.caves.ai.Squad
import com.Atom2Universe.app.games.caves.ai.SquadCommand
import com.Atom2Universe.app.games.caves.ai.SquadMember
import com.Atom2Universe.app.games.caves.ai.SquadSpawn
import com.Atom2Universe.app.games.caves.ai.SquadTuning
import com.Atom2Universe.app.games.caves.ai.SuburbDeployment
import com.Atom2Universe.app.games.caves.ai.TowerDeployment
import com.Atom2Universe.app.games.caves.ai.LineOfSight
import com.Atom2Universe.app.games.caves.world.BuiltinMaps
import com.Atom2Universe.app.games.caves.world.MapleCrossingMap
import com.Atom2Universe.app.games.caves.world.OfficeTowerMap
import com.Atom2Universe.app.games.caves.entity.RangedProfile
import com.Atom2Universe.app.games.caves.entity.Enemy
import com.Atom2Universe.app.games.caves.entity.EnemyState
import com.Atom2Universe.app.games.caves.entity.Projectile
import com.Atom2Universe.app.games.caves.entity.ProjectileKind
import com.Atom2Universe.app.games.caves.entity.WeaponColor
import com.Atom2Universe.app.games.caves.entity.WeaponDef
import com.Atom2Universe.app.games.caves.entity.WeaponVariant
import com.Atom2Universe.app.games.caves.node.GameEvent
import com.Atom2Universe.app.games.caves.node.MobDef
import com.Atom2Universe.app.games.caves.world.AIR
import com.Atom2Universe.app.games.caves.world.MapSource
import com.Atom2Universe.app.games.caves.world.isDecoration
import com.Atom2Universe.app.games.caves.world.isWater
import kotlin.math.atan2
import kotlin.random.Random

/**
 * Le mode Assaut : une carte préparée, jouée au fusil (voir CAVE_WORLD_ASSAUT.md).
 *
 * Chaque manche déploie sa garnison dès le départ : 60 gardes dans la tour, répartis par niveau.
 * Il faut tous les éliminer avant la fin du chrono ; mourir perd la manche. Toutes les
 * armes sont prêtées, les munitions de réserve sont illimitées, l'heure est figée selon la carte, et la
 * carte ne se creuse pas.
 *
 * Les soldats pensent avec [Soldier] (Kotlin pur, testé seul). Leur corps est un simple [Enemy]
 * rangé dans la liste de l'EnemyManager : le renderer le dessine et les balles du joueur le
 * touchent. L'EnemyManager n'est jamais mis à jour ici : c'est ce mode qui place les corps là où
 * les cerveaux ont décidé d'aller.
 */
internal class AssaultMode(
    private val r: CaveRenderer,
    private val source: MapSource,
) : GameMode {

    private val isTower = source.map.name == OfficeTowerMap.ID
    private val isSuburb = source.map.name == MapleCrossingMap.ID
    /** Le quartier des fonderies : déploiement au sol dans la moitié est, jamais sur les toits. */
    private val isArena = source.map.name == BuiltinMaps.ARENA_ID &&
        source.map.sizeX == BuiltinMaps.ARENA_SIZE && source.map.sizeZ == BuiltinMaps.ARENA_DEPTH &&
        source.map.spawnsB.isNotEmpty()
    // Une escouade tient un secteur et monte à l'assaut d'un seul bloc : quatre à six hommes.
    // Dans la tour, chaque poste fixe lui-même son effectif.
    private val squadSize = FIELD_SQUAD_SIZE
    private val squadCount = if (isSuburb) SUBURB_SQUADS else FIELD_SQUADS
    private val soldierCount =
        if (isTower) OfficeTowerMap.POSTS.sumOf { it.size } else squadSize * squadCount
    val match = AssaultMatch(targetsPerRound = soldierCount,
        roundSeconds = if (isTower) 20 * 60f else if (isSuburb) 10 * 60f else ROUND_SECONDS, chooseWeaponEachRound = true)
    private var roundWeapon = "gun"
    val weaponChoices: List<String> = RangedProfile.all.filterValues { it.magazine > 0 }.keys.toList()
    private val recovery = AssaultRecovery(r.playerNode)
    private val pickups = ArrayList<ShieldPickup>()
    val shieldPickups: List<ShieldPickup> get() = pickups
    @Volatile var onShieldCollected: ((Int) -> Unit)? = null

    /** Nouvel état de la partie à afficher. Appelé sur le thread GL, une dizaine de fois par seconde au plus. */
    @Volatile var onStatus: ((AssaultMatch.Status) -> Unit)? = null

    /** Un soldat vient de tomber d'un tir à la tête. Appelé sur le thread GL. */
    @Volatile var onHeadshotKill: (() -> Unit)? = null

    override val allowsWorldEdits: Boolean get() = false
    override val infiniteAmmo: Boolean get() = true
    override val singleWeapon: Boolean get() = true
    override val allowsCombat: Boolean get() = match.phase == AssaultMatch.Phase.PLAYING
    override val fixedTimeOfDayMs: Long get() =
        if (source.map.name == com.Atom2Universe.app.games.caves.world.OfficeTowerMap.ID)
            com.Atom2Universe.app.games.caves.world.OfficeTowerMap.DUSK_MS else NOON_MS
    override val headshotMultiplier: Float get() = HEADSHOT_MULTIPLIER

    /**
     * Un soldat : son corps (dessiné, touché par les balles), son cerveau, et son escouade.
     *
     * C'est lui qui fait le [SquadMember] : l'état-major ne connaît ni l'`Enemy` ni le renderer,
     * il ne sait que donner une case à rejoindre et un secteur à tenir.
     */
    private class Trooper(val body: Enemy, val brain: Soldier, val damage: Int) : SquadMember {
        var elapsed = 0f
        var squad: Squad? = null
        /** Dernier instant (horloge d'occlusion) où la caméra l'a vu. */
        var lastSeenAt = Float.NEGATIVE_INFINITY
        /** Depuis combien de temps il n'a ni bougé ni fini son trajet (diagnostic, debug only). */
        var stuckSeconds = 0f
        var stuckLogged = false

        override val alive: Boolean get() = body.hp > 0
        override val x: Double get() = brain.x
        override val y: Double get() = brain.y
        override val z: Double get() = brain.z
        override val seesTarget: Boolean get() = brain.seesPlayer
        override val shaken: Boolean get() = brain.shaken
        override fun order(node: Int, strict: Boolean) = brain.order(node, strict)
        override fun radioContact(x: Double, z: Double) = brain.radioContact(x, z)
        override fun leashTo(x: Double, z: Double, radius: Double) = brain.leashTo(x, z, radius)
    }

    private val units = ArrayList<Trooper>(soldierCount)
    private val crowd = SoldierCrowd(source.map.sizeX, source.map.sizeY, source.map.sizeZ)
    private var updateCursor = 0
    private var aiAccumulator = 0f
    private var commandNs = 0L
    private var aiLogSeconds = 0f
    private var aiPeakNs = 0L
    private var aiTotalNs = 0L
    private val bodies get() = r.enemyManager.enemies

    // Dernier coup reçu par chaque soldat (par id) : c'est lui qui dit s'il est tombé d'un tir à la tête.
    private val lastHitWasHead = HashMap<Int, Boolean>()
    private var nextSoldierId = 1
    private val rng = Random.Default
    private var statusTimer = 0f

    // ── Navigation (construite dans onSurfaceCreated, une fois les blocs connus) ──
    private val solid = object : SolidGrid {
        override fun isSolid(x: Int, y: Int, z: Int) =
            blocksMovement(source.map.blockAt(x, y, z)) || source.decor.occupied(x, y, z)

        override fun blocksSight(x: Int, y: Int, z: Int, x0: Double, y0: Double, z0: Double,
                                 dx: Double, dy: Double, dz: Double): Boolean {
            if (source.decor.blocksSight(x, y, z, x0, y0, z0, dx, dy, dz)) return true
            if (!blocksMovement(source.map.blockAt(x, y, z))) return false
            val def = com.Atom2Universe.app.games.caves.node.BlockRegistry.get(source.map.blockAt(x, y, z))
                ?: return true
            if (!def.stairs && !def.slab && def.blockHeight >= 1f) return true
            return com.Atom2Universe.app.games.caves.world.PartialBlockModel.intersect(
                source.map.metaAt(x, y, z), x0 - x, y0 - y, z0 - z, dx, dy, dz, 1.0,
                def.slab, def.blockHeight,
                com.Atom2Universe.app.games.caves.world.StairConnections.maskAt(
                    x, y, z, source.map::blockAt, source.map::metaAt)) != null
        }
    }
    private var navGrid: NavGrid? = null
    private var pathFinder: PathFinder? = null
    private var routes: RouteQueue? = null
    private var towerDeployment: TowerDeployment? = null
    private var suburbDeployment: SuburbDeployment? = null
    /** Le « talkie-walkie » : une seule escouade sur le joueur à la fois (voir [SquadCommand]). */
    private var command: SquadCommand? = null

    // ── Le joueur vu par les soldats (coordonnées de la carte) ──
    private val player = PlayerSnapshot()
    private var prevPlayerX = Double.NaN
    private var prevPlayerZ = Double.NaN

    // Balles des soldats : même aspect que les balles du joueur, dégâts fixes.
    private val bulletLook = WeaponDef(WeaponColor.WHITE, WeaponVariant.SQUARE)
    private val soldierTuning = if (isTower) SoldierTuning(hearingRange = 48.0) else SoldierTuning()
    private var firingBody: Enemy? = null
    private var firingUnit: Trooper? = null
    private val shotSink = ShotSink { x, y, z, dx, dy, dz ->
        firingBody?.shotRecoil = .16f
        val unit = firingUnit ?: error("Tir sans soldat actif")
        val profile = RangedProfile.all.getValue(roundWeapon)
        repeat(profile.pellets) {
            val spread = profile.spread.toDouble()
            var sx = dx + rng.nextDouble(-spread, spread)
            var sy = dy + rng.nextDouble(-spread, spread)
            var sz = dz + rng.nextDouble(-spread, spread)
            val length = kotlin.math.sqrt(sx * sx + sy * sy + sz * sz).coerceAtLeast(.0001)
            sx /= length; sy /= length; sz /= length
            r.projectiles.add(Projectile(
                x + source.originX, y + source.originY, z + source.originZ, sx, sy, sz,
                profile.speed, (unit.damage / profile.pellets).coerceAtLeast(1), bulletLook,
                kind = profile.kind, maxRange = profile.range, fromEnemy = true,
            ))
        }
        r.eventBus.publish(GameEvent.EnemyFired(roundWeapon))
    }

    override fun spawnPoint(): FloatArray = source.spawnPoint()

    override fun onSurfaceCreated(savedState: CaveRenderer.SavedState?) {
        r.physics.dynamicCollision = { x, feetY, z, height ->
            crowd.overlaps(x - source.originX, feetY - source.originY, z - source.originZ, height = height)
        }
        // L'arme et les ennemis seront créés après le choix de début de manche.
        recovery.reset()

        val map = source.map
        val grid = NavGrid.build(map.sizeX, map.sizeY, map.sizeZ, solid)
        navGrid = grid
        pathFinder = PathFinder(grid)
        routes = RouteQueue(grid)
        command = SquadCommand(grid, solid, rng, if (isTower) TOWER_COMMAND else SquadTuning())
        if (!listeningSteps) {
            listeningSteps = true
            // Les soldats entendent exactement les pas que le joueur entend : même cadence, et
            // rien du tout accroupi ou dans l'eau, puisque le renderer n'y publie aucun pas.
            r.eventBus.subscribe { event ->
                if (event is GameEvent.Footstep && event.moving) {
                    stepInterval = event.interval
                    stepRunning = event.running
                    stepSurface = event.surface
                    stepSilence = 0f
                }
            }
        }
        if (isTower) towerDeployment = TowerDeployment(grid, map.spawnsA.first())
        if (isSuburb) suburbDeployment = SuburbDeployment(grid, map.spawnsA.first())
    }

    override fun onPlayerPlaced(x: Double, y: Double, z: Double) = kotlin.Unit

    override fun onEnemyHit(enemy: Enemy, headshot: Boolean) {
        lastHitWasHead[enemy.id] = headshot
        r.eventBus.publish(GameEvent.MobHit(false))
        // Touché : il sait d'où vient le tir, même sans avoir vu le tireur.
        val unit = units.firstOrNull { it.body === enemy } ?: return
        unit.brain.healthFraction = enemy.hp.toFloat() / enemy.maxHp.coerceAtLeast(1)
        refreshPlayerPosition()
        unit.brain.onDamaged(player.x, player.eyeY, player.z)
        unit.elapsed = 1f // priorité au prochain passage, même si ce garde était au repos
    }

    override fun onPlayerShot(damage: Int, dirX: Double, dirZ: Double) {
        if (match.phase != AssaultMatch.Phase.PLAYING || r.playerNode.hp <= 0) return
        if (damage <= 0) return
        recovery.hit(damage)
        r.eventBus.publish(GameEvent.PlayerHit(damage, dirX.toFloat(), dirZ.toFloat()))
    }

    override fun onPlayerFired() {
        refreshPlayerPosition()
        for (u in units) {
            // Les dalles épaisses atténuent le bruit : un tir n'alerte pas les six niveaux.
            if (!isTower || kotlin.math.abs(u.brain.y + 1.62 - player.eyeY) < 4.0)
                u.brain.hearShot(player.x, player.eyeY, player.z)
        }
    }

    // Dernier pas publié par le renderer (le même que celui qu'on entend) : cadence, surface,
    // course, et temps écoulé depuis. Au-delà de [STEP_SILENCE] sans publication, le joueur s'est arrêté.
    private var listeningSteps = false
    private var stepInterval = 0f
    private var stepRunning = false
    private var stepSurface = "earth"
    private var stepSilence = 1f
    private var stepTimer = 0f

    /**
     * Le bruit des pas du joueur, calé sur le son réellement joué : un bruit par pas entendu.
     *
     * Contrairement aux tirs, il ne porte que de près, et la surface compte : la pierre claque,
     * la terre étouffe. Accroupi ou dans l'eau, le renderer ne publie aucun pas, donc les soldats
     * n'entendent rien. L'origine du bruit est brouillée d'un bloc environ : le soldat vient voir
     * l'endroit, il ne pointe pas le joueur au bloc près.
     */
    private fun emitFootsteps(dt: Float) {
        stepSilence += dt
        if (stepSilence > STEP_SILENCE) { stepTimer = 0f; return }
        if (units.isEmpty()) return
        stepTimer += dt
        if (stepTimer < stepInterval.coerceAtLeast(STEP_MIN_INTERVAL)) return
        stepTimer = 0f

        val range = (if (stepRunning) FOOTSTEP_RANGE_RUN else FOOTSTEP_RANGE_WALK) *
            surfaceLoudness(stepSurface)
        val nx = player.x + rng.nextDouble(-FOOTSTEP_BLUR, FOOTSTEP_BLUR)
        val nz = player.z + rng.nextDouble(-FOOTSTEP_BLUR, FOOTSTEP_BLUR)
        for (u in units) {
            if (u.body.hp <= 0) continue
            // Comme pour les tirs, les dalles épaisses de la tour arrêtent le bruit entre étages.
            if (isTower && kotlin.math.abs(u.brain.y + 1.62 - player.eyeY) >= 4.0) continue
            u.brain.hearNoise(nx, player.eyeY, nz, range)
        }
    }

    private fun surfaceLoudness(surface: String) = when (surface) {
        "stone" -> 1.15
        "wood" -> 1.05
        else -> 0.85
    }

    override fun update(dt: Float) {
        // Tombé hors de la carte : retour au point d'apparition.
        if (r.camera.playerY < source.originY - FALL_LIMIT) respawnPlayer()

        // L'EnemyManager ne tourne pas dans ce mode : on éteint nous-mêmes le flash des coups.
        for (b in bodies) if (b.hitFlash > 0f) b.hitFlash -= dt

        updatePlayerSnapshot(dt)
        if (match.phase == AssaultMatch.Phase.PLAYING && r.playerNode.isAlive) {
            recovery.update(dt)
            collectShieldPickups()
            emitFootsteps(dt)
            // La radio raisonne en cases de la grille : on lui donne les pieds, pas les yeux.
            val startedCommand = System.nanoTime()
            command?.update(dt, player.x, player.eyeY - EYE_HEIGHT, player.z)
            commandNs = System.nanoTime() - startedCommand
        }

        var forceStatus = false
        if (match.phase == AssaultMatch.Phase.PLAYING && r.playerNode.hp <= 0 &&
            match.onPlayerDied() == AssaultMatch.Event.ROUND_ENDED) {
            clearSoldiers()
            forceStatus = true
        }

        updateSoldiers(dt)
        if (collectFallenSoldiers()) forceStatus = true

        when (match.update(dt)) {
            AssaultMatch.Event.WEAPON_CHOICE -> { clearSoldiers(); forceStatus = true }
            AssaultMatch.Event.ROUND_STARTED -> { startRound(); forceStatus = true }
            AssaultMatch.Event.ROUND_ENDED -> { clearSoldiers(); forceStatus = true }
            AssaultMatch.Event.NONE -> kotlin.Unit
        }

        statusTimer += dt
        if (forceStatus || statusTimer >= STATUS_INTERVAL) {
            statusTimer = 0f
            onStatus?.invoke(match.status())
        }
    }

    /** Appelé sur le thread GL depuis le sélecteur en jeu. */
    fun chooseWeapon(type: String): Boolean {
        if (match.phase != AssaultMatch.Phase.CHOOSING_WEAPON || type !in weaponChoices) return false
        if (!r.equipAssaultWeapon(type)) return false
        roundWeapon = type
        if (!match.confirmWeapon()) return false
        startRound()
        onStatus?.invoke(match.status())
        return true
    }

    // ── Soldats ───────────────────────────────────────────────────────────────

    /**
     * Fait réfléchir la garnison, à **cadence fixe** et non à celle de l'écran.
     *
     * La tablette affiche 120 images par seconde : sans ce pas fixe, la file de chemins et les
     * cerveaux consommaient leur budget deux fois plus souvent que sur un écran 60 Hz, pour un
     * résultat rigoureusement identique — les soldats ne pensent de toute façon qu'à 20 Hz au
     * mieux. C'était du processeur brûlé, et de la chaleur, sans rien à l'écran en échange.
     */
    private fun updateSoldiers(frameDt: Float) {
        aiAccumulator += frameDt
        if (aiAccumulator < AI_STEP) return
        val dt = aiAccumulator
        aiAccumulator = 0f
        val started = System.nanoTime()
        updateOcclusion(dt)
        // Les morts ne participent plus aux collisions ni aux recherches, dès ce pas.
        for (i in units.indices) {
            val u = units[i]
            if (u.body.hp <= 0) { crowd.remove(u.body.id); u.brain.cancelRoute(); continue }
            u.elapsed += dt
            u.body.shotRecoil = (u.body.shotRecoil - dt).coerceAtLeast(0f)
        }
        routes?.update()
        pathFinder?.remainingExpansions = 256 // petites recherches d'abri et pas latéraux
        if (units.isEmpty()) return
        val deadline = System.nanoTime() + 2_000_000L
        var updated = 0
        var inspected = 0
        // Le plafond par image compte ceux qui *pensent* : un soldat non mis à jour garde sa
        // pose et son regard, et paraît figé. La vraie limite reste l'échéance en temps.
        while (inspected < units.size && updated < MAX_BRAINS_PER_FRAME) {
            if (updated > 0 && System.nanoTime() >= deadline) break
            updateCursor %= units.size
            val u = units[updateCursor]
            updateCursor = (updateCursor + 1) % units.size
            inspected++
            if (u.body.hp <= 0) continue
            val brain = u.brain
            val distance = distSq(brain.x, brain.z, player.x, player.z)
            val floorDistance = kotlin.math.abs(brain.y + 1.62 - player.eyeY)
            // Une escouade qui se regroupe ou monte à l'assaut réfléchit à pleine cadence, même
            // loin du joueur : sinon elle traverse la carte au ralenti et arrive en ordre dispersé.
            val committed = (u.squad?.stance ?: Squad.Stance.HOLD) != Squad.Stance.HOLD
            val interval = when {
                committed || brain.knowsPlayer || (floorDistance < 5 && distance < 40 * 40) -> .05f
                floorDistance < 8 && distance < 80 * 80 -> .2f
                else -> 1f
            }
            if (u.elapsed < interval) continue
            // Le pas ne doit être borné que pour éviter un bond visible chez un soldat qu'on
            // regarde de près : le brider au même 0,15 s pour la case « loin/réserve » (revu une
            // fois par seconde) revenait à figer son horloge interne à 15 % du temps réel — sa
            // patrouille et son balayage du regard s'étiraient alors sur des dizaines de secondes
            // au lieu de quelques-unes, et un homme malchanceux paraissait ne plus bouger du tout.
            // À cette distance, un homme qui avance d'un coup ne se voit de toute façon pas.
            val step = u.elapsed.coerceAtMost(if (interval >= 1f) interval else .15f)
            u.elapsed = 0f
            updated++
            brain.healthFraction = u.body.hp.toFloat() / u.body.maxHp.coerceAtLeast(1)
            noticeNearMisses(brain, u.body)
            firingBody = u.body
            firingUnit = u
            brain.update(step, player, shotSink)
            firingBody = null
            firingUnit = null
            crowd.move(u.body.id, brain.x, brain.y, brain.z)
            if (brain.justSpotted) r.eventBus.publish(GameEvent.MobNearby(false))

            if (com.Atom2Universe.app.BuildConfig.DEBUG) {
                if (!brain.follower.arrived && !brain.isMoving) u.stuckSeconds += step
                else { u.stuckSeconds = 0f; u.stuckLogged = false }
                if (u.stuckSeconds > STUCK_LOG_SECONDS && !u.stuckLogged) {
                    u.stuckLogged = true
                    android.util.Log.w("CaveAI", "bloqué id=${u.body.id} etat=${brain.state} " +
                        "pos=(${"%.1f".format(brain.x)},${"%.1f".format(brain.y)},${"%.1f".format(brain.z)}) " +
                        "escouade=${u.squad?.id}/${u.squad?.stance} attendTrajet=${brain.waitingForRoute} " +
                        "connaitJoueur=${brain.knowsPlayer} voitJoueur=${brain.seesPlayer}")
                }
            }

            val body = u.body
            body.weaponReload = brain.reloadProgress
            body.x = brain.x + source.originX
            body.y = brain.y + source.originY
            body.z = brain.z + source.originZ
            body.yaw = brain.yawDeg
            body.resting = !brain.isMoving
            if (brain.isMoving) {
                body.state = EnemyState.CHASE   // jambes et bras qui balancent
                body.animTime += step
            } else {
                body.state = EnemyState.WANDER
            }
        }
        if (com.Atom2Universe.app.BuildConfig.DEBUG) {
            val spent = commandNs + System.nanoTime() - started
            aiPeakNs = maxOf(aiPeakNs, spent)
            aiTotalNs += spent
            aiLogSeconds += dt
            if (aiLogSeconds >= 5f) {
                // « HOLD 5 » = cinq hommes debout en réserve ; « ASSAULT 3 » = trois à l'assaut.
                val roster = command?.all.orEmpty()
                    .filter { it.living > 0 }
                    .joinToString(" ") { "${it.stance.name.take(1)}${it.living}" }
                // Répartition par état : « ENG2 » = deux soldats en train de tirer. Un nombre qui
                // ne bouge plus d'un log à l'autre pendant qu'on regarde l'écran est le signal
                // qu'on cherche : quelque chose garde tout le monde dans cet état-là.
                val states = units.filter { it.body.hp > 0 }.groupingBy { it.brain.state }.eachCount()
                    .entries.joinToString(" ") { "${it.key.name.take(3)}${it.value}" }
                val stuck = units.count { it.stuckSeconds > STUCK_LOG_SECONDS }
                // aiPerSecUs est le chiffre qui compte pour la chauffe : combien de microsecondes
                // d'IA sont dépensées par seconde de jeu. 1 000 000 = un cœur saturé. En dessous
                // de ~50 000 (5 %), une baisse d'images ne vient pas d'ici.
                android.util.Log.i("CavePerf", "assaultSoldiers=${units.size} " +
                    "aiPerSecUs=${(aiTotalNs / 1000 / aiLogSeconds).toLong()} " +
                    "aiPeakUs=${aiPeakNs / 1000} routesWaiting=${routes?.waitingCount ?: 0} " +
                    "routeStalled=${units.count { it.brain.waitingForRoute }} bloques=$stuck " +
                    "etats=[$states] squads=[$roster]")
                aiPeakNs = 0L
                aiTotalNs = 0L
                aiLogSeconds = 0f
            }
        }
    }

    // ── Soldats cachés derrière les murs ──────────────────────────────────────

    // Ce qui cache un soldat à l'écran : un bloc plein et opaque. Ni vitre, ni escalier, ni dalle,
    // ni meuble — dans le doute on dessine : mieux vaut un soldat construit pour rien qu'un soldat
    // invisible alors qu'on devrait le voir. Cache par identifiant de bloc (0 = pas encore vu).
    private val occluderKind = ByteArray(65536)
    private val occluders = SolidGrid { x, y, z ->
        val block = source.map.blockAt(x, y, z)
        val key = block.toInt() and 0xFFFF
        var kind = occluderKind[key]
        if (kind == 0.toByte()) {
            val def = com.Atom2Universe.app.games.caves.node.BlockRegistry.get(block)
            val opaque = blocksMovement(block) && def != null &&
                !com.Atom2Universe.app.games.caves.node.BlockRegistry.isTransparent(block) &&
                !def.stairs && !def.slab && def.blockHeight >= 1f
            kind = if (opaque) OCCLUDES else SEE_THROUGH
            occluderKind[key] = kind
        }
        kind == OCCLUDES
    }
    private var occlusionCursor = 0
    private var occlusionClock = 0f

    /**
     * Marque les soldats que la caméra ne peut pas voir, pour que le renderer ne les construise pas.
     *
     * Mesuré sur tablette le 16/09/2026 : dès que le joueur se tournait vers la tour, les 60 gardes
     * des six étages entraient dans le cône de vue et **chaque corps était reconstruit à chaque
     * image**, à travers les dalles — 60 à 67 % du processeur de l'appli, contre 0,4 % pour toute
     * l'IA. Tourné vers un mur extérieur : plus rien. Le renderer n'éliminait que ce qui sort du
     * cône, jamais ce qui est derrière un mur.
     *
     * **Toujours dessinés, sans attendre de rayon** : ceux qui savent où est le joueur, ceux dont
     * l'escouade monte à l'assaut, et ceux qui sont à moins de [ALWAYS_DRAWN_DISTANCE] blocs.
     * Essai en jeu : sans cette règle, les soldats qui débouchaient d'un angle de couloir
     * apparaissaient en retard. Or ce sont justement eux qui viennent vers le joueur ; les rayons,
     * eux, servent à écarter les réserves des autres étages, qui étaient tout le coût.
     *
     * Pour les autres, une douzaine de soldats par pas : chacun revu toutes les ~80 ms, par rayons
     * vers la tête, le torse, puis les deux flancs — une épaule qui dépasse de l'angle suffit.
     * Un soldat reste dessiné [OCCLUSION_GRACE] après avoir été vu.
     */
    private fun updateOcclusion(dt: Float) {
        occlusionClock += dt
        if (units.isEmpty()) return
        val camera = r.camera
        val cx = camera.x - source.originX
        val cy = camera.y - source.originY
        val cz = camera.z - source.originZ
        // 1. Les soldats qui comptent, à chaque pas : une simple lecture de drapeaux.
        for (i in units.indices) {
            val u = units[i]
            if (u.body.hp <= 0) continue
            if (mustDraw(u, cx, cz)) {
                u.lastSeenAt = occlusionClock
                u.body.occluded = false
            }
        }
        // 2. Les autres, à tour de rôle, par rayons.
        repeat(minOf(units.size, OCCLUSION_PER_STEP)) {
            occlusionCursor %= units.size
            val u = units[occlusionCursor]
            occlusionCursor++
            if (u.body.hp <= 0 || mustDraw(u, cx, cz)) return@repeat
            if (visibleFrom(cx, cy, cz, u.brain)) u.lastSeenAt = occlusionClock
            u.body.occluded = occlusionClock - u.lastSeenAt > OCCLUSION_GRACE
        }
    }

    private fun mustDraw(u: Trooper, cx: Double, cz: Double): Boolean {
        val brain = u.brain
        return brain.knowsPlayer || brain.seesPlayer ||
            (u.squad?.stance ?: Squad.Stance.HOLD) != Squad.Stance.HOLD ||
            distSq(brain.x, brain.z, cx, cz) < ALWAYS_DRAWN_DISTANCE * ALWAYS_DRAWN_DISTANCE
    }

    /** Tête, torse, puis les deux flancs à hauteur de torse, perpendiculairement au regard. */
    private fun visibleFrom(cx: Double, cy: Double, cz: Double, brain: Soldier): Boolean {
        val x = brain.x; val y = brain.y; val z = brain.z
        if (LineOfSight.isClear(cx, cy, cz, x, y + OCCLUSION_HEAD, z, occluders)) return true
        if (LineOfSight.isClear(cx, cy, cz, x, y + OCCLUSION_CHEST, z, occluders)) return true
        val dx = x - cx; val dz = z - cz
        val length = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(1e-6)
        val sideX = -dz / length * OCCLUSION_SIDE
        val sideZ = dx / length * OCCLUSION_SIDE
        return LineOfSight.isClear(cx, cy, cz, x + sideX, y + OCCLUSION_CHEST, z + sideZ, occluders) ||
            LineOfSight.isClear(cx, cy, cz, x - sideX, y + OCCLUSION_CHEST, z - sideZ, occluders)
    }

    /**
     * Prévient un soldat qu'une balle du joueur va passer tout près de lui.
     *
     * On projette la trajectoire de la balle au lieu de comparer des positions : à 120 blocs par
     * seconde, une balle traverse plus de six blocs entre deux images et ne serait jamais vue
     * « à côté » du soldat.
     */
    private fun noticeNearMisses(brain: Soldier, body: Enemy) {
        for (p in r.projectiles) {
            if (p.fromEnemy || p.stuck) continue
            val dx = body.x - p.x
            val dy = body.y + NEAR_MISS_CHEST - p.y
            val dz = body.z - p.z
            val ahead = dx * p.dirX + dy * p.dirY + dz * p.dirZ
            if (ahead < 0.0 || ahead > NEAR_MISS_LOOKAHEAD) continue
            val ex = dx - p.dirX * ahead; val ey = dy - p.dirY * ahead; val ez = dz - p.dirZ * ahead
            if (ex * ex + ey * ey + ez * ez <= NEAR_MISS_RADIUS * NEAR_MISS_RADIUS) {
                brain.onNearMiss()
                return
            }
        }
    }

    /**
     * Un des leurs vient de tomber en (x, eyeY, z) [coordonnées locales à la carte] : ceux qui ont
     * une ligne de vue dégagée jusque-là le savent, même bien au-delà de la portée d'ouïe normale.
     *
     * Ce n'est pas de la triche : voir un camarade s'effondrer à ciel ouvert porte l'information
     * bien plus loin qu'un mur ne laisse passer un bruit, exactement l'inverse d'un couloir fermé,
     * où la ligne de vue s'arrête de toute façon à quelques pas. La portée d'ouïe elle-même
     * (`hearingRange`) reste inchangée : ceci ne s'ajoute que là où l'œil porte, jamais à travers
     * une cloison.
     */
    private fun alertWitnesses(x: Double, eyeY: Double, z: Double) {
        for (u in units) {
            val brain = u.brain
            if (brain.knowsPlayer) continue
            if (isTower && kotlin.math.abs(brain.y + EYE_HEIGHT - eyeY) >= 4.0) continue
            val range = if (LineOfSight.isClear(brain.x, brain.y + EYE_HEIGHT, brain.z, x, eyeY, z, solid))
                brain.tuning.alertedSightRange else brain.tuning.hearingRange
            brain.hearNoise(x, eyeY, z, range)
        }
    }

    /** Retire les soldats abattus. Renvoie vrai si l'affichage de la partie doit être rafraîchi. */
    private fun collectFallenSoldiers(): Boolean {
        var changed = false
        var i = units.size - 1
        while (i >= 0) {
            val body = units[i].body
            if (body.hp <= 0) {
                units[i].brain.cancelRoute()
                crowd.remove(body.id)
                units.removeAt(i)
                bodies.remove(body)
                if (rng.nextFloat() < AssaultRecovery.DROP_CHANCE)
                    pickups.add(ShieldPickup(body.x, body.y, body.z))
                changed = true
                val headshot = lastHitWasHead.remove(body.id) == true
                if (headshot) onHeadshotKill?.invoke()
                alertWitnesses(body.x - source.originX, body.y - source.originY + EYE_HEIGHT,
                    body.z - source.originZ)
                // Pas MobDied : lui déclenche le butin de la survie, qui ne connaît pas les soldats.
                r.eventBus.publish(GameEvent.SoldierDown)
                if (match.onTargetDown(headshot) == AssaultMatch.Event.ROUND_ENDED) {
                    clearSoldiers()
                    return true
                }
            }
            i--
        }
        return changed
    }

    private fun startRound() {
        clearSoldiers()
        respawnPlayer()
        recovery.reset()
        spawnSoldiers()
        match.setDeployedTargets(units.size)
    }

    private fun clearSoldiers() {
        command?.clear()
        units.forEach { it.brain.cancelRoute() }
        routes?.clear()
        crowd.clear()
        updateCursor = 0
        units.clear()
        bodies.clear()
        pickups.clear()
        lastHitWasHead.clear()
        // Les balles encore en vol ne doivent pas toucher le joueur pendant la pause.
        r.projectiles.clear()
    }

    private fun collectShieldPickups() {
        if (r.playerNode.shield >= r.playerNode.maxShield) return
        val feetY = r.camera.playerY - source.originY - 1.62
        var restored = 0
        var i = pickups.lastIndex
        while (i >= 0 && r.playerNode.shield < r.playerNode.maxShield) {
            val pickup = pickups[i]
            val x = pickup.x - source.originX
            val y = pickup.y - source.originY
            val z = pickup.z - source.originZ
            // Le test vertical et le segment empêchent la collecte à travers murs et dalles.
            if (distSq(x, z, player.x, player.z) <= 1.8 * 1.8 &&
                kotlin.math.abs(y - feetY) <= 1.1 &&
                LineOfSight.isClear(player.x, player.eyeY, player.z, x, y + .5, z, solid)) {
                val amount = recovery.recharge(pickup.charge)
                pickup.charge -= amount
                restored += amount
                if (pickup.charge == 0) pickups.removeAt(i)
            }
            i--
        }
        if (restored > 0) onShieldCollected?.invoke(restored)
    }

    /**
     * Déploie la garnison **par escouades**. Chaque groupe de quatre à six hommes apparaît au même
     * endroit : c'est ce qui lui donne un secteur à tenir et un côté d'où arriver. Sans ça, le
     * premier ordre de regroupement ferait traverser la carte à chacun séparément, et l'assaut
     * arriverait en file indienne — précisément ce que la coordination cherche à éviter.
     */
    private fun spawnSoldiers() {
        val grid = navGrid ?: return
        val finder = pathFinder ?: return
        val headquarters = command ?: return
        if (grid.nodeCount == 0) return
        for (nodes in planSquads(grid)) {
            val members = ArrayList<Trooper>(nodes.size)
            for (n in nodes) members += enlistSoldier(grid, finder, n) ?: continue
            if (members.isEmpty()) continue
            val squad = headquarters.enlist(members)
            for (m in members) m.squad = squad
        }
        // La garnison sait par où l'attaque commence : c'est l'entrée, pas un don de voyance.
        val spawn = source.spawnPoint(0)
        headquarters.start(spawn[0].toDouble() - source.originX,
            spawn[1].toDouble() - source.originY, spawn[2].toDouble() - source.originZ)
    }

    /** Les cases de départ, déjà groupées par escouade, selon la carte jouée. */
    private fun planSquads(grid: NavGrid): List<IntArray> {
        towerDeployment?.let { return it.chooseSquads(rng) }
        suburbDeployment?.let { return it.chooseSquads(squadCount, squadSize, rng) }
        val pool = deploymentPool(grid)
        if (pool.isEmpty()) return emptyList()
        return SquadSpawn.cluster(grid, pool, squadCount, squadSize, rng)
    }

    /**
     * Cases candidates hors carte dédiée : loin du point d'apparition du joueur, et au sol dans la
     * moitié adverse pour le quartier des fonderies (les toits n'ont jamais été des postes).
     */
    private fun deploymentPool(grid: NavGrid): List<Int> {
        val playerSpawn = source.spawnPoint(0)
        val px = playerSpawn[0].toDouble() - source.originX
        val pz = playerSpawn[2].toDouble() - source.originZ
        val groundY = if (isArena) source.map.spawnsB.first().y else -1
        val halfX = source.map.sizeX / 2
        val pool = ArrayList<Int>()
        for (n in 0 until grid.nodeCount) {
            if (groundY >= 0 && (grid.nodeY[n] != groundY || grid.nodeX[n] < halfX)) continue
            val x = grid.nodeX[n] + 0.5
            val z = grid.nodeZ[n] + 0.5
            if (distSq(x, z, px, pz) < MIN_DIST_FROM_PLAYER * MIN_DIST_FROM_PLAYER) continue
            pool.add(n)
        }
        return pool
    }

    /** Corps et cerveau d'un soldat sur la case [node], ou null si la place est déjà prise. */
    private fun enlistSoldier(grid: NavGrid, finder: PathFinder, node: Int): Trooper? {
        val x = grid.nodeX[node] + 0.5
        val y = grid.nodeY[node].toDouble()
        val z = grid.nodeZ[node] + 0.5
        if (units.any { SoldierCollision.overlaps(x, y, z, it.brain.x, it.brain.y, it.brain.z) })
            return null
        val weaponType = roundWeapon
        val profile = RangedProfile.all.getValue(weaponType)
        val tuning = soldierTuning.copy(bulletSpeed = profile.speed, bulletRange = profile.range,
            fireInterval = profile.interval, magazineSize = profile.magazine,
            reloadSeconds = profile.reload).withPersonality(rng)
        val id = nextSoldierId++
        val clearance = BodyClearance { bx, by, bz ->
            SoldierCollision.clearsWorld(solid, bx, by, bz) &&
                !crowd.overlaps(bx, by, bz, except = id) &&
                !(r.playerNode.hp > 0 && SoldierCollision.overlaps(bx, by, bz,
                    r.camera.playerX - source.originX, r.camera.playerY - source.originY - EYE_HEIGHT,
                    r.camera.playerZ - source.originZ,
                    (r.camera.eyeY - r.camera.playerY + 1.8).coerceAtLeast(.5)))
        }
        val brain = Soldier(grid, solid, finder, rng, tuning, clearance, routes)
        brain.place(x, y, z)
        val body = Enemy(id, SOLDIER, x + source.originX, y + source.originY, z + source.originZ)
        body.hp = body.maxHp
        body.heldWeaponType = weaponType
        // Cadences identiques au joueur ; dégâts ajustés pour le solo, surtout la SMG.
        val damage = when (weaponType) {
            "smg" -> 3; "dual_pistols" -> 5; "shotgun" -> 24; "lever_rifle" -> 16; else -> 8
        }
        val trooper = Trooper(body, brain, damage).also { it.elapsed = rng.nextFloat() }
        units += trooper
        bodies += body
        crowd.move(id, brain.x, brain.y, brain.z)
        return trooper
    }

    // ── Joueur ────────────────────────────────────────────────────────────────

    private fun refreshPlayerPosition() {
        val camera = r.camera
        player.x = camera.playerX - source.originX
        player.eyeY = camera.eyeY - source.originY
        player.z = camera.playerZ - source.originZ
        player.alive = r.playerNode.hp > 0
    }

    private fun updatePlayerSnapshot(dt: Float) {
        refreshPlayerPosition()
        // Vitesse à l'horizontale, pour que les soldats visent moins bien un joueur qui court.
        if (!prevPlayerX.isNaN() && dt > 0f) {
            val vx = (player.x - prevPlayerX) / dt
            val vz = (player.z - prevPlayerZ) / dt
            // Une téléportation (réapparition) n'est pas une course.
            val teleported = vx * vx + vz * vz > MAX_PLAYER_SPEED * MAX_PLAYER_SPEED
            player.velX = if (teleported) 0.0 else vx
            player.velZ = if (teleported) 0.0 else vz
        }
        prevPlayerX = player.x
        prevPlayerZ = player.z
    }

    private fun respawnPlayer() {
        val spawn = source.spawnPoint()
        val camera = r.camera
        camera.playerX = spawn[0].toDouble()
        camera.playerY = spawn[1].toDouble()
        camera.playerZ = spawn[2].toDouble()
        // Regard tourné vers le centre de la carte.
        val centerX = source.originX + source.map.sizeX / 2.0
        val centerZ = source.originZ + source.map.sizeZ / 2.0
        camera.yaw = Math.toDegrees(atan2(centerX - camera.playerX, centerZ - camera.playerZ)).toFloat()
        camera.pitch = 0f
        r.physics.reset()
    }

    private fun distSq(ax: Double, az: Double, bx: Double, bz: Double): Double {
        val dx = ax - bx; val dz = az - bz
        return dx * dx + dz * dz
    }

    companion object {
        /** Blocs de chute sous la carte avant d'être ramené. */
        const val FALL_LIMIT = 16

        /** Midi dans le cycle de CaveRenderer (6 h = 0 ms, 100 000 ms par heure de jour). */
        const val NOON_MS = 600_000L

        /** Hauteur des yeux d'un personnage debout, joueur comme soldat. */
        const val EYE_HEIGHT = 1.62

        /** Temps sans avancer avant qu'un soldat soit signalé « bloqué » dans les logs (debug). */
        const val STUCK_LOG_SECONDS = 2f

        // Effectifs : une seule escouade attaque à la fois, la garnison entière sert de réserve.
        const val FIELD_SQUAD_SIZE = 4
        /**
         * La tour est un parcours : ses postes sont placés à la main, ils ne glissent pas vers le
         * joueur annoncé, et un appel au secours n'est suivi que par les escouades du même étage —
         * celles des autres étages restent à leur poste au lieu de se masser à la verticale du combat.
         */
        val TOWER_COMMAND = SquadTuning(holdDriftSpeed = 0f, reinforceFloorBand = 4.0)
        const val SUBURB_SQUADS = 2
        const val FIELD_SQUADS = 3

        /** Sans nouveau pas publié pendant ce temps, le joueur s'est arrêté. */
        const val STEP_SILENCE = 0.25f
        const val STEP_MIN_INTERVAL = 0.2f
        /** Portée d'un pas : marcher s'entend à une dizaine de blocs, courir à près de vingt. */
        const val FOOTSTEP_RANGE_WALK = 11.0
        const val FOOTSTEP_RANGE_RUN = 18.0
        /** Flou sur l'origine du bruit : le soldat vient voir la zone, pas le bloc exact. */
        const val FOOTSTEP_BLUR = 1.0

        /** Une balle qui passe à moins d'un bloc et demi du torse fait réagir le soldat. */
        const val NEAR_MISS_RADIUS = 1.6
        const val NEAR_MISS_LOOKAHEAD = 12.0
        const val NEAR_MISS_CHEST = 1.0
        const val ROUND_SECONDS = 300f
        const val PLAYER_MAX_HP = 100
        const val HEADSHOT_MULTIPLIER = 2f
        const val STATUS_INTERVAL = 0.1f
        /** Cerveaux mis à jour par pas d'IA, sous réserve de l'échéance de 2 ms. */
        const val MAX_BRAINS_PER_FRAME = 8

        /** Pas de réflexion de la garnison : 60 fois par seconde, quel que soit l'écran. */
        const val AI_STEP = 1f / 60f

        // Occlusion des soldats par le décor (voir updateOcclusion).
        const val OCCLUSION_PER_STEP = 12
        const val OCCLUSION_GRACE = .2f
        const val ALWAYS_DRAWN_DISTANCE = 12.0
        /** Demi-largeur visée sur les flancs : épaule et fusil dépassent avant le centre du corps. */
        const val OCCLUSION_SIDE = .55
        const val OCCLUSION_HEAD = 1.55
        const val OCCLUSION_CHEST = .9
        const val OCCLUDES: Byte = 1
        const val SEE_THROUGH: Byte = 2

        /** Dégâts d'une balle de soldat : une douzaine suffisent à abattre le joueur. */
        const val SOLDIER_DAMAGE = 8

        const val MIN_DIST_FROM_PLAYER = 40.0
        const val MAX_PLAYER_SPEED = 30.0

        /** Même règle que la physique du joueur : l'air, la déco et l'eau ne bloquent pas. */
        fun blocksMovement(block: Short) = block != AIR && !isDecoration(block) && !isWater(block)

        /** Soldat : 100 PV (3 à 5 balles de pistolet au corps, 2 ou 3 à la tête), insensible aux effets élémentaires. */
        val SOLDIER = MobDef(
            id = "assault_soldier", hpBase = 100, damageBase = SOLDIER_DAMAGE, speed = 4.2f,
            attackRange = 45.0, detectRange = 45.0, eyeHeight = 1.62f, radius = 0.45f,
            spriteScale = 0.9f, hpScalePerLevel = 1.0, hpScaleCap = 1.0, damageScalePer3Lvl = 0,
            speedScalePerLevel = 0f, biomes = emptyList(), model = "soldier", spawnZoneMin = 0,
            spawnWeight = 0f, lootTable = "", behavior = "soldier", bossEligible = false, xpBase = 0,
            resistances = mapOf("bleed" to 0f, "poison" to 0f, "fire" to 0f, "ice" to 0f, "electric" to 0f),
        )
    }
}
