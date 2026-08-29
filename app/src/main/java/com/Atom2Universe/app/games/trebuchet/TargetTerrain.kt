package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.random.Random

/**
 * Les reliefs qu'on sait dessiner. Chacun raconte un tir différent.
 *
 * Ce n'est pas de la décoration. Un site posé quinze mètres plus haut demande une
 * trajectoire plus tendue ; une colline plantée à mi-chemin interdit le tir rasant et
 * force la cloche ; un vallon cache le pied des maisons et rend la portée difficile à
 * juger à l'œil. Le relief est le seul réglage du jeu que le joueur ne touche pas.
 */
enum class TerrainShape {
    /** Le terrain d'origine : plat du pied de la machine jusqu'au bout du monde. */
    PLAINE,

    /** Le croquis : des plateaux qui montent en escalier, un bâtiment par palier. */
    TERRASSES,

    /** L'inverse : le premier palier est le plus haut, et le site descend derrière. */
    GRADINS,

    /** Le site est à plat, mais une colline se dresse entre la machine et lui. */
    COLLINE,

    /** Un seul grand plateau, haut, abordé par une falaise verticale. */
    MESA,

    /** Le site est dans un creux : on ne voit que ses toits depuis la machine. */
    VALLON,

    /** Une crête sèche juste devant le site, comme un glacis de fortification. */
    CRETE
}

/** Un point de la ligne de profil. Le sol est la ligne brisée qui relie ces points. */
class TerrainNode(val x: Float, val y: Float)

/**
 * Une aire plate réservée à une construction : c'est là qu'un module a le droit de
 * se poser, et à cette altitude-là.
 *
 * Un bâtiment ne se bâtit **jamais** sur une pente. C'est la décision qui rend tout le
 * reste simple : le tassement, la validation, la maçonnerie et les tests continuent de
 * travailler sur un sol à zéro, et le relief se contente de soulever le résultat. Une
 * tour posée en travers d'un talus demanderait de tailler ses assises en biais, de
 * retasser sur un plan incliné, et de deviner si elle glisse — pour une image qu'on ne
 * regarderait de toute façon que trois secondes.
 */
class Pad(val left: Float, val right: Float, val y: Float) {
    val width: Float get() = right - left
    fun translated(dx: Float): Pad = Pad(left + dx, right + dx, y)
}

/** Le relief et les emplacements qu'il réserve, sortis du même tirage. */
class TerrainPlan(val terrain: Terrain, val pads: List<Pad>) {
    fun translated(dx: Float): TerrainPlan =
        TerrainPlan(terrain.translated(dx), pads.map { it.translated(dx) })
}

/**
 * Le sol, vu de profil : une ligne brisée, et les corps immobiles qui la rendent
 * solide.
 *
 * **Les segments se touchent exactement, et rien ne se chevauche à la surface.** Une
 * dalle plate va d'un nœud à l'autre ; une pente est une boîte tournée dont la face
 * supérieure est le segment lui-même, épaisse vers le bas. Deux voisines partagent
 * donc leur arête au millimètre : la surface est continue, un boulet qui la longe est
 * toujours en contact avec l'une ou l'autre, et il n'y a nulle part de joint par où
 * tomber. Une marche verticale — une falaise — n'est même pas un corps : c'est la
 * face latérale de la dalle du dessus.
 *
 * **Les dalles sont épaisses, et ce n'est pas de la coquetterie.** Le moteur règle ses
 * sous-pas sur la pièce la plus mince qu'un corps rapide peut atteindre
 * ([PhysWorld.subStepsFor]) : un sol découpé en lamelles de vingt centimètres ferait
 * découper chaque image en trente sous-pas **pendant tout le vol**, puisque le boulet
 * rase le relief d'un bout à l'autre du terrain. Elles descendent donc toutes jusqu'à
 * [DEPTH] mètres sous le point le plus bas du relief.
 */
class Terrain(nodes: List<TerrainNode>) {

    val nodes: List<TerrainNode> = simplify(nodes)

    val lowest: Float = if (this.nodes.isEmpty()) 0f else this.nodes.minOf { it.y }
    val highest: Float = if (this.nodes.isEmpty()) 0f else this.nodes.maxOf { it.y }

    /** Vrai quand le relief est plat partout : le jeu peut alors s'économiser du travail. */
    val flat: Boolean get() = highest - lowest < 1e-3f

    /**
     * L'altitude du sol en [x].
     *
     * Sur une marche verticale on rend la valeur **haute**. C'est la lecture juste pour
     * tout le monde : « à quelle hauteur est le sol ici » veut dire « où se poserait
     * quelque chose lâché ici », et au bord d'une falaise c'est le haut.
     */
    fun heightAt(x: Float): Float {
        val n = nodes
        if (n.isEmpty()) return 0f
        if (x <= n.first().x) return n.first().y
        if (x >= n.last().x) return n.last().y
        var lo = 0
        var hi = n.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) ushr 1
            if (n[mid].x <= x) lo = mid else hi = mid
        }
        val a = n[lo]
        val b = n[hi]
        val dx = b.x - a.x
        if (dx <= 1e-4f) return maxOf(a.y, b.y)
        return a.y + (b.y - a.y) * (x - a.x) / dx
    }

    /**
     * Le point le plus bas du sol entre [x0] et [x1].
     *
     * Exact et pas échantillonné : le profil est une ligne brisée, donc son minimum sur
     * un intervalle est soit à l'un des deux bords, soit sur un nœud compris entre les
     * deux. Il n'y a rien à balayer.
     *
     * C'est la vue qui s'en sert, à chaque image, pour savoir jusqu'où descendre son
     * cadrage. D'où le soin : un balayage au mètre coûterait mille appels par image sur
     * une vue large, pour un résultat moins juste.
     */
    fun lowestBetween(x0: Float, x1: Float): Float {
        val lo = minOf(x0, x1)
        val hi = maxOf(x0, x1)
        var best = minOf(heightAt(lo), heightAt(hi))
        for (n in nodes) {
            if (n.x <= lo) continue
            if (n.x >= hi) break
            if (n.y < best) best = n.y
        }
        return best
    }

    fun translated(dx: Float): Terrain =
        Terrain(nodes.map { TerrainNode(it.x + dx, it.y) })

    /**
     * Le même relief, prolongé à plat jusqu'aux bornes du monde.
     *
     * C'est ce qui donne son tablier à la machine : le premier nœud est toujours à
     * l'altitude zéro, donc le prolongement vers la gauche est plat et le trébuchet se
     * retrouve sur le sol qu'il a toujours connu.
     */
    fun extended(leftX: Float, rightX: Float): Terrain {
        if (nodes.isEmpty()) return flatBetween(leftX, rightX)
        val out = ArrayList<TerrainNode>(nodes.size + 2)
        if (leftX < nodes.first().x) out += TerrainNode(leftX, nodes.first().y)
        out += nodes
        if (rightX > nodes.last().x) out += TerrainNode(rightX, nodes.last().y)
        return Terrain(out)
    }

    /**
     * Les corps immobiles qui portent le monde.
     *
     * Une dalle par segment plat, une boîte tournée par pente, rien du tout pour une
     * falaise. Ils ne se touchent jamais entre eux — deux corps immobiles ne sont même
     * pas testés par le moteur — et leurs faces du dessous se recouvrent librement sous
     * terre, là où personne ne va.
     */
    fun bodies(friction: Float = 0.55f): List<PhysBody> {
        val out = ArrayList<PhysBody>(nodes.size)
        if (nodes.size < 2) return out
        val base = lowest - DEPTH
        for (i in 0 until nodes.size - 1) {
            val a = nodes[i]
            val b = nodes[i + 1]
            val dx = b.x - a.x
            // Une marche verticale n'est pas un corps : la dalle du dessus a déjà une
            // face latérale, et c'est elle, la falaise.
            if (dx <= 1e-4f) continue
            val dy = b.y - a.y
            if (abs(dy) <= 1e-4f) {
                val halfW = dx / 2f
                val halfH = (a.y - base) / 2f
                out += slab(a.x + halfW, base + halfH, halfW, halfH, 0f, friction)
            } else {
                // La face supérieure **est** le segment. On descend perpendiculairement
                // d'une demi-épaisseur pour trouver le centre de la boîte : c'est ce qui
                // garantit que les deux bouts de la pente tombent exactement sur les
                // nœuds, donc sur les arêtes de ses voisines.
                val len = hypot(dx, dy)
                val angle = atan2(dy, dx)
                val thickness = (a.y.coerceAtMost(b.y) - base).coerceAtLeast(DEPTH)
                val nx = -sin(angle)
                val ny = cos(angle)
                val cx = (a.x + b.x) / 2f - nx * thickness / 2f
                val cy = (a.y + b.y) / 2f - ny * thickness / 2f
                out += slab(cx, cy, len / 2f, thickness / 2f, angle, friction)
            }
        }
        return out
    }

    private fun slab(
        cx: Float, cy: Float, halfW: Float, halfH: Float, angle: Float, friction: Float
    ): PhysBody = PhysBody(halfW.coerceAtLeast(0.05f), halfH.coerceAtLeast(0.05f), 0f).apply {
        x = cx
        y = cy
        this.angle = angle
        lockPosition = true
        lockRotation = true
        this.friction = friction
        category = TrebuchetCategory.GROUND
        refreshMass()
    }

    companion object {

        /** Profondeur de terre sous le point le plus bas du relief, en mètres. */
        const val DEPTH = 30f

        /**
         * Le terrain d'origine : plat d'un bout à l'autre du monde.
         *
         * Un seul exemplaire pour tout le monde, et c'est sans risque : un relief est
         * immuable une fois construit. C'est aussi ce qui permet à
         * [Structure.problems] de le prendre par défaut sans en fabriquer un à chaque
         * appel — la validation en fait des milliers.
         */
        val FLAT: Terrain =
            flatBetween(TrebuchetRules.GROUND_LEFT, TrebuchetRules.GROUND_RIGHT)

        fun flatBetween(leftX: Float, rightX: Float): Terrain =
            Terrain(listOf(TerrainNode(leftX, 0f), TerrainNode(rightX, 0f)))

        /**
         * Retire les nœuds qui ne disent rien : ceux qui tombent dans l'alignement de
         * leurs deux voisins.
         *
         * Ce n'est pas de l'esthétique, c'est du budget. Un site de quatre plateaux à la
         * même altitude — une mesa, une plaine — décrirait sinon neuf segments plats
         * bout à bout, donc neuf corps immobiles à tester contre chaque pierre du site.
         * Simplifiés, il n'en reste qu'un.
         */
        private fun simplify(raw: List<TerrainNode>): List<TerrainNode> {
            if (raw.size < 3) return raw
            val out = ArrayList<TerrainNode>(raw.size)
            out += raw.first()
            for (i in 1 until raw.size - 1) {
                val a = out.last()
                val b = raw[i]
                val c = raw[i + 1]
                val dx1 = b.x - a.x
                val dx2 = c.x - b.x
                // Deux segments verticaux de suite, ou un point confondu avec le
                // précédent : rien à garder.
                if (dx1 <= 1e-4f && dx2 <= 1e-4f) continue
                if (dx1 > 1e-4f && dx2 > 1e-4f) {
                    val p1 = (b.y - a.y) / dx1
                    val p2 = (c.y - b.y) / dx2
                    if (abs(p1 - p2) < 1e-4f) continue
                }
                out += b
            }
            out += raw.last()
            return out
        }

        /** Marge plate laissée de chaque côté d'une construction, sur son plateau. */
        private val margin: Float get() = TargetRules.site(3f)

        /** Pente maximale d'un talus : au-delà, tout ce qui tombe dessus dévale. */
        private const val SLOPE = 0.55f

        /**
         * Longueur minimale d'un segment de sol, en mètres.
         *
         * Une dalle d'un mètre de long est une **lamelle** : le moteur règle ses
         * sous-pas sur la pièce la plus mince du monde, et une lamelle de terrain fait
         * découper chaque image en trente sous-pas sans que rien ne le dise. Tout
         * entre-deux du relief est donc au moins aussi long que ça.
         */
        private const val MIN_SEGMENT = 4f

        /**
         * En dessous de cette dénivelée, un raccord n'est qu'un cran à peine visible :
         * pas la peine de lui trouver la place d'un arrondi.
         */
        private const val ROUND_THRESHOLD = 1.5f

        /**
         * Dessine un relief qui porte [padWidths] constructions, la première commençant
         * à l'abscisse zéro.
         *
         * Le repère est **local** : le générateur pose son site autour de zéro, le
         * tasse, puis déplace le tout — relief compris — à la distance de tir. C'est ce
         * qui permet au site de tomber au mètre près sur sa distance annoncée sans que
         * les bâtiments glissent de leurs plateaux.
         *
         * [approach] est la longueur de terrain réservée **devant** le site, celle où
         * une colline a le droit de se dresser.
         */
        fun plan(
            rng: Random,
            shape: TerrainShape,
            padWidths: List<Float>,
            approach: Float
        ): TerrainPlan {
            if (padWidths.isEmpty()) {
                return TerrainPlan(flatBetween(-approach, 40f), emptyList())
            }
            // Le décor — bosses et creux — tire sur son propre flux, jamais sur [rng].
            // La même graine doit bâtir le même village qu'on dessine ses talus bien
            // droits ou qu'on les arrondisse : si le décor mangeait des tirages de
            // [rng], chaque bosse de plus décalerait tout ce que [TargetGenerator]
            // tire après coup — la forme des maisons y compris.
            val deco = run {
                var h = shape.ordinal.toLong() * 1_000_003L + approach.toRawBits()
                for (w in padWidths) h = h * 31L + w.toRawBits()
                Random(h)
            }
            val u = TargetRules.site(1f)
            val n = padWidths.size
            val ys = altitudes(rng, shape, n, u)
            val liens = FloatArray(n) { i ->
                linkLength(rng, shape, if (i == 0) 0f else ys[i - 1], ys[i], i)
            }

            // Le premier talus est **devant** l'origine : le premier plateau commence à
            // zéro, et tout ce qui le précède est de l'approche. On s'assure donc que
            // l'approche a de quoi le contenir — un talus plus long que l'approche
            // ferait remonter le relief sous les pieds de la machine.
            val approcheUtile = approach.coerceAtLeast(liens[0] + MIN_SEGMENT + margin)

            // Les plateaux, posés bout à bout de gauche à droite. Chacun déborde de sa
            // construction d'une marge plate : un bâtiment au ras d'une falaise
            // basculerait dans le vide au premier éclat.
            //
            // **On avance, on ne revient jamais en arrière.** La première version
            // plaçait le pied de chaque talus en reculant depuis le plateau qu'il
            // dessert — et quand la dénivelée était forte, ce pied atterrissait au
            // milieu du plateau précédent, voire au milieu de la construction posée
            // dessus. Le plateau n'était plus plat, et il n'y avait aucun moyen de s'en
            // apercevoir autrement qu'en mesurant.
            val pads = ArrayList<Pad>(n)
            val nodes = ArrayList<TerrainNode>()
            var x = -margin - liens[0]
            nodes += approachNodes(rng, deco, shape, -approcheUtile, x, u)
            nodes += TerrainNode(x, 0f)
            for (i in 0 until n) {
                val y = ys[i]
                val yAvant = if (i == 0) 0f else ys[i - 1]
                val xAvant = x
                // Le talus, ou la falaise : c'est le même geste, avec une longueur nulle.
                x += liens[i]
                nodes += smoothRamp(xAvant, yAvant, x, y)
                // Le plateau : la marge, la construction, la marge.
                val plateauLeft = x
                pads += Pad(plateauLeft + margin, plateauLeft + margin + padWidths[i], y)
                x = plateauLeft + margin + padWidths[i] + margin
                nodes += TerrainNode(x, y)
                // L'entre-deux : un bout de terrain plat avant de repartir vers le haut
                // ou vers le bas. Il ne sert à rien qu'à l'œil, et il suffit qu'il soit
                // assez long pour ne pas fabriquer une dalle en lamelle.
                if (i < n - 1) {
                    x += MIN_SEGMENT + rng.nextFloat() * TargetRules.site(6f)
                    nodes += TerrainNode(x, y)
                }
            }
            return TerrainPlan(Terrain(nodes), pads)
        }

        /**
         * Remplace l'angle vif d'un talus par une courbe douce : un ou deux nœuds
         * intermédiaires qui adoucissent les deux raccords à la fois — celui du bas et
         * celui du haut — puisqu'un lissage en S a une dérivée nulle à ses deux bouts.
         *
         * **On allonge avant de courber, on ne subdivise jamais ce qu'on a.** Un talus
         * pile à [MIN_SEGMENT] coupé en trois ferait des lamelles d'un mètre trente ;
         * [linkLength] lui a donc déjà réservé deux fois cette longueur avant qu'on
         * arrive ici. Ici, on se contente de vérifier qu'il y a la place, et de rendre
         * le segment tel quel sinon — un petit cran reste un petit cran.
         */
        private fun smoothRamp(x0: Float, y0: Float, x1: Float, y1: Float): List<TerrainNode> {
            val dx = x1 - x0
            val dy = y1 - y0
            if (dx < 2f * MIN_SEGMENT || abs(dy) < 1e-4f) return listOf(TerrainNode(x1, y1))
            val n = if (dx >= 3f * MIN_SEGMENT) 3 else 2
            return (1..n).map { i ->
                val t = i / n.toFloat()
                // Lissage en S (smoothstep) : plat aux deux bouts, penché au milieu.
                val s = t * t * (3f - 2f * t)
                TerrainNode(x0 + dx * t, y0 + dy * s)
            }
        }

        /**
         * Une bosse ou un creux léger sur un tronçon d'approche qui ne porte rien.
         * Jamais sur un plateau, jamais derrière le site non plus : au-delà du dernier
         * talus le boulet roule encore et longtemps une fois le site rasé, et une
         * bosse sur son chemin déciderait où il s'arrête — donc combien de poussière
         * elle soulève, donc jusqu'où le tirage du spectacle a dérivé au moment du feu
         * d'artifice. Un décor ne doit jamais se voir dans le jeu qu'il habille.
         *
         * Un seul sommet, jamais plus : la ligne brisée n'a pas besoin de dix
         * ondulations pour cesser d'être une droite, et chaque nœud de plus coûte une
         * dalle. Il part toujours d'une marge d'au moins [MIN_SEGMENT] de chaque côté
         * si la place le permet, et prend tout le tronçon sinon — jamais un bout plus
         * court que la garde-fou.
         */
        private fun bumpyFlat(rng: Random, x0: Float, x1: Float, y: Float, u: Float): List<TerrainNode> {
            val run = x1 - x0
            if (run < 2f * MIN_SEGMENT) return emptyList()
            if (rng.nextFloat() < 0.35f) return emptyList()
            val rawMargin = (run - 2f * MIN_SEGMENT) / 2f
            val margin = if (rawMargin >= MIN_SEGMENT) rawMargin.coerceAtMost(MIN_SEGMENT * 1.5f) else 0f
            val start = x0 + margin
            val end = x1 - margin
            val span = end - start
            val minFrac = (MIN_SEGMENT / span).coerceAtMost(0.5f)
            val peakFrac = minFrac + rng.nextFloat() * (1f - 2f * minFrac)
            val peak = start + span * peakFrac
            val amp = (0.4f + rng.nextFloat() * 0.8f) * u * if (rng.nextBoolean()) 1f else -1f
            val out = ArrayList<TerrainNode>(2)
            if (margin > 1e-3f) out += TerrainNode(start, y)
            out += TerrainNode(peak, y + amp)
            return out
        }

        /**
         * L'altitude de chaque plateau, en mètres, selon le relief demandé.
         *
         * Les hauteurs passent par [TargetRules.site] : en mode arcade les bâtiments
         * font le double, et un escalier aux marches inchangées serait devenu une
         * rampe d'accès. Le relief doit grandir avec ce qu'il porte.
         */
        private fun altitudes(rng: Random, shape: TerrainShape, n: Int, u: Float): FloatArray {
            fun between(a: Float, b: Float) = (a + rng.nextFloat() * (b - a)) * u
            val ys = FloatArray(n)
            // On tire une **dénivelée totale**, qu'on répartit ensuite sur les marches,
            // et non une hauteur de marche qu'on empilerait.
            //
            // La différence n'a l'air de rien et elle décide de tout : une marche de
            // cinq mètres tirée quatre fois de suite fait vingt mètres, soit deux fois
            // la plus haute construction du jeu, et le site quitte l'écran par le haut.
            // Un escalier se dessine par sa hauteur d'ensemble — celle qu'on voit — et
            // le nombre de marches n'est qu'un détail de découpage.
            val marches = (n - 1).coerceAtLeast(1)
            when (shape) {
                TerrainShape.PLAINE, TerrainShape.COLLINE -> {
                    // Le site est à plat : tout se joue dans l'approche.
                    for (i in 0 until n) ys[i] = 0f
                }

                TerrainShape.TERRASSES -> {
                    val bas = between(0f, 2f)
                    val montee = between(6f, 13f)
                    for (i in 0 until n) ys[i] = bas + montee * i / marches
                }

                TerrainShape.GRADINS -> {
                    val haut = between(6f, 12f)
                    for (i in 0 until n) ys[i] = haut * (1f - i.toFloat() / marches)
                }

                TerrainShape.MESA -> {
                    val h = between(6f, 11f)
                    for (i in 0 until n) ys[i] = h
                }

                TerrainShape.VALLON -> {
                    val creux = -between(3f, 6f)
                    for (i in 0 until n) ys[i] = creux
                    // Le dernier palier remonte : le fond du vallon a une rive.
                    if (n > 1) ys[n - 1] = between(0f, 2f)
                }

                TerrainShape.CRETE -> {
                    val bas = between(0.5f, 2f)
                    val montee = between(1.5f, 4f)
                    for (i in 0 until n) ys[i] = bas + montee * i / marches
                }
            }
            return ys
        }

        /**
         * La longueur du raccord entre deux plateaux : un talus, ou rien du tout.
         *
         * Zéro veut dire **falaise** — une marche verticale, que le générateur tire
         * volontiers quand la dénivelée est franche. C'est la silhouette la plus lisible
         * du lot : on voit immédiatement que la construction du dessus est hors
         * d'atteinte d'un tir rasant.
         */
        private fun linkLength(
            rng: Random,
            shape: TerrainShape,
            from: Float,
            to: Float,
            index: Int
        ): Float {
            val dy = abs(to - from)
            if (dy < 0.05f) return maxOf(MIN_SEGMENT, TargetRules.site(2f))
            // Une mesa s'aborde toujours par sa falaise, et c'est tout son propos.
            if (shape == TerrainShape.MESA && index == 0) return 0f
            // Ailleurs, une marche franche sur trois est verticale.
            if (dy > TargetRules.site(3f) && rng.nextFloat() < 0.3f) return 0f
            // Un talus a le droit d'être plus doux que la pente maximale, jamais plus
            // court que [MIN_SEGMENT] : une marche de cinquante centimètres donnerait
            // une rampe d'un mètre de long, c'est-à-dire une lamelle. Elle se rattrape
            // en pente douce, ce qui ne se voit même pas.
            val base = (dy / SLOPE).coerceAtLeast(MIN_SEGMENT)
            // Une dénivelée qui se voit mérite un raccord arrondi ([smoothRamp]), et un
            // arrondi a besoin de place : deux [MIN_SEGMENT] au moins, pour ne jamais
            // avoir à subdiviser sous la garde-fou. En dessous du seuil, le cran reste
            // un cran — ça ne vaut pas la peine de l'étirer pour si peu.
            return if (dy >= ROUND_THRESHOLD) base.coerceAtLeast(2f * MIN_SEGMENT) else base
        }

        /**
         * Le terrain **devant** le site, de la machine au pied du premier plateau.
         *
         * C'est là que se joue la colline, et c'est le seul relief que le joueur voie
         * pendant qu'il règle sa machine. Les nœuds rendus vont de [from] jusqu'à
         * [to] exclu — le pied du premier talus, qui est toujours à l'altitude zéro.
         */
        private fun approachNodes(
            rng: Random,
            deco: Random,
            shape: TerrainShape,
            from: Float,
            to: Float,
            u: Float
        ): List<TerrainNode> {
            val out = ArrayList<TerrainNode>()
            out += TerrainNode(from, 0f)
            val run = to - from
            when (shape) {
                TerrainShape.COLLINE -> {
                    // Une colline large, plantée aux deux tiers du chemin. Il faut la
                    // franchir : un tir rasant vient mourir dedans, et c'est le seul
                    // relief du jeu qui interdise vraiment quelque chose.
                    if (run < 12f * MIN_SEGMENT) return out
                    val h = (6f + rng.nextFloat() * 6f) * u
                    val sommet = from + run * (0.5f + rng.nextFloat() * 0.15f)
                    // La colline ne touche jamais les bords de l'approche : ce qui reste
                    // de plat de chaque côté est un vrai bout de terrain, pas une
                    // lamelle de vingt centimètres.
                    val place = minOf(sommet - from, to - sommet) - MIN_SEGMENT
                    val demi = minOf(run * (0.225f + rng.nextFloat() * 0.1f), place)
                    if (demi < 3f * MIN_SEGMENT) return out
                    out += TerrainNode(sommet - demi, 0f)
                    out += TerrainNode(sommet - demi * 0.35f, h)
                    out += TerrainNode(sommet + demi * 0.35f, h)
                    out += TerrainNode(sommet + demi, 0f)
                }

                TerrainShape.CRETE -> {
                    // Une crête sèche, juste devant le site : un glacis. Elle ne cache
                    // pas la cible, elle mange les tirs qui rasent le sol.
                    //
                    // Tout se mesure en **parts de l'approche** et non en mètres : c'est
                    // la seule façon de garantir qu'une crête tient dans la place qu'on
                    // lui laisse, quelle que soit cette place.
                    if (run < 10f * MIN_SEGMENT) return out
                    val h = (4f + rng.nextFloat() * 4f) * u
                    val largeur = run * (0.2f + rng.nextFloat() * 0.12f)
                    val dos = maxOf(MIN_SEGMENT, largeur * 0.3f)
                    val pied = to - maxOf(MIN_SEGMENT, run * (0.06f + rng.nextFloat() * 0.1f))
                    out += TerrainNode(pied - largeur, 0f)
                    out += TerrainNode(pied - dos, h)
                    out += TerrainNode(pied, 0f)
                }

                // Rien à dessiner : juste une bosse ou un creux léger, comme partout
                // ailleurs où le terrain ne porte rien.
                else -> out += bumpyFlat(deco, from, to, 0f, u)
            }
            return out
        }
    }
}
