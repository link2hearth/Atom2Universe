package com.Atom2Universe.app.games.farm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

/**
 * Garde l'économie de Ma Ferme. Le défaut que cette table corrige était invisible à l'œil : toutes
 * les cultures rapportaient entre 0,50 et 1,00 pièce par heure, donc dormir rapportait autant que
 * jouer. Ce sont des rapports, pas des prix, qu'il faut vérifier — un prix se retouche librement
 * tant que les rapports ci-dessous tiennent.
 */
class FarmEconomyTest {

    private val ladder = FarmCrop.ladder
    /** Gain net d'une récolte. */
    private fun gain(crop: FarmCrop) = (crop.sale - crop.cost).toDouble()
    private fun hours(crop: FarmCrop) = crop.seconds / 3600.0
    /** Le coefficient de valeur du palier : gain = u × √durée. */
    private fun unit(crop: FarmCrop) = gain(crop) / sqrt(hours(crop))

    @Test
    fun `une graine par parcelle, sans trou dans l'échelle`() {
        assertEquals(FarmLayout.lands.size, ladder.size)
        assertEquals((1..ladder.size).toList(), ladder.map { it.rank })
        assertTrue("les arbres restent hors échelle", ladder.none { it.tree })
    }

    /**
     * Le plafond de la culture de session est passé de 8 h à 10 h avec la carotte. Ce n'est plus
     * une session au sens strict, mais ce que la borne protège vraiment, c'est l'écart : tant que
     * la culture rapide reste franchement plus courte que sa jumelle de nuit, revenir dans la
     * journée garde un intérêt. À 10 h contre 24 h, l'écart tient. C'est au-delà d'une journée
     * éveillée qu'il faudrait s'inquiéter.
     */
    @Test
    fun `chaque paire offre une culture de session et une culture de nuit`() {
        ladder.chunked(2).forEach { (quick, slow) ->
            assertTrue("${quick.name} doit tenir dans une journée", hours(quick) <= 10.0)
            assertTrue("${slow.name} doit couvrir une absence", hours(slow) >= 6.0)
            assertTrue("${slow.name} doit durer plus que ${quick.name}", hours(slow) > hours(quick))
        }
    }

    /**
     * Le cœur du rééquilibrage. Dans une paire, la culture de nuit est toujours la graine la plus
     * récente : si on lui donnait aussi un meilleur coefficient, elle écraserait la culture de
     * session et laisser tourner le jeu redeviendrait la stratégie optimale.
     */
    @Test
    fun `le coefficient est partage a l'interieur d'une paire`() {
        ladder.chunked(2).forEach { (quick, slow) ->
            val ecart = unit(slow) / unit(quick)
            assertTrue("${quick.name}/${slow.name} : coefficients trop éloignés ($ecart)",
                ecart in 0.95..1.05)
        }
    }

    @Test
    fun `la valeur d'un palier progresse regulierement`() {
        val units = ladder.chunked(2).map { unit(it.first()) }
        units.zipWithNext().forEach { (before, after) ->
            assertTrue("progression hors cible : ${after / before}", after / before in 1.75..1.95)
        }
    }

    /**
     * La promesse faite au joueur : chaque passage supplémentaire rapporte. Trois récoltes de la
     * culture de session battent nettement une seule récolte de nuit, et deux la dépassent déjà.
     */
    @Test
    fun `jouer rapporte plus que laisser tourner`() {
        ladder.chunked(2).forEach { (quick, slow) ->
            val trois = 3 * gain(quick) / gain(slow)
            val deux = 2 * gain(quick) / gain(slow)
            assertTrue("${quick.name} ×3 ne bat pas ${slow.name} ($trois)", trois >= 1.6)
            assertTrue("${quick.name} ×2 ne bat pas ${slow.name} ($deux)", deux > 1.0)
        }
    }

    /**
     * Vu de n'importe quel moment de la partie : la culture la plus longue à laquelle on a accès ne
     * doit jamais être aussi celle qui rapporte le plus par heure.
     */
    @Test
    fun `la plus longue culture disponible n'est jamais la plus rentable`() {
        for (parcelles in 2..ladder.size) {
            val disponibles = ladder.take(parcelles)
            val laPlusLongue = disponibles.maxBy { it.seconds }
            val laMeilleure = disponibles.maxBy { it.coinsPerHour }
            assertTrue("avec $parcelles parcelles, ${laPlusLongue.name} domine",
                laMeilleure.coinsPerHour > laPlusLongue.coinsPerHour * 1.15f)
        }
    }

    @Test
    fun `la graine coute une part stable de la recolte`() {
        ladder.forEach { crop ->
            val part = crop.cost.toDouble() / crop.sale
            assertTrue("${crop.name} : semence à ${part * 100} %% de la vente", part in 0.14..0.20)
        }
    }

    /**
     * Le fumier coûte le rang de la graine. C'est ce qui empêche la boucle de déraper : un prix fixe
     * couvrirait un poulailler débutant puis, avec quatre troupeaux pleins, couvrirait absolument
     * toutes les plantations - le bonus deviendrait un doublement permanent du potager.
     */
    @Test
    fun `le fumier coute le rang de la graine`() {
        ladder.forEach { assertEquals(it.rank, it.manureCost) }
        assertTrue("une citrouille doit coûter douze fois un radis",
            FarmCrop.PUMPKIN.manureCost == 12 * FarmCrop.RADISH.manureCost)
    }

    @Test
    fun `une bete plus grosse produit plus de fumier`() {
        LivestockKind.entries.zipWithNext().forEach { (before, after) ->
            assertTrue(after.manurePerDay > before.manurePerDay)
        }
        // La moyenne vise ~6 points par bête et par jour : c'est ce qui tient la couverture autour
        // d'un tiers des plantations, aussi bien à dix poules qu'à quatre troupeaux pleins.
        val moyenne = LivestockKind.entries.sumOf { it.manurePerDay } / LivestockKind.entries.size.toDouble()
        assertTrue("moyenne hors cible : $moyenne", moyenne in 5.0..8.0)
    }

    @Test
    fun `le prix des parcelles ne redescend jamais`() {
        val prices = FarmLayout.lands.map { it.price }
        assertEquals("la première parcelle est offerte", 0, prices.first())
        prices.drop(1).zipWithNext().forEach { (before, after) ->
            assertTrue("prix non croissant : $before puis $after", after > before)
        }
    }

    /** Les enclos se gagnent : chacun demande plus de récoltes que le précédent, et coûte plus cher. */
    @Test
    fun `l'elevage suit un ordre strict`() {
        LivestockKind.entries.zipWithNext().forEach { (before, after) ->
            assertTrue(after.harvestsNeeded > before.harvestsNeeded)
            assertTrue(after.landPrice > before.landPrice)
            assertTrue(after.cycleHours > before.cycleHours)
        }
        LivestockKind.entries.forEach {
            assertTrue("${it.name} : revendre doit toujours perdre de l'argent", it.sale < it.price)
        }
    }
}
