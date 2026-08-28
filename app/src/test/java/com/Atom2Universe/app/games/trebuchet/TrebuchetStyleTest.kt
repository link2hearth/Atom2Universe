package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Compare les deux tempéraments de front : réaliste contre arcade.
 *
 * Ce n'est pas un test de plus, c'est **la** justification du mode arcade. On y vérifie
 * la promesse : un coup **ordinaire** emporte une pierre entière au lieu de la fêler,
 * les pierres sont grosses et assez légères pour être renversées, et une construction
 * coûte beaucoup moins de corps au moteur.
 *
 * Le test le plus important du fichier est celui du **hameau**, et il est né d'un
 * défaut qu'aucun autre n'avait vu : le mode arcade ne réglait que la taille des
 * assises, une maison n'en a aucune, et la moitié des sites du jeu sont des hameaux.
 * On changeait donc de mode sans rien voir changer. Un test qui compare les deux modes
 * sur le seul module qui n'est pas en pierre est la seule façon de ne pas y revenir.
 */
class TrebuchetStyleTest {

    @After
    fun rendLeMode() {
        TargetRules.style = TargetStyle.ARCADE
    }

    /**
     * Un monde vide posé sur un relief — plat par défaut, celui du niveau quand on en
     * charge un.
     *
     * Depuis que les sites s'étagent, charger une construction sur un sol plat n'est
     * plus une simplification : c'est un contresens. Un bourg en terrasses bâti à
     * quinze mètres d'altitude tomberait de quinze mètres au premier pas, et le test
     * conclurait qu'il s'est affaissé tout seul — ce qui serait vrai, et n'aurait rien
     * à voir avec ce qu'on voulait mesurer.
     */
    private fun world(terrain: Terrain = Terrain.FLAT): PhysWorld = PhysWorld().apply {
        iterations = 16
        for (b in terrain.bodies(friction = 0.7f)) add(b)
    }

    private fun PhysWorld.fireBall(x: Float, y: Float, speed: Float): PhysBody {
        val b = PhysBody.circle(TrebuchetRules.BALL_RADIUS, TrebuchetRules.BALL_MASS).apply {
            this.x = x; this.y = y
            vx = speed
            friction = 0.2f
            restitution = 0.1f
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
        }
        add(b)
        return b
    }

    /** Tire un boulet dans le pied d'une courtine et rend ce qu'il en reste. */
    private fun shootWall(style: TargetStyle): Triple<Int, Float, Float> {
        TargetRules.style = style
        val s = TargetField.settle(
            Structure(TargetModules.curtainWall(Random(5), 20f, 10f, 8f), "courtine")
        )
        val w = world()
        val f = TargetField(w)
        f.load(s)
        repeat(90) { w.stepFrame(1f / 60f); f.update(1f / 60f) }
        val avant = f.ruinHeight()

        w.fireBall(10f, 1.2f, 130f)
        repeat(300) { w.stepFrame(1f / 60f); f.update(1f / 60f) }

        return Triple(s.blocks.size, f.brokenRatio, avant - f.ruinHeight())
    }

    @Test
    fun `l arcade emporte des morceaux la ou le realiste egratigne`() {
        val (corpsR, casseR, chuteR) = shootWall(TargetStyle.REALISTE)
        val (corpsA, casseA, chuteA) = shootWall(TargetStyle.ARCADE)

        println(
            "STYLE réaliste : $corpsR corps, un coup détruit ${"%.1f".format(casseR * 100)}%, " +
                "la crête descend de ${"%.2f".format(chuteR)} m"
        )
        println(
            "STYLE arcade   : $corpsA corps, un coup détruit ${"%.1f".format(casseA * 100)}%, " +
                "la crête descend de ${"%.2f".format(chuteA)} m"
        )

        assertTrue(
            "l'arcade ne coûte pas moins de corps : $corpsA contre $corpsR",
            corpsA < corpsR
        )
        assertTrue(
            "un coup d'arcade ne fait pas plus de dégâts qu'en réaliste : $casseA contre $casseR",
            casseA > casseR
        )
    }

    @Test
    fun `une pierre d arcade se casse d un coup et se laisse pousser`() {
        TargetRules.style = TargetStyle.ARCADE
        // On ne recopie pas les dimensions à la main : on demande une vraie courtine et
        // on prend sa première pierre. Un chiffre recopié se périme au premier réglage,
        // et le test jurerait alors sur une pierre qui n'existe plus.
        val pierre = TargetModules.curtainWall(Random(5), 20f, 10f, 8f).first()
        val taille = pierre.parts.first()

        // Le coup de référence n'est **pas** le coup parfait. Un trébuchet bien réglé
        // envoie son boulet à cent cinquante mètres par seconde, mais il arrive sur la
        // cible bien plus lentement, et un mode arcade qui ne casse que sur un tir
        // parfait n'est pas un mode arcade. On se cale donc sur un tir moyen.
        val ordinaire = 0.5f * TrebuchetRules.BALL_MASS * 70f * 70f
        println(
            "ARCADE pierre de ${"%.2f".format(2 * taille.halfW)} x " +
                "${"%.2f".format(2 * taille.halfH)} m, ${pierre.mass.toInt()} kg, " +
                "${(pierre.hp / 1000).toInt()} kJ de vie, " +
                "boulet ordinaire à 70 m/s = ${(ordinaire / 1000).toInt()} kJ, " +
                "poussée = ${"%.2f".format(TrebuchetRules.BALL_MASS * 70f / pierre.mass)} m/s"
        )
        assertTrue(
            "une pierre d'arcade encaisse plus qu'un tir ordinaire : ${pierre.hp}",
            pierre.hp < ordinaire
        )
        // Elle doit se **voir** : une pierre d'arcade fait au moins deux mètres de front
        // et un mètre de haut, sinon le joueur ne fait pas la différence avec le mode
        // réaliste — ce qui est très exactement ce qui s'était passé.
        assertTrue(
            "une pierre d'arcade n'est pas plus grosse qu'une pierre réelle : " +
                "${2 * taille.halfW} x ${2 * taille.halfH}",
            2 * taille.halfW > 2f && 2 * taille.halfH > 1f
        )
        // Et elle doit rester assez lourde pour tomber sur ses voisines au lieu de
        // s'envoler comme un fétu.
        assertTrue("une pierre d'arcade est devenue une plume", pierre.mass > 1200f)
    }

    @Test
    fun `un site d arcade coute bien moins de corps`() {
        TargetRules.style = TargetStyle.REALISTE
        val realiste = TargetGenerator.generate(8L).structure
        TargetRules.style = TargetStyle.ARCADE
        val arcade = TargetGenerator.generate(8L).structure

        println(
            "SITE château : réaliste ${realiste.blocks.size} corps / " +
                "${(realiste.totalMass / 1000).toInt()} t, " +
                "arcade ${arcade.blocks.size} corps / ${(arcade.totalMass / 1000).toInt()} t"
        )
        assertTrue(
            "l'arcade ne simplifie pas le site : ${arcade.blocks.size} contre ${realiste.blocks.size}",
            arcade.blocks.size < realiste.blocks.size
        )
        assertTrue("le site d'arcade est vide", arcade.blocks.size > 8)
        assertTrue(
            "le site d'arcade s'est mis à flotter",
            arcade.problems().isEmpty()
        )
    }

    /**
     * La promesse la plus simple, et celle qui a mis le plus longtemps à être tenue :
     * **un site d'arcade est deux fois plus grand**.
     *
     * Deux versions du mode arcade s'y sont cassé les dents en ne grossissant que les
     * pierres à l'intérieur d'un site de taille inchangée. C'est logique sur le papier
     * — moins de pierres, plus grosses — et parfaitement invisible en jeu : de trois
     * cents mètres, le joueur voit une silhouette, pas des joints. Ce qui se remarque
     * est qu'une maison fasse douze mètres au lieu de six.
     */
    @Test
    fun `un site d arcade est deux fois plus grand`() {
        TargetRules.style = TargetStyle.REALISTE
        val r = TargetGenerator.generate(1L).structure
        TargetRules.style = TargetStyle.ARCADE
        val a = TargetGenerator.generate(1L).structure

        println(
            "TAILLE hameau : réaliste ${"%.1f".format(r.baseHeight)} m de haut sur " +
                "${"%.1f".format(r.width)} m ; arcade ${"%.1f".format(a.baseHeight)} m " +
                "sur ${"%.1f".format(a.width)} m"
        )
        assertTrue(
            "le site d'arcade n'est pas plus haut : ${a.baseHeight} contre ${r.baseHeight}",
            a.baseHeight > 1.3f * r.baseHeight
        )
        assertTrue(
            "le site d'arcade n'est pas plus large : ${a.width} contre ${r.width}",
            a.width > 1.3f * r.width
        )
    }

    /**
     * Le test du hameau : le mode arcade doit se voir **là où il n'y a pas de pierre**.
     *
     * Une maison est faite de poteaux, de poutres et d'un toit, tous donnés en mètres
     * fixes. Tant que le tempérament ne réglait que les assises, un hameau d'arcade
     * était le sosie exact d'un hameau réaliste — même nombre de corps, mêmes formes,
     * même silhouette — et comme la moitié des sites du jeu sont des hameaux, changer
     * de mode ne changeait rien de visible.
     */
    @Test
    fun `un hameau d arcade ne ressemble pas a un hameau realiste`() {
        fun maison(style: TargetStyle): List<Block> {
            TargetRules.style = style
            return TargetModules.house(Random(3), 0f, 5.5f, 8f)
        }

        val r = maison(TargetStyle.REALISTE)
        val a = maison(TargetStyle.ARCADE)
        fun grosseur(m: List<Block>) =
            m.flatMap { b -> b.parts.map { 4f * it.halfW * it.halfH } }.average().toFloat()

        println(
            "MAISON réaliste ${r.size} corps, pièce moyenne ${"%.2f".format(grosseur(r))} m² ; " +
                "arcade ${a.size} corps, pièce moyenne ${"%.2f".format(grosseur(a))} m²"
        )
        assertTrue(
            "une maison d'arcade a autant de pièces qu'une maison réaliste : " +
                "${a.size} contre ${r.size}",
            a.size < r.size
        )
        assertTrue(
            "les pièces d'une maison d'arcade ne sont pas plus grosses : " +
                "${grosseur(a)} contre ${grosseur(r)}",
            grosseur(a) > 1.5f * grosseur(r)
        )
    }

    /**
     * Une tournée complète de l'échelle des sites, chargée pour de vrai dans le moteur.
     *
     * Les huit premières graines donnent exactement une fois chaque sorte de site. Des
     * pierres plus grosses et plus légères changent tout l'équilibre d'une pile : ce
     * test est là pour dire, en une ligne, qu'aucun site d'arcade ne s'abîme ni ne
     * s'affaisse tout seul avant le premier tir.
     */
    @Test
    fun `aucun site d arcade ne s abime tout seul`() {
        TargetRules.style = TargetStyle.ARCADE
        for (seed in 1L..8L) {
            val lvl = TargetGenerator.generate(seed)
            // On rapproche le site de l'origine : un monde qui commence à cinq cents
            // mètres ne prouve rien de plus et coûte de la précision. Le relief fait le
            // même chemin — c'est lui qui porte la construction.
            val dx = -lvl.distance + 20f
            val s = lvl.structure.translated(dx)
            val sol = lvl.terrain.translated(dx)
            val w = world(sol)
            val f = TargetField(w)
            f.load(s)
            repeat(240) { w.stepFrame(1f / 60f); f.update(1f / 60f) }
            println(
                "TENUE $seed ${lvl.kind} : ${s.blocks.size} corps, " +
                    "abîmé ${"%.1f".format(f.brokenRatio * 100)}%, crête " +
                    "${"%.2f".format(f.ruinHeight())} pour ${"%.2f".format(s.baseHeight)} m"
            )
            assertTrue("graine $seed : ${s.problems(sol)}", s.problems(sol).isEmpty())
            assertEquals(
                "graine $seed : le site s'est abîmé tout seul",
                0f, f.brokenRatio, 1e-4f
            )
            assertTrue(
                "graine $seed : le site s'est affaissé, " +
                    "${f.ruinHeight()} pour ${s.baseHeight}",
                f.ruinHeight() > 0.95f * s.baseHeight
            )
        }
    }
}
