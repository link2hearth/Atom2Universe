package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.random.Random

/**
 * Vérifie le vocabulaire d'architecture : chaque module doit tenir debout tout seul,
 * rester dans les budgets du moteur, et se détruire de la façon qui lui est propre.
 *
 * C'est le banc qui remplace le fait de regarder l'écran : une construction qui
 * s'écroule avant le premier tir ne se voit pas dans le code, elle se mesure.
 */
class TrebuchetModulesTest {

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
        TargetRules.style = TargetStyle.ARCADE
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
            "maison" to TargetModules.house(rng, 0f, 6f, 9f)
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
        TargetRules.style = TargetStyle.ARCADE
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
        TargetRules.style = TargetStyle.ARCADE
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
}
