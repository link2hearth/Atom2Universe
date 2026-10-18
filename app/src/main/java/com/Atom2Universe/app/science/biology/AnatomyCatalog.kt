package com.Atom2Universe.app.science.biology

import android.content.Context
import androidx.annotation.StringRes
import com.Atom2Universe.app.R
import org.json.JSONObject
import java.text.Normalizer
import java.util.Locale

enum class AnatomyRegion(@StringRes val label: Int, val color: FloatArray) {
    BODY(R.string.bio_region_body, floatArrayOf(.73f, .53f, .40f)),
    SKULL(R.string.bio_region_skull, floatArrayOf(.91f, .75f, .47f)),
    NECK(R.string.bio_region_neck, floatArrayOf(.82f, .63f, .75f)),
    SPINE(R.string.bio_region_spine, floatArrayOf(.47f, .74f, .88f)),
    THORAX(R.string.bio_region_thorax, floatArrayOf(.74f, .86f, .69f)),
    ABDOMEN(R.string.bio_region_abdomen, floatArrayOf(.83f, .72f, .53f)),
    UPPER(R.string.bio_region_upper, floatArrayOf(.86f, .60f, .48f)),
    HANDS(R.string.bio_region_hands, floatArrayOf(.92f, .77f, .52f)),
    PELVIS(R.string.bio_region_pelvis, floatArrayOf(.67f, .60f, .89f)),
    LOWER(R.string.bio_region_lower, floatArrayOf(.42f, .78f, .71f)),
    FEET(R.string.bio_region_feet, floatArrayOf(.47f, .68f, .88f)),
    TEETH(R.string.bio_region_teeth, floatArrayOf(.99f, .98f, .94f));
}

enum class AnatomyLayer(@StringRes val label: Int, @StringRes val colorLabel: Int, val color: FloatArray) {
    SKELETON(R.string.bio_layer_skeleton, R.string.bio_colors_bones, floatArrayOf(.94f, .91f, .78f)),
    CARTILAGE(R.string.bio_layer_cartilage, R.string.bio_colors_cartilage, floatArrayOf(.46f, .72f, .84f)),
    MUSCLES(R.string.bio_layer_muscles, R.string.bio_colors_muscles, floatArrayOf(.62f, .16f, .14f)),
    ORGANS(R.string.bio_layer_organs, R.string.bio_colors_organs, floatArrayOf(.82f, .57f, .49f)),
    SENSES(R.string.bio_layer_senses, R.string.bio_colors_senses, floatArrayOf(.92f, .89f, .80f)),
    NERVOUS(R.string.bio_layer_nervous, R.string.bio_colors_nervous, floatArrayOf(.92f, .79f, .44f)),
    VASCULAR(R.string.bio_layer_vascular, R.string.bio_colors_vascular, floatArrayOf(.77f, .20f, .24f)),
    LYMPHATIC(R.string.bio_layer_lymphatic, R.string.bio_colors_lymphatic, floatArrayOf(.42f, .72f, .38f)),
    CONNECTIVE(R.string.bio_layer_connective, R.string.bio_colors_connective, floatArrayOf(.87f, .85f, .71f)),
    SKIN(R.string.bio_layer_skin, R.string.bio_colors_skin, floatArrayOf(.73f, .53f, .40f));

    companion object {
        val defaultVisible: Set<AnatomyLayer> = entries.filter { it != SKIN }.toSet()
    }
}

/** Groupes de navigation : un organe peut avoir plusieurs fonctions physiologiques. */
enum class OrganSystem(@StringRes val label: Int, val color: FloatArray) {
    CARDIOVASCULAR(R.string.bio_system_cardiovascular, floatArrayOf(.88f, .33f, .35f)),
    RESPIRATORY(R.string.bio_system_respiratory, floatArrayOf(.43f, .66f, .92f)),
    DIGESTIVE(R.string.bio_system_digestive, floatArrayOf(.91f, .65f, .35f)),
    URINARY(R.string.bio_system_urinary, floatArrayOf(.82f, .75f, .38f)),
    ENDOCRINE(R.string.bio_system_endocrine, floatArrayOf(.72f, .47f, .85f)),
    LYMPHATIC(R.string.bio_system_lymphatic, floatArrayOf(.46f, .76f, .52f)),
    REPRODUCTIVE(R.string.bio_system_reproductive, floatArrayOf(.89f, .52f, .69f));
}

data class AnatomicalStructure(
    val id: String,
    @StringRes val name: Int,
    @StringRes val summary: Int,
    val region: AnatomyRegion,
    val standardBone: Boolean,
    val boneCount: Int,
    val hasMesh: Boolean,
    val minimum: FloatArray,
    val maximum: FloatArray,
    val wikiEn: String,
    val wikiFr: String,
    val sourceName: String,
    val layer: AnatomyLayer,
    val family: String,
    @StringRes val groupLabel: Int,
    val organSystem: OrganSystem? = null,
    val baseColor: FloatArray? = null,
    val category: String? = null,
    val opacity: Float = 1f,
) {
    // Consulté pour chaque structure à chaque filtrage : évite de reconstruire la clé.
    val groupId: String = "${layer.name}:${anatomyGroupKey(layer, region, family, organSystem, category)}"
}

internal fun anatomyGroupKey(layer: AnatomyLayer, region: AnatomyRegion, family: String, system: OrganSystem?, category: String? = null): String =
    category ?: when (layer) {
        AnatomyLayer.SKELETON -> region.name
        AnatomyLayer.MUSCLES -> family
        AnatomyLayer.ORGANS -> requireNotNull(system).name
        AnatomyLayer.CARTILAGE -> when {
            family.startsWith("cartilage_meniscus_") -> "cartilage_menisci"
            family in setOf("cartilage_disc", "cartilage_costal", "cartilage_nasal") -> family
            else -> "cartilage_laryngeal"
        }
        else -> requireNotNull(category)
    }

data class AnatomyGroup(val id: String, val layer: AnatomyLayer, @StringRes val label: Int, val count: Int)
data class AnatomyBounds(val minimum: FloatArray, val maximum: FloatArray)

/** Palette stable entre langues et côtés : quadriceps, ischio-jambiers, etc. */
private object MusclePalette {
    private val colors = arrayOf(
        floatArrayOf(.88f, .34f, .30f), floatArrayOf(.96f, .64f, .28f),
        floatArrayOf(.48f, .60f, .92f), floatArrayOf(.73f, .43f, .83f),
        floatArrayOf(.63f, .77f, .35f), floatArrayOf(.92f, .49f, .69f),
        floatArrayOf(.35f, .66f, .83f), floatArrayOf(.80f, .69f, .42f),
        floatArrayOf(.52f, .43f, .77f), floatArrayOf(.89f, .50f, .32f),
        floatArrayOf(.53f, .72f, .57f), floatArrayOf(.73f, .57f, .52f),
    )
    fun color(family: String): FloatArray = colors[Math.floorMod(family.hashCode(), colors.size)]
}

val AnatomicalStructure.groupColor: FloatArray
    get() = when (layer) {
        AnatomyLayer.SKELETON -> region.color
        AnatomyLayer.MUSCLES -> MusclePalette.color(category ?: family)
        AnatomyLayer.ORGANS -> requireNotNull(organSystem).color
        AnatomyLayer.CARTILAGE -> when (family) {
            "cartilage_disc" -> floatArrayOf(.72f, .56f, .89f)
            "cartilage_costal" -> floatArrayOf(.40f, .62f, .92f)
            "cartilage_nasal" -> floatArrayOf(.61f, .82f, .49f)
            "cartilage_meniscus_medial", "cartilage_meniscus_lateral" -> floatArrayOf(.91f, .53f, .70f)
            else -> floatArrayOf(.92f, .69f, .43f)
        }
        else -> MusclePalette.color(requireNotNull(category))
    }

class AnatomyCatalog(
    val structures: List<AnatomicalStructure>, val modelParts: List<String>, val modelBytes: Int,
    val atlas: AnatomyAtlas, val defaultLayers: Set<AnatomyLayer>, val defaultHidden: Set<String>,
    val externalGenitalIds: Set<String>, val externalHiddenVariants: Map<String, String>,
    val externalHiddenBounds: Map<String, AnatomyBounds>,
) {
    val byId = structures.associateBy { it.id }
    val structuresWithExternalVariant = externalHiddenVariants.values.toSet()
    val groups = structures.filter { it.hasMesh }.groupBy { it.groupId }.map { (id, items) ->
        AnatomyGroup(id, items.first().layer, items.first().groupLabel, items.size)
    }
    val groupIds = groups.map { it.id }.toSet()
    val defaultGroups = groupIds - structures.filter { it.hasMesh }.groupBy { it.groupId }
        .filterValues { items -> items.all { it.id in defaultHidden } }.keys
    val layers = AnatomyLayer.entries.filter { layer -> structures.any { it.layer == layer && it.hasMesh } }.toSet()
    val renderedBones = structures.filter { it.standardBone && it.hasMesh }.sumOf { it.boneCount }
    val extraBones = structures.filter { it.layer == AnatomyLayer.SKELETON && !it.standardBone && it.hasMesh }.sumOf { it.boneCount }
    val teeth = structures.count { it.region == AnatomyRegion.TEETH }
    val muscles = structures.count { it.layer == AnatomyLayer.MUSCLES && it.hasMesh }
    val cartilages = structures.count { it.layer == AnatomyLayer.CARTILAGE && it.hasMesh }
    val organs = structures.count { it.layer == AnatomyLayer.ORGANS && it.hasMesh }

    companion object {
        const val MODEL_SOURCE = "https://dbarchive.biosciencedbc.jp/en/bodyparts3d/desc.html"
        const val LICENSE_SOURCE = "https://dbarchive.biosciencedbc.jp/en/bodyparts3d/lic.html"
        const val SKELETON_REFERENCE = "https://openstax.org/books/anatomy-and-physiology-2e/pages/7-1-divisions-of-the-skeletal-system"
        const val MUSCLE_REFERENCE = "https://openstax.org/books/anatomy-and-physiology-2e/pages/11-3-axial-muscles-of-the-head-neck-and-back"
        const val CARTILAGE_REFERENCE = "https://openstax.org/books/anatomy-and-physiology-2e/pages/9-3-cartilaginous-joints"
        const val KNEE_REFERENCE = "https://openstax.org/books/anatomy-and-physiology-2e/pages/9-6-anatomy-of-selected-synovial-joints"
        const val ORGAN_REFERENCE = "https://openstax.org/details/books/anatomy-and-physiology-2e"
        const val FACIAL_MODEL_SOURCE = "https://github.com/LluisV/Z-Anatomy/tree/PC-Version/Resources/Models/FBX"
        const val FACIAL_LICENSE_SOURCE = "https://creativecommons.org/licenses/by-sa/4.0/"

        fun load(context: Context, atlas: AnatomyAtlas = AnatomyAtlas.MALE): AnatomyCatalog {
            val root = context.assets.open("${atlas.directory}/catalog.json").bufferedReader().use { JSONObject(it.readText()) }
            val list = root.getJSONArray("structures")
            fun resource(name: String): Int = context.resources.getIdentifier(name, "string", context.packageName)
                .also { require(it != 0) { "Missing anatomy resource: $name" } }
            val result = (0 until list.length()).map { index ->
                val row = list.getJSONObject(index)
                fun vector(key: String) = FloatArray(3) { row.optJSONArray(key)?.optDouble(it)?.toFloat() ?: 0f }
                val region = AnatomyRegion.valueOf(row.getString("region").uppercase(Locale.ROOT))
                val layer = AnatomyLayer.valueOf(row.getString("layer").uppercase(Locale.ROOT))
                val family = row.getString("kind")
                val category = row.optString("category").takeIf { it.isNotBlank() }
                val system = row.optString("organSystem").takeIf { it.isNotBlank() }?.let {
                    OrganSystem.valueOf(it.uppercase(Locale.ROOT))
                }
                val groupLabel = if (category != null) resource("bio_group_$category") else when (layer) {
                    AnatomyLayer.SKELETON -> region.label
                    AnatomyLayer.ORGANS -> requireNotNull(system).label
                    else -> resource("bio_group_" + anatomyGroupKey(layer, region, family, system, category))
                }
                AnatomicalStructure(
                    row.getString("id"), resource(row.getString("name")),
                    resource("bio_summary_" + row.getString("kind")),
                    region,
                    row.getBoolean("standard"), row.getInt("boneCount"), row.getBoolean("mesh"),
                    vector("min"), vector("max"), row.getString("wikiEn"), row.getString("wikiFr"), row.getString("sourceName"),
                    layer, family, groupLabel, system,
                    row.optJSONArray("color")?.let { color ->
                        require(color.length() == 3)
                        FloatArray(3) { color.getDouble(it).toFloat() }.also { values ->
                            require(values.all { it.isFinite() && it in 0f..1f })
                        }
                    },
                    category,
                    row.optDouble("opacity", 1.0).toFloat().also { require(it.isFinite() && it > 0f && it <= 1f) },
                )
            }
            require(result.map { it.id }.distinct().size == result.size)
            if (atlas == AnatomyAtlas.MALE) require(result.filter { it.standardBone }.sumOf { it.boneCount } == 206)
            require(result.filter { it.layer != AnatomyLayer.SKELETON }.all { !it.standardBone && it.boneCount == 0 && it.hasMesh })
            require(result.all { (it.layer == AnatomyLayer.ORGANS) == (it.organSystem != null) })
            val parts = root.getJSONArray("modelParts")
            val paths = (0 until parts.length()).map { index ->
                // .gz serait décompressé et renommé par la fusion des assets Android.
                parts.getString(index).also { require(it == "atlas-%03d.part.gzip".format(Locale.ROOT, index)) }
            }
            val modelBytes = root.getInt("modelBytes")
            require(paths.isNotEmpty() && paths.size <= 11 && modelBytes in 12..512 * 1024 * 1024)
            val layers = result.filter { it.hasMesh }.map { it.layer }.toSet()
            val defaults = root.optJSONArray("defaultLayers")?.let { array ->
                (0 until array.length()).map { AnatomyLayer.valueOf(array.getString(it).uppercase(Locale.ROOT)) }.toSet()
            } ?: (AnatomyLayer.defaultVisible intersect layers)
            val hidden = root.optJSONArray("hiddenByDefault")?.let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }.orEmpty()
            require(defaults.all { it in layers })
            require(hidden.all { id -> result.any { it.id == id && it.hasMesh } })
            val external = root.optJSONArray("externalGenitalIds")?.let { array ->
                (0 until array.length()).map { array.getString(it) }.toSet()
            }.orEmpty()
            val variants = root.optJSONObject("externalHiddenVariants")?.let { values ->
                values.keys().asSequence().associateWith { values.getString(it) }
            }.orEmpty()
            require((external + variants.values).all { id -> result.any { it.id == id && it.hasMesh } })
            require(variants.keys.none { key -> result.any { it.id == key } })
            require(variants.values.toSet().size == variants.size && variants.values.none { it in external })
            val variantBounds = root.optJSONObject("externalHiddenBounds")?.let { values ->
                values.keys().asSequence().associateWith { key ->
                    val bounds = values.getJSONObject(key)
                    fun vector(name: String) = bounds.getJSONArray(name).let { array ->
                        require(array.length() == 3)
                        FloatArray(3) { array.getDouble(it).toFloat() }.also { require(it.all(Float::isFinite)) }
                    }
                    AnatomyBounds(vector("min"), vector("max")).also { box ->
                        require((0..2).all { box.minimum[it] <= box.maximum[it] })
                    }
                }
            }.orEmpty()
            require(variantBounds.keys == variants.values.toSet())
            return AnatomyCatalog(result, paths, modelBytes, atlas, defaults, hidden, external, variants, variantBounds)
        }

        /** Recherche tolérante aux accents, dans les deux langues et par identifiant FMA. */
        fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[\\p{Pd}−]"), "-").lowercase(Locale.ROOT)
    }
}
