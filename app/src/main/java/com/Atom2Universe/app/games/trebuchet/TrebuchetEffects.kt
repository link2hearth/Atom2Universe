package com.Atom2Universe.app.games.trebuchet

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Ce qu'une particule est venue faire, et donc comment elle vit.
 *
 * Ce n'est pas un habillage : la sorte décide de la physique. Une étincelle tombe et
 * s'éteint, une fumée monte et gonfle, un éclat de bombe est lourd et file droit. Un
 * seul intégrateur pour tout le monde, sept comportements.
 */
enum class Puff(
    /** Ce que la pesanteur lui fait, en fraction de g. Négatif pour ce qui monte. */
    val gravity: Float,
    /** Freinage de l'air, par seconde. Zéro pour ce qui ne ralentit pas. */
    val drag: Float,
    /** De combien la particule grossit ou maigrit sur sa vie, en facteur. */
    val growth: Float,
    /** Vrai si elle laisse une traînée derrière elle. */
    val trailing: Boolean = false
) {
    /**
     * La fusée qui monte, avec sa queue de feu. Elle éclate en mourant.
     *
     * Elle traîne moins que ses étoiles, et c'est physique : un obus de feu d'artifice
     * est dense et compact, là où une étoile est un grain de poudre qui brûle. Le
     * freinage reste bien visible — une fusée ralentit franchement en haut de sa
     * course — mais il ne lui mange plus la moitié de son altitude.
     */
    SHELL(1f, 0.08f, 1f, trailing = true),

    /** L'étoile d'un bouquet : elle brûle, elle tombe, elle s'éteint. */
    STAR(0.55f, 0.8f, 0.7f, trailing = true),

    /** Le saule : lourd, lent, il retombe en pluie. */
    WILLOW(0.9f, 0.35f, 0.8f, trailing = true),

    /** Le crépitement : minuscule, vif, sans traînée. Il fait le bruit qu'on n'entend pas. */
    CRACKLE(0.3f, 2.5f, 0.5f),

    /** L'étincelle d'un choc : rapide, courte, elle meurt avant de tomber. */
    SPARK(0.7f, 1.6f, 0.4f),

    /** La boule de feu d'une explosion : elle gonfle, elle monte, elle pâlit. */
    FIRE(-0.35f, 3.5f, 3.2f),

    /**
     * La fumée : elle monte, elle gonfle, elle traîne longtemps, et le vent l'emporte.
     *
     * Son gonflement est modéré — deux fois et demie, là où il valait cinq — parce
     * qu'elle est maintenant nombreuse et petite : cinquante paquets qui triplent de
     * taille font un panache, dix-huit qui quintuplent font une tache.
     */
    SMOKE(-0.12f, 1.6f, 2.5f)
}

/**
 * Une particule. Rien qu'un point qui vole, mais un point qui vole **pour de vrai**.
 *
 * Les champs sont publics et modifiables, et l'objet ne meurt jamais : il retourne au
 * bassin. Un feu d'artifice, c'est mille particules relues soixante fois par seconde —
 * en allouer ne serait-ce qu'une par image donnerait du travail au ramasse-miettes
 * exactement pendant qu'on regarde l'écran.
 */
class Spark internal constructor() {
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var life = 0f
    var maxLife = 1f
    var size = 1f
    var kind = Puff.SPARK
    /** Indice dans la palette de [TrebuchetEffects], pas une couleur. */
    var tint = 0
    /** Vrai pour ce qui se dessine derrière le décor : les feux du fond. */
    var background = false

    /** Vrai pour une étoile qui se désagrège en petites braises crépitantes. */
    var cracklesOnDeath = false

    /**
     * Pour une fusée : de combien son bouquet s'ouvrira, en facteur.
     *
     * Il voyage avec elle parce que c'est elle qui sait de quelle hauteur elle vient,
     * et que le bouquet n'existe qu'au moment où elle meurt.
     */
    var spread = 1f

    /** Ce qu'il reste à vivre, de 1 (neuve) à 0 (éteinte). */
    val fade: Float get() = if (maxLife <= 0f) 0f else (life / maxLife).coerceIn(0f, 1f)

    val alive: Boolean get() = life > 0f

    /** Taille au moment où on la regarde : elle grossit ou maigrit avec l'âge. */
    val shownSize: Float get() = size * (1f + (kind.growth - 1f) * (1f - fade))

    internal fun reset() {
        life = 0f
    }
}

/**
 * Ce qu'une fusée fait en éclatant. Tiré au sort à chaque tir, et jamais deux fois
 * pareil.
 */
enum class Burst {
    /** La sphère classique : des étoiles partout, à la même vitesse. */
    SPHERE,

    /** L'anneau : les étoiles dans un plan, ce qui se voit de profil comme un trait. */
    RING,

    /** Le saule pleureur : lent, lourd, il retombe en rideau. */
    WILLOW,

    /** Le pissenlit : deux couronnes, une lente dedans, une rapide dehors. */
    DOUBLE,

    /** La comète : tout part du même côté, en gerbe. */
    COMET,

    /** Le pétard : beaucoup de petites, très vives, très courtes. */
    CRACKLE,

    /** Une étoile à cinq branches, lisible même au milieu d'un grand bouquet. */
    STAR,

    /** Un coeur avec sa pointe vers le bas, jamais retourné par le tirage. */
    HEART,

    /** Un visage souriant, droit : yeux en haut, sourire en bas. */
    SMILEY,

    /** Un croissant de lune, ouvert vers la droite. */
    CRESCENT,

    /** Un carré lumineux, aux côtés bien reconnaissables. */
    SQUARE,

    /** Un triangle, pointe vers le haut. */
    TRIANGLE,

    /** Une soucoupe : ovale, hublot et dôme. */
    SAUCER,

    /** Un saule dont chaque retombée finit en petites braises crépitantes. */
    CRACKLING_WILLOW
}

/**
 * Les effets : feux d'artifice, explosions, étincelles.
 *
 * **Tout est simulé, rien n'est dessiné ici.** Ce fichier ne connaît ni la toile, ni les
 * pixels, ni Android : il fait voler des points dans des mètres, et la vue les dessine.
 * C'est ce qui permet de vérifier au banc qu'un feu d'artifice monte, éclate, retombe et
 * finit par s'éteindre — trois choses qu'on ne saurait pas prouver en regardant l'écran.
 *
 * **Et rien n'est une image.** Un bouquet est un tirage : sa couleur, sa hauteur, sa
 * puissance, son nombre d'étoiles, sa forme et sa traînée sortent tous du hasard, dans
 * des bornes choisies. Deux victoires ne donneront jamais le même ciel, ce qui est très
 * exactement l'intérêt d'un feu d'artifice.
 *
 * Le bassin est **fixe** : passé son plafond, les nouvelles particules remplacent les
 * plus vieilles. Une explosion ne peut donc jamais faire tomber l'image, quel que soit
 * ce qu'on lui demande.
 */
class TrebuchetEffects(seed: Long = 1L) {

    private val rng = Random(seed)

    companion object {
        /**
         * Nombre maximal de particules vivantes.
         *
         * Deux mille tiennent largement l'image : elles se dessinent en une poignée
         * d'appels groupés par couleur, et l'essentiel du temps il y en a zéro. Le
         * plafond n'est pas là pour l'affichage courant, il est là pour qu'un joueur qui
         * enchaîne dix bouquets ne fasse pas ramer sa fin de partie.
         */
        const val MAX_SPARKS = 2000

        /** Pesanteur des particules. La même que pour les boulets, forcément. */
        const val GRAVITY = TrebuchetRules.GRAVITY

        /** Palette : les teintes possibles, en ARGB opaque. */
        val PALETTE = intArrayOf(
            0xFFFFF3C4.toInt(), // or pâle
            0xFFFF6B6B.toInt(), // rouge
            0xFFFFA94D.toInt(), // orange
            0xFFFFD166.toInt(), // ambre
            0xFF7BE495.toInt(), // vert
            0xFF4DABF7.toInt(), // bleu
            0xFFB197FC.toInt(), // violet
            0xFFFF8FD6.toInt(), // rose
            0xFF9FE7FF.toInt(), // cyan
            0xFFFFFFFF.toInt(), // blanc
            0xFF8A8F9C.toInt(), // fumée
            0xFFFF4D1A.toInt()  // feu
        )

        /** Les teintes qui servent à autre chose qu'à faire joli. */
        const val TINT_SMOKE = 10
        const val TINT_FIRE = 11

        /** Les teintes vives, celles qu'un bouquet a le droit de tirer. */
        val FESTIVE = intArrayOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)
    }

    private val pool = Array(MAX_SPARKS) { Spark() }
    private var next = 0

    /**
     * La vitesse de l'air. Les particules y tendent au lieu de tendre vers l'arrêt.
     *
     * C'est toute la différence entre une fumée qui monte en colonne et une fumée qui
     * part de côté en s'étirant : le freinage ne ramène pas une particule vers zéro, il
     * la ramène vers **l'air qui l'entoure**. Sans vent, l'air est immobile et le calcul
     * est exactement celui d'avant.
     */
    private var windX = 0f
    private var windY = 0f

    fun setWind(x: Float, y: Float) {
        windX = x
        windY = y
    }

    /**
     * Préviennent qu'une fusée part ou qu'un bouquet éclate, pour qui voudrait en
     * faire un bruit — les effets restent du Kotlin pur, sans dépendance à l'audio
     * Android. Même patron que [TrebuchetGame.onExplosion].
     */
    var onRocketLaunch: ((Float, Float) -> Unit)? = null
    var onBurst: ((Float, Float) -> Unit)? = null

    /** Toutes les particules du bassin, vivantes ou non. À filtrer sur [Spark.alive]. */
    val sparks: Array<Spark> get() = pool

    var aliveCount = 0
        private set

    // ── Les bouquets programmés ───────────────────────────────────────────────

    private val pendingAt = FloatArray(64)
    private val pendingX = FloatArray(64)
    private val pendingH = FloatArray(64)
    private val pendingFountain = BooleanArray(64)
    private var pendingCount = 0
    private var showClock = 0f

    /** Vrai tant qu'il reste quelque chose à voir : des particules ou des fusées à tirer. */
    val busy: Boolean get() = aliveCount > 0 || pendingCount > 0

    fun clear() {
        for (p in pool) p.reset()
        aliveCount = 0
        pendingCount = 0
        showClock = 0f
    }

    /**
     * Prépare un feu d'artifice : une série de fusées tirées au hasard, étalées dans le
     * temps, entre deux abscisses, et **dans la hauteur de ciel qu'on voit**.
     *
     * Ce dernier point n'est pas un détail. Un bouquet éclate à quatre-vingts mètres,
     * ce qui remplit joliment un écran couché ; le même écran debout montre huit cents
     * mètres de ciel, et les mêmes fusées se retrouvent tassées dans le bas de l'image
     * pendant que les trois quarts de l'écran restent noirs. La hauteur visée se
     * mesure donc à ce que le joueur a sous les yeux, et le nombre de fusées suit :
     * plus il y a de ciel, plus il en faut pour le remplir.
     *
     * Les fusées ne partent pas toutes ensemble et ne partent pas non plus en cadence :
     * un intervalle régulier ferait métronome. On tire donc chaque départ dans une
     * fourchette, ce qui donne au bouquet ce désordre qu'on ne remarque que quand il
     * manque.
     */
    fun celebrate(xFrom: Float, xTo: Float, shots: Int = 22) {
        pendingCount = 0
        showClock = 0f
        // La première part une demi-seconde plus tard qu'avant : la caméra recule à la
        // fin d'un tir pour montrer tout l'arc, et une fusée tirée pendant ce
        // mouvement-là viserait un ciel qui n'existe déjà plus.
        var t = 0.6f
        val lo = minOf(xFrom, xTo)
        val span = kotlin.math.abs(xTo - xFrom).coerceAtLeast(1f)
        for (i in 0 until minOf(shots, pendingAt.size)) {
            pendingAt[pendingCount] = t
            pendingX[pendingCount] = lo + rng.nextFloat() * span
            // On range une **part de ciel**, pas une hauteur en mètres : du tiers aux
            // quatre cinquièmes de l'image, étalées pour qu'un bouquet à mi-hauteur et
            // un autre tout en haut valent mieux que dix à la même altitude. On ne va
            // pas jusqu'au bord : les étoiles montent encore après l'éclatement.
            pendingH[pendingCount] = 0.33f + rng.nextFloat() * 0.47f
            // Deux départs sur le spectacle deviennent aussi des fontaines au sol :
            // elles encadrent les fusées sans les remplacer.
            pendingFountain[pendingCount] = i == 1 || i == shots / 2
            pendingCount++
            t += 0.22f + rng.nextFloat() * 0.62f
        }
    }

    /**
     * Hauteur de ciel visible, en mètres. Le jeu la pose à chaque image.
     *
     * **Elle se lit au moment où la fusée part, et jamais avant.** C'était tout le
     * défaut : le feu d'artifice était réglé à l'instant de la victoire, c'est-à-dire
     * pendant que la caméra était encore collée au boulet, puis celle-ci reculait pour
     * montrer tout l'arc — et les fusées, calculées pour cent cinquante mètres de ciel,
     * éclataient dans le bas d'une image qui en montrait six cents. On rangeait une
     * hauteur là où il fallait ranger une **proportion**.
     */
    var skyTop = 150f

    // ── Les émetteurs ─────────────────────────────────────────────────────────

    /**
     * Tire une fusée depuis le sol, qui éclatera d'elle-même en haut de sa course.
     *
     * On lui donne une altitude à viser, et **on ne l'y pose pas** : on en déduit une
     * vitesse de départ et une longueur de mèche, puis la fusée monte ce que la
     * physique lui permet — freinée par l'air, tirée par la pesanteur, penchée par le
     * vent. Elle éclate donc *à peu près* là où on voulait, et deux fusées lancées
     * pareil n'éclatent pas à la même hauteur. C'est exactement ce qu'on veut : une
     * altitude imposée ferait un alignement, un tirage complet ferait une bouillie.
     *
     * La vitesse sort de la balistique du collège — celle qu'il faut pour monter de
     * tant — corrigée d'un dixième parce que l'air en mange une part, et la mèche est
     * réglée sur le temps de montée. Voir [Puff.SHELL] pour le freinage.
     */
    /**
     * L'altitude du sol, que le jeu renseigne quand le terrain n'est pas plat.
     *
     * Une fusée part **du sol**, et depuis qu'il y a des collines ce n'est plus zéro
     * partout. Sans ça, un bouquet tiré devant une butte de quinze mètres jaillirait du
     * flanc de la butte, ce qui se voit tout de suite.
     */
    var groundAt: (Float) -> Float = { 0f }

    fun rocket(x: Float, ground: Float = 0f, apex: Float = 55f + rng.nextFloat() * 35f) {
        val h = apex.coerceIn(15f, 1200f)
        val speed = speedFor(h)
        val lean = (rng.nextFloat() - 0.5f) * 0.22f * speed
        val s = spawn(Puff.SHELL, x, ground, lean, speed, background = true) ?: return
        s.tint = FESTIVE[rng.nextInt(FESTIVE.size)]
        s.size = 0.7f + h / 400f
        s.maxLife = fuseFor(speed, h) * (0.97f + rng.nextFloat() * 0.07f)
        s.life = s.maxLife
        // Un bouquet haut doit être large, sinon il n'est qu'un point de plus dans le
        // ciel. La racine, et pas la proportion : une fusée qui monte quatre fois plus
        // haut s'ouvre deux fois plus, ce qui est ce que fait un vrai obus.
        s.spread = sqrt(h / 70f).coerceIn(0.7f, 3.2f)
        onRocketLaunch?.invoke(x, ground)
    }

    /**
     * Fontaine au sol : une gerbe continue en éventail, qui se résout en braises.
     *
     * Les particules ne partent pas à l'horizontale par hasard : leur cône est centré
     * sur le haut du monde. Une fontaine reste donc une fontaine, quel que soit le
     * vent ou l'orientation de l'écran.
     */
    fun fountain(x: Float, ground: Float = groundAt(x)) {
        val tint = FESTIVE[rng.nextInt(FESTIVE.size)]
        repeat(58) {
            val a = -PI.toFloat() / 2f + (rng.nextFloat() - 0.5f) * 1.05f
            val v = 14f + rng.nextFloat() * 17f
            val s = spawn(Puff.STAR, x, ground, cos(a) * v, -sin(a) * v, background = true)
                ?: return@repeat
            s.tint = if (rng.nextFloat() < 0.22f) 0 else tint
            s.size = 0.22f + rng.nextFloat() * 0.2f
            s.maxLife = 1.15f + rng.nextFloat() * 1.15f
            s.life = s.maxLife
            s.cracklesOnDeath = rng.nextFloat() < 0.38f
        }
    }

    /**
     * La vitesse de départ qu'il faut pour **atteindre** la hauteur voulue.
     *
     * Elle se cherche par dichotomie, et c'est la seule façon d'y arriver. La formule
     * du collège — la vitesse vaut la racine de deux g h — ignore l'air ; la corriger
     * d'un facteur ne marche que sur une plage, parce que la part que l'air prend
     * grandit avec la hauteur. Mesuré : un facteur réglé pour soixante-dix mètres
     * laissait une fusée visant cinq cents mètres s'arrêter à trois cent cinquante,
     * soit la moitié de l'écran d'une tablette debout au lieu des trois quarts.
     *
     * Huit essais suffisent à tomber au mètre près, et chaque essai n'est qu'une montée
     * simulée — quelques centaines de multiplications. Pour une vingtaine de fusées par
     * bouquet, c'est gratuit.
     */
    private fun speedFor(height: Float): Float {
        var lo = sqrt(2f * GRAVITY * height)
        var hi = lo * 4f
        repeat(8) {
            val mid = (lo + hi) * 0.5f
            if (apexOf(mid) < height) lo = mid else hi = mid
        }
        return hi.coerceAtMost(260f)
    }

    /** Le sommet qu'atteint une fusée lancée à cette vitesse-là. */
    private fun apexOf(speed: Float): Float {
        val dt = 0.02f
        val k = exp(-Puff.SHELL.drag * dt)
        var vy = speed
        var y = 0f
        var t = 0f
        while (t < 30f && vy > 0f) {
            vy = (vy - GRAVITY * Puff.SHELL.gravity * dt) * k
            y += vy * dt
            t += dt
        }
        return y
    }

    /**
     * La longueur de mèche qu'il faut pour éclater à la hauteur voulue.
     *
     * Même méthode que [speedFor], et pour la même raison : on simule la montée au lieu
     * de la calculer, avec exactement le même pas et le même freinage que [update]. La
     * fusée sait donc monter là où on l'envoie **et** y couper sa mèche.
     */
    private fun fuseFor(speed: Float, height: Float): Float {
        val dt = 0.02f
        val k = exp(-Puff.SHELL.drag * dt)
        var vy = speed
        var y = 0f
        var t = 0f
        while (t < 20f) {
            vy = (vy - GRAVITY * Puff.SHELL.gravity * dt) * k
            y += vy * dt
            t += dt
            // Arrivée à hauteur, ou sommet atteint sans y parvenir : dans les deux cas
            // c'est là qu'il faut éclater.
            if (y >= height || vy <= 0f) break
        }
        return t
    }

    /**
     * Le bouquet : une fusée qui meurt se transforme en étoiles.
     *
     * Chaque tirage change six choses à la fois — la forme, la couleur, le nombre
     * d'étoiles, leur vitesse, leur durée et leur traînée. C'est ce qui fait qu'on ne
     * reconnaît jamais deux fois le même.
     */
    private fun burst(x: Float, y: Float, tint: Int, spread: Float = 1f) {
        val shape = Burst.entries[rng.nextInt(Burst.entries.size)]
        val second = if (rng.nextFloat() < 0.35f) FESTIVE[rng.nextInt(FESTIVE.size)] else tint
        // Un bouquet sur trois est bicolore étoile par étoile, et pas seulement par
        // couronnes : c'est le seul moyen d'obtenir ces gerbes mêlées qu'on voit dans
        // les vrais feux, et ça coûte un tirage.
        val panache = rng.nextFloat() < 0.33f
        val power = (9f + rng.nextFloat() * 14f) * spread
        val count = when (shape) {
            Burst.CRACKLE -> 70 + rng.nextInt(90)
            Burst.DOUBLE -> 60 + rng.nextInt(60)
            else -> 36 + rng.nextInt(60)
        }
        // Un bouquet large met plus longtemps à se déployer : ses étoiles vivent plus
        // longtemps, sans quoi on ne verrait que le début de leur course.
        val life = (1.1f + rng.nextFloat() * 1.8f) * (0.7f + 0.4f * spread)

        if (shape in setOf(
                Burst.STAR, Burst.HEART, Burst.SMILEY, Burst.CRESCENT,
                Burst.SQUARE, Burst.TRIANGLE, Burst.SAUCER
            )
        ) {
            shapedBurst(shape, x, y, tint, second, spread, life)
            return
        }

        for (i in 0 until count) {
            val kind = when (shape) {
                Burst.WILLOW, Burst.CRACKLING_WILLOW -> Puff.WILLOW
                Burst.CRACKLE -> Puff.CRACKLE
                else -> Puff.STAR
            }
            // La direction, et surtout la **vitesse**, dépendent de la forme.
            var a = rng.nextFloat() * 2f * PI.toFloat()
            var v = power
            when (shape) {
                Burst.SPHERE ->
                    // Une sphère vue de côté n'est pas un disque plein : les étoiles se
                    // répartissent sur une sphère, donc leur vitesse projetée varie.
                    v *= sqrt(rng.nextFloat())
                Burst.RING -> {
                    // L'anneau garde sa vitesse : c'est ce qui en fait un cercle net.
                    v *= 0.95f + rng.nextFloat() * 0.1f
                }
                Burst.WILLOW, Burst.CRACKLING_WILLOW -> {
                    v *= 0.55f + 0.5f * rng.nextFloat()
                    // Un saule pousse vers le haut avant de retomber.
                    a = -PI.toFloat() / 2f + (rng.nextFloat() - 0.5f) * 2.2f
                }
                Burst.DOUBLE -> v *= if (i % 2 == 0) 0.45f else 1f
                Burst.COMET -> {
                    val dir = rng.nextFloat() * 2f * PI.toFloat()
                    a = dir + (rng.nextFloat() - 0.5f) * 0.7f
                    v *= 0.4f + rng.nextFloat() * 0.9f
                }
                Burst.CRACKLE -> v *= 0.3f + rng.nextFloat() * 0.9f
                // Ces cas ont déjà été émis ci-dessus sous forme de dessin.
                Burst.STAR, Burst.HEART, Burst.SMILEY, Burst.CRESCENT, Burst.SQUARE,
                Burst.TRIANGLE, Burst.SAUCER -> Unit
            }
            val s = spawn(kind, x, y, cos(a) * v, -sin(a) * v, background = true) ?: return
            s.tint = when {
                shape == Burst.DOUBLE && i % 2 == 0 -> second
                panache && rng.nextFloat() < 0.5f -> second
                else -> tint
            }
            s.size = (if (kind == Puff.CRACKLE) 0.25f else 0.4f + rng.nextFloat() * 0.35f) * spread
            // Les braises du saule crépitant ne naissent qu'à la fin de la retombée.
            val duration = if (shape == Burst.CRACKLING_WILLOW) 2.1f else 1f
            s.maxLife = life * (0.6f + rng.nextFloat() * 0.7f) * duration
            s.life = s.maxLife
            s.cracklesOnDeath = shape == Burst.CRACKLING_WILLOW
        }
    }

    /** Émet les points d'un dessin dans le repère du monde (y positif vers le haut). */
    private fun shapedBurst(
        shape: Burst, x: Float, y: Float, tint: Int, second: Int, spread: Float, life: Float
    ) {
        val points = ArrayList<Pair<Float, Float>>()
        fun line(ax: Float, ay: Float, bx: Float, by: Float, n: Int) {
            repeat(n) { i ->
                val t = i / (n - 1f)
                points += (ax + (bx - ax) * t) to (ay + (by - ay) * t)
            }
        }
        when (shape) {
            Burst.STAR -> {
                val vertices = FloatArray(20)
                for (i in 0 until 10) {
                    val radius = if (i % 2 == 0) 1f else 0.42f
                    // La pointe est en haut : l'étoile ne peut pas être à l'envers.
                    val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
                    vertices[i * 2] = cos(a) * radius
                    vertices[i * 2 + 1] = -sin(a) * radius
                }
                for (i in 0 until 10) line(
                    vertices[i * 2], vertices[i * 2 + 1],
                    vertices[(i * 2 + 2) % 20], vertices[(i * 2 + 3) % 20], 7
                )
            }
            Burst.HEART -> repeat(76) { i ->
                val a = 2f * PI.toFloat() * i / 75f
                // Formule classique : lobes en haut, pointe en bas dans notre monde.
                points += (sin(a) * sin(a) * sin(a) * 0.82f) to
                    ((13f * cos(a) - 5f * cos(2f * a) - 2f * cos(3f * a) - cos(4f * a)) / 17f)
            }
            Burst.SMILEY -> {
                repeat(56) { i ->
                    val a = 2f * PI.toFloat() * i / 55f
                    points += cos(a) to sin(a)
                }
                // Les yeux restent au-dessus de la bouche : aucun tirage ne les retourne.
                repeat(8) { i ->
                    val a = 2f * PI.toFloat() * i / 7f
                    points += (-0.36f + cos(a) * 0.11f) to (0.30f + sin(a) * 0.14f)
                    points += (0.36f + cos(a) * 0.11f) to (0.30f + sin(a) * 0.14f)
                }
                repeat(23) { i ->
                    val a = PI.toFloat() * i / 22f
                    points += (cos(a) * 0.50f) to (-0.16f - sin(a) * 0.36f)
                }
            }
            Burst.CRESCENT -> {
                // Contour extérieur à gauche, puis contour intérieur : un vrai C,
                // plutôt qu'un disque auquel il manquerait seulement des points.
                repeat(42) { i ->
                    val a = PI.toFloat() / 2f + PI.toFloat() * i / 41f
                    points += (-0.10f + cos(a) * 0.92f) to (sin(a) * 0.92f)
                }
                repeat(42) { i ->
                    val a = 3f * PI.toFloat() / 2f - PI.toFloat() * i / 41f
                    points += (0.30f + cos(a) * 0.58f) to (sin(a) * 0.78f)
                }
            }
            Burst.SQUARE -> {
                line(-0.85f, 0.85f, 0.85f, 0.85f, 18)
                line(0.85f, 0.85f, 0.85f, -0.85f, 18)
                line(0.85f, -0.85f, -0.85f, -0.85f, 18)
                line(-0.85f, -0.85f, -0.85f, 0.85f, 18)
            }
            Burst.TRIANGLE -> {
                // Cette pointe est construite vers le haut dans le repère du monde.
                line(0f, 1f, -0.92f, -0.72f, 25)
                line(-0.92f, -0.72f, 0.92f, -0.72f, 25)
                line(0.92f, -0.72f, 0f, 1f, 25)
            }
            Burst.SAUCER -> {
                repeat(58) { i ->
                    val a = 2f * PI.toFloat() * i / 57f
                    points += (cos(a) * 1.16f) to (sin(a) * 0.46f)
                }
                // Dôme supérieur et ligne lumineuse : l'ovale devient une soucoupe.
                repeat(28) { i ->
                    val a = PI.toFloat() * i / 27f
                    points += (cos(a) * 0.46f) to (sin(a) * 0.62f + 0.10f)
                }
                line(-1.05f, 0f, 1.05f, 0f, 34)
            }
            else -> return
        }
        // Les formes orientées dansent un peu sans jamais faire un demi-tour : ±8°.
        // À l'inverse, les formes géométriques gagnent à pouvoir surgir dans toutes
        // les positions : une soucoupe renversée reste une soucoupe, un coeur non.
        val tilt = when (shape) {
            Burst.HEART, Burst.SMILEY ->
                (rng.nextFloat() - 0.5f) * (16f * PI.toFloat() / 180f)
            Burst.SQUARE, Burst.TRIANGLE, Burst.SAUCER ->
                rng.nextFloat() * 2f * PI.toFloat()
            else -> 0f
        }
        if (tilt != 0f) {
            val c = cos(tilt)
            val s = sin(tilt)
            for (i in points.indices) {
                val (px, py) = points[i]
                points[i] = (px * c - py * s) to (px * s + py * c)
            }
        }
        val reach = (8.5f + spread * 8f)
        val formTime = (life * 0.52f).coerceIn(0.7f, 1.35f)
        for ((i, point) in points.withIndex()) {
            val dx = point.first * reach
            val dy = point.second * reach
            val s = spawn(
                Puff.STAR, x, y, dx / formTime,
                dy / formTime + GRAVITY * Puff.STAR.gravity * formTime * 0.5f,
                background = true
            ) ?: return
            s.tint = if (i % 7 == 0) second else tint
            s.size = 0.26f + (i % 3) * 0.06f
            s.maxLife = life * (0.9f + rng.nextFloat() * 0.22f)
            s.life = s.maxLife
        }
    }

    /**
     * L'explosion d'une bombe : un éclair, une boule de feu, des éclats, de la fumée.
     *
     * Les quatre ne sont pas décoratives l'une par rapport à l'autre — elles ont des
     * durées de vie très différentes, et c'est **ça** qui fait une explosion. L'éclair
     * dure trois images, le feu un demi-tour de seconde, les éclats une seconde, la
     * fumée cinq. Ce qu'on retient d'une explosion, c'est la fumée qui reste après que
     * tout le reste a disparu.
     */
    fun explosion(x: Float, y: Float, radius: Float) {
        val r = radius.coerceAtLeast(1f)

        // La boule de feu : peu de particules, énormes, très courtes.
        repeat(14) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val v = r * (0.3f + rng.nextFloat() * 0.9f)
            val s = spawn(Puff.FIRE, x, y, cos(a) * v, -sin(a) * v * 0.7f) ?: return@repeat
            s.tint = TINT_FIRE
            s.size = r * (0.16f + rng.nextFloat() * 0.14f)
            s.maxLife = 0.25f + rng.nextFloat() * 0.3f
            s.life = s.maxLife
        }
        // Les éclats : nombreux, rapides, ils partent en étoile et rebondissent nulle
        // part — ce sont des étincelles, pas des débris. Les vrais débris, eux, sont
        // des corps du moteur et c'est le souffle qui s'en occupe.
        repeat(60) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val v = r * (0.8f + rng.nextFloat() * 2.2f)
            val s = spawn(Puff.SPARK, x, y, cos(a) * v, -sin(a) * v) ?: return@repeat
            s.tint = if (rng.nextFloat() < 0.5f) 2 else 3
            s.size = 0.18f + rng.nextFloat() * 0.22f
            s.maxLife = 0.4f + rng.nextFloat() * 0.7f
            s.life = s.maxLife
        }
        // La fumée : **beaucoup de petits paquets**, et pas trois gros ronds.
        //
        // C'est la deuxième version, et la première était l'erreur qu'on fait toujours :
        // une explosion large avait de gros nuages, donc dix-huit disques gris de trois
        // mètres qui se superposaient en une bouillie opaque, ronde, immobile. Une fumée
        // ne se voit pas comme ça — elle se voit parce qu'elle est **inégale**. On en
        // met donc trois fois plus, cinq fois plus petits, lâchés dans un rayon autour
        // du point d'impact et non tous du même point, avec des durées très étalées : le
        // panache se déchire tout seul, et le vent l'emporte en l'étirant.
        repeat(52) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val v = r * (0.08f + rng.nextFloat() * 0.45f)
            // Chaque paquet naît un peu à côté des autres : c'est ce décalage, et rien
            // d'autre, qui fait qu'un panache a une forme.
            val ox = cos(a) * r * 0.35f * rng.nextFloat()
            val oy = sin(a) * r * 0.25f * rng.nextFloat()
            val s = spawn(Puff.SMOKE, x + ox, y + oy, cos(a) * v, -sin(a) * v * 0.5f)
                ?: return@repeat
            s.tint = TINT_SMOKE
            s.size = r * (0.03f + rng.nextFloat() * 0.05f)
            s.maxLife = 1.4f + rng.nextFloat() * 3.4f
            s.life = s.maxLife
        }
    }

    /**
     * La bouffée d'une pierre qui s'en va en poussière.
     *
     * Elle ne monte pas comme la fumée d'une explosion : de la pierre broyée, c'est
     * lourd, ça retombe et ça s'étale au sol. D'où une vitesse basse, très peu de
     * hauteur, et une vie courte — on veut un nuage qui se pose, pas un panache.
     *
     * C'est le seul signe que le joueur a du dernier palier de destruction. Sans elle,
     * un tas qu'on s'acharne à broyer ne ferait que **disparaître**, ce qui se lit comme
     * un bug et non comme une victoire.
     */
    fun dust(x: Float, y: Float, radius: Float) {
        val r = radius.coerceIn(0.2f, 3f)
        val n = (5f + r * 4f).toInt().coerceIn(5, 14)
        repeat(n) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val v = 0.6f + rng.nextFloat() * 2.2f * r
            val s = spawn(Puff.SMOKE, x, y, cos(a) * v, -sin(a) * v * 0.35f) ?: return@repeat
            s.tint = TINT_SMOKE
            s.size = r * (0.25f + rng.nextFloat() * 0.35f)
            s.maxLife = 0.5f + rng.nextFloat() * 0.9f
            s.life = s.maxLife
        }
    }

    /** Une poignée d'étincelles au point d'impact : le boulet qui mord la pierre. */
    fun impact(x: Float, y: Float, force: Float) {
        val n = (4 + force * 12f).toInt().coerceIn(4, 26)
        repeat(n) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val v = 3f + rng.nextFloat() * 9f * (0.4f + force)
            val s = spawn(Puff.SPARK, x, y, cos(a) * v, -sin(a) * v) ?: return@repeat
            s.tint = 3
            s.size = 0.1f + rng.nextFloat() * 0.15f
            s.maxLife = 0.2f + rng.nextFloat() * 0.4f
            s.life = s.maxLife
        }
    }

    // ── La vie des particules ─────────────────────────────────────────────────

    /**
     * Fait vivre tout le monde d'une image.
     *
     * L'intégration est celle du moteur — une demi-implicite, vitesse d'abord — et le
     * freinage est **exponentiel**, pas linéaire. C'est la même leçon que pour les
     * corps : un freinage retranché par pas dépend de la durée du pas, et une image qui
     * traîne ferait alors reculer les étincelles.
     */
    fun update(dt: Float) {
        if (dt <= 0f) return
        tickShow(dt)

        var alive = 0
        for (s in pool) {
            if (!s.alive) continue
            s.life -= dt
            if (!s.alive) {
                // Une fusée qui meurt éclate : c'est là, et nulle part ailleurs, que
                // naissent les bouquets.
                if (s.kind == Puff.SHELL) {
                    burst(s.x, s.y, s.tint, s.spread)
                    onBurst?.invoke(s.x, s.y)
                } else if (s.cracklesOnDeath) {
                    crackle(s.x, s.y, s.tint, s.background)
                }
                continue
            }
            // Le freinage ramène la particule vers la vitesse de l'air, pas vers zéro.
            val k = exp(-s.kind.drag * dt)
            s.vx = windX + (s.vx - windX) * k
            s.vy = windY + (s.vy - GRAVITY * s.kind.gravity * dt - windY) * k
            s.x += s.vx * dt
            s.y += s.vy * dt
            alive++
        }
        aliveCount = alive
    }

    /** Tire les fusées programmées quand leur heure arrive. */
    private fun tickShow(dt: Float) {
        if (pendingCount == 0) return
        showClock += dt
        var kept = 0
        for (i in 0 until pendingCount) {
            if (pendingAt[i] <= showClock) {
                // La part de ciel devient une hauteur ici, avec le cadrage du moment.
                rocket(
                    pendingX[i],
                    ground = groundAt(pendingX[i]),
                    apex = skyTop.coerceIn(40f, 700f) * pendingH[i]
                )
                if (pendingFountain[i]) fountain(pendingX[i], groundAt(pendingX[i]))
            } else {
                pendingAt[kept] = pendingAt[i]
                pendingX[kept] = pendingX[i]
                pendingH[kept] = pendingH[i]
                pendingFountain[kept] = pendingFountain[i]
                kept++
            }
        }
        pendingCount = kept
    }

    /**
     * Prend une particule dans le bassin.
     *
     * Quand tout est occupé, on **recycle la plus proche de sa fin** plutôt que de
     * refuser. Refuser ferait des bouquets tronqués — la moitié des étoiles manquantes,
     * toujours du même côté, puisque la boucle d'émission tourne dans l'ordre. Voler sa
     * place à une particule qui allait mourir ne se voit pas.
     */
    private fun spawn(
        kind: Puff,
        x: Float,
        y: Float,
        vx: Float,
        vy: Float,
        background: Boolean = false
    ): Spark? {
        var chosen: Spark? = null
        var worst = Float.MAX_VALUE
        for (i in 0 until MAX_SPARKS) {
            val s = pool[(next + i) % MAX_SPARKS]
            if (!s.alive) {
                chosen = s
                next = (next + i + 1) % MAX_SPARKS
                break
            }
            if (s.life < worst) {
                worst = s.life
                chosen = s
            }
        }
        val s = chosen ?: return null
        s.kind = kind
        s.x = x
        s.y = y
        s.vx = vx
        s.vy = vy
        s.size = 0.3f
        s.tint = 0
        s.spread = 1f
        s.background = background
        s.cracklesOnDeath = false
        s.maxLife = 1f
        s.life = 1f
        return s
    }

    /** Les petites braises secondaires d'une retombée crépitante. */
    private fun crackle(x: Float, y: Float, tint: Int, background: Boolean) {
        repeat(5 + rng.nextInt(6)) {
            val a = rng.nextFloat() * 2f * PI.toFloat()
            val v = 1.8f + rng.nextFloat() * 5.5f
            val s = spawn(Puff.CRACKLE, x, y, cos(a) * v, -sin(a) * v, background) ?: return
            s.tint = if (rng.nextFloat() < 0.45f) 0 else tint
            s.size = 0.12f + rng.nextFloat() * 0.12f
            s.maxLife = 0.28f + rng.nextFloat() * 0.42f
            s.life = s.maxLife
        }
    }
}
