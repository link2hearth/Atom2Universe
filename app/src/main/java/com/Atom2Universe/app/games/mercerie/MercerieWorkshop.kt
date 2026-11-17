package com.Atom2Universe.app.games.mercerie

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * L'atelier qui fournit les niveaux : les trente premiers et les dix de la mise à jour (posés à la
 * suite de la progression du joueur, [MercerieProgress.extraAt]) sont faits main, les autres sont
 * fabriqués à la demande par [MercerieGenerator].
 *
 * Régler un étage demande au robot quelques parties (une à quelques secondes) : cela se fait sur un
 * fil à part, et le résultat (variante, coups, étoiles) est gardé dans `mercerie_levels` pour ne
 * jamais être refait. [warm] prépare l'étage suivant pendant que le joueur finit le sien.
 *
 * En mode facile ([MercerieProgress.easy]), chaque numéro donne la version facile du même niveau :
 * [MercerieLevel.asEasy] pour les niveaux faits main, un étage fabriqué et réglé à part pour les
 * étages sans fin (gardé sous sa propre clé).
 */
class MercerieWorkshop(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mercerie_levels", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val ready = HashMap<String, MercerieLevel>()
    private val waiting = HashMap<String, ArrayList<(MercerieLevel) -> Unit>>()
    private val progress = MercerieProgress(context.applicationContext)

    /** Un étage se range sous son numéro de fabrication : celui-ci ne bouge pas quand des niveaux s'intercalent. */
    private fun key(floor: Int, easy: Boolean) = "${if (easy) "e" else "g"}${MercerieGenerator.VERSION}_$floor"

    private fun tag(number: Int, easy: Boolean) = if (easy) "e$number" else "$number"

    /** Le niveau s'il est déjà prêt, sinon null. À appeler depuis le fil d'affichage. */
    fun peek(number: Int): MercerieLevel? {
        val easy = progress.easy
        when (val slot = MercerieLevels.slot(number, progress.extraAt)) {
            is MercerieLevels.Slot.Base -> return MercerieLevels.get(slot.number).let { if (easy) it.asEasy() else it }
            is MercerieLevels.Slot.Extra -> return MercerieLevels.extra(slot.k, number).let { if (easy) it.asEasy() else it }
            is MercerieLevels.Slot.Floor -> {
                ready[tag(number, easy)]?.let { return it }
                val f = prefs.getString(key(slot.floor, easy), null)?.split(';')?.mapNotNull { it.toIntOrNull() } ?: return null
                if (f.size != 6) return null
                return MercerieGenerator.build(slot.floor, f[0], easy).with(moves = f[1], stars = intArrayOf(f[2], f[3], f[4]), number = number, par = f[5])
                    .also { ready[tag(number, easy)] = it }
            }
        }
    }

    /** Donne le niveau à [then], tout de suite s'il est prêt, sinon dès que le robot l'a réglé. */
    fun request(number: Int, then: (MercerieLevel) -> Unit) {
        peek(number)?.let { then(it); return }
        val easy = progress.easy
        val tag = tag(number, easy)
        val queue = waiting[tag]
        if (queue != null) { queue += then; return }
        val floor = (MercerieLevels.slot(number, progress.extraAt) as? MercerieLevels.Slot.Floor)?.floor ?: return
        waiting[tag] = arrayListOf(then)
        worker.execute {
            val (variant, made) = MercerieGenerator.make(floor, easy)
            val level = made.with(number = number)
            main.post {
                ready[tag] = level
                prefs.edit().putString(key(floor, easy), "$variant;${level.moves};${level.stars.joinToString(";")};${level.par}").apply()
                waiting.remove(tag)?.forEach { it(level) }
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
