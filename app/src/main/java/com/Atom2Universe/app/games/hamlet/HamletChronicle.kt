package com.Atom2Universe.app.games.hamlet

/** Local, typed village news. Text is resolved when displayed, so saved news follows the locale. */
class HamletChronicle(private val game: HamletGame) {
    enum class Kind { WELCOME, LEVEL, DELIVERY, GIFT, EXPANSION, HALL, REALM, VISIT, ORDER, PICKUP, HERO, LEGEND }
    data class Entry(val kind: Kind, val at: Long, val subject: String = "", val actor: String = "", val amount: Long = 0)
    private val entries = ArrayList<Entry>(24)
    val rows: List<Entry> get() = entries.toList()
    fun reset() { entries.clear() }
    fun restore(saved: List<Entry>) {
        entries.clear()
        entries.addAll(saved.take(24).map { it.copy(at = it.at.coerceAtLeast(0), subject = it.subject.take(80), actor = it.actor.take(80), amount = it.amount.coerceAtLeast(0)) })
    }
    fun record(kind: Kind, subject: String = "", actor: String = "", amount: Long = 0) {
        entries.add(0, Entry(kind, game.now().coerceAtLeast(0), subject.take(80), actor.take(80), amount.coerceAtLeast(0)))
        if (entries.size > 24) entries.removeAt(entries.lastIndex)
        game.listener?.onChanged()
    }
}
