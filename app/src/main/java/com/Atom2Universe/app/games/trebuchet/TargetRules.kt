package com.Atom2Universe.app.games.trebuchet

/**
 * Qui a le droit de toucher qui, sur le champ de tir.
 *
 * Les catégories étaient jusqu'ici cachées dans la machine, qui était seule à en
 * avoir besoin. Les cibles arrivent de l'autre bout du terrain avec les leurs, et
 * deux jeux de bits qui doivent s'accorder sans se voir finissent toujours par se
 * contredire : ils vivent donc ici, en un seul endroit.
 */
object TrebuchetCategory {
    const val GROUND = 1
    const val BEAM = 2
    const val WEIGHT = 4
    const val BALL = 8
    const val TARGET = 16
    const val DEBRIS = 32

    /**
     * Ce qu'un boulet **en vol libre** rencontre. Tant qu'il est dans la fronde il
     * ne connaît que le sol : un boulet traîné sous le bâti n'a aucune raison de
     * venir cogner sa propre machine, et le laisser faire finissait mal.
     */
    const val BALL_FREE_MASK = GROUND or TARGET or DEBRIS

    /** Ce qu'une pierre de la cible rencontre : tout, sauf la machine. */
    const val TARGET_MASK = GROUND or BALL or TARGET or DEBRIS

    /**
     * Ce qu'un projectile rencontre vraiment, une fois le tempérament du jeu consulté.
     *
     * En arcade, **il ignore les gravats**, et c'est la moitié de la promesse de la
     * traversée. Mesuré au banc : un boulet à cent dix mètres par seconde entrait dans
     * une maison, emportait un poteau, ressortait à cent sept — et se faisait arrêter
     * net, l'image suivante, par un éclat de ce même poteau tombé devant lui. Il
     * passait de 107 à 14 m/s contre un morceau de bois déjà cassé.
     *
     * Un débris ne peut pas se casser — c'est la règle qui garantit qu'un effondrement
     * laisse un **tas**, et donc que le niveau reste gagnable. Mais un obstacle
     * incassable est aussi un obstacle infranchissable : la traversée ne peut rien pour
     * un projectile arrêté par quelque chose qui n'a plus de points de vie à lui faire
     * payer. On le laisse donc passer au travers du gravier, ce qui est arcade, lisible,
     * et surtout la seule chose qui marche.
     */
    fun projectileMask(): Int =
        if (TargetRules.style.pierce > 0f) GROUND or TARGET else BALL_FREE_MASK
}

/**
 * Comment un matériau cesse d'exister quand il rompt.
 *
 * Ce n'est pas de la décoration : c'est le budget de corps du moteur qui se joue
 * là. Un bloc qui éclate coûte deux à quatre corps de plus, un bloc qui tombe en
 * poussière n'en coûte aucun.
 */
enum class Rupture {
    /** Le bloc se fend en morceaux qui restent sur le terrain, et font le tas. */
    ECLATS,

    /** Le bloc s'effrite et disparaît : le torchis, la terre, le chaume. */
    POUSSIERE,

    /** Rien ne le casse. Le fer, et le socle d'une construction. */
    INCASSABLE
}

/**
 * De quoi une pierre est faite : sa densité, son adhérence, et surtout sa **vitesse
 * critique**.
 *
 * La vitesse critique se donne en mètres par seconde, et se lit ainsi : *la vitesse à
 * laquelle ce matériau, lancé contre quelque chose d'immobile, se briserait de
 * lui-même*. Les points de vie d'un bloc valent donc l'énergie cinétique
 * correspondante, `½ · masse · vc²`, en joules — et se comparent directement à ce que
 * le moteur comptabilise dans
 * [com.Atom2Universe.app.games.physics.PhysBody.impactAccum], qui est l'énergie
 * réellement dissipée par les chocs. Rien à convertir, rien à inventer : les dégâts
 * sont ceux que la physique a infligés.
 *
 * **Pourquoi une énergie et pas une impulsion.** Le premier modèle comptait des kg·m/s,
 * et il était irrattrapable : une pierre d'une tonne et demie qui se tasse de six
 * centimètres délivre autant de quantité de mouvement qu'un boulet de douze kilos lancé
 * à cent mètres par seconde. Un mur intact se broyait donc tout seul avant le premier
 * tir. En énergie, le même tassement pèse trois cents joules contre soixante mille pour
 * le boulet.
 *
 * Les valeurs sont calées au banc contre le boulet du jeu — 12 kg, jusqu'à 150 m/s,
 * soit 60 à 135 kJ dans un coup franc. Une assise de rempart d'une tonne et demie
 * demande deux coups, un poteau de charpente un seul, et un monolithe de quatre tonnes
 * quatre ou cinq — mais il se **bascule** bien plus facilement qu'il ne se casse, et
 * c'est le vrai jeu. Le jour où il faudra percer du gros mur pour de bon, le levier est
 * le projectile (boulet lourd, boulet explosif), pas cette table.
 */
enum class Material(
    /** Masse volumique en kg/m³. Le monde est plat : on lui suppose 1 m d'épaisseur. */
    val density: Float,
    val friction: Float,
    /**
     * Élasticité des chocs. **Elle vaut zéro pour tout le monde, et ce n'est pas un
     * oubli.**
     *
     * Mesuré au banc : deux centièmes de rebond — la valeur la plus timide qu'on
     * puisse écrire — suffisent à empêcher un mur de huit assises de se taire. Les
     * pierres se renvoient indéfiniment de quoi franchir le seuil de choc, le mur
     * vibre pour toujours, et `isAtRest` ne dit jamais que le coup est fini. Sans
     * rebond, le même mur est parfaitement immobile.
     *
     * C'est aussi le comportement juste : de la pierre sur de la pierre, ça fait
     * « toc », ça ne rebondit pas. Le rebond est l'affaire du boulet, que la machine
     * règle de son côté.
     */
    val restitution: Float,
    /** Vitesse critique, en m/s. Voir la documentation de la classe. */
    val criticalSpeed: Float,
    val rupture: Rupture,
    /**
     * Vrai pour la maçonnerie, faux pour ce qui se charpente.
     *
     * Ce n'est pas de la taxonomie : c'est ce qui décide de **l'objectif du niveau**.
     * Un village de bois se démolit, une muraille de pierre se renverse — on n'en
     * casse que les assises qu'on touche, le reste bascule intact. Demander la même
     * part de pierres brisées aux deux, c'est rendre l'un facile et l'autre
     * interminable. Voir [TargetRules.winRatio].
     */
    val masonry: Boolean
) {
    /** Le chaume d'un toit : ça ne pèse rien et ça ne tient rien. */
    THATCH(120f, 0.70f, 0f, 3f, Rupture.POUSSIERE, masonry = false),

    /** La glace et le verre : lourds comme de l'eau, fragiles comme du sucre, et ça glisse. */
    ICE(900f, 0.15f, 0f, 4f, Rupture.ECLATS, masonry = true),

    /** Le torchis et la brique crue : le mur des pauvres, et ça se voit. */
    COB(1500f, 0.70f, 0f, 6f, Rupture.POUSSIERE, masonry = true),

    /** La terre d'un talus : elle n'éclate pas, elle s'affaisse, et elle adhère à tout. */
    EARTH(1800f, 0.85f, 0f, 8f, Rupture.POUSSIERE, masonry = true),

    /** Le bois de charpente : le vocabulaire des maisons, des portiques et des cartes. */
    WOOD(700f, 0.55f, 0f, 9f, Rupture.ECLATS, masonry = false),

    /** La pierre de taille : le gros œuvre. Lourde, tenace, et c'est elle qui fait le tas. */
    STONE(2400f, 0.65f, 0f, 12f, Rupture.ECLATS, masonry = true),

    /**
     * Le grès : la pierre des pyramides et des temples. Un peu plus légère que la
     * pierre de taille, sensiblement plus tendre, et elle glisse moins.
     *
     * Elle existe pour une raison de jeu autant que de couleur : une pyramide de pierre
     * de taille serait une montagne qu'on ne raye pas, alors qu'un même volume de grès
     * s'entame au troisième coup. C'est ce qui rend une nécropole jouable.
     */
    SANDSTONE(2200f, 0.72f, 0f, 10f, Rupture.ECLATS, masonry = true),

    /** Le fer : hors de prix, indestructible, et lourd au point d'écraser ce qu'il tient. */
    IRON(7800f, 0.40f, 0f, 60f, Rupture.INCASSABLE, masonry = true)
}

/**
 * Le tempérament des constructions : fidèle à la matière, ou taillé pour le jeu.
 *
 * Le mode réaliste est celui de la table des matériaux : de la vraie pierre, de la
 * vraie densité, et un boulet de douze kilos qui ne peut pas grand-chose contre un
 * rempart — ce qui est exact, et lent.
 *
 * **Le mode arcade n'est pas « la même chose en plus faible ».** Doubler la taille des
 * pierres en 2D quadruple leur masse, donc leurs points de vie : des pierres deux fois
 * plus grosses et deux fois moins tenaces seraient **deux fois plus dures** à abattre.
 * Il faut donc bouger les curseurs ensemble :
 *
 *  - **plus grosses** ([stoneScale]) : chaque coup emporte un morceau qui se voit, et
 *    une construction coûte quatre fois moins de corps au moteur ;
 *  - **plus épaisses** ([detailScale]) : le curseur des pièces qui ne sont pas des
 *    assises — poteaux, poutres, toits, merlons, tonneaux. Il est plus doux que celui
 *    des pierres, parce qu'une charpente épaissie au même facteur remplirait la
 *    maison ;
 *  - **plus légères** ([densityScale]) : sans ça, un bloc de cinq tonnes ne bougerait
 *    pas d'un pouce sous un boulet de douze kilos, et il n'y aurait plus rien à
 *    renverser — or renverser est le plus beau du jeu ;
 *  - **plus fragiles** ([toughnessScale]) : de quoi qu'un coup franc emporte une
 *    pierre entière plutôt que de la fêler.
 *
 * **Le second curseur est né d'un oubli, et il vaut la peine d'être raconté.** La
 * première version ne réglait que la taille des assises. Or la moitié des sites du jeu
 * sont des hameaux, et une maison n'a pas d'assises : elle a des poteaux, des poutres
 * et un toit, tous donnés en mètres fixes. Les hameaux d'arcade sortaient donc
 * **exactement identiques** aux hameaux réalistes, à la masse près — le joueur
 * changeait de mode et ne voyait rien changer. Toute mesure en mètres qui décrit une
 * pièce de construction doit passer par [TargetRules.detail] ou [TargetRules.stone] ;
 * une constante nue est un morceau du décor qui a oublié dans quel jeu il est.
 *
 * Réglé pour qu'une assise de rempart d'arcade — trois mètres sur un et demi, contre
 * un mètre vingt sur cinquante en réaliste — parte d'un seul boulet bien placé, tout
 * en pesant assez pour tomber sur ses voisines.
 */
enum class TargetStyle(
    /**
     * Facteur sur la taille du **site entier** : la largeur et la hauteur de chaque
     * module, et par conséquent tout ce qu'il contient.
     *
     * C'est le curseur qui manquait, et c'est le seul que le joueur voie vraiment.
     * Grossir les pierres à l'intérieur d'un château de taille inchangée ne change que
     * le nombre de joints — de loin, à trois cents mètres, ça ne se remarque pas. Ce
     * qui se remarque, c'est la silhouette : un château deux fois plus haut se voit
     * immédiatement, et ses planches font quatre mètres au lieu de deux.
     */
    val siteScale: Float,
    /** Facteur sur la taille des pierres d'appareil, **à site égal** : assises, merlons. */
    val stoneScale: Float,
    /**
     * Facteur sur l'épaisseur, **à site égal**, des pièces qui ne sont pas des
     * assises : poteaux, poutres, hauteur d'étage, toits, socles de tour, tonneaux.
     */
    val detailScale: Float,
    /** Facteur sur la masse volumique. */
    val densityScale: Float,
    /** Facteur sur la vitesse critique, donc la racine des points de vie. */
    val toughnessScale: Float,
    /**
     * De 0 à 1 : ce que le projectile récupère de son élan quand il **casse** ce
     * qu'il touche. C'est le curseur du « ça passe au travers ».
     *
     * À zéro, la physique décide seule, et elle est impitoyable : un boulet de douze
     * kilos qui percute une pierre de trois tonnes repart en arrière, même si la pierre
     * se brise. C'est exact — c'est aussi tout ce qu'on ne veut pas voir en arcade, où
     * un boulet doit **entrer** dans la construction et en ressortir de l'autre côté.
     *
     * À un, le projectile ne paie que ce qu'il a détruit : on lui rend sa vitesse le
     * long de sa trajectoire d'avant le choc, amputée de l'énergie exacte des points de
     * vie qu'il vient d'emporter. Il ne gagne jamais d'énergie — il ne perd que la
     * bonne — et il ne récupère rien s'il n'a rien cassé. Un boulet qui cogne sans
     * casser rebondit dans les deux modes.
     */
    val pierce: Float
) {
    REALISTE(1f, 1f, 1f, 1f, 1f, 0f),
    ARCADE(2f, 2.6f, 1.6f, 0.07f, 0.26f, 1f)
}

/**
 * Les constantes du champ de cibles. Elles ne sont pas choisies au jugé : les deux
 * premières sortent d'un banc d'essai du moteur, mené avant d'écrire une ligne de
 * génération.
 */
object TargetRules {

    /**
     * Le tempérament en cours. Voir [TargetStyle].
     *
     * C'est un réglage global, et il ne se change pas en cours de partie sans
     * reconstruire le niveau : la masse et les points de vie d'une pierre sont calculés
     * une fois pour toutes à sa création.
     */
    var style: TargetStyle = TargetStyle.ARCADE

    /**
     * Une longueur de pierre d'appareil, à l'échelle du tempérament en cours.
     *
     * Toute dimension d'assise ou de merlon passe par ici. C'est la seule façon de ne
     * pas se retrouver avec un module qui ignore le mode arcade — voir la mésaventure
     * des hameaux racontée dans [TargetStyle].
     */
    fun stone(metres: Float): Float = metres * style.siteScale * style.stoneScale

    /**
     * Une épaisseur de charpente ou d'ornement, à l'échelle du tempérament en cours.
     *
     * Plus douce que [stone] : un poteau grossi autant qu'une assise mangerait la
     * travée qu'il est censé encadrer.
     */
    fun detail(metres: Float): Float = metres * style.siteScale * style.detailScale

    /**
     * Une dimension d'ensemble : l'emprise d'un module, l'écart entre deux modules.
     *
     * C'est le seul curseur qui change la **silhouette** du site, et c'est donc le seul
     * que le joueur remarque de loin.
     */
    fun site(metres: Float): Float = metres * style.siteScale

    /**
     * Épaisseur minimale d'une pièce, en demi-extension : 5 cm, donc une planche de
     * 10 cm.
     *
     * Ce n'est **pas** une question de traversée — les sous-pas adaptatifs du moteur
     * empêchent déjà un boulet à 150 m/s de passer au travers d'une planche de 8 cm,
     * c'est mesuré. C'est une question de coût : [com.Atom2Universe.app.games.physics.PhysWorld]
     * découpe l'image d'après la pièce **la plus mince du monde entier**, machine
     * comprise. Une seule carte trop fine posée à trois cents mètres ralentit tout
     * le reste.
     */
    const val MIN_HALF_THICKNESS = 0.05f

    /**
     * Le joint de maçonnerie : 3 mm laissés entre deux pierres voisines.
     *
     * Deux blocs posés bord à bord avec un écart **exactement nul** mettent le
     * détecteur de collision en difficulté : leurs faces sont confondues, et l'axe
     * séparateur peut aussi bien sortir par le côté que par le haut. Mesuré au banc,
     * une pierre d'une tonne et demie posée tranquillement décollait à 50 cm/s en se
     * mettant à tourner, ce qui suffisait à la faire compter comme un choc violent.
     * Un mur intact se broyait tout seul avant le premier tir.
     *
     * Un joint, si mince soit-il, lève l'ambiguïté. Il ne suffit pas à lui seul — les
     * joints s'additionnent dans une pile, et la pierre du haut d'un mur de huit
     * assises tombe alors de deux centimètres et demi — c'est pourquoi une
     * construction se **tasse** ensuite, voir [TargetField.settle].
     */
    const val JOINT = 0.003f

    /**
     * Durée **maximale** du tassement, en secondes de simulation.
     *
     * Le tassement s'arrête dès que tout dort ; ce plafond n'est là que pour une
     * construction qui ne s'endort jamais, laquelle est de toute façon à jeter.
     */
    const val SETTLE_SECONDS = 8f

    /**
     * Déplacement au-delà duquel une pierre n'a pas fait que se tasser : elle est
     * tombée. Une construction dont une pierre bouge de plus de ça est à jeter.
     *
     * Trente centimètres peut sembler large : c'est qu'un affaissement honnête n'est
     * pas nul. Les joints s'additionnent, et le moteur laisse les corps s'enfoncer de
     * cinq millimètres les uns dans les autres — un mur de douze assises descend donc
     * d'une dizaine de centimètres sans que rien n'aille mal. Ce qu'on cherche à
     * attraper ici, c'est la pierre qui **part**, et celle-là fait des mètres.
     */
    const val SETTLE_TOLERANCE = 0.3f

    /**
     * Part des pierres d'origine qu'il faut avoir brisées pour que le site compte
     * pour rasé.
     *
     * **Ce n'est pas la première règle qu'on a essayée, et l'histoire vaut d'être
     * racontée.** La première demandait de faire descendre la *silhouette* sous une
     * ligne, au cinquième de la hauteur d'origine. C'était joli sur le papier : ça se
     * voyait d'un coup d'œil, ça se dessinait à l'écran, et ça mesurait quelque chose
     * de vrai. Sauf que ce qui tombe ne disparaît pas — ça fait un tas. Une
     * construction parfaitement rasée laisse des gravats de deux mètres de haut, et la
     * ligne était à deux mètres cinquante : le joueur voyait un champ de ruines
     * complet, et le jeu lui répondait « pas encore ». Une règle qui punit un tir
     * parfait est une mauvaise règle, quelle que soit son élégance.
     *
     * On compte donc ce qui a **cassé**, ce que le joueur voit arriver et qu'il
     * provoque, plutôt que la hauteur de ce que la gravité a laissé retomber.
     * Quatre-vingt-cinq pour cent laissent la marge des deux ou trois pierres
     * imprenables — une assise de fondation coincée derrière un tas, un poteau au fond
     * d'une cour — sans jamais demander l'impossible.
     */
    const val WIN_RATIO = 0.75f

    /**
     * Ce que vaut une pierre **renversée** mais entière, par rapport à une pierre
     * brisée.
     *
     * Une demie, et pas une entière : renverser une pierre n'est pas la détruire, elle
     * est toujours là, et on peut encore la casser. C'est aussi ce qui garde un intérêt
     * au dernier tir dans un champ de ruines — chaque pierre couchée qu'on brise vaut
     * une demi-pierre de plus, ce qui donne au joueur quelque chose à gagner là où il
     * n'avait plus qu'à gratter.
     *
     * Elle répare surtout un défaut mesuré : le compteur plafonnait sur les hameaux
     * d'arcade parce que les derniers poteaux, couchés au sol, se laissaient survoler.
     * Le site était rasé à l'œil et le jeu répondait « pas encore », ce qui est
     * exactement le reproche qui avait déjà fait tomber la ligne de ruine.
     */
    const val TOPPLED_WORTH = 0.5f

    /**
     * De combien une pierre doit avoir bougé pour compter pour renversée, en mètres et
     * en fraction de sa propre taille.
     *
     * Les deux, parce qu'un site d'arcade a des pierres de six mètres et un site
     * réaliste des planches de trente centimètres : une valeur en mètres seule
     * déclarerait la planche renversée pour un frisson, une valeur en fractions seule
     * laisserait la pierre glisser d'un mètre sans rien dire.
     */
    const val TOPPLED_SHIFT = 0.6f
    const val TOPPLED_SHIFT_RATIO = 0.35f

    /** Rotation, en radians, au-delà de laquelle une pierre a basculé. */
    const val TOPPLED_TURN = 0.35f

    /**
     * Objectif en mode réaliste, sur un site **de charpente** : un village de bois.
     *
     * Le bois casse pour de bon — un poteau qui prend un boulet éclate — mais le
     * réaliste ne pardonne pas la traversée : le boulet rebondit, et il faut un tir
     * par pièce ou presque. Soixante pour cent est ce qu'un joueur patient obtient
     * sans avoir l'impression de gratter.
     */
    const val WIN_RATIO_WOOD = 0.60f

    /**
     * Objectif en mode réaliste, sur un site **de maçonnerie**.
     *
     * Trente pour cent, et ce n'est pas de la générosité : **on ne détruit pas une
     * muraille, on la renverse**. Un boulet de douze kilos casse l'assise qu'il touche
     * et fait basculer les vingt autres, lesquelles atterrissent intactes. Le compteur
     * de pierres brisées mesure donc très mal ce que le joueur a réellement fait à la
     * construction, et lui demander quatre-vingt-cinq pour cent reviendrait à lui
     * demander de viser une à une des pierres déjà couchées par terre.
     *
     * L'arcade n'a pas ce problème : ses pierres sont assez fragiles pour que tomber
     * les casse, et le projectile traverse.
     */
    const val WIN_RATIO_STONE = 0.30f

    /**
     * L'objectif d'un site donné, d'après le tempérament du jeu et ce dont il est bâti.
     *
     * [masonryShare] est la part de pierres de maçonnerie, de 0 (tout en bois) à 1
     * (tout en pierre). Entre les deux, on interpole : une ferme fortifiée est une
     * maison au milieu d'un rempart, et son objectif doit tomber entre celui de l'une
     * et celui de l'autre plutôt que de basculer d'un seuil arbitraire.
     */
    fun winRatio(masonryShare: Float): Float {
        if (style == TargetStyle.ARCADE) return WIN_RATIO
        val t = masonryShare.coerceIn(0f, 1f)
        return WIN_RATIO_WOOD + (WIN_RATIO_STONE - WIN_RATIO_WOOD) * t
    }

    /**
     * Marge de part et d'autre de l'emprise, en mètres, à l'intérieur de laquelle
     * un morceau compte encore dans la silhouette.
     *
     * Un bloc expédié à trente mètres n'est plus la construction, c'est un caillou dans
     * un champ. Ne sert plus qu'à la **mesure** de la hauteur restante, dont les bancs
     * d'essai se servent pour dire si une construction s'est écroulée toute seule ;
     * l'objectif du joueur, lui, se compte en pierres brisées.
     */
    const val FOOTPRINT_MARGIN = 5f

    /**
     * Part des points de vie en dessous de laquelle un choc, dans une image, ne fend
     * rien du tout : une égratignure n'est pas une fêlure.
     *
     * Cinq pour cent laisse passer tout ce qui compte et arrête tout ce qui ne compte
     * pas. Un tremblement de tassement pèse trois kilojoules contre les cinquante d'une
     * demi-assise, soit six pour cent — juste sous la barre. Un coup de boulet franc en
     * pèse soixante, soit plus de la moitié d'une assise entière. L'écart entre les deux
     * est d'un facteur vingt : le seuil n'a pas à être fin.
     */
    const val DAMAGE_FLOOR = 0.05f

    /** Nombre maximal de débris vivants. Au-delà, ce qui casse tombe en poussière. */
    const val MAX_DEBRIS = 50

    /** En dessous de cette demi-taille, un morceau ne vaut plus la peine d'exister. */
    const val MIN_FRAGMENT_HALF = 0.15f

    /** Vitesse maximale que l'éclatement donne aux morceaux, en m/s. */
    const val MAX_BURST = 2f

    /** Vitesse maximale qu'un souffle peut donner à un bloc, en m/s. */
    const val MAX_BLAST_SPEED = 12f

    /**
     * Impulsion spécifique d'un souffle au point d'explosion, en pascals-secondes.
     *
     * **C'est ce qui a remplacé la poussée en énergie, et l'écart est tout le sujet.**
     * L'ancienne donnait à chaque bloc la vitesse dont l'énergie cinétique valait ce que
     * le souffle lui versait : une pierre de trois tonnes et un fétu de bois recevaient
     * alors des vitesses dans un rapport de racine de leurs masses, c'est-à-dire presque
     * la même chose. Une explosion ne fait pas ça. Elle pousse une **surface**, et ce
     * qu'elle communique se divise par la masse : la charpente s'envole, la muraille
     * bouge à peine, et c'est très exactement l'image qu'on a en tête.
     *
     * Neuf cents pascals-secondes donnent six mètres par seconde à une assise de
     * rempart d'arcade et onze à un poteau de maison, ce qui se voit sans être absurde.
     */
    const val BLAST_IMPULSE = 900f

    /**
     * Budget de corps d'une construction, débris exclus.
     *
     * Mesuré au banc : à 240 corps le moteur tient l'image moyenne mais l'image de
     * l'impact coûte vingt fois plus que les autres, ce qui se voit. À 120 il n'y a
     * plus rien à voir. Le budget est donc un plafond de génération, pas une limite
     * technique — et la parade au manque de détail n'est pas d'ajouter des corps,
     * c'est le bloc composé, qui met plusieurs pierres dans un seul corps.
     */
    const val BODY_BUDGET = 130

    /**
     * Nombre de **corps** qu'on peut empiler à la verticale sans que la pile finisse
     * par s'écrouler toute seule.
     *
     * Attention à ce que ça veut dire, parce que ce n'est pas le nombre d'étages
     * visibles. C'est le nombre de contacts superposés que le solveur doit tenir. Les
     * impulsions séquentielles propagent l'effort d'un contact à la fois : plus la pile
     * est profonde, plus il faut de passes pour que la pierre du bas apprenne ce
     * qu'elle porte, et passé une certaine profondeur elle ne l'apprend jamais.
     *
     * Mesuré, avec les pierres de rempart du jeu :
     *
     *  - 16 corps empilés : ça tient aux 16 passes du monde de jeu ;
     *  - 20 corps : il faut monter à 64 passes, et ça tient de justesse ;
     *  - 24 corps : rien ne le sauve — ni les passes, ni un joint plus fin, ni un
     *    enfoncement plus serré, ni un mur trois fois plus large. Le même mur de 24 m
     *    monté en 48 corps s'affaisse de **quatre mètres** avant de tomber.
     *
     * Ce n'est ni une question de hauteur ni de masse : des planches de kapla, légères
     * et fines, tiennent **moins** haut que la pierre, parce qu'elles font plus de
     * contacts par mètre.
     *
     * La parade n'est donc pas d'empiler moins, c'est d'empiler **moins de corps** —
     * autrement dit le bloc composé. Le même mur de 24 m monté en 12 corps de quatre
     * pierres chacun ne s'affaisse plus que de 7 cm et reste parfaitement immobile. Le
     * joueur voit quarante-huit assises, le moteur en compte douze, et le jour où une
     * pierre casse elle éclate en ses quatre pierres. C'est très exactement ce pour quoi
     * [Block.compound] existe.
     */
    const val MAX_STACKED_BODIES = 16

    /**
     * Profondeur d'empilement que les modules se donnent pour cible : la limite dure
     * est à 16, on construit à 12 pour avoir de la marge quand deux modules se posent
     * l'un sur l'autre.
     */
    const val COMFORTABLE_STACK = 12

    /**
     * Seuils d'usure des craquelures, de la première fêlure à la pierre sur le point de
     * rompre. Trois niveaux : la vue en tire trois dessins, et le joueur sait d'un coup
     * d'œil où il en est sans qu'aucun chiffre ne s'affiche.
     */
    val CRACK_THRESHOLDS = floatArrayOf(0.15f, 0.40f, 0.70f)

    /**
     * Délai au bout duquel les chocs comptent, même si la construction n'a jamais
     * réussi à s'immobiliser, en secondes.
     *
     * Sans lui, une construction qui tremble un peu serait **invulnérable pour
     * toujours** : le compteur de dégâts n'attend qu'un repos qui ne vient jamais. Le
     * filet de sécurité est plus important qu'il n'en a l'air — c'est exactement ce
     * qu'on ne remarque pas en jouant.
     */
    const val ARM_TIMEOUT = 4f

    /**
     * Durée pendant laquelle une construction doit être **restée** immobile avant que
     * les chocs comptent, en secondes.
     *
     * Le repos instantané ne prouve rien : une construction fraîchement chargée a
     * toutes ses vitesses à zéro et paraît donc parfaitement calme, alors qu'elle n'a
     * pas encore commencé à s'asseoir.
     */
    const val ARM_CALM = 0.6f

    /**
     * Distance, de part et d'autre de l'emprise, à laquelle la cible se réveille.
     *
     * Voir [TargetField.dormant]. Quarante mètres laissent au château plus d'un quart
     * de seconde pour se remettre debout devant un boulet à cent cinquante mètres par
     * seconde — largement plus que l'image nécessaire.
     */
    const val WATCH_MARGIN = 40f

    /**
     * Marge, en mètres, autour d'un projectile, dans laquelle une pierre qui se brise
     * lui est attribuée.
     *
     * Elle s'ajoute au chemin parcouru dans l'image : à cent cinquante mètres par
     * seconde, un boulet franchit deux mètres et demi entre deux images, et la pierre
     * qu'il vient d'emporter est déjà loin derrière lui quand on regarde.
     */
    const val PIERCE_REACH = 1.5f

    /** Temps qu'un petit débris passe immobile avant d'être ramassé, en secondes. */
    const val DEBRIS_LIFETIME = 3f

    /** Taille en dessous de laquelle un débris immobile finit par être ramassé. */
    const val DEBRIS_SWEEP_HALF = 0.3f
}
