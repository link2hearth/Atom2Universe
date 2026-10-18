package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Bump
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Kicker
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Layout
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Level
import com.Atom2Universe.app.games.toyboxracers.track.SculptedCircuits.Prop

/**
 * Les tracés sculptés, un par circuit. Coordonnées de la pièce : x de -118 à 118 (ouest → est),
 * z de -75 à 75 (nord → sud) ; les meubles des huit pièces bordent les murs nord et sud entre
 * x = -56 et x = 46, le tracé reste donc à |z| < 50 dans cette bande.
 *
 * Chaque tracé est un polygone dont les coins sont arrondis à un rayon choisi ([Corner]) ; le
 * tour part de la sortie du premier coin. Le relief se pose ensuite par des points de la pièce
 * (`at(x, z)` : la fraction du tour qui passe là), ce qui le garde en place si l'on retouche
 * un virage. Tout se relit avec le banc d'aperçu (`ToyboxCircuitApercuTest`) : vue de dessus,
 * profil, vues de poursuite et un tour de pilote automatique.
 */
internal object CircuitDesigns {
    fun all(): Map<CircuitKind, Layout> = mapOf(
        CircuitKind.ROLLING_HILLS to rollingHills(),
        CircuitKind.DOUBLE_BUMPS to doubleBumps(),
        CircuitKind.HIGH_GARDEN to viaduct(),
        CircuitKind.SWITCHBACKS to switchbacks(),
        CircuitKind.JUMP_PARADE to jumpParade(),
        CircuitKind.RIBBON_RALLY to ribbonRally(),
        CircuitKind.GRAND_PRIX to grandPrix(),
        CircuitKind.ROLLER_COASTER to rollerCoaster(),
        CircuitKind.TOY_TOWN to toyTown()
    ) + DestinationCircuits.all()

    /** Un tracé en cours d'écriture : sa boucle, et de quoi repérer ses points. */
    private class Plan(vararg corners: Float) {
        val loop = PlanarLoop.corners(corners.toList().chunked(3).map { (x, z, r) -> Corner(x, z, r) })

        /** Fraction du tour qui passe en (x, z), décalée de [plus] mètres. */
        fun at(x: Float, z: Float, plus: Float = 0f, near: Float = -1f): Float {
            val f = (loop.fractionNear(x, z, near) + plus / loop.length) % 1f
            return if (f < 0f) f + 1f else f
        }
    }

    private fun toy(kind: ToyKind, x: Float, z: Float) = when (kind) {
        ToyKind.BLOCKS -> ToyObstacle(kind, x, z, 6.5f, 7f)
        ToyKind.TEDDY -> ToyObstacle(kind, x, z, 5.5f, 10f)
        ToyKind.TRAIN -> ToyObstacle(kind, x, z, 8f, 7f)
        ToyKind.SPINNING_TOP -> ToyObstacle(kind, x, z, 5f, 8f)
    }

    /**
     * Collines : un grand haricot qui ondule. Les bosses du nord rapetissent, une longue
     * montée sur le côté est, puis la crête au creux du haricot, au milieu d'un virage.
     */
    private fun rollingHills(): Layout {
        val p = Plan(-100f,-46f,22f, 0f,-34f,60f, 100f,-46f,22f, 100f,44f,22f, 50f,44f,18f,
            22f,14f,16f, -14f,14f,16f, -40f,44f,18f, -100f,44f,22f)
        return Layout(p.loop, width = 10f,
            bumps = listOf(
                Bump(p.at(-50f, -40f), 44f, 5.5f), Bump(p.at(5f, -35f), 36f, 4f), Bump(p.at(55f, -40f), 28f, 2.5f),
                Bump(p.at(100f, 0f), 64f, 5f), Bump(p.at(4f, 14f), 46f, 5.5f),
                Bump(p.at(-72f, 44f), 30f, 3f), Bump(p.at(-100f, 0f), 36f, 3f)
            ),
            props = listOf(
                Prop("outdoor.tree", -70f, -6f, .55f), Prop("outdoor.tree", 66f, -14f, .6f, 40f),
                Prop("kawaii.toy_pine", -22f, -14f, 2.2f), Prop("kawaii.toy_pine", 30f, -14f, 1.8f),
                Prop("kawaii.toy_pine", -82f, 18f, 2f), Prop("kawaii.toy_pine", 78f, 20f, 2.4f),
                Prop("kawaii.mushroom", 4f, -10f, 2.2f), Prop("kawaii.rabbit", 12f, 30f, 1.6f, 200f),
                Prop("kawaii.hay_bale", 0f, 34f, 2f, 20f), Prop("kawaii.hay_bale", 36f, 50f, 1.6f, -10f),
                Prop("kawaii.frog", -4f, 25f, 1.5f, 160f), Prop("kawaii.duck", 86f, 2f, 1.5f, 270f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -50f, 8f), toy(ToyKind.TEDDY, 52f, 4f),
                toy(ToyKind.TRAIN, 25f, -62f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = -46f to -14f)
    }

    /**
     * Doubles bosses : trois « doubles » de motocross — on décolle d'une bosse pour se poser
     * sur le dos de la suivante, trop lent on tombe entre les deux — et des vagues en série.
     */
    private fun doubleBumps(): Layout {
        val p = Plan(-100f,-42f,20f, 100f,-42f,20f, 102f,42f,20f, 58f,42f,12f, 34f,8f,12f,
            -34f,8f,12f, -58f,42f,12f, -102f,42f,20f)
        fun double(x: Float, z: Float) = Kicker(p.at(x, z), 16f, 2.8f, 6.5f, .6f, 12f)
        val waves = buildList {
            for (z in floatArrayOf(-12f, -2f, 8f)) add(Bump(p.at(102f, z), 9f, 1.1f))
            for (z in floatArrayOf(8f, -2f, -12f)) add(Bump(p.at(-101f, z), 9f, 1.1f))
            add(Bump(p.at(80f, 42f), 16f, 2f))
            add(Bump(p.at(-80f, 42f), 16f, 2f))
        }
        return Layout(p.loop, width = 9.5f,
            bumps = waves,
            kickers = listOf(double(-38f, -42f), double(32f, -42f), double(4f, 8f)),
            props = listOf(
                Prop("kawaii.hay_bale", -108f, -50f, 2f, 45f), Prop("kawaii.hay_bale", 108f, -50f, 2f, -45f),
                Prop("kawaii.hay_bale", 110f, 50f, 2f, 45f), Prop("kawaii.hay_bale", -110f, 50f, 2f, -45f),
                Prop("garage.tires", 60f, 10f, 1.2f), Prop("garage.tires", -60f, 10f, 1.2f),
                Prop("kawaii.light_beacon", -38f, -30f, 1.3f), Prop("kawaii.light_beacon", 32f, -30f, 1.3f),
                Prop("kawaii.light_beacon", 4f, 20f, 1.3f),
                Prop("garage.cone", 72f, -6f, 1f), Prop("garage.cone", -72f, -6f, 1f),
                Prop("kawaii.sandcastle", 0f, -22f, 1.6f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -70f, -16f), toy(ToyKind.TEDDY, 70f, -16f),
                toy(ToyKind.TRAIN, 25f, -62f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = 0f to 28f)
    }

    /**
     * Viaduc aérien : un huit. La première diagonale traverse au ras du sol, la boucle est
     * grimpe, et la seconde diagonale repasse en pont 13 mètres plus haut, sur ses piliers.
     */
    private fun viaduct(): Layout {
        val p = Plan(-100f,50f,28f, 100f,-50f,28f, 100f,50f,28f, -100f,-50f,28f)
        val low = p.at(-30f, 15f)
        return Layout(p.loop, width = 10f,
            levels = listOf(Level(p.at(25f, -12.5f, near = low), 0f), Level(p.at(100f, 0f), 13f),
                Level(p.at(-60f, -30f), 13f), Level(p.at(-100f, 0f), 0f)),
            bumps = listOf(Bump(p.at(60f, 30f), 34f, 3f), Bump(p.at(-60f, 30f, near = low), 30f, 2.5f)),
            props = listOf(
                Prop("living.plant", -72f, 0f, 2f), Prop("living.plant", 72f, 0f, 2f),
                Prop("outdoor.planter", 0f, 34f, 1.2f), Prop("outdoor.planter", 0f, -40f, 1.2f, 180f),
                Prop("outdoor.tree", -40f, 42f, .55f), Prop("outdoor.tree", 40f, -42f, .55f),
                Prop("outdoor.hedge", 30f, 40f, .8f), Prop("outdoor.hedge", -30f, -40f, .8f),
                Prop("outdoor.bench", 0f, 46f, .7f), Prop("kawaii.potted_cactus", -60f, 12f, 2f),
                Prop("kawaii.potted_cactus", 60f, -12f, 2f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -72f, 16f), toy(ToyKind.TEDDY, 72f, -16f),
                toy(ToyKind.TRAIN, 25f, -62f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = 0f to -26f)
    }

    /**
     * Épingles : quatre lacets en terrasses qui grimpent chacun de cinq mètres, trois épingles
     * à plat, puis la redescente par le nord et l'ouest, en vagues de toboggan.
     */
    private fun switchbacks(): Layout {
        val p = Plan(-104f,40f,14f, 105f,40f,11f, 105f,18f,11f, -41f,18f,11f, -41f,-4f,11f,
            105f,-4f,11f, 105f,-26f,11f, -104f,-26f,14f)
        return Layout(p.loop, width = 9f,
            levels = listOf(
                Level(p.at(-40f, 40f), 0f), Level(p.at(85f, 40f), 5f),
                Level(p.at(85f, 18f), 5f), Level(p.at(-20f, 18f), 10f),
                Level(p.at(-20f, -4f), 10f), Level(p.at(85f, -4f), 15f),
                Level(p.at(85f, -26f), 15f), Level(p.at(-104f, 10f), 0f)
            ),
            bumps = listOf(Bump(p.at(40f, -26f), 24f, 2.5f), Bump(p.at(0f, -26f), 24f, 2.5f),
                Bump(p.at(-40f, -26f), 24f, 2.5f)),
            props = listOf(
                Prop("kawaii.toy_pine", 30f, 29f, 1.6f), Prop("kawaii.toy_pine", -10f, 29f, 1.4f),
                Prop("kawaii.toy_pine", 60f, 7f, 1.6f), Prop("kawaii.toy_pine", 10f, 7f, 1.3f),
                Prop("kawaii.toy_pine", 40f, -15f, 1.5f), Prop("kawaii.snowman", 94f, -15f, 1.2f, 270f),
                Prop("kawaii.toy_pine", 94f, 29f, 1.2f), Prop("kawaii.ice_crystal", -30f, 7f, 1.2f),
                Prop("kawaii.penguin", -60f, -40f, 1.8f, 30f), Prop("kawaii.toy_pine", -70f, 50f, 2f),
                Prop("kawaii.toy_pine", 80f, 52f, 2f), Prop("kawaii.ice_crystal", 60f, -40f, 1.8f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -76f, 12f), toy(ToyKind.TEDDY, -78f, -6f),
                toy(ToyKind.TRAIN, 25f, -62f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = -72f to 5f)
    }

    /**
     * Parade de sauts : de vrais tremplins sur les lignes droites — un petit bond, un grand
     * saut par-dessus un donut, une montée sur un plateau qui finit en saut vers le bas, un
     * double, et un dernier bond avant la ligne.
     */
    private fun jumpParade(): Layout {
        val p = Plan(-104f,-44f,22f, 104f,-44f,22f, 104f,44f,22f, -64f,44f,16f, -90f,16f,16f, -104f,-8f,14f)
        val drop = p.at(40f, 44f)
        return Layout(p.loop, width = 10.5f,
            levels = listOf(Level(p.at(104f, -20f), 0f), Level(p.at(78f, 44f), 5f),
                Level(drop, 5f), Level(drop + 8f / p.loop.length, 0f)),
            kickers = listOf(
                Kicker(p.at(-60f, -44f), 14f, 2f, 5f, 1.5f, 8f),
                Kicker(p.at(-15f, -44f), 14f, 2f, 5f, 1.5f, 8f),
                Kicker(p.at(30f, -44f), 24f, 3.5f, 9f, 3f, 12f),
                Kicker(drop, 16f, 1.5f, 8f, 5f, 16f),
                Kicker(p.at(-24f, 44f), 16f, 2.8f, 6.5f, .6f, 12f)
            ),
            bumps = listOf(Bump(p.at(-80f, 30f), 12f, 1.2f), Bump(p.at(-90f, 16f), 12f, 1.2f)),
            props = listOf(
                Prop("kawaii.donut", 34.5f, -44f, 1.2f), Prop("kawaii.donut", 64f, -60f, 2f),
                Prop("kawaii.light_beacon", -60f, -55f, 1.4f), Prop("kawaii.light_beacon", -15f, -32f, 1.4f),
                Prop("kawaii.light_beacon", 30f, -31f, 1.4f), Prop("kawaii.light_beacon", -24f, 31f, 1.4f),
                Prop("kawaii.hay_bale", 112f, -52f, 2f, 45f), Prop("kawaii.hay_bale", 112f, 52f, 2f, -45f),
                Prop("garage.barrier", -40f, -30f, .8f), Prop("kawaii.rocket", 0f, 0f, 3f),
                Prop("garage.cone", -60f, 0f, 1.2f), Prop("garage.cone", 60f, 0f, 1.2f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -50f, 14f), toy(ToyKind.TEDDY, 60f, 16f),
                toy(ToyKind.TRAIN, 25f, -64f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = -36f to -10f)
    }

    /**
     * Rallye ruban : étroit et tortueux. Chicane du nord en creux, crête aveugle sur le droit,
     * un nœud d'épingles à l'est, et des crêtes au milieu des virages.
     */
    private fun ribbonRally(): Layout {
        val p = Plan(-104f,-44f,14f, -44f,-44f,16f, -24f,-24f,12f, 6f,-24f,12f, 26f,-44f,16f,
            104f,-44f,14f, 104f,-4f,10f, 64f,-4f,10f, 64f,20f,10f, 104f,22f,10f, 104f,44f,10f,
            20f,44f,14f, 0f,24f,12f, -30f,24f,12f, -50f,44f,12f, -104f,44f,14f)
        return Layout(p.loop, width = 8.5f,
            bumps = listOf(
                Bump(p.at(-75f, -44f), 30f, 3f), Bump(p.at(-9f, -24f), 26f, 3.5f), Bump(p.at(65f, -44f), 40f, 5f),
                Bump(p.at(84f, 21f), 22f, 2f), Bump(p.at(60f, 44f), 34f, 4f), Bump(p.at(-15f, 24f), 24f, 3f),
                Bump(p.at(-104f, 0f), 40f, 4.5f)
            ),
            props = listOf(
                Prop("kawaii.toy_pine", -60f, -20f, 2f), Prop("kawaii.toy_pine", -86f, -16f, 2.4f),
                Prop("kawaii.toy_pine", -40f, 4f, 1.8f), Prop("kawaii.toy_pine", 20f, -8f, 2.2f),
                Prop("kawaii.toy_pine", 40f, 18f, 2f), Prop("kawaii.mushroom", -9f, -12f, 1.6f),
                Prop("kawaii.hay_bale", 112f, -52f, 2f, 45f), Prop("kawaii.hay_bale", 84f, 8f, 1.4f),
                Prop("garage.tires", 112f, 52f, 1.3f), Prop("garage.tires", -112f, 52f, 1.3f),
                Prop("outdoor.tree", -80f, 22f, .5f), Prop("kawaii.hay_bale", -15f, 36f, 1.4f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -60f, 4f), toy(ToyKind.TEDDY, 84f, -24f),
                toy(ToyKind.TRAIN, 25f, -64f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = -64f to 2f)
    }

    /**
     * Grand Prix des jouets : le tour de la pièce. Chicane autour d'une pile de pneus, une esse
     * autour de la fusée, passage sous une grande table, puis l'infield : un échangeur qui
     * boucle sur lui-même et repasse en pont, et une épingle avant la ligne.
     */
    private fun grandPrix(): Layout {
        val p = Plan(-92f,44f,9f, 24f,44f,16f, 40f,32f,12f, 56f,44f,16f, 104f,44f,12f,
            104f,20f,10f, 66f,20f,10f, 66f,-14f,10f, 104f,-14f,10f, 104f,-42f,18f,
            -104f,-42f,18f, -104f,4f,12f, 30f,4f,14f, 30f,-24f,14f, 2f,-24f,14f, 2f,26f,12f,
            -92f,26f,9f)
        return Layout(p.loop, width = 10.5f,
            levels = listOf(Level(p.at(16f, 4f), 0f), Level(p.at(2f, -12f), 7.5f),
                Level(p.at(2f, 14f), 7.5f), Level(p.at(-40f, 26f), 0f)),
            bumps = listOf(Bump(p.at(66f, 3f), 24f, 3f), Bump(p.at(-50f, -42f), 36f, 3.5f),
                Bump(p.at(-60f, 4f), 30f, 2.5f)),
            props = listOf(
                Prop("living.dining_table", 20f, -42f, 1.8f),
                Prop("garage.tires", 40f, 49f, 1.2f),
                Prop("kawaii.stacking_rings", 16f, -10f, 1.6f),
                Prop("kawaii.rocket", 90f, 3f, 2.6f), Prop("kawaii.robot", 48f, 8f, 2.4f, -30f),
                Prop("kawaii.dinosaur", -52f, -20f, 3f, 20f),
                Prop("kawaii.light_beacon", -66f, 35.5f, 1.5f), Prop("kawaii.light_beacon", -66f, 52.5f, 1.5f),
                Prop("garage.barrier", -110f, 35f, 1f, 90f),
                Prop("garage.tires", 109f, 52f, 1.2f), Prop("garage.tires", 109f, -52f, 1.2f),
                Prop("garage.tires", -109f, -52f, 1.2f), Prop("garage.cone", -40f, 15f, 1f),
                Prop("garage.cone", -60f, 15f, 1f), Prop("garage.cone", -80f, 15f, 1f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -84f, -18f), toy(ToyKind.TEDDY, 48f, -10f),
                toy(ToyKind.TRAIN, 72f, -58f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = 48f to -24f)
    }

    /**
     * Montagnes russes : une hélice qui grimpe d'un tour et demi jusqu'à seize mètres, la
     * grande descente, une seconde bosse, une hélice qui redescend en tournant, puis un U de
     * bosses à chameau entre les deux hélices.
     */
    private fun rollerCoaster(): Layout {
        val p = Plan(-100f,44f,16f, -40f,44f,12f, -40f,6f,12f, 22f,6f,10f, 30f,26f,10f,
            96f,26f,26f, 96f,-26f,26f, 44f,-26f,26f, 44f,26f,26f, 96f,26f,26f, 96f,-26f,26f,
            30f,-42f,20f, -14f,-42f,14f, -30f,-24f,14f,
            -100f,-24f,24f, -100f,24f,24f, -52f,24f,24f, -52f,-24f,24f, -100f,-24f,24f)
        val entry = p.at(44f, 26f)
        return Layout(p.loop, width = 10f,
            levels = listOf(
                Level(entry, 0f), Level(p.at(60f, -34f), 16f),
                Level(p.at(-14f, -42f, plus = -8f), 2f), Level(p.at(-60f, -24f), 10f),
                Level(p.at(-100f, 20f), 0f)
            ),
            bumps = listOf(Bump(p.at(-54f, 44f), 20f, 2.5f), Bump(p.at(-40f, 25f), 22f, 3f),
                Bump(p.at(-14f, 6f), 26f, 3.5f), Bump(p.at(8f, 6f), 18f, 2.2f)),
            props = listOf(
                Prop("kawaii.ice_cream", 70f, 0f, 4f), Prop("kawaii.cupcake", -76f, 0f, 4f),
                Prop("kawaii.donut", 2f, 40f, 2.5f), Prop("kawaii.light_beacon", 42f, -52f, 1.6f),
                Prop("kawaii.light_beacon", -24f, -52f, 1.6f), Prop("kawaii.stacking_rings", 0f, -10f, 1.8f),
                Prop("kawaii.cherries", 24f, -8f, 2f), Prop("kawaii.watermelon", -110f, 54f, 2f, 30f),
                Prop("kawaii.ice_cream", 110f, -54f, 2f), Prop("kawaii.cupcake", 110f, 54f, 2f)
            ),
            toys = listOf(toy(ToyKind.BLOCKS, -24f, -8f), toy(ToyKind.TEDDY, 14f, 28f),
                toy(ToyKind.TRAIN, 25f, -62f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = -16f to 26f)
    }

    /**
     * Petite ville : des rues à angle droit entre des pâtés de maisons, des dos-d'âne, un crochet
     * autour du parc, et une rue qui monte en pont au-dessus d'une autre avant de redescendre.
     */
    private fun toyTown(): Layout {
        val p = Plan(-104f,44f,10f, 100f,44f,12f, 100f,20f,10f, 76f,20f,10f, 76f,-10f,10f, 100f,-10f,10f,
            100f,-42f,12f, 60f,-42f,10f, 60f,8f,10f,
            -40f,8f,10f, -40f,-42f,10f, 10f,-42f,10f, 10f,26f,10f, -70f,26f,10f, -70f,-42f,10f,
            -104f,-42f,10f)
        return Layout(p.loop, width = 9.5f,
            levels = listOf(Level(p.at(-25f, -42f), 0f), Level(p.at(10f, -6f), 7f),
                Level(p.at(10f, 20f), 7f), Level(p.at(-24f, 26f), 0f)),
            bumps = listOf(
                Bump(p.at(-40f, 44f), 5f, .7f), Bump(p.at(60f, 44f), 5f, .7f), Bump(p.at(76f, 5f), 5f, .7f),
                Bump(p.at(20f, 8f), 5f, .7f), Bump(p.at(-40f, -20f), 5f, .7f), Bump(p.at(-70f, 0f), 5f, .7f),
                Bump(p.at(-104f, 0f), 5f, .7f)
            ),
            props = townProps(),
            toys = listOf(toy(ToyKind.BLOCKS, 92f, 4f), toy(ToyKind.TEDDY, -88f, 0f),
                toy(ToyKind.TRAIN, 25f, -64f), toy(ToyKind.SPINNING_TOP, 0f, 62f)),
            island = 82f to 57f)
    }

    /** Pâtés de maisons, jardins et mobilier de rue de la petite ville. */
    private fun townProps() = listOf(
        // Pâté nord-est, entre la rue du pont et l'avenue est.
        Prop("outdoor.house", 34f, -20f, .42f, 90f), Prop("outdoor.tree", 34f, -2f, .45f),
        // Pâté du centre-nord, maisons tournées vers la rue du sud.
        Prop("outdoor.house", -16f, -22f, .42f), Prop("outdoor.mailbox", -4f, -6f, .5f),
        // Pâté ouest, étroit : une maison et ses haies.
        Prop("outdoor.house", -55f, -18f, .36f, 90f), Prop("outdoor.hedge", -55f, 10f, .55f, 90f),
        // Grande bande ouest.
        Prop("outdoor.house", -87f, -24f, .38f, 270f), Prop("outdoor.house", -87f, 16f, .38f, 270f),
        // Jardin entre la rue du pont et l'avenue sud.
        Prop("outdoor.house", 34f, 26f, .4f, 180f), Prop("outdoor.bench", 23f, 36f, .6f),
        // Parc de l'est, coupé par le crochet de la rue.
        Prop("outdoor.tree", 82f, -28f, .5f), Prop("outdoor.tree", 66f, 32f, .45f),
        Prop("outdoor.porch", 108f, 5f, .5f, 270f), Prop("outdoor.bench", 88f, 32f, .55f),
        // Mobilier de rue le long de l'avenue sud.
        Prop("outdoor.fence", -50f, 34f, .6f), Prop("outdoor.fence", -30f, 34f, .6f),
        Prop("outdoor.planter", -10f, 34f, .6f), Prop("kawaii.light_beacon", -80f, 34f, 1.2f),
        Prop("outdoor.family_car", -60f, 52f, .3f, 90f), Prop("outdoor.family_car", 56f, 54f, .3f, 90f)
    )
}
