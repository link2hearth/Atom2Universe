package com.Atom2Universe.app.games.physics

/**
 * Un contact qui vient de **naître** ou de se **défaire** pendant le pas écoulé.
 *
 * Le moteur savait déjà empêcher deux corps de se traverser ; il ne savait pas le
 * **dire**. C'est pourtant tout ce qu'un jeu de mécanismes demande : une machine
 * infernale ne s'intéresse jamais à la position de la boule, elle s'intéresse au
 * moment précis où la boule touche le levier. Interroger les positions à chaque image
 * donne la bonne réponse presque toujours, et la rate exactement quand elle compte —
 * une boule à 15 m/s parcourt 25 cm entre deux images à 60 Hz, et un moteur qui
 * sous-découpe son pas peut très bien voir le contact naître et mourir sans qu'aucune
 * image ne tombe au milieu.
 *
 * **Un événement par couple de corps, pas par couple de formes.** Un corps composé de
 * trois boîtes qui se pose sur une planche fabrique trois contacts dans le solveur, et
 * un seul événement ici : le jeu veut savoir que la caisse a touché la planche, pas
 * combien de coins la touchent.
 *
 * **L'objet est réemployé d'une image à l'autre.** Il est valable le temps qu'on lise
 * [PhysWorld.contactEvents] ; ce qu'on veut garder au-delà se recopie.
 */
class ContactEvent internal constructor() {

    /** Les deux corps concernés. L'ordre est celui de la découverte, sans autre sens. */
    var a: PhysBody? = null
        internal set

    var b: PhysBody? = null
        internal set

    /** Vrai quand le contact vient de naître, faux quand il vient de se défaire. */
    var begin = false
        internal set

    /** Vrai quand l'un des deux corps est une zone de détection ([PhysBody.isSensor]). */
    var sensor = false
        internal set

    internal fun set(a: PhysBody?, b: PhysBody?, begin: Boolean = false, sensor: Boolean = false) {
        this.a = a
        this.b = b
        this.begin = begin
        this.sensor = sensor
    }

    /**
     * L'autre corps du couple, quand on en connaît déjà un. Rend `null` si [body] n'est
     * pas concerné.
     *
     * C'est la question qu'on pose neuf fois sur dix : « ma zone d'arrivée a été
     * touchée — par quoi ? »
     */
    fun other(body: PhysBody): PhysBody? = when {
        a === body -> b
        b === body -> a
        else -> null
    }

    /** Vrai si ce couple est exactement celui-là, quel que soit l'ordre. */
    fun involves(first: PhysBody, second: PhysBody): Boolean =
        (a === first && b === second) || (a === second && b === first)

    /** Vrai si [body] est l'un des deux corps. */
    operator fun contains(body: PhysBody): Boolean = a === body || b === body
}
