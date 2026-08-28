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
    DONJON
}

/**
 * Un niveau prêt à jouer : une construction posée à sa distance, et la graine qui l'a
 * produite.
 *
 * Tout est dans la graine — la sorte de site, sa taille, ses matériaux, et la distance
 * à laquelle il attend. Un niveau se rejoue donc à l'identique, se partage en un entier,
 * et se réengendre sans être stocké.
 */
class TargetLevel(
    val seed: Long,
    val kind: SiteKind,
    /** Distance du pied de la construction au pied de la machine, en mètres. */
    val distance: Float,
    /** Le vent qui souffle sur ce site-là. Il sort de la même graine. */
    val wind: Wind,
    /** La construction, déjà tassée et posée à sa distance. */
    val structure: Structure
)

/**
 * Le générateur de niveaux.
 *
 * Le principe est celui d'une **enfilade** : vu de profil, un site n'a pas de cour, il a
 * une succession de pièces posées côte à côte le long du sol. On tire une sorte de site,
 * on en déduit la liste des modules et leur silhouette, on les pose l'un après l'autre
 * d'après leur emprise réelle, et on tasse le tout.
 *
 * La validation est délibérément **légère** : on vérifie que la construction ne
 * s'écroule pas toute seule au tassement, et c'est tout. Savoir si un tas de gravats
 * précis peut passer sous une ligne précise demanderait de raser le niveau par
 * simulation à chaque graine, ce qui coûte cher pour une question dont la réponse est
 * presque toujours oui.
 */
object TargetGenerator {

    /** Bornes de la distance de tir, en mètres. */
    const val MIN_DISTANCE = 100f
    const val MAX_DISTANCE = 500f

    /** Espace laissé entre deux modules voisins, à l'échelle du site. */
    private val gap: Float get() = TargetRules.site(0.5f)

    /** Nombre de graines dérivées qu'on essaie avant de se contenter de ce qu'on a. */
    private const val MAX_ATTEMPTS = 4

    /**
     * Fabrique le niveau de la graine [seed].
     *
     * La distance, elle, ne dépend que de la graine et pas des essais : deux joueurs
     * avec la même graine tirent sur la même cible, à la même distance, quoi qu'il
     * arrive ensuite.
     */
    fun generate(seed: Long): TargetLevel {
        val head = Random(seed)
        val distance = MIN_DISTANCE + head.nextFloat() * (MAX_DISTANCE - MIN_DISTANCE)
        val kind = kindFor(seed)

        var best: Structure? = null
        var bestDrift = Float.MAX_VALUE
        for (attempt in 0 until MAX_ATTEMPTS) {
            val draft = draw(Random(seed * 31L + attempt * 7919L), kind)
            val settled = TargetField.settle(draft)
            val drift = TargetField.drift(draft, settled)
            if (draft.problems().isEmpty() && drift < TargetRules.SETTLE_TOLERANCE) {
                best = settled
                break
            }
            // On garde quand même le moins mauvais : mieux vaut un niveau un peu bancal
            // qu'une boucle qui ne rend jamais la main.
            if (drift < bestDrift) {
                bestDrift = drift
                best = settled
            }
        }
        val site = best!!
        return TargetLevel(
            seed, kind, distance, Wind.forSeed(seed),
            site.translated(distance - site.left)
        )
    }

    /**
     * La sorte de site d'une graine, en **progression**.
     *
     * Ce n'est pas un tirage au sort, et c'est délibéré. Un boulet de douze kilos fait
     * 2,5 % de dégâts à un château de six cents tonnes : commencer par là, c'est
     * demander cinquante coups avant le premier niveau réussi. La suite monte donc
     * doucement — quelques hameaux de bois, puis des fermes, un donjon, et le château
     * en récompense — et se répète ensuite en boucle, chaque tour donnant des sites
     * différents puisque la graine, elle, continue d'avancer.
     */
    private fun kindFor(seed: Long): SiteKind {
        val ladder = arrayOf(
            SiteKind.HAMEAU,
            SiteKind.HAMEAU,
            SiteKind.FERME,
            SiteKind.HAMEAU,
            SiteKind.FERME,
            SiteKind.DONJON,
            SiteKind.FERME,
            SiteKind.CHATEAU
        )
        val i = ((seed - 1L) % ladder.size).toInt()
        return ladder[if (i < 0) i + ladder.size else i]
    }

    // ── Le plan ───────────────────────────────────────────────────────────────

    /** Dessine un site entier, le pied gauche à zéro. */
    private fun draw(rng: Random, kind: SiteKind): Structure {
        val pieces = plan(rng, kind)
        val budget = (TargetRules.BODY_BUDGET / pieces.size).coerceAtLeast(10)
        val blocks = ArrayList<Block>()
        var x = 0f
        for (p in pieces) {
            // Le plan est écrit en mètres réels — une maison de cinq mètres, une tour de
            // douze — et c'est le tempérament qui décide de la taille à laquelle on la
            // bâtit. Un plan n'a pas à savoir dans quel mode il est joué.
            val w = TargetRules.site(p.width)
            val h = TargetRules.site(p.height)
            val module = when (p.module) {
                ModuleKind.TOWER -> TargetModules.tower(rng, x, w, h, p.material, budget)
                ModuleKind.WALL -> TargetModules.curtainWall(rng, x, w, h, p.material, budget)
                ModuleKind.HOUSE -> TargetModules.house(rng, x, w, h)
                ModuleKind.PROPS -> TargetModules.props(rng, x, 0f, w, 2 + rng.nextInt(2))
            }
            if (module.isEmpty()) continue
            blocks += module
            // On avance d'après l'emprise **réelle** : le socle d'une tour déborde, et
            // deux centimètres de chevauchement suffisent à faire s'entre-broyer deux
            // modules dès la première image.
            x = Structure(module).right + gap
        }
        return Structure(blocks, kind.name.lowercase())
    }

    private enum class ModuleKind { TOWER, WALL, HOUSE, PROPS }

    private class Slot(
        val module: ModuleKind,
        val width: Float,
        val height: Float,
        val material: Material = Material.STONE
    )

    /**
     * La liste des modules d'un site, dans l'ordre.
     *
     * C'est ici que se joue la **silhouette**, et elle est écrite à la main plutôt que
     * tirée au sort : une enfilade aléatoire donne un profil en dents de scie qui ne
     * ressemble à rien. Un château a ses tours aux extrémités et son corps de logis au
     * milieu ; un hameau est bas et régulier ; un donjon est un pic.
     */
    private fun plan(rng: Random, kind: SiteKind): List<Slot> {
        fun between(a: Float, b: Float) = a + rng.nextFloat() * (b - a)
        val stone = if (rng.nextFloat() < 0.25f) Material.COB else Material.STONE

        return when (kind) {
            SiteKind.HAMEAU -> {
                val n = 2 + rng.nextInt(3)
                (0 until n).map {
                    Slot(ModuleKind.HOUSE, between(4f, 7f), between(5.5f, 9f))
                } + Slot(ModuleKind.PROPS, between(1.5f, 3f), 0f)
            }

            SiteKind.FERME -> listOf(
                Slot(ModuleKind.WALL, between(5f, 8f), between(4f, 6f), stone),
                Slot(ModuleKind.HOUSE, between(4.5f, 6.5f), between(6f, 8.5f)),
                Slot(ModuleKind.TOWER, between(3f, 4f), between(8f, 12f), stone),
                Slot(ModuleKind.PROPS, between(1.5f, 3f), 0f)
            )

            SiteKind.CHATEAU -> {
                val hauteurTours = between(11f, 16f)
                val hauteurMurs = hauteurTours * between(0.5f, 0.68f)
                listOf(
                    Slot(ModuleKind.TOWER, between(3.5f, 4.5f), hauteurTours, stone),
                    Slot(ModuleKind.WALL, between(6f, 10f), hauteurMurs, stone),
                    Slot(ModuleKind.HOUSE, between(4.5f, 6f), hauteurMurs + between(0f, 2f)),
                    Slot(ModuleKind.WALL, between(6f, 10f), hauteurMurs, stone),
                    Slot(ModuleKind.TOWER, between(4f, 5f), hauteurTours + between(1f, 4f), stone)
                )
            }

            SiteKind.DONJON -> listOf(
                Slot(ModuleKind.WALL, between(4f, 7f), between(3.5f, 5f), stone),
                Slot(ModuleKind.TOWER, between(4.5f, 6f), between(15f, 22f), stone),
                Slot(ModuleKind.WALL, between(4f, 7f), between(3.5f, 5f), stone),
                Slot(ModuleKind.PROPS, between(1.5f, 2.5f), 0f)
            )
        }
    }

    /** Un nom lisible, pour l'écran de fin de niveau. */
    fun label(level: TargetLevel): String = when (level.kind) {
        SiteKind.HAMEAU -> "Hameau"
        SiteKind.FERME -> "Ferme fortifiée"
        SiteKind.CHATEAU -> "Château"
        SiteKind.DONJON -> "Donjon"
    } + " · ${level.distance.roundToInt()} m"
}
