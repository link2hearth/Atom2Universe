package com.Atom2Universe.app.zoomcanvas.core

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Ce qu'une couche contient : un trait (gomme comprise), une image, une forme ou un texte, chacun avec son identifiant. */
sealed interface LayerItem {
    val id: Long
}

/**
 * Une couche : son ancre dans la couche du dessus (en unités de celle-ci) et ses éléments, en
 * coordonnées locales. Tout se dessine dans l'ordre de la pile ([entries]) : un coup de gomme creuse
 * ce qui a été posé avant lui, jamais ce qui vient après.
 *
 * Chaque élément est rangé dans une [Entry] qui porte son rang de dessin (`z`, strictement
 * croissant le long de la pile, avec des trous : en poser un entre deux autres ne renumérote rien)
 * et sa boîte. Un index spatial ([LayerIndex]) retrouve ce qui touche un rectangle sans parcourir
 * toute la couche : le rendu, la sélection et l'enregistrement coûtent ce qu'ils touchent, pas la
 * taille du dessin.
 *
 * Tout se passe sur le fil de l'écran ; seules des [Entry] déjà construites (immuables) peuvent
 * être lues ailleurs.
 */
class Layer(val depth: Long, var ax: Double, var ay: Double) {

    /** Le journal où chaque pose, retrait ou remplacement est noté (null tant que la couche n'est pas dans une scène). */
    internal var journal: ChangeLog? = null

    private val byId = HashMap<Long, Entry>()
    private val zList = ArrayList<Entry>()
    private val index = LayerIndex()
    private var eraserCount = 0

    /**
     * Les éléments sont-ils en mémoire ? Une couche qu'on ne voit pas est **déchargée** : elle ne
     * garde que son ancre et un résumé (de quoi dire si elle est vide, jusqu'où elle s'étend, si elle
     * a des gommes), et ses éléments restent dans la base jusqu'à ce que la caméra s'en approche.
     * Déchargée, elle ne se trace pas et ne se modifie pas.
     */
    var isLoaded = true
        private set

    private var sumCount = 0
    private var sumErasers = 0
    private var sumBounds: DoubleArray? = null

    /**
     * Changée depuis la dernière écriture réussie : on ne la décharge pas (la base ne la connaît pas
     * encore) ; voir [markSaved].
     */
    var dirty = false
        private set

    /** Dit que la couche, telle qu'elle était à la [version] donnée, est écrite. Si elle a bougé depuis, elle reste à écrire. */
    internal fun markSaved(atVersion: Long) {
        if (version == atVersion) dirty = false
    }

    /**
     * Décharge les éléments de la mémoire en gardant le résumé. Refusé (faux) si la couche a des
     * changements pas encore écrits.
     */
    fun unload(): Boolean {
        if (!isLoaded || dirty) return false
        sumCount = byId.size
        sumErasers = eraserCount
        sumBounds = boundsApprox()?.copyOf()
        zList.clear(); byId.clear(); index.clear()
        eraserCount = 0
        exact = null; exactValid = false
        isLoaded = false
        version++
        observer?.reset()
        return true
    }

    /** Un résumé de ce que la couche contient, tel que la base le garde (voir [shell]). */
    class Summary(val count: Int, val erasers: Int, val bounds: DoubleArray?)

    /** Les chiffres du résumé : une couche chargée les calcule, une couche déchargée les a gardés. */
    fun summary(): Summary = if (isLoaded) Summary(byId.size, eraserCount, boundsApprox()) else Summary(sumCount, sumErasers, sumBounds)

    /** A déjà porté quelque chose : on ne l'oublie plus (l'historique compte sur son repère). */
    var used = false

    /** Incrémenté à chaque changement du contenu de la couche (pose, retrait, remplacement, changement d'ordre). */
    var version = 0L
        private set

    /** Prévenu, sur le fil de l'écran, de chaque changement du contenu : le cache raster de la couche s'y tient à jour. */
    interface Observer {
        /** Un élément est parti ([removed]), arrivé ([added]), ou remplacé par un autre ([removed] → [added]). */
        fun changed(removed: Entry?, added: Entry?)
        /** Tout a changé (couche relue, pile renumérotée) : rien de ce qu'on savait n'est sûr. */
        fun reset()
    }

    var observer: Observer? = null

    /**
     * L'index de la couche à un instant, lisible depuis un autre fil pendant que l'écran continue de
     * la modifier : l'arbre ne change jamais, la liste d'attente est copiée, et les entrées sont
     * immuables. Un élément retiré depuis est simplement sauté (il est marqué mort).
     */
    class Snapshot internal constructor(private val tree: PackedTree, private val pending: Array<Entry>, val version: Long) {
        /** Les entrées vivantes dont la boîte touche le rectangle, du fond vers l'avant. */
        fun query(x0: Double, y0: Double, x1: Double, y1: Double, out: ArrayList<Entry>) {
            out.clear()
            tree.query(x0, y0, x1, y1, out)
            for (e in pending) if (!e.dead && e.overlaps(x0, y0, x1, y1)) out.add(e)
            if (out.size > 1) out.sortWith(Z_ASCENDING)
        }
    }

    /** Prend l'instantané de l'index (sur le fil de l'écran). */
    fun snapshot(): Snapshot {
        index.ensure(zList)
        return index.snapshot(version)
    }

    private fun changed(removed: Entry?, added: Entry?) {
        version++
        dirty = true
        observer?.changed(removed, added)
    }

    val isEmpty: Boolean get() = if (isLoaded) byId.isEmpty() else sumCount == 0
    /** Nombre d'éléments dans la couche, coups de gomme compris. */
    val size: Int get() = if (isLoaded) byId.size else sumCount
    /** Ce qu'on voit dans la couche (les coups de gomme ne comptent pas, ils ne font que creuser). */
    val itemCount: Int get() = size - erasers
    /** Nombre de coups de gomme. */
    val erasers: Int get() = if (isLoaded) eraserCount else sumErasers
    /** Contient au moins un coup de gomme : il faut alors la composer à part pour qu'il ne creuse qu'elle. */
    val hasEraser: Boolean get() = erasers > 0

    // ---- Lecture --------------------------------------------------------------------------

    /** Les entrées dans l'ordre de dessin, du fond vers l'avant. À ne pas modifier. */
    fun entries(): List<Entry> = zList

    /** Traits, coups de gomme, images, formes et textes dans l'ordre de dessin. Copie : pour les tests et les chemins froids. */
    fun drawOrder(): List<LayerItem> = zList.map { it.item }

    /** Les traits (gommes comprises) dans l'ordre de dessin. Copie : à ne pas appeler à chaque image. */
    val strokes: List<Stroke> get() = zList.mapNotNull { it.item as? Stroke }
    val images: List<ImageItem> get() = zList.mapNotNull { it.item as? ImageItem }
    val shapes: List<ShapeItem> get() = zList.mapNotNull { it.item as? ShapeItem }
    val texts: List<TextItem> get() = zList.mapNotNull { it.item as? TextItem }

    /** L'élément [id] (un coup de gomme compris : on l'édite dans le mode d'édition des gommes), ou null. */
    fun box(id: Long): BoxItem? = byId[id]?.item?.let { boxOf(it) }

    fun entry(id: Long): Entry? = byId[id]

    private fun boxOf(item: LayerItem): BoxItem? = when (item) {
        is Stroke -> StrokeBox(item)
        is BoxItem -> item
    }

    /** Les clés des fichiers d'images utilisées par la couche. */
    fun imageKeys(out: MutableSet<String>) {
        for (e in zList) (e.item as? ImageItem)?.let { out.add(it.key) }
    }

    // ---- Pose, retrait, remplacement ------------------------------------------------------

    fun add(s: Stroke) = insert(s, -1)

    /** Retire l'élément [id] (rend son rang de dessin, -1 s'il n'y était pas). */
    fun remove(id: Long) { removeBox(id) }

    fun addImage(i: ImageItem) = addBox(i)

    /** Pose une image, une forme, un texte ou un trait, à la fin (devant tout) ou au rang [index] de l'ordre de dessin. */
    fun addBox(b: BoxItem, index: Int = -1) = insert(if (b is StrokeBox) b.stroke else b, index)

    private fun insert(item: LayerItem, at: Int) {
        // Une couche déchargée ne se modifie pas : la scène la recharge avant de dessiner dedans.
        if (!isLoaded) return
        if (byId.containsKey(item.id)) removeBox(item.id)
        val pos = if (at in 0..zList.size) at else zList.size
        val e = Entry(item, zBetween(pos))
        zList.add(pos, e)
        byId[item.id] = e
        if (e.isEraser) eraserCount++
        index.add(e)
        if (exactValid && !e.isEraser) growExact(e)
        used = true
        journal?.upsert(depth, e)
        changed(null, e)
    }

    /** Retire l'élément [id] et rend le rang qu'il avait dans l'ordre de dessin (-1 s'il n'y était pas). */
    fun removeBox(id: Long): Int {
        val e = byId.remove(id) ?: return -1
        val at = rankOf(e)
        zList.removeAt(at)
        e.dead = true
        index.remove(e)
        if (e.isEraser) eraserCount--
        exactValid = false
        journal?.delete(depth, id)
        changed(e, null)
        return at
    }

    /** Remplace l'élément de même identifiant : son rang de dessin ne change pas. */
    fun replaceBox(b: BoxItem) {
        val item: LayerItem = if (b is StrokeBox) b.stroke else b
        val old = byId[item.id] ?: return
        if (old.item === item) return
        val e = Entry(item, old.z)
        zList[rankOf(old)] = e
        byId[item.id] = e
        if (old.isEraser) eraserCount--
        if (e.isEraser) eraserCount++
        old.dead = true
        index.remove(old)
        index.add(e)
        exactValid = false
        journal?.upsert(depth, e)
        changed(old, e)
    }

    /** Remplace tous les objets à boîte (hors traits) par [f] appliquée à chacun (recentrage de la couche). */
    fun mapBoxes(f: (BoxItem) -> BoxItem) {
        for (e in zList.toList()) {
            val item = e.item
            if (item is BoxItem) replaceBox(f(item))
        }
    }

    /**
     * Remplace tout le contenu par [items] (chaque élément avec son rang de dessin) : lecture d'un
     * projet. Rien n'est noté dans le journal, c'est déjà écrit.
     */
    fun load(items: List<Pair<LayerItem, Long>>) {
        zList.clear(); byId.clear(); eraserCount = 0
        val entries = items.map { Entry(it.first, it.second) }.sortedBy { it.z }
        for (e in entries) {
            zList.add(e)
            byId[e.id] = e
            if (e.isEraser) eraserCount++
        }
        index.rebuild(zList)
        exactValid = false
        if (zList.isNotEmpty()) used = true
        isLoaded = true
        dirty = false
        sumBounds = null
        version++
        observer?.reset()
    }

    // ---- Ordre de dessin ------------------------------------------------------------------

    /** Rang (position dans la pile) de l'entrée [e], par dichotomie sur son `z`. */
    private fun rankOf(e: Entry): Int {
        var lo = 0
        var hi = zList.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val z = zList[mid].z
            if (z < e.z) lo = mid + 1 else if (z > e.z) hi = mid - 1 else return mid
        }
        error("entry ${e.id} not in the layer order")
    }

    /**
     * Le `z` à donner à ce qui s'insère au rang [pos] : entre ses deux voisins. Les trous sont
     * grands ([Z_STEP]) ; s'il n'en reste plus, la pile est renumérotée.
     */
    private fun zBetween(pos: Int): Long {
        val prev = if (pos > 0) zList[pos - 1].z else null
        val next = if (pos < zList.size) zList[pos].z else null
        return when {
            prev == null && next == null -> 0L
            next == null -> prev!! + Z_STEP
            prev == null -> next - Z_STEP
            next - prev >= 2 -> prev + (next - prev) / 2
            else -> { renumber(); zBetween(pos) }
        }
    }

    /** Redonne à toute la pile des rangs bien espacés (très rare : après des dizaines d'insertions au même endroit). */
    private fun renumber() {
        for (i in zList.indices) {
            val old = zList[i]
            val e = Entry(old.item, i * Z_STEP)
            old.dead = true
            zList[i] = e
            byId[e.id] = e
            journal?.upsert(depth, e)
        }
        index.rebuild(zList)
        version++
        dirty = true
        observer?.reset()
    }

    /** La pile n'est plus dans l'ordre de pose (les identifiants ne se suivent plus). Parcourt toute la couche : pour les tests. */
    val hasCustomOrder: Boolean get() {
        for (i in 1 until zList.size) if (zList[i].id < zList[i - 1].id) return true
        return false
    }

    /** Rang de [id] dans l'ordre de dessin (-1 s'il n'est pas là). */
    fun orderIndex(id: Long): Int {
        val e = byId[id] ?: return -1
        return rankOf(e)
    }

    private fun visibleAt(k: Int): Boolean = !zList[k].isEraser

    /**
     * Le rang où irait [id] pour un déplacement [move], ou null s'il n'y a rien à faire. Les coups
     * de gomme se traversent : « d'un cran » veut dire juste après le prochain élément visible.
     * Passer devant un coup de gomme, c'est ne plus être creusé par lui ; passer derrière, l'être.
     */
    fun moveTarget(id: Long, move: OrderMove): Int? {
        val i = orderIndex(id)
        if (i < 0) return null
        return when (move) {
            OrderMove.FORWARD -> (i + 1 until zList.size).firstOrNull { visibleAt(it) }
            OrderMove.BACKWARD -> (i - 1 downTo 0).firstOrNull { visibleAt(it) }
            OrderMove.TO_FRONT -> if (i < zList.size - 1) zList.size - 1 else null
            OrderMove.TO_BACK -> if ((0 until i).any { visibleAt(it) }) 0 else null
        }
    }

    /** Range [id] au rang [to] (compté dans l'ordre d'avant le déplacement). */
    fun moveInOrder(id: Long, to: Int) {
        val e = byId[id] ?: return
        val from = rankOf(e)
        zList.removeAt(from)
        val pos = to.coerceIn(0, zList.size)
        val moved = Entry(e.item, zBetween(pos))
        zList.add(pos, moved)
        byId[id] = moved
        e.dead = true
        index.remove(e)
        index.add(moved)
        journal?.upsert(depth, moved)
        changed(e, moved)
    }

    /** Rang de [id] parmi les éléments visibles (1 = le plus au fond) et leur nombre, ou null. */
    fun visibleRank(id: Long): IntArray? {
        val i = orderIndex(id)
        if (i < 0) return null
        // Pour un coup de gomme : le nombre d'éléments visibles qu'il y a sous lui, donc ceux qu'il creuse.
        var rank = 0
        var count = 0
        for (k in zList.indices) if (visibleAt(k)) { count++; if (k <= i) rank++ }
        return intArrayOf(rank, count)
    }

    // ---- Recherche spatiale ---------------------------------------------------------------

    /**
     * Met dans [out], du fond vers l'avant, les entrées dont la boîte touche le rectangle (en unités
     * de la couche). C'est la liste de ce qu'il faut tracer pour une vue, ou d'où chercher un appui.
     */
    fun query(x0: Double, y0: Double, x1: Double, y1: Double, out: ArrayList<Entry>) {
        out.clear()
        index.ensure(zList)
        index.query(x0, y0, x1, y1, out)
        if (out.size > 1) out.sortWith(Z_ASCENDING)
    }

    /** Borne haute du nombre d'entrées qui touchent le rectangle (sans les lister). */
    fun countIn(x0: Double, y0: Double, x1: Double, y1: Double): Int {
        index.ensure(zList)
        return index.count(x0, y0, x1, y1)
    }

    private val scratch = ArrayList<Entry>()

    /**
     * L'élément que touche le point, le plus en avant d'abord. Avec [below], celui qui est juste
     * sous cet élément à cet endroit (et, s'il n'y en a plus, on repart du dessus) : un appui répété
     * descend ainsi dans la pile. [textOnly] ne regarde que les textes ; [erasers] ne regarde que les
     * coups de gomme (et eux seuls : sinon ils sont invisibles pour la sélection).
     */
    fun boxAt(px: Double, py: Double, slack: Double, below: Long? = null, textOnly: Boolean = false, erasers: Boolean = false): BoxItem? {
        query(px - slack, py - slack, px + slack, py + slack, scratch)
        val hits = ArrayList<BoxItem>(2)
        for (k in scratch.indices.reversed()) {
            val item = scratch[k].item
            val box: BoxItem = when (item) {
                is BoxItem -> if (erasers) continue else item
                is Stroke -> if (item.isEraser != erasers) continue else StrokeBox(item)
            }
            if (textOnly && box !is TextItem) continue
            if (box.hit(px, py, slack)) hits.add(box)
        }
        scratch.clear()
        if (hits.isEmpty()) return null
        if (below == null) return hits[0]
        val at = hits.indexOfFirst { it.id == below }
        return hits[if (at < 0) 0 else (at + 1) % hits.size]
    }

    // ---- Étendue --------------------------------------------------------------------------

    private var exact: DoubleArray? = null
    private var exactValid = false

    private fun growExact(e: Entry) {
        val b = exact
        if (b == null) exact = doubleArrayOf(e.x0, e.y0, e.x1, e.y1)
        else {
            b[0] = min(b[0], e.x0); b[1] = min(b[1], e.y0); b[2] = max(b[2], e.x1); b[3] = max(b[3], e.y1)
        }
    }

    /**
     * Rectangle englobant exact (minX, minY, maxX, maxY) de ce qu'on voit, épaisseur comprise, ou
     * null si rien. Les coups de gomme n'y comptent pas : ils ne font que creuser. Recalculé (sur
     * toute la couche) seulement après un retrait ou un remplacement : pour le rendu, qui le demande à
     * chaque image, voir [boundsApprox].
     */
    fun bounds(): DoubleArray? {
        if (!isLoaded) return sumBounds
        if (!exactValid) {
            exact = null
            for (e in zList) if (!e.isEraser) growExact(e)
            exactValid = true
        }
        return exact
    }

    /**
     * Un rectangle qui contient tout ce qu'on voit, rendu à coût constant : il ne rétrécit pas quand
     * on retire ou déplace un élément (seulement à la prochaine reconstruction de l'index). Assez
     * juste pour décider si une couche est à l'écran, trop large pour la cadrer.
     */
    fun boundsApprox(): DoubleArray? {
        if (!isLoaded) return sumBounds
        index.ensure(zList)
        return index.visibleBounds()
    }

    /** Le trait [s] passe-t-il sur ce qu'on voit de la couche ? (Un coup de gomme dans le vide ne sert à rien.) */
    fun touches(s: Stroke): Boolean {
        val b = bounds() ?: return false
        val h = s.width / 2
        return s.x + s.maxX + h >= b[0] && s.x + s.minX - h <= b[2] && s.y + s.maxY + h >= b[1] && s.y + s.minY - h <= b[3]
    }

    /** Plus grande dimension du contenu, en unités locales (0 si vide). */
    fun extent(): Double {
        val b = bounds() ?: return 0.0
        return max(b[2] - b[0], b[3] - b[1])
    }

    /** Comme [extent], à coût constant et par excès (voir [boundsApprox]). */
    fun extentApprox(): Double {
        val b = boundsApprox() ?: return 0.0
        return max(b[2] - b[0], b[3] - b[1])
    }

    companion object {
        /**
         * Une couche déchargée dès le départ (projet qu'on ouvre) : son ancre et son résumé viennent de la base
         * (voir [Summary]), ses éléments restent dans la base jusqu'à [load].
         */
        fun shell(depth: Long, ax: Double, ay: Double, count: Int, erasers: Int, bounds: DoubleArray?): Layer =
            Layer(depth, ax, ay).also {
                it.isLoaded = false
                it.sumCount = count
                it.sumErasers = erasers
                it.sumBounds = bounds
                it.used = count > 0
            }

        /** Écart entre deux rangs voisins de la pile. */
        const val Z_STEP = 1L shl 24

        internal val Z_ASCENDING = Comparator<Entry> { a, b -> a.z.compareTo(b.z) }
    }
}

/**
 * Un trait de dessin libre : sa position ([x], [y], en `Double`) et sa forme [pts] (paires x, y,
 * en `Float`) relative à cette position — le premier point vaut (0, 0). [width] est en unités de la
 * couche. Les points sont petits (relatifs au trait, jamais à une origine lointaine) : un `Float`
 * y garde bien assez de précision, et pèse moitié moins qu'un `Double` dans la mémoire et dans le fichier.
 *
 * Un trait de couleur transparente ([isEraser]) est un coup de gomme : il dessine de la
 * transparence, qui creuse ce que sa couche a reçu avant lui et laisse voir la couche du dessous.
 *
 * [kind] dit avec quoi il a été tracé : le crayon ([PEN]) garde la même épaisseur partout ; le
 * pinceau ([BRUSH]) s'affine aux deux bouts ; le feutre ([MARKER]) se multiplie avec ce qu'il
 * recouvre, comme une encre transparente (deux passages foncent).
 */
class Stroke(
    override val id: Long, val x: Double, val y: Double, val pts: FloatArray, val color: Int, val width: Double,
    val kind: Int = PEN,
) : LayerItem {
    companion object {
        const val PEN = 0
        const val BRUSH = 1
        const val MARKER = 2

        /** Le pinceau s'affine sur une longueur de [BRUSH_TAPER] fois son épaisseur, à chaque bout. */
        const val BRUSH_TAPER = 3.0
        /** Épaisseur du pinceau tout au bout, en part de son épaisseur. */
        const val BRUSH_TIP = 0.15

        /** Part de l'épaisseur du pinceau à [d] (unités de la couche) du bout le plus proche. */
        fun brushFactor(d: Double, width: Double): Double {
            val t = d / (BRUSH_TAPER * width)
            return if (t >= 1.0) 1.0 else max(BRUSH_TIP, sqrt(max(t, 0.0)))
        }
    }

    val pointCount: Int get() = pts.size / 2

    val isEraser: Boolean get() = color ushr 24 == 0

    val minX: Double
    val minY: Double
    val maxX: Double
    val maxY: Double

    init {
        var a = Double.POSITIVE_INFINITY; var b = Double.POSITIVE_INFINITY
        var c = Double.NEGATIVE_INFINITY; var d = Double.NEGATIVE_INFINITY
        var i = 0
        while (i < pts.size) {
            val px = pts[i].toDouble()
            val py = pts[i + 1].toDouble()
            if (px < a) a = px; if (px > c) c = px
            if (py < b) b = py; if (py > d) d = py
            i += 2
        }
        if (pts.isEmpty()) { a = 0.0; b = 0.0; c = 0.0; d = 0.0 }
        minX = a; minY = b; maxX = c; maxY = d
    }

    private var totalLength = -1.0

    /** Longueur du trait (unités locales) : l'affinement du pinceau se règle dessus. Calculée une fois. */
    fun length(): Double {
        var sum = totalLength
        if (sum < 0.0) {
            sum = 0.0
            var i = 0
            while (i + 3 < pts.size) {
                val dx = (pts[i + 2] - pts[i]).toDouble()
                val dy = (pts[i + 3] - pts[i + 1]).toDouble()
                sum += sqrt(dx * dx + dy * dy)
                i += 2
            }
            totalLength = sum
        }
        return sum
    }
}

/**
 * Une image posée sur une couche : son centre ([x], [y]) et sa taille ([w], [h]) en unités de la
 * couche. Les pixels vivent dans un fichier du projet ([key]) de [pxW]×[pxH] pixels.
 */
class ImageItem(override val id: Long, val key: String, val pxW: Int, val pxH: Int, override val x: Double, override val y: Double, override val w: Double, override val h: Double) : BoxItem {
    override val keepsRatio: Boolean get() = true
    fun contains(px: Double, py: Double) = hit(px, py, 0.0)
    override fun moved(dx: Double, dy: Double) = ImageItem(id, key, pxW, pxH, x + dx, y + dy, w, h)
    override fun boxed(x: Double, y: Double, w: Double, h: Double, crossX: Boolean, crossY: Boolean) = ImageItem(id, key, pxW, pxH, x, y, w, h)
}
