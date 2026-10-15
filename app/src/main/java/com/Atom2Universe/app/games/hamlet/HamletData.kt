package com.Atom2Universe.app.games.hamlet

/*
 * Hameau : les données du jeu. Rien ici ne dépend d'Android, pour que les règles se testent sans
 * téléphone. Les durées sont en secondes, l'argent en pièces ; les gemmes ne servent qu'aux
 * bâtiments « précieux » et à presser une construction ou une récolte.
 */

enum class Cat { HALL, FIELD, HOME, SHOP, RANCH, DECOR, NATURE, PATH }

/** Ce qu'il faut pour dessiner un objet : une famille de dessin et quelques couleurs. */
class Look(val kind: String, val a: Int = 0, val b: Int = 0, val c: Int = 0, val n: Int = 0)

class Def(
    val id: String,
    val cat: Cat,
    val w: Int,
    val d: Int,
    val cost: Int,
    val lvl: Int,
    val xp: Int,
    val look: Look,
    val gems: Int = 0,
    // maisons
    val tax: Int = 0,
    val period: Int = 0,
    val taxXp: Int = 0,
    val pop: Int = 0,
    // ateliers
    val job: Int = 0,
    val pay: Int = 0,
    val payXp: Int = 0,
    val glyph: String = "",
    // chantier
    val build: Int = 0,
    // champs enchantés, pose en glissant, éléments qui se raccordent
    val magic: Boolean = false,
    val drag: Boolean = false,
    val link: Boolean = false,
    /** Se pose sur l'eau seulement (barques…). */
    val onWater: Boolean = false,
    /** Doit toucher l'eau (pêche, moulin à eau, chantier naval). */
    val shore: Boolean = false,
    /** Points de charme que cet objet donne aux maisons et ateliers voisins. */
    val charm: Int = 0,
) {
    val isPath get() = cat == Cat.PATH
    val premium get() = gems > 0
}

class Crop(
    val id: String,
    val lvl: Int,
    val cost: Int,
    val time: Int,
    val earn: Int,
    val xp: Int,
    val kind: String,
    val leaf: Int,
    val fruit: Int,
    val magic: Boolean = false,
)

class HallLevel(val lvl: Int, val cost: Int, val realm: Int)

class Quest(val key: String, val n: Int, val coins: Int, val xp: Int, val gems: Int = 0, val tickets: Int = 0)

object Hamlet {
    const val GRID = 64
    const val MAX_REALM = 60
    const val MIN = 60
    const val HOUR = 3600
    const val MAX_LEVEL = 40
    const val DAY_MS = 86_400_000L

    /** Une tuile de sol : numéro stocké dans la grille. */
    const val PATH_NONE = 0
    const val PATH_WATER = 3
    const val PATH_BRIDGE = 4

    val halls = listOf(
        HallLevel(1, 0, 22), HallLevel(5, 2_500, 28), HallLevel(10, 20_000, 34),
        HallLevel(15, 75_000, 40), HallLevel(20, 250_000, 46),
        HallLevel(26, 750_000, 50), HallLevel(32, 1_500_000, 54), HallLevel(38, 3_000_000, 58),
    )

    /** XP cumulé de base nécessaire pour terminer chaque niveau (avant l'allongement des niveaux élevés). */
    private val baseXp = intArrayOf(
        0, 45, 130, 280, 520, 850, 1300, 1900, 2600, 3300, 3700, 5500, 8000, 12000, 18000, 27000, 40000,
        60000, 90000, 130000, 185000, 230000, 285000, 350000, 430000, 520000, 630000, 760000, 910000,
        1080000, 1280000, 1500000, 1750000, 2050000, 2400000, 2800000, 3250000, 3750000, 4300000, 4900000,
    )
    /** Au-delà du niveau 8 chaque niveau coûte de plus en plus : le dernier demande XP_BOOST fois plus. */
    const val XP_BOOST = 4.5
    val xpTable: IntArray = IntArray(baseXp.size) { l ->
        val past = (l - 8).coerceAtLeast(0) / 32.0
        (baseXp[l] * (1.0 + (XP_BOOST - 1.0) * Math.pow(past, 1.2))).toLong().coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    fun levelForXp(xp: Long): Int {
        var l = 1
        while (l < MAX_LEVEL && xp >= xpTable[l]) l++
        return l
    }

    /** Les gemmes données en passant un niveau : une, trois tous les cinq niveaux. */
    fun gemsForLevel(level: Int) = if (level % 5 == 0) 3 else 1

    fun expandCost(done: Int): Int = (Math.round(5000.0 * Math.pow(1.6, done.toDouble()) / 500.0) * 500).toInt()

    fun realmSize(hall: Int, expand: Int) = minOf(MAX_REALM, halls[hall - 1].realm + 2 * expand)

    /** Prix en gemmes pour terminer d'un coup un temps d'attente de [ms] millisecondes. */
    fun gemsFor(ms: Long): Int = maxOf(1, Math.ceil(ms / (2 * 3_600_000.0)).toInt())

    val visitors = listOf(
        "Odile", "Basile", "Mireille", "Gaspard", "Colette", "Hugo", "Perrine", "Anselme", "Noémie",
        "Théo", "Lucie", "Armand", "Josette", "Eliott", "Marguerite", "Octave",
    )

    // ------------------------------------------------------------------ cultures

    private fun crop(id: String, lvl: Int, time: Int, kind: String, leaf: Int, fruit: Int, magic: Boolean = false): Crop {
        val earn = HamletEconomy.earn(time, magic)
        return Crop(id, lvl, HamletEconomy.seed(earn), time, earn, HamletEconomy.xp(earn), kind, leaf, fruit, magic)
    }

    /**
     * Les cultures : seuls la durée et le dessin changent d'une ligne à l'autre. Le gain, le prix de la graine et
     * l'expérience viennent de [HamletEconomy] ; le niveau décide seulement de l'ouverture.
     */
    val crops: List<Crop> = listOf(
        // Du blé d'abord, puis une ou deux plantes par niveau. Aucune durée sous deux minutes, et deux plantes n'ont jamais la même durée.
        crop("wheat", 1, 2 * MIN, "grain", 0xC9B04A, 0xE8CF5A),
        crop("corn", 2, 4 * MIN, "stalk", 0x6FB83A, 0xF2D12E),
        crop("radish", 3, 3 * MIN, "root", 0x4F9D37, 0xD8334A),
        crop("carrots", 3, 8 * MIN, "root", 0x4F9D37, 0xF08A1C),
        crop("herbs", 4, 10 * MIN, "bush", 0x3F8F3A, 0x8BC878),
        crop("lettuce", 4, 12 * MIN, "head", 0x7CC545, 0xA6DC5A),
        crop("wildflowers", 5, 15 * MIN, "flower", 0x5AA03C, 0xE05A8A),
        crop("potatoes", 5, 40 * MIN, "bush", 0x3F8F3A, 0xB98A52),
        crop("strawberries", 6, 25 * MIN, "bush", 0x3F8F3A, 0xE0263A),
        crop("turnips", 6, HOUR, "root", 0x6C9C45, 0xD5B9D6),
        crop("peas", 7, 90 * MIN, "tall", 0x5AA83C, 0x8AD25A),
        crop("lavender", 7, 150 * MIN, "flower", 0x6A8F4A, 0x9A6AD8),
        crop("onions", 8, 75 * MIN, "root", 0x5AA03C, 0xE9D8A0),
        crop("tomatoes", 8, 2 * HOUR, "bush", 0x3F8F3A, 0xE0382A),
        crop("squash", 9, 3 * HOUR, "vine", 0x3F8F3A, 0xE8B02A),
        crop("cabbage", 9, 4 * HOUR, "head", 0x6FB84A, 0x9AD66A),
        crop("peppers", 10, 5 * HOUR, "bush", 0x3F8F3A, 0xE8402A),
        crop("glowcabbage", 10, 105 * MIN, "head", 0x6A5AD8, 0xC8A0FF, magic = true),
        crop("oats", 10, 6 * HOUR, "grain", 0xA9B45D, 0xDED496),
        crop("garlic", 11, 8 * HOUR, "root", 0x5AA03C, 0xF3EEDD),
        crop("beets", 11, 9 * HOUR, "root", 0x4F8D37, 0x9A1F52),
        crop("blueberries", 12, 11 * HOUR, "bush", 0x3F8F3A, 0x3A4FC8),
        crop("beans", 12, 24 * HOUR, "tall", 0x4F9D37, 0x5AB83C),
        crop("pumpkins", 13, 12 * HOUR, "vine", 0x3F8F3A, 0xF08A1C),
        crop("leeks", 13, 13 * HOUR, "grain", 0x5AA83C, 0xDFF0C8),
        crop("broccoli", 14, 14 * HOUR, "head", 0x4F9D37, 0x2F8A3A),
        crop("starfruit", 15, 210 * MIN, "bush", 0x4F9D5A, 0xFFE24A, magic = true),
        crop("watermelon", 15, 16 * HOUR, "vine", 0x3F8F3A, 0x2F8F4A),
        crop("cotton", 16, 18 * HOUR, "bush", 0x4F8D37, 0xFFFFFF),
        crop("eggplant", 17, 20 * HOUR, "bush", 0x3F8F3A, 0x6A2A8A),
        crop("hops", 18, 22 * HOUR, "tall", 0x5AA83C, 0xB8D65A),
        crop("tea", 19, 28 * HOUR, "bush", 0x2F7A3A, 0x8FD05A),
        crop("starasparagus", 20, 270 * MIN, "grain", 0x6FB83A, 0xB8FFD8, magic = true),
        crop("sunflower", 21, 30 * HOUR, "flower", 0x4F8D37, 0xF2C21A),
        crop("grapes", 22, 36 * HOUR, "tall", 0x4F8D37, 0x7A2A8A),
        crop("saffron", 24, 40 * HOUR, "flower", 0x4F8D37, 0xA84AD8),
        crop("sunpineapple", 26, 7 * HOUR, "bush", 0x4FA83A, 0xF2B01A, magic = true),
        crop("moonmelon", 30, 10 * HOUR, "vine", 0x3F8F6A, 0x9AD8F2, magic = true),
    )
    val cropById: Map<String, Crop> = crops.associateBy { it.id }

    // ------------------------------------------------------------------ objets

    private val list = ArrayList<Def>()

    private fun add(d: Def) { list.add(d) }

    private fun L(kind: String, a: Int = 0, b: Int = 0, c: Int = 0, n: Int = 0) = Look(kind, a, b, c, n)

    private fun xpFor(cost: Int, gems: Int) = if (gems > 0) gems * 125 else maxOf(1, cost / 18 + 10)

    /** Plus l'objet est cher, plus il embellit le voisinage ; une plante vaut un peu moins qu'une statue. */
    private fun charmFor(cost: Int, gems: Int, w: Int, d: Int, nature: Boolean): Int {
        val value = if (gems > 0) gems * 400 else cost
        val base = Math.log(value / 60.0 + 1.0) / Math.log(2.0)
        val scaled = base * (if (nature) 0.8 else 1.0) * (1 + 0.15 * (w * d - 1))
        return maxOf(1, Math.round(scaled).toInt())
    }

    private fun home(
        id: String, lvl: Int, cost: Int, tax: Int, periodMin: Int, look: Look,
        w: Int = 2, d: Int = 2, pop: Int = 1, gems: Int = 0, build: Int = -1,
    ) = add(
        Def(
            id, Cat.HOME, w, d, cost, lvl, xpFor(cost, gems), look, gems = gems,
            tax = tax, period = periodMin * MIN, taxXp = maxOf(1, tax / 7), pop = pop,
            build = if (gems > 0) 0 else minOf(2 * HOUR, if (build >= 0) build else lvl * 2 * MIN),
        )
    )

    private fun shop(
        id: String, lvl: Int, cost: Int, jobMin: Int, pay: Int, glyph: String, look: Look,
        w: Int = 2, d: Int = 2, pop: Int = 1, gems: Int = 0, shore: Boolean = false, build: Int = -1,
    ) = add(
        Def(
            id, Cat.SHOP, w, d, cost, lvl, if (gems > 0) gems * 120 else cost / 20 + 20, look, gems = gems,
            job = jobMin * MIN, pay = pay, payXp = Math.round(pay / 3.2f), glyph = glyph, pop = pop, shore = shore,
            build = if (gems > 0) 0 else minOf(2 * HOUR, if (build >= 0) build else lvl * 3 * MIN),
        )
    )

    private fun decor(
        id: String, lvl: Int, cost: Int, look: Look, w: Int = 1, d: Int = 1, gems: Int = 0,
        link: Boolean = false, drag: Boolean = false, onWater: Boolean = false,
    ) = add(
        Def(
            id, Cat.DECOR, w, d, cost, lvl, if (gems > 0) gems * 60 else maxOf(1, cost / 30), look, gems = gems,
            link = link, drag = drag || link, onWater = onWater,
            charm = if (link) 0 else charmFor(cost, gems, w, d, false),
        )
    )

    private fun nature(
        id: String, lvl: Int, cost: Int, look: Look, w: Int = 1, d: Int = 1, gems: Int = 0, drag: Boolean = false,
    ) = add(
        Def(
            id, Cat.NATURE, w, d, cost, lvl, if (gems > 0) gems * 60 else maxOf(1, cost / 30), look, gems = gems,
            drag = drag, charm = charmFor(cost, gems, w, d, true),
        )
    )

    private fun path(id: String, lvl: Int, cost: Int, look: Look, over: Boolean = false) = add(
        Def(id, Cat.PATH, 1, 1, cost, lvl, maxOf(1, cost / 25), look, drag = true, magic = over)
    )

    private fun banner(color: String, lvl: Int, c: Int) = decor("banner_$color", lvl, 250, L("banner", c))

    init {
        // ---- la halle (une seule, qui s'agrandit) et les champs
        add(Def("hall", Cat.HALL, 4, 4, 0, 1, 0, L("hall")))
        add(Def("field", Cat.FIELD, 2, 2, 50, 1, 5, L("field"), build = 0))
        add(Def("magicfield", Cat.FIELD, 2, 2, 5000, 10, 100, L("field", n = 1), magic = true, build = 0))
        // Élevages : un cycle nourri, des animaux visibles, puis une vente de produits.
        add(Def("henyard", Cat.RANCH, 2, 2, 240, 2, 18, L("ranch", 0xB8452E, n = 0), build = 30, charm = 2))
        add(Def("rabbitgarden", Cat.RANCH, 2, 2, 650, 3, 40, L("ranch", 0x6A8A54, n = 1), build = 60, charm = 3))
        add(Def("sheepmeadow", Cat.RANCH, 3, 2, 1500, 5, 80, L("ranch", 0x789957, n = 2), build = 120, charm = 4))
        add(Def("dairyfarm", Cat.RANCH, 3, 3, 2800, 7, 140, L("ranch", 0xB8452E, n = 3), build = 180, charm = 4))
        add(Def("duckpond", Cat.RANCH, 2, 2, 1000, 4, 60, L("ranch", 0x4F8395, n = 4), build = 90, charm = 5))
        add(Def("goathill", Cat.RANCH, 3, 2, 2100, 6, 110, L("ranch", 0x9A7C55, n = 5), build = 150, charm = 4))
        shop("farmmarket", 4, 1800, 12, 90, "fruit", L("s_hall", 0xE8D8B0, 0x56844A, 0xF4C24A), w = 3, d = 2, build = 120)
        shop("jamkitchen", 6, 2600, 20, 145, "apple", L("s_shop", 0xF1D8C8, 0xA34D64, 0xE8A83A), build = 180)

        // ---- maisons : un impôt toutes les `period` minutes
        home("cottage", 1, 100, 10, 5, L("h_cot", 0xE8D8B0, 0xC9A24A, 0x7A4A22), build = 0)
        home("hut", 2, 300, 24, 15, L("h_hut", 0xB89868, 0xA8883A, 0x6A4A2A), build = 30)
        home("stonecottage", 3, 500, 34, 30, L("h_cot", 0xB8B2A6, 0x8A5A44, 0x5A4A3A))
        home("bluecottage", 4, 800, 48, 45, L("h_cot", 0x7FA8D8, 0x5A4A6A, 0xF4F0E4))
        home("farmhouse", 5, 1000, 60, 60, L("h_farm", 0xE9DFC4, 0xB8452E, 0x6A4A2A), build = 2 * MIN)
        home("cabin", 6, 1800, 85, 90, L("h_cabin", 0x9A6A3A, 0x5A6A3A, 0x3A2A1A))
        home("fisherhut", 7, 2400, 100, 120, L("h_shack", 0x7A8A8A, 0x5A6A7A, 0xD8C08A))
        home("yurt", 8, 3200, 120, 150, L("h_yurt", 0xF0E6D0, 0xB8452E, 0x3A5AA8))
        home("villa", 9, 4000, 140, 180, L("h_villa", 0xF2E8D0, 0xC8503A, 0x4A6A9A), pop = 2, build = 10 * MIN)
        home("timber", 9, 5500, 170, 180, L("h_timber", 0xEFE6CE, 0x7A4A32, 0x4A3222), pop = 2)
        home("mushroomhouse", 10, 6000, 180, 210, L("h_mush", 0xEAD8B8, 0xD8403A, 0xFFFFFF))
        home("townhouse", 11, 8000, 210, 240, L("h_town", 0xE8C8A0, 0x6A4A5A, 0x3A4A6A), pop = 2)
        home("burrow", 12, 9500, 240, 270, L("h_burrow", 0x6FAE3C, 0x8A5A2A, 0xD8B058))
        home("treehouse", 13, 12000, 280, 300, L("h_tree", 0xB8884A, 0x4F9D37, 0x6A4A2A))
        home("manor", 14, 15000, 340, 360, L("h_manor", 0xE6D8BC, 0x5A6A8A, 0x8A5A3A), w = 3, d = 3, pop = 2, build = 30 * MIN)
        home("merchanthouse", 15, 20000, 400, 420, L("h_town", 0xD8B888, 0x3A6A5A, 0xC9A24A), pop = 2)
        home("watchhouse", 16, 24000, 450, 480, L("h_watch", 0xA8A89A, 0x7A4A3A, 0x5A5A52))
        home("towerhouse", 17, 28000, 500, 480, L("h_tower", 0xD0C8B0, 0x4A6A9A, 0x8A5A3A), pop = 2)
        home("longhouse", 18, 36000, 620, 600, L("h_long", 0xC8A878, 0xA8883A, 0x5A3A22), w = 3, d = 2, pop = 3)
        home("mansion", 19, 50000, 760, 720, L("h_mansion", 0xF0E6CC, 0x4A5A7A, 0xB8923A), w = 3, d = 3, pop = 3, build = HOUR)
        home("chalet", 20, 55000, 640, 600, L("h_chalet", 0xB8884A, 0x6A4A3A, 0xF0E6CC), w = 3, d = 2, pop = 2)
        home("guildhall", 21, 65000, 900, 720, L("h_guild", 0xD8C8A8, 0x8A3A32, 0x4A3A6A), w = 3, d = 3, pop = 3)
        home("greenmanor", 23, 90000, 1150, 840, L("h_manor", 0xD8EAD0, 0x2F7A5A, 0xC9A24A), w = 3, d = 3, pop = 3)
        home("rubycottage", 12, 0, 700, 720, L("h_cot", 0xF0D8D8, 0xC81E3A, 0xF4C24A), gems = 12)
        home("chateau", 15, 0, 1800, 1440, L("h_chateau", 0xEDE3D0, 0xC81E3A, 0xF4C24A), w = 3, d = 3, pop = 3, gems = 40)
        home("goldpalace", 28, 0, 3600, 1440, L("h_palace", 0xF4E6B0, 0xE8B82A, 0xFFF2B8), w = 3, d = 3, pop = 4, gems = 60)
        home("icepalace", 32, 0, 4800, 1440, L("h_palace", 0xDDF0FA, 0x7ACBEA, 0xFFFFFF), w = 3, d = 3, pop = 4, gems = 80)
        home("citadel", 30, 0, 6000, 1440, L("h_citadel", 0xE8D0CC, 0xB81E32, 0xF4C24A), w = 4, d = 4, pop = 5, gems = 120)

        // ---- ateliers : les visiteurs commandent, l'atelier prépare, ils paient
        shop("bakery", 2, 600, 5, 45, "bread", L("s_shop", 0xEBD2A0, 0xB8452E, 0xE8A83A), build = MIN)
        shop("florist", 5, 900, 10, 70, "flower", L("s_shop", 0xF3D8E4, 0x7A9A4A, 0xE05A8A), build = 2 * MIN)
        shop("cheesemaker", 5, 1300, 15, 95, "cheese", L("s_shop", 0xF0E2A8, 0x8A5A3A, 0xE8B82A))
        shop("tailor", 6, 1500, 20, 110, "cloth", L("s_shop", 0xC8B8E0, 0x5A4A8A, 0xB84AC8), build = 3 * MIN)
        shop("herbalist", 4, 1000, 15, 80, "leaf", L("s_shop", 0xCFE0B0, 0x3F7A3A, 0x6AB83A))
        shop("weaver", 5, 2200, 45, 180, "cloth", L("s_shop", 0xE0C8A0, 0x8A3A3A, 0xE0903A), build = 4 * MIN)
        shop("chandler", 5, 2000, 36, 150, "candle", L("s_shop", 0xF4E8C0, 0xA8683A, 0xF4C23A))
        shop("school", 6, 3000, 60, 210, "book", L("s_shop", 0xE8DCC0, 0x3A5A8A, 0xC8392E), build = 5 * MIN)
        shop("toymaker", 6, 2600, 40, 170, "toy", L("s_shop", 0xF0D090, 0xC8392E, 0x3A9AD8), build = 4 * MIN)
        shop("potter", 6, 2900, 72, 230, "pot", L("s_shop", 0xD8A878, 0x8A4A2E, 0xC87A3A))
        shop("lumbermill", 3, 900, 15, 75, "log", L("s_lumber", 0xB88A5A, 0x6A4A32, 0xD8B070), w = 3, d = 2, build = 90)
        shop("tradingpost", 7, 3800, 100, 290, "crate", L("s_shop", 0xC8A878, 0x5A6A4A, 0xB8923A), build = 8 * MIN)
        shop("marketHall", 9, 6000, 180, 410, "fruit", L("s_hall", 0xE8D8B0, 0xC8503A, 0xE8B82A), w = 3, d = 2)
        shop("cobbler", 7, 4000, 108, 310, "boot", L("s_shop", 0xC8A888, 0x4A3A32, 0x8A5A32))
        shop("bookshop", 8, 5200, 150, 380, "book", L("s_shop", 0xD8C8B0, 0x4A3A5A, 0x7A3A8A))
        shop("blacksmith", 8, 6000, 180, 440, "shoe", L("s_smith", 0xB0A89A, 0x4A4A52, 0xE8602A), build = 15 * MIN)
        shop("tavern", 11, 11000, 30, 160, "mug", L("s_shop", 0xD8B888, 0x7A3A2A, 0xE8A82A), pop = 2, build = 20 * MIN)
        shop("painter", 11, 8000, 240, 520, "brush", L("s_shop", 0xEFE8DC, 0x3A6A8A, 0xE8603A))
        shop("hatter", 9, 6600, 180, 430, "hat", L("s_shop", 0xD8C0D8, 0x6A3A6A, 0xE8B82A))
        shop("butcher", 9, 7000, 150, 390, "meat", L("s_shop", 0xE8C8B8, 0x8A3A32, 0xC8392E), build = 15 * MIN)
        shop("greenhouse", 10, 8500, 210, 500, "orchid", L("s_glass", 0xCDEAD8, 0x6AA88A, 0xE05AA8), build = 20 * MIN)
        shop("stables", 10, 9000, 240, 560, "horse", L("s_stable", 0xB88A5A, 0x6A4A32, 0xD8B070), w = 3, d = 2, build = 20 * MIN)
        shop("brewery", 10, 9500, 270, 600, "mug", L("s_smith", 0xC8A070, 0x6A4A2A, 0xD8902A))
        shop("petshop", 11, 11500, 330, 720, "paw", L("s_shop", 0xF0D8B0, 0x4A8A7A, 0xE8903A))
        shop("cider", 13, 15000, 420, 900, "apple", L("s_barn", 0xC8A06A, 0x8A3A32, 0xE0402A))
        shop("fishing", 11, 10000, 300, 680, "fish", L("s_shop", 0x9AB0B8, 0x4A6A7A, 0x3A9AD8), shore = true, build = 20 * MIN)
        shop("barn", 12, 13000, 360, 780, "milk", L("s_barn", 0xC8453A, 0x6A3A32, 0xF4F0E4), build = 30 * MIN)
        shop("watertower", 9, 8000, 90, 270, "drop", L("s_tower", 0xB8884A, 0x4A6A9A, 0x3A9AD8, 1), w = 1, d = 1, pop = 0, build = 10 * MIN)
        shop("courthouse", 15, 26000, 840, 1800, "key", L("s_hall", 0xD8D0C0, 0x5A5A68, 0xB8923A), build = HOUR)
        shop("library", 12, 14000, 360, 820, "book", L("s_hall", 0xE0D0B0, 0x3A4A6A, 0x8A5A3A), w = 3, d = 2)
        shop("lighthouse", 12, 14000, 420, 880, "lantern", L("s_tower", 0xF4F0E4, 0xC8392E, 0xF4D44A, 2), shore = true, build = 30 * MIN)
        shop("watermill", 16, 30000, 660, 1450, "flour", L("s_mill", 0xC8A878, 0x6A4A32, 0x3A9AD8, 1), w = 3, d = 2, shore = true)
        shop("windmill", 13, 17000, 480, 980, "flour", L("s_mill", 0xE8DCC0, 0x8A3A32, 0xE8E0C8, 0), build = 40 * MIN)
        shop("chapel", 13, 19000, 540, 1080, "candle", L("s_chapel", 0xE8E2D4, 0x4A5A8A, 0xF4C24A), build = 40 * MIN)
        shop("barracks", 14, 24000, 660, 1350, "shield", L("s_hall", 0xB8B0A0, 0x6A2A2A, 0xC8392E), w = 3, d = 2, pop = 2, build = 50 * MIN)
        shop("apothecary", 14, 22000, 600, 1250, "bottle", L("s_shop", 0xC8D8C0, 0x3A6A5A, 0x6AD88A), build = 45 * MIN)
        shop("aviary", 17, 33000, 720, 1600, "bird", L("s_glass", 0xF0E8C8, 0xB8923A, 0x3AB8D8))
        shop("clocktower", 16, 32000, 780, 1650, "bell", L("s_tower", 0xD8C8A8, 0x4A5A7A, 0xF4E8C0, 3), build = HOUR)
        shop("armory", 13, 18000, 510, 1020, "sword", L("s_smith", 0xA8A8B0, 0x4A4A5A, 0xC8D0D8), build = 45 * MIN)
        shop("cartographer", 15, 27000, 720, 1550, "map", L("s_shop", 0xE8D8A8, 0x6A4A32, 0xC8903A))
        shop("clockmaker", 20, 55000, 1200, 2500, "gear", L("s_shop", 0xD0C0A0, 0x3A4A5A, 0xE8B82A))
        shop("wizard", 16, 30000, 720, 1500, "potion", L("s_tower", 0x7A68B8, 0x3A2A7A, 0xE8D84A, 4), build = HOUR)
        shop("foundry", 21, 62000, 1320, 2800, "bell", L("s_smith", 0x8A8680, 0x3A3A42, 0xFF7A2A), w = 3, d = 2)
        shop("musichall", 17, 38000, 900, 1900, "note", L("s_hall", 0xE8D0B0, 0x7A3A5A, 0xE8B82A), w = 3, d = 2)
        shop("vineyard", 22, 40000, 1080, 2300, "grape", L("s_vine", 0xD8B880, 0x8A4A32, 0x7A2A8A), w = 3, d = 2, build = 90 * MIN)
        shop("teahouse", 19, 48000, 1020, 2200, "cup", L("s_pagoda", 0xF0E4D0, 0x2F7A5A, 0xC8392E))
        shop("bank", 19, 52000, 1140, 2450, "coins", L("s_hall", 0xE8E0D0, 0x4A5A4A, 0xE8B82A), w = 3, d = 2)
        shop("spicemarket", 20, 58000, 1260, 2700, "sack", L("s_hall", 0xE8B878, 0xC8602A, 0xE8302A), build = 90 * MIN)
        shop("jeweler", 18, 45000, 960, 2100, "gem", L("s_shop", 0xE8E2F0, 0x5A3A8A, 0xD84AE8), build = HOUR)
        shop("glassworks", 22, 80000, 1380, 3100, "gem", L("s_glass", 0xC8E8F0, 0x4A8AB8, 0xFFFFFF))
        shop("quarry", 3, 950, 18, 80, "ore", L("s_mine", 0xAAA397, 0x6A5A4A, 0xD2CAB8), w = 3, d = 2, build = 90)
        shop("mine", 20, 60000, 1200, 2600, "ore", L("s_mine", 0x8A7A6A, 0x6A5A4A, 0xE8B82A), w = 3, d = 3, pop = 2, build = 2 * HOUR)
        shop("circus", 21, 75000, 1320, 2950, "ticket", L("s_circus", 0xE8302A, 0xF4F0E4, 0xF4D42A), w = 3, d = 3, pop = 3, build = 2 * HOUR)
        shop("academy", 23, 100000, 1560, 3400, "scroll", L("s_hall", 0xE0D8C8, 0x3A4A7A, 0xB8923A), w = 3, d = 3, pop = 2)
        shop("observatory", 24, 110000, 1800, 4000, "star", L("s_tower", 0xD8D8E8, 0x3A4A8A, 0xE8D84A, 5), build = 3 * HOUR)
        shop("theater", 22, 90000, 1440, 3300, "mask", L("s_theater", 0xE8D8C0, 0x7A2A3A, 0xE8B82A), w = 3, d = 3, pop = 3, build = 3 * HOUR)
        shop("museum", 24, 120000, 1800, 3900, "vase", L("s_theater", 0xE8E4DC, 0x4A5A6A, 0xB8923A), w = 3, d = 3, pop = 2)
        shop("shipyard", 25, 130000, 1920, 4500, "ship", L("s_dock", 0xB8885A, 0x5A4A3A, 0xE8E0C8), w = 3, d = 3, pop = 2, shore = true, build = 2 * HOUR)
        shop("jousting", 26, 150000, 2160, 5200, "lance", L("s_arena", 0xE8D8A8, 0xC8392E, 0x3A5AC8), w = 3, d = 3, pop = 3, build = 4 * HOUR)
        shop("alchemist", 27, 170000, 2400, 5800, "potion", L("s_tower", 0x8AA88A, 0x2A4A3A, 0x6AF08A, 4), build = 2 * HOUR)
        shop("balloon", 29, 220000, 2640, 6800, "ticket", L("s_tower", 0xF4E0B0, 0xE8302A, 0x3A9AD8, 6), build = 2 * HOUR)
        shop("opera", 30, 300000, 2880, 7500, "mask", L("s_theater", 0xF0E4D0, 0x5A2A5A, 0xE8B82A, 1), w = 3, d = 3, pop = 3, build = 4 * HOUR)
        shop("dragon", 8, 0, 1440, 4200, "egg", L("s_lair", 0x6A5A52, 0xC8392E, 0xFF7A2A), w = 3, d = 3, pop = 0, gems = 25)
        shop("griffin", 14, 0, 1200, 3600, "feather", L("s_lair", 0xA8946A, 0xE8B82A, 0xF4F0E4, 1), w = 3, d = 3, pop = 0, gems = 30)
        shop("unicorn", 18, 0, 1320, 4000, "horse", L("s_lair", 0x6FAE5A, 0xF4E8FF, 0xFF8AD8, 2), pop = 0, gems = 35)

        // ---- décors
        decor("maypole", 8, 1100, L("d_maypole"))
        decor("campfire", 2, 160, L("d_fire", n = 0))
        decor("catapult", 12, 2200, L("d_catapult"))
        decor("beehive", 4, 280, L("d_beehive"))
        decor("wheelbarrow", 2, 140, L("d_wheelbarrow"))
        decor("laundry", 1, 110, L("d_laundry"))
        decor("picnic", 4, 300, L("d_picnic"))
        decor("kennel", 4, 500, L("d_kennel"))
        decor("mailbox", 3, 220, L("d_mailbox"))
        decor("brazier", 7, 650, L("d_fire", n = 1))
        decor("runestone", 11, 1500, L("d_rune"))
        decor("stocks", 15, 1800, L("d_stocks"))
        decor("tent", 7, 900, L("d_tent", 0xE8E0C8, 0x4A5A8A))
        decor("tentRed", 7, 900, L("d_tent", 0xE8D0C0, 0xC8392E))
        decor("tentGreen", 7, 900, L("d_tent", 0xD8E0C0, 0x4F8D37))
        decor("torch", 3, 180, L("d_torch"))
        decor("archery", 6, 450, L("d_archery"))
        decor("birdbath", 5, 600, L("d_birdbath"))
        decor("flowercart", 8, 800, L("d_flowercart"))
        decor("rosearch", 9, 1000, L("d_arch", 0xE05A8A))
        decor("haystack", 2, 100, L("d_haystack"))
        decor("fountain", 8, 3500, L("d_fountain", 0xC8C4B8, 0x7ACBEA), w = 2, d = 2)
        decor("smallfountain", 5, 900, L("d_fountain", 0xB8B0A0, 0x7ACBEA, n = 1))
        decor("well", 2, 400, L("d_well"))
        decor("signpost", 1, 80, L("d_signpost"))
        decor("haybale", 1, 60, L("d_haybale"))
        decor("scarecrow", 2, 150, L("d_scarecrow"))
        decor("lamp", 4, 200, L("d_lamp", n = 0))
        decor("lampDouble", 8, 600, L("d_lamp", n = 1))
        decor("bench", 3, 120, L("d_bench", 0x9A6A3A))
        decor("stonebench", 4, 220, L("d_bench", 0xB8B2A6))
        decor("cart", 5, 350, L("d_cart"))
        decor("barrels", 2, 90, L("d_barrels"))
        decor("gazebo", 10, 5000, L("d_gazebo"), w = 2, d = 2)
        decor("pond", 7, 2000, L("d_pond", n = 0), w = 2, d = 2)
        decor("swanpond", 11, 3800, L("d_pond", n = 1), w = 2, d = 2)
        decor("waterfall", 15, 8000, L("d_waterfall"), w = 2, d = 2)
        decor("marketstall", 6, 700, L("d_stall", 0xC8392E))
        decor("fruitstand", 5, 650, L("d_stall", 0x4F9D37, n = 1))
        decor("fishstand", 7, 800, L("d_stall", 0x3A7AC8, n = 2))
        decor("guardtower", 9, 2500, L("d_guardtower"))
        decor("gnome", 3, 150, L("d_gnome"))
        decor("stonearch", 10, 2400, L("d_stonearch"))
        decor("ruins", 12, 3200, L("d_ruins"), w = 2, d = 2)
        decor("stonecircle", 14, 5000, L("d_circle"), w = 2, d = 2)
        decor("tombstones", 9, 900, L("d_tomb"))
        decor("totem", 8, 1200, L("d_totem"))
        decor("chest", 7, 1500, L("d_chest"))
        decor("goldpile", 12, 5000, L("d_gold"))
        decor("throne", 15, 7500, L("d_throne"))
        decor("armorstand", 9, 1400, L("d_armor"))
        decor("dummy", 5, 400, L("d_dummy"))
        decor("cannon", 13, 2800, L("d_cannon"))
        decor("trebuchet", 16, 6000, L("d_trebuchet"), w = 2, d = 2)
        decor("portal", 17, 12000, L("d_portal"), w = 2, d = 2)
        decor("cauldron", 8, 900, L("d_cauldron"))
        decor("crystalball", 11, 1900, L("d_crystalball"))
        decor("sundial", 6, 700, L("d_sundial"))
        decor("obelisk", 9, 1500, L("d_obelisk"))
        decor("crystal_blue", 5, 0, L("d_crystal", 0x4AA8F0), gems = 5)
        decor("crystal_red", 9, 0, L("d_crystal", 0xF0425A), gems = 5)
        decor("crystal_green", 9, 0, L("d_crystal", 0x4AE08A), gems = 5)
        decor("crystal_purple", 9, 0, L("d_crystal", 0xB05AF0), gems = 5)
        decor("sacks", 2, 90, L("d_sacks"))
        decor("crates", 2, 90, L("d_crates"))
        decor("woodpile", 2, 100, L("d_woodpile"))
        decor("trough", 3, 180, L("d_trough"))
        decor("bonfire", 6, 500, L("d_fire", n = 2))
        decor("chickencoop", 4, 600, L("d_coop"))
        decor("pigpen", 6, 1100, L("d_pen", 0xF0A8B0))
        decor("cowpen", 8, 1900, L("d_pen", 0xF4F0E8, n = 1), w = 2, d = 2)
        decor("paddock", 10, 3000, L("d_pen", 0x8A5A32, n = 2), w = 2, d = 2)
        decor("dovecote", 7, 950, L("d_dovecote"))
        decor("carriage", 14, 6500, L("d_carriage"))
        decor("rowboat", 9, 1200, L("d_boat"), onWater = true)
        decor("hotair", 18, 15000, L("d_hotair"))
        decor("picnicblanket", 3, 160, L("d_blanket"))
        decor("pavilion", 13, 5500, L("d_pavilion"), w = 2, d = 2)
        decor("stage", 12, 4200, L("d_stage"), w = 2, d = 2)
        decor("bunting", 5, 300, L("d_bunting"))
        decor("snowman", 5, 350, L("d_snowman"))
        decor("pumpkinlantern", 5, 350, L("d_jack"))
        decor("xmastree", 8, 1200, L("d_xmas"))
        banner("red", 3, 0xC8392E); banner("black", 5, 0x2A2A32); banner("blue", 4, 0x2F6AD8)
        banner("green", 5, 0x2F9A4A); banner("purple", 7, 0x8A3AB8); banner("gold", 9, 0xE8B82A)
        banner("white", 6, 0xF4F0E4); banner("orange", 6, 0xE8802A)
        decor("statue", 6, 1200, L("d_statue", n = 0), gems = 0)
        decor("statueKing", 12, 8000, L("d_statue", n = 1), w = 2, d = 2)
        decor("statueQueen", 14, 9000, L("d_statue", n = 2), w = 2, d = 2)
        decor("statueDragon", 10, 2600, L("d_statue", n = 3))
        decor("statueLion", 8, 1800, L("d_statue", n = 4))
        decor("statueAngel", 11, 3000, L("d_statue", n = 5))
        decor("statueWizard", 13, 3600, L("d_statue", n = 6))
        decor("fence", 1, 15, L("f_fence", 0xA87A44), link = true)
        decor("ropefence", 2, 20, L("f_rope", 0xC8A878), link = true)
        decor("picket", 4, 30, L("f_picket", 0xF4F0E4), link = true)
        decor("hedgefence", 6, 50, L("f_hedge", 0x3F8F3A), link = true)
        decor("stonewall", 8, 60, L("f_wall", 0xA8A296), link = true)
        decor("ironfence", 10, 80, L("f_iron", 0x3A3A44), link = true)

        // ---- nature
        nature("treeOval", 1, 40, L("t_oval", 0x3F8F3A))
        nature("treeRound", 1, 50, L("t_round", 0x4F9D37))
        nature("treePine", 2, 50, L("t_pine", 0x2F7A4A))
        nature("treeOrange", 3, 120, L("t_round", 0x4F9D37, 0xF08A1C))
        nature("treeApple", 4, 120, L("t_round", 0x4F9D37, 0xE0302A))
        nature("birch", 3, 70, L("t_birch", 0x7CC545))
        nature("lemon", 5, 150, L("t_round", 0x5AAD3C, 0xF4E24A))
        nature("pear", 6, 150, L("t_round", 0x5AAD3C, 0xC8D83A))
        nature("plum", 7, 180, L("t_round", 0x4F8D4A, 0x7A2AA8))
        nature("cypress", 6, 160, L("t_oval", 0x2F6A3A, n = 1))
        nature("treeAutumn", 7, 200, L("t_round", 0xE0702A))
        nature("bluespruce", 8, 220, L("t_pine", 0x4A8AB8))
        nature("deadtree", 8, 250, L("t_dead"))
        nature("willow", 9, 400, L("t_willow", 0x6FAE4A))
        nature("bigpine", 9, 900, L("t_pine", 0x2F7A4A, n = 1), w = 2, d = 2)
        nature("treeCherry", 10, 300, L("t_round", 0xF4A8C8))
        nature("bonsai", 10, 500, L("t_bonsai", 0x3F8F3A))
        nature("goldtree", 12, 700, L("t_round", 0xF2C21A))
        nature("treeBig", 12, 1500, L("t_round", 0x4F9D37, n = 1), w = 2, d = 2)
        nature("palm", 14, 600, L("t_palm"))
        nature("magictree", 16, 0, L("t_round", 0x5AE0C8, 0xFFFFFF, n = 2), gems = 8)
        nature("rubyGrove", 26, 0, L("t_round", 0xE0304A, 0xFFE0E8, n = 1), w = 2, d = 2, gems = 15)
        nature("giantmushroom", 11, 650, L("n_mushroom", 0xD8403A, n = 1))
        nature("mushrooms", 5, 80, L("n_mushroom", 0xD8403A))
        nature("fairyring", 13, 1500, L("n_fairy"))
        nature("cactus", 9, 300, L("n_cactus"))
        nature("bamboo", 10, 350, L("n_bamboo"))
        nature("reeds", 3, 60, L("n_reeds"), drag = true)
        nature("topiary", 8, 350, L("n_topiary", 0x3F8F3A, n = 0))
        nature("topiaryCone", 9, 380, L("n_topiary", 0x2F8A3A, n = 1))
        nature("topiaryCube", 9, 380, L("n_topiary", 0x4F9D37, n = 2))
        nature("topiarySpiral", 12, 700, L("n_topiary", 0x3F8F3A, n = 3))
        nature("bush", 1, 20, L("n_bush", 0x4F9D37))
        nature("rosebush", 6, 120, L("n_bush", 0x4F9D37, 0xE0304A))
        nature("hedge", 3, 45, L("n_hedge", 0x3F8F3A), drag = true)
        nature("fern", 2, 30, L("n_fern"), drag = true)
        nature("lavender", 7, 90, L("n_flowers", 0x9A6AD8, n = 1), drag = true)
        nature("sunflowers", 5, 90, L("n_sunflower"), drag = true)
        nature("flowersRed", 1, 30, L("n_flowers", 0xE0304A), drag = true)
        nature("flowersYellow", 2, 30, L("n_flowers", 0xF4D42A), drag = true)
        nature("flowersWhite", 2, 30, L("n_flowers", 0xFFFFFF), drag = true)
        nature("flowersBlue", 3, 35, L("n_flowers", 0x4A7AE8), drag = true)
        nature("flowersOrange", 4, 40, L("n_flowers", 0xF08A1C), drag = true)
        nature("flowersPurple", 4, 40, L("n_flowers", 0xA84AD8), drag = true)
        nature("flowersPink", 6, 60, L("n_flowers", 0xF88AB8), drag = true)
        nature("flowersMixed", 5, 55, L("n_flowers", 0, n = 2), drag = true)
        nature("rock", 1, 25, L("n_rock", 0x9A968A))
        nature("bigrock", 6, 400, L("n_rock", 0x8A867A, n = 1), w = 2, d = 2)
        nature("stump", 1, 20, L("n_stump"))
        nature("logpile", 2, 40, L("n_log"))
        nature("pumpkinpatch", 5, 200, L("n_pumpkins"))

        // Les découvertes des chapitres : le joueur choisit toujours leur emplacement.
        shop("adventureInn", 2, 500, 8, 45, "mug", L("asset", n = 0), w = 3, d = 3, pop = 2, build = 60)
        shop("adventureAcademy", 4, 1600, 15, 110, "book", L("asset", n = 1), w = 3, d = 3, pop = 2, build = 180)
        shop("adventureForge", 3, 1100, 12, 85, "lance", L("asset", n = 2), w = 3, d = 3, pop = 2, build = 120)
        shop("adventureStore", 3, 900, 15, 75, "crate", L("asset", n = 3), w = 3, d = 3, pop = 1, build = 90)
        shop("adventureYard", 4, 1400, 18, 105, "shield", L("asset", n = 4), w = 3, d = 3, pop = 2, build = 150)
        shop("adventureGate", 6, 3800, 25, 190, "map", L("asset", n = 5), w = 3, d = 3, pop = 3, build = 240)
        shop("waypost", 2, 600, 8, 55, "map", L("s_hall", 0xE6D6AC, 0x478272, 0xD4AD58), pop = 1, build = 60)
        shop("seedhouse", 4, 900, 15, 115, "flower", L("s_glass", 0xDBEAD6, 0x6FA394, 0xE0BB61), pop = 1, build = 180)
        shop("carpenters", 6, 2500, 25, 240, "wood", L("s_lumber", 0xB59767, 0x506B7D, 0xF4D389), pop = 2)
        shop("gardenremedies", 9, 4500, 40, 420, "potion", L("s_shop", 0xEFE1C9, 0x79609B, 0xB292C8), pop = 2)
        shop("weavery", 12, 11000, 60, 780, "cloth", L("s_hall", 0xE9CFB3, 0xB36076, 0xE5BD67), w = 3, pop = 3)
        shop("cartographers", 15, 22000, 90, 1500, "map", L("s_tower", 0xD9DFCD, 0x527B91, 0xE0BE74, n = 7), pop = 3)
        shop("prismforge", 18, 45000, 120, 2600, "pot", L("s_smith", 0xD7DFDC, 0x408E99, 0xDBAF69), w = 3, pop = 3)
        shop("conservatory", 21, 80000, 180, 4200, "flower", L("s_glass", 0xD4E8D8, 0x548C7E, 0xE2C374), w = 3, d = 3, pop = 4)
        shop("starwatch", 25, 150000, 240, 7500, "star", L("s_tower", 0xDADBE8, 0x535E93, 0xEDD290, n = 5), w = 3, d = 3, pop = 4)
        shop("skygarden", 29, 260000, 360, 13000, "orchid", L("s_pagoda", 0xE9E2CD, 0xA68BB3, 0xE9C677), w = 3, d = 3, pop = 5)
        shop("grandacademy", 33, 450000, 480, 22000, "book", L("s_chapel", 0xE5E3D6, 0x42798E, 0xD8B667), w = 4, d = 3, pop = 6)
        shop("festivalhall", 38, 850000, 720, 40000, "star", L("s_theater", 0xF2DEC1, 0xA85B6F, 0xE4B85F), w = 4, d = 3, pop = 8)

        // ---- sols : peints dans la grille, pas des objets
        path("road", 1, 10, L("p", 0xB89868, 0x8A6A44))
        path("cobble", 6, 40, L("p", 0x9A968A, 0x6A665C, n = 1))
        path("river", 5, 60, L("p", 0x3A8AD8, 0x2A6AB0), over = true)
        path("bridge", 5, 150, L("p", 0x9A6A3A, 0x6A4A22, n = 2))
        path("brickroad", 9, 60, L("p", 0xB8503A, 0x7A3224, n = 1))
        path("flagstone", 12, 80, L("p", 0xC8C4B8, 0x8A867A, n = 1))
        path("goldroad", 20, 500, L("p", 0xE8B82A, 0xA87A12, n = 1))
        path("boardwalk", 4, 50, L("p", 0xA87A44, 0x6A4A22, n = 3))
        path("marblepath", 14, 120, L("p", 0xEDEAE4, 0xA8A498, n = 1))
        path("darkstone", 10, 90, L("p", 0x5A5A62, 0x34343A, n = 1))
        path("mudpath", 2, 8, L("p", 0x7A5230, 0x4A2E14))
        path("redcarpet", 11, 150, L("p", 0xC8302A, 0xF2C230, n = 4))
        path("snowpath", 8, 50, L("p", 0xF0F4FA, 0xB8C8DC))
        path("haypath", 3, 15, L("p", 0xD8B84A, 0xA8801C, n = 5))
        path("sandpath", 5, 20, L("p", 0xE8D8A0, 0xC8B070))
        path("eraser", 1, 0, L("p", 0xE8E0D0, 0xA8A090, n = 9))
        path("land", 5, 0, L("p", 0x6FB83A, 0x4F9D37, n = 8))
        HamletPortCatalog.appendTo(list)
    }

    val defs: List<Def> = list
    val byId: Map<String, Def> = list.associateBy { it.id }
    val pathIds: List<String> = list.filter { it.isPath }.map { it.id }

    /** Numéro de tuile stocké dans la grille pour chaque sol (0 = herbe). */
    val pathCode: Map<String, Int> = run {
        val m = HashMap<String, Int>()
        m["road"] = 1; m["cobble"] = 2; m["river"] = PATH_WATER; m["bridge"] = PATH_BRIDGE
        var next = 5
        for (id in pathIds) if (id !in m && id != "eraser" && id != "land") m[id] = next++
        m
    }
    val pathById: Map<Int, String> = pathCode.entries.associate { it.value to it.key }

    /** Les sols praticables à pied : tout sauf l'eau. */
    fun walkable(code: Int) = code != 0 && code != PATH_WATER && code != pathCode["lavaflow"]

    // ------------------------------------------------------------------ quêtes

    /**
     * La chaîne des quêtes du village. La deuxième offre le premier appel (un ticket), puis quelques-unes
     * en offrent un autre au fil des grands jalons. Les quêtes de récolte demandent une culture précise.
     */
    val quests = listOf(
        // L'ouverture, pas à pas : un semis, la première récolte (et le premier héros), des combats, la première maison, un deuxième champ.
        Quest("planted", 1, 8, 3), Quest("harvested", 1, 15, 6, tickets = 1), Quest("battles_won", 3, 25, 8),
        Quest("built_cottage", 1, 40, 12), Quest("built_field", 1, 50, 15), Quest("taxes", 2, 60, 20),
        Quest("built_bakery", 1, 300, 60), Quest("accepted", 1, 200, 40), Quest("delivered", 1, 300, 60),
        Quest("cat_decor", 5, 500, 120), Quest("cat_nature", 8, 500, 120), Quest("harvested", 20, 600, 150),
        Quest("harvest_carrots", 12, 700, 160), Quest("cat_home", 3, 1000, 250), Quest("upgrades", 1, 600, 150),
        Quest("water", 6, 400, 100), Quest("bridges", 1, 400, 100, 1),
        Quest("tips", 5, 600, 150), Quest("gifts", 1, 500, 120), Quest("delivered", 10, 2500, 600),
        Quest("built_fountain", 1, 1500, 400, 1), Quest("work_used", 300, 500, 200), Quest("castle", 1, 5000, 1500, 2, tickets = 1),
        Quest("earned", 25_000, 5000, 2000), Quest("cat_shop", 10, 6000, 2200), Quest("harvest_strawberries", 24, 4000, 1500),
        Quest("expanded", 3, 6000, 2500, 2), Quest("pop", 40, 8000, 3000), Quest("upgrades", 6, 7000, 3000),
        Quest("harvested", 100, 8000, 3000), Quest("harvest_pumpkins", 20, 9000, 3500), Quest("delivered", 50, 15000, 6000, 3, tickets = 1),
        Quest("earned", 250_000, 40000, 15000, 3), Quest("harvest_grapes", 16, 30000, 12000, 2),
        Quest("harvested", 500, 60000, 25000, 5), Quest("hall", 5, 100000, 40000, 10, tickets = 2),
    )
}
