package com.Atom2Universe.app.games.trebuchet

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
        bodyBudget: Int = TargetRules.BODY_BUDGET
    ): List<Block> {
        val out = ArrayList<Block>()
        if (width <= 0.05f || height <= 0.05f) return out
        val j = TargetRules.JOINT

        // Le tempérament du jeu décide de la taille des pierres : en arcade, elles sont
        // deux fois plus grosses, ce qui rend chaque coup lisible et divise par quatre
        // le nombre de corps.
        val sw = stoneWidth * TargetRules.style.stoneScale
        val sh = stoneHeight * TargetRules.style.stoneScale
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
                out += Block.compound(material, sx + sw / 2f, y0 + bodyH / 2f, role = role) {
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
        height: Float = 0.9f,
        merlonWidth: Float = 0.8f,
        gapWidth: Float = 0.6f
    ): List<Block> {
        val out = ArrayList<Block>()
        val scale = TargetRules.style.stoneScale
        val mw = merlonWidth * scale
        val gw = gapWidth * scale
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
        thickness: Float = 0.18f
    ): Block {
        val halfW = width / 2f
        val slope = hypot(halfW, rise)
        val angle = atan2(rise, halfW)
        return Block.compound(material, left + halfW, bottom + rise / 2f) {
            box(slope / 2f, thickness / 2f, -halfW / 2f, 0f, angle)
            box(slope / 2f, thickness / 2f, halfW / 2f, 0f, -angle)
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

    /**
     * Ce qu'un module s'autorise en corps, faute d'instruction contraire.
     *
     * Le budget total d'un site est de [TargetRules.BODY_BUDGET] ; un plan en pose
     * deux à quatre. Chacun se tient donc largement en dessous, et le plan répartit
     * lui-même quand il veut une pièce maîtresse plus détaillée que les autres.
     */
    const val DEFAULT_MODULE_BUDGET = 45

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
        val merlonH = 0.9f
        val walkway = (height - merlonH).coerceAtLeast(1f)
        val stoneH = if (rng.nextBoolean()) 0.5f else 0.6f
        val out = ArrayList<Block>()
        // Les merlons se paient sur le budget : il en faut un tous les mètre et demi.
        val merlonCount = (width / 1.4f).toInt() + 1
        out += Masonry.wall(
            material, left, 0f, width, walkway,
            stoneWidth = 1.2f, stoneHeight = stoneH,
            bodyBudget = (bodyBudget - merlonCount).coerceAtLeast(4)
        )
        out += Masonry.merlons(material, left, walkway, width, merlonH)
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
        val plinthH = 0.6f
        val corbelH = 0.45f
        val merlonH = 0.9f
        val shaftH = (height - plinthH - corbelH - merlonH).coerceAtLeast(1.5f)
        val overhang = 0.22f

        // Le socle : une assise plus large, qui assied la tour.
        out += Masonry.wall(
            material, left - overhang, 0f, width + 2f * overhang, plinthH,
            stoneWidth = (width + 2f * overhang) / 2f, stoneHeight = plinthH
        )
        // Le fût. Deux ou trois pierres de large selon la tour, ce qui change son allure.
        val stoneW = width / (if (width > 3.5f) 3 else 2)
        out += Masonry.wall(
            material, left, plinthH, width, shaftH,
            stoneWidth = stoneW, stoneHeight = if (rng.nextBoolean()) 0.5f else 0.55f,
            // Le socle, l'encorbellement et les merlons prennent trois corps de haut :
            // on les réserve sur le budget de profondeur, et une douzaine sur celui des
            // corps.
            stackBudget = TargetRules.COMFORTABLE_STACK - 3,
            bodyBudget = (bodyBudget - 12).coerceAtLeast(4)
        )
        // L'encorbellement : un seul corps, sinon ses bords tombent tout seuls.
        out += Masonry.wall(
            material, left - overhang, plinthH + shaftH, width + 2f * overhang, corbelH,
            stoneWidth = width + 2f * overhang, stoneHeight = corbelH
        )
        out += Masonry.merlons(
            material, left - overhang, plinthH + shaftH + corbelH, width + 2f * overhang, merlonH
        )
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
        val postW = 0.32f
        val beamH = 0.35f
        val rise = (width * 0.35f).coerceIn(0.8f, 2.2f)
        val storeyH = 2.3f
        val storeys = ((height - rise) / storeyH).toInt().coerceIn(1, 3)
        // Un poteau de refend dès que la maison est large, sinon le linteau porte sur
        // rien et l'étage du dessus repose sur du vide.
        val middlePost = width > 4f

        var y = 0f
        repeat(storeys) { s ->
            val postH = storeyH - beamH
            out += Masonry.post(material, left + postW / 2f, y, postW, postH)
            out += Masonry.post(material, left + width - postW / 2f, y, postW, postH)
            if (middlePost) out += Masonry.post(material, left + width / 2f, y, postW, postH)
            // Un hourdis de torchis entre deux poteaux : il casse en poussière et donne
            // au tir un retour immédiat sans rien changer à la structure.
            //
            // Il repose **sur le plancher de son étage**. Posé en l'air, comme il l'a
            // d'abord été, il tombait au chargement et venait s'allonger devant les
            // poteaux, où il servait de bouclier : trois boulets dans la façade ne
            // faisaient plus rien, et la maison passait pour solide alors qu'elle était
            // simplement protégée par son propre mur tombé.
            if (rng.nextFloat() < 0.5f) {
                // Il se pose dans **une travée**, entre deux poteaux, et jamais à cheval
                // sur celui du milieu : deux corps qui se chevauchent à la pose se
                // repoussent violemment dès la première image.
                val bays = if (middlePost) 2 else 1
                val bay = rng.nextInt(bays)
                val bayW = (width - (bays + 1) * postW) / bays
                val bayLeft = left + postW + bay * (bayW + postW)
                out += Block.laid(
                    Material.COB,
                    bayLeft + TargetRules.JOINT,
                    y + TargetRules.JOINT,
                    bayW - 2f * TargetRules.JOINT,
                    (postH * 0.6f).coerceAtMost(1.3f)
                )
            }
            out += Masonry.beam(material, left, y + postH, width, beamH)
            y += storeyH
        }
        out += Masonry.roof(roofMaterial, left, y, width, rise)
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
            val r = 0.28f + rng.nextFloat() * 0.14f
            out += Block.circle(material, left + step * (i + 0.5f), bottom + r, r, Role.PROP)
        }
        return out
    }
}
