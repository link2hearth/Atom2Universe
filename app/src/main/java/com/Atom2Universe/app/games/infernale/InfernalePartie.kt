package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody

/** Pourquoi une pose a ete refusee. [OK] est le seul cas ou elle passe. */
enum class Refus {
    OK,

    /** Il ne reste plus de piece de ce type dans l'inventaire. */
    PLUS_EN_STOCK,

    /** La piece sortirait du tableau. */
    HORS_TABLEAU,

    /** La piece en chevaucherait une autre, ou la bille, ou le bouton. */
    OCCUPE,

    /** On ne pose plus rien une fois la machine lancee. */
    DEJA_LANCEE
}

/**
 * Une partie : un tableau, ce qu'on y a pose, et ce qu'il reste en stock.
 *
 * Elle a **deux temps**, et la separation est ce qui fait le jeu. Pendant la pose, rien
 * ne bouge : on place, on reprend, on reflechit, le monde est fige. Puis on lance, et la
 * machine part sans qu'on puisse plus rien toucher — on ne peut que regarder, et
 * recommencer.
 *
 * C'est ce qui distingue un casse-tete d'un bac a sable. Pouvoir corriger en cours de
 * route retirerait tout son poids a la decision de placement, qui est precisement le
 * seul geste du jeu.
 */
class Partie(val tableau: Tableau) {

    /** Le monde. Il existe des le debut : on voit le decor avant de poser. */
    var plateau: Plateau = Tableaux.monter(tableau)
        private set

    private val placements = ArrayList<Pose>()
    private val corps = ArrayList<Piece>()
    private val restant = HashMap<TypePiece, Int>(tableau.inventaire)

    /** Vrai une fois la machine lancee : plus rien ne se pose. */
    var lancee = false
        private set

    /** Temps simule depuis le lancement, en secondes. */
    var chrono = 0f
        private set

    /** Nombre de lancements depuis le debut du tableau, pour information. */
    var essais = 0
        private set

    val gagne: Boolean get() = plateau.gagne

    /**
     * Vrai quand la machine s'est arretee sans gagner : c'est un echec, pas une attente.
     *
     * On le mesure sur le moteur plutot que sur un chronometre. Une machine lente qui
     * fait encore tomber son dernier domino n'a pas echoue ; une machine ou plus rien ne
     * bouge, si — et le joueur n'a aucune raison d'attendre dix secondes pour
     * l'apprendre.
     */
    val echoue: Boolean
        get() = lancee && !gagne && chrono > 1.2f && (plateau.billePerdue() || plateau.immobile())

    /** Ce qu'il reste a poser, par type. */
    fun stock(): Map<TypePiece, Int> = restant.filterValues { it > 0 }

    fun stock(type: TypePiece): Int = restant[type] ?: 0

    /** Les poses du joueur, dans l'ordre. */
    fun placees(): List<Pose> = placements.toList()

    /**
     * Nombre de pieces posees.
     *
     * C'est devenu la note du tableau. Le joueur a toute la panoplie a chaque fois, donc
     * finir n'est plus la question — **finir avec peu** l'est. Les etoiles se comptent
     * la-dessus et plus sur le nombre d'essais : recommencer ne doit rien couter, sinon on
     * decourage precisement le geste qui fait ce jeu, qui est de reessayer en regardant.
     */
    val posees: Int get() = placements.size

    /**
     * Peut-on poser cette piece la ? Rend la raison du refus, [Refus.OK] si ca passe.
     *
     * C'est separe de [poser] a dessein : l'interface a besoin de la reponse **avant**
     * de lacher le doigt, pour montrer la piece en rouge pendant qu'on la traine.
     *
     * [sauf] permet d'ignorer une piece deja posee — celle qu'on est en train de
     * deplacer, qui ne doit evidemment pas se chevaucher elle-meme.
     */
    fun verifier(pose: Pose, sauf: Int = -1): Refus {
        if (lancee) return Refus.DEJA_LANCEE
        if (sauf !in placements.indices && stock(pose.type) <= 0) return Refus.PLUS_EN_STOCK
        val essai = pose.creer()
        if (!Placement.dansLeCadre(essai, tableau.cadreMinX, tableau.cadreMaxX, tableau.cadreMaxY)) {
            return Refus.HORS_TABLEAU
        }
        if (Placement.heurte(essai, occupants(sauf))) return Refus.OCCUPE
        // La zone du bouton n'arrete que ce qui bouge. Une rampe scellee posee par-dessus
        // ne la declenchera jamais — sa categorie est celle du decor — alors qu'un domino
        // pose dedans gagnerait la partie avant meme le premier pas. Le distinguo est donc
        // la seule facon d'avoir a la fois un placement libre et un jeu non triche.
        plateau.bouton?.let { b ->
            b.zone.updateAabb()
            for (c in essai.corps) {
                if (c.immovable) continue
                c.updateAabb()
                if (Placement.seChevauchent(c, b.zone)) return Refus.OCCUPE
            }
        }
        return Refus.OK
    }

    /**
     * Pose une piece. Rend [Refus.OK] et la decompte du stock si elle passe, sinon ne
     * touche a rien.
     */
    fun poser(pose: Pose): Refus {
        val verdict = verifier(pose)
        if (verdict != Refus.OK) return verdict
        corps.add(plateau.poser(pose.creer()))
        placements.add(pose)
        restant[pose.type] = stock(pose.type) - 1
        return Refus.OK
    }

    /**
     * Deplace la piece [index] a un nouvel endroit, sans passer par le stock.
     *
     * C'est le geste qu'on fait vingt fois par tableau : la rampe est presque bonne, il
     * lui manque dix centimetres. Reprendre puis reposer marcherait, mais la piece
     * changerait de rang dans la liste et le doigt perdrait ce qu'il tenait.
     */
    fun deplacer(index: Int, pose: Pose): Refus {
        if (lancee) return Refus.DEJA_LANCEE
        if (index !in placements.indices) return Refus.OCCUPE
        if (pose.type != placements[index].type) return Refus.OCCUPE
        val verdict = verifier(pose, sauf = index)
        if (verdict != Refus.OK) return verdict
        corps[index] = plateau.remplacer(index, pose.creer())
        placements[index] = pose
        return Refus.OK
    }

    /** Reprend la derniere piece posee et la remet au stock. */
    fun reprendre(): Boolean {
        if (lancee || placements.isEmpty()) return false
        return reprendre(placements.lastIndex)
    }

    /** Reprend la piece posee a l'indice [index]. */
    fun reprendre(index: Int): Boolean {
        if (lancee || index !in placements.indices) return false
        val pose = placements.removeAt(index)
        plateau.retirer(corps.removeAt(index))
        restant[pose.type] = stock(pose.type) + 1
        return true
    }

    /** Lance la machine. Plus rien ne se pose ensuite. */
    fun lancer() {
        if (lancee) return
        lancee = true
        essais++
    }

    /** Une image, et seulement si la machine est lancee. */
    fun avancer(dt: Float) {
        if (!lancee) return
        plateau.avancer(dt)
        chrono += dt
    }

    /**
     * Remonte le tableau a neuf en gardant les pieces posees : le geste qu'on fait
     * apres un essai rate, quand on veut juste corriger un domino.
     */
    fun rejouer() {
        val garde = placements.toList()
        val comptes = essais
        remonter()
        essais = comptes
        for (p in garde) poser(p)
    }

    /** Vide le tableau et rend tout au stock. */
    fun tableauRase() {
        val comptes = essais
        remonter()
        essais = comptes
    }

    private fun remonter() {
        plateau = Tableaux.monter(tableau)
        corps.clear()
        placements.clear()
        restant.clear()
        restant.putAll(tableau.inventaire)
        lancee = false
        chrono = 0f
        essais = 0
    }

    /** Tout ce qui occupe deja de la place : les pieces posees et la bille. */
    private fun occupants(sauf: Int): List<PhysBody> {
        val out = ArrayList<PhysBody>()
        for (i in corps.indices) {
            if (i == sauf) continue
            out.addAll(corps[i].corps)
        }
        plateau.bille?.let { out.add(it) }
        for (c in out) c.updateAabb()
        return out
    }
}
