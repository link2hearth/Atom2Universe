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
    var plateau: Plateau = Tableaux.monter(tableau, avecSolution = false)
        private set

    private val posees = ArrayList<Pose>()
    private val corps = ArrayList<Piece>()
    private val restant = HashMap<TypePiece, Int>(tableau.inventaire)

    /** Vrai une fois la machine lancee : plus rien ne se pose. */
    var lancee = false
        private set

    /** Temps simule depuis le lancement, en secondes. */
    var chrono = 0f
        private set

    val gagne: Boolean get() = plateau.gagne

    /** Ce qu'il reste a poser, par type. */
    fun stock(): Map<TypePiece, Int> = restant.filterValues { it > 0 }

    fun stock(type: TypePiece): Int = restant[type] ?: 0

    /** Les poses du joueur, dans l'ordre. */
    fun placees(): List<Pose> = posees.toList()

    /** Vrai quand tout l'inventaire est sur le tableau. */
    val toutPose: Boolean get() = restant.values.all { it == 0 }

    /**
     * Peut-on poser cette piece la ? Rend la raison du refus, [Refus.OK] si ca passe.
     *
     * C'est separe de [poser] a dessein : l'interface a besoin de la reponse **avant**
     * de lacher le doigt, pour montrer la piece en rouge pendant qu'on la traine.
     */
    fun verifier(pose: Pose): Refus {
        if (lancee) return Refus.DEJA_LANCEE
        if (stock(pose.type) <= 0) return Refus.PLUS_EN_STOCK
        val essai = pose.creer()
        val limite = plateau.largeur / 2f
        for (c in essai.corps) c.updateAabb()
        for (c in essai.corps) {
            if (c.aabbMinX < -limite || c.aabbMaxX > limite) return Refus.HORS_TABLEAU
            if (c.aabbMinY < -MARGE || c.aabbMaxY > plateau.hauteur) return Refus.HORS_TABLEAU
        }
        val places = occupants()
        for (c in essai.corps) {
            for (autre in places) if (seChevauchent(c, autre)) return Refus.OCCUPE
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
        posees.add(pose)
        restant[pose.type] = stock(pose.type) - 1
        return Refus.OK
    }

    /** Reprend la derniere piece posee et la remet au stock. */
    fun reprendre(): Boolean {
        if (lancee || posees.isEmpty()) return false
        val pose = posees.removeAt(posees.lastIndex)
        plateau.retirer(corps.removeAt(corps.lastIndex))
        restant[pose.type] = stock(pose.type) + 1
        return true
    }

    /** Reprend la piece posee a l'indice [index]. */
    fun reprendre(index: Int): Boolean {
        if (lancee || index !in posees.indices) return false
        val pose = posees.removeAt(index)
        plateau.retirer(corps.removeAt(index))
        restant[pose.type] = stock(pose.type) + 1
        return true
    }

    /** Lance la machine. Plus rien ne se pose ensuite. */
    fun lancer() {
        lancee = true
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
        val garde = posees.toList()
        plateau = Tableaux.monter(tableau, avecSolution = false)
        corps.clear()
        posees.clear()
        restant.clear()
        restant.putAll(tableau.inventaire)
        lancee = false
        chrono = 0f
        for (p in garde) poser(p)
    }

    /** Vide le tableau et rend tout au stock. */
    fun tableauRase() {
        plateau = Tableaux.monter(tableau, avecSolution = false)
        corps.clear()
        posees.clear()
        restant.clear()
        restant.putAll(tableau.inventaire)
        lancee = false
        chrono = 0f
    }

    /** Tout ce qui occupe deja de la place : les pieces posees, la bille, le bouton. */
    private fun occupants(): List<PhysBody> {
        val out = ArrayList<PhysBody>()
        for (p in corps) out.addAll(p.corps)
        plateau.bille?.let { out.add(it) }
        plateau.bouton?.let { out.add(it.zone) }
        for (c in out) c.updateAabb()
        return out
    }

    /**
     * Chevauchement par boites englobantes, avec une marge de tolerance.
     *
     * **Pas par cercles englobants**, et ca s'est paye : le cercle circonscrit d'un
     * domino pose au sol plonge sous le sol (son centre est a 22 cm, son rayon a 22,4),
     * si bien qu'aucune piece n'etait posable. Et celui d'une rampe de deux metres fait
     * un metre de rayon, ce qui lui faisait « chevaucher » tout ce qui passait a moins
     * d'un metre — y compris la solution du generateur. Un cercle est une mauvaise
     * approximation des pieces longues et plates, et elles le sont presque toutes ici.
     *
     * La marge autorise les pieces qui se touchent sans se penetrer : un domino pose
     * contre une rampe est un placement legitime, et souvent le bon.
     */
    private fun seChevauchent(a: PhysBody, b: PhysBody): Boolean =
        a.aabbMinX < b.aabbMaxX - MARGE && b.aabbMinX < a.aabbMaxX - MARGE &&
            a.aabbMinY < b.aabbMaxY - MARGE && b.aabbMinY < a.aabbMaxY - MARGE

    private companion object {
        /** Tolerance de placement, en metres. */
        const val MARGE = 0.02f
    }
}
