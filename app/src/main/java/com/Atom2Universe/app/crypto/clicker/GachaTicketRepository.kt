package com.Atom2Universe.app.crypto.clicker

import android.content.Context

class GachaTicketRepository(context: Context) {

    private val dao = ClickerDatabase.getInstance(context).gachaTicketDao()
    private val achievementRepo = ClickerAchievementRepository(context.applicationContext)

    /** Tickets gagnés par heure : doublé une fois le succès cyanobactérie débloqué. */
    private fun ticketsPerHour(): Int =
        if (ClickerAchievements.CYANOBACTERIA_ID in achievementRepo.loadUnlocked()) 2 else 1

    suspend fun load(): GachaTicketStateEntity {
        return dao.load() ?: GachaTicketStateEntity(id = 0, totalTickets = 10, lastTicketAwardMs = System.currentTimeMillis())
    }

    suspend fun save(entity: GachaTicketStateEntity) {
        dao.save(entity)
    }

    suspend fun awardTickets(nowMs: Long): GachaTicketStateEntity {
        val current = load()
        val elapsedMs = if (current.lastTicketAwardMs > 0L) nowMs - current.lastTicketAwardMs else 0L
        val hoursElapsed = (elapsedMs / (60 * 60 * 1000L)).toInt()
        if (hoursElapsed <= 0) return current
        val ticketsEarned = hoursElapsed * ticketsPerHour()
        val updated = current.copy(
            totalTickets = current.totalTickets + ticketsEarned,
            lastTicketAwardMs = current.lastTicketAwardMs + hoursElapsed * (60 * 60 * 1000L)
        )
        save(updated)
        return updated
    }

    suspend fun addTickets(count: Int): GachaTicketStateEntity {
        val current = load()
        val updated = current.copy(totalTickets = (current.totalTickets + count).coerceAtLeast(0))
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
