package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Le garde-fou de la traversée : **rien ne doit jamais passer au travers de rien**.
 *
 * C'est le seul défaut du moteur que les tests d'énergie ne peuvent pas voir. Un
 * solveur qui crée de l'énergie se dénonce tout seul, chiffre à l'appui ; un boulet
 * qui traverse un mur, lui, laisse une simulation parfaitement saine — simplement, le
 * mur n'a rien senti. Ça ne se remarque qu'à l'œil, une fois sur vingt, et ça ne se
 * reproduit pas : tout dépend de la phase, c'est-à-dire de la fraction de sous-pas qui
 * sépare le boulet de la planche à l'image précédente.
 *
 * D'où la forme de ces tests : on ne tire pas un coup, on en tire **une dizaine, décalés
 * chacun d'un dixième du chemin parcouru en une image**. Un découpage trop grossier finit
 * toujours par se faire prendre par l'un d'eux.
 */
class PhysicsTunnelTest {

    /** Un mur mince et infiniment lourd, planté en [x]. */
    private fun wall(x: Float, halfThickness: Float = 0.05f, halfHeight: Float = 2f): PhysBody =
        PhysBody(halfThickness, halfHeight, 0f).apply {
            this.x = x
            this.y = 0f
            lockPosition = true
            lockRotation = true
            friction = 0.6f
            refreshMass()
        }

    /** Un boulet de trébuchet : douze kilos, vingt-quatre centimètres. */
    private fun cannonball(x: Float, speed: Float): PhysBody =
        PhysBody.circle(0.12f, 12f).apply {
            this.x = x
            this.y = 0f
            vx = speed
        }

    /**
     * Une planche de dix centimètres arrête un boulet, quelle que soit la phase.
     *
     * Le monde est sans pesanteur : on ne teste ici qu'une chose, la traversée, et une
     * parabole ne ferait qu'ajouter du bruit sur la hauteur d'impact.
     */
    @Test
    fun `un boulet ne traverse jamais une planche isolee`() {
        val dt = 1f / 60f
        for (speed in listOf(80f, 120f, 150f, 200f)) {
            // Un dixième de chemin d'image à chaque essai : de quoi balayer toutes les
            // positions possibles du boulet à l'image qui précède le choc.
            val pas = speed * dt / 10f
            for (i in 0 until 10) {
                val world = PhysWorld().apply {
                    gravity = 0f
                    linearDamping = 0f
                    angularDamping = 0f
                }
                world.add(wall(0f))
                val ball = cannonball(-(20f + i * pas), speed)
                world.add(ball)

                repeat(120) { world.stepFrame(dt) }

                assertTrue(
                    "à %.0f m/s (essai %d), le boulet est ressorti à x=%.2f".format(speed, i, ball.x),
                    ball.x < 0f
                )
                assertTrue(
                    "à %.0f m/s (essai %d), le boulet n'a jamais atteint la planche : x=%.2f"
                        .format(speed, i, ball.x),
                    ball.x > -1f
                )
            }
        }
    }

    /**
     * Le cas qui compte pour le jeu : **un mur endormi**.
     *
     * Une pierre qui dort n'est plus intégrée et ses contacts ne sont plus cherchés
     * entre dormeurs — mais elle reste un obstacle, et un boulet qui la traverserait
     * serait exactement le bug qu'on redoute. C'est aussi le test qui attrape la
     * tentation la plus naturelle : ignorer les dormeurs dans le calcul des sous-pas.
     */
    @Test
    fun `un boulet ne traverse jamais un mur endormi`() {
        val dt = 1f / 60f
        val pas = 150f * dt / 10f
        for (i in 0 until 10) {
            val world = PhysWorld().apply {
                sleepEnabled = true
                linearDamping = 0f
            }
            world.add(ground(60f))
            // Huit assises de soixante centimètres sur vingt-cinq d'épaisseur.
            val assises = ArrayList<PhysBody>()
            for (k in 0 until 8) {
                val b = box(0.3f, 0.125f, 200f, 0f, 0.125f + k * 0.25f)
                assises.add(b)
                world.add(b)
            }
            // On laisse le mur se tasser et s'endormir pour de bon.
            world.simulate(1.5f)
            assertTrue("le mur ne s'est pas endormi", assises.all { it.sleeping })

            val ball = cannonball(-(20f + i * pas), 150f).apply { y = 1f }
            world.add(ball)
            repeat(60) { world.stepFrame(dt) }

            assertTrue(
                "essai %d : le boulet est ressorti de l'autre côté, x=%.2f".format(i, ball.x),
                ball.x < 0.3f
            )
            assertTrue(
                "essai %d : le mur n'a rien senti".format(i),
                assises.any { it.impactAccum > 0f }
            )
        }
    }

    /**
     * L'autre moitié du contrat : une pièce mince **hors d'atteinte** ne doit pas
     * découper l'image.
     *
     * C'est le défaut que la proximité vient corriger. Le boulet file à cent cinquante
     * mètres par seconde, donc deux mètres et demi par image : une planche à deux cents
     * mètres ne peut rien lui faire cette image-ci, et n'a aucune raison de lui imposer
     * trente-deux sous-pas. La même planche à un mètre, elle, les mérite tous.
     */
    @Test
    fun `une piece mince hors d atteinte ne decoupe pas l image`() {
        val dt = 1f / 60f
        val world = PhysWorld().apply { gravity = 0f }
        world.add(wall(200f))
        val ball = cannonball(0f, 150f)
        world.add(ball)

        assertEquals(
            "une planche à deux cents mètres taille les sous-pas du boulet",
            1,
            world.subStepsFor(dt)
        )

        // Un mètre devant : là, il faut découper.
        ball.x = 199f
        assertTrue(
            "une planche à un mètre ne taille plus rien : ${world.subStepsFor(dt)} sous-pas",
            world.subStepsFor(dt) > 8
        )
    }

    /**
     * Et la proximité ne se mesure pas qu'entre le boulet et sa cible : **deux débris
     * qui se croisent** doivent découper l'image autant qu'il le faut.
     */
    @Test
    fun `deux eclats qui se croisent decoupent l image`() {
        val dt = 1f / 60f
        val world = PhysWorld().apply { gravity = 0f }
        val a = box(0.05f, 0.4f, 20f, -1f, 0f).apply { vx = 60f }
        val b = box(0.05f, 0.4f, 20f, 1f, 0f).apply { vx = -60f }
        world.add(a)
        world.add(b)

        assertTrue(
            "deux éclats qui se croisent ne découpent rien : ${world.subStepsFor(dt)} sous-pas",
            world.subStepsFor(dt) > 4
        )

        // Écartés, ils ne coûtent plus rien.
        a.x = -200f
        b.x = 200f
        assertEquals(
            "des éclats éloignés découpent encore l'image",
            1,
            world.subStepsFor(dt)
        )
    }

    /**
     * La propriété que tout le découpage sert à garantir, énoncée en clair.
     *
     * Deux corps se recouvrent tant que leurs centres sont plus proches que la somme de
     * leurs appuis : la **fenêtre** où la détection peut les voir se toucher est donc
     * large de `2 × (rayon du boulet + demi-épaisseur du mur)`, soit 34 cm ici. Le pas
     * retenu doit laisser plusieurs relevés à l'intérieur de cette fenêtre — le moteur
     * en vise quatre.
     */
    @Test
    fun `le pas retenu laisse plusieurs releves dans la fenetre`() {
        val dt = 1f / 60f
        val world = PhysWorld().apply { gravity = 0f }
        world.add(wall(2f))
        val ball = cannonball(0f, 150f)
        world.add(ball)

        val fenetre = 2f * (0.12f + 0.05f)
        val h = dt / world.subStepsFor(dt)
        val chemin = sqrt(ball.speedSq) * h
        assertTrue(
            "le boulet avance de %.3f m par sous-pas, pour une fenêtre de %.2f m"
                .format(chemin, fenetre),
            chemin < fenetre / 2f
        )
    }
}
