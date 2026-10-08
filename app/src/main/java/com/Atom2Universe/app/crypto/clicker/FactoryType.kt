package com.Atom2Universe.app.crypto.clicker

enum class FactoryType(
    val id: String,
    val basePrice: Double,
    val bonusApcPerUnit: Double,
    val bonusApsPerUnit: Double,
    val boostPerUnit: Double = 0.0
) {
    PROTON_ACCELERATOR("proton_accel",    10_000_000.0,  0.05, 0.00),
    FUSION_REACTOR    ("fusion_reactor",  10_000_000.0,  0.00, 0.05),
    HADRON_COLLIDER   ("hadron_collider", 15_000_000_000.0, 0.03, 0.08),
    PROTON_INJECTOR   ("proton_injector",    50_000_000.0, 0.00, 0.00, 0.10),
    PLASMA_CATALYST   ("plasma_catalyst",    50_000_000.0, 0.00, 0.00, 0.10),
    SYNCHROTRON       ("synchrotron",    75_000_000_000.0, 0.00, 0.00, 0.10);
}
