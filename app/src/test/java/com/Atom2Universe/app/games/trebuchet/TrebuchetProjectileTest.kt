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
        TargetRules.style = TargetStyle.ARCADE
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

        // Le champ de cibles ne rend pas l'élan tout seul : c'est le jeu qui le fait,
        // et on refait ici, à la main, exactement ce que fait [TrebuchetGame.step].
        var vitesseFinale = 0f
        repeat(240) {
            val vx0 = b.vx
            val vy0 = b.vy
            val e0 = 0.5f * b.mass * (vx0 * vx0 + vy0 * vy0)
            w.stepFrame(1f / 60f)
            f.update(1f / 60f)
            val cost = f.pierceCost(b)
            if (cost > 0f && TargetRules.style.pierce > 0f) {
                val v0 = hypot(vx0, vy0)
                val reste = (e0 - cost).coerceAtLeast(0f)
                val voulu = kotlin.math.sqrt(2f * reste / b.mass)
                if (voulu > hypot(b.vx, b.vy) && v0 > 1f) {
                    b.vx = vx0 / v0 * voulu
                    b.vy = vy0 / v0 * voulu
                    w.forgetContacts(b)
                }
            }
            vitesseFinale = b.vx
        }
        return Triple(f.brokenRatio, b.x, vitesseFinale)
    }

    @Test
    fun `en arcade le boulet traverse au lieu de rebondir`() {
        val (casseR, xR, vxR) = tirDansUnMur(TargetStyle.REALISTE)
        val (casseA, xA, vxA) = tirDansUnMur(TargetStyle.ARCADE)
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
        // Le mur est à quarante mètres : un boulet qui traverse en ressort par l'autre
        // côté, un boulet qui rebondit revient en arrière.
        assertTrue("le boulet d'arcade n'a pas traversé le mur : x=$xA", xA > 45f)
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
        TargetRules.style = TargetStyle.ARCADE
        for (kind in listOf(Projectile.BOULET, Projectile.LOURD)) {
            val g = TrebuchetGame()
            g.config.projectile = kind
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
        TargetRules.style = TargetStyle.ARCADE
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
            TargetRules.style = TargetStyle.ARCADE
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
        TargetRules.style = TargetStyle.ARCADE
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
        for (style in listOf(TargetStyle.REALISTE, TargetStyle.ARCADE)) {
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
     * La charge est un **arbitrage**, pas un curseur : plus de poudre, plus de dégâts,
     * mais une bombe plus lourde qui part moins loin.
     *
     * C'est la seule chose qui empêche le réglage d'être décoratif. Un réglage de
     * puissance sans contrepartie se pousse à fond une fois pour toutes, et le joueur n'y
     * revient jamais.
     */
    @Test
    fun `une grosse charge frappe plus fort et vole moins loin`() {
        TargetRules.style = TargetStyle.ARCADE

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
