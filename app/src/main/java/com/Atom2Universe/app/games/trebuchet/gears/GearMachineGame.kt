package com.Atom2Universe.app.games.trebuchet.gears

import com.Atom2Universe.app.games.physics.GearJoint
import com.Atom2Universe.app.games.physics.Joint
import com.Atom2Universe.app.games.physics.OneWayRotaryJoint
import com.Atom2Universe.app.games.physics.FlywheelSpec
import com.Atom2Universe.app.games.physics.MachineMaterials
import com.Atom2Universe.app.games.physics.MachinePartFactory
import com.Atom2Universe.app.games.physics.GearSpec
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.PhysicsConstants
import com.Atom2Universe.app.games.physics.RevoluteJoint
import com.Atom2Universe.app.games.physics.RotaryDriveJoint
import com.Atom2Universe.app.games.trebuchet.TargetField
import com.Atom2Universe.app.games.trebuchet.TargetGenerator
import com.Atom2Universe.app.games.trebuchet.TargetLevel
import com.Atom2Universe.app.games.trebuchet.Terrain
import com.Atom2Universe.app.games.trebuchet.TrebuchetCategory
import com.Atom2Universe.app.games.trebuchet.TargetRules
import com.Atom2Universe.app.games.trebuchet.TrebuchetEffects
import com.Atom2Universe.app.games.trebuchet.TrebuchetRules
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/** Atelier physique autonome de la première machine à engrenages. */
class GearMachineGame(initial: GearMachineConfig = GearMachineConfig()) {
    data class GearState(
        val wheel: GearWheelConfig,
        val body: PhysBody,
        val support: PhysBody,
        val axle: RevoluteJoint,
        /** Le moteur attelé à cette roue, s'il y en a un. */
        val drive: RotaryDriveJoint? = null,
        /**
         * Le frottement sec du palier, en N·m. Il est mis de côté parce que le frein
         * emprunte le **même** moteur d'axe : freiner, c'est en relever le couple, et
         * relâcher, c'est lui rendre cette valeur-là.
         */
        val bearingTorque: Float = 0f,
        /**
         * Le réservoir d'un canon, en volume standard déjà pompé — voir
         * [GearMachineGame.gasPressure]. Sans objet pour une roue qui n'est pas un
         * canon en train de tirer : elle reste alors à zéro, inerte.
         */
        var reservoirStdVolume: Float = 0f
    )

    data class Mesh(val firstId: Int, val secondId: Int, val joint: GearJoint)
    data class Transmission(
        val config: GearLinkConfig,
        val joint: Joint,
        /** Prise progressive de l'embrayage coaxial, de 0 à 1. */
        var clutchGrip: Float = 0f
    )

    data class GearResizeResult(val teeth: Int, val conflicts: Set<Int> = emptySet()) {
        val success: Boolean get() = conflicts.isEmpty()
    }

    private data class MeshConstraint(
        val groupA: Int,
        val groupB: Int,
        val wheelA: Int,
        val wheelB: Int,
        val ux: Float,
        val uy: Float
    )

    /**
     * Un tir, du départ jusqu'à sa trace au sol.
     *
     * Le boulet **reste** une fois posé : c'est là tout le résultat du coup, et le
     * faire disparaître trois secondes plus tard reviendrait à effacer la seule chose
     * que le joueur voulait voir. Il n'est retiré qu'au tir suivant.
     */
    data class ProjectileState(
        val body: PhysBody,
        val startX: Float,
        val startY: Float,
        val launchEnergy: Float,
        /** Où était le mannequin au moment du départ : la cible ne bouge pas en vol. */
        val targetX: Float,
        var peakY: Float = startY,
        /** Vrai dès le premier contact avec le sol. */
        var landed: Boolean = false,
        /** Portée relevée au **premier** contact, pas là où le boulet finit de rouler. */
        var distance: Float = 0f,
        var hitTarget: Boolean = false,
        var previousX: Float = startX,
        var previousY: Float = startY,
        /** Depuis combien de temps le boulet ne bouge plus. Voir le quatrième relevé. */
        var stalled: Float = 0f
    )

    val world = PhysWorld().apply {
        // Une magnitude, dirigée vers le bas : le monde intègre `vy -= gravity·dt`.
        // Elle valait ici -9,8 depuis l'origine, donc le boulet **montait** — invisible
        // tant qu'il n'y avait pas de sol pour le rattraper.
        gravity = PhysicsConstants.STANDARD_GRAVITY
        linearDamping = 0.015f
        // Une faible traînée de l'air sur les roues — et **seulement** ça : les pertes
        // qui comptent doivent venir des paliers, parce que ce sont les seules que le
        // joueur peut travailler en changeant de matière.
        //
        // Elle valait 0,002, ce qui était sans conséquence tant qu'un doigt pouvait
        // injecter quatre-vingts mégajoules d'un geste. Avec des moteurs réels elle
        // devenait la perte dominante : sur un train de manège monté au soixante-
        // quatrième, elle mangeait trois kilowatts quand les paliers en prenaient un
        // demi, et le volant plafonnait à trente-cinq fois l'allure de la bête au lieu
        // des soixante-quatre que le train promettait. À 0,0005 ce sont les paliers qui
        // décident, et le plafond redevient celui qu'annonce le rapport de la cascade.
        angularDamping = 0.0005f
        sleepEnabled = false
        iterations = 18
    }

    var config = initial.deepCopy()
        private set
    val gears = ArrayList<GearState>()
    val meshes = ArrayList<Mesh>()
    val transmissions = ArrayList<Transmission>()
    var projectile: ProjectileState? = null
        private set
    var lastLaunchSpeed = 0f
        private set
    var lastLaunchEnergy = 0f
        private set


    /**
     * Le résultat du dernier tir **posé**. Il survit au boulet : c'est le score de
     * l'atelier, et il doit rester lisible pendant qu'on retouche la machine.
     */
    var lastShotDistance = 0f
        private set
    var lastShotHeight = 0f
        private set
    var lastShotHitTarget = false
        private set

    /** Le sol de l'atelier : une seule dalle plate, refaite à chaque reconstruction. */
    lateinit var ground: PhysBody
        private set

    /**
     * Le relief et le site, **les memes qu'au champ de tir**.
     *
     * L'atelier n'est plus une dalle nue : on y tire sur des collines et des batiments
     * qui s'effondrent, parce qu'il n'y avait aucune raison qu'une machine a engrenages
     * mesure ses coups sur un terrain moins vrai qu'un trebuchet. Le tout sort d'une
     * graine, exactement comme un niveau du trebuchet.
     *
     * Le relief est **plat sous la machine** : c'est [TargetGenerator] qui le garantit,
     * il batit le terrain autour des batiments et laisse la ligne de tir de niveau. Une
     * colline sous l'atelier enterrerait les roues.
     */
    var terrain: Terrain = Terrain.FLAT
        private set

    var level: TargetLevel? = null
        private set

    val targets = TargetField(world).apply {
        // La poussiere d'une pierre qui cede : c'est le seul retour visible d'un coup
        // qui a porte sans casser, et il ne coute rien puisque les effets tournent deja.
        onDust = { x, y, r -> effects.dust(x, y, r) }
    }

    /**
     * Fumee, gravats, poussiere et feu d'artifice.
     *
     * Les memes que le trebuchet, pour la meme raison que le decor : une explosion ne
     * ressemble pas plus a un train d'engrenages qu'a un contrepoids. La vue les peint
     * avec [com.Atom2Universe.app.games.trebuchet.SparkScene].
     */
    val effects = TrebuchetEffects()

    /** Le feu d'artifice ne part qu'une fois par site. */
    private var celebrated = false

    /**
     * Hauteur de ciel visible a l'ecran, en metres. La vue la pose a chaque image.
     *
     * **Personne ne la posait**, et le feu d'artifice de l'atelier tirait donc bas :
     * les fusees visent une *part* de ciel — du tiers aux quatre cinquiemes de l'image
     * — et cette part se calcule sur cette hauteur-la. Faute d'etre renseignee, elle
     * restait a sa valeur par defaut de cent cinquante metres, si bien que les bouquets
     * eclataient a quatre-vingts metres dans une image qui en montrait trois fois plus,
     * tasses au ras du sol pendant que le ciel restait noir. Le trebuchet, lui, la pose
     * depuis toujours : voir [TrebuchetGame.skyTop].
     *
     * C'est la seule chose que la simulation sait de l'affichage.
     */
    var skyTop = 150f
        set(value) {
            field = value
            effects.skyTop = value
        }

    // ── La traversée ─────────────────────────────────────────────────────────
    //
    // L'élan du boulet avant le choc de l'image en cours. Une fois le pas simulé il est
    // perdu, et c'est justement lui qu'on veut rendre à un projectile qui vient de
    // casser quelque chose.
    private var momentumVx = 0f
    private var momentumVy = 0f
    private var momentumEnergy = 0f

    private fun rememberMomentum() {
        val b = projectile?.body ?: return
        momentumVx = b.vx
        momentumVy = b.vy
        momentumEnergy = 0.5f * b.mass * (b.vx * b.vx + b.vy * b.vy)
    }

    /**
     * **Un projectile ne paie que ce qu'il a détruit.**
     *
     * C'est la moitié manquante du mode arcade dans l'atelier. Sans elle, la physique
     * décide seule et elle est impitoyable : un boulet de vingt kilos qui percute une
     * pierre de trois tonnes repart en arrière, **même si la pierre se brise**. C'est
     * exact — l'impulsion de contact se calcule avant que la pierre ne meure, et elle ne
     * sait pas que sa cible n'existera plus dans un dixième de seconde. C'est aussi tout
     * ce qu'on ne veut pas voir en arcade, où un boulet doit entrer dans la construction
     * et ressortir de l'autre côté.
     *
     * On le remet donc dans l'axe qu'il avait avant le choc, avec l'énergie qu'il avait
     * **moins celle des points de vie qu'il vient d'emporter**. Trois garde-fous font
     * que ce n'est pas de la triche :
     *
     *  - il ne récupère rien s'il n'a **rien cassé** : cogner sans casser rebondit, dans
     *    les deux modes ;
     *  - il ne dépasse jamais l'énergie qu'il avait au début de l'image, donc le moteur
     *    ne crée pas d'énergie — la règle d'or de cette physique ;
     *  - on ne le relance que si la physique l'a laissé **plus lent** que ça.
     *
     * Et le curseur [com.Atom2Universe.app.games.trebuchet.TargetStyle.pierce] vaut zéro
     * en réaliste, où le rebond honnête est précisément ce qu'on est venu voir.
     */
    private fun pierceThrough() {
        val refund = TargetRules.style.pierce
        if (refund <= 0f) return
        val b = projectile?.body ?: return
        val cost = targets.pierceCost(b)
        if (cost <= 0f) return
        // Plein s'il a gagné son passage — pierre déjà fêlée, ou coup critique, voir
        // [com.Atom2Universe.app.games.trebuchet.TargetField.piercedThrough] — et un
        // reliquat sinon.
        val part = refund *
            if (targets.piercedThrough(b)) 1f else TargetRules.PIERCE_UNEARNED
        val v0 = hypot(momentumVx, momentumVy)
        if (v0 < 1f) return
        val left = (momentumEnergy - cost).coerceAtLeast(0f)
        val voulu = sqrt(2f * left / b.mass)
        val maintenant = hypot(b.vx, b.vy)
        if (voulu <= maintenant) return
        val v = maintenant + (voulu - maintenant) * part
        b.wake()
        b.vx = momentumVx / v0 * v
        b.vy = momentumVy / v0 * v
        // Les contacts gardent leurs impulsions d'une image à l'autre : sans les
        // oublier, le solveur retiendrait le boulet contre une pierre qui n'est déjà
        // plus là.
        world.forgetContacts(b)
    }

    /**
     * Tire le feu d'artifice de la victoire, une fois par site.
     *
     * Il part **de la machine jusqu'au bout des decombres**, et non plus seulement
     * jusqu'au pied de la construction — meme regle qu'au trebuchet, et pour la meme
     * raison : la camera prend tout le champ a la fin d'un tir, et un bouquet qui
     * s'arrete avant les ruines laisse noire la moitie de l'image ou le joueur regarde
     * ce qu'il vient d'abattre.
     *
     * Le bout des decombres se lit sur les pierres et non sur l'empreinte d'origine :
     * une construction qui s'effondre projette ses blocs plus loin qu'elle ne
     * s'etendait.
     */
    private fun celebrate() {
        if (celebrated || level == null || !targets.cleared) return
        celebrated = true
        val debut = bounds()[1] + 30f
        var bout = targets.right
        for (p in targets.pieces) if (p.body.x > bout) bout = p.body.x
        val fin = (bout + 25f).coerceAtLeast(debut + 40f)
        effects.celebrate(debut, fin)
    }

    /**
     * Charge le site de la graine donnee, ou le retire si [seed] est nul.
     *
     * Le remontage passe par [rebuild] parce que le relief decide des corps immobiles du
     * monde : changer de terrain sans refaire le sol laisserait la machine posee sur
     * l'ancien.
     */
    fun loadSite(seed: Long?) {
        if (seed == null) {
            level = null
            terrain = Terrain.FLAT
            targets.clear()
            rebuild()
            return
        }
        // Le site se pose **exactement comme au trebuchet** : la ou le generateur l'a
        // mis, avec son relief. C'est deja lui qui garantit un tablier plat a l'altitude
        // zero sous la ligne de tir, sinon la machine s'enterrerait.
        //
        // On a essaye de le rapprocher, sur l'idee qu'un atelier tire moins loin qu'un
        // trebuchet. C'est faux : la machine par defaut envoie deja son boulet a pres de
        // trois cents metres. Rapprocher le village a la distance du mannequin — soixante
        // metres — mettait un chateau en travers de chaque tir des la premiere image.
        // La distance de visee resservira pour placer un site **choisi**, pas celui-ci.
        val lvl = TargetGenerator.generate(seed)
        level = lvl
        terrain = lvl.terrain
        // Les gravats retombent sur le relief, pas sur l'altitude zero.
        effects.groundAt = { x -> lvl.terrain.heightAt(x) }
        effects.clear()
        celebrated = false
        rebuild()
        targets.load(lvl.structure, lvl.terrain)
        stampSite()
        // **Pas de `reattach()` ici.** [TargetField.load] pose deja chaque pierre dans le
        // monde en la fabriquant ; `reattach` ne sert qu'a les y remettre apres un
        // `world.clear()`, ce que fait [rebuild]. Les deux enchaines mettaient **chaque
        // pierre deux fois** dans la liste des corps — et rien ne s'en plaignait : le
        // balayage large appariait alors un corps avec lui-meme, le solveur inventait des
        // contacts entre ses propres morceaux, et une construction touchee se figeait ou
        // partait de travers une fois sur deux.
    }


    /**
     * Met les pierres du site sur **toutes** les couches de l'atelier.
     *
     * [TargetField] ne connait pas les etages : il laisse ses corps sur la couche zero.
     * Un boulet parti d'un lanceur monte au troisieme etage les traverserait donc sans
     * les voir. On leur donne la meme envergure qu'au sol, qui traverse deja tout.
     */
    private fun stampSite() {
        for (p in targets.pieces) {
            p.body.collisionLayer = GearMachineRules.MIN_LAYER
            p.body.collisionLayerDepth =
                GearMachineRules.MAX_LAYER - GearMachineRules.MIN_LAYER + 1
        }
    }

    /**
     * Les trois temps d'un tir, comme au trébuchet.
     *
     * L'atelier n'en avait aucun : on tirait, et c'était tout — le boulet roulait
     * jusqu'à la fin des temps sans que rien ne dise que le coup était terminé. Un tir
     * qui ne finit pas ne se lit pas, ne laisse pas de fantôme, et ne rend jamais la
     * caméra à la machine.
     */
    enum class Phase { BUILD, FLIGHT, RESULT }

    var phase = Phase.BUILD
        private set
    var shotCount = 0
        private set

    /** Trajectoire du tir en cours, en couples (x, y), à lire jusqu'à [trailCount]. */
    private var trailBuf = FloatArray(1_024)
    val trail: FloatArray get() = trailBuf
    var trailCount = 0
        private set

    private val ghostList = ArrayList<FloatArray>()

    /** Les tirs précédents, le plus récent en tête. */
    val ghosts: List<FloatArray> get() = ghostList

    /**
     * Change à chaque fois que la liste des fantômes change, et jamais autrement.
     *
     * La vue peint les fantômes dans un calque qu'elle garde d'une image à l'autre ;
     * il lui faut donc savoir si la liste a bougé. Comparer les tableaux eux-mêmes
     * voudrait dire relire dix fois trois mille nombres à chaque image, soit exactement
     * le travail qu'on essaie d'éviter. Un compteur suffit.
     */
    var ghostStamp = 0
        private set

    /** Combien de fantômes on garde. Le réglage est celui du trébuchet. */
    var ghostLimit: Int = TrebuchetRules.GHOST_HISTORY
        set(value) {
            field = value.coerceIn(1, TrebuchetRules.GHOST_CHOICES.last())
            while (ghostList.size > field) ghostList.removeAt(ghostList.size - 1)
            ghostStamp++
        }

    private var trailTimer = 0f
    private var restCalm = 0f
    private var sinceLanding = 0f

    /** La roue dont on serre le frein, s'il y en a une. */
    private var brakingId: Int? = null

    /** Temps machine encore à jouer en accéléré, et durée totale demandée. */
    var chargeRemaining = 0f
        private set
    var chargeTotal = 0f
        private set

    private data class SavedMotion(
        val angle: Float,
        val omega: Float,
        val energy: Float,
        val reservoirStdVolume: Float = 0f
    )

    init {
        rebuild(preserveMotion = false)
        // Le site **apres** la machine : il a besoin d'un monde deja monte, et
        // [loadSite] le remonte de toute facon pour poser le relief.
        loadSite(DEFAULT_SITE_SEED)
    }

    fun loadConfig(value: GearMachineConfig) {
        // Ouvrir une machine est le seul moment ou l'on sait tenir un tout : c'est donc
        // la qu'on lui rend les pieces qui lui manqueraient. Une sauvegarde d'avant les
        // moteurs se reouvre ainsi avec son moulin et son pas de tir.
        config = value.deepCopy().also { it.ensureCorePieces() }
        rebuild(preserveMotion = false)
    }

    fun rebuild(
        preserveMotion: Boolean = true,
        useConfiguredAngleFor: Set<Int> = emptySet()
    ) {
        val savedMotion = if (preserveMotion) gears.associate { state ->
            state.wheel.id to SavedMotion(
                state.body.angle,
                state.body.omega,
                0.5f * state.body.inertia * state.body.omega * state.body.omega,
                state.reservoirStdVolume
            )
        } else emptyMap()
        // Un boulet posé n'est plus un corps physique : il ne revient dans le monde
        // que s'il est encore en vol.
        val savedProjectile = if (preserveMotion) projectile else null
        val savedFlying = savedProjectile != null && phase == Phase.FLIGHT
        world.clear()
        gears.clear()
        meshes.clear()
        transmissions.clear()
        projectile = null
        addGround()
        config.clamp()
        alignShaftCenters()
        for (wheel in config.wheels) {
            val spec = GearSpec(
                pitchRadius = wheel.pitchRadius,
                teeth = wheel.teeth,
                faceWidth = 0.10f,
                boreRadius = minOf(0.18f, wheel.pitchRadius * 0.25f),
                material = MachineMaterials.STEEL
            )
            // Volant **comme** engrenage sont des jantes montées sur des rayons, et se
            // fabriquent donc tous deux avec la même pièce : seules l'épaisseur et la
            // largeur de la jante les séparent. `MachinePartFactory.gear` taille un
            // disque plein — cinquante-six tonnes pour trois mètres de rayon — que
            // plus aucun moteur de l'atelier ne saurait faire tourner.
            val body = MachinePartFactory.flywheel(
                FlywheelSpec(
                    outerRadius = wheel.outerRadius,
                    innerRadius = GearMachineRules.rimInnerRadius(wheel.kind, wheel.outerRadius),
                    width = GearMachineRules.rimWidth(wheel.kind),
                    material = wheel.material.physics
                )
            ).apply {
                x = wheel.x
                y = wheel.y
                angle = if (wheel.id in useConfiguredAngleFor) {
                    wheel.angle
                } else {
                    savedMotion[wheel.id]?.angle ?: wheel.angle
                }
                category = GearMachineRules.CATEGORY_WHEEL
                collidesWith = 0 // les dents sont logiques ; aucune collision parasite.
                collisionLayer = wheel.layer
            }
            val support = PhysBody.circle(maxOf(0.08f, spec.boreRadius), 0f).apply {
                x = wheel.x
                y = wheel.y
                lockPosition = true
                lockRotation = true
                collidesWith = 0
                collisionLayer = wheel.layer
                refreshMass()
            }
            world.add(support)
            world.add(body)
            savedMotion[wheel.id]?.let { motion ->
                if (wheel.id !in useConfiguredAngleFor) wheel.angle = motion.angle
                if (motion.energy > 0f && body.inertia > 1e-12f) {
                    val speed = sqrt(2f * motion.energy / body.inertia)
                    body.omega = if (motion.omega < 0f) -speed else speed
                }
                body.wake()
            }
            val bearingTorque = wheel.material.axleFriction * body.mass *
                PhysicsConstants.STANDARD_GRAVITY * maxOf(0.04f, spec.boreRadius)
            val axle = RevoluteJoint.pin(support, body, wheel.x, wheel.y).apply {
                // Frottement sec du palier : il dissipe jusqu'à l'arrêt mais ne peut
                // jamais accélérer la roue ni inverser son sens.
                motorEnabled = true
                motorSpeed = 0f
                maxMotorTorque = bearingTorque
            }
            world.addJoint(axle)
            val drive = wheel.motor
                ?.takeIf { it.kind != GearMotorKind.NONE }
                ?.let { motor ->
                    RotaryDriveJoint(support, body).apply {
                        val sign = if (motor.direction < 0) -1f else 1f
                        targetOmega = sign * GearMotorRules.freeOmega(motor)
                        maxTorque = GearMotorRules.maxTorque(motor)
                    }
                }
            drive?.let { world.addJoint(it) }
            gears += GearState(
                wheel, body, support, axle, drive, bearingTorque,
                reservoirStdVolume = savedMotion[wheel.id]?.reservoirStdVolume ?: 0f
            )
        }
        connectTransmissions()
        connectMeshes()
        savedProjectile?.let { shot ->
            if (shot.body.x.isFinite() && shot.body.y.isFinite()) {
                if (savedFlying) world.add(shot.body)
                projectile = shot
            }
        }

        // **Le site rentre dans le monde ici, et nulle part ailleurs.**
        //
        // [TargetField.load] fabrique les pierres mais ne les pose pas : c'est
        // [TargetField.reattach] qui le fait, et il faut le rappeler apres **chaque**
        // `world.clear()`. C'est exactement ce qui manquait — les boulets traversaient
        // le village sans le voir, filtres de collision parfaits et pierres absentes du
        // monde. Le trebuchet fait la meme chose au meme endroit, dans son `build()`.
        //
        // La cible garde ses corps d'un remontage a l'autre : le joueur qui deplace un
        // engrenage entre deux tirs ne doit pas voir le village se reconstruire derriere
        // lui.
        targets.reattach()
        stampSite()
        // Le boulet qui vient de la toucher n'a pas survecu au vidage : c'est le moment
        // de rendormir la cible si elle est prete, plutot que d'attendre la prochaine
        // image de vol pour s'en apercevoir.
        targets.trySleep()
    }

    /**
     * Pose la dalle de sol, **sous** le relief et jamais au travers.
     *
     * Elle traverse toutes les couches — un boulet tiré depuis l'étage +3 retombe sur la
     * même terre que les autres — et elle ferme le monde de part et d'autre du relief.
     *
     * **Sa face supérieure suit le point le plus bas du relief, elle n'est pas à zéro.**
     * Elle l'a été, et c'était le défaut le plus déroutant du jeu : une dalle de quarante
     * kilomètres calée à l'altitude zéro passe **au-dessus** d'un site posé au fond d'un
     * vallon, lequel descend jusqu'à huit mètres sous le niveau de la machine. C'était
     * donc la dalle qui portait le village, pas le relief. Le décor, lui, se dessine sur
     * le vrai profil : le joueur voyait ses bâtiments et leurs gravats **suspendus à huit
     * mètres au-dessus du sol**.
     *
     * Et le décalage ne se voyait qu'au premier impact : la cible se charge endormie
     * ([TargetField.trySleep]), donc rien ne bougeait tant que rien ne la touchait. Le
     * boulet arrivait, tout se réveillait, et le site remontait d'un bloc — « l'immeuble
     * a grandi pendant le tir ».
     *
     * Sur un relief plat ou entièrement au-dessus de zéro, le minimum vaut zéro et la
     * dalle ne bouge pas d'un millimètre : c'est la même dalle qu'avant.
     */
    private fun addGround() {
        val sommet = minOf(0f, terrain.lowest)
        ground = PhysBody(
            GearMachineRules.GROUND_HALF_WIDTH, GearMachineRules.GROUND_DEPTH / 2f, 0f
        ).apply {
            x = 0f
            y = sommet - GearMachineRules.GROUND_DEPTH / 2f
            lockPosition = true
            lockRotation = true
            friction = 0.62f
            restitution = 0f
            refreshMass()
        }
        stampGround(ground)
        world.add(ground)

        // Le relief par-dessus la dalle, une fois qu'il y en a un. La dalle reste : elle
        // porte la machine et ferme le monde de part et d'autre du terrain, qui ne
        // s'etend que sur la longueur du site.
        if (!terrain.flat) {
            for (b in terrain.bodies()) {
                stampGround(b)
                world.add(b)
            }
        }
    }

    /** Ce qu'un morceau de sol rencontre : le boulet, les pierres, et leurs gravats. */
    private fun stampGround(b: PhysBody) {
        b.category = GearMachineRules.CATEGORY_GROUND
        b.collidesWith =
            TrebuchetCategory.BALL or TrebuchetCategory.TARGET or TrebuchetCategory.DEBRIS
        b.collisionLayer = GearMachineRules.MIN_LAYER
        b.collisionLayerDepth = GearMachineRules.MAX_LAYER - GearMachineRules.MIN_LAYER + 1
    }

    /** Où se dresse le mannequin : toujours à la même distance devant le lanceur. */
    fun targetX(): Float {
        val launcher = config.launcherWheelId?.let { id ->
            gears.firstOrNull { it.wheel.id == id }?.body?.x
        }
        return (launcher ?: bounds()[1]) + GearMachineRules.TARGET_DISTANCE
    }

    private fun connectTransmissions() {
        for (link in config.links) {
            val a = gears.firstOrNull { it.wheel.id == link.firstId } ?: continue
            val b = gears.firstOrNull { it.wheel.id == link.secondId } ?: continue
            val joint: Joint = when (link.kind) {
                GearLinkKind.BELT_OPEN -> GearJoint.belt(
                    a.body, a.wheel.pitchRadius, b.body, b.wheel.pitchRadius, crossed = false
                ).apply { maxTorqueOnB = 18_000f * b.wheel.pitchRadius }
                GearLinkKind.BELT_CROSSED -> GearJoint.belt(
                    a.body, a.wheel.pitchRadius, b.body, b.wheel.pitchRadius, crossed = true
                ).apply { maxTorqueOnB = 14_000f * b.wheel.pitchRadius }
                GearLinkKind.CHAIN_FREEWHEEL -> OneWayRotaryJoint(
                    a.body, b.body,
                    ratio = a.wheel.pitchRadius / b.wheel.pitchRadius,
                    inputDirection = link.inputDirection
                ).apply { maxTorqueOnB = 30_000f * b.wheel.pitchRadius }
                GearLinkKind.SHAFT_CLUTCH -> GearJoint(a.body, b.body, -1f).apply {
                    // Un même arbre traverse volontairement plusieurs couches.
                    requiresLayerOverlap = false
                    maxTorqueOnB = 0f
                }
            }
            world.addJoint(joint)
            transmissions += Transmission(link, joint)
        }
    }

    private fun connectMeshes() {
        for (i in 0 until gears.size) for (j in i + 1 until gears.size) {
            val a = gears[i]
            val b = gears[j]
            if (config.links.any {
                    (it.firstId == a.wheel.id && it.secondId == b.wheel.id) ||
                        (it.firstId == b.wheel.id && it.secondId == a.wheel.id)
                }) continue
            if (a.wheel.layer != b.wheel.layer) continue
            val expected = a.wheel.pitchRadius + b.wheel.pitchRadius
            val actual = hypot(b.wheel.x - a.wheel.x, b.wheel.y - a.wheel.y)
            if (abs(actual - expected) > GearMachineRules.MODULE * 0.38f) continue
            val joint = GearJoint.external(a.body, a.wheel.teeth, b.body, b.wheel.teeth)
            // Limite de denture simplifiée (Lewis) : face × module × résistance,
            // volontairement prudente pour que les matériaux aient un vrai effet.
            val weaker = minOf(a.wheel.material.physics.yieldStrength, b.wheel.material.physics.yieldStrength)
            // Le facteur de jeu garde l'acier rigide sous un entraînement manuel,
            // tout en laissant le bois ou un rapport extrême atteindre le patinage.
            val toothForce = weaker * GearMachineRules.MODULE * 0.10f * 0.060f
            joint.maxTorqueOnB = toothForce * b.wheel.pitchRadius
            world.addJoint(joint)
            meshes += Mesh(a.wheel.id, b.wheel.id, joint)
        }
    }

    fun step(dt: Float) {
        val h = dt.coerceIn(0f, 1f / 30f)
        updateProgressiveClutches(h)
        applyBrake()
        // L'élan d'avant le choc, gardé pour la traversée.
        if (phase == Phase.FLIGHT) rememberMomentum()
        world.stepFrame(h)
        // **Après**, jamais avant : c'est la vitesse que le solveur vient d'accorder à
        // la manivelle une fois le couple résistant du réservoir pris en compte, pas
        // celle d'avant qui l'ignorait encore.
        updatePumpReservoir(h)
        effects.update(h)
        celebrate()
        // Le site s'arme, encaisse et se rendort tout seul — c'est [TargetField.update]
        // qui le fait, et sans cet appel un chateau de quatre-vingts pierres serait
        // resolu a chaque sous-pas d'un boulet a trois cents metres de la.
        //
        // Il vient **avant** la traversée : c'est lui qui transforme les chocs en
        // dégâts, et la traversée a besoin de savoir ce que le boulet vient de casser.
        targets.update(h)
        if (phase == Phase.FLIGHT) {
            pierceThrough()
            projectile?.let { trackShot(it, h) }
        }
    }

    /**
     * Le frein d'axe de la roue tenue, et la résistance du réservoir sur la manivelle
     * d'un canon, s'il y en a une.
     *
     * Les deux se posent au même endroit — le couple maximal du même moteur d'axe qui
     * porte déjà le frottement sec — parce que ce sont deux résistances de même
     * nature : elles ne font jamais qu'opposer un couple, jamais avancer. Freiner,
     * c'est en relever le couple pour ramener la vitesse relative à zéro ; la
     * résistance du réservoir fait le même geste sans jamais viser zéro, elle
     * s'ajoute simplement au frottement sec tant qu'on ne serre pas le frein
     * par-dessus.
     */
    private fun applyBrake() {
        for (gear in gears) {
            val braking = gear.wheel.id == brakingId
            gear.axle.maxMotorTorque = when {
                braking -> gear.bearingTorque + gear.body.inertia * GearMachineRules.BRAKE_RATE
                gear.wheel.kind == GearWheelKind.PUMP && gear.wheel.id == config.launcherWheelId ->
                    gear.bearingTorque + pumpLoadTorque(gear)
                else -> gear.bearingTorque
            }
        }
    }

    /**
     * La pression qu'un volume standard [stdVolume] déjà pompé donne dans le
     * réservoir, en pascals.
     *
     * Loi des gaz parfaits à température constante : le réservoir a un volume fixe
     * (réglable, [GearWheelConfig.reservoirVolume]), et y ajouter de l'air pris à
     * pression atmosphérique fait monter la pression **linéairement** avec le volume
     * standard injecté. Elle ne dépend jamais de la vitesse de la manivelle — on
     * peut donc la lire à l'arrêt, entre deux charges.
     */
    private fun gasPressure(stdVolume: Float, reservoirVolume: Float): Float =
        GearMachineRules.ATMOSPHERIC_PRESSURE * (1f + stdVolume / reservoirVolume)

    /**
     * Ce que le réservoir résiste sur l'axe de la manivelle d'un canon, en N·m.
     *
     * **C'est tout le mécanisme du plafond, et il n'est écrit nulle part ailleurs.**
     * Ce n'est **pas** la pression du réservoir prise telle quelle : le piston ne
     * pousse pas tout le volume balayé contre la pleine pression finale, il
     * **comprime** chaque bouffée d'air prise à pression atmosphérique jusqu'à la
     * rejoindre, et ce travail de compression isotherme est logarithmique —
     * `P₀ · ln(pression / P₀)` — pas linéaire. Une charge trop tôt (`ln(1) = 0`)
     * ne coûte donc rien, comme il se doit : on n'a encore rien comprimé.
     *
     * Passé le couple que le train peut vraiment fournir, cette résistance l'emporte
     * et la manivelle cale : c'est ça qui plafonne la pression, le même équilibre de
     * forces que celui qui plafonne déjà la vitesse d'un volant via
     * [GearMotorRules.freeOmega]/[GearMotorRules.maxTorque].
     */
    private fun pumpLoadTorque(gear: GearState): Float {
        val pressure = gasPressure(gear.reservoirStdVolume, gear.wheel.reservoirVolume)
        val ratio = pressure / GearMachineRules.ATMOSPHERIC_PRESSURE
        if (ratio <= 1f) return 0f
        val compressionWork = GearMachineRules.ATMOSPHERIC_PRESSURE * ln(ratio)
        return compressionWork * GearMachineRules.PISTON_AREA * gear.wheel.pitchRadius
    }

    /**
     * L'énergie déjà emmagasinée dans un réservoir de volume [reservoirVolume] qui a
     * reçu [stdVolume] de volume standard, en joules.
     *
     * Primitive du travail de compression isotherme sur le volume,
     * `∫ P₀·ln(P/P₀) dV`, qui vaut `P₀·V·(r·ln(r) − r + 1)` avec `r = P/P₀`. C'est
     * **exactement** l'énergie que [pumpLoadTorque] retire du train pas à pas
     * (puissance = couple × oméga = travail de compression × débit) — rien n'est
     * compté séparément, donc rien ne peut créer d'énergie que le train n'a pas
     * vraiment fournie.
     *
     * **Vérifié contre une bouteille de plongée** (200 bar dans 12 L) : cette
     * formule y trouve environ 1 MJ, l'ordre de grandeur publié pour une vraie
     * bouteille. La première version, qui comptait `P·dV` au lieu de `P₀·ln(P/P₀)·dV`
     * — comme si tout le volume était comprimé d'un coup à la pression finale —
     * en trouvait 23 MJ, vingt fois trop : un canon à 7 bar suffisait à lancer un
     * boulet de cinquante kilos à quatre cents mètres.
     */
    private fun reservoirEnergy(stdVolume: Float, reservoirVolume: Float): Float {
        val p0 = GearMachineRules.ATMOSPHERIC_PRESSURE
        val ratio = 1f + stdVolume / reservoirVolume
        if (ratio <= 1f) return 0f
        return p0 * reservoirVolume * (ratio * ln(ratio) - ratio + 1f)
    }

    /**
     * Ce que la manivelle du canon a réussi à pousser dans le réservoir ce pas-ci.
     *
     * Le débit d'un piston vaut sa section fois la vitesse à laquelle il file, et
     * cette vitesse est celle de la manivelle, `rayon × oméga`. Appelée depuis
     * [step], **après** `world.stepFrame` et jamais avant : c'est la vitesse que le
     * solveur vient réellement d'accorder à la roue une fois [pumpLoadTorque] pris
     * en compte, pas celle d'avant qui l'ignorait encore.
     */
    private fun updatePumpReservoir(dt: Float) {
        val launcherId = config.launcherWheelId ?: return
        val gear = gears.firstOrNull { it.wheel.id == launcherId } ?: return
        if (gear.wheel.kind != GearWheelKind.PUMP) return
        val flow = GearMachineRules.PISTON_AREA * gear.wheel.pitchRadius * abs(gear.body.omega)
        gear.reservoirStdVolume += flow * dt
    }

    /**
     * La pression courante du réservoir du lanceur, en pascals — zéro si le lanceur
     * n'est pas un canon. Pendant de [rimSpeed] pour l'affichage.
     */
    fun launcherPressure(): Float {
        val wheel = config.launcher()?.takeIf { it.kind == GearWheelKind.PUMP } ?: return 0f
        val gear = gears.firstOrNull { it.wheel.id == wheel.id } ?: return 0f
        return gasPressure(gear.reservoirStdVolume, wheel.reservoirVolume)
    }

    /**
     * Le volume du réservoir d'un canon, en m³ — sans effet sur un volant.
     *
     * Deuxième levier de puissance à côté de la denture de la manivelle : voir
     * [GearWheelConfig.reservoirVolume].
     */
    fun setReservoirVolume(id: Int, cubicMeters: Float): Float? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        if (wheel.kind != GearWheelKind.PUMP) return null
        wheel.reservoirVolume = cubicMeters.coerceIn(
            GearMachineRules.MIN_RESERVOIR_VOLUME, GearMachineRules.MAX_RESERVOIR_VOLUME
        )
        return wheel.reservoirVolume
    }

    /** Serre ou desserre le frein d'une roue. Une seule roue est freinée à la fois. */
    fun setBrake(id: Int?) {
        brakingId = id?.takeIf { wanted -> gears.any { it.wheel.id == wanted } }
    }

    val braking: Boolean get() = brakingId != null

    /**
     * Arrête net la roue et tout ce qui lui est accroché.
     *
     * Il faut prendre **tout le train** : mettre une seule roue à zéro alors qu'elle
     * est engrenée demanderait au solveur de résoudre une contradiction dans le pas
     * suivant, et il la résoudrait par un à-coup.
     */
    fun stopConnected(id: Int) {
        val connected = connectedTo(id)
        for (gear in gears) if (gear.wheel.id in connected) {
            gear.body.omega = 0f
            gear.body.vx = 0f
            gear.body.vy = 0f
            gear.body.wake()
        }
        for (mesh in meshes) mesh.joint.reset()
        for (transmission in transmissions) transmission.joint.reset()
        for (gear in gears) gear.drive?.reset()
    }

    /**
     * Lance une charge : la machine tourne pendant [seconds] secondes de son temps.
     *
     * **Rien n'est inventé.** C'est la même simulation, jouée en accéléré : les mêmes
     * moteurs, les mêmes frottements, les mêmes patinages. Une machine laissée
     * tourner très longtemps finit d'ailleurs sur un plateau, quand le frottement des
     * paliers mange exactement ce que les moteurs fournissent — la durée décide
     * seulement si on s'arrête avant d'y arriver.
     */
    fun startCharge(seconds: Float = config.chargeSeconds): Boolean {
        if (phase == Phase.FLIGHT) return false
        if (gears.none { it.drive != null }) return false
        val total = seconds.takeIf { it.isFinite() }
            ?.coerceIn(GearMachineRules.MIN_CHARGE, GearMachineRules.MAX_CHARGE) ?: return false
        chargeTotal = total
        chargeRemaining = total
        return true
    }

    fun cancelCharge() {
        chargeRemaining = 0f
        chargeTotal = 0f
    }

    /**
     * Avance la charge d'au plus [maxSteps] pas, et rend le temps machine consommé.
     *
     * La charge est découpée en tranches plutôt que jouée d'un bloc : la vue reste
     * vivante, et on **voit** le train monter en régime au lieu d'attendre devant une
     * image figée.
     */
    fun advanceCharge(fixed: Float, maxSteps: Int): Float {
        if (chargeRemaining <= 0f) return 0f
        var consumed = 0f
        var steps = 0
        while (chargeRemaining > 0f && steps < maxSteps) {
            val h = minOf(fixed, chargeRemaining)
            step(h)
            chargeRemaining -= h
            consumed += h
            steps++
        }
        if (chargeRemaining <= 1e-4f) cancelCharge()
        return consumed
    }

    val charging: Boolean get() = chargeRemaining > 0f

    /** Où en est la charge, de 0 à 1. */
    val chargeProgress: Float
        get() = if (chargeTotal <= 0f) 0f else
            (1f - chargeRemaining / chargeTotal).coerceIn(0f, 1f)

    /** La puissance mécanique installée sur la machine, en watts. */
    fun installedPower(): Float {
        var total = 0f
        for (gear in gears) {
            total += GearMotorRules.power(gear.wheel.motor ?: continue)
        }
        return total
    }

    private fun trailClear() { trailCount = 0 }

    private fun trailAdd(x: Float, y: Float) {
        if (trailCount + 2 > trailBuf.size) trailBuf = trailBuf.copyOf(trailBuf.size * 2)
        trailBuf[trailCount++] = x
        trailBuf[trailCount++] = y
    }

    /**
     * Termine le tir sur demande, sans attendre que le boulet se calme.
     *
     * Le boulet est gardé là où il en était : la traînée devient un fantôme arrêté à
     * ce point, et la portée déjà relevée reste acquise.
     */
    fun stopShot() {
        if (phase != Phase.FLIGHT) return
        finishShot()
    }

    /**
     * Range le tir : il devient un fantôme, et le boulet quitte la simulation.
     *
     * **Le boulet sort du monde**, il n'est pas seulement immobilisé. Un corps qui
     * reste dans le monde continue de rebondir, de rouler, de réveiller le solveur et
     * de tailler les sous-pas, pour une bille dont plus personne n'attend rien. Ce
     * qu'on veut garder est l'endroit où elle s'est arrêtée, et ça ne demande pas de
     * physique.
     */
    private fun finishShot() {
        phase = Phase.RESULT
        shotCount++
        projectile?.let { shot ->
            trailAdd(shot.body.x, shot.body.y)
            world.remove(shot.body)
        }
        ghostList.add(0, trailBuf.copyOf(trailCount))
        while (ghostList.size > ghostLimit) ghostList.removeAt(ghostList.size - 1)
        ghostStamp++
    }

    /** Rebande l'atelier : le boulet posé disparaît, son fantôme reste. */
    fun newShot() {
        if (phase == Phase.BUILD) return
        projectile?.let { world.remove(it.body) }
        projectile = null
        phase = Phase.BUILD
    }

    /** Efface la mémoire des tirs. */
    fun clearGhosts() {
        ghostList.clear()
        trailClear()
        ghostStamp++
    }

    /**
     * Relève le vol : le passage sur la cible, puis le premier contact au sol.
     *
     * La portée est celle du **toucher**, pas celle où le boulet finit de rouler : un
     * boulet qui frappe à quarante mètres par seconde rebondit et roule encore cent
     * mètres, ce qui n'apprend rien sur la machine qui l'a lancé. C'est la règle du
     * trébuchet, et les deux modes doivent se lire de la même façon.
     */
    private fun trackShot(shot: ProjectileState, dt: Float) {
        val body = shot.body
        if (!body.x.isFinite() || !body.y.isFinite() ||
            abs(body.x) > GearMachineRules.GROUND_HALF_WIDTH) {
            finishShot()
            return
        }
        trailTimer += dt
        if (trailTimer > TRAIL_INTERVAL && trailCount < MAX_TRAIL_FLOATS) {
            trailTimer = 0f
            trailAdd(body.x, body.y)
        }
        shot.peakY = maxOf(shot.peakY, body.y)
        if (!shot.hitTarget && crossesTarget(shot)) {
            shot.hitTarget = true
            if (shot.landed) lastShotHitTarget = true
        }
        if (!shot.landed) {
            // Trois façons de constater le toucher, parce qu'une seule ne suffit pas :
            // le boulet est au sol maintenant, il l'était à l'image précédente, ou le
            // moteur lui a compté un choc. Un boulet rapide frappe et **repart** en
            // l'air dans la même image : sans le troisième relevé, sa portée serait
            // celle de son deuxième rebond.
            // Le sol n'est plus a zero : sur une colline, un boulet << pose >> a
            // l'altitude zero serait deja trois metres sous terre.
            val touchLevel = terrain.heightAt(body.x) + body.radius + 0.03f
            val auSol = body.y <= touchLevel || shot.previousY <= touchLevel
            // **Un choc en l'air est un mur, pas le sol.** Le troisième relevé existe
            // pour les boulets rapides, qui frappent la terre et repartent dans la même
            // image ; il comptait n'importe quel choc, ce qui était sans conséquence
            // tant que l'atelier tirait sur une dalle nue. Depuis qu'il y a des
            // bâtiments, le premier mur touché arrêtait le tir : la portée se lisait au
            // pied du rempart, et en arcade le boulet est justement censé le traverser
            // et retomber plus loin.
            val chocAuSol = body.impactAccum > 0f && body.y <= touchLevel + 2f * body.radius

            // **Quatrième relevé : un boulet arrêté a atterri**, où qu'il se soit
            // arrêté. Les trois autres cherchent tous le *sol*, et c'était suffisant
            // quand il n'y avait rien d'autre à toucher. Depuis qu'il y a des bâtiments,
            // un gros boulet laboure le village et finit couché **sur ses gravats**, à
            // deux mètres du sol : aucun des trois relevés ne le voyait, le tir restait
            // « en vol » indéfiniment, et le joueur ne pouvait plus tirer. Mesuré : un
            // boulet de deux cent cinquante kilos restait en vol au bout de quinze
            // secondes, le village rasé à quatre-vingt-huit pour cent.
            shot.stalled = if (hypot(body.vx, body.vy) < STALL_SPEED) shot.stalled + dt else 0f
            val arrete = shot.stalled >= STALL_TIME
            if (auSol || chocAuSol || arrete) {
                shot.landed = true
                // Un boulet arrêté est déjà là où il finit : rien à interpoler.
                shot.distance =
                    (if (arrete) body.x else groundContactX(shot, touchLevel)) - shot.startX
                lastShotDistance = shot.distance
                lastShotHeight = shot.peakY - shot.startY
                lastShotHitTarget = shot.hitTarget
            }
        }
        shot.previousX = body.x
        shot.previousY = body.y
        if (shot.landed) endOfShot(shot, dt)
    }

    /**
     * Quand un tir est fini.
     *
     * Deux conditions, et il en suffit d'une. Le boulet **s'est calmé** — c'est le cas
     * ordinaire, et le petit roulement qui suit l'impact fait partie du spectacle. Ou
     * bien il roule depuis assez longtemps pour qu'on ait tout vu : une bille lancée à
     * grande vitesse sur une plaine parfaitement plate rebondit et roule presque
     * indéfiniment, et attendre son immobilité serait attendre pour rien.
     */
    private fun endOfShot(shot: ProjectileState, dt: Float) {
        sinceLanding += dt
        val body = shot.body
        val calm = body.speedSq < REST_SPEED * REST_SPEED && abs(body.omega) < 1.5f
        restCalm = if (calm) restCalm + dt else 0f
        if (restCalm >= REST_CALM || sinceLanding >= MAX_ROLL) finishShot()
    }

    /** Où le vol a croisé le sol, interpolé sur l'image : la portée s'y lit. */
    private fun groundContactX(shot: ProjectileState, level: Float): Float {
        val y0 = shot.previousY
        val y1 = shot.body.y
        if (y0 <= level) return shot.previousX
        if (y1 > level) return shot.body.x
        val t = ((y0 - level) / (y0 - y1)).coerceIn(0f, 1f)
        return shot.previousX + (shot.body.x - shot.previousX) * t
    }

    private fun crossesTarget(shot: ProjectileState): Boolean =
        GearMachineRules.segmentHitsTarget(
            shot.targetX, shot.body.radius,
            shot.previousX, shot.previousY, shot.body.x, shot.body.y
        )

    /**
     * Embrayage centrifuge simplifié : à grand écart de vitesse il patine et protège
     * la roue lourde ; en se rapprochant de la synchronisation il serre davantage.
     */
    private fun updateProgressiveClutches(dt: Float) {
        for (transmission in transmissions) {
            if (transmission.config.kind != GearLinkKind.SHAFT_CLUTCH) continue
            val joint = transmission.joint as? GearJoint ?: continue
            val a = gears.firstOrNull { it.wheel.id == transmission.config.firstId } ?: continue
            val b = gears.firstOrNull { it.wheel.id == transmission.config.secondId } ?: continue
            val slipSpeed = abs(a.body.omega - b.body.omega)
            val targetGrip = 0.16f + 0.84f / (1f + slipSpeed / 7f)
            val response = (dt * if (targetGrip > transmission.clutchGrip) 2.4f else 7f).coerceIn(0f, 1f)
            transmission.clutchGrip += (targetGrip - transmission.clutchGrip) * response
            val smallerRadius = minOf(a.wheel.pitchRadius, b.wheel.pitchRadius).coerceAtLeast(0.25f)
            // La capacité pleine doit dépasser le frottement du palier d'une grande
            // roue d'acier ; c'est la prise faible à grand glissement qui adoucit le
            // choc, pas un embrayage trop faible incapable de la mettre en mouvement.
            joint.maxTorqueOnB = 90_000f * smallerRadius * transmission.clutchGrip.coerceIn(0.05f, 1f)
        }
    }

    fun addGear(teeth: Int, x: Float, y: Float, layer: Int): Int? {
        if (config.wheels.size >= GearMachineRules.MAX_GEARS) return null
        val wheel = GearWheelConfig(
            config.nextId++, x, y,
            teeth.coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH),
            layer.coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        )
        snap(wheel, ignoreId = -1, GearMachineRules.MAGNET_ENGAGE_MODULES)
        config.wheels += wheel
        rebuild()
        return wheel.id
    }

    fun moveGear(id: Int, x: Float, y: Float, snap: Boolean) {
        if (config.isPinned(id)) return
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return
        moveCoaxialGroup(id, x, y)
        val target = if (snap) {
            snap(wheel, id, GearMachineRules.MAGNET_ENGAGE_MODULES)
        } else null
        moveCoaxialFollowers(id)
        rebuild(useConfiguredAngleFor = if (target != null) setOf(id) else emptySet())
    }

    /**
     * Déplacement assisté. [preferredTargetId] donne une petite hystérésis à une prise
     * déjà engagée ; dès que le doigt s'en écarte franchement, la roue redevient libre.
     */
    fun moveGearMagnetic(id: Int, x: Float, y: Float, preferredTargetId: Int?): Int? {
        // Le volant est planté sur son pas de tir : c'est l'origine de la mesure, et
        // le laisser glisser rendrait deux essais incomparables.
        if (config.isPinned(id)) return null
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        moveCoaxialGroup(id, x, y)
        val held = preferredTargetId?.let {
            snap(wheel, id, GearMachineRules.MAGNET_RELEASE_MODULES, it)
        }
        val target = held ?: snap(wheel, id, GearMachineRules.MAGNET_ENGAGE_MODULES)
        moveCoaxialFollowers(id)
        rebuild(useConfiguredAngleFor = if (target != null) setOf(id) else emptySet())
        return target
    }

    /**
     * Ensemble mécanique solidaire d'une roue : engrènements et transmissions.
     * Les arbres coaxiaux y sont déjà inclus via leurs transmissions.
     */
    fun assemblyIds(startId: Int): Set<Int> {
        if (config.wheels.none { it.id == startId }) return emptySet()
        val ids = linkedSetOf(startId)
        var changed = true
        while (changed) {
            changed = false
            for (mesh in meshes) {
                if (mesh.firstId in ids && ids.add(mesh.secondId)) changed = true
                if (mesh.secondId in ids && ids.add(mesh.firstId)) changed = true
            }
            for (link in config.links) {
                if (link.firstId in ids && ids.add(link.secondId)) changed = true
                if (link.secondId in ids && ids.add(link.firstId)) changed = true
            }
        }
        return ids
    }

    /** Déplace une machine assemblée en gardant toutes ses distances internes. */
    fun moveAssembly(id: Int, x: Float, y: Float) {
        val root = config.wheels.firstOrNull { it.id == id } ?: return
        val dx = x.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD) - root.x
        val dy = y.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD) - root.y
        // La fermeture transitive se calcule **une** fois : elle était évaluée pour
        // chaque roue de l'atelier, à chaque événement du doigt.
        val members = assemblyIds(id)
        // Le lanceur reste sur son socle meme quand on tire tout le batî : la prise se
        // defait alors d'elle-meme, ce qui est exactement ce que le geste demande.
        for (wheel in config.wheels) if (wheel.id in members && !config.isPinned(wheel.id)) {
            wheel.x = (wheel.x + dx).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            wheel.y = (wheel.y + dy).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        }
        rebuild()
    }

    private fun coaxialIds(startId: Int): Set<Int> {
        val ids = linkedSetOf(startId)
        var changed = true
        while (changed) {
            changed = false
            for (link in config.links) {
                if (link.kind != GearLinkKind.SHAFT_CLUTCH) continue
                if (link.firstId in ids && ids.add(link.secondId)) changed = true
                if (link.secondId in ids && ids.add(link.firstId)) changed = true
            }
        }
        return ids
    }

    private fun moveCoaxialGroup(id: Int, x: Float, y: Float) {
        if (config.isPinned(id)) return
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return
        val nx = x.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        val ny = y.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        val dx = nx - wheel.x
        val dy = ny - wheel.y
        val shaft = coaxialIds(id)
        for (member in config.wheels) if (member.id in shaft) {
            member.x = (member.x + dx).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            member.y = (member.y + dy).coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
        }
    }

    /** Après un cran magnétique, recolle le reste de l'arbre sur la roue manipulée. */
    private fun moveCoaxialFollowers(id: Int) {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return
        val shaft = coaxialIds(id)
        for (member in config.wheels) if (member.id in shaft && member.id != id) {
            if (config.isPinned(member.id)) continue
            member.x = wheel.x
            member.y = wheel.y
        }
    }

    private fun alignShaftCenters() {
        val visited = HashSet<Int>()
        for (root in config.wheels) {
            if (!visited.add(root.id)) continue
            val group = coaxialIds(root.id)
            visited += group
            if (group.size < 2) continue
            // L'ancre est le lanceur s'il est du groupe : lui seul ne bouge pas.
            val anchor = config.wheels.firstOrNull { it.id in group && config.isPinned(it.id) } ?: root
            for (member in config.wheels) if (member.id in group && member.id != anchor.id) {
                member.x = anchor.x
                member.y = anchor.y
            }
        }
    }

    /** Fige la phase visible atteinte par la simulation avant une opération d'édition. */
    fun captureWheelAngles() {
        for (state in gears) state.wheel.angle = state.body.angle
    }

    private fun snap(
        wheel: GearWheelConfig,
        ignoreId: Int,
        distanceModules: Float,
        onlyTargetId: Int? = null
    ): Int? {
        var best: GearWheelConfig? = null
        var bestError = GearMachineRules.MODULE * distanceModules
        for (other in config.wheels) {
            if (other.id == ignoreId || other.layer != wheel.layer ||
                (onlyTargetId != null && other.id != onlyTargetId)) continue
            val distance = hypot(wheel.x - other.x, wheel.y - other.y)
            val expected = wheel.pitchRadius + other.pitchRadius
            val error = abs(distance - expected)
            if (error < bestError && distance > 1e-4f) {
                best = other
                bestError = error
            }
        }
        best?.let { other ->
            val angle = atan2(wheel.y - other.y, wheel.x - other.x)
            val distance = wheel.pitchRadius + other.pitchRadius
            wheel.x = other.x + cos(angle) * distance
            wheel.y = other.y + sin(angle) * distance
            alignTeeth(wheel, other, angle)
        }
        return best?.id
    }

    /** Place une dent face à un creux, quelle que soit l'orientation de la prise. */
    private fun alignTeeth(wheel: GearWheelConfig, other: GearWheelConfig, centerAngle: Float) {
        val tau = (Math.PI * 2.0).toFloat()
        fun unitPhase(value: Float): Float {
            val remainder = value % 1f
            return if (remainder < 0f) remainder + 1f else remainder
        }
        val otherPhase = unitPhase((centerAngle - other.angle) * other.teeth / tau)
        val complementaryPhase = unitPhase(otherPhase + 0.5f)
        wheel.angle = centerAngle + Math.PI.toFloat() - complementaryPhase * tau / wheel.teeth
    }

    /** Les pieces uniques ne se suppriment pas : la machine ne peut pas en manquer. */
    fun isRemovable(id: Int): Boolean {
        if (config.isPinned(id)) return false
        return config.wheels.firstOrNull { it.id == id }?.motor == null
    }

    fun deleteGear(id: Int): Boolean {
        if (!isRemovable(id)) return false
        val removed = config.wheels.removeAll { it.id == id }
        config.links.removeAll { it.firstId == id || it.secondId == id }
        if (config.launcherWheelId == id) config.launcherWheelId = null
        if (removed) rebuild()
        return removed
    }

    /**
     * Redimensionne une roue comme une opération transactionnelle sur tout le train.
     * La roue éditée reste l'ancre ; chaque engrènement existant repousse sa branche,
     * y compris à travers les groupes coaxiaux. Au moindre conflit, rien ne bouge.
     */
    fun resizeGearAndReflow(id: Int, teeth: Int): GearResizeResult? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        val clamped = teeth.coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH)
        if (wheel.teeth == clamped) return GearResizeResult(clamped)

        val wheelById = config.wheels.associateBy { it.id }
        val groupByWheel = HashMap<Int, Int>()
        for (candidate in config.wheels) {
            if (candidate.id in groupByWheel) continue
            val group = coaxialIds(candidate.id)
            val root = group.minOrNull() ?: candidate.id
            for (memberId in group) groupByWheel[memberId] = root
        }
        val rootGroup = groupByWheel[id] ?: id
        val constraints = ArrayList<MeshConstraint>()
        val meshPairs = HashSet<String>()
        for (mesh in meshes) {
            val a = wheelById[mesh.firstId] ?: continue
            val b = wheelById[mesh.secondId] ?: continue
            val groupA = groupByWheel[a.id] ?: a.id
            val groupB = groupByWheel[b.id] ?: b.id
            if (groupA == groupB) continue
            val dx = b.x - a.x
            val dy = b.y - a.y
            val distance = hypot(dx, dy)
            if (distance <= 1e-4f) continue
            constraints += MeshConstraint(groupA, groupB, a.id, b.id, dx / distance, dy / distance)
            meshPairs += pairKey(a.id, b.id)
        }

        fun pitchRadius(candidate: GearWheelConfig): Float =
            (if (candidate.id == id) clamped else candidate.teeth) * GearMachineRules.MODULE / 2f
        fun outerRadius(candidate: GearWheelConfig): Float = pitchRadius(candidate) + GearMachineRules.MODULE

        val proposed = HashMap<Int, FloatArray>()
        proposed[rootGroup] = floatArrayOf(wheel.x, wheel.y)
        val parentEdge = HashMap<Int, Pair<Int, MeshConstraint>>()
        val traversalOrder = ArrayList<Int>()
        val queue = java.util.ArrayDeque<Int>()
        queue.add(rootGroup)
        val conflicts = linkedSetOf<Int>()
        while (queue.isNotEmpty()) {
            val group = queue.removeFirst()
            val origin = proposed[group] ?: continue
            for (edge in constraints) {
                val forward = edge.groupA == group
                if (!forward && edge.groupB != group) continue
                val next = if (forward) edge.groupB else edge.groupA
                val a = wheelById[edge.wheelA] ?: continue
                val b = wheelById[edge.wheelB] ?: continue
                val distance = pitchRadius(a) + pitchRadius(b)
                val direction = if (forward) 1f else -1f
                val candidate = floatArrayOf(
                    origin[0] + edge.ux * direction * distance,
                    origin[1] + edge.uy * direction * distance
                )
                val known = proposed[next]
                if (known == null) {
                    proposed[next] = candidate
                    parentEdge[next] = group to edge
                    traversalOrder += next
                    queue.add(next)
                } else if (hypot(known[0] - candidate[0], known[1] - candidate[1]) >
                    GearMachineRules.MODULE * 0.18f) {
                    conflicts += id
                    conflicts += edge.wheelA
                    conflicts += edge.wheelB
                }
            }
        }

        fun position(candidate: GearWheelConfig): FloatArray =
            proposed[groupByWheel[candidate.id] ?: candidate.id]
                ?: floatArrayOf(candidate.x, candidate.y)

        // Seuls les groupes que ce redimensionnement déplace (ou dont le rayon
        // change) peuvent rendre l'opération impossible. Une collision déjà
        // présente dans une autre zone de l'atelier ne doit pas empêcher
        // l'édition d'une roue isolée.
        val affectedGroups = proposed.keys
        for (candidate in config.wheels) {
            if ((groupByWheel[candidate.id] ?: candidate.id) !in affectedGroups) continue
            val p = position(candidate)
            if (p[0] !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD ||
                p[1] !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD) {
                conflicts += id
                conflicts += candidate.id
            }
        }
        for (i in 0 until config.wheels.size) for (j in i + 1 until config.wheels.size) {
            val a = config.wheels[i]
            val b = config.wheels[j]
            if (a.layer != b.layer || pairKey(a.id, b.id) in meshPairs) continue
            val groupA = groupByWheel[a.id] ?: a.id
            val groupB = groupByWheel[b.id] ?: b.id
            if (groupA !in affectedGroups && groupB !in affectedGroups) continue
            val pa = position(a)
            val pb = position(b)
            val distance = hypot(pb[0] - pa[0], pb[1] - pa[1])
            val clearance = outerRadius(a) + outerRadius(b) - GearMachineRules.MODULE * 0.12f
            if (distance < clearance) {
                conflicts += id
                conflicts += a.id
                conflicts += b.id
            }
        }
        if (conflicts.isNotEmpty()) return GearResizeResult(wheel.teeth, conflicts)

        wheel.teeth = clamped
        for (candidate in config.wheels) {
            val p = position(candidate)
            candidate.x = p[0]
            candidate.y = p[1]
        }
        // Les branches déplacées reprennent aussi une phase dent/creux cohérente.
        for (childGroup in traversalOrder) {
            val parent = parentEdge[childGroup] ?: continue
            val parentGroup = parent.first
            val edge = parent.second
            val childWheelId = if (edge.groupA == childGroup) edge.wheelA else edge.wheelB
            val parentWheelId = if (edge.groupA == parentGroup) edge.wheelA else edge.wheelB
            val child = wheelById[childWheelId] ?: continue
            val anchor = wheelById[parentWheelId] ?: continue
            val centerAngle = atan2(child.y - anchor.y, child.x - anchor.x)
            alignTeeth(child, anchor, centerAngle)
        }
        rebuild()
        return GearResizeResult(clamped)
    }

    private fun pairKey(firstId: Int, secondId: Int): String =
        "${minOf(firstId, secondId)}:${maxOf(firstId, secondId)}"

    /**
     * Copie une roue près de l'originale, mais hors de la portée d'engrènement.
     * Les liaisons et le lanceur ne sont pas dupliqués : la nouvelle pièce reste
     * immédiatement réglable avant que l'utilisateur choisisse de la raccorder.
     */
    fun duplicateGear(id: Int): Int? {
        // Dupliquer une piece unique en ferait deux : c'est justement ce qu'on interdit.
        if (!isRemovable(id)) return null
        if (config.wheels.size >= GearMachineRules.MAX_GEARS) return null
        val source = config.wheels.firstOrNull { it.id == id } ?: return null
        val position = findFreeDuplicatePosition(source) ?: return null
        val copy = source.copy(
            id = config.nextId++,
            x = position[0],
            y = position[1]
        )
        config.wheels += copy
        rebuild()
        return copy.id
    }

    /** Cherche une place de préparation, sans contact ni aimantation automatique. */
    private fun findFreeDuplicatePosition(source: GearWheelConfig): FloatArray? {
        // La copie est posée avec assez de marge pour aller directement jusqu'à
        // sa taille maximale depuis sa bulle, sans venir heurter l'originale.
        val maxCopyOuterRadius =
            GearMachineRules.MAX_TEETH * GearMachineRules.MODULE / 2f + GearMachineRules.MODULE
        val baseDistance = source.outerRadius + maxCopyOuterRadius + GearMachineRules.MODULE * 2f
        val angles = 16
        for (ring in 1..4) {
            val distance = baseDistance * ring
            for (index in 0 until angles) {
                val angle = (Math.PI * 2.0 * index / angles).toFloat()
                val x = source.x + cos(angle) * distance
                val y = source.y + sin(angle) * distance
                if (x !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD ||
                    y !in GearMachineRules.MIN_COORD..GearMachineRules.MAX_COORD) continue
                val clear = config.wheels.filter { it.layer == source.layer }.all { other ->
                    hypot(x - other.x, y - other.y) >=
                        maxCopyOuterRadius + other.outerRadius + GearMachineRules.MODULE * 0.35f
                }
                if (clear) return floatArrayOf(x, y)
            }
        }
        return null
    }

    fun changeLayer(id: Int, delta: Int): Int? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        return setLayer(id, wheel.layer + delta)
    }

    /**
     * Pose une roue sur une couche donnee.
     *
     * Un arbre coaxial relie deux couches **differentes** par definition : celui qui
     * se retrouverait avec ses deux bouts au meme etage n'a plus de sens, et il part.
     */
    fun setLayer(id: Int, layer: Int): Int? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        wheel.layer = layer.coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
        if (config.isPinned(id)) wheel.y = GearMachineRules.launcherY(wheel)
        config.links.removeAll { link ->
            if (link.kind != GearLinkKind.SHAFT_CLUTCH || (link.firstId != id && link.secondId != id)) {
                false
            } else {
                val otherId = if (link.firstId == id) link.secondId else link.firstId
                config.wheels.firstOrNull { it.id == otherId }?.layer == wheel.layer
            }
        }
        rebuild()
        return wheel.layer
    }

    fun changeMaterial(id: Int, delta: Int): GearWheelMaterial? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        val materials = GearWheelMaterial.entries
        val index = Math.floorMod(wheel.material.ordinal + delta, materials.size)
        wheel.material = materials[index]
        rebuild()
        return wheel.material
    }

    /** Choisit le lanceur qui tire. Seuls un volant ou un canon portent une visée. */
    fun attachLauncher(id: Int): Boolean {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return false
        if (wheel.kind !in GearMachineRules.LAUNCHER_KINDS) return false
        config.launcherWheelId = id
        return true
    }

    /** Regle l'elevation du tir d'un volant ou l'angle du tube d'un canon. */
    fun setLaunchAngle(id: Int, degrees: Float): Float? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        if (wheel.kind !in GearMachineRules.LAUNCHER_KINDS) return null
        wheel.launchAngle = degrees.coerceIn(
            GearMachineRules.MIN_LAUNCH_DEG, GearMachineRules.MAX_LAUNCH_DEG
        )
        return wheel.launchAngle
    }

    /**
     * Bascule la pièce épinglée entre volant et canon — même geste que
     * [cycleMotorKind] pour un moteur, même bouton dans la bulle d'édition.
     *
     * Position, denture et matière restent : seule la **forme** du lanceur change.
     * L'oméga et l'angle survivent au passage via [rebuild] comme pour tout
     * remontage ; le réservoir d'un canon qu'on vient de démonter, lui, cesse
     * simplement d'être lu tant qu'on ne rebascule pas dessus.
     */
    fun cycleLauncherKind(id: Int): GearWheelKind? {
        if (id != config.launcherWheelId) return null
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        wheel.kind = if (wheel.kind == GearWheelKind.PUMP) {
            GearWheelKind.FLYWHEEL
        } else {
            GearWheelKind.PUMP
        }
        rebuild()
        return wheel.kind
    }

    /**
     * La masse du boulet. C'est le réglage qui décide de tout le reste : à énergie
     * donnée, `E = ½mv²` échange la vitesse contre la masse, et le même train envoie
     * soit une bille très loin, soit un bloc très fort.
     */
    fun setProjectileMass(mass: Float): Float {
        config.projectileMass = mass.coerceIn(
            GearMachineRules.MIN_PROJECTILE_MASS, GearMachineRules.MAX_PROJECTILE_MASS
        )
        return config.projectileMass
    }

    /**
     * Change le moteur d'une roue, d'un cran dans la liste.
     *
     * L'ordre des crans est celui de l'énumération, « aucun » compris : on démonte
     * donc un moteur par le même geste qui l'a posé.
     */
    /**
     * Passe au moteur suivant, en tournant en rond sur les trois.
     *
     * Il n'y a pas de cran « aucun » : la machine a **toujours** un moteur, et une
     * roue motrice qu'on viderait ne pourrait plus jamais en retrouver un.
     */
    fun cycleMotorKind(id: Int): GearMotorKind? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        val motor = wheel.motor ?: return null
        val kinds = GearMotorKind.entries.filter { it != GearMotorKind.NONE }
        val next = kinds[(kinds.indexOf(motor.kind) + 1).mod(kinds.size)]
        // **Le gabarit ne bouge pas.** Une roue de cage de seize metres existe pour de
        // bon -- les grues de cathedrale en avaient -- et repartir du gabarit normal a
        // chaque changement effacerait un reglage que le joueur venait de trouver. Le
        // genre change, la taille reste : c'est deux reglages, pas un.
        motor.kind = next
        rebuild()
        return next
    }


    /**
     * Change l'attelage d'un moteur : le nombre d'unités, et son signe le sens.
     *
     * Les crans traversent zéro : à une unité, un cran de plus vers le bas repasse de
     * l'autre côté avec une unité. Le sens de rotation est donc au bout du même
     * réglage, ce qui évite une ligne de plus dans une bulle déjà chargée.
     */
    fun changeMotorUnits(id: Int, delta: Int): Int? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        val motor = wheel.motor ?: return null
        val signed = motor.units * if (motor.direction < 0) -1 else 1
        // La suite des crans : -8..-1 puis 1..8, sans le zéro qui ne veut rien dire.
        val next = when (val moved = signed + delta) {
            0 -> if (delta > 0) 1 else -1
            else -> moved
        }
        return setMotorUnits(id, next)
    }

    /**
     * Regle l'attelage d'un coup : le nombre d'unites, et son signe le sens.
     *
     * Zero n'existe pas — un moteur sans bete attelee ne serait pas un moteur, ce
     * serait l'absence de moteur, et ca se dit sur l'autre ligne.
     */
    fun setMotorUnits(id: Int, signedUnits: Int): Int? {
        val wheel = config.wheels.firstOrNull { it.id == id } ?: return null
        val motor = wheel.motor ?: return null
        val clamped = signedUnits.coerceIn(-GearMotorRules.MAX_UNITS, GearMotorRules.MAX_UNITS)
        motor.units = kotlin.math.abs(clamped)
            .coerceIn(GearMotorRules.MIN_UNITS, GearMotorRules.MAX_UNITS)
        motor.direction = if (clamped < 0) -1 else 1
        rebuild()
        return motor.units * motor.direction
    }


    /**
     * Regle le gabarit d'une machine motrice, en metres de rayon.
     *
     * C'est **le** levier d'un moteur : la puissance d'un moulin suit le carre de son
     * envergure, le couple d'un manege la longueur du bras, la chute d'une roue a eau
     * son diametre. La denture du rouet, elle, ne change rien a la puissance.
     */
    fun setMotorSpan(id: Int, span: Float): Float? {
        val motor = config.wheels.firstOrNull { it.id == id }?.motor ?: return null
        motor.span = span.takeIf { it.isFinite() }
            ?.coerceIn(GearMotorRules.MIN_SPAN, GearMotorRules.MAX_SPAN)
            ?: return null
        rebuild()
        return motor.span
    }

    /** Règle la durée d'une charge, commune à toute la machine. */
    fun setChargeSeconds(seconds: Float): Float {
        config.chargeSeconds = seconds.coerceIn(
            GearMachineRules.MIN_CHARGE, GearMachineRules.MAX_CHARGE
        )
        return config.chargeSeconds
    }

    fun addLink(firstId: Int, secondId: Int, kind: GearLinkKind, inputDirection: Int = 1): Boolean {
        if (firstId == secondId || config.links.size >= GearMachineRules.MAX_LINKS) return false
        val first = config.wheels.firstOrNull { it.id == firstId } ?: return false
        val second = config.wheels.firstOrNull { it.id == secondId } ?: return false
        if (kind == GearLinkKind.SHAFT_CLUTCH) {
            if (first.layer == second.layer) return false
            second.x = first.x
            second.y = first.y
            second.angle = first.angle
        } else if (first.layer != second.layer) return false
        config.links.removeAll {
            (it.firstId == firstId && it.secondId == secondId) ||
                (it.firstId == secondId && it.secondId == firstId)
        }
        config.links += GearLinkConfig(firstId, secondId, kind, if (inputDirection < 0) -1 else 1)
        rebuild()
        return true
    }

    fun removeLinksFor(id: Int): Int {
        val before = config.links.size
        config.links.removeAll { it.firstId == id || it.secondId == id }
        val removed = before - config.links.size
        if (removed > 0) rebuild()
        return removed
    }

    fun slippingCount(): Int = meshes.count { it.joint.slipping } + transmissions.count {
        when (val joint = it.joint) {
            is GearJoint -> joint.slipping
            is OneWayRotaryJoint -> joint.slipping
            else -> false
        }
    }

    fun rotationalEnergy(ids: Set<Int>? = null): Float {
        var energy = 0.0
        for (gear in gears) {
            if (ids == null || gear.wheel.id in ids) {
                energy += 0.5 * gear.body.inertia * gear.body.omega * gear.body.omega
            }
        }
        return energy.coerceAtMost(Float.MAX_VALUE.toDouble()).toFloat()
    }

    fun connectedTo(startId: Int): Set<Int> {
        val found = linkedSetOf(startId)
        var changed = true
        while (changed) {
            changed = false
            for (mesh in meshes) {
                if (mesh.firstId in found && found.add(mesh.secondId)) changed = true
                if (mesh.secondId in found && found.add(mesh.firstId)) changed = true
            }
            for (transmission in transmissions) {
                val usable = transmission.config.kind != GearLinkKind.CHAIN_FREEWHEEL ||
                    (transmission.joint as OneWayRotaryJoint).engaged
                if (!usable) continue
                val first = transmission.config.firstId
                val second = transmission.config.secondId
                if (first in found && found.add(second)) changed = true
                if (second in found && found.add(first)) changed = true
            }
        }
        return found
    }

    /**
     * Embraye le lanceur sur son arbre et convertit une partie de l'énergie du train
     * connecté en énergie de translation. Le solde est laissé dans les roues ; les
     * pertes du rendement disparaissent en chaleur, jamais en vitesse supplémentaire.
     */
    /**
     * La vitesse qu'aurait le boulet s'il partait maintenant, en m/s.
     *
     * C'est le seul chiffre qui compte pour tirer : la jante donne sa vitesse, et rien
     * d'autre. Une machine pleine d'energie mais lente ne lance rien.
     */
    fun rimSpeed(): Float {
        val wheel = config.launcher() ?: return 0f
        val omega = gears.firstOrNull { it.wheel.id == wheel.id }?.body?.omega ?: return 0f
        return abs(omega) * wheel.launchRadius
    }

    /**
     * Le sens dans lequel un volant lachera son boulet.
     *
     * Un volant a l'arret n'a pas de sens : on lui en prete un, le meme que celui du
     * croquis, pour que le bras ait toujours un cote ou se dessiner et que le reglage
     * de l'angle reste lisible machine immobile.
     */
    fun launchSpin(wheelId: Int): Float {
        val omega = gears.firstOrNull { it.wheel.id == wheelId }?.body?.omega ?: 0f
        return when {
            omega > 1e-4f -> 1f
            omega < -1e-4f -> -1f
            else -> GearMachineRules.DEFAULT_SPIN
        }
    }

    /**
     * Ou se trouve l'encoche de sortie sur la jante, en radians.
     *
     * Le boulet part **tangentiellement**, donc perpendiculairement au rayon : c'est la
     * gorge qui lance, pas le moyeu. L'encoche se tient donc un quart de tour en
     * arriere de la direction de tir, du cote d'ou la roue arrive -- a droite du tir
     * quand elle tourne dans le sens horaire, a gauche quand elle tourne a l'envers.
     */
    fun launchPointAngle(wheel: GearWheelConfig): Float {
        val aim = Math.toRadians(wheel.launchAngle.toDouble()).toFloat()
        return aim - launchSpin(wheel.id) * (Math.PI / 2.0).toFloat()
    }

    /**
     * Lache le boulet depuis la jante du volant.
     *
     * **La vitesse est celle du bout du bras, pas une conversion d'energie.** L'ancien
     * lanceur prenait toute l'energie du train et en faisait de la vitesse : une roue
     * d'acier de six metres pese vingt tonnes, et le moindre tour de main envoyait le
     * boulet a cinq cents metres par seconde. Une machine reelle ne peut donner que la
     * vitesse de sa gorge, `omega x rayon` -- pour tirer loin il faut tourner vite ou
     * agrandir la roue, ce qui est tout l'interet d'un volant.
     *
     * L'energie du train reste la borne haute : on ne peut pas emporter plus que ce
     * qu'il y a, et ce qu'on emporte lui est retire.
     */
    fun launchProjectile(): Boolean {
        // Tirer met fin a la charge : on ne remplit pas un volant pendant qu'il vide.
        cancelCharge()
        val launcherId = config.launcherWheelId ?: return false
        val launcher = gears.firstOrNull { it.wheel.id == launcherId } ?: return false
        val mass = config.projectileMass
        val efficiency = GearMachineRules.LAUNCH_EFFICIENCY

        val projectileEnergy: Float
        val muzzleX: Float
        val muzzleY: Float
        val nx: Float
        val ny: Float

        when (launcher.wheel.kind) {
            GearWheelKind.FLYWHEEL -> {
                val omega = launcher.body.omega
                if (abs(omega) < GearMachineRules.MIN_LAUNCH_OMEGA) return false
                val connected = connectedTo(launcherId)
                val available = rotationalEnergy(connected)
                if (available <= 0f) return false

                val armRadius = launcher.wheel.launchRadius
                val rimSpeed = (abs(omega) * armRadius).coerceAtMost(MAX_PROJECTILE_SPEED)
                // Ce que la jante voudrait donner, et ce que le train peut reellement
                // fournir.
                val wanted = 0.5f * mass * rimSpeed * rimSpeed
                projectileEnergy = minOf(wanted, available * efficiency)
                if (projectileEnergy <= 0f) return false

                val energyTaken = projectileEnergy / efficiency
                val remainingScale = sqrt(((available - energyTaken) / available).coerceIn(0f, 1f))
                for (gear in gears) if (gear.wheel.id in connected) {
                    gear.body.omega *= remainingScale
                    gear.body.wake()
                }
                for (mesh in meshes) mesh.joint.reset()
                for (transmission in transmissions) transmission.joint.reset()

                // Le bras au moment du lacher, et la tangente qui en part.
                val armAngle = launchPointAngle(launcher.wheel)
                val aim = Math.toRadians(launcher.wheel.launchAngle.toDouble()).toFloat()
                nx = cos(aim)
                ny = sin(aim)
                muzzleX = launcher.body.x + cos(armAngle) * armRadius
                muzzleY = launcher.body.y + sin(armAngle) * armRadius
            }
            GearWheelKind.PUMP -> {
                // Un canon ne dose pas : la vanne s'ouvre en grand, le réservoir se
                // vide entièrement d'un coup — pas de reliquat comme un volant qui
                // continue de tourner après avoir cédé une part de son élan.
                val available = reservoirEnergy(launcher.reservoirStdVolume, launcher.wheel.reservoirVolume)
                if (available <= 0f) return false
                projectileEnergy = available * efficiency
                launcher.reservoirStdVolume = 0f

                // Un tube ne lance pas tangentiellement : la vitesse part droit dans
                // l'axe visé. La bouche est au bout du tube, monté sur un tourillon
                // fixe planté à distance de la manivelle — les pistons et le réservoir
                // se dressent entre les deux, voir [GearCannonArt] pour le même repère.
                val aim = Math.toRadians(launcher.wheel.launchAngle.toDouble()).toFloat()
                nx = cos(aim)
                ny = sin(aim)
                val pivotX = launcher.body.x + GearMachineRules.CANNON_PIVOT_DISTANCE
                val pivotY = launcher.body.y
                muzzleX = pivotX + nx * GearMachineRules.CANNON_BARREL_LENGTH
                muzzleY = pivotY + ny * GearMachineRules.CANNON_BARREL_LENGTH
            }
            else -> return false
        }

        val speed = sqrt(2f * projectileEnergy / mass).coerceAtMost(MAX_PROJECTILE_SPEED)
        if (phase == Phase.FLIGHT) projectile?.let { world.remove(it.body) }
        val radius = (0.075f * kotlin.math.cbrt(mass.toDouble())).toFloat().coerceIn(0.06f, 0.55f)
        val shot = PhysBody.circle(radius, mass).apply {
            x = muzzleX
            // Un volant pose tres bas ferait naitre le boulet dans la terre, et le
            // tir serait << pose >> avant d'avoir commence.
            y = muzzleY.coerceAtLeast(radius + 0.05f)
            vx = nx * speed
            vy = ny * speed
            restitution = 0.18f
            friction = 0.55f
            dragFactor = 0.5f * 1.225f * 0.47f * Math.PI.toFloat() * radius * radius
            category = GearMachineRules.CATEGORY_SHOT
            // Le sol, les pierres et les gravats — tout ce qui n'est pas le mecanisme.
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
            // Sur toutes les couches : le boulet quitte l'etage du lanceur des la sortie
            // du canon, et un site pose a la couche zero doit l'arreter quel que soit
            // l'etage d'ou il est parti.
            collisionLayer = GearMachineRules.MIN_LAYER
            collisionLayerDepth = GearMachineRules.MAX_LAYER - GearMachineRules.MIN_LAYER + 1
        }
        world.add(shot)
        // À partir d'ici, le site saura ce que ce corps-là casse lui-même — c'est ce qui
        // permet de ne lui faire payer que ça. Les compteurs du tir précédent s'oublient
        // au même endroit, sinon un boulet hériterait des ruines d'un autre.
        targets.forgetPiercers()
        targets.resetHitFlag()
        targets.trackPiercer(shot)
        // Le tir s'ouvre : le site relève l'état de chaque pierre, qui décidera de ce
        // que ce boulet-là a le droit de traverser. Même règle qu'au trébuchet.
        targets.beginShot()
        projectile = ProjectileState(shot, shot.x, shot.y, projectileEnergy, targetX())
        phase = Phase.FLIGHT
        trailClear()
        trailAdd(shot.x, shot.y)
        trailTimer = 0f
        restCalm = 0f
        sinceLanding = 0f
        lastShotDistance = 0f
        lastShotHeight = 0f
        lastShotHitTarget = false
        lastLaunchSpeed = speed
        lastLaunchEnergy = projectileEnergy
        return true
    }

    /**
     * La vitesse a laquelle le boulet partirait maintenant, en m/s.
     *
     * Ce n'est pas la vitesse de la jante : c'est **le plus petit** de ce que la jante
     * offre et de ce que le train peut payer. Une machine lancee tres vite mais vide
     * ne lancera pas plus loin qu'elle n'a d'energie, et l'afficher autrement serait
     * promettre un tir qui ne partira pas.
     */
    fun launchSpeedNow(): Float {
        val wheel = config.launcher() ?: return 0f
        val mass = config.projectileMass
        val available = launcherAvailableEnergy()
        if (available <= 0f) return 0f
        val payable = sqrt(2f * available * GearMachineRules.LAUNCH_EFFICIENCY / mass)
            .coerceAtMost(MAX_PROJECTILE_SPEED)
        if (wheel.kind == GearWheelKind.PUMP) return payable
        // Un volant a en plus une jante : il ne peut jamais rendre plus vite qu'elle
        // ne file, meme quand le train a de quoi payer davantage.
        val rim = rimSpeed()
        if (rim <= 0f) return 0f
        return minOf(rim, payable)
    }

    /**
     * L'énergie que le lanceur a sous la main **maintenant**, en joules.
     *
     * L'énergie cinétique du train relié pour un volant ([rotationalEnergy]),
     * celle déjà comprimée dans le réservoir pour un canon ([reservoirEnergy]).
     * C'est la grandeur que le panneau « Stocké » affiche, et celle que
     * [launchSpeedNow] convertit en vitesse de tir.
     */
    fun launcherAvailableEnergy(): Float {
        val wheel = config.launcher() ?: return 0f
        if (wheel.kind == GearWheelKind.PUMP) {
            val gear = gears.firstOrNull { it.wheel.id == wheel.id } ?: return 0f
            return reservoirEnergy(gear.reservoirStdVolume, wheel.reservoirVolume)
        }
        return rotationalEnergy(connectedTo(wheel.id))
    }

    /**
     * La portee qu'atteindrait le tir, en metres, **trainee comprise**.
     *
     * Une formule de tir dans le vide surestimait la vraie portee d'assez loin pour
     * ne plus servir a rien -- un boulet leger perd beaucoup a l'air, voir
     * [GearMachineRules.PISTON_AREA] et la traînée posée dans [launchProjectile]. On
     * rejoue donc la meme chute que le vrai tir, hors du monde physique : memes
     * pas, memes formules de trainee et d'amortissement que [PhysWorld.stepFrame],
     * juste sans corps ni collision a fabriquer pour un chiffre d'avant-tir.
     */
    fun estimatedRange(): Float {
        val wheel = config.launcher() ?: return 0f
        val v = launchSpeedNow()
        if (v <= 0.01f) return 0f
        val aim = Math.toRadians(wheel.launchAngle.toDouble()).toFloat()
        var vx = v * cos(aim)
        var vy = v * sin(aim)
        val state = gears.firstOrNull { it.wheel.id == wheel.id } ?: return 0f
        // La bouche part du bout du tube pour un canon, de la gorge pour un volant —
        // le meme depart qu'au tir reel, voir [launchProjectile].
        var x = 0f
        var y = if (wheel.kind == GearWheelKind.PUMP) {
            (state.body.y + sin(aim) * GearMachineRules.CANNON_BARREL_LENGTH).coerceAtLeast(0f)
        } else {
            val armAngle = launchPointAngle(wheel)
            (state.body.y + sin(armAngle) * wheel.launchRadius).coerceAtLeast(0f)
        }

        val mass = config.projectileMass
        val radius = (0.075f * kotlin.math.cbrt(mass.toDouble())).toFloat().coerceIn(0.06f, 0.55f)
        val dragFactor = 0.5f * PhysicsConstants.AIR_DENSITY * 0.47f * Math.PI.toFloat() * radius * radius
        val g = PhysicsConstants.STANDARD_GRAVITY
        // Un pas grossier suffit a une estimation -- ce n'est pas le tir qui se joue
        // ici, seulement sa longueur -- et ca borne le cout d'un chiffre recalcule a
        // chaque image du panneau.
        val dt = 1f / 30f
        val linearKeep = (1f - world.linearDamping * dt).coerceIn(0f, 1f)
        var steps = 0
        while (y > 0f && steps < 900) {
            vy -= g * dt
            if (dragFactor > 0f) {
                val speed = hypot(vx, vy)
                if (speed > 1e-4f) {
                    val dv = minOf(dragFactor * speed * speed / mass * dt, speed)
                    vx -= dv * vx / speed
                    vy -= dv * vy / speed
                }
            }
            x += vx * dt
            y += vy * dt
            vx *= linearKeep
            vy *= linearKeep
            steps++
        }
        return x
    }

    fun spinGear(id: Int, angularSpeed: Float) {
        val state = gears.firstOrNull { it.wheel.id == id } ?: return
        state.body.omega = angularSpeed.coerceIn(-GearMachineRules.MAX_MANUAL_SPEED, GearMachineRules.MAX_MANUAL_SPEED)
        state.body.wake()
    }

    /**
     * Entraîne la roue **pendant** que le doigt la tourne.
     *
     * La main se comporte comme une roue libre : elle peut porter la roue jusqu'à la
     * vitesse du doigt, jamais la freiner ni la faire reculer. C'est ce qui permet de
     * pomper — chaque passage rend un peu de vitesse et rien n'en reprend — et c'est
     * aussi la seule façon de voir la machine répondre tant qu'on la tient. Sans ça,
     * un doigt posé sur la jante ne produisait rien du tout avant d'être relâché.
     */
    fun driveGear(id: Int, gestureAngularSpeed: Float) {
        val state = gears.firstOrNull { it.wheel.id == id } ?: return
        if (!gestureAngularSpeed.isFinite()) return
        val target = gestureAngularSpeed.coerceIn(
            -GearMachineRules.MAX_MANUAL_SPEED,
            GearMachineRules.MAX_MANUAL_SPEED
        )
        val omega = state.body.omega
        val drives = (target > 0f && omega >= 0f && target > omega) ||
            (target < 0f && omega <= 0f && target < omega)
        if (!drives) return
        state.body.omega = target
        state.body.wake()
    }

    /** Ajoute un coup de doigt sans écraser l'élan déjà accumulé. */
    fun flickGear(id: Int, gestureAngularSpeed: Float) {
        val state = gears.firstOrNull { it.wheel.id == id } ?: return
        val added = gestureAngularSpeed.coerceIn(
            -GearMachineRules.MAX_MANUAL_SPEED,
            GearMachineRules.MAX_MANUAL_SPEED
        ) * GearMachineRules.FLICK_TRANSFER
        state.body.omega = (state.body.omega + added).coerceIn(
            -GearMachineRules.MAX_MANUAL_SPEED,
            GearMachineRules.MAX_MANUAL_SPEED
        )
        state.body.wake()
    }

    fun stopAll() {
        cancelCharge()
        for (gear in gears) {
            gear.body.vx = 0f
            gear.body.vy = 0f
            gear.body.omega = 0f
            gear.body.wake()
            gear.drive?.reset()
        }
        for (mesh in meshes) mesh.joint.reset()
        for (transmission in transmissions) transmission.joint.reset()
    }

    fun gearAt(x: Float, y: Float, layer: Int? = null): GearState? = gears.asReversed().firstOrNull {
        (layer == null || it.wheel.layer == layer) &&
            hypot(x - it.body.x, y - it.body.y) <= it.wheel.outerRadius
    }

    fun bounds(): FloatArray {
        if (gears.isEmpty()) return floatArrayOf(-5f, 5f, 0f, 8f)
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = 0f; var maxY = -Float.MAX_VALUE
        for (gear in gears) {
            val r = gear.wheel.outerRadius
            minX = minOf(minX, gear.wheel.x - r)
            maxX = maxOf(maxX, gear.wheel.x + r)
            minY = minOf(minY, gear.wheel.y - r)
            maxY = maxOf(maxY, gear.wheel.y + r)
            // Une machine motrice se tient a gauche de son rouet et le depasse de
            // beaucoup : sans elle dans le cadre, un moulin de seize metres sortirait
            // de l'image des qu'on le pose.
            val motor = gear.wheel.motor ?: continue
            val cx = gear.wheel.x - GearMotorRules.offsetX(motor, r)
            val hub = GearMotorRules.hubHeight(motor, gear.wheel.y)
            minX = minOf(minX, cx - motor.span)
            maxX = maxOf(maxX, cx + motor.span)
            // La machine part du sol et monte jusqu'au bout de ses ailes.
            minY = minOf(minY, 0f)
            maxY = maxOf(maxY, hub + motor.span)
        }
        return floatArrayOf(minX, maxX, minY, maxY)
    }

    companion object {
        /**
         * La graine du site que l'atelier montre par defaut.
         *
         * Un atelier vide serait une dalle grise : on y pose donc un village des
         * l'ouverture, comme le trebuchet en pose un. La graine est fixe pour que le
         * decor ne change pas sous les yeux du joueur d'une ouverture a l'autre.
         */
        const val DEFAULT_SITE_SEED = 7L

        /**
         * En dessous de quoi un boulet est considéré comme arrêté, en m/s, et pendant
         * combien de temps il doit l'être pour que le tir se termine.
         *
         * Six dixièmes de seconde : assez long pour qu'un tir presque vertical ne se
         * termine pas à son apogée, assez court pour qu'un boulet couché dans les ruines
         * ne fasse pas attendre.
         */
        const val STALL_SPEED = 0.5f
        const val STALL_TIME = 0.6f

        private const val MAX_PROJECTILE_SPEED = 1_500f

        /** Un point de traînée toutes les vingt millisecondes, comme au trébuchet. */
        private const val TRAIL_INTERVAL = 0.02f
        private const val MAX_TRAIL_FLOATS = 6_000

        /** En dessous, le boulet est considéré comme posé pour de bon. */
        private const val REST_SPEED = 0.30f

        /** Immobilité **tenue** : un rebond passe par zéro sans être arrêté. */
        private const val REST_CALM = 0.35f

        /** Au-delà, on arrête d'attendre : le boulet roule, on a tout vu. */
        private const val MAX_ROLL = 4f
    }
}
