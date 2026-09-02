package com.Atom2Universe.app.games.trebuchet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI

/**
 * Le ciel : l'heure, les astres, les couleurs, et l'éclipse.
 *
 * Tout se mesure sans écran, parce que [SkyState] ne connaît pas Android — il rend des
 * fractions, des hauteurs et des entiers ARGB, et la vue se contente de les peindre.
 * C'est ce qui permet de vérifier qu'une pleine lune se lève au coucher du soleil, ce
 * qu'aucun coup d'œil à l'application ne dirait avant d'avoir attendu dix minutes.
 */
class TrebuchetSkyTest {

    /** Minuit UTC le 1er janvier 2026, comme point de départ lisible. */
    private val minuit = 1767225600000L
    private val heure = 3_600_000L

    private fun ciel(t: Long) = SkyState().apply { update(t) }

    @Test
    fun `le soleil monte le matin et se couche le soir`() {
        val aube = ciel(minuit + 6 * heure)
        val midi = ciel(minuit + 12 * heure)
        val crepuscule = ciel(minuit + 18 * heure)
        val nuit = ciel(minuit)

        assertEquals("à six heures le soleil devrait être à l'horizon", 0f, aube.sunAltitude, 0.02f)
        assertEquals("à midi il devrait être au zénith", 1f, midi.sunAltitude, 0.02f)
        assertEquals("à dix-huit heures, de nouveau à l'horizon", 0f, crepuscule.sunAltitude, 0.02f)
        assertTrue("à minuit il devrait être sous terre", nuit.sunAltitude < -0.9f)

        // Et il traverse le ciel d'est en ouest sans jamais se téléporter.
        assertTrue("le soleil ne va pas vers l'ouest", midi.sunX > aube.sunX)
        assertTrue("le soleil dépasse le bord", crepuscule.sunX <= 1f)
    }

    @Test
    fun `il fait clair le jour et sombre la nuit`() {
        assertTrue("il ne fait pas jour à midi", ciel(minuit + 12 * heure).light > 0.95f)
        assertTrue("il ne fait pas nuit à minuit", ciel(minuit).light < 0.4f)
        // Le crépuscule n'est ni l'un ni l'autre : c'est tout l'intérêt.
        val entreDeux = ciel(minuit + 19 * heure).light
        assertTrue("le crépuscule est un interrupteur : $entreDeux", entreDeux in 0.02f..0.9f)
    }

    @Test
    fun `les etoiles ne sortent que quand le soleil est parti`() {
        assertEquals("des étoiles à midi", 0f, ciel(minuit + 12 * heure).starAlpha, 1e-3f)
        assertTrue("pas d'étoiles à minuit", ciel(minuit).starAlpha > 0.9f)
    }

    /**
     * **Une pleine lune se lève quand le soleil se couche.**
     *
     * C'est le détail qu'on ne sait pas nommer mais qu'on sent : une lune qui montrerait
     * un croissant en plein milieu de la nuit, ou un disque plein à midi, aurait l'air
     * fausse sans qu'on puisse dire pourquoi. La position sort de l'élongation, donc des
     * éphémérides, donc elle est juste par construction — ce test vérifie qu'on n'a pas
     * inversé le signe en chemin.
     */
    @Test
    fun `la lune est au bon endroit du ciel pour la phase qu elle montre`() {
        // On balaie un mois lunaire à la recherche d'une pleine lune et d'une nouvelle.
        var pleine = -1L
        var nouvelle = -1L
        for (j in 0 until 30) {
            val t = minuit + j * 24L * heure
            val p = ciel(t).moonPhase
            if (p > 0.99f && pleine < 0) pleine = t
            if (p < 0.01f && nouvelle < 0) nouvelle = t
        }
        assertTrue("aucune pleine lune en un mois", pleine > 0)
        assertTrue("aucune nouvelle lune en un mois", nouvelle > 0)

        // Pleine lune : à minuit elle est haute, à midi elle est couchée.
        val minuitPleine = ciel(pleine - pleine % (24L * heure) + 0 * heure)
        val midiPleine = ciel(pleine - pleine % (24L * heure) + 12 * heure)
        println(
            "LUNE pleine : phase ${"%.2f".format(minuitPleine.moonPhase)}, " +
                "hauteur à minuit ${"%.2f".format(minuitPleine.moonAltitude)}, " +
                "à midi ${"%.2f".format(midiPleine.moonAltitude)}"
        )
        assertTrue(
            "une pleine lune devrait être haute à minuit",
            minuitPleine.moonAltitude > midiPleine.moonAltitude
        )

        // Nouvelle lune : elle suit le soleil, donc elle est au ciel en plein jour.
        val midiNouvelle = ciel(nouvelle - nouvelle % (24L * heure) + 12 * heure)
        println(
            "LUNE nouvelle : phase ${"%.2f".format(midiNouvelle.moonPhase)}, " +
                "hauteur à midi ${"%.2f".format(midiNouvelle.moonAltitude)}"
        )
        assertTrue(
            "une nouvelle lune devrait accompagner le soleil",
            midiNouvelle.moonAltitude > 0.5f
        )
    }

    /**
     * **La phase du jour est celle du calendrier.**
     *
     * Les quatre instants ci-dessous ne sortent pas de ce code : ce sont les nouvelles
     * et pleines lunes d'août et septembre 2026 telles que les donnent les éphémérides
     * (Meeus, chapitre 49). C'est le seul test qui puisse dire que la Lune du jeu est
     * bien celle du ciel, et c'est celui qui manquait — les autres vérifiaient la
     * cohérence interne, qui restait parfaite pendant que la phase se trompait d'une
     * demi-journée.
     *
     * Elle se trompait parce que le code lisait l'élongation **moyenne** D au lieu de la
     * vraie : treize heures d'écart au pire, soit un croissant visiblement en avance sur
     * l'almanach deux semaines par mois.
     */
    @Test
    fun `les nouvelles et pleines lunes tombent aux dates des ephemerides`() {
        // Nouvelles lunes : 12 août 2026 17h38 UTC, 11 septembre 2026 03h28 UTC.
        // Pleines lunes : 28 août 2026 04h19 UTC, 26 septembre 2026 16h50 UTC.
        val nouvelles = longArrayOf(1_786_556_280_000L, 1_789_097_280_000L)
        val pleines = longArrayOf(1_787_890_740_000L, 1_790_441_400_000L)

        for (t in nouvelles) {
            val p = ciel(t).moonPhase
            println("LUNE nouvelle attendue : phase ${"%.4f".format(p)}")
            assertTrue("la nouvelle lune de l'almanach est éclairée à ${"%.3f".format(p)}", p < 0.005f)
        }
        for (t in pleines) {
            val p = ciel(t).moonPhase
            println("LUNE pleine attendue : phase ${"%.4f".format(p)}")
            assertTrue("la pleine lune de l'almanach n'est qu'à ${"%.3f".format(p)}", p > 0.995f)
        }

        // Et entre les deux elle croît, puis décroît — le sens du croissant en dépend.
        val jour = 24L * heure
        assertTrue("la lune ne croît pas après la nouvelle", ciel(nouvelles[1] + 3 * jour).moonWaxing)
        assertTrue("la lune croît encore après la pleine", !ciel(pleines[1] + 3 * jour).moonWaxing)

        // Au premier quartier, sept jours et demi après la nouvelle, la moitié du disque.
        val quartier = ciel(nouvelles[1] + 7 * jour + 9 * heure).moonPhase
        println("LUNE premier quartier : phase ${"%.3f".format(quartier)}")
        assertEquals("le premier quartier n'est pas à moitié", 0.5f, quartier, 0.06f)
    }

    /**
     * **La sphère éclairée montre exactement la part éclairée.**
     *
     * C'est le test qui remplace tous les autres depuis que la Lune est une vraie sphère
     * texturée : on ne trace plus une forme censée valoir la phase, on éclaire un
     * caillou rond et on compte les pixels qui voient le Soleil. Si le compte tombe
     * juste pour vingt-et-une phases, il n'y a plus de place pour une inversion ni pour
     * un terminateur qui bomberait du mauvais côté.
     *
     * L'intégration se fait à la main sur une grille de quatre cents points : c'est
     * lourd pour un test, mais c'est la seule mesure qui ne suppose rien de la formule
     * qu'elle vérifie.
     */
    @Test
    fun `l eclairage de la sphere couvre exactement la part eclairee`() {
        val n = 400
        for (i in 0..20) {
            val k = i / 20f
            val sx = MoonLight.sunX(k, true)
            val sz = MoonLight.sunZ(k)
            var dedans = 0
            var eclaire = 0
            for (py in 0 until n) {
                for (px in 0 until n) {
                    val u = (px + 0.5f) / n * 2f - 1f
                    val v = (py + 0.5f) / n * 2f - 1f
                    if (u * u + v * v > 1f) continue
                    dedans++
                    if (MoonLight.incidence(u, v, sx, sz) > 0f) eclaire++
                }
            }
            val part = eclaire.toFloat() / dedans
            assertEquals("à k=$k la sphère montre ${"%.3f".format(part)}", k, part, 0.01f)
        }
    }

    /**
     * **Le croissant pointe du bon côté**, et le quartier est droit.
     *
     * Un croissant à l'envers est la faute que personne ne sait nommer mais que tout le
     * monde sent. Elle tient à un seul signe, celui de la composante horizontale du
     * Soleil vu depuis la Lune, d'où ce test qui ne vérifie que ça.
     */
    @Test
    fun `la lune croissante est eclairee a droite`() {
        // À demi-lune croissante, la droite du disque voit le Soleil et la gauche non.
        val sx = MoonLight.sunX(0.5f, waxing = true)
        val sz = MoonLight.sunZ(0.5f)
        assertTrue("la droite du premier quartier est dans l'ombre", MoonLight.incidence(0.5f, 0f, sx, sz) > 0f)
        assertTrue("la gauche du premier quartier est éclairée", MoonLight.incidence(-0.5f, 0f, sx, sz) < 0f)
        // Et le terminateur passe pile par le centre : c'est ça, un quartier.
        assertEquals("le quartier n'est pas droit", 0f, MoonLight.incidence(0f, 0.7f, sx, sz), 1e-6f)

        // Décroissante, tout s'inverse.
        val dx = MoonLight.sunX(0.5f, waxing = false)
        assertTrue("le dernier quartier est éclairé à droite", MoonLight.incidence(0.5f, 0f, dx, sz) < 0f)
        assertTrue("le dernier quartier est sombre à gauche", MoonLight.incidence(-0.5f, 0f, dx, sz) > 0f)

        // Pleine lune : tout le disque voit le Soleil, jusqu'au limbe.
        val px = MoonLight.sunX(1f, true)
        val pz = MoonLight.sunZ(1f)
        assertTrue("la pleine lune a une ombre", MoonLight.incidence(0.99f, 0f, px, pz) > 0f)
    }

    /**
     * **La lumière cendrée se voit au croissant et pas ailleurs.**
     *
     * La face nocturne de la Lune est éclairée par la Terre, et la Terre vue de la Lune
     * montre la phase complémentaire : elle est pleine quand la Lune est nouvelle. Le
     * reste du disque se devine donc autour d'un croissant fin, et pas autour d'une
     * gibbeuse — ce qui est exactement ce qu'on voit dans le ciel.
     */
    @Test
    fun `la lumiere cendree ne se voit qu au croissant`() {
        val fin = MoonLight.earthshine(0.05f)
        val gibbeuse = MoonLight.earthshine(0.9f)
        println("CENDRÉE croissant ${"%.3f".format(fin)}, gibbeuse ${"%.3f".format(gibbeuse)}")
        assertTrue("un croissant n'a pas de cendre", fin > gibbeuse * 3f)
        assertEquals("une pleine lune a une face nocturne", 0f, MoonLight.earthshine(1f), 1e-6f)

        // Et la nuit lunaire reste franchement plus sombre que le jour, sinon la phase
        // ne se lirait plus.
        val jour = MoonLight.brightness(1f, fin)
        val nuit = MoonLight.brightness(-0.5f, fin)
        assertEquals("le plein soleil n'éclaire pas à fond", 1f, jour, 1e-4f)
        assertTrue("la nuit lunaire est trop claire : $nuit", nuit < 0.15f)
        // Le terminateur ne fait pas une marche : à incidence nulle on est déjà à la
        // cendre, et ça monte continûment.
        assertEquals("le terminateur est une marche", fin, MoonLight.brightness(0f, fin), 1e-6f)

        // Et de jour, la face nocturne s'efface au lieu de faire un disque gris dans le
        // bleu — c'est ce que le ciel fait vraiment à une lune de midi.
        assertEquals("la nuit, la face sombre disparaît", 1f, MoonLight.opacity(nuit, 0f), 1e-6f)
        assertEquals("de jour, la face sombre reste", nuit, MoonLight.opacity(nuit, 1f), 1e-6f)
        assertEquals("le croissant s'efface de jour", 1f, MoonLight.opacity(1f, 1f), 1e-6f)
    }

    /**
     * **Ce qu'on peint vaut la phase, et non son complément.**
     *
     * Le bord de la lumière est une demi-ellipse posée sur un demi-limbe, et l'aire de
     * cette figure se calcule : `πr²/2 + πra/2`, avec `a` le demi-axe du terminateur.
     * Elle doit valoir exactement `k` fois le disque. C'est la vérification qui aurait
     * attrapé le bug d'origine — un second disque décalé de la part **non** éclairée,
     * qui affichait une pleine lune noire et une nouvelle lune pleine.
     */
    @Test
    fun `la figure du terminateur a l aire de la part eclairee`() {
        for (i in 0..20) {
            val k = i / 20f
            val a = SkyState.terminatorAxis(k)   // en fraction du rayon
            // Aire de la figure, rayon 1 : le demi-limbe plus la demi-ellipse signée.
            val aire = (PI.toFloat() / 2f) + (PI.toFloat() * a / 2f)
            val part = aire / PI.toFloat()
            assertEquals("à k=$k on peint ${"%.3f".format(part)} du disque", k, part, 1e-5f)
        }
        // Et le sens du bombement : gibbeuse vers l'ombre, croissant vers la lumière.
        assertTrue("une gibbeuse ne bombe pas du bon côté", SkyState.terminatorAxis(0.8f) > 0f)
        assertTrue("un croissant ne se creuse pas", SkyState.terminatorAxis(0.2f) < 0f)
        assertEquals("le quartier n'est pas droit", 0f, SkyState.terminatorAxis(0.5f), 1e-6f)
    }

    @Test
    fun `la phase parcourt tout le cycle en un mois lunaire`() {
        var mini = 1f
        var maxi = 0f
        for (j in 0 until 30) {
            val p = ciel(minuit + j * 24L * heure).moonPhase
            if (p < mini) mini = p
            if (p > maxi) maxi = p
        }
        println("LUNE phase de ${"%.2f".format(mini)} à ${"%.2f".format(maxi)} sur un mois")
        assertTrue("la lune ne devient jamais nouvelle : $mini", mini < 0.05f)
        assertTrue("la lune ne devient jamais pleine : $maxi", maxi > 0.95f)
    }

    /**
     * Le crépuscule passe par **l'orange et le violet**, et pas par un gris.
     *
     * C'est toute la raison d'avoir cinq paliers de couleur au lieu de deux : entre une
     * nuit bleue et un jour bleu, une interpolation directe ne donne rien. On vérifie
     * donc que l'horizon devient franchement plus rouge que bleu au moment du coucher,
     * ce qu'aucune interpolation nuit↔jour ne ferait.
     */
    @Test
    fun `le couchant rougit l horizon`() {
        fun rouge(c: Int) = (c shr 16) and 0xFF
        fun bleu(c: Int) = c and 0xFF

        val midi = ciel(minuit + 12 * heure)
        val couchant = ciel(minuit + 18 * heure)
        val nuit = ciel(minuit)

        println(
            "CIEL horizon midi=${"%06X".format(midi.horizon and 0xFFFFFF)} " +
                "couchant=${"%06X".format(couchant.horizon and 0xFFFFFF)} " +
                "nuit=${"%06X".format(nuit.horizon and 0xFFFFFF)}"
        )
        assertTrue("un midi bleu devrait l'être", bleu(midi.horizon) > rouge(midi.horizon))
        assertTrue("un couchant devrait rougir", rouge(couchant.horizon) > bleu(couchant.horizon) + 40)
        assertTrue("une nuit devrait rester bleue", bleu(nuit.horizon) > rouge(nuit.horizon))
    }

    /**
     * **Une éclipse finit par arriver, et pas tous les mois.**
     *
     * Les deux moitiés comptent autant : un ciel où le Soleil s'éteint à chaque nouvelle
     * lune serait faux et lassant, un ciel où ça n'arrive jamais ne mériterait pas le
     * code. On balaie donc trois années de jeu et on demande que ce soit rare — mais
     * réel.
     */
    @Test
    fun `une eclipse arrive parfois et jamais tous les mois`() {
        var jours = 0
        var eclipses = 0
        var pire = 0f
        val pas = heure / 2
        var t = minuit
        var dansUne = false
        while (t < minuit + 3L * 365 * 24 * heure) {
            val e = ciel(t).eclipse
            if (e > pire) pire = e
            if (e > 0f && !dansUne) {
                eclipses++
                dansUne = true
            } else if (e == 0f) {
                dansUne = false
            }
            t += pas
            jours++
        }
        println(
            "ÉCLIPSES $eclipses en trois ans, couverture maximale ${"%.2f".format(pire)}"
        )
        assertTrue("aucune éclipse en trois ans : le code ne sert à rien", eclipses >= 2)
        assertTrue("une éclipse par mois : ce n'est plus une éclipse", eclipses <= 20)
        assertTrue("aucune éclipse n'est franche : $pire", pire > 0.4f)
    }

    @Test
    fun `une eclipse assombrit sans faire la nuit`() {
        // On cherche le moment le plus couvert, puis on regarde ce qu'il fait.
        var meilleur = minuit
        var pire = 0f
        var t = minuit
        while (t < minuit + 3L * 365 * 24 * heure) {
            val c = ciel(t)
            // Une éclipse ne se voit que si le soleil est levé.
            if (c.eclipse > pire && c.sunAltitude > 0.3f) {
                pire = c.eclipse
                meilleur = t
            }
            t += heure / 2
        }
        val pendant = ciel(meilleur)
        // Le jour suivant, à la même heure : même soleil, plus d'éclipse. Comparer à
        // douze heures d'écart comparerait à la nuit, ce qui ne dit rien.
        val avant = ciel(meilleur + 24 * heure)
        println(
            "ÉCLIPSE couverture ${"%.2f".format(pendant.eclipse)}, " +
                "clarté ${"%.2f".format(pendant.light)} contre ${"%.2f".format(avant.light)} un jour ordinaire"
        )
        assertTrue("aucune éclipse de jour trouvée", pire > 0.3f)
        assertTrue("l'éclipse n'assombrit rien", pendant.light < avant.light)
        assertTrue("l'éclipse fait la nuit noire", pendant.light > 0.05f)
    }

    /**
     * **Un site allumé s'éteint quand on le démolit.**
     *
     * C'est la promesse de la nuit, et elle ne tient à aucun code d'extinction : une
     * flamme appartient à une pierre, donc casser la pierre emporte la flamme. Ce test
     * vérifie les deux bouts — qu'un site s'allume, et qu'un site rasé est noir.
     */
    @Test
    fun `un site rase n a plus de lumieres`() {
        TargetRules.style = TargetStyle.ARCADE
        val g = TrebuchetGame()
        g.loadLevel(4L)
        val f = g.targets
        val allumees = f.pieces.count { it.lightRadius > 0f }
        println(
            "FEUX ${f.pieces.size} pierres, $allumees allumées, " +
                "front de ${"%.0f".format(f.right - f.left)} m"
        )
        assertTrue("un village n'a aucune lumière", allumees >= 2)
        assertTrue("le village est une vitrine : $allumees lumières", allumees <= f.pieces.size / 3)

        // On rase tout, et le noir doit se faire tout seul.
        repeat(14) {
            val x = f.left + (it % 7) * (f.right - f.left) / 7f
            f.blast(x, g.terrain.heightAt(x) + 2f, 900_000f, 30f)
            repeat(60) { g.step(1f / 60f) }
        }
        val reste = f.pieces.count { it.lightRadius > 0f }
        println("FEUX après la démolition : $reste allumées sur ${f.pieces.size} pierres")
        assertTrue("le village brûle encore après avoir été rasé : $reste", reste < allumees)
    }

    /**
     * Une lumière se pose **à hauteur d'homme**, au-dessus de son propre sol.
     *
     * Sur un site étagé, « à hauteur d'homme » ne veut rien dire dans l'absolu : une
     * torche posée à quatre mètres d'altitude est à hauteur d'homme sur la terrasse du
     * bas et au ras du sol sur celle du haut. C'est encore le zéro implicite, et c'est
     * la raison pour laquelle la pose consulte le relief.
     */
    @Test
    fun `les lumieres se posent au-dessus de leur propre sol`() {
        TargetRules.style = TargetStyle.ARCADE
        for (seed in longArrayOf(8L, 14L, 4L, 2L)) {
            val g = TrebuchetGame()
            g.loadLevel(seed)
            for (p in g.targets.pieces) {
                if (p.lightRadius <= 0f) continue
                val hauteur = p.body.y - g.terrain.heightAt(p.body.x)
                assertTrue(
                    "graine $seed : une lumière flotte à ${"%.1f".format(hauteur)} m de son sol",
                    hauteur >= TargetRules.site(1f) - 0.5f &&
                        hauteur <= TargetRules.site(6f) + 0.5f
                )
            }
        }
    }

    /**
     * Le balayage du temps à la main : ce que le doigt promet.
     *
     * Trois choses à tenir, et chacune s'est décidée pour une raison. Au point d'appui,
     * **le temps s'arrête** — attraper l'heure la retient, ce qui est la moitié de
     * l'intérêt du geste. À droite il avance, à gauche il recule. Et il ne dépasse jamais
     * une heure par seconde, sinon un pouce qui dérape ferait sauter le ciel d'une
     * journée.
     */
    @Test
    fun `le balayage du temps va d une heure par seconde a rien du tout`() {
        val course = 360f

        assertEquals("au point d'appui le temps devrait s'arrêter", 0f, SkyClock.scrubRate(0f, course), 1e-4f)
        assertEquals(
            "à mi-course on devrait aller à mi-vitesse",
            SkyClock.MAX_SCRUB_RATE / 2f, SkyClock.scrubRate(course / 2f, course), 1e-3f
        )
        assertEquals(
            "à pleine course, une heure par seconde",
            SkyClock.MAX_SCRUB_RATE, SkyClock.scrubRate(course, course), 1e-3f
        )
        assertEquals(
            "vers la gauche, le temps devrait reculer",
            -SkyClock.MAX_SCRUB_RATE, SkyClock.scrubRate(-course, course), 1e-3f
        )
        // Au-delà de la course, on ne va pas plus vite : le doigt sort de l'écran bien
        // avant que le ciel ne devienne illisible.
        assertEquals(
            "le balayage s'emballe hors de la course",
            SkyClock.MAX_SCRUB_RATE, SkyClock.scrubRate(course * 10f, course), 1e-3f
        )
        // Un écran de largeur nulle n'existe pas, mais une vue pas encore mesurée, si.
        assertEquals(0f, SkyClock.scrubRate(100f, 0f), 1e-4f)
    }

    @Test
    fun `une seconde de balayage a pleine vitesse avance d une heure`() {
        val h = SkyClock(minuit)
        val vitesse = SkyClock.scrubRate(500f, 500f)
        h.scrub(vitesse * 1f)
        assertEquals("une seconde à pleine vitesse ne fait pas une heure", heure, h.instant - minuit)
    }

    /**
     * **Les nuages vont dans le sens du vent.** C'est tout le bug qu'ils corrigent.
     *
     * Les stries qu'ils remplacent étaient inclinées selon l'angle du vent mais
     * dérivaient toujours vers la droite : leur vitesse se calculait à partir de
     * `Wind.speed`, qui est une norme et donc toujours positive. Par vent debout, elles
     * pointaient à gauche et filaient à droite, et personne ne l'a vu pendant des mois
     * parce qu'un trait fin qui bouge n'a pas de sens de lecture évident.
     *
     * On dérive donc avec `vx`, dont le signe est celui du vent, et ce test le dit.
     */
    @Test
    fun `les nuages derivent dans le sens du vent`() {
        fun apres(vx: Float): Float {
            val champ = CloudField()
            val depart = champ.clouds[0].x
            champ.update(10f, vx)
            // L'écart signé, rebouclé sur la moitié de la bande : un nuage qui sort d'un
            // côté rentre de l'autre, et une soustraction naïve dirait qu'il a reculé.
            val demi = CloudField.SPAN / 2f
            var d = champ.clouds[0].x - depart
            if (d > demi) d -= CloudField.SPAN
            if (d < -demi) d += CloudField.SPAN
            return d
        }

        val arriere = apres(Wind.MAX_SPEED)
        val debout = apres(-Wind.MAX_SPEED)
        println(
            "NUAGES vent arrière : ${"%.1f".format(arriere)} m en 10 s ; " +
                "vent debout : ${"%.1f".format(debout)} m"
        )
        assertTrue("un vent arrière ne pousse pas les nuages vers la cible", arriere > 0f)
        assertTrue("un vent debout ne les ramène pas", debout < 0f)
        assertEquals("le vent n'est pas symétrique", arriere, -debout, 0.01f)
        assertEquals("un air calme fait bouger les nuages", 0f, apres(0f), 1e-6f)
    }

    /**
     * **Un nuage va à la vitesse du vent**, puisqu'il est dedans.
     *
     * Ce n'est pas une coquetterie : c'est ce qui a remplacé un facteur de dérive
     * inventé, du temps où les nuages vivaient en fractions d'écran et où « vite » ne
     * voulait rien dire faute d'unité. En mètres par seconde, la question ne se pose
     * plus — et le réglage se vérifie au lieu de se croire.
     */
    @Test
    fun `un nuage va a la vitesse du vent`() {
        val champ = CloudField()
        val c = champ.clouds[0]
        val depart = c.x
        champ.update(1f, 5f)
        assertEquals(
            "un nuage ne suit pas le vent à sa profondeur près",
            5f * c.depth, c.x - depart, 1e-3f
        )
        // Et la traversée de la bande reste de l'ordre de la minute par vent fort.
        val traversee = CloudField.SPAN / (Wind.MAX_SPEED * c.depth)
        println("NUAGES traversée de la bande par vent fort : ${"%.0f".format(traversee)} s")
        assertTrue("les nuages filent ou se traînent : $traversee s", traversee in 60f..250f)
    }

    /**
     * **Un nuage est un objet du monde, et il est haut.**
     *
     * Assez haut pour être hors de l'écran quand la vue est serrée sur la machine : à
     * ce cadrage-là on voit une cinquantaine de mètres de ciel, et le plus bas des
     * nuages est trois fois plus haut. Ils apparaissent quand on recule, et c'est le
     * comportement voulu — deux versions ont tenté de le contourner avec un calque à
     * l'échelle bornée, et ça n'a produit que des nuages collés à la dalle.
     */
    @Test
    fun `les nuages sont trop haut pour un cadrage serre sur la machine`() {
        // Ce que la vue montre du ciel quand elle cadre la machine : la hauteur de
        // l'écran divisée par l'échelle. Une quinzaine de mètres de machine, une marge,
        // et on tourne autour de la soixantaine de mètres visibles.
        val cielVisible = 60f
        println(
            "NUAGES le plus bas à ${CloudField.MIN_ALTITUDE.toInt()} m, " +
                "le plus haut à ${CloudField.MAX_ALTITUDE.toInt()} m ; " +
                "cadrage machine : ${cielVisible.toInt()} m de ciel visible"
        )
        assertTrue(
            "un nuage se voit alors qu'on règle la machine : " +
                "${CloudField.MIN_ALTITUDE} m contre $cielVisible m de ciel",
            CloudField.MIN_ALTITUDE > cielVisible * 2f
        )
        // Et ils tiennent dans le cadrage large, sinon on ne les verrait jamais.
        val cielRecule = 1100f
        assertTrue(
            "les nuages restent hors champ même en reculant : ${CloudField.MAX_ALTITUDE} m",
            CloudField.MAX_ALTITUDE < cielRecule
        )
    }

    @Test
    fun `les nuages sont en l air, en metres, et ne se ressemblent pas`() {
        val champ = CloudField()
        for (c in champ.clouds) {
            assertTrue("un nuage sous terre : ${c.altitude} m", c.altitude >= CloudField.MIN_ALTITUDE)
            assertTrue("un nuage en orbite : ${c.altitude} m", c.altitude <= CloudField.MAX_ALTITUDE)
            assertTrue("un nuage a moins de quatre boules", c.puffs.size >= 12)
            assertTrue("un nuage sans épaisseur : ${c.size} m", c.size >= CloudField.MIN_SIZE)
            assertTrue("un nuage plus gros qu'un orage : ${c.size} m", c.size <= CloudField.MAX_SIZE)
            assertTrue("un nuage dans la bande", c.x >= 0f && c.x < CloudField.SPAN)
        }
        // Deux nuages identiques feraient un motif : on vérifie qu'ils diffèrent.
        assertTrue(
            "les nuages sont tous à la même altitude",
            champ.clouds.map { it.altitude }.toSet().size > 3
        )
        // Et la bande dépasse la portée du jeu, sinon le joueur verrait deux fois le
        // même nuage à l'écran.
        assertTrue(
            "la bande de ciel est plus courte que le terrain de tir",
            CloudField.SPAN > TargetGenerator.MAX_DISTANCE
        )
    }

    @Test
    fun `l horloge court soixante-douze fois plus vite que la vraie`() {
        val h = SkyClock(minuit)
        h.advance(SkyClock.DAY_SECONDS)
        val ecoule = h.instant - minuit
        assertTrue(
            "un jour de jeu ne fait pas vingt minutes réelles : $ecoule ms",
            kotlin.math.abs(ecoule - 24L * heure) < 60_000L
        )

        val avant = h.instant
        h.scrub(-3f)
        assertEquals("le balayage ne recule pas", -3 * heure, h.instant - avant)
    }
}
