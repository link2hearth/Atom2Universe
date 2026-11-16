package com.Atom2Universe.app.games.mercerie

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * L'atelier qui fournit les niveaux : les trente premiers sont faits main, les suivants sont
 * fabriqués à la demande par [MercerieGenerator].
 *
 * Régler un étage demande au robot quelques parties (une à quelques secondes) : cela se fait sur un
 * fil à part, et le résultat (variante, coups, étoiles) est gardé dans `mercerie_levels` pour ne
 * jamais être refait. [warm] prépare l'étage suivant pendant que le joueur finit le sien.
 */
class MercerieWorkshop(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mercerie_levels", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val ready = HashMap<Int, MercerieLevel>()
    private val waiting = HashMap<Int, ArrayList<(MercerieLevel) -> Unit>>()

    private fun key(number: Int) = "g${MercerieGenerator.VERSION}_$number"

    /** Le niveau s'il est déjà prêt, sinon null. À appeler depuis le fil d'affichage. */
    fun peek(number: Int): MercerieLevel? {
        if (number <= MercerieLevels.HANDMADE) return MercerieLevels.get(number)
        ready[number]?.let { return it }
        val f = prefs.getString(key(number), null)?.split(';')?.mapNotNull { it.toIntOrNull() } ?: return null
        if (f.size != 5) return null
        return MercerieGenerator.build(number, f[0]).with(moves = f[1], stars = intArrayOf(f[2], f[3], f[4])).also { ready[number] = it }
    }

    /** Donne le niveau à [then], tout de suite s'il est prêt, sinon dès que le robot l'a réglé. */
    fun request(number: Int, then: (MercerieLevel) -> Unit) {
        peek(number)?.let { then(it); return }
        val queue = waiting[number]
        if (queue != null) { queue += then; return }
        waiting[number] = arrayListOf(then)
        worker.execute {
            val (variant, level) = MercerieGenerator.make(number)
            main.post {
                ready[number] = level
                prefs.edit().putString(key(number), "$variant;${level.moves};${level.stars.joinToString(";")}").apply()
                waiting.remove(number)?.forEach { it(level) }
            }
        }
    }

    /** Prépare un niveau d'avance, sans rien attendre en retour. */
    fun warm(number: Int) = request(number) {}

    private companion object {
        /** Un seul réglage à la fois, en basse priorité : la partie en cours ne doit pas saccader. */
        val worker = Executors.newSingleThreadExecutor { task ->
            Thread(task, "mercerie-atelier").apply { priority = Thread.MIN_PRIORITY; isDaemon = true }
        }
    }
}
