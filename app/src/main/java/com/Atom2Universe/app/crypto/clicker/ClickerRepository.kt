package com.Atom2Universe.app.crypto.clicker

import android.content.Context
import com.Atom2Universe.app.crypto.clicker.engine.LayeredNumber

class ClickerRepository(context: Context) {

    private val dao = ClickerDatabase.getInstance(context).dao()
    private val ticketRepo = GachaTicketRepository(context)

    suspend fun save(state: ClickerGameState) {
        dao.save(state.toEntity())
    }

    suspend fun reset() {
        dao.deleteAll()
    }

    suspend fun resetAll() {
        dao.deleteAll()
        ticketRepo.resetTickets()
    }

    suspend fun load(): ClickerGameState {
        val state = dao.load()?.toState() ?: ClickerGameState()
        val ticketState = ticketRepo.awardTickets(System.currentTimeMillis())
        return state.copy(gachaTickets = ticketState.totalTickets)
    }

    private fun ClickerGameState.toEntity() = ClickerStateEntity(
        id = 0,
        atomsSign = atoms.sign, atomsLayer = atoms.layer,
        atomsMantissa = atoms.mantissa, atomsExponent = atoms.exponent, atomsValue = atoms.value,
        lifetimeSign = lifetime.sign, lifetimeLayer = lifetime.layer,
        lifetimeMantissa = lifetime.mantissa, lifetimeExponent = lifetime.exponent, lifetimeValue = lifetime.value,
        perClickSign = perClick.sign, perClickLayer = perClick.layer,
        perClickMantissa = perClick.mantissa, perClickExponent = perClick.exponent, perClickValue = perClick.value,
        perSecondSign = perSecond.sign, perSecondLayer = perSecond.layer,
        perSecondMantissa = perSecond.mantissa, perSecondExponent = perSecond.exponent, perSecondValue = perSecond.value,
        godFingerLevel = godFingerLevel,
        starCoreLevel = starCoreLevel,
        neutrinosCount = neutrinos,
        apcToApsLevel = apcToApsLevel,
        apsToApcLevel = apsToApcLevel
    )

    private fun ClickerStateEntity.toState() = ClickerGameState(
        atoms = layered(atomsSign, atomsLayer, atomsMantissa, atomsExponent, atomsValue),
        lifetime = layered(lifetimeSign, lifetimeLayer, lifetimeMantissa, lifetimeExponent, lifetimeValue),
        perClick = layered(perClickSign, perClickLayer, perClickMantissa, perClickExponent, perClickValue)
            .takeUnless { it.isZero() } ?: LayeredNumber.one(),
        perSecond = layered(perSecondSign, perSecondLayer, perSecondMantissa, perSecondExponent, perSecondValue),
        godFingerLevel = godFingerLevel,
        starCoreLevel = starCoreLevel,
        neutrinos = neutrinosCount,
        apcToApsLevel = apcToApsLevel,
        apsToApcLevel = apsToApcLevel
    )

    private fun layered(sign: Int, layer: Int, mantissa: Double, exponent: Double, value: Double) =
        LayeredNumber(mapOf("sign" to sign, "layer" to layer,
            "mantissa" to mantissa, "exponent" to exponent, "value" to value))

    suspend fun consumeGachaTicket() {
        ticketRepo.consumeTicket()
    }

    suspend fun loadGachaTickets(): Int {
        return ticketRepo.awardTickets(System.currentTimeMillis()).totalTickets
    }
}
