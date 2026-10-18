package com.Atom2Universe.app.games.physics

/** Outils communs aux tests du moteur : un monde avec un sol, et des raccourcis. */

/** Un monde plat, avec un sol large et bien adhérent dont la surface est en y = 0. */
internal fun worldWithGround(halfWidth: Float = 5f): PhysWorld {
    val w = PhysWorld()
    w.add(ground(halfWidth))
    return w
}

internal fun ground(halfWidth: Float = 5f): PhysBody =
    PhysBody(halfWidth, 0.5f, 0f).apply {
        x = 0f
        y = -0.5f
        lockPosition = true
        lockRotation = true
        friction = 0.8f
        refreshMass()
    }

/** Un corps totalement immobile, qui sert de point d'ancrage aux liaisons. */
internal fun anchorPost(x: Float, y: Float): PhysBody =
    PhysBody(0.05f, 0.05f, 0f).apply {
        this.x = x
        this.y = y
        lockPosition = true
        lockRotation = true
        refreshMass()
    }

internal fun box(halfW: Float, halfH: Float, mass: Float, x: Float, y: Float): PhysBody =
    PhysBody(halfW, halfH, mass).apply {
        this.x = x
        this.y = y
        friction = 0.62f
    }

internal fun disc(radius: Float, mass: Float, x: Float, y: Float): PhysBody =
    PhysBody.circle(radius, mass).apply {
        this.x = x
        this.y = y
        friction = 0.62f
    }

/** Simule [seconds] à 120 Hz, avec les sous-pas automatiques (le mode du jeu). */
internal fun PhysWorld.simulate(seconds: Float, dt: Float = 1f / 120f) {
    repeat((seconds / dt).toInt()) { stepFrame(dt) }
}

/** Simule sans sous-pas : sert à montrer ce qui se passerait sans eux. */
internal fun PhysWorld.simulateNaive(seconds: Float, dt: Float = 1f / 120f) {
    repeat((seconds / dt).toInt()) { step(dt) }
}
