package com.Atom2Universe.app.games.trebuchet.gears

import com.Atom2Universe.app.games.trebuchet.TrebuchetCategory

import com.Atom2Universe.app.games.trebuchet.MachinePreset
import com.Atom2Universe.app.games.physics.MachineMaterial
import com.Atom2Universe.app.games.physics.MachineMaterials
import com.Atom2Universe.app.games.physics.PhysicsConstants
import kotlin.math.hypot

enum class GearWheelKind { GEAR, FLYWHEEL }

enum class GearLinkKind { BELT_OPEN, BELT_CROSSED, CHAIN_FREEWHEEL, SHAFT_CLUTCH }

/**
 * Ce qui fait tourner la machine.
 *
 * Les trois moteurs ne sont pas trois décors : ils tombent à trois endroits
 * différents du couple et de la vitesse, et c'est **ça** que le train d'engrenages
 * a pour métier de rattraper. Le manège arrache tout mais avance au pas, l'aile de
 * moulin tourne vite pour presque rien, la roue à eau donne les deux mais demande
 * une grande roue.
 */
enum class GearMotorKind { NONE, CAROUSEL, WINDMILL, WATERWHEEL }

data class GearMotorConfig(
    var kind: GearMotorKind = GearMotorKind.CAROUSEL,
    /** Unités motrices attelées : bêtes, ailes, ou vannes d'eau ouvertes. */
    var units: Int = 4,
    /** Le sens entraîné : +1 antihoraire, -1 horaire. */
    var direction: Int = -1,
    /**
     * Le rayon de la machine motrice elle-même, en mètres.
     *
     * **C'est elle qui décide de tout, pas l'engrenage qu'elle entraîne.** Une aile de
     * moulin balaie une surface qui grandit comme le carré de son envergure, une bête
     * tire au bout d'un bras dont la longueur fait le couple, une roue à eau reçoit une
     * chute qui vaut son diamètre. L'engrenage monté sur son arbre, lui, tourne à la
     * même vitesse quelle que soit sa denture : il ne sert qu'à rejoindre le train.
     */
    var span: Float = GearMotorRules.DEFAULT_SPAN
) {
    fun copyMotor() = GearMotorConfig(kind, units, direction, span)
}

/**
 * Le dimensionnement des trois moteurs, en unités SI.
 *
 * Chaque moteur se résume à deux nombres — un couple maximal et une vitesse libre —
 * et leur produit est sa puissance. Les valeurs sont celles des machines réelles :
 * un cheval de trait vaut 750 W parce que c'est la définition du cheval-vapeur, un
 * moulin de huit mètres fait une douzaine de kilowatts, une roue à eau de village
 * quelques kilowatts. Rien n'est réglé « au feeling » : c'est ce qui garantit que
 * les trois restent comparables entre eux quand on change une taille de roue.
 */
object GearMotorRules {
    const val MIN_UNITS = 1
    const val MAX_UNITS = 8

    /**
     * Le vent de l'atelier, en m/s. **Il est constant, et c'est un choix de jeu** :
     * la même machine chargée deux fois doit rendre deux fois le même tir, sinon la
     * boucle « je règle, j'observe, je corrige » ne tient plus. C'est la règle que le
     * trébuchet s'est déjà donnée pour le vent de ses sites.
     */
    const val WIND_SPEED = 7f

    /** Ce qu'une bête attelée tire, en newtons, et à quelle allure elle marche. */
    private const val ANIMAL_PULL = 700f
    private const val ANIMAL_WALK = 1.0f

    /** Rapport de vitesse périphérique d'une aile de moulin, et densité de l'air. */
    private const val TIP_SPEED_RATIO = 2.5f

    /** Débit d'une vanne ouverte (m³/s), rendement de la roue, vitesse de jante visée. */
    private const val GATE_FLOW = 0.05f
    private const val WATER_EFFICIENCY = 0.7f
    private const val WATER_RIM_SPEED = 1.5f

    /**
     * Les tailles possibles d'une machine motrice, en mètres de rayon, et celles qu'on
     * pose par défaut : un moulin de seize mètres d'envergure, une roue de cage de six,
     * une roue à eau de sept. Ce sont les dimensions des machines réelles.
     */
    const val MIN_SPAN = 1f
    const val MAX_SPAN = 12f
    const val DEFAULT_SPAN = 4f

    fun defaultSpan(kind: GearMotorKind): Float = when (kind) {
        GearMotorKind.NONE -> DEFAULT_SPAN
        GearMotorKind.CAROUSEL -> 3f
        GearMotorKind.WINDMILL -> 5f
        GearMotorKind.WATERWHEEL -> 3.5f
    }

    /**
     * De combien la machine se tient à gauche de l'engrenage qu'elle entraîne.
     *
     * Les deux sont sur le **même arbre** et tournent donc ensemble ; l'engrenage est
     * seulement déporté sur le côté, hors de la carcasse, pour qu'on puisse venir s'y
     * engrener. C'est exactement la disposition d'un moulin : les ailes dehors, le
     * rouet à l'autre bout de l'arbre.
     */
    fun offsetX(motor: GearMotorConfig, gearOuterRadius: Float): Float =
        motor.span + gearOuterRadius + CLEARANCE

    /** L'air qu'on laisse entre la carcasse de la machine et la denture. */
    const val CLEARANCE = 0.8f

    /**
     * La hauteur de l'axe d'une machine motrice, en mètres au-dessus du sol.
     *
     * **Elle ne se lit pas sur l'engrenage entraîné.** Une aile de huit mètres plantée
     * sur un axe à quatre mètres balaierait quatre mètres sous terre, et le moulin
     * aurait des ailes deux fois plus longues que lui — ce qui est exactement ce qui
     * arrivait quand l'axe suivait la roue. La machine se dresse donc à **sa** hauteur,
     * celle qu'il faut pour que rien ne passe sous le sol, et c'est un arbre vertical
     * qui descend chercher le rouet à l'intérieur de la tour. Un vrai moulin ne fait
     * rien d'autre : l'arbre des ailes est en haut, le rouet est en bas, et le grand
     * arbre debout relie les deux.
     *
     * Elle ne descend jamais en dessous de la roue non plus : un rouet posé haut fait
     * une machine haute, et la descente reste toujours une descente.
     */
    fun hubHeight(motor: GearMotorConfig, gearY: Float): Float {
        val span = motor.span.coerceIn(MIN_SPAN, MAX_SPAN)
        val minimum = when (motor.kind) {
            GearMotorKind.NONE -> 0f
            // Une tour de moulin est à peu près aussi haute que ses ailes sont longues,
            // et les bouts frôlent le sol sans le toucher.
            GearMotorKind.WINDMILL -> maxOf(span * 1.15f, span + GROUND_CLEARANCE)
            // Une roue de cage se pose juste au-dessus du sol, sur sa charpente.
            GearMotorKind.CAROUSEL -> span + GROUND_CLEARANCE * 0.5f
            // Une roue à eau plonge au ras du bief de fuite : c'est sa place.
            GearMotorKind.WATERWHEEL -> span + GROUND_CLEARANCE * 0.2f
        }
        return maxOf(gearY, minimum)
    }

    /** Ce qui doit rester entre le bas d'une machine et la terre. */
    const val GROUND_CLEARANCE = 0.6f

    /** La vitesse que le moteur ne dépasse pas, quoi qu'on lui accroche derrière. */
    fun freeOmega(motor: GearMotorConfig): Float {
        val r = motor.span.coerceIn(MIN_SPAN, MAX_SPAN)
        return when (motor.kind) {
            GearMotorKind.NONE -> 0f
            GearMotorKind.CAROUSEL -> ANIMAL_WALK / r
            GearMotorKind.WINDMILL -> TIP_SPEED_RATIO * WIND_SPEED / r
            GearMotorKind.WATERWHEEL -> WATER_RIM_SPEED / r
        }
    }

    /** La puissance mécanique disponible, en watts. */
    fun power(motor: GearMotorConfig): Float {
        val r = motor.span.coerceIn(MIN_SPAN, MAX_SPAN)
        val n = motor.units.coerceIn(MIN_UNITS, MAX_UNITS).toFloat()
        return when (motor.kind) {
            GearMotorKind.NONE -> 0f
            // Chaque bête donne son cheval-vapeur, indépendamment du bras qu'elle pousse.
            GearMotorKind.CAROUSEL -> n * ANIMAL_PULL * ANIMAL_WALK
            // Loi de Betz rabotée : la voilure décide du coefficient, la surface du reste.
            GearMotorKind.WINDMILL -> {
                val area = Math.PI.toFloat() * r * r
                val cp = (0.06f * n).coerceAtMost(0.35f)
                0.5f * PhysicsConstants.AIR_DENSITY * area *
                    WIND_SPEED * WIND_SPEED * WIND_SPEED * cp
            }
            // Roue par-dessus : la chute utile vaut le diamètre de la roue.
            GearMotorKind.WATERWHEEL -> PhysicsConstants.WATER_DENSITY *
                PhysicsConstants.STANDARD_GRAVITY * (GATE_FLOW * n) * (2f * r) * WATER_EFFICIENCY
        }
    }

    /**
     * Le couple maximal, en N·m.
     *
     * Il se déduit de la puissance et de la vitesse libre : un moteur qui donne `P`
     * watts en tournant à `ω` développe `P/ω`. Le manège fait exception parce que
     * c'est le **couple** qui est sa donnée première — une bête tire tant de newtons
     * au bout d'un bras de tant de mètres — et sa puissance qui en découle.
     */
    fun maxTorque(motor: GearMotorConfig): Float {
        val r = motor.span.coerceIn(MIN_SPAN, MAX_SPAN)
        val n = motor.units.coerceIn(MIN_UNITS, MAX_UNITS).toFloat()
        if (motor.kind == GearMotorKind.CAROUSEL) return n * ANIMAL_PULL * r
        val omega = freeOmega(motor)
        if (omega <= 1e-6f) return 0f
        return power(motor) / omega
    }
}

data class GearLinkConfig(
    val firstId: Int,
    val secondId: Int,
    val kind: GearLinkKind,
    /** Sens de pédalage autorisé sur [firstId] : +1 antihoraire, -1 horaire. */
    val inputDirection: Int = 1
) {
    fun copyLink() = copy(inputDirection = if (inputDirection < 0) -1 else 1)
}

enum class GearWheelMaterial(val physics: MachineMaterial, val axleFriction: Float) {
    WOOD(MachineMaterials.WOOD, 0.020f),
    ALUMINUM(MachineMaterials.ALUMINUM, 0.012f),
    STEEL(MachineMaterials.STEEL, 0.016f),
    TITANIUM(MachineMaterials.TITANIUM, 0.010f);

    fun next(): GearWheelMaterial = entries[(ordinal + 1) % entries.size]
}

data class GearWheelConfig(
    val id: Int,
    var x: Float,
    var y: Float,
    var teeth: Int,
    var layer: Int = 0,
    var kind: GearWheelKind = GearWheelKind.GEAR,
    var material: GearWheelMaterial = GearWheelMaterial.WOOD,
    /** Phase visuelle de la denture, en radians. */
    var angle: Float = 0f,
    /**
     * L'elevation du tir de ce volant, en degres.
     *
     * Un volant **porte toujours son bras de lancement** : c'est ce qui en fait autre
     * chose qu'une masse qui tourne. Le boulet part de la jante, tangentiellement,
     * et cet angle-la est la direction qu'il prend -- pas la position du bras, qui
     * s'en deduit et depend du sens de rotation.
     */
    var launchAngle: Float = 35f,
    /**
     * Le moteur monté sur cette roue, s'il y en a un.
     *
     * Un moteur n'est pas une pièce de plus à poser : c'est une roue **équipée**.
     * Elle garde donc sa denture, ses couches, son aimantation et ses liaisons —
     * rien de l'atelier n'a besoin d'apprendre un nouveau geste, et le moteur
     * entraîne le train par les dents qu'il avait déjà.
     */
    var motor: GearMotorConfig? = null
) {
    val pitchRadius: Float get() = teeth * GearMachineRules.MODULE / 2f
    val outerRadius: Float get() = pitchRadius + GearMachineRules.MODULE

    /**
     * La gorge de lancement : le chemin creuse dans la jante ou le boulet est loge.
     *
     * Le boulet n'est pas au bout d'une perche, il est **dans** le mecanisme. La gorge
     * le retient contre la force centrifuge tout le temps que la roue prend de la
     * vitesse, et le lache par son encoche quand elle passe au point de largage.
     */
    val grooveWidth: Float get() = maxOf(0.30f, outerRadius * 0.16f)

    /** Le bord exterieur de la gorge, juste sous la denture. */
    val grooveOuterRadius: Float get() = outerRadius - GearMachineRules.MODULE * 0.5f

    /** Le rayon ou court le boulet : le milieu de la gorge. */
    val launchRadius: Float
        get() = (grooveOuterRadius - grooveWidth / 2f).coerceAtLeast(0.15f)

    fun copyWheel() =
        GearWheelConfig(
            id, x, y, teeth, layer, kind, material, angle, launchAngle, motor?.copyMotor()
        )
}

object GearMachineRules {
    const val MODULE = 0.12f
    const val MIN_TEETH = 8
    const val MAX_TEETH = 200
    const val MAX_GEARS = 64
    const val MAX_LINKS = 96
    const val MIN_COORD = -10_000f
    const val MAX_COORD = 10_000f
    const val MIN_LAYER = -32
    const val MAX_LAYER = 32
    /** Portée courte de l'aimant : assez pour corriger un lâcher, pas pour attirer de loin. */
    const val MAGNET_ENGAGE_MODULES = 1.05f
    /** Hystérésis : une roue déjà prise se décolle dès un mouvement volontaire. */
    const val MAGNET_RELEASE_MODULES = 2.2f
    const val MAX_MANUAL_SPEED = 120f
    const val FLICK_TRANSFER = 0.55f
    /**
     * La denture du volant lanceur.
     *
     * Il n'y en a qu'un, il est planté sur le pas de tir, et il est **grand** : c'est
     * la jante qui envoie, et sa vitesse périphérique est tout ce qui décide de la
     * portée.
     */
    const val FLYWHEEL_TEETH = 96

    /**
     * Le pas de tir : l'abscisse où le volant est planté, et l'air sous sa jante.
     *
     * **Il ne se déplace pas.** Le volant n'est pas une pièce parmi d'autres : c'est le
     * point de départ du boulet et l'origine de la mesure — le mannequin se dresse à
     * soixante mètres *de lui*. Une machine dont le point de tir se promène ne se
     * compare plus d'un essai à l'autre. Seule sa taille bouge son centre, parce qu'une
     * roue plus grande repose plus haut sur le même socle.
     */
    const val LAUNCHER_X = 0f
    const val LAUNCHER_CLEARANCE = 0.35f

    /** La hauteur d'axe du volant : il repose sur son socle, il ne flotte pas. */
    fun launcherY(wheel: GearWheelConfig): Float = wheel.outerRadius + LAUNCHER_CLEARANCE

    /**
     * La denture du rouet posé sur l'arbre d'une machine motrice.
     *
     * Ce n'est plus lui qui fait la puissance — la machine a son propre gabarit — mais
     * c'est lui qui **multiplie** : gros contre un petit pignon, il donne un premier
     * étage franc. Quarante-huit dents font six mètres de diamètre, ce qu'un rouet de
     * moulin fait vraiment, et la moitié de l'envergure des ailes qui l'entraînent.
     */
    const val MOTOR_TEETH = 96

    /**
     * L'épaisseur et la largeur des jantes, en mètres.
     *
     * **Une roue est une jante sur des rayons, jamais un disque plein**, et ce n'est
     * pas un détail de dessin : la masse d'un disque grandit comme le carré du rayon,
     * celle d'une jante seulement comme le rayon. Un volant de trois mètres taillé
     * dans un disque d'acier de trente-cinq centimètres — ce que l'atelier fabriquait
     * — pèse **cinquante-six tonnes** et demande quatre-vingts mégajoules pour
     * envoyer un boulet à soixante mètres par seconde. Aucun moteur réel ne remplit
     * ça : un moulin à vent y passerait deux heures. La même roue en jante pèse une
     * tonne et demande huit cents kilojoules, soit une minute de roue à eau.
     *
     * La jante d'un volant est franche, celle d'un engrenage se contente de porter
     * ses dents : c'est ce qui fait qu'à diamètre égal un volant emmagasine toujours
     * plus qu'un engrenage, ce qui est bien toute sa raison d'être.
     */
    const val FLYWHEEL_RIM = 0.10f
    const val FLYWHEEL_WIDTH = 0.10f
    const val GEAR_RIM = 0.06f
    const val GEAR_WIDTH = 0.05f

    /** L'épaisseur de jante d'une roue, selon ce qu'elle est. */
    fun rimThickness(kind: GearWheelKind): Float =
        if (kind == GearWheelKind.FLYWHEEL) FLYWHEEL_RIM else GEAR_RIM

    /** La largeur de jante d'une roue, selon ce qu'elle est. */
    fun rimWidth(kind: GearWheelKind): Float =
        if (kind == GearWheelKind.FLYWHEEL) FLYWHEEL_WIDTH else GEAR_WIDTH

    /**
     * Le rayon intérieur de la jante : là où commencent les rayons, et le vide.
     *
     * Il ne descend jamais sous la moitié du rayon extérieur — une toute petite roue
     * n'a pas la place d'être creuse, et une jante plus épaisse que large ne
     * ressemblerait plus à rien.
     */
    fun rimInnerRadius(kind: GearWheelKind, outerRadius: Float): Float =
        (outerRadius - rimThickness(kind)).coerceIn(outerRadius * 0.5f, outerRadius * 0.97f)

    /**
     * Le couple de freinage d'un frein d'axe, en N·m.
     *
     * Il est proportionnel à l'inertie freinée pour que le frein se comporte pareil
     * sur une petite roue et sur un gros volant : il retire toujours [BRAKE_RATE]
     * radians par seconde et par seconde, donc une roue lancée s'arrête en quelques
     * secondes quelle que soit sa taille.
     */
    const val BRAKE_RATE = 8f

    /**
     * Les bornes d'une charge, en secondes.
     *
     * Charger n'invente rien : c'est la **même** simulation, jouée en accéléré. Une
     * machine laissée tourner longtemps finit de toute façon sur un plateau, là où le
     * frottement des paliers mange exactement ce que le moteur fournit ; la durée ne
     * fait que décider si l'on s'arrête avant.
     */
    const val DEFAULT_CHARGE = 120f
    const val MIN_CHARGE = 5f
    const val MAX_CHARGE = 1_200f

    /** En dessous, la jante ne va pas assez vite pour lancer quoi que ce soit. */
    const val MIN_LAUNCH_OMEGA = 0.25f

    /** Le sens de rotation par defaut d'un volant a l'arret : horaire. */
    const val DEFAULT_SPIN = -1f
    const val LAUNCH_EFFICIENCY = 0.72f
    const val DEFAULT_PROJECTILE_MASS = 4f
    const val MIN_PROJECTILE_MASS = 0.1f
    const val MAX_PROJECTILE_MASS = 2_000f
    val SIZES = intArrayOf(12, 24, 48, 96)

    /**
     * Le rayon d'un boulet de cette masse, en metres.
     *
     * Il sert au tir **et** au dessin du boulet en attente dans sa gorge : les deux
     * doivent donner exactement la meme bille, sinon celle qu'on voit charger n'est
     * pas celle qui part.
     */
    fun projectileRadius(mass: Float): Float =
        (0.075f * Math.cbrt(mass.toDouble())).toFloat().coerceIn(0.06f, 0.55f)

    /**
     * Qui touche quoi. Les roues ne se heurtent jamais — leurs dents sont logiques,
     * et deux roues d'un même train se recouvrent par construction. Le boulet, lui,
     * ne connaît que le sol : le laisser cogner la machine qui vient de le lancer
     * finirait toujours mal.
     */
    /**
     * Les categories de collision, **empruntees au trebuchet**.
     *
     * L'atelier tire desormais sur le meme genre de site que le champ de tir : relief,
     * batiments qui s'effondrent, gravats. Or `TargetField` estampille lui-meme ses
     * pierres avec [TrebuchetCategory] a chaque fois qu'une d'elles casse, et re-marquer
     * cent corps par image pour les traduire dans un autre jeu de bits serait a la fois
     * couteux et fragile. On adopte donc directement le sien.
     *
     * Les roues gardent un bit **hors** de ce jeu : elles n'entrent dans le masque de
     * personne, ce qui garantit qu'aucune pierre, aucun gravat et aucun boulet ne vient
     * cogner le mecanisme. De toute facon elles ne collisionnent avec rien
     * (`collidesWith = 0`) : un engrenage engrene par le calcul, pas par le contact.
     */
    const val CATEGORY_WHEEL = 64
    const val CATEGORY_GROUND = TrebuchetCategory.GROUND
    const val CATEGORY_SHOT = TrebuchetCategory.BALL

    /**
     * La dalle de sol : assez large pour porter le plus long des tirs, et **épaisse**,
     * parce que le moteur taille ses sous-pas sur l'épaisseur de ce qu'un corps rapide
     * peut atteindre. Une dalle mince hacherait chaque image en trente-deux sous-pas
     * dès qu'un boulet file au ras du sol.
     */
    const val GROUND_HALF_WIDTH = 20_000f
    const val GROUND_DEPTH = 30f

    /** Le mannequin planté devant le lanceur : le seul but de l'atelier, pour l'instant. */
    const val TARGET_DISTANCE = 60f
    const val TARGET_HEIGHT = 1.72f
    const val TARGET_RADIUS = 0.38f

    /** L'elevation d'un tir : de l'horizontale au tir en cloche. */
    const val MIN_LAUNCH_DEG = 0f
    const val MAX_LAUNCH_DEG = 85f

    /**
     * Le trajet d'une image passe-t-il dans le mannequin planté en [targetX] ?
     *
     * On mesure la distance du **segment** parcouru au centre de la cible, et non
     * celle du point d'arrivée : à trois cents mètres par seconde, un boulet saute
     * plus de deux mètres par sous-pas et traverserait une cible de quarante
     * centimètres sans jamais être relevé dedans.
     */
    fun segmentHitsTarget(
        targetX: Float, ballRadius: Float,
        x0: Float, y0: Float, x1: Float, y1: Float
    ): Boolean {
        val cy = TARGET_HEIGHT
        val dx = x1 - x0
        val dy = y1 - y0
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq <= 1e-12f) {
            0f
        } else {
            (((targetX - x0) * dx + (cy - y0) * dy) / lengthSq).coerceIn(0f, 1f)
        }
        val nearestX = x0 + dx * t
        val nearestY = y0 + dy * t
        return hypot(targetX - nearestX, cy - nearestY) <= TARGET_RADIUS + ballRadius
    }
}

/** Configuration sauvegardable de l'atelier d'engrenages. */
class GearMachineConfig(
    val wheels: MutableList<GearWheelConfig> = defaultWheels().toMutableList(),
    var launcherWheelId: Int? = null,
    var projectileMass: Float = GearMachineRules.DEFAULT_PROJECTILE_MASS,
    val links: MutableList<GearLinkConfig> = mutableListOf(),
    /**
     * Combien de temps on laisse les moteurs travailler quand on lance une charge.
     *
     * Elle appartient à l'atelier et non à une roue — on charge la machine entière,
     * pas un moteur — mais elle se règle depuis la bulle d'un moteur, là où on la
     * regarde. C'est exactement le partage déjà retenu pour la masse du boulet.
     */
    var chargeSeconds: Float = GearMachineRules.DEFAULT_CHARGE
) {
    var nextId: Int = (wheels.maxOfOrNull { it.id } ?: 0) + 1

    fun deepCopy(): GearMachineConfig = GearMachineConfig(
        wheels.map { it.copyWheel() }.toMutableList(), launcherWheelId,
        projectileMass, links.map { it.copyLink() }.toMutableList(), chargeSeconds
    ).also {
        it.nextId = nextId
    }

    /** Les roues qui portent un moteur en état de tourner. */
    fun motorWheels(): List<GearWheelConfig> =
        wheels.filter { it.motor?.kind?.takeIf { k -> k != GearMotorKind.NONE } != null }

    /** Le volant qui tire, s'il y en a un. */
    fun launcher(): GearWheelConfig? = wheels.firstOrNull { it.id == launcherWheelId }

    fun clamp() {
        val seen = HashSet<Int>()
        val iterator = wheels.iterator()
        while (iterator.hasNext()) {
            val wheel = iterator.next()
            if (!wheel.x.isFinite() || !wheel.y.isFinite() || wheel.id < 0 || !seen.add(wheel.id)) {
                iterator.remove()
                continue
            }
            wheel.x = wheel.x.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            wheel.y = wheel.y.coerceIn(GearMachineRules.MIN_COORD, GearMachineRules.MAX_COORD)
            wheel.teeth = wheel.teeth.coerceIn(GearMachineRules.MIN_TEETH, GearMachineRules.MAX_TEETH)
            wheel.layer = wheel.layer.coerceIn(GearMachineRules.MIN_LAYER, GearMachineRules.MAX_LAYER)
            wheel.angle = wheel.angle.takeIf { it.isFinite() } ?: 0f
            wheel.launchAngle = wheel.launchAngle.takeIf { it.isFinite() }
                ?.coerceIn(GearMachineRules.MIN_LAUNCH_DEG, GearMachineRules.MAX_LAUNCH_DEG) ?: 35f
            // Un volant est ce qui **garde** l'élan ; l'atteler ferait de la pièce de
            // stockage une pièce d'entraînement, et le train n'aurait plus rien à faire.
            val motor = wheel.motor
            if (motor == null || motor.kind == GearMotorKind.NONE ||
                wheel.kind == GearWheelKind.FLYWHEEL
            ) {
                wheel.motor = null
            } else {
                motor.units = motor.units.coerceIn(GearMotorRules.MIN_UNITS, GearMotorRules.MAX_UNITS)
                motor.direction = if (motor.direction < 0) -1 else 1
                motor.span = motor.span.takeIf { it.isFinite() }
                    ?.coerceIn(GearMotorRules.MIN_SPAN, GearMotorRules.MAX_SPAN)
                    ?: GearMotorRules.defaultSpan(motor.kind)
            }
        }
        while (wheels.size > GearMachineRules.MAX_GEARS) wheels.removeLast()
        val ids = wheels.mapTo(HashSet()) { it.id }
        val wheelById = wheels.associateBy { it.id }
        val linkPairs = HashSet<String>()
        links.removeAll { link ->
            val key = "${minOf(link.firstId, link.secondId)}:${maxOf(link.firstId, link.secondId)}"
            val firstLayer = wheelById[link.firstId]?.layer
            val secondLayer = wheelById[link.secondId]?.layer
            val invalidLayers = if (link.kind == GearLinkKind.SHAFT_CLUTCH) {
                firstLayer != null && firstLayer == secondLayer
            } else {
                firstLayer != null && secondLayer != null && firstLayer != secondLayer
            }
            link.firstId == link.secondId || link.firstId !in ids || link.secondId !in ids ||
                invalidLayers || !linkPairs.add(key)
        }
        while (links.size > GearMachineRules.MAX_LINKS) links.removeLast()
        enforceSinglePieces()
        projectileMass = projectileMass.takeIf { it.isFinite() }
            ?.coerceIn(GearMachineRules.MIN_PROJECTILE_MASS, GearMachineRules.MAX_PROJECTILE_MASS)
            ?: GearMachineRules.DEFAULT_PROJECTILE_MASS
        chargeSeconds = chargeSeconds.takeIf { it.isFinite() }
            ?.coerceIn(GearMachineRules.MIN_CHARGE, GearMachineRules.MAX_CHARGE)
            ?: GearMachineRules.DEFAULT_CHARGE
        nextId = maxOf(nextId, (wheels.maxOfOrNull { it.id } ?: 0) + 1)
    }

    /**
     * Les deux pièces uniques de l'atelier : un moteur, un lanceur.
     *
     * **La machine a deux bouts fixes et un milieu libre.** Le moteur produit, le
     * volant lance, et tout le jeu est dans le train qu'on bâtit entre les deux. Deux
     * moteurs ne feraient qu'additionner des watts sans rien apprendre, et un second
     * volant serait une masse qui tourne pour rien puisqu'un seul tire.
     *
     * Ce qui manque est **créé**, ce qui est en trop est **rétrogradé** plutôt que
     * jeté : une machine relue d'une sauvegarde ancienne, ou vidée par le joueur, doit
     * toujours ressortir d'ici jouable.
     */
    private fun enforceSinglePieces() {
        // Le lanceur : celui qui était désigné, sinon le premier volant venu.
        val launcher = wheels.firstOrNull { it.id == launcherWheelId && it.kind == GearWheelKind.FLYWHEEL }
            ?: wheels.firstOrNull { it.kind == GearWheelKind.FLYWHEEL }
        launcherWheelId = launcher?.id
        for (wheel in wheels) {
            if (wheel.kind == GearWheelKind.FLYWHEEL && wheel.id != launcherWheelId) {
                wheel.kind = GearWheelKind.GEAR
            }
        }
        launcher?.let {
            it.x = GearMachineRules.LAUNCHER_X
            it.y = GearMachineRules.launcherY(it)
            it.motor = null
        }

        // Le moteur : un seul.
        var seen = false
        for (wheel in wheels) {
            if (wheel.motor == null) continue
            if (seen) wheel.motor = null else seen = true
        }
    }

    /**
     * Complète une machine à laquelle il manque une de ses deux pièces uniques.
     *
     * **La complétude n'est pas la cohérence, et les deux ne se vérifient pas au même
     * moment.** [clamp] tourne à chaque reconstruction, donc plusieurs fois par geste :
     * elle ne fait qu'écarter ce qui est en trop et remettre le lanceur sur son socle.
     * Créer des pièces là ferait pousser un moulin dans toute machine qu'on assemble
     * morceau par morceau. Ceci ne tourne donc qu'à l'ouverture d'une machine — une
     * sauvegarde d'avant les moteurs, par exemple — là où il est acquis qu'on tient
     * une machine finie et jouable.
     */
    fun ensureCorePieces() {
        if (wheels.none { it.kind == GearWheelKind.FLYWHEEL } &&
            wheels.size < GearMachineRules.MAX_GEARS
        ) {
            val launcher = GearWheelConfig(
                nextId++, GearMachineRules.LAUNCHER_X, 0f,
                GearMachineRules.FLYWHEEL_TEETH, kind = GearWheelKind.FLYWHEEL
            )
            launcher.y = GearMachineRules.launcherY(launcher)
            wheels += launcher
        }
        if (wheels.none { it.motor != null } && wheels.size < GearMachineRules.MAX_GEARS) {
            val leftmost = wheels.minByOrNull { it.x - it.outerRadius }
            val motorPitch = GearMachineRules.MOTOR_TEETH * GearMachineRules.MODULE / 2f
            wheels += GearWheelConfig(
                nextId++,
                leftmost?.let { it.x - it.pitchRadius - motorPitch } ?: -12f,
                leftmost?.y ?: 6f,
                GearMachineRules.MOTOR_TEETH,
                layer = leftmost?.layer ?: 0,
                motor = GearMotorConfig(
                    GearMotorKind.WINDMILL,
                    span = GearMotorRules.defaultSpan(GearMotorKind.WINDMILL)
                )
            )
        }
        clamp()
    }

    /** Vrai pour les deux pièces qu'on ne déplace ni ne supprime : le volant lanceur. */
    fun isPinned(id: Int): Boolean = id == launcherWheelId

    companion object {
        /**
         * La machine de départ : un moulin à gauche, un renvoi au milieu, le volant sur
         * son pas de tir à droite.
         *
         * Les trois roues sont sur une **même ligne d'axes**, à la hauteur qu'impose le
         * volant posé sur son socle : c'est la disposition la plus lisible, et elle
         * montre d'emblée les trois postes du jeu. Le renvoi de 24 dents ne change
         * aucun rapport — dans un train simple seules la première et la dernière roue
         * comptent — il ne fait qu'inverser le sens et couvrir la distance. Découvrir
         * ça, et bâtir un vrai étage multiplicateur, c'est tout l'atelier.
         */
        fun defaultWheels(): List<GearWheelConfig> {
            val launcher = GearWheelConfig(
                3, GearMachineRules.LAUNCHER_X, 0f,
                GearMachineRules.FLYWHEEL_TEETH, kind = GearWheelKind.FLYWHEEL
            )
            launcher.y = GearMachineRules.launcherY(launcher)
            val idlerTeeth = 24
            val idler = GearWheelConfig(
                2,
                launcher.x - launcher.pitchRadius - idlerTeeth * GearMachineRules.MODULE / 2f,
                launcher.y, idlerTeeth
            )
            val motor = GearWheelConfig(
                1,
                idler.x - idler.pitchRadius - GearMachineRules.MOTOR_TEETH * GearMachineRules.MODULE / 2f,
                launcher.y, GearMachineRules.MOTOR_TEETH,
                motor = GearMotorConfig(
                    GearMotorKind.WINDMILL,
                    span = GearMotorRules.defaultSpan(GearMotorKind.WINDMILL)
                )
            )
            return listOf(motor, idler, launcher)
        }
    }
}

class GearMachinePreset(name: String, config: GearMachineConfig) {
    val name: String = MachinePreset.clean(name)
    val config: GearMachineConfig = config.deepCopy()
}

/** Format tolérant et versionné des machines à engrenages. */
object GearMachineLibrary {
    const val MAX_PRESETS = 30
    private const val VERSION = "G6"
    private const val VERSION_G5 = "G5"
    private const val VERSION_G4 = "G4"
    private const val VERSION_G3 = "G3"
    private const val VERSION_G2 = "G2"
    private const val VERSION_G1 = "G1"

    fun encode(list: List<GearMachinePreset>): String = buildString {
        for (preset in list.take(MAX_PRESETS)) {
            append(VERSION).append('\t').append(preset.name)
            // L'angle garde sa place dans l'entete pour que les versions anterieures
            // relisent quelque chose de sense : c'est celui du volant qui tire.
            append('\t').append("L,").append(preset.config.launcherWheelId ?: -1).append(',')
                .append(preset.config.launcher()?.launchAngle ?: 35f).append(',')
                .append(preset.config.projectileMass).append(',')
                .append(preset.config.chargeSeconds)
            for (link in preset.config.links) {
                append('\t').append("T,").append(link.kind.name).append(',')
                    .append(link.firstId).append(',').append(link.secondId).append(',')
                    .append(if (link.inputDirection < 0) -1 else 1)
            }
            for (wheel in preset.config.wheels) {
                append('\t').append(wheel.id).append(',')
                    .append(wheel.x).append(',').append(wheel.y).append(',')
                    .append(wheel.teeth).append(',').append(wheel.layer).append(',')
                    .append(wheel.kind.name).append(',').append(wheel.material.name).append(',')
                    .append(wheel.angle).append(',').append(wheel.launchAngle).append(',')
                    .append(wheel.motor?.kind?.name ?: GearMotorKind.NONE.name).append(',')
                    .append(wheel.motor?.units ?: 0).append(',')
                    .append(wheel.motor?.direction ?: -1).append(',')
                    .append(wheel.motor?.span ?: 0f)
            }
            append('\n')
        }
    }

    fun decode(text: String): List<GearMachinePreset> {
        val out = ArrayList<GearMachinePreset>()
        for (line in text.lineSequence()) {
            if (out.size >= MAX_PRESETS) break
            val fields = line.split('\t')
            val version = fields.getOrNull(0) ?: continue
            if (fields.size < 2 || version !in setOf(
                    VERSION, VERSION_G5, VERSION_G4, VERSION_G3, VERSION_G2, VERSION_G1
                )
            ) continue
            val name = MachinePreset.clean(fields[1])
            if (name.isEmpty()) continue
            val wheels = ArrayList<GearWheelConfig>()
            var launcherId: Int? = null
            var launcherAngle = 35f
            var projectileMass = GearMachineRules.DEFAULT_PROJECTILE_MASS
            var chargeSeconds = GearMachineRules.DEFAULT_CHARGE
            val links = ArrayList<GearLinkConfig>()
            for (field in fields.drop(2)) {
                val value = field.split(',')
                if (value.size in 4..5 && value[0] == "L") {
                    launcherId = value[1].toIntOrNull()?.takeIf { it >= 0 }
                    launcherAngle = value[2].toFloatOrNull() ?: launcherAngle
                    projectileMass = value[3].toFloatOrNull() ?: projectileMass
                    // Avant G6 personne ne chargeait : la durée par défaut fait l'affaire.
                    chargeSeconds = value.getOrNull(4)?.toFloatOrNull() ?: chargeSeconds
                    continue
                }
                if (value.size == 5 && value[0] == "T") {
                    val kind = runCatching { GearLinkKind.valueOf(value[1]) }.getOrNull() ?: continue
                    val first = value[2].toIntOrNull() ?: continue
                    val second = value[3].toIntOrNull() ?: continue
                    val direction = value[4].toIntOrNull() ?: 1
                    links += GearLinkConfig(first, second, kind, direction)
                    continue
                }
                if (value.size !in 5..13 || value.size == 6) continue
                val id = value[0].toIntOrNull() ?: continue
                val x = value[1].toFloatOrNull() ?: continue
                val y = value[2].toFloatOrNull() ?: continue
                val teeth = value[3].toIntOrNull() ?: continue
                val layer = value[4].toIntOrNull() ?: continue
                val kind = value.getOrNull(5)?.let { runCatching { GearWheelKind.valueOf(it) }.getOrNull() }
                    ?: GearWheelKind.GEAR
                val material = value.getOrNull(6)?.let { runCatching { GearWheelMaterial.valueOf(it) }.getOrNull() }
                    ?: GearWheelMaterial.WOOD
                val angle = value.getOrNull(7)?.toFloatOrNull() ?: 0f
                // Avant G5, l'angle de tir etait unique et vivait dans l'entete : il
                // devient celui de chaque volant relu.
                val launch = value.getOrNull(8)?.toFloatOrNull() ?: launcherAngle
                val motorKind = value.getOrNull(9)
                    ?.let { runCatching { GearMotorKind.valueOf(it) }.getOrNull() }
                    ?: GearMotorKind.NONE
                val motor = if (motorKind == GearMotorKind.NONE) null else GearMotorConfig(
                    motorKind,
                    value.getOrNull(10)?.toIntOrNull() ?: 4,
                    value.getOrNull(11)?.toIntOrNull() ?: -1,
                    // Avant que les machines aient leur propre gabarit, il se lisait sur
                    // la roue : on repart de la taille normale du moteur relu.
                    value.getOrNull(12)?.toFloatOrNull()?.takeIf { it > 0f }
                        ?: GearMotorRules.defaultSpan(motorKind)
                )
                wheels += GearWheelConfig(
                    id, x, y, teeth, layer, kind, material, angle, launch, motor
                )
            }
            val config =
                GearMachineConfig(wheels, launcherId, projectileMass, links, chargeSeconds)
            config.clamp()
            if (config.wheels.isNotEmpty()) out += GearMachinePreset(name, config)
        }
        return out
    }

    fun put(list: List<GearMachinePreset>, preset: GearMachinePreset): List<GearMachinePreset> =
        buildList {
            add(preset)
            for (old in list) if (!old.name.equals(preset.name, ignoreCase = true)) add(old)
        }.take(MAX_PRESETS)

    fun remove(list: List<GearMachinePreset>, name: String): List<GearMachinePreset> =
        list.filterNot { it.name.equals(name, ignoreCase = true) }
}
