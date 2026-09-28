package com.Atom2Universe.app.games.caves.mode

/** Shadow health: the real player stays alive while the HUD shows survival damage. */
internal class TrainingHealth(val maxHp: Int = 50) {
    var hp = maxHp
        private set
    var lastDamage = 0
        private set
    var defeats = 0
        private set
    private var resetIn = 0f

    fun damage(amount: Int) {
        if (amount <= 0 || hp == 0) return
        lastDamage = amount
        hp = (hp - amount).coerceAtLeast(0)
        if (hp == 0) { defeats++; resetIn = 3f }
    }
    fun tick(dt: Float) {
        if (hp != 0) return
        resetIn -= dt
        if (resetIn <= 0f) hp = maxHp
    }
    fun reset() { hp=maxHp; lastDamage=0; defeats=0; resetIn=0f }
}
