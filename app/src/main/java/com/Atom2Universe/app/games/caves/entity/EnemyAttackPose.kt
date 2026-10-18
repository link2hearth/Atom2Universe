package com.Atom2Universe.app.games.caves.entity

/** Animation timing comes from the same attack clock as impact, including the false start. */
internal object EnemyAttackPose {
    fun preparation(shape: AttackShape?, progress: Float): Float {
        val t=progress.coerceIn(0f,1f)
        if(shape!=AttackShape.FEINT) return t
        return when {
            t<.3f -> t/.3f*.85f
            t<.6f -> .85f-(t-.3f)/.3f*.6f
            else -> .25f+(t-.6f)/.4f*.75f
        }
    }
    fun spin(remaining: Float): Float = if(remaining>0f) (1f-remaining/.55f).coerceIn(0f,1f)*360f else 0f

    fun doubleArm(side: Int,hitIndex: Int,followUp: Boolean,charge: Float,strike: Float): Float {
        val striking=side==(if(hitIndex==0) 1 else -1)
        val preparing=if(followUp) !striking else striking
        return (if(preparing) charge*1.5f else .15f) + (if(striking) strike*1.25f else .2f)
    }
}
