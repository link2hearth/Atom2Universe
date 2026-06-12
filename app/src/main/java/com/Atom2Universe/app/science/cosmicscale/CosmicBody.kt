package com.Atom2Universe.app.science.cosmicscale

import android.graphics.Color
import com.Atom2Universe.app.R
import kotlin.math.ln
import kotlin.math.pow

/** Famille d'astre — pilote le mode de rendu GL et le regroupement dans les sélecteurs. */
enum class BodyKind { ROCKY, GAS, STAR, BLACK_HOLE }

/** Catégorie d'affichage dans les menus de sélection. */
enum class CosmicCategory { PLANET, STAR, BLACK_HOLE }

/**
 * Un astre comparable. Les rayons couvrent ~11 ordres de grandeur (Lune ≈ 1 737 km →
 * TON 618 ≈ 1,95e11 km), d'où l'usage systématique de [Double].
 *
 * - [radiusKm]       rayon physique (équatorial), source NASA fact sheets / IAU.
 * - [textureAsset]   carte equirectangulaire (planètes/étoile) ou billboard (trou noir) ; null → fallback.
 * - [tintColor]      couleur de teinte (étoiles : corps noir ; planètes : couleur de repli).
 * - [temperatureK]   température de surface (étoiles), 0 sinon.
 * - [spectralType]   code spectral universel (ex. "G2V"), null si non applicable.
 * - [massSolar]      masse en masses solaires (étoiles & trous noirs), null sinon.
 */
data class CosmicBody(
    val id: String,
    val nameRes: Int,
    val kind: BodyKind,
    val radiusKm: Double,
    val textureAsset: String?,
    val tintColor: Int,
    val temperatureK: Int = 0,
    val spectralType: String? = null,
    val massSolar: Double? = null,
    val hasRings: Boolean = false
) {
    val category: CosmicCategory
        get() = when (kind) {
            BodyKind.ROCKY, BodyKind.GAS -> CosmicCategory.PLANET
            BodyKind.STAR -> CosmicCategory.STAR
            BodyKind.BLACK_HOLE -> CosmicCategory.BLACK_HOLE
        }

    val radiusInEarths: Double get() = radiusKm / CosmicScaleData.R_EARTH_KM
    val radiusInSuns: Double get() = radiusKm / CosmicScaleData.R_SUN_KM

    /** Rayon de Schwarzschild en km — défini pour les trous noirs. */
    val schwarzschildKm: Double? get() = massSolar?.takeIf { kind == BodyKind.BLACK_HOLE }
        ?.let { it * CosmicScaleData.RS_PER_SOLAR_MASS_KM }
}

object CosmicScaleData {

    const val R_SUN_KM = 696_340.0
    const val R_EARTH_KM = 6_371.0
    /** Rayon de Schwarzschild par masse solaire : R_s = 2GM/c² ≈ 2,9536 km/M☉. */
    const val RS_PER_SOLAR_MASS_KM = 2.9536

    private fun planet(
        id: String, nameRes: Int, kind: BodyKind, radiusKm: Double,
        texture: String, color: Int, hasRings: Boolean = false
    ) = CosmicBody(id, nameRes, kind, radiusKm, texture, color, hasRings = hasRings)

    private fun star(
        id: String, nameRes: Int, solarRadii: Double, temperatureK: Int,
        spectralType: String, massSolar: Double, texture: String = STAR_TEXTURE
    ) = CosmicBody(
        id = id, nameRes = nameRes, kind = BodyKind.STAR,
        radiusKm = solarRadii * R_SUN_KM, textureAsset = texture,
        tintColor = tempToRgb(temperatureK.toDouble()),
        temperatureK = temperatureK, spectralType = spectralType, massSolar = massSolar
    )

    private fun blackHole(
        id: String, nameRes: Int, massSolar: Double
    ) = CosmicBody(
        id = id, nameRes = nameRes, kind = BodyKind.BLACK_HOLE,
        radiusKm = massSolar * RS_PER_SOLAR_MASS_KM, textureAsset = BLACKHOLE_TEXTURE,
        tintColor = 0xFFFF8A3D.toInt(), massSolar = massSolar
    )

    const val STAR_TEXTURE = "textures/cosmic/star_surface.jpg"
    const val BLACKHOLE_TEXTURE = "Assets/sprites/blackhole.jpg"

    /** Roster curé, trié par rayon croissant — sert aussi d'ordre dans les sélecteurs. */
    val bodies: List<CosmicBody> = listOf(
        // ── Lune & planètes (textures réelles déjà présentes dans le projet) ──
        planet("moon", R.string.cosmic_name_moon, BodyKind.ROCKY, 1_737.4, "textures/moon.jpg", 0xFFCCCCCC.toInt()),
        planet("mercury", R.string.cosmic_name_mercury, BodyKind.ROCKY, 2_439.7, "textures/planets/mercury.jpg", 0xFFB5B5B5.toInt()),
        planet("mars", R.string.cosmic_name_mars, BodyKind.ROCKY, 3_389.5, "textures/planets/mars.jpg", 0xFFC1440E.toInt()),
        planet("venus", R.string.cosmic_name_venus, BodyKind.ROCKY, 6_051.8, "textures/planets/venus.jpg", 0xFFE8C76E.toInt()),
        planet("earth", R.string.cosmic_name_earth, BodyKind.ROCKY, 6_371.0, "textures/earth.jpg", 0xFF4A8EBA.toInt()),
        planet("neptune", R.string.cosmic_name_neptune, BodyKind.GAS, 24_622.0, "textures/planets/neptune.jpg", 0xFF4B70DD.toInt()),
        planet("uranus", R.string.cosmic_name_uranus, BodyKind.GAS, 25_362.0, "textures/planets/uranus.jpg", 0xFFC6EBF5.toInt()),
        planet("saturn", R.string.cosmic_name_saturn, BodyKind.GAS, 58_232.0, "textures/planets/saturn.jpg", 0xFFE4D191.toInt(), hasRings = true),
        planet("jupiter", R.string.cosmic_name_jupiter, BodyKind.GAS, 69_911.0, "textures/planets/jupiter.jpg", 0xFFC88B3A.toInt()),

        // ── Étoiles (surface réelle teintée par température de corps noir) ──
        star("sun", R.string.cosmic_name_sun, 1.0, 5_772, "G2V", 1.0, texture = "textures/planets/sun.jpg"),
        star("sirius", R.string.cosmic_name_sirius, 1.19, 9_940, "A1V", 2.06),
        star("pollux", R.string.cosmic_name_pollux, 8.8, 4_860, "K0III", 1.91),
        star("arcturus", R.string.cosmic_name_arcturus, 25.4, 4_286, "K1.5III", 1.08),
        star("aldebaran", R.string.cosmic_name_aldebaran, 45.1, 3_900, "K5III", 1.16),
        star("rigel", R.string.cosmic_name_rigel, 78.9, 12_100, "B8Ia", 21.0),
        star("antares", R.string.cosmic_name_antares, 680.0, 3_660, "M1.5Iab", 12.0),
        star("betelgeuse", R.string.cosmic_name_betelgeuse, 764.0, 3_600, "M1Ia", 16.5),
        star("uyscuti", R.string.cosmic_name_uyscuti, 1_708.0, 3_365, "M4Ia", 7.0),
        star("stephenson218", R.string.cosmic_name_stephenson, 2_150.0, 3_200, "M6", 10.0),

        // ── Trous noirs (rayon = horizon de Schwarzschild, billboard image EHT) ──
        blackHole("bh_stellar", R.string.cosmic_name_bh_stellar, 9.0),
        blackHole("cygx1", R.string.cosmic_name_cygx1, 21.2),
        blackHole("sgra", R.string.cosmic_name_sgra, 4_297_000.0),
        blackHole("m87", R.string.cosmic_name_m87, 6.5e9),
        blackHole("ton618", R.string.cosmic_name_ton618, 6.6e10)
    )

    fun byId(id: String): CosmicBody = bodies.firstOrNull { it.id == id } ?: bodies.first { it.id == "earth" }

    /**
     * Couleur d'un corps noir à [kelvin] (approximation Tanner Helland).
     * Utilisée pour teinter la surface stellaire selon la classe spectrale.
     */
    fun tempToRgb(kelvin: Double): Int {
        val t = (kelvin.coerceIn(1_000.0, 40_000.0)) / 100.0
        val r = if (t <= 66.0) 255.0
                else (329.698727446 * (t - 60).pow(-0.1332047592)).coerceIn(0.0, 255.0)
        val g = if (t <= 66.0) (99.4708025861 * ln(t) - 161.1195681661).coerceIn(0.0, 255.0)
                else (288.1221695283 * (t - 60).pow(-0.0755148492)).coerceIn(0.0, 255.0)
        val b = when {
            t >= 66.0 -> 255.0
            t <= 19.0 -> 0.0
            else -> (138.5177312231 * ln(t - 10) - 305.0447927307).coerceIn(0.0, 255.0)
        }
        return Color.rgb(r.toInt(), g.toInt(), b.toInt())
    }
}
