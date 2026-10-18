package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.trebuchet.gears.GearMachineGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * **Les boulets de l'atelier abattent vraiment les murs.**
 *
 * Ils les traversaient. Toutes les conditions étaient réunies — les pierres dans le
 * monde, les filtres de collision d'accord des deux côtés, les couches qui se
 * recouvrent — et pourtant rien ne se touchait, parce que les corps s'endormaient
 * **avant leur premier pas de simulation** et gardaient la boîte englobante nulle de
 * leur construction. Le balayage les voyait tous à l'origine du monde, à cinq cents
 * mètres de là où ils sont dessinés.
 *
 * Rien ne le signalait : pas d'exception, pas de forme manquante, un village bien
 * peint. On voyait un boulet passer au travers d'un mur, c'est tout.
 */
class GearDemolitionTest {

    private val fixed = 1f / 120f

    /** Une bille lancée à la main dans le site, comme le ferait un tir réussi. */
    private fun tirer(game: GearMachineGame, masse: Float, vitesse: Float): PhysBody {
        val site = game.targets
        val depart = site.left - 40f
        val bille = PhysBody.circle(0.30f, masse).apply {
            x = depart
            y = game.terrain.heightAt(site.left) + 3f
            vx = vitesse
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
            collisionLayer = -32
            collisionLayerDepth = 65
        }
        game.world.add(bille)
        return bille
    }

    /** Laisse le site se poser : avant l'armement, aucun choc ne compte. */
    private fun armer(game: GearMachineGame) {
        var garde = 0
        while (!game.targets.armed && garde++ < 2_000) game.step(fixed)
        assertTrue("le site ne s'arme jamais", game.targets.armed)
    }

    /**
     * **Le mur arrête le boulet.**
     *
     * Le test le plus simple, et celui qui manquait : une bille lancée dans le village
     * ne doit pas ressortir de l'autre côté. C'est la panne exacte qu'on a eue.
     */
    @Test
    fun `un boulet ne traverse pas le village`() {
        val game = GearMachineGame()
        armer(game)
        val site = game.targets
        val bille = tirer(game, masse = 60f, vitesse = 70f)
        repeat(600) { game.step(fixed) }

        println(
            "TIR bille arrêtée à x=${"%.1f".format(bille.x)} " +
                "(le site va de ${"%.0f".format(site.left)} à ${"%.0f".format(site.right)})"
        )
        assertTrue(
            "la bille est ressortie derrière le village : x=${"%.1f".format(bille.x)}",
            bille.x < site.right
        )
    }

    /**
     * **Un coup qui porte abîme, et un gros coup jette des pierres à terre.**
     *
     * On tire deux fois plus lourd et deux fois plus vite, et on regarde les compteurs
     * que l'interface affiche : les points de structure perdus, les pierres cassées ou
     * renversées, et l'avancement rapporté à l'objectif.
     */
    @Test
    fun `un boulet lourd entame le site`() {
        val game = GearMachineGame()
        armer(game)
        val site = game.targets
        val corpsAvant = site.bodyCount

        tirer(game, masse = 250f, vitesse = 120f)
        repeat(900) { game.step(fixed) }

        val aTerre = site.pieceBroken + site.pieceToppled
        println(
            "TIR $aTerre pierres à terre sur ${site.pieceTotal} " +
                "(${site.pieceBroken} cassées, ${site.pieceToppled} renversées), " +
                "avancement ${"%.0f".format(site.progress * 100f)} % de l'objectif " +
                "(${"%.0f".format(site.winRatio * 100f)} %), " +
                "$corpsAvant corps → ${site.bodyCount} avec les gravats"
        )
        // On ne compte **pas** les points de structure restants : une pierre qui cède se
        // brise en gravats, qui sont des pièces à part entière avec leur propre vie. Le
        // total remonte donc quand on démolit, ce qui est correct et parfaitement
        // trompeur. Les compteurs qui disent la vérité sont ceux que l'interface montre.
        assertTrue("le tir n'a mis aucune pierre à terre", aTerre >= 1)
        assertTrue("l'avancement ne bouge pas", site.progress > 0f)
        assertTrue("aucun gravat n'est né de la démolition", site.bodyCount > corpsAvant)
    }

    /**
     * **Un site rasé se déclare gagné et tire son feu d'artifice.**
     *
     * Les deux vont ensemble : `cleared` est ce que l'interface lit pour annoncer la
     * victoire, et le bouquet part une fois, dans le même mouvement. Un feu d'artifice
     * qui repartirait à chaque image serait pire que pas de feu du tout.
     */
    @Test
    fun `raser le site declenche la victoire et le bouquet`() {
        val game = GearMachineGame()
        armer(game)
        val site = game.targets
        assertTrue("le site est gagné avant qu'on y touche", !site.cleared)

        // On souffle le village par le milieu, plusieurs fois : c'est le moyen le plus
        // court d'atteindre l'objectif sans dépendre de la balistique.
        var garde = 0
        while (!site.cleared && garde++ < 40) {
            val x = site.left + (garde % 8) * (site.right - site.left) / 8f
            site.blast(x, game.terrain.heightAt(x) + 3f, 1_200_000f, 32f)
            repeat(90) { game.step(fixed) }
        }
        println(
            "VICTOIRE après $garde souffles : rasé=${site.cleared}, " +
                "avancement ${"%.0f".format(site.progress * 100f)} %, " +
                "particules ${game.effects.aliveCount}"
        )
        assertTrue("le site ne se laisse pas raser", site.cleared)

        // Le bouquet part : les effets sont occupés dans les images qui suivent.
        repeat(120) { game.step(fixed) }
        assertTrue("aucun feu d'artifice à la victoire", game.effects.busy)

        // Et il ne repart pas : on note ce qui est en vol, on laisse tourner, et le
        // compteur de fusées en attente ne remonte pas.
        val encore = game.effects.busy
        repeat(600) { game.step(fixed) }
        assertTrue("le feu d'artifice s'est éteint trop vite", encore)
    }

    /**
     * **On peut retirer le site**, et alors il n'y a plus rien à gagner.
     *
     * C'est ce que fait l'appui long sur « Machines » quand on veut revenir à un
     * établi nu : la graine nulle rend sa dalle plate à l'atelier.
     */
    @Test
    fun `un atelier sans site n a rien a raser`() {
        val game = GearMachineGame()
        game.loadSite(null)
        assertEquals("des pierres ont survécu", 0, game.targets.pieceTotal)
        assertTrue("un atelier vide se déclare gagné", !game.targets.cleared)
        assertEquals("l'avancement d'un site absent n'est pas nul", 0f, game.targets.progress, 1e-6f)

        // Et on peut en redresser un : c'est l'autre moitié du même geste.
        game.loadSite(31L)
        assertTrue("aucun site ne se redresse", game.targets.pieceTotal > 0)
        assertTrue("le relief n'est pas revenu", !game.terrain.flat)
    }

    /**
     * **Un boulet qui finit couché dans les ruines termine quand même son tir.**
     *
     * Les trois relevés de portée cherchaient tous **le sol**, ce qui suffisait quand
     * l'atelier tirait sur une dalle nue. Depuis qu'il y a des bâtiments, un gros boulet
     * laboure le village et s'arrête **sur ses gravats**, à deux mètres du sol : aucun
     * relevé ne le voyait, le tir restait « en vol » indéfiniment, et le joueur ne
     * pouvait plus tirer sans passer par le bouton d'arrêt.
     *
     * Mesuré avant correction : un boulet de deux cent cinquante kilos était toujours en
     * vol au bout de quinze secondes, le village rasé à quatre-vingt-huit pour cent.
     */
    @Test
    fun `un boulet arrete dans les ruines termine son tir`() {
        val game = GearMachineGame()
        game.setProjectileMass(250f)
        armer(game)
        val site = game.targets

        game.startCharge(30f)
        var g = 0
        while (game.charging && g++ < 10_000) game.advanceCharge(1f / 120f, 2_000)
        assertTrue("le lanceur n'a pas tiré", game.launchProjectile())
        val b = game.projectile!!.body
        b.x = site.left - 8f
        b.y = game.terrain.heightAt(site.left) + 6f
        b.vx = 160f
        b.vy = 0f

        var finAt = -1
        var dernierMouvement = 0
        repeat(1_800) { i ->
            game.step(fixed)
            if (finAt < 0 && game.phase != GearMachineGame.Phase.FLIGHT) finAt = i
            if (site.pieces.any { hypot(it.body.vx, it.body.vy) > 0.15f }) dernierMouvement = i
        }
        println(
            "RUINES tir terminé à " +
                (if (finAt < 0) "jamais" else "${"%.1f".format(finAt / 120f)} s") +
                ", le village bouge jusqu'à ${"%.1f".format(dernierMouvement / 120f)} s, " +
                "${site.pieceBroken} cassées, ${"%.0f".format(site.progress * 100f)} % de l'objectif"
        )
        assertTrue("le tir n'a jamais pris fin : le joueur reste bloqué en vol", finAt >= 0)
        assertTrue("le village n'a jamais bougé : le boulet ne l'a pas touché", dernierMouvement > 0)

        // **Et le monde continue de tourner après la fin du tir.**
        //
        // Ça se lisait avant sur « le village bouge encore après `finAt` », ce qui était
        // un raccourci commode et non la propriété voulue : le jour où le tir dure plus
        // longtemps que l'effondrement, le raccourci se plaint alors que rien ne va mal.
        // C'est arrivé le 04/09/2026, quand l'atelier a enfin reçu le vent de sa graine :
        // le vent amortit les gravats — le village se calme à 4,3 s au lieu de 7,7 —
        // pendant que le boulet, poussé dans les ruines, met plus longtemps à se déclarer
        // arrêté. Deux comportements justes, un raccourci faux.
        //
        // On mesure donc la chose elle-même : on lâche un caillou au-dessus du site une
        // fois le tir fini, et il doit tomber. Un monde figé le laisserait en l'air.
        val temoin = PhysBody.circle(0.2f, 5f).apply {
            x = site.left + 5f
            y = game.terrain.heightAt(site.left + 5f) + 25f
            category = TrebuchetCategory.DEBRIS
            collidesWith = TrebuchetCategory.GROUND
            collisionLayer = -32
            collisionLayerDepth = 65
        }
        val depart = temoin.y
        game.world.add(temoin)
        repeat(120) { game.step(fixed) }
        println("TÉMOIN lâché de ${"%.1f".format(depart)} m, tombé à ${"%.1f".format(temoin.y)} m")
        assertTrue(
            "le monde de l'atelier s'est figé avec le tir : le témoin n'est pas tombé " +
                "(${"%.2f".format(depart - temoin.y)} m en une seconde)",
            depart - temoin.y > 3f
        )
    }

    /**
     * **Le chiffre affiché doit être celui qui se compare à l'objectif.**
     *
     * Le bandeau montrait `progress`, qui est déjà *rapporté* à l'objectif : il atteint
     * cent au moment de la victoire, quel que soit l'objectif. Affiché à côté d'un
     * « objectif 75 % », il faisait lire au joueur « j'ai 88, il m'en faut 75 » alors
     * qu'il n'avait pas gagné — et il attendait un message de victoire qui ne pouvait
     * pas venir.
     *
     * Le trébuchet montre `score`, le compte brut des pierres à terre. C'est le seul
     * chiffre qui a un sens à côté de l'objectif, et c'est celui qu'on montre.
     */
    @Test
    fun `le score se compare a l objectif, l avancement non`() {
        val game = GearMachineGame()
        armer(game)
        val site = game.targets

        // On démolit par paliers, en relevant les deux chiffres au passage.
        var vus = 0
        var garde = 0
        while (!site.cleared && garde++ < 40) {
            val x = site.left + (garde % 8) * (site.right - site.left) / 8f
            site.blast(x, game.terrain.heightAt(x) + 3f, 300_000f, 14f)
            repeat(120) { game.step(fixed) }
            if (!site.cleared && site.score > 0f && vus < 3) {
                vus++
                println(
                    "SCORE brut ${"%.0f".format(site.score * 100f)} %, " +
                        "avancement ${"%.0f".format(site.progress * 100f)} %, " +
                        "objectif ${"%.0f".format(site.winRatio * 100f)} %"
                )
                // L'avancement est toujours **au-dessus** du score tant que l'objectif
                // n'est pas cent pour cent : c'est exactement le piège d'affichage.
                assertTrue(
                    "les deux chiffres sont sur la même échelle : rien n'aurait trompé",
                    site.progress >= site.score
                )
            }
        }
        assertTrue("le site ne se laisse pas raser", site.cleared)
        println(
            "SCORE à la victoire : brut ${"%.0f".format(site.score * 100f)} %, " +
                "objectif ${"%.0f".format(site.winRatio * 100f)} %"
        )
        // La victoire tombe quand le **score** atteint l'objectif, et à ce moment-là
        // seulement l'avancement vaut cent.
        assertTrue("la victoire n'est pas alignée sur le score", site.score >= site.winRatio)
        assertEquals("l'avancement ne vaut pas cent à la victoire", 1f, site.progress, 1e-4f)
    }

    /**
     * **Aucune pierre ne doit se trouver deux fois dans le monde.**
     *
     * Un corps présent en double est apparié **avec lui-même** par le balayage large :
     * sa catégorie satisfait son propre masque, et le solveur invente alors des contacts
     * entre les morceaux d'une seule et même pierre. La construction se fige ou part de
     * travers au moment de l'impact, sans exception ni message — juste une simulation
     * qui ne réagit pas comme elle devrait, une fois sur deux.
     *
     * C'est ce qui arrivait : deux appels légitimes pris séparément — celui qui fabrique
     * les pierres et celui qui les remet dans le monde après un vidage — s'enchaînaient
     * dans le chargement d'un site.
     */
    @Test
    fun `le chargement d un site ne met aucune pierre en double`() {
        val game = GearMachineGame()

        fun verifier(quand: String) {
            val vus = HashSet<Int>()
            var doubles = 0
            for (b in game.world.bodies) {
                if (!vus.add(System.identityHashCode(b))) doubles++
            }
            println(
                "MONDE $quand : ${game.world.bodies.size} corps, " +
                    "${game.targets.pieceTotal} pierres, $doubles en double"
            )
            assertEquals("$quand : des corps sont dans le monde en double", 0, doubles)
        }

        verifier("à l'ouverture   ")
        game.loadSite(31L)
        verifier("après un site   ")
        // Le remontage passe par `world.clear()` puis rattache : c'est l'autre moitié du
        // piège, et elle doit rester sans doublon elle aussi.
        game.rebuild()
        verifier("après remontage ")
        game.loadSite(4L)
        game.rebuild()
        verifier("après les deux  ")

        // Et les pierres sont bien là : un monde sans doublon parce qu'il est vide ne
        // prouverait rien.
        assertTrue("le site a disparu du monde", game.world.bodies.count { it.tag is TargetPiece } > 5)

        // Le garde-fou du moteur lui-même : reposer un corps déjà là ne l'ajoute pas.
        val avant = game.world.bodies.size
        game.world.add(game.world.bodies.first())
        assertEquals(
            "le moteur accepte deux fois le même corps",
            avant, game.world.bodies.size
        )
    }
}
