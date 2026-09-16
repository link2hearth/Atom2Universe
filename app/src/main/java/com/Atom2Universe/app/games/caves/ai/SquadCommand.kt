package com.Atom2Universe.app.games.caves.ai

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Ce que l'état-major peut demander à un soldat : le seul lien entre la radio et le corps du jeu.
 *
 * Remarquer ce qui **n'est pas** dans cette interface : aucun moyen d'apprendre à un soldat qu'il
 * voit le joueur. La radio déplace les hommes, elle ne leur prête pas ses yeux.
 */
internal interface SquadMember {
    val alive: Boolean
    val x: Double
    val y: Double
    val z: Double
    /** Il voit le joueur en ce moment, de ses propres yeux. */
    val seesTarget: Boolean
    /** Il vient d'être touché ou frôlé : la discrétion n'a plus d'intérêt. */
    val shaken: Boolean
    /**
     * Case où se rendre tant qu'il ne voit rien lui-même ; -1 le laisse libre. Un ordre [strict]
     * passe avant ce qu'il a seulement entendu : c'est ce qui tient une escouade groupée.
     */
    fun order(node: Int, strict: Boolean)
    /** Position approximative annoncée à la radio : il sait de quel côté regarder. */
    fun radioContact(x: Double, z: Double)
    /** Secteur à tenir : hors contact, il n'en sort pas et n'y tire pas au-delà. */
    fun leashTo(x: Double, z: Double, radius: Double)
}

/** Réglages de la coordination. Distances en blocs, durées en secondes. */
internal data class SquadTuning(
    /** Cadence des annonces : la position qui circule n'est pas rafraîchie plus souvent. */
    val radioIntervalSeconds: Float = 2f,
    /** Flou sur la position annoncée : « il est vers l'entrepôt », pas « il est sur ce bloc ». */
    val radioBlur: Double = 3.0,
    /** Rayon du secteur tenu par une escouade en réserve. */
    val holdRadius: Double = 14.0,
    /** Distance à laquelle l'escouade activée se regroupe, hors de vue du joueur. */
    val rallyDistance: Double = 22.0,
    /** Un homme à moins de ça de son poste de regroupement est considéré en place. */
    val rallyTolerance: Double = 6.0,
    /** Au-delà, l'assaut part même si les traînards ne sont pas arrivés. */
    val rallyTimeoutSeconds: Float = 25f,
    /** Une fois que l'éclaireur a trouvé le joueur, le reste n'a plus que ça pour se placer. */
    val contactRallySeconds: Float = 8f,
    /** Rayon des postes de tir autour de la position annoncée. */
    val engageRadius: Double = 12.0,
    /**
     * Nouveau plan d'assaut si la position annoncée a bougé d'autant de blocs. Le seuil reste
     * bien au-dessus du flou des annonces : un joueur immobile ne doit pas faire replanifier
     * l'escouade à chaque message, chaque replanification coûtant un A* par homme.
     */
    val replanDistance: Double = 8.0,
    /** Filet de sécurité : au bout de ce temps, on refait le plan même sans mouvement. */
    val replanSeconds: Float = 6f,
    /** La relève part quand il ne reste plus que tant d'hommes debout dans l'escouade engagée. */
    val reliefAtSurvivors: Int = 1,
    /** Souffle laissé au joueur avant que la relève ne soit lancée. */
    val reliefSeconds: Float = 7f,
    /** Même chose lorsque l'escouade engagée a été anéantie. */
    val wipedPauseSeconds: Float = 5f,
)

/**
 * Une escouade : quatre à six hommes déployés ensemble, qui tiennent un secteur puis montent à
 * l'assaut ensemble. Les postes ([slots]) sont indexés comme [members] : un mort garde son index.
 */
internal class Squad(val id: Int, val members: Array<SquadMember>) {
    /**
     * Le poste de chacun. Attribué au rang dans l'escouade et **recalculé à chaque plan** : quand
     * l'éclaireur tombe, quelqu'un d'autre prend la pointe. Un groupe qui ne redistribue pas ses
     * rôles après ses pertes se comporte comme quatre individus.
     */
    enum class Role {
        /** L'éclaireur : il ne se regroupe pas, il part devant chercher le contact. */
        POINT,
        /** La base de feu : elle reste en retrait, en vue, et cloue le joueur sur place. */
        ANCHOR,
        /** Le contournement : large détour hors de vue, puis retour sur le flanc ou le dos. */
        FLANK,
        /** Le soutien : il épaule la base de feu et bouche les trous. */
        SUPPORT,
    }

    enum class Stance {
        /** En réserve : ils tiennent leur secteur et ne courent pas au bruit lointain. */
        HOLD,
        /** Activée : elle se rassemble à couvert avant de se montrer. */
        RALLY,
        /** Elle monte à l'assaut : un homme fixe le joueur de face, les autres contournent. */
        ASSAULT,
    }

    var stance = Stance.HOLD; internal set
    var anchorX = 0.0; internal set
    var anchorY = 0.0; internal set
    var anchorZ = 0.0; internal set
    var rallyNode = -1; internal set
    internal var stanceTime = 0f
    internal var planAge = 0f
    internal var plannedX = 0.0
    internal var plannedZ = 0.0
    /** Le plan en cours a été fait au contact : plus de détours, on se bat à découvert. */
    internal var hadContact = false
    internal val slots = IntArray(members.size) { -1 }
    /** Poste final d'un contournement, tant que son point de passage n'est pas atteint. */
    internal val pendingPosts = IntArray(members.size) { -1 }
    val roles = arrayOfNulls<Role>(members.size)

    // Parcours par indice : ces trois-là sont lus plusieurs fois par décision, et un `for (m in
    // liste)` sur une List alloue un itérateur à chaque appel.
    val living: Int get() {
        var n = 0
        for (i in members.indices) if (members[i].alive) n++
        return n
    }

    internal inline fun anyAlive(predicate: (SquadMember) -> Boolean): Boolean {
        for (i in members.indices) {
            val m = members[i]
            if (m.alive && predicate(m)) return true
        }
        return false
    }

    internal fun centroidX(): Double {
        var sum = 0.0; var n = 0
        for (i in members.indices) {
            val m = members[i]
            if (m.alive) { sum += m.x; n++ }
        }
        return if (n == 0) anchorX else sum / n
    }

    internal fun centroidZ(): Double {
        var sum = 0.0; var n = 0
        for (i in members.indices) {
            val m = members[i]
            if (m.alive) { sum += m.z; n++ }
        }
        return if (n == 0) anchorZ else sum / n
    }
}

/**
 * L'état-major du camp adverse : le « talkie-walkie » du mode Assaut.
 *
 * Le problème qu'il résout : avec une garnison entière qui converge, le joueur est soit submergé,
 * soit servi en file indienne. On veut le contraire — **une seule escouade sur lui à la fois**,
 * mais qui arrive groupée et le prend en tenaille, pendant que les autres tiennent leur secteur.
 *
 * Ce que la radio sait, et ce qu'elle ne sait pas :
 * - **elle connaît en permanence la position approximative du joueur** ([reportedX] / [reportedZ]),
 *   floutée de quelques blocs et rafraîchie par intervalles. C'est la seule concession assumée :
 *   sans elle, une garnison de soixante hommes ne trouverait jamais un joueur qui se déplace.
 * - **elle ne voit pas pour ses soldats.** Un soldat ne tire que sur ce que ses propres yeux
 *   trouvent (voir [Soldier]) ; la radio ne fait que l'amener au bon endroit.
 *
 * Le cycle d'une escouade : [Squad.Stance.HOLD] (réserve, secteur tenu) → [Squad.Stance.RALLY]
 * (regroupement à couvert, à une vingtaine de blocs) → [Squad.Stance.ASSAULT] (postes de tir
 * répartis tout autour). La relève est lancée quand l'escouade engagée est anéantie, ou qu'il ne
 * lui reste qu'un homme : la pression ne retombe jamais tout à fait, sans jamais doubler non plus.
 */
internal class SquadCommand(
    private val grid: NavGrid,
    private val world: SolidGrid,
    private val rng: Random,
    val tuning: SquadTuning = SquadTuning(),
) {
    private val squads = ArrayList<Squad>()
    val all: List<Squad> get() = squads

    /** L'escouade qui a la main. Une rescapée peut continuer à se battre après avoir passé le relais. */
    var active: Squad? = null; private set

    /** Position du joueur telle qu'elle circule à la radio : approximative, et un peu en retard. */
    var reportedX = 0.0; private set
    var reportedY = 0.0; private set
    var reportedZ = 0.0; private set

    private var sinceTick = 0f
    private var radioLeft = 0f
    private var reliefLeft = 0f
    private var awaitingRelief = false
    /** Direction d'où est venue la dernière attaque : la relève arrive d'ailleurs. */
    private var lastApproachDeg = Float.NaN
    private var nextId = 1

    fun clear() {
        squads.clear()
        active = null
        sinceTick = 0f; radioLeft = 0f; reliefLeft = 0f; awaitingRelief = false
        lastApproachDeg = Float.NaN
        nextId = 1
    }

    /** Enrôle une escouade déployée ensemble ; son secteur est le barycentre de son déploiement. */
    fun enlist(members: List<SquadMember>): Squad {
        val squad = Squad(nextId++, members.toTypedArray())
        squad.anchorX = squad.centroidX()
        squad.anchorZ = squad.centroidZ()
        squad.anchorY = members.firstOrNull()?.y ?: 0.0
        squads.add(squad)
        holdSector(squad)
        return squad
    }

    /** Début de manche : la garnison est prévenue que l'attaque vient de là. */
    fun start(playerX: Double, playerY: Double, playerZ: Double) {
        reportedX = playerX; reportedY = playerY; reportedZ = playerZ
        radioLeft = tuning.radioIntervalSeconds
        broadcast()
        awaitingRelief = true
        reliefLeft = tuning.wipedPauseSeconds
    }

    /**
     * [playerY] est la hauteur des **pieds** du joueur, comme les cases de la grille.
     *
     * Appelé à chaque image, mais **il ne décide que six fois par seconde**. Un état-major n'est
     * pas un réflexe : ses balayages de postes et ses comptages coûtaient, à 120 images par
     * seconde, vingt fois le prix des décisions qu'ils servaient — pour rien, puisque rien ne
     * change entre deux images.
     */
    fun update(dt: Float, playerX: Double, playerY: Double, playerZ: Double) {
        if (squads.isEmpty()) return
        sinceTick += dt
        if (sinceTick < TICK_SECONDS) return
        val step = sinceTick
        sinceTick = 0f
        decide(step, playerX, playerY, playerZ)
    }

    private fun decide(dt: Float, playerX: Double, playerY: Double, playerZ: Double) {
        radioLeft -= dt
        if (radioLeft <= 0f) {
            radioLeft = tuning.radioIntervalSeconds
            val blur = tuning.radioBlur
            reportedX = playerX + rng.nextDouble(-blur, blur)
            reportedY = playerY
            reportedZ = playerZ + rng.nextDouble(-blur, blur)
            broadcast()
        }

        val current = active
        if (current != null && current.living == 0) {
            active = null
            awaitingRelief = false
            scheduleRelief(tuning.wipedPauseSeconds)
        } else if (current != null && current.living <= tuning.reliefAtSurvivors) {
            scheduleRelief(tuning.reliefSeconds)
        } else if (current == null) {
            scheduleRelief(tuning.wipedPauseSeconds)
        }
        if (awaitingRelief) {
            reliefLeft -= dt
            if (reliefLeft <= 0f) {
                awaitingRelief = false
                activateNext()
            }
        }

        for (i in squads.indices) {
            val s = squads[i]
            if (s.living == 0) continue
            s.stanceTime += dt
            when (s.stance) {
                Squad.Stance.HOLD -> Unit
                Squad.Stance.RALLY -> if (rallyReady(s)) beginAssault(s)
                Squad.Stance.ASSAULT -> {
                    advanceDetours(s)
                    // Dès qu'un des leurs a le joueur en vue, toute l'escouade se recale sur sa
                    // position réelle : c'est le « Contact ! » de la radio, joué en déplacements
                    // plutôt qu'en réplique. Et on cesse de contourner : ils sont déjà trouvés.
                    val contact = s.anyAlive { it.seesTarget }
                    s.planAge += dt
                    // Le contact peut clignoter (le joueur passe derrière un mur) : on ne refait
                    // pas un plan, donc un A* par homme, à chaque battement de paupière. Le
                    // besoin reste en attente tant qu'il n'est pas servi — sans quoi la première
                    // image de contact consommerait la transition et le plan ne viendrait jamais.
                    val wantsContactPlan = contact && !s.hadContact
                    if ((wantsContactPlan && s.planAge >= CONTACT_REPLAN_MIN) ||
                        s.planAge >= tuning.replanSeconds ||
                        distSq(s.plannedX, s.plannedZ, reportedX, reportedZ) >
                        tuning.replanDistance * tuning.replanDistance)
                        planAssault(s, sneak = !contact)
                }
            }
        }
    }

    // ── Radio ─────────────────────────────────────────────────────────────────

    private fun broadcast() {
        for (i in squads.indices) {
            val members = squads[i].members
            for (j in members.indices) {
                val m = members[j]
                if (m.alive) m.radioContact(reportedX, reportedZ)
            }
        }
    }

    private fun scheduleRelief(seconds: Float) {
        if (awaitingRelief) return
        awaitingRelief = true
        reliefLeft = seconds
    }

    /**
     * Choisit l'escouade qui monte. Pas simplement la plus proche : celle qui arrivera dans un
     * délai raisonnable **par un autre côté que la précédente**, pour que le joueur ne prenne pas
     * trois vagues de suite dans le même couloir.
     */
    private fun activateNext() {
        var best: Squad? = null
        var bestScore = Float.NEGATIVE_INFINITY
        for (i in squads.indices) {
            val s = squads[i]
            if (s.stance != Squad.Stance.HOLD || s.living == 0) continue
            val score = approachScore(s)
            if (score > bestScore) { bestScore = score; best = s }
        }
        val chosen = best ?: return   // plus de réserve : ceux qui se battent finissent seuls
        active = chosen
        beginRally(chosen)
    }

    private fun approachScore(s: Squad): Float {
        // Un étage coûte bien plus qu'un bloc à plat : il faut d'abord trouver l'escalier.
        val travel = sqrt(distSq(s.anchorX, s.anchorZ, reportedX, reportedZ)) +
            abs(s.anchorY - reportedY) * FLOOR_DETOUR
        var score = -travel.toFloat() / TRAVEL_SCALE
        if (!lastApproachDeg.isNaN()) {
            val bearing = bearingTo(reportedX, reportedZ, s.anchorX, s.anchorZ)
            score += abs(normalizeDeg(bearing - lastApproachDeg)) / BEARING_SCALE
        }
        return score + rng.nextFloat() * .5f
    }

    // ── Postures ──────────────────────────────────────────────────────────────

    private fun holdSector(s: Squad) {
        s.stance = Squad.Stance.HOLD
        s.stanceTime = 0f
        s.slots.fill(-1)
        for (i in s.members.indices) {
            val m = s.members[i]
            m.leashTo(s.anchorX, s.anchorZ, tuning.holdRadius)
            m.order(-1, strict = false)
        }
    }

    /**
     * Regroupement : un point à couvert du côté d'où ils viennent, et des places autour — **sauf
     * pour l'éclaireur**, qui part aussitôt devant. C'est lui qu'on voit arriver en premier, et
     * souvent tomber en premier ; pendant ce temps le reste du groupe se met en place.
     */
    private fun beginRally(s: Squad) {
        s.stance = Squad.Stance.RALLY
        s.stanceTime = 0f
        s.slots.fill(-1)
        s.pendingPosts.fill(-1)
        s.roles.fill(null)   // un mort ne tient plus de rôle : sa place est à reprendre
        clearTaken()
        val approach = bearingTo(reportedX, reportedZ, s.centroidX(), s.centroidZ())
        lastApproachDeg = approach
        val rally = take(postNear(reportedX, reportedY, reportedZ, approach, tuning.rallyDistance,
            Sight.AVOID, taken))
        s.rallyNode = rally
        val rx = if (rally >= 0) grid.nodeX[rally] + .5 else s.centroidX()
        val ry = if (rally >= 0) grid.nodeY[rally].toDouble() else s.anchorY
        val rz = if (rally >= 0) grid.nodeZ[rally] + .5 else s.centroidZ()
        var rank = 0
        var staged = 0
        for ((index, m) in s.members.withIndex()) {
            if (!m.alive) continue
            val role = ROLES[rank % ROLES.size]
            s.roles[index] = role
            rank++
            m.leashTo(rx, rz, Double.POSITIVE_INFINITY)   // libérés de leur secteur
            if (role == Squad.Role.POINT) {
                // Souple, et pas de regroupement : sa mission est d'aller voir.
                m.order(take(postNear(reportedX, reportedY, reportedZ, approach,
                    tuning.engageRadius * SCOUT_RADIUS, Sight.REQUIRED, taken)).also {
                    s.slots[index] = it
                }, strict = false)
                continue
            }
            val node = if (staged == 0 && rally >= 0) rally
                else take(postNear(rx, ry, rz, staged * STAGING_SPREAD_DEG, STAGING_SPACING,
                    Sight.ANY, taken))
            staged++
            s.slots[index] = node
            // Strict : une fusillade au loin ne doit pas vider le point de regroupement.
            m.order(node, strict = true)
        }
    }

    /**
     * L'assaut part dès que les deux tiers de l'escouade sont en place — ou tout de suite si elle
     * est repérée : attendre bien rangé pendant qu'on vous tire dessus n'a aucun sens.
     */
    private fun rallyReady(s: Squad): Boolean {
        if (s.stanceTime >= tuning.rallyTimeoutSeconds) return true
        var living = 0
        var inPlace = 0
        var contact = false
        for ((index, m) in s.members.withIndex()) {
            if (!m.alive) continue
            if (m.shaken) return true                     // on leur tire dessus : c'est parti
            if (m.seesTarget) contact = true
            if (s.roles[index] == Squad.Role.POINT) continue   // il est déjà parti devant
            living++
            val node = s.slots[index]
            if (node < 0) { inPlace++; continue }
            if (distSq(m.x, m.z, grid.nodeX[node] + .5, grid.nodeZ[node] + .5) <=
                tuning.rallyTolerance * tuning.rallyTolerance) inPlace++
        }
        // L'éclaireur a trouvé le joueur : le gros du groupe n'a plus longtemps pour se placer.
        if (contact && s.stanceTime >= tuning.contactRallySeconds) return true
        return living == 0 || inPlace * 3 >= living * 2
    }

    private fun beginAssault(s: Squad) {
        s.stance = Squad.Stance.ASSAULT
        s.stanceTime = 0f
        planAssault(s, sneak = !s.anyAlive { it.seesTarget })
    }

    /**
     * Distribue les rôles et les postes autour de la position annoncée. Chaque rang a sa place :
     * pointe au contact, base de feu en retrait, contournements par les flancs, soutien entre les
     * deux. Les rôles sont redistribués à chaque plan, donc les pertes se comblent d'elles-mêmes.
     *
     * Un contourneur ne fonce pas vers son poste : il reçoit d'abord un **point de passage** large
     * et hors de vue ([advanceDetours] le rabat ensuite). C'est ce détour qu'on lit comme « il m'a
     * pris à revers » — sans lui, quatre lignes droites vers quatre points restent quatre lignes
     * droites, et le joueur les voit toutes arriver.
     */
    private fun planAssault(s: Squad, sneak: Boolean = true) {
        s.planAge = 0f
        s.hadContact = !sneak
        s.plannedX = reportedX
        s.plannedZ = reportedZ
        val approach = bearingTo(reportedX, reportedZ, s.centroidX(), s.centroidZ())
        s.slots.fill(-1)
        s.pendingPosts.fill(-1)
        s.roles.fill(null)
        clearTaken()
        var rank = 0
        for ((index, m) in s.members.withIndex()) {
            if (!m.alive) continue
            val slot = rank % ROLES.size
            rank++
            s.roles[index] = ROLES[slot]
            val post = take(postNear(reportedX, reportedY, reportedZ, approach + POST_DEG[slot],
                tuning.engageRadius * POST_RADIUS[slot], Sight.REQUIRED, taken))
            val detour = if (sneak && DETOUR_RADIUS[slot] > 0f)
                take(postNear(reportedX, reportedY, reportedZ, approach + DETOUR_DEG[slot],
                    tuning.engageRadius * DETOUR_RADIUS[slot], Sight.AVOID, taken)) else -1
            if (detour >= 0 && detour != post) {
                s.slots[index] = detour
                s.pendingPosts[index] = post
            } else {
                s.slots[index] = post
            }
            // Souple : à l'assaut, un homme qui entend le joueur a raison d'aller voir.
            m.order(s.slots[index], strict = false)
        }
    }

    /** Un contourneur arrivé à son point de passage se rabat sur son poste de tir. */
    private fun advanceDetours(s: Squad) {
        for ((index, m) in s.members.withIndex()) {
            val post = s.pendingPosts[index]
            if (post < 0 || !m.alive) continue
            val waypoint = s.slots[index]
            if (waypoint >= 0 && distSq(m.x, m.z, grid.nodeX[waypoint] + .5,
                    grid.nodeZ[waypoint] + .5) > DETOUR_REACHED * DETOUR_REACHED) continue
            s.slots[index] = post
            s.pendingPosts[index] = -1
            m.order(post, strict = false)
        }
    }

    // Cases déjà attribuées pendant un plan : deux hommes ne visent pas le même carreau.
    private val taken = IntArray(16)
    private var takenCount = 0

    private fun clearTaken() { taken.fill(-1); takenCount = 0 }

    private fun take(node: Int): Int {
        if (node >= 0 && takenCount < taken.size) taken[takenCount++] = node
        return node
    }

    // ── Géométrie ─────────────────────────────────────────────────────────────

    private enum class Sight {
        /** Un poste de tir : il doit voir la position annoncée. */
        REQUIRED,
        /** Un point de regroupement : il doit en être caché. */
        AVOID,
        ANY,
    }

    /**
     * Cherche une case praticable à peu près à [radius] blocs de (cx, cz) dans la direction
     * [angleDeg], en élargissant l'angle puis le rayon si le poste idéal n'existe pas. Ne renvoie
     * -1 que si le coin est complètement impraticable.
     */
    private fun postNear(cx: Double, cy: Double, cz: Double, angleDeg: Float, radius: Double,
                         sight: Sight, used: IntArray): Int {
        var fallback = -1
        for (scale in RADIUS_SCALES) {
            for (offset in ANGLE_OFFSETS_DEG) {
                val a = Math.toRadians((angleDeg + offset).toDouble())
                val x = cx + sin(a) * radius * scale
                val z = cz + cos(a) * radius * scale
                for (dy in LEVEL_OFFSETS) {
                    val n = grid.nodeAt(floor(x).toInt(), floor(cy).toInt() + dy, floor(z).toInt())
                    if (n < 0 || used.contains(n)) continue
                    if (sight == Sight.ANY) return n
                    val clear = LineOfSight.isClear(
                        grid.nodeX[n] + .5, grid.nodeY[n] + EYE_HEIGHT, grid.nodeZ[n] + .5,
                        cx, cy + EYE_HEIGHT, cz, world)
                    if (clear == (sight == Sight.REQUIRED)) return n
                    if (fallback < 0) fallback = n
                }
            }
        }
        return fallback
    }

    private companion object {
        const val EYE_HEIGHT = 1.62
        /** Six décisions par seconde : au-delà, on repaie des balayages pour un résultat identique. */
        const val TICK_SECONDS = 1f / 6f

        /** Rôle du rang n dans l'escouade, de la pointe au sixième homme. */
        val ROLES = arrayOf(Squad.Role.POINT, Squad.Role.ANCHOR, Squad.Role.FLANK,
            Squad.Role.FLANK, Squad.Role.SUPPORT, Squad.Role.FLANK)
        /** Azimut du poste, relatif à l'axe d'arrivée de l'escouade. */
        val POST_DEG = floatArrayOf(0f, 40f, -112f, 115f, -45f, 170f)
        /** Distance du poste, en proportion de [SquadTuning.engageRadius]. */
        val POST_RADIUS = floatArrayOf(.65f, 1.4f, .95f, .95f, 1.15f, 1f)
        /** Point de passage du contournement : large, derrière, et hors de vue. 0 = pas de détour. */
        val DETOUR_DEG = floatArrayOf(0f, 0f, -162f, 160f, 0f, 178f)
        val DETOUR_RADIUS = floatArrayOf(0f, 0f, 1.75f, 1.75f, 0f, 1.6f)
        /** Distance à laquelle un point de passage est considéré atteint. */
        const val DETOUR_REACHED = 3.5
        /** Délai minimal entre deux plans déclenchés par un contact visuel. */
        const val CONTACT_REPLAN_MIN = 2f
        /** L'éclaireur pousse plus près que les autres : il va chercher le contact. */
        const val SCOUT_RADIUS = .8
        // Chaque sondage qui tombe sur une case coûte une ligne de vue, soit une trentaine de
        // pas de voxels. Le produit de ces trois tables est le budget d'une recherche de poste :
        // il était de 315 sondages, il est de 45. En intérieur, où la vue est presque toujours
        // coupée, on payait ce maximum à chaque fois — six fois par escouade, à chaque plan.
        val RADIUS_SCALES = doubleArrayOf(1.0, .75, 1.3)
        val ANGLE_OFFSETS_DEG = floatArrayOf(0f, 18f, -18f, 42f, -42f)
        /** Un poste peut être un étage plus haut ou plus bas : une mezzanine reste un bon angle. */
        val LEVEL_OFFSETS = intArrayOf(0, -1, 1)

        const val STAGING_SPACING = 2.5
        const val STAGING_SPREAD_DEG = 72f
        /** Un bloc de dénivelé compte comme ça de trajet : il faut d'abord trouver l'escalier. */
        const val FLOOR_DETOUR = 4.0
        const val TRAVEL_SCALE = 20f
        const val BEARING_SCALE = 60f

        fun distSq(ax: Double, az: Double, bx: Double, bz: Double): Double {
            val dx = ax - bx; val dz = az - bz
            return dx * dx + dz * dz
        }

        fun bearingTo(fromX: Double, fromZ: Double, toX: Double, toZ: Double): Float =
            Math.toDegrees(atan2(toX - fromX, toZ - fromZ)).toFloat()

        fun normalizeDeg(a: Float): Float {
            var v = a % 360f
            if (v > 180f) v -= 360f
            if (v < -180f) v += 360f
            return v
        }
    }
}
