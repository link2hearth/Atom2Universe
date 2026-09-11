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
    JET
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

    /** Les poignees d'une piece posee, en coordonnees du monde. */
    fun pour(pose: Pose): List<Poignee> {
        val l = longueur(pose)
        return when {
            estPlanche(pose.type) -> {
                val a = angle(pose)
                val dx = cos(a) * l / 2f
                val dy = sin(a) * l / 2f
                listOf(
                    Poignee(Prise.BOUT_DEBUT, pose.x - dx, pose.y - dy),
                    Poignee(Prise.BOUT_FIN, pose.x + dx, pose.y + dy)
                )
            }

            // La bascule est posee par le bas de son pied, et sa planche est centree sur
            // lui : les deux bouts ne reglent que la longueur.
            pose.type == TypePiece.BASCULE -> {
                val h = pose.y + 0.34f
                listOf(
                    Poignee(Prise.BOUT_DEBUT, pose.x - l / 2f, h),
                    Poignee(Prise.BOUT_FIN, pose.x + l / 2f, h)
                )
            }

            // Le tremplin a une charniere d'un cote : seul le bout libre se tire.
            pose.type == TypePiece.TREMPLIN -> {
                val cote = if (pose.miroir) -1f else 1f
                listOf(Poignee(Prise.BOUT_FIN, pose.x + cote * l / 2f, pose.y + 0.35f))
            }

            pose.type == TypePiece.PLOT -> listOf(Poignee(Prise.RAYON, pose.x + l, pose.y))

            // Une bille est posee par sa base : son centre est un rayon plus haut.
            pose.type == TypePiece.BILLE -> listOf(Poignee(Prise.RAYON, pose.x + l, pose.y + l))

            pose.type == TypePiece.BLOC -> listOf(
                Poignee(Prise.COIN, pose.x + l / 2f, pose.y + hauteurBloc(pose) / 2f)
            )

            pose.type == TypePiece.DOMINO -> listOf(Poignee(Prise.SOMMET, pose.x, pose.y + l))

            pose.type == TypePiece.POULIE -> listOf(Poignee(Prise.SOMMET, pose.x, pose.y + l))

            pose.type == TypePiece.VENTILATEUR -> {
                val a = Math.toRadians(pose.reglage.toDouble()).toFloat()
                listOf(Poignee(Prise.JET, pose.x + cos(a) * 0.85f, pose.y + sin(a) * 0.85f))
            }

            else -> emptyList()
        }
    }

    /**
     * Rend la pose qu'on obtient en amenant la poignee [prise] au point ([x], [y]).
     *
     * Le resultat n'est pas garanti posable : c'est a la partie de le refuser si la piece
     * en chevauche une autre. Ici on ne fait que de la geometrie, et on borne les cotes
     * pour qu'aucun geste maladroit ne produise une planche de trois centimetres ou de
     * quarante metres.
     */
    fun tirer(pose: Pose, prise: Prise, x: Float, y: Float): Pose = when {
        estPlanche(pose.type) && (prise == Prise.BOUT_DEBUT || prise == Prise.BOUT_FIN) ->
            tirerPlanche(pose, prise, x, y)

        pose.type == TypePiece.BASCULE -> {
            // Le pied ne bouge pas : la longueur est le double de l'ecart au pied.
            val demi = kotlin.math.abs(x - pose.x).coerceIn(LONGUEUR_MIN / 2f, LONGUEUR_MAX / 2f)
            pose.copy(taille = demi * 2f)
        }

        pose.type == TypePiece.TREMPLIN -> {
            val cote = if (pose.miroir) -1f else 1f
            // La charniere est le point fixe ; on la retrouve depuis la pose actuelle.
            val charniere = pose.x - cote * longueur(pose) / 2f
            val l = kotlin.math.abs(x - charniere).coerceIn(LONGUEUR_MIN, LONGUEUR_MAX)
            pose.copy(x = charniere + cote * l / 2f, taille = l)
        }

        prise == Prise.RAYON -> {
            val centreY = if (pose.type == TypePiece.BILLE) pose.y + longueur(pose) else pose.y
            val r = hypot(x - pose.x, y - centreY).coerceIn(RAYON_MIN, RAYON_MAX)
            pose.copy(taille = r)
        }

        prise == Prise.COIN -> pose.copy(
            taille = (kotlin.math.abs(x - pose.x) * 2f).coerceIn(DEMI_MIN * 2f, DEMI_MAX * 2f),
            taille2 = (kotlin.math.abs(y - pose.y) * 2f).coerceIn(DEMI_MIN * 2f, DEMI_MAX * 2f)
        )

        prise == Prise.SOMMET ->
            pose.copy(taille = (y - pose.y).coerceIn(HAUTEUR_MIN, HAUTEUR_MAX))

        prise == Prise.JET -> {
            val degres = Math.toDegrees(
                atan2((y - pose.y).toDouble(), (x - pose.x).toDouble())
            ).toFloat()
            pose.copy(reglage = arrondir(degres, 5f))
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
    private fun tirerPlanche(pose: Pose, prise: Prise, x: Float, y: Float): Pose {
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
        val longue = norme.coerceIn(LONGUEUR_MIN, LONGUEUR_MAX)
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
