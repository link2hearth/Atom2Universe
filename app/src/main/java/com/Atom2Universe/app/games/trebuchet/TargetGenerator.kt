package com.Atom2Universe.app.games.trebuchet

import kotlin.math.roundToInt
import kotlin.random.Random

/** Les sortes de sites qu'on sait dessiner. Chacune a sa silhouette et son enjeu. */
enum class SiteKind {
    /** Trois ou quatre maisons de bois : ça s'écroule vite et ça fait du bruit. */
    HAMEAU,

    /** Une maison derrière un bout de rempart, avec une tour de guet. */
    FERME,

    /** Deux tours, des courtines, une maison entre les deux. La pièce de résistance. */
    CHATEAU,

    /** Une seule grosse tour, flanquée de deux murets. Haute et têtue. */
    DONJON,

    /** Un village palissadé : des maisons, une grange, et de quoi ranger le grain. */
    VILLAGE,

    /** Un moulin et ce qui vit autour. Le chapeau à ailes est la vraie cible. */
    MOULIN,

    /** Une ville de pierre : des immeubles et des greniers, un par palier. Le croquis. */
    BOURG,

    /** Un temple, un amphithéâtre, un aqueduc. Que de la pierre taillée. */
    CITE_ANTIQUE,

    /** Des pyramides de grès. Rien ne bascule : tout se casse, gradin par gradin. */
    NECROPOLE,

    /** Une pyramide de tentes en planches, comme un vrai château de cartes géant. */
    CHATEAU_CARTES,

    /** Une haute tour de garde, un village sous sa protection, puis un petit château. */
    SEIGNEURIE
}

/**
 * Un niveau prêt à jouer : un relief, une construction posée dessus à sa distance, et
 * la graine qui a produit les deux.
 *
 * Tout est dans la graine — la sorte de site, sa taille, ses matériaux, la forme du
 * terrain, et la distance à laquelle il attend. Un niveau se rejoue donc à l'identique,
 * se partage en un entier, et se réengendre sans être stocké.
 */
class TargetLevel(
    val seed: Long,
    val kind: SiteKind,
    /** Distance du pied de la construction au pied de la machine, en mètres. */
    val distance: Float,
    /** Le vent qui souffle sur ce site-là. Il sort de la même graine. */
    val wind: Wind,
    /** La construction, déjà tassée, soulevée sur ses plateaux et posée à sa distance. */
    val structure: Structure,
    /** Le relief, prolongé à plat d'un bout à l'autre du monde. */
    val terrain: Terrain,
    /** La forme du relief, pour l'écran de fin de niveau. */
    val shape: TerrainShape
)

/**
 * Le générateur de niveaux.
 *
 * Le principe est celui d'une **enfilade à étages**. Vu de profil, un site n'a pas de
 * cour : il a des groupes de bâtiments posés côte à côte, chaque groupe sur son propre
 * plateau, et les plateaux sont à des altitudes différentes. On tire une sorte de site,
 * on en déduit les groupes de modules, on demande au relief un plateau par groupe assez
 * large pour le porter, on bâtit chaque groupe à plat puis on le soulève à l'altitude
 * de son plateau, et on tasse le tout.
 *
 * **Rien ne se bâtit jamais sur une pente.** C'est la décision qui rend tout le reste
 * possible sans réécrire une ligne de maçonnerie : les modules continuent de croire que
 * le sol est à zéro, le tassement se fait sur le vrai relief, et la validation ne
 * change pas.
 *
 * La validation reste délibérément **légère** : on vérifie que la construction ne
 * s'écroule pas toute seule au tassement, et c'est tout. Savoir si un tas de gravats
 * précis peut passer sous une ligne précise demanderait de raser le niveau par
 * simulation à chaque graine, ce qui coûte cher pour une question dont la réponse est
 * presque toujours oui.
 */
object TargetGenerator {

    /** Bornes de la distance de tir, en mètres. */
    const val MIN_DISTANCE = 100f
    const val MAX_DISTANCE = 500f

    /** Espace laissé entre deux modules voisins d'un même groupe, à l'échelle du site. */
    private val gap: Float get() = TargetRules.site(0.5f)

    /** Nombre de graines dérivées qu'on essaie avant de se contenter de ce qu'on a. */
    private const val MAX_ATTEMPTS = 4

    /**
     * Fabrique le niveau de la graine [seed].
     *
     * La distance et la sorte de site ne dépendent que de la graine et pas des essais :
     * deux joueurs avec la même graine tirent sur la même cible, à la même distance,
     * quoi qu'il arrive ensuite.
     */
    fun generate(seed: Long): TargetLevel {
        val head = Random(seed)
        val distance = MIN_DISTANCE + head.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE)
        val kind = kindFor(seed)

        var best: Structure? = null
        var bestTerrain: Terrain? = null
        var bestShape = TerrainShape.PLAINE
        var bestDrift = Float.MAX_VALUE
        for (attempt in 0 until MAX_ATTEMPTS) {
            val rng = Random(seed * 31L + attempt * 7919L)
            val shape = shapeFor(rng, kind)
            val (draft, terrain) = draw(rng, kind, shape, distance)
            val settled = TargetField.settle(draft, terrain)
            val drift = TargetField.drift(draft, settled)
            if (draft.problems(terrain).isEmpty() && drift < TargetRules.SETTLE_TOLERANCE) {
                best = settled
                bestTerrain = terrain
                bestShape = shape
                break
            }
            // On garde quand même le moins mauvais : mieux vaut un niveau un peu bancal
            // qu'une boucle qui ne rend jamais la main.
            if (drift < bestDrift) {
                bestDrift = drift
                best = settled
                bestTerrain = terrain
                bestShape = shape
            }
        }
        val site = best!!
        // Le site a été bâti et tassé dans son repère local, relief compris. On déplace
        // maintenant **les deux ensemble** jusqu'à la distance de tir : c'est ce qui
        // garantit que la construction tombe au centimètre près sur sa distance
        // annoncée sans jamais glisser de ses plateaux.
        val dx = distance - site.left
        return TargetLevel(
            seed, kind, distance, Wind.forSeed(seed),
            site.translated(dx),
            bestTerrain!!.translated(dx)
                .extended(TrebuchetRules.GROUND_LEFT, TrebuchetRules.GROUND_RIGHT),
            bestShape
        )
    }

    /**
     * La sorte de site d'une graine, en **progression**.
     *
     * Ce n'est pas un tirage au sort, et c'est délibéré. Un boulet de douze kilos fait
     * 2,5 % de dégâts à un château de six cents tonnes : commencer par là, c'est
     * demander cinquante coups avant le premier niveau réussi. La suite monte donc
     * doucement — quelques hameaux de bois, puis des fermes, des villages, un donjon,
     * et les gros morceaux de pierre en récompense — et se répète ensuite en boucle,
     * chaque tour donnant des sites différents puisque la graine, elle, continue
     * d'avancer.
     */
    internal fun kindFor(seed: Long, style: TargetStyle = TargetRules.style): SiteKind {
        val arcadeLadder = arrayOf(
            SiteKind.HAMEAU,
            SiteKind.HAMEAU,
            SiteKind.FERME,
            SiteKind.VILLAGE,
            SiteKind.FERME,
            SiteKind.MOULIN,
            SiteKind.DONJON,
            SiteKind.BOURG,
            SiteKind.VILLAGE,
            SiteKind.FERME,
            SiteKind.CITE_ANTIQUE,
            SiteKind.DONJON,
            SiteKind.NECROPOLE,
            SiteKind.BOURG,
            SiteKind.CHATEAU,
            // Ajoutée en fin de liste, et pas mêlée aux autres : une graine désigne un
            // niveau pour toujours, et glisser un nouveau genre au milieu aurait changé
            // ce que chaque graine existante redonne.
            SiteKind.CHATEAU_CARTES,
            SiteKind.SEIGNEURIE
        )
        // Le château de cartes est une fantaisie d'arcade. En réaliste, la même place
        // dans la progression donne la nouvelle seigneurie : une composition crédible,
        // plus longue et plus haute, mais bâtie avec les modules ordinaires.
        val ladder = if (style == TargetStyle.ARCADE) arcadeLadder else
            arcadeLadder.filterNot { it == SiteKind.CHATEAU_CARTES }.toTypedArray()
        val i = ((seed - 1L) % ladder.size).toInt()
        return ladder[if (i < 0) i + ladder.size else i]
    }

    /**
     * Le relief d'un site.
     *
     * Chaque sorte de site a ses reliefs plausibles, et le tirage se fait dedans. Ce
     * n'est pas de la couleur locale : une nécropole en terrasses est un escalier de
     * pyramides, et c'est une image ; un château dans un vallon serait une cible qu'on
     * ne voit pas, et c'est une frustration. Les reliefs qui **cachent** le site sont
     * réservés aux sites bas ; ceux qui le **surélèvent** aux sites hauts, dont la
     * silhouette pardonne d'être encore montée.
     */
    private fun shapeFor(rng: Random, kind: SiteKind): TerrainShape {
        val choix = when (kind) {
            SiteKind.HAMEAU -> arrayOf(
                TerrainShape.PLAINE, TerrainShape.PLAINE,
                TerrainShape.TERRASSES, TerrainShape.COLLINE, TerrainShape.VALLON
            )

            SiteKind.FERME -> arrayOf(
                TerrainShape.PLAINE, TerrainShape.COLLINE,
                TerrainShape.CRETE, TerrainShape.TERRASSES
            )

            SiteKind.VILLAGE -> arrayOf(
                TerrainShape.TERRASSES, TerrainShape.TERRASSES,
                TerrainShape.PLAINE, TerrainShape.GRADINS, TerrainShape.VALLON
            )

            SiteKind.MOULIN -> arrayOf(
                // Un moulin est sur une butte, c'est même à ça qu'on le reconnaît.
                TerrainShape.MESA, TerrainShape.TERRASSES,
                TerrainShape.CRETE, TerrainShape.PLAINE
            )

            SiteKind.BOURG -> arrayOf(
                TerrainShape.TERRASSES, TerrainShape.TERRASSES,
                TerrainShape.GRADINS, TerrainShape.MESA
            )

            SiteKind.CITE_ANTIQUE -> arrayOf(
                TerrainShape.PLAINE, TerrainShape.TERRASSES,
                TerrainShape.MESA, TerrainShape.CRETE
            )

            SiteKind.NECROPOLE -> arrayOf(
                TerrainShape.PLAINE, TerrainShape.TERRASSES,
                TerrainShape.GRADINS, TerrainShape.MESA
            )

            SiteKind.DONJON -> arrayOf(
                TerrainShape.MESA, TerrainShape.PLAINE,
                TerrainShape.CRETE, TerrainShape.COLLINE
            )

            SiteKind.CHATEAU -> arrayOf(
                TerrainShape.MESA, TerrainShape.CRETE,
                TerrainShape.PLAINE, TerrainShape.COLLINE
            )

            SiteKind.SEIGNEURIE -> arrayOf(
                // La lecture gauche-droite doit rester nette : la tour ouvre la scène,
                // le village la prolonge, le château la ferme au fond.
                TerrainShape.TERRASSES, TerrainShape.PLAINE,
                TerrainShape.CRETE, TerrainShape.TERRASSES
            )

            // Un château de cartes n'a rien d'un site fortifié : on le pose bien en
            // évidence, jamais caché par un relief qui masquerait sa silhouette absurde.
            SiteKind.CHATEAU_CARTES -> arrayOf(
                TerrainShape.PLAINE, TerrainShape.PLAINE, TerrainShape.MESA
            )
        }
        return choix[rng.nextInt(choix.size)]
    }

    // ── Le plan ───────────────────────────────────────────────────────────────

    /**
     * Dessine un site entier avec son relief, le premier plateau commençant à zéro.
     *
     * L'ordre compte : on ne peut pas demander un plateau avant de savoir ce qui va
     * dessus, et on ne peut pas bâtir avant de savoir à quelle altitude on bâtit. On
     * estime donc d'abord l'emprise de chaque groupe, on demande le relief, puis on
     * bâtit pour de bon en soulevant chaque groupe sur son plateau.
     */
    private fun draw(
        rng: Random,
        kind: SiteKind,
        shape: TerrainShape,
        distance: Float
    ): Pair<Structure, Terrain> {
        val groupes = plan(rng, kind)
        val total = groupes.sumOf { it.size }.coerceAtLeast(1)
        val budget = (TargetRules.BODY_BUDGET / total).coerceAtLeast(10)

        // L'emprise **estimée** d'un groupe : la somme des largeurs du plan, plus les
        // écarts, plus une marge. Un module rend souvent un peu plus large que ce qu'on
        // lui a demandé — le socle d'une tour déborde, les ailes d'un moulin encore
        // plus — et un plateau trop juste ferait poser un pied dans le vide.
        val emprises = groupes.map { groupe ->
            var w = 0f
            for (p in groupe) w += TargetRules.site(p.width) + gap
            (w * 1.12f + TargetRules.site(1.5f)).coerceAtLeast(TargetRules.site(3f))
        }

        // La longueur de terrain réservée devant le site : c'est là qu'une colline a le
        // droit de se dresser. On en prend une bonne part du trajet, sans jamais
        // remonter jusque sous la machine.
        val approche = (distance * 0.45f).coerceIn(60f, 180f)
        val relief = Terrain.plan(rng, shape, emprises, approche)

        val blocks = ArrayList<Block>()
        for ((gi, groupe) in groupes.withIndex()) {
            val pad = relief.pads.getOrNull(gi) ?: continue
            var x = pad.left
            for (p in groupe) {
                // Le plan est écrit en mètres réels — une maison de cinq mètres, une
                // tour de douze — et c'est le tempérament qui décide de la taille à
                // laquelle on la bâtit. Un plan n'a pas à savoir dans quel mode il est
                // joué.
                val w = TargetRules.site(p.width)
                val h = TargetRules.site(p.height)
                val module = build(rng, p, x, w, h, budget)
                if (module.isEmpty()) continue
                // On soulève le module sur son plateau. C'est tout ce que le relief
                // coûte à la maçonnerie : une translation.
                for (b in module) blocks += b.translated(0f, pad.y)
                // Et on avance d'après l'emprise **réelle** : deux centimètres de
                // chevauchement suffisent à faire s'entre-broyer deux modules dès la
                // première image.
                x = Structure(module).right + gap
            }
        }
        return Structure(blocks, kind.name.lowercase()) to relief.terrain
    }

    /** Pose un module d'après sa fiche. */
    private fun build(
        rng: Random,
        slot: Slot,
        x: Float,
        w: Float,
        h: Float,
        budget: Int
    ): List<Block> = when (slot.module) {
        ModuleKind.TOWER -> TargetModules.tower(rng, x, w, h, slot.material, budget)
        ModuleKind.WALL -> TargetModules.curtainWall(rng, x, w, h, slot.material, budget)
        ModuleKind.HOUSE -> TargetModules.house(rng, x, w, h)
        ModuleKind.PROPS -> TargetModules.props(rng, x, 0f, w, 2 + rng.nextInt(2))
        ModuleKind.WINDMILL -> TargetModules.windmill(rng, x, w, h, slot.material)
        ModuleKind.PYRAMID -> TargetModules.pyramid(rng, x, w, h, slot.material, budget)
        ModuleKind.ARENA -> TargetModules.arena(rng, x, w, h, slot.material, budget)
        ModuleKind.TEMPLE -> TargetModules.temple(rng, x, w, h, slot.material)
        ModuleKind.AQUEDUCT -> TargetModules.aqueduct(rng, x, w, h, slot.material)
        ModuleKind.GRANARY -> TargetModules.granary(rng, x, w, h)
        ModuleKind.INSULA -> TargetModules.insula(rng, x, w, h, slot.material)
        ModuleKind.BARN -> TargetModules.barn(rng, x, w, h)
        ModuleKind.PALISADE -> TargetModules.palisade(rng, x, w, h)
        ModuleKind.CARD_CASTLE -> TargetModules.cardCastle(rng, x, w, h, slot.material)
        ModuleKind.WELL -> listOf(TargetModules.well(x + w / 2f, 0f, minOf(w, h) * 0.28f))
        ModuleKind.SHELTER -> listOf(TargetModules.shelter(x, w, h))
        ModuleKind.STAIRCASE -> listOf(Masonry.staircase(slot.material, x, 0f, w, h))
        ModuleKind.GATEHOUSE -> TargetModules.gatehouse(rng, x, w, h, slot.material, budget)
        ModuleKind.CHAPEL -> TargetModules.chapel(rng, x, w, h, slot.material, budget)
        ModuleKind.TOWER_HOUSE -> TargetModules.towerHouse(rng, x, w, h, slot.material, budget)
    }

    private enum class ModuleKind {
        TOWER, WALL, HOUSE, PROPS,
        WINDMILL, PYRAMID, ARENA, TEMPLE, AQUEDUCT, GRANARY, INSULA, BARN, PALISADE,
        CARD_CASTLE, WELL, SHELTER, STAIRCASE, GATEHOUSE, CHAPEL, TOWER_HOUSE
    }

    private class Slot(
        val module: ModuleKind,
        val width: Float,
        val height: Float,
        val material: Material = Material.STONE
    )

    /**
     * Les groupes de modules d'un site, dans l'ordre.
     *
     * C'est ici que se joue la **silhouette**, et elle est écrite à la main plutôt que
     * tirée au sort : une enfilade aléatoire donne un profil en dents de scie qui ne
     * ressemble à rien. Un château a ses tours aux extrémités et son corps de logis au
     * milieu ; un hameau est bas et régulier ; un donjon est un pic.
     *
     * **Un groupe est une unité de terrain.** Tout ce qui est dans le même groupe
     * partage un plateau, et se touche donc du coude ; deux groupes sont séparés par un
     * talus, une falaise, ou simplement du terrain plat. Un château est un seul groupe
     * parce qu'un château est un seul bâtiment ; un village en compte autant qu'il a de
     * maisons, et c'est ce qui lui donne son escalier.
     */
    private fun plan(rng: Random, kind: SiteKind): List<List<Slot>> {
        fun between(a: Float, b: Float) = a + rng.nextFloat() * (b - a)
        // Les fortifications de torchis géantes appartiennent au langage arcade. En
        // réaliste, les plans militaires restent en pierre ; le torchis demeure sur
        // les maisons, où il est architecturalement plausible.
        val stone = if (
            TargetRules.style == TargetStyle.ARCADE && rng.nextFloat() < 0.25f
        ) Material.COB else Material.STONE

        // La hauteur d'une tour : la plupart du temps sa gamme habituelle, et de temps
        // en temps un colosse. Ça ne coûte plus rien au moteur depuis que
        // [TargetModules.tower] se coupe tout seul en sections passé un certain seuil
        // ([[trebuchet-grands-batiments]]) — n'importe quelle tour du jeu peut donc se
        // permettre d'être immense de temps en temps, pas seulement le donjon.
        fun tourHauteur(
            modMin: Float, modMax: Float,
            geanteMin: Float, geanteMax: Float,
            chanceGeante: Float = 0.15f
        ): Float = if (TargetRules.style == TargetStyle.REALISTE) {
            between(modMin, modMax)
        } else if (rng.nextFloat() < 1f - chanceGeante) {
            between(modMin, modMax)
        } else {
            between(geanteMin, geanteMax)
        }

        return when (kind) {
            SiteKind.HAMEAU -> {
                // Un hameau doit désormais se lire comme un lieu habité, pas comme
                // deux maisons isolées : trois à six bâtiments, avec un peu de vie.
                val n = 3 + rng.nextInt(4)
                val groupes = ArrayList<List<Slot>>(n)
                for (i in 0 until n) {
                    val maison = Slot(ModuleKind.HOUSE, between(4f, 7f), between(5.5f, 9f))
                    // Le dernier hameau a un petit élément de vie : tonneaux ou puits.
                    groupes += if (i == n - 1) {
                        listOf(
                            maison,
                            if (rng.nextBoolean()) Slot(ModuleKind.PROPS, between(1.5f, 3f), 0f)
                            else Slot(ModuleKind.WELL, between(2f, 2.8f), between(2.4f, 3.2f))
                        )
                    } else {
                        listOf(maison)
                    }
                }
                if (n >= 5 && rng.nextFloat() < 0.45f) {
                    groupes += listOf(
                        Slot(ModuleKind.CHAPEL, between(4.5f, 6.5f), between(8f, 12f), Material.STONE)
                    )
                }
                groupes
            }

            SiteKind.FERME -> listOf(
                listOf(
                    Slot(ModuleKind.WALL, between(5f, 8f), between(4f, 6f), stone),
                    if (rng.nextFloat() < 0.35f) {
                        Slot(ModuleKind.TOWER_HOUSE, between(4.5f, 6f), between(10f, 14f), stone)
                    } else {
                        Slot(ModuleKind.HOUSE, between(4.5f, 6.5f), between(6f, 8.5f))
                    }
                ),
                listOf(
                    Slot(ModuleKind.BARN, between(7f, 10f), between(5f, 6.5f), Material.WOOD),
                    if (rng.nextBoolean()) Slot(ModuleKind.PROPS, between(1.5f, 3f), 0f)
                    else Slot(ModuleKind.SHELTER, between(3f, 4.5f), between(2.5f, 3.5f), Material.WOOD)
                ),
                listOf(Slot(ModuleKind.TOWER, between(3f, 4f), tourHauteur(8f, 12f, 22f, 38f), stone))
            )

            SiteKind.CHATEAU -> {
                val hauteurTours = between(11f, 16f)
                val hauteurMurs = hauteurTours * between(0.5f, 0.68f)
                // Un château est un seul bâtiment : un seul plateau. Ce qui varie, c'est
                // ce qu'on lui met devant — une basse-cour, sur son propre palier. Les
                // deux tours tirent **chacune** leur colosse séparément : un château
                // dissymétrique, une tour normale d'un côté et une immense de l'autre,
                // est une bien meilleure surprise qu'un château uniformément géant.
                val corps = listOf(
                    Slot(
                        ModuleKind.TOWER, between(3.5f, 4.5f),
                        tourHauteur(hauteurTours, hauteurTours, hauteurTours * 2.5f, hauteurTours * 4.5f, 0.12f),
                        stone
                    ),
                    Slot(ModuleKind.WALL, between(6f, 10f), hauteurMurs, stone),
                    Slot(ModuleKind.GATEHOUSE, between(5.5f, 7.5f), hauteurMurs + between(1f, 3f), stone),
                    Slot(ModuleKind.WALL, between(6f, 10f), hauteurMurs, stone),
                    Slot(
                        ModuleKind.TOWER, between(4f, 5f),
                        tourHauteur(
                            hauteurTours + 1f, hauteurTours + 4f,
                            hauteurTours * 2.5f, hauteurTours * 4.5f, 0.12f
                        ),
                        stone
                    )
                )
                if (rng.nextFloat() < 0.6f) {
                    listOf(
                        listOf(
                            Slot(ModuleKind.PALISADE, between(6f, 9f), between(3f, 4.5f)),
                            Slot(ModuleKind.BARN, between(6f, 8f), between(4.5f, 6f))
                        ),
                        corps
                    )
                } else {
                    listOf(corps)
                }
            }

            SiteKind.DONJON -> listOf(
                listOf(
                    Slot(ModuleKind.WALL, between(4f, 7f), between(3.5f, 5f), stone),
                    // Le donjon reste le site le plus susceptible de rouler un colosse :
                    // moitié-moitié, contre 12 à 15 % ailleurs — c'est thématiquement sa
                    // raison d'être.
                    Slot(ModuleKind.TOWER, between(5.5f, 8f), tourHauteur(15f, 24f, 30f, 55f, 0.5f), stone),
                    Slot(ModuleKind.WALL, between(4f, 7f), between(3.5f, 5f), stone),
                    Slot(ModuleKind.PROPS, between(1.5f, 2.5f), 0f)
                )
            )

            SiteKind.CHATEAU_CARTES -> {
                // Plusieurs versions, comme demandé : la plupart du temps un château
                // raisonnable, plus rarement un colosse deux à quatre fois plus haut.
                // La largeur monte avec la hauteur pour que les versions géantes aient
                // vraiment plus d'étages, et pas seulement des tentes plus grandes.
                val tirage = rng.nextFloat()
                val (largeur, hauteur) = when {
                    tirage < 0.5f -> between(12f, 20f) to between(16f, 30f)
                    tirage < 0.8f -> between(18f, 28f) to between(40f, 65f)
                    else -> between(24f, 34f) to between(75f, 120f)
                }
                listOf(listOf(Slot(ModuleKind.CARD_CASTLE, largeur, hauteur, Material.CARDBOARD)))
            }

            SiteKind.SEIGNEURIE -> {
                // L'ordre est le récit du site, puisque le trébuchet est à gauche :
                // 1. la tour de garde prend le premier impact ;
                // 2. plusieurs foyers occupent le terrain qu'elle protège ;
                // 3. un petit château ferme l'horizon à droite.
                val tourAvant = if (TargetRules.style == TargetStyle.ARCADE) {
                    between(34f, 58f)
                } else {
                    between(20f, 29f)
                }
                val groupes = ArrayList<List<Slot>>()
                groupes += listOf(
                    Slot(ModuleKind.TOWER, between(5f, 7f), tourAvant, Material.STONE)
                )

                // Quatre maisons donnent un vrai village tout en laissant assez de
                // corps au petit château final dans le budget physique global.
                val maisons = 4
                for (i in 0 until maisons) {
                    val maison = Slot(
                        ModuleKind.HOUSE,
                        between(4.2f, 6.2f),
                        between(6f, 9.5f),
                        Material.WOOD
                    )
                    groupes += when {
                        i == 1 -> listOf(
                            maison,
                            Slot(ModuleKind.WELL, between(2f, 2.8f), between(2.4f, 3.2f))
                        )
                        i == maisons - 1 -> listOf(
                            Slot(ModuleKind.BARN, between(7f, 9f), between(5f, 6.5f), Material.WOOD),
                            maison
                        )
                        else -> listOf(maison)
                    }
                }

                val petiteTour = between(10f, 14f)
                groupes += listOf(
                    Slot(ModuleKind.TOWER, between(3.5f, 4.5f), petiteTour, Material.STONE),
                    Slot(ModuleKind.WALL, between(5f, 7f), petiteTour * between(0.45f, 0.58f), Material.STONE),
                    Slot(ModuleKind.GATEHOUSE, between(5f, 6.5f), between(7f, 9.5f), Material.STONE),
                    Slot(ModuleKind.WALL, between(4f, 6f), petiteTour * between(0.42f, 0.55f), Material.STONE),
                    Slot(ModuleKind.TOWER, between(3.5f, 4.5f), petiteTour + between(1f, 3f), Material.STONE)
                )
                groupes
            }

            SiteKind.VILLAGE -> {
                val groupes = ArrayList<List<Slot>>(7)
                groupes += listOf(
                    Slot(ModuleKind.PALISADE, between(5f, 9f), between(2.5f, 4f)),
                    Slot(ModuleKind.PROPS, between(1.5f, 3f), 0f)
                )
                groupes += listOf(
                    Slot(ModuleKind.HOUSE, between(4.5f, 6.5f), between(6f, 9f)),
                    Slot(ModuleKind.HOUSE, between(4f, 6f), between(5.5f, 8f))
                )
                groupes += listOf(Slot(ModuleKind.BARN, between(8f, 12f), between(5f, 7f)))
                groupes += if (rng.nextBoolean()) {
                    listOf(Slot(ModuleKind.GRANARY, between(4.5f, 6.5f), between(7f, 10f)))
                } else {
                    listOf(
                        Slot(ModuleKind.HOUSE, between(4.5f, 6f), between(6f, 8f)),
                        Slot(ModuleKind.PROPS, between(1.5f, 2.5f), 0f)
                    )
                }
                groupes += listOf(
                    Slot(ModuleKind.CHAPEL, between(5f, 7f), between(9f, 13f), Material.STONE)
                )
                // Deux à trois foyers supplémentaires donnent enfin la densité d'un
                // village, sans répéter une rangée parfaitement régulière.
                repeat(2 + rng.nextInt(2)) { i ->
                    groupes += if (i == 1 && rng.nextBoolean()) {
                        listOf(
                            Slot(ModuleKind.HOUSE, between(4f, 6f), between(6f, 9f)),
                            Slot(ModuleKind.WELL, between(2f, 2.8f), between(2.4f, 3.2f))
                        )
                    } else {
                        listOf(Slot(ModuleKind.HOUSE, between(4f, 6.5f), between(6f, 9.5f)))
                    }
                }
                groupes
            }

            SiteKind.MOULIN -> listOf(
                listOf(
                    Slot(ModuleKind.BARN, between(7f, 10f), between(5f, 6.5f)),
                    Slot(ModuleKind.PROPS, between(1.5f, 3f), 0f)
                ),
                listOf(Slot(ModuleKind.GRANARY, between(4.5f, 6f), between(7f, 9.5f))),
                // Le moulin tout en haut : c'est la pièce à faire tomber, et de tous les
                // modules du jeu c'est celui dont la chute se voit de plus loin.
                listOf(Slot(ModuleKind.WINDMILL, between(4f, 5.5f), between(12f, 17f), stone))
            )

            SiteKind.BOURG -> {
                // Le croquis, à la lettre : un bâtiment par palier, du plus bas au plus
                // haut, et de moins en moins de bois à mesure qu'on monte.
                val groupes = ArrayList<List<Slot>>(4)
                groupes += listOf(
                    Slot(ModuleKind.HOUSE, between(4f, 5.5f), between(7f, 10f)),
                    Slot(ModuleKind.TOWER_HOUSE, between(4f, 5.5f), between(11f, 16f), Material.STONE)
                )
                groupes += listOf(Slot(ModuleKind.HOUSE, between(6f, 8f), between(8f, 11f)))
                groupes += listOf(Slot(ModuleKind.GRANARY, between(5f, 7f), between(9f, 12f)))
                groupes += listOf(
                    Slot(ModuleKind.INSULA, between(3.5f, 5f), between(9f, 13f), Material.STONE),
                    Slot(ModuleKind.INSULA, between(3.5f, 5f), between(9f, 13f), Material.STONE)
                )
                groupes
            }

            SiteKind.CITE_ANTIQUE -> {
                val gres = if (rng.nextBoolean()) Material.SANDSTONE else Material.STONE
                listOf(
                    listOf(
                        Slot(ModuleKind.AQUEDUCT, between(10f, 14f), between(9f, 13f), Material.STONE)
                    ),
                    listOf(
                        Slot(ModuleKind.TEMPLE, between(9f, 13f), between(8f, 11f), gres),
                        Slot(ModuleKind.PROPS, between(1.5f, 2.5f), 0f)
                    ),
                    listOf(
                        Slot(ModuleKind.ARENA, between(13f, 18f), between(8f, 12f), Material.STONE)
                    )
                )
            }

            SiteKind.NECROPOLE -> {
                val n = 2 + rng.nextInt(2)
                val groupes = ArrayList<List<Slot>>(n + 1)
                for (i in 0 until n) {
                    val w = between(9f, 14f)
                    groupes += listOf(
                        Slot(ModuleKind.PYRAMID, w, w * between(0.6f, 0.78f), Material.SANDSTONE)
                    )
                }
                groupes += listOf(
                    Slot(ModuleKind.TEMPLE, between(8f, 11f), between(7f, 10f), Material.SANDSTONE),
                    Slot(ModuleKind.PROPS, between(1.5f, 2.5f), 0f)
                )
                groupes
            }
        }
    }

    /** Un nom lisible, pour l'écran de fin de niveau. */
    fun label(level: TargetLevel): String = buildString {
        append(
            when (level.kind) {
                SiteKind.HAMEAU -> "Hameau"
                SiteKind.FERME -> "Ferme fortifiée"
                SiteKind.CHATEAU -> "Château"
                SiteKind.DONJON -> "Donjon"
                SiteKind.VILLAGE -> "Village"
                SiteKind.MOULIN -> "Moulin"
                SiteKind.BOURG -> "Bourg étagé"
                SiteKind.CITE_ANTIQUE -> "Cité antique"
                SiteKind.NECROPOLE -> "Nécropole"
                SiteKind.CHATEAU_CARTES -> "Château de cartes"
                SiteKind.SEIGNEURIE -> "Seigneurie protégée"
            }
        )
        // Le relief ne se dit que quand il change quelque chose au tir : annoncer « en
        // plaine » à chaque niveau plat serait du bruit.
        when (level.shape) {
            TerrainShape.PLAINE -> Unit
            TerrainShape.TERRASSES -> append(" en terrasses")
            TerrainShape.GRADINS -> append(" en gradins")
            TerrainShape.COLLINE -> append(" derrière la colline")
            TerrainShape.MESA -> append(" sur la butte")
            TerrainShape.VALLON -> append(" au fond du vallon")
            TerrainShape.CRETE -> append(" derrière la crête")
        }
        append(" · ${level.distance.roundToInt()} m")
    }
}
