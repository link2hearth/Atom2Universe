package com.Atom2Universe.app.games.physics

/** Constantes SI communes aux machines du bac à sable. */
object PhysicsConstants {
    /** Pesanteur conventionnelle terrestre, en m/s². */
    const val STANDARD_GRAVITY = 9.80665f

    /** Pression atmosphérique standard au niveau de la mer, en pascals. */
    const val ATMOSPHERIC_PRESSURE = 101_325f

    /** Masse volumique de l'air sec à 15 °C et au niveau de la mer, en kg/m³. */
    const val AIR_DENSITY = 1.225f

    /** Exposant adiabatique de l'air, sans unité. */
    const val AIR_HEAT_CAPACITY_RATIO = 1.4f

    /** Constante spécifique de l'air sec, en J/(kg·K). */
    const val AIR_GAS_CONSTANT = 287.05f

    /** Température de référence des composants pneumatiques, en kelvins. */
    const val ROOM_TEMPERATURE = 293.15f

    /** Masse volumique de l'eau douce, en kg/m³. */
    const val WATER_DENSITY = 998.2f
}
