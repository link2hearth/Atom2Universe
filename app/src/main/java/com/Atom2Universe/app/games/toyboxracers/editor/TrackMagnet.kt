package com.Atom2Universe.app.games.toyboxracers.editor

import kotlin.math.*

/** Endpoint-only attraction in 3D: crossing tracks and adjacent lanes are not junctions. */
internal object TrackMagnet {
    private data class End(val piece: ToyboxTrackSection, val finish: Boolean) {
        val t get() = if (finish) 1f else 0f
        val point get() = piece.centerAt(t)
        val width get() = if (finish) piece.endWidth else piece.width
        val leftBank get() = piece.leftBankAt(t)
        val rightBank get() = piece.rightBankAt(t)
        val direction: Float get() {
            val override = if (finish) piece.endTangentDegrees else piece.startTangentDegrees
            if (override != null) return (piece.yawDegrees+override)*PI.toFloat()/180f
            val a = piece.centerAt(if (finish) .999f else 0f)
            val b = piece.centerAt(if (finish) 1f else .001f)
            return atan2(b.x-a.x,b.z-a.z)
        }
        val grade: Float get() {
            val override = if (finish) piece.endGrade else piece.startGrade
            if (override != null) return override
            if (piece.smoothElevation) return 0f
            val a = piece.centerAt(if (finish) .999f else 0f)
            val b = piece.centerAt(if (finish) 1f else .001f)
            return (b.y-a.y)/hypot(b.x-a.x,b.z-a.z).coerceAtLeast(.0001f)
        }
    }

    fun snap(pieces: List<ToyboxTrackSection>, selectedId: Long, radius: Float,
             onlyFinish: Boolean? = null): List<ToyboxTrackSection> {
        var result = pieces
        val used = mutableSetOf<Pair<Long,Boolean>>()
        for (finish in if (onlyFinish == null) listOf(false,true) else listOf(onlyFinish)) {
            val selected = result.firstOrNull { it.id == selectedId } ?: return result
            val own = End(selected,finish)
            var best: End? = null
            var bestDistance = radius
            for (piece in result) {
                if (piece.id == selectedId) continue
                for (otherFinish in listOf(false,true)) {
                    if ((piece.id to otherFinish) in used) continue
                    val other = End(piece,otherFinish)
                    val a = own.point
                    val b = other.point
                    val distance = sqrt((a.x-b.x).pow(2)+(a.y-b.y).pow(2)+(a.z-b.z).pow(2))
                    val reverse = if (finish == otherFinish) PI.toFloat() else 0f
                    // Do not fold either ribbon back on itself to make a connection.
                    if (cos(own.direction-other.direction-reverse) < -.85f) continue
                    if (distance <= bestDistance) {
                        best = other
                        bestDistance = distance
                    }
                }
            }
            val other = best ?: continue
            val target = other.point
            val opposite = selected.centerAt(if (finish) 0f else 1f)
            if (hypot(target.x-opposite.x,target.z-opposite.z) < .05f) continue
            val moved = selected.withEndpoint(finish,target.x,target.y,target.z)
            val sign = if (finish == other.finish) -1f else 1f
            val otherDirection = other.direction + if (sign < 0f) PI.toFloat() else 0f
            val direction = atan2(sin(own.direction)+sin(otherDirection),cos(own.direction)+cos(otherDirection))
            val grade = (own.grade + other.grade*sign)*.5f
            // Mirrored joints (sign<0) also swap which physical edge is which: travelling
            // through the seam, the other piece's own right becomes this one's left.
            val otherLeftForOwn = if (sign < 0f) -other.rightBank else other.leftBank
            val otherRightForOwn = if (sign < 0f) -other.leftBank else other.rightBank
            val leftBank = (own.leftBank + otherLeftForOwn)*.5f
            val rightBank = (own.rightBank + otherRightForOwn)*.5f
            val width = (own.width+other.width)*.5f
            fun join(piece: ToyboxTrackSection, end: Boolean, heading: Float, slope: Float, left: Float, right: Float): ToyboxTrackSection {
                val offset = heading*180f/PI.toFloat()-piece.yawDegrees
                return if (end) piece.copy(endTangentDegrees=offset,endGrade=slope,
                    endBankDegrees=left,endRightBankDegrees=right,endWidth=width,smoothElevation=true)
                else piece.copy(startTangentDegrees=offset,startGrade=slope,
                    startBankDegrees=left,startRightBankDegrees=right,width=width,smoothElevation=true)
            }
            val a = join(moved,finish,direction,grade,leftBank,rightBank)
            val otherHeading = direction + if (sign < 0f) PI.toFloat() else 0f
            val bLeft = if (sign < 0f) -rightBank else leftBank
            val bRight = if (sign < 0f) -leftBank else rightBank
            val b = join(other.piece,other.finish,otherHeading,grade*sign,bLeft,bRight)
            result = result.map { when (it.id) { a.id -> a; b.id -> b; else -> it } }
            used += other.piece.id to other.finish
        }
        return result
    }
}
