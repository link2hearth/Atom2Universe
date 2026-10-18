package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld

/**
 * Le plateau : le monde, le sol, et les pièces posées. Pas de murs.
 *
 * C'est un **bac à sable** : rien n'y est posé d'avance, aucune cible, aucune victoire.
 * On pose ce qu'on veut où on veut, on lance, et on regarde tout réagir. Une bille est une
 * pièce comme une autre (voir [TypePiece.BILLE]).
 */
class Plateau(
    val largeur: Float = LARGEUR,
    val hauteur: Float = HAUTEUR
) {
    val monde = PhysWorld().apply {
        // Les pièces posées passent beaucoup de temps immobiles à attendre leur tour :
        // le sommeil est exactement fait pour ça, et il rend le tableau presque gratuit
        // tant que rien n'est lâché.
        sleepEnabled = true
    }

    /** Le sol, scellé, à l'altitude zéro. */
    val sol: PhysBody = PhysBody(largeur, 0.5f, 0f).apply {
        x = 0f
        y = -0.5f
        lockPosition = true
        lockRotation = true
        friction = 0.7f
        refreshMass()
    }

    val pieces = ArrayList<Piece>()

    /** Les liens plaque -> canon, par rang dans [pieces]. Tenus a jour par la partie. */
    val liens = ArrayList<Lien>()

    companion object {
        /**
         * Largeur de la zone constructible, en metres.
         *
         * Un terrain d'experimentation ne doit pas etre un couloir : **il n'y a plus de murs**,
         * rien ne borne l'air a part la limite de pose, et elle est large. Le sol est plus
         * large encore, pour qu'une bille qui s'en va roule longtemps avant de tomber du monde.
         */
        const val LARGEUR = 40f

        /** Hauteur de la zone constructible, en metres. */
        const val HAUTEUR = 20f

        /** Delai minimal entre deux basculements d'un interrupteur, en secondes. */
        const val REBOND = 0.6f

        /** Categorie de tout ce qui bouge : billes, dominos, planches de bascule. */
        const val MOBILE = 2

        // La fenetre ouverte au chargement. Le plateau est bien plus grand : on batit
        // partout, la camera se contente de s'ouvrir sur un coin confortable.

        /** Bord gauche de la vue au chargement. */
        const val VUE_MIN_X = -4.5f

        /** Bord droit de la vue au chargement. */
        const val VUE_MAX_X = 4.5f

        /** Plafond de la vue au chargement. */
        const val VUE_MAX_Y = 5.5f
    }

    init {
        monde.add(sol)
    }

    /** Les jets d'air des ventilateurs posés. Refait à chaque image, il est court. */
    private val souffles = ArrayList<Souffle>()

    /** Temps simule depuis le debut, pour ignorer les rebonds d'un interrupteur. */
    private var temps = 0f

    fun poser(piece: Piece): Piece {
        piece.poser(monde)
        pieces.add(piece)
        return piece
    }

    fun retirer(piece: Piece) {
        piece.retirer(monde)
        pieces.remove(piece)
    }

    /**
     * Echange la piece [index] contre une autre **sans changer son rang**.
     *
     * Le rang n'est pas cosmetique : la partie tient la liste des poses du joueur en
     * parallele de celle-ci, et l'interface designe une piece par son indice. Retirer
     * puis reposer aurait renvoye la piece deplacee en fin de liste, et le doigt aurait
     * deplace la voisine au geste suivant.
     */
    fun remplacer(index: Int, piece: Piece): Piece {
        pieces[index].retirer(monde)
        piece.poser(monde)
        pieces[index] = piece
        return piece
    }

    /**
     * Les ventilateurs poussent **avant** le pas, jamais pendant.
     *
     * Le moteur remet les forces à zéro à la fin de chaque image et pas entre les
     * sous-pas : une force posée ici vaut donc pour toute l'image, ce qui est exactement
     * ce qu'on veut d'un vent — il ne change pas trois fois pendant un centième de
     * seconde.
     *
     * Une force posée sur un corps le **réveille**. C'est voulu pour ce qui flotte dans
     * le jet, et c'est pourquoi [Souffle.appliquer] refuse les corps scellés : un
     * ventilateur qui soufflerait sur son propre carter empêcherait le tableau entier
     * de s'endormir, et un tableau qui ne dort jamais coûte le prix fort à ne rien faire.
     */
    private fun souffler() {
        for (p in pieces) for (l in p.portances) l.appliquer()
        // L'attraction des aimants, avant le souffle : meme principe, une force par image.
        for (p in pieces) {
            val a = p.attraction ?: continue
            val corps = monde.bodies
            for (i in corps.indices) {
                val c = corps[i]
                if (c.inWorld && !c.isSensor) a.appliquer(c)
            }
        }
        souffles.clear()
        // Un ventilateur commande par une plaque ne souffle que si l'une de ses plaques est
        // allumee (interrupteur en marche, ou bouton enfonce).
        for ((rang, p) in pieces.withIndex()) {
            val jet = p.souffle ?: continue
            p.eteint = liens.any { it.cible == rang } &&
                liens.none { it.cible == rang && pieces.getOrNull(it.plaque)?.actif == true }
            if (!p.eteint) souffles.add(jet)
        }
        if (souffles.isEmpty()) return
        val corps = monde.bodies
        for (i in corps.indices) {
            val c = corps[i]
            if (!c.inWorld || c.immovable || c.isSensor) continue
            for (j in souffles.indices) souffles[j].appliquer(c)
        }
    }

    /** Une image de simulation. */
    fun avancer(dt: Float) {
        temps += dt
        souffler()
        monde.stepFrame(dt)
        crever()
        for (p in pieces) if (p.eclatAge >= 0f) p.eclatAge += dt
    }

    /**
     * Les ballons qui touchent un pic eclatent, et les canons dont le bouton est touche tirent.
     *
     * On lit les evenements **apres** le pas, comme partout : retirer un corps au milieu de la
     * resolution la casserait. L'enveloppe quitte le monde (ses cordes avec elle, c'est ce que
     * fait `PhysWorld.remove`), sa portance s'arrete faute de corps, et le panier disparait avec lui :
     * ce qu'il portait retombe seul.
     */
    private fun crever() {
        for (e in monde.contactEvents) {
            if (e.sensor) {
                lireDeclencheur(e)
                continue
            }
            if (!e.begin) continue
            val a = e.a ?: continue
            val b = e.b ?: continue
            if (e.sensor) continue
            val ballon = when {
                a.tag == Element.BALLON && b.tag == Element.PIC -> a
                b.tag == Element.BALLON && a.tag == Element.PIC -> b
                else -> continue
            }
            val piece = ballon.owner as? Piece ?: continue
            if (piece.creve) continue
            piece.creve = true
            piece.eclatAge = 0f
            // Le ballon creve, et son panier part avec lui : il ne reste que ce qu'il portait.
            for (corps in piece.corps) {
                piece.eclats.add(floatArrayOf(corps.x, corps.y, if (corps === ballon) 0f else 1f))
                monde.remove(corps)
                corps.inWorld = false
            }
        }
    }

    /**
     * Un objet entre dans la zone d'un canon ou d'une plaque, ou en sort.
     *
     * Ce qui compte est ce qui **bouge** et qui n'est pas la piece elle-meme : la bille chargee
     * d'un canon est dans son tube, et ne doit pas le faire tirer.
     */
    private fun lireDeclencheur(e: com.Atom2Universe.app.games.physics.ContactEvent) {
        for (p in pieces) {
            val zone = p.declencheur ?: continue
            val autre = e.other(zone) ?: continue
            if (autre.immovable || autre in p.corps) continue
            if (e.begin) {
                val avant = p.contacts
                p.contacts++
                if (p.type == TypePiece.PLAQUE) {
                    if (avant == 0) appuyer(p)
                } else {
                    p.tirer()
                }
            } else {
                p.contacts = maxOf(0, p.contacts - 1)
                if (p.type == TypePiece.PLAQUE && p.continu && p.contacts == 0) p.actif = false
            }
        }
    }

    /**
     * Quelqu'un vient d'appuyer sur une plaque. Un bouton continu s'allume ; un interrupteur
     * bascule, sauf si un rebond vient de le faire il y a un instant : une bille qui sautille
     * sur la plaque ne doit pas la faire clignoter. Allumee, elle fait tirer ses canons.
     */
    private fun appuyer(p: Piece) {
        if (p.continu) {
            p.actif = true
        } else if (temps - p.dernierBascule >= REBOND) {
            p.dernierBascule = temps
            p.actif = !p.actif
        }
        if (!p.actif) return
        val rang = pieces.indexOf(p)
        for (l in liens) if (l.plaque == rang) pieces.getOrNull(l.cible)?.tirer()
    }

    /** Vrai quand plus rien ne bouge assez pour que la suite change quoi que ce soit. */
    fun immobile(): Boolean = monde.isAtRest()

    /**
     * Fait tourner la machine pendant [duree] secondes, ou jusqu'à ce que [jusqua] soit
     * vrai, et rend le temps écoulé. C'est le raccourci des essais : « est-ce que ça
     * marche ? ».
     */
    fun derouler(
        duree: Float = 20f,
        dt: Float = 1f / 120f,
        jusqua: () -> Boolean = { false }
    ): Float {
        var t = 0f
        while (t < duree) {
            avancer(dt)
            t += dt
            if (jusqua()) break
        }
        return t
    }
}
