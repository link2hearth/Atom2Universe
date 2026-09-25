package com.Atom2Universe.app.games.roguelike

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Le garde-fou du butin, comme `PhysicsEnergyTest` l'est pour le moteur physique.
 *
 * La règle de [AffixBudget] — « un affixe plein vaut 12 % de son axe » — ne tient que si
 * le héros de référence modélisé dans [AffixBudget] correspond vraiment à ce que
 * [LootSystem.create] fabrique. Le jour où on changera les dégâts d'une épée, les PV par
 * point de CON ou l'armure d'un plastron, ces tests diront tout de suite quels affixes
 * sont devenus morts ou obligatoires.
 *
 * Ils écrivent aussi la table complète dans `build/affix-tiers.txt` : c'est elle qu'on
 * recopie dans DONJON.md.
 */
class AffixBudgetTest {

    /** Les paliers qu'on vérifie : jusqu'au 26ᵉ, celui de l'étage 10 000. */
    private val DEEP_TIERS = 26

    /** Les affixes réglés par la règle des 12 % ; les autres ont un plafond posé à la main. */
    private val budgetStats = StatType.entries.filter { !AffixBudget.isCapped(it) }

    /** Ce que vaut une valeur d'affixe sur son axe, en %, rapporté à sa part (les stats étalées sur sept pièces, AffixBudget.spread). */
    private fun worth(type: StatType, value: Float, power: Int) =
        value * AffixBudget.perPoint(type, power) * 100f / AffixBudget.spread(type)

    // ── L'invariant ─────────────────────────────────────────────────────────────

    @Test
    fun unAffixePleinVautDouzePourCentDeSonAxe() {
        for (type in budgetStats) for (tier in 1..minOf(AffixBudget.maxTierOf(type), DEEP_TIERS)) {
            val p = AffixBudget.tierPower(tier)
            val w = worth(type, AffixBudget.nominal(type, tier), p)
            assertTrue("$type P$tier vaut $w %, on veut 12 %", kotlin.math.abs(w - 12f) < 0.1f)
        }
    }

    /**
     * Après arrondi à l'entier, un affixe reste dans une fourchette raisonnable. Les
     * paliers bas d'une stat qui vaut cher (un point de CON, un point de dégâts d'arme)
     * ne peuvent pas descendre sous +1 : ils dépassent donc un peu les 12 %. C'est le
     * plancher de l'entier, pas un déréglage — mais il ne doit jamais s'emballer.
     */
    @Test
    fun apresArrondiAucunAffixeNeSEmballe() {
        for (type in budgetStats) for (tier in 1..minOf(AffixBudget.maxTierOf(type), DEEP_TIERS)) {
            val p = AffixBudget.tierPower(tier)
            val nominal = AffixBudget.nominal(type, tier)
            val hi = if (type.isPercent) nominal else nominal.roundToInt().coerceAtLeast(1).toFloat()
            val lo = AffixBudget.minRoll(type, tier)
            assertTrue("$type P$tier monte à ${worth(type, hi, p)} %", worth(type, hi, p) <= 25f)
            assertTrue("$type P$tier tombe à ${worth(type, lo, p)} %", worth(type, lo, p) >= 5f)
            assertTrue("$type P$tier : le minimum dépasse le maximum", lo <= hi)
        }
    }

    @Test
    fun chaquePalierEstMeilleurQueLePrecedent() {
        for (type in StatType.entries) for (tier in 2..minOf(AffixBudget.maxTierOf(type), DEEP_TIERS)) {
            val below = AffixBudget.nominal(type, tier - 1)
            val here  = AffixBudget.nominal(type, tier)
            assertTrue("$type : P$tier ($here) ne dépasse pas P${tier - 1} ($below)", here > below)
        }
    }

    /**
     * Un objet ne peut pas porter un palier que sa puissance n'autorise pas, ni un palier
     * au-delà du dernier de sa stat. Jusqu'à l'étage 10 000.
     */
    @Test
    fun unPalierNeSortJamaisTropTot() {
        val rng = Random(11)
        repeat(20000) {
            val floor = if (rng.nextBoolean()) rng.nextInt(1, 101) else rng.nextInt(101, 10_001)
            val e = LootSystem.generate(floor, 0, rng)
            for (a in e.affixes) {
                assertTrue("affixe P${a.tier} sur un objet de puissance ${e.power}",
                    AffixBudget.tierPower(a.tier) <= e.power)
                assertTrue("affixe ${a.type} de palier ${a.tier}", a.tier in 1..AffixBudget.maxTierOf(a.type))
            }
            for (i in e.implicits) assertTrue("une stat de base n'a pas de palier", i.tier == 0)
        }
    }

    // ── Les stats à plafond ─────────────────────────────────────────────────────

    /**
     * Les taux ne se règlent pas aux 12 % : on fixe ce qu'un équipement complet peut en
     * porter. Ce test vérifie qu'un personnage couvert d'objets Rares de fin de partie ne
     * fait pas sauter les plafonds tout seul — la chance de critique du héros est bornée à
     * 60 %, un équipement ne doit pas y suffire.
     */
    @Test
    fun lesTauxNeFontPasSauterLesPlafonds() {
        val rng = Random(12)
        val worst = mutableMapOf<StatType, Float>()
        repeat(2000) {
            val hero = Hero()
            for (base in ItemBase.entries) {
                val e = LootSystem.create(base, LootSystem.DEEP_POWER, Rarity.EPIC, 0, rng)
                if (hero.equipped[e.slot] == null) hero.equipped[e.slot] = e
            }
            for (t in listOf(StatType.CRIT_CHANCE, StatType.CRIT_DAMAGE, StatType.SPELL_DMG, StatType.LIFE_STEAL)) {
                val total = if (t == StatType.LIFE_STEAL) hero.lifeSteal else hero.equipped.values.sumOf { it.sum(t).toDouble() }.toFloat()
                worst[t] = maxOf(worst[t] ?: 0f, total)
            }
        }
        assertTrue("critique des objets : ${worst[StatType.CRIT_CHANCE]}", worst.getValue(StatType.CRIT_CHANCE) <= 0.30f)
        assertTrue("dégâts critiques : ${worst[StatType.CRIT_DAMAGE]}", worst.getValue(StatType.CRIT_DAMAGE) <= 2.0f)
        assertTrue("dégâts des sorts : ${worst[StatType.SPELL_DMG]}", worst.getValue(StatType.SPELL_DMG) <= 1.60f)
        assertTrue("vol de vie : ${worst[StatType.LIFE_STEAL]}", worst.getValue(StatType.LIFE_STEAL) <= 0.25f)
    }

    // ── La note dit la même chose que le budget ─────────────────────────────────

    /**
     * La note affichée au joueur se compte dans la monnaie du budget : un affixe plein
     * vaut 12 points. Sur beaucoup d'objets, un affixe doit donc rapporter en moyenne
     * 12 × 0,775 ≈ 9,3 points (un tirage va de 55 % à 100 %).
     */
    @Test
    fun laNoteCompteEnPointsDeBudget() {
        val rng = Random(13)
        var points = 0f
        var affixes = 0
        repeat(20000) {
            val floor = rng.nextInt(1, 101)
            val e = LootSystem.generate(floor, 0, rng)
            if (e.affixes.isEmpty()) return@repeat
            // Les taux en rampe sont volontairement sous le budget jusqu'au palier 20 (24/09/2026) : hors de la moyenne
            val budgeted = e.affixes.filter { !AffixBudget.isRamp(it.type) }
            // Les stats étalées sur sept pièces valent une part de leur budget (AffixBudget.spread)
            points += budgeted.sumOf { (it.value * AffixBudget.perPoint(it.type, e.power) * 100f / AffixBudget.spread(it.type)).toDouble() }.toFloat()
            affixes += budgeted.size
        }
        val avg = points / affixes
        assertTrue("un affixe vaut $avg points de note, on attend ~9,3", avg in 8f..11f)
    }

    /**
     * La note doit monter avec la puissance, sinon plus rien ne se voit comme une
     * amélioration : le joueur (et les bots de la simulation) gardent leur vieux stuff et
     * la progression s'arrête net. À qualité égale, un objet plus profond note plus haut.
     */
    @Test
    fun laNoteMonteAvecLaPuissance() {
        val rng = Random(14)
        // Les 60 premiers crans (au-delà de l'étage 100), puis de loin en loin jusqu'à l'étage 10 000
        val powers = (1..60) + (80..4000 step 97)
        for (base in ItemBase.entries) for (rarity in Rarity.entries) {
            // La moyenne de beaucoup de tirages. Pas la médiane : un objet Magique porte
            // 1 ou 2 affixes, ce qui fait deux paquets distincts, et la médiane saute de
            // l'un à l'autre sans que rien n'ait bougé.
            // Trois crans au début ; en profondeur, trois crans ne font plus que +0,5 % : on
            // compare alors d'un palier au suivant (×1,3).
            fun deeper(power: Int) = maxOf(power + 3, Math.round(power * 1.3f))
            val notes = (powers + powers.map { deeper(it) }).toSet().associateWith { power ->
                List(200) { LootSystem.rating(LootSystem.create(base, power, rarity, 0, rng)) }.average()
            }
            // D'un cran à l'autre, le hasard des affixes pèse plus que la puissance ; c'est
            // sur quelques crans que l'objet plus profond doit se voir.
            for (power in powers) {
                val here = notes.getValue(power)
                val deep = notes.getValue(deeper(power))
                assertTrue("$base $rarity : puissance ${deeper(power)} note $deep, pas mieux que $here à $power",
                    deep > here)
            }
        }
    }

    // ── La table, pour DONJON.md ────────────────────────────────────────────────

    @Test
    fun tableDesAffixes() {
        val out = StringBuilder()
        out.appendLine("Table des affixes — fourchettes par palier (générée par AffixBudgetTest)")
        out.appendLine("Un palier s'ouvre à une puissance d'objet, c'est-à-dire vers un étage :")
        val tiers = (1..AffixBudget.TIERS) + listOf(9, 10, 12, 15, 20, DEEP_TIERS)
        out.append(String.format("%-14s", "palier"))
        for (t in tiers) out.append(String.format("%-14s", "P$t"))
        out.appendLine()
        out.append(String.format("%-14s", "étage >="))
        for (t in tiers) {
            val p = AffixBudget.tierPower(t)
            out.append(String.format("%-14s", (1 + (p - 1) / 0.4f).roundToInt()))
        }
        out.appendLine(); out.appendLine("-".repeat(14 * (tiers.size + 1)))

        for (type in StatType.entries) {
            out.append(String.format("%-14s", type.name))
            for (t in tiers) {
                if (t < AffixBudget.minTier(type) || t > AffixBudget.maxTierOf(type)) { out.append(String.format("%-14s", "—")); continue }
                val nominal = AffixBudget.nominal(type, t)
                val cell = if (type.isPercent)
                    "%.1f-%.1f%%".format(nominal * AffixBudget.ROLL_MIN * 100, nominal * 100)
                else
                    "%d-%d".format(AffixBudget.minRoll(type, t).roundToInt(),
                                   nominal.roundToInt().coerceAtLeast(AffixBudget.minRoll(type, t).roundToInt()))
                out.append(String.format("%-14s", cell))
            }
            out.appendLine()
            out.append(String.format("%-14s", "  vaut"))
            for (t in tiers) {
                if (t < AffixBudget.minTier(type) || t > AffixBudget.maxTierOf(type)) { out.append(String.format("%-14s", "—")); continue }
                val p = AffixBudget.tierPower(t)
                out.append(String.format("%-14s", "%.0f %%".format(worth(type, AffixBudget.nominal(type, t), p))))
            }
            out.appendLine()
        }
        File("build").mkdirs()
        File("build/affix-tiers.txt").writeText(out.toString())
        println(out)
    }
}
