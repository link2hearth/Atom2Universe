package com.Atom2Universe.app.science.solarsystem

enum class ProportionMode { CLOSE, COMPRESSED, REALISTIC }

data class MoonDef(
    val name: String,
    val textureAsset: String,
    val radiusKm: Float,
    val orbitRadiusKm: Float,
    val orbitalPeriodDays: Float,
    val fallbackColor: Int
)

data class PlanetDef(
    val id: Int,
    val name: String,
    val textureAsset: String,
    val radiusKm: Float,
    val orbitRadiusAU: Float,
    val orbitalPeriodDays: Float,
    val rotationPeriodDays: Float,
    val axialTiltDeg: Float,
    val initialAngleDeg: Float,
    val knownMoons: Int = 0,
    val hasRings: Boolean = false,
    val ringsTextureAsset: String? = null,
    val ringsInnerFactor: Float = 1.25f,
    val ringsOuterFactor: Float = 2.35f,
    val fallbackColor: Int,
    val moons: List<MoonDef> = emptyList()
)

object SolarSystemData {
    const val SUN_RADIUS_KM = 696_000f
    const val SUN_TEXTURE = "textures/planets/sun.jpg"
    val SUN_FALLBACK_COLOR = 0xFFFDB813.toInt()

    val planets = listOf(
        PlanetDef(
            id = 0, name = "Mercury", textureAsset = "textures/planets/mercury.jpg",
            radiusKm = 2_439f, orbitRadiusAU = 0.387f,
            orbitalPeriodDays = 87.97f, rotationPeriodDays = 58.65f, axialTiltDeg = 0.03f,
            initialAngleDeg = 11f, knownMoons = 0,
            fallbackColor = 0xFFB5B5B5.toInt()
        ),
        PlanetDef(
            id = 1, name = "Venus", textureAsset = "textures/planets/venus.jpg",
            radiusKm = 6_051f, orbitRadiusAU = 0.723f,
            orbitalPeriodDays = 224.7f, rotationPeriodDays = -243f, axialTiltDeg = 177.4f,
            initialAngleDeg = 212f, knownMoons = 0,
            fallbackColor = 0xFFE8C76E.toInt()
        ),
        PlanetDef(
            id = 2, name = "Earth", textureAsset = "textures/earth.jpg",
            radiusKm = 6_371f, orbitRadiusAU = 1.0f,
            orbitalPeriodDays = 365.25f, rotationPeriodDays = 1.0f, axialTiltDeg = 23.44f,
            initialAngleDeg = 165f, knownMoons = 1,
            fallbackColor = 0xFF4A8EBA.toInt(),
            moons = listOf(
                MoonDef(
                    name = "Moon", textureAsset = "textures/moon.jpg",
                    radiusKm = 1_737f, orbitRadiusKm = 384_400f,
                    orbitalPeriodDays = 27.32f, fallbackColor = 0xFFCCCCCC.toInt()
                )
            )
        ),
        PlanetDef(
            id = 3, name = "Mars", textureAsset = "textures/planets/mars.jpg",
            radiusKm = 3_389f, orbitRadiusAU = 1.524f,
            orbitalPeriodDays = 686.97f, rotationPeriodDays = 1.026f, axialTiltDeg = 25.19f,
            initialAngleDeg = 93f, knownMoons = 2,
            fallbackColor = 0xFFC1440E.toInt()
        ),
        PlanetDef(
            id = 4, name = "Jupiter", textureAsset = "textures/planets/jupiter.jpg",
            radiusKm = 69_911f, orbitRadiusAU = 5.203f,
            orbitalPeriodDays = 4_332.59f, rotationPeriodDays = 0.414f, axialTiltDeg = 3.13f,
            initialAngleDeg = 356f, knownMoons = 95,
            fallbackColor = 0xFFC88B3A.toInt()
        ),
        PlanetDef(
            id = 5, name = "Saturn", textureAsset = "textures/planets/saturn.jpg",
            radiusKm = 58_232f, orbitRadiusAU = 9.537f,
            orbitalPeriodDays = 10_759.22f, rotationPeriodDays = 0.444f, axialTiltDeg = 26.73f,
            initialAngleDeg = 271f, knownMoons = 146,
            hasRings = true, ringsTextureAsset = "textures/planets/saturn_ring.jpg",
            ringsInnerFactor = 1.25f, ringsOuterFactor = 2.35f,
            fallbackColor = 0xFFE4D191.toInt()
        ),
        PlanetDef(
            id = 6, name = "Uranus", textureAsset = "textures/planets/uranus.jpg",
            radiusKm = 25_362f, orbitRadiusAU = 19.19f,
            orbitalPeriodDays = 30_688.5f, rotationPeriodDays = -0.718f, axialTiltDeg = 97.77f,
            initialAngleDeg = 332f, knownMoons = 28,
            fallbackColor = 0xFFC6EBF5.toInt()
        ),
        PlanetDef(
            id = 7, name = "Neptune", textureAsset = "textures/planets/neptune.jpg",
            radiusKm = 24_622f, orbitRadiusAU = 30.07f,
            orbitalPeriodDays = 60_195f, rotationPeriodDays = 0.671f, axialTiltDeg = 28.32f,
            initialAngleDeg = 270f, knownMoons = 16,
            fallbackColor = 0xFF4B70DD.toInt()
        )
    )
}
