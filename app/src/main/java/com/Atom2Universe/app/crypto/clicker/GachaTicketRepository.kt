package com.Atom2Universe.app.crypto.clicker

import android.content.Context

class GachaTicketRepository(context: Context) {

    private val dao = ClickerDatabase.getInstance(context).gachaTicketDao()

    suspend fun load(): GachaTicketStateEntity {
        return dao.load() ?: GachaTicketStateEntity(id = 0, totalTickets = 10, lastTicketAwardMs = System.currentTimeMillis())
    }

    suspend fun save(entity: GachaTicketStateEntity) {
        dao.save(entity)
    }

    suspend fun awardTickets(nowMs: Long): GachaTicketStateEntity {
        val current = load()
        val elapsedMs = if (current.lastTicketAwardMs > 0L) nowMs - current.lastTicketAwardMs else 0L
        val ticketsEarned = (elapsedMs / (60 * 60 * 1000)).toInt()

        val updated = if (ticketsEarned > 0) {
            current.copy(
                totalTickets = current.totalTickets + ticketsEarned,
                lastTicketAwardMs = nowMs
            )
        } else {
            current
        }

        save(updated)
        return updated
    }

    suspend fun consumeTicket(): GachaTicketStateEntity {
        val current = load()
        val updated = if (current.totalTickets > 0) {
            current.copy(totalTickets = current.totalTickets - 1)
        } else {
            current
        }
        save(updated)
        return updated
    }

    suspend fun consumeTickets(count: Int): GachaTicketStateEntity {
        val current = load()
        val updated = current.copy(totalTickets = (current.totalTickets - count).coerceAtLeast(0))
        save(updated)
        return updated
    }

    suspend fun resetTickets() {
        save(GachaTicketStateEntity(id = 0, totalTickets = 10, lastTicketAwardMs = System.currentTimeMillis()))
    }
}
