package com.Atom2Universe.app.games.trebuchet

import kotlin.random.Random

/**
 * Les petites gens du site, et ce qu'ils font quand on leur tombe dessus.
 *
 * Ce ne sont pas des corps physiques : personne ne les vise, ils ne pèsent sur
 * rien, le boulet les traverse sans les voir. Ce sont des silhouettes qui courent,
 * déclenchées par ce que [TargetField] rapporte déjà avoir cassé — la construction
 * reste seule responsable du score, les fuyards ne sont qu'un commentaire sur ce
 * qu'elle vient de subir.
 *
 * **Ils fuient tous dans le même sens : loin de la machine.** Le site est toujours
 * posé à distance positive du pied du trébuchet — c'est de là que vient le coup —
 * et courir vers elle n'aurait aucun sens, en plus de les faire traverser l'écran
 * jusque sous les jauges de l'interface. Ils s'enfoncent donc dans leur propre
 * arrière-pays, ce qui a l'avantage d'être aussi la direction la plus simple à
 * coder : une seule vitesse, jamais de choix à faire.
 */
class VillagerField {

    class Villager internal constructor(
        var x: Float,
        /** Taille adulte ou enfant : de 1,1 à 1,8 m, en mètres — jamais mise à l'échelle
         *  du site, comme la végétation : voir [VegetationField]. */
        val height: Float,
        val speed: Float,
        val legPhase: Float,
        var age: Float = 0f
    )

    private val villagers = ArrayList<Villager>()
    val list: List<Villager> get() = villagers

    private var terrain: Terrain = Terrain.FLAT
    private var siteLeft = 0f
    private var siteRight = 0f
    private var rng = Random(1L)

    /**
     * Efface tout et repart d'un terrain neuf : à faire à chaque nouveau site, sans
     * quoi les fuyards d'un village déjà rasé continueraient de courir sur le
     * suivant.
     */
    fun reset(seed: Long, terrain: Terrain, siteLeft: Float, siteRight: Float) {
        villagers.clear()
        this.terrain = terrain
        this.siteLeft = siteLeft
        this.siteRight = siteRight
        rng = Random(seed)
    }

    /** Fait déguerpir quelques habitants de plus, autour de [alarmX]. */
    fun panic(alarmX: Float) {
        val count = 1 + rng.nextInt(2)
        repeat(count) {
            if (villagers.size >= MAX_VILLAGERS) return
            villagers += Villager(
                x = alarmX + (rng.nextFloat() - 0.5f) * 5f,
                height = 1.1f + rng.nextFloat() * 0.7f,
                speed = 2.4f + rng.nextFloat() * 1.6f,
                legPhase = rng.nextFloat() * 6.2832f
            )
        }
    }

    /** Les fait courir, et balaie ceux qui sont sortis de vue ou ont fini leur course. */
    fun update(dt: Float) {
        if (villagers.isEmpty()) return
        val clearedAt = siteRight + RUN_CLEAR
        villagers.removeAll { v ->
            v.x += v.speed * dt
            v.age += dt
            v.age > LIFETIME || v.x > clearedAt
        }
    }

    fun groundY(x: Float): Float = terrain.heightAt(x)

    companion object {
        private const val MAX_VILLAGERS = 10
        private const val LIFETIME = 9f

        /** Distance au-delà du bord du site où l'on cesse de les suivre : ils sont
         *  réputés en sûreté, et ça ne sert plus à rien de les animer. */
        private const val RUN_CLEAR = 40f
    }
}
