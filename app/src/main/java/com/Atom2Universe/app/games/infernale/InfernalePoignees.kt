package com.Atom2Universe.app.games.infernale

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Ce qu'une poignee regle quand on la traine. */
enum class Prise {
    /** Un bout de planche. L'autre bout ne bouge pas : on regle a la fois longueur et angle. */
    BOUT_DEBUT,

    /** L'autre bout de la meme planche. */
    BOUT_FIN,

    /** Le coin d'un bloc : largeur et hauteur, chacune de son cote. */
    COIN,

    /** Le bord d'un disque : son rayon. */
    RAYON,

    /** Le sommet d'une piece dressee : sa hauteur. */
    SOMMET,

    /** La bouche d'un ventilateur : la direction du jet. */
    JET,

    /** Le bord d'une plaque de pression : sa largeur. */
    LARGEUR,

    /** Le long du jet d'un ventilateur : sa puissance. Plus loin, plus fort. */
    PUISSANCE
}

/** Une poignee : ce qu'elle regle, et ou la saisir. */
class Poignee(val prise: Prise, val x: Float, val y: Float)

/**
 * Les poignees : ce qu'on attrape sur une piece posee pour la regler au doigt.
 *
 * ## Pourquoi ca ne pouvait pas rester un curseur
 *
 * Une planche a une longueur **et** un angle, et les deux se decident ensemble : on ne
 * pense pas « vingt-deux degres sur un metre cinquante », on pense « d'ici a la ». Deux
 * curseurs separes obligent a traduire une intention geometrique en deux nombres, puis a
 * corriger l'un apres l'autre parce que changer l'angle a deplace le bout qu'on voulait
 * garder.
 *
 * **Une poignee par bout, et l'autre bout qui ne bouge pas.** C'est le geste du menuisier :
 * on tient une extremite et on tire l'autre. Longueur, angle et position se reglent d'un
 * seul mouvement, et ce qu'on voulait garder en place y reste.
 *
 * ## Pourquoi c'est un objet a part, et pas du code de vue
 *
 * Parce que ca se teste. Tirer un bout de planche est de la geometrie pure — une [Pose] et
 * un point du monde entrent, une [Pose] sort — et rien la-dedans n'a besoin d'un ecran. Le
 * jour ou le miroir d'un tapis inversera aussi son angle sans qu'on l'ait demande, c'est un
 * test qui le dira, pas une capture d'ecran.
 */
object Poignees {

    /** Longueurs extremes d'une planche, en metres. */
    const val LONGUEUR_MIN = 0.35f
    const val LONGUEUR_MAX = 6f

    /** Rayons extremes d'un disque. */
    const val RAYON_MIN = 0.08f
    const val RAYON_MAX = 0.6f

    /** Distance minimale entre le pied d'une bascule et chacun de ses bouts. */
    const val PIVOT_MARGE = 0.1f

    /** Largeurs extremes d'une plaque de pression. */
    const val PLAQUE_MIN = 0.3f
    const val PLAQUE_MAX = 2f

    /** Longueurs extremes d'un pendule. */
    const val PENDULE_MIN = 0.4f
    const val PENDULE_MAX = 4f

    /** Hauteurs extremes d'une piece dressee. */
    const val HAUTEUR_MIN = 0.25f
    const val HAUTEUR_MAX = 3.2f

    /** Demi-cotes extremes d'un bloc. */
    const val DEMI_MIN = 0.08f
    const val DEMI_MAX = 2.5f

    /**
     * Les pieces dont on tire les deux bouts : longueur **et** angle d'un seul geste.
     *
     * Ce sont les trois planches libres d'orientation. La bascule et le tremplin ont beau
     * etre des planches, leur angle appartient a la physique ou a leur charniere : y
     * proposer un angle serait mentir, puisque la piece le reprendrait au premier pas.
     */
    private fun estPlanche(type: TypePiece): Boolean =
        type == TypePiece.RAMPE || type == TypePiece.TAPIS || type == TypePiece.TAMBOUR

    /** Cote principale de la pose, ou celle par defaut. */
    fun longueur(pose: Pose): Float = if (pose.taille > 0f) pose.taille else when (pose.type) {
        TypePiece.RAMPE -> Pieces.RAMPE_LONGUEUR
        TypePiece.TAPIS -> Pieces.TAPIS_LONGUEUR
        TypePiece.TAMBOUR -> Pieces.TAMBOUR_LARGEUR
        TypePiece.BASCULE -> Pieces.BASCULE_LONGUEUR
        TypePiece.TREMPLIN -> Pieces.TREMPLIN_LONGUEUR
        TypePiece.PLOT -> Pieces.PLOT_RAYON
        TypePiece.BILLE -> Pieces.BILLE_RAYON
        TypePiece.BLOC -> Pieces.BLOC_COTE
        TypePiece.DOMINO -> Pieces.DOMINO_HAUTEUR
        TypePiece.POULIE -> Pieces.POULIE_HAUTEUR
        TypePiece.VENTILATEUR -> Pieces.VENTILATEUR_COTE
        TypePiece.BALLON -> Pieces.BALLON_RAYON
        TypePiece.PENDULE -> Pieces.PENDULE_LONGUEUR
        TypePiece.AIMANT -> Pieces.AIMANT_COTE
        TypePiece.CANON -> Pieces.CANON_DEMI_LONGUEUR * 2f
        TypePiece.PIC -> Pieces.PIC_DEMI_HAUTEUR * 2f
        TypePiece.PLAQUE -> Pieces.PLAQUE_LARGEUR
        TypePiece.TORCHE -> 0.26f
    }

    /** Seconde cote d'un bloc. */
    private fun hauteurBloc(pose: Pose): Float =
        if (pose.taille2 > 0f) pose.taille2 else longueur(pose)

    /**
     * L'angle du corps, en radians.
     *
     * Le reglage d'une planche est une **pente** — positive, elle descend vers la droite —
     * alors que l'angle du moteur tourne dans l'autre sens, les y montant. Le signe moins
     * vit ici et dans `Pieces`, jamais ailleurs.
     */
    private fun angle(pose: Pose): Float = -Math.toRadians(pose.reglage.toDouble()).toFloat()

    /**
     * Les poignees d'une piece posee, en coordonnees du monde.
     *
     * [ecart] repousse chaque poignee **hors de la piece**, en metres. Sans lui, la poignee
     * d'un petit plot se superposait au plot lui-meme : poser le doigt dessus attrapait la
     * taille alors qu'on voulait le deplacer, et on ne savait jamais ce qu'on touchait. La
     * vue passe un ecart constant **en pixels** (converti en metres), donc la poignee reste
     * a la meme distance visuelle de la piece quel que soit le zoom.
     */
    fun pour(pose: Pose, ecart: Float = 0f): List<Poignee> {
        val l = longueur(pose)
        return when {
            estPlanche(pose.type) -> {
                val a = angle(pose)
                val dx = cos(a) * (l / 2f + ecart)
                val dy = sin(a) * (l / 2f + ecart)
                listOf(
                    Poignee(Prise.BOUT_DEBUT, pose.x - dx, pose.y - dy),
                    Poignee(Prise.BOUT_FIN, pose.x + dx, pose.y + dy)
                )
            }

            // La bascule est posee par le bas de son pied. Ses deux bouts se tirent chacun a son
            // tour, le pied restant ou il est : on allonge un cote, on raccourcit l'autre, et le
            // pivot se retrouve ailleurs que le milieu — c'est un levier.
            pose.type == TypePiece.BASCULE -> {
                val h = pose.y + 0.34f
                val gauche = pose.x + pose.taille2 - l / 2f
                val droite = pose.x + pose.taille2 + l / 2f
                listOf(
                    Poignee(Prise.BOUT_DEBUT, gauche - ecart, h),
                    Poignee(Prise.BOUT_FIN, droite + ecart, h)
                )
            }

            // Le tremplin a une charniere d'un cote : seul le bout libre se tire.
            pose.type == TypePiece.TREMPLIN -> {
                val cote = if (pose.miroir) -1f else 1f
                listOf(Poignee(Prise.BOUT_FIN, pose.x + cote * (l / 2f + ecart), pose.y + 0.35f))
            }

            pose.type == TypePiece.PLOT -> listOf(Poignee(Prise.RAYON, pose.x + l + ecart, pose.y))

            // Le ballon se regle par son enveloppe, tout en haut : le panier est loin dessous.
            pose.type == TypePiece.BALLON ->
                listOf(Poignee(Prise.RAYON, pose.x + l + ecart, Pieces.ballonCentre(pose.y, l)))

            // Le pendule se tire par son boulet : le pivot ne bouge pas, la barre suit le
            // doigt. Longueur et angle d'un seul geste, comme une planche.
            pose.type == TypePiece.PENDULE -> {
                val a = Math.toRadians(pose.reglage.toDouble()).toFloat()
                val d = l + ecart
                listOf(Poignee(Prise.BOUT_FIN, pose.x + sin(a) * d, pose.y - cos(a) * d))
            }

            // Une bille est posee par sa base : son centre est un rayon plus haut.
            pose.type == TypePiece.BILLE -> listOf(Poignee(Prise.RAYON, pose.x + l + ecart, pose.y + l))

            pose.type == TypePiece.BLOC -> listOf(
                Poignee(Prise.COIN, pose.x + l / 2f + ecart, pose.y + hauteurBloc(pose) / 2f + ecart)
            )

            pose.type == TypePiece.DOMINO -> listOf(Poignee(Prise.SOMMET, pose.x, pose.y + l + ecart))

            pose.type == TypePiece.POULIE -> listOf(Poignee(Prise.SOMMET, pose.x, pose.y + l + ecart))

            // La plaque s'elargit par son bord droit, centree sur elle-meme.
            pose.type == TypePiece.PLAQUE -> listOf(Poignee(Prise.LARGEUR, pose.x + l / 2f + ecart, pose.y))

            // Le pic se tourne : une poignee de direction, a sa pointe.
            pose.type == TypePiece.PIC -> {
                val a = Math.toRadians(pose.reglage.toDouble()).toFloat()
                val d = Pieces.PIC_DEMI_HAUTEUR + 0.1f + ecart
                listOf(Poignee(Prise.JET, pose.x + cos(a) * d, pose.y + sin(a) * d))
            }

            // L'aimant n'a qu'une poignee, celle de sa force, a droite : il n'a pas de direction.
            pose.type == TypePiece.AIMANT -> listOf(
                Poignee(
                    Prise.PUISSANCE,
                    pose.x + DISTANCE_PUISSANCE_MIN + force(pose) * PAS_PUISSANCE + ecart,
                    pose.y
                )
            )

            pose.type == TypePiece.VENTILATEUR || pose.type == TypePiece.CANON -> {
                val a = Math.toRadians(pose.reglage.toDouble()).toFloat()
                val jet = DISTANCE_JET + ecart
                val puissance = DISTANCE_PUISSANCE_MIN + force(pose) * PAS_PUISSANCE + ecart
                listOf(
                    Poignee(Prise.JET, pose.x + cos(a) * jet, pose.y + sin(a) * jet),
                    Poignee(Prise.PUISSANCE, pose.x + cos(a) * puissance, pose.y + sin(a) * puissance)
                )
            }

            else -> emptyList()
        }
    }

    /** Distance entre le centre d'un ventilateur et sa poignee de direction, sans ecart. */
    private const val DISTANCE_JET = 0.45f

    /** Ou commence l'echelle de puissance le long du jet, et combien de metres vaut un newton. */
    private const val DISTANCE_PUISSANCE_MIN = 0.85f
    private const val PAS_PUISSANCE = 0.05f

    /** Puissance de la pose, ou celle par defaut. */
    fun force(pose: Pose): Float = if (pose.force > 0f) pose.force else Pieces.forceParDefaut(pose.type)

    /**
     * Rend la pose qu'on obtient en amenant la poignee [prise] au point ([x], [y]).
     *
     * Le resultat n'est pas garanti posable : c'est a la partie de le refuser si la piece
     * en chevauche une autre. Ici on ne fait que de la geometrie, et on borne les cotes
     * pour qu'aucun geste maladroit ne produise une planche de trois centimetres ou de
     * quarante metres.
     */
    fun tirer(pose: Pose, prise: Prise, x: Float, y: Float, ecart: Float = 0f): Pose = when {
        estPlanche(pose.type) && (prise == Prise.BOUT_DEBUT || prise == Prise.BOUT_FIN) ->
            tirerPlanche(pose, prise, x, y, ecart)

        pose.type == TypePiece.BASCULE -> {
            // Le pied et l'autre bout ne bougent pas. Le bout tire ne passe jamais de l'autre
            // cote du pied : il doit rester dessus pour que ce soit un pivot.
            val l = longueur(pose)
            val gauche = pose.x + pose.taille2 - l / 2f
            val droite = pose.x + pose.taille2 + l / 2f
            var g = gauche
            var d = droite
            if (prise == Prise.BOUT_DEBUT) {
                g = (x + ecart).coerceAtMost(pose.x - PIVOT_MARGE).coerceAtMost(d - LONGUEUR_MIN)
                g = g.coerceAtLeast(d - LONGUEUR_MAX)
            } else {
                d = (x - ecart).coerceAtLeast(pose.x + PIVOT_MARGE).coerceAtLeast(g + LONGUEUR_MIN)
                d = d.coerceAtMost(g + LONGUEUR_MAX)
            }
            pose.copy(taille = d - g, taille2 = (g + d) / 2f - pose.x)
        }

        pose.type == TypePiece.TREMPLIN -> {
            val cote = if (pose.miroir) -1f else 1f
            // La charniere est le point fixe ; on la retrouve depuis la pose actuelle.
            val charniere = pose.x - cote * longueur(pose) / 2f
            val l = (kotlin.math.abs(x - charniere) - ecart).coerceIn(LONGUEUR_MIN, LONGUEUR_MAX)
            pose.copy(x = charniere + cote * l / 2f, taille = l)
        }

        pose.type == TypePiece.PENDULE && prise == Prise.BOUT_FIN -> {
            val vx = x - pose.x
            val vy = y - pose.y
            val norme = hypot(vx, vy)
            if (norme < 1e-3f) pose else pose.copy(
                taille = (norme - ecart).coerceIn(PENDULE_MIN, PENDULE_MAX),
                reglage = arrondir(Math.toDegrees(atan2(vx.toDouble(), -vy.toDouble())).toFloat(), 1f)
            )
        }

        prise == Prise.RAYON -> {
            val centreY = when (pose.type) {
                TypePiece.BILLE -> pose.y + longueur(pose)
                TypePiece.BALLON -> Pieces.ballonCentre(pose.y, longueur(pose))
                else -> pose.y
            }
            val (mini, maxi) = if (pose.type == TypePiece.BALLON) {
                Pieces.BALLON_RAYON_MIN to Pieces.BALLON_RAYON_MAX
            } else {
                RAYON_MIN to RAYON_MAX
            }
            val r = (hypot(x - pose.x, y - centreY) - ecart).coerceIn(mini, maxi)
            pose.copy(taille = r)
        }

        prise == Prise.LARGEUR ->
            pose.copy(taille = ((kotlin.math.abs(x - pose.x) - ecart) * 2f).coerceIn(PLAQUE_MIN, PLAQUE_MAX))

        prise == Prise.COIN -> pose.copy(
            taille = ((kotlin.math.abs(x - pose.x) - ecart) * 2f).coerceIn(DEMI_MIN * 2f, DEMI_MAX * 2f),
            taille2 = ((kotlin.math.abs(y - pose.y) - ecart) * 2f).coerceIn(DEMI_MIN * 2f, DEMI_MAX * 2f)
        )

        prise == Prise.SOMMET ->
            pose.copy(taille = (y - pose.y - ecart).coerceIn(HAUTEUR_MIN, HAUTEUR_MAX))

        prise == Prise.JET -> {
            val degres = Math.toDegrees(
                atan2((y - pose.y).toDouble(), (x - pose.x).toDouble())
            ).toFloat()
            pose.copy(reglage = arrondir(degres, 5f))
        }

        // La puissance se lit sur l'axe du jet : on projette le doigt dessus, donc on peut
        // tirer de travers sans que ca change la direction.
        prise == Prise.PUISSANCE -> {
            val a = Math.toRadians(pose.reglage.toDouble()).toFloat()
            val le = (x - pose.x) * cos(a) + (y - pose.y) * sin(a) - ecart
            val newtons = ((le - DISTANCE_PUISSANCE_MIN) / PAS_PUISSANCE)
                .coerceIn(Pieces.SOUFFLE_POUSSEE_MIN, Pieces.SOUFFLE_POUSSEE_MAX)
            pose.copy(force = arrondir(newtons, 1f))
        }

        else -> pose
    }

    /**
     * Tire un bout de planche, l'autre restant ou il est.
     *
     * C'est le seul calcul un peu dense du fichier, et il tient en trois lignes : le point
     * fixe est le bout oppose, le vecteur va de lui au doigt, et la pose se relit dedans —
     * le centre au milieu, la longueur par la norme, la pente par l'angle.
     */
    private fun tirerPlanche(pose: Pose, prise: Prise, x: Float, y: Float, ecart: Float): Pose {
        val l = longueur(pose)
        val a = angle(pose)
        val dx = cos(a) * l / 2f
        val dy = sin(a) * l / 2f
        val fixeX = if (prise == Prise.BOUT_FIN) pose.x - dx else pose.x + dx
        val fixeY = if (prise == Prise.BOUT_FIN) pose.y - dy else pose.y + dy

        var vx = x - fixeX
        var vy = y - fixeY
        var norme = hypot(vx, vy)
        // Doigt pose pile sur le point fixe : plus aucune direction. On garde celle qu'on
        // avait plutot que de faire disparaitre la planche.
        if (norme < 1e-3f) {
            vx = cos(a)
            vy = sin(a)
            norme = 1f
        }
        val longue = (norme - ecart).coerceIn(LONGUEUR_MIN, LONGUEUR_MAX)
        val ux = vx / norme
        val uy = vy / norme
        // Le bout tire est du cote du doigt ; quand c'est le bout « debut », le vecteur
        // pointe a rebours de l'axe de la piece.
        val sens = if (prise == Prise.BOUT_FIN) 1f else -1f
        val angleMonde = atan2(uy * sens, ux * sens)
        return pose.copy(
            x = fixeX + ux * longue / 2f,
            y = fixeY + uy * longue / 2f,
            taille = longue,
            reglage = arrondir(-Math.toDegrees(angleMonde.toDouble()).toFloat(), 1f)
        )
    }

    /**
     * Accroche un angle a une grille.
     *
     * Sans elle, une planche tiree au doigt affiche `21,7384°` et ne sera jamais exactement
     * horizontale, ce qui est pourtant la chose qu'on veut le plus souvent.
     */
    private fun arrondir(valeur: Float, pas: Float): Float =
        Math.round(valeur / pas) * pas
}
