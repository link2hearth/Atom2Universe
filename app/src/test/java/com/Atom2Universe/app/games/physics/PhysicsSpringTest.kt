package com.Atom2Universe.app.games.physics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Le ressort-amortisseur : ce qu'il rend, et ce qu'il ne fait jamais.
 *
 * Deux questions comptent, et une seule est négociable. La raideur qu'il tient sans
 * diverger doit être quelconque — sinon il ne sert qu'aux tremplins mous. L'énergie,
 * elle, ne se négocie pas : il en range et il en rend, il n'en crée pas.
 */
class PhysicsSpringTest {

    /** Un monde vide, sans pesanteur ni air : on n'y mesure que le ressort. */
    private fun bench(): PhysWorld = PhysWorld().apply {
        gravity = 0f
        linearDamping = 0f
        angularDamping = 0f
    }

    private fun distance(a: PhysBody, b: PhysBody) = hypot(b.x - a.x, b.y - a.y)

    @Test
    fun `une masse suspendue se pose a l allongement attendu`() {
        // Le ressort doit donner le bon nombre, pas seulement une allure plausible :
        // à l'équilibre, l'allongement vaut exactement `mg / k`.
        val w = PhysWorld()
        w.linearDamping = 0f
        val masse = 2f
        val k = 200f
        val poteau = anchorPost(0f, 5f)
        val charge = box(0.2f, 0.2f, masse, 0f, 4f)
        w.add(poteau)
        w.add(charge)
        val ressort = SpringJoint.between(poteau, 0f, 5f, charge, 0f, 4f, k,
            SpringJoint.criticalDamping(k, masse))
        w.addJoint(ressort)

        w.simulate(6f)

        val attendu = 1f + masse * PhysicsConstants.STANDARD_GRAVITY / k
        assertEquals("mauvais allongement à l'équilibre",
            attendu, distance(poteau, charge), 0.005f)
    }

    @Test
    fun `un ressort ne cree jamais d energie`() {
        // Le garde-fou. On lâche une masse écartée d'un mètre, sans amortissement, et
        // on surveille pendant seize oscillations : le total « cinétique + élastique »
        // ne doit jamais repasser au-dessus de ce qu'on a mis au départ.
        val w = bench()
        val poteau = anchorPost(0f, 0f)
        val masse = 1f
        val k = 100f
        val charge = box(0.1f, 0.1f, masse, 2f, 0f)
        w.add(poteau)
        w.add(charge)
        val ressort = SpringJoint(poteau, charge).apply {
            restLength = 1f
            stiffness = k
            damping = 0f
        }
        w.addJoint(ressort)

        fun total(): Float {
            val x = distance(poteau, charge) - 1f
            val v2 = charge.vx * charge.vx + charge.vy * charge.vy
            return 0.5f * masse * v2 + 0.5f * k * x * x
        }

        val depart = total()
        assertEquals("le banc d'essai ne part pas de 50 J", 50f, depart, 0.5f)
        var pire = 0f
        repeat(1200) {
            w.stepFrame(1f / 120f)
            val e = total()
            if (e > pire) pire = e
        }
        assertTrue("le ressort a créé de l'énergie : $pire J pour $depart J au départ",
            pire <= depart * 1.001f)
    }

    @Test
    fun `un ressort rend l essentiel de ce qu on lui a confie`() {
        // L'autre moitié de la question : ne pas créer d'énergie est facile si on n'en
        // rend aucune. Un ressort armé puis relâché doit envoyer sa charge, et sur le
        // quart de période qui fait la détente il ne perd presque rien.
        val w = bench()
        val poteau = anchorPost(0f, 0f)
        val masse = 1f
        val k = 100f
        val charge = box(0.1f, 0.1f, masse, 2f, 0f)
        w.add(poteau)
        w.add(charge)
        w.addJoint(SpringJoint(poteau, charge).apply {
            restLength = 1f
            stiffness = k
            damping = 0f
        })

        // Quart de période : T = 2π√(m/k), donc la détente complète prend T/4.
        val quart = 0.25f * 2f * Math.PI.toFloat() * sqrt(masse / k)
        w.simulate(quart)

        val cinetique = 0.5f * masse * (charge.vx * charge.vx + charge.vy * charge.vy)
        val range = 0.5f * k * 1f * 1f
        assertTrue("le ressort n'a rendu que $cinetique J sur $range J",
            cinetique > range * 0.85f)
    }

    @Test
    fun `la perte d un ressort sans amortissement reste celle qu on a mesuree`() {
        // Le pendant du garde-fou : un ressort qui ne crée rien pourrait très bien ne
        // rien rendre, et le premier test ne le verrait pas. Ces bornes sont les valeurs
        // mesurées, écrites en tête de [SpringJoint] — elles sont là pour qu'un
        // changement du solveur qui les dégraderait se voie tout de suite.
        fun reste(seconds: Float): Float {
            val w = bench()
            val poteau = anchorPost(0f, 0f)
            val charge = box(0.1f, 0.1f, 1f, 2f, 0f)
            w.add(poteau)
            w.add(charge)
            w.addJoint(SpringJoint(poteau, charge).apply {
                restLength = 1f
                stiffness = 100f
                damping = 0f
            })
            w.simulate(seconds)
            val x = distance(poteau, charge) - 1f
            val v2 = charge.vx * charge.vx + charge.vy * charge.vy
            return (0.5f * v2 + 0.5f * 100f * x * x) / 50f
        }

        val detente = reste(0.157f)
        assertTrue("la détente ne rend plus que ${detente * 100} %", detente > 0.85f)
        assertTrue("la détente rend plus qu'elle n'a reçu : ${detente * 100} %",
            detente <= 1f)
        val uneSeconde = reste(1f)
        assertTrue("la perte sur une seconde s'est aggravée : ${uneSeconde * 100} %",
            uneSeconde > 0.42f)
    }

    @Test
    fun `un ressort tres raide ne fait pas exploser la machine`() {
        // C'est ce qui justifie une contrainte molle plutôt qu'une force appliquée à la
        // main : à 10 MN/m dans un pas de 1/120 s, le calcul explicite enverrait la
        // charge à l'infini en trois pas. Celui-ci n'a pas de seuil.
        val w = bench()
        val poteau = anchorPost(0f, 0f)
        val charge = box(0.1f, 0.1f, 1f, 1.2f, 0f)
        w.add(poteau)
        w.add(charge)
        w.addJoint(SpringJoint(poteau, charge).apply {
            restLength = 1f
            stiffness = 1e7f
            damping = 1e4f
        })

        repeat(1200) {
            w.stepFrame(1f / 120f)
            assertTrue("la charge est partie dans le décor : ${charge.x}",
                charge.x.isFinite() && abs(charge.x) < 100f)
        }
        assertEquals("un ressort très raide devrait tenir sa longueur",
            1f, distance(poteau, charge), 0.02f)
    }

    @Test
    fun `l amortissement finit par tout arreter`() {
        val w = bench()
        val poteau = anchorPost(0f, 0f)
        val charge = box(0.1f, 0.1f, 1f, 2f, 0f)
        w.add(poteau)
        w.add(charge)
        w.addJoint(SpringJoint(poteau, charge).apply {
            restLength = 1f
            stiffness = 100f
            damping = SpringJoint.criticalDamping(100f, 1f)
        })

        w.simulate(5f)
        assertEquals("la charge ne s'est pas posée au repos",
            1f, distance(poteau, charge), 0.01f)
        assertTrue("la charge bouge encore", abs(charge.vx) < 0.01f)
    }

    @Test
    fun `un amortisseur pur ne pousse pas`() {
        // Raideur nulle : il ne reste que la dissipation. La charge lancée doit
        // s'arrêter, et surtout ne pas être ramenée vers sa longueur de repos.
        val w = bench()
        val poteau = anchorPost(0f, 0f)
        val charge = box(0.1f, 0.1f, 1f, 2f, 0f)
        charge.vx = 5f
        w.add(poteau)
        w.add(charge)
        w.addJoint(SpringJoint(poteau, charge).apply {
            restLength = 1f
            stiffness = 0f
            damping = 50f
        })

        w.simulate(5f)
        assertTrue("l'amortisseur n'a pas freiné : vx = ${charge.vx}",
            abs(charge.vx) < 0.05f)
        assertTrue("l'amortisseur a ramené la charge comme un ressort",
            distance(poteau, charge) > 2f)
    }

    @Test
    fun `une liaison sans raideur ni amortissement ne tient rien`() {
        // Le piège du calcul : les deux termes à zéro annulent la souplesse, et la
        // liaison redeviendrait une barre rigide si on ne l'écartait pas explicitement.
        val w = bench()
        val poteau = anchorPost(0f, 0f)
        val charge = box(0.1f, 0.1f, 1f, 2f, 0f)
        charge.vx = 3f
        w.add(poteau)
        w.add(charge)
        w.addJoint(SpringJoint(poteau, charge).apply {
            restLength = 1f
            stiffness = 0f
            damping = 0f
        })

        w.simulate(1f)
        // La vitesse est le bon témoin : le nombre exact de pas que `simulate` exécute
        // dépend d'un arrondi de flottant, la position aussi, mais une liaison qui ne
        // tient rien n'a touché à aucune vitesse, au bit près.
        assertEquals("la liaison molle a freiné la charge", 3f, charge.vx, 0f)
        assertTrue("la liaison molle s'est comportée comme une barre",
            distance(poteau, charge) > 4.9f)
    }

    @Test
    fun `la butee d effort empeche le ressort de devenir une barre`() {
        val w = PhysWorld()
        w.linearDamping = 0f
        val poteau = anchorPost(0f, 5f)
        val charge = box(0.2f, 0.2f, 50f, 0f, 4f)
        w.add(poteau)
        w.add(charge)
        val ressort = SpringJoint.between(poteau, 0f, 5f, charge, 0f, 4f, 1e6f, 5e3f).apply {
            maxForce = 100f
        }
        w.addJoint(ressort)

        w.simulate(2f)
        assertTrue("la butée n'a pas été atteinte", ressort.saturated)
        assertTrue("le ressort a tiré plus fort que sa butée : ${ressort.force} N",
            abs(ressort.force) < 110f)
        // 50 kg retenus par 100 N seulement : la charge doit descendre pour de bon.
        assertTrue("la butée n'a rien laissé passer", charge.y < 3f)
    }

    @Test
    fun `la precharge decale la longueur au repos`() {
        val w = bench()
        val poteau = anchorPost(0f, 0f)
        val charge = box(0.1f, 0.1f, 1f, 1f, 0f)
        w.add(poteau)
        w.add(charge)
        w.addJoint(SpringJoint(poteau, charge).apply {
            restLength = 1f
            stiffness = 100f
            preload = 50f
            damping = SpringJoint.criticalDamping(100f, 1f)
        })

        w.simulate(5f)
        // 50 N de précharge sur 100 N/m : le repos se déplace d'un demi-mètre.
        assertEquals("la précharge n'a pas décalé le repos",
            1.5f, distance(poteau, charge), 0.01f)
    }
}
