package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Eighteen mini-golf holes of the kind found on every seafront: straight lanes, bends, a windmill,
 * a bridge, a sliding gate. Coordinates are metres, the tee is the origin, +x is the player's left.
 * One putter, a 15 cm cup, rails that bounce the ball: the same ball physics as the golf courses.
 */
object MiniGolfCourse {
    /** A closed ring of [segments] points around ([cx], [cz]), starting at its lowest point and turning towards +x. */
    private fun ring(cx: Float, cz: Float, radius: Float, segments: Int = 16) = (0..segments).map { k ->
        val a = (-PI / 2 + k * 2 * PI / segments).toFloat()
        (cx + radius * cos(a)) to (cz + radius * sin(a))
    }

    val holes: List<ClassicHole> = listOf(
        // 1. The warm-up: a straight lane, a clear line to the cup.
        miniHole(1, 2, 0f, 8.6f, MiniTheme.MEADOW) {
            lane(1.3f, 0f to -.6f, 0f to 9.2f)
        },
        // 2. A right-angle bend: bank the ball off the corner or play it in two.
        miniHole(2, 2, -4.8f, 5.5f, MiniTheme.MEADOW) {
            lane(1.3f, 0f to -.6f, 0f to 5.5f, -5.5f to 5.5f)
        },
        // 3. A diagonal dogleg.
        miniHole(3, 3, 2.8f, 9.4f, MiniTheme.GARDEN) {
            lane(1.3f, 0f to -.6f, 0f to 3f, 2.8f to 6.2f, 2.8f to 10f)
        },
        // 4. Zigzag: two hairpins close together.
        miniHole(4, 3, 0f, 8.4f, MiniTheme.GARDEN) {
            lane(1.2f, 0f to -.6f, 0f to 2.4f, -3.6f to 2.4f, -3.6f to 5.6f, 0f to 5.6f, 0f to 9f)
        },
        // 5. The hairpin: up, across, and back down beside the tee.
        miniHole(5, 3, -2.6f, 3.2f, MiniTheme.CANDY) {
            lane(1.2f, 0f to -.6f, 0f to 7f, -2.6f to 7f, -2.6f to 2.4f)
            fill(miniBox(-1.3f, 4.1f, .7f, 2.3f))
        },
        // 6. The windmill: time the arms.
        miniHole(6, 3, 0f, 10.2f, MiniTheme.OCEAN) {
            lane(2.2f, 0f to -.6f, 0f to 11f)
            rotor(0f, 6f, 3, 1f, 1f)
        },
        // 7. A hump in the lane: enough speed to cross it, not too much for the cup.
        miniHole(7, 2, 0f, 9.6f, MiniTheme.MEADOW) {
            lane(1.4f, 0f to -.6f, 0f to 10.4f)
            hill(0f, 5.2f, .32f, .35f, 1.5f)
        },
        // 8. The plateau: a ramp up to a raised green.
        miniHole(8, 3, 0f, 10.2f, MiniTheme.GARDEN) {
            lane(1.4f, 0f to -.6f, 0f to 11f)
            terrace(miniBox(0f, 9.5f, 1.2f, 3.5f), .45f, 1.3f)
        },
        // 9. Two bunkers in a wide lane: go round them.
        miniHole(9, 3, 0f, 10.2f, MiniTheme.OCEAN) {
            lane(2.4f, 0f to -.6f, 0f to 11f)
            sand(miniBox(0f, 5.5f, .8f, 1f, 0f, .3f))
            sand(miniDisc(-.6f, 8.6f, .5f))
        },
        // 10. The little bridge: a plank between two ponds.
        miniHole(10, 3, 0f, 10.2f, MiniTheme.OCEAN) {
            lane(2.4f, 0f to -.6f, 0f to 11f)
            water(miniBox(.95f, 5.5f, .65f, 1f))
            water(miniBox(-.95f, 5.5f, .65f, 1f))
            bridge(0f, 5.5f, .3f, 1f)
        },
        // 11. Posts to slalom between.
        miniHole(11, 3, 0f, 10.8f, MiniTheme.GARDEN) {
            lane(2.6f, 0f to -.6f, 0f to 11.5f)
            post(.3f, 3.2f, .3f); post(-.35f, 5.4f, .3f); post(.3f, 7.6f, .3f); post(-.3f, 9.4f, .28f)
        },
        // 12. The gate slides to and fro across the lane.
        miniHole(12, 3, 0f, 10.2f, MiniTheme.CANDY) {
            lane(1.8f, 0f to -.6f, 0f to 11f)
            slider(0f, 5.5f, .62f, .07f, .55f, 0f, 3.4f)
        },
        // 13. The tunnel: roll into the pipe, come out on the far side.
        miniHole(13, 3, 0f, 10.8f, MiniTheme.MEADOW) {
            lane(1.4f, 0f to -.6f, 0f to 4.4f)
            lane(1.4f, 0f to 8f, 0f to 11.5f)
            pipe(0f, 3.6f, 0f, 8.4f, heading = 0f)
            fill(miniBox(0f, 6.5f, 1f, 1.6f))
            route(0f to 3.6f, 0f to 8.4f)
        },
        // 14. The billiard table: a block in the middle, banks at the sides.
        miniHole(14, 3, 0f, 9.6f, MiniTheme.OCEAN) {
            room(0f, 5f, 2f, 5.6f, 0f, .35f)
            block(0f, 5.8f, .8f, 1.2f, 0f, .12f)
            route(1.4f to 5.8f)
        },
        // 15. The fork: the left way is long and smooth, the right one short with a bunker.
        miniHole(15, 3, 0f, 11.4f, MiniTheme.GARDEN) {
            lane(1.3f, 0f to -.6f, 0f to 2.6f)
            lane(1.2f, 0f to 2.6f, 1.9f to 4.6f, 1.9f to 8f, 0f to 10f)
            lane(1.2f, 0f to 2.6f, -1.9f to 4.6f, -1.9f to 8f, 0f to 10f)
            lane(1.3f, 0f to 10f, 0f to 12.2f)
            sand(miniBox(-1.9f, 6.4f, .75f, .8f, 0f, .3f))
            hill(1.9f, 6.3f, .22f, .2f, 1f)
            route(1.9f to 4.6f, 1.9f to 8f, 0f to 10f)
        },
        // 16. The ring: round a garden island, a hill on one side and a bunker on the other.
        miniHole(16, 3, 0f, 4.4f, MiniTheme.CANDY) {
            val loop = ring(0f, 2.2f, 2.2f)
            chain(1.4f, loop)
            sand(miniDisc(-2.2f, 2.2f, .55f))
            hill(2.2f, 2.2f, .2f, .2f, 1f)
            route(*loop.subList(1, 8).toTypedArray())
        },
        // 17. Two steps up.
        miniHole(17, 3, 0f, 11.8f, MiniTheme.OCEAN) {
            lane(1.4f, 0f to -.6f, 0f to 13f)
            terrace(miniBox(0f, 12f, 1.2f, 7.5f), .26f, 1f)
            terrace(miniBox(0f, 13f, 1.2f, 4.5f), .26f, 1f)
        },
        // 18. The castle: a gate, a bend, a hump and a moat with a drawbridge.
        miniHole(18, 4, -4.2f, 12.2f, MiniTheme.MEADOW) {
            lane(1.4f, 0f to -.6f, 0f to 6.5f, -4.2f to 6.5f, -4.2f to 13f)
            slider(0f, 3.2f, .55f, .07f, .4f, 0f, 3f)
            hill(-4.2f, 8f, .18f, .2f, 1f)
            water(miniBox(-3.625f, 10.2f, .325f, .6f))
            water(miniBox(-4.775f, 10.2f, .325f, .6f))
            bridge(-4.2f, 10.2f, .25f, .6f)
        }
    )
}
