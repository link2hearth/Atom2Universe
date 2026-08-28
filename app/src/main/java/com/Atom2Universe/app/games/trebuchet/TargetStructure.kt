package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.Shape
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ce qu'un bloc est venu faire dans la construction.
 *
 * Le rôle ne change rien à la physique sauf pour le socle ; il sert à la génération
 * (où poser un objectif), à la vue (quoi dessiner), et au décompte.
 */
enum class Role {
    /** Le gros œuvre : murs, assises, poteaux. */
    STRUCTURE,

    /**
     * Une pierre de fondation, scellée au sol : elle ne glisse ni ne bascule.
     *
     * À manier avec précaution. Un socle est indéboulonnable **par construction**,
     * donc s'il dépasse la ligne de ruine le niveau devient impossible. C'est
     * exactement ce que [Structure.problems] vérifie.
     */
    FOUNDATION,

    /** Le décor mobile : tonneaux, caisses, rochers. Ça roule et ça emporte le reste. */
    PROP,

    /** La pièce à abattre quand le niveau demande une cible précise. */
    OBJECTIVE
}

/**
 * Une forme d'un bloc, dans le repère du bloc.
 *
 * C'est le calque de [com.Atom2Universe.app.games.physics.BodyPart], mais du côté
 * description : la génération n'a pas le droit de toucher au moteur, elle décrit des
 * mètres et rien d'autre. C'est ce qui rend un niveau testable sans monde physique,
 * rejouable à partir d'une graine, et dessinable avant d'exister.
 */
class Piece(
    val shape: Shape,
    val halfW: Float,
    val halfH: Float,
    val localX: Float,
    val localY: Float,
    val localAngle: Float
) {
    val radius: Float get() = halfW

    val area: Float
        get() = if (shape == Shape.CIRCLE) (PI * radius * radius).toFloat() else 4f * halfW * halfH

    /** De combien le sommet de cette forme dépasse le centre du bloc, celui-ci tourné de [angle]. */
    fun topAbove(angle: Float): Float {
        val py = localX * sin(angle) + localY * cos(angle)
        return py + if (shape == Shape.CIRCLE) {
            radius
        } else {
            val a = angle + localAngle
            halfW * abs(sin(a)) + halfH * abs(cos(a))
        }
    }

    /** De combien cette forme s'écarte du centre du bloc en largeur, celui-ci tourné de [angle]. */
    fun reachAside(angle: Float): Float {
        val px = localX * cos(angle) - localY * sin(angle)
        return abs(px) + if (shape == Shape.CIRCLE) {
            radius
        } else {
            val a = angle + localAngle
            halfW * abs(cos(a)) + halfH * abs(sin(a))
        }
    }
}

/** Assemble les formes d'un bloc composé, dans un repère libre. */
class PieceBuilder internal constructor() {
    internal val pieces = ArrayList<Piece>()

    fun box(halfW: Float, halfH: Float, x: Float = 0f, y: Float = 0f, angle: Float = 0f) {
        pieces += Piece(
            Shape.BOX,
            halfW.coerceAtLeast(TargetRules.MIN_HALF_THICKNESS),
            halfH.coerceAtLeast(TargetRules.MIN_HALF_THICKNESS),
            x, y, angle
        )
    }

    fun circle(radius: Float, x: Float = 0f, y: Float = 0f) {
        val r = radius.coerceAtLeast(TargetRules.MIN_HALF_THICKNESS)
        pieces += Piece(Shape.CIRCLE, r, r, x, y, 0f)
    }
}

/**
 * Une pierre de la construction : une ou plusieurs formes solidaires, d'un matériau,
 * posée quelque part.
 *
 * **Le bloc composé est aussi le plan de fracture.** C'est l'idée qui tient tout le
 * système. Une assise de tour faite de trois boîtes — deux chaînages d'angle et un
 * remplissage — se lit comme trois pierres, ne coûte qu'un seul corps au moteur, ne
 * tremble jamais (des formes d'un même corps sont parfaitement rigides), et quand
 * elle rompt, on sait déjà en quoi la casser : en ses propres parties. Le détail est
 * gratuit tant que le joueur ne l'a pas mérité, et il apparaît au moment exact où il
 * le mérite.
 *
 * [x], [y] désignent le **centre de masse**, recalculé à la construction comme le
 * moteur le fait de son côté : les deux repères coïncident donc toujours.
 */
class Block private constructor(
    val parts: List<Piece>,
    val x: Float,
    val y: Float,
    val angle: Float,
    val material: Material,
    val role: Role
) {

    val area: Float = parts.sumOf { it.area.toDouble() }.toFloat()

    /**
     * Le monde est plat mais les pierres sont épaisses : on leur suppose 1 m.
     * Le tempérament du jeu ([TargetRules.style]) allège la matière en mode arcade.
     */
    val mass: Float = material.density * TargetRules.style.densityScale * area

    /**
     * Points de vie, en joules — la même unité que l'énergie de choc du moteur.
     *
     * C'est l'énergie cinétique qu'aurait ce bloc lancé à la vitesse critique de son
     * matériau : ce qu'il faut lui rendre pour le briser.
     */
    val hp: Float = run {
        val vc = material.criticalSpeed * TargetRules.style.toughnessScale
        0.5f * mass * vc * vc
    }

    /** Hauteur du sommet du bloc, tel qu'il est posé. */
    fun top(): Float {
        var best = -Float.MAX_VALUE
        for (p in parts) best = maxOf(best, p.topAbove(angle))
        return y + best
    }

    /** Hauteur du point le plus bas du bloc. */
    fun bottom(): Float {
        var best = -Float.MAX_VALUE
        for (p in parts) {
            val mirrored = Piece(p.shape, p.halfW, p.halfH, -p.localX, -p.localY, p.localAngle)
            best = maxOf(best, mirrored.topAbove(angle))
        }
        return y - best
    }

    /** Demi-largeur de la boîte englobante alignée sur les axes. */
    fun halfSpan(): Float {
        var best = 0f
        for (p in parts) best = maxOf(best, p.reachAside(angle))
        return best
    }

    /** Demi-hauteur de la boîte englobante alignée sur les axes. */
    fun halfHeight(): Float = top() - y

    /**
     * Épaisseur de ce que ce bloc laisse au sol une fois brisé.
     *
     * Ce n'est pas son épaisseur à lui : un bloc composé de quatre assises fait deux
     * mètres d'épaisseur tant qu'il est entier, et cinquante centimètres dès qu'il ne
     * l'est plus, puisqu'il éclate en ses assises. C'est cette seconde valeur qui décide
     * de la hauteur du tas de gravats, donc de la faisabilité du niveau.
     */
    fun rubbleThickness(): Float {
        var worst = 0f
        for (p in parts) {
            val t = if (p.shape == Shape.CIRCLE) 2f * p.radius
            else minOf(2f * p.halfW, 2f * p.halfH)
            if (t > worst) worst = t
        }
        return worst
    }

    fun translated(dx: Float, dy: Float = 0f): Block =
        Block(parts, x + dx, y + dy, angle, material, role)

    /** La même pierre, posée ailleurs. Sert au tassement, qui réécrit toutes les poses. */
    fun posed(x: Float, y: Float, angle: Float): Block =
        Block(parts, x, y, angle, material, role)

    companion object {

        /** Recentre les formes sur leur centre de masse, exactement comme le moteur. */
        private fun centered(raw: List<Piece>): Triple<List<Piece>, Float, Float> {
            var total = 0f
            var cx = 0f
            var cy = 0f
            for (p in raw) {
                total += p.area
                cx += p.area * p.localX
                cy += p.area * p.localY
            }
            if (total > 0f) {
                cx /= total
                cy /= total
            }
            val moved = raw.map {
                Piece(it.shape, it.halfW, it.halfH, it.localX - cx, it.localY - cy, it.localAngle)
            }
            return Triple(moved, cx, cy)
        }

        /** Une pierre d'un seul tenant, décrite par son centre et ses demi-dimensions. */
        fun box(
            material: Material,
            cx: Float,
            cy: Float,
            halfW: Float,
            halfH: Float,
            angle: Float = 0f,
            role: Role = Role.STRUCTURE
        ): Block = Block(
            listOf(
                Piece(
                    Shape.BOX,
                    halfW.coerceAtLeast(TargetRules.MIN_HALF_THICKNESS),
                    halfH.coerceAtLeast(TargetRules.MIN_HALF_THICKNESS),
                    0f, 0f, 0f
                )
            ),
            cx, cy, angle, material, role
        )

        /** Une pierre posée par son **coin bas-gauche** : c'est ainsi qu'on maçonne. */
        fun laid(
            material: Material,
            left: Float,
            bottom: Float,
            width: Float,
            height: Float,
            role: Role = Role.STRUCTURE
        ): Block =
            box(material, left + width / 2f, bottom + height / 2f, width / 2f, height / 2f, 0f, role)

        fun circle(
            material: Material,
            cx: Float,
            cy: Float,
            radius: Float,
            role: Role = Role.PROP
        ): Block = Block(
            listOf(
                Piece(
                    Shape.CIRCLE,
                    radius.coerceAtLeast(TargetRules.MIN_HALF_THICKNESS),
                    radius.coerceAtLeast(TargetRules.MIN_HALF_THICKNESS),
                    0f, 0f, 0f
                )
            ),
            cx, cy, 0f, material, role
        )

        /**
         * Un bloc de plusieurs formes. Les formes se décrivent dans un repère libre
         * dont l'origine tombera en ([cx], [cy]) une fois le centre de masse recalé —
         * autrement dit, on dessine la pierre autour de zéro et on la pose ensuite.
         */
        fun compound(
            material: Material,
            cx: Float,
            cy: Float,
            angle: Float = 0f,
            role: Role = Role.STRUCTURE,
            build: PieceBuilder.() -> Unit
        ): Block {
            val b = PieceBuilder().apply(build)
            require(b.pieces.isNotEmpty()) { "un bloc composé a besoin d'au moins une forme" }
            val (moved, ox, oy) = centered(b.pieces)
            // Le repère libre s'est déplacé de son centre de masse : on rend ce
            // décalage au bloc, pour que ce que l'auteur a dessiné autour de zéro
            // atterrisse bien en (cx, cy).
            val c = cos(angle)
            val s = sin(angle)
            return Block(moved, cx + ox * c - oy * s, cy + ox * s + oy * c, angle, material, role)
        }
    }
}

/**
 * Une construction entière : une liste de pierres, et de quoi la juger.
 *
 * Elle ne connaît ni le moteur ni le jeu. C'est une description, et c'est tout son
 * intérêt : on peut la fabriquer, la mesurer, la valider et la comparer sans avoir
 * simulé quoi que ce soit.
 */
class Structure(val blocks: List<Block>, val name: String = "") {

    /** Hauteur d'origine de la silhouette : c'est elle dont l'objectif est un cinquième. */
    val baseHeight: Float = if (blocks.isEmpty()) 0f else blocks.maxOf { it.top() }

    val left: Float = if (blocks.isEmpty()) 0f else blocks.minOf { it.x - it.halfSpan() }
    val right: Float = if (blocks.isEmpty()) 0f else blocks.maxOf { it.x + it.halfSpan() }
    val width: Float get() = right - left

    val totalMass: Float = blocks.sumOf { it.mass.toDouble() }.toFloat()

    /** Ligne sous laquelle tout doit être descendu pour que ce soit rasé. */
    val ruinLine: Float get() = TargetRules.RUIN_RATIO * baseHeight

    fun translated(dx: Float, dy: Float = 0f): Structure =
        Structure(blocks.map { it.translated(dx, dy) }, name)

    /**
     * La pile la plus profonde de la construction : combien de corps se superposent au
     * pire endroit.
     *
     * C'est **le** chiffre qui décide si une construction tiendra debout, et ce n'est
     * pas sa hauteur. Le solveur propage l'effort d'un contact à la fois ; passé une
     * quinzaine de contacts superposés, la pierre du bas n'apprend jamais ce qu'elle
     * porte et la pile s'affaisse. La mesure se fait en balayant l'emprise et en
     * comptant, à chaque abscisse, les blocs qui la traversent.
     */
    fun deepestStack(step: Float = 0.25f): Int {
        if (blocks.isEmpty()) return 0
        var worst = 0
        var x = left + step / 2f
        while (x < right) {
            var n = 0
            for (b in blocks) if (abs(b.x - x) <= b.halfSpan()) n++
            if (n > worst) worst = n
            x += step
        }
        return worst
    }

    /**
     * Ce qui cloche dans la construction, en clair.
     *
     * Rendre une liste plutôt que lever une exception est délibéré : le générateur
     * doit pouvoir **essayer** une graine, voir ce qui ne va pas, et recommencer. Une
     * construction n'est pas fausse, elle est ratée, et ça se rattrape.
     */
    fun problems(): List<String> {
        val out = ArrayList<String>()
        if (blocks.isEmpty()) {
            out += "construction vide"
            return out
        }
        if (blocks.size > TargetRules.BODY_BUDGET) {
            out += "trop de corps : ${blocks.size} pour un budget de ${TargetRules.BODY_BUDGET}"
        }
        val depth = deepestStack()
        if (depth > TargetRules.MAX_STACKED_BODIES) {
            out += "pile de $depth corps superposés : au-delà de " +
                "${TargetRules.MAX_STACKED_BODIES} elle s'écroule toute seule, " +
                "regrouper des pierres en blocs composés"
        }

        // Le cas où le niveau est **certainement** impossible : une seule pierre
        // couchée à plat dépasse déjà la ligne de ruine, et rien ne pourra jamais la
        // faire descendre.
        //
        // Ce garde-fou est volontairement minimal. La vraie question — « ce tas de
        // gravats-là peut-il passer sous cette ligne-là » — dépend de la façon dont la
        // construction s'écroule, donc d'une simulation, pas d'une formule. C'est au
        // générateur de la trancher en rasant le niveau pour voir. Une formule plus
        // ambitieuse a été essayée ici et refusait des courtines parfaitement jouables
        // à cause d'un merlon un peu large.
        //
        // Seules les pierres qui **laissent** quelque chose comptent : ce qui tombe en
        // poussière — le torchis d'un hourdis, le chaume d'un toit — disparaît sans
        // rien poser au sol, et un hourdis d'arcade un peu haut faisait refuser des
        // hameaux parfaitement jouables.
        val thickest = blocks
            .filter { it.material.rupture != Rupture.POUSSIERE }
            .maxOfOrNull { it.rubbleThickness() } ?: 0f
        if (ruinLine < thickest) {
            out += "construction trop basse pour ses pierres : ligne de ruine à " +
                "${"%.2f".format(ruinLine)} m, une seule pierre couchée en fait " +
                "${"%.2f".format(thickest)}"
        }
        for ((i, b) in blocks.withIndex()) {
            if (b.bottom() < -0.05f) out += "bloc $i enterré"
            if (b.role == Role.FOUNDATION && b.top() > ruinLine) {
                out += "socle $i au-dessus de la ligne de ruine : le niveau serait impossible"
            }
            for (p in b.parts) {
                if (p.halfW < TargetRules.MIN_HALF_THICKNESS ||
                    p.halfH < TargetRules.MIN_HALF_THICKNESS
                ) {
                    out += "bloc $i trop mince : il ferait ramer toute la simulation"
                }
            }
        }
        // Chevauchement : on ne le teste que sur les blocs droits, dont la boîte
        // englobante **est** la forme. Une pièce inclinée a une englobante bien plus
        // grande qu'elle, et deux planches en A se chevaucheraient sans se toucher.
        val upright = blocks.withIndex().filter { (_, b) -> abs(b.angle) < 1e-3f && b.parts.size == 1 }
        for (i in upright.indices) {
            val (ia, a) = upright[i]
            for (j in i + 1 until upright.size) {
                val (ib, bb) = upright[j]
                val dx = abs(a.x - bb.x) - (a.halfSpan() + bb.halfSpan())
                val dy = abs(a.y - bb.y) - (a.halfHeight() + bb.halfHeight())
                if (dx < -0.02f && dy < -0.02f) out += "blocs $ia et $ib se chevauchent"
            }
        }
        return out
    }
}
