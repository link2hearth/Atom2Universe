package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import com.Atom2Universe.app.games.physics.Shape
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Une pierre vivante : sa description, son corps dans le moteur, et ce qu'il lui
 * reste à encaisser.
 *
 * C'est aussi ce que la vue trouvera dans [PhysBody.tag] : elle a là tout ce qu'il
 * lui faut pour dessiner — le matériau, les formes, et l'usure.
 */
class TargetPiece internal constructor(
    val block: Block,
    val body: PhysBody,
    /**
     * À quelle génération de rupture cette pierre appartient.
     *
     * Zéro pour une pierre de la construction d'origine, un pour un éclat, deux pour un
     * morceau, trois pour un grain. C'est ce compteur qui a remplacé le booléen
     * « débris » : il disait seulement *si* une pierre était née d'une rupture, jamais
     * *de combien de ruptures*, et il n'y avait donc aucun moyen d'écrire « on peut
     * encore casser ça, mais pas indéfiniment ».
     */
    val tier: Int
) {
    val material: Material get() = block.material
    val role: Role get() = block.role

    /** Vrai si cette pierre est née d'une rupture, et non de la construction d'origine. */
    val debris: Boolean get() = tier > 0

    /**
     * Points de vie, en joules — et un gravat en a **sept fois plus** qu'un bloc neuf de
     * la même taille.
     *
     * Ce n'est pas une faveur faite au gravier, c'est ce qui empêche un effondrement de
     * se pulvériser lui-même. Voir [TargetRules.RUBBLE_TOUGHNESS], où le calcul est
     * fait.
     */
    val maxHp: Float =
        block.hp * (if (tier > 0) TargetRules.RUBBLE_TOUGHNESS else 1f)
    var hp: Float = maxHp
        internal set

    /** Là où on l'a posée. C'est de là qu'on mesure si elle a été renversée. */
    internal val poseX = block.x
    internal val poseY = block.y
    internal val poseAngle = block.angle

    /**
     * Vrai quand la pierre n'est plus où on l'avait mise : elle a glissé, basculé, ou
     * dévalé la pente d'un tas.
     *
     * Deux mesures et non une, parce qu'une pierre se renverse de deux façons. Celle
     * qui **part** franchit une distance ; celle qui **bascule sur place** ne bouge
     * presque pas mais tourne. La première manquerait un monolithe tombé à la
     * verticale, la seconde un merlon qui a roulé au bas du mur sans jamais se
     * retourner.
     */
    val toppled: Boolean
        get() {
            if (debris) return false
            val dx = body.x - poseX
            val dy = body.y - poseY
            val seuil = maxOf(
                TargetRules.TOPPLED_SHIFT,
                TargetRules.TOPPLED_SHIFT_RATIO * body.boundingRadius
            )
            if (dx * dx + dy * dy > seuil * seuil) return true
            var da = body.angle - poseAngle
            val twoPi = 2f * kotlin.math.PI.toFloat()
            while (da > kotlin.math.PI) da -= twoPi
            while (da < -kotlin.math.PI) da += twoPi
            return kotlin.math.abs(da) > TargetRules.TOPPLED_TURN
        }

    /**
     * Rayon de la flamme que cette pierre porte, en mètres, ou zéro si elle n'en porte
     * pas.
     *
     * **Une lumière appartient à une pierre**, et c'est toute l'idée. Elle n'a pas de
     * position à elle : elle est là où la pierre est, elle bascule quand la pierre
     * bascule, elle tombe quand la pierre tombe, et elle disparaît quand la pierre
     * disparaît. Éteindre les fenêtres d'un village en le démolissant n'a donc demandé
     * aucun code d'extinction — c'est une conséquence, pas une règle.
     */
    var lightRadius = 0f
        internal set

    /** Teinte de la flamme, en ARGB : une bougie n'est pas une torche. */
    var lightTint = 0
        internal set

    /** Temps passé immobile, pour le ramassage des petits débris. */
    internal var restTimer = 0f

    /** 0 = intacte, 1 = sur le point de rompre. */
    val wear: Float get() = if (maxHp <= 0f) 0f else (1f - hp / maxHp).coerceIn(0f, 1f)

    /**
     * Niveau de craquelure, de 0 (intacte) à 3 (elle ne tiendra pas un coup de plus).
     *
     * C'est le seul retour que le joueur ait sur une pierre qu'il a touchée sans la
     * casser, et il en a besoin : sans lui, un tir qui enlève la moitié de la vie d'une
     * assise ressemble exactement à un tir qui n'a rien fait. Trois niveaux suffisent à
     * lire une construction d'un coup d'œil, et l'usure ne redescend jamais — une pierre
     * fêlée le reste.
     *
     * À la vue de dessiner : quelques traits fins au premier niveau, un réseau de
     * fissures au deuxième, une pierre visiblement éclatée et rongée sur les bords au
     * troisième. Le matériau est disponible dans [material], donc le verre peut se
     * fendiller autrement que la pierre.
     */
    val crackLevel: Int
        get() {
            val w = wear
            var level = 0
            for (t in TargetRules.CRACK_THRESHOLDS) if (w >= t) level++
            return level
        }

    val broken: Boolean get() = hp <= 0f
}

/**
 * Le champ de cibles : il instancie une [Structure] dans le monde physique, applique
 * les dégâts que la physique a réellement infligés, casse ce qui doit casser, et dit
 * si la construction est rasée.
 *
 * **Les dégâts ne sont pas inventés.** Le moteur comptabilise déjà, pour chaque corps,
 * l'énergie dissipée par les vrais chocs — les contacts au repos en sont exclus, et le
 * poids qu'un contact porte par ailleurs aussi, sans quoi une pierre paierait le mur
 * qu'elle soutient. C'est cette énergie, en joules, qu'on retranche à des points de vie
 * exprimés dans la même unité. Un boulet qui frappe fort fait mal, une tour qui
 * s'écroule sur ses propres assises les fend, et rien de tout ça n'a demandé de règle
 * spéciale.
 *
 * **Les débris ne se cassent plus.** C'est le seul écart volontaire avec la physique,
 * et il est là pour une raison de jeu : l'objectif du joueur est de faire descendre la
 * silhouette sous une ligne, donc il lui faut un **tas**. Si le gravier se pulvérisait
 * à son tour, un effondrement ferait disparaître la construction au lieu de l'aplatir,
 * et le niveau se gagnerait tout seul. Une pierre déjà brisée s'entasse, elle ne se
 * brise plus : c'est aussi à peu près vrai dehors.
 */
class TargetField(private val world: PhysWorld, seed: Long = 1L) {

    private val rng = Random(seed)
    private val live = ArrayList<TargetPiece>()
    private val dead = ArrayList<TargetPiece>()
    private val pose = FloatArray(3)

    /** Toutes les pierres encore sur le terrain, construction et débris confondus. */
    val pieces: List<TargetPiece> get() = live

    /**
     * Le relief sur lequel la construction est posée.
     *
     * Le champ de cibles ne s'en sert que pour une chose — savoir à quelle profondeur
     * une pierre est réputée sortie du monde — mais cette chose-là compte : sans lui, le
     * plancher est une constante, et un site bâti au fond d'un vallon passe dessous.
     * Il arrive avec la construction, dans [load], pour qu'on ne puisse pas l'oublier.
     */
    var terrain: Terrain = Terrain.FLAT
        private set

    /**
     * Prévenu quand une pierre disparaît sans rien laisser : dernier palier, morceau
     * trop petit, ou budget de gravier saturé.
     *
     * C'est un crochet et non un appel direct aux effets, parce que le champ de cibles
     * ne connaît ni la vue ni le jeu — et qu'il doit rester utilisable dans un test sans
     * qu'aucune particule n'existe. Les arguments sont le point et le rayon de ce qui
     * vient de s'en aller.
     */
    var onDust: ((Float, Float, Float) -> Unit)? = null

    /** Hauteur de la silhouette au moment où la construction a été posée. */
    var baseHeight = 0f
        private set

    var left = 0f
        private set
    var right = 0f
        private set

    /** Masse de la construction d'origine. Les débris n'en font pas partie. */
    var totalMass = 0f
        private set

    /** Nombre de pierres de la construction d'origine. Les débris n'en font pas partie. */
    var pieceTotal = 0
        private set

    /** Nombre de pierres d'origine qui ont rompu, qu'elles aient laissé des débris ou non. */
    var pieceBroken = 0
        private set

    private var debrisCount = 0

    /**
     * Les projectiles qu'on suit, et ce que chacun a détruit dans la dernière image.
     *
     * Deux listes parallèles plutôt qu'une table : il y en a au plus une poignée, elles
     * sont relues à chaque image de vol, et une table de hachage y ferait des déchets
     * au pire moment. Le jeu y inscrit ses boulets ; le champ y répond « celui-ci vient
     * d'emporter tant de joules de pierre », et le jeu en fait ce qu'il veut — voir
     * [TargetStyle.pierce].
     */
    private val piercers = ArrayList<PhysBody>(8)
    private val pierceCosts = ArrayList<Float>(8)

    /** Déclare un projectile : on saura désormais ce qu'il casse lui-même. */
    fun trackPiercer(body: PhysBody) {
        if (piercers.contains(body)) return
        piercers.add(body)
        pierceCosts.add(0f)
    }

    /** Oublie tous les projectiles suivis. À faire entre deux tirs. */
    fun forgetPiercers() {
        piercers.clear()
        pierceCosts.clear()
    }

    /**
     * Ce que ce projectile a détruit dans la dernière image, en joules — c'est-à-dire
     * les points de vie qu'il restait aux pierres qu'il vient d'achever.
     *
     * Zéro s'il n'a rien cassé, ce qui est le cas le plus fréquent : une pierre fêlée
     * ne compte pas, seule la rupture compte.
     */
    fun pierceCost(body: PhysBody): Float {
        val i = piercers.indexOf(body)
        return if (i < 0) 0f else pierceCosts[i]
    }

    /**
     * Attribue une pierre rompue au projectile qui l'a achevée, s'il y en a un.
     *
     * On cherche le plus proche, et la portée de recherche tient compte du chemin
     * parcouru dans l'image : sans ça, un boulet rapide serait toujours trop loin de ce
     * qu'il vient de casser. Une pierre écrasée par l'effondrement d'une tour n'est
     * attribuée à personne, et c'est exactement ce qu'on veut : le boulet ne doit pas
     * être remboursé d'une ruine qu'il a seulement déclenchée.
     */
    private fun creditPiercer(p: TargetPiece, cost: Float, dt: Float) {
        if (cost <= 0f) return
        var best = -1
        var bestD = Float.MAX_VALUE
        for (i in piercers.indices) {
            val b = piercers[i]
            if (!b.inWorld) continue
            val d = hypot(b.x - p.body.x, b.y - p.body.y)
            val reach = b.boundingRadius + p.body.boundingRadius +
                hypot(b.vx, b.vy) * dt + TargetRules.PIERCE_REACH
            if (d <= reach && d < bestD) {
                bestD = d
                best = i
            }
        }
        if (best >= 0) pierceCosts[best] = pierceCosts[best] + cost
    }

    /**
     * Vrai une fois que la construction s'est posée et que les chocs comptent.
     *
     * Voir [update] : avant ça, ils sont ignorés — et passé
     * [TargetRules.ARM_TIMEOUT] ils comptent de toute façon, sans quoi une
     * construction qui tremble un peu serait invulnérable pour toujours.
     */
    var armed = false
        private set

    private var armingTimer = 0f
    private var calmTimer = 0f

    /**
     * Vrai quand la cible est **mise en veille** : ses corps sont retirés de la
     * simulation tant que rien ne vient les toucher.
     *
     * C'est de très loin l'optimisation la plus rentable du jeu, et elle ne coûte rien
     * parce que le moteur savait déjà le faire — [PhysBody.inWorld] existait pour le
     * contrepoids qu'on tient à la main. Mesuré : une image de vol coûte 30 µs sans
     * cible, et 7 300 µs avec un château. Le facteur deux cent quarante s'explique en
     * une phrase : le boulet file à cent cinquante mètres par seconde, le moteur
     * découpe donc chaque image en une vingtaine de sous-pas pour que rien ne traverse
     * rien, et **les quatre-vingts pierres du château sont résolues à chacun de ces
     * sous-pas** alors qu'elles dorment à trois cents mètres de là.
     *
     * En veille, elles ne coûtent plus rien du tout : ni détection, ni résolution, ni
     * même leur épaisseur dans le calcul des sous-pas.
     */
    var dormant = false
        private set

    /** Nombre de corps que le champ de cibles occupe dans le moteur. */
    val bodyCount: Int get() = live.size

    /** Vrai quand plus une pierre ne bouge — la construction, pas la machine. */
    fun piecesAtRest(): Boolean {
        for (p in live) if (!p.body.atRest()) return false
        return true
    }

    /**
     * La ligne de ruine : tout doit passer dessous. C'est elle que la vue dessine en
     * pointillé au travers de la construction, et c'est tout l'énoncé du niveau.
     */
    /**
     * Part des pierres d'origine qui ont rompu, de 0 à 1.
     *
     * On compte les **pierres**, pas les tonnes. Le joueur voit tomber des morceaux, il
     * ne voit pas des kilos : une règle qui se vérifie du regard vaut mieux qu'une
     * règle exacte qu'il faudrait croire sur parole.
     */
    val brokenRatio: Float
        get() = if (pieceTotal > 0) (pieceBroken.toFloat() / pieceTotal).coerceIn(0f, 1f) else 0f

    /** Nombre de pierres d'origine encore entières mais qui ne sont plus à leur place. */
    var pieceToppled = 0
        private set

    /**
     * Ce que vaut le travail accompli, de 0 à 1 : les pierres brisées, plus les pierres
     * renversées comptées pour une demie.
     *
     * C'est **ce chiffre-là** que l'objectif regarde, et pas le seul compte des pierres
     * brisées. Une muraille ne se détruit pas, elle se renverse : demander au joueur de
     * briser une à une des assises déjà couchées par terre reviendrait à ne jamais
     * récompenser le seul coup qui compte vraiment, celui qui fait tomber le mur.
     *
     * Et comme une pierre couchée peut encore être brisée, il reste toujours quelque
     * chose à gagner dans un champ de ruines : une demi-pierre de plus à chaque fois.
     */
    val score: Float
        get() {
            if (pieceTotal <= 0) return 0f
            val v = pieceBroken + TargetRules.TOPPLED_WORTH * pieceToppled
            return (v / pieceTotal).coerceIn(0f, 1f)
        }

    // ── Pose et dépose ────────────────────────────────────────────────────────

    fun load(structure: Structure, terrain: Terrain = Terrain.FLAT) {
        clear()
        this.terrain = terrain
        baseHeight = structure.baseHeight
        left = structure.left
        right = structure.right
        totalMass = structure.totalMass
        pieceTotal = structure.blocks.size
        pieceBroken = 0
        pieceToppled = 0
        winRatio = TargetRules.winRatio(structure.masonryShare)
        armed = false
        armingTimer = 0f
        calmTimer = 0f
        dormant = false
        for (b in structure.blocks) spawn(b, tier = 0)
        lightUp()
    }

    /**
     * Allume le site : quelques feux posés sur les pierres qui les porteraient.
     *
     * **Le choix se fait ici et pas à la génération**, parce qu'une lumière n'est pas
     * une pièce d'architecture : elle ne pèse rien, ne porte rien, ne casse rien, et un
     * plan de château n'a pas à savoir où le veilleur pose sa torche. Elle se pose donc
     * au chargement, sur la construction déjà bâtie, à partir du tirage du champ — donc
     * de façon reproductible.
     *
     * Deux règles, et elles suffisent. **À hauteur d'homme** : une lumière se pose entre
     * un et six mètres au-dessus de son propre sol, là où vivent les gens et où le
     * joueur regarde ; une torche au sommet d'une tour de vingt mètres serait une
     * étoile de plus. Et **espacées** : une par tranche de front, sinon un mur de
     * quarante assises s'allumerait comme une vitrine.
     */
    private fun lightUp() {
        if (live.isEmpty()) return
        val pas = TargetRules.site(TargetRules.LIGHT_SPACING)
        var prochaine = left
        for (p in live) {
            val b = p.body
            if (b.x < prochaine) continue
            val sol = terrain.heightAt(b.x)
            val hauteur = b.y - sol
            if (hauteur < TargetRules.site(1f) || hauteur > TargetRules.site(6f)) continue
            // Le bois s'éclaire à la bougie, la pierre à la torche : c'est la même
            // flamme, mais on n'accroche pas une torche dans une chambre.
            val bois = !p.material.masonry || p.material == Material.COB
            p.lightRadius = TargetRules.site(if (bois) 2.2f else 3.4f)
            p.lightTint = if (bois) 0xFFFFC46A.toInt() else 0xFFFF8A3C.toInt()
            prochaine = b.x + pas
        }
    }

    fun clear() {
        for (p in live) world.remove(p.body)
        live.clear()
        dead.clear()
        debrisCount = 0
        armed = false
        armingTimer = 0f
        calmTimer = 0f
        dormant = false
        pieceTotal = 0
        pieceBroken = 0
        pieceToppled = 0
        baseHeight = 0f
        totalMass = 0f
        left = 0f
        right = 0f
    }

    /**
     * Remet tous les corps de la cible dans le monde, tels qu'ils sont.
     *
     * Sert quand le jeu vide le monde pour remonter sa machine : le joueur qui règle sa
     * poutre entre deux tirs ne doit pas voir le château se reconstruire. Les corps
     * existent toujours — avec leurs poses, leurs vitesses et leurs fêlures — seule la
     * liste du monde a été effacée.
     */
    fun reattach() {
        for (p in live) {
            p.body.inWorld = !dormant
            world.add(p.body)
        }
    }

    private fun spawn(b: Block, tier: Int): TargetPiece {
        val debris = tier > 0
        // Toujours par le constructeur composé, même pour une seule forme : c'est le
        // seul qui sache placer une forme tournée dans son corps, et il recentre sur
        // le centre de masse exactement comme [Block] l'a fait de son côté.
        val body = PhysBody.compound(b.mass) {
            for (p in b.parts) {
                if (p.shape == Shape.CIRCLE) circle(p.radius, p.localX, p.localY)
                else box(p.halfW, p.halfH, p.localX, p.localY, p.localAngle)
            }
        }
        body.x = b.x
        body.y = b.y
        body.angle = b.angle
        body.friction = b.material.friction
        body.restitution = b.material.restitution
        body.category = if (debris) TrebuchetCategory.DEBRIS else TrebuchetCategory.TARGET
        body.collidesWith = TrebuchetCategory.TARGET_MASK
        if (b.role == Role.FOUNDATION) {
            body.lockPosition = true
            body.lockRotation = true
        }
        body.refreshMass()

        val piece = TargetPiece(b, body, tier)
        body.tag = piece
        world.add(body)
        live.add(piece)
        if (debris) debrisCount++
        return piece
    }

    // ── Ce qui se passe à chaque image ────────────────────────────────────────

    /**
     * À appeler après [PhysWorld.stepFrame], une fois par image.
     *
     * L'ordre compte : on lit les impacts de l'image qui vient d'être simulée, on les
     * transforme en dégâts, **puis** on remet les compteurs à zéro. Ne pas les remettre
     * à zéro reviendrait à réappliquer indéfiniment le même choc.
     */
    fun update(dt: Float) {
        if (live.isEmpty()) return

        // Tant que la construction ne s'est pas posée une première fois, **aucun choc
        // ne compte**.
        //
        // Une construction qu'on vient de charger n'est pas encore en équilibre : le
        // solveur met quelques images à retrouver les efforts qui tiennent la pile, et
        // pendant ce temps les pierres se tassent en se donnant de petits coups. Chacun
        // est dérisoire — trois kilojoules contre les cinquante d'une demi-assise — mais
        // rien ne les guérit, et au bout de quelques secondes de tassement une pierre
        // finit par céder toute seule. Mesuré : un mur de huit assises perdait une
        // pierre à la cent-quarantième image, sans que personne y ait touché, puis
        // s'écroulait pour de bon.
        //
        // Rien ne se perd à attendre : la cible est à des centaines de mètres, et le
        // premier boulet met plusieurs secondes à arriver.
        if (!armed) {
            armingTimer += dt
            // Il ne suffit **pas** que rien ne bouge : il faut que rien n'ait bougé
            // depuis un moment.
            //
            // Une construction qu'on vient de charger est immobile par construction —
            // toutes les vitesses sont nulles — et le test du repos est donc vrai dès la
            // première image, alors que le solveur n'a pas encore retrouvé les efforts
            // qui tiennent la pile. Ce qui suit est un affaissement de quelques
            // millimètres, invisible à l'œil, et pourtant deux corps de dix tonnes qui se
            // rasseyent l'un sur l'autre échangent de quoi les fendre. Une tour perdait
            // ainsi un quart de sa vie sans que rien ne bouge à l'écran.
            calmTimer = if (piecesAtRest()) calmTimer + dt else 0f
            if (calmTimer >= TargetRules.ARM_CALM || armingTimer >= TargetRules.ARM_TIMEOUT) {
                armed = true
            }
            world.clearImpacts()
            // La mise en veille se décide même avant l'armement : une cible qui attend
            // le premier boulet ne doit rien coûter non plus.
            updateDormancy()
            // Le compte des renversées, lui, n'attend pas l'armement : une pierre
            // poussée avant que les chocs ne comptent est renversée quand même, et un
            // compteur qui ne la verrait pas mentirait jusqu'au tir suivant.
            countToppled()
            return
        }

        updateDormancy()
        if (dormant) return

        for (i in pierceCosts.indices) pierceCosts[i] = 0f

        var someBroke = false
        for (p in live) {
            // Les gravats prennent des coups comme le reste : c'est ce qui permet au
            // projectile de les payer, donc de les écarter au lieu de les traverser.
            if (p.material.rupture == Rupture.INCASSABLE) continue
            val impact = p.body.impactAccum
            if (impact <= p.maxHp * TargetRules.DAMAGE_FLOOR) continue
            // Ce qu'il lui restait de vie est exactement ce que son bourreau a dû payer
            // pour l'achever. Le surplus du coup, lui, n'est pas perdu : il repart avec
            // le projectile, et c'est tout le principe de la traversée.
            val reste = p.hp
            p.hp -= impact
            if (p.hp <= 0f) {
                someBroke = true
                creditPiercer(p, reste, dt)
            }
        }
        world.clearImpacts()

        if (someBroke) breakDead()
        countToppled()
        sweepDebris(dt)
    }

    /**
     * Endort la cible quand plus rien ne bouge et que rien n'approche, la réveille dès
     * que quelque chose entre dans son champ de veille.
     *
     * La marge de veille est large — quarante mètres — parce qu'elle doit couvrir bien
     * plus qu'une image de vol : à cent cinquante mètres par seconde, un boulet en
     * franchit deux et demi par image, et la cible a donc plus d'un quart de seconde
     * pour se remettre debout avant qu'il n'arrive. Un réveil trop tardif serait un
     * boulet qui traverse un château endormi.
     */
    private fun updateDormancy() {
        val approche = somethingApproaching()
        if (dormant) {
            if (!approche) return
            // Elles rentrent dans le monde **endormies** : rien ne les a touchées, et
            // le premier contact venu les réveillera de lui-même, pierre par pierre.
            for (p in live) p.body.inWorld = true
            dormant = false
            return
        }
        if (approche || !piecesAtRest()) return
        // On fige des corps déjà immobiles : on met quand même les vitesses à zéro,
        // pour qu'aucun reliquat ne les fasse dériver au réveil.
        for (p in live) {
            // Et on les endort **pour de bon**, pas seulement le temps de la veille.
            //
            // Sans cette ligne, la mise en veille était un cadeau empoisonné : la
            // cible se réveille quarante mètres avant le boulet, soit un quart de
            // seconde, et la mise en sommeil du moteur demande quatre dixièmes
            // d'immobilité pour se déclencher. Les pierres n'avaient donc jamais le
            // temps de se rendormir, et le quart de seconde qui précède l'impact —
            // exactement celui que le joueur regarde — se jouait avec tout le château
            // dans le solveur, à trente-deux sous-pas par image.
            p.body.sleep()
            p.body.inWorld = false
            world.forgetContacts(p.body)
        }
        dormant = true
    }

    /** Vrai si un corps mobile étranger à la cible est dans son champ de veille. */
    private fun somethingApproaching(): Boolean {
        val lo = left - TargetRules.WATCH_MARGIN
        val hi = right + TargetRules.WATCH_MARGIN
        for (b in world.bodies) {
            if (b.invMass == 0f && b.invI == 0f) continue
            if (b.tag is TargetPiece) continue
            if (b.x in lo..hi) return true
        }
        return false
    }

    /** Réveille la cible sur-le-champ : à appeler avant de lui faire quoi que ce soit. */
    fun wake() {
        if (!dormant) return
        for (p in live) {
            p.body.inWorld = true
            p.body.wake()
        }
        dormant = false
    }

    /**
     * Un souffle d'explosion, centré en ([x], [y]).
     *
     * Rien ne s'en sert encore : c'est le point d'accroche du boulet explosif. Il est
     * écrit maintenant parce qu'il **prouve que le modèle de dégâts tient debout tout
     * seul** — la même unité, les mêmes points de vie, la même rupture, sans que la
     * source du coup ait la moindre importance.
     *
     * [energy] est une énergie en joules, comme les points de vie ; elle décroît
     * linéairement jusqu'à [radius], et sert à la fois de dégât et de poussée.
     */
    fun blast(
        x: Float,
        y: Float,
        energy: Float,
        radius: Float,
        impulse: Float = TargetRules.blastImpulse()
    ) {
        if (radius <= 0f) return
        wake()
        var someBroke = false
        for (p in live) {
            val dx = p.body.x - x
            val dy = p.body.y - y
            val d = hypot(dx, dy)
            if (d > radius) continue
            val falloff = 1f - d / radius
            val e = energy * falloff

            // La poussée est une **impulsion sur une surface**, pas un versement
            // d'énergie : le souffle communique tant de kilogrammes-mètres par seconde
            // par mètre carré exposé, et la vitesse qui en sort se divise par la masse.
            // C'est ce qui envoie voler une planche et laisse une assise sur place.
            // Elle est bornée, sinon un éclat léger partirait en orbite.
            val nx = if (d > 1e-3f) dx / d else 0f
            val ny = if (d > 1e-3f) dy / d else 1f
            val dv = minOf(
                impulse * falloff * p.block.area * p.body.invMass,
                TargetRules.MAX_BLAST_SPEED
            )
            // Une pierre endormie ne serait pas intégrée : la poussée lui serait
            // versée puis oubliée. Toute vitesse posée à la main réveille son corps.
            p.body.wake()
            p.body.vx += nx * dv
            p.body.vy += ny * dv

            if (p.material.rupture == Rupture.INCASSABLE) continue
            p.hp -= e
            if (p.hp <= 0f) someBroke = true
        }
        if (someBroke) breakDead()
        countToppled()
    }

    /**
     * Recompte les pierres qui ne sont plus à leur place.
     *
     * Un balayage complet plutôt qu'un compteur entretenu au fil de l'eau, et c'est
     * volontaire : une pierre peut se renverser **puis revenir** — un mur qui oscille,
     * un bloc qui retombe dans son trou — et un compteur qu'on incrémente ne sait pas
     * défaire. Cinquante pierres à mesurer par image ne coûtent rien à côté d'un seul
     * contact du solveur.
     */
    private fun countToppled() {
        var n = 0
        for (p in live) if (p.toppled) n++
        pieceToppled = n
    }

    // ── La rupture ────────────────────────────────────────────────────────────

    /**
     * Casse tout ce qui n'a plus de vie, et le remplace par la génération suivante.
     *
     * Une pierre descend d'un palier à chaque rupture : entière, puis éclats, puis
     * morceaux, puis grains. Trois choses l'arrêtent, et elles finissent toutes de la
     * même façon — une bouffée de poussière et plus rien :
     *
     *  - le **dernier palier** ([TargetRules.LAST_SOLID_TIER]) : un grain qui casse ne
     *    laisse pas de plus petit grain ;
     *  - la **taille** : [fragmentsOf] refuse de refendre ce qui est déjà trop petit ;
     *  - le **budget** : au-delà de [TargetRules.MAX_DEBRIS] corps, le gravier coûte
     *    plus cher qu'il ne rapporte.
     *
     * Que les trois se terminent en poussière plutôt qu'en disparition muette est ce qui
     * rend la limite invisible : le joueur voit toujours quelque chose se passer, et il
     * ne saura jamais laquelle des trois vient de s'appliquer.
     */
    private fun breakDead() {
        dead.clear()
        for (p in live) if (p.broken) dead.add(p)
        for (p in dead) {
            if (!p.debris) pieceBroken++
            val suivant = p.tier + 1
            val shards =
                if (p.material.rupture == Rupture.ECLATS && suivant <= TargetRules.LAST_SOLID_TIER) {
                    fragmentsOf(p)
                } else {
                    emptyList()
                }
            val x = p.body.x
            val y = p.body.y
            val r = p.body.boundingRadius
            removePiece(p)
            // Le budget se vérifie **après** avoir retiré le bloc rompu : casser une
            // pierre en deux ne coûte qu'un corps de plus, pas deux.
            if (shards.isEmpty() || debrisCount + shards.size > TargetRules.MAX_DEBRIS) {
                onDust?.invoke(x, y, r)
                continue
            }
            spawnShards(p, shards, suivant)
        }
        dead.clear()
    }

    private fun removePiece(p: TargetPiece) {
        world.remove(p.body)
        live.remove(p)
        if (p.debris) debrisCount--
    }

    private fun spawnShards(parent: TargetPiece, shards: List<Block>, tier: Int) {
        // Le petit jaillissement des morceaux se paie sur les **dégâts en trop** :
        // le coup a fait plus que nécessaire, et ce surplus part dans les éclats. Le
        // moteur a pour règle de ne jamais créer d'énergie, et ce n'est pas au jeu de
        // le faire dans son dos.
        val overkill = (-parent.hp).coerceAtLeast(0f)
        val burst = minOf(sqrt(2f * overkill / parent.body.mass), TargetRules.MAX_BURST)
        for (s in shards) {
            val piece = spawn(s, tier)
            val rx = s.x - parent.body.x
            val ry = s.y - parent.body.y
            // Vitesse du point correspondant sur le bloc d'origine, rotation comprise.
            piece.body.vx = parent.body.vx - parent.body.omega * ry
            piece.body.vy = parent.body.vy + parent.body.omega * rx
            piece.body.omega = parent.body.omega
            val d = hypot(rx, ry)
            if (d > 1e-3f && burst > 0f) {
                piece.body.vx += burst * rx / d
                piece.body.vy += burst * ry / d
            }
        }
    }

    /**
     * En quoi une pierre se casse, décrit dans le monde, à la pose qu'elle avait à
     * l'instant de rompre.
     *
     * Un bloc composé se casse **en ses propres parties** : le plan de fracture était
     * déjà écrit le jour où on a dessiné la pierre. Un bloc d'une seule forme se
     * refend en deux ou en quatre, selon ce qu'il a de trop grand.
     */
    private fun fragmentsOf(p: TargetPiece): List<Block> {
        val mat = p.material
        val parts = p.block.parts
        val out = ArrayList<Block>()

        if (parts.size >= 2) {
            for (i in parts.indices) {
                p.body.partWorld(i, pose)
                val q = parts[i]
                out += if (q.shape == Shape.CIRCLE) {
                    Block.circle(mat, pose[0], pose[1], q.radius, Role.PROP)
                } else {
                    Block.box(mat, pose[0], pose[1], q.halfW, q.halfH, pose[2], Role.PROP)
                }
            }
            return out
        }

        val q = parts[0]
        p.body.partWorld(0, pose)
        val px = pose[0]
        val py = pose[1]
        val pa = pose[2]

        if (q.shape == Shape.CIRCLE) {
            // Un galet se casse en trois, pas en quatre : c'est plus joli et ça coûte moins.
            if (q.radius < 2f * TargetRules.MIN_FRAGMENT_HALF) return out
            val r = q.radius * 0.5f
            val start = rng.nextFloat() * 6.2832f
            for (k in 0 until 3) {
                val a = start + k * 2.0944f
                out += Block.circle(mat, px + r * cos(a), py + r * sin(a), r, Role.PROP)
            }
            return out
        }

        val min = TargetRules.MIN_FRAGMENT_HALF
        val nx = if (q.halfW > 2f * min) 2 else 1
        val ny = if (q.halfH > 2f * min) 2 else 1
        if (nx == 1 && ny == 1) return out

        val fw = q.halfW / nx
        val fh = q.halfH / ny
        val c = cos(pa)
        val s = sin(pa)
        for (i in 0 until nx) {
            for (j in 0 until ny) {
                val lx = -q.halfW + fw + 2f * fw * i
                val ly = -q.halfH + fh + 2f * fh * j
                out += Block.box(
                    mat,
                    px + lx * c - ly * s,
                    py + lx * s + ly * c,
                    fw, fh, pa, Role.PROP
                )
            }
        }
        return out
    }

    // ── Le ménage ─────────────────────────────────────────────────────────────

    /**
     * Ramasse les petits débris endormis, et tout ce qui a quitté le terrain.
     *
     * Il fallait autrefois épargner tout morceau dont le sommet dépassait la ligne de
     * ruine, sans quoi le ménage gagnait la partie à la place du joueur en faisant
     * disparaître le caillou qui tenait la silhouette en l'air. L'objectif ne se
     * mesurant plus en hauteur, le ménage n'a plus aucune prise sur l'issue : il ne
     * fait plus que ce pour quoi il est là, ramasser le gravier qui traîne.
     */
    private fun sweepDebris(dt: Float) {
        dead.clear()
        for (p in live) {
            val b = p.body
            // « Sorti du monde » se mesure sous **le sol**, pas sous zéro : un site au
            // fond d'un vallon est huit mètres plus bas que la machine, et il n'est pas
            // pour autant tombé de la carte.
            val plancher = terrain.lowest - TargetRules.FALL_OUT_DEPTH
            if (b.y < plancher || b.x < TrebuchetRules.GROUND_LEFT || b.x > TrebuchetRules.GROUND_RIGHT) {
                dead.add(p)
                continue
            }
            if (!p.debris) continue
            if (b.boundingRadius > TargetRules.DEBRIS_SWEEP_HALF) continue
            p.restTimer = if (b.atRest()) p.restTimer + dt else 0f
            if (p.restTimer > TargetRules.DEBRIS_LIFETIME) dead.add(p)
        }
        for (p in dead) removePiece(p)
        dead.clear()
    }

    // ── L'objectif ────────────────────────────────────────────────────────────

    /**
     * Hauteur de ce qui reste debout dans l'emprise de la construction, **mesurée depuis
     * le sol de chaque pierre**.
     *
     * Ce qui a été expédié hors de l'emprise ne compte plus : c'est un caillou dans un
     * champ, pas un morceau de château. Le compter reviendrait à punir le joueur d'avoir
     * trop bien tiré.
     *
     * La mesure se prend au-dessus du terrain et non au-dessus de l'altitude zéro. Sur
     * un sol plat les deux sont la même chose, et c'est pour ça que la distinction a
     * dormi longtemps ; sur un site étagé, la version absolue disait qu'un hameau rasé
     * posé quinze mètres plus haut mesurait encore quinze mètres de haut. Rien ne s'en
     * servait au moment où le relief est arrivé — cette fonction ne sert plus qu'aux
     * bancs d'essai — mais un piège qui dort est un piège qui se referme le jour où on
     * rebranche la ligne de ruine.
     */
    fun ruinHeight(): Float {
        var best = 0f
        val lo = left - TargetRules.FOOTPRINT_MARGIN
        val hi = right + TargetRules.FOOTPRINT_MARGIN
        for (p in live) {
            val b = p.body
            if (b.x < lo || b.x > hi) continue
            val t = b.topY() - terrain.heightAt(b.x)
            if (t > best) best = t
        }
        return best
    }

    /**
     * Part de pierres à briser pour que **ce site-ci** compte pour rasé.
     *
     * Elle est fixée au chargement et ne bouge plus : un objectif qui changerait à
     * mesure que les pierres de bois disparaissent serait un objectif qui recule quand
     * on avance.
     */
    var winRatio = TargetRules.WIN_RATIO
        private set

    /** Vrai quand le site a assez souffert. Voir [score] et [winRatio]. */
    val cleared: Boolean get() = pieceTotal > 0 && score >= winRatio

    /**
     * Ce qui a été accompli, de 0 (rien n'a bougé) à 1 (c'est rasé), rapporté à
     * **l'objectif** et non à la destruction totale.
     *
     * Le joueur doit voir qu'un tir a servi même quand il ne gagne pas, et il doit voir
     * arriver la fin : une jauge qui plafonnerait à quatre-vingt-cinq pour cent au
     * moment de la victoire serait une jauge qui ment.
     */
    val progress: Float
        get() = (score / winRatio).coerceIn(0f, 1f)

    companion object {

        /**
         * Tasse une construction dans un monde jetable, et rend la même construction
         * posée **telle que le moteur l'aime**.
         *
         * C'est l'étape la plus rentable de tout le système, et elle coûte quelques
         * dizaines de millisecondes. Une construction sort du générateur dessinée à la
         * règle : les pierres se touchent au millimètre près, mais pas là où le
         * solveur les mettrait. Chargée telle quelle, elle passe sa première seconde à
         * se tasser — elle s'affaisse à l'écran, elle s'inflige des petits chocs, et
         * `isAtRest` refuse de dire que le coup est fini avant qu'elle se calme.
         *
         * Tassée d'avance, elle démarre immobile, et la première chose qui la fera
         * bouger sera le boulet.
         *
         * Le tassement sert aussi de **jugement** : ce que la construction a bougé
         * pendant ces deux secondes dit si elle tenait debout. Le générateur s'en sert
         * pour jeter une graine ratée plutôt que d'offrir au joueur un château qui
         * s'écroule avant qu'il ait tiré. C'est ce que rend [drift].
         */
        fun settle(
            structure: Structure,
            terrain: Terrain = Terrain.FLAT,
            seconds: Float = TargetRules.SETTLE_SECONDS,
            dt: Float = 1f / 120f
        ): Structure {
            if (structure.blocks.isEmpty()) return structure

            val w = PhysWorld().apply {
                iterations = 16
                // Le tassement s'arrête de lui-même : les pierres qui ont trouvé leur
                // place s'endorment et ne coûtent plus rien aux secondes suivantes.
                sleepEnabled = true
            }
            // On tasse sur **le vrai relief**, et pas sur un sol plat qu'on relèverait
            // ensuite. C'est la seule façon de savoir ce que fait un tonneau posé au
            // bord d'un plateau : sur un sol plat il ne bouge pas, sur le vrai terrain
            // il dévale le talus — et mieux vaut qu'il le fasse maintenant, dans un
            // monde jetable, qu'au premier chargement sous les yeux du joueur.
            for (b in terrain.bodies(friction = 0.7f)) w.add(b)
            val field = TargetField(w)
            field.load(structure, terrain)
            // On tasse **sans dégâts** : à ce stade personne n'a encore tiré, et les
            // petits chocs du tassement ne sont pas des coups.
            //
            // Et on tasse **jusqu'au repos**, pas pendant une durée décidée d'avance.
            // Une durée fixe est un piège : un mur de huit assises n'avait rattrapé que
            // la moitié de son affaissement au bout de deux secondes, si bien que la
            // construction « cuite » retombait encore une fois chargée dans le jeu — et
            // se faisait mal en retombant. Une construction qui ne s'endort pas du tout
            // est de toute façon une construction ratée, que [drift] dénoncera.
            val maxSteps = (seconds / dt).toInt()
            var guard = 0
            while (guard < maxSteps) {
                w.stepFrame(dt)
                guard++
                if (guard % 30 == 0 && w.isAtRest()) break
            }

            val posed = field.live.map { p ->
                p.block.posed(p.body.x, p.body.y, p.body.angle)
            }
            field.clear()
            return Structure(posed, structure.name)
        }

        /**
         * De combien la pierre la plus mobile a bougé entre deux états d'une même
         * construction, en mètres. Au-delà de [TargetRules.SETTLE_TOLERANCE], ce n'est
         * plus du tassement.
         */
        fun drift(before: Structure, after: Structure): Float {
            var worst = 0f
            val n = minOf(before.blocks.size, after.blocks.size)
            for (i in 0 until n) {
                val a = before.blocks[i]
                val b = after.blocks[i]
                val d = hypot(b.x - a.x, b.y - a.y)
                if (d > worst) worst = d
            }
            return worst
        }
    }
}
