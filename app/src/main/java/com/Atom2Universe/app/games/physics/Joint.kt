package com.Atom2Universe.app.games.physics

/**
 * Une liaison entre deux corps.
 *
 * Toutes les liaisons se résolvent en deux temps, comme les contacts :
 *
 *  - [applyImpulse] corrige les **vitesses réelles** pour que la liaison soit
 *    respectée à l'avenir ;
 *  - [applyPositionImpulse] rattrape l'écart **déjà accumulé**, mais en poussant
 *    sur les vitesses fantômes du corps ([PhysBody.pvx]) et non sur les vraies.
 *
 * Cette séparation est ce qui rend le moteur solide. Corriger une position en
 * poussant sur les vraies vitesses revient à créer de l'énergie, et une chaîne de
 * liaisons un peu raide transforme cette énergie en explosion.
 */
abstract class Joint(val a: PhysBody, val b: PhysBody) {

    /**
     * Liaison active. La mettre à `false` décroche instantanément les deux corps :
     * c'est comme ça qu'une fronde lâche son projectile.
     */
    var enabled = true

    /**
     * Faut-il quand même tester la collision entre les deux corps reliés ? Non par
     * défaut : l'axe d'un trébuchet traverse le bras, les deux se chevauchent donc
     * forcément, et les laisser se repousser ferait vibrer la machine.
     */
    var collideConnected = false

    /** Prépare la résolution du pas : masses effectives, écarts, réapplication. */
    internal abstract fun preStep(invDt: Float)

    /** Une passe sur les vitesses réelles. */
    internal abstract fun applyImpulse()

    /** Une passe sur les vitesses fantômes, pour replacer les corps. */
    internal abstract fun applyPositionImpulse()

    /** Oublie les impulsions mémorisées (après avoir téléporté un corps). */
    abstract fun reset()
}
