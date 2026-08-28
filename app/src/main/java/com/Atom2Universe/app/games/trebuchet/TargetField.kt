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
    /** Vrai si cette pierre est née d'une rupture, et non de la construction d'origine. */
    val debris: Boolean
) {
    val material: Material get() = block.material
    val role: Role get() = block.role

    val maxHp: Float = block.hp
    var hp: Float = block.hp
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

    /** Masse des blocs d'origine qui ont rompu, qu'ils aient laissé des débris ou non. */
    var brokenMass = 0f
        private set

    private var debrisCount = 0

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
    val ruinLine: Float get() = TargetRules.RUIN_RATIO * baseHeight

    /** Part de la construction d'origine qui a rompu, de 0 à 1. Pour l'affichage. */
    val brokenRatio: Float get() = if (totalMass > 0f) (brokenMass / totalMass).coerceIn(0f, 1f) else 0f

    // ── Pose et dépose ────────────────────────────────────────────────────────

    fun load(structure: Structure) {
        clear()
        baseHeight = structure.baseHeight
        left = structure.left
        right = structure.right
        totalMass = structure.totalMass
        armed = false
        armingTimer = 0f
        calmTimer = 0f
        dormant = false
        for (b in structure.blocks) spawn(b, debris = false)
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
        brokenMass = 0f
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

    private fun spawn(b: Block, debris: Boolean): TargetPiece {
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

        val piece = TargetPiece(b, body, debris)
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
            return
        }

        updateDormancy()
        if (dormant) return

        var someBroke = false
        for (p in live) {
            if (p.debris) continue
            if (p.material.rupture == Rupture.INCASSABLE) continue
            val impact = p.body.impactAccum
            if (impact <= p.maxHp * TargetRules.DAMAGE_FLOOR) continue
            p.hp -= impact
            if (p.hp <= 0f) someBroke = true
        }
        world.clearImpacts()

        if (someBroke) breakDead()
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
    fun blast(x: Float, y: Float, energy: Float, radius: Float) {
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

            // La poussée découle de l'énergie reçue, comme il se doit : la vitesse
            // qu'elle donne est celle dont l'énergie cinétique vaut ce qu'on a versé.
            // Elle est bornée, sinon un éclat léger partirait en orbite.
            val nx = if (d > 1e-3f) dx / d else 0f
            val ny = if (d > 1e-3f) dy / d else 1f
            val dv = minOf(sqrt(2f * e * p.body.invMass), TargetRules.MAX_BLAST_SPEED)
            // Une pierre endormie ne serait pas intégrée : la poussée lui serait
            // versée puis oubliée. Toute vitesse posée à la main réveille son corps.
            p.body.wake()
            p.body.vx += nx * dv
            p.body.vy += ny * dv

            if (p.debris || p.material.rupture == Rupture.INCASSABLE) continue
            p.hp -= e
            if (p.hp <= 0f) someBroke = true
        }
        if (someBroke) breakDead()
    }

    // ── La rupture ────────────────────────────────────────────────────────────

    private fun breakDead() {
        dead.clear()
        for (p in live) if (p.broken) dead.add(p)
        for (p in dead) {
            if (!p.debris) brokenMass += p.block.mass
            val shards = if (p.material.rupture == Rupture.ECLATS) fragmentsOf(p) else emptyList()
            removePiece(p)
            // Le budget se vérifie **après** avoir retiré le bloc rompu : casser une
            // pierre en deux ne coûte qu'un corps de plus, pas deux.
            if (shards.isEmpty() || debrisCount + shards.size > TargetRules.MAX_DEBRIS) continue
            spawnShards(p, shards)
        }
        dead.clear()
    }

    private fun removePiece(p: TargetPiece) {
        world.remove(p.body)
        live.remove(p)
        if (p.debris) debrisCount--
    }

    private fun spawnShards(parent: TargetPiece, shards: List<Block>) {
        // Le petit jaillissement des morceaux se paie sur les **dégâts en trop** :
        // le coup a fait plus que nécessaire, et ce surplus part dans les éclats. Le
        // moteur a pour règle de ne jamais créer d'énergie, et ce n'est pas au jeu de
        // le faire dans son dos.
        val overkill = (-parent.hp).coerceAtLeast(0f)
        val burst = minOf(sqrt(2f * overkill / parent.body.mass), TargetRules.MAX_BURST)
        for (s in shards) {
            val piece = spawn(s, debris = true)
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
            // Un débris ne se casse plus : on lui donne des points de vie hors de
            // portée plutôt qu'un cas particulier de plus dans la boucle de dégâts.
            piece.hp = Float.MAX_VALUE
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
     * Le garde-fou est important : on ne ramasse **jamais** un morceau dont le sommet
     * dépasse la ligne de ruine. Sinon le ménage gagnerait la partie à la place du
     * joueur, en faisant disparaître le caillou qui tenait la silhouette en l'air.
     */
    private fun sweepDebris(dt: Float) {
        dead.clear()
        for (p in live) {
            val b = p.body
            if (b.y < -5f || b.x < TrebuchetRules.GROUND_LEFT || b.x > TrebuchetRules.GROUND_RIGHT) {
                dead.add(p)
                continue
            }
            if (!p.debris) continue
            if (b.boundingRadius > TargetRules.DEBRIS_SWEEP_HALF) continue
            if (b.topY() > ruinLine) continue
            p.restTimer = if (b.atRest()) p.restTimer + dt else 0f
            if (p.restTimer > TargetRules.DEBRIS_LIFETIME) dead.add(p)
        }
        for (p in dead) removePiece(p)
        dead.clear()
    }

    // ── L'objectif ────────────────────────────────────────────────────────────

    /**
     * Hauteur de ce qui reste debout, dans l'emprise de la construction.
     *
     * Ce qui a été expédié hors de l'emprise ne compte plus : c'est un caillou dans un
     * champ, pas un morceau de château. Le compter reviendrait à punir le joueur d'avoir
     * trop bien tiré.
     */
    fun ruinHeight(): Float {
        var best = 0f
        val lo = left - TargetRules.FOOTPRINT_MARGIN
        val hi = right + TargetRules.FOOTPRINT_MARGIN
        for (p in live) {
            val b = p.body
            if (b.x < lo || b.x > hi) continue
            val t = b.topY()
            if (t > best) best = t
        }
        return best
    }

    /** Vrai quand la silhouette est passée sous la ligne. */
    val cleared: Boolean get() = baseHeight > 0f && ruinHeight() <= ruinLine

    /**
     * Ce qui a été accompli, de 0 (rien n'a bougé) à 1 (c'est rasé). Sert à la barre
     * de progression : le joueur doit voir qu'un tir a servi même quand il ne gagne pas.
     */
    val progress: Float
        get() {
            if (baseHeight <= 0f) return 0f
            val span = baseHeight - ruinLine
            if (span <= 0f) return 1f
            return ((baseHeight - ruinHeight()) / span).coerceIn(0f, 1f)
        }

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
            val half = structure.width / 2f + 20f
            w.add(
                PhysBody(half, 1f, 0f).apply {
                    x = (structure.left + structure.right) / 2f
                    y = -1f
                    lockPosition = true
                    lockRotation = true
                    friction = 0.7f
                    category = TrebuchetCategory.GROUND
                    refreshMass()
                }
            )
            val field = TargetField(w)
            field.load(structure)
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
