package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Ce qu'on met dans la fronde, et ce que ça fait en arrivant.
 *
 * Deux promesses se vérifient ici, et elles sont de nature très différente.
 *
 * La première est une **promesse de jeu** : en arcade, un projectile qui casse ce qu'il
 * touche continue son chemin au lieu de rebondir. C'est le défaut que le mode arcade
 * traînait depuis le début — ça cognait, ça cassait, et ça repartait en arrière.
 *
 * La seconde est une **promesse de moteur**, et c'est la plus importante : quoi qu'on
 * rende au projectile, il ne gagne jamais d'énergie. La physique de ce jeu a pour règle
 * de n'en créer nulle part, la traversée est le seul endroit du code qui pose une
 * vitesse à la main, et c'est donc le seul endroit où cette règle pourrait se perdre.
 */
class TrebuchetProjectileTest {

    @After
    fun rendLeMode() {
        TargetRules.style = TargetStyle.JEU
    }

    private fun world(): PhysWorld = PhysWorld().apply {
        iterations = 16
        add(
            PhysBody(900f, 1f, 0f).apply {
                x = 0f; y = -1f
                lockPosition = true; lockRotation = true
                friction = 0.7f
                category = TrebuchetCategory.GROUND
                refreshMass()
            }
        )
    }

    /**
     * Tire un boulet dans une courtine et rend ce qu'il en advient : ce qui a été
     * détruit, et **où le boulet est reparti**.
     */
    private fun tirDansUnMur(style: TargetStyle): Triple<Float, Float, Float> {
        TargetRules.style = style
        val s = TargetField.settle(
            Structure(
                TargetModules.curtainWall(
                    Random(5), 40f, TargetRules.site(10f), TargetRules.site(8f)
                ),
                "courtine"
            )
        )
        val w = world()
        val f = TargetField(w)
        f.load(s)
        repeat(120) { w.stepFrame(1f / 60f); f.update(1f / 60f) }

        val b = PhysBody.circle(Projectile.BOULET.radius, Projectile.BOULET.mass).apply {
            x = 20f; y = s.baseHeight * 0.45f
            vx = 110f
            friction = 0.2f
            restitution = 0.1f
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.projectileMask()
        }
        w.add(b)
        f.trackPiercer(b)
        f.beginShot()

        // Le champ de cibles ne rend pas l'élan tout seul : c'est le jeu qui le fait,
        // et on refait ici, à la main, exactement ce que fait [TrebuchetGame.step].
        //
        // **Le facteur [TargetStyle.pierce] compris.** Cette boucle l'a longtemps ignoré
        // — elle ne s'en servait que comme d'un interrupteur, et rendait toujours la
        // traversée *complète*. Tant que le curseur valait un, la différence ne se
        // voyait pas ; le jour où il est passé à 0,6 pour cesser qu'un seul boulet rase
        // un hameau entier, le banc a continué de mesurer l'ancien jeu et n'aurait rien
        // dit d'une régression.
        val rendu = TargetRules.style.pierce
        var vitesseFinale = 0f
        repeat(240) {
            val vx0 = b.vx
            val vy0 = b.vy
            val e0 = 0.5f * b.mass * (vx0 * vx0 + vy0 * vy0)
            w.stepFrame(1f / 60f)
            f.update(1f / 60f)
            val cost = f.pierceCost(b)
            if (cost > 0f && rendu > 0f) {
                // Plein si le passage a été gagné — pierre déjà fêlée ou coup critique —
                // et un reliquat sinon, exactement comme [TrebuchetGame.pierceThrough].
                val part = rendu *
                    if (f.piercedThrough(b)) 1f else TargetRules.PIERCE_UNEARNED
                val v0 = hypot(vx0, vy0)
                val reste = (e0 - cost).coerceAtLeast(0f)
                val voulu = kotlin.math.sqrt(2f * reste / b.mass)
                val maintenant = hypot(b.vx, b.vy)
                if (voulu > maintenant && v0 > 1f) {
                    val v = maintenant + (voulu - maintenant) * part
                    b.vx = vx0 / v0 * v
                    b.vy = vy0 / v0 * v
                    w.forgetContacts(b)
                }
            }
            vitesseFinale = b.vx
        }
        return Triple(f.brokenRatio, b.x, vitesseFinale)
    }

    /**
     * La promesse de jeu : **le boulet ne repart jamais en arrière**.
     *
     * Elle disait « il traverse » du temps où la traversée était gratuite : le boulet
     * ressortait alors sept mètres derrière la courtine, et le même mécanisme lui
     * faisait raser un hameau entier d'un seul tir. Le passage se **gagne** maintenant
     * — pierre déjà fêlée ou coup critique — et sur une courtine de pierre encore
     * intacte, le premier boulet s'arrête contre elle.
     *
     * C'est la bonne promesse, et c'est la meilleure des deux : ce qu'on ne veut pas
     * voir n'est pas un boulet qui s'arrête, c'est un boulet qui **revient sur le
     * joueur** après avoir cassé ce qu'il touchait. Le tir part de vingt mètres, le mur
     * commence à quarante : en réaliste le boulet finit **derrière** son point de
     * départ, en jeu il finit contre la maçonnerie.
     */
    @Test
    fun `le boulet ne repart jamais en arriere`() {
        val (casseR, xR, vxR) = tirDansUnMur(TargetStyle.REALISTE)
        val (casseA, xA, vxA) = tirDansUnMur(TargetStyle.JEU)
        println(
            "TRAVERSÉE réaliste : ${"%.1f".format(casseR * 100)}% détruit, " +
                "le boulet finit en x=${"%.0f".format(xR)} à ${"%.0f".format(vxR)} m/s"
        )
        println(
            "TRAVERSÉE arcade   : ${"%.1f".format(casseA * 100)}% détruit, " +
                "le boulet finit en x=${"%.0f".format(xA)} à ${"%.0f".format(vxA)} m/s"
        )
        assertTrue(
            "l'arcade ne casse pas plus que le réaliste : $casseA contre $casseR",
            casseA > casseR
        )
        assertTrue(
            "le boulet réaliste n'est pas revenu en arrière : x=$xR (le tir part de 20)",
            xR < 20f
        )
        assertTrue(
            "le boulet de jeu est revenu en arrière : x=$xA (le tir part de 20)",
            xA > 20f
        )
        assertTrue(
            "le boulet de jeu n'a pas atteint le mur : x=$xA (le mur commence à 40)",
            xA > 35f
        )
    }

    /**
     * Le garde-fou : **un projectile ne gagne jamais d'énergie**.
     *
     * On suit l'énergie mécanique — la cinétique plus la pesanteur — d'un tir complet,
     * impact compris. Elle doit décroître à chaque image : la traînée en mange, les
     * chocs en mangent, la traversée en mange encore. Rien n'a le droit d'en ajouter.
     *
     * On ne teste ni la fragmentation ni la bombe, et c'est assumé : la première donne
     * à chaque éclat la masse du paquet entier, la seconde sort son souffle de nulle
     * part. Ce sont deux tricheries déclarées, écrites en toutes lettres dans
     * [Projectile], et le reste du jeu n'en profite pas.
     */
    @Test
    fun `la traversee ne cree jamais d energie`() {
        TargetRules.style = TargetStyle.JEU
        // Le boulet léger et le boulet lourd : le second remplace le « bloc lourd » qui
        // était une entrée du catalogue avant que le poids ne se règle.
        for (kg in listOf(Projectile.DEFAULT_BALL_MASS, 34f)) {
            val kind = "boulet de ${kg.toInt()} kg"
            val g = TrebuchetGame()
            g.config.projectile = Projectile.BOULET
            g.config.ballMass = kg
            g.loadLevel(1L)
            g.release()
            // On amène le projectile devant la cible, sinon il retombe dans un champ et
            // ne casse jamais rien : c'est l'impact qu'on est venu surveiller.
            repeat(90) { g.step(1f / 60f) }
            g.ball.x = g.targets.left - 12f
            g.ball.y = g.targets.baseHeight * 0.45f
            g.ball.vx = 100f
            g.ball.vy = 0f
            g.world.forgetContacts(g.ball)

            fun energie(): Float {
                var e = 0f
                val tous = listOf(g.ball) + g.shards
                for (b in tous) {
                    e += 0.5f * b.mass * (b.vx * b.vx + b.vy * b.vy)
                    e += b.mass * TrebuchetRules.GRAVITY * b.y
                }
                return e
            }

            var pire = 0f
            var e0 = energie()
            var t = 0f
            while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 20f) {
                g.step(1f / 60f)
                t += 1f / 60f
                val e1 = energie()
                pire = maxOf(pire, e1 - e0)
                e0 = e1
            }
            println("ÉNERGIE $kind : gain maximal sur une image ${"%.1f".format(pire)} J")
            // La marge couvre l'intégration : une image de chute libre à cent mètres par
            // seconde n'est pas exactement réversible, et ça se compte en joules.
            assertTrue("$kind a créé de l'énergie : $pire J en une image", pire < 200f)
        }
    }

    @Test
    fun `le paquet se separe pendant la descente et pas avant`() {
        TargetRules.style = TargetStyle.JEU
        val g = TrebuchetGame()
        g.config.projectile = Projectile.FRAGMENTATION
        g.loadLevel(4L)
        g.release()

        var separeEnMontee = false
        var hauteurSeparation = -1f
        var t = 0f
        while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 30f) {
            val montait = g.ball.vy > 0f
            val avant = g.shards.size
            g.step(1f / 60f)
            t += 1f / 60f
            if (avant == 0 && g.shards.isNotEmpty()) {
                hauteurSeparation = g.ball.y
                if (montait) separeEnMontee = true
            }
        }
        println(
            "PAQUET : ${1 + g.shards.size} morceaux, séparés à " +
                "${"%.0f".format(hauteurSeparation)} m pour un sommet à " +
                "${"%.0f".format(g.peakHeight)} m"
        )
        assertEquals("le paquet ne s'est pas séparé en cinq", 4, g.shards.size)
        assertTrue("le paquet s'est séparé pendant la montée", !separeEnMontee)
        assertTrue(
            "le paquet ne s'est pas séparé du tout",
            hauteurSeparation > 0f
        )
        // Trente pour cent de la descente : ni au sommet, ni au ras du sol.
        assertTrue(
            "la séparation est tombée n'importe où : $hauteurSeparation pour un sommet " +
                "à ${g.peakHeight}",
            hauteurSeparation in 0.5f * g.peakHeight..0.9f * g.peakHeight
        )
    }

    @Test
    fun `chaque projectile a sa maniere de casser`() {
        val degats = HashMap<Projectile, Float>()
        for (kind in Projectile.entries) {
            TargetRules.style = TargetStyle.JEU
            val g = TrebuchetGame()
            g.config.projectile = kind
            g.loadLevel(1L)
            g.release()
            repeat(90) { g.step(1f / 60f) }
            g.ball.x = g.targets.left - 12f
            g.ball.y = g.targets.baseHeight * 0.45f
            g.ball.vx = 95f
            g.ball.vy = 0f
            g.world.forgetContacts(g.ball)
            var t = 0f
            while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 20f) {
                g.step(1f / 60f)
                t += 1f / 60f
            }
            degats[kind] = g.targets.brokenRatio
            println(
                "COUP $kind : ${"%.1f".format(g.targets.brokenRatio * 100)}% du hameau, " +
                    "crête ${"%.1f".format(g.targets.ruinHeight())} pour " +
                    "${"%.1f".format(g.targets.baseHeight)} m"
            )
        }
        for ((kind, part) in degats) {
            assertTrue("$kind n'a rien fait au hameau", part > 0.05f)
        }
    }

    @Test
    fun `la bombe souffle a l impact`() {
        TargetRules.style = TargetStyle.JEU
        val g = TrebuchetGame()
        g.config.projectile = Projectile.BOMBE
        g.loadLevel(1L)
        g.release()
        repeat(90) { g.step(1f / 60f) }

        // Elle arrive **lentement** : sans souffle, un boulet à vingt mètres par seconde
        // ne casserait rien du tout. C'est tout l'intérêt de la bombe.
        g.ball.x = g.targets.left + 4f
        g.ball.y = g.targets.baseHeight * 0.5f
        g.ball.vx = 20f
        g.ball.vy = 0f
        g.world.forgetContacts(g.ball)
        var t = 0f
        while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 20f) {
            g.step(1f / 60f)
            t += 1f / 60f
        }
        println("BOMBE : ${"%.1f".format(g.targets.brokenRatio * 100)}% à vingt mètres par seconde")
        assertTrue(
            "la bombe n'a pas soufflé : ${g.targets.brokenRatio}",
            g.targets.brokenRatio > 0.2f
        )
    }

    /**
     * **Une bombe qui a soufflé cesse d'exister.**
     *
     * Elle s'éteignait sans disparaître : le corps restait dans le monde, incapable de
     * toucher autre chose que le sol, et il finissait le tir en roulant sur le terrain.
     * La caméra suit le projectile pendant le vol — elle restait donc accrochée à ce
     * débris sans force pendant que la construction soufflée s'écroulait hors du cadre.
     *
     * Trois choses se vérifient ensemble, et il faut les trois : la bombe quitte le
     * monde, elle ne bouge plus, et **la portée du tir est quand même relevée**. Cette
     * dernière était la raison invoquée pour la garder ; elle se relève au moment du
     * souffle, qui est justement le point d'impact.
     */
    @Test
    fun `la bombe cesse d exister une fois qu elle a explose`() {
        TargetRules.style = TargetStyle.JEU
        val g = TrebuchetGame()
        g.config.projectile = Projectile.BOMBE
        g.loadLevel(1L)
        g.release()
        repeat(90) { g.step(1f / 60f) }

        // Elle arrive lentement sur la construction, comme dans « la bombe souffle à
        // l'impact » : c'est le souffle qu'on veut, pas le choc.
        g.ball.x = g.targets.left + 4f
        g.ball.y = g.targets.baseHeight * 0.5f
        g.ball.vx = 20f
        g.ball.vy = 0f
        g.world.forgetContacts(g.ball)

        var t = 0f
        var xSouffle = Float.NaN
        var xFin = 0f
        while (g.phase == TrebuchetGame.Phase.FLIGHT && t < 20f) {
            val avant = g.ballGone
            g.step(1f / 60f)
            t += 1f / 60f
            if (!avant && g.ballGone) xSouffle = g.ball.x
            xFin = g.ball.x
        }

        println(
            "BOMBE soufflée en x=${"%.1f".format(xSouffle)} m, " +
                "portée relevée ${"%.1f".format(g.shotDistance)} m, " +
                "corps encore dans le monde : ${g.world.bodies.contains(g.ball)}"
        )
        assertTrue("la bombe n'a pas explosé, le test ne prouve rien", g.ballGone)
        assertTrue(
            "la bombe est restée dans le monde après avoir soufflé",
            !g.world.bodies.contains(g.ball)
        )
        // Elle ne roule plus : le corps est retiré, donc plus rien ne l'intègre.
        assertEquals("la bombe a continué sa route après le souffle", xSouffle, xFin, 1e-3f)
        // Et le tir a bien une portée, relevée au point du souffle.
        assertEquals(
            "la portée n'est pas celle du point d'impact",
            xSouffle - TrebuchetRules.FIRING_LINE, g.shotDistance, 0.5f
        )
    }

    /**
     * **Une bombe fait quelque chose à un château de pierre, dans les deux modes.**
     *
     * Le test qui manquait, et son absence a coûté une panne que le joueur a trouvée
     * avant nous : il envoyait bombe sur bombe dans un château réaliste et ne voyait
     * strictement rien se passer. Deux causes empilées — un souffle calibré sur les
     * masses d'arcade, et une charge de quarante-cinq kilojoules qui ne venait de nulle
     * part — et aucun test pour les dénoncer, parce que tous les bancs de la bombe
     * tiraient sur un hameau de bois.
     *
     * On tire donc sur ce qu'il y a de plus dur, et on demande peu : que quelque chose
     * bouge et que quelque chose casse. Le réaliste a le droit d'être lent, il n'a pas
     * le droit d'être inerte.
     */
    @Test
    fun `une bombe entame un chateau dans les deux modes`() {
        for (style in listOf(TargetStyle.REALISTE, TargetStyle.JEU)) {
            TargetRules.style = style
            val g = TrebuchetGame()
            g.config.projectile = Projectile.BOMBE
            g.loadLevel(15L)
            val f = g.targets
            val poses = f.pieces.map { it.body to Pair(it.body.x, it.body.y) }

            repeat(6) {
                g.rebuild()
                g.release()
                repeat(90) { g.step(1f / 60f) }
                // La bombe **arrive de l'extérieur**, comme dans le jeu : posée à
                // l'intérieur de la construction pendant que la cible est en veille,
                // elle en ressort simplement écartée, sans le choc franc qui la
                // déclenche — et le banc mesurait alors le vide.
                g.ball.x = f.left - 10f
                g.ball.y = g.terrain.heightAt(f.left) + 8f
                g.ball.vx = 25f
                g.ball.vy = -5f
                g.world.forgetContacts(g.ball)
                repeat(300) { g.step(1f / 60f) }
            }

            var bouge = 0f
            for ((b, p0) in poses) bouge = maxOf(bouge, hypot(b.x - p0.first, b.y - p0.second))
            println(
                "CHÂTEAU $style, six bombes : ${"%.0f".format(f.brokenRatio * 100)}% cassé, " +
                    "score ${"%.2f".format(f.score)}, pierre la plus déplacée ${"%.1f".format(bouge)} m"
            )
            assertTrue(
                "$style : six bombes n'ont rien déplacé du château (${"%.2f".format(bouge)} m)",
                bouge > 1f
            )
            assertTrue(
                "$style : six bombes n'ont rien entamé du château",
                f.score > 0.05f
            )
        }
    }

    /**
     * **Le poids du boulet remplace les deux entrées du catalogue d'avant.**
     *
     * Un boulet de douze kilos et un « bloc lourd » de trente-quatre étaient deux points
     * fixes sur un axe continu, dans un jeu dont la règle est que les réglages sont
     * continus. Ce qu'on vérifie ici est que l'axe se comporte comme les deux points le
     * faisaient : un caillou léger part vite, un bloc lourd part lentement, et le rayon
     * suit la masse — c'est lui qui décide de la traînée, donc de ce que l'air mange du
     * tir.
     *
     * On ne vérifie **pas** que le lourd va moins loin, et c'est délibéré : ce n'est pas
     * vrai. Un projectile trop léger se fait manger par l'air avant d'arriver, un trop
     * lourd ne part pas assez vite, et l'optimum est quelque part au milieu — il dépend
     * de la machine, et le trouver est précisément ce que le jeu demande au joueur. Un
     * test qui trancherait dans un sens ou dans l'autre figerait un réglage qui doit
     * rester ouvert.
     */
    @Test
    fun `le poids du boulet se regle et emmene le rayon avec lui`() {
        TargetRules.style = TargetStyle.JEU
        val boulet = Projectile.BOULET

        // Le rayon suit la masse en racine cubique, et la loi est calée sur le catalogue
        // d'avant : douze kilos faisaient seize centimètres, trente-quatre en faisaient
        // vingt-quatre écrits à la main. La règle en rend vingt-deux et demi.
        assertEquals(0.16f, boulet.radiusFor(12f), 1e-3f)
        assertEquals(0.225f, boulet.radiusFor(34f), 0.01f)
        assertTrue(
            "un boulet léger devrait être plus petit",
            boulet.radiusFor(Projectile.MIN_BALL_MASS) < boulet.radiusFor(12f)
        )

        fun tir(kg: Float): Triple<Float, Float, Float> {
            val g = TrebuchetGame()
            g.setProjectile(Projectile.BOULET)
            g.setBallMass(kg)
            g.clearLevel()
            val portee = g.simulateShot()
            return Triple(portee, g.peakSpeed, g.config.shotRadius)
        }

        val (porteeLeger, vitesseLeger, rLeger) = tir(Projectile.MIN_BALL_MASS)
        val (porteeLourd, vitesseLourd, rLourd) = tir(Projectile.MAX_BALL_MASS)
        println(
            "POIDS ${Projectile.MIN_BALL_MASS.toInt()} kg : " +
                "${"%.0f".format(vitesseLeger)} m/s, rayon ${"%.2f".format(rLeger)} m, " +
                "${"%.0f".format(porteeLeger)} m ; " +
                "${Projectile.MAX_BALL_MASS.toInt()} kg : ${"%.0f".format(vitesseLourd)} m/s, " +
                "rayon ${"%.2f".format(rLourd)} m, ${"%.0f".format(porteeLourd)} m"
        )
        assertTrue("un boulet léger ne part pas plus vite", vitesseLeger > vitesseLourd)
        assertTrue("le rayon ne suit pas la masse", rLourd > rLeger)
        assertTrue("les deux poids donnent le même tir", porteeLeger != porteeLourd)

        // Et le haut de la plage n'est pas mort. Sur la machine par défaut, accordée pour
        // douze kilos, un boulet de quatre-vingt-dix-neuf part **en arrière** : le
        // crochet lâche bien trop tôt pour cette masse-là, et le jeu a déjà un message
        // pour le dire. C'est le comportement juste, pas un défaut — mais il faut qu'une
        // machine réaccordée autour de lui existe, sinon la borne haute du réglage serait
        // un piège plutôt qu'un choix. Douze tonnes pour quatre-vingt-dix-neuf kilos font
        // un rapport de cent vingt, en plein dans la fourchette des vraies machines.
        var meilleure = -Float.MAX_VALUE
        var crochet = 0f
        var pin = TrebuchetRules.PIN_MIN_DEG
        while (pin <= TrebuchetRules.PIN_MAX_DEG) {
            val g = TrebuchetGame()
            g.setProjectile(Projectile.BOULET)
            g.setBallMass(Projectile.MAX_BALL_MASS)
            g.setCounterweightMass(TrebuchetRules.CW_MAX)
            g.setPinAngle(pin)
            g.clearLevel()
            val p = g.simulateShot()
            if (p > meilleure) {
                meilleure = p
                crochet = pin
            }
            pin += 10f
        }
        println(
            "POIDS lourd réaccordé : ${Projectile.MAX_BALL_MASS.toInt()} kg sous " +
                "${TrebuchetRules.CW_MAX.toInt()} kg de contrepoids, crochet ${crochet.toInt()}° " +
                "→ ${"%.0f".format(meilleure)} m"
        )
        assertTrue(
            "aucun réglage n'envoie le boulet le plus lourd vers l'avant : $meilleure m",
            meilleure > 50f
        )
    }

    /**
     * La charge est un **arbitrage**, pas un curseur : plus de poudre, plus de dégâts,
     * mais une bombe plus lourde qui part moins loin.
     *
     * C'est la seule chose qui empêche le réglage d'être décoratif. Un réglage de
     * puissance sans contrepartie se pousse à fond une fois pour toutes, et le joueur n'y
     * revient jamais.
     */
    @Test
    fun `une grosse charge frappe plus fort et vole moins loin`() {
        TargetRules.style = TargetStyle.JEU

        fun portee(sticks: Int): Float {
            val g = TrebuchetGame()
            g.config.projectile = Projectile.BOMBE
            g.setBombSticks(sticks)
            g.clearLevel()
            return g.simulateShot()
        }

        val leger = portee(Projectile.MIN_STICKS)
        val lourd = portee(Projectile.MAX_STICKS)
        val kind = Projectile.BOMBE
        println(
            "CHARGE ${Projectile.MIN_STICKS} bâton = ${"%.1f".format(kind.massFor(Projectile.MIN_STICKS))} kg " +
                "→ ${"%.0f".format(leger)} m, souffle ${"%.0f".format(kind.blastEnergyFor(Projectile.MIN_STICKS) / 1000f)} kJ " +
                "sur ${"%.1f".format(kind.blastRadiusFor(Projectile.MIN_STICKS))} m ; " +
                "${Projectile.MAX_STICKS} bâtons = ${"%.1f".format(kind.massFor(Projectile.MAX_STICKS))} kg " +
                "→ ${"%.0f".format(lourd)} m, souffle ${"%.0f".format(kind.blastEnergyFor(Projectile.MAX_STICKS) / 1000f)} kJ " +
                "sur ${"%.1f".format(kind.blastRadiusFor(Projectile.MAX_STICKS))} m"
        )
        assertTrue("une bombe chargée à ras bord ne vole pas moins loin : $leger contre $lourd", lourd < leger)
        assertTrue(
            "une grosse charge ne souffle pas plus fort",
            kind.blastEnergyFor(Projectile.MAX_STICKS) > kind.blastEnergyFor(Projectile.MIN_STICKS)
        )
        // Et la charge de référence redonne la bombe d'avant le réglage, au gramme et au
        // joule près : c'est ce qui garantit qu'aucune machine enregistrée ne change de
        // comportement.
        assertEquals(14f, kind.massFor(Projectile.DEFAULT_STICKS), 0.01f)
        assertEquals(400_000f, kind.blastEnergyFor(Projectile.DEFAULT_STICKS), 2_000f)
    }
}
