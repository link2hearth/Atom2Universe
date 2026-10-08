package com.Atom2Universe.app.games.roguelike

/** Cri de guerre : tenir n'importe où, puis relâcher quand l'onde atteint la zone. */
internal class WaveGesture(
    val heroX: Float,
    val heroY: Float,
    val radius: Float,
    private val fillMs: Float,
    val center: Float,
    val good: Float,
    val perfect: Float,
    private val limitMs: Float,
) : TouchGesture {
    override var result: Timing? = null
        private set
    private var pointer = -1
    private var heldAt = 0f
    val holding get() = pointer >= 0
    fun gauge(t: Float) = if (holding) ((t - heldAt) / fillMs).coerceAtLeast(0f) else 0f

    override fun down(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || holding) return
        pointer = id
        heldAt = t
    }
    override fun move(id: Int, x: Float, y: Float, t: Float) {}
    override fun up(id: Int, x: Float, y: Float, t: Float) {
        if (result != null || id != pointer) return
        result = gaugeGrade(gauge(t), center, good, perfect)
    }
    override fun update(t: Float) {
        if (result == null && (gauge(t) >= 1f || t >= limitMs)) result = Timing.MISS
    }
}
