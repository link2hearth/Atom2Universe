package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysWorld

/**
 * **La partie : le site, le relief, le vent, les compteurs, le bouquet final.**
 *
 * Quatrième et dernière pièce partagée entre le trébuchet et l'atelier d'engrenages,
 * après [TrebuchetGround], [ShotTrail] et [ShotCamera]. C'est tout ce qu'une partie
 * possède **sauf la machine** : ce sur quoi on tire, ce que ça fait, et ce qu'on en
 * retient. Rien là-dedans ne dépend de la façon dont le boulet a été lancé.
 *
 * ### Ce que ça a coûté d'exister en double
 *
 * Les deux jeux chargeaient le même niveau, sorti du même générateur, par deux procédures
 * écrites séparément — et elles ne faisaient pas les mêmes choses. Mesuré le 04/09/2026,
 * même graine des deux côtés :
 *
 * ```
 *                                        trébuchet          atelier
 * vent de la graine -> world.windX     0,27 · -2,15 · 8,49   0,00 toujours
 * shotCount après changement de site        1 -> 0            1 -> 1
 * ```
 *
 * **L'atelier jetait le vent que sa propre graine venait de tirer.** [TargetGenerator]
 * en produit un pour chaque niveau, jusqu'à 8,5 m/s ; le trébuchet le pose aux deux
 * endroits qui s'en servent — la traînée du monde et le souffle des effets — et l'atelier
 * ne le posait nulle part. Sa vue en était réduite à inventer une brise sinusoïdale pour
 * faire bouger les arbres, pendant que le vrai vent du site dormait dans un objet que
 * personne ne lisait. Et son compteur de tirs traversait les sites : on arrivait sur un
 * village neuf avec les tirs du précédent au compteur.
 *
 * ### La règle
 *
 * Il n'y a **qu'un seul** ordre de chargement, celui de [load], et il compte : le relief
 * se pose avant que la machine ne se remonte, parce que c'est lui qui décide des corps
 * immobiles du monde ; les pierres se posent après, parce que le remontage vide le monde.
 * Se tromper d'ordre ne lève rien — ça donne un site qui flotte, ou des pierres comptées
 * deux fois.
 */
class ShotSite(val world: PhysWorld) {

    /**
     * Remonte la machine et refait le sol. C'est `TrebuchetGame.build` d'un côté,
     * `GearMachineGame.rebuild` de l'autre.
     *
     * C'est une **variable** et non un paramètre de construction, parce qu'un site peut
     * servir successivement à plusieurs machines : celle qui se branche pose son crochet,
     * celle qui s'en va le laisse à la suivante. C'est ce qui permet de garder le village
     * et ses gravats en changeant de machine.
     */
    var remount: () -> Unit = {}

    /**
     * De quoi tamponner les pierres une fois posées. L'atelier s'en sert pour les mettre
     * sur toutes ses couches de collision — un boulet parti du troisième étage doit
     * pouvoir les toucher. Le trébuchet, qui n'a pas d'étages, ne pose rien.
     */
    var stampPieces: (TargetField) -> Unit = {}

    /**
     * Fumée, gravats, poussière et feu d'artifice.
     *
     * Ils vivent dans la simulation et non dans la vue : ce sont des choses qui bougent
     * dans des mètres, elles se simulent, et ce qui se simule se vérifie au banc. La vue
     * n'en fait que des pixels.
     */
    val effects = TrebuchetEffects()

    /**
     * La cible : la construction à raser, et tout ce qui lui arrive.
     *
     * Elle vit dans le même monde que la machine, à des centaines de mètres de là. Les
     * catégories de collision font que les deux ne se rencontrent jamais autrement que
     * par le projectile.
     */
    val targets = TargetField(world).apply {
        // La poussière du dernier palier de destruction. Le champ de cibles ne connaît
        // pas les effets — c'est la partie qui les lui prête, une fois, ici.
        onDust = { x, y, r -> effects.dust(x, y, r) }
    }

    /**
     * Retamponne les pierres.
     *
     * [load] le fait déjà en posant le site ; c'est ici pour la machine qui remonte son
     * monde entre deux tirs, parce que [TargetField.reattach] remet les corps dans le
     * monde tels qu'elle les a fabriqués — donc sans les couches de l'atelier.
     */
    fun restamp() = stampPieces(targets)

    /** Le niveau en cours, ou nul en bac à sable (record de portée, sans cible). */
    var level: TargetLevel? = null
        private set

    /**
     * Le relief du niveau en cours : plat en bac à sable, dessiné par la graine sinon.
     *
     * Il n'appartient pas au monde physique — c'est une **description**, et c'est elle qui
     * fait autorité. Le monde en tire ses corps immobiles à chaque remontage, la vue en
     * tire sa ligne d'horizon, et le jeu lui demande où est le sol quand il veut savoir si
     * le projectile a touché. Un seul profil, trois lecteurs, et aucun risque qu'ils
     * racontent trois histoires différentes.
     */
    var terrain: Terrain = Terrain.FLAT
        private set

    /**
     * Le vent du moment. Il vient du niveau, donc de sa graine.
     *
     * Le poser ici le pose partout : le monde s'en sert pour la traînée du projectile, les
     * effets pour emporter la fumée et coucher les feux. Rien d'autre n'a besoin de le
     * connaître.
     */
    var wind: Wind = Wind.CALM
        private set

    /** Tirs joués sur ce site. Remis à zéro en changeant de site, jamais autrement. */
    var shotCount = 0

    /** Tirs qui ont entamé quelque chose. C'est la note du joueur, pas son nombre d'essais. */
    var hitCount = 0

    /** Vrai quand le site en cours a déjà eu droit à son feu d'artifice. */
    private var celebrated = false

    /**
     * Hauteur de ciel visible à l'écran, en mètres. La vue la pose à chaque image.
     *
     * C'est la seule chose que la simulation sache de l'affichage, et elle ne sert qu'à
     * une chose : dire au feu d'artifice jusqu'où monter. Un écran couché montre cent
     * mètres de ciel, le même écran debout en montre huit cents — des fusées réglées pour
     * l'un se tassent dans le bas de l'autre.
     */
    var skyTop = 150f
        set(value) {
            field = value
            effects.skyTop = value
        }

    /**
     * Pose un niveau, ou rend le bac à sable quand [lvl] est nul.
     *
     * L'ordre n'est pas négociable :
     *
     *  1. le relief **avant** le remontage — c'est lui qui décide des corps immobiles ;
     *  2. le remontage, qui vide le monde et le rebâtit ;
     *  3. les pierres **après**, parce que l'étape 2 vient de vider le monde ;
     *  4. le vent, en dernier, parce qu'il se pose sur le monde *et* sur les effets.
     *
     * Une cible qu'on efface se retire en revanche **avant** le remontage : le remontage
     * remet dans le monde les pierres que le champ tient encore, et les effacer après
     * reviendrait à les y remettre pour rien.
     */
    fun load(lvl: TargetLevel?) {
        level = lvl
        terrain = lvl?.terrain ?: Terrain.FLAT
        val relief = terrain
        effects.groundAt = { x -> relief.heightAt(x) }
        effects.clear()
        celebrated = false
        shotCount = 0
        hitCount = 0
        if (lvl == null) targets.clear()
        remount()
        if (lvl != null) {
            targets.load(lvl.structure, lvl.terrain)
            stampPieces(targets)
        }
        applyWind(lvl?.wind ?: Wind.CALM)
    }

    /**
     * Pose le vent, une fois, aux deux endroits qui s'en servent.
     *
     * Le monde ne connaît qu'une vitesse d'air, dont il se sert dans la traînée ; les
     * effets s'en servent pour emporter la fumée et coucher les feux d'artifice. Passer
     * par ici garantit que les deux racontent la même histoire — un vent qui souffle sur
     * le projectile mais pas sur la fumée serait pire que pas de vent du tout.
     */
    fun applyWind(w: Wind) {
        wind = w
        world.windX = w.vx
        world.windY = w.vy
        effects.setWind(w.vx, w.vy)
    }

    /**
     * L'abscisse de la pierre la plus à droite, débris compris, ou le bord de l'empreinte
     * d'origine s'il est encore plus loin.
     */
    fun rubbleRight(): Float {
        var bout = targets.right
        for (p in targets.pieces) if (p.body.x > bout) bout = p.body.x
        return bout
    }

    /**
     * Tire le feu d'artifice de la victoire, une fois par site.
     *
     * Il part **de la machine jusqu'au bout des décombres**, et non pas seulement jusqu'au
     * pied de la construction. La caméra prend tout le champ à la fin d'un tir : un
     * bouquet qui s'arrête avant les ruines laisse noire la moitié de l'image, celle-là
     * même où le joueur regarde ce qu'il vient d'abattre. On fête un site rasé, donc on
     * éclaire le site entier.
     *
     * Le bout des décombres se lit sur les pierres et non sur l'empreinte d'origine : une
     * construction qui s'effondre projette ses blocs plus loin qu'elle ne s'étendait.
     *
     * @param machineRight le bord droit de la machine : le bouquet part de là. C'est la
     *   seule chose que la partie ne sache pas toute seule.
     */
    fun celebrate(machineRight: Float) {
        if (celebrated || level == null || !targets.cleared) return
        celebrated = true
        val debut = machineRight + 30f
        val fin = (rubbleRight() + 25f).coerceAtLeast(debut + 40f)
        effects.celebrate(debut, fin)
    }
}
