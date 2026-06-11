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
    val axialTiltDeg: Float,             // obliquité citée (par rapport à l'orbite) — pour l'affichage
    // Orientation réelle de l'axe de rotation dans le repère écliptique (pôle nord IAU J2000).
    // axisEclLonDeg = longitude écliptique vers laquelle l'axe penche (azimut),
    // axisObliquityEclDeg = angle de l'axe par rapport à la normale de l'écliptique.
    val axisEclLonDeg: Float = 90f,
    val axisObliquityEclDeg: Float = 0f,
    // Éléments orbitaux à J2000 (Meeus "Astronomical Algorithms" Table 31.a + VSOP87)
    val initialAngleDeg: Float,          // L0 : longitude moyenne à J2000 (°)
    val meanMotionDegCentury: Double,    // L1 : mouvement moyen (°/siècle julien)
    val eccentricity: Float,             // e  : excentricité à J2000
    val perihelionLongDeg: Float,        // ω̃  : longitude du périhélie à J2000 (°)
    val orbitalInclinationDeg: Float,    // i  : inclinaison sur l'écliptique (°)
    val ascendingNodeDeg: Float,         // Ω  : longitude du nœud ascendant (°)
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

    // Source : Meeus "Astronomical Algorithms" Table 31.a + VSOP87 simplifié.
    // L0 = longitude moyenne à J2000 (°), L1 = mouvement moyen (°/siècle julien),
    // e = excentricité, ω̃ = longitude du périhélie (°) — tous à J2000.0.
    val planets = listOf(
        PlanetDef(
            id = 0, name = "Mercury", textureAsset = "textures/planets/mercury.jpg",
            radiusKm = 2_439f, orbitRadiusAU = 0.387f,
            orbitalPeriodDays = 87.97f, rotationPeriodDays = 58.65f, axialTiltDeg = 0.03f,
            axisEclLonDeg = 318.24f, axisObliquityEclDeg = 7.04f,
            initialAngleDeg = 252.250324f, meanMotionDegCentury = 149472.674986,
            eccentricity = 0.20563f, perihelionLongDeg = 77.456f,
            orbitalInclinationDeg = 7.005f, ascendingNodeDeg = 48.331f,
            knownMoons = 0, fallbackColor = 0xFFB5B5B5.toInt()
        ),
        PlanetDef(
            id = 1, name = "Venus", textureAsset = "textures/planets/venus.jpg",
            radiusKm = 6_051f, orbitRadiusAU = 0.723f,
            orbitalPeriodDays = 224.7f, rotationPeriodDays = -243f, axialTiltDeg = 177.4f,
            axisEclLonDeg = 30.19f, axisObliquityEclDeg = 1.24f,
            initialAngleDeg = 181.979801f, meanMotionDegCentury = 58517.815676,
            eccentricity = 0.00677f, perihelionLongDeg = 131.564f,
            orbitalInclinationDeg = 3.395f, ascendingNodeDeg = 76.680f,
            knownMoons = 0, fallbackColor = 0xFFE8C76E.toInt()
        ),
        PlanetDef(
            id = 2, name = "Earth", textureAsset = "textures/earth.jpg",
            radiusKm = 6_371f, orbitRadiusAU = 1.0f,
            orbitalPeriodDays = 365.25f, rotationPeriodDays = 1.0f, axialTiltDeg = 23.44f,
            axisEclLonDeg = 90.0f, axisObliquityEclDeg = 23.44f,
            initialAngleDeg = 100.464457f, meanMotionDegCentury = 35999.372851,
            eccentricity = 0.01671f, perihelionLongDeg = 102.937f,
            orbitalInclinationDeg = 0.000f, ascendingNodeDeg = 0.000f,
            knownMoons = 1, fallbackColor = 0xFF4A8EBA.toInt(),
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
            axisEclLonDeg = 352.91f, axisObliquityEclDeg = 26.72f,
            initialAngleDeg = 355.453408f, meanMotionDegCentury = 19140.299314,
            eccentricity = 0.09341f, perihelionLongDeg = 336.061f,
            orbitalInclinationDeg = 1.850f, ascendingNodeDeg = 49.558f,
            knownMoons = 2, fallbackColor = 0xFFC1440E.toInt()
        ),
        PlanetDef(
            id = 4, name = "Jupiter", textureAsset = "textures/planets/jupiter.jpg",
            radiusKm = 69_911f, orbitRadiusAU = 5.203f,
            orbitalPeriodDays = 4_332.59f, rotationPeriodDays = 0.414f, axialTiltDeg = 3.13f,
            axisEclLonDeg = 247.82f, axisObliquityEclDeg = 2.22f,
            initialAngleDeg = 34.396441f, meanMotionDegCentury = 3034.905675,
            eccentricity = 0.04839f, perihelionLongDeg = 14.331f,
            orbitalInclinationDeg = 1.303f, ascendingNodeDeg = 100.464f,
            knownMoons = 95, fallbackColor = 0xFFC88B3A.toInt()
        ),
        PlanetDef(
            id = 5, name = "Saturn", textureAsset = "textures/planets/saturn.jpg",
            radiusKm = 58_232f, orbitRadiusAU = 9.537f,
            orbitalPeriodDays = 10_759.22f, rotationPeriodDays = 0.444f, axialTiltDeg = 26.73f,
            axisEclLonDeg = 79.53f, axisObliquityEclDeg = 28.05f,
            initialAngleDeg = 49.954244f, meanMotionDegCentury = 1222.113795,
            eccentricity = 0.05415f, perihelionLongDeg = 92.598f,
            orbitalInclinationDeg = 2.489f, ascendingNodeDeg = 113.666f,
            knownMoons = 146, hasRings = true,
            ringsTextureAsset = "textures/planets/saturn_ring.jpg",
            ringsInnerFactor = 1.25f, ringsOuterFactor = 2.35f,
            fallbackColor = 0xFFE4D191.toInt()
        ),
        PlanetDef(
            id = 6, name = "Uranus", textureAsset = "textures/planets/uranus.jpg",
            radiusKm = 25_362f, orbitRadiusAU = 19.19f,
            orbitalPeriodDays = 30_688.5f, rotationPeriodDays = -0.718f, axialTiltDeg = 97.77f,
            axisEclLonDeg = 257.65f, axisObliquityEclDeg = 82.28f,
            initialAngleDeg = 313.232504f, meanMotionDegCentury = 428.379134,
            eccentricity = 0.04717f, perihelionLongDeg = 170.964f,
            orbitalInclinationDeg = 0.773f, ascendingNodeDeg = 74.006f,
            knownMoons = 28, fallbackColor = 0xFFC6EBF5.toInt()
        ),
        PlanetDef(
            id = 7, name = "Neptune", textureAsset = "textures/planets/neptune.jpg",
            radiusKm = 24_622f, orbitRadiusAU = 30.07f,
            orbitalPeriodDays = 60_195f, rotationPeriodDays = 0.671f, axialTiltDeg = 28.32f,
            axisEclLonDeg = 319.24f, axisObliquityEclDeg = 28.03f,
            initialAngleDeg = 304.880003f, meanMotionDegCentury = 218.459395,
            eccentricity = 0.00859f, perihelionLongDeg = 44.971f,
            orbitalInclinationDeg = 1.770f, ascendingNodeDeg = 131.784f,
            knownMoons = 16, fallbackColor = 0xFF4B70DD.toInt()
        )
    )
}
