package com.Atom2Universe.app.crypto.clicker

import kotlin.math.pow

private const val EFFECT_DURATION_MS   = 30_000L
const val FRENZY_MULTIPLIER            = 2.0

data class FrenzyTickResult(
    val changed: Boolean,
    val apcTriggered: Int = 0,
    val apsTriggered: Int = 0,
    val apcExpiredClickCounts: List<Int> = emptyList()
)

private data class FrenzyInstance(val expiryMs: Long, val clickCount: Int = 0)

private data class FrenzyTypeState(
    val instances: List<FrenzyInstance> = emptyList()
)

private data class InternalFrenzyState(
    val perClick: FrenzyTypeState = FrenzyTypeState(),
    val perSecond: FrenzyTypeState = FrenzyTypeState(),
    val spawnAccumulator: Double = 0.0
)

private data class PruneResult(
    val state: FrenzyTypeState,
    val expiredInstances: List<FrenzyInstance>
)

class FrenzyManager {

    private var state = InternalFrenzyState()

    fun tick(nowMs: Long, deltaMs: Long, spawnChancePerSec: Double): FrenzyTickResult {
        var s = state
        var changed = false
        var apcTriggered = 0
        var apsTriggered = 0

        // Purge des instances expirées, on récupère leurs compteurs
        val pcPrune = prune(s.perClick, nowMs)
        val psPrune = prune(s.perSecond, nowMs)

        if (pcPrune.state !== s.perClick || psPrune.state !== s.perSecond) {
            s = s.copy(perClick = pcPrune.state, perSecond = psPrune.state)
            changed = true
        }

        val expiredClickCounts = pcPrune.expiredInstances.map { it.clickCount }

        // Tentatives de spawn (1 tentative par seconde accumulée)
        val newAcc = s.spawnAccumulator + deltaMs / 1000.0
        val attempts = newAcc.toInt()
        if (attempts > 0) {
            var newPc = s.perClick
            var newPs = s.perSecond
            repeat(attempts) {
                if (spawnChancePerSec > 0.0 && Math.random() < spawnChancePerSec) {
                    newPc = newPc.copy(instances = newPc.instances + FrenzyInstance(nowMs + EFFECT_DURATION_MS))
                    apcTriggered++
                    changed = true
                }
                if (spawnChancePerSec > 0.0 && Math.random() < spawnChancePerSec) {
                    newPs = newPs.copy(instances = newPs.instances + FrenzyInstance(nowMs + EFFECT_DURATION_MS))
                    apsTriggered++
                    changed = true
                }
            }
            s = s.copy(perClick = newPc, perSecond = newPs, spawnAccumulator = newAcc - attempts)
        } else {
            s = s.copy(spawnAccumulator = newAcc)
        }

        state = s
        return FrenzyTickResult(changed, apcTriggered, apsTriggered, expiredClickCounts)
    }

    private fun prune(t: FrenzyTypeState, nowMs: Long): PruneResult {
        val (active, expired) = t.instances.partition { it.expiryMs > nowMs }
        val newState = if (active.size == t.instances.size) t else t.copy(instances = active)
        return PruneResult(newState, expired)
    }

    fun recordApcClick(nowMs: Long): Boolean {
        val s = state
        val hasActive = s.perClick.instances.any { it.expiryMs > nowMs }
        if (!hasActive) return false
        // Incrémente le compteur de chaque instance active indépendamment
        val updated = s.perClick.instances.map {
            if (it.expiryMs > nowMs) it.copy(clickCount = it.clickCount + 1) else it
        }
        state = s.copy(perClick = s.perClick.copy(instances = updated))
        return true
    }

    fun getMultiplier(type: FrenzyType, nowMs: Long): Double {
        val entry = when (type) {
            FrenzyType.PER_CLICK  -> state.perClick
            FrenzyType.PER_SECOND -> state.perSecond
        }
        val stacks = entry.instances.count { it.expiryMs > nowMs }
        return if (stacks == 0) 1.0 else FRENZY_MULTIPLIER.pow(stacks.toDouble())
    }

    fun buildUiState(nowMs: Long): FrenzyUiState {
        val activeApc = state.perClick.instances.filter { it.expiryMs > nowMs }
        val activeAps = state.perSecond.instances.filter { it.expiryMs > nowMs }
        return FrenzyUiState(
            apcEffectExpiries = activeApc.map { it.expiryMs },
            apsEffectExpiries = activeAps.map { it.expiryMs },
            apcClickCount     = activeApc.maxOfOrNull { it.clickCount } ?: 0
        )
    }

    fun reset() {
        state = InternalFrenzyState()
    }
}
