package com.Atom2Universe.app.games.toyboxracers.track

import com.Atom2Universe.app.games.toyboxracers.models.DecorCatalog
import com.Atom2Universe.app.games.toyboxracers.models.DecorPlacement
import com.Atom2Universe.app.games.toyboxracers.track.PrototypeTrack.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max

/**
 * Rubans sculptés : un tracé vu de dessus, puis un relief posé dessus en mètres réels.
 *
 * Le relief se compose de trois briques :
 * - des **paliers** ([Level]) : altitudes clés le long du tour, raccordées par des S doux ;
 * - des **bosses** ([Bump]) : la voiture les suit sans décoller (ses roues collent à la route
 *   tant qu'elle ne s'arrête pas), ce sont des montagnes russes, pas des sauts ;
 * - des **tremplins** ([Kicker]) : la route monte, s'interrompt (un vrai vide, sans dalle), puis
 *   reprend plus bas. C'est le seul moyen de faire voler la voiture : elle ne quitte le sol que
 *   quand la route disparaît sous ses roues.
 *
 * Les positions se donnent en fraction du tour (les tracés les repèrent par un point de la
 * pièce, voir [CircuitDesigns]), les longueurs et hauteurs en mètres. Les piliers sous les
 * portions hautes sont posés tout seuls, jamais au-dessus d'une autre portion de la route.
 */
internal object SculptedCircuits {
    /** Altitude de la route à cette fraction du tour ; entre deux paliers, un S doux. */
    data class Level(val at: Float, val height: Float)

    /** Bosse en cosinus centrée sur [at] : longueur totale et hauteur en mètres. */
    data class Bump(val at: Float, val length: Float, val height: Float)

    /**
     * Tremplin dont la lèvre est à [lip] : [approach] mètres d'élan qui montent de [rise], un
     * vide de [gap] mètres, puis une réception [drop] mètres sous la lèvre qui rejoint le palier
     * en [landing] mètres. Le vide reste au-dessus du sol : une voiture trop lente y tombe et
     * remonte sur la réception, assez basse pour être reprise depuis le parquet.
     */
    data class Kicker(
        val lip: Float, val approach: Float, val rise: Float,
        val gap: Float, val drop: Float, val landing: Float
    )

    /** Un modèle du catalogue posé autour de la piste ; [yaw] en degrés. */
    data class Prop(val id: String, val x: Float, val z: Float, val scale: Float = 1f,
                    val yaw: Float = 0f, val y: Float = 0f)

    class Layout(
        val loop: PlanarLoop,
        val width: Float,
        levels: List<Level> = emptyList(),
        val bumps: List<Bump> = emptyList(),
        val kickers: List<Kicker> = emptyList(),
        val props: List<Prop> = emptyList(),
        /** Jouets de la chambre ; null garde la ronde habituelle autour de la pièce. */
        val toys: List<ToyObstacle>? = null,
        /** Place de l'îlot de cuisine, au pourtour de la pièce, hors du tracé. */
        val island: Pair<Float, Float> = -105f to 0f,
        val pillarColor: Int = 0xC9D3DC
    ) {
        val levels = levels.sortedBy { it.at }
        val length get() = loop.length
    }

    private val layouts: Map<CircuitKind, Layout> by lazy { CircuitDesigns.all() }

    fun layout(kind: CircuitKind): Layout = layouts.getValue(kind)

    fun sampleCount(kind: CircuitKind): Int = max(480, (layout(kind).length / 1.4f).toInt())

    fun point(kind: CircuitKind, fraction: Float): Vec3 {
        val layout = layout(kind)
        val s = fraction * layout.length
        val (x, z) = layout.loop.at(s)
        return Vec3(x, height(layout, s), z)
    }

    fun width(kind: CircuitKind, fraction: Float): Float {
        val layout = layout(kind)
        val s = fraction * layout.length
        var extra = 0f
        // Les tremplins s'élargissent de l'élan à la fin de la réception : une trajectoire
        // de vol un peu de travers doit encore trouver la route en retombant.
        for (k in layout.kickers) {
            val lip = k.lip * layout.length
            extra = max(extra, 3f * window(s, lip - k.approach * .4f, lip + k.gap + k.landing, 5f, layout.length))
        }
        return layout.width + extra
    }

    /** Élan d'un tremplin (1), réception (2) ou rien (0) : le rendu les peint à part. */
    fun stuntZone(kind: CircuitKind, fraction: Float): Int {
        val layout = layout(kind)
        val s = fraction * layout.length
        for (k in layout.kickers) {
            val d = loopDelta(s, k.lip * layout.length, layout.length)
            if (d >= -k.approach && d <= 0f) return 1
            if (d >= k.gap && d <= k.gap + k.landing) return 2
        }
        return 0
    }

    fun crossings(kind: CircuitKind): List<GradeCrossing> {
        val layout = layout(kind)
        return layout.kickers.map { GradeCrossing(it.lip, it.lip + it.gap / layout.length) }
    }

    fun decorations(kind: CircuitKind): List<DecorPlacement> = layout(kind).props.map {
        DecorPlacement(DecorCatalog[it.id], it.x, it.y, it.z, scale = it.scale, yawDegrees = it.yaw)
    }

    private val pillarCache = HashMap<CircuitKind, List<RoomBox>>()

    /** Piliers sous les portions hautes, jamais sur le passage d'une autre portion. */
    fun pillars(kind: CircuitKind): List<RoomBox> = synchronized(pillarCache) {
        pillarCache.getOrPut(kind) { buildPillars(layout(kind)) }
    }

    private fun buildPillars(layout: Layout): List<RoomBox> {
        val length = layout.length
        val step = 1f
        val count = (length / step).toInt()
        val xs = FloatArray(count); val zs = FloatArray(count); val ys = FloatArray(count)
        val deck = BooleanArray(count)
        for (i in 0 until count) {
            val s = i * step
            val (x, z) = layout.loop.at(s)
            xs[i] = x; zs[i] = z; ys[i] = height(layout, s)
            deck[i] = layout.kickers.none { k ->
                val d = loopDelta(s, k.lip * length, length)
                d > -1.5f && d < k.gap + 1.5f
            }
        }
        val result = ArrayList<RoomBox>()
        var last = -1000f
        for (i in 0 until count) {
            val s = i * step
            if (!deck[i] || ys[i] < 3.2f || s - last < 17f) continue
            // Une autre portion passe dessous : proche dans la pièce, mais loin le long du tour
            // (la route elle-même, quelques mètres plus bas dans sa descente, ne compte pas).
            val clear = (0 until count).none { j ->
                val apart = hypot(xs[j] - xs[i], zs[j] - zs[i])
                apart < layout.width * .5f + 4.5f && ys[j] < ys[i] - .5f &&
                    abs(loopDelta(j * step, s, length)) > apart * 1.5f + 12f
            }
            if (!clear) continue
            // Dans une pente, le pilier est plus large que le point qui le porte : son sommet
            // suit le bas de la dalle au plus bas de son empreinte, sinon il la traverse.
            var lowest = ys[i]
            for (k in -2..2) lowest = minOf(lowest, ys[(i + k + count) % count])
            val top = lowest + PrototypeTrack.ROAD_SURFACE_LIFT - PrototypeTrack.ROAD_THICKNESS
            result += RoomBox(xs[i], top / 2f, zs[i], 2.2f, top, 2.2f, layout.pillarColor)
            result += RoomBox(xs[i], .3f, zs[i], 4.4f, .6f, 4.4f, layout.pillarColor)
            last = s
        }
        return result
    }

    private fun height(layout: Layout, s: Float): Float {
        val length = layout.length
        var y = base(layout, s)
        for (bump in layout.bumps) {
            val d = loopDelta(s, bump.at * length, length)
            if (abs(d) < bump.length / 2f)
                y += bump.height * .5f * (1f + cos(2f * PI.toFloat() * d / bump.length))
        }
        for (k in layout.kickers) {
            val lipS = k.lip * length
            val d = loopDelta(s, lipS, length)
            if (d < -k.approach || d > k.gap + k.landing) continue
            val lipBase = base(layout, lipS)
            val lipY = lipBase + k.rise
            val landY = lipY - k.drop
            if (d <= 0f) {
                val t = (d + k.approach) / k.approach
                return base(layout, s) + k.rise * t * t
            }
            if (d < k.gap) {
                // Dans le vide, une parabole de vol : la tangente reste celle de la lèvre, si
                // bien que la dernière dalle ne finit pas sur une petite crête.
                val slope = 2f * k.rise / k.approach
                val curve = (lipY + slope * k.gap - landY) / (k.gap * k.gap)
                return lipY + slope * d - curve * d * d
            }
            val t = (d - k.gap) / k.landing
            val landBase = base(layout, lipS + k.gap)
            return base(layout, s) + (landY - landBase) * (1f - t) * (1f - t)
        }
        return y
    }

    private fun base(layout: Layout, s: Float): Float {
        val levels = layout.levels
        if (levels.isEmpty()) return 0f
        val f = wrap(s / layout.length)
        var index = levels.indexOfLast { it.at <= f }
        if (index < 0) index = levels.lastIndex
        val a = levels[index]
        val b = levels[(index + 1) % levels.size]
        var span = b.at - a.at
        if (span <= 0f) span += 1f
        var u = f - a.at
        if (u < 0f) u += 1f
        val t = (u / span).coerceIn(0f, 1f)
        return a.height + (b.height - a.height) * t * t * (3f - 2f * t)
    }

    /** 1 entre [from] et [to], 0 au-delà, raccordé en douceur sur [ramp] mètres. */
    private fun window(s: Float, from: Float, to: Float, ramp: Float, length: Float): Float {
        val center = (from + to) / 2f
        val half = (to - from) / 2f
        val d = abs(loopDelta(s, center, length))
        val t = ((half + ramp - d) / ramp).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** Écart signé de [s] à [reference] le long de la boucle, dans ]-L/2, L/2]. */
    private fun loopDelta(s: Float, reference: Float, length: Float): Float {
        var d = (s - reference) % length
        if (d > length / 2f) d -= length
        if (d <= -length / 2f) d += length
        return d
    }

    private fun wrap(f: Float): Float { val w = f % 1f; return if (w < 0f) w + 1f else w }
}
