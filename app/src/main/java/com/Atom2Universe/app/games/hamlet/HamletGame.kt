package com.Atom2Universe.app.games.hamlet

import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/** Une commande passée par un visiteur à un atelier. [t0] vaut 0 tant que l'atelier ne l'a pas acceptée. */
class Order(val from: String, var t0: Long = 0L, var recipeRank: Int = 0, var recipeVersion: Int = 1)

/** Un objet posé dans le village. Les horloges sont en millisecondes d'horloge murale : le village vit hors ligne. */
class Obj(val id: Int, val t: String, var x: Int, var y: Int, var flip: Boolean = false) {
    val def: Def get() = Hamlet.byId.getValue(t)
    val w: Int get() = if (flip) def.d else def.w
    val d: Int get() = if (flip) def.w else def.d

    /** Culture en terre. */
    var crop: String? = null

    /** Plantation (champ) ou dernier impôt perçu (maison). */
    var t0: Long = 0L

    /** Début du chantier ; 0 = terminé. */
    var b0: Long = 0L
    var order: Order? = null

    /** Prochaine commande possible (ateliers). */
    var next: Long = 0L
    var tier = 1
    var tended = false
    /** Session-only guard: undo must not erase an investment or replay a harvested object. */
    internal var economicRevision = 0
}

/** Ce que l'on voit au-dessus d'un objet. */
enum class Status { NONE, BUILDING, GROWING, RIPE, WITHERED, TAX, ORDER_NEW, ORDER_BUSY, ORDER_DONE, GIFT, FEED, PRODUCING, PRODUCT }

interface HamletListener {
    fun onReward(o: Obj?, coins: Int, xp: Int, gems: Int)
    fun onLevelUp(level: Int, gems: Int)

    /** Un message court : [code] est la clé d'un texte, [arg] sa valeur éventuelle. */
    fun onMessage(code: String, arg: String = "")
    fun onChanged()
}

class HamletGame(
    private val clock: () -> Long = System::currentTimeMillis,
    var rng: Random = Random.Default,
) {
    var listener: HamletListener? = null

    var coins = 0L
    var gems = 0
    var xp = 0L
    var hall = 1
    var expand = 0
    var sandbox = false
    var nextId = 1
    var quest = 0
    var questBase = 0L
    var questNoted = -1
    var giftT = 0L
    var created = 0L
    var visitT = 0L
    var dailyDay = -1L
    var dailyClaimed = 0
    val dailyBase = LongArray(3)
    val stats = HashMap<String, Long>()
    /** Réserve de récoltes : unités de chaque culture, qui servent aux améliorations et aux veillées. */
    val pantry = HashMap<String, Int>()
    val objs = LinkedHashMap<Int, Obj>()
    val journey = HamletJourney(this)
    val adventure = HamletAdventure(this)
    val stewardship = HamletStewardship(this)
    val armory = HamletArmory(this)
    val shardShop = HamletShardShop(this)
    val legends = HamletLegends(this)
    val buildHistory = HamletBuildHistory(this)
    val chronicle = HamletChronicle(this)
    /** Travail gratuit mis de côté, plafonné à deux heures ; les gestes du village le remplissent. */
    var workSeconds = 0
    var workStep = 0
    var workTool = -1
    // Les consignes de l'atelier n'influencent pas le hasard des commandes ni celui des cadeaux.
    private val workRng = Random.Default

    /** Sols peints (0 = herbe), puis occupation (0 = libre, sinon l'identifiant de l'objet). */
    val paths = IntArray(Hamlet.GRID * Hamlet.GRID)
    val occ = IntArray(Hamlet.GRID * Hamlet.GRID)
    val realms = HamletRealms(this)
    val neighbors = HamletNeighbors(this)

    /** Augmente à chaque changement de décor : les caches de dessin s'y réfèrent. */
    var rev = 0
        private set

    /** Augmente quand le sol ou les limites changent : le sol en cache doit être repeint. */
    var groundRev = 0
        private set

    fun now() = clock()
    private fun owns(o: Obj) = objs[o.id] === o

    private var charmRev = -1
    private val charmCache = HashMap<Int, Int>()

    // ------------------------------------------------------------------ monnaie et niveau

    fun level() = Hamlet.levelForXp(xp)

    /** Le niveau qui compte pour débloquer : le mode libre ouvre tout. */
    fun ulvl() = if (sandbox) 999 else level()

    fun addCoins(n: Int) { coins += n; changed() }

    fun addXp(n: Int) {
        if (n <= 0) return
        val before = level()
        xp += n
        val after = level()
        if (after > before) {
            var g = 0
            for (l in before + 1..after) g += Hamlet.gemsForLevel(l)
            gems += g
            chronicle.record(HamletChronicle.Kind.LEVEL, amount = after.toLong())
            listener?.onLevelUp(after, g)
        }
        changed()
    }

    fun stat(key: String, n: Long = 1) {
        ensureDaily()
        stewardship.ensureDay()
        stats[key] = (stats[key] ?: 0L) + n
        adventure.villageAction(key, n)
        val work = when (key) { "harvested" -> 10; "products" -> 15; "delivered" -> 30; else -> 0 }
        if (work > 0) workSeconds = (workSeconds + work * n.coerceAtMost(100).toInt()).coerceAtMost(7200)
        checkQuest()
    }

    /** Un bâtiment amélioré rapporte plus de pièces (voir [yieldCoins]) et plus d'expérience, du même pourcentage. */
    fun reward(o: Obj?, coinsGain: Int, baseXp: Int) {
        val xpGain = if (o == null) baseXp else baseXp * (100 + (o.tier - 1) * 25) / 100
        if (coinsGain > 0) { coins += coinsGain; stat("earned", coinsGain.toLong()) }
        listener?.onReward(o, coinsGain, xpGain, 0)
        addXp(xpGain)
    }

    private fun changed() { listener?.onChanged() }

    private fun touch() { rev++ }

    /** Pour la sauvegarde : le décor vient de changer en bloc. */
    fun bump() { rev++; groundRev++ }

    internal fun spendCoins(n: Int): Boolean {
        if (sandbox) return true
        if (coins < n) return false
        coins -= n
        return true
    }

    private fun spendGems(n: Int): Boolean {
        if (sandbox) return true
        if (gems < n) return false
        gems -= n
        return true
    }

    fun canAfford(d: Def) = HamletIndustry.building(d).canPay(this)

    // ------------------------------------------------------------------ terrain

    fun realmSize() = Hamlet.realmSize(hall, expand)
    fun realmOrigin() = (Hamlet.GRID - realmSize()) / 2
    fun inRealm(x: Int, y: Int, w: Int = 1, d: Int = 1): Boolean {
        val o = realmOrigin()
        val r = realmSize()
        return x >= o && y >= o && x + w <= o + r && y + d <= o + r
    }

    fun idx(x: Int, y: Int) = y * Hamlet.GRID + x
    fun inGrid(x: Int, y: Int) = x in 0 until Hamlet.GRID && y in 0 until Hamlet.GRID
    fun pathAt(x: Int, y: Int) = if (inGrid(x, y)) paths[idx(x, y)] else 0
    fun objAt(x: Int, y: Int): Obj? = if (inGrid(x, y)) occ[idx(x, y)].let { if (it == 0) null else objs[it] } else null

    fun isWater(x: Int, y: Int) = pathAt(x, y) == Hamlet.PATH_WATER

    private fun touchesWater(x: Int, y: Int, w: Int, d: Int): Boolean {
        for (j in y - 1..y + d) for (i in x - 1..x + w) {
            val inside = i in x until x + w && j in y until y + d
            if (!inside && isWater(i, j)) return true
        }
        return false
    }

    fun canPlace(id: String, x: Int, y: Int, flip: Boolean, ignoreId: Int = 0): Boolean {
        val def = Hamlet.byId[id] ?: return false
        val w = if (flip) def.d else def.w
        val d = if (flip) def.w else def.d
        if (!inRealm(x, y, w, d)) return false
        for (j in y until y + d) for (i in x until x + w) {
            val o = occ[idx(i, j)]
            val p = paths[idx(i, j)]
            if (o != 0 && o != ignoreId) return false
            if (def.onWater) { if (p != Hamlet.PATH_WATER) return false } else if (p != 0) return false
        }
        if (def.shore && !touchesWater(x, y, w, d)) return false
        return true
    }

    private fun setOcc(o: Obj, v: Int) {
        for (j in o.y until o.y + o.d) for (i in o.x until o.x + o.w) occ[idx(i, j)] = v
    }

    private fun insert(o: Obj) {
        objs[o.id] = o
        setOcc(o, o.id)
        touch()
    }

    // ------------------------------------------------------------------ états d'un objet

    fun buildLeft(o: Obj): Long {
        val d = o.def
        if (o.b0 == 0L || d.build <= 0) return 0
        return max(0L, o.b0 + d.build * 1000L - now())
    }

    fun cropOf(o: Obj): Crop? = o.crop?.let { Hamlet.cropById[it] }
    fun cropLeft(o: Obj): Long = cropOf(o)?.let { max(0L, o.t0 + it.time * 1000L - now()) } ?: 0L
    private fun witherAt(o: Obj, c: Crop) = o.t0 + (c.time * 2L + if (o.tended) 86400 else 3600) * 1000L

    /** 0 jeune pousse, 1 en croissance, 2 mûr, 3 flétri. */
    fun cropStage(o: Obj): Int {
        val c = cropOf(o) ?: return 0
        val e = (now() - o.t0).toDouble() / (c.time * 1000.0)
        if (e >= 1) return if (now() > witherAt(o, c)) 3 else 2
        return if (e < 0.4) 0 else 1
    }

    fun cropProgress(o: Obj): Float {
        val c = cropOf(o) ?: return 0f
        return ((now() - o.t0).toDouble() / (c.time * 1000.0)).coerceIn(0.0, 1.0).toFloat()
    }

    fun taxLeft(o: Obj): Long = max(0L, o.t0 + o.def.period * 1000L - now())
    fun jobLeft(o: Obj): Long = o.order?.takeIf { it.t0 != 0L }?.let { max(0L, it.t0 + o.def.job * 1000L - now()) } ?: 0L

    fun status(o: Obj): Status {
        val d = o.def
        if (buildLeft(o) > 0) return Status.BUILDING
        if (o.t in HamletLife.produce) return when {
            d.cat == Cat.RANCH && o.t0 == 0L -> Status.FEED
            productCount(o) > 0 -> Status.PRODUCT
            else -> Status.PRODUCING
        }
        return when (d.cat) {
            Cat.HALL -> if (giftReady()) Status.GIFT else Status.NONE
            Cat.FIELD -> if (o.crop == null) Status.NONE else when (cropStage(o)) {
                2 -> Status.RIPE
                3 -> Status.WITHERED
                else -> Status.GROWING
            }
            Cat.HOME -> if (taxLeft(o) <= 0) Status.TAX else Status.NONE
            Cat.SHOP -> {
                val ord = o.order
                when {
                    ord == null -> Status.NONE
                    ord.t0 == 0L -> Status.ORDER_NEW
                    jobLeft(o) <= 0 -> Status.ORDER_DONE
                    else -> Status.ORDER_BUSY
                }
            }
            else -> Status.NONE
        }
    }

    // ------------------------------------------------------------------ charme

    private fun ensureCharm() {
        if (charmRev == rev) return
        charmCache.clear()
        charmRev = rev
    }

    /** Points de charme autour d'une maison ou d'un atelier : chaque décor ou plante voisin compte une fois. */
    fun charmPoints(o: Obj): Int {
        ensureCharm()
        return charmCache.getOrPut(o.id) {
            val seen = HashSet<Int>()
            var sum = 0
            val r = CHARM_RANGE
            for (j in o.y - r until o.y + o.d + r) for (i in o.x - r until o.x + o.w + r) {
                if (!inGrid(i, j)) continue
                val id = occ[idx(i, j)]
                if (id == 0 || id == o.id || !seen.add(id)) continue
                val n = objs[id] ?: continue
                if (n.def.charm > 0 && buildLeft(n) == 0L) sum += n.def.charm
            }
            sum
        }
    }

    /** Le supplément d'impôt (en %) que le charme apporte, plafonné. */
    fun charmBonus(o: Obj): Int {
        val d = o.def
        val raw = (charmPoints(o) * CHARM_PERCENT).toInt()
        return when (d.cat) {
            Cat.HOME -> min(CHARM_CAP, raw)
            Cat.SHOP -> min(CHARM_CAP / 2, raw / 2)
            else -> 0
        }
    }

    fun population(): Int {
        var n = 0
        for (o in objs.values) if (buildLeft(o) == 0L) n += o.def.pop
        return n
    }

    fun happiness(): Int {
        val complete = objs.values.filter { buildLeft(it) == 0L }
        val services = complete.map { it.t }.toSet().count { it in HamletLife.services }
        val variety = complete.filter { it.def.cat == Cat.NATURE || it.def.cat == Cat.DECOR }.map { it.t }.toSet().size
        return (55 + services * 5 + min(20, variety * 2) + min(10, complete.count { it.def.cat == Cat.RANCH } * 2)).coerceAtMost(100)
    }

    /** Améliorer augmente les recettes de 25 % par rang, jusqu'au rang 5 ; jamais les durées. */
    fun upgradeCost(o: Obj) = HamletEconomy.upgradeCoins(o.def.cost, o.tier)
    /** Les récoltes demandées en plus des pièces pour passer au rang suivant. */
    fun upgradeNeeds(o: Obj) = HamletEconomy.upgradeNeeds(o.def, o.tier + 1)
    fun canUpgrade(o: Obj) = owns(o) && o.tier < HamletEconomy.MAX_TIER && o.def.gems == 0 && buildLeft(o) == 0L &&
        (o.def.cat in setOf(Cat.HOME, Cat.SHOP, Cat.FIELD, Cat.RANCH) || o.t in HamletLife.produce)
    fun canAffordUpgrade(o: Obj) = canUpgrade(o) && HamletIndustry.upgrade(o).canPay(this)
    fun upgrade(o: Obj): Boolean {
        if (!canAffordUpgrade(o)) return false
        val cost = upgradeCost(o)
        if (!HamletIndustry.upgrade(o).pay(this)) return false
        adventure.advanceProduction(true)
        o.tier++
        o.economicRevision++
        stat("upgrades"); addXp(cost / 15)
        touch(); changed()
        return true
    }

    // ------------------------------------------------------------------ réserve de récoltes

    fun pantryCap() = HamletEconomy.pantryCap(realms.bestHall())
    fun stock(crop: String) = pantry[crop] ?: 0
    /** Range des unités ; ce qui dépasse la place de la réserve est perdu (les pièces sont déjà versées). */
    fun addStock(crop: String, units: Int) {
        if (units <= 0 || crop !in Hamlet.cropById) return
        pantry[crop] = maxOf(stock(crop), minOf(pantryCap().toLong(), stock(crop).toLong() + units).toInt())
    }
    fun yieldCoins(o: Obj, base: Int) = base * (100 + (o.tier - 1) * 25 + journey.bonus(o.def.cat) + stewardship.villageBonus(o.def.cat)) / 100

    /** Douze consignes de chantier, sans chronomètre ni dépense. Une erreur conserve l'avancement. */
    fun beginWork() { if (workTool < 0) workTool = workRng.nextInt(3) }
    fun answerWork(tool: Int): Boolean {
        if (workTool < 0 || tool != workTool) return false
        workStep++
        if (workStep == 12) {
            workSeconds = (workSeconds + 300).coerceAtMost(7200)
            workStep = 0; stat("workshops")
        }
        // Ne pas répéter la même consigne : le joueur lit et choisit au lieu de marteler un bouton.
        workTool = (workTool + workRng.nextInt(1, 3)) % 3
        changed(); return true
    }
    fun workLeft(o: Obj): Long = when {
        !owns(o) -> 0
        buildLeft(o) > 0 -> buildLeft(o)
        o.crop != null && cropStage(o) < 2 -> cropLeft(o)
        o.order?.t0?.let { it > 0 } == true -> jobLeft(o)
        o.t in HamletLife.produce && o.t0 > 0 -> productLeft(o)
        else -> 0
    }
    fun applyWork(o: Obj): Boolean {
        val left = workLeft(o)
        if (left <= 0 || workSeconds <= 0) return false
        adventure.advanceProduction(true)
        val used = minOf(workSeconds, ((left + 999) / 1000).toInt(), 300)
        val ms = used * 1000L
        when {
            buildLeft(o) > 0 -> {
                o.b0 -= ms
                // Les producteurs autonomes doivent démarrer à la fin du chantier avancé.
                if (o.t in HamletLife.produce && o.def.cat != Cat.RANCH) o.t0 -= ms
                if (buildLeft(o) == 0L) o.b0 = 0
            }
            o.crop != null -> o.t0 -= ms
            o.order != null -> o.order!!.t0 -= ms
            else -> o.t0 -= ms
        }
        o.economicRevision++
        workSeconds -= used; stat("work_used", used.toLong()); touch(); changed(); return true
    }
    fun workOnExpedition(): Boolean {
        val left = journey.expeditionLeft()
        if (journey.expedition < 0 || left <= 0 || workSeconds <= 0) return false
        val used = minOf(workSeconds, ((left + 999) / 1000).toInt(), 300)
        journey.arrival -= used * 1000L
        workSeconds -= used; stat("work_used", used.toLong()); changed(); return true
    }

    fun productLeft(o: Obj): Long {
        val p = HamletLife.produce[o.t] ?: return 0
        return if (o.t0 == 0L) 0 else max(0, o.t0 + p.seconds * 1000L - now())
    }
    fun productCount(o: Obj): Int {
        val p = HamletLife.produce[o.t] ?: return 0
        if (o.t0 == 0L || buildLeft(o) > 0) return 0
        return ((now() - o.t0).coerceAtLeast(0) / (p.seconds * 1000L)).coerceAtMost(if (p.feed > 0) 1L else 3L).toInt()
    }
    fun feed(o: Obj): Boolean {
        val p = HamletLife.produce[o.t] ?: return false
        if (!owns(o) || p.feed == 0 || o.t0 != 0L || buildLeft(o) > 0) return false
        if (!HamletIndustry.feed(o).pay(this)) { listener?.onMessage("industry_missing"); return false }
        o.t0 = now()
        o.economicRevision++
        stat("fed"); touch(); changed()
        return true
    }
    fun collectProduct(o: Obj): Boolean {
        if (!owns(o)) return false
        val p = HamletLife.produce[o.t] ?: return false
        val count = productCount(o)
        if (count == 0) return false
        // Conserver la fraction de croissance, mais jeter le surplus au-delà du stockage.
        o.t0 = if (p.feed > 0) 0 else now() - (now() - o.t0) % (p.seconds * 1000L)
        o.economicRevision++
        HamletIndustry.products(o.t, o.tier).forEach { (r, n) -> adventure.add(r, n * count) }
        stat("products", count.toLong())
        reward(o, yieldCoins(o, p.coins) * count, p.xp * count)
        touch(); changed()
        return true
    }
    fun tendCost(o: Obj) = 2 + (cropOf(o)?.cost ?: 0) / 10
    fun tend(o: Obj): Boolean {
        if (!owns(o) || o.crop == null || o.tended || cropStage(o) >= 2 || !spendCoins(tendCost(o))) return false
        o.tended = true
        o.economicRevision++
        stat("tended"); touch(); changed()
        return true
    }

    fun canCollectAll() = sandbox || journey.chapter >= COLLECT_ALL_CHAPTER
    /** Récompense gratuite d'un seul bâtiment, accessible dès le premier geste. */
    fun collectReady(o: Obj): Boolean {
        if (!owns(o)) return false
        return when (status(o)) {
            Status.RIPE, Status.WITHERED -> harvest(o)
            Status.TAX -> collectTax(o)
            Status.ORDER_DONE -> deliver(o)
            Status.PRODUCT -> collectProduct(o)
            Status.GIFT -> openGift()
            else -> false
        }
    }
    /** Confort débloqué tard : laisse les champs vides et les élevages au repos. */
    fun collectAll(): Int {
        if (!canCollectAll()) return 0
        var count = 0
        for (o in objs.values.toList()) {
            if (collectReady(o)) count++
        }
        return count
    }
    fun plantAll(crop: String): Int = objs.values.toList().count { it.def.cat == Cat.FIELD && plant(it, crop) }

    fun ensureDaily() {
        val day = now() / Hamlet.DAY_MS
        if (day <= dailyDay) return
        dailyDay = day; dailyClaimed = 0
        for (i in dailyBase.indices) dailyBase[i] = stats[HamletLife.dailyKeys[i]] ?: 0L
    }
    fun dailyProgress(i: Int): Long {
        ensureDaily()
        return max(0, (stats[HamletLife.dailyKeys[i]] ?: 0) - dailyBase[i])
    }
    fun claimDaily(i: Int): Boolean {
        if (i !in dailyBase.indices || dailyProgress(i) < HamletLife.dailyTargets[i] || dailyClaimed and (1 shl i) != 0) return false
        dailyClaimed = dailyClaimed or (1 shl i)
        reward(null, HamletLife.dailyCoins[i], 25)
        changed()
        return true
    }

    // ------------------------------------------------------------------ champs

    fun plant(o: Obj, cropId: String): Boolean {
        val c = Hamlet.cropById[cropId] ?: return false
        if (!owns(o) || o.def.cat != Cat.FIELD || o.crop != null || buildLeft(o) > 0 || ulvl() < c.lvl) return false
        if (c.magic != o.def.magic) return false
        if (!spendCoins(c.cost)) { listener?.onMessage("poor"); return false }
        o.crop = cropId
        o.economicRevision++
        o.tended = false
        o.t0 = now()
        stat("planted"); stat("plant_$cropId")
        touch(); changed()
        return true
    }

    fun harvest(o: Obj): Boolean {
        if (!owns(o) || o.def.cat != Cat.FIELD || buildLeft(o) > 0) return false
        val c = cropOf(o) ?: return false
        val st = cropStage(o)
        if (st < 2) return false
        o.economicRevision++
        if (st == 3) listener?.onMessage("withered")
        else {
            val gain = yieldCoins(o, c.earn) * (if (o.tended) 120 else 100) / 100
            reward(o, gain, c.xp)
            addStock(c.id, HamletEconomy.UNITS_PER_HARVEST + if (o.tended) 1 else 0)
            stat("harvested"); stat("harvest_${c.id}")
        }
        o.crop = null
        o.t0 = 0
        o.tended = false
        touch(); changed()
        return true
    }

    /** Un champ prêt qui attend (pour la pastille du hub). */
    fun readyCount(): Int = objs.values.count {
        val s = status(it)
        s == Status.RIPE || s == Status.TAX || s == Status.ORDER_DONE || s == Status.ORDER_NEW || s == Status.PRODUCT
    }
    fun collectableCount(): Int = objs.values.count {
        when (status(it)) {
            Status.RIPE, Status.WITHERED, Status.TAX, Status.ORDER_DONE, Status.PRODUCT, Status.GIFT -> true
            else -> false
        }
    }

    // ------------------------------------------------------------------ maisons

    fun collectTax(o: Obj): Boolean {
        val d = o.def
        if (!owns(o) || d.cat != Cat.HOME || buildLeft(o) > 0 || taxLeft(o) > 0) return false
        val bonus = charmBonus(o) + max(0, happiness() - 70) / 2
        o.t0 = now()
        o.economicRevision++
        reward(o, yieldCoins(o, d.tax + d.tax * bonus / 100), d.taxXp)
        stat("taxes")
        touch()
        return true
    }

    // ------------------------------------------------------------------ ateliers

    fun acceptOrder(o: Obj): Boolean {
        if (!owns(o) || o.def.cat != Cat.SHOP || buildLeft(o) > 0) return false
        val ord = o.order ?: return false
        if (ord.t0 != 0L) return false
        if (!HamletIndustry.recipe(o.t, o.tier).cost.pay(this)) { listener?.onMessage("industry_missing"); return false }
        ord.recipeRank = o.tier
        ord.recipeVersion = HamletIndustry.RECIPE_VERSION
        ord.t0 = now()
        o.economicRevision++
        stat("accepted")
        touch(); changed()
        return true
    }

    fun declineOrder(o: Obj) {
        if (!owns(o) || o.def.cat != Cat.SHOP || o.order?.t0 != 0L) return
        o.order = null
        o.economicRevision++
        o.next = now() + rng.nextLong(20_000, 60_000)
        touch(); changed()
    }

    fun deliver(o: Obj): Boolean {
        if (!owns(o) || o.def.cat != Cat.SHOP || buildLeft(o) > 0) return false
        val d = o.def
        val ord = o.order ?: return false
        if (ord.t0 == 0L || jobLeft(o) > 0) return false
        val bonus = charmBonus(o)
        val from = ord.from
        if (ord.recipeRank > 0) HamletIndustry.recipe(o.t, ord.recipeRank, ord.recipeVersion).output.forEach { (r, n) -> adventure.add(r, n) }
        o.order = null
        o.economicRevision++
        o.next = now() + rng.nextLong(25_000, 110_000)
        reward(o, yieldCoins(o, d.pay + d.pay * bonus / 100), d.payXp)
        stat("delivered")
        chronicle.record(HamletChronicle.Kind.DELIVERY, subject = o.t, actor = from)
        listener?.onMessage("picked_up", from)
        touch()
        return true
    }

    // ------------------------------------------------------------------ presser avec des gemmes

    fun hurryCost(o: Obj): Int {
        val d = o.def
        val left = when {
            buildLeft(o) > 0 -> buildLeft(o)
            d.cat == Cat.FIELD && o.crop != null && cropLeft(o) > 0 -> cropLeft(o)
            d.cat == Cat.SHOP && o.order?.t0 != 0L && o.order != null && jobLeft(o) > 0 -> jobLeft(o)
            o.t in HamletLife.produce && o.t0 != 0L && productLeft(o) > 0 -> productLeft(o)
            else -> 0L
        }
        return if (left <= 0) 0 else Hamlet.gemsFor(left)
    }

    fun hurry(o: Obj): Boolean {
        if (!owns(o)) return false
        val cost = hurryCost(o)
        if (cost <= 0) return false
        if (!spendGems(cost)) { listener?.onMessage("poor_gems"); return false }
        adventure.advanceProduction(true)
        val d = o.def
        when {
            buildLeft(o) > 0 -> { o.b0 = 0; if (o.t in HamletLife.produce && o.def.cat != Cat.RANCH) o.t0 = now() }
            d.cat == Cat.FIELD -> o.t0 = now() - cropOf(o)!!.time * 1000L
            d.cat == Cat.SHOP -> o.order!!.t0 = now() - d.job * 1000L
            o.t in HamletLife.produce -> o.t0 = now() - HamletLife.produce.getValue(o.t).seconds * 1000L
        }
        stat("gemsUsed", cost.toLong())
        o.economicRevision++
        touch(); changed()
        return true
    }

    // ------------------------------------------------------------------ construire, déplacer, vendre

    fun buy(id: String, x: Int, y: Int, flip: Boolean): Obj? {
        val d = Hamlet.byId[id] ?: return null
        if (d.isPath || d.cat == Cat.HALL) return null
        if (!journey.unlocked(d) || !canAfford(d) || !canPlace(id, x, y, flip)) return null
        val beforeLevel = level()
        val paidCoins = if (!sandbox && d.gems == 0) d.cost.toLong() else 0L
        val paidGems = if (!sandbox) d.gems else 0
        val receipt = HamletIndustry.building(d)
        if (!receipt.pay(this)) return null
        adventure.advanceProduction(true)
        val o = Obj(nextId++, id, x, y, flip)
        if (d.build > 0 && !sandbox) o.b0 = now()
        if (d.cat == Cat.HOME) o.t0 = now()
        if (d.cat == Cat.SHOP) o.next = now() + rng.nextLong(8_000, 30_000)
        if (id in HamletLife.produce && d.cat != Cat.RANCH) o.t0 = now() + d.build * 1000L
        insert(o)
        addXp(d.xp)
        stat("built"); stat("built_$id"); stat("cat_${d.cat.name.lowercase()}")
        buildHistory.bought(o, paidCoins, paidGems, constructionRewards(d.xp, beforeLevel, "built", "built_$id", "cat_${d.cat.name.lowercase()}"), if (sandbox) emptyMap() else receipt.resources)
        buildHistory.noteRecent(id)
        changed()
        return o
    }

    fun move(o: Obj, x: Int, y: Int, flip: Boolean): Boolean {
        if (!owns(o) || o.def.cat == Cat.HALL || !canPlace(o.t, x, y, flip, o.id)) return false
        if (o.x == x && o.y == y && o.flip == flip) return true
        val oldX = o.x; val oldY = o.y; val oldFlip = o.flip
        setOcc(o, 0)
        o.x = x; o.y = y; o.flip = flip
        setOcc(o, o.id)
        buildHistory.moved(o, oldX, oldY, oldFlip)
        touch(); changed()
        return true
    }

    fun sellValue(o: Obj) = o.def.cost / 4

    fun sell(o: Obj): Boolean {
        if (o.def.cat == Cat.HALL || objs[o.id] !== o) return false
        adventure.advanceProduction(true)
        setOcc(o, 0)
        objs.remove(o.id)
        val v = sellValue(o)
        if (v > 0) coins += v
        buildHistory.sold(o, v.toLong())
        touch(); changed()
        return true
    }

    /** A stamp covers its w × d footprint and charges once, as in the source game. */
    fun paint(id: String, x: Int, y: Int): Boolean {
        val d = Hamlet.byId[id] ?: return false
        if (!d.isPath || !journey.unlocked(d)) return false
        val clearing = id == "eraser" || id == "land" || id == "landbig"
        val code = when (id) { "eraser", "land", "landbig" -> 0; "lake", "sea" -> Hamlet.PATH_WATER; else -> Hamlet.pathCode[id] ?: return false }
        val tiles = ArrayList<Int>()
        for (ty in y until y + d.d) for (tx in x until x + d.w) {
            if (!inRealm(tx, ty)) continue
            val at = idx(tx, ty); val cur = paths[at]; val obj = objAt(tx, ty)
            val valid = when {
                id == "eraser" -> cur != 0 && obj?.def?.onWater != true
                id == "land" || id == "landbig" -> cur == Hamlet.PATH_WATER && obj?.def?.onWater != true
                obj != null -> false
                id == "bridge" -> cur == Hamlet.PATH_WATER
                else -> cur != code && (cur == 0 || code == Hamlet.PATH_WATER || d.magic)
            }
            if (valid) tiles.add(at)
        }
        if (tiles.isEmpty()) return false
        if (!spendCoins(d.cost)) { listener?.onMessage("poor"); return false }
        val beforeLevel = level()
        val rewards = if (clearing) null else {
            addXp(d.xp)
            val delta = linkedMapOf("roads" to tiles.size.toLong())
            if (code == Hamlet.PATH_WATER) delta["water"] = tiles.size.toLong()
            if (id == "bridge") delta["bridges"] = tiles.size.toLong()
            delta.forEach { (key, amount) -> stat(key, amount) }
            HamletBuildHistory.Rewards(d.xp, beforeLevel + 1, level(), delta, quest, journey.chapter)
        }
        buildHistory.beginGesture()
        for ((i, at) in tiles.withIndex()) {
            val before = paths[at]; paths[at] = code
            buildHistory.ground(at, before, code, if (i == 0 && !sandbox) d.cost.toLong() else 0, if (i == 0) rewards else null)
        }
        buildHistory.endGesture(); groundRev++
        touch(); changed()
        return true
    }

    private fun constructionRewards(amount: Int, beforeLevel: Int, vararg keys: String) = HamletBuildHistory.Rewards(
        amount, beforeLevel + 1, level(), keys.associateWith { 1L }, quest, journey.chapter)

    /** Source game's free terrain tool: only empty grass floods; boats retain their water. */
    fun floodRealm(on: Boolean): Int {
        buildHistory.beginGesture()
        var count = 0
        val origin = realmOrigin()
        for (y in origin until origin + realmSize()) for (x in origin until origin + realmSize()) {
            val k = idx(x, y); val before = paths[k]
            val valid = if (on) before == 0 && occ[k] == 0
                else before == Hamlet.PATH_WATER && objAt(x, y)?.def?.onWater != true
            if (!valid) continue
            paths[k] = if (on) Hamlet.PATH_WATER else 0
            buildHistory.ground(k, before, paths[k]); count++
        }
        buildHistory.endGesture()
        if (count > 0) { bump(); changed() }
        return count
    }

    /** Fill a connected empty patch. Each tile obeys the same costs and rules as the brush. */
    fun fillConnected(id: String, x: Int, y: Int): Int {
        val def = Hamlet.byId[id] ?: return 0
        if (!def.isPath || def.w != 1 || def.d != 1 || !inRealm(x, y) || objAt(x, y) != null || !journey.unlocked(def)) return 0
        val original = pathAt(x, y)
        val queue = java.util.ArrayDeque<Int>()
        val seen = BooleanArray(paths.size)
        queue.add(idx(x, y)); seen[idx(x, y)] = true
        buildHistory.beginGesture()
        var count = 0
        while (queue.isNotEmpty()) {
            val at = queue.removeFirst(); val tx = at % Hamlet.GRID; val ty = at / Hamlet.GRID
            if (paths[at] != original || occ[at] != 0) continue
            if (!paint(id, tx, ty)) continue
            count++
            if (!canAfford(def)) break
            for ((dx, dy) in arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
                val nx = tx + dx; val ny = ty + dy
                if (!inRealm(nx, ny)) continue
                val next = idx(nx, ny)
                if (!seen[next]) { seen[next] = true; queue.addLast(next) }
            }
        }
        buildHistory.endGesture()
        return count
    }

    // ------------------------------------------------------------------ halle et terres

    fun nextHall(): HallLevel? = Hamlet.halls.getOrNull(hall)

    fun upgradeHall(): Boolean {
        val n = nextHall() ?: return false
        if (ulvl() < n.lvl || !journey.canUpgradeHall() || !HamletIndustry.hall(this).pay(this)) return false
        hall++
        groundRev++
        addXp(n.cost / 20)
        stat("castle")
        chronicle.record(HamletChronicle.Kind.HALL, amount = hall.toLong())
        touch(); changed()
        return true
    }

    fun expandCost() = if (sandbox) 0 else Hamlet.expandCost(expand)
    fun canExpand() = realmSize() < Hamlet.MAX_REALM

    fun expandLand(): Boolean {
        if (!canExpand()) return false
        if (!HamletIndustry.land(this).pay(this)) { listener?.onMessage("industry_missing"); return false }
        val c = Hamlet.expandCost(expand)
        expand++
        groundRev++
        addXp(c / 25)
        stat("expanded")
        chronicle.record(HamletChronicle.Kind.EXPANSION, amount = realmSize().toLong())
        touch(); changed()
        return true
    }

    // ------------------------------------------------------------------ cadeau du jour et pourboires

    fun giftReady() = now() - giftT >= Hamlet.DAY_MS

    fun openGift(): Boolean {
        if (!giftReady()) return false
        giftT = now()
        val l = level()
        val roll = rng.nextDouble()
        var c = 0
        var g = 0
        val x = 20 * l
        if (roll < 0.2) g = 1 + if (l >= 15) 1 else 0
        else c = (Math.round((60 + rng.nextDouble() * 80) * (1 + l * 0.6) / 10) * 10).toInt()
        if (c > 0) { coins += c; stat("earned", c.toLong()) }
        gems += g
        // Le premier cadeau est une surprise ; les suivants glissent un appel dans le paquet.
        if ((stats["gifts"] ?: 0L) >= 1) adventure.add(AdventureResource.TICKETS, 1)
        listener?.onReward(null, c, x, g)
        addXp(x)
        stat("gifts")
        chronicle.record(HamletChronicle.Kind.GIFT)
        touch(); changed()
        return true
    }

    fun tip(): Int {
        val c = (rng.nextInt(6, 16) * (1 + level() * 0.3)).toInt()
        coins += c
        stat("earned", c.toLong()); stat("tips")
        listener?.onReward(null, c, 0, 0)
        changed()
        return c
    }

    // ------------------------------------------------------------------ quêtes

    fun currentQuest(): Quest? = Hamlet.quests.getOrNull(quest)

    fun questProgress(q: Quest): Long = when (q.key) {
        "pop" -> population().toLong()
        "hall" -> hall.toLong()
        // Les jalons reconnaissent ce qui a déjà été fait : construire tôt ne bloque pas une quête.
        else -> max(0L, stats[q.key] ?: 0L)
    }

    fun questDone(q: Quest) = questProgress(q) >= q.n

    private fun checkQuest() {
        val q = currentQuest() ?: return
        if (questDone(q) && questNoted != quest) {
            questNoted = quest
            listener?.onMessage("quest_done")
        }
    }

    fun claimQuest(): Boolean {
        val q = currentQuest() ?: return false
        if (!questDone(q)) return false
        quest++
        questBase = currentQuest()?.let { stats[it.key] ?: 0L } ?: 0L
        coins += q.coins
        gems += q.gems
        adventure.add(AdventureResource.TICKETS, q.tickets.toLong())
        stat("earned", q.coins.toLong())
        listener?.onReward(null, q.coins, q.xp, q.gems)
        addXp(q.xp)
        checkQuest()
        changed()
        return true
    }

    // ------------------------------------------------------------------ le temps qui passe

    private var tickAt = 0L

    /** À appeler souvent : termine les chantiers, fait venir les commandes. Rend vrai si l'allure d'un objet a changé. */
    fun tick(): Boolean {
        val t = now()
        adventure.advanceProduction(objs.values.any { it.b0 != 0L && buildLeft(it) == 0L })
        ensureDaily()
        if (t - tickAt < 400) return false
        tickAt = t
        var dirty = false
        for (o in objs.values) {
            val d = o.def
            if (o.b0 != 0L && buildLeft(o) <= 0) {
                o.b0 = 0
                dirty = true
                listener?.onMessage("built", o.t)
            }
            if (d.cat == Cat.SHOP && o.order == null && buildLeft(o) <= 0) {
                if (o.next == 0L) o.next = t + rng.nextLong(10_000, 60_000)
                if (t >= o.next) {
                    o.order = Order(Hamlet.visitors.random(rng))
                    dirty = true
                    listener?.onMessage("order_new", o.t)
                }
            }
        }
        if (dirty) { touch(); changed() }
        return dirty
    }

    // ------------------------------------------------------------------ nouvelle partie

    fun startNew(sandboxMode: Boolean) {
        sandbox = sandboxMode
        objs.clear(); stats.clear(); pantry.clear(); paths.fill(0); occ.fill(0)
        journey.reset(); workSeconds = 0; workStep = 0; workTool = -1
        adventure.reset()
        stewardship.reset()
        armory.reset()
        shardShop.reset()
        legends.reset()
        buildHistory.clear(); buildHistory.restoreRecent(emptyList()); realms.reset(); neighbors.reset(); chronicle.reset()
        nextId = 1; quest = 0; questBase = 0; questNoted = -1; expand = 0; hall = 1; xp = 0
        dailyDay = -1; dailyClaimed = 0; dailyBase.fill(0); tickAt = 0; giftT = 0; visitT = 0
        sandbox = sandboxMode
        coins = 250; gems = 5
        created = now()
        giftT = now() - Hamlet.DAY_MS + 180_000
        val o = realmOrigin()
        val hx = o + realmSize() / 2 - 2
        val hy = o + 1
        insert(Obj(nextId++, "hall", hx, hy))
        // une allée jusqu'à la halle, un carrefour, deux champs et une chaumière
        for (y in hy + 4..hy + 12) { put(hx + 1, y, 1); put(hx + 2, y, 1) }
        for (x in hx - 4..hx + 7) put(x, hy + 8, 1)
        val fx = hx - 3
        val fy = hy + 10
        place("field", fx, fy); place("field", fx + 2, fy)
        place("cottage", hx + 5, hy + 9)?.t0 = now()
        scatter(Random(7), o, 0.06)
        ensureDaily()
        chronicle.record(HamletChronicle.Kind.WELCOME)
        stewardship.ensureDay()
        groundRev++
        touch(); changed()
    }

    private fun put(x: Int, y: Int, code: Int) { if (inRealm(x, y)) paths[idx(x, y)] = code }

    private fun place(id: String, x: Int, y: Int): Obj? {
        if (!canPlace(id, x, y, false)) return null
        val o = Obj(nextId++, id, x, y)
        insert(o)
        return o
    }

    private fun scatter(r: Random, o: Int, prob: Double) {
        val ids = listOf("treeRound", "treeOval", "treePine", "bush", "rock", "flowersRed", "flowersYellow")
        val s = realmSize()
        for (y in o until o + s) for (x in o until o + s) if (r.nextDouble() < prob) place(ids[r.nextInt(ids.size)], x, y)
    }

    /** Un village de démonstration, pour essayer le dessin et les règles d'un coup d'œil. */
    fun fillDemo(r: Random = Random(3)) {
        startNew(true)
        hall = 5
        val o = realmOrigin()
        val homes = Hamlet.defs.filter { it.cat == Cat.HOME && it.gems == 0 }
        val shops = Hamlet.defs.filter { it.cat == Cat.SHOP && it.gems == 0 }
        var x = o + 1
        var y = o + 7
        var rowH = 0
        for (d in homes + shops) {
            if (x + d.w > o + realmSize() - 1) { x = o + 1; y += rowH + 1; rowH = 0 }
            if (y + d.d > o + realmSize() - 1) break
            val ob = place(d.id, x, y)
            if (ob != null) { if (d.cat == Cat.HOME) ob.t0 = now() - d.period * 1000L - 1 }
            x += d.w + 1
            rowH = max(rowH, d.d)
        }
        touch(); changed()
    }

    companion object {
        const val COLLECT_ALL_CHAPTER = 10
        const val CHARM_RANGE = 4
        const val CHARM_PERCENT = 0.8
        const val CHARM_CAP = 40
    }
}
