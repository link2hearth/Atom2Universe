package com.Atom2Universe.app.games.trebuchet

import com.Atom2Universe.app.games.physics.PhysBody
import com.Atom2Universe.app.games.physics.PhysWorld
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Vérifie le champ de cibles : une construction posée doit tenir toute seule, un
 * boulet doit faire des dégâts à la mesure de ce qu'il emporte vraiment, et le
 * ménage ne doit jamais gagner la partie à la place du joueur.
 *
 * Les chiffres de ténacité de [Material] ne sont pas des goûts personnels : ils sont
 * calés sur ce banc, contre le vrai boulet du jeu.
 */
class TrebuchetTargetTest {

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
                x = 0f
                y = -1f
                lockPosition = true
                lockRotation = true
                friction = 0.7f
                category = TrebuchetCategory.GROUND
                refreshMass()
            }
        )
    }

    /** Le boulet du jeu, lancé horizontalement, comme s'il arrivait de la machine. */
    private fun PhysWorld.fireBall(x: Float, y: Float, speed: Float): PhysBody {
        val b = PhysBody.circle(TrebuchetRules.BALL_RADIUS, TrebuchetRules.BALL_MASS).apply {
            this.x = x
            this.y = y
            vx = speed
            friction = 0.2f
            restitution = 0.1f
            category = TrebuchetCategory.BALL
            collidesWith = TrebuchetCategory.BALL_FREE_MASK
        }
        add(b)
        return b
    }

    /**
     * Laisse la construction se poser et **s'armer** avant qu'on lui tire dessus.
     *
     * Ce n'est pas une précaution de test, c'est la règle du champ de cibles : les chocs
     * ne comptent qu'une fois la construction restée immobile un moment, sinon son
     * propre réveil lui ferait des dégâts. Dans une partie, la cible est debout depuis
     * longtemps quand le premier boulet arrive ; ici, il faut le dire.
     */
    private fun arm(w: PhysWorld, f: TargetField) {
        run(w, f, TargetRules.ARM_CALM + 0.6f)
        assertTrue("la cible ne s'est pas armée", f.armed)
    }

    private fun run(w: PhysWorld, f: TargetField, seconds: Float, dt: Float = 1f / 60f) {
        repeat((seconds / dt).toInt()) {
            w.stepFrame(dt)
            f.update(dt)
        }
    }

    /** Un poteau de charpente : 0,30 m de côté, 2,50 m de haut. */
    private fun woodPost(x: Float) = Block.laid(Material.WOOD, x - 0.15f, 0f, 0.3f, 2.5f)

    /**
     * Une assise de rempart : 1,20 × 0,50 m de pierre de taille, une tonne et demie,
     * moins le joint de maçonnerie.
     */
    private fun stoneCourse(left: Float, bottom: Float, width: Float = 1.2f) =
        Block.laid(
            Material.STONE, left, bottom,
            width - TargetRules.JOINT, 0.5f - TargetRules.JOINT
        )

    /**
     * Un petit mur **en appareil** : 2,40 m de large, joints décalés d'une assise à
     * l'autre par des demi-pierres aux extrémités, et tassé avant d'être rendu.
     *
     * Deux leçons du banc tiennent dans ces quelques lignes. Le décalage se fait en
     * coupant les pierres de bout, jamais en glissant toute l'assise : une assise
     * glissée déborde dans le vide et le mur s'écroule tout seul. Et les pierres se
     * posent avec un joint, sans quoi leurs faces confondues rendent la normale de
     * contact ambiguë et le mur se broie sous son propre poids.
     */
    private fun wall(x: Float, courses: Int): Structure {
        val blocks = ArrayList<Block>()
        for (r in 0 until courses) {
            val y = r * 0.5f
            if (r % 2 == 0) {
                blocks += stoneCourse(x, y)
                blocks += stoneCourse(x + 1.2f, y)
            } else {
                blocks += stoneCourse(x, y, 0.6f)
                blocks += stoneCourse(x + 0.6f, y)
                blocks += stoneCourse(x + 1.8f, y, 0.6f)
            }
        }
        return TargetField.settle(Structure(blocks, "mur"))
    }

    // ── Ce qui ne doit jamais arriver ─────────────────────────────────────────

    @Test
    fun `une construction posee ne s abime pas toute seule`() {
        val w = world()
        val f = TargetField(w)
        val s = wall(20f, 8)
        f.load(s)
        val h0 = f.ruinHeight()

        run(w, f, 4f)

        assertTrue("des pierres ont disparu : ${f.pieces.size}", f.pieces.size == s.blocks.size)
        assertEquals("le mur s'est abîmé tout seul", 0f, f.brokenRatio, 1e-4f)
        for (p in f.pieces) {
            assertTrue("pierre usée sans avoir été touchée : ${p.wear}", p.wear < 0.01f)
        }
        assertTrue(
            "le mur s'est tassé de trop : $h0 puis ${f.ruinHeight()}",
            abs(f.ruinHeight() - h0) < 0.1f
        )
    }

    // ── Ce que le boulet fait vraiment ────────────────────────────────────────

    @Test
    fun `un boulet emporte un poteau de bois d un seul coup`() {
        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(woodPost(20f)), "poteau"))
        val hp = f.pieces[0].maxHp
        arm(w, f)

        w.fireBall(14f, 1.2f, 100f)
        run(w, f, 1.5f)

        val standing = f.pieces.count { !it.debris }
        println("POTEAU pv=$hp reste=$standing débris=${f.pieces.count { it.debris }}")
        assertTrue("le poteau a tenu le coup", standing == 0)
        assertTrue("il n'a laissé aucun morceau", f.pieces.any { it.debris })
    }

    @Test
    fun `une assise de rempart encaisse un coup mais pas deux`() {
        // On ne casse pas une pierre d'une tonne et demie avec un caillou de douze
        // kilos : on l'entame, et il faut y revenir. C'est le cœur du réglage.
        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(stoneCourse(20f, 0f)), "assise"))
        arm(w, f)

        w.fireBall(14f, 0.25f, 100f)
        run(w, f, 1.2f)

        val after = f.pieces.firstOrNull { !it.debris }
        println("ASSISE après un coup : usure=${after?.wear} reste=${f.pieces.size}")
        assertTrue("l'assise a été pulvérisée d'un seul coup", after != null)
        assertTrue("le coup n'a rien fait du tout : ${after!!.wear}", after.wear > 0.2f)
    }

    @Test
    fun `le fer ne casse jamais`() {
        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(Block.laid(Material.IRON, 20f, 0f, 0.8f, 0.8f)), "fer"))
        arm(w, f)

        w.fireBall(14f, 0.4f, 150f)
        run(w, f, 1.5f)

        assertTrue("le fer a cassé", f.pieces.any { !it.debris })
        assertEquals(0f, f.pieces.first { !it.debris }.wear, 1e-4f)
    }

    // ── La rupture ────────────────────────────────────────────────────────────

    @Test
    fun `un bloc compose eclate en ses propres parties`() {
        // Le plan de fracture était déjà écrit le jour où on a dessiné la pierre.
        val w = world()
        val f = TargetField(w)
        // Une assise chaînée de la taille d'une vraie : 1,20 m de large, deux
        // chaînages d'angle et un remplissage. Une tonne trois, 97 kJ de vie : un coup
        // franc à 150 m/s en apporte 135, donc elle part d'un seul.
        val chained = Block.compound(Material.STONE, 20f, 0.25f) {
            box(0.15f, 0.25f, -0.45f, 0f)   // chaînage gauche
            box(0.30f, 0.22f, 0f, 0f)       // remplissage
            box(0.15f, 0.25f, 0.45f, 0f)    // chaînage droit
        }
        f.load(Structure(listOf(chained), "assise chaînée"))
        arm(w, f)

        w.fireBall(14f, 0.25f, 150f)
        run(w, f, 1.5f)

        val shards = f.pieces.count { it.debris }
        println("COMPOSÉ débris=$shards")
        assertTrue("le bloc chaîné n'a pas éclaté en trois : $shards", shards == 3)
    }

    @Test
    fun `les debris ne se cassent plus`() {
        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(stoneCourse(20f, 0f)), "assise"))
        arm(w, f)
        // Deux coups pour venir à bout d'une tonne et demie : c'est le réglage voulu.
        repeat(2) {
            w.fireBall(14f, 0.25f, 150f)
            run(w, f, 1.2f)
        }
        val shards = f.pieces.filter { it.debris }
        assertTrue("rien n'a éclaté", shards.isNotEmpty())

        // On tire encore trois fois dans le tas : il doit s'entasser, pas s'évaporer.
        repeat(3) {
            w.fireBall(14f, 0.25f, 150f)
            run(w, f, 1.5f)
        }
        assertTrue(
            "les débris se sont pulvérisés : ${f.pieces.count { it.debris }}",
            f.pieces.count { it.debris } >= shards.size
        )
    }

    @Test
    fun `le budget de debris est tenu`() {
        val w = world()
        val f = TargetField(w)
        f.load(wall(20f, 14))

        // Un souffle énorme, bien plus large que le mur : tout casse d'un coup.
        f.blast(21f, 3f, 4_000_000f, 40f)
        run(w, f, 0.5f)

        val debris = f.pieces.count { it.debris }
        println("BUDGET débris=$debris pour un plafond de ${TargetRules.MAX_DEBRIS}")
        assertTrue("budget de débris dépassé : $debris", debris <= TargetRules.MAX_DEBRIS)
    }

    // ── L'objectif ────────────────────────────────────────────────────────────

    @Test
    fun `la ligne de ruine se franchit quand tout est a terre`() {
        val w = world()
        val f = TargetField(w)
        // Douze assises : la ligne de ruine tombe alors à 1,19 m, au-dessus de la
        // hauteur qu'un tas de pierres de cinquante centimètres peut atteindre.
        val s = wall(20f, 12)
        f.load(s)

        assertTrue("un mur intact serait déjà rasé", !f.cleared)
        assertEquals("la ligne de ruine n'est pas le cinquième", 0.2f * s.baseHeight, f.ruinLine, 1e-3f)
        assertTrue("le mur part déjà gagné : ${f.progress}", f.progress < 0.05f)

        // On rase tout à l'explosif, faute de mieux dans un test.
        repeat(8) {
            f.blast(21f, 2f, 600_000f, 30f)
            run(w, f, 1f)
        }
        run(w, f, 3f)

        println("RUINE hauteur=${f.ruinHeight()} ligne=${f.ruinLine} rasé=${f.cleared}")
        assertTrue("le mur n'est pas descendu sous la ligne : ${f.ruinHeight()} > ${f.ruinLine}", f.cleared)
        assertEquals(1f, f.progress, 1e-3f)
    }

    @Test
    fun `ce qui est expedie hors de l emprise ne compte plus`() {
        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(stoneCourse(20f, 0f)), "assise"))
        val piece = f.pieces[0]

        // On téléporte la pierre très loin, debout : elle ne fait plus partie du château.
        piece.body.x = 20f + TargetRules.FOOTPRINT_MARGIN + 30f
        piece.body.y = 8f
        w.forgetContacts(piece.body)

        assertEquals("un caillou dans un champ compte encore", 0f, f.ruinHeight(), 1e-3f)
    }

    @Test
    fun `le menage ne change jamais la hauteur de ruine`() {
        // Le ramassage des petits débris endormis est là pour le confort du moteur.
        // S'il pouvait faire baisser la silhouette, il gagnerait la partie à la place
        // du joueur — c'est la seule chose qu'on lui interdit absolument.
        val w = world()
        val f = TargetField(w)
        f.load(wall(20f, 12))
        repeat(4) {
            f.blast(21f, 2f, 400_000f, 25f)
            run(w, f, 1f)
        }
        run(w, f, 4f)

        val avant = f.ruinHeight()
        val piecesAvant = f.pieces.size
        run(w, f, TargetRules.DEBRIS_LIFETIME + 4f)

        println("MÉNAGE hauteur $avant -> ${f.ruinHeight()}, pièces $piecesAvant -> ${f.pieces.size}")
        assertEquals("le ménage a fait baisser la silhouette", avant, f.ruinHeight(), 0.05f)
    }

    // ── Le souffle, pour plus tard ────────────────────────────────────────────

    @Test
    fun `un souffle ne casse que dans son rayon`() {
        val w = world()
        val f = TargetField(w)
        f.load(
            Structure(
                listOf(woodPost(20f), woodPost(24f), woodPost(40f)),
                "trois poteaux"
            )
        )

        f.blast(20f, 1f, 60_000f, 6f)
        run(w, f, 0.5f)

        val loin = f.pieces.firstOrNull { !it.debris && it.body.x > 35f }
        println("SOUFFLE reste=${f.pieces.count { !it.debris }} loin=${loin?.wear}")
        assertTrue("le poteau à vingt mètres a souffert", loin != null && loin.wear < 1e-4f)
        assertTrue("le poteau au centre a survécu au souffle", f.pieces.none { !it.debris && it.body.x < 22f })
    }

    // ── Ce qui doit rester reproductible ──────────────────────────────────────

    @Test
    fun `une meme graine donne un meme resultat`() {
        fun play(seed: Long): List<Triple<Float, Float, Float>> {
            val w = world()
            val f = TargetField(w, seed)
            f.load(wall(20f, 6))
            w.fireBall(14f, 1.4f, 150f)
            run(w, f, 3f)
            return f.pieces.map { Triple(it.body.x, it.body.y, it.body.angle) }
        }

        val a = play(7L)
        val b = play(7L)
        assertEquals("le même tir ne rend pas le même champ", a.size, b.size)
        for (i in a.indices) assertEquals(a[i], b[i])
    }

    // ── La validation d'une construction ──────────────────────────────────────

    @Test
    fun `la validation repere un socle trop haut et un chevauchement`() {
        val haut = Structure(
            listOf(
                Block.laid(Material.STONE, 0f, 0f, 1f, 3f, Role.FOUNDATION),
                Block.laid(Material.STONE, 0f, 3f, 1f, 1f)
            ),
            "socle abusif"
        )
        assertTrue(
            "un socle au-dessus de la ligne de ruine passe la validation",
            haut.problems().any { it.contains("socle") }
        )

        val melange = Structure(
            listOf(
                Block.laid(Material.STONE, 0f, 0f, 2f, 1f),
                Block.laid(Material.STONE, 0.5f, 0.2f, 2f, 1f)
            ),
            "pierres mêlées"
        )
        assertTrue(
            "deux pierres l'une dans l'autre passent la validation",
            melange.problems().any { it.contains("chevauchent") }
        )

        // Une seule pierre couchée à plat dépasse déjà la ligne de ruine : ce
        // niveau-là ne peut être gagné par personne, quoi qu'on fasse.
        val trop_bas = Structure(
            listOf(Block.laid(Material.STONE, 20f, 0f, 2f, 1.2f)),
            "borne trapue"
        )
        assertTrue(
            "un niveau impossible passe la validation : ${trop_bas.problems()}",
            trop_bas.problems().any { it.contains("trop basse") }
        )

        assertTrue("un mur honnête est refusé", wall(20f, 12).problems().isEmpty())
    }

    @Test
    fun `le tassement pose la construction et juge si elle tenait debout`() {
        // Un mur correct ne doit quasiment pas bouger pendant le tassement.
        val brut = ArrayList<Block>()
        for (r in 0 until 8) {
            val y = r * 0.5f
            if (r % 2 == 0) {
                brut += stoneCourse(20f, y)
                brut += stoneCourse(21.2f, y)
            } else {
                brut += stoneCourse(20f, y, 0.6f)
                brut += stoneCourse(20.6f, y)
                brut += stoneCourse(21.8f, y, 0.6f)
            }
        }
        val avant = Structure(brut, "mur")
        val apres = TargetField.settle(avant)
        val bouge = TargetField.drift(avant, apres)
        println("TASSEMENT mur : ${"%.3f".format(bouge)} m")
        assertTrue("un mur correct a trop bougé : $bouge m", bouge < TargetRules.SETTLE_TOLERANCE)

        // Une pile en porte-à-faux, elle, doit être dénoncée par le tassement.
        val bancal = Structure(
            (0 until 6).map { stoneCourse(20f + it * 0.9f, it * 0.5f) },
            "escalier bancal"
        )
        val tombe = TargetField.drift(bancal, TargetField.settle(bancal))
        println("TASSEMENT bancal : ${"%.3f".format(tombe)} m")
        assertTrue("le tassement n'a pas vu que ça s'écroulait", tombe > TargetRules.SETTLE_TOLERANCE)
    }

    // ── Ce que le joueur voit ─────────────────────────────────────────────────

    @Test
    fun `une pierre fragilisee se craquelle par paliers`() {
        // Sans ce retour, un tir qui enlève la moitié de la vie d'une assise ressemble
        // exactement à un tir qui n'a rien fait.
        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(stoneCourse(20f, 0f)), "assise"))
        val p = f.pieces[0]
        assertEquals("une pierre neuve est déjà fêlée", 0, p.crackLevel)
        arm(w, f)

        w.fireBall(14f, 0.25f, 100f)
        run(w, f, 1.2f)

        val after = f.pieces.first { !it.debris }
        println("CRAQUELURE usure=${after.wear} niveau=${after.crackLevel}")
        assertTrue("un coup franc ne laisse aucune trace", after.crackLevel >= 2)
        assertTrue("la pierre est déjà au dernier palier", after.crackLevel <= 3)
    }

    @Test
    fun `les paliers de craquelure suivent l usure`() {
        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(stoneCourse(20f, 0f)), "assise"))
        val p = f.pieces[0]
        val niveaux = floatArrayOf(0f, 0.2f, 0.5f, 0.9f).map { usure ->
            p.hp = p.maxHp * (1f - usure)
            p.crackLevel
        }
        assertEquals("les paliers ne se suivent pas : $niveaux", listOf(0, 1, 2, 3), niveaux)
    }

    // ── Le filet de sécurité ──────────────────────────────────────────────────

    @Test
    fun `une construction qui ne s immobilise jamais finit quand meme par etre vulnerable`() {
        // Sinon un château qui tremble un peu serait invulnérable pour toujours, et
        // rien à l'écran ne dirait pourquoi.
        val w = world()
        val f = TargetField(w)
        // Une pierre en équilibre sur un galet : ça n'arrête jamais complètement de
        // bouger, et pourtant ça doit rester cassable.
        f.load(
            Structure(
                listOf(
                    Block.circle(Material.STONE, 20f, 0.3f, 0.3f),
                    Block.laid(Material.STONE, 19.4f, 0.6f, 1.2f, 0.4f)
                ),
                "pierre sur galet"
            )
        )
        assertTrue("armé avant même d'avoir simulé", !f.armed)
        run(w, f, TargetRules.ARM_TIMEOUT + 1f)
        assertTrue("la construction n'est jamais devenue vulnérable", f.armed)
    }

    // ── La profondeur d'empilement ────────────────────────────────────────────

    @Test
    fun `la validation refuse une pile trop profonde et accepte la meme en blocs composes`() {
        // Le chiffre qui décide si une construction tient debout n'est pas sa hauteur,
        // c'est le nombre de corps superposés. Mesuré : un mur de 24 m monté en 48 corps
        // s'affaisse de quatre mètres, le même en 12 corps de quatre pierres ne bouge pas.
        val profonde = Structure(
            (0 until 24).map { stoneCourse(20f, it * 0.5f) },
            "pile profonde"
        )
        assertEquals(24, profonde.deepestStack())
        assertTrue(
            "une pile de 24 corps passe la validation",
            profonde.problems().any { it.contains("superposés") }
        )

        // Les mêmes 24 pierres, regroupées quatre par quatre : six corps.
        val groupee = Structure(
            (0 until 6).map { g ->
                Block.compound(Material.STONE, 20.6f, g * 2f + 1f) {
                    for (k in 0 until 4) {
                        box(0.6f - TargetRules.JOINT, 0.25f - TargetRules.JOINT, 0f, (k - 1.5f) * 0.5f)
                    }
                }
            },
            "pile groupée"
        )
        assertEquals(6, groupee.deepestStack())
        assertTrue(
            "la même pile en blocs composés est refusée : ${groupee.problems()}",
            groupee.problems().none { it.contains("superposés") }
        )
    }

    @Test
    fun `un bloc compose tombe la ou on l a dessine`() {
        // Le recentrage sur le centre de masse est fait des deux côtés — description
        // et moteur. Si les deux ne coïncidaient pas, tout serait décalé à l'écran.
        val b = Block.compound(Material.STONE, 10f, 2f) {
            box(0.2f, 0.4f, -0.7f, 0f)
            box(0.5f, 0.35f, 0f, 0f)
            box(0.2f, 0.4f, 0.7f, 0f)
        }
        assertEquals(10f, b.x, 1e-3f)
        assertEquals(2f, b.y, 1e-3f)

        val w = world()
        val f = TargetField(w)
        f.load(Structure(listOf(b), "assise chaînée"))
        assertEquals(10f, f.pieces[0].body.x, 1e-3f)
        assertEquals(2f, f.pieces[0].body.y, 1e-3f)
    }
}
