package com.Atom2Universe.app.games.toyboxracers.driving

/** Réglages physiques d'un véhicule Toybox.
 *
 * Les dimensions sont en unités monde. La conduite reste arcade, mais les
 * contacts viennent des roues : cette fiche pourra porter les futurs véhicules.
 */
internal data class VehicleSpec(
    val wheelBase: Float,
    val trackWidth: Float,
    val wheelRadius: Float,
    val rideHeight: Float,
    val suspensionTravel: Float,
    val suspensionStiffness: Float,
    val suspensionDamping: Float,
    val lateralGrip: Float,
    val longitudinalGrip: Float,
    val mass: Float
) {
    companion object {
        val ToyCar = VehicleSpec(
            wheelBase = 0.92f,
            trackWidth = 0.78f,
            wheelRadius = 0.21f,
            rideHeight = 0.24f,
            suspensionTravel = 0.16f,
            suspensionStiffness = 46f,
            suspensionDamping = 8.5f,
            lateralGrip = 1.0f,
            longitudinalGrip = 1.0f,
            mass = 1.0f
        )
    }
}
