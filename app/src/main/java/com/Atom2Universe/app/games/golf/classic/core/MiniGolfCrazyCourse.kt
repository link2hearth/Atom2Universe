package com.Atom2Universe.app.games.golf.classic.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The same putter and the same carpet, in worlds that do not exist: pipes that swap rooms, black holes,
 * conveyor belts, ice, pinball bumpers, jaws, a spiral and a maze.
 */
object MiniGolfCrazyCourse {
    /** An inward spiral that starts at the tee and winds [turns] times round (0, [cz]). */
    private fun spiral(cz: Float, from: Float, to: Float, turns: Float, steps: Int) = (0..steps).map { k ->
        val t = k.toFloat() / steps
        val a = (-PI / 2 + t * turns * 2 * PI).toFloat()
        val r = from + (to - from) * t
        (r * cos(a)) to (cz + r * sin(a))
    }

    private val snail = spiral(4.4f, 4.4f, 1f, 2.25f, 36)

    val holes: List<ClassicHole> = listOf(
        // 1. Two pipes: one leads to the cup, the other back to the tee.
        miniHole(1, 2, 4.5f, 11.2f, MiniTheme.COSMOS) {
            room(0f, 2f, 1.6f, 2.6f, 0f, .3f)
            lane(1.4f, 4.5f to 6.8f, 4.5f to 12f)
            pipe(.9f, 3.8f, 4.5f, 7.4f, heading = 0f, tint = 0)
            pipe(-.9f, 3.8f, 0f, .8f, heading = 0f, tint = 1)
            route(.9f to 3.8f, 4.5f to 7.4f)
        },
        // 2. A black hole beside the lane bends the ball, and swallows it if it gets too close.
        miniHole(2, 3, 0f, 11.8f, MiniTheme.COSMOS) {
            lane(3f, 0f to -.6f, 0f to 12.6f)
            well(1f, 6f, 7f, 2.6f, .22f)
        },
        // 3. Conveyor belts push the ball one way, then the other.
        miniHole(3, 3, 0f, 11.8f, MiniTheme.CANDY) {
            lane(2.4f, 0f to -.6f, 0f to 12.6f)
            conveyor(miniBox(0f, 3.8f, 1.3f, 1f), 1f, 0f)
            conveyor(miniBox(0f, 7.4f, 1.3f, 1f), -1f, 0f)
        },
        // 4. Pinball: five bumpers that throw the ball back.
        miniHole(4, 3, 0f, 11.4f, MiniTheme.COSMOS) {
            room(0f, 6f, 2.4f, 6.6f, 0f, .5f)
            post(0f, 6.2f, .26f, .8f, 2f)
            post(1.4f, 4.4f, .22f, .8f, 2f); post(-1.4f, 4.4f, .22f, .8f, 2f)
            post(1f, 8.2f, .22f, .8f, 2f); post(-1f, 8.2f, .22f, .8f, 2f)
        },
        // 5. Two windmills turning against each other.
        miniHole(5, 3, 0f, 12.2f, MiniTheme.OCEAN) {
            lane(2.2f, 0f to -.6f, 0f to 13f)
            rotor(0f, 4.6f, 3, 1f, 1.1f)
            rotor(0f, 8.6f, 3, 1f, -1.4f, 1f)
        },
        // 6. Three pistons, each at its own pace.
        miniHole(6, 3, 0f, 12.2f, MiniTheme.CANDY) {
            lane(1.8f, 0f to -.6f, 0f to 13f)
            slider(0f, 3.8f, .6f, .08f, .5f, 0f, 2.6f)
            slider(0f, 6.6f, .6f, .08f, -.5f, 0f, 3.1f, 1f)
            slider(0f, 9.2f, .6f, .08f, .5f, 0f, 2.3f, 2f)
        },
        // 7. The ice rink: a gentle push goes a long way.
        miniHole(7, 3, 0f, 9.4f, MiniTheme.OCEAN) {
            room(0f, 5f, 2.2f, 5.6f, 0f, .5f)
            ice(miniBox(0f, 3.4f, 2.3f, 4.6f))
            post(-1f, 4f, .25f); post(1f, 7f, .25f)
        },
        // 8. Mud slows the ball to a crawl, then a boost strip throws it forward.
        miniHole(8, 3, 0f, 10f, MiniTheme.GARDEN) {
            lane(1.6f, 0f to -.6f, 0f to 11.4f)
            mud(miniBox(0f, 2.3f, .9f, 1f))
            boost(miniBox(0f, 6.8f, .8f, .5f), 0f, 2.2f)
        },
        // 9. The snail: a spiral lane to the cup at its heart.
        miniHole(9, 5, snail.last().first, snail.last().second, MiniTheme.COSMOS) {
            chain(.8f, snail)
        },
        // 10. Roller coaster: three humps, and a funnel round the cup.
        miniHole(10, 3, 0f, 11.4f, MiniTheme.CANDY) {
            lane(1.6f, 0f to -.6f, 0f to 13f)
            hill(0f, 3f, .2f, .2f, 1f); hill(0f, 5.4f, .2f, .2f, 1f); hill(0f, 7.8f, .2f, .2f, 1f)
            terrace(miniDisc(0f, 11.4f, .3f), -.1f, 1f)
        },
        // 11. The jaws open and close in front of the cup.
        miniHole(11, 3, 0f, 12f, MiniTheme.CANDY) {
            lane(2f, 0f to -.6f, 0f to 13f)
            slider(.62f, 8f, .5f, .12f, -.55f, 0f, 3f)
            slider(-.62f, 8f, .5f, .12f, .55f, 0f, 3f)
        },
        // 12. Rapids carry the ball towards a waterfall on one side.
        miniHole(12, 3, 0f, 12f, MiniTheme.OCEAN) {
            lane(2.2f, 0f to -.6f, 0f to 13f)
            conveyor(miniBox(0f, 6.5f, 1.1f, 1.6f), 1.4f, 0f)
            water(miniBox(1.15f, 6.5f, .65f, 1.6f))
        },
        // 13. Two planets: one pulls, one pushes.
        miniHole(13, 3, 0f, 12.2f, MiniTheme.COSMOS) {
            lane(3.2f, 0f to -.6f, 0f to 13f)
            well(1f, 4.5f, 5f, 2.2f, .18f)
            well(-.9f, 8f, -6f, 2.4f)
        },
        // 14. The camber: the floor tilts, the ball drifts to one side.
        miniHole(14, 3, 0f, 12.2f, MiniTheme.GARDEN) {
            lane(1.5f, 0f to -.6f, 0f to 13f)
            terrace(miniBox(0f, 6.5f, 1.1f, 7.5f), 0f, 1f, tiltX = .09f)
        },
        // 15. Propellers.
        miniHole(15, 3, 0f, 11.2f, MiniTheme.OCEAN) {
            room(0f, 5.8f, 2.4f, 6.4f, 0f, .5f)
            rotor(-1f, 4f, 2, .95f, 1.3f)
            rotor(1f, 6.4f, 2, .95f, -1.7f)
            rotor(-.9f, 9f, 3, .9f, 1f)
        },
        // 16. The slide: the tee stands high, the ball picks up speed on its own.
        miniHole(16, 3, 0f, 11.6f, MiniTheme.CANDY) {
            lane(1.4f, 0f to -.6f, 0f to 13f)
            terrace(miniBox(0f, -3f, 1.5f, 6f), .9f, 3.5f)
            hill(0f, 9.6f, .25f, .25f, 1.4f)
            sand(miniBox(0f, 12.7f, .7f, .35f))
        },
        // 17. The maze: dead ends and one way through.
        miniHole(17, 4, -1.2f, 4.5f, MiniTheme.COSMOS) {
            lane(.85f, 0f to -.6f, 0f to 1.5f, -1.5f to 1.5f, -1.5f to 3f, 1.5f to 3f, 1.5f to 4.5f, -1.5f to 4.5f)
            lane(.85f, 0f to 1.5f, 1.5f to 1.5f)
            lane(.85f, 1.5f to 4.5f, 1.5f to 5.9f)
        },
        // 18. The grand finale: a windmill, a belt and a pipe, then a black hole, sand and a pull towards the cup.
        miniHole(18, 5, -.6f, 9.6f, MiniTheme.COSMOS) {
            lane(1.6f, 0f to -.6f, 0f to 5.2f)
            lane(1.6f, -4f to 5.6f, -4f to 9.6f, -.2f to 9.6f)
            rotor(0f, 2.4f, 2, .72f, 1.6f)
            conveyor(miniBox(0f, 4f, .8f, .6f), .8f, 0f)
            pipe(0f, 4.6f, -4f, 5.9f, heading = 0f)
            well(-3.3f, 7.2f, 4f, 2f)
            sand(miniBox(-4f, 8.4f, .6f, .45f))
            route(0f to 4.6f, -4f to 5.9f, -4f to 9.6f)
        }
    )
}
