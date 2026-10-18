package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.crypto.AstronomyCalculator
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * L'heure qu'il est dans le jeu.
 *
 * Elle part de **l'heure réelle** et court soixante-douze fois plus vite : un jour entier
 * en vingt minutes, dix de jour et dix de nuit. Deux conséquences, et les deux sont
 * voulues. D'abord, la première partie d'un joueur commence sous le ciel qu'il a
 * réellement au-dessus de la tête — même lune, même phase, et s'il joue le soir, il joue
 * de nuit. Ensuite, une partie un peu longue voit le soleil se lever et se coucher
 * plusieurs fois, ce qui est le seul moyen de rentabiliser un crépuscule.
 *
 * L'instant est un **millis epoch**, pas un compteur : c'est ce que réclame
 * [AstronomyCalculator], et ça donne gratuitement une date, donc une position réelle de
 * la Lune sur son orbite.
 */
class SkyClock(startMillis: Long = System.currentTimeMillis()) {

    /** L'instant du jeu, en millisecondes depuis l'époque Unix. */
    var instant: Long = startMillis
        private set

    /**
     * Fait couler le temps. [dt] est en secondes **réelles**, celles de l'écran.
     *
     * Elles sont multipliées par [SPEED] en chemin : une seconde passée à regarder vaut
     * soixante-douze secondes de monde, ce qui fait tenir un jour dans vingt minutes.
     */
    fun advance(dt: Float) {
        instant += (dt * SPEED * 1000f).toLong()
    }

    /**
     * Avance de [seconds] secondes **du monde**, telles quelles.
     *
     * C'est l'autre unité, et confondre les deux est exactement le bug que cette
     * méthode répare. L'atelier d'engrenages fait tourner sa machine pendant une durée
     * choisie — deux minutes, dix minutes — et ces secondes-là sont déjà des secondes de
     * monde : la machine tourne vraiment deux minutes. Les passer à [advance] les
     * multipliait une seconde fois par soixante-douze, et une charge de deux minutes
     * faisait courir le Soleil sur **deux heures et demie**. Au maximum du réglage, une
     * seule pression déroulait un jour et une nuit entiers.
     *
     * Deux méthodes plutôt qu'un facteur passé en paramètre : l'unité se lit alors sur
     * le nom de l'appel, là où elle se choisit.
     */
    fun skip(seconds: Float) {
        instant += (seconds * 1000f).toLong()
    }

    /** Avance ou recule d'un nombre d'heures : c'est ce que fait le doigt qui balaie. */
    fun scrub(hours: Float) {
        instant += (hours * 3_600_000f).toLong()
    }

    companion object {
        /** Durée d'un jour de jeu, en secondes réelles : dix minutes de jour, dix de nuit. */
        const val DAY_SECONDS = 1200f

        /** De combien le temps du jeu court plus vite que le vrai. */
        const val SPEED = 86_400f / DAY_SECONDS

        /** Vitesse maximale du balayage à la main, en heures par seconde. */
        const val MAX_SCRUB_RATE = 1f

        /**
         * À quelle vitesse le temps file quand le doigt s'est écarté de [deltaX] pixels
         * de son point d'appui, [throwPx] étant la course qui donne la pleine vitesse.
         *
         * **La vitesse suit l'écart et non la position absolue du doigt.** Un appui près
         * du bord droit partirait sinon à pleine vitesse avant d'avoir bougé, ce qui
         * ferait sauter le ciel de plusieurs heures à chaque fois qu'on pose le doigt un
         * peu à droite.
         *
         * Et au point d'appui la vitesse est **nulle**, pas normale : attraper l'heure
         * l'arrête. C'est ce qui permet de retenir un crépuscule aussi longtemps qu'on
         * veut le regarder, et c'est la moitié de l'intérêt du geste.
         *
         * La règle vit ici et pas dans la vue parce que c'en est une : elle décide de ce
         * que le joueur peut faire, elle se mesure, et elle n'a pas besoin d'un écran
         * pour être juste.
         */
        fun scrubRate(deltaX: Float, throwPx: Float): Float {
            if (throwPx <= 1f) return 0f
            return (deltaX / throwPx).coerceIn(-1f, 1f) * MAX_SCRUB_RATE
        }
    }
}

/**
 * Les nuages : des paquets de boules molles qui traversent le ciel au fil du vent.
 *
 * **Ils ont remplacé des stries de vitesse, et pas seulement pour la beauté.** Les
 * stries étaient inclinées selon l'angle du vent mais dérivaient toujours vers la
 * droite : leur vitesse se calculait à partir de `Wind.speed`, qui est une **norme** et
 * donc toujours positive. Par vent debout, elles pointaient à gauche et filaient à
 * droite. Un nuage dérive ici avec `Wind.vx`, dont le signe est celui du vent — la
 * question ne peut plus se poser.
 *
 * Chaque nuage est une poignée de boules de rayons et de décalages tirés une fois pour
 * toutes : c'est ce qui donne le contour cotonneux, sans texture ni image à charger.
 */
class CloudField(seed: Long = 7L, count: Int = 9) {

    /**
     * Un nuage : où il est **dans le monde**, à quelle altitude, et de quelle taille.
     *
     * Tout est en mètres, et c'est le point important. La première version rangeait les
     * nuages en fractions d'écran : ils restaient donc collés à la vitre, immobiles
     * quand on zoomait, comme un décor peint sur la dalle. Un nuage a une altitude et
     * une taille, comme le château ; c'est à la vue de les projeter.
     *
     * [x] tourne dans [0, [SPAN][ et se reboucle : un nuage qui sort d'un côté rentre de
     * l'autre, et le ciel n'a jamais de trou.
     */
    class Cloud internal constructor(
        /** Altitude, en mètres au-dessus du sol. */
        val altitude: Float,
        /** Demi-largeur du nuage, en mètres. */
        val size: Float,
        /**
         * Léger écart de vitesse d'un nuage à l'autre, autour de 1.
         *
         * L'air n'est pas un bloc : deux nuages voisins ne vont jamais exactement à la
         * même vitesse, et sans cet écart ils dérivent en formation, ce qui se remarque
         * au bout de trente secondes.
         */
        val depth: Float,
        /** Les boules, par triplets : décalage x, décalage y, rayon — en parts de [size]. */
        val puffs: FloatArray
    ) {
        var x = 0f
            internal set
    }

    val clouds: List<Cloud> = run {
        val rng = Random(seed)
        List(count) { index ->
            val n = 4 + rng.nextInt(3)
            val puffs = FloatArray(n * 3)
            for (i in 0 until n) {
                // Les boules s'alignent en longueur et se chevauchent : un nuage est plus
                // large que haut, et deux boules qui ne se touchent pas font deux nuages.
                puffs[i * 3] = (i - (n - 1) / 2f) * 0.62f + (rng.nextFloat() - 0.5f) * 0.2f
                puffs[i * 3 + 1] = (rng.nextFloat() - 0.5f) * 0.28f
                puffs[i * 3 + 2] = 0.42f + rng.nextFloat() * 0.34f
            }
            Cloud(
                altitude = MIN_ALTITUDE + rng.nextFloat() * (MAX_ALTITUDE - MIN_ALTITUDE),
                size = MIN_SIZE + rng.nextFloat() * (MAX_SIZE - MIN_SIZE),
                depth = 0.85f + rng.nextFloat() * 0.3f,
                puffs = puffs
            ).apply {
                // Étalés d'emblée sur toute la bande : sans ça, ils partiraient tous du
                // même bord et le ciel mettrait une minute à se remplir.
                x = (index + 0.5f) / count * SPAN
            }
        }
    }

    /**
     * Fait dériver les nuages. [windX] est la composante horizontale du vent, en m/s,
     * **signe compris** : un vent debout les fait donc revenir vers la machine.
     *
     * Ils vont **à la vitesse du vent**, tout simplement, puisqu'ils sont dedans. Il n'y
     * a plus de facteur de dérive à régler : c'était un nombre inventé du temps où les
     * nuages vivaient en fractions d'écran et où « vite » ne voulait rien dire.
     */
    fun update(dt: Float, windX: Float) {
        for (c in clouds) {
            var x = c.x + windX * c.depth * dt
            x -= kotlin.math.floor(x / SPAN) * SPAN
            c.x = x
        }
    }

    companion object {
        /**
         * Largeur de la bande de ciel où les nuages tournent, en mètres.
         *
         * Six cents mètres, soit un peu plus que la portée du jeu : le joueur ne peut
         * donc pas voir deux fois le même nuage à l'écran, ce qui serait le seul défaut
         * visible d'un ciel qui se reboucle.
         */
        const val SPAN = 600f

        /**
         * Altitude des nuages, en mètres. De vrais chiffres de vrais nuages : un cumulus
         * bas traîne vers cent cinquante mètres, un beau cumulus de beau temps monte à
         * huit cents.
         *
         * **Ce sont des objets du monde, comme le château.** Ils se projettent avec les
         * mêmes abscisses et ordonnées que tout le reste — donc quand la vue est serrée
         * sur une machine haute de quinze mètres, ils sont très largement hors de
         * l'écran, et c'est le dézoom qui les fait apparaître.
         *
         * Deux versions ont essayé d'éviter ça, avec un calque à l'échelle bornée pour
         * « garantir » qu'on les voie toujours. C'était une erreur de conception : ça
         * fabriquait exactement ce qu'on cherchait à fuir, des nuages recalés sur la
         * dalle qui ne bougeaient plus au zoom. Un nuage est en l'air à huit cents
         * mètres ; si on ne le voit pas en réglant sa machine, c'est normal.
         */
        const val MIN_ALTITUDE = 150f
        const val MAX_ALTITUDE = 800f

        /**
         * Demi-largeur d'un nuage, en mètres.
         *
         * À l'échelle de leur altitude : on les regarde d'un cadrage qui montre le
         * kilomètre, pas d'un qui montre trente mètres. Des nuages de dix mètres y
         * seraient des mouches.
         */
        const val MIN_SIZE = 40f
        const val MAX_SIZE = 120f
    }
}

/**
 * Les oiseaux : quelques silhouettes qui traversent le ciel bas, sous les nuages.
 *
 * **Ils ne dérivent pas dans le vent, ils volent dedans.** Un nuage est un paquet
 * de vapeur porté par l'air ; un oiseau garde sa propre allure et lutte contre un
 * vent contraire au lieu de reculer avec lui. C'est pour ça que [Bird.cruiseSpeed]
 * n'emprunte le vent qu'en petite part, et garde son signe propre — la moitié des
 * oiseaux vont vers la droite, l'autre vers la gauche, alors que tous les nuages
 * d'un même ciel filent ensemble.
 *
 * Ils se couchent avec le jour : c'est [SkyState.light] qui décide de leur
 * visibilité, à la vue de le lire, pas à eux de s'arrêter de voler pour de vrai —
 * un oiseau qui continuerait son vol sous une opacité nulle reprendrait sa place
 * exacte au matin, sans code de plus.
 */
class BirdField(seed: Long = 13L, count: Int = 7) {

    /** Un oiseau : son altitude, son envergure, et l'allure qui lui est propre. */
    class Bird internal constructor(
        /** Altitude, en mètres au-dessus du sol — basse, sous les nuages. */
        val altitude: Float,
        /** Demi-envergure, en mètres. */
        val span: Float,
        /** Vitesse de croisière, en m/s, signée : le sens de vol ne dépend pas du vent. */
        val cruiseSpeed: Float,
        /** Battements par seconde, propre à chaque oiseau — sans ça ils battent en cadence. */
        val flapRate: Float,
        val flapPhase: Float,
        val bobPhase: Float
    ) {
        var x = 0f
            internal set
    }

    val birds: List<Bird> = run {
        val rng = Random(seed)
        List(count) { index ->
            val versLaDroite = rng.nextBoolean()
            Bird(
                altitude = MIN_ALTITUDE + rng.nextFloat() * (MAX_ALTITUDE - MIN_ALTITUDE),
                span = MIN_SPAN + rng.nextFloat() * (MAX_SPAN - MIN_SPAN),
                cruiseSpeed = (CRUISE_MIN + rng.nextFloat() * (CRUISE_MAX - CRUISE_MIN)) *
                    (if (versLaDroite) 1f else -1f),
                flapRate = 2.2f + rng.nextFloat() * 1.6f,
                flapPhase = rng.nextFloat() * 6.2832f,
                bobPhase = rng.nextFloat() * 6.2832f
            ).apply { x = (index + 0.5f) / count * SPAN }
        }
    }

    /**
     * Fait avancer les oiseaux. [windX] ne compte que pour un quart : voir la note de
     * classe, un vol n'est pas une dérive.
     */
    fun update(dt: Float, windX: Float) {
        for (b in birds) {
            var x = b.x + (b.cruiseSpeed + windX * 0.25f) * dt
            x -= kotlin.math.floor(x / SPAN) * SPAN
            b.x = x
        }
    }

    companion object {
        /** Largeur de la bande où les oiseaux tournent — bien plus courte que celle des
         * nuages : ils volent bas et près, pas de quoi couvrir tout le champ de tir. */
        const val SPAN = 260f

        const val MIN_ALTITUDE = 10f
        const val MAX_ALTITUDE = 90f

        const val MIN_SPAN = 0.35f
        const val MAX_SPAN = 0.6f

        const val CRUISE_MIN = 6f
        const val CRUISE_MAX = 12f
    }
}

/**
 * Le ciel à un instant donné : où sont les astres, de quelle couleur est l'air, et
 * combien il fait clair.
 *
 * **Rien ici ne connaît Android.** C'est une description — des fractions, des angles et
 * des couleurs en entiers ARGB — que la vue se contente de peindre. C'est ce qui permet
 * de vérifier au banc qu'une pleine lune éclaire, qu'un crépuscule passe par l'orange
 * avant le violet, et qu'une éclipse assombrit, sans avoir jamais ouvert l'écran.
 */
class SkyState {

    /** Position du Soleil : 0 à l'est, 1 à l'ouest ; hauteur de -1 (nadir) à 1 (zénith). */
    var sunX = 0f; private set
    var sunAltitude = 0f; private set

    /** Idem pour la Lune, qui suit son propre horaire. */
    var moonX = 0f; private set
    var moonAltitude = 0f; private set

    /**
     * Part éclairée du disque lunaire, de 0 (nouvelle) à 1 (pleine).
     *
     * Elle sort des vraies éphémérides, pas d'un compteur : c'est la phase que la Lune
     * a **ce jour-là**, celle qu'on verrait en levant les yeux.
     */
    var moonPhase = 0f; private set

    /** Vrai quand la Lune croît — le croissant est éclairé à droite dans l'hémisphère nord. */
    var moonWaxing = true; private set

    /** Clarté ambiante, de 0 (nuit noire) à 1 (plein jour). */
    var light = 1f; private set

    /** Part du disque solaire masquée par la Lune, de 0 à 1. */
    var eclipse = 0f; private set

    /** Opacité des étoiles, de 0 (invisibles) à 1. */
    var starAlpha = 0f; private set

    /** Les deux couleurs du dégradé, du haut du ciel à l'horizon. */
    var zenith = 0; private set
    var horizon = 0; private set

    /**
     * Recalcule tout pour l'instant donné.
     *
     * L'objet est **réutilisé** d'une image à l'autre : soixante allocations par seconde
     * pour un ciel qui bouge de trois millièmes de degré seraient un gaspillage
     * caractérisé, et c'est le genre de détail que la vue paie en saccades.
     */
    fun update(utcMillis: Long) {
        val astro = AstronomyCalculator.compute(utcMillis)

        // L'heure solaire du jour, de 0 (minuit) à 1.
        val jour = ((utcMillis % DAY_MS) + DAY_MS) % DAY_MS / DAY_MS.toFloat()

        // Le Soleil : il passe au plus haut à midi, à l'horizon à six heures et à
        // dix-huit. Une sinusoïde suffit — on ne joue pas à une latitude précise, et
        // prétendre le contraire demanderait de choisir un endroit sur Terre.
        sunAltitude = sin(TWO_PI * (jour - 0.25f))
        sunX = arcX(jour)

        // La Lune retarde sur le Soleil de son **élongation** : c'est ce qui fait qu'une
        // pleine lune se lève au coucher du soleil, et qu'une nouvelle lune se couche
        // avec lui. Le retard sort des éphémérides, donc la Lune est au bon endroit du
        // ciel pour la phase qu'elle montre — c'est ce détail-là qu'on remarque quand il
        // est faux, sans savoir le nommer.
        val elongation = astro.moonPhaseRad.toFloat()
        val heureLune = jour - elongation / TWO_PI
        moonAltitude = sin(TWO_PI * (heureLune - 0.25f))
        moonX = arcX(heureLune)
        moonPhase = ((1f - cos(elongation)) / 2f).coerceIn(0f, 1f)
        moonWaxing = sin(elongation) > 0f

        eclipse = eclipseAmount(astro, elongation)

        // La clarté, et le seuil de jouabilité. Le crépuscule s'étale sous l'horizon :
        // il ne fait pas nuit noire à l'instant où le Soleil passe dessous.
        val jourClair = ((sunAltitude + 0.18f) / 0.30f).coerceIn(0f, 1f)
        // Une pleine lune haute éclaire assez pour qu'on distingue un château. C'est un
        // choix de jeu autant que de physique — une nuit noire injouable serait une
        // punition pour avoir joué trop longtemps.
        val clairDeLune = (moonPhase * moonAltitude.coerceAtLeast(0f)) * 0.35f
        light = (maxOf(jourClair, clairDeLune) * (1f - 0.75f * eclipse)).coerceIn(0f, 1f)
        starAlpha = (1f - jourClair * 1.6f).coerceIn(0f, 1f)

        paint()
    }

    /**
     * Où l'astre se trouve en largeur, de 0 à 1.
     *
     * Le lever est à l'est, le coucher à l'ouest, et la course se poursuit **sous
     * l'horizon** au lieu de sauter : un astre qui se téléporterait d'un bord à l'autre
     * à minuit se ferait remarquer le jour où on balaie le temps à la main.
     */
    private fun arcX(fraction: Float): Float {
        val f = ((fraction % 1f) + 1f) % 1f
        // Le temps écoulé depuis le lever, de 0 à 1 sur la journée entière.
        val t = (f - 0.25f + 1f) % 1f
        // De 6 h à 18 h on traverse le ciel de gauche à droite ; de 18 h à 6 h on
        // revient par le même chemin, sous l'horizon, où personne ne nous voit.
        return if (t <= 0.5f) t * 2f else 1f - (t - 0.5f) * 2f
    }

    /**
     * De combien la Lune mange le Soleil.
     *
     * Une éclipse demande deux choses en même temps : une **nouvelle lune** — la Lune est
     * du même côté que le Soleil — et une Lune **près d'un nœud**, c'est-à-dire dans le
     * plan de l'écliptique. Le reste du temps elle passe au-dessus ou en dessous, et
     * c'est pour ça qu'il n'y a pas d'éclipse tous les mois.
     *
     * **Une tolérance est élargie, et il faut le dire.** Une éclipse réelle demande une
     * latitude lunaire sous le demi-degré, et n'est visible que d'une bande étroite du
     * globe : au rythme du jeu, personne n'en verrait jamais. On retient donc le degré et
     * demi, et on ne se demande pas d'où on regarde — ce qui donne quelques éclipses par
     * année de jeu au lieu de deux invisibles. C'est le seul nombre tordu de ce fichier,
     * tout le reste sort des éphémérides.
     */
    private fun eclipseAmount(astro: AstronomyCalculator.AstroSnapshot, elongation: Float): Float {
        // Nouvelle lune : l'élongation est proche de zéro (ou de 2π).
        val depuisNouvelle = minOf(elongation, TWO_PI - elongation)
        if (depuisNouvelle > NEW_MOON_WINDOW) return 0f

        val r = astro.moonPos.length()
        if (r <= 0.0) return 0f
        val latitude = abs(asin(astro.moonPos.z / r)).toFloat()
        if (latitude > NODE_WINDOW) return 0f

        // Deux recouvrements qui se multiplient : l'un dans le temps, l'autre dans le
        // ciel. Le produit donne une éclipse qui grandit puis décroît au lieu de
        // s'allumer d'un coup.
        val proximite = 1f - depuisNouvelle / NEW_MOON_WINDOW
        val alignement = 1f - latitude / NODE_WINDOW
        return (proximite * alignement).coerceIn(0f, 1f)
    }

    /** Les couleurs du ciel, interpolées entre les paliers de [PALETTE]. */
    private fun paint() {
        val a = sunAltitude
        var i = 0
        while (i < PALETTE.size - 1 && a > PALETTE[i + 1].altitude) i++
        val bas = PALETTE[i]
        val haut = PALETTE[minOf(i + 1, PALETTE.size - 1)]
        val span = haut.altitude - bas.altitude
        val t = if (span <= 1e-4f) 0f else ((a - bas.altitude) / span).coerceIn(0f, 1f)
        zenith = mix(bas.zenith, haut.zenith, t)
        horizon = mix(bas.horizon, haut.horizon, t)
        if (eclipse > 0f) {
            // Une éclipse ne fait pas la nuit : elle fait un jour **sale**, une lumière
            // de fin du monde. On tire donc vers la couleur de nuit sans jamais
            // l'atteindre, ce qui est bien plus inquiétant qu'un simple noir.
            zenith = mix(zenith, PALETTE.first().zenith, eclipse * 0.8f)
            horizon = mix(horizon, PALETTE.first().horizon, eclipse * 0.8f)
        }
    }

    private class Step(val altitude: Float, val zenith: Int, val horizon: Int)

    companion object {

        private const val DAY_MS = 86_400_000L
        private const val TWO_PI = (2.0 * PI).toFloat()

        /**
         * Le demi-axe du terminateur lunaire, en fraction du rayon, pour une part
         * éclairée [lit].
         *
         * Le bord de la lumière sur la Lune n'est pas un cercle : c'est le **grand
         * cercle** qui sépare le jour de la nuit, vu de biais, donc une ellipse. Son
         * demi-axe horizontal vaut `2k − 1`, et le signe fait tout : positif, l'ellipse
         * bombe du côté de l'ombre et la Lune est gibbeuse ; négatif, elle bombe du côté
         * de la lumière et creuse un croissant ; nul, c'est le quartier et le
         * terminateur est droit.
         *
         * La règle vit ici et pas dans la vue parce qu'elle se mesure : un demi-disque
         * fermé par cette demi-ellipse a pour aire exactement `k` fois le disque, ce qui
         * est la seule façon de vérifier qu'on affiche la phase et non son complément.
         * Le dessin précédent superposait deux disques et affichait `1 − k` — une pleine
         * lune noire et une nouvelle lune pleine, sans que le code ait l'air faux.
         */
        fun terminatorAxis(lit: Float): Float = 2f * lit - 1f

        /** Écart à la nouvelle lune en deçà duquel une éclipse est possible, en radians. */
        private const val NEW_MOON_WINDOW = 0.10f

        /** Latitude lunaire en deçà de laquelle l'alignement suffit, en radians (≈ 1,5°). */
        private const val NODE_WINDOW = 0.026f

        /**
         * Les couleurs du ciel, du plus bas soleil au plus haut.
         *
         * Cinq paliers et pas deux, parce que c'est **entre** le jour et la nuit que
         * tout se passe : la nuit est bleue et sourde, le plein jour est bleu et clair,
         * et si on interpole directement de l'un à l'autre on obtient un fondu gris qui
         * n'a jamais fait pleurer personne. Les deux paliers du milieu sont là pour ça —
         * le violet qui monte quand le Soleil est encore sous l'horizon, puis l'orange
         * franc qui prend l'horizon au moment où il le franchit.
         */
        private val PALETTE = arrayOf(
            Step(-1f, 0xFF050A18.toInt(), 0xFF0B1026.toInt()),      // nuit profonde
            Step(-0.09f, 0xFF241A4A.toInt(), 0xFF6B2B5E.toInt()),   // crépuscule violet
            Step(0.02f, 0xFF2E3A72.toInt(), 0xFFE4703A.toInt()),    // orange franc
            Step(0.16f, 0xFF3E6FB0.toInt(), 0xFFF0B070.toInt()),    // heure dorée
            Step(0.45f, 0xFF2C6FB5.toInt(), 0xFFA8D0E8.toInt())     // plein jour
        )

        /** Mélange deux couleurs ARGB, composante par composante. */
        fun mix(a: Int, b: Int, t: Float): Int {
            val k = t.coerceIn(0f, 1f)
            fun c(shift: Int): Int {
                val ca = (a shr shift) and 0xFF
                val cb = (b shr shift) and 0xFF
                return (ca + (cb - ca) * k).toInt().coerceIn(0, 255)
            }
            return (0xFF shl 24) or (c(16) shl 16) or (c(8) shl 8) or c(0)
        }
    }
}
