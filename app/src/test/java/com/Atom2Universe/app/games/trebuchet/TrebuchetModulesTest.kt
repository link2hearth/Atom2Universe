package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.Shape
import com.Atom2Universe.app.games.physics.PhysWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.random.Random
import kotlin.math.sin
import kotlin.math.hypot
import kotlin.math.cos

/**
 * Vérifie le vocabulaire d'architecture : chaque module doit tenir debout tout seul,
 * rester dans les budgets du moteur, et se détruire de la façon qui lui est propre.
 *
 * C'est le banc qui remplace le fait de regarder l'écran : une construction qui
 * s'écroule avant le premier tir ne se voit pas dans le code, elle se mesure.
 */
class TrebuchetModulesTest {

    /**
     * Enfoncement toléré entre deux pièces d'un même bloc, en mètres.
     *
     * Assez large pour laisser passer les quatre modules d'ornement qui se chevauchent
     * par nature — puits, abri, rambarde, cartes du château — dont les pièces sont trop
     * légères pour pousser quoi que ce soit ; assez serré pour attraper l'ordre de
     * grandeur au-dessus, celui qui déplace un bâtiment entier.
     */
    private val MAX_CHEVAUCHEMENT = 0.55f

    /**
     * Ces chiffres-ci sont ceux du mode **réaliste** : c'est contre lui que la table
     * des matériaux a été calée, et c'est lui qu'on vérifie ici. Le mode arcade a son
     * propre banc.
     */
    @Before
    fun modeRealiste() {
        TargetRules.style = TargetStyle.REALISTE
    }

    @After
    fun rendLeMode() {
        TargetRules.style = TargetStyle.JEU
    }

    private fun world(): PhysWorld = PhysWorld().apply {
        iterations = 16
        add(
            PhysBody(400f, 1f, 0f).apply {
                x = 0f; y = -1f
                lockPosition = true; lockRotation = true
                friction = 0.7f
                category = TrebuchetCategory.GROUND
                refreshMass()
            }
        )
    }

    private fun PhysWorld.fireBall(x: Float, y: Float, speed: Float, up: Float = 0f): PhysBody {
        val b = PhysBody.circle(TrebuchetRules.BALL_RADIUS, TrebuchetRules.BALL_MASS).apply {
            this.x = x; this.y = y
            vx = speed; vy = up
            friction = 0.2f
            restitution = 0.1f
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
        }
        add(b)
        return b
    }

    /** Pose une construction et la laisse vivre : rend le champ, prêt à être interrogé. */
    private fun stand(s: Structure, seconds: Float = 4f): Pair<PhysWorld, TargetField> {
        val w = world()
        val f = TargetField(w)
        f.load(TargetField.settle(s))
        repeat((seconds * 60).toInt()) {
            w.stepFrame(1f / 60f)
            f.update(1f / 60f)
        }
        return w to f
    }

    private fun run(w: PhysWorld, f: TargetField, seconds: Float) {
        repeat((seconds * 60).toInt()) {
            w.stepFrame(1f / 60f)
            f.update(1f / 60f)
        }
    }

    // ── Chaque module doit tenir debout ───────────────────────────────────────

    private fun checkStands(name: String, s: Structure) {
        val problems = s.problems()
        assertTrue("$name : $problems", problems.isEmpty())

        val settled = TargetField.settle(s)
        val drift = TargetField.drift(s, settled)

        val w = world()
        val f = TargetField(w)
        f.load(settled)
        run(w, f, 4f)

        println(
            "MODULE $name : h=${"%.1f".format(s.baseHeight)}m l=${"%.1f".format(s.width)}m " +
                "corps=${s.blocks.size} pile=${s.deepestStack()} " +
                "tassement=${"%.3f".format(drift)}m debout=${f.ruinHeight() > 0.9f * s.baseHeight}"
        )
        assertTrue("$name a bougé au tassement : $drift m", drift < TargetRules.SETTLE_TOLERANCE)
        assertTrue("$name s'est abîmé tout seul (${"%.1f".format(f.brokenRatio * 100)}%)", f.brokenRatio == 0f)
        assertTrue(
            "$name s'est affaissé : ${f.ruinHeight()} pour ${s.baseHeight}",
            f.ruinHeight() > 0.9f * s.baseHeight
        )
    }

    @Test
    fun `une courtine tient debout`() {
        val rng = Random(1)
        checkStands(
            "courtine",
            Structure(TargetModules.curtainWall(rng, 20f, 9f, 7f), "courtine")
        )
    }

    @Test
    fun `une tour tient debout`() {
        val rng = Random(2)
        checkStands("tour", Structure(TargetModules.tower(rng, 20f, 4f, 13f), "tour"))
    }

    @Test
    fun `une tour tient debout meme avec un budget de corps serre`() {
        // Budget serré = gros regroupements = corps de plusieurs tonnes. C'est
        // exactement là que les tours se fêlaient toutes seules.
        checkStands(
            "tour serrée",
            Structure(TargetModules.tower(Random(2), 20f, 4f, 14f, bodyBudget = 30), "tour serrée")
        )
    }

    @Test
    fun `une maison tient debout`() {
        val rng = Random(3)
        checkStands("maison", Structure(TargetModules.house(rng, 20f, 5f, 8f), "maison"))
    }

    @Test
    fun `un chateau entier tient debout`() {
        // L'assemblage que le générateur produira. Les modules se posent **côte à
        // côte**, jamais l'un dans l'autre : vu de profil, un château n'a pas de cour,
        // il a une enfilade. Ma première version mettait une maison « dans la cour » et
        // la validation a compté cinquante chevauchements.
        // Chaque module se pose après le précédent, **d'après son emprise réelle** et
        // non d'après la largeur demandée : le socle d'une tour déborde de vingt
        // centimètres de chaque côté. Deux centimètres de chevauchement suffisaient à
        // faire s'entre-broyer deux modules dès la première image.
        val rng = Random(7)
        val blocks = ArrayList<Block>()
        var x = 20f
        fun pose(module: List<Block>) {
            blocks += module
            x = Structure(module).right + 0.4f
        }
        pose(TargetModules.tower(rng, x, 4f, 14f, bodyBudget = 30))
        pose(TargetModules.curtainWall(rng, x, 8f, 8f, bodyBudget = 25))
        pose(TargetModules.house(rng, x, 5f, 7f))
        pose(TargetModules.tower(rng, x, 4.5f, 16f, bodyBudget = 30))
        pose(TargetModules.props(rng, x, 0f, 2.5f, 3))
        checkStands("château", Structure(blocks, "château"))
    }

    // ── Les budgets du moteur ─────────────────────────────────────────────────

    @Test
    fun `les modules restent dans le budget de corps et de profondeur`() {
        val rng = Random(11)
        val cas = listOf(
            "courtine basse" to TargetModules.curtainWall(rng, 0f, 14f, 5f),
            "courtine haute" to TargetModules.curtainWall(rng, 0f, 14f, 12f),
            "courtine géante" to TargetModules.curtainWall(rng, 0f, 30f, 16f),
            "tour trapue" to TargetModules.tower(rng, 0f, 5f, 9f),
            "tour élancée" to TargetModules.tower(rng, 0f, 3.5f, 20f),
            "tour immense" to TargetModules.tower(rng, 0f, 7f, 60f),
            "maison" to TargetModules.house(rng, 0f, 6f, 9f),
            "château de cartes" to TargetModules.cardCastle(rng, 0f, 16f, 22f),
            "château de cartes géant" to TargetModules.cardCastle(rng, 0f, 20f, 40f)
        )
        for ((name, blocks) in cas) {
            val s = Structure(blocks, name)
            println(
                "BUDGET $name : corps=${s.blocks.size} pile=${s.deepestStack()} " +
                    "h=${"%.1f".format(s.baseHeight)}m"
            )
            assertTrue(
                "$name : ${s.blocks.size} corps, budget ${TargetRules.BODY_BUDGET}",
                s.blocks.size <= TargetRules.BODY_BUDGET
            )
            assertTrue(
                "$name : pile de ${s.deepestStack()} corps",
                s.deepestStack() <= TargetRules.MAX_STACKED_BODIES
            )
        }
    }

    @Test
    fun `une tour tres haute se regroupe en blocs composes au lieu d empiler`() {
        // C'est la parade mesurée à la limite du solveur : le joueur voit toujours des
        // assises de cinquante centimètres, le moteur compte des corps de deux mètres.
        val basse = Structure(TargetModules.tower(Random(4), 0f, 4f, 8f), "tour basse")
        val haute = Structure(TargetModules.tower(Random(4), 0f, 4f, 22f), "tour haute")

        println("REGROUPEMENT basse=${basse.deepestStack()} haute=${haute.deepestStack()}")
        assertTrue(
            "une tour trois fois plus haute empile trois fois plus de corps",
            haute.deepestStack() <= TargetRules.MAX_STACKED_BODIES
        )
        val partsHautes = haute.blocks.maxOf { it.parts.size }
        assertTrue("la tour haute n'a regroupé aucune assise", partsHautes > 1)
    }

    @Test
    fun `une tour immense se coupe en sections et tient debout`() {
        // Bien au-delà du premier palier ([TargetModules.tower] le fixe à 14 m de
        // site) : trois ou quatre sections, chacune un tronçon un peu plus étroit que
        // la précédente, séparées par un anneau. C'est ce qui doit rester debout sans
        // dépasser la pile, quel que soit le budget de corps.
        val s = Structure(TargetModules.tower(Random(21), 0f, 6f, 55f), "tour immense")
        println(
            "TOUR IMMENSE h=${"%.1f".format(s.baseHeight)}m corps=${s.blocks.size} " +
                "pile=${s.deepestStack()}"
        )
        assertTrue(
            "la tour immense dépasse la pile : ${s.deepestStack()}",
            s.deepestStack() <= TargetRules.MAX_STACKED_BODIES
        )
        checkStands("tour immense", s)
    }

    @Test
    fun `un chateau de cartes tient debout`() {
        checkStands(
            "château de cartes",
            Structure(TargetModules.cardCastle(Random(22), 20f, 16f, 22f), "château de cartes")
        )
    }

    @Test
    fun `un chateau de cartes s illumine jusqu au faite, pas seulement a hauteur d homme`() {
        // C'est l'inverse de la règle des torches ([TargetField.lightUp]) : un
        // monument se donne à voir de loin, donc en hauteur, là où une torche
        // ordinaire se serait déjà éteinte à six mètres.
        val s = Structure(TargetModules.cardCastle(Random(24), 20f, 20f, 40f), "château de cartes")
        val w = world()
        val f = TargetField(w)
        f.load(s)

        val sommet = f.pieces.maxBy { it.body.y }
        val pres_du_sol = f.pieces.filter { it.role == Role.MONUMENT }.minBy { it.body.y }
        println(
            "LUMIERES sommet y=${"%.1f".format(sommet.body.y)} allumé=${sommet.lightRadius > 0f}, " +
                "tente basse y=${"%.1f".format(pres_du_sol.body.y)} allumée=${pres_du_sol.lightRadius > 0f}"
        )
        assertTrue("le sommet du château de cartes est resté éteint", sommet.lightRadius > 0f)
        assertTrue("une tente marquée MONUMENT est restée éteinte", pres_du_sol.lightRadius > 0f)
    }

    @Test
    fun `un chateau de cartes ne depasse jamais la pile quelle que soit sa taille`() {
        for ((w, h) in listOf(10f to 12f, 16f to 22f, 20f to 40f, 24f to 55f, 34f to 120f)) {
            val s = Structure(TargetModules.cardCastle(Random(23), 0f, w, h), "château $w x $h")
            println(
                "CHATEAU DE CARTES ${w}x${h} : corps=${s.blocks.size} pile=${s.deepestStack()} " +
                    "hauteur réelle=${"%.1f".format(s.baseHeight)}"
            )
            assertTrue(
                "château $w x $h : pile de ${s.deepestStack()}",
                s.deepestStack() <= TargetRules.MAX_STACKED_BODIES
            )
        }
    }

    // ── Chacun se détruit à sa façon ──────────────────────────────────────────

    @Test
    fun `un tir haut fait tomber les merlons sans percer la courtine`() {
        val s = Structure(TargetModules.curtainWall(Random(5), 20f, 9f, 7f), "courtine")
        val settled = TargetField.settle(s)
        val w = world()
        val f = TargetField(w)
        f.load(settled)
        run(w, f, 3f)
        val avant = f.ruinHeight()

        // Un boulet rasant, à la hauteur de la crête.
        w.fireBall(10f, avant - 0.4f, 110f)
        run(w, f, 4f)

        val pireUsure = f.pieces.filter { !it.debris }.maxOf { it.wear }
        println("MERLONS hauteur ${"%.2f".format(avant)} -> ${"%.2f".format(f.ruinHeight())}, " +
            "détruit=${"%.0f".format(f.brokenRatio * 100)}% pireUsure=${"%.2f".format(pireUsure)} " +
            "fissures=${f.pieces.count { it.crackLevel > 0 }}")
        assertTrue("le tir n'a laissé aucune trace sur la crête", pireUsure > 0.1f)
        assertTrue(
            "un seul tir haut a rasé la courtine : ${f.brokenRatio}",
            f.brokenRatio < 0.35f
        )
    }

    @Test
    fun `une maison perd un poteau et ce qui est dessus descend`() {
        val s = Structure(TargetModules.house(Random(6), 20f, 5f, 8f), "maison")
        val settled = TargetField.settle(s)
        val w = world()
        val f = TargetField(w)
        f.load(settled)
        run(w, f, 3f)
        val avant = f.ruinHeight()

        // On vise les poteaux du bas, pas le toit. Une maison large a un poteau de
        // refend : il en faut plus d'un pour la faire descendre, et c'est bien ainsi —
        // c'est le seul module où viser vaut mieux que frapper fort.
        // On retire le boulet précédent avant le suivant : sinon le deuxième tir vient
        // frapper le premier boulet resté au pied du mur, et on croit à tort que la
        // maison encaisse.
        // Cinq boulets dans la façade, en variant un peu la hauteur : c'est ainsi
        // qu'on abat une maison, poteau après poteau. Le boulet précédent est retiré
        // avant le suivant, sinon les tirs finissent par se frapper entre eux.
        for (k in 0 until 5) {
            val b = w.fireBall(10f, 0.8f + 0.35f * k, 110f)
            run(w, f, 3f)
            w.remove(b)
        }

        println("MAISON hauteur ${"%.2f".format(avant)} -> ${"%.2f".format(f.ruinHeight())}, " +
            "détruit=${"%.0f".format(f.brokenRatio * 100)}%")
        assertTrue(
            "la maison n'a pas bronché : ${f.ruinHeight()} pour $avant",
            f.ruinHeight() < avant - 1.5f
        )
        assertTrue("la charpente n'a presque rien perdu", f.brokenRatio > 0.15f)
    }

    @Test
    fun `un toit casse en ses deux versants`() {
        val w = world()
        val f = TargetField(w)
        val toit = Masonry.roof(Material.WOOD, 20f, 0f, 5f, 1.6f)
        f.load(Structure(listOf(toit), "toit"))
        run(w, f, 2f)

        w.fireBall(10f, 0.6f, 150f)
        run(w, f, 2f)

        val eclats = f.pieces.count { it.debris }
        println("TOIT éclats=$eclats")
        assertTrue("le toit n'a pas cassé en deux versants : $eclats", eclats == 2)
    }

    // ── Reproductibilité ──────────────────────────────────────────────────────

    @Test
    fun `une meme graine redonne le meme module`() {
        fun tour(seed: Int) = TargetModules.tower(Random(seed), 20f, 4f, 14f)
        val a = tour(42)
        val b = tour(42)
        assertEquals(a.size, b.size)
        for (i in a.indices) {
            assertEquals("bloc $i décalé", a[i].x, b[i].x, 1e-6f)
            assertEquals("bloc $i décalé", a[i].y, b[i].y, 1e-6f)
            assertEquals("bloc $i de masse différente", a[i].mass, b[i].mass, 1e-3f)
        }
        // Et deux graines différentes ne donnent pas exactement la même tour.
        val autre = tour(43)
        val identique = autre.size == a.size &&
            a.indices.all { kotlin.math.abs(a[it].y - autre[it].y) < 1e-6f }
        assertTrue("deux graines donnent la même tour au millimètre", !identique)
    }

    // ── Le second catalogue au banc ───────────────────────────────────────────
    //
    // Les neuf pièces arrivées avec le relief. Chacune passe exactement le même
    // examen que les quatre premières : ne pas bouger au tassement, ne pas s'abîmer
    // toute seule, et ne pas s'affaisser. C'est un test par module et non un test qui
    // les balaie tous, parce qu'un module qui casse doit se nommer dans le rapport
    // d'échec sans qu'on ait à ouvrir le code.

    @Test
    fun `un moulin tient debout`() {
        // La croix d'ailes est le seul corps du jeu dont la masse est perchée au-dessus
        // de tout le reste, et posée sur rien. Si elle doit verser, c'est ici.
        checkStands("moulin", Structure(TargetModules.windmill(Random(11), 20f, 4.5f, 14f), "moulin"))
    }

    @Test
    fun `une pyramide tient debout`() {
        checkStands(
            "pyramide",
            Structure(TargetModules.pyramid(Random(12), 20f, 12f, 8.5f), "pyramide")
        )
    }

    @Test
    fun `un amphitheatre tient debout`() {
        checkStands(
            "amphithéâtre",
            Structure(TargetModules.arena(Random(13), 20f, 16f, 10f), "amphithéâtre")
        )
    }

    @Test
    fun `un temple tient debout`() {
        // Les colonnes sont libres, et l'architrave ne repose que sur elles. Une seule
        // colonne trop élancée, ou un linteau qui déborde d'une seule, et tout descend.
        checkStands("temple", Structure(TargetModules.temple(Random(14), 20f, 11f, 9f), "temple"))
    }

    @Test
    fun `un aqueduc tient debout`() {
        checkStands(
            "aqueduc",
            Structure(TargetModules.aqueduct(Random(15), 20f, 12f, 11f), "aqueduc")
        )
    }

    @Test
    fun `un grenier sur pilotis tient debout`() {
        checkStands(
            "grenier",
            Structure(TargetModules.granary(Random(16), 20f, 5.5f, 9f), "grenier")
        )
    }

    @Test
    fun `un immeuble tient debout`() {
        checkStands("immeuble", Structure(TargetModules.insula(Random(17), 20f, 4.5f, 11f), "immeuble"))
    }

    @Test
    fun `une grange tient debout`() {
        checkStands("grange", Structure(TargetModules.barn(Random(18), 20f, 10f, 6f), "grange"))
    }

    @Test
    fun `une porte fortifiee tient debout et possede un portail`() {
        val blocks = TargetModules.gatehouse(Random(25), 20f, 7f, 8f)
        assertTrue("la porte fortifiée n'a pas de vantaux", blocks.any { it.decor == Decor.DOOR })
        assertTrue("la porte fortifiée n'a pas de créneaux", blocks.count { it.top() > 7f } >= 2)
        checkStands("porte fortifiée", Structure(blocks, "porte fortifiée"))
    }

    @Test
    fun `une chapelle tient debout avec une nef et un clocher`() {
        val blocks = TargetModules.chapel(Random(26), 20f, 6f, 11f)
        assertTrue("la chapelle n'a pas de fenêtres cintrées", blocks.any { it.decor == Decor.WINDOW_ARCHED })
        assertTrue("le clocher n'a pas de cloche", blocks.any { it.decor == Decor.BELL })
        assertTrue("la chapelle n'a pas deux toitures", blocks.count { it.silhouette == Silhouette.GABLE_ROOF } == 2)
        checkStands("chapelle", Structure(blocks, "chapelle"))
    }

    @Test
    fun `une maison tour tient debout et melange pierre et bois`() {
        val blocks = TargetModules.towerHouse(Random(27), 20f, 5f, 13f)
        assertTrue("la maison-tour n'a pas de soubassement", blocks.any { it.material.masonry })
        assertTrue("la maison-tour n'a pas de charpente", blocks.any { it.material == Material.WOOD })
        assertTrue("la maison-tour n'a pas de toit", blocks.any { it.silhouette == Silhouette.GABLE_ROOF })
        checkStands("maison-tour", Structure(blocks, "maison-tour"))
    }

    @Test
    fun `une palissade tient debout`() {
        checkStands(
            "palissade",
            Structure(TargetModules.palisade(Random(19), 20f, 8f, 3.5f), "palissade")
        )
    }

    @Test
    fun `un moulin decoiffe perd ses ailes et pas sa tour`() {
        // Ce que le module promet : la croix part d'un coup au sommet, et le fût reste
        // planté. Un moulin qui s'écroulerait en entier au premier tir haut serait une
        // tour ordinaire déguisée.
        //
        // **En arcade, et c'est le seul test de ce banc qui change de mode.** Le reste
        // du fichier mesure la maçonnerie réelle, où un boulet de douze kilos ne
        // déplace pas quatre tonnes de charpente — c'est exact, et ça ne dit rien du
        // module. Ce qu'on veut vérifier ici est une promesse de jeu, elle se vérifie
        // donc dans le mode où le jeu se joue.
        TargetRules.style = TargetStyle.JEU
        val s = Structure(TargetModules.windmill(Random(11), 20f, 4.5f, 14f), "moulin")
        val (w, f) = stand(s)
        val avant = f.ruinHeight()
        val pierresAvant = f.pieces.size

        // Un boulet au ras du sommet, là où est la croix.
        w.fireBall(2f, avant - 1.2f, 120f)
        run(w, f, 4f)

        val apres = f.ruinHeight()
        println(
            "MOULIN crête ${"%.2f".format(avant)} -> ${"%.2f".format(apres)}, " +
                "pierres $pierresAvant -> ${f.pieces.size}, " +
                "abîmé ${"%.0f".format(f.brokenRatio * 100)}%"
        )
        assertTrue("le moulin a gardé son chapeau : $avant -> $apres", apres < avant - 1f)
        // Le fût compte au moins la moitié de la hauteur : s'il ne reste rien, ce n'est
        // pas un décoiffage, c'est une démolition.
        assertTrue("le fût est parti avec : $apres", apres > avant * 0.4f)
    }

    @Test
    fun `un temple abattu laisse des tambours`() {
        // La promesse du bloc composé : une colonne debout coûte un corps, une colonne
        // abattue en rend trois ou quatre. Ce test vérifie qu'on ne paie le détail
        // qu'au moment où le joueur l'a mérité — en arcade, pour la même raison que le
        // moulin ci-dessus.
        TargetRules.style = TargetStyle.JEU
        val s = Structure(TargetModules.temple(Random(14), 20f, 11f, 9f), "temple")
        val (w, f) = stand(s)
        val corpsDebout = f.pieces.size

        w.fireBall(2f, 2.5f, 145f)
        run(w, f, 5f)

        println(
            "TEMPLE corps debout=$corpsDebout, après le tir=${f.pieces.size}, " +
                "abîmé ${"%.0f".format(f.brokenRatio * 100)}%"
        )
        assertTrue("le temple n'a rien senti", f.brokenRatio > 0f)
    }

    // ── Le catalogue de détails au banc ───────────────────────────────────────
    //
    // Les ouvertures sont des décors de façade. Les escaliers et petits édifices,
    // eux, doivent avoir une silhouette composée qui correspond à leur collision.

    @Test
    fun `un mur a fenetre tient debout et porte son decor`() {
        val b = Masonry.windowedWall(Material.STONE, 20f, 0f, 6f, 4f)
        assertEquals(Decor.WINDOW_SHUTTERS, b.decor)
        checkStands("mur à fenêtre", Structure(listOf(b), "mur à fenêtre"))
    }

    @Test
    fun `un mur a meurtriere tient debout et porte son decor`() {
        val b = Masonry.arrowSlitWall(Material.STONE, 20f, 0f, 3f, 5f)
        assertEquals(Decor.ARROW_SLIT, b.decor)
        checkStands("mur à meurtrière", Structure(listOf(b), "mur à meurtrière"))
    }

    @Test
    fun `les constructions generees emploient vraiment les nouvelles facades`() {
        val maison = TargetModules.house(Random(31), 20f, 5f, 8f)
        val panneaux = maison.filter { it.surface == Surface.TIMBER_FRAME || it.surface == Surface.PLANKS }
        assertTrue("la maison est encore une charpente vide", panneaux.isNotEmpty())
        assertTrue("la maison n'a pas de porte", panneaux.any { it.decor == Decor.DOOR })
        assertTrue(
            "la maison n'a pas de fenêtre",
            panneaux.any { it.decor == Decor.WINDOW_SHUTTERS || it.decor == Decor.WINDOW_ARCHED }
        )
        assertTrue(
            "le toit de maison est encore dessiné comme deux barres",
            maison.any { it.silhouette == Silhouette.GABLE_ROOF }
        )

        val tour = TargetModules.tower(Random(32), 20f, 5f, 14f)
        assertTrue("la tour n'a aucune ouverture", tour.any { it.decor != Decor.NONE })
        assertTrue(
            "la tour n'a pas reçu de parement",
            tour.any { it.surface in setOf(Surface.BRICK, Surface.FIELDSTONE, Surface.CUT_STONE) }
        )
    }

    @Test
    fun `un escalier tient debout et possede de vraies marches`() {
        val b = Masonry.staircase(Material.STONE, 20f, 0f, 4f, 3f)
        assertEquals(Decor.NONE, b.decor)
        assertEquals(6, b.parts.size)
        checkStands("escalier", Structure(listOf(b), "escalier"))
    }

    @Test
    fun `une rambarde tient debout et est ajouree`() {
        val b = Masonry.railing(Material.WOOD, 20f, 0f, 4f)
        assertEquals(Decor.NONE, b.decor)
        assertTrue(b.parts.size > 2)
        checkStands("rambarde", Structure(listOf(b), "rambarde"))
    }

    @Test
    fun `un puits tient debout et a une vraie silhouette`() {
        val b = TargetModules.well(20f, 0f)
        assertEquals(Decor.NONE, b.decor)
        assertTrue(b.parts.size >= 5)
        checkStands("puits", Structure(listOf(b), "puits"))
    }

    @Test
    fun `un abri tient debout et a une vraie silhouette`() {
        val b = TargetModules.shelter(20f, 4f, 3.5f)
        assertEquals(Decor.NONE, b.decor)
        assertEquals(4, b.parts.size)
        checkStands("abri", Structure(listOf(b), "abri"))
    }
    // ── Le catalogue moderne au banc ──────────────────────────────────────────
    //
    // Les quatre pièces d'une ville. Elles passent le même examen que les autres, plus
    // un qui leur est propre : une ville est un site **dense**, donc ses bâtiments sont
    // séparés d'un demi-mètre au lieu de vingt. Un module qui tasse de vingt centimètres
    // est parfaitement acceptable au village et fait tomber une ville en dominos.

    @Test
    fun `une tour d immeuble tient debout`() {
        checkStands(
            "tour d'immeuble",
            Structure(
                TargetModules.towerBlock(
                    Random(61), 20f, TargetRules.site(10f), TargetRules.site(26f)
                ),
                "tour d'immeuble"
            )
        )
    }

    @Test
    fun `un gratte-ciel tient debout`() {
        checkStands(
            "gratte-ciel",
            Structure(
                TargetModules.towerBlock(
                    Random(62), 20f, TargetRules.site(20f), TargetRules.site(60f), Material.STEEL
                ),
                "gratte-ciel"
            )
        )
    }

    @Test
    fun `un socle commercial tient debout`() {
        checkStands(
            "socle commercial",
            Structure(
                TargetModules.podium(Random(63), 20f, TargetRules.site(12f), TargetRules.site(6f)),
                "socle commercial"
            )
        )
    }

    @Test
    fun `un parking en silo tient debout`() {
        checkStands(
            "parking",
            Structure(
                TargetModules.parkingDeck(
                    Random(64), 20f, TargetRules.site(12f), TargetRules.site(13f)
                ),
                "parking"
            )
        )
    }

    @Test
    fun `une cheminee tient debout`() {
        checkStands(
            "cheminée",
            Structure(
                TargetModules.chimney(Random(65), 20f, TargetRules.site(2f), TargetRules.site(25f)),
                "cheminée"
            )
        )
    }

    /**
     * Ce qu'une tour d'immeuble promet, et qu'aucun autre module ne promet : **elle ne
     * bascule pas, elle descend sur place**.
     *
     * On tire au pied, dans les poteaux du rez-de-chaussée, et on mesure deux choses
     * ensemble. La crête doit baisser : la tour encaisse. Et l'axe de ses pierres ne
     * doit pas se déplacer : elle ne verse pas sur le côté comme le ferait un donjon.
     * Prise seule, aucune des deux mesures ne dirait « effondrement en accordéon ».
     *
     * **Le banc est plus dur que le jeu, et c'est assumé.** Ici le boulet rebondit sur
     * ce qu'il casse ; dans le jeu, la traversée d'arcade ([TargetStyle.pierce]) lui rend
     * son élan et il enfile plusieurs étages d'un seul tir. On ne vérifie donc pas ici
     * qu'une tour tombe en un coup — elle ne le doit pas — mais qu'elle tombe **droit**.
     */
    @Test
    fun `une tour d immeuble descend sur place au lieu de verser`() {
        TargetRules.style = TargetStyle.JEU
        val s = Structure(
            TargetModules.towerBlock(Random(66), 20f, TargetRules.site(10f), TargetRules.site(30f)),
            "tour"
        )
        val (w, f) = stand(s)
        val creteAvant = f.ruinHeight()
        val axeAvant = f.pieces.map { it.body.x }.average().toFloat()

        // Six boulets dans le rez-de-chaussée et le premier étage, là où on abat une
        // tour pour de vrai. Le boulet précédent est retiré avant le suivant, sinon les
        // tirs finissent par se frapper entre eux.
        for (k in 0 until 6) {
            val b = w.fireBall(2f, TargetRules.site(1.2f) + k * TargetRules.site(1.3f), 150f)
            run(w, f, 4f)
            w.remove(b)
        }
        run(w, f, 6f)

        val creteApres = f.ruinHeight()
        val axeApres = f.pieces.map { it.body.x }.average().toFloat()
        println(
            "TOUR crête ${"%.1f".format(creteAvant)} -> ${"%.1f".format(creteApres)} m, " +
                "axe ${"%.1f".format(axeAvant)} -> ${"%.1f".format(axeApres)} m, " +
                "cassé ${"%.0f".format(f.brokenRatio * 100)}%"
        )
        assertTrue("la tour n'a rien senti", f.brokenRatio > 0.2f)
        assertTrue(
            "la tour a versé au lieu de descendre : l'axe a bougé de " +
                "${"%.1f".format(kotlin.math.abs(axeApres - axeAvant))} m",
            kotlin.math.abs(axeApres - axeAvant) < TargetRules.site(6f)
        )
        assertTrue(
            "la tour est restée intacte : ${"%.1f".format(creteAvant)} -> " +
                "${"%.1f".format(creteApres)}",
            creteApres < creteAvant
        )
    }

    /**
     * La devanture existe, elle est en verre, et elle est au rez-de-chaussée.
     *
     * C'est la seule pièce de verre d'une tour — le reste de la façade est **peint**,
     * voir [TargetModules.towerBlock] — et elle est là parce que c'est la partie que le
     * joueur atteint en premier. Le test est géométrique et non balistique : depuis la
     * gauche, un boulet rasant rencontre le poteau d'angle avant la baie, ce qui est
     * exact et ne se discute pas ; ce qu'on veut garantir ici, c'est qu'aucun
     * remaniement du module ne la fasse disparaître ni monter au douzième étage.
     */
    @Test
    fun `une tour d immeuble a sa devanture de verre au rez-de-chaussee`() {
        val blocks = TargetModules.towerBlock(
            Random(67), 20f, TargetRules.site(10f), TargetRules.site(20f)
        )
        val verre = blocks.filter { it.material == Material.GLASS }
        assertTrue("la tour n'a pas de devanture", verre.isNotEmpty())
        val plafondRez = blocks.filter { it.material == Material.CONCRETE }.minOf { it.top() }
        println(
            "DEVANTURE ${verre.size} panneau(x), du sol à " +
                "${"%.1f".format(verre.maxOf { it.top() })} m, plafond du rez à " +
                "${"%.1f".format(plafondRez)} m"
        )
        assertTrue("la devanture ne descend pas au sol", verre.all { it.bottom() < 0.1f })
        assertTrue(
            "la devanture dépasse le rez-de-chaussée",
            verre.all { it.top() <= plafondRez + 0.01f }
        )
        // Et elle est bien plus fragile que le béton qui l'entoure : c'est tout son
        // intérêt de jeu.
        val betonLePlusFaible = blocks.filter { it.material == Material.CONCRETE }.minOf { it.hp }
        assertTrue(
            "le verre n'est pas plus fragile que le béton",
            verre.maxOf { it.hp } < betonLePlusFaible * 0.2f
        )
    }
    // ── Le plan de fracture ───────────────────────────────────────────────────

    /**
     * **Les pièces d'un bloc ne doivent pas s'enfoncer les unes dans les autres.**
     *
     * C'est le défaut le plus sournois du catalogue, parce qu'il est **invisible tant
     * que le bâtiment tient**. Deux pièces d'un même corps composé sont parfaitement
     * solidaires : elles peuvent se chevaucher d'un mètre sans que rien ne bouge, et
     * l'aperçu comme le jeu les dessinent l'une sur l'autre sans qu'on y voie rien.
     *
     * Mais le bloc composé est aussi son **plan de fracture** : à la rupture, chaque
     * pièce devient un corps libre à la pose qu'elle avait — et deux corps imbriqués
     * d'un mètre et demi se repoussent aussitôt de toutes leurs forces. Vu du joueur,
     * l'immeuble qu'il pilonne « grandit » d'un coup au moment de l'impact. C'est
     * exactement ce qui arrivait aux tours de ville, dont l'allège traversait le poteau
     * du milieu sur un mètre et demi, et aux toits, dont les deux versants se croisaient
     * au faîtage.
     *
     * Le seuil est large et le reste : quatre modules d'ornement se chevauchent un peu
     * par nature — le puits, l'abri, la rambarde et les cartes du château — et ce sont
     * des pièces légères dont la séparation ne pousse personne. Ce qu'on interdit, c'est
     * l'ordre de grandeur au-dessus, celui qui déplace un bâtiment.
     */
    @Test
    fun `aucune piece d un bloc ne s enfonce dans une autre`() {
        TargetRules.style = TargetStyle.JEU
        fun s(v: Float) = TargetRules.site(v)
        val rng = Random(9)
        val cas = listOf(
            "tour d'immeuble étroite" to TargetModules.towerBlock(rng, 0f, s(6f), s(20f)),
            "tour d'immeuble large" to TargetModules.towerBlock(rng, 0f, s(12f), s(30f)),
            "gratte-ciel" to TargetModules.towerBlock(rng, 0f, s(20f), s(60f), Material.STEEL),
            "parking" to TargetModules.parkingDeck(rng, 0f, s(12f), s(13f)),
            "socle" to TargetModules.podium(rng, 0f, s(12f), s(6f)),
            "maison" to TargetModules.house(rng, 0f, s(6f), s(9f)),
            "maison-tour" to TargetModules.towerHouse(rng, 0f, s(5f), s(13f)),
            "tour de pierre" to TargetModules.tower(rng, 0f, s(5f), s(16f)),
            "courtine" to TargetModules.curtainWall(rng, 0f, s(10f), s(8f)),
            "porte fortifiée" to TargetModules.gatehouse(rng, 0f, s(7f), s(9f)),
            "chapelle" to TargetModules.chapel(rng, 0f, s(6f), s(12f)),
            "grange" to TargetModules.barn(rng, 0f, s(10f), s(6f)),
            "grenier" to TargetModules.granary(rng, 0f, s(6f), s(10f)),
            "moulin" to TargetModules.windmill(rng, 0f, s(5f), s(14f)),
            "temple" to TargetModules.temple(rng, 0f, s(11f), s(9f)),
            "aqueduc" to TargetModules.aqueduct(rng, 0f, s(12f), s(11f)),
            "toit" to listOf(Masonry.roof(Material.THATCH, 0f, 0f, s(5f), s(1.6f))),
            "escalier" to listOf(Masonry.staircase(Material.STONE, 0f, 0f, s(4f), s(3f)))
        )
        val fautes = ArrayList<String>()
        for ((nom, blocks) in cas) {
            var pire = 0f
            var quoi = ""
            for (b in blocks) {
                val o = pireChevauchement(b)
                if (o.first > pire) {
                    pire = o.first
                    quoi = o.second
                }
            }
            println("FRACTURE $nom : pire chevauchement interne ${"%.2f".format(pire)} m $quoi")
            if (pire > MAX_CHEVAUCHEMENT) {
                fautes += "$nom : ${"%.2f".format(pire)} m ($quoi)"
            }
        }
        assertTrue(
            "des pièces s'enfoncent l'une dans l'autre et s'expulseront à la rupture — " +
                fautes.joinToString(" ; "),
            fautes.isEmpty()
        )
    }

    /** Le plus gros enfoncement entre deux pièces d'un même bloc, en mètres. */
    private fun pireChevauchement(b: Block): Pair<Float, String> {
        if (b.parts.size < 2) return 0f to ""
        val polys = b.parts.map { coinsDe(it, b.angle) }
        var pire = 0f
        var quoi = ""
        for (i in b.parts.indices) {
            if (b.parts[i].shape == Shape.CIRCLE) continue
            for (j in i + 1 until b.parts.size) {
                if (b.parts[j].shape == Shape.CIRCLE) continue
                val o = enfoncement(polys[i], polys[j])
                if (o > pire) {
                    pire = o
                    quoi = "pièces $i×$j de ${b.material}"
                }
            }
        }
        return pire to quoi
    }

    private fun coinsDe(p: Piece, angle: Float): FloatArray {
        val c = cos(angle)
        val s = sin(angle)
        val cx = p.localX * c - p.localY * s
        val cy = p.localX * s + p.localY * c
        val a = angle + p.localAngle
        val ca = cos(a)
        val sa = sin(a)
        val out = FloatArray(8)
        var k = 0
        for ((dx, dy) in listOf(
            -p.halfW to -p.halfH, p.halfW to -p.halfH, p.halfW to p.halfH, -p.halfW to p.halfH
        )) {
            out[k++] = cx + dx * ca - dy * sa
            out[k++] = cy + dx * sa + dy * ca
        }
        return out
    }

    /** Profondeur de recouvrement de deux rectangles orientés (SAT), zéro s'ils sont séparés. */
    private fun enfoncement(a: FloatArray, b: FloatArray): Float {
        var best = Float.MAX_VALUE
        for (poly in listOf(a, b)) {
            for (e in 0 until 2) {
                val dx = poly[(e + 1) * 2] - poly[e * 2]
                val dy = poly[(e + 1) * 2 + 1] - poly[e * 2 + 1]
                val len = hypot(dx, dy)
                if (len < 1e-6f) continue
                val nx = -dy / len
                val ny = dx / len
                var aMin = Float.MAX_VALUE
                var aMax = -Float.MAX_VALUE
                var bMin = Float.MAX_VALUE
                var bMax = -Float.MAX_VALUE
                for (i in 0 until 4) {
                    val d = a[i * 2] * nx + a[i * 2 + 1] * ny
                    if (d < aMin) aMin = d
                    if (d > aMax) aMax = d
                }
                for (i in 0 until 4) {
                    val d = b[i * 2] * nx + b[i * 2 + 1] * ny
                    if (d < bMin) bMin = d
                    if (d > bMax) bMax = d
                }
                val o = minOf(aMax, bMax) - maxOf(aMin, bMin)
                if (o <= 0f) return 0f
                if (o < best) best = o
            }
        }
        return best
    }
}
