package com.Atom2Universe.app.games.trebuchet

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * La maçonnerie : les gestes de base, qui appliquent d'eux-mêmes les règles apprises
 * au banc d'essai.
 *
 * Personne au-dessus n'a plus à y penser. Un module demande « un pan de mur de six
 * mètres sur huit », et ce qui sort respecte le joint de maçonnerie, l'appareil à
 * joints décalés, et surtout la profondeur d'empilement — c'est-à-dire qu'il regroupe
 * tout seul les assises en blocs composés dès que le mur monte trop haut pour que le
 * solveur le tienne.
 */
object Masonry {

    /**
     * Nombre maximal d'assises réunies dans un seul corps.
     *
     * Au-delà, le corps devient un monolithe : sa masse, donc ses points de vie,
     * grandissent avec lui, et le boulet ne l'entame plus. Six assises de pierre font
     * déjà quatre tonnes et demie, soit quatre coups au but — c'est le maximum
     * raisonnable avant que le mur ne devienne une falaise.
     */
    private const val MAX_GROUP = 6

    /**
     * Un pan de mur plein, en appareil, de ([left], [bottom]) à ([left] + [width],
     * [bottom] + [height]).
     *
     * **Le regroupement est le cœur de cette fonction.** Un mur de vingt assises monté
     * en vingt corps s'affaisse et s'écroule tout seul : le solveur ne sait pas
     * propager l'effort si loin. Le même mur monté en cinq corps de quatre assises ne
     * bouge pas d'un cheveu, et le joueur voit exactement la même chose — jusqu'au jour
     * où une pierre casse, et elle éclate alors en ses quatre assises. On calcule donc
     * ici combien d'assises mettre dans un corps pour ne jamais dépasser [stackBudget],
     * et on les y met.
     *
     * L'appareil se fait au niveau des **corps** : une rangée de corps sur deux en
     * compte un de moins, donc plus larges, et leurs joints ne retombent jamais sur ceux
     * de la rangée voisine. C'est ce qui empêche un joint vertical de courir sur toute la
     * hauteur, et c'est la seule chose qui tienne un mur latéralement.
     */
    fun wall(
        material: Material,
        left: Float,
        bottom: Float,
        width: Float,
        height: Float,
        stoneWidth: Float = 1.2f,
        stoneHeight: Float = 0.5f,
        role: Role = Role.STRUCTURE,
        stackBudget: Int = TargetRules.COMFORTABLE_STACK,
        bodyBudget: Int = TargetRules.BODY_BUDGET,
        surface: Surface = Surface.AUTO
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 0.05f || height <= 0.05f) return out
        val j = TargetRules.JOINT

        // Le tempérament du jeu décide de la taille des pierres : en arcade, elles font
        // deux fois et demie la pierre réelle, ce qui rend chaque coup lisible et divise
        // par six le nombre de corps.
        val sw = TargetRules.stone(stoneWidth)
        val sh = TargetRules.stone(stoneHeight)
        val courses = max(1, (height / sh).roundToInt())
        val courseH = height / courses
        var cols = max(1, (width / sw).roundToInt())

        // Combien d'assises par corps pour tenir dans le budget de profondeur.
        var group = max(1, ceil(courses / stackBudget.toFloat()).toInt())
        var bodyRows = ceil(courses / group.toFloat()).toInt()

        // Puis, s'il y a trop de corps, on en met davantage dans chacun.
        //
        // L'ordre compte, et il n'est pas arbitraire : **regrouper est gratuit à
        // l'œil**, puisque les assises dessinées gardent leur taille et que seule leur
        // solidarité change. Élargir les pierres, en revanche, se voit tout de suite.
        // On regroupe donc d'abord, jusqu'à ce qu'un corps devienne un monolithe qu'on
        // ne casserait plus — et seulement là, on prend de plus grosses pierres.
        while (cols * bodyRows > bodyBudget && group < MAX_GROUP) {
            group++
            bodyRows = ceil(courses / group.toFloat()).toInt()
        }
        while (cols * bodyRows > bodyBudget && cols > 1) {
            cols--
        }
        // Dernier garde-fou : un corps ne doit jamais être plus de deux fois plus haut
        // que large. Un domino de deux mètres de haut sur soixante centimètres, planté
        // au bord d'une tour, bascule tout seul dans la seconde qui suit le chargement —
        // c'est la façon dont les tours se fêlaient sans que rien ne les touche.
        while (cols > 1 && group * courseH > 2f * (width / cols)) {
            cols--
        }
        val colW = width / cols

        for (r in 0 until bodyRows) {
            val firstCourse = r * group
            val n = minOf(group, courses - firstCourse)
            val y0 = bottom + firstCourse * courseH
            val bodyH = n * courseH
            for ((sx, sw) in bondSpans(left, width, cols, staggered = r % 2 == 1)) {
                if (sw <= 3f * j) continue
                out += Block.compound(
                    material, sx + sw / 2f, y0 + bodyH / 2f,
                    role = role, surface = surface
                ) {
                    for (k in 0 until n) {
                        box(
                            (sw - j) / 2f,
                            (courseH - j) / 2f,
                            0f,
                            (k - (n - 1) / 2f) * courseH
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * Découpe une rangée en corps, décalée une fois sur deux pour faire l'appareil.
     *
     * **Aucun corps n'est plus étroit que les autres.** C'est la leçon de deux versions
     * précédentes, qui décalaient la rangée en la commençant par un demi-corps : dès que
     * le regroupement rendait les corps hauts, ces demi-corps devenaient des dominos de
     * soixante centimètres de large sur deux mètres de haut, plantés au bord d'une tour,
     * et ils basculaient tout seuls dans la seconde qui suivait le chargement. La tour
     * perdait un cinquième de sa masse sans que rien ne l'ait touchée.
     */
    private fun bondSpans(
        left: Float,
        width: Float,
        cols: Int,
        staggered: Boolean
    ): List<Pair<Float, Float>> {
        // Une rangée sur deux compte **un corps de moins**, donc des corps plus larges.
        // Les joints des deux rangées ne se rencontrent alors qu'aux extrémités du mur,
        // ce qui est exactement ce qu'on demande à un appareil — et, contrairement à un
        // décalage d'un demi-corps, ça ne fabrique aucune pièce étroite.
        val n = if (staggered) maxOf(1, cols - 1) else cols
        val w = width / n
        val spans = ArrayList<Pair<Float, Float>>(n)
        for (i in 0 until n) spans += (left + i * w) to w
        return spans
    }

    /**
     * Les merlons d'un chemin de ronde : des pierres dressées séparées par des
     * créneaux vides.
     *
     * Chacun est un corps à part, et c'est délibéré — ils sont ce qu'un tir un peu haut
     * fait tomber en premier, et voir la crête d'un rempart s'égrener est la meilleure
     * façon de dire au joueur qu'il a touché quelque chose sans avoir encore percé.
     */
    fun merlons(
        material: Material,
        left: Float,
        bottom: Float,
        width: Float,
        height: Float = TargetRules.detail(0.9f),
        merlonWidth: Float = 0.8f,
        gapWidth: Float = 0.6f
    ): List<Block> {
        val out = ArrayList<Block>()
        // Un merlon est une pierre d'appareil : il grandit comme une assise. Sa
        // **hauteur**, elle, suit le curseur doux — un merlon aussi haut que large
        // n'est plus un créneau, c'est un second mur posé sur le premier, et il
        // dépasserait à lui seul la ligne de ruine.
        val mw = TargetRules.stone(merlonWidth)
        val gw = TargetRules.stone(gapWidth)
        val pitch = mw + gw
        // On veut commencer et finir par un merlon : il en faut un de plus que de créneaux.
        val count = max(2, ((width + gw) / pitch).roundToInt())
        if (count < 2) return out
        val step = (width - mw) / (count - 1)
        for (i in 0 until count) {
            out += Block.laid(
                material,
                left + i * step,
                bottom,
                mw - TargetRules.JOINT,
                height
            )
        }
        return out
    }

    /**
     * Une rangée de pierres d'appareil découpée en **exactement** [cols] morceaux.
     *
     * C'est le geste que [wall] ne sait pas faire, et il manquait dès qu'un module a
     * besoin d'un nombre précis de pièces : un linteau d'arcade doit avoir une pierre
     * par travée, un gradin de pyramide doit rétrécir avec elle. [wall] décide seule
     * de son découpage à partir d'une taille de pierre, ce qui est exactement ce qu'on
     * veut pour un mur et exactement ce qu'on ne veut pas ici.
     *
     * Une seule assise de haut : c'est un lit de pierres, pas un mur. Qui en veut
     * plusieurs les empile.
     */
    fun band(
        material: Material,
        left: Float,
        bottom: Float,
        width: Float,
        height: Float,
        cols: Int = 1,
        role: Role = Role.STRUCTURE
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 0.05f || height <= 0.05f) return out
        val n = cols.coerceAtLeast(1)
        val w = width / n
        val j = TargetRules.JOINT
        for (i in 0 until n) {
            if (w <= 2f * j) continue
            out += Block.laid(material, left + i * w + j / 2f, bottom, w - j, height, role)
        }
        return out
    }

    /**
     * Une colonne : un fût d'un seul corps, mais **taillé en tambours**.
     *
     * C'est le bloc composé dans son meilleur emploi. Debout, la colonne est
     * parfaitement rigide — elle ne tremble pas, ne se tasse pas, ne coûte qu'un corps
     * au moteur. Le jour où elle prend un boulet, elle ne disparaît pas : elle
     * s'égrène en ses tambours, qui roulent et s'entassent. Une colonne de temple
     * abattue ressemble alors à une colonne de temple abattue, et ça n'a rien coûté.
     */
    fun column(
        material: Material,
        centerX: Float,
        bottom: Float,
        width: Float,
        height: Float,
        drums: Int = 3
    ): Block {
        val n = drums.coerceAtLeast(1)
        val h = height / n
        val j = TargetRules.JOINT
        return Block.compound(material, centerX, bottom + height / 2f) {
            for (k in 0 until n) {
                box((width - j) / 2f, (h - j) / 2f, 0f, (k - (n - 1) / 2f) * h)
            }
        }
    }

    /** Un poteau de charpente, debout. */
    fun post(material: Material, centerX: Float, bottom: Float, width: Float, height: Float): Block =
        Block.laid(material, centerX - width / 2f, bottom, width - TargetRules.JOINT, height)

    /** Une poutre couchée, qui porte l'étage du dessus. */
    fun beam(material: Material, left: Float, bottom: Float, width: Float, height: Float): Block =
        Block.laid(material, left, bottom, width - TargetRules.JOINT, height)

    /**
     * Un toit à deux pentes, **d'un seul tenant**.
     *
     * Deux planches appuyées l'une contre l'autre au faîtage seraient plus jolies sur
     * le papier et impossibles à poser : il faudrait qu'elles se touchent exactement,
     * et un joint de trois millimètres suffit à les faire glisser. Un seul corps portant
     * les deux pentes tient tout seul, se pose sur les murs sans discussion — et le jour
     * où il casse, il casse **en ses deux versants**, ce qui est exactement l'image
     * qu'on voulait.
     */
    fun roof(
        material: Material,
        left: Float,
        bottom: Float,
        width: Float,
        rise: Float,
        thickness: Float = TargetRules.detail(0.18f),
        surface: Surface = when (material) {
            Material.THATCH -> Surface.THATCH
            Material.WOOD -> Surface.SHINGLES
            Material.STONE -> Surface.SLATE
            Material.SANDSTONE -> Surface.TILES
            else -> Surface.AUTO
        },
        visualVariant: Int = 0
    ): Block {
        val halfW = width / 2f
        val slope = hypot(halfW, rise)
        val angle = atan2(rise, halfW)
        return Block.compound(
            material, left + halfW, bottom + rise / 2f,
            surface = surface, silhouette = Silhouette.GABLE_ROOF,
            visualVariant = visualVariant
        ) {
            box(slope / 2f, thickness / 2f, -halfW / 2f, 0f, angle)
            box(slope / 2f, thickness / 2f, halfW / 2f, 0f, -angle)
        }
    }

    /**
     * Un pan de mur à fenêtre : **un seul corps**, plein comme n'importe quel mur —
     * la fenêtre, les volets et les carreaux ne sont qu'une peinture par-dessus
     * ([Decor.WINDOW_SHUTTERS], voir `LandScene.appendDecor`).
     *
     * C'est le contraire du premier essai de ce module, qui perçait un vrai trou en
     * assemblant piédroits, linteau et allège — quatre corps pour un motif que
     * l'œil ne lisait même pas comme une fenêtre. Une fenêtre n'a pas besoin d'être
     * simulée pour se reconnaître ; elle a besoin d'être **dessinée**.
     */
    fun windowedWall(
        material: Material,
        left: Float, bottom: Float, width: Float, height: Float,
        role: Role = Role.STRUCTURE,
        arched: Boolean = false,
        surface: Surface = Surface.AUTO,
        visualVariant: Int = 0
    ): Block = Block.laid(
        material, left, bottom, width, height, role,
        decor = if (arched) Decor.WINDOW_ARCHED else Decor.WINDOW_SHUTTERS,
        surface = surface, visualVariant = visualVariant
    )

    /** Un grand pan de façade à porte ferrée. */
    fun doorWall(
        material: Material,
        left: Float, bottom: Float, width: Float, height: Float,
        role: Role = Role.STRUCTURE,
        surface: Surface = Surface.AUTO,
        visualVariant: Int = 0
    ): Block = Block.laid(
        material, left, bottom, width, height, role,
        decor = Decor.DOOR, surface = surface, visualVariant = visualVariant
    )

    /** Un pan de mur à meurtrière : le vocabulaire d'un rempart plutôt que d'une maison. */
    fun arrowSlitWall(
        material: Material,
        left: Float, bottom: Float, width: Float, height: Float,
        role: Role = Role.STRUCTURE
    ): Block = Block.laid(
        material, left, bottom, width, height, role, Decor.ARROW_SLIT, Surface.CUT_STONE
    )

    /** Un escalier extérieur dont les six marches sont la vraie silhouette physique. */
    fun staircase(
        material: Material,
        left: Float, bottom: Float, width: Float, height: Float,
        role: Role = Role.STRUCTURE
    ): Block {
        val steps = 6
        val sw = width / steps
        val sh = height / steps
        return Block.compound(
            material, left + width / 2f, bottom + height / 2f,
            role = role, surface = Surface.CUT_STONE
        ) {
            for (k in 0 until steps) {
                val h = sh * (k + 1)
                box(
                    sw / 2f, h / 2f,
                    -width / 2f + sw * (k + 0.5f),
                    -height / 2f + h / 2f
                )
            }
        }
    }

    /** Une rambarde ajourée : les poteaux et les deux lisses sont ses vraies formes. */
    fun railing(
        material: Material,
        left: Float, bottom: Float, width: Float,
        height: Float = TargetRules.detail(1f),
        role: Role = Role.STRUCTURE
    ): Block = Block.compound(
        material, left + width / 2f, bottom + height / 2f,
        role = role, surface = Surface.PLANKS
    ) {
        val thick = (height * 0.12f).coerceAtLeast(TargetRules.MIN_HALF_THICKNESS * 2f)
        box(width / 2f, thick / 2f, 0f, height / 2f - thick / 2f)
        box(width / 2f, thick / 2f, 0f, -height / 2f + thick / 2f)
        val posts = 5
        for (k in 0 until posts) {
            val x = -width / 2f + width * k / (posts - 1)
            box(thick / 2f, height / 2f, x, 0f)
        }
    }
}

/**
 * Le vocabulaire d'architecture : les pièces dont un plan de niveau assemble un site.
 *
 * Chaque module remplit une emprise qu'on lui donne — une abscisse de départ, une
 * largeur, une hauteur visée — et rend ses pierres en coordonnées monde, le sol étant
 * à zéro. Il ne connaît ni le moteur, ni le reste du niveau.
 *
 * Le catalogue est court, et c'est voulu : trois pièces suffisent à faire un château, et
 * chacune doit se **détruire différemment**, sinon la variété n'est que du décor. La
 * courtine est un bouclier qui se plie par le pied ; la tour bascule d'un côté qu'on
 * choisit ; la maison est un jeu de poteaux dont un seul qui saute fait descendre tout
 * ce qui est dessus.
 */
object TargetModules {

    /** Donne de la variété aux couvertures sans modifier leur masse ni leur casse. */
    private fun roofSurface(rng: Random, material: Material): Surface = when (material) {
        Material.THATCH -> when (rng.nextInt(10)) {
            in 0..4 -> Surface.THATCH
            in 5..7 -> Surface.SHINGLES
            else -> Surface.TILES
        }
        Material.WOOD -> if (rng.nextInt(4) == 0) Surface.TILES else Surface.SHINGLES
        Material.STONE -> Surface.SLATE
        Material.SANDSTONE -> Surface.TILES
        else -> Surface.AUTO
    }

    /** Palette de façade choisie une fois par bâtiment, pas une fois par pierre. */
    private fun masonrySurface(rng: Random, material: Material): Surface = when (material) {
        Material.STONE -> when (rng.nextInt(5)) {
            0 -> Surface.BRICK
            in 1..2 -> Surface.CUT_STONE
            else -> Surface.FIELDSTONE
        }
        Material.SANDSTONE -> if (rng.nextBoolean()) Surface.CUT_STONE else Surface.BRICK
        else -> Surface.AUTO
    }

    /** Largeur et hauteur visibles d'un bloc de façade composé d'assises superposées. */
    private fun facadeSize(block: Block): Pair<Float, Float> {
        var left = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = Float.MAX_VALUE
        var top = -Float.MAX_VALUE
        for (part in block.parts) {
            if (part.shape != com.Atom2Universe.app.games.physics.Shape.BOX) continue
            left = minOf(left, part.localX - part.halfW)
            right = maxOf(right, part.localX + part.halfW)
            bottom = minOf(bottom, part.localY - part.halfH)
            top = maxOf(top, part.localY + part.halfH)
        }
        return if (left == Float.MAX_VALUE) 0f to 0f else (right - left) to (top - bottom)
    }

    /**
     * Transforme un mur structurel en vraie façade, sans ajouter de corps qui se
     * chevaucheraient : une pierre basse reçoit la porte, les pierres hautes les baies.
     */
    private fun dressStoneFacade(
        source: List<Block>,
        rng: Random,
        surface: Surface,
        upperDecor: Decor,
        includeDoor: Boolean = true,
        maxOpenings: Int = 3
    ): List<Block> {
        if (source.isEmpty()) return source
        val out = source.map { it.dressed(surface = surface, visualVariant = rng.nextInt(4)) }.toMutableList()
        val usable = out.indices.filter { index ->
            val (w, h) = facadeSize(out[index])
            w >= TargetRules.detail(0.75f) && h >= TargetRules.detail(0.75f)
        }
        if (usable.isEmpty()) return out
        val centre = (out.minOf { it.x - it.halfSpan() } + out.maxOf { it.x + it.halfSpan() }) / 2f
        val doorIndex = if (includeDoor) usable.minWithOrNull(
            compareBy<Int> { out[it].bottom() }.thenBy { kotlin.math.abs(out[it].x - centre) }
        ) else null
        if (doorIndex != null) out[doorIndex] = out[doorIndex].dressed(decor = Decor.DOOR)

        usable.asSequence()
            .filter { it != doorIndex }
            .sortedWith(compareByDescending<Int> { out[it].y }.thenBy { kotlin.math.abs(out[it].x - centre) })
            .take(maxOpenings)
            .forEach { index -> out[index] = out[index].dressed(decor = upperDecor) }
        return out
    }

    /**
     * Un panneau plein mais friable, cassé en lattes horizontales pour que ses débris
     * restent minces. Le décor est dessiné sur l'ensemble du panneau.
     */
    private fun facadePanel(
        rng: Random,
        left: Float,
        bottom: Float,
        width: Float,
        height: Float,
        decor: Decor,
        surface: Surface
    ): Block {
        val rows = (height / TargetRules.detail(0.48f)).roundToInt().coerceIn(3, 6)
        val rowH = height / rows
        return Block.compound(
            Material.COB, left + width / 2f, bottom + height / 2f,
            decor = decor, surface = surface, visualVariant = rng.nextInt(4)
        ) {
            repeat(rows) { row ->
                box(
                    width / 2f, (rowH - TargetRules.JOINT) / 2f,
                    0f, -height / 2f + rowH * (row + 0.5f)
                )
            }
        }
    }

    /**
     * Ce qu'un module s'autorise en corps, faute d'instruction contraire.
     *
     * Le budget total d'un site est de [TargetRules.BODY_BUDGET] ; un plan en pose
     * deux à quatre. Chacun se tient donc largement en dessous, et le plan répartit
     * lui-même quand il veut une pièce maîtresse plus détaillée que les autres.
     */
    const val DEFAULT_MODULE_BUDGET = 45

    /** Un huitième de tour : l'inclinaison d'une aile de moulin. */
    private val QUART = (PI / 4.0).toFloat()

    /**
     * **La courtine** : un rempart plein, couronné de merlons.
     *
     * Lourde, stable, et faite pour être contournée autant que percée. Frappée par le
     * haut elle perd ses merlons ; frappée par le pied elle se plie et emmène tout ce
     * qui était dessus.
     */
    fun curtainWall(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE,
        bodyBudget: Int = DEFAULT_MODULE_BUDGET
    ): List<Block> {
        val merlonH = TargetRules.detail(0.9f)
        val walkway = (height - merlonH).coerceAtLeast(1f)
        // Donnée en pierre réelle : c'est [Masonry.wall] qui la met à l'échelle du
        // tempérament, et il ne faut surtout pas l'y mettre deux fois.
        val stoneH = if (rng.nextBoolean()) 0.5f else 0.6f
        val out = ArrayList<Block>()
        val surface = masonrySurface(rng, material)
        // Les merlons se paient sur le budget : il en faut un par pas de merlon.
        val merlonCount = (width / TargetRules.stone(1.4f)).toInt() + 1
        val wall = Masonry.wall(
            material, left, 0f, width, walkway,
            stoneWidth = 1.2f, stoneHeight = stoneH,
            stackBudget = 3,
            bodyBudget = (bodyBudget - merlonCount).coerceAtLeast(4),
            surface = surface
        )
        out += dressStoneFacade(wall, rng, surface, Decor.ARROW_SLIT, maxOpenings = 3)
        out += Masonry.merlons(material, left, walkway, width, merlonH)
            .map { it.dressed(surface = surface, visualVariant = rng.nextInt(4)) }
        return out
    }

    /**
     * **La tour** : un fût de pierre sur un socle débordant, coiffé d'un encorbellement
     * et de ses merlons.
     *
     * Ce qui la rend intéressante à abattre est qu'elle tombe **d'un côté**, celui d'où
     * on l'a frappée. Une tour posée près d'une maison est donc une question qu'on pose
     * au joueur : par où la faire tomber.
     *
     * L'encorbellement est un seul corps qui porte toute la largeur. Une couronne
     * débordante découpée en morceaux serait une rangée de pierres en porte-à-faux, et
     * elles tomberaient d'elles-mêmes avant le premier tir.
     *
     * **Passé une certaine hauteur, le fût se coupe en sections**, séparées par un
     * léger anneau et un peu plus étroites à mesure qu'on monte — la même idée que le
     * moulin ([windmill]), appliquée au donjon : un seul pan de mur de soixante mètres
     * serait un poteau sans repère d'échelle. Une tour ordinaire ne dépasse jamais le
     * premier palier et garde exactement son allure d'avant. Le budget de profondeur du
     * fût se **partage** entre les sections plutôt que de se répéter pour chacune :
     * sinon la somme des sections dépasserait la limite d'empilement même si chacune,
     * prise seule, la respectait. Plus il y a de sections, plus chacune se contente de
     * grosses pierres peu nombreuses — c'est le même geste que [Masonry.wall] pratiqué
     * à l'échelle de l'étage plutôt que de l'assise, et c'est ce qui permet à une tour
     * de grandir sans fin sans jamais dépasser [TargetRules.MAX_STACKED_BODIES].
     */
    fun tower(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE,
        bodyBudget: Int = DEFAULT_MODULE_BUDGET
    ): List<Block> {
        val out = ArrayList<Block>()
        val plinthH = TargetRules.detail(0.6f)
        val corbelH = TargetRules.detail(0.45f)
        val merlonH = TargetRules.detail(0.9f)
        val overhang = TargetRules.detail(0.22f)
        val ringH = TargetRules.detail(0.4f)
        val ringPitch = TargetRules.site(14f)
        val surface = masonrySurface(rng, material)

        // L'anneau intermédiaire déborde plus largement qu'un simple socle, pour bien
        // se lire comme un balcon et pas comme une reprise de maçonnerie. Pas de
        // merlons dessus, en revanche : un rang de merlons a des créneaux **vides**,
        // et la section du dessus a besoin d'une portée pleine pour se poser — un
        // rang de merlons qui porterait un mur entier est un contresens architectural
        // qui a coûté un effondrement partiel au banc d'essai (une pierre se fêlait
        // toute seule au tassement, faute d'appui continu). Les merlons restent ce
        // qu'ils sont ailleurs dans le jeu : un couronnement, jamais un plancher.
        val ringOverhang = overhang * 1.6f

        val budgetH = (height - plinthH - corbelH - merlonH).coerceAtLeast(1.5f)
        val sections = (budgetH / ringPitch).roundToInt().coerceAtLeast(1)
        val shaftH = (budgetH - (sections - 1) * ringH).coerceAtLeast(1.5f)
        val secH = shaftH / sections

        // Le socle : une assise plus large, qui assied la tour.
        out += Masonry.wall(
            material, left - overhang, 0f, width + 2f * overhang, plinthH,
            stoneWidth = (width + 2f * overhang) / 2f, stoneHeight = plinthH,
            surface = surface
        )

        // Le socle, l'encorbellement, les merlons et les anneaux prennent chacun un
        // corps de haut : on les réserve sur le budget de profondeur avant de répartir
        // ce qui reste entre les sections du fût.
        val reserve = 3 + (sections - 1)
        val perSectionStack =
            ((TargetRules.COMFORTABLE_STACK - reserve) / sections).coerceAtLeast(1)
        val perSectionBodies = ((bodyBudget - 12) / sections).coerceAtLeast(4)

        var y = plinthH
        var w = width
        for (i in 0 until sections) {
            // Deux ou trois pierres de large selon la tour, ce qui change son allure.
            val stoneW = w / (if (w > 3.5f) 3 else 2)
            val section = Masonry.wall(
                material, left + (width - w) / 2f, y, w, secH,
                stoneWidth = stoneW, stoneHeight = if (rng.nextBoolean()) 0.5f else 0.55f,
                stackBudget = perSectionStack,
                bodyBudget = perSectionBodies,
                surface = surface
            )
            out += dressStoneFacade(
                section, rng, surface, Decor.ARROW_SLIT,
                includeDoor = i == 0, maxOpenings = 2
            )
            y += secH
            if (i < sections - 1) {
                // Le balcon : un seul corps débordant, comme l'encorbellement final —
                // découpé en morceaux, ce serait une rangée de pierres en porte-à-faux,
                // et elles tomberaient d'elles-mêmes.
                val ringLeft = left + (width - w) / 2f - ringOverhang
                val ringW = w + 2f * ringOverhang
                out += Masonry.wall(
                    material, ringLeft, y, ringW, ringH,
                    stoneWidth = ringW, stoneHeight = ringH, surface = surface
                )
                y += ringH
                w *= 0.9f
            }
        }

        // L'encorbellement final et ses merlons, à la largeur du dernier tronçon.
        val topLeft = left + (width - w) / 2f
        out += Masonry.wall(
            material, topLeft - overhang, y, w + 2f * overhang, corbelH,
            stoneWidth = w + 2f * overhang, stoneHeight = corbelH, surface = surface
        )
        out += Masonry.merlons(material, topLeft - overhang, y + corbelH, w + 2f * overhang, merlonH)
            .map { it.dressed(surface = surface, visualVariant = rng.nextInt(4)) }
        return out
    }

    /**
     * **La maison** : des poteaux, un linteau, un étage, un toit.
     *
     * C'est le vocabulaire fragile du site, et le seul qui soit creux. Un poteau qui
     * saute et tout ce qui repose dessus descend — c'est le seul module où un tir bien
     * placé vaut mieux qu'un tir puissant.
     */
    fun house(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.WOOD,
        roofMaterial: Material = Material.THATCH
    ): List<Block> {
        val out = ArrayList<Block>()
        // Toute la charpente est à l'échelle du tempérament. C'est ce qui manquait à la
        // première version du mode arcade : un hameau n'a que des maisons, donc aucune
        // assise, et il sortait identique dans les deux modes.
        val postW = TargetRules.detail(0.32f)
        val beamH = TargetRules.detail(0.35f)
        val rise = (width * 0.35f).coerceIn(TargetRules.detail(0.8f), TargetRules.detail(2.2f))
        // La hauteur d'étage est une dimension de **site**, pas une épaisseur : elle
        // suit donc le curseur d'ensemble et pas celui des charpentes. Réglée sur le
        // curseur doux, elle donnait des étages de sept mètres, un seul par maison, et
        // des maisons qui ne remplissaient plus leur emprise — le site était deux fois
        // plus large sans être deux fois plus haut.
        val storeyH = TargetRules.site(2.3f)
        val storeys = ((height - rise) / storeyH).toInt().coerceIn(1, 3)
        // Un poteau de refend dès que la maison est large, sinon le linteau porte sur
        // rien et l'étage du dessus repose sur du vide. Le seuil suit l'épaisseur des
        // poteaux : en arcade ils portent seuls une façade que le mode réaliste doit
        // refendre.
        val middlePost = width > TargetRules.detail(4f)
        val facadeSurface = if (rng.nextInt(3) == 0) Surface.PLANKS else Surface.TIMBER_FRAME

        var y = 0f
        repeat(storeys) { s ->
            val postH = storeyH - beamH
            out += Masonry.post(material, left + postW / 2f, y, postW, postH)
            out += Masonry.post(material, left + width - postW / 2f, y, postW, postH)
            if (middlePost) out += Masonry.post(material, left + width / 2f, y, postW, postH)
            // Chaque travée est désormais réellement fermée. Les panneaux restent
            // légers : ils se fracturent en petites lattes, tandis que les poteaux
            // continuent à porter la maison et à commander son effondrement.
            val bays = if (middlePost) 2 else 1
            val bayW = (width - (bays + 1) * postW) / bays
            for (bay in 0 until bays) {
                val bayLeft = left + postW + bay * (bayW + postW)
                val facade = when {
                    s == 0 && bay == 0 -> Decor.DOOR
                    rng.nextBoolean() -> Decor.WINDOW_SHUTTERS
                    else -> Decor.WINDOW_ARCHED
                }
                out += facadePanel(
                    rng,
                    bayLeft + TargetRules.JOINT,
                    y + TargetRules.JOINT,
                    bayW - 2f * TargetRules.JOINT,
                    postH - 2f * TargetRules.JOINT,
                    facade,
                    facadeSurface
                )
            }
            out += Masonry.beam(material, left, y + postH, width, beamH)
            y += storeyH
        }
        out += Masonry.roof(
            roofMaterial, left, y, width, rise,
            surface = roofSurface(rng, roofMaterial), visualVariant = rng.nextInt(4)
        )
        return out
    }

    /**
     * **La porte fortifiée** : deux piles de pierre, un passage fermé par des vantaux
     * de bois, puis un corps de garde crénelé qui relie l'ensemble.
     *
     * Le portail est un corps composé de planches verticales : il paraît plein avant
     * l'impact, mais se transforme en longues échardes au premier coup bien placé.
     */
    fun gatehouse(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE,
        bodyBudget: Int = DEFAULT_MODULE_BUDGET
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= TargetRules.site(3f) || height <= TargetRules.site(3f)) return out
        val surface = masonrySurface(rng, material)
        val merlonH = TargetRules.detail(0.8f)
        val guardH = (height * 0.28f).coerceAtLeast(TargetRules.site(1.2f))
        val passageH = (height - guardH - merlonH).coerceAtLeast(TargetRules.site(1.8f))
        val pierW = (width * 0.23f).coerceIn(TargetRules.site(0.8f), width * 0.3f)
        val openingLeft = left + pierW
        val openingW = width - 2f * pierW
        val perPierBudget = ((bodyBudget - 10) / 2).coerceAtLeast(4)

        val leftPier = Masonry.wall(
            material, left, 0f, pierW, passageH,
            stoneWidth = pierW / 2f, stackBudget = 3,
            bodyBudget = perPierBudget, surface = surface
        )
        val rightPier = Masonry.wall(
            material, left + width - pierW, 0f, pierW, passageH,
            stoneWidth = pierW / 2f, stackBudget = 3,
            bodyBudget = perPierBudget, surface = surface
        )
        out += dressStoneFacade(leftPier, rng, surface, Decor.ARROW_SLIT, includeDoor = false, maxOpenings = 1)
        out += dressStoneFacade(rightPier, rng, surface, Decor.ARROW_SLIT, includeDoor = false, maxOpenings = 1)

        // Les vantaux ne chevauchent jamais les piles ; chaque planche devient un
        // fragment mince, donc le portail ne forme pas un gravat infranchissable.
        val gateH = passageH - TargetRules.JOINT
        val planks = (openingW / TargetRules.detail(0.45f)).roundToInt().coerceIn(4, 9)
        val plankW = openingW / planks
        out += Block.compound(
            Material.WOOD,
            openingLeft + openingW / 2f,
            gateH / 2f,
            decor = Decor.DOOR,
            surface = Surface.PLANKS,
            visualVariant = rng.nextInt(4)
        ) {
            repeat(planks) { i ->
                box(
                    (plankW - TargetRules.JOINT) / 2f,
                    gateH / 2f,
                    -openingW / 2f + plankW * (i + 0.5f),
                    0f
                )
            }
        }

        val guard = Masonry.wall(
            material, left, passageH, width, guardH,
            stoneWidth = width / 3f, stackBudget = 2,
            bodyBudget = (bodyBudget - out.size - 5).coerceAtLeast(4), surface = surface
        )
        out += dressStoneFacade(guard, rng, surface, Decor.ARROW_SLIT, includeDoor = false, maxOpenings = 2)
        out += Masonry.merlons(material, left, passageH + guardH, width, merlonH)
            .map { it.dressed(surface = surface, visualVariant = rng.nextInt(4)) }
        return out
    }

    /**
     * **La chapelle** : une nef basse coiffée de tuiles et un clocher étroit qui ferme
     * sa silhouette. Les fenêtres cintrées la distinguent immédiatement d'une maison.
     */
    fun chapel(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE,
        bodyBudget: Int = DEFAULT_MODULE_BUDGET
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= TargetRules.site(3f) || height <= TargetRules.site(4f)) return out
        val surface = masonrySurface(rng, material)
        // Le toit de la nef déborde légèrement de son emprise à cause de son
        // épaisseur inclinée : un vrai intervalle évite qu'il morde dans le clocher.
        val gap = TargetRules.detail(0.28f)
        val towerW = (width * 0.28f).coerceAtLeast(TargetRules.site(1.25f))
        val naveW = width - towerW - gap
        val naveRise = (naveW * 0.3f).coerceIn(TargetRules.detail(0.8f), height * 0.25f)
        val naveH = (height * 0.48f).coerceAtLeast(TargetRules.site(2f))
        val towerRise = (towerW * 0.58f).coerceAtMost(height * 0.2f)
        val towerH = (height - towerRise).coerceAtLeast(naveH + TargetRules.site(1f))
        val wallBudget = (bodyBudget / 2).coerceAtLeast(6)

        val nave = Masonry.wall(
            material, left, 0f, naveW, naveH,
            stoneWidth = 1.1f, stackBudget = 3,
            bodyBudget = wallBudget, surface = surface
        )
        out += dressStoneFacade(
            nave, rng, surface, Decor.WINDOW_ARCHED,
            includeDoor = true, maxOpenings = 2
        )
        out += Masonry.roof(
            if (rng.nextBoolean()) Material.WOOD else Material.SANDSTONE,
            left, naveH, naveW, naveRise,
            surface = if (rng.nextBoolean()) Surface.TILES else Surface.SLATE,
            visualVariant = rng.nextInt(4)
        )

        val towerLeft = left + naveW + gap
        val tower = Masonry.wall(
            material, towerLeft, 0f, towerW, towerH,
            stoneWidth = towerW / 2f, stackBudget = 4,
            bodyBudget = wallBudget, surface = surface
        )
        out += dressStoneFacade(
            tower, rng, surface, Decor.BELL,
            includeDoor = false, maxOpenings = 1
        )
        out += Masonry.roof(
            Material.WOOD, towerLeft, towerH, towerW, towerRise,
            surface = Surface.SLATE, visualVariant = rng.nextInt(4)
        )
        return out
    }

    /**
     * **La maison-tour** : un rez-de-chaussée maçonné et une maison de bois perchée
     * dessus. Elle apporte de la hauteur aux bourgs sans devenir un ouvrage militaire.
     */
    fun towerHouse(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE,
        bodyBudget: Int = DEFAULT_MODULE_BUDGET
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= TargetRules.site(2.5f) || height <= TargetRules.site(5f)) return out
        val baseH = (height * 0.36f).coerceAtLeast(TargetRules.site(2f))
        val surface = masonrySurface(rng, material)
        val base = Masonry.wall(
            material, left, 0f, width, baseH,
            stoneWidth = width / 2f, stackBudget = 3,
            bodyBudget = (bodyBudget / 2).coerceAtLeast(5), surface = surface
        )
        out += dressStoneFacade(
            base, rng, surface, Decor.WINDOW_ARCHED,
            includeDoor = true, maxOpenings = 1
        )
        out += house(
            rng, left, width, height - baseH,
            material = Material.WOOD,
            roofMaterial = if (rng.nextBoolean()) Material.WOOD else Material.THATCH
        ).map { it.translated(0f, baseH) }
        return out
    }

    /**
     * **Le lest** : tonneaux et rochers posés au pied ou sur une plate-forme.
     *
     * Ce sont des disques, et c'est tout leur intérêt : ça roule. Un effondrement qui
     * emporte trois tonneaux va bien plus loin qu'un effondrement propre, et c'est
     * souvent ce qui fait passer un tir de « presque » à « gagné ».
     */
    fun props(
        rng: Random,
        left: Float,
        bottom: Float,
        width: Float,
        count: Int = 3,
        material: Material = Material.WOOD
    ): List<Block> {
        val out = ArrayList<Block>()
        if (count <= 0 || width <= 0.5f) return out
        val step = width / count
        for (i in 0 until count) {
            // Un tonneau d'arcade est un gros tonneau — mais jamais au point de
            // chevaucher son voisin : deux corps posés l'un dans l'autre se repoussent
            // violemment dès la première image.
            val r = TargetRules.detail(0.28f + rng.nextFloat() * 0.14f)
                .coerceAtMost(step / 2f - TargetRules.JOINT)
                .coerceAtLeast(TargetRules.MIN_HALF_THICKNESS)
            out += Block.circle(material, left + step * (i + 0.5f), bottom + r, r, Role.PROP)
        }
        return out
    }

    /**
     * **Le puits** : margelle, montants et toit réunis dans un seul corps composé.
     *
     * Comme les tonneaux ([props]), c'est une pièce qu'on pose seule et pas qu'on
     * assemble. La première version le construisait vraiment en pierres —
     * margelle, montants, toit à deux pentes, cinq corps — et le résultat ne se
     * lisait pas mieux qu'un tas de rectangles gris. Un puits se reconnaît à son
     * dessin, pas à sa géométrie.
     */
    fun well(
        centerX: Float,
        bottom: Float,
        radius: Float = TargetRules.detail(0.7f),
        material: Material = Material.STONE
    ): Block = Block.compound(
        material, centerX, bottom + radius,
        role = Role.PROP, surface = Surface.FIELDSTONE
    ) {
        circle(radius * 0.72f, 0f, -radius * 0.28f, Surface.FIELDSTONE)
        box(radius * 0.09f, radius * 0.82f, -radius * 0.62f, radius * 0.55f, surface = Surface.PLANKS)
        box(radius * 0.09f, radius * 0.82f, radius * 0.62f, radius * 0.55f, surface = Surface.PLANKS)
        val roofHalf = radius * 0.86f
        val rise = radius * 0.62f
        val slope = hypot(roofHalf, rise)
        val a = atan2(rise, roofHalf)
        box(slope / 2f, radius * 0.09f, -roofHalf / 2f, radius * 1.48f, a, Surface.SHINGLES)
        box(slope / 2f, radius * 0.09f, roofHalf / 2f, radius * 1.48f, -a, Surface.SHINGLES)
    }

    /**
     * **L'abri** : un toit et deux poteaux, réellement ajourés mais réunis dans un
     * seul corps — le module le plus court du catalogue.
     */
    fun shelter(
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.WOOD
    ): Block {
        val halfW = width / 2f
        val rise = height * 0.4f
        val eaveY = height * 0.1f
        val slope = hypot(halfW, rise)
        val a = atan2(rise, halfW)
        val thick = TargetRules.detail(0.16f)
        return Block.compound(
            material, left + halfW, height / 2f,
            surface = Surface.SHINGLES
        ) {
            box(thick / 2f, height * 0.3f, -width * 0.34f, -height * 0.2f, surface = Surface.PLANKS)
            box(thick / 2f, height * 0.3f, width * 0.34f, -height * 0.2f, surface = Surface.PLANKS)
            box(slope / 2f, thick / 2f, -halfW / 2f, eaveY, a, Surface.SHINGLES)
            box(slope / 2f, thick / 2f, halfW / 2f, eaveY, -a, Surface.SHINGLES)
        }
    }

    // ── Le second catalogue : ce qui n'est ni un mur, ni une tour, ni une maison ──
    //
    // Les quatre premières pièces suffisaient à faire un château, et pas grand-chose
    // d'autre. Celles qui suivent existent pour que deux sites de suite ne se
    // ressemblent pas, et chacune est arrivée avec sa propre façon de tomber — c'est
    // le seul critère qui vaille : un module qui s'écroule comme un autre est du décor
    // repeint, pas une pièce de plus.

    /**
     * **Le moulin à vent** : une tour tronconique, une galerie, et un chapeau qui porte
     * les ailes.
     *
     * Tout l'intérêt tient dans le dernier bloc. Le chapeau et sa croix d'ailes sont
     * **un seul corps**, simplement posé sur la tour : rien ne l'y attache, et sa
     * masse est haut perchée. Un boulet qui passe à ras du sommet, ou une tour qu'on
     * fait vibrer par le pied, et la croix part par-dessus bord en tournant — puis se
     * rompt en trois morceaux, le chapeau et les deux ailes.
     *
     * C'est aussi le seul module dont la silhouette dépasse largement son emprise :
     * les ailes débordent de la tour, et le plateau qui le porte doit en tenir compte.
     */
    fun windmill(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 0.5f || height <= 0.5f) return out
        val galerieH = TargetRules.detail(0.5f)
        // L'envergure des ailes se déduit de la tour, et la hauteur qu'elles réclament
        // au-dessus du chapeau vaut leur demi-envergure. On la réserve d'abord : une
        // tour qui mangerait toute la hauteur donnerait un moulin sans ailes.
        val demiAile = width * 0.6f
        val reserve = demiAile * 1.5f + galerieH
        val futH = (height - reserve).coerceAtLeast(TargetRules.site(2.5f))

        // Le fût, en trois sections qui rétrécissent : c'est ce qui fait lire « moulin »
        // et non « tour ». Chaque section est un mur d'appareil ordinaire, plafonné à
        // quatre corps de haut pour que les trois ensemble tiennent dans le budget de
        // profondeur.
        val sections = 3
        val secH = futH / sections
        for (i in 0 until sections) {
            val t = i / sections.toFloat()
            val w = width * (1f - 0.24f * t)
            out += Masonry.wall(
                material, left + (width - w) / 2f, i * secH, w, secH,
                stoneWidth = 1.1f, stoneHeight = if (rng.nextBoolean()) 0.5f else 0.6f,
                stackBudget = 4, bodyBudget = 14
            )
        }

        // La galerie : un lit débordant, celui sur lequel le meunier fait le tour de sa
        // machine. Il donne au chapeau une assise plus large que le sommet du fût.
        val sommetW = width * (1f - 0.24f * (sections - 1) / sections.toFloat())
        val galerieW = sommetW + TargetRules.detail(1.2f)
        out += Masonry.band(
            material, left + (width - galerieW) / 2f, futH, galerieW, galerieH, cols = 2
        )

        // Le chapeau et ses ailes, d'un seul tenant.
        val chapeauW = sommetW * 0.95f
        val chapeauH = TargetRules.detail(0.55f)
        val epaisseur = TargetRules.detail(0.3f)
        // Le moyeu est assez haut pour que les deux ailes du bas ne plongent pas dans
        // le chapeau : elles sont dans le même corps, elles ne se repousseraient donc
        // pas — elles feraient un moulin difforme, ce qui est pire.
        val moyeu = chapeauH / 2f + demiAile * 0.75f
        val cx = left + width / 2f
        out += Block.compound(Material.WOOD, cx, futH + galerieH + chapeauH / 2f) {
            box(chapeauW / 2f, chapeauH / 2f, 0f, 0f)
            box(epaisseur / 2f, (moyeu - chapeauH / 2f) / 2f, 0f, (moyeu + chapeauH / 2f) / 2f)
            box(demiAile, epaisseur / 2f, 0f, moyeu, QUART)
            box(demiAile, epaisseur / 2f, 0f, moyeu, -QUART)
        }
        return out
    }

    /**
     * **Le château de cartes** : des tentes de planches posées en pyramide, exactement
     * comme un vrai château de cartes agrandi à l'échelle d'un bâtiment.
     *
     * Rien n'est réaliste ici, et c'est voulu — c'est un jeu de cartes, pas de la
     * maçonnerie. Chaque tente est **un seul corps** (deux planches soudées à leur
     * sommet, comme un toit, [Masonry.roof]), posée sur une carte à plat qui relie les
     * sommets des deux tentes de l'étage du dessous. Un étage compte donc une tente de
     * moins que celui d'en dessous, jusqu'à une tente unique au faîte — c'est ce
     * rétrécissement, et lui seul, qui tient toute la pile debout : chaque tente porte
     * son poids sur ses deux pieds, jamais en porte-à-faux.
     *
     * C'est aussi le module le plus spectaculaire à raser : une tente fauchée à un
     * étage bas emporte tout ce qui reposait dessus, à l'identique d'un vrai château de
     * cartes qu'on effleure du doigt.
     */
    fun cardCastle(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.CARDBOARD
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 1f || height <= 1f) return out
        // Le nombre de tentes du rez-de-chaussée décide de tout : la largeur du site,
        // et le nombre d'étages puisque la pile se termine forcément à une seule tente
        // au sommet. Plafonné à 7 : au-delà, la pile centrale (tente, carte, tente,
        // carte...) dépasserait la profondeur d'empilement que le moteur peut tenir.
        val base = (width / TargetRules.site(2.4f)).roundToInt().coerceIn(3, 7)
        val tentH = (height / base).coerceAtLeast(TargetRules.site(0.8f))
        val thickness = TargetRules.detail(0.1f)
        val cardH = TargetRules.detail(0.08f)
        val pitch = width / base
        val halfSpan = (pitch * 0.46f).coerceAtLeast(TargetRules.MIN_HALF_THICKNESS * 4f)
        val slope = hypot(halfSpan, tentH)
        val angle = atan2(tentH, halfSpan)
        // Un petit plat au faîte de chaque tente : sans lui, la carte du dessus se
        // pose sur la pointe exacte où les deux planches se croisent, un contact
        // aussi étroit qu'un fil et que le solveur ne sait pas tenir — la pile entière
        // partait en glissade dès le tassement. Un vrai château de cartes ne tient pas
        // sur des pointes non plus : les cartes se croisent sur une petite surface, et
        // c'est elle qu'on donne ici au moteur.
        val ridgeHalf = (thickness * 1.5f).coerceAtLeast(TargetRules.MIN_HALF_THICKNESS)

        var count = base
        var y = 0f
        var xs = FloatArray(count) { left + pitch / 2f + it * pitch }
        while (true) {
            for (cx in xs) {
                // Rôle MONUMENT : la tente s'allume à toute hauteur, façon tour Eiffel,
                // au lieu de s'éteindre au-delà de six mètres comme une torche
                // ordinaire — voir [TargetField.lightUp]. Les cartes à plat, elles,
                // restent de simples pierres de structure : la lumière dessine la
                // silhouette, pas les planchers.
                out += Block.compound(material, cx, y + tentH / 2f, role = Role.MONUMENT) {
                    box(slope / 2f, thickness / 2f, -halfSpan / 2f, 0f, angle)
                    box(slope / 2f, thickness / 2f, halfSpan / 2f, 0f, -angle)
                    box(ridgeHalf, thickness / 2f, 0f, tentH / 2f - thickness / 2f)
                }
            }
            if (count == 1) break
            // Les cartes à plat, une entre chaque paire de tentes voisines : c'est sur
            // elles que se pose l'étage suivant.
            val nextXs = FloatArray(count - 1)
            for (i in 0 until count - 1) {
                val cx = (xs[i] + xs[i + 1]) / 2f
                out += Block.laid(
                    material, cx - pitch / 2f + TargetRules.JOINT, y + tentH,
                    pitch - 2f * TargetRules.JOINT, cardH
                )
                nextXs[i] = cx
            }
            xs = nextXs
            count--
            y += tentH + cardH
        }
        return out
    }

    /**
     * **La pyramide de grès** : des gradins de moins en moins larges, et rien d'autre.
     *
     * C'est le module le plus stable du jeu, et c'est tout son propos : il n'y a rien à
     * renverser. Une pyramide ne se gagne qu'en cassant, gradin par gradin, ce qui en
     * fait le contraire exact de la courtine — laquelle se gagne d'un seul bon coup au
     * pied. Un site qui mélange les deux demande deux façons de tirer.
     */
    fun pyramid(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.SANDSTONE,
        bodyBudget: Int = DEFAULT_MODULE_BUDGET
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 0.5f || height <= 0.5f) return out
        val gradins = (height / TargetRules.stone(1.3f)).roundToInt().coerceIn(4, 8)
        val gradinH = height / gradins
        // Ce que chaque gradin s'autorise en pierres, sachant qu'il y en a plusieurs et
        // que les hauts sont plus étroits que les bas.
        val parGradin = (bodyBudget / gradins).coerceIn(1, 5)
        for (i in 0 until gradins) {
            val t = i / gradins.toFloat()
            val w = width * (1f - 0.84f * t)
            val cols = (w / TargetRules.stone(2f)).roundToInt().coerceIn(1, parGradin)
            out += Masonry.band(material, left + (width - w) / 2f, i * gradinH, w, gradinH, cols)
        }
        // Le pyramidion, une seule pierre : c'est ce qui donne la pointe, et c'est aussi
        // ce qui tombe en premier.
        val pointe = width * 0.16f + TargetRules.JOINT
        out += Block.laid(
            material, left + (width - pointe) / 2f, gradins * gradinH, pointe, gradinH * 0.9f
        )
        return out
    }

    /**
     * **L'amphithéâtre** : deux ou trois étages d'arcades, chacun un peu en retrait du
     * précédent.
     *
     * Il se détruit comme une maison en beaucoup plus grand : ce sont les piles qui
     * portent tout, et une pile qui saute fait descendre l'arcade au-dessus d'elle, qui
     * emporte l'étage suivant. Un tir bien placé au pied ouvre une brèche qui monte
     * toute seule jusqu'en haut — le seul module du jeu où la ruine se propage vers le
     * ciel.
     */
    fun arena(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE,
        bodyBudget: Int = DEFAULT_MODULE_BUDGET
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 1f || height <= 1f) return out
        val etages = if (height > TargetRules.site(8f)) 3 else 2
        val bandeH = TargetRules.detail(0.75f)
        val etageH = height / etages
        var y = 0f
        var l = left
        var w = width
        for (e in 0 until etages) {
            val travees = (w / TargetRules.site(3.4f)).roundToInt().coerceIn(2, 5)
            val piles = travees + 1
            // Une pile jamais deux fois plus haute que large : au-delà elle bascule
            // toute seule avant que l'arcade ne soit posée dessus.
            val pileW = (w / travees * 0.42f).coerceAtLeast(TargetRules.detail(0.5f))
            val pileH = (etageH - bandeH).coerceAtLeast(TargetRules.site(1f))
            out += arcade(material, l, y, w, pileH, bandeH, piles, pileW)
            y += pileH + bandeH
            val retrait = pileW * 0.6f
            l += retrait
            w -= 2f * retrait
            if (w < TargetRules.site(2f)) break
        }
        // Le couronnement : un rang de merlons, qui s'égrène au premier tir un peu haut
        // et dit au joueur qu'il a touché sans avoir encore percé.
        if (w > TargetRules.site(1f)) {
            out += Masonry.merlons(material, l, y, w, TargetRules.detail(0.7f))
        }
        return out
    }

    /**
     * **Le temple** : un stylobate, une colonnade, une architrave et un fronton.
     *
     * Les colonnes sont des blocs composés en tambours ([Masonry.column]) : debout
     * elles ne coûtent qu'un corps chacune et ne bronchent pas, abattues elles
     * s'égrènent et roulent. C'est l'image qu'on cherchait, et elle est gratuite tant
     * que le joueur ne l'a pas méritée.
     *
     * L'architrave ne va **que d'un axe de colonne au suivant**, et jamais jusqu'au
     * bord : une pierre posée à cheval sur une seule colonne est une balance, et elle
     * verse du côté où elle déborde avant même le premier tir.
     */
    fun temple(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.SANDSTONE
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 1f || height <= 1f) return out
        val marcheH = TargetRules.detail(0.45f)
        val archiH = TargetRules.detail(0.6f)
        val nColonnes = (width / TargetRules.site(2.8f)).roundToInt().coerceIn(3, 7)

        // Les deux marches du stylobate, la seconde en retrait.
        val retrait = width * 0.04f
        out += Masonry.band(material, left, 0f, width, marcheH, nColonnes - 1)
        out += Masonry.band(
            material, left + retrait, marcheH, width - 2f * retrait, marcheH, nColonnes - 1
        )
        val socle = 2f * marcheH

        // Les colonnes. Leur élancement est plafonné : une colonne de temple réelle fait
        // six diamètres de haut, et à ce compte-là elle tombe toute seule au tassement.
        // Ce que la hauteur perd, le fronton le récupère.
        val emprise = width - 2f * retrait
        val pas = emprise / nColonnes
        val colonneW = pas * 0.5f
        val voulu = height - socle - archiH - width * 0.2f
        val colonneH = voulu.coerceIn(TargetRules.site(1.5f), 3.2f * colonneW)
        val axe0 = left + retrait + pas / 2f
        for (i in 0 until nColonnes) {
            out += Masonry.column(
                material, axe0 + i * pas, socle, colonneW, colonneH,
                drums = if (colonneH > TargetRules.site(3f)) 4 else 3
            )
        }

        // L'architrave, d'axe en axe.
        val hautColonnes = socle + colonneH
        for (i in 0 until nColonnes - 1) {
            val a = axe0 + i * pas
            out += Block.laid(material, a, hautColonnes, pas - TargetRules.JOINT, archiH)
        }
        // Le fronton : une corniche horizontale et deux rampants, **d'un seul tenant**.
        //
        // La corniche n'est pas un ornement, c'est ce qui tient tout. Deux rampants
        // seuls ne touchent l'architrave que par la pointe de leur pied, et ces deux
        // pointes-là tombent hors de la colonnade — l'architrave s'arrête au dernier axe
        // de colonne, le fronton déborde. Le premier temple bâti sans corniche perdait
        // donc son fronton avant le premier tir : il n'était posé sur rien. Avec elle,
        // il repose à plat sur toute la colonnade, et il se rompt en trois morceaux au
        // lieu de deux.
        val fronton = (height - hautColonnes - archiH)
            .coerceIn(width * 0.12f, width * 0.32f)
        val corniche = TargetRules.detail(0.32f)
        val epaisseur = TargetRules.detail(0.3f)
        val demi = width / 2f
        val rampant = hypot(demi, fronton)
        val pente = atan2(fronton, demi)
        out += Block.compound(material, left + demi, hautColonnes + archiH + corniche / 2f) {
            box(demi, corniche / 2f, 0f, 0f)
            box(rampant / 2f, epaisseur / 2f, -demi / 2f, (corniche + fronton) / 2f, pente)
            box(rampant / 2f, epaisseur / 2f, demi / 2f, (corniche + fronton) / 2f, -pente)
        }
        return out
    }

    /**
     * **L'aqueduc** : un grand ordre d'arcades, un petit ordre par-dessus, et le canal
     * tout en haut.
     *
     * Les deux ordres ne sont pas un ornement : **un seul rang d'arches se lit comme une
     * colonnade**, et c'est ce qu'a donné la première version — quatre piles et un
     * linteau, indiscernables d'un temple sans fronton. C'est le petit ordre, avec ses
     * arches deux fois plus serrées, qui fait dire « aqueduc » d'un coup d'œil.
     *
     * C'est aussi le module qui profite le plus du relief : posé sur un plateau
     * au-dessus d'un creux, il barre le ciel entre la machine et ce qu'il y a derrière,
     * et le joueur doit décider s'il le perce ou s'il passe au-dessus.
     */
    fun aqueduct(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 1f || height <= 1f) return out
        val travees = (width / TargetRules.site(4.5f)).roundToInt().coerceIn(2, 4)
        val arcH = TargetRules.detail(0.7f)
        val canalH = TargetRules.detail(0.6f)

        // Le grand ordre prend les deux tiers de ce qui reste une fois le canal servi.
        val utile = (height - canalH).coerceAtLeast(TargetRules.site(2f))
        val petitH = (utile * 0.3f).coerceAtLeast(arcH + TargetRules.site(0.8f))
        val grandH = (utile - petitH).coerceAtLeast(TargetRules.site(1.5f))

        val grosPileW = (width / travees * 0.34f).coerceAtLeast(TargetRules.detail(0.7f))
        out += arcade(material, left, 0f, width, grandH - arcH, arcH, travees + 1, grosPileW)

        // Le petit ordre, en retrait de rien du tout : il porte le canal, il doit donc
        // tomber d'aplomb sur le grand. Deux fois plus de travées, deux fois plus
        // étroites, et des piles fines — c'est la seule chose que le joueur voie.
        val menues = travees * 2
        val petitePileW = (width / menues * 0.4f).coerceAtLeast(TargetRules.detail(0.45f))
        out += arcade(
            material, left, grandH, width, petitH - arcH, arcH, menues + 1, petitePileW
        )

        // Le canal : il repose sur les arcs, et il est **plein**. Un aqueduc percé perd
        // son tablier d'un coup, ce qui est de très loin le plus bel effondrement du
        // catalogue.
        val axeG = left + petitePileW / 2f
        val axeD = left + width - petitePileW / 2f
        out += Masonry.band(material, axeG, grandH + petitH, axeD - axeG, canalH, cols = travees)
        return out
    }

    /**
     * **Le grenier sur pilotis** : une cave ouverte, un plancher épais, un étage clos,
     * un toit.
     *
     * C'est le bâtiment du croquis, et c'est le plus fragile du lot : tout le poids de
     * l'étage passe par quatre poteaux de bois. Un boulet dans les pilotis et la maison
     * s'assied d'un bloc — c'est le seul module qu'on abat sans jamais toucher à ce
     * qu'on veut détruire.
     */
    fun granary(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.WOOD
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 1f || height <= 1f) return out
        val poteauW = TargetRules.detail(0.45f)
        val plancherH = TargetRules.detail(0.5f)
        val rise = (width * 0.34f).coerceIn(TargetRules.detail(0.9f), TargetRules.detail(2.4f))
        val caveH = (height * 0.34f).coerceAtLeast(TargetRules.site(1.4f))
        val etageH = (height - caveH - 2f * plancherH - rise).coerceAtLeast(TargetRules.site(1.2f))

        // Les pilotis, et le coffre de la cave entre les deux du milieu.
        val pilotis = if (width > TargetRules.detail(5f)) 4 else 3
        for (i in 0 until pilotis) {
            val cx = left + poteauW / 2f + i * (width - poteauW) / (pilotis - 1)
            out += Masonry.post(material, cx, 0f, poteauW, caveH)
        }
        // Le coffre se pose **dans une travée**, entre deux pilotis voisins, et jamais
        // à cheval sur ceux du milieu : deux corps qui se chevauchent à la pose se
        // repoussent violemment dès la première image.
        val travees = pilotis - 1
        val pas = (width - poteauW) / travees
        val coffreW = pas - poteauW - TargetRules.JOINT
        if (rng.nextFloat() < 0.7f && coffreW > TargetRules.site(0.5f)) {
            val travee = rng.nextInt(travees)
            out += Block.laid(
                Material.COB,
                left + poteauW + travee * pas + TargetRules.JOINT, TargetRules.JOINT,
                coffreW, (caveH * 0.65f).coerceAtMost(TargetRules.site(1.2f))
            )
        }
        out += Masonry.beam(material, left, caveH, width, plancherH)

        // L'étage : deux poteaux d'angle et une façade pleine entre eux.
        val basEtage = caveH + plancherH
        out += Masonry.post(material, left + poteauW / 2f, basEtage, poteauW, etageH)
        out += Masonry.post(material, left + width - poteauW / 2f, basEtage, poteauW, etageH)
        val mur = width - 2f * poteauW
        if (mur > TargetRules.site(0.6f)) {
            out += facadePanel(
                rng,
                left + poteauW + TargetRules.JOINT,
                basEtage + TargetRules.JOINT,
                mur - 2f * TargetRules.JOINT,
                etageH - 2f * TargetRules.JOINT,
                if (rng.nextBoolean()) Decor.WINDOW_SHUTTERS else Decor.WINDOW_ARCHED,
                if (rng.nextBoolean()) Surface.TIMBER_FRAME else Surface.PLANKS
            )
        }
        out += Masonry.beam(material, left, basEtage + etageH, width, plancherH)
        out += Masonry.roof(
            Material.THATCH, left, basEtage + etageH + plancherH, width, rise,
            surface = roofSurface(rng, Material.THATCH), visualVariant = rng.nextInt(4)
        )
        return out
    }

    /**
     * **L'immeuble** : trois ou quatre lits de grosses pierres, et un toit.
     *
     * Le bâtiment de droite du croquis. Massif, sans poteau ni creux, il ne s'abat pas
     * — il se **renverse**, d'un bloc, du côté d'où on l'a frappé, et ses lits
     * dégringolent en escalier. C'est le module qui récompense le mieux un tir au pied,
     * et celui qui pardonne le moins un tir au sommet : on n'y décoiffe qu'un toit.
     */
    fun insula(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.STONE
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 0.5f || height <= 0.5f) return out
        val rise = (width * 0.42f).coerceIn(TargetRules.detail(0.9f), TargetRules.detail(2.6f))
        val corps = (height - rise).coerceAtLeast(TargetRules.site(1.5f))
        val lits = (corps / TargetRules.stone(1.5f)).roundToInt().coerceIn(2, 5)
        val litH = corps / lits
        val cols = if (width > TargetRules.stone(2.4f)) 2 else 1
        val masonrySurface = masonrySurface(rng, material)
        val walls = ArrayList<Block>()
        for (i in 0 until lits) {
            // Un lit sur deux en un seul morceau : c'est l'appareil, et sans lui le
            // joint vertical courrait du sol au toit et l'immeuble se fendrait en deux.
            walls += Masonry.band(
                material, left, i * litH, width, litH,
                if (i % 2 == 1) maxOf(1, cols - 1) else cols
            )
        }
        out += dressStoneFacade(
            walls, rng, masonrySurface, Decor.WINDOW_ARCHED,
            includeDoor = true, maxOpenings = (lits - 1).coerceAtLeast(1)
        )
        val roofMaterial = if (rng.nextBoolean()) Material.THATCH else Material.WOOD
        out += Masonry.roof(
            roofMaterial, left, corps, width, rise,
            surface = roofSurface(rng, roofMaterial), visualVariant = rng.nextInt(4)
        )
        return out
    }

    /**
     * **La grange** : basse, large, et à peu près rien d'autre qu'un toit.
     *
     * Elle existe pour la silhouette. Un village fait de maisons toutes semblables se
     * lit comme un peigne ; une grange couchée au milieu lui donne un profil. Et son
     * toit immense, qui pèse plus que ce qui le porte, en fait la construction la plus
     * rentable du jeu au boulet près : trois poteaux, et tout descend.
     */
    fun barn(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.WOOD
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 1f || height <= 1f) return out
        val poteauW = TargetRules.detail(0.4f)
        val beamH = TargetRules.detail(0.35f)
        val rise = (width * 0.46f).coerceIn(TargetRules.detail(1.2f), height * 0.62f)
        val murH = (height - rise - beamH).coerceAtLeast(TargetRules.site(1.2f))
        val poteaux = (width / TargetRules.detail(3f)).roundToInt().coerceIn(3, 5)
        for (i in 0 until poteaux) {
            val cx = left + poteauW / 2f + i * (width - poteauW) / (poteaux - 1)
            out += Masonry.post(material, cx, 0f, poteauW, murH)
        }
        // Toutes les travées sont closes ; la grande porte reste le point de lecture.
        val travees = poteaux - 1
        val pas = (width - poteauW) / travees
        val doorBay = rng.nextInt(travees)
        val pignonW = pas - poteauW - TargetRules.JOINT
        if (pignonW > TargetRules.site(0.5f)) {
            for (bay in 0 until travees) {
                out += facadePanel(
                    rng,
                    left + poteauW + bay * pas + TargetRules.JOINT,
                    TargetRules.JOINT,
                    pignonW,
                    murH - 2f * TargetRules.JOINT,
                    if (bay == doorBay) Decor.DOOR else Decor.NONE,
                    if ((bay + doorBay) % 3 == 0) Surface.TIMBER_FRAME else Surface.PLANKS
                )
            }
        }
        out += Masonry.beam(material, left, murH, width, beamH)
        out += Masonry.roof(
            Material.THATCH, left, murH + beamH, width, rise,
            surface = roofSurface(rng, Material.THATCH), visualVariant = rng.nextInt(4)
        )
        return out
    }

    /**
     * **La palissade** : des panneaux de pieux, plantés côte à côte.
     *
     * Un pieu isolé serait un domino de quatre mètres sur cinquante centimètres, et il
     * tomberait tout seul avant le premier tir. Un **panneau** de six pieux est aussi
     * large que haut, il tient sans broncher — et quand il rompt, il rompt en ses six
     * pieux, ce qui est très exactement l'image qu'on voulait.
     */
    fun palisade(
        rng: Random,
        left: Float,
        width: Float,
        height: Float,
        material: Material = Material.WOOD
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 0.5f) return out
        val h = height.coerceAtLeast(TargetRules.site(1.5f))
        val panneaux = (width / (h * 0.9f)).roundToInt().coerceAtLeast(1)
        val panneauW = width / panneaux
        val pieux = (panneauW / TargetRules.detail(0.55f)).roundToInt().coerceIn(3, 7)
        val pieuW = panneauW / pieux
        for (p in 0 until panneaux) {
            val cx = left + (p + 0.5f) * panneauW
            out += Block.compound(material, cx, h / 2f) {
                for (k in 0 until pieux) {
                    box(
                        (pieuW - TargetRules.JOINT) / 2f, h / 2f,
                        (k - (pieux - 1) / 2f) * pieuW, 0f
                    )
                }
            }
        }
        return out
    }

    /**
     * Une file de piles portant une file d'arcs, chaque arc allant **d'un axe de pile
     * au suivant**.
     *
     * C'est le geste commun de l'amphithéâtre et de l'aqueduc, et il n'a l'air de rien
     * jusqu'à ce qu'on le fasse de travers : un linteau qui déborde au-delà de la
     * dernière pile est une balance qui verse dès la pose, et c'est la seule façon de
     * rater une arcade.
     */
    private fun arcade(
        material: Material,
        left: Float,
        bottom: Float,
        width: Float,
        pierHeight: Float,
        bandHeight: Float,
        piers: Int,
        pierWidth: Float
    ): List<Block> {
        val out = ArrayList<Block>()
        val n = piers.coerceAtLeast(2)
        val pas = (width - pierWidth) / (n - 1)
        val axe0 = left + pierWidth / 2f
        for (i in 0 until n) {
            out += Masonry.post(material, axe0 + i * pas, bottom, pierWidth, pierHeight)
        }
        for (i in 0 until n - 1) {
            out += Block.laid(
                material, axe0 + i * pas, bottom + pierHeight,
                pas - TargetRules.JOINT, bandHeight
            )
        }
        return out
    }
}
