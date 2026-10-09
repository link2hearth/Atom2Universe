package com.Atom2Universe.app.games.infernale

import com.Atom2Universe.app.games.physics.PhysBody

/** Pourquoi une pose a ete refusee. [OK] est le seul cas ou elle passe. */
enum class Refus {
    OK,

    /** La piece sortirait du tableau. */
    HORS_TABLEAU,

    /** La piece en chevaucherait une autre. */
    OCCUPE,

    /** On ne pose plus rien une fois la machine lancee. */
    DEJA_LANCEE
}

/**
 * Un tableau en cours d'edition : ce qu'on y a pose, et le monde qui en decoule.
 *
 * C'est un **bac a sable**. Il n'y a ni objectif, ni victoire, ni stock : on pose ce qu'on
 * veut ou on veut, puis on lance et on regarde.
 *
 * Elle a **deux temps**. Pendant la pose, rien ne bouge : on place, on reprend, on
 * reflechit, le monde est fige. Puis on lance, et la machine part. Les poses ne changent
 * jamais pendant le lancement — c'est ce qui permet de tout remettre exactement comme
 * c'etait avec [arreter], et de sauvegarder le montage plutot que son etat en cours de route.
 */
class Partie(
    /** Graine du decor (cailloux du sol) : la meme caverne revient a chaque ouverture. */
    val graine: Long = 0L
) {

    /** Le monde. Il existe des le debut : on voit le decor avant de poser. */
    var plateau: Plateau = Plateau()
        private set

    private val placements = ArrayList<Pose>()
    private val corps = ArrayList<Piece>()

    /** Vrai une fois la machine lancee : plus rien ne se pose. */
    var lancee = false
        private set

    /** Temps simule depuis le lancement, en secondes. */
    var chrono = 0f
        private set

    /** Les liens plaque -> canon, par rang de pose. */
    fun liens(): List<Lien> = plateau.liens.toList()

    /**
     * Relie une plaque et un canon, ou delie s'ils l'etaient deja. [a] et [b] sont des rangs de
     * pose, dans n'importe quel ordre. Rend vrai si quelque chose a change.
     */
    fun lier(a: Int, b: Int): Boolean {
        if (lancee) return false
        val pa = placements.getOrNull(a)?.type ?: return false
        val pb = placements.getOrNull(b)?.type ?: return false
        if (!Liens.complementaires(pa, pb)) return false
        val lien = if (pa == TypePiece.PLAQUE) Lien(a, b) else Lien(b, a)
        if (!plateau.liens.remove(lien)) plateau.liens.add(lien)
        return true
    }

    /** Les poses du joueur, dans l'ordre. */
    fun placees(): List<Pose> = placements.toList()

    /** Nombre de pieces posees, pour l'affichage. */
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
        val essai = pose.creer()
        if (!Placement.dansLeCadre(
                essai, -Plateau.LARGEUR / 2f, Plateau.LARGEUR / 2f, Plateau.HAUTEUR
            )
        ) {
            return Refus.HORS_TABLEAU
        }
        if (Placement.heurte(essai, occupants(sauf))) return Refus.OCCUPE
        return Refus.OK
    }

    /** Pose une piece. Rend [Refus.OK] si elle passe, sinon ne touche a rien. */
    fun poser(pose: Pose): Refus {
        val verdict = verifier(pose)
        if (verdict != Refus.OK) return verdict
        corps.add(plateau.poser(pose.creer()))
        placements.add(pose)
        return Refus.OK
    }

    /**
     * Pose une liste de pieces d'un coup, par exemple a l'ouverture d'un tableau
     * sauvegarde. Celles qui ne passent plus sont ignorees.
     */
    fun poserTout(poses: List<Pose>) {
        for (p in poses) poser(p)
    }

    /**
     * Rejoue une sauvegarde : les poses, puis les liens. Si une pose ne passe plus, les rangs
     * suivants se decalent ; les liens sont renumerotes en consequence, et ceux qui visaient
     * une pose perdue sont abandonnes.
     */
    fun charger(poses: List<Pose>, liens: List<Lien>) {
        val rang = IntArray(poses.size) { -1 }
        for ((i, p) in poses.withIndex()) {
            if (poser(p) == Refus.OK) rang[i] = placements.lastIndex
        }
        for (l in liens) {
            val a = rang.getOrNull(l.plaque) ?: continue
            val b = rang.getOrNull(l.cible) ?: continue
            if (a >= 0 && b >= 0) lier(a, b)
        }
    }

    /**
     * Deplace la piece [index] a un nouvel endroit.
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

    /** Reprend la derniere piece posee. */
    fun reprendre(): Boolean {
        if (lancee || placements.isEmpty()) return false
        return reprendre(placements.lastIndex)
    }

    /** Reprend la piece posee a l'indice [index]. */
    fun reprendre(index: Int): Boolean {
        if (lancee || index !in placements.indices) return false
        placements.removeAt(index)
        plateau.retirer(corps.removeAt(index))
        // Les liens de la piece retiree disparaissent, et les rangs suivants reculent d'un.
        val gardes = plateau.liens.filter { it.plaque != index && it.cible != index }.map {
            Lien(if (it.plaque > index) it.plaque - 1 else it.plaque, if (it.cible > index) it.cible - 1 else it.cible)
        }
        plateau.liens.clear()
        plateau.liens.addAll(gardes)
        return true
    }

    /** Lance la machine. Plus rien ne se pose ensuite. */
    fun lancer() {
        if (lancee) return
        lancee = true
        for (p in plateau.pieces) p.demarrer()
    }

    /** Une image, et seulement si la machine est lancee. */
    fun avancer(dt: Float) {
        if (!lancee) return
        plateau.avancer(dt)
        chrono += dt
    }

    /**
     * Arrete la machine et remet le montage exactement comme il etait avant le lancement :
     * les pieces reprennent leur place de depart, prêtes a etre modifiees ou relancees.
     */
    fun arreter() {
        val garde = placements.toList()
        val liensGardes = plateau.liens.toList()
        remonter()
        charger(garde, liensGardes)
    }

    /** Vide le tableau. */
    fun vider() = remonter()

    private fun remonter() {
        plateau = Plateau()
        corps.clear()
        placements.clear()
        lancee = false
        chrono = 0f
    }

    /** Tout ce qui occupe deja de la place : les pieces posees. */
    private fun occupants(sauf: Int): List<PhysBody> {
        val out = ArrayList<PhysBody>()
        for (i in corps.indices) {
            if (i == sauf) continue
            out.addAll(corps[i].corps)
        }
        for (c in out) c.updateAabb()
        return out
    }
}
