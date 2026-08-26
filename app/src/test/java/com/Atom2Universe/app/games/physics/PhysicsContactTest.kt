package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Vérifie la partie « contacts » du moteur : les piles doivent tenir (c'était
 * déjà l'exigence du jeu d'équilibre), les disques doivent rouler, les chocs
 * doivent rebondir quand on le demande, et rien ne doit traverser rien.
 */
class PhysicsContactTest {

    // ─────────────────────────── Piles de briques ───────────────────────────

    @Test
    fun `une brique lachee se pose sur le sol et sy immobilise`() {
        val world = worldWithGround()
        val b = box(0.2f, 0.15f, 10f, 0f, 1.2f)
        world.add(b)

        world.simulate(3f)

        assertTrue("la brique traverse le sol : y=${b.y}", b.y > 0.10f)
        assertTrue("la brique flotte : y=${b.y}", b.y < 0.20f)
        assertTrue("la brique ne s'immobilise pas : v=${b.vy}", abs(b.vy) < 0.05f)
        assertTrue("la brique pivote sans raison : ${b.angle}", abs(b.angle) < 0.08f)
    }

    @Test
    fun `une grosse brique bien centree sur une petite tient debout`() {
        val world = worldWithGround()
        // Petite brique étroite de 2 kg, puis 25 kg large posés pile au centre.
        val small = box(0.075f, 0.12f, 2f, 0f, 0.12f)
        val heavy = box(0.25f, 0.11f, 25f, 0f, 0.35f)
        world.add(small)
        world.add(heavy)

        world.simulate(4f)

        assertTrue("la pile centrée bascule : ${heavy.angle}", abs(heavy.angle) < 0.25f)
        assertTrue("la grosse brique est tombée : y=${heavy.y}", heavy.y > 0.25f)
    }

    @Test
    fun `une grosse brique decalee sur une petite bascule`() {
        val world = worldWithGround()
        // Même duo, mais le centre de gravité des 25 kg tombe hors de l'appui.
        val small = box(0.075f, 0.12f, 2f, 0f, 0.12f)
        val heavy = box(0.25f, 0.11f, 25f, 0.22f, 0.36f)
        world.add(small)
        world.add(heavy)

        world.simulate(4f)

        assertTrue(
            "la pile déséquilibrée reste en l'air : y=${heavy.y}, angle=${heavy.angle}",
            heavy.y < 0.25f || abs(heavy.angle) > 0.3f
        )
    }

    @Test
    fun `une pile de briques reste stable`() {
        val world = worldWithGround()
        val boxes = (0 until 4).map { i ->
            box(0.18f, 0.09f, 6f, 0f, 0.09f + i * 0.185f).also { world.add(it) }
        }

        world.simulate(4f)

        for ((i, b) in boxes.withIndex()) {
            assertTrue("brique $i a glissé : x=${b.x}", abs(b.x) < 0.06f)
            assertTrue("brique $i a pivoté : ${b.angle}", abs(b.angle) < 0.2f)
        }
    }

    // ───────────────────────────── Disques ─────────────────────────────

    @Test
    fun `un disque pose sur le sol sy immobilise sans senfoncer`() {
        val world = worldWithGround()
        val d = disc(0.12f, 4f, 0f, 0.8f)
        world.add(d)

        world.simulate(3f)

        assertTrue("le disque traverse le sol : y=${d.y}", d.y > 0.10f)
        assertTrue("le disque flotte : y=${d.y}", d.y < 0.14f)
        assertTrue("le disque ne s'arrête pas : vy=${d.vy}", abs(d.vy) < 0.05f)
    }

    @Test
    fun `un disque pose sur une pente roule vers le bas`() {
        val world = PhysWorld()
        // Sol incliné qui descend vers la gauche.
        val slope = PhysBody(4f, 0.4f, 0f).apply {
            x = 0f
            y = -0.4f
            angle = 0.25f
            lockPosition = true
            lockRotation = true
            friction = 0.9f
            refreshMass()
        }
        world.add(slope)
        val d = disc(0.15f, 3f, 0f, 0.35f).apply { friction = 0.9f }
        world.add(d)

        world.simulate(1.5f)

        assertTrue("le disque ne descend pas la pente : x=${d.x}", d.x < -0.3f)
        // Rouler sans glisser, c'est vx + oméga·r ≈ 0 : le point de contact est immobile.
        val slip = d.vx + d.omega * d.radius
        assertTrue("le disque glisse au lieu de rouler : glissement=$slip", abs(slip) < 0.35f)
        assertTrue("le disque tourne à l'envers : oméga=${d.omega}", d.omega > 0.5f)
    }

    @Test
    fun `une caisse posee sur le sol ne roule pas`() {
        // Contre-épreuve du test précédent : la boîte, elle, doit rester sur place.
        val world = worldWithGround()
        val b = box(0.15f, 0.15f, 3f, 0f, 0.16f)
        world.add(b)

        world.simulate(2f)

        assertTrue("la caisse dérive : x=${b.x}", abs(b.x) < 0.03f)
    }

    // ───────────────────────────── Rebond ─────────────────────────────

    @Test
    fun `une balle elastique rebondit et une balle molle non`() {
        fun maxReboundHeight(restitution: Float): Float {
            val world = worldWithGround()
            val d = disc(0.1f, 2f, 0f, 1.0f).apply { this.restitution = restitution }
            world.add(d)
            var touched = false
            var best = 0f
            val dt = 1f / 240f
            repeat((3f / dt).toInt()) {
                world.stepFrame(dt)
                if (d.y < 0.12f) touched = true
                if (touched && d.y > best) best = d.y
            }
            return best
        }

        val soft = maxReboundHeight(0f)
        val bouncy = maxReboundHeight(0.6f)

        assertTrue("la balle molle rebondit quand même : $soft", soft < 0.16f)
        // Théorie : la balle remonte à e² fois sa hauteur, soit 0,36 m ici.
        assertTrue("la balle élastique ne rebondit pas assez : $bouncy", bouncy > 0.22f)
        assertTrue("la balle élastique rebondit trop haut : $bouncy", bouncy < 0.55f)
    }

    // ──────────────────── Sous-pas : pas de traversée ────────────────────

    @Test
    fun `un boulet rapide ne traverse pas une planche fine`() {
        fun launch(adaptive: Boolean): PhysBody {
            val world = PhysWorld()
            world.gravity = 0f
            // Une planche verticale de 8 cm d'épaisseur, plantée en x = 2.
            val wall = PhysBody(0.04f, 1f, 0f).apply {
                x = 2f
                y = 0f
                lockPosition = true
                lockRotation = true
                refreshMass()
            }
            world.add(wall)
            // Départ décalé pour qu'à 40 m/s et un seul pas de 1/60 s, aucune des
            // positions testées ne tombe sur le mur : le boulet passe à travers.
            val ball = disc(0.08f, 5f, -0.3f, 0f).apply { vx = 40f }
            world.add(ball)
            val dt = 1f / 60f
            repeat(12) { if (adaptive) world.stepFrame(dt) else world.step(dt) }
            return ball
        }

        val naive = launch(adaptive = false)
        val guarded = launch(adaptive = true)

        // Le pas unique laisse passer le boulet : c'est précisément pour ça que
        // les sous-pas existent. Si ce jour-là il ne traverse plus, tant mieux,
        // mais l'essentiel est la ligne suivante.
        assertTrue(
            "avec les sous-pas, le boulet traverse le mur : x=${guarded.x}",
            guarded.x < 1.9f
        )
        assertTrue(
            "le boulet n'a pas été arrêté par le mur : vx=${guarded.vx}",
            guarded.vx < 1f
        )
        assertTrue(
            "le test ne prouve rien : sans sous-pas le boulet ne traverse pas non plus (x=${naive.x})",
            naive.x > 2.5f
        )
    }

    @Test
    fun `un monde au repos ne coute quun seul sous pas`() {
        val world = worldWithGround()
        world.add(box(0.2f, 0.15f, 10f, 0f, 0.16f))
        world.simulate(2f)

        assertTrue("un monde immobile se subdivise pour rien", world.subStepsFor(1f / 60f) == 1)
        assertTrue("le monde ne se met pas au repos", world.isAtRest())
    }

    // ───────────────────────────── Dégâts ─────────────────────────────

    @Test
    fun `une caisse au repos naccumule aucun degat mais un boulet lui en inflige`() {
        val world = worldWithGround()
        val crate = box(0.2f, 0.2f, 8f, 0f, 0.21f)
        world.add(crate)

        world.simulate(2f)
        world.clearImpacts()
        world.simulate(1f)
        val atRest = crate.impactAccum

        // Même caisse, mais percutée par un boulet lancé fort.
        val ball = disc(0.1f, 6f, -1.5f, 0.25f).apply { vx = 18f }
        world.add(ball)
        world.simulate(1f)
        val hit = crate.impactAccum

        assertTrue("une caisse posée s'abîme toute seule : $atRest", atRest < 0.01f)
        assertTrue("le choc du boulet n'est pas comptabilisé : $hit", hit > 5f)
    }
}
