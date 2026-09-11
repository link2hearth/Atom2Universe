package com.Atom2Universe.app.games.infernale

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le generateur de tableaux.
 *
 * Une seule promesse compte, et elle est dure a tenir : **tout tableau propose a un
 * joueur doit avoir une solution**. Rien n'est plus decourageant qu'un casse-tete
 * insoluble, et rien n'est plus difficile a prouver apres coup. La fabrique s'y prend
 * donc a l'envers — elle construit la solution, la fait tourner, et ne garde que ce
 * qui a gagne.
 */
class InfernaleTableauTest {

    @Test
    fun `toutes les graines donnent un tableau soluble`() {
        // **Le test qui porte tout.** Il monte et fait tourner des machines completes, et
        // il tourne en quelques secondes : c'est le genre de garantie qu'on ne peut se
        // payer que parce que le jeu se simule sans ecran.
        //
        // C'est [Tableaux.genererSurement] qui est interroge, et non [Tableaux.generer],
        // parce que c'est lui que le jeu appelle et lui seul qui promet de toujours
        // rendre quelque chose. Une tentative isolee a parfaitement le droit d'echouer :
        // elle batit une machine au hasard et la refuse si chaque piece n'y est pas
        // indispensable, ce qui est exigeant par construction.
        for (graine in 1L..25L) {
            val tableau = Tableaux.genererSurement(graine)
            val monde = Tableaux.monter(tableau, avecSolution = true)
            monde.derouler(12f)
            assertTrue("la graine $graine a produit un tableau que sa propre solution " +
                "ne gagne pas", monde.gagne)
        }
    }

    @Test
    fun `une tentative isolee aboutit le plus souvent`() {
        // Le garde-fou de cout. [Tableaux.genererSurement] finit toujours par trouver,
        // donc il ne dira jamais que le generateur s'est mis a echouer neuf fois sur
        // dix — il le paiera seulement en secondes d'attente devant le joueur. Ce
        // test-ci mesure le taux brut, et c'est lui qui previent avant que l'ecran de
        // chargement ne s'eternise.
        var trouves = 0
        for (graine in 1L..50L) if (Tableaux.generer(graine) != null) trouves++
        assertTrue("seulement $trouves tableaux sur 50 du premier coup : le generateur " +
            "echoue trop souvent, et chaque echec se paie en attente", trouves >= 30)
    }

    @Test
    fun `la meme graine rend exactement le meme tableau`() {
        // Un tableau se partage par son numero : deux joueurs qui tapent la meme graine
        // doivent avoir le meme casse-tete, sinon le numero ne veut rien dire.
        for (graine in listOf(1L, 7L, 42L, 1234L)) {
            val a = Tableaux.genererSurement(graine)
            val b = Tableaux.genererSurement(graine)
            assertEquals("la graine $graine ne donne pas deux fois la meme bille",
                a.billeX to a.billeY, b.billeX to b.billeY)
            assertEquals("la graine $graine ne donne pas deux fois le meme bouton",
                a.boutonX, b.boutonX, 0f)
            assertEquals("la graine $graine ne donne pas deux fois la meme solution",
                a.solution, b.solution)
        }
    }

    @Test
    fun `deux graines differentes donnent des tableaux differents`() {
        val tableaux = (1L..20L).map { Tableaux.genererSurement(it) }
        val distincts = tableaux.map { it.solution }.toSet()
        assertTrue("le generateur se repete : ${distincts.size} tableaux distincts sur 20",
            distincts.size >= 18)
    }

    @Test
    fun `un tableau vide ne se gagne pas tout seul`() {
        // Le pendant du test precedent, et il est indispensable. Si le decor seul
        // suffisait a declencher le bouton, l'inventaire ne servirait a rien et le
        // « generateur » ne genererait qu'une animation.
        for (graine in 1L..20L) {
            val tableau = Tableaux.genererSurement(graine)
            val monde = Tableaux.monter(tableau, avecSolution = false)
            monde.derouler(12f)
            assertFalse("la graine $graine se gagne sans poser une seule piece",
                monde.gagne)
        }
    }

    @Test
    fun `l inventaire correspond exactement a la solution`() {
        for (graine in 1L..10L) {
            val tableau = Tableaux.genererSurement(graine)
            assertEquals("graine $graine : l'inventaire ne compte pas les memes pieces " +
                "que la solution", tableau.pieces, tableau.inventaire.values.sum())
            assertTrue("graine $graine : un tableau sans aucune piece a poser",
                tableau.pieces >= 1)
        }
    }

    @Test
    fun `les tableaux restent de taille raisonnable`() {
        // La contrainte de cout : peu de dominos et gros. Une ligne de trente dominos
        // fins couterait plus cher qu'un chateau au moteur, pour le meme effet.
        for (graine in 1L..30L) {
            val tableau = Tableaux.genererSurement(graine)
            val dominos = tableau.inventaire[TypePiece.DOMINO] ?: 0
            assertTrue("la graine $graine demande $dominos dominos", dominos <= 8)
            assertTrue("la graine $graine demande ${tableau.pieces} pieces en tout",
                tableau.pieces <= 11)
        }
    }

    @Test
    fun `le generateur ne se contente pas d une seule forme de machine`() {
        // **Le test qui dit si la refonte du generateur a servi a quelque chose.** La
        // version precedente ne savait produire qu'une rampe suivie d'une ligne de
        // dominos : tous les tableaux se ressemblaient, et deux types de pieces sur neuf
        // n'etaient jamais distribues. Exiger cinq types differents sur trente tableaux
        // est modeste, et c'etait pourtant impossible avant.
        val vus = HashSet<TypePiece>()
        for (graine in 1L..30L) vus.addAll(Tableaux.genererSurement(graine).inventaire.keys)
        assertTrue("le generateur n'emploie que ${vus.size} types de pieces : $vus",
            vus.size >= 5)
    }

    @Test
    fun `chaque piece de l inventaire est indispensable`() {
        // L'elagage promet qu'une piece donnee au joueur sert a quelque chose. C'est la
        // promesse la plus facile a rompre sans s'en apercevoir — une piece posee a
        // l'etape deux peut se retrouver hors du chemin apres l'etape trois — et la plus
        // penible pour le joueur, qui cherche a quoi sert une piece qui ne sert a rien.
        for (graine in 1L..12L) {
            val tableau = Tableaux.genererSurement(graine)
            if (tableau.pieces < 2) continue
            for (i in tableau.solution.indices) {
                val ampute = tableau.solution.toMutableList().also { it.removeAt(i) }
                val monde = Plateau()
                monde.poserBouton(tableau.boutonX, tableau.boutonBas)
                for (pose in ampute) monde.poser(pose.creer())
                monde.poserBille(tableau.billeX, tableau.billeY)
                monde.derouler(9f)
                assertFalse(
                    "graine $graine : la machine gagne sans sa piece ${tableau.solution[i].type} " +
                        "(rang $i) — elle n'avait rien a faire dans l'inventaire",
                    monde.gagne
                )
            }
        }
    }

    @Test
    fun `chaque niveau propose un tableau soluble`() {
        // La difficulte monte par etapes, donc le generateur travaille plus dur au
        // niveau douze qu'au niveau un. C'est exactement la ou un generateur cesse de
        // trouver, et c'est donc la qu'il faut le mesurer.
        for (niveau in 1..12) {
            val tableau = Tableaux.pourNiveau(niveau)
            val monde = Tableaux.monter(tableau, avecSolution = true)
            monde.derouler(12f)
            assertTrue("le niveau $niveau n'est pas soluble par sa propre solution",
                monde.gagne)
        }
    }

    @Test
    fun `une pose se recree a l identique`() {
        // Une pose est un souvenir : la recreer deux fois doit donner deux pieces
        // identiques, sinon rien ne se sauvegarde ni ne se rejoue.
        val pose = Pose(TypePiece.RAMPE, x = 1.5f, y = 0.8f, reglage = 24f)
        val a = pose.creer()
        val b = pose.creer()
        assertEquals(a.type, b.type)
        assertEquals(a.principal.x, b.principal.x, 0f)
        assertEquals(a.principal.y, b.principal.y, 0f)
        assertEquals(a.principal.angle, b.principal.angle, 0f)
        assertEquals("une rampe doit etre scellee", Ancrage.SCELLE, a.ancrage)
    }

    @Test
    fun `chaque type de piece sait se recreer depuis une pose`() {
        // Garde-fou contre l'oubli : ajouter un type de piece sans l'ajouter a `creer`
        // ne compilerait meme pas, mais rien ne garantit qu'il produise quelque chose.
        for (type in TypePiece.entries) {
            val piece = Pose(type, x = 0f, y = 0.5f, reglage = 20f).creer()
            assertNotNull("le type $type ne produit aucune piece", piece)
            assertTrue("le type $type ne produit aucun corps", piece.corps.isNotEmpty())
            assertEquals("le type $type ne se souvient pas de son ancrage",
                type.ancrage, piece.ancrage)
        }
    }
}
