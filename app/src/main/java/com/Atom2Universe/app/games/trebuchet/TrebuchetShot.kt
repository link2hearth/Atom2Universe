package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * **La trace du tir en cours, et la mémoire des tirs passés.**
 *
 * Deuxième pièce partagée entre le trébuchet et l'atelier d'engrenages, après
 * [TrebuchetGround]. Une trajectoire n'est pas une propriété de la machine : c'est ce
 * qu'un projectile a écrit dans le ciel, et ça se raconte pareil qu'on l'ait lancé avec
 * un contrepoids ou avec de l'air comprimé.
 *
 * Les deux jeux en avaient chacun leur copie, identiques à la lecture — et pourtant pas
 * d'accord. Mesuré le 04/09/2026, un tir joué puis rangé dans chaque mode :
 *
 * ```
 *                      après clearGhosts()        fin du fantôme <-> le boulet
 * trébuchet    0 fantôme, 858 valeurs de trace     0,25 m   (tir coupé à 30 m/s)
 * atelier      0 fantôme,   0 valeur               0,00 m
 * ```
 *
 * Deux défauts, tous deux du côté du trébuchet, tous deux invisibles à la relecture
 * parce qu'ils ne sont pas *dans* le code dupliqué mais dans ce qui l'entoure :
 *
 *  - `clearGhosts` n'effaçait que la liste des fantômes. La vue, elle, dessine **aussi**
 *    la trace vivante à chaque image — donc « effacer les tirs » laissait la dernière
 *    trajectoire à l'écran, quatre cent vingt-neuf points, avec zéro fantôme dans la
 *    liste. Le même bouton nettoyait vraiment tout dans l'atelier.
 *  - le fantôme s'arrêtait au dernier point échantillonné, jusqu'à [INTERVAL] seconde de
 *    vol **avant** l'endroit où le boulet s'est arrêté. Vingt-cinq centimètres à trente
 *    mètres par seconde, un mètre vingt sur une machine qui en fait cent cinquante.
 *    L'atelier, lui, posait un dernier point à l'endroit exact.
 *
 * Ici, ni l'un ni l'autre n'est possible : [clearGhosts] efface les deux, et [archive]
 * pose le point final. Une seule pièce, donc une seule façon de se tromper.
 */
class ShotTrail(initialCapacity: Int = 2_048) {

    private var buf = FloatArray(initialCapacity)

    /** Les points de la trajectoire en cours, en couples (x, y), à lire jusqu'à [count]. */
    val points: FloatArray get() = buf

    var count = 0
        private set

    /** Temps écoulé depuis le dernier point posé. */
    private var timer = 0f

    private val ghostList = ArrayList<FloatArray>(TrebuchetRules.GHOST_HISTORY)

    /** Les tirs passés, le plus récent en tête. */
    val ghosts: List<FloatArray> get() = ghostList

    /**
     * Change à chaque fois que [ghosts] change.
     *
     * La vue redessine ses fantômes dans un calque qu'elle garde d'une image à l'autre :
     * ce compteur est ce qui lui dit que le calque est périmé, sans quoi elle retracerait
     * des milliers de points à chaque image pour rien.
     */
    var stamp = 0
        private set

    /**
     * Combien de tirs on garde. Le joueur le règle dans le menu.
     *
     * Baisser la limite taille la pile sur-le-champ : un réglage qui n'agirait qu'aux
     * tirs suivants laisserait à l'écran des traces que le menu prétend avoir oubliées.
     */
    var limit: Int = TrebuchetRules.GHOST_HISTORY
        set(value) {
            field = value.coerceIn(1, TrebuchetRules.GHOST_CHOICES.last())
            while (ghostList.size > field) ghostList.removeAt(ghostList.size - 1)
            stamp++
        }

    /**
     * Ouvre un tir.
     *
     * Le point de départ est facultatif : l'atelier pose la bouche du canon, le trébuchet
     * ne pose rien parce que sa trace commence par le balancement du bras, bien avant que
     * le boulet ne soit libre.
     */
    fun begin(x: Float? = null, y: Float = 0f) {
        count = 0
        timer = 0f
        if (x != null) add(x, y)
    }

    fun add(x: Float, y: Float) {
        if (count + 2 > buf.size) buf = buf.copyOf(buf.size * 2)
        buf[count++] = x
        buf[count++] = y
    }

    /**
     * Avance l'horloge de la trace et pose un point si l'intervalle est écoulé.
     *
     * @param record à faux, l'horloge avance mais rien ne s'écrit. Le trébuchet s'en sert
     *   pour ses bombes : une bombe partie en fumée n'écrit plus, sinon la trace
     *   empilerait des centaines de points au même endroit pendant que la construction
     *   s'écroule, et le fantôme du tir garderait ce pâté-là.
     */
    fun sample(dt: Float, x: Float, y: Float, record: Boolean = true) {
        timer += dt
        if (timer > INTERVAL && count < MAX_FLOATS && record) {
            timer = 0f
            add(x, y)
        }
    }

    /**
     * Range le tir : un dernier point là où le projectile s'est vraiment arrêté, puis la
     * trace devient le fantôme le plus récent.
     *
     * Ce point final n'est pas un détail. Sans lui la trace s'arrête au dernier
     * échantillon, donc jusqu'à [INTERVAL] seconde de vol trop tôt — et un tir coupé en
     * plein vol par le joueur laisse un trait qui s'arrête en l'air.
     */
    fun archive(x: Float? = null, y: Float = 0f) {
        if (x != null) add(x, y)
        ghostList.add(0, buf.copyOf(count))
        while (ghostList.size > limit) ghostList.removeAt(ghostList.size - 1)
        stamp++
    }

    /**
     * Efface la mémoire des tirs — **la trace vivante comprise**.
     *
     * C'est le seul chemin qui enlève les fantômes, et la seule chose que le joueur en
     * attend est un ciel vide. Oublier la trace vivante laissait la dernière trajectoire
     * dessinée par-dessus zéro fantôme, ce qui n'a de sens pour personne.
     */
    fun clearGhosts() {
        ghostList.clear()
        count = 0
        timer = 0f
        stamp++
    }

    companion object {
        /** Un point tous les cinquantièmes de seconde de vol. */
        const val INTERVAL = 0.02f

        /**
         * Plafond de la trace : trois mille points, soit une minute de vol.
         *
         * Au-delà on cesse d'écrire plutôt que de laisser grossir un tableau qu'on
         * recopie en entier à chaque fantôme.
         */
        const val MAX_FLOATS = 6_000
    }
}

/**
 * **La traversée : un projectile ne paie que ce qu'il a détruit.**
 *
 * La physique, laissée seule, fait rebondir un boulet de douze kilos sur une pierre de
 * trois tonnes **même quand la pierre se brise** — le choc est résolu avant que la pierre
 * ne meure, et l'impulsion, elle, ne sait pas que sa cible n'existera plus dans un
 * dixième de seconde. C'est exact, et c'est tout ce qu'on ne veut pas voir en arcade :
 * ça cogne, ça casse, et ça repart en arrière.
 *
 * On remet donc le projectile dans l'axe qu'il avait avant le choc, avec l'énergie qu'il
 * avait **moins celle des points de vie qu'il vient d'emporter**. Trois garde-fous font
 * que ce n'est pas de la triche gratuite :
 *
 *  - il ne récupère rien s'il n'a **rien cassé** : cogner sans casser rebondit, dans les
 *    deux modes ;
 *  - il ne dépasse jamais l'énergie qu'il avait au début de l'image, donc le moteur ne
 *    crée pas d'énergie — la règle d'or de cette physique ;
 *  - on ne le relance que si la physique l'a laissé **plus lent** que ça, sinon on ne
 *    touche à rien.
 *
 * Et le curseur [TargetStyle.pierce] vaut zéro en réaliste, où le rebond honnête est
 * précisément ce qu'on est venu voir.
 *
 * ### Pourquoi c'est une pièce à part
 *
 * Ce calcul existait en deux exemplaires, un par jeu, et il a déjà fallu le corriger
 * **deux fois au même endroit logique** : l'atelier n'a hérité de « cogner sans casser
 * rebondit » qu'après coup. Rien dans le code ne reliait les deux copies, et rien
 * n'aurait signalé la troisième divergence. Il n'y en a plus qu'une.
 *
 * ### Comment on s'en sert
 *
 * Deux temps, et l'ordre n'est pas négociable :
 *
 * ```
 * pierce.bodies.clear()                     // avant le pas :
 * if (enVol) pierce.bodies += leProjectile  //   qui a le droit de traverser cette image
 * pierce.remember()                         //   son élan, que le pas va effacer
 * world.stepFrame(dt)
 * targets.update(dt)                        // les chocs deviennent des dégâts
 * pierce.apply(world, targets)              // après : on rend ce qui a été payé
 * ```
 *
 * Le site doit avoir encaissé les dégâts **avant** [apply], sinon le projectile ne sait
 * pas ce qu'il vient de casser et repart les mains vides.
 */
class ShotPierce {

    /**
     * Les corps qui ont le droit de traverser cette image : le projectile, et ses éclats
     * s'il s'est fendu en vol.
     *
     * Une liste que la machine vide et remplit à chaque image, plutôt qu'un tableau qu'on
     * lui passe : il y a jusqu'à cinq éclats relus soixante fois par seconde pendant tout
     * un vol, et allouer là serait donner du travail au ramasse-miettes exactement
     * pendant l'impact. Vide, tout ce qui suit est un non-événement.
     */
    val bodies = ArrayList<PhysBody>(8)

    /** Élan de chaque corps au début de l'image : vitesse en x, en y, et énergie. */
    private var momentum = FloatArray(3 * 8)

    /** Combien de corps [remember] a relevés, pour ne jamais lire un élan périmé. */
    private var remembered = 0

    /**
     * Relève l'élan d'avant le choc. À appeler **avant** de simuler le pas : une fois le
     * pas simulé il est perdu, et c'est justement lui qu'on veut rendre.
     */
    fun remember() {
        val n = bodies.size
        if (momentum.size < 3 * n) momentum = FloatArray(3 * n)
        for (i in 0 until n) {
            val b = bodies[i]
            momentum[3 * i] = b.vx
            momentum[3 * i + 1] = b.vy
            momentum[3 * i + 2] = 0.5f * b.mass * (b.vx * b.vx + b.vy * b.vy)
        }
        remembered = n
    }

    /** Rend à chaque corps l'élan qu'il a payé en dégâts. À appeler **après** le pas. */
    fun apply(world: PhysWorld, targets: TargetField) {
        val refund = TargetRules.style.pierce
        if (refund <= 0f) return
        val n = minOf(remembered, bodies.size)
        for (i in 0 until n) {
            val b = bodies[i]
            val cost = targets.pierceCost(b)
            if (cost <= 0f) continue
            // Le passage se **gagne** : pierre déjà fêlée, ou coup critique. Plein s'il
            // l'a gagné, un reliquat sinon — voir [TargetField.piercedThrough], c'est le
            // champ qui tient la règle, parce que c'est lui qui sait ce que le projectile
            // vient d'achever et dans quel état c'était.
            val part = refund *
                if (targets.piercedThrough(b)) 1f else TargetRules.PIERCE_UNEARNED
            val vx0 = momentum[3 * i]
            val vy0 = momentum[3 * i + 1]
            val v0 = hypot(vx0, vy0)
            if (v0 < 1f) continue
            val left = (momentum[3 * i + 2] - cost).coerceAtLeast(0f)
            val wanted = sqrt(2f * left / b.mass)
            val now = hypot(b.vx, b.vy)
            if (wanted <= now) continue
            val v = now + (wanted - now) * part
            b.wake()
            b.vx = vx0 / v0 * v
            b.vy = vy0 / v0 * v
            // Les contacts gardent leurs impulsions d'une image à l'autre : sans les
            // oublier, le solveur retiendrait le projectile contre une pierre qui n'est
            // déjà plus là.
            world.forgetContacts(b)
        }
    }
}
