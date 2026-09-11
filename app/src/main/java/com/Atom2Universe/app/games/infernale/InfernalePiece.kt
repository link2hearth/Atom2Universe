package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.Joint
import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.RevoluteJoint
import com.Atom2Universe.app.games.physics.SpringJoint

/**
 * Ce qui tient une pièce en place — et c'est la question qui structure tout le jeu.
 *
 * Une pièce n'est pas un corps, c'est un **petit assemblage**. Le moteur ne connaît que
 * des corps et des liaisons ; ce qui distingue une rampe d'un domino, ce n'est pas leur
 * forme, c'est ce qui les retient.
 */
enum class Ancrage {
    /**
     * Scellée au fond : rien ne la déplace, jamais. Elle peut tenir en l'air sans
     * support, ce qui est précisément l'intérêt d'une rampe.
     *
     * Dans le moteur : un corps de masse nulle avec `lockPosition` et `lockRotation`.
     */
    SCELLE,

    /**
     * Libre : elle tombe, elle roule, elle se renverse. Un domino n'est intéressant que
     * parce qu'il peut tomber, donc il ne tient que par le sol sur lequel on le pose.
     */
    LIBRE,

    /**
     * **Un pied scellé, et le reste qui bouge autour.** C'est la bascule : le pied est
     * collé au fond, la planche ne l'est pas, et c'est de cet écart que vient tout le
     * mécanisme.
     *
     * Dans le moteur : deux corps, l'un immobile, l'autre libre, tenus ensemble par une
     * liaison pivot. Rien d'autre à inventer — clouer un corps au décor, c'est
     * exactement le prendre en pivot sur une masse immobile.
     */
    MIXTE
}

/**
 * Les pièces que le joueur peut poser. Volontairement court : la difficulté d'une
 * machine infernale ne vient pas du nombre de pièces disponibles mais du nombre qu'on
 * vous en donne. Six entrées se lisent d'un coup d'œil ; cent vingt-six sont un mur.
 */
enum class TypePiece(val ancrage: Ancrage) {
    /** Planche inclinée, scellée : elle guide la bille. */
    RAMPE(Ancrage.SCELLE),

    /** Plot rond et rebondissant, scellé : la bille repart ailleurs. */
    PLOT(Ancrage.SCELLE),

    /** Mur ou butée, scellé : il barre ou renvoie. */
    BLOC(Ancrage.SCELLE),

    /** Tombe quand on le pousse, et pousse le suivant. */
    DOMINO(Ancrage.LIBRE),

    /** Pied scellé, planche libre : ce qui tombe d'un côté soulève l'autre. */
    BASCULE(Ancrage.MIXTE),

    /** Socle scellé, volet sur ressort : ce qui tombe dessus repart en l'air. */
    TREMPLIN(Ancrage.MIXTE)
}

/**
 * Une pièce posée sur le tableau : ses corps, ses liaisons, et de quoi la reprendre.
 *
 * Les corps portent tous cette pièce comme propriétaire, ce qui rend le retrait exact :
 * on retire **ses** corps, jamais on ne vide le monde — un tableau contient d'autres
 * pièces, la bille et le bouton, qui n'ont pas à souffrir d'un déplacement.
 */
class Piece internal constructor(
    val type: TypePiece,
    val corps: List<PhysBody>,
    val liaisons: List<Joint>
) {
    init {
        for (c in corps) {
            c.owner = this
            // Ce qui bouge se signale : une zone de detection ne veut voir ni le sol, ni
            // les murs, ni le pied scelle d'une bascule. Marquer la categorie ici plutot
            // que dans chaque fabrique garantit qu'aucune piece ne l'oubliera.
            if (!c.immovable) c.category = Plateau.MOBILE
        }
    }

    val ancrage: Ancrage get() = type.ancrage

    /** Les corps collés au fond. Vide pour un domino, tout pour une rampe. */
    val scelles: List<PhysBody> get() = corps.filter { it.immovable }

    /** Les corps qui bougent. Vide pour une rampe, tout pour un domino. */
    val mobiles: List<PhysBody> get() = corps.filter { !it.immovable }

    /** Le corps principal, celui qu'on désigne quand on parle de la pièce. */
    val principal: PhysBody get() = corps.first()

    fun poser(monde: PhysWorld) {
        for (c in corps) monde.add(c)
        for (l in liaisons) monde.addJoint(l)
    }

    /** Reprend la pièce sans toucher au reste du tableau. */
    fun retirer(monde: PhysWorld) {
        monde.removeOwned(this)
    }
}

/**
 * L'atelier : une fabrique par pièce, en mètres et en kilogrammes comme tout le moteur.
 *
 * Les cotes sont ici et nulle part ailleurs, pour qu'essayer autre chose tienne en une
 * ligne à changer.
 */
object Pieces {

    /** Frottement commun : du bois sur du bois, ça accroche. */
    private const val FROTTEMENT = 0.6f

    private fun scelle(corps: PhysBody): PhysBody = corps.apply {
        lockPosition = true
        lockRotation = true
        refreshMass()
    }

    /**
     * Planche inclinée scellée. [pente] en degrés, **positive = descend vers la droite**,
     * ce qui est le sens où on lit une pente.
     *
     * L'angle du moteur, lui, tourne dans l'autre sens : les y montent, donc un angle
     * positif lève le bout droit. D'où le signe moins, une fois ici plutôt qu'à chaque
     * appel — sinon chaque rampe posée dans le jeu devrait le refaire, et l'une d'elles
     * l'oublierait.
     */
    fun rampe(x: Float, y: Float, pente: Float = 20f, longueur: Float = 1.5f): Piece {
        val planche = PhysBody(longueur / 2f, 0.04f, 0f).apply {
            this.x = x
            this.y = y
            angle = -Math.toRadians(pente.toDouble()).toFloat()
            friction = 0.35f
            restitution = 0.05f
        }
        return Piece(TypePiece.RAMPE, listOf(scelle(planche)), emptyList())
    }

    /** Plot rond et rebondissant, scellé au fond. */
    fun plot(x: Float, y: Float, rayon: Float = 0.15f): Piece {
        val disque = PhysBody.circle(rayon, 0f).apply {
            this.x = x
            this.y = y
            friction = 0.2f
            restitution = 0.75f
        }
        return Piece(TypePiece.PLOT, listOf(scelle(disque)), emptyList())
    }

    /** Mur scellé. */
    fun bloc(x: Float, y: Float, largeur: Float = 0.4f, hauteur: Float = 0.4f): Piece {
        val mur = PhysBody(largeur / 2f, hauteur / 2f, 0f).apply {
            this.x = x
            this.y = y
            friction = FROTTEMENT
            restitution = 0.05f
        }
        return Piece(TypePiece.BLOC, listOf(scelle(mur)), emptyList())
    }

    /**
     * Domino libre. [y] est le **bas** de la pièce : on pose un domino sur un sol, on ne
     * calcule pas son centre.
     *
     * Il est volontairement gros. Les petits corps qui se touchent sont le pire cas du
     * solveur, et une rangée de trente dominos fins coûte plus cher qu'un château —
     * huit gros dominos font le même effet pour une fraction du prix.
     */
    fun domino(x: Float, bas: Float, hauteur: Float = 0.44f, epaisseur: Float = 0.08f): Piece {
        val piece = PhysBody(epaisseur / 2f, hauteur / 2f, 0.5f).apply {
            this.x = x
            this.y = bas + hauteur / 2f
            friction = FROTTEMENT
            restitution = 0f
        }
        return Piece(TypePiece.DOMINO, listOf(piece), emptyList())
    }

    /**
     * Bascule : **le pied est collé, la planche ne l'est pas.**
     *
     * C'est l'exemple qui justifie [Ancrage.MIXTE] à lui seul. Le pied est un corps
     * immobile ; la planche est un corps libre qui lui est cloué en un point par un
     * pivot. Ce qui tombe d'un côté fait monter l'autre, et rien dans le moteur n'a eu
     * besoin d'apprendre le mot « bascule ».
     *
     * [y] est le bas du pied.
     */
    fun bascule(x: Float, bas: Float, longueur: Float = 1.2f, hauteurPied: Float = 0.3f): Piece {
        val pied = scelle(PhysBody(0.07f, hauteurPied / 2f, 0f).apply {
            this.x = x
            this.y = bas + hauteurPied / 2f
            friction = FROTTEMENT
        })
        val sommet = bas + hauteurPied
        val planche = PhysBody(longueur / 2f, 0.04f, 1.2f).apply {
            this.x = x
            this.y = sommet + 0.04f
            friction = FROTTEMENT
            restitution = 0.05f
            // La planche et son pied se chevauchent au pivot : ils ne doivent pas se
            // repousser. C'est ce que fait déjà `collideConnected = false` par défaut.
        }
        val pivot = RevoluteJoint.pin(pied, planche, x, sommet)
        return Piece(TypePiece.BASCULE, listOf(pied, planche), listOf(pivot))
    }

    /**
     * Tremplin : socle scellé, volet articulé, et un ressort qui le remonte.
     *
     * ## Le réglage qui compte est [hauteur], pas [raideur]
     *
     * C'est contre-intuitif, et c'est mesuré. Une bille lâchée de 1,5 m repart à :
     *
     * | hauteur de charnière | 300 N/m | 900 N/m | 2500 N/m |
     * |---|---|---|---|
     * | 18 cm | 23 % | 37 % | 52 % |
     * | 35 cm | 14 % | **61 %** | 63 % |
     * | 50 cm | 7 % | 65 % | 62 % |
     *
     * Deux choses se lisent là-dedans. D'abord **la course fait plus que la raideur** :
     * à raideur égale, monter la charnière de 18 à 35 cm fait passer de 37 % à 61 %.
     * Ensuite, un ressort trop mou pour la hauteur **talonne** — il touche le sol avant
     * d'avoir rien stocké, et le tremplin devient une planche (7 %).
     *
     * La raison tient au moteur : la perte d'un ressort vaut à peu près `(π/2)·ω·h`, avec
     * `ω = √(k/m)` et `h` le sous-pas. Un ressort raide et court a un `ω` énorme, donc
     * perd énormément ; un ressort doux et long a le même travail à fournir avec un `ω`
     * bien plus faible. C'est aussi ce que fait un vrai trampoline : il est **grand**.
     *
     * Au-delà d'environ 65 % ça plafonne, et c'est le plafond du solveur, pas du réglage.
     * Un tremplin rend donc les deux tiers de ce qu'on lui donne : c'est net à l'œil et
     * ça suffit largement à relancer une bille.
     */
    fun tremplin(
        x: Float,
        bas: Float,
        longueur: Float = 0.7f,
        raideur: Float = 900f,
        masseVolet: Float = 0.3f,
        amortissement: Float = 1f,
        hauteur: Float = 0.35f
    ): Piece {
        val socle = scelle(PhysBody(0.08f, hauteur / 2f, 0f).apply {
            this.x = x - longueur / 2f
            this.y = bas + hauteur / 2f
            friction = FROTTEMENT
        })
        val charniere = bas + hauteur
        val volet = PhysBody(longueur / 2f, 0.035f, masseVolet).apply {
            this.x = x
            this.y = charniere
            friction = 0.3f
            restitution = 0.1f
        }
        val pivot = RevoluteJoint.pin(socle, volet, x - longueur / 2f, charniere)

        // Le pied du ressort est un second corps scellé, sous l'extrémité libre : c'est
        // lui qui donne au ressort un point d'appui qui ne bouge pas.
        val appui = scelle(PhysBody(0.05f, 0.02f, 0f).apply {
            this.x = x + longueur / 2f
            this.y = bas
        })
        val ressort = SpringJoint.between(
            appui, x + longueur / 2f, bas,
            volet, x + longueur / 2f, charniere,
            stiffness = raideur,
            damping = amortissement
        )
        return Piece(TypePiece.TREMPLIN, listOf(socle, appui, volet), listOf(pivot, ressort))
    }
}
